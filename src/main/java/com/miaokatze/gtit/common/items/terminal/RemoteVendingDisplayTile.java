package com.miaokatze.gtit.common.items.terminal;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Client rendering address. This tile is never installed or ticked in any world. */
public final class RemoteVendingDisplayTile extends BaseMetaTileEntity {

    private final NekoVendingTarget target;

    public RemoteVendingDisplayTile(NekoVendingTarget target) {
        this.target = target;
        xCoord = target.x;
        yCoord = target.y;
        zCoord = target.z;
    }

    public static int dimensionOf(IGregTechTileEntity machine) {
        if (machine instanceof RemoteVendingDisplayTile) return ((RemoteVendingDisplayTile) machine).target.dimension;
        return machine.getWorld() == null ? 0 : machine.getWorld().provider.dimensionId;
    }
}
