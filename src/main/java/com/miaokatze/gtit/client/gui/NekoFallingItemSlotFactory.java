package com.miaokatze.gtit.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.cleanroommc.modularui.animation.Animator;
import com.cleanroommc.modularui.animation.IAnimatable;
import com.cleanroommc.modularui.animation.MutableObjectAnimator;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Interpolation;
import com.cleanroommc.modularui.utils.Interpolations;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.widgets.TransformWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

/**
 * 掉落物品槽工厂
 * <p>
 * 全量复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.fallingitem.FallingItemSlotFactory}。
 * <p>
 * 为猫猫售货机的每个输出槽创建一个带弹跳掉落动画的物品槽组件。
 * 物品从指定高度以弹跳效果落入槽位，使用黄金比例共轭分布 X 轴位置。
 * <p>
 * 与 VM 原版的差异：
 * <ul>
 * <li>构造器接收 outputSlots 参数，替代 VM 的 MTEVendingMachine.OUTPUT_SLOTS 常量</li>
 * <li>使用 {@link NekoItemSlotWithDepth} 替代 VM 的 ItemSlotWithDepth</li>
 * <li>Pos 类作为内部类包含，替代 VM 的独立 Pos.java</li>
 * </ul>
 */
public class NekoFallingItemSlotFactory {

    /** 掉落动画持续时间（毫秒），与 VM 原版一致 */
    private static final int FALL_ANIMATION_DURATION = 1000;

    /** X 轴位置最大值（用于黄金比例分布的范围限制），与 VM 原版一致 */
    private static final int MAX_X_POS = 24;

    /** 输出槽 X 轴位置列表（按黄金比例共轭分布） */
    private final List<Integer> outputSlotXPositions;

    /** 输出物品容器 */
    private final ItemStackHandler outputItems;

    /** 物品掉落高度（像素） */
    private final int fallDistance;

    /** v1.7.8 B：已创建的物品槽组件（按创建顺序），供 GUI 层收集后强制同步输出槽 */
    private final List<ItemSlot> createdItemSlots = new ArrayList<>();

    /**
     * 构造一个掉落物品槽工厂
     *
     * @param outputItems  输出物品容器
     * @param fallDistance 物品掉落高度（像素）
     * @param outputSlots  输出槽数量
     */
    public NekoFallingItemSlotFactory(ItemStackHandler outputItems, int fallDistance, int outputSlots) {
        this.outputItems = outputItems;
        this.fallDistance = fallDistance;
        this.outputSlotXPositions = computeXPositions(outputSlots);
    }

    /**
     * 计算输出槽的 X 轴位置列表
     * <p>
     * 使用黄金比例共轭 (golden ratio conjugate) 分布算法：
     * 
     * <pre>
     * phi = (sqrt(5) - 1) / 2 ≈ 0.618
     * position[i] = (int) ((i * phi % 1.0) * MAX_X_POS)
     * </pre>
     * <p>
     * 黄金比例共轭分布的原理：
     * <ul>
     * <li>每个新槽位填入当前最大的间隙，实现均匀覆盖</li>
     * <li>呈现非线性的外观，避免槽位排列过于规律</li>
     * <li>适用于任意数量的槽位，无需预设位置表</li>
     * </ul>
     * <p>
     * 示例（MAX_X_POS=24）：
     * 
     * <pre>
     * i=0: (0 * 0.618 % 1.0) * 24 = 0
     * i=1: (0.618 % 1.0) * 24 = 14
     * i=2: (1.236 % 1.0) * 24 = 0.236 * 24 = 5
     * i=3: (1.854 % 1.0) * 24 = 0.854 * 24 = 20
     * ...形成均匀但非线性的分布
     * </pre>
     *
     * @param outputSlots 输出槽数量
     * @return X 轴位置列表
     */
    private static List<Integer> computeXPositions(int outputSlots) {
        // 黄金比例共轭：每个新槽位填入当前最大的间隙，实现均匀覆盖且呈非线性外观
        final double phi = (Math.sqrt(5) - 1) / 2.0;
        List<Integer> positions = new ArrayList<>(outputSlots);
        for (int i = 0; i < outputSlots; i++) {
            positions.add((int) ((i * phi % 1.0) * MAX_X_POS));
        }
        return positions;
    }

    /**
     * 获取指定索引的掉落物品槽组件
     * <p>
     * 创建一个 {@link TransformWidget} 包装的物品槽，带有弹跳掉落动画。
     * 当槽位中的物品发生变化时（新物品放入），自动触发掉落动画。
     *
     * @param index 槽位索引
     * @return 包装了掉落动画的 TransformWidget
     */
    public TransformWidget getFallingItemSlot(int index) {
        // 初始位置设为顶部 (y=-1)，避免物品先出现在底部再跳到顶部
        final Pos fallingPosition = new Pos(this.outputSlotXPositions.get(index), -1);
        Animator fallingPositionAnimation = createFallingAnimation(fallingPosition, this.fallDistance);
        IWidget widget = createItemSlot(index, fallingPositionAnimation);
        // v1.7.8 B：记录内部物品槽引用，供 GUI 层（NekoVMGuiV2）强制同步输出槽使用
        if (widget instanceof ItemSlot) {
            createdItemSlots.add((ItemSlot) widget);
        }
        // TransformWidget 根据动画驱动的 fallingPosition 实时平移物品槽
        return new TransformWidget(widget)
            .transform(stack -> stack.translate((float) fallingPosition.getX(), (float) fallingPosition.getY()));
    }

    /**
     * 获取已创建的物品槽组件列表（v1.7.8 B）
     * <p>
     * 每次 {@link #getFallingItemSlot(int)} 创建的内部 {@link ItemSlot} 按序记录，
     * 供 GUI 层收集引用后遍历 sync handler 调 forceSyncItem() 强制同步输出槽。
     *
     * @return 已创建的物品槽列表（随 getFallingItemSlot 调用增长）
     */
    public List<ItemSlot> getCreatedItemSlots() {
        return createdItemSlots;
    }

