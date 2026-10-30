package com.miaokatze.gtit.reincarnation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;

import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;
import com.miaokatze.gtit.reincarnation.core.ReincarnationWorldDeletion;
import com.miaokatze.gtit.reincarnation.gui.ReincarnationContainer;
import com.miaokatze.gtit.reincarnation.gui.ReincarnationLayout;
import com.miaokatze.gtit.reincarnation.handler.HardcoreEnforcer;
import com.miaokatze.gtit.reincarnation.handler.ReincarnationHandler;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;
import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.gameevent.TickEvent;

/** Exercises actual container callbacks without constructing a server, world, or client. */
public final class ReincarnationSessionRegressionTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(ReincarnationSessionRegressionTest.class, args)) return;
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("closeCancelsQueuedConfirmation", () -> checked(() -> queuedConfirmation(true)));
        cases.put("replacementCancelsQueuedConfirmation", () -> checked(() -> queuedConfirmation(false)));
        cases.put("foreignPlayerCannotUseContainer", () -> checked(() -> foreignPlayerCannotUseContainer()));
        cases.put("serverStopClearsSessionState", () -> checked(() -> serverStopClearsSessionState()));
        cases.put("deletionCallbackDoesNotFollowLinks", () -> checked(() -> deletionCallbackDoesNotFollowLinks()));
        cases.put("completionCheckpointsEverySuffixItem", () -> checked(() -> completionCheckpointsEverySuffixItem()));
        TestRunner.run(ReincarnationSessionRegressionTest.class, cases);
    }

    private static void queuedConfirmation(boolean close) throws Exception {
        ServerTaskScheduler.clear();
        EntityPlayerMP player = player();
        ReincarnationContainer container = container(player);
        ReincarnationConfirmHook previous = ReincarnationConfirmHook.Holder.getHook();
        int[] confirmations = { 0 };
        ReincarnationConfirmHook.Holder.setHook(who -> confirmations[0]++);
        try {
            SimpleAssert.that(container.enchantItem(player, ReincarnationLayout.BUTTON_CONFIRM), "first click arms");
            SimpleAssert.that(container.enchantItem(player, ReincarnationLayout.BUTTON_CONFIRM), "second click queues");
            if (close) {
                container.onContainerClosed(player);
            } else {
                player.openContainer = null;
            }
            ServerTaskScheduler.INSTANCE.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
            SimpleAssert.eq(0, confirmations[0], "obsolete confirmation must not reach destructive lifecycle hook");
            SimpleAssert.that(
                !container.enchantItem(player, ReincarnationLayout.BUTTON_UNLOCK_NORMAL),
                "obsolete session cannot queue coin rolls");
        } finally {
            ReincarnationConfirmHook.Holder.setHook(previous);
            ServerTaskScheduler.clear();
        }
    }

    private static void foreignPlayerCannotUseContainer() throws Exception {
        EntityPlayerMP owner = player();
        ReincarnationContainer container = container(owner);
        SimpleAssert.that(container.canInteractWith(owner), "live owner can interact");
        SimpleAssert.that(!container.canInteractWith(player()), "foreign player cannot interact");
        owner.isDead = true;
        SimpleAssert.that(!container.canInteractWith(owner), "dead owner cannot interact");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void serverStopClearsSessionState() throws Exception {
        Field instanceField = field(ReincarnationHandler.class, "INSTANCE");
        Object handler = instanceField.get(null);
        List actions = (List) field(ReincarnationHandler.class, "scheduledActions").get(handler);
        Map rolls = (Map) field(ReincarnationHandler.class, "rollSessions").get(handler);
        actions.add(new Object());
        rolls.put(UUID.randomUUID(), new Object());
        field(ReincarnationHandler.class, "ascensionPlayer").set(handler, player());
        field(ReincarnationHandler.class, "ascensionPollTicksLeft").setInt(handler, 600);
        ReincarnationHandler.class.getMethod("onServerStopping")
            .invoke(null);
        SimpleAssert.that(actions.isEmpty(), "stopped server retains no scheduled actions");
        SimpleAssert.that(rolls.isEmpty(), "stopped server retains no roll sessions");
        SimpleAssert.eq(null, field(ReincarnationHandler.class, "ascensionPlayer").get(handler), "player released");
        SimpleAssert.eq(0, field(ReincarnationHandler.class, "ascensionPollTicksLeft").getInt(handler), "poll reset");
        ReincarnationHandler.class.getMethod("onServerStopping")
            .invoke(null);
    }

    private static EntityPlayerMP player() throws Exception {
        EntityPlayerMP player = allocate(EntityPlayerMP.class);
        player.inventory = new InventoryPlayer(player);
        return player;
    }

    private static void deletionCallbackDoesNotFollowLinks() throws Exception {
        Path base = Files.createTempDirectory("gtit-delete-callback-");
        try {
            Path outside = Files.createDirectories(base.resolve("other-save"));
            Path sentinel = Files.write(outside.resolve("level.dat"), new byte[] { 42 });
            Path selected = Files.createDirectories(base.resolve("selected-save"));
            Files.createSymbolicLink(selected.resolve("linked-save"), outside);
            Method delete = HardcoreEnforcer.class.getDeclaredMethod("deleteRecursively", java.io.File.class);
            delete.setAccessible(true);
            SimpleAssert.eq(true, delete.invoke(null, selected.toFile()), "selected save deletion succeeds");
            SimpleAssert.that(Files.exists(sentinel), "actual deletion callback does not destroy linked save");
        } finally {
            ReincarnationWorldDeletion.deleteTree(base);
        }
    }

    private static void completionCheckpointsEverySuffixItem() throws Exception {
        Path base = Files.createTempDirectory("gtit-grant-suffix-");
        Object handler = field(ReincarnationHandler.class, "INSTANCE").get(null);
        Field storeField = field(ReincarnationHandler.class, "store");
        Object previousStore = storeField.get(handler);
        try {
            Item.itemRegistry.addObject(31001, "minecraft:gtit_reincarnation_regression_item", new Item());
            SimpleAssert.that(
                Item.itemRegistry.getObject("minecraft:gtit_reincarnation_regression_item") != null,
                "native fixture registry ID resolves");
            ReincarnationStore store = new ReincarnationStore(base.toFile());
            storeField.set(handler, store);
            List<ReincarnationCycle.ItemRef> items = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                items.add(new ReincarnationCycle.ItemRef("minecraft:gtit_reincarnation_regression_item", 0));
            }
            ReincarnationCycle cycle = new ReincarnationCycle(GrantPlayer.ID.toString());
            cycle.deposit(items);
            cycle.confirmReincarnation(42L);
            store.save(cycle);
            store.markGrantDelivered(GrantPlayer.ID.toString(), 7);
            GrantPlayer player = allocate(GrantPlayer.class);
            FailingInventory inventory = new FailingInventory(player);
            player.inventory = inventory;
            Class<?> context = Class.forName(ReincarnationHandler.class.getName() + "$GrantShowContext");
            Constructor<?> constructor = context.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            Method complete = ReincarnationHandler.class
                .getDeclaredMethod("completeGrant", EntityPlayerMP.class, context);
            complete.setAccessible(true);
            complete.invoke(handler, player, constructor.newInstance(7));
            SimpleAssert.eq(1, inventory.deliveries, "first suffix item delivered before injected failure");
            int delivered = store.readGrantDelivered(GrantPlayer.ID.toString());
            SimpleAssert.eq(8, delivered, "successful suffix item checkpoint survives the next handoff failure");
            inventory.failOn = -1;
            complete.invoke(handler, player, constructor.newInstance(delivered));
            SimpleAssert.eq(3, inventory.deliveries, "resume delivers the three suffix items exactly once");
            SimpleAssert.that(
                !store.load(GrantPlayer.ID.toString())
                    .canClaimGrant(),
                "successful resume clears mailbox");
        } finally {
            storeField.set(handler, previousStore);
            ReincarnationWorldDeletion.deleteTree(base);
        }
    }

    private static final class FailingInventory extends InventoryPlayer {

        int calls;
        int deliveries;
        int failOn = 2;

        FailingInventory(EntityPlayerMP player) {
            super(player);
        }

        @Override
        public boolean addItemStackToInventory(ItemStack stack) {
            if (++calls == failOn) {
                throw new IllegalStateException("Injected inventory handoff failure");
            }
            deliveries++;
            return true;
        }
    }

    private static final class GrantPlayer extends EntityPlayerMP {

        static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

        private GrantPlayer() {
            super(null, null, null, null); // Never called: the fixture allocates without constructing a server.
        }

        @Override
        public UUID getUniqueID() {
            return ID;
        }

        @Override
        public String getCommandSenderName() {
            return "reincarnation-regression";
        }

        @Override
        public void addChatMessage(IChatComponent component) {}
    }

    private static ReincarnationContainer container(EntityPlayerMP player) throws Exception {
        ReincarnationContainer container = allocate(ReincarnationContainer.class);
        field(ReincarnationContainer.class, "player").set(container, player);
        field(ReincarnationContainer.class, "server").setBoolean(container, true);
        ReincarnationCycle cycle = new ReincarnationCycle("11111111-2222-3333-4444-555555555555");
        cycle.deposit(Collections.singletonList(new ReincarnationCycle.ItemRef("minecraft:diamond", 0)));
        field(ReincarnationContainer.class, "cycle").set(container, cycle);
        player.openContainer = container;
        return container;
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Object instance = field(unsafe, "theUnsafe").get(null);
        return type.cast(
            unsafe.getMethod("allocateInstance", Class.class)
                .invoke(instance, type));
    }

    private static void checked(Checked action) {
        try {
            action.run();
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private interface Checked {

        void run() throws Exception;
    }
}
