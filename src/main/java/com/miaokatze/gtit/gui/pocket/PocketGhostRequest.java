package com.miaokatze.gtit.gui.pocket;

import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;

/**
 * ghost 就地转换的<b>文法与判定本体</b>（纯 JVM 件：只碰字符串、整数与 {@link PocketFilterConfig}，
 * 不触达 MC 世界对象、AE2 handler、Thaumcraft 或任何 widget）。
 * <p>
 * <b>为什么把这层单切出来</b：{@code NekoPocketPanel#onServerGhostRequest} 是 ghost 的<b>唯一</b>
 * 执行体（R18/R19：判定与落档必须在服务端，客户端只发请求），但它同时握着会话守卫、槽位 widget
 * 登记表与虚化广播，零依赖回归套件碰不到。本类把其中<b>会算错的那一半</b>（文法解析、分区域
 * 越界、按载荷类型分派 kind、同槽覆盖判定）搬成纯函数，面板那侧只剩
 * 「守卫 → {@link #apply} → 变了就写档并刷虚化」三步。
 * <p>
 * <b>请求文法</b>（分隔符与操作码见 {@link PocketConstants}）：
 * 
 * <pre>
 * SET|&lt;slot&gt;|&lt;载荷键&gt;          kind 由载荷键前缀推出（i/f/e ⇒ ITEM/FLUID/ESSENCE）
 * CLR|&lt;slot&gt;|&lt;kind 字母&gt;      I/F/E，见 {@link PocketConstants#GHOST_KIND_ITEM} 等
 * </pre>
 * 
 * ★CLR 必须带 kind：三个区域的槽索引<b>各自从 0 起</b>（中栏 0…134 / 流体槽 0…17 / 源质格 0…71，
 * R78②③ 把后两个上界放开到 18 与 72，但"同一数字分属三个区域"这件事一个字都没变），
 * 裸 {@code CLR|0} 分不清"清中栏第 0 格"还是"清流体槽第 0 格"——那正是
 * {@code PocketFilterConfig} 把键做成 {@code (kind, slotIndex)} 复合键的同一条结构约束
 * （R59b 偏离④ / R70）。载荷键自带前缀，故 SET 不需要第三段 kind。
 * <p>
 * 本功能未发布过 ⇒ <b>不写任何向后兼容分支</b>（R70 明文）。
 */
public final class PocketGhostRequest {

    /** 一次请求的结论（{@link #REJECTED} 与 {@link #UNCHANGED} 都不该写档、不该刷虚化）。 */
    public enum Outcome {
        /** 声明表被本请求改变（新增或删掉一条）。 */
        APPLIED,
        /** 请求合法但该格已经是这个状态（重复拖入同一载荷 / 解绑一个本来就没声明的格）。 */
        UNCHANGED,
        /** 文法不合法、索引越出该区域白名单、或载荷解不出（含伪造包）。 */
        REJECTED
    }

    /** 判定结论 + 命中的区域与槽号（{@code kind} 在 {@link Outcome#REJECTED} 时可为 {@code null}）。 */
    public static final class Decision {

        public final Outcome outcome;
        public final PocketFilterConfig.Kind kind;
        public final int slotIndex;

        Decision(Outcome outcome, PocketFilterConfig.Kind kind, int slotIndex) {
            this.outcome = outcome;
            this.kind = kind;
            this.slotIndex = slotIndex;
        }

        public boolean changed() {
            return outcome == Outcome.APPLIED;
        }
    }

    private static final Decision REJECT = new Decision(Outcome.REJECTED, null, PocketConstants.FILTER_SLOT_UNSET);

    private PocketGhostRequest() {}

    // ------------------------------------------------------------------ 文法（客户端与服务端共用同一份拼装）

    /** 拼装"把这一格就地转成配置格"的请求（kind 由载荷键前缀自己说明）。 */
    public static String setRequest(int slotIndex, String payloadKey) {
        return PocketConstants.GHOST_REQUEST_SET + PocketConstants.GHOST_REQUEST_SEPARATOR
            + slotIndex
            + PocketConstants.GHOST_REQUEST_SEPARATOR
            + payloadKey;
    }

