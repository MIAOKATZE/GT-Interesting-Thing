package com.miaokatze.gtit.reincarnation.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;

/**
 * 周目进度「每存档隔离」载体（v1.9.x D1 切片）：overworld MapStorage 的
 * {@link WorldSavedData}，随当前存档（world save）持久化。
 * <p>
 * <b>职责切分（D1 方案）</b>：跨存档共享的全局文件（{@code config/gtit/reincarnation.dat}，
 * 见 {@link com.miaokatze.gtit.reincarnation.core.ReincarnationStore}）只保留「许可字段」
 * （UUID + 永久时间线指纹 + 投胎信箱）；而<b>属于单个存档</b>的周目进度——15 列外壳进度、
 * 15 列解锁标记、全局行解锁数、本轮寄存物品快照与一次性迁移标记——迁入本类，
 * 从根上修复"换存档进度/待寄存物品串档"问题。
 * <p>
 * <b>侧与线程</b>：服务端专用（仅集成服线程路径触达；物理专用服务器上周目系统整体门控，
 * 本类不可达）。{@link #get(World)} 只接受服务端 overworld（调用方传
 * {@code MinecraftServer.getServer().getEntityWorld()}），客户端传入即抛
 * {@link IllegalArgumentException}。
 * <p>
 * <b>NBT 布局</b>（data 根下）：
 * <ul>
 * <li>{@code HullProgress}：int[15]（长度非法按缺省复位，防御手改档）；</li>
 * <li>{@code UnlockedColumns}：byte[15]（0/1，长度非法按缺省复位）；</li>
 * <li>{@code UnlockedRows}：int（写回时经 {@link ReincarnationCycle#setUnlockedRows} 同款
 * 钳制语义在 applyTo 阶段收敛）；</li>
 * <li>{@code DepositedItems}：{@code [{Id, Meta}]}——本轮寄存物品快照（DEPOSITED 态），
 * EXECUTED（投胎信箱在全局文件）后清空；</li>
 * <li>{@code Migrated}：boolean——旧全局 v1 格式一次性 consume-once 迁移标记
 * （见 {@link ReincarnationMigration}），置位后不再重复合并；</li>
 * <li>{@code GrantClaimed}：boolean（v1.8.6 新增）——本存档轮回奖励"已领取"标记，
 * 发放门控（{@code ReincarnationGrantGate}）三信号之一；新增键、旧档读取缺省
 * false、无迁移。</li>
 * </ul>
 * 变更一律 {@link #markDirty()}，由 vanilla 存档保存期落盘。
 */
public class ReincarnationWorldData extends WorldSavedData {

    /** MapStorage 键（与 {@link #ReincarnationWorldData(String)} 构造同名） */
    public static final String DATA_NAME = "gtit_reincarnation_world";

    private static final String TAG_HULL_PROGRESS = "HullProgress";
    private static final String TAG_UNLOCKED_COLUMNS = "UnlockedColumns";
    private static final String TAG_UNLOCKED_ROWS = "UnlockedRows";
    private static final String TAG_DEPOSITED_ITEMS = "DepositedItems";
    private static final String TAG_MIGRATED = "Migrated";
    /** v1.8.6：本存档轮回奖励"已领取"标记（发放门控三信号之一） */
    private static final String TAG_GRANT_CLAIMED = "GrantClaimed";

    private static final String TAG_ITEM_ID = "Id";
    private static final String TAG_ITEM_META = "Meta";

    /** 15 列已累计消耗外壳数（下标 0=蒸汽，1..14=LV..MAX） */
    private final int[] hullProgress = new int[ReincarnationCycle.COLUMN_COUNT];
    /** 15 列解锁标记 */
    private final boolean[] unlockedColumns = new boolean[ReincarnationCycle.COLUMN_COUNT];
    /** 全局行解锁数（1..3） */
    private int unlockedRows = ReincarnationCycle.MIN_UNLOCKED_ROWS;
    /** 本轮寄存物品快照（DEPOSITED 态）；EXECUTED 后清空（信箱在全局文件） */
    private final List<ReincarnationCycle.ItemRef> depositedItems = new ArrayList<>();
    /** 旧全局 v1 格式 consume-once 迁移标记 */
    private boolean migrated;
    /** v1.8.6：本存档轮回奖励已领取（completeGrant 清信箱后置位，发放门控拒绝重复发放） */
    private boolean grantClaimed;

    /** MapStorage.loadData 反射恢复路径所需构造（name 必须与 {@link #DATA_NAME} 一致） */
    public ReincarnationWorldData(String name) {
        super(name);
    }

