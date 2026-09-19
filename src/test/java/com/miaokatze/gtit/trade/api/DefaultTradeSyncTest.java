package com.miaokatze.gtit.trade.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;
import com.miaokatze.gtit.trade.api.NekoTradeIntegrationAPI.GroupRecord;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;

/**
 * 默认贸易体系（v1.8.17 引入，v1.8.20 ID 隔离重构）纯 JVM 测试：更新标签版本比较、
 * 记账同步字段 round-trip、MIAO 语义 ID 确定性派生与旧机制记账识别。
 * <p>
 * 覆盖目标：
 * <ul>
 * <li>{@code BundledTradeGroups#compareVersions}——升自更新标签之前判定（旧记账 null/空 = 最低）</li>
 * <li>{@code GroupRecord} 同步字段（dismissedVersion/handledUpdateTag）
 * 序列化 round-trip 与旧 JSON 缺字段缺省（v1.8.20 起 contentHash 字段已废弃）</li>
 * <li>{@code NekoTradeRegistryV2#parseTradeGroupId}——MIAO&lt;序号&gt; 确定性派生
 * （同号恒同 UUID、异号异 UUID、合法 UUID 形态）；非 MIAO 非法串抛
 * {@link IllegalArgumentException}（注册链路按随机 UUID 兜底）</li>
 * <li>{@code BundledTradeGroups#recordHasLegacyIds}——旧机制 UUID 记账识别
 * （升旧存档触发一次性迁移覆盖，即使资产版本号未变）</li>
 * <li>新构造记账默认已处理当前更新标签（全新安装不触发强制同步询问）</li>
 * </ul>
 * 零依赖断言套件，入口为 {@code main}（与 {@code GroupRecordTest} 同模式）。
 */
public class DefaultTradeSyncTest {

    private static final String ID = "unit-test-default-sync";

    public static void main(String[] args) throws Exception {
        cleanup();
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("compareVersionsSemantics", DefaultTradeSyncTest::compareVersionsSemantics);
        cases.put("freshRecordHandlesCurrentUpdateTag", DefaultTradeSyncTest::freshRecordHandlesCurrentUpdateTag);
        cases.put(
            "groupRecordSyncFieldsRoundTrip",
            () -> runChecked(DefaultTradeSyncTest::groupRecordSyncFieldsRoundTrip));
        cases.put("legacyRecordJsonDefaults", () -> runChecked(DefaultTradeSyncTest::legacyRecordJsonDefaults));
        cases.put("miaoIdDerivesDeterministicUuid", DefaultTradeSyncTest::miaoIdDerivesDeterministicUuid);
        cases.put("miaoIdRejectsNonMiaoNonUuid", DefaultTradeSyncTest::miaoIdRejectsNonMiaoNonUuid);
        cases.put("recordHasLegacyIdsDetection", DefaultTradeSyncTest::recordHasLegacyIdsDetection);
        try {
            TestRunner.run(DefaultTradeSyncTest.class, cases);
        } finally {
            cleanup();
        }
    }

