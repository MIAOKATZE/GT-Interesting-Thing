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
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.api.NekoTradeIntegrationAPI.GroupRecord;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;

/**
 * 默认贸易体系（v1.8.17 引入，v1.8.20 ID 隔离，v1.8.21 两场景弹框）纯 JVM 测试：
 * 更新标签版本比较、记账同步字段 round-trip、MIAO 语义 ID 确定性派生与
 * 例行更新内容比对。
 * <p>
 * 覆盖目标：
 * <ul>
 * <li>{@code BundledTradeGroups#compareVersions}——升自更新标签之前判定（旧记账 null/空 = 最低）</li>
 * <li>{@code GroupRecord} 同步字段（dismissedVersion/handledUpdateTag）
 * 序列化 round-trip 与旧 JSON 缺字段缺省（v1.8.20 起 contentHash 字段已废弃）</li>
 * <li>{@code NekoTradeRegistryV2#parseTradeGroupId}——MIAO&lt;序号&gt; 确定性派生
 * （同号恒同 UUID、异号异 UUID、合法 UUID 形态）；非 MIAO 非法串抛
 * {@link IllegalArgumentException}（注册链路按随机 UUID 兜底）</li>
 * <li>{@code NekoTradeIntegrationAPI#defaultEntryContentMatches}——例行更新内容比对
 * （defaultEntry 标记归一、字段差异检出）</li>
 * <li>新构造记账默认已处理当前更新标签（全新安装不触发强制覆盖通知）</li>
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
        cases.put("idMatchesRuntimeUuidSemantics", DefaultTradeSyncTest::idMatchesRuntimeUuidSemantics);
        cases.put("defaultEntryContentComparison", DefaultTradeSyncTest::defaultEntryContentComparison);
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
        // v1.8.20：contentHash 字段已删，旧记账残留键由 Gson 宽容忽略（不再参与任何判定）；
        // v1.8.21：旧记账 handledUpdateTag 低于当前更新标签 → 走强制覆盖分支（recordHasLegacyIds 已并入该判定）
        SimpleAssert.that(
            BundledTradeGroups.compareVersions(loaded.handledUpdateTag, BundledTradeGroups.UPDATE_TAG) < 0,
            "旧记账低于更新标签（v1.8.21 强制覆盖分支捕获，无需独立 legacy 判定）");
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

    // ==================== idMatchesRuntimeUuid（v1.8.21 审查修复：编辑/删除定位） ====================

    static void idMatchesRuntimeUuidSemantics() {
        // MIAO 字面 id ↔ 派生 UUID 匹配（游戏内编辑/删除请求携带派生 UUID，磁盘字面为 MIAO<n>）
        UUID miao1 = NekoTradeRegistryV2.parseTradeGroupId("MIAO1");
        SimpleAssert.that(
            NekoTradeRegistryV2.idMatchesRuntimeUuid("MIAO1", miao1.toString()),
            "MIAO 字面 id 与派生 UUID 匹配（saveTrade/deleteTrade 定位链）");
        SimpleAssert.that(!NekoTradeRegistryV2.idMatchesRuntimeUuid("MIAO2", miao1.toString()), "不同序号的 MIAO 条目不匹配");
        // 玩家自定义 UUID 条目字面透传自匹配（零回归）
        SimpleAssert.that(
            NekoTradeRegistryV2
                .idMatchesRuntimeUuid("123e4567-e89b-12d3-a456-426614174000", "123e4567-e89b-12d3-a456-426614174000"),
            "玩家自定义 UUID 字面自匹配");
        // 非法输入不抛出、返回 false（lambda 内安全）
        SimpleAssert.that(!NekoTradeRegistryV2.idMatchesRuntimeUuid("garbage", "not-a-uuid"), "非法输入返回 false 不抛出");
        SimpleAssert.that(!NekoTradeRegistryV2.idMatchesRuntimeUuid(null, miao1.toString()), "null entryId 返回 false");
        SimpleAssert.that(!NekoTradeRegistryV2.idMatchesRuntimeUuid("MIAO1", null), "null runtimeUuid 返回 false");
    }

    // ==================== defaultEntryContentMatches（v1.8.21 例行更新内容比对） ====================

    /** 构造基础测试条目 */
    private static NekoTradeEntry entry(String id, int tabId, int orderId, int cooldown) {
        NekoTradeEntry e = new NekoTradeEntry();
        e.setId(id);
        e.setTabId(tabId);
        e.setOrderId(orderId);
        e.setCooldown(cooldown);
        return e;
    }

    static void defaultEntryContentComparison() {
        // 资产条目（defaultEntry=false）vs 磁盘条目（注册入盘统一 true）：标记差异应被归一
        NekoTradeEntry asset = entry("MIAO1", 5, 0, 79200);
        NekoTradeEntry disk = entry("MIAO1", 5, 0, 79200);
        disk.setDefaultEntry(true);
        SimpleAssert.that(
            NekoTradeIntegrationAPI.defaultEntryContentMatches(asset, disk),
            "defaultEntry 标记差异归一后内容一致（玩家未改动 → 无变化）");

        // cooldown 差异（玩家改过默认条目）→ 有变化
        NekoTradeEntry modified = entry("MIAO1", 5, 0, 3600);
        modified.setDefaultEntry(true);
        SimpleAssert.that(!NekoTradeIntegrationAPI.defaultEntryContentMatches(asset, modified), "cooldown 差异检出（玩家改动）");

        // 物品清单差异 → 有变化
        NekoTradeEntry itemsChanged = entry("MIAO1", 5, 0, 79200);
        itemsChanged.setDefaultEntry(true);
        itemsChanged.setFromItems(new java.util.ArrayList<>());
        itemsChanged.getFromItems()
            .add(new NekoTradeEntry.ItemEntry("minecraft:bread", 0, 1));
        SimpleAssert.that(!NekoTradeIntegrationAPI.defaultEntryContentMatches(asset, itemsChanged), "fromItems 差异检出");

        // id 不同 → 不匹配（bundledContentDiffers 的缺失路径）
        NekoTradeEntry otherId = entry("MIAO2", 5, 0, 79200);
        otherId.setDefaultEntry(true);
        SimpleAssert.that(!NekoTradeIntegrationAPI.defaultEntryContentMatches(asset, otherId), "id 不同不匹配（按 id 匹配后再比对）");
    }

    private static void cleanup() {
        try {
            Files.deleteIfExists(Paths.get("config/gtit/trade/integrated", ID + ".json"));
        } catch (Exception ignored) {}
    }
}
