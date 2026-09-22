package com.miaokatze.gtit.common.items.pocket;

/**
 * 一次元件写入的回执码。
 * <p>
 * <b>R10 的硬要求</b>：「被分区 WHITELIST 拒收」与「元件已满」必须分开发码。
 * 前者在 AE2 侧的表现是注入<b>原样返回 input</b>（{@code MEInventoryHandler.java:98-101,212-226}
 * 先查 {@code canAccept}，分区非空且 WHITELIST 时直接拒收未列出的物品），
 * 后者是 {@code canAccept} 通过但内部装不下；两者都让 remainder 等于请求量，
 * 只看返回值无法区分，必须把 {@code canAccept} 与写权限一起作为入参分类。
 * 混用的后果是玩家体验为「通道开了但东西不见了」。
 */
public enum PocketReceipt {

    /** 全部写入。 */
    OK,
    /** 部分写入（元件剩余容量不足，余量留在源槽）。 */
    PARTIAL,
    /** 分区 WHITELIST 未列出 / BLACKLIST 命中 ⇒ 原样退回，与"元件满"必须分开（R10）。 */
    FILTER_REJECTED,
    /** 元件已满：可接受但装不下。 */
    FULL,
    /** 抽取方向的落点（玩家背包/口袋真实栏）没有空位，与"元件已满"是两回事。 */
    TARGET_FULL,
    /** 无写权限（元件被锁、只读卡、驱动器掉电导致 handler 不可写）。 */
    NO_ACCESS,
    /** 元件失联：区块未加载、被搬走、已销毁或不再处于带电容器内。 */
    LOST,
    /** 该元件没有可用通道（typeId 候选为空）。 */
    NO_CHANNEL;

    /** 是否有任何内容真的进了元件（决定是否计入"已传输"与网络通知）。 */
    public boolean moved() {
        return this == OK || this == PARTIAL;
    }

    /** 是否属于"本轮就此打住、余量顺延下一轮"（R11 的 remainder 即 break；★只用于<b>注入方向</b>）。 */
    public boolean stopsBatch() {
        return this != OK;
    }

    /**
     * ★R87（饿死收窄，取证 r87-ret-essence §2.2 次级 A）：<b>注入方向</b>的新停批判据。
     * <p>
     * 旧注入批沿用 {@link #stopsBatch()}（= {@code this != OK}），代价是"队首一格对当前轮转通道产出
     * {@code FULL/FILTER_REJECTED}，排在后面的来源（流体/源质在队尾）每拍都被截断"的结构性饿死。
     * 收窄后只有<b>元件/网络侧此刻给不出</b>（失联、无写权限）才停批；{@code FULL/FILTER_REJECTED/
     * NO_CHANNEL} 是"这一格对这一通道自己的属性"——计失败后<b>跳过该来源继续</b>，余量照旧留在原槽
     * （本批绝不重试同一槽，这条 remainder 纪律不变）。
     * <p>
     * ★{@link #stopsBatch()} 与 {@link #stopsExtractBatch()} 的<b>本体语义一字未动</b>（别处还在用，
     * 且有测试钉着"两条判据不得并回一条"）；注入批的调用侧改读本谓词。
     */
    public boolean stopsInjectBatch() {
        return this == LOST || this == NO_ACCESS;
    }

    /**
     * ★R85 A1：<b>抽取方向</b>的停批判据。
     * <p>
     * 抽取侧的"落点已满"（{@link #TARGET_FULL}／{@link #PARTIAL}）与"这只元件没有这个通道"
     * （{@link #NO_CHANNEL}）都是<b>单条声明自己的属性</b>——每条物品声明有<b>自己的</b>落点格、每条流体声明有
     * 自己的 tank，所以它们绝不能牵连同批的其余声明。R84 把物品落点改成声明格之后，"格已补满"是每条声明的
     * <b>必然稳态</b>，沿用 {@link #stopsBatch()} 就等于"队首一条补满 ⇒ 后面全部永久饿死"。
     * 只有元件/网络侧此刻给不出更多（失联、无读权限）才值得整对打住、顺延下一轮。
     */
    public boolean stopsExtractBatch() {
        return this == LOST || this == NO_ACCESS;
    }

    /**
     * 把 AE 侧的原始结论分类成回执码。
     * <p>
     * 判定顺序即优先级：失联 &gt; 无请求量 &gt; 全量入仓 &gt; 无写权限 &gt; 分区拒收 &gt; 满 &gt; 部分。
     * "无写权限"排在"分区拒收"之前，是因为 {@code MEInventoryHandler.canAccept} 对
     * {@code !hasWriteAccess} 同样返回 false，不先拆出来会把锁仓元件误报成分区问题。
     *
     * @param resolved       元件与通道 handler 是否解析成功
     * @param hasWriteAccess handler 的 {@code getAccess()} 是否含写权限
     * @param acceptable     handler 的 {@code canAccept(stack)} 结论
     * @param requested      请求写入的点数
     * @param leftover       {@code injectItems} 返回的余量（null 记为 0）
     */
    public static PocketReceipt classify(boolean resolved, boolean hasWriteAccess, boolean acceptable, long requested,
        long leftover) {
        if (!resolved) {
            return LOST;
        }
        if (requested <= 0L || leftover <= 0L) {
            return OK;
        }
        if (!hasWriteAccess) {
            return NO_ACCESS;
        }
        if (!acceptable) {
            return FILTER_REJECTED;
        }
        return leftover >= requested ? FULL : PARTIAL;
    }
}