    /**
     * 取/建当前存档的周目进度数据（overworld MapStorage 幂等语义，同
     * Infinity Cell {@code StorageManager} 接线先例）。
     *
     * @param overworld 服务端主世界（{@code MinecraftServer.getServer().getEntityWorld()}）；
     *                  客户端世界传入即拒绝
     */
    public static ReincarnationWorldData get(World overworld) {
        if (overworld == null || overworld.isRemote) {
            throw new IllegalArgumentException("ReincarnationWorldData 仅服务端 overworld 可用");
        }
        ReincarnationWorldData data = (ReincarnationWorldData) overworld.mapStorage
            .loadData(ReincarnationWorldData.class, DATA_NAME);
        if (data == null) {
            data = new ReincarnationWorldData(DATA_NAME);
            overworld.mapStorage.setData(DATA_NAME, data);
        }
        return data;
    }

    // ==================== 与 ReincarnationCycle 的双向搬运 ====================

    /**
     * 把每存档进度灌入权威周目模型（GUI 打开/登录加载后的合并步）：
     * 进度字段经 {@link ReincarnationCycle} 公开变更口写入；寄存物品快照仅在模型
     * 可寄存（IDLE）时 {@code deposit}（EXECUTED 互斥锁语义不受影响）。
     */
    public void applyTo(ReincarnationCycle cycle) {
        if (cycle == null) {
            return;
        }
        int[] hull = getHullProgress();
        for (int i = 0; i < ReincarnationCycle.COLUMN_COUNT; i++) {
            if (hull[i] > 0) {
                cycle.recordHullConsumption(i, hull[i]);
            }
            if (this.unlockedColumns[i]) {
                cycle.unlockColumn(i);
            }
        }
        cycle.setUnlockedRows(this.unlockedRows);
        if (cycle.canDeposit() && !this.depositedItems.isEmpty()) {
            cycle.deposit(new ArrayList<>(this.depositedItems));
        }
    }

    /**
     * 从权威周目模型回写每存档快照（保存步）：进度全量覆盖；寄存物品快照按状态
     * 收敛——EXECUTED（投胎信箱已写入全局文件）时清空，其余状态取
     * {@code pendingItems} 快照。变更即 {@link #markDirty()}。
     */
    public void saveFrom(ReincarnationCycle cycle) {
        if (cycle == null) {
            return;
        }
        System.arraycopy(cycle.getHullProgress(), 0, this.hullProgress, 0, ReincarnationCycle.COLUMN_COUNT);
        System.arraycopy(cycle.getUnlockedColumns(), 0, this.unlockedColumns, 0, ReincarnationCycle.COLUMN_COUNT);
        this.unlockedRows = cycle.getUnlockedRows();
        this.depositedItems.clear();
        if (cycle.getCycleState() != ReincarnationCycle.CycleState.EXECUTED) {
            this.depositedItems.addAll(cycle.getPendingItems());
        }
        markDirty();
    }

    // ==================== 字段访问器 ====================

    /** @return 15 列外壳进度（防御性副本） */
    public int[] getHullProgress() {
        return this.hullProgress.clone();
    }

    /** 全量覆盖 15 列外壳进度（长度不符按缺省复位；入参为 null 时清零） */
    public void setHullProgress(int[] values) {
        java.util.Arrays.fill(this.hullProgress, 0);
        if (values != null) {
            int n = Math.min(values.length, ReincarnationCycle.COLUMN_COUNT);
            System.arraycopy(values, 0, this.hullProgress, 0, n);
        }
        markDirty();
    }

    /** @return 15 列解锁标记（防御性副本） */
    public boolean[] getUnlockedColumns() {
        return this.unlockedColumns.clone();
    }

    /** 全量覆盖 15 列解锁标记（长度不符按缺省复位；入参为 null 时清 false） */
    public void setUnlockedColumns(boolean[] values) {
        java.util.Arrays.fill(this.unlockedColumns, false);
        if (values != null) {
            int n = Math.min(values.length, ReincarnationCycle.COLUMN_COUNT);
            System.arraycopy(values, 0, this.unlockedColumns, 0, n);
        }
        markDirty();
    }

    public int getUnlockedRows() {
        return this.unlockedRows;
    }

    /** 行解锁数（越界入参按 {@link ReincarnationCycle#setUnlockedRows} 同款钳制 1..3 收敛） */
    public void setUnlockedRows(int rows) {
        this.unlockedRows = Math
            .max(ReincarnationCycle.MIN_UNLOCKED_ROWS, Math.min(ReincarnationCycle.MAX_UNLOCKED_ROWS, rows));
        markDirty();
    }

    /** @return 本轮寄存物品快照（只读视图） */
    public List<ReincarnationCycle.ItemRef> getDepositedItems() {
        return Collections.unmodifiableList(this.depositedItems);
    }

