package com.miaokatze.gtit.common.items.pocket.distill;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;

/**
 * 蒸馏/注入两条路共用的<b>源质探针</b>抽象（把"这栈是什么、里面有什么、抽出来多少"三个触点收在一处）。
 * <p>
 * <b>为什么要有这层</b>：判据本体（{@code PocketIntakeOps#classifyIncoming} 分流与 {@code PocketIntakeOps#injectContainer}
 * 注入执行、{@code PocketDistillDriver#planDistillBatch} 蒸馏装箱）必须是纯逻辑才能在零依赖回归套件里
 * 端到端验证；而 {@link TaumCompat} 的<b>五条</b>读数在 Thaumcraft 缺席时<b>恒</b>返回
 * 「非容器 / 空 / 抽不出东西 / {@code CAPACITY_NOT_A_CONTAINER} / 解不出伪物品 tag」，
 * 用它跑测试只会得到一片假绿（本轮已为此踩过 R59b 偏离①同形坑）。
 * 因此判据一律收一个 {@code EssenceGate} 入参：生产传 {@link #TAUM}，测试传记录调用序列的桩件。
 * <p>
 * ⚠ 本接口<b>不含分流判定</b>：分流与注入执行的唯一实现住 {@code common/items/pocket/PocketIntakeOps}
 * （★S7/T1 自 {@code gui/pocket/PocketSlots} 下沉；R69 对 D4 的裁决不变——{@code distill/} 内禁止再造平行分流器，只做消费方）。
 * <p>
 * 容器识别一律走 {@code IEssentiaContainerItem} 接口探测（R31/R44a：本环境无 TT {@code ItemVessel}），
 * <b>禁止</b> {@code instanceof ItemEssence} / {@code instanceof ItemVessel}（R44a）。
 */
public interface EssenceGate {

    /** 该栈是否源质容器（TC 缺席时恒 false ⇒ 一切按普通物品走蒸馏判定，与降级承诺一致）。 */
    boolean isContainer(ItemStack stack);

    /** 读容器内容（非容器/空容器返回 {@link TaumAspectAmounts#EMPTY}）。 */
    TaumAspectAmounts readContainer(ItemStack stack);

    /**
     * 抽干容器（返回实际抽出的内容；容器随之变空）。
     * <p>
     * ⚠ <b>晶化源质不走本方法</b>：{@code TaumBridge#drainAll} 对晶<b>恒返 EMPTY</b>——清空内容但
     * 物品还在场，TC 的服务端 {@code onItemUpdate} 会给它随机重赋型（{@code ItemCrystalEssence.java:98-110}），
     * 那是销毁价值之外的第二条危害。晶的"读出 + 消耗整叠"由注入执行层落实
     * （{@code PocketIntakeOps#injectContainer} 的 {@link #capacityOf(ItemStack)} 分流 → {@code injectCrystals}），
     * ★R86 起这句从空头承诺变成已实现。
     */
    TaumAspectAmounts drainContainer(ItemStack stack);

    /**
     * ★R86（缺陷 2）：单容器容量档位——注入执行层用它<b>只</b>判"是不是晶化源质"
     * （等于 {@code TaumDistillRules.CRYSTAL_CAPACITY} ⇒ 整叠消耗、不退回空壳）。
     * <p>
     * 为什么把这第四枚触点放进本接口而不是在分流/注入层里 {@code instanceof}：
     * 晶/瓶/第三方罐的区分证据全在 TC 侧，测试桩必须能注入同一判据（同 R59b 的假绿教训）。
     *
     * @return 晶 {@code CRYSTAL_CAPACITY} / 瓶 {@code PHIAL_CAPACITY} / 其他容器
     *         {@code CAPACITY_UNKNOWN} / 非容器或 TC 缺席 {@code CAPACITY_NOT_A_CONTAINER}
     */
    int capacityOf(ItemStack stack);

    /**
     * 该栈的<b>蒸馏</b>产出（判据照 {@code TileAlchemyFurnace.canSmelt()}：
     * {@code getObjectTags + getBonusTags}，空 ⇒ 不可蒸 ⇒ 不推进、不消耗）。
     * <p>
     * ★R44c（按 R63b 改述：<b>容器不得进入蒸馏判定路径</b>）：实现方<b>不得</b>拿本方法去问容器——
     * 分流器会在调用之前就把它判给注入支。这条纪律由
     * {@code NekoPocketModelTest#distill_path_never_sees_container} 用桩件锁死。
     */
    TaumAspectAmounts aspectsOf(ItemStack stack);

    /**
     * ★<b>R91-⑦（第 6 触点）：源质伪物品的声明 tag</b>——ARI 一类配方显示件既不是
     * {@code IEssentiaContainerItem}（{@link #readContainer} 恒空）、也不在 TC 蒸馏注册表里
     * （{@link #aspectsOf} 恒空），于是拖它声明绑定就<b>结构性双空</b>⇒ 拒收（用户报的
     * 「无法绑定标记」根-3）。本触点是这条缺口的<b>唯一</b>补口。
     * <p>
     * <b>只声明、绝不入库存计点</b>：本方法与 {@link #capacityOf(ItemStack)} 无关（伪物品不是容器 ⇒
     * 档位恒 {@code CAPACITY_NOT_A_CONTAINER}），因此 {@code PocketEssenceIntake#intake} 与 12 格注入支
     * 对它天然拒收，<b>不新增第二道计点门</b>。识别本体（注册名精确白名单 + TC 复核）只住
     * {@code crossmod/taum/} 一份，本触点只是转调。
     * <p>
     * ★<b>为什么同样要收进本接口</b>（{@code EssenceGate.java} 类注释 R59b 那条理由在此成立）：生产实现
     * 只在 TC 在场时被加载，判据若绕过本接口直调 {@link TaumCompat}，零依赖回归套件就只能拿到"恒空"
     * 的假绿——所以桩件必须能注入同一判据。
     *
     * @return 复核后的 tag；读不出（非白名单物品 / 无 NBT / TC 解不出 / TC 与 ARI 缺席）⇒ {@code null}
     */
    String pseudoAspectTag(ItemStack stack);

    /** 生产实现：五条读数逐字转 {@link TaumCompat}（TC 缺席时天然全降级）。 */
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
        public int capacityOf(ItemStack stack) {
            return TaumCompat.capacityOf(stack);
        }

        @Override
        public TaumAspectAmounts aspectsOf(ItemStack stack) {
            return TaumCompat.distill(stack);
        }

        @Override
        public String pseudoAspectTag(ItemStack stack) {
            return TaumCompat.pseudoAspectTag(stack);
        }
    };
}
