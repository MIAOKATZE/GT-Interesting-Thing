package com.miaokatze.gtit.common.items.pocket;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

/** One bounded outgoing escrow. Unknown target outcomes are never automatically refunded or replayed. */
public final class PocketTransferReservation {

    static final String NBT_KEY = "gtitPocketOutgoingTransfer";
    final NBTTagCompound record;
    final ItemStack item;
    final FluidStack fluid;
    final String essenceTag;
    final int sourceSlot;
    final long offered;

    private PocketTransferReservation(String kind, int slot, long amount, ItemStack item, FluidStack fluid,
        String essenceTag, String destination) {
        this.record = new NBTTagCompound();
        record.setInteger("version", 1);
        record.setString(
            "token",
            UUID.randomUUID()
                .toString());
        record.setString("kind", kind);
        record.setString("state", "unknown");
        record.setString("destination", destination);
        record.setInteger("sourceSlot", slot);
        record.setLong("offered", amount);
        record.setLong("amount", amount);
        this.item = item;
        this.fluid = fluid;
        this.essenceTag = essenceTag;
        this.sourceSlot = slot;
        this.offered = amount;
        if (item != null) {
            ItemStack sample = item.copy();
            sample.stackSize = 1;
            record.setTag("item", sample.writeToNBT(new NBTTagCompound()));
        }
        if (fluid != null) {
            FluidStack sample = fluid.copy();
            sample.amount = 1;
            record.setTag("fluid", sample.writeToNBT(new NBTTagCompound()));
        }
        if (essenceTag != null) record.setString("essenceTag", essenceTag);
    }

    static PocketTransferReservation item(int slot, ItemStack item, int amount, String destination) {
        return new PocketTransferReservation("item", slot, amount, item.copy(), null, null, destination);
    }

    static PocketTransferReservation fluid(int slot, FluidStack fluid, long amount, String destination) {
        return new PocketTransferReservation("fluid", slot, amount, null, fluid.copy(), null, destination);
    }

    static PocketTransferReservation essence(String tag, int amount, String destination) {
        return new PocketTransferReservation("essence", -1, amount, null, null, tag, destination);
    }

    public String token() {
        return record.getString("token");
    }

    /** Only explicitly known returned resources may be released from escrow. */
    void knownRemainder(long amount) {
        if (amount < 0 || amount > offered) throw new IllegalArgumentException("Invalid transfer remainder");
        record.setLong("amount", amount);
        record.setString("state", "refund");
    }

    static PocketTransferReservation knownRefund(NBTTagCompound saved) {
        try {
            if (saved.getInteger("version") != 1 || !"refund".equals(saved.getString("state"))
                || !saved.hasKey("amount", 4)
                || !saved.hasKey("offered", 4)) return null;
            long amount = saved.getLong("amount"), offered = saved.getLong("offered");
            if (amount < 0 || amount > offered || offered <= 0 || offered > Integer.MAX_VALUE) return null;
            UUID.fromString(saved.getString("token"));
            PocketTransferReservation result;
            switch (saved.getString("kind")) {
                case "item": {
                    ItemStack item = ItemStack.loadItemStackFromNBT(saved.getCompoundTag("item"));
                    if (item == null) return null;
                    result = item(saved.getInteger("sourceSlot"), item, (int) offered, saved.getString("destination"));
                    break;
                }
                case "fluid": {
                    FluidStack fluid = FluidStack.loadFluidStackFromNBT(saved.getCompoundTag("fluid"));
                    if (fluid == null) return null;
                    result = fluid(saved.getInteger("sourceSlot"), fluid, offered, saved.getString("destination"));
                    break;
                }
                case "essence": {
                    if (saved.getString("essenceTag")
                        .isEmpty()) return null;
                    result = essence(saved.getString("essenceTag"), (int) offered, saved.getString("destination"));
                    break;
                }
                default:
                    return null;
            }
            for (Object key : saved.func_150296_c()) result.record.setTag(
                (String) key,
                saved.getTag((String) key)
                    .copy());
            return result;
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
