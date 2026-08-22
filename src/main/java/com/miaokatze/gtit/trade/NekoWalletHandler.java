package com.miaokatze.gtit.trade;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 猫猫币钱包事件处理器（BUG B1 生命周期接入）
 * <p>
 * 监听玩家登出与服务器 tick 事件（参照 {@code MailHandler} / {@code LotteryHandler} 模式）：
 * <ul>
 * <li>登出：保存并从内存卸载个人钱包（{@link NekoWalletManager#unloadWallet}，
 * 此前该方法全项目零调用，个人钱包下线后常驻内存）</li>
 * <li>tick：冲刷余额推送脏标记（约 100ms 合帧，见
 * {@link NekoWalletManager#flushBalanceNotifications}）+ 周期落盘脏钱包
 * （兜底覆盖未走显式 saveWallet 的入账路径）</li>
 * </ul>
 * 服务器停止钩子（saveAll/unloadAll）走 FML 生命周期事件
 * （{@code CommonProxy.serverStopping/serverStopped}），不在此注册——
 * FMLServerStoppingEvent/FMLServerStoppedEvent 只投递给 @Mod.EventHandler，不投递给事件总线监听器。
 * 在 {@code CommonProxy.preInit()} 中注册到 FML 事件总线。
 */
public class NekoWalletHandler {

    /** tick 计数器（达到 {@link #SAVE_INTERVAL_TICKS} 时落盘脏钱包） */
    private int tickCounter = 0;
    /** 周期落盘间隔（6000 tick = 5 分钟，与邮件/抽奖周期保存口径一致） */
    private static final int SAVE_INTERVAL_TICKS = 6000;

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        // 登出：保存并卸载个人钱包，避免离线玩家钱包常驻内存
        try {
            NekoWalletManager.INSTANCE.unloadWallet(event.player.getUniqueID());
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("登出卸载猫猫币钱包失败: " + event.player.getUniqueID(), t);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 余额推送节流冲刷（内部按 ~100ms 间隔合帧，无脏标记时零开销）
        try {
            NekoWalletManager.INSTANCE.flushBalanceNotifications();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("冲刷钱包余额推送失败", t);
        }

        // 周期落盘脏钱包（兜底：未走显式 saveWallet 的入账路径不丢账）
        if (++tickCounter >= SAVE_INTERVAL_TICKS) {
            tickCounter = 0;
            try {
                NekoWalletManager.INSTANCE.saveDirtyWallets();
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("猫猫币钱包周期保存失败", t);
            }
        }
    }
}
