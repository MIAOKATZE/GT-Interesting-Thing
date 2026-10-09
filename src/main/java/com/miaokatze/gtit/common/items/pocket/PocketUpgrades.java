package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 升级效果位图的唯一读写面（R95 升级插件体系）。
 * <p>
 * <b>升级固化语义</b>：插件<b>放入对应插件格即固化</b>——效果位被置起、写进档里，<b>不可卸下</b>
 * （没有"拔掉插件退回效果"的路；{@link #install} 是唯一的置位入口，且只置不清）。
 * <p>
 * <b>位图与槽组的双表示关系</b>：同一份升级在档里有两种表示——
 * <ul>
 * <li><b>效果位图</b>（根键 {@link PocketConstants#UPGRADES_KEY} 下的 byte，位序
 * = {@link PocketUpgradeType#ordinal()}）：<b>效果查询的唯一真相</b>，所有"有没有这个升级"的
 * 判定一律走 {@link #hasUpgrade} 读它，不数槽里的插件；</li>
 * <li><b>插件槽组</b>（持久化键 {@link PocketConstants#UPGRADE_SLOT_GROUP}，{@code ItemStackHandler}
 * 形状）：只是 <b>GUI 呈现</b>——让玩家看见插件插在哪格，不承担效果判定。</li>
 * </ul>
 * 两者由槽组落格的那一次手势同时写（一次手势一次写）；读侧只有位图一条路。
 * <p>
 * ★读路径遵守 R53c 纪律：{@link #hasUpgrade} <b>只读不建档</b>——无 NBT / 无键一律
 * {@code false}，绝不在读路径上建 compound（同 {@code ItemNekoDimensionPocket#setOpenFlag}
 * 头部那条"一次会话一次的建档不在禁令内"的口径）。
 */
public final class PocketUpgrades {

    private PocketUpgrades() {}

    /**
     * 该口袋是否已固化指定升级（只读，不建档）。
     *
     * @param stack 口袋物品栈；{@code null} / 无 NBT / 无 {@link PocketConstants#UPGRADES_KEY} 键一律 {@code false}
     * @param type  要查询的升级；按位图位（= {@link PocketUpgradeType#ordinal()}）读
     */
    public static boolean hasUpgrade(ItemStack stack, PocketUpgradeType type) {
        return hasUpgrade(stack == null ? null : stack.getTagCompound(), type);
    }

    /**
     * ★R95 S5：按 <b>NBT 根</b>查升级位的重载 —— 双轨容量（16M/16G）与堆叠上限（64/1024）都要在
     * "手里只有 NBTTagCompound" 的场合现读升级位（{@code PocketInventory#readFrom} 只面对化合物；
     * 根实例是活的 ⇒ 会话期内 {@code install} 写进的位<b>下一次查询即生效</b>，与栈版同一条真相）。
     * <p>
     * ★栈版 {@link #hasUpgrade(ItemStack, PocketUpgradeType)} 薄委派到这里 ⇒ 位图判据只有一份。
     */
    public static boolean hasUpgrade(NBTTagCompound root, PocketUpgradeType type) {
        if (root == null || !root.hasKey(PocketConstants.UPGRADES_KEY)) {
            return false;
        }
        return (root.getByte(PocketConstants.UPGRADES_KEY) & (1 << type.ordinal())) != 0;
    }

    /** 已固化的数量；旧档仅有位图时按一个插件恢复，读取不修改NBT。 */
    public static int stackUpgradeCount(NBTTagCompound root) {
        if (!hasUpgrade(root, PocketUpgradeType.STACK)) {
            return 0;
        }
        return root.hasKey(PocketConstants.STACK_UPGRADE_COUNT_KEY) ? Math.max(
            1,
            Math.min(PocketConstants.STACK_UPGRADE_MAX_COUNT, root.getInteger(PocketConstants.STACK_UPGRADE_COUNT_KEY)))
            : 1;
    }

    public static int stackUpgradeCount(ItemStack carrier) {
        return stackUpgradeCount(carrier == null ? null : carrier.getTagCompound());
    }

    /** 从不可拆的插件格固化累计数量；不会降低已安装的效果。 */
    public static void installStackCount(ItemStack carrier, int count) {
        if (carrier == null || count <= 0) {
            return;
        }
        install(carrier, PocketUpgradeType.STACK);
        final NBTTagCompound root = carrier.getTagCompound();
        root.setInteger(
            PocketConstants.STACK_UPGRADE_COUNT_KEY,
            Math.max(stackUpgradeCount(root), Math.min(PocketConstants.STACK_UPGRADE_MAX_COUNT, count)));
    }

    /**
     * 固化一个升级：置位图位并写回 stack NBT（一次手势一次写；插件放入对应格时调用，不可逆）。
     * <p>
     * {@code stack} 为 {@code null} 直接 return（调用侧的手势已被吞掉，不在这里造栈）。
     *
     * @param stack 口袋物品栈
     * @param type  要固化的升级；位序 = {@link PocketUpgradeType#ordinal()}
     */
    public static void install(ItemStack stack, PocketUpgradeType type) {
        if (stack == null) {
            return;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            stack.setTagCompound(root);
        }
        install(root, type);
    }

    /**
     * ★R95 S5：往 <b>NBT 根</b>固化升级的重载（栈版薄委派到这里 ⇒ 写入算式只有一份）。
     * 给"手里只有化合物"的装配与用例面用（读侧的对应重载见 {@link #hasUpgrade(NBTTagCompound, PocketUpgradeType)}）。
     */
    public static void install(NBTTagCompound root, PocketUpgradeType type) {
        if (root == null) {
            return;
        }
        root.setByte(
            PocketConstants.UPGRADES_KEY,
            (byte) (root.getByte(PocketConstants.UPGRADES_KEY) | (1 << type.ordinal())));
    }
}
