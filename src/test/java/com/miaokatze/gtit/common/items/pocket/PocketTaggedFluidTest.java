package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

/** Tagged fluids must remain distinct through tank probes, commits, containers and persistence. */
public final class PocketTaggedFluidTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static FluidStack tagged(String value, int amount) {
        FluidStack fluid = new FluidStack(FluidRegistry.WATER, amount);
        fluid.tag = new NBTTagCompound();
        fluid.tag.setString("variant", value);
        return fluid;
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(PocketTaggedFluidTest.class, args)) return;
        PocketInventory inventory = PocketInventory.readFrom(new NBTTagCompound());
        FluidStack original = tagged("a", 2000);
        check(inventory.fillOwnTank(0, original) == 2000, "seed tagged fluid");
        inventory.markClean();
        check(inventory.tankAt(0).fill(tagged("b", 1000), false) == 0, "simulation rejects different tags");
        check(inventory.tankAt(0).fill(tagged("b", 1000), true) == 0, "commit rejects different tags");
        check(inventory.fluidBarRoom(0, tagged("b", 1)) == 0, "int room probe includes tags");
        check(inventory.fluidBarRoomL(0, tagged("b", 1)) == 0, "long room probe includes tags");
        check(inventory.fillOwnTankL(0, FluidRegistry.WATER, 1000) == 0, "untagged long fill cannot mix into tagged stock");
        check(!inventory.isDirty() && inventory.tankTruthAt(0) == 2000, "rejected operations do not mutate");
        FluidStack simulated = inventory.tankAt(0).drain(1000, false);
        check(simulated.isFluidEqual(original) && simulated.amount == 1000, "simulated drain preserves tag");
        simulated.tag.setString("variant", "changed");
        check(inventory.ownTankFluid(0).isFluidEqual(original), "simulation result owns its NBT copy");
        check(!inventory.isDirty() && inventory.tankTruthAt(0) == 2000, "drain simulation is read-only");
        FluidStack drained = inventory.tankAt(0).drain(1000, true);
        check(drained.isFluidEqual(original) && drained.amount == 1000, "committed drain preserves tag");
        NBTTagCompound saved = new NBTTagCompound();
        inventory.writeTo(saved);
        PocketInventory reopened = PocketInventory.readFrom(saved);
        check(reopened.tankTruthAt(0) == 1000 && reopened.ownTankFluid(0).isFluidEqual(original),
            "NBT close/reopen preserves amount and tagged identity");
        check(reopened.tankAt(0).drain(1000, true).isFluidEqual(original), "last drain preserves tag after head cleared");

        // Isolate the container planner from PocketInventory's custom tank implementation.
        final FluidStack[] stock = { original.copy() };
        final FluidStackTank tank = new FluidStackTank(() -> stock[0], fluid -> stock[0] = fluid, 16000);
        final ItemStack[] cells = { new ItemStack(new Bottle(), 2), null };
        PocketFluidTransfer.Cells port = new PocketFluidTransfer.Cells() {
            public FluidStackTank tank(int index) { return tank; }
            public ItemStack cellStack(int index) { return cells[index]; }
            public int outputCellOf(int index) { return 1; }
            public int restCellOf(int index) { return 0; }
            public boolean hasSlot(int index) { return index >= 0 && index < 2; }
            public void abandoned(int index, String key, Object... values) { throw new AssertionError(key); }
            public boolean memoryAllowsFluid(int index, FluidStack fluid) { return true; }
            public void memoryDeclareFromIntake(int index, FluidStack fluid) {}
            public void placeProcessed(int index, ItemStack product, int moved, ItemStack unit, int rest) {
                cells[1] = PocketFluidTransfer.sized(product, moved);
                cells[0] = rest == 0 ? null : PocketFluidTransfer.sized(unit, rest);
            }
        };
        ItemStack unit = cells[0].copy();
        unit.stackSize = 1;
        PocketFluidTransfer.fillFromTank(port, 0, 0, unit, 2);
        check(cells[0] == null && cells[1].stackSize == 2, "both containers delivered");
        FluidStack product = ((Bottle) cells[1].getItem()).getFluid(cells[1]);
        check(product.isFluidEqual(original) && product.amount == 1000, "planned output preserves fluid tag");
        check(tank.getFluidAmount() == 0, "fluid plus filled containers conserved");
        com.miaokatze.gtit.gui.pocket.PocketCarrierIdentityTest.run();
        System.out.println("PocketTaggedFluidTest: PASS (identity, simulation, copies, NBT, stacked container transfer)");
    }

    private static final class Bottle extends Item implements IFluidContainerItem {
        public FluidStack getFluid(ItemStack container) {
            return FluidStack.loadFluidStackFromNBT(container.getTagCompound());
        }
        public int getCapacity(ItemStack container) { return 1000; }
        public int fill(ItemStack container, FluidStack resource, boolean commit) {
            FluidStack current = getFluid(container);
            if (resource == null || current != null && !current.isFluidEqual(resource)) return 0;
            int accepted = Math.min(resource.amount, 1000 - (current == null ? 0 : current.amount));
            if (commit && accepted > 0) {
                FluidStack filled = resource.copy();
                filled.amount = (current == null ? 0 : current.amount) + accepted;
                container.setTagCompound(filled.writeToNBT(new NBTTagCompound()));
            }
            return accepted;
        }
        public FluidStack drain(ItemStack container, int amount, boolean commit) {
            FluidStack fluid = getFluid(container);
            if (fluid == null || amount <= 0) return null;
            FluidStack result = fluid.copy();
            result.amount = Math.min(amount, fluid.amount);
            if (commit) {
                fluid.amount -= result.amount;
                container.setTagCompound(fluid.amount == 0 ? null : fluid.writeToNBT(new NBTTagCompound()));
            }
            return result;
        }
    }
}
