package com.miaokatze.gtit.reincarnation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.integrated.IntegratedPlayerList;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;

import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;
import com.miaokatze.gtit.reincarnation.core.ReincarnationWorldDeletion;
import com.miaokatze.gtit.reincarnation.gui.ReincarnationContainer;
import com.miaokatze.gtit.reincarnation.handler.ReincarnationHandler;
import com.miaokatze.gtit.reincarnation.storage.ReincarnationWorldData;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;
import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Actual login/respawn and slot callbacks, using real MC APIs without launching a server. */
public final class ReincarnationResumeRegressionTest {
    private static final String ITEM = "gtit:reincarnation_resume_regression";

    public static void main(String[] args) {
        Item.itemRegistry.addObject(31002, ITEM, new Item());
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("firstLoginCommitsEligibilityBeforeDelay", () -> checked(() -> loginCommit()));
        cases.put("disconnectBeforeDelayRetainsEligibility", () -> checked(() -> disconnectResume()));
        cases.put("deathBeforeDelayResumesOnRespawn", () -> checked(() -> deathResume()));
        cases.put("ordinaryRespawnDoesNotCreateEligibility", () -> checked(() -> ordinaryRespawn()));
        cases.put("legacyLoginMigratesBeforeGrantWrites", () -> checked(() -> legacyLogin()));
        cases.put("closeFlushesAcceptedDepositExactlyOnce", () -> checked(() -> depositClose()));
        cases.put("closeFlushesAcceptedWithdrawalExactlyOnce", () -> checked(() -> withdrawClose()));
        cases.put("shutdownFlushesAcceptedTransferAfterSchedulerClear", () -> checked(() -> stopFlush()));
        cases.put("checkpointFailureDoesNotScheduleDelivery", () -> checked(() -> admissionFailure()));
        cases.put("repeatedLoginReplacesOldGrantStart", () -> checked(() -> repeatedLogin()));
        TestRunner.run(ReincarnationResumeRegressionTest.class, cases);
    }

