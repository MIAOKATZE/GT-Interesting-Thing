package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;

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
     * 本键独立持久化 ⇒ 同一份档两次读出的格序一致；★<b>R86 改判</b>（作废 R78③「撤空不回收」）：点数
     * 扣到 0 会<b>当场释放</b>那一格（见 {@code PocketEssenceStore#extract}），所以"不重排"指的是
     * <b>有货的那些格</b>之间不重排，不含"腾出来的格永远属于那个 tag"。
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
     * {@code kind} 决定（物品 = 中栏 {@link #GHOST_ITEM_SLOT_LIMIT} 格、流体 = {@link #FLUID_TANK_TOTAL} 个流体槽/tank、
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
    /**
     * ★<b>R83 C2（缺陷 6 / D-5）新增</b>：单条 ghost 声明的<b>组上限</b>（TAG_Int=3），
     * 与 {@link #FILTER_SLOT} 同层登记（都是"每条声明自带"的字段，不是根级键）。
     * <p>
     * 语义 = <b>该条声明一次批次最多拉多少</b>（物品 = 件、流体 = mB、源质 = 点 = 晶）。
     * ★刻意<b>不</b>进 {@code PocketFilterConfig.Filter#key()}（载荷键是跨重启稳定标识，
     * {@code contains()} 与 {@code PocketAeChannelOps.contentKey} 都依赖它；把数量塞进载荷键会让
     * "同一种物品两条声明"分裂成不同身份）。
     * <p>
     * ★<b>缺键 = {@link #FILTER_CAP_UNSET} 而不是 0</b>（与 {@link #FILTER_SLOT}/{@link #FILTER_SLOT_UNSET}
     * 同一条套路）：未设的旧档条目必须回落到"该类<b>今天</b>的现全局量"，这样"引入可调上限"与
     * "改变现有填充行为"两件事不会互相掩盖。写档侧同样只在已设时落键 ⇒ 旧档形状逐字节不变
     * （回归用例 {@code filter_keys_carry_no_channel_index} 的"物品声明只允许
     * itemId/meta/nbt/slotIndex"白名单因此仍然成立）。
     */
    public static final String FILTER_CAP = "cap";
    /** {@link #FILTER_CAP} 的"未设置"哨兵（0 与正数都是合法读数，故未设必须可区分）。 */
    public static final int FILTER_CAP_UNSET = -1;
    /**
     * ★★<b>R91-⑤⑥（格子三属性）</b>：{@code FILTERS} 域下的<b>第四枚列表</b> —— 属性位表
     * （{@code attr} + {@code P}），与 {@link #FILTER_ITEMS}/{@link #FILTER_FLUIDS}/
     * {@link #FILTER_ESSENTIA} 三枚<b>载荷</b>列表平行。
     * <p>
     * ★为什么必须是一张<b>独立的位表</b>而不是往 {@code PocketFilterConfig.Filter} 里塞两个布尔
     * （取证 {@code r91-ret-gesture.md} §4 的方案 β 否决理由，被 R91-⑥ 采纳为裁定）：
     * <ol>
     * <li><b>空格也要能进状态</b>（用户原话"没东西则进入这个状态，然后 NEI 再拖动时记录为对应的状态"
     * = R91-b）——一条 <i>声明</i>的存在感来自它的载荷键，"无载荷的声明"在本仓表达不了，
     * 而位表的一个条目<b>不需要载荷</b>；</li>
     * <li>挤进 Filter 会正面撞两条既有执法点：{@code PocketGhostRequest#applyCap}（"没有声明就不许挂
     * 属性"）与 {@code #hasRequiredPayload}（不接受空载荷）；</li>
     * <li>会撞回归门禁 {@code assertNoChannelIndexCarried}（物品条目字段白名单
     * {@code itemId/meta/nbt/slotIndex}）——★位表是<b>另一个列表</b>，因此天然不触那条；</li>
     * <li>★反过来也成立：{@code attr} <b>不许</b>从 Filter 派生（R91-b 明文），否则空格无从表达。</li>
     * </ol>
     * ★写档口径与 {@link #FILTER_CAP} 同一条纪律：<b>只写非默认条目</b> ⇒ 没有属性的旧档形状<b>逐字节不变</b>
     * （成对门禁见 {@code verify-pocket.sh} 的 {@code R91-c} 段与用例
     * {@code ghost_flags_persistence_pairwise}）。
     */
    public static final String FILTER_FLAGS = "flags";
    /** 位表条目：<b>区域</b>（{@code Kind} 枚举名字符串，与 {@link #FILTER_SLOT} 一起定位一格）。 */
    public static final String FILTER_FLAG_KIND = "kind";
    /** 位表条目：{@link #GHOST_ATTR_BIND}/{@link #GHOST_ATTR_MEMORY}（TAG_Int）。 */
    public static final String FILTER_ATTR = "attr";
    /** 位表条目：阻拦上传位 {@code P}（TAG_Int 0/1，★与 {@link #FILTER_ATTR} 正交，可与任一 attr 并存）。 */
    public static final String FILTER_UPLOAD_BLOCK = "uploadBlock";
    /** attr 值域之一：无属性（默认值 ⇒ 不落档）。 */
    public static final int GHOST_ATTR_NONE = 0;
    /** attr 值域之一：<b>请求绑定</b>（★R91-⑤ 起由中键挂上）= 本格的补货请求，会从 AE 抽取。 */
    public static final int GHOST_ATTR_BIND = 1;
    /** attr 值域之一：<b>记忆</b>（alt+左键）= 本格只能放那一种东西，★纯过滤、不产生任何抽取。 */
    public static final int GHOST_ATTR_MEMORY = 2;
    /** attr 值域上界（含）：越界值一律按 {@link #GHOST_ATTR_NONE} 收口（伪造包/外来档）。 */
    public static final int GHOST_ATTR_MAX = GHOST_ATTR_MEMORY;

    /**
     * attr 原始值的<b>唯一</b>收口口：越界（含负数、外来档、伪造包）一律回落
     * {@link #GHOST_ATTR_NONE}，★不猜、不钳到"最近的一档"（那等于替玩家选属性）。
     * <p>
     * 单源的理由与 {@code PocketGhostRequest#clampCap} 同一条：读档侧、C2S 侧与位表读口都问这里，
     * 三处各写一遍 {@code if (raw < 0 || raw > 2)} 迟早分叉。
     */
    public static int normalizeGhostAttr(int raw) {
        return raw < GHOST_ATTR_NONE || raw > GHOST_ATTR_MAX ? GHOST_ATTR_NONE : raw;
    }

    // ------------------------------------------------------------------ 容量与时序
    /**
     * 绑定表的<b>数据结构</b>上限：超出即拒收，防 NBT 无界膨胀（元件侧同口径按条数收口）。
     * <p>
     * ★R84 起这一条<b>不再是玩家可感知的口径</b>，可感知的那一条是 {@link #ALLOWED_BOUND_CELLS}。
     * 两者刻意分开：把数据上限也砍成 1 会让<b>旧档里已有的多枚绑定在读档时被静默销毁</b>（丢的是玩家的
     * 绑定关系，且 JVM 侧 8 条数据层用例——顺序往返、逐条 mode、身份去重回填位置、外来档折叠——
     * 全靠多条目才表达得出来）。
     */
    public static final int MAX_BOUND_CELLS = 64;
    /**
     * ★R84 用户裁定：<b>一只口袋只允许绑定一枚元件</b>（"必须要求只能绑 1 个，不能对多个"）。
     * 钉在两个面上，不碰数据上限：
     * <ul>
     * <li>入口面 {@code PocketBindFlow#bind}：第二枚不同身份一律 {@code FULL}（配 {@code bind.full}
     * 的"先解绑"文案），解绑后才能换；</li>
     * <li>服务面 {@code PocketChannelRunner}：每批只服务绑定序<b>前 1 枚</b>，所以旧档里残留的多枚
     * 会变成"仍在表里、仍在绑定行上显示，但不再被搬运"的惰性条目（不销毁任何玩家数据）。</li>
     * </ul>
     */
    public static final int ALLOWED_BOUND_CELLS = 1;
    /**
     * 每格源质上限。★R84 裁定：64 → <b>256</b>（"单个源质格可以存储 256 个"），不再对齐 TC4 Warded Jar
     * 的 {@code maxAmount=64}；代价是同 tag 的 12 格逐格蒸馏候选更不容易互相挤掉。
     * ★<b>R95 S5：本常量是未升级口径</b>——STACK 升级位（用户原话"所有物品和源质最大单格堆叠数*16"，
     * 一位管两者）固化后为 {@link #ESSENCE_CAP_PER_TAG_UPGRADED}（4096），选择点
     * {@link #essenceCapPerTag(boolean)}；执法点在 {@code PocketEssenceStore#capPerTag()}（单源注入）。
     * <p>
     * ★取出侧与它<b>刻意解耦</b>：一次动作最多 {@link #ESSENCE_OUT_MAX_POINTS_PER_ACTION} 点，抬上限不等于
     * 一次能掏 256 点。★R88 载体改判后这一句换算成瓶：256 点 = {@link #ESSENCE_MAX_PHIALS_PER_TAG} 只满瓶，
     * 一次动作最多 {@link #ESSENCE_OUT_MAX_PHIALS_PER_ACTION} 只 —— 旧文案里"晶化源质单堆 64"那个理由
     * 已随载体一起退役（★R96 S9b 起晶在「结晶模式」这一条出口上恢复可产，但那条出口<b>不</b>走这里那套
     * 瓶粒度换算，也不改"就地排空"的禁令，见 {@code TaumBridge#newCrystalStack} 的 ★R96 S9b 注）。
     */
    public static final int ESSENCE_CAP_PER_TAG = 256;

    /** ★R95 S5：源质每格上限的唯一选择点（语义同 {@link #fluidTankCapacityMl(boolean)} 的那条纪律）。 */
    public static int essenceCapPerTag(boolean stackUpgraded) {
        return stackUpgraded ? ESSENCE_CAP_PER_TAG_UPGRADED : ESSENCE_CAP_PER_TAG;
    }

    // ---------------------------------------------------------- R75/R78 钉死的列数（几何与索引空间同源）
    /**
     * 流体<b>组数</b>为5；每组输入行18 + 流体槽36 + 输出行18 = 72高，
     * 组间距18，内容高432，以180高的独立滚动视窗展示。
     * <p>
     * 与 {@link #FLUID_COLUMN_COUNT} 一样是"格数 / tank 数 / ghost 索引空间"三者的唯一来源：
     * {@link #FLUID_TANK_TOTAL}、{@link #FLUID_INTERACTION_TOTAL}、
     * {@link #GHOST_FLUID_SLOT_LIMIT} 全部从它派生，不留字面量。
     */
    public static final int FLUID_GROUP_COUNT = 5;
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
     * 独立流体 tank <b>总数</b> = {@link #FLUID_GROUP_COUNT} × {@link #FLUID_COLUMN_COUNT} = <b>30</b>
     * （R78②）。也是 {@code Kind.FLUID} 的 ghost 索引空间与总容量口径的基数
     * （总容量 = {@link #FLUID_TANK_TOTAL} × {@link #FLUID_BAR_CAPACITY_ML}）。
     */
    public static final int FLUID_TANK_TOTAL = FLUID_GROUP_COUNT * FLUID_COLUMN_COUNT;
    /** 流体側交互格总数 = 30 tank × 2 格 = 60。 */
    public static final int FLUID_INTERACTION_TOTAL = FLUID_TANK_TOTAL * FLUID_INTERACTION_PER_COLUMN;
    /**
     * 源质盘列数（R78：与流体块<b>同为 6 列</b>，规整由列数对齐达成；派生自
     * {@link #FLUID_COLUMN_COUNT} ⇒ 改流体列数会连带改这里，不会出现两处真相）。
     */
    public static final int ESSENCE_GRID_COLUMNS = FLUID_COLUMN_COUNT;
    /** 源质盘20行；仅盘面滚动，进度条与蒸馏输入固定在列底部。 */
    public static final int ESSENCE_GRID_ROWS = 20;
    /**
     * 显示格数 = <b>{@link #ESSENCE_GRID_COLUMNS} 列 × {@link #ESSENCE_GRID_ROWS} 行 = 120</b>
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
     * （<b>含整份 NBT 深比较</b>），内容一变就把整枚口袋连同 184 格一起重发。
     * 这是用户为"要玩家背包"明确换回来的代价，README 与交付说明同处点名。
     */
    public static final int PLAYER_BACKPACK_SLOTS = PLAYER_BACKPACK_COLUMNS * PLAYER_BACKPACK_ROWS;

    // ---------------------------------------------------------- ghost 可占索引白名单（R38 第 4 条）
    /**
     * 中栏<b>列数</b>（R80 用户定稿：10 → <b>9</b>）。
     * <p>
     * ★与 {@link #STORAGE_ROWS} 一起是"矩阵列宽 / {@code SlotGroup.rowSize} / ghost 索引上界 /
     * 面板横向加总"四者的<b>唯一</b>来源：本轮的失败形态是"矩阵改成 9 字符、Container 仍按 10 列算
     * rowSize"（排序与 shift 落点会错位到看不见的格），故 {@code PocketSlots} 只做<b>转发</b>、
     * 不另立字面量，乘积关系由 {@code PocketSlots} 的静态断言与 {@code NekoPocketModelTest} 双向把守。
     */
    public static final int STORAGE_COLUMNS = 9;
    /** 中栏<b>行数</b>（R75 的"选项 A"：16 → 15，本轮 R80 <b>未动</b>；★纵向 360 是硬天花板）。 */
    public static final int STORAGE_ROWS = 23;

    /**
     * ★★<b>R92-⑤（D5）：格身份的三个读数单源</b> —— 空格子 tooltip 要说"这一格是什么"，
     * 行/列/格号就只能有一处算式。三个显示点各写一遍 {@code / 9 + 1} 的话，改一次行列数就有两说。
     * <p>
     * ★一律给 <b>1 起</b>的玩家可见口径（内部索引仍是 0 起）；★<b>两侧都夹</b>——未绑定（{@code < 0}）与
     * 越出盘面（{@code >= 行 × 列} / {@code >= ESSENCE_DISPLAY_GRID}）一律返回 0，
     * 显示侧按"0 = 读不出身份"自行早退，★不拿越界索引去凑一个看起来合法的行列号。
     */
    public static int storageRowOf(int slotIndex) {
        return !isStorageSlotInGrid(slotIndex) ? 0 : slotIndex / STORAGE_COLUMNS + 1;
    }

    /** ★R92-⑤：中栏格身份的列号（1 起；越界给 0，理由同 {@link #storageRowOf}）。 */
    public static int storageColumnOf(int slotIndex) {
        return !isStorageSlotInGrid(slotIndex) ? 0 : slotIndex % STORAGE_COLUMNS + 1;
    }

    /** ★R92-⑤：格号是否落在 {@link #STORAGE_ROWS} × {@link #STORAGE_COLUMNS} 这张盘内（两侧都夹）。 */
    private static boolean isStorageSlotInGrid(int slotIndex) {
        return slotIndex >= 0 && slotIndex < STORAGE_ROWS * STORAGE_COLUMNS;
    }

    /** ★R92-⑤：源质显示盘的格号（1 起，与 {@link #ESSENCE_DISPLAY_GRID} 那一盘同一读法；越界给 0）。 */
    public static int essenceCellNumberOf(int cellIndex) {
        return cellIndex < 0 || cellIndex >= ESSENCE_DISPLAY_GRID ? 0 : cellIndex + 1;
    }

    /**
     * 物品支可被就地转 ghost 的索引上界 = 中栏 <b>15 行 × 9 列 = 135</b>（R80 的用户定稿，
     * 覆盖 R75 的 150、R43b/R74 的 128 与 160；R78 只动流体/源质/背包未动中栏，本轮把列数由 10 收到 <b>9</b>）。
     * <p>
     * 这三个上界就是「允许被拖的索引集合」白名单本体：R38 第 4 条的可逆开关（X「独立配置槽区」
     * 与 Y「就地转换」两种读法）只改这里的区间，不改数据结构与抽取逻辑。
     * <p>
     * ★它同时是 {@code PocketInventory.STORAGE_SLOTS} 的单源（格数 = ghost 索引空间，一一对应），
     * 所以"10 列→9 列"这一改必然连带中栏矩阵、Container 槽数与写档形状；由
     * {@code NekoPocketModelTest} 的加总用例钉住（<b>135 = 15 × 9</b>，且
     * <b>220 = 135 + 36 + 12 + 1 + 36</b>，R80 的口径）。
     * <p>
     * ★<b>本轮与前几轮的方向相反：这次是收缩</b>。128→150 是"新形状更大"，旧档读进来永远不越界；
     * 150→135 是"新形状更小"，旧档的 135…149 号槽条目<b>必然</b>越界 ⇒
     * {@code PocketInventory#loadGroup} 的既有口径（忽略 {@code Size} + 越界条目<b>丢弃并一次性 WARN</b>）
     * 是本轮唯一的兼容落点：既不静默丢件（WARN 报出条数与现有格数），也不炸容器
     * （handler 格数由构造期定为 135，Container 那 135 个 {@code ModularSlot} 永远点在合法区间内）。
     */
    public static final int GHOST_ITEM_SLOT_LIMIT = STORAGE_ROWS * STORAGE_COLUMNS;
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
    /**
     * ★<b>R80③ 收单源</b>：游戏刻 → 秒的换算基数（Minecraft 固定 20 tick / 秒，不可配）。
     * <p>
     * 存在的理由：本轮实测到 tick→秒的换算在<b>三个文件里各写了一遍字面量 20</b>
     * （物品 tooltip 的蒸馏间隔、蒸馏驱动的"还剩几秒"、面板状态行的剩余秒数）。
     * 这类"三处看起来一样的算式"只要有一处忘了进位规则（向下取整 vs 向上取整）就会给玩家
     * 三个不同的读数 ⇒ 换算本体收进 {@link #ticksToSecondsCeil(int)}，字面量只活在下面这一行与它里面。
     */
    public static final int TICKS_PER_SECOND = 20;
    /** 一个游戏刻的毫秒数 = {@code 1000 / }{@link #TICKS_PER_SECOND}（★同样不得在别处写 50）。 */
    public static final int MILLISECONDS_PER_TICK = 1000 / TICKS_PER_SECOND;

    /**
     * tick → 秒，<b>向上取整</b>（{@code 0 tick → 0 秒}、{@code 1 tick → 1 秒}）。
     * <p>
     * 为什么统一取"上"而不是"下取整/四舍五入"：这三处读数都是"<b>还要多久才发生</b>"
     * （蒸馏还剩几轮、短效通道还剩几秒），向下取整会在只剩 19 tick 时显示 0 秒 ⇒ 玩家
     * 读到"已经结束"但实际还在跑（R10 的"失败与状态不得说谎"同一族）。
     * 取整的进位表达式（{@link #TICKS_PER_SECOND} − 1）也只存在于此一处。
     *
     * @param ticks 剩余 tick 数；{@code <= 0} 一律给 0 秒（不把"没有剩余"显示成"还剩 1 秒"）
     */
    public static int ticksToSecondsCeil(int ticks) {
        if (ticks <= 0) {
            return 0;
        }
        return (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    // ------------------------------------------------------ ★R100：持续化通道的频率档位表（单源）
    //
    // ★整段只允许活在这里（"频率值单源"裁定）：秒表、默认档索引、NBT 键名、秒→tick 换算
    // 四件一体；主源任何别处再写一个秒数/tick 数/换算式就是第二处速率源（R80③ ticksToSecondsCeil
    // 同一条纪律）。★只作用于<b>持续化</b>通道：手动付费短效道维持 1s 节拍 ×30 批（R100 用户裁决，
    // 见 {@link #CHANNEL_TICK_PERIOD} 的注释），档位 UI 也只挂在 CHANNEL_PERSIST 那一面。

    /**
     * 持续化通道的频率档位表（<b>秒/批</b>，升序；下标即档位索引）。
     * <p>
     * ★表本体即判据：档位 UI 的升/降钮、NBT 档位值、服务端校验全部按下标消费本表，
     * 不许在任何调用方再抄一份秒数清单。11 档 = 用户点名的 {@code {1,2,3,5,10,15,30,60,120,300,600}}。
     */
    public static final int[] CHANNEL_FREQ_TIERS_SECONDS = { 1, 2, 3, 5, 10, 15, 30, 60, 120, 300, 600 };
    /** 默认档 = <b>5 秒</b>那一档（下标 3）。★无键即默认 ⇒ 旧存档口袋零迁移读出 5s 档。 */
    public static final int CHANNEL_FREQ_TIER_DEFAULT = 3;
    /**
     * 频率档位在口袋 NBT 根层的键名（int = 档位下标）。
     * <p>
     * ★★<b>键名 {@code channelFreqTier} 一经落档即冻结</b>（「可加不可改」的键名版，同
     * {@link #UPGRADES_OFF_KEY} 那条）：改字面量 = 玩家已调好的频率读回来全变缺省，且零日志。
     * 缺键与「默认档下标」同义（读写两侧都不为默认档建档 ⇒ 老档天然干净，R53c 的"读路径不建档"）。
     */
    public static final String CHANNEL_FREQ_TIER_KEY = "channelFreqTier";

    /** 档位下标钳到合法区间（越界/脏档一律回落 {@link #CHANNEL_FREQ_TIER_DEFAULT}，读侧不抛、不写）。 */
    public static int channelFreqTierClamp(int tier) {
        return tier >= 0 && tier < CHANNEL_FREQ_TIERS_SECONDS.length ? tier : CHANNEL_FREQ_TIER_DEFAULT;
    }

    /** 档位 → 秒（唯一换算；调用方不得再写 {@code CHANNEL_FREQ_TIERS_SECONDS[x]} 式直取）。 */
    public static int channelFreqTierSeconds(int tier) {
        return CHANNEL_FREQ_TIERS_SECONDS[channelFreqTierClamp(tier)];
    }

    /** 档位 → tick（秒→tick 的<b>唯一</b>换算点：{@code 秒 × }{@link #TICKS_PER_SECOND}）。 */
    public static int channelFreqTierTicks(int tier) {
        return channelFreqTierSeconds(tier) * TICKS_PER_SECOND;
    }

    /**
     * 读口袋档上的频率档位（无栈/无根/缺键/脏档 ⇒ {@link #CHANNEL_FREQ_TIER_DEFAULT}；只读不建档）。
     * ★R101 起它是<b>旧档位键的兼容读口</b>：新秒值键（{@link #CHANNEL_FREQ_SECONDS_KEY}）在场的档上
     * 不再被读（新键优先），读取侧只经由 {@link #readChannelFreqSeconds} 的回落支到达这里。
     */
    public static int readChannelFreqTier(net.minecraft.nbt.NBTTagCompound root) {
        if (root == null || !root.hasKey(CHANNEL_FREQ_TIER_KEY)) {
            return CHANNEL_FREQ_TIER_DEFAULT;
        }
        return channelFreqTierClamp(root.getInteger(CHANNEL_FREQ_TIER_KEY));
    }

    // ------------------------------------------------------ ★R101：频率改「秒值输入框」的数据面
    //
    // ★任务拍板：频率档位 UI 从「更快/更慢步进的 11 档封闭表」改为「直接填秒数的输入框」，域钳到
    // [1,60] 秒（旧档位表中 >60s 的档位在读取侧按域钳制回落）。档位表本体（
    // {@link #CHANNEL_FREQ_TIERS_SECONDS}）与旧键 {@link #CHANNEL_FREQ_TIER_KEY} 都<b>不删不改</b>：
    // 前者还是手动付费短效道 1s×30 批的节拍源（{@link #CHANNEL_TICK_PERIOD}，R100 用户裁决不动），
    // 后者是旧存档的兼容读面（读侧映射，写侧不碰）。★新键一经落档即冻结（同上面那条键名纪律）。

    /**
     * 频率秒值在口袋 NBT 根层的键名（int = <b>秒/批</b>）。
     * <p>
     * ★★<b>键名 {@code channelFreqSeconds} 一经落档即冻结</b>（同 {@link #CHANNEL_FREQ_TIER_KEY} 那条：
     * 改字面量 = 玩家已调好的频率读回来全变缺省，且零日志）。★写腿只写本键、不碰旧档位键：
     * 读取优先级 = 本键在场 ⇒ 以本键为准；否则旧档位键映射成秒；两者都缺 ⇒ 默认值。
     */
    public static final String CHANNEL_FREQ_SECONDS_KEY = "channelFreqSeconds";
    /** 频率秒值域下界（含）：1 秒 = 手动道同款最快节拍。 */
    public static final int CHANNEL_FREQ_SECONDS_MIN = 1;
    /** 频率秒值域上界（含）：任务拍板 60 秒；旧档位档超过它的部分在读取侧钳到本值。 */
    public static final int CHANNEL_FREQ_SECONDS_MAX = 60;
    /** 默认频率秒值 = 旧默认档（下标 {@link #CHANNEL_FREQ_TIER_DEFAULT} = 5s）的秒值（★不手抄 5）。 */
    public static final int CHANNEL_FREQ_SECONDS_DEFAULT = CHANNEL_FREQ_TIERS_SECONDS[CHANNEL_FREQ_TIER_DEFAULT];

    /**
     * 频率秒值钳到 {@code [}{@link #CHANNEL_FREQ_SECONDS_MIN}{@code , }{@link #CHANNEL_FREQ_SECONDS_MAX}{@code ]}（读侧不抛、不写）。
     */
    public static int channelFreqSecondsClamp(int seconds) {
        return Math.max(CHANNEL_FREQ_SECONDS_MIN, Math.min(CHANNEL_FREQ_SECONDS_MAX, seconds));
    }

    /**
     * 读口袋档上的频率<b>秒值</b>（★三段优先级：新键 → 旧档位键映射 → 默认；只读不建档）。
     * <p>
     * ★旧档位键支 = 「按现有档位表映射成秒」：{@code channelFreqTierSeconds(readChannelFreqTier(root))}
     * ——两步各自带自己的钳制（脏档先钳档、映射出的 120/300/600s 再钳进 [1,60]），合起来就是
     * "旧档最慢读成 60s"的任务口径。★读路径一个字节都不写（R53c）。
     */
    public static int readChannelFreqSeconds(net.minecraft.nbt.NBTTagCompound root) {
        if (root == null) {
            return CHANNEL_FREQ_SECONDS_DEFAULT;
        }
        if (root.hasKey(CHANNEL_FREQ_SECONDS_KEY)) {
            return channelFreqSecondsClamp(root.getInteger(CHANNEL_FREQ_SECONDS_KEY));
        }
        if (root.hasKey(CHANNEL_FREQ_TIER_KEY)) {
            return channelFreqSecondsClamp(channelFreqTierSeconds(readChannelFreqTier(root)));
        }
        return CHANNEL_FREQ_SECONDS_DEFAULT;
    }

    /** 频率秒值 → tick（★秒→tick 的唯一换算点：{@code 秒 × }{@link #TICKS_PER_SECOND}；与档位那条并列只服务秒值域）。 */
    public static int channelFreqSecondsTicks(int seconds) {
        return channelFreqSecondsClamp(seconds) * TICKS_PER_SECOND;
    }

    /**
     * 写频率秒值（★仅服务端写腿调；★只写新键、旧档位键一字不动——键名冻结纪律）：
     * 越域（{@code clamp(s) != s}）拒写；同值（现读 == 目标）零写入（连点不刷整栈同步）；
     * 合法且真改 ⇒ 落 int。
     *
     * @return 本次是否真的改变
     */
    public static boolean writeChannelFreqSeconds(net.minecraft.nbt.NBTTagCompound root, int seconds) {
        if (root == null || channelFreqSecondsClamp(seconds) != seconds) {
            return false;
        }
        if (readChannelFreqSeconds(root) == seconds) {
            return false;
        }
        root.setInteger(CHANNEL_FREQ_SECONDS_KEY, seconds);
        return true;
    }

    /**
     * 短效通道节拍：<b>手动付费道恒 1s</b>（= 档位表的 1 秒档经 {@link #channelFreqTierTicks} 换算，
     * ★R100 频率片起它是档位表成员、不再独立成第二个速率源）。
     * <p>
     * ★只描述<b>手动</b>短效道（每秒一批、共 {@value #SHORT_CHANNEL_SECONDS} 批=30 秒语义不动，
     * R100 用户裁决「可调档位只作用于持续化通道」）；持续化通道的节拍按载体 NBT 档位走
     * {@link #readChannelFreqTier} + {@link #channelFreqTierTicks}，与手动道互不顶替。
     */
    public static final int CHANNEL_TICK_PERIOD = channelFreqTierTicks(0);
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
     * ★<b>R78 再点名一次</b>：本轮把源质 48→72、流体 6→18、真实槽 175→235，
     * 235 与 128 无关、72 与 128 也无关，本行<b>仍不得</b>被任何"顺手一起改"波及。
     * ★<b>R80 第三次点名</b>：本轮把中栏 150→<b>135</b>、真实槽 235→<b>220</b>，
     * 135 与 128 无关、220 与 128 也无关 ⇒ "150→135"或"235→220"的批量替换<b>一律不得</b>命中本行。
     */
    public static final int MAX_ROTATION_ENTRIES = 128;

    // ------------------------------------------------------ S3/S4 追加（口袋自身的档内区）
    //
    // 以下键由 S3（物品 tick / 图标位）与 S4（GUI 内存数组 ↔ NBT）追加，**只是新增，未改动任何既有键
    // 的形状与语义**（pocket-plan §2 的「可加不可改」）。放这里的理由与全类一致：NBT 键名一旦落档即
    // 不可改，且 GUI 侧禁止再写字面量（slice-s4-brief §1.1「PocketConstants：全部 NBT 键名单源」）。
    // S6（通道写栏位）与 S7（蒸馏产出落格）读同一批键，不再另立第二份名单。

    /** 中栏 {@link #GHOST_ITEM_SLOT_LIMIT} 格内容（R53a/R53b：有界随身容器存物品 NBT；读写口径见 {@link #UI_WORK_TICKS} 的 R53c）。 */
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

    // ------------------------------------------------------ R95 升级插件体系（S2a 契约面）
    //
    // 升级插件体系的三件套契约：效果位图根键、插件槽数、插件槽组持久化键。后续并行片只依赖这里。
    // ★键可加不可改（同上面 S3/S4 段的口径：NBT 键与位序一旦落档即冻结）。读写入口只有
    // {@code PocketUpgrades}，别处不得再摸这份位图。

    /**
     * 升级<b>效果位图</b>的根键（byte，位序 = {@code PocketUpgradeType#ordinal()} 第 0-4 位）。
     * <p>
     * ★效果查询的唯一真相：所有"有没有这个升级"的判定一律读它，不数槽里的插件；
     * 读侧只读不建档（R53c）。位图由 {@code PocketUpgrades#install} 在插件放入对应格的
     * 那一次手势置位，固化后不可逆。
     */
    public static final String UPGRADES_KEY = "upgrades";
    /** 升级插件槽数 = {@code PocketUpgradeType} 的枚举数（位图恰用 5 位）。 */
    public static final int UPGRADE_SLOTS = 5;
    /**
     * 升级<b>插件槽组</b>的 NBT 持久化键（{@code ItemStackHandler} 形状，同
     * {@link #ITEM_CONTENTS} 一族的槽组键）。
     * <p>
     * ★槽组<b>只是 GUI 呈现</b>（玩家看见插件插在哪格），效果判定不走它——双表示里
     * {@link #UPGRADES_KEY} 位图才是唯一真相，两者由落格的那一次手势同时写。
     * <p>
     * ★与 {@link #UPGRADES_KEY} 必须异值：槽组与位图同写在物品 NBT 根层，同值会同键异型互踩
     * （槽组是 compound、位图是 byte）。
     */
    public static final String UPGRADE_SLOT_GROUP = "upgradeCells";
    /**
     * ★R96 S1：升级<b>启用位图</b>的根键（byte，位序同 {@code PocketUpgradeType#ordinal()}）。
     * <p>
     * <b>语义 = off-mask（关闭位掩码）</b>：bit=1 表示"这一型当前被玩家关掉"，<b>缺键 = 五型全开</b>
     * ⇒ 默认态零写入，天然满足 R53c 的读路径不建档。它与 {@link #UPGRADES_KEY} <b>正交</b>：那位回答
     * "买没买到"（只置不清、放入即固化不可取出，R95 裁定一字未动），本位回答"买到的当前开不开"（可置可清）。
     * 效果的判据是两者的合成 {@code PocketUpgradeSwitches#isActive} = 位图 ∧ ¬本位。
     * <p>
     * ★<b>键名 {@code upgradesOff} 一经落档即冻结</b>（「可加不可改」的键名版）：改字面量会让玩家存档里
     * 已写出的关闭位读不回来，表现是"关掉的插件自己开回来了"，且零日志。
     * <p>
     * ★<b>为什么不塞进 {@link #UPGRADES_KEY} 的高位</b>：{@code PocketUpgrades#install} 的写进口是
     * {@code setByte} —— 若为容纳启用位把那个 byte 加宽成 TAG_Short，任何一次装插件都会把它<b>降级</b>回
     * TAG_Byte 并静默吃掉高字节（四个开关悄悄回到"默认开"、零日志），同时破掉既有锚「三轮 install 后字节
     * == 0b11111」（那条锚正是"位图唯一真相"的门禁化身）。
     * <p>
     * ★与 {@link #UPGRADES_KEY}/{@link #UPGRADE_SLOT_GROUP} 必须<b>异值</b>（同键异型互踩）。
     */
    public static final String UPGRADES_OFF_KEY = "upgradesOff";
    /**
     * ★R96 S7a：磁力<b>三态名单</b>的根键（compound：{@link #MAGNET_FILTER_MODE} + {@link #MAGNET_FILTER_LIST}）。
     * <p>
     * <b>形状</b>：{@code {mode(String, ★仅非 NONE 时写), list(NBTTagList of String, ★仅非空时写)}} ——
     * 条目是 {@link PocketMagnetFilter#itemKey(int, int)} 那枚 <b>Kind-free</b> 的物品键
     * （{@code i:itemId:meta:}，与 {@code PocketFilterConfig.itemKey(id, meta, "")} <b>逐字符同形</b>，
     * 于是 ghost 拖入那份 {@code contentKey} 可以直接喂进来）。★<b>条目里不嵌 NBT</b>（前提 P-11：磁力名单
     * 不做 NBT 敏感匹配），也★<b>不嵌 base64 NBT</b>（既无必要，又要撞同步墙）。
     * <p>
     * <b>三态语义</b>：{@code NONE}（无限制）= <b>名单保留在场但不生效</b>（全部放行）；{@code WHITELIST} =
     * 只吸名单内；{@code BLACKLIST} = 名单内一律不吸。⇒「无限制」不清名单，切回来条目还在。
     * <p>
     * ★<b>键名 {@code magnetFilter} 一经落档即冻结</b>（「可加不可改」的键名版，同 {@link #UPGRADES_OFF_KEY}
     * 那一条）：改字面量 = 玩家配好的名单读回来变空，且零日志。子键 {@code mode}/{@code list} 只活在
     * 本 compound 内，不与根层任何键比异值。
     * <p>
     * ★与 {@link #UPGRADES_KEY}/{@link #UPGRADES_OFF_KEY}/{@link #FILTERS}/{@link #UPGRADE_SLOT_GROUP}
     * 必须<b>异值</b>；它是 {@code PocketInventory} <b>不拥有</b>的根键（写口在
     * {@code NekoPocketServerHandler}，读口在 {@code PocketMagnetDriver}）⇒ 会话落盘不会覆掉它。
     * <p>
     * <b>同步预算（供 S7b 决定载体）</b>：满档 72 条 × 键长 12–25 字符 ⇒ 纯名单串长上界
     * <b>864–1800 B</b>，远低于 {@code StringSyncValue} 的 32693 字节墙（{@code Short.MAX_VALUE-74}）⇒
     * <b>不撞墙</b>；算术与结论见 {@link PocketMagnetFilter} 类注释与交付记录。
     */
    public static final String MAGNET_FILTER = "magnetFilter";
    /** {@link #MAGNET_FILTER} 内的三态位（String = {@code PocketMagnetFilter.Mode} 的枚举名；缺键/空 = {@code NONE}）。 */
    public static final String MAGNET_FILTER_MODE = "mode";
    /** {@link #MAGNET_FILTER} 内的条目表（NBTTagList of String，元素 = Kind-free 物品键；★插入序就是 S7b 的格序）。 */
    public static final String MAGNET_FILTER_LIST = "list";
    /**
     * ★R96 S7b：{@link #MAGNET_FILTER} 内的<b>吸取目标</b>两档位（String = {@code PocketMagnetFilter.Target}
     * 的枚举名；★缺键/空 = {@code POCKET} = 口袋内 135 格栏，即 R96 P-11 定案后的<b>现状行为</b>）。
     * <p>
     * 与 {@link #MAGNET_FILTER_MODE} 同一条"非默认才占键"的纪律：{@code POCKET} 不写键 ⇒ 本片之前落的所有
     * 档读回来都是 {@code POCKET}，字节形状与"本档不存在"时<b>逐字节相同</b>。
     * <p>
     * ★<b>这是配置位，不是槽位</b>：它不进口袋栏位算法、不进 {@code PocketSlots}（守恒 225 与本键无关）。
     * ★执法腿（把落点从中栏优先换成玩家主背包优先）需要 {@code PocketSession} 新增一条 player-first
     * 落点口并过 S4b 那条天然满量拆堆漏斗 ⇒ <b>本片只落配置面 + 写口 + 读数</b>，缺口逐字登记在
     * {@code PocketMagnetFilter.Target} 的注释与交付报告，不在这里含糊成"已经生效"。
     */
    public static final String MAGNET_FILTER_TARGET = "target";
    /**
     * ★R96 S7a：磁力名单的<b>格数口径</b> —— 与源质显示格<b>同形</b>（12 行 × 6 列 = 72，
     * RET-3 C7 / EVA-2 AUQ-5 的朝向歧义在此定案）。
     * <p>
     * ★<b>刻意不写成 {@link #ESSENCE_DISPLAY_GRID} 的别名</b>：那是"源质格"的语义，S7b 若把磁力名单的
     * 排布翻成 6 行 × 12 列（计划写的"反转成本 = 换两个常量 + 一处循环序"）不该顺手动到源质格。
     * 两者<b>数值同形</b>由用例 {@code magnet_filter_grid_shape_and_no_real_slot} 钉住，而不是由引用钉住。
     * <p>
     * ★这是<b>数据层的条目预算</b>，不是槽位：名单格件是 phantom（S7b 落地），守恒 225 与本常量无关。
     */
    public static final int MAGNET_FILTER_ROWS = 12;
    public static final int MAGNET_FILTER_COLUMNS = 6;
    public static final int MAGNET_FILTER_SLOTS = MAGNET_FILTER_ROWS * MAGNET_FILTER_COLUMNS;
    // ★R96 根层新键的异值预留位：R96 计划新增的三个根键必须彼此异值、也与上面三条 R95 键异值 ——
    // upgradesOff（S1 已落）、magnetFilter（★S7a 本片已落，见上）、elem（S9 元素容量，内含 6 条 tag→int，
    // ★不建 FLUID_BAR_AMOUNT_L 式双轨）。写在这里的理由：根键一经落档即冻结，事后发现撞键只能靠迁移救，
    // 而迁移没有回头路。
    // ★同一段给 S9 预留 PRIMAL_TAGS（6 项 primal 白名单，判据 Aspect#isPrimal() ★叠加该白名单）的常量落点
    // —— 它不是 NBT 键，但同样住在本类尾部追加区，先占位免得两片并行时在同一段互相挤位置。
    //
    // ================================================================================ ★R96 S9a：元素容量载体（已落地，占用上面那块预留位）
    //
    // 形状与三条纪律，一次写清楚，免得后来人在别处抄第二份：
    // ① 容量的唯一落点 = 根层独立 compound {@link #ELEMENTS}，<b>内含</b>「primal tag → int」，
    // ★<b>绝不把这 6 条 tag 写在栈根</b>（栈根已有 {@link #ESSENCE} 一族同批 tag 名的条目，
    // 平铺会互混淆 —— R96 计划 §5 S9 禁止项原文）；
    // ② ★<b>不建 long/int 双轨</b>：单 tag 上限 {@link #ELEMENT_CAP_PER_TAG}=500，int 头恒不越界，
    // 流体那条 AmountL 双轨的根因（{@code FluidStack.amount} 是 int、真值要冲到 16G）在这里结构性不存在；
    // ③ 键名一经落档即冻结（「可加不可改」）：{@link #ELEMENTS} 与 compound 内的节拍键同此纪律（★模式位图由 S9b 追加，见下面那段；
    // S9b 曾追加的第四条节拍键 tickCrystal 已随结晶并入蒸馏腿而删除，不写读档迁移——旧档残留值无人读，携带侧也不再跨根搬运）。

    /**
     * ★R96 S9a：元素容量的根键（独立 compound，读写本体在 {@code PocketElementStore}）。
     * <p>
     * 与 {@link #ESSENCE}（源质表）是<b>两张独立的表</b>：同一批 tag 名在两处各存一份不是冗余，
     * 而是两个量纲 —— 源质是「可蒸/可搬的原料点数」，元素是「已经折成元始、可直接给法杖与护盾用的容量」。
     * 折价发生在 {@code mage/PocketEssenceTransmuteDriver}（1 源质点 → 1 元素/秒），不在存储层。
     */
    public static final String ELEMENTS = "elem";
    /**
     * ★R96 S9a：{@link #ELEMENTS} compound 内的<b>节拍键</b>（法杖缓慢充能剩余 tick，int）。
     * <p>
     * 为什么把计时状态放进 {@link #ELEMENTS} 而不是栈根：根键一经落档即冻结，R96 计划 §3 只给本轮
     * 批了三个新根键（{@code upgradesOff}/{@code magnetFilter}/{@code elem}），多开一个根键就是第四次
     * 表态；而这三条节拍<b>本来就是元素容量这一族的私有状态</b>，同住一个 compound 是最贴近所有权的放法。
     * <p>
     * ★读侧只按 {@link #PRIMAL_TAGS} 那 6 个键名取值，节拍键与 tag 键因此<b>不可能混淆</b>
     * （{@code PocketElementStore} 的取值循环以白名单为输入，不做「扫全 compound 条目」）。
     */
    public static final String ELEMENT_TICK_WAND = "tickWand";
    /** ★R96 S9a：猫猫币充能剩余 tick（int；计时走 NBT 剩余 tick = 不变量 G8，★禁止 {@code ticksExisted % n}）。 */
    public static final String ELEMENT_TICK_COIN = "tickCoin";
    /** ★R96 S9a：源质转换剩余 tick（int；同上）。 */
    public static final String ELEMENT_TICK_TRANSMUTE = "tickTransmute";
    /**
     * ★R96 P-7 定案：<b>6 个</b> primal tag（TC4 {@code Aspect.getPrimalAspects()} 的同一批，
     * 逐字对齐 {@code thaumcraft/api/aspects/Aspect.java:20-25} 的 AIR/EARTH/FIRE/WATER/ORDER/ENTROPY）。
     * <p>
     * ★这条白名单是<b>独立于 TC 的第二道判据</b>，不是 {@code Aspect#isPrimal()} 的装饰：addon 可以注册
     * <b>没有 components</b> 的新 aspect，而 TC 的 {@code isPrimal()} 实现正是「{@code components} 空 ⇒ true」
     * ⇒ 单用它会把手写的无成分 aspect 判成元始（R96 计划 §5 S9 禁止项原文）。取值序 = UI 网格序，
     * <b>只增不改序</b>（改序会让面板与已存档的读数对不上号）。
     */
    public static final String[] PRIMAL_TAGS = { "aer", "terra", "ignis", "aqua", "ordo", "perditio" };

    /**
     * ★R98 S2（DP-1 定案）：与 {@link #PRIMAL_TAGS} <b>同序同长</b>的本地 tooltip 色表（六项），
     * 唯一消费者是物品 tooltip 的储量行（{@code ItemNekoDimensionPocket#appendElementReserveLines}）。
     * <p>
     * ★<b>本表是有意的第二份真相</b>，必须如实登记：原始色值住在 TC
     * {@code thaumcraft/api/aspects/Aspect#getChatcolor()}（{@code Aspect.java:174-176}，返回单个色码字符），
     * 这里抄了一份。立法理由与 {@link #isPrimalTag(String)} 那条白名单腿<b>完全同一条</b> ——
     * <b>TC 缺席时口袋必须照常工作</b>：那六行储量在没装神秘时代的整合包里照样要出（行序与存在性由
     * {@link #PRIMAL_TAGS} 定，★不由桥定），所以颜色这一维不能去问桥要。
     * <p>
     * ★为什么不选另两条看起来"更单源"的路（DP-1 的三条候选，本条拍板后前两条作废）：
     * ① 新增第 16 个桥方法 {@code chatColorOf(tag)} —— TC 缺席时<b>仍然</b>得有本地兜底才出得来颜色，
     * 于是实际形态是"桥 + 本地表"两份都要，还得同批改 {@code TaumBridgeApi}/{@code TaumBridge}/
     * {@code TaumCompat} 三处与门 P、F-1 复核 ⇒ 多出来的是一份<b>永久</b>的桥面，换不到"少一份真相"；
     * ② 拿现成的 {@code TaumCompat.colorOf(tag)}（RGB int）反查最近的 {@link EnumChatFormatting} ——
     * 省掉桥方法，但新增一个 16 格量化函数（★那本身就是新口径），且 TC 缺席时它返
     * {@code TaumCompat.COLOR_UNKNOWN}（-1）⇒ 仍需第三份兜底色。
     * <p>
     * ★代价照记（不许只写收益）：TC 若改了某个 aspect 的配色，本表<b>不会跟着变</b>，
     * 那六行的颜色会与 TC 自家物品差一色。可接受的依据 = 这一维<b>只影响观感</b>：
     * 不承载规格数字（R75 的数字单源纪律不受触）、不承载任何判定（入账/白名单/上限都不读它）、
     * 玩家侧也不会因色读错数值 —— 数值那一半永远由 {@link PocketElementStore#get(String)} 现读。
     * <p>
     * ★取值逐字对齐 {@code Aspect.java:20-25} 的 chatcolor 字符 {@code e / 2 / c / 3 / 7 / 8}
     * （aer/terra/ignis/aqua/ordo/perditio 依次），下标与 {@link #PRIMAL_TAGS} 一一对应 ⇒
     * {@code PRIMAL_TAGS[i]} 配 {@code PRIMAL_CHAT_CODES[i]}。<b>只增不改序</b>那条纪律对本表同样成立，
     * 两表长度不等或某项为 null 会被用例当场钉红。
     */
    public static final EnumChatFormatting[] PRIMAL_CHAT_CODES = { EnumChatFormatting.YELLOW,
        EnumChatFormatting.DARK_GREEN, EnumChatFormatting.RED, EnumChatFormatting.DARK_AQUA, EnumChatFormatting.GRAY,
        EnumChatFormatting.DARK_GRAY };

    /**
     * 该 tag 是否在 {@link #PRIMAL_TAGS} 白名单内（<b>纯查表、零 TC 依赖</b>的一条腿）。
     * <p>
     * ★两条腿必须分开用：本方法定"容量表与 UI 有几行、按什么序"（TC 缺席照样成立）；
     * {@code TaumCompat.isPrimalTag} 定"外部拿来的 tag 到底是不是 TC 认的元始"（TC 缺席恒 false）。
     * 合成点在 {@code TaumBridge#isPrimalTag} = 本方法 ∧ {@code Aspect#isPrimal()}。
     */
    public static boolean isPrimalTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }
        for (String primal : PRIMAL_TAGS) {
            if (primal.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /** ★R96 P-7：单 tag 元素容量上限 = <b>500</b>（用户裁定的"各 500"，★不是 {@link #ESSENCE_CAP_PER_TAG}）。 */
    public static final int ELEMENT_CAP_PER_TAG = 500;
    /**
     * 元素总容量 = {@link #ELEMENT_CAP_PER_TAG} × {@code PRIMAL_TAGS.length} = <b>3000</b>（派生，不留字面量）。
     * <p>
     * ★留派生式的理由与 {@link #ESSENCE_MAX_PHIALS_PER_TAG} 同一条：这个数同时是"UI 的格数账"和
     * "测试的总量锚"，写死一份就会出现"改了白名单长度而总量锚不动"的假绿。
     */
    public static final int ELEMENT_TOTAL_CAP = ELEMENT_CAP_PER_TAG * PRIMAL_TAGS.length;
    /**
     * ★R96 S9a：法杖缓慢充能的<b>节拍</b>（tick）与<b>单次每 tag 上界</b>（点）。
     * <p>
     * 两个数照 TC4 魔力石 {@code thaumcraft/common/items/baubles/ItemAmuletVis.java:74} 的同一形状
     * （{@code entity.ticksExisted % 5 == 0} + {@code Math.min(5, 法杖余量, 自身存量)}），
     * 取证原文见 {@code r96-eva3.md} §2.3。★<b>取模那一半不照抄</b>：本仓的计时一律走 NBT 剩余 tick
     * （不变量 G8 与 R59e；磁力是既存例外，单独记账，不得扩散），这里只继承"5 tick 一批、每批 ≤5 点"
     * 这个速率事实。两处字面量<b>只</b>活在下面两行，搬运侧与测试都读符号（钉"节拍常量单源"的那半边判据）。
     */
    public static final int MAGE_WAND_INTERVAL_TICKS = 5;
    /** 法杖缓慢充能：一批里<b>每个 tag</b> 至多搬这么多点（照 {@link #MAGE_WAND_INTERVAL_TICKS} 的同一出处）。 */
    public static final int MAGE_WAND_MAX_POINTS_PER_BATCH = 5;
    /**
     * 秒级节拍的<b>单源</b> = {@link #TICKS_PER_SECOND}（猫猫币"1 秒恰 1 枚"与源质转换"1 元素/秒"共用）。
     * <p>
     * ★两条被动各自持有<b>自己的剩余 tick 键</b>（{@link #ELEMENT_TICK_COIN} / {@link #ELEMENT_TICK_TRANSMUTE}），
     * 共用只是"同一个速率事实"，不是同一份状态 —— 合并成一键会让"关掉其中一条"必须连带停另一条的拍。
     */
    public static final int MAGE_SECOND_INTERVAL_TICKS = TICKS_PER_SECOND;
    /** 源质转换：一批里每个 tag 折这么多点（1 元素/秒），★6 种 primal <b>同批并行</b>各折这么多。 */
    public static final int MAGE_TRANSMUTE_POINTS_PER_BATCH = 1;
    /**
     * 猫猫币充能的一枚价值（点）—— 普通猫猫币 {@code +1}、闪烁猫猫币 {@code +10}（需求原文）。
     * <p>
     * ★这里的"点"是<b>一次入账同时给 6 条 tag 各加的量</b>（"普通各元素 +1"里的"各"），
     * 不是"六个元素合计 1 点"；入账因此是一份 {@code tag → 价值} 的全有全无候选，
     * 预检走 {@code PocketElementStore#canAcceptAll}。
     */
    public static final int MAGE_COIN_VALUE_NORMAL = 1;
    public static final int MAGE_COIN_VALUE_SHIMMERING = 10;
    // ================================================================================ ★R96
    // S9b：魔法使<b>四模式</b>与<b>结晶模式</b>
    //
    // ★R96 S9b 只在 S9a 那块预留段之后<b>追加</b>，S9a 的六条 tag / 三个节拍键 / 两档上限一字未动。
    // 两件新事各有一条落档纪律，写在这里免得后来人在别处再立一份：
    // ① 模式状态住在 {@link #ELEMENTS} compound <b>内</b>的 {@link #ELEMENT_MODES} 一枚 byte，
    // ★<b>不开第四个根键</b>（R96 计划 §3 只批了 upgradesOff / magnetFilter / elem 三枚根键，
    // 多开一根就是第四次表态），也★<b>不塞进 {@link #UPGRADES_OFF_KEY}</b>（那位回答"这一<b>型</b>开不开"，
    // 一共五位；模式回答"型内哪一条<b>被动</b>开不开"，两码事，并成一位会让"关掉魔法使"与
    // "只关结晶模式"读起来是同一个键）；
    // ② 结晶模式的出件量以<b>整枚晶</b>为单位（{@code TaumDistillRules#CRYSTAL_CAPACITY} = 1 点/枚），
    // ★不复用 {@code TaumDistillRules#credit} 那套"整轮批量"语义做逐点入账（R96 计划 §5 S9 禁止项）。
    /**
     * ★R96 S9b：{@link #ELEMENTS} compound 内的<b>模式位图</b>（byte，bit = {@link #MAGE_MODE_CRYSTAL} 一族）。
     * <p>
     * <b>语义 = on-mask（启用位）而不是 off-mask</b>：bit=1 ⇒ 该模式开。三种模式各有<b>自己的缺省</b>，
     * 缺省合起来就是 {@link #MAGE_MODES_DEFAULT}（猫猫币 + 源质转换开、结晶关）⇒
     * <b>缺键按 {@link #MAGE_MODES_DEFAULT} 读</b>（R53c 读路径不建档 ⇒ 默认态零写入，
     * S9a 那三条被动在旧档上的行为逐字不变），写回缺省值时<b>{@code removeTag}</b> 而不是留 0 壳。
     * <p>
     * ★<b>键名 {@code modes} 一经落档即冻结</b>（「可加不可改」的键名版，同 {@link #ELEMENTS} 与
     * {@link #UPGRADES_OFF_KEY} 那两条）：改字面量 = 玩家配好的三枚模式读回来全变缺省，且零日志。
     * <b>位序同样冻结</b>：追加第四种模式只许用 bit3，★不得复用已作废的位（老档里那位可能正被人手改过）。
     */
    public static final String ELEMENT_MODES = "modes";
    /** ★R96 S9b 模式位 0：结晶模式（蒸馏出的源质直接以<b>晶</b>形态进背包）。★缺省 = <b>关</b>（用户裁定"默认关"）。 */
    public static final int MAGE_MODE_CRYSTAL = 1;
    /** ★R96 S9b 模式位 1：猫猫币充能（S9a 那条被动被本位包一层可关的外壳）。缺省 = 开（S9a 的既有行为）。 */
    public static final int MAGE_MODE_COIN = 2;
    /** ★R96 S9b 模式位 2：源质转换（同上）。缺省 = 开（S9a 的既有行为）。 */
    public static final int MAGE_MODE_TRANSMUTE = 4;
    /**
     * 缺省模式位图 = {@link #MAGE_MODE_COIN} | {@link #MAGE_MODE_TRANSMUTE} = <b>6</b>。
     * <p>
     * ★这一枚是"缺键怎么读"的<b>唯一</b>真值：三条腿的判据与写腿的"回到缺省就摘键"都读它，
     * 抄第二份字面量就会出现"读侧默认与写侧摘键条件不同值"的静默漂移。
     */
    public static final int MAGE_MODES_DEFAULT = MAGE_MODE_COIN | MAGE_MODE_TRANSMUTE;
    /** 全部已定义的模式位（★新增模式位必须同时进这张表与 {@link #MAGE_MODES_DEFAULT} 的推理，缺一即红）。 */
    public static final int[] MAGE_MODE_BITS = { MAGE_MODE_CRYSTAL, MAGE_MODE_COIN, MAGE_MODE_TRANSMUTE };

    /**
     * 这一枚位是不是已定义的模式位（★纯查表，与 {@link #isPrimalTag(String)} 同一条纪律：
     * 白名单外的位一律不认 —— 认了就会出现"往档里写进一个没人读的第 7 位"，
     * 而那一位在下次掩码比较里既不算缺省也读不回来，是静默的档污染）。
     */
    public static boolean isMageModeBit(int bit) {
        for (int defined : MAGE_MODE_BITS) {
            if (defined == bit) {
                return true;
            }
        }
        return false;
    }

    /**
     * ★R96 S9b：结晶交付里<b>每堆</b>至多这么多个晶（同一 tag 超出按堆循环 mint）。
     * <p>
     * 64 的依据是 {@code TaumBridge#CRYSTAL_STACK_LIMIT}（TC 晶的可堆数）——桥里那枚 {@code min} 只是
     * 兜底、★不是"半枚也出"的许可，所以本仓按<b>整枚</b>取上界（一晶 = {@code CRYSTAL_CAPACITY} = 1 点）。
     * ★结晶并入蒸馏腿分叉后，本上界管的是"一次 mint 一堆"的堆容量——时序随蒸馏轮次，
     * 不再有独立的秒级节拍与剩余 tick 键（原 {@code tickCrystal} 已删）。
     */
    public static final int MAGE_CRYSTAL_MAX_PER_BATCH = 64;
    /**
     * ★R95 S5：STACK 升级位对"单格堆叠"的<b>倍率</b>（用户原话"所有物品和源质最大单格堆叠数*16"，
     * 一位同时管物品格与源质格）。执法点：{@code PocketInventory#effectiveStorageLimit}（物品侧单源）
     * 与 {@code PocketEssenceStore#capPerTag()}（源质侧单源），别处不得再抄 16。
     */
    public static final int UPGRADE_STACK_MULTIPLIER = 16;
    public static final int STACK_UPGRADE_MAX_COUNT = 64;
    public static final int STORAGE_LIMIT_PER_STACK_UPGRADE = 1024;
    public static final String STACK_UPGRADE_COUNT_KEY = "stackUpgradeCount";
    /**
     * ★R95 S5：中栏存储格的<b>槽位上限</b>两档 —— 未升级 = {@code 64}（= 上游
     * {@code ItemStackHandler#getSlotLimit} 的既有默认，现状逐字不变）；STACK 位固化 = <b>1024</b>
     * （= 64 × 16，用户原话的单格堆叠上限）。单源消费点：{@code PocketInventory#newStorageGroup}
     * 的 {@code getSlotLimit}/{@code getStackLimit} 覆写。
     */
    public static final int STORAGE_SLOT_LIMIT_BASE = 64;
    public static final int STORAGE_SLOT_LIMIT_UPGRADED = STORAGE_SLOT_LIMIT_BASE * UPGRADE_STACK_MULTIPLIER;
    /**
     * ★R95 S5：STACK 位固化后的每格源质上限 = 256 × 16 = <b>4096</b>（派生自倍率，不留裸字面量；
     * 放在本段而不是 {@link #ESSENCE_CAP_PER_TAG} 旁边是<b>初始化序</b>的硬要求：静态字段初始化
     * 不允许前向引用，倍率常量必须先于它出现）。
     */
    public static final int ESSENCE_CAP_PER_TAG_UPGRADED = ESSENCE_CAP_PER_TAG * UPGRADE_STACK_MULTIPLIER;
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
     * 流体条目<b>头值</b>的键名（TAG_Int）—— ★这不是本仓写的键，是 vanilla/Forge
     * {@code FluidStack.writeToNBT} 自己写出的 {@code Amount}；列在这里只为让<b>只读</b>侧
     * （{@code PocketUpgradeGuards} 的容量守卫回落腿）也遵守"NBT 键名住在 PocketConstants"这一条。
     * <p>
     * ★写侧一律仍走 {@code FluidStack.writeToNBT}（见 {@code PocketInventory#saveTanks} 的双写）：
     * 拿这个键去<b>写</b>档就是绕开流体栈自己的序列化形状，会造出读不回来的档。
     */
    public static final String FLUID_BAR_AMOUNT = "Amount";
    /** 流体条目的流体名键（同样由 {@code FluidStack.writeToNBT} 写出；守卫按"有没有名"判条目是不是真内容）。 */
    public static final String FLUID_BAR_FLUID_NAME = "FluidName";
    /**
     * ★<b>R95 S5：流体条目的 long 真值键</b>（TAG_Long）—— 双轨计数的落档面。
     * <p>
     * 写档<b>双写</b>：{@code Amount}（由 {@code FluidStack.writeToNBT} 写出的 int 头值
     * = min(真值, {@code Integer.MAX_VALUE})，旧版读侧的唯一来源）+ 本键（long 真值，超 int 顶时的唯一权威）。
     * 读档<b>优先 AmountL</b>、无则回退旧 {@code Amount}。★回退腿只对「双轨之前的老档」有意义：那批档真值
     * 恒 ≤ 当时的基值 ≤ 头值 ⇒ 零损失。★R96 S3 把两档改成 20M / 2G <b>不</b>新增回退面（2G 档真值恒 ≤ 头值
     * ⇒ 双写两数相等，走哪条腿读数一致）；本键仍是已写出的 16G 时代存档保住原值的唯一来源，故不删。
     * <p>
     * ★<b>降级损失如实声明</b>：S5 之前的 jar 读 S5 档只认 {@code Amount} ⇒ 真值超头部分
     * （&gt; 2,147,483,647 mB）在旧版里读不到 —— 写档侧在真值超头时打一次性 WARN（不阻止写）。
     */
    public static final String FLUID_BAR_AMOUNT_L = "AmountL";
    /**
     * <b>单个</b>流体 tank 的容量（mB）⇒ {@link #FLUID_TANK_TOTAL} 个 tank 的总量是它的 18 倍
     * （★R96 S3：{@code 18 × 20,000,000 = 360,000,000}；R78② 的 18 × 16M 口径与更早的"六槽合计"口径
     * 都已作废，数值史只留在 README / wiki，不在这个常量上再立一份）。
     * <p>
     * ★<b>规格外自立项</b>（R75 明文，须"游戏内文案 + 交付说明"双处声明；R78 的合计变更同样双处同步）：
     * 本值由 R75 那句「流体槽容量 16M」定为每槽 16,000,000（×1000 于更早的 16,000），★R96 S3 按用户那句
     * 「原始流体容量 16M → 20M」抬到<b>每槽 20,000,000</b>。两代都是<b>口头量级、不是需求原文数字</b>。
     * ★R95 S5 起本常量是<b>未升级口径</b>——玩家可见侧的容量一律由
     * {@link #fluidTankCapacityMl(boolean)} 按升级位选值填进 {@code gtit.pocket.fluid.capacity}
     * （lang 不得写死规格数字）。★<b>R98 S2 改口</b>：物品 tooltip 的 {@code tooltip.9} 已随激进裁剪
     * 退场（那六位腾给六行元素储量），容量在玩家侧的<b>唯一</b>读数面只剩面板那一条
     * （DP-5 定案），"两代都是口头量级"这句显式声明也随之改由 README 承担。
     */
    public static final int FLUID_BAR_CAPACITY_ML = 20_000_000;
    /**
     * ★<b>R96 S3 改值：CAPACITY 升级位固化后的单 tank 容量</b> = <b>2G = 2,000,000,000 mB</b>
     * （十进制 G，与旧 16G 同口径；用户已裁定）。★仍写成 <b>long 字面量</b>：这一档的<b>形状</b>是双轨
     * 计数的前提（内存 long 真值 + int 头，见 {@code PocketInventory} 的 tank 注释），且
     * {@code verify-pocket.sh} 的字面量判据按尾缀 {@code L} 认它 —— 降成 int 会同时破掉
     * "两档常量 + 一个选择点"的结构判据，而 {@link #fluidTankCapacityMl(boolean)} 的返回型本就是 long。
     * <p>
     * ★<b>2G 与旧 16G 的实质差别（理由文按 2G 重写）</b>：2G <b>不进</b> int 顶之外
     * （{@code 2,000,000,000 < 2,147,483,647}）⇒「真值超头」这一支在数值上<b>不可达</b>，双轨读数恒满足
     * 头 ≡ 真值（用例 {@code fluid_truth_never_exceeds_int_head} 把这条钉成用例，而不是只删文档）。
     * 双轨机制本身<b>全部保留</b>，三条理由：① 已写出的 16G 时代存档里 {@code Amount} 已被钳在 int 顶，
     * 只有 {@link #FLUID_BAR_AMOUNT_L} 保得住原值；② {@code PocketInventory} 的执法面（构造期覆写、
     * 头 amount 落点恰 3）按 头 = min(真值, {@code Integer.MAX_VALUE}) 的<b>一般式</b>写，为前向兼容零改动；
     * ③ 全仓 7 处 {@code min(…, Integer.MAX_VALUE)} 在 2G 下退化为恒等（零成本、零行为差），
     * 删任何一处都是把「当前够不着」读成「永远够不着」。
     * <p>
     * ★两套常量 + 运行时选择（而不是改写 {@link #FLUID_BAR_CAPACITY_ML} 一个数）的理由：基值是
     * R75 起已对玩家双处声明（当时的 tooltip.9 / README）的<b>落档口径</b>，且全仓十余处派生（步进、总容量、
     * verify 门禁）都按"未升级"读它 ⇒ 动态化收进 {@link #fluidTankCapacityMl(boolean)} 这<b>一个</b>
     * 选择点，基值常量与既有派生一个字不改，升级侧另立 long 常量。
     * （★R98 S2 后物品侧那一处已退场 ⇒ 双处变单处，但"两套常量 + 一个选择点"的形状判据不动。）
     */
    public static final long FLUID_BAR_CAPACITY_UPGRADED_ML = 2_000_000_000L;

    /**
     * ★R95 S5：单 tank 容量的<b>唯一选择点</b> —— 未升级 = {@link #FLUID_BAR_CAPACITY_ML}（★R96 S3：20M），
     * CAPACITY 位固化 = {@link #FLUID_BAR_CAPACITY_UPGRADED_ML}（★R96 S3：2G）。
     * <p>
     * 调用方（tank 头容量 supplier、{@link #fluidTotalCapacityMl(boolean)}、GUI 容量读数、tooltip 参数）
     * 一律经它取值，不得各自再写一遍三元。
     */
    public static long fluidTankCapacityMl(boolean capacityUpgraded) {
        return capacityUpgraded ? FLUID_BAR_CAPACITY_UPGRADED_ML : FLUID_BAR_CAPACITY_ML;
    }

    /**
     * 流体侧<b>总</b>容量（mB）= {@link #FLUID_TANK_TOTAL} × {@link #FLUID_BAR_CAPACITY_ML}
     * = {@code 18 × 20,000,000 =} <b>360,000,000</b>（★R96 S3；R78② 那代「18 × 16M」合计已作废。
     * ★R95 S5 起这是<b>未升级口径</b>，升级后的合计走 {@link #fluidTotalCapacityMl(boolean)}，
     * 两个读数不得互相顶替）。
     * <p>
     * ★存在的理由与 {@link #BURST_SHOW_TICKS} 同一条纪律：合计是"规格外自立项"的玩家可见读数，
     * 一旦在 lang 或 README 里另写一个数就是两处真相；玩家侧只允许由本常量填占位。
     */
    public static final int FLUID_TOTAL_CAPACITY_ML = FLUID_TANK_TOTAL * FLUID_BAR_CAPACITY_ML;

    /** ★R95 S5：升级后的流体总容量（long，★R96 S3 = 18 × 2G = 36G）——选择点语义同 {@link #fluidTankCapacityMl(boolean)}。 */
    public static long fluidTotalCapacityMl(boolean capacityUpgraded) {
        return FLUID_TANK_TOTAL * fluidTankCapacityMl(capacityUpgraded);
    }

    // ------------------------------------------------------ R83 C2（缺陷 6）：每条声明的组上限口径
    //
    // ★这一整段的存在理由：用户那句「alt 滚轮调整数量…物品每次 1 个；流体每次 1%；源质每次 1 个；
    // alt+ctrl+滚轮 步进 ×10」里每一个数字都只允许活在这里（D-6 裁定：×10 是<b>步进</b>的倍率，
    // ★★<b>R100 用户改判</b>：旧句「本片一个字节都不碰 Config.pocketChannelPairsPerSecond、
    // CHANNEL_TICK_PERIOD 与 ticksToSecondsCeil」中关于 CHANNEL_TICK_PERIOD 的那一半<b>自 R100 起作废</b>
    // —— 频率片把 CHANNEL_TICK_PERIOD 吸收为档位表成员（见上方 R100 段：值仍 20 tick、语义仍是
    // "手动道每秒一批"，动的只是它的<b>出处</b>：由独立字面量改经 channelFreqTierTicks(0) 换算，
    // 全仓速率源从两处并一处）。Config.pocketChannelPairsPerSecond 与 ticksToSecondsCeil 的不碰裁定
    // ★仍然成立（R100 只动了节拍出处，没动每批对数与取整口径））。步进量必须由<b>服务端</b>与
    // <b>客户端读数</b>共读同一份常量，否则"滚一下看到的"与"落档的"就是两个数。

    /**
     * 组上限的下界。★刻意取 1 而不是 0：{@code PocketAeChannelOps#extract} 对 {@code count <= 0}
     * 走的是 {@code NO_CHANNEL} 分支，会把"玩家自己调到 0"显示成"通道失联"（撒谎）；不想拉就该右键解绑。
     */
    public static final int FILTER_CAP_MIN = 1;
    /** 物品支一次滚轮的步进 = <b>1 件</b>（用户原话"物品是每次1个"）。 */
    public static final int FILTER_CAP_STEP_ITEM = 1;
    /**
     * 源质支一次滚轮的步进 = <b>1 点</b>（★R86 起取出侧不再有"每次几点"的常量，取出量见
     * {@code NekoPocketPanel#performEssenceOut}）。
     * <p>
     * ★<b>R88 起挂着、★R92-① 收口的那条张力</b>：本步进是<b>点数</b>，
     * 而 R88 之后两条搬运路都按<b>整瓶</b>取整（{@link #ESSENCE_OUT_UNIT_POINTS}，裁定 C1）⇒ 玩家把组上限
     * 停在非 8 倍数（例如 3）时，那一档<b>一瓶也搬不动</b>，通道照跑、零搬运（旧载体 1 点/枚 时"步进粒度与
     * 搬运粒度天然重合"的前提随载体一起没了）。★R92-① 把通道档粒度改成 {@link #ESSENCE_CHANNEL_UNIT_POINTS}
     * = 1 点之后，步进粒度与搬运粒度<b>重新重合</b> ⇒ 停在任意点数的档都搬得动，无需"抬步进成瓶数 / 在
     * tooltip 声明不足一瓶"那两种收口。⚠ 取瓶路（面向玩家）仍按整瓶，那条 C1 未被改判。
     */
    public static final int FILTER_CAP_STEP_ESSENCE = 1;
    /** 流体支"1%"的档数（★分母按 D-6 = 单 tank 容量 {@link #FLUID_BAR_CAPACITY_ML}，不是 360M 合计）。 */
    public static final int FILTER_CAP_PERCENT_STEPS = 100;
    /** 流体支一次滚轮的步进 = 单 tank 容量的 1% = {@code 20,000,000 / 100 =} <b>200,000 mB</b>（派生，不留字面量）。 */
    public static final int FILTER_CAP_STEP_FLUID = FLUID_BAR_CAPACITY_ML / FILTER_CAP_PERCENT_STEPS;
    /**
     * ★R95 S5：CAPACITY 位固化后的流体步进 = ★R96 S3 的 2G / 100 = <b>20,000,000 mB</b>（"每次 1%"的口径跟
     * 容量走；选择点 {@link #filterCapStepFluid(boolean)}，消费点 {@code PocketGhostRequest#nextCapEffective}）。
     * ★外面那层 {@code min(Integer.MAX_VALUE, …)} 在 2G 下退化为恒等，仍<b>保留</b>：它是「升级档一旦抬回
     * int 顶之外就不会溢出成负数」的唯一防线，删它等于把 (int) 强转裸露出来。
     */
    public static final int FILTER_CAP_STEP_FLUID_UPGRADED = (int) Math
        .min(Integer.MAX_VALUE, FLUID_BAR_CAPACITY_UPGRADED_ML / FILTER_CAP_PERCENT_STEPS);

    /** ★R95 S5：流体步进的唯一选择点（★R96 S3：未升级 200K / 升级 20M，语义同 {@link #fluidTankCapacityMl(boolean)}）。 */
    public static int filterCapStepFluid(boolean capacityUpgraded) {
        return capacityUpgraded ? FILTER_CAP_STEP_FLUID_UPGRADED : FILTER_CAP_STEP_FLUID;
    }

    /** alt+ctrl 的步进倍率（★作用在<b>步进</b>上：物品 1→10、流体 1%→10%、源质 1→10）。 */
    public static final int FILTER_CAP_FAST_MULTIPLIER = 10;
    /**
     * 流体支组上限的<b>上界</b>，同时是"未设置"时的<b>回落值</b>。
     * <p>
     * ★★<b>R95 S5 改判：钉 {@code Integer.MAX_VALUE}</b>（旧值 = {@link #FLUID_BAR_CAPACITY_ML}）。
     * ★<b>R96 S3 之后这一枚「声明档天花板」在数值上不再执法</b>：两档容量（20M / 2G）都<b>低于</b> int 顶
     * ⇒ 真正收口的是 {@code min(本常量, tank 容量) = tank 容量}。值仍<b>不动</b>：改它会破
     * {@code verify-pocket.sh} 的符号判据，并让「未设 ⇒ 从天花板起滚」的手感凭空跳档。双口径如实声明：
     * <ul>
     * <li><b>声明档是 int</b>（{@link #FILTER_CAP} 的 NBT 形状与滚轮文法都装不下 long 档）⇒ 一条声明
     * <b>单批</b>至多补 {@code 2,147,483,647} mB ≈ 2.147G；</li>
     * <li><b>★R96 S3：两档容量都在 int 顶之内</b> ⇒ 一批就够填满整个 tank；旧「升级档 16G &gt; 声明档上界、
     * 必须多批灌满」那条前提随容量降档一起消失。通道仍按 "落点余量 ∧ 声明上限" 逐批收口
     * （消费侧 {@code PocketFluidChannelOps#extractFluid} 的 {@code min(room, cap)} 已 long 化、<b>保留</b>），
     * 只是余量恰好一批就能收满。</li>
     * </ul>
     * ★回落语义因此回到"一拍填到本 tank 自然满量"——对<b>两档</b>口袋都逐字同效（room ≤ 20M / ≤ 2G 恒为
     * 收口侧）；<b>显示侧</b>的"自然满量"读数（未调过的默认显示）由各格件按 {@code min(本常量, tank 容量)}
     * 现算，不在本常量上再立第二份真相。
     */
    public static final int FILTER_CAP_CEILING_FLUID = Integer.MAX_VALUE;
    /**
     * 源质格一次<b>取瓶</b>的硬上界 = 64 点（★R84：与 {@link #ESSENCE_CAP_PER_TAG}=256 解耦，
     * 一格存 256 也要按次掏）。
     * <p>
     * ★R88 载体改判后这一枚 64 的<b>理由换了来源、值不动</b>：旧形状是"64 = 晶化源质的单堆上限"
     * （外部事实，见 {@code TaumBridge.CRYSTAL_STACK_LIMIT} 那条 ★★R88 段），新形状是
     * <b>纯设计量</b> 64 点 = {@link #ESSENCE_OUT_MAX_PHIALS_PER_ACTION} 只满瓶 ×
     * {@link #ESSENCE_OUT_UNIT_POINTS} 点。TC 的瓶堆叠上限是 64 <b>只</b>意味着"8 只瓶天然装得进一叠"，
     * 不再决定这个数。
     * <p>
     * ★★<b>R92-② 之后本常量只服务取瓶路</b>：旧形状里 {@link #FILTER_CAP_CEILING_ESSENCE} 按符号引用它
     * （⇒ 动它会连带改声明档天花板），现在那条改成引用 {@link #ESSENCE_CAP_PER_TAG} ⇒ 本行回到
     * <b>单一读者</b>。★声明位置仍建议保持在 {@link #ESSENCE_OUT_MAX_PHIALS_PER_ACTION} 之前（后者按符号
     * 引用本行，静态字段初始化不允许前向引用，放错位置就是编译错）。
     */
    public static final int ESSENCE_OUT_MAX_POINTS_PER_ACTION = 64;
    /**
     * ★★<b>R88 裁定：源质搬运载体 = TC 安瓿瓶（{@code ItemEssence}），本行就是"一次<b>取瓶</b>一格"的
     * 点数粒度</b>（旧载体是晶化源质，1 点/枚 ⇒ 粒度 1）。
     * <p>
     * ★★<b>R92-① 收窄本常量的执法面（不改判 R88，只划清两档）</b>：R88 那句"改判后<b>所有</b>取整都按它做"
     * 自 R92-① 起只对<b>取瓶路</b>成立；通道两条路（上传 / 下传）改按
     * {@link #ESSENCE_CHANNEL_UNIT_POINTS} 的 1 点量化，算式单源在
     * {@code PocketEssenceChannelOps#channelUnitsForPoints}，本类里已不再出现 {@code floorToPhialUnits}。
     * <p>
     * <b>真值住在</b> {@code TaumDistillRules.PHIAL_CAPACITY}（= TC {@code ItemEssence.java:109-185}
     * 实测的单瓶容量，也是 {@code TaumBridge#capacityOf} 给瓶的档位、{@code newFilledContainer} 的装填
     * 上限），本行是<b>转发</b>，不留第二份 8：取出侧（面板游标/背包）与通道侧的"一只瓶折多少点"读的都是这里，
     * 而"瓶子究竟装几点"只有 {@code crossmod/taum} 那一侧认识。
     * <p>
     * ★随之生效的口径（裁定 C1，★R92-① 后<b>仅取瓶路</b>）：<b>取瓶量向下取整到本常量的整数倍，余数留盘</b>
     * ——TC 的 {@code ItemEssence} 没有半瓶语义（{@code canHoldPartialAmount = false}），自造半瓶就是把
     * 私有形状送进第三方兼容面。换算见下面两条派生常量。
     * ⚠ 本名字 R86 曾以 {@code =1}（晶粒度）存在并被当作零调用方删掉，R88 起带着新值与新调用方回来，
     * 文件末尾那条"★R88 同名提醒"就是为这件事留的。
     */
    public static final int ESSENCE_OUT_UNIT_POINTS = TaumDistillRules.PHIAL_CAPACITY;
    /**
     * ★★<b>R92-① 裁定：源质「口袋 ↔ 元件」<b>通道档</b>的搬运粒度 = 1 点</b>（本行是这条粒度的唯一落点）。
     * <p>
     * 它与 {@link #ESSENCE_OUT_UNIT_POINTS}（瓶物化档 = 8 点）<b>是两件事，不是同一件事的两个名字</b>：
     * <ul>
     * <li><b>通道档（本常量）</b>：AE2 源质原生通道里点数进出的粒度。通道单位与点数的倍率由
     * {@code EssenceNativeChannels} 的容器探针实测，天然细到 1 点 ⇒ 上传/下传<b>不再向下取整到整瓶</b>，
     * 组上限停在任意点数都搬得动（与 {@link #FILTER_CAP_STEP_ESSENCE} 天然重合，R88 起挂着的那条张力
     * 就此收口）。</li>
     * <li><b>瓶物化档（{@link #ESSENCE_OUT_UNIT_POINTS}）</b>：面向玩家的取瓶粒度，一灌就是整瓶 ⇒
     * <b>取出侧仍是 8 点，R88 裁定 C1 对这一侧原样有效</b>。</li>
     * </ul>
     * ★两档之比（{@link #ESSENCE_OUT_UNIT_POINTS} / 本值）= "一次取瓶动作折多少个通道档"，仍不写死倍率；
     * 本常量的执法点在 {@code PocketEssenceChannelOps#channelUnitsForPoints}（唯一的量化点，别处不得再抄）。
     */
    public static final int ESSENCE_CHANNEL_UNIT_POINTS = 1;
    /**
     * 一次取出动作的<b>瓶数</b>上界 = {@link #ESSENCE_OUT_MAX_POINTS_PER_ACTION} /
     * {@link #ESSENCE_OUT_UNIT_POINTS} = <b>8</b>（派生，不留字面量；给 tooltip 与面板回执读数用，
     * 执法仍以点数为判据）。8 只瓶在一叠之内装得下（TC 瓶 {@code maxStackSize = 64}），
     * 所以"一次动作"天然不需要拆叠；拆叠循环只在 12 格/背包落位那一面（见 {@code NekoPocketPanel}）。
     */
    public static final int ESSENCE_OUT_MAX_PHIALS_PER_ACTION = ESSENCE_OUT_MAX_POINTS_PER_ACTION
        / ESSENCE_OUT_UNIT_POINTS;
    /**
     * 单 tag 满格（{@link #ESSENCE_CAP_PER_TAG} 点）折算的<b>瓶数</b> = <b>32</b>（派生）。
     * 与 {@link #ESSENCE_OUT_MAX_PHIALS_PER_ACTION} 的差就是"一格存满也要分 {@code 32 / 8 = 4} 次掏"
     * 这条玩家可见节奏的来源。
     */
    public static final int ESSENCE_MAX_PHIALS_PER_TAG = ESSENCE_CAP_PER_TAG / ESSENCE_OUT_UNIT_POINTS;
    static {
        // 上面两条派生量都做了<b>整除</b>：一旦上限不再是从粒度上派生（例如有人把 256 改成 255），
        // 折算瓶数会静默少一瓶，而 tooltip 与实机读数各说各话。构造期就炸，别留给实机。
        // ★R95 S5：升级档（4096）一并核 —— 两档都必须是整瓶的倍数，否则 STACK 位一开就是坏账。
        if (ESSENCE_CAP_PER_TAG % ESSENCE_OUT_UNIT_POINTS != 0
            || ESSENCE_CAP_PER_TAG_UPGRADED % ESSENCE_OUT_UNIT_POINTS != 0
            || ESSENCE_OUT_MAX_POINTS_PER_ACTION % ESSENCE_OUT_UNIT_POINTS != 0) {
            throw new IllegalStateException(
                "[pocket] 源质上限不是整瓶（粒度 " + ESSENCE_OUT_UNIT_POINTS
                    + "）的整数倍: cap="
                    + ESSENCE_CAP_PER_TAG
                    + ", 升级档="
                    + ESSENCE_CAP_PER_TAG_UPGRADED
                    + ", 单次上界="
                    + ESSENCE_OUT_MAX_POINTS_PER_ACTION);
        }
    }
    /**
     * 源质支<b>声明档</b>（ghost 组上限）的上界与"未设置"时的回落值。
     * ★★<b>R92-② 裁定：= {@link #ESSENCE_CAP_PER_TAG}（256 点），与 {@link #ESSENCE_OUT_MAX_POINTS_PER_ACTION}
     * （一次<b>取瓶</b>动作的 64 点上界）解耦</b> —— 用户实机读数口径："源质绑定标记时其上限为 256 而不是 64"。
     * <p>
     * ★<b>本号正面撤销 R84「三处解耦」裁定的显示侧半条</b>（原文在 {@code decision-ledger.md} 的 R84 节）：
     * 那条裁定把声明档天花板钉在取瓶动作上界 64 上，理由是"一批 256 点 ⇒ 一次要落 32 只瓶，而
     * {@code extractEssence} 的『放不下就注回』兜底粒度是整瓶 ⇒ 差额折算不成整数就<b>净吞点数</b>"
     * （R85 耦合审计里同形状点过一次，R88 载体改判后换了说法但结论一字未动）。
     * ★<b>该否决理由的前提已由 R92-① 消除</b>：通道两条腿改按<b>通道档 1 点</b>量化
     * （单源 {@code PocketEssenceChannelOps#channelUnitsForPoints}），落点预检也从"逐瓶回退 + 每退一次
     * 重跑 {@code canAcceptAll}"收口成一次 {@code PocketEssenceStore#roomFor} ⇒ 下传<b>不再按瓶取整</b>，
     * 256 点就是 256 点，"折算不成整数"这一族形状结构性不可达（V 段 ★R92-② G3 把这两条前提钉成读数）。
     * <p>
     * ★<b>两义仍然不同</b>，只是这次同值：本常量是"一条声明一次最多补满多少点"（<b>声明档语义</b>），
     * {@link #ESSENCE_OUT_MAX_POINTS_PER_ACTION} 是"玩家一次掏多少点进游标/背包"（<b>取瓶动作语义</b>）。
     * 前者跟每格存储上限走是刻意的：一格存得下 256 ⇒ 声明就该能一次要满 256，否则"已绑定元件 1/1"
     * 那一档永远填不满。后者不跟本常量走 ⇒ {@link #ESSENCE_OUT_MAX_PHIALS_PER_ACTION} 那套瓶数账不变。
     */
    public static final int FILTER_CAP_CEILING_ESSENCE = ESSENCE_CAP_PER_TAG;
    /**
     * ★物品支<b>服务端</b>一侧的收口上界 = 不设界（刻意）。
     * <p>
     * 物品的自然满量是<b>该物品自己的</b> {@code maxStackSize}（1 / 16 / 64 / 960 都可能），而"解出这一件
     * 能叠多少"要拿着 {@code ItemStack} 才做得到；在纯 JVM 的 {@code PocketGhostRequest} 里猜一个数
     * 就是第二处真相（还会把 16 格的药材判成"最多 64"）。⇒ 请求侧只保证下界，真正的收口在消费侧
     * {@code min(cap, maxStackSize)}（见 R83 C2 记录里的 Ops 待办 O-1，那一行本来就在那里取
     * {@code wanted.getMaxStackSize()}）。
     */
    public static final int FILTER_CAP_CEILING_ITEM_SERVER = Integer.MAX_VALUE;

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
    /** burst 动画显示窗口的 tick 数 = {@link PocketConstants#BURST_ANIMATION_MS} / {@link #MILLISECONDS_PER_TICK}。 */
    public static final int BURST_SHOW_TICKS = (int) (BURST_ANIMATION_MS / MILLISECONDS_PER_TICK);

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
    // ★R86 删除 ESSENCE_OUT_UNIT_POINTS(=1) 与 ESSENCE_OUT_SHIFT_POINTS(=64)：取出侧改成
    // "左键该组上游标 / Shift 该格整份进背包"后两条再无读点，留着就是零调用方的公共面（R85 耦合审计口径）。
    // ★R88 同名提醒：ESSENCE_OUT_UNIT_POINTS 这个名字被<b>重新启用且换了值</b>（1 → 8，见上面那两条
    // R88 常量与裁定 C1），语义位相同（"一次搬运的粒度"）、载体不同（晶 1 点 → 瓶 8 点）。
    // 读 R86 档案里的"=1"时不要按现役值理解；ESSENCE_OUT_SHIFT_POINTS 则至今仍未回来（Shift 支按整份算）。
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
     * ★<b>R83 C2</b> ghost 请求：调整这一格声明的<b>组上限</b>（alt+滚轮）。
     * <p>
     * 文法 {@code CAP|<槽号>|<区域字母>|<绝对值>}。★大容量（★R96 S3：未升级档一档就 20,000,000）<b>只能</b>走
     * {@code SYNC_GHOST_REQUEST}（面板那侧的字符串同步键）：自家 int 动作通道按 {@code code*1024+arg}
     * 打包，{@code 20_000_000 / 1024 = 19531} 会落进 {@code onServerAction} 的 {@code default: break}
     * ⇒ 静默失效（R64c 的容量判据）。这里传的是<b>绝对值</b>而非增量：增量要在服务端知道"当前值"，
     * 而物品支的当前默认值 = 该物品自己的 {@code maxStackSize}（纯 JVM 件解不出），
     * 绝对值则两端都由 {@code PocketGhostRequest#nudgedCap} 这<b>一条</b>算式算出 ⇒ 步进表只有一份。
     */
    public static final String GHOST_REQUEST_CAP = "CAP";
    /**
     * ghost 请求：解绑这一格（右键，判定与执行都在服务端）。
     * <p>
     * ★第三段<b>必须</b>带区域字母（下面三个 {@code GHOST_KIND_*}）：三个区域的槽索引各从 0 起，
     * 裸 {@code CLR|0} 分不清"清中栏第 0 格"还是"清流体槽第 0 格"（同
     * {@code PocketFilterConfig} 的 {@code (kind, slotIndex)} 复合键，R59b 偏离④ / R70）。
     */
    public static final String GHOST_REQUEST_CLEAR = "CLR";
    /**
     * ★★<b>R91-⑤⑥</b> ghost 请求：切这一格的<b>属性位</b>（中键 / alt+左 / alt+右 三个手势共用一条支）。
     * <p>
     * 文法 {@code FLG|<槽号>|<区域字母>|<手势字母>} —— ★<b>恰 4 段</b>，与 {@link #GHOST_REQUEST_CAP}
     * 同形 ⇒ {@code PocketGhostRequest#apply} 那条 {@code split(..., 4)} 一个字都不改（段数本身就是判据，
     * 放宽它必红）。
     * <p>
     * ★<b>为什么走这条通道而不是新造动作码</b>（R91-⑥ 的优先路）：{@code SYNC_ACTION} 是
     * {@code code*1024 + arg} 的<b>单值</b>打包，一个码只装得下<b>一个</b> arg ⇒"区域 + 格号"要再造一处
     * 打包算术（第二处真相）。本通道自带区域字母 + 槽索引，且服务端入口
     * {@code NekoPocketServerHandler#onServerGhostRequest} 本来就是单点（守卫 → apply → 变了才写档刷虚化）。
     * ★真值（迁移表）由<b>服务端</b>算，客户端只报表征哪个手势。
     */
    public static final String GHOST_REQUEST_FLAG = "FLG";
    /** FLG 第四段：请求<b>绑定</b>（中键）。 */
    public static final String GHOST_FLAG_BIND = "B";
    /** FLG 第四段：请求<b>记忆</b>（alt+左键）。 */
    public static final String GHOST_FLAG_MEMORY = "M";
    /** FLG 第四段：翻转<b>阻拦上传</b>位（alt+右键）。 */
    public static final String GHOST_FLAG_UPLOAD_BLOCK = "P";
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

    /**
     * ★<b>R85 N3</b>：ghost 声明 blob（{@code SYNC_GHOST}）单次同步的<b>字符预算</b>。
     * <p>
     * <b>为什么必须有预算</b>：一条 {@code ITEM} 声明的载荷键里带着整段 gzip+base64 的 NBT
     * （{@code PocketAeChannelOps#contentKey} → {@code NbtBase64Util#nbtToBase64}，<b>没有长度上限</b>），
     * 一条带肥 NBT 的声明（GT 工具箱、带库存的容器）就能把整根通道顶爆。上游
     * {@code StringSyncValue.serialize} → {@code NetworkUtils.writeStringSafe(..., Short.MAX_VALUE - 74)}
     * 对超过 <b>32,693 字节</b>的串<b>静默截断、只打一行 WARN</b> ⇒ 尾部声明在客户端既不虚化也不执法一致，
     * 玩家往里放东西被服务端拒收，读起来就是"放进去又弹回来"。
     * <p>
     * ★★<b>R97 R4 改值：24,000 → <b>32,000</b></b>（决策④：把现实规模收进全量同步）。
     * <b>为什么是 32,000 而不是贴着 32,693</b>：本 blob 只由
     * 枚举名 / 十进制槽号 / base64 字母表 / {@code '|'} / {@code ';'} 组成 ⇒ 全部是 ASCII 单字节字符，
     * 字符数即字节数，所以字符预算就是字节预算。693 字节的余量覆盖同一根同步通道所在包的<b>其它
     * 字段与帧头</b>（{@code writeStringSafe} 的硬顶按包内可写字节算，不是只按本串算）；
     * 余量刻意不再放大——R4 的目的就是让「现实规模的声明集」落进全量（24k 时代会被尾部截断），
     * 而<b>病理规模</b>（135 格全满 gzip NBT）仍走既有 tail-stop + {@code ghostNotSyncedCount} 读数，
     * 这两条语义与读数逐字不动。
     * <p>
     * 超预算的处置见 {@code NekoPocketPanel#ghostBlobOf}：<b>停止追加尾部声明</b>（已写部分的字节格式与
     * 无预算时逐字相同 ⇒ 解析器一个字都不改），客户端把差额报成一条玩家可见读数
     * （{@code gtit.pocket.ghost.not_synced}，★措辞必须点名"服务端执法不受影响"，不得写成丢件）。
     */
    public static final int GHOST_BLOB_MAX_CHARS = 32_000;

    private PocketConstants() {}
}
