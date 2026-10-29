package com.miaokatze.gtit.rewards;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import com.miaokatze.gtit.mail.BlessingConfig;
import com.miaokatze.gtit.signin.DailySignInConfig;
import com.miaokatze.gtit.signin.OnlineTimeConfig;
import com.miaokatze.gtit.signin.SignInReward;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

/** File-writing mains require the build's explicit isolated scratch opt-in. */
public final class RewardConfigPersistenceTest {

    private static final Path DAILY = Paths.get("config/gtit/signin/daily_signin.json");
    private static final Path ONLINE = Paths.get("config/gtit/signin/online_time_config.json");
    private static final Path BLESSING = Paths.get("config/gtit/mail/blessing_config.json");

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("gtit.rewards.testScratch"))
            throw new IllegalStateException("Run only in an isolated scratch working directory with -Dgtit.rewards.testScratch=true");
        if (!MinecraftTestBootstrap.enter(RewardConfigPersistenceTest.class, args)) return;
        int failed = 0;
        for (String name : new String[] { "missingFilesCreateDefaults", "dailyMalformedFileIsPreservedAndDisabled",
            "onlineMalformedFileIsPreservedAndDisabled", "blessingMalformedFileIsPreservedAndDisabled",
            "dailyNullRootIsNotDefaults", "onlineNullRootIsNotDefaults", "blessingNullRootIsNotDefaults",
            "explicitEmptyTiersRemainDisabled", "dailyFailedReplacementIsVisible", "onlineFailedReplacementIsVisible",
            "blessingFailedReplacementIsVisible", "blessingMissingConfiguredItemCannotConsumeReward", "dailyMalformedNestedItemsCannotBecomeEmptyReward",
            "rewardAmountsCannotWrapOrTruncate", "editorRejectsPresentMalformedReward" }) {
            try { RewardConfigPersistenceTest.class.getDeclaredMethod(name).invoke(null); System.out.println("PASS " + name); }
            catch (ReflectiveOperationException failure) { failed++; System.err.println("FAIL " + name + ": " + failure.getCause()); }
        }
        if (failed > 0) throw new AssertionError(failed + " reward configuration regressions failed");
    }

    private static void missingFilesCreateDefaults() throws Exception {
        Files.deleteIfExists(DAILY); Files.deleteIfExists(ONLINE); Files.deleteIfExists(BLESSING);
        DailySignInConfig.loadConfig(); OnlineTimeConfig.loadConfig(); BlessingConfig.loadConfig();
        check(Files.exists(DAILY) && Files.exists(ONLINE) && Files.exists(BLESSING), "First use creates all three defaults");
        check(!DailySignInConfig.getRewardTiers().isEmpty() && !OnlineTimeConfig.getTiers().isEmpty()
            && !BlessingConfig.getFestivals().isEmpty(), "Missing source has intended default rewards");
    }
    private static byte[] corrupt(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent()); Files.write(path, text.getBytes(StandardCharsets.UTF_8)); return Files.readAllBytes(path);
    }
    private static void preserved(Path path, byte[] bytes) throws Exception { check(Arrays.equals(bytes, Files.readAllBytes(path)), "Existing invalid bytes must remain untouched"); }
    private static void dailyMalformedFileIsPreservedAndDisabled() throws Exception {
        byte[] before = corrupt(DAILY, "{ broken: ["); preservingRejectedLoad(DAILY, before, DailySignInConfig::loadConfig);
        rejects(() -> DailySignInConfig.getEffectiveDayReward("2026-10-29"));
        rejects(() -> DailySignInConfig.setMonthlyGlobal(false, 0, SignInReward.EMPTY, SignInReward.EMPTY));
        rejects(DailySignInConfig::saveConfig); preserved(DAILY, before);
        corrupt(DAILY, "{\"version\":2,\"weekday_default\":{\"currency\":\"neko\",\"amount\":73},\"tiers\":[],\"cumulative_tiers\":[]}");
        DailySignInConfig.loadConfig(); check(DailySignInConfig.getWeekdayDefault().getCurrencyAmount() == 73, "Explicit repaired reload restores authority");
    }
    private static void onlineMalformedFileIsPreservedAndDisabled() throws Exception {
        byte[] before = corrupt(ONLINE, "{ broken: ["); preservingRejectedLoad(ONLINE, before, OnlineTimeConfig::loadConfig);
        rejects(OnlineTimeConfig::getTiers); rejects(() -> OnlineTimeConfig.setTiers(Collections.emptyList()));
        rejects(OnlineTimeConfig::saveConfig); preserved(ONLINE, before);
        corrupt(ONLINE, "{\"tiers\":[]}"); OnlineTimeConfig.loadConfig();
    }
    private static void blessingMalformedFileIsPreservedAndDisabled() throws Exception {
        byte[] before = corrupt(BLESSING, "{ broken: ["); preservingRejectedLoad(BLESSING, before, BlessingConfig::loadConfig);
        rejects(BlessingConfig::getFestivals); rejects(() -> BlessingConfig.setSender("overwrite"));
        rejects(BlessingConfig::saveConfig); preserved(BLESSING, before);
        corrupt(BLESSING, "{\"sender\":\"repaired\",\"festivals\":[]}"); BlessingConfig.loadConfig();
        check("repaired".equals(BlessingConfig.getSender()), "Explicit repaired reload restores blessing authority");
    }
    private static void dailyNullRootIsNotDefaults() throws Exception { byte[] bytes = corrupt(DAILY, "null"); preservingRejectedLoad(DAILY, bytes, DailySignInConfig::loadConfig); }
    private static void onlineNullRootIsNotDefaults() throws Exception { byte[] bytes = corrupt(ONLINE, "null"); preservingRejectedLoad(ONLINE, bytes, OnlineTimeConfig::loadConfig); }
    private static void blessingNullRootIsNotDefaults() throws Exception { byte[] bytes = corrupt(BLESSING, "null"); preservingRejectedLoad(BLESSING, bytes, BlessingConfig::loadConfig); }
    private static void explicitEmptyTiersRemainDisabled() throws Exception {
        corrupt(DAILY, "{\"version\":2,\"tiers\":[],\"cumulative_tiers\":[]}"); DailySignInConfig.loadConfig(); DailySignInConfig.saveConfig(); DailySignInConfig.loadConfig();
        corrupt(ONLINE, "{\"tiers\":[]}"); OnlineTimeConfig.loadConfig(); OnlineTimeConfig.saveConfig(); OnlineTimeConfig.loadConfig();
        check(DailySignInConfig.getRewardTiers().isEmpty() && DailySignInConfig.getCumulativeTiers().isEmpty()
            && OnlineTimeConfig.getTiers().isEmpty(), "Explicit empty arrays must not silently reactivate defaults");
    }
    private static void replacementFailure(Path path, Action load, Action save, Action query) throws Exception {
        corrupt(path, path.equals(DAILY) ? "{\"version\":2,\"tiers\":[]}" : path.equals(ONLINE) ? "{\"tiers\":[]}" : "{\"festivals\":[]}"); load.run();
        byte[] before = Files.readAllBytes(path); Files.delete(path); Files.createDirectory(path); Files.write(path.resolve("keep"), before);
        try { rejects(save); rejects(query); check(Arrays.equals(before, Files.readAllBytes(path.resolve("keep"))), "Failed replacement preserves existing target"); }
        finally { Files.delete(path.resolve("keep")); Files.delete(path); Files.write(path, before); load.run(); }
    }
    private static void dailyFailedReplacementIsVisible() throws Exception { replacementFailure(DAILY, DailySignInConfig::loadConfig, DailySignInConfig::saveConfig, DailySignInConfig::getWeekdayDefault); }
    private static void onlineFailedReplacementIsVisible() throws Exception { replacementFailure(ONLINE, OnlineTimeConfig::loadConfig, OnlineTimeConfig::saveConfig, OnlineTimeConfig::getTiers); }
    private static void blessingFailedReplacementIsVisible() throws Exception { replacementFailure(BLESSING, BlessingConfig::loadConfig, BlessingConfig::saveConfig, BlessingConfig::getFestivals); }
    private static void blessingMissingConfiguredItemCannotConsumeReward() {
        BlessingConfig.BirthdayBlessing birthday = new BlessingConfig.BirthdayBlessing();
        birthday.items.add(new BlessingConfig.BlessingItem("missing_mod:owed", 0, 3));
        rejects(birthday::buildAttachments);
    }
    private static void dailyMalformedNestedItemsCannotBecomeEmptyReward() throws Exception {
        byte[] bytes = corrupt(DAILY, "{\"version\":2,\"weekday_default\":{\"items\":\"owed-items\"},\"tiers\":[]}");
        preservingRejectedLoad(DAILY, bytes, DailySignInConfig::loadConfig); rejects(DailySignInConfig::getWeekdayDefault);
        com.google.gson.JsonObject reward = new com.google.gson.JsonParser().parse("{\"items\":[\"owed-item\"]}").getAsJsonObject();
        rejects(() -> SignInReward.fromJson(reward));
    }
    private static void rewardAmountsCannotWrapOrTruncate() {
        for (String amount : new String[] { "2147483648", "4294967297", "1.5", "-1" }) {
            com.google.gson.JsonObject reward = new com.google.gson.JsonParser().parse("{\"currency\":\"neko\",\"amount\":" + amount + "}").getAsJsonObject();
            rejects(() -> SignInReward.fromJson(reward));
            com.google.gson.JsonObject item = new com.google.gson.JsonParser().parse("{\"item\":\"minecraft:diamond\",\"amount\":" + amount + "}").getAsJsonObject();
            rejects(() -> com.miaokatze.gtit.signin.RewardItem.fromJson(item));
        }
    }
    private static void editorRejectsPresentMalformedReward() throws Exception {
        Class<?> editor = Class.forName("com.miaokatze.gtit.trade.v2.SignInEditActions");
        java.lang.reflect.Method parse = editor.getDeclaredMethod("parseReward", com.google.gson.JsonObject.class, String.class);
        parse.setAccessible(true);
        com.google.gson.JsonObject malformed = new com.google.gson.JsonParser().parse("{\"reward\":\"owed-items\"}").getAsJsonObject();
        rejects(() -> parse.invoke(null, malformed, "reward"));
        check(parse.invoke(null, new com.google.gson.JsonObject(), "reward") == null, "Absent optional editor reward remains no-change");
        com.google.gson.JsonObject empty = new com.google.gson.JsonParser().parse("{\"reward\":{\"items\":[]}}").getAsJsonObject();
        check(!((SignInReward) parse.invoke(null, empty, "reward")).hasItems(), "Explicit empty editor reward remains intentional");
    }
    private static void preservingRejectedLoad(Path path, byte[] before, Action load) throws Exception {
        boolean rejected = false;
        try { load.run(); } catch (Exception expected) { rejected = true; }
        preserved(path, before);
        check(rejected, "Invalid existing configuration must report its unavailable state");
    }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action) { try { action.run(); } catch (Exception expected) { return; } throw new AssertionError("Expected explicit unavailable/error result"); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
