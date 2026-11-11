// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.trade.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.common.collect.ArrayListMultimap;
import com.miaokatze.gtit.crossmod.gtmb.GtmbProjectorCompat;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.registry.GameData;

/** 真实 Forge 物品、重映射与生产贸易管线；仅在 Gradle scratch 目录运行。 */
public final class GtmbProjectorCompatibilityTest {

    private static final String TRADE_ID = "d7f5e8f2-247c-49af-b19b-d2f726ce34d0";
    private static final String CUSTOM_ID = "2f0c54f4-9f4c-47a5-a819-d462585dd899";
    private static Item projector;

    private GtmbProjectorCompatibilityTest() {}

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("gtit.projector.testScratch")) throw new IllegalStateException("scratch required");
        if (!MinecraftTestBootstrap.enter(GtmbProjectorCompatibilityTest.class, args)) return;
        projector = new Item();
        NekoTradeConfig.setDefaultTradesSuppressed(true);
        NekoCurrencyRegistrar.nekoCoinItem = Items.gold_nugget;
        NekoCurrencyRegistrar.shimmeringNekoCoinItem = Items.emerald;
        setPresence(false);
        check(
            JarAssetManifest.parse(JarAssetManifest.readJsonResource("assets/gtit/gtit/trade/index.json"))
                .getGroups()
                .isEmpty(),
            "GTIT no longer declares any GTMB trade injection");
        check(
            JarAssetManifest.readJsonResource("assets/gtit/gtit/trade/groups/neko_hologram_projector.json") == null,
            "retired projector exchange asset is absent");
        check(
            JarAssetManifest.readJsonResource("assets/gtit/bqtrades/base_trades.json")
                .get("version")
                .getAsInt() == 9,
            "main default group version unchanged");
        check(
            JarAssetManifest.readJsonResource("assets/gtit/bqtrades/base_trades.json")
                .getAsJsonArray("trades")
                .size() == 92,
            "main default group retains all 92 entries");
        check(
            !JarAssetManifest.readJsonResource("assets/gtit/bqquests/index.json")
                .toString()
                .contains("NekoHologramProj"),
            "projector quest no longer bundled");

        NekoTradeConfig.NekoTradeData data = new NekoTradeConfig.NekoTradeData();
        NekoTradeEntry custom = new NekoTradeEntry();
        custom.setId(CUSTOM_ID);
        custom.getToItems()
            .add(new NekoTradeEntry.ItemEntry("minecraft:paper", 0, 1));
        NekoTradeEntry legacy = new NekoTradeEntry();
        legacy.setId(TRADE_ID);
        legacy.getToItems()
            .add(new NekoTradeEntry.ItemEntry(GtmbProjectorCompat.OLD_ITEM_ID, 0, 1));
        legacy.setCurrency(new NekoTradeEntry.NekoCurrencyCost("shimmeringNeko", 1));
        data.getTrades()
            .add(custom);
        data.getTrades()
            .add(legacy);
        NekoTradeConfig.save(data);
        NekoTradeIntegrationAPI.GroupRecord oldRecord = new NekoTradeIntegrationAPI.GroupRecord(
            GtmbProjectorCompat.GROUP_ID,
            2);
        oldRecord.tradeIds.add(TRADE_ID);
        oldRecord.save();
        NekoTradeIntegrationAPI.GroupRecord unrelated = new NekoTradeIntegrationAPI.GroupRecord("unit-custom", 7);
        unrelated.tradeIds.add(CUSTOM_ID);
        unrelated.save();
        byte[] unrelatedRecord = Files.readAllBytes(unrelated.path());
        BundledTradeGroups.applyBundledGroups();
        check(find(TRADE_ID) == null && find(CUSTOM_ID) != null, "without GTMB removes only retired managed exchange");
        check(
            NekoTradeIntegrationAPI.GroupRecord.load(GtmbProjectorCompat.GROUP_ID) == null,
            "retired group leaves no registration record");

        setPresence(true);
        FMLMissingMappingsEvent.MissingMapping missing = mapping(GtmbProjectorCompat.OLD_ITEM_ID, 24000);
        GtmbProjectorCompat.remapMissingItems(event(missing));
        check(
            missing.getAction() == FMLMissingMappingsEvent.Action.DEFAULT,
            "missing target never requests illegal remap");
        register(projector, GtmbProjectorCompat.GTMB_ITEM_ID);
        nativeRemap();
        // GTMB present and fully registered: remove the prior exchange rather than upgrading it.
        NekoTradeConfig.save(data);
        oldRecord.save();
        BundledTradeGroups.applyBundledGroups();
        check(find(TRADE_ID) == null && find(CUSTOM_ID) != null, "installed GTMB also retires the managed exchange");
        applyAssetQueue();
        BundledTradeGroups.applyBundledGroups();
        check(find(TRADE_ID) == null, "restart never reinjects retired exchange");
        check(
            java.util.Arrays.equals(unrelatedRecord, Files.readAllBytes(unrelated.path())),
            "unrelated group record unchanged");

        // Losing the integration receipt must not strand the original fixed UUID or remove custom trades.
        NekoTradeConfig.save(data);
        BundledTradeGroups.applyBundledGroups();
        check(
            find(TRADE_ID) == null && find(CUSTOM_ID) != null,
            "lost-record cleanup only targets stable managed UUID");
        setPresence(false);
        FMLMissingMappingsEvent.MissingMapping reverse = mapping(GtmbProjectorCompat.GTMB_ITEM_ID, 24000);
        GtmbProjectorCompat.remapMissingItems(event(reverse));
        check(reverse.getAction() == FMLMissingMappingsEvent.Action.DEFAULT, "no reverse GTMB to GTIT mapping");
        System.out.println("GtmbProjectorCompatibilityTest passed");
    }

    private static void applyAssetQueue() {
        NekoTradeIntegrationAPI.registerTradeAssetsFromJar("gtit");
        NekoTradeIntegrationAPI.applyQueuedGroups();
    }

    private static NekoTradeEntry find(String id) {
        for (NekoTradeEntry entry : NekoTradeConfig.load()
            .getTrades()) if (id.equals(entry.getId())) return entry;
        return null;
    }

    private static void setPresence(boolean present) throws Exception {
        Loader loader = Loader.instance();
        Field names = Loader.class.getDeclaredField("namedMods");
        names.setAccessible(true);
        Map<String, ModContainer> mods = new HashMap<>();
        if (present) {
            ModMetadata metadata = new ModMetadata();
            metadata.modId = "gtmb";
            mods.put("gtmb", new DummyModContainer(metadata));
        }
        names.set(loader, mods);
        Field controller = Loader.class.getDeclaredField("modController");
        controller.setAccessible(true);
        controller.set(loader, new LoadController(loader));
    }

    private static void register(Item item, String id) throws Exception {
        Method main = GameData.class.getDeclaredMethod("getMain");
        main.setAccessible(true);
        Method register = GameData.class.getDeclaredMethod("registerItem", Item.class, String.class, int.class);
        register.setAccessible(true);
        register.invoke(main.invoke(null), item, id, -1);
    }

    private static FMLMissingMappingsEvent.MissingMapping mapping(String name, int id) {
        return new FMLMissingMappingsEvent.MissingMapping("\u0002" + name, id);
    }

    private static FMLMissingMappingsEvent event(FMLMissingMappingsEvent.MissingMapping mapping) {
        ArrayListMultimap<String, FMLMissingMappingsEvent.MissingMapping> mappings = ArrayListMultimap.create();
        mappings.put(mapping.name.split(":", 2)[0], mapping);
        return new FMLMissingMappingsEvent(mappings);
    }

    private static void nativeRemap() throws Exception {
        FMLMissingMappingsEvent.MissingMapping mapping = mapping(GtmbProjectorCompat.OLD_ITEM_ID, 24001);
        GtmbProjectorCompat.remapMissingItems(event(mapping));
        check(
            mapping.getAction() == FMLMissingMappingsEvent.Action.REMAP && mapping.getTarget() == projector,
            "production handler requests registered GTMB target");
        Constructor<GameData> empty = GameData.class.getDeclaredConstructor();
        empty.setAccessible(true);
        Method main = GameData.class.getDeclaredMethod("getMain");
        main.setAccessible(true);
        GameData current = (GameData) main.invoke(null);
        Constructor<GameData> copy = GameData.class.getDeclaredConstructor(GameData.class);
        copy.setAccessible(true);
        GameData original = copy.newInstance(current);
        GameData remapped = empty.newInstance();
        check(
            GameData.processIdRematches(Collections.singletonList(mapping), true, remapped, new HashMap<>())
                .isEmpty(),
            "native Forge rematch resolves saved ID");
        Method replace = GameData.class.getDeclaredMethod("set", GameData.class);
        replace.setAccessible(true);
        try {
            replace.invoke(current, remapped);
            NBTTagCompound saved = new NBTTagCompound();
            saved.setShort("id", (short) 24001);
            saved.setByte("Count", (byte) 2);
            saved.setShort("Damage", (short) 7);
            NBTTagCompound tool = new NBTTagCompound();
            NBTTagCompound draft = new NBTTagCompound();
            draft.setString("preset", "preserved-user-draft");
            tool.setTag("gtitHologramDraft", draft);
            saved.setTag("tag", tool);
            ItemStack loaded = ItemStack.loadItemStackFromNBT(saved);
            check(loaded != null && loaded.getItem() == projector, "saved numeric ID loads real GTMB item");
            check(
                loaded.stackSize == 2 && loaded.getItemDamage() == 7 && tool.equals(loaded.stackTagCompound),
                "Forge keeps count, damage and complete tool NBT");
            check(Item.itemRegistry.getObject(mapping.name) == projector, "native Forge creates legacy name alias");
        } finally {
            replace.invoke(current, original);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
