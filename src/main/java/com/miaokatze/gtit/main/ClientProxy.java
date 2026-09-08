package com.miaokatze.gtit.main;

import com.miaokatze.gtit.common.machine.neko.NekoMusicEventHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;

/**
 * 客户端代理类
 * 继承自 CommonProxy，用于处理仅在客户端（Client Side）执行的逻辑。
 * 如：渲染注册、按键绑定等。
 */
public class ClientProxy extends CommonProxy {

    /**
     * 初始化阶段 (Init)
     * 在此阶段注册客户端特定的事件处理器，如 HUD 渲染器。
     */
    @Override
    public void init(FMLInitializationEvent event) {
        // 调用父类的 init 方法，确保通用逻辑正常执行
        super.init(event);

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

        GTInterestingThing.LOG.info("[2/3] 客户端初始化完成");
    }

    /**
     * v1.9.0 S4：打开周目 GUI（仅物理客户端覆盖；ClientProxy 不进专用服类加载路径，
     * 可安全触达 client/gui 包——common 物品类仍保持零 client/gui import）
     */
    @Override
    public void openReincarnationGui(net.minecraft.entity.player.EntityPlayer player) {
        com.miaokatze.gtit.client.gui.ReincarnationGuiOpener.openFor(player);
    }
}
