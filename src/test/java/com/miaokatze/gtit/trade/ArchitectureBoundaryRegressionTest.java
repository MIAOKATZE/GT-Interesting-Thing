package com.miaokatze.gtit.trade;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataRegistry;
import com.gtnewhorizon.gtnhlib.teams.TeamDataSaver;
import com.miaokatze.gtit.common.items.pocket.magnet.PocketMagnetClaims;
import com.miaokatze.gtit.config.DefaultContentConfig;
import com.miaokatze.gtit.config.MuteConfig;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.preset.PresetFileTransaction;
import com.miaokatze.gtit.trade.v2.NekoFavouritesTracker;
import com.miaokatze.gtit.util.PlayerResolver;

/** Real NBT, GTNHLib loader and disk operations, confined to a dedicated test working directory. */
public final class ArchitectureBoundaryRegressionTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(ArchitectureBoundaryRegressionTest.class, args)) return;
        check(Boolean.getBoolean("gtit.architecture.testScratch"), "isolated test working directory required");
        damagedTeamDatumSurvivesNativeLoader();
        partialTradeReadCannotDeleteAnotherTab();
        transactionRollsBackAnEarlierReplacement();
        favouritesFollowWorldAndRetainFailedSaves();
        orphanMagnetClaimsExpireWithoutCarrierTicks();
        muteUsesJsonBooleans();
        check(PlayerResolver.getUserCacheFile() == MinecraftServer.field_152367_a, "native cache file identity");
        System.out.println("[ArchitectureBoundaryRegressionTest] 7/7 passed");
    }

    private static void damagedTeamDatumSurvivesNativeLoader() throws Exception {
        TeamDataRegistry.register(NekoTeamData.ID, NekoTeamData::new);
        Constructor<TeamDataSaver> constructor = TeamDataSaver.class.getDeclaredConstructor(File.class);
        constructor.setAccessible(true);
        TeamDataSaver saver = constructor.newInstance(
            Files.createTempDirectory("gtit-team-")
                .toFile());
        Method load = TeamDataSaver.class.getDeclaredMethod("loadFromNBT", NBTTagCompound.class);
        Method write = TeamDataSaver.class.getDeclaredMethod("writeToNBT", Team.class);
        load.setAccessible(true);
        write.setAccessible(true);
        NBTTagCompound damaged = new NBTTagCompound();
        damaged.setString("wallet", "damaged compound");
        damaged.setString("repair-note", "keep unknown fields too");
        NBTTagCompound allData = new NBTTagCompound();
        allData.setTag(NekoTeamData.ID, damaged);
        NBTTagCompound outer = new NBTTagCompound();
        outer.setString("TeamName", "recovery");
        outer.setString(
            "UUID",
            UUID.randomUUID()
                .toString());
        outer.setTag("TeamData", allData);
        Team team = (Team) load.invoke(saver, outer);
        NekoTeamData data = (NekoTeamData) team.getData(NekoTeamData.ID);
        check(data != null, "native loader must retain rejected datum");
        rejects(data::getWallet, "damaged team cannot use an empty wallet");
        rejects(() -> data.getTradeHistory(UUID.randomUUID()), "damaged team cannot redeem fresh history");
        NBTTagCompound saved = (NBTTagCompound) write.invoke(saver, team);
        check(
            damaged.equals(
                saved.getCompoundTag("TeamData")
                    .getCompoundTag(NekoTeamData.ID)),
            "native team save preserves raw rejected NBT");
        NBTTagCompound repaired = (NBTTagCompound) damaged.copy();
        repaired.setTag("wallet", new NBTTagCompound());
        data.readFromNBT(repaired);
        check(data.getWallet() != null, "valid repair restores service");
    }

    private static void partialTradeReadCannotDeleteAnotherTab() throws Exception {
        Field defaults = DefaultContentConfig.class.getDeclaredField("tradeGroups");
        defaults.setAccessible(true);
        defaults.setBoolean(null, false);
        NekoTradeConfig.setDefaultTradesSuppressed(true);
        Path directory = Paths.get("config/gtit/trade/trades");
        Files.createDirectories(directory);
        Path first = directory.resolve("tab_1.json");
        Path second = directory.resolve("tab_2.json");
        byte[] good = ("{\"version\":1,\"tabId\":1,\"trades\":[{\"id\":\"" + UUID.randomUUID()
            + "\",\"tabId\":1,\"cooldown\":0}]}").getBytes(StandardCharsets.UTF_8);
        byte[] bad = "{invalid json".getBytes(StandardCharsets.UTF_8);
        Files.write(first, good);
        Files.write(second, bad);
        NekoTradeConfig.NekoTradeData partial = NekoTradeConfig.load();
        check(
            !partial.isComplete() && partial.getLoadFailures()
                .size() == 1,
            "bad source is explicit");
        rejects(() -> NekoTradeConfig.save(partial), "partial load cannot be saved");
        check(
            Arrays.equals(good, Files.readAllBytes(first)) && Arrays.equals(bad, Files.readAllBytes(second)),
            "failed edit preserves both source files");
        Files.write(second, "{\"tabId\":2,\"trades\":[]}".getBytes(StandardCharsets.UTF_8));
        NekoTradeConfig.NekoTradeData complete = NekoTradeConfig.load();
        check(complete.isComplete(), "repair allows full read");
        NekoTradeConfig.save(complete);
        check(!Files.exists(second), "legitimate empty-tab removal still commits");
        Path blocked = directory.resolve("tab_3.json");
        Files.createDirectory(blocked);
        NekoTradeEntry extra = new NekoTradeEntry();
        extra.setId(
            UUID.randomUUID()
                .toString());
        extra.setTabId(3);
        complete.getTrades()
            .add(extra);
        rejects(() -> NekoTradeConfig.save(complete), "disk failure propagates to caller before success ACK");
        Files.delete(blocked);
    }

    private static void transactionRollsBackAnEarlierReplacement() throws Exception {
        Path first = Paths.get("config/gtit/trade/trades/tab_1.json");
        byte[] before = Files.readAllBytes(first);
        Path blockedParent = Paths.get("config/gtit/trade/preset_tabs");
        Files.write(blockedParent, new byte[] { 1 });
        Map<String, byte[]> changes = new LinkedHashMap<>();
        changes.put("config/gtit/trade/trades/tab_1.json", new byte[] { 2 });
        changes.put("config/gtit/trade/preset_tabs/tab_-100001.json", new byte[] { 3 });
        try {
            new PresetFileTransaction(Paths.get("")).commit(changes);
            throw new AssertionError("later write must fail");
        } catch (java.io.IOException expected) {
            check(Arrays.equals(before, Files.readAllBytes(first)), "first replacement rolled back");
            check(
                !Files.exists(Paths.get("config/gtit/trade/preset_transaction.json")),
                "successful rollback closes journal");
        } finally {
            Files.delete(blockedParent);
        }
    }

    private static void favouritesFollowWorldAndRetainFailedSaves() throws Exception {
        NekoFavouritesTracker tracker = NekoFavouritesTracker.INSTANCE;
        Method bind = NekoFavouritesTracker.class.getDeclaredMethod("bindDirectory", File.class);
        bind.setAccessible(true);
        Path a = Files.createTempDirectory("gtit-favourites-a-");
        Path b = Files.createTempDirectory("gtit-favourites-b-");
        UUID player = UUID.randomUUID(), group = UUID.randomUUID();
        bind.invoke(tracker, a.toFile());
        tracker.addFavourite(player, group, 0);
        bind.invoke(tracker, b.toFile());
        check(!tracker.isFavourite(player, group, 0), "B does not inherit A cache");
        bind.invoke(tracker, a.toFile());
        check(tracker.isFavourite(player, group, 0), "A reloads its own favourites");
        Path file = a.resolve(player + ".dat");
        Files.delete(file);
        Files.createDirectory(file);
        tracker.addFavourite(player, group, 1);
        tracker.unloadPlayer(player);
        check(tracker.isFavourite(player, group, 1), "failed logout save retains pending cache");
        try {
            bind.invoke(tracker, b.toFile());
            throw new AssertionError("unsaved old world must block rebind");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            check(expected.getCause() instanceof IllegalStateException, "rebind reports storage failure");
        }
        Files.delete(file);
        bind.invoke(tracker, b.toFile());
        check(!tracker.isFavourite(player, group, 1), "retry flushes A before entering B");
        bind.invoke(tracker, a.toFile());
        check(tracker.isFavourite(player, group, 1), "pending A edit survives retry");
        tracker.unloadAll();
        Files.write(file, new byte[] { 1, 2, 3 });
        bind.invoke(tracker, a.toFile());
        rejects(
            () -> tracker.toggleFavourite(player, group, 0),
            "damaged favourites cannot become an empty writable set");
        check(
            Arrays.equals(new byte[] { 1, 2, 3 }, Files.readAllBytes(file)),
            "damaged favourite source remains unchanged");
        tracker.unloadAll();
    }

    private static void orphanMagnetClaimsExpireWithoutCarrierTicks() {
        PocketMagnetClaims.reset();
        UUID player = UUID.randomUUID();
        ItemStack carrier = new ItemStack(Items.stick);
        ItemStack other = new ItemStack(Items.stick);
        ArrayList<PocketMagnetClaims.Claim> ripe = new ArrayList<>();
        check(PocketMagnetClaims.claim(player, 100, carrier, 2), "claim registered");
        PocketMagnetClaims.ripeClaims(player, other, ripe);
        PocketMagnetClaims.ripeClaims(player, other, ripe);
        check(PocketMagnetClaims.ripeClaims(player, carrier, ripe) == 0, "other pocket cannot accelerate hover");
        for (int i = 0; i < PocketMagnetClaims.CLAIM_STALE_TICKS; i++) PocketMagnetClaims.advanceTick();
        check(!PocketMagnetClaims.isClaimed(100), "no-carrier server ticks expire claim");
        PocketMagnetClaims.claim(player, 101, carrier, 2);
        PocketMagnetClaims.noteScanBackoff(player, 3);
        PocketMagnetClaims.forget(player);
        check(
            PocketMagnetClaims.liveClaimCount() == 0 && PocketMagnetClaims.scanBackoffLeft(player) == 0,
            "logout releases all runtime state");
        PocketMagnetClaims.claim(player, 102, carrier, 2);
        PocketMagnetClaims.reset();
        check(PocketMagnetClaims.liveClaimCount() == 0, "world shutdown releases claims");
    }

    private static void muteUsesJsonBooleans() throws Exception {
        Method parse = MuteConfig.class.getDeclaredMethod("parseConfig", String.class);
        parse.setAccessible(true);
        parse.invoke(null, "{ \"extra_mute\" : true,\n\"mute_machine_working_sounds\" : true }");
        check(MuteConfig.isExtraMute() && MuteConfig.isMuteMachineWorkingSounds(), "valid JSON whitespace");
        parse.invoke(null, "{\"extra_mute\":\"true\",\"other\":true}");
        check(
            !MuteConfig.isExtraMute() && !MuteConfig.isMuteMachineWorkingSounds(),
            "wrong type and missing key default false");
        parse.invoke(null, "{");
        check(
            !MuteConfig.isExtraMute() && !MuteConfig.isMuteMachineWorkingSounds(),
            "damaged document resets both switches");
    }

    private static void rejects(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
