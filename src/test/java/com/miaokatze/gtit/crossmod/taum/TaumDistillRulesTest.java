package com.miaokatze.gtit.crossmod.taum;

import java.util.LinkedHashMap;
import java.util.Map;

import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/**
 * 源质桥接层「判定逻辑」的零依赖回归套件（入口 {@code main}，与
 * {@code InfinityStorageTypeKeyTest} / {@code DefaultTradeSyncTest} 同形态）。
 * <p>
 * 覆盖 {@link TaumDistillRules} 与 {@link TaumAspectAmounts} 这两块<b>不依赖 MC / TC 类型</b>的
 * 纯函数：是否有 aspect、按 {@code AspectList} <b>原量</b>入账、<b>全有全无</b>的消耗判定、
 * 按 8 点装箱、以及 TC 图标路径派生。
 * <p>
 * <b>只有一套口径可测</b>：产出量 = 原量、溢出 = 全有全无。桥层已按裁定删掉
 * 「每 aspect 各 +1 点」与「部分入账」两档，因此本套件里也不留它们的用例（两说不得并存）。
 * <p>
 * 不覆盖（须实机或 TC 在场环境）：{@code TaumBridge} 对 {@code ThaumcraftCraftingManager}
 * 的真实查表、{@code Aspect.getColor()} 的实际染色、瓶 meta 0/1 切换与晶化源质的 NBT 形状
 * ——那些调用点在本层被刻意压薄（只做「读 TC → 转值对象」），TC 缺席时整条路径不加载。
 */
public class TaumDistillRulesTest {

    /** 显示/存储序桩件：只取 4 格，够覆盖「命中 / 满格 / 无对应格」三类分支 */
    private static final String[] ORDER = { "aer", "ignis", "aqua", "terra" };

    public static void main(String[] args) {
        final Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("无aspect不消耗不推进", TaumDistillRulesTest::noAspectMeansNoConsume);
        cases.put("distill_raw_amount_equals_aspectlist_amount", TaumDistillRulesTest::rawAmountEqualsAspectListAmount);
        cases.put("distill_all_or_nothing_partial_reject", TaumDistillRulesTest::allOrNothingRejectsWholeRound);
        cases.put("distill_full_store_progress_stops_at_100", TaumDistillRulesTest::fullStoreStopsProgress);
        cases.put("超出格表的aspect整轮放弃", TaumDistillRulesTest::unknownTagRejectsRound);
        cases.put("入账换算不改动入参", TaumDistillRulesTest::creditIsPure);
        cases.put("8点装箱与未知容量", TaumDistillRulesTest::bottlingMath);
        cases.put("aspect快照过滤脏条目", TaumDistillRulesTest::snapshotDropsJunk);
        cases.put("图标路径派生", TaumDistillRulesTest::texturePathDerivedFromTag);
        cases.put("需求数值常量钉死", TaumDistillRulesTest::literalNumbersLocked);
        cases.put("单一口径无档位可切", TaumDistillRulesTest::noSwitchableCaliberLeft);
        TestRunner.run(TaumDistillRulesTest.class, cases);
    }

    /** 需求「无源质物品不推进进度条」＝ TC4 canSmelt 的空 AspectList 判据 */
    private static void noAspectMeansNoConsume() {
        SimpleAssert.that(TaumAspectAmounts.EMPTY.isEmpty(), "EMPTY 必须是空快照");
        SimpleAssert.eq(0, TaumAspectAmounts.EMPTY.total(), "EMPTY 总点数为 0");

        final TaumDistillRules.Credit credit = TaumDistillRules
            .credit(new int[4], ORDER, TaumAspectAmounts.EMPTY, TaumDistillRules.MAX_CELL);
        SimpleAssert.that(!credit.consume, "无 aspect ⇒ 本轮不消耗物品");
        SimpleAssert.eq(0, credit.acceptedTotal, "无 aspect ⇒ 不入账");

        // 蒸馏结果为 null 与为空同义（防御上层把 null 传进来）
        final TaumDistillRules.Credit nullResult = TaumDistillRules
            .credit(new int[4], ORDER, null, TaumDistillRules.MAX_CELL);
        SimpleAssert.that(!nullResult.consume, "null 结果同样不消耗");
        // 格表为空（TC 缺席时 aspectOrder() 返回空数组）⇒ 无事发生，不得越界
        final TaumDistillRules.Credit noGrid = TaumDistillRules
            .credit(new int[0], new String[0], TaumAspectAmounts.single("aer", 3), TaumDistillRules.MAX_CELL);
        SimpleAssert.that(!noGrid.consume, "无格表 ⇒ 不消耗");
    }

