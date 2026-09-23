package com.miaokatze.gtit.common.items.pocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;
import com.miaokatze.gtit.util.NbtBase64Util;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.util.item.AEItemStack;

/**
 * {@link PocketChannelOps} 的 AE2 实现——本切片里唯一触达 AE2 的类，测试不覆盖它（端到端行为属实机项）。
 * <p>
 * 三条硬口径：
 * <ol>
 * <li><b>写入唯一通路（R7）</b>：
 * {@code AEApi.instance().registries().cell().getCellInventory(stack, host, type)}，
 * 其中 {@code host} 传<b>驱动器/ME 箱 tile 本体</b>（它就是 {@code ISaveProvider}）而不是 {@code null}。
 * 传 null 时 {@code CellInventory:376-378} 不回调宿主，{@code saveChanges} 链断裂 ⇒
 * {@code setDirty}/{@code is_empty}/分区门禁/Issue#16 嵌套守卫全被跳过 = 静默丢件面。
 * {@code Platform.poweredInsert} <b>禁用</b>（按 AE 能量限流，与"瞬时一次性全量"矛盾）。</li>
 * <li><b>识别口径（R6，★R87-c 修订）</b>："已识别 + 宿主在位"的门禁统一收在 {@link #foundOf}
 * （probe 解析到 tile 且它是 {@code IChestOrDrive}）；<b>handler 解析不再要求
 * {@code getCellArray(type)} 非空</b>——AE2 宿主对多通道元件只把第一个命中的通道注册进 cellsMap
 * （{@code TileDrive.updateState} 的 break，遍历序是 HashMap 不可控），拿它当门会把流体/第三方通道
 * 误判成"无通道"（R87-c 的根因）。也绝不缓存"已识别"结论——{@code TileDrive.updateState} 被
 * {@code isCached} 闩住，只有 {@code isActive()} 翻转才重算。</li>
 * <li><b>批后必须显式通知网络视图（R7）</b>：AE2 对驱动器元件不轮询，直写后终端/合成读到的是陈旧库存。
 * 首选 typed {@code IStorageGrid.postAlterationOfStoredItems(IAEStackType, Iterable, BaseActionSource)}
 * （<b>必须走 typed 重载</b>：default 版对第三方通道两个 {@code if} 都不命中会静默什么都不做，
 * 而 {@code GridStorageCache:317-320} 的 typed 覆盖才能处理），
 * 取路 {@code IChestOrDrive → ICellContainer extends IActionHost → getActionableNode() →
 * IGridNode.getGrid() → IGrid.getCache(IStorageGrid.class)}；
 * 失败或拿不到原型栈时兜底 {@code IGrid.postEvent(new MENetworkCellArrayUpdate())}（全表重建，语义最稳）。</li>
 * </ol>
 * <p>
 * handler 实例<b>一律每次现取</b>：元件内容在外置桶里，缓存实例会读到陈旧内容，
 * 且 {@code InfinityCellHandler.getCellInventory} 每次都新建对象，缓存实例键毫无意义（R9）。
 * <p>
 * <b>抽取方向三支齐全</b>（R45b 的缺口由本批闭合，此前流体支与源质支直接 {@code return NO_CHANNEL}
 * ⇒ 需求 2「要素栏取出→源质」与需求 4「按配置补满流体」两条并列要求整体静默失效）：
 * <ul>
 * <li>物品支 → 本条声明自己那一格（★R84：中栏声明格就是落点，旧实现落玩家背包 ⇒ 需求格永远空着）；</li>
 * <li>流体支 → 口袋流体条（{@link PocketSession}），先问落点空间再抽，抽了放不下就原路注回
 * （★R90 T3 起注入/抽取与两条算术纯搬移到 {@link PocketFluidChannelOps}，复用本类的批级缓存与
 * 动作源，行为零变化；来源快照的 {@code appendFluidSources} 仍在本类，与 {@code appendEssenceSources} 同位）；</li>
 * <li>源质支 → ★R87-A 起整体搬去 {@link PocketEssenceChannelOps}（那一次是行数纪律的<b>纯搬移</b>）：
 * 经 AE2 的<b>容器契约</b>（★R91-① 改口，单源在 {@link EssenceNativeChannels#channelStackFromContainer}）
 * 以<b>自家装满的源质瓶 {@code ItemEssence}</b> 为探针
 * 反算数额后<b>溶回 72 格源质盘</b>（★R88 载体与落点双双改判，旧形状是"以 {@code ItemCrystalEssence}
 * 为探针、1 点 = 1 晶、物化成晶进口袋真实栏"）。现在的单位语义是
 * <b>1 只瓶 = {@value com.miaokatze.gtit.crossmod.taum.TaumDistillRules#PHIAL_CAPACITY} 点</b>，
 * 且两侧都按<b>整瓶</b>向下取整、零头留在原侧（裁定 C1）；旧晶只保留"读得回点数"的识别支（C2 只读不产）。
 * ★R90 E2（AUQ-①=B）：满瓶探针<b>不再有物品通道兜底</b>——"是不是源质原生通道"由
 * {@link EssenceNativeChannels} 单源判定，上传/下传都只走原生通道；无原生通道 ⇒ {@code NO_CHANNEL}
 * 拒收（经 {@code PocketChannelRunner.Report#essenceNoChannel} 进面板回执，非静默），<b>上传永不产瓶</b>。
 * 不猜第三方 mod 的私有栈格式这一条 R15/R31 起未变。
 * 本类只在 {@link #inject}/{@link #extract} 的分派口委派过去。</li>
 * </ul>
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class PocketAeChannelOps implements PocketChannelOps {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /**
     * 口袋是 item 拿不到 {@code IActionHost}，{@code PlayerSource}/{@code MachineSource} 都需要 host，
     * 故用裸实例（{@code BaseActionSource} 是可实例化具体类）；也因此不走 {@code poweredInsert} 的
     * {@code src.isPlayer()} 统计分支。
     */
    private static final BaseActionSource SOURCE = new BaseActionSource();

    /**
     * 服务端玩家本体：只用于 tick 读数与元件解析（维度/坐标）——★R84 起它<b>不再是注入来源</b>
     * （旧 R12 读法 B"背包 36 格当来源"已作废，现来源是中栏，见 {@link #snapshotSources()}）。
     */
    private final EntityPlayer player;
    /** 口袋本体，扫描来源时按身份排除，避免"口袋自己吸自己"（R12 点名的自吸风险）。 */
    private final ItemStack pocket;
    private final PocketCellProbe probe;
    /**
     * 拉取方向的落点（流体条与口袋真实栏都在活会话里）。
     * <p>
     * ★<b>R85 小项 1 起本字段按"恒非空"用</b>：物品支（{@link #extractItem}）已经不再为
     * {@code session == null} 留分支（生产两条构造点都带会话，driver 那道守卫还在这之前），
     * 流体支与源质支仍各自保留"没有落点 ⇒ 一克都不抽"的判据（那是<b>可达</b>的：
     * {@code PocketFluidChannelOps#extractFluid} 的 tank 越界与 {@code room <= 0} 走的是同一形状）。
     * 会话缺失本来就是关屏+通道停之后的正常态，不建条目也不报错（R45b 禁止的是"返回 OK 却什么都不搬"）。
     */
    private final PocketSession session;

    /** contentKey → 本轮见过的 AE 栈原型，用于把带符号 delta 还原成 {@code IAEStack} 列表。 */
    private final Map<String, IAEStack<?>> prototypes = new HashMap<>();
    /** 实验 E1 的一次性取证日志开关：typed post 是否真被接住。 */
    private static boolean loggedTypedPost;

    // ------------------------------------------------------------------ ★R85 P1：批级缓存（住在本类，不外泄）
    //
    // 三条链路的重复计算账（取证档案 r85-ret-deadpath §2.2 / r85-ret-coupling §3c）：
    // 一次 burst 批里 135 个来源格逐个走 inject ⇒ foundOf(diskuuid) + handlerOf(found, type) 全同跑 135 次
    // （每次含 chunkExists + getTileEntity + 两次 observe 扫驱动器槽读元件 NBT），contentKey 对同一个
    // ItemStack 对象在同一拍算两次（snapshotSources 与 inject 的重读校验）。
    // ★缓存<b>不外泄到窄接口</b>：{@link PocketChannelOps.SourceSlot} 的纪律是"只传 String/int/long/枚举，
    // 逻辑核心可纯 JVM 实例化"（见 PocketChannelOps.java:6-19），所以这里记 ItemStack 引用，而<b>不</b>把
    // ItemStack 塞进那个值对象。
    //
    // ★<b>失效点（三个，一个都不能少）</b>：
    // ① {@link #announce} 批尾（档案给的口径）；② {@link #snapshotSources} 头部（下一批的快照起点）；
    // ③ <b>tick 变化</b>。②③ 补的是档案没写的一个洞：{@code PocketChannelRunner#flush} 只在
    // {@code deltas} 非空时才调 announce ⇒ "零搬运的一批"根本不会走到①，只靠①就会把缓存跨批留活。
    // 类 javadoc 那条"handler 一律每次现取"针对的是<b>跨批</b>陈旧（元件内容住在 AE 侧外置桶里，别的 mod
    // 可能在两批之间改它）；<b>同一拍</b>里口袋是唯一写入者（一次批全程在服务器主线程、{@code handler
    // .extractItems}/{@code injectItems} 不回回调口袋），所以批内重复才是本缓存要消掉的那 135 倍。
    /** 批级元件解析缓存：这一批记住的 {@code diskuuid}（{@code null} = 无缓存）。 */
    private String cachedFoundId;
    /** 与 {@link #cachedFoundId} 同时失效：解析到的元件栈与宿主。 */
    private Found cachedFound;
    /** 批级 handler 缓存的键 = {@code diskuuid + '#' + typeId}。 */
    private String cachedHandlerKey;
    /** 与 {@link #cachedHandlerKey} 同时失效：该 (元件, 通道) 的 handler。 */
    private IMEInventoryHandler cachedHandler;
    /** 上面两枚缓存所属的 tick（{@link #currentTick()}）；变了即整体作废。 */
    private long cachedTick = Long.MIN_VALUE;
    /**
     * 批级 contentKey 缓存：本批 {@link #snapshotSources} 算过的栈 → 那次算出的键。
     * 用 <b>身份</b>表（{@link IdentityHashMap}）而不是 equals 表：本缓存的判据是"还是<b>那一个</b>栈对象
     * 且件数/损伤/NBT 容器都没换"，绝不是"内容长得一样"（那样就把 R84 的防陈旧校验判成恒真了）。
     */
    private Map<ItemStack, KeyStamp> batchKeys;

    public PocketAeChannelOps(EntityPlayer player, ItemStack pocket) {
        this(player, pocket, PocketCellProbe.INSTANCE, null);
    }

    public PocketAeChannelOps(EntityPlayer player, ItemStack pocket, PocketSession session) {
        this(player, pocket, PocketCellProbe.INSTANCE, session);
    }

    PocketAeChannelOps(EntityPlayer player, ItemStack pocket, PocketCellProbe probe) {
        this(player, pocket, probe, null);
    }

    public PocketAeChannelOps(EntityPlayer player, ItemStack pocket, PocketCellProbe probe, PocketSession session) {
        this.player = player;
        this.pocket = pocket;
        this.probe = probe == null ? PocketCellProbe.INSTANCE : probe;
        this.session = session;
    }

    // ------------------------------------------------------------------ 同包访问口（★R87-A：源质支搬去
    // PocketEssenceChannelOps 后，那边经这四个口复用本类的批级缓存/会话/动作源；字段保持 private）

    /** 拉取方向落点与源质/流体条所在的活会话（可能为 null，语义见字段上的 javadoc）。 */
    PocketSession session() {
        return session;
    }

    /** 注入/抽取共用的裸动作源（口袋拿不到 {@code IActionHost}，见 {@link #SOURCE} 的说明）。 */
    static BaseActionSource actionSource() {
        return SOURCE;
    }

    // ------------------------------------------------------------------ 时钟与点检

    @Override
    public long nowMs() {
        return System.currentTimeMillis();
    }

    @Override
    public long currentTick() {
        return player == null ? 0L : player.ticksExisted;
    }

    /**
     * 本轮不可传输即 true：元件解析不到（含"区块未加载/无法判定"）。
     * <p>
     * 注意与"解绑"的区别：解绑只在 {@link PocketCellProbe#stillPresent(String)} 为 false（确认不在原处）
     * 时发生，本方法不为"无法判定"清理绑定。
     */
    @Override
    public boolean isCellLost(String diskuuid) {
        return !probe.isResolvable(diskuuid);
    }

    @Override
    public List<String> channelIdsOf(String diskuuid) {
        final List<String> ids = new ArrayList<>(3);
        // ★R85 P1：走批级缓存（同一 disksuuid 在一批里被 channelIdsOf / inject / extract 各问一遍）
        final Found found = foundOfCached(diskuuid);
        if (found == null) {
            return ids;
        }
        // 遍历序即 allSupportedTypes() 的序（物品→流体→注册中的第三方），但持久化/轮转一律只用字符串 id。
        // ★R87-c：handlerOf 不再看 getCellArray —— 多通道件（自家无限单元）在这里会给出全部通道，
        // 不再被宿主 cellsMap 的"只注册第一个命中"截成一条。
        for (final IAEStackType<?> type : InfinityStackTypes.allSupportedTypes()) {
            if (handlerOf(found, type) != null) {
                ids.add(type.getId());
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------ 来源快照

    @Override
    public List<SourceSlot> snapshotSources() {
        final List<SourceSlot> slots = new ArrayList<>();
        // ★R84 作废 R12 读法 B：注入来源由"玩家背包 mainInventory 36 格"改为<b>只取猫猫包中栏</b>
        // （用户原话"直接把我整个背包栏给输入到元件了（应该只输入猫猫包里面的）"）。
        // ★ghost 声明格不参与上传（同轮裁定），它只是抽取落点，绝不回流成来源——否则"从元件抽出来
        // 补进需求格、下一拍又被自己灌回元件"会成环。
        if (session == null) {
            // 中栏真相只在活会话（承载栈的解析结果）里，关屏且会话已回收 ⇒ 本拍不猜、不搬运
            return NO_SOURCES;
        }
        // ★R85 P1：一批的起点先丢弃上一批的派生缓存（announce 只在 deltas 非空时被调，不能只靠它）。
        invalidateBatchCache();
        cachedTick = currentTick();
        final int size = session.storageSlots();
        for (int i = 0; i < size; i++) {
            final ItemStack stack = session.storageStackAt(i);
            if (stack == null || stack.stackSize <= 0) {
                continue;
            }
            if (pocket != null && stack == pocket) {
                // 排除手持口袋所在槽：否则通道会把口袋吸进自己绑定的元件（R12 点名的自吸风险，口径不变）
                continue;
            }
            if (session.isStorageGhostDeclared(i)) {
                continue;
            }
            slots.add(new SourceSlot(i, batchContentKey(stack), stack.stackSize));
        }
        // ★R86（缺陷 3）：口袋里的流体与源质此前<b>根本没有通往元件的路</b>（来源快照只枚举中栏物品，
        // 而非物品通道又被 :457 判成"无事可做"）。两支各出一条来源，由 inject 的三类分派接住。
        appendFluidSources(slots);
        appendEssenceSources(slots);
        return slots;
    }

    /**
     * ★R86（缺陷 3）：流体条 → 来源条目（一条非空 tank 一条）。
     * <p>
     * ★<b>不进 {@code batchKeys}/{@code KeyStamp} 那套批级缓存</b>：那四条复用判据全是围着
     * {@code ItemStack} 的对象引用与 NBT 键数建的（R85 P1），流体没有这些形状。防陈旧改由
     * {@code PocketFluidChannelOps#injectFluidSource} <b>现读现比</b>（内容键 + 量），代价只是每批多读 18 次引用比较，
     * 换来的是"tank 被玩家换过 ⇒ 一定搬不出去"而不是"看缓存脸色"。
     */
    private void appendFluidSources(List<SourceSlot> slots) {
        final int tanks = session.fluidTankCount();
        for (int tank = 0; tank < tanks; tank++) {
            final FluidStack fluid = session.fluidInTank(tank);
            if (fluid == null || fluid.amount <= 0 || fluid.getFluid() == null) {
                continue;
            }
            slots.add(
                new SourceSlot(
                    tank,
                    PocketFilterConfig.fluidKey(
                        fluid.getFluid()
                            .getName()),
                    fluid.amount,
                    SourceKind.FLUID));
        }
    }

    /**
     * ★R86（缺陷 3）：源质表 → 来源条目（一个有货的 tag 一条，与格位无关）。
     * <p>
     * 键用 {@code essenceKey("", tag)}：口袋里的一点源质<b>不属于任何 AE2 通道</b>，typeId 那一段
     * 天然为空（读侧 {@code parseKey} 会解出 {@code typeId == ""}，见其"按最后一个分隔符切"的注释）。
     * ★R90 E2：哪条通道收得下由 {@link PocketEssenceChannelOps#injectEssenceSource} 经
     * {@link EssenceNativeChannels}（★R91-①：满瓶探针 + AE2 <b>容器契约</b>，单源）判——<b>只认
     * 源质原生通道</b>，缺席按 {@code NO_CHANNEL} 拒收，不再有任何物品通道兜底。
     */
    private void appendEssenceSources(List<SourceSlot> slots) {
        for (Map.Entry<String, Integer> entry : session.essenceStock()
            .entrySet()) {
            final String tag = entry.getKey();
            final Integer points = entry.getValue();
            if (tag == null || tag.isEmpty() || points == null || points <= 0) {
                continue;
            }
            slots.add(
                new SourceSlot(
                    PocketConstants.FILTER_SLOT_UNSET,
                    PocketFilterConfig.essenceKey("", tag),
                    points,
                    SourceKind.ESSENCE));
        }
    }

    /**
     * 物品内容标识：{@code itemId + meta + nbtString}（R17，流体/源质键由配置侧同族给出）。
     * <p>
     * ★<b>无缓存的裸实现</b>：每次都是一遍 NBT 序列化 + gzip + base64，所以<b>同一批里对同一个栈</b>
     * 必须走 {@link #batchContentKey(ItemStack)}（R85 P1），只有"跨批/独立一次"的调用才直接用本方法。
     */
    public static String contentKey(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        final Item item = stack.getItem();
        if (item == null) {
            return "";
        }
        final NBTTagCompound data = stack.stackTagCompound;
        final String nbt = data == null || data.hasNoTags() ? "" : NbtBase64Util.nbtToBase64(data);
        return PocketFilterConfig.itemKey(Item.getIdFromItem(item), stack.getItemDamage(), nbt == null ? "" : nbt);
    }

    /**
     * ★<b>R85 P1</b>：批级 contentKey（<b>只</b>在本类内用；窄接口 {@code SourceSlot} 一个字都不带 MC 类型）。
     * <p>
     * 省掉的是档案点名的那一倍：一次 burst 批里同一个来源栈被算两次 base64（{@link #snapshotSources}
     * 与 {@link #inject} 的防陈旧重读校验）⇒ 135 格 = 270 次 gzip+base64，本方法把它压回 135 次。
     * <p>
     * ★<b>复用判据（四条，全中才复用）</b>：同一个 {@code ItemStack} <b>对象引用</b> + 同一件数 +
     * 同一损伤 + 同一个 NBT 容器引用（且其键数相同）。这四条同时保住 R84 的<b>防陈旧判据</b>：
     * {@code PocketInventory}/{@code ItemStackHandler#setStackInSlot} 存的是<b>副本</b>，槽位内容一旦变了
     * 就是<b>另一个对象</b> ⇒ 复用落空、重算 ⇒ {@link #inject} 的比对照常按新内容判"跳过"。
     * <b>残余边界（必须知道）</b>：如果有人<b>就地</b>改一个已入槽栈的 NBT <b>值</b>（不换引用、不改键数），
     * 本缓存看不出变化；全仓对 {@code storage()} 的写全部走 {@code setStackInSlot}（副本替换），
     * 故该形态当前不可达，写档面另有 R85 的越界判据守着（{@link #shrinkSource}）。
     * 一次批内驻留的条目数上界 = 中栏格数（135），批尾随 {@link #invalidateBatchCache()} 整体丢弃 ⇒
     * 不会像 {@code N1} 那条那样跨批滞留。
     */
    private String batchContentKey(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        // ★先对表再复用：缓存的作用域必须是"本批"，跨 tick 的条目一律不作数（同批 == 同 tick）。
        touchCacheTick();
        final KeyStamp fresh = new KeyStamp(stack);
        if (batchKeys != null) {
            final KeyStamp previous = batchKeys.get(stack);
            if (previous != null && previous.sameReadingAs(fresh)) {
                return previous.key;
            }
        }
        fresh.key = contentKey(stack);
        if (batchKeys == null) {
            batchKeys = new IdentityHashMap<>();
        }
        batchKeys.put(stack, fresh);
        return fresh.key;
    }

    /** {@link #batchContentKey} 的复用判据（★按引用与三个读数比，绝不按内容比）。 */
    private static final class KeyStamp {

        final int size;
        final int damage;
        final NBTTagCompound tags;
        final int tagKeys;
        /** 那一次算出的键（新建戳时还是空串，写进表之前被填上）。 */
        String key = "";

        KeyStamp(ItemStack stack) {
            this.size = stack.stackSize;
            this.damage = stack.getItemDamage();
            this.tags = stack.stackTagCompound;
            this.tagKeys = tags == null || tags.hasNoTags() ? 0
                : tags.func_150296_c()
                    .size();
        }

        boolean sameReadingAs(KeyStamp other) {
            return size == other.size && damage == other.damage && tags == other.tags && tagKeys == other.tagKeys;
        }
    }

    /**
     * ★R85 P1：丢弃本批的全部派生缓存（元件解析 + handler + contentKey）。
     * <p>
     * 三个调用点见上面那段注释：批尾 {@link #announce}、{@link #snapshotSources} 头部、tick 一变。
     * 特别是<b>零搬运的一批不会走到 announce</b>（{@code PocketChannelRunner#flush} 只在 deltas 非空时
     * post），所以只靠 announce 会让缓存活到下一批。
     */
    private void invalidateBatchCache() {
        cachedFoundId = null;
        cachedFound = null;
        cachedHandlerKey = null;
        cachedHandler = null;
        cachedTick = Long.MIN_VALUE;
        if (batchKeys != null) {
            batchKeys.clear();
        }
    }

    /**
     * {@link #foundOf} 的批级包装：同一 {@code diskuuid} 在一批里只解析一次（省掉 134 次
     * chunkExists + getTileEntity + observe 扫驱动器槽读元件 NBT）。
     * ★R87-A 起包内可见：源质支（{@link PocketEssenceChannelOps}）复用同一批缓存。
     */
    Found foundOfCached(String diskuuid) {
        touchCacheTick();
        if (diskuuid == null) {
            return null;
        }
        if (diskuuid.equals(cachedFoundId)) {
            return cachedFound;
        }
        final Found found = foundOf(diskuuid);
        cachedFoundId = diskuuid;
        cachedFound = found;
        return found;
    }

    /**
     * {@link #handlerOf} 的批级包装：同一 {@code (元件, 通道)} 在一批里只造一次 handler。
     * <p>
     * ★与类 javadoc"handler 一律每次现取"不矛盾：那条针对的是<b>跨批</b>（元件内容住在 AE 侧外置桶，
     * 两批之间可能被别的 mod 改），而本缓存的作用域被 {@link #invalidateBatchCache()} 钉死在<b>一批</b>内；
     * 批内口袋是该 handler 的唯一写入者（AE 的 {@code injectItems}/{@code extractItems} 不回回调口袋），
     * 所以批内复用读到的就是本批自己写进去的最新状态。
     * ★R87-A 起包内可见：源质支（{@link PocketEssenceChannelOps}）复用同一批缓存。
     */
    IMEInventoryHandler handlerOfCached(Found found, IAEStackType<?> type, String diskuuid) {
        if (found == null || type == null) {
            return null;
        }
        final String key = diskuuid + '#' + type.getId();
        if (key.equals(cachedHandlerKey)) {
            return cachedHandler;
        }
        final IMEInventoryHandler handler = handlerOf(found, type);
        cachedHandlerKey = key;
        cachedHandler = handler;
        return handler;
    }

    /** tick 变了就先整体作废（{@link #currentTick()} 在读 {@code player.ticksExisted}，零成本）。 */
    private void touchCacheTick() {
        if (player == null) {
            // 没有玩家就没有 tick 读数可言 ⇒ 保守起见不缓存（每次现解析，语义与改动前逐字相同）。
            if (cachedFound != null || cachedHandler != null || batchKeys != null) {
                invalidateBatchCache();
            }
            return;
        }
        final long tick = currentTick();
        if (tick != cachedTick) {
            invalidateBatchCache();
            cachedTick = tick;
        }
    }

    /**
     * 由内容标识还原栈（只用于抽取请求、ghost 样本与通知原型；数量由调用方给）。
     * <p>
     * public 的理由：ghost 格的<b>渲染样本</b>（GUI 侧）必须与抽取请求走同一个反解，
     * 否则"拖进去的东西"与"补出来的东西"会在 NBT 细节上分叉（R46a 的正交覆写点纪律同样要求这点）。
     */
    public static ItemStack stackFromContentKey(String contentKey, int count) {
        return stackOfKey(contentKey, count);
    }

    /** 由内容标识还原栈（只用于抽取请求与通知原型；数量由调用方给）。 */
    private static ItemStack stackOfKey(String contentKey, int count) {
        final PocketFilterConfig.Filter parsed = PocketFilterConfig.parseKey(contentKey);
        if (!(parsed instanceof PocketFilterConfig.ItemFilter item)) {
            return null;
        }
        final Item raw = Item.getItemById(item.itemId);
        if (raw == null) {
            return null;
        }
        final ItemStack stack = new ItemStack(raw, Math.max(1, count), item.meta);
        final NBTTagCompound data = NbtBase64Util.nbtFromBase64(item.nbtString);
        if (data != null) {
            stack.setTagCompound(data);
        }
        return stack;
    }

    // ------------------------------------------------------------------ 写入与抽取

    @Override
    public Outcome inject(SourceSlot source, String diskuuid, String typeId) {
        // ★R86（缺陷 3）：三类来源分派<b>必须走在下面那次 storageStackAt 之前</b> —— 流体来源的
        // {@code source.slot} 是 tank 号、源质来源是"身份全在键上"，拿它们去读中栏格要么越界静默跳过、
        // 要么搬错格（同 R85 小项 2 的越界判据）。
        if (source != null && source.kind == SourceKind.FLUID) {
            // ★R90 T3：流体支纯搬移到 PocketFluidChannelOps（宿主引用改显式 ops 首参），分派口不变
            return PocketFluidChannelOps.injectFluidSource(this, source, diskuuid, typeId);
        }
        if (source != null && source.kind == SourceKind.ESSENCE) {
            // ★R87-A：源质支整体搬去了 {@link PocketEssenceChannelOps}（那一次是行数纪律的纯搬移）；
            // ★R88 起那边的载体与落点已改判（晶 → 瓶、物品槽 → 源质盘），委派口本身不变
            return PocketEssenceChannelOps.injectEssenceSource(this, source, diskuuid, typeId);
        }
        // ★R84：来源格现在在中栏（见 snapshotSources 的口径变更），且只在活会话期内可搬运
        final ItemStack slotStack = source == null || session == null ? null : session.storageStackAt(source.slot);
        // ★R85 P1：批级缓存版 contentKey —— 判据一个字没减（还是"槽里的现在内容 vs 快照键"），
        // 只是同一批里对同一个栈对象不再重复 gzip+base64；内容被换掉 ⇒ 对象引用变了 ⇒ 必然重算。
        if (slotStack == null || slotStack.stackSize <= 0 || !batchContentKey(slotStack).equals(source.contentKey)) {
            // 槽位已被玩家换掉或掏空：本条跳过，不改写任何状态
            return new Outcome(PocketReceipt.OK, 0);
        }
        final int requested = Math.min(slotStack.stackSize, slotStack.getMaxStackSize());
        final IAEStackType<?> type = InfinityStackTypes.byId(typeId);
        final Found found = foundOfCached(diskuuid);
        final IMEInventoryHandler handler = found == null || type == null ? null
            : handlerOfCached(found, type, diskuuid);
        if (handler == null) {
            // ★R84：元件解析不到该通道的 handler 是"这只元件不吃这个通道"，不是"元件失联"。
            // 旧代码发 LOST，而 LOST.stopsBatch() 为真 ⇒ 既 break 掉该元件整串声明，又把回执报成
            // "未识别到元件"（玩家读到的是谎报）。失联由 runInjectBatch 的 isCellLost 那一支负责。
            return new Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        if (!InfinityStackTypes.ITEM_STACK_TYPE.getId()
            .equals(type.getId())) {
            // ★R84 崩溃根因（crash-2026-09-22_16.40.05-server.txt，`Ticking player` /
            // ClassCastException: AEItemStack→IAEFluidStack @ InfinityTypedCellInventory:101）：
            // 轮转把流体（或第三方）通道派给物品来源时，旧代码仍无条件造 AEItemStack 投进去 ⇒ 服务端崩。
            // 物品来源喂不了非物品通道，本条按"无事可做"跳过（OK 才不会 break 掉后面的来源）。
            return new Outcome(PocketReceipt.OK, 0);
        }
        final IAEItemStack request = AEItemStack.create(copyOf(slotStack, requested));
        if (request == null) {
            return new Outcome(PocketReceipt.LOST, 0);
        }
        rememberPrototype(source.contentKey, request);
        final boolean writable = handler.getAccess()
            .hasPermission(AccessRestriction.WRITE);
        final boolean acceptable = handler.canAccept(request);
        final IAEItemStack leftover = (IAEItemStack) handler.injectItems(request, Actionable.MODULATE, SOURCE);
        final long leftoverSize = leftover == null ? 0L : leftover.getStackSize();
        final PocketReceipt receipt = PocketReceipt.classify(true, writable, acceptable, requested, leftoverSize);
        final int moved = (int) Math.max(0L, requested - leftoverSize);
        if (moved > 0 && !shrinkSource(source.slot, slotStack, moved)) {
            // ★R85 小项 2：来源格因为槽号越界没扣成 ⇒ 这份<b>原路注回元件</b>（与流体支那条
            // "落点又变小了就原路注回"同一条纪律），并按 0 件成交。发 NO_ACCESS 而不是任何"成功"码：
            // 本次改写来源格被拒（越界），本批后面的来源同形必败 ⇒ 让它 stopsBatch 并计入 failures()，
            // 比"元件收了货、来源没扣件"的复制好一万倍。
            final IAEItemStack back = AEItemStack.create(copyOf(slotStack, moved));
            if (back != null) {
                handler.injectItems(back, Actionable.MODULATE, SOURCE);
            }
            return new Outcome(PocketReceipt.NO_ACCESS, 0);
        }
        return new Outcome(receipt, moved);
    }

    /**
     * ★流体支（注入 {@code injectFluidSource} / 抽取 {@code extractFluid} / 算术
     * {@code fluidRequestFor}、{@code fluidFallback}）已纯搬移到 {@code PocketFluidChannelOps}
     * （★R90 T3，行数纪律；方法体逐字保留，宿主引用改显式 {@code ops} 首参并复用本类的批级缓存/
     * 会话/动作源），本类只在 {@code inject}/{@code extract} 的分派口委派过去；
     * 来源快照的 appendFluidSources 仍在本类。
     */

    @Override
    public Outcome extract(PocketFilterConfig.Filter filter, String diskuuid, int count) {
        if (filter == null || count <= 0) {
            return new Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        if (filter instanceof PocketFilterConfig.ItemFilter) {
            return extractItem(filter, diskuuid, count);
        }
        if (filter instanceof PocketFilterConfig.FluidFilter) {
            // ★R90 T3：流体支纯搬移到 PocketFluidChannelOps（宿主引用改显式 ops 首参），分派口不变
            return PocketFluidChannelOps.extractFluid(this, (PocketFilterConfig.FluidFilter) filter, diskuuid, count);
        }
        // ★R87-A：源质支整体搬去了 {@link PocketEssenceChannelOps}（那一次是行数纪律的纯搬移）；
        // ★R88 起那边的载体与落点已改判（晶 → 瓶、物品槽 → 源质盘），委派口本身不变
        return PocketEssenceChannelOps.extractEssence(this, (PocketFilterConfig.EssenceFilter) filter, diskuuid, count);
    }

    /**
     * 物品支：从元件的 item 通道抽到<b>本条声明自己那一格</b>（★R84 用户裁定"物品应该落到物品格"，
     * 与流体支"落点＝本 tank"同构；旧实现落玩家背包空槽，导致需求格永远空着、玩家只看到一片虚化）。
     * 先问落点空间、后抽，落不下就不抽（与流体支同一纪律）。
     */
    private Outcome extractItem(PocketFilterConfig.Filter filter, String diskuuid, int count) {
        final ItemStack wanted = stackOfKey(filter.key(), count);
        if (wanted == null) {
            return new Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final Found found = foundOfCached(diskuuid);
        final IMEInventoryHandler handler = handlerOfCached(found, InfinityStackTypes.ITEM_STACK_TYPE, diskuuid);
        if (handler == null) {
            // ★R84：与注入侧同口径——"这只元件没有物品通道"不是"元件失联"（失联由 isCellLost 判），
            // 且 LOST.stopsBatch() 会把该元件整串声明 break 掉并谎报成"未识别到元件"。
            return new Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final IAEItemStack request = AEItemStack.create(wanted);
        if (request == null) {
            return new Outcome(PocketReceipt.LOST, 0);
        }
        request.setStackSize(count);
        rememberPrototype(filter.key(), request);
        // 抽取不受分区限制（MEInventoryHandler:106-118 只查读权限，GTIT 从不 setIsExtractFilterActive）
        final IAEItemStack simulated = (IAEItemStack) handler.extractItems(request, Actionable.SIMULATE, SOURCE);
        if (simulated == null || simulated.getStackSize() <= 0L) {
            // 元件里没有该物品：等同"无事可做"，让调用方继续下一条声明
            return new Outcome(PocketReceipt.OK, 0);
        }
        final int target = filter.slotIndex();
        // ★小项 1（R85）：这里<b>只</b>判越界，不再判 "session == null"。
        // 该半条在生产里<b>永不成立</b>（取证档案 r85-ret-robust §3-①）：全仓只有两条造 ops 的路径，
        // ①{@code NekoPocketPanel#performChannelRequest}（传 this，恒非 null）、
        // ②{@code PocketChannelDriver#onItemTick}（先有 "session == null 或承载栈不符 ⇒ 本拍直接 return"
        // 那道守卫，之后才 new 出带会话的 ops）⇒ <b>会话为空根本走不到这个方法</b>。
        // ★摘掉它不会把"空会话"变成 NPE：driver 在构造 ops 之前已经先解引用过同一个 session
        // （那道守卫本身），所以"会话丢了"这一件事在到达本行之前就已经以响亮的方式暴露了；
        // 反过来留一条永不成立的分支，等于给"通道坏了"这个读数多挂一个假来源。
        // 越界那一半<b>是真判据</b>（旧档/外来档的 slotIndex 可以越出当前形状），保留。
        if (target < 0 || target >= session.storageSlots()) {
            // 落点格在当前形状里不存在 ⇒ 一格都不抽（旧实现落玩家背包故不需要会话）
            return new Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final ItemStack held = session.storageStackAt(target);
        final int max = wanted.getMaxStackSize();
        // ★R85 P1：批级缓存版（同批同格重复读不再重算 base64；内容被换掉 ⇒ 对象引用变了 ⇒ 必然重算）
        final int stored = held != null && batchContentKey(held).equals(filter.key()) ? held.stackSize : 0;
        final int room = max - stored;
        if (held != null && stored <= 0) {
            // 声明格里杵着别的东西：错内容走 applyItemGhosts 的服务端那一支搬空，本拍绝不与它抢
            return new Outcome(PocketReceipt.TARGET_FULL, 0);
        }
        if (room <= 0) {
            // ★R85 A1：这一格已经补到堆叠上限 ⇒ 对<b>这条声明</b>而言"无事可做"，必须发 OK——
            // 发 TARGET_FULL 在 R84 之前的 stopsBatch 语义下会牵连同批其余声明（每条声明有自己的落点格，
            // 补满本是稳态，队首一条就能把后面全部永久饿死）。
            return new Outcome(PocketReceipt.OK, 0);
        }
        // ★每条声明的组上限全仓只在这一处被消费；未设时 resolveCap 回落 = maxStackSize ⇒ 旧档逐字不改行为
        final int wantedSize = (int) Math
            .min(Math.min(simulated.getStackSize(), (long) room), PocketFilterConfig.resolveCap(filter, max));
        request.setStackSize(wantedSize);
        final IAEItemStack taken = (IAEItemStack) handler.extractItems(request, Actionable.MODULATE, SOURCE);
        if (taken == null || taken.getStackSize() <= 0L) {
            return new Outcome(PocketReceipt.OK, 0);
        }
        final ItemStack out = taken.getItemStack();
        if (out == null || out.stackSize <= 0) {
            return new Outcome(PocketReceipt.OK, 0);
        }
        final ItemStack merged = held == null ? out : mergeInto(held, out);
        session.setStorageStackAt(target, merged);
        return new Outcome(PocketReceipt.OK, out.stackSize);
    }

    /** 把刚抽出的一份并进声明格里已有的同种堆（★只在调用方已按 room 钳过量之后使用）。 */
    private static ItemStack mergeInto(ItemStack held, ItemStack added) {
        final ItemStack merged = held.copy();
        merged.stackSize = Math.min(merged.getMaxStackSize(), merged.stackSize + added.stackSize);
        return merged;
    }

    /**
     * ★源质支已整体搬去 {@link PocketEssenceChannelOps}（★R87-A 那次是行数纪律的纯搬移；★R88 换载体后
     * 那边的逻辑按瓶改判过，别把纯搬移读成至今未改；★R90 E2 又把<b>物品通道兜底删净</b>——
     * {@code essenceStackFor} 不再对 {@code ITEM_STACK_TYPE} 特判，通道侧的满瓶容器只剩只读探针/读回）：
     * 抽取（extractEssence）/ 注入（injectEssenceSource）/ 换算（essenceStackFor、carriersFromUnits、
     * unitsForCarriers）与不可物化日志都在那边；原生通道判定单源在 {@link EssenceNativeChannels}；
     * 本类经 {@code inject}/{@code extract} 的分派口委派过去。
     */

    /**
     * 中栏来源格扣件（★R84：来源在中栏，不再是玩家背包）。写回走 {@code PocketSession#setStorageStackAt}
     * 那条不打扰 ghost 判据的程序化面，并由会话自己打脏；这里刻意改副本而不动传入栈，避免与 handler
     * 内那份别名互踩。
     * <p>
     * ★<b>R85 小项 2（取证档案 N7）：越界槽号必须显式判，不许静默丢件。</b>
     * {@code setStorageStackAt} 的形状是"复制一份再写"，而它对越界槽号的行为是<b>静默 return</b>
     * （连 dirty 都不打）⇒ 一旦 {@code slot} 与 {@code session.storageSlots()} 不匹配（读档收缩过的旧档、
     * 或将来把来源面换成别的 handler），本方法的调用链就变成"<b>元件已收货、来源格未扣件</b>"，
     * 那是<b>复制物品</b>而不是"UI 不动"。现在：越界 ⇒ 一条 WARN + 按 0 件成交返回 false，
     * 由调用方（{@link #inject}）把这一笔退回元件侧。
     * <p>
     * ★节流口径照 {@code PocketFilterConfig#readFrom} 那条（<b>按区</b>一次，不是每拍打）：越界是
     * 形状级问题，一旦成立就是每一批每一条都成立，每拍一行会把日志淹掉。
     *
     * @return true = 来源格确实扣到了件；false = 槽号越界（调用方必须把这份退回元件，不许白拿）
     */
    private boolean shrinkSource(int slot, ItemStack slotStack, int moved) {
        if (slot < 0 || slot >= session.storageSlots()) {
            warnShrinkOutOfRangeOnce(slot, session.storageSlots(), moved);
            return false;
        }
        final int left = slotStack.stackSize - moved;
        if (left <= 0) {
            session.setStorageStackAt(slot, null);
            return true;
        }
        final ItemStack rest = slotStack.copy();
        rest.stackSize = left;
        session.setStorageStackAt(slot, rest);
        return true;
    }

    /** {@link #shrinkSource} 越界 WARN 的一次性闩（键 = 形状级，不按玩家分，理由见那里的★段）。 */
    private static final Set<String> SHRINK_OUT_OF_RANGE_WARNED = new LinkedHashSet<>();

    private static void warnShrinkOutOfRangeOnce(int slot, int slots, int dropped) {
        if (!SHRINK_OUT_OF_RANGE_WARNED.add("storage")) {
            return;
        }
        LOG.warn(
            "[pocket] 通道注入要扣的来源格 {} 越出中栏形状（现有 {} 格），本次按 0 件成交并已把 {} 件退回元件" + "（宁可不搬，也不出现『元件收了货、来源没扣件』的复制；本条只报一次）",
            slot,
            slots,
            dropped);
    }

    // ------------------------------------------------------------------ 解析与通知

    /** 元件栈 + 宿主（R6：宿主必须是 {@code IChestOrDrive}；★R87-A 起包内可见，供源质支透传）。 */
    static final class Found {

        final ItemStack cellStack;
        /** 同时是 {@code ISaveProvider}（写入通路要它，R7）与 {@code IActionHost}（取 grid 通知网络，R7）。 */
        final IChestOrDrive host;

        Found(ItemStack cellStack, IChestOrDrive host) {
            this.cellStack = cellStack;
            this.host = host;
        }
    }

    private Found foundOf(String diskuuid) {
        final PocketCellProbe.Found probed = probe.foundAt(diskuuid);
        if (probed == null || !(probed.tile instanceof IChestOrDrive device)) {
            // 只有驱动器/ME 箱能给"已识别 + 有电"的可信结论（R6 的 getCellArray 门禁挂在它身上）
            return null;
        }
        // IChestOrDrive extends ICellContainer extends IActionHost, ICellProvider, ISaveProvider
        return new Found(probed.stack, device);
    }

    /**
     * ★R87-c：直问元件要 handler —— <b>不再</b>要求 {@code getCellArray(type)} 非空。
     * <p>
     * 旧前置门的漏洞：AE2 宿主对多通道元件只把<b>第一个命中</b>的通道注册进 cellsMap
     * （AE2-ref {@code TileDrive.java:296-331} / {@code TileChest.java:216-229} 的 {@code break}；
     * 遍历容器是 {@code HashMap}，序不可控）⇒ 流体/第三方通道被误判"这只元件没有该通道"，
     * {@code channelIdsOf} 里根本没有它 ⇒ 轮转/全通道遍历都到不了 ⇒ 流体与源质推送静默哑火。
     * {@code getCellInventory(stack, host, type)} 对多通道件的<b>每条</b>通道都能给出 handler，
     * 直问即修。R6 的"已识别 + 宿主在位"门禁保持在 {@link #foundOf}（probe 解析 + instanceof 门）。
     */
    private IMEInventoryHandler handlerOf(Found found, IAEStackType<?> type) {
        if (found == null || type == null) {
            return null;
        }
        try {
            // host 传 tile 本体（IChestOrDrive 继承链上就是 ISaveProvider），绝不传 null（R7）
            return AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(found.cellStack, found.host, type);
        } catch (Throwable t) {
            LOG.warn("[Pocket] 取元件 handler 失败（通道 {}）", type.getId(), t);
            return null;
        }
    }

    /** ★R87-A 起包内可见：源质支的注入/抽取也要登记 delta 原型（同一 {@code prototypes} 表）。 */
    void rememberPrototype(String contentKey, IAEStack<?> prototype) {
        if (contentKey == null || prototype == null) {
            return;
        }
        prototypes.put(contentKey, prototype.copy());
    }

    /**
     * 批尾一次通知（R7）：按 (元件, 通道) 分组，每组一次 typed post；
     * 任一组拿不到原型栈、或 typed post 抛异常（实验 E1：{@code GridStorageCache:319} 的
     * {@code monitors.get(type)} 无判空）即退到 {@code MENetworkCellArrayUpdate} 全表重建。
     */
    @Override
    public void announce(List<Delta> deltas) {
        // ★R85 P1：批尾统一失效本批的派生缓存（元件解析 / handler / contentKey）。
        // 这一行是档案给的口径；<b>但它不是唯一失效点</b> —— {@code PocketChannelRunner#flush} 只在
        // deltas 非空时才调 announce，所以"零搬运的一批"根本走不到这里 ⇒ 另有
        // {@code snapshotSources} 头部与 tick 变化两道兜底（见字段区那段★失效点注释）。
        try {
            if (deltas == null || deltas.isEmpty()) {
                return;
            }
            final Map<String, List<Delta>> grouped = new LinkedHashMap<>();
            for (Delta delta : deltas) {
                grouped.computeIfAbsent(delta.diskuuid + '#' + delta.typeId, k -> new ArrayList<Delta>())
                    .add(delta);
            }
            for (Map.Entry<String, List<Delta>> entry : grouped.entrySet()) {
                final List<Delta> group = entry.getValue();
                announceGroup(group.get(0).diskuuid, group.get(0).typeId, group);
            }
        } finally {
            prototypes.clear();
            invalidateBatchCache();
        }
    }

    private void announceGroup(String diskuuid, String typeId, List<Delta> group) {
        final IAEStackType<?> type = InfinityStackTypes.byId(typeId);
        // ★R85 P1：批尾通知走的也是同一枚元件 ⇒ 复用本批解析结果（缓存的失效在 announce 的 finally 里，
        // 排在本次调用<b>之后</b>，所以这里命中是安全的）
        final Found found = foundOfCached(diskuuid);
        final IGrid grid = gridOf(found);
        if (grid == null) {
            return;
        }
        final List<IAEStack<?>> signed = new ArrayList<>(group.size());
        for (Delta delta : group) {
            final IAEStack<?> prototype = prototypes.get(delta.contentKey);
            if (prototype == null) {
                postCellArrayUpdate(grid);
                return;
            }
            signed.add(
                prototype.copy()
                    .setStackSize(delta.amount));
        }
        final IStorageGrid storage = grid.getCache(IStorageGrid.class);
        if (storage == null) {
            postCellArrayUpdate(grid);
            return;
        }
        try {
            storage.postAlterationOfStoredItems(type, signed, SOURCE);
            if (!loggedTypedPost) {
                loggedTypedPost = true;
                LOG.info("[gtit] 口袋通道 typed postAlterationOfStoredItems 已执行（通道 id={}）", typeId);
            }
        } catch (Throwable t) {
            // 实验 E1 的兜底路径：typed 覆盖点内部无判空时整体退回全表重建
            LOG.warn("[gtit] 口袋通道 typed 通知失败，退回 MENetworkCellArrayUpdate", t);
            postCellArrayUpdate(grid);
        }
    }

    private void postCellArrayUpdate(IGrid grid) {
        try {
            grid.postEvent(new MENetworkCellArrayUpdate());
        } catch (Throwable t) {
            LOG.warn("[gtit] 口袋通道全表重建通知失败（本轮终端显示会陈旧，数据本身不受影响）", t);
        }
    }

    /** {@code IChestOrDrive → ICellContainer extends IActionHost → getActionableNode() → getGrid()}。 */
    private IGrid gridOf(Found found) {
        if (found == null) {
            return null;
        }
        try {
            final IGridNode node = found.host.getActionableNode();
            return node == null ? null : node.getGrid();
        } catch (Throwable t) {
            return null;
        }
    }

    private static ItemStack copyOf(ItemStack stack, int size) {
        final ItemStack copy = stack.copy();
        copy.stackSize = size;
        return copy;
    }
}
