package com.miaokatze.gtit.client.gui;

import java.util.function.IntConsumer;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

/**
 * 默认贸易组同步弹框（v1.8.17 引入；v1.8.18 修复按钮行布局；v1.8.21 两场景定型）
 * <p>
 * 玩家打开猫猫贸易机时，若服务端判定需要提示默认贸易组状态（见
 * {@code BundledTradeGroups#getPromptState()}），由宿主打开本弹框，
 * 玩家必须选择一个按钮才能继续使用贸易机：
 * <ul>
 * <li><b>强制覆盖通知</b>（升自更新标签之前的存档，启动已无条件强制覆盖）：
 * "默认贸易组已更新。"，单按钮 知道了（标记更新标签已处理，配置不可关闭）</li>
 * <li><b>例行更新询问</b>（资产版本变化且内容有变化）：
 * "检测到默认贸易组更新，是否同步？"，按钮 是 / 否 / 否且不再通知
 * （是=覆盖同步；否=该版本不再打扰；否且不再通知=写配置永久关闭此类询问）</li>
 * </ul>
 * <p>
 * 模式在构造时确定（MUI2 2.3.88 无 widget 可见性 API，两种模式按钮组合不同，
 * 宿主经 {@code IPanelHandler} 的 provider 按模式现场构建实例）。
 * 按钮结果经 {@link #setActionHandler} 注入的回调发往宿主
 * （宿主经 C2S 同步值通知服务端执行覆盖同步/跳过/不再通知/标记已处理）。
 * <p>
 * v1.8.18 布局修复：{@code Flow.row()} 内子元素的 {@code left(n)} 是相对行的
 * 绝对钉位（非流式 margin）——按钮按固定行宽 + 显式互斥 x 钉位排布
 * （{@code NekoConfirmationDialog} left/right 同模式）。
 */
public class NekoDefaultTradeSyncDialog extends Dialog<Integer> {

    /** 结果：知道了（强制覆盖通知收束，标记更新标签已处理） */
    public static final int RESULT_ACK = 0;
    /** 结果：是（覆盖同步当前资产内容） */
    public static final int RESULT_RESTORE = 1;
    /** 结果：否（该版本不再打扰） */
    public static final int RESULT_DISMISS = 2;
    /** 结果：否且不再通知（写配置 defaultTradeUpdateNotice=false，仅例行更新询问有此按钮） */
    public static final int RESULT_NEVER = 3;

    /** 按钮行宽（面板宽 200，行居中） */
    private static final int ROW_WIDTH = 190;

    /** 强制模式（升自更新标签之前）：文案与按钮组合切换 */
    private final boolean forceMode;
    /** 按钮结果回调（宿主注入：发送 C2S 同步值） */
    private IntConsumer actionHandler = r -> {};

    /**
     * 构造同步弹框（模式在构建时确定）
     *
     * @param name      面板名称（用于同步标识）
     * @param forceMode true=强制覆盖通知（单按钮"知道了"）；false=例行更新三按钮询问
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
            IKey.dynamic(() -> EnumChatFormatting.WHITE + (this.forceMode ? "默认贸易组已更新。" : "检测到默认贸易组更新，是否同步？"))).top(12)
                .widthRel(0.9f)
                .height(24)
                .horizontalCenter();
    }

    /**
     * 按钮行（固定行宽 + 显式互斥 x 钉位，防重叠）
     * <p>
     * 强制模式单按钮居中：知道了(60) x65-125；
     * 询问模式三按钮居中：是(36) x7-43 / 否(36) x47-83 / 否且不再通知(96) x87-183。
     */
    private Flow buildButtonRow() {
        Flow row = Flow.row()
            .bottom(6)
            .size(ROW_WIDTH, 16)
            .horizontalCenter();
        if (this.forceMode) {
            return row.child(
                new ButtonWidget<>().size(60, 16)
                    .left(65)
                    .overlay(IKey.str("知道了"))
                    .onMouseTapped(mouse -> {
                        this.closeWith(RESULT_ACK);
                        return true;
                    }));
        }
        return row.child(
            new ButtonWidget<>().size(36, 16)
                .left(7)
                .overlay(IKey.str("是"))
                .onMouseTapped(mouse -> {
                    this.closeWith(RESULT_RESTORE);
                    return true;
                }))
            .child(
                new ButtonWidget<>().size(36, 16)
                    .left(47)
                    .overlay(IKey.str("否"))
                    .onMouseTapped(mouse -> {
                        this.closeWith(RESULT_DISMISS);
                        return true;
                    }))
            .child(
                new ButtonWidget<>().size(96, 16)
                    .left(87)
                    .overlay(IKey.str("否且不再通知"))
                    .tooltipBuilder(t -> t.addLine(IKey.str("保持现状，且后续默认贸易组例行更新不再询问（更新标签强制覆盖不受影响）")))
                    .onMouseTapped(mouse -> {
                        this.closeWith(RESULT_NEVER);
                        return true;
                    }));
    }

    /**
     * 关闭弹框并执行按钮结果回调
     *
     * @param result {@link #RESULT_ACK} / {@link #RESULT_RESTORE} / {@link #RESULT_DISMISS} /
     *               {@link #RESULT_NEVER}
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
     * @return true=强制覆盖通知
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
