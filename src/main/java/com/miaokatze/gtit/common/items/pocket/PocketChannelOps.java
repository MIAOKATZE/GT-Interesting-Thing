package com.miaokatze.gtit.common.items.pocket;

import java.util.Collections;
import java.util.List;

/**
 * 口袋通道与外部世界之间<b>唯一</b>的窄接口（照 {@code NekoMeTransferQueue.java:24-35} 的
 * {@code UplinkOps} 形态：把「解析元件 / 注入 / 抽取 / 是否失联 / 当前墙钟毫秒 / 当前 tick」
 * 六个触点抽出去）。
 * <p>
 * 逻辑核心（{@link PocketChannelRunner} / {@link PocketChannelManager} / {@link PocketRotationCursor}
 * 以及三个纯数据件）只操作 {@code String/int/long/枚举/数组}，因此可在纯 JVM 下实例化并单测；
 * 一切 {@code ItemStack}/{@code World}/{@code TileEntity}/AE2 触点都沉到唯一实现
 * {@link PocketAeChannelOps}，测试不触达它。
 * <p>
 * 内容与数量以"标识串 + 点数"传递：{@code contentKey} 采用 {@link PocketFilterConfig} 的稳定键式样
 * （物品 {@code itemId+meta+nbt}、流体 {@code fluidName}、源质 {@code typeId+aspect tag}），
 * 实现方据此反解真实栈；<b>绝不传递通道索引或列表下标</b>（R9/R17）。
 */
public interface PocketChannelOps {

    /** 来源的形状（★R86 缺陷 3：注入向不再只有中栏物品）。 */
    enum SourceKind {
        /** 口袋中栏真实格：{@code slot} = 格号，{@code count} = 件数。 */
        ITEM,
        /** 口袋流体条：{@code slot} = tank 号，{@code count} = mB。 */
        FLUID,
        /**
         * 口袋源质表：{@code slot} 不参与定位（恒 {@code FILTER_SLOT_UNSET}），身份全在
         * {@code contentKey} 解出的 tag 上；{@code count} = 点数。
         */
        ESSENCE
    }

    /**
     * 源容器里的一个待搬运条目：区域坐标 + 内容标识 + 数量。
     * <p>
     * ★R86：{@code slot} 的含义由 {@link #kind} 决定（中栏格号 / tank 号 / 源质格号），
     * 三类各自只在<b>自己的区域空间</b>里计数——与 {@code PocketFilterConfig} 的
     * {@code kind + ':' + slotIndex} 复合键同一条纪律，裸索引会让两类互相覆盖。
     */
    final class SourceSlot {

        /** 源容器（玩家背包/口袋物品栏）的槽位号；仅用于实现方回读真实栈与回写余量。 */
        public final int slot;
        /** 内容稳定标识（{@link PocketFilterConfig} 的键式样）。 */
        public final String contentKey;
        /** 该槽当前点数/个数。 */
        public final int count;
        /** ★R86：本条来源是哪一类（缺省 {@link SourceKind#ITEM}，旧调用点一个字不用改）。 */
        public final SourceKind kind;

        public SourceSlot(int slot, String contentKey, int count) {
            this(slot, contentKey, count, SourceKind.ITEM);
        }

        public SourceSlot(int slot, String contentKey, int count, SourceKind kind) {
            this.slot = slot;
            this.contentKey = contentKey == null ? "" : contentKey;
            this.count = count;
            this.kind = kind == null ? SourceKind.ITEM : kind;
        }
    }

    /** 一次搬运造成的带符号变化量（相对元件：注入为正、抽取为负），供网络视图通知合并。 */
    final class Delta {

        public final String diskuuid;
        public final String typeId;
        public final String contentKey;
        public final long amount;

        public Delta(String diskuuid, String typeId, String contentKey, long amount) {
            this.diskuuid = diskuuid;
            this.typeId = typeId;
            this.contentKey = contentKey;
            this.amount = amount;
        }
    }

    /** 一次注入/抽取的结果：回执码 + 实际动起来的点数。 */
    final class Outcome {

        public final PocketReceipt receipt;
        public final int moved;

