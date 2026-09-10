package com.miaokatze.gtit.reincarnation;

import java.util.LinkedHashMap;
import java.util.Map;

import com.miaokatze.gtit.reincarnation.core.ReincarnationGrantGate;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/**
 * 发放门控（v1.8.6 {@link ReincarnationGrantGate}）纯 JVM 测试：新档首次登录 + 本档
 * 未领取的判定矩阵与阈值边界。零 MC 类型、零外部依赖（同
 * {@code ReincarnationStoreTest} 口径：零依赖断言套件，入口为 {@code main}，
 * 用例注册经 {@code TestRunner}，任一失败以非零退出码结束）。
 */
public class ReincarnationGrantGateTest {

    public static void main(String[] args) {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("freshSaveFirstLoginGrants", ReincarnationGrantGateTest::freshSaveFirstLoginGrants);
        cases.put("grantClaimedMarkerRejects", ReincarnationGrantGateTest::grantClaimedMarkerRejects);
        cases.put("playedTicksRejects", ReincarnationGrantGateTest::playedTicksRejects);
        cases.put("matureOverworldRejects", ReincarnationGrantGateTest::matureOverworldRejects);
        cases.put("zeroTicksButOldWorldRejects", ReincarnationGrantGateTest::zeroTicksButOldWorldRejects);
        cases.put("cannotClaimRejects", ReincarnationGrantGateTest::cannotClaimRejects);
        cases.put("thresholdBoundary", ReincarnationGrantGateTest::thresholdBoundary);
        TestRunner.run(ReincarnationGrantGateTest.class, cases);
    }

    /** 新存档首次登录（未游玩 + 世界新 + 未领取）→ 可领 */
    static void freshSaveFirstLoginGrants() {
        SimpleAssert.that(ReincarnationGrantGate.shouldGrant(true, false, 0L, 0L), "全新存档首登可领");
        SimpleAssert.that(ReincarnationGrantGate.shouldGrant(true, false, 0L, 500L), "世界 500 tick（窗口内）仍可领");
        SimpleAssert.that(ReincarnationGrantGate.shouldGrant(true, false, 0L, 1199L), "世界 1199 tick（阈值下界）仍可领");
    }

    /** 本档已领取标记（GrantClaimed）置位 → 拒绝（即使其余信号全新） */
    static void grantClaimedMarkerRejects() {
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, true, 0L, 0L), "已领取标记拒绝");
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, true, 0L, 1199L), "已领取标记 + 世界窗口内仍拒绝");
    }

    /** 玩家累计游玩 tick != 0 → 拒绝（非首次登录；统计溢出负值形态同样按非零拒绝） */
    static void playedTicksRejects() {
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, false, 1L, 0L), "游玩 1 tick 即拒绝");
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, false, Long.MAX_VALUE, 0L), "大量游玩拒绝（不溢出错判）");
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, false, -1L, 0L), "负值（统计溢出形态）按非零拒绝");
    }

    /** overworld 总计时 >= 1200 → 拒绝（严格小于：=1200 即拒） */
    static void matureOverworldRejects() {
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, false, 0L, 1200L), "=1200 即拒绝（严格小于语义）");
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, false, 0L, 1201L), ">1200 拒绝");
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, false, 0L, Long.MAX_VALUE), "远超阈值拒绝");
    }

    /** playTicks=0（统计丢失形态）但世界已老 → 拒绝（时间信号互补兜底） */
    static void zeroTicksButOldWorldRejects() {
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, false, 0L, 24000L), "统计丢失但世界已运行一天 → 拒绝");
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, true, 0L, 24000L), "统计丢失 + 已领取 → 拒绝");
    }

    /** canClaim=false（许可链路无可领取）→ 一票拒绝，与其余信号无关 */
    static void cannotClaimRejects() {
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(false, false, 0L, 0L), "无可领取即拒绝");
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(false, false, 0L, 1199L), "无可领取 + 其余信号全新仍拒绝");
    }

    /** 阈值边界对拍：1199 → true，1200 → false */
    static void thresholdBoundary() {
        SimpleAssert.that(
            ReincarnationGrantGate
                .shouldGrant(true, false, 0L, ReincarnationGrantGate.NEW_SAVE_MAX_TOTAL_WORLD_TIME - 1L),
            "阈值-1（1199）→ 放行");
        SimpleAssert.that(
            !ReincarnationGrantGate.shouldGrant(true, false, 0L, ReincarnationGrantGate.NEW_SAVE_MAX_TOTAL_WORLD_TIME),
            "阈值本身（1200）→ 拒绝");
        SimpleAssert.eq(1200L, ReincarnationGrantGate.NEW_SAVE_MAX_TOTAL_WORLD_TIME, "阈值常量语义锚定");
    }
}
