package com.miaokatze.gtit.client.gui;

import java.util.function.IntConsumer;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

/**
 * 默认贸易组同步询问弹框（v1.8.17，本地实现；v1.8.18 修复按钮行布局）
 * <p>
 * 玩家打开猫猫贸易机时，若服务端判定需要同步默认贸易组（见
 * {@code BundledTradeGroups#getPromptState()}），由宿主打开本弹框，
 * 玩家必须选择一个按钮才能继续使用贸易机：
 * <ul>
 * <li><b>普通询问</b>（版本更新且玩家改动过默认条目）："检测到默认贸易组变动，是否复原？"，
 * 按钮 复原 / 否 / 不再提醒（"否"=该版本不再打扰，"不再提醒"=后续版本也不再问）</li>
 * <li><b>强制询问</b>（升自更新标签之前的存档）："默认贸易组已更新，请同步。"，
 * 按钮 是 / 否（配置不可关闭，无"不再提醒"）</li>
 * </ul>
 * <p>
 * 模式在构造时确定（MUI2 2.3.88 无 widget 可见性 API，两种模式按钮组合不同，
 * 宿主经 {@code IPanelHandler} 的 provider 按模式现场构建实例）。
 * 按钮结果经 {@link #setActionHandler} 注入的回调发往宿主
 * （宿主经 C2S 同步值通知服务端执行复原/跳过/不再提醒/标记已处理）。
 * <p>
 * v1.8.18 布局修复：{@code Flow.row()} 内子元素的 {@code left(n)} 是相对行的
 * 绝对钉位（非流式 margin）——原实现三按钮 left(0)/left(4)/left(4) 全部叠在行首；
 * 现按固定行宽 + 显式互斥 x 钉位（{@code NekoConfirmationDialog} left/right 同模式）。
 */
public class NekoDefaultTradeSyncDialog extends Dialog<Integer> {

    /** 结果：复原/同步（"是"） */
    public static final int RESULT_RESTORE = 0;
    /** 结果：否（该版本不再打扰） */
    public static final int RESULT_DISMISS = 1;
    /** 结果：不再提醒（后续版本也不再问，仅普通询问有此按钮） */
    public static final int RESULT_NEVER = 2;

    /** 按钮行宽（面板宽 200，行居中） */
    private static final int ROW_WIDTH = 190;

    /** 强制模式（升自更新标签之前）：文案与按钮组合切换 */
    private final boolean forceMode;
    /** 按钮结果回调（宿主注入：发送 C2S 同步值） */
    private IntConsumer actionHandler = r -> {};

    /**
     * 构造同步询问弹框（模式在构建时确定）
     *
     * @param name      面板名称（用于同步标识）
     * @param forceMode true=强制同步询问（"是/否"两按钮）
     */
    public NekoDefaultTradeSyncDialog(String name, boolean forceMode) {
        super(name, _unused -> {});
        this.forceMode = forceMode;
        this.size(200, 106)
            .child(buildMessage())
            .child(buildButtonRow());

        this.setDisablePanelsBelow(true)
            .setDraggable(false);
    }

    /** 提示文案（按模式取文案） */
    private TextWidget<?> buildMessage() {
        return new TextWidget<>(
            IKey.dynamic(() -> EnumChatFormatting.WHITE + (this.forceMode ? "默认贸易组已更新，请同步。" : "检测到默认贸易组变动，是否复原？")))
                .top(12)
                .widthRel(0.9f)
                .height(20)
                .horizontalCenter();
    }

    /**
     * 按钮行（固定行宽 + 显式互斥 x 钉位，防重叠）
     * <p>
     * 普通模式三按钮：复原(54) x2-56 / 否(36) x64-100 / 不再提醒(84) x104-188；
     * 强制模式两按钮居中：是(60) x24-84 / 否(60) x106-166。
     */
    private Flow buildButtonRow() {
        Flow row = Flow.row()
            .bottom(6)
            .size(ROW_WIDTH, 16)
            .horizontalCenter();
        if (this.forceMode) {
            return row.child(
                new ButtonWidget<>().size(60, 16)
                    .left(24)
                    .overlay(IKey.str("是"))
                    .onMouseTapped(mouse -> {
                        this.closeWith(RESULT_RESTORE);
                        return true;
                    }))
                .child(
                    new ButtonWidget<>().size(60, 16)
                        .left(106)
                        .overlay(IKey.str("否"))
                        .onMouseTapped(mouse -> {
                            this.closeWith(RESULT_DISMISS);
                            return true;
                        }));
        }
        return row.child(
            new ButtonWidget<>().size(54, 16)
                .left(2)
                .overlay(IKey.str("复原"))
                .onMouseTapped(mouse -> {
                    this.closeWith(RESULT_RESTORE);
                    return true;
                }))
            .child(
                new ButtonWidget<>().size(36, 16)
                    .left(64)
                    .overlay(IKey.str("否"))
                    .onMouseTapped(mouse -> {
                        this.closeWith(RESULT_DISMISS);
                        return true;
                    }))
            .child(
                new ButtonWidget<>().size(84, 16)
                    .left(104)
                    .overlay(IKey.str("不再提醒"))
                    .tooltipBuilder(t -> t.addLine(IKey.str("后续默认贸易组版本更新不再弹此询问")))
                    .onMouseTapped(mouse -> {
                        this.closeWith(RESULT_NEVER);
                        return true;
                    }));
    }

    /**
     * 关闭弹框并执行按钮结果回调
     *
     * @param result {@link #RESULT_RESTORE} / {@link #RESULT_DISMISS} / {@link #RESULT_NEVER}
     */
    @Override
    public void closeWith(Integer result) {
        if (result != null) {
            this.actionHandler.accept(result);
        }
        closeIfOpen();
    }

    /**
     * 是否处于强制模式
     *
     * @return true=强制同步询问
     */
    public boolean isForceMode() {
        return this.forceMode;
    }

    /**
     * 注入按钮结果回调（宿主经 C2S 同步值通知服务端）
     *
     * @param handler 结果回调
     * @return this
     */
    public NekoDefaultTradeSyncDialog setActionHandler(IntConsumer handler) {
        this.actionHandler = handler != null ? handler : r -> {};
        return this;
    }
}