    /** 入账量 = AspectList 原量（与 TC 炼金炉的全量并入同构，可与炼金炉对账）。 */
    private static void rawAmountEqualsAspectListAmount() {
        final TaumAspectAmounts distilled = TaumAspectAmounts.of(new String[] { "aer", "ignis" }, new int[] { 7, 12 });
        final TaumDistillRules.Credit credit = TaumDistillRules
            .credit(new int[4], ORDER, distilled, TaumDistillRules.MAX_CELL);
        SimpleAssert.that(credit.consume, "两项都放得下 ⇒ 消耗");
        SimpleAssert.eq(19, credit.acceptedTotal, "入账 = 原量 7 + 12（不是每 tag 各 1 点）");
        SimpleAssert.eq(7, credit.delta[0], "aer 格按原量加 7");
        SimpleAssert.eq(12, credit.delta[1], "ignis 格按原量加 12");
        SimpleAssert.that(credit.fullyAccepted, "整份放下");
        SimpleAssert.eq(0, credit.discarded, "放下时没有放弃量");
        SimpleAssert.eq(distilled.total(), credit.acceptedTotal, "入账总量必须逐点等于 AspectList 的 total()");
    }

    /** 全有全无：某个 tag 已满 ⇒ 整轮零入账、零消耗（截断后照扣 = 静默销毁价值）。 */
    private static void allOrNothingRejectsWholeRound() {
        final TaumAspectAmounts distilled = TaumAspectAmounts.of(new String[] { "aer", "ignis" }, new int[] { 5, 5 });
        final TaumDistillRules.Credit credit = TaumDistillRules
            .credit(new int[] { 64, 5, 0, 0 }, ORDER, distilled, TaumDistillRules.MAX_CELL);
        SimpleAssert.that(!credit.consume, "aer 满格 ⇒ 整轮不消耗物品");
        SimpleAssert.eq(0, credit.acceptedTotal, "整轮不落账（ignis 那 5 点也不单独入）");
        SimpleAssert.eq(0, credit.delta.length, "无 delta 输出");
        SimpleAssert.eq(0, credit.acceptedPerTag.length, "无逐条目入账输出");
        SimpleAssert.eq(10, credit.discarded, "放弃量 = 本轮全部应得点数");
        SimpleAssert.that(!credit.fullyAccepted, "非完整放下");

        // 空间不足以容纳原量（不是"满格"，而是"差一点"）同样整轮放弃，绝不截断入账
        final TaumAspectAmounts overflow = TaumAspectAmounts.of(new String[] { "aer" }, new int[] { 10 });
        final TaumDistillRules.Credit tight = TaumDistillRules
            .credit(new int[] { 61, 0, 0, 0 }, ORDER, overflow, TaumDistillRules.MAX_CELL);
        SimpleAssert.that(!tight.consume, "只剩 3 格位、想要 10 ⇒ 整轮放弃（旧的「入 3 弃 7」口径已删）");
        SimpleAssert.eq(0, tight.acceptedTotal, "一格都不进");
        SimpleAssert.eq(10, tight.discarded, "放弃量按应得原量计");
    }

