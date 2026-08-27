package com.miaokatze.gtit.common.loot;

import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;

import com.miaokatze.gtit.common.api.enums.GTITItemList;

import gregtech.api.util.GTLog;

/**
 * 战利品注册
 * 将猫猫币和戒指添加到各种箱子战利品表中
 * <p>
 * 猫猫币/闪烁猫猫币的钓鱼获取已改为"玩家每次成功钓到鱼后服务端掷概率附赠"
 * （见 com.miaokatze.gtit.mixin.MixinEntityFishHook），不再进入全局钓鱼战利品表，
 * 以免 GT 工业鱼塘与 GT++ 鱼陷阱等复用 FishingHooks 表的机器批量刷币。
 */
public class LootRegistrar {

    /**
     * 注册所有战利品
     */
    public static void init() {
        registerChestLoot();
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
}
