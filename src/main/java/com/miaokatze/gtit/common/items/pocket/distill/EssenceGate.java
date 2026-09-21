package com.miaokatze.gtit.common.items.pocket.distill;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;

/**
 * 蒸馏/注入两条路共用的<b>源质探针</b>抽象（把"这栈是什么、里面有什么、抽出来多少"三个触点收在一处）。
 * <p>
 * <b>为什么要有这层</b>：判据本体（{@code PocketSlots#classifyIncoming} 分流与 {@code PocketSlots#injectContainer}
 * 注入执行、{@code PocketDistillDriver#planDistillBatch} 蒸馏装箱）必须是纯逻辑才能在零依赖回归套件里
 * 端到端验证；而 {@link TaumCompat} 的三条读数在 Thaumcraft 缺席时<b>恒</b>返回
 * 「非容器 / 空 / 抽不出东西」，用它跑测试只会得到一片假绿（本轮已为此踩过 R59b 偏离①同形坑）。
 * 因此判据一律收一个 {@code EssenceGate} 入参：生产传 {@link #TAUM}，测试传记录调用序列的桩件。
 * <p>
 * ⚠ 本接口<b>不含分流判定</b>：分流与注入执行的唯一实现仍在 {@code gui/pocket/PocketSlots}
 * （R69 对 D4 的裁决——{@code distill/} 内禁止再造平行分流器，只做消费方）。
 * <p>
 * 容器识别一律走 {@code IEssentiaContainerItem} 接口探测（R31/R44a：本环境无 TT {@code ItemVessel}），
 * <b>禁止</b> {@code instanceof ItemEssence} / {@code instanceof ItemVessel}（R44a）。
 */
public interface EssenceGate {

    /** 该栈是否源质容器（TC 缺席时恒 false ⇒ 一切按普通物品走蒸馏判定，与降级承诺一致）。 */
    boolean isContainer(ItemStack stack);

    /** 读容器内容（非容器/空容器返回 {@link TaumAspectAmounts#EMPTY}）。 */
    TaumAspectAmounts readContainer(ItemStack stack);

    /** 抽干容器（返回实际抽出的内容；容器随之变空。晶化源质按"读出 + 消耗整件"处理）。 */
    TaumAspectAmounts drainContainer(ItemStack stack);

    /**
     * 该栈的<b>蒸馏</b>产出（判据照 {@code TileAlchemyFurnace.canSmelt()}：
     * {@code getObjectTags + getBonusTags}，空 ⇒ 不可蒸 ⇒ 不推进、不消耗）。
     * <p>
     * ★R44c（按 R63b 改述：<b>容器不得进入蒸馏判定路径</b>）：实现方<b>不得</b>拿本方法去问容器——
     * 分流器会在调用之前就把它判给注入支。这条纪律由
     * {@code NekoPocketModelTest#distill_path_never_sees_container} 用桩件锁死。
     */
    TaumAspectAmounts aspectsOf(ItemStack stack);

    /** 生产实现：三条读数逐字转 {@link TaumCompat}（TC 缺席时天然全降级）。 */
    EssenceGate TAUM = new EssenceGate() {

        @Override
        public boolean isContainer(ItemStack stack) {
            return TaumCompat.isContainer(stack);
        }

        @Override
        public TaumAspectAmounts readContainer(ItemStack stack) {
            return TaumCompat.readContainer(stack);
        }

        @Override
        public TaumAspectAmounts drainContainer(ItemStack stack) {
            return TaumCompat.drainAll(stack);
        }

        @Override
        public TaumAspectAmounts aspectsOf(ItemStack stack) {
            return TaumCompat.distill(stack);
        }
    };
}
