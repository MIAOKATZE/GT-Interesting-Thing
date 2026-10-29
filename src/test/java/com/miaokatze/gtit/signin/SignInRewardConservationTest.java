package com.miaokatze.gtit.signin;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Collections;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.RewardDeliveryTestPlayer;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.util.NbtBase64Util;
import com.miaokatze.gtit.util.PlayerLookup;

/** Exact reward entitlement tests; all persistence and inventory work is actual production code. */
public final class SignInRewardConservationTest {

    private static final DailySignInManager MANAGER = DailySignInManager.INSTANCE;

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(SignInRewardConservationTest.class, args)) return;
        // These focused fixtures bind a temporary save directory instead of starting a server.
        try {
            Field ready = DailySignInManager.class.getDeclaredField("sessionUnavailable");
            ready.setAccessible(true);
            ready.setBoolean(DailySignInManager.INSTANCE, false);
        } catch (NoSuchFieldException legacyBaseline) { /* Pre-fence baseline comparison. */ }
        Field directory = DailySignInManager.class.getDeclaredField("saveDir");
        directory.setAccessible(true);
        directory.set(MANAGER, Files.createTempDirectory("gtit-signin-conservation-").toFile());
        Field walletDirectory = NekoWalletManager.class.getDeclaredField("saveDir");
        Field walletReady = NekoWalletManager.class.getDeclaredField("acceptingWallets");
        walletDirectory.setAccessible(true);
        walletReady.setAccessible(true);
        Object previousDirectory = walletDirectory.get(NekoWalletManager.INSTANCE);
        boolean previousReady = walletReady.getBoolean(NekoWalletManager.INSTANCE);
        walletDirectory.set(NekoWalletManager.INSTANCE, Files.createTempDirectory("gtit-signin-wallet-").toFile());
        walletReady.setBoolean(NekoWalletManager.INSTANCE, true);
        try {
        configure(new SignInReward("neko", 7, Collections.singletonList(item(256))));
        String[] cases = args.length > 0 && "baseline".equals(args[0])
            ? new String[] { "canceledDropRetainsOnlyRemainingQuantity", "malformedRewardsDoNotConsumeClaims", "corruptExistingFileIsNeverReplaced", "failedWorldSwitchNeverServesOldClaims" }
            : new String[] { "offlineRewardsSurviveLogoutAndReload", "canceledDropRetainsOnlyRemainingQuantity",
                "hugeConfiguredRewardsUseBoundedAttempts", "malformedRewardsDoNotConsumeClaims",
                "onlineRewardUsesSamePendingLedger", "corruptExistingFileIsNeverReplaced", "requestActorMustBeCurrentSession", "failedWorldSwitchNeverServesOldClaims" };
        int failed = 0;
        for (String name : cases) {
            try {
                SignInRewardConservationTest.class.getDeclaredMethod(name).invoke(null);
                System.out.println("PASS " + name);
            } catch (ReflectiveOperationException failure) {
                failed++;
                System.err.println("FAIL " + name + ": " + failure.getCause());
            }
        }
        if (failed != 0) throw new AssertionError(failed + " sign-in regressions failed");
        } finally {
            walletReady.setBoolean(NekoWalletManager.INSTANCE, previousReady);
            walletDirectory.set(NekoWalletManager.INSTANCE, previousDirectory);
        }

    }

    private static RewardItem item(int amount) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("receipt", "tagged-signin-reward");
        return new RewardItem("minecraft:iron_ingot", amount, 0, NbtBase64Util.nbtToBase64(tag));
    }

    private static void configure(SignInReward reward) throws Exception {
        set(DailySignInConfig.class, "weekdayDefault", reward);
        set(DailySignInConfig.class, "weekendDefault", reward);
        set(DailySignInConfig.class, "rewardTiers", Collections.emptyList());
        set(DailySignInConfig.class, "cumulativeTiers", Collections.emptyList());
        set(DailySignInConfig.class, "dayOverrides", Collections.emptyMap());
        set(DailySignInConfig.class, "incrementEnabled", false);
    }

    private static void offlineRewardsSurviveLogoutAndReload() throws Exception {
        UUID id = UUID.randomUUID();
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(id, false)) {
            actor.disconnect();
            check(MANAGER.signIn(id).getStatus() == DailySignInManager.SignInResult.Status.SUCCESS, "Offline entitlement is queued");
            check(pending(id) == 256, "Exact count queued");
            MANAGER.unloadSignInData(id);
            check(pending(id) == 256, "Count survives persisted reload");
            check(MANAGER.signIn(id).getStatus() == DailySignInManager.SignInResult.Status.ALREADY_SIGNED, "Repeated click does not regrant");
            actor.connect();
            deliver(id);
            check(pending(id) == 0 && inventoryCount(actor) == 256, "Login retry delivers exactly once");
            deliver(id);
            check(inventoryCount(actor) == 256, "Completed queue cannot replay");
        }
        MANAGER.unloadSignInData(id);
    }

    private static void canceledDropRetainsOnlyRemainingQuantity() throws Exception {
        UUID id = UUID.randomUUID();
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(id, false)) {
            fill(actor);
            ItemStack merge = new ItemStack(Items.iron_ingot, 63);
            merge.setTagCompound(NbtBase64Util.nbtFromBase64(item(1).getNbtBase64()));
            actor.player.inventory.mainInventory[0] = merge;
            MANAGER.signIn(id);
            check(actor.player.inventory.mainInventory[0].stackSize == 64 && pending(id) == 255, "Only inserted one item consumed");
            MANAGER.unloadSignInData(id);
            check(pending(id) == 255, "Rejected drop remainder survives logout/reload");
            actor.world.accept = true;
            deliver(id);
            check(pending(id) == 0 && actor.world.delivered == 255, "Retry grants only remainder");
            deliver(id);
            check(actor.world.delivered == 255, "No replay after completed retry");
        }
        MANAGER.unloadSignInData(id);
    }

    private static void hugeConfiguredRewardsUseBoundedAttempts() throws Exception {
        configure(new SignInReward("neko", 7, Collections.singletonList(item(Integer.MAX_VALUE))));
        UUID id = UUID.randomUUID();
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(id, true)) {
            fill(actor);
            MANAGER.signIn(id);
            check(actor.world.calls == 64 && actor.world.delivered == 4096, "One claim has 64 bounded legal drops");
            check(pending(id) == Integer.MAX_VALUE - 4096, "Huge remainder never narrows to a byte");
            MANAGER.unloadSignInData(id);
            check(pending(id) == Integer.MAX_VALUE - 4096, "Huge remainder reload is exact");
        }
        MANAGER.unloadSignInData(id);
        configure(new SignInReward("neko", 7, Collections.singletonList(item(256))));
    }

    private static void malformedRewardsDoNotConsumeClaims() throws Exception {
        for (RewardItem invalid : new RewardItem[] {
            new RewardItem("minecraft:missing_reward", 1, 0, ""),
            new RewardItem("minecraft:iron_ingot", 1, 0, "%%%") }) {
            configure(new SignInReward("neko", 7, Collections.singletonList(invalid)));
            UUID id = UUID.randomUUID();
            try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(id, false)) {
                int before = NekoWalletManager.INSTANCE.getWallet(id).getCount("neko");
                check(MANAGER.signIn(id).getStatus() == DailySignInManager.SignInResult.Status.ERROR, "Bad configured reward rejected");
                check(!MANAGER.getSignInData(id).hasSignedToday(DailySignInManager.getToday()), "Claim remains unused");
                check(pending(id) == 0 && NekoWalletManager.INSTANCE.getWallet(id).getCount("neko") == before, "No partial currency/items issued");
            }
            MANAGER.unloadSignInData(id);
        }
        configure(new SignInReward("neko", 7, Collections.singletonList(item(256))));
    }

    private static void onlineRewardUsesSamePendingLedger() throws Exception {
        set(OnlineTimeConfig.class, "tiers", Collections.singletonList(new OnlineTimeRewardTier(60, "neko", 3,
            "minecraft:iron_ingot", 256, 0, item(1).getNbtBase64())));
        UUID id = UUID.randomUUID();
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(id, false)) {
            fill(actor);
            MANAGER.getSignInData(id).addOnlineSeconds(DailySignInManager.getToday(), 60);
            check(MANAGER.claimOnlineReward(id, 0) == DailySignInManager.CLAIM_ONLINE_SUCCESS && pending(id) == 256, "Rejected online reward retained");
            check(MANAGER.claimOnlineReward(id, 0) == DailySignInManager.CLAIM_ONLINE_ALREADY_CLAIMED && pending(id) == 256, "Online claim cannot replay");
            MANAGER.unloadSignInData(id);
            check(pending(id) == 256, "Online entitlement persists");
            actor.world.accept = true;
            deliver(id);
            check(actor.world.delivered == 256 && pending(id) == 0, "Online retry consumes only pending items");
        }
        MANAGER.unloadSignInData(id);
    }

    private static void corruptExistingFileIsNeverReplaced() throws Exception {
        UUID id = UUID.randomUUID();
        Field dir = DailySignInManager.class.getDeclaredField("saveDir");
        dir.setAccessible(true);
        java.nio.file.Path path = ((java.io.File) dir.get(MANAGER)).toPath().resolve(id + ".dat");
        byte[] bad = { 1, 2, 3, 4, 5 };
        Files.write(path, bad);
        boolean failed = false;
        try { MANAGER.getSignInData(id); } catch (IllegalStateException expected) { failed = true; }
        check(failed, "Unreadable existing state fails closed");
        MANAGER.saveAll();
        check(java.util.Arrays.equals(bad, Files.readAllBytes(path)), "Failed read cannot overwrite old bytes");
    }

    private static void requestActorMustBeCurrentSession() throws Exception {
        UUID id = UUID.randomUUID();
        try (RewardDeliveryTestPlayer old = new RewardDeliveryTestPlayer(id, false)) {
            check(PlayerLookup.isCurrentSession(old.player), "Live actor admitted");
            old.disconnect();
            check(!PlayerLookup.isCurrentSession(old.player), "Logged-out actor rejected");
            try (RewardDeliveryTestPlayer replacement = new RewardDeliveryTestPlayer(id, false)) {
                check(PlayerLookup.isCurrentSession(replacement.player) && !PlayerLookup.isCurrentSession(old.player), "Same UUID cannot revive stale actor");
            }
        }
    }

    private static void failedWorldSwitchNeverServesOldClaims() throws Exception {
        UUID id = UUID.randomUUID();
        java.nio.file.Path oldRoot = Files.createTempDirectory("gtit-signin-failed-old-");
        java.nio.file.Path next = Files.createTempDirectory("gtit-signin-unbound-new-");
        Field directory = DailySignInManager.class.getDeclaredField("saveDir");
        directory.setAccessible(true);
        directory.set(MANAGER, oldRoot.toFile());
        MANAGER.getSignInData(id).recordSignIn(DailySignInManager.getToday(), DailySignInManager.getYesterday(), 1);
        java.nio.file.Path blocked = Files.createDirectory(oldRoot.resolve(id + ".dat"));
        Files.write(blocked.resolve("keep"), new byte[] { 1 });
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(id, false)) {
            actor.setWorldDirectory(next);
            boolean failed = false;
            try { MANAGER.init(actor.world); } catch (IllegalStateException expected) { failed = true; }
            check(failed, "Failed old-world save prevents binding to new world");
            failed = false;
            try { MANAGER.getSignInData(id); } catch (IllegalStateException expected) { failed = true; }
            check(failed, "Old-world claim cache cannot serve new-world player");
            check(!Files.exists(next.resolve("gtit_signin").resolve(id + ".dat")), "New-world data never overwritten by old cache");
            check(Files.exists(blocked.resolve("keep")), "Failed old record preserved");
        }
    }

    private static int pending(UUID id) {
        net.minecraft.nbt.NBTTagList items = MANAGER.getSignInData(id).writeToNBT().getTagList("pendingRewardItems", 10);
        int amount = 0;
        for (int i = 0; i < items.tagCount(); i++) amount += items.getCompoundTagAt(i).getInteger("gtitRewardCount");
        return amount;
    }
    private static void deliver(UUID id) throws Exception {
        DailySignInManager.class.getMethod("deliverPendingRewards", UUID.class).invoke(MANAGER, id);
    }
    private static int inventoryCount(RewardDeliveryTestPlayer actor) {
        int amount = 0;
        for (ItemStack stack : actor.player.inventory.mainInventory) if (stack != null && stack.getItem() == Items.iron_ingot) amount += stack.stackSize;
        return amount;
    }
    private static void fill(RewardDeliveryTestPlayer actor) {
        for (int i = 0; i < actor.player.inventory.mainInventory.length; i++) actor.player.inventory.mainInventory[i] = new ItemStack(Blocks.cobblestone, 64);
    }
    private static void set(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
