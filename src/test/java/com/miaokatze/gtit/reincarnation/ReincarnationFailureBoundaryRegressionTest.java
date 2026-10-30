package com.miaokatze.gtit.reincarnation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;
import com.miaokatze.gtit.reincarnation.core.ReincarnationWorldDeletion;
import com.miaokatze.gtit.reincarnation.entity.EntityAscensionCarrier;
import com.miaokatze.gtit.reincarnation.entity.ReincarnationEntities;
import com.miaokatze.gtit.reincarnation.gui.ReincarnationContainer;
import com.miaokatze.gtit.reincarnation.handler.HardcoreEnforcer;
import com.miaokatze.gtit.reincarnation.handler.ReincarnationHandler;
import com.miaokatze.gtit.reincarnation.storage.ReincarnationWorldData;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;
import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.gameevent.TickEvent;

/** Native IntegratedServer/MapStorage/slot boundaries; no game or network launch. */
public final class ReincarnationFailureBoundaryRegressionTest {

    private static final String ID = ReincarnationDurabilityRegressionTest.ID;
    private static final String ITEM = "minecraft:gtit_reincarnation_failure_boundary";

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(ReincarnationFailureBoundaryRegressionTest.class, args)) return;
        Item.itemRegistry.addObject(31003, ITEM, new Item());
        SimpleAssert.that(Item.itemRegistry.getObject(ITEM) != null, "native fixture registry ID resolves");
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("globalWriteFailureCannotLoseAcceptedDeposit", () -> checked(() -> transferFailure(false)));
        cases.put("globalWriteFailureCannotResurrectWithdrawal", () -> checked(() -> transferFailure(true)));
        cases.put("localProgressDoesNotRequireGlobalLicenseWrite", () -> checked(() -> localProgress()));
        cases.put("failedWorldCheckpointRetainsAcceptedReceipt", () -> checked(() -> localCheckpointFailure()));
        cases.put("lanTransitionBlocksAlreadyQueuedKill", () -> checked(() -> queuedKill()));
        cases.put("lanDeathCannotArmWorldDeletion", () -> checked(() -> sharedDeath()));
        cases.put("lanTransitionRestoresEnforcerOverrides", () -> checked(() -> cancelOverrides(false)));
        cases.put("lanTransitionPreservesOriginallyHardcoreWorld", () -> checked(() -> cancelOverrides(true)));
        cases.put("lanTransitionCancelsAscensionWithoutFall", () -> checked(() -> cancelCarrier()));
        cases.put("lanTransitionCancelsDeletionCountdown", () -> checked(() -> cancelDeletion()));
        cases.put("sharedSessionCannotBeginAscension", () -> checked(() -> sharedStart()));
        cases.put("missingMailboxCannotAcknowledgeHandoff", () -> checked(() -> unavailableMailbox(false)));
        cases.put("unreadableMailboxCannotAcknowledgeHandoff", () -> checked(() -> unavailableMailbox(true)));
        cases.put("sharedCancellationIsIdempotentAndPreservesEscrow", () -> checked(() -> repeatedCancel()));
        cases.put("sharedCancellationPreservesIntentionalModeChange", () -> checked(() -> changedMode()));
        cases.put("sharedCancellationDoesNotTeleportAcrossWorlds", () -> checked(() -> crossWorldCancel()));
        cases.put("sharedCancellationRejectsInvalidOrigin", () -> checked(() -> invalidOrigin()));
        cases.put("sharedShutdownCancelsPendingDeletionWithoutTick", () -> checked(() -> sharedShutdown()));
        cases.put("cancellationDoesNotUndoUnappliedModeOverride", () -> checked(() -> unappliedMode()));
        cases.put("rejectedCarrierSpawnPreservesPriorMount", () -> checked(() -> carrierSpawn(false)));
        cases.put("acceptedCarrierSpawnMountsNativeCarrier", () -> checked(() -> carrierSpawn(true)));
        TestRunner.run(ReincarnationFailureBoundaryRegressionTest.class, cases);
    }

    private static void carrierSpawn(boolean accepted) throws Exception {
        withSession(s -> {
            BoundaryWorld world = (BoundaryWorld) s.world;
            world.rejectSpawn = !accepted;
            Entity priorMount = ReincarnationDurabilityRegressionTest.allocate(EntityAscensionCarrier.class);
            s.player.ridingEntity = priorMount;
            EntityAscensionCarrier result = ReincarnationEntities.spawnFor(s.player, 37);
            SimpleAssert.that(world.spawnAttempt instanceof EntityAscensionCarrier, "native carrier spawn attempted");
            if (accepted) {
                SimpleAssert.that(result == world.spawnAttempt, "accepted native carrier returned");
                SimpleAssert.that(s.player.ridingEntity == result, "accepted carrier mounted");
                SimpleAssert.eq(37, result.getDurationTicks(), "requested ascent duration preserved");
            } else {
                SimpleAssert.eq(null, result, "rejected spawn signals the existing delayed-death fallback");
                SimpleAssert.that(s.player.ridingEntity == priorMount, "rejected carrier cannot disturb prior mount");
                SimpleAssert.that(!priorMount.isDead, "prior mount must not be torn down");
            }
        });
    }

    private static void transferFailure(boolean withdrawal) throws Exception {
        withSession(s -> {
            ReincarnationContainer first = container(s, withdrawal);
            Slot slot = first.getSlot(15);
            // Fail just the unrelated global temporary-file replacement, retaining a readable license.
            Files.createDirectory(s.base.resolve("config/gtit/reincarnation.dat.tmp"));
            if (withdrawal) {
                ItemStack taken = slot.decrStackSize(1);
                slot.onPickupFromSlot(s.player, taken);
                s.player.inventory.addItemStackToInventory(taken);
            } else {
                slot.putStack(new ItemStack((Item) Item.itemRegistry.getObject(ITEM), 1, 0));
            }
            first.onContainerClosed(s.player);
            container(s, false).onContainerClosed(s.player);
            ServerTaskScheduler.INSTANCE.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
            ReincarnationWorldData data = ReincarnationWorldData.get(s.world);
            data.saveImmediately(s.world);
            SimpleAssert.eq(
                withdrawal ? 0 : 1,
                ReincarnationDurabilityRegressionTest.reload(s.world)
                    .getDepositedItems()
                    .size(),
                "accepted movement must survive close/reopen and actual native saved-world reload");
            SimpleAssert.eq(withdrawal ? 1 : 0, countItems(s.player), "withdrawn source transferred exactly once");
        });
    }

    private static void localProgress() throws Exception {
        withSession(s -> {
            ReincarnationContainer container = container(s, false);
            Files.createDirectory(s.base.resolve("config/gtit/reincarnation.dat.tmp"));
            ReincarnationCycle cycle = (ReincarnationCycle) field(ReincarnationContainer.class, "cycle").get(container);
            cycle.recordHullConsumption(1, 4);
            cycle.setUnlockedRows(2);
            container.onContainerClosed(s.player);
            ReincarnationWorldData data = ReincarnationWorldData.get(s.world);
            data.saveImmediately(s.world);
            ReincarnationWorldData saved = ReincarnationDurabilityRegressionTest.reload(s.world);
            SimpleAssert.eq(4, saved.getHullProgress()[1], "consumed hull receipt is world-local");
            SimpleAssert.eq(2, saved.getUnlockedRows(), "paid row progress is world-local");
        });
    }

    private static void localCheckpointFailure() throws Exception {
        withSession(s -> {
            ReincarnationContainer first = container(s, false);
            first.getSlot(15)
                .putStack(new ItemStack((Item) Item.itemRegistry.getObject(ITEM), 1, 0));
            first.onContainerClosed(s.player);
            ReincarnationWorldData data = ReincarnationWorldData.get(s.world);
            Path target = s.world.getSaveHandler()
                .getMapFileFromName(ReincarnationWorldData.DATA_NAME)
                .toPath();
            Files.createDirectory(target);
            boolean failed = false;
            try {
                data.saveImmediately(s.world);
            } catch (IllegalStateException expected) {
                failed = true;
            }
            SimpleAssert.that(failed && data.isDirty(), "failed write cannot acknowledge/mark receipt clean");
            container(s, false).onContainerClosed(s.player);
            SimpleAssert.eq(
                1,
                data.getDepositedItems()
                    .size(),
                "close/reopen retains the unsaved accepted receipt");
            Files.delete(target);
            data.saveImmediately(s.world);
            SimpleAssert.that(!data.isDirty(), "only successful replacement acknowledges the checkpoint");
            SimpleAssert.eq(
                1,
                ReincarnationDurabilityRegressionTest.reload(s.world)
                    .getDepositedItems()
                    .size(),
                "recovered checkpoint survives native reload without replaying the physical source");
        });
    }

    private static void queuedKill() throws Exception {
        withSession(s -> {
            publish(s);
            method(ReincarnationHandler.class, "killPlayer", EntityPlayerMP.class, String.class)
                .invoke(s.handler, s.player, "already queued fallback");
            SimpleAssert.eq(0, s.player.attacks, "newly shared session must not execute delayed ascension damage");
        });
    }

    private static void sharedDeath() throws Exception {
        withSession(s -> {
            HardcoreEnforcer.beginEnforcement(s.player);
            publish(s);
            LivingDeathEvent event = new LivingDeathEvent(s.player, DamageSource.outOfWorld);
            s.enforcer.onLivingDeath(event);
            SimpleAssert.eq(
                -1,
                field(HardcoreEnforcer.class, "deleteCountdownTicks").getInt(s.enforcer),
                "LAN owner death must not arm reincarnation deletion");
            SimpleAssert.eq(null, field(HardcoreEnforcer.class, "pendingDeleteDir").get(null), "no deletion target");
        });
    }

    private static void cancelOverrides(boolean initiallyHardcore) throws Exception {
        withSession(s -> {
            field(WorldInfo.class, "hardcore").setBoolean(s.world.getWorldInfo(), initiallyHardcore);
            s.player.manager.type = GameType.CREATIVE;
            HardcoreEnforcer.beginEnforcement(s.player);
            s.enforcer.onServerTick(tick());
            SimpleAssert.that(
                s.world.getWorldInfo()
                    .isHardcoreModeEnabled(),
                "enforcement applied hardcore");
            SimpleAssert.eq(GameType.SURVIVAL, s.player.manager.type, "enforcement applied survival");
            publish(s);
            s.enforcer.onServerTick(tick());
            SimpleAssert.that(!HardcoreEnforcer.isActive(), "published session cancels enforcement");
            SimpleAssert.eq(
                initiallyHardcore,
                s.world.getWorldInfo()
                    .isHardcoreModeEnabled(),
                "restore original value, never downgrade a genuinely hardcore world");
            SimpleAssert
                .eq(GameType.CREATIVE, s.player.manager.type, "restore only this enforcer's game-mode override");
        });
    }

    private static void cancelCarrier() throws Exception {
        withSession(s -> {
            s.player.posX = 12;
            s.player.posY = 64;
            s.player.posZ = 34;
            method(ReincarnationHandler.class, "beginAscension", EntityPlayerMP.class).invoke(s.handler, s.player);
            s.actions()
                .clear(); // Isolate cancellation from subsequent scheduled actions.
            EntityAscensionCarrier carrier = ReincarnationDurabilityRegressionTest
                .allocate(EntityAscensionCarrier.class);
            field(ReincarnationHandler.class, "activeCarrier").set(s.handler, carrier);
            field(ReincarnationHandler.class, "ascensionPlayer").set(s.handler, s.player);
            field(ReincarnationHandler.class, "ascensionPollTicksLeft").setInt(s.handler, 600);
            s.player.ridingEntity = carrier;
            s.player.posY = 120;
            s.player.fallDistance = 20;
            publish(s);
            s.handler.onServerTick(tick());
            SimpleAssert.eq(
                null,
                field(ReincarnationHandler.class, "activeCarrier").get(s.handler),
                "shared session must release destructive ascension context");
            SimpleAssert.eq(null, s.player.ridingEntity, "dismounted from canceled carrier");
            SimpleAssert.that(carrier.isDead, "canceled carrier removed");
            SimpleAssert.that(
                s.player.posX == 12 && s.player.posY == 64 && s.player.posZ == 34,
                "return to original safe position instead of dropping player from ascent height");
            SimpleAssert.that(s.player.fallDistance == 0, "no accumulated fall damage");
            SimpleAssert.eq(0, s.player.attacks, "no kill on cancellation");
        });
    }

    private static void cancelDeletion() throws Exception {
        withSession(s -> {
            HardcoreEnforcer.beginEnforcement(s.player);
            field(HardcoreEnforcer.class, "deleteCountdownTicks").setInt(s.enforcer, 1);
            field(HardcoreEnforcer.class, "pendingDeleteDir").set(
                null,
                s.world.getSaveHandler()
                    .getWorldDirectory());
            publish(s);
            s.enforcer.onServerTick(tick());
            SimpleAssert.eq(
                -1,
                field(HardcoreEnforcer.class, "deleteCountdownTicks").getInt(s.enforcer),
                "LAN publication cancels outstanding countdown");
            SimpleAssert.eq(
                null,
                field(HardcoreEnforcer.class, "pendingDeleteDir").get(null),
                "stop hook cannot subsequently delete the newly shared world");
        });
    }

    private static void sharedStart() throws Exception {
        withSession(s -> {
            publish(s);
            method(ReincarnationHandler.class, "beginAscension", EntityPlayerMP.class).invoke(s.handler, s.player);
            SimpleAssert.that(!HardcoreEnforcer.isActive(), "no new enforcement in shared session");
            SimpleAssert.that(
                s.actions()
                    .isEmpty(),
                "no fallback kill scheduled in shared session");
            SimpleAssert.that(
                !s.world.getWorldInfo()
                    .isHardcoreModeEnabled(),
                "no hardcore override");
        });
    }

    private static void unavailableMailbox(boolean unreadable) throws Exception {
        withSession(s -> {
            ReincarnationCycle cycle = new ReincarnationCycle(ID);
            cycle.deposit(Collections.singletonList(new ReincarnationCycle.ItemRef(ITEM, 0)));
            cycle.confirmReincarnation(42);
            s.store.save(cycle);
            Class<?> contextClass = Class.forName(ReincarnationHandler.class.getName() + "$GrantShowContext");
            Constructor<?> constructor = contextClass.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            Object context = constructor.newInstance(0);
            Method grant = method(
                ReincarnationHandler.class,
                "grantShowItem",
                EntityPlayerMP.class,
                contextClass,
                int.class,
                ReincarnationCycle.ItemRef.class);
            Path target = s.base.resolve("config/gtit/reincarnation.dat");
            Path backup = target.resolveSibling("intact-license");
            Files.move(target, backup);
            if (unreadable) Files.createDirectory(target);
            try {
                grant.invoke(
                    s.handler,
                    s.player,
                    context,
                    0,
                    cycle.getPendingItems()
                        .get(0));
            } catch (InvocationTargetException expected) {
                if (!(expected.getCause() instanceof IllegalStateException)) throw expected;
            }
            SimpleAssert.eq(0, countItems(s.player), "unavailable mailbox is not an acknowledged checkpoint");
            if (unreadable) Files.delete(target);
            Files.move(backup, target);
            grant.invoke(
                s.handler,
                s.player,
                context,
                0,
                cycle.getPendingItems()
                    .get(0));
            SimpleAssert.eq(1, countItems(s.player), "recovery hands off the preserved source once");
            SimpleAssert.eq(1, s.store.readGrantDelivered(ID), "successful handoff is checkpointed");
        });
    }

    private static void repeatedCancel() throws Exception {
        withSession(s -> {
            ReincarnationCycle cycle = new ReincarnationCycle(ID);
            cycle.deposit(Collections.singletonList(new ReincarnationCycle.ItemRef(ITEM, 0)));
            cycle.confirmReincarnation(42);
            s.store.save(cycle);
            byte[] before = Files.readAllBytes(s.base.resolve("config/gtit/reincarnation.dat"));
            HardcoreEnforcer.beginEnforcement(s.player);
            publish(s);
            s.enforcer.onServerTick(tick());
            s.handler.onServerTick(tick());
            s.player.manager.type = GameType.ADVENTURE;
            s.enforcer.onServerTick(tick());
            s.handler.onServerTick(tick());
            SimpleAssert.eq(GameType.ADVENTURE, s.player.manager.type, "repeated cancellation cannot restore twice");
            SimpleAssert.that(!HardcoreEnforcer.isActive(), "remains canceled");
            SimpleAssert.that(
                java.util.Arrays.equals(before, Files.readAllBytes(s.base.resolve("config/gtit/reincarnation.dat"))),
                "escrow and license unchanged");
            SimpleAssert.eq(0, s.player.attacks, "no delayed death");
        });
    }

    private static void changedMode() throws Exception {
        withSession(s -> {
            s.player.manager.type = GameType.CREATIVE;
            HardcoreEnforcer.beginEnforcement(s.player);
            s.enforcer.onServerTick(tick());
            s.player.manager.type = GameType.ADVENTURE;
            field(WorldInfo.class, "hardcore").setBoolean(s.world.getWorldInfo(), false);
            publish(s);
            s.enforcer.onServerTick(tick());
            SimpleAssert.eq(GameType.ADVENTURE, s.player.manager.type, "do not overwrite a later intentional mode");
            SimpleAssert.that(
                !s.world.getWorldInfo()
                    .isHardcoreModeEnabled(),
                "already reverted hardcore stays false");
        });
    }

    private static void crossWorldCancel() throws Exception {
        withSession(s -> {
            s.player.posY = 64;
            method(ReincarnationHandler.class, "beginAscension", EntityPlayerMP.class).invoke(s.handler, s.player);
            s.actions()
                .clear();
            field(ReincarnationHandler.class, "activeCarrier")
                .set(s.handler, ReincarnationDurabilityRegressionTest.allocate(EntityAscensionCarrier.class));
            field(ReincarnationHandler.class, "ascensionPlayer").set(s.handler, s.player);
            s.player.worldObj = ReincarnationDurabilityRegressionTest.world(s.base, "different-save");
            s.player.posY = 90;
            publish(s);
            s.handler.onServerTick(tick());
            SimpleAssert.that(s.player.posY == 90, "never teleport a different-world entity to old origin");
            SimpleAssert.eq(0, s.player.attacks, "different-world cancellation cannot fall through to kill");
            SimpleAssert
                .eq(null, field(ReincarnationHandler.class, "activeCarrier").get(s.handler), "carrier canceled");
        });
    }

    private static void invalidOrigin() throws Exception {
        withSession(s -> {
            s.player.posX = Double.NaN;
            s.player.posY = 64;
            method(ReincarnationHandler.class, "beginAscension", EntityPlayerMP.class).invoke(s.handler, s.player);
            s.actions()
                .clear();
            field(ReincarnationHandler.class, "activeCarrier")
                .set(s.handler, ReincarnationDurabilityRegressionTest.allocate(EntityAscensionCarrier.class));
            field(ReincarnationHandler.class, "ascensionPlayer").set(s.handler, s.player);
            s.player.posX = 12;
            s.player.posY = 90;
            publish(s);
            s.handler.onServerTick(tick());
            SimpleAssert.that(s.player.posX == 12 && s.player.posY == 90, "invalid origin must not be teleported to");
            SimpleAssert.eq(0, s.player.attacks, "invalid-origin cancellation cannot fall through to kill");
            SimpleAssert
                .eq(null, field(ReincarnationHandler.class, "activeCarrier").get(s.handler), "carrier canceled");
        });
    }

    private static void sharedShutdown() throws Exception {
        withSession(s -> {
            HardcoreEnforcer.beginEnforcement(s.player);
            field(HardcoreEnforcer.class, "deleteCountdownTicks").setInt(s.enforcer, 1);
            field(HardcoreEnforcer.class, "pendingDeleteDir").set(
                null,
                s.world.getSaveHandler()
                    .getWorldDirectory());
            publish(s);
            ReincarnationHandler.onServerStopping();
            HardcoreEnforcer.onServerStopping();
            SimpleAssert.eq(
                null,
                field(HardcoreEnforcer.class, "pendingDeleteDir").get(null),
                "publication followed by immediate stop cannot leave a deletion target for Stopped");
            SimpleAssert.that(
                !s.world.getWorldInfo()
                    .isHardcoreModeEnabled(),
                "restore override before world save");
        });
    }

    private static void unappliedMode() throws Exception {
        withSession(s -> {
            s.player.manager.type = GameType.CREATIVE;
            HardcoreEnforcer.beginEnforcement(s.player);
            // Another actor changed the mode before this enforcer's first forcing tick.
            s.player.manager.type = GameType.SURVIVAL;
            publish(s);
            s.enforcer.onServerTick(tick());
            SimpleAssert.eq(GameType.SURVIVAL, s.player.manager.type, "do not restore a mode never applied by us");
            SimpleAssert.that(!HardcoreEnforcer.isActive(), "enforcement still cancels");
        });
    }

    private static int countItems(TestPlayer player) {
        int total = 0;
        for (ItemStack stack : player.inventory.mainInventory) if (stack != null) total += stack.stackSize;
        return total;
    }

    private static void publish(Session s) throws Exception {
        field(IntegratedServer.class, "isPublic").setBoolean(s.server, true);
        SimpleAssert.that(s.server.isSinglePlayer(), "native isSinglePlayer remains true after LAN publication");
        SimpleAssert.that(s.server.getPublic(), "native IntegratedServer reports shared session");
        SimpleAssert.that(!ReincarnationHandler.isStrictSinglePlayer(), "strict gate must reject same native server");
    }

    private static ReincarnationContainer container(Session s, boolean deposited) throws Exception {
        ReincarnationWorldData data = ReincarnationWorldData.get(s.world);
        boolean[] columns = new boolean[15];
        columns[0] = true;
        data.setUnlockedColumns(columns);
        if (deposited) data.setDepositedItems(Collections.singletonList(new ReincarnationCycle.ItemRef(ITEM, 0)));
        s.world.isRemote = true;
        ReincarnationContainer container = new ReincarnationContainer(s.player);
        s.world.isRemote = false;
        field(ReincarnationContainer.class, "server").setBoolean(container, true);
        field(ReincarnationContainer.class, "store").set(container, s.store);
        field(ReincarnationContainer.class, "worldData").set(container, data);
        ReincarnationCycle cycle = s.store.load(ID);
        data.applyTo(cycle);
        field(ReincarnationContainer.class, "cycle").set(container, cycle);
        method(ReincarnationContainer.class, "fillItemSlotsFromCycle").invoke(container);
        s.player.openContainer = container;
        return container;
    }

    private static final class Session {

        Path base;
        World world;
        IntegratedServer server;
        ReincarnationStore store;
        ReincarnationHandler handler;
        HardcoreEnforcer enforcer;
        TestPlayer player;

        List<?> actions() throws Exception {
            return (List<?>) field(ReincarnationHandler.class, "scheduledActions").get(handler);
        }
    }

    private static void withSession(SessionCase action) throws Exception {
        Session s = new Session();
        s.base = Files.createTempDirectory("gtit-failure-boundary-");
        Field serverField = field(MinecraftServer.class, "mcServer");
        Object oldServer = serverField.get(null);
        s.handler = (ReincarnationHandler) field(ReincarnationHandler.class, "INSTANCE").get(null);
        s.enforcer = (HardcoreEnforcer) field(HardcoreEnforcer.class, "INSTANCE").get(null);
        Field storeField = field(ReincarnationHandler.class, "store");
        Object oldStore = storeField.get(s.handler);
        try {
            ReincarnationHandler.onServerStopping();
            HardcoreEnforcer.onServerStopping();
            ServerTaskScheduler.clear();
            s.world = ReincarnationDurabilityRegressionTest.world(s.base, "save", BoundaryWorld.class);
            field(World.class, "worldInfo").set(s.world, new WorldInfo(new NBTTagCompound()));
            s.server = ReincarnationDurabilityRegressionTest.allocate(IntegratedServer.class);
            s.server.setServerOwner("fixture");
            s.server.worldServers = new WorldServer[] { (WorldServer) s.world };
            serverField.set(null, s.server);
            s.store = new ReincarnationStore(s.base.toFile());
            s.store.save(new ReincarnationCycle(ID));
            storeField.set(s.handler, s.store);
            s.player = ReincarnationDurabilityRegressionTest.allocate(TestPlayer.class);
            s.player.worldObj = s.world;
            s.player.inventory = new InventoryPlayer(s.player);
            s.player.manager = new TestManager(s.world);
            field(EntityPlayerMP.class, "theItemInWorldManager").set(s.player, s.player.manager);
            DataWatcher watcher = new DataWatcher(s.player);
            watcher.addObject(6, Float.valueOf(20));
            field(Entity.class, "dataWatcher").set(s.player, watcher);
            action.run(s);
        } finally {
            // Never invoke the real deletion callback on a failed assertion's armed fixture.
            field(HardcoreEnforcer.class, "pendingDeleteDir").set(null, null);
            HardcoreEnforcer.onServerStopping();
            ReincarnationHandler.onServerStopping();
            ServerTaskScheduler.clear();
            serverField.set(null, oldServer);
            storeField.set(s.handler, oldStore);
            ReincarnationWorldDeletion.deleteTree(s.base);
        }
    }

    /** Keep native entity construction while isolating chunk loading from lifecycle tests. */
    public static final class BoundaryWorld extends WorldServer {

        boolean rejectSpawn;
        Entity spawnAttempt;

        private BoundaryWorld() {
            super(null, null, "fixture", 0, null, null); // Allocated without starting a server.
        }

        @Override
        public boolean spawnEntityInWorld(Entity entity) {
            SimpleAssert.that(entity.worldObj == this, "carrier belongs to the fixture world");
            spawnAttempt = entity;
            return !rejectSpawn;
        }
    }

    public static final class TestPlayer extends EntityPlayerMP {

        int attacks;
        TestManager manager;

        private TestPlayer() {
            super(null, null, null, null);
        }

        @Override
        public UUID getUniqueID() {
            return UUID.fromString(ID);
        }

        @Override
        public String getCommandSenderName() {
            return "reincarnation-failure-boundary";
        }

        @Override
        public void addChatMessage(IChatComponent component) {}

        @Override
        public boolean attackEntityFrom(DamageSource source, float amount) {
            attacks++;
            isDead = true;
            return true;
        }

        @Override
        public void mountEntity(Entity entity) {
            ridingEntity = entity;
        }

        @Override
        public void setPositionAndUpdate(double x, double y, double z) {
            posX = x;
            posY = y;
            posZ = z;
        }
    }

    private static final class TestManager extends ItemInWorldManager {

        GameType type = GameType.SURVIVAL;

        TestManager(World world) {
            super(world);
        }

        @Override
        public GameType getGameType() {
            return type;
        }

        @Override
        public void setGameType(GameType type) {
            this.type = type;
        }
    }

    private static TickEvent.ServerTickEvent tick() {
        return new TickEvent.ServerTickEvent(TickEvent.Phase.END);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
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

    private interface SessionCase {

        void run(Session session) throws Exception;
    }
}
