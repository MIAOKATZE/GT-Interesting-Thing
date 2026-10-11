package com.miaokatze.gtit.client.hologram;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructableProvider;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.miaokatze.gtit.hologram.HologramNetwork;
import com.miaokatze.gtit.hologram.HologramSnapshotAssembly;
import com.miaokatze.gtit.hologram.HologramSnapshotCodec;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Registered only by ClientProxy; the common network receiver never links client classes. */
public final class HologramClient {

    private static boolean installed;
    static HologramState state;
    private static net.minecraft.world.World snapshotWorld;
    static boolean worldPreview = true;
    private static long receivedAt;
    private static long acceptedBatch = -1;
    private static final Set<String> retiredSessions = new LinkedHashSet<>();
    private static final long SNAPSHOT_TTL_NS = 300000000000L;
    private static HologramSnapshotAssembly assembly = new HologramSnapshotAssembly();
    private static net.minecraft.world.World assemblyWorld;

    private HologramClient() {}

    public static void install() {
        if (installed) return;
        installed = true;
        HologramNetwork.setClientReceiver(
            nbt -> Minecraft.getMinecraft()
                .func_152344_a(() -> receive(nbt)));
        HologramNetwork.setClientInteractionSender(HologramClient::use);
        MinecraftForge.EVENT_BUS.register(new HologramClient());
        HologramClientSmoke.install();
    }

    private static long lastBlockUseTick = Long.MIN_VALUE;

