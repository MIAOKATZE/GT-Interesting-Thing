package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 单个玩家的通道状态（服务端内存条目，由 {@link PocketChannelManager} 按 {@code UUID} 持有；
 * 驱动口径见 R24：倒计时走 {@code Item.onUpdate} 递减 NBT 剩余 tick，冷却走墙钟）。
 * <p>
 * 五种计时量的分工（这个分工不是偏好，是两类时钟各有失败模式）：
 * <ul>
 * <li><b>短效通道剩余批次与节拍用「相对 tick 倒计时」</b>：{@link #ticksUntilDue} 每被驱动一次
 * （= 一个 {@code Item.onUpdate} tick）减一，归零即到期。tick 计数天然「每 tick 至多一次」，
 * 墙钟 ms 在同一 tick 内可能被多次放行，需要额外去重。
 * ⚠ <b>不得</b>改回「当前 tick + 节拍」的<b>绝对到期时刻</b>（R59e/R60/R62）：绝对值绑在跨维度
 * <b>不连续</b>的 {@code player.ticksExisted} 上（1.7.10 跨维重建 {@code EntityPlayerMP}），
 * 已写入的到期值会永远大于回退后的当前值 ⇒ 短效通道静默停摆。相对倒计时不引用任何外部时间基准
 * ⇒ 该失败模式结构上不存在。<b>绝对到期与倒计时不得并存成两处真相</b>（R62 第 1 条）。</li>
 * <li><b>瞬时冷却与动画窗口用墙钟 ms</b>：tick 计数随实体重建归零，重载即白嫖刷新；
 * 墙钟天然跨会话。设备维落口袋 NBT（{@link PocketConstants#LAST_BURST_AT_MS}），
 * 玩家维是本对象里的 {@link #lastBurstAtMs} 内存镜像。</li>
 * <li><b>短效通道故意不持久化</b>：重载续跑会把一次性成本摊成无限传输（R24 沿用该防刷理由）。
 * 因此本类没有任何 {@code writeTo}/{@code readFrom}，只有冷却的读写。
 * 运行期所需的绑定表/配置/承载栈由 {@link #attachSession} 在<b>激活时快照一次</b>，
 * 之后每拍只读内存对象（{@code onUpdate} 每 tick 现解 NBT 是 R53c 明令禁止的形态）。</li>
 * </ul>
 * <p>
 * 纯 JVM 件：不持玩家对象，键由 {@link PocketChannelManager} 传入。
 */
public final class PocketChannelState {

    /** 通道模式：未开启 / 短效（每秒一批、共 30 批）/ 瞬时（一次穿完）。 */
    public enum Mode {
        NONE,
        SHORT,
        BURST
    }

    private Mode mode = Mode.NONE;
    /**
     * 距下一拍的<b>相对</b> tick 倒计时（R62）：由 {@link #advanceClock()} 每次减一，{@code <=0} 即到期。
     * 本类不存在任何绝对到期字段，也不参与 {@code ticksExisted}/{@code getTotalTime()} 的比较。
     */
    private int ticksUntilDue;
    /** 短效通道剩余批次数。 */
    private int remainingBatches;
    /**
     * 本次通道运行的推送/拉取结论（R39b）：<b>服务端在激活时算一次</b>，之后 30 批与回执都读这一个值，
     * 客户端只格式化它（见 {@code NekoPocketPanel#composeModeState}），绝不按 ghost 表自行推断。
     */
    private boolean pullMode;
    /** 同 tick 不可重入闩（本 tick 已处理过一次传输请求）。 */
    private long lastHandledTick = Long.MIN_VALUE;
    /** 传输进行中闩：{@code injectItems} 会经宿主回调，理论上可再入（E3 §4.4）。 */
    private boolean inBatch;
    /** 瞬时 burst 的动画显示窗口终点（墙钟 ms，仅内存，随冷却同一个包发给客户端）。 */
    private long burstShownUntilMs;
    /** 玩家维的 burst 冷却起点（墙钟 ms）。 */
    private long lastBurstAtMs;
    /** 该玩家所持口袋的轮转游标（内存件：跨通道公平轮转，不因激活重置）。 */
    private final PocketRotationCursor rotation = new PocketRotationCursor();

    /**
     * 开启一条通道。
     * <p>
     * 本方法<b>不读任何 NBT、不查任何世界时刻</b>：短效节拍只是把 {@link #ticksUntilDue} 装填成
     * {@link PocketConstants#CHANNEL_TICK_PERIOD} 个 tick 的相对倒计时，由
     * {@link PocketChannelManager#tickShortChannel} 每拍减一（R62）。
     *
     * @param activating 目标模式；{@link Mode#NONE} 视为关闭
     * @param nowTick    当前 tick（<b>只</b>用于同 tick 闩的语义对齐，不参与任何到期判定）
     * @param nowMs      当前墙钟毫秒（burst 的冷却/动画窗口）
     * @return true 表示状态确实发生了变化
     */
    public boolean activate(Mode activating, long nowTick, long nowMs) {
        if (activating == null || activating == Mode.NONE) {
            stop();
            return true;
        }
        this.mode = activating;
        if (activating == Mode.BURST) {
            this.markBurst(nowMs);
            // 瞬时是一次穿完，不占短效节拍；把节拍字段留在关闭态
            this.ticksUntilDue = 0;
            this.remainingBatches = 0;
            return true;
        }
        this.ticksUntilDue = PocketConstants.CHANNEL_TICK_PERIOD;
        this.remainingBatches = PocketConstants.SHORT_CHANNEL_BATCHES;
        return true;
    }

    /** 关闭通道并清掉短效节拍与会话快照；不动冷却（冷却跟着物品/玩家走，与开关无关）。 */
    public void stop() {
        this.mode = Mode.NONE;
        this.ticksUntilDue = 0;
        this.remainingBatches = 0;
        this.pullMode = false;
        this.bindings = null;
        this.filters = null;
        this.carrier = null;
        this.lastReport = null;
    }

    public Mode mode() {
        return mode;
    }

    public boolean idle() {
        return mode == Mode.NONE;
    }

    /**
     * 短效通道是否到期该跑一批。<b>只看相对倒计时</b>（R62：不再有 {@code dueAt(nowTick)} 的绝对比较）。
     */
    public boolean due() {
        return mode == Mode.SHORT && remainingBatches > 0 && ticksUntilDue <= 0;
    }

    /**
     * 时钟走一格（由宿主每 tick 调一次；一次 {@code Item.onUpdate} = 一格，天然不会重复放行）。
     * 已到期就不再往下减成负数，进度语义停在 0。
     */
    public void advanceClock() {
        if (mode == Mode.SHORT && ticksUntilDue > 0) {
            ticksUntilDue--;
        }
    }

    /** 跑完一批后重新装填倒计时；批次用尽即回到 {@link Mode#NONE}。 */
    public void finishBatch() {
        if (mode != Mode.SHORT) {
            return;
        }
        remainingBatches--;
        if (remainingBatches <= 0) {
            stop();
            return;
        }
        ticksUntilDue = PocketConstants.CHANNEL_TICK_PERIOD;
    }

    public int remainingBatches() {
        return remainingBatches;
    }

    /** 距下一拍还剩几个 tick（动画/进度回显用；未开启即 0）。 */
    public int ticksUntilDue() {
        return ticksUntilDue;
    }

    /** 本次通道运行的推送/拉取结论（服务端在激活时算一次，随回执下发，R39b）。 */
    public boolean pullMode() {
        return pullMode;
    }

    // ---------------------------------------------------------------- 运行期会话快照（R53c：每拍不得现解 NBT）

    /** 激活时快照的绑定表；通道关闭后为 {@code null}。 */
    private PocketCellBindings bindings;
    /** 激活时快照的 ghost 配置（拉取模式按它补满）；通道关闭后为 {@code null}。 */
    private PocketFilterConfig filters;
    /** 承载本次通道的口袋栈（设备维冷却与落盘归属）；通道关闭后为 {@code null}。 */
    private ItemStack carrier;
    /**
     * 最近一批的统计（{@code null} = 还没跑过批）。
     * <p>
     * 存在的理由：R14 要求"全失败同回执退款、部分失败不退"，而扣费发生在调用方（面板），
     * 调用方因此必须能读到本次传输的失败构成。本字段只是上一批的结果<b>镜像</b>，
     * 不参与任何判定（判定在 {@code PocketChannelRunner} 内已做完），不构成第二处真相。
     */
    private PocketChannelRunner.Report lastReport;

    /**
     * 把本次通道运行需要的三份内存对象挂到状态条目上（<b>只在服务端、只在激活时调一次</b>）。
     * <p>
     * 为什么是快照引用而不是每 tick 重读 NBT：宿主是 {@code Item.onUpdate}（每 tick 一次），
     * 现解 NBT 正是 R53c 点名的成本形态；而绑定表/配置在会话内只可能由面板改，面板改的
     * 就是这里挂的同一批对象（{@code PocketInventory} 持有的实例），因此"快照"不等于"陈旧"。
     */
    public void attachSession(PocketCellBindings bindings, PocketFilterConfig filters, ItemStack carrier,
        boolean pullMode) {
        this.bindings = bindings;
        this.filters = filters;
        this.carrier = carrier;
        this.pullMode = pullMode;
    }

    public PocketCellBindings sessionBindings() {
        return bindings;
    }

    public PocketFilterConfig sessionFilters() {
        return filters;
    }

    public ItemStack sessionCarrier() {
        return carrier;
    }

    /** 最近一批的统计（面板据此发 R14 口径的回执；未跑过批为 {@code null}）。 */
    public PocketChannelRunner.Report lastReport() {
        return lastReport;
    }

    /** 由 {@link PocketChannelManager} 在每批结束后写入（调用方不得自行构造）。 */
    void recordReport(PocketChannelRunner.Report report) {
        this.lastReport = report;
    }

    /**
     * 承载栈换了对象（关屏后重定位、跨维重建）时由宿主回填引用，不改模式与批次。
     * 传 {@code null} 表示"暂时找不到承载栈"，此时设备维写入自然跳过（{@code PocketChannelState#writeDeviceLastBurstAtMs} 已判 null）。
     */
    public void retargetCarrier(ItemStack carrier) {
        this.carrier = carrier;
    }

    /**
     * 同 tick 闩：同一玩家在同一 tick 内只放行第一个请求（防连点/重复包造成的自重入）。
     *
     * @return true 表示本 tick 尚未处理过，可以继续
     */
    public boolean tryLatchTick(long nowTick) {
        if (lastHandledTick == nowTick) {
            return false;
        }
        lastHandledTick = nowTick;
        return true;
    }

    /** 进入传输段（配合 {@code try/finally} 与 {@link #leaveBatch()} 使用）。已在段内则返回 false。 */
    public boolean enterBatch() {
        if (inBatch) {
            return false;
        }
        inBatch = true;
        return true;
    }

    public void leaveBatch() {
        inBatch = false;
    }

    public boolean inBatch() {
        return inBatch;
    }

    /** 记一次瞬时通道：刷新玩家维冷却与动画窗口。 */
    public void markBurst(long nowMs) {
        this.lastBurstAtMs = nowMs;
        this.burstShownUntilMs = nowMs + PocketConstants.BURST_ANIMATION_MS;
    }

    public long lastBurstAtMs() {
        return lastBurstAtMs;
    }

    /** burst 动画是否仍在显示窗口内（客户端渲染侧与 GUI 状态回显共用这个判据）。 */
    public boolean showBurstAnimation(long nowMs) {
        return nowMs < burstShownUntilMs;
    }

    public long burstShownUntilMs() {
        return burstShownUntilMs;
    }

    /** 本玩家所持口袋的轮转游标（序号不因激活重置）。 */
    public PocketRotationCursor rotation() {
        return rotation;
    }

    // ---------------------------------------------------------------- 冷却口径

    /**
     * 墙钟冷却剩余秒数，口径逐字照 {@code trade/NekoTradeHistory.java:57-61} 的
     * {@code getCooldownRemaining}：{@code (now - last) / 1000} 与冷却秒数相减，负数钳到 0。
     *
     * @param lastAtMs        上次触发时刻（&lt;=0 表示从未触发 ⇒ 无冷却）
     * @param nowMs           当前墙钟毫秒
     * @param cooldownSeconds 冷却秒数（&lt;=0 表示不冷却）
     */
    public static long remainingCooldownSeconds(long lastAtMs, long nowMs, int cooldownSeconds) {
        if (cooldownSeconds <= 0 || lastAtMs <= 0) {
            return 0;
        }
        final long elapsed = (nowMs - lastAtMs) / 1000L;
        final long remaining = cooldownSeconds - elapsed;
        return remaining > 0 ? remaining : 0;
    }

    /**
     * 双维校验（R16）：设备维（口袋 NBT 的 {@code lastBurstAtMs}，天然随物品共享给任何持有者）
     * 与玩家维（内存条目，防一人多口袋并发）取更严的一侧。
     * <p>
     * 不再加团队维：团队钱包只改变<b>付钱主体</b>，不改变冷却共享口径；若两名队员交替触发
     * 同一枚元件而各自持有不同口袋，则由设备维与玩家维分别覆盖不到的部分记为未决项 P8。
     */
    public static long burstCooldownRemaining(long deviceLastBurstAtMs, long playerLastBurstAtMs, long nowMs,
        int cooldownSeconds) {
        return Math.max(
            remainingCooldownSeconds(deviceLastBurstAtMs, nowMs, cooldownSeconds),
            remainingCooldownSeconds(playerLastBurstAtMs, nowMs, cooldownSeconds));
    }

    /** 口袋 NBT 里的设备维冷却起点；缺档即 0（表示未触发过）。 */
    public static long readDeviceLastBurstAtMs(NBTTagCompound root) {
        return root == null ? 0L : root.getLong(PocketConstants.LAST_BURST_AT_MS);
    }

    /** 把设备维冷却起点写入口袋 NBT（只在服务端；一次 burst 成功后写）。 */
    public static void writeDeviceLastBurstAtMs(NBTTagCompound root, long nowMs) {
        if (root != null) {
            root.setLong(PocketConstants.LAST_BURST_AT_MS, nowMs);
        }
    }
}
