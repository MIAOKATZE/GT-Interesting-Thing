package com.miaokatze.gtit.client.hologram;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructableProvider;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.miaokatze.gtit.hologram.HologramNetwork;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Registered only by ClientProxy; the common network receiver never links client classes. */
public final class HologramClient {

    private static boolean installed;
    static HologramState state;
    private static net.minecraft.world.World snapshotWorld;
    static boolean worldPreview = true;
    static final Map<Integer, Long> arrivals = new HashMap<>();
    private static long receivedAt, preparedAt, preparedDuration;
    private static long acceptedBatch = -1;
    private static final Set<String> retiredSessions = new LinkedHashSet<>();
    private static final long SNAPSHOT_TTL_NS = 300000000000L;

    private HologramClient() {}

    public static void install() {
        if (installed) return;
        installed = true;
        HologramNetwork.setClientReceiver(
            nbt -> Minecraft.getMinecraft()
                .func_152344_a(() -> receive(nbt)));
        MinecraftForge.EVENT_BUS.register(new HologramClient());
        HologramClientSmoke.install();
    }

    private static void receive(NBTTagCompound nbt) {
        HologramState next = new HologramState(nbt);
        String generation = next.data.getString("generation");
        if ((!generation.isEmpty() && !generation.equals(next.session)) || retiredSessions.contains(next.session))
            return;
        boolean same = state != null && state.session.equals(next.session);
        if (same && next.data.getLong("planRevision") < state.data.getLong("planRevision")) return;
        if (same && next.data.getLong("planRevision") != state.data.getLong("planRevision")) {
            arrivals.clear();
            preparedAt = preparedDuration = 0;
        }
        long batch = next.data.getLong("batchSeq");
        if (same && (batch < acceptedBatch || (batch == acceptedBatch && "ACK".equals(state.data.getString("phase"))
            && "PREPARE".equals(next.data.getString("phase"))))) return;
        if (!same) {
            if (state != null) retire(state.session);
            arrivals.clear();
            acceptedBatch = -1;
        }
        long now = System.nanoTime();
        if ("PREPARE".equals(next.data.getString("phase")) && (batch != acceptedBatch || !same)) {
            preparedAt = now;
            long ticks = next.data.hasKey("layerDuration") ? next.data.getInteger("layerDuration")
                : next.data.getLong("due") - next.data.getLong("serverTick");
            ticks = Math.max(1, Math.min(40, ticks));
            preparedDuration = ticks * 50000000L;
        }
        if (next.data.hasKey("layerDuration")
            && (next.data.getInteger("job") == 1 || next.data.getInteger("job") == 2)) {
            preparedAt = now - (next.data.getLong("serverTick") - next.data.getLong("due")) * 50000000L;
            preparedDuration = Math.max(1, next.data.getInteger("layerDuration")) * 50000000L;
        }
        if ("ACK".equals(next.data.getString("phase")) && next.data.getBoolean("ackSuccess")
            && (!same || batch != acceptedBatch || !"ACK".equals(state.data.getString("phase")))) {
            for (int index : next.data.getIntArray("ack")) {
                if (index >= 0 && index < next.cells.size()
                    && next.cells.get(index)
                        .completed())
                    arrivals.put(index, now);
            }
        }
        if (next.data.getInteger("job") == 4) arrivals.clear();
        int[] ackIndices = next.data.getIntArray("ackIndices"), ackSuccesses = next.data.getIntArray("ackSuccesses");
        if ("ACK".equals(next.data.getString("phase"))
            && (!same || batch != acceptedBatch || !"ACK".equals(state.data.getString("phase")))) {
            for (int i = 0; i < ackIndices.length && i < ackSuccesses.length; i++) {
                int index = ackIndices[i];
                if (ackSuccesses[i] != 0 && index >= 0
                    && index < next.cells.size()
                    && next.cells.get(index)
                        .completed())
                    arrivals.put(index, now);
            }
        }
        acceptedBatch = batch;
        receivedAt = now;
        arrivals.entrySet()
            .removeIf(entry -> now - entry.getValue() > 1250000000L);
        state = next;
        Minecraft mc = Minecraft.getMinecraft();
        snapshotWorld = mc.theWorld;
        if (nbt.getBoolean("hints")) hints(next);
        if (mc.currentScreen instanceof HologramScreen && same) {
            ((HologramScreen) mc.currentScreen).updateState(next);
        } else if (!same || nbt.getBoolean("open")) {
            mc.displayGuiScreen(new HologramScreen(next));
        }
    }

