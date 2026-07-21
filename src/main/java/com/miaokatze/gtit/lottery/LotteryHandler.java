package com.miaokatze.gtit.lottery;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 抽奖事件处理器
 * <p>
 * 监听玩家登录/登出/服务器 tick 事件（参照 {@code DailySignInHandler} 模式）：
 * <ul>
 * <li>登录：预加载玩家所属团队的保底/历史数据，并向客户端推送全量同步
 * （卡池摘要 + 保底计数 + 最近历史）</li>
 * <li>登出：落盘团队保底/历史数据（数据常驻内存，团队其他成员可能仍在线）</li>
 * <li>tick：清空网络线程投递的抽奖任务队列 + 周期保存全部团队数据</li>
 * </ul>
 * 在 {@code CommonProxy.preInit()} 中注册到 FML 事件总线。
 */
public class LotteryHandler {

    /**
     * 网络线程 → 服务器主线程的任务队列
     * <p>
     * 1.7.10 的 IMessageHandler 运行在 Netty IO 线程，不能直接操作钱包/背包/机器。
     * 抽奖请求（{@link LotteryRequestPacket}）投递到此队列，由服务器 tick 在主线程消费。
     */
    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<>();

    /** tick 计数器（达到 {@link #SAVE_INTERVAL_TICKS} 时做一次全量保存） */
    private int tickCounter = 0;
    /** 周期保存间隔（6000 tick = 5 分钟，防崩溃丢保底/历史） */
    private static final int SAVE_INTERVAL_TICKS = 6000;

    /**
     * 将任务调度到服务器主线程执行（供网络包处理器调用）
     *
     * @param task 待执行任务（下一 tick 开始时执行）
     */
    public static void scheduleServerTask(Runnable task) {
        if (task != null) {
            SERVER_TASKS.offer(task);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        UUID playerId = player.getUniqueID();

        // 预加载团队保底/历史数据，并推送全量同步给客户端（供抽奖页渲染）
        LotteryManager.INSTANCE.loadPlayerTeamData(playerId);
        LotteryNetworkManager.sendSyncToClient(player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        // 落盘团队数据（不清内存——团队共享，其他成员可能仍在线）
        LotteryManager.INSTANCE.unloadPlayer(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 消费网络线程投递的抽奖任务（每 tick 清空，抽奖请求延迟 ≤1 tick）
        Runnable task;
        while ((task = SERVER_TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("执行抽奖任务失败", t);
            }
        }

        // 周期保存（保底/历史按团队落盘）
        if (++tickCounter >= SAVE_INTERVAL_TICKS) {
            tickCounter = 0;
            try {
                LotteryManager.INSTANCE.saveAll();
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("抽奖周期保存失败", t);
            }
        }
    }
}
