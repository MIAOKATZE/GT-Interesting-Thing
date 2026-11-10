package com.miaokatze.gtit.testlab.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeExecutor;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;
import com.miaokatze.gtit.trade.v2.NekoTradeResult;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.blocks.ItemMachines;

final class MachineNativeScenario implements ProbeScenario {

    @Override
    public String id() {
        return "native-machine-trade";
    }

    @Override
    public ProbeTask start(ProbeContext context) throws Exception {
        FakePlayer player = context.player("operator");
        NativeFixtures.ownIdentity(context, player.getUniqueID());
        return () -> {
            ItemStack machineStack = context.mark(GTITItemList.NekoVendingMachine.get(1));
            boolean placed = place(context, player, machineStack);
            context.check("native-placement", placed, "Real registered GT5U ItemMachines.placeBlockAt");
            if (!placed) return true;
            TileEntity tile = context.world()
                .getTileEntity(context.x(4), context.y(0), context.z(4));
            context.check(
                "native-mte-type",
                tile instanceof BaseMetaTileEntity
                    && ((BaseMetaTileEntity) tile).getMetaTileEntity() instanceof MTENekoVendingMachineV2,
                "Placed block instantiates the registered GTIT vending machine");
            if (!(tile instanceof BaseMetaTileEntity)
                || !(((BaseMetaTileEntity) tile).getMetaTileEntity() instanceof MTENekoVendingMachineV2)) return true;
            BaseMetaTileEntity base = (BaseMetaTileEntity) tile;
            MTENekoVendingMachineV2 machine = (MTENekoVendingMachineV2) base.getMetaTileEntity();
            ItemStack input = NativeFixtures.specialStack(context, 7);
            machine.inputItems.setStackInSlot(0, input.copy());
            NBTTagCompound saved = new NBTTagCompound();
            base.writeToNBT(saved);
            TileEntity restoredTile = TileEntity.createAndLoadEntity(saved);
            boolean restored = restoredTile instanceof BaseMetaTileEntity
                && ((BaseMetaTileEntity) restoredTile).getMetaTileEntity() instanceof MTENekoVendingMachineV2;
            context
                .check("native-tile-nbt-type", restored, "Vanilla TileEntity registry reloads GT5U base and GTIT MTE");
            if (restored) {
                MTENekoVendingMachineV2 restoredMachine = (MTENekoVendingMachineV2) ((BaseMetaTileEntity) restoredTile)
                    .getMetaTileEntity();
                context.check(
                    "native-tile-item-nbt",
                    NativeFixtures.sameStack(input, restoredMachine.inputItems.getStackInSlot(0)),
                    "Machine's real saveNBTData/loadNBTData preserve exact input contents");
                context.check(
                    "native-nbt-fixture-detached",
                    restoredTile.getWorldObj() == null && context.world()
                        .getTileEntity(context.x(4), context.y(0), context.z(4)) == base,
                    "NBT-only copy never joins a world/tick/cache; original registered controller remains installed");
                restoredMachine.inputItems.setStackInSlot(0, null);
                // This copy was only decoded, never placed or ticked. Let its local object graph be collected.
                // invalidate() is the world-removal lifecycle and GT5U's power-failure cleanup requires a world.
            }
            trade(context, player.getUniqueID(), machine, input);
            // Harvest an empty controller: GTIT deliberately drops inventories separately on removal.
            for (int i = 0; i < machine.inputItems.getSlots(); i++) machine.inputItems.setStackInSlot(i, null);
            for (int i = 0; i < machine.outputItems.getSlots(); i++) machine.outputItems.setStackInSlot(i, null);
            List<ItemStack> drops = base.getDrops();
            context.check(
                "native-controller-harvest",
                drops.size() == 1 && machineStack.isItemEqual(drops.get(0)),
                "Real BaseMetaTileEntity.getDrops produces exactly one registered controller");
            context.setBlock(4, 0, 4, Blocks.air, 0);
            if (drops.size() == 1) {
                boolean replaced = place(context, player, context.mark(drops.get(0)));
                context.check(
                    "native-harvest-replacement",
                    replaced && context.world()
                        .getTileEntity(context.x(4), context.y(0), context.z(4)) instanceof BaseMetaTileEntity,
                    "Harvested controller can be placed again through the real item path");
            }
            context.skip(
                "formed-multiblock-and-me",
                "Controller probe does not build the casing/uplink or simulate a live AE network");
            return true;
        };
    }

