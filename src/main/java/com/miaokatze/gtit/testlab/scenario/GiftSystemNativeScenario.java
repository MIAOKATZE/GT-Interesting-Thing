package com.miaokatze.gtit.testlab.scenario;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;

import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.google.gson.Gson;
import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.config.DefaultContentConfig;
import com.miaokatze.gtit.config.GiftConfig;
import com.miaokatze.gtit.config.GiftConfig.ItemEntry;
import com.miaokatze.gtit.gift.GiftDefinition;
import com.miaokatze.gtit.gift.GiftIconCatalog;
import com.miaokatze.gtit.gift.GiftRegistry;
import com.miaokatze.gtit.gift.GiftService;
import com.miaokatze.gtit.gift.GiftShellCatalog;
import com.miaokatze.gtit.gift.GiftWirelessEU;
import com.miaokatze.gtit.gift.ui.GiftAccessHandler;
import com.miaokatze.gtit.gift.ui.GiftGuiFactory;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;

import betterquesting.api.events.QuestEvent;
import cpw.mods.fml.common.Loader;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.common.misc.spaceprojects.SpaceProjectWorldSavedData;
import gtPlusPlus.xmod.gregtech.registration.gregtech.GregtechConduits.PipeStats;

/** Real inventory/player.dat/GT energy operations, confined to synthetic players and an isolated registry. */
final class GiftSystemNativeScenario implements ProbeScenario {

    @Override
    public String id() {
        return "native-gift-system";
    }

    @Override
    public ProbeTask start(ProbeContext context) {
        return () -> {
            Path root = IsolatedProbeFiles.directory(context, "gift");
            FakePlayer owner = context.player("gift-owner");
            FakePlayer outsider = context.player("gift-outsider");
            FakePlayer offline = context.player("gift-offline-member");
            FakePlayer full = context.player("gift-full-inventory");
            for (FakePlayer player : Arrays.asList(owner, outsider, offline, full)) ownPlayerFiles(context, player);
            try (GiftScope scope = new GiftScope(root)) {
                exercise(context, owner, outsider, offline, full);
            }
            context.skip(
                "physical-login-network-and-ui",
                "Fake players have no physical client; onLogin API and synthetic BQ event dispatch are exercised explicitly");
            return true;
        };
    }

