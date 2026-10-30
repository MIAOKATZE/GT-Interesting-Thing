package com.miaokatze.gtit.gui.pocket;

import java.lang.reflect.Proxy;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.common.items.pocket.PocketCarrierIdentity;

/** Carrier selection must survive vanilla copies without granting writes to another pocket. */
public final class PocketCarrierIdentityTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void run() {
        Item item = new Item();
        ItemStack a = new ItemStack(item, 1);
        a.setTagCompound(new NBTTagCompound());
        a.getTagCompound()
            .setString("contentsMarker", "A");
        ItemStack b = new ItemStack(item, 1);
        PocketCarrierIdentity identity = PocketCarrierIdentity.capture(a, true);
        check(identity.matches(a), "opened carrier is authorized");
        check(!identity.matches(b), "same item is not the same carrier");
        check(
            "A".equals(
                a.getTagCompound()
                    .getString("contentsMarker")),
            "opening preserves existing NBT");

        ItemStack moved = a.copy();
        check(identity.matches(moved), "vanilla ItemStack copies retain session identity");
        check(
            identity.locate(b, null, new ItemStack[] { b, moved }, null).carrier == moved,
            "A-to-B replacement must resolve moved A, never overwrite B");
        check(
            identity.locate(b, null, new ItemStack[] { b }, null).carrier == null,
            "replacement without A fails closed");
        check(
            identity.locate(moved, null, new ItemStack[] { moved, b }, null).carrier == moved,
            "same reference seen through two paths is not ambiguity");
        check(
            identity.locate(moved, null, new ItemStack[] { moved, a.copy() }, null).ambiguous,
            "two physical stacks bearing the same token are ambiguous");
        check(
            identity.locate(b, moved, new ItemStack[] { b }, null).carrier == moved,
            "moving A to cursor retains its write target");

        final ItemStack[] worn = { b, moved };
        IInventory baubles = (IInventory) Proxy.newProxyInstance(
            IInventory.class.getClassLoader(),
            new Class<?>[] { IInventory.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getSizeInventory")) return worn.length;
                if (method.getName()
                    .equals("getStackInSlot")) return worn[(Integer) args[0]];
                throw new AssertionError("Unexpected Baubles operation " + method);
            });
        check(
            identity.locate(b, null, new ItemStack[] { b }, baubles).carrier == moved,
            "Baubles relocation selects only matching A, not the first same-type pocket");
        check(
            PocketCarrierIdentity.isHeld(moved, new ItemStack[] { b }, baubles),
            "onWornTick carrier is held and must survive another player's orphan sweep");
        check(
            !PocketCarrierIdentity.isHeld(moved, new ItemStack[] { b }, null),
            "cursor-only or departed carrier is not a ticked inventory");
        check(
            !PocketCarrierIdentity.isHeld(null, new ItemStack[] { b }, baubles),
            "ambiguous or revoked carrier cannot be kept alive merely by a same-type worn item");
        PocketCarrierIdentity reopened = PocketCarrierIdentity.capture(a, true);
        check(reopened.matches(a) && !identity.matches(a), "reopen revokes stale close/save on the same object");
        check(!reopened.matches(moved), "old-session copies cannot impersonate the new session");

        ItemStack client = new ItemStack(item, 1);
        PocketCarrierIdentity.capture(client, false);
        check(client.getTagCompound() == null, "client opening does not create an authoritative token");
        System.out.println("PocketCarrierIdentityTest: PASS (replacement, copies, ambiguity, Baubles, stale reopen)");
    }
}
