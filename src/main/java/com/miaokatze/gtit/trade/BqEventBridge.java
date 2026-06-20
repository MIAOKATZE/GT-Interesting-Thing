package com.miaokatze.gtit.trade;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraftforge.common.MinecraftForge;

import com.cubefury.vendingmachine.VendingMachine;
import com.cubefury.vendingmachine.integration.betterquesting.BqAdapter;
import com.cubefury.vendingmachine.network.handlers.NetSatisfiedQuestSync;
import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import betterquesting.api.events.QuestEvent;
import betterquesting.api.questing.IQuest;
import betterquesting.questing.QuestDatabase;

/**
 * BQ 任务事件桥接器
 * <p>
 * VM 的 BqAdapter 是"半成品"：setQuestFinished()/setQuestUnfinished() 从未被调用，
 * 导致 playerSatisfiedCache 永远为空，checkPlayerCompletedQuest() 永远返回 false。
 * <p>
 * 本类监听 BQ 的 QuestEvent，在任务完成/重置时更新 BqAdapter 的缓存，
 * 使 VM 的条件系统能正常工作。
 * <p>
 * 同时在玩家登录时同步已完成的任务到缓存（处理跨会话的任务完成状态）。
 */
public class BqEventBridge {

    /**
     * 注册事件监听器
     * 仅在 BQ 已加载时注册，避免类加载崩溃
     */
    public static void register() {
        if (!VendingMachine.isBqLoaded) {
            GTInterestingThing.LOG.info("BetterQuesting 未加载，跳过 BqEventBridge 注册");
            return;
        }
        BqEventBridge bridge = new BqEventBridge();
        MinecraftForge.EVENT_BUS.register(bridge);
        FMLCommonHandler.instance()
            .bus()
            .register(bridge);
        GTInterestingThing.LOG.info("BqEventBridge 已注册，监听 BQ 任务事件和玩家登录");
    }

    /**
     * 监听 BQ 任务事件
     * <p>
     * QuestEvent.Type:
     * - COMPLETED: 玩家完成任务 → 调用 setQuestFinished
     * - RESET: 任务被重置 → 调用 setQuestUnfinished
     * - UPDATED: 任务有更新（忽略，不需要处理）
     */
    @SubscribeEvent
    @Optional.Method(modid = "betterquesting")
    public void onQuestEvent(QuestEvent event) {
        UUID playerId = event.getPlayerID();
        Set<UUID> questIds = event.getQuestIDs();

        if (playerId == null || questIds == null || questIds.isEmpty()) {
            return;
        }

        switch (event.getType()) {
            case COMPLETED:
                for (UUID questId : questIds) {
                    BqAdapter.INSTANCE.setQuestFinished(playerId, questId);
                    GTInterestingThing.LOG.info("BQ 任务完成: player={}, questId={}", playerId, questId);
                }
                syncToClients();
                break;
            case RESET:
                for (UUID questId : questIds) {
                    BqAdapter.INSTANCE.setQuestUnfinished(playerId, questId);
                    GTInterestingThing.LOG.info("BQ 任务重置: player={}, questId={}", playerId, questId);
                }
                syncToClients();
                break;
            default:
                break;
        }
    }

    /**
     * 监听玩家登录事件
     * <p>
     * 在玩家登录时，检查所有绑定了 BQ 任务的猫猫币交易，
     * 如果玩家已完成对应任务，更新 BqAdapter 缓存。
     * <p>
     * 这解决了跨会话的任务完成状态同步问题：
     * BQ 的 QuestEvent 只在任务状态变化时触发，
     * 玩家之前会话中完成的任务不会在登录时触发事件。
     */
    @SubscribeEvent
    @Optional.Method(modid = "betterquesting")
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player == null) return;

        UUID playerId = event.player.getUniqueID();
        Set<UUID> syncedQuests = new HashSet<>();

        // 遍历所有猫猫币交易，检查 BQ 任务完成状态
        for (Map.Entry<UUID, NekoTradeRegistry.NekoTradeInfo> entry : NekoTradeRegistry.NEKO_TRADES.entrySet()) {
            NekoTradeRegistry.NekoTradeInfo info = entry.getValue();
            if (info.bqQuestId == null || info.bqQuestId.isEmpty()) continue;

            UUID questId = NekoTradeRegistry.parseBqQuestIdPublic(info.bqQuestId);
            if (questId == null || syncedQuests.contains(questId)) continue;

            // 直接查询 BQ API 获取任务完成状态
            try {
                IQuest quest = QuestDatabase.INSTANCE.get(questId);
                if (quest != null && quest.isComplete(playerId)) {
                    BqAdapter.INSTANCE.setQuestFinished(playerId, questId);
                    syncedQuests.add(questId);
                    GTInterestingThing.LOG.info("玩家登录同步 BQ 任务: player={}, questId={}", playerId, questId);
                }
            } catch (Exception e) {
                GTInterestingThing.LOG.warn("查询 BQ 任务完成状态失败: questId={}, player={}", questId, playerId, e);
            }
        }

        if (!syncedQuests.isEmpty()) {
            syncToClients();
            GTInterestingThing.LOG.info("玩家 {} 登录同步了 {} 个已完成的 BQ 任务", playerId, syncedQuests.size());
        }
    }

    /**
     * 同步缓存到所有客户端
     */
    private void syncToClients() {
        try {
            NetSatisfiedQuestSync.sendSync();
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("同步 BQ 任务缓存到客户端失败", e);
        }
    }
}
