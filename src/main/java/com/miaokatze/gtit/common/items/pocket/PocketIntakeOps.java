package com.miaokatze.gtit.common.items.pocket;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;
import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;

/**
 * 口袋 12 格「蒸馏 / 注入」双用入口的<b>分流 + 注入原语</b>（★S7/T1 自 {@code gui/pocket/PocketSlots}
 * 下沉，行为零变化：判据、算术与出口逐字保留）。
 * <p>
 * 本类是 {@code common/items/pocket/} 侧的<b>可继承宿主</b>（非 final、protected 构造）：
 * {@code PocketSlots} 以 {@code extends} 挂靠本类，既有调用点与回归套件里的限定名
 * {@code PocketSlots.IncomingAction} / {@code PocketSlots.Intake} / {@code PocketSlots.IntakeResult}
 * / {@code PocketSlots.classifyIncoming(...)} 等经<b>成员类型与静态成员的继承</b>继续解析——
 * 那一份既有断言（{@code NekoPocketModelTest}）因此一个字都不用改。
 * <p>
 * R63b / R69-D4 的纪律随迁：<b>唯一分流器</b>（禁止在 {@code distill/} 与 GUI 两侧各写一套判据）与
 * 唯一注入执行都住本类；{@code distill/} 内只做消费方（{@code PocketDistillDriver}），
 * GUI 侧只做槽位外壳（{@code PocketSlots#injectContainerIfAny}）。
 */
public class PocketIntakeOps {

    protected PocketIntakeOps() {}

    /** 12 格的分流结论。 */
    public enum IncomingAction {
        /** 普通物品：留在格内等 S7 的 100 tick 蒸馏节拍。 */
        DISTILL,
        /** 有内容的源质容器：当场走注入支（不进蒸馏判定，R44c 改述）。 */
        INJECT,
        /** 空栈或空容器：不收。 */
        REJECT
    }

    /**
     * <b>唯一</b>分流函数（R63b：禁止在 {@code distill/} 与 GUI 两侧各写一套判据）。
     * 判据走 {@link EssenceGate#isContainer(ItemStack)} 的<b>接口探测</b>
     * （{@code IEssentiaContainerItem} + 单 aspect 容器，R31/R44a）⇒
     * <b>不得</b> {@code instanceof} 任何具体物品类（TC 的晶/瓶与第三方罐子自动同判）。
     * <p>
     * ★R88 换载体后本函数<b>一行判据都没改</b>，改的是它下游的计点：瓶（现役，
     * {@code capacityOf == PHIAL_CAPACITY} → 8 点/只）与旧晶（C2 只读，
     * {@code capacityOf == CRYSTAL_CAPACITY} → 1 点/枚）都落 {@link IncomingAction#INJECT}，
     * 空瓶／空罐照旧 {@link IncomingAction#REJECT}（不收空壳，也不许它进蒸馏判定）。
     * "这一叠值几点"由 {@link #injectContainer} 按<b>单件点数 × 只数</b>算，这里不参与算术。
     * <p>
     * 本重载是生产入口（{@link EssenceGate#TAUM} ⇒ 转 {@code TaumCompat}）；
     * 回归套件用 {@link #classifyIncoming(ItemStack, EssenceGate)} 传桩件，两者是<b>同一段代码</b>。
     */
    public static IncomingAction classifyIncoming(ItemStack stack) {
        return classifyIncoming(stack, EssenceGate.TAUM);
    }

    /** 分流判定的实现本体（见 {@link #classifyIncoming(ItemStack)}）。 */
    public static IncomingAction classifyIncoming(ItemStack stack, EssenceGate gate) {
        if (stack == null || gate == null) {
            return IncomingAction.REJECT;
        }
        if (!gate.isContainer(stack)) {
            return IncomingAction.DISTILL;
        }
        final TaumAspectAmounts content = gate.readContainer(stack);
        return content == null || content.isEmpty() ? IncomingAction.REJECT : IncomingAction.INJECT;
    }