    /** 拼装"解绑这一格"的请求（★kind 必带，见类 javadoc）。 */
    public static String clearRequest(PocketFilterConfig.Kind kind, int slotIndex) {
        return PocketConstants.GHOST_REQUEST_CLEAR + PocketConstants.GHOST_REQUEST_SEPARATOR
            + slotIndex
            + PocketConstants.GHOST_REQUEST_SEPARATOR
            + letterOf(kind);
    }

    /** 区域 → CLR 第三段的单字母（<b>唯一</b>映射点，两端都不写字面量）。 */
    public static String letterOf(PocketFilterConfig.Kind kind) {
        if (kind == null) {
            return "";
        }
        switch (kind) {
            case ITEM:
                return PocketConstants.GHOST_KIND_ITEM;
            case FLUID:
                return PocketConstants.GHOST_KIND_FLUID;
            case ESSENCE:
                return PocketConstants.GHOST_KIND_ESSENCE;
            default:
                return "";
        }
    }

    /** CLR 第三段 → 区域；不认识的字母一律 {@code null}（⇒ 调用方判 {@link Outcome#REJECTED}）。 */
    public static PocketFilterConfig.Kind kindOf(String letter) {
        if (PocketConstants.GHOST_KIND_ITEM.equals(letter)) {
            return PocketFilterConfig.Kind.ITEM;
        }
        if (PocketConstants.GHOST_KIND_FLUID.equals(letter)) {
            return PocketFilterConfig.Kind.FLUID;
        }
        if (PocketConstants.GHOST_KIND_ESSENCE.equals(letter)) {
            return PocketFilterConfig.Kind.ESSENCE;
        }
        return null;
    }

    // ------------------------------------------------------------------ 判定本体（★服务端唯一执行体的内核）

