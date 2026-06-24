package com.miaokatze.gtit.mixin;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2 兼容性修复：在 GuiScreen 的 func_146283_a（3 参数 drawHoveringText）方法头部注入，
 * 直接实现 tooltip 渲染逻辑并取消原始方法执行，避免虚分派到已被 Angelica 改为 abstract 的 4 参数版本。
 * <p>
 * <b>问题根因</b>：Angelica（渲染优化核心模组）通过 Mixin 将 GuiScreen 的
 * {@code drawHoveringText(List, int, int, FontRenderer)} 方法改为 abstract。
 * AEBaseGui 虽然有该方法的实现（标注了 @Override），但其方法体中调用了 {@code super.drawHoveringText()}，
 * 而 super 已被改为 abstract，导致运行时抛出 {@code AbstractMethodError}。
 * <p>
 * 同时，AEBaseGui 的 3 参数版本 {@code drawHoveringText(List, int, int)} 调用了 {@code super.func_146283_a()}，
 * 而 GuiScreen 的 3 参数方法内部会虚分派到 4 参数版本，最终也会触发同样的错误。
 * <p>
 * <b>修复方式</b>：在 GuiScreen 的 {@code func_146283_a}（3 参数版本）方法头部注入，
 * 直接实现 tooltip 渲染逻辑并取消原始方法执行。
 * 这样无论哪个子类调用 3 参数版本，都不会走到 4 参数版本的虚分派。
 * <p>
 * 同时也在 4 参数版本 {@code drawHoveringText} 方法头部注入，直接实现渲染逻辑并取消原始方法执行，
 * 防止任何直接调用 4 参数版本的路径触发 AbstractMethodError。
 * <p>
 * <b>为什么 Mixin AEBaseGui 不够</b>：AEBaseGui 的 4 参数 drawHoveringText 方法在运行时可能
 * 因为 Mixin 加载顺序问题未被正确注入，导致子类（如 GuiMEMonitorable、GuiCraftingTerm）
 * 在 JVM 类链接阶段就被判定为未实现 abstract 方法。直接在 GuiScreen 层面修复可以确保
 * 所有子类都继承到修复后的方法。
 * <p>
 * <b>注意</b>：zLevel 和 drawGradientRect 定义在父类 Gui 中，不能通过 @Shadow 访问
 * （Mixin 的 @Shadow 方法不会在父类中查找，会导致 InvalidMixinException）。
 * 因此这里内联了 drawGradientRect 的实现，并使用局部变量代替 zLevel 字段。
 * <p>
 * <b>移除条件</b>：当 Angelica 不再将 GuiScreen 的 drawHoveringText 改为 abstract 时，
 * 可安全移除此 Mixin。
 * <p>
 * <b>相关文件</b>：plan/sum/ae2_fix_sum.md
 */
@Mixin(GuiScreen.class)
public abstract class MixinAEBaseGuiDrawHoveringTextFix {

    @Shadow
    private FontRenderer fontRendererObj;

    @Shadow
    private static RenderItem itemRender;

    @Shadow
    private int width;

    @Shadow
    private int height;

    /**
     * 在 3 参数版本 func_146283_a 方法头部注入，直接实现 tooltip 渲染逻辑并取消原始方法执行。
     * 原始方法内部会调用 4 参数版本的 drawHoveringText，而 4 参数版本已被 Angelica 改为 abstract，
     * 虚分派到未实现该方法的子类时会抛出 AbstractMethodError。
     */
    @Inject(method = "func_146283_a", at = @At("HEAD"), cancellable = true)
    private void onFunc_146283_a(List<String> textLines, int x, int y, CallbackInfo ci) {
        renderTooltipDirect(textLines, x, y, this.fontRendererObj);
        ci.cancel();
    }

