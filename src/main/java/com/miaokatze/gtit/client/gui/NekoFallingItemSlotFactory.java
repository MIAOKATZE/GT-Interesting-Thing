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
 * <p>
 * v1.7.14：回归 v1.6.34 的动画处理方式（初始位置 (x,-1) + setEnabledIf + 无 client 检查），
 * 新增 init=true 时将位置设为终点 (x, fallDistance) 的逻辑，解决 v1.7.13 物品无下落动画的问题。
 * <p>
 * v1.7.15：回归 VM 原版（VendingMachine-0.4.95 第 42 行）简洁逻辑。
 * <ul>
 * <li>初始位置改回 (x, fallDistance)——v1.7.14 的 (x,-1) 在客户端 changeListener 不触发时导致物品完全不可见
 * （藏在悬垂装饰后）；(x, fallDistance) 至少物品可见。</li>
 * <li>移除 init=true 分支处理——初始位置已是终点 (x, fallDistance)，init 同步时无需额外处理。</li>
 * <li>createFallingAnimation 终点用 animatedPos.copyOrImmutable()，与 VM 原版一致。</li>
 * <li>新增 [NekoFactory]/[NekoEnabled]/[NekoDraw] 诊断日志（限频 1000ms），用于排查客户端 changeListener
 * 从未触发的问题（v1.7.14 日志中所有 [NekoFall] 都是 Server thread/client=false）。</li>
 * </ul>
 */
public class NekoFallingItemSlotFactory {

    /** 掉落动画持续时间（毫秒），与 VM 原版一致 */
    private static final int FALL_ANIMATION_DURATION = 1000;

    /** X 轴位置最大值（用于黄金比例分布的范围限制），与 VM 原版一致 */
    private static final int MAX_X_POS = 24;

    /** v1.7.15 诊断日志：上次 [NekoEnabled] 日志输出时间戳（限频 1000ms，所有 slot 共享） */
    private static long lastEnabledLogTime = 0;

    /** 输出槽 X 轴位置列表（按黄金比例共轭分布） */
    private final List<Integer> outputSlotXPositions;

    /** 输出物品容器 */
    private final ItemStackHandler outputItems;

    /** 物品掉落高度（像素） */
    private final int fallDistance;

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
     * <p>
     * v1.7.15：回归 VM 原版（VendingMachine-0.4.95 第 42 行）——初始位置设为终点 (x, fallDistance)。
     * <ul>
     * <li>初始位置即终点，物品默认可见（即便客户端 changeListener 不触发也不会"藏起来"）</li>
     * <li>init=true 同步时无需额外处理（初始位置已是终点）</li>
     * <li>init=false 新物品到达时，changeListener 触发动画，Pos 从 (x,-1) 弹跳到 (x,fallDistance)</li>
     * </ul>
     * v1.7.14 的 (x,-1) 初始位置在客户端 changeListener 不触发时导致物品完全不可见（藏在悬垂装饰后），
     * v1.7.15 回归 VM 原版以至少保证物品可见。
     *
     * @param index 槽位索引
     * @return 包装了掉落动画的 TransformWidget
     */
    public TransformWidget getFallingItemSlot(int index) {
        // v1.7.15 诊断日志：确认 widget 创建（客户端/服务端），用于排查客户端 changeListener 不触发的问题
        System.out.println(
            "[NekoFactory] getFallingItemSlot index=" + index
                + " thread="
                + Thread.currentThread()
                    .getName()
                + " fallDistance="
                + this.fallDistance
                + " initialPos=(x="
                + this.outputSlotXPositions.get(index)
                + ", y="
                + this.fallDistance
                + ")");
        // v1.7.15：回归 VM 原版——初始位置设为终点 (x, fallDistance)，物品默认可见
        // - 即便客户端 changeListener 不触发，物品也不会藏在悬垂装饰后
        // - init=false 新物品到达时动画从 (x,-1) 弹跳到 (x,fallDistance)
        final Pos fallingPosition = new Pos(this.outputSlotXPositions.get(index), this.fallDistance);
        Animator fallingPositionAnimation = createFallingAnimation(fallingPosition, this.fallDistance);
        IWidget widget = createItemSlot(index, fallingPositionAnimation);
        // TransformWidget 根据动画驱动的 fallingPosition 实时平移物品槽
        return new TransformWidget(widget)
            .transform(stack -> stack.translate((float) fallingPosition.getX(), (float) fallingPosition.getY()));
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
        // Y = fallDistance - 1（掉落动画终点/槽位静止落点区域，供粒子定位）
        return new int[] { outputSlotXPositions.get(index), fallDistance - 1 };
    }

