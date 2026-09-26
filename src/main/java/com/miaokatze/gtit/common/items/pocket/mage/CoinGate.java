package com.miaokatze.gtit.common.items.pocket.mage;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;

/**
 * 猫猫币身份的<b>探针</b>（"这一栈值几点容量"的唯一问句）。
 * <p>
 * <b>为什么要有这层</b>（形状与理由照 {@code distill/EssenceGate} 与 {@link WandVisGate}）：
 * 判据本体（{@link PocketCoinChargeDriver#tick} 的"先整笔预检、再扣币、再入账"三段式）必须能在零依赖
 * 回归套件里端到端跑；而生产实现读的 {@link NekoCurrencyRegistrar} 两条物品引用是
 * {@code CommonProxy.postInit} 才赋值的<b>可变静态字段</b> —— 在 preInit 之前、或在纯 JVM 套件里，
 * 它们恒为 {@code null} ⇒ 判据若绕过本接口直调注册表，测试拿到的永远是"什么都不是币"的假绿。
 * <p>
 * ★身份的<b>唯一</b>判据仍是 {@link NekoCurrencyRegistrar}（认 {@code NekoCoin} 与
 * {@code ShimmeringNekoCoin} 两件，走 {@code Item} 引用相等，不按 unlocalized 前缀猜）；
 * 本接口只把"两件分别值多少点"这一层换算收到一处，价值单源在
 * {@link PocketConstants#MAGE_COIN_VALUE_NORMAL} / {@link PocketConstants#MAGE_COIN_VALUE_SHIMMERING}。
 */
public interface CoinGate {

    /**
     * @param stack 候选栈（可以是任何东西）
     * @return {@code 0} = 不是可用的猫猫币；否则<b>一枚</b>折算的容量点数
     *         （普通 {@code +1}、闪烁 {@code +10}）。★这个量是"六条 primal <b>各</b>加这么多"，
     *         不是六条合计（需求原文"普通各元素 +1"里的"各"）。
     */
    int valueOf(ItemStack stack);

    /** 生产实现：转 {@link NekoCurrencyRegistrar}（未注册/不在场 ⇒ 恒 0，天然降级）。 */
    CoinGate NEKO = new CoinGate() {

        @Override
        public int valueOf(ItemStack stack) {
            final String currencyId = NekoCurrencyRegistrar.getNekoCurrencyId(stack);
            if (NekoCurrencyRegistrar.NEKO_ID.equals(currencyId)) {
                return PocketConstants.MAGE_COIN_VALUE_NORMAL;
            }
            if (NekoCurrencyRegistrar.SHIMMERING_NEKO_ID.equals(currencyId)) {
                return PocketConstants.MAGE_COIN_VALUE_SHIMMERING;
            }
            return 0;
        }
    };
}
