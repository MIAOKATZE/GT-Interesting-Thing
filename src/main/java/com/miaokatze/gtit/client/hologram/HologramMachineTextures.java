package com.miaokatze.gtit.client.hologram;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Read-only GT texture access across the inventory-model API introduced after beta2. */
final class HologramMachineTextures {

    private static final Method INVENTORY_TEXTURES = findInventoryTextures();
    private static final int[][] NORTH_TO_FRONT = { { 2, 3, 1, 0, 4, 5 }, { 3, 2, 0, 1, 4, 5 }, { 0, 1, 2, 3, 4, 5 },
        { 0, 1, 3, 2, 5, 4 }, { 0, 1, 5, 4, 2, 3 }, { 0, 1, 4, 5, 3, 2 } };
    private static final int[] NORTH_TO_WEST = { 0, 1, 4, 5, 3, 2 };

    private HologramMachineTextures() {}

    private static Method findInventoryTextures() {
        try {
            return IMetaTileEntity.class.getMethod("getInventoryTextures");
        } catch (NoSuchMethodException absentOnBeta1And2) {
            return null;
        }
    }

    static ITexture[][] resolve(IMetaTileEntity machine, int front, int[] textureSides) {
        ITexture[][] inventory = inventory(machine);
        if (front < 0 || front >= 6) return inventory;
        ITexture[][] textures = new ITexture[6][];
        for (int side = 0; side < 6; side++) {
            // World-side callbacks preserve controller overlays and facing without mutating the prototype.
            try {
                textures[side] = machine.getTexture(
                    null,
                    ForgeDirection.getOrientation(side),
                    ForgeDirection.getOrientation(front),
                    -1,
                    false,
                    false);
            } catch (RuntimeException requiresWorldTile) {}
            if (empty(textures[side]) && inventory != null) {
                int source = inventorySide(front, side);
                if (source < inventory.length) {
                    textures[side] = inventory[source];
                    textureSides[side] = source;
                }
            }
        }
        return textures;
    }

    private static ITexture[][] inventory(IMetaTileEntity machine) {
        if (INVENTORY_TEXTURES != null) {
            try {
                ITexture[][] inventory = (ITexture[][]) INVENTORY_TEXTURES.invoke(machine);
                if (inventory != null) return inventory;
            } catch (InvocationTargetException failedDelegate) {
                Throwable cause = failedDelegate.getCause();
                if (cause instanceof Error) throw (Error) cause;
                if (!(cause instanceof RuntimeException))
                    throw new IllegalStateException("Inventory texture callback failed", cause);
                // A world-dependent optional model can still expose its legacy inventory faces.
            } catch (IllegalAccessException inaccessibleDelegate) {
                throw new IllegalStateException("Cannot invoke inventory texture callback", inaccessibleDelegate);
            }
        }
        ITexture[][] inventory = new ITexture[6][];
        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        // Match GT's inventory model: WEST front, active overlays, existing prototype base only.
        for (int side = 0; side < 6; side++) {
            try {
                inventory[side] = machine
                    .getTexture(base, ForgeDirection.getOrientation(side), ForgeDirection.WEST, -1, true, false);
            } catch (RuntimeException requiresWorldTile) {}
        }
        return inventory;
    }

    static int inventorySide(int front, int side) {
        return front >= 0 && front < 6 && side >= 0 && side < 6 ? NORTH_TO_WEST[NORTH_TO_FRONT[front][side]] : side;
    }

    private static boolean empty(ITexture[] textures) {
        return textures == null || textures.length == 0;
    }
}
