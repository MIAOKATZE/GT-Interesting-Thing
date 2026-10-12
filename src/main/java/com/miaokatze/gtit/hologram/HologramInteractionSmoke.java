package com.miaokatze.gtit.hologram;

import java.io.File;
import java.io.FileOutputStream;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.structure.IStructureElement;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.HatchElement;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchMuffler;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;

/** Dedicated-world fixture and independent material/world receipts for the real network GUI driver. */
public final class HologramInteractionSmoke {

    private EntityPlayerMP player;
    private File receipts;
    private boolean failed;
    private int ticks, lastPlaced = -1, lastStock = -1;
    private boolean sawBuilt, sawUpgraded;
    private int receiptPublishRetries;
    private int mufflerId = -1, lastMufflerWorld, lastMufflerStock;

    public static void registerIfEnabled() {
        if (Boolean.getBoolean("gtit.hologram.interactionServer")) FMLCommonHandler.instance()
            .bus()
            .register(new HologramInteractionSmoke());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (failed || event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = MinecraftServer.getServer();
        try {
            if (++ticks > 18000) throw new IllegalStateException("real interaction timeout");
            WorldServer world = server.worldServerForDimension(0);
            if (!world.getWorldInfo()
                .getWorldName()
                .startsWith("hologram-interaction-")) throw new IllegalStateException("isolated world guard");
            if (receipts == null) {
                receipts = new File(System.getProperty("gtit.hologram.interactionReceiptDir", ""));
                if (!receipts.isDirectory()) throw new IllegalStateException("prepared receipt directory required");
            }
            File clientFailure = new File(receipts, "client-failed.txt");
            if (clientFailure.isFile()) throw new IllegalStateException(
                "real interaction client failed: " + new String(
                    java.nio.file.Files.readAllBytes(clientFailure.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8));
            if (player == null) {
                if (server.getConfigurationManager().playerEntityList.isEmpty()) return;
                player = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
                BaseMetaTileEntity controller = createFixture(world, player, 48, 80, 48);
                mufflerId = installMuffler(world, player, controller, 48, 80, 48);
                player.theItemInWorldManager.setGameType(net.minecraft.world.WorldSettings.GameType.SURVIVAL);
                player.capabilities.allowFlying = true;
                player.capabilities.isFlying = true;
                player.sendPlayerAbilities();
                player.playerNetServerHandler.setPlayerLocation(48.5, 80, 46.5, 0, 0);
                for (int i = 0; i < player.inventory.mainInventory.length; i++)
                    player.inventory.mainInventory[i] = null;
                Item item = (Item) Item.itemRegistry.getObject("gtit:neko_hologram_projector");
                player.inventory.mainInventory[0] = new ItemStack(item);
                player.inventory.mainInventory[1] = new ItemStack(GregTechAPI.sBlockCasings1, 64, 11);
                for (int tier = 0; tier < 8; tier++)
                    player.inventory.mainInventory[tier + 2] = new ItemStack(GregTechAPI.sBlockCasings5, 16, tier);
                player.inventory.currentItem = 0;
                player.inventoryContainer.detectAndSendChanges();
                // Deliberately do not collect here: the client's first C08 must exercise the cold capture path.
                receipt(world, "fixture");
                return;
            }
            File complete = new File(receipts, "client-complete.txt");
            if (complete.isFile()) {
                if (!sawBuilt || !sawUpgraded
                    || lastPlaced != 0
                    || lastStock != 192
                    || lastMufflerWorld != 0
                    || lastMufflerStock != 1)
                    throw new AssertionError(
                        "client completion requires independent built/upgraded/recovered world observations");
                if (!receipt(world, "final")) return;
                System.out.println(
                    "[GTIT-HOLOGRAM-INTERACTION-SERVER] FINAL PASS real-client completion; independent world/material receipts saved");
                failed = true;
                server.initiateShutdown();
                return;
            }
            receipt(world, "live");
        } catch (Throwable failure) {
            failed = true;
            System.err.println("[GTIT-HOLOGRAM-INTERACTION-SERVER] FINAL FAIL " + failure);
            failure.printStackTrace();
            server.initiateShutdown();
        }
    }

    private boolean receipt(WorldServer world, String stage) throws Exception {
        int placed = 0, coils = 0, casings = 0, stock = 0, highCoils = 0, mufflerWorld = 0, mufflerStock = 0;
        // EBF extends at most three blocks from its controller. This fixed box is independent of capture output.
        for (int x = 45; x <= 51; x++) for (int y = 77; y <= 83; y++) for (int z = 45; z <= 51; z++) {
            if (world.getBlock(x, y, z) == GregTechAPI.sBlockCasings5) {
                coils++;
                placed++;
                if (world.getBlockMetadata(x, y, z) > 0) highCoils++;
            }
            if (world.getBlock(x, y, z) == GregTechAPI.sBlockCasings1) {
                casings++;
                placed++;
            }
            if (world.getTileEntity(x, y, z) instanceof BaseMetaTileEntity
                && ((BaseMetaTileEntity) world.getTileEntity(x, y, z)).getMetaTileID() == mufflerId) mufflerWorld++;
        }
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack == null) continue;
            if (stack.getItem() == Item.getItemFromBlock(GregTechAPI.sBlockCasings5)
                || stack.getItem() == Item.getItemFromBlock(GregTechAPI.sBlockCasings1)) stock += stack.stackSize;
            if (stack.getItem() == Item.getItemFromBlock(GregTechAPI.sBlockMachines)
                && stack.getItemDamage() == mufflerId) mufflerStock += stack.stackSize;
        }
        // Conservation is checked from actual server world and inventory, independent of GUI state.
        if (stock + placed != 64 + 8 * 16)
            throw new AssertionError("material conservation stock=" + stock + " placed=" + placed);
        if (mufflerWorld + mufflerStock != 1) throw new AssertionError(
            "mandatory muffler conservation world=" + mufflerWorld + " inventory=" + mufflerStock);
        if (coils == 16) sawBuilt = true;
        if (highCoils == 16) sawUpgraded = true;
        // Upgrades keep total counts constant; still publish metadata observations every tick.
        lastPlaced = placed;
        lastStock = stock;
        lastMufflerWorld = mufflerWorld;
        lastMufflerStock = mufflerStock;
        NBTTagCompound n = new NBTTagCompound();
        n.setString("stage", stage);
        n.setInteger("placed", placed);
        n.setInteger("coils", coils);
        n.setInteger("highCoils", highCoils);
        n.setInteger("casings", casings);
        n.setInteger("stock", stock);
        n.setInteger("mufflerWorld", mufflerWorld);
        n.setInteger("mufflerStock", mufflerStock);
        n.setLong("tick", world.getTotalWorldTime());
        n.setInteger(
            "facing",
            ((BaseMetaTileEntity) world.getTileEntity(48, 80, 48)).getFrontFacing()
                .ordinal());
        File temp = new File(receipts, "world-receipt.tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            CompressedStreamTools.writeCompressed(n, out);
        }
        try {
            java.nio.file.Files.move(
                temp.toPath(),
                new File(receipts, "world-receipt.nbt").toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            receiptPublishRetries = 0;
        } catch (java.nio.file.FileSystemException locked) {
            String reason = String.valueOf(locked.getReason())
                .toLowerCase(java.util.Locale.ROOT);
            boolean sharingViolation = reason.contains("另一个程序") || reason.contains("another process")
                || reason.contains("being used")
                || reason.contains("sharing violation");
            if (!sharingViolation || ++receiptPublishRetries > 20) throw locked;
            if (receiptPublishRetries == 1) System.out.println(
                "[GTIT-HOLOGRAM-INTERACTION-SERVER] receipt publication pending: Windows sharing violation; bounded 20 tick retry");
            // Retry publication with a fresh snapshot on the next tick. World assertion failures are never retried.
            return false;
        }
        if (!"live".equals(stage) || world.getTotalWorldTime() % 40 == 0) System.out.println(
            "[GTIT-HOLOGRAM-INTERACTION-SERVER] RECEIPT stage=" + stage
                + " placed="
                + placed
                + " highCoils="
                + highCoils
                + " stock="
                + stock
                + " mufflerWorld="
                + mufflerWorld
                + " mufflerStock="
                + mufflerStock);
        return true;
    }

    private static int installMuffler(WorldServer world, EntityPlayerMP player, BaseMetaTileEntity controller, int x,
        int y, int z) {
        int id = -1;
        for (int i = 1; i < GregTechAPI.METATILEENTITIES.length; i++) {
            if (GregTechAPI.METATILEENTITIES[i] instanceof MTEHatchMuffler
                && ((MTEHatchMuffler) GregTechAPI.METATILEENTITIES[i]).mTier == 1) {
                id = i;
                break;
            }
        }
        if (id <= 0 || !world.isAirBlock(x, y + 3, z + 1))
            throw new IllegalStateException("fresh registered LV muffler fixture required");
        if (!world.setBlock(x, y + 3, z + 1, GregTechAPI.sBlockMachines, 0, 3))
            throw new IllegalStateException("muffler fixture placement failed");
        BaseMetaTileEntity base = (BaseMetaTileEntity) world.getTileEntity(x, y + 3, z + 1);
        base.setInitialValuesAsNBT(null, (short) id);
        base.setFrontFacing(ForgeDirection.UP);
        base.setOwnerName(player.getCommandSenderName());
        base.setOwnerUuid(player.getUniqueID());
        MTEElectricBlastFurnace machine = (MTEElectricBlastFurnace) controller.getMetaTileEntity();
        boolean accepted = false;
        for (IStructureElement<MTEElectricBlastFurnace> element : machine.getStructureDefinition()
            .getStructureFor("main")) {
            for (IStructureElement<?> part : HologramElementCatalog.parts(element)) {
                if (HologramHatchPolicy.declared(part)
                    .contains(HatchElement.Muffler)) {
                    accepted = HologramHatchPolicy.allowed(part, machine.getExtendedFacing(), ForgeDirection.UP)
                        && element.check(machine, world, x, y + 3, z + 1);
                    break;
                }
            }
            if (accepted) break;
        }
        machine.mMufflerHatches.clear();
        if (!accepted || !(base.getMetaTileEntity() instanceof MTEHatchMuffler) || base.getMetaTileID() != id)
            throw new AssertionError("installed real LV muffler must satisfy the actual EBF declaration");
        System.out.println(
            "[GTIT-HOLOGRAM-INTERACTION-SERVER] FIXTURE real LV muffler id=" + id
                + " at="
                + x
                + ","
                + (y + 3)
                + ","
                + (z + 1)
                + " facing="
                + base.getFrontFacing()
                + " declaredAccepted=true");
        return id;
    }

    static BaseMetaTileEntity createFixture(WorldServer world, EntityPlayerMP player, int x, int y, int z) {
        int id = -1;
        for (int i = 0; i < GregTechAPI.METATILEENTITIES.length; i++)
            if (GregTechAPI.METATILEENTITIES[i] instanceof MTEElectricBlastFurnace) {
                id = i;
                break;
            }
        if (id <= 0) throw new IllegalStateException("registered EBF required");
        for (int xx = x - 5; xx <= x + 5; xx++) for (int zz = z - 5; zz <= z + 5; zz++) {
            world.getChunkFromBlockCoords(xx, zz);
            for (int yy = y - 5; yy <= y + 5; yy++)
                if (!world.isAirBlock(xx, yy, zz)) throw new IllegalStateException("fixture must be fresh empty world");
        }
        world.setBlock(x, y, z, GregTechAPI.sBlockMachines, 0, 3);
        BaseMetaTileEntity base = (BaseMetaTileEntity) world.getTileEntity(x, y, z);
        base.setInitialValuesAsNBT(null, (short) id);
        base.setFrontFacing(ForgeDirection.NORTH);
        base.setOwnerName(player.getCommandSenderName());
        base.setOwnerUuid(player.getUniqueID());
        return base;
    }
}
