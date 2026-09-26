package com.miaokatze.gtit.common.items.pocket.mage;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.crossmod.taum.TaumBridgeApi;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;

/**
 * 法杖 vis 写入的<b>探针</b>抽象（把"这一栈能不能充、这一拍实际吃了多少点"两个触点收在一处）。
 * <p>
 * <b>为什么要有这层</b>（形状与理由照 {@code distill/EssenceGate}，原文口径见其类注释）：判据本体
 * （{@link PocketWandChargeDriver#chargeOnce} 的"按对方实收量掏自己"算式）必须是纯逻辑才能在零依赖
 * 回归套件里端到端验证；而生产实现经 {@link TaumCompat} 在 Thaumcraft 缺席时<b>恒</b>返回
 * {@link #NOT_CHARGEABLE} —— 判据若绕过本接口直调门面，回归套件只会拿到"永远不搬"的一片假绿。
 * 因此判据一律收一个 {@code WandVisGate} 入参：生产传 {@link #TAUM}，测试传记录调用序列的桩件。
 * <p>
 * ★本接口与其生产实现都<b>不出现任何 {@code thaumcraft.*} 类型</b>：单位换算（仓内 1 点 ↔ TC 的
 * ×100 刻度）与"是不是 {@code ItemWandCasting}"的判据全部收在桥内一处
 * （{@code TaumBridge#chargeWandVis}），常驻类只见 {@link TaumCompat}
 * （类污染红线 {@code TaumCompat:15-19}）。
 */
public interface WandVisGate {

    /**
     * 该栈不是"可被缓慢充能的法杖"（非该物品、tag 非元始、TC 缺席、桥接层未装配）。
     * ★派生自桥接契约，不留第二个 {@code -1}（单源纪律，同 {@code PocketConstants} 转发瓶容量的形状）。
     */
    int NOT_CHARGEABLE = TaumBridgeApi.WAND_NOT_CHARGEABLE;

    /**
     * 尝试把 {@code points} 点该 tag 的元素灌进法杖（<b>原子语义</b>：由 TC 自己按容量钳制）。
     *
     * @param wand   候选栈（可以是任何东西，判据在实现里）
     * @param tag    primal tag（实现侧仍会复核白名单 ∧ {@code Aspect#isPrimal()}）
     * @param points 本批期望灌入的点数（&lt;=0 时实现不得改动法杖）
     * @return {@link #NOT_CHARGEABLE} = 不认这一栈；{@code 0} = 认得但<b>一分没吃</b>（已满/放不下）；
     *         {@code >0} = <b>实际灌入</b>的点数（仓内单位）。调用方只能按这个返回值掏自己的容量，
     *         掏多于实收 = 玩家凭空损失（R96 S9a 验收 4 的同一条纪律）。
     */
    int chargeWandVis(ItemStack wand, String tag, int points);

    /** 生产实现：逐字转 {@link TaumCompat}（TC 缺席时天然降级为 {@link #NOT_CHARGEABLE}）。 */
    WandVisGate TAUM = new WandVisGate() {

        @Override
        public int chargeWandVis(ItemStack wand, String tag, int points) {
            return TaumCompat.chargeWandVis(wand, tag, points);
        }
    };
}
