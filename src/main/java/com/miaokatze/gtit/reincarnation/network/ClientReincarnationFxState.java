package com.miaokatze.gtit.reincarnation.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 周目系统客户端 FX 状态 holder（v1.9.0，纯静态字段容器）
 * <p>
 * <b>仅物理 CLIENT 注册路径可加载，common 禁止引用。</b>
 * 本类只被 {@code ClientProxy.init()} 注册路径上的 packet Handler 写入
 * （S→C 四包：发放演出/飞升开始/倒计时/状态快照），真实 FX 渲染与 GUI 展示由消费方
 * （S8 演出切片 / S6 GUI 切片）在客户端主线程读取——handler 运行在 Netty 线程，
 * 全部字段 volatile 保证跨线程可见性；渲染方对多字段一致性有要求时以
 * {@link #getLastSync()} 的不可变快照为准。
 * <p>
 * 本切片只落状态与日志：演出启动/推进/收尾的 tick 采样由消费方回填
 * （{@link #setGrantEffectStartTick(long)}，-1 表示尚未回填）。
 */
public final class ClientReincarnationFxState {

    /**
     * 周目只读状态不可变快照（GUI 只读展示用）
     */
    public static final class SyncSnapshot {

        /** 周目状态枚举 ordinal */
        public final int cycleStateOrdinal;
        /** 已解锁行数 */
        public final int unlockedRows;
        /** 船体进度（防御副本，约定长度 15） */
        public final int[] hullProgress;
        /** 已解锁列标记（防御副本，约定长度 15） */
        public final boolean[] unlockedColumns;
        /** 待领取发放物品数 */
        public final int pendingItemsCount;
        /** 是否存在未领取轮回（互斥锁位） */
        public final boolean mutextLock;

        public SyncSnapshot(int cycleStateOrdinal, int unlockedRows, int[] hullProgress, boolean[] unlockedColumns,
            int pendingItemsCount, boolean mutextLock) {
            this.cycleStateOrdinal = cycleStateOrdinal;
            this.unlockedRows = unlockedRows;
            this.hullProgress = hullProgress == null ? new int[0] : hullProgress.clone();
            this.unlockedColumns = unlockedColumns == null ? new boolean[0] : unlockedColumns.clone();
            this.pendingItemsCount = pendingItemsCount;
            this.mutextLock = mutextLock;
        }
    }

    // ==================== 发放演出（GrantEffectPacket） ====================

    /** 待发放物品清单（演出展示用，实际发放由服务端另行完成） */
    private static volatile List<GrantEffectPacket.ItemRef> pendingGrantItems = Collections.emptyList();
    /** 发放演出起始客户端 tick（由消费方主线程回填；-1 = 尚未回填） */
    private static volatile long grantEffectStartTick = -1L;
    /** 发放演出起始墙钟毫秒（handler 落包时刻，{@link System#currentTimeMillis()} 口径） */
    private static volatile long grantEffectStartMillis = 0L;

    // ==================== 飞升演出（AscensionStartPacket） ====================

    /** 飞升演出是否进行中 */
    private static volatile boolean ascensionActive = false;
    /** 飞升实体 ID（-1 = 未指定） */
    private static volatile int ascensionTargetEntityId = -1;
    /** 飞升演出起始客户端 tick（消费方主线程回填；-1 = 尚未回填）——渐变/音效渐强的时长基准 */
    private static volatile long ascensionStartTick = -1L;

    // ==================== 倒计时（CountdownPacket） ====================

    /** 轮回倒计时截止时刻（绝对墙钟毫秒；0 = 无进行中的倒计时） */
    private static volatile long countdownDeadlineMillis = 0L;

    // ==================== 周目只读快照（ReincarnationSyncPacket） ====================

    /** 最近一次周目只读状态快照（GUI 展示用；null = 尚未收到） */
    private static volatile SyncSnapshot lastSync = null;

    private ClientReincarnationFxState() {
        // 纯静态 holder，禁止实例化
    }

    // ==================== 发放演出 ====================

    /**
     * 写入发放演出载荷（GrantEffectPacket.Handler 调用）：置清单 + 起始毫秒，并重置 tick 回填位
     */
    public static void beginGrantEffect(List<GrantEffectPacket.ItemRef> items) {
        pendingGrantItems = items == null ? Collections.<GrantEffectPacket.ItemRef>emptyList()
            : Collections.unmodifiableList(new ArrayList<>(items));
        grantEffectStartMillis = System.currentTimeMillis();
        grantEffectStartTick = -1L;
    }

    public static List<GrantEffectPacket.ItemRef> getPendingGrantItems() {
        return pendingGrantItems;
    }

    public static long getGrantEffectStartMillis() {
        return grantEffectStartMillis;
    }

    public static long getGrantEffectStartTick() {
        return grantEffectStartTick;
    }

    /**
     * 消费方（S8）在客户端主线程回填演出起始 tick（-1 表示尚未回填）
     */
    public static void setGrantEffectStartTick(long tick) {
        grantEffectStartTick = tick;
    }

    /**
     * 清除发放演出状态（演出收尾或玩家登出时调用）
     */
    public static void clearGrantEffect() {
        pendingGrantItems = Collections.emptyList();
        grantEffectStartMillis = 0L;
        grantEffectStartTick = -1L;
    }

    // ==================== 飞升演出 ====================

    /**
     * 置飞升演出进行位（true = AscensionStartPacket 到达；false = 消费方判定演出结束条件满足）
     */
    public static void setAscensionActive(boolean active) {
        ascensionActive = active;
    }

    public static boolean isAscensionActive() {
        return ascensionActive;
    }

    public static int getAscensionTargetEntityId() {
        return ascensionTargetEntityId;
    }

    public static void setAscensionTargetEntityId(int entityId) {
        ascensionTargetEntityId = entityId;
    }

    /** 飞升演出起始客户端 tick（消费方主线程回填；-1 = 尚未回填） */
    public static long getAscensionStartTick() {
        return ascensionStartTick;
    }

    /** 消费方（FX 切片）在客户端主线程回填飞升演出起始 tick（-1 表示尚未回填） */
    public static void setAscensionStartTick(long tick) {
        ascensionStartTick = tick;
    }

    public static void clearAscension() {
        ascensionActive = false;
        ascensionTargetEntityId = -1;
        ascensionStartTick = -1L;
    }

    // ==================== 倒计时 ====================

    public static void setCountdownDeadlineMillis(long deadlineMillis) {
        countdownDeadlineMillis = deadlineMillis;
    }

    public static long getCountdownDeadlineMillis() {
        return countdownDeadlineMillis;
    }

    public static void clearCountdown() {
        countdownDeadlineMillis = 0L;
    }

    // ==================== 周目只读快照 ====================

    /**
     * 写入周目只读状态快照（ReincarnationSyncPacket.Handler 调用；数组做防御副本）
     */
    public static void updateSync(int cycleStateOrdinal, int unlockedRows, int[] hullProgress,
        boolean[] unlockedColumns, int pendingItemsCount, boolean mutextLock) {
        lastSync = new SyncSnapshot(
            cycleStateOrdinal,
            unlockedRows,
            hullProgress,
            unlockedColumns,
            pendingItemsCount,
            mutextLock);
    }

    public static SyncSnapshot getLastSync() {
        return lastSync;
    }

    public static void clearLastSync() {
        lastSync = null;
    }

    // ==================== 全量清理 ====================

    /**
     * 清空全部 FX/展示状态（玩家登出、维度切换等场景由客户端消费方调用；幂等）
     */
    public static void clear() {
        clearGrantEffect();
        clearAscension();
        clearCountdown();
        clearLastSync();
    }
}