    /** 一次注入的结论（三条互斥出口，对应三条玩家可读到的文案）。 */
    public enum Intake {
        /** 不是容器 / 容器为空：本轮无事发生（对应 {@code REJECT}，格内保持原样）。 */
        NOTHING,
        /**
         * 源质格放不下：★容器<b>分毫未动</b>（{@code still.inject_full}，R29 全有全无的失败面）。
         * ★R88 后这一态只在"连一只容器都塞不下"时出现（一叠里塞得下几只就吃几只，见
         * {@link #injectContainer} 的第 2 条修正）。
         */
        STORE_FULL,
        /**
         * 已抽干并入账（{@code still.injected}）；容器变空，由调用方按 R40a 非消耗退回。
         * ★R88：<b>瓶</b>（现役载体）走这一态，且允许只抽干一叠里的前几只 ⇒ 空壳与余量分别由
         * {@link IntakeResult#returnedCarrier} / {@link IntakeResult#remainder} 交代。
         */
        DRAINED,
        /**
         * ★R86（缺陷 2）：晶化源质<b>整叠销毁</b>并入账（同样报 {@code still.injected}）。
         * 与 {@link #DRAINED} 的唯一区别就是调用方<b>不得</b>退回——空壳晶留在场会被 TC 随机重赋型。
         * ★R88 C2：晶已是<b>只读不产</b>的旧载体，本态因此在现役路径上只由"玩家手里还留着旧晶"触发；
         * 新产出的搬运一律走瓶、落 {@link #DRAINED}。
         */
        CONSUMED
    }

    /** 注入结果：结论 + 实际入账点数 + （★R88 瓶支）本轮被抽干的那一份与留在格内的余量。 */
    public static final class IntakeResult {

        public final Intake kind;
        public final int points;
        /**
         * ★R88：<b>本轮实际被抽干、该退回玩家的那一份容器</b>（空壳仍有玻璃价值，R40a 的退回口径不变）。
         * <p>
         * 只在"一叠里只抽干了其中几只"时与调用方手上那一叠<b>不是同一个对象</b>（{@link #remainder}
         * 同时非 null）；其余情形恒等于原栈（旧行为逐字保留）。{@link Intake#CONSUMED} 时本字段无意义
         * （晶整叠销毁，不许退回）。
         */
        public final ItemStack returnedCarrier;
        /**
         * ★R88：<b>源质盘塞不下整叠、留在格内等下一轮的那一叠</b>（非 null ⇒ 调用方必须把它写回格子，
         * 且它仍带着没被抽走的源质）。旧形状里 12 格一次只处理"一份内容"，永远用不到这一支。
         */
        public final ItemStack remainder;

        IntakeResult(Intake kind, int points) {
            this(kind, points, null, null);
        }

        IntakeResult(Intake kind, int points, ItemStack returnedCarrier, ItemStack remainder) {
            this.kind = kind;
            this.points = points;
            this.returnedCarrier = returnedCarrier;
            this.remainder = remainder;
        }

        public boolean drained() {
            return kind == Intake.DRAINED;
        }

        /** ★R86：这一笔是"消耗整叠"，容器不退回。 */
        public boolean consumed() {
            return kind == Intake.CONSUMED;
        }
    }

