package com.miaokatze.gtit.common.items.infinitycell;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;

import java.lang.reflect.Field;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.MEPassThrough;
import appeng.util.item.AEFluidStack;
import appeng.util.prioitylist.DefaultPriorityList;
import appeng.util.prioitylist.IPartitionList;
import appeng.util.prioitylist.PrecisePriorityList;

public class InfinityFluidCellInventoryHandler extends MEInventoryHandler<IAEFluidStack> implements ICellCacheRegistry {

    private static final Field fPartitionList;
    private static final Field fInternal;

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

    public InfinityFluidCellInventoryHandler(final IMEInventory<IAEFluidStack> c) {
        super(c, FLUID_STACK_TYPE);
        final ITFluidCellInventory ci = this.getCellInv();
        if (ci != null) {
            final IInventory config = ci.getConfigInventory();
            final IItemList<IAEFluidStack> priorityList = AEApi.instance()
                .storage()
                .createFluidList();
            for (int x = 0; x < config.getSizeInventory(); x++) {
                final ItemStack is = config.getStackInSlot(x);
                FluidStack fluidStack = getFluidFromItem(is);
                if (fluidStack != null) {
                    priorityList.add(AEFluidStack.create(fluidStack));
                }
            }
            if (!priorityList.isEmpty()) {
                this.setPartitionList(new PrecisePriorityList<>(priorityList));
            }
        }
    }

    private static FluidStack getFluidFromItem(ItemStack is) {
        if (is == null) return null;
        if (is.getItem() instanceof net.minecraftforge.fluids.IFluidContainerItem fci) {
            return fci.getFluid(is);
        }
        return null;
    }

    public ITFluidCellInventory getCellInv() {
        Object o = this.getInternal();
        if (o instanceof MEPassThrough<?>mpt) {
            try {
                o = fInternal.get(mpt);
            } catch (Exception e) {
                return null;
            }
        }
        return (o instanceof ITFluidCellInventory) ? (ITFluidCellInventory) o : null;
    }

    @SuppressWarnings("unchecked")
    public Iterable<IAEFluidStack> getPartitionInv() {
        return (Iterable<IAEFluidStack>) getPartitionList(this).getItems();
    }

    public boolean isPreformatted() {
        return !getPartitionList(this).isEmpty();
    }

    public IncludeExclude getIncludeExcludeMode() {
        return IncludeExclude.WHITELIST;
    }

    public int getStatusForCell() {
        int val = this.getCellInv()
            .getStatusForCell();
        if (val == 1 && this.isPreformatted()) {
            val = 2;
        }
        return val;
    }

    @Override
    public boolean canGetInv() {
        return true;
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
            .getTotalFluidTypes();
    }

    public long getFreeTypes() {
        return this.getCellInv()
            .getRemainingFluidTypes();
    }

    public long getUsedTypes() {
        return this.getCellInv()
            .getStoredFluidTypes();
    }

    public int getCellStatus() {
        return this.getStatusForCell();
    }

    public TYPE getCellType() {
        return TYPE.FLUID;
    }

    private static IPartitionList<?> getPartitionList(MEInventoryHandler<?> handler) {
        try {
            return (IPartitionList<?>) fPartitionList.get(handler);
        } catch (Exception e) {
            return new DefaultPriorityList<>();
        }
    }
}
