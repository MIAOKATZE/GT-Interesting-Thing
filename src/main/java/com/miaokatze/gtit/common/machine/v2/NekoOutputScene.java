package com.miaokatze.gtit.common.machine.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBounds;
import com.miaokatze.miaokatzecore.api.miaovision.SceneSimulation;
import com.miaokatze.miaokatzecore.api.miaovision.SceneSnapshot;

/** Machine-owned server simulation and bounded presentation DTOs. Never owns or transfers an item. */
public final class NekoOutputScene {

    public static final String LAYOUT = "neko-room-v1";
    public static final int SCHEMA = 1, MAX_FRAME_BYTES = 65536;

    public interface Inventory {

        ItemStack get(int slot);
    }

    private final NekoOutputLedger ledger;
    private final Inventory inventory;
    private final NekoMeTransferQueue transfers;
    private final SceneSimulation simulation = new SceneSimulation(
        LAYOUT,
        new SceneBounds(-5, 0, -3, 5, 8, 3),
        SceneSimulation.ContactModel.ORIENTED_BOX);
    private final Map<Long, NBTTagCompound> published = new LinkedHashMap<>();
    private final Map<Long, Long> changed = new LinkedHashMap<>(), removed = new LinkedHashMap<>();
    private final Map<Long, Integer> removalCauses = new LinkedHashMap<>();
    private long revision, lastLedger = -1, historyFloor, serverTick;
    private boolean physicsAllowed = true, lastPublishedActive, lastPublishedAllowed = true;
    private long publishedTick;
    private boolean migrationSettlePending;
    public static final int WAIT_DOOR = 0, FEED = 1, RELEASED = 2;
    public static final int DOOR_TICKS = 12, FEED_TICKS = 10;

    private static final class Delivery {

        int route, stage, age;
        SceneBody pose;

        Delivery(int route, SceneBody pose) {
            this.route = route;
            this.pose = pose;
        }
    }

    private final Map<Long, Delivery> deliveries = new LinkedHashMap<>();
    private final int[] doorTicks = new int[3], laneCooldown = new int[3];
    private final int[] publishedDoors = new int[3];

    public boolean isVisible(long dropId) {
        Delivery delivery = deliveries.get(dropId);
        return delivery != null && delivery.stage != WAIT_DOOR;
    }

    public void invalidate() {
        lastLedger = -1;
    }

    public void markMeRemoval(long dropId) {
        for (NekoOutputLedger.Drop drop : ledger.drops()) if (drop.dropId == dropId && drop.slots.size() == 1) {
            removalCauses.put(dropId, 1);
            return;
        }
    }

    public NekoOutputScene(NekoOutputLedger ledger, Inventory inventory, NekoMeTransferQueue transfers) {
        this.ledger = ledger;
        this.inventory = inventory;
        this.transfers = transfers;
    }

    /** Default simulation is OFF; only a delivery/removal explicitly enables a finite motion window. */
    public boolean isPhysicsActive() {
        return simulation.isEnabled();
    }

    public void setPhysicsAllowed(boolean allowed) {
        physicsAllowed = allowed;
        if (!allowed) simulation.setEnabled(false);
        else if (!simulation.isSettled()) simulation.setEnabled(true);
        publish();
    }

    public boolean tick(long tick) {
        return tick(tick, System.currentTimeMillis());
    }

    /** Delivery/business clocks advance independently of the optional physics engine. */
    public boolean tick(long tick, long now) {
        serverTick = tick;
        reconcile();
        // A one-time, machine-owned shape-change event, never activation inside Core/NBT restore.
        boolean migrated = migrationSettlePending && physicsAllowed;
        if (migrated) {
            migrationSettlePending = false;
            simulation.wake();
            if (!simulation.isSettled()) simulation.setEnabled(true);
        }
        boolean animated = advanceDelivery(now);
        boolean moved = simulation.isEnabled() && simulation.step();
        if (simulation.isSettled()) simulation.setEnabled(false);
        if ((animated || moved || !transfers.isEmpty()) && (tick % 4 == 0 || moved && simulation.isSettled()))
            publish();
        return animated || moved || migrated;
    }