    /**
     * 注入支本体：把容器内容抽进 {@code store}。
     * <p>
     * <b>全有全无</b>（R29）：{@code canAcceptAll(候选) → drainContainer → putAll(候选)} 三段，
     * 顺序不能换——<b>预检必须在抽取之前</b>，否则"装不下"就成了"抽出来却没地方放"= 销毁价值。
     * 装不下 ⇒ 一格都不动、<b>容器分毫未动</b>（对应 {@code still.inject_full}）。
     * ⚠ 禁止用逐 tag 截断的 {@code add()} 做消耗判定（R45c/FIX-6：{@code isFull()} 只服务 GUI 置灰）。
     * <p>
     * ★★<b>R88 换载体带来的两处实质修正</b>（都不是口味改动，是瓶成为现役载体后才暴露的形状）：
     * <ol>
     * <li><b>候选按"单件点数 × 叠数"算</b>：TC 的 {@code ItemEssence} 一叠最多 64 只、
     * <b>整叠共享同一份 {@code AspectList}</b>（NBT 挂在栈上，一只满瓶写的是 {@code add(tag, 8)}）。
     * 旧形状只读一份 NBT 就入账 ⇒ "64 只满瓶进账 8 点、退回 64 只空瓶"＝<b>静默吞 504 点</b>，
     * 而 {@code drainAll} 会把整叠的 NBT 一次抹掉，玩家连"退回来重灌"的机会都没有。
     * （晶那一支在 R86 就乘了叠数，见 {@link #injectCrystals}；这一支当时只服务不可堆叠的第三方罐，
     * 乘不乘都一样，所以那条乘法从没被要求过。）</li>
     * <li><b>叠内允许部分抽干</b>：一叠 64 只瓶 = 512 点 &gt; 单 tag 上限
     * {@code PocketConstants.ESSENCE_CAP_PER_TAG}（256）⇒ 若仍按"整叠全有全无"判，玩家<b>永远</b>
     * 塞不进这一叠（盘全空也只收 32 只），症状就是 R86 缺陷 2 的"放进去无事发生"换皮回来。
     * 于是本轮实际吃的只数由 {@code canAcceptAll} 逐只<b>向下收口</b>（最多收到 1 只；一只也收不下才是
     * {@link Intake#STORE_FULL}），抽干的那一份退回玩家、余量留在格内等下一拍 ——
     * <b>全有全无的作用单位仍是"一个容器"</b>（R84 定档），只是"这一格"从"一叠"改成了"一叠里的一只"。</li>
     * </ol>
     *
     * @param container 玩家放进 12 格里的栈（★就地被抽干／就地减叠；调用方按
     *                  {@link IntakeResult#returnedCarrier} 与 {@link IntakeResult#remainder} 收尾）
     */
    public static IntakeResult injectContainer(ItemStack container, PocketEssenceStore store, EssenceGate gate) {
        if (container == null || store == null || gate == null) {
            return new IntakeResult(Intake.NOTHING, 0);
        }
        if (classifyIncoming(container, gate) != IncomingAction.INJECT) {
            return new IntakeResult(Intake.NOTHING, 0);
        }
        // ★R86（实机缺陷 2）／★R88 C2：晶化源质走"读出 × 叠数 + 消耗整叠"那一条独立支，且<b>只读</b>
        // ——它不能复用下面的 drainContainer（{@code TaumBridge#drainAll} 对晶恒返 EMPTY：清空但物品还在场
        // = TC 随机重赋型的危险态，{@code ItemCrystalEssence.java:98-110}），所以晶的消耗必须由本层显式
        // 表达成"销毁整叠"。本仓不再<b>产出</b>晶，但存量旧晶仍从这里读回点数（不吃件）。
        if (gate.capacityOf(container) == TaumDistillRules.CRYSTAL_CAPACITY) {
            return injectCrystals(container, store, gate);
        }
        final Map<String, Integer> perCarrier = toMap(gate.readContainer(container));
        if (perCarrier.isEmpty()) {
            return new IntakeResult(Intake.NOTHING, 0);
        }
        final int carriers = Math.max(1, container.stackSize);
        // ★上面第 2 条的收口：从"这一叠全都要"起逐只往下退，直到盘塞得下；一次都不塞 ⇒ STORE_FULL。
        // 判据只用 store 自己的 canAcceptAll（单点执法），这里<b>不</b>复制一份"上限 256"的算术。
        int fit = carriers;
        while (fit > 0 && !store.canAcceptAll(scaledByStackSize(perCarrier, fit))) {
            fit--;
        }
        if (fit <= 0) {
            return new IntakeResult(Intake.STORE_FULL, 0);
        }
        final boolean partial = fit < carriers;
        final ItemStack drainedPart = partial ? container.splitStack(fit) : container;
        final TaumAspectAmounts drained = gate.drainContainer(drainedPart);
        if (drained == null || drained.isEmpty()) {
            if (partial) {
                // 只数已经分出去了却什么都没抽出来 ⇒ 原样并回，绝不留"少了两只但没入账"的中间态
                // （并回是安全的：splitStack 是<b>复制</b> NBT，原栈那一份一直没被动过）
                container.stackSize += drainedPart.stackSize;
            }
            return new IntakeResult(Intake.NOTHING, 0);
        }
        final int points = store.putAll(scaledByStackSize(perCarrier, fit));
        if (!partial) {
            return new IntakeResult(points > 0 ? Intake.DRAINED : Intake.NOTHING, points);
        }
        if (points <= 0) {
            // 与预检矛盾的分支（canAcceptAll 过了却一点没进）：能救的是<b>瓶子本体</b>，把只数并回去；
            // 已被抽干那一份的源质在此分支里确实保不住 —— 走到这里就是 PocketEssenceStore
            // "预检 + putAll 成对"这条契约被破坏的信号，必须让下一轮重跑而不是静默收下。
            container.stackSize += drainedPart.stackSize;
            return new IntakeResult(Intake.NOTHING, 0);
        }
        return new IntakeResult(Intake.DRAINED, points, drainedPart, container.stackSize > 0 ? container : null);
    }