    private static void exercise(ProbeContext context, FakePlayer owner, FakePlayer outsider, FakePlayer offline,
        FakePlayer full) throws Exception {
        GiftRegistry registry = GiftRegistry.INSTANCE;
        GiftService service = GiftService.INSTANCE;
        java.util.Set<net.minecraft.item.Item> shellItems = new java.util.HashSet<>();
        for (String key : GiftIconCatalog.keys()) {
            ItemStack shell = GiftShellCatalog.stack(key);
            context.check(
                "registered-shell-" + key,
                shell != null && key.equals(GiftShellCatalog.key(shell))
                    && GiftShellCatalog.registryName(key)
                        .equals(net.minecraft.item.Item.itemRegistry.getNameForObject(shell.getItem()))
                    && shellItems.add(shell.getItem()),
                "Physical Forge item exists with its own registry identity and fixed shell: "
                    + GiftShellCatalog.registryName(key));
        }
        GiftDefinition definition = new GiftDefinition();
        definition.id = "lab-" + context.runId();
        definition.name = "Native gift " + context.runId();
        definition.trigger = GiftDefinition.Trigger.FIRST_LOGIN;
        definition.shellKey = "lv";
        definition.wirelessEU = "1234567";
        ItemStack reward = NativeFixtures.specialStack(context, 65);
        definition.rewards.add(new ItemEntry("minecraft:paper", 65, 0, reward.getTagCompound()));
        GiftDefinition saved = registry.save(definition, 0);
        boolean staleRejected = false;
        try {
            registry.save(definition, 0);
        } catch (IllegalStateException expected) {
            staleRejected = true;
        }
        context.check(
            "gift-revision-conflict",
            staleRejected && registry.get(definition.id).revision == 1,
            "Real registry rejects stale creation revision and preserves the existing definition");
        context.check(
            "custom-enabled-with-defaults-disabled",
            registry.isEnabled(saved),
            "Isolated snapshot defaultsEnabled=false leaves custom definitions enabled");
        GiftDefinition builtIn = saved.copy();
        builtIn.builtIn = true;
        context.check(
            "built-in-disabled-boundary",
            !registry.isEnabled(builtIn),
            "Built-in definitions respect the isolated defaults switch");

        service.onLogin(owner);
        ItemStack gift = findPackage(owner, definition.id);
        context.check(
            "first-login-package",
            gift != null && "lv".equals(GiftShellCatalog.key(gift))
                && service.status(owner, definition.id) == GiftService.Status.ISSUED,
            "Production onLogin API persists an owner-bound package for a disconnected synthetic player");
        if (gift == null) return;
        String token = gift.getTagCompound()
            .getString(GiftService.TAG_TOKEN);
        UUID receipt = GiftWirelessEU.receiptKey(token);
        ownReceipt(context, receipt);
        service.onLogin(owner);
        context.check(
            "repeat-login-no-double-grant",
            packageCount(owner, definition.id) == 1,
            "Replaying onLogin keeps one issued package");
        context.check(
            "wrong-owner-refused",
            !service.open(outsider, gift),
            "Different synthetic UUID cannot open this owner's package");
        ItemStack tampered = gift.copy();
        tampered.getTagCompound()
            .setString(
                GiftService.TAG_TOKEN,
                UUID.randomUUID()
                    .toString());
        int slot = slotOf(owner, gift);
        owner.inventory.currentItem = slot;
        ItemStack wrongShell = GiftShellCatalog.stack("hv");
        wrongShell.setTagCompound(
            (NBTTagCompound) gift.getTagCompound()
                .copy());
        owner.inventory.setInventorySlotContents(slot, wrongShell);
        context.check(
            "valid-token-wrong-physical-shell-refused",
            !service.open(owner, wrongShell),
            "An authentic token cannot be transferred from its authorized LV carrier to the HV item");
        ItemStack wrongStarter = GiftShellCatalog.stack("starter");
        wrongStarter.setTagCompound(
            (NBTTagCompound) gift.getTagCompound()
                .copy());
        owner.inventory.setInventorySlotContents(slot, wrongStarter);
        context.check(
            "new-token-starter-substitution-refused",
            !service.open(owner, wrongStarter),
            "New grants cannot use the historical StarterGift compatibility exception");
        for (String key : GiftIconCatalog.keys()) {
            if ("starter".equals(key)) continue;
            ItemStack raw = GiftShellCatalog.stack(key);
            owner.inventory.setInventorySlotContents(slot, raw);
            boolean refused = !service.open(owner, raw) && !raw.hasTagCompound();
            raw.setTagCompound(new NBTTagCompound());
            refused &= !service.open(owner, raw) && !raw.getTagCompound()
                .hasKey(GiftService.TAG_TOKEN);
            context.check(
                "raw-shell-no-authorization-" + key,
                refused,
                "Untagged and empty-NBT creative/NEI shells cannot create grants or rewards");
        }
        owner.inventory.setInventorySlotContents(slot, tampered);
        context.check(
            "tampered-token-refused",
            !service.open(owner, tampered),
            "Unknown token cannot resolve an authoritative server grant");
        owner.inventory.setInventorySlotContents(slot, gift);
        BigInteger before = GlobalVariableStorage.GlobalEnergy.getOrDefault(owner.getUniqueID(), BigInteger.ZERO);
        ItemStack replay = gift.copy();
        boolean opened = service.open(owner, gift);
        BigInteger after = GlobalVariableStorage.GlobalEnergy.getOrDefault(owner.getUniqueID(), BigInteger.ZERO);
        int exactRewards = 0;
        for (ItemStack stack : owner.inventory.mainInventory) {
            if (stack != null && stack.isItemEqual(reward) && ItemStack.areItemStackTagsEqual(stack, reward)) {
                exactRewards += stack.stackSize;
            }
        }
        context.check(
            "open-preserves-ordinary-item-nbt",
            opened && exactRewards == 65,
            "Real opening splits 65 paper across stacks and preserves nested Unicode/list/byte-array NBT; count="
                + exactRewards);
        context.check(
            "real-wireless-eu-delta",
            after.subtract(before)
                .equals(new BigInteger(definition.wirelessEU))
                && GlobalVariableStorage.GlobalEnergy.containsKey(receipt),
            "Actual GT GlobalEnergy delta=" + after.subtract(before) + "; durable-map receipt present");
        service.onLogin(owner); // Real deliver branch replays energy transaction from opened player receipt.
        owner.inventory.currentItem = 8;
        owner.inventory.setInventorySlotContents(8, replay);
        boolean reopened = service.open(owner, replay);
        context.check(
            "opened-receipt-replay-idempotent",
            !reopened && after.equals(GlobalVariableStorage.GlobalEnergy.get(owner.getUniqueID())),
            "Login energy replay plus duplicate package opening neither credits twice nor issues another reward");
        owner.inventory.setInventorySlotContents(8, null);

        for (int i = 0; i < full.inventory.mainInventory.length; i++) {
            full.inventory.setInventorySlotContents(i, context.mark(new ItemStack(Items.stick, 64)));
        }
        int entitiesBefore = context.world().loadedEntityList.size();
        service.onLogin(full);
        context.check(
            "full-inventory-grant-remains-pending",
            service.status(full, definition.id) == GiftService.Status.PENDING && packageCount(full, definition.id) == 0
                && context.world().loadedEntityList.size() == entitiesBefore,
            "No insertion space leaves durable grant pending and spawns no dropped entity");
        full.inventory.setInventorySlotContents(0, null);
        service.onLogin(full);
        ItemStack fullGift = findPackage(full, definition.id);
        if (fullGift != null) ownReceipt(
            context,
            GiftWirelessEU.receiptKey(
                fullGift.getTagCompound()
                    .getString(GiftService.TAG_TOKEN)));
        full.inventory.currentItem = 0;
        boolean fullOpened = service.open(full, fullGift);
        context.check(
            "full-inventory-open-atomic",
            fullGift != null && !fullOpened
                && full.inventory.getStackInSlot(0) == fullGift
                && context.world().loadedEntityList.size() == entitiesBefore
                && !GlobalVariableStorage.GlobalEnergy.containsKey(full.getUniqueID()),
            "65 rewards need two free slots; failed opening retains the package, creates no drop, and credits no EU");

        GiftDefinition questGift = saved.copy();
        questGift.shellKey = "hv";
        questGift.trigger = GiftDefinition.Trigger.BQ_COMPLETE;
        UUID quest = UUID.nameUUIDFromBytes((context.runId() + ":gift-quest").getBytes(StandardCharsets.UTF_8));
        questGift.questId = quest.toString();
        questGift.wirelessEU = "0";
        registry.save(questGift, saved.revision);
        service.onQuestCompleted(owner.getUniqueID(), Arrays.asList(owner.getUniqueID(), offline.getUniqueID()), quest);
        context.check(
            "offline-snapshot-authorization",
            service.status(offline, definition.id) == GiftService.Status.PENDING
                && packageCount(offline, definition.id) == 0,
            "Direct completion API receives an explicit offline member snapshot; no network login is simulated");
        service.onLogin(offline);
        service.onLogin(offline);
        context.check(
            "offline-login-api-delivers-once",
            service.status(offline, definition.id) == GiftService.Status.ISSUED
                && packageCount(offline, definition.id) == 1
                && "hv".equals(GiftShellCatalog.key(findPackage(offline, definition.id))),
            "Later onLogin API retries deliver the stored snapshot grant once even without live BQ completion state");
        if (Loader.isModLoaded("betterquesting")) {
            MinecraftForge.EVENT_BUS.post(new QuestEvent(QuestEvent.Type.COMPLETED, outsider.getUniqueID(), quest));
            context.check(
                "registered-bq-completion-handler",
                service.status(outsider, definition.id) == GiftService.Status.PENDING,
                "Synthetic QuestEvent traverses the real registered Forge handler for this disconnected UUID");
        } else context.skip("registered-bq-completion-handler", "BetterQuesting is absent");
        GiftDefinition unlock = questGift.copy();
        unlock.id = definition.id + "-unlock";
        unlock.trigger = GiftDefinition.Trigger.BQ_UNLOCK;
        unlock.rewards.clear();
        unlock.rewards.add(new ItemEntry("minecraft:paper", 1, 0));
        registry.save(unlock, 0);
        context.check(
            "bq-unlock-initially-locked",
            service.status(offline, unlock.id) == GiftService.Status.LOCKED,
            "Missing live quest completion and no authorization leave the custom unlock gift locked");
        service.onQuestCompleted(owner.getUniqueID(), Arrays.asList(offline.getUniqueID()), quest);
        context.check(
            "bq-unlock-awaits-explicit-claim",
            service.status(offline, unlock.id) == GiftService.Status.AVAILABLE && packageCount(offline, unlock.id) == 0,
            "Completion snapshot authorizes the unlock gift without auto-delivering a package");
        String claim = service.claim(offline, unlock.id);
        boolean repeatClaimRejected = false;
        try {
            service.claim(offline, unlock.id);
        } catch (IllegalStateException expected) {
            repeatClaimRejected = true;
        }
        context.check(
            "bq-unlock-claim-once",
            "issued".equals(claim) && repeatClaimRejected && packageCount(offline, unlock.id) == 1,
            "Explicit server claim issues once; duplicate request cannot issue another package");
        questGift.enabled = false;
        registry.save(questGift, saved.revision + 1);
        context.check(
            "definition-disabled-boundary",
            service.status(offline, definition.id) == GiftService.Status.DISABLED,
            "Disabling a definition hides an existing authorization without resetting its ledger");
        exerciseFirstLoginAndBatches(context, owner, definition);
        exerciseHistoricalGrant(context, definition);
        exerciseLegacy(context, definition);
        exerciseEraShellDelivery(context, definition);
        exerciseServerUiTree(context, owner);
        context.skip(
            "real-bq-party-membership-and-completion",
            "Offline member snapshot is supplied through the API; synthetic completion event does not prove quest gameplay or party membership changes");
    }

