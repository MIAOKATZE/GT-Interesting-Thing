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
        final NBTTagCompound root = stack == null ? null : stack.getTagCompound();
        if (root == null || !root.hasKey(PocketConstants.UPGRADES_KEY)) {
            return false;
        }
        return (root.getByte(PocketConstants.UPGRADES_KEY) & (1 << type.ordinal())) != 0;
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
        root.setByte(
            PocketConstants.UPGRADES_KEY,
            (byte) (root.getByte(PocketConstants.UPGRADES_KEY) | (1 << type.ordinal())));
    }
}
