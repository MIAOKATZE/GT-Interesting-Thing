package com.miaokatze.gtit.lottery.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.miaokatze.gtit.lottery.LotteryPool;
import com.miaokatze.gtit.lottery.LotteryRarity;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/**
 * {@link LotteryPoolGroupDef} 的 Gson 反序列化测试（内嵌 JSON 字符串样例），
 * 经 {@link LotteryIntegrationAPI#gson()} 的真实适配器配置（rarity 大小写不敏感）。
 * <p>
 * 覆盖边界说明：只测纯 JVM 可达的字段映射与池校验（validate/truncate 均不触 MC 运行时）。
 * 刻意不含 costItems 与 toItemStack 路径——NekoBigItemStack 物品适配器依赖
 * GameRegistry.findItem（MC 物品注册表，纯 JVM 测试环境不可用；未注册物品由适配器
 * 返回 null 并在游戏内装载路径统一清理），故不为其编写 JVM 测试。
 * 因 GTNH convention 未随 test source set 提供测试框架依赖（见
 * {@code SimpleAssert} javadoc），本类为零依赖断言套件，入口为 {@code main}。
 */
public class LotteryPoolGroupDefTest {

    private static final String FULL_JSON = "{" + "\"groupId\":\"mymod.lottery.festival\","
        + "\"version\":2,"
        + "\"requiresMods\":[\"gtsr\",\"betterquesting\"],"
        + "\"pools\":[{"
        + "\"id\":\"festival_basic\","
        + "\"name\":\"节日池\","
        + "\"iconItem\":\"minecraft:fireworks\","
        + "\"iconMeta\":0,"
        + "\"entries\":["
        + "{\"id\":\"bread\",\"item\":\"minecraft:bread\",\"meta\":0,\"minAmount\":2,\"maxAmount\":6,\"weight\":100,\"rarity\":\"COMMON\"},"
        + "{\"id\":\"diamond\",\"item\":\"minecraft:diamond\",\"meta\":0,\"minAmount\":1,\"maxAmount\":1,\"weight\":8,\"rarity\":\"EPIC\"},"
        + "{\"id\":\"coin_back\",\"nekoCurrencyId\":\"neko\",\"minAmount\":2,\"maxAmount\":4,\"weight\":20,\"rarity\":\"RARE\"}"
        + "],"
        + "\"pityConfig\":{\"enabled\":true,\"softPityThreshold\":30,\"softPityIncrement\":5.0,\"hardPityThreshold\":50,\"guaranteedRarity\":\"EPIC\",\"replaceOnPity\":true}"
        + "}]"
        + "}";

    public static void main(String[] args) {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("deserializeFullGroup", LotteryPoolGroupDefTest::deserializeFullGroup);
        cases.put("rarityIsCaseInsensitive", LotteryPoolGroupDefTest::rarityIsCaseInsensitive);
        cases.put("minimalJsonUsesDefaults", LotteryPoolGroupDefTest::minimalJsonUsesDefaults);
        cases.put("entryCountTruncatedToMaxTen", LotteryPoolGroupDefTest::entryCountTruncatedToMaxTen);
        TestRunner.run(LotteryPoolGroupDefTest.class, cases);
    }

    static void deserializeFullGroup() {
        LotteryPoolGroupDef def = LotteryIntegrationAPI.gson()
            .fromJson(FULL_JSON, LotteryPoolGroupDef.class);
        SimpleAssert.that(def != null, "完整组 JSON 反序列化成功");
        SimpleAssert.eq("mymod.lottery.festival", def.getGroupId(), "groupId");
        SimpleAssert.eq(2, def.getVersion(), "version");
        SimpleAssert.that(
            java.util.Arrays.equals(new String[] { "gtsr", "betterquesting" }, def.getRequiresMods()),
            "requiresMods round-trip");
        SimpleAssert.eq(
            1,
            def.getPools()
                .size(),
            "池数量");

        LotteryPool pool = def.getPools()
            .get(0);
        SimpleAssert.eq("festival_basic", pool.getId(), "池 id");
        SimpleAssert.eq("节日池", pool.getName(), "池 name");
        SimpleAssert.eq("minecraft:fireworks", pool.getIconItem(), "池 iconItem");
        SimpleAssert.eq(
            3,
            pool.getEntries()
                .size(),
            "条目数量");
        SimpleAssert.eq(
            "diamond",
            pool.getEntries()
                .get(1)
                .getId(),
            "条目 id");
        SimpleAssert.eq(
            LotteryRarity.EPIC,
            pool.getEntries()
                .get(1)
                .getRarity(),
            "条目 rarity（适配器解析）");
        SimpleAssert.eq(
            30,
            pool.getPityConfig()
                .getSoftPityThreshold(),
            "pityConfig softPityThreshold");
        // 池校验是纯 JVM 路径（条目非空 + 总权重 > 0，内建截断）
        SimpleAssert.that(pool.validate(), "池校验通过");
        SimpleAssert.eq(128, pool.getTotalWeight(), "总权重 100+8+20");
    }

    static void rarityIsCaseInsensitive() {
        String json = "{\"groupId\":\"a\",\"pools\":[{\"id\":\"p\",\"entries\":["
            + "{\"id\":\"e\",\"item\":\"minecraft:apple\",\"weight\":1,\"rarity\":\"epic\"}]}]}";
        LotteryPoolGroupDef def = LotteryIntegrationAPI.gson()
            .fromJson(json, LotteryPoolGroupDef.class);
        SimpleAssert.eq(
            LotteryRarity.EPIC,
            def.getPools()
                .get(0)
                .getEntries()
                .get(0)
                .getRarity(),
            "小写 rarity 经大小写不敏感适配器解析");
    }

    static void minimalJsonUsesDefaults() {
        LotteryPoolGroupDef def = LotteryIntegrationAPI.gson()
            .fromJson("{\"groupId\":\"a\"}", LotteryPoolGroupDef.class);
        SimpleAssert.eq("a", def.getGroupId(), "groupId");
        SimpleAssert.eq(1, def.getVersion(), "version 缺省 1");
        SimpleAssert.that(
            def.getPools() != null && def.getPools()
                .isEmpty(),
            "pools 缺省空列表");
        SimpleAssert.that(def.getRequiresMods() == null, "requiresMods 缺省 null");
    }

    static void entryCountTruncatedToMaxTen() {
        // 12 条目 → validate 内建截断保留前 10（与 LotteryConfig 加载路径一致）
        StringBuilder entries = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0) entries.append(',');
            entries.append("{\"id\":\"e")
                .append(i)
                .append("\",\"item\":\"minecraft:apple\",\"weight\":1,\"rarity\":\"COMMON\"}");
        }
        String json = "{\"groupId\":\"a\",\"pools\":[{\"id\":\"p\",\"entries\":[" + entries + "]}]}";
        LotteryPoolGroupDef def = LotteryIntegrationAPI.gson()
            .fromJson(json, LotteryPoolGroupDef.class);
        LotteryPool pool = def.getPools()
            .get(0);
        SimpleAssert.that(pool.validate(), "超限池截断后仍可校验通过");
        SimpleAssert.eq(
            LotteryPool.MAX_ENTRIES,
            pool.getEntries()
                .size(),
            "12 条截断为 10 条");
    }
}
