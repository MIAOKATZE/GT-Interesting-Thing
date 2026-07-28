package com.miaokatze.gtit.client.gui;

import com.cleanroommc.modularui.widgets.PagedWidget;

/**
 * NekoPagedWidget：覆写 canHover/canHoverThrough，避免 PagedWidget 的 area 覆盖背包栏时拦截 hover 检测。
 * <p>
 * v1.7.18 修复"物品放不回背包"问题：
 * PagedWidget 默认 canHover()=true, canHoverThrough()=false（IWidget 接口默认值），
 * 当 PagedWidget 的 area 覆盖背包栏时，ModularGuiContext.getHoveredWidgets() 会在 PagedWidget 处 break，
 * 导致背包槽无法成为 theSlot，vanilla 点击失效。
 * <p>
 * 覆写后 canHover()=false 使 PagedWidget 不进入 newHovered，
 * canHoverThrough()=true 允许 hover 穿透到下层 widget（背包槽）。
 */
public class NekoPagedWidget<W extends NekoPagedWidget<W>> extends PagedWidget<W> {

    @Override
    public boolean canHover() {
        return false; // 不拦截 hover，让事件穿透到下层 widget
    }

    @Override
    public boolean canHoverThrough() {
        return true; // 允许 hover 穿透
    }
}
