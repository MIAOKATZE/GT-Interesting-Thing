package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

import appeng.api.storage.data.IAEFluidStack;
import appeng.util.item.AEFluidStack;

/** Must run against the actual AE2 conversion, not an IAEFluidStack fixture. */
public final class PocketFluidNativeBoundaryTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(PocketFluidNativeBoundaryTest.class, args)) return;
        run();
    }

    public static void run() {
        FluidStack plain = new FluidStack(FluidRegistry.WATER, 1000);
        IAEFluidStack accepted = PocketAeFluidStacks.createExact(plain);
        check(accepted != null && accepted.getStackSize() == 1000, "ordinary untagged upload remains available");

        FluidStack tagged = plain.copy();
        tagged.tag = new NBTTagCompound();
        tagged.tag.setString("variant", "A");
        IAEFluidStack nativeResult = AEFluidStack.create(tagged);
        IAEFluidStack guarded = PocketAeFluidStacks.createExact(tagged);
        boolean nativeExact = nativeResult != null && tagged.isFluidEqual(nativeResult.getFluidStack());
        check(nativeExact ? guarded != null && tagged.isFluidEqual(guarded.getFluidStack()) : guarded == null,
            "lossy native conversion must be refused, while tag-capable native implementations remain supported");
        check(tagged.amount == 1000 && "A".equals(tagged.tag.getString("variant")), "caller fluid remains untouched");
        check(PocketAeFluidStacks.createExact(null) == null, "absent fluid has no request");
        plain.amount = 0;
        check(PocketAeFluidStacks.createExact(plain) == null, "empty fluid has no request");
        System.out.println("PocketFluidNativeBoundaryTest: PASS (native tagged conversion exact=" + nativeExact + ")");
    }
}
