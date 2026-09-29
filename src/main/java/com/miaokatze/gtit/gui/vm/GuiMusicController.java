package com.miaokatze.gtit.gui.vm;

import com.miaokatze.gtit.common.machine.neko.NekoMusicEventHandler;

/**
 * V2 GUI BGM 开关联动控制器（A01 蓝图 G5 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * ★R100 片 F（架构解耦）：GUI 打开标志的真相迁回 {@link NekoMusicEventHandler}（写点只有它的
 * {@code onGuiOpened}/{@code onGuiClosed} 两条生命周期口），本类退化为纯转发壳——common 侧
 * 事件处理器因此不再 import gui 包。BGM 打开/关闭联动的调用形制不变：宿主 build() 客户端分支
 * 调用 {@link #onGuiOpened()}，panel.onCloseAction 与 onDispose 兜底调用 {@link #close()}。
 */
public final class GuiMusicController {

    /**
     * GUI 打开联动：置打开标志并通知 BGM 事件处理器（标志写点在 handler 侧同方法内，顺序不变）
     */
    public void onGuiOpened() {
        NekoMusicEventHandler.onGuiOpened();
    }

    /**
     * 关闭猫猫售货机 GUI 的 BGM
     * <p>
     * 幂等语义保持：守卫与置标志随 ★R100 片 F 一并迁入
     * {@code NekoMusicEventHandler#onGuiClosed}（仅当 GUI 仍标记为打开时才执行清理，
     * 避免 onCloseAction 与 onDispose 重复调用导致淡出被反复重置、BGM 微弱未止的问题）。
     */
    public void close() {
        NekoMusicEventHandler.onGuiClosed();
    }
}
