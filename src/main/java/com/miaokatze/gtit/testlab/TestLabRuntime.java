package com.miaokatze.gtit.testlab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.world.WorldServer;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Cooperative single-run scheduler. World operations stay on the server thread; completed reports use a writer thread.
 */
public final class TestLabRuntime {

    public static final TestLabRuntime INSTANCE = new TestLabRuntime();
    private static final Map<String, ProbeScenario> SCENARIOS = new LinkedHashMap<>();
    private final Deque<ProbeScenario> remaining = new ArrayDeque<>();
    private final List<Double> tickSamples = new ArrayList<>();
    private TestLabReport report;
    private ProbeContext context;
    private ProbeTask task;
    private WorldServer world;
    private ExecutorService writer;
    private boolean resetting;
    private boolean refresh;
    private String refreshRequestId;
    private int taskTicks;
    private int tickCount;
    private long stepNanos;
    private long worstStepNanos;
    private long tickStarted;
    private long startNanos;
    private long cleanupNanos;
    private boolean executing;
    private boolean finishRequested;
    private net.minecraftforge.common.ForgeChunkManager.Ticket ticket;
    private final List<net.minecraft.entity.Entity> spawned = new ArrayList<>();

    private TestLabRuntime() {}

    public static void initialize(FMLPreInitializationEvent event) {
        TestLabConfig.load(event.getModConfigurationDirectory());
        if (!TestLabConfig.enabled) return;
        TestLabDimension.register();
        if (TestLabDimension.problem() != null) return;
        BasicProbeScenarios.register();
        com.miaokatze.gtit.testlab.scenario.NativeProbeScenarios.register();
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(INSTANCE);
        TestLabUiNetwork.initialize();
        net.minecraftforge.common.ForgeChunkManager
            .setForcedChunkLoadingCallback(GTInterestingThing.instance, (tickets, loadedWorld) -> {
                for (net.minecraftforge.common.ForgeChunkManager.Ticket stale : tickets)
                    net.minecraftforge.common.ForgeChunkManager.releaseTicket(stale);
            });
    }

    public static void register(ProbeScenario scenario) {
        if (scenario == null || !scenario.id()
            .matches("[a-z0-9][a-z0-9_-]{0,63}") || SCENARIOS.putIfAbsent(scenario.id(), scenario) != null)
            throw new IllegalArgumentException("Duplicate or invalid test scenario");
    }

    public static void serverStarting(FMLServerStartingEvent event) {
        if (TestLabConfig.enabled && TestLabDimension.problem() == null)
            event.registerServerCommand(new TestLabCommand());
    }

    public static List<String> scenarioIds() {
        return new ArrayList<>(SCENARIOS.keySet());
    }

    public String status() {
        return "enabled=" + TestLabConfig.enabled
            + " dimension="
            + TestLabConfig.dimensionId
            + " problem="
            + TestLabDimension.problem()
            + " active="
            + (report == null ? "none" : report.runId)
            + " remaining="
            + remaining.size()
            + " cleaning="
            + resetting
            + " scenarios="
            + SCENARIOS.keySet();
    }

    public String run(String mode, String id, int iterations) {
        return run(mode, id, iterations, null);
    }

