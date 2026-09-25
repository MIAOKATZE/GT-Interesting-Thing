package com.miaokatze.gtit.common.items.pocket;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

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
     */
    private static final String[] TOKENS = { "capacity", "stack", "magnet", "channel_persist", "distill_fast" };

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
        "gtit.pocket.upgrade.channel_persist.tooltip", "gtit.pocket.upgrade.distill_fast.tooltip" };

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
    }

    /** 本实例代表的效果（构造期注入，不可变）。 */
    private final PocketUpgradeType type;

    public ItemPocketUpgrade(PocketUpgradeType type) {
        this.type = type;
        final String id = ID_PREFIX + tokenOf(type);
        setUnlocalizedName(id);
        setTextureName(GTInterestingThing.MODID + ":" + id);
        setMaxStackSize(1);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
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
     */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean showAdvanced) {
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(TOOLTIP_KEYS[type.ordinal()]));
    }
}
