package com.miaokatze.gtit.common.items.pocket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;
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
     * ★R39b 的唯一计算点：<code>pullMode = !filters.isEmpty()</code> <b>每次运行只算一次</b>（算式本身
     * ★R96 S5 起内聚到 {@link #pullPhaseOf} 一处，供本方法与 {@link #ensurePersistentShortChannel}
     * 两个激活口共用 —— <b>两个激活口共用一条算式，不是两条算式各说一遍</b>），随
     * {@link PocketChannelState#attachSession} 进状态条目，之后 30 批与 GUI 回执都读同一个值
     * （客户端不得按 ghost 表推断）。中途新增/删除 ghost 不改变本次运行的模式。
     * ★R87-a 起该位的<b>语义</b>是"本次运行<b>含补满相</b>"——推送/拉取不再互斥（R39b 的互斥口径
     * 作废），注入批无条件跑，这一位只决定补满相在不在。
     *
     * @param player    玩家维键
     * @param bindings  该玩家所持口袋的绑定表（激活时快照，运行期不再读 NBT）
     * @param filters   该口袋的 ghost 配置；{@code null}/空 ⇒ 纯推送，非空 ⇒ 推送 + 补满双相
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
        final boolean pull = pullPhaseOf(filters);
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
     * ★★<b>R96 S5（TP-S5）：把「有位常驻」做成一条真正的生产激活口</b> —— 持续化生效时幂等地保证
     * <b>有一条活的短效通道</b>。
     * <p>
     * <b>它修的是什么</b>（取证 {@code .qoder/tmp/r96-ret1.md} §2.4/§2.5）：R95 那一版里全仓唯一能造出
     * SHORT 状态的入口只有 {@code NekoPocketServerHandler#performChannelRequest} 尾部那条
     * {@code openChannel}，而 {@code CHANNEL_PERSIST} 位一置起，同方法的早退就把它关在门外；
     * driver 的续批腿又排在 {@code if (ranBatch)} 之内，而 {@code ranBatch} 要求"已经有活通道"
     * （{@link #peek} 按设计不建条目）⇒ <b>闭环死锁</b>：没有通道 ⇒ 不会有 ranBatch ⇒ 不会回满 ⇒
     * 永远没有通道。算式一直是对的，缺的从来只是可达性。
     * <p>
     * <b>★不建第二台状态机</b>：本方法一个字节都不写批次/节拍字段，装填一律走
     * {@link PocketChannelState#activate} 那<b>一个</b> SHORT 单点（与 {@link #openChannel} 的短效支
     * 同一条），并且照它的形状<b>成对</b>挂 {@link PocketChannelState#attachSession} —— 缺了后半句
     * 会让 {@code sessionBindings} 为 null，下一拍被 driver 的守卫就地停道（ret1 §2.5 末段点名的坑）。
     * <p>
     * <b>免费、免冷却不是本方法给的</b>：本方法<b>不碰</b>钱包、<b>不碰</b>冷却、<b>不发</b>识别查询；
     * 成本语义仍由调用方那一条"持续化不产生第二条扣费路径"的裁定守着（README 代价 33）。
     *
     * @param player   玩家维键
     * @param carrier  承载这一型的口袋栈（★判据读的就是它；{@code null} ⇒ 不给道）
     * @param bindings 会话里那一份绑定表<b>实例</b>（★必须是会话那一份 —— driver 的 R85 D1 守卫比的是
     *                 对象身份，给它另一份等价表等于下一拍停道）
     * @param filters  会话里那一份 ghost 配置（可为 {@code null} ⇒ 纯推送）
     * @return {@code true} = "有位 ⇒ 有一条活短效通道"这件事在本拍之后<b>成立</b>（含"本来就在跑"那种
     *         幂等命中）；{@code false} = 位没生效 / 没有可搬的东西 / 那条道上跑着别的模式 ⇒
     *         ★三种情况都<b>零写入</b>
     */
    public boolean ensurePersistentShortChannel(UUID player, ItemStack carrier, PocketCellBindings bindings,
        PocketFilterConfig filters) {
        if (player == null || carrier == null || bindings == null || bindings.isEmpty()) {
            // ★没绑定就不开道：那是一条只会空跑、还会把会话永久钉在内存里的通道（retireIdleSession
            // 只在真空态才回收 ⇒ 开一条空道等于替玩家造一个永不退休的活会话）。
            return false;
        }
        if (!PocketUpgradeSwitches.isActive(carrier, PocketUpgradeType.CHANNEL_PERSIST)) {
            // ★组合谓词，不是裸位图：关着开关还白给一条道 = S1 那一族"开关对某条路径无效"的复发形状。
            return false;
        }
        final PocketChannelState live = states.get(player);
        if (live != null && !live.idle()) {
            // 幂等：已经有一条活道 ⇒ 零写入。★唯一不给过的是"跑着别的模式"（BURST 的会话快照不该被
            // 常开腿改写成 SHORT），那种情况由调用方按自己的判据决定要不要继续。
            return live.mode() == PocketChannelState.Mode.SHORT;
        }
        final PocketChannelState state = stateOf(player);
        state.activate(PocketChannelState.Mode.SHORT, 0L, 0L);
        state.attachSession(bindings, filters, null, pullPhaseOf(filters));
        return true;
    }

    /**
     * 本次运行含不含补满相（★R39b 的那一次计算，R96 S5 起与 {@link #ensurePersistentShortChannel}
     * 共用这一个式子 —— 式子内聚到一处，两个激活口才不会算出两个值）。
     */
    private static boolean pullPhaseOf(PocketFilterConfig filters) {
        return filters != null && !filters.isEmpty();
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
     * 一批的实际执行（★R87-a：<b>双相</b>——先注入相（口袋 → 元件）后补满相（元件 → 口袋，按声明），
     * 两相合并成一份 Report；R39b 的"推送/拉取互斥"口径作废）。
     * <p>
     * 补满相在不在读 {@link PocketChannelState#pullMode()}（激活时算的那一次，R87-a 起语义 =
     * "本次运行含补满相"），<b>不</b>现读 {@code filters} 判相，否则同一通道会出现两处真相、
     * 且面板中途增删 ghost 会换轨。与声明语义匹配的来源不进注入相（反成环铁律），
     * 剔除面在 {@code PocketChannelRunner#injectPhase}。
     */
    private static PocketChannelRunner.Report runBatch(PocketChannelState state, PocketCellBindings bindings,
        PocketChannelOps ops, int pairLimit) {
        final PocketChannelRunner.Report report = PocketChannelRunner.runDualPhase(
            bindings,
            state == null ? null : state.rotation(),
            state == null ? null : state.sessionFilters(),
            state != null && state.pullMode(),
            ops,
            pairLimit);
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
