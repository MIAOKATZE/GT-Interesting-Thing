package com.miaokatze.gtit.gift;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.config.GiftConfig.ItemEntry;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/** Server-thread gift delivery, durable authorizations and owner-bound gift packages. */
public final class GiftService {

    public static final GiftService INSTANCE = new GiftService();
    public static final String LEGACY_FLAG = "gtit_received_starter_gift";
    public static final String TAG_TOKEN = "gtitGiftToken";
    public static final String TAG_ID = "gtitGiftId";
    public static final String TAG_OWNER = "gtitGiftOwner";
    public static final String TAG_ICON = "gtitGiftIcon";
    public static final String TAG_ITEM_ICON = "gtitGiftItemIcon";
    private static final String RECEIPTS = "gtit_gift_receipts";
    private static final Logger LOG = LogManager.getLogger("gtit");

    public enum Status {
        LOCKED,
        AVAILABLE,
        PENDING,
        ISSUED,
        OPENED,
        DISABLED
    }

    static final class Ledger {

        public Map<String, Grant> grants = new LinkedHashMap<>();
        public boolean loginObserved;
    }

    static final class Grant {

        public String token;
        public String owner;
        public String target;
        public GiftDefinition definition;
        public List<ItemEntry> items = new ArrayList<>();
        public boolean requested;
        public boolean legacy;
    }

    private Path directory;
    private final Map<UUID, Ledger> ledgers = new LinkedHashMap<>();
    private int ticks;

    private GiftService() {}

    public void init(World world) throws IOException {
        unload();
        directory = world.getSaveHandler()
            .getWorldDirectory()
            .toPath()
            .resolve("gtit_gifts");
        Files.createDirectories(directory);
    }

    public void unload() {
        directory = null;
        ledgers.clear();
        ticks = 0;
    }

    public boolean isReady() {
        return directory != null && GiftRegistry.INSTANCE.isReady();
    }

    public void onLogin(EntityPlayerMP player) {
        if (!isReady() || player.worldObj.isRemote) return;
        try {
            Ledger current = ledger(player.getUniqueID());
            boolean legacyReceived = persisted(player).getBoolean(LEGACY_FLAG);
            boolean firstLogin = !current.loginObserved && !legacyReceived;
            for (GiftDefinition gift : GiftRegistry.INSTANCE.snapshot().definitions) {
                if (!GiftRegistry.INSTANCE.isEnabled(gift)) continue;
                if (gift.trigger == GiftDefinition.Trigger.FIRST_LOGIN) {
                    if ("starter".equals(gift.id) ? legacyReceived : !firstLogin) continue;
                    authorize(player.getUniqueID(), gift, true);
                } else if (GiftBqBridge.isComplete(player.getUniqueID(), gift.questId)) {
                    authorize(player.getUniqueID(), gift, gift.trigger == GiftDefinition.Trigger.BQ_COMPLETE);
                }
            }
            current = ledger(player.getUniqueID());
            if (!current.loginObserved) {
                Ledger observed = copy(current);
                observed.loginObserved = true;
                persist(player.getUniqueID(), observed);
            }
            deliver(player);
        } catch (RuntimeException e) {
            LOG.error("Gift login processing failed for {}", player.getUniqueID(), e);
        }
    }

    /** Called with the member snapshot taken when BQ posts COMPLETED, including offline members. */
    public void onQuestCompleted(UUID playerId, Iterable<UUID> members, UUID quest) {
        if (!isReady()) return;
        for (GiftDefinition gift : GiftRegistry.INSTANCE.snapshot().definitions) {
            if (!GiftRegistry.INSTANCE.isEnabled(gift) || gift.trigger == GiftDefinition.Trigger.FIRST_LOGIN
                || !quest.equals(com.miaokatze.gtit.trade.v2.NekoBqQuestIdParser.parse(gift.questId))) continue;
            for (UUID member : members) {
                try {
                    authorize(member, gift, gift.trigger == GiftDefinition.Trigger.BQ_COMPLETE);
                } catch (RuntimeException e) {
                    LOG.error("Gift authorization failed for {} / {}", member, gift.id, e);
                } finally {
                    if (com.miaokatze.gtit.util.PlayerLookup.getOnlinePlayerByUuid(member) == null)
                        ledgers.remove(member);
                }
            }
        }
        com.miaokatze.gtit.util.PlayerLookup.forEachOnlinePlayer(this::deliverSafely);
    }

