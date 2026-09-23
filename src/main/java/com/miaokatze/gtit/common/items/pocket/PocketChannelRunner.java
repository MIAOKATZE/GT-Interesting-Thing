package com.miaokatze.gtit.common.items.pocket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 通道的一次批次（R9/R11/R12）：外层按<b>绑定序</b>遍历元件，内层遍历该元件的<b>全部可用通道</b>
 * （★R87-b 起：每枚元件每批对 {@code channelIdsOf} 的每条通道各服务一轮，不再是"每批单通道轮转"），
 * 每个「(元件, 通道) 对」在一批里至多服务一次。
 * <p>
 * 来自 E3 §4 / R87 的语义：
 * <ul>
 * <li><b>一次 = 一个（元件,通道）对的一个批次</b>（R9 窄口径），每秒穿几对由
 * {@code Config.pocketChannelPairsPerSecond} 决定；瞬时通道一次穿完全部（R12）。
 * ★R87-b：注入向不再走 {@link PocketRotationCursor}（全通道各跑一轮后"轮转选一"没有意义），
 * 游标只剩补满相在推进（见该类的 javadoc）；候选序 = {@code channelIdsOf} 的稳定列表序。</li>
 * <li><b>remainder 一律留在源槽</b>，本批不重试同一槽；★R87（饿死收窄）起只有
 * {@code LOST/NO_ACCESS}（元件此刻给不出）才 break 整串来源，{@code FULL/FILTER_REJECTED/NO_CHANNEL}
 * 记失败后<b>跳过该来源继续</b>——队首一格满/被分区拒收不再饿死排在后面的流体与源质来源。</li>
 * <li><b>批尾一次网络通知</b>：把所有带符号 delta 合并成一次 {@link PocketChannelOps#announce}，
 * 瞬时=一次调用一次 post、短效=每秒一次 post（R7）。双相批（R87-a）在
 * {@link #runDualPhase} 里合并后仍只 flush 一次。</li>
 * <li>★R87-a（反成环铁律）：与任一 ghost 声明<b>语义匹配</b>的来源不进注入相——声明格物品
 * （按槽排除，快照侧职责）、同名流体 tank、同 tag 源质（按语义载荷匹配，不按原始键串），
 * 见 {@link #withoutDeclarationMatches}。</li>
 * </ul>
 * 纯 JVM 件：只经 {@link PocketChannelOps} 触达世界，测试用桩件驱动。
 */
public final class PocketChannelRunner {

    /** ★R90 E2 L6：批序号（纯 debug 读数，不参与任何判定；三条批入口都经 {@link #flush} 各记一次）。 */
    private static final AtomicLong BATCH_SERIAL = new AtomicLong();

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
        /**
         * ★R90 E2（AUQ-①=B）：<b>源质载荷</b>因「无源质原生通道」被拒的次数（注入向 = 源质来源撞上
         * 非原生通道；抽取向 = 源质声明换不出探针，含旧档 {@code e:item:} 死声明）。
         * <p>
         * 它<b>计入 {@link #failures()}</b>而普通 {@link #noChannel} 仍不计（R85 A3 的钉子不动）：
         * 面板回执链（{@code NekoPocketPanel#performChannelRequest} 的"全失败退款"口径）只认
         * {@code failures() > 0} 才走 {@code receiptOfReport}——不单列这一项，「整批只有源质无处可上」
         * 会被吞成 {@code nothing_to_do}（静默），恰好违背 AUQ-①=B 的"拒收必须面呈"裁定。
         */
        public int essenceNoChannel;
        /** 最后一个回执码，供 GUI 直接取用。 */
        public PocketReceipt lastReceipt;
        /** 本批累计的带符号变化量（注入为正、抽取为负）。 */
        public final List<PocketChannelOps.Delta> deltas = new ArrayList<>();

        /** 本批是否有任何东西真的动了。 */
        public boolean didWork() {
            return transferred > 0;
        }

        /**
         * 需要向玩家解释的失败次数（拒收 + 满 + 落点满 + 无权限 + 失联 + ★R90 E2 的源质无原生通道拒收）。
         * 普通的无通道（{@link #noChannel}）仍<b>不</b>计入——它不停批也不是"哪条声明挡住了全队"
         * 那种失败（R85 A3 的既有钉子）；源质那一支单列进 {@link #essenceNoChannel}。
         */
        public int failures() {
            return filterRejected + full + targetFull + noAccess + lost + essenceNoChannel;
        }
    }

    /**
     * 注入方向：口袋/背包 → 已绑定元件。
     *
     * @param pairLimit 本批最多服务几个「(元件,通道) 对」；{@code Integer.MAX_VALUE} 即瞬时通道的一次穿完
     * @param filters   该口袋的 ghost 声明快照（★R87-a 反成环铁律的匹配面；{@code null}/空 = 无声明，全量推送）
     */
    public static Report runInjectBatch(PocketCellBindings bindings, PocketChannelOps ops, int pairLimit,
        PocketFilterConfig filters) {
        final Report report = injectPhase(bindings, ops, pairLimit, filters);
        flush(report, ops);
        return report;
    }

    /**
     * 抽取方向（R38 第 2 条的补满）：按 ghost 声明从元件抽进<b>本条声明自己那一格</b>。
     * <p>
     * ★R84：落点由"口袋其余非 ghost 真实栏"改为声明格本体（用户裁定"物品应该落到物品格、流体落到流体槽内"），
     * 与流体支"落点＝本 tank"同构。声明格因此不再是"永远空着的虚化占位"，而是能看见补到哪了的落点。
     * <p>
     * 抽取<b>不受分区拒收影响</b>（{@code MEInventoryHandler:106-118} 只查读权限，
     * GTIT 从不 {@code setIsExtractFilterActive(true)}），这是"配置补满能跨分区"的依据。
     *
     * @param amountPerFilter 单条声明一次最多要多少（GUI 侧决定；AE 侧本身无视 MC 堆叠上限）
     */
    public static Report runRefillBatch(PocketCellBindings bindings, PocketRotationCursor rotation,
        PocketFilterConfig filters, PocketChannelOps ops, int pairLimit, int amountPerFilter) {
        final Report report = refillPhase(bindings, rotation, filters, ops, pairLimit, amountPerFilter);
        flush(report, ops);
        return report;
    }

    /**
     * ★R87-a：双相批——先<b>注入相</b>（口袋 → 元件，无条件跑）后<b>补满相</b>（元件 → 口袋，按
     * {@code refillPhase} 标志跑），两份 Report 合并、批尾仍只 {@link #flush} 一次网络通知。
     * <p>
     * ★{@code refillPhase} 必须由调用方传<b>激活时算好的那一次</b>（{@code PocketChannelState#pullMode}，
     * R39b 的单点保留；R87-a 起它的语义 = "本次运行含补满相"），<b>不得</b>在本方法里现读
     * {@code filters.isEmpty()} 判相——面板中途增删 ghost 改的就是会话里挂的同一个对象，
     * 现读等于给"运行中换轨"开了门。两相各自享受 {@code pairLimit}（合并后的
     * {@code Report#pairsServed} 因此可到 2×pairLimit，「每秒几对」的限流口径按相生效）。
     *
     * @param filters     该口袋的 ghost 声明快照（注入相的反成环匹配面 + 补满相的需求面）
     * @param refillPhase true = 本批跑补满相（激活时算好的那一次）
     */
    static Report runDualPhase(PocketCellBindings bindings, PocketRotationCursor rotation, PocketFilterConfig filters,
        boolean refillPhase, PocketChannelOps ops, int pairLimit) {
        final Report report = new Report();
        mergeInto(report, injectPhase(bindings, ops, pairLimit, filters));
        if (refillPhase) {
            mergeInto(
                report,
                refillPhase(
                    bindings,
                    rotation,
                    filters,
                    ops,
                    pairLimit,
                    PocketConstants.REFILL_AMOUNT_PER_FILTER_UNBOUNDED));
        }
        flush(report, ops);
        return report;
    }

    /**
     * ★R87-a：两份相 Report 的合并（用测试钉住的语义）：
     * <ul>
     * <li>计数全部累加（含 {@code noChannel}）；{@code deltas} 直接拼接（批尾 {@link #flush} 前合并才有意义，
     * 双相批 {@link #runDualPhase} 正是这么用的；单相入口 flush 后 deltas 已清空，合并时自然是空）。</li>
     * <li>{@code lastReceipt}：<b>失败码优先于成功码</b>（玩家必须先看到失败面，不能让"无事可做"的 OK
     * 盖掉另一相的元件满）；两相同为成功或同为失败时取<b>后跑的一相</b>（补满相）；一侧为 null 让位给非 null。</li>
     * </ul>
     */
    static void mergeInto(Report target, Report addition) {
        if (addition == null) {
            return;
        }
        target.pairsServed += addition.pairsServed;
        target.transferred += addition.transferred;
        target.filterRejected += addition.filterRejected;
        target.full += addition.full;
        target.targetFull += addition.targetFull;
        target.noAccess += addition.noAccess;
        target.lost += addition.lost;
        target.noChannel += addition.noChannel;
        target.essenceNoChannel += addition.essenceNoChannel;
        target.deltas.addAll(addition.deltas);
        if (addition.lastReceipt != null && (target.lastReceipt == null || isFailureReceipt(addition.lastReceipt)
            || !isFailureReceipt(target.lastReceipt))) {
            target.lastReceipt = addition.lastReceipt;
        }
    }

    /** 合并语义里的"失败码"（与 {@link Report#failures()} 同一集合；NO_CHANNEL/OK/PARTIAL 不算）。 */
    private static boolean isFailureReceipt(PocketReceipt receipt) {
        return receipt == PocketReceipt.FILTER_REJECTED || receipt == PocketReceipt.FULL
            || receipt == PocketReceipt.TARGET_FULL
            || receipt == PocketReceipt.NO_ACCESS
            || receipt == PocketReceipt.LOST;
    }

    /** 注入相本体（不 flush；单相入口与双相批共用，见 {@link #runInjectBatch}/{@link #runDualPhase}）。 */
    private static Report injectPhase(PocketCellBindings bindings, PocketChannelOps ops, int pairLimit,
        PocketFilterConfig filters) {
        final Report report = new Report();
        if (bindings == null || bindings.isEmpty() || pairLimit <= 0) {
            return report;
        }
        List<PocketChannelOps.SourceSlot> sources = ops.snapshotSources();
        if (sources == null || sources.isEmpty()) {
            return report;
        }
        // ★R87-a 反成环铁律：与任一 ghost 声明语义匹配的来源（同名流体 tank / 同 tag 源质）不进注入——
        // 否则"推出去一拍、按声明抽回来一拍"就是永动环。物品声明格在快照侧按槽排除（职责不在这里）。
        sources = withoutDeclarationMatches(filters, sources);
        if (sources.isEmpty()) {
            return report;
        }
        // ★R84：服务面只吃绑定序前 ALLOWED_BOUND_CELLS 枚 ⇒ 旧档里残留的第二枚起是"仍显示、不再搬运"
        // 的惰性条目（刻意不销毁玩家数据，也不让它继续产生"能绑多枚却在轮转"的错觉）。
        final List<String> cells = bindings.cells();
        final int serveCells = Math.min(cells.size(), PocketConstants.ALLOWED_BOUND_CELLS);
        for (int cellIndex = 0; cellIndex < serveCells; cellIndex++) {
            final String diskuuid = cells.get(cellIndex);
            if (report.pairsServed >= pairLimit) {
                break;
            }
            if (ops.isCellLost(diskuuid)) {
                report.lost++;
                report.lastReceipt = PocketReceipt.LOST;
                continue;
            }
            final List<String> channels = ops.channelIdsOf(diskuuid);
            if (channels == null || channels.isEmpty()) {
                report.noChannel++;
                report.lastReceipt = PocketReceipt.NO_CHANNEL;
                continue;
            }
            // ★R87-b：每枚元件对全部可用通道各服务一轮（顺序 = 列表的稳定序），不再"每批单通道轮转"——
            // 否则瞬时通道一次按键只推一个通道，流体/源质要等下一次按键才轮得到（"按一次没反应"的观感）。
            for (int i = 0; i < channels.size(); i++) {
                if (report.pairsServed >= pairLimit) {
                    break;
                }
                final String typeId = channels.get(i);
                if (typeId == null || typeId.isEmpty()) {
                    continue;
                }
                report.pairsServed++;
                injectIntoPair(sources, diskuuid, typeId, ops, report);
            }
        }
        return report;
    }

    /** 补满相本体（不 flush；单相入口与双相批共用，见 {@link #runRefillBatch}/{@link #runDualPhase}）。 */
    private static Report refillPhase(PocketCellBindings bindings, PocketRotationCursor rotation,
        PocketFilterConfig filters, PocketChannelOps ops, int pairLimit, int amountPerFilter) {
        final Report report = new Report();
        if (bindings == null || bindings.isEmpty() || filters == null || filters.isEmpty() || pairLimit <= 0) {
            return report;
        }
        final List<String> cells = bindings.cells();
        // ★R84：与注入侧同一条服务面口径（见 injectPhase），只吃绑定序前 ALLOWED_BOUND_CELLS 枚
        final int serveCells = Math.min(cells.size(), PocketConstants.ALLOWED_BOUND_CELLS);
        for (int cellIndex = 0; cellIndex < serveCells; cellIndex++) {
            final String diskuuid = cells.get(cellIndex);
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
                // ★★<b>R91-⑤：补货行为只认 BIND</b>——本格若被 alt+左 挂上<b>记忆 L</b>，它就是一条
                // 纯过滤规则（"这格只能放那一种东西"），★<b>不产生任何 AE 拉取行为</b>（用户原话）。
                // 判据单源在 PocketFilterConfig#pullsFromCell（NONE=隐式 BIND 与 BIND 都拉 ⇒ 需求 4 的
                // 既有拖拽建档语义一字不回退；只有 MEMORY 不拉）。
                // ★写在本方法（且只在这一处）的理由：这里同时拿得到 kind 与 slotIndex ——
                // 换到 PocketAeChannelOps#extract 里就要把两张表都传下去，写进 Report 统计又是第二处真相。
                if (!filters.pullsFromCell(filter.kind(), filter.slotIndex())) {
                    continue;
                }
                final PocketChannelOps.Outcome outcome = ops.extract(filter, diskuuid, amountPerFilter);
                if (outcome == null) {
                    continue;
                }
                report.lastReceipt = outcome.receipt;
                if (outcome.moved > 0) {
                    report.transferred += outcome.moved;
                    report.deltas.add(new PocketChannelOps.Delta(diskuuid, typeId, filter.key(), -outcome.moved));
                }
                if (outcome.receipt == PocketReceipt.NO_CHANNEL) {
                    // ★R85 A3：这条现在不停批了，但必须留痕——R84 改判后 Report.noChannel 全仓零读者，
                    // "这只元件没有该通道"既不计也不说，玩家只看到"通道开了却没动静"。
                    report.noChannel++;
                    if (filter instanceof PocketFilterConfig.EssenceFilter) {
                        // ★R90 E2（下传对称）：源质声明换不出探针（含旧档 e:item: 死声明）⇒ 同一档
                        // 玩家可解释失败，进 failures() 走面板回执（非静默），见注入侧同处注释。
                        report.essenceNoChannel++;
                    }
                }
                if (outcome.receipt.stopsExtractBatch()) {
                    accountFailure(report, outcome.receipt);
                    // ★R85 A1：抽取侧只有"元件此刻给不出"才打住；落点满/无通道是这一条声明自己的事，
                    // 其余声明各有自己的落点（物品=各自的声明格、流体=各自的 tank），必须继续跑完。
                    break;
                }
            }
        }
        return report;
    }

    /**
     * ★R87-a 反成环铁律的匹配面：剔除「与任一 ghost 声明<b>语义</b>匹配」的来源。
     * <p>
     * 匹配按<b>语义载荷</b>（流体名 / aspect tag 字符串），<b>绝不按原始键串</b>——声明侧与来源侧的键
     * typeId 段可以不同（声明 {@code e:item:<tag>}（第三方通道回落），来源 {@code e::<tag>}（口袋源质
     * 不属于任何通道）），逐字比键会漏剔。物品来源不在这里匹配：ghost 声明格在
     * {@code snapshotSources} 里按槽排除（现状保持，R87-a 裁定）。
     * {@code filters} 为 null/空（纯推送）照旧全量。
     */
    private static List<PocketChannelOps.SourceSlot> withoutDeclarationMatches(PocketFilterConfig filters,
        List<PocketChannelOps.SourceSlot> sources) {
        if (filters == null || filters.isEmpty()) {
            return sources;
        }
        List<PocketChannelOps.SourceSlot> kept = null;
        for (int i = 0; i < sources.size(); i++) {
            final PocketChannelOps.SourceSlot source = sources.get(i);
            if (source != null && matchesDeclaration(filters, source)) {
                if (kept == null) {
                    kept = new ArrayList<>(sources.size());
                    for (int j = 0; j < i; j++) {
                        kept.add(sources.get(j));
                    }
                }
                continue;
            }
            if (kept != null) {
                kept.add(source);
            }
        }
        return kept == null ? sources : kept;
    }

    /** 单条来源是否与任一声明语义撞车（流体比流体名、源质比 tag、物品不比——见上面那段）。 */
    private static boolean matchesDeclaration(PocketFilterConfig filters, PocketChannelOps.SourceSlot source) {
        if (source.kind == PocketChannelOps.SourceKind.FLUID) {
            final PocketFilterConfig.Filter parsed = PocketFilterConfig.parseKey(source.contentKey);
            if (parsed instanceof PocketFilterConfig.FluidFilter fluid) {
                for (PocketFilterConfig.Filter filter : filters.filters()) {
                    if (filter instanceof PocketFilterConfig.FluidFilter declared
                        && declared.fluidName.equals(fluid.fluidName)) {
                        return true;
                    }
                }
            }
            return false;
        }
        if (source.kind == PocketChannelOps.SourceKind.ESSENCE) {
            final PocketFilterConfig.Filter parsed = PocketFilterConfig.parseKey(source.contentKey);
            if (parsed instanceof PocketFilterConfig.EssenceFilter essence) {
                for (PocketFilterConfig.Filter filter : filters.filters()) {
                    if (filter instanceof PocketFilterConfig.EssenceFilter declared
                        && declared.tag.equals(essence.tag)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
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
            if (outcome.receipt == PocketReceipt.NO_CHANNEL) {
                // ★R87（饿死收窄）：这条通道收不了这个来源——是"这一格对这一通道"的属性，
                // 计数留痕（与抽取侧 R85 A3 同一条纪律）后跳过该来源继续，不再停批。
                report.noChannel++;
                if (source.kind == PocketChannelOps.SourceKind.ESSENCE) {
                    // ★R90 E2（AUQ-①=B）：源质上传只认源质原生通道——这一条 NO_CHANNEL 是
                    // 「盘内源质无处可上」的玩家可解释失败，单列进 failures()，让面板既有回执链
                    // （receiptOfReport → gtit.pocket.receipt.no_channel）在"整批只有这一种失败"时
                    // 也走得进映射，不再被"全失败退款"口径吞成 nothing_to_do。
                    report.essenceNoChannel++;
                }
                continue;
            }
            if (outcome.receipt.stopsInjectBatch()) {
                accountFailure(report, outcome.receipt);
                // ★R87：只有 LOST/NO_ACCESS（元件此刻给不出）才停批顺延下一轮；
                // remainder 纪律不变——余量留在源槽，本批绝不重试同一槽。
                break;
            }
            if (outcome.receipt != PocketReceipt.OK && outcome.receipt != PocketReceipt.PARTIAL) {
                // ★R87（饿死收窄）：FULL/FILTER_REJECTED（及理论上的 TARGET_FULL）是"这一格对这一通道"
                // 自己的属性——记失败后跳过该来源继续，队首一格满/被拒收不再截断后面的流体与源质来源。
                accountFailure(report, outcome.receipt);
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
     * delta 为空则完全不 post，避免空批次白刷全网。双相批（R87-a）在两相合并后的这份 Report 上
     * 只调一次，仍是"批尾一次通知"。
     */
    private static void flush(Report report, PocketChannelOps ops) {
        // ★R90 E2 L6（debug）：每批一条服务序号读数——零搬运的批也记（flush 的空批早退之前），
        // 与"这批到底有没有跑"的服务端痕迹对得上；debug 级别默认不打，不构成刷屏面。
        GTInterestingThing.LOG.debug(
            "[PocketR89] L6 通道批 #{}：服务 {} 对，搬运 {} 点",
            BATCH_SERIAL.incrementAndGet(),
            report.pairsServed,
            report.transferred);
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