    /**
     * 获取指定槽位在 dispenserChute 内的屏幕坐标（v1.6.23 新增）
     * <p>
     * 供 {@link NekoMeTransferParticleWidget} 定位粒子渲染位置使用。
     * 返回的坐标是相对于 dispenserChute ParentWidget 左上角的偏移：
     * <ul>
     * <li>X = outputSlotXPositions.get(index)（黄金比例分布的 X 偏移，0~23 像素）</li>
     * <li>Y = fallDistance - 1（掉落动画终点位置，与槽位最终落点一致）</li>
     * </ul>
     *
     * @param index 槽位索引（0~outputSlots-1）
     * @return int[]{x, y}，索引无效时返回 {0, 0}
     */
    public int[] getSlotScreenPos(int index) {
        if (index < 0 || index >= outputSlotXPositions.size()) {
            return new int[] { 0, 0 };
        }
        // Y = fallDistance - 1（与 getFallingItemSlot 中 fallingPosition 初始 y=-1 + fallDistance 一致）
        return new int[] { outputSlotXPositions.get(index), fallDistance - 1 };
    }

    /**
     * 创建带掉落动画监听的物品槽
     * <p>
     * 使用 {@link NekoItemSlotWithDepth} 实现深度效果。
     * 当槽位物品变化（非初始化、非仅数量变化、有新物品）时，触发弹跳动画。
     *
     * @param index    槽位索引
     * @param animator 掉落动画控制器
     * @return 配置好的物品槽组件
     */
    private IWidget createItemSlot(int index, Animator animator) {
        // v1.7.8 B：移除原 .setEnabledIf(slot -> slot.getSlot().getHasStack())——
        // setEnabledIf 每 tick 重评估是首帧闪烁源（物品已入槽但首帧未启用不绘制），
        // 且空槽本无可画内容，无需按有无物品动态启停
        return new NekoItemSlotWithDepth(index).slot(
            new ModularSlot(this.outputItems, index).accessibility(false, true)
                .slotGroup("outputSlotGroup")
                .changeListener((newItem, onlyAmountChanged, client, init) -> {
                    // 仅在非初始化、有新物品、非仅数量变化时触发掉落动画
                    if (!init && newItem != null && !onlyAmountChanged) {
                        animator.reset();
                        animator.animate();
                    }
                }))
            .background(IDrawable.EMPTY)
            .disableHoverBackground();
    }

    /**
     * 创建弹跳掉落动画
     * <p>
     * 动画从 (x, -1) 位置弹跳到 (x, fallDistance) 位置，
     * 使用 BOUNCE_OUT 插值实现自然的弹跳效果。
     * 动画时长 {@value #FALL_ANIMATION_DURATION}ms。
     * <p>
     * 注意：animatedPos 同时作为动画的可变状态对象和 TransformWidget 的位置源。
     * MutableObjectAnimator 在每帧将插值结果写入 animatedPos，TransformWidget 读取其值进行平移。
     * <p>
     * 重要：animatedPos 的初始值应为顶部 (y=-1)，与动画起点一致，
     * 避免物品在动画启动前先渲染在底部位置造成视觉跳跃。
     *
     * @param animatedPos  动画目标位置（同时作为动画的可变状态对象，初始 y 应为 -1）
     * @param fallDistance 掉落终点 Y 坐标（最终落点位置）
     * @return 配置好的动画控制器
     */
    private static Animator createFallingAnimation(Pos animatedPos, int fallDistance) {
        // 起点在槽位上方 (y=-1)，终点为最终位置 (y=fallDistance)
        return new MutableObjectAnimator<>(
            animatedPos,
            new Pos(animatedPos.getX(), -1),
            new Pos(animatedPos.getX(), fallDistance)).bounds(0, 1)
                .curve(Interpolation.BOUNCE_OUT)
                .duration(FALL_ANIMATION_DURATION);
    }

    /**
     * 位置类 - 用于掉落动画
     * <p>
     * 实现 {@link IAnimatable} 接口，支持在动画帧之间进行线性插值。
     * 全量复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.fallingitem.Pos}。
     * <p>
     * 作为 {@link NekoFallingItemSlotFactory} 的内部类，避免额外创建独立文件。
     */
    private static class Pos implements IAnimatable<Pos> {

        /** X 轴坐标 */
        private int x;

        /** Y 轴坐标 */
        private int y;

        /**
         * 构造一个位置
         *
         * @param x X 轴坐标
         * @param y Y 轴坐标
         */
        public Pos(int x, int y) {
            this.x = x;
            this.y = y;
        }

        /**
         * 线性插值
         * <p>
         * 在 start 和 end 之间按比例 t 进行插值，更新当前位置。
         *
         * @param start 起始位置
         * @param end   结束位置
         * @param t     插值比例 (0.0~1.0)
         * @return 当前位置（this）
         */
        @Override
        public Pos interpolate(Pos start, Pos end, float t) {
            this.x = Interpolations.lerp(start.x, end.x, t);
            this.y = Interpolations.lerp(start.y, end.y, t);
            return this;
        }

        /**
         * 创建当前位置的副本
         *
         * @return 新的 Pos 对象，值与当前相同
         */
        @Override
        public Pos copyOrImmutable() {
            return new Pos(x, y);
        }

        /**
         * 获取 X 轴坐标
         *
         * @return X 轴坐标
         */
        public int getX() {
            return x;
        }

        /**
         * 获取 Y 轴坐标
         *
         * @return Y 轴坐标
         */
        public int getY() {
            return y;
        }
    }
}
