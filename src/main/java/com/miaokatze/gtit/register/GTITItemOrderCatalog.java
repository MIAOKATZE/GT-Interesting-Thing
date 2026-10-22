package com.miaokatze.gtit.register;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.gift.GiftIconCatalog;
import com.miaokatze.gtit.gift.GiftShellCatalog;
import com.miaokatze.miaokatzecore.api.items.ItemOrderCatalog;

/** GTIT owns the domain order; Core applies it to both the creative tab and NEI. */
public final class GTITItemOrderCatalog {

    private static final String OWNER = "gtit";
    private static boolean registered;

    // Preserve the established order within each domain, independent of registry IDs and translated names.
    private static final GTITItemList[][] GROUPS = {
        // Mobility and scanning cores.
        { GTITItemList.FloatCore, GTITItemList.ElectricFloatCore, GTITItemList.TelekinesisOreScannerCore },
        // Wearable rings.
        { GTITItemList.RingDistantGrasp, GTITItemList.RingSkywalk, GTITItemList.RingWindrider,
            GTITItemList.RingGluttony, GTITItemList.RingIronheart, GTITItemList.RingDragonBreath,
            GTITItemList.RingMountainbreaker, GTITItemList.RingTempest },
        // Portable storage and its upgrades.
        { GTITItemList.NekoDimensionPocket, GTITItemList.NekoPocketUpgradeCapacity, GTITItemList.NekoPocketUpgradeStack,
            GTITItemList.NekoPocketUpgradeMagnet, GTITItemList.NekoPocketUpgradeChannelPersist,
            GTITItemList.NekoPocketUpgradeMage },
        // AE storage cells, legacy cells before the replacement unit.
        { GTITItemList.InfinityCell, GTITItemList.InfinityFluidCell, GTITItemList.NekoInfinityStorageUnit },
        // Machine controllers (the shared GregTech item is identified by exact metadata).
        { GTITItemList.NekoVendingMachine },
        // Currency and progression.
        { GTITItemList.NekoCoin, GTITItemList.ShimmeringNekoCoin, GTITItemList.ReincarnationCrystal } };

    private GTITItemOrderCatalog() {}

    public static void register() {
        if (registered) return;
        ItemOrderCatalog.register(OWNER, GTITItemOrderCatalog::rawItems);
        registered = true;
    }

    static void invalidate() {
        if (registered) ItemOrderCatalog.invalidate(OWNER);
    }

    /** Fresh domain catalog; reads the live registration list, never the Core-sorted creative entry point. */
    public static List<ItemStack> rawItems() {
        List<ItemStack> remaining = CreativeTabManager.getRegisteredItems();
        List<ItemStack> result = new ArrayList<>(remaining.size());
        for (GTITItemList[] group : GROUPS) {
            for (GTITItemList entry : group) {
                if (entry.hasBeenSet()) take(result, remaining, entry.get(1));
            }
        }
        // All 32 shells, including starter, follow the existing complete author-defined shell sequence.
        for (String key : GiftIconCatalog.keys()) {
            take(result, remaining, GiftShellCatalog.stack(key));
        }
        // Late additions remain visible in stable registration order until assigned a domain above.
        result.addAll(remaining);
        return result;
    }

    public static List<ItemStack> orderedItems() {
        register();
        return ItemOrderCatalog.orderedItems(OWNER);
    }

    private static void take(List<ItemStack> result, List<ItemStack> remaining, ItemStack identity) {
        if (identity == null) return;
        for (Iterator<ItemStack> iterator = remaining.iterator(); iterator.hasNext();) {
            ItemStack stack = iterator.next();
            if (stack.getItem() == identity.getItem() && stack.getItemDamage() == identity.getItemDamage()) {
                result.add(stack);
                iterator.remove();
            }
        }
    }
}
