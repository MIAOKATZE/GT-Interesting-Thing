package com.miaokatze.gtit.trade.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/**
 * jar 资产清单解析与 groupId/path 白名单的纯 JVM 测试。
 * <p>
 * 仅覆盖 {@link JarAssetManifest}（无 Minecraft/Forge 运行时依赖）：
 * 合法清单、缺字段、formatVersion 错误、空 groups（合法空集）与路径穿越拒绝。
 * 因 GTNH convention 未随 test source set 提供测试框架依赖（见
 * {@code SimpleAssert} javadoc），本类为零依赖断言套件，入口为 {@code main}。
 */
public class JarAssetManifestTest {

    public static void main(String[] args) {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("parseValidManifest", JarAssetManifestTest::parseValidManifest);
        cases.put("parseEmptyGroupsIsValidManifest", JarAssetManifestTest::parseEmptyGroupsIsValidManifest);
        cases.put("rejectWrongFormatVersion", JarAssetManifestTest::rejectWrongFormatVersion);
        cases.put("rejectMissingGroups", JarAssetManifestTest::rejectMissingGroups);
        cases.put("rejectEntriesWithMissingFields", JarAssetManifestTest::rejectEntriesWithMissingFields);
        cases.put("rejectUnsafePaths", JarAssetManifestTest::rejectUnsafePaths);
        cases.put("parseNullReturnsNull", JarAssetManifestTest::parseNullReturnsNull);
        cases.put("versionDefaultsToOne", JarAssetManifestTest::versionDefaultsToOne);
        cases.put("validGroupIds", JarAssetManifestTest::validGroupIds);
        cases.put("invalidGroupIds", JarAssetManifestTest::invalidGroupIds);
        TestRunner.run(JarAssetManifestTest.class, cases);
    }

    private static JsonObject json(String s) {
        return new JsonParser().parse(s)
            .getAsJsonObject();
    }

    static void parseValidManifest() {
        JarAssetManifest m = JarAssetManifest.parse(
            json("{\"formatVersion\":1,\"groups\":[{\"groupId\":\"mymod.basic\",\"version\":2,\"path\":\"groups/basic.json\"}]}"));
        SimpleAssert.that(m != null, "合法清单应解析成功");
        SimpleAssert.eq(1, m.getFormatVersion(), "formatVersion");
        SimpleAssert.eq(1, m.getGroups()
            .size(), "组条目数");
        JarAssetManifest.GroupEntry e = m.getGroups()
            .get(0);
        SimpleAssert.eq("mymod.basic", e.getGroupId(), "条目 groupId");
        SimpleAssert.eq(2, e.getVersion(), "条目 version");
        SimpleAssert.eq("groups/basic.json", e.getPath(), "条目 path");
    }

    static void parseEmptyGroupsIsValidManifest() {
        JarAssetManifest m = JarAssetManifest.parse(json("{\"formatVersion\":1,\"groups\":[]}"));
        SimpleAssert.that(m != null, "空 groups 是合法清单（资产可选，装载为空集）");
        SimpleAssert.that(
            m.getGroups()
                .isEmpty(),
            "空清单组列表为空");
    }

    static void rejectWrongFormatVersion() {
        SimpleAssert.that(JarAssetManifest.parse(json("{\"formatVersion\":2,\"groups\":[]}")) == null, "formatVersion=2 必须拒绝");
        SimpleAssert.that(
            JarAssetManifest.parse(json("{\"groups\":[]}")) == null,
            "formatVersion 缺失必须拒绝");
    }

    static void rejectMissingGroups() {
        SimpleAssert.that(JarAssetManifest.parse(json("{\"formatVersion\":1}")) == null, "groups 缺失必须拒绝");
        SimpleAssert.that(
            JarAssetManifest.parse(json("{\"formatVersion\":1,\"groups\":\"not-array\"}")) == null,
            "groups 非数组必须拒绝");
    }

    static void rejectEntriesWithMissingFields() {
        SimpleAssert.that(
            JarAssetManifest.parse(json("{\"formatVersion\":1,\"groups\":[{\"version\":1,\"path\":\"groups/a.json\"}]}")) == null,
            "条目缺 groupId 必须整清单拒绝");
        SimpleAssert.that(
            JarAssetManifest.parse(json("{\"formatVersion\":1,\"groups\":[{\"groupId\":\"mymod.a\",\"version\":1}]}")) == null,
            "条目缺 path 必须整清单拒绝");
    }

    static void rejectUnsafePaths() {
        SimpleAssert.that(
            JarAssetManifest.parse(json("{\"formatVersion\":1,\"groups\":[{\"groupId\":\"mymod.a\",\"version\":1,\"path\":\"../../escape.json\"}]}")) == null,
            "相对路径穿越必须拒绝");
        SimpleAssert.that(
            JarAssetManifest.parse(json("{\"formatVersion\":1,\"groups\":[{\"groupId\":\"mymod.a\",\"version\":1,\"path\":\"/groups/a.json\"}]}")) == null,
            "绝对路径必须拒绝");
        SimpleAssert.that(
            JarAssetManifest.parse(json("{\"formatVersion\":1,\"groups\":[{\"groupId\":\"mymod.a\",\"version\":1,\"path\":\"groups\\\\a.json\"}]}")) == null,
            "反斜杠必须拒绝");
    }

    static void parseNullReturnsNull() {
        SimpleAssert.that(JarAssetManifest.parse(null) == null, "null 输入返回 null");
    }

    static void versionDefaultsToOne() {
        JarAssetManifest m = JarAssetManifest.parse(json("{\"formatVersion\":1,\"groups\":[{\"groupId\":\"a\",\"path\":\"g/a.json\"}]}"));
        SimpleAssert.that(m != null, "version 可缺省");
        SimpleAssert.eq(1, m.getGroups()
            .get(0)
            .getVersion(), "version 缺省为 1");
    }

    // ==================== groupId 白名单 ====================

    static void validGroupIds() {
        SimpleAssert.that(JarAssetManifest.isValidGroupId("a"), "单字符合法");
        SimpleAssert.that(JarAssetManifest.isValidGroupId("gtit-base"), "连字符合法");
        SimpleAssert.that(JarAssetManifest.isValidGroupId("mymod.trade.basic"), "点号合法");
        SimpleAssert.that(JarAssetManifest.isValidGroupId("a1-b.c"), "混合字符合法");
        SimpleAssert.that(
            JarAssetManifest.isValidGroupId(new String(new char[64]).replace('\0', 'a')),
            "恰 64 位合法");
    }

    static void invalidGroupIds() {
        SimpleAssert.that(!JarAssetManifest.isValidGroupId(null), "null 非法");
        SimpleAssert.that(!JarAssetManifest.isValidGroupId(""), "空串非法");
        SimpleAssert.that(!JarAssetManifest.isValidGroupId("MyMod"), "大写非法");
        SimpleAssert.that(!JarAssetManifest.isValidGroupId("-abc"), "前导连字符非法");
        SimpleAssert.that(!JarAssetManifest.isValidGroupId("a/b"), "路径分隔符非法");
        SimpleAssert.that(!JarAssetManifest.isValidGroupId("a_b"), "下划线非法");
        SimpleAssert.that(!JarAssetManifest.isValidGroupId("a..b"), "连续点路径穿越非法");
        SimpleAssert.that(
            !JarAssetManifest.isValidGroupId(new String(new char[65]).replace('\0', 'a')),
            "超 64 位非法");
    }
}
