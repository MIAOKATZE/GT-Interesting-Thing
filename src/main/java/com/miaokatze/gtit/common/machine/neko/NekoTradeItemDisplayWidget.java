package com.miaokatze.gtit.common.machine.neko;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cubefury.vendingmachine.blocks.MTEVendingMachine;
import com.cubefury.vendingmachine.blocks.gui.DisplayType;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplay;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplayWidget;

/**
 * 猫猫售货机专用交易显示 Widget
 * <p>
 * 当产物只有猫猫币/闪烁猫猫币时，显示双图标：
 * - 主图标（fromItems 第一个物品）原大小显示
 * - 副图标（猫猫币）小号显示在右下角
 */
public class NekoTradeItemDisplayWidget extends TradeItemDisplayWidget {

    /** 副图标（小号猫猫币图标），null 表示无副图标 */
    private ItemStack secondaryIcon;

    public NekoTradeItemDisplayWidget(TradeItemDisplay display, MTEVendingMachine base, DisplayType displayType) {
        super(display, base, displayType);
    }

    /**
     * 设置副图标
     */
    public void setSecondaryIcon(ItemStack icon) {
        this.secondaryIcon = icon;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);

        // 渲染副图标（小号猫猫币图标）
        if (secondaryIcon != null && this.displayType == DisplayType.TILE) {
            // TILE 模式：右下角，8x8 大小
            // Widget 47x25，主图标在 (26,4) 16x16
            // 副图标放在主图标右下方
            GuiDraw.drawItem(secondaryIcon, 35, 13, 8.0f, 8.0f, context.getCurrentDrawingZ());
        } else if (secondaryIcon != null && this.displayType == DisplayType.LIST) {
            // LIST 模式：右下角，6x6 大小
            GuiDraw.drawItem(secondaryIcon, 145, 6, 6.0f, 6.0f, context.getCurrentDrawingZ());
        }
    }
}
