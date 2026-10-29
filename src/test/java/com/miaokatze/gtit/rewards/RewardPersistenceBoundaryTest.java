package com.miaokatze.gtit.rewards;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.miaokatze.gtit.lottery.LotteryManager;
import com.miaokatze.gtit.mail.Mail;
import com.miaokatze.gtit.mail.MailData;
import com.miaokatze.gtit.mail.MailManager;
import com.miaokatze.gtit.signin.DailySignInData;
import com.miaokatze.gtit.signin.DailySignInManager;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

/** Native NBT fallbacks must not erase entitlements or reset receipts; team reads must load once. */
public final class RewardPersistenceBoundaryTest {

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("gtit.rewards.testScratch"))
            throw new IllegalStateException("Run only in an isolated scratch working directory with -Dgtit.rewards.testScratch=true");
        if (!MinecraftTestBootstrap.enter(RewardPersistenceBoundaryTest.class, args)) return;
        int failed = 0;
        for (String name : new String[] { "nativeWrongElementListReturnsEmpty", "legacyAbsentAndEmptyFieldsRemainReadable",
            "mailRejectsWrongAttachmentListType", "mailRejectsWrongAttachmentElementType", "mailRejectsWrongClaimType",
            "mailRejectsWrongRemainderType", "mailRejectsWrongItemTagType", "mailboxRejectsWrongReceiptList",
            "signinRejectsWrongPendingListType", "signinRejectsWrongPendingElementType", "signinRejectsWrongClaimList",
            "signinRejectsWrongDateType", "mailManagerPreservesTypedCorruption", "signinManagerPreservesTypedCorruption",
            "globalMailPreservesWrongListElements", "lotteryRejectsWrongPityContainer", "lotteryRejectsWrongCounterType",
            "lotteryRejectsNegativePity", "lotteryReadLoadsUnseenTeam", "lotterySnapshotLoadsUnseenTeam",
            "lotteryLogoutCannotOverwriteUnseenTeam", "teammateLoginCannotRollbackFailedSave", "lotteryMalformedLoadPublishesNoPartialState", "pityNeverWrapsNegative" }) {
            try {
                RewardPersistenceBoundaryTest.class.getDeclaredMethod(name).invoke(null);
                System.out.println("PASS " + name);
            } catch (ReflectiveOperationException failure) {
                failed++;
                System.err.println("FAIL " + name + ": " + failure.getCause());
            }
        }
        if (failed > 0) throw new AssertionError(failed + " reward persistence boundary regressions failed");
    }

    private static NBTTagList strings() {
        NBTTagList list = new NBTTagList(); list.appendTag(new NBTTagString("not-a-compound")); return list;
    }

    private static NBTTagCompound mail() {
        return new Mail("owed", "", "system", Collections.singletonList(new ItemStack(Items.iron_ingot, 128))).writeToNBT();
    }

    private static NBTTagCompound signIn() {
        NBTTagCompound nbt = new DailySignInData().writeToNBT();
        NBTTagCompound item = new ItemStack(Items.diamond, 1).writeToNBT(new NBTTagCompound());
        item.setInteger("gtitRewardCount", 128);
        NBTTagList list = new NBTTagList(); list.appendTag(item); nbt.setTag("pendingRewardItems", list);
        return nbt;
    }

    private static void nativeWrongElementListReturnsEmpty() {
        NBTTagCompound nbt = new NBTTagCompound(); nbt.setTag("owed", strings());
        check(nbt.hasKey("owed", 9) && nbt.getTagList("owed", 10).tagCount() == 0,
            "Native getTagList accepts the outer list type but discards mismatched elements");
    }

    private static void legacyAbsentAndEmptyFieldsRemainReadable() {
        new MailData().readFromNBT(new NBTTagCompound());
        new DailySignInData().readFromNBT(new NBTTagCompound());
        NBTTagCompound mail = mail(); mail.removeTag("type");
        NBTTagCompound item = mail.getTagList("attachments", 10).getCompoundTagAt(0);
        item.removeTag("gtitMailCount"); item.removeTag("gtitMailRemaining"); item.setByte("Count", (byte) 12);
        check(Mail.fromNBT(mail).getAttachments().get(0).stackSize == 12, "Legacy native count remains valid");
        NBTTagCompound data = new MailData().writeToNBT(); new MailData().readFromNBT(data);
        DailySignInData decoded = new DailySignInData(); decoded.readFromNBT(signIn());
        check(decoded.writeToNBT().getTagList("pendingRewardItems", 10).getCompoundTagAt(0).getInteger("gtitRewardCount") == 128,
            "Valid large pending quantities survive native byte Count");
    }

    private static void mailRejectsWrongAttachmentListType() {
        NBTTagCompound nbt = mail(); nbt.setString("attachments", "owed"); rejects(() -> Mail.fromNBT(nbt));
    }
    private static void mailRejectsWrongAttachmentElementType() {
        NBTTagCompound nbt = mail(); nbt.setTag("attachments", strings()); rejects(() -> Mail.fromNBT(nbt));
    }
    private static void mailRejectsWrongClaimType() {
        NBTTagCompound nbt = mail(); nbt.setString("claimed", "true"); rejects(() -> Mail.fromNBT(nbt));
    }
    private static void mailRejectsWrongRemainderType() {
        NBTTagCompound nbt = mail(); nbt.getTagList("attachments", 10).getCompoundTagAt(0).setString("gtitMailRemaining", "0");
        rejects(() -> Mail.fromNBT(nbt));
    }
    private static void mailRejectsWrongItemTagType() {
        NBTTagCompound nbt = mail(); nbt.getTagList("attachments", 10).getCompoundTagAt(0).setString("tag", "owned-item-identity");
        rejects(() -> Mail.fromNBT(nbt));
    }
    private static void mailboxRejectsWrongReceiptList() {
        NBTTagCompound nbt = new MailData().writeToNBT();
        NBTTagList wrong = new NBTTagList(); wrong.appendTag(new NBTTagCompound()); nbt.setTag("receivedOnceIds", wrong);
        rejects(() -> new MailData().readFromNBT(nbt));
    }
    private static void signinRejectsWrongPendingListType() {
        NBTTagCompound nbt = signIn(); nbt.setString("pendingRewardItems", "owed"); rejects(() -> new DailySignInData().readFromNBT(nbt));
    }
    private static void signinRejectsWrongPendingElementType() {
        NBTTagCompound nbt = signIn(); nbt.setTag("pendingRewardItems", strings()); rejects(() -> new DailySignInData().readFromNBT(nbt));
    }
    private static void signinRejectsWrongClaimList() {
        NBTTagCompound nbt = signIn(); NBTTagList wrong = new NBTTagList(); wrong.appendTag(new NBTTagCompound());
        nbt.setTag("claimedCumulativeTiers", wrong); rejects(() -> new DailySignInData().readFromNBT(nbt));
    }
    private static void signinRejectsWrongDateType() {
        NBTTagCompound nbt = signIn(); nbt.setInteger("lastSignInDate", 20261029); rejects(() -> new DailySignInData().readFromNBT(nbt));
    }

    private static void mailManagerPreservesTypedCorruption() throws Exception {
        MailManager manager = MailManager.INSTANCE; Path root = Files.createTempDirectory("gtit-typed-mail-"); UUID id = UUID.randomUUID();
        set(manager, "saveDir", root.toFile()); set(manager, "sessionUnavailable", false); ((Map<?, ?>) get(manager, "mailDataMap")).clear();
        NBTTagCompound record = new NBTTagCompound(); NBTTagCompound data = new MailData().writeToNBT(); data.setTag("mails", strings()); record.setTag("mail", data);
        Path file = root.resolve(id + ".dat"); CompressedStreamTools.write(record, file.toFile()); byte[] before = Files.readAllBytes(file);
        rejects(() -> manager.getMailData(id)); manager.saveMailData(id);
        check(Arrays.equals(before, Files.readAllBytes(file)), "Malformed existing mail bytes cannot be rewritten empty");
    }

    private static void signinManagerPreservesTypedCorruption() throws Exception {
        DailySignInManager manager = DailySignInManager.INSTANCE; Path root = Files.createTempDirectory("gtit-typed-signin-"); UUID id = UUID.randomUUID();
        set(manager, "saveDir", root.toFile()); set(manager, "sessionUnavailable", false); ((Map<?, ?>) get(manager, "signInDataMap")).clear();
        NBTTagCompound record = new NBTTagCompound(); NBTTagCompound data = signIn(); data.setTag("pendingRewardItems", strings()); record.setTag("signin", data);
        Path file = root.resolve(id + ".dat"); CompressedStreamTools.write(record, file.toFile()); byte[] before = Files.readAllBytes(file);
        rejects(() -> manager.getSignInData(id)); manager.saveSignInData(id);
        check(Arrays.equals(before, Files.readAllBytes(file)), "Malformed existing sign-in bytes cannot erase pending rewards");
    }

    private static void globalMailPreservesWrongListElements() throws Exception {
        MailManager manager = MailManager.INSTANCE; Path file = Files.createTempDirectory("gtit-typed-global-mail-").resolve("global.dat");
        NBTTagCompound record = new NBTTagCompound(); record.setTag("onceRewards", strings()); CompressedStreamTools.write(record, file.toFile());
        byte[] before = Files.readAllBytes(file); set(manager, "globalFile", file.toFile()); set(manager, "globalDataLoaded", false);
        rejects(() -> invoke(manager, "loadGlobalData")); rejects(() -> invoke(manager, "saveGlobalData"));
        check(Arrays.equals(before, Files.readAllBytes(file)), "Invalid global list elements must not become an empty reward table");
    }

    private static Path lotteryRoot() throws Exception {
        LotteryManager manager = LotteryManager.INSTANCE; Path root = Files.createTempDirectory("gtit-typed-pity-");
        set(manager, "saveDir", root.toFile()); set(manager, "sessionUnavailable", false);
        ((Map<?, ?>) get(manager, "pityCounters")).clear(); ((java.util.Set<?>) get(manager, "unreadableTeams")).clear(); return root;
    }

    private static Path writePity(Path root, UUID id, int count) throws Exception {
        NBTTagCompound record = new NBTTagCompound(); NBTTagCompound pity = new NBTTagCompound(); pity.setInteger("pool", count); record.setTag("pity", pity);
        Path file = root.resolve(id + ".dat"); CompressedStreamTools.write(record, file.toFile()); return file;
    }

    private static void invalidPity(NBTTagCompound record) throws Exception {
        Path root = lotteryRoot(); UUID id = UUID.randomUUID(); Path file = root.resolve(id + ".dat");
        CompressedStreamTools.write(record, file.toFile()); byte[] before = Files.readAllBytes(file); LotteryManager manager = LotteryManager.INSTANCE;
        rejects(() -> manager.loadTeamData(id)); rejects(() -> manager.getPityCounter(id, "pool")); rejects(() -> manager.saveTeamData(id));
        check(Arrays.equals(before, Files.readAllBytes(file)), "Unreadable pity must remain unchanged");
    }
    private static void lotteryRejectsWrongPityContainer() throws Exception {
        NBTTagCompound record = new NBTTagCompound(); record.setString("pity", "87"); invalidPity(record);
    }
    private static void lotteryRejectsWrongCounterType() throws Exception {
        NBTTagCompound record = new NBTTagCompound(); NBTTagCompound pity = new NBTTagCompound(); pity.setString("pool", "87"); record.setTag("pity", pity); invalidPity(record);
    }
    private static void lotteryRejectsNegativePity() throws Exception {
        NBTTagCompound record = new NBTTagCompound(); NBTTagCompound pity = new NBTTagCompound(); pity.setInteger("pool", -1); record.setTag("pity", pity); invalidPity(record);
    }
    private static void lotteryReadLoadsUnseenTeam() throws Exception {
        Path root = lotteryRoot(); UUID team = UUID.randomUUID(); writePity(root, team, 87);
        check(LotteryManager.INSTANCE.getPityCounter(team, "pool") == 87, "A newly resolved team key must load before draw reads/advances it");
    }
    private static void lotterySnapshotLoadsUnseenTeam() throws Exception {
        Path root = lotteryRoot(); UUID team = UUID.randomUUID(); writePity(root, team, 87);
        check(Integer.valueOf(87).equals(LotteryManager.INSTANCE.getPityCounters(team).get("pool")), "Sync must load newly resolved team's pity");
    }
    private static void lotteryLogoutCannotOverwriteUnseenTeam() throws Exception {
        Path root = lotteryRoot(); UUID team = UUID.randomUUID(); Path file = writePity(root, team, 87);
        LotteryManager.INSTANCE.saveTeamData(team);
        check(CompressedStreamTools.read(file.toFile()).getCompoundTag("pity").getInteger("pool") == 87, "Logout save for newly resolved unseen team must never write empty state");
    }
    @SuppressWarnings("unchecked")
    private static void teammateLoginCannotRollbackFailedSave() throws Exception {
        Path root = lotteryRoot(); UUID team = UUID.randomUUID(); Path file = writePity(root, team, 87); byte[] before = Files.readAllBytes(file);
        LotteryManager manager = LotteryManager.INSTANCE; manager.loadTeamData(team);
        Map<UUID, Map<String, Integer>> counters = (Map<UUID, Map<String, Integer>>) get(manager, "pityCounters"); counters.get(team).put("pool", 88);
        Files.delete(file); Files.createDirectory(file); Files.write(file.resolve("block"), new byte[] { 1 });
        rejects(() -> manager.saveTeamData(team)); Files.delete(file.resolve("block")); Files.delete(file); Files.write(file, before);
        manager.loadTeamData(team);
        check(manager.getPityCounter(team, "pool") == 88, "A second teammate login must not replay stale disk over failed-save live pity");
        manager.saveTeamData(team);
        check(CompressedStreamTools.read(file.toFile()).getCompoundTag("pity").getInteger("pool") == 88, "Retry must persist retained authoritative state");
    }
    private static void lotteryMalformedLoadPublishesNoPartialState() throws Exception {
        Path root = lotteryRoot(); UUID team = UUID.randomUUID(); NBTTagCompound record = new NBTTagCompound(); NBTTagCompound pity = new NBTTagCompound();
        pity.setInteger("valid", 87); pity.setString("bad", "invalid"); record.setTag("pity", pity); CompressedStreamTools.write(record, root.resolve(team + ".dat").toFile());
        rejects(() -> LotteryManager.INSTANCE.loadTeamData(team));
        check(!((Map<?, ?>) get(LotteryManager.INSTANCE, "pityCounters")).containsKey(team), "Failed decoding must not publish a partially trusted counter map");
    }

    private static void pityNeverWrapsNegative() throws Exception {
        Path root = lotteryRoot(); UUID team = UUID.randomUUID(); writePity(root, team, Integer.MAX_VALUE);
        LotteryManager.INSTANCE.loadTeamData(team);
        com.miaokatze.gtit.lottery.PityConfig pity = new com.miaokatze.gtit.lottery.PityConfig(); pity.setEnabled(false);
        com.miaokatze.gtit.lottery.LotteryPool pool = new com.miaokatze.gtit.lottery.LotteryPool("pool", "pool", "", 0, pity);
        com.miaokatze.gtit.lottery.LotteryEntry entry = new com.miaokatze.gtit.lottery.LotteryEntry();
        entry.setItem("minecraft:iron_ingot"); entry.setWeight(1); entry.setMinAmount(1); entry.setMaxAmount(1);
        entry.setRarity(com.miaokatze.gtit.lottery.LotteryRarity.COMMON); pool.getEntries().add(entry);
        check(LotteryManager.INSTANCE.drawSingle(team, pool) != null, "Real pool draw succeeds");
        check(LotteryManager.INSTANCE.getPityCounter(team, "pool") == Integer.MAX_VALUE, "Pity saturates instead of writing a negative, unreadable record");
    }

    private static Object get(Object owner, String name) throws Exception { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
    private static void set(Object owner, String name, Object value) throws Exception { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value); }
    private static void invoke(Object owner, String name) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name); method.setAccessible(true);
        try { method.invoke(owner); } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception) throw (Exception) failure.getCause(); throw failure;
        }
    }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action) { try { action.run(); } catch (Exception expected) { return; } throw new AssertionError("Expected fail-closed rejection"); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
