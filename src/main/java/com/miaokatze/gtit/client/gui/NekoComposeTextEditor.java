package com.miaokatze.gtit.client.gui;

import java.util.function.Consumer;

import javax.annotation.Nonnull;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widgets.textfield.TextEditorWidget;

/**
 * 写邮件多行正文编辑器（v1.7.6 G2② 写邮件页面）
 * <p>
 * 基于 ModularUI2 的 {@link TextEditorWidget}（非同步、多行、纯客户端文本输入），
 * 补充写邮件页面所需的三个能力：
 * <ul>
 * <li>{@link #getComposeText()}：读取全部正文（多行以 \n 连接）</li>
 * <li>{@link #setComposeText(String)}：写入正文（按 \n 拆行），用于 GUI 重开时恢复草稿</li>
 * <li>{@link #clearText()}：清空正文（发送成功/手动清空按钮）</li>
 * </ul>
 * <p>
 * 草稿持久化：本 widget 实例随 GUI 关闭销毁，调用方可通过
 * {@link #setChangeListener(Consumer)} 监听文本变化并把草稿存到客户端静态字段
 * （参照 {@link NekoSearchBar} 的 onUpdate 变化检测模式），重建 GUI 时再用
 * {@link #setComposeText(String)} 恢复。
 * <p>
 * <b>双端安全</b>：TextEditorWidget 官方定位为「client only screens」，
 * 本类只被 {@code MailGui} 的写邮件页引用（该页整条 PagedWidget 链仅客户端构建），
 * 服务端不会加载本类。
 */
public class NekoComposeTextEditor extends TextEditorWidget {

    /** 正文最大行数（约 500 字符上限的宽松行数余量，超出由服务端限长兜底） */
    private static final int MAX_LINES = 20;

    /** 文本变化监听器（草稿持久化用；null 表示不通知） */
    private Consumer<String> changeListener;

    /** 上一次的文本快照，用于 onUpdate 检测变化（避免每帧回调） */
    private String previousText = "";

    public NekoComposeTextEditor() {
        super();
        // 限制最大行数（默认 10000 行过大，正文总量由服务端 500 字符兜底）
        this.handler.setMaxLines(MAX_LINES);
        // ModularUI2 不变量：文本列表必须始终 >= 1 行——TextFieldRenderer.drawMeasuredLines
        // 渲染光标时直接 handler.getText().get(cursorY)，空列表会抛 IndexOutOfBoundsException。
        // 构造即补 1 行空行，保证任何调用顺序下不变量都成立（空行不影响 hint 显示，
        // isTextEmpty() 对全空行仍返回 true）
        this.handler.getText()
            .add("");
        // 文本域背景主题（与搜索栏同款深色底）
        widgetTheme(NekoWidgetThemes.BACKGROUND_SEARCH_BAR);
    }

    // ==================== 正文读写 API ====================

    /**
     * 读取全部正文（多行以 \n 连接；不为 null）
     */
    @Nonnull
    public String getComposeText() {
        return this.handler.getTextAsString();
    }

    /**
     * 写入正文（按 \n 拆行覆盖现有内容；null 按空串处理）
     * <p>
     * 用于 GUI 重开时恢复草稿。写入后同步 {@link #previousText} 快照，
     * 避免恢复草稿当帧误触发一次变化回调。
     *
     * @param text 正文文本（可含 \n 换行）
     */
    public void setComposeText(String text) {
        this.handler.getText()
            .clear();
        if (text != null && !text.isEmpty()) {
            for (String line : text.split("\n", -1)) {
                this.handler.getText()
                    .add(line);
            }
        }
        // ModularUI2 不变量兜底：文本列表必须 >= 1 行（渲染光标时直接 get(cursorY)，
        // 空列表抛 IndexOutOfBoundsException）。text 为 null/空串时补 1 行空行；
        // 空行不影响 getComposeText() 结果（单行空串 join 后仍为 ""），草稿判空不受影响
        if (this.handler.getText()
            .isEmpty()) {
            this.handler.getText()
                .add("");
        }
        // 内容整体替换后光标归位 (0,0)：防止复用实例时旧光标行号超出新列表行数
        // （与空列表同属 drawMeasuredLines 的越界诱因）
        this.handler.setCursor(0, 0, false);
        this.previousText = getComposeText();
    }

    /**
     * 清空正文（发送成功后或「清空」按钮调用）
     * <p>
     * 先调用 {@code handler.clear()} 走 markAll + deleteMarked 的合并删除路径；
     * 再防御性补一行空行，确保 ModularUI2「文本列表 >= 1 行」不变量成立，
     * 避免库实现细节或未来版本变化导致清空后出现 0 行而触发 drawMeasuredLines 越界。
     */
    public void clearText() {
        this.handler.clear();
        // 防御性兜底：handler.clear() 理论上保留 1 行空行，但直接依赖库内部行为
        // 风险高；若出现 0 行则主动补空行，保证 TextFieldRenderer.get(cursorY) 不抛
        // IndexOutOfBoundsException。
        if (this.handler.getText()
            .isEmpty()) {
            this.handler.getText()
                .add("");
        }
    }

    /**
     * 设置文本变化监听器（草稿持久化用）
     *
     * @param listener 监听器（文本每次实际变化时回调新全文；null 解除）
     * @return 当前组件，用于链式调用
     */
    public NekoComposeTextEditor setChangeListener(Consumer<String> listener) {
        this.changeListener = listener;
        return this;
    }

    // ==================== 变化检测 ====================

    /**
     * 每帧更新：检测正文是否变化，变化时回调 {@link #changeListener}
     * （参照 {@link NekoSearchBar#onUpdate()} 的快照对比模式）
     */
    @Override
    public void onUpdate() {
        super.onUpdate();
        String curText = getComposeText();
        if (!curText.equals(previousText)) {
            if (changeListener != null) {
                changeListener.accept(curText);
            }
        }
        previousText = curText;
    }

    /**
     * 是否支持悬停效果（与搜索栏一致，允许悬停 tooltip）
     */
    @Override
    public boolean canHover() {
        return true;
    }

    /**
     * 绘制前景层：滚动条激活且悬停到达提示阈值时绘制 tooltip
     * （参照 {@link NekoSearchBar#drawForeground(ModularGuiContext)}）
     */
    @Override
    public void drawForeground(ModularGuiContext context) {
        if (hasTooltip() && getScrollData().isScrollBarActive(getScrollArea())
            && isHoveringFor(getTooltip().getShowUpTimer())) {
            getTooltip().draw(getContext());
        }
    }
}
