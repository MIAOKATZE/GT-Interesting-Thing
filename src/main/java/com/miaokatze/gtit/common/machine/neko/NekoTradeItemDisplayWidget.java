package com.miaokatze.gtit.common.machine.neko;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cubefury.vendingmachine.blocks.MTEVendingMachine;
import com.cubefury.vendingmachine.blocks.gui.DisplayType;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplay;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplayWidget;
import com.cubefury.vendingmachine.gui.GuiTextures;

/**
 * 猫猫售货机专用交易显示 Widget
 * <p>
 * 所有交易都显示双图标：
 * - 主图标（toItems 第一个物品）原大小显示，数字为获取数量
 * - 副图标（fromItems 第一个物品或猫猫币）小号显示在右下角，置于所有图层之上
 * <p>
 * 收藏交易显示星标图标。
 */
public class NekoTradeItemDisplayWidget extends TradeItemDisplayWidget {

    /** 副图标（fromItems 第一个物品或猫猫币的小号图标），null 表示无副图标 */
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

        // 渲染收藏星标图标
        TradeItemDisplay currentDisplay = this.getDisplay();
        if (currentDisplay != null && currentDisplay.isFavourite) {
            if (this.displayType == DisplayType.TILE) {
                GuiTextures.FAVOURITE_SPRITE.draw(context, 4, 4, 6, 6, widgetTheme.getTheme());
            } else if (this.displayType == DisplayType.LIST) {
                GuiTextures.FAVOURITE_SPRITE.draw(context, 139, 2, 10, 10, widgetTheme.getTheme());
            }
        }

        // 渲染副图标，使用更高的 Z 值确保在所有 overlay 之上
        if (secondaryIcon != null && this.displayType == DisplayType.TILE) {
            // TILE 模式：右下角，10x10 大小
            // Widget 47x25，主图标在 (26,4) 16x16
            // 副图标放在主图标右下方，Z+200 确保在 overlay 之上
            GuiDraw.drawItem(secondaryIcon, 33, 11, 10.0f, 10.0f, context.getCurrentDrawingZ() + 200);
        } else if (secondaryIcon != null && this.displayType == DisplayType.LIST) {
            // LIST 模式：右下角，7x7 大小
            GuiDraw.drawItem(secondaryIcon, 144, 5, 7.0f, 7.0f, context.getCurrentDrawingZ() + 200);
        }
    }
}
