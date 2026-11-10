package com.miaokatze.gtit.testlab.scenario;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;

import com.miaokatze.gtit.config.GiftConfig.ItemEntry;
import com.miaokatze.gtit.gift.GiftDefinition;
import com.miaokatze.gtit.gift.GiftRegistry;
import com.miaokatze.gtit.gift.GiftService;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;
import com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher;

/** Explicit, opt-in physical integrated-host stage/restart/resume. Only the generated marker reward is touched. */
final class GiftHostPersistenceScenario implements ProbeScenario {

    private static final String MARKER = "gtitHostGiftProbe";
    private static final String JVM = ManagementFactory.getRuntimeMXBean()
        .getName() + ":"
        + ManagementFactory.getRuntimeMXBean()
            .getStartTime();
    private final boolean stage;

    GiftHostPersistenceScenario(boolean stage) {
        this.stage = stage;
    }

    @Override
    public String id() {
        return stage ? "native-gift-host-stage" : "native-gift-host-resume";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public ProbeTask start(ProbeContext context) {
        return () -> {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.isDedicatedServer()) {
                context.check(
                    "physical-integrated-host",
                    false,
                    "Requires the explicitly owned integrated test world and its real host");
                return true;
            }
            EntityPlayerMP host = server.getConfigurationManager()
                .func_152612_a(server.getServerOwner());
            if (host == null || host instanceof net.minecraftforge.common.util.FakePlayer) {
                context.check("physical-integrated-host", false, "The real integrated host must be online");
                return true;
            }
            Path world = server.worldServerForDimension(0)
                .getSaveHandler()
                .getWorldDirectory()
                .toPath()
                .toAbsolutePath()
                .normalize();
            Path root = world.resolve("gtit_testlab/gift-host");
            Path phase = root.resolve("phase.dat");
            context.check(
                "physical-integrated-host",
                true,
                "host=" + host.getUniqueID() + " name=" + host.getCommandSenderName());
            if (stage) {
                if (Files.exists(root)) {
                    context.check("stage-unused", false, "Existing host fixture must be resumed before another stage");
                    return true;
                }
                if (emptyHotbar(host) < 0 || host.openContainer != host.inventoryContainer) {
                    context.check("host-ready", false, "Close the host GUI and leave one empty hotbar slot");
                    return true;
                }
                Files.createDirectories(root);
                try (GiftSystemNativeScenario.GiftScope scope = new GiftSystemNativeScenario.GiftScope(root)) {
                    String marker = UUID.randomUUID()
                        .toString();
                    GiftDefinition gift = new GiftDefinition();
                    gift.id = "lab-host-" + marker;
                    gift.name = "Owned host persistence fixture";
                    gift.trigger = GiftDefinition.Trigger.BQ_COMPLETE;
                    gift.questId = UUID.randomUUID()
                        .toString();
                    gift.shellKey = "lv";
                    gift.wirelessEU = "0";
                    NBTTagCompound itemTag = new NBTTagCompound();
                    itemTag.setString(MARKER, marker);
                    gift.rewards.add(new ItemEntry("minecraft:paper", 17, 0, itemTag));
                    GiftRegistry.INSTANCE.save(gift, 0);
                    GiftService.INSTANCE.onQuestCompleted(
                        host.getUniqueID(),
                        Collections.singletonList(host.getUniqueID()),
                        UUID.fromString(gift.questId));
                    int slot = packageSlot(host, gift.id);
                    context.check(
                        "host-package-issued",
                        slot >= 0 && slot < 9,
                        "Production service issued its owner-bound package to the host");
                    if (slot < 0 || slot >= 9) return true;
                    ItemStack replay = host.inventory.mainInventory[slot].copy();
                    String token = replay.getTagCompound()
                        .getString(GiftService.TAG_TOKEN);
                    int previousSlot = host.inventory.currentItem;
                    host.inventory.currentItem = slot;
                    boolean opened;
                    try {
                        opened = GiftService.INSTANCE.open(host, host.inventory.mainInventory[slot]);
                    } finally {
                        host.inventory.currentItem = previousSlot;
                    }
                    NBTTagCompound level;
                    try (java.io.InputStream input = Files.newInputStream(world.resolve("level.dat"))) {
                        level = CompressedStreamTools.readCompressed(input);
                    }
                    NBTTagCompound savedPlayer = level.getCompoundTag("Data")
                        .getCompoundTag("Player");
                    context.check(
                        "host-canonical-level-inventory-and-receipt",
                        opened && markedCount(host, marker) == 17
                            && markedCount(savedPlayer, marker) == 17
                            && opened(savedPlayer, token),
                        "Immediately after the gift commit, level.dat contains host inventory and opened receipt together");
                    context.check(
                        "host-worldinfo-snapshot-updated",
                        opened(
                            server.worldServerForDimension(0)
                                .getWorldInfo()
                                .getPlayerNBTTagCompound(),
                            token),
                        "Native same-process reconnect reads the updated WorldInfo.Player snapshot");
                    NBTTagCompound envelope = new NBTTagCompound();
                    envelope.setInteger("schema", 1);
                    envelope.setString("writerJvm", JVM);
                    envelope.setString("world", world.toString());
                    envelope.setString(
                        "host",
                        host.getUniqueID()
                            .toString());
                    envelope.setString("hostName", host.getCommandSenderName());
                    envelope.setString("marker", marker);
                    envelope.setString("token", token);
                    envelope.setString("gift", gift.id);
                    envelope.setTag("replay", replay.writeToNBT(new NBTTagCompound()));
                    AtomicFilePublisher.publishAtomic(phase, CompressedStreamTools.compress(envelope));
                    context.metric("hostRewardCount", markedCount(host, marker));
                }
            } else {
                if (!Files.isRegularFile(phase)) {
                    context.check("phase-present", false, "Missing host stage receipt");
                    return true;
                }
                NBTTagCompound envelope;
                try (java.io.InputStream input = Files.newInputStream(phase)) {
                    envelope = CompressedStreamTools.readCompressed(input);
                }
                boolean identity = envelope.getInteger("schema") == 1 && !JVM.equals(envelope.getString("writerJvm"))
                    && world.toString()
                        .equals(envelope.getString("world"))
                    && host.getUniqueID()
                        .toString()
                        .equals(envelope.getString("host"))
                    && host.getCommandSenderName()
                        .equals(envelope.getString("hostName"));
                context.check(
                    "same-host-new-process",
                    identity,
                    "Resume requires the original world/profile and a different JVM");
                if (!identity) return true;
                String marker = envelope.getString("marker"), token = envelope.getString("token");
                try (GiftSystemNativeScenario.GiftScope scope = new GiftSystemNativeScenario.GiftScope(root, true)) {
                    context.check(
                        "native-host-restart-retains-gift",
                        markedCount(host, marker) == 17 && GiftService.INSTANCE.status(host, envelope.getString("gift"))
                            == GiftService.Status.OPENED,
                        "Real host login restored the committed reward and opened receipt");
                    int slot = emptyHotbar(host);
                    if (slot < 0) {
                        context.check("replay-slot", false, "Leave one empty host hotbar slot");
                        return true;
                    }
                    ItemStack replay = ItemStack.loadItemStackFromNBT(envelope.getCompoundTag("replay"));
                    int previousSlot = host.inventory.currentItem;
                    host.inventory.mainInventory[slot] = replay;
                    host.inventory.currentItem = slot;
                    boolean refused;
                    try {
                        refused = !GiftService.INSTANCE.open(host, replay);
                    } finally {
                        if (host.inventory.mainInventory[slot] == replay) host.inventory.mainInventory[slot] = null;
                        host.inventory.currentItem = previousSlot;
                        host.inventory.markDirty();
                    }
                    context.check(
                        "host-replayed-package-refused",
                        refused && markedCount(host, marker) == 17,
                        "Exact old package token is rejected without creating another reward");
                    context.metric("hostRewardCount", markedCount(host, marker));
                    context.check(
                        "host-receipt-token-preserved",
                        host.getEntityData()
                            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                            .getCompoundTag("gtit_gift_receipts")
                            .getBoolean(token + ":opened"),
                        "Opened receipt remains authoritative");
                }
                // Retain the tiny run-specific phase/ledger as evidence; normal testlab cleanup must not erase host
                // inventory.
            }
            return true;
        };
    }

