package com.miaokatze.gtit.reincarnation.storage;

import net.minecraft.server.MinecraftServer;

import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;

/**
 * 旧全局 v1 格式 → 新「全局许可 + 每存档进度」拆分的<b>一次性 consume-once 迁移</b>
 * （v1.9.x D1 切片）。
 * <p>
 * 背景：v1 全局文件把「许可字段（指纹/信箱）」与「每存档进度（外壳/列/行/寄存快照）」
 * 混在一个 {@code config/gtit/reincarnation.dat} 里，换存档即串档。D1 将进度迁入
 * {@link ReincarnationWorldData}；本类把旧 v1 字段灌入当前存档的 WorldData 后，
 * 把全局文件改写为 v2（只留许可字段），并置 WorldData 迁移标记。
 * <p>
 * <b>consume-once 语义</b>：
 * <ul>
 * <li>标记在 {@link ReincarnationWorldData}（每存档隔离）：已置位不重复合并，但重试消费源文件；</li>
 * <li>全局文件无 v1 载荷（已是 v2 / 从未存在 / 损坏走最后仁慈 / UUID 不匹配）→
 * 仅置标记，不做任何改写（损坏文件保持不动，与 load 的最后仁慈口径一致）；</li>
 * <li>命中 v1 → 进度字段灌入当档 WorldData（按 UUID 隔离），全局改写 v2
 * （指纹保留；EXECUTED 待领取转入投胎信箱；DEPOSITED 寄存快照转入当档 WorldData）。</li>
 * </ul>
 * <p>
 * <b>触发点</b>：服务端 GUI 打开与 Handler 合并加载；登录发放也可能改写全局许可，
 * 因而必须先保护 v1 中的每存档进度。
 * <p>
 * <b>侧与线程</b>：服务端专用；纯 Java 依赖（store/WorldData/模型），零 client 引用。
 */
public final class ReincarnationMigration {

    private ReincarnationMigration() {}

    /**
     * 一次性迁移（幂等；重复调用安全）。
     *
     * @param store 全局许可仓库（v1/v2 双格式读写）
     * @param data  当前存档的周目进度载体（迁移标记所在）
     * @param uuid  玩家 UUID（迁移按 UUID 隔离）
     */
    public static void migrateOnce(ReincarnationStore store, ReincarnationWorldData data, String uuid) {
        migrateOnce(
            store,
            data,
            uuid,
            MinecraftServer.getServer()
                .getEntityWorld());
    }

    /**
     * Persist world identity -> reserve the complete source -> checkpoint receiver
     * receipt -> consume source. A receipt alone must never leave v1 importable
     * by another world; a reservation alone must remain retryable by its owner.
     * The UUID lives inside WorldData so renaming the world directory is safe.
     */
    public static void migrateOnce(ReincarnationStore store, ReincarnationWorldData data, String uuid,
        net.minecraft.world.World overworld) {
        if (store == null || data == null) {
            return;
        }
        ReincarnationStore.LegacyRecord legacy = store.readLegacyV1(uuid);
        if (legacy == null) {
            if (!data.isMigrated()) {
                data.setMigrated(true);
                if (overworld != null) {
                    data.saveImmediately(overworld);
                }
            }
            return;
        }
        if (overworld == null) {
            return; // No durable receiver: never reserve or consume the source.
        }
        if (data.isMigrated()) {
            if (data.getMigrationId()
                .isEmpty()) {
                // Older builds left no ownership evidence. Do not guess which world's
                // unrelated Migrated flag authorizes destruction of a still-v1 source.
                throw new IllegalStateException("Legacy migration receipt lacks a world identity");
            }
            // Also retries a failed checkpoint in the same session before consuming.
            data.saveImmediately(overworld);
            store.consumeLegacyV1(uuid, data.getMigrationId());
            return;
        }
        String worldId = data.ensureMigrationId();
        data.saveImmediately(overworld); // identity must survive a crash before source reservation
        legacy = store.reserveLegacyV1(uuid, worldId);
        if (legacy == null) {
            return;
        }
        data.setHullProgress(legacy.hullProgress);
        data.setUnlockedColumns(legacy.unlockedColumns);
        data.setUnlockedRows(legacy.unlockedRows);
        data.setDepositedItems(legacy.depositedItems);
        data.setMigrated(true);
        data.saveImmediately(overworld); // throwing checkpoint; source is still intact on failure
        store.consumeLegacyV1(uuid, worldId);
    }
}