    /**
     * 解一条请求并就地改动 {@code filters}。
     * <p>
     * 客户端传来的字符串一律不可信：载荷键<b>重新解析</b>（{@link PocketFilterConfig#parseKey}）、
     * 槽索引<b>按所属区域重新校验</b>（{@link PocketFilterConfig#isAllowedSlotIndex}），
     * 两道都不过就一个字节都不写。伪造包最多只能往自己口袋里写声明（会话守卫在面板那侧）。
     */
    public static Decision apply(String request, PocketFilterConfig filters) {
        if (request == null || request.isEmpty() || filters == null) {
            return REJECT;
        }
        final String[] parts = request.split(java.util.regex.Pattern.quote(PocketConstants.GHOST_REQUEST_SEPARATOR), 3);
        if (parts.length < 2) {
            return REJECT;
        }
        final int slotIndex;
        try {
            slotIndex = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {
            return REJECT;
        }
        if (PocketConstants.GHOST_REQUEST_CLEAR.equals(parts[0])) {
            return applyClear(parts, slotIndex, filters);
        }
        if (PocketConstants.GHOST_REQUEST_SET.equals(parts[0])) {
            return parts.length < 3 ? REJECT : applySet(slotIndex, parts[2], filters);
        }
        return REJECT;
    }

    /** ★CLR：三段齐（第三段是区域字母）才动；越界或字母不认识一律拒收。 */
    private static Decision applyClear(String[] parts, int slotIndex, PocketFilterConfig filters) {
        if (parts.length < 3) {
            return REJECT;
        }
        final PocketFilterConfig.Kind kind = kindOf(parts[2]);
        if (kind == null || !PocketFilterConfig.isAllowedSlotIndex(kind, slotIndex)) {
            return REJECT;
        }
        return new Decision(filters.removeAt(kind, slotIndex) ? Outcome.APPLIED : Outcome.UNCHANGED, kind, slotIndex);
    }

    /** ★SET：kind 由<b>解出来的载荷类型</b>给出（流体条落 FLUID 空间、源质格落 ESSENCE 空间），不按"只有物品"一刀切。 */
    private static Decision applySet(int slotIndex, String payloadKey, PocketFilterConfig filters) {
        if (payloadKey.contains(PocketConstants.GHOST_REQUEST_SEPARATOR)) {
            // 式样不变量：载荷键内部只用 ':' 分段，'|' 一旦出现就是"拼接过的假键"
            // （parseKey 会把尾段整个吃进流体名里，归一化比对反而看不出来 ⇒ 必须在这里先拦）
            return REJECT;
        }
        final PocketFilterConfig.Filter parsed = PocketFilterConfig.parseKey(payloadKey);
        if (parsed == null || !payloadKey.equals(parsed.key())) {
            // 载荷键解不出、或"能解但归一化后不等于原文"（外来/被改写过形状的键）⇒ 整条拒收
            return REJECT;
        }
        final PocketFilterConfig.Kind kind = parsed.kind();
        if (!PocketFilterConfig.isAllowedSlotIndex(kind, slotIndex) || !hasRequiredPayload(parsed)) {
            return REJECT;
        }
        final PocketFilterConfig.Filter rebuilt = rebuildAt(kind, slotIndex, parsed);
        final PocketFilterConfig.Filter before = filters.at(kind, slotIndex);
        filters.add(slotIndex, rebuilt);
        final PocketFilterConfig.Filter now = filters.at(kind, slotIndex);
        if (now == null || !now.key()
            .equals(rebuilt.key())) {
            // add 的 false 有两种含义（同槽覆盖 / 越界或载荷空），故以"该格现在到底是不是这条"为准
            return new Decision(Outcome.REJECTED, kind, slotIndex);
        }
        return new Decision(
            before != null && before.key()
                .equals(rebuilt.key()) ? Outcome.UNCHANGED : Outcome.APPLIED,
            kind,
            slotIndex);
    }

    /**
     * 载荷的<b>非空</b>校验：{@link PocketFilterConfig#parseKey} 对 {@code f:}（空流体名）与
     * {@code e::TAG}（空通道 id）这类"能解但没内容"的串照样给出对象，而 {@code add} 只看整键是否为空。
     * 流体名与源质 typeId 的读档口径本来就是"缺键即丢条目"（{@code PocketFilterConfig#readFrom}），
     * 服务端写入口因此同样拒收，免得 ghost 表里留一条永远抽不出东西的死声明。
     */
    private static boolean hasRequiredPayload(PocketFilterConfig.Filter parsed) {
        if (parsed instanceof PocketFilterConfig.FluidFilter fluid) {
            return !fluid.fluidName.isEmpty();
        }
        if (parsed instanceof PocketFilterConfig.EssenceFilter essence) {
            return !essence.typeId.isEmpty() && !essence.tag.isEmpty();
        }
        return true;
    }

    /**
     * 把一条<b>只带载荷、不带槽位</b>的解出结果落到指定格上（ghost 的槽位由"被拖的那一格"决定，
     * 载荷键本身不含槽位，R38 第 1 条）。
     * <p>
     * ★单点：blob 编解码（S2C 回显）与服务端写入口都走这里，否则两处 rebuild 迟早漂移。
     */
    public static PocketFilterConfig.Filter rebuildAt(PocketFilterConfig.Kind kind, int slotIndex,
        PocketFilterConfig.Filter payload) {
        if (kind == null || payload == null) {
            return null;
        }
        switch (kind) {
            case ITEM:
                if (!(payload instanceof PocketFilterConfig.ItemFilter item)) {
                    return null;
                }
                return new PocketFilterConfig.ItemFilter(slotIndex, item.itemId, item.meta, item.nbtString);
            case FLUID:
                if (!(payload instanceof PocketFilterConfig.FluidFilter fluid)) {
                    return null;
                }
                return new PocketFilterConfig.FluidFilter(slotIndex, fluid.fluidName);
            case ESSENCE:
                if (!(payload instanceof PocketFilterConfig.EssenceFilter essence)) {
                    return null;
                }
                return new PocketFilterConfig.EssenceFilter(slotIndex, essence.typeId, essence.tag);
            default:
                return null;
        }
    }
}
