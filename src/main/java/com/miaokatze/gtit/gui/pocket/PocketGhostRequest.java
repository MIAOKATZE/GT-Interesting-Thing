package com.miaokatze.gtit.gui.pocket;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.utils.Color;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceStore;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;

/**
 * ghost 就地转换的<b>文法与判定本体</b>（纯 JVM 件：只碰字符串、整数与 {@link PocketFilterConfig}，
 * 不触达 MC 世界对象、AE2 handler、Thaumcraft 或任何 widget）。
 * <p>
 * <b>为什么把这层单切出来</b：{@code NekoPocketPanel#onServerGhostRequest} 是 ghost 的<b>唯一</b>
 * 执行体（R18/R19：判定与落档必须在服务端，客户端只发请求），但它同时握着会话守卫、槽位 widget
 * 登记表与虚化广播，零依赖回归套件碰不到。本类把其中<b>会算错的那一半</b>（文法解析、分区域
 * 越界、按载荷类型分派 kind、同槽覆盖判定、★组上限的步进与收口）搬成纯函数，面板那侧只剩
 * 「守卫 → {@link #apply} → 变了就写档并刷虚化」三步。
 * <p>
 * ★R83 C2 起本类有两个<b>惰性</b>入站引用：{@link UpOrDown}（只是带 {@code modifier} 的枚举，无 MC 依赖）
 * 与 {@link Color}（只活在 {@link #capReadoutColor()} 的<b>方法体</b>里 —— {@code Color} 的类初始化会牵进
 * {@code ModularUI} 主类）。⇒ {@link #capReadoutColor()} <b>只能</b>被客户端绘制路径调用，不得进任何
 * 判定/落档分支，也不得放进本类的静态字段初始化，零依赖回归套件同样不得调它。
 * <p>
 * <b>请求文法</b>（分隔符与操作码见 {@link PocketConstants}）：
 * 
 * <pre>
 * SET|&lt;slot&gt;|&lt;载荷键&gt;          kind 由载荷键前缀推出（i/f/e ⇒ ITEM/FLUID/ESSENCE）
 * CLR|&lt;slot&gt;|&lt;kind 字母&gt;      I/F/E，见 {@link PocketConstants#GHOST_KIND_ITEM} 等
 * CAP|&lt;slot&gt;|&lt;kind 字母&gt;|&lt;绝对值&gt;   ★R83 C2：调这一格声明的组上限（alt+滚轮）
 * FLG|&lt;slot&gt;|&lt;kind 字母&gt;|&lt;手势字母&gt;  ★R91-⑤：切这一格的<b>属性位</b>（B=中键 BIND /
 *                                              M=alt+左 记忆 / P=alt+右 阻拦上传）——★也恰 4 段；
 *                                              ★R91-h：该支读回的载荷键必须与 kind 字母<b>同区</b>，
 *                                              跨区合法键整条拒收（见 {@link #applyFlag} 纪律 6）
 * </pre>
 *
 * ★<b>CAP 为什么走字符串而不是 int 动作通道</b>：流体一档就是 160,000 mB、默认值 16,000,000，
 * 而自家 {@code SYNC_ACTION} 的打包式样是 {@code code*1024 + arg}（{@code ACTION_ARG_BASE}），
 * {@code 16_000_000 / 1024 = 15625} 早已越出"一个动作码的 arg 区间"⇒ 会落进
 * {@code onServerAction} 的 {@code default: break} <b>静默失效</b>。字符串通道无此上限，
 * 且 CLR/SET 已经在用它 ⇒ <b>零新动作码、零新同步键</b>（面板那根 {@code SYNC_GHOST_REQUEST} 原样复用）。
 * <p>
 * ★<b>CAP 的第四段是绝对值</b>，但"步进"这条算式仍只有一份：{@link #nextCap}（客户端格件用，手里有
 * 样本栈 ⇒ 知道该物品的 {@code maxStackSize}）与 {@link #applyCap}（服务端用，只做区间收口）
 * 共读同一份 {@link PocketConstants} 步进与上下界常量 ⇒ 不会出现"客户端按一套表、服务端按另一套表"。
 * ★服务端<b>不重算步进</b>的硬理由：物品的自然满量只有拿着 {@code ItemStack} 的一端解得出，
 * 服务端若再解一次载荷键去查注册表就是第二处真相 ⇒ 物品支的服务端上界刻意不设
 * （{@link PocketConstants#FILTER_CAP_CEILING_ITEM_SERVER}），真正的"不超过一叠"在消费侧收口。
 * <p>
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
        if (isCapDirective(payloadKey)) {
            // ★R83 C2 的过渡形状：格件只能拿到面板那一个 requestGhost(槽, 载荷键) 入口（面板文件归主代理独占，
            // 本片不得给它加新方法），于是"调上限"借同一个入口上来、由这里把槽号插进指令里。
            // 判别是<b>无歧义</b>的：真载荷键只用 ':' 分段、绝不含 '|'（{@link #applySet} 的守卫正是这一条），
            // 所以带 '|' 的第二段只可能是 {@link #capDirective} 造出来的指令。
            // 产物与 {@code capRequest(slot, kind, cap)} 逐字符相同（用例 capDirectiveRoundTrip 钉住），
            // 面板若日后加一个 requestGhostCap(...) 转发，本分支即可删除（Panel 待办 P-1）。
            return PocketConstants.GHOST_REQUEST_CAP + PocketConstants.GHOST_REQUEST_SEPARATOR
                + slotIndex
                + PocketConstants.GHOST_REQUEST_SEPARATOR
                + capDirectiveTail(payloadKey);
        }
        return PocketConstants.GHOST_REQUEST_SET + PocketConstants.GHOST_REQUEST_SEPARATOR
            + slotIndex
            + PocketConstants.GHOST_REQUEST_SEPARATOR
            + payloadKey;
    }

    /** {@link #capDirective} 的固定头（= {@code CAP|}），同时是 {@link #isCapDirective} 的判别前缀。 */
    private static final String CAP_DIRECTIVE_PREFIX = PocketConstants.GHOST_REQUEST_CAP
        + PocketConstants.GHOST_REQUEST_SEPARATOR;

    /**
     * ★R83 C2：客户端交给 {@code requestGhost} 第二段的"调上限指令"（不是载荷键）。
     * <p>
     * 式样 {@code CAP|<区域字母>|<绝对值>} —— 三段里已经带了区域与目标值，槽号由 {@link #setRequest}
     * 那一步补上（调用方本来就握着 {@code slotIndex}，两个入口共用同一个数 ⇒ 不加第二处真相）。
     */
    public static String capDirective(PocketFilterConfig.Kind kind, int cap) {
        return CAP_DIRECTIVE_PREFIX + letterOf(kind) + PocketConstants.GHOST_REQUEST_SEPARATOR + cap;
    }

    /** 第二段是否是 {@link #capDirective} 造出来的调上限指令（真载荷键永不含 '|'，见 {@link #setRequest}）。 */
    public static boolean isCapDirective(String payloadKey) {
        return payloadKey != null && payloadKey.startsWith(CAP_DIRECTIVE_PREFIX);
    }

    /** ★R83 C2：完整的调上限请求 {@code CAP|<slot>|<kind 字母>|<绝对值>}。 */
    public static String capRequest(int slotIndex, PocketFilterConfig.Kind kind, int cap) {
        return PocketConstants.GHOST_REQUEST_CAP + PocketConstants.GHOST_REQUEST_SEPARATOR
            + slotIndex
            + PocketConstants.GHOST_REQUEST_SEPARATOR
            + letterOf(kind)
            + PocketConstants.GHOST_REQUEST_SEPARATOR
            + cap;
    }

    /**
     * 从指令串里取出"区域字母 + 绝对值"尾段（{@link #setRequest} 插槽号时用）。
     * <p>
     * ★不在此处解 kind/值：解它们是 {@link #applyCap} 的活（那里要判白名单、区间与"这一格到底有没有声明"），
     * 在这里再解一次就是两处真相。
     */
    private static String capDirectiveTail(String directive) {
        return directive.substring(CAP_DIRECTIVE_PREFIX.length());
    }

    /** 拼装"解绑这一格"的请求（★kind 必带，见类 javadoc）。 */
    public static String clearRequest(PocketFilterConfig.Kind kind, int slotIndex) {
        return PocketConstants.GHOST_REQUEST_CLEAR + PocketConstants.GHOST_REQUEST_SEPARATOR
            + slotIndex
            + PocketConstants.GHOST_REQUEST_SEPARATOR
            + letterOf(kind);
    }

    /**
     * ★★<b>R91-⑤⑥</b> 拼装一条属性请求 {@code FLG|<槽号>|<区域字母>|<手势字母>}（★恰 4 段）。
     * <p>
     * 三个手势字母 = {@link PocketConstants#GHOST_FLAG_BIND}（中键）/
     * {@link PocketConstants#GHOST_FLAG_MEMORY}（alt+左）/
     * {@link PocketConstants#GHOST_FLAG_UPLOAD_BLOCK}（alt+右）。★客户端只报"玩家做了哪个手势"，
     * <b>迁移真值由服务端算</b>（{@link #nextAttr} / {@link #nextUploadBlocked}）——R18/R19 的老口径：
     * 客户端字符串一律不可信，且让两端各算一次迁移表就是两处真相。
     */
    public static String flagRequest(int slotIndex, PocketFilterConfig.Kind kind, String gesture) {
        return PocketConstants.GHOST_REQUEST_FLAG + PocketConstants.GHOST_REQUEST_SEPARATOR
            + slotIndex
            + PocketConstants.GHOST_REQUEST_SEPARATOR
            + letterOf(kind)
            + PocketConstants.GHOST_REQUEST_SEPARATOR
            + gesture;
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
     * 解一条请求并就地改动 {@code filters}（旧两参形态 = 不带源质格位真值的<b>遗留口径</b>：
     * 源质 SET 不做归属对账 / 不建档，行为与 R88 逐字一致，供既有回归用例与不关心格位的调用方）。
     * 生产的服务端唯一执行体走 {@link #apply(String, PocketFilterConfig, PocketEssenceStore)}。
     */
    public static Decision apply(String request, PocketFilterConfig filters) {
        return apply(request, filters, null);
    }

    /**
     * ★R90 E3（D3）三参形态：多带<b>源质格位归属表</b>（{@code PocketEssenceStore}，可为 {@code null}
     * = 旧两参口径）。客户端传来的字符串一律不可信：载荷键<b>重新解析</b>
     * （{@link PocketFilterConfig#parseKey}）、槽索引<b>按所属区域重新校验</b>
     * （{@link PocketConstants#GHOST_REQUEST_SEPARATOR} + {@link PocketFilterConfig#isAllowedSlotIndex}）、
     * 上限值<b>重新收口</b>（{@link #clampCap}）、★源质声明<b>与格位归属对账</b>
     * （{@link #applySet}：无归属格 + 载荷 tag 非空 ⇒ 放行建档 + {@code assignCell}；有归属但
     * tag 不匹配 ⇒ 拒——旧口径"只校验槽号白名单 + 载荷非空"留下的<b>伪造缝隙</b>：任何 C2S 都能在
     * 无归属格 / 别人的格上写死一条声明，格位归属与声明表从此各说各话；新语义下声明必须与格位
     * 同源成立），四道都不过就一个字节都不写。伪造包最多只能往自己口袋里写声明（会话守卫在面板那侧）。
     * <p>
     * ★切分用 {@code limit = 4}（★R83 C2 的 {@code CAP} 要第四段）而不是"每个操作码各切一次"，因此<b>段数</b>本身
     * 成了判据的一部分：SET/CLR 只接受恰 3 段、CAP 只接受恰 4 段。旧口径"载荷键里含 '|' 就拒收"由
     * {@link #applySet} 的长度检查与原守卫<b>共同</b>保住（两种写法都是 REJECTED，不改变判据）。
     */
    public static Decision apply(String request, PocketFilterConfig filters, PocketEssenceStore essenceCells) {
        return apply(request, filters, essenceCells, null);
    }

    /**
     * ★<b>R91-⑤</b>「格内有物 ⇒ 按该物记录」的<b>注入接口</b>：属性迁移要落载荷时问这一条
     * （服务端实现读真实内容；纯 JVM 用例注入桩件）。
     * <p>
     * ★为什么不在客户端把载荷一起发上来（三个手势 ⇒ 两条请求）：① R18/R19 的老口径——客户端字符串
     * 一律不可信，"这一格里躺的是什么"只有服务端知道；② 两条请求 = 一次手势两次往返，中间被别的包
     * 插队就会出现"attr 落了、载荷没落"的半态；③ 三个格件各写一遍"从本格内容拼载荷键"就是三份真相
     * （中栏是 {@code contentKey}、流体是 {@code fluidKey}、源质要现算通道 id）。
     * <p>
     * ★{@code null}（两参/三参旧形态）⇒ <b>只切属性、不落载荷</b>，既有回归用例行为逐字不变。
     */
    public interface PayloadSource {

        /**
         * 该格<b>当前真实内容</b>对应的载荷键。
         *
         * @return 载荷键；格内空 / 读不出身份 ⇒ {@code null} 或空串（＝进入"只挂状态、内容待拖拽落成"）
         */
        String payloadKeyAt(PocketFilterConfig.Kind kind, int slotIndex);
    }

    /**
     * ★★<b>R91-⑤⑥ 的四参形态</b>（生产的服务端唯一执行体走这一条）：在三参形态之上多带
     * {@link PayloadSource}，于是 {@code FLG} 支能把"属性 + 该格现有内容"一次写成一格的状态。
     * 其余四道校验与段数纪律与三参形态<b>逐字相同</b>。
     */
    public static Decision apply(String request, PocketFilterConfig filters, PocketEssenceStore essenceCells,
        PayloadSource payloads) {
        if (request == null || request.isEmpty() || filters == null) {
            return REJECT;
        }
        final String[] parts = request.split(java.util.regex.Pattern.quote(PocketConstants.GHOST_REQUEST_SEPARATOR), 4);
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
            return parts.length != 3 ? REJECT : applyClear(parts, slotIndex, filters);
        }
        if (PocketConstants.GHOST_REQUEST_SET.equals(parts[0])) {
            return parts.length != 3 ? REJECT : applySet(slotIndex, parts[2], filters, essenceCells);
        }
        if (PocketConstants.GHOST_REQUEST_CAP.equals(parts[0])) {
            return parts.length != 4 ? REJECT : applyCap(slotIndex, parts[2], parts[3], filters);
        }
        if (PocketConstants.GHOST_REQUEST_FLAG.equals(parts[0])) {
            // ★FLG 与 CAP 同为恰 4 段 ⇒ 上面那条 split(..., 4) 一字不改（段数即判据，放宽必红）
            return parts.length != 4 ? REJECT
                : applyFlag(slotIndex, parts[2], parts[3], filters, essenceCells, payloads);
        }
        return REJECT;
    }

    // -------------------------------------------------- ★R91-⑤ 属性迁移真值表（★唯一一份，服务端算）

    /**
     * <b>attr 的迁移单源</b>：{@code gesture} 是玩家刚做的手势字母（
     * {@link PocketConstants#GHOST_FLAG_BIND} / {@link PocketConstants#GHOST_FLAG_MEMORY}），
     * 返回迁移<b>之后</b>的 attr。
     * <p>
     * 三条裁定（R91-⑤）逐字兑现：
     * <ol>
     * <li><b>同格重复同手势 = 撤销该 attr</b> ⇒ {@code BIND} 手势落在 {@code BIND} 上 ⇒ {@code NONE}；
     * {@code MEMORY} 手势落在 {@code MEMORY} 上 ⇒ {@code NONE}；</li>
     * <li><b>互斥单值</b> ⇒ {@code BIND} 手势落在 {@code MEMORY} 上就是换过去（不会出现一格两个 attr），
     * 换过去时<b>载荷不换</b>（只有换到 {@code NONE} 才随解绑一起撤，见 {@link #applyFlag}）；</li>
     * <li>★本方法<b>只管 attr</b>：{@code P} 是正交位、走 {@link #nextUploadBlocked}，两者不互相影响。</li>
     * </ol>
     * 不认识的手势字母 ⇒ 原值返回（调用方据"没变"拒收/不写档，★不猜玩家想挂哪个属性）。
     */
    public static int nextAttr(int currentAttr, String gesture) {
        final int current = PocketConstants.normalizeGhostAttr(currentAttr);
        if (PocketConstants.GHOST_FLAG_BIND.equals(gesture)) {
            return current == PocketConstants.GHOST_ATTR_BIND ? PocketConstants.GHOST_ATTR_NONE
                : PocketConstants.GHOST_ATTR_BIND;
        }
        if (PocketConstants.GHOST_FLAG_MEMORY.equals(gesture)) {
            return current == PocketConstants.GHOST_ATTR_MEMORY ? PocketConstants.GHOST_ATTR_NONE
                : PocketConstants.GHOST_ATTR_MEMORY;
        }
        return current;
    }

    /** <b>P 位的迁移单源</b>：alt+右 = 翻转（正交位 ⇒ 与 {@code attr} 各走各的，可并存）。 */
    public static boolean nextUploadBlocked(boolean current, String gesture) {
        return PocketConstants.GHOST_FLAG_UPLOAD_BLOCK.equals(gesture) ? !current : current;
    }

    /** 一个手势字母是否是<b>attr</b> 类（B/M）；不是则只可能是 P 类。 */
    public static boolean isAttrGesture(String gesture) {
        return PocketConstants.GHOST_FLAG_BIND.equals(gesture) || PocketConstants.GHOST_FLAG_MEMORY.equals(gesture);
    }

    /** 一个手势字母是否是本文法认识的（FLG 第四段的白名单，★伪造包在这里被拒）。 */
    public static boolean isKnownGesture(String gesture) {
        return isAttrGesture(gesture) || PocketConstants.GHOST_FLAG_UPLOAD_BLOCK.equals(gesture);
    }

    /**
     * ★★<b>R91-b 的 NEI 拖拽分派表</b>（★唯一判据、只住本类一处；三个格件都调它，服务端 SET 支也认它）：
     * 一格当前的属性状态 ⇒ 拖进来的载荷该<b>落到哪一档</b>。
     */
    public enum DragRoute {
        /** 显式 {@code attr = BIND} ⇒ 载荷写进 BIND（＝既有语义，不变）。 */
        EXPLICIT_BIND,
        /** 显式 {@code attr = MEMORY} ⇒ 载荷写进 MEMORY（新增：记忆内容 = 拖进来的那个东西）。 */
        EXPLICIT_MEMORY,
        /** ★<b>无 attr 也无 P</b> 的空格 ⇒ 隐式按 BIND 建档（＝需求 4 的既有拖拽建档语义，不许回退）。 */
        IMPLICIT_BIND,
        /** 只有 {@code P}（无 attr）⇒ <b>不响应</b>（用户原话"alt 右键的锁格子是没效果的"）。 */
        IGNORE
    }

    /**
     * 分派表的<b>纯函数本体</b>（四行真值，回归套件逐行钉死）：
     * <table border="1">
     * <tr>
     * <th>格子当前状态</th>
     * <th>结果</th>
     * </tr>
     * <tr>
     * <td>显式 attr=BIND</td>
     * <td>{@link DragRoute#EXPLICIT_BIND}</td>
     * </tr>
     * <tr>
     * <td>显式 attr=MEMORY</td>
     * <td>{@link DragRoute#EXPLICIT_MEMORY}</td>
     * </tr>
     * <tr>
     * <td>无 attr 且无 P</td>
     * <td>{@link DragRoute#IMPLICIT_BIND}（★需求 4 不回退）</td>
     * </tr>
     * <tr>
     * <td>只有 P（无 attr）</td>
     * <td>{@link DragRoute#IGNORE}</td>
     * </tr>
     * </table>
     * ★<b>attr 优先</b>：已有显式 attr 时不论 P 与否都按 attr 走 ⇒ 不会"借拖拽顺手改成另一种属性"
     * （R91-b 连带改判的那半句）。
     */
    public static DragRoute dragRouteOf(int attr, boolean uploadBlocked) {
        final int normalized = PocketConstants.normalizeGhostAttr(attr);
        if (normalized == PocketConstants.GHOST_ATTR_BIND) {
            return DragRoute.EXPLICIT_BIND;
        }
        if (normalized == PocketConstants.GHOST_ATTR_MEMORY) {
            return DragRoute.EXPLICIT_MEMORY;
        }
        return uploadBlocked ? DragRoute.IGNORE : DragRoute.IMPLICIT_BIND;
    }

    /**
     * ★R91-⑤ 的<b>遮罩在场判据</b>（★只放宽"这一格算不算需求态"，★一条内容判据都不放宽）：
     * {@code declared}（= 有载荷声明，即旧的 {@code ghost} 比特）<b>或</b> {@code attr != NONE}。
     * <p>
     * ★"真实内容为空才遮"那三条判据（{@code storedSize() <= 0} / {@code stock <= 0} /
     * {@code super.getFluidStack()} 空）<b>一字未动</b>，仍是各格件里的唯一实质门；本方法只回答
     * "这一格是否处于需要虚化的状态"。真值表（R91-⑤ 表格的第 3/5 行 ⇒ pending 态也要遮）：
     * attr=BIND 或 MEMORY 且格内空 ⇒ 遮；只有 P ⇒ <b>不遮</b>（用户口径：P 不携带内容，观感只是角标）。
     */
    public static boolean drawsGhostMask(boolean declared, int attr) {
        return declared || PocketConstants.normalizeGhostAttr(attr) != PocketConstants.GHOST_ATTR_NONE;
    }

    /**
     * ★<b>蓝色 {@code L} 角标的文本单源</b>（javadoc 承诺"attr 不是 MEMORY 就不绘制"⇒ 用例
     * {@code ghost_badge_readouts_are_empty_when_not_applicable} 钉住这里返回空串）。
     * <p>
     * 返回空串 = 各格件的"空文本即不画"早退生效（与 {@link #capReadout} 对负数返回空串同一形状）。
     */
    public static String memoryBadgeText(int attr) {
        return PocketConstants.normalizeGhostAttr(attr) == PocketConstants.GHOST_ATTR_MEMORY ? "L" : "";
    }

    /**
     * ★<b>绿色 {@code P} 角标的文本单源</b>（javadoc 承诺"P 位为 off 就不绘制"⇒ 同一条用例钉住空串）。
     * ★只读 P 这一位：{@code attr} 不参与 ⇒ 一格同时有 attr 与 P 时两个角标各画各的（互不覆盖：
     * L 在<b>左上</b>、P 在<b>左下</b>、cap 读数在<b>右上</b>）。
     */
    public static String uploadBlockBadgeText(boolean uploadBlocked) {
        return uploadBlocked ? "P" : "";
    }

    /**
     * {@code L} 的纵向落点：<b>左上</b>角。
     * <p>
     * ★取证 {@code r91-ret-gesture.md} §5 实测过"左上角当前无人占用"（橙字在右上 y=2、数量文字在
     * BottomRight），所以本行不与现有任何读数重叠；纵向仍走 {@link #CAP_READOUT_TOP} 那一条上带，
     * ★<b>横向</b>换成左对齐（{@link #badgeLeftX()}）。
     */
    public static float memoryBadgeTop() {
        return CAP_READOUT_TOP;
    }

    /**
     * {@code P} 的纵向落点：<b>左下</b>角 = 格高 − 内缩 − 缩放后字高。
     * <p>
     * ★与 BottomRight 的数量文字<b>同带</b>但异侧（那一串右对齐），16px 格内的像素净空只能实机判
     * （已进"只能实机"清单）；这里给的是算式，不是"看着办"。
     */
    public static float uploadBlockBadgeTop(int cellHeight) {
        return cellHeight - CAP_READOUT_MARGIN - BADGE_TEXT_PX * CAP_READOUT_SCALE;
    }

    /** 两个角标共同的横向落点 = 左边距（与右对齐的 {@link #capReadoutX} 同一枚边距常量）。 */
    public static float badgeLeftX() {
        return CAP_READOUT_MARGIN;
    }

    /** vanilla 字体未缩放行高（只用于 {@link #uploadBlockBadgeTop} 的净空算式，★不是新贴图尺寸）。 */
    private static final float BADGE_TEXT_PX = 9f;

    /**
     * ★{@code L} 的蓝色（用户原话"左上角蓝色 L"）。
     * <p>
     * ★★<b>写成方法而不是 {@code static final int}</b>——取证 D 的既有约束、与本类
     * {@link #capReadoutColor()} 同一条纪律：{@code Color} 的类初始化会牵进 {@code ModularUI} 主类，
     * 放进静态字段就让本类（被零依赖套件直调的纯 JVM 件）在测试 JVM 里初始化即炸。
     */
    public static int memoryBadgeColor() {
        return Color.BLUE.main;
    }

    /** ★{@code P} 的绿色（"左下角绿色 P"）；{@code static final} 禁令同 {@link #memoryBadgeColor()}。 */
    public static int uploadBlockBadgeColor() {
        return Color.GREEN.main;
    }

    /**
     * 属性层的 S2C 编码（★<b>独立的一根串</b>，走面板新加的 {@code StringSyncValue}）：
     * 每格一条 {@code 区域字母,槽号,attr,P}，记录之间 {@code ';'}。
     * <p>
     * ★<b>不扩 {@code SYNC_GHOST} blob 的段数、也不把属性层焊回它的编解码</b>（R91-a 裁定的原话）：
     * 方案 α 被选中的全部理由就是"零编解码风险、不连坐既有用例"，把 attr 塞回 blob 等于自己废掉它。
     * ★只编"至少挂了一个属性"的格（{@link PocketFilterConfig#hasNoFlags()} 为空 ⇒ 整串是空串），
     * 长度上界 = 135+18+72 格 × 8 字符，远小于 {@link PocketConstants#GHOST_BLOB_MAX_CHARS}
     * 与上游 {@code writeStringSafe} 的截断线 ⇒ 本串<b>不需要</b>预算回退逻辑（少一套会写错的代码）。
     */
    public static String flagsBlobOf(PocketFilterConfig filters) {
        if (filters == null || filters.hasNoFlags()) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        for (PocketFilterConfig.FlagRecord record : filters.flagRecords()) {
            if (builder.length() > 0) {
                builder.append(FLAG_RECORD_SEPARATOR);
            }
            builder.append(letterOf(record.kind))
                .append(FLAG_FIELD_SEPARATOR)
                .append(record.slotIndex)
                .append(FLAG_FIELD_SEPARATOR)
                .append(record.attr)
                .append(FLAG_FIELD_SEPARATOR)
                .append(record.uploadBlocked ? 1 : 0);
        }
        return builder.toString();
    }

    /** 属性 blob 的记录间分隔符（与 ghost blob 同字符，但那是<b>另一根通道</b>，互不解析）。 */
    private static final char FLAG_RECORD_SEPARATOR = ';';
    /** 属性 blob 的字段间分隔符。 */
    private static final char FLAG_FIELD_SEPARATOR = ',';

    /**
     * {@link #flagsBlobOf} 的<b>解码侧</b>：把属性层落到 {@code target} 的位表上（★客户端镜像专用）。
     * <p>
     * 三条口径：① 解不出的记录<b>整条丢弃</b>，不炸面板（与 {@code NekoPocketPanel#parseGhostBlob}
     * 同一条"外来/陈旧串不许成为崩溃源"）；② 槽号越出该区域白名单即丢（位表读口拿不到 = 那格无属性，
     * 与没配过等效）；③ 一个字节都不写进 {@code target} 的<b>载荷表</b> ⇒ 本方法与 ghost blob 的
     * 解码互不影响，两根通道各自只写自己那半（★这就是"不把属性层焊回 blob"的兑现点）。
     *
     * @return 成功落进位表的条目数（= 本端解析到的条数，与 ghost 的 {@code ghostSyncedCount} 同一族读数）
     */
    public static int applyFlagsBlob(String blob, PocketFilterConfig target) {
        if (target == null) {
            return 0;
        }
        target.clearFlags();
        if (blob == null || blob.isEmpty()) {
            return 0;
        }
        int applied = 0;
        for (String record : blob.split(String.valueOf(FLAG_RECORD_SEPARATOR))) {
            final String[] parts = record.split(java.util.regex.Pattern.quote(String.valueOf(FLAG_FIELD_SEPARATOR)), 4);
            if (parts.length != 4) {
                continue;
            }
            final PocketFilterConfig.Kind kind = kindOf(parts[0]);
            if (kind == null) {
                continue;
            }
            final int slot;
            final int attr;
            final int blocked;
            try {
                slot = Integer.parseInt(parts[1]);
                attr = PocketConstants.normalizeGhostAttr(Integer.parseInt(parts[2]));
                blocked = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (!PocketFilterConfig.isAllowedSlotIndex(kind, slot)) {
                continue;
            }
            target.setAttr(kind, slot, attr);
            target.setUploadBlocked(kind, slot, blocked != 0);
            applied++;
        }
        return applied;
    }

    /**
     * ★R91-⑤ 的 {@code FLG} 支本体（服务端唯一迁移点）：把手势字母作用到该格当前的 attr / P 上。
     * <p>
     * 五条纪律：
     * <ol>
     * <li>区域字母不认识 / 槽号越出白名单 / 手势字母不在白名单 ⇒ {@link Outcome#REJECTED}；</li>
     * <li>迁移后与迁移前<b>完全相同</b> ⇒ {@link Outcome#UNCHANGED}（★不写档、不刷虚化，与 SET/CLR
     * 的同一条脏标记纪律）；</li>
     * <li>★<b>attr 换到 {@code NONE} 时随解绑一起撤载荷</b>（撤销 = 回到"普通格"，留一条载荷就是
     * 玩家眼里的"我没清掉的需求"）；attr <b>换档</b>（BIND↔MEMORY）时<b>载荷不换</b>；</li>
     * <li>{@code P} 支<b>永不</b>碰 attr、也永不碰载荷（正交位；"alt 右键的锁格子不携带内容"）；</li>
     * <li>进入 BIND / MEMORY 且本格<b>还没有载荷</b>时，先向 {@link PayloadSource} 要一次该格的真实内容
     * ⇒ 有物就一次写全（attr + 载荷），空格就只挂状态（内容待 NEI 拖拽落成 = R91-b）。
     * ★{@code payloads == null}（两参/三参旧形态）⇒ 跳过这一步，既有回归用例行为逐字不变。</li>
     * <li>★<b>R91-h（硬校验，先问后写才拦得住）</b>：读回来的载荷键若能解成一条<b>合法声明</b>、
     * 其 kind 却 ≠ 本请求的区域字母 ⇒ 整条 {@link Outcome#REJECTED}（<b>不写</b> attr、<b>不写</b> 载荷、
     * <b>不猜</b>"把它翻译成本区域同号格"）。这是 R70 {@code CLR|0} bug 家族的形状：三个区的索引
     * 各自从 0 起，跨区合法键会落进"那个区同号的格" ⇒ attr 记在本格、载荷出现在别人区里，两张表各错一格。
     * ★解不出的杂键<b>不算</b>跨区（没有可比对的 kind，按纪律 5 停在 pending，attr 手势本身不被毁，
     * 见用例 {@code ghostFlagPayloadFromServerTruth} ③）；钉死这条的是
     * {@code ghost_flag_cross_region_key_rejected}。</li>
     * </ol>
     */
    private static Decision applyFlag(int slotIndex, String letter, String gesture, PocketFilterConfig filters,
        PocketEssenceStore essenceCells, PayloadSource payloads) {
        final PocketFilterConfig.Kind kind = kindOf(letter);
        if (kind == null || !PocketFilterConfig.isAllowedSlotIndex(kind, slotIndex) || !isKnownGesture(gesture)) {
            return REJECT;
        }
        final int currentAttr = filters.attrAt(kind, slotIndex);
        final boolean currentBlock = filters.uploadBlockedAt(kind, slotIndex);
        final int nextAttr = isAttrGesture(gesture) ? nextAttr(currentAttr, gesture) : currentAttr;
        final boolean nextBlock = nextUploadBlocked(currentBlock, gesture);
        if (nextAttr == currentAttr && nextBlock == currentBlock) {
            return new Decision(Outcome.UNCHANGED, kind, slotIndex);
        }
        if (isAttrGesture(gesture)) {
            // ★纪律 5 的"问"必须发生在纪律 6 的"判"之前、且两者都在<b>任何一个字节落下之前</b>——
            // 先写 attr 再发现载荷键跨区，就退化成"拦了一半"（位表已多一条，回滚又要碰第二处真相）。
            final String payloadKey = payloads != null && nextAttr != PocketConstants.GHOST_ATTR_NONE
                && filters.at(kind, slotIndex) == null ? payloadKeyQuietly(payloads, kind, slotIndex) : null;
            if (isCrossRegionPayload(kind, payloadKey)) {
                // ★R91-h：伪造跨区域键 ⇒ 整条拒收（不写、不猜；Decision 带回区域与格号供 L8 读数定位）
                return new Decision(Outcome.REJECTED, kind, slotIndex);
            }
            final boolean attrChanged = filters.setAttr(kind, slotIndex, nextAttr);
            boolean payloadDropped = false;
            if (nextAttr == PocketConstants.GHOST_ATTR_NONE) {
                // 纪律 3：撤属性 = 这一格不再是"某条需求" ⇒ 载荷一起撤（P 位刻意保留，它有自己的手势）
                payloadDropped = filters.removeAt(kind, slotIndex);
            } else if (payloadKey != null && !payloadKey.isEmpty()) {
                // ★纪律 5 的"写"仍走 applySet 单点：载荷键合法性、源质归属对账与建档 assignCell 都只有一套
                // 判据（这里绕过去就是第二处真相）；applySet 自己拒收 ⇒ 本格停在 pending（★服务端不猜）。
                applySet(slotIndex, payloadKey, filters, essenceCells);
            }
            if (!attrChanged && !payloadDropped && filters.at(kind, slotIndex) == null) {
                // ★兜底读数：位表说"没变"且这一格既没载荷也没被撤掉什么 ⇒ UNCHANGED（不写档、不刷虚化）。
                // 正常迁移路径在上面那条等值判断就返回了，走到这里只有"位表实现与真值表不一致"这一种可能
                // ⇒ 这条分支是<b>防写歪</b>的，不是装饰（同 #applyCap 那条"写完再复核"的纪律）。
                return new Decision(Outcome.UNCHANGED, kind, slotIndex);
            }
        } else {
            filters.setUploadBlocked(kind, slotIndex, nextBlock);
        }
        return new Decision(Outcome.APPLIED, kind, slotIndex);
    }

    /**
     * ★R91-h 的"问"半边：内容读取要碰 ItemStack / 注册表，桩件与真实档都可能抛 ⇒ 拿不到就当
     * "没有载荷可读"（纪律 5 自然停在 pending，★属性手势本身不因读数口故障被毁，也不崩服务端）。
     */
    private static String payloadKeyQuietly(PayloadSource payloads, PocketFilterConfig.Kind kind, int slotIndex) {
        try {
            return payloads.payloadKeyAt(kind, slotIndex);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * ★★<b>R91-h（阻断级落码）</b>：载荷键是否是一条"解得开、但 kind 与手势所在区域不一致"的<b>跨区域键</b>。
     * <p>
     * 三区索引各自从 0 起（中栏 0…134 / 流体槽 0…17 / 源质格 0…71），{@link #applySet} 的 kind 由
     * 载荷键自己的前缀决定 ⇒ 不加这道硬校验，一条"别区的合法键"会被完好地写进<b>那个区</b>同号格
     * （R70 的原教训：伪造一条 {@code CLR|0} 就能撤掉不相干区域的 ghost ⇒ 测试假绿）。
     * 生产读数口 {@code NekoPocketServerHandler#ghostPayloadAt} 按 kind 分三支读真值 ⇒ 正常路径给不出
     * 跨区键；被拦下的只有伪造/写歪的 {@link PayloadSource}，而校验放在这里（★不是只信读数口）才是
     * "区域字母 ↔ kind"的<b>唯一</b>执法点——所有注入实现都得过这道门，不许各区调用方各判一遍。
     *
     * @return {@code true} ⇒ 键解得开且属于别的区域（调用方整条拒收）；空键 / 解不开 ⇒ {@code false}
     *         （没有可比对的 kind，走纪律 5 的 pending 口径）
     */
    private static boolean isCrossRegionPayload(PocketFilterConfig.Kind kind, String payloadKey) {
        if (payloadKey == null || payloadKey.isEmpty()) {
            return false;
        }
        final PocketFilterConfig.Filter parsed = PocketFilterConfig.parseKey(payloadKey);
        return parsed != null && parsed.kind() != kind;
    }

    /** ★CLR：三段齐（第三段是区域字母）才动；越界或字母不认识一律拒收。 */
    private static Decision applyClear(String[] parts, int slotIndex, PocketFilterConfig filters) {
        final PocketFilterConfig.Kind kind = kindOf(parts[2]);
        if (kind == null || !PocketFilterConfig.isAllowedSlotIndex(kind, slotIndex)) {
            return REJECT;
        }
        return new Decision(filters.removeAt(kind, slotIndex) ? Outcome.APPLIED : Outcome.UNCHANGED, kind, slotIndex);
    }

    /**
     * ★CAP（R83 C2）：把绝对值收口后落到<b>已有</b>声明上。
     * <p>
     * 三条拒收线，一条比一条容易静默：① 区域字母不认识 / 槽号越出该区域白名单；② 值解不出整数
     * 或为负；③ ★<b>那一格根本没有声明</b>——没有声明就没有"这一条的上限"可言，此时绝不能顺手
     * 建一条空声明（那会让"上限"变成第二种声明入口，绕开载荷键的合法性判定）。
     * <p>
     * 服务端<b>只</b>做 {@link #clampCap} 的区间收口，不重算步进：步进表在 {@link #nudgedCap} 里只有一份，
     * 由发起方（客户端读自己那份镜像）调用；物品支的自然满量只有拿着 {@code ItemStack} 的一端知道。
     */
    private static Decision applyCap(int slotIndex, String letter, String rawValue, PocketFilterConfig filters) {
        final PocketFilterConfig.Kind kind = kindOf(letter);
        if (kind == null || !PocketFilterConfig.isAllowedSlotIndex(kind, slotIndex)) {
            return REJECT;
        }
        final PocketFilterConfig.Filter current = filters.at(kind, slotIndex);
        if (current == null) {
            return new Decision(Outcome.REJECTED, kind, slotIndex);
        }
        final int requested;
        try {
            requested = Integer.parseInt(rawValue);
        } catch (NumberFormatException ignored) {
            return REJECT;
        }
        if (requested < PocketConstants.FILTER_CAP_MIN) {
            // ★0 与负数一律拒收而不是"悄悄抬到 1"：0 的语义是"这一条不拉了"，而本仓的口径是
            // "不想拉就右键解绑"（extract 对 count<=0 走 NO_CHANNEL 分支，会把玩家自己的调整显示成
            // "通道失联" = 撒谎）。下界因此是硬门，不是钳位目标。
            return REJECT;
        }
        final int ceiling = ceilingOf(kind);
        final int next = clampCap(requested, ceiling);
        if (next == current.cap()) {
            return new Decision(Outcome.UNCHANGED, kind, slotIndex);
        }
        filters.add(slotIndex, current.withCap(next));
        final PocketFilterConfig.Filter now = filters.at(kind, slotIndex);
        if (now == null || now.cap() != next) {
            return new Decision(Outcome.REJECTED, kind, slotIndex);
        }
        return new Decision(Outcome.APPLIED, kind, slotIndex);
    }

    /**
     * ★SET：kind 由<b>解出来的载荷类型</b>给出（流体条落 FLUID 空间、源质格落 ESSENCE 空间），不按"只有物品"一刀切。
     * <p>
     * ★R90 E3（D3）<b>源质格的归属对账</b>（{@code essenceCells != null} 时，生产必非空）：
     * <ul>
     * <li><b>有归属格</b>（{@code tagAtCell} 非空）且载荷 tag <b>不匹配</b> ⇒ <b>REJECTED</b>——旧口径
     * 只校验槽号白名单 + 载荷非空，伪造 C2S 能把任意 tag 写上别人的格；新语义下声明必须与格位归属
     * 同源成立（合法客户端本来也只能发出"与本格 tag 匹配"的载荷，被拒的只有伪造包）；</li>
     * <li><b>无归属格</b>（{@code cellTag == null/空}）且载荷 tag 非空 ⇒ <b>放行建档</b>：写声明之外还
     * {@link PocketEssenceStore#assignCell(String)} 占格（幂等：该 tag 已有格则落回原格；占的是
     * <b>最小空位</b>——与"格序 = 首次入账顺序"同一条既定口径，不一定是被拖的那一格）。R86"空格
     * 拒收"由此收窄为"空格只收<b>自带可读 tag 的拖入物</b>"（无 NBT 裸栈 / 裸晶在客户端
     * {@code carriesTag} 那一关就到不了这里，见 {@code NekoEssenceGhostCell#tagOfCarrier}）。</li>
     * </ul>
     * 建档的占格发生在<b>声明写入并复核成功之后</b>（顺序即纪律：先写字档后占格，失败路径不留
     * "占了格却没声明"的半档；本分支里占格结构上不可能失败——被拖的格无归属 ⇒ 至少它自己是空位）。
     */
    private static Decision applySet(int slotIndex, String payloadKey, PocketFilterConfig filters,
        PocketEssenceStore essenceCells) {
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
        final boolean essenceRecheck = kind == PocketFilterConfig.Kind.ESSENCE && essenceCells != null;
        final String cellOwner = essenceRecheck ? essenceCells.tagAtCell(slotIndex) : null;
        if (essenceRecheck && cellOwner != null
            && !(parsed instanceof PocketFilterConfig.EssenceFilter essence && cellOwner.equals(essence.tag))) {
            // 有归属但 tag 不匹配 ⇒ 拒（伪造缝隙闭合；合法客户端发不出这种载荷）
            return new Decision(Outcome.REJECTED, kind, slotIndex);
        }
        final PocketFilterConfig.Filter before = filters.at(kind, slotIndex);
        // ★同槽覆盖必须把<b>已调好的上限</b>带过去：载荷键一样就是"还是这一条需求"，玩家滚出来的
        // 数值不该因为"又拖了一次同一个东西"而被抹回默认值（抹了就是"我调的上限悄悄没了"）。
        final PocketFilterConfig.Filter rebuilt = rebuildAt(
            kind,
            slotIndex,
            parsed,
            before == null ? PocketConstants.FILTER_CAP_UNSET : before.cap());
        filters.add(slotIndex, rebuilt);
        final PocketFilterConfig.Filter now = filters.at(kind, slotIndex);
        if (now == null || !now.key()
            .equals(rebuilt.key())) {
            // add 的 false 有两种含义（同槽覆盖 / 越界或载荷空），故以"该格现在到底是不是这条"为准
            return new Decision(Outcome.REJECTED, kind, slotIndex);
        }
        if (essenceRecheck && cellOwner == null) {
            // ★D3 建档：声明已写好且复核过，才占格（幂等；返回 -1 = 72 格全满 ⇒ 撤回刚写的声明并拒）
            final String declaredTag = ((PocketFilterConfig.EssenceFilter) parsed).tag;
            if (essenceCells.assignCell(declaredTag) < 0) {
                filters.removeAt(kind, slotIndex);
                return REJECT;
            }
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
     * <p>
     * 三参形态给"载荷键里本来就没有上限"的解析路径（{@link PocketFilterConfig#parseKey}）用 ⇒ 上限
     * 一律 {@link PocketConstants#FILTER_CAP_UNSET}；带已有声明的场合必须走四参形态把上限带上
     * （见 {@link #applySet}）。
     */
    public static PocketFilterConfig.Filter rebuildAt(PocketFilterConfig.Kind kind, int slotIndex,
        PocketFilterConfig.Filter payload) {
        return rebuildAt(kind, slotIndex, payload, PocketConstants.FILTER_CAP_UNSET);
    }

    /** ★R83 C2：同 {@link #rebuildAt(Kind, int, PocketFilterConfig.Filter)}，但显式给出组上限。 */
    public static PocketFilterConfig.Filter rebuildAt(PocketFilterConfig.Kind kind, int slotIndex,
        PocketFilterConfig.Filter payload, int cap) {
        if (kind == null || payload == null) {
            return null;
        }
        switch (kind) {
            case ITEM:
                if (!(payload instanceof PocketFilterConfig.ItemFilter item)) {
                    return null;
                }
                return new PocketFilterConfig.ItemFilter(slotIndex, item.itemId, item.meta, item.nbtString, cap);
            case FLUID:
                if (!(payload instanceof PocketFilterConfig.FluidFilter fluid)) {
                    return null;
                }
                return new PocketFilterConfig.FluidFilter(slotIndex, fluid.fluidName, cap);
            case ESSENCE:
                if (!(payload instanceof PocketFilterConfig.EssenceFilter essence)) {
                    return null;
                }
                return new PocketFilterConfig.EssenceFilter(slotIndex, essence.typeId, essence.tag, cap);
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ ★R83 C2：组上限的步进与收口（唯一一份算式）

    /**
     * 一类声明"滚一格"的步进量（★数值全部来自 {@link PocketConstants}，本方法只做分派）。
     * <p>
     * 用户原话：「物品是每次 1 个；流体是每次调整 1%；源质是每次 1 个」⇒
     * 物品 {@link PocketConstants#FILTER_CAP_STEP_ITEM}、流体
     * {@link PocketConstants#FILTER_CAP_STEP_FLUID}（= 单 tank 容量 / 100 = 160,000 mB，
     * D-6 裁定的分母口径）、源质 {@link PocketConstants#FILTER_CAP_STEP_ESSENCE}。
     */
    public static int stepOf(PocketFilterConfig.Kind kind) {
        if (kind == null) {
            return PocketConstants.FILTER_CAP_MIN;
        }
        return switch (kind) {
            case ITEM -> PocketConstants.FILTER_CAP_STEP_ITEM;
            case FLUID -> PocketConstants.FILTER_CAP_STEP_FLUID;
            case ESSENCE -> PocketConstants.FILTER_CAP_STEP_ESSENCE;
        };
    }

    /**
     * 一类声明在<b>服务端收口</b>时允许的上界。
     * <p>
     * 流体 / 源质是具名常量（就是这一类今天的自然满量）；★物品的上界<b>不是</b>常量而是该物品自己的
     * {@code maxStackSize} —— 那只有拿着样本栈的一端（客户端显示、以及 {@code PocketAeChannelOps}
     * 抽出时的 {@code wanted} 栈）解得出，纯 JVM 件在这里猜一个数就是第二处真相。
     * ⇒ 物品支的区间只保证下界，真正的"不超过一叠"由消费侧 {@code min(cap, maxStackSize)} 收口
     * （Ops 待办 O-1）。
     */
    public static int ceilingOf(PocketFilterConfig.Kind kind) {
        if (kind == null) {
            return PocketConstants.FILTER_CAP_MIN;
        }
        return switch (kind) {
            case ITEM -> PocketConstants.FILTER_CAP_CEILING_ITEM_SERVER;
            case FLUID -> PocketConstants.FILTER_CAP_CEILING_FLUID;
            case ESSENCE -> PocketConstants.FILTER_CAP_CEILING_ESSENCE;
        };
    }

    /** 区间收口：下界 {@link PocketConstants#FILTER_CAP_MIN}、上界由调用方给出（★不四舍五入、不取模）。 */
    public static int clampCap(int value, int ceiling) {
        return Math
            .min(Math.max(PocketConstants.FILTER_CAP_MIN, value), Math.max(PocketConstants.FILTER_CAP_MIN, ceiling));
    }

    /**
     * ★<b>步进的唯一落点</b>（判据 4 的"分流点"）：从当前值走 {@code steps} 格，返回收口后的新值。
     * <p>
     * 未设过的声明（{@link PocketConstants#FILTER_CAP_UNSET}）从"现全局量"起步 ⇒ 第一次往下滚就是
     * {@code 现全局量 - 步进}，与右上角那一刻显示的默认读数<b>连续</b>（不会出现"显示 16M，滚一下变成
     * 160,000"这种把默认值当 0 读的形状）。
     * <p>
     * {@code steps} 的符号 = 方向（滚轮 UP 为正）、绝对值 = 倍率（alt 为 1、alt+ctrl 为
     * {@link PocketConstants#FILTER_CAP_FAST_MULTIPLIER}）⇒ D-6 的"×10 是步进倍率"在这里、且只在这里兑现：
     * 通道节拍（{@code CHANNEL_TICK_PERIOD} / {@code ticksToSecondsCeil} /
     * {@code Config.pocketChannelPairsPerSecond}）一个字都不动。
     *
     * @param kind    声明区域（决定步进）
     * @param current 当前<b>原始</b>值（可为 {@link PocketConstants#FILTER_CAP_UNSET}）
     * @param ceiling 这一格的上界：调用方经 {@link PocketFilterConfig#defaultCap} 现算（物品 = 该物品的
     *                {@code maxStackSize}、流体 = 16M、源质 = 64），★不在本方法里再猜一遍
     * @param steps   带符号的格数
     */
    public static int nudgedCap(PocketFilterConfig.Kind kind, int current, int ceiling, int steps) {
        if (kind == null) {
            return PocketConstants.FILTER_CAP_UNSET;
        }
        final int from = current == PocketConstants.FILTER_CAP_UNSET ? ceiling : current;
        return clampCap(from + steps * stepOf(kind), ceiling);
    }

    /**
     * ★<b>alt+滚轮 与 alt+ctrl+滚轮 的分流点</b>（判据 4）：把"方向 + 按住的修饰键"折成带符号的格数。
     * <p>
     * 方向取 {@link UpOrDown#modifier}（UP = +1、DOWN = −1）；倍率只有两档 —— 按住 ctrl 是
     * {@link PocketConstants#FILTER_CAP_FAST_MULTIPLIER}，否则 1 格。★这里出现 {@code 1} 不是"另一处步进"，
     * 它就是"不乘"这一件事的写法。
     */
    public static int capSteps(UpOrDown scrollDirection, boolean fast) {
        final int sign = scrollDirection == null ? 0 : scrollDirection.modifier;
        return sign * (fast ? PocketConstants.FILTER_CAP_FAST_MULTIPLIER : 1);
    }

    /**
     * 客户端格件的一次到位算式：{@code (当前原始值, 区域, 本格的天然满量, 滚轮方向, 是否 ctrl)} →
     * 要发给服务端的新上限（绝对值）。
     * <p>
     * 三个格件（中栏 / 流体槽 / 源质格）各自只负责"我是哪一类、我的天然满量是多少、玩家滚了几格"，
     * 换算一律走这里 ⇒ 步进表、下界、上界与 ×10 都只有一份（D-6）。
     *
     * @param itemMaxStackSize 物品支该物品的堆叠上限（其余两支传 0 也行：{@link PocketFilterConfig#defaultCap}
     *                         只在 {@link PocketFilterConfig.Kind#ITEM} 一支读它）
     */
    public static int nextCap(PocketFilterConfig.Kind kind, int currentRawCap, int itemMaxStackSize,
        UpOrDown scrollDirection, boolean fast) {
        return nudgedCap(
            kind,
            currentRawCap,
            PocketFilterConfig.defaultCap(kind, itemMaxStackSize),
            capSteps(scrollDirection, fast));
    }

    // ------------------------------------------------------------------ ★R83 C2：读数的缩写与右上角落点（三类共用一份）

    /** 读数缩放（与面板其余 0.5 缩放的读数同口径；★不是新贴图尺寸，只是字号）。 */
    public static final float CAP_READOUT_SCALE = 0.5f;
    /** 读数与格内的内缩边距：与三类虚像遮罩的 {@code drawRect(1, 1, w-2, h-2)} 同一个数。 */
    public static final int CAP_READOUT_MARGIN = 1;
    /** 读数的纵向落点（★上半带：库把数量/容量文字画在 BottomRight，三类都留了 y 0…7 这一条）。 */
    public static final float CAP_READOUT_TOP = 2f;
    /** vanilla 字体里<b>最宽</b>字符的像素宽（缩放前）。★只用于估宽，见 {@link #capReadoutWidth}。 */
    private static final float CAP_GLYPH_MAX_PX = 6f;

    /**
     * 读数控色 = 库里的橙色（用户原话"右上角会显示组上限（橙色）"）。
     * <p>
     * ★写成方法而不是 {@code static final int}：{@code Color} 的类初始化会牵进 {@code ModularUI} 主类，
     * 放进静态字段就让本类（被零依赖套件直调的纯 JVM 件）在测试 JVM 里初始化即炸。方法体只在客户端
     * 绘制路径上被调 ⇒ 与三类格件现有的 {@code GuiDraw} 调用同一层级。
     */
    public static int capReadoutColor() {
        return Color.ORANGE.main;
    }

    /**
     * 读数字宽（★保守估计：按最宽字符算再乘缩放，宁可估宽让数字整体左移，也不估窄把尾巴裁掉）。
     * <p>
     * 不直读 {@code fontRenderer} 的理由：那要给本类 import {@code Minecraft}（客户端类），而
     * {@code gui/pocket} 这一层是双端同树的，本仓现有口袋格件里 {@code Minecraft} 引用数为 0。
     * 真实像素是否恰好不压线属実机项（已进"只能实机"清单）。
     */
    public static float capReadoutWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        return text.length() * CAP_GLYPH_MAX_PX * CAP_READOUT_SCALE;
    }

    /**
     * 右上角横向落点（右对齐）：{@code 格宽 - 内缩 - 文字宽}。
     * <p>
     * 单独成纯函数的理由与 {@link #stepOf} 同一条——三类的绘制点各在三个文件里，几何口径写三遍迟早有一遍
     * 少减一个像素（压到边上就是"数字被裁一半"）。文字宽度由 {@link #capReadoutWidth} 给出（同一份估算），
     * 本函数不碰任何 MC / widget 类型 ⇒ 零依赖套件能钉住这条算式。
     */
    public static float capReadoutX(int cellWidth, float textWidth) {
        return cellWidth - CAP_READOUT_MARGIN - textWidth;
    }

    /**
     * 组上限的玩家可见读数：{@code 16000000 → "16M"}、{@code 160000 → "160K"}、{@code 64 → "64"}。
     * <p>
     * ★存在的理由是像素而非观感：格内可写的净空只有 16 px 上下（{@code NekoPocketBottomBand} 那一条
     * "86 ÷ 0.5 = 172 逻辑像素"的算术口径），把 8 位数原样画进右上角就是把读数画成一条糊线。
     * 缩写只走整数与一位小数（★不引 {@code DecimalFormat}，免得区域设置把小数点写成逗号、再与
     * {@code PocketFilterConfig} 的 ':' 分段和 blob 的 '|' 分段打架）。
     * <p>
     * ★只有"给玩家看"这一支用它；落档与服务端读的都是<b>原始整数</b>（缩写不是可逆形状，
     * 拿它当第二份真相迟早出事）。
     */
    public static String capReadout(int cap) {
        if (cap < 0) {
            return "";
        }
        if (cap >= CAP_READOUT_BILLION) {
            return scaledSuffix(cap, CAP_READOUT_BILLION, "G");
        }
        if (cap >= CAP_READOUT_MILLION) {
            return scaledSuffix(cap, CAP_READOUT_MILLION, "M");
        }
        if (cap >= CAP_READOUT_THOUSAND) {
            return scaledSuffix(cap, CAP_READOUT_THOUSAND, "K");
        }
        return Integer.toString(cap);
    }

    /** 缩写阶梯的三档（★"1G" 那档常态用不到 —— 上界最大就是流体的 16M；留着是为了伪造包把值推到天上去时右上角仍然只有 4 个字符）。 */
    private static final int CAP_READOUT_THOUSAND = 1_000;
    private static final int CAP_READOUT_MILLION = 1_000_000;
    private static final int CAP_READOUT_BILLION = 1_000_000_000;

    /** 一档缩写的实际产出：能整除到一位小数就带一位，否则只取整数部分（★向下取整，不虚报"调大了"）。 */
    private static String scaledSuffix(int value, int divisor, String suffix) {
        final long tenths = value / (divisor / 10);
        final String head = tenths % 10 == 0 ? Long.toString(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
        return head + suffix;
    }

    // ------------------------------------------------------------------ ★R83 C2：ghost blob 记录的单源编解码

    /**
     * 一条声明的机读记录：{@code kind|slot|载荷键}，★上限已设时再挂第 4 段 {@code |cap}。
     * <p>
     * 未设时<b>不写</b>第 4 段（而不是写 {@code |-1}）：面板现有的解码器按"三段"切，多写一段会让它在
     * 主代理接线之前就把 {@code |-1} 当成载荷键尾段（虽然同批落地不会有这一刻，但"编码先上、
     * 解码后上"的中间态必须是<b>可运行</b>的）。
     * <p>
     * ★面板那两份（{@code NekoPocketPanel#ghostBlobOf} / {@code #parseGhostBlob}）应改调本方法与
     * {@link #filterOf}（Panel 待办 P-2）：编解码各留一份实现就会在同步里静默变形，R78 给源质 blob
     * 立的那条"单函数编解码"纪律在这里同样成立。
     */
    public static String recordOf(PocketFilterConfig.Filter filter) {
        if (filter == null) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        builder.append(filter.kind())
            .append(PocketConstants.GHOST_REQUEST_SEPARATOR)
            .append(filter.slotIndex())
            .append(PocketConstants.GHOST_REQUEST_SEPARATOR)
            .append(filter.key());
        if (filter.cap() != PocketConstants.FILTER_CAP_UNSET) {
            builder.append(PocketConstants.GHOST_REQUEST_SEPARATOR)
                .append(filter.cap());
        }
        return builder.toString();
    }

    /**
     * {@link #recordOf} 的解侧：三段（旧形状 / 未设上限）与四段（已设上限）都收；解不出 ⇒ {@code null}。
     * <p>
     * ★这里<b>不</b>复用面板那份 {@code split("\\|", 3)}：三段切法会把第 4 段整个并进载荷键里，
     * 于是上限永远读不上来（并且会造出一条载荷键尾部带 {@code |数字} 的假声明）。
     */
    public static PocketFilterConfig.Filter filterOf(String record) {
        if (record == null || record.isEmpty()) {
            return null;
        }
        final String[] parts = record.split(java.util.regex.Pattern.quote(PocketConstants.GHOST_REQUEST_SEPARATOR), 4);
        if (parts.length < 3) {
            return null;
        }
        final PocketFilterConfig.Kind kind;
        final int slotIndex;
        try {
            kind = PocketFilterConfig.Kind.valueOf(parts[0]);
            slotIndex = Integer.parseInt(parts[1]);
        } catch (RuntimeException ignored) {
            return null;
        }
        final PocketFilterConfig.Filter payload = PocketFilterConfig.parseKey(parts[2]);
        if (payload == null || payload.kind() != kind || !PocketFilterConfig.isAllowedSlotIndex(kind, slotIndex)) {
            return null;
        }
        int cap = PocketConstants.FILTER_CAP_UNSET;
        if (parts.length >= 4) {
            try {
                cap = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {
                return null;
            }
            if (cap < PocketConstants.FILTER_CAP_MIN) {
                // 服务端不会编出这种值（写档前就收口）；外来/陈旧 blob 里出现即整条丢弃，
                // 与面板那份解码器"解不出的条目直接丢弃、绝不炸"的既有口径同形
                return null;
            }
        }
        return rebuildAt(kind, slotIndex, payload, cap);
    }
}
