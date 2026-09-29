package com.miaokatze.gtit.common.items.pocket.distill;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceStore;
import com.miaokatze.gtit.common.items.pocket.PocketIntakeOps;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;

/**
 * 源质蒸馏的 <b>tick 宿主本体</b>（需求 2 右半 / S7）。
 * <p>
 * 与通道同一 tick 源、同一 {@code slot}（R57c⑤）：全仓不存在第二个口袋 tick 宿主，
 * GUI 侧只做进度<b>显示</b>，绝不做推进（{@code NekoPocketEssenceColumn} 的进度条读
 * {@link #progressOf(UUID)}，推进在本类）。
 * <p>
 * <b>落地口径</b>（逐条对应计划 §7 S7 与 R63b 的改述；★第 4/5/6 条的<b>粒度</b>已由 R84 改写，见各条）：
 * <ol>
 * <li>节拍 = <b>5 秒/轮</b>基档（★R95 起有加速档，★R96 S8：该型插件改名「魔法使」并把提速定到
 * {@code PocketUpgradeType.MAGE} 固化时 1 秒/轮），且只引用单一权威
 * {@link TaumDistillRules#distillIntervalTicks(boolean)}
 * （{@link TaumDistillRules#DISTILL_INTERVAL_TICKS} 是其 {@code fast=false} 派生）。本类与 GUI 两侧
 * <b>都没有</b>第二个 {@code 100}/{@code 20}（计划 §17.2 第 4 条）；<b>中途安装</b>：当前轮按旧间隔
 * 跑完、下一轮生效（节拍只在装填点 {@link #distillIntervalOf} 读一次位图，不中途改拍）；</li>
 * <li>计时是 {@link Clock#ticksLeft} 这一<b>相对倒计时</b>（R62）：不读
 * {@code ticksExisted}/{@code getTotalWorldTime()}，因此跨维重建玩家不会让进度漂走；</li>
 * <li>判据照 TC {@code TileAlchemyFurnace.canSmelt()}：{@code getObjectTags + getBonusTags}
 * （由 {@link EssenceGate#aspectsOf} 承担）返回空 ⇒ <b>不推进、不消耗</b>（R28/C4）；</li>
 * <li>★<b>一轮的对象是「格」：每个非空且含源质的格各蒸 1 件、各消耗 1 件</b>（R84 用户裁定：
 * "对象应该是格子，每个格子蒸 1 件"）⇒ 同一物品放 3 格就是 3 格各减 1 件；枚举与装箱一律
 * <b>格号升序</b>，同输入必同结论（确定性可复现）。<b>★旧口径（R83 A2 / D-3 β）原文照录以备对照</b>：
 * "一轮的粒度 = 每组物品各 1 次、各消耗 1 件；同一 {@code item + damage} 散在几格都算一组，一轮只问一次
 * 产物、只从该组的一格扣一件"——该句<b>已被 R84 作废</b>，它正是"12 格装同一种物品 ⇒ 整轮只蒸 1 件"
 * 那个症状的成因（早退发生在"折成组"那一步）。作废的只是<b>把多格折成一个消耗单位</b>这一层；
 * "单件产出按 {@code AspectList} <b>原量</b>入账、<b>不乘 {@code stackSize}</b>"这条算术纪律<b>原样保留</b>
 * ——乘堆叠数会正面撞 {@link PocketConstants#ESSENCE_CAP_PER_TAG} 的单格上限与全有全无 ⇒ 整堆一次入账
 * <b>永久</b>判"放不下"、进度条钉死在满格，比逐格更糟（这是"为什么不整堆一次入"的算术理由，不是口味选择）；
 * TC 查询成本由"对象折组"压到"× 组数"改为由 <b>{@link Probe} 记忆化</b>压到"× 不同的
 * {@code item + damage} 数"（{@link #planDistillBatch(ItemStack[], EssenceGate, PocketEssenceStore)}
 * 第一段，压掉了哪几次见该方法注释；上界仍是 {@code DISTILL_INPUT_SLOTS}）；</li>
 * <li>入账<b>按格全有全无</b>（R29 的作用单位：R83 A2 收到"一组" → ★R84 收到"<b>一格</b>"）：一格蒸出的
 * 全部 aspect 必须<b>整体</b>放得下，放不下就<b>只放弃这一格</b>（该格物品一件都不消耗 ⇒ 不销毁价值），
 * 其它格照常入账。判据走 {@link PocketEssenceStore#canAcceptAll(Map, Map)}（第二参是本轮已许诺给
 * <b>前面那些格</b>的预留量）→ {@code putAll}；<b>禁止</b>用逐 tag 截断的 {@code add()} 或
 * {@code isFull()} 决定"要不要消耗物品"（R45c/FIX-6：{@code isFull()} 只服务 GUI 置灰）。
 * 整轮一格都没收下且是被空间挡下 ⇒ 进度<b>停在满格不重跑</b>，玩家取走晶化源质腾出空间即自动解卡；</li>
 * <li>★单件原量就超 {@link PocketConstants#ESSENCE_CAP_PER_TAG} 的格（TC {@code getBonusTags} 在
 * {@code capAspects(ret, …)} 之后继续累加 ⇒ 护甲/武器/工具类可越上限）是"再怎么腾格也放不下"，
 * <b>不再</b>把整轮永久冻住（R83 偏差 3c）：它被单独放弃并留下读数——{@link #discardedGroupsOf(UUID)} /
 * {@link #discardedPointsOf(UUID)} 与 {@link Status#OVER_CAP}。★R84 起这两个读数的单位是
 * <b>格</b>（旧句"放弃多少<b>组</b>、多少点"中的"组"已作废；字段名沿用 {@code discardedGroups} 以免撞
 * 回归套件的编译，改名的落点见 {@link Batch#discardedGroups} 注释），不再"放了东西、进度条满着、
 * 什么都不发生"；</li>
 * <li>★<b>容器绝不进入蒸馏判定路径</b>（R44c，按 R63b 改述；不是"不得进入这 12 格"）：
 * 每个候选格都先过唯一分流器 {@code PocketIntakeOps#classifyIncoming}，只有判为 {@code DISTILL}
 * 的栈才会被问 {@link EssenceGate#aspectsOf}；容器当场走注入支（{@code PocketIntakeOps#injectContainer}，
 * 由 S4 落地、本类<b>不</b>再造第二条平行分流，R69）。
 * 这样 {@code getBonusTags} 把栈内已有源质重复计入产出的回路从入口就断了。</li>
 * </ol>
 * <p>
 * <b>会话来源</b>（R53c）：12 格内容与源质表都在 {@link PocketSession} 里（面板持有的那一份内存真相）。
 * 关屏后会话继续存活并由 driver 落盘，因此"把物品留在格里去干别的"能真的蒸完；
 * 反过来 driver <b>绝不</b>每 tick 现解 NBT（那是 R53c 点名的成本形态）。
 * <p>
 * <b>与通道缓冲不共用</b>（R15）：蒸馏写口袋 {@code ess}；通道拉取的源质支物化成晶化源质进物品栏；
 * 需求 2 的「要素栏取出 → 晶化源质」是第三条独立路径（{@code NekoPocketPanel#performEssenceOut}）。
 */