    public String run(String mode, String id, int iterations, String requestId) {
        if (!TestLabConfig.enabled || TestLabDimension.problem() != null)
            throw new IllegalStateException("Test laboratory disabled or conflicted");
        if (report != null || resetting) throw new IllegalStateException("Laboratory busy; use status or reset");
        if (iterations < 1 || iterations > TestLabConfig.maxIterations)
            throw new IllegalArgumentException("Iteration limit: " + TestLabConfig.maxIterations);
        List<ProbeScenario> selected = new ArrayList<>();
        if (id != null) {
            ProbeScenario scenario = SCENARIOS.get(id);
            if (scenario == null) throw new IllegalArgumentException("Unknown scenario: " + id);
            selected.add(scenario);
        } else {
            for (ProbeScenario scenario : SCENARIOS.values())
                if (scenario.defaultEnabled() && scenario.stress() == "stress".equals(mode)) selected.add(scenario);
        }
        if (selected.isEmpty()) throw new IllegalArgumentException("No scenarios selected");
        report = new TestLabReport(mode);
        report.environment.put("requestId", requestId);
        report.environment.put("iterations", iterations);
        report.environment.put(
            "selectedScenarios",
            selected.stream()
                .map(ProbeScenario::id)
                .toArray(String[]::new));
        for (int i = 0; i < iterations; i++) remaining.addAll(selected);
        resetting = true;
        tickSamples.clear();
        tickCount = 0;
        stepNanos = worstStepNanos = startNanos = cleanupNanos = 0;
        GTInterestingThing.LOG.info(
            "[GTIT-LAB] START run={} mode={} tasks={} request={}",
            report.runId,
            mode,
            remaining.size(),
            requestId == null ? "-" : requestId);
        return report.runId;
    }

    public void reset(boolean runAfter) {
        reset(runAfter, null);
    }

    public void reset(boolean runAfter, String requestId) {
        if (report != null) {
            report.add("harness", "interrupted", "FAIL", "Explicit reset cancelled the active run");
            closeTask();
            remaining.clear();
        }
        refresh = runAfter;
        refreshRequestId = requestId;
        resetting = true;
    }

