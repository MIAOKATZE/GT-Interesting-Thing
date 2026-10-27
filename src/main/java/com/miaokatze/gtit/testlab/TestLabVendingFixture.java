package com.miaokatze.gtit.testlab;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import com.cubefury.vendingmachine.blocks.VendingMachineBlocks;
import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.items.terminal.NekoVendingTarget;
import com.miaokatze.gtit.common.items.terminal.RemoteVendingContainer;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.blocks.ItemMachines;

/** Explicit manual native-client fixture. Never opens a screen, teleports, or places blocks in a player's world. */
final class TestLabVendingFixture implements ProbeScenario {

    private static UUID requestedPlayer;
    private static Session active;

    @Override
    public String id() {
        return "client-ui-vending";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    static String command(EntityPlayerMP player, String command) {
        requirePlayer(player);
        if ("vending".equals(command)) {
            TestLabUiNetwork.requireEmptyInventory(player);
            requestedPlayer = player.getUniqueID();
            try {
                return "run=" + TestLabRuntime.INSTANCE.run("ui", "client-ui-vending", 1);
            } catch (RuntimeException error) {
                requestedPlayer = null;
                throw error;
            }
        }
        Session session = active;
        if (session == null || session.player != player) throw new IllegalStateException("Start with ui vending");
        if ("vending-done".equals(command)) {
            session.done = true;
            return "Fixture closing; visual acceptance must be recorded separately";
        }
        String mode = switch (command) {
            case "vending-ordinary" -> "ordinary";
            case "vending-batch" -> "batch";
            case "vending-legendary" -> "legendary";
            default -> throw new IllegalArgumentException("Unknown vending fixture command");
        };
        if (session.pending != null) throw new IllegalStateException("A sample is already waiting for the native UI");
        session.pending = mode;
        session.openTicks = 0;
        return mode + " queued; hold the terminal and right-click to open the native UI";
    }

    private static void requirePlayer(EntityPlayerMP player) {
        if (!TestLabConfig.enabled || !TestLabConfig.clientUiEnabled
            || !TestLabUiNetwork.validPlayer(player)
            || !player.capabilities.isCreativeMode
            || !MinecraftServer.getServer()
                .isSinglePlayer())
            throw new IllegalStateException(
                "Requires enabled + clientUiEnabled, local singleplayer creative OP test client");
    }

    @Override
    public ProbeTask start(ProbeContext context) {
        EntityPlayerMP player = null;
        for (Object candidate : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList)
            if (candidate instanceof EntityPlayerMP online && online.getUniqueID()
                .equals(requestedPlayer)) player = online;
        requestedPlayer = null;
        requirePlayer(player);
        TestLabUiNetwork.requireEmptyInventory(player);
        Session session = new Session(player, context);
        active = session;
        context.onCleanup(() -> {
            try {
                session.player.closeScreen();
            } finally {
                scrub(session);
                if (active == session) active = null;
            }
        });
        return session;
    }

    static void onEntityJoin(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        Session session = active;
        if (session != null && event.entity instanceof net.minecraft.entity.item.EntityItem drop
            && TestLabUiNetwork.marked(drop.getEntityItem(), session.context.runId())) {
            event.setCanceled(true);
            event.entity.setDead();
            session.violation = "Marked fixture drop suppressed; manual session stopped";
        }
    }

    static void onLogout(net.minecraft.entity.player.EntityPlayer player) {
        Session session = active;
        if (session != null && session.player == player) {
            session.violation = "Test client disconnected";
            scrub(session);
            session.player.getServerForPlayer()
                .getSaveHandler()
                .getSaveHandler()
                .writePlayerData(session.player);
        }
    }

    private static void scrub(Session session) {
        if (TestLabUiNetwork.marked(session.player.inventory.getItemStack(), session.context.runId()))
            session.player.inventory.setItemStack(null);
        for (int i = 0; i < session.player.inventory.getSizeInventory(); i++)
            if (TestLabUiNetwork.marked(session.player.inventory.getStackInSlot(i), session.context.runId()))
                session.player.inventory.setInventorySlotContents(i, null);
        session.player.inventoryContainer.detectAndSendChanges();
    }

    private static final class Session implements ProbeTask {

        final EntityPlayerMP player;
        final ProbeContext context;
        final net.minecraft.world.World world;
        final int terminalSlot;
        BaseMetaTileEntity base;
        MTENekoVendingMachineV2 machine;
        String pending = "ordinary";
        String violation;
        int ticks;
        int openTicks;
        boolean done;

        Session(EntityPlayerMP player, ProbeContext context) {
            this.player = player;
            this.context = context;
            world = player.worldObj;
            terminalSlot = player.inventory.currentItem;
        }

        @Override
        public boolean tick() {
            if (done) {
                context.skip(
                    "visual-input-acceptance",
                    "Manual session ended; screenshots/input observations are separate evidence");
                context.skip("live-me-network", "No AE network fixture; no ME success or failure is simulated");
                return true;
            }
            if (violation != null || !TestLabUiNetwork.validPlayer(player)
                || !player.capabilities.isCreativeMode
                || player.worldObj != world) {
                context
                    .check("session-guard", false, violation == null ? "Client permission/world changed" : violation);
                return true;
            }
            if (ticks++ == 0) {
                place();
                return false;
            }
            if (ticks == 10) {
                boolean formed = machine.checkStructure(true, base);
                context.check(
                    "native-formed-controller",
                    formed,
                    "Real StructureLib check of owned native controller/casings");
                if (!formed) return true;
                ItemStack terminal = context.mark(GTITItemList.NekoVendingTerminal.get(1));
                new NekoVendingTarget(context.world().provider.dimensionId, context.x(12), context.y(0), context.z(12))
                    .write(terminal);
                player.inventory.setInventorySlotContents(terminalSlot, terminal);
                player.inventory
                    .setInventorySlotContents((terminalSlot + 1) % 9, context.mark(new ItemStack(Items.paper, 64)));
                player.inventoryContainer.detectAndSendChanges();
                player.addChatMessage(
                    new ChatComponentText(
                        "[GTIT-LAB] Vending ready: hold terminal and right-click. Ordinary sample follows after 2 seconds. ui vending-done cleans marked items/owned blocks."));
            }
            if (ticks < 10 || pending == null) return false;
            if (!(player.openContainer instanceof RemoteVendingContainer)) {
                openTicks = 0;
                return false;
            }
            if (++openTicks < 40 || machine.getOutputLedger()
                .hasOpenBatch()) return false;
            emit();
            pending = null;
            openTicks = 0;
            return false;
        }

        private void place() {
            ItemStack stack = context.mark(GTITItemList.NekoVendingMachine.get(1));
            int metadata = GregTechAPI.METATILEENTITIES[stack.getItemDamage()].getTileEntityBaseType();
            boolean placed = context.placeNativeBlock(
                12,
                0,
                12,
                GregTechAPI.sBlockMachines,
                metadata,
                () -> ((ItemMachines) stack.getItem()).placeBlockAt(
                    stack,
                    context.player("placer"),
                    context.world(),
                    context.x(12),
                    context.y(0),
                    context.z(12),
                    1,
                    0.5F,
                    0.5F,
                    0.5F,
                    metadata));
            if (!placed || !(context.world()
                .getTileEntity(context.x(12), context.y(0), context.z(12)) instanceof BaseMetaTileEntity tile)
                || !(tile.getMetaTileEntity() instanceof MTENekoVendingMachineV2 vending))
                throw new IllegalStateException("Native vending controller placement failed");
            base = tile;
            machine = vending;
            base.setOwnerName(player.getCommandSenderName());
            base.setOwnerUuid(player.getUniqueID());
            // Symmetric, small owned casing fixture works with the native placement-facing choice.
            for (int[] offset : new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }) {
                if (!context.setBlock(12 + offset[0], 0, 12 + offset[1], VendingMachineBlocks.casingBlock, 0)
                    || !context.setBlock(12 + offset[0], 1, 12 + offset[1], VendingMachineBlocks.casingBlock, 0))
                    throw new IllegalStateException("Owned vending casing placement failed");
            }
            if (!context.setBlock(12, 1, 12, VendingMachineBlocks.casingBlock, 0))
                throw new IllegalStateException("Owned vending top casing placement failed");
            context
                .check("native-controller", true, "Registered ItemMachines placement, owned laboratory dimension only");
        }

        private void emit() {
            // Bounded per-session inventory: reject instead of invoking the overflow/drop path.
            int count = "batch".equals(pending) ? 17 : "ordinary".equals(pending) ? 3 : 1;
            int free = 0;
            for (int i = 0; i < machine.outputItems.getSlots(); i++)
                if (machine.outputItems.getStackInSlot(i) == null) free++;
            if (free < count)
                throw new IllegalStateException("Take existing fixture output before queuing another sample");
            List<ItemStack> samples = new ArrayList<>();
            for (int i = 0; i < count; i++) samples.add(
                context.mark(
                    new ItemStack(
                        "legendary".equals(pending) ? Items.diamond : Items.emerald,
                        "batch".equals(pending) ? 64 : 8)));
            if ("legendary".equals(pending)) {
                ItemStack remainder = machine.dispenseItemStack(samples.get(0), true);
                if (remainder != null) throw new IllegalStateException("Legendary sample was not accepted");
            } else machine.dispenseItemStacks(samples);
            context
                .check("queued-" + pending, true, "Public native output API accepted fixture; not visual acceptance");
        }
    }
}
