package com.miaokatze.gtit.mixin;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;

import appeng.client.gui.AEBaseGui;

/**
 * AE2 兼容性修复：为 AEBaseGui 补充 drawHoveringText(List, int, int, FontRenderer) 实现。
 * <p>
 * <b>问题根因</b>：Angelica（渲染优化核心模组）通过 Mixin 将 GuiScreen 的
 * {@code drawHoveringText(List, int, int, FontRenderer)} 方法改为 abstract，
 * 但 AE2 的 AEBaseGui 未实现该方法，导致所有继承 AEBaseGui 的 GUI（如 GuiCraftingTerm）
 * 在调用该方法时抛出 {@code AbstractMethodError}。
 * <p>
 * <b>修复方式</b>：在 AEBaseGui 中添加该方法的实现，逻辑与 GuiScreen 原版实现一致。
 * <p>
 * <b>移除条件</b>：当 AE2 GTNH 版本更新后自带该方法的实现时，可安全移除此 Mixin。
 * 检查方式：确认 AEBaseGui 中存在 {@code drawHoveringText(List, int, int, FontRenderer)} 方法。
 * <p>
 * <b>相关文件</b>：plan/sum/ae2_fix_sum.md
 */
@Mixin(AEBaseGui.class)
public abstract class MixinAEBaseGuiDrawHoveringTextFix extends GuiScreen {

    /**
     * 实现 GuiScreen 中被 Angelica 改为 abstract 的 drawHoveringText 方法。
     * 逻辑完全复制自 MC 1.7.10 GuiScreen 原版实现。
     * itemRender 和 zLevel 直接继承自 GuiScreen，无需 @Shadow。
     */
    @SuppressWarnings("unchecked")
    public void drawHoveringText(List textLines, int x, int y, FontRenderer font) {
        if (!textLines.isEmpty()) {
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            int k = 0;

            for (Object o : textLines) {
                String s = (String) o;
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
                String s1 = (String) textLines.get(i2);
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