    public Status status(EntityPlayerMP player, String id) {
        requireReady();
        GiftDefinition definition = GiftRegistry.INSTANCE.get(id);
        if (!GiftRegistry.INSTANCE.isEnabled(definition)) return Status.DISABLED;
        Grant grant = ledger(player.getUniqueID()).grants.get(id);
        if (grant != null) {
            NBTTagCompound receipt = receipts(player);
            if (receipt.getBoolean(grant.token + ":opened")) return Status.OPENED;
            if (receipt.getBoolean(grant.token + ":issued")) return Status.ISSUED;
            return grant.requested ? Status.PENDING : Status.AVAILABLE;
        }
        if ("starter".equals(id) && persisted(player).getBoolean(LEGACY_FLAG)) return Status.ISSUED;
        if (definition.trigger == GiftDefinition.Trigger.BQ_UNLOCK
            && GiftBqBridge.isComplete(player.getUniqueID(), definition.questId)) return Status.AVAILABLE;
        return Status.LOCKED;
    }

    public String claim(EntityPlayerMP player, String id) {
        requireReady();
        GiftDefinition definition = GiftRegistry.INSTANCE.get(id);
        if (!GiftRegistry.INSTANCE.isEnabled(definition) || definition.trigger != GiftDefinition.Trigger.BQ_UNLOCK) {
            throw new IllegalStateException("Gift is unavailable");
        }
        Status status = status(player, id);
        if (status != Status.AVAILABLE && status != Status.PENDING)
            throw new IllegalStateException("Gift cannot be claimed");
        authorize(player.getUniqueID(), definition, true);
        deliver(player);
        return status(player, id) == Status.ISSUED ? "issued" : "pending";
    }

    /** Package NBT is a locator, never an authoritative reward or energy payload. */
    public boolean open(EntityPlayerMP player, ItemStack stack) {
        if (!isReady() || stack == null
            || stack.stackSize != 1
            || player.worldObj.isRemote
            || player.inventory.getCurrentItem() != stack
            || !GTITItemList.StarterGift.isStackEqual(stack, true, true)) return false;
        try {
            NBTTagCompound tag = stack.getTagCompound();
            Grant grant;
            if (tag == null || !tag.hasKey(TAG_TOKEN)) {
                GiftDefinition starter = GiftRegistry.INSTANCE.get("starter");
                // Disabling new built-in gifts must not invalidate already-owned legacy packages.
                if (starter == null) starter = GiftDefaults.createMigratingLegacy()
                    .get(0);
                grant = newGrant(player.getUniqueID(), starter, true);
                grant.legacy = true;
                Ledger next = copy(ledger(player.getUniqueID()));
                next.grants.put("legacy:" + grant.token, grant);
                persist(player.getUniqueID(), next);
                tag = new NBTTagCompound();
                writePackage(tag, grant);
                stack.setTagCompound(tag);
                GiftPlayerFiles.save(player);
            } else {
                if (!player.getUniqueID()
                    .toString()
                    .equals(tag.getString(TAG_OWNER))) return false;
                UUID.fromString(tag.getString(TAG_TOKEN));
                grant = findToken(ledger(player.getUniqueID()), tag.getString(TAG_TOKEN));
                if (grant == null || !grant.definition.id.equals(tag.getString(TAG_ID))) return false;
            }
            NBTTagCompound receipt = receipts(player);
            if (receipt.getBoolean(grant.token + ":opened")) return false;
            List<ItemStack> rewards = materialize(grant.items);
            ItemStack[] inventory = copyInventory(player);
            NBTTagCompound before = (NBTTagCompound) player.getEntityData()
                .copy();
            int selected = player.inventory.currentItem;
            int previousCursor = receipt.getInteger(grant.token + ":delivered");
            Withdrawal withdrawal = planWithdrawal(inventory, selected, rewards, previousCursor);
            if (withdrawal == null) {
                player.addChatMessage(new ChatComponentTranslation("gtit.gift.inventory_full"));
                return false;
            }
            // If player.dat was saved before GT's world data, replay this same transaction at login.
            if (withdrawal.complete) {
                GiftWirelessEU
                    .credit(grant.token, UUID.fromString(grant.target), new BigInteger(grant.definition.wirelessEU));
            }
            try {
                System
                    .arraycopy(withdrawal.inventory, 0, player.inventory.mainInventory, 0, withdrawal.inventory.length);
                receipt.setInteger(grant.token + ":delivered", withdrawal.cursor);
                receipt.setBoolean(grant.token + ":opened", withdrawal.complete);
                attachReceipts(player, receipt);
                GiftPlayerFiles.save(player);
            } catch (IOException | RuntimeException e) {
                System.arraycopy(inventory, 0, player.inventory.mainInventory, 0, inventory.length);
                restoreEntityData(player, before);
                throw new IllegalStateException("Unable to save gift opening", e);
            }
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            if (!withdrawal.complete) player
                .addChatMessage(new ChatComponentTranslation("gtit.gift.partial", rewards.size() - withdrawal.cursor));
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.error("Gift opening failed for {}", player.getUniqueID(), e);
            player.addChatMessage(new ChatComponentTranslation("gtit.gift.failed"));
            return false;
        }
    }

