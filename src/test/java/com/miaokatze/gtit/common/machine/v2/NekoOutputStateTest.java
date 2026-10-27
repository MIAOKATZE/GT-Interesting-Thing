package com.miaokatze.gtit.common.machine.v2;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;

/** JavaExec regression; no client, world, texture, machine registration, or AE network needed. */
public final class NekoOutputStateTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ItemStack iron(int count) {
        return new ItemStack(Items.iron_ingot, count);
    }

    private static final class Host implements NekoMeTransferQueue.OutputSlots, NekoMeTransferQueue.UplinkOps {

        final ItemStack[] slots = new ItemStack[100];
        final NekoOutputLedger ledger = new NekoOutputLedger();
        final List<ItemStack> pending = new ArrayList<>();
        final NekoMeTransferQueue queue = new NekoMeTransferQueue(this, this, () -> {}, () -> this.meMode);
        NekoOutputScene scene;
        boolean lost, accept = true, meMode = true, visible = true;
        int accepted = Integer.MAX_VALUE, injected, resets, acceptCalls, injectCalls;

        public ItemStack get(int slot) {
            return slot >= 0 ? slots[slot] : null;
        }

        public void set(int slot, ItemStack stack) {
            slots[slot] = stack;
            ledger.changed(slot, stack);
        }

        public long dropId(int slot) {
            NekoOutputLedger.Token t = ledger.slot(slot);
            return t == null ? 0 : t.dropId;
        }

        public long generation(int slot) {
            NekoOutputLedger.Token t = ledger.slot(slot);
            return t == null ? 0 : t.generation;
        }

        public boolean isVisible(int slot) {
            return visible && (scene == null || scene.isVisible(dropId(slot)));
        }

        public boolean preserveLegacy(ItemStack stack) {
            pending.add(stack);
            ledger.enqueue(stack, false);
            return true;
        }

        public void visualReset(int slot) {
            resets++;
        }

        public boolean canAccept(ItemStack stack) {
            acceptCalls++;
            return accept;
        }

        public boolean isLost() {
            return lost;
        }

        public appeng.api.storage.data.IAEItemStack inject(ItemStack stack) {
            injectCalls++;
            int take = Math.min(accepted, stack.stackSize);
            injected += take;
            final ItemStack remainder = stack.copy();
            remainder.stackSize -= take;
            if (remainder.stackSize == 0) return null;
            return (appeng.api.storage.data.IAEItemStack) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { appeng.api.storage.data.IAEItemStack.class },
                (proxy, method, args) -> {
                    if (method.getName()
                        .equals("getStackSize")) return (long) remainder.stackSize;
                    if (method.getName()
                        .equals("getItemStack")) return remainder.copy();
                    throw new UnsupportedOperationException(method.getName());
                });
        }

        ItemStack enqueue(int count) {
            ItemStack item = iron(count);
            ledger.enqueue(item, false);
            pending.add(item);
            return item;
        }

        void deliver(ItemStack item, int slot) {
            slots[slot] = item.copy();
            ledger.delivered(item, slot);
            pending.remove(item);
        }

        long queueNow(int slot) {
            queue.enqueueIfMeMode(slots[slot], slot);
            return queue.writeToNBT()
                .getCompoundTagAt(0)
                .getLong("creationTime");
        }
    }

    private static void migration() {
        for (int oldSize : new int[] { 8, 16, 32 }) {
            ItemStackHandler old = new ItemStackHandler(oldSize);
            ItemStack preserved = iron(9);
            preserved.setTagCompound(new NBTTagCompound());
            preserved.getTagCompound()
                .setString("round2", "preserve-exact-nbt");
            old.setStackInSlot(oldSize - 1, preserved);
            NBTTagCompound saved = old.serializeNBT();
            NBTTagCompound unchanged = (NBTTagCompound) saved.copy();
            ItemStackHandler upgraded = new ItemStackHandler(32);
            upgraded.deserializeNBT(NekoInventoryMigration.expand(saved, 32));
            check(saved.equals(unchanged), "capacity migration never mutates the original save");
            check(upgraded.getSlots() == 32, "eight/sixteen/thirty-two slots load as thirty-two");
            check(
                ItemStack.areItemStacksEqual(upgraded.getStackInSlot(oldSize - 1), preserved),
                "old indexed stack and complete NBT retained");
            for (int i = oldSize; i < 32; i++) check(upgraded.getStackInSlot(i) == null, "new inputs start empty");
            upgraded.setStackInSlot(31, iron(11));
            ItemStackHandler round = new ItemStackHandler(1);
            round.deserializeNBT(NekoInventoryMigration.expand(upgraded.serializeNBT(), 32));
            check(round.getSlots() == 32 && round.getStackInSlot(31).stackSize == 11, "thirty-two roundtrip");
        }
        for (int i = 0; i < 100; i++)
            check(NekoInventoryMigration.outputIndex(i + 8) == i, "legacy output indices unchanged");
        for (int i = 0; i < 32; i++)
            check(NekoInventoryMigration.inputIndex(i < 8 ? i : i + 100) == i, "appended input indices");
        check(
            NekoInventoryMigration.inputIndex(107) == -1 && NekoInventoryMigration.inputIndex(132) == -1,
            "input mapping does not alias output or out-of-range slots");
        Host h = new Host();
        h.slots[5] = iron(3);
        h.pending.add(iron(4));
        List<NBTTagCompound> missing = Arrays.asList(new NBTTagCompound());
        h.ledger.restore(new NBTTagCompound(), h.slots, h.pending, missing);
        NBTTagCompound first = h.ledger.write();
        NekoOutputLedger other = new NekoOutputLedger();
        other.restore(new NBTTagCompound(), h.slots, h.pending, missing);
        check(first.equals(other.write()), "old migration deterministic");
        check(h.slots[5].stackSize == 3 && h.pending.get(0).stackSize == 4, "migration keeps sole inventory");
    }

    private static void identityAndSnapshots() throws Exception {
        Host h = new Host();
        h.ledger.beginBatch(2);
        ItemStack a = h.enqueue(64), b = h.enqueue(20);
        check(h.ledger.pending(a).dropId == h.ledger.pending(b).dropId, "same batch same item shares body");
        h.deliver(a, 0);
        h.deliver(b, 1);
        long drop = h.dropId(0), generation = h.generation(0);
        h.ledger.beginBatch(1);
        ItemStack c = h.enqueue(1);
        h.deliver(c, 2);
        check(drop != h.dropId(2), "cross batch same item independent");
        NekoOutputScene scene = new NekoOutputScene(h.ledger, h::get, h.queue);
        for (int i = 1; i <= 40; i++) scene.tick(i);
        NBTTagCompound initial = scene.frame(-1);
        check(
            initial.getTagList("bodies", 10)
                .tagCount() == 2,
            "one body multi real slots");
        NBTTagCompound pose = initial.getTagList("bodies", 10)
            .getCompoundTagAt(0);
        h.set(0, iron(10));
        NBTTagCompound partial = scene.frame(-1)
            .getTagList("bodies", 10)
            .getCompoundTagAt(0);
        check(h.dropId(0) == drop && h.generation(0) == generation, "partial take preserves identity");
        check(
            partial.getDouble("x") == pose.getDouble("x") && partial.getDouble("y") == pose.getDouble("y"),
            "quantity never repositions body");
        check(partial.getInteger("quantity") == 30, "multi-slot aggregate quantity");
        h.set(0, null);
        check(
            scene.frame(-1)
                .getTagList("bodies", 10)
                .tagCount() == 2,
            "body remains until every mapped slot empty");
        h.set(1, null);
        check(
            scene.frame(-1)
                .getTagList("bodies", 10)
                .tagCount() == 1,
            "remove only empty body");
        check(scene.isPhysicsActive(), "support removal explicitly wakes motion");
        scene.setPhysicsAllowed(false);
        long before = scene.write()
            .getLong("tick");
        for (int i = 0; i < 100; i++) scene.tick(i);
        check(
            scene.write()
                .getLong("tick") == before,
            "disabled physics zero steps");
        NBTTagCompound saved = scene.write();
        NekoOutputScene reopened = new NekoOutputScene(h.ledger, h::get, h.queue);
        reopened.restore(saved);
        check(
            reopened.write()
                .equals(saved),
            "pose quaternion velocity sleep phase roundtrip without emission");
        long rev = reopened.frame(-1)
            .getLong("revision");
        check(
            reopened.frame(rev)
                .getTagList("bodies", 10)
                .tagCount() == 0,
            "unchanged delta empty");
        NBTTagCompound broken = (NBTTagCompound) saved.copy();
        broken.getTagList("bodies", 10)
            .getCompoundTagAt(0)
            .setDouble("x", Double.NaN);
        reopened.restore(broken);
        check(h.slots[2].stackSize == 1, "invalid snapshot cannot destroy inventory");
        check(
            reopened.frame(-1)
                .getTagList("bodies", 10)
                .getCompoundTagAt(0)
                .getBoolean("sleeping"),
            "invalid snapshot settled fallback not re-emission");
        Host full = new Host();
        for (int i = 0; i < 100; i++) {
            ItemStack item = full.enqueue(64);
            full.deliver(item, i);
        }
        NekoOutputScene fullScene = new NekoOutputScene(full.ledger, full::get, full.queue);
        NBTTagCompound frame = fullScene.frame(-1);
        check(
            frame.getTagList("bodies", 10)
                .tagCount() == 100,
            "all 100 logical outputs represented");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(frame, new DataOutputStream(bytes));
        check(bytes.size() <= NekoOutputScene.MAX_FRAME_BYTES, "100-body frame hard byte budget: " + bytes.size());
        System.out.println("100-body full frame bytes=" + bytes.size());
        long fullRevision = frame.getLong("revision");
        for (int i = 0; i < 100; i++) {
            full.set(i, null);
            ItemStack replacement = full.enqueue(1);
            full.deliver(replacement, i);
        }
        NBTTagCompound replacementFrame = fullScene.frame(fullRevision);
        check(
            replacementFrame.getTagList("bodies", 10)
                .tagCount() == 100
                && replacementFrame.getTagList("removed", 10)
                    .tagCount() == 100,
            "replace full inventory removes obsolete bodies before adding");
        bytes.reset();
        CompressedStreamTools.write(replacementFrame, new DataOutputStream(bytes));
        check(
            bytes.size() <= NekoOutputScene.MAX_FRAME_BYTES,
            "worst 100 changes plus 100 tombstones byte budget: " + bytes.size());
        System.out.println("100-body replacement delta bytes=" + bytes.size());
    }

    private static NBTTagCompound firstBody(NekoOutputScene scene) {
        return scene.frame(-1)
            .getTagList("bodies", 10)
            .getCompoundTagAt(0);
    }

    private static void authoritativeDelivery() {
        Host h = new Host();
        h.scene = new NekoOutputScene(h.ledger, h::get, h.queue);
        h.scene.setPhysicsAllowed(false);
        ItemStack item = h.enqueue(8);
        h.deliver(item, 0);
        check(h.queueNow(0) == -1, "ME waits for authoritative visible entrance, not inventory placement");
        check(
            firstBody(h.scene).getInteger("deliveryStage") == NekoOutputScene.WAIT_DOOR,
            "new output waits behind its actual door");
        for (int t = 1; t < NekoOutputScene.DOOR_TICKS; t++) {
            h.scene.tick(t, 100000 + t * 50);
            h.queue.tick(100000 + t * 50);
            check(!h.scene.isVisible(h.dropId(0)), "door must finish opening before appearance");
            check(!h.scene.isPhysicsActive(), "queued feed never enables physics");
        }
        NBTTagCompound savedWait = h.scene.write();
        NekoOutputScene resumed = new NekoOutputScene(h.ledger, h::get, h.queue);
        resumed.restore(savedWait);
        check(
            resumed.write()
                .equals(savedWait),
            "mid-door stage pose and elapsed restore exactly");
        h.scene = resumed;
        h.scene.tick(12, 100600);
        NBTTagCompound feed = firstBody(h.scene);
        check(feed.getInteger("deliveryStage") == NekoOutputScene.FEED, "door completion starts visible feed");
        check(
            Math.abs(feed.getDouble("x")) == 4.3 && feed.getDouble("y") == 3.0,
            "ordinary delivery starts inside side paw outlet before clearing the interpolated door");
        check(
            h.queue.phase(0, 100600) == 1 && h.queue.phaseAgeMs(0, 100600) == 0,
            "visible entrance starts exact ME hold clock");
        NBTTagCompound savedFeed = h.scene.write();
        resumed = new NekoOutputScene(h.ledger, h::get, h.queue);
        resumed.restore(savedFeed);
        check(
            resumed.write()
                .equals(savedFeed),
            "mid-feed load never replays emission");
        h.scene = resumed;
        for (int t = 13; t <= 40; t++) h.scene.tick(t, 100000 + t * 50);
        check(
            firstBody(h.scene).getInteger("deliveryStage") == NekoOutputScene.RELEASED,
            "physics OFF does not stall delivery");
        check(
            !h.scene.isPhysicsActive() && h.scene.write()
                .getLong("tick") == 0,
            "physics OFF performs zero simulation steps");
        NBTTagCompound idle = h.scene.write();
        h.queue.cancelAll();
        h.scene.frame(-1);
        long revision = h.scene.frame(-1)
            .getLong("revision");
        for (int t = 41; t <= 100; t++) check(!h.scene.tick(t, 102000), "settled local scene has no idle work");
        check(
            h.scene.frame(-1)
                .getLong("revision") == revision,
            "settled local scene has no idle revisions");
        check(
            h.scene.write()
                .equals(idle),
            "idle scene does not alter persisted physical state");
        ItemStack later = h.enqueue(3);
        h.deliver(later, 1);
        NBTTagList crossBatch = h.scene.frame(-1)
            .getTagList("bodies", 10);
        check(
            crossBatch.tagCount() == 2 && crossBatch.getCompoundTagAt(0)
                .getInteger("deliveryStage") == NekoOutputScene.RELEASED
                && crossBatch.getCompoundTagAt(1)
                    .getInteger("deliveryStage") == NekoOutputScene.WAIT_DOOR,
            "new batch after load has its own timeline without replaying old output");
        for (int t = 101; t <= 112; t++) h.scene.tick(t, 110000 + t * 50);
        check(h.scene.isVisible(h.dropId(1)), "second batch begins normal side feed");
        h.set(1, null);
        check(
            h.scene.frame(-1)
                .getTagList("bodies", 10)
                .tagCount() == 1,
            "hand take during feed removes only the exact authoritative body");

        Host rear = new Host();
        rear.scene = new NekoOutputScene(rear.ledger, rear::get, rear.queue);
        rear.scene.setPhysicsAllowed(false);
        rear.ledger.beginBatch(17);
        for (int i = 0; i < 17; i++) {
            ItemStack stack = iron(1);
            NBTTagCompound unique = new NBTTagCompound();
            unique.setInteger("test", i);
            stack.setTagCompound(unique);
            rear.ledger.enqueue(stack, false);
            rear.pending.add(stack);
            rear.deliver(stack, i);
        }
        for (int t = 1; t <= 12; t++) rear.scene.tick(t, t * 50);
        int visible = 0;
        for (NekoOutputLedger.Drop d : rear.ledger.drops()) if (rear.scene.isVisible(d.dropId)) visible++;
        check(visible == 1, "rear burst is bounded feed, not simultaneous pile explosion");
        NBTTagCompound rearBody = firstBody(rear.scene);
        check(
            rearBody.getInteger("route") == 2 && rearBody.getDouble("z") == -2.65,
            "over16 stacks emerge through open rear shutter");
        check(
            rear.scene.frame(-1)
                .getDouble("doorRear") == 1,
            "rear opens before emergence");
        for (int t = 13; t < 100; t++) rear.scene.tick(t, t * 50);
        for (NekoOutputLedger.Drop d : rear.ledger.drops())
            check(rear.scene.isVisible(d.dropId), "rear queue drains without physics");
        NBTTagCompound legacy = rear.scene.write();
        legacy.removeTag("deliveryVersion");
        rear.scene.restore(legacy);
        check(
            firstBody(rear.scene).getInteger("deliveryStage") == NekoOutputScene.RELEASED
                && firstBody(rear.scene).getInteger("route") == 2,
            "legacy rear bodies migrate released with ledger route, never re-enter doors");
        NBTTagCompound wrongRoute = rear.scene.write();
        wrongRoute.getTagList("bodies", 10)
            .getCompoundTagAt(0)
            .setInteger("route", 0);
        rear.scene.restore(wrongRoute);
        check(
            firstBody(rear.scene).getInteger("route") == 2 && firstBody(rear.scene).getBoolean("sleeping"),
            "invalid stored route falls back settled without corrupting authoritative rear metadata");
    }

    private static void meVisibleClockPersistence() {
        Host h = new Host();
        h.scene = new NekoOutputScene(h.ledger, h::get, h.queue);
        ItemStack item = h.enqueue(10);
        h.deliver(item, 0);
        h.queueNow(0);
        h.queue.tick(1000000);
        check(h.injected == 0 && h.queue.phase(0, 1000000) == 0, "invisible output cannot fade or inject");
        h.queue.visible(h.dropId(0), 1000000);
        h.queue.tick(1002999);
        check(h.injected == 0 && h.queue.phase(0, 1002999) == 1, "full three-second visible hold");
        check(h.queue.phase(0, 1003000) == 2, "fade begins exactly three seconds after entrance");
        NBTTagList saved = h.queue.writeToNBT(1004000);
        h.queue.readFromNBT(saved, 2000000);
        check(
            h.queue.phase(0, 2000000) == 2 && h.queue.phaseAgeMs(0, 2000000) == 1000,
            "persist ME elapsed fade, do not advance while unloaded or replay hold");
        h.queue.tick(2000999);
        check(h.injected == 0, "restored remainder fade still waits");
        h.queue.tick(2001000);
        check(h.injected == 10 && h.slots[0] == null, "exact accepted removal at restored fade completion");
    }

    private static void meRaces() {
        Host h = new Host();
        ItemStack a = h.enqueue(10);
        h.deliver(a, 0);
        long start = h.queueNow(0);
        h.queue.tick(start + 2999);
        check(h.injected == 0, "visible wait");
        h.queue.tick(start + 4999);
        check(h.injected == 0, "fade is not acceptance");
        h.accepted = 4;
        h.queue.tick(start + 5000);
        check(h.injected == 4 && h.slots[0].stackSize == 6, "only actual ME accepted amount removed");
        check(h.queue.size() == 1 && h.resets > 0, "partial remainder resets visual wait");
        h.set(0, iron(3));
        h.queue.tick(start + 10000);
        check(h.injected == 4 && h.slots[0].stackSize == 3, "hand take cancels in-flight remainder countdown");
        long oldGeneration = h.generation(0);
        h.set(0, null);
        ItemStack replacement = h.enqueue(3);
        h.deliver(replacement, 0);
        check(h.generation(0) != oldGeneration, "slot reuse new generation");
        h.queue.tick(start + 20000);
        check(h.slots[0].stackSize == 3 && h.queue.isEmpty(), "stale ME job cannot steal same item reused slot");
        start = h.queueNow(0);
        h.accept = false;
        h.queue.tick(start + 5000);
        check(h.slots[0].stackSize == 3 && h.queue.size() == 1, "reject retains actual stack");
        h.lost = true;
        h.queue.tick(start + 10000);
        check(h.queue.isEmpty() && h.slots[0].stackSize == 3, "offline resets leaves local inventory");
        h.lost = false;
        h.queueNow(0);
        check(h.queue.retrieveEarliest() && h.slots[0].stackSize == 3, "cancel does not duplicate inventory");
        NBTTagList legacy = new NBTTagList();
        NBTTagCompound e = new NBTTagCompound();
        iron(7).writeToNBT(e);
        e.setLong("creationTime", 0);
        legacy.appendTag(e);
        h.queue.readFromNBT(legacy);
        h.queue.tick(10000);
        check(
            h.pending.size() == 1 && h.pending.get(0).stackSize == 7,
            "detached legacy ME entry preserved paid pending");
        check(h.injected == 4, "legacy migration never injects detached copy");
    }

    private static int ownedQuantity(Host host) {
        int count = host.injected;
        for (ItemStack item : host.slots) if (item != null) count += item.stackSize;
        for (ItemStack item : host.pending) count += item.stackSize;
        return count;
    }

    private static void meExistingOutputStaggerAndBudget() {
        Host h = new Host();
        for (int slot = 0; slot < 100; slot++) h.deliver(h.enqueue(64), slot);
        long now = 100000;
        h.meMode = false;
        h.queue.enqueueExisting(100, now);
        check(h.queue.isEmpty(), "disabled mode never enrolls existing inventory");
        h.meMode = true;
        h.queue.enqueueExisting(100, now);
        check(h.queue.size() == 100 && ownedQuantity(h) == 6400, "toggle enrolls all existing owned outputs once");
        NBTTagList jobs = h.queue.writeToNBT(now);
        h.queue.enqueueExisting(100, now + 50);
        check(
            h.queue.size() == 100 && h.queue.writeToNBT(now)
                .equals(jobs),
            "repeat admission does not duplicate or reset");
        for (int slot = 0; slot < 100; slot++) check(
            jobs.getCompoundTagAt(slot)
                .getLong("creationTime") == now + slot * NekoMeTransferQueue.ME_STAGGER_MS,
            "each output receives a distinct staggered start");
        check(
            h.queue.phase(0, now) == 1 && h.queue.phase(1, now) == 0,
            "future jobs stay fully visible while awaiting turn");
        check(h.queue.phase(0, now + 3000) == 2 && h.queue.phase(1, now + 3000) == 1, "fade starts are staggered");
        h.queue.tick(now + 4999);
        check(h.acceptCalls == 0 && h.injected == 0, "each item keeps all three hold and two fade seconds");
        h.queue.tick(now + 5000);
        check(
            h.injectCalls == 1 && h.injected == 64 && h.slots[0] == null,
            "first turn removes only the accepted first stack");
        h.queue.tick(now + 5099);
        check(h.injectCalls == 1, "next stack cannot transfer ahead of its own complete fade");
        // Simulate a server stall: all remaining entries are overdue, still only one network pair per tick.
        while (!h.queue.isEmpty()) {
            int probes = h.acceptCalls, transfers = h.injectCalls;
            h.queue.tick(now + 100000);
            check(
                h.acceptCalls - probes == 1 && h.injectCalls - transfers == 1,
                "overdue catch-up has a hard per-machine tick budget");
            check(ownedQuantity(h) == 6400, "every bounded round conserves exact quantity");
        }
        check(h.injected == 6400, "all existing output eventually reaches ME without a burst");
    }

    private static void meStaggerPersistenceAndFailures() {
        Host h = new Host();
        for (int slot = 0; slot < 4; slot++) h.deliver(h.enqueue(10), slot);
        long now = 100000, resumed = 200000;
        h.queue.enqueueExisting(100, now);
        h.queue.readFromNBT(h.queue.writeToNBT(now + 50), resumed);
        check(h.queue.phaseAgeMs(0, resumed) == 50, "reload preserves elapsed hold");
        check(
            h.queue.phase(1, resumed + 49) == 0 && h.queue.phase(1, resumed + 50) == 1,
            "reload preserves pending stagger delay");
        check(
            h.queue.phase(2, resumed + 149) == 0 && h.queue.phase(2, resumed + 150) == 1,
            "reload never collapses distinct starts");
        h.accept = false;
        h.queue.tick(resumed + 10000);
        check(
            h.acceptCalls == 1 && h.injectCalls == 0 && ownedQuantity(h) == 40,
            "rejected simulation spends the tick budget and owns no items");
        h.accept = true;
        h.accepted = 4;
        h.queue.tick(resumed + 10050);
        check(
            h.injectCalls == 1 && h.injected == 4 && h.slots[1].stackSize == 6,
            "rejection does not starve another ready slot");
        check(ownedQuantity(h) == 40 && h.queue.size() == 4, "partial acceptance retains exactly the remainder");
        h.lost = true;
        h.queue.tick(resumed + 10100);
        check(
            h.queue.isEmpty() && ownedQuantity(h) == 40 && h.injectCalls == 1,
            "disconnect cancels every job but retains real remainder");
        h.lost = false;
        h.queue.enqueueExisting(100, resumed + 11000);
        h.queue.cancelAll();
        h.queue.tick(resumed + 99999);
        check(
            h.queue.isEmpty() && ownedQuantity(h) == 40 && h.injectCalls == 1,
            "disable never auto-reenrolls cancelled output");
    }

    private static void meToggleDuringDelivery() {
        Host h = new Host();
        h.scene = new NekoOutputScene(h.ledger, h::get, h.queue);
        h.scene.setPhysicsAllowed(false);
        h.deliver(h.enqueue(8), 0);
        h.scene.frame(-1);
        h.queue.enqueueExisting(100, 100000);
        check(
            h.queue.size() == 1 && h.queue.phase(0, 100000) == 0,
            "toggle enrolls WAIT_DOOR without starting or dimming it");
        h.queue.tick(200000);
        check(
            h.injected == 0 && h.queue.phase(0, 200000) == 0,
            "hidden work remains owned and cannot time out into ME");
        for (int tick = 1; tick <= NekoOutputScene.DOOR_TICKS; tick++) h.scene.tick(tick, 300000 + tick * 50);
        long visibleAt = 300000 + NekoOutputScene.DOOR_TICKS * 50;
        check(
            h.queue.phase(0, visibleAt) == 1 && h.queue.phaseAgeMs(0, visibleAt) == 0,
            "in-flight toggle eventually starts from actual visibility");
        h.queue.tick(visibleAt + 4999);
        check(h.injected == 0, "in-flight enrollment still gives the full hold and fade");
        h.queue.tick(visibleAt + 5000);
        check(h.injected == 8 && h.slots[0] == null, "in-flight enrollment is eventually delivered exactly once");
    }

    private static void meSharedBodyRoundAndRecovery() {
        Host h = new Host();
        h.ledger.beginBatch(3);
        for (int slot = 0; slot < 3; slot++) h.deliver(h.enqueue(10), slot);
        h.ledger.beginBatch(1);
        h.deliver(h.enqueue(10), 3);
        long now = 100000;
        h.queue.enqueueExisting(100, now);
        NBTTagList jobs = h.queue.writeToNBT(now);
        check(
            h.dropId(0) == h.dropId(2) && h.generation(0) != h.generation(2),
            "one body retains distinct slot-generation guards");
        for (int slot = 0; slot < 3; slot++) check(
            jobs.getCompoundTagAt(slot)
                .getLong("creationTime") == now,
            "all stacks of one visual body share the same hold and fade");
        check(
            jobs.getCompoundTagAt(3)
                .getLong("creationTime") == now + 100,
            "only distinct visual bodies are staggered");
        for (int slot = 0; slot < 3; slot++)
            check(h.queue.phase(slot, now + 3000) == 2, "shared body starts its coherent fade");
        h.accepted = 4;
        h.queue.tick(now + 5000);
        check(
            h.injected == 4 && h.slots[0].stackSize == 6 && ownedQuantity(h) == 40,
            "one partial network operation conserves shared-body inventory");
        for (int slot = 0; slot < 3; slot++) check(
            h.queue.phase(slot, now + 5000) == 1 && h.queue.phaseAgeMs(slot, now + 5000) == 0,
            "partial acceptance makes every remaining shared member visible together");
        h.accepted = Integer.MAX_VALUE;
        h.queue.tick(now + 5100);
        check(h.slots[3] == null && h.injectCalls == 2, "restarted group does not block another ready body");
        h.queue.tick(now + 10000);
        check(
            h.slots[0] == null && h.slots[1] != null && h.slots[2] != null,
            "shared body drains only one real slot per tick");
        h.accept = false;
        h.queue.tick(now + 10050);
        check(
            h.queue.phase(1, now + 10050) == 1 && h.queue.phase(2, now + 10050) == 1,
            "a failed member restores the entire remaining group visibly");
        h.accept = true;
        h.queue.tick(now + 15049);
        check(h.slots[1] != null && h.slots[2] != null, "group retry receives a complete new hold and fade");
        h.queue.tick(now + 15050);
        h.queue.tick(now + 15100);
        check(
            h.queue.isEmpty() && h.injected == 40 && ownedQuantity(h) == 40,
            "group eventually drains exactly once after partial and rejected rounds");
    }

    private static void meLateSharedBodyMember() {
        for (boolean hiddenUntilEvent : new boolean[] { false, true }) {
            Host h = new Host();
            h.ledger.beginBatch(2);
            ItemStack first = h.enqueue(10), late = h.enqueue(10);
            h.deliver(first, 0);
            long now = 100000;
            h.queue.enqueueExisting(100, now);
            h.queue.tick(now + 4999);
            h.deliver(late, 1);
            h.visible = !hiddenUntilEvent;
            h.queue.enqueueIfMeMode(h.slots[1], 1, now + 5000);
            if (hiddenUntilEvent) {
                check(h.queue.phase(1, now + 5000) == 0, "late hidden member waits for actual visibility");
                h.visible = true;
                h.queue.visible(h.dropId(1), now + 5000);
            }
            for (int slot = 0; slot < 2; slot++) check(
                h.queue.phase(slot, now + 5000) == 1 && h.queue.phaseAgeMs(slot, now + 5000) == 0,
                "new visible member restarts its surviving shared body coherently");
            h.queue.enqueueExisting(100, now + 6000);
            check(h.queue.phaseAgeMs(0, now + 6000) == 1000, "duplicate admission still does not restart the group");
            h.queue.tick(now + 9999);
            check(h.injected == 0 && ownedQuantity(h) == 20, "late member cannot inherit an old completed fade");
            h.queue.tick(now + 10000);
            check(
                h.injected == 10 && h.queue.size() == 1,
                "late group starts bounded draining only after its new full hold and fade");
            h.queue.tick(now + 10050);
            check(h.injected == 20 && h.queue.isEmpty(), "late shared member eventually transfers exactly once");
        }
    }

    private static void meClockRollbackAndLegacyFuture() {
        Host h = new Host();
        for (int slot = 0; slot < 3; slot++) h.deliver(h.enqueue(10), slot);
        h.queue.enqueueExisting(100, 100000);
        h.queue.tick(100100);
        h.queue.tick(90000);
        NBTTagList jobs = h.queue.writeToNBT(90000);
        for (int slot = 0; slot < 3; slot++) check(
            jobs.getCompoundTagAt(slot)
                .getLong("creationTime") == 90000 + slot * 100,
            "backwards wall clock restarts bounded staggered rounds rather than stranding jobs");
        h.queue.tick(95000);
        check(h.injected == 10 && ownedQuantity(h) == 30, "rollback retains the full hold and fade and real ownership");

        Host legacy = new Host();
        legacy.deliver(legacy.enqueue(7), 0);
        NBTTagCompound entry = new NBTTagCompound();
        legacy.slots[0].writeToNBT(entry);
        entry.setInteger("slotIndex", 0);
        entry.setLong("creationTime", Long.MAX_VALUE);
        NBTTagList old = new NBTTagList();
        old.appendTag(entry);
        legacy.queue.readFromNBT(old, 100000);
        legacy.queue.tick(104999);
        check(legacy.injected == 0, "legacy future clock is restarted, not instantly transferred");
        legacy.queue.tick(105000);
        check(
            legacy.injected == 7 && legacy.slots[0] == null,
            "legacy future timestamp cannot strand owned output forever");
    }

    private static void persistentEnqueueIdentity() {
        Host h = new Host();
        h.ledger.beginBatch(2);
        ItemStack a = h.enqueue(64), b = h.enqueue(12);
        h.deliver(a, 0);
        long drop = h.dropId(0), generation = h.generation(0), pendingGeneration = h.ledger.pending(b).generation;
        NekoOutputLedger restored = new NekoOutputLedger();
        restored.restore(h.ledger.write(), h.slots, h.pending, Arrays.asList(h.ledger.pendingTag(b)));
        check(
            restored.slot(0).dropId == drop && restored.slot(0).generation == generation,
            "delivered identity persists");
        check(
            restored.pending(b).dropId == drop && restored.pending(b).generation == pendingGeneration,
            "ENQUEUE identity persists before delivery");
        restored.delivered(b, 1);
        check(
            restored.drops()
                .size() == 1
                && restored.drops()
                    .get(0).slots.size() == 2,
            "pending restore joins same body, not re-emission");
        Host routes = new Host();
        routes.ledger.beginBatch(2);
        ItemStack normal = routes.enqueue(1);
        ItemStack legendary = iron(1);
        routes.ledger.enqueue(legendary, true);
        check(
            !routes.ledger.pending(normal).rear && routes.ledger.pending(legendary).rear,
            "legendary rear is independent even for matching ordinary item");
        check(
            routes.ledger.pending(normal).dropId != routes.ledger.pending(legendary).dropId,
            "distinct routes do not move an existing normal body");
    }

    private static void twoPlayersAndReservations() {
        check(NekoOutputLedger.availableSlots(100, 100) == 0, "full paid buffer reserves every future slot");
        check(NekoOutputLedger.availableSlots(1, 2) == 0, "legacy overflow cannot oversell");
        Host h = new Host();
        h.ledger.beginBatch(2);
        ItemStack a = h.enqueue(64), b = h.enqueue(10);
        h.deliver(a, 0);
        h.deliver(b, 1);
        h.ledger.beginBatch(1);
        ItemStack c = h.enqueue(20);
        h.deliver(c, 2);
        long id = h.dropId(0);
        NekoOutputTake.Slots slots = new NekoOutputTake.Slots() {

            public ItemStack get(int i) {
                return h.get(i);
            }

            public void set(int i, ItemStack s) {
                h.set(i, s);
            }
        };
        int first = NekoOutputTake.take(h.ledger, slots, s -> s.stackSize, id, id, 1, 0);
        check(first == 47, "half applies to item+NBT group across independent batches");
        int second = NekoOutputTake.take(h.ledger, slots, s -> Math.min(3, s.stackSize), id, id, 2, 1000);
        check(second == 3, "second viewer clamps live slots and player acceptance");
        int third = NekoOutputTake.take(h.ledger, slots, s -> s.stackSize, id, id, 0, 0);
        check(first + second + third == 94, "two players conserve quantity without stale-frame duplication");
        check(
            NekoOutputTake.take(h.ledger, slots, s -> s.stackSize, id, id, 0, 0) == 0,
            "replayed drained identity rejected");
        ItemStack reused = h.enqueue(5);
        h.deliver(reused, 0);
        check(
            NekoOutputTake.take(h.ledger, slots, s -> s.stackSize, id, id, 0, 0) == 0 && h.slots[0].stackSize == 5,
            "stale take does not grab slot reuse");
        h.ledger.beginBatch(17);
        ItemStack rear = h.enqueue(1);
        check(h.ledger.pending(rear).rear, "over16 queued stacks routes whole batch rear");
        h.ledger.beginBatch(1);
        ItemStack normal = h.enqueue(1);
        check(!h.ledger.pending(normal).rear, "small later batch is independent");
    }

    private static void detachedBeforeFirstTick() {
        Host h = new Host();
        NBTTagList legacy = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        iron(7).writeToNBT(entry);
        entry.setLong("creationTime", 0);
        legacy.appendTag(entry); // Pre-1.6.23: no slotIndex, no corresponding output inventory stack.
        h.queue.readFromNBT(legacy);
        check(h.queue.isEmpty() && h.pending.size() == 1, "detached legacy stack migrates at load, before formed tick");
        check(h.injected == 0, "load migration never injects into ME");
        h.queue.preserveDetached();
        h.queue.preserveDetached();
        int droppedOnRemoval = 0;
        for (ItemStack pending : new ArrayList<>(h.pending)) {
            droppedOnRemoval += pending.stackSize;
            h.ledger.cancel(pending);
            h.pending.remove(pending);
        }
        check(
            droppedOnRemoval == 7 && h.queue.isEmpty() && h.pending.isEmpty(),
            "breaking unformed controller before its first tick returns legacy paid stack exactly once");
    }

    private static void restoredCounterHardening() {
        Host h = new Host();
        ItemStack existing = h.enqueue(7);
        h.deliver(existing, 0);
        long oldDrop = h.dropId(0), oldGeneration = h.generation(0);
        for (long malformed : new long[] { Long.MAX_VALUE, Long.MIN_VALUE, 0, -1, Long.MAX_VALUE - 1000 }) {
            NBTTagCompound saved = h.ledger.write();
            saved.setLong("nextBatch", malformed);
            saved.setLong("nextDrop", malformed);
            saved.setLong("nextGeneration", malformed);
            NekoOutputLedger restored = new NekoOutputLedger();
            restored.restore(saved, h.slots, new ArrayList<ItemStack>(), new ArrayList<NBTTagCompound>());
            NekoOutputLedger.Token a = restored.enqueue(iron(2), false);
            NekoOutputLedger.Token b = restored.enqueue(iron(3), false);
            check(
                a.batchId > 0 && a.dropId > oldDrop
                    && a.generation > oldGeneration
                    && b.batchId > a.batchId
                    && b.dropId > a.dropId
                    && b.generation > a.generation,
                "malformed root counters cannot overflow or reuse output identity: " + malformed);
            check(
                restored.slot(0).dropId == oldDrop && restored.slot(0).generation == oldGeneration
                    && h.slots[0].stackSize == 7,
                "counter repair preserves valid identities and sole inventory");
        }
        NBTTagCompound saved = h.ledger.write();
        saved.setLong("nextBatch", 700);
        saved.setLong("nextDrop", 800);
        saved.setLong("nextGeneration", 900);
        NekoOutputLedger restored = new NekoOutputLedger();
        restored.restore(saved, h.slots, new ArrayList<ItemStack>(), new ArrayList<NBTTagCompound>());
        NekoOutputLedger.Token fresh = restored.enqueue(iron(1), false);
        check(
            fresh.batchId == 700 && fresh.dropId == 800 && fresh.generation == 900,
            "valid larger counters persist even when previous high-water identities were removed");
    }

    private static void cancelledUnformedFade() {
        Host h = new Host();
        ItemStack item = h.enqueue(9);
        h.deliver(item, 0);
        long drop = h.dropId(0), generation = h.generation(0);
        long start = h.queueNow(0);
        h.queue.tick(start + 4000);
        check(h.queue.phase(0, start + 4000) == 2 && h.injected == 0, "ME fade pending before structure loss");
        // The machine unformed branch must call this without tick/inject, even with a still-live uplink.
        h.queue.cancelAll();
        NekoOutputScene scene = new NekoOutputScene(h.ledger, h::get, h.queue);
        check(
            h.queue.isEmpty() && h.queue.phase(0, start + 4000) == 0 && h.resets > 0,
            "unformed cancellation restores opacity instead of leaving a permanently faded job");
        check(
            firstBody(scene).getInteger("phase") == 0 && firstBody(scene).getInteger("quantity") == 9,
            "server presentation after unformed cancellation retains full local quantity");
        h.queue.tick(start + 10000);
        check(
            h.injected == 0 && h.slots[0].stackSize == 9
                && h.dropId(0) == drop
                && h.generation(0) == generation
                && h.pending.isEmpty(),
            "unformed cancellation never injects, moves, duplicates, or changes identity");
    }

    private static void remoteAuthorization() {
        Object target = new Object();
        Object anotherMachine = new Object();
        check(
            NekoOutputAccess.permits(target, true, false, () -> target),
            "out-of-range/cross-world request accepts exact server-validated remote session proof");
        check(
            !NekoOutputAccess.permits(target, true, false, () -> null),
            "forged distant request without live remote session denied");
        check(
            !NekoOutputAccess.permits(target, true, false, () -> anotherMachine),
            "remote session for another machine cannot authorize target");
        check(
            !NekoOutputAccess.permits(target, false, true, () -> target),
            "dead/client/inactive request cannot bypass server guard even with remote proof");
        check(
            NekoOutputAccess
                .permits(target, true, true, () -> { throw new AssertionError("local need not resolve remote"); }),
            "authorized local path remains compatible");
        java.util.concurrent.atomic.AtomicReference<Object> liveSession = new java.util.concurrent.atomic.AtomicReference<>(
            target);
        check(NekoOutputAccess.permits(target, true, false, liveSession::get), "live proof accepted");
        liveSession.set(null);
        check(
            !NekoOutputAccess.permits(target, true, false, liveSession::get),
            "closed or replaced session is revalidated and denied");
    }

    private static void paidQueueCapacityAndDelay() {
        final int[] empty = { 100 }, pending = { 0 };
        NekoOutputCapacity capacity = new NekoOutputCapacity(() -> empty[0], () -> pending[0]);
        NekoOutputCapacity.Reservation first = capacity.reserve(60);
        check(first != null && capacity.available() == 40, "reservation blocks other buyers before payment");
        check(capacity.reserve(41) == null, "second draw cannot overbook reserved output");
        first.close();
        first.close();
        check(capacity.available() == 100, "failed payment releases capacity exactly once");
        try (NekoOutputCapacity.Reservation actual = capacity.reserve(30)) {
            pending[0] += 17;
            actual.consume(17);
            check(capacity.available() == 70, "reserved capacity converts into real pending ownership");
        }
        check(capacity.available() == 83, "unused worst-case reservation released");
        empty[0] = 17;
        check(capacity.available() == 0 && capacity.reserve(1) == null, "full queue refuses before charging");
        check(capacity.reserve(-1) == null, "negative reservation rejected");
        NekoOutputCapacity other = new NekoOutputCapacity(() -> 100, () -> 0);
        check(!other.owns(first), "cross-machine reservation rejected");

        Host host = new Host();
        ItemStack paid = host.enqueue(7);
        long now = System.currentTimeMillis();
        host.ledger.delayPending(paid, now + 4000);
        check(
            !host.ledger.isReady(paid, now + 3999) && host.ledger.isReady(paid, now + 4000),
            "animation delays delivery, never queue ownership");
        NBTTagCompound token = host.ledger.pendingTag(paid);
        check(token.getLong("readyRemainingMs") > 0, "pending animation timer is persisted");
        NekoOutputLedger restored = new NekoOutputLedger();
        restored.restore(host.ledger.write(), host.slots, host.pending, Arrays.asList(token));
        check(
            restored.pending(paid) != null && !restored.isReady(paid, now),
            "reload keeps paid reward and delivery delay without a volatile reward task");
        check(
            host.pending.size() == 1 && host.pending.get(0).stackSize == 7,
            "delay metadata is not another inventory");
    }

    private static void continuousPaidQueueBackpressure() {
        Host host = new Host();
        NekoOutputCapacity capacity = new NekoOutputCapacity(
            () -> (int) Arrays.stream(host.slots)
                .filter(stack -> stack == null)
                .count(),
            host.pending::size);
        int charged = 0;
        long ready = System.currentTimeMillis() + 1550;
        for (int draw = 0; draw < 10; draw++) {
            try (NekoOutputCapacity.Reservation reservation = capacity.reserve(10)) {
                check(reservation != null, "ten sequential ten-draws fit exactly one hundred slots");
                charged++;
                host.ledger.beginBatch(10);
                for (int prize = 0; prize < 10; prize++) {
                    ItemStack stack = host.enqueue(1);
                    host.ledger.delayPending(stack, ready);
                    reservation.consume(1);
                }
            }
            host.ledger.routeWaitingToRear(id -> true);
        }
        check(capacity.reserve(10) == null && charged == 10, "eleventh draw refused before payment");
        check(
            host.pending.size() == 100 && host.ledger.drops()
                .size() == 10,
            "all paid prizes remain owned by queue, without display copies counting as inventory");
        List<NBTTagCompound> identities = new ArrayList<>();
        for (ItemStack stack : host.pending) identities.add(host.ledger.pendingTag(stack));
        NekoOutputLedger reloaded = new NekoOutputLedger();
        reloaded.restore(host.ledger.write(), host.slots, host.pending, identities);
        for (ItemStack stack : host.pending) check(
            reloaded.pending(stack).rear && stack.stackSize == 1,
            "reload retains every paid prize and rear routing");
        for (int i = 0; i < 100; i++) host.deliver(host.pending.get(0), i);
        int quantity = 0;
        for (ItemStack stack : host.slots) quantity += stack.stackSize;
        check(quantity == 100 && host.pending.isEmpty(), "paid queue moves exactly once into real output slots");
        check(capacity.available() == 0, "delivery does not free capacity until real inventory is removed");
    }

    private static void continuousWaitingRearRoute() {
        Host h = new Host();
        h.ledger.beginBatch(10);
        for (int i = 0; i < 10; i++) h.enqueue(1);
        h.ledger.beginBatch(6);
        for (int i = 0; i < 6; i++) h.enqueue(1);
        check(!h.ledger.routeWaitingToRear(id -> true), "exactly sixteen waiting stacks stays side delivery");
        h.ledger.beginBatch(1);
        h.enqueue(1);
        check(h.ledger.routeWaitingToRear(id -> true), "separate draws aggregate seventeen waiting stacks");
        for (ItemStack item : h.pending) check(h.ledger.pending(item).rear, "all queued batches promoted together");
        check(
            h.ledger.drops()
                .size() == 3,
            "threshold uses seventeen stacks, not three merged visual bodies");
        check(!h.ledger.routeWaitingToRear(id -> true), "routing is idempotent");

        Host settled = new Host();
        for (int i = 0; i < 20; i++) settled.deliver(settled.enqueue(1), i);
        settled.enqueue(64);
        check(
            !settled.ledger.routeWaitingToRear(id -> false),
            "settled outputs and sixty-four item quantity do not count");
        for (int i = 0; i < 15; i++) settled.enqueue(1);
        check(
            settled.ledger.routeWaitingToRear(id -> id == settled.dropId(0)),
            "slotted WAIT_DOOR stack plus sixteen pending stacks crosses threshold");
        check(!settled.ledger.slot(1).rear, "already visible output is never rerouted");
    }

    private static void nativeBlockShapeMigration() {
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[] { Blocks.stone, Blocks.glass,
            Blocks.diamond_block, Blocks.fence, Blocks.chest })
            check(NekoOutputModelShape.nativeBlock(new ItemStack(block)), "real native block remains volumetric");
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[] { Blocks.torch, Blocks.sapling,
            Blocks.red_flower, Blocks.yellow_flower })
            check(!NekoOutputModelShape.volumetric(new ItemStack(block)), "vanilla inventory sprite remains thin");
        Host host = new Host();
        for (int i = 0; i < 2; i++) {
            ItemStack block = new ItemStack(Blocks.diamond_block, 3);
            host.ledger.enqueue(block, false);
            host.pending.add(block);
            host.deliver(block, i);
        }
        host.deliver(host.enqueue(2), 2);
        ItemStack torch = new ItemStack(Blocks.torch, 4);
        host.ledger.enqueue(torch, false);
        host.pending.add(torch);
        host.deliver(torch, 3);
        NekoOutputScene scene = new NekoOutputScene(host.ledger, host::get, host.queue);
        NBTTagCompound old = scene.write();
        NBTTagList list = old.getTagList("bodies", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound body = list.getCompoundTagAt(i);
            body.setInteger("modelShapeVersion", 1);
            body.setInteger("deliveryStage", NekoOutputScene.RELEASED);
            body.setInteger("deliveryAgeTicks", 0);
            body.setBoolean("sleeping", true);
            body.setDouble("x", i == 0 ? 4.78 : i);
            body.setDouble("y", i == 0 ? .22 : 2);
            body.setDouble("z", i == 0 ? -2.78 : 0);
            if (i < 2 || i == 3) {
                body.setDouble("hx", .22);
                body.setDouble("hy", .22);
                body.setDouble("hz", .22);
            }
            body.setDouble("qx", 0);
            body.setDouble("qy", i == 1 ? Math.sin(.3) : 0);
            body.setDouble("qz", 0);
            body.setDouble("qw", i == 1 ? Math.cos(.3) : 1);
        }
        scene.restore(old);
        NBTTagCompound resized = scene.write();
        check(
            !scene.isPhysicsActive() && resized.getBoolean("migrationSettlePending"),
            "enlarged native blocks record one owner event but restore remains OFF");
        NBTTagList actual = resized.getTagList("bodies", 10);
        for (int i = 0; i < actual.tagCount(); i++) {
            NBTTagCompound before = list.getCompoundTagAt(i), after = actual.getCompoundTagAt(i);
            check(
                after.getDouble("hx") == (i < 2 ? .39 : .27) && after.getDouble("hz") == (i < 2 ? .39 : .027),
                "only native blocks enlarge");
            for (String key : new String[] { "id", "route", "deliveryStage", "deliveryAgeTicks", "qx", "qy", "qz", "qw",
                "vx", "vy", "vz", "wx", "wy", "wz", "sleeping", "quietTicks" })
                check(
                    before.getTag(key)
                        .equals(after.getTag(key)),
                    "block migration preserves " + key);
            if (i == 0) check(
                after.getDouble("x") == 5 - .39 && after.getDouble("y") == .39 && after.getDouble("z") == -3 + .39,
                "corner block receives minimum room correction");
            else for (String key : new String[] { "x", "y", "z" })
                check(before.getDouble(key) == after.getDouble(key), "valid block/sprite pose remains exact");
        }
        scene.tick(1);
        check(
            !scene.write()
                .getBoolean("migrationSettlePending"),
            "allowed owner tick consumes block resize event");
        for (int t = 2; t < 2400 && scene.isPhysicsActive(); t++) scene.tick(t);
        check(!scene.isPhysicsActive(), "resized native blocks settle finitely");
        NekoOutputScene modern = new NekoOutputScene(host.ledger, host::get, host.queue);
        modern.restore(scene.write());
        check(
            !modern.isPhysicsActive() && !modern.write()
                .getBoolean("migrationSettlePending"),
            "version-two block shape does not repeat migration");
        old.setBoolean("physicsAllowed", false);
        scene.restore(old);
        long tick = scene.write()
            .getLong("tick");
        for (int t = 1; t <= 8; t++) scene.tick(t);
        check(
            !scene.isPhysicsActive() && scene.write()
                .getLong("tick") == tick
                && scene.write()
                    .getBoolean("migrationSettlePending"),
            "disabled physics retains migration without stepping");
        check(
            host.slots[0].stackSize == 3 && host.slots[1].stackSize == 3
                && host.slots[2].stackSize == 2
                && host.slots[3].stackSize == 4,
            "shape migration never changes actual output quantity");
    }

    private static void legacyShapeSettlement() {
        check(NekoOutputModelShape.volumetric(new ItemStack(Blocks.glass)), "native glass uses a volume proxy");
        check(!NekoOutputModelShape.volumetric(iron(1)), "ordinary ingot uses a thin proxy");
        Host host = new Host();
        for (int i = 0; i < 100; i++) host.deliver(host.enqueue(1), i);
        NekoOutputScene scene = new NekoOutputScene(host.ledger, host::get, host.queue);
        NBTTagCompound old = scene.write();
        old.removeTag("migrationSettlePending");
        NBTTagList oldBodies = old.getTagList("bodies", 10);
        for (int i = 0; i < oldBodies.tagCount(); i++) {
            NBTTagCompound body = oldBodies.getCompoundTagAt(i);
            body.removeTag("modelShapeVersion");
            body.setInteger("deliveryStage", NekoOutputScene.RELEASED);
            body.setInteger("deliveryAgeTicks", 0);
            body.setDouble("hx", .22);
            body.setDouble("hy", .22);
            body.setDouble("hz", .22);
            body.setDouble("x", (i % 10 - 4.5) * .65);
            body.setDouble("y", i == 99 ? 6.8 : .22 + (i / 10) * .65);
            body.setDouble("z", 0);
            for (String key : new String[] { "qx", "qy", "qz", "vx", "vy", "vz", "wx", "wy", "wz" })
                body.setDouble(key, 0);
            body.setDouble("qw", 1);
            body.setBoolean("sleeping", true);
            body.setInteger("quietTicks", 20);
        }
        scene.restore(old);
        check(!scene.isPhysicsActive(), "legacy restore itself remains OFF");
        NBTTagCompound loaded = scene.write();
        check(loaded.getBoolean("migrationSettlePending"), "legacy shape change records one owner event");
        NBTTagList loadedBodies = loaded.getTagList("bodies", 10);
        for (int i = 0; i < 100; i++) {
            NBTTagCompound a = oldBodies.getCompoundTagAt(i), b = loadedBodies.getCompoundTagAt(i);
            check(
                a.getString("id")
                    .equals(b.getString("id")) && a.getDouble("x") == b.getDouble("x")
                    && a.getDouble("y") == b.getDouble("y")
                    && a.getDouble("z") == b.getDouble("z")
                    && b.getDouble("qw") == 1
                    && b.getBoolean("sleeping"),
                "migration initially preserves exact old pose");
            check(b.getDouble("hz") < b.getDouble("hx") / 5, "legacy ordinary item acquires thin shape");
        }
        check(
            loadedBodies.getCompoundTagAt(99)
                .getDouble("y") == 6.8,
            "high legacy pose is not culled or clamped");
        scene.tick(1, 50);
        check(
            scene.isPhysicsActive() && !scene.write()
                .getBoolean("migrationSettlePending"),
            "normal owner tick consumes allowed migration event exactly once");
        for (int t = 2; t < 1000 && scene.isPhysicsActive(); t++) scene.tick(t, t * 50L);
        check(!scene.isPhysicsActive(), "legacy 100-body migration settles finitely");
        for (int i = 0; i < 100; i++) check(host.slots[i].stackSize == 1, "migration never changes real inventory");
        NBTTagCompound settled = scene.write();
        for (int i = 0; i < 100; i++) {
            NBTTagCompound b = settled.getTagList("bodies", 10)
                .getCompoundTagAt(i);
            check(
                b.getInteger("deliveryStage") == NekoOutputScene.RELEASED && b.getBoolean("sleeping")
                    && b.getDouble("y") < 3,
                "old floating column settles without re-emission");
        }
        NekoOutputScene modern = new NekoOutputScene(host.ledger, host::get, host.queue);
        modern.restore(settled);
        for (int t = 1000; t < 1020; t++) modern.tick(t, t * 50L);
        check(
            !modern.isPhysicsActive() && modern.write()
                .getLong("tick") == settled.getLong("tick"),
            "modern settled load never activates or repeats migration");

        old.setBoolean("physicsAllowed", false);
        scene.restore(old);
        for (int t = 1; t < 20; t++) scene.tick(t, t * 50L);
        check(
            !scene.isPhysicsActive() && scene.write()
                .getLong("tick") == old.getLong("tick"),
            "disabled physics never processes migration work");
        NekoOutputScene disabledReload = new NekoOutputScene(host.ledger, host::get, host.queue);
        disabledReload.restore(scene.write());
        check(
            disabledReload.write()
                .getBoolean("migrationSettlePending") && !disabledReload.isPhysicsActive(),
            "pending owner event survives disabled save and load");
        disabledReload.setPhysicsAllowed(true);
        check(!disabledReload.isPhysicsActive(), "allowing asleep migration still waits for owner tick");
        disabledReload.tick(20, 1000);
        check(
            disabledReload.isPhysicsActive() && !disabledReload.write()
                .getBoolean("migrationSettlePending"),
            "explicitly allowed owner event starts on next tick");
    }

    private static void partialVisibleBatchRearSplit() throws Exception {
        for (int visibleStage : new int[] { NekoOutputScene.FEED, NekoOutputScene.RELEASED }) {
            Host h = new Host();
            h.ledger.beginBatch(3);
            ItemStack visible = h.enqueue(64), pendingA = h.enqueue(20), pendingB = h.enqueue(5);
            long originalDrop = h.ledger.pending(visible).dropId;
            long pendingGeneration = h.ledger.pending(pendingA).generation;
            long readyAt = System.currentTimeMillis() + 3000;
            h.ledger.delayPending(pendingA, readyAt);
            h.deliver(visible, 0);
            long originalGeneration = h.generation(0);
            NekoOutputScene scene = new NekoOutputScene(h.ledger, h::get, h.queue);
            h.scene = scene;
            scene.setPhysicsAllowed(false);
            int ticks = NekoOutputScene.DOOR_TICKS
                + (visibleStage == NekoOutputScene.RELEASED ? NekoOutputScene.FEED_TICKS : 0);
            for (int tick = 1; tick <= ticks; tick++) scene.tick(tick, 1000);
            com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.Entry before = com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames
                .decode(com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.encode(scene.frame(-1))).entries
                    .get(Long.toString(originalDrop));
            check(
                before.deliveryStage == visibleStage && !before.rear && before.route != 2,
                "partial batch first stack is already on a visible side route");
            h.ledger.beginBatch(15);
            for (int i = 0; i < 15; i++) h.enqueue(1);
            // Two pending stacks from the visible group + fifteen newer stacks cross the real queue threshold.
            com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.Frame promoted = com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames
                .decode(com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.encode(scene.frame(-1)));
            com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.Entry stillVisible = promoted.entries
                .get(Long.toString(originalDrop));
            check(
                !stillVisible.rear && stillVisible.route == before.route && stillVisible.quantity == 64,
                "backlog promotion preserves visible side route and only its real quantity");
            check(
                stillVisible.pose.x == before.pose.x && stillVisible.pose.y == before.pose.y
                    && stillVisible.pose.z == before.pose.z
                    && stillVisible.pose.qw == before.pose.qw,
                "metadata split never teleports or rotates the existing visible body");
            check(
                h.dropId(0) == originalDrop && h.generation(0) == originalGeneration && !h.ledger.slot(0).rear,
                "existing slot drop and generation are unchanged");
            NekoOutputLedger.Token moved = h.ledger.pending(pendingA);
            check(
                moved.dropId != originalDrop && moved.dropId == h.ledger.pending(pendingB).dropId
                    && moved.rear
                    && moved.generation != pendingGeneration
                    && moved.readyAtMs == readyAt,
                "only pending portion gets one rear identity with fresh generation and preserved release clock");
            check(!h.ledger.routeWaitingToRear(scene.waitingDropIds()::contains), "promotion is idempotent");
            h.deliver(pendingA, 1);
            h.deliver(pendingB, 2);
            com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.Frame delivered = com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames
                .decode(com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.encode(scene.frame(-1)));
            com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.Entry rear = delivered.entries
                .get(Long.toString(moved.dropId));
            check(
                delivered.entries.size() == 2 && rear.rear && rear.route == 2 && rear.quantity == 25,
                "new rear portion has exactly its two real slots and the whole wire frame remains valid");
            int quantity = 0;
            for (ItemStack stack : h.slots) if (stack != null) quantity += stack.stackSize;
            for (ItemStack stack : h.pending) quantity += stack.stackSize;
            check(quantity == 104 && h.pending.size() == 15, "route split neither duplicates nor loses real inventory");
            List<NBTTagCompound> tokens = new ArrayList<>();
            for (ItemStack stack : h.pending) tokens.add(h.ledger.pendingTag(stack));
            NekoOutputLedger reloaded = new NekoOutputLedger();
            reloaded.restore(h.ledger.write(), h.slots, h.pending, tokens);
            check(
                reloaded.slot(0).dropId == originalDrop && reloaded.slot(0).generation == originalGeneration
                    && !reloaded.slot(0).rear
                    && reloaded.slot(1).dropId == moved.dropId
                    && reloaded.slot(1).rear,
                "split identities and old visible generation persist across reload");
            NekoOutputScene reopened = new NekoOutputScene(reloaded, h::get, h.queue);
            reopened.restore(scene.write());
            check(
                com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames
                    .decode(com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.encode(reopened.frame(-1))).entries
                        .size()
                    == 2,
                "reloaded split group still passes the real client frame codec");
        }
    }

    public static void main(String[] args) throws Exception {
        if (!com.miaokatze.gtit.testutil.MinecraftTestBootstrap.enter(NekoOutputStateTest.class, args)) return;
        migration();
        identityAndSnapshots();
        legacyShapeSettlement();
        nativeBlockShapeMigration();
        meRaces();
        meExistingOutputStaggerAndBudget();
        meStaggerPersistenceAndFailures();
        meToggleDuringDelivery();
        meSharedBodyRoundAndRecovery();
        meLateSharedBodyMember();
        meClockRollbackAndLegacyFuture();
        authoritativeDelivery();
        meVisibleClockPersistence();
        twoPlayersAndReservations();
        persistentEnqueueIdentity();
        detachedBeforeFirstTick();
        remoteAuthorization();
        restoredCounterHardening();
        cancelledUnformedFade();
        paidQueueCapacityAndDelay();
        continuousWaitingRearRoute();
        continuousPaidQueueBackpressure();
        partialVisibleBatchRearSplit();
        System.out.println("NekoOutputStateTest PASS");
    }
}
