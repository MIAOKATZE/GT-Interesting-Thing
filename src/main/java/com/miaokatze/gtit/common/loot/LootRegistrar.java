package com.miaokatze.gtit.common.loot;

import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;

import com.miaokatze.gtit.common.api.enums.GTITItemList;

import gregtech.api.util.GTLog;

/**
 * 战利品注册
 * 将猫猫币添加到各种箱子战利品表和钓鱼战利品中
 */
public class LootRegistrar {

    private static final Random RANDOM = new Random();

    /**
     * 注册所有战利品
     */
    public static void init() {
        registerChestLoot();
        registerFishingLoot();
        GTLog.out.println("[GTIT] 战利品注册完成");
    }

    /**
     * 注册箱子战利品
     * 村庄、神庙、地牢、废弃矿井、沙漠神殿等
     */
    private static void registerChestLoot() {
        ItemStack nekoCoin = GTITItemList.NekoCoin.get(1);
        if (nekoCoin == null) return;

        // WeightedRandomChestContent(itemStack, minAmount, maxAmount, weight)
        // weight: 较低值 = 较稀有。普通物品通常 1-10，稀有物品 1-3
        // 我们用 weight=3，每个箱子2-8个
        WeightedRandomChestContent nekoCoinLoot = new WeightedRandomChestContent(nekoCoin, 2, 8, 3);

        // 地牢箱子
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.DUNGEON_CHEST)
            .addItem(nekoCoinLoot);

        // 村庄铁匠铺箱子
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.VILLAGE_BLACKSMITH)
            .addItem(nekoCoinLoot);

        // 废弃矿井箱子
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.MINESHAFT_CORRIDOR)
            .addItem(nekoCoinLoot);

        // 沙漠神殿箱子
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.PYRAMID_DESERT_CHEST)
            .addItem(nekoCoinLoot);

        // 丛林神庙箱子
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.PYRAMID_JUNGLE_CHEST)
            .addItem(nekoCoinLoot);

        // 强盗要塞箱子
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.STRONGHOLD_CORRIDOR)
            .addItem(nekoCoinLoot);

        // 强盗要塞十字路口箱子
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.STRONGHOLD_CROSSING)
            .addItem(nekoCoinLoot);
    }

    /**
     * 注册钓鱼战利品
     * 2% 概率钓到猫猫币
     */
    private static void registerFishingLoot() {
        ItemStack nekoCoin = GTITItemList.NekoCoin.get(1);
        if (nekoCoin == null) return;

        // 钓鱼战利品使用 WeightedRandomFishable
        // weight=2（约2%概率，普通鱼weight=60，宝藏weight=5）
        net.minecraft.util.WeightedRandomFishable nekoCoinFish = new net.minecraft.util.WeightedRandomFishable(
            nekoCoin,
            2);
        net.minecraftforge.common.FishingHooks.addFish(nekoCoinFish);
    }
}