public final class PocketDistillDriver {

    /** 蒸馏一轮的可观测状态（面板 tooltip 据此选 {@code still.*} 文案，不另造键）。 */
    public enum Status {
        /** 12 格里没有东西。 */
        IDLE,
        /** 有东西但都不含源质 ⇒ 进度不推进、物品不消耗（R28）。 */
        NO_ASPECT,
        /** 正常推进中。 */
        RUNNING,
        /** 有产物但源质格装不下 ⇒ 进度停在满格不重跑（R29 全有全无，取走晶即解卡）。 */
        STORE_FULL,
        /**
         * ★R83 A2（偏差 3c）＋★R84（单位改格）：本轮<b>没有一格</b>被收下，且原因是"某一格的单件原量
         * 本身超单格上限"——腾格子救不了它，所以它既不是 {@link #STORE_FULL}（那是可解卡的"需要空间"）
         * 也不是 {@link #IDLE}（12 格里确实躺着可蒸物）。放弃了哪几<b>格</b>、多少点由
         * {@link #discardedGroupsOf(UUID)} / {@link #discardedPointsOf(UUID)} 给读数。
         */
        OVER_CAP
    }

    /**
     * 每玩家的节拍状态（服务端内存；随会话生死，不做持久化——R24：短效不持久化，
     * 蒸馏进度同理，重启后从 0 重新数 100 tick，代价是玩家多看 5 秒空条，换来的是零跨存档状态）。
     */
    private static final class Clock {

