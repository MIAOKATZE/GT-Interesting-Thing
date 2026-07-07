package com.miaokatze.gtit.common.items.infinitycell;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.lang.reflect.Field;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.MEPassThrough;
import appeng.util.item.AEItemStack;
import appeng.util.prioitylist.DefaultPriorityList;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.IPartitionList;
import appeng.util.prioitylist.OreFilteredList;
import appeng.util.prioitylist.PrecisePriorityList;

public class InfinityCellInventoryHandler extends MEInventoryHandler<IAEItemStack> implements ICellCacheRegistry {

    private static final Field fPartitionList;
    private static final Field fInternal;

    // v1.5.11+: 合并为单个 static 块（此前分两块，fInternal 声明在使用它的方法之后，
    // 虽然 Java 静态初始化顺序能保证正确，但易误导维护者）。与 InfinityFluidCellInventoryHandler 风格统一。
    static {
        try {
            fPartitionList = MEInventoryHandler.class.getDeclaredField("myPartitionList");
            fPartitionList.setAccessible(true);
            fInternal = MEPassThrough.class.getDeclaredField("internal");
            fInternal.setAccessible(true);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reflect MEInventoryHandler/MEPassThrough fields", e);
        }
    }

    public InfinityCellInventoryHandler(IMEInventory<IAEItemStack> i) {
        super(i, ITEM_STACK_TYPE);
        init(this.getCellInv());
    }

    public void init(ITCellInventory ci) {
        if (ci != null) {
            final IInventory upgrades = ci.getUpgradesInventory();
            final IInventory config = ci.getConfigInventory();
            final FuzzyMode fzMode = ci.getFuzzyMode();
            final String filter = ci.getOreFilter();

            boolean hasInverter = false;
            boolean hasFuzzy = false;
            boolean hasOreFilter = false;
            boolean hasSticky = false;

            for (int x = 0; x < upgrades.getSizeInventory(); x++) {
                final ItemStack is = upgrades.getStackInSlot(x);
                if (is != null && is.getItem() instanceof IUpgradeModule) {
                    final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                    if (u != null) {
                        switch (u) {
                            case FUZZY -> hasFuzzy = true;
                            case INVERTER -> hasInverter = true;
                            case ORE_FILTER -> hasOreFilter = true;
                            case STICKY -> hasSticky = true;
                            default -> {}
                        }
                    }
                }
            }
            this.setWhitelist(hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);

            if (hasSticky) {
                setSticky(true);
            }

            if (hasOreFilter && !filter.isEmpty()) {
                this.setPartitionList(new OreFilteredList(filter));
            } else {
                final IItemList<IAEItemStack> priorityList = AEApi.instance()
                    .storage()
                    .createItemList();
                for (int x = 0; x < config.getSizeInventory(); x++) {
                    final ItemStack is = config.getStackInSlot(x);
                    if (is != null) {
                        priorityList.add(AEItemStack.create(is));
                    }
                }
                if (!priorityList.isEmpty()) {
                    if (hasFuzzy) {
                        this.setPartitionList(new FuzzyPriorityList<>(priorityList, fzMode));
                    } else {
                        this.setPartitionList(new PrecisePriorityList<>(priorityList));
                    }
                }
            }
        }
    }

    @Override
    public boolean canGetInv() {
        return this.getCellInv() != null;
    }

    public long getTotalBytes() {
        return this.getCellInv()
            .getTotalBytes();
    }

    public long getFreeBytes() {
        return this.getCellInv()
            .getFreeBytes();
    }

    public long getUsedBytes() {
        return this.getCellInv()
            .getUsedBytes();
    }

    public long getTotalTypes() {
        return this.getCellInv()
            .getTotalItemTypes();
    }

    public long getFreeTypes() {
        return this.getCellInv()
            .getRemainingItemTypes();
    }

    public long getUsedTypes() {
        return this.getCellInv()
            .getStoredItemTypes();
    }

    public int getCellStatus() {
        return this.getStatusForCell();
    }

    public TYPE getCellType() {
        return TYPE.ITEM;
    }

    public ITCellInventory getCellInv() {
        Object o = this.getInternal();
        if (o instanceof MEPassThrough<?>mpt) {
            try {
                o = fInternal.get(mpt);
            } catch (Exception e) {
                return null;
            }
        }
        return (o instanceof ITCellInventory) ? (ITCellInventory) o : null;
    }

    public boolean isPreformatted() {
        return !getPartitionList(this).isEmpty();
    }

    public boolean isFuzzy() {
        return getPartitionList(this) instanceof FuzzyPriorityList;
    }

    public IncludeExclude getIncludeExcludeMode() {
        return this.getWhitelist();
    }

    public int getStatusForCell() {
        int val = this.getCellInv()
            .getStatusForCell();
        if (val == 1 && this.isPreformatted()) {
            val = 2;
        }
        return val;
    }

    private static IPartitionList<?> getPartitionList(MEInventoryHandler<?> handler) {
        try {
            return (IPartitionList<?>) fPartitionList.get(handler);
        } catch (Exception e) {
            return new DefaultPriorityList<>();
        }
    }
}
