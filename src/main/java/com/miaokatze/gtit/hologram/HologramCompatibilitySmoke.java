package com.miaokatze.gtit.hologram;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;

/** Opt-in real registered-machine capture and shell construction in a fresh, isolated server world. */
public final class HologramCompatibilitySmoke {

    private static final String PREFIX = "[GTIT-HOLOGRAM-COMPAT] ";
    private int ticks, assertions;
    private boolean finished;

    static void registerIfEnabled() {
        if (Boolean.getBoolean("gtit.hologram.compatibilitySmoke")) FMLCommonHandler.instance()
            .bus()
            .register(new HologramCompatibilitySmoke());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.END || ++ticks < 40) return;
        finished = true;
        MinecraftServer server = MinecraftServer.getServer();
        try {
            run(server.worldServerForDimension(0));
            System.out.println(
                PREFIX + "FINAL PASS assertions="
                    + assertions
                    + " real registered machines; survival shell construction; formation not checked");
        } catch (Throwable failure) {
            System.err.println(PREFIX + "FINAL FAIL " + failure);
            failure.printStackTrace();
        } finally {
            HologramService.clear();
            server.initiateShutdown();
        }
    }

    private void run(WorldServer world) throws Exception {
        require(
            world.getWorldInfo()
                .getWorldName()
                .startsWith("hologram-compat-"),
            "fresh_world_guard");
        ItemStack probe = new ItemStack(net.minecraft.init.Items.feather, 7);
        HologramChannelTrace.Scope scope = HologramChannelTrace.begin(this);
        HologramChannelTrace.Report report;
        try {
            ChannelDataAccessor.getChannelData(probe, "compat_probe");
        } finally {
            report = HologramChannelTrace.end(scope);
        }
        require(report.observations.containsKey("compat_probe"), "actual_accessor_mixin_injected");
        require(report.observations.get("compat_probe").effective == 7, "actual_accessor_master_fallback");
        FakePlayer player = FakePlayerFactory
            .get(world, new GameProfile(UUID.fromString("a2b5ac19-10f6-4daf-93cd-e188a29041f6"), "GTITCompatSmoke"));
        EmbeddedChannel transport = new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        NetworkManager manager = new NetworkManager(false);
        for (Field field : NetworkManager.class.getDeclaredFields()) if (field.getType() == Channel.class) {
            field.setAccessible(true);
            field.set(manager, transport);
        }
        player.playerNetServerHandler = new NetHandlerPlayServer(MinecraftServer.getServer(), manager, player);
        try {
            machine(
                world,
                player,
                "goodgenerator.blocks.tileEntity.MTEPreciseAssembler",
                48,
                new String[] { "unit_casing", "machine_casing", "glass" },
                new int[] { 3, 5, 2 });
            machine(
                world,
                player,
                "gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.processing.MTEIndustrialAlloySmelter",
                80,
                new String[] { "coil" },
                new int[] { 4 });
            machine(
                world,
                player,
                "com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2",
                112,
                new String[0],
                new int[0]);
        } finally {
            transport.finish();
            Object pending;
            while ((pending = transport.readOutbound()) != null) io.netty.util.ReferenceCountUtil.release(pending);
        }
    }

    private void machine(WorldServer world, FakePlayer player, String type, int x, String[] channels, int[] values)
        throws Exception {
        int id = -1;
        for (int i = 1; i < GregTechAPI.METATILEENTITIES.length; i++) {
            if (GregTechAPI.METATILEENTITIES[i] != null && type.equals(
                GregTechAPI.METATILEENTITIES[i].getClass()
                    .getName())) {
                id = i;
                break;
            }
        }
        require(id > 0, type + "_registered");
        for (int xx = x - 12; xx <= x + 12; xx++) for (int zz = 36; zz <= 60; zz++) {
            world.getChunkFromBlockCoords(xx, zz);
            for (int yy = 68; yy <= 92; yy++)
                if (!world.isAirBlock(xx, yy, zz)) throw new AssertionError("fixture must be fresh empty world");
        }
        require(true, type + "_fresh_fixture_air");
        world.setBlock(x, 80, 48, GregTechAPI.sBlockMachines, 0, 3);
        BaseMetaTileEntity base = (BaseMetaTileEntity) world.getTileEntity(x, 80, 48);
        base.setInitialValuesAsNBT(null, (short) id);
        base.setFrontFacing(ForgeDirection.NORTH);
        base.setOwnerName(player.getCommandSenderName());
        base.setOwnerUuid(player.getUniqueID());
        MTEEnhancedMultiBlockBase<?> machine = (MTEEnhancedMultiBlockBase<?>) base.getMetaTileEntity();
        Item projector = (Item) Item.itemRegistry.getObject("gtit:neko_hologram_projector");
        ItemStack inherited = new ItemStack(projector);
        HologramCapture first = collect(machine, inherited);
        require(first.channelReport != null, type + "_real_mixin_report");
        for (String channel : channels)
            require(first.channelReport.observations.containsKey(channel), type + "_observed_" + channel);
        ItemStack signal = inherited.copy();
        for (int i = 0; i < channels.length; i++) ChannelDataAccessor.setChannelData(signal, channels[i], values[i]);
        HologramCapture selected = collect(machine, signal);
        for (int i = 0; i < channels.length; i++) {
            ItemStack one = inherited.copy();
            ChannelDataAccessor.setChannelData(one, channels[i], values[i]);
            HologramCapture isolated = collect(machine, one);
            boolean changedChannel = false;
            for (HologramCapture.Cell cell : isolated.cells) {
                if (cell.anchor) continue;
                HologramCapture.Cell old = first.positions.get(cell.x + "," + cell.y + "," + cell.z);
                if (old == null || cell.block == old.block && cell.meta == old.meta) continue;
                require(
                    HologramDeclarationWrappers.channels(cell.element)
                        .contains(channels[i]),
                    type + "_signal_does_not_change_other_components_" + channels[i]);
                changedChannel = true;
            }
            require(changedChannel, type + "_single_channel_changes_declared_component_" + channels[i]);
        }
        int changed = 0;
        for (HologramCapture.Cell cell : selected.cells) {
            if (cell.anchor || cell.block == net.minecraft.init.Blocks.air) continue;
            require(!cell.unknown && cell.block != null, type + "_known_declared_target");
            HologramCapture.Cell old = first.positions.get(cell.x + "," + cell.y + "," + cell.z);
            if (old != null && (cell.block != old.block || cell.meta != old.meta)) changed++;
        }
        require(channels.length == 0 || changed > 0, type + "_independent_signal_changes_targets");
        HologramService.clear();
        player.inventory.mainInventory = new ItemStack[36];
        player.inventory.mainInventory[0] = signal.copy();
        player.inventory.currentItem = 0;
        player.setPosition(x + .5, 80, 46.5);
        player.theItemInWorldManager.setGameType(net.minecraft.world.WorldSettings.GameType.SURVIVAL);
        player.capabilities.allowEdit = true;
        HologramService.open(player, x, 80, 48, 2);
        Object session = session(player);
        settleCapture(world, session);
        NBTTagCompound configure = action(session, "configure");
        configure.setInteger("main", 1);
        configure.setTag(
            "channels",
            signal.getTagCompound() == null ? new NBTTagCompound()
                : signal.getTagCompound()
                    .getCompoundTag("channels")
                    .copy());
        configure.setBoolean("noHatches", true);
        configure.setInteger("mode", 0);
        configure.setInteger("scope", 0);
        configure.setInteger("layer", 0);
        configure.setInteger("selected", -1);
        configure.setInteger(
            "facing",
            machine.getExtendedFacing()
                .ordinal());
        HologramService.handle(player, configure);
        settleCapture(world, session);
        HologramCapture planned = (HologramCapture) get(session, "capture");
        require(!planned.incomplete && !planned.pending, type + "_service_complete_capture");
        int slot = 1, targets = 0;
        java.util.LinkedHashMap<String, ItemStack> materials = new java.util.LinkedHashMap<>();
        for (HologramCapture.Cell cell : planned.cells) {
            if (cell.anchor || cell.block == net.minecraft.init.Blocks.air) continue;
            require(!cell.unknown && cell.block != null, type + "_service_known_shell");
            ItemStack stack = new ItemStack(cell.block, 1, cell.meta);
            require(stack.getItem() != null, type + "_registered_shell_item");
            String key = Item.itemRegistry.getNameForObject(stack.getItem()) + ":" + stack.getItemDamage();
            if (materials.containsKey(key)) materials.get(key).stackSize++;
            else materials.put(key, stack);
            targets++;
        }
        for (ItemStack stack : materials.values()) while (stack.stackSize > 0) {
            require(slot < 36, "material_fixture_inventory_capacity");
            ItemStack batch = stack.copy();
            batch.stackSize = Math.min(stack.stackSize, stack.getMaxStackSize());
            stack.stackSize -= batch.stackSize;
            player.inventory.mainInventory[slot++] = batch;
        }
        HologramService.handle(player, action(session, "build"));
        for (int attempt = 0; attempt < 128
            && ((Integer) get(session, "job") == 1 || (Boolean) get(session, "capturePending")); attempt++) {
            world.getWorldInfo()
                .incrementTotalWorldTime(world.getTotalWorldTime() + 20);
            HologramService.tick();
        }
        require((Integer) get(session, "job") != 1, type + "_build_completes");
        int placed = 0;
        for (HologramCapture.Cell cell : planned.cells) {
            if (cell.anchor || cell.block == net.minecraft.init.Blocks.air) continue;
            require(
                world.getBlock(cell.x, cell.y, cell.z) == cell.block
                    && world.getBlockMetadata(cell.x, cell.y, cell.z) == cell.meta,
                type + "_survival_shell_placed_"
                    + cell.x
                    + "_"
                    + cell.y
                    + "_"
                    + cell.z
                    + " status="
                    + get(session, "status"));
            placed++;
        }
        require(world.getTileEntity(x, 80, 48) == base, type + "_controller_preserved");
        int remaining = 0;
        for (int i = 1; i < 36; i++)
            if (player.inventory.mainInventory[i] != null) remaining += player.inventory.mainInventory[i].stackSize;
        require(remaining == 0 && placed == targets, type + "_materials_consumed_once");
        System.out.println(
            PREFIX + "MACHINE PASS id="
                + id
                + " type="
                + type
                + " cells="
                + planned.cells.size()
                + " placed="
                + placed
                + " channelChanges="
                + changed);
    }

    private HologramCapture collect(MTEEnhancedMultiBlockBase<?> machine, ItemStack signal) {
        HologramCapture capture = null;
        for (int i = 0; i < 128; i++) {
            capture = HologramCapture.collect(machine, signal, machine.getExtendedFacing(), capture);
            if (!capture.pending) break;
        }
        require(capture != null && !capture.pending && !capture.incomplete, "real_capture_complete");
        return capture;
    }

    private void settleCapture(WorldServer world, Object session) throws Exception {
        for (int i = 0; i < 128 && (Boolean) get(session, "capturePending"); i++) {
            world.getWorldInfo()
                .incrementTotalWorldTime(world.getTotalWorldTime() + 20);
            HologramService.tick();
        }
        require(!(Boolean) get(session, "capturePending"), "service_capture_settled");
    }

    private static Object session(EntityPlayerMP player) throws Exception {
        Field sessions = HologramService.class.getDeclaredField("SESSIONS");
        sessions.setAccessible(true);
        return ((Map<?, ?>) sessions.get(null)).get(player.getUniqueID());
    }

    private static Object get(Object session, String name) throws Exception {
        Field field = session.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.get(session);
    }

    private static NBTTagCompound action(Object session, String op) throws Exception {
        NBTTagCompound action = new NBTTagCompound();
        action.setString("session", (String) get(session, "id"));
        action.setString("op", op);
        return action;
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        assertions++;
        if (!"fresh_fixture_air".equals(name)) System.out.println(PREFIX + "PASS " + name);
    }
}
