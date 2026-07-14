package com.miaokatze.gtit.client.gui;

import java.util.List;
import java.util.Random;

import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
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
 * 每个队列条目对应一簇粒子，粒子从物品图标底部向上运动并逐渐变淡消失（2.5 秒）。
 * 点击 Widget 区域可取回最早入队的物品（FIFO）。
 * <p>
 * <b>渲染时序</b>（v1.6.27 调整）：
 * <ul>
 * <li>0~1000ms：物品下落动画（NekoFallingItemSlotFactory），不渲染粒子</li>
 * <li>1000~3500ms：粒子动画播放（2500ms），物品已稳定在槽位</li>
 * </ul>
 * <p>
 * <b>渲染原理</b>（v1.6.28 调整）：
 * <ul>
 * <li>每帧根据 entry.creationTimeMs 计算粒子动画进度 progress (0~1)，延迟 1000ms 后开始</li>
 * <li>alpha = (1.0f - progress)²（v1.6.28 平方衰减，后期更透明，越来越透明）</li>
 * <li>上升起始点为物品图标底部（v1.6.28：向下偏移 16px，标准物品图标高度）</li>
 * <li>v1.6.28 调整为 5 种简单上升类预设（无螺旋/抛物线，上升幅度均减半），每个 entry 随机选一种：
 * <ul>
 * <li>预设 0：直线上升（2 粒子，上升 12.5px，轻微摆动）</li>
 * <li>预设 1：之字形上升（3 粒子，上升 10px，左右摆动）</li>
 * <li>预设 2：散开上升（4 粒子，上升 9px，向四周散开如花朵绽放）</li>
 * <li>预设 3：V 形上升（5 粒子，上升 9px，向中心汇聚）</li>
 * <li>预设 4：扇形上升（6 粒子，上升 9px，沿 -60°~+60° 扇形扩散）</li>
 * </ul>
 * </li>
 * <li>紫色粒子 RGB(220, 190, 240)（偏白的紫），白色粒子 RGB(245, 240, 255)（淡紫白）</li>
 * <li>使用 GL11 透明混合 + GuiDraw.drawRect 绘制小方块（v1.6.26 从 Tessellator 迁移）</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class NekoMeTransferParticleWidget extends Widget<NekoMeTransferParticleWidget> implements Interactable {

    // ==================== 常量 ====================

    /**
     * 粒子动画时长（毫秒），v1.6.27 调整为 2500ms
     * <p>
     * 时序：下落动画 1000ms（FALL_ANIMATION_DURATION_MS）+ 粒子动画 2500ms = 总 3500ms（ME_TRANSFER_DELAY_MS）
     */
    private static final long ANIMATION_DURATION_MS = 2500L;

    /**
     * 下落动画时长（毫秒），与 NekoFallingItemSlotFactory.FALL_ANIMATION_DURATION 一致
     * <p>
     * v1.6.27 新增：粒子动画在下落动画完成后才开始播放（物品完全落下后再播放动画）
     */
    private static final long FALL_ANIMATION_DURATION_MS = 1000L;

    /** 预设轨迹数量（v1.6.28 调整：5 种简单上升类预设，无螺旋/抛物线） */
    private static final int PRESET_COUNT = 5;

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
     * 渲染紫白渐变粒子簇。粒子随时间向上运动并变淡，2.5 秒后消失。
     * <p>
     * <b>渲染时序</b>（v1.6.27 调整）：
     * 下落动画 1000ms 完成后才开始粒子动画（物品完全落下后再播放）。
     * 前 1000ms（elapsed < FALL_ANIMATION_DURATION_MS）跳过不渲染。
     * <p>
     * <b>GL 状态管理</b>（v1.6.25 重写）：
     * 使用 GlStateManager（Forge 1.12.2 移植版，带内部状态追踪）+ 手动 save/restore。
     * 移除了 glPushAttrib/glPopAttrib（OpenGL 3.0+ 核心配置文件已移除，
     * Angelica GLSM 桥接层对其模拟不完整，可能导致后续 glDisable 不生效）。
     * <p>
     * <b>渲染方式</b>（v1.6.26 迁移）：
     * 从 Tessellator 迁移到 GuiDraw.drawRect（与项目其他 widget 一致）。
     * Tessellator 的 t.draw() 内部可能重置 GL 状态导致后续粒子纹理问题，
     * GuiDraw.drawRect 内部正确处理纹理和颜色状态，是项目认可的标准做法。
     * <p>
     * <b>预设轨迹</b>（v1.6.28 调整为 5 种简单上升类，无螺旋/抛物线）：
     * 5 种预设（直线 2/之字 3/散开 4/V 形 5/扇形 6 粒子），每个 entry 随机选一种（基于 entryIdx 固定种子）。
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

        // 当前时间戳，用于计算每个 entry 的动画进度（elapsed = now - creationTimeMs）
        long now = System.currentTimeMillis();
        int widgetW = getArea().width;
        int widgetH = getArea().height;

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

            // v1.6.27: 延迟 1000ms（等下落动画完成）后才开始粒子动画
            // 物品完全落下后再播放动画，避免下落过程中粒子已出现
            long elapsed = now - entry.creationTimeMs;
            if (elapsed < FALL_ANIMATION_DURATION_MS) {
                // 下落中，不渲染粒子（物品还未完全落下）
                continue;
            }

            // 粒子动画进度（延迟后，0~1）
            float particleElapsed = elapsed - FALL_ANIMATION_DURATION_MS;
            float progress = particleElapsed / ANIMATION_DURATION_MS;
            if (progress >= 1f) {
                // 粒子动画结束（应该已被服务端移除）
                continue;
            }

            // v1.6.28: 平方衰减，后期更透明（越来越透明）
            float alpha = (1.0f - progress) * (1.0f - progress);

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

            // v1.6.28: 上升起始点改为物品图标底部（向下偏移 16 像素，标准物品图标高度）
            entryCenterY += 16f;

            // v1.6.27: 随机选一种预设（基于 entryIdx 固定种子，避免每帧抖动）
            // 用质数 7919 作为种子乘数，避免与粒子索引冲突
            particleRng.setSeed(entryIdx * 7919L);
            int presetIdx = particleRng.nextInt(PRESET_COUNT);

            // v1.6.28: 5 种简单上升类预设（无螺旋，粒子数 2/3/4/5/6）
            switch (presetIdx) {
                case 0:
                    renderStraightRise(entryIdx, entryCenterX, entryCenterY, progress, alpha);
                    break; // 2 粒子直线
                case 1:
                    renderZigzagRise(entryIdx, entryCenterX, entryCenterY, progress, alpha);
                    break; // 3 粒子之字
                case 2:
                    renderScatterRise(entryIdx, entryCenterX, entryCenterY, progress, alpha);
                    break; // 4 粒子散开
                case 3:
                    renderVShapeRise(entryIdx, entryCenterX, entryCenterY, progress, alpha);
                    break; // 5 粒子 V 形
                case 4:
                    renderFanRise(entryIdx, entryCenterX, entryCenterY, progress, alpha);
                    break; // 6 粒子扇形
                default:
                    break;
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

    // ==================== 预设轨迹渲染（v1.6.28 调整为 5 种简单上升类） ====================

    /**
     * 预设0：直线上升（2 粒子）
     * <p>
     * 2 个粒子从物品底部直线上升，轻微 X 轴偏移避免完全重叠。
     * 上升幅度 12.5px（原 25px 减半）。
     *
     * @param entryIdx entry 索引
     * @param centerX  槽位中心 X（已含底部偏移）
     * @param centerY  槽位中心 Y（已含底部偏移）
     * @param progress 动画进度 (0~1)
     * @param alpha    透明度 (0~1)
     */
    private void renderStraightRise(int entryIdx, float centerX, float centerY, float progress, float alpha) {
        int particleCount = 2;
        // v1.6.28: 2 粒子 X 偏移（-3, 3）
        float[] xOffsets = { -3f, 3f };
        for (int p = 0; p < particleCount; p++) {
            // v1.6.28: 上升幅度减半 25→12.5
            float yOffset = -progress * 12.5f;
            // 轻微摆动（1 圈，幅度 2 像素）
            float xOffset = xOffsets[p]
                + (float) Math.sin(progress * 2f * (float) Math.PI + p * (float) (Math.PI / 2)) * 2f;

            float px = centerX + xOffset;
            float py = centerY + yOffset;

            renderParticle(entryIdx, p, px, py, progress, alpha);
        }
    }

    /**
     * 预设1：之字形上升（3 粒子）
     * <p>
     * 3 个粒子初始 X 偏移不同（-5, 0, 5），向上运动同时左右摆动。
     * 每个粒子相位不同，形成交错摆动效果。给人"蜿蜒传输"的感觉。
     * v1.6.28: 上升幅度减半 20→10。
     *
     * @param entryIdx entry 索引
     * @param centerX  槽位中心 X（已含底部偏移）
     * @param centerY  槽位中心 Y（已含底部偏移）
     * @param progress 动画进度 (0~1)
     * @param alpha    透明度 (0~1)
     */
    private void renderZigzagRise(int entryIdx, float centerX, float centerY, float progress, float alpha) {
        int particleCount = 3;
        for (int p = 0; p < particleCount; p++) {
            // 初始 X 偏移（-5, 0, 5）
            float initialXOffset = (p - 1) * 5f;
            // 相位不同（0, π/2, π）
            float phase = p * (float) (Math.PI / 2);
            // v1.6.28: 向上运动幅度减半 20→10
            float yOffset = -progress * 10f;
            // 左右摆动（2 圈，幅度 6 像素）
            float xOffset = (float) Math.sin(progress * 4f * (float) Math.PI + phase) * 6f;

            float px = centerX + initialXOffset + xOffset;
            float py = centerY + yOffset;

            renderParticle(entryIdx, p, px, py, progress, alpha);
        }
    }

    /**
     * 预设2：散开上升（4 粒子）
     * <p>
     * 4 个粒子从物品底部向四周散开同时上升，呈花朵绽放效果。
     * 上升幅度 9px（参考原 parabolic 的 18 减半）。
     *
     * @param entryIdx entry 索引
     * @param centerX  槽位中心 X（已含底部偏移）
     * @param centerY  槽位中心 Y（已含底部偏移）
     * @param progress 动画进度 (0~1)
     * @param alpha    透明度 (0~1)
     */
    private void renderScatterRise(int entryIdx, float centerX, float centerY, float progress, float alpha) {
        int particleCount = 4;
        for (int p = 0; p < particleCount; p++) {
            // 初始角度均匀分布（0°, 90°, 180°, 270°）
            float initialAngle = p * (float) (Math.PI / 2);
            // v1.6.28: 水平散开（0→8 像素）
            float scatterRadius = progress * 8f;
            // v1.6.28: 上升幅度 9px
            float yOffset = -progress * 9f;

            float px = centerX + (float) Math.cos(initialAngle) * scatterRadius;
            float py = centerY + (float) Math.sin(initialAngle) * scatterRadius * 0.5f + yOffset;

            renderParticle(entryIdx, p, px, py, progress, alpha);
        }
    }

    /**
     * 预设3：V形上升（5 粒子）
     * <p>
     * 5 个粒子初始位置水平分布，向中心汇聚后上升，形成 V 形轨迹。
     * 上升幅度 9px。
     *
     * @param entryIdx entry 索引
     * @param centerX  槽位中心 X（已含底部偏移）
     * @param centerY  槽位中心 Y（已含底部偏移）
     * @param progress 动画进度 (0~1)
     * @param alpha    透明度 (0~1)
     */
    private void renderVShapeRise(int entryIdx, float centerX, float centerY, float progress, float alpha) {
        int particleCount = 5;
        for (int p = 0; p < particleCount; p++) {
            // 初始 X 位置均匀分布（-8, -4, 0, 4, 8）
            float initialX = (p - 2) * 4f;
            // v1.6.28: V 形效果——向中心汇聚（progress=1 时全部到中心）
            float xOffset = initialX * (1.0f - progress);
            // v1.6.28: 上升幅度 9px
            float yOffset = -progress * 9f;

            float px = centerX + xOffset;
            float py = centerY + yOffset;

            renderParticle(entryIdx, p, px, py, progress, alpha);
        }
    }

    /**
     * 预设4：扇形上升（6 粒子）
     * <p>
     * 6 个粒子沿扇形方向（-60° 到 +60°）向外扩散同时上升，呈扇形喷洒效果。
     * 上升幅度 9px。
     *
     * @param entryIdx entry 索引
     * @param centerX  槽位中心 X（已含底部偏移）
     * @param centerY  槽位中心 Y（已含底部偏移）
     * @param progress 动画进度 (0~1)
     * @param alpha    透明度 (0~1)
     */
    private void renderFanRise(int entryIdx, float centerX, float centerY, float progress, float alpha) {
        int particleCount = 6;
        // 扇形张角 120°，6 个粒子均匀分布
        float fanSpread = (float) (Math.PI / 3); // 60° 半张角
        for (int p = 0; p < particleCount; p++) {
            // 角度从 -60° 到 +60° 均匀分布
            float angle = -fanSpread + (float) p / (particleCount - 1) * 2f * fanSpread;
            // v1.6.28: 沿角度方向向外移动（0→10 像素）
            float distance = progress * 10f;
            // v1.6.28: 上升幅度 9px
            float yOffset = -progress * 9f;

            float px = centerX + (float) Math.cos(angle) * distance;
            float py = centerY + (float) Math.sin(angle) * distance * 0.5f + yOffset;

            renderParticle(entryIdx, p, px, py, progress, alpha);
        }
    }

    /**
     * 渲染单个粒子（v1.6.27 抽取公共方法）
     * <p>
     * 计算粒子大小、颜色、ARGB，调用 GuiDraw.drawRect 绘制。
     * 粒子大小随进度缩小（PARTICLE_SIZE * (1 - progress * 0.5)），
     * 颜色紫色和白色交替（基于粒子索引 p）。
     *
     * @param entryIdx entry 索引
     * @param p        粒子索引（用于颜色交替）
     * @param px       粒子 X 坐标
     * @param py       粒子 Y 坐标
     * @param progress 动画进度 (0~1)，用于计算粒子大小
     * @param alpha    透明度 (0~1)
     */
    private void renderParticle(int entryIdx, int p, float px, float py, float progress, float alpha) {
        // 粒子大小随进度缩小（保留 50% 大小）
        float size = PARTICLE_SIZE * (1.0f - progress * 0.5f);

        // 颜色：紫色和白色交替
        boolean isPurple = p % 2 == 0;
        float r = isPurple ? PURPLE_R : WHITE_R;
        float g = isPurple ? PURPLE_G : WHITE_G;
        float b = isPurple ? PURPLE_B : WHITE_B;

        // v1.6.26: 用 GuiDraw.drawRect 绘制（ARGB 格式颜色）
        int argbColor = ((int) (alpha * 255) << 24) | ((int) (r * 255) << 16)
            | ((int) (g * 255) << 8)
            | (int) (b * 255);
        int drawX = (int) (px - size);
        int drawY = (int) (py - size);
        int drawSize = Math.max(1, (int) (size * 2));
        GuiDraw.drawRect(drawX, drawY, drawSize, drawSize, argbColor);
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
