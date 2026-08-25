package com.miaokatze.gtit.lottery.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import com.miaokatze.gtit.lottery.api.LotteryIntegrationAPI.PoolGroupRecord;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/**
 * 抽奖池组记账（PoolGroupRecord）序列化 round-trip 与无效判定的纯 JVM 测试。
 * <p>
 * 与贸易侧 GroupRecordTest 同构：记账形状 {groupId, version, poolIds}，
 * 关键字段缺失或 groupId 不符时 load 返回 null（等同强制重注册）。
 * 文件写在测试工作目录的 {@code config/gtit/lottery/integrated/} 下，每用例前后清理。
 * 因 GTNH convention 未随 test source set 提供测试框架依赖（见
 * {@code SimpleAssert} javadoc），本类为零依赖断言套件，入口为 {@code main}。
 */
public class PoolGroupRecordTest {

    private static final String ID = "unit-test-pool-record";

    public static void main(String[] args) throws Exception {
        cleanup();
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("roundTripPreservesFields", () -> runChecked(PoolGroupRecordTest::roundTripPreservesFields));
        cases.put("missingFileLoadsNull", () -> runChecked(PoolGroupRecordTest::missingFileLoadsNull));
        cases.put("corruptJsonLoadsNull", () -> runChecked(PoolGroupRecordTest::corruptJsonLoadsNull));
        cases.put("mismatchedGroupIdLoadsNull", () -> runChecked(PoolGroupRecordTest::mismatchedGroupIdLoadsNull));
        cases.put("missingKeyFieldsLoadsNull", () -> runChecked(PoolGroupRecordTest::missingKeyFieldsLoadsNull));
        cases.put("deleteRemovesFile", () -> runChecked(PoolGroupRecordTest::deleteRemovesFile));
        try {
            TestRunner.run(PoolGroupRecordTest.class, cases);
        } finally {
            cleanup();
        }
    }

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

    private static Path recordPath(String id) {
        return Paths.get("config/gtit/lottery/integrated", id + ".json");
    }

    private static void cleanup() {
        try {
            Files.deleteIfExists(recordPath(ID));
        } catch (Exception ignored) {}
    }

    static void roundTripPreservesFields() throws Exception {
        PoolGroupRecord rec = new PoolGroupRecord(ID, 5);
        rec.poolIds.add("festival_basic");
        rec.poolIds.add("festival_rare");
        rec.save();

        PoolGroupRecord loaded = PoolGroupRecord.load(ID);
        SimpleAssert.that(loaded != null, "记账回读非 null");
        SimpleAssert.eq(ID, loaded.groupId, "groupId round-trip");
        SimpleAssert.eq(5, loaded.version, "version round-trip");
        SimpleAssert.eq(2, loaded.poolIds.size(), "poolIds 条数 round-trip");
        SimpleAssert.that(loaded.poolIds.contains("festival_rare"), "poolIds 内容 round-trip");
    }

    static void missingFileLoadsNull() {
        SimpleAssert.that(PoolGroupRecord.load("unit-test-pool-record-absent") == null, "文件缺失返回 null");
    }

    static void corruptJsonLoadsNull() throws Exception {
        Path p = recordPath(ID);
        Files.createDirectories(p.getParent());
        Files.write(p, "not-json".getBytes(StandardCharsets.UTF_8));
        SimpleAssert.that(PoolGroupRecord.load(ID) == null, "损坏 JSON 返回 null");
    }

    static void mismatchedGroupIdLoadsNull() throws Exception {
        Path p = recordPath(ID);
        Files.createDirectories(p.getParent());
        Files.write(
            p,
            "{\"groupId\":\"other\",\"version\":1,\"poolIds\":[]}".getBytes(StandardCharsets.UTF_8));
        SimpleAssert.that(PoolGroupRecord.load(ID) == null, "记账 groupId 与查询不符视为无效（防串文件）");
    }

    static void missingKeyFieldsLoadsNull() throws Exception {
        Path p = recordPath(ID);
        Files.createDirectories(p.getParent());
        Files.write(p, ("{\"groupId\":\"" + ID + "\",\"version\":1}").getBytes(StandardCharsets.UTF_8));
        SimpleAssert.that(PoolGroupRecord.load(ID) == null, "缺 poolIds 关键字段视为无效（强制重注册）");
    }

    static void deleteRemovesFile() throws Exception {
        PoolGroupRecord rec = new PoolGroupRecord(ID, 1);
        rec.save();
        rec.delete();
        SimpleAssert.that(PoolGroupRecord.load(ID) == null, "delete 后 load 返回 null");
    }
}
