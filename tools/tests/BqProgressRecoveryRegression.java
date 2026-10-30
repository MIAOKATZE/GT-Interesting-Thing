package com.miaokatze.gtit.crossmod.bq;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.NBTConverter;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.tasks.TaskRegistry;
import bq_standard.tasks.TaskCheckbox;
import bq_standard.tasks.factory.FactoryTaskCheckbox;

/** Native BQ completion/claim/task recovery through the complete production inject entry point. */
public final class BqProgressRecoveryRegression {

    private static final long HIGH = 5139813699862659072L;
    private static final String ROOT = "assets/gtit/bqquests/";
    private static final String MIAO_ROOT = ROOT + "miao/";
    private static final UUID MIAO_QUEST = new UUID(5569054221778550784L, 1);
    private static final UUID QUEST = new UUID(HIGH, 1);
    private static final UUID UNLISTED = new UUID(HIGH, 99);
    private static final UUID FOREIGN = new UUID(12, 34);
    private static final UUID PLAYER = new UUID(123, 456);
    private static Path work;
    private static int checks;
    private static int failures;

    public static void main(String[] args) throws Exception {
        work = Paths.get(args[0]);
        try {
            net.minecraft.init.Bootstrap.register();
            TaskRegistry.INSTANCE.register(FactoryTaskCheckbox.INSTANCE);
            TaskRegistry.INSTANCE.register(bq_standard.tasks.factory.FactoryTaskRetrieval.INSTANCE);
            TaskRegistry.INSTANCE.register(bq_standard.tasks.factory.FactoryTaskCrafting.INSTANCE);
            BqCompat.detect();
            writeResources();
            recreate(false);
            recreate(true);
            precedence(false);
            precedence(true);
            retained(false, false);
            retained(true, false);
            retained(false, true);
            retained(true, true);
            retainedOwnedDuringRecovery(false, false);
            retainedOwnedDuringRecovery(true, false);
            retainedOwnedDuringRecovery(false, true);
            retainedOwnedDuringRecovery(true, true);
            unrelated(FOREIGN, false);
            unrelated(FOREIGN, true);
            prunedOwned();
            unknownRoot();
            twoPackPass(false, false);
            twoPackPass(false, true);
            twoPackPass(true, false);
            twoPackPass(true, true);
            emptyMiaoPass();
            missingStampRetained();
            malformedStampRetained();
            failedStampNonfatal();
            mixedUnknown();
            fresh();
            migration(true);
            migration(false);
            partialOnly("checkbox");
            partialOnly("retrieval");
            partialOnly("crafting");
            System.out.println("RESULT checks=" + checks + " failures=" + failures);
            if (failures != 0) throw new AssertionError(failures + " recovery contract failures");
        } finally {
            betterquesting.api2.utils.BQThreadedIO.INSTANCE.shutdown();
            betterquesting.api2.utils.BQThreadedIO.DISK_IO.shutdown();
        }
    }

    private static void recreate(boolean legacy) throws Exception {
        String name = legacy ? "aggregate-only" : "per-player";
        reset(name);
        seed(QUEST, true);
        writeProgress(legacy ? "QuestProgress.json" : playerFile());
        current().resetUser(PLAYER, true);
        nativeLoadProgress();
        check(name + " native disk load restores completion and serialized claim", completeAndClaimed(current()));
        check(name + " native disk load restores native checkbox progress", checkbox(current()).isComplete(PLAYER));
        nativeDefaultReload(false);
        check(name + " definition really absent during defaults window", QuestDatabase.INSTANCE.get(QUEST) == null);
        Map<Path, byte[]> disk = snapshotDisk();
        BqQuestInjector.inject(ROOT);
        check(name + " recovered completion and serialized claim", completeAndClaimed(current()));
        check(name + " recovered native checkbox progress", checkbox(current()).isComplete(PLAYER));
        check(name + " same-version stamp and all progress bytes unchanged", diskEquals(disk));
        QuestInstance same = current();
        same.resetUser(PLAYER, true);
        BqQuestInjector.inject(ROOT);
        check(name + " second inject keeps same definition and newer live reset", current() == same && !same.isComplete(PLAYER));
        check(name + " second inject does not rewrite saved files", diskEquals(disk));
    }

