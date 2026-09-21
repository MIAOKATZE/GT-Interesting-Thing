package com.miaokatze.gtit.common.items.pocket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.config.Config;

/**
 * 通道状态机的宿主（R24 定稿的驱动口径 + 内存条目形态）：服务端内存 {@code Map<UUID, PocketChannelState>}。
 * <p>
 * <b>驱动通路（实测口径，R57b/R57c③ 更正本类旧注释）</b>：<b>不是</b> {@code ServerTickEvent}——
 * 全仓九个真 tick 处理器无一属于口袋，本类曾承诺的该机制客观不存在（R57a）。真实通路是
 * {@code EntityPlayer.onLivingUpdate} → {@code InventoryPlayer.decrementAnimations()}
 * （{@code InventoryPlayer.java:341-349} 每 tick 遍历 {@code mainInventory} 全部 36 格）
 * → {@code ItemStack.updateAnimation} → <b>五参</b> {@code Item.onUpdate(stack, world, entity, slot, selected)}
 * → {@code ItemNekoDimensionPocket.onUpdate} → {@code PocketChannelDriver.onItemTick}
 * → {@link #tickShortChannel}。节拍因此天然「每 tick 至多一次」，不需要任何监听器注册顺序假设。
 * <p>
 * 三条刻意的取舍：
 * <ul>
 * <li><b>短效通道不持久化</b>：重载/断线/死亡即停止。续跑会开出「反复重载续传」的刷取面，
 * 把一次性成本摊成无限传输。</li>
 * <li><b>瞬时冷却持久化在口袋 NBT</b>（设备维）+ 内存条目（玩家维）双校验（R16）：
 * 设备维天然随物品共享给任何持有者，玩家维防一人多口袋并发；不加团队维
 * （团队钱包只改变付钱主体）。</li>
 * <li><b>故意不把状态挂到 GUI 生命周期</b>：30 秒通道要求关掉界面仍继续；
 * 也故意不写第二份 {@code WorldSavedData}（状态在世界层、归属在玩家/物品层，会残留他人通道）。
 * 界面关闭后通道仍需的三份内存对象由 {@link PocketChannelState#attachSession} 在激活时挂进状态条目，
 * 因此本类每拍只读内存，不碰 NBT（R53c）。</li>
 * </ul>
 * 单线程访问（服务器主线程），与 {@code NekoMeTransferQueue} 的「仅服务器主线程访问」纪律一致，故不加锁。
 * <p>
 * 纯 JVM 件：玩家以 {@code UUID} 为键，世界/AE2 触点全部经 {@link PocketChannelOps}。
 */
public final class PocketChannelManager {

    public static final PocketChannelManager INSTANCE = new PocketChannelManager();

    /** 玩家维条目表（内存，停服/换档由 {@link #reset()} 清空）。 */
    private final Map<UUID, PocketChannelState> states = new HashMap<>();

    private PocketChannelManager() {}

    /** 取（或建）某玩家的状态条目。 */
    public PocketChannelState stateOf(UUID player) {
        if (player == null) {
            return new PocketChannelState();
        }
        final PocketChannelState existing = states.get(player);
        if (existing != null) {
            return existing;
        }
        final PocketChannelState created = new PocketChannelState();
        states.put(player, created);
        return created;
    }

    /** 不建条目的读法（客户端/只读回显用）。 */
    public PocketChannelState peek(UUID player) {
        return player == null ? null : states.get(player);
    }

    /** 玩家离线/丢弃口袋时清条目。 */
    public void forget(UUID player) {
        if (player != null) {
            states.remove(player);
        }
    }

    /** 停服/换档复位，防跨存档残留通道。 */
    public void reset() {
        states.clear();
    }

    public int trackedPlayers() {
        return states.size();
    }

    /** 每秒可穿的「(元件,通道) 对」数（R9 的「一次」窄口径），来自 {@code config/Config.java}。 */
    public static int pairsPerBatchFromConfig() {
        return Math.max(1, Config.pocketChannelPairsPerSecond);
    }

    // ---------------------------------------------------------------- 开道

    /**
     * 开一条通道（点检 → 由调用方扣费 → 本方法传输；R14 的顺序里"扣费"发生在调用方，
     * 本方法只负责冷却/重入/失联这三项点检与传输）。
     * <p>
     * 本重载等价于「无 ghost 配置」= 推送模式（{@code pullMode=false}），保留给纯 JVM 回归套件；
     * 生产入口走 {@link #openChannel(UUID, Mode, PocketCellBindings, PocketFilterConfig, NBTTagCompound,
     * PocketChannelOps, int)}。
     */
    public boolean openChannel(UUID player, PocketChannelState.Mode mode, PocketCellBindings bindings,
        NBTTagCompound pocketTag, PocketChannelOps ops, int pairs) {
        return openChannel(player, mode, bindings, null, pocketTag, ops, pairs);
    }