    @SubscribeEvent
    public void onEntityJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        TestLabUiNetwork.onEntityJoin(event);
        boolean markedDrop = event.entity instanceof net.minecraft.entity.item.EntityItem drop && report != null
            && TestLabUiNetwork.marked(drop.getEntityItem(), report.runId);
        if (context != null && (executing || markedDrop)
            && event.world == world
            && TestLabDimension.isLab(event.world)
            && !(event.entity instanceof net.minecraft.entity.player.EntityPlayer)
            && Math.abs(event.entity.posX - context.x(0)) <= 256
            && Math.abs(event.entity.posZ) <= 256) {
            event.entity.getEntityData()
                .setString("GTITTestLabRun", report.runId);
            spawned.add(event.entity);
        }
    }

    @SubscribeEvent
    public void onLogout(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        TestLabUiNetwork.onLogout(event.player);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStarted = System.nanoTime();
            return;
        }
        TestLabUiNetwork.tick();
        if (report == null && !resetting) return;
        boolean measured = report != null;
        try {
            world = TestLabDimension.world();
            pin(world, 0, 0);
            TestLabOwnership owned = TestLabOwnership.get(world);
            if (resetting) {
                long begin = System.nanoTime();
                try {
                    owned.clean(world, TestLabConfig.cleanupPerTick);
                } finally {
                    cleanupNanos += System.nanoTime() - begin;
                }
                if (owned.size() != 0) return;
                resetting = false;
                if (report == null) {
                    releaseTicket();
                    GTInterestingThing.LOG.info("[GTIT-LAB] RESET_COMPLETE");
                    if (refresh) {
                        refresh = false;
                        run("suite", null, 1, refreshRequestId);
                        refreshRequestId = null;
                    }
                    return;
                }
            }
            if (task == null) {
                if (remaining.isEmpty()) {
                    finishRequested = true;
                    return;
                }
                ProbeScenario scenario = remaining.removeFirst();
                context = new ProbeContext(world, report, scenario.id(), scenario.stress());
                taskTicks = 0;
                long begin = System.nanoTime();
                executing = true;
                try {
                    task = scenario.start(context);
                } finally {
                    executing = false;
                    startNanos += System.nanoTime() - begin;
                }
                if (task == null) throw new IllegalStateException("Scenario returned null task: " + scenario.id());
                return;
            }
            long start = System.nanoTime();
            boolean done;
            try {
                executing = true;
                done = task.tick();
            } finally {
                executing = false;
                long duration = System.nanoTime() - start;
                stepNanos += duration;
                worstStepNanos = Math.max(worstStepNanos, duration);
            }
            if (++taskTicks >= TestLabConfig.timeoutTicks && !done) {
                context.check("timeout", false, "Scenario exceeded " + TestLabConfig.timeoutTicks + " ticks");
                done = true;
            }
            if (done) {
                closeTask();
                resetting = true;
            }
        } catch (Exception | LinkageError error) {
            if (report != null) {
                report.add("harness", "exception", "FAIL", error.toString());
                closeTask();
                remaining.clear();
                if (resetting) finishRequested = true;
                else resetting = true;
            }
            if (report == null) resetting = false;
            GTInterestingThing.LOG.error("[GTIT-LAB] Operation failed", error);
        } finally {
            if (measured) {
                if (tickSamples.size() < 4096) tickSamples.add((System.nanoTime() - tickStarted) / 1_000_000.0);
                tickCount++;
            }
            if (finishRequested) {
                finishRequested = false;
                finish();
                resetting = false;
                releaseTicket();
                if (refresh) {
                    refresh = false;
                    if (world != null && TestLabOwnership.get(world)
                        .size() == 0) run("suite", null, 1, refreshRequestId);
                    else GTInterestingThing.LOG
                        .error("[GTIT-LAB] Refresh aborted because fixture cleanup is incomplete");
                    refreshRequestId = null;
                }
            }
        }
    }

    private void closeTask() {
        long begin = System.nanoTime();
        executing = true;
        if (task != null) {
            try {
                task.close();
            } catch (Exception | LinkageError error) {
                if (report != null) report.add("harness", "task-cleanup", "FAIL", error.toString());
            }
        }
        task = null;
        if (context != null) context.close();
        context = null;
        for (net.minecraft.entity.Entity entity : spawned) entity.setDead();
        spawned.clear();
        executing = false;
        cleanupNanos += System.nanoTime() - begin;
    }

    private void finish() {
        if (report == null) return;
        report.metrics.put("schedulerTicks", (double) tickCount);
        report.metrics.put("probeStepMillis", stepNanos / 1_000_000.0);
        report.metrics.put("worstProbeStepMillis", worstStepNanos / 1_000_000.0);
        report.metrics.put("scenarioStartMillis", startNanos / 1_000_000.0);
        report.metrics.put("cleanupMillis", cleanupNanos / 1_000_000.0);
        int ownedRemaining = world == null ? -1
            : TestLabOwnership.get(world)
                .size();
        report.environment.put("ownedBlocksRemaining", ownedRemaining);
        report.environment.put("ownedRemaining", ownedRemaining);
        report.environment.put("cleanupComplete", ownedRemaining == 0 && context == null && spawned.isEmpty());
        report.environment.put(
            "tickMeasurementScope",
            "ServerTick START through lab END handler, including probe start/steps/cleanup; first 4096 active ticks");
        if (ownedRemaining != 0) report.add(
            "harness",
            "owned-block-cleanup",
            "FAIL",
            "Cleanup pending; remaining=" + ownedRemaining + "; next reset retries only owned fixtures");
        if (!tickSamples.isEmpty()) {
            Collections.sort(tickSamples);
            report.metrics.put("serverTickP50Millis", tickSamples.get(tickSamples.size() / 2));
            report.metrics.put("serverTickP95Millis", tickSamples.get((tickSamples.size() - 1) * 95 / 100));
            report.metrics.put("serverTickMaxMillis", tickSamples.get(tickSamples.size() - 1));
            report.metrics.put("serverTickSamples", (double) tickSamples.size());
        }
        report.finish();
        if (writer == null || writer.isShutdown()) writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "GTIT-testlab-report");
            thread.setDaemon(true);
            return thread;
        });
        TestLabReport completed = report;
        report = null;
        writer.execute(completed::write);
    }

    public static void serverStopping() {
        if (!TestLabConfig.enabled) return;
        INSTANCE.closeTask();
        WorldServer loaded = net.minecraftforge.common.DimensionManager.getWorld(TestLabConfig.dimensionId);
        if (TestLabDimension.isLab(loaded) && (INSTANCE.report != null || INSTANCE.resetting)) {
            long begin = System.nanoTime();
            try {
                TestLabOwnership.get(loaded)
                    .clean(loaded, 4096);
            } catch (Exception | LinkageError error) {
                if (INSTANCE.report != null)
                    INSTANCE.report.add("harness", "shutdown-cleanup", "FAIL", error.toString());
                GTInterestingThing.LOG
                    .error("[GTIT-LAB] Shutdown cleanup incomplete; persistent manifest retained", error);
            } finally {
                INSTANCE.cleanupNanos += System.nanoTime() - begin;
            }
        }
        if (INSTANCE.report != null) {
            INSTANCE.report.add("harness", "server-stopped", "FAIL", "Interrupted run; never resumed as a success");
            INSTANCE.finish();
        }
        INSTANCE.remaining.clear();
        INSTANCE.world = null;
        INSTANCE.releaseTicket();
        INSTANCE.resetting = INSTANCE.refresh = false;
        INSTANCE.refreshRequestId = null;
        TestLabUiNetwork.stop();
        if (INSTANCE.writer != null) {
            INSTANCE.writer.shutdown();
            try {
                if (!INSTANCE.writer.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS))
                    GTInterestingThing.LOG.error("[GTIT-LAB] REPORT_FAILED shutdown timed out while flushing reports");
            } catch (InterruptedException error) {
                Thread.currentThread()
                    .interrupt();
                GTInterestingThing.LOG.error("[GTIT-LAB] REPORT_FAILED report flush interrupted", error);
            }
        }
    }

    static void pin(WorldServer world, int x, int z) {
        if (!TestLabDimension.isLab(world)) throw new IllegalArgumentException("Not the laboratory world");
        if (net.minecraftforge.common.DimensionManager.getWorld(TestLabConfig.dimensionId) != world)
            throw new IllegalStateException("Laboratory world is no longer registered");
        if (INSTANCE.ticket == null) {
            INSTANCE.ticket = net.minecraftforge.common.ForgeChunkManager.requestTicket(
                GTInterestingThing.instance,
                world,
                net.minecraftforge.common.ForgeChunkManager.Type.NORMAL);
            if (INSTANCE.ticket == null) throw new IllegalStateException("No GTIT laboratory chunk ticket available");
        }
        net.minecraft.world.ChunkCoordIntPair chunk = new net.minecraft.world.ChunkCoordIntPair(x >> 4, z >> 4);
        if (!INSTANCE.ticket.getChunkList()
            .contains(chunk)) {
            if (INSTANCE.ticket.getChunkList()
                .size() >= INSTANCE.ticket.getMaxChunkListDepth())
                throw new IllegalStateException("Laboratory chunk-ticket budget exceeded");
            net.minecraftforge.common.ForgeChunkManager.forceChunk(INSTANCE.ticket, chunk);
        }
        // A ticket only prevents unloading: Forge's forceChunk does not load a chunk. With no real players,
        // loadChunkOnProvideRequest may be false and World.setBlock would silently target EmptyChunk,
        // whose setter returns true even though subsequent reads always return air.
        net.minecraft.world.chunk.Chunk loaded = world.theChunkProviderServer.loadChunk(x >> 4, z >> 4);
        if (loaded == null || loaded instanceof net.minecraft.world.chunk.EmptyChunk
            || loaded.worldObj != world
            || loaded.xPosition != (x >> 4)
            || loaded.zPosition != (z >> 4)
            || world.theChunkProviderServer.provideChunk(x >> 4, z >> 4) != loaded)
            throw new IllegalStateException(
                "Laboratory fixture chunk did not synchronously load at " + (x >> 4) + "," + (z >> 4));
    }

    private void releaseTicket() {
        if (ticket != null) net.minecraftforge.common.ForgeChunkManager.releaseTicket(ticket);
        ticket = null;
    }
}
