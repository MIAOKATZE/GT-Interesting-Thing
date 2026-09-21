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
 * {@code PocketCellProbe}（推送式观测），绑定按钮 tooltip 的「维度+xyz」也只读探针；绑定表里的位置
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
    /**
     * ★<b>R78③ 新增</b>：源质<b>格位归属表</b>（NBTTagList of String，元素 = aspect tag 字符串，
     * 下标 = 显示格号 0…{@link #ESSENCE_DISPLAY_GRID}−1）。
     * <p>
     * 存在的理由是"格序 = 该 tag <b>首次入账</b>的顺序"这条用户需求：{@link #ASPECTS} 列表的
     * 顺序<b>不能</b>承担它，因为扣到 0 的 tag 会被摘掉（0 值不落档），下次入账就会跑到列表末尾。
     * 本键独立持久化，<b>撤空不回收格位</b>（R78③ 的用户裁定），重开面板/重进世界都不重排。
     * <p>
     * 三条硬口径：① 只存 tag 字符串（绝不存裸索引，同 {@link #ASPECT_KEY} 的理由）；
     * ② 长度上界 {@link #ESSENCE_DISPLAY_GRID}，越界条目读档时丢弃；
     * ③ 旧档缺本键 ⇒ 由 {@code PocketEssenceStore} 按 {@link #ASPECTS} 的<b>入账顺序</b>补出
     * 格位（LinkedHashMap 的插入序即首入账序）， ⇒ 旧档不重排、不丢格。
     */
    public static final String ESSENCE_CELL_ORDER = "essCellOrder";

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
     * {@code kind} 决定（物品 = 中栏 150 格、流体 = {@link #FLUID_TANK_TOTAL} 个流体槽/tank、
     * 源质 = {@link #ESSENCE_DISPLAY_GRID} 格，当前一一对应）。
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
    // ---------------------------------------------------------- R75/R78 钉死的列数（几何与索引空间同源）
    /**
     * 流体<b>组数</b>（R78②：由 1 组增至 <b>3 组</b>，每组 = 输入行 18 + 流体槽 36 + 输出行 18 = 72 高，
     * 组间距 18 ⇒ 左栏 {@code 3×72 + 2×18 = 252 ≤ 270}，余 18 给状态行）。
     * <p>
     * 与 {@link #FLUID_COLUMN_COUNT} 一样是"格数 / tank 数 / ghost 索引空间"三者的唯一来源：
     * {@link #FLUID_TANK_TOTAL}、{@link #FLUID_INTERACTION_TOTAL}、
     * {@link #GHOST_FLUID_SLOT_LIMIT} 全部从它派生，不留字面量。
     */
    public static final int FLUID_GROUP_COUNT = 3;
    /**
     * 每组流体的<b>列数</b> = 每组一个流体槽一列 = 每组 {@code 6} 个独立 tank
     * （R75① 定为 6 列，R78 只加组数、<b>没有</b>改列数 ⇒ 与源质盘同为 6 列，"规整"由列数对齐达成）。
     * <p>
     * 本常量是"每列一个 tank""流体 ghost 索引上界""交互格格数"三者的<b>唯一</b>来源：
     * {@link #GHOST_FLUID_SLOT_LIMIT} 与 {@code PocketInventory.FLUID_INTERACTION_SLOTS} 都从它派生，
     * 改列数不会出现"加了格子却忘了同步索引空间"的分叉。
     */
    public static final int FLUID_COLUMN_COUNT = 6;
    /** 每个流体列携带的交互格数（1 输入 + 1 输出；两格都仍是 {@code R39a} 的双用格）。 */
    public static final int FLUID_INTERACTION_PER_COLUMN = 2;
    /**
     * 独立流体 tank <b>总数</b> = {@link #FLUID_GROUP_COUNT} × {@link #FLUID_COLUMN_COUNT} = <b>18</b>
     * （R78②）。也是 {@code Kind.FLUID} 的 ghost 索引空间与总容量口径的基数
     * （总容量 = {@link #FLUID_TANK_TOTAL} × {@link #FLUID_BAR_CAPACITY_ML}）。
     */
    public static final int FLUID_TANK_TOTAL = FLUID_GROUP_COUNT * FLUID_COLUMN_COUNT;
    /** 流体侧交互格<b>总数</b> = {@link #FLUID_TANK_TOTAL} tank × {@link #FLUID_INTERACTION_PER_COLUMN} 格 = <b>36</b>。 */
    public static final int FLUID_INTERACTION_TOTAL = FLUID_TANK_TOTAL * FLUID_INTERACTION_PER_COLUMN;
    /**
     * 源质盘列数（R78：与流体块<b>同为 6 列</b>，规整由列数对齐达成；派生自
     * {@link #FLUID_COLUMN_COUNT} ⇒ 改流体列数会连带改这里，不会出现两处真相）。
     */
    public static final int ESSENCE_GRID_COLUMNS = FLUID_COLUMN_COUNT;
    /** 源质盘行数（R78②：8 → <b>12</b> 行；12 行 + 空 1 行 + 蒸馏 2 行 = 15 行 = 与中栏同高）。 */
    public static final int ESSENCE_GRID_ROWS = 12;
    /**
     * 显示格数 = <b>{@link #ESSENCE_GRID_COLUMNS} 列 × {@link #ESSENCE_GRID_ROWS} 行 = 72</b>
     * （R78② 由 48 增至 72；<b>只用于 GUI 行序与格位空间</b>，存储与显示解耦：多于 72 的 tag
     * 仍照常入账，只是不显示，见 {@code gtit.pocket.aspect.overflow_note} 那条兜底文案）。
     * <p>
     * ★72 ≥ 用户实测包内的 aspect 注册数 69 ⇒ 溢出兜底在常态下不再触发，但<b>代码路径保留</b>
     * （addon 追加条目时仍可能超出；格数恒定这一层是 R32 双端同树的前提，不随内容伸缩）。
     */
    public static final int ESSENCE_DISPLAY_GRID = ESSENCE_GRID_ROWS * ESSENCE_GRID_COLUMNS;
    /** 玩家背包的<b>列数</b>（R78①：底部带中间段 9 列 × 4 行 = 162×72，正好等于带高）。 */
    public static final int PLAYER_BACKPACK_COLUMNS = 9;
    /** 玩家背包的<b>行数</b>（R78① 回归；★连带 E4 包放大风险，见 {@link #PLAYER_BACKPACK_SLOTS} 的说明）。 */
    public static final int PLAYER_BACKPACK_ROWS = 4;
    /**
     * 玩家背包进 Container 的真实槽数 = {@link #PLAYER_BACKPACK_COLUMNS} × {@link #PLAYER_BACKPACK_ROWS}
     * = <b>36</b>。
     * <p>
     * ★这 36 格<b>不由本仓的槽工厂造</b>：MUI2 的 {@code ModularSyncManager#construct} 在没有预先
     * 注册 {@code player_inventory} 槽组时会自动 {@code bindPlayerInventory}（{@code ISyncRegistrar}
     * 字节码：循环 {@code bipush 36} 逐格 {@code itemSlot("player", i, …)}）。R78① 撤销 R69-D2
     * （旧实现预注册一个<b>空</b> {@code PlayerSlotGroup} 让那一支跳过）⇒ 本常量的职责是把
     * "框架给的 36" 与"面板画的 9×4"钉成同一个数：两者一旦不等，就会有一部分背包格<b>只存在于
     * Container 而看不见</b>（隐形槽 = shift 落点与排序都会打到看不见的格）。
     * <p>
     * ★<b>代价（R78① 点名，不得静默）</b>：E4 风险回归——首开同步 36 格 + vanilla
     * {@code Container#detectAndSendChanges} 每 tick 对 36 格做 {@code ItemStack} 相等比较
     * （<b>含整份 NBT 深比较</b>），内容一变就把整枚口袋连同 199 格一起重发。
     * 这是用户为"要玩家背包"明确换回来的代价，README 与交付说明同处点名。
     */
    public static final int PLAYER_BACKPACK_SLOTS = PLAYER_BACKPACK_COLUMNS * PLAYER_BACKPACK_ROWS;

    // ---------------------------------------------------------- ghost 可占索引白名单（R38 第 4 条）
    /**
     * 物品支可被就地转 ghost 的索引上界 = 中栏 <b>15 行 × 10 列 = 150</b>（R75 的真实槽口径，
     * 覆盖 R43b/R74 的 128 与 160；R78 只动流体/源质/背包，<b>中栏 15 行一行不删</b>）。
     * <p>
     * 这三个上界就是「允许被拖的索引集合」白名单本体：R38 第 4 条的可逆开关（X「独立配置槽区」
     * 与 Y「就地转换」两种读法）只改这里的区间，不改数据结构与抽取逻辑。
     * <p>
     * ★它同时是 {@code PocketInventory.STORAGE_SLOTS} 的单源（格数 = ghost 索引空间，一一对应），
     * 所以"128→150"这一改必然连带中栏矩阵、Container 槽数与写档形状；由
     * {@code NekoPocketModelTest} 的加总用例钉住（150 = 15 × 10，且
     * <b>235 = 150 + 36 + 12 + 1 + 36</b>，R78 的口径）。
     */
    public static final int GHOST_ITEM_SLOT_LIMIT = 150;
    /** 流体支：一个流体列一个 ghost 索引位（0…{@link #FLUID_TANK_TOTAL}−1；R78② 由 6 变 <b>18</b>）。 */
    public static final int GHOST_FLUID_SLOT_LIMIT = FLUID_TANK_TOTAL;
    /**
     * 源质支：72 格显示位（与 {@link #ESSENCE_DISPLAY_GRID} 同值但语义独立；R78② 由 48 变 <b>72</b>）。
     * <p>
     * ★索引空间的<b>格号</b>现在是"该 tag 首次入账拿到的格位"（R78③，持久化在
     * {@link #ESSENCE_CELL_ORDER}），不再是 {@code TaumCompat.aspectOrder()} 的固定派生序；
     * 变的是<b>哪个 tag 落在第几格</b>，格数与索引上界恒定 ⇒ R32 的双端同树前提不受影响。
     */
    public static final int GHOST_ESSENCE_SLOT_LIMIT = ESSENCE_DISPLAY_GRID;
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
    /**
     * 轮转游标表的清理阈值：只在条目过多时整体清空，不参与序号推进语义。
     * <p>
     * ★<b>与格子数无关，也不是格数</b>（R75 点名的批量替换豁免项）：中栏由 128 变 150 时
     * 这一行<b>必须保持 128</b>，它是"游标表条目数上限"，与 {@link #GHOST_ITEM_SLOT_LIMIT}
     * 只是数值巧合。任何"128→150"的批量替换波及到这里，都会静默改变轮转清理的节拍。
     * ★<b>R78 再点名一次</b>：本轮把源质 48→72、流体 6→18、真实槽 175→<b>235</b>，
     * 235 与 128 无关、72 与 128 也无关，本行<b>仍不得</b>被任何"顺手一起改"波及。
     */
    public static final int MAX_ROTATION_ENTRIES = 128;

    // ------------------------------------------------------ S3/S4 追加（口袋自身的档内区）
    //
    // 以下键由 S3（物品 tick / 图标位）与 S4（GUI 内存数组 ↔ NBT）追加，**只是新增，未改动任何既有键
    // 的形状与语义**（pocket-plan §2 的「可加不可改」）。放这里的理由与全类一致：NBT 键名一旦落档即
    // 不可改，且 GUI 侧禁止再写字面量（slice-s4-brief §1.1「PocketConstants：全部 NBT 键名单源」）。
    // S6（通道写栏位）与 S7（蒸馏产出落格）读同一批键，不再另立第二份名单。

    /** 中栏 150 格内容（R53a/R53b：有界随身容器存物品 NBT；读写口径见 {@link #UI_WORK_TICKS} 的 R53c）。 */
    public static final String ITEM_CONTENTS = "contents";
    /**
     * 流体列的交互格（R78②：{@link #FLUID_GROUP_COUNT} 组 × {@link #FLUID_COLUMN_COUNT} 列 ×
     * {@link #FLUID_INTERACTION_PER_COLUMN} 格 = <b>36</b> 格）；关闭界面后仍留在档内，
     * 不销毁玩家放进来的储罐。
     * <p>
     * ★键名沿用旧单 tank 时代的 {@code interactionSlots}，<b>形状</b>是按槽号索引的同一套
     * {@code ItemStackHandler} 形状 ⇒ 旧档的 12 格读进新 handler 的前 12 格，不丢件
     * （前 12 格在新排布下恰好还是"第 1 组的 6 进 + 6 出"，见
     * {@code PocketInventory#tankOfInteractionSlot}：组数=1 时它与旧的取模式逐字同解）。
     */
    public static final String FLUID_INTERACTION_SLOTS = "interactionSlots";
    /** 源质列的蒸馏输入 2 行 × 6 列 = 12 格（R75 只换排布，格数与 §14.3 一致）；R44c 的拒容器判定发生在槽过滤，不在档形状。 */
    public static final String DISTILL_INPUT_SLOTS = "distillInput";
    /** 底部带绑定格（需求 5）：R40a 非消耗，绑定动作读完 ID 就把原栈放回玩家处。 */
    public static final String BIND_SLOT = "bindSlot";
    /**
     * 流体槽内容（{@link #FLUID_TANK_TOTAL} 个独立 tank = 18 个）。
     * <p>
     * ★<b>形状两代并存，读侧必须分派</b>（R75 的存档兼容项，R78 把 tank 数由 6 增至 18 但
     * <b>没有</b>再换形状 ⇒ 同一套读法天然覆盖三代：单 compound、6 tank 列表、18 tank 列表）：
     * <ul>
     * <li><b>旧档</b>：本键下是一个 {@code NBTTagCompound}（tag id 10，单个 {@code FluidStack} 形状）
     * ⇒ 整份落到 <b>0 号 tank</b>，其余 tank 为空；</li>
     * <li><b>新档（也是唯一写出形状）</b>：本键下是一个 {@code NBTTagList}（tag id 9），
     * 每个元素 = {@code FluidStack} 形状 + {@link #FLUID_BAR_TANK} 槽号；<b>只写非空的 tank</b>，
     * 全空即 {@code removeTag}（与 {@code PocketInventory#saveGroup} 的"空区不留壳"同口径）。</li>
     * </ul>
     * 用带 {@link #FLUID_BAR_TANK} 的列表而不是"按位置对齐的定长列表"，是为了让"哪个 tank 有货"
     * 由条目自己说明 ⇒ 将来增减列数不会让后面所有 tank 的内容整体错位（R78 的 6→18 正是这一条的兑现）。
     */
    public static final String FLUID_BAR = "fluidBar";
    /** 新档里每个流体条目的 tank 序号键（int，0…{@link #FLUID_TANK_TOTAL}−1；缺键按 0 读并一次性 WARN）。 */
    public static final String FLUID_BAR_TANK = "tank";
    /**
     * <b>单个</b>流体 tank 的容量（mB）⇒ {@link #FLUID_TANK_TOTAL} 个 tank 的总量是它的 18 倍
     * （R78②：{@code 18 × 16,000,000 = 288,000,000}，旧"六槽合计 96,000,000"口径作废）。
     * <p>
     * ★<b>规格外自立项</b>（R75 明文，须"游戏内文案 + 交付说明"双处声明；R78 的合计变更同样双处同步）：
     * 值由旧口径 16,000 改为 <b>每槽 16,000,000</b>（×1000），来源是用户那句「流体槽容量 16M」，
     * <b>不是</b>需求原文数字。玩家可见侧的容量一律由本常量填进 {@code gtit.pocket.fluid.capacity}
     * 与 {@code item.neko_dimension_pocket.tooltip.9}（lang 不得写死规格数字）。
     */
    public static final int FLUID_BAR_CAPACITY_ML = 16_000_000;
    /**
     * 流体侧<b>总</b>容量（mB）= {@link #FLUID_TANK_TOTAL} × {@link #FLUID_BAR_CAPACITY_ML}
     * = {@code 18 × 16,000,000 =} <b>288,000,000</b>（R78②）。
     * <p>
     * ★存在的理由与 {@link #BURST_SHOW_TICKS} 同一条纪律：合计是"规格外自立项"的玩家可见读数，
     * 一旦在 lang 或 README 里另写一个数就是两处真相；玩家侧只允许由本常量填占位。
     */
    public static final int FLUID_TOTAL_CAPACITY_ML = FLUID_TANK_TOTAL * FLUID_BAR_CAPACITY_ML;

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
     * （物品 = {@code maxStackSize}、流体 = 本列流体槽剩余空间、源质 = 单堆晶化源质上限），
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
     * 裸 {@code CLR|0} 分不清"清中栏第 0 格"还是"清流体槽第 0 格"（同
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
    /** CLR 第三段的区域字母：流体槽（{@link #FLUID_TANK_TOTAL} 个 tank 各一位，R75① + R78②）。 */
    public static final String GHOST_KIND_FLUID = "F";
    /** CLR 第三段的区域字母：源质格。 */
    public static final String GHOST_KIND_ESSENCE = "E";

    private PocketConstants() {}
}
