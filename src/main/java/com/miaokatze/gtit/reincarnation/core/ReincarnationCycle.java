package com.miaokatze.gtit.reincarnation.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 单玩家周目（轮回）记录 + 单次轮回状态机（纯 Java 模型，禁止依赖 MC 类）。
 * <p>
 * 状态机（单轮回互斥）：{@code IDLE --deposit()--> DEPOSITED --confirmReincarnation()-->
 * EXECUTED --claimGrant()--> IDLE}。同一玩家同一时刻至多存在一个未领取轮回
 * （DEPOSITED/EXECUTED 期间 deposit/confirmReincarnation 一律拒绝）；
 * 非法迁移抛 {@link IllegalStateException}，参数非法抛 {@link IllegalArgumentException}。
 * <p>
 * 待领取物品清单的逐件调整口径（v1.9.0 S4 收束）：IDLE/DEPOSITED 可经
 * {@link #addPendingItem}/{@link #withdraw} 逐件增删；EXECUTED（已执行待领取）
 * 期间清单锁定只读，增删一律拒绝——即互斥锁口径"只读锁 = EXECUTED"。
 * <p>
 * 字段语义：
 * <ul>
 * <li>{@code hullProgress}：15 等级列的下标 {@code 0=蒸汽（Steam），1..14=LV..MAX}，
 * 值 = 该列已累计消耗的外壳数；</li>
 * <li>{@code unlockedColumns}：同下标的 15 列解锁标记；</li>
 * <li>{@code unlockedRows}：全局行解锁数（1..3）；</li>
 * <li>{@code executedFingerprints}：已确认轮回的时间线指纹永久集合
 * （指纹 = SHA-256(seed) hex，见 {@link ReincarnationFingerprints}），claim 后保留。</li>
 * </ul>
 * <p>
 * 物品一律以 String registry name + int meta 表示（{@link ItemRef}），
 * ItemStack/ItemStack 映射交由后续 GUI/Handler 切片完成。
 * 本类刻意零外部依赖（java.* only），供无头测试直跑。
 */
public final class ReincarnationCycle {

    /** 轮回状态：待寄存 / 已寄存待确认 / 已执行待领取 */
    public enum CycleState {
        IDLE,
        DEPOSITED,
        EXECUTED
    }

    /** 等级列总数：下标 0=蒸汽，1..14=LV..MAX */
    public static final int COLUMN_COUNT = 15;

    /** 全局行解锁数下限 */
    public static final int MIN_UNLOCKED_ROWS = 1;

    /** 全局行解锁数上限 */
    public static final int MAX_UNLOCKED_ROWS = 3;

    /** 物品引用（String registry name + int meta），不可变值对象 */
    public static final class ItemRef {

        /** 物品 registry name（如 {@code "minecraft:diamond"}） */
        private final String id;
        /** 1.7.10 物品 meta（damage 值） */
        private final int meta;

        public ItemRef(String id, int meta) {
            if (id == null || id.trim()
                .isEmpty()) {
                throw new IllegalArgumentException("ItemRef.id 不能为空");
            }
            this.id = id;
            this.meta = meta;
        }

        public String getId() {
            return id;
        }

        public int getMeta() {
            return meta;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemRef)) return false;
            ItemRef other = (ItemRef) o;
            return meta == other.meta && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode() * 31 + meta;
        }

        @Override
        public String toString() {
            return id + "@" + meta;
        }
    }

    /** 玩家 UUID（canonical 小写形式） */
    private final String uuid;
    /** 单次轮回状态机当前状态 */
    private CycleState cycleState = CycleState.IDLE;
    /** 本轮寄存、待轮回/待领取的物品清单 */
    private final List<ItemRef> pendingItems = new ArrayList<>();
    /** 15 列已累计消耗外壳数（下标语义见类 javadoc） */
    private final int[] hullProgress = new int[COLUMN_COUNT];
    /** 15 列解锁标记 */
    private final boolean[] unlockedColumns = new boolean[COLUMN_COUNT];
    /** 全局行解锁数（1..3） */
    private int unlockedRows = MIN_UNLOCKED_ROWS;
    /** 已执行轮回的时间线指纹永久集合 */
    private final Set<String> executedFingerprints = new LinkedHashSet<>();

    /**
     * @param uuid 玩家 UUID 字符串（会经 {@link UUID#fromString} 校验并 canonical 化为小写）
     * @throws IllegalArgumentException uuid 为空或格式非法
     */
    public ReincarnationCycle(String uuid) {
        if (uuid == null || uuid.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("uuid 不能为空");
        }
        this.uuid = UUID.fromString(uuid)
            .toString();
    }

    // ==================== 只读视图 ====================

    public String getUuid() {
        return uuid;
    }

    public CycleState getCycleState() {
        return cycleState;
    }

    /** @return 待领取物品只读视图 */
    public List<ItemRef> getPendingItems() {
        return Collections.unmodifiableList(pendingItems);
    }

    /** @return 15 列已累计消耗外壳数（防御性副本） */
    public int[] getHullProgress() {
        return hullProgress.clone();
    }

    /** @return 15 列解锁标记（防御性副本） */
    public boolean[] getUnlockedColumns() {
        return unlockedColumns.clone();
    }

    public int getUnlockedRows() {
        return unlockedRows;
    }

    /** @return 已执行轮回的永久指纹集合（只读视图） */
    public Set<String> getExecutedFingerprints() {
        return Collections.unmodifiableSet(executedFingerprints);
    }

    public boolean hasFingerprint(String fingerprint) {
        return fingerprint != null && executedFingerprints.contains(fingerprint.toLowerCase(Locale.ROOT));
    }

    // ==================== 状态机迁移（单轮回互斥） ====================

    /** @return 当前是否允许寄存（仅 IDLE） */
    public boolean canDeposit() {
        return cycleState == CycleState.IDLE;
    }

    /**
     * 寄存本轮轮回物品：IDLE → DEPOSITED。
     *
     * @param items 寄存物品清单（非空、元素非 null）
     * @throws IllegalStateException    存在未完成轮回（DEPOSITED/EXECUTED），单轮回互斥
     * @throws IllegalArgumentException 清单为空或含 null 元素
     */
    public void deposit(List<ItemRef> items) {
        if (!canDeposit()) {
            throw new IllegalStateException("存在未完成的轮回（当前 " + cycleState + "），禁止再次寄存");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("寄存物品清单不能为空");
        }
        for (ItemRef item : items) {
            if (item == null) {
                throw new IllegalArgumentException("寄存物品清单含 null 元素");
            }
        }
        pendingItems.clear();
        pendingItems.addAll(items);
        cycleState = CycleState.DEPOSITED;
    }

    /** @return 当前是否允许确认轮回（仅 DEPOSITED） */
    public boolean canConfirmReincarnation() {
        return cycleState == CycleState.DEPOSITED;
    }

    /**
     * 确认轮回：DEPOSITED → EXECUTED。以 seed 派生时间线指纹并写入永久集合
     * （claim 后保留，实现"该时间线已周目过"的永久标记）。
     *
     * @param seed 时间线种子
     * @return 本轮时间线指纹（64 位小写 hex）
     * @throws IllegalStateException 状态非 DEPOSITED
     */
    public String confirmReincarnation(long seed) {
        if (!canConfirmReincarnation()) {
            throw new IllegalStateException("状态 " + cycleState + " 不能确认轮回（需先寄存进入 DEPOSITED）");
        }
        String fingerprint = ReincarnationFingerprints.fingerprintOf(seed);
        executedFingerprints.add(fingerprint);
        cycleState = CycleState.EXECUTED;
        return fingerprint;
    }

    /** @return 当前是否允许领取轮回奖励（仅 EXECUTED） */
    public boolean canClaimGrant() {
        return cycleState == CycleState.EXECUTED;
    }

    /**
     * 领取轮回奖励：EXECUTED → IDLE。待领取物品清空（返回值即本轮奖励），
     * {@code executedFingerprints} 保留（永久集合不受领取影响）。
     *
     * @return 本轮奖励物品清单（副本）
     * @throws IllegalStateException 状态非 EXECUTED（如 IDLE 直接领取）
     */
    public List<ItemRef> claimGrant() {
        if (!canClaimGrant()) {
            throw new IllegalStateException("状态 " + cycleState + " 没有可领取的轮回奖励");
        }
        List<ItemRef> grant = new ArrayList<>(pendingItems);
        pendingItems.clear();
        cycleState = CycleState.IDLE;
        return grant;
    }

    // ==================== 进度类可变操作（不属于单轮回状态机） ====================

    /**
     * 累计某等级列消耗的外壳数。
     *
     * @param column 列下标（0=蒸汽，1..14=LV..MAX）
     * @param amount 本次消耗数（非负）
     * @throws IllegalArgumentException 列下标越界或 amount 为负
     */
    public void recordHullConsumption(int column, int amount) {
        checkColumn(column);
        if (amount < 0) {
            throw new IllegalArgumentException("外壳消耗数不能为负: " + amount);
        }
        hullProgress[column] += amount;
    }

    /** @return 该列是否已解锁 */
    public boolean isColumnUnlocked(int column) {
        checkColumn(column);
        return unlockedColumns[column];
    }

    /** 解锁某等级列（幂等） */
    public void unlockColumn(int column) {
        checkColumn(column);
        unlockedColumns[column] = true;
    }

    /**
     * 设置全局行解锁数（钳制到 1..3，越界入参不抛错按边界值收敛）。
     */
    public void setUnlockedRows(int rows) {
        this.unlockedRows = Math.max(MIN_UNLOCKED_ROWS, Math.min(MAX_UNLOCKED_ROWS, rows));
    }

    /**
     * 追加时间线指纹到永久集合（幂等；小写归一）。供外部已知指纹的补录路径。
     *
     * @throws IllegalArgumentException 指纹为空
     */
    public void addFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("指纹不能为空");
        }
        executedFingerprints.add(fingerprint.toLowerCase(Locale.ROOT));
    }

    /**
     * 追加单个待领取物品（IDLE/DEPOSITED 允许，支持寄存后逐件补寄；EXECUTED 锁定拒绝）。
     *
     * @throws IllegalStateException 已执行轮回锁定（EXECUTED 期间待领取清单只读）
     */
    public void addPendingItem(ItemRef item) {
        if (cycleState == CycleState.EXECUTED) {
            throw new IllegalStateException("已执行轮回锁定（当前 " + cycleState + "），禁止追加物品");
        }
        if (item == null) {
            throw new IllegalArgumentException("追加物品不能为 null");
        }
        pendingItems.add(item);
    }

    /**
     * 移除一件待领取物品（IDLE/DEPOSITED 可调整；EXECUTED 锁定拒绝，与
     * {@link #addPendingItem} 同一调整口径）。
     *
     * @param ref 待移除物品引用
     * @return 清单中存在并成功移除返回 true；状态可调整但清单中不存在该物品返回 false
     * @throws IllegalStateException    已执行轮回锁定（EXECUTED 期间待领取清单只读）
     * @throws IllegalArgumentException ref 为 null
     */
    public boolean withdraw(ItemRef ref) {
        if (cycleState == CycleState.EXECUTED) {
            throw new IllegalStateException("已执行轮回锁定（当前 " + cycleState + "），禁止移除物品");
        }
        if (ref == null) {
            throw new IllegalArgumentException("移除物品不能为 null");
        }
        return pendingItems.remove(ref);
    }

    // ==================== 内部 ====================

    private static void checkColumn(int column) {
        if (column < 0 || column >= COLUMN_COUNT) {
            throw new IllegalArgumentException("等级列下标越界: " + column + "（合法 0.." + (COLUMN_COUNT - 1) + "）");
        }
    }

    /**
     * 由持久化载荷重建实例（仅 {@link ReincarnationStore} 反序列化路径使用，
     * 不进入公开 API；结构非法抛 {@link IllegalArgumentException}，由 store 按
     * "文件不存在"语义兜底）。
     */
    static ReincarnationCycle restore(String uuid, CycleState cycleState, List<ItemRef> pendingItems,
        int[] hullProgress, boolean[] unlockedColumns, int unlockedRows, Set<String> executedFingerprints) {
        ReincarnationCycle cycle = new ReincarnationCycle(uuid);
        if (cycleState == null) {
            throw new IllegalArgumentException("cycleState 缺失");
        }
        if (hullProgress == null || hullProgress.length != COLUMN_COUNT) {
            throw new IllegalArgumentException(
                "hullProgress 长度非法: " + (hullProgress == null ? "null" : hullProgress.length));
        }
        if (unlockedColumns == null || unlockedColumns.length != COLUMN_COUNT) {
            throw new IllegalArgumentException(
                "unlockedColumns 长度非法: " + (unlockedColumns == null ? "null" : unlockedColumns.length));
        }
        if (pendingItems == null || executedFingerprints == null) {
            throw new IllegalArgumentException("pendingItems/executedFingerprints 缺失");
        }
        for (ItemRef item : pendingItems) {
            if (item == null) {
                throw new IllegalArgumentException("pendingItems 含 null 元素");
            }
        }
        cycle.cycleState = cycleState;
        cycle.pendingItems.addAll(pendingItems);
        System.arraycopy(hullProgress, 0, cycle.hullProgress, 0, COLUMN_COUNT);
        System.arraycopy(unlockedColumns, 0, cycle.unlockedColumns, 0, COLUMN_COUNT);
        cycle.setUnlockedRows(unlockedRows);
        for (String fingerprint : executedFingerprints) {
            cycle.addFingerprint(fingerprint);
        }
        return cycle;
    }

    @Override
    public String toString() {
        return "ReincarnationCycle{uuid=" + uuid
            + ", state="
            + cycleState
            + ", pending="
            + pendingItems.size()
            + ", rows="
            + unlockedRows
            + ", fingerprints="
            + executedFingerprints.size()
            + ", hull="
            + Arrays.toString(hullProgress)
            + "}";
    }
}