    private static void use(NBTTagCompound input) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;
        boolean air = input.getBoolean("air");
        long tick = mc.theWorld.getTotalWorldTime();
        if (air && (tick == lastBlockUseTick || mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)) return;
        if (!air) lastBlockUseTick = tick;
        boolean alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        input.setString("action", air ? "useAir" : "useBlock");
        input.setBoolean("alt", alt);
        input.setBoolean("shift", shift);
        if (air && alt) {
            int[] anchor = state == null ? new int[] {
                net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posX + mc.thePlayer.getLookVec().xCoord * 3),
                net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.getLookVec().yCoord * 3),
                net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posZ + mc.thePlayer.getLookVec().zCoord * 3) }
                : movingAnchor(state);
            input.setInteger("x", anchor[0]);
            input.setInteger("y", anchor[1]);
            input.setInteger("z", anchor[2]);
            input.setInteger("side", state == null ? 2 : state.data.getInteger("side"));
            input.setBoolean("anchorPosition", true);
        }
        input.setBoolean(
            "virtualEdit",
            shift && !alt && state != null && state.data.getBoolean("virtual") && state.data.getInteger("job") != 4);
        if (alt && !shift
            && state != null
            && state.data.getBoolean("virtual")
            && state.data.getInteger("job") != 4
            && !state.cells.isEmpty()) {
            input.setString("virtualIntent", state.data.getBoolean("virtualLocked") ? "cancel" : "lock");
            if (!state.data.getBoolean("virtualLocked")) {
                int[] anchor = movingAnchor(state);
                input.setInteger("x", anchor[0]);
                input.setInteger("y", anchor[1]);
                input.setInteger("z", anchor[2]);
                input.setBoolean("anchorPosition", true);
            }
        }
        HologramNetwork.sendAction(input);
    }

    public static void openSettings() {
        NBTTagCompound request = new NBTTagCompound();
        request.setString("action", "openSettings");
        HologramNetwork.sendAction(request);
    }

    /** Only a render translation; no moving-preview world mutation packets are emitted. */
    static int[] movingAnchor(HologramState snapshot) {
        Minecraft mc = Minecraft.getMinecraft();
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            ForgeDirection face = ForgeDirection.getOrientation(hit.sideHit);
            return new int[] { hit.blockX + face.offsetX, hit.blockY + face.offsetY, hit.blockZ + face.offsetZ };
        }
        return new int[] { snapshot.data.getInteger("x"), snapshot.data.getInteger("y"),
            snapshot.data.getInteger("z") };
    }

    private static void receive(NBTTagCompound nbt) {
        if (nbt.getBoolean("transportError")) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) mc.thePlayer.addChatMessage(
                new net.minecraft.util.ChatComponentText(
                    text(
                        "transportError",
                        "Hologram snapshot exceeds the transfer budget; the previous preview was retained.")));
            return;
        }
        if (nbt.getBoolean("transportFragment")) {
            net.minecraft.world.World world = Minecraft.getMinecraft().theWorld;
            if (world == null) return;
            if (world != assemblyWorld) {
                assembly = new HologramSnapshotAssembly();
                assemblyWorld = world;
            }
            byte[] complete = assembly.accept(
                nbt.getLong("transportSequence"),
                nbt.getInteger("transportTotal"),
                nbt.getInteger("transportIndex"),
                nbt.getByteArray("transportBytes"),
                System.nanoTime());
            if (complete == null) return;
            try {
                nbt = HologramSnapshotCodec.decode(complete);
            } catch (IOException | RuntimeException e) {
                com.miaokatze.gtit.main.GTInterestingThing.LOG.warn("Hologram snapshot decoding failed", e);
                return;
            }
        }
        if (nbt.getBoolean("cancelled")) {
            if (state != null && state.session.equals(nbt.getString("session"))) clearProjection();
            return;
        }
        // Collection progress is not a complete editable structure; retain the last complete snapshot.
        if (nbt.getBoolean("capturePending")) return;
        HologramState next = new HologramState(nbt);
        String generation = next.data.getString("generation");
        if ((!generation.isEmpty() && !generation.equals(next.session)) || retiredSessions.contains(next.session))
            return;
        boolean same = state != null && state.session.equals(next.session);
        if (same && next.data.getLong("planRevision") < state.data.getLong("planRevision")) return;
        if (same && next.data.getLong("planRevision") != state.data.getLong("planRevision")) {
            HologramRenderer.release();
        }
        long batch = next.data.getLong("batchSeq");
        if (same && (batch < acceptedBatch || (batch == acceptedBatch && "ACK".equals(state.data.getString("phase"))
            && "PREPARE".equals(next.data.getString("phase"))))) return;
        if (!same) {
            if (state != null) retire(state.session);
            HologramRenderer.release();
            acceptedBatch = -1;
        }
        long now = System.nanoTime();
        acceptedBatch = batch;
        receivedAt = now;
        HologramRenderer.release();
        state = next;
        Minecraft mc = Minecraft.getMinecraft();
        snapshotWorld = mc.theWorld;
        if (nbt.getBoolean("hints")) hints(next);
        if (nbt.getBoolean("settingsOnly")) {
            if (mc.currentScreen instanceof HologramSettingsScreen && same)
                ((HologramSettingsScreen) mc.currentScreen).updateState(next);
            else mc.displayGuiScreen(new HologramSettingsScreen(next));
        } else if (nbt.getBoolean("virtual") && nbt.getBoolean("moving") && !nbt.getBoolean("open")) {
            if (mc.currentScreen instanceof HologramScreen) mc.displayGuiScreen(null);
        } else if (mc.currentScreen instanceof HologramScreen && same) {
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

    private static void retire(String session) {
        retiredSessions.add(session);
        if (retiredSessions.size() > 32) retiredSessions.remove(
            retiredSessions.iterator()
                .next());
    }

    static void clearProjection() {
        assembly.clear();
        if (state != null) retire(state.session);
        HologramRenderer.release();
        state = null;
        snapshotWorld = null;
        acceptedBatch = -1;
    }

    static String text(String key, String fallback) {
        String full = "gtit.hologram." + key;
        return StatCollector.canTranslate(full) ? StatCollector.translateToLocal(full) : fallback;
    }

    @SubscribeEvent
    public void renderWorld(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        assembly.expire(System.nanoTime());
        if (mc.theWorld != assemblyWorld) {
            assembly = new HologramSnapshotAssembly();
            assemblyWorld = mc.theWorld;
        }
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
        if (state.distanceSquared(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ) > 4096
            && !(state.data.getBoolean("virtual") && state.data.getBoolean("moving"))) return;
        HologramRenderer.world(state, event.partialTicks);
    }
}
