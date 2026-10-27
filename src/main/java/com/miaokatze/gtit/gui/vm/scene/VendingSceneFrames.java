package com.miaokatze.gtit.gui.vm.scene;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBounds;

/** Bounded, atomic authoritative-frame decoder. No inventory, world or simulation is owned here. */
public final class VendingSceneFrames {

    public static final int MAX_BYTES = 65536;
    public static final String LAYOUT = "neko-room-v1";
    public static final SceneBounds BOUNDS = new SceneBounds(-5, 0, -3, 5, 8, 3);

    public static final class Entry {

        public final SceneBody pose;
        public final long dropId, batchId, generation, seed, phaseAgeMs;
        public final int quantity, phase, deliveryStage, deliveryAgeTicks, route;
        public final int[] slots;
        public final boolean rear;

        Entry(NBTTagCompound n) {
            dropId = n.getLong("dropId");
            batchId = n.getLong("batchId");
            generation = n.getLong("generation");
            seed = n.getLong("seed");
            phaseAgeMs = n.getLong("phaseAgeMs");
            if (phaseAgeMs < 0 || phaseAgeMs > 86400000) throw new IllegalArgumentException("phase clock");
            quantity = n.getInteger("quantity");
            phase = n.getInteger("phase");
            rear = n.getBoolean("rear");
            deliveryStage = n.hasKey("deliveryStage") ? n.getInteger("deliveryStage") : 2;
            deliveryAgeTicks = n.getInteger("deliveryAgeTicks");
            route = n.hasKey("route") ? n.getInteger("route") : rear ? 2 : 0;
            if (deliveryStage < 0 || deliveryStage > 2
                || deliveryAgeTicks < 0
                || deliveryAgeTicks > 1728000
                || route < 0
                || route > 2
                || rear != (route == 2)) throw new IllegalArgumentException("scene delivery stage/route");
            slots = n.getIntArray("slots");
            if (dropId <= 0 || batchId < 0
                || generation <= 0
                || quantity <= 0
                || phase < 0
                || phase > 2
                || slots.length < 1
                || slots.length > 100) throw new IllegalArgumentException("scene identity/quantity");
            Set<Integer> unique = new HashSet<>();
            for (int slot : slots)
                if (slot < 0 || slot >= 100 || !unique.add(slot)) throw new IllegalArgumentException("scene slots");
            if (!Long.toString(dropId)
                .equals(n.getString("id"))) throw new IllegalArgumentException("scene id mismatch");
            pose = new SceneBody(
                n.getString("id"),
                n.getDouble("hx"),
                n.getDouble("hy"),
                n.getDouble("hz"),
                n.getDouble("x"),
                n.getDouble("y"),
                n.getDouble("z"),
                n.getDouble("qx"),
                n.getDouble("qy"),
                n.getDouble("qz"),
                n.getDouble("qw"),
                n.getDouble("vx"),
                n.getDouble("vy"),
                n.getDouble("vz"),
                n.getDouble("wx"),
                n.getDouble("wy"),
                n.getDouble("wz"),
                n.getBoolean("sleeping"),
                n.getInteger("quietTicks"));
            BOUNDS.validate(pose);
        }
    }

    public static final class Frame {

        public final long revision, tick;
        public final boolean full, active;
        public final double doorLeft, doorRight, doorRear;
        public final Map<String, Entry> entries;
        public final Set<String> removed;

        Frame(NBTTagCompound n) {
            if (n.getInteger("schema") != 1 || !LAYOUT.equals(n.getString("layout")))
                throw new IllegalArgumentException("scene schema/layout");
            revision = n.getLong("revision");
            tick = n.getLong("tick");
            full = n.getBoolean("full");
            active = n.getBoolean("active");
            doorLeft = openness(n, "doorLeft");
            doorRight = openness(n, "doorRight");
            doorRear = openness(n, "doorRear");
            if (revision < 0 || tick < 0) throw new IllegalArgumentException("scene revision");
            NBTTagList list = n.getTagList("bodies", 10), deletes = n.getTagList("removed", 10);
            if (list.tagCount() > 100 || deletes.tagCount() > 100) throw new IllegalArgumentException("scene capacity");
            Map<String, Entry> map = new LinkedHashMap<>();
            Set<String> gone = new LinkedHashSet<>();
            for (int i = 0; i < list.tagCount(); i++) {
                Entry e = new Entry(list.getCompoundTagAt(i));
                if (map.put(e.pose.id, e) != null) throw new IllegalArgumentException("duplicate body");
            }
            for (int i = 0; i < deletes.tagCount(); i++) {
                long id = deletes.getCompoundTagAt(i)
                    .getLong("dropId");
                if (id <= 0 || !gone.add(Long.toString(id)) || map.containsKey(Long.toString(id)))
                    throw new IllegalArgumentException("invalid removal");
            }
            if (full && !gone.isEmpty()) throw new IllegalArgumentException("full frame with removals");
            entries = Collections.unmodifiableMap(map);
            removed = Collections.unmodifiableSet(gone);
        }
    }

    private static double openness(NBTTagCompound n, String key) {
        double value = n.getDouble(key);
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("door openness");
        return value;
    }

    public static byte[] encode(NBTTagCompound tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        if (bytes.size() > MAX_BYTES) throw new IOException("scene frame exceeds 64 KiB");
        return bytes.toByteArray();
    }

    public static Frame decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 1 || bytes.length > MAX_BYTES) throw new IOException("scene frame size");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            NBTTagCompound tag = CompressedStreamTools.func_152456_a(in, new NBTSizeTracker(262144));
            if (in.available() != 0) throw new IOException("scene trailing bytes");
            return new Frame(tag);
        } catch (RuntimeException e) {
            throw new IOException("invalid scene frame", e);
        }
    }

    private VendingSceneFrames() {}
}