    /** Actual queued visual identities; settled or already-feeding drops are never rerouted. */
    public java.util.Set<Long> waitingDropIds() {
        java.util.Set<Long> waiting = new java.util.LinkedHashSet<>();
        for (NekoOutputLedger.Drop drop : ledger.drops()) {
            Delivery d = deliveries.get(drop.dropId);
            if (d == null || d.stage == WAIT_DOOR) waiting.add(drop.dropId);
        }
        return java.util.Collections.unmodifiableSet(waiting);
    }

    private void reconcile() {
        if (lastLedger == ledger.revision()) return;
        ledger.routeWaitingToRear(id -> {
            Delivery d = deliveries.get(id);
            return d == null || d.stage == WAIT_DOOR;
        });
        lastLedger = ledger.revision();
        java.util.Set<Long> live = new java.util.HashSet<>();
        for (NekoOutputLedger.Drop d : ledger.drops()) if (!d.slots.isEmpty()) live.add(d.dropId);
        boolean motion = false;
        // Remove before adding, including a full 100-slot replacement.
        for (Long id : new ArrayList<>(deliveries.keySet())) if (!live.contains(id)) {
            deliveries.remove(id);
            motion |= simulation.remove(Long.toString(id));
        }
        for (NekoOutputLedger.Drop d : ledger.drops()) if (!d.slots.isEmpty() && !deliveries.containsKey(d.dropId)) {
            int route = d.rear ? 2 : (int) (d.dropId & 1);
            Delivery delivery = new Delivery(route, feedPose(d.dropId, route, 0));
            deliveries.put(d.dropId, delivery);
        }
        for (NekoOutputLedger.Drop drop : ledger.drops()) {
            Delivery d = deliveries.get(drop.dropId);
            if (d != null && d.stage == WAIT_DOOR) {
                int route = drop.rear ? 2 : (int) (drop.dropId & 1);
                if (route != d.route) {
                    d.route = route;
                    d.age = 0;
                    d.pose = feedPose(drop.dropId, route, 0);
                }
            }
        }
        if (motion && physicsAllowed && !simulation.isSettled()) simulation.setEnabled(true);
        publish();
    }

    private boolean advanceDelivery(long now) {
        boolean changed = false;
        boolean[] requested = new boolean[3];
        for (Delivery d : deliveries.values()) if (d.stage != RELEASED) requested[d.route] = true;
        for (int route = 0; route < 3; route++) {
            int before = doorTicks[route];
            doorTicks[route] = Math.max(0, Math.min(DOOR_TICKS, before + (requested[route] ? 1 : -1)));
            changed |= before != doorTicks[route];
            if (laneCooldown[route] > 0) {
                laneCooldown[route]--;
                changed = true;
            }
        }
        for (Map.Entry<Long, Delivery> entry : deliveries.entrySet()) {
            Delivery d = entry.getValue();
            if (d.stage == RELEASED) continue;
            changed = true;
            if (d.stage == WAIT_DOOR) {
                d.age++;
                if (doorTicks[d.route] < DOOR_TICKS) continue;
                d.stage = FEED;
                d.age = 0;
                // All ready identities emerge together once their door is open. The existing 100-slot
                // inventory bounds the burst; the legacy cooldown field remains readable in old saves.
                laneCooldown[d.route] = 0;
                transfers.visible(entry.getKey(), now);
            } else {
                d.age++;
                d.pose = feedPose(entry.getKey(), d.route, d.age);
                if (d.age >= FEED_TICKS) {
                    d.stage = RELEASED;
                    d.age = 0;
                    if (!physicsAllowed) d.pose = settledPose(entry.getKey());
                    simulation.put(d.pose);
                    if (physicsAllowed) simulation.setEnabled(true);
                }
            }
        }
        return changed;
    }

    private ItemStack outputStack(long id) {
        for (NekoOutputLedger.Drop d : ledger.drops()) if (d.dropId == id) for (int slot : d.slots) {
            ItemStack stack = inventory.get(slot);
            if (stack != null) return stack;
        }
        return null;
    }

    private boolean blockShape(long id) {
        return NekoOutputModelShape.volumetric(outputStack(id));
    }

    private SceneBody shape(long id, double x, double y, double z) {
        // Full native block geometry versus a genuinely thin native pixel-extruded sprite.
        ItemStack stack = outputStack(id);
        return NekoOutputBodyGeometry.create(
            Long.toString(id),
            NekoOutputModelShape.nativeBlock(stack),
            NekoOutputModelShape.volumetric(stack),
            x,
            y,
            z);
    }

