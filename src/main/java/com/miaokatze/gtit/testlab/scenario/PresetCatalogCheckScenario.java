package com.miaokatze.gtit.testlab.scenario;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.Gson;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;

import appeng.items.materials.ItemMultiMaterial;
import appeng.items.materials.MaterialType;
import appeng.items.parts.ItemMultiPart;
import appeng.items.parts.PartType;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.common.Loader;
import gregtech.api.items.MetaGeneratedItem;

/** Read-only validation of the reviewed proposal. This never imports or selects a preset. */
final class PresetCatalogCheckScenario implements ProbeScenario {

    private static final String RESOURCE = "/assets/gtit/testlab/preset-catalog-check.json";
    private static final String ITEMS_RESOURCE = "/assets/gtit/testlab/preset-catalog-items.json";

    private static final class Entry {

        String item;
        int meta;
        String questId;
        String tradeId;
    }

    @Override
    public String id() {
        return "native-preset-catalog-check";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public ProbeTask start(ProbeContext context) {
        return () -> {
            Entry[] entries;
            try (InputStream stream = PresetCatalogCheckScenario.class.getResourceAsStream(RESOURCE)) {
                if (stream == null) throw new IllegalStateException("Missing reviewed catalog " + RESOURCE);
                entries = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Entry[].class);
            }
            context.check(
                "reviewed-catalog-size",
                entries != null && entries.length == 82,
                "Expected 82 reviewed proposals; no trade definitions are loaded or applied");
            if (entries == null) return true;
            Map<Item, List<ItemStack>> subItems = new HashMap<>();
            for (Entry entry : entries) {
                Item item = (Item) Item.itemRegistry.getObject(entry.item);
                String key = entry.tradeId + "-" + entry.item + ":" + entry.meta;
                context.check(key + "-registry", item != null, "Exact registry ID " + entry.item);
                if (item != null) validateMeta(context, key, item, entry.meta, subItems);
                if (Loader.isModLoaded("betterquesting")) {
                    UUID quest = UUID.fromString(entry.questId);
                    context.check(
                        key + "-quest",
                        QuestDatabase.INSTANCE.get(quest) != null,
                        "Actual server QuestDatabase contains " + quest);
                } else context.skip(key + "-quest", "BetterQuesting is absent; cannot validate the reviewed quest ID");
            }
            Entry[] materials;
            try (InputStream stream = PresetCatalogCheckScenario.class.getResourceAsStream(ITEMS_RESOURCE)) {
                if (stream == null) throw new IllegalStateException("Missing reviewed material catalog");
                materials = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Entry[].class);
            }
            context.check(
                "reviewed-material-catalog-size",
                materials != null && materials.length == 73,
                "73 distinct reviewed input/output item+metadata pairs");
            if (materials != null) for (Entry entry : materials) {
                Item item = (Item) Item.itemRegistry.getObject(entry.item);
                String key = "material-" + entry.item + ":" + entry.meta;
                context.check(key + "-registry", item != null, "Exact reviewed input/output registry ID");
                if (item != null) validateMeta(context, key, item, entry.meta, subItems);
            }
            ItemStack coin = NekoCurrencyRegistrar.getItemStack(NekoCurrencyRegistrar.NEKO_ID, 1);
            context.check(
                "neko-currency-registered",
                coin != null && coin.stackSize == 1,
                "Real currency registrar resolves the neko currency used by the reviewed trades");
            context.skip(
                "physical-client-catalog-render",
                "Dedicated server validates data only; icons and physical client UI need a client");
            return true;
        };
    }

    private static void validateMeta(ProbeContext context, String key, Item item, int meta,
        Map<Item, List<ItemStack>> subItems) throws Exception {
        ItemStack candidate = new ItemStack(item, 1, meta);
        // AE's server registry distinguishes missing variants from a merely existing Item object.
        // Avoid reflection on AE's method table: client-only parameter classes are absent on a server.
        if (item instanceof ItemMultiPart) {
            PartType type = ((ItemMultiPart) item).getTypeByStack(candidate);
            context.check(
                key + "-meta",
                type != null && type != PartType.InvalidType,
                "AE server getTypeByStack returned " + type);
            return;
        }
        if (item instanceof ItemMultiMaterial) {
            MaterialType type = ((ItemMultiMaterial) item).getTypeByStack(candidate);
            context.check(
                key + "-meta",
                type != null && type != MaterialType.InvalidType,
                "AE server getTypeByStack returned " + type);
            return;
        }
        if (item instanceof MetaGeneratedItem) {
            MetaGeneratedItem generated = (MetaGeneratedItem) item;
            int offset = meta - generated.mOffset;
            context.check(
                key + "-meta",
                offset >= 0 && generated.mEnabledItems.get(offset),
                "GT server enabled-meta bitset, offset=" + offset);
            return;
        }
        if (!item.getHasSubtypes() && meta == 0) {
            context.check(key + "-meta", true, "Non-subtyped item at its undamaged metadata 0");
            return;
        }
        List<ItemStack> variants = subItems.get(item);
        if (!subItems.containsKey(item)) {
            variants = new ArrayList<>();
            try {
                // Some GTNH items retain this method on servers; stripped client-only APIs are SKIP.
                Method method = item.getClass()
                    .getMethod("getSubItems", Item.class, CreativeTabs.class, List.class);
                method.invoke(item, item, null, variants);
            } catch (ReflectiveOperationException | LinkageError error) {
                variants = null;
            }
            subItems.put(item, variants);
        }
        if (variants == null || variants.isEmpty()) {
            context.skip(
                key + "-meta",
                "No server-safe metadata enumeration; registry presence alone does not validate metadata");
            return;
        }
        boolean found = variants.stream()
            .anyMatch(stack -> stack != null && stack.getItem() == item && stack.getItemDamage() == meta);
        context
            .check(key + "-meta", found, "Exact metadata in the item's enumerated variants, count=" + variants.size());
    }
}