    /**
     * 在 4 参数版本 drawHoveringText 方法头部注入，直接实现 tooltip 渲染逻辑并取消原始方法执行。
     * 4 参数版本已被 Angelica 改为 abstract，直接调用会抛出 AbstractMethodError。
     * 注意：此注入可能因 Angelica 的 Mixin 修改而无法匹配，但 3 参数版本的注入已足够覆盖大部分场景。
     */
    @Inject(method = "drawHoveringText", at = @At("HEAD"), cancellable = true, remap = false)
    private void onDrawHoveringText4(List<String> textLines, int x, int y, FontRenderer font, CallbackInfo ci) {
        renderTooltipDirect(textLines, x, y, font);
        ci.cancel();
    }

    /**
     * 直接实现 tooltip 渲染逻辑，复制自 MC 1.7.10 GuiScreen 原版实现。
     * 避免虚分派到任何可能为 abstract 的方法。
     * 注意：drawGradientRect 和 zLevel 在父类 Gui 中，不能通过 @Shadow 访问，
     * 因此这里内联了 drawGradientRect 的实现，并使用局部变量 zLevel 代替字段。
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

            float zLevel = 300.0F;
            this.itemRender.zLevel = 300.0F;
            int j1 = -267386864;
            drawGradientRectInline(j2 - 3, k2 - 4, j2 + k + 3, k2 - 3, j1, j1, zLevel);
            drawGradientRectInline(j2 - 3, k2 + i1 + 3, j2 + k + 3, k2 + i1 + 4, j1, j1, zLevel);
            drawGradientRectInline(j2 - 3, k2 - 3, j2 + k + 3, k2 + i1 + 3, j1, j1, zLevel);
            drawGradientRectInline(j2 - 4, k2 - 3, j2 - 3, k2 + i1 + 3, j1, j1, zLevel);
            drawGradientRectInline(j2 + k + 3, k2 - 3, j2 + k + 4, k2 + i1 + 3, j1, j1, zLevel);
            int k1 = 1347420415;
            int l1 = (k1 & 16711422) >> 1 | k1 & -16777216;
            drawGradientRectInline(j2 - 3, k2 - 3 + 1, j2 - 3 + 1, k2 + i1 + 3 - 1, k1, l1, zLevel);
            drawGradientRectInline(j2 + k + 2, k2 - 3 + 1, j2 + k + 3, k2 + i1 + 3 - 1, k1, l1, zLevel);
            drawGradientRectInline(j2 - 3, k2 - 3, j2 + k + 3, k2 - 3 + 1, k1, k1, zLevel);
            drawGradientRectInline(j2 - 3, k2 + i1 + 2, j2 + k + 3, k2 + i1 + 3, l1, l1, zLevel);

            for (int i2 = 0; i2 < textLines.size(); ++i2) {
                String s1 = textLines.get(i2);
                font.drawStringWithShadow(s1, j2, k2, -1);

                if (i2 == 0) {
                    k2 += 2;
                }

                k2 += 10;
            }

            this.itemRender.zLevel = 0.0F;
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderHelper.enableStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        }
    }

    /**
     * 内联的 drawGradientRect 实现，复制自 MC 1.7.10 Gui 类。
     * 使用参数 zLevel 代替 this.zLevel 字段（因为 zLevel 在父类 Gui 中，无法通过 @Shadow 访问）。
     */
    private void drawGradientRectInline(int left, int top, int right, int bottom, int startColor, int endColor,
        float zLevel) {
        float f = (float) (startColor >> 24 & 255) / 255.0F;
        float f1 = (float) (startColor >> 16 & 255) / 255.0F;
        float f2 = (float) (startColor >> 8 & 255) / 255.0F;
        float f3 = (float) (startColor & 255) / 255.0F;
        float f4 = (float) (endColor >> 24 & 255) / 255.0F;
        float f5 = (float) (endColor >> 16 & 255) / 255.0F;
        float f6 = (float) (endColor >> 8 & 255) / 255.0F;
        float f7 = (float) (endColor & 255) / 255.0F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(f1, f2, f3, f);
        tessellator.addVertex((double) right, (double) top, (double) zLevel);
        tessellator.addVertex((double) left, (double) top, (double) zLevel);
        tessellator.setColorRGBA_F(f5, f6, f7, f4);
        tessellator.addVertex((double) left, (double) bottom, (double) zLevel);
        tessellator.addVertex((double) right, (double) bottom, (double) zLevel);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
