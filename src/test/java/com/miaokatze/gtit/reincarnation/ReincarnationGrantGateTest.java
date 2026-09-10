package com.miaokatze.gtit.reincarnation;

import java.util.LinkedHashMap;
import java.util.Map;

import com.miaokatze.gtit.reincarnation.core.ReincarnationGrantGate;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/**
 * 发放门控（{@link ReincarnationGrantGate}）纯 JVM 测试：新档首次登录
 * （playTicks==0）/ 本档发放续跑（GrantStarted 置位）双通路 + 未领取不变量的判定矩阵。
 * 时间阈值用例已随门控修订删除（时变信号在 +200t 复核点漂移误拦，实测 playTicks=198）。
 * 零 MC 类型、零外部依赖（同 {@code ReincarnationStoreTest} 口径：零依赖断言套件，
 * 入口为 {@code main}，用例注册经 {@code TestRunner}，任一失败以非零退出码结束）。
 */
public class ReincarnationGrantGateTest {

    public static void main(String[] args) {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("freshSaveFirstLoginGrants", ReincarnationGrantGateTest::freshSaveFirstLoginGrants);
        cases.put("crashResumeBypassGrants", ReincarnationGrantGateTest::crashResumeBypassGrants);
        cases.put("grantClaimedMarkerRejects", ReincarnationGrantGateTest::grantClaimedMarkerRejects);
        cases.put("startedButClaimedRejects", ReincarnationGrantGateTest::startedButClaimedRejects);
        cases.put("playedTicksWithoutStartRejects", ReincarnationGrantGateTest::playedTicksWithoutStartRejects);
        cases.put("cannotClaimRejects", ReincarnationGrantGateTest::cannotClaimRejects);
        TestRunner.run(ReincarnationGrantGateTest.class, cases);
    }

    /** 新存档首次登录（可领取，未领取，未启动发放，playTicks=0）→ 可领 */
    static void freshSaveFirstLoginGrants() {
        SimpleAssert.that(ReincarnationGrantGate.shouldGrant(true, false, false, 0L), "全新存档首登可领（playTicks=0）");
    }

    /** 续跑旁路：GrantStarted 置位 → playTicks>0（登录后计时漂移形态）仍放行（崩溃续跑） */
    static void crashResumeBypassGrants() {
        SimpleAssert.that(
            ReincarnationGrantGate.shouldGrant(true, false, true, 198L),
            "续跑旁路：playTicks 漂移到 198 仍可领（v1.8.6 误拦形态）");
        SimpleAssert.that(ReincarnationGrantGate.shouldGrant(true, false, true, Long.MAX_VALUE), "续跑旁路：大量游玩不误拦");
    }

    /** 本档已领取标记（GrantClaimed）置位 → 拒绝（即使 playTicks=0 形态全新） */
    static void grantClaimedMarkerRejects() {
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, true, false, 0L), "已领取标记拒绝（未 started）");
    }

    /** 已启动发放但本档已领取 → 拒绝（不变量：claimed 一票否决，旁路不越过已领取） */
    static void startedButClaimedRejects() {
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, true, true, 0L), "started + 已领取拒绝（playTicks=0）");
        SimpleAssert
            .that(!ReincarnationGrantGate.shouldGrant(true, true, true, 198L), "started + 已领取 + playTicks>0 拒绝");
    }

    /** playTicks>0 且未 started → 拒绝（非首登且无续跑承诺；统计溢出负值形态同按非零拒绝） */
    static void playedTicksWithoutStartRejects() {
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, false, false, 1L), "游玩 1 tick 即拒绝");
        SimpleAssert
            .that(!ReincarnationGrantGate.shouldGrant(true, false, false, 198L), "playTicks=198（v1.8.6 误拦形态的未承诺版）拒绝");
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, false, false, Long.MAX_VALUE), "大量游玩拒绝（不溢出错判）");
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(true, false, false, -1L), "负值（统计溢出形态）按非零拒绝");
    }

    /** canClaim=false（许可链路无可领取）→ 一票拒绝，与其余信号无关 */
    static void cannotClaimRejects() {
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(false, false, false, 0L), "无可领取即拒绝");
        SimpleAssert.that(!ReincarnationGrantGate.shouldGrant(false, false, true, 198L), "无可领取 + 续跑旁路仍拒绝");
    }
}
