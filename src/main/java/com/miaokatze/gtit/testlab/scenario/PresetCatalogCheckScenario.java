package com.miaokatze.gtit.testlab.scenario;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.Gson;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.preset.TradePresetDefinition;
import com.miaokatze.gtit.trade.v2.NekoBqQuestIdParser;

import appeng.items.materials.ItemMultiMaterial;
import appeng.items.materials.MaterialType;
import appeng.items.parts.ItemMultiPart;
import appeng.items.parts.PartType;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.common.Loader;
import gregtech.api.items.MetaGeneratedItem;

/** Read-only validation of the shipped preset resources. Never imports or selects a preset. */
final class PresetCatalogCheckScenario implements ProbeScenario {

    private static final String[] PRESETS = { "gtit-ae-basic", "gtit-ae-advanced", "gtit-base-decoration" };
    private static final int[] TRADE_COUNTS = { 123, 239, 64 };
    private static final int[] PAGE_COUNTS = { 7, 12, 2 };

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
            Map<Item, List<ItemStack>> subItems = new HashMap<>();
            Set<String> checkedVariants = new HashSet<>();
            Set<String> tradeIds = new HashSet<>();
            int total = 0;
            for (int index = 0; index < PRESETS.length; index++) {
                String id = PRESETS[index];
                String resource = "/assets/gtit/trade_presets/" + id + ".json";
                TradePresetDefinition preset;
                try (InputStream stream = PresetCatalogCheckScenario.class.getResourceAsStream(resource)) {
                    if (stream == null) throw new IllegalStateException("Missing bundled preset " + resource);
                    preset = new Gson()
                        .fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), TradePresetDefinition.class);
                }
                int count = preset.tabs.stream()
                    .mapToInt(tab -> tab.trades.size())
                    .sum();
                total += count;
                context.check(
                    id + "-bundled-size",
                    id.equals(preset.id) && count == TRADE_COUNTS[index] && preset.tabs.size() == PAGE_COUNTS[index],
                    "Actual bundled resource: " + count + " trades / " + preset.tabs.size() + " tabs");
                validateVariant(context, id + "-icon", preset.icon.item, preset.icon.meta, subItems, checkedVariants);
                for (TradePresetDefinition.Tab tab : preset.tabs) {
                    validateVariant(
                        context,
                        id + "-page-" + tab.page.getId(),
                        tab.page.getIconItem(),
                        tab.page.getIconMeta(),
                        subItems,
                        checkedVariants);
                    for (NekoTradeEntry trade : tab.trades) {
                        String key = trade.getId();
                        context.check(
                            key + "-identity",
                            tradeIds.add(key) && trade.getTabId() == tab.page.getId(),
                            "Unique source ID and matching local tab");
                        List<String> missing = new ArrayList<>();
                        if (trade.getRequiresMods() != null)
                            for (String mod : trade.getRequiresMods()) if (!Loader.isModLoaded(mod)) missing.add(mod);
                        context.check(key + "-mods", missing.isEmpty(), "Missing declared dependencies: " + missing);
                        String required = trade.getBqQuestId();
                        if (required != null && !required.isEmpty()) {
                            UUID quest = NekoBqQuestIdParser.parse(required);
                            context.check(
                                key + "-quest",
                                Loader.isModLoaded("betterquesting") && quest != null
                                    && QuestDatabase.INSTANCE.get(quest) != null,
                                "Actual QuestDatabase contains " + required);
                        }
                        int itemIndex = 0;
                        for (NekoTradeEntry.ItemEntry entry : trade.getFromItems())
                            validateStack(context, key + "-from-" + itemIndex++, entry, subItems, checkedVariants);
                        itemIndex = 0;
                        for (NekoTradeEntry.ItemEntry entry : trade.getToItems())
                            validateStack(context, key + "-to-" + itemIndex++, entry, subItems, checkedVariants);
                        if (trade.getCurrency() != null && trade.getCurrency()
                            .getAmount() > 0)
                            context.check(
                                key + "-currency",
                                NekoCurrencyRegistrar.getItemStack(
                                    trade.getCurrency()
                                        .getType(),
                                    1) != null,
                                "Real currency registrar resolves " + trade.getCurrency()
                                    .getType());
                    }
                }
            }
            context.check(
                "bundled-catalog-total",
                total == 426,
                "426 shipped definitions; this scenario never applies them");
            context.skip(
                "physical-client-catalog-render",
                "Dedicated server validates data only; icons and physical client UI need a client");
            return true;
        };
    }

    private static void validateStack(ProbeContext context, String key, NekoTradeEntry.ItemEntry entry,
        Map<Item, List<ItemStack>> subItems, Set<String> checkedVariants) throws Exception {
        ItemStack stack = entry.toItemStack();
        boolean hasNbt = entry.getNbtBase64() != null && !entry.getNbtBase64()
            .isEmpty();
        context.check(
            key + "-stack",
            stack != null && stack.stackSize == entry.getAmount()
                && stack.getItemDamage() == entry.getMeta()
                && (!hasNbt || stack.hasTagCompound()),
            "Exact registry, quantity, metadata and decoded NBT: " + entry.getItem() + ":" + entry.getMeta());
        validateVariant(context, key, entry.getItem(), entry.getMeta(), subItems, checkedVariants);
    }

    private static void validateVariant(ProbeContext context, String key, String registryId, int meta,
        Map<Item, List<ItemStack>> subItems, Set<String> checkedVariants) throws Exception {
        Item item = (Item) Item.itemRegistry.getObject(registryId);
        context.check(key + "-registry", item != null, "Exact registry ID " + registryId);
        if (item != null && checkedVariants.add(registryId + ":" + meta))
            validateMeta(context, "variant-" + registryId + ":" + meta, item, meta, subItems);
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