    private SceneBody feedPose(long id, int route, int age) {
        double t = Math.min(1, Math.max(0, age / (double) FEED_TICKS));
        double sign = route == 0 ? -1 : 1;
        double spread = ((id * 37 % 17) - 8) / 8.0;
        // Start fully inside the real shell: the first 200 ms clears the interpolated door before protruding.
        // Per-identity exit directions then spread a pile over the floor.
        // Fan the simultaneous rear burst across the aperture, with shallow depth layers for the
        // 100-slot worst case. Full native blocks start apart instead of sharing one launch point.
        long cell = (id - 1) % 32;
        double x = route == 2 ? (cell % 8 - 3.5) * .82 : sign * (4.3 - 1.2 * t);
        double y = route == 2 ? .58 + (cell / 8) * .82 : 3.0;
        double z = route == 2 ? -2.65 + ((id - 1) / 32 % 4) * .82 + 1.15 * t : .5 + spread * .14 * t;
        double pitch = t * (.35 + (id % 7) * .11), yaw = t * spread * .65, roll = t * sign * .28;
        double cx = Math.cos(pitch / 2), sx = Math.sin(pitch / 2), cy = Math.cos(yaw / 2), sy = Math.sin(yaw / 2),
            cz = Math.cos(roll / 2), sz = Math.sin(roll / 2);
        SceneBody pose = shape(id, x, y, z)
            .withRotation(
                sx * cy * cz + cx * sy * sz,
                cx * sy * cz - sx * cy * sz,
                cx * cy * sz + sx * sy * cz,
                cx * cy * cz - sx * sy * sz)
            .withVelocity(
                route == 2 ? spread * .7 : -sign * (1.8 + (id % 5) * .18),
                -.15,
                route == 2 ? 2.1 : spread * 1.6,
                1.1 + (id % 3) * .3,
                spread * 1.2,
                sign * .8);
        // Larger native blocks must also start fully within the rear lane's bounded simulation envelope.
        return NekoOutputBodyGeometry.fitInside(pose, simulation.bounds());
    }

    private SceneBody settledPose(long id) {
        // Use authoritative live poses, including rotated/frozen bodies, never stale published positions.
        Map<String, SceneBody> occupied = new LinkedHashMap<>();
        for (SceneBody body : simulation.snapshot().bodies) occupied.put(body.id, body);
        for (Map.Entry<Long, Delivery> entry : deliveries.entrySet()) if (entry.getValue().stage == RELEASED)
            occupied.putIfAbsent(Long.toString(entry.getKey()), entry.getValue().pose);
        SceneBody template = shape(id, 0, 1, 0);
        if (!blockShape(id)) {
            double yaw = (id % 31) * .2;
            template = template.withRotation(
                Math.sqrt(.5) * Math.cos(yaw / 2),
                Math.sqrt(.5) * Math.sin(yaw / 2),
                -Math.sqrt(.5) * Math.sin(yaw / 2),
                Math.sqrt(.5) * Math.cos(yaw / 2));
        }
        for (int layer = 0; layer < 8; layer++)
            for (int row = 0; row < 7; row++) for (int column = 0; column < 12; column++) {
                double x = -4.4 + column * .8, y = template.extentY() + layer * .8, z = -2.4 + row * .8;
                boolean clear = true;
                for (SceneBody body : occupied.values()) if (!body.id.equals(Long.toString(id))
                    && Math.abs(x - body.x) < template.extentX() + .01 + body.extentX()
                    && Math.abs(y - body.y) < template.extentY() + .01 + body.extentY()
                    && Math.abs(z - body.z) < template.extentZ() + .01 + body.extentZ()) {
                        clear = false;
                        break;
                    }
                if (clear) return template.withPosition(x, y, z)
                    .withSleeping(true);
            }
        // 672 candidates for at most 99 other bounded proxies; defensive finite presentation fallback.
        return template.withPosition(0, 7.5, 0)
            .withSleeping(true);
    }

