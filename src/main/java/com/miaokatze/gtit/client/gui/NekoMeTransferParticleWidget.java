package com.miaokatze.gtit.client.gui;

import java.util.List;
import java.util.Random;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2.MeTransferEntry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * ME 传输粒子动画 Widget
 * <p>
 * 在 GUI 内渲染 ME 传输队列中物品的紫白粒子动画。
 * 每个队列条目对应一簇粒子，粒子从中心散布并逐渐变淡消失（3 秒）。
 * 点击 Widget 区域可取回最早入队的物品（FIFO）。
 * <p>
 * <b>渲染原理</b>：
 * <ul>
 * <li>每帧根据 entry.creationTimeMs 计算动画进度 progress (0~1)</li>
 * <li>alpha = 1.0f - progress（线性渐淡）</li>
 * <li>粒子位置：基于 entry 索引 + 固定随机种子（避免每帧抖动）</li>
 * <li>紫色粒子 RGB(180, 80, 220)，白色粒子 RGB(255, 255, 255)</li>
 * <li>使用 GL11 透明混合 + Tessellator 绘制小方块</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class NekoMeTransferParticleWidget extends Widget<NekoMeTransferParticleWidget> implements Interactable {

    // ==================== 常量 ====================

    /** 粒子动画总时长（毫秒），与服务端 ME_TRANSFER_DELAY_MS 一致 */
    private static final long ANIMATION_DURATION_MS = 3000L;

    /** 每个队列条目渲染的粒子数 */
    private static final int PARTICLES_PER_ENTRY = 12;

    /** 粒子基础大小（像素） */
    private static final float PARTICLE_SIZE = 2.5f;

    /** 同时渲染的最大队列条目数（防止队列过长时性能下降） */
    private static final int MAX_ENTRIES_TO_RENDER = 18;

    // ==================== 紫白粒子颜色（归一化 0~1） ====================

    /** 紫色粒子 R 分量（180/255） */
    private static final float PURPLE_R = 180f / 255f;
    /** 紫色粒子 G 分量（80/255） */
    private static final float PURPLE_G = 80f / 255f;
    /** 紫色粒子 B 分量（220/255） */
    private static final float PURPLE_B = 220f / 255f;
    /** 白色粒子 R 分量 */
    private static final float WHITE_R = 1.0f;
    /** 白色粒子 G 分量 */
    private static final float WHITE_G = 1.0f;
    /** 白色粒子 B 分量 */
    private static final float WHITE_B = 1.0f;

    // ==================== 字段 ====================

    /** 客户端缓存的队列引用（从 NekoVMGuiV2 传入，与 clientMeTransferQueue 同一引用） */
    private final List<MeTransferEntry> queueRef;

    /** 取回回调（点击时触发，由 NekoVMGuiV2 设置 retrieveMeItemSync.setValue(true)） */
    private Runnable retrieveCallback;

    /** 随机数生成器（固定种子，避免每帧抖动） */
    private final Random particleRng = new Random(42L);

    // ==================== 构造器 ====================

    /**
     * 构造 ME 传输粒子动画 Widget
     *
     * @param queueRef 客户端 ME 传输队列引用（与 NekoVMGuiV2.clientMeTransferQueue 同一引用）
     */
    public NekoMeTransferParticleWidget(List<MeTransferEntry> queueRef) {
        this.queueRef = queueRef;
        // 配置动态 Tooltip（每次显示时重新构建，确保队列状态最新）
        this.tooltipDynamic(tooltip -> buildTooltip((RichTooltip) tooltip));
        this.tooltipAutoUpdate(true);
    }

    // ==================== 回调设置 ====================

    /**
     * 设置取回回调
     * <p>
     * 点击 Widget 时触发，通常由 NekoVMGuiV2 设置为
     * {@code retrieveMeItemSync.setValue(true)} 以通知服务端取回最早入队物品。
     *
     * @param callback 取回回调
     * @return 自身（链式调用）
     */
    public NekoMeTransferParticleWidget onRetrieve(Runnable callback) {
        this.retrieveCallback = callback;
        return this;
    }

    // ==================== 渲染 ====================

    /**
     * 每帧绘制粒子动画
     * <p>
     * 遍历 ME 传输队列中每个条目，根据其入队时间计算动画进度，
     * 渲染紫白渐变粒子簇。粒子随时间扩散并变淡，3 秒后消失。
     * <p>
     * <b>GL 状态管理</b>：使用 glPushAttrib/glPopAttrib + glPushMatrix/glPopMatrix
     * 严格保护 GL 状态，确保不影响后续渲染。
     *
     * @param context     ModularUI 渲染上下文
     * @param widgetTheme Widget 主题
     */
    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        // 队列为空时不渲染
        if (queueRef == null || queueRef.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        // Widget 内部坐标（0,0 为左上角），通过 getArea() 获取实际渲染尺寸
        int widgetW = getArea().width;
        int widgetH = getArea().height;
        int centerX = widgetW / 2;
        int centerY = widgetH / 2;

        // GL 状态保护：保存所有属性位和矩阵状态
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        // 启用透明混合，禁用纹理和光照（纯色粒子绘制）
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        // 遍历队列，渲染每个 entry 的粒子簇
        int renderCount = Math.min(queueRef.size(), MAX_ENTRIES_TO_RENDER);
        for (int entryIdx = 0; entryIdx < renderCount; entryIdx++) {
            MeTransferEntry entry = queueRef.get(entryIdx);
            if (entry == null) {
                continue;
            }

            // 计算动画进度 progress (0~1)
            long elapsed = now - entry.creationTimeMs;
            float progress = (float) elapsed / ANIMATION_DURATION_MS;
            if (progress < 0f) {
                progress = 0f;
            }
            if (progress >= 1f) {
                // 已过期，不渲染（应该已被服务端移除）
                continue;
            }

            // alpha 随进度线性渐淡
            float alpha = 1.0f - progress;

            // entry 的中心点基于 entryIdx 在 Widget 内网格分布（6 列）
            float entryCenterX = centerX + (entryIdx % 6 - 2.5f) * 18f;
            float entryCenterY = centerY + (entryIdx / 6) * 18f - 9f;

            // 渲染该 entry 的粒子簇
            for (int p = 0; p < PARTICLES_PER_ENTRY; p++) {
                // 固定种子（基于 entryIdx 和粒子索引），避免每帧抖动
                particleRng.setSeed(entryIdx * 1000L + p);

                // 粒子初始角度（随机方向）
                float angle = particleRng.nextFloat() * (float) (Math.PI * 2);
                // 半径随时间扩大（扩散效果）
                float radius = 5f + progress * 15f;
                float px = entryCenterX + (float) Math.cos(angle) * radius;
                float py = entryCenterY + (float) Math.sin(angle) * radius;

                // 粒子大小随进度缩小
                float size = PARTICLE_SIZE * (1.0f - progress * 0.5f);

                // 颜色：紫色和白色交替
                boolean isPurple = p % 2 == 0;
                float r = isPurple ? PURPLE_R : WHITE_R;
                float g = isPurple ? PURPLE_G : WHITE_G;
                float b = isPurple ? PURPLE_B : WHITE_B;

                // 用 Tessellator 绘制小方块（GL_QUADS）
                GL11.glColor4f(r, g, b, alpha);
                Tessellator t = Tessellator.instance;
                t.startDrawing(GL11.GL_QUADS);
                t.addVertex(px - size, py - size, 0);
                t.addVertex(px + size, py - size, 0);
                t.addVertex(px + size, py + size, 0);
                t.addVertex(px - size, py + size, 0);
                t.draw();
            }
        }

        // 恢复 GL 状态
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    // ==================== Tooltip ====================

    /**
     * 构建 Tooltip
     * <p>
     * 当队列非空时显示提示文字：
     * <ul>
     * <li>紫色：点击取回提示</li>
     * <li>灰色：队列剩余物品数</li>
     * </ul>
     *
     * @param tooltip Tooltip 构建器
     */
    private void buildTooltip(RichTooltip tooltip) {
        if (queueRef != null && !queueRef.isEmpty()) {
            tooltip.addLine(IKey.str(EnumChatFormatting.LIGHT_PURPLE + "点击此区域取回最早入队的物品"));
            tooltip.addLine(IKey.str(EnumChatFormatting.GRAY + "队列中还有 " + queueRef.size() + " 个物品待传输"));
        }
    }

    // ==================== 鼠标交互 ====================

    /**
     * 鼠标按下处理
     * <p>
     * 左键点击时触发取回回调（FIFO 取回最早入队物品）。
     *
     * @param mouseButton 鼠标按钮（0=左键, 1=右键, 2=中键）
     * @return SUCCESS 表示已处理，IGNORE 表示未处理
     */
    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        // 仅响应左键
        if (mouseButton != 0) {
            return Interactable.Result.IGNORE;
        }
        // 队列为空时不响应
        if (queueRef == null || queueRef.isEmpty()) {
            return Interactable.Result.IGNORE;
        }
        // 触发取回回调
        if (retrieveCallback != null) {
            retrieveCallback.run();
            return Interactable.Result.SUCCESS;
        }
        return Interactable.Result.IGNORE;
    }
}
