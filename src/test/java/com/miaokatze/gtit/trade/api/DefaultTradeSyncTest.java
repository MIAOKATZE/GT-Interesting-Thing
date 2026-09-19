package com.miaokatze.gtit.trade.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.api.NekoTradeIntegrationAPI.GroupRecord;

/**
 * 默认贸易体系（v1.8.17）纯 JVM 测试：更新标签版本比较、记账同步字段 round-trip、
 * 组内容哈希稳定性与变化敏感度。
 * <p>
 * 覆盖目标：
 * <ul>
 * <li>{@code BundledTradeGroups#compareVersions}——升自更新标签之前判定（旧记账 null/空 = 最低）</li>
 * <li>{@code GroupRecord} 新字段（contentHash/dismissedVersion/handledUpdateTag）
 * 序列化 round-trip 与旧 JSON 缺字段缺省</li>
 * <li>{@code NekoTradeIntegrationAPI#computeContentHash}——同内容同哈希、改动变哈希、
 * 条目缺失返回 null（视为已改动）</li>
 * <li>新构造记账默认已处理当前更新标签（全新安装不触发强制同步询问）</li>
 * </ul>
 * 零依赖断言套件，入口为 {@code main}（与 {@code GroupRecordTest} 同模式）。
 */
public class DefaultTradeSyncTest {

    private static final String ID = "unit-test-default-sync";
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        cleanup();
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("compareVersionsSemantics", DefaultTradeSyncTest::compareVersionsSemantics);
        cases.put("freshRecordHandlesCurrentUpdateTag", DefaultTradeSyncTest::freshRecordHandlesCurrentUpdateTag);
        cases.put(
            "groupRecordSyncFieldsRoundTrip",
            () -> runChecked(DefaultTradeSyncTest::groupRecordSyncFieldsRoundTrip));
        cases.put("legacyRecordJsonDefaults", () -> runChecked(DefaultTradeSyncTest::legacyRecordJsonDefaults));
        cases.put("contentHashStableForSameContent", DefaultTradeSyncTest::contentHashStableForSameContent);
        cases.put("contentHashChangesWithContent", DefaultTradeSyncTest::contentHashChangesWithContent);
        cases.put("contentHashNullOnMissingEntry", DefaultTradeSyncTest::contentHashNullOnMissingEntry);
        cases.put("contentHashFollowsRecordOrder", DefaultTradeSyncTest::contentHashFollowsRecordOrder);
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

    // ==================== GroupRecord 新字段 ====================

    static void freshRecordHandlesCurrentUpdateTag() {
        GroupRecord rec = new GroupRecord(ID, 1);
        SimpleAssert.eq(BundledTradeGroups.UPDATE_TAG, rec.handledUpdateTag, "新记账默认已处理当前更新标签（全新安装不弹强制询问）");
        SimpleAssert.eq(0, rec.dismissedVersion, "新记账 dismissedVersion 默认 0");
        SimpleAssert.that(rec.contentHash == null, "新记账 contentHash 初始 null（注册后入账）");
    }

    static void groupRecordSyncFieldsRoundTrip() throws Exception {
        GroupRecord rec = new GroupRecord(ID, 4);
        rec.tradeIds.add("t1");
        rec.tradeIds.add("t2");
        rec.pageIds.add(5);
        rec.contentHash = "abcdef0123456789";
        rec.dismissedVersion = 3;
        rec.handledUpdateTag = "1.8.18";
        rec.save();

        GroupRecord loaded = GroupRecord.load(ID);
        SimpleAssert.that(loaded != null, "记账回读非 null");
        SimpleAssert.eq("abcdef0123456789", loaded.contentHash, "contentHash round-trip");
        SimpleAssert.eq(3, loaded.dismissedVersion, "dismissedVersion round-trip");
        SimpleAssert.eq("1.8.18", loaded.handledUpdateTag, "handledUpdateTag round-trip");
    }

    static void legacyRecordJsonDefaults() throws Exception {
        Path p = Paths.get("config/gtit/trade/integrated", ID + ".json");
        Files.createDirectories(p.getParent());
        // 1.8.17 之前的记账：无同步扩展字段
        Files.write(
            p,
            ("{\"groupId\":\"" + ID + "\",\"version\":3,\"tradeIds\":[\"t1\"],\"pageIds\":[]}")
                .getBytes(StandardCharsets.UTF_8));
        GroupRecord loaded = GroupRecord.load(ID);
        SimpleAssert.that(loaded != null, "旧 JSON 记账回读非 null（关键字段齐全）");
        SimpleAssert.that(loaded.contentHash == null, "旧记账 contentHash 缺省 null（视为已改动，走 GUI 询问）");
        SimpleAssert.eq(0, loaded.dismissedVersion, "旧记账 dismissedVersion 缺省 0");
        SimpleAssert.that(loaded.handledUpdateTag == null, "旧记账 handledUpdateTag 缺省 null（升自更新标签之前 → 强制询问）");
        SimpleAssert.that(
            BundledTradeGroups.compareVersions(loaded.handledUpdateTag, BundledTradeGroups.UPDATE_TAG) < 0,
            "旧记账低于更新标签");
    }

