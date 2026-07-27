package com.miaokatze.gtit.lottery;

/**
 * 抽奖轮盘动画控制器（客户端）
 * <p>
 * 纯客户端时间驱动（{@link System#currentTimeMillis()}）状态机，
 * 由 {@link LotteryGui} 的轮盘格动态绘制 Supplier 每帧求值——不阻塞主线程、
 * 不依赖服务端 tick：
 * <ul>
 * <li>{@link AnimationState#IDLE}：无动画，轮盘格全部正常显示</li>
 * <li>{@link AnimationState#SPINNING}：点亮格绕圈跳动，速度按 cubic ease-out 递减
 * （起步约每 40ms 一格，末尾约每 300ms 一格），总步数 = 整圈数 × 格数 + 到目标格的步数，
 * 保证最后一格精确停在 {@code targetSlot}</li>
 * <li>{@link AnimationState#FINISHED}：停格完成，目标格持续高亮（GUI 叠加闪烁绘制），
 * 直至下一次 {@link #startAnimation} 或 {@link #reset()}</li>
 * </ul>
 * <p>
 * <b>10 连从简</b>：{@code quick=true} 时缩短整圈数与总时长（快闪），
 * 停格取 10 条结果中最高稀有度条目的格索引，结果以列表形式展示。
 * <p>
 * 单例模式：抽奖页全客户端唯一，状态跨 GUI 打开保持（{@link LotteryClientData}
 * 的未消费结果时间戳保证同一结果不会重复触发动画）。
 */
public class LotteryAnimationController {

    /** 动画状态 */
    public enum AnimationState {
        /** 无动画（轮盘静态展示） */
        IDLE,
        /** 旋转中（点亮格绕圈减速） */
        SPINNING,
        /** 已停格（目标格高亮，展示结果） */
        FINISHED
    }

    // ==================== 动画参数 ====================

    /** 单抽整圈数（减速前绕圈次数） */
    private static final int FULL_ROUNDS_SINGLE = 3;
    /** 10 连快闪整圈数 */
    private static final int FULL_ROUNDS_QUICK = 1;
    /** 单抽动画总时长（毫秒）——public：服务端延迟出货调度据此对齐「动画播完再落槽」时机 */
    public static final long DURATION_SINGLE_MS = 2800L;
    /** 10 连快闪总时长（毫秒）——public：同上，延迟出货按连抽数取对应时长 */
    public static final long DURATION_QUICK_MS = 1400L;

    // ==================== 单例 ====================

    private static final LotteryAnimationController INSTANCE = new LotteryAnimationController();

    /** 获取全局唯一动画控制器 */
    public static LotteryAnimationController getInstance() {
        return INSTANCE;
    }

    // ==================== 状态字段 ====================

    /** 当前状态 */
    private AnimationState state = AnimationState.IDLE;
    /** 轮盘格数（= 卡池条目数，启动时快照） */
    private int slotCount = 0;
    /** 起步格索引（承接上次停格，动画连贯） */
    private int startSlot = 0;
    /** 目标格索引（中奖格，服务端结果包给定） */
    private int targetSlot = -1;
    /** 总步进数（整圈 × 格数 + 到目标的步数） */
    private int totalSteps = 0;
    /** 动画起始时间（System.currentTimeMillis） */
    private long startTimeMs = 0L;
    /** 动画总时长（毫秒） */
    private long durationMs = 0L;
    /** 动画所属卡池 ID（防止跨池结果错投到当前轮盘） */
    private String animatingPoolId = "";
    /** 当前动画所对应的抽奖结果时间戳（{@link LotteryClientData#getLastResultTimeMs()}） */
    private long animatingResultTimeMs = 0L;

    private LotteryAnimationController() {}

    // ==================== 动画控制 ====================

