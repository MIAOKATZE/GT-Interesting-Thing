package com.miaokatze.gtit.client.gui.reincarnation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * 周目轮回 GUI 绘制工具（v1.8.8，契约 §4 绘制规则）：面板整幅与 nine-slice 拉伸片
 * 走 Tessellator 归一 UV 直绘（320&gt;256 或源区≠目标区时 drawTexturedModalRect 的
 * 256 分母 1:1 假设不可用）、图集 1:1 小件经宿主 {@code Gui.drawTexturedModalRect}。
 * <p>
 * 1.7.10 仅用 {@code org.lwjgl.opengl.GL11}（无 GlStateManager）。半透明件
 * （chip_count / paw_watermark 含 α&lt;255 像素）的 blend 由消费方显式
 * enable/disable 成对管理（契约 §4-5），本类不越权代管状态。
 */
public final class ReincarnationGuiDrawing {

    /**
     * 面板整幅直绘（契约 §4-1）：bind {@link ReincarnationGuiTextures#RL_PANEL} 后
     * 四顶点直绘 {@code (left, top)-(left+320, top+258)}，UV 分数 u = px/320.0F、
     * v = py/258.0F（整幅即 u/v∈[0,1]）；z=0 与旧纯色面板同层。面板不透明
     * （全图 alpha=255，脚本断言），无需 blend；绘制前复位顶点色。
     */
    public static void drawPanel(int left, int top) {
        bindTexture(ReincarnationGuiTextures.RL_PANEL);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(left, top + ReincarnationGuiTextures.PANEL_H, 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(
            left + ReincarnationGuiTextures.PANEL_W,
            top + ReincarnationGuiTextures.PANEL_H,
            0.0D,
            1.0D,
            1.0D);
        tessellator.addVertexWithUV(left + ReincarnationGuiTextures.PANEL_W, top, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(left, top, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
    }

    /** 贴图绑定唯一入口（消费方不得各自持 TextureManager 直调） */
    public static void bindTexture(ResourceLocation rl) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(rl);
    }

    /**
     * 图集小件区域绘制（契约 §3 UV 策略）：atlas 整数 UV 直接走宿主
     * {@code Gui.drawTexturedModalRect}（256 分母假设；z = 宿主 zLevel）。
     * 宿主须已 bind {@link ReincarnationGuiTextures#RL_ATLAS}。
     */
    public static void drawAtlasRegion(Gui host, int x, int y, int u, int v, int width, int height) {
        host.drawTexturedModalRect(x, y, u, v, width, height);
    }

    /**
     * nine-slice 拉伸绘制（契约 §4-3：label_strip slice=4 → (GRID_X, LABELS_Y, 298, 10)）。
     * 四角 1:1（整数 UV 走宿主 {@code drawTexturedModalRect}）；四边与中心为拉伸片，
     * 1.7.10 该原语是 1:1 采样不具备拉伸能力（UV 跨度=宽高而非归一化），故改
     * Tessellator 四顶点直绘、按 {@link ReincarnationGuiTextures#ATLAS_SIZE} 归一
     * UV（契约 §4-3「Tessellator 实现，真实宽高归一 UV」）。宿主须已 bind 目标贴图。
     */
    public static void drawNineSlice(Gui host, int u, int v, int srcW, int srcH, int slice, int x, int y, int dstW,
        int dstH) {
        int midW = dstW - slice * 2;
        int midH = dstH - slice * 2;
        // 四角（1:1）
        host.drawTexturedModalRect(x, y, u, v, slice, slice);
        host.drawTexturedModalRect(x + dstW - slice, y, u + srcW - slice, v, slice, slice);
        host.drawTexturedModalRect(x, y + dstH - slice, u, v + srcH - slice, slice, slice);
        host.drawTexturedModalRect(
            x + dstW - slice,
            y + dstH - slice,
            u + srcW - slice,
            v + srcH - slice,
            slice,
            slice);
        // 四边（单向拉伸）
        drawStretchedRegion(u + slice, v, srcW - slice * 2, slice, x + slice, y, midW, slice);
        drawStretchedRegion(
            u + slice,
            v + srcH - slice,
            srcW - slice * 2,
            slice,
            x + slice,
            y + dstH - slice,
            midW,
            slice);
        drawStretchedRegion(u, v + slice, slice, srcH - slice * 2, x, y + slice, slice, midH);
        drawStretchedRegion(
            u + srcW - slice,
            v + slice,
            slice,
            srcH - slice * 2,
            x + dstW - slice,
            y + slice,
            slice,
            midH);
        // 中心（双向拉伸）
        drawStretchedRegion(u + slice, v + slice, srcW - slice * 2, srcH - slice * 2, x + slice, y + slice, midW, midH);
    }

    /**
     * 图集源区 → 目标矩形的拉伸直绘（atlas UV 按 256 归一；z=0 与面板同层，
     * 顶点序与 {@link #drawPanel} 一致）。调用方负责已 bind atlas 与 blend 状态。
     */
    private static void drawStretchedRegion(int u, int v, int srcW, int srcH, int x, int y, int dstW, int dstH) {
        float u0 = u / (float) ReincarnationGuiTextures.ATLAS_SIZE;
        float v0 = v / (float) ReincarnationGuiTextures.ATLAS_SIZE;
        float u1 = (u + srcW) / (float) ReincarnationGuiTextures.ATLAS_SIZE;
        float v1 = (v + srcH) / (float) ReincarnationGuiTextures.ATLAS_SIZE;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + dstH, 0.0D, u0, v1);
        tessellator.addVertexWithUV(x + dstW, y + dstH, 0.0D, u1, v1);
        tessellator.addVertexWithUV(x + dstW, y, 0.0D, u1, v0);
        tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
        tessellator.draw();
    }

    private ReincarnationGuiDrawing() {}
}