        /** 距下一轮的相对 tick 倒计时（{@code 0} 表示"该跑了/没在跑"）。 */
        int ticksLeft;
        /** 当前 12 格是否至少有一<b>格</b>可蒸物（只在内容或源质总量变化时重算）。 */
        boolean distillable;
        /** 进度停在满格（本轮一格都没收下且是被空间挡下）。 */
        boolean stalledFull;
        /** ★本轮一格都没收下且全部因"单件原量超单格上限"被放弃（不倒计时，但状态与读数要报出来）。 */
        boolean stalledOverCap;
        /**
         * 最近一次评估被放弃的<b>格数</b>（放弃必须留读数，R83 偏差 3c；★R84 起单位是格，
         * 字段名沿用 {@code discardedGroups} 只为不撞回归套件的编译）。
         */
        int discardedGroups;
        /** 最近一次评估被放弃的点数合计。 */
        int discardedPoints;
        /** 上一次"评估过"的内容签名。 */
        long checkedSignature;
    }

    private static final Map<UUID, Clock> CLOCKS = new LinkedHashMap<>();

    /**
     * 蒸馏加速档的「双口径」tooltip 行 lang 键（消费点在
     * {@code ItemNekoDimensionPocket#appendDistillFastLine}；lang 行由 S2b 落）。
     * <p>
     * ★R96 S8：这一档的<b>型</b>已改名「魔法使」（{@code PocketUpgradeType.MAGE}），但<b>本键与本常量名
     * 不改</b>——键说的是"这一行讲什么规格"（蒸馏节拍），不是"哪一型插件"；行为层的类名/方法名/键名同此理由
     * （改名面只覆盖名字面：型名、注册名、lang 键族 {@code gtit.pocket.upgrade.*}、贴图基名）。
     * 于是这一族留下"名实分家"的一处不对称：{@code mage} 型 ⇒ 读 {@code distill_fast} 行为键，
     * 已登记进 README 代价条目。
     * <p>
     * 键字面量住在本类而不是物品类的理由：R88① 的 pocket 域聊天白名单门禁对白名单文件
     * （{@code ItemNekoDimensionPocket} 在列）做「文件内 {@code "gtit.pocket.*"} 字面量一律须
     * {@code world.} 前缀」的粗粒度键前缀检查——tooltip 键放那边会被误判成越权聊天键；本类不在
     * 白名单内，且该键本就是蒸馏域的规格读数，与节拍真值同域。
     */
    public static final String TOOLTIP_DISTILL_FAST_KEY = "gtit.pocket.tooltip.distill_fast";

    private PocketDistillDriver() {}

    /**
     * 由 {@code ItemNekoDimensionPocket.onUpdate} 的<b>服务端</b>分支调用（每 tick 一次）。
     * <p>
     * ★R95 门控放宽：宿主已不再要求主手持有——背包 36 格任意位都推进
     * （vanilla 证据 {@code InventoryPlayer.java:343-348}；R66b 时代"selected 即门控"的口径随之作废）；
     * 多枚口袋时靠下面的会话身份守卫只让"开界面那一枚"跑。
     */
    public static void onItemTick(ItemStack stack, World world, EntityPlayer player, int slot, boolean isHeld) {
        if (player == null || player.getGameProfile() == null) {
            return;
        }
        final UUID uuid = player.getGameProfile()
            .getId();
        final PocketSession session = PocketSessions.peek(uuid);
        if (session == null) {
            // 从没打开过界面 = 12 格里必然没有东西；顺手摘掉残留时钟
            CLOCKS.remove(uuid);
            return;
        }
        if (session.carrierStack() != stack) {
            // 与通道侧同一条守卫：一个玩家只有一个活会话，会话认的是开界面那一枚口袋（对象身份），
            // 同时持两枚时另一枚的 onUpdate 必须直接跳过，否则把 A 的内容蒸进 B 的 NBT
            return;
        }
        final long signature = signature(session);
        Clock clock = CLOCKS.get(uuid);
        if (clock == null) {
            clock = new Clock();
            CLOCKS.put(uuid, clock);
        }
        if (signature == 0L) {
            clock.ticksLeft = 0;
            clock.distillable = false;
            clock.stalledFull = false;
            clock.stalledOverCap = false;
            clock.discardedGroups = 0;
            clock.discardedPoints = 0;
            clock.checkedSignature = 0L;
            return;
        }
        if (signature != clock.checkedSignature) {
            clock.checkedSignature = signature;
            final Batch batch = planDistillBatch(session, EssenceGate.TAUM);
            applyAssessment(clock, batch);
            if (!batch.advanceable) {
                clock.ticksLeft = 0;
            }
        }
        if (clock.stalledFull || clock.stalledOverCap) {
            // 停在满格不重跑 / 本轮没有一格放得进：都不消耗、不入账、也不倒计时
            // （前者由源质格腾出空间解卡，后者由 12 格内容变化解卡）
            return;
        }
        if (!clock.distillable) {
            return;
        }
        if (clock.ticksLeft <= 0) {
            clock.ticksLeft = distillIntervalOf(session);
        } else {
            clock.ticksLeft--;
        }
        if (clock.ticksLeft > 0) {
            return;
        }
        runBatch(session, clock);
    }

