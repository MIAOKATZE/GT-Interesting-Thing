package com.miaokatze.gtit.common.items.pocket;

/**
 * 猫猫次元口袋的数据模型口径常量：口袋物品 NBT 键名、容量与时序数值。
 * <p>
 * 键名一旦落档即不可改（与 {@code InfinityCellConstants} 同理），因此集中在此处钉死，
 * 由 {@code NekoPocketModelTest} 按字面量断言。
 * <p>
 * 与元件侧的关系：绑定表存<b>元件自身的 {@code diskuuid}</b>（身份）与一份可选的<b>位置快照</b>
 * （{@link #ENTRY_DIM}/{@link #ENTRY_X}/{@link #ENTRY_Y}/{@link #ENTRY_Z}/{@link #ENTRY_SLOT}，
 * 未定位时整体置 {@link #UNLOCATED} 哨兵），元件内容仍由
 * {@code common/items/infinitycell/StorageManager} 的外置桶持有，本包不重复持久化。
 * <p>
 * ⚠ <b>位置快照不是"当前在哪"的真相</b>：当前生效位置的唯一来源是
 * {@code PocketCellProbe}（推送式观测），GUI 右栏的「维度+xyz」也只读探针；绑定表里的位置
 * 只是落档缓存（供跨重启提示上次位置与"位置已失效，请重新绑定"判定）。两处真相即 bug 面。
 */
public final class PocketConstants {

    // ------------------------------------------------------------------ 绑定表
    /** 绑定表外层键，元素为 {@link #ENTRY_ID}/{@link #ENTRY_MODE}/位置快照五键的复合条目列表。 */
    public static final String BOUND_CELLS = "boundCells";
    /** 绑定条目：元件身份，即元件 NBT 里的 {@code diskuuid} 字符串。 */
    public static final String ENTRY_ID = "id";
    /** 绑定条目：载荷模式字节（预留位，本期恒 {@link #MODE_DISK_UUID}；<b>不是</b>表级互斥枚举）。 */
    public static final String ENTRY_MODE = "mode";
    /** 绑定条目：位置快照的维度（{@link #UNLOCATED} 表示尚未定位）。 */
    public static final String ENTRY_DIM = "dim";
    /** 绑定条目：位置快照的 x（{@link #UNLOCATED} 表示尚未定位）。 */
    public static final String ENTRY_X = "x";
    /** 绑定条目：位置快照的 y。 */
    public static final String ENTRY_Y = "y";
    /** 绑定条目：位置快照的 z。 */
    public static final String ENTRY_Z = "z";
    /** 绑定条目：元件在宿主容器 {@code IInventory} 里的槽号。 */
    public static final String ENTRY_SLOT = "slotIndex";
    /**
     * 位置快照的"未定位"哨兵：{@code Integer.MIN_VALUE}（即 {@code -2147483648}）。
     * <p>
     * 不能用 0（0 是合法维度号与合法坐标），也不能用 -1（-1 是合法的无效维与下标）；
     * 未定位时 {@code dim/x/y/z/slotIndex} 五键<b>整体</b>置该值，读档缺键同样回落为它。
     */
    public static final int UNLOCATED = Integer.MIN_VALUE;
    /** 绑定模式：按元件 {@code diskuuid} 字符串绑定（本项目自家元件，唯一载荷，见 R39c）。 */
    public static final byte MODE_DISK_UUID = 0;
    /** 绑定模式：按 (dim,pos,slot)+itemId+meta 绑定第三方原装元件，<b>本期不实现</b>。 */
    public static final byte MODE_POSITION = 1;

    // ------------------------------------------------------------------ 源质表
    /** 源质存储（CompoundTag），内部只挂一个 {@link #ASPECTS} 列表（TC {@code AspectList} 形状）。 */
    public static final String ESSENCE = "ess";
    /**
     * TC {@code AspectList.writeToNBT} 的列表键名（实测 {@code AspectList.java:257-269}：
     * {@code nbttagcompound.setTag("Aspects", list)}，每项 {@code key}/{@code amount}）。
     */
    public static final String ASPECTS = "Aspects";
    /** {@code AspectList} 条目键：aspect 的 tag <b>字符串</b>（绝不存裸索引，同 R17 理由）。 */
    public static final String ASPECT_KEY = "key";
    /** {@code AspectList} 条目值：点数值（{@code setShort}）。 */
    public static final String ASPECT_AMOUNT = "amount";