    private void publish() {
        for (SceneBody b : simulation.snapshot().bodies) {
            Delivery delivery = deliveries.get(Long.parseLong(b.id));
            if (delivery != null) delivery.pose = b;
        }
        Map<Long, NBTTagCompound> next = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (NekoOutputLedger.Drop d : ledger.drops()) if (!d.slots.isEmpty()) {
            Delivery delivery = deliveries.get(d.dropId);
            if (delivery == null) continue;
            SceneBody body = delivery.pose;
            NBTTagCompound n = bodyTag(body);
            n.setLong("dropId", d.dropId);
            n.setLong("batchId", d.batchId);
            n.setLong("seed", d.seed);
            n.setBoolean("rear", d.rear);
            n.setInteger("route", delivery.route);
            n.setInteger("deliveryStage", delivery.stage);
            n.setInteger("deliveryAgeTicks", delivery.age);
            int[] slots = new int[d.slots.size()];
            int quantity = 0, phase = Integer.MAX_VALUE;
            long phaseAge = Long.MAX_VALUE;
            for (int i = 0; i < slots.length; i++) {
                slots[i] = d.slots.get(i);
                ItemStack item = inventory.get(slots[i]);
                if (item != null) quantity += item.stackSize;
                int slotPhase = transfers.phase(slots[i], now);
                long slotAge = transfers.phaseAgeMs(slots[i], now);
                if (slotPhase < phase) {
                    phase = slotPhase;
                    phaseAge = slotAge;
                } else if (slotPhase == phase) phaseAge = Math.min(phaseAge, slotAge);
            }
            n.setIntArray("slots", slots);
            n.setInteger("quantity", quantity);
            n.setInteger("phase", phase);
            n.setLong("phaseAgeMs", phaseAge);
            n.setLong("generation", d.dropId);
            next.put(d.dropId, n);
        }
        boolean dirty = !java.util.Arrays.equals(doorTicks, publishedDoors);
        for (Map.Entry<Long, NBTTagCompound> e : next.entrySet()) if (!e.getValue()
            .equals(published.get(e.getKey()))) {
                dirty = true;
                break;
            }
        if (!dirty && !published.keySet()
            .equals(next.keySet())) dirty = true;
        if (lastPublishedActive != simulation.isEnabled() || lastPublishedAllowed != physicsAllowed) dirty = true;
        if (!dirty) return;
        System.arraycopy(doorTicks, 0, publishedDoors, 0, 3);
        lastPublishedActive = simulation.isEnabled();
        lastPublishedAllowed = physicsAllowed;
        publishedTick = serverTick;
        revision++;
        for (Long id : published.keySet()) if (!next.containsKey(id)) removed.put(id, revision);
        for (Map.Entry<Long, NBTTagCompound> e : next.entrySet()) if (!e.getValue()
            .equals(published.get(e.getKey()))) changed.put(e.getKey(), revision);
        for (Long id : next.keySet()) {
            removed.remove(id);
            removalCauses.remove(id);
        }
        published.clear();
        published.putAll(next);
        changed.keySet()
            .retainAll(next.keySet());
        while (removed.size() > 100) {
            Long id = removed.keySet()
                .iterator()
                .next();
            historyFloor = Math.max(historyFloor, removed.remove(id));
            removalCauses.remove(id);
        }
    }

    /** afterRevision=-1 is a full open snapshot. Dirty frames are polled at most 5 Hz by the GUI. */
    public NBTTagCompound frame(long afterRevision) {
        reconcile();
        if (afterRevision < 0) publish();
        boolean full = afterRevision < 0 || afterRevision < historyFloor || afterRevision > revision;
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("schema", SCHEMA);
        n.setString("layout", LAYOUT);
        n.setBoolean("full", full);
        n.setLong("revision", revision);
        n.setLong("tick", publishedTick);
        n.setBoolean("active", simulation.isEnabled());
        n.setBoolean("physicsAllowed", physicsAllowed);
        n.setDouble("doorLeft", doorTicks[0] / (double) DOOR_TICKS);
        n.setDouble("doorRight", doorTicks[1] / (double) DOOR_TICKS);
        n.setDouble("doorRear", doorTicks[2] / (double) DOOR_TICKS);
        NBTTagList bodies = new NBTTagList();
        for (Map.Entry<Long, NBTTagCompound> e : published.entrySet())
            if (full || changed.get(e.getKey()) > afterRevision) bodies.appendTag(
                e.getValue()
                    .copy());
        n.setTag("bodies", bodies);
        NBTTagList deletes = new NBTTagList();
        if (!full) for (Map.Entry<Long, Long> e : removed.entrySet()) if (e.getValue() > afterRevision) {
            NBTTagCompound d = new NBTTagCompound();
            d.setLong("dropId", e.getKey());
            d.setInteger("reason", removalCauses.containsKey(e.getKey()) ? removalCauses.get(e.getKey()) : 0);
            deletes.appendTag(d);
        }
        n.setTag("removed", deletes);
        return n;
    }