    /** 全满 ⇒ 整轮判 false ⇒ 上层进度停在 100 不重跑（这里验判据点，不验 tick 宿主）。 */
    private static void fullStoreStopsProgress() {
        final TaumAspectAmounts distilled = TaumAspectAmounts.of(new String[] { "aer", "ignis" }, new int[] { 1, 9 });
        final TaumDistillRules.Credit credit = TaumDistillRules
            .credit(new int[] { 64, 64, 0, 0 }, ORDER, distilled, TaumDistillRules.MAX_CELL);
        SimpleAssert.that(!credit.consume, "两格皆满 ⇒ 不消耗（进度因此停在 100 不重跑）");
        SimpleAssert.eq(0, credit.acceptedTotal, "不落账");
        // 只要还有一格放得下（aqua 空着），同一份候选就能整轮入账
        final TaumDistillRules.Credit withRoom = TaumDistillRules.credit(
            new int[] { 64, 64, 0, 64 },
            new String[] { "aer", "ignis", "aqua", "terra" },
            TaumAspectAmounts.of(new String[] { "aqua" }, new int[] { 9 }),
            TaumDistillRules.MAX_CELL);
        SimpleAssert.that(withRoom.consume, "有空格 ⇒ 消耗");
        SimpleAssert.eq(9, withRoom.acceptedTotal, "原量入账");
    }

    /** 48 不是恒值：addon/GT5U 追加的 aspect 可能不在口袋格表内 ⇒ 无格可放即"放不下"。 */
    private static void unknownTagRejectsRound() {
        final TaumAspectAmounts distilled = TaumAspectAmounts
            .of(new String[] { "aer", "potentia" }, new int[] { 1, 1 });
        final TaumDistillRules.Credit credit = TaumDistillRules
            .credit(new int[4], ORDER, distilled, TaumDistillRules.MAX_CELL);
        SimpleAssert.that(!credit.consume, "格表外的 tag ⇒ 无格可放 ⇒ 整轮放弃（不得只入 aer 那 1 点）");
        SimpleAssert.eq(0, credit.acceptedTotal, "一格都不进");
        SimpleAssert.eq(2, credit.discarded, "放弃量含格表外那份");
    }

    /** 纯函数性：入账换算不得改动调用方的存量数组（存储归口袋侧）。 */
    private static void creditIsPure() {
        final int[] stored = { 10, 20, 30, 40 };
        final TaumAspectAmounts distilled = TaumAspectAmounts.of(new String[] { "aer", "ignis" }, new int[] { 2, 3 });
        TaumDistillRules.credit(stored, ORDER, distilled, TaumDistillRules.MAX_CELL);
        SimpleAssert.eq(10, stored[0], "入参 stored 未被改动");
        SimpleAssert.eq(20, stored[1], "入参 stored 未被改动");
        SimpleAssert.eq(30, stored[2], "入参 stored 未被改动");
        SimpleAssert.eq(40, stored[3], "入参 stored 未被改动");
        // 存量长度与格表不一致时按全 0 处理而不是越界
        final TaumDistillRules.Credit mismatch = TaumDistillRules
            .credit(new int[] { 64 }, ORDER, distilled, TaumDistillRules.MAX_CELL);
        SimpleAssert.eq(5, mismatch.acceptedTotal, "长度不符 ⇒ 视作空格，全部入账");
    }

