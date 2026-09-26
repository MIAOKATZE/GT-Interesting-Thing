package com.miaokatze.gtit.common.items.pocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 磁力升级（R95 {@code PocketUpgradeType#MAGNET}）的<b>三态名单数据层</b>（★R96 S7a）：
 * <b>无限制 / 白名单 / 黑名单</b> 一枚枚举 + 一份 {@code itemId + meta} 的条目表。
 * <p>
 * <b>★三态的读法（这条必须写在明面上，不许只靠实现隐含 —— 用例
 * {@code magnet_filter_three_state_cycle_keeps_entries} 与 S7b 的 tooltip 文案都引这一段）</b>：
 * <ol>
 * <li>{@link Mode#NONE}（<b>无限制</b>）= <b>名单原样保留在场，但一律放行</b>。切到无限制<b>不清</b>名单，
 * 切回白/黑名单条目还在（含跨存读档）。玩家观感是"开关松开了，清单还在我能接着改"。</li>
 * <li>{@link Mode#WHITELIST}（<b>白名单</b>）= <b>只吸名单里的</b>，名单外即使就落在同一个 AABB 也不吸。</li>
 * <li>{@link Mode#BLACKLIST}（<b>黑名单</b>）= <b>名单里的一律不吸</b>，其余照吸。</li>
 * </ol>
 * 空名单在白/黑档下的读法<b>不顺着"无限制"含糊</b>：白名单 + 空名单 = 什么都不吸（那是玩家显式选的姿态，
 * 与"没配过"可区分，因为"没配过"是 {@code NONE}）。
 * <p>
 * <b>★条目只到 {@code itemId + meta}，不做 NBT 敏感匹配</b>（写死前提 P-11）。两个理由各自成立：
 * ① 需求侧不需要按 NBT 区分（要的是"这种矿不吸"，不是"这一把附魔剑不吸"）；② 条目一旦嵌 NBT 就要背
 * {@code StringSyncValue} 的同步预算（见下面那段算术），而 {@code meta} 已经足够表达 GT 系里绝大多数
 * "同种不同档"的差异。⇒ {@link #parseEntry(String)} 见到带 NBT 尾段的关键<b>把尾段丢掉、只留身份</b>
 * （不是拒收：NEI 拖入递过来的就是 {@code PocketAeChannelOps#contentKey} 那份四段键，丢掉 NBT 尾段
 * 才是 P-11 的正解）。
 * <p>
 * <b>键式样</b>（{@link #itemKey(int, int)} / {@link #parseEntry(String)}）<b>复用
 * {@code PocketFilterConfig} 的 {@code itemKey/parseKey}（{@code :919-921}/{@code :871-897}）的形状，
 * 但刻意是 Kind-free 的</b>：
 * <ul>
 * <li>同一枚前缀 {@code 'i'} 与同一枚分隔符 {@code ':'}，写出形态与
 * {@code PocketFilterConfig.itemKey(id, meta, "")} <b>逐字符相同</b>（{@code i:<id>:<meta>:}，末尾那枚空
 * NBT 段照留）⇒ S7b 的拖入腿可以把现成的 {@code contentKey} / {@code itemKey} 直接喂进本类，不需要第三份键式样；</li>
 * <li>★<b>不</b>经 {@code PocketFilterConfig.Kind}，也★<b>不</b>给那枚枚举加第四值（爆炸半径实测 main 侧
 * 142 处 / 13 文件；而且名单条目根本没有"槽索引空间"这一层，套 Kind 是假归类）；</li>
 * <li>★<b>不</b>复用 {@code PocketFilterConfig.Filter} 那条 {@code slotIndex}/{@code cap} 的形状
 * （磁力名单既无槽位可占，也无可调上限，硬套会造出两格无意义的字段）。</li>
 * </ul>
 * <b>持久化</b>：根键 {@link PocketConstants#MAGNET_FILTER}（compound），条目落在
 * {@link PocketConstants#MAGNET_FILTER_LIST}（{@code NBTTagList of String}，★插入序 = S7b 的格序），
 * 三态落在 {@link PocketConstants#MAGNET_FILTER_MODE}（枚举名字符串，★{@code NONE} 不写键）。
 * <b>空态不占键</b>：{@code NONE} + 零条目 ⇒ {@code writeTo} 走 {@code removeTag}（R53c"读路径不建档"的写侧对偶，
 * 与 {@code PocketFilterConfig#writeTo} 的 cap/位表同一条纪律 ⇒ 从没配过名单的档字节形状与旧档相同）。
 * <p>
 * <b>★同步预算算术（给 S7b 决定载体，结论：不撞墙）</b>：{@code StringSyncValue} 的硬墙是
 * {@code Short.MAX_VALUE - 74} = <b>32693 字节</b>（{@code ModularUI2/StringSyncValue.java:51}），
 * 超限走 {@code NetworkUtils.writeStringSafe(..., crash=false)}（{@code :113-137}）⇒ <b>按字节腰切、只
 * WARN 不抛</b>，读侧 {@code toString(...,UTF_8)} 把断字符变成 U+FFFD（静默损坏）。本类的负载：
 * 条目上限 {@link PocketConstants#MAGNET_FILTER_SLOTS} = 72，单条 {@code i:<id>:<meta>:} 的字符数
 * 下界 5（{@code i:0:0:}）、上界 14（id 与 meta 各按 5 位算），加 NBT 字符串条目的定长开销后与取证档案
 * {@code r96-ret7.md §4.5} 给的 <b>864–1800 B</b> 同一量级 ⇒ <b>72 条满档 ≤ 1800 B，距 32693 有 ≥18 倍余量，
 * 用 StringSyncValue 装得下</b>。<b>唯一会撞墙的形态是往条目里嵌 base64 NBT</b>（P-11 已排除）；届时必须先
 * 自设预算并显式报"未同步条数"（照 {@link PocketConstants#GHOST_BLOB_MAX_CHARS} 那条纪律），不得静默。
 * <p>
 * <b>成本纪律（S6 那一条的延续，用例 {@code magnet_filter_one_scan_gate_per_scan} 钉住）</b>：
 * 执法侧每<b>扫</b>一次构造一次 {@link ScanGate}（一次集合复制），★禁止每个实体构造一次。
 * {@link ScanGate} 用 {@code Set<Long>}（{@code itemId} 与 {@code meta} 合成一个 long）而不是
 * {@code Set<String>} ⇒ 每实体那一步<b>零字符串分配、零拼接</b>，只做一次装箱查找。
 * {@link #newScanGate()} 顺手自增 {@link #scanGatesBuilt()} 这根<b>计数探针</b>（同
 * {@code PocketUpgradeGuards} 的探针口径），用例据此断言"构建次数 = 扫描次数"。
 * <p>
 * <b>纯 JVM 件</b>：照 {@code PocketFilterConfig} 的既有纪律 —— 只操作字符串/整数与 NBT 容器，
 * ★不 import 任何 MC 物品、AE2、TC 类型（{@code NBTTagCompound} 在未经 Forge 注册的 JVM 里可用，
 * 本套件的存档往返腿一直这么跑），物品 ↔ 键的那一步换算住在 {@code PocketMagnetDriver}（它本来就 import MC）。
 * <p>
 * <b>本轮本片不做</b>：72 格格件、NEI 拖入、背包拖入手势、面板挂载与 tooltip 落位全归 <b>S7b</b>
 * （它还要等主干 S2 的配置面板挂载点）；"吸取目标两档"（P-11 的 36 格 / 135 格栏）也不在本片。
 * 本类只保证：<b>名单与三态有唯一真相、能落档、能被执法</b>。
 */
public final class PocketMagnetFilter {

    /**
     * ★与 {@code PocketFilterConfig} 同一枚 logger 名字 {@code "gtit"}：不走 {@code GTInterestingThing.LOG}
     * 那条会把 mod 主类（{@code @Mod}/{@code Tags}）拉进本类类初始化链的路 —— 本类与它一起属"纯 JVM
     * 可实例化"的那一侧（{@code NekoPocketModelTest} 直接 new 它）。
     */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 分隔符与物品前缀：与 {@code PocketFilterConfig.SEPARATOR}/{@code ITEM_PREFIX} <b>同值同义</b>（Kind-free 复用，见类注释）。 */
    private static final char SEPARATOR = ':';
    private static final String ITEM_PREFIX = "i";
    /** NBT tag id：compound(10) 与 string(8)（同 {@code PocketFilterConfig} 的裸数字纪律，不引 MC 常量类）。 */
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_STRING = 8;

    /** 读档丢弃条目的一次性 WARN 闩（★进程维，同 {@code PocketFilterConfig#droppedWarnedKeys} 的既存弱点与理由）。 */
    private static final Set<String> droppedWarnedKeys = new LinkedHashSet<>();

    /**
     * 三态。★序就是循环序：{@code NONE → WHITELIST → BLACKLIST → NONE}（面板上那一枚循环按钮的落点）。
     * <b>{@code NONE} 是"名单在场但不生效"，不是"名单被清空"</b> —— 见类注释第 1 条。
     */
    public enum Mode {
        /** 无限制：全部放行（★名单仍保留在场，只是不被问）。 */
        NONE,
        /** 白名单：只吸名单内的。 */
        WHITELIST,
        /** 黑名单：名单内的一律不吸。 */
        BLACKLIST;

        /** 循环到下一态（★三态用 {@code enum + next()}，不留第二份"下一个是什么"的表）。 */
        public Mode next() {
            final Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        /** 按枚举名安全解析：{@code null}/空/不认识 ⇒ {@code null}（★不回落成"随便一个态"，由调用方按缺键 = {@link #NONE} 处理）。 */
        public static Mode of(String name) {
            if (name == null || name.isEmpty()) {
                return null;
            }
            for (Mode candidate : values()) {
                if (candidate.name()
                    .equals(name)) {
                    return candidate;
                }
            }
            return null;
        }
    }

    /** 当前三态（默认 {@link Mode#NONE} = 旧档/没配过的天然读法）。 */
    private Mode mode = Mode.NONE;
    /**
     * 条目表：{@code (itemId, meta)} 合成一个 long，★{@link LinkedHashSet} 保插入序
     * （S7b 的 72 格按这份顺序渲染，所以<b>不得</b>换成 {@code HashSet}；同
     * {@code PocketCellBindings} 的"绑定序 = 轮转外层序"纪律）。
     */
    private final Set<Long> entries = new LinkedHashSet<>();
    /** 上一次 {@link #readFrom} 丢掉了几条（外来/陈旧档；★用例读它，日志本身只一次性 WARN）。 */
    private int readDropped;
    /** {@link #newScanGate()} 的构建次数（成本探针，热路径只 ++int）。 */
    private int scanGatesBuilt;

    /** 当前的三态。 */
    public Mode mode() {
        return mode;
    }

    /**
     * 循环按钮的服务端读数：下一个态是什么（★客户端只发"我点了一次"，目标态由本类推 ⇒ 伪造包最多把
     * 自己的名单在三态里轮一圈，改不到别人的口袋，也改不出第四种态）。
     */
    public Mode nextMode() {
        return mode.next();
    }

    /**
     * 写口（三态）：照 {@code PocketFilterConfig#setAttr}（{@code :289-300}）的形状。
     *
     * @return 本次是否<b>真的</b>改变（同值 / {@code null} ⇒ {@code false} ⇒ 调用方不写档、不刷虚化）
     */
    public boolean setMode(Mode next) {
        if (next == null || next == mode) {
            return false;
        }
        mode = next;
        return true;
    }

    /**
     * 推进到下一态（{@link #nextMode()} + {@link #setMode(Mode)} 的合成，★只有这一处用到"循环"这件事）。
     *
     * @return 本次是否真的改变（三态互不相同 ⇒ 真按一次按钮必为 {@code true}）
     */
    public boolean cycleMode() {
        return setMode(nextMode());
    }

    /** 条目数（≤{@link PocketConstants#MAGNET_FILTER_SLOTS}）。 */
    public int size() {
        return entries.size();
    }

    /** 名单是否一条都没有（★与"三态是 NONE"是两件事：{@code NONE} + 有条目 是合法且常见的档）。 */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 本端是否<b>一个字节都不必落档</b>（{@code NONE} 且零条目 ⇒ {@code writeTo} 走 {@code removeTag}）。 */
    public boolean isDefaultShape() {
        return mode == Mode.NONE && entries.isEmpty();
    }

    /**
     * 写口（加一条）。
     *
     * @return 本次是否真的改变；<b>重复条目</b>与<b>越出 72 格预算</b>都返 {@code false}（★不是抛，也不静默塞）
     */
    public boolean addEntry(int itemId, int meta) {
        if (!isLegalIdentity(itemId)) {
            return false;
        }
        // ★预算闸排在 add 之前：满了就不再改集合（不是"先塞进去再裁剪"，那会让插入序随越界条目抖动）
        if (!entries.contains(composite(itemId, meta)) && entries.size() >= PocketConstants.MAGNET_FILTER_SLOTS) {
            return false;
        }
        return entries.add(Long.valueOf(composite(itemId, meta)));
    }

    /** 写口（加一条，键式样入参 = S7b 的拖入腿与 FLG 记录腿共用的那一枚）。解不出 ⇒ {@code false}。 */
    public boolean addEntryKey(String key) {
        final long[] parsed = parseEntry(key);
        return parsed != null && addEntry((int)parsed[0], (int)parsed[1]);
    }

    /**
     * 写口（摘一条）。
     *
     * @return 本次是否真的改变（不在名单里 ⇒ {@code false}）
     */
    public boolean removeEntry(int itemId, int meta) {
        return entries.remove(Long.valueOf(composite(itemId, meta)));
    }

    /** 写口（摘一条，键式样入参）。解不出 ⇒ {@code false}。 */
    public boolean removeEntryKey(String key) {
        final long[] parsed = parseEntry(key);
        return parsed != null && removeEntry((int)parsed[0], (int)parsed[1]);
    }

    /** 写口（按格号摘，S7b 的"右键解绑某一格"）：越界 ⇒ {@code false}。 */
    public boolean removeAt(int index) {
        if (index < 0 || index >= entries.size()) {
            return false;
        }
        int cursor = 0;
        for (Long holder : entries) {
            if (cursor++ == index) {
                return entries.remove(holder);
            }
        }
        return false;
    }

    /**
     * 写口（清名单，★<b>不动三态</b>）：与"切到无限制"是两件事 —— 后者保留条目（类注释第 1 条），
     * 这一条才是真的把条目抹掉。
     *
     * @return 本次是否真的改变
     */
    public boolean clearEntries() {
        if (entries.isEmpty()) {
            return false;
        }
        entries.clear();
        return true;
    }

    /**
     * 执法判据（★单件直问，给非扫描路径与用例用；扫描路径走 {@link #newScanGate()}）。
     * <p>
     * 三态读法逐字照类注释：{@code NONE} ⇒ 恒真（名单在场但全部放行）；{@code WHITELIST} ⇒ 名单内才真；
     * {@code BLACKLIST} ⇒ 名单内即假。
     */
    public boolean allows(int itemId, int meta) {
        return allowsMode(mode, itemId, meta, entries);
    }

    /** 三态判据本体（★唯一一份：{@link #allows(int, int)} 与 {@link ScanGate#allows(int, int)} 都走它，
     * 不留第二份读法 —— 第二份迟早只更新一处，那是"名单生效与否"最容易静默分叉的地方）。 */
    private static boolean allowsMode(Mode mode, int itemId, int meta, Set<Long> keys) {
        switch (mode) {
            case WHITELIST:
                return keys.contains(Long.valueOf(composite(itemId, meta)));
            case BLACKLIST:
                return !keys.contains(Long.valueOf(composite(itemId, meta)));
            default:
                // ★NONE = 无限制：名单原样在场，但一律放行（类注释三态读法第 1 条）
                return true;
        }
    }

    /** 条目的<b>插入序</b>快照（S7b 渲染 72 格与"按格号解绑"的读数源；★不暴露可写集合）。 */
    public List<String> entryKeys() {
        final List<String> out = new ArrayList<>(entries.size());
        for (Long holder : entries) {
            out.add(itemKey(itemIdOf(holder.longValue()), metaOf(holder.longValue())));
        }
        return out;
    }

    /** 第 {@code index} 格的键（越界 ⇒ {@code null}）。 */
    public String entryKeyAt(int index) {
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        int cursor = 0;
        for (Long holder : entries) {
            if (cursor++ == index) {
                return itemKey(itemIdOf(holder.longValue()), metaOf(holder.longValue()));
            }
        }
        return null;
    }

    /** 上一次读档丢掉了几条（★用例外加一条正控读数：畸形条目不抛、不静默吞）。 */
    public int readDropped() {
        return readDropped;
    }

    /**
     * ★成本探针：本 filter 被构造过多少个 {@link ScanGate}。用例据此断言"每扫一次一个集合"
     * （构建次数 = 扫描次数，而不是实体数）。
     */
    public int scanGatesBuilt() {
        return scanGatesBuilt;
    }

    /**
     * 一次扫描的判定上下文：<b>每扫一次构造一个，★禁每实体构造</b>（S6 的成本纪律在此延续）。
     * <p>
     * 构造 = 一次三态读数 + 一次集合复制（{@code NONE} 或空名单 ⇒ {@link Collections#emptySet()}，
     * 连复制都省）。构造之后整轮扫描不再碰本 filter，也不再碰 NBT。
     */
    public ScanGate newScanGate() {
        scanGatesBuilt++;
        return new ScanGate(mode, entries.isEmpty() || mode == Mode.NONE
            ? Collections.<Long>emptySet()
            : new LinkedHashSet<>(entries));
    }

    /** {@link #newScanGate()} 的产物：一份<b>已经定型</b>的三态 + 集合快照（★整轮扫描零再构建）。 */
    public static final class ScanGate {

        private final Mode mode;
        private final Set<Long> keys;

        private ScanGate(Mode mode, Set<Long> keys) {
            this.mode = mode;
            this.keys = keys;
        }

        /** 快照里的条目数（★用例读它确认"集合真的被带走了"，而不是每实体再回表查）。 */
        public int size() {
            return keys.size();
        }

        /** 该不该吸这一件（判据与 {@link PocketMagnetFilter#allows(int, int)} <b>同源同一份函数</b>）。 */
        public boolean allows(int itemId, int meta) {
            return allowsMode(mode, itemId, meta, keys);
        }
    }

    // ------------------------------------------------------------------ 键式样（Kind-free，与 PocketFilterConfig 同形）

    /**
     * 物品的稳定键：{@code i:<itemId>:<meta>:} —— 与 {@code PocketFilterConfig.itemKey(id, meta, "")}
     * <b>逐字符相同</b>（末尾空 NBT 段照留 ⇒ 两边互通），但★本类不引 {@code Kind}。
     */
    public static String itemKey(int itemId, int meta) {
        return ITEM_PREFIX + SEPARATOR + itemId + SEPARATOR + meta + SEPARATOR;
    }

    /**
     * 键 → {@code {itemId, meta}}；★解不出（前缀不对 / 段数不足 / 非数字 / 负 id）⇒ {@code null}。
     * <p>
     * ★<b>带 NBT 尾段的键不算畸形</b>：P-11 不做 NBT 敏感匹配 ⇒ 尾段被<b>丢掉</b>，身份仍成立
     * （NEI 拖入递来的 {@code contentKey} 就是四段形态，这条读法让 S7b 不需要再拆一次）。
     */
    public static long[] parseEntry(String key) {
        if (key == null || key.length() < 5) {
            return null;
        }
        final int first = key.indexOf(SEPARATOR);
        if (first < 0 || !ITEM_PREFIX.equals(key.substring(0, first))) {
            return null;
        }
        final int second = key.indexOf(SEPARATOR, first + 1);
        if (second < 0) {
            return null;
        }
        try {
            final int itemId = Integer.parseInt(key.substring(first + 1, second));
            // ★第三段只取到下一个分隔符为止 ⇒ 后面是否还有 NBT 段与本题无关（丢掉尾段，不丢身份）
            final int tail = key.indexOf(SEPARATOR, second + 1);
            final int meta = Integer.parseInt(tail < 0 ? key.substring(second + 1) : key.substring(second + 1, tail));
            return isLegalIdentity(itemId) ? new long[] { itemId, meta } : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** ★身份合法线：{@code itemId} 必须非负（{@code Item.getIdFromItem(null)} 给 0，负数只能是外来档）。 */
    private static boolean isLegalIdentity(int itemId) {
        return itemId >= 0;
    }

    /** 两个 int 合成一个 long（★每实体那一步零字符串分配）。 */
    private static long composite(int itemId, int meta) {
        return ((long)itemId << 32) ^ (meta & 0xFFFFFFFFL);
    }

    private static int itemIdOf(long packed) {
        return (int)(packed >> 32);
    }

    private static int metaOf(long packed) {
        return (int)packed;
    }

    // ------------------------------------------------------------------ NBT 往返

    /**
     * 读档：缺根键 / 根键不是 compound ⇒ 得到"全默认"的一份（{@code NONE} + 空名单），★<b>不建档</b>
     * （R53c：读路径不写）。★畸形与越界条目按<b>丢弃 + 一次性 WARN</b>处理 —— 不抛（外来档不该让整枚
     * 口袋读不出来），也不静默吞（配好的名单凭空少一条玩家必须看得见账）。
     */
    public static PocketMagnetFilter readFrom(NBTTagCompound root) {
        final PocketMagnetFilter filter = new PocketMagnetFilter();
        if (root == null) {
            return filter;
        }
        final NBTTagCompound domain = root.getCompoundTag(PocketConstants.MAGNET_FILTER);
        int dropped = 0;
        final String rawMode = domain.getString(PocketConstants.MAGNET_FILTER_MODE);
        if (rawMode != null && !rawMode.isEmpty()) {
            final Mode parsed = Mode.of(rawMode);
            if (parsed == null) {
                // ★不认识的态名 ⇒ 回落 NONE（"没配过"的那一态），并计入丢弃读数
                dropped++;
            } else {
                filter.mode = parsed;
            }
        }
        final NBTTagList list = domain.getTagList(PocketConstants.MAGNET_FILTER_LIST, TAG_STRING);
        final int read = list.tagCount();
        for (int i = 0; i < read; i++) {
            if (!filter.addEntryKey(list.getStringTagAt(i))) {
                dropped++;
            }
        }
        filter.readDropped = dropped;
        if (dropped > 0 && droppedWarnedKeys.add(PocketConstants.MAGNET_FILTER)) {
            LOG.warn(
                "[pocket] 磁力名单（键 {}）有 {} 条没能落进配置（本档共读到 {} 条 + 1 项态名；条目上限 {}）"
                    + "——条形态不认识 / itemId 为负 / 超出 {} 格预算的尾部条目已丢弃"
                    + "（外来或形状变更后的旧档；本条只报一次）",
                PocketConstants.MAGNET_FILTER,
                Integer.valueOf(dropped),
                Integer.valueOf(read),
                Integer.valueOf(PocketConstants.MAGNET_FILTER_SLOTS),
                Integer.valueOf(PocketConstants.MAGNET_FILTER_SLOTS));
        }
        return filter;
    }

    /**
     * 落档。★三条"非默认才占键"的纪律一起走（与 {@code PocketFilterConfig#writeTo} 的 cap / 位表同构）：
     * {@code NONE} 不写 {@code mode}、空名单不写 {@code list}、两者都默认时<b>整根键摘掉</b>
     * ⇒ 从没配过名单的档，字节形状与"本特性不存在"时<b>逐字节相同</b>。
     */
    public void writeTo(NBTTagCompound root) {
        if (root == null) {
            return;
        }
        if (isDefaultShape()) {
            root.removeTag(PocketConstants.MAGNET_FILTER);
            return;
        }
        final NBTTagCompound domain = new NBTTagCompound();
        if (mode != Mode.NONE) {
            domain.setString(PocketConstants.MAGNET_FILTER_MODE, mode.name());
        }
        if (!entries.isEmpty()) {
            final NBTTagList list = new NBTTagList();
            for (String key : entryKeys()) {
                // ★条目 = 一枚 String tag（同 PocketEssenceStore#writeCellOrder 的既存形状，不留第二套写法）
                list.appendTag(new NBTTagString(key));
            }
            domain.setTag(PocketConstants.MAGNET_FILTER_LIST, list);
        }
        root.setTag(PocketConstants.MAGNET_FILTER, domain);
    }
}