    /** 覆盖寄存物品快照（null 按空清单；元素 null 跳过） */
    public void setDepositedItems(List<ReincarnationCycle.ItemRef> items) {
        this.depositedItems.clear();
        if (items != null) {
            for (ReincarnationCycle.ItemRef item : items) {
                if (item != null) {
                    this.depositedItems.add(item);
                }
            }
        }
        markDirty();
    }

    /** @return 旧全局 v1 一次性迁移是否已完成（置位后 {@link ReincarnationMigration} 不再重试） */
    public boolean isMigrated() {
        return this.migrated;
    }

    public void setMigrated(boolean migrated) {
        this.migrated = migrated;
        markDirty();
    }

    /**
     * @return 本存档轮回奖励是否已领取（v1.8.6 发放门控：true 即拦截重复发放；
     *         新键，旧档读取缺省 false——无迁移，历史存档天然视为未领取）
     */
    public boolean isGrantClaimed() {
        return this.grantClaimed;
    }

    /** 置/清已领取标记（completeGrant 清信箱<b>之后</b>置位，顺序铁律见 Handler） */
    public void setGrantClaimed(boolean grantClaimed) {
        this.grantClaimed = grantClaimed;
        markDirty();
    }

    /**
     * Flush this data immediately through the overworld MapStorage save cycle.
     * MapStorage.saveData(WorldSavedData) is private in 1.7.10; saveAllData() is
     * the public immediate API and writes every dirty entry synchronously.
     */
    public void saveImmediately(World overworld) {
        if (overworld == null || overworld.isRemote || overworld.mapStorage == null) {
            throw new IllegalArgumentException("ReincarnationWorldData 需要服务端 overworld MapStorage");
        }
        overworld.mapStorage.saveAllData();
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void readFromNBT(NBTTagCompound data) {
        java.util.Arrays.fill(this.hullProgress, 0);
        int[] hull = data.getIntArray(TAG_HULL_PROGRESS);
        int n = Math.min(hull.length, ReincarnationCycle.COLUMN_COUNT);
        System.arraycopy(hull, 0, this.hullProgress, 0, n);

        java.util.Arrays.fill(this.unlockedColumns, false);
        byte[] columns = data.getByteArray(TAG_UNLOCKED_COLUMNS);
        int m = Math.min(columns.length, ReincarnationCycle.COLUMN_COUNT);
        for (int i = 0; i < m; i++) {
            this.unlockedColumns[i] = columns[i] != 0;
        }

        int rows = data.getInteger(TAG_UNLOCKED_ROWS);
        this.unlockedRows = rows < ReincarnationCycle.MIN_UNLOCKED_ROWS || rows > ReincarnationCycle.MAX_UNLOCKED_ROWS
            ? ReincarnationCycle.MIN_UNLOCKED_ROWS
            : rows;

        this.depositedItems.clear();
        NBTTagList items = data.getTagList(TAG_DEPOSITED_ITEMS, 10);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound item = items.getCompoundTagAt(i);
            String id = item.getString(TAG_ITEM_ID);
            if (id == null || id.isEmpty()) {
                continue; // 缺 id 的损坏条目跳过（不阻塞其余快照）
            }
            this.depositedItems.add(new ReincarnationCycle.ItemRef(id, item.getInteger(TAG_ITEM_META)));
        }

        this.migrated = data.getBoolean(TAG_MIGRATED);
        // v1.8.6 新键：旧档/损坏档缺省 false（无迁移语义），历史存档首次过门控按未领取处理
        this.grantClaimed = data.getBoolean(TAG_GRANT_CLAIMED);
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        data.setIntArray(TAG_HULL_PROGRESS, this.hullProgress);
        byte[] columns = new byte[ReincarnationCycle.COLUMN_COUNT];
        for (int i = 0; i < ReincarnationCycle.COLUMN_COUNT; i++) {
            columns[i] = (byte) (this.unlockedColumns[i] ? 1 : 0);
        }
        data.setByteArray(TAG_UNLOCKED_COLUMNS, columns);
        data.setInteger(TAG_UNLOCKED_ROWS, this.unlockedRows);

        NBTTagList items = new NBTTagList();
        for (ReincarnationCycle.ItemRef item : this.depositedItems) {
            NBTTagCompound itemTag = new NBTTagCompound();
            itemTag.setString(TAG_ITEM_ID, item.getId());
            itemTag.setInteger(TAG_ITEM_META, item.getMeta());
            items.appendTag(itemTag);
        }
        data.setTag(TAG_DEPOSITED_ITEMS, items);

        data.setBoolean(TAG_MIGRATED, this.migrated);
        data.setBoolean(TAG_GRANT_CLAIMED, this.grantClaimed);
    }
}
