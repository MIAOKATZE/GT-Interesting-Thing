package com.miaokatze.gtit.trade;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

import com.miaokatze.gtit.config.DefaultContentConfig;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.preset.PresetFileTransaction;
import com.miaokatze.gtit.trade.preset.TradePresetLayout;

import cpw.mods.fml.common.Loader;

/** Real page/trade edit entry points and bounded journal recovery, confined to a temporary game root. */
public final class TradeEditTransactionTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(TradeEditTransactionTest.class, args)) return;
        Path root = Files.createTempDirectory("gtit-combined-edit-");
        Field config = Loader.class.getDeclaredField("canonicalConfigDir");
        config.setAccessible(true);
        Object previous = config.get(Loader.instance());
        Field defaults = DefaultContentConfig.class.getDeclaredField("tradeGroups");
        defaults.setAccessible(true);
        boolean previousDefaults = defaults.getBoolean(null);
        try {
            Files.createDirectories(root.resolve("config"));
            config.set(
                Loader.instance(),
                root.resolve("config")
                    .toFile());
            defaults.setBoolean(null, false);
            NekoTradeConfig.setDefaultTradesSuppressed(true);
            NekoPageConfig.NekoPageData pages = new NekoPageConfig.NekoPageData();
            pages.getPages()
                .add(new NekoPageEntry(1, "before", "minecraft:chest", 0, false));
            NekoTradeConfig.NekoTradeData trades = new NekoTradeConfig.NekoTradeData();
            NekoTradeEntry entry = new NekoTradeEntry();
            entry.setId("combined-edit");
            entry.setTabId(1);
            trades.getTrades()
                .add(entry);
            NekoPageConfig.saveWithTrades(pages, trades);
            Path pageFile = root.resolve("config/gtit/trade/pages.json");
            Path tradeFile = root.resolve("config/gtit/trade/trades/tab_1.json");
            byte[] pageBefore = Files.readAllBytes(pageFile), tradeBefore = Files.readAllBytes(tradeFile);
            Path blockedReceiptParent = root.resolve("config/gtit/trade/integrated");
            Files.write(blockedReceiptParent, new byte[] { 9 });
            pages.getPages()
                .get(0)
                .setName("receipt-must-not-commit");
            boolean receiptFailed = false;
            try {
                NekoPageConfig.saveWithTrades(
                    pages,
                    trades,
                    java.util.Collections.singletonMap(1, 77),
                    java.util.Collections
                        .singletonMap("config/gtit/trade/integrated/combined-edit.json", new byte[] { 3 }));
            } catch (RuntimeException expected) {
                receiptFailed = true;
            }
            check(
                receiptFailed && Arrays.equals(pageBefore, Files.readAllBytes(pageFile))
                    && Arrays.equals(tradeBefore, Files.readAllBytes(tradeFile))
                    && !Files.exists(root.resolve("config/gtit/trade/order_watermark.json")),
                "late ownership receipt failure rolls back page/trade/watermark as one edit");
            Files.delete(blockedReceiptParent);
            pages.getPages()
                .get(0)
                .setName("before");
            Path managed = root.resolve(TradePresetLayout.PAGES_PATH);
            Files.createDirectories(managed.getParent());
            Files.createDirectory(managed);
            pages.getPages()
                .get(0)
                .setName("after");
            boolean failed = false;
            try {
                NekoPageConfig.saveWithTrades(pages, trades);
            } catch (RuntimeException expected) {
                failed = true;
            }
            check(
                failed && Arrays.equals(pageBefore, Files.readAllBytes(pageFile))
                    && Arrays.equals(tradeBefore, Files.readAllBytes(tradeFile)),
                "later page preparation failure preserves both earlier files");
            Files.delete(managed);
            NekoPageRegistry.applySyncedPages(NekoPageConfig.load());
            long version = NekoPageRegistry.getVersion();
            Path blocked = root.resolve("config/gtit/trade/trades/tab_3.json");
            Files.createDirectory(blocked);
            failed = false;
            try {
                NekoPageRegistry.deletePage(1);
            } catch (RuntimeException expected) {
                failed = true;
            }
            check(
                failed && NekoPageRegistry.hasPage(1)
                    && NekoPageRegistry.getVersion() == version
                    && Arrays.equals(pageBefore, Files.readAllBytes(pageFile))
                    && Arrays.equals(tradeBefore, Files.readAllBytes(tradeFile)),
                "failed delete publishes neither disk metadata nor the in-memory page/version");
            Files.delete(blocked);
            NekoPageRegistry.deletePage(1);
            check(
                !NekoPageRegistry.hasPage(1) && !Files.exists(tradeFile)
                    && NekoTradeConfig.load()
                        .getTrades()
                        .get(0)
                        .getTabId() == 3,
                "successful delete commits page removal and relocated trade together");
            Path first = root.resolve("MiaoTrade/first.json"), second = root.resolve("MiaoTrade/second.json");
            Files.createDirectories(first.getParent());
            Files.write(first, new byte[] { 1 });
            Files.write(second, new byte[] { 2 });
            Path journal = root.resolve("config/gtit/trade/preset_transaction.json");
            String recovery = "{\"before\":{\"MiaoTrade/first.json\":\"!invalid-base64!\",\"MiaoTrade/second.json\":\""
                + Base64.getEncoder()
                    .encodeToString(new byte[] { 3 })
                + "\"}}";
            Files.write(journal, recovery.getBytes(StandardCharsets.UTF_8));
            failed = false;
            try {
                new PresetFileTransaction(root).recover();
            } catch (java.io.IOException expected) {
                failed = true;
            }
            check(
                failed && Arrays.equals(new byte[] { 1 }, Files.readAllBytes(first))
                    && Arrays.equals(new byte[] { 2 }, Files.readAllBytes(second))
                    && Files.exists(journal),
                "all journal payloads are validated before any restoration; damaged journal stays available for repair");
            Files.delete(journal);
            NekoTradeConfig.raiseOrderWatermark(3, 500);
            check(
                Files.readAllBytes(root.resolve("config/gtit/trade/order_watermark.json")).length > 0,
                "watermark uses the same allowed transaction path");
            com.miaokatze.gtit.trade.api.NekoTradeGroupDef queued = new com.miaokatze.gtit.trade.api.NekoTradeGroupDef(
                "queue-snapshot",
                1);
            com.miaokatze.gtit.trade.api.NekoTradeIntegrationAPI.registerTradeGroup(queued);
            queued.setVersion(9);
            Field pending = com.miaokatze.gtit.trade.api.NekoTradeIntegrationAPI.class.getDeclaredField("PENDING");
            pending.setAccessible(true);
            java.util.Queue<?> queue = (java.util.Queue<?>) pending.get(null);
            Object copied = queue.poll();
            check(
                copied instanceof com.miaokatze.gtit.trade.api.NekoTradeGroupDef
                    && ((com.miaokatze.gtit.trade.api.NekoTradeGroupDef) copied).getVersion() == 1,
                "later caller mutation cannot change queued registration snapshot");
            System.out.println(
                "TradeEditTransactionTest: PASS (combined edit, preflight failure, memory publication, delete/move, bad journal, watermark)");
        } finally {
            config.set(Loader.instance(), previous);
            defaults.setBoolean(null, previousDefaults);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