        public Outcome(PocketReceipt receipt, int moved) {
            this.receipt = receipt == null ? PocketReceipt.LOST : receipt;
            this.moved = Math.max(0, moved);
        }

        public static Outcome of(PocketReceipt receipt, int moved) {
            return new Outcome(receipt, moved);
        }
    }

    /** 无变化的结果（用于「无事可做」而不便调用方自行造对象）。 */
    Outcome NOTHING = new Outcome(PocketReceipt.OK, 0);

    /** 空源快照常量，避免实现方每次分配列表。 */
    List<SourceSlot> NO_SOURCES = Collections.emptyList();

    /** 当前墙钟毫秒（冷却与动画窗口用，口径照 {@code NekoTradeHistory}）。 */
    long nowMs();

    /** 当前 tick（服务端节拍用；实现方给 {@code player.ticksExisted} 或服务器 tick）。 */
    long currentTick();

    /**
     * 元件是否已失联（R6 的统一口径：区块未加载、不在带电驱动器/ME 箱内、
     * 或 {@code getCellArray(type)} 全空）。
     * <p>
     * 「无法判定」与「确定没了」都返回 true 语义上不等价：区块未加载时实现方应返回
     * {@code false}（保留绑定、本轮不传输），与 {@code LegacyCellReminderScheduler.stillPresent:153-170}
     * 的 {@code chunkExists} 守卫分工一致。
     */
    boolean isCellLost(String diskuuid);

    /** 该元件当前可用的 typeId 列表（运行时注册序，元素为字符串 id；空列表即 {@link PocketReceipt#NO_CHANNEL}）。 */
    List<String> channelIdsOf(String diskuuid);

    /**
     * ★R97 S2：该通道承载的<b>来源类别</b>——注入相「有机会投」判据的一半（另一半是来源快照按
     * {@link SourceKind} 分桶，见 {@code PocketChannelRunner#injectPhase}）。
     * <p>
     * 三<b>已知</b>类：物品通道 → {@link SourceKind#ITEM}、流体通道 → {@link SourceKind#FLUID}、
     * 源质通道（ThaumicEnergistics 注册的 {@code "essentia"}）→ {@link SourceKind#ESSENCE}。
     * 返回 {@code null} = <b>未知第三方通道</b>：本仓不猜第三方通道吃哪种来源（R31/R44a 纪律），
     * 调用方按保守规则处理（有任意来源即视为可投、照常占对——宁可多占一对，也不让配额上限被
     * 未识别的通道绕过）。纯字符串映射，不触达世界。
     */
    SourceKind kindOfChannel(String typeId);

    /** 本轮可搬运的源条目快照（实现方按来源与配置过滤后给出，顺序即搬运序）。 */
    List<SourceSlot> snapshotSources();

    /** 把一个源条目注入指定 (元件, 通道)；实现方负责真实栈反解、MODULATE 写入与余量回写。 */
    Outcome inject(SourceSlot source, String diskuuid, String typeId);

    /**
     * 按配置声明从指定元件抽取到<b>本条声明自己的落点</b>；{@code count} 为需求量。
     * <p>
     * ★R84 起三支落点齐了：物品＝中栏声明格本体、流体＝本条声明那个 tank、源质＝物化成晶化源质进中栏。
     */
    Outcome extract(PocketFilterConfig.Filter filter, String diskuuid, int count);

    /**
     * 一次批量结束后的网络视图通知（R7：AE2 不轮询驱动器元件，直写后必须显式 post，
     * 否则终端/合成查到的是陈旧库存）。
     * <p>
     * 调用节奏由通道模式决定：<b>瞬时通道一次调用内穿完并把所有 delta 合并成一次带符号 post</b>；
     * <b>短效通道每秒一次</b>。实现方内部首选 typed
     * {@code IStorageGrid.postAlterationOfStoredItems(IAEStackType, Iterable<IAEStack>, BaseActionSource)}，
     * 兜底 {@code IGrid.postEvent(new MENetworkCellArrayUpdate())}。
     */
    void announce(List<Delta> deltas);
}
