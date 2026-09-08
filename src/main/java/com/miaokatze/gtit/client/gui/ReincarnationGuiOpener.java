package com.miaokatze.gtit.client.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;

/**
 * 周目 GUI 打开入口（v1.9.0 C批 S7）
 * <p>
 * <b>打开链路</b>：{@code ReincarnationCrystal.onItemRightClick}（S4 接线，服务端分支）→
 * {@link #openFor(EntityPlayer)} → MUI2 {@link SimpleGuiFactory#open(EntityPlayerMP)} →
 * {@code GuiManager.open} → 服务端构建 panel（{@link ReincarnationCycleGui#buildUI}）→
 * OpenGuiPacket 下发客户端镜像构建。
 * <p>
 * <b>为何不用 {@code ItemGuiFactory}</b>：MUI2 的 item 工厂要求手持物品的
 * {@code Item} 本体实现 {@code IGuiHolder}（"Item was not a gui holder!"），
 * 而 {@code ReincarnationCrystal} 属 common/items（本切片禁改，由 S1 交付）。
 * 故改用 {@link GuiFactories#createSimple(String, java.util.function.Supplier)} 等价的
 * 具名 {@link SimpleGuiFactory} 挂 {@link ReincarnationCycleGui#INSTANCE}，以静态方法
 * {@link #openFor} 作为 S4 的唯一调用面。
 * <p>
 * <b>侧约束（import 自查标注，任务包要求）</b>：本类与 {@link ReincarnationCycleGui} 均在
 * {@code client/gui} 包，零 {@code net.minecraft.client} 引用（MUI2 双端构建需要服务端可加载）；
 * 物理专用服务器上周目系统整体门控（S1/S3 同款 {@code getSide() == Side.SERVER} 拒绝注册），
 * 不可达。<b>本类不得进入任何 common 静态构造路径</b>——唯一入口 {@link #openFor} 仅允许由
 * S4 在物品右击的服务端逻辑分支调用（同步 GUI 只能服务端打开，MUI2 约束：
 * "Synced GUIs must be opened from server side"）。
 */
public final class ReincarnationGuiOpener {

    /** MUI2 工厂注册名（GuiManager 唯一；客户端按名解析同一工厂） */
    public static final String FACTORY_NAME = "gtit:reincarnation_gui";

    /** 周目 GUI 工厂（懒注册；SimpleGuiFactory.init 为空实现，需显式 registerFactory） */
    private static volatile SimpleGuiFactory factory;

    private ReincarnationGuiOpener() {}

    /**
     * 打开周目 GUI（S4 唯一调用面；仅服务端语义）
     * <p>
     * 内部完成：工厂懒注册（幂等）→ MUI2 同步打开（服务端 load 周目记录 + 双端镜像构建）。
     * 周目记录的 load/save 由 {@link ReincarnationCycleGui} 的会话路径完成，本方法不感知。
     *
     * @param player 右击轮回水晶的玩家；非服务端玩家（{@code EntityPlayerMP}）直接忽略
     *               （MUI2 同步 GUI 仅服务端可开；客户端分支由 OpenGuiPacket 自动镜像）
     */
    public static void openFor(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        SimpleGuiFactory guiFactory = ensureFactoryRegistered();
        if (guiFactory != null) {
            guiFactory.open((EntityPlayerMP) player);
        }
    }

    /**
     * 懒注册工厂（幂等；已注册则直接复用）
     * <p>
     * 注册时机延后到首次打开，避免依赖 ClientProxy 初始化顺序（本切片禁改 proxy）。
     * MUI2 的 OpenGuiPacket 以工厂名跨端传递，双端必须同名字注册——物理客户端上
     * 本类仅经客户端路径加载，注册面恒为集成环境双端，专用服不触达。
     */
    private static SimpleGuiFactory ensureFactoryRegistered() {
        SimpleGuiFactory guiFactory = factory;
        if (guiFactory == null) {
            synchronized (ReincarnationGuiOpener.class) {
                guiFactory = factory;
                if (guiFactory == null) {
                    guiFactory = GuiFactories.createSimple(FACTORY_NAME, ReincarnationCycleGui.INSTANCE);
                    GuiManager.registerFactory(guiFactory);
                    factory = guiFactory;
                }
            }
        } else if (!GuiManager.hasFactory(FACTORY_NAME)) {
            // 防御：外部注销（理论上不发生）时重建注册
            synchronized (ReincarnationGuiOpener.class) {
                if (!GuiManager.hasFactory(FACTORY_NAME)) {
                    GuiManager.registerFactory(factory);
                }
            }
        }
        return guiFactory;
    }
}