    /** 到点了：重新评估一次（上一拍之后内容可能又变了），再决定消耗与入账。 */
    private static void runBatch(PocketSession session, Clock clock) {
        final Batch batch = planDistillBatch(session, EssenceGate.TAUM);
        applyAssessment(clock, batch);
        if (!batch.advanceable) {
            clock.ticksLeft = 0;
            return;
        }
        if (!batch.accepted) {
            // 本轮没有一格放得下 ⇒ 一格都不消耗、一分都不入账（消耗判据始终是"这格建效了"）
            clock.ticksLeft = 0;
            return;
        }
        session.essence()
            .putAll(batch.candidates);
        // ★sourceSlots 的长度就是"本轮被收下的格数"（每个被收下的格各扣 1 件，未收下的格一个都不扣）
        for (int index : batch.sourceSlots) {
            session.consumeOneDistillInput(index);
        }
        session.markDirty();
        // 界面已关时写权在 driver 手上（R57c①：关屏后"东西留在格里继续蒸"是正常用法）
        session.persistIdle();
        clock.ticksLeft = distillIntervalOf(session);
    }

    /**
     * 本轮蒸馏节拍（★R95 起有加速档）：读<b>载体栈</b>（取法照 {@link #onItemTick} 的会话口径
     * {@code session.carrierStack()}）的 {@code MAGE} 位（★R96 S8 型名，原名「蒸馏加速」），经唯一真值点
     * {@link TaumDistillRules#distillIntervalTicks(boolean)} 选 100/20。
     * <p>
     * <b>中途安装：当前轮旧间隔跑完，下一轮生效</b>——节拍只在装填点（这里）读一次位图，
     * 跑动中的倒计时不改拍；位图只读不建档（R53c 读路径纪律）。
     * <p>
     * ★R96 S1：读点换组合谓词 {@code PocketUpgradeSwitches.isActive}（位图 ∧ ¬off-mask）⇒ 玩家关掉加速
     * 之后同样"下一轮生效"（回落到基档 100），与"中途安装下一轮生效"是同一条不对称，不需要第二套计时。
     */
    private static int distillIntervalOf(PocketSession session) {
        final ItemStack carrier = session == null ? null : session.carrierStack();
        final boolean fast = PocketUpgradeSwitches.isActive(carrier, PocketUpgradeType.MAGE);
        return TaumDistillRules.distillIntervalTicks(fast);
    }

    /** 把一次评估的结论落到节拍状态上（判据位只有这一处写，避免签名支与到点支各说各话）。 */
    private static void applyAssessment(Clock clock, Batch batch) {
        clock.distillable = batch.advanceable;
        clock.stalledFull = batch.needsRoom;
        clock.stalledOverCap = batch.overCap && !batch.accepted;
        clock.discardedGroups = batch.discardedGroups;
        clock.discardedPoints = batch.discardedPoints;
    }

