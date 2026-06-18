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

        GTInterestingThing.LOG.info("[2/3] 客户端初始化完成");
    }
}
