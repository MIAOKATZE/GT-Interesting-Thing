package com.miaokatze.gtit.common.items.pocket.distill;

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
 * <b>五条落地口径</b>（逐条对应计划 §7 S7 与 R63b 的改述）：
 * <ol>
 * <li>节拍 = <b>5 秒/轮</b>，且只引用单一权威
 * {@link TaumDistillRules#DISTILL_INTERVAL_TICKS}（= 100）。本类与 GUI 两侧<b>都没有</b>第二个
 * {@code 100}（计划 §17.2 第 4 条）；</li>
 * <li>计时是 {@link Clock#ticksLeft} 这一<b>相对倒计时</b>（R62）：不读
 * {@code ticksExisted}/{@code getTotalWorldTime()}，因此跨维重建玩家不会让进度漂走；</li>
 * <li>判据照 TC {@code TileAlchemyFurnace.canSmelt()}：{@code getObjectTags + getBonusTags}
 * （由 {@link EssenceGate#aspectsOf} 承担）返回空 ⇒ <b>不推进、不消耗</b>（R28/C4）；</li>
 * <li>产出按 {@code AspectList} <b>原量</b>入账，且<b>全有全无</b>：
 * {@link PocketEssenceStore#canAcceptAll(Map)} → {@code putAll}（R29）。<b>禁止</b>用逐 tag 截断的
 * {@code add()} 或 {@code isFull()} 决定"本轮要不要消耗物品"（R45c/FIX-6：{@code isFull()} 只服务 GUI 置灰）；
 * 装不下时进度<b>停在满格不重跑</b>，一格东西都不丢；</li>
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
        /** 有产物但源质格装不下 ⇒ 进度停在满格不重跑（R29 全有全无）。 */
        STORE_FULL
    }

    /**
     * 每玩家的节拍状态（服务端内存；随会话生死，不做持久化——R24：短效不持久化，
     * 蒸馏进度同理，重启后从 0 重新数 100 tick，代价是玩家多看 5 秒空条，换来的是零跨存档状态）。
     */
    private static final class Clock {

        /** 距下一轮的相对 tick 倒计时（{@code 0} 表示"该跑了/没在跑"）。 */
        int ticksLeft;
        /** 当前 12 格是否至少有一个可蒸物（只在内容或源质总量变化时重算）。 */
        boolean distillable;
        /** 进度停在满格（源质格装不下）。 */
        boolean stalledFull;
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
            clock.checkedSignature = 0L;
            return;
        }
        if (signature != clock.checkedSignature) {
            clock.checkedSignature = signature;
            final Batch batch = planDistillBatch(session, EssenceGate.TAUM);
            clock.distillable = batch.advanceable;
            clock.stalledFull = batch.needsRoom;
            if (!batch.advanceable) {
                clock.ticksLeft = 0;
            }
        }
        if (clock.stalledFull) {
            // 停在满格不重跑：不消耗、不入账、也不倒计时（源质格腾出空间后签名变化即自动解卡）
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
        clock.distillable = batch.advanceable;
        clock.stalledFull = batch.needsRoom;
        if (!batch.advanceable) {
            clock.ticksLeft = 0;
            return;
        }
        if (!batch.accepted) {
            // 全有全无失败 ⇒ 一格都不消耗、一分都不入账
            clock.ticksLeft = 0;
            return;
        }
        session.essence()
            .putAll(batch.candidates);
        for (int index : batch.sourceSlots) {
            session.consumeOneDistillInput(index);
        }
        session.markDirty();
        // 界面已关时写权在 driver 手上（R57c①：关屏后"东西留在格里继续蒸"是正常用法）
        session.persistIdle();
        clock.ticksLeft = TaumDistillRules.DISTILL_INTERVAL_TICKS;
    }

    /**
     * 一轮蒸馏的候选与判据（<b>纯函数</b>，回归套件用桩件 {@link EssenceGate} 直接驱动）。
     * <p>
     * 遍历序 = 格号升序（与 {@code DISTILL_MATRIX} 行主序一致）；每格<b>至多贡献 1 个物品</b>的
     * 源质（R28 的"一轮 = 每格各消耗 1 个"）。★容器格在 {@code DISTILL} 判定处就被跳过，
     * 其 {@link EssenceGate#aspectsOf} <b>永不被调用</b>（R44c/R63b 的硬口径，由
     * {@code distill_path_never_sees_container} 锁死）。
     */
    public static Batch planDistillBatch(PocketSession session, EssenceGate gate) {
        return planDistillBatch(
            session == null ? null : slotsOf(session),
            gate,
            session == null ? null : session.essence());
    }

    /** 数组形态（回归套件用；生产入口是上面的会话重载，两者同一段代码）。 */
    public static Batch planDistillBatch(ItemStack[] slots, EssenceGate gate, PocketEssenceStore store) {
        final Map<String, Integer> candidates = new LinkedHashMap<>();
        final int[] sources = new int[slots == null ? 0 : slots.length];
        int used = 0;
        if (slots != null && gate != null) {
            for (int index = 0; index < slots.length; index++) {
                final ItemStack stack = slots[index];
                if (stack == null || stack.stackSize <= 0) {
                    continue;
                }
                if (PocketSlots.classifyIncoming(stack, gate) != PocketSlots.IncomingAction.DISTILL) {
                    continue;
                }
                final TaumAspectAmounts aspects = gate.aspectsOf(stack);
                if (aspects == null || aspects.isEmpty()) {
                    continue;
                }
                for (int at = 0; at < aspects.size(); at++) {
                    final String tag = aspects.tagAt(at);
                    final int amount = aspects.amountAt(at);
                    if (tag != null && !tag.isEmpty() && amount > 0) {
                        candidates.merge(tag, amount, Integer::sum);
                    }
                }
                sources[used++] = index;
            }
        }
        if (candidates.isEmpty()) {
            return new Batch(candidates, sources, used, false, false, false, 0);
        }
        final boolean room = store != null && store.canAcceptAll(candidates);
        if (!room) {
            return new Batch(candidates, sources, used, true, false, true, totalOf(candidates));
        }
        return new Batch(candidates, sources, used, true, true, false, totalOf(candidates));
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
        if (!clock.distillable) {
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
        return Status.RUNNING;
    }

    /** 距下一轮还剩几秒（{@code still.progress} 的 {@code %d}，与冷却的墙钟口径无关）。 */
    public static int secondsToNextBatch(UUID player) {
        final Clock clock = player == null ? null : CLOCKS.get(player);
        if (clock == null || !clock.distillable || clock.stalledFull) {
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

    /** 蒸馏一轮的结论（值对象；测试直接断言它的四个位）。 */
    public static final class Batch {

        /** 本轮候选：{@code tag → 原量点数}（多格已合并）。 */
        public final Map<String, Integer> candidates;
        /** 参与本轮的格号（每个格各消耗 1 个物品）。 */
        public final int[] sourceSlots;
        /** 参与格数。 */
        public final int sourceCount;
        /** 至少有一个可蒸物（false ⇒ 不推进、不消耗）。 */
        public final boolean advanceable;
        /** 全有全无通过（★唯一的消耗判据，R29）。 */
        public final boolean accepted;
        /** 有产物但源质格放不下 ⇒ 进度停在满格不重跑。 */
        public final boolean needsRoom;
        /** 本轮应入账的总点数。 */
        public final int points;

        Batch(Map<String, Integer> candidates, int[] sourceSlots, int sourceCount, boolean advanceable,
            boolean accepted, boolean needsRoom, int points) {
            this.candidates = candidates;
            this.sourceSlots = sourceSlots;
            this.sourceCount = sourceCount;
            this.advanceable = advanceable;
            this.accepted = accepted;
            this.needsRoom = needsRoom;
            this.points = points;
        }
    }
}
