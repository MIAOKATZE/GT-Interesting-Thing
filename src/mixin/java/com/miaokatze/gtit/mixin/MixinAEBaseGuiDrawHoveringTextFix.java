package com.miaokatze.gtit.mixin;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.client.gui.AEBaseGui;

/**
 * AE2 兼容性修复：拦截 AEBaseGui 的 drawHoveringText 方法中对 super 的调用，避免触发 AbstractMethodError。
 * <p>
 * <b>问题根因</b>：Angelica（渲染优化核心模组）通过 Mixin 将 GuiScreen 的
 * {@code drawHoveringText(List, int, int, FontRenderer)} 方法改为 abstract。
 * AEBaseGui 虽然有该方法的实现（标注了 @Override），但其方法体中调用了 {@code super.drawHoveringText()}，
 * 而 super 已被改为 abstract，导致运行时抛出 {@code AbstractMethodError}。
 * 同理，3 参数版本 {@code drawHoveringText(List, int, int)} 调用了 {@code super.func_146283_a()}，
 * 而 GuiScreen 的 3 参数方法内部会虚分派到 4 参数版本，最终也会触发同样的错误。
 * <p>
 * <b>修复方式</b>：
 * <ul>
 * <li>3 参数版本：用 {@code @Redirect} 拦截对 {@code super.func_146283_a()} 的调用，
 * 替换为直接实现 tooltip 渲染逻辑。</li>
 * <li>4 参数版本：用 {@code @Inject}(remap=false) 在方法头部注入，直接实现渲染逻辑然后取消原始方法执行，
 * 避免调用 {@code super.drawHoveringText()}（abstract 方法）。
 * remap=false 是因为 4 参数版本是 Angelica 通过 Mixin 添加的方法，没有 SRG 混淆映射。</li>
 * </ul>
 * <p>
 * <b>移除条件</b>：当 AE2 GTNH 版本更新后 AEBaseGui 的 drawHoveringText 不再调用 super 时，
 * 或 Angelica 不再将 GuiScreen 的 drawHoveringText 改为 abstract 时，可安全移除此 Mixin。
 * <p>
 * <b>相关文件</b>：plan/sum/ae2_fix_sum.md
 */
@Mixin(AEBaseGui.class)
public abstract class MixinAEBaseGuiDrawHoveringTextFix extends GuiScreen {

    /**
     * 拦截 3 参数版本中对 super.func_146283_a() 的调用。
     * GuiScreen.func_146283_a 内部会调用 4 参数版本的 drawHoveringText，
     * 而 4 参数版本已被 Angelica 改为 abstract，会导致 AbstractMethodError。
     * 替换为直接实现 tooltip 渲染逻辑，避免经过 abstract 方法。
     */
    @Redirect(
        method = "drawHoveringText(Ljava/util/List;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;func_146283_a(Ljava/util/List;II)V"))
    private void redirectThreeParamSuperCall(GuiScreen instance, List<String> textLines, int x, int y) {
        renderTooltipDirect(textLines, x, y, this.fontRendererObj);
    }

    /**
     * 在 4 参数版本方法头部注入，直接实现 tooltip 渲染逻辑并取消原始方法执行。
     * 原始方法调用 super.drawHoveringText()，而 super 已被 Angelica 改为 abstract，
     * 直接调用会抛出 AbstractMethodError，因此需要完全替换方法体。
     * <p>
     * 使用 remap = false 因为 4 参数版本的 drawHoveringText 是 Angelica 通过 Mixin 添加的方法，
     * 没有 SRG 混淆映射，默认的映射查找会失败。
     */
    @Inject(
        method = "drawHoveringText(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private void injectFourParamDrawHoveringText(List<String> textLines, int x, int y, FontRenderer font,
        CallbackInfo ci) {
        renderTooltipDirect(textLines, x, y, font);
        ci.cancel();
    }

    /**
     * 直接实现 tooltip 渲染逻辑，复制自 MC 1.7.10 GuiScreen 原版实现。
     * 避免调用任何 super 方法，彻底绕开 Angelica 的 abstract 修改。
     */
    private void renderTooltipDirect(List<String> textLines, int x, int y, FontRenderer font) {
        if (!textLines.isEmpty()) {
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            int k = 0;

            for (String s : textLines) {
                int l = font.getStringWidth(s);
                if (l > k) {
                    k = l;
                }
            }

            int j2 = x + 12;
            int k2 = y - 12;
            int i1 = 8;

            if (textLines.size() > 1) {
                i1 += 2 + (textLines.size() - 1) * 10;
            }

            if (j2 + k > this.width) {
                j2 -= 28 + k;
            }

            if (k2 + i1 + 6 > this.height) {
                k2 = this.height - i1 - 6;
            }

            this.zLevel = 300.0F;
            itemRender.zLevel = 300.0F;
            int j1 = -267386864;
            this.drawGradientRect(j2 - 3, k2 - 4, j2 + k + 3, k2 - 3, j1, j1);
            this.drawGradientRect(j2 - 3, k2 + i1 + 3, j2 + k + 3, k2 + i1 + 4, j1, j1);
            this.drawGradientRect(j2 - 3, k2 - 3, j2 + k + 3, k2 + i1 + 3, j1, j1);
            this.drawGradientRect(j2 - 4, k2 - 3, j2 - 3, k2 + i1 + 3, j1, j1);
            this.drawGradientRect(j2 + k + 3, k2 - 3, j2 + k + 4, k2 + i1 + 3, j1, j1);
            int k1 = 1347420415;
            int l1 = (k1 & 16711422) >> 1 | k1 & -16777216;
            this.drawGradientRect(j2 - 3, k2 - 3 + 1, j2 - 3 + 1, k2 + i1 + 3 - 1, k1, l1);
            this.drawGradientRect(j2 + k + 2, k2 - 3 + 1, j2 + k + 3, k2 + i1 + 3 - 1, k1, l1);
            this.drawGradientRect(j2 - 3, k2 - 3, j2 + k + 3, k2 - 3 + 1, k1, k1);
            this.drawGradientRect(j2 - 3, k2 + i1 + 2, j2 + k + 3, k2 + i1 + 3, l1, l1);

            for (int i2 = 0; i2 < textLines.size(); ++i2) {
                String s1 = textLines.get(i2);
                font.drawStringWithShadow(s1, j2, k2, -1);

                if (i2 == 0) {
                    k2 += 2;
                }

                k2 += 10;
            }

            this.zLevel = 0.0F;
            itemRender.zLevel = 0.0F;
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderHelper.enableStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        }
    }
}
