package com.miaokatze.gtit.hologram;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/** Both handlers are common-side classes; the client installs its own queued receiver. */
public final class HologramNetwork {

    private static SimpleNetworkWrapper channel;
    private static volatile Consumer<NBTTagCompound> receiver;
    private static volatile Consumer<NBTTagCompound> interactionSender;
    private static final int MAX_BYTES = 1048576;
    private static final AtomicLong snapshotSequence = new AtomicLong();
    private static final Map<UUID, Outbox> outgoing = new LinkedHashMap<>();

    private HologramNetwork() {}

    public static void init() {
        if (channel != null) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("gtit_hologram");
        channel.registerMessage(ServerHandler.class, Action.class, 0, Side.SERVER);
        channel.registerMessage(ClientHandler.class, State.class, 1, Side.CLIENT);
        channel.registerMessage(FragmentHandler.class, Fragment.class, 2, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(new TransportTicks());
    }

    public static void setClientReceiver(Consumer<NBTTagCompound> value) {
        receiver = value;
    }

    public static void setClientInteractionSender(Consumer<NBTTagCompound> value) {
        interactionSender = value;
    }

    /** Invoked only on the logical client; common item code never links keyboard/client classes. */
    public static void clientUse(int x, int y, int z, int side, boolean air) {
        Consumer<NBTTagCompound> sender = interactionSender;
        if (sender == null) return;
        NBTTagCompound input = new NBTTagCompound();
        input.setInteger("x", x);
        input.setInteger("y", y);
        input.setInteger("z", z);
        input.setInteger("side", side);
        input.setBoolean("air", air);
        sender.accept(input);
    }

    public static void sendAction(NBTTagCompound tag) {
        if (channel != null) channel.sendToServer(new Action(tag));
    }

    public static void sendState(EntityPlayerMP player, NBTTagCompound tag) {
        if (channel == null) return;
        if (tag.getBoolean("capturePending")) return;
        try {
            byte[] bytes = HologramSnapshotCodec.compress(tag);
            if (bytes.length > HologramSnapshotAssembly.MAX_BYTES)
                throw new IllegalArgumentException("hologram snapshot byte budget");
            Outbox box = outgoing.get(player.getUniqueID());
            String session = tag.getString("session");
            if (box == null || box.player != player || !box.session.equals(session) || tag.getBoolean("cancelled")) {
                box = new Outbox(player, session);
                outgoing.put(player.getUniqueID(), box);
            }
            OutgoingSnapshot previous = box.snapshots.peekLast();
            boolean replace = previous != null && previous.canReplace(tag);
            long replacedBytes = replace ? previous.bytes.length : 0;
            if (box.pendingBytes - replacedBytes + bytes.length > HologramSnapshotAssembly.MAX_BYTES)
                throw new IllegalArgumentException("hologram queued snapshot byte budget");
            if (replace) {
                box.snapshots.removeLast();
                box.pendingBytes -= previous.bytes.length;
            }
            box.snapshots.addLast(new OutgoingSnapshot(snapshotSequence.incrementAndGet(), bytes, tag));
            box.pendingBytes += bytes.length;
        } catch (IOException | RuntimeException e) {
            com.miaokatze.gtit.main.GTInterestingThing.LOG.warn("Hologram snapshot could not be queued", e);
            NBTTagCompound failure = new NBTTagCompound();
            failure.setBoolean("transportError", true);
            failure.setString("session", tag.getString("session"));
            channel.sendTo(new State(failure), player);
        }
    }

    private static final class Outbox {

        final EntityPlayerMP player;
        final net.minecraft.world.World world;
        final int dimension;
        final String session;
        final Deque<OutgoingSnapshot> snapshots = new ArrayDeque<>();
        long pendingBytes;

        Outbox(EntityPlayerMP player, String session) {
            this.player = player;
            world = player.worldObj;
            dimension = player.dimension;
            this.session = session;
        }
    }

    private static final class OutgoingSnapshot {

        final long sequence;
        final byte[] bytes;
        final long uiSequence, batchSequence, revision;
        final String phase;
        final int job;
        final boolean opening;
        int offset, index;

        OutgoingSnapshot(long sequence, byte[] bytes, NBTTagCompound tag) {
            this.sequence = sequence;
            this.bytes = bytes;
            uiSequence = tag.getLong("uiSequence");
            batchSequence = tag.getLong("batchSeq");
            revision = tag.getLong("planRevision");
            phase = tag.getString("phase");
            job = tag.getInteger("job");
            opening = tag.getBoolean("open") || tag.getBoolean("hints") || tag.getBoolean("settingsOnly");
        }

        boolean canReplace(NBTTagCompound tag) {
            return offset == 0 && !opening
                && !tag.getBoolean("open")
                && !tag.getBoolean("hints")
                && !tag.getBoolean("settingsOnly")
                && uiSequence == tag.getLong("uiSequence")
                && batchSequence == tag.getLong("batchSeq")
                && revision == tag.getLong("planRevision")
                && phase.equals(tag.getString("phase"))
                && job == tag.getInteger("job");
        }
    }

    /** Main-thread FIFO: one bounded fragment per player and tick, including every ACK snapshot. */
    public static final class TransportTicks {

        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Iterator<Outbox> iterator = outgoing.values()
                .iterator();
            while (iterator.hasNext()) {
                Outbox box = iterator.next();
                if (box.player.worldObj != box.world || box.player.dimension != box.dimension
                    || box.player.playerNetServerHandler == null
                    || box.player.playerNetServerHandler.netManager == null
                    || !box.player.playerNetServerHandler.netManager.isChannelOpen()) {
                    iterator.remove();
                    continue;
                }
                OutgoingSnapshot snapshot = box.snapshots.peekFirst();
                if (snapshot == null) {
                    iterator.remove();
                    continue;
                }
                int end = Math.min(snapshot.bytes.length, snapshot.offset + HologramSnapshotAssembly.CHUNK_BYTES);
                channel.sendTo(
                    new Fragment(
                        snapshot.sequence,
                        snapshot.bytes.length,
                        snapshot.index++,
                        Arrays.copyOfRange(snapshot.bytes, snapshot.offset, end)),
                    box.player);
                snapshot.offset = end;
                if (end == snapshot.bytes.length) {
                    box.pendingBytes -= snapshot.bytes.length;
                    box.snapshots.removeFirst();
                    if (box.snapshots.isEmpty()) iterator.remove();
                }
            }
        }
    }

