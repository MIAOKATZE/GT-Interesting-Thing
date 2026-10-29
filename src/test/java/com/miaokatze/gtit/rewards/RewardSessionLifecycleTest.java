package com.miaokatze.gtit.rewards;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.lottery.LotteryManager;
import com.miaokatze.gtit.mail.Mail;
import com.miaokatze.gtit.mail.MailData;
import com.miaokatze.gtit.mail.MailManager;
import com.miaokatze.gtit.signin.DailySignInData;
import com.miaokatze.gtit.signin.DailySignInManager;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.RewardDeliveryTestPlayer;

/** Rewards must remain world-bound across successful stops, failed stops, and explicit retry. */
public final class RewardSessionLifecycleTest {

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("gtit.rewards.testScratch"))
            throw new IllegalStateException("Run only in an isolated scratch working directory with -Dgtit.rewards.testScratch=true");
        if (!MinecraftTestBootstrap.enter(RewardSessionLifecycleTest.class, args)) return;
        int failed = 0;
        for (String name : new String[] { "preInitManagersRejectUnsavableState", "mailStopIsDurableAndIdempotent",
            "failedMailStopRetainsBindingUntilRetry", "lotteryStopIsDurableAndIdempotent",
            "failedLotteryStopRetainsBindingUntilRetry", "signinPendingRewardsSurviveStopAndReload" }) {
            try {
                RewardSessionLifecycleTest.class.getDeclaredMethod(name).invoke(null);
                System.out.println("PASS " + name);
            } catch (ReflectiveOperationException failure) {
                failed++;
                System.err.println("FAIL " + name + ": " + failure.getCause());
            }
        }
        if (failed > 0) throw new AssertionError(failed + " reward session regressions failed");
    }

    private static void preInitManagersRejectUnsavableState() {
        UUID id = UUID.randomUUID();
        rejects(() -> MailManager.INSTANCE.getMailData(id), "Mail must not create unsavable state before init");
        rejects(() -> DailySignInManager.INSTANCE.getSignInData(id), "Sign-in must not create unsavable state before init");
        rejects(() -> LotteryManager.INSTANCE.getPityCounter(id, "pool"), "Lottery must not use unsavable state before init");
    }

    private static Mail reward() {
        ItemStack stack = new ItemStack(Items.iron_ingot, 128);
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("owed", "exact-world-bound"); stack.setTagCompound(tag);
        return new Mail("owed", "", "", Collections.singletonList(stack));
    }

    private static void mailStopIsDurableAndIdempotent() throws Exception {
        MailManager manager = MailManager.INSTANCE;
        Path root = Files.createTempDirectory("gtit-mail-stop-"); UUID id = UUID.randomUUID();
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(id, false)) {
            actor.setWorldDirectory(root); manager.init(actor.world);
            Mail mail = reward(); manager.getMailData(id).addMail(mail);
            manager.setFirstRewardTemplate(reward());
            stop(manager); stop(manager);
            rejects(() -> manager.getMailData(id), "Stopped mail service denies claims");
            check(get(manager, "saveDir") == null && ((Map<?, ?>) get(manager, "mailDataMap")).isEmpty(), "Successful stop releases world cache");
            NBTTagCompound saved = CompressedStreamTools.read(root.resolve("gtit_mail").resolve(id + ".dat").toFile());
            MailData restored = new MailData(); restored.readFromNBT(saved.getCompoundTag("mail"));
            check(restored.findMail(mail.getId()).getAttachments().get(0).stackSize == 128, "Exact paid attachment count saved before clearing");
            manager.init(actor.world);
            check(manager.getMailData(id).findMail(mail.getId()).hasUnclaimedAttachments(), "Unclaimed mail survives same-world restart");
            check(manager.getFirstRewardTemplate() != null, "Global pending template survives stop");
            stop(manager);
        }
    }

    private static void failedMailStopRetainsBindingUntilRetry() throws Exception {
        MailManager manager = MailManager.INSTANCE;
        Path root = Files.createTempDirectory("gtit-mail-stop-failed-"); UUID id = UUID.randomUUID();
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(id, false)) {
            actor.setWorldDirectory(root); manager.init(actor.world);
            MailData data = manager.getMailData(id); Mail mail = reward(); data.addMail(mail);
            Path directory = root.resolve("gtit_mail"); Path blocker = Files.createDirectory(directory.resolve(id + ".dat"));
            Files.write(blocker.resolve("keep"), new byte[] { 42 });
            rejects(() -> stop(manager), "Failed mail flush is visible");
            rejects(() -> manager.getMailData(id), "Failed stop still revokes authority");
            check(((java.io.File) get(manager, "saveDir")).toPath().equals(directory), "Failed stop retains old world binding");
            check(((Map<?, ?>) get(manager, "mailDataMap")).get(id) == data, "Failed stop retains owned mail cache");
            check(Files.readAllBytes(blocker.resolve("keep"))[0] == 42, "Existing blocker preserved");
            Files.delete(blocker.resolve("keep")); Files.delete(blocker);
            stop(manager); stop(manager);
            manager.init(actor.world);
            check(manager.getMailData(id).findMail(mail.getId()).getAttachments().get(0).stackSize == 128, "Retried stop saves same obligation without loss");
            Path next = Files.createTempDirectory("gtit-mail-stop-next-"); actor.setWorldDirectory(next); manager.init(actor.world);
            check(manager.getMailData(id).getMails().isEmpty(), "Other world never inherits prior mail");
            stop(manager);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setPity(UUID id, int count) throws Exception {
        ((Map<UUID, Map<String, Integer>>) get(LotteryManager.INSTANCE, "pityCounters"))
            .put(id, new java.util.HashMap<>(Collections.singletonMap("pool", count)));
    }

    private static void lotteryStopIsDurableAndIdempotent() throws Exception {
        LotteryManager manager = LotteryManager.INSTANCE; Path root = Files.createTempDirectory("gtit-lottery-stop-"); UUID id = UUID.randomUUID();
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(id, false)) {
            actor.setWorldDirectory(root); manager.init(actor.world); setPity(id, 87);
            stop(manager); stop(manager);
            rejects(() -> manager.getPityCounter(id, "pool"), "Stopped lottery service denies draws");
            check(get(manager, "saveDir") == null && ((Map<?, ?>) get(manager, "pityCounters")).isEmpty(), "Successful stop releases pity cache");
            manager.init(actor.world); manager.loadTeamData(id);
            check(manager.getPityCounter(id, "pool") == 87, "Pity survives stop and reload"); stop(manager);
        }
    }

    private static void failedLotteryStopRetainsBindingUntilRetry() throws Exception {
        LotteryManager manager = LotteryManager.INSTANCE; Path root = Files.createTempDirectory("gtit-lottery-stop-failed-"); UUID id = UUID.randomUUID();
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(id, false)) {
            actor.setWorldDirectory(root); manager.init(actor.world); setPity(id, 87);
            Path directory = root.resolve("gtit_lottery"); Path blocker = Files.createDirectory(directory.resolve(id + ".dat"));
            Files.write(blocker.resolve("keep"), new byte[] { 42 });
            rejects(() -> stop(manager), "Failed lottery flush is visible");
            rejects(() -> manager.getPityCounter(id, "pool"), "Failed stop revokes lottery authority");
            check(((java.io.File) get(manager, "saveDir")).toPath().equals(directory), "Pity retains old binding after failed flush");
            check(((Map<?, ?>) get(manager, "pityCounters")).containsKey(id), "Unsaved pity survives failed flush");
            Files.delete(blocker.resolve("keep")); Files.delete(blocker);
            stop(manager); stop(manager); manager.init(actor.world); manager.loadTeamData(id);
            check(manager.getPityCounter(id, "pool") == 87, "Retry saves original pity");
            Path next = Files.createTempDirectory("gtit-lottery-stop-next-"); actor.setWorldDirectory(next); manager.init(actor.world);
            check(manager.getPityCounter(id, "pool") == 0, "Other world receives no stale pity"); stop(manager);
        }
    }

    private static void signinPendingRewardsSurviveStopAndReload() throws Exception {
        DailySignInManager manager = DailySignInManager.INSTANCE; Path root = Files.createTempDirectory("gtit-signin-stop-"); UUID id = UUID.randomUUID();
        try (RewardDeliveryTestPlayer actor = new RewardDeliveryTestPlayer(id, false)) {
            actor.setWorldDirectory(root); manager.init(actor.world);
            DailySignInData data = manager.getSignInData(id);
            java.lang.reflect.Method queue = DailySignInData.class.getDeclaredMethod("queueRewards", java.util.List.class); queue.setAccessible(true);
            queue.invoke(data, reward().getAttachments());
            data.recordSignIn(DailySignInManager.getToday(), DailySignInManager.getYesterday(), 1);
            actor.disconnect(); stop(manager); stop(manager);
            rejects(() -> manager.getSignInData(id), "Stopped sign-in cannot accept claims");
            manager.init(actor.world);
            DailySignInData reloaded = manager.getSignInData(id);
            check(reloaded.hasSignedToday(DailySignInManager.getToday()), "Claim receipt survives stop");
            check(reloaded.writeToNBT().getTagList("pendingRewardItems", 10).getCompoundTagAt(0).getInteger("gtitRewardCount") == 128,
                "Disconnected player's pending item obligation survives stop/reload"); stop(manager);
        }
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void stop(Object manager) throws Exception {
        try { manager.getClass().getMethod("unloadAll").invoke(manager); }
        catch (InvocationTargetException e) { if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause(); throw e; }
    }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action, String message) {
        try { action.run(); } catch (IllegalStateException expected) { return; } catch (Exception failure) { throw new AssertionError(failure); }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
