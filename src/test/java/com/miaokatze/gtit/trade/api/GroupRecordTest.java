package com.miaokatze.gtit.trade.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;
import com.miaokatze.gtit.trade.api.NekoTradeIntegrationAPI.GroupRecord;

/**
 * 贸易组记账（GroupRecord）序列化 round-trip 与无效判定的纯 JVM 测试。
 * <p>
 * GroupRecord 为包级可见纯类，直接构造/落盘/回读；文件写在测试工作目录的
 * {@code config/gtit/trade/integrated/} 下（仓库 gitignore 覆盖），每用例前后清理。
 * 无效判定 = 关键字段缺失或 groupId 不符时 load 返回 null（等同强制重注册）。
 * 因 GTNH convention 未随 test source set 提供测试框架依赖（见
 * {@code SimpleAssert} javadoc），本类为零依赖断言套件，入口为 {@code main}。
 */
public class GroupRecordTest {

    private static final String ID = "unit-test-record";

    public static void main(String[] args) throws Exception {
        cleanup();
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("roundTripPreservesFields", () -> runChecked(GroupRecordTest::roundTripPreservesFields));
        cases.put("missingFileLoadsNull", () -> runChecked(GroupRecordTest::missingFileLoadsNull));
        cases.put("corruptJsonLoadsNull", () -> runChecked(GroupRecordTest::corruptJsonLoadsNull));
        cases.put("mismatchedGroupIdLoadsNull", () -> runChecked(GroupRecordTest::mismatchedGroupIdLoadsNull));
        cases.put("missingKeyFieldsLoadsNull", () -> runChecked(GroupRecordTest::missingKeyFieldsLoadsNull));
        cases.put("deleteRemovesFile", () -> runChecked(GroupRecordTest::deleteRemovesFile));
        try {
            TestRunner.run(GroupRecordTest.class, cases);
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

    private static Path recordPath(String id) {
        return Paths.get("config/gtit/trade/integrated", id + ".json");
    }

    private static void cleanup() {
        try {
            Files.deleteIfExists(recordPath(ID));
        } catch (Exception ignored) {}
    }

    static void roundTripPreservesFields() throws Exception {
        GroupRecord rec = new GroupRecord(ID, 3);
        rec.tradeIds.add("t1");
        rec.tradeIds.add("t2");
        rec.pageIds.add(10);
        rec.save();

        GroupRecord loaded = GroupRecord.load(ID);
        SimpleAssert.that(loaded != null, "记账回读非 null");
        SimpleAssert.eq(ID, loaded.groupId, "groupId round-trip");
        SimpleAssert.eq(3, loaded.version, "version round-trip");
        SimpleAssert.eq(2, loaded.tradeIds.size(), "tradeIds 条数 round-trip");
        SimpleAssert.that(loaded.tradeIds.contains("t2"), "tradeIds 内容 round-trip");
        SimpleAssert.eq(1, loaded.pageIds.size(), "pageIds 条数 round-trip");
        SimpleAssert.eq(Integer.valueOf(10), loaded.pageIds.get(0), "pageIds 内容 round-trip");
    }

    static void missingFileLoadsNull() {
        SimpleAssert.that(GroupRecord.load("unit-test-record-absent") == null, "文件缺失返回 null");
    }

    static void corruptJsonLoadsNull() throws Exception {
        Path p = recordPath(ID);
        Files.createDirectories(p.getParent());
        Files.write(p, "not-json".getBytes(StandardCharsets.UTF_8));
        SimpleAssert.that(GroupRecord.load(ID) == null, "损坏 JSON 返回 null");
    }

    static void mismatchedGroupIdLoadsNull() throws Exception {
        Path p = recordPath(ID);
        Files.createDirectories(p.getParent());
        Files.write(
            p,
            "{\"groupId\":\"other\",\"version\":1,\"tradeIds\":[],\"pageIds\":[]}".getBytes(StandardCharsets.UTF_8));
        SimpleAssert.that(GroupRecord.load(ID) == null, "记账 groupId 与查询不符视为无效（防串文件）");
    }

    static void missingKeyFieldsLoadsNull() throws Exception {
        Path p = recordPath(ID);
        Files.createDirectories(p.getParent());
        Files.write(p, ("{\"groupId\":\"" + ID + "\",\"version\":1}").getBytes(StandardCharsets.UTF_8));
        SimpleAssert.that(GroupRecord.load(ID) == null, "缺 tradeIds/pageIds 关键字段视为无效（强制重注册）");
    }

    static void deleteRemovesFile() throws Exception {
        GroupRecord rec = new GroupRecord(ID, 1);
        rec.save();
        rec.delete();
        SimpleAssert.that(GroupRecord.load(ID) == null, "delete 后 load 返回 null");
    }
}
