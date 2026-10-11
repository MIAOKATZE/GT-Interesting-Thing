package com.miaokatze.gtit.hologram;

import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;

/** Opt-in C08 entry regression. No Session construction or reflection is used. */
public final class HologramSmokeTest {

    private boolean finished;
    private int ticks, assertions;

    public static void registerIfEnabled() {
        if (Boolean.getBoolean("gtit.hologram.smoketest")) FMLCommonHandler.instance()
            .bus()
            .register(new HologramSmokeTest());
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.END || ++ticks < 40) return;
        finished = true;
        MinecraftServer server = MinecraftServer.getServer();
        try {
            run(server.worldServerForDimension(0));
            System.out.println("[GTIT-HOLOGRAM-SMOKE] FINAL PASS assertions=" + assertions);
        } catch (Throwable failure) {
            System.err.println("[GTIT-HOLOGRAM-SMOKE] FINAL FAIL " + failure);
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
                .contains("hologram-smoke"),
            "isolated_world_guard");
        FakePlayer player = FakePlayerFactory
            .get(world, new GameProfile(UUID.fromString("53ef3b84-b25b-4b81-a89f-88c27596992f"), "GTITHoloSmoke"));
        BaseMetaTileEntity base = HologramInteractionSmoke.createFixture(world, player, 48, 80, 48);
        Item projector = (Item) Item.itemRegistry.getObject("gtit:neko_hologram_projector");
        var tradeGroup = com.miaokatze.gtit.trade.v2.NekoTradeDatabase.INSTANCE
            .getTradeGroup(UUID.fromString("d7f5e8f2-247c-49af-b19b-d2f726ce34d0"));
        require(
            tradeGroup != null && tradeGroup.getTrades()
                .stream()
                .anyMatch(
                    trade -> trade.getToItems()
                        .stream()
                        .anyMatch(
                            output -> output.getBaseStack()
                                .getItem() == projector)),
            "dedicated_server_trade_group_contains_registered_projector_exchange");
        capabilityProfiles(base.getMetaTileEntity(), projector);
        player.inventory.mainInventory[0] = new ItemStack(projector);
        player.inventory.currentItem = 0;
        player.setPosition(48.5, 80, 46.5);
        player.theItemInWorldManager.setGameType(net.minecraft.world.WorldSettings.GameType.SURVIVAL);
        EmbeddedChannel embedded = new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        NetworkManager manager = new NetworkManager(false);
        // NetworkManager exposes no setter; only the transport is wired reflectively.
        for (Field field : NetworkManager.class.getDeclaredFields()) if (field.getType() == Channel.class) {
            field.setAccessible(true);
            field.set(manager, embedded);
        }
        player.playerNetServerHandler = new NetHandlerPlayServer(MinecraftServer.getServer(), manager, player);
        Field channel = HologramNetwork.class.getDeclaredField("channel");
        channel.setAccessible(true);
        Object oldChannel = channel.get(null);
        CaptureNetwork capture = new CaptureNetwork();
        channel.set(null, capture);
        try {
            player.playerNetServerHandler
                .processPlayerBlockPlacement(decodePlacement(-1, -1, -1, 255, player.getHeldItem(), 0, 0, 0));
            require(capture.state != null && !capture.state.getBoolean("target"), "first_air_C08_opens_empty_target");
            HologramService.clear();
            world.getWorldInfo()
                .incrementTotalWorldTime(world.getTotalWorldTime() + 3);
            ItemStack before = player.getHeldItem();
            player.playerNetServerHandler
                .processPlayerBlockPlacement(decodePlacement(48, 80, 48, 2, before, .5F, .5F, .5F));
            require(
                capture.state.getBoolean("target") && capture.state.getBoolean("supported"),
                "first_block_C08_opens_real_EBF");
            net.minecraft.nbt.NBTTagList capabilityRows = capture.state.getTagList("capabilities", 10);
            boolean coilObserved = false, hatchPresence = false;
            for (int i = 0; i < capabilityRows.tagCount(); i++) {
                NBTTagCompound row = capabilityRows.getCompoundTagAt(i);
                if ("coil".equals(row.getString("id"))) coilObserved = row.getInteger("operations") > 0;
                if ("gt_hatch".equals(row.getString("id")))
                    hatchPresence = "presence".equals(row.getString("kind")) && row.getTagList("options", 10)
                        .tagCount() == 2;
            }
            require(coilObserved, "real_C08_capture_observes_named_coil_channel_mixin_calls");
            require(hatchPresence, "gt_hatch_is_presence_option_without_fabricated_tier_range");
            Field traceActive = HologramChannelTrace.class.getDeclaredField("ACTIVE");
            traceActive.setAccessible(true);
            require(
                ((ThreadLocal<?>) traceActive.get(null)).get() == null,
                "capture_finally_clears_named_channel_trace_threadlocal");
            require(
                capture.state.getTagList("cells", 10)
                    .tagCount() == 36,
                "controller_anchor_plus_35_structure_cells");
            int anchors = 0;
            net.minecraft.nbt.NBTTagList rows = capture.state.getTagList("cells", 10);
            for (int i = 0; i < rows.tagCount(); i++) if (rows.getCompoundTagAt(i)
                .getBoolean("anchor")) anchors++;
            require(
                anchors == 1 && world.getTileEntity(48, 80, 48) == base,
                "controller_anchor_is_explicit_and_preserved");
            require(player.getHeldItem() != before, "vanilla_C08_replaces_held_stack_with_copy");
            NBTTagCompound configure = action(capture.state, "configure");
            configure.setBoolean("noHatches", true);
            configure.setLong("uiSequence", 71);
            HologramService.handle(player, configure);
            require(
                capture.state.getLong("uiSequence") == 71 && capture.state.getBoolean("noHatches"),
                "C08_copy_keeps_session_valid_for_first_action");
            require(
                player.getHeldItem()
                    .getTagCompound()
                    .getInteger("gtitHologramMain") == capture.state.getInteger("main"),
                "persist_updates_current_held_NBT");
            NBTTagCompound forgedConfiguration = action(capture.state, "configure");
            NBTTagCompound forgedChannels = (NBTTagCompound) capture.state.getCompoundTag("channels")
                .copy();
            forgedChannels.setInteger("unregistered.attack.channel", 1);
            forgedConfiguration.setTag("channels", forgedChannels);
            forgedConfiguration.setInteger("main", 2);
            HologramService.handle(player, forgedConfiguration);
            require(
                capture.state.getInteger("main") == 1 && !capture.state.getCompoundTag("channels")
                    .hasKey("unregistered.attack.channel"),
                "real_service_forged_channel_rejects_configuration_atomically");
            require(
                player.getHeldItem()
                    .getTagCompound()
                    .getInteger("gtitHologramMain") == 1,
                "rejected_configuration_does_not_mutate_current_tool_NBT");
            require(
                base.getFrontFacing() == net.minecraftforge.common.util.ForgeDirection.NORTH,
                "configure_preserves_actual_controller_facing");
            try (java.io.FileOutputStream output = new java.io.FileOutputStream("hologram-client-state.nbt")) {
                net.minecraft.nbt.CompressedStreamTools.writeCompressed(capture.state, output);
            }
            HologramService.handle(player, action(capture.state, "start"));
            require(capture.state.getInteger("job") != 1, "whole_plan_missing_materials_preflight_blocks_start");
            // Supply the server-declared plan, then exercise veto rollback through the actual job scheduler.
            net.minecraft.nbt.NBTTagList materials = capture.state.getTagList("materials", 10);
            int slot = 2;
            for (int i = 0; i < materials.tagCount(); i++) {
                NBTTagCompound row = materials.getCompoundTagAt(i);
                ItemStack material = ItemStack.loadItemStackFromNBT(row);
                if (material == null || row.getInteger("required") <= 0) continue;
                material.stackSize = row.getInteger("required");
                player.inventory.mainInventory[slot++] = material;
            }
            world.getWorldInfo()
                .incrementTotalWorldTime(world.getTotalWorldTime() + 1);
            HologramService.handle(player, action(capture.state, "scan"));
            require(capture.state.getInteger("missing") == 0, "full_plan_materials_satisfy_preflight");
            player.getHeldItem()
                .getTagCompound()
                .setString("gtitSmokeSentinel", "preserve-unrelated-current-tool-NBT");
            HologramService.handle(player, action(capture.state, "configure"));
            require(
                "preserve-unrelated-current-tool-NBT".equals(
                    player.getHeldItem()
                        .getTagCompound()
                        .getString("gtitSmokeSentinel")),
                "persist_preserves_unrelated_NBT_on_current_held_stack");
            NBTTagCompound staleStart = action(capture.state, "start");
            staleStart.setLong("planRevision", capture.state.getLong("planRevision") - 1);
            HologramService.handle(player, staleStart);
            require(capture.state.getInteger("job") != 1, "stale_plan_revision_cannot_start_job");
            net.minecraft.nbt.NBTTagList plannedRows = capture.state.getTagList("cells", 10);
            NBTTagCompound changedCell = null;
            for (int i = 0; i < plannedRows.tagCount(); i++) {
                NBTTagCompound row = plannedRows.getCompoundTagAt(i);
                if (!row.getBoolean("anchor") && "PLACE".equals(row.getString("operation"))
                    && world.isAirBlock(row.getInteger("x"), row.getInteger("y"), row.getInteger("z"))) {
                    changedCell = row;
                    break;
                }
            }
            require(changedCell != null, "safe_non_controller_plan_target_available_for_world_change_probe");
            int changedX = changedCell.getInteger("x"), changedY = changedCell.getInteger("y"),
                changedZ = changedCell.getInteger("z");
            long previousRevision = capture.state.getLong("planRevision");
            NBTTagCompound previouslyConfirmedStart = action(capture.state, "start");
            try {
                require(
                    world.setBlock(changedX, changedY, changedZ, net.minecraft.init.Blocks.stone, 0, 3),
                    "world_change_probe_places_safe_stone");
                HologramService.handle(player, previouslyConfirmedStart);
                require(
                    capture.state.getInteger("job") != 1 && capture.state.getLong("planRevision") > previousRevision,
                    "changed_world_rejects_confirmed_plan_and_refreshes_revision");
                require(
                    world.getBlock(changedX, changedY, changedZ) == net.minecraft.init.Blocks.stone,
                    "rejected_start_does_not_change_probe_world_block");
            } finally {
                world.setBlockToAir(changedX, changedY, changedZ);
            }
            HologramService.handle(player, action(capture.state, "scan"));
            require(
                capture.state.getInteger("missing") == 0 && world.isAirBlock(changedX, changedY, changedZ),
                "world_change_probe_restored_and_plan_rescanned");
            PlaceVeto veto = new PlaceVeto();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(veto);
            try {
                HologramService.handle(player, action(capture.state, "start"));
                require(capture.state.getInteger("job") == 1, "real_C08_session_starts_bounded_job");
                int initialCoils = countCoils(player);
                HologramService.tick();
                require(
                    "ACK".equals(capture.state.getString("phase")) && capture.state.getIntArray("pending").length == 0,
                    "first_tick_executes_batch_without_prepare");
                require(
                    veto.hits > 0 && capture.state.getInteger("job") == 5,
                    "place_event_veto_returns_partial_batch_result");
                require(countCoils(player) == initialCoils, "veto_restores_consumed_coil_material");
                require(world.isAirBlock(veto.x, veto.y, veto.z), "veto_restores_original_world_cell");
            } finally {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);
            }
            HologramService.handle(player, action(capture.state, "scan"));
            HologramService.handle(player, action(capture.state, "start"));
            require(capture.state.getInteger("job") == 1, "event_veto_preserves_tool_identity_for_new_batch");
            HologramService.handle(player, action(capture.state, "pause"));
            int completed = capture.state.getInteger("completed");
            world.getWorldInfo()
                .incrementTotalWorldTime(world.getTotalWorldTime() + 20);
            HologramService.tick();
            require(
                capture.state.getInteger("job") == 2 && capture.state.getInteger("completed") == completed,
                "paused_job_does_not_advance");
            NBTTagCompound cancel = action(capture.state, "cancel");
            cancel.setString("session", "forged");
            int job = capture.state.getInteger("job");
            HologramService.handle(player, cancel);
            require(capture.state.getInteger("job") == job, "forged_session_is_rejected");
            FakePlayer stranger = FakePlayerFactory
                .get(world, new GameProfile(UUID.fromString("7d36e922-3b19-4f92-a5e8-bd5c71614202"), "GTITHoloOther"));
            HologramService.handle(stranger, action(capture.state, "cancel"));
            require(capture.state.getInteger("job") == job, "other_player_cannot_act_on_known_session_id");
            player.inventory.mainInventory[1] = player.getHeldItem()
                .copy();
            player.inventory.currentItem = 1;
            HologramService.handle(player, action(capture.state, "configure"));
            require(capture.state.getInteger("job") == 4, "switching_to_copy_in_another_slot_invalidates_session");
        } finally {
            channel.set(null, oldChannel);
            embedded.finish();
            Object pending;
            while ((pending = embedded.readOutbound()) != null) io.netty.util.ReferenceCountUtil.release(pending);
            HologramService.clear();
        }
    }

    static NBTTagCompound action(NBTTagCompound state, String op) {
        NBTTagCompound action = (NBTTagCompound) state.copy();
        action.setString("op", op);
        return action;
    }

    /** C08 wire decoding works on a dedicated server where its coordinate constructor is stripped. */
    private static C08PacketPlayerBlockPlacement decodePlacement(int x, int y, int z, int side, ItemStack item,
        float hitX, float hitY, float hitZ) throws java.io.IOException {
        io.netty.buffer.ByteBuf bytes = io.netty.buffer.Unpooled.buffer();
        try {
            net.minecraft.network.PacketBuffer wire = new net.minecraft.network.PacketBuffer(bytes);
            wire.writeInt(x);
            wire.writeByte(y);
            wire.writeInt(z);
            wire.writeByte(side);
            wire.writeItemStackToBuffer(item);
            wire.writeByte((int) (hitX * 16));
            wire.writeByte((int) (hitY * 16));
            wire.writeByte((int) (hitZ * 16));
            C08PacketPlayerBlockPlacement packet = new C08PacketPlayerBlockPlacement();
            packet.readPacketData(wire);
            if (wire.readableBytes() != 0) throw new java.io.IOException("C08 decoder left unread payload");
            return packet;
        } finally {
            bytes.release();
        }
    }

    private static final class CaptureNetwork extends SimpleNetworkWrapper {

        NBTTagCompound state;

        CaptureNetwork() {
            super("gtit_smoke_observer");
        }

        @Override
        public void sendTo(IMessage message, EntityPlayerMP player) {
            if (message instanceof HologramNetwork.State)
                state = (NBTTagCompound) ((HologramNetwork.State) message).tag.copy();
        }
    }

    public static final class PlaceVeto {

        int hits, x, y, z;

        @SubscribeEvent
        public void place(net.minecraftforge.event.world.BlockEvent.PlaceEvent event) {
            if (event.world.getBlock(event.x, event.y, event.z) == gregtech.api.GregTechAPI.sBlockCasings5) {
                hits++;
                x = event.x;
                y = event.y;
                z = event.z;
                event.setCanceled(true);
            }
        }
    }

    private static int countCoils(FakePlayer player) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack != null && stack.getItem() == Item.getItemFromBlock(gregtech.api.GregTechAPI.sBlockCasings5))
                count += stack.stackSize;
        return count;
    }

    private void capabilityProfiles(Object ebf, Item item) {
        Object extractor = new gregtech.common.tileentities.machines.multi.MTEIndustrialExtractor(
            "gtit.smoke.extractor");
        Object implosion = new gregtech.common.tileentities.machines.multi.MTEElectricImplosionCompressor(
            "gtit.smoke.implosion");
        Object tower = new gregtech.common.tileentities.machines.multi.MTEMegaDistillationTower("gtit.smoke.tower");
        gregtech.common.tileentities.machines.multi.MTEIndustrialCokeOven coke = new gregtech.common.tileentities.machines.multi.MTEIndustrialCokeOven(
            "gtit.smoke.coke");
        Object[] contexts = { ebf, extractor, implosion, tower, coke };
        String[][] ids = { { "coil", "gt_hatch" }, { "item_pipe", "glass" }, { "glass", "piston_block" }, { "height" },
            { "coil", "coke_oven_casing", "length" } };
        int[][] maxima = { { 14, 1 }, { 8, 11 },
            { 11, gregtech.common.tileentities.machines.multi.MTEElectricImplosionCompressor.getTierBlockList()
                .size() },
            { 5 }, { 14, 2, 16 } };
        for (int p = 0; p < contexts.length; p++) {
            HologramCapabilities.Result profile = HologramCapabilities.describe(contexts[p], new ItemStack(item), null);
            for (int r = 0; r < ids[p].length; r++) {
                String id = ids[p][r];
                int maximum = maxima[p][r];
                NBTTagCompound legal = new NBTTagCompound();
                legal.setInteger(id, maximum);
                require(
                    maximum > 0 && profile.validConfiguration(1, legal),
                    "profile_" + p + "_" + id + "_upper_bound_valid");
                legal.setInteger(id, maximum + 1);
                require(profile.validConfiguration(1, legal), "profile_" + p + "_" + id + "_raw_signal_accepted");
                legal.setInteger(id, 0);
                require(!profile.validConfiguration(1, legal), "profile_" + p + "_" + id + "_explicit_zero_rejected");
                require(
                    profile.encodeOption(legal, id, 0) && !legal.hasKey(id),
                    "profile_" + p + "_" + id + "_inherit_removes_key");
            }
            NBTTagCompound forged = new NBTTagCompound();
            forged.setInteger("unregistered.attack.channel", 1);
            require(profile.validConfiguration(1, forged), "profile_" + p + "_custom_channel_accepted");
            require(
                profile.validConfiguration(65, new NBTTagCompound()),
                "profile_" + p + "_main_raw_integer_accepted");
            HologramCapabilities.Result saved = HologramCapabilities.describe(contexts[p], new ItemStack(item), null)
                .withSavedChannels(forged);
            require(saved.validConfiguration(1, forged), "profile_" + p + "_saved_foreign_key_retained");
            forged.setInteger("unregistered.attack.channel", 2);
            require(saved.validConfiguration(1, forged), "profile_" + p + "_saved_foreign_key_edit_accepted");
            require(
                saved.sanitizeChannels(forged)
                    .hasKey("unregistered.attack.channel"),
                "profile_" + p + "_custom_key_used_as_current_machine_signal");
        }
        coke.setCoilLevel(gregtech.api.enums.HeatingCoilLevel.MAX);
        HologramCapabilities.Result maximumCoil = HologramCapabilities.describe(coke, new ItemStack(item), null);
        NBTTagCompound length = new NBTTagCompound();
        length.setInteger("length", 1);
        require(maximumCoil.validConfiguration(1, length), "MAX_coke_inactive_channel_remains_editable");
        require(maximumCoil.validConfiguration(17, new NBTTagCompound()), "MAX_coke_main_accepts_raw_integer");
    }

    private void require(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        assertions++;
        System.out.println("[GTIT-HOLOGRAM-SMOKE] ASSERT PASS " + label);
    }
}
