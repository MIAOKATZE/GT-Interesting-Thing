package com.miaokatze.gtit.gift;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.config.DefaultContentConfig;
import com.miaokatze.gtit.config.GiftConfig;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.v2.NekoBqQuestIdParser;

/** Real Gson/NBT/registry persistence and GT energy-map transaction regression coverage. */
public final class GiftSystemTest {

    private GiftSystemTest() {}

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(GiftSystemTest.class, args)) return;
        Path root = Files.createTempDirectory("gtit-gift-regression-");
        migrationAndUpdates(root);
        eraDefaultsAndUpgrade(root);
        shellBindingMigration(root);
        malformedAndFailedWrites(root);
        itemAndEnergyTransactions();
        mixedDefaultPolicies();
        capacityBoundaries(root);
        validation();
        System.out.println(
            "GiftSystemTest: migration, empty/custom/default preservation, revisions, update policies, atomic writes, NBT and wireless receipts passed");
    }

    private static void eraDefaultsAndUpgrade(Path root) throws Exception {
        java.util.List<GiftDefinition> defaults = GiftDefaults.create();
        GiftDefinition starter = defaults.get(0);
        check(
            GiftFiles.JSON.toJson(starter.rewards)
                .equals(GiftFiles.JSON.toJson(GiftConfig.createDefaultGuaranteedItems()))
                && GiftFiles.JSON.toJson(starter.randomRewards)
                    .equals(GiftFiles.JSON.toJson(GiftConfig.createDefaultRandomItems()))
                && starter.randomCount == 2
                && starter.trigger == GiftDefinition.Trigger.FIRST_LOGIN,
            "Era update leaves authored starter rewards and first-login trigger unchanged");
        int[] quests = { 44, 88, 1148, 160, 176, 213, 1493 };
        int[] motors = { 32600, 32601, 32602, 32603, 32604, 32606 };
        for (int i = 1; i < defaults.size(); i++) {
            GiftDefinition era = defaults.get(i);
            check(
                era.trigger == GiftDefinition.Trigger.BQ_COMPLETE && era.questId.equals("0:" + quests[i - 1])
                    && era.id.equals(era.shellKey)
                    && era.updatePolicy == GiftDefinition.UpdatePolicy.RECOMMENDED
                    && era.rewards.size() == 4
                    && era.randomRewards.isEmpty()
                    && era.randomCount == 0,
                "Era identity, completion quest, carrier and recommended policy are stable: " + era.id);
            check(
                era.rewards.stream()
                    .allMatch(reward -> reward.itemId.startsWith("gregtech:")),
                "Era materials replace every old food, torch, pearl and coin reward: " + era.id);
            if (i > 1) {
                int tier = i - 2;
                reward(era, 0, "gregtech:gt.blockmachines", 4, 11 + tier);
                reward(era, 1, "gregtech:gt.metaitem.01", 4, 32610 + tier);
                reward(era, 2, "gregtech:gt.metaitem.01", 4, 32630 + tier);
                reward(era, 3, "gregtech:gt.metaitem.01", 8, motors[tier]);
            }
        }
        reward(defaults.get(1), 0, "gregtech:gt.blockcasings", 16, 10);
        reward(defaults.get(1), 1, "gregtech:gt.blockmachines", 32, 30729);
        reward(defaults.get(1), 2, "gregtech:gt.blockmachines", 256, 30727);
        reward(defaults.get(1), 3, "gregtech:gt.blockmachines", 2, 114);

        GiftRegistry registry = new GiftRegistry();
        Path file = root.resolve("era-v1-upgrade.json");
        registry.init(file);
        GiftRegistry.Snapshot previous = registry.snapshot();
        previous.defaultVersion = 1;
        previous.evaluatedDefaultVersion = 1;
        for (GiftDefinition era : previous.definitions) {
            if ("starter".equals(era.id)) continue;
            era.rewards.clear();
            era.rewards.add(new GiftConfig.ItemEntry("minecraft:bread", 16, 0));
            era.rewards.add(new GiftConfig.ItemEntry("minecraft:torch", 64, 0));
            era.rewards.add(new GiftConfig.ItemEntry("minecraft:ender_pearl", 2, 0));
            era.rewards.add(new GiftConfig.ItemEntry("gtit:neko_coin", 1, 0));
            if ("lv".equals(era.id)) era.name = "User edited LV";
        }
        previous.definitions.removeIf(gift -> "mv".equals(gift.id));
        GiftFiles.write(file, previous);
        String oldStarter = GiftFiles.JSON.toJson(previous.definitions.get(0));
        String oldSteam = GiftFiles.JSON.toJson(previous.definitions.get(1));
        registry.init(file);
        check(
            "ASK".equals(registry.snapshot().updatePrompt) && registry.get("mv") == null
                && "User edited LV".equals(registry.get("lv").name)
                && oldStarter.equals(GiftFiles.JSON.toJson(registry.get("starter")))
                && oldSteam.equals(GiftFiles.JSON.toJson(registry.get("steam"))),
            "Version-one recommended upgrade retains old rewards, user edits and deleted eras before acceptance");
        registry.resolveDefaultUpdate("SKIP", registry.snapshot().revision);
        registry.init(file);
        check(
            registry.snapshot().updatePrompt.isEmpty() && registry.get("mv") == null
                && "User edited LV".equals(registry.get("lv").name)
                && oldStarter.equals(GiftFiles.JSON.toJson(registry.get("starter")))
                && oldSteam.equals(GiftFiles.JSON.toJson(registry.get("steam"))),
            "Skipping version-two materials survives restart without mutating old definitions");
        registry.resolveDefaultUpdate("APPLY", registry.snapshot().revision);
        check(
            registry.snapshot().defaultVersion == 2 && registry.get("mv") != null
                && GiftFiles.JSON.toJson(registry.get("steam").rewards)
                    .equals(GiftFiles.JSON.toJson(defaults.get(1).rewards)),
            "Explicit acceptance installs era materials through existing whole-default replacement semantics");
    }

    private static void reward(GiftDefinition gift, int index, String item, int amount, int meta) {
        GiftConfig.ItemEntry actual = gift.rewards.get(index);
        check(
            item.equals(actual.itemId) && actual.amount == amount && actual.meta == meta,
            "Exact material descriptor and quantity for " + gift.id + " reward " + index);
    }

    private static void migrationAndUpdates(Path root) throws Exception {
        NBTTagCompound special = new NBTTagCompound();
        special.setString("marker", "legacy-nbt");
        GiftConfig.setGuaranteedItems(Arrays.asList(new GiftConfig.ItemEntry("minecraft:bread", 7, 0, special)));
        GiftConfig.setRandomItems(java.util.Collections.emptyList());
        GiftConfig.setRandomCount(0);
        GiftRegistry registry = new GiftRegistry();
        Path file = root.resolve("registry.json");
        registry.init(file);
        check(registry.snapshot().definitions.size() == 8, "Initial registry has eight defaults");
        for (GiftDefinition definition : registry.snapshot().definitions) {
            check(definition.id.equals(definition.shellKey), "Each default era binds its matching physical carrier");
        }
        GiftDefinition starter = registry.get("starter");
        check(starter.rewards.size() == 1 && starter.rewards.get(0).amount == 7, "Legacy content migrates");
        check(
            "legacy-nbt".equals(
                starter.rewards.get(0)
                    .toItemStack()
                    .getTagCompound()
                    .getString("marker")),
            "Legacy NBT migrates");
        check(
            starter.randomRewards.isEmpty() && starter.randomCount == 0,
            "Explicitly empty legacy random pool survives");
        GiftDefinition custom = simple("custom");
        custom = registry.save(custom, 0);
        check(
            custom.revision == registry.snapshot().revision,
            "New definitions receive globally monotonic server revision");
        GiftDefinition stale = custom.copy();
        custom.name = "Changed";
        custom = registry.save(custom, custom.revision);
        expectFailure(() -> registry.save(stale, stale.revision), "Stale save rejected");
        stale.name = "Must not mutate registry";
        check("Changed".equals(registry.get("custom").name), "Snapshots and caller DTOs are isolated");
        registry.setDefaultsEnabled(false, registry.snapshot().revision);
        check(
            !registry.isEnabled(registry.get("starter")) && registry.isEnabled(custom),
            "Default toggle preserves custom availability");
        registry.init(file);
        check(!registry.snapshot().defaultsEnabled && registry.get("custom") != null, "Disabled state persists");
        registry.setDefaultsEnabled(true, registry.snapshot().revision);
        GiftDefinition steam = registry.get("steam");
        registry.delete("steam", steam.revision);
        registry.init(file);
        check(registry.get("steam") == null, "Deleted default does not regenerate on same-version restart");
        GiftRegistry.Snapshot older = registry.snapshot();
        older.defaultVersion = 0;
        older.evaluatedDefaultVersion = 0;
        GiftFiles.write(file, older);
        registry.init(file);
        check("ASK".equals(registry.snapshot().updatePrompt), "Recommended update asks before replacing edits");
        registry.resolveDefaultUpdate("SKIP", registry.snapshot().revision);
        registry.init(file);
        check(
            registry.snapshot().updatePrompt.isEmpty() && registry.get("steam") == null,
            "Skip survives restart and retains deletion");
        registry.resolveDefaultUpdate("APPLY", registry.snapshot().revision);
        check(
            registry.get("steam") != null && "Changed".equals(registry.get("custom").name),
            "Applying defaults preserves custom content");
        older = registry.snapshot();
        older.defaultVersion = 0;
        older.evaluatedDefaultVersion = 0;
        older.skippedVersion = 0;
        GiftFiles.write(file, older);
        registry.init(file);
        check(registry.snapshot().updatePrompt.isEmpty(), "Content-identical version update silently acknowledges");
        older = registry.snapshot();
        older.forcedVersion = -1;
        for (GiftDefinition gift : older.definitions) if (gift.builtIn) gift.name = "Edited default";
        GiftFiles.write(file, older);
        registry.init(file);
        check("FORCE".equals(registry.snapshot().updatePrompt), "Author forced tag replaces edited defaults");
        check(
            !"Edited default".equals(registry.get("starter").name) && registry.get("custom") != null,
            "Forced updates touch only built-ins");
        registry.resolveDefaultUpdate("ACK", registry.snapshot().revision);
        registry.init(file);
        check(registry.snapshot().updatePrompt.isEmpty(), "Forced notification acknowledgement persists");
        GiftDefinition oldCustom = registry.get("custom");
        registry.delete(oldCustom.id, oldCustom.revision);
        GiftDefinition recreated = registry.save(simple(oldCustom.id), 0);
        check(recreated.revision > oldCustom.revision, "Deleting and recreating never reuses a definition revision");
        expectFailure(
            () -> registry.save(oldCustom, oldCustom.revision),
            "Pre-deletion stale editor cannot overwrite recreated gift");
        java.lang.reflect.Field gate = DefaultContentConfig.class.getDeclaredField("gifts");
        gate.setAccessible(true);
        boolean previous = gate.getBoolean(null);
        try {
            gate.setBoolean(null, false);
            check(
                !registry.isEnabled(registry.get("starter")) && registry.isEnabled(registry.get("custom")),
                "Global switch only gates defaults");
            Path disabled = root.resolve("disabled-new.json");
            registry.init(disabled);
            check(registry.snapshot().definitions.isEmpty(), "Global disable prevents initial injection");
            registry.init(disabled);
            check(registry.snapshot().definitions.isEmpty(), "Empty registry is stable");
        } finally {
            gate.setBoolean(null, previous);
        }
    }

    private static void mixedDefaultPolicies() {
        GiftRegistry.Snapshot state = new GiftRegistry.Snapshot();
        state.revision = 5;
        state.evaluatedDefaultVersion = 1;
        GiftDefinition forced = simple("steam");
        forced.builtIn = true;
        forced.revision = 3;
        forced.name = "Edited steam";
        GiftDefinition recommended = simple("lv");
        recommended.builtIn = true;
        recommended.revision = 4;
        recommended.name = "Edited LV";
        GiftDefinition custom = simple("custom-policy");
        custom.revision = 5;
        state.definitions.add(forced);
        state.definitions.add(recommended);
        state.definitions.add(custom);
        GiftDefinition incomingForced = forced.copy();
        incomingForced.name = "Authored steam";
        incomingForced.updatePolicy = GiftDefinition.UpdatePolicy.FORCED;
        GiftDefinition incomingRecommended = recommended.copy();
        incomingRecommended.name = "Authored LV";
        incomingRecommended.updatePolicy = GiftDefinition.UpdatePolicy.RECOMMENDED;
        GiftRegistry.evaluateDefaultUpgrade(state, Arrays.asList(incomingForced, incomingRecommended), 2);
        check(
            state.definitions.stream()
                .anyMatch(g -> "Authored steam".equals(g.name)),
            "Incoming forced definition overwrites only itself");
        check(
            state.definitions.stream()
                .anyMatch(g -> "Edited LV".equals(g.name)),
            "Incoming recommended definition preserves edits");
        check(
            state.forcedNotificationPending && state.pendingDefaultIds.equals(Arrays.asList("lv")),
            "Mixed updates retain forced notice and recommended request");
        check(
            state.definitions.stream()
                .anyMatch(g -> "custom-policy".equals(g.id)),
            "Mixed updates preserve custom gift");
        for (GiftDefinition gift : state.definitions)
            if (gift.id.equals("steam")) gift.name = "Edited again after release";
        GiftRegistry.evaluateDefaultUpgrade(state, Arrays.asList(incomingForced, incomingRecommended), 2);
        check(
            state.definitions.stream()
                .anyMatch(g -> "Edited again after release".equals(g.name)),
            "Same authored version does not reapply forced updates");
        state.definitions.removeIf(g -> g.id.equals("lv"));
        GiftRegistry.evaluateDefaultUpgrade(state, Arrays.asList(incomingForced, incomingRecommended), 3);
        check(
            state.definitions.stream()
                .noneMatch(g -> g.id.equals("lv")) && state.pendingDefaultIds.contains("lv"),
            "Recommended deleted definition remains deleted until acceptance");
    }

    private static void shellBindingMigration(Path root) throws Exception {
        GiftRegistry registry = new GiftRegistry();
        Path file = root.resolve("shell-migration.json");
        registry.init(file);
        GiftRegistry.Snapshot old = registry.snapshot();
        for (GiftDefinition definition : old.definitions) {
            definition.shellKey = null;
            definition.name = "User edited " + definition.id;
            definition.rewards.clear();
            definition.rewards.add(new GiftConfig.ItemEntry("minecraft:diamond", 37, 0));
        }
        old.definitions.removeIf(g -> "steam".equals(g.id));
        old.defaultsEnabled = false;
        old.updateNotice = false;
        GiftFiles.write(file, old);
        registry.init(file);
        check(
            registry.snapshot().definitions.size() == 7 && registry.get("steam") == null,
            "Binding migration does not restore deleted defaults");
        check(
            !registry.snapshot().defaultsEnabled && !registry.snapshot().updateNotice,
            "Binding migration preserves default switches");
        for (GiftDefinition definition : registry.snapshot().definitions) {
            check(definition.id.equals(definition.shellKey), "Every old era binds its physical item");
            check(
                definition.name.equals("User edited " + definition.id) && definition.rewards.size() == 1
                    && definition.rewards.get(0).amount == 37,
                "Binding-only migration preserves user names and rewards");
        }
        long revision = registry.snapshot().revision;
        registry.init(file);
        check(registry.snapshot().revision == revision, "Binding migration is persisted exactly once");
        GiftDefinition first = simple("shared-shell-a");
        first.shellKey = "festival";
        GiftDefinition second = simple("shared-shell-b");
        second.shellKey = "festival";
        registry.save(first, 0);
        registry.save(second, 0);
        registry.init(file);
        check(
            registry.boundDefinitions("festival")
                .size() == 2,
            "Multiple definitions may persist the same shell without a uniqueness restriction");
        check(
            "gtit:starter_gift".equals(GiftShellCatalog.registryName("starter")),
            "Historical starter registry name is retained");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String key : GiftIconCatalog.keys()) names.add(GiftShellCatalog.registryName(key));
        check(names.size() == 32, "All 32 catalog shells have independent registry identities");
        first.shellKey = "unknown";
        expectFailure(first::validate, "Unknown physical shell binding is rejected");
    }

    private static void capacityBoundaries(Path root) throws Exception {
        java.lang.reflect.Field gate = DefaultContentConfig.class.getDeclaredField("gifts");
        gate.setAccessible(true);
        boolean previous = gate.getBoolean(null);
        try {
            gate.setBoolean(null, false);
            GiftRegistry registry = new GiftRegistry();
            Path file = root.resolve("capacity.json");
            registry.init(file);
            for (int i = 0; i < GiftRegistry.MAX_DEFINITIONS; i++) registry.save(simple("capacity-" + i), 0);
            byte[] full = Files.readAllBytes(file);
            long revision = registry.snapshot().revision;
            gate.setBoolean(null, true);
            expectFailure(
                () -> registry.setDefaultsEnabled(true, revision),
                "Enabling defaults cannot exceed registry bound");
            expectFailure(
                () -> registry.resolveDefaultUpdate("APPLY", revision),
                "Applying defaults cannot exceed registry bound");
            check(
                Arrays.equals(full, Files.readAllBytes(file)) && registry.snapshot().definitions.size() == 128,
                "Capacity rejection preserves live and persisted custom definitions");
            GiftRegistry.Snapshot snapshot = registry.snapshot();
            GiftDefinition forced = simple("steam");
            forced.builtIn = true;
            forced.updatePolicy = GiftDefinition.UpdatePolicy.FORCED;
            expectFailure(
                () -> GiftRegistry.evaluateDefaultUpgrade(snapshot, Arrays.asList(forced), GiftDefaults.VERSION + 1),
                "Forced insertion cannot exceed registry bound");
            check(
                snapshot.definitions.size() == 128 && snapshot.pendingDefaultIds.isEmpty(),
                "Capacity preflight precedes update mutation");
            gate.setBoolean(null, false);
            registry.init(file);
            check(registry.snapshot().definitions.size() == 128, "Rejected updates leave a reloadable registry");
        } finally {
            gate.setBoolean(null, previous);
        }
    }

    private static void malformedAndFailedWrites(Path root) throws Exception {
        Path bad = root.resolve("malformed.json");
        byte[] original = "{ broken gift config".getBytes(StandardCharsets.UTF_8);
        Files.write(bad, original);
        GiftRegistry registry = new GiftRegistry();
        try {
            registry.init(bad);
            throw new AssertionError("Malformed registry was accepted");
        } catch (java.io.IOException expected) {
            check(!registry.isReady(), "Malformed registry fails closed");
        }
        check(Arrays.equals(original, Files.readAllBytes(bad)), "Malformed original is never overwritten");
        Path file = root.resolve("failure.json");
        registry.init(file);
        GiftDefinition custom = registry.save(simple("failure-test"), 0);
        Path backup = root.resolve("failure.backup");
        Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        Files.createDirectory(file);
        custom.name = "Should not publish";
        expectFailure(() -> registry.save(custom, custom.revision), "Write failure propagated");
        check(
            !"Should not publish".equals(registry.get(custom.id).name),
            "Failed writes do not publish in-memory state");
        Files.delete(file);
        Files.move(backup, file);
    }

    private static void itemAndEnergyTransactions() {
        String token = UUID.randomUUID()
            .toString();
        UUID target = UUID.randomUUID();
        BigInteger amount = new BigInteger("1234567890123456789012345678901234567890");
        Map<UUID, BigInteger> map = new LinkedHashMap<>();
        map.put(target, BigInteger.TEN);
        check(GiftWirelessEU.apply(map, token, target, amount), "First wireless transaction applies");
        check(
            map.get(target)
                .equals(amount.add(BigInteger.TEN)),
            "Wireless amount never narrows to long");
        Map<UUID, BigInteger> reloaded = new LinkedHashMap<>(map);
        check(!GiftWirelessEU.apply(reloaded, token, target, amount), "Serialized-map replay is idempotent");
        UUID otherTeam = UUID.randomUUID();
        check(
            !GiftWirelessEU.apply(reloaded, token, otherTeam, amount) && !reloaded.containsKey(otherTeam),
            "Team changes cannot replay a token");
        expectFailure(
            () -> GiftWirelessEU.apply(
                map,
                UUID.randomUUID()
                    .toString(),
                target,
                BigInteger.ONE.negate()),
            "Negative wireless payment rejected");
        expectFailure(() -> GiftWirelessEU.receiptKey("forged-token"), "Malformed receipt token rejected");
        ItemStack a = new ItemStack(Items.bread, 63);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("marker", "first");
        a.setTagCompound(tag);
        ItemStack b = a.copy();
        b.stackSize = 2;
        ItemStack[] inventory = { a, null };
        check(GiftService.insert(inventory, Arrays.asList(b)), "Exact inventory insertion plan succeeds");
        check(inventory[0].stackSize == 64 && inventory[1].stackSize == 1, "Stack splitting preserves all items");
        ItemStack distinct = b.copy();
        distinct.getTagCompound()
            .setString("marker", "second");
        check(
            !GiftService.insert(new ItemStack[] { a.copy() }, Arrays.asList(distinct)),
            "Different NBT cannot merge into a full inventory");
        check(
            NekoBqQuestIdParser.parse("0:44")
                .equals(NekoBqQuestIdParser.parse("AAAAAAAAAAAAAAAAAAAALA==")),
            "BQ IDs use trade-machine parser");
        ItemStack packageItem = new ItemStack(Items.bread, 1);
        ItemStack[] smallInventory = { packageItem, null, null };
        java.util.List<ItemStack> largeGift = Arrays.asList(
            new ItemStack(Items.paper, 64),
            new ItemStack(Items.paper, 64),
            new ItemStack(Items.paper, 64),
            new ItemStack(Items.paper, 64));
        GiftService.Withdrawal first = GiftService.planWithdrawal(smallInventory, 0, largeGift, 0);
        check(
            first != null && !first.complete && first.cursor == 2 && first.inventory[0] != null,
            "Oversized gifts deliver a bounded prefix and retain their package");
        check(smallInventory[1] == null && smallInventory[2] == null, "Withdrawal planning never mutates live input");
        first.inventory[1] = null;
        first.inventory[2] = null;
        GiftService.Withdrawal second = GiftService.planWithdrawal(first.inventory, 0, largeGift, first.cursor);
        check(
            second != null && second.complete && second.cursor == 4 && second.inventory[0].getItem() == Items.paper,
            "Saved cursor resumes remaining suffix and consumes the package only after completion");
    }

    private static void validation() {
        GiftDefinition gift = simple("bounds");
        gift.wirelessEU = "-1";
        expectFailure(gift::validate, "Negative EU definition rejected");
        gift.wirelessEU = new String(new char[129]).replace('\0', '1');
        expectFailure(gift::validate, "Oversized EU rejected");
        gift.wirelessEU = "0";
        gift.randomCount = 1;
        expectFailure(gift::validate, "Invalid random count rejected");
        gift.randomCount = 0;
        gift.trigger = GiftDefinition.Trigger.BQ_COMPLETE;
        gift.questId = "missing";
        expectFailure(gift::validate, "Invalid BQ ID rejected");
        gift.enabled = false;
        gift.validate();
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream output = new java.io.DataOutputStream(bytes);
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(7);
            output.writeUTF("forged-array");
            output.writeInt(0x20000000);
            output.writeByte(0);
            String malicious = java.util.Base64.getEncoder()
                .encodeToString(bytes.toByteArray());
            expectFailure(
                () -> GiftNbt.decode(malicious),
                "Overflowing vanilla NBT array length rejected before allocation");
            NBTTagCompound valid = new NBTTagCompound();
            valid.setString("text", "Unicode 礼包");
            String encoded = com.miaokatze.gtit.util.NbtBase64Util.nbtToBase64(valid);
            check(
                "Unicode 礼包".equals(
                    GiftNbt.decode(encoded)
                        .getString("text")),
                "Bounded NBT round trip preserves content");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static GiftDefinition simple(String id) {
        GiftDefinition result = new GiftDefinition();
        result.id = id;
        result.name = id;
        result.trigger = GiftDefinition.Trigger.FIRST_LOGIN;
        result.icon = null;
        return result;
    }

    private static void expectFailure(Runnable operation, String message) {
        try {
            operation.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