    /**
     * 一轮蒸馏的候选与判据（<b>纯函数</b>，回归套件用桩件 {@link EssenceGate} 直接驱动）。
     * <p>
     * 遍历序 = 格号升序（与 {@code DISTILL_MATRIX} 行主序一致）。★R84 的两条口径：
     * <ol>
     * <li><b>按格不按组</b>：工作单位是<b>格</b>，12 格里每个非空且含源质的格各是本轮的一份独立候选，
     * 各问一次产物（同物同 damage 的后续格走 {@link Probe} 记忆化，<b>不再</b>因此少蒸一件）、各留一个
     * 扣件落点。★旧口径（R83 A2）原文照录以备对照："<b>按组不按格</b>：组身份 = {@code item 引用 +
     * damage}，同一组散在几格都只算一组，每组的产物只问一次、只留<b>一个</b>扣件落点"——其中"只留一个
     * 扣件落点"已被 R84 作废（它就是"同物多格整轮只蒸 1 件"的成因），"只问一次产物"这一半由
     * {@link Probe} 以<b>纯查询记忆化</b>的形式保留；</li>
     * <li><b>按格全有全无</b>：一格的产物放得下就收这格（该格扣 1 件），放不下就整格放弃（该格一件都不扣 ⇒
     * 不销毁价值）；"放不下"再分两种——单件原量本身就超单格上限（腾格也没救 ⇒ {@link Status#OVER_CAP}
     * + 放弃读数）与"当前存量 + 本轮已许诺给前面格的量"挤不下（可解卡 ⇒ {@link Status#STORE_FULL}）。
     * 装箱按格号升序依次许诺 ⇒ 空间不够时<b>靠前的格</b>优先拿到空间，被挡下的格下一轮重试。</li>
     * </ol>
     * ★TC 查询成本（判据 4）：上界由"× 折出的组数"抬到"× 候选格数（≤ {@code DISTILL_INPUT_SLOTS}）"，
     * 再由 {@link Probe} 压回"× 不同的 {@code item + damage} 数"。具体压掉的次数：12 格全是同一种物品
     * 同一 damage 时，一次评估只问 {@code aspectsOf} <b>1 次</b>（压掉 11 次），但这 12 格<b>仍然</b>各蒸
     * 1 件；再叠上 {@link #signature} 的"内容没变不重算 plan"，稳态下的查询频率是
     * <b>每次内容变化 1 次 + 每轮 1 次</b>，与 R83 持平。
     * <p>
     * ★容器格在 {@code DISTILL} 判定处就被跳过，其 {@link EssenceGate#aspectsOf} <b>永不被调用</b>
     * （R44c/R63b 的硬口径，由 {@code distill_path_never_sees_container} 锁死）。
     */
    public static Batch planDistillBatch(PocketSession session, EssenceGate gate) {
        return planDistillBatch(
            session == null ? null : slotsOf(session),
            gate,
            session == null ? null : session.essence());
    }

    /** 数组形态（回归套件用；生产入口是上面的会话重载，两者同一段代码）。 */
    public static Batch planDistillBatch(ItemStack[] slots, EssenceGate gate, PocketEssenceStore store) {
        final int capacity = slots == null ? 0 : slots.length;
        // 第一段 + 第二段合并成一次格序升序遍历：候选就是格，装箱也按格号序许诺
        final Probe[] probes = new Probe[capacity];
        int probed = 0;
        // candidates 只装"被收下的格"的产物，所以它同时就是 runBatch 的入账内容
        final Map<String, Integer> candidates = new LinkedHashMap<>();
        final int[] chosen = new int[capacity];
        int used = 0;
        int cells = 0;
        int discardedSlots = 0;
        int discardedPoints = 0;
        boolean needsRoom = false;
        boolean overCap = false;
        if (slots != null && gate != null) {
            for (int index = 0; index < slots.length; index++) {
                final ItemStack stack = slots[index];
                if (stack == null || stack.stackSize <= 0) {
                    continue;
                }
                if (PocketIntakeOps.classifyIncoming(stack, gate) != PocketIntakeOps.IncomingAction.DISTILL) {
                    continue;
                }
                final Item item = stack.getItem();
                final int damage = stack.getItemDamage();
                // R84：命中记忆表只是"不再问一次 TC"，这一格照样是独立候选、照样扣 1 件
                Probe probe = item == null ? null : probeOf(probes, probed, item, damage);
                if (probe == null) {
                    final TaumAspectAmounts aspects = gate.aspectsOf(stack);
                    if (aspects == null || aspects.isEmpty()) {
                        continue;
                    }
                    final Map<String, Integer> single = toAmountMap(aspects);
                    if (single.isEmpty()) {
                        continue;
                    }
                    probe = new Probe(item, damage, single);
                    if (item != null && probed < capacity) {
                        probes[probed++] = probe;
                    }
                }
                cells++;
                final int wantPoints = totalOf(probe.want);
                if (store != null && store.exceedsCellCap(probe.want)) {
                    // 单件原量就超单格上限：这格永远放不下 ⇒ 放弃它（物品留在格里），但不冻结别的格
                    overCap = true;
                    discardedSlots++;
                    discardedPoints += wantPoints;
                    continue;
                }
                if (store == null || !store.canAcceptAll(probe.want, candidates)) {
                    needsRoom = true;
                    discardedSlots++;
                    discardedPoints += wantPoints;
                    continue;
                }
                mergeInto(candidates, probe.want);
                chosen[used++] = index;
            }
        }
        if (cells == 0) {
            return emptyBatch();
        }
        final boolean accepted = used > 0;
        // needsRoom 只在"整轮一格都没收下"时才成立：那才是 R29 的"停在满格不重跑"，
        // 有格建效时它必须是 false，否则倒计时被冻住、本轮已收下的格也不再推进
        return new Batch(
            candidates,
            Arrays.copyOf(chosen, used),
            used,
            true,
            accepted,
            !accepted && needsRoom,
            overCap,
            totalOf(candidates),
            discardedSlots,
            discardedPoints);
    }

