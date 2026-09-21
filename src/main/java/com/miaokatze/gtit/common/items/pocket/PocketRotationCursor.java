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
 * 纯 JVM 件：只有字符串与列表。
 */
public final class PocketRotationCursor {

    /** diskuuid 字符串 → 上次服务过的 typeId 字符串。 */
    private final Map<String, String> lastServed = new LinkedHashMap<>();

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

    /** 该元件上次实际服务过的 typeId（诊断与 GUI 回显用）。 */
    public String lastServedOf(String diskuuid) {
        return diskuuid == null ? null : lastServed.get(diskuuid);
    }

    /** 元件解绑后清掉它的游标；不影响其他元件。 */
    public void forget(String diskuuid) {
        if (diskuuid != null) {
            lastServed.remove(diskuuid);
        }
    }

    public void reset() {
        lastServed.clear();
    }

    /** 被跟踪的元件数。 */
    public int trackedCells() {
        return lastServed.size();
    }

    /** 只在条目过多时整体清空（纯内存回收，不参与序号推进语义）。 */
    private void trimIfNeeded() {
        if (lastServed.size() > PocketConstants.MAX_ROTATION_ENTRIES) {
            lastServed.clear();
        }
    }
}
