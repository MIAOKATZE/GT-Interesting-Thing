package com.miaokatze.gtit.client.vending.scene;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames;
import com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.Entry;
import com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.Frame;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBounds;
import com.miaokatze.miaokatzecore.api.miaovision.SceneSnapshot;

/** Client-only interpolation/selection. All stacks are resolved from the SAME 100 synchronized slots. */
public final class VendingSceneView implements AutoCloseable {

    private final List<ItemSlot> slots;
    private Map<String, Entry> entries = new LinkedHashMap<>();
    private Map<String, SceneBody> previous = new HashMap<>();
    private final Map<String, Long> fadeStart = new HashMap<>();
    private final List<Particle> particles = new ArrayList<>();
    private long revision = -1, tick, received;
    private String selected, hovered;
    private boolean closed;
    public boolean reducedMotion, paused;
    private SceneSnapshot frozen;
    public final VendingCatChoreography cat = new VendingCatChoreography();
    private double doorLeft, doorRight, doorRear, frozenLeft, frozenRight, frozenRear;
    private double fromLeft, fromRight, fromRear;
    private long doorReceived, cosmeticTime;

    public static final class Particle {

        final double x, y, z, dx, dz;
        final long start;

        Particle(SceneBody b, long now, int i) {
            x = b.x;
            y = b.y;
            z = b.z;
            start = now;
            dx = Math.sin(i * 2.4) * .6;
            dz = Math.cos(i * 2.4) * .6;
        }
    }

    public VendingSceneView(List<ItemSlot> slots) {
        if (slots.size() != 100) throw new IllegalArgumentException("100 real output slots required");
        this.slots = slots;
    }

    public boolean accept(Frame frame, long now) {
        if (closed || frame.revision <= revision || frame.tick < tick || revision < 0 && !frame.full) return false;
        Map<String, Entry> next = frame.full ? new LinkedHashMap<>() : new LinkedHashMap<>(entries);
        for (String id : frame.removed) next.remove(id);
        next.putAll(frame.entries);
        if (next.size() > 100) return false;
        Set<Integer> owners = new HashSet<>();
        for (Entry e : next.values()) for (int slot : e.slots) if (!owners.add(slot)) return false;
        SceneSnapshot last = snapshot(now);
        Map<String, SceneBody> prior = new HashMap<>();
        for (SceneBody b : last.bodies) prior.put(b.id, b);
        // A WAIT -> FEED transition starts at its real shell pose, not an instant jump to the next 5 Hz sample.
        // Doors and travel then share the same bounded interpolation interval; no business clock waits on a client.
        for (Entry e : next.values()) {
            Entry old = entries.get(e.pose.id);
            if (e.deliveryStage == 1 && old != null && old.deliveryStage == 0) prior.put(e.pose.id, old.pose);
        }
        // Removal cause is not transported: never infer ME success from a removed ID.
        for (Entry e : next.values()) if (e.phase == 2) {
            fadeStart.put(e.pose.id, now - Math.min(2000, e.phaseAgeMs) * 1000000L);
        } else fadeStart.remove(e.pose.id);
        fadeStart.keySet()
            .retainAll(next.keySet());
        String reaction = null;
        for (Entry e : next.values()) {
            Entry old = entries.get(e.pose.id);
            if (e.deliveryStage == 2 && (old == null || old.deliveryStage != 2)) reaction = e.pose.id;
        }
        entries = next;
        if (revision < 0) {
            // Opening a GUI restores the current mechanism, it must not replay a delivery.
            fromLeft = doorLeft = frame.doorLeft;
            fromRight = doorRight = frame.doorRight;
            fromRear = doorRear = frame.doorRear;
            doorReceived = now;
        } else if (doorLeft != frame.doorLeft || doorRight != frame.doorRight || doorRear != frame.doorRear) {
            fromLeft = sampleDoor(0, now);
            fromRight = sampleDoor(1, now);
            fromRear = sampleDoor(2, now);
            doorLeft = frame.doorLeft;
            doorRight = frame.doorRight;
            doorRear = frame.doorRear;
            doorReceived = now;
        }
        if (revision >= 0 && !reducedMotion && reaction != null) cat.react(reaction);
        previous = prior;
        revision = frame.revision;
        tick = frame.tick;
        received = now;
        if (selected != null && !entries.containsKey(selected)) selected = null;
        return true;
    }