    /** Raw compressed slices stay below both the Forge packet and per-NBT limits. */
    public static final class Fragment implements IMessage {

        private long sequence;
        private int total, index;
        private byte[] bytes;

        public Fragment() {}

        Fragment(long sequence, int total, int index, byte[] bytes) {
            this.sequence = sequence;
            this.total = total;
            this.index = index;
            this.bytes = bytes;
        }

        public void fromBytes(ByteBuf buf) {
            if (buf.readableBytes() < 20) throw new IllegalArgumentException("hologram fragment header");
            sequence = buf.readLong();
            total = buf.readInt();
            index = buf.readInt();
            int length = buf.readInt();
            if (sequence < 0 || total <= 0
                || total > HologramSnapshotAssembly.MAX_BYTES
                || index < 0
                || index >= (total - 1) / HologramSnapshotAssembly.CHUNK_BYTES + 1
                || length <= 0
                || length > HologramSnapshotAssembly.CHUNK_BYTES
                || length != buf.readableBytes()) throw new IllegalArgumentException("hologram fragment size");
            bytes = new byte[length];
            buf.readBytes(bytes);
        }

        public void toBytes(ByteBuf buf) {
            buf.writeLong(sequence);
            buf.writeInt(total);
            buf.writeInt(index);
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    public static final class FragmentHandler implements IMessageHandler<Fragment, IMessage> {

        public IMessage onMessage(Fragment msg, MessageContext ctx) {
            Consumer<NBTTagCompound> current = receiver;
            if (current != null) {
                NBTTagCompound envelope = new NBTTagCompound();
                envelope.setBoolean("transportFragment", true);
                envelope.setLong("transportSequence", msg.sequence);
                envelope.setInteger("transportTotal", msg.total);
                envelope.setInteger("transportIndex", msg.index);
                envelope.setByteArray("transportBytes", msg.bytes);
                current.accept(envelope);
            }
            return null;
        }
    }

    public static class Packet implements IMessage {

        NBTTagCompound tag = new NBTTagCompound();

        public Packet() {}

        Packet(NBTTagCompound tag) {
            this.tag = (NBTTagCompound) tag.copy();
        }

        public void fromBytes(ByteBuf buf) {
            int length = buf.readInt();
            if (length < 0 || length > MAX_BYTES || length > buf.readableBytes())
                throw new IllegalArgumentException("hologram packet size");
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            try {
                tag = HologramSnapshotCodec.decode(bytes, 16 * MAX_BYTES, 16L * MAX_BYTES);
            } catch (IOException e) {
                throw new IllegalArgumentException("hologram NBT", e);
            }
        }

        public void toBytes(ByteBuf buf) {
            try {
                byte[] bytes = CompressedStreamTools.compress(tag);
                if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("hologram packet size");
                buf.writeInt(bytes.length);
                buf.writeBytes(bytes);
            } catch (IOException e) {
                throw new IllegalArgumentException("hologram NBT", e);
            }
        }
    }

    public static final class Action extends Packet {

        public Action() {}

        Action(NBTTagCompound tag) {
            super(tag);
        }
    }

    public static final class State extends Packet {

        public State() {}

        State(NBTTagCompound tag) {
            super(tag);
        }
    }

    public static final class ServerHandler implements IMessageHandler<Action, IMessage> {

        public IMessage onMessage(Action msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (msg.tag.toString()
                .length() <= 16384) ServerTaskScheduler.scheduleServerTask(() -> dispatch(player, msg.tag));
            return null;
        }
    }

    private static void dispatch(EntityPlayerMP player, NBTTagCompound tag) {
        String action = tag.getString("action");
        if ("useBlock".equals(action) || "useAir".equals(action)) {
            if (!com.miaokatze.gtit.common.items.hologram.ItemNekoHologramProjector.isProjector(player.getHeldItem()))
                return;
            boolean air = "useAir".equals(action);
            if (air && !tag.getBoolean("alt")) {
                if (tag.getBoolean("shift")) HologramService.openSettings(player);
                else HologramService.openAir(player);
                return;
            }
            int x = tag.getInteger("x"), y = tag.getInteger("y"), z = tag.getInteger("z");
            int side = tag.getInteger("side");
            if (side < 0 || side > 5
                || !player.worldObj.blockExists(x, y, z)
                || player.getDistanceSq(x + .5, y + .5, z + .5) > 64) return;
            if (tag.getBoolean("alt")) {
                net.minecraftforge.common.util.ForgeDirection face = net.minecraftforge.common.util.ForgeDirection
                    .getOrientation(side);
                int offsetX = air ? 0 : face.offsetX, offsetY = air ? 0 : face.offsetY,
                    offsetZ = air ? 0 : face.offsetZ;
                if (tag.getBoolean("shift"))
                    HologramService.immediateVirtual(player, x + offsetX, y + offsetY, z + offsetZ, side, "");
                else if ("lock".equals(tag.getString("virtualIntent")))
                    HologramService.lockVirtual(player, x, y, z, side);
                else if ("cancel".equals(tag.getString("virtualIntent"))) HologramService.cancel(player);
                else HologramService.openVirtual(player, x + offsetX, y + offsetY, z + offsetZ, side, "");
            } else if (tag.getBoolean("shift") && tag.getBoolean("virtualEdit")) HologramService.openAir(player);
            else HologramService.open(player, x, y, z, side);
            return;
        }
        if ("openSettings".equals(action)) {
            if (com.miaokatze.gtit.common.items.hologram.ItemNekoHologramProjector.isProjector(player.getHeldItem()))
                HologramService.openSettings(player);
            return;
        }
        HologramService.handle(player, tag);
    }

    public static final class ClientHandler implements IMessageHandler<State, IMessage> {

        public IMessage onMessage(State msg, MessageContext ctx) {
            Consumer<NBTTagCompound> current = receiver;
            if (current != null) current.accept(msg.tag);
            return null;
        }
    }
}
