package com.miaokatze.gtit.client.gui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

/**
 * 确认对话框（本地实现）
 * <p>
 * 由于项目依赖的 GTNHLib 0.10.3 尚未提供 {@code ConfirmationDialog}，
 * 此类复刻 GTNHLib 0.11.24 中 {@code ConfirmationDialog} 的核心逻辑：
 * 继承 {@link Dialog}，提供消息文本和确认回调，点击"确认"执行回调并关闭，
 * 点击"取消"直接关闭。
 * <p>
 * 用法：先调用 {@link #setParams(String, Runnable)} 设置消息和回调，
 * 再通过 {@link com.cleanroommc.modularui.api.IPanelHandler#openPanel()} 打开。
 */
public class NekoConfirmationDialog extends Dialog<Boolean> {

    /** 当前消息文本（动态显示） */
    private String message = "";
    /** 确认时执行的回调 */
    private Runnable runnable = () -> {};

    /**
     * 构造确认对话框
     *
     * @param name 面板名称（用于同步标识）
     */
    public NekoConfirmationDialog(String name) {
        super(name, _unused -> {});
        this.size(140, 70)
            .child(
                Flow.column()
                    .child(
                        new TextWidget<>(IKey.dynamic(() -> this.message)).top(10)
                            .sizeRel(0.9f, 0.5f)
                            .horizontalCenter())
                    .child(
                        Flow.row()
                            .bottom(5)
                            .size(110, 16)
                            .horizontalCenter()
                            .child(
                                new ButtonWidget<>().size(45, 16)
                                    .left(5)
                                    .overlay(IKey.str("确认"))
                                    .onMouseTapped(mouse -> {
                                        this.closeWith(true);
                                        return true;
                                    }))
                            .child(
                                new ButtonWidget<>().size(45, 16)
                                    .right(5)
                                    .overlay(IKey.str("取消"))
                                    .onMouseTapped(mouse -> {
                                        this.closeWith(false);
                                        return true;
                                    }))));

        this.setDisablePanelsBelow(true)
            .setDraggable(false);
    }

    /**
     * 关闭对话框时执行回调
     * <p>
     * 仅当用户点击"确认"（result=true）时执行 {@link #runnable}。
     *
     * @param result true 表示确认，false 表示取消
     */
    @Override
    public void closeWith(Boolean result) {
        if (result) {
            this.runnable.run();
        }
        closeIfOpen();
    }

    /**
     * 设置对话框参数
     *
     * @param message  显示的消息文本
     * @param runnable 确认时执行的回调
     */
    public void setParams(String message, Runnable runnable) {
        this.message = message;
        this.runnable = runnable;
    }
}