    public NBTTagCompound write() {
        reconcile();
        publish();
        SceneSnapshot snapshot = simulation.snapshot();
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("schema", SCHEMA);
        n.setInteger("deliveryVersion", 1);
        n.setString("layout", LAYOUT);
        n.setLong("revision", snapshot.revision);
        n.setLong("tick", snapshot.tick);
        n.setBoolean("physicsAllowed", physicsAllowed);
        n.setBoolean("migrationSettlePending", migrationSettlePending);
        n.setIntArray("doors", doorTicks.clone());
        n.setIntArray("laneCooldown", laneCooldown.clone());
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Long, Delivery> entry : deliveries.entrySet()) {
            Delivery d = entry.getValue();
            NBTTagCompound body = bodyTag(d.pose);
            body.setInteger("route", d.route);
            body.setInteger("deliveryStage", d.stage);
            body.setInteger("deliveryAgeTicks", d.age);
            list.appendTag(body);
        }
        n.setTag("bodies", list);
        return n;
    }

    /** Validate the complete snapshot before committing. Legacy poses migrate as released, never replay. */
    public void restore(NBTTagCompound n) {
        List<SceneBody> bodies = new ArrayList<>();
        Map<Long, Delivery> restored = new LinkedHashMap<>();
        java.util.Set<String> expected = new java.util.HashSet<>();
        Map<Long, Integer> expectedRoutes = new LinkedHashMap<>();
        for (NekoOutputLedger.Drop d : ledger.drops()) if (!d.slots.isEmpty()) {
            expected.add(Long.toString(d.dropId));
            expectedRoutes.put(d.dropId, d.rear ? 2 : (int) (d.dropId & 1));
        }
        boolean valid = false, legacyShapeChanged = false;
        int[] doors = n.getIntArray("doors"), cooldown = n.getIntArray("laneCooldown");
        boolean staged = n.getInteger("deliveryVersion") == 1;
        try {
            if (n.getInteger("schema") != SCHEMA || !LAYOUT.equals(n.getString("layout")))
                throw new IllegalArgumentException("schema/layout");
            if (staged) {
                if (doors.length != 3 || cooldown.length != 3) throw new IllegalArgumentException("doors");
                for (int i = 0; i < 3; i++)
                    if (doors[i] < 0 || doors[i] > DOOR_TICKS || cooldown[i] < 0 || cooldown[i] > 4)
                        throw new IllegalArgumentException("door clock");
            }
            NBTTagList list = n.getTagList("bodies", 10);
            if (list.tagCount() != expected.size() || list.tagCount() > 100)
                throw new IllegalArgumentException("body count");
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                SceneBody body = readBody(tag);
                simulation.bounds()
                    .validate(body); // Do not repair an invalid saved pose by clamping it.
                double previousWidth = body.halfX, previousHeight = body.halfY, previousDepth = body.halfZ;
                // Old room saves used identical cubes for every item. Shrink only a sprite's depth;
                // identity, position, orientation, clocks and inventory remain exact, and restore stays OFF.
                if (!tag.hasKey("modelShapeVersion") && !blockShape(Long.parseLong(body.id))) {
                    body = new SceneBody(
                        body.id,
                        body.halfX,
                        body.halfY,
                        Math.min(body.halfZ, Math.min(body.halfX, body.halfY) * .1),
                        body.x,
                        body.y,
                        body.z,
                        body.qx,
                        body.qy,
                        body.qz,
                        body.qw,
                        body.vx,
                        body.vy,
                        body.vz,
                        body.wx,
                        body.wy,
                        body.wz,
                        body.sleeping,
                        body.quietTicks);
                }
                ItemStack stack = outputStack(Long.parseLong(body.id));
                if (tag.getInteger("modelShapeVersion") < 2 && stack != null && stack.getItem() instanceof ItemBlock)
                    body = NekoOutputModelShape.nativeBlock(stack)
                        ? NekoOutputBodyGeometry.enlargeBlock(body, simulation.bounds())
                        : NekoOutputBodyGeometry.flattenInventoryBlock(body, simulation.bounds());
                if (!expected.remove(body.id)) throw new IllegalArgumentException("identity");
                simulation.bounds()
                    .validate(body);
                int route = expectedRoutes.get(Long.parseLong(body.id));
                Delivery d = new Delivery(staged ? tag.getInteger("route") : route, body);
                if (d.route != route) throw new IllegalArgumentException("delivery route");
                d.stage = staged ? tag.getInteger("deliveryStage") : RELEASED;
                d.age = staged ? tag.getInteger("deliveryAgeTicks") : 0;
                if (d.route < 0 || d.route > 2
                    || d.stage < WAIT_DOOR
                    || d.stage > RELEASED
                    || d.age < 0
                    || d.stage == FEED && (d.age >= FEED_TICKS || doors[d.route] != DOOR_TICKS))
                    throw new IllegalArgumentException("delivery clock");
                restored.put(Long.parseLong(body.id), d);
                if (d.stage == RELEASED) {
                    bodies.add(body);
                    legacyShapeChanged |= body.halfX != previousWidth || body.halfY != previousHeight
                        || body.halfZ != previousDepth;
                }
            }
            simulation.restore(new SceneSnapshot(LAYOUT, n.getLong("revision"), n.getLong("tick"), bodies));
            valid = true;
        } catch (IllegalArgumentException ignored) { /* deterministic settled migration, no replayed emission */ }
        deliveries.clear();
        java.util.Arrays.fill(doorTicks, 0);
        java.util.Arrays.fill(laneCooldown, 0);
        if (valid) {
            deliveries.putAll(restored);
            if (staged) {
                System.arraycopy(doors, 0, doorTicks, 0, 3);
                System.arraycopy(cooldown, 0, laneCooldown, 0, 3);
            }
        } else {
            bodies.clear();
            simulation.restore(new SceneSnapshot(LAYOUT, 0, 0, bodies));
            for (NekoOutputLedger.Drop drop : ledger.drops()) if (!drop.slots.isEmpty()) {
                Delivery d = new Delivery(drop.rear ? 2 : (int) (drop.dropId & 1), settledPose(drop.dropId));
                d.stage = RELEASED;
                deliveries.put(drop.dropId, d);
                bodies.add(d.pose);
            }
            simulation.restore(new SceneSnapshot(LAYOUT, 0, 0, bodies));
        }
        physicsAllowed = !n.hasKey("physicsAllowed") || n.getBoolean("physicsAllowed");
        // Loading remains OFF. Poses stay exact except minimum boundary corrections for revised ItemBlock shapes.
        // The owner processes this finite event on a later tick only when allowed; persisting the pending bit
        // also covers save-before-first-tick and disabled physics.
        migrationSettlePending = valid && !bodies.isEmpty()
            && (legacyShapeChanged || n.getBoolean("migrationSettlePending"));
        simulation.setEnabled(false);
        lastLedger = ledger.revision();
        published.clear();
        changed.clear();
        removed.clear();
        removalCauses.clear();
        historyFloor = revision;
        publish();
    }

    public static NBTTagCompound bodyTag(SceneBody b) {
        NBTTagCompound n = new NBTTagCompound();
        n.setString("id", b.id);
        n.setInteger("modelShapeVersion", 2);
        double[] values = { b.halfX, b.halfY, b.halfZ, b.x, b.y, b.z, b.qx, b.qy, b.qz, b.qw, b.vx, b.vy, b.vz, b.wx,
            b.wy, b.wz };
        String[] names = { "hx", "hy", "hz", "x", "y", "z", "qx", "qy", "qz", "qw", "vx", "vy", "vz", "wx", "wy",
            "wz" };
        for (int i = 0; i < names.length; i++) n.setDouble(names[i], values[i]);
        n.setBoolean("sleeping", b.sleeping);
        n.setInteger("quietTicks", b.quietTicks);
        return n;
    }

    public static SceneBody readBody(NBTTagCompound n) {
        return new SceneBody(
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
    }
}
