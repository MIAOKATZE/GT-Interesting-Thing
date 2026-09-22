package com.miaokatze.gtit.common.items.pocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

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

    public static PocketFilterConfig readFrom(NBTTagCompound root) {
        final PocketFilterConfig config = new PocketFilterConfig();
        if (root == null) {
            return config;
        }
        final NBTTagCompound domain = root.getCompoundTag(PocketConstants.FILTERS);
        final NBTTagList items = domain.getTagList(PocketConstants.FILTER_ITEMS, TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            final NBTTagCompound entry = items.getCompoundTagAt(i);
            final int slot = readSlot(entry);
            config.add(
                slot,
                new ItemFilter(
                    slot,
                    entry.getInteger(PocketConstants.FILTER_ITEM_ID),
                    entry.getInteger(PocketConstants.FILTER_META),
                    entry.getString(PocketConstants.FILTER_NBT),
                    readCap(entry)));
        }
        final NBTTagList fluids = domain.getTagList(PocketConstants.FILTER_FLUIDS, TAG_COMPOUND);
        for (int i = 0; i < fluids.tagCount(); i++) {
            final NBTTagCompound entry = fluids.getCompoundTagAt(i);
            final String name = entry.getString(PocketConstants.FILTER_FLUID);
            if (!name.isEmpty()) {
                final int slot = readSlot(entry);
                config.add(slot, new FluidFilter(slot, name, readCap(entry)));
            }
        }
        final NBTTagList essentia = domain.getTagList(PocketConstants.FILTER_ESSENTIA, TAG_COMPOUND);
        for (int i = 0; i < essentia.tagCount(); i++) {
            final NBTTagCompound entry = essentia.getCompoundTagAt(i);
            final String typeId = entry.getString(PocketConstants.FILTER_TYPE_ID);
            final String tag = entry.getString(PocketConstants.FILTER_TAG);
            if (!typeId.isEmpty() && !tag.isEmpty()) {
                final int slot = readSlot(entry);
                config.add(slot, new EssenceFilter(slot, typeId, tag, readCap(entry)));
            }
        }
        return config;
    }

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
     * （16M/tank）、源质 = {@link PocketConstants#FILTER_CAP_CEILING_ESSENCE}（64 点）、
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
