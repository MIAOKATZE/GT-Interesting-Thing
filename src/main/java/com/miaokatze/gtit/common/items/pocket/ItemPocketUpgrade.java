package com.miaokatze.gtit.common.items.pocket;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtit.common.util.GTITUtils;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.register.CreativeTabManager;

/**
 * 口袋升级插件物品（R95 升级插件体系，S2b）。
 * <p>
 * 五个实例对应 {@link PocketUpgradeType} 的五个效果，<b>一型一件、零元数据</b>：
 * 每实例的 unlocalized / 注册名 / 贴图名全部由该实例的 type 派生（ID 单源风格同
 * {@code ItemNekoDimensionPocket#ID}：全类只有 {@link #ID_PREFIX} 一处前缀字面量）。
 * <p>
 * ★<b>注册名禁家族号后缀</b>（R54c 的 missing-texture 坑）：注册名就是
 * {@code neko_pocket_upgrade_<token>}，与 {@code setTextureName(MODID + ":" + ID)} 派生出的
 * 贴图路径 {@code assets/gtit/textures/items/<ID>.png} 天然一致；家族号只允许出现在
 * {@code tools/artgen_catalog/**} 的 {@code out/} 候选侧。
 * <p>
 * <b>槽位即类型</b>：{@link PocketConstants#UPGRADE_SLOTS} 个插件格的第 N 格只收
 * {@code type().ordinal() == N} 的那一件（判据单源 {@link PocketInventory#acceptsUpgradeCell}）；
 * 放入对应格的那一次手势同时置位效果位图（{@link PocketUpgrades#install}，固化不可逆——
 * 调用点在 GUI 装配片 S4 的服务端槽变更回调，见 {@code PocketInventory#newUpgradeGroup} javadoc）。
 * <p>
 * 物品本体<b>零行为</b>：效果判定全部走 {@code PocketUpgrades#hasUpgrade} 读口袋 NBT 的位图，
 * 本类不持任何驱动逻辑（磁力/通道/蒸馏的驱动归行为层）。
 */
public class ItemPocketUpgrade extends Item {

    /** 注册名（= unlocalized = 贴图基名）的族前缀：全类唯一的名字面量。 */
    private static final String ID_PREFIX = "neko_pocket_upgrade_";

    /**
     * type → 注册名 token（<b>下标 = {@link PocketUpgradeType#ordinal()}</b>，同
     * {@code ItemNekoDimensionPocket#ICON_SUFFIXES} 的"顺序即分派"先例）。
     * <p>
     * ★与 lang 键前缀 {@code item.<token>.name} 天然一致；改 token = 改注册名 = 破档，落档后不可再改。
     * <p>
     * ★R96 S8 是本条纪律唯一一次<b>带防护</b>的破档：下标 4 由 {@code distill_fast} 换成 {@code mage}，
     * 旧注册名不由 Forge 释放，而是被 {@code ItemRegistrar#registerNekoPocketUpgrades} 的<b>影子注册</b>
     * （同一 {@link PocketUpgradeType#MAGE} 实例、裸 {@code GameRegistry.registerItem}、不进创造栏）钉住
     * ⇒ 旧存档那一栈读回来仍是"这一型插件"，只是名字与像素都显示成魔法使。
     */
    private static final String[] TOKENS = { "capacity", "stack", "magnet", "channel_persist", "mage" };

    /**
     * type → tooltip 的完整 lang 键（下标口径同 {@link #TOKENS}）。
     * <p>
     * ★<b>整键字面量表</b>而不是前缀拼接：verify 门禁（R91-e①）按「源码里每条
     * {@code gtit.pocket.*} 引号字面量 ⇔ 两份 lang 各恰一条键行」对账，拼接前缀会以悬空键形态
     * 误红；整键表让五条键天然可被机检对账（与 {@code ICON_SUFFIXES} 同一类"顺序即分派"的冻结表）。
     * 静态块钉住与 {@link #TOKENS} 的耦合（token 改名而键没跟 = 类初始化即炸）。
     */
    private static final String[] TOOLTIP_KEYS = { "gtit.pocket.upgrade.capacity.tooltip",
        "gtit.pocket.upgrade.stack.tooltip", "gtit.pocket.upgrade.magnet.tooltip",
        "gtit.pocket.upgrade.channel_persist.tooltip", "gtit.pocket.upgrade.mage.tooltip" };