    private static void precedence(boolean perPlayerClaimed) throws Exception {
        reset("precedence-" + perPlayerClaimed);
        seed(QUEST, !perPlayerClaimed);
        writeProgress("QuestProgress.json");
        seed(QUEST, perPlayerClaimed);
        writeProgress(playerFile());
        current().resetUser(PLAYER, true);
        nativeLoadProgress();
        check("native per-player precedence " + perPlayerClaimed, claimed(current()) == perPlayerClaimed);
        nativeDefaultReload(false);
        Map<Path, byte[]> disk = snapshotDisk();
        BqQuestInjector.inject(ROOT);
        check("recovery matches native per-player precedence " + perPlayerClaimed,
            current().isComplete(PLAYER) && claimed(current()) == perPlayerClaimed);
        check("precedence preserves saved files " + perPlayerClaimed, diskEquals(disk));
    }

    /** Native default reload retains its in-memory snapshot; stale disk is not authoritative afterward. */
    private static void retained(boolean reset, boolean refresh) throws Exception {
        String name = "retained-" + (reset ? "reset" : "claim") + "-refresh-" + refresh;
        reset(name);
        seed(QUEST, reset);
        writeProgress(playerFile());
        nativeLoadProgress();
        if (reset) current().resetUser(PLAYER, true);
        else current().setClaimed(PLAYER, 999);
        nativeDefaultReload(true);
        QuestInstance live = current();
        NBTTagCompound before = live.writeProgressToNBT(new NBTTagCompound(), null);
        if (refresh) writeWorld("gtit-injected.json", "{\"gtit\":\"older\",\"miao\":\"test-same\"}");
        Map<Path, byte[]> disk = snapshotProgressDisk();
        check(name + " native reload retained latest memory state", reset ? !live.isComplete(PLAYER) : claimed(live));
        BqQuestInjector.inject(ROOT);
        check(name + " inject preserves exact live progress and identity",
            current() == live && before.equals(live.writeProgressToNBT(new NBTTagCompound(), null)));
        check(name + " progress files unchanged", diskEquals(disk));
        check(name + " final stamp current", stampCurrent());
    }

    /** Keep recovery active by removing the listed quest, while a distinct definition survives native reload. */
    private static void unrelated(UUID id, boolean reset) throws Exception {
        String name = "unrelated-" + id + "-reset-" + reset;
        reset(name);
        seed(QUEST, true);
        seed(id, reset);
        writeProgress(playerFile());
        nativeLoadProgress();
        QuestInstance other = (QuestInstance) QuestDatabase.INSTANCE.get(id);
        if (reset) other.resetUser(PLAYER, true);
        else other.setClaimed(PLAYER, 999);
        nativeDefaultReload(false);
        other = (QuestInstance) QuestDatabase.INSTANCE.get(id);
        NBTTagCompound before = other.writeProgressToNBT(new NBTTagCompound(), null);
        Map<Path, byte[]> disk = snapshotDisk();
        BqQuestInjector.inject(ROOT);
        check(name + " owned recovery still works", completeAndClaimed(current()));
        check(name + " unrelated definition and live progress unchanged",
            QuestDatabase.INSTANCE.get(id) == other && before.equals(other.writeProgressToNBT(new NBTTagCompound(), null)));
        check(name + " saved files unchanged", diskEquals(disk));
    }

    private static void mixedUnknown() throws Exception {
        reset("mixed-unknown");
        seed(QUEST, true);
        seed(FOREIGN, true);
        writeProgress(playerFile());
        QuestDatabase.INSTANCE.clear();
        writeWorld("QuestProgress/ignore.txt", "untouched non-JSON file");
        writeWorld("QuestProgress/broken.json", "not valid JSON");
        Map<Path, byte[]> disk = snapshotDisk();
        BqQuestInjector.inject(ROOT);
        check("valid per-player progress survives malformed sibling", completeAndClaimed(current()));
        check("foreign saved record does not create a definition", QuestDatabase.INSTANCE.get(FOREIGN) == null);
        check("malformed and unknown files are not rewritten or deleted", diskEquals(disk));
    }


