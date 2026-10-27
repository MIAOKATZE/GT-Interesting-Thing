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

    public static void main(String[] args) throws Exception {
        if (!com.miaokatze.gtit.testutil.MinecraftTestBootstrap.enter(VendingSceneContracts.class, args)) return;
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
        NBTTagCompound gates = frame(5, true, waiting);
        gates.setDouble("doorLeft", .5);
        check(view.accept(decode(gates), 2200000000L), "wait-door frame accepted");
        check(
            view.snapshot(2200000000L).bodies.isEmpty() && view.entries()
                .size() == 1,
            "queued item keeps ownership but is not rendered or pickable");
        check(view.door(0) == .5 && view.door(2) == 0, "authoritative route door openness");
        waiting.setInteger("deliveryStage", 1);
        gates = frame(6, true, waiting);
        gates.setDouble("doorLeft", 1);
        check(
            view.accept(decode(gates), 2300000000L) && view.snapshot(2300000000L).bodies.size() == 1,
            "feed visible after authoritative door open");
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