    /**
     * ★R86（实机缺陷 2）／★R88 C2：<b>晶化源质</b>（旧载体，<b>只读不产</b>）→ 源质格的入账支，
     * 兑现 {@link EssenceGate#drainContainer} 那句"读出 + 消耗整叠"的旧契约（此前从未实现，
     * 玩家把晶放进 12 格只会看到"无事发生"）。
     * <p>
     * 三条纪律与瓶支同源：<b>全有全无</b>（预检在入账之前，装不下就一格不动）、<b>不截断消耗</b>
     * （禁止拿逐 tag 的 {@code add()} 做判定，R45c/FIX-6）、<b>消耗后不退回</b>（空壳晶归
     * {@link Intake#CONSUMED}，退回就等于把危险态交回 TC 的 {@code onItemUpdate}）。
     * <p>
     * 换算：晶的 {@code CRYSTAL_CAPACITY = 1} ⇒ 一枚晶一点源质，且<b>整叠共享同一份 aspect NBT</b>
     * ⇒ 点数 = 读到的单件 amount × {@code stackSize}（与瓶支共用 {@link #scaledByStackSize} 这一条算术，
     * 只是瓶的单件 amount 是 {@code TaumDistillRules.PHIAL_CAPACITY} = 8）。这条乘法是"64 枚进 64 点"
     * 与"64 枚进 1 点"的分界，故单独成函数并由 JVM 用例 {@code inject_crystal_consumes_whole_stack_into_store}
     * 钉住。★本支<b>不做</b>瓶支那套"逐只向下收口"：晶的整叠最多 64 点，永远塞得进 256 点的空盘，
     * 全有全无在这里不会变成死路（那是 R88 瓶支特有的问题）。
     */
    public static IntakeResult injectCrystals(ItemStack crystal, PocketEssenceStore store, EssenceGate gate) {
        final TaumAspectAmounts content = gate.readContainer(crystal);
        if (content == null || content.isEmpty()) {
            return new IntakeResult(Intake.NOTHING, 0);
        }
        final Map<String, Integer> candidates = scaledByStackSize(toMap(content), crystal.stackSize);
        if (!store.canAcceptAll(candidates)) {
            return new IntakeResult(Intake.STORE_FULL, 0);
        }
        final int points = store.putAll(candidates);
        return new IntakeResult(points > 0 ? Intake.CONSUMED : Intake.NOTHING, points);
    }

    /**
     * ★R86：把"单件内容"按叠放大（每 tag 点数 × {@code stackSize}；叠数非正按 1 计，不造负点数）。
     * <p>
     * ★R88：这条现在是<b>两条载体的共同算式</b>——晶支 1 点/枚 × 叠数、瓶支
     * {@code PHIAL_CAPACITY} = 8 点/只 × 只数（TC 把 {@code AspectList} 挂在栈上，一叠只有一份 NBT，
     * 所以"读一件"与"读一叠"必须靠这一步区分）。别再在两处各写一遍乘法。
     */
    public static Map<String, Integer> scaledByStackSize(Map<String, Integer> single, int stackSize) {
        final int copies = Math.max(1, stackSize);
        final Map<String, Integer> scaled = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : single.entrySet()) {
            scaled.put(entry.getKey(), entry.getValue() * copies);
        }
        return scaled;
    }

    private static Map<String, Integer> toMap(TaumAspectAmounts amounts) {
        final Map<String, Integer> map = new LinkedHashMap<>();
        if (amounts == null) {
            return map;
        }
        for (int index = 0; index < amounts.size(); index++) {
            final String tag = amounts.tagAt(index);
            final int amount = amounts.amountAt(index);
            if (tag != null && amount > 0) {
                map.merge(tag, amount, Integer::sum);
            }
        }
        return map;
    }
}