    private static void exerciseFirstLoginAndBatches(ProbeContext context, FakePlayer existing, GiftDefinition source)
        throws Exception {
        GiftRegistry registry = GiftRegistry.INSTANCE;
        GiftService service = GiftService.INSTANCE;
        GiftDefinition late = source.copy();
        late.id = source.id + "-late";
        late.wirelessEU = "2345678";
        late.rewards.clear();
        ItemStack expected = NativeFixtures.specialStack(context, 64);
        for (int i = 0; i < 40; i++) {
            NBTTagCompound tag = (NBTTagCompound) expected.getTagCompound()
                .copy();
            tag.setInteger("giftBatchIndex", i);
            late.rewards.add(new ItemEntry("minecraft:paper", 64, 0, tag));
        }
        registry.save(late, 0);
        service.onLogin(existing);
        context.check(
            "first-login-new-definition-not-retroactive",
            service.status(existing, late.id) == GiftService.Status.LOCKED && packageCount(existing, late.id) == 0,
            "Account already observed before this FIRST_LOGIN definition was created receives no later grant");
        FakePlayer fresh = context.player("gift-late-first-login");
        ownPlayerFiles(context, fresh);
        service.onLogin(fresh);
        ItemStack initialPackage = findPackage(fresh, late.id);
        context.check(
            "first-login-new-account-eligible",
            initialPackage != null && service.status(fresh, late.id) == GiftService.Status.ISSUED,
            "A new synthetic account receives the newly created FIRST_LOGIN definition");
        if (initialPackage == null) return;
        String token = initialPackage.getTagCompound()
            .getString(GiftService.TAG_TOKEN);
        UUID receipt = GiftWirelessEU.receiptKey(token);
        ownReceipt(context, receipt);
        ItemStack replay = initialPackage.copy();
        for (int i = 10; i < fresh.inventory.mainInventory.length; i++) {
            fresh.inventory.setInventorySlotContents(i, context.mark(new ItemStack(Items.stick, 64)));
        }
        fresh.inventory.currentItem = 0;
        BigInteger baseline = GlobalVariableStorage.GlobalEnergy.getOrDefault(fresh.getUniqueID(), BigInteger.ZERO);
        Path playerFile = fresh.mcServer.worldServerForDimension(0)
            .getSaveHandler()
            .getWorldDirectory()
            .toPath()
            .resolve("playerdata")
            .resolve(fresh.getUniqueID() + ".dat");
        int delivered = 0;
        int batches = 0;
        boolean ordered = true;
        boolean progressPersisted = true;
        boolean partialEnergyZero = true;
        while (delivered < 40 && batches < 10) {
            ItemStack currentPackage = findPackage(fresh, late.id);
            if (currentPackage == null || !service.open(fresh, currentPackage)) break;
            batches++;
            int previous = delivered;
            for (int slot = 0; slot < fresh.inventory.mainInventory.length; slot++) {
                ItemStack stack = fresh.inventory.getStackInSlot(slot);
                if (stack == null || stack.getItem() != Items.paper) continue;
                ItemStack nextExpected = expected.copy();
                nextExpected.getTagCompound()
                    .setInteger("giftBatchIndex", delivered);
                ordered &= NativeFixtures.sameStack(nextExpected, stack);
                delivered++;
            }
            NBTTagCompound saved;
            try (java.io.InputStream stream = Files.newInputStream(playerFile)) {
                saved = CompressedStreamTools.readCompressed(stream);
            }
            NBTTagCompound savedReceipts = saved.getCompoundTag("ForgeData")
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                .getCompoundTag("gtit_gift_receipts");
            progressPersisted &= savedReceipts.getInteger(token + ":delivered") == delivered
                && savedReceipts.getBoolean(token + ":opened") == (delivered == 40);
            NBTTagList savedInventory = saved.getTagList("Inventory", 10);
            int savedRewards = 0;
            for (int i = 0; i < savedInventory.tagCount(); i++) {
                ItemStack stack = ItemStack.loadItemStackFromNBT(savedInventory.getCompoundTagAt(i));
                if (stack == null || stack.getItem() != Items.paper) continue;
                ItemStack savedExpected = expected.copy();
                savedExpected.getTagCompound()
                    .setInteger("giftBatchIndex", previous + savedRewards++);
                progressPersisted &= NativeFixtures.sameStack(savedExpected, stack);
            }
            progressPersisted &= savedRewards == delivered - previous;
            if (delivered < 40) {
                partialEnergyZero &= GlobalVariableStorage.GlobalEnergy
                    .getOrDefault(fresh.getUniqueID(), BigInteger.ZERO)
                    .equals(baseline) && !GlobalVariableStorage.GlobalEnergy.containsKey(receipt)
                    && findPackage(fresh, late.id) != null;
            }
            // Receive the current batch, then free those exact reward slots before the next opening.
            for (int slot = 0; slot < fresh.inventory.mainInventory.length; slot++) {
                ItemStack stack = fresh.inventory.getStackInSlot(slot);
                if (stack != null && stack.getItem() == Items.paper)
                    fresh.inventory.setInventorySlotContents(slot, null);
            }
            if (delivered <= previous) break;
        }
        context.check(
            "large-gift-sequential-batches-nbt",
            batches > 1 && delivered == 40
                && ordered
                && packageCount(fresh, late.id) == 0
                && service.status(fresh, late.id) == GiftService.Status.OPENED,
            "40 distinct full stacks withdrawn in order with exact nested NBT; batches=" + batches
                + ", stacks="
                + delivered);
        context.check(
            "large-gift-cursor-playerdata",
            batches > 1 && progressPersisted,
            "Each compressed real player.dat contains the matching reward inventory, cursor and completion receipt");
        BigInteger expectedEnergy = baseline.add(new BigInteger(late.wirelessEU));
        context.check(
            "large-gift-energy-only-final-batch",
            batches > 1 && partialEnergyZero
                && expectedEnergy.equals(GlobalVariableStorage.GlobalEnergy.get(fresh.getUniqueID()))
                && GlobalVariableStorage.GlobalEnergy.containsKey(receipt),
            "Intermediate batches retain package and credit zero EU; final batch consumes it and credits exactly "
                + late.wirelessEU);
        service.onLogin(fresh);
        fresh.inventory.setInventorySlotContents(0, replay);
        fresh.inventory.currentItem = 0;
        context.check(
            "large-gift-final-replay-no-double-credit",
            !service.open(fresh, replay)
                && expectedEnergy.equals(GlobalVariableStorage.GlobalEnergy.get(fresh.getUniqueID())),
            "Final opened receipt blocks package replay and login energy replay remains idempotent");
        fresh.inventory.setInventorySlotContents(0, null);
    }