    /** 本轮无事发生（没有一格含可蒸产物）：不推进、不消耗、零读数。 */
    private static Batch emptyBatch() {
        return new Batch(new LinkedHashMap<String, Integer>(), new int[0], 0, false, false, false, false, 0, 0, 0);
    }

    /**
     * 产物记忆表查表：命中返回该条，未命中 {@code null}（12 格上限，线性查比建 key 对象便宜）。
     * <p>
     * ★键的取舍照旧<b>不读 NBT</b>（与 {@link #signature} 同一理由：读 NBT 就是每 tick 序列化）；
     * {@code item} 用引用相等比较，与 R83 的组身份判据一致，故本层不会比旧实现更容易错配。
     */
    private static Probe probeOf(Probe[] probes, int size, Item item, int damage) {
        for (int at = 0; at < size; at++) {
            final Probe probe = probes[at];
            if (probe != null && probe.item == item && probe.damage == damage) {
                return probe;
            }
        }
        return null;
    }

    /**
     * 一份 {@code (item, damage)} 的<b>单件原量</b>产物（★R84：只为压 TC 查询而存在，
     * <b>不</b>代表工作单位；同一 {@code (item, damage)} 散在几格就有几份候选，各自扣 1 件）。
     * <p>
     * {@link #want} 对表内所有格<b>共享同一实例</b> ⇒ 一律只读：入账只走 {@link #mergeInto} 把它并进
     * {@code candidates}，装箱判据（{@code exceedsCellCap} / {@code canAcceptAll}）也都不改它。
     */
    private static final class Probe {

        final Item item;
        final int damage;
        final Map<String, Integer> want;

        Probe(Item item, int damage, Map<String, Integer> want) {
            this.item = item;
            this.damage = damage;
            this.want = want;
        }
    }

    /** 一份产物转 {@code tag → 点数}（同 tag 重复条目按求和收，非正数与空 tag 条目丢弃）。 */
    private static Map<String, Integer> toAmountMap(TaumAspectAmounts aspects) {
        final Map<String, Integer> out = new LinkedHashMap<>();
        for (int at = 0; at < aspects.size(); at++) {
            final String tag = aspects.tagAt(at);
            final int amount = aspects.amountAt(at);
            if (tag != null && !tag.isEmpty() && amount > 0) {
                out.merge(tag, amount, Integer::sum);
            }
        }
        return out;
    }

