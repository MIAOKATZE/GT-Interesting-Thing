package com.miaokatze.gtit.client.gui;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicHull;
import gregtech.common.blocks.ItemMachines;

/**
 * 周目 GUI 外壳累计槽物品判定器（v1.9.0 C批 S7）
 * <p>
 * 判定「某 ItemStack 是否为指定等级列可消耗的机械外壳/外壳代材」（任务包冻结口径）：
 * <ul>
 * <li>列 1..14（LV..MAX）：{@code stack.item instanceof ItemMachines} 且
 * {@code ItemMachines.getMetaTileEntity(stack) instanceof MTEBasicHull} 且
 * {@code mTier == column}（mTier 定义见 GT5U
 * {@code MTETieredMachineBlock}，先例 {@code ItemMachines.java:79-84}、
 * {@code MTEBasicHull.java}；LV..MAX 恰为 mTier 1..14，蒸汽列除外）；</li>
 * <li>列 0（蒸汽）：接受 {@code GregTechAPI.sBlockCasings1}（registry 名
 * {@code gt.blockcasings}）meta 10（Bronze Plated Bricks 镀铜砖块），
 * 或注册名 {@code miscutils.blockcasings} meta 10。</li>
 * </ul>
 * <p>
 * <b>侧与加载</b>：仅引用 common/GT 类型（无 {@code net.minecraft.client}），
 * 双端可加载；专用服上因周目系统整体门控永不触达。
 * 本类不持有状态，全部静态方法。
 */
public final class ReincarnationHullMatcher {

    /** 蒸汽列外壳代材 meta：sBlockCasings1 meta 10 = Bronze Plated Bricks（镀铜砖块） */
    public static final int STEAM_CASING_META = 10;

    /** miscutils 镀铜砖块注册名（列 0 备选材质） */
    private static final String MISCUTILS_CASINGS_NAME = "miscutils.blockcasings";

    private ReincarnationHullMatcher() {}

    /**
     * 判定物品是否可作为指定等级列的外壳消耗件
     *
     * @param stack  待判定物品堆（可为 null/无效，一律 false）
     * @param column 等级列下标（0=蒸汽，1..14=LV..MAX；越界一律 false）
     * @return true = 可放入该列外壳累计槽
     */
    public static boolean matchesHull(ItemStack stack, int column) {
        if (stack == null || stack.getItem() == null || column < 0 || column > 14) {
            return false;
        }
        if (column == 0) {
            return matchesSteamCasing(stack);
        }
        // LV..MAX 列：GT 机械外壳（ItemMachines 载荷 + MTEBasicHull 档位匹配）
        if (stack.getItem() instanceof ItemMachines) {
            IMetaTileEntity metaTileEntity = ItemMachines.getMetaTileEntity(stack);
            // MTEBasicHull extends MTEBasicTank extends MTETieredMachineBlock（public final byte mTier）
            return metaTileEntity instanceof MTEBasicHull && ((MTEBasicHull) metaTileEntity).mTier == column;
        }
        return false;
    }

    /**
     * 蒸汽列（列 0）判定：sBlockCasings1 meta {@value #STEAM_CASING_META}（镀铜砖块）
     * 或注册名 {@value #MISCUTILS_CASINGS_NAME} meta {@value #STEAM_CASING_META}
     */
    private static boolean matchesSteamCasing(ItemStack stack) {
        if (stack.getItemDamage() != STEAM_CASING_META) {
            return false;
        }
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == null) {
            return false;
        }
        // 权威判定：直接比对 GT 注册的 sBlockCasings1 实例（不依赖 registry 名字符串）
        if (block == GregTechAPI.sBlockCasings1) {
            return true;
        }
        // 备选：miscutils 镀铜砖块（按注册名识别）
        Object registryName = Block.blockRegistry.getNameForObject(block);
        return registryName != null && MISCUTILS_CASINGS_NAME.equals(registryName.toString());
    }
}