    public SceneSnapshot snapshot(long now) {
        if (paused && frozen != null) return frozen;
        double t = reducedMotion ? 1 : Math.max(0, Math.min(1, (now - received) / 200000000.0));
        List<SceneBody> bodies = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.deliveryStage == 0) continue; // Queued stock still owns its real slots, never a visible proxy.
            SceneBody b = e.pose, p = previous.get(b.id);
            if (p != null && t < 1) b = interpolate(p, b, t);
            bodies.add(b);
        }
        SceneSnapshot result = new SceneSnapshot(VendingSceneFrames.LAYOUT, Math.max(0, revision), tick, bodies);
        frozen = result;
        frozenLeft = sampleDoor(0, now);
        frozenRight = sampleDoor(1, now);
        frozenRear = sampleDoor(2, now);
        return result;
    }

    /** Door pose belonging to the most recently drawn snapshot; preserves the existing caller API. */
    public double door(int route) {
        return route == 0 ? frozenLeft : route == 1 ? frozenRight : frozenRear;
    }

    /** Bounded 5 Hz interpolation; no extrapolation, physics, or dependency on wall-clock time. */
    public double door(int route, long now) {
        if (paused && frozen != null) return door(route);
        return sampleDoor(route, now);
    }

    private double sampleDoor(int route, long now) {
        double target = route == 0 ? doorLeft : route == 1 ? doorRight : doorRear;
        double from = route == 0 ? fromLeft : route == 1 ? fromRight : fromRear;
        double t = reducedMotion ? 1 : Math.max(0, Math.min(1, (now - doorReceived) / 200000000.0));
        return from + (target - from) * t;
    }

    public void animate(long now, SceneSnapshot snapshot) {
        double dt = cosmeticTime == 0 ? 0 : (now - cosmeticTime) / 1000000000.0;
        cosmeticTime = now;
        cat.update(dt, snapshot.bodies, reducedMotion, paused);
    }

    public static SceneBody interpolate(SceneBody a, SceneBody b, double t) {
        t = Math.max(0, Math.min(1, t));
        double dot = a.qx * b.qx + a.qy * b.qy + a.qz * b.qz + a.qw * b.qw, sign = dot < 0 ? -1 : 1;
        double x = a.qx * (1 - t) + b.qx * t * sign, y = a.qy * (1 - t) + b.qy * t * sign,
            z = a.qz * (1 - t) + b.qz * t * sign, w = a.qw * (1 - t) + b.qw * t * sign;
        double norm = Math.sqrt(x * x + y * y + z * z + w * w);
        SceneBody result = new SceneBody(
            b.id,
            b.halfX,
            b.halfY,
            b.halfZ,
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t,
            x / norm,
            y / norm,
            z / norm,
            w / norm,
            b.vx,
            b.vy,
            b.vz,
            b.wx,
            b.wy,
            b.wz,
            b.sleeping,
            b.quietTicks);
        SceneBounds bounds = VendingSceneFrames.BOUNDS;
        return result.withPosition(
            Math.max(bounds.minX + result.extentX(), Math.min(bounds.maxX - result.extentX(), result.x)),
            Math.max(bounds.minY + result.extentY(), Math.min(bounds.maxY - result.extentY(), result.y)),
            Math.max(bounds.minZ + result.extentZ(), Math.min(bounds.maxZ - result.extentZ(), result.z)));
    }

    public ItemStack stack(String id) {
        Entry e = entries.get(id);
        if (e == null) return null;
        for (int index : e.slots) {
            ItemSlot slot = slots.get(index);
            if (slot.getSyncHandler() != null) {
                ItemStack s = slot.getSlot()
                    .getStack();
                if (s != null && s.stackSize > 0) return s;
            }
        }
        return null;
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    public List<Entry> groups() {
        List<Entry> groups = new ArrayList<>();
        for (Entry e : entries.values()) {
            ItemStack s = stack(e.pose.id);
            if (s == null) continue;
            boolean found = false;
            for (Entry g : groups) if (same(s, stack(g.pose.id))) {
                found = true;
                break;
            }
            if (!found) groups.add(e);
        }
        return groups;
    }

    public int quantity(String id) {
        ItemStack key = stack(id);
        int total = 0;
        Set<Integer> seen = new HashSet<>();
        for (Entry e : entries.values()) if (same(key, stack(e.pose.id))) for (int i : e.slots) if (seen.add(i)) {
            ItemStack s = slots.get(i)
                .getSlot()
                .getStack();
            if (same(key, s)) total += s.stackSize;
        }
        return total;
    }

    public Entry selected() {
        return entries.get(selected);
    }

    public void select(String id) {
        String next = entries.containsKey(id) ? id : null;
        if (next != null && !next.equals(selected) && !reducedMotion) cat.react(next);
        selected = next;
    }

    public void hover(String id) {
        hovered = id;
    }

    public boolean highlighted(String id) {
        String key = hovered != null ? hovered : selected;
        return key != null && same(stack(key), stack(id));
    }

    public float opacity(String id, long now) {
        Long start = fadeStart.get(id);
        return start == null || reducedMotion ? 1 : (float) Math.max(.18, 1 - (now - start) / 2000000000.0);
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public List<Particle> particles(long now) {
        particles.clear();
        // Cosmetic only while the server explicitly reports fade. Rejection/reset cancels immediately.
        for (Entry e : entries.values()) if (e.phase == 2) {
            Long start = fadeStart.get(e.pose.id);
            if (start == null) continue;
            for (int i = 0; i < 8 && particles.size() < 64; i++) {
                long born = start + i * 200000000L;
                if (now >= born && now - born < 1200000000L)
                    particles.add(new Particle(e.pose, born, i + (int) (e.seed & 15)));
            }
        }
        return particles;
    }

    @Override
    public void close() {
        closed = true;
        entries.clear();
        previous.clear();
        fadeStart.clear();
        particles.clear();
        selected = hovered = null;
        frozen = null;
        cat.reset();
        cosmeticTime = 0;
    }
}
