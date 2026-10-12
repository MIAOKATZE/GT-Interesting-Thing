package com.miaokatze.gtit.client.hologram;

import net.minecraft.nbt.NBTTagCompound;

/** Client review policy; the server owns the second-confirmation capability and material transaction. */
final class HologramPlacementConfirmation {

    private HologramPlacementConfirmation() {}

    static boolean lockedBlueprint(NBTTagCompound state) {
        return state.getBoolean("virtual") && state.getBoolean("virtualPlacement") && state.getBoolean("virtualLocked");
    }

    static boolean canBuild(NBTTagCompound state, long reviewedRevision) {
        return reviewedRevision == state.getLong("planRevision")
            && (!state.getBoolean("virtual") || lockedBlueprint(state))
            && (state.getInteger("missing") == 0 || lockedBlueprint(state));
    }

    static boolean forceReview(NBTTagCompound state, long reviewedRevision) {
        return lockedBlueprint(state) && state.getBoolean("forcePlacementReady")
            && reviewedRevision == state.getLong("planRevision");
    }
}