    // ------------------------------------------------------------------ 冷却
    /** 瞬时通道的设备维冷却起点（墙钟毫秒），随口袋物品走（R16 的设备维 + R24 的墙钟口径）。 */
    public static final String LAST_BURST_AT_MS = "lastBurstAtMs";

    // ------------------------------------------------------------------ 配置过滤器（ghost 声明）
    public static final String FILTERS = "filters";
    public static final String FILTER_ITEMS = "items";
    public static final String FILTER_FLUIDS = "fluids";
    public static final String FILTER_ESSENTIA = "essentia";
    /**
     * ghost 声明条目：<b>被就地转换的那个既有槽的索引</b>（R38 第 1 条）。
     * <p>
     * 语义是「就地把既有槽转 ghost」，所以每条声明必须记住自己占的是哪一格；索引空间由
     * {@code kind} 决定（物品 = 中栏槽、流体 = 流体条、源质 = 源质格，当前一一对应）。
     * 0 是合法索引，故「未设置」用 {@link #FILTER_SLOT_UNSET} 而不是 0。
     */
    public static final String FILTER_SLOT = "slotIndex";
    /** ghost 声明缺失槽索引时的标记（陈旧/外来档）：与合法索引 0 可区分，读到即丢弃该条。 */
    public static final int FILTER_SLOT_UNSET = -1;
    public static final String FILTER_ITEM_ID = "itemId";
    public static final String FILTER_META = "meta";
    public static final String FILTER_NBT = "nbt";
    public static final String FILTER_FLUID = "fluid";
    /** 源质配置的通道标识（typeId <b>字符串</b>）；与元件侧 {@code typeid} 同义但键名独立。 */
    public static final String FILTER_TYPE_ID = "typeId";
    /** 源质配置的 aspect tag 字符串。 */
    public static final String FILTER_TAG = "tag";

    // ------------------------------------------------------------------ 容量与时序
    /** 绑定条目上限：超出即拒绝新绑定（防 NBT 无界膨胀，元件侧同口径按条数收口）。 */
    public static final int MAX_BOUND_CELLS = 64;
    /** 每格源质上限，对齐 TC4 Warded Jar 的 {@code maxAmount=64}。 */
    public static final int ESSENCE_CAP_PER_TAG = 64;
    /** 显示格数（需求 12 行 × 4 列）；<b>只用于 GUI 行序</b>，存储与显示解耦，多于 48 的 tag 仍存不显。 */
    public static final int ESSENCE_DISPLAY_GRID = 48;

    // ---------------------------------------------------------- ghost 可占索引白名单（R38 第 4 条）
    /**
     * 物品支可被就地转 ghost 的索引上界 = 中栏 16 行 × 8 列（R43b 的真实槽口径）。
     * <p>
     * 这三个上界就是「允许被拖的索引集合」白名单本体：R38 第 4 条的可逆开关（X「独立配置槽区」
     * 与 Y「就地转换」两种读法）只改这里的区间，不改数据结构与抽取逻辑。
     */
    public static final int GHOST_ITEM_SLOT_LIMIT = 128;
    /** 流体支：流体条自身是一格（左栏那 8 格是真实储罐交互格，不是 ghost 目标）。 */
    public static final int GHOST_FLUID_SLOT_LIMIT = 1;
    /** 源质支：48 格显示位（与 {@link #ESSENCE_DISPLAY_GRID} 同值但语义独立）。 */
    public static final int GHOST_ESSENCE_SLOT_LIMIT = 48;
    /** 短效通道持续秒数。 */
    public static final int SHORT_CHANNEL_SECONDS = 30;
    /** 短效通道节拍：每 20 tick 一批（= 每秒一批，共 {@value #SHORT_CHANNEL_SECONDS} 批）。 */
    public static final int CHANNEL_TICK_PERIOD = 20;
    /** 短效通道批次数上限 = 秒数（一批一秒）。 */
    public static final int SHORT_CHANNEL_BATCHES = SHORT_CHANNEL_SECONDS;
    /** 瞬时通道冷却秒数（墙钟口径）。 */
    public static final int BURST_COOLDOWN_SECONDS = 10;
    /** 瞬时通道动画显示窗口毫秒（仅内存，随冷却同包发给客户端）。 */
    public static final long BURST_ANIMATION_MS = 5000L;
    /** 轮转游标表的清理阈值：只在条目过多时整体清空，不参与序号推进语义。 */
    public static final int MAX_ROTATION_ENTRIES = 128;

