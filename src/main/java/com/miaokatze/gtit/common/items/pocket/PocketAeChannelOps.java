package com.miaokatze.gtit.common.items.pocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;
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
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.util.item.AEFluidStack;
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
 * <li><b>识别口径（R6）</b>：统一用 {@code getCellArray(type)} 非空（其内部即 {@code isActive()}），
 * 不用 {@code isPowered()} 做门禁（ME 箱允许本地电池态，口径与驱动器分歧）；
 * 也绝不缓存"已识别"结论——{@code TileDrive.updateState} 被 {@code isCached} 闩住，
 * 只有 {@code isActive()} 翻转才重算。</li>
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
 * ⇒ 需求 2「要素栏取出→晶化源质」与需求 4「按配置补满流体」两条并列要求整体静默失效）：
 * <ul>
 * <li>物品支 → 玩家背包空槽（沿用 S1 实现，未改语义）；</li>
 * <li>流体支 → 口袋流体条（{@link PocketSession}），先问落点空间再抽，抽了放不下就原路注回；</li>
 * <li>源质支 → 经 {@code IAEStackType.convertStackFromItem} 以 {@code ItemCrystalEssence} 为探针
 * 反算数额后<b>物化成晶化源质</b>进口袋真实栏（R15/R31：1 点 = 1 晶；不猜第三方 mod 的私有栈格式）。</li>
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

    /** 玩家背包是注入来源（R12 裁定：读法 B = {@code mainInventory}，含快捷栏 0-8）。 */
    private final EntityPlayer player;
    /** 口袋本体，扫描来源时按身份排除，避免"口袋自己吸自己"（R12 点名的自吸风险）。 */
    private final ItemStack pocket;
    private final PocketCellProbe probe;
    /**
     * 拉取方向的落点（流体条与口袋真实栏都在活会话里）。
     * <p>
     * 可为 {@code null}：那时流体支与源质支按"没有落点"处理（{@code TARGET_FULL}/{@code NO_CHANNEL}，
     * <b>一件都不抽</b>），只有物品支仍能用玩家背包。会话缺失是关屏+通道停之后的正常态，
     * 不是错误，因此不建条目也不报错——静默但语义明确（R45b 禁止的是"返回 OK 却什么都不搬"）。
     */
    private final PocketSession session;

    /** 实验 E3 的降级日志去重集（"通道 × aspect tag"一条一行）。 */
    private static final Set<String> LOG_UNMATERIALIZED = new LinkedHashSet<>();

    /** contentKey → 本轮见过的 AE 栈原型，用于把带符号 delta 还原成 {@code IAEStack} 列表。 */
    private final Map<String, IAEStack<?>> prototypes = new HashMap<>();
    /** 实验 E1 的一次性取证日志开关：typed post 是否真被接住。 */
    private static boolean loggedTypedPost;

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
        final Found found = foundOf(diskuuid);
        if (found == null) {
            return ids;
        }
        // 遍历序即 allSupportedTypes() 的序（物品→流体→注册中的第三方），但持久化/轮转一律只用字符串 id
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
        if (player == null || player.inventory == null) {
            return slots;
        }
        final ItemStack[] main = player.inventory.mainInventory;
        for (int i = 0; i < main.length; i++) {
            final ItemStack stack = main[i];
            if (stack == null || stack.stackSize <= 0) {
                continue;
            }
            if (pocket != null && stack == pocket) {
                // 排除手持口袋所在槽：否则通道会把口袋吸进自己绑定的元件
                continue;
            }
            slots.add(new SourceSlot(i, contentKey(stack), stack.stackSize));
        }
        return slots;
    }

    /** 物品内容标识：{@code itemId + meta + nbtString}（R17，流体/源质键由配置侧同族给出）。 */
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
        final ItemStack slotStack = source == null ? null : player.inventory.getStackInSlot(source.slot);
        if (slotStack == null || slotStack.stackSize <= 0 || !contentKey(slotStack).equals(source.contentKey)) {
            // 槽位已被玩家换掉或掏空：本条跳过，不改写任何状态
            return new Outcome(PocketReceipt.OK, 0);
        }
        final int requested = Math.min(slotStack.stackSize, slotStack.getMaxStackSize());
        final IAEStackType<?> type = InfinityStackTypes.byId(typeId);
        final Found found = foundOf(diskuuid);
        final IMEInventoryHandler handler = found == null || type == null ? null : handlerOf(found, type);
        if (handler == null) {
            return new Outcome(PocketReceipt.LOST, 0);
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
        if (moved > 0) {
            shrinkSource(source.slot, slotStack, moved);
        }
        return new Outcome(receipt, moved);
    }

    @Override
    public Outcome extract(PocketFilterConfig.Filter filter, String diskuuid, int count) {
        if (filter == null || count <= 0) {
            return new Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        if (filter instanceof PocketFilterConfig.ItemFilter) {
            return extractItem(filter, diskuuid, count);
        }
        if (filter instanceof PocketFilterConfig.FluidFilter) {
            return extractFluid((PocketFilterConfig.FluidFilter) filter, diskuuid, count);
        }
        return extractEssence((PocketFilterConfig.EssenceFilter) filter, diskuuid, count);
    }

    /**
     * 物品支：从元件的 item 通道抽到玩家背包（R38 第 2 条的"补满"里唯一不进口袋中栏的一支——
     * 背包是推送源的同一空间，玩家一眼看得见，且不需要会话存活）。
     */
    private Outcome extractItem(PocketFilterConfig.Filter filter, String diskuuid, int count) {
        final ItemStack wanted = stackOfKey(filter.key(), count);
        if (wanted == null) {
            return new Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final Found found = foundOf(diskuuid);
        final IMEInventoryHandler handler = handlerOf(found, InfinityStackTypes.ITEM_STACK_TYPE);
        if (handler == null) {
            return new Outcome(PocketReceipt.LOST, 0);
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
        final int target = firstFreeSourceSlot();
        if (target < 0) {
            return new Outcome(PocketReceipt.TARGET_FULL, 0);
        }
        final int wantedSize = (int) Math.min(simulated.getStackSize(), wanted.getMaxStackSize());
        request.setStackSize(wantedSize);
        final IAEItemStack taken = (IAEItemStack) handler.extractItems(request, Actionable.MODULATE, SOURCE);
        if (taken == null || taken.getStackSize() <= 0L) {
            return new Outcome(PocketReceipt.OK, 0);
        }
        final ItemStack out = taken.getItemStack();
        if (out == null || out.stackSize <= 0) {
            return new Outcome(PocketReceipt.OK, 0);
        }
        player.inventory.setInventorySlotContents(target, out);
        // 背包内容变化后同步一次，避免客户端仍显示旧栈（实验 E2 的时点之一）
        player.inventory.markDirty();
        return new Outcome(PocketReceipt.OK, out.stackSize);
    }

    /**
     * ★流体支（R45b 缺口之一，需求 4「按配置补满流体」的唯一通路）。
     * <p>
     * 三步固定顺序，<b>顺序本身就是不丢件的保证</b>：
     * <ol>
     * <li>先问落点还能收多少（{@link PocketSession#fluidBarRoom(int, FluidStack)}）——
     * 先抽后放会把超出部分凭空抹掉，AE2 侧已经扣了；</li>
     * <li>{@code SIMULATE} 出这一份，把请求量钳到"落点空间"与"元件可得"的较小值；</li>
     * <li>{@code MODULATE} 抽出来 → 灌进流体槽；<b>灌不进的部分立刻原路注回元件</b>
     * （同一 handler、同一 SOURCE，语义上就是"这一拍没发生过"）。落点完全不可用（会话已丢）
     * ⇒ 根本不抽，直接 {@code TARGET_FULL}。</li>
     * </ol>
     */
    private Outcome extractFluid(PocketFilterConfig.FluidFilter filter, String diskuuid, int count) {
        final Fluid fluid = FluidRegistry.getFluid(filter.fluidName);
        if (fluid == null) {
            // 声明里的流体已从注册表消失（整合包变更）：按"无事可做"继续下一条，不判失联
            return new Outcome(PocketReceipt.OK, 0);
        }
        final Found found = foundOf(diskuuid);
        final IMEInventoryHandler handler = handlerOf(found, InfinityStackTypes.FLUID_STACK_TYPE);
        if (handler == null) {
            return new Outcome(PocketReceipt.LOST, 0);
        }
        // ★落点 = 本条声明自己的那一列（R75①：ghost 的 slotIndex 就是 tank 号，六列各拉各的）
        final int tank = filter.slotIndex();
        final int room = session == null ? 0 : session.fluidBarRoom(tank, new FluidStack(fluid, 1));
        if (room <= 0) {
            return new Outcome(session == null ? PocketReceipt.NO_CHANNEL : PocketReceipt.TARGET_FULL, 0);
        }
        final int request = fluidRequestFor(room, count);
        final IAEFluidStack probe = AEFluidStack.create(new FluidStack(fluid, request));
        if (probe == null) {
            return new Outcome(PocketReceipt.LOST, 0);
        }
        rememberPrototype(filter.key(), probe);
        final IAEFluidStack simulated = (IAEFluidStack) handler.extractItems(probe, Actionable.SIMULATE, SOURCE);
        final long available = simulated == null ? 0L : simulated.getStackSize();
        if (available <= 0L) {
            return new Outcome(PocketReceipt.OK, 0);
        }
        final int want = fluidRequestFor((int) available, room);
        probe.setStackSize(want);
        final IAEFluidStack taken = (IAEFluidStack) handler.extractItems(probe, Actionable.MODULATE, SOURCE);
        final long takenAmount = taken == null ? 0L : taken.getStackSize();
        if (takenAmount <= 0L) {
            return new Outcome(PocketReceipt.OK, 0);
        }
        final int moved = session.depositFluid(tank, new FluidStack(fluid, (int) takenAmount));
        final int fallback = fluidFallback(takenAmount, moved);
        if (fallback > 0) {
            // 落点在抽取瞬间又变小了：把差额原路注回，绝不让流体凭空消失
            handler.injectItems(AEFluidStack.create(new FluidStack(fluid, fallback)), Actionable.MODULATE, SOURCE);
        }
        return new Outcome(moved >= takenAmount ? PocketReceipt.OK : PocketReceipt.PARTIAL, moved);
    }

    /**
     * 流体支的请求量钳制：一次最多要"落点空间"与"本轮配额"的较小值。
     * <p>
     * 拉取模式的配额是 {@link PocketConstants#REFILL_AMOUNT_PER_FILTER_UNBOUNDED}（= 不设限），
     * 所以实际值恒等于落点空间 —— 这条算式是"先问落点再抽"的实现，写错一次就会超发；
     * 单独成函数是为了让零依赖套件能直接钉住它（AE2 handler 与 Forge 流体对象在纯 JVM 里都拿不到）。
     */
    static int fluidRequestFor(int room, int quota) {
        if (room <= 0 || quota <= 0) {
            return 0;
        }
        return Math.min(room, quota);
    }

    /**
     * 流体支的退回量：抽出来却没能落进条子的部分（必须原路注回元件）。
     * 负数与零一律返回 0（"全部落下"是正常路径，不该触发任何回滚）。
     */
    static int fluidFallback(long takenAmount, int moved) {
        if (takenAmount <= 0L) {
            return 0;
        }
        final long left = takenAmount - Math.max(0, moved);
        return left <= 0L ? 0 : (int) Math.min(left, Integer.MAX_VALUE);
    }

    /**
     * ★源质支（R45b 缺口之二，需求 4 的源质声明 + R15 的第三条路径之一）。
     * <p>
     * 元件侧的源质栈格式属对应 mod 私有（实验 E3 保留），因此<b>不猜格式</b>：
     * 用 {@code IAEStackType.convertStackFromItem}（AE2 自己给第三方通道留的"TC4 aspect item → 通道栈"
     * 换算口）以自家 {@code ItemCrystalEssence} 为探针反算数额，1 点 = 1 晶
     * （{@code TaumDistillRules.CRYSTAL_CAPACITY}）。换算拿不到 ⇒ 判"该通道不可物化"，
     * 一次性 INFO 后按 {@code NO_CHANNEL} 收口（R31/R44a：不 {@code instanceof} 任何具体物品类）。
     * <p>
     * 落点同样是"先问、后抽、抽了必须放得下、放不下就注回"（与流体支同一纪律）。
     */
    private Outcome extractEssence(PocketFilterConfig.EssenceFilter filter, String diskuuid, int count) {
        final IAEStackType<?> type = InfinityStackTypes.byId(filter.typeId);
        if (type == null) {
            return new Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final Found found = foundOf(diskuuid);
        final IMEInventoryHandler handler = handlerOf(found, type);
        if (handler == null) {
            return new Outcome(PocketReceipt.LOST, 0);
        }
        if (session == null) {
            // 源质支的落点是口袋真实栏（晶化源质是物品），没有活会话就没有落点 ⇒ 根本不抽
            return new Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        // 一次一条声明至多补满一整堆晶（= ESSENCE_CAP_PER_TAG 点），剩下的顺延下一拍
        final int wantPoints = Math.min(count, PocketConstants.ESSENCE_CAP_PER_TAG);
        final ItemStack single = TaumCompat.newCrystalStack(filter.tag, 1);
        final IAEStack<?> perPoint = single == null ? null : type.convertStackFromItem(single);
        if (perPoint == null || perPoint.getStackSize() <= 0L) {
            logUnmaterializableOnce(type.getId(), filter.tag);
            return new Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final long unit = perPoint.getStackSize();
        final ItemStack probeStack = TaumCompat.newCrystalStack(filter.tag, wantPoints);
        final IAEStack<?> probe = probeStack == null ? null : type.convertStackFromItem(probeStack);
        if (probe == null) {
            return new Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        rememberPrototype(filter.key(), probe);
        final IAEStack<?> simulated = handler.extractItems(probe, Actionable.SIMULATE, SOURCE);
        final long available = simulated == null ? 0L : simulated.getStackSize();
        final int points = crystalsFromUnits(available, unit, wantPoints);
        if (points <= 0) {
            // 元件里没有该 tag，或零头不足 1 晶：不动，避免"抽了通道单位却放不下整晶"
            return new Outcome(PocketReceipt.OK, 0);
        }
        probe.setStackSize(unitsForCrystals(points, unit));
        final IAEStack<?> taken = handler.extractItems(probe, Actionable.MODULATE, SOURCE);
        final int takenPoints = crystalsFromUnits(taken == null ? 0L : taken.getStackSize(), unit, points);
        if (takenPoints <= 0) {
            return new Outcome(PocketReceipt.OK, 0);
        }
        final ItemStack crystals = TaumCompat.newCrystalStack(filter.tag, takenPoints);
        final int moved = crystals == null ? 0 : session.depositItem(crystals.copy());
        if (moved < takenPoints) {
            final int back = takenPoints - Math.max(0, moved);
            final ItemStack backStack = TaumCompat.newCrystalStack(filter.tag, back);
            final IAEStack<?> backRequest = backStack == null ? null : type.convertStackFromItem(backStack);
            if (backRequest != null) {
                handler.injectItems(backRequest, Actionable.MODULATE, SOURCE);
            }
        }
        return new Outcome(moved >= takenPoints ? PocketReceipt.OK : PocketReceipt.PARTIAL, moved);
    }

    /**
     * 通道单位 → 可物化的晶化源质个数（<b>向下取整</b>：零头必须留在元件侧，
     * 否则"抽得出通道单位、放不下整晶"就成了凭空销毁价值）。
     * <p>
     * 单独成函数的理由：AE2 handler 在本仓的零依赖套件里拿不到（{@code PocketAeChannelOps} 整体属
     * 实机项），但这条换算本身是纯算术，也是源质支<b>唯一</b>会静默吞点数的地方，
     * 因此由 {@code extract_essence_branch_yields_crystal} 直接钉住。
     *
     * @param availableUnits 元件侧该 tag 现有的通道单位（SIMULATE 回报）
     * @param unitPerCrystal 一枚晶化源质对应多少通道单位（由 {@code convertStackFromItem} 实测）
     * @param wantCrystals   本轮最多要几晶（含 {@code ESSENCE_CAP_PER_TAG} 自缚）
     */
    static int crystalsFromUnits(long availableUnits, long unitPerCrystal, int wantCrystals) {
        if (availableUnits <= 0L || unitPerCrystal <= 0L || wantCrystals <= 0) {
            return 0;
        }
        final long whole = availableUnits / unitPerCrystal;
        return whole <= 0L ? 0 : (int) Math.min((long) wantCrystals, whole);
    }

    /** 晶数 → 通道单位（{@link #crystalsFromUnits} 的逆运算；非正数一律 0）。 */
    static long unitsForCrystals(int crystals, long unitPerCrystal) {
        return crystals <= 0 || unitPerCrystal <= 0L ? 0L : (long) crystals * unitPerCrystal;
    }

    /** 实验 E3：某第三方通道的栈无法物化成晶化源质，只报一次（每次抽取都刷一行会淹掉日志）。 */
    private static void logUnmaterializableOnce(String typeId, String tag) {
        if (LOG_UNMATERIALIZED.add(typeId + '#' + tag)) {
            LOG.info("[gtit] 口袋源质支：通道 {} 的条目（tag={}）无法物化为晶化源质，该声明按不可物化跳过", typeId, tag);
        }
    }

    /** 找一个可落地的背包空槽（只认完全空槽，不做堆叠合并，语义简单且不会拆错堆）。 */
    private int firstFreeSourceSlot() {
        if (player == null || player.inventory == null) {
            return -1;
        }
        final ItemStack[] main = player.inventory.mainInventory;
        for (int i = 0; i < main.length; i++) {
            if (main[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private void shrinkSource(int slot, ItemStack slotStack, int moved) {
        final int left = slotStack.stackSize - moved;
        if (left <= 0) {
            player.inventory.setInventorySlotContents(slot, null);
        } else {
            slotStack.stackSize = left;
            player.inventory.setInventorySlotContents(slot, slotStack);
        }
        player.inventory.markDirty();
    }

    // ------------------------------------------------------------------ 解析与通知

    /** 元件栈 + 宿主（R6：宿主必须是 {@code IChestOrDrive} 且该通道 {@code getCellArray} 非空）。 */
    private static final class Found {

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

    /** 每次现取 handler；同时承担 R6 的识别门禁。 */
    private IMEInventoryHandler handlerOf(Found found, IAEStackType<?> type) {
        if (found == null || type == null) {
            return null;
        }
        final List<IMEInventoryHandler> array = found.host.getCellArray(type);
        if (array == null || array.isEmpty()) {
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

    private void rememberPrototype(String contentKey, IAEStack<?> prototype) {
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
        prototypes.clear();
    }

    private void announceGroup(String diskuuid, String typeId, List<Delta> group) {
        final IAEStackType<?> type = InfinityStackTypes.byId(typeId);
        final Found found = foundOf(diskuuid);
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
