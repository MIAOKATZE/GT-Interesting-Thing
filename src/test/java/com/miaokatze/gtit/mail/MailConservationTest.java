package com.miaokatze.gtit.mail;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;


/** Actual mail models/manager and NBT; no server, network, player or Forge drop event required. */
public class MailConservationTest {

    public static void main(String[] args) throws Exception {
        if (!com.miaokatze.gtit.testutil.MinecraftTestBootstrap.enter(MailConservationTest.class, args)) return;
        // These focused fixtures bind a temporary save directory instead of starting a server.
        try {
            Field ready = MailManager.class.getDeclaredField("sessionUnavailable");
            ready.setAccessible(true);
            ready.setBoolean(MailManager.INSTANCE, false);
        } catch (NoSuchFieldException legacyBaseline) { /* Pre-fence baseline comparison. */ }
        String[] cases = {
            "largeAttachmentCountsAndTagsSurviveNbt", "legacyByteCountRemainsReadable",
            "fullMailboxNeverEvictsExistingAttachments", "fullMailboxDefersFirstAndOnceRewardsWithoutConsumingReceipts",
            "rejectedAndPartialDeliverySurviveReloadWithoutReplay", "completedRowsDoNotReplayWhenLaterRowIsRejected",
            "managerRetainsRejectedDropAfterPartialInsertion", "managerSplitsAcceptedDropsIntoLegalStacks",
            "failedWritesRetainSourceAndRemoveUncommittedMail", "corruptExistingMailboxNeverBecomesAnEmptyMailbox",
            "worldInitializationSavesOldMailboxAndLoadsOnlyNewWorld", "failedWorldSwitchNeverServesOldMailAsNewWorld"
        };
        int failed = 0;
        for (String name : cases) {
            if (args.length > 0 && "baseline".equals(args[0]) && (name.startsWith("rejected") || name.startsWith("completed"))) continue;
            try {
                MailConservationTest.class.getMethod(name).invoke(null);
                System.out.println("PASS " + name);
            } catch (ReflectiveOperationException failure) {
                failed++;
                System.err.println("FAIL " + name + ": " + failure.getCause());
            }
        }
        if (failed != 0) throw new AssertionError(failed + " mail conservation regressions failed");
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
    private static void assertSame(Object expected, Object actual) { if (expected != actual) throw new AssertionError("Identity changed"); }
    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void fail(String message) { throw new AssertionError(message); }

    private static int deliver(Mail mail, java.util.function.Consumer<ItemStack> delivery) {
        try {
            java.lang.reflect.Method method = Mail.class.getDeclaredMethod("deliverAttachments", java.util.function.Consumer.class);
            method.setAccessible(true);
            return (Integer) method.invoke(mail, delivery);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Mail reward(int count) {
        ItemStack stack = new ItemStack(Items.iron_ingot, count, 0);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("owner", "exact-original-nbt");
        stack.setTagCompound(tag);
        return new Mail("reward", "content", "sender", Collections.singletonList(stack));
    }

    public static void largeAttachmentCountsAndTagsSurviveNbt() {
        for (int amount : new int[] { 1, 16, 64, 127, 128, 255, 256, 4096, Integer.MAX_VALUE }) {
            Mail saved = reward(amount);
            Mail loaded = Mail.fromNBT(saved.writeToNBT());
            assertEquals(amount, loaded.getAttachments().get(0).stackSize);
            assertEquals(saved.getAttachments().get(0).getTagCompound(), loaded.getAttachments().get(0).getTagCompound());
        }
    }

    public static void legacyByteCountRemainsReadable() {
        NBTTagCompound nbt = reward(16).writeToNBT();
        NBTTagCompound item = nbt.getTagList("attachments", 10).getCompoundTagAt(0);
        item.removeTag("gtitMailCount");
        item.removeTag("gtitMailRemaining");
        assertEquals(16, Mail.fromNBT(nbt).getAttachments().get(0).stackSize);
    }

    public static void fullMailboxNeverEvictsExistingAttachments() {
        MailData data = new MailData();
        Mail first = reward(16);
        assertTrue(data.addMail(first));
        for (int i = 1; i < MailManager.MAX_MAILS; i++) assertTrue(data.addMail(reward(64)));
        data.addMailForced(reward(256));
        assertSame(first, data.findMail(first.getId()));
        assertEquals(MailManager.MAX_MAILS, data.getMails().size());
    }

    public static void fullMailboxDefersFirstAndOnceRewardsWithoutConsumingReceipts() throws Exception {
        MailManager manager = MailManager.INSTANCE;
        Field dir = MailManager.class.getDeclaredField("saveDir");
        dir.setAccessible(true);
        Object originalDir = dir.get(manager);
        dir.set(manager, Files.createTempDirectory("gtit-mail-conservation-").toFile());
        UUID player = UUID.randomUUID();
        MailData data = manager.getMailData(player);
        Mail old = reward(16);
        data.addMail(old);
        for (int i = 1; i < MailManager.MAX_MAILS; i++) data.addMail(reward(64));
        Field first = MailManager.class.getDeclaredField("firstRewardTemplate");
        first.setAccessible(true);
        Object oldFirst = first.get(manager);
        Map<String, Mail> rewards = manager.getOnceRewards();
        String id = "test-" + UUID.randomUUID();
        first.set(manager, reward(256));
        rewards.put(id, reward(128));
        try {
            assertFalse(manager.deliverPendingRewards(player));
            assertFalse(data.isFirstRewardReceived());
            assertFalse(data.hasReceivedOnce(id));
            assertSame(old, data.findMail(old.getId()));
            data.removeMail(old.getId());
            assertTrue(manager.deliverPendingRewards(player));
            assertTrue(data.isFirstRewardReceived());
            assertFalse(data.hasReceivedOnce(id));
            data.removeMail(data.getMails().get(data.getMails().size() - 1).getId());
            assertTrue(manager.deliverPendingRewards(player));
            assertTrue(data.hasReceivedOnce(id));
            assertFalse(manager.deliverPendingRewards(player));
            MailData reload = new MailData();
            reload.readFromNBT(data.writeToNBT());
            assertTrue(reload.isFirstRewardReceived());
            assertTrue(reload.hasReceivedOnce(id));
            assertEquals(128, reload.getMails().get(0).getAttachments().get(0).stackSize);
        } finally {
            rewards.remove(id);
            first.set(manager, oldFirst);
            manager.unloadMailData(player);
            dir.set(manager, originalDir);
        }
    }

    public static void rejectedAndPartialDeliverySurviveReloadWithoutReplay() {
        Mail mail = reward(256);
        assertEquals(0, deliver(mail, remainder -> {}));
        assertTrue(mail.hasUnclaimedAttachments());
        assertEquals(1, deliver(mail, remainder -> remainder.stackSize -= 16));
        assertTrue(mail.hasUnclaimedAttachments());
        Mail reload = Mail.fromNBT(mail.writeToNBT());
        final int[] delivered = { 16 };
        assertEquals(1, deliver(reload, remainder -> {
            assertEquals(240, remainder.stackSize);
            assertEquals("exact-original-nbt", remainder.getTagCompound().getString("owner"));
            delivered[0] += remainder.stackSize;
            remainder.stackSize = 0;
        }));
        assertEquals(256, delivered[0]);
        assertFalse(reload.hasUnclaimedAttachments());
        assertEquals(0, deliver(reload, remainder -> fail("Completed attachment must not replay")));
        assertEquals(256, reload.getAttachments().get(0).stackSize);
        assertFalse(Mail.fromNBT(reload.writeToNBT()).hasUnclaimedAttachments());
    }

    public static void completedRowsDoNotReplayWhenLaterRowIsRejected() {
        Mail mail = new Mail("mixed", "", "", Arrays.asList(new ItemStack(Items.iron_ingot, 16), new ItemStack(Items.gold_ingot, 32)));
        assertEquals(1, deliver(mail, remainder -> {
            if (remainder.getItem() == Items.iron_ingot) remainder.stackSize = 0;
        }));
        Mail reload = Mail.fromNBT(mail.writeToNBT());
        assertEquals(1, deliver(reload, remainder -> {
            assertSame(Items.gold_ingot, remainder.getItem());
            assertEquals(32, remainder.stackSize);
            remainder.stackSize = 0;
        }));
        assertFalse(reload.hasUnclaimedAttachments());
    }
    public static void managerRetainsRejectedDropAfterPartialInsertion() throws Exception {
        net.minecraft.entity.player.EntityPlayerMP player = player(false);
        for (int i = 0; i < player.inventory.mainInventory.length; i++)
            player.inventory.mainInventory[i] = new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.cobblestone), 64);
        ItemStack merge = reward(63).getAttachments().get(0).copy();
        player.inventory.mainInventory[0] = merge;
        Mail mail = reward(16);
        MailManager.INSTANCE.getMailData(player.getUniqueID()).addMail(mail);
        try {
            assertEquals(1, MailManager.INSTANCE.claimAttachments(player.getUniqueID(), mail.getId(), player));
            assertEquals(64, player.inventory.mainInventory[0].stackSize);
            assertTrue(mail.hasUnclaimedAttachments());
            Mail reload = Mail.fromNBT(mail.writeToNBT());
            assertEquals(1, deliver(reload, remainder -> {
                assertEquals(15, remainder.stackSize);
                remainder.stackSize = 0;
            }));
        } finally {
            MailManager.INSTANCE.unloadMailData(player.getUniqueID());
        }
    }

    public static void managerSplitsAcceptedDropsIntoLegalStacks() throws Exception {
        net.minecraft.entity.player.EntityPlayerMP player = player(true);
        for (int i = 0; i < player.inventory.mainInventory.length; i++)
            player.inventory.mainInventory[i] = new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.cobblestone), 64);
        Mail mail = reward(256);
        MailManager.INSTANCE.getMailData(player.getUniqueID()).addMail(mail);
        try {
            assertEquals(1, MailManager.INSTANCE.claimAttachments(player.getUniqueID(), mail.getId(), player));
            assertEquals(256, ((DropWorld) player.worldObj).delivered);
            assertEquals(4, ((DropWorld) player.worldObj).calls);
            assertFalse(mail.hasUnclaimedAttachments());
        } finally {
            MailManager.INSTANCE.unloadMailData(player.getUniqueID());
        }
    }

    public static void failedWritesRetainSourceAndRemoveUncommittedMail() throws Exception {
        MailManager manager = MailManager.INSTANCE;
        Field directory = MailManager.class.getDeclaredField("saveDir");
        directory.setAccessible(true);
        Object previous = directory.get(manager);
        java.nio.file.Path root = Files.createTempDirectory("gtit-mail-failed-write-");
        UUID id = UUID.randomUUID();
        // A nonempty directory cannot be replaced by the new mailbox file.
        java.nio.file.Path blocked = Files.createDirectory(root.resolve(id + ".dat"));
        Files.write(blocked.resolve("keep"), new byte[] { 1 });
        directory.set(manager, root.toFile());
        Field cache = MailManager.class.getDeclaredField("mailDataMap");
        cache.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID, MailData> cached = (Map<UUID, MailData>) cache.get(manager);
        MailData data = new MailData();
        cached.put(id, data);
        try {
            boolean failed = false;
            try { manager.sendMail(id, reward(16)); } catch (IllegalStateException expected) { failed = true; }
            assertTrue(failed);
            assertTrue(data.getMails().isEmpty());
            assertTrue(Files.exists(blocked.resolve("keep")));
        } finally {
            cached.remove(id);
            directory.set(manager, previous);
        }
    }

    public static void corruptExistingMailboxNeverBecomesAnEmptyMailbox() throws Exception {
        MailManager manager = MailManager.INSTANCE;
        Field directory = MailManager.class.getDeclaredField("saveDir");
        directory.setAccessible(true);
        Object previous = directory.get(manager);
        java.nio.file.Path root = Files.createTempDirectory("gtit-mail-bad-read-");
        directory.set(manager, root.toFile());
        UUID id = UUID.randomUUID();
        java.nio.file.Path path = root.resolve(id + ".dat");
        byte[] original = { 1, 2, 3, 4, 5 };
        Files.write(path, original);
        try {
            boolean failed = false;
            try { manager.getMailData(id); } catch (IllegalStateException expected) { failed = true; }
            assertTrue(failed);
            manager.saveMailData(id);
            assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(path)));
        } finally {
            directory.set(manager, previous);
        }
    }

    public static void worldInitializationSavesOldMailboxAndLoadsOnlyNewWorld() throws Exception {
        MailManager manager = MailManager.INSTANCE;
        UUID id = UUID.randomUUID();
        java.nio.file.Path oldRoot = Files.createTempDirectory("gtit-mail-old-world-");
        java.nio.file.Path newRoot = Files.createTempDirectory("gtit-mail-new-world-");
        Field directory = MailManager.class.getDeclaredField("saveDir");
        directory.setAccessible(true);
        directory.set(manager, oldRoot.toFile());
        Mail old = reward(256);
        manager.getMailData(id).addMail(old);
        net.minecraft.world.World world = player(false).worldObj;
        Object saveHandler = java.lang.reflect.Proxy.newProxyInstance(MailConservationTest.class.getClassLoader(),
            new Class<?>[] { net.minecraft.world.storage.ISaveHandler.class }, (proxy, method, arguments) ->
                method.getName().equals("getWorldDirectory") ? newRoot.toFile() : null);
        Field handler = net.minecraft.world.World.class.getDeclaredField("saveHandler");
        handler.setAccessible(true);
        handler.set(world, saveHandler);
        manager.init(world);
        assertTrue(Files.exists(oldRoot.resolve(id + ".dat")));
        assertTrue(manager.getMailData(id).getMails().isEmpty());
        NBTTagCompound saved = net.minecraft.nbt.CompressedStreamTools.read(oldRoot.resolve(id + ".dat").toFile());
        MailData oldData = new MailData();
        oldData.readFromNBT(saved.getCompoundTag("mail"));
        assertEquals(256, oldData.findMail(old.getId()).getAttachments().get(0).stackSize);
        manager.unloadMailData(id);
    }

    public static void failedWorldSwitchNeverServesOldMailAsNewWorld() throws Exception {
        MailManager manager = MailManager.INSTANCE;
        java.nio.file.Path oldRoot = Files.createTempDirectory("gtit-mail-failed-old-");
        java.nio.file.Path newRoot = Files.createTempDirectory("gtit-mail-unbound-new-");
        UUID id = UUID.randomUUID();
        Field directory = MailManager.class.getDeclaredField("saveDir");
        directory.setAccessible(true);
        directory.set(manager, oldRoot.toFile());
        manager.getMailData(id).addMail(reward(16));
        java.nio.file.Path blocker = Files.createDirectory(oldRoot.resolve(id + ".dat"));
        Files.write(blocker.resolve("keep"), new byte[] { 1 });
        net.minecraft.world.World world = player(false).worldObj;
        Object handler = java.lang.reflect.Proxy.newProxyInstance(MailConservationTest.class.getClassLoader(),
            new Class<?>[] { net.minecraft.world.storage.ISaveHandler.class }, (proxy, method, arguments) ->
                method.getName().equals("getWorldDirectory") ? newRoot.toFile() : null);
        Field save = net.minecraft.world.World.class.getDeclaredField("saveHandler");
        save.setAccessible(true);
        save.set(world, handler);
        boolean failed = false;
        try { manager.init(world); } catch (IllegalStateException expected) { failed = true; }
        assertTrue(failed);
        failed = false;
        try { manager.getMailData(id); } catch (IllegalStateException expected) { failed = true; }
        assertTrue(failed);
        assertFalse(Files.exists(newRoot.resolve("gtit_mail").resolve(id + ".dat")));
        assertTrue(Files.exists(blocker.resolve("keep")));
    }

    private static net.minecraft.entity.player.EntityPlayerMP player(boolean accept) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        DropWorld world = (DropWorld) unsafe.allocateInstance(DropWorld.class);
        Field provider = net.minecraft.world.World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(world, new net.minecraft.world.WorldProviderSurface());
        world.accept = accept;
        net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP)
            unsafe.allocateInstance(net.minecraft.entity.player.EntityPlayerMP.class);
        Field id = net.minecraft.entity.Entity.class.getDeclaredField("entityUniqueID");
        id.setAccessible(true);
        id.set(player, UUID.randomUUID());
        player.worldObj = world;
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        player.capabilities = new net.minecraft.entity.player.PlayerCapabilities();
        player.inventoryContainer = new net.minecraft.inventory.Container() {
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer ignored) { return true; }
        };
        return player;
    }

    /** Only world acceptance is controlled; actual ItemStack, player inventory, EntityItem and manager execute. */
    private static final class DropWorld extends net.minecraft.world.WorldServer {
        boolean accept;
        int calls;
        int delivered;
        private DropWorld() { super(null, null, "", 0, null, null); }
        @Override public boolean spawnEntityInWorld(net.minecraft.entity.Entity entity) {
            calls++;
            ItemStack stack = ((net.minecraft.entity.item.EntityItem) entity).getEntityItem();
            if (accept) {
                assertTrue(stack.stackSize > 0 && stack.stackSize <= Math.min(64, stack.getMaxStackSize()));
                delivered += stack.stackSize;
            }
            return accept;
        }
    }

}
