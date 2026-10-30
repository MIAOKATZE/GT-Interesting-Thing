package com.miaokatze.gtit.common.items;

import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.rings.RingWindrider;

import baubles.api.BaublesApi;

/** Availability only: a depleted or removed GTIT source must not revoke another equipped source's flight. */
public final class FlightSourceSupport {

    private FlightSourceSupport() {}

    static boolean hasAvailableFlight(EntityPlayer player) {
        return hasAvailableFlight(player, null);
    }

    private static boolean hasAvailableFlight(EntityPlayer player, ItemStack departing) {
        return hasAvailableFlight(
            BaublesApi.getBaubles(player),
            player.getFoodStats()
                .getFoodLevel() >= 6,
            ElectricFloatCore::hasFlightPower,
            departing);
    }

    /** Baubles 2.2.6 calls before clearing the slot; newer removal paths may call after clearing it. */
    public static void onUnequipped(EntityPlayer player, ItemStack departing) {
        if (player.capabilities.isCreativeMode || player.worldObj.isRemote) return;
        if ((player.capabilities.allowFlying || player.capabilities.isFlying)
            && !hasAvailableFlight(player, departing)) {
            player.capabilities.allowFlying = false;
            player.capabilities.isFlying = false;
            player.sendPlayerAbilities();
        }
    }

    /** The power predicate is a read-only canUse probe, never an energy or hunger debit. */
    static boolean hasAvailableFlight(IInventory baubles, boolean hasFood, Predicate<ItemStack> hasPower) {
        return hasAvailableFlight(baubles, hasFood, hasPower, null);
    }

    static boolean hasAvailableFlight(IInventory baubles, boolean hasFood, Predicate<ItemStack> hasPower,
        ItemStack departing) {
        if (baubles == null) return false;
        for (int i = 0; i < baubles.getSizeInventory(); i++) {
            ItemStack stack = baubles.getStackInSlot(i);
            // Reference identity, never item/NBT equality: another identical worn stack still grants flight.
            if (stack == null || stack == departing || stack.stackSize <= 0) continue;
            if (stack.getItem() instanceof RingWindrider) return true;
            if (stack.getItem() instanceof FloatCore && hasFood) return true;
            if (stack.getItem() instanceof ElectricFloatCore && (hasFood || hasPower.test(stack))) return true;
        }
        return false;
    }
}
