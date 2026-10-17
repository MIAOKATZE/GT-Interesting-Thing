package com.miaokatze.gtit.gift;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.items.GiftShell;
import com.miaokatze.gtit.common.items.StarterGift;
import com.miaokatze.gtit.register.CreativeTabManager;

import cpw.mods.fml.common.registry.GameRegistry;

/** Registry identity is independent of the mutable definition and the package authorization NBT. */
public final class GiftShellCatalog {

    private GiftShellCatalog() {}

    public static String registryName(String key) {
        if (!GiftIconCatalog.contains(key)) throw new IllegalArgumentException("Unknown gift shell: " + key);
        return "gtit:" + key + "_gift";
    }

    public static void register() {
        GTITItemList.StarterGift.setAndRegister(new StarterGift(), "starter_gift", true);
        for (String key : GiftIconCatalog.keys()) {
            if ("starter".equals(key)) continue;
            GiftShell shell = new GiftShell(key);
            GameRegistry.registerItem(shell, key + "_gift");
            CreativeTabManager.addItemToTab(new ItemStack(shell));
        }
    }

    public static ItemStack stack(String key) {
        Item item = (Item) Item.itemRegistry.getObject(registryName(key));
        return item instanceof GiftShell ? new ItemStack(item) : null;
    }

    public static String key(ItemStack stack) {
        return stack != null && stack.getItem() instanceof GiftShell shell ? shell.shellKey() : null;
    }

    /** Missing fields in pre-binding configurations and already-saved grants get a stable compatibility binding. */
    public static String effectiveKey(GiftDefinition definition) {
        if (definition.shellKey != null && !definition.shellKey.isEmpty()) return definition.shellKey;
        if (GiftIconCatalog.contains(definition.id)) return definition.id;
        return GiftIconCatalog.normalize(definition.iconKey);
    }
}
