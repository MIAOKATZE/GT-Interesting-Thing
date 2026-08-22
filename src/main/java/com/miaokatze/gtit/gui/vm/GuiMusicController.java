package com.miaokatze.gtit.gui.vm;

import com.miaokatze.gtit.common.machine.neko.NekoMusicEventHandler;

/**
 * V2 GUI BGM 开关联动控制器（A01 蓝图 G5 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * 接管 GUI 打开标志 {@link #isV2GuiOpen}（原 NekoVMGuiV2 公有静态字段，
 * {@code NekoMusicEventHandler} 的外部消费点随本类迁移）与 BGM 打开/关闭联动：
 * 宿主 build() 客户端分支调用 {@link #onGuiOpened()}，panel.onCloseAction 与
 * onDispose 兜底调用 {@link #close()}。
 */
public final class GuiMusicController {

    /** V2 GUI 是否打开（客户端，供 NekoMusicEventHandler 检测 GUI 状态） */
    public static boolean isV2GuiOpen = false;

    /**
     * GUI 打开联动：置打开标志并通知 BGM 事件处理器
     */
    public void onGuiOpened() {
        isV2GuiOpen = true;
        // 通知 NekoMusicEventHandler GUI 已打开
        NekoMusicEventHandler.onGuiOpened();
    }

    /**
     * 关闭猫猫售货机 GUI 的 BGM
     * <p>
     * 幂等方法：仅当 GUI 仍标记为打开时才执行清理，避免 onCloseAction 与 onDispose 重复调用
     * 导致淡出被反复重置、BGM 微弱未止的问题。
     */
    public void close() {
        if (!isV2GuiOpen) return;
        isV2GuiOpen = false;
        // 通知 NekoMusicEventHandler GUI 已关闭，触发淡出
        NekoMusicEventHandler.onGuiClosed();
    }
}
