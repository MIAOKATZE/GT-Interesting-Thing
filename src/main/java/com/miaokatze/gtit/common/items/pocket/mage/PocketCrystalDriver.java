package com.miaokatze.gtit.common.items.pocket.mage;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.PocketConstants;

/**
 * ★结晶模式「源质直接出晶」的<b>交付算法</b>（蒸馏腿分叉的出晶半段；类名与文件保留，
 * 独立 tick 宿主已随分叉退役，不再有"每秒抽干全盘"那条腿）。
 *
 * <h2>干什么</h2>
 * 魔法使的「结晶模式」开通后，<b>本轮蒸馏的产出不进源质盘</b>：由
 * {@code PocketDistillDriver#settleBatch} 把候选点数交给本类，逐 tag mint 成源质结晶交到玩家背包；
 * 背包放不下的点数由调用侧回退源质盘，盘也满才经蒸馏腿的 spill 支掉脚下兜底。
 * <b>源质盘已有存量原地不动</b>——本类不持有、不读、不写 {@code ess}，没有独立节拍，
 * 一切时机由蒸馏轮次决定。★缺省<b>关</b>（用户裁定），与「猫猫币充能」「源质转换」共用主开关
 * {@code PocketUpgradeType.MAGE}，主开关与子模式位的合取点在
 * {@code PocketDistillDriver#crystallizeRedirectOn}（与节拍读点同宿主，不另设第二处）。
 *
 * <h2>★三条口径纪律（沿用旧 tick 宿主定下的判据，只换触发方）</h2>
 * <ol>
 * <li><b>按实付量记账、不掉地</b>：交付走 {@link CrystalSink}（生产实现
 * {@link com.miaokatze.gtit.common.items.pocket.PocketItemExit#giveIntoInventory} = 只进背包、
 * 余量留栈）。mint 出的 {@code stackSize} 是真的成了的枚数，{@code deliver} 返回的是真的进包的
 * 枚数，两者之差折回点数进回退表，由调用方回退源质盘——本层不就地丢弃、不掉地，
 * 掉地兜底的裁决不在交付层；</li>
 * <li><b>不就地排空</b>：晶自身的 {@code AspectList} 从头到尾没被改过一次（wiki
 * {@code gtit-taumcraft-essentia-carriers.md} §3 证死：就地排空会让服务端随机重赋型），
 * 产晶一律是"mint 新晶"；</li>
 * <li><b>mint-null 零消耗</b>：出晶返 {@code null}（TC 缺席 / 桥未装配 / tag 不认识）⇒ 该段点数
 * 整体计入回退表，宁可不交付也不凭空记账。</li>
 * </ol>
 *
 * <h2>记账量为什么取"实付枚数"而不是"请求枚数"</h2>
 * 桥侧 {@code newCrystalStack} 自带 {@code min(points, 64)} 的钳制，返回的 {@code stackSize} 才是
 * 真的成了的枚数；{@code deliver} 再报告其中真的进包的那部分。按请求量记账就会出现
 * "请求 70、到手 64、回退 0"那 6 点的空洞——与 S9a 法杖那条「按对方实收量掏自己」是同一条纪律的镜像。
 */
public final class PocketCrystalDriver {

    private PocketCrystalDriver() {}

    /**
     * 把一份 {@code tag → 点数} mint 成晶并交付（<b>不碰源质盘</b>）。
     * <p>
     * 遍历序 = 入参的入账序（调用方传 {@code LinkedHashMap} ⇒ 同输入必同结论）；同一 tag 超过
     * {@link PocketConstants#MAGE_CRYSTAL_MAX_PER_BATCH} 枚按堆循环交付；★不做"只处理 primal"的
     * 过滤——有什么就出什么晶（TC 的晶本来就允许非元始 aspect，"6 条白名单"那套判据管的是
     * <b>元素容量表</b>有几行，与本出口无关，别把两条判据混成一条）。
     *
     * @return 回退表（{@code tag → 未交付点数}）：背包放不下的实付差额 + mint 失败的整段，
     *         由调用方回退源质盘、盘也满再经 spill 掉脚下
     */
    public static Map<String, Integer> deliverAsCrystals(Map<String, Integer> points, CrystalGate gate,
        CrystalSink sink) {
        final Map<String, Integer> refund = new LinkedHashMap<>();
        if (points == null || points.isEmpty() || gate == null || sink == null) {
            return refund;
        }
        for (Map.Entry<String, Integer> entry : points.entrySet()) {
            final Integer wanted = entry.getValue();
            if (wanted == null || wanted.intValue() <= 0) {
                continue;
            }
            final String tag = entry.getKey();
            int remaining = wanted.intValue();
            while (remaining > 0) {
                final int size = Math.min(remaining, PocketConstants.MAGE_CRYSTAL_MAX_PER_BATCH);
                final ItemStack crystals = gate.mint(tag, size);
                if (crystals == null || crystals.stackSize <= 0) {
                    // 防御分支（生产不可达：请求量已钳在每堆上界内，桥侧对合法请求要么整出要么缺席）：
                    // 出不了晶就整段回退，宁可不交付也不凭空记账
                    refund.merge(tag, remaining, Integer::sum);
                    break;
                }
                final int got = crystals.stackSize;
                final int paid = sink.deliver(crystals);
                remaining -= got;
                if (got - paid > 0) {
                    refund.merge(tag, got - paid, Integer::sum);
                }
            }
        }
        return refund;
    }
}
