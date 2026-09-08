package com.miaokatze.gtit.reincarnation.client.render;

import net.minecraft.client.renderer.entity.Render;

import com.miaokatze.gtit.reincarnation.entity.EntityAscensionCarrier;

import cpw.mods.fml.client.registry.RenderingRegistry;

/**
 * 飞升载具客户端渲染器注册器（仅客户端可达，{@code ClientProxy.init()} 专用入口）。
 * <p>
 * v1.9.0 冒烟修正（20260909 R1/R2 实证）：渲染注册原与实体登记同体于
 * {@code ReincarnationEntities}——{@code registerClientRender()} 方法体引用
 * {@code RenderingRegistry} 与 extends {@link Render} 的空渲染类，JVM 类校验期
 * 对整类字节码验证，专用服上调用 {@code register()} 即触发整类链接失败
 * （NoClassDefFoundError: net/minecraft/client/renderer/entity/Render），
 * 属 wiki client-class-contamination 案例A"登记与渲染同体"同构缺陷。
 * 按规避清单条款 2 拆出：本类仅由 ClientProxy 引用加载，物理专用服务器
 * 类加载永不触达。
 */
public final class AscensionCarrierRenderRegistrar {

    /** 注册幂等标志 */
    private static boolean registered = false;

    private AscensionCarrierRenderRegistrar() {}

    /**
     * 注册飞升载具客户端渲染器（幂等，空渲染）。
     * 1.7.10 下实体未注册渲染器，客户端 track 到即崩溃；载具刻意不可见，
     * 以 {@link AscensionCarrierBlankRender} 空渲染占位。
     */
    public static void register() {
        if (registered) return;
        RenderingRegistry
            .registerEntityRenderingHandler(EntityAscensionCarrier.class, new AscensionCarrierBlankRender());
        registered = true;
    }
}
