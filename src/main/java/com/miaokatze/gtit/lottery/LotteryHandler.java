package com.miaokatze.gtit.lottery;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 * <li>tick：消费抽奖延迟出货任务 + 周期保存全部团队数据（网络线程投递的
 * 立即任务自 O2-03 起统一走 {@code util.ServerTaskScheduler} 消费）</li>
 * </ul>
 * 在 {@code CommonProxy.preInit()} 中注册到 FML 事件总线。
 */
public class LotteryHandler {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /**
     * 延迟任务（到期时间 + 任务体）
     * <p>
     * 用于「服务端延迟出货」：抽奖结果包先行下发驱动客户端轮盘动画，
     * 奖品（物品与货币均构建为物品堆落出货槽，v1.7.10 起）延迟到动画时长 + 余量后再执行，
     * 使产物下落动画与轮盘停格对齐，且保底/历史同步不提前剧透。
     */
    private static class DelayedTask {

        /** 到期时间（System.currentTimeMillis） */
        final long dueMs;
        /** 到期后执行的任务（服务器主线程） */
        final Runnable task;

        DelayedTask(long dueMs, Runnable task) {
            this.dueMs = dueMs;
            this.task = task;
        }
    }

    /** 延迟任务队列（由 {@link #onServerTick} 在主线程消费到期任务） */
    private static final Queue<DelayedTask> DELAYED_TASKS = new ConcurrentLinkedQueue<>();

    /** tick 计数器（达到 {@link #SAVE_INTERVAL_TICKS} 时做一次全量保存） */
    private int tickCounter = 0;
    /** 周期保存间隔（6000 tick = 5 分钟，防崩溃丢保底/历史） */
    private static final int SAVE_INTERVAL_TICKS = 6000;

    /**
     * 将任务调度到指定延迟后的服务器主线程执行
     * <p>
     * 抽奖动画专用的墙钟延迟语义，故留在本类（O2-03 只收编立即任务队列）；
     * 由 {@link #onServerTick} 每 tick 检查并消费全部到期任务。
     * 典型用途：抽奖延迟出货（动画时长 + 150ms 余量）。
     *
     * @param delayMs 延迟毫秒数（≤0 时下一 tick 执行）
     * @param task    到期后执行的任务
     */
    public static void scheduleDelayedTask(long delayMs, Runnable task) {
        if (task == null) return;
        DELAYED_TASKS.offer(new DelayedTask(System.currentTimeMillis() + Math.max(0, delayMs), task));
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

        // 消费到期的延迟任务（抽奖延迟出货等；单 tick 内可能到期多个，逐个消费；
        // 网络线程投递的立即任务由 ServerTaskScheduler 统一消费）
        long nowMs = System.currentTimeMillis();
        java.util.Iterator<DelayedTask> it = DELAYED_TASKS.iterator();
        while (it.hasNext()) {
            DelayedTask delayed = it.next();
            if (delayed.dueMs > nowMs) continue; // 未到期，留给后续 tick
            it.remove();
            try {
                delayed.task.run();
            } catch (Throwable t) {
                LOG.error("执行抽奖延迟任务失败", t);
            }
        }

        // 周期保存（保底/历史按团队落盘）
        if (++tickCounter >= SAVE_INTERVAL_TICKS) {
            tickCounter = 0;
            try {
                LotteryManager.INSTANCE.saveAll();
            } catch (Throwable t) {
                LOG.error("抽奖周期保存失败", t);
            }
        }
    }
}
