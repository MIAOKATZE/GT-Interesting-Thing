package com.miaokatze.gtit.common.items.infinitycell;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.main.GTInterestingThing;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.exceptions.AppEngException;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellConfigLegacy;
import appeng.items.contents.CellUpgrades;
import appeng.util.Platform;

public class ItemInfinityStorageFluidCell extends Item implements IInfinityCellItem {

    private final int perType = 1;
    private final double idleDrain = 10D;

    public ItemInfinityStorageFluidCell() {
        this.setMaxStackSize(1);
        this.setUnlocalizedName("gtit.infinity_fluid_cell");
        this.setTextureName(GTInterestingThing.MODID + ":infinity_fluid_cell");
        this.setFeature(EnumSet.of(AEFeature.StorageCells));
    }

    @Override
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
        final boolean displayMoreInfo) {
        final IMEInventoryHandler<?> inventory = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.FLUIDS);

        if (inventory instanceof InfinityFluidCellInventoryHandler handler) {
            final ITFluidCellInventory cellInventory = handler.getCellInv();
            if (cellInventory != null) {
                String uid = cellInventory.getUUID();
                if (!uid.isEmpty()) lines.add(uid);
                if (handler.isPreformatted()) {
                    final String list = (handler.getIncludeExcludeMode() == IncludeExclude.WHITELIST ? GuiText.Included
                        : GuiText.Excluded).getLocal();
                    lines.add(GuiText.Partitioned.getLocal() + " - " + list + ' ' + GuiText.Precise.getLocal());
                    if (GuiScreen.isShiftKeyDown()) {
                        lines.add(GuiText.Filter.getLocal() + ": ");
                        for (IAEFluidStack aeFluidStack : handler.getPartitionInv()) {
                            if (aeFluidStack != null) lines.add(
                                "  " + aeFluidStack.getFluidStack()
                                    .getLocalizedName());
                        }
                    }
                }
            }
        }
    }

    public long getBytes(ItemStack cellItem) {
        return Integer.MAX_VALUE;
    }

    public int getBytesPerType(ItemStack cellItem) {
        return this.perType;
    }

    public boolean isBlackListed(ItemStack cellItem, IAEFluidStack requestedAddition) {
        return requestedAddition == null || requestedAddition.getFluid() == null;
    }

    public boolean storableInStorageCell() {
        return false;
    }

    public boolean isStorageCell(ItemStack i) {
        return false;
    }

    public double getIdleDrain(ItemStack is) {
        return this.idleDrain;
    }

    public int getTotalTypes(ItemStack cellItem) {
        return Integer.MAX_VALUE;
    }

    public boolean isEditable(ItemStack is) {
        return true;
    }

    public IInventory getUpgradesInventory(ItemStack is) {
        return new CellUpgrades(is, 0);
    }

    public IInventory getConfigInventory(ItemStack is) {
        return new CellConfigLegacy(new CellConfig(is), FLUID_STACK_TYPE);
    }

    public FuzzyMode getFuzzyMode(ItemStack is) {
        final String fz = Platform.openNbtData(is)
            .getString("FuzzyMode");
        try {
            return FuzzyMode.valueOf(fz);
        } catch (final Throwable t) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        Platform.openNbtData(is)
            .setString("FuzzyMode", fzMode.name());
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.FLUIDS;
    }

    @Override
    public IMEInventoryHandler<IAEFluidStack> getInventoryHandler(ItemStack o, ISaveProvider container,
        EntityPlayer player) throws AppEngException {
        return new InfinityFluidCellInventoryHandler(new InfinityFluidStorageCellInventory(o, container, player));
    }

    // Helper method for AE2 feature registration
    private void setFeature(EnumSet<AEFeature> features) {
        // No-op for non-AEBaseItem items
    }
}