    private static boolean place(ProbeContext context, FakePlayer player, ItemStack stack) {
        ItemMachines item = (ItemMachines) stack.getItem();
        int metadata = GregTechAPI.METATILEENTITIES[stack.getItemDamage()].getTileEntityBaseType();
        return context.placeNativeBlock(
            4,
            0,
            4,
            GregTechAPI.sBlockMachines,
            metadata,
            () -> item.placeBlockAt(
                stack,
                player,
                context.world(),
                context.x(4),
                context.y(0),
                context.z(4),
                1,
                0.5F,
                0.5F,
                0.5F,
                metadata));
    }

    private static void trade(ProbeContext context, UUID player, MTENekoVendingMachineV2 machine, ItemStack input) {
        NekoTradeGroup group = new NekoTradeGroup();
        NekoTrade trade = new NekoTrade();
        trade.setRecordNBT(true);
        trade.getFromItems()
            .add(new NekoBigItemStack(2, "", input));
        ItemStack output = context.mark(new ItemStack(Items.emerald, 3));
        trade.getToItems()
            .add(new NekoBigItemStack(output));
        group.getTrades()
            .add(trade);
        if (NekoTradeDatabase.INSTANCE.getTradeGroup(group.getId()) != null)
            throw new IllegalStateException("Synthetic trade UUID collision");
        context.onCleanup(() -> {
            if (NekoTradeDatabase.INSTANCE.getTradeGroup(group.getId()) == group)
                NekoTradeDatabase.INSTANCE.removeTradeGroup(group.getId());
        });
        NekoTradeDatabase.INSTANCE.addTradeGroup(group);
        context.check(
            "unformed-machine-refuses-trade",
            machine.processTrade(player, group.getId(), 0)
                .getStatus() == NekoTradeResult.Status.NOT_FORMED
                && machine.inputItems.getStackInSlot(0).stackSize == 7,
            "Actual machine guard preserves inputs before executor is called");
        CollectedOutputs outputs = new CollectedOutputs(false);
        NekoTradeResult result = NekoTradeExecutor.INSTANCE
            .executeTrade(player, group.getId(), 0, machine.createInputSlotAccessor(), outputs);
        context.check(
            "native-trade-success",
            result.isSuccess() && machine.inputItems.getStackInSlot(0).stackSize == 5
                && outputs.items.size() == 1
                && NativeFixtures.sameStack(output, outputs.items.get(0)),
            "Real executor + native machine input adapter consumes 2 and produces exactly 3 emeralds");
        long historyBefore = NekoHistoryManager.INSTANCE.getHistory(player, group.getId())
            .getTradeCount();
        CollectedOutputs refusing = new CollectedOutputs(true);
        NekoTradeResult refused = NekoTradeExecutor.INSTANCE
            .executeTrade(player, group.getId(), 0, machine.createInputSlotAccessor(), refusing);
        context.check(
            "native-trade-output-rollback",
            refused.getStatus() == NekoTradeResult.Status.OUTPUT_FULL
                && machine.inputItems.getStackInSlot(0).stackSize == 5
                && refusing.items.isEmpty()
                && NekoHistoryManager.INSTANCE.getHistory(player, group.getId())
                    .getTradeCount() == historyBefore,
            "Output gate closes after preflight; executor refunds inputs and leaves history unchanged");
        ItemStack wrongNbt = input.copy();
        wrongNbt.getTagCompound()
            .setString("different", "payload");
        machine.inputItems.setStackInSlot(0, wrongNbt);
        NekoTradeResult mismatch = NekoTradeExecutor.INSTANCE
            .executeTrade(player, group.getId(), 0, machine.createInputSlotAccessor(), new CollectedOutputs(false));
        context.check(
            "native-trade-nbt-rejection",
            mismatch.getStatus() == NekoTradeResult.Status.INSUFFICIENT_ITEMS
                && NativeFixtures.sameStack(wrongNbt, machine.inputItems.getStackInSlot(0)),
            "recordNBT rejects a different payload without consuming the native input slot");
    }

    private static final class CollectedOutputs implements NekoTradeExecutor.OutputSlotAccessor {

        private final List<ItemStack> items = new ArrayList<>();
        private final boolean closeAfterPreflight;
        private int checks;

        private CollectedOutputs(boolean closeAfterPreflight) {
            this.closeAfterPreflight = closeAfterPreflight;
        }

        @Override
        public boolean hasSpaceFor(ItemStack stack) {
            return !closeAfterPreflight || checks++ == 0;
        }

        @Override
        public void insertItem(ItemStack stack) {
            items.add(stack.copy());
        }

        @Override
        public void rollback(int count) {
            while (count-- > 0 && !items.isEmpty()) items.remove(items.size() - 1);
        }
    }
}
