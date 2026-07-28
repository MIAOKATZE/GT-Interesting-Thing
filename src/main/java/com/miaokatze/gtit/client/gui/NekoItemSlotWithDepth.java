package com.miaokatze.gtit.client.gui;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

/**
 * 带深度偏移的物品槽
 * <p>
 * 全量复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.fallingitem.ItemSlotWithDepth}。
 * <p>
 * 通过 {@link GL11#glTranslatef} 在 Z 轴上偏移，使物品在渲染时产生立体深度效果。
 * 主要用于掉落动画中，不同槽位的物品以不同深度渲染，增强视觉层次感。
 * <p>
 * 槽位的 depth 值等于其索引（由 NekoFallingItemSlotFactory 传入），
 * 因此索引越大的槽位渲染时越靠前（Z 值越大），形成自然的深度排序。
 */
public class NekoItemSlotWithDepth extends ItemSlot {

    /** Z 轴深度偏移量（正值向屏幕外，负值向屏幕内） */
    private final int depth;

    /** v1.7.15 诊断日志：上次 [NekoDraw] 日志输出时间戳（限频 1000ms） */
    private long lastDrawLogTime = 0;

    /**
     * 构造一个带深度偏移的物品槽
     * <p>
     * 注意：depth 参数在此类中仅用于 Z 轴渲染偏移，与槽位索引无关。
     * 实际的槽位索引通过后续的 {@code .slot(new ModularSlot(...))} 调用设置。
     *
     * @param depth Z 轴深度偏移量
     */
    public NekoItemSlotWithDepth(int depth) {
        this.depth = depth;
    }

    /**
     * 绘制物品槽
     * <p>
     * 在绘制前向 Z 轴正方向偏移 depth，绘制完成后恢复 Z 轴位置。
     * 确保深度偏移不影响后续组件的渲染。
     *
     * @param context     ModularUI 渲染上下文
     * @param widgetTheme 控件主题样式
     */
    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        // v1.7.15 诊断日志：限频 1000ms，仅 hasStack=true 时输出，确认 draw 调用和 slot 状态
        // depth 字段在此类中作为槽位索引使用（见类注释：槽位的 depth 值等于其索引）
        boolean hasStack = getSlot() != null && getSlot().getHasStack();
        if (hasStack) {
            long now = System.currentTimeMillis();
            if (now - lastDrawLogTime > 1000) {
                lastDrawLogTime = now;
                System.out.println(
                    "[NekoDraw] slot=" + depth
                        + " hasStack="
                        + hasStack
                        + " thread="
                        + Thread.currentThread()
                            .getName());
            }
        }
        // 向 Z 轴正方向偏移，产生深度效果
        GL11.glTranslatef(0f, 0f, depth);
        super.draw(context, widgetTheme);
        // 恢复 Z 轴位置，避免影响后续渲染
        GL11.glTranslatef(0f, 0f, -depth);
    }
}
