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
import com.gtnewhorizons.modularui.api.GlStateManager;
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
 * <li>紫色粒子 RGB(220, 190, 240)（偏白的紫），白色粒子 RGB(245, 240, 255)（淡紫白）</li>
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

    /** 紫色粒子 R 分量（220/255，偏白的紫色） */
    private static final float PURPLE_R = 220f / 255f;
    /** 紫色粒子 G 分量（190/255） */
    private static final float PURPLE_G = 190f / 255f;
    /** 紫色粒子 B 分量（240/255） */
    private static final float PURPLE_B = 240f / 255f;
    /** 白色粒子 R 分量（245/255，带淡淡紫色的白） */
    private static final float WHITE_R = 245f / 255f;
    /** 白色粒子 G 分量（240/255） */
    private static final float WHITE_G = 240f / 255f;
    /** 白色粒子 B 分量（255/255） */
    private static final float WHITE_B = 255f / 255f;

    // ==================== 字段 ====================

    /** 客户端缓存的队列引用（从 NekoVMGuiV2 传入，与 clientMeTransferQueue 同一引用） */
    private final List<MeTransferEntry> queueRef;

    /**
     * 掉落槽工厂引用（v1.6.23 新增）
     * <p>
     * 供粒子定位槽位坐标使用。通过 {@link NekoFallingItemSlotFactory#getSlotScreenPos(int)}
     * 获取每个 entry 对应槽位的屏幕坐标，粒子围绕该坐标渲染。
     * </p>
     */
    private final NekoFallingItemSlotFactory factory;

    /** 取回回调（点击时触发，由 NekoVMGuiV2 设置 retrieveMeItemSync.setValue(true)） */
    private Runnable retrieveCallback;

    /** 随机数生成器（固定种子，避免每帧抖动） */
    private final Random particleRng = new Random(42L);

    // ==================== 构造器 ====================

    /**
     * 构造 ME 传输粒子动画 Widget（v1.6.23 新增 factory 参数）
     *
     * @param queueRef 客户端 ME 传输队列引用（与 NekoVMGuiV2.clientMeTransferQueue 同一引用）
     * @param factory  掉落槽工厂，供粒子定位槽位坐标（可为 null，回退到旧网格布局）
     */
    public NekoMeTransferParticleWidget(List<MeTransferEntry> queueRef, NekoFallingItemSlotFactory factory) {
        this.queueRef = queueRef;
        this.factory = factory;
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
     * <b>GL 状态管理</b>（v1.6.25 重写）：
     * 使用 GlStateManager（Forge 1.12.2 移植版，带内部状态追踪）+ 手动 save/restore。
     * 移除了 glPushAttrib/glPopAttrib（OpenGL 3.0+ 核心配置文件已移除，
     * Angelica GLSM 桥接层对其模拟不完整，可能导致后续 glDisable 不生效）。
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

        // v1.6.24 临时日志：用于排查粒子动画未渲染根因，确认 draw 是否被调用、队列是否非空
        long now = System.currentTimeMillis();
        int widgetW = getArea().width;
        int widgetH = getArea().height;
        System.out.println(
            "[NekoParticle] draw called, queueSize=" + queueRef
                .size() + ", widgetW=" + widgetW + ", widgetH=" + widgetH + ", now=" + now);

        // v1.6.24: 获取当前绘制层 Z 坐标，避免被其他 widget（如 OVERHANG 装饰）的渲染覆盖
        // 项目内 NekoTradeItemDisplayWidget 等 3 处已使用此 API
        float drawZ = context.getCurrentDrawingZ();

        // Widget 内部坐标（0,0 为左上角），通过 getArea() 获取实际渲染尺寸
        int centerX = widgetW / 2;
        int centerY = widgetH / 2;

        // v1.6.25: 移除 GL11.glPushAttrib(GL_ALL_ATTRIB_BITS) 和 GL11.glPushMatrix()
        // 原因：glPushAttrib 在 OpenGL 3.0+ 核心配置文件中已移除，Angelica GLSM 桥接层
        // 对其模拟不完整（见 MinecraftForge issue #1637，ModularUI 1.3.4 GlStateManager
        // 源码也将 pushAttrib/popAttrib 标注为 "Do not use"）。
        // 后续 glDisable(GL_TEXTURE_2D) 等状态变更可能未正确生效，导致 Tessellator
        // 顶点无 UV 坐标却采样到无效纹理，片元被丢弃 → 粒子不可见。
        // 改用 GlStateManager + 手动 save/restore 替代。

        // --- 手动保存当前 GL 状态（替代 glPushAttrib）---
        // 记录需要恢复的状态，draw 结束后逐一恢复，避免影响后续 widget 渲染
        boolean wasBlendEnabled = GL11.glGetBoolean(GL11.GL_BLEND);
        boolean wasTexture2DEnabled = GL11.glGetBoolean(GL11.GL_TEXTURE_2D);
        boolean wasLightingEnabled = GL11.glGetBoolean(GL11.GL_LIGHTING);
        boolean wasDepthTestEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
        boolean wasAlphaTestEnabled = GL11.glGetBoolean(GL11.GL_ALPHA_TEST);
        int prevBlendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        int prevBlendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);

        // --- 设置粒子绘制所需的 GL 状态 ---
        // 使用 GlStateManager（Forge 1.12.2 移植版，带内部状态追踪）替代原始 GL11 调用，
        // 与 GT5U LineChartWidget 保持一致
        GlStateManager.enableBlend();
        // v1.6.25: 用 tryBlendFuncSeparate 替代 glBlendFunc（与 LineChartWidget 一致），
        // 内部调用 OpenGlHelper.glBlendFunc，兼容 Angelica 着色器管线
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        // v1.6.25: 启用 Alpha 测试（对应根因 3，与 LineChartWidget 一致）
        // 之前缺少 enableAlpha，可能导致 alpha 测试函数异常时片元被丢弃
        GlStateManager.enableAlpha();

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
                // v1.6.24 临时日志：entry 已过期被跳过
                System.out.println(
                    "[NekoParticle] SKIPPED entry=" + entryIdx
                        + ", creationTimeMs="
                        + entry.creationTimeMs
                        + ", elapsed="
                        + elapsed
                        + ", progress="
                        + progress);
                // 已过期，不渲染（应该已被服务端移除）
                continue;
            }

            // alpha 随进度线性渐淡
            float alpha = 1.0f - progress;

            // v1.6.23: 粒子围绕出货槽中的物品渲染
            // 通过 factory.getSlotScreenPos(slotIndex) 获取槽位坐标
            // slotIndex == -1（旧存档兼容）时回退到旧网格布局
            float entryCenterX;
            float entryCenterY;
            if (entry.slotIndex >= 0 && factory != null) {
                int[] slotPos = factory.getSlotScreenPos(entry.slotIndex);
                entryCenterX = slotPos[0];
                entryCenterY = slotPos[1];
            } else {
                // 回退：旧网格布局（6 列）
                entryCenterX = centerX + (entryIdx % 6 - 2.5f) * 18f;
                entryCenterY = centerY + (entryIdx / 6) * 18f - 9f;
            }

            // v1.6.24 临时日志：输出渲染关键参数（只输出第一个粒子的坐标避免日志爆炸）
            System.out.println(
                "[NekoParticle] RENDER entry=" + entryIdx
                    + ", slotIndex="
                    + entry.slotIndex
                    + ", creationTimeMs="
                    + entry.creationTimeMs
                    + ", elapsed="
                    + elapsed
                    + ", progress="
                    + progress
                    + ", alpha="
                    + alpha
                    + ", entryCenterX="
                    + entryCenterX
                    + ", entryCenterY="
                    + entryCenterY
                    + ", drawZ="
                    + drawZ);

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
                // v1.6.24: 使用 Tessellator 标准颜色 API（与 GT5U LineChartWidget 一致），
                // 替代 GL11.glColor4f，避免 Tessellator 内部状态覆盖全局颜色
                Tessellator t = Tessellator.instance;
                t.startDrawing(GL11.GL_QUADS);
                t.setColorRGBA((int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (alpha * 255));
                // v1.6.24: 顶点 z 使用 context.getCurrentDrawingZ()，避免被后续 widget 覆盖
                t.addVertex(px - size, py - size, drawZ);
                t.addVertex(px + size, py - size, drawZ);
                t.addVertex(px + size, py + size, drawZ);
                t.addVertex(px - size, py + size, drawZ);
                t.draw();
            }
        }

        // v1.6.25: 手动恢复 GL 状态（替代 glPopAttrib + glPopMatrix）
        // 逐一恢复到 draw 调用前的状态，避免影响后续 widget 渲染
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        if (wasTexture2DEnabled) {
            GlStateManager.enableTexture2D();
        } else {
            GlStateManager.disableTexture2D();
        }
        if (wasLightingEnabled) {
            GlStateManager.enableLighting();
        } else {
            GlStateManager.disableLighting();
        }
        if (wasDepthTestEnabled) {
            GlStateManager.enableDepth();
        } else {
            GlStateManager.disableDepth();
        }
        if (wasAlphaTestEnabled) {
            GlStateManager.enableAlpha();
        } else {
            GlStateManager.disableAlpha();
        }
        if (wasBlendEnabled) {
            GlStateManager.enableBlend();
            // 恢复原来的 blendFunc（用 tryBlendFuncSeparate 4 参数形式，
            // 内部调用 OpenGlHelper.glBlendFunc 设置完整 4 因子）
            // ONE=1, ZERO=0 是默认的 srcFactorAlpha/dstFactorAlpha
            GlStateManager.tryBlendFuncSeparate(prevBlendSrc, prevBlendDst, 1, 0);
        } else {
            GlStateManager.disableBlend();
        }
    }

    // ==================== Tooltip ====================

    /**
     * 构建 Tooltip
     * <p>
     * 当队列非空时显示提示文字：
     * <ul>
     * <li>紫色：提示物品正在传输中</li>
     * <li>灰色：队列剩余物品数</li>
     * </ul>
     * v1.6.23: 不再提示"点击取回"，改为提示"直接点击物品取出"
     *
     * @param tooltip Tooltip 构建器
     */
    private void buildTooltip(RichTooltip tooltip) {
        if (queueRef != null && !queueRef.isEmpty()) {
            tooltip.addLine(IKey.str(EnumChatFormatting.LIGHT_PURPLE + "物品正在传输至 ME 网络..."));
            tooltip.addLine(IKey.str(EnumChatFormatting.GRAY + "直接点击物品可取出并中断传输"));
            tooltip.addLine(IKey.str(EnumChatFormatting.GRAY + "队列中还有 " + queueRef.size() + " 个物品待传输"));
        }
    }

    // ==================== 鼠标交互 ====================

    /**
     * 鼠标按下处理
     * <p>
     * v1.6.23: 粒子 Widget 纯视觉，不拦截鼠标事件，返回 IGNORE 让点击穿透到出货槽。
     * 玩家直接点击出货槽中的物品取出，取出后系统自动跳过 ME 注入（槽位为空时 processMeTransferQueue 跳过）。
     *
     * @param mouseButton 鼠标按钮（0=左键, 1=右键, 2=中键）
     * @return 总是 IGNORE，让点击穿透到下层出货槽
     */
    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        // v1.6.23: 不拦截鼠标，让点击穿透到出货槽
        return Interactable.Result.IGNORE;
    }
}
