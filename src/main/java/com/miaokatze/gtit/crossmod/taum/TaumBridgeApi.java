package com.miaokatze.gtit.crossmod.taum;

import net.minecraft.item.ItemStack;

/**
 * Thaumcraft / Thaumic Tinkerer 桥接面的形状定义。
 * <p>
 * 本接口<b>不出现任何 TC/TT 类型</b>（只用 MC 的 {@link ItemStack} 与本包值对象），
 * 因此 {@link TaumCompat} 可以安全持有它并做强制转换，即使 Thaumcraft 缺席
 * ——接口本身的加载不会牵动 {@link TaumBridge}。
 * <p>
 * 唯一实现 {@link TaumBridge} 由 {@link TaumCompat} 在双哨兵（{@code Loader.isModLoaded}
 * + {@code Class.forName} 探测）都通过后才用 {@code Class.forName(...).newInstance()} 反射装配，
 * TC 不在场时该实现类<b>永不被加载</b>，不会触发 {@code NoClassDefFoundError}。
 * <p>
 * 契约：所有方法都<b>不得抛出</b>给上层（实现内以 try/catch(Throwable) 兜底并降级为
 * 「不可用」空结果），且都允许 {@code null} 入参。
 */
public interface TaumBridgeApi {

    /**
     * @return 运行时派生的 aspect 注册序 tag 快照（{@code Aspect.aspects.keySet()} 迭代序）；
     *         数量不假设恰为 48（addon 可追加）
     */
    String[] aspectOrder();

    /**
     * @param tag aspect tag
     * @return RGB 染色（{@code Aspect.getColor()}），未知或不可用时 -1
     */
    int colorOf(String tag);

    /**
     * @param tag aspect tag
     * @return 显示名（{@code Aspect.getName()}），未知时回落为 tag 本身
     */
    String nameOf(String tag);

    /**
     * 蒸馏判据与产出集合（照 TC4 {@code TileAlchemyFurnace.canSmelt()} 的查表口径）。
     *
     * @param stack 待蒸馏物品
     * @return aspect → 点数（原量）；空结果即「不可蒸馏 / TC 缺席 / 入参为 null」
     */
    TaumAspectAmounts distill(ItemStack stack);

    /**
     * 读容器内源质（晶化源质、源质瓶，以及任何实现 {@code IEssentiaContainerItem} 的第三方容器）。
     *
     * @param stack 容器物品
     * @return 内容快照；空结果表示「非容器 / 容器是空的 / 不可用」
     */
    TaumAspectAmounts readContainer(ItemStack stack);

    /**
     * 单容器容量档位。
     *
     * @param stack 容器物品
     * @return 晶化源质 {@value TaumDistillRules#CRYSTAL_CAPACITY}、源质瓶
     *         {@value TaumDistillRules#PHIAL_CAPACITY}；识别为容器但容量无证据时
     *         {@value TaumDistillRules#CAPACITY_UNKNOWN}；非容器
     *         {@value TaumDistillRules#CAPACITY_NOT_A_CONTAINER}
     */
    int capacityOf(ItemStack stack);

    /**
     * 往容器里注入源质（合并语义：容器已有<b>不同</b> aspect 时整笔拒绝，不做混合、不清空）。
     * <p>
     * 晶化源质不走本方法（1 点/个由 {@link #newCrystalStack} 产出新晶），返回 0。
     * <p>
     * <b>单容器语义</b>：本方法按「一个容器」写入，不拆堆也不改动 {@code stackSize}；
     * {@code stackSize > 1} 时改动的是整堆共享的 NBT，调用方须自行先拆成 1 个。
     *
     * @param stack  容器物品
     * @param tag    aspect tag
     * @param points 期望注入点数
     * @return 实际注入点数（0 表示未改动容器）
     */
    int addEssentia(ItemStack stack, String tag, int points);

    /**
     * 取空容器（读出全部源质并把容器清成空态；瓶会退回 meta 0 的空瓶态）。
     *
     * @param stack 容器物品
     * @return 被取出的内容快照；空结果表示没取出任何东西（容器未改动）
     */
    TaumAspectAmounts drainAll(ItemStack stack);

    /**
     * 产出晶化源质（1 点 = 1 个晶，可堆到 64）。
     *
     * @param tag    aspect tag
     * @param points 点数，实际产出 {@code min(points, 64)} 个
     * @return 新物品栈；points &lt;= 0、tag 未知或不可用时 null
     */
    ItemStack newCrystalStack(String tag, int points);

    /**
     * 产出一个装好源质的容器：优先第三方罐（Thaumic Tinkerer 一类，运行时探测），
     * 缺席时回落 TC 源质瓶（{@code ItemEssence}，一次 8 点）。
     *
     * @param tag    aspect tag
     * @param points 期望装点数；瓶按 8 点封顶（不足 8 按实际点数装）
     * @return 装好的容器栈（stackSize 1）；不可用时 null
     */
    ItemStack newFilledContainer(String tag, int points);

    /**
     * @return {@link #newFilledContainer} 使用的单容器容量（无容量证据的罐返回
     *         {@value TaumDistillRules#CAPACITY_UNKNOWN}，此时按调用方给的点数装）
     */
    int filledContainerCapacity();
}
