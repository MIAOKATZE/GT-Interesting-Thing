package com.miaokatze.gtit.common.items.pocket;

import java.util.ArrayList;
import java.util.List;

/**
 * 通道的一次批次（R9/R11/R12）：外层按<b>绑定序</b>遍历元件，内层按 {@code typeId} <b>轮转</b>选通道，
 * 每个「(元件, 通道) 对」在一批里只服务一次。
 * <p>
 * 三条来自 E3 §4 的语义：
 * <ul>
 * <li><b>一次 = 一个（元件,通道）对的一个批次</b>（R9 窄口径，与 ME-IO 端口的轮转口径一致），
 * 每秒穿几对由 {@code Config.pocketChannelPairsPerSecond} 决定；瞬时通道一次穿完全部（R12）。</li>
 * <li><b>remainder 一律留在源槽</b>，本批不再对该槽重试，且首个非 OK 回执即 {@code break}
 * 顺延下一轮（照 {@code NekoMeTransferQueue.tick():111-126} 的形态）。</li>
 * <li><b>批尾一次网络通知</b>：把所有带符号 delta 合并成一次 {@link PocketChannelOps#announce}，
 * 瞬时=一次调用一次 post、短效=每秒一次 post（R7）。</li>
 * </ul>
 * 纯 JVM 件：只经 {@link PocketChannelOps} 触达世界，测试用桩件驱动。
 */
public final class PocketChannelRunner {

    private PocketChannelRunner() {}

    /** 一批的统计与待发 delta（GUI 回执码与网络通知都读它）。 */
    public static final class Report {

        /** 本批实际服务过的「(元件,通道) 对」数。 */
        public int pairsServed;
        /** 进入元件的点数/个数总和。 */
        public int transferred;
        /** 被分区 WHITELIST/BLACKLIST 拒收的次数（R10，必须与"满"分开呈现）。 */
        public int filterRejected;
        /** 元件已满的次数。 */
        public int full;
        /** 落点（玩家背包/口袋真实栏）已满，抽取方向失败。 */
        public int targetFull;
        /** 无写权限的次数。 */
        public int noAccess;
        /** 元件失联的次数。 */
        public int lost;
        /** 无可用电通道的次数。 */
        public int noChannel;
        /** 最后一个回执码，供 GUI 直接取用。 */
        public PocketReceipt lastReceipt;
        /** 本批累计的带符号变化量（注入为正、抽取为负）。 */
        public final List<PocketChannelOps.Delta> deltas = new ArrayList<>();

        /** 本批是否有任何东西真的动了。 */
        public boolean didWork() {
            return transferred > 0;
        }

        /** 需要向玩家解释的失败次数（拒收 + 满 + 落点满 + 无权限 + 失联）。 */
        public int failures() {
            return filterRejected + full + targetFull + noAccess + lost;
        }
    }

    /**
     * 注入方向：口袋/背包 → 已绑定元件。
     *
     * @param pairLimit 本批最多服务几个「(元件,通道) 对」；{@code Integer.MAX_VALUE} 即瞬时通道的一次穿完
     */
    public static Report runInjectBatch(PocketCellBindings bindings, PocketRotationCursor rotation,
        PocketChannelOps ops, int pairLimit) {
        final Report report = new Report();
        if (bindings == null || bindings.isEmpty() || pairLimit <= 0) {
            return report;
        }
        final List<PocketChannelOps.SourceSlot> sources = ops.snapshotSources();
        if (sources == null || sources.isEmpty()) {
            return report;
        }
        for (String diskuuid : bindings.cells()) {
            if (report.pairsServed >= pairLimit) {
                break;
            }
            if (ops.isCellLost(diskuuid)) {
                report.lost++;
                report.lastReceipt = PocketReceipt.LOST;
                continue;
            }
            final List<String> channels = ops.channelIdsOf(diskuuid);
            final String typeId = rotation == null ? (channels == null || channels.isEmpty() ? null : channels.get(0))
                : rotation.next(diskuuid, channels);
            if (typeId == null || typeId.isEmpty()) {
                report.noChannel++;
                report.lastReceipt = PocketReceipt.NO_CHANNEL;
                continue;
            }
            report.pairsServed++;
            injectIntoPair(sources, diskuuid, typeId, ops, report);
        }
        flush(report, ops);
        return report;
    }

