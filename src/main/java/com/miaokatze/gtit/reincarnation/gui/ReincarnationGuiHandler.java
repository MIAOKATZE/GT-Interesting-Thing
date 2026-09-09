package com.miaokatze.gtit.reincarnation.gui;

import java.util.function.Function;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;

/**
 * 周目 GUI 的 Forge 原版 {@link IGuiHandler}（v1.8.3，MUI2 工厂注册链路替代）。
 * <p>
 * <b>打开链路</b>：{@code ReincarnationCrystal.onItemRightClick}（服务端分支）→
 * {@code ClientProxy.openReincarnationGui}（严格单机门控）→
 * {@code EntityPlayer.openGui(GTInterestingThing.instance, ReincarnationLayout.GUI_ID, ...)} →
 * FML {@code FMLNetworkHandler.openGui} → 本 handler 服务端元素（{@link ReincarnationContainer}）
 * → FML 自动指派 windowId + {@code addCraftingToCrafters} 初始全量同步 → 客户端经
 * {@code OpenGuiHandler} 回调本 handler 客户端元素（{@code ReincarnationGuiContainer}）。
 * <p>
 * <b>崩溃根因修复（v1.8.3）</b>：旧 MUI2 链路在集成服双线程各执行一次工厂注册
 * （服务端线程第二次执行 MUI2 工厂注册（"gtit:reincarnation_gui"）抛
 * IllegalArgumentException）；原版链路无跨端工厂注册语义，注册在
 * {@code ClientProxy.init} 单线程单侧完成，根因消除。
 * <p>
 * <b>客户端工厂注入</b>：本类为 common 类，按「common 类零 client 引用三层校验」红线
 * 不得 import 任何 client 类型——客户端 GUI 工厂经
 * {@link #setClientFactory(Function)} 由 {@code ClientProxy.init} 在物理客户端路径注入
 * （{@code Function<EntityPlayer, Object>}），专用服务器上 ClientProxy 不加载、工厂恒 null。
 * <p>
 * <b>侧与加载（三层自查：import/字段/方法体）</b>：本类 import/字段/方法体三层零
 * MC 客户端侧类型、零 client 包引用，物理集成服务端可加载；
 * 物理专用服务器上周目系统整体门控，注册路径（{@code ClientProxy.init}）不可达。
 */
public class ReincarnationGuiHandler implements IGuiHandler {

    /**
     * 客户端 GUI 工厂（仅物理客户端由 ClientProxy 注入；返回值约定为
     * {@code GuiContainer} 实例，本类仅以 {@code Object} 透传给 FML {@code showGuiScreen}）
     */
    private static volatile Function<EntityPlayer, Object> clientFactory;

    /**
     * 注入客户端工厂回调（幂等覆盖；仅 {@code ClientProxy.init} 在注册 handler 前调用）
     *
     * @param factory 玩家 → 客户端 GUI（GuiContainer）实例的工厂
     */
    public static void setClientFactory(Function<EntityPlayer, Object> factory) {
        clientFactory = factory;
    }

    /** 服务端元素：周目权威会话容器（非本 mod 的 GUI id 返回 null） */
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return id == ReincarnationLayout.GUI_ID ? new ReincarnationContainer(player) : null;
    }

    /** 客户端元素：经注入的工厂回调构建（工厂未注入/非本 mod id 返回 null，FML 显示空屏不崩） */
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        Function<EntityPlayer, Object> factory = clientFactory;
        return id == ReincarnationLayout.GUI_ID && factory != null ? factory.apply(player) : null;
    }
}
