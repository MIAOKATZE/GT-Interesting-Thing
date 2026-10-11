package com.miaokatze.gtit.main;

import com.miaokatze.gtit.common.machine.neko.NekoMusicEventHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * 客户端代理类
 * 继承自 CommonProxy，用于处理仅在客户端（Client Side）执行的逻辑。
 * 如：渲染注册、按键绑定等。
 */
public class ClientProxy extends CommonProxy {

    /**
     * ★R96 S11（需求 8）：饰品背包按键注册。放 <b>preInit</b> 的理由与 MUI2 自己的
     * {@code ClientProxy#preInit}（{@code :76-77}）一致 —— {@code KeyBinding} 要在
     * {@code GameSettings} 成形之前进列表，玩家换绑后的 {@code options.txt} 才读得到它。
     * <p>
     * ★这里只调 {@code install()}，不在常驻类里碰任何客户端类型：物理专用服务器不加载本类 ⇒
     * {@code PocketBaubleKeybind} 与 {@code KeyBinding}/{@code Keyboard}/{@code ClientRegistry}
     * 全链路不可达（注册失败只损失按键，不影响既有右击，照 {@code ReincarnationClientFx} 骨架吞进日志）。
     */
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        try {
            com.miaokatze.gtit.client.PocketBaubleKeybind.install();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[1/3] 饰品背包按键注册失败（B 键不可用；右击开屏与抽液不受影响）", t);
        }
    }

    /**
     * 初始化阶段 (Init)
     * 在此阶段注册客户端特定的事件处理器，如 HUD 渲染器。
     */
    @Override
    public void init(FMLInitializationEvent event) {
        // 调用父类的 init 方法，确保通用逻辑正常执行
        super.init(event);
        com.miaokatze.gtit.client.hologram.HologramClient.install();
        com.miaokatze.gtit.client.hologram.HologramInteractionClientSmoke.install();

        // 注册猫猫售货机 BGM 事件处理器（客户端）
        // 原因：之前因 getTooltip() NPE 崩溃而临时禁用，现 @SkipGenerateDescription 已修复根因
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(new NekoMusicEventHandler());
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("猫猫售货机 BGM 事件处理器注册失败", t);
        }

        // v1.9.0: 周目系统网络包客户端 handler 注册（S→C 四包，discriminator 见 ReincarnationNetwork）。
        // 通道本体已在 super.init() → CommonProxy.init() 中创建（物理专用服务器门控跳过）；
        // handler 注册收束在 ClientProxy，保证 reincarnation.network 包内 server 公共路径零 client 引用
        try {
            com.miaokatze.gtit.reincarnation.network.ReincarnationNetwork.registerClientHandlers();
            GTInterestingThing.LOG.info("[2/3] 周目系统网络包客户端 handler 已注册");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[2/3] 周目系统网络包客户端 handler 注册失败", t);
        }

        // v1.9.0 S4: 周目实体客户端渲染器（空渲染，仅客户端可达的注册器）+ 客户端演出/输入封锁安装
        // （仅物理客户端路径；client/fx 与 client/render 类加载不进专用服）
        try {
            com.miaokatze.gtit.reincarnation.client.render.AscensionCarrierRenderRegistrar.register();
            com.miaokatze.gtit.reincarnation.client.fx.ReincarnationClientFx.install();
            GTInterestingThing.LOG.info("[2/3] 周目系统客户端渲染与演出已安装");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[2/3] 周目系统客户端渲染与演出安装失败", t);
        }

        // v1.8.3: 周目 GUI 打开链路弃 MUI2，改 Forge 原版 IGuiHandler + EntityPlayer.openGui：
        // 先注入客户端工厂回调（common 的 ReincarnationGuiHandler 三层零 client 引用，
        // 客户端 GuiContainer 仅经本回调触达），再注册 handler（NetworkRegistry 每 mod 单 handler）。
        // 物理专用服务器不加载 ClientProxy，注册路径不可达（周目系统整体门控）。
        //
        // 更正（本仓 v1.9 复查）：当年弃用 MUI2 时记录的根因「MUI2 工厂注册在集成服双线程重复执行」
        // 是误诊。真实机制是单线程内对同一工厂实例注册两次——GuiFactories.createSimple 的构造器
        // 已经自注册，旧 ReincarnationGuiOpener 又手写了一次 registerFactory，首次右击即抛
        // GuiManager 的 dup-IAE；注册本身没有阶段或线程检查。MUI2 因此并未不可用于物品 GUI。
        // 证据：plan/_taskpack/ultra-07-mui2-open-crash.md §1.2（dev jar 字节码）与 §2.1-§2.2（被删源码）。
        // 本链路不回迁：现状稳定服役，且换载体的功能收益已兑现。
        try {
            com.miaokatze.gtit.reincarnation.gui.ReincarnationGuiHandler
                .setClientFactory(player -> new com.miaokatze.gtit.client.gui.ReincarnationGuiContainer(player));
            cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(
                GTInterestingThing.instance,
                new com.miaokatze.gtit.reincarnation.gui.ReincarnationGuiHandler());
            GTInterestingThing.LOG.info("[2/3] 周目 GUI 原版 GuiHandler 已注册（客户端工厂已注入）");
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[2/3] 周目 GUI 原版 GuiHandler 注册失败", t);
        }

        GTInterestingThing.LOG.info("[2/3] 客户端初始化完成");
    }

    /**
     * 打开周目 GUI（仅物理客户端覆盖；ClientProxy 不进专用服类加载路径，
     * 可安全触达 client/gui 包——common 物品类仍保持零 client/gui import）
     * <p>
     * v1.8.3 打开链路：严格单机门控与 chat 拒绝原样保留（v1.8.2 语义零漂移），通过后经
     * {@code EntityPlayer.openGui} → FML → {@code ReincarnationGuiHandler} 走原版双端
     * Container/GuiContainer（替代 v1.8.2 前的 MUI2 {@code ReincarnationGuiOpener.openFor}，
     * 根因：MUI2 工厂注册在集成服双线程重复执行抛 IllegalArgumentException）。
     * <p>
     * v1.8.2 严格单机门控：非单机（开放 LAN / 防御纵深）不开 GUI，仅回玩家
     * {@code gtit.reincarnation.single_player_only} 提示；判定复用
     * {@code ReincarnationHandler.isStrictSinglePlayer()}（isSinglePlayer 在 1.7.10
     * 对开放 LAN 的集成服仍为 true，须叠加 getPublic 反射检测，详见该方法 javadoc）。
     */
    @Override
    public void openReincarnationGui(net.minecraft.entity.player.EntityPlayer player) {
        if (!com.miaokatze.gtit.reincarnation.handler.ReincarnationHandler.isStrictSinglePlayer()) {
            if (player != null) {
                player.addChatMessage(
                    new net.minecraft.util.ChatComponentTranslation("gtit.reincarnation.single_player_only"));
            }
            GTInterestingThing.LOG.info("[reincarnation] 非单机环境，拒绝打开周目 GUI");
            return;
        }
        player.openGui(
            GTInterestingThing.instance,
            com.miaokatze.gtit.reincarnation.gui.ReincarnationLayout.GUI_ID,
            player.worldObj,
            0,
            0,
            0);
    }
}
