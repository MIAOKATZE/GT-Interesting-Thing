package com.miaokatze.gtit.common.items.pocket;

import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.util.item.AEFluidStack;

/** Reject lossy Forge-to-AE conversions before either inventory is changed. */
final class PocketAeFluidStacks {

    private PocketAeFluidStacks() {}

    static IAEFluidStack createExact(FluidStack offered) {
        if (offered == null || offered.amount <= 0) {
            return null;
        }
        final IAEFluidStack request = AEFluidStack.create(offered);
        // Some AE2 versions discard FluidStack.tag in create(). Copying offered is not enough:
        // the native conversion itself must retain the identity or the upload transmutes fluid.
        return request != null && offered.isFluidEqual(request.getFluidStack()) ? request : null;
    }
}
