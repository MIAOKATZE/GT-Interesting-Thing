package com.miaokatze.gtit.common.items.pocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.items.infinitycell.IInfinityCellItem;
import com.miaokatze.gtit.common.items.infinitycell.InfinityCellConstants;
import com.miaokatze.gtit.common.items.infinitycell.StorageManager;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * 元件位置探针：<b>推送式观测</b>——元件被 AE2 的驱动器/ME 箱取用时，由自家
 * {@code ICellHandler.getCellInventory} 就地把它此刻所在的容器上报进来，本类只登记与复验。
 * <p>
 * <b>接线点</b>：{@code common/items/infinitycell/InfinityCellHandler} 的两个
 * {@code getCellInventory} 重载里各一行 {@code PocketCellProbe.INSTANCE.observe(is, container, …)}，
 * 与既有 {@code LegacyCellReminderScheduler.INSTANCE.observe} <b>并排</b>存在（后者是旧元件迁移提醒，
 * 语义不同，不得合并、不得删除）。AE2 每次刷新都会重报 ⇒ 本表<b>自愈</b>，不需要任何扫描。
 * <p>
 * <b>为什么不复用 {@code LegacyCellReminderScheduler}</b>：该类 {@code cellUuid():215-223} 显式
 * {@code item instanceof ItemNekoInfinityStorageUnit → return null}，且只认 {@code IInfinityCellItem}
 * 的旧两枚，对新单元 {@code locations} 表恒空；放宽它的过滤器会让「旧元件迁移提醒」开始播报新单元坐标，
 * 属行为回退。因此这里<b>另建</b>探针，只照抄其 {@code stillPresent():153-170} 的判定结构：
 * {@code chunkExists} 守卫 → {@code getTileEntity} → {@code instanceof IInventory} → 逐槽比身份。
 * <p>
 * <b>三态口径</b>：区块未加载 ⇒ {@link Presence#UNKNOWN}（"无法判定"，算仍在，不删绑定、不判丢失），
 * 只有确认容器换了 / 槽位空了 / 身份不符才是 {@link Presence#GONE}。元件被销毁不需要 GC：
 * 下一次点检不到，通道自然停止。
 * <p>
 * <b>世界触点收敛在 {@link HostResolver}</b>：生产实现走 {@code MinecraftServer} 查维 +
 * {@code chunkExists} + {@code getTileEntity}；纯 JVM 回归套件换一个实现即可端到端验证
 * 「宿主上报 → 定位成功 → 从宿主移除 → 判 GONE」这条链（否则本类的核心语义只能实机验证）。
 * 该缝只为测试而存在，{@link #reset()} 会把解析器复位回生产实现。
 */
public final class PocketCellProbe {

    public static final PocketCellProbe INSTANCE = new PocketCellProbe();

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 调用方拿不到槽号时的入参（驱动器/ME 箱场景）：由本探针在宿主 {@code IInventory} 里扫槽得出。 */
    public static final int SLOT_UNKNOWN = -1;

    /** 一枚元件当前所在的容器坐标与其槽号。 */
    public static final class Located {

        public final int dim;
        public final int x;
        public final int y;
        public final int z;
        /** 元件在宿主 {@code IInventory} 里的槽号；{@link #SLOT_UNKNOWN} 表示未知。 */
        public final int slot;

        public Located(int dim, int x, int y, int z, int slot) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.slot = slot;
        }

        public int dimension() {
            return dim;
        }

        @Override
        public String toString() {
            return "Located{" + dim + " @" + x + '/' + y + '/' + z + " slot=" + slot + '}';
        }
    }

    /**
     * 坐标 ↔ 容器对象的唯一外部触点（生产实现 = {@code MinecraftServer}；测试实现 = 内存假宿主）。
     * <p>
     * 三个方法都不许抛：探针的调用点挂在 AE2 刷新路径与通道点检路径上，任何异常外溢都会变成
     * "元件静默失效"。
     */
    public interface HostResolver {

        /**
         * 容器对象 → 世界坐标（槽号留 {@link #SLOT_UNKNOWN}，由调用方给定或 {@code observe} 扫槽补齐）。
         *
         * @return 坐标；{@code container} 为 null、不是 {@code TileEntity}、或当前是客户端侧时返回 null
         */
        Located locate(Object container);

        /** 该坐标所在区块是否已加载（未加载 ⇒ 结论是"无法判定"，绝不判元件已迁走）。 */
        boolean chunkLoaded(Located at);

        /**
         * 该坐标当前承载的容器对象（仅在 {@link #chunkLoaded(Located)} 为 true 时才会被问到）。
         *
         * @return 通常是 {@code TileEntity}（同时是 {@code IInventory}）；那里没有容器时返回 null
         */
        Object hostAt(Located at);
    }

    /** {@code diskuuid} → 最近一次被驱动器/ME 箱装载的位置。 */
    private final Map<String, Located> locations = new ConcurrentHashMap<>();

    private static final HostResolver MC_RESOLVER = new MinecraftHostResolver();

    private static volatile HostResolver resolver = MC_RESOLVER;

    private PocketCellProbe() {}

    /**
     * 元件被容器取用时就地登记位置。
     * <p>
     * 三条门禁——{@code container} 为 null 安全跳过、非 {@code TileEntity}（IO 端口、tooltip 路径）跳过、
     * 客户端侧跳过——都收敛在 {@link HostResolver#locate(Object)} 里；本方法只处理身份与槽号。
     *
     * @param slot 元件在容器 {@code IInventory} 里的槽号；传 {@link #SLOT_UNKNOWN} 时由本方法
     *             扫宿主槽位比对 {@code diskuuid} 得出（驱动器/ME 箱 ≤36 槽，成本可忽略）
     */
    public void observe(ItemStack cell, Object container, int slot) {
        try {
            final String uuid = cellUuid(cell);
            if (uuid == null) {
                return;
            }
            final Located base = resolver.locate(container);
            if (base == null) {
                return;
            }
            final int resolved = slot >= 0 ? slot : findSlot(container, uuid);
            locations.put(uuid, new Located(base.dim, base.x, base.y, base.z, resolved));
        } catch (Throwable t) {
            LOG.warn("[Pocket] 元件位置登记失败", t);
        }
    }

    /** 在宿主的 {@code IInventory} 里找出装着该身份的槽号；找不到返回 {@link #SLOT_UNKNOWN}。 */
    private static int findSlot(Object container, String uuid) {
        if (!(container instanceof IInventory inventory)) {
            return SLOT_UNKNOWN;
        }
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            if (uuid.equals(cellUuid(inventory.getStackInSlot(slot)))) {
                return slot;
            }
        }
        return SLOT_UNKNOWN;
    }

    /** 元件当前所在容器坐标（未知即 null）。 */
    public Located locationOf(String diskuuid) {
        return diskuuid == null ? null : locations.get(diskuuid);
    }

    public boolean isTracked(String diskuuid) {
        return locationOf(diskuuid) != null;
    }

    /** 解绑时清掉坐标记录。 */
    public void forget(String diskuuid) {
        if (diskuuid != null) {
            locations.remove(diskuuid);
        }
    }

    /** 停服/换档复位，防跨存档残留坐标；同时把 {@link HostResolver} 复位回生产实现。 */
    public void reset() {
        locations.clear();
        resolver = MC_RESOLVER;
    }

    public int trackedCells() {
        return locations.size();
    }

    /**
     * 换掉世界触点（仅测试用；传 null 复位回生产实现）。
     * <p>
     * 之所以需要它：本类的核心语义（登记 → 复验 → 转 GONE）全靠 {@code World}/{@code TileEntity}，
     * 纯 JVM 里拿不到真世界，没有这条缝就只能"实机才能验"，而这条链一旦不验就是
     * "注册表恒空 ⇒ 通道恒不可开且无报错"的静默死路。
     */
    static void useHostResolverForTest(HostResolver next) {
        resolver = next == null ? MC_RESOLVER : next;
    }

    /**
     * 二次校验：坐标处仍是容器、且槽内仍是同一 {@code diskuuid} 的元件
     * （防区块卸载/元件已迁走后的陈旧记录）。判定结构逐段照
     * {@code LegacyCellReminderScheduler.stillPresent():153-170}。
     * <p>
     * 三态收敛成两态：{@link Presence#UNKNOWN}（区块未加载、槽号未知）算 <b>true</b>，
     * 即"无法判定就不删绑定"；只有 {@link Presence#GONE} 才是 false。
     */
    public boolean stillPresent(String diskuuid) {
        return probe(diskuuid).presence != Presence.GONE;
    }

    /**
     * 取该元件当前真实所在的 {@link ItemStack}；拿不到（含"无法判定"）返回 null。
     * <p>
     * 与 {@link #stillPresent(String)} 的分工：写入通路拿不到栈只能放弃本轮，
     * 但放弃本轮不等于元件没了。
     */
    public ItemStack cellAt(String diskuuid) {
        return probe(diskuuid).stack;
    }

    /** 元件栈本体 + 宿主 tile（写入通路要把 tile 当 {@code ISaveProvider} 传进去）。 */
    public Found foundAt(String diskuuid) {
        final ProbeResult result = probe(diskuuid);
        return result.presence == Presence.PRESENT ? new Found(result.stack, result.tile) : null;
    }

    /** 元件当前是否可被写入通路解析到（有栈可拿）。 */
    public boolean isResolvable(String diskuuid) {
        return probe(diskuuid).presence == Presence.PRESENT;
    }

    /** 探针的三态结论 + 命中时的真实栈。 */
    private static final class ProbeResult {

        final Presence presence;
        final ItemStack stack;
        /** 命中时的宿主 tile，供写入通路作为 {@code ISaveProvider} 传入；非 tile 宿主为 null。 */
        final TileEntity tile;

        ProbeResult(Presence presence, ItemStack stack, TileEntity tile) {
            this.presence = presence;
            this.stack = stack;
            this.tile = tile;
        }

        static final ProbeResult GONE = new ProbeResult(Presence.GONE, null, null);
        static final ProbeResult UNKNOWN = new ProbeResult(Presence.UNKNOWN, null, null);
    }

    /** {@link #foundAt(String)} 的公开结果：元件栈本体 + 宿主 tile。 */
    public static final class Found {

        public final ItemStack stack;
        public final TileEntity tile;

        Found(ItemStack stack, TileEntity tile) {
            this.stack = stack;
            this.tile = tile;
        }
    }

    private enum Presence {
        /** 槽内仍是同一身份且可判定的元件。 */
        PRESENT,
        /** 无法判定：坐标未登记、区块未加载、槽号未知、世界拿不到。 */
        UNKNOWN,
        /** 确认不在原处：容器换了、槽位空了、身份不符。 */
        GONE
    }

    private ProbeResult probe(String diskuuid) {
        try {
            final Located located = locationOf(diskuuid);
            if (located == null || located.slot < 0) {
                return ProbeResult.UNKNOWN;
            }
            final HostResolver active = resolver;
            // 1.7.10 的 getTileEntity 会 getChunkFromChunkCoords 强制载块，所以先查 chunkExists：
            // 未加载就当"无法判定"留着，既不为验证而拖服务器加载远端区块，
            // 也不把仅因未加载而查不到的元件误判为已迁走
            if (!active.chunkLoaded(located)) {
                return ProbeResult.UNKNOWN;
            }
            final Object host = active.hostAt(located);
            if (!(host instanceof IInventory inventory)) {
                return ProbeResult.GONE;
            }
            if (located.slot >= inventory.getSizeInventory()) {
                return ProbeResult.GONE;
            }
            final ItemStack found = inventory.getStackInSlot(located.slot);
            if (!diskuuid.equals(cellUuid(found))) {
                return ProbeResult.GONE;
            }
            return new ProbeResult(Presence.PRESENT, found, host instanceof TileEntity te ? te : null);
        } catch (Throwable t) {
            return ProbeResult.GONE;
        }
    }

    /**
     * 元件身份读取：任何 {@link IInfinityCellItem}（含新单元）的 {@code diskuuid}。
     * <p>
     * 这里<b>必须</b>与旧提醒类的过滤器不同——后者故意排除新单元，本探针恰恰要认新单元。
     */
    public static String cellUuid(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof IInfinityCellItem)) {
            return null;
        }
        final NBTTagCompound data = stack.stackTagCompound;
        if (data == null) {
            return null;
        }
        final String uuid = data.getString(InfinityCellConstants.DISKUUID);
        return uuid == null || uuid.isEmpty() ? null : uuid;
    }

    /**
     * 格内的栈<b>是不是一枚元件</b>（★R81① 四态里的第一判）。
     * <p>
     * 修复前没有这个方法：{@link #cellUuid} 返回 null 同时代表"空格子/非元件"和"元件还没拿到身份"
     * 两件事，于是两支共用一条 {@code bind.slot_hint}（「把元件放入此格，再按绑定键」）——
     * 后者格子里明明躺着元件，玩家照字面做就永远绑不上，观感即"只能绑一个"。
     */
    public static boolean isCell(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IInfinityCellItem;
    }

    /**
     * 绑定入口用的<b>身份读写面</b>（★R81②，{@link PocketBindFlow.Identity} 的生产实现）。
     * <p>
     * <b>读</b>＝{@link #cellUuid}（两端都可以读，纯读 NBT）。<b>物化</b>＝调一次 AE2 侧<b>现成的
     * 公开 API</b> {@code StorageManager.getStorage(ItemStack)}，只为它的<b>副作用</b>：那条链是
     * {@code getStorage(item) → getStorage(item, type) → allocateOrReadUuid(Platform.openNbtData(item),
     * Platform.isServer())}（{@code StorageManager.java:89-95,109-118}），服务端那一支会把
     * {@code diskuuid} 就地写进元件 NBT。本仓<b>不</b>另写分配逻辑，也<b>不</b>把 package-private 的
     * {@code allocateOrReadUuid} 抬成 public（{@code common/items/infinitycell/**} 是禁改面）。
     * <p>
     * ★★客户端<b>绝不</b>物化，两道闸：
     * <ol>
     * <li>{@link PocketBindFlow#identityOrMaterialize} 只在 {@code isServer} 为真时才调
     * {@code materialize()}（这条是 JVM 可断言的）；</li>
     * <li>本实现自己再判一次 {@code server}——{@code StorageManager.getInstance()} 在客户端
     * 未初始化（{@code CommonProxy:656-661} 只在服务端 {@code WorldSavedData} 装载时赋值），
     * 取到 null 就直接返回；即使将来有人在客户端把这个面传进去，NBT 也不会被写。</li>
     * </ol>
     * 客户端写物品 NBT 永不到达服务端（本仓已证死），且 {@code StorageManager} 的注释点名要防
     * "客户端每次悬停泄漏一个随机 UUID 条目"，故这一跳<b>只能</b>挂在服务端。
     *
     * @param server 当前是否服务端（生产调用点由 {@code !syncManager.isClient()} 给）
     */
    public static PocketBindFlow.Identity bindIdentity(final ItemStack stack, final boolean server) {
        return new PocketBindFlow.Identity() {

            @Override
            public String read() {
                return cellUuid(stack);
            }

            @Override
            public void materialize() {
                if (!server || !isCell(stack)) {
                    // ★客户端 / 非元件：一个字节都不写
                    return;
                }
                final StorageManager manager = StorageManager.getInstance();
                if (manager == null) {
                    // serverStarted 之前（或极端时序）没有 StorageManager 实例：身份读不到，
                    // 由调用方发 bind.no_identity —— 不再退回旧的误导文案
                    return;
                }
                try {
                    // 只求副作用：把 diskuuid 写进 stack；返回值（那个临时桶）故意丢掉
                    manager.getStorage(stack);
                } catch (Throwable t) {
                    // 与 InfinityCellHandler:47-51 同一口径：元件侧异常必须留痕，否则又变成"静默绑不上"
                    LOG.warn("[pocket] 绑定前物化元件身份失败（服务端），本次绑定按无身份处理", t);
                }
            }
        };
    }

    /** 生产实现：坐标 ↔ 真实世界的服务端维度。 */
    private static final class MinecraftHostResolver implements HostResolver {

        @Override
        public Located locate(Object container) {
            if (!(container instanceof TileEntity te)) {
                return null;
            }
            if (FMLCommonHandler.instance()
                .getEffectiveSide()
                .isClient()) {
                return null;
            }
            final World world = te.getWorldObj();
            if (world == null || world.provider == null) {
                return null;
            }
            return new Located(world.provider.dimensionId, te.xCoord, te.yCoord, te.zCoord, SLOT_UNKNOWN);
        }

        @Override
        public boolean chunkLoaded(Located at) {
            final World world = worldOf(at.dim);
            return world != null && world.getChunkProvider()
                .chunkExists(at.x >> 4, at.z >> 4);
        }

        @Override
        public Object hostAt(Located at) {
            final World world = worldOf(at.dim);
            return world == null ? null : world.getTileEntity(at.x, at.y, at.z);
        }

        private static World worldOf(int dim) {
            try {
                final MinecraftServer server = FMLCommonHandler.instance()
                    .getMinecraftServerInstance();
                return server == null ? null : server.worldServerForDimension(dim);
            } catch (Throwable t) {
                // 服务端还没起来（启动早期/集成服双线程窗口）：当作"拿不到世界"，由调用侧收敛成 UNKNOWN
                return null;
            }
        }
    }
}