    private static void exerciseServerUiTree(ProbeContext context, FakePlayer player) throws Exception {
        try {
            GiftGuiFactory.open(player);
            if (player.openContainer instanceof ModularContainer) {
                context.check(
                    "gift-factory-server-open-container",
                    true,
                    "Production gift factory produced a real server ModularContainer; no physical client is present");
            } else {
                context.skip(
                    "gift-factory-server-open-container",
                    "MUI2 GuiManager.open explicitly rejects FakePlayer before building; no real network/screen acceptance is claimed");
            }
        } finally {
            player.closeScreen();
        }
        SimpleGuiFactory management = (SimpleGuiFactory) GuiManager.getFactory("gtit:gift_management");
        net.minecraft.inventory.Container before = player.openContainer;
        GiftGuiFactory.openManagement(player);
        context.check(
            "gift-management-rejects-fake-player",
            !GiftAccessHandler.authorized(player) && player.openContainer == before
                && !management.canInteractWith(player, new GuiData(player)),
            "Production O-key manager entry and live factory permission both reject synthetic players");
        io.netty.buffer.ByteBuf bytes = io.netty.buffer.Unpooled.buffer();
        boolean forgedRejected = false;
        try {
            management.readGuiData(player, new net.minecraft.network.PacketBuffer(bytes));
        } catch (IllegalArgumentException expected) {
            forgedRejected = true;
        } finally {
            bytes.release();
        }
        context.check(
            "gift-management-client-factory-request-rejected",
            forgedRejected,
            "Server-side factory deserialization cannot bypass the authenticated O-key request");
        exerciseServerUiTree(context, player, "gtit:gift_library", "gift-real-server-widget-tree");
        exerciseServerUiTree(context, player, "gtit:gift_management", "gift-management-server-widget-tree");
    }

