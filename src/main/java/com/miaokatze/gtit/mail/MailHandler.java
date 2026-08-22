package com.miaokatze.gtit.mail;

import java.util.UUID;

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
 * <li>tick：周期性全量保存（网络线程投递的任务自 O2-03 起统一走
 * {@code util.ServerTaskScheduler} 消费）</li>
 * </ul>
 * 在 {@code CommonProxy.preInit()} 中注册到 FML 事件总线。
 * <p>
 * 参照 {@code DailySignInHandler} 的事件分发模式。
 */
public class MailHandler {

    /** tick 计数器（达到 {@link #TICK_SAVE_INTERVAL} 时做一次全量保存） */
    private int tickCounter = 0;
    /** 周期保存间隔（6000 tick = 5 分钟，防异常退出丢数据） */
    private static final int TICK_SAVE_INTERVAL = 6000;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        UUID playerId = player.getUniqueID();
        MailManager manager = MailManager.INSTANCE;

        // 加载（或新建）数据，投递待发的首登/一次性奖励（内部有改动即落盘）
        boolean delivered = manager.deliverPendingRewards(playerId);
        // v1.7.6 G5：登录检测当日祝福（生日/纪念日/节日），防重键拦截重复投递，投递成功内部落盘
        delivered |= BlessingManager.INSTANCE.checkAndSend(player);
        if (delivered) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "你收到了新邮件，请到猫猫售货机查看！"));
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

        // 周期性全量保存（玩家数据 + 全局奖励表；网络线程投递的任务由 ServerTaskScheduler 统一消费）
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
