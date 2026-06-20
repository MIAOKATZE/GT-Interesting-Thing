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
 * - 主图标（猫猫币/闪烁猫猫币）原大小显示，数字为获取数量
 * - 副图标（fromItems 第一个物品）小号显示在右下角，置于主图标图层之上
 */
public class NekoTradeItemDisplayWidget extends TradeItemDisplayWidget {

    /** 副图标（fromItems 第一个物品的小号图标），null 表示无副图标 */
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

        // 渲染副图标（fromItems 小号物品图标），在主图标图层之上
        if (secondaryIcon != null && this.displayType == DisplayType.TILE) {
            // TILE 模式：右下角，10x10 大小（8 * 1.2 ≈ 10）
            // Widget 47x25，主图标在 (26,4) 16x16
            // 副图标放在主图标右下方
            GuiDraw.drawItem(secondaryIcon, 33, 11, 10.0f, 10.0f, context.getCurrentDrawingZ());
        } else if (secondaryIcon != null && this.displayType == DisplayType.LIST) {
            // LIST 模式：右下角，7x7 大小（6 * 1.2 ≈ 7）
            GuiDraw.drawItem(secondaryIcon, 144, 5, 7.0f, 7.0f, context.getCurrentDrawingZ());
        }
    }
}