    private static void exerciseServerUiTree(ProbeContext context, FakePlayer player, String factoryId, String check)
        throws Exception {
        SimpleGuiFactory factory = (SimpleGuiFactory) GuiManager.getFactory(factoryId);
        GuiData data = new GuiData(player);
        UISettings settings = new UISettings(RecipeViewerSettings.DUMMY);
        settings.defaultCanInteractWith(factory, data);
        ModularSyncManager sync = new ModularSyncManager(false);
        PanelSyncManager panelSync = new PanelSyncManager(sync, true);
        ModularPanel panel = factory.createPanel(data, panelSync, settings);
        WidgetTree.collectSyncValues(panelSync, panel);
        ModularContainer container = settings.hasCustomContainer() ? settings.createContainer()
            : factory.createContainer();
        try {
            container.construct(player, sync, settings, panel.getName(), data);
            sync.onOpen();
            com.cleanroommc.modularui.api.IPanelHandler delete = panelSync
                .findPanelHandlerNullable("gift_delete_confirmation");
            context.check(
                check,
                container.isInitialized() && container.inventorySlots.size() >= 36
                    && panelSync.findSyncHandlerNullable("gift_admin", 0) != null
                    && panelSync.findSyncHandlerNullable("gift_row_status_0", 0) != null
                    && delete instanceof PanelSyncHandler
                    && panelSync.getSyncHandlerFromMapKey("gift_delete_confirmation") == delete,
                "Registered production factory creates and collects its actual server widget/sync tree; inventory slots="
                    + container.inventorySlots.size()
                    + "; detached server container only");
            if (delete instanceof PanelSyncHandler deleteSync) {
                // Exercise the actual registered builder and open lifecycle without fabricating a physical client.
                deleteSync.openPanel();
                try {
                    PanelSyncManager child = (PanelSyncManager) GiftScope.field(PanelSyncHandler.class, "syncManager")
                        .get(deleteSync);
                    context.check(
                        check + "-delete-confirmation-lifecycle",
                        deleteSync.isValid() && "gift_delete_confirmation".equals(deleteSync.getKey())
                            && deleteSync.isPanelOpen()
                            && sync.isOpen("gift_delete_confirmation")
                            && child != null
                            && child.isInitialised()
                            && child.findSyncHandlerNullable("gift_delete", 0) != null
                            && child.findSyncHandlerNullable("gift_delete_target", 0) != null,
                        "Real registered PanelSyncHandler opens its synchronized child manager with target and delete handlers; server-only lifecycle, no client rendering claim");
                } finally {
                    deleteSync.closePanelInternal();
                }
                context.check(
                    check + "-delete-confirmation-closed",
                    !deleteSync.isPanelOpen() && !sync.isOpen("gift_delete_confirmation"),
                    "Native child manager closes and unregisters without leaving a detached panel open");
            }
        } finally {
            if (sync.isOpen()) sync.onClose();
            if (sync.isClosed()) sync.dispose();
        }
    }

    private static void exerciseLegacy(ProbeContext context, GiftDefinition source) throws Exception {
        if (!DefaultContentConfig.loadDefaultGifts()) {
            context.skip(
                "legacy-package-migration",
                "Global default gift switch is disabled; no global configuration is changed");
            return;
        }
        FakePlayer legacy = context.player("gift-legacy");
        ownPlayerFiles(context, legacy);
        GiftRegistry.Snapshot snapshot = GiftRegistry.INSTANCE.snapshot();
        snapshot.defaultsEnabled = true;
        GiftDefinition starter = source.copy();
        starter.id = "starter";
        starter.builtIn = true;
        starter.wirelessEU = "0";
        // This fixture enters through a snapshot rather than save(), so assign the same monotonic
        // identity as production before subsequent native saves validate the whole registry.
        starter.revision = ++snapshot.revision;
        snapshot.definitions.add(starter);
        GiftScope.field(GiftRegistry.class, "state")
            .set(GiftRegistry.INSTANCE, snapshot);
        NBTTagCompound persisted = new NBTTagCompound();
        persisted.setBoolean(GiftService.LEGACY_FLAG, true);
        legacy.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        ItemStack oldPackage = GTITItemList.StarterGift.get(1);
        legacy.inventory.setInventorySlotContents(0, oldPackage);
        legacy.inventory.currentItem = 0;
        boolean migrated = GiftService.INSTANCE.open(legacy, oldPackage);
        GiftService.INSTANCE.onLogin(legacy);
        context.check(
            "legacy-package-migration",
            migrated && packageCount(legacy, "starter") == 0,
            "Real untagged historical package migrates for the UUID with the old received flag; login does not reissue starter");
        if (oldPackage.hasTagCompound()) {
            ItemStack replay = oldPackage.copy();
            legacy.inventory.setInventorySlotContents(8, replay);
            legacy.inventory.currentItem = 8;
            context.check(
                "legacy-migration-replay-refused",
                !GiftService.INSTANCE.open(legacy, replay),
                "Migrated legacy package is owner/token bound and its opened receipt blocks replay");
        }
        ItemStack replacement = GiftShellCatalog.stack("starter");
        legacy.inventory.setInventorySlotContents(8, replacement);
        legacy.inventory.currentItem = 8;
        context.check(
            "legacy-untagged-replacement-remains-repeatable",
            GiftService.INSTANCE.open(legacy, replacement),
            "A second untagged historical starter remains a supported manual replacement; existing token replay stays blocked");
        ItemStack partial = GiftShellCatalog.stack("starter");
        NBTTagCompound forgedLocator = new NBTTagCompound();
        forgedLocator.setString(GiftService.TAG_ID, "starter");
        partial.setTagCompound(forgedLocator);
        legacy.inventory.setInventorySlotContents(8, partial);
        context.check(
            "partial-starter-locator-not-legacy",
            !GiftService.INSTANCE.open(legacy, partial),
            "A partial grant locator cannot fall back to repeatable untagged starter migration");
    }

