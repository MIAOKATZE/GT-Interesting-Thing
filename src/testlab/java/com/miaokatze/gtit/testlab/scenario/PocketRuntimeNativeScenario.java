// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.testlab.scenario;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidRegistry;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.items.pocket.PocketCarrierIdentity;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketRuntimeSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;

/** Runs the actual extracted session against registered items and disconnected actors in the lab world. */
final class PocketRuntimeNativeScenario implements ProbeScenario {

    @Override
    public String id() {
        return "native-pocket-runtime";
    }

    @Override
    public ProbeTask start(ProbeContext context) {
        FakePlayer player = context.player("runtime");
        context.onCleanup(() -> PocketSessions.forget(player.getUniqueID()));
        return () -> {
            PocketInjectionChecks.run(
                context.player("injection-contract"),
                () -> context.mark(GTITItemList.NekoDimensionPocket.get(1)),
                (name, pass) -> context
                    .check("injection-" + name, pass, "Real FML AE stacks and callback failure contract"));
            int nativeEssence = PocketInjectionChecks.runEssence(
                context.player("injection-essence"),
                () -> context.mark(GTITItemList.NekoDimensionPocket.get(1)),
                (name, pass) -> context
                    .check("injection-" + name, pass, "Real registered native essence channel callback contract"));
            if (nativeEssence == 0)
                context.skip("injection-native-essence", "No registered essence container channel in this instance");
            ItemStack carrier = context.mark(GTITItemList.NekoDimensionPocket.get(1));
            if (carrier.getTagCompound() == null) carrier.setTagCompound(new NBTTagCompound());
            carrier.getTagCompound()
                .setString("unknown-runtime-key", "preserve");
            player.inventory.mainInventory[0] = carrier;
            PocketRuntimeSession session = new PocketRuntimeSession(
                () -> player,
                () -> player.inventory.mainInventory[0],
                0,
                carrier,
                false);
            PocketSessions.register(session);
            PocketInventory inventory = session.inventory();
            inventory.essence()
                .add("aer", 100);
            inventory.fillOwnTankL(0, FluidRegistry.WATER, 1000);
            session.persistFinal();
            NBTTagCompound root = carrier.getTagCompound();
            session.drainEssence("aer", 16);
            session.drainOwnTankL(0, 400);
            session.persistIdle();
            context.check(
                "open-idle-does-not-persist",
                PocketInventory.readFrom(root)
                    .essence()
                    .get("aer") == 100,
                "Open session retains its single live model until final save");
            ItemStack relocated = carrier.copy();
            player.inventory.mainInventory[0] = null;
            player.inventory.mainInventory[1] = relocated;
            session.close();
            PocketInventory reopened = PocketInventory.readFrom(relocated.getTagCompound());
            context.check(
                "native-copy-persists-accepted-state",
                reopened.essence()
                    .get("aer") == 84 && reopened.tankTruthAt(0) == 600
                    && !inventory.isDirty(),
                "Close follows the unique token after native stack copying");
            context.check(
                "runtime-only-retention",
                PocketSessions.peek(player.getUniqueID()) == session && !session.isOpen(),
                "Registry retains the runtime after closing without a panel or widget dependency");
            context.check(
                "unknown-root-preserved",
                "preserve".equals(
                    relocated.getTagCompound()
                        .getString("unknown-runtime-key")),
                "Existing carrier namespace survives publication");
            session.drainEssence("aer", 8);
            player.inventory.mainInventory[2] = relocated.copy();
            session.persistFinal();
            context.check(
                "ambiguous-authority-retains-pending",
                inventory.essence()
                    .get("aer") == 76
                    && inventory.essenceDeltas()
                        .get("aer") == -8
                    && inventory.isDirty(),
                "Duplicate tokens retain the accepted inventory and pending delta without publishing");
            session.persistFinal();
            context.check(
                "failed-save-preserves-quantity",
                inventory.essence()
                    .get("aer") == 76,
                "Repeated persistence failure cannot alter the accepted quantity");
            player.inventory.mainInventory[2] = null;
            NBTTagCompound savedAuthority = (NBTTagCompound) relocated.getTagCompound()
                .copy();
            PocketCarrierIdentity.capture(relocated, true);
            session.persistFinal();
            context.check(
                "revoked-session-no-overwrite",
                PocketInventory.readFrom(relocated.getTagCompound())
                    .essence()
                    .get("aer") == 84,
                "A new session token revokes the previous writer");
            // Restore only this owned fixture's token so cleanup can flush and release its retained dirty model.
            relocated.setTagCompound(savedAuthority);
            session.persistFinal();
            session.setStorageStackAt(0, new ItemStack(net.minecraft.init.Items.gold_ingot, 3));
            NBTTagCompound playerData = new NBTTagCompound();
            player.writeToNBT(playerData);
            context.check(
                "player-serialization-flushes-live-model",
                !inventory.isDirty() && PocketInventory.readFrom(relocated.getTagCompound())
                    .storageStack(0).stackSize == 3,
                "Actual transformed EntityPlayer serialization commits the live model first");
            session.setStorageStackAt(0, null);
            session.drainEssence("aer", 76);
            session.drainOwnTankL(0, 600);
            player.writeToNBT(new NBTTagCompound());
            PocketInventory emptied = PocketInventory.readFrom(relocated.getTagCompound());
            context.check(
                "all-channels-empty-after-player-save",
                !inventory.isDirty() && emptied.storageStack(0) == null
                    && emptied.tankTruthAt(0) == 0
                    && emptied.essence()
                        .get("aer") == 0
                    && !relocated.getTagCompound()
                        .hasKey(com.miaokatze.gtit.common.items.pocket.PocketConstants.ESSENCE_CELL_ORDER),
                "Removing the last item, fluid and essence removes the previously committed contents");
            NBTTagCompound corrupt = (NBTTagCompound) relocated.getTagCompound()
                .copy();
            corrupt.setString(com.miaokatze.gtit.common.items.pocket.PocketConstants.ITEM_CONTENTS, "invalid");
            NBTTagCompound originalCorrupt = (NBTTagCompound) corrupt.copy();
            context.check(
                "unreadable-carrier-preserved",
                PocketInventory.tryReadFrom(corrupt) == null && corrupt.equals(originalCorrupt),
                "Rejected loading preserves the original raw NBT");
            context.onCleanup(() -> {
                for (Object raw : context.world()
                    .getEntitiesWithinAABB(
                        net.minecraft.entity.item.EntityItem.class,
                        player.boundingBox.expand(4, 4, 4))) {
                    net.minecraft.entity.item.EntityItem drop = (net.minecraft.entity.item.EntityItem) raw;
                    ItemStack stack = drop.getEntityItem();
                    if (stack != null && stack.hasTagCompound()
                        && context.runId()
                            .equals(
                                stack.getTagCompound()
                                    .getCompoundTag(ProbeContext.TEST_ITEM_TAG)
                                    .getString("runId")))
                        drop.setDead();
                }
            });
            session.setStorageStackAt(0, new ItemStack(net.minecraft.init.Items.gold_ingot, 5));
            player.inventory.dropAllItems();
            context.check(
                "inventory-drop-flushes-live-model",
                !inventory.isDirty() && PocketInventory.readFrom(relocated.getTagCompound())
                    .storageStack(0).stackSize == 5,
                "Actual transformed InventoryPlayer drop commits accepted contents before the carrier leaves");
            return true;
        };
    }
}
