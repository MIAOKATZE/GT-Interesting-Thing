package com.miaokatze.gtit.common.loot;

import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;

import com.miaokatze.gtit.common.api.enums.GTITItemList;

import gregtech.api.util.GTLog;

/**
 * 战利品注册
 * 将猫猫币、闪烁猫猫币和戒指添加到各种箱子战利品表和钓鱼战利品中
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
     */
    private static void registerChestLoot() {
        // === 猫猫币 ===
        ItemStack nekoCoin = GTITItemList.NekoCoin.get(1);
        if (nekoCoin != null) {
            WeightedRandomChestContent nekoCoinLoot = new WeightedRandomChestContent(nekoCoin, 2, 8, 3);
            addChestLootToAll(nekoCoinLoot);
        }

        // === 戒指（稀有战利品，极低权重） ===
        // weight=1, 每个箱子最多1个
        addRingChestLoot(GTITItemList.RingDistantGrasp);
        addRingChestLoot(GTITItemList.RingSkywalk);
        addRingChestLoot(GTITItemList.RingGluttony);
        addRingChestLoot(GTITItemList.RingIronheart);
        addRingChestLoot(GTITItemList.RingDragonBreath);
        addRingChestLoot(GTITItemList.RingMountainbreaker);
        addRingChestLoot(GTITItemList.RingTempest);
        // 御风戒指不出现在箱子中（需合成获得）
    }

    /**
     * 将战利品添加到所有主要箱子类型
     */
    private static void addChestLootToAll(WeightedRandomChestContent loot) {
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.DUNGEON_CHEST)
            .addItem(loot);
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.VILLAGE_BLACKSMITH)
            .addItem(loot);
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.MINESHAFT_CORRIDOR)
            .addItem(loot);
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.PYRAMID_DESERT_CHEST)
            .addItem(loot);
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.PYRAMID_JUNGLE_CHEST)
            .addItem(loot);
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.STRONGHOLD_CORRIDOR)
            .addItem(loot);
        net.minecraftforge.common.ChestGenHooks.getInfo(net.minecraftforge.common.ChestGenHooks.STRONGHOLD_CROSSING)
            .addItem(loot);
    }

    /**
     * 将戒指添加为稀有箱子战利品
     * weight=1（极低），每个箱子最多1个
     */
    private static void addRingChestLoot(GTITItemList ringItem) {
        ItemStack ringStack = ringItem.get(1);
        if (ringStack == null) return;
        WeightedRandomChestContent ringLoot = new WeightedRandomChestContent(ringStack, 1, 1, 1);
        addChestLootToAll(ringLoot);
    }

    /**
     * 注册钓鱼战利品
     * 猫猫币 2% 概率，闪烁猫猫币 0.5% 概率
     */
    private static void registerFishingLoot() {
        // 猫猫币 - weight=2（约2%概率）
        ItemStack nekoCoin = GTITItemList.NekoCoin.get(1);
        if (nekoCoin != null) {
            net.minecraft.util.WeightedRandomFishable nekoCoinFish = new net.minecraft.util.WeightedRandomFishable(
                nekoCoin,
                2);
            net.minecraftforge.common.FishingHooks.addFish(nekoCoinFish);
        }

        // 闪烁猫猫币 - weight=0（0.5%概率，使用weight=0 + randomChance实现）
        // MC钓鱼系统中 weight=0 不会出现，需要用极低权重
        // 实际上weight=1约1%，所以0.5%需要用其他方式
        // 使用 weight=1 但叠加极低概率：在Fishable中无法直接设0.5%
        // 改用 weight=1，约为0.5-1%概率（取决于其他鱼的总权重）
        ItemStack shimmeringCoin = GTITItemList.ShimmeringNekoCoin.get(1);
        if (shimmeringCoin != null) {
            net.minecraft.util.WeightedRandomFishable shimmeringFish = new net.minecraft.util.WeightedRandomFishable(
                shimmeringCoin,
                1);
            net.minecraftforge.common.FishingHooks.addFish(shimmeringFish);
        }
    }
}