    /**
     * 开一条通道（生产入口）。
     * <p>
     * ★R39b 的唯一计算点：{@code pullMode = !filters.isEmpty()} <b>只在这里算一次</b>，随
     * {@link PocketChannelState#attachSession} 进状态条目，之后 30 批与 GUI 回执都读同一个值
     * （客户端不得按 ghost 表推断）。中途新增/删除 ghost 不改变本次运行的模式。
     *
     * @param player    玩家维键
     * @param bindings  该玩家所持口袋的绑定表（激活时快照，运行期不再读 NBT）
     * @param filters   该口袋的 ghost 配置；{@code null}/空 ⇒ 推送，非空 ⇒ 拉取
     * @param pocketTag 口袋物品 NBT（设备维冷却落点，可为 null 表示不落档）
     * @param pairs     短效通道每批穿几对
     * @return true 表示已受理（短效=已登记节拍，瞬时=已穿完）
     */
    public boolean openChannel(UUID player, PocketChannelState.Mode mode, PocketCellBindings bindings,
        PocketFilterConfig filters, NBTTagCompound pocketTag, PocketChannelOps ops, int pairs) {
        if (mode == null) {
            stateOf(player).stop();
            return false;
        }
        final PocketChannelState state = stateOf(player);
        final boolean pull = filters != null && !filters.isEmpty();
        if (mode == PocketChannelState.Mode.BURST) {
            state.attachSession(bindings, filters, null, pull);
            final boolean accepted = requestBurst(player, bindings, pocketTag, ops);
            if (!accepted) {
                state.stop();
            }
            return accepted;
        }
        state.activate(mode, ops.currentTick(), ops.nowMs());
        state.attachSession(bindings, filters, null, pull);
        // 短效通道不在这里跑第一批：激活时倒计时已装填为整拍（CHANNEL_TICK_PERIOD），
        // 不依赖任何监听器注册顺序（E3 §3.4），也不引用绝对 tick（R62）。
        return true;
    }

    /**
     * 瞬时通道：同 tick 闩 + 双维冷却点检 → 一次调用内穿完全部绑定 → 合并一次网络通知
     * → 记冷却（玩家维内存 + 设备维 NBT）。
     *
     * @return false 表示被冷却或重入挡住（调用方据此发失败码，且不应扣费）
     */
    public boolean requestBurst(UUID player, PocketCellBindings bindings, NBTTagCompound pocketTag,
        PocketChannelOps ops) {
        final PocketChannelState state = stateOf(player);
        if (!state.enterBatch()) {
            return false;
        }
        try {
            final long nowMs = ops.nowMs();
            final long nowTick = ops.currentTick();
            if (!state.tryLatchTick(nowTick) || burstRemaining(state, pocketTag, nowMs) > 0L) {
                return false;
            }
            runBatch(state, bindings, ops, Integer.MAX_VALUE);
            state.markBurst(nowMs);
            PocketChannelState.writeDeviceLastBurstAtMs(pocketTag, nowMs);
            return true;
        } finally {
            state.leaveBatch();
        }
    }

    /**
     * 一批的实际执行：<b>推送 / 拉取两条路在此分叉，且互斥</b>（R39b）。
     * <p>
     * 结论取自 {@link PocketChannelState#pullMode()}（激活时算的那一次），不接受调用方临时传参，
     * 否则同一通道会出现两处真相。
     */
    private static PocketChannelRunner.Report runBatch(PocketChannelState state, PocketCellBindings bindings,
        PocketChannelOps ops, int pairLimit) {
        final PocketFilterConfig filters = state == null ? null : state.sessionFilters();
        final PocketChannelRunner.Report report = filters != null && !filters.isEmpty()
            // 拉取：按 ghost 声明从元件抽进真实栏；单条批次量由 ops 按声明物自身上限收口
            ? PocketChannelRunner.runRefillBatch(
                bindings,
                state.rotation(),
                filters,
                ops,
                pairLimit,
                PocketConstants.REFILL_AMOUNT_PER_FILTER_UNBOUNDED)
            : PocketChannelRunner.runInjectBatch(bindings, state == null ? null : state.rotation(), ops, pairLimit);
        if (state != null) {
            state.recordReport(report);
        }
        return report;
    }

    /** 瞬时通道还剩几秒可用（双维取严；0 即可用）。GUI 置灰与点检共用。 */
    public static long burstRemaining(PocketChannelState state, NBTTagCompound pocketTag, long nowMs) {
        return PocketChannelState.burstCooldownRemaining(
            PocketChannelState.readDeviceLastBurstAtMs(pocketTag),
            state == null ? 0L : state.lastBurstAtMs(),
            nowMs,
            PocketConstants.BURST_COOLDOWN_SECONDS);
    }

    // ---------------------------------------------------------------- 节拍

    /**
     * 短效通道的一拍（宿主 {@code Item.onUpdate} 每 tick 调一次；未到拍即 no-op）。
     * <p>
     * 时钟推进走 {@link PocketChannelState#advanceClock()}（相对倒计时），本方法
     * <b>不</b>把 {@code ops.currentTick()} 参与到期比较——那正是 R59e/R60 要消灭的绝对到期形态。
     *
     * @return true 表示本拍确实跑了一批
     */
    public boolean tickShortChannel(UUID player, PocketCellBindings bindings, PocketChannelOps ops, int pairs) {
        final PocketChannelState state = player == null ? null : states.get(player);
        if (state == null || state.idle()) {
            return false;
        }
        state.advanceClock();
        if (!state.due()) {
            return false;
        }
        if (!state.enterBatch()) {
            return false;
        }
        try {
            runBatch(state, bindings, ops, Math.max(1, pairs));
            state.finishBatch();
        } finally {
            state.leaveBatch();
        }
        if (state.idle()) {
            // 30 批用尽即自然回收，不留"已停但占内存"的条目
            if (player != null) {
                states.remove(player);
            }
        }
        return true;
    }
}
