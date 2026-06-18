package com.miaokatze.gtit.common.machine.neko;

import java.util.Set;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Icon;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cubefury.vendingmachine.blocks.gui.MTEVendingMachineGui;
import com.cubefury.vendingmachine.gui.GuiTextures;
import com.cubefury.vendingmachine.trade.TradeCategory;

/**
 * 猫猫售货机自定义标签页按钮
 * <p>
 * 与 VendingPageButton 类似，但使用 ItemStack 图标替代 TradeCategory 纹理图标。
 * 支持高亮和悬停效果。
 */
public class NekoPageButton extends PageButton {

    private static final int ICON_MARGIN = 6;
    private final int index;
    private final Icon tabIcon;

    /**
     * @param index           标签页索引
     * @param controller      分页控制器
     * @param category        对应的 TradeCategory（用于高亮判断）
     * @param highlightedTabs 高亮标签页集合
     * @param iconStack       标签页图标 ItemStack
     */
    public NekoPageButton(int index, PagedWidget.Controller controller, TradeCategory category,
        Set<TradeCategory> highlightedTabs, ItemStack iconStack) {
        super(index, controller);
        this.index = index;

        IDrawable[] overlays = new IDrawable[2];
        // 高亮图标
        overlays[0] = new DynamicDrawable(() -> {
            if (highlightedTabs.contains(category)) {
                return GuiTextures.TAB_HIGHLIGHT.asIcon()
                    .size(20, 20);
            }
            return IDrawable.EMPTY;
        });
        // ItemStack 图标
        if (iconStack != null && iconStack.getItem() != null) {
            this.tabIcon = new Icon(new ItemDrawable(iconStack)).size(16, 16)
                .margin(ICON_MARGIN)
                .center();
        } else {
            // fallback: 使用 category 的默认纹理
            this.tabIcon = category.getTexture()
                .asIcon()
                .margin(ICON_MARGIN)
                .center();
        }
        overlays[1] = this.tabIcon;
        this.overlay(overlays);
    }

    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        MTEVendingMachineGui.lastPage = this.index;
        return super.onMousePressed(mouseButton);
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (this.isHovering()) {
            this.tabIcon.marginLeft(5)
                .marginRight(7);
        } else {
            this.tabIcon.marginLeft(ICON_MARGIN)
                .marginRight(ICON_MARGIN);
        }
        super.draw(context, widgetTheme);
    }
}
