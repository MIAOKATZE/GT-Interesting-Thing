package com.miaokatze.gtit.common.machine.v2;

import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * ★R100 片 F（架构解耦）：猫猫售货机 GUI 工厂注入点。
 * <p>
 * 旧形状是 {@code MTENekoVendingMachineV2#getGui} 直接 {@code new NekoVMGuiV2(this)} 并 import
 * {@code gui.vm} 包——common 反向依赖 gui（依赖方向唯一允许 gui→common）。本类把那一次构造改成
 * <b>工厂委托</b>：实现由客户端专属的 {@code ClientProxy#init} 注册（{@code NekoVMGuiV2::new}）。
 * <p>
 * ★为什么注册点只在客户端：GUI 实例只在客户端创建（GT5U 的 buildUI final 链 → getGui 仅客户端
 * 触达），专用服上本桥保持未注册态而服务端路径永不调 {@code create} —— 与口袋面板那座桥
 * （{@code common/items/pocket/PocketPanelBridge}，MUI2 双端建树 ⇒ 必须双端注册）刻意不同侧。
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

    /** 客户端侧（{@code ClientProxy#init}）在首次开屏前注册实现；后注册者覆盖先注册者。 */
    public static void register(GuiFactory guiFactory) {
        factory = guiFactory;
    }

    /**
     * {@code MTENekoVendingMachineV2#getGui} 的唯一出口：转发到已注册的客户端工厂。
     *
     * @throws IllegalStateException 客户端 init 未跑就有人要开 GUI（工程上不可达）
     */
    public static MTEMultiBlockBaseGui<?> create(MTENekoVendingMachineV2 machine) {
        final GuiFactory guiFactory = factory;
        if (guiFactory == null) {
            throw new IllegalStateException("猫猫售货机 GUI 工厂未注册：ClientProxy#init 未执行（客户端注入点 VendingGuiBridge.register）");
        }
        return guiFactory.create(machine);
    }
}
