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
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;
import com.miaokatze.gtit.gui.pocket.PocketSlots;

/**
 * 源质蒸馏的 <b>tick 宿主本体</b>（需求 2 右半 / S7）。
 * <p>
 * 与通道同一 tick 源、同一 {@code slot}（R57c⑤）：全仓不存在第二个口袋 tick 宿主，
 * GUI 侧只做进度<b>显示</b>，绝不做推进（{@code NekoPocketEssenceColumn} 的进度条读
 * {@link #progressOf(UUID)}，推进在本类）。
 * <p>
 * <b>落地口径</b>（逐条对应计划 §7 S7 与 R63b 的改述；★三条 R83 A2 的是本轮缺陷 3 (2)(3) 的新口径）：
 * <ol>
 * <li>节拍 = <b>5 秒/轮</b>，且只引用单一权威
 * {@link TaumDistillRules#DISTILL_INTERVAL_TICKS}（= 100）。本类与 GUI 两侧<b>都没有</b>第二个
 * {@code 100}（计划 §17.2 第 4 条）；</li>
 * <li>计时是 {@link Clock#ticksLeft} 这一<b>相对倒计时</b>（R62）：不读
 * {@code ticksExisted}/{@code getTotalWorldTime()}，因此跨维重建玩家不会让进度漂走；</li>
 * <li>判据照 TC {@code TileAlchemyFurnace.canSmelt()}：{@code getObjectTags + getBonusTags}
 * （由 {@link EssenceGate#aspectsOf} 承担）返回空 ⇒ <b>不推进、不消耗</b>（R28/C4）；</li>
 * <li>★<b>一轮的粒度 = 每组物品各 1 次、各消耗 1 件</b>（R83 A2 / D-3 β，对应玩家那句"蒸馏应该是对
 * 每组物品进行 1 次"）：同一 {@code item + damage} 散在几格都算<b>一组</b>，一轮只问一次产物、
 * 只从该组的一格扣一件。产出入账按 {@code AspectList} <b>原量</b>，<b>既不按格重复计、也不乘
 * {@code stackSize}</b>——乘堆叠数会正面撞 {@link PocketConstants#ESSENCE_CAP_PER_TAG}=64 的单格上限
 * 与全有全无 ⇒ 一件 ≥2 点的整堆（33…64 件 → ≥66 点）<b>永久</b>判"放不下"、进度条钉死在满格，
 * 比改之前更糟（这是"为什么不整堆一次入"的算术理由，不是口味选择）；</li>
 * <li>入账<b>按组全有全无</b>（R29 的作用单位由"整轮"收到"一组"，R83 A2）：一组蒸出的全部 aspect
 * 必须<b>整体</b>放得下，放不下就<b>只放弃这一组</b>（该组物品一件都不消耗 ⇒ 不销毁价值），其它组照常
 * 入账。判据走 {@link PocketEssenceStore#canAcceptAll(Map, Map)}（第二参是本轮已许诺给前面组的预留量）
 * → {@code putAll}；<b>禁止</b>用逐 tag 截断的 {@code add()} 或 {@code isFull()} 决定"要不要消耗物品"
 * （R45c/FIX-6：{@code isFull()} 只服务 GUI 置灰）。整轮一组都没收下且是被空间挡下 ⇒ 进度<b>停在满格
 * 不重跑</b>，玩家取走晶化源质腾出空间即自动解卡；</li>
 * <li>★单件原量就超 64 点的组（TC {@code getBonusTags} 在 {@code capAspects(ret,64)} 之后继续累加 ⇒
 * 护甲/武器/工具类可越上限）是"再怎么腾格也放不下"，<b>不再</b>把整轮永久冻住（R83 偏差 3c）：它被单独
 * 放弃并留下读数——{@link #discardedGroupsOf(UUID)} / {@link #discardedPointsOf(UUID)} 与
 * {@link Status#OVER_CAP}，放弃多少组、多少点都是可读的，不再"放了东西、进度条满着、什么都不发生"；</li>
 * <li>★<b>容器绝不进入蒸馏判定路径</b>（R44c，按 R63b 改述；不是"不得进入这 12 格"）：
 * 每个候选格都先过唯一分流器 {@code PocketSlots#classifyIncoming}，只有判为 {@code DISTILL}
 * 的栈才会被问 {@link EssenceGate#aspectsOf}；容器当场走注入支（{@code PocketSlots#injectContainer}，
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
         * ★R83 A2（偏差 3c）：本轮<b>没有一组</b>被收下，且原因是"某组的单件原量本身超单格上限"——
         * 腾格子救不了它，所以它既不是 {@link #STORE_FULL}（那是可解卡的"需要空间"）也不是
         * {@link #IDLE}（12 格里确实躺着可蒸物）。放弃了哪几组、多少点由
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
        /** 当前 12 格是否至少有一组可蒸物（只在内容或源质总量变化时重算）。 */
        boolean distillable;
        /** 进度停在满格（本轮一组都没收下且是被空间挡下）。 */
        boolean stalledFull;
        /** ★本轮一组都没收下且全部因"单件原量超单格上限"被放弃（不倒计时，但状态与读数要报出来）。 */
        boolean stalledOverCap;
        /** 最近一次评估被放弃的组数（放弃必须留读数，R83 偏差 3c）。 */
        int discardedGroups;
        /** 最近一次评估被放弃的点数合计。 */
        int discardedPoints;
        /** 上一次"评估过"的内容签名。 */
        long checkedSignature;
    }

    private static final Map<UUID, Clock> CLOCKS = new LinkedHashMap<>();

    private PocketDistillDriver() {}

    /**
     * 由 {@code ItemNekoDimensionPocket.onUpdate} 的<b>服务端且主手持有</b>分支调用（每 tick 一次）。
     * <p>
     * 主手门控是 R24 的裁定口径（"要求主手持有"），vanilla 侧证据见
     * {@code InventoryPlayer.java:347}（{@code selected = currentItem == i}）。
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
            // 停在满格不重跑 / 本轮没有一组放得进：都不消耗、不入账、也不倒计时
            // （前者由源质格腾出空间解卡，后者由 12 格内容变化解卡）
            return;
        }
        if (!clock.distillable) {
            return;
        }
        if (clock.ticksLeft <= 0) {
            clock.ticksLeft = TaumDistillRules.DISTILL_INTERVAL_TICKS;
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
            // 本轮没有一组放得下 ⇒ 一格都不消耗、一分都不入账（消耗判据始终是"这组建效了"）
            clock.ticksLeft = 0;
            return;
        }
        session.essence()
            .putAll(batch.candidates);
        // ★sourceSlots 的长度就是"本轮被收下的组数"（每个被收下的组各扣 1 件，未收下的组一个都不扣）
        for (int index : batch.sourceSlots) {
            session.consumeOneDistillInput(index);
        }
        session.markDirty();
        // 界面已关时写权在 driver 手上（R57c①：关屏后"东西留在格里继续蒸"是正常用法）
        session.persistIdle();
        clock.ticksLeft = TaumDistillRules.DISTILL_INTERVAL_TICKS;
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
     * 遍历序 = 格号升序（与 {@code DISTILL_MATRIX} 行主序一致）。★R83 A2 的两条口径：
     * <ol>
     * <li><b>按组不按格</b>：组身份 = {@code item 引用 + damage}（与 {@code NekoPocketPanel#sameSample}
     * 同判据；<b>不</b>读 NBT，理由与 {@link #signature} 的同类取舍一致），同一组散在几格都只算一组，
     * 每组的产物<b>只问一次</b> {@link EssenceGate#aspectsOf}、只留<b>一个</b>扣件落点；</li>
     * <li><b>按组全有全无</b>：一组放得下就收这组（该组各扣 1 件），放不下就整组放弃（该组一件都不扣 ⇒
     * 不销毁价值）；"放不下"再分两种——单件原量本身就超单格上限（腾格也没救 ⇒ {@link Status#OVER_CAP}
     * + 放弃读数）与"当前存量 + 本轮已许诺给前面组的量"挤不下（可解卡 ⇒ {@link Status#STORE_FULL}）。</li>
     * </ol>
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
        // 第一段：把 12 格折成"组"，每组只留第一次出现的格与它的产物（组内不重复问 TC）
        final Item[] groupItem = new Item[capacity];
        final int[] groupDamage = new int[capacity];
        final int[] groupSlot = new int[capacity];
        final TaumAspectAmounts[] groupAspects = new TaumAspectAmounts[capacity];
        int groups = 0;
        if (slots != null && gate != null) {
            for (int index = 0; index < slots.length; index++) {
                final ItemStack stack = slots[index];
                if (stack == null || stack.stackSize <= 0) {
                    continue;
                }
                if (PocketSlots.classifyIncoming(stack, gate) != PocketSlots.IncomingAction.DISTILL) {
                    continue;
                }
                final Item item = stack.getItem();
                final int damage = stack.getItemDamage();
                // β：同物多格 = 一组 ⇒ 后面那些格既不再问产物，也不再多扣一件
                if (item != null && indexOfGroup(groupItem, groupDamage, groups, item, damage) >= 0) {
                    continue;
                }
                final TaumAspectAmounts aspects = gate.aspectsOf(stack);
                if (aspects == null || aspects.isEmpty()) {
                    continue;
                }
                groupItem[groups] = item;
                groupDamage[groups] = damage;
                groupSlot[groups] = index;
                groupAspects[groups] = aspects;
                groups++;
            }
        }
        if (groups == 0) {
            return emptyBatch();
        }
        // 第二段：逐组装箱。candidates 只装"被收下的组"，所以它同时就是 runBatch 的入账内容
        final Map<String, Integer> candidates = new LinkedHashMap<>();
        final int[] chosen = new int[groups];
        int used = 0;
        int realGroups = 0;
        int discardedGroups = 0;
        int discardedPoints = 0;
        boolean needsRoom = false;
        boolean overCap = false;
        for (int group = 0; group < groups; group++) {
            final Map<String, Integer> want = toAmountMap(groupAspects[group]);
            if (want.isEmpty()) {
                continue;
            }
            realGroups++;
            final int wantPoints = totalOf(want);
            if (store != null && store.exceedsCellCap(want)) {
                // 单件原量就超单格上限：这组永远放不下 ⇒ 放弃它（物品留在格里），但不冻结别的组
                overCap = true;
                discardedGroups++;
                discardedPoints += wantPoints;
                continue;
            }
            if (store == null || !store.canAcceptAll(want, candidates)) {
                needsRoom = true;
                discardedGroups++;
                discardedPoints += wantPoints;
                continue;
            }
            mergeInto(candidates, want);
            chosen[used++] = groupSlot[group];
        }
        if (realGroups == 0) {
            return emptyBatch();
        }
        final boolean accepted = used > 0;
        // needsRoom 只在"整轮一组都没收下"时才成立：那才是 R29 的"停在满格不重跑"，
        // 有组建效时它必须是 false，否则倒计时被冻住、本轮已收下的组也不再推进
        return new Batch(
            candidates,
            Arrays.copyOf(chosen, used),
            used,
            true,
            accepted,
            !accepted && needsRoom,
            overCap,
            totalOf(candidates),
            discardedGroups,
            discardedPoints);
    }

    /** 本轮无事发生（没有一组含可蒸产物）：不推进、不消耗、零读数。 */
    private static Batch emptyBatch() {
        return new Batch(new LinkedHashMap<String, Integer>(), new int[0], 0, false, false, false, false, 0, 0, 0);
    }

    /** 组身份查表：命中返回组下标，未命中 −1（12 格上限，线性查比建 key 对象便宜）。 */
    private static int indexOfGroup(Item[] items, int[] damages, int size, Item item, int damage) {
        for (int group = 0; group < size; group++) {
            if (items[group] == item && damages[group] == damage) {
                return group;
            }
        }
        return -1;
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
            // ★超上限那组被放弃时不报"满格"也不报"在跑"：条子停着 = 什么都不说，读 0 才是不说谎
            return 0d;
        }
        final int interval = TaumDistillRules.DISTILL_INTERVAL_TICKS;
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
     * 最近一次评估里<b>被放弃的组数</b>（R83 偏差 3c：放弃必须留下读数）。
     * <p>
     * 非 0 的含义是"这轮的这些组放不下，所以一件都没吃"，两种原因（超单格上限 / 格子被存量挤满）
     * 由 {@link #statusOf(UUID)} 与 {@link #progressOf(UUID)} 区分；要"哪一组、哪一 tag"得另开同步位，
     * 本轮只做组数与点数（面板侧见 Panel 待办）。
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

        /** 本轮<b>被收下的那些组</b>的候选：{@code tag → 原量点数}（同 tag 跨组已求和）；入账就用它。 */
        public final Map<String, Integer> candidates;
        /** 被收下的组各自的扣件格号，★长度 {@code == sourceCount}（一组一个落点，多出来的下标不存在）。 */
        public final int[] sourceSlots;
        /** 参与本轮的<b>组</b>数（R83 A2：不再是"参与格数"；同物多格只算一组）。 */
        public final int sourceCount;
        /** 至少有一组含可蒸产物（false ⇒ 不推进、不消耗）。 */
        public final boolean advanceable;
        /** 至少有一组被收下（★唯一的消耗判据，R29 的按组口径）：{@code sourceSlots} 就是被收下的那些组。 */
        public final boolean accepted;
        /** 本轮一组都没收下且原因是"当前存量 + 本轮已许诺量"挤不下 ⇒ 进度停在满格不重跑（可解卡）。 */
        public final boolean needsRoom;
        /** ★R83 偏差 3c：至少有一组因"单件原量本身超单格上限"被放弃（腾格也没救，与 {@link #needsRoom} 分开）。 */
        public final boolean overCap;
        /** 本轮应入账的总点数（= {@link #candidates} 的合计，不含被放弃的组）。 */
        public final int points;
        /** 本轮被放弃的组数（放弃留读数）。 */
        public final int discardedGroups;
        /** 本轮被放弃的点数合计（与 {@link #discardedGroups} 同一批组）。 */
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