    // ==================== computeContentHash ====================

    /** 构造两条测试条目（id 指定，内容可辨） */
    private static NekoTradeConfig.NekoTradeData twoEntryData() {
        NekoTradeEntry a = new NekoTradeEntry();
        a.setId("hash-entry-a");
        a.setTabId(5);
        a.setOrderId(0);
        a.setCooldown(79200);
        NekoTradeEntry b = new NekoTradeEntry();
        b.setId("hash-entry-b");
        b.setTabId(5);
        b.setOrderId(1);
        b.setCooldown(3600);
        NekoTradeConfig.NekoTradeData data = new NekoTradeConfig.NekoTradeData();
        data.getTrades()
            .add(a);
        data.getTrades()
            .add(b);
        return data;
    }

    private static GroupRecord twoEntryRecord() {
        GroupRecord rec = new GroupRecord(ID, 4);
        rec.tradeIds.add("hash-entry-a");
        rec.tradeIds.add("hash-entry-b");
        return rec;
    }

    static void contentHashStableForSameContent() {
        GroupRecord rec = twoEntryRecord();
        NekoTradeConfig.NekoTradeData data = twoEntryData();
        String h1 = NekoTradeIntegrationAPI.computeContentHash(rec, data);
        String h2 = NekoTradeIntegrationAPI.computeContentHash(rec, data);
        SimpleAssert.that(h1 != null && !h1.isEmpty(), "内容哈希非空");
        SimpleAssert.eq(h1, h2, "同内容两次计算哈希一致（确定性）");
        // 条目在 data 中的存放顺序不影响哈希（按 tradeIds 顺序序列化）
        java.util.Collections.reverse(data.getTrades());
        SimpleAssert.eq(h1, NekoTradeIntegrationAPI.computeContentHash(rec, data), "data 顺序变化不影响哈希");
    }

    static void contentHashChangesWithContent() {
        GroupRecord rec = twoEntryRecord();
        NekoTradeConfig.NekoTradeData data = twoEntryData();
        String baseline = NekoTradeIntegrationAPI.computeContentHash(rec, data);
        data.getTrades()
            .get(0)
            .setCooldown(12345);
        String changed = NekoTradeIntegrationAPI.computeContentHash(rec, data);
        SimpleAssert.that(!baseline.equals(changed), "条目内容变化 → 哈希变化（玩家改过默认条目判定依据）");
    }

    static void contentHashNullOnMissingEntry() {
        GroupRecord rec = twoEntryRecord();
        NekoTradeConfig.NekoTradeData data = twoEntryData();
        // 玩家删除了一条默认条目 → 缺失 → null（视为已改动）
        data.getTrades()
            .remove(1);
        SimpleAssert.that(NekoTradeIntegrationAPI.computeContentHash(rec, data) == null, "记账条目缺失返回 null");
    }

    static void contentHashFollowsRecordOrder() {
        GroupRecord rec = twoEntryRecord();
        NekoTradeConfig.NekoTradeData data = twoEntryData();
        String baseline = NekoTradeIntegrationAPI.computeContentHash(rec, data);
        // 换一组 tradeIds 顺序 → 序列化顺序变化 → 哈希变化
        GroupRecord reordered = twoEntryRecord();
        java.util.Collections.reverse(reordered.tradeIds);
        String reorderedHash = NekoTradeIntegrationAPI.computeContentHash(reordered, data);
        SimpleAssert.that(!baseline.equals(reorderedHash), "tradeIds 顺序参与哈希（口径稳定）");
        // GSON 往返 round-trip 后哈希不变（等价于注册→落盘→重载链路）
        NekoTradeConfig.NekoTradeData roundTripped = GSON
            .fromJson(GSON.toJson(data, NekoTradeConfig.NekoTradeData.class), NekoTradeConfig.NekoTradeData.class);
        SimpleAssert.eq(baseline, NekoTradeIntegrationAPI.computeContentHash(rec, roundTripped), "GSON 往返后哈希不变");
    }

    private static void cleanup() {
        try {
            Files.deleteIfExists(Paths.get("config/gtit/trade/integrated", ID + ".json"));
        } catch (Exception ignored) {}
    }
}
