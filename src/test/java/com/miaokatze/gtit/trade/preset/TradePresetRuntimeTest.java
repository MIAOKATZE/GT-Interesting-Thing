package com.miaokatze.gtit.trade.preset;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

import com.miaokatze.gtit.config.DefaultContentConfig;
import com.miaokatze.gtit.gui.vm.StatusCodec;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;

/** Actual file/config/registry pipeline, launched by StoreTest in a separate temporary working directory. */
public final class TradePresetRuntimeTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(TradePresetRuntimeTest.class, args)) return;
        if (!Files.exists(Paths.get("preset-runtime-fixture.marker"))) {
            throw new IllegalStateException("Runtime test requires its isolated StoreTest fixture");
        }
        flags(true);
        NekoTradeConfig.setDefaultTradesSuppressed(true);
        int pageId = TradePresetLayout.pageId("gtit-base", 1);
        NekoPageConfig.NekoPageData pages = new NekoPageConfig.NekoPageData();
        NekoPageEntry userPage = new NekoPageEntry(1000, "user", "minecraft:stone", 0, false);
        NekoPageEntry presetPage = new NekoPageEntry(pageId, "default", "minecraft:stone", 0, false);
        presetPage.setPresetBuiltin(true);
        pages.setPages(Arrays.asList(userPage, presetPage));
        NekoPageConfig.save(pages);
        check(!read("config/gtit/trade/pages.json").contains(Integer.toString(pageId)), "page write isolation");
        check(
            NekoPageConfig.load()
                .getPages()
                .size()
                == DefaultTradeSources.pages()
                    .size() + 1,
            "page merge");

        NekoTradeEntry user = entry(
            UUID.randomUUID()
                .toString(),
            1000,
            false);
        String id = TradePresetLayout.tradeId("gtit-base", "source-stable-id");
        NekoTradeEntry preset = entry(id, pageId, true);
        NekoTradeConfig.NekoTradeData trades = new NekoTradeConfig.NekoTradeData();
        trades.setTrades(Arrays.asList(user, preset));
        NekoTradeConfig.save(trades);
        check(
            Files.exists(Paths.get("config/gtit/trade/default_overrides/gtit-base.json"))
                && !Files.exists(Paths.get(TradePresetLayout.tabPath(pageId)))
                && !Files.exists(Paths.get("config/gtit/trade/trades/tab_" + pageId + ".json")),
            "trade write isolation");
        check(
            NekoTradeConfig.load()
                .getTrades()
                .size() == 2,
            "trade merge");
        check(NekoTradeRegistryV2.reload(), "registry reload");
        UUID runtimeId = NekoTradeRegistryV2.parseTradeGroupId(id);
        check(
            NekoPageRegistry.hasPage(pageId) && NekoTradeDatabase.INSTANCE.getTradeGroup(runtimeId)
                .getCategory()
                .getTabId() == pageId,
            "negative page is a real visible trade category");
        check(NekoTradeRegistryV2.idMatchesRuntimeUuid(id, runtimeId.toString()), "default editor ID routing");
        long version = NekoTradeDatabase.INSTANCE.getVersion();
        preset.getToItems()
            .get(0)
            .setAmount(17);
        NekoTradeConfig.save(trades);
        check(
            NekoTradeRegistryV2.reload() && NekoTradeDatabase.INSTANCE.getVersion() > version
                && NekoTradeDatabase.INSTANCE.getTradeGroup(runtimeId)
                    .getTrades()
                    .get(0)
                    .getToItems()
                    .get(0)
                    .getStackSize() == 17,
            "loaded registry hot replacement");

        java.lang.reflect.Method cleanup = com.miaokatze.gtit.trade.api.NekoTradeIntegrationAPI.class
            .getDeclaredMethod("removeDefaultEntriesByMiaoPrefix");
        cleanup.setAccessible(true);
        cleanup.invoke(null);
        check(
            NekoTradeConfig.load()
                .getTrades()
                .stream()
                .anyMatch(value -> id.equals(value.getId())),
            "bundled restore never removes preset namespace");

        byte[] defaultBytes = Files.readAllBytes(Paths.get("config/gtit/trade/default_overrides/gtit-base.json"));
        flags(false);
        check(
            NekoTradeRegistryV2.reload() && !NekoPageRegistry.hasPage(pageId)
                && NekoTradeDatabase.INSTANCE.getTradeGroup(runtimeId) == null
                && NekoTradeDatabase.INSTANCE.getTradeGroupCount() == 1,
            "default switch hides only default groups");
        check(
            Arrays.equals(
                defaultBytes,
                Files.readAllBytes(Paths.get("config/gtit/trade/default_overrides/gtit-base.json"))),
            "disabled defaults remain on disk");
        flags(true);
        check(
            NekoTradeRegistryV2.reload() && NekoPageRegistry.hasPage(pageId)
                && NekoTradeDatabase.INSTANCE.getTradeGroup(runtimeId) != null,
            "reenable keeps stable identity");
        Files.delete(Paths.get(TradePresetLayout.tabPath(1000)));
        check(
            NekoTradeConfig.load()
                .getTrades()
                .size() == 1
                && NekoTradeConfig.load()
                    .getTrades()
                    .get(0)
                    .getTabId() == pageId,
            "managed trades load without any user tab file");

        Path userPages = Paths.get("config/gtit/trade/pages.json");
        Files.delete(userPages);
        check(
            NekoPageConfig.load()
                .getPages()
                .stream()
                .anyMatch(value -> value.getId() == pageId),
            "missing user pages merge");
        NekoPageConfig.init();
        check(
            NekoPageConfig.load()
                .getPages()
                .stream()
                .anyMatch(value -> value.getId() == pageId),
            "init preserves managed pages");
        Files.write(userPages, "{\"pages\":[]}".getBytes(StandardCharsets.UTF_8));
        check(
            NekoPageConfig.load()
                .getPages()
                .stream()
                .anyMatch(value -> value.getId() == pageId),
            "empty user pages merge");
        Files.delete(userPages);
        Path legacy = Paths.get("config/gtit/nekovm_pages.json");
        Files.write(legacy, "{\"pages\":[{\"id\":123,\"name\":\"legacy\"}]}".getBytes(StandardCharsets.UTF_8));
        check(
            NekoPageConfig.load()
                .getPages()
                .stream()
                .anyMatch(value -> value.getId() == pageId),
            "legacy page migration merges presets");

        NekoPageConfig.NekoPageData customPages = NekoPageConfig.load();
        NekoPageEntry customPresetPage = new NekoPageEntry(
            -600001,
            "custom imported preset",
            "minecraft:stone",
            0,
            false);
        customPresetPage.setSourcePresetId("fixture-custom-preset");
        customPages.getPages()
            .add(customPresetPage);
        NekoPageConfig.save(customPages);
        NekoTradeConfig.NekoTradeData withCustom = NekoTradeConfig.load();
        NekoTradeEntry customPresetTrade = entry("MIAO900123456789", -600001, false);
        withCustom.getTrades()
            .add(customPresetTrade);
        NekoTradeConfig.save(withCustom);
        flags(false);
        TradePresetDefinition configurableCustom = new TradePresetDefinition();
        configurableCustom.id = "fixture-custom-preset";
        TradePresetManager.validateAvailable(configurableCustom);
        check(
            NekoTradeRegistryV2.reload() && NekoPageRegistry.hasPage(-600001)
                && !NekoPageRegistry.hasPage(pageId)
                && NekoTradeDatabase.INSTANCE
                    .getTradeGroup(NekoTradeRegistryV2.parseTradeGroupId(customPresetTrade.getId())) != null
                && NekoTradeDatabase.INSTANCE.getTradeGroup(runtimeId) == null,
            "disabled defaults preserve imported custom preset category and trade");
        flags(true);

        StatusCodec codec = new StatusCodec(null, null, () -> {});
        NekoTradeGroup oneTimeGroup = NekoTradeDatabase.INSTANCE
            .getTradeGroup(NekoTradeRegistryV2.parseTradeGroupId(customPresetTrade.getId()));
        oneTimeGroup.setCooldown(-1);
        java.lang.reflect.Method bindHistory = com.miaokatze.gtit.trade.v2.NekoHistoryManager.class
            .getDeclaredMethod("bindSaveDirectory", java.io.File.class);
        bindHistory.setAccessible(true);
        bindHistory
            .invoke(com.miaokatze.gtit.trade.v2.NekoHistoryManager.INSTANCE, new java.io.File("history-fixture"));
        UUID statusPlayer = UUID.randomUUID();
        com.miaokatze.gtit.trade.v2.NekoTradeHistory oneTimeHistory = com.miaokatze.gtit.trade.v2.NekoHistoryManager.INSTANCE
            .getHistory(statusPlayer, oneTimeGroup.getId());
        java.lang.reflect.Method buildCooldown = StatusCodec.class
            .getDeclaredMethod("buildCooldownStatusString", UUID.class);
        java.lang.reflect.Method parseCooldown = StatusCodec.class
            .getDeclaredMethod("parseCooldownStatus", String.class);
        buildCooldown.setAccessible(true);
        parseCooldown.setAccessible(true);
        String oneTimeKey = oneTimeGroup.getId() + ":0";
        parseCooldown.invoke(codec, buildCooldown.invoke(codec, statusPlayer));
        check(!codec.isOneTimeRedeemed(oneTimeKey), "unredeemed one-time status is independent of affordability");
        oneTimeHistory.recordTrade(-1);
        parseCooldown.invoke(codec, buildCooldown.invoke(codec, statusPlayer));
        check(
            codec.isOneTimeRedeemed(oneTimeKey) && codec.getCooldownRemaining(oneTimeKey) == 0,
            "redeemed one-time state synchronizes without negative countdown seconds");
        oneTimeHistory.reset();
        parseCooldown.invoke(codec, buildCooldown.invoke(codec, statusPlayer));
        check(!codec.isOneTimeRedeemed(oneTimeKey), "explicit history reset clears synchronized one-time marker");
        set(codec, "cachedTradeableStatusString", "stale removed trade");
        set(codec, "tradeableStatusDirty", false);
        set(codec, "lastTradeRegistryVersion", NekoTradeDatabase.INSTANCE.getVersion());
        NekoTradeDatabase.INSTANCE.clear();
        java.lang.reflect.Method status = StatusCodec.class.getDeclaredMethod("buildTradeableStatusString", UUID.class);
        status.setAccessible(true);
        check(
            "".equals(status.invoke(codec, UUID.randomUUID())),
            "open container status cache invalidates immediately");
        com.miaokatze.gtit.gui.vm.TradeWidgetEditModeTest.run();
        com.miaokatze.gtit.trade.v2.NekoHistoryManager.INSTANCE.unloadAll();
        System.out.println("TradePresetRuntimeTest: 22 checks passed");
    }

    private static NekoTradeEntry entry(String id, int page, boolean defaults) {
        NekoTradeEntry result = new NekoTradeEntry();
        result.setId(id);
        result.setTabId(page);
        result.setDefaultEntry(defaults);
        result.setPresetBuiltin(defaults);
        result.getToItems()
            .add(new NekoTradeEntry.ItemEntry("minecraft:stone", 0, 1));
        return result;
    }

    private static void flags(boolean enabled) throws Exception {
        Path config = Paths.get("config/gtit/default_content.cfg");
        Files.createDirectories(config.getParent());
        Files.write(
            config,
            ("defaults {\nB:loadDefaultTradeGroups=" + enabled + "\n}\n").getBytes(StandardCharsets.UTF_8));
        DefaultContentConfig.init(
            Paths.get("config")
                .toFile());
    }

    private static void set(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }
}
