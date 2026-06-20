package com.miaokatze.gtit.common.items.infinitycell;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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
import appeng.api.implementations.items.IItemGroup;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellConfigLegacy;
import appeng.items.contents.CellUpgrades;
import appeng.util.Platform;

public class ItemInfinityStorageCell extends Item implements IInfinityCellItem, IStorageCell, IItemGroup {

    private final int perType = 1;
    private final double idleDrain = 1D;

    public ItemInfinityStorageCell() {
        this.setMaxStackSize(1);
        this.setUnlocalizedName("gtit.infinity_cell");
        this.setTextureName(GTInterestingThing.MODID + ":infinity_cell");
        this.setFeature(EnumSet.of(AEFeature.StorageCells));
    }

    @Override
    public String getUnlocalizedGroupName(Set<ItemStack> otherItems, ItemStack is) {
        return GuiText.StorageCells.getUnlocalized();
    }

    @Override
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
        final boolean displayMoreInfo) {
        lines.add("\u00A7b无限存储 \u00A77- 无限种类/无限数量");
        lines.add("\u00A77空闲功耗: " + (int) this.idleDrain + " AE/t");

        final IMEInventoryHandler<?> inventory = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(stack, null, StorageChannel.ITEMS);

        if (inventory instanceof InfinityCellInventoryHandler handler) {
            final ITCellInventory cellInventory = handler.getCellInv();
            if (cellInventory != null) {
                String uid = cellInventory.getUUID();
                if (!uid.isEmpty()) lines.add(uid);
                if (handler.isPreformatted()) {
                    String filter = cellInventory.getOreFilter();
                    if (filter.isEmpty()) {
                        final String list = (handler.getIncludeExcludeMode() == IncludeExclude.WHITELIST
                            ? GuiText.Included
                            : GuiText.Excluded).getLocal();
                        if (handler.isFuzzy()) {
                            lines.add(GuiText.Partitioned.getLocal() + " - " + list + ' ' + GuiText.Fuzzy.getLocal());
                        } else {
                            lines.add(GuiText.Partitioned.getLocal() + " - " + list + ' ' + GuiText.Precise.getLocal());
                        }
                        if (GuiScreen.isShiftKeyDown()) {
                            lines.add(GuiText.Filter.getLocal() + ": ");
                            for (int i = 0; i < cellInventory.getConfigInventory()
                                .getSizeInventory(); ++i) {
                                ItemStack s = cellInventory.getConfigInventory()
                                    .getStackInSlot(i);
                                if (s != null) lines.add(s.getDisplayName());
                            }
                        }
                    } else {
                        lines.add(GuiText.PartitionedOre.getLocal() + " : " + filter);
                    }
                    if (handler.getSticky()) {
                        lines.add(GuiText.Sticky.getLocal());
                    }
                }
            }
        }
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getBytesLong(final ItemStack cellItem) {
        return Integer.MAX_VALUE;
    }

    @Override
    public int BytePerType(ItemStack cellItem) {
        return this.perType;
    }

    @Override
    public int getBytesPerType(ItemStack cellItem) {
        return this.perType;
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isBlackListed(ItemStack cellItem, IAEItemStack requestedAddition) {
        return false;
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    @Override
    public boolean isStorageCell(ItemStack i) {
        return false;
    }

    @Override
    public double getIdleDrain() {
        return this.idleDrain;
    }

    @Override
    public boolean isEditable(ItemStack is) {
        return true;
    }

    @Override
    public IInventory getUpgradesInventory(ItemStack is) {
        return new CellUpgrades(is, 2);
    }

    @Override
    public IInventory getConfigInventory(ItemStack is) {
        return new CellConfigLegacy(new CellConfig(is), ITEM_STACK_TYPE);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return FuzzyMode.fromItemStack(is);
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        Platform.openNbtData(is)
            .setString("FuzzyMode", fzMode.name());
    }

    public String getOreFilter(ItemStack is) {
        return Platform.openNbtData(is)
            .getString("OreFilter");
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public IMEInventoryHandler<IAEItemStack> getInventoryHandler(ItemStack o, ISaveProvider container,
        EntityPlayer player) throws AppEngException {
        return new InfinityCellInventoryHandler(new InfinityItemStorageCellInventory(o, container, player));
    }

    // Helper method for AE2 feature registration
    private void setFeature(EnumSet<AEFeature> features) {
        // No-op for non-AEBaseItem items; AE2 features are handled differently
    }
}
