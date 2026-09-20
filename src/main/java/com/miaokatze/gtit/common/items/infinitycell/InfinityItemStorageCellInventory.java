package com.miaokatze.gtit.common.items.infinitycell;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.exceptions.AppEngException;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class InfinityItemStorageCellInventory implements ITCellInventory {

    /** 统一 logger（与同包 InfinityCellHandler 共用 "gtit" 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private static final String ITEM_TYPE_TAG = "it";
    private static final String ITEM_COUNT_TAG = "ic";
    protected final ItemStack cellItem;
    protected ItemInfinityStorageCell cellType;
    protected final ISaveProvider container;
    protected final EntityPlayer player;
    protected IItemList<IAEItemStack> cellItems = null;
    protected long storedItemTypes;
    protected long storedItemCount = 0;
    protected final NBTTagCompound data;
    protected final IChestOrDrive drive;
    protected final DataStorage storage;

    public InfinityItemStorageCellInventory(ItemStack o, ISaveProvider c, EntityPlayer p) throws AppEngException {
        if (o == null) {
            throw new AppEngException("ItemStack was used as a cell, but was not a cell!");
        }
        cellItem = o;
        container = c;
        player = p;
        this.drive = c instanceof IChestOrDrive ? (IChestOrDrive) c : null;
        this.cellType = (ItemInfinityStorageCell) this.cellItem.getItem();
        this.data = Platform.openNbtData(this.cellItem);
        this.storedItemTypes = data.getLong(ITEM_TYPE_TAG);
        this.storedItemCount = data.getLong(ITEM_COUNT_TAG);
        // 防护：StorageManager 在 serverStarted 事件才初始化，客户端 tooltip 路径或
        // serverStarted 抛错时 getInstance() 为 null，直接解引用会 NPE。
        // 抛出明确异常，由 InfinityCellHandler.getCellInventory 的 catch 记录日志，
        // 而非让 NPE 冒泡导致"元件静默失效且无任何线索"。
        StorageManager manager = StorageManager.getInstance();
        if (manager == null) {
            throw new AppEngException("StorageManager 未初始化（服务器未完全启动或客户端 tooltip 路径）");
        }
        this.storage = manager.getStorage(this.cellItem);
    }

    @Override
    public double getIdleDrain(ItemStack is) {
        return this.cellType.getIdleDrain();
    }

    @Override
    public void loadCellItems() {
        if (this.cellItems == null) {
            this.cellItems = this.storage.getItems();
            for (IAEItemStack is : this.cellItems) {
                if (is.getStackSize() <= 0) is.reset();
            }
        }
        if (!this.getUUID()
            .equals(this.storage.getUUID())) {
            data.setString(InfinityCellConstants.DISKUUID, this.storage.getUUID());
        }
    }

    protected IItemList<IAEItemStack> getCellItems() {
        if (this.cellItems == null) {
            this.loadCellItems();
        }
        return this.cellItems;
    }

    @Override
    public String getUUID() {
        if (data.hasNoTags()) {
            return "";
        }
        return data.getString(InfinityCellConstants.DISKUUID);
    }

    @Override
    public ItemStack getItemStack() {
        return this.cellItem;
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        return this.cellType.getFuzzyMode(this.cellItem);
    }

    @Override
    public IInventory getConfigInventory() {
        return this.cellType.getConfigInventory(this.cellItem);
    }

    @Override
    public IInventory getUpgradesInventory() {
        return this.cellType.getUpgradesInventory(this.cellItem);
    }

    @Override
    public int getBytesPerType() {
        return this.cellType.getBytesPerType(this.cellItem);
    }

    @Override
    public boolean canHoldNewItem(ItemStack is) {
        return true;
    }

    @Override
    public long getTotalBytes() {
        return this.cellType.getTotalTypes(this.cellItem);
    }

    @Override
    public long getFreeBytes() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getUsedBytes() {
        return 0;
    }

    @Override
    public long getTotalItemTypes() {
        return this.cellType.getTotalTypes(this.cellItem);
    }

    @Override
    public long getStoredItemCount() {
        return this.storedItemCount;
    }

    @Override
    public long getStoredItemTypes() {
        return this.storedItemTypes;
    }

    @Override
    public long getRemainingItemTypes() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getRemainingItemCount() {
        return Long.MAX_VALUE;
    }

    @Override
    public int getUnusedItemCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getStatusForCell() {
        if (this.canHoldNewItem(this.cellItem)) {
            return 1;
        }
        if (this.getRemainingItemCount() > 0) {
            return 2;
        }
        return 3;
    }

    @Override
    public String getOreFilter() {
        return this.cellType.getOreFilter(this.cellItem);
    }

    @Override
    public StorageManager getStorageManager() {
        return StorageManager.getInstance();
    }

    private static boolean isStorageCell(final ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        try {
            final Item type = itemStack.getItem();
            if (type instanceof IStorageCell) {
                return !((IStorageCell) type).storableInStorageCell();
            }
        } catch (final Throwable err) {
            return true;
        }
        return false;
    }

    /**
     * 嵌套元件 handler 的供给口，把 AE2 registry 查询（需要已引导的运行环境）与「非空判定」隔离，
     * 使判定 + 探测可在纯 JVM 单测里用接口 stub 覆盖。按元件自己的通道参数化，探测侧无需强转。
     */
    interface NestedCellLookup<T extends IAEStack> {

        IMEInventoryHandler<T> get(IAEStackType<T> type);
    }

    /** 取嵌套元件自己声明的通道；取不到（非 IStorageCell、返回 null、连 {@code getItem()} 都抛错）返回 null 表示不可判定。 */
    private static IAEStackType<?> nestedStackType(final ItemStack nested) {
        try {
            final Item item = nested.getItem();
            if (item instanceof IStorageCell) {
                return ((IStorageCell) item).getStackType();
            }
        } catch (final Throwable err) {
            LOG.warn("读取嵌套元件通道失败，按放行处理（fail-open）", err);
        }
        return null;
    }

    /** AE2 的 {@code ICellRegistry} 该重载自身返回原始 {@code IMEInventoryHandler}，unchecked 来源仅此一处。 */
    @SuppressWarnings("unchecked")
    private static <T extends IAEStack> IMEInventoryHandler<T> nestedCellHandler(final ItemStack nested,
        final IAEStackType<T> type) {
        return (IMEInventoryHandler<T>) AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(nested, null, type);
    }

    private static <T extends IAEStack> NestedCellLookup<T> nestedCellLookup(final ItemStack nested) {
        return type -> nestedCellHandler(nested, type);
    }

    /**
     * 「非空则拒收」判定的可测内核：探测列表由元件自己的通道给出，判定只读返回列表的 {@code isEmpty()}。
     * 不可判定一律放行（返回 false）：handler 为 null（registry 缺失或元件的通道守卫拒绝错配）、
     * 或探测抛出任何 Throwable（含第三方 handler 对元素类型的盲转）。
     */
    static <T extends IAEStack> boolean handlerHasContent(final IMEInventoryHandler<T> handler,
        final Supplier<IItemList<T>> probeList) {
        if (handler == null || probeList == null) {
            return false;
        }
        try {
            return !handler.getAvailableItems(probeList.get())
                .isEmpty();
        } catch (final Throwable err) {
            LOG.warn("探测嵌套元件内容失败，按放行处理（fail-open）", err);
            return false;
        }
    }

    /** 通道或供给口取不到即不可判定 → 放行，且不向 registry 发问。 */
    static <T extends IAEStack> boolean nestedCellHasContent(final IAEStackType<T> type,
        final NestedCellLookup<T> handlerSource) {
        if (type == null || handlerSource == null) {
            return false;
        }
        try {
            return handlerHasContent(handlerSource.get(type), type::createList);
        } catch (final Throwable err) {
            LOG.warn("查询嵌套元件 handler 失败，按放行处理（fail-open）", err);
            return false;
        }
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, BaseActionSource src) {
        if (input == null) {
            return null;
        }
        if (input.getStackSize() == 0) {
            return null;
        }
        if (this.cellType.isBlackListed(this.cellItem, input)) {
            return input;
        }
        final ItemStack sharedItemStack = input.getItemStack();
        if (isStorageCell(sharedItemStack)) {
            // 固定 ITEM_STACK_TYPE 会绕过 AE2 handler 自己的通道守卫（BasicCellHandler 以
            // cell.getStackType() == type 拒绝错配），通道必须取元件自己的 getStackType()（Issue #16）。
            if (nestedCellHasContent(nestedStackType(sharedItemStack), nestedCellLookup(sharedItemStack))) {
                return input;
            }
        }
        final IAEItemStack l = this.getCellItems()
            .findPrecise(input);
        if (l != null) {
            final long remainingItemSlots = this.getRemainingItemCount();
            if (remainingItemSlots < 0) {
                return input;
            }
            if (input.getStackSize() > remainingItemSlots) {
                final IAEItemStack r = input.copy();
                r.setStackSize(r.getStackSize() - remainingItemSlots);
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() + remainingItemSlots);
                    this.saveChanges();
                }
                return r;
            } else {
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() + input.getStackSize());
                    this.saveChanges();
                }
                return null;
            }
        }
        if (this.canHoldNewItem(this.cellItem)) {
            final long remainingItemCount = this.getRemainingItemCount() - this.getBytesPerType() * 8L;
            if (remainingItemCount > 0) {
                if (input.getStackSize() > remainingItemCount) {
                    final IAEItemStack toReturn = AEItemStack.create(sharedItemStack);
                    toReturn.decStackSize(remainingItemCount);
                    if (mode == Actionable.MODULATE) {
                        final IAEItemStack toWrite = AEItemStack.create(sharedItemStack);
                        toWrite.setStackSize(remainingItemCount);
                        this.cellItems.add(toWrite);
                        this.saveChanges();
                    }
                    return toReturn;
                }
                if (mode == Actionable.MODULATE) {
                    this.cellItems.add(input);
                    this.saveChanges();
                }
                return null;
            }
        }
        return input;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource src) {
        if (request == null) {
            return null;
        }
        final long size = request.getStackSize();
        IAEItemStack results = null;
        final IAEItemStack l = this.getCellItems()
            .findPrecise(request);
        if (l != null) {
            results = l.copy();
            if (l.getStackSize() <= size) {
                results.setStackSize(l.getStackSize());
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(0);
                    this.saveChanges();
                }
            } else {
                results.setStackSize(size);
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() - size);
                    this.saveChanges();
                }
            }
        }
        return results;
    }

    private void saveChanges() {
        this.data.setBoolean(InfinityCellConstants.IS_EMPTY, this.cellItems.isEmpty());
        if (this.container != null) {
            this.container.saveChanges(this);
        }
        StorageManager.getInstance()
            .postChanges(this.storage);
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        for (final IAEItemStack i : this.getCellItems()) {
            out.add(i);
        }
        return out;
    }

    @Override
    public StorageChannel getChannel() {
        return ((IInfinityCellItem) Objects.requireNonNull(this.cellItem.getItem())).getChannel();
    }
}