    /** Explicit administrator reset; normal edits and team changes never clear claims. */
    public boolean resetStarter(UUID playerId) {
        if (!isReady()) return false;
        Ledger next = copy(ledger(playerId));
        if (next.grants.remove("starter") == null) return false;
        persist(playerId, next);
        return true;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ledgers.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isReady() || ++ticks % 100 != 0) return;
        com.miaokatze.gtit.util.PlayerLookup.forEachOnlinePlayer(this::deliverSafely);
    }

    private void deliverSafely(EntityPlayerMP player) {
        try {
            deliver(player);
        } catch (RuntimeException e) {
            LOG.error("Gift delivery failed for {}", player.getUniqueID(), e);
        }
    }

    private void deliver(EntityPlayerMP player) {
        Ledger ledger = ledger(player.getUniqueID());
        for (Grant grant : ledger.grants.values()) {
            NBTTagCompound receipt = receipts(player);
            if (receipt.getBoolean(grant.token + ":opened")) {
                GiftWirelessEU
                    .credit(grant.token, UUID.fromString(grant.target), new BigInteger(grant.definition.wirelessEU));
                continue;
            }
            if (!grant.requested || grant.legacy || receipt.getBoolean(grant.token + ":issued")) continue;
            ItemStack gift = GTITItemList.StarterGift.get(1);
            if (gift == null) throw new IllegalStateException("Gift package item is unavailable");
            NBTTagCompound tag = new NBTTagCompound();
            writePackage(tag, grant);
            gift.setTagCompound(tag);
            ItemStack[] original = copyInventory(player);
            ItemStack[] planned = copyInventory(player);
            if (!insert(planned, Collections.singletonList(gift))) continue;
            NBTTagCompound before = (NBTTagCompound) player.getEntityData()
                .copy();
            try {
                System.arraycopy(planned, 0, player.inventory.mainInventory, 0, planned.length);
                receipt.setBoolean(grant.token + ":issued", true);
                attachReceipts(player, receipt);
                if ("starter".equals(grant.definition.id)) {
                    NBTTagCompound persisted = persisted(player);
                    persisted.setBoolean(LEGACY_FLAG, true);
                    player.getEntityData()
                        .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
                }
                GiftPlayerFiles.save(player);
            } catch (IOException | RuntimeException e) {
                System.arraycopy(original, 0, player.inventory.mainInventory, 0, original.length);
                restoreEntityData(player, before);
                throw new IllegalStateException("Unable to save gift delivery", e);
            }
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            player.addChatMessage(new ChatComponentTranslation("gtit.gift.received", grant.definition.name));
        }
    }

    private void authorize(UUID player, GiftDefinition gift, boolean requested) {
        Ledger current = ledger(player);
        Grant previous = current.grants.get(gift.id);
        if (previous != null && (!requested || previous.requested)) return;
        Ledger next = copy(current);
        if (previous == null) next.grants.put(gift.id, newGrant(player, gift, requested));
        else next.grants.get(gift.id).requested = true;
        persist(player, next);
    }

    private Grant newGrant(UUID player, GiftDefinition gift, boolean requested) {
        Grant grant = new Grant();
        grant.token = UUID.randomUUID()
            .toString();
        grant.owner = player.toString();
        grant.target = SpaceProjectManager.getLeader(player)
            .toString();
        grant.definition = gift.copy();
        grant.requested = requested;
        grant.items.addAll(gift.rewards);
        List<ItemEntry> random = new ArrayList<>(gift.randomRewards);
        Collections.shuffle(random);
        grant.items.addAll(random.subList(0, Math.min(gift.randomCount, random.size())));
        materialize(grant.items); // Missing items prevent authorization instead of deleting rewards.
        return grant;
    }

    private Ledger ledger(UUID player) {
        requireReady();
        Ledger loaded = ledgers.get(player);
        if (loaded != null) return loaded;
        Path file = directory.resolve(player + ".json");
        try {
            loaded = Files.exists(file) ? GiftFiles.read(file, Ledger.class) : new Ledger();
            if (loaded.grants == null) throw new IOException("Invalid gift ledger");
            for (Grant grant : loaded.grants.values()) {
                UUID.fromString(grant.token);
                UUID.fromString(grant.target);
                if (!player.toString()
                    .equals(grant.owner) || grant.definition == null || grant.items == null) {
                    throw new IOException("Invalid gift grant");
                }
                grant.definition.validate();
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Unable to load gift ledger", e);
        }
        ledgers.put(player, loaded);
        return loaded;
    }

    private void persist(UUID player, Ledger ledger) {
        try {
            GiftFiles.write(directory.resolve(player + ".json"), ledger);
            ledgers.put(player, ledger);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save gift ledger", e);
        }
    }

    private static Ledger copy(Ledger value) {
        return GiftFiles.JSON.fromJson(GiftFiles.JSON.toJson(value), Ledger.class);
    }

    private static Grant findToken(Ledger value, String token) {
        for (Grant grant : value.grants.values()) if (grant.token.equals(token)) return grant;
        return null;
    }

    private static void writePackage(NBTTagCompound tag, Grant grant) {
        tag.setString(TAG_TOKEN, grant.token);
        tag.setString(TAG_ID, grant.definition.id);
        tag.setString(TAG_OWNER, grant.owner);
        tag.setString(TAG_ICON, grant.definition.iconKey);
        if (grant.definition.icon != null) {
            ItemStack icon = grant.definition.icon.toItemStack();
            if (icon != null) tag.setTag(TAG_ITEM_ICON, icon.writeToNBT(new NBTTagCompound()));
        }
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", grant.definition.name);
        tag.setTag("display", display);
    }

    private static NBTTagCompound persisted(EntityPlayerMP player) {
        return player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static NBTTagCompound receipts(EntityPlayerMP player) {
        return persisted(player).getCompoundTag(RECEIPTS);
    }

    private static void attachReceipts(EntityPlayerMP player, NBTTagCompound receipt) {
        NBTTagCompound persisted = persisted(player);
        persisted.setTag(RECEIPTS, receipt);
        player.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    private static void restoreEntityData(EntityPlayerMP player, NBTTagCompound previous) {
        player.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, previous.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG));
    }

    private static List<ItemStack> materialize(List<ItemEntry> entries) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemEntry entry : entries) {
            ItemStack stack = entry.toItemStack();
            if (stack == null) throw new IllegalStateException("Gift item is unavailable: " + entry.itemId);
            int remaining = stack.stackSize;
            while (remaining > 0) {
                if (result.size() >= 4096) throw new IllegalStateException("Gift exceeds 4096 reward stacks");
                ItemStack part = stack.copy();
                part.stackSize = Math.min(remaining, Math.min(64, part.getMaxStackSize()));
                if (part.stackSize < 1) throw new IllegalStateException("Invalid gift stack size");
                result.add(part);
                remaining -= part.stackSize;
            }
        }
        return result;
    }

    private static ItemStack[] copyInventory(EntityPlayerMP player) {
        return copyInventory(player.inventory.mainInventory);
    }

    private static ItemStack[] copyInventory(ItemStack[] inventory) {
        ItemStack[] result = new ItemStack[inventory.length];
        for (int i = 0; i < result.length; i++) {
            ItemStack stack = inventory[i];
            result[i] = stack == null ? null : stack.copy();
        }
        return result;
    }

    static final class Withdrawal {

        final ItemStack[] inventory;
        final int cursor;
        final boolean complete;

        Withdrawal(ItemStack[] inventory, int cursor, boolean complete) {
            this.inventory = inventory;
            this.cursor = cursor;
            this.complete = complete;
        }
    }

    /** Large gifts remain in their package; the saved prefix cursor permits bounded, lossless withdrawals. */
    static Withdrawal planWithdrawal(ItemStack[] original, int selected, List<ItemStack> rewards, int cursor) {
        if (cursor < 0 || cursor > rewards.size()) throw new IllegalStateException("Invalid saved gift progress");
        ItemStack[] complete = copyInventory(original);
        complete[selected] = null;
        if (insert(complete, rewards.subList(cursor, rewards.size())))
            return new Withdrawal(complete, rewards.size(), true);
        ItemStack[] partial = copyInventory(original);
        int delivered = cursor;
        for (; delivered < rewards.size(); delivered++) {
            ItemStack[] trial = copyInventory(partial);
            if (!insert(trial, Collections.singletonList(rewards.get(delivered)))) break;
            partial = trial;
        }
        return delivered == cursor ? null : new Withdrawal(partial, delivered, false);
    }

    /** All-or-nothing inventory plan. Partial insertion never mutates the player's live inventory. */
    static boolean insert(ItemStack[] inventory, List<ItemStack> items) {
        for (ItemStack reward : items) {
            int remaining = reward.stackSize;
            for (ItemStack existing : inventory) {
                if (existing == null || !existing.isItemEqual(reward)
                    || !ItemStack.areItemStackTagsEqual(existing, reward)) continue;
                int count = Math
                    .min(remaining, Math.max(0, Math.min(64, existing.getMaxStackSize()) - existing.stackSize));
                existing.stackSize += count;
                remaining -= count;
            }
            for (int i = 0; i < inventory.length && remaining > 0; i++) {
                if (inventory[i] != null) continue;
                ItemStack copy = reward.copy();
                copy.stackSize = Math.min(remaining, Math.min(64, copy.getMaxStackSize()));
                inventory[i] = copy;
                remaining -= copy.stackSize;
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private void requireReady() {
        if (!isReady()) throw new IllegalStateException("Gift service is unavailable");
    }
}
