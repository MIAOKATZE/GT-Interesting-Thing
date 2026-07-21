package com.miaokatze.gtit.client.gui;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.Icon;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;

/**
 * 主标签页按钮（贸易/签到/抽奖/邮件）
 * <p>
 * 与 {@link NekoPageButtonV2}（贸易分类子标签）同源的视觉风格，
 * 但使用 v1.7.0 新生图素材（32x32 PNG 图标）替代 ItemStack 图标。
 * <p>
 * <b>与 NekoPageButtonV2 的差异</b>：
 * <ul>
 * <li>图标来源：UITexture（PNG 素材），而非 ItemStack</li>
 * <li>不支持高亮覆盖层（主标签无搜索高亮需求）</li>
 * <li>通过 {@link #lastMainTab} 记录最后选中的主标签，供 GUI 重开恢复</li>
 * </ul>
 *
 * @see NekoPageButtonV2
 * @see NekoGuiTextures#MAIN_TAB_TRADE
 */
public class NekoMainTabButton extends PageButton {

    /** 图标左边距（悬停时减 1，产生轻微右移动画，与 NekoPageButtonV2 一致） */
    private static final int ICON_MARGIN_LEFT = 7;
    /** 图标右边距 */
    private static final int ICON_MARGIN_RIGHT = 5;
    /** 图标上下边距 */
    private static final int ICON_MARGIN_VERTICAL = 6;

    /**
     * 记录最后选中的主标签索引
     * <p>
     * 0=贸易，1=签到，2=抽奖，3=邮件。
     * GUI 重新打开时据此恢复上次的主标签位置。
     */
    public static int lastMainTab = 0;

    /** 主标签索引 */
    private final int index;
    /** 主标签图标（margin 在 draw 中根据悬停状态动态调整） */
    private final Icon tabIcon;
    /** 点击选中后的回调（用于同步主标签索引到服务端等扩展） */
    private Runnable onSelected;

    /**
     * 构造主标签页按钮
     *
     * @param index       主标签索引（0=贸易，1=签到，2=抽奖，3=邮件）
     * @param controller  外层主面板 PagedWidget 的分页控制器
     * @param iconTexture 主标签图标纹理（32x32 PNG）
     */
    public NekoMainTabButton(int index, PagedWidget.Controller controller, UITexture iconTexture) {
        super(index, controller);
        this.index = index;

        // 主标签图标：32x32 缩到 16x16 显示，与贸易分类标签尺寸对齐
        IDrawable drawable = iconTexture != null ? iconTexture : IDrawable.EMPTY;
        this.tabIcon = new Icon(drawable).size(16, 16)
            .margin(ICON_MARGIN_LEFT, ICON_MARGIN_RIGHT, ICON_MARGIN_VERTICAL, ICON_MARGIN_VERTICAL)
            .center();
        this.overlay(this.tabIcon);
    }

    /**
     * 设置点击选中后的回调（在 super.onMousePressed 之后触发）
     *
     * @param onSelected 回调 Runnable
     * @return this（链式调用）
     */
    public NekoMainTabButton onSelected(Runnable onSelected) {
        this.onSelected = onSelected;
        return this;
    }

    /**
     * 鼠标按下：记录最后选中的主标签索引，并触发 onSelected 回调
     */
    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        lastMainTab = this.index;
        Interactable.Result result = super.onMousePressed(mouseButton);
        if (result == Interactable.Result.SUCCESS && onSelected != null) {
            onSelected.run();
        }
        return result;
    }

    /**
     * 绘制：悬停时调整图标 margin，产生轻微右移动画（与 NekoPageButtonV2 一致）
     */
    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (this.isHovering()) {
            this.tabIcon.marginLeft(ICON_MARGIN_LEFT - 1)
                .marginRight(ICON_MARGIN_RIGHT + 1);
        } else {
            this.tabIcon.marginLeft(ICON_MARGIN_LEFT)
                .marginRight(ICON_MARGIN_RIGHT);
        }
        super.draw(context, widgetTheme);
    }
}
