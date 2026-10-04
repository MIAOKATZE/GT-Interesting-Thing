package com.miaokatze.gtit.common.machine.v2;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * ★R100 片 F（架构解耦）：猫猫售货机 GUI 工厂注入点。
 * <p>
 * 旧形状是 {@code MTENekoVendingMachineV2#getGui} 直接 {@code new NekoVMGuiV2(this)} 并 import
 * {@code gui.vm} 包——common 反向依赖 gui（依赖方向唯一允许 gui→common）。本类把那一次构造改成
 * <b>工厂委托</b>：实现由 {@code CommonProxy#init} 注册（{@code NekoVMGuiV2::new}）。
 * <p>
 * ★注册点必须双端（v1.8.56 起对齐口袋面板那座桥 {@code common/items/pocket/PocketPanelBridge}
 * 的范式）：GT5U 的 buildUI final 链在<b>服务端也构建 MUI2 面板树</b>、{@code getGui} 双端触达。
 * v1.8.45~v1.8.55 只在客户端 {@code ClientProxy#init} 注册，物理专用服务器上工厂恒 null，
 * 右键猫猫贸易机即抛 {@code IllegalStateException}、被 GT5U 右键守卫吞掉成不开屏（专用服日志
 * 实证）。{@code CommonProxy#init} 双端执行：专用服直接跑，客户端经 {@code ClientProxy#init}
 * 首行 {@code super.init} 同样先完成，两侧同享这一次注册。
 * <p>
 * 行为零变化：委托目标就是原来那行 {@code new NekoVMGuiV2(this)} 本身。
 */
public final class VendingGuiBridge {

    /** 与 {@code new NekoVMGuiV2(MTENekoVendingMachineV2)} 同形的工厂口。 */
    @FunctionalInterface
    public interface GuiFactory {

        MTEMultiBlockBaseGui<?> create(MTENekoVendingMachineV2 machine);
    }

    private static volatile GuiFactory factory;

    private VendingGuiBridge() {}

    /** 双端（{@code CommonProxy#init}）在首次开屏前注册实现；后注册者覆盖先注册者。 */
    public static void register(GuiFactory guiFactory) {
        factory = guiFactory;
    }

    /**
     * {@code MTENekoVendingMachineV2#getGui} 的唯一出口：转发到已注册的工厂。
     *
     * @throws IllegalStateException CommonProxy#init 未执行（双端注册点）
     */
    public static MTEMultiBlockBaseGui<?> create(MTENekoVendingMachineV2 machine) {
        final GuiFactory guiFactory = factory;
        if (guiFactory == null) {
            throw new IllegalStateException("猫猫售货机 GUI 工厂未注册：CommonProxy#init 未执行（双端注册点 VendingGuiBridge.register）");
        }
        return guiFactory.create(machine);
    }
}
