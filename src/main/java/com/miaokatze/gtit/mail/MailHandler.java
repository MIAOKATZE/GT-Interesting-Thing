package com.miaokatze.gtit.mail;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 邮件事件处理器
 * <p>
 * 监听玩家登录/登出/服务器 tick 事件：
 * <ul>
 * <li>登录：加载玩家邮箱数据 → 投递待发的首登/一次性奖励 → 推送同步包给客户端</li>
 * <li>登出：保存并从内存卸载玩家邮箱数据</li>
 * <li>tick：清空网络线程投递的邮件任务队列 + 周期性全量保存</li>
 * </ul>
 * 在 {@code CommonProxy.preInit()} 中注册到 FML 事件总线。
 * <p>
 * 参照 {@code DailySignInHandler} 的事件分发模式。
 */
public class MailHandler {

    /**
     * 网络线程 → 服务器主线程的任务队列
     * <p>
     * 1.7.10 的 IMessageHandler 运行在 Netty IO 线程，不能直接操作背包/文件。
     * 邮件操作请求（{@link MailActionPacket}）投递到此队列，由服务器 tick 在主线程消费。
     */
    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<>();

    /** tick 计数器（达到 {@link #TICK_SAVE_INTERVAL} 时做一次全量保存） */
    private int tickCounter = 0;
    /** 周期保存间隔（6000 tick = 5 分钟，防异常退出丢数据） */
    private static final int TICK_SAVE_INTERVAL = 6000;

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
        MailManager manager = MailManager.INSTANCE;

        // 加载（或新建）数据，投递待发的首登/一次性奖励（内部有改动即落盘）
        boolean delivered = manager.deliverPendingRewards(playerId);
        if (delivered) {
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GOLD + "你收到了新邮件，请到猫猫售货机查看！"));
        }
        // 推送完整数据给客户端，供邮件 GUI 渲染
        MailNetworkManager.sendSyncToClient(player, manager.getMailData(playerId));
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        // 保存并从内存卸载，避免离线玩家数据常驻
        MailManager.INSTANCE.unloadMailData(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 消费网络线程投递的邮件任务（每 tick 清空，操作延迟 ≤1 tick）
        Runnable task;
        while ((task = SERVER_TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("执行邮件任务失败", t);
            }
        }

        // 周期性全量保存（玩家数据 + 全局奖励表）
        if (++tickCounter >= TICK_SAVE_INTERVAL) {
            tickCounter = 0;
            try {
                MailManager.INSTANCE.saveAll();
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("邮件周期保存失败", t);
            }
        }
    }
}
