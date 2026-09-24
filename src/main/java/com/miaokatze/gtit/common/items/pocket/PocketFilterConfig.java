package com.miaokatze.gtit.common.items.pocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 口袋的 ghost 配置（NEI 拖拽进来的"需求清单"）：一条声明 =
 * <b>{@code {slotIndex, kind, payloadKey, cap}}</b>（{@code cap} 是 ★R83 C2 的组上限，见 {@link Filter#cap()}）。
 * <p>
 * <b>为什么必须带槽索引</b>：本需求的读法是「<b>就地把既有槽转 ghost</b>」——拖到哪一格，那一格本身
 * 变虚化、禁放置禁取出。所以每条声明必须记住自己<b>占的是哪一格</b>（解绑、渲染虚化、
 * 「一格转 ghost 就少一格真实容量」的容量核算都靠它），只存"声明列表"是不完整的结构。
 * <p>
 * <b>为什么上限挂在声明上而不是别处</b>（D-5）：拉取模式填的就是"这一条声明"（{@code REFILL_AMOUNT_PER_FILTER_UNBOUNDED}
 * 早就按声明物自身收口，缺的只是"那个自身量可调"这一层），挂到绑定元件或另立一张表都会造出第二处真相。
 * <b>载荷键一个字都不带它</b>：{@code cap} 是数量、不是身份（{@link #contains(String)} 与
 * {@code PocketAeChannelOps#contentKey} 都按载荷键比对，掺进数量会让同一种东西的两条声明分裂）。
 * <p>
 * <b>持久化载荷键的硬约束</b>（键式样本身不变）：
 * <ul>
 * <li>物品 = {@code itemId + meta + nbtString}</li>
 * <li>流体 = {@code fluidName}</li>
 * <li>源质 = {@code typeId + aspect tag}（{@code typeId} 是 {@code AEStackTypeRegistry} 的<b>字符串</b> id）</li>
 * <li><b>载荷键里一律不存通道索引</b> —— {@code AEStackTypeRegistry} 底层是 {@code HashMap}，索引随整合包内
 * mod 增减而漂移。槽索引是<b>独立字段</b>（{@link PocketConstants#FILTER_SLOT}），不进载荷键。</li>
 * </ul>
 * <p>
 * 同一槽位二次声明 = <b>覆盖</b>（不是追加，也不保留第二条同槽条目）；同载荷键出现在两个槽是合法的
 * （两格都要同一种东西）。因此本类的身份是<b>槽位</b>而非载荷键，解绑入口也按槽位走
 * （ghost 右键解绑作用于"这一格"）。
 * <p>
 * 可被拖的索引集合由 {@link PocketConstants#GHOST_ITEM_SLOT_LIMIT} 等三个上界常量给出白名单
 * （「独立配置槽区」与「就地转换」两种读法的差异只剩允许被拖的索引集合，切换不改本类结构）。
 * <p>
 * 纯 JVM 件：只操作字符串与整数（物品的 {@code maxStackSize} 一律由<b>调用方</b>取好再经
 * {@link #resolveCap(Filter, int)} 传进来 ⇒ 本类不 import 任何 MC/AE2/TC 类型）。
 */
public final class PocketFilterConfig {

    /**
     * ★R85 B1 用的日志器。<b>不</b>走 {@code GTInterestingThing.LOG}：那条要把 mod 主类拉进本类的
     * 类初始化链（{@code @Mod}/{@code Tags} 注解），而 {@code PocketFilterConfig} 与它一起属"纯 JVM
     * 可实例化"的那一侧（{@code NekoPocketModelTest} 直接 new 它）。取与 {@code PocketAeChannelOps}/
     * {@code PocketCellProbe} 同一枚名字 {@code "gtit"} ⇒ 落进同一个 logger 配置，日志路由不变。
     */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 标识串内部分隔符；流体名/aspect tag 均不含裸冒号冲突（源质键只切第一段）。 */
    private static final char SEPARATOR = ':';
    private static final String ITEM_PREFIX = "i";
    private static final String FLUID_PREFIX = "f";
    private static final String ESSENCE_PREFIX = "e";
    /** NBTTagList 里复合条目的 tag id（10）。 */
    private static final int TAG_COMPOUND = 10;
    /** NBT 的 int tag id（3），槽索引落档形状。 */
    private static final int TAG_INT = 3;

    /** ghost 声明的种类，同时决定其槽索引所属的区域。 */
    public enum Kind {
        /** 中栏物品槽（索引空间 0…{@link PocketConstants#GHOST_ITEM_SLOT_LIMIT}−1）。 */
        ITEM,
        /** 流体槽（0…{@link PocketConstants#FLUID_COLUMN_COUNT}−1，{@link PocketConstants#GHOST_FLUID_SLOT_LIMIT}）。 */
        FLUID,
        /** 源质格（{@link PocketConstants#GHOST_ESSENCE_SLOT_LIMIT}，排布 6 列 × 8 行、总数不变）。 */
        ESSENCE
    }

    /** 一条需求声明的公共形状。 */
    public interface Filter {

        /** 被就地转换的槽索引（{@link PocketConstants#FILTER_SLOT_UNSET} 表示未绑定到槽，不可加入）。 */
        int slotIndex();

        /** 声明所属区域（决定槽索引空间）。 */
        Kind kind();

        /** 稳定标识串（载荷键），跨重启/跨整合包有效；<b>不含任何索引</b>。 */
        String key();

        /**
         * ★R83 C2（D-5）：本条声明的<b>组上限</b>原始值。
         * <p>
         * {@link PocketConstants#FILTER_CAP_UNSET} = <b>未设</b>（旧档、NEI 刚拖入、从没滚过轮）⇒
         * 消费与显示都必须走 {@link #resolveCap(Filter, int)} 回落到"该类今天的现全局量"，
         * <b>不得</b>把未设当成 0（那是"不拉了"，等于悄悄改变旧档行为）。
         */
        int cap();

        /**
         * 换一条"除了上限其余逐字相同"的声明（alt+滚轮的落档形状）。
         * <p>
         * ★返回<b>新实例</b>而不是原地改：本类的声明与 {@code Map} 里的旧值可能被别处持有
         * （{@code filters()} 的视图、正在跑的批次），可变共享对象会让"这一批按旧上限还是新上限"变成 races。
         */
        Filter withCap(int cap);
    }

    /** 声明的公共基座：槽索引 + 组上限由子类经本基座携带，载荷键由子类给出。 */
    private abstract static class BaseFilter implements Filter {

        final int slotIndex;
        /** ★R83 C2：组上限原始值（{@link PocketConstants#FILTER_CAP_UNSET} = 未设，回落现全局量）。 */
        final int cap;

        BaseFilter(int slotIndex) {
            this(slotIndex, PocketConstants.FILTER_CAP_UNSET);
        }

        BaseFilter(int slotIndex, int cap) {
            this.slotIndex = slotIndex;
            this.cap = cap;
        }

        @Override
        public final int slotIndex() {
            return slotIndex;
        }

        @Override
        public final int cap() {
            return cap;
        }
    }

    /** 物品需求：itemId + meta + nbt 字符串（组上限见 {@link Filter#cap()}）。 */
    public static final class ItemFilter extends BaseFilter {

        public final int itemId;
        public final int meta;
        public final String nbtString;

        public ItemFilter(int slotIndex, int itemId, int meta, String nbtString) {
            super(slotIndex);
            this.itemId = itemId;
            this.meta = meta;
            this.nbtString = nbtString == null ? "" : nbtString;
        }

        /** ★R83 C2：带组上限的完整构造（旧四参构造保留 ⇒ 载荷解析与既有用例一字不改）。 */
        public ItemFilter(int slotIndex, int itemId, int meta, String nbtString, int cap) {
            super(slotIndex, cap);
            this.itemId = itemId;
            this.meta = meta;
            this.nbtString = nbtString == null ? "" : nbtString;
        }

        @Override
        public Kind kind() {
            return Kind.ITEM;
        }

        @Override
        public String key() {
            return itemKey(itemId, meta, nbtString);
        }

        @Override
        public Filter withCap(int cap) {
            return new ItemFilter(slotIndex, itemId, meta, nbtString, cap);
        }
    }

    /** 流体需求：只看流体名（组上限见 {@link Filter#cap()}）。 */
    public static final class FluidFilter extends BaseFilter {

        public final String fluidName;

        public FluidFilter(int slotIndex, String fluidName) {
            super(slotIndex);
            this.fluidName = fluidName == null ? "" : fluidName;
        }

        /** ★R83 C2：带组上限的完整构造。 */
        public FluidFilter(int slotIndex, String fluidName, int cap) {
            super(slotIndex, cap);
            this.fluidName = fluidName == null ? "" : fluidName;
        }

        @Override
        public Kind kind() {
            return Kind.FLUID;
        }

        @Override
        public String key() {
            return fluidKey(fluidName);
        }

        @Override
        public Filter withCap(int cap) {
            return new FluidFilter(slotIndex, fluidName, cap);
        }
    }

    /** 源质需求：typeId 字符串 + aspect tag 字符串（组上限见 {@link Filter#cap()}）。 */
    public static final class EssenceFilter extends BaseFilter {

        public final String typeId;
        public final String tag;

        public EssenceFilter(int slotIndex, String typeId, String tag) {
            super(slotIndex);
            this.typeId = typeId == null ? "" : typeId;
            this.tag = tag == null ? "" : tag;
        }

        /** ★R83 C2：带组上限的完整构造。 */
        public EssenceFilter(int slotIndex, String typeId, String tag, int cap) {
            super(slotIndex, cap);
            this.typeId = typeId == null ? "" : typeId;
            this.tag = tag == null ? "" : tag;
        }

        @Override
        public Kind kind() {
            return Kind.ESSENCE;
        }

        @Override
        public String key() {
            return essenceKey(typeId, tag);
        }

        @Override
        public Filter withCap(int cap) {
            return new EssenceFilter(slotIndex, typeId, tag, cap);
        }
    }

    /**
     * (区域, 槽索引) → 声明；插入序即补满顺序，同一区域内同一槽二次写入即覆盖。
     * <p>
     * 键是 {@code kind + ':' + slotIndex} 的复合键而不是裸 {@code Integer}：三类的槽索引各在自己的
     * 区域里计数（中栏 0…134 / 流体槽 0…17 / 源质格 0…71，R78②③），用裸索引会让「中栏第 0 格」与
     * 「流体槽第 0 格」互相覆盖——那是结构缺陷，不是省事。
     */
    private final Map<String, Filter> byKindSlot = new LinkedHashMap<>();

    // -------------------------------------------------- ★R91-⑤⑥ 属性位表（与上面的载荷表<b>平行</b>）

    /**
     * ★R91-⑤：{@code (kind, slotIndex)} → {@code attr}（{@link PocketConstants#GHOST_ATTR_BIND} /
     * {@link PocketConstants#GHOST_ATTR_MEMORY}）。<b>缺条目 = {@link PocketConstants#GHOST_ATTR_NONE}</b>
     * ⇒ 无属性的格一个字节都不占（写档侧同一条口径 ⇒ 旧档形状逐字节不变）。
     * <p>
     * ★键式样与 {@link #byKindSlot} <b>逐字相同</b>（{@code kind + ':' + slotIndex}）：三个区域的槽索引
     * 各自从 0 起，裸索引会让"中栏第 0 格"与"流体槽第 0 格"互相覆盖（R59b 偏离④ / R70 的同一条结构约束）。
     * ★<b>互斥单值</b>：一格同时只可能有一个 attr，因此这里存的是 int 而不是位掩码。
     */
    private final Map<String, Integer> attrByKindSlot = new LinkedHashMap<>();
    /**
     * ★R91-⑤：阻拦上传位 {@code P} 的格集合。<b>正交位</b> —— 可与任一 {@code attr} 并存
     * （"已声明 + 锁上传"是完全合法的一格），因此刻意<b>不</b>塞进 {@link #attrByKindSlot} 的 int 里
     * （那会把互斥单值偷偷变成掩码，读法分叉）。
     */
    private final java.util.Set<String> uploadBlockedKeys = new java.util.LinkedHashSet<>();

    /**
     * ★R91-⑥ 位表读口（attr）：该格当前的互斥属性；无属性 ⇒ {@link PocketConstants#GHOST_ATTR_NONE}。
     * <p>
     * ★这是 attr 的<b>唯一</b>存储与<b>唯一</b>读法：执法侧（{@code PocketAeChannelOps} 的 P 早退、
     * {@code PocketInventory#isItemValid} 的 L 腿、{@code PocketChannelRunner} 的"补货只认 BIND"）
     * 都经本方法，★不得从 {@link #at(Kind, int)} 那条载荷表派生（R91-b 明文：空格无从表达）。
     */
    public int attrAt(Kind kind, int slotIndex) {
        final Integer raw = kind == null ? null : attrByKindSlot.get(slotKey(kind, slotIndex));
        return raw == null ? PocketConstants.GHOST_ATTR_NONE : PocketConstants.normalizeGhostAttr(raw);
    }

    /** ★R91-⑥ 位表读口（P）：该格是否永不进注入向。 */
    public boolean uploadBlockedAt(Kind kind, int slotIndex) {
        return kind != null && uploadBlockedKeys.contains(slotKey(kind, slotIndex));
    }

    /**
     * ★R91-⑤ 迁移写口（attr）：落到 {@code attr} 上（{@link PocketConstants#GHOST_ATTR_NONE} = 撤属性）。
     *
     * @return 本次是否真的改变（同值 = false ⇒ 调用方不写档、不刷虚化）
     */
    public boolean setAttr(Kind kind, int slotIndex, int attr) {
        if (kind == null || slotIndex < 0) {
            return false;
        }
        final int next = PocketConstants.normalizeGhostAttr(attr);
        final String key = slotKey(kind, slotIndex);
        if (next == PocketConstants.GHOST_ATTR_NONE) {
            return attrByKindSlot.remove(key) != null;
        }
        final Integer before = attrByKindSlot.put(key, Integer.valueOf(next));
        return before == null || before.intValue() != next;
    }

    /**
     * ★R91-⑤ 迁移写口（P）：{@code true} = 本格内容永不进注入向。
     *
     * @return 本次是否真的改变
     */
    public boolean setUploadBlocked(Kind kind, int slotIndex, boolean blocked) {
        if (kind == null || slotIndex < 0) {
            return false;
        }
        final String key = slotKey(kind, slotIndex);
        return blocked ? uploadBlockedKeys.add(key) : uploadBlockedKeys.remove(key);
    }

    /** 位表条目数（attr 与 P 的<b>并集</b>，只数"至少挂了一个属性"的格）。 */
    public int flagEntryCount() {
        final java.util.Set<String> both = new java.util.LinkedHashSet<>(attrByKindSlot.keySet());
        both.addAll(uploadBlockedKeys);
        return both.size();
    }

    /** 本端是否<b>一个属性都没有</b>（⇒ 位表不落档、blob 不写）。 */
    public boolean hasNoFlags() {
        return attrByKindSlot.isEmpty() && uploadBlockedKeys.isEmpty();
    }

    /** 位表的<b>插入序</b>快照（S2C 属性 blob 的编码源，与载荷表的"声明序"同一条纪律）。 */
    public List<FlagRecord> flagRecords() {
        final java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        keys.addAll(attrByKindSlot.keySet());
        keys.addAll(uploadBlockedKeys);
        final List<FlagRecord> records = new ArrayList<>(keys.size());
        for (String key : keys) {
            final int at = key.indexOf(SEPARATOR);
            final Kind kind = Kind.valueOf(key.substring(0, at));
            final int slot = Integer.parseInt(key.substring(at + 1));
            records.add(new FlagRecord(kind, slot, attrAt(kind, slot), uploadBlockedKeys.contains(key)));
        }
        return records;
    }

    /**
     * ★R91-⑥：<b>只清属性位表</b>，一个字节都不碰载荷表。
     * <p>
     * 存在的理由：客户端的属性镜像由<b>另一根</b> S2C 通道（{@code SYNC_GHOST_FLAGS}）整体覆盖，
     * 每次覆盖必须"先清后写"（否则被服务端撤掉的属性会留在镜像上永远不落），★但<b>不许</b>连带清掉
     * ghost 载荷 —— 那是 {@code SYNC_GHOST} 那一根通道的东西，两根通道各写各的半才是"属性层不焊回
     * blob 编解码"（R91-a）的兑现点。
     */
    public void clearFlags() {
        attrByKindSlot.clear();
        uploadBlockedKeys.clear();
    }

    /** 一条位表条目（★只是值对象：判定与迁移全在 {@code PocketGhostRequest} 那一张真值表里）。 */
    public static final class FlagRecord {

        public final PocketFilterConfig.Kind kind;
        public final int slotIndex;
        public final int attr;
        public final boolean uploadBlocked;

        public FlagRecord(Kind kind, int slotIndex, int attr, boolean uploadBlocked) {
            this.kind = kind;
            this.slotIndex = slotIndex;
            this.attr = PocketConstants.normalizeGhostAttr(attr);
            this.uploadBlocked = uploadBlocked;
        }
    }

    /**
     * ★R91-⑤ 的 <b>L 执法腿单源</b>：一格<b>已经是需求格</b>（默认禁放置）时，玩家这次的"放入本格"
     * 要不要放行 —— 「本格只能放<b>那一种</b>东西」这一条判据的<b>唯一</b>实现。
     * <p>
     * 三条口径，一条都不能少：
     * <ol>
     * <li><b>默认不放行</b>：{@code attr != MEMORY}（含 BIND 与无属性）⇒ {@code false}。★这不是重复执法：
     * 需求格"禁放置"是 R84 立的规定（它是抽取<b>落点</b>，不是玩家输入口），本方法只在
     * "玩家显式按了 alt+左 挂了记忆"这一档上<b>开一个受限的口子"；</li>
     * <li>{@code attr == MEMORY} 且<b>已有载荷</b> ⇒ 只有载荷键<b>逐字相同</b>才放行（载荷键是跨重启稳定
     * 身份，与 {@code PocketAeChannelOps#contentKey} 同一比对口径 ⇒ "只能放该种东西"真的只放那一种）；</li>
     * <li>{@code attr == MEMORY} 但<b>还没有载荷</b>（pending 记忆 = R91-b 的"空格先进状态、内容待拖拽落成"）
     * ⇒ ★<b>R91-i 显式裁定：不限制放置</b>——还没有可比载荷，"只能放那一种"无从谈起；载荷定档只由
     * NEI 拖拽、或"格内有物时再按一次手势"（R91-⑤ 共同规则）完成，一落成载荷本方法第二条接管。
     * ★这一档是<b>契约</b>不是推演：用例 {@code ghost_memory_pending_placement_unrestricted} 直接调本方法
     * 钉住返回值（本仓通则：能用一行源码判真伪的前提，不许留在"推断"栏——物品调用方今天只在
     * 有载荷时问，但这条返回不依赖"谁来问"）。</li>
     * </ol>
     * ★调用方是 {@code PocketInventory#newStorageGroup} 的 {@code isItemValid} 链
     * （R83-B1 改判：<b>不是</b>字面 {@code canPut}）；★补货行为仍只认 BIND（{@link #pullsFromCell}），
     * L 不参与抽取。
     */
    public boolean allowsPlayerPlacement(Kind kind, int slotIndex, String contentKey) {
        if (attrAt(kind, slotIndex) != PocketConstants.GHOST_ATTR_MEMORY) {
            return false;
        }
        final Filter declared = at(kind, slotIndex);
        if (declared == null) {
            // ★R91-i：pending 档（挂了 L、还没定档）⇒ 不限制放置。这条返回就是裁定的实现形态，
            // ★不许被"调用方结构上问不到"之类的推演顶替（推演会随下一个调用方悄悄过期）。
            return true;
        }
        return declared.key()
            .equals(contentKey);
    }

    /**
     * ★R91-⑤ 的 <b>L 执法腿（源质支）</b>单源：本格记的是不是<b>这一个 tag</b>。
     * <p>
     * 与 {@link #allowsPlayerPlacement} 同一族的三条口径（非 MEMORY 不判 / 无载荷不拦 / 载荷不同才拒），
     * ★但<b>比较粒度是 tag 而不是整条载荷键</b>，理由是<b>身份</b>不是<b>运输方式</b>：源质载荷键是
     * {@code e:<typeId>:<tag>}，其中 {@code typeId} 是"哪个 AE2 通道装得下它"的<b>读数</b>
     * （换整合包、少一个 addon 就会变），而入槽时载体带的只有 tag。拿整键比就会出现
     * "同一格同一源质，因为通道 id 解不出而被自己的记忆规则拒收"。
     * <p>
     * ★本格声明<b>不是</b>源质声明（档位在别处）时按"不拦"处理：那是数据被改坏的形状，
     * 宁可乐观放行也不凭猜测销毁玩家的入槽。
     */
    public boolean memoryAllowsTag(Kind kind, int slotIndex, String tag) {
        if (attrAt(kind, slotIndex) != PocketConstants.GHOST_ATTR_MEMORY) {
            return true;
        }
        final Filter declared = at(kind, slotIndex);
        if (!(declared instanceof EssenceFilter essence)) {
            return true;
        }
        return tag != null && tag.equals(essence.tag);
    }

    /**
     * ★★<b>R91-p：L 的执法腿（流体支）</b>单源 —— 本列记的是不是<b>这一种流体</b>。
     * <p>
     * 与 {@link #memoryAllowsTag}（源质支）、{@link #allowsPlayerPlacement}（物品支）同一族的三条口径
     * （非 MEMORY 不判 / 无载荷不拦 / 载荷不同才拒），R91-⑤ 的"三处逐字同形"到本片才对流体列成立。
     * ★比较粒度是<b>流体名</b>：流体载荷键就是 {@code f:<fluidName>} 一段，名字即声明身份的全部
     * （不像源质键里 {@code typeId} 是通道读数要剥掉 ⇒ 这里没有第二档粒度可选）。
     * <p>
     * ★调用方只有"玩家把流体灌进这一列"的两个入口（点流体槽的容器灌入 {@code fillFluid} 支、
     * 交互格容器处理 {@code PocketFluidTransfer#drainIntoTank}），它们<b>只问结论不含判定</b>；
     * 灌装回写、通道回滚与抽取方向<b>不经本闸</b>（L 纯过滤，一条抽取/一条退液都不改变）。
     * ★本格声明<b>不是</b>流体声明（档位串了）时按"不拦"处理：与源质支同一条"数据被改坏的形状
     * 宁可乐观放行也不凭猜测销毁玩家动作"。
     */
    public boolean memoryAllowsFluid(Kind kind, int slotIndex, String fluidName) {
        if (attrAt(kind, slotIndex) != PocketConstants.GHOST_ATTR_MEMORY) {
            return true;
        }
        final Filter declared = at(kind, slotIndex);
        if (!(declared instanceof FluidFilter fluid)) {
            return true;
        }
        return fluidName != null && fluidName.equals(fluid.fluidName);
    }

    /**
     * ★R91-⑤ 的<b>抽取闸门</b>单源：这一格<b>是不是</b>该从 AE 补货。
     * <p>
     * 裁定原文：「{@code L}（记忆）纯过滤，<b>不产生任何 AE 拉取行为</b>；补货行为仍只认 BIND」。
     * ★刻意<b>不</b>写成"只有 BIND 才拉"：那会回退需求 4 的既有语义 —— 从没做过手势的旧声明
     * （{@code attr == NONE} + 有载荷）今天就在拉，且用户没抱怨过它。因此真值只有 MEMORY 一档不拉：
     * {@code NONE}（隐式 BIND）与 {@code BIND} 都拉。
     */
    public boolean pullsFromCell(Kind kind, int slotIndex) {
        return attrAt(kind, slotIndex) != PocketConstants.GHOST_ATTR_MEMORY;
    }

    /**
     * ★R91-⑤ 的 <b>P 执法腿</b>单源（注入向来源枚举读这一条）：本格内容永不进注入向。
     * <p>
     * 与 {@link #isStorageGhostDeclared} 那条"声明格不回流成来源"（R84 反成环铁律）<b>并列</b>而不是
     * 替代 —— 两条各挡一件事：那条防"抽出来又灌回去"，本条防玩家显式锁住一格。★{@code attr} 不参与：
     * 只有 {@code P} 拦上传（BIND/MEMORY 都不拦，否则一格两种属性就会互相改变对方的语义）。
     */
    public boolean blocksUpload(Kind kind, int slotIndex) {
        return uploadBlockedAt(kind, slotIndex);
    }

    public static PocketFilterConfig readFrom(NBTTagCompound root) {
        final PocketFilterConfig config = new PocketFilterConfig();
        if (root == null) {
            return config;
        }
        final NBTTagCompound domain = root.getCompoundTag(PocketConstants.FILTERS);
        // ★R85 B1：三个区各自统计"读了但没落进表"的条数。旧写法把 add() 的返回值丢掉 ⇒
        // 超出 GHOST_ITEM_SLOT_LIMIT 的尾部声明（R80 把中栏 150 收到 135 时的 135…149 号格）
        // 整条消失且**零日志**，而同一次收缩在 PocketInventory#loadGroup 对**物品**条目是有一条
        // 一次性 WARN 的（两套纪律，取证档案 r85-ret-robust §4-①）。R84 之后声明格还是抽取落点
        // ⇒ 丢一条声明 = 同时丢一个落点，更不能静默。
        int itemsDropped = 0;
        final NBTTagList items = domain.getTagList(PocketConstants.FILTER_ITEMS, TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            final NBTTagCompound entry = items.getCompoundTagAt(i);
            final int slot = readSlot(entry);
            if (!config.add(
                slot,
                new ItemFilter(
                    slot,
                    entry.getInteger(PocketConstants.FILTER_ITEM_ID),
                    entry.getInteger(PocketConstants.FILTER_META),
                    entry.getString(PocketConstants.FILTER_NBT),
                    readCap(entry)))) {
                itemsDropped++;
            }
        }
        warnDroppedOnce(Kind.ITEM, PocketConstants.FILTER_ITEMS, items.tagCount(), itemsDropped);
        int fluidsDropped = 0;
        final NBTTagList fluids = domain.getTagList(PocketConstants.FILTER_FLUIDS, TAG_COMPOUND);
        for (int i = 0; i < fluids.tagCount(); i++) {
            final NBTTagCompound entry = fluids.getCompoundTagAt(i);
            final String name = entry.getString(PocketConstants.FILTER_FLUID);
            final int slot = readSlot(entry);
            if (name.isEmpty() || !config.add(slot, new FluidFilter(slot, name, readCap(entry)))) {
                fluidsDropped++;
            }
        }
        warnDroppedOnce(Kind.FLUID, PocketConstants.FILTER_FLUIDS, fluids.tagCount(), fluidsDropped);
        int essentiaDropped = 0;
        final NBTTagList essentia = domain.getTagList(PocketConstants.FILTER_ESSENTIA, TAG_COMPOUND);
        for (int i = 0; i < essentia.tagCount(); i++) {
            final NBTTagCompound entry = essentia.getCompoundTagAt(i);
            final String typeId = entry.getString(PocketConstants.FILTER_TYPE_ID);
            final String tag = entry.getString(PocketConstants.FILTER_TAG);
            final int slot = readSlot(entry);
            if (typeId.isEmpty() || tag.isEmpty()
                || !config.add(slot, new EssenceFilter(slot, typeId, tag, readCap(entry)))) {
                essentiaDropped++;
            }
        }
        warnDroppedOnce(Kind.ESSENCE, PocketConstants.FILTER_ESSENTIA, essentia.tagCount(), essentiaDropped);
        // ★R91-⑥：第四枚列表 = 属性位表（attr + P）。缺键（旧档 / 没有任何属性的档）⇒ 整张表为空，
        // 三个读口都回落 NONE/false ⇒ 旧档行为逐字不变。区域字母不认识或槽号越出白名单的条目<b>丢掉</b>
        // （与上面三条载荷列表同一条"宁可少读也不臆造"口径：位表读口拿不到越界条目 = 那格无属性 = 与
        // 没配过等效，而臆造成"有属性"会凭空拦掉玩家的上传/落位）。
        final NBTTagList flags = domain.getTagList(PocketConstants.FILTER_FLAGS, TAG_COMPOUND);
        int flagsDropped = 0;
        for (int i = 0; i < flags.tagCount(); i++) {
            final NBTTagCompound entry = flags.getCompoundTagAt(i);
            final Kind kind = kindOfLetter(entry.getString(PocketConstants.FILTER_FLAG_KIND));
            final int slot = entry.getInteger(PocketConstants.FILTER_SLOT);
            if (kind == null || !isAllowedSlotIndex(kind, slot)) {
                flagsDropped++;
                continue;
            }
            final int attr = entry.hasKey(PocketConstants.FILTER_ATTR, TAG_INT)
                ? PocketConstants.normalizeGhostAttr(entry.getInteger(PocketConstants.FILTER_ATTR))
                : PocketConstants.GHOST_ATTR_NONE;
            if (attr != PocketConstants.GHOST_ATTR_NONE) {
                config.setAttr(kind, slot, attr);
            }
            if (entry.hasKey(PocketConstants.FILTER_UPLOAD_BLOCK, TAG_INT)
                && entry.getInteger(PocketConstants.FILTER_UPLOAD_BLOCK) != 0) {
                config.setUploadBlocked(kind, slot, true);
            }
        }
        if (flagsDropped > 0) {
            LOG.warn(
                "[pocket] 存档里的格子属性位表有 {} 条没能落进配置表（共读到 {} 条）——区域字母不认识或槽号越出当前面板形状，" + "这些格子按\"无属性\"处理（本条只报一次）",
                flagsDropped,
                flags.tagCount());
        }
        return config;
    }

    /**
     * 位表条目里的区域字符串 → {@link Kind}；不认识 ⇒ {@code null}（调用方丢弃该条）。
     * <p>
     * ★用<b>枚举名</b>而不是 {@code PocketGhostRequest#letterOf} 的那三个单字母：载荷三列表的键名本来也是
     * 枚举名式样的独立常量，且 NBT 是存档侧、单字母是 C2S 文法侧 —— 两侧各一套字母会让"改一个不改另一个"
     * 变成静默 bug。纯 JVM 侧解不出的字符串在这里收口。
     */
    private static Kind kindOfLetter(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Kind.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * "ghost 声明读档被丢弃"的一次性 WARN（★R85 B1，照 {@code PocketInventory#warnOutOfRangeOnce} 的
     * 句式与节流口径：<b>每个区一次</b>，不是每拍打）。
     * <p>
     * 三条口径：
     * <ol>
     * <li><b>不许静默</b>：面板形状收缩（150 → 135）后旧档的尾部声明必然越界，读档整条消失；
     * 一件物品都没丢（声明表是配置不是库存），但"我明明配过"的配置消失同样不许零行日志；</li>
     * <li><b>不每拍打</b>：本方法只在读档路径上被调（每次开屏一次），闩按<b>区</b>固定三枚键 ⇒
     * 反复开关屏也只各报一行；</li>
     * <li>★与 {@code PocketInventory} 同一条既存弱点：闩是<b>进程维</b>而非玩家维（这里拿不到玩家身份，
     * {@code readFrom} 的入参只有一份 NBT），所以同 JVM 连开两档时第二档的同区丢弃不再复报。
     * 收口属日志面结构改动（要么键里加身份、要么挂 lifecycle 清理），与 {@code outOfRangeWarnedKeys}
     * 同一批处理，本片不自作主张换纪律。</li>
     * </ol>
     */
    private static void warnDroppedOnce(Kind kind, String key, int read, int dropped) {
        if (dropped <= 0 || !droppedWarnedKeys.add(key)) {
            return;
        }
        final int limit = switch (kind) {
            case ITEM -> PocketConstants.GHOST_ITEM_SLOT_LIMIT;
            case FLUID -> PocketConstants.GHOST_FLUID_SLOT_LIMIT;
            case ESSENCE -> PocketConstants.GHOST_ESSENCE_SLOT_LIMIT;
        };
        LOG.warn(
            "[pocket] 存档里的 ghost 声明 {}（键 {}）有 {} 条没能落进配置表（本区共读到 {} 条；可用槽号 0…{}）"
                + "——槽号越出当前面板形状 / 载荷为空 / 同槽重复被折叠，这些条目已丢弃"
                + "（形状变更后的旧/外来档；本条按区只报一次）",
            kind,
            key,
            dropped,
            read,
            limit - 1);
    }

    /** ghost 声明读档丢弃 WARN 的"每个区一次"闩（LinkedHashSet 保首次出现顺序，日志可读）。 */
    private static final java.util.Set<String> droppedWarnedKeys = new java.util.LinkedHashSet<>();

    public void writeTo(NBTTagCompound root) {
        final NBTTagList items = new NBTTagList();
        final NBTTagList fluids = new NBTTagList();
        final NBTTagList essentia = new NBTTagList();
        for (Filter filter : byKindSlot.values()) {
            final NBTTagCompound entry = new NBTTagCompound();
            // 槽索引逐条必写（含 0）：缺键在读档时回落为"未设置"，与合法索引 0 可区分
            entry.setInteger(PocketConstants.FILTER_SLOT, filter.slotIndex());
            // ★R83 C2：上限<b>只在玩家真调过之后才落档</b>（未设 = 回落现全局量，不必占一个键）。
            // 这一条同时保住既有契约"物品声明只允许 itemId/meta/nbt/slotIndex 四个字段"
            // （NekoPocketModelTest.assertNoChannelIndexCarried）⇒ 没人滚轮时旧档形状逐字节不变。
            if (filter.cap() != PocketConstants.FILTER_CAP_UNSET) {
                entry.setInteger(PocketConstants.FILTER_CAP, filter.cap());
            }
            if (filter instanceof ItemFilter item) {
                entry.setInteger(PocketConstants.FILTER_ITEM_ID, item.itemId);
                entry.setInteger(PocketConstants.FILTER_META, item.meta);
                entry.setString(PocketConstants.FILTER_NBT, item.nbtString);
                items.appendTag(entry);
            } else if (filter instanceof FluidFilter fluid) {
                entry.setString(PocketConstants.FILTER_FLUID, fluid.fluidName);
                fluids.appendTag(entry);
            } else if (filter instanceof EssenceFilter essence) {
                entry.setString(PocketConstants.FILTER_TYPE_ID, essence.typeId);
                entry.setString(PocketConstants.FILTER_TAG, essence.tag);
                essentia.appendTag(entry);
            }
        }
        final NBTTagCompound domain = new NBTTagCompound();
        domain.setTag(PocketConstants.FILTER_ITEMS, items);
        domain.setTag(PocketConstants.FILTER_FLUIDS, fluids);
        domain.setTag(PocketConstants.FILTER_ESSENTIA, essentia);
        // ★R91-⑥：位表<b>只在真有条目时</b>才挂第四枚列表（同上面 cap 那条纪律）⇒ 无属性档的序列化字节与
        // "根本没有 flags 键"的旧形状<b>逐字节相同</b>（成对门禁：用例
        // ghost_flags_persistence_pairwise + verify-pocket.sh 的 R91-c 段）。
        // 条目形状 {kind(String), slotIndex(Int), attr(Int), uploadBlock(Int 0|1)}：★attr 与 P 都只写
        // "非默认"的那一半 ⇒ 只有 P 的一格不会写出 attr=0。
        if (!hasNoFlags()) {
            final NBTTagList flags = new NBTTagList();
            for (FlagRecord record : flagRecords()) {
                final NBTTagCompound entry = new NBTTagCompound();
                entry.setString(PocketConstants.FILTER_FLAG_KIND, record.kind.name());
                entry.setInteger(PocketConstants.FILTER_SLOT, record.slotIndex);
                if (record.attr != PocketConstants.GHOST_ATTR_NONE) {
                    entry.setInteger(PocketConstants.FILTER_ATTR, record.attr);
                }
                if (record.uploadBlocked) {
                    entry.setInteger(PocketConstants.FILTER_UPLOAD_BLOCK, 1);
                }
                flags.appendTag(entry);
            }
            domain.setTag(PocketConstants.FILTER_FLAGS, flags);
        }
        root.setTag(PocketConstants.FILTERS, domain);
    }

    /**
     * 把一条声明落到它占用的槽上。
     * <p>
     * 同一区域内同一 {@code slotIndex} 二次 add = <b>覆盖</b>旧声明（ghost 一格只有一条声明），
     * 不同区域（{@link Kind}）的同名索引各占各的格，互不覆盖；
     * 槽索引与声明自带的 {@code slotIndex()} 不一致、越出该 {@link Kind} 的白名单、
     * 或声明为 null/载荷键为空都拒收。
     *
     * @return true 表示该槽本次<b>新增</b>了声明（覆盖返回 false）
     */
    public boolean add(int slotIndex, Filter filter) {
        if (filter == null || slotIndex != filter.slotIndex()) {
            // 槽索引与声明自带的位置不一致 = 调用方拼错了，宁可拒收也不静默改一处
            return false;
        }
        return put(filter);
    }

    /** 该槽当前的声明；无声明返回 null（{@code kind} 决定查哪个索引空间）。 */
    public Filter at(Kind kind, int slotIndex) {
        return byKindSlot.get(slotKey(kind, slotIndex));
    }

    /**
     * ★★<b>R92-④：把一条"只带载荷、不带槽位"的解出结果落到指定格上（单源）</b>。
     * <p>
     * ghost 的槽位由"被拖/被点/被放的那一格"决定，载荷键本身不含槽位（R38 第 1 条），所以任何写入口
     * 都必须经过本方法把槽位贴回去 ⇒ 单点：blob 编解码（S2C 回显）、C2S 请求（{@code applySet}）与
     * ★R92-④ 新增的<b>服务端放置定档</b>腿共用这一份，否则三处 rebuild 迟早漂移。
     * <p>
     * ★<b>为什么住在这一层（而不是留在 {@code gui/pocket/PocketGhostRequest}）</b>：放置定档的三条腿里，
     * 物品支的落档点在 {@code PocketInventory} 的槽组回调上，那是 {@code common/items/pocket} ——
     * 为够到一个纯 JVM 函数而新增第三条 {@code common → gui} 反向 import，代价大于收益，且那条方向
     * 本仓已登记为旧债（R92 计划 §1.2 明令不扩）。本方法<b>只依赖本层类型</b>，下移到零依赖层是收敛方向。
     * ★{@code PocketGhostRequest#rebuildAt} 保留为薄委派（与 R91-③ 的 {@code essenceStackFor} 同一形状）。
     * <p>
     * 三参形态给"载荷键里本来就没有上限"的解析路径（{@link #parseKey}）用 ⇒ 上限一律
     * {@link PocketConstants#FILTER_CAP_UNSET}；带已有声明的场合必须走四参形态把上限带上
     * （抹了就是"我调的上限悄悄没了"）。
     */
    public static Filter rebuildAt(Kind kind, int slotIndex, Filter payload) {
        return rebuildAt(kind, slotIndex, payload, PocketConstants.FILTER_CAP_UNSET);
    }

    /** 同 {@link #rebuildAt(Kind, int, Filter)}，但显式给出组上限。 */
    public static Filter rebuildAt(Kind kind, int slotIndex, Filter payload, int cap) {
        if (kind == null || payload == null) {
            return null;
        }
        switch (kind) {
            case ITEM:
                if (!(payload instanceof ItemFilter item)) {
                    return null;
                }
                return new ItemFilter(slotIndex, item.itemId, item.meta, item.nbtString, cap);
            case FLUID:
                if (!(payload instanceof FluidFilter fluid)) {
                    return null;
                }
                return new FluidFilter(slotIndex, fluid.fluidName, cap);
            case ESSENCE:
                if (!(payload instanceof EssenceFilter essence)) {
                    return null;
                }
                return new EssenceFilter(slotIndex, essence.typeId, essence.tag, cap);
            default:
                return null;
        }
    }

    /**
     * ★★<b>R92-④（D4）：放置即配置的准入判据单源</b> —— 该格是否处于
     * <b>「记忆档（L）+ 尚无声明（pending）」</b>这一种"可以被一次放置定档"的状态。
     * <p>
     * 存在的理由与 {@link #allowsPlayerPlacement} 同一条：三条腿（物品 {@code PocketInventory} /
     * 流体 {@code PocketFluidTransfer} 与左列 / 源质 {@code NekoPocketServerHandler}）都必须问<b>同一条</b>
     * 判据。各腿自己写一遍 {@code attrAt(...) == GHOST_ATTR_MEMORY && at(...) == null}，就会出现
     * "某一腿忘了带 pending 条件 ⇒ 放置把已定档格的声明覆盖了"——那正是 P2 裁定明令禁止的行为。
     * <p>
     * ★两个条件都不可省：NONE / BIND 格今天照样禁放（{@link #allowsPlayerPlacement} 那条门一字未动），
     * 而已定档的 MEMORY 格只允许放<b>被记住那一种</b>，放成之后不得再改声明（改走 NEI 拖入或手势）。
     */
    public boolean memoryPendingForPlacement(Kind kind, int slotIndex) {
        return attrAt(kind, slotIndex) == PocketConstants.GHOST_ATTR_MEMORY && at(kind, slotIndex) == null;
    }

    /**
     * ★★<b>R92-④：把一条载荷落到指定格的唯一服务端写入原语</b> —— 读旧档保上限 →
     * {@link #rebuildAt} → {@link #add} → 复核"这一格现在到底是不是这条"。
     * <p>
     * 存在的理由：C2S 请求腿（{@code PocketGhostRequest#applySet}）与★R92-④ 新增的<b>放置定档</b>三条腿
     * 必须走<b>同一条</b>写入口。各写一份"add 完不复核"的支，就会出现"回执说成功了、声明表里其实没有"
     * 的假档（R91 记录里那条"投影成晶档"的同族形状）。★复核<b>不</b>回滚既有声明：本方法判不出
     * "该格原本那条"该不该留，删它就是把玩家的上限与另一条声明一起抹掉。
     *
     * @param payload 只带载荷、不带槽位的解出结果（来自 {@link #parseKey}）
     * @return null = 被拒（载荷类型不符 / 越界 / 复核不过）；非 null = 该格<b>现在</b>的声明
     */
    public Filter declare(Kind kind, int slotIndex, Filter payload) {
        if (kind == null || payload == null) {
            return null;
        }
        // ★同槽覆盖必须把<b>已调好的上限</b>带过去：载荷键一样就是"还是这一条需求"，玩家滚出来的
        // 数值不该因为"又拖了一次同一个东西 / 又放了一件"而被抹回默认值。
        final Filter before = at(kind, slotIndex);
        final Filter rebuilt = rebuildAt(
            kind,
            slotIndex,
            payload,
            before == null ? PocketConstants.FILTER_CAP_UNSET : before.cap());
        if (rebuilt == null) {
            return null;
        }
        add(slotIndex, rebuilt);
        final Filter now = at(kind, slotIndex);
        // add 的 false 有两种含义（同槽覆盖 / 越界或载荷空），故以"该格现在到底是不是这条"为准
        return now != null && now.key()
            .equals(rebuilt.key()) ? now : null;
    }

    /** 落到声明自带的槽位上；同槽即覆盖（{@code Map.put} 的返回值天然区分"新增/覆盖"）。 */
    private boolean put(Filter filter) {
        if (filter.key()
            .isEmpty() || !isAllowedSlotIndex(filter.kind(), filter.slotIndex())) {
            return false;
        }
        return byKindSlot.put(slotKey(filter.kind(), filter.slotIndex()), filter) == null;
    }

    /**
     * 解绑某一格的 ghost 声明（R38 的右键解绑入口落在"这一格"，故按槽位而非载荷键）。
     *
     * @return 该槽本次是否真的删掉了一条
     */
    public boolean removeAt(Kind kind, int slotIndex) {
        return byKindSlot.remove(slotKey(kind, slotIndex)) != null;
    }

    /** 是否有任何槽已声明同一载荷键（同载荷多槽合法，故这只用于"该东西被要过吗"的查询）。 */
    public boolean contains(String key) {
        for (Filter filter : byKindSlot.values()) {
            if (filter.key()
                .equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** 声明序视图（补满时按此顺序从元件抽取）；遍历时用 {@link Filter#slotIndex()} 取回所在格。 */
    public List<Filter> filters() {
        return Collections.unmodifiableList(new ArrayList<>(byKindSlot.values()));
    }

    public int size() {
        return byKindSlot.size();
    }

    public boolean isEmpty() {
        return byKindSlot.isEmpty();
    }

    public void clear() {
        byKindSlot.clear();
        // ★R91-⑥：属性位表与载荷表是<b>同一格的两半</b> ⇒ 整表清空必须一起清，
        // 否则清完还留着 L/P 的读数（客户端镜像换实例时就会照旧画角标）。
        attrByKindSlot.clear();
        uploadBlockedKeys.clear();
    }

    /** 复合键：区域 + 槽索引（区域名是枚举名，不是任何注册索引）。 */
    private static String slotKey(Kind kind, int slotIndex) {
        return kind + ":" + slotIndex;
    }

    /**
     * 槽索引白名单：某类声明允许落在哪些格上。
     * <p>
     * 「就地把既有槽转 ghost」与「另开独立配置槽区」两种读法切换时<b>只改这里</b>
     * （以及对应的 GUI 布局常量），数据结构与抽取逻辑不变。
     */
    public static boolean isAllowedSlotIndex(Kind kind, int slotIndex) {
        if (kind == null || slotIndex < 0) {
            return false;
        }
        final int limit = switch (kind) {
            case ITEM -> PocketConstants.GHOST_ITEM_SLOT_LIMIT;
            case FLUID -> PocketConstants.GHOST_FLUID_SLOT_LIMIT;
            case ESSENCE -> PocketConstants.GHOST_ESSENCE_SLOT_LIMIT;
        };
        return slotIndex < limit;
    }

    /** 由标识串反解载荷（GUI 同步、元件侧内容比对用）；解不出返回 null。 */
    public static Filter parseKey(String key) {
        if (key == null || key.length() < 3) {
            return null;
        }
        final int sep = key.indexOf(SEPARATOR);
        if (sep < 0) {
            return null;
        }
        final String head = key.substring(0, sep);
        final String rest = key.substring(sep + 1);
        if (ITEM_PREFIX.equals(head)) {
            return parseItem(rest);
        }
        if (FLUID_PREFIX.equals(head)) {
            return new FluidFilter(PocketConstants.FILTER_SLOT_UNSET, rest);
        }
        if (ESSENCE_PREFIX.equals(head)) {
            // ★按<b>最后</b>一个分隔符切：typeId 是 AE2 第三方通道自己取的字符串
            // （自家只见到 "item"/"fluid" 两枚无冒号的内置 id，addon 完全可能取 "mod:channel" 形式），
            // 而 aspect tag 是 TC 的小写拉丁标识符 ⇒ 只有"冒号只可能出现在 typeId 一侧"这一条方向是安全的。
            // 用 indexOf 会把带命名空间的 typeId 从中间切断，并把残段当成 tag ⇒ 静默错声明。
            final int at = rest.lastIndexOf(SEPARATOR);
            return at < 0 ? null
                : new EssenceFilter(PocketConstants.FILTER_SLOT_UNSET, rest.substring(0, at), rest.substring(at + 1));
        }
        return null;
    }

    private static Filter parseItem(String rest) {
        final int metaStart = rest.indexOf(SEPARATOR);
        if (metaStart < 0) {
            return null;
        }
        final int nbtStart = rest.indexOf(SEPARATOR, metaStart + 1);
        try {
            final int itemId = Integer.parseInt(rest.substring(0, metaStart));
            final int meta = Integer
                .parseInt(nbtStart < 0 ? rest.substring(metaStart + 1) : rest.substring(metaStart + 1, nbtStart));
            return new ItemFilter(
                PocketConstants.FILTER_SLOT_UNSET,
                itemId,
                meta,
                nbtStart < 0 ? "" : rest.substring(nbtStart + 1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String itemKey(int itemId, int meta, String nbtString) {
        return ITEM_PREFIX + SEPARATOR + itemId + SEPARATOR + meta + SEPARATOR + (nbtString == null ? "" : nbtString);
    }

    public static String fluidKey(String fluidName) {
        return FLUID_PREFIX + SEPARATOR + (fluidName == null ? "" : fluidName);
    }

    public static String essenceKey(String typeId, String tag) {
        return ESSENCE_PREFIX + SEPARATOR + (typeId == null ? "" : typeId) + SEPARATOR + (tag == null ? "" : tag);
    }

    /** 缺键（外来/陈旧档）回落为"未设置"而不是 0：0 是合法槽索引，混用会把声明错挂到中栏第一格。 */
    private static int readSlot(NBTTagCompound entry) {
        return entry.hasKey(PocketConstants.FILTER_SLOT, TAG_INT) ? entry.getInteger(PocketConstants.FILTER_SLOT)
            : PocketConstants.FILTER_SLOT_UNSET;
    }

    /**
     * ★R83 C2 的读档口径：组上限<b>缺键 = 未设置</b>（同 {@link #readSlot}），绝不当成 0，
     * 也绝不当成"某个默认数"——默认值是 {@link #resolveCap} 那一步才落的，落在这里会让
     * "旧档"与"新档但玩家调到过默认值"两种状态在写档时无法区分（前者不该占键、后者必须占键）。
     */
    private static int readCap(NBTTagCompound entry) {
        return entry.hasKey(PocketConstants.FILTER_CAP, TAG_INT) ? entry.getInteger(PocketConstants.FILTER_CAP)
            : PocketConstants.FILTER_CAP_UNSET;
    }

    /**
     * ★R83 C2（判据 2 的单点）：把一条声明的原始上限换算成<b>真正生效的数</b>。
     * <p>
     * 未设 ⇒ 回落到"这一类今天的现全局量"：流体 = {@link PocketConstants#FILTER_CAP_CEILING_FLUID}
     * （16M/tank）、源质 = {@link PocketConstants#FILTER_CAP_CEILING_ESSENCE}
     * （★R92-② 起 = 每格存储上限 {@code ESSENCE_CAP_PER_TAG} = 256 点；R84～R91 期间是取瓶动作上界 64）、
     * 物品 = 调用方取好的 {@code itemMaxStackSize}（本类是纯 JVM 件，解不出 {@code ItemStack}，
     * 也不猜一个 64 —— 物品的现全局量<b>本来就是每件自己带的</b>）。
     * <p>
     * ★消费侧（{@code PocketAeChannelOps} 的批次收口）与显示侧（三类虚像右上角的橙色读数）都必须走
     * 本方法，否则"看到的上限"与"填到多少才停"就是两处真相。
     *
     * @param filter           声明本身；{@code null} ⇒ 返回 {@link PocketConstants#FILTER_CAP_UNSET}（没有声明就没有上限可读）
     * @param itemMaxStackSize 物品支的现全局量（该物品自己的堆叠上限；其余两类的入参被忽略）
     */
    public static int resolveCap(Filter filter, int itemMaxStackSize) {
        return filter == null ? PocketConstants.FILTER_CAP_UNSET
            : resolveRawCap(filter.kind(), filter.cap(), itemMaxStackSize);
    }

    /**
     * {@link #resolveCap(Filter, int)} 的"没有 Filter 在手"形态：格件（三类虚像）只持有服务端同步来的
     * <b>原始值</b>与自己的区域，不必为了读一个数再造一条声明。
     * <p>
     * ★判据仍然只有一条：未设 ⇒ {@link #defaultCap}；已设 ⇒ 原值。两条出口都只写在这一个方法里。
     */
    public static int resolveRawCap(Kind kind, int rawCap, int itemMaxStackSize) {
        if (kind == null) {
            return PocketConstants.FILTER_CAP_UNSET;
        }
        return rawCap == PocketConstants.FILTER_CAP_UNSET ? defaultCap(kind, itemMaxStackSize) : rawCap;
    }

    /**
     * "没人调过"时这一类的现全局量（★与 {@link #resolveCap} 同一条判据的另一半，只在未设时被读到）。
     * <p>
     * 物品支的兜底：{@code itemMaxStackSize <= 0}（纯 JVM 桩件、解不出的物品）时给
     * {@link PocketConstants#FILTER_CAP_MIN}，即"一次 1 件"——比"回落到 0 ⇒ 这一条永远不拉"诚实。
     */
    public static int defaultCap(Kind kind, int itemMaxStackSize) {
        if (kind == null) {
            return PocketConstants.FILTER_CAP_MIN;
        }
        return switch (kind) {
            case ITEM -> Math.max(PocketConstants.FILTER_CAP_MIN, itemMaxStackSize);
            case FLUID -> PocketConstants.FILTER_CAP_CEILING_FLUID;
            case ESSENCE -> PocketConstants.FILTER_CAP_CEILING_ESSENCE;
        };
    }
}
