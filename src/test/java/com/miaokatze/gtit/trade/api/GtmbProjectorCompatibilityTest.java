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
import com.google.gson.Gson;
import com.miaokatze.gtit.crossmod.gtmb.GtmbProjectorCompat;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.registry.GameData;

/** 真实 Forge 物品、重映射与生产贸易管线；仅在 Gradle scratch 目录运行。 */
public final class GtmbProjectorCompatibilityTest {

    private static final String RESOURCE = "assets/gtit/gtit/trade/groups/neko_hologram_projector.json";
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
        NekoTradeGroupDef def = group();
        check(
            def.getVersion() == 3 && def.getTrades()
                .size() == 1,
            "original managed group upgrades to v3");
        check(
            TRADE_ID.equals(
                def.getTrades()
                    .get(0)
                    .getId()),
            "original UUID stays stable");
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
        NekoTradeEntry legacy = group().getTrades()
            .get(0);
        legacy.getToItems()
            .get(0)
            .setItem(GtmbProjectorCompat.OLD_ITEM_ID);
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
        applyAssetQueue();
        check(find(TRADE_ID) == null && find(CUSTOM_ID) != null, "without GTMB removes only old managed exchange");
        check(
            NekoTradeIntegrationAPI.GroupRecord.load(GtmbProjectorCompat.GROUP_ID) == null,
            "absent mod leaves no record");

        // GTIT postInit 只排队，GTMB 稍后完成注册；不能预先查出 null 物品写入记录。
        setPresence(true);
        NekoTradeIntegrationAPI.registerTradeAssetsFromJar("gtit");
        check(!NekoTradeIntegrationAPI.applyGroup(group()), "loaded mod without item refuses exchange");
        check(find(TRADE_ID) == null, "missing registry item creates no phantom entry");
        FMLMissingMappingsEvent.MissingMapping missing = mapping(GtmbProjectorCompat.OLD_ITEM_ID, 24000);
        GtmbProjectorCompat.remapMissingItems(event(missing));
        check(
            missing.getAction() == FMLMissingMappingsEvent.Action.DEFAULT,
            "missing target never requests illegal remap");
        register(projector, GtmbProjectorCompat.GTMB_ITEM_ID);
        // 真实旧 v2 记账/闪烁币条目在前置齐全后按版本更新为 v3。
        NekoTradeConfig.save(data);
        oldRecord.save();
        NekoTradeIntegrationAPI.applyQueuedGroups();
        assertTrade();
        check(!NekoTradeIntegrationAPI.applyGroup(group()), "same source version is idempotent");
        nativeRemap();

        NekoTradeEntry saved = find(TRADE_ID);
        NekoTradeConfig.NekoTradeData stale = new NekoTradeConfig.NekoTradeData();
        stale.getTrades()
            .add(saved);
        NekoCurrencyRegistrar.nekoCoinItem = null;
        NekoTradeRegistryV2.applySyncedTrades(stale);
        check(NekoTradeDatabase.INSTANCE.getTradeGroupCount() == 0, "missing coin cannot turn exchange free");
        NekoCurrencyRegistrar.nekoCoinItem = Items.gold_nugget;
        setPresence(false);
        NekoTradeRegistryV2.applySyncedTrades(stale);
        check(
            NekoTradeDatabase.INSTANCE.getTradeGroupCount() == 0,
            "first disk/sync load rejects stale optional exchange");
        applyAssetQueue();
        check(find(TRADE_ID) == null && find(CUSTOM_ID) != null, "unload clears managed exchange only");
        FMLMissingMappingsEvent.MissingMapping reverse = mapping(GtmbProjectorCompat.GTMB_ITEM_ID, 24000);
        GtmbProjectorCompat.remapMissingItems(event(reverse));
        check(reverse.getAction() == FMLMissingMappingsEvent.Action.DEFAULT, "no reverse GTMB to GTIT mapping");
        setPresence(true);
        applyAssetQueue();
        assertTrade();
        check(
            java.util.Arrays.equals(unrelatedRecord, Files.readAllBytes(unrelated.path())),
            "unrelated group record unchanged");

        NekoTradeIntegrationAPI.GroupRecord.load(GtmbProjectorCompat.GROUP_ID)
            .delete();
        setPresence(false);
        applyAssetQueue();
        check(
            find(TRADE_ID) == null && find(CUSTOM_ID) != null,
            "lost-record cleanup only targets stable managed UUID");
        System.out.println("GtmbProjectorCompatibilityTest passed");
    }

    private static NekoTradeGroupDef group() {
        return new Gson().fromJson(JarAssetManifest.readJsonResource(RESOURCE), NekoTradeGroupDef.class);
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

    private static void assertTrade() {
        NekoTradeGroup group = NekoTradeDatabase.INSTANCE
            .getTradeGroup(NekoTradeRegistryV2.parseTradeGroupId(TRADE_ID));
        check(
            group != null && group.getTrades()
                .size() == 1,
            "exactly one managed exchange");
        NekoTrade trade = group.getTrades()
            .get(0);
        check(
            trade.getFromItems()
                .size() == 1,
            "one paid input without duplicates");
        check(
            trade.getCurrencyCosts()
                .get("neko") == 1,
            "exactly one ordinary neko coin");
        check(
            !trade.getCurrencyCosts()
                .containsKey("shimmeringNeko"),
            "zero shimmering coin input");
        check(
            trade.getToItems()
                .size() == 1
                && trade.getToItems()
                    .get(0)
                    .getBaseStack()
                    .getItem() == projector,
            "output resolves to the actual GTMB registry item");
        check(
            trade.getToItems()
                .get(0)
                .getStackSize() == 1,
            "exactly one projector output");
        check(find(CUSTOM_ID) != null, "custom entry preserved");
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