    /** 用例体允许抛受检异常（文件写入） */
    private static void runChecked(CheckedCase c) {
        try {
            c.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private interface CheckedCase {

        void run() throws Exception;
    }

    // ==================== compareVersions ====================

    static void compareVersionsSemantics() {
        SimpleAssert.that(BundledTradeGroups.compareVersions("1.8.16", "1.8.17") < 0, "1.8.16 < 1.8.17");
        SimpleAssert.eq(0, BundledTradeGroups.compareVersions("1.8.17", "1.8.17"), "1.8.17 == 1.8.17");
        SimpleAssert.that(BundledTradeGroups.compareVersions(null, "1.8.17") < 0, "null（旧记账）< 更新标签");
        SimpleAssert.that(BundledTradeGroups.compareVersions("", "1.8.17") < 0, "空串 < 更新标签");
        SimpleAssert.that(BundledTradeGroups.compareVersions("1.8.20", "1.8.17") > 0, "1.8.20 > 1.8.17");
        SimpleAssert.eq(0, BundledTradeGroups.compareVersions("1.8", "1.8.0"), "缺段补 0（1.8 == 1.8.0）");
        SimpleAssert.that(BundledTradeGroups.compareVersions("1.9", "1.8.17") > 0, "1.9 > 1.8.17");
        SimpleAssert.that(BundledTradeGroups.compareVersions("2.0", "1.10.3") > 0, "2.0 > 1.10.3（数值段比较）");
    }

    // ==================== GroupRecord 同步字段 ====================

    static void freshRecordHandlesCurrentUpdateTag() {
        GroupRecord rec = new GroupRecord(ID, 1);
        SimpleAssert.eq(BundledTradeGroups.UPDATE_TAG, rec.handledUpdateTag, "新记账默认已处理当前更新标签（全新安装不弹强制询问）");
        SimpleAssert.eq(0, rec.dismissedVersion, "新记账 dismissedVersion 默认 0");
    }

    static void groupRecordSyncFieldsRoundTrip() throws Exception {
        GroupRecord rec = new GroupRecord(ID, 5);
        rec.tradeIds.add("MIAO1");
        rec.tradeIds.add("MIAO2");
        rec.pageIds.add(5);
        rec.dismissedVersion = 3;
        rec.handledUpdateTag = "1.8.19";
        rec.save();

        GroupRecord loaded = GroupRecord.load(ID);
        SimpleAssert.that(loaded != null, "记账回读非 null");
        SimpleAssert.eq("MIAO1", loaded.tradeIds.get(0), "MIAO 语义 id round-trip");
        SimpleAssert.eq("MIAO2", loaded.tradeIds.get(1), "MIAO 语义 id round-trip (2)");
        SimpleAssert.eq(3, loaded.dismissedVersion, "dismissedVersion round-trip");
        SimpleAssert.eq("1.8.19", loaded.handledUpdateTag, "handledUpdateTag round-trip");
    }

    static void legacyRecordJsonDefaults() throws Exception {
        Path p = Paths.get("config/gtit/trade/integrated", ID + ".json");
        Files.createDirectories(p.getParent());
        // 1.8.19 时代的记账：UUID tradeIds + 已废弃的 contentHash 残留键
        Files.write(
            p,
            ("{\"groupId\":\"" + ID
                + "\",\"version\":4,\"tradeIds\":[\"0a0a0a0a-0a0a-0a0a-0a0a-0a0a0a0a0a0a\"],\"pageIds\":[],"
                + "\"contentHash\":\"deadbeef\",\"dismissedVersion\":0,\"handledUpdateTag\":\"1.8.19\"}")
                    .getBytes(StandardCharsets.UTF_8));
        GroupRecord loaded = GroupRecord.load(ID);
        SimpleAssert.that(loaded != null, "旧 JSON 记账回读非 null（关键字段齐全）");
        SimpleAssert.eq(0, loaded.dismissedVersion, "旧记账 dismissedVersion 缺省 0");
        SimpleAssert.eq("1.8.19", loaded.handledUpdateTag, "旧记账 handledUpdateTag 回读");
        // v1.8.20：contentHash 字段已删，旧记账残留键由 Gson 宽容忽略（不再参与任何判定）
        SimpleAssert.that(BundledTradeGroups.recordHasLegacyIds(loaded), "旧 UUID tradeIds 识别为旧机制记账（触发迁移覆盖）");
    }

    // ==================== MIAO 语义 ID 派生 ====================

    static void miaoIdDerivesDeterministicUuid() {
        UUID a1 = NekoTradeRegistryV2.parseTradeGroupId("MIAO1");
        UUID a2 = NekoTradeRegistryV2.parseTradeGroupId("MIAO1");
        SimpleAssert.eq(a1, a2, "MIAO1 两次派生同一 UUID（确定性，收藏/冷却跨版本保持）");
        UUID b = NekoTradeRegistryV2.parseTradeGroupId("MIAO2");
        SimpleAssert.that(!a1.equals(b), "不同序号派生不同 UUID");
        // 派生值是合法 v3（nameUUIDFromBytes）UUID 形态
        SimpleAssert.eq(3, a1.version(), "派生 UUID 为 v3（nameUUIDFromBytes）");
        // 标准 UUID 字符串原样解析（玩家自定义条目不受影响）
        UUID custom = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        SimpleAssert.eq(
            custom,
            NekoTradeRegistryV2.parseTradeGroupId("123e4567-e89b-12d3-a456-426614174000"),
            "合法 UUID 字符串原样解析");
        // MIAO 前缀但非纯数字序号 → 不按 MIAO 形态处理（交由 UUID 解析抛错，注册链路随机兜底）
        try {
            NekoTradeRegistryV2.parseTradeGroupId("MIAOx");
            throw new AssertionError("MIAOx（非数字序号）不应按 MIAO 形态派生");
        } catch (IllegalArgumentException expected) {
            // 预期路径
        }
    }

    static void miaoIdRejectsNonMiaoNonUuid() {
        try {
            NekoTradeRegistryV2.parseTradeGroupId("not-a-uuid");
            throw new AssertionError("非法串应抛 IllegalArgumentException（注册链路按随机 UUID 兜底）");
        } catch (IllegalArgumentException expected) {
            // 预期路径
        }
        try {
            NekoTradeRegistryV2.parseTradeGroupId(null);
            throw new AssertionError("null 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 预期路径
        }
    }

    // ==================== recordHasLegacyIds（v1.8.20 迁移判定） ====================

    static void recordHasLegacyIdsDetection() {
        GroupRecord miao = new GroupRecord(ID, 5);
        miao.tradeIds.add("MIAO1");
        miao.tradeIds.add("MIAO88");
        SimpleAssert.that(!BundledTradeGroups.recordHasLegacyIds(miao), "全 MIAO 记账 = 新机制（不触发迁移）");

        GroupRecord mixed = new GroupRecord(ID, 4);
        mixed.tradeIds.add("MIAO1");
        mixed.tradeIds.add("97be5bf3-b07f-470d-8225-88e4ea740fd3");
        SimpleAssert.that(BundledTradeGroups.recordHasLegacyIds(mixed), "混入 UUID 记账 = 旧机制（触发一次性迁移覆盖）");

        GroupRecord legacy = new GroupRecord(ID, 4);
        legacy.tradeIds.add("97be5bf3-b07f-470d-8225-88e4ea740fd3");
        SimpleAssert.that(BundledTradeGroups.recordHasLegacyIds(legacy), "纯 UUID 记账 = 旧机制");

        GroupRecord empty = new GroupRecord(ID, 4);
        SimpleAssert.that(!BundledTradeGroups.recordHasLegacyIds(empty), "空 tradeIds 不触发迁移（交由版本门控）");
    }

    private static void cleanup() {
        try {
            Files.deleteIfExists(Paths.get("config/gtit/trade/integrated", ID + ".json"));
        } catch (Exception ignored) {}
    }
}