    private static void exerciseHistoricalGrant(ProbeContext context, GiftDefinition source) throws Exception {
        FakePlayer player = context.player("gift-historical-token");
        ownPlayerFiles(context, player);
        GiftDefinition definition = source.copy();
        definition.id = source.id + "-historical";
        definition.shellKey = "ev";
        definition.wirelessEU = "0";
        definition.rewards.clear();
        definition.rewards.add(new ItemEntry("minecraft:paper", 1, 0));
        GiftRegistry.INSTANCE.save(definition, 0);
        GiftService.INSTANCE.onLogin(player);
        ItemStack issued = findPackage(player, definition.id);
        if (issued == null) {
            context.check(
                "historical-token-starter-wrapper-compatible",
                false,
                "Expected synthetic authorization was not delivered");
            return;
        }
        GiftDefinition edited = GiftRegistry.INSTANCE.get(definition.id);
        edited.rewards = authoredDefaults().get(1).rewards;
        edited.shellKey = "luv";
        GiftRegistry.INSTANCE.save(edited, edited.revision);
        Path directory = (Path) GiftScope.field(GiftService.class, "directory")
            .get(GiftService.INSTANCE);
        Path ledger = directory.resolve(player.getUniqueID() + ".json");
        Gson gson = new Gson();
        com.google.gson.JsonObject json = gson
            .fromJson(new String(Files.readAllBytes(ledger), StandardCharsets.UTF_8), com.google.gson.JsonObject.class);
        json.getAsJsonObject("grants")
            .getAsJsonObject(definition.id)
            .getAsJsonObject("definition")
            .remove("shellKey");
        Files.write(
            ledger,
            gson.toJson(json)
                .getBytes(StandardCharsets.UTF_8));
        ((Map<?, ?>) GiftScope.field(GiftService.class, "ledgers")
            .get(GiftService.INSTANCE)).remove(player.getUniqueID());
        ItemStack historical = GiftShellCatalog.stack("starter");
        historical.setTagCompound(
            (NBTTagCompound) issued.getTagCompound()
                .copy());
        int slot = slotOf(player, issued);
        player.inventory.setInventorySlotContents(slot, historical);
        player.inventory.currentItem = slot;
        int paperBefore = countItem(player, new ItemStack(Items.paper));
        context.check(
            "historical-token-starter-wrapper-compatible",
            GiftService.INSTANCE.open(player, historical)
                && GiftService.INSTANCE.status(player, definition.id) == GiftService.Status.OPENED
                && countItem(player, new ItemStack(Items.paper)) == paperBefore + 1,
            "Reloaded pre-shellKey grant retains its paper reward and old carrier after the live definition changes to era materials and LuV shell");
    }