    /** Reconstruct one listed quest while a different listed quest in the SAME namespace survives. */
    private static void retainedOwnedDuringRecovery(boolean reset, boolean refresh) throws Exception {
        String name = "retained-owned-active-recovery-reset-" + reset + "-refresh-" + refresh;
        reset(name);
        UUID retainedId = new UUID(HIGH, 2);
        Path resources = work.resolve("resources");
        NBTTagCompound definition = definition().writeToNBT(new NBTTagCompound());
        NBTConverter.UuidValueType.QUEST.writeId(retainedId, definition);
        write(resources.resolve("retained-quest.json"), NBTConverter.NBTtoJSON_Compound(definition, new JsonObject(), true).toString());
        write(resources.resolve("retained-entry.json"), "{\"questIDHigh:4\":" + HIGH + ",\"questIDLow:4\":2}");
        JsonObject index = new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(
            resources.resolve(ROOT + "index.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject entry = new JsonObject();
        entry.addProperty("questFile", "retained-quest.json"); entry.addProperty("entryFile", "retained-entry.json");
        index.getAsJsonArray("questLines").get(0).getAsJsonObject().getAsJsonArray("entries").add(entry);
        write(resources.resolve(ROOT + "index.json"), index.toString());
        seed(QUEST, true);
        seed(retainedId, reset);
        writeProgress("QuestProgress.json");
        writeProgress(playerFile());
        nativeLoadProgress();
        QuestInstance retained = (QuestInstance) QuestDatabase.INSTANCE.get(retainedId);
        if (reset) retained.resetUser(PLAYER, true);
        else retained.setClaimed(PLAYER, 987);
        nativeDefaultReload(false);
        retained = (QuestInstance) QuestDatabase.INSTANCE.get(retainedId);
        NBTTagCompound before = retained.writeProgressToNBT(new NBTTagCompound(), null);
        if (refresh) writeWorld("gtit-injected.json", "{\"gtit\":\"older\",\"miao\":\"test-same\"}");
        Map<Path, byte[]> disk = snapshotProgressDisk();
        BqQuestInjector.inject(ROOT);
        check(name + " missing listed quest is recovered", completeAndClaimed(current()));
        check(name + " different retained listed owned quest keeps exact live state", QuestDatabase.INSTANCE.get(retainedId) == retained
            && before.equals(retained.writeProgressToNBT(new NBTTagCompound(), null)));
        check(name + " recovery preserves all input progress files", diskEquals(disk));
        writeResources();
    }

    private static void prunedOwned() throws Exception {
        reset("owned-pruning");
        seed(QUEST, true);
        seed(UNLISTED, true);
        writeProgress(playerFile());
        nativeLoadProgress();
        nativeDefaultReload(false);
        Map<Path, byte[]> disk = snapshotDisk();
        BqQuestInjector.inject(ROOT);
        check("listed owned quest recovers while unlisted-owned quest is pruned", completeAndClaimed(current())
            && QuestDatabase.INSTANCE.get(UNLISTED) == null);
        check("pruning does not rewrite original progress files", diskEquals(disk));
        BqQuestInjector.inject(ROOT);
        check("repeat pass never resurrects pruned owned progress", QuestDatabase.INSTANCE.get(UNLISTED) == null);
    }

    private static void unknownRoot() throws Exception {
        reset("unknown-root-no-recovery");
        seed(QUEST, true);
        writeProgress(playerFile());
        QuestDatabase.INSTANCE.clear();
        write(work.resolve("resources/custom/index.json"),
            new String(Files.readAllBytes(work.resolve("resources/" + ROOT + "index.json")), StandardCharsets.UTF_8));
        Map<Path, byte[]> disk = snapshotDisk();
        BqQuestInjector.inject("custom/");
        check("unknown root get-or-create is retained without broad fallback replay", current() != null
            && !current().isComplete(PLAYER) && !checkbox(current()).isComplete(PLAYER));
        check("unknown root neither stamps nor mutates progress files", diskEquals(disk));
    }

    /** Synthetic second pack proves each pass's newly-created allowlist is separate, in both orders. */
    private static void twoPackPass(boolean miaoFirst, boolean resetFirst) throws Exception {
        String name = "two-packs-miao-first-" + miaoFirst + "-reset-first-" + resetFirst;
        reset(name);
        writePackResources(MIAO_ROOT, MIAO_QUEST, "miao-");
        seed(QUEST, false);
        seed(MIAO_QUEST, false);
        writeProgress("QuestProgress.json");
        seed(QUEST, true);
        seed(MIAO_QUEST, true);
        writeProgress(playerFile());
        QuestDatabase.INSTANCE.clear();
        writeWorld("gtit-injected.json", "{\"gtit\":\"older\",\"miao\":\"older\",\"metadata\":{\"keep\":7}}");
        Map<Path, byte[]> disk = snapshotProgressDisk();
        String firstRoot = miaoFirst ? MIAO_ROOT : ROOT;
        String secondRoot = miaoFirst ? ROOT : MIAO_ROOT;
        UUID firstId = miaoFirst ? MIAO_QUEST : QUEST;
        UUID secondId = miaoFirst ? QUEST : MIAO_QUEST;
        BqQuestInjector.inject(firstRoot);
        QuestInstance first = (QuestInstance) QuestDatabase.INSTANCE.get(firstId);
        check(name + " first pack recovers and cannot create other pack", completeAndClaimed(first)
            && QuestDatabase.INSTANCE.get(secondId) == null);
        if (resetFirst) first.resetUser(PLAYER, true);
        else first.setClaimed(PLAYER, 987); // Different native completion record from disk, not only a boolean.
        NBTTagCompound latest = first.writeProgressToNBT(new NBTTagCompound(), null);
        BqQuestInjector.inject(secondRoot);
        check(name + " second pack recovers its own progress", completeAndClaimed((QuestInstance) QuestDatabase.INSTANCE.get(secondId)));
        check(name + " second pass cannot overwrite first pack's latest memory", QuestDatabase.INSTANCE.get(firstId) == first
            && latest.equals(first.writeProgressToNBT(new NBTTagCompound(), null)));
        JsonObject stamp = readStamp();
        check(name + " both keys and unknown metadata survive", stamp.get("gtit").getAsString().equals("test-same")
            && stamp.get("miao").getAsString().equals("test-same") && stamp.getAsJsonObject("metadata").get("keep").getAsInt() == 7);
        check(name + " all saved progress bytes unchanged", diskEquals(disk));
        writeResources();
    }

    private static void emptyMiaoPass() throws Exception {
        reset("empty-miao");
        seed(QUEST, false);
        seed(MIAO_QUEST, true);
        seed(FOREIGN, false);
        writeProgress(playerFile());
        current().setClaimed(PLAYER, 987);
        QuestInstance foreign = (QuestInstance) QuestDatabase.INSTANCE.get(FOREIGN);
        foreign.resetUser(PLAYER, true);
        NBTTagCompound before = current().writeProgressToNBT(new NBTTagCompound(), null);
        Map<Path, byte[]> disk = snapshotDisk();
        BqQuestInjector.inject(MIAO_ROOT);
        check("empty miao keeps newer GTIT claim and foreign reset", before.equals(current().writeProgressToNBT(new NBTTagCompound(), null))
            && !foreign.isComplete(PLAYER));
        check("empty miao still prunes only its own namespace", QuestDatabase.INSTANCE.get(MIAO_QUEST) == null
            && QuestDatabase.INSTANCE.get(FOREIGN) == foreign);
        check("empty miao does not rewrite progress or sibling stamp", diskEquals(disk));
    }

    private static void missingStampRetained() throws Exception {
        retainedStamp("missing-stamp", null);
    }

    private static void malformedStampRetained() throws Exception {
        retainedStamp("malformed-stamp", "{not valid JSON");
    }

    private static void retainedStamp(String name, String stamp) throws Exception {
        reset(name);
        seed(QUEST, false);
        writeProgress(playerFile());
        current().setClaimed(PLAYER, 987);
        NBTTagCompound latest = current().writeProgressToNBT(new NBTTagCompound(), null);
        if (stamp == null) Files.delete(BQ_Settings.curWorldDir.toPath().resolve("gtit-injected.json"));
        else writeWorld("gtit-injected.json", stamp);
        Map<Path, byte[]> disk = snapshotProgressDisk();
        BqQuestInjector.inject(ROOT);
        check(name + " refresh keeps exact retained completion/claim/task state", latest.equals(current().writeProgressToNBT(new NBTTagCompound(), null)));
        if (stamp == null) check(name + " completes missing stamp without rewriting progress", stampCurrent() && diskEquals(disk));
        else check(name + " preserves malformed stamp bytes and leaves progress unchanged", stamp.equals(new String(
            Files.readAllBytes(BQ_Settings.curWorldDir.toPath().resolve("gtit-injected.json")), StandardCharsets.UTF_8)) && diskEquals(disk));
    }

    private static void failedStampNonfatal() throws Exception {
        reset("failed-stamp-publication");
        Path stamp = BQ_Settings.curWorldDir.toPath().resolve("gtit-injected.json");
        Files.delete(stamp);
        write(stamp.resolve("keep.txt"), "preserve existing destination");
        Map<Path, byte[]> disk = snapshotDisk();
        BqQuestInjector.inject(ROOT);
        check("failed stamp publication does not prevent quest injection", current() != null);
        check("failed stamp publication preserves destination and leaves no temp", diskEquals(disk));
    }

    private static JsonObject readStamp() throws Exception {
        return new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(
            BQ_Settings.curWorldDir.toPath().resolve("gtit-injected.json")), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void fresh() throws Exception {
        reset("fresh");
        Files.delete(BQ_Settings.curWorldDir.toPath().resolve("gtit-injected.json"));
        Files.delete(BQ_Settings.curWorldDir.toPath()); // Actual missing BQ parent on a fresh world.
        BqQuestInjector.inject(ROOT);
        check("fresh no-progress world creates quest without completion", current() != null && !current().isComplete(PLAYER));
        check("fresh no-progress world publishes current stamp", stampCurrent());
    }

    @SuppressWarnings("unchecked")
    private static void partialOnly(String family) throws Exception {
        String name = "partial-only-" + family;
        reset(name);
        QuestInstance quest = new QuestInstance();
        betterquesting.api.questing.tasks.ITask task;
        if (family.equals("checkbox")) {
            task = new TaskCheckbox();
            quest.getTasks().add(1, new TaskCheckbox()); // One of two done: genuinely incomplete quest.
        } else if (family.equals("retrieval")) {
            bq_standard.tasks.TaskRetrieval retrieval = new bq_standard.tasks.TaskRetrieval();
            retrieval.requiredItems.add(new betterquesting.api.utils.BigItemStack(net.minecraft.init.Items.apple, 2));
            task = retrieval;
        } else {
            bq_standard.tasks.TaskCrafting crafting = new bq_standard.tasks.TaskCrafting();
            crafting.requiredItems.add(new betterquesting.api.utils.BigItemStack(net.minecraft.init.Items.apple, 2));
            task = crafting;
        }
        quest.getTasks().add(0, task);
        NBTTagCompound definition = quest.writeToNBT(new NBTTagCompound());
        NBTConverter.UuidValueType.QUEST.writeId(QUEST, definition);
        write(work.resolve("resources/quest.json"), NBTConverter.NBTtoJSON_Compound(definition, new JsonObject(), true).toString());
        if (family.equals("checkbox")) task.setComplete(PLAYER);
        else {
            NBTTagCompound user = new NBTTagCompound();
            user.setString("uuid", PLAYER.toString());
            user.setIntArray("data", new int[] { 1 }); // Native 1-of-2 item progress, without task completion.
            NBTTagList users = new NBTTagList(); users.appendTag(user);
            NBTTagCompound progress = new NBTTagCompound(); progress.setTag("userProgress", users);
            task.readProgressFromNBT(progress, false);
        }
        QuestDatabase.INSTANCE.put(QUEST, quest);
        writeProgress("QuestProgress.json");
        quest.resetUser(PLAYER, true);
        SaveLoadHandler handler = nativeLoadProgress();
        Field dirty = SaveLoadHandler.class.getDeclaredField("dirtyPlayers"); dirty.setAccessible(true);
        java.util.Set<UUID> dirtyUsers = (java.util.Set<UUID>) dirty.get(handler);
        check(name + " native load restores partial state without a completion entry", partialState(family));
        check(name + " native initial legacy migration does not discover task-only user", !dirtyUsers.contains(PLAYER));
        UUID alreadyDirty = new UUID(901, 902);
        handler.addDirtyPlayers(alreadyDirty);
        nativeDefaultReload(false);
        Map<Path, byte[]> disk = snapshotDisk();
        BqQuestInjector.inject(ROOT);
        check(name + " pre-existing dirty user remains queued", dirtyUsers.contains(alreadyDirty));
        check(name + " injection restores exact partial state without completing quest", partialState(family));
        check(name + " recovered partial-only user queued by known native envelope", dirtyUsers.contains(PLAYER));
        check(name + " injector does not mutate progress files", diskEquals(disk));
        Method save = SaveLoadHandler.class.getDeclaredMethod("saveProgress"); save.setAccessible(true);
        for (java.util.concurrent.Future<Void> future : (java.util.List<java.util.concurrent.Future<Void>>) save.invoke(handler)) future.get();
        check(name + " native save writes offline per-player file", Files.isRegularFile(BQ_Settings.curWorldDir.toPath().resolve(playerFile())));
        QuestDatabase.INSTANCE.clear();
        quest = new QuestInstance(); quest.readFromNBT(definition); QuestDatabase.INSTANCE.put(QUEST, quest);
        nativeLoadProgress();
        check(name + " native reload retains partial progress and no completion entry", partialState(family));
        writeResources();
    }

    private static boolean partialState(String family) {
        QuestInstance quest = current();
        if (quest == null || quest.isComplete(PLAYER) || quest.getCompletionInfo(PLAYER) != null) return false;
        betterquesting.api.questing.tasks.ITask task = quest.getTasks().getValue(0);
        if (family.equals("checkbox")) return task.isComplete(PLAYER) && !quest.getTasks().getValue(1).isComplete(PLAYER);
        if (family.equals("retrieval")) return !task.isComplete(PLAYER) && ((bq_standard.tasks.TaskRetrieval) task).getUsersProgress(PLAYER)[0] == 1;
        return !task.isComplete(PLAYER) && ((bq_standard.tasks.TaskCrafting) task).getUsersProgress(PLAYER)[0] == 1;
    }

    @SuppressWarnings("unchecked")
    private static void migration(boolean definitionAtInitialLoad) throws Exception {
        String name = "offline-native-migration-initial-definition-" + definitionAtInitialLoad;
        reset(name);
        seed(QUEST, true);
        writeProgress("QuestProgress.json");
        if (definitionAtInitialLoad) current().resetUser(PLAYER, true);
        else QuestDatabase.INSTANCE.clear();
        SaveLoadHandler handler = nativeLoadProgress();
        Field dirty = SaveLoadHandler.class.getDeclaredField("dirtyPlayers");
        dirty.setAccessible(true);
        java.util.Set<UUID> dirtyUsers = (java.util.Set<UUID>) dirty.get(handler);
        check(name + " initial native dirty-user behavior", dirtyUsers.contains(PLAYER) == definitionAtInitialLoad);
        UUID alreadyDirty = new UUID(901, 902);
        handler.addDirtyPlayers(alreadyDirty);
        nativeDefaultReload(false);
        Map<Path, byte[]> disk = snapshotDisk();
        BqQuestInjector.inject(ROOT);
        check(name + " completion and claim restored before save", completeAndClaimed(current()));
        check(name + " recovered user queued without replacing existing dirty set",
            dirtyUsers.contains(PLAYER) && dirtyUsers.contains(alreadyDirty));
        check(name + " injector itself leaves every input byte unchanged", diskEquals(disk));
        Method save = SaveLoadHandler.class.getDeclaredMethod("saveProgress");
        save.setAccessible(true);
        java.util.List<java.util.concurrent.Future<Void>> futures =
            (java.util.List<java.util.concurrent.Future<Void>>) save.invoke(handler);
        for (java.util.concurrent.Future<Void> future : futures) future.get();
        Path perPlayer = BQ_Settings.curWorldDir.toPath().resolve(playerFile());
        check(name + " native save creates offline per-player file", Files.isRegularFile(perPlayer));
        QuestDatabase.INSTANCE.clear();
        QuestDatabase.INSTANCE.put(QUEST, definition());
        nativeLoadProgress();
        check(name + " next native load retains serialized completion and claim", completeAndClaimed(current()));
        check(name + " next native load retains native checkbox progress", checkbox(current()).isComplete(PLAYER));
    }

    private static SaveLoadHandler nativeLoadProgress() throws Exception {
        SaveLoadHandler handler = new SaveLoadHandler();
        SaveLoadHandler.INSTANCE = handler;
        for (String name : new String[] { "fileProgress", "dirProgress" }) {
            Field field = SaveLoadHandler.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(handler, new File(BQ_Settings.curWorldDir,
                name.equals("fileProgress") ? "QuestProgress.json" : "QuestProgress"));
        }
        Method load = SaveLoadHandler.class.getDeclaredMethod("loadProgress");
        load.setAccessible(true);
        load.invoke(handler);
        return handler;
    }

    /** Same native snapshot/reload/restore operations used by QuestCommandDefaults. */
    private static void nativeDefaultReload(boolean retainOwned) {
        NBTTagList definitions = QuestDatabase.INSTANCE.writeToNBT(new NBTTagList(), null);
        NBTTagList selected = new NBTTagList();
        for (int i = 0; i < definitions.tagCount(); i++) {
            NBTTagCompound entry = definitions.getCompoundTagAt(i);
            if (retainOwned || !QUEST.equals(NBTConverter.UuidValueType.QUEST.readId(entry))) selected.appendTag(entry);
        }
        NBTTagList progress = QuestDatabase.INSTANCE.writeProgressToNBT(new NBTTagList(), null);
        QuestDatabase.INSTANCE.readFromNBT(selected, false);
        QuestDatabase.INSTANCE.readProgressFromNBT(progress, false);
        QuestLineDatabase.INSTANCE.clear();
    }

    private static void reset(String name) throws Exception {
        QuestDatabase.INSTANCE.clear();
        QuestLineDatabase.INSTANCE.clear();
        SaveLoadHandler.INSTANCE = new SaveLoadHandler();
        Path worlds = work.resolve("worlds");
        Files.createDirectories(worlds);
        BQ_Settings.curWorldDir = Files.createTempDirectory(worlds, name + "-").toFile();
        writeWorld("gtit-injected.json", "{\"gtit\":\"test-same\",\"miao\":\"test-same\"}");
    }

    private static QuestInstance seed(UUID id, boolean claim) {
        QuestInstance quest = definition();
        quest.setComplete(PLAYER, 123);
        if (claim) quest.setClaimed(PLAYER, 124);
        checkbox(quest).setComplete(PLAYER);
        QuestDatabase.INSTANCE.put(id, quest);
        return quest;
    }

    private static QuestInstance definition() {
        QuestInstance quest = new QuestInstance();
        quest.setProperty(NativeProps.NAME, "progress fixture");
        quest.getTasks().add(0, new TaskCheckbox());
        return quest;
    }

    private static TaskCheckbox checkbox(QuestInstance quest) {
        return (TaskCheckbox) quest.getTasks().getValue(0);
    }

    private static QuestInstance current() {
        return (QuestInstance) QuestDatabase.INSTANCE.get(QUEST);
    }

    /** Deliberately inspect the persisted bit, not hasClaimed's zero-reward shortcut. */
    private static boolean claimed(QuestInstance quest) {
        return quest.getCompletionInfo(PLAYER) != null && quest.getCompletionInfo(PLAYER).getBoolean("claimed");
    }

    private static boolean completeAndClaimed(QuestInstance quest) {
        return quest != null && quest.isComplete(PLAYER) && claimed(quest);
    }

    private static void writeProgress(String file) throws Exception {
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("questProgress", QuestDatabase.INSTANCE.writeProgressToNBT(new NBTTagList(), null));
        writeWorld(file, NBTConverter.NBTtoJSON_Compound(root, new JsonObject(), true).toString());
    }

    private static String playerFile() {
        return "QuestProgress/" + PLAYER + ".json";
    }

    private static void writeWorld(String file, String contents) throws Exception {
        write(BQ_Settings.curWorldDir.toPath().resolve(file), contents);
    }

    private static void write(Path file, String contents) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean stampCurrent() throws Exception {
        if (!Files.isRegularFile(BQ_Settings.curWorldDir.toPath().resolve("gtit-injected.json"))) return false;
        String text = new String(Files.readAllBytes(BQ_Settings.curWorldDir.toPath().resolve("gtit-injected.json")), StandardCharsets.UTF_8);
        return new com.google.gson.JsonParser().parse(text).getAsJsonObject().get("gtit").getAsString().equals("test-same");
    }

    private static Map<Path, byte[]> snapshotProgressDisk() throws Exception {
        Map<Path, byte[]> files = snapshotDisk();
        files.remove(BQ_Settings.curWorldDir.toPath().resolve("gtit-injected.json"));
        return files;
    }

    private static Map<Path, byte[]> snapshotDisk() throws Exception {
        Map<Path, byte[]> files = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(BQ_Settings.curWorldDir.toPath())) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) files.put(path, Files.readAllBytes(path));
        }
        return files;
    }