    // ------------------------------------------------------ S3/S4 追加（口袋自身的档内区）
    //
    // 以下键由 S3（物品 tick / 图标位）与 S4（GUI 内存数组 ↔ NBT）追加，**只是新增，未改动任何既有键
    // 的形状与语义**（pocket-plan §2 的「可加不可改」）。放这里的理由与全类一致：NBT 键名一旦落档即
    // 不可改，且 GUI 侧禁止再写字面量（slice-s4-brief §1.1「PocketConstants：全部 NBT 键名单源」）。
    // S6（通道写栏位）与 S7（蒸馏产出落格）读同一批键，不再另立第二份名单。

    /** 中栏 128 格内容（R53a/R53b：有界随身容器存物品 NBT；读写口径见 {@link #UI_WORK_TICKS} 的 R53c）。 */
    public static final String ITEM_CONTENTS = "contents";
    /** 左栏 8 个同权流体交互格（R39a）；关闭界面后仍留在档内，不销毁玩家放进来的储罐。 */
    public static final String FLUID_INTERACTION_SLOTS = "interactionSlots";
    /** 右栏蒸馏输入 3 行 × 4 列 = 12 格（§14.3）；R44c 的拒容器判定发生在槽过滤，不在档形状。 */
    public static final String DISTILL_INPUT_SLOTS = "distillInput";
    /** 右下绑定格（需求 5）：R40a 非消耗，绑定动作读完 ID 就把原栈放回玩家处。 */
    public static final String BIND_SLOT = "bindSlot";
    /** 左栏流体条内容（{@code FluidStack} 形状：{@code FluidName}/{@code Amount}）。 */
    public static final String FLUID_BAR = "fluidBar";
    /** 流体条容量（mB）。设计期取值，非裁定项（§16 只锁了几何不锁容量）⇒ 已回报主代理。 */
    public static final int FLUID_BAR_CAPACITY_ML = 16000;

    // ------------------------------------------------------ R24 的「剩余 tick」与 R37 的状态位
    /**
     * 短效通道剩余 tick（R24：倒计时用 NBT 剩余 tick，不用世界绝对时刻）。
     * <p>
     * 由 S6 在激活时写入、由 {@code ItemNekoDimensionPocket.onUpdate} 每 tick 递减，归零即
     * {@code removeTag} 自清理。同时是 R37 {@code work} 位的一个来源。
     */
    public static final String UI_WORK_TICKS = "workTicks";
    /**
     * 瞬时通道 burst 动画的剩余显示 tick（R24 的第二键；5 秒 = 100 tick，由 S6 写入）。
     * <p>
     * <b>故意用 tick 镜像而不是把墙钟 {@link #LAST_BURST_AT_MS} 直接喂给图标</b>：客户端与服务器的
     * 墙钟不同步，读墙钟会让 {@code work} 位在两端给出不同答案（图标是双端都调的
     * {@code getIconIndex}）。冷却判定仍走墙钟（跨重启有效，R16/R24）。
     */
    public static final String UI_BURST_SHOW_TICKS = "burstShowTicks";
    /** GUI 打开期标记（R37 的 {@code open} 位）：字节 0/1，关屏清（落点 {@code onModularContainerClosed}，R35）。 */
    public static final String UI_OPEN = "uiOpen";
    /** burst 动画显示窗口的 tick 数 = {@link PocketConstants#BURST_ANIMATION_MS} / 50。 */
    public static final int BURST_SHOW_TICKS = (int) (BURST_ANIMATION_MS / 50L);

    // ------------------------------------------------------ 通道成本（R58b 命名化，禁内联）
    //
    // 需求 3 的两个成本值原本只活在计划 §7 S6 的散文里（R58b 实测「全仓零命中」）。lang 契约早期版本
    // 还把 8 / 2 / 30 / 64 直接写死在文案里，现已改为 %d 占位（pocket-lang-keys.md §7 第 5 条），
    // 由 Java 侧用下面这两个常量格式化 ⇒ **数值只有一个权威**，改成本不会出现"改了常量不改文案"。

    /** 瞬时通道成本：猫猫币（需求 3 字面「-8 猫猫币」；扣费顺序照 R14 点检前置）。 */
    public static final int BURST_COST_NEKO = 8;
    /** 短效通道成本：闪烁猫猫币（需求 3 字面「-2 闪烁猫猫币」，持续 {@link #SHORT_CHANNEL_SECONDS} 秒）。 */
    public static final int SHORT_COST_SHIMMERING_NEKO = 2;