    /**
     * ★R100 片 B：魔法使的<b>连号说明族</b>整键字面量表（下标即行号 {@code line.0..line.9}）。
     * <p>
     * 为什么是整键表而不是前缀拼接：R91-e① 的对账门按「源码里每条 {@code gtit.pocket.*} 引号
     * 字面量 ⇔ 两份 lang 各恰一条键行」对账，前缀拼接既进不了那道门（拼出来的键不是字面量，
     * 漏写 lang 抓不住），还会以悬空前缀形态误红；整键表让十行键天然可被机检对账（与
     * {@link #TOOLTIP_KEYS} 同一类冻结表）。静态块钉住连号形状与 mage token 的耦合：
     * 第 i 项必须恰是挂在 {@code TOKENS[MAGE.ordinal()]} 下的 {@code line.<i>} ⇒ 跳号/重号/
     * token 改名而键族没跟都在类初始化即炸（与上面两张表同一条纪律的第三名成员）。
     * <p>
     * 行序：固化 → 四模式 → 蒸馏补充 → 三被动（法杖/猫猫币/源质转换）→ 结晶 → 元素容量 → 代价；
     * 旧单键 {@code mage.tooltip} 仍是首行效果行，本族追加在其后、{@code getAddedByLine()} 尾行之前，
     * 统一 GRAY 单色、不加空行分隔（对齐升级插件 tooltip 的既有单色惯例）。
     */
    private static final String[] MAGE_LINE_KEYS = { "gtit.pocket.upgrade.mage.line.0",
        "gtit.pocket.upgrade.mage.line.1", "gtit.pocket.upgrade.mage.line.2", "gtit.pocket.upgrade.mage.line.3",
        "gtit.pocket.upgrade.mage.line.4", "gtit.pocket.upgrade.mage.line.5", "gtit.pocket.upgrade.mage.line.6",
        "gtit.pocket.upgrade.mage.line.7", "gtit.pocket.upgrade.mage.line.8", "gtit.pocket.upgrade.mage.line.9" };

    static {
        if (TOOLTIP_KEYS.length != TOKENS.length) {
            throw new IllegalStateException("[pocket] 升级插件的 token/tooltip 键两张表长度不一致");
        }
        for (int i = 0; i < TOKENS.length; i++) {
            if (!TOOLTIP_KEYS[i].endsWith("." + TOKENS[i] + ".tooltip")) {
                throw new IllegalStateException(
                    "[pocket] 升级插件 token " + TOKENS[i] + " 与 tooltip 键 " + TOOLTIP_KEYS[i] + " 漂移（两表必须同下标耦合）");
            }
        }
        // ★R100 片 B：连号族的期望键由 TOOLTIP_KEYS[4] 派生（去掉 .tooltip 尾换成 .line.<i>）——
        // 不在这里写出第二条前缀字面量，否则那条半截字面量会被 R91-e① 当悬空键误红。
        final String mageKey = TOOLTIP_KEYS[PocketUpgradeType.MAGE.ordinal()];
        final String familyPrefix = mageKey.substring(0, mageKey.length() - ".tooltip".length());
        for (int i = 0; i < MAGE_LINE_KEYS.length; i++) {
            if (!MAGE_LINE_KEYS[i].equals(familyPrefix + ".line." + i)) {
                throw new IllegalStateException(
                    "[pocket] 魔法使连号说明键 " + MAGE_LINE_KEYS[i]
                        + " 与期望 "
                        + familyPrefix
                        + ".line."
                        + i
                        + " 漂移（族必须 0..N 连续且挂在 mage token 下）");
            }
        }
    }

