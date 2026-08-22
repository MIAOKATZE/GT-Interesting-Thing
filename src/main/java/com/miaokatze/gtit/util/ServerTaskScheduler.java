package com.miaokatze.gtit.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 服务器主线程任务调度器（O2-03）
 * <p>
 * 收编原先散落在 {@code MailHandler} / {@code LotteryHandler} / {@code DailySignInHandler}
 * 的三份同构「网络线程 → 服务器主线程」任务队列——1.7.10 的 IMessageHandler 运行在
 * Netty IO 线程，不能直接操作背包/文件/机器，网络包处理器将操作投递到本队列，
 * 由服务器 tick（END 相位）在主线程统一消费，操作延迟 ≤1 tick。
 * <p>
 * 消费方（邮件/抽奖/签到/交易编辑四链路的网络包处理器）一律调用
 * {@link #scheduleServerTask}；{@code LotteryHandler.scheduleDelayedTask}
 * （抽奖动画专用的墙钟延迟队列）语义不同，不并入本类。
 * <p>
 * 顺补原三份队列的缺口：static 队列原先没有 server stop 清理点，停服残留任务
 * 会在单玩家连续开新世界时跨世界执行——由 {@link #clear()}（经
 * {@code CommonProxy.serverStopping} 调用；FMLServerStoppingEvent 不投递给
 * 事件总线监听器，见 {@code NekoWalletHandler} 类注释）在停服时清空。
 * 在 {@code CommonProxy.preInit()} 中注册到 FML 事件总线。
 */
public final class ServerTaskScheduler {

    /** 统一 logger（O2-B02：去中心化，名称与 LOG 同为 "gtit"，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 网络线程 → 服务器主线程的任务队列 */
    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<>();

    /** 唯一实例（事件总线注册用） */
    public static final ServerTaskScheduler INSTANCE = new ServerTaskScheduler();

    private ServerTaskScheduler() {}

    /**
     * 将任务调度到服务器主线程执行（供网络包处理器调用）
     *
     * @param task 待执行任务（下一 tick 开始时执行；null 直接忽略）
     */
    public static void scheduleServerTask(Runnable task) {
        if (task != null) {
            SERVER_TASKS.offer(task);
        }
    }

    /**
     * 清空待执行任务（停服时由 {@code CommonProxy.serverStopping} 调用，
     * 防止单玩家连续开新世界时残留任务跨世界执行）
     */
    public static void clear() {
        SERVER_TASKS.clear();
    }

    /**
     * 服务器 tick：消费网络线程投递的任务（每 tick 清空，操作延迟 ≤1 tick）。
     * <p>
     * 任务失败逐个捕获（一个任务异常不影响同批其余任务），统一日志不再按
     * 原三份队列的「邮件/抽奖/签到」文案误导排障。
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runnable task;
        while ((task = SERVER_TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                LOG.error("执行服务器主线程任务失败", t);
            }
        }
    }
}
