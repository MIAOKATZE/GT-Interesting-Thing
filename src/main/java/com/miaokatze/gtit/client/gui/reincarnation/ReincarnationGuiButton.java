package com.miaokatze.gtit.client.gui.reincarnation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

/**
 * 周目轮回 GUI 三态贴图按钮基类（v1.8.8，契约 §4-4 / §6-A6）：vanilla widgets.png
 * 灰石三态 → gtit atlas 金描边三态整件平铺（禁拉伸；按钮宽高即布局常量定值）。
 * <p>
 * 不变量：按钮矩形、id、{@code displayString}、{@code enabled}/{@code visible} 语义、
 * {@code mousePressed} 命中判定与 {@code sendEnchantPacket} 事件通道全部沿用
 * {@link GuiButton} 原实现——1.7.10 {@code mousePressed} 按矩形现算、不读 hover 字段，
 * 绘制态与点击命中解耦；本类 hover 为类内自声明字段逐帧重算（契约：勿引 vanilla 字段名）。
 * <p>
 * 三态优先级 disabled &gt; hovered &gt; normal（与 vanilla {@code getHoverState} 一致）；
 * 文字色：disabled 无阴影 / hover 与 normal 带阴影（§4-4 提案落地）。GL 纪律：绘制前
 * {@code glColor4f(1,1,1,1)} 复位顶点色；按钮三态区域不透明，无 blend、无矩阵栈操作。
 */
public class ReincarnationGuiButton extends GuiButton {

    /** 类内自声明 hover 态（每帧 drawButton 重算，仅驱动绘制；契约 §4-4） */
    private boolean selfHovered;

    /** 图集三态 v 起点（normal/hover/disabled；按按钮族由构造传入） */
    private final int normalV;
    private final int hoverV;
    private final int disabledV;

    public ReincarnationGuiButton(int id, int x, int y, int width, int height, String label, int normalV, int hoverV,
        int disabledV) {
        super(id, x, y, width, height, label);
        this.normalV = normalV;
        this.hoverV = hoverV;
        this.disabledV = disabledV;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        this.selfHovered = mouseX >= this.xPosition && mouseY >= this.yPosition
            && mouseX < this.xPosition + this.width
            && mouseY < this.yPosition + this.height;
        boolean disabled = !this.enabled;
        boolean hovered = !disabled && this.selfHovered;
        int stateV = disabled ? this.disabledV : hovered ? this.hoverV : this.normalV;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        ReincarnationGuiDrawing.bindTexture(ReincarnationGuiTextures.RL_ATLAS);
        // 图集整数 UV 走 drawTexturedModalRect 的 256 分母假设；整件三态平铺，禁拉伸
        this.drawTexturedModalRect(this.xPosition, this.yPosition, 0, stateV, this.width, this.height);
        this.mouseDragged(mc, mouseX, mouseY);
        int textX = this.xPosition + this.width / 2;
        int textY = this.yPosition + (this.height - 8) / 2;
        if (disabled) {
            // disabled 无阴影（契约 §4-4 提案；居中口径与 vanilla drawCenteredString 一致）
            mc.fontRenderer.drawString(
                this.displayString,
                textX - mc.fontRenderer.getStringWidth(this.displayString) / 2,
                textY,
                ReincarnationGuiPalette.BUTTON_TEXT_DISABLED);
        } else {
            this.drawCenteredString(
                mc.fontRenderer,
                this.displayString,
                textX,
                textY,
                hovered ? ReincarnationGuiPalette.BUTTON_TEXT_HOVER : ReincarnationGuiPalette.BUTTON_TEXT_NORMAL);
        }
    }
}
