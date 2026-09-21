package com.miaokatze.gtit.gui.pocket;

import com.cleanroommc.modularui.screen.ModularContainer;

/**
 * 口袋面板的 Container（只承担一件事：<b>关屏写状态的落点</b>）。
 * <p>
 * 为什么必须是这个钩子（R35）：MUI2 在 {@code EntityPlayerMPMixin:22-27} 里把
 * {@code Container#onContainerClosed} 的"真关屏"事件转成 {@code onModularContainerClosed()}，
 * 与 {@code onContainerClosed} 本身的区别是 <b>NEI/HEI 顶屏不会误触发</b>
 * （{@code ModularContainer.java:104-108} 的 javadoc 明写这一点）。
 * 若把写档放在 {@code addCloseListener}，玩家只是打开 NEI 看一眼就会把会话缓冲落盘一次。
 * <p>
 * 本类由 {@link NekoPocketPanel} 经 {@code UISettings#customContainer} 提供
 * （{@code GuiManager.java:81/112/143} 双端都读该 supplier ⇒ 双端同一个类，不破坏 R32）。
 * <b>不覆写</b> {@code slotClick} 与 {@code transferStackInSlot}，也<b>不手工加槽</b>：
 * 槽位注册与点击处理全交 MUI2（slice-s4-brief §5 判据里"口袋不手工加槽"是硬口径）。
 */
public class NekoPocketContainer extends ModularContainer {

    private final NekoPocketPanel panel;

    public NekoPocketContainer(NekoPocketPanel panel) {
        this.panel = panel;
    }

    @Override
    public void onModularContainerClosed() {
        super.onModularContainerClosed();
        // 落点集中：脏标记判定、open 位清零、承载格重定位与"找不到只 warn 不落盘"
        // 全在 NekoPocketPanel.onContainerClosed()（R35 四道防御 + R24 清理点集中一处）
        panel.onContainerClosed();
    }
}
