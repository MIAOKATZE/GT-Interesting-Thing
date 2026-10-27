package com.miaokatze.gtit.common.machine.v2;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        final NekoMeTransferQueue queue = new NekoMeTransferQueue(this, this, () -> {}, () -> true);
        NekoOutputScene scene;
        boolean lost, accept = true;
        int accepted = Integer.MAX_VALUE, injected, resets;

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
            return scene == null || scene.isVisible(dropId(slot));
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
            return accept;
        }

        public boolean isLost() {
            return lost;
        }

        public appeng.api.storage.data.IAEItemStack inject(ItemStack stack) {
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
        ItemStackHandler old = new ItemStackHandler(8);
        old.setStackInSlot(7, iron(9));
        ItemStackHandler upgraded = new ItemStackHandler(16);
        upgraded.deserializeNBT(NekoInventoryMigration.expand(old.serializeNBT(), 16));
        check(
            upgraded.getSlots() == 16 && upgraded.getStackInSlot(7).stackSize == 9,
            "old eight slots append to sixteen");
        upgraded.setStackInSlot(15, iron(11));
        ItemStackHandler round = new ItemStackHandler(1);
        round.deserializeNBT(NekoInventoryMigration.expand(upgraded.serializeNBT(), 16));
        check(round.getSlots() == 16 && round.getStackInSlot(15).stackSize == 11, "sixteen roundtrip");
        check(
            NekoInventoryMigration.outputIndex(8) == 0 && NekoInventoryMigration.outputIndex(107) == 99,
            "legacy output indices unchanged");
        check(
            NekoInventoryMigration.inputIndex(108) == 8 && NekoInventoryMigration.inputIndex(115) == 15,
            "appended input indices");
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
            Math.abs(feed.getDouble("x")) == 3.9 && feed.getDouble("y") == 3.0,
            "ordinary delivery emerges at side paw outlet, not random ceiling");
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

    public static void main(String[] args) throws Exception {
        if (!com.miaokatze.gtit.testutil.MinecraftTestBootstrap.enter(NekoOutputStateTest.class, args)) return;
        migration();
        identityAndSnapshots();
        meRaces();
        authoritativeDelivery();
        meVisibleClockPersistence();
        twoPlayersAndReservations();
        persistentEnqueueIdentity();
        detachedBeforeFirstTick();
        remoteAuthorization();
        restoredCounterHardening();
        cancelledUnformedFade();
        System.out.println("NekoOutputStateTest PASS");
    }
}
