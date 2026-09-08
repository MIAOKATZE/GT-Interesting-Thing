package com.miaokatze.gtit.reincarnation.client.render;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/**
 * 飞升载具空渲染器（v1.9.0 B批）。
 * <p>
 * 载具本体<strong>刻意不可见</strong>——飞升演出由客户端 FX 状态驱动
 * （见 {@code com.miaokatze.gtit.reincarnation.network.ClientReincarnationFxState}
 * 与后续 S4 演出切片），本类唯一职责是占住 1.7.10 的实体渲染器注册位：
 * 1.7.10 下未注册渲染器的实体一旦被客户端 track 将直接崩溃
 * （{@code "No renderer for entity"}），故以全空实现防崩。
 * <p>
 * 仅经 {@code AscensionCarrierRenderRegistrar.register()}（ClientProxy.init 调用路径，
 * v1.9.0 冒烟修正后与实体登记分文件）注册，物理专用服务器不会触发本类类加载。
 */
public class AscensionCarrierBlankRender extends Render {

    /**
     * 刻意空实现：载具不可见，不绘制任何内容（连阴影/火焰都不经此路径，
     * doRender 未被调用时基类的 doRenderShadowAndFire 不会执行）
     */
    @Override
    public void doRender(Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        // intentionally blank：防 1.7.10 无渲染器崩溃，见类 javadoc
    }

    // 注：任务包草案中的 "passSpecialRender 空处理" 在 1.7.10 无对应超类方法——
    // 该钩子为 1.8+ 新增；1.7.10 的 Render 反编译源（SRG func_147906_a）是 protected
    // renderLivingLabel(Entity, String, double, double, double, int)，签名不同且仅在
    // LivingEntity 渲染器 doRender 内部被调用，本空 doRender 天然不会触达，无需也无法覆写。

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        // doRender 恒空，纹理绑定不会被触发；返回 null 仅满足抽象签名
        return null;
    }
}
