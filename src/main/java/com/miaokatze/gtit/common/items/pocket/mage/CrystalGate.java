package com.miaokatze.gtit.common.items.pocket.mage;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.crossmod.taum.TaumCompat;

/**
 * 结晶模式出晶的<b>探针</b>（"这一 tag、这些点数能不能出一批晶、出来的是哪枚栈"的唯一问句）。
 * <p>
 * <b>为什么要有这层</b>（形状与理由照 {@link WandVisGate} 与 {@code distill/EssenceGate}）：
 * 判据本体（{@link PocketCrystalDriver#crystallizeOnce} 的「读数 → 出整枚 → 交付 → 按实交付量消耗」
 * 四段式）必须能在零依赖回归套件里端到端跑；而生产实现经 {@link TaumCompat} 在 Thaumcraft 缺席时
 * <b>恒</b>返回 {@code null} —— 判据若绕过本接口直调门面，回归套件只会拿到"永远产不出"的一片假绿
 * （★与 S9a 报告 §3.4 那条"两条腿分开测"是同一条纪律）。
 * <p>
 * ★本接口与其生产实现都<b>不出现任何 {@code thaumcraft.*} 类型</b>，也不出现
 * {@code newCrystalStack(} 这个码位：TC 的类型面与"晶从哪来"那一问全部收在
 * {@code crossmod/taum}（类污染红线 {@code TaumCompat:15-27} + R88③ 那条"口袋目录不直接产晶"的既有锚，
 * 两条与新功能如何并存写在 {@code TaumCompat#newCrystalStack} 的 javadoc）。
 */
public interface CrystalGate {

    /**
     * 请求产出 {@code points} 点的该 tag 晶化源质（<b>整枚</b>语义：一晶 1 点，堆数由桥侧钳）。
     *
     * @param tag    aspect tag（★不限于 primal：盘里有什么就出什么，判据在桥侧的注册表）
     * @param points 本批期望枚数（&lt;=0 时实现不得产出任何东西）
     * @return 产出的栈（{@code stackSize} = <b>实际枚数</b>）；{@code null} = 什么都没产
     *         （TC 缺席 / 桥未装配 / tag 不认识 / 点数非法）。★调用方只能按返回栈的 {@code stackSize}
     *         折算消耗量；按请求量记账就是"晶没到手、源质先没了"。
     */
    ItemStack mint(String tag, int points);

    /** 生产实现：逐字转 {@link TaumCompat}（TC 缺席时天然降级为 {@code null}）。 */
    CrystalGate TAUM = new CrystalGate() {

        @Override
        public ItemStack mint(String tag, int points) {
            return TaumCompat.mintCrystals(tag, points);
        }
    };
}
