package com.miaokatze.gtit.reincarnation.core;

import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle.CycleState;

/**
 * 丢更新覆写防御（A4 纯逻辑判定）：判定"陈旧内存模型保存"是否会覆写掉磁盘上
 * 已推进的许可状态（EXECUTED 只读锁 + 投胎信箱 + 永久指纹）。
 * <p>
 * 背景（实证 bug 形态）：GUI 确认轮回经权威链路（Handler.confirmReincarnation →
 * saveSplit 写 EXECUTED+信箱+指纹）推进落盘后，容器仍持确认前的陈旧快照；
 * 关窗时对陈旧模型做全量覆写保存会抹掉信箱与指纹。防御方向：保存前对比磁盘
 * 许可，磁盘已 EXECUTED 而内存漂移为非 EXECUTED → 先按权威状态重载内存模型，
 * 再走保存（"先重载再保存"）。
 * <p>
 * 判定只依赖 {@link ReincarnationCycle} 纯 JVM 模型、零 MC 类型引用（本类与
 * reincarnation/core 同红线：零 client 类型、零 {@code @SideOnly}），可被零依赖
 * 测试套件（{@code ReincarnationStoreTest}）直接覆盖。
 */
public final class ReincarnationSaveGuard {

    private ReincarnationSaveGuard() {}

    /**
     * 保存前防御判定：磁盘许可已是 EXECUTED 而内存模型漂移为非 EXECUTED
     * → 必须先重载（返回 true），调用方按权威状态重载后再保存。
     * <p>
     * 反向漂移也可能是正常领取：GUI 打开期间 Handler 已清空信箱。仅当磁盘保留
     * 内存的全部非空永久指纹时，将其视作已领取并重载，避免关窗复活信箱。
     * 文件缺失/损坏的最后仁慈视图没有指纹，不据此丢弃内存中仍待领取的信箱。
     * 任一入参为 null 不触发（按正常保存处理，与调用方既有 null 防御口径一致）。
     *
     * @param memoryModel 容器/会话持有的内存模型（可能陈旧）
     * @param diskLicense 保存前从仓库新读的磁盘许可
     * @return true = 磁盘许可已确认或已领取且内存漂移，须先重载再保存
     */
    public static boolean needsReloadBeforeSave(ReincarnationCycle memoryModel, ReincarnationCycle diskLicense) {
        if (memoryModel == null || diskLicense == null) {
            return false;
        }
        if (diskLicense.getCycleState() == CycleState.EXECUTED) {
            return memoryModel.getCycleState() != CycleState.EXECUTED;
        }
        return memoryModel.getCycleState() == CycleState.EXECUTED
            && !memoryModel.getExecutedFingerprints()
                .isEmpty()
            && diskLicense.getExecutedFingerprints()
                .containsAll(memoryModel.getExecutedFingerprints());
    }
}
