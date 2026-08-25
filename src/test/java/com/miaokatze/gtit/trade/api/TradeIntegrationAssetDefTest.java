package com.miaokatze.gtit.trade.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/**
 * {@link TradeIntegrationAssetDef} 纯字段类的 Gson 反序列化测试（内嵌 JSON 字符串样例）。
 * <p>
 * groupJson 是"JSON 文本"字段——经转义嵌入外层 JSON 后 round-trip 必须逐字还原，
 * 这是 jar 资产清单（清单条目 → 组定义文件文本）通道的载荷基础。
 * 因 GTNH convention 未随 test source set 提供测试框架依赖（见
 * {@code SimpleAssert} javadoc），本类为零依赖断言套件，入口为 {@code main}。
 */
public class TradeIntegrationAssetDefTest {

    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("roundTripPreservesAllFields", TradeIntegrationAssetDefTest::roundTripPreservesAllFields);
        cases.put("deserializeFromEscapedAssetJson", TradeIntegrationAssetDefTest::deserializeFromEscapedAssetJson);
        cases.put("defaultsAreTolerated", TradeIntegrationAssetDefTest::defaultsAreTolerated);
        TestRunner.run(TradeIntegrationAssetDefTest.class, cases);
    }

    static void roundTripPreservesAllFields() {
        String groupJson = "{\"groupId\":\"mymod.basic\",\"version\":1,\"trades\":[],\"pages\":[]}";
        TradeIntegrationAssetDef def = new TradeIntegrationAssetDef("mymod", "mymod.basic", 2, groupJson);

        TradeIntegrationAssetDef copy = GSON.fromJson(GSON.toJson(def), TradeIntegrationAssetDef.class);
        SimpleAssert.eq("mymod", copy.getOwnerModId(), "ownerModId round-trip");
        SimpleAssert.eq("mymod.basic", copy.getGroupId(), "groupId round-trip");
        SimpleAssert.eq(2, copy.getVersion(), "version round-trip");
        SimpleAssert.eq(groupJson, copy.getGroupJson(), "内嵌 JSON 文本 round-trip 不变形");
    }

    static void deserializeFromEscapedAssetJson() {
        String assetJson = "{\"ownerModId\":\"mymod\",\"groupId\":\"mymod.basic\",\"version\":3,"
            + "\"groupJson\":\"{\\\"groupId\\\":\\\"mymod.basic\\\",\\\"version\\\":3,\\\"trades\\\":[]}\"}";
        TradeIntegrationAssetDef def = GSON.fromJson(assetJson, TradeIntegrationAssetDef.class);
        SimpleAssert.that(def != null, "资产 JSON 反序列化成功");
        SimpleAssert.eq("mymod", def.getOwnerModId(), "ownerModId");
        SimpleAssert.eq("mymod.basic", def.getGroupId(), "groupId");
        SimpleAssert.eq(3, def.getVersion(), "version");
        SimpleAssert.eq("{\"groupId\":\"mymod.basic\",\"version\":3,\"trades\":[]}", def.getGroupJson(), "转义还原后的 groupJson");
    }

    static void defaultsAreTolerated() {
        TradeIntegrationAssetDef def = GSON.fromJson("{\"groupJson\":\"{}\"}", TradeIntegrationAssetDef.class);
        SimpleAssert.eq("{}", def.getGroupJson(), "groupJson 保留");
        // ownerModId/groupId 未声明、version 缺省 0（= 未声明，以 groupJson 内为准）
        SimpleAssert.eq(null, def.getOwnerModId(), "ownerModId 缺省 null");
        SimpleAssert.eq(null, def.getGroupId(), "groupId 缺省 null");
        SimpleAssert.eq(0, def.getVersion(), "version 缺省 0");
    }
}
