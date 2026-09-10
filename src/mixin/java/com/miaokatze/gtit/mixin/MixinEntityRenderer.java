package com.miaokatze.gtit.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtit.reincarnation.network.ClientReincarnationFxState;

/**
 * Mixin for EntityRenderer to apply an ascension view warp
 * (vision blur ramping up over the reincarnation ascension cutscene).
 * <p>
 * <b>数学来源（只读复刻）：</b>vanilla 传送门眩晕 warp——
 * {@code mcp_patched_minecraft-sources.jar} EntityRenderer.java:705-714：
 * {@code f3 = 5/(p*p+5) - p*0.04; f3*=f3; rotate((count+pt)*b0, 0,1,1);
 * scale(1/f3,1,1); rotate(-(count+pt)*b0, 0,1,1)}（b0=20，无反胃药水时）。
 * 本 mixin 复刻同款 rotate/scale/rotate-back 夹心，但驱动源不同：
 * 强度 p = 飞升演出进度（(世界总 tick - ascensionStartTick + partialTicks) / 120t），
 * 在窗口内单调增强（p↑ ⇒ f3↓ ⇒ 1/f3↑）。
 * <p>
 * <b>窗口门控（120t，每次现算）：</b>{@code ascensionStartTick >= 0} 且
 * {@code 0 <= 世界总tick - ascensionStartTick (+partialTicks) < 120} 才施 warp，
 * 窗口外（含演出异常滞留）零残留；{@code clearAscension()} 将起始 tick 复位为 -1
 * 后门控自动失活，无需任何手动清理。
 * <p>
 * <b>注入点：</b>{@code setupCameraTransform(float, int)}（dev MCP 名，refmap 由编译期
 * AP 生成）内、{@code orientCamera(float)} 调用之前（EntityRenderer.java:715）——
 * 与 vanilla warp 块（:705-714，位于 hurtCameraEffect/setupViewBobbing 之后、
 * orientCamera 之前）完全同位，作用于模型视图矩阵。
 * <p>
 * <b>无残留状态纪律：</b>本 mixin 不持有任何可累加 static 状态（仅 final 常量 +
 * {@code @Shadow} 实例字段）——进度每次调用由 {@link ClientReincarnationFxState}
 * 只读现算。client 侧 mixin（mixins.gtit.json client 数组），专用服不加载。
 */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    /** 视野渐模糊窗口（tick）：与飞升演出强度基准（120t）同步，进度线性映射 0..1 */
    private static final float ASCENSION_WARP_WINDOW_TICKS = 120.0F;

    /** vanilla 相机帧计数（EntityRenderer.java:76 private 字段，Shadow 引用驱动旋转相位） */
    @Shadow
    private int rendererUpdateCount;

    @Inject(
        method = "setupCameraTransform",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;orientCamera(F)V"))
    private void applyAscensionWarp(float partialTicks, int pass, CallbackInfo ci) {
        // 零强度守卫：非活跃 / 空 world / 空 player / 起始 tick 未回填 → 直接不动矩阵
        if (!ClientReincarnationFxState.isAscensionActive()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null || mc.thePlayer == null) {
            return;
        }
        long startTick = ClientReincarnationFxState.getAscensionStartTick();
        if (startTick < 0L) {
            return;
        }
        float progress = (float) (world.getTotalWorldTime() - startTick) + partialTicks;
        if (progress <= 0.0F || progress >= ASCENSION_WARP_WINDOW_TICKS) {
            return; // 120t 窗口门控：窗口外（含演出超长滞留）零残留
        }
        progress /= ASCENSION_WARP_WINDOW_TICKS; // (0,1)，随进度单调增强

        // vanilla 传送门 warp 数学同款（EntityRenderer.java:705-714，b0 恒取 20 档）：
        // rotate(a, 0,1,1) + scale(1/f3) + rotate(-a, 0,1,1)——随进度增强的旋绕缩放
        float f3 = 5.0F / (progress * progress + 5.0F) - progress * 0.04F;
        f3 *= f3;
        float phase = (float) this.rendererUpdateCount + partialTicks;
        GL11.glRotatef(phase * 20.0F, 0.0F, 1.0F, 1.0F);
        GL11.glScalef(1.0F / f3, 1.0F, 1.0F);
        GL11.glRotatef(-phase * 20.0F, 0.0F, 1.0F, 1.0F);
    }
}
