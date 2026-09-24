package com.miaokatze.gtit.common.items.pocket;

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
     * <p>
     * ★取出侧与它<b>刻意解耦</b>：一次动作最多 {@link #ESSENCE_OUT_MAX_POINTS_PER_ACTION} 点，抬上限不等于
     * 一次能掏 256 点。★R88 载体改判后这一句换算成瓶：256 点 = {@link #ESSENCE_MAX_PHIALS_PER_TAG} 只满瓶，
     * 一次动作最多 {@link #ESSENCE_OUT_MAX_PHIALS_PER_ACTION} 只 —— 旧文案里"晶化源质单堆 64"那个理由
     * 已随载体一起退役（晶只读不产，见 {@code TaumBridge#newCrystalStack} 的 ★R88 注）。
     */
    public static final int ESSENCE_CAP_PER_TAG = 256;
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
    public static final int STORAGE_ROWS = 15;
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
     * 为什么统一取"上"而不是"四舍五入/向下"：这三处读数都是"<b>还要多久才发生</b>"
     * （蒸馏还剩几轮、短效通道还剩几秒），向下取整会在只剩 19 tick 时显示 0 秒 ⇒
     * 玩家读到"已经结束"但实际还在跑（R10 的"失败与状态不得说谎"同一族）。
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

    /** 短效通道节拍：每 {@link #TICKS_PER_SECOND} tick 一批（= 每秒一批，共 {@value #SHORT_CHANNEL_SECONDS} 批）。 */
    public static final int CHANNEL_TICK_PERIOD = TICKS_PER_SECOND;
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

    // ------------------------------------------------------ R83 C2（缺陷 6）：每条声明的组上限口径
    //
    // ★这一整段的存在理由：用户那句「alt 滚轮调整数量…物品每次 1 个；流体每次 1%；源质每次 1 个；
    // alt+ctrl+滚轮 步进 ×10」里每一个数字都只允许活在这里（D-6 裁定：×10 是<b>步进</b>的倍率，
    // 不是第二处通道速率 ⇒ 本片一个字节都不碰 Config.pocketChannelPairsPerSecond、CHANNEL_TICK_PERIOD
    // 与 ticksToSecondsCeil）。步进量必须由<b>服务端</b>与<b>客户端读数</b>共读同一份常量，
    // 否则"滚一下看到的"与"落档的"就是两个数。

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
    /** 流体支"1%"的档数（★分母按 D-6 = 单 tank 容量 {@link #FLUID_BAR_CAPACITY_ML}，不是 288M 合计）。 */
    public static final int FILTER_CAP_PERCENT_STEPS = 100;
    /** 流体支一次滚轮的步进 = 单 tank 容量的 1% = {@code 16,000,000 / 100 =} <b>160,000 mB</b>（派生，不留字面量）。 */
    public static final int FILTER_CAP_STEP_FLUID = FLUID_BAR_CAPACITY_ML / FILTER_CAP_PERCENT_STEPS;
    /** alt+ctrl 的步进倍率（★作用在<b>步进</b>上：物品 1→10、流体 1%→10%、源质 1→10）。 */
    public static final int FILTER_CAP_FAST_MULTIPLIER = 10;
    /**
     * 流体支组上限的<b>上界</b>，同时是"未设置"时的<b>回落值</b>（两者同值不是巧合：
     * 现行为就是"一拍填到本 tank 的自然满量"，回落必须逐字复现它）。
     */
    public static final int FILTER_CAP_CEILING_FLUID = FLUID_BAR_CAPACITY_ML;
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
        if (ESSENCE_CAP_PER_TAG % ESSENCE_OUT_UNIT_POINTS != 0
            || ESSENCE_OUT_MAX_POINTS_PER_ACTION % ESSENCE_OUT_UNIT_POINTS != 0) {
            throw new IllegalStateException(
                "[pocket] 源质上限不是整瓶（粒度 " + ESSENCE_OUT_UNIT_POINTS
                    + "）的整数倍: cap="
                    + ESSENCE_CAP_PER_TAG
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
     * 文法 {@code CAP|<槽号>|<区域字母>|<绝对值>}。★大容量（流体 16,000,000 一档）<b>只能</b>走
     * {@code SYNC_GHOST_REQUEST}（面板那侧的字符串同步键）：自家 int 动作通道按 {@code code*1024+arg}
     * 打包，{@code 16_000_000 / 1024 = 15625} 会落进 {@code onServerAction} 的 {@code default: break}
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
     * <b>为什么是 24,000 而不是贴着 32,693</b>：本 blob 只由
     * 枚举名 / 十进制槽号 / base64 字母表 / {@code '|'} / {@code ';'} 组成 ⇒ 全部是 ASCII 单字节字符，
     * 字符数即字节数，所以字符预算就是字节预算，换算不需要余量系数。留出的 ~8.6 KB 是给
     * ①同一个包里的其它字段与包头的字节、②将来在键式样里多出任何非 ASCII 字符（那才会"字符数 &lt; 字节数"）
     * 的<b>硬余量</b>——预算一旦贴着硬顶，超出方式就是上游那条静默截断，正是要避免的那件事。
     * <p>
     * 超预算的处置见 {@code NekoPocketPanel#ghostBlobOf}：<b>停止追加尾部声明</b>（已写部分的字节格式与
     * 无预算时逐字相同 ⇒ 解析器一个字都不改），客户端把差额报成一条玩家可见读数
     * （{@code gtit.pocket.ghost.not_synced}，★措辞必须点名"服务端执法不受影响"，不得写成丢件）。
     */
    public static final int GHOST_BLOB_MAX_CHARS = 24_000;

    private PocketConstants() {}
}