    private static boolean diskEquals(Map<Path, byte[]> expected) throws Exception {
        Map<Path, byte[]> current = snapshotDisk();
        current.keySet().removeIf(p -> p.getFileName().toString().equals("gtit-injected.json") && !expected.containsKey(p));
        if (!current.keySet().equals(expected.keySet())) return false;
        for (Path path : expected.keySet()) if (!Arrays.equals(expected.get(path), current.get(path))) return false;
        return true;
    }

    private static void writeResources() throws Exception {
        writePackResources(ROOT, QUEST, "");
        write(work.resolve("resources/" + MIAO_ROOT + "index.json"), "{\"questLines\":[]}");
    }

    private static void writePackResources(String root, UUID questId, String prefix) throws Exception {
        Path resources = work.resolve("resources");
        long high = questId.getMostSignificantBits(), low = questId.getLeastSignificantBits();
        NBTTagCompound quest = definition().writeToNBT(new NBTTagCompound());
        NBTConverter.UuidValueType.QUEST.writeId(questId, quest);
        write(resources.resolve(prefix + "quest.json"), NBTConverter.NBTtoJSON_Compound(quest, new JsonObject(), true).toString());
        write(resources.resolve(prefix + "entry.json"), "{\"questIDHigh:4\":" + high + ",\"questIDLow:4\":" + low + "}");
        write(resources.resolve(prefix + "line.json"), "{\"questLineIDHigh:4\":" + high + ",\"questLineIDLow:4\":1}");
        JsonObject entry = new JsonObject();
        entry.addProperty("questFile", prefix + "quest.json");
        entry.addProperty("entryFile", prefix + "entry.json");
        JsonArray entries = new JsonArray(); entries.add(entry);
        JsonObject line = new JsonObject();
        line.addProperty("idHigh", high); line.addProperty("idLow", 1); line.addProperty("orderIndex", 900);
        line.addProperty("lineFile", prefix + "line.json"); line.add("entries", entries);
        JsonArray lines = new JsonArray(); lines.add(line);
        JsonObject index = new JsonObject(); index.add("questLines", lines);
        write(resources.resolve(root + "index.json"), index.toString());
    }

    private static void check(String name, boolean passed) {
        checks++;
        if (!passed) failures++;
        System.out.println((passed ? "PASS " : "FAIL ") + name);
    }
}