    /** 本实例代表的效果（构造期注入，不可变）。 */
    private final PocketUpgradeType type;

    public ItemPocketUpgrade(PocketUpgradeType type) {
        this.type = type;
        setUnlocalizedName(unlocalizedNameOf(type));
        setTextureName(GTInterestingThing.MODID + ":" + unlocalizedNameOf(type));
        setMaxStackSize(1);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
    }

    /**
     * 注册名/unlocalized 基名（{@code neko_pocket_upgrade_<token>}，单源 {@link #TOKENS}）。
     * <p>
     * ★R101.2：影子注册（{@code ItemRegistrar} 的旧注册名防护腿）注册后要用它<b>回填</b>翻译键
     * ——{@code GameRegistry.registerItem} 会把键改写成注册名，影子件因此裸键显示过
     * （{@code gtit.neko_pocket_upgrade_distill_fast}，用户实机判"两个魔法使插件"）。
     */
    public static String unlocalizedNameOf(PocketUpgradeType type) {
        return ID_PREFIX + tokenOf(type);
    }

    /** 本实例的效果类型（{@code final} 字段读取，无推导）。 */
    public PocketUpgradeType type() {
        return type;
    }

    /**
     * 静态反查：这一栈是什么效果的插件。
     *
     * @return 对应的 {@link PocketUpgradeType}；{@code stack} 为 {@code null} 或物品不是插件 ⇒ {@code null}
     */
    public static PocketUpgradeType getType(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemPocketUpgrade
            ? ((ItemPocketUpgrade) stack.getItem()).type
            : null;
    }

    /** type → 注册名 token（唯一派生点；越界入参直接炸，注册表是闭集）。 */
    private static String tokenOf(PocketUpgradeType type) {
        return TOKENS[type.ordinal()];
    }

