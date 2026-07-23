package com.miaokatze.gtit.signin;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 签到事件处理器
 * <p>
 * 监听玩家登录/登出/服务器 tick 事件：
 * <ul>
 * <li>登录：加载玩家签到数据 → 断签/跨月修正（有改动则保存）→ 推送同步包给客户端</li>
 * <li>登出：保存并从内存卸载玩家签到数据</li>
 * <li>tick：清空网络线程投递的签到任务队列 + 周期性跨日检查</li>
 * </ul>
 * 在 {@code CommonProxy.preInit()} 中注册到 FML 事件总线。
 */
public class DailySignInHandler {

    /**
     * 网络线程 → 服务器主线程的任务队列
     * <p>
     * 1.7.10 的 IMessageHandler 运行在 Netty IO 线程，不能直接操作钱包/背包/文件。
     * 签到请求（{@link SignInRequestPacket}）投递到此队列，由服务器 tick 在主线程消费。
     */
    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<>();

    /** tick 计数器（达到 {@link #TICK_CHECK_INTERVAL} 时做一次跨日检查） */
    private int tickCounter = 0;
    /** 跨日检查间隔（100 tick = 5 秒） */
    private static final int TICK_CHECK_INTERVAL = 100;

    /** 在线累计 tick 计数器（v1.7.6 G2③；达到 {@link #ONLINE_TICK_INTERVAL} 时做一次在线时间累计） */
    private int onlineTickCounter = 0;
    /** 在线累计间隔（1200 tick = 1 分钟，v1.7.6 用户确认的统计精度） */
    private static final int ONLINE_TICK_INTERVAL = 1200;

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
        DailySignInManager manager = DailySignInManager.INSTANCE;

        // 加载（或新建）数据并做断签/跨月修正，有改动立即落盘
        DailySignInData data = manager.getSignInData(playerId);
        boolean dirty = manager.applyDateCorrections(data);
        // v1.7.6 G2③：首次登录自动写入 firstJoinDate（只写一次，旧存档补记为当天）
        dirty |= manager.ensureFirstJoinDate(data);
        if (dirty) {
            manager.saveSignInData(playerId);
        }
        // 推送完整数据给客户端，供签到日历 GUI 渲染
        SignInNetworkManager.sendSyncToClient(player, data);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        // 保存并从内存卸载，避免离线玩家数据常驻
        DailySignInManager.INSTANCE.unloadSignInData(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 消费网络线程投递的签到任务（每 tick 清空，签到请求延迟 ≤1 tick）
        Runnable task;
        while ((task = SERVER_TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("执行签到任务失败", t);
            }
        }

        // 周期性跨日检查（日期变化时修正已加载玩家数据并重发同步）
        if (++tickCounter >= TICK_CHECK_INTERVAL) {
            tickCounter = 0;
            try {
                DailySignInManager.INSTANCE.checkDailyReset();
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("签到跨日检查失败", t);
            }
        }

        // v1.7.6 G2③：每分钟为在线玩家累计在线时长（跨日重置在累计方法内部完成）
        if (++onlineTickCounter >= ONLINE_TICK_INTERVAL) {
            onlineTickCounter = 0;
            try {
                DailySignInManager.INSTANCE.tickOnlineMinute();
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("在线时间累计失败", t);
            }
        }
    }
}