    private static void loginCommit() throws Exception {
        withSession(s -> {
            s.handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(s.player));
            ReincarnationWorldData saved = ReincarnationDurabilityRegressionTest.reload(s.world);
            SimpleAssert.that(saved != null && saved.isGrantStarted(),
                "first login eligibility must be durable before the 100-tick animation delay");
            SimpleAssert.eq(1, s.actions().size(), "one delayed grant start");
        });
    }

    private static void disconnectResume() throws Exception {
        withSession(s -> {
            s.handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(s.player));
            s.handler.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(s.player));
            s.player.stats.ticks = 40;
            s.handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(s.player));
            SimpleAssert.eq(1, s.actions().size(), "nonzero play time must not strand an admitted grant");
        });
    }

    private static void deathResume() throws Exception {
        withSession(s -> {
            s.handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(s.player));
            s.player.isDead = true;
            s.handler.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
            SimpleAssert.that(s.actions().isEmpty(), "dead entity's callbacks removed");
            TestPlayer replacement = player(s.world, s.base);
            replacement.stats.ticks = 50;
            respawn(s.handler, replacement);
            SimpleAssert.eq(1, s.actions().size(), "respawn resumes durable eligibility without relog");
        });
    }

    private static void ordinaryRespawn() throws Exception {
        withSession(s -> {
            respawn(s.handler, s.player);
            SimpleAssert.that(s.actions().isEmpty(), "respawn cannot make a fresh claim solely from zero stats");
            SimpleAssert.that(!ReincarnationWorldData.get(s.world).isGrantStarted(), "unadmitted mailbox stays untouched");
        });
    }

    private static void legacyLogin() throws Exception {
        withSession(s -> {
            ReincarnationDurabilityRegressionTest.legacy(s.base, true);
            s.handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(s.player));
            SimpleAssert.eq(null, s.store.readLegacyV1(ReincarnationDurabilityRegressionTest.ID),
                "login migrates before any grant marker can replace v1");
            SimpleAssert.eq(9, ReincarnationWorldData.get(s.world).getHullProgress()[0], "legacy progress survives login");
        });
    }

    private static void depositClose() throws Exception {
        withSession(s -> {
            s.store.save(new ReincarnationCycle(ReincarnationDurabilityRegressionTest.ID));
            ReincarnationContainer first = container(s, false);
            Slot slot = first.getSlot(15);
            slot.putStack(new ItemStack((Item) Item.itemRegistry.getObject(ITEM), 1, 0));
            first.onContainerClosed(s.player);
            SimpleAssert.eq(1, ReincarnationWorldData.get(s.world).getDepositedItems().size(),
                "close checkpoints a deposit vanilla has already accepted, before next-tick drain");
            ReincarnationContainer second = container(s, false);
            second.onContainerClosed(s.player);
            drain();
            SimpleAssert.eq(1, ReincarnationWorldData.get(s.world).getDepositedItems().size(),
                "reopened session cannot erase deposit, and queued wrapper cannot repeat it");
        });
    }

    private static void withdrawClose() throws Exception {
        withSession(s -> {
            s.store.save(new ReincarnationCycle(ReincarnationDurabilityRegressionTest.ID));
            ReincarnationContainer first = container(s, true);
            Slot slot = first.getSlot(15);
            ItemStack taken = slot.decrStackSize(1);
            slot.onPickupFromSlot(s.player, taken);
            first.onContainerClosed(s.player);
            SimpleAssert.that(ReincarnationWorldData.get(s.world).getDepositedItems().isEmpty(),
                "close checkpoints withdrawal vanilla has already transferred");
            ReincarnationContainer second = container(s, false);
            second.onContainerClosed(s.player);
            drain();
            SimpleAssert.that(ReincarnationWorldData.get(s.world).getDepositedItems().isEmpty(),
                "reopened session cannot resurrect the accepted withdrawal");
        });
    }

    private static void stopFlush() throws Exception {
        withSession(s -> {
            s.store.save(new ReincarnationCycle(ReincarnationDurabilityRegressionTest.ID));
            ReincarnationContainer container = container(s, false);
            container.getSlot(15).putStack(new ItemStack((Item) Item.itemRegistry.getObject(ITEM), 1, 0));
            IntegratedPlayerList players = ReincarnationDurabilityRegressionTest.allocate(IntegratedPlayerList.class);
            field(ServerConfigurationManager.class, "playerEntityList").set(players, Collections.singletonList(s.player));
            MinecraftServer.getServer().setConfigManager(players);
            s.player.isDead = true; // An accepted transfer is still owed to a dead/disconnected player.
            ServerTaskScheduler.clear(); // Matches the actual CommonProxy order.
            ReincarnationHandler.onServerStopping();
            ReincarnationWorldData saved = ReincarnationDurabilityRegressionTest.reload(s.world);
            SimpleAssert.that(saved != null, "shutdown flush writes a real receiver checkpoint");
            SimpleAssert.eq(1, saved.getDepositedItems().size(), "accepted transfer survives queue clear and stop");
            drain();
            SimpleAssert.eq(1, saved.getDepositedItems().size(), "no later wrapper repeats transfer");
        });
    }

    private static void admissionFailure() throws Exception {
        withSession(s -> {
            ReincarnationWorldData data = ReincarnationWorldData.get(s.world);
            data.setMigrated(true); // Isolate the admission write, not the preceding migration checkpoint.
            Files.createDirectory(s.world.getSaveHandler().getMapFileFromName(ReincarnationWorldData.DATA_NAME).toPath());
            s.handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(s.player));
            SimpleAssert.that(s.actions().isEmpty(), "unconfirmed eligibility must not schedule delivery");
            SimpleAssert.that(s.store.load(ReincarnationDurabilityRegressionTest.ID).canClaimGrant(), "mailbox retained");
        });
    }

    private static void repeatedLogin() throws Exception {
        withSession(s -> {
            s.handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(s.player));
            s.handler.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(s.player));
            SimpleAssert.eq(1, s.actions().size(), "repeated admission cannot launch parallel grant contexts");
        });
    }

    private static ReincarnationContainer container(Session s, boolean deposited) throws Exception {
        ReincarnationWorldData data = ReincarnationWorldData.get(s.world);
        boolean[] columns = new boolean[15]; columns[0] = true;
        data.setUnlockedColumns(columns);
        if (deposited) data.setDepositedItems(Collections.singletonList(new ReincarnationCycle.ItemRef(ITEM, 0)));
        // Client constructor builds the actual Slot/SessionInventory graph without touching global cwd config.
        s.world.isRemote = true;
        ReincarnationContainer container = new ReincarnationContainer(s.player);
        s.world.isRemote = false;
        field(ReincarnationContainer.class, "server").setBoolean(container, true);
        field(ReincarnationContainer.class, "store").set(container, s.store);
        field(ReincarnationContainer.class, "worldData").set(container, data);
        ReincarnationCycle cycle = s.store.load(ReincarnationDurabilityRegressionTest.ID);
        data.applyTo(cycle);
        field(ReincarnationContainer.class, "cycle").set(container, cycle);
        Method fill = ReincarnationContainer.class.getDeclaredMethod("fillItemSlotsFromCycle");
        fill.setAccessible(true); fill.invoke(container);
        s.player.openContainer = container;
        return container;
    }

    private static void respawn(ReincarnationHandler handler, TestPlayer player) throws Exception {
        ReincarnationHandler.class.getMethod("onPlayerRespawned", PlayerEvent.PlayerRespawnedEvent.class)
            .invoke(handler, new PlayerEvent.PlayerRespawnedEvent(player));
    }

    private static void drain() {
        ServerTaskScheduler.INSTANCE.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
    }

    private static final class Session {
        Path base;
        World world;
        ReincarnationStore store;
        ReincarnationHandler handler;
        TestPlayer player;
        List<?> actions() throws Exception { return (List<?>) field(ReincarnationHandler.class, "scheduledActions").get(handler); }
    }

    private static void withSession(SessionCase action) throws Exception {
        Session s = new Session();
        s.base = Files.createTempDirectory("gtit-resume-");
        Field serverField = field(MinecraftServer.class, "mcServer");
        Object oldServer = serverField.get(null);
        s.handler = (ReincarnationHandler) field(ReincarnationHandler.class, "INSTANCE").get(null);
        Field storeField = field(ReincarnationHandler.class, "store");
        Object oldStore = storeField.get(s.handler);
        try {
            ReincarnationHandler.onServerStopping();
            ServerTaskScheduler.clear();
            s.world = ReincarnationDurabilityRegressionTest.world(s.base, "save");
            field(World.class, "worldInfo").set(s.world, new WorldInfo(new NBTTagCompound()));
            IntegratedServer server = ReincarnationDurabilityRegressionTest.allocate(IntegratedServer.class);
            server.setServerOwner("fixture");
            server.worldServers = new WorldServer[] { (WorldServer) s.world };
            serverField.set(null, server);
            s.store = new ReincarnationStore(s.base.toFile());
            storeField.set(s.handler, s.store);
            ReincarnationCycle cycle = new ReincarnationCycle(ReincarnationDurabilityRegressionTest.ID);
            cycle.deposit(Collections.singletonList(new ReincarnationCycle.ItemRef(ITEM, 0)));
            cycle.confirmReincarnation(123456L); s.store.save(cycle);
            s.player = player(s.world, s.base);
            action.run(s);
        } finally {
            ReincarnationHandler.onServerStopping();
            ServerTaskScheduler.clear();
            serverField.set(null, oldServer);
            storeField.set(s.handler, oldStore);
            ReincarnationWorldDeletion.deleteTree(s.base);
        }
    }

    private static TestPlayer player(World world, Path base) throws Exception {
        TestPlayer player = ReincarnationDurabilityRegressionTest.allocate(TestPlayer.class);
        player.worldObj = world;
        player.inventory = new InventoryPlayer(player);
        player.stats = new TestStats(base.resolve("stats.json").toFile());
        return player;
    }

    public static final class TestPlayer extends EntityPlayerMP {
        TestStats stats;
        private TestPlayer() { super(null, null, null, null); }
        @Override public UUID getUniqueID() { return UUID.fromString(ReincarnationDurabilityRegressionTest.ID); }
        @Override public String getCommandSenderName() { return "reincarnation-resume-fixture"; }
        @Override public StatisticsFile func_147099_x() { return stats; }
        @Override public void addChatMessage(IChatComponent component) {}
    }

    private static final class TestStats extends StatisticsFile {
        int ticks;
        TestStats(java.io.File file) { super(null, file); }
        @Override public int writeStat(StatBase stat) { return ticks; }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        return ReincarnationDurabilityRegressionTest.field(owner, name);
    }
    private static void checked(ReincarnationDurabilityRegressionTest.Checked action) {
        ReincarnationDurabilityRegressionTest.checked(action);
    }
    private interface SessionCase { void run(Session session) throws Exception; }
}