    /**
     * 启动轮盘动画
     *
     * @param poolId     结果所属卡池（与当前选中池不一致时直接忽略，防错投）
     * @param targetSlot 停格索引（中奖条目在卡池 entries 中的下标，-1 时取 0 兜底）
     * @param slotCount  轮盘格数（卡池条目数，≤0 时不动画）
     * @param quick      是否快闪模式（10 连：少圈数 + 短时长）
     */
    public void startAnimation(String poolId, int targetSlot, int slotCount, boolean quick) {
        if (slotCount <= 0) return;
        this.animatingPoolId = poolId == null ? "" : poolId;
        this.slotCount = slotCount;
        // 起步格承接上次停格（首次从 0 起）
        this.startSlot = this.targetSlot >= 0 ? this.targetSlot : 0;
        // 目标格取模容错（保底替换可能出现 -1）
        int target = targetSlot < 0 ? 0 : targetSlot % slotCount;
        this.targetSlot = target;

        // 总步数 = 整圈 + 从起步到目标的正向步数
        int stepsToTarget = (target - this.startSlot + slotCount) % slotCount;
        int fullRounds = quick ? FULL_ROUNDS_QUICK : FULL_ROUNDS_SINGLE;
        this.totalSteps = fullRounds * slotCount + stepsToTarget;
        // 兜底：恰好转 0 步（目标 == 起步且 0 圈）时强制至少 1 圈，保证有动画
        if (this.totalSteps <= 0) {
            this.totalSteps = slotCount;
        }

        this.durationMs = quick ? DURATION_QUICK_MS : DURATION_SINGLE_MS;
        this.startTimeMs = System.currentTimeMillis();
        this.state = AnimationState.SPINNING;
    }

    /**
     * 每帧推进（GUI onUpdate / 动态 Supplier 求值时调用）
     * <p>
     * SPINNING 且超时 → FINISHED；其余状态幂等。
     */
    public void onUpdate() {
        if (state == AnimationState.SPINNING && System.currentTimeMillis() - startTimeMs >= durationMs) {
            state = AnimationState.FINISHED;
        }
    }

    /**
     * 当前应点亮的格索引
     * <p>
     * IDLE → -1（无点亮格）；SPINNING → 按 ease-out 位置函数计算的当前格；
     * FINISHED → 目标格（恒亮）。
     *
     * @return 点亮格索引（[0, slotCount)），无动画时 -1
     */
    public int getCurrentLitSlot() {
        switch (state) {
            case SPINNING:
                long elapsed = System.currentTimeMillis() - startTimeMs;
                if (elapsed >= durationMs) {
                    return targetSlot; // 边界：onUpdate 未及时调用时直接给停格
                }
                double progress = elapsed / (double) durationMs;
                // cubic ease-out：起步快、末尾慢（位置 = 总步数 × 缓动值）
                double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
                int step = (int) (totalSteps * eased);
                return (startSlot + step) % slotCount;
            case FINISHED:
                return targetSlot;
            case IDLE:
            default:
                return -1;
        }
    }

    /**
     * 停格后的闪烁相位（FINISHED 状态下目标格高亮闪烁用）
     *
     * @return true 表示当前处于「亮」相位（约 500ms 周期方波）
     */
    public boolean isFinishBlinkOn() {
        if (state != AnimationState.FINISHED) return false;
        // 停格完成时刻起算，500ms 周期方波
        return ((System.currentTimeMillis() - startTimeMs - durationMs) / 250L) % 2 == 0;
    }

    /**
     * 动画是否已停格（FINISHED）
     */
    public boolean isFinished() {
        return state == AnimationState.FINISHED;
    }

    /**
     * 是否正在旋转（SPINNING）
     */
    public boolean isSpinning() {
        return state == AnimationState.SPINNING;
    }

    /**
     * 重置到 IDLE（关闭 GUI / 切换卡池 / 新结果到达前调用）
     */
    public void reset() {
        state = AnimationState.IDLE;
        // 保留 targetSlot 作为下次起步格（startAnimation 读取）
        animatingPoolId = "";
        animatingResultTimeMs = 0L;
    }

    public AnimationState getState() {
        return state;
    }

    /** 动画所属卡池 ID */
    public String getAnimatingPoolId() {
        return animatingPoolId;
    }

    /** 目标格索引（FINISHED 后为中奖格） */
    public int getTargetSlot() {
        return targetSlot;
    }

    /**
     * 当前动画所对应的结果时间戳（v1.7.7 G3：用于把 consumeDrawResult 推迟到动画完全停止后）
     *
     * @return 结果时间戳；未绑定结果时返回 0
     */
    public long getAnimatingResultTimeMs() {
        return animatingResultTimeMs;
    }

    /**
     * 绑定当前动画到指定结果时间戳
     *
     * @param timeMs {@link LotteryClientData#getLastResultTimeMs()}
     */
    public void setAnimatingResultTimeMs(long timeMs) {
        this.animatingResultTimeMs = timeMs;
    }
}
