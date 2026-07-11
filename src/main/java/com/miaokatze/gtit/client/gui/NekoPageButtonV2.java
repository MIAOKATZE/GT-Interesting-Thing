package com.miaokatze.gtit.client.gui;

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
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;

/**
 * V2 猫猫售货机标签页按钮
 * <p>
 * 全量复刻 VM {@code VendingPageButton} 的视觉效果 + V1 {@code NekoPageButton} 的 ItemStack 图标灵活性，
 * 完全脱离 VM 依赖，使用 GTIT 本地组件。
 * <p>
 * <b>与 V1 NekoPageButton 的差异</b>：
 * <ul>
 * <li>使用 {@link NekoGuiTextures#TAB_HIGHLIGHT} 替代 VM {@code GuiTextures.TAB_HIGHLIGHT}</li>
 * <li>使用 {@link NekoTradeCategory} 替代 VM {@code TradeCategory}（NekoTradeCategory 无 getTexture()，必须用 ItemStack 图标）</li>
 * <li>使用本类静态字段 {@link #lastPage} 替代 VM {@code MTEVendingMachineGui.lastPage}</li>
 * <li>无图标时用空图标替代 V1 的 {@code category.getTexture()} fallback（因 NekoTradeCategory 无纹理）</li>
 * </ul>
 * <p>
 * <b>与 VM VendingPageButton 的差异</b>：
 * <ul>
 * <li>VM 用 {@code TradeCategory.getTexture()} 作为图标，V2 用 ItemStack（更灵活，可自定义图标）</li>
 * <li>VM 用 {@code List<TradeCategory> + index} 查找高亮分类，V2 直接传入 category（更直观）</li>
 * <li>保留 VM 的不对称 margin 悬停效果（悬停时图标轻微右移，增强交互反馈）</li>
 * </ul>
 *
 * @see NekoGuiTextures#TAB_HIGHLIGHT
 * @see NekoTradeCategory
 */
public class NekoPageButtonV2 extends PageButton {

    /** 图标左边距（悬停时减 1，产生轻微右移动画） */
    private static final int ICON_MARGIN_LEFT = 7;
    /** 图标右边距（悬停时加 1，补偿左移，保持居中） */
    private static final int ICON_MARGIN_RIGHT = 5;
    /** 图标上下边距 */
    private static final int ICON_MARGIN_VERTICAL = 6;

    /**
     * 记录最后选中的标签页索引
     * <p>
     * 替代 VM 的 {@code MTEVendingMachineGui.lastPage}，
     * 用于 GUI 重新打开时恢复上次的标签页位置。
     */
    public static int lastPage = 0;

    /** 标签页索引（用于记录 lastPage） */
    private final int index;
    /** 标签页图标（margin 会在 draw 中根据悬停状态动态调整） */
    private final Icon tabIcon;

    /**
     * 构造 V2 标签页按钮
     *
     * @param index           标签页索引
     * @param controller      分页控制器（来自 PagedWidget）
     * @param category        对应的交易分类（用于高亮判断）
     * @param highlightedTabs 高亮标签页集合（包含此 category 时显示高亮覆盖层）
     * @param iconStack       标签页图标 ItemStack（为 null 时显示空图标）
     */
    public NekoPageButtonV2(int index, PagedWidget.Controller controller, NekoTradeCategory category,
        Set<NekoTradeCategory> highlightedTabs, ItemStack iconStack) {
        super(index, controller);
        this.index = index;

        // 构建两层覆盖层：高亮层 + 图标层
        IDrawable[] overlays = new IDrawable[2];

        // 第 0 层：高亮覆盖层
        // 当分类在高亮集合中时显示 TAB_HIGHLIGHT（复刻 VM VendingPageButton 的高亮逻辑）
        overlays[0] = new DynamicDrawable(() -> {
            if (highlightedTabs != null && highlightedTabs.contains(category)) {
                return NekoGuiTextures.TAB_HIGHLIGHT.asIcon()
                    .size(20, 20);
            }
            return IDrawable.EMPTY;
        });

        // 第 1 层：标签页图标
        // 使用 ItemStack 作为图标（V1 方式，比 VM 的 TradeCategory.getTexture() 更灵活）
        if (iconStack != null && iconStack.getItem() != null) {
            this.tabIcon = new Icon(new ItemDrawable(iconStack)).size(16, 16)
                .margin(ICON_MARGIN_LEFT, ICON_MARGIN_RIGHT, ICON_MARGIN_VERTICAL, ICON_MARGIN_VERTICAL)
                .center();
        } else {
            // 无图标时使用空图标
            // 注意：NekoTradeCategory 无 getTexture()，不能用 V1 的 category.getTexture() fallback
            this.tabIcon = new Icon(IDrawable.EMPTY).size(16, 16)
                .margin(ICON_MARGIN_LEFT, ICON_MARGIN_RIGHT, ICON_MARGIN_VERTICAL, ICON_MARGIN_VERTICAL)
                .center();
        }
        overlays[1] = this.tabIcon;

        this.overlay(overlays);
    }

    /**
     * 鼠标按下事件：记录最后选中的标签页索引
     * <p>
     * 替代 VM 的 {@code MTEVendingMachineGui.lastPage = this.index}，
     * 使用本类静态字段 {@link #lastPage}，避免依赖 VM GUI 类。
     *
     * @param mouseButton 鼠标按键（0=左键，1=右键）
     * @return 交互结果（交给父类处理实际的页面切换）
     */
    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        lastPage = this.index;
        return super.onMousePressed(mouseButton);
    }

    /**
     * 绘制：悬停时调整图标 margin，产生轻微右移动画
     * <p>
     * 复刻 VM VendingPageButton 的悬停效果：左边距 -1、右边距 +1，
     * 视觉上图标向右轻微移动，增强交互反馈。
     * <p>
     * 与 V1 NekoPageButton 的差异：V1 用对称 margin（5/7），
     * V2 采用 VM 的不对称基础 margin（7/5），悬停时变为 6/6，移动更自然。
     *
     * @param context     GUI 上下文
     * @param widgetTheme 控件主题
     */
    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (this.isHovering()) {
            // 悬停：左 -1、右 +1，图标轻微右移
            this.tabIcon.marginLeft(ICON_MARGIN_LEFT - 1)
                .marginRight(ICON_MARGIN_RIGHT + 1);
        } else {
            // 非悬停：恢复基础 margin
            this.tabIcon.marginLeft(ICON_MARGIN_LEFT)
                .marginRight(ICON_MARGIN_RIGHT);
        }
        super.draw(context, widgetTheme);
    }
}
