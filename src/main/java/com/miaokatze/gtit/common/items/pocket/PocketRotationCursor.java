package com.miaokatze.gtit.common.items.pocket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多元件多通道的轮转游标（R9）。
 * <p>
 * <b>键必须是 {@code diskuuid}（元件 diskuuid）字符串</b>：
 * <ul>
 * <li>元件的 handler 每次取用都新建实例（{@code ItemNekoInfinityStorageUnit.java:35-55}、
 * {@code InfinityCellHandler.java:55-67}），用 {@code ItemStack} 实例或 {@code IdentityHashMap}
 * 作键会复刻 v1.8.26 刚修掉的「序号恒归 0」——{@code MixinTileIOPort.java:56-58,197-206} 的
 * {@code IdentityHashMap} 之所以成立，是因为 IO 端口全程持有同一实例，口袋不满足这个前提。</li>
 * <li>也<b>绝不可用 {@code AEStackTypeRegistry} 的索引</b>（其底层 {@code HashMap}，
 * {@code AEStackTypeRegistry.java:26,70}），索引随整合包 mod 增减漂移。</li>
 * </ul>
 * <p>
 * 因此这里存的不是"序号"而是<b>该元件上次服务过的 typeId 字符串</b>：下一次从候选列表里它的后一个开始，
 * 候选列表由调用方现场给出（{@code InfinityStackTypes.allSupportedTypes()} 的 typeId 序）。
 * 序号<b>不因通道激活而重置</b>，三通道对同一元件公平轮转；多枚元件再由绑定序做外层轮转。
 * <p>
 * ★R87-b 起<b>补满相</b>走 {@link #next}（{@code runRefillBatch} 里每次取 typeId 记账）。
 * ★R97 S2 起<b>注入相有自己的游标</b>（{@link #nextInjectStart}）：R87-b 那次"注入向改为全通道各服务一轮、
 * 不再轮转"在 {@code pairLimit=1}（配置默认值）时退化成「ITEM 通道恒吃首对、流体/源质通道永久饿死」
 * （取证 {@code plan/_taskpack/R97-inv-channel.md} §3）——注入相现在仍是全通道各服务一轮（R87-b 的
 * 该语义保留），但<b>起始序号每批 +1</b>（mod 通道数），有源通道轮流吃到首对。两条游标
 * <b>同构但独立推进</b>（两张表、两个记账口），互不读写；注入相游标<b>只在内存延续、不进 NBT</b>
 * ——重启丢的只是公平起始位，无害（避免 NBT 格式变更）。
 * <p>
 * 纯 JVM 件：只有字符串与列表。
 */
public final class PocketRotationCursor {

    /** diskuuid 字符串 → 补满相上次服务过的 typeId 字符串。 */
    private final Map<String, String> lastServed = new LinkedHashMap<>();
    /**
     * ★R97 S2：diskuuid 字符串 → 注入相上一批的<b>起始</b>typeId 字符串（独立于 {@link #lastServed}）。
     * 存"起始位"而不是"最后一个服务位"：注入相每批对全部可用通道各服务一轮，有意义的轮转量只有起点。
     */
    private final Map<String, String> injectStartedAt = new LinkedHashMap<>();

    /**
     * 取该元件本轮应服务的 typeId，并把它记为"上次服务过"。
     *
     * @param diskuuid         元件身份（键，字符串）
     * @param typeIdCandidates 该元件当前可用的 typeId 列表，顺序即轮转序
     * @return 选中的 typeId；候选为空时返回 null
     */
    public String next(String diskuuid, List<String> typeIdCandidates) {
        if (typeIdCandidates == null || typeIdCandidates.isEmpty()) {
            return null;
        }
        if (diskuuid == null || diskuuid.isEmpty()) {
            return typeIdCandidates.get(0);
        }
        this.trimIfNeeded();
        final int size = typeIdCandidates.size();
        final String previous = lastServed.get(diskuuid);
        int index = 0;
        if (previous != null) {
            final int at = typeIdCandidates.indexOf(previous);
            // 上次服务过的通道已经不在候选里（mod 掉线/元件换型）⇒ 从 0 重新开始，不做隐式对齐
            if (at >= 0) {
                index = (at + 1) % size;
            }
        }
        final String chosen = typeIdCandidates.get(index);
        lastServed.put(diskuuid, chosen);
        return chosen;
    }

    /**
     * ★R97 S2：注入相本批的<b>起始通道序号</b>（记帐后每批自动 +1，mod 通道数）。
     * <p>
     * 与 {@link #next} 同构：存"本批起始的 typeId"，下一次从它的后一个开始；上一批起始通道已不在
     * 候选里（mod 掉线/元件换型）⇒ 从 0 重新开始。<b>与补满相游标互不相干</b>——两相各自推进，
     * 互相的推进也不影响对方的下一个值。只应被注入相在<b>每元件每批恰好一次</b>地调用
     * （每批起始位 +1 正是公平性的来源；BURST 一次穿完全部通道，起始位对结果无影响，照常记帐）。
     *
     * @return 本批注入相该元件的通道起始序号；候选为空返回 -1（调用方据此走无通道支）
     */
    public int nextInjectStart(String diskuuid, List<String> typeIdCandidates) {
        if (typeIdCandidates == null || typeIdCandidates.isEmpty()) {
            return -1;
        }
        if (diskuuid == null || diskuuid.isEmpty()) {
            return 0;
        }
        this.trimIfNeeded();
        final int size = typeIdCandidates.size();
        final String previous = injectStartedAt.get(diskuuid);
        int index = 0;
        if (previous != null) {
            final int at = typeIdCandidates.indexOf(previous);
            if (at >= 0) {
                index = (at + 1) % size;
            }
        }
        injectStartedAt.put(diskuuid, typeIdCandidates.get(index));
        return index;
    }

    /** 该元件上次实际服务过的 typeId（诊断与 GUI 回显用）。 */
    public String lastServedOf(String diskuuid) {
        return diskuuid == null ? null : lastServed.get(diskuuid);
    }

    /** ★R97 S2：该元件注入相上一批的起始 typeId（诊断与测试用；补满相游标读 {@link #lastServedOf}）。 */
    public String lastInjectStartOf(String diskuuid) {
        return diskuuid == null ? null : injectStartedAt.get(diskuuid);
    }

    /** 元件解绑后清掉它的两条游标；不影响其他元件。 */
    public void forget(String diskuuid) {
        if (diskuuid != null) {
            lastServed.remove(diskuuid);
            injectStartedAt.remove(diskuuid);
        }
    }

    public void reset() {
        lastServed.clear();
        injectStartedAt.clear();
    }

    /** 被跟踪的元件数。 */
    public int trackedCells() {
        return lastServed.size();
    }

    /** 只在条目过多时整体清空（纯内存回收，不参与序号推进语义；★R97 S2 起两条游标一起回收）。 */
    private void trimIfNeeded() {
        if (lastServed.size() > PocketConstants.MAX_ROTATION_ENTRIES
            || injectStartedAt.size() > PocketConstants.MAX_ROTATION_ENTRIES) {
            lastServed.clear();
            injectStartedAt.clear();
        }
    }
}