    /**
     * 创建带掉落动画监听的物品槽
     * <p>
     * 使用 {@link NekoItemSlotWithDepth} 实现深度效果。
     * 当槽位物品变化时，根据同步类型触发不同行为：
     * <ul>
     * <li>init=true（GUI 重开同步）：v1.7.15 起无需额外处理（初始位置已是终点 (x,fallDistance)，物品可见）</li>
     * <li>init=false + 新物品（交易产出）：播放下落动画，从 (x,-1) 弹跳到 (x,fallDistance)</li>
     * </ul>
     * <p>
     * v1.7.15：回归 VM 原版简洁逻辑——仅在非 init、有新物品、非仅数量变化时触发动画。
     * 移除 v1.7.14 的 init=true 分支处理（初始位置已是终点，init 同步时无需额外处理）。
     * <p>
     * v1.7.14 关键修复（保留）：重新加入 setEnabledIf(slot -> getHasStack())。
     * <ul>
     * <li>空槽时 setEnabledIf=false → ItemSlot.onUpdate() 将 slot.func_111238_b() 设为 false → drawSlot 跳过渲染</li>
     * <li>这确保空槽不渲染，新物品到达时仅渲染动画中的帧（从顶部下落），而非先在底部闪一帧</li>
     * <li>v1.7.13 移除 setEnabledIf 导致空槽始终渲染，物品到达时先在底部 (x,fallDistance) 闪现一帧，
     * 再跳到顶部 (x,-1) 开始下落——用户感知为"没有下落动画，只有最终渲染"</li>
     * </ul>
     * <p>
     * v1.7.14 移除 client 检查（保留）：与 v1.6.34 一致。服务端触发 animator 无害（AnimatorManager 仅客户端 tick），
     * 但确保客户端 changeListener 不会被意外跳过。
     * <p>
     * v1.7.15 新增诊断日志：
     * <ul>
     * <li>[NekoFall]：changeListener 触发时输出（保留 v1.7.14 日志，验证客户端是否触发）</li>
     * <li>[NekoEnabled]：setEnabledIf 评估时输出（限频 1000ms，确认 enabled 状态与线程）</li>
     * </ul>
     *
     * @param index    槽位索引
     * @param animator 掉落动画控制器
     * @return 配置好的物品槽组件
     */
    private IWidget createItemSlot(int index, Animator animator) {
        return new NekoItemSlotWithDepth(index).slot(
            new ModularSlot(this.outputItems, index).accessibility(false, true)
                .slotGroup("outputSlotGroup")
                .changeListener((newItem, onlyAmountChanged, client, init) -> {
                    // v1.7.15 诊断日志：保留 [NekoFall] 日志用于排查客户端 changeListener 从未触发的问题
                    System.out.println(
                        "[NekoFall] slot=" + index
                            + " newItem="
                            + (newItem != null ? newItem.getDisplayName() : "null")
                            + " onlyAmount="
                            + onlyAmountChanged
                            + " client="
                            + client
                            + " init="
                            + init);
                    // v1.7.15：回归 VM 原版简洁逻辑——仅在非 init、有新物品、非仅数量变化时触发动画
                    // 初始位置已是终点 (x, fallDistance)，init 同步时无需额外处理
                    if (!init && newItem != null && !onlyAmountChanged) {
                        animator.reset();
                        animator.animate();
                        System.out.println("[NekoFall] slot=" + index + " → play fall animation");
                    }
                }))
            .background(IDrawable.EMPTY)
            .disableHoverBackground()
            .setEnabledIf(slot -> {
                boolean hasStack = slot.getSlot()
                    .getHasStack();
                // v1.7.15 诊断日志：限频 1000ms，确认 enabled 状态与线程（所有 slot 共享限频）
                long now = System.currentTimeMillis();
                if (now - lastEnabledLogTime > 1000) {
                    lastEnabledLogTime = now;
                    System.out.println(
                        "[NekoEnabled] slot=" + index
                            + " hasStack="
                            + hasStack
                            + " thread="
                            + Thread.currentThread()
                                .getName());
                }
                return hasStack;
            });
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
     * v1.7.15：回归 VM 原版——终点使用 animatedPos.copyOrImmutable()（即 animatedPos 的初始值快照）。
     * v1.7.14 用 new Pos(animatedPos.getX(), fallDistance) 作为终点，与 VM 原版不一致；
     * v1.7.15 改为 copyOrImmutable()，与 VM 原版（VendingMachine-0.4.95）完全对齐。
     * <p>
     * v1.7.15：animatedPos 的初始值改为 (x, fallDistance)（见 getFallingItemSlot），
     * 因此 copyOrImmutable() 返回的终点即为 (x, fallDistance)，与 v1.7.14 显式构造的终点等价，
     * 但语义上更贴近 VM 原版（终点 = 初始值快照）。
     *
     * @param animatedPos  动画目标位置（同时作为动画的可变状态对象，v1.7.15 起初始 y 为 fallDistance）
     * @param fallDistance 掉落终点 Y 坐标（最终落点位置）
     * @return 配置好的动画控制器
     */
    private static Animator createFallingAnimation(Pos animatedPos, int fallDistance) {
        // 起点在槽位上方 (y=-1)，终点为 animatedPos 的初始值 (x, fallDistance)——与 VM 原版一致
        return new MutableObjectAnimator<>(animatedPos, new Pos(animatedPos.getX(), -1), animatedPos.copyOrImmutable())
            .bounds(0, 1)
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
