package com.miaokatze.gtit.common.items.pocket;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import com.miaokatze.gtit.common.items.pocket.distill.PocketDistillDriver;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * 口袋服务端内存状态的<b>真挂钩</b>（R57c④：没有它，{@code PocketChannelManager} 自己承诺的
 * "防跨存档残留通道"与 {@code PocketSessions} 的"会话不泄漏"都落空）。
 * <p>
 * 两条钩子各管一件事，都在服务器主线程（FML 的 {@code PlayerEvent} 与 mod 生命周期同线程派发）：
 * <ol>
 * <li><b>玩家离线</b>：先让会话落盘（{@link PocketSessions#forget}，顺序是"落盘 → 摘除"），
 * 再摘通道条目与蒸馏时钟。<b>不</b>在离线时清 {@code PocketCellProbe} 的坐标表——那张表是
 * 元件↔容器 的全局观测，与单个玩家无关，且元件可能还在原地（重登即自愈）。</li>
 * <li><b>停服/换档</b>（由 {@code CommonProxy.serverStopping} 转发，{@code FMLServerStoppingEvent}
 * 在本映射下不投递给总线监听器，与 {@code LegacyCellReminderScheduler}/{@code HardcoreEnforcer}
 * 同一处理形态）：全部会话落盘 + 通道表与蒸馏时钟复位，防单机连续开新世界时把上一档的
 * 30 秒通道与进度带进新档。</li>
 * </ol>
 * 通道一拍与蒸馏推进本身不在这里：宿主是 {@code Item.onUpdate}（R57b/R57c，实测通路见
 * {@code InventoryPlayer#decrementAnimations}），本类只负责"什么时候该把它们的东西清掉"。
 */
public final class PocketLifecycleHandler {

    public static final PocketLifecycleHandler INSTANCE = new PocketLifecycleHandler();

    private PocketLifecycleHandler() {}

    /** 玩家离线：会话先落盘再摘除，通道条目与蒸馏时钟一并清；顺带做一次僵尸会话兜底扫描。 */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        final UUID uuid = playerId(event.player);
        if (uuid == null) {
            return;
        }
        PocketSessions.forget(uuid);
        PocketChannelManager.INSTANCE.forget(uuid);
        PocketDistillDriver.forget(uuid);
        // 兜底：口袋被塞进箱子（不再被 tick）的会话，driver 永远等不到它的那一拍 ⇒ 只能这样收。
        // 以"有人离线"为节拍（低频事件），代价是一次 O(在线会话数) 扫描，且每条都是先落盘再摘。
        PocketSessions.sweepOrphans();
    }

    /**
     * 停服/换档复位（{@code CommonProxy.serverStopping} 调用）。
     * <p>
     * 顺带扫一次"界面已关且口袋已不在玩家身上"的僵尸会话：这条路径 {@code Item.onUpdate} 永远
     * 等不到（不再被 tick），只能由生命周期钩子收（{@link PocketSessions#sweepOrphans()}）。
     */
    public static void onServerStopping() {
        PocketSessions.clearAll();
        PocketChannelManager.INSTANCE.reset();
        PocketDistillDriver.reset();
        PocketCellProbe.INSTANCE.reset();
    }

    private static UUID playerId(EntityPlayer player) {
        if (player == null || player.getGameProfile() == null) {
            return null;
        }
        return player.getGameProfile()
            .getId();
    }
}
