package com.miaokatze.gtit.client.gui;

import java.awt.Point;

import javax.annotation.Nonnull;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.TextFieldTheme;
import com.cleanroommc.modularui.widgets.textfield.BaseTextFieldWidget;

/**
 * 搜索栏组件
 * <p>
 * 完美复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.SearchBar}，
 * 但移除了对 {@code MTEVendingMachineGui} 的直接依赖，
 * 改为通过 {@link SearchListener} 回调接口通知搜索文本变化。
 * <p>
 * 使用 {@link NekoWidgetThemes#BACKGROUND_SEARCH_BAR} 主题。
 * 搜索提示文本（hintText）由调用方传入，不再依赖 VM 的 Translator 工具类。
 */
public class NekoSearchBar extends BaseTextFieldWidget<NekoSearchBar> {

    /**
     * 搜索文本变化监听器接口
     * <p>
     * 当搜索栏文本发生变化时，通过此接口通知外部（如 GUI 控制器），
     * 替代 VM 中直接调用 {@code MTEVendingMachineGui.resetTradeDisplayScroll()}
     * 和 {@code MTEVendingMachineGui.setForceRefresh()} 的方式。
     */
    public interface SearchListener {

        /**
         * 搜索文本发生变化时调用
         *
         * @param newText 新的搜索文本
         */
        void onSearchChanged(String newText);
    }

    /** 搜索文本变化监听器 */
    private SearchListener listener;

    /** 上一次的搜索文本，用于检测变化 */
    private String previousText;

    /**
     * 创建搜索栏
     * <p>
     * 初始化时设置搜索栏主题为 {@link NekoWidgetThemes#BACKGROUND_SEARCH_BAR}，
     * 并将搜索文本清空。
     *
     * @param hintText 搜索提示文本（已本地化的字符串或本地化 key）
     */
    public NekoSearchBar(String hintText) {
        super();
        // 设置搜索栏背景主题
        widgetTheme(NekoWidgetThemes.BACKGROUND_SEARCH_BAR);
        // 初始化文本为空
        setText("");
        this.previousText = "";
        // 设置搜索提示文本
        hintText(hintText);
    }

    /**
     * 设置搜索文本变化监听器
     *
     * @param listener 监听器实例
     * @return 当前组件，用于链式调用
     */
    public NekoSearchBar setSearchListener(SearchListener listener) {
        this.listener = listener;
        return this;
    }

    /**
     * 获取当前搜索文本
     * <p>
     * 搜索栏只支持单行文本，若存在多行则抛出异常。
     *
     * @return 当前搜索文本（不为 null）
     */
    @Nonnull
    public String getText() {
        if (this.handler.getText()
            .isEmpty()) {
            return "";
        }
        if (this.handler.getText()
            .size() > 1) {
            throw new IllegalStateException("TextFieldWidget can only have one line!");
        }
        return this.handler.getText()
            .get(0);
    }

    /**
     * 设置搜索文本
     *
     * @param text 要设置的文本（不为 null）
     */
    public void setText(@Nonnull String text) {
        if (this.handler.getText()
            .isEmpty()) {
            this.handler.getText()
                .add(text);
        } else {
            this.handler.getText()
                .set(0, text);
        }
    }

    /**
     * 聚焦时的处理
     * <p>
     * 聚焦时将光标移动到文本末尾，方便用户直接输入。
     */
    @Override
    public void onFocus(ModularGuiContext context) {
        super.onFocus(context);
        Point main = this.handler.getMainCursor();
        if (main.x == 0) {
            this.handler.setCursor(main.y, getText().length(), true, true);
        }
    }

    /**
     * 是否支持悬停效果
     *
     * @return true（搜索栏支持悬停）
     */
    @Override
    public boolean canHover() {
        return true;
    }

    /**
     * 绘制前景层
     * <p>
     * 当滚动条激活且鼠标悬停时间达到提示显示阈值时，绘制 tooltip。
     */
    @Override
    public void drawForeground(ModularGuiContext context) {
        if (hasTooltip() && getScrollData().isScrollBarActive(getScrollArea())
            && isHoveringFor(getTooltip().getShowUpTimer())) {
            getTooltip().draw(getContext());
        }
    }

    /**
     * 设置文本绘制参数
     * <p>
     * 配置渲染器的位置、缩放和对齐方式。
     */
    @Override
    protected void setupDrawText(ModularGuiContext context, TextFieldTheme widgetTheme) {
        this.renderer.setSimulate(false);
        this.renderer.setPos(
            getArea().getPadding()
                .getLeft(),
            0);
        this.renderer.setScale(this.scale);
        this.renderer.setAlignment(this.textAlignment, -1, getArea().height);
    }

    /**
     * 每帧更新
     * <p>
     * 检测搜索文本是否发生变化，若变化则通过 {@link SearchListener} 通知外部。
     * 替代 VM 中直接操作 {@code MTEVendingMachineGui.lastSearch}、
     * {@code gui.resetTradeDisplayScroll()}、{@code gui.setForceRefresh()} 的逻辑。
     */
    @Override
    public void onUpdate() {
        super.onUpdate();
        String curText = getText();
        if (!curText.equals(previousText)) {
            // 文本变化时通过回调接口通知外部
            if (listener != null) {
                listener.onSearchChanged(curText);
            }
        }
        previousText = curText;
    }
}