    /** 取＝按 8 点/瓶装箱（不足 8 按实际点数装）；罐容量无证据时按给定值整罐装。 */
    private static void bottlingMath() {
        SimpleAssert.eq(3, TaumDistillRules.containerCountFor(20, TaumDistillRules.PHIAL_CAPACITY), "20 点需 3 瓶");
        SimpleAssert.eq(8, TaumDistillRules.unitFor(20, TaumDistillRules.PHIAL_CAPACITY), "第一瓶满装 8");
        SimpleAssert.eq(4, TaumDistillRules.unitFor(4, TaumDistillRules.PHIAL_CAPACITY), "末瓶按余数 4");
        SimpleAssert.eq(0, TaumDistillRules.unitFor(0, TaumDistillRules.PHIAL_CAPACITY), "无余数 ⇒ 不出瓶");
        SimpleAssert.eq(0, TaumDistillRules.containerCountFor(8, TaumDistillRules.CAPACITY_UNKNOWN), "容量未知 ⇒ 不预判瓶数");
        SimpleAssert.eq(20, TaumDistillRules.unitFor(20, TaumDistillRules.CAPACITY_UNKNOWN), "容量未知 ⇒ 按给定值整份装");
        SimpleAssert
            .eq(64, TaumDistillRules.containerCountFor(64, TaumDistillRules.CRYSTAL_CAPACITY), "64 点 = 64 个晶（1 点/个）");
    }

    /** TC 的 AspectList 会出现 null key（未注册 tag）与 0 点条目，值对象必须过滤干净。 */
    private static void snapshotDropsJunk() {
        final TaumAspectAmounts cleaned = TaumAspectAmounts
            .of(new String[] { "aer", null, "ignis", "terra" }, new int[] { 0, 5, 3, 1 });
        SimpleAssert.eq(2, cleaned.size(), "只剩 ignis 与 terra");
        SimpleAssert.eq("ignis", cleaned.tagAt(0), "保序");
        SimpleAssert.eq(3, cleaned.amountAt(0), "点数保序");
        SimpleAssert.eq(4, cleaned.total(), "总点数 3+1");
        SimpleAssert.eq(3, cleaned.getAmount("ignis"), "按 tag 取点数");
        SimpleAssert.eq(0, cleaned.getAmount("aer"), "被过滤的 tag 读回 0");
        SimpleAssert.eq(0, cleaned.getAmount(null), "null tag 不炸");
        SimpleAssert.that(
            TaumAspectAmounts.single("aer", 0)
                .isEmpty(),
            "0 点单条目即空");
        SimpleAssert.that(
            TaumAspectAmounts.single(null, 5)
                .isEmpty(),
            "null tag 即空");
        final String[] tags = { "aer" };
        try {
            TaumAspectAmounts.of(tags, new int[] { 1, 2 });
            throw new AssertionError("长度不一致必须抛错");
        } catch (IllegalArgumentException expected) {
            // 契约违例要响，静默吞掉会掩盖上层的数组错位
        }
    }

    /** aspect 图标来自 TC 资源域，路径按 tag 派生（不 baked 清单）。 */
    private static void texturePathDerivedFromTag() {
        SimpleAssert.eq("thaumcraft:textures/aspects/aer.png", TaumDistillRules.aspectTexturePath("aer"), "小写 tag 路径");
        SimpleAssert.eq(
            "thaumcraft:textures/aspects/perditio.png",
            TaumDistillRules.aspectTexturePath("PERDITIO"),
            "tag 大小写不敏感（TC 侧 image 用 toLowerCase）");
        SimpleAssert.eq(null, TaumDistillRules.aspectTexturePath(null), "null tag ⇒ null 路径");
        SimpleAssert.eq(null, TaumDistillRules.aspectTexturePath(""), "空 tag ⇒ null 路径");
    }