    private static void exerciseEraShellDelivery(ProbeContext context, GiftDefinition source) throws Exception {
        FakePlayer player = context.player("gift-era-shell-delivery");
        ownPlayerFiles(context, player);
        List<GiftDefinition> defaults = authoredDefaults();
        verifyEraRewardItems(context, defaults);
        for (String key : GiftIconCatalog.DEFAULT_KEYS) {
            GiftDefinition definition = source.copy();
            definition.id = source.id + "-shell-" + key;
            definition.shellKey = key;
            definition.name = "Era shell " + key;
            definition.wirelessEU = "0";
            definition.rewards.clear();
            definition.randomRewards.clear();
            definition.randomCount = 0;
            for (GiftDefinition authored : defaults) {
                if (key.equals(authored.id) && !"starter".equals(key)) definition.rewards = authored.rewards;
            }
            GiftRegistry.INSTANCE.save(definition, 0);
        }
        GiftService.INSTANCE.onLogin(player);
        int shellIndex = 0;
        for (String key : GiftIconCatalog.DEFAULT_KEYS) {
            final boolean creative = shellIndex++ % 2 == 1;
            String id = source.id + "-shell-" + key;
            ItemStack issued = findPackage(player, id);
            String actualRegistry = issued == null ? "<missing>"
                : String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(issued.getItem()));
            int sourceSlot = issued == null ? -1 : slotOf(player, issued);
            int handSlot = 0;
            GiftService.Status before = GiftService.INSTANCE.status(player, id);
            boolean held = false;
            boolean returnedMatches = false;
            GiftDefinition definition = GiftRegistry.INSTANCE.get(id);
            List<Integer> beforeCounts = new ArrayList<>();
            for (ItemEntry entry : definition.rewards) beforeCounts.add(countItem(player, entry.toItemStack()));
            boolean correct = issued != null && key.equals(GiftShellCatalog.key(issued))
                && GiftShellCatalog.registryName(key)
                    .equals(actualRegistry)
                && player.getUniqueID()
                    .toString()
                    .equals(
                        issued.getTagCompound()
                            .getString(GiftService.TAG_OWNER));
            if (correct) {
                // A selected item must be in the real hotbar (0..8), not merely somewhere in mainInventory.
                // Preserve the displaced item, including other packages awaiting their own checks.
                if (sourceSlot != handSlot) {
                    ItemStack displaced = player.inventory.getStackInSlot(handSlot);
                    player.inventory.setInventorySlotContents(handSlot, issued);
                    player.inventory.setInventorySlotContents(sourceSlot, displaced);
                }
                player.inventory.currentItem = handSlot;
                held = player.inventory.getCurrentItem() == issued;
                net.minecraft.server.management.ItemInWorldManager use = new net.minecraft.server.management.ItemInWorldManager(
                    player.worldObj) {

                    @Override
                    public boolean isCreative() {
                        return creative;
                    }
                };
                use.thisPlayerMP = player;
                // Invoke vanilla's caller too: creative use restores the old count onto a changed return stack.
                // GiftService owns the inventory transaction, so the original callback object stays unchanged.
                returnedMatches = !use.tryUseItem(player, player.worldObj, issued) && issued.stackSize == 1;
            }
            GiftService.Status after = GiftService.INSTANCE.status(player, id);
            int remaining = packageCount(player, id);
            correct &= held && returnedMatches
                && before == GiftService.Status.ISSUED
                && after == GiftService.Status.OPENED
                && remaining == 0;
            List<String> rewardCounts = new ArrayList<>();
            for (int i = 0; i < definition.rewards.size(); i++) {
                ItemEntry entry = definition.rewards.get(i);
                int added = countItem(player, entry.toItemStack()) - beforeCounts.get(i);
                correct &= added == entry.amount;
                rewardCounts.add(entry.itemId + ":" + entry.meta + "=" + added + "/" + entry.amount);
            }
            context.check(
                "era-shell-issued-and-right-click-" + key,
                correct,
                "Production delivery and real item right-click: registry=" + actualRegistry
                    + ", sourceSlot="
                    + sourceSlot
                    + ", handSlot="
                    + handSlot
                    + ", held="
                    + held
                    + ", returnedMatches="
                    + returnedMatches
                    + ", creative="
                    + creative
                    + ", before="
                    + before
                    + ", after="
                    + after
                    + ", remaining="
                    + remaining
                    + ", reward counts="
                    + rewardCounts);
            // Receive verified era materials before the next package; preserve every pending package.
            for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                for (ItemEntry entry : definition.rewards) {
                    if (stack != null && stack.isItemEqual(entry.toItemStack())) {
                        player.inventory.setInventorySlotContents(i, null);
                        break;
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<GiftDefinition> authoredDefaults() throws Exception {
        java.lang.reflect.Method method = Class.forName("com.miaokatze.gtit.gift.GiftDefaults")
            .getDeclaredMethod("create");
        method.setAccessible(true);
        return (List<GiftDefinition>) method.invoke(null);
    }

    private static void verifyEraRewardItems(ProbeContext context, List<GiftDefinition> defaults) {
        ItemStack[][] expected = {
            { ItemList.Casing_BronzePlatedBricks.get(16L),
                GTOreDictUnificator.get(OrePrefixes.pipeHuge, PipeStats.Potin, 32L),
                // OreDict get() uses copyAmount(), which caps one lookup stack at 64; total gift quantity is 256.
                GTUtility.copyAmountUnsafe(256, GTOreDictUnificator.get(OrePrefixes.pipeMedium, PipeStats.Potin, 1L)),
                ItemList.Machine_HP_Solar.get(2L) },
            { ItemList.Hull_LV.get(4L), ItemList.Electric_Pump_LV.get(4L), ItemList.Conveyor_Module_LV.get(4L),
                ItemList.Electric_Motor_LV.get(8L) },
            { ItemList.Hull_MV.get(4L), ItemList.Electric_Pump_MV.get(4L), ItemList.Conveyor_Module_MV.get(4L),
                ItemList.Electric_Motor_MV.get(8L) },
            { ItemList.Hull_HV.get(4L), ItemList.Electric_Pump_HV.get(4L), ItemList.Conveyor_Module_HV.get(4L),
                ItemList.Electric_Motor_HV.get(8L) },
            { ItemList.Hull_EV.get(4L), ItemList.Electric_Pump_EV.get(4L), ItemList.Conveyor_Module_EV.get(4L),
                ItemList.Electric_Motor_EV.get(8L) },
            { ItemList.Hull_IV.get(4L), ItemList.Electric_Pump_IV.get(4L), ItemList.Conveyor_Module_IV.get(4L),
                ItemList.Electric_Motor_IV.get(8L) },
            { ItemList.Hull_LuV.get(4L), ItemList.Electric_Pump_LuV.get(4L), ItemList.Conveyor_Module_LuV.get(4L),
                ItemList.Electric_Motor_LuV.get(8L) } };
        for (int i = 0; i < expected.length; i++) {
            GiftDefinition definition = defaults.get(i + 1);
            for (int j = 0; j < expected[i].length; j++) {
                ItemStack actual = definition.rewards.get(j)
                    .toItemStack();
                ItemStack reference = expected[i][j];
                context.check(
                    "era-material-registry-" + definition.id + "-" + j,
                    actual != null && reference != null
                        && actual.isItemEqual(reference)
                        && ItemStack.areItemStackTagsEqual(actual, reference)
                        && actual.stackSize == reference.stackSize,
                    "Authored descriptor resolves to the registered GT ItemList/OreDict material and exact quantity; actual="
                        + (actual == null ? "<missing>" : actual.stackSize)
                        + ", expected="
                        + (reference == null ? "<missing>" : reference.stackSize));
            }
        }
    }

    private static int countItem(FakePlayer player, ItemStack expected) {
        int count = 0;
        if (expected == null) return count;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.isItemEqual(expected) && ItemStack.areItemStackTagsEqual(stack, expected)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private static ItemStack findPackage(FakePlayer player, String id) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.hasTagCompound()
                && id.equals(
                    stack.getTagCompound()
                        .getString(GiftService.TAG_ID)))
                return stack;
        }
        return null;
    }

    private static int packageCount(FakePlayer player, String id) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.hasTagCompound()
                && id.equals(
                    stack.getTagCompound()
                        .getString(GiftService.TAG_ID)))
                count += stack.stackSize;
        }
        return count;
    }

    private static int slotOf(FakePlayer player, ItemStack stack) {
        for (int i = 0; i < player.inventory.mainInventory.length; i++)
            if (player.inventory.mainInventory[i] == stack) return i;
        throw new IllegalStateException("Package absent from synthetic inventory");
    }

    private static void ownReceipt(ProbeContext context, UUID receipt) {
        if (GlobalVariableStorage.GlobalEnergy.containsKey(receipt))
            throw new IllegalStateException("Receipt already exists");
        context.onCleanup(() -> {
            GlobalVariableStorage.GlobalEnergy.remove(receipt);
            if (GlobalEnergyWorldSavedData.INSTANCE != null) GlobalEnergyWorldSavedData.INSTANCE.markDirty();
        });
    }

    private static void ownPlayerFiles(ProbeContext context, FakePlayer player) throws Exception {
        UUID id = player.getUniqueID();
        Map<?, ?> bqCache = (Map<?, ?>) GiftScope
            .field(com.miaokatze.gtit.trade.v2.NekoBqBridge.class, "completedQuestsCache")
            .get(null);
        Path folder = player.mcServer.worldServerForDimension(0)
            .getSaveHandler()
            .getWorldDirectory()
            .toPath()
            .resolve("playerdata");
        Path data = folder.resolve(id + ".dat");
        Path temporary = folder.resolve(id + ".gtit-gift.tmp");
        if (Files.exists(data) || Files.exists(temporary)
            || GlobalVariableStorage.GlobalEnergy.containsKey(id)
            || SpaceProjectManager.spaceTeams.containsKey(id)
            || bqCache.containsKey(id)) throw new IllegalStateException("Synthetic identity already has native state");
        context.onCleanup(() -> {
            try {
                Files.deleteIfExists(data);
                Files.deleteIfExists(temporary);
                GlobalVariableStorage.GlobalEnergy.remove(id);
                SpaceProjectManager.spaceTeams.remove(id);
                bqCache.remove(id);
                if (GlobalEnergyWorldSavedData.INSTANCE != null) GlobalEnergyWorldSavedData.INSTANCE.markDirty();
                if (SpaceProjectWorldSavedData.INSTANCE != null) SpaceProjectWorldSavedData.INSTANCE.markDirty();
                context.check(
                    "cleanup-gift-" + id.toString()
                        .substring(0, 8),
                    !Files.exists(data) && !Files.exists(temporary)
                        && !GlobalVariableStorage.GlobalEnergy.containsKey(id)
                        && !SpaceProjectManager.spaceTeams.containsKey(id)
                        && !bqCache.containsKey(id),
                    "Exact synthetic UUID playerdata, temp file, GT energy, leader mapping and BQ completion cache removed");
            } catch (java.io.IOException error) {
                throw new IllegalStateException(error);
            }
        });
    }

    /** Scope never spans a tick; no live registry/ledger path is written and every reference is restored in finally. */
    private static final class GiftScope implements AutoCloseable {

        private final Field file = field(GiftRegistry.class, "file");
        private final Field state = field(GiftRegistry.class, "state");
        private final Field directory = field(GiftService.class, "directory");
        private final Field ticks = field(GiftService.class, "ticks");
        private final Object oldFile;
        private final Object oldState;
        private final Object oldDirectory;
        private final Object oldTicks;
        private final Map<Object, Object> ledgers;
        private final Map<Object, Object> oldLedgers;
        private final List<ItemEntry> oldGuaranteed = new ArrayList<>(GiftConfig.getGuaranteedItems());
        private final List<ItemEntry> oldRandom = new ArrayList<>(GiftConfig.getRandomItems());
        private final int oldRandomCount = GiftConfig.getRandomCount();

        @SuppressWarnings("unchecked")
        GiftScope(Path root) throws Exception {
            oldFile = file.get(GiftRegistry.INSTANCE);
            oldState = state.get(GiftRegistry.INSTANCE);
            oldDirectory = directory.get(GiftService.INSTANCE);
            oldTicks = ticks.get(GiftService.INSTANCE);
            ledgers = (Map<Object, Object>) field(GiftService.class, "ledgers").get(GiftService.INSTANCE);
            oldLedgers = new HashMap<>(ledgers);
            Path grants = root.resolve("grants");
            Files.createDirectories(grants);
            GiftRegistry.Snapshot snapshot = new GiftRegistry.Snapshot();
            snapshot.defaultsEnabled = false;
            Path config = root.resolve("gifts.json");
            Files.write(
                config,
                new Gson().toJson(snapshot)
                    .getBytes(StandardCharsets.UTF_8));
            file.set(GiftRegistry.INSTANCE, config);
            state.set(GiftRegistry.INSTANCE, snapshot);
            directory.set(GiftService.INSTANCE, grants);
            ledgers.clear();
            ticks.set(GiftService.INSTANCE, 0);
        }

        @Override
        public void close() throws Exception {
            file.set(GiftRegistry.INSTANCE, oldFile);
            state.set(GiftRegistry.INSTANCE, oldState);
            directory.set(GiftService.INSTANCE, oldDirectory);
            ledgers.clear();
            ledgers.putAll(oldLedgers);
            ticks.set(GiftService.INSTANCE, oldTicks);
            GiftConfig.acceptStarterDefinition(oldGuaranteed, oldRandom, oldRandomCount);
        }

        private static Field field(Class<?> owner, String name) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }
    }
}