    /**
     * 抽取方向（R38 第 2 条的补满）：按 ghost 声明从元件抽进口袋其余非 ghost 真实栏。
     * <p>
     * 抽取<b>不受分区拒收影响</b>（{@code MEInventoryHandler:106-118} 只查读权限，
     * GTIT 从不 {@code setIsExtractFilterActive(true)}），这是"配置补满能跨分区"的依据。
     *
     * @param amountPerFilter 单条声明一次最多要多少（GUI 侧决定；AE 侧本身无视 MC 堆叠上限）
     */
    public static Report runRefillBatch(PocketCellBindings bindings, PocketRotationCursor rotation,
        PocketFilterConfig filters, PocketChannelOps ops, int pairLimit, int amountPerFilter) {
        final Report report = new Report();
        if (bindings == null || bindings.isEmpty() || filters == null || filters.isEmpty() || pairLimit <= 0) {
            return report;
        }
        for (String diskuuid : bindings.cells()) {
            if (report.pairsServed >= pairLimit) {
                break;
            }
            if (ops.isCellLost(diskuuid)) {
                report.lost++;
                report.lastReceipt = PocketReceipt.LOST;
                continue;
            }
            final List<String> channels = ops.channelIdsOf(diskuuid);
            final String typeId = rotation == null ? (channels == null || channels.isEmpty() ? null : channels.get(0))
                : rotation.next(diskuuid, channels);
            if (typeId == null || typeId.isEmpty()) {
                report.noChannel++;
                report.lastReceipt = PocketReceipt.NO_CHANNEL;
                continue;
            }
            report.pairsServed++;
            for (PocketFilterConfig.Filter filter : filters.filters()) {
                final PocketChannelOps.Outcome outcome = ops.extract(filter, diskuuid, amountPerFilter);
                if (outcome == null) {
                    continue;
                }
                report.lastReceipt = outcome.receipt;
                if (outcome.moved > 0) {
                    report.transferred += outcome.moved;
                    report.deltas.add(new PocketChannelOps.Delta(diskuuid, typeId, filter.key(), -outcome.moved));
                }
                if (outcome.receipt.stopsBatch()) {
                    accountFailure(report, outcome.receipt);
                    // 余量留元件侧，本对就此打住，顺延下一轮
                    break;
                }
            }
        }
        flush(report, ops);
        return report;
    }

    private static void injectIntoPair(List<PocketChannelOps.SourceSlot> sources, String diskuuid, String typeId,
        PocketChannelOps ops, Report report) {
        for (PocketChannelOps.SourceSlot source : sources) {
            if (source == null || source.count <= 0 || source.contentKey.isEmpty()) {
                continue;
            }
            final PocketChannelOps.Outcome outcome = ops.inject(source, diskuuid, typeId);
            if (outcome == null) {
                continue;
            }
            report.lastReceipt = outcome.receipt;
            if (outcome.moved > 0) {
                report.transferred += outcome.moved;
                report.deltas.add(new PocketChannelOps.Delta(diskuuid, typeId, source.contentKey, outcome.moved));
            }
            if (outcome.receipt.stopsBatch()) {
                accountFailure(report, outcome.receipt);
                // remainder 留在源槽（不落地上、不进队列），本批不再对该槽重试
                break;
            }
        }
    }

    private static void accountFailure(Report report, PocketReceipt receipt) {
        if (receipt == PocketReceipt.FILTER_REJECTED) {
            report.filterRejected++;
        } else if (receipt == PocketReceipt.FULL) {
            report.full++;
        } else if (receipt == PocketReceipt.NO_ACCESS) {
            report.noAccess++;
        } else if (receipt == PocketReceipt.TARGET_FULL) {
            report.targetFull++;
        } else if (receipt == PocketReceipt.LOST) {
            report.lost++;
        }
    }

    /**
     * 批尾一次性通知网络视图（R7）。
     * <p>
     * 同一条内容多次出现时先在此合并成一个带符号量，使「瞬时通道一次调用内穿完 + 一次 post」成立；
     * delta 为空则完全不 post，避免空批次白刷全网。
     */
    private static void flush(Report report, PocketChannelOps ops) {
        if (report.deltas.isEmpty()) {
            return;
        }
        ops.announce(merge(report.deltas));
        report.deltas.clear();
    }

    private static List<PocketChannelOps.Delta> merge(List<PocketChannelOps.Delta> deltas) {
        final List<PocketChannelOps.Delta> merged = new ArrayList<>(deltas.size());
        for (PocketChannelOps.Delta delta : deltas) {
            int at = -1;
            for (int i = 0; i < merged.size(); i++) {
                final PocketChannelOps.Delta other = merged.get(i);
                if (sameTarget(other, delta)) {
                    at = i;
                    break;
                }
            }
            if (at < 0) {
                merged.add(new PocketChannelOps.Delta(delta.diskuuid, delta.typeId, delta.contentKey, delta.amount));
            } else {
                final PocketChannelOps.Delta other = merged.remove(at);
                final long sum = other.amount + delta.amount;
                if (sum != 0L) {
                    merged.add(new PocketChannelOps.Delta(other.diskuuid, other.typeId, other.contentKey, sum));
                }
            }
        }
        return merged;
    }

    private static boolean sameTarget(PocketChannelOps.Delta left, PocketChannelOps.Delta right) {
        return left.diskuuid.equals(right.diskuuid) && left.typeId.equals(right.typeId)
            && left.contentKey.equals(right.contentKey);
    }
}