    private static int emptyHotbar(EntityPlayerMP host) {
        for (int i = 0; i < 9; i++) if (host.inventory.mainInventory[i] == null) return i;
        return -1;
    }

    private static int packageSlot(EntityPlayerMP host, String id) {
        for (int i = 0; i < host.inventory.mainInventory.length; i++) {
            ItemStack s = host.inventory.mainInventory[i];
            if (s != null && s.hasTagCompound()
                && id.equals(
                    s.getTagCompound()
                        .getString(GiftService.TAG_ID)))
                return i;
        }
        return -1;
    }

    private static int markedCount(EntityPlayerMP player, String marker) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) if (stack != null && stack.hasTagCompound()
            && marker.equals(
                stack.getTagCompound()
                    .getString(MARKER)))
            count += stack.stackSize;
        return count;
    }

    private static int markedCount(NBTTagCompound player, String marker) {
        int count = 0;
        NBTTagList list = player.getTagList("Inventory", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack stack = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
            if (stack != null && stack.hasTagCompound()
                && marker.equals(
                    stack.getTagCompound()
                        .getString(MARKER)))
                count += stack.stackSize;
        }
        return count;
    }

    private static boolean opened(NBTTagCompound player, String token) {
        return player != null && player.getCompoundTag("ForgeData")
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
            .getCompoundTag("gtit_gift_receipts")
            .getBoolean(token + ":opened");
    }
}
