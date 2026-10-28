package com.miaokatze.gtit.gui.vm.scene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.miaokatze.gtit.client.vending.scene.VendingSceneView;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;

/** Codec/ownership/lifecycle tests, deliberately no OpenGL context or simulation. */
public final class VendingSceneContracts {

    static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }

    static NBTTagCompound body(long id, int slot) {
        NBTTagCompound n = new NBTTagCompound();
        n.setString("id", Long.toString(id));
        n.setLong("dropId", id);
        n.setLong("batchId", 1);
        n.setLong("generation", id);
        n.setLong("seed", 42);
        n.setInteger("quantity", 5);
        n.setIntArray("slots", new int[] { slot });
        n.setDouble("hx", .22);
        n.setDouble("hy", .22);
        n.setDouble("hz", .22);
        n.setDouble("x", 0);
        n.setDouble("y", 1);
        n.setDouble("z", 0);
        n.setDouble("qw", 1);
        return n;
    }

    static NBTTagCompound frame(long rev, boolean full, NBTTagCompound... bodies) {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("schema", 1);
        n.setString("layout", VendingSceneFrames.LAYOUT);
        n.setLong("revision", rev);
        n.setLong("tick", rev);
        n.setBoolean("full", full);
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound b : bodies) list.appendTag(b);
        n.setTag("bodies", list);
        n.setTag("removed", new NBTTagList());
        return n;
    }

    static VendingSceneFrames.Frame decode(NBTTagCompound n) throws IOException {
        return VendingSceneFrames.decode(VendingSceneFrames.encode(n));
    }

    static void rejected(NBTTagCompound n) throws Exception {
        try {
            decode(n);
            throw new AssertionError("malformed frame accepted");
        } catch (IOException expected) {}
    }

    private static NBTTagCompound doorFrame(long revision, NBTTagCompound body, int route, double door) {
        NBTTagCompound n = frame(revision, true, body);
        n.setDouble(route == 0 ? "doorLeft" : route == 1 ? "doorRight" : "doorRear", door);
        return n;
    }

    private static void continuousEntrance(List<ItemSlot> slots) throws Exception {
        for (int route = 0; route < 3; route++) {
            VendingSceneView view = new VendingSceneView(slots);
            NBTTagCompound waiting = body(1, 0);
            waiting.setInteger("route", route);
            waiting.setBoolean("rear", route == 2);
            waiting.setInteger("deliveryStage", 0);
            waiting.setDouble("x", route == 0 ? -3.25 : route == 1 ? 3.25 : 0);
            waiting.setDouble("y", route == 2 ? .58 : 3);
            waiting.setDouble("z", route == 2 ? -2.65 : .5);
            waiting.setDouble("vx", route == 0 ? 3 : route == 1 ? -3 : 0);
            waiting.setDouble("vy", 2.5);
            waiting.setDouble("vz", route == 2 ? 2.5 : 0);
            check(view.accept(decode(doorFrame(1, waiting, route, 2.0 / 3)), 0), "waiting frame");
            NBTTagCompound launch = (NBTTagCompound) waiting.copy();
            launch.setInteger("deliveryStage", 1);
            check(view.accept(decode(doorFrame(2, launch, route, 1)), 200000000L), "launch frame");
            check(
                view.entries()
                    .size() == 1,
                "hidden launch retains its synchronized slot ownership");
            for (long now : new long[] { 200000000L, 250000000L, 399000000L })
                check(view.snapshot(now).bodies.isEmpty(), "launch sample stays hidden instead of hovering for 200ms");
            check(view.door(route, 400000000L) == 1, "client door finishes opening before first moving segment");
            SceneBody origin = decode(doorFrame(2, launch, route, 1)).entries.get("1").pose;
            NBTTagCompound feed = (NBTTagCompound) launch.copy();
            for (int segment = 1; segment <= 3; segment++) {
                double age = segment * .2;
                feed.setInteger("deliveryStage", segment == 3 ? 2 : 1);
                feed.setInteger("deliveryAgeTicks", segment == 3 ? 0 : segment * 4);
                feed.setDouble("x", origin.x + origin.vx * age);
                feed.setDouble("y", origin.y + origin.vy * age - .5 * 9.81 * age * age);
                feed.setDouble("z", origin.z + origin.vz * age);
                feed.setDouble("vy", origin.vy - 9.81 * age);
                long received = (segment + 1) * 200000000L;
                SceneBody before = segment == 1 ? origin : view.snapshot(received).bodies.get(0);
                check(view.accept(decode(doorFrame(segment + 2, feed, route, 1)), received), "moving sample");
                SceneBody first = view.snapshot(received).bodies.get(0);
                check(
                    Math.abs(first.x - before.x) < 1e-10 && Math.abs(first.y - before.y) < 1e-10
                        && Math.abs(first.z - before.z) < 1e-10,
                    "new segment and FEED/release handoff preserve displayed position");
                double position = route == 2 ? first.z : first.x;
                for (int sample = 1; sample <= 4; sample++) {
                    SceneBody next = view.snapshot(received + sample * 50000000L).bodies.get(0);
                    double nextPosition = route == 2 ? next.z : next.x;
                    check(
                        (nextPosition - position) * (route == 1 ? -1 : 1) > 0,
                        "every drawn segment continues inward/forward after spawn without a stationary interval");
                    position = nextPosition;
                }
            }
            SceneBody end = view.snapshot(1000000000L).bodies.get(0);
            check(
                view.snapshot(1200000000L).bodies.get(0).x == end.x
                    && view.snapshot(1200000000L).bodies.get(0).z == end.z,
                "presentation remains bounded by the last sample with no extrapolation");
            view.close();

            VendingSceneView reopened = new VendingSceneView(slots);
            reopened.accept(decode(doorFrame(1, launch, route, 1)), 0);
            check(reopened.snapshot(0).bodies.size() == 1, "full GUI reopen restores current FEED without replay");
            reopened.close();
        }
        VendingSceneView view = new VendingSceneView(slots);
        view.accept(decode(frame(1, true)), 0);
        NBTTagCompound launch = body(1, 0);
        launch.setInteger("deliveryStage", 1);
        view.accept(decode(frame(2, false, launch)), 200000000L);
        check(view.snapshot(250000000L).bodies.isEmpty(), "delta birth without WAIT also defers a stationary launch");
        view.paused = true;
        check(view.snapshot(350000000L).bodies.isEmpty(), "pause preserves hidden entrance");
        view.paused = false;
        view.reducedMotion = true;
        check(
            view.snapshot(350000000L).bodies.size() == 1,
            "reduced motion keeps immediate authoritative target semantics");
        view.close();
        System.out.println(
            "Continuous entrance: no launch hover, three door directions, segment/handoff continuity, bounded interpolation, reopen, pause and reduced motion passed");
    }

    public static void main(String[] args) throws Exception {
        if (!com.miaokatze.gtit.testutil.MinecraftTestBootstrap.enter(VendingSceneContracts.class, args)) return;
        VendingNativeModelPolicyTest.main(args);
        VendingScenePickingTest.main(args);
        VendingCatChoreographyTest.main(args);
        NBTTagCompound n = frame(1, true, body(1, 0));
        check(decode(n).entries.size() == 1, "full roundtrip");
        n.setInteger("schema", 2);
        rejected(n);
        n = frame(1, true, body(1, 0), body(1, 1));
        rejected(n);
        NBTTagCompound bad = body(1, 100);
        rejected(frame(1, true, bad));
        bad = body(1, 0);
        bad.setDouble("x", Double.NaN);
        rejected(frame(1, true, bad));
        bad = body(1, 0);
        bad.setDouble("qw", 2);
        rejected(frame(1, true, bad));
        bad = body(1, 0);
        bad.setDouble("y", 100);
        rejected(frame(1, true, bad));
        bad = body(1, 0);
        bad.setInteger("deliveryStage", 3);
        rejected(frame(1, true, bad));
        bad = body(1, 0);
        bad.setInteger("route", 2);
        rejected(frame(1, true, bad));
        NBTTagCompound badDoor = frame(1, true, body(1, 0));
        badDoor.setDouble("doorRear", Double.NaN);
        rejected(badDoor);
        badDoor.setDouble("doorRear", 1.1);
        rejected(badDoor);
        List<NBTTagCompound> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) many.add(body(i + 1, i % 100));
        rejected(frame(1, true, many.toArray(new NBTTagCompound[0])));
        try {
            VendingSceneFrames.decode(new byte[65537]);
            throw new AssertionError("wire cap");
        } catch (IOException expected) {}
        byte[] valid = VendingSceneFrames.encode(frame(1, true, body(1, 0)));
        try {
            VendingSceneFrames.decode(Arrays.copyOf(valid, valid.length + 1));
            throw new AssertionError("trailing byte");
        } catch (IOException expected) {}
        SceneBody a = new SceneBody("1", .2, .2, .2, -1, 1, 0),
            b = new SceneBody("1", .2, .2, .2, 1, 1, 0).withRotation(0, 0, 0, -1);
        SceneBody half = VendingSceneView.interpolate(a, b, .5);
        check(Math.abs(half.x) < 1e-9 && Math.abs(Math.abs(half.qw) - 1) < 1e-9, "shortest quaternion interpolation");
        Bootstrap.func_151354_b();
        ItemStackHandler inventory = new ItemStackHandler(100);
        inventory.setStackInSlot(0, new ItemStack(Items.iron_ingot, 5));
        inventory.setStackInSlot(1, new ItemStack(Items.iron_ingot, 7));
        List<ItemSlot> slots = new ArrayList<>();
        for (int i = 0; i < 100; i++) slots.add(new ItemSlot().slot(inventory, i));
        continuousEntrance(slots);
        VendingSceneView view = new VendingSceneView(slots);
        check(!view.accept(decode(frame(1, false, body(1, 0))), 0), "must open with full");
        check(view.accept(decode(frame(1, true, body(1, 0), body(2, 1))), 0), "full accepted");
        check(!view.cat.active(), "initial full snapshot does not replay old interactions");
        check(!view.accept(decode(frame(1, true, body(1, 0))), 1), "stale rejected");
        check(
            view.groups()
                .size() == 1 && view.quantity("1") == 12,
            "same item+NBT merged across bodies");
        view.select("1");
        check(view.highlighted("2"), "selection maps all matching bodies");
        check(!view.accept(decode(frame(2, false, body(3, 1))), 1), "duplicate slot ownership rejected atomically");
        check(
            view.entries()
                .size() == 2,
            "failed delta does not alter state");
        NBTTagCompound fade = body(1, 0);
        fade.setInteger("phase", 2);
        fade.setLong("phaseAgeMs", 1500);
        check(view.accept(decode(frame(3, false, fade)), 2000000000L), "fade delta");
        check(view.opacity("1", 2000000000L) < .3, "reopened phase uses server elapsed age");
        view.paused = true;
        view.snapshot(2000000000L);
        check(view.accept(decode(frame(4, false, body(1, 0))), 2100000000L), "pause does not stall frames");
        check(
            view.opacity("1", 2100000000L) == 1 && view.particles(2100000000L)
                .isEmpty(),
            "rejected/reset fade cancels opacity and particles");
        view.paused = false;
        NBTTagCompound waiting = body(1, 0);
        waiting.setInteger("deliveryStage", 0);
        waiting.setInteger("route", 0);
        waiting.setDouble("x", -4.3);
        NBTTagCompound gates = frame(5, true, waiting);
        gates.setDouble("doorLeft", .5);
        check(view.accept(decode(gates), 2200000000L), "wait-door frame accepted");
        check(
            view.snapshot(2200000000L).bodies.isEmpty() && view.entries()
                .size() == 1,
            "queued item keeps ownership but is not rendered or pickable");
        check(view.door(0) == 0 && view.door(2) == 0, "door target does not step at packet receipt");
        check(view.door(0, 2300000000L) == .25, "door interpolates between 5 Hz server frames");
        check(view.door(0, 2400000000L) == .5, "door converges exactly without extrapolation");
        waiting.setInteger("deliveryStage", 1);
        waiting.setDouble("x", -3.82);
        gates = frame(6, true, waiting);
        gates.setDouble("doorLeft", 1);
        check(
            view.accept(decode(gates), 2300000000L) && view.snapshot(2300000000L).bodies.size() == 1,
            "feed visible after authoritative door open");
        check(
            view.snapshot(2300000000L).bodies.get(0).x == -4.3,
            "WAIT to FEED begins inside the real shell, not at a jumped pose");
        check(
            Math.abs(view.snapshot(2400000000L).bodies.get(0).x + 4.06) < .00001,
            "door and feed travel share the same interpolation interval");
        view.snapshot(2300000000L);
        check(view.door(0) == .25, "new door segment starts at current interpolated pose");
        check(view.door(0, 2400000000L) == .625, "door stays smooth between updates");
        view.snapshot(2400000000L);
        view.paused = true;
        check(view.door(0, 2900000000L) == .625, "pause freezes door with body snapshot");
        view.paused = false;
        check(view.door(0, 2900000000L) == 1, "resume converges to authoritative door state");
        view.reducedMotion = true;
        gates = frame(7, true, waiting);
        gates.setDouble("doorLeft", .5);
        check(view.accept(decode(gates), 3000000000L), "reduced-motion door frame");
        check(view.door(0, 3000000000L) == .5, "reduced motion uses current server target");
        VendingSceneView reopened = new VendingSceneView(slots);
        check(reopened.accept(decode(gates), 3000000000L), "reopened full frame");
        check(reopened.door(0, 3000000000L) == .5, "reopen restores door without replaying opening");
        reopened.close();
        view.close();
        check(
            view.entries()
                .isEmpty(),
            "close releases state");
        check(!view.accept(decode(frame(5, true, body(1, 0))), 0), "closed view rejects frames");
        view.close();
        check(
            inventory.getStackInSlot(0).stackSize == 5 && inventory.getStackInSlot(1).stackSize == 7,
            "visual layer never mutates real stock");
        System.out.println(
            "VendingSceneContracts: codec, bounds, atomic ownership, interpolation, grouping, fade cancellation, pause and close passed");
    }
}
