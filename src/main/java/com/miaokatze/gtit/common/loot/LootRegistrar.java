package com.miaokatze.gtit.common.loot;

import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.api.enums.GTITItemList;

/**
 * 战利品注册
 * 将猫猫币和戒指添加到各种箱子战利品表中
 * <p>
 * 猫猫币/闪烁猫猫币的钓鱼获取已改为"玩家每次成功钓到鱼后服务端掷概率附赠"
 * （见 com.miaokatze.gtit.mixin.MixinEntityFishHook），不再进入全局钓鱼战利品表，
 * 以免 GT 工业鱼塘与 GT++ 鱼陷阱等复用 FishingHooks 表的机器批量刷币。
 */
public class LootRegistrar {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /**
     * 注册所有战利品
     */
    public static void init() {
        registerChestLoot();
        LOG.info("[GTIT] 战利品注册完成");
    }

    /**
     * TooMuchLoot 在 ServerStarting 按整合包 XML 替换箱子池，晚于 init/loadComplete。
     * 整波 ServerStarting 结束后补齐被覆盖的物品；保留 init 注册，供新世界早期生成使用。
     * 单机切换世界也会调用，因此依据当前池内容去重，不能用进程级已注册标记。
     */
    public static void onServerStarted() {
        registerChestLoot();
        LOG.info("[GTIT] 服务器箱子战利品补齐完成");
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
        // weight=1，每次抽中生成1个（同一箱子的多次抽取仍可能重复命中）
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
    static void addChestLootToAll(WeightedRandomChestContent loot) {
        addChestLootIfMissing(ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST), loot);
        addChestLootIfMissing(ChestGenHooks.getInfo(ChestGenHooks.VILLAGE_BLACKSMITH), loot);
        addChestLootIfMissing(ChestGenHooks.getInfo(ChestGenHooks.MINESHAFT_CORRIDOR), loot);
        addChestLootIfMissing(ChestGenHooks.getInfo(ChestGenHooks.PYRAMID_DESERT_CHEST), loot);
        addChestLootIfMissing(ChestGenHooks.getInfo(ChestGenHooks.PYRAMID_JUNGLE_CHEST), loot);
        addChestLootIfMissing(ChestGenHooks.getInfo(ChestGenHooks.STRONGHOLD_CORRIDOR), loot);
        addChestLootIfMissing(ChestGenHooks.getInfo(ChestGenHooks.STRONGHOLD_CROSSING), loot);
    }

    private static void addChestLootIfMissing(ChestGenHooks pool, WeightedRandomChestContent loot) {
        // 使用独立随机源查询 Forge 的实际内容，不消耗世界随机序列；已有配置的权重/数量原样保留。
        for (WeightedRandomChestContent existing : pool.getItems(new Random(0))) {
            if (existing.theItemId.isItemEqual(loot.theItemId)
                && ItemStack.areItemStackTagsEqual(existing.theItemId, loot.theItemId)) {
                return;
            }
        }
        pool.addItem(loot);
    }

    /**
     * 将戒指添加为稀有箱子战利品
     * weight=1（极低），每次抽中生成1个
     */
    private static void addRingChestLoot(GTITItemList ringItem) {
        ItemStack ringStack = ringItem.get(1);
        if (ringStack == null) return;
        WeightedRandomChestContent ringLoot = new WeightedRandomChestContent(ringStack, 1, 1, 1);
        addChestLootToAll(ringLoot);
    }
}
