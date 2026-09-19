package com.miaokatze.gtit.client.gui;

import java.util.function.IntConsumer;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

/**
 * 默认贸易组同步询问弹框（v1.8.17，本地实现）
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
 * 按钮结果经 {@link #setActionHandler} 注入的回调发往宿主
 * （宿主经 C2S 同步值通知服务端执行复原/跳过/不再提醒/标记已处理）。
 */
public class NekoDefaultTradeSyncDialog extends Dialog<Integer> {

    /** 结果：复原/同步（"是"） */
    public static final int RESULT_RESTORE = 0;
    /** 结果：否（该版本不再打扰） */
    public static final int RESULT_DISMISS = 1;
    /** 结果：不再提醒（后续版本也不再问，仅普通询问有此按钮） */
    public static final int RESULT_NEVER = 2;

    /** 强制模式（升自更新标签之前）：文案与按钮组切换 */
    private boolean forceMode = false;
    /** 按钮结果回调（宿主注入：发送 C2S 同步值） */
    private IntConsumer actionHandler = r -> {};

    /**
     * 构造同步询问弹框
     *
     * @param name 面板名称（用于同步标识）
     */
    public NekoDefaultTradeSyncDialog(String name) {
        super(name, _unused -> {});
        this.size(200, 106)
            .child(
                Flow.column()
                    .child(
                        new TextWidget<>(
                            IKey.dynamic(
                                () -> EnumChatFormatting.WHITE
                                    + (this.forceMode ? "默认贸易组已更新，请同步。" : "检测到默认贸易组变动，是否复原？"))).top(12)
                                        .widthRel(0.9f)
                                        .height(20)
                                        .horizontalCenter())
                    .child(
                        Flow.row()
                            .bottom(6)
                            .height(16)
                            .horizontalCenter()
                            .child(
                                new ButtonWidget<>().size(50, 16)
                                    .left(0)
                                    .overlay(IKey.dynamic(() -> this.forceMode ? "是" : "复原"))
                                    .onMouseTapped(mouse -> {
                                        this.closeWith(RESULT_RESTORE);
                                        return true;
                                    }))
                            .child(
                                new ButtonWidget<>().size(40, 16)
                                    .left(4)
                                    .overlay(IKey.str("否"))
                                    .onMouseTapped(mouse -> {
                                        this.closeWith(RESULT_DISMISS);
                                        return true;
                                    }))
                            .child(
                                new ButtonWidget<>().size(70, 16)
                                    .left(4)
                                    .overlay(IKey.str("不再提醒"))
                                    .setEnabledIf(w -> !this.forceMode)
                                    .tooltipBuilder(t -> t.addLine(IKey.str("后续默认贸易组版本更新不再弹此询问")))
                                    .onMouseTapped(mouse -> {
                                        this.closeWith(RESULT_NEVER);
                                        return true;
                                    }))));

        this.setDisablePanelsBelow(true)
            .setDraggable(false);
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
     * 设置强制模式（文案"默认贸易组已更新，请同步。"+隐藏"不再提醒"按钮）
     *
     * @param force true=强制同步询问
     * @return this
     */
    public NekoDefaultTradeSyncDialog setForceMode(boolean force) {
        this.forceMode = force;
        return this;
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
