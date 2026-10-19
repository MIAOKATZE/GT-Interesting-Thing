package com.miaokatze.gtit.testlab.scenario;

import java.io.ByteArrayInputStream;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.common.items.pocket.PocketUpgrades;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;

final class PocketNativeScenario implements ProbeScenario {

    @Override
    public String id() {
        return "native-pocket";
    }

    @Override
    public ProbeTask start(ProbeContext context) {
        return () -> {
            ItemStack carrier = context.mark(GTITItemList.NekoDimensionPocket.get(1));
            PocketUpgrades.install(carrier, PocketUpgradeType.CAPACITY);
            PocketUpgrades.installStackCount(carrier, 4);
            PocketInventory inventory = PocketInventory.readFrom(carrier.getTagCompound());
            ItemStack special = NativeFixtures.specialStack(context, 17);
            int inserted = inventory.depositIntoStorage(special);
            context.check(
                "special-item-insert",
                inserted == 17,
                "Actual PocketInventory.depositIntoStorage inserted=" + inserted);
            context
                .check("source-unchanged", special.stackSize == 17, "Deposit must preserve caller-owned source stack");
            long quantity = 1_500_000_123L;
            long filled = inventory.fillOwnTankL(0, FluidRegistry.WATER, quantity);
            FluidStack taggedLava = new FluidStack(FluidRegistry.LAVA, 321);
            taggedLava.tag = new NBTTagCompound();
            taggedLava.tag.setString("probeFluidPayload", "猫猫 lava");
            inventory.fillOwnTank(1, taggedLava);
            context.check(
                "long-fluid-fill",
                filled == quantity && inventory.tankTruthAt(0) == quantity,
                "Capacity-upgraded long truth=" + inventory.tankTruthAt(0));
            long before = inventory.tankTruthAt(0);
            context.check(
                "foreign-fluid-rejected",
                inventory.fillOwnTank(0, new FluidStack(FluidRegistry.LAVA, 20)) == 0
                    && inventory.tankTruthAt(0) == before,
                "Incompatible lava cannot overwrite stored water");
            int simulated = inventory.tankAt(0)
                .fill(new FluidStack(FluidRegistry.WATER, 91), false);
            context.check(
                "simulate-does-not-mutate",
                simulated == 91 && inventory.tankTruthAt(0) == before,
                "MUI tank simulate leaves long truth untouched");
            long capacity = PocketConstants.fluidTankCapacityMl(PocketConstants.CAPACITY_UPGRADE_MAX_COUNT);
            long boundaryFill = inventory.fillOwnTankL(2, FluidRegistry.WATER, Long.MAX_VALUE);
            context.check(
                "native-capacity-boundary",
                boundaryFill == capacity && inventory.tankTruthAt(2) == capacity
                    && inventory.fillOwnTankL(2, FluidRegistry.WATER, 1) == 0,
                "Native fully upgraded tank caps long requests at current capacity=" + capacity + " mB");
            inventory.writeTo(carrier.getTagCompound());
            NBTTagCompound serializedCarrier = carrier.writeToNBT(new NBTTagCompound());
            byte[] persisted = CompressedStreamTools.compress(serializedCarrier);
            ItemStack restoredCarrier = ItemStack
                .loadItemStackFromNBT(CompressedStreamTools.readCompressed(new ByteArrayInputStream(persisted)));
            PocketInventory restored = PocketInventory.readFrom(restoredCarrier.getTagCompound());
            context.check(
                "carrier-codec",
                carrier.isItemEqual(restoredCarrier) && ItemStack.areItemStackTagsEqual(carrier, restoredCarrier),
                "Registered pocket ItemStack NBT round-trip");
            context.check(
                "special-nbt-roundtrip",
                NativeFixtures.sameStack(special, restored.storageStack(0)),
                "Nested compound/list/byte array/unicode/long and stack size survive real pocket codec");
            context.check(
                "multi-fluid-roundtrip",
                restored.tankTruthAt(0) == quantity && restored.tankTruthAt(1) == 321
                    && restored.ownTankFluid(1)
                        .getFluid() == FluidRegistry.LAVA,
                "Independent water/lava identities and quantities");
            context.check(
                "fluid-special-nbt-roundtrip",
                restored.ownTankFluid(1) != null && taggedLava.isFluidEqual(restored.ownTankFluid(1)),
                "FluidStack custom NBT survives the native tank persistence path");
            context.check(
                "int-head-clamp",
                restored.ownTankFluid(0).amount == (int) Math.min(quantity, Integer.MAX_VALUE),
                "MUI head equals min(native long truth, int max); current 2G capacity remains below int max");
            FluidStack drained = restored.tankAt(0)
                .drain(123, true);
            context.check(
                "head-drain-updates-truth",
                drained != null && drained.amount == 123 && restored.tankTruthAt(0) == quantity - 123,
                "MUI head drain changes authoritative long truth exactly once");
            restored.storageStack(0)
                .getTagCompound()
                .setString("cloneMutation", "only-copy");
            context.check(
                "deserialization-isolation",
                !special.getTagCompound()
                    .hasKey("cloneMutation")
                    && !inventory.storageStack(0)
                        .getTagCompound()
                        .hasKey("cloneMutation"),
                "Reopened storage does not alias source/session");
            context.metric("persistedCarrierBytes", persisted.length);
            return true;
        };
    }
}