    private static void hints(HologramState snapshot) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null
            || mc.thePlayer.dimension != snapshot.data.getInteger("dimension")) return;
        TileEntity tile = mc.theWorld
            .getTileEntity(snapshot.data.getInteger("x"), snapshot.data.getInteger("y"), snapshot.data.getInteger("z"));
        IConstructable target = null;
        if (tile instanceof IConstructableProvider) target = ((IConstructableProvider) tile).getConstructable();
        else if (tile instanceof IConstructable) target = (IConstructable) tile;
        else if (tile != null && IMultiblockInfoContainer.contains(tile.getClass())) {
            ExtendedFacing facing = tile instanceof IAlignment ? ((IAlignment) tile).getExtendedFacing()
                : ExtendedFacing.of(ForgeDirection.getOrientation(snapshot.data.getInteger("side")));
            target = IMultiblockInfoContainer.<TileEntity>get(tile.getClass())
                .toConstructable(tile, facing);
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (target == null || held == null) return;
        ItemStack trigger = held.copy();
        trigger.stackSize = snapshot.data.getInteger("main");
        NBTTagCompound channels = snapshot.data.getCompoundTag("channels");
        for (Object key : channels.func_150296_c())
            ChannelDataAccessor.setChannelData(trigger, key.toString(), channels.getInteger(key.toString()));
        // hintsOnly=true is the native client hint path; this never requests a construction operation.
        StructureLibAPI.startHinting(mc.theWorld);
        try {
            target.construct(trigger, true);
        } catch (RuntimeException e) {
            com.miaokatze.gtit.main.GTInterestingThing.LOG.warn("Hologram native hints failed", e);
        } finally {
            StructureLibAPI.endHinting(mc.theWorld);
        }
    }

    static double fall(HologramState.Cell cell) {
        if (state == null || !animationPhase()) return 0;
        int[] pending = state.data.getIntArray("pending");
        int offset = -1;
        for (int i = 0; i < pending.length; i++) if (pending[i] == cell.index) {
            offset = i;
            break;
        }
        if (offset < 0) return 0;
        long age = state.data.getInteger("job") == 2
            ? ((long) state.data.getInteger("layerElapsed") - state.data.getInteger("layerLeadRemaining")) * 50000000L
            : System.nanoTime() - preparedAt;
        if (age > preparedDuration + 1500000000L) return 0;
        double p;
        if (state.data.hasKey("layerDuration")) {
            int total = Math.max(pending.length, state.data.getInteger("layerTotal"));
            int order = Math.max(0, total - pending.length + offset);
            int duration = Math.max(1, state.data.getInteger("layerDuration"));
            int commitElapsed = order * duration / Math.max(1, total);
            // Mirror the server's ceil quota ordering. Each block descends in the eight ticks
            // before its estimated commit; only a successful ACK marks it as completed.
            p = (age / 50000000.0 - (commitElapsed - 8)) / 8;
        } else p = age / (double) Math.max(1, preparedDuration);
        p = Math.max(0, Math.min(1, p));
        return 3 * (1 - Math.sin(p * Math.PI / 2));
    }

    static boolean assembling(HologramState.Cell cell) {
        if (state == null || !animationPhase() || prepareExpired() || cell.completed()) return false;
        for (int index : state.data.getIntArray("pending")) if (index == cell.index) return true;
        return false;
    }

    static boolean prepareExpired() {
        return state != null && state.data.getInteger("job") != 2
            && animationPhase()
            && System.nanoTime() - preparedAt > preparedDuration + 1500000000L;
    }

    private static boolean animationPhase() {
        return "PREPARE".equals(state.data.getString("phase"))
            || state.data.hasKey("layerDuration") && "ACK".equals(state.data.getString("phase"))
                && (state.data.getInteger("job") == 1 || state.data.getInteger("job") == 2);
    }

    private static void retire(String session) {
        retiredSessions.add(session);
        if (retiredSessions.size() > 32) retiredSessions.remove(
            retiredSessions.iterator()
                .next());
    }

    static void clearProjection() {
        if (state != null) retire(state.session);
        state = null;
        snapshotWorld = null;
        arrivals.clear();
        acceptedBatch = -1;
        preparedAt = 0;
    }

    static String text(String key, String fallback) {
        String full = "gtit.hologram." + key;
        return StatCollector.canTranslate(full) ? StatCollector.translateToLocal(full) : fallback;
    }

    @SubscribeEvent
    public void renderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (state == null) return;
        if (mc.theWorld == null || mc.theWorld != snapshotWorld
            || mc.thePlayer == null
            || mc.thePlayer.dimension != state.data.getInteger("dimension")
            || System.nanoTime() - receivedAt > SNAPSHOT_TTL_NS) {
            clearProjection();
            return;
        }
        if (!worldPreview || state.data.getInteger("job") == 4) return;
        // Avoid stale projection after disconnect/reconnect or travelling away from the target.
        if (mc.thePlayer
            .getDistanceSq(state.data.getInteger("x"), state.data.getInteger("y"), state.data.getInteger("z")) > 4096)
            return;
        HologramRenderer.world(state, event.partialTicks);
    }
}
