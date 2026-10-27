package com.miaokatze.gtit.client.gui;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.gui.vm.edit.NekoGhostSlot;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

/** Isolated phantom-copy contract; does not open a GUI, player inventory, or world. */
public final class NekoGhostSlotTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(NekoGhostSlotTest.class, args)) return;
        ItemStack original = new ItemStack(Items.diamond, 41);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("source", "catalog");
        original.setTagCompound(tag);
        final ItemStack[] delivered = new ItemStack[1];
        final int[] mouse = new int[1];
        NekoGhostSlot slot = new NekoGhostSlot() {

            @Override
            public boolean handleDragAndDrop(ItemStack stack, int button) {
                delivered[0] = stack.copy();
                mouse[0] = button;
                // Match the real MUI phantom API, which consumes its argument, not an actual inventory item.
                stack.stackSize = 0;
                stack.getTagCompound()
                    .setString("source", "draft");
                return true;
            }
        };
        check(slot.acceptNeiGhost(original, 0), "left drop accepted");
        check(delivered[0] != original && delivered[0].stackSize == 41, "native quantity copied intact");
        check(
            "catalog".equals(
                delivered[0].getTagCompound()
                    .getString("source")),
            "NBT copied intact");
        check(
            original.stackSize == 41 && "catalog".equals(
                original.getTagCompound()
                    .getString("source")),
            "MUI consuming/relabeling a ghost cannot mutate catalog or real cursor source");
        check(
            slot.acceptNeiGhost(original, 2) && mouse[0] == 0,
            "middle selection normalizes to safe phantom left drop");
        check(slot.acceptNeiGhost(original, 1) && mouse[0] == 1, "right-drop phantom semantics retained");
        check(!slot.acceptNeiGhost(null, 0), "no null item operation");
        System.out.println("NekoGhostSlotTest: copy, quantity/NBT isolation and safe buttons passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