    private static void mergeInto(Map<String, Integer> target, Map<String, Integer> add) {
        for (Map.Entry<String, Integer> entry : add.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    private static int totalOf(Map<String, Integer> candidates) {
        int sum = 0;
        for (int value : candidates.values()) {
            sum += value;
        }
        return sum;
    }

    private static ItemStack[] slotsOf(PocketSession session) {
        final int slots = session.distillInputSlots();
        final ItemStack[] snapshot = new ItemStack[slots];
        for (int index = 0; index < slots; index++) {
            snapshot[index] = session.distillInputStack(index);
        }
        return snapshot;
    }

    /**
     * 内容签名：12 格（身份 + 数量）与源质总量的哈希。
     * <p>
     * 为什么需要它：可蒸性与"装不装得下"都只在内容变化时才可能改变，而 {@code onUpdate} 每 tick 都来。
     * 有了签名，{@code aspectsOf}（TC 侧可能递归 {@code generateTags}）就是<b>每次变化一次 + 每轮一次</b>，
     * 而不是每 tick 一次。源质总量也进签名，是为了让"玩家取出晶化源质腾出空间"能自动解掉
     * {@link Status#STORE_FULL} 的卡死。
     * <p>
     * ⚠ 只用 {@code itemId + damage + stackSize}：<b>不</b>读 NBT（读 NBT 就是每 tick 序列化，R53c）。
     * 代价是"同种物品但 NBT 不同且源质不同"的极端情形要等下一轮到点才刷新——可接受。
     * <p>
     * ★R84：本哈希<b>本来就是逐格</b>的（一格三项各 mix 一次，同物多格互不抵消），所以"对象由组改为格"
     * 不需要动签名机制；它顺带提供判据 4 要的那层压制——12 格内容一字不差时整轮 plan 不重算，
     * 一旦任一格数量变化则整个 plan 重算一次，而重算里同物同 damage 的格由 {@link Probe} 共享一次
     * {@code aspectsOf}（12 格同物 ⇒ 每次重算只问 1 次）。
     */
    private static long signature(PocketSession session) {
        long hash = 1469598103934665603L;
        final int slots = session.distillInputSlots();
        for (int index = 0; index < slots; index++) {
            final ItemStack stack = session.distillInputStack(index);
            if (stack == null) {
                continue;
            }
            final Item item = stack.getItem();
            hash = mix(hash, item == null ? -1 : Item.getIdFromItem(item));
            hash = mix(hash, stack.getItemDamage());
            hash = mix(hash, stack.stackSize);
        }
        return hash == 1469598103934665603L ? 0L
            : mix(
                hash,
                session.essence()
                    .totalPoints());
    }

    private static long mix(long hash, int value) {
        return (hash ^ value) * 1099511628211L;
    }

    /** 面板进度条读数（服务端；GUI 只显示不推进）。 */
    public static double progressOf(UUID player) {
        final Clock clock = player == null ? null : CLOCKS.get(player);
        if (clock == null) {
            return 0d;
        }
        if (clock.stalledFull) {
            return 1d;
        }
        if (!clock.distillable || clock.stalledOverCap) {
            // ★整轮都被"单件原量超上限"挡下时不报"满格"也不报"在跑"：条子停着 = 什么都不说，读 0 才是不说谎
            return 0d;
        }
        final int interval = distillIntervalOf(PocketSessions.peek(player));
        return (double) (interval - clock.ticksLeft) / (double) interval;
    }

    /** 面板 tooltip 的状态位（选 {@code still.*} 文案用）。 */
    public static Status statusOf(UUID player) {
        final Clock clock = player == null ? null : CLOCKS.get(player);
        if (clock == null) {
            return Status.IDLE;
        }
        if (clock.stalledFull) {
            return Status.STORE_FULL;
        }
        if (!clock.distillable) {
            return clock.checkedSignature == 0L ? Status.IDLE : Status.NO_ASPECT;
        }
        if (clock.stalledOverCap) {
            return Status.OVER_CAP;
        }
        return Status.RUNNING;
    }

    /**
     * 最近一次评估里<b>被放弃的格数</b>（R83 偏差 3c：放弃必须留下读数；★R84：单位由"组"改为"格"）。
     * <p>
     * 非 0 的含义是"这轮的这些格放不下，所以一件都没吃"，两种原因（单件原量超单格上限 / 空间被存量与
     * 前面格的许诺挤满）由 {@link #statusOf(UUID)} 与 {@link #progressOf(UUID)} 区分；要"哪一格、哪一 tag"
     * 得另开同步位，本轮只做格数与点数（面板侧见 {@code NekoPocketEssenceColumn#distillStateLine}）。
     * <p>
     * ⚠ 方法名里的 {@code Groups} 与读数单位（格）已经错位：改名要同时动回归套件
     * （{@code NekoPocketModelTest} 直读 {@code batch.discardedGroups}）⇒ 由主代理单独立项，本片不改名。
     */
    public static int discardedGroupsOf(UUID player) {
        final Clock clock = player == null ? null : CLOCKS.get(player);
        return clock == null ? 0 : clock.discardedGroups;
    }

    /** 最近一次评估里<b>被放弃的点数合计</b>（与 {@link #discardedGroupsOf(UUID)} 同一次评估）。 */
    public static int discardedPointsOf(UUID player) {
        final Clock clock = player == null ? null : CLOCKS.get(player);
        return clock == null ? 0 : clock.discardedPoints;
    }

    /** 距下一轮还剩几秒（{@code still.progress} 的 {@code %d}，与冷却的墙钟口径无关）。 */
    public static int secondsToNextBatch(UUID player) {
        final Clock clock = player == null ? null : CLOCKS.get(player);
        if (clock == null || !clock.distillable || clock.stalledFull || clock.stalledOverCap) {
            return 0;
        }
        return PocketConstants.ticksToSecondsCeil(clock.ticksLeft);
    }

    /** 12 格里是否还有东西（会话回收判据用）。 */
    public static boolean hasInputs(PocketSession session) {
        return session != null && signature(session) != 0L;
    }

    /** 会话被回收/玩家离线时摘掉时钟（面板重开即重新装填）。 */
    public static void forget(UUID player) {
        if (player != null) {
            CLOCKS.remove(player);
        }
    }

    public static int trackedPlayers() {
        return CLOCKS.size();
    }

    /** 停服/换档复位（R57c④）。 */
    public static void reset() {
        CLOCKS.clear();
    }

    /** 蒸馏一轮的结论（值对象；测试直接断言它的判据位）。 */
    public static final class Batch {

        /** 本轮<b>被收下的那些格</b>的候选：{@code tag → 单件原量点数}（同 tag 跨格已求和）；入账就用它。 */
        public final Map<String, Integer> candidates;
        /** 被收下的格各自的扣件落点，★长度 {@code == sourceCount}（一格一个落点，多出来的下标不存在）。 */
        public final int[] sourceSlots;
        /**
         * 本轮<b>被收下的格数</b>（★R84：对象是格 ⇒ 同物多格各算一格；旧口径"同物多格只算一组"已作废）。
         * <p>
         * ⚠ 它一直是"被收下的单位数"，R83 时等于组数、R84 起等于格数，与 {@link #sourceSlots} 的长度同值。
         */
        public final int sourceCount;
        /** 至少有一格含可蒸产物（false ⇒ 不推进、不消耗）。 */
        public final boolean advanceable;
        /** 至少有一格被收下（★唯一的消耗判据，R29 的按格口径）：{@code sourceSlots} 就是被收下的那些格。 */
        public final boolean accepted;
        /** 本轮一格都没收下且原因是"当前存量 + 本轮已许诺量"挤不下 ⇒ 进度停在满格不重跑（可解卡）。 */
        public final boolean needsRoom;
        /** ★R83 偏差 3c：至少有一格因"单件原量本身超单格上限"被放弃（腾格也没救，与 {@link #needsRoom} 分开）。 */
        public final boolean overCap;
        /** 本轮应入账的总点数（= {@link #candidates} 的合计，不含被放弃的格）。 */
        public final int points;
        /**
         * 本轮被放弃的<b>格数</b>（放弃留读数；★R84 单位由组改格）。
         * <p>
         * ⚠ 字段名 {@code discardedGroups} 与单位（格）<b>刻意错位</b>：本仓回归套件按名直读该字段
         * （{@code NekoPocketModelTest} 的 {@code distill_all_or_nothing_keeps_both} /
         * {@code distill_over_cap_group_leaves_reading}），改名属测试文件改动 ⇒ 交主代理落。
         */
        public final int discardedGroups;
        /** 本轮被放弃的点数合计（= 被放弃的那些<b>格</b>各自原量点数之和，与 {@link #discardedGroups} 同一批格）。 */
        public final int discardedPoints;

        Batch(Map<String, Integer> candidates, int[] sourceSlots, int sourceCount, boolean advanceable,
            boolean accepted, boolean needsRoom, boolean overCap, int points, int discardedGroups,
            int discardedPoints) {
            this.candidates = candidates;
            this.sourceSlots = sourceSlots;
            this.sourceCount = sourceCount;
            this.advanceable = advanceable;
            this.accepted = accepted;
            this.needsRoom = needsRoom;
            this.overCap = overCap;
            this.points = points;
            this.discardedGroups = discardedGroups;
            this.discardedPoints = discardedPoints;
        }
    }
}