    // ------------------------------------------------------ D 批（S5/S6/S7）追加的单点口径
    //
    // 这一段的每一条都是"某个数字或键式样只允许活在这里"的产物：GUI 侧、driver 侧、ops 侧
    // 任何一方再写一遍就是两处真相（R58b 同一纪律）。

    /**
     * 拉取模式下单条 ghost 声明一次最多要多少。
     * <p>
     * 取"不设限"是刻意的：真实批次量由 {@code PocketAeChannelOps.extract} 按<b>声明物自身</b>收口
     * （物品 = {@code maxStackSize}、流体 = 流体条剩余空间、源质 = 单堆晶化源质上限），
     * 在这里写死一个数只会与那三处各自的上限打架（计划 §7 S6 第 3 条的"批次量 = 声明物 maxStackSize"）。
     */
    public static final int REFILL_AMOUNT_PER_FILTER_UNBOUNDED = Integer.MAX_VALUE;
    /** 源质格点击取出的产量：1 点 = 1 晶（R44e③，与 {@code TaumDistillRules.CRYSTAL_CAPACITY} 同值不同语义）。 */
    public static final int ESSENCE_OUT_UNIT_POINTS = 1;
    /** 源质格 Shift 取出的产量：一次取满一格（{@link #ESSENCE_CAP_PER_TAG} 点 = 一整堆晶）。 */
    public static final int ESSENCE_OUT_SHIFT_POINTS = ESSENCE_CAP_PER_TAG;
    /**
     * {@code ESSENCE_OUT} 动作参数里"按下 Shift"的偏移量（{@code arg = cell + 本值}）。
     * <p>
     * 取 {@link #ESSENCE_DISPLAY_GRID} 是因为格数天然小于它 ⇒ 一个 int 里同时装下"哪一格"与"要不要多取"，
     * 不必再加第二个同步键（两个值之间有竞态，见 {@code NekoPocketPanel.ACTION_ARG_BASE} 的取舍）。
     */
    public static final int ESSENCE_OUT_SHIFT_FLAG = ESSENCE_DISPLAY_GRID;
    /**
     * ghost 就地转换的 C2S 请求串分隔符（{@code SET|slotIndex|载荷键} 与 {@code CLR|slotIndex|区域字母}）。
     * <p>
     * 只在"客户端 → 服务端"这一根同步通道上出现（{@code SYNC_GHOST_REQUEST}），
     * <b>不是 NBT 键名</b>；落档形状由 {@code PocketFilterConfig} 自己负责。
     * 载荷键内部用 {@code ':'} 分段（同 {@code PocketFilterConfig.SEPARATOR}），故本式样一律用 {@code '|'}。
     */
    public static final String GHOST_REQUEST_SEPARATOR = "|";
    /** ghost 请求：把这一格就地转成配置格（NEI 左键拖入）。 */
    public static final String GHOST_REQUEST_SET = "SET";
    /**
     * ghost 请求：解绑这一格（右键，判定与执行都在服务端）。
     * <p>
     * ★第三段<b>必须</b>带区域字母（下面三个 {@code GHOST_KIND_*}）：三个区域的槽索引各从 0 起，
     * 裸 {@code CLR|0} 分不清"清中栏第 0 格"还是"清流条第 0 格"（同
     * {@code PocketFilterConfig} 的 {@code (kind, slotIndex)} 复合键，R59b 偏离④ / R70）。
     */
    public static final String GHOST_REQUEST_CLEAR = "CLR";
    /**
     * CLR 第三段的区域字母：中栏物品格。
     * <p>
     * 三个字母是<b>单源</b>常量——客户端拼装与服务端解析都经
     * {@code gui/pocket/PocketGhostRequest} 的 {@code letterOf/kindOf} 映射，两端都不写字面量
     * （R58b 的"同一个值只活在一处"纪律，作用域从成本常量扩到文法字母）。
     */
    public static final String GHOST_KIND_ITEM = "I";
    /** CLR 第三段的区域字母：左栏流体条。 */
    public static final String GHOST_KIND_FLUID = "F";
    /** CLR 第三段的区域字母：右栏源质格。 */
    public static final String GHOST_KIND_ESSENCE = "E";

    private PocketConstants() {}
}