    /** 需求原文写死的数值即验收判据，改动必须先过账本。 */
    private static void literalNumbersLocked() {
        SimpleAssert.eq(64, TaumDistillRules.MAX_CELL, "每格最大 64 点（TC4 Warded Jar maxAmount 同值）");
        // ★R95 蒸馏加速：节拍从单一 100 字面量改为 distillIntervalTicks(fast) 双口径（100/20 只活在那一处）；
        // DISTILL_INTERVAL_TICKS 是 fast=false 的派生，三条一起钉「基档不变 + 加速档在场 + 常数只是派生」。
        SimpleAssert.eq(
            100,
            TaumDistillRules.DISTILL_INTERVAL_TICKS,
            "基档每 5 秒 = 100 tick 一轮（5 秒是节拍不是产量；★R95：本常数是 distillIntervalTicks(false) 的派生，不是独立真值）");
        SimpleAssert
            .eq(100, TaumDistillRules.distillIntervalTicks(false), "★蒸馏双口径：未装 MAGE ⇒ 100 tick（5 秒）");
        SimpleAssert
            .eq(20, TaumDistillRules.distillIntervalTicks(true), "★R96 S8 提速：装 MAGE（原「蒸馏加速」）⇒ 20 tick（1 秒）");
        SimpleAssert.eq(
            12,
            TaumDistillRules.DISTILL_INPUT_SLOTS,
            "12 个输入格（真值单源 PocketInventory#DISTILL_INPUT_SLOTS）；同物多格聚合成一组，每轮该组只消耗 1 件");
        SimpleAssert.eq(8, TaumDistillRules.PHIAL_CAPACITY, "源质瓶 8 点/次（ItemEssence.java:109-185）");
        SimpleAssert.eq(1, TaumDistillRules.CRYSTAL_CAPACITY, "晶化源质 1 点/个（TileEssentiaCrystalizer.java:293）");
        // ★R78：格数权威已从本桥层摘除（旧 DISPLAY_CELLS=48 是第二份真相）。这里成对断言
        // "旧形状消失 + 新形状在场"（R72 的判据写法），而不是笼统禁止某个符号：
        // 桥层不得再有格数常量，口袋侧必须有一个 ESSENCE_DISPLAY_GRID。
        for (java.lang.reflect.Field field : TaumCompat.class.getDeclaredFields()) {
            SimpleAssert.eq(
                Boolean.FALSE,
                Boolean.valueOf("DISPLAY_CELLS".equals(field.getName())),
                "★TaumCompat 不得再有 DISPLAY_CELLS（格数单源 = PocketConstants.ESSENCE_DISPLAY_GRID，R78②）");
        }
        SimpleAssert.eq(
            72,
            com.miaokatze.gtit.common.items.pocket.PocketConstants.ESSENCE_DISPLAY_GRID,
            "格数权威在 PocketConstants：6 列 × 12 行 = 72（R78②，≥ 实测 aspect 注册数 69）");
        SimpleAssert.eq(-1, TaumCompat.COLOR_UNKNOWN, "颜色未知标记");
        SimpleAssert.eq(-1, TaumDistillRules.CAPACITY_UNKNOWN, "容量未知标记");
        SimpleAssert.eq(0, TaumDistillRules.CAPACITY_NOT_A_CONTAINER, "非容器标记");
    }

    /**
     * 反证「档位没留下」：桥层不得再有可切换的产出量/溢出档位（不留死配置、不留两说并存）。
     * 这里用反射枚举公开成员，出现 {@code Gain}/{@code Overflow} 或五参以上的 {@code credit} 重载即判负。
     */
    private static void noSwitchableCaliberLeft() {
        for (Class<?> nested : TaumDistillRules.class.getDeclaredClasses()) {
            SimpleAssert.eq("Credit", nested.getSimpleName(), "桥层只应剩 Credit 一个嵌套类型，实得 " + nested.getName());
        }
        int creditOverloads = 0;
        for (java.lang.reflect.Method m : TaumDistillRules.class.getDeclaredMethods()) {
            if ("credit".equals(m.getName())) {
                creditOverloads++;
                SimpleAssert.eq(4, m.getParameterCount(), "credit 只许一个四参形态，实得 " + m.getParameterTypes());
            }
            SimpleAssert.that(
                !m.getName()
                    .startsWith("gainsOf"),
                "旧档位入口 gainsOf 必须已删除");
        }
        SimpleAssert.eq(1, creditOverloads, "credit 重载数必须恰为 1");
    }
}