    /**
     * 单行 tooltip：键取 {@link #TOOLTIP_KEYS}（效果说明文案，两份 lang 落地）。
     * <p>
     * 消费端是 {@code equals(key)} 即 break 的循环（先例 {@code common/items/NekoCoin.java:28-34}）；
     * 本类只有一行，缺键时显示键名原文（可观测），不静默空白。
     * <p>
     * ★R100 片 B：mage 型在首行效果行之后追加 {@link #appendMageLines(List)} 的连号说明族；
     * 两类行都在 {@link GTITUtils#getAddedByLine()} 尾行（片 A）之前。
     */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean showAdvanced) {
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(TOOLTIP_KEYS[type.ordinal()]));
        if (type == PocketUpgradeType.MAGE) {
            appendMageLines(tooltip);
        }
        tooltip.add(GTITUtils.getAddedByLine());
    }

    /**
     * ★R100 片 B：魔法使连号说明族（{@link #MAGE_LINE_KEYS}，循环上限 = 表长钉死）。
     * <p>
     * 读法对齐口袋本体先例（{@code ItemNekoDimensionPocket#addInformation} 的连号循环）：键未落时
     * {@code translateToLocal} 原样返回键名 ⇒ 与那位同一个判据跳过，不把键名当文案展示；
     * 只在真的含占位时才格式化一次（口径同 {@code NekoPocketPanel#receiptText}）。
     * 行内数字一律 {@code %n$d} 带位置下标的占位（裸 {@code %d} 会一律取第 1 个实参，与口袋本体
     * 同一条理由），实参由 {@link #mageLineArgs()} 从 PocketConstants 符号派生 —— 本类不写
     * 任何第二份规格数字。
     */
    private static void appendMageLines(List tooltip) {
        final Object[][] args = mageLineArgs();
        for (int i = 0; i < MAGE_LINE_KEYS.length; i++) {
            final String template = StatCollector.translateToLocal(MAGE_LINE_KEYS[i]);
            if (template.equals(MAGE_LINE_KEYS[i])) {
                continue;
            }
            tooltip.add(
                EnumChatFormatting.GRAY + (template.indexOf('%') < 0 ? template : String.format(template, args[i])));
        }
    }

    /**
     * 连号说明族的实参（<b>顺序即各行 {@code %1$d…%n$d}</b>，下标与 {@link #MAGE_LINE_KEYS} 一一对应；
     * 每行自己的实参组，不与口袋本体 {@code tooltipArgs} 那个族混槽 —— 那位是口袋规格读数单源，
     * 本族是插件说明行，两族数字互不相欠）。
     * <p>
     * 全部取自常量符号（节拍换算走 {@code PocketConstants.ticksToSecondsCeil} 单源），本方法
     * 不出现任何规格数字字面量；改任何一处常量 ⇒ 两份 lang 的读数零改动自动跟。
     */
    private static Object[][] mageLineArgs() {
        return new Object[][] {
            // line.0 固化：插件格号 = MAGE 的 ordinal + 1（ordinal 同时是效果位图位，别处不抄格号）
            { Integer.valueOf(PocketUpgradeType.MAGE.ordinal() + 1) },
            // line.1 四模式：主开关之外的子模式位数
            { Integer.valueOf(PocketConstants.MAGE_MODE_BITS.length) },
            // line.2 蒸馏补充：无数字行（间隔秒数住首行效果与口袋本体的 distill_fast 行，不重抄）
            {},
            // line.3 法杖·速率：节拍 tick 与单批每杖上界
            { Integer.valueOf(PocketConstants.MAGE_WAND_INTERVAL_TICKS),
                Integer.valueOf(PocketConstants.MAGE_WAND_MAX_POINTS_PER_BATCH) },
            // line.4 法杖·扫描：玩家背包格数（主手优先那半句无数字）
            { Integer.valueOf(PocketConstants.PLAYER_BACKPACK_SLOTS) },
            // line.5 猫猫币：秒节拍 / 口袋中栏格数 / 背包格数 / 普通币值 / 闪烁币值
            { Integer.valueOf(PocketConstants.ticksToSecondsCeil(PocketConstants.MAGE_SECOND_INTERVAL_TICKS)),
                Integer.valueOf(PocketConstants.GHOST_ITEM_SLOT_LIMIT),
                Integer.valueOf(PocketConstants.PLAYER_BACKPACK_SLOTS),
                Integer.valueOf(PocketConstants.MAGE_COIN_VALUE_NORMAL),
                Integer.valueOf(PocketConstants.MAGE_COIN_VALUE_SHIMMERING) },
            // line.6 源质转换：秒节拍 / 单批每元始点数 / 元始种数
            { Integer.valueOf(PocketConstants.ticksToSecondsCeil(PocketConstants.MAGE_SECOND_INTERVAL_TICKS)),
                Integer.valueOf(PocketConstants.MAGE_TRANSMUTE_POINTS_PER_BATCH),
                Integer.valueOf(PocketConstants.PRIMAL_TAGS.length) },
            // line.7 结晶：秒节拍 / 单批每源质枚数上界 / 每枚点数（真值在 TaumDistillRules）
            { Integer.valueOf(PocketConstants.ticksToSecondsCeil(PocketConstants.MAGE_SECOND_INTERVAL_TICKS)),
                Integer.valueOf(PocketConstants.MAGE_CRYSTAL_MAX_PER_BATCH),
                Integer.valueOf(TaumDistillRules.CRYSTAL_CAPACITY) },
            // line.8 元素容量：元始种数 / 单 tag 上限 / 合计（派生常量）
            { Integer.valueOf(PocketConstants.PRIMAL_TAGS.length), Integer.valueOf(PocketConstants.ELEMENT_CAP_PER_TAG),
                Integer.valueOf(PocketConstants.ELEMENT_TOTAL_CAP) },
            // line.9 代价：结晶先取的秒节拍（其余半句无数字）
            { Integer.valueOf(PocketConstants.ticksToSecondsCeil(PocketConstants.MAGE_SECOND_INTERVAL_TICKS)) }, };
    }
}
