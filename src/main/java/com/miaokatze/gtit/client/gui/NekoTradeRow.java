package com.miaokatze.gtit.client.gui;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

/**
 * 交易行布局组件
 * <p>
 * 完美复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.TradeRow}，
 * 是一个简单的水平 {@link Flow} 子类，用于组织交易显示 Widget 的行布局。
 * <p>
 * 特性：
 * <ul>
 * <li>水平排列子组件（{@link GuiAxis#X}）</li>
 * <li>自动折叠被禁用的子组件，避免出现空白间隙</li>
 * <li>当所有子组件都被禁用时，自身也自动禁用</li>
 * </ul>
 */
public class NekoTradeRow extends Flow {

    /**
     * 创建一个水平排列的交易行
     * <p>
     * 默认配置：
     * <ul>
     * <li>{@code collapseDisabledChild(true)} - 折叠禁用的子组件</li>
     * <li>{@code setEnabledIf(...)} - 当至少一个子组件启用时自身才启用</li>
     * </ul>
     */
    public NekoTradeRow() {
        super(GuiAxis.X);
        // 折叠被禁用的子组件，避免出现空白间隙
        this.collapseDisabledChild(true)
            // 当所有子组件都被禁用时，自动禁用自身
            .setEnabledIf(
                r -> r.getChildren()
                    .stream()
                    .anyMatch(IWidget::isEnabled));
    }
}
