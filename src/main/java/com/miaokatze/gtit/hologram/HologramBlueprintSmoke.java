package com.miaokatze.gtit.hologram;

import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import gregtech.api.GregTechAPI;
import gregtech.api.covers.CoverRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.util.GTUtility;
import gregtech.common.misc.WirelessNetworkManager;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;

/** Opt-in dedicated-world receipts from public service actions and actual world/inventory observations. */
public final class HologramBlueprintSmoke {

    private static final String PREFIX = "[GTIT-HOLOGRAM-BLUEPRINT-SMOKE] ";
    private static final UUID USER = UUID.fromString("914aa60c-3f03-46a1-a681-2ca6bb545d73");
    private static final UUID SOURCE_OWNER = UUID.fromString("b372ac1e-5f4e-46ed-bc74-73c419ce0e43");
    private final CaptureNetwork capture = new CaptureNetwork();
    private int assertions;
    private WorldServer world;
    private FakePlayer player;
    private String blueprintId;

    private HologramBlueprintSmoke() {}

    public static void run(WorldServer world) throws Exception {
        HologramBlueprintSmoke smoke = new HologramBlueprintSmoke();
        try {
            smoke.exercise(world);
            System.out.println(PREFIX + "FINAL PASS assertions=" + smoke.assertions);
        } catch (Throwable failure) {
            System.err.println(PREFIX + "FINAL FAIL " + failure);
            throw failure;
        }
    }

    private void exercise(WorldServer serverWorld) throws Exception {
        world = serverWorld;
        require(
            world.getWorldInfo()
                .getWorldName()
                .startsWith("hologram-smoke-"),
            "isolated_world_guard");
        player = FakePlayerFactory.get(world, new GameProfile(USER, "GTITBlueprint"));
        player.inventory.mainInventory = new ItemStack[36];
        player.inventory.mainInventory[0] = new ItemStack(
            (Item) Item.itemRegistry.getObject("gtit:neko_hologram_projector"));
        player.inventory.currentItem = 0;
        player.theItemInWorldManager.setGameType(net.minecraft.world.WorldSettings.GameType.SURVIVAL);
        player.capabilities.allowEdit = true;
        player.setPosition(180.5, 80, 174.5);
        EmbeddedChannel transport = new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        NetworkManager manager = new NetworkManager(false);
        for (Field field : NetworkManager.class.getDeclaredFields()) if (field.getType() == Channel.class) {
            field.setAccessible(true);
            field.set(manager, transport);
        }
        player.playerNetServerHandler = new NetHandlerPlayServer(MinecraftServer.getServer(), manager, player);
        Field channel = HologramNetwork.class.getDeclaredField("channel");
        channel.setAccessible(true);
        Object previous = channel.get(null);
        channel.set(null, capture);
        HologramService.clear();
        try {
            sourceFixture();
            areaAndSharing();
            placement();
            wirelessEnergy();
        } finally {
            HologramService.clear();
            channel.set(null, previous);
            transport.finish();
            Object pending;
            while ((pending = transport.readOutbound()) != null) io.netty.util.ReferenceCountUtil.release(pending);
        }
    }

    private void sourceFixture() {
        BaseMetaTileEntity first = HologramInteractionSmoke.createFixture(world, player, 176, 80, 176);
        HologramInteractionSmoke.createFixture(world, player, 190, 80, 176);
        first.setOwnerName("OriginalBlueprintOwner");
        first.setOwnerUuid(SOURCE_OWNER);
        ItemStack cover = ItemList.Cover_Controller.get(1);
        require(CoverRegistry.isCover(cover), "fixture_real_registered_GT_cover");
        first.attachCover(CoverRegistry.buildCover(cover, ForgeDirection.UP, first));
        require(
            first.getCoverAtSide(ForgeDirection.UP)
                .isValid(),
            "fixture_real_GT_cover_attached");
        require(world.setBlock(178, 80, 176, Blocks.chest, 5, 3), "fixture_real_chest_created");
        TileEntityChest chest = (TileEntityChest) world.getTileEntity(178, 80, 176);
        ItemStack treasure = new ItemStack(Items.diamond, 7);
        treasure.setStackDisplayName("Blueprint NBT receipt");
        chest.setInventorySlotContents(4, treasure);
        NBTTagCompound chestConfiguration = new NBTTagCompound();
        chest.writeToNBT(chestConfiguration);
        chestConfiguration.setString("CustomName", "Blueprint chest configuration receipt");
        chest.readFromNBT(chestConfiguration);
        world.setBlock(179, 80, 176, Blocks.stone, 0, 3);
        world.setBlock(180, 80, 176, Blocks.gold_block, 0, 3);
        world.setBlockMetadataWithNotify(178, 80, 176, 5, 3);
        require(stock(Items.diamond) == 0, "captured_inventory_is_world_NBT_not_player_stock");
    }

    private void areaAndSharing() throws Exception {
        HologramService.toggleAreaSelection(player);
        HologramSmokeTest.flushTransport();
        require(capture.state.getBoolean("areaSelecting"), "public_toggle_enters_region_selection");
        HologramService.areaPoint(player, 1, 176, 80, 176, false);
        HologramSmokeTest.flushTransport();
        HologramService.areaPoint(player, 2, 190, 80, 176, false);
        HologramSmokeTest.flushTransport();
        require(capture.state.getIntArray("areaPoints").length == 6, "both_region_points_have_server_receipt");
        HologramService.toggleAreaSelection(player);
        HologramSmokeTest.flushTransport();
        require(capture.state.getBoolean("areaImporting"), "closing_selection_starts_resumable_import");
        settle();
        require(capture.state.getBoolean("areaImported"), "real_region_import_completes");
        require(
            capture.state.getTagList("cells", 10)
                .tagCount() == 5,
            "region_captures_arbitrary_blocks_and_two_controllers");
        NBTTagList structures = capture.state.getTagList("structures", 10);
        require(structures.tagCount() == 2, "two_actual_EBF_controllers_discovered");
        require(
            structures.getCompoundTagAt(0)
                .getBoolean("declared")
                && structures.getCompoundTagAt(1)
                    .getBoolean("declared"),
            "both_structures_use_real_complete_declaration_capture");
        require(
            stock(Items.diamond) == 0
                && ((TileEntityChest) world.getTileEntity(178, 80, 176)).getStackInSlot(4).stackSize == 7,
            "import_does_not_mutate_world_or_extract_inventory");

        NBTTagCompound flags = new NBTTagCompound();
        flags.setIntArray("indices", new int[] { indexAt(180, 80, 176) });
        flags.setBoolean("excluded", true);
        flags.setString("prompt", "保留这格金块作为手工接口");
        action("editCellFlags", flags);
        require(rowAt(180, 80, 176).getBoolean("excluded"), "server_cell_exclusion_has_snapshot_receipt");
        NBTTagCompound hint = new NBTTagCompound();
        hint.setIntArray("indices", new int[] { indexAt(178, 80, 176), indexAt(180, 80, 176) });
        hint.setString("prompt", "胸箱与接口提示区域");
        action("editHintArea", hint);
        NBTTagCompound save = new NBTTagCompound();
        save.setString("name", "Runtime blueprint receipt");
        save.setString("prompt", "真实多结构蓝图与NBT往返");
        action("saveBlueprint", save);
        blueprintId = capture.state.getString("virtualPresetId");
        HologramPresets.Preset saved = HologramPresets.get(blueprintId);
        require(
            saved != null && saved.schema == 2 && saved.cells.size() == 5,
            "public_save_publishes_schema2_region_blueprint");
        HologramPresets.Cell chest = cell(saved, 2, 0, 0);
        require(chest.metadata == 5, "saved_arbitrary_block_metadata_is_original_world_metadata");
        NBTTagCompound chestData = HologramBlueprintNbt.decode(chest.tileNbt);
        require(
            chestData.getTagList("Items", 10)
                .tagCount() == 1,
            "saved_chest_inventory_NBT_is_retained");
        ItemStack treasure = ItemStack.loadItemStackFromNBT(
            chestData.getTagList("Items", 10)
                .getCompoundTagAt(0));
        require(
            treasure.getItem() == Items.diamond && treasure.stackSize == 7
                && "Blueprint NBT receipt".equals(treasure.getDisplayName()),
            "saved_chest_item_NBT_and_count_are_exact");
        require(
            "Blueprint chest configuration receipt".equals(chestData.getString("CustomName")),
            "saved_chest_non_inventory_configuration_is_exact");
        NBTTagCompound machineData = HologramBlueprintNbt.decode(cell(saved, 0, 0, 0).tileNbt);
        require(
            machineData.getString("mOwnerName")
                .isEmpty()
                && machineData.getString("mOwnerUuid")
                    .isEmpty(),
            "stored_GT_NBT_strips_original_owner");
        require(
            machineData.getTagList("gt.covers", 10)
                .tagCount() == 1,
            "stored_GT_NBT_preserves_real_cover");
        require(
            SOURCE_OWNER.equals(((BaseMetaTileEntity) world.getTileEntity(176, 80, 176)).getOwnerUuid()),
            "owner_sanitization_does_not_mutate_source_machine");
        require(cell(saved, 4, 0, 0).excluded && saved.hintAreas.size() == 1, "exclude_and_hint_area_are_saved");

        String fileId = "runtime_smoke_" + UUID.randomUUID()
            .toString()
            .replace("-", "");
        NBTTagCompound share = new NBTTagCompound();
        share.setString("presetId", blueprintId);
        share.setString("fileId", fileId);
        Path path = HologramBlueprintSharing.resolve(fileId);
        try {
            action("exportBlueprint", share);
            require(Files.isRegularFile(path), "public_export_writes_separate_share_file");
            HologramPresets.Preset imported = HologramPresets.importBlueprint(fileId);
            require(
                imported.schema == 2 && imported.cells.size() == 5
                    && cell(imported, 4, 0, 0).excluded
                    && "胸箱与接口提示区域".equals(cell(imported, 4, 0, 0).prompt)
                    && imported.hintAreas.size() == 1
                    && "真实多结构蓝图与NBT往返".equals(imported.prompt),
                "share_roundtrip_retains_exclusion_cell_prompt_region_prompt_and_blueprint_prompt");
            require(
                HologramBlueprintNbt.decode(cell(imported, 2, 0, 0).tileNbt)
                    .equals(chestData),
                "share_roundtrip_retains_arbitrary_full_tile_NBT");
            int oldSize = HologramPresets.all()
                .size();
            action("importBlueprint", share);
            require(
                HologramPresets.all()
                    .size() == oldSize + 1,
                "public_import_adds_independent_library_entry");
        } finally {
            Files.deleteIfExists(path);
        }
        NBTTagCompound structure = new NBTTagCompound();
        structure.setString(
            "structureId",
            structures.getCompoundTagAt(1)
                .getString("id"));
        structure.setBoolean("enabled", false);
        action("structureState", structure);
        require(
            !capture.state.getTagList("structures", 10)
                .getCompoundTagAt(1)
                .getBoolean("enabled"),
            "second_structure_can_be_disabled_independently");
        structure.setBoolean("enabled", true);
        action("structureState", structure);
    }

    private void placement() throws Exception {
        HologramPresets.Preset saved = HologramPresets.get(blueprintId);
        supply(saved, true);
        beginPlacement(208, blueprintId);
        require(
            capture.state.getBoolean("blueprintAwaitingConfirmation") && !capture.state.getBoolean("virtualPlacement")
                && !capture.state.getBoolean("moving"),
            "choosing_blueprint_waits_for_explicit_confirmation_before_projection");
        require(
            capture.state.getInteger("missing") > 0 && stock(Item.getItemFromBlock(Blocks.chest)) == 1,
            "material_preview_reports_real_missing_stock_without_deduction");
        require(stock(Items.diamond) == 7, "preview_keeps_real_inventory_contents_material_untouched");
        snapshotReceipt();
        NBTTagCompound stale = new NBTTagCompound();
        stale.setLong("planRevision", capture.state.getLong("planRevision") - 1);
        stale.setString("session", capture.state.getString("session"));
        stale.setString("op", "confirmBlueprintPlacement");
        advance();
        HologramService.handle(player, stale);
        HologramSmokeTest.flushTransport();
        require(capture.state.getBoolean("blueprintAwaitingConfirmation"), "stale_material_confirmation_is_rejected");
        action("confirmBlueprintPlacement");
        require(
            capture.state.getBoolean("virtualPlacement") && capture.state.getBoolean("moving")
                && stock(Item.getItemFromBlock(Blocks.chest)) == 1
                && world.isAirBlock(178, 80, 208),
            "valid_confirmation_starts_preview_without_world_or_inventory_mutation");
        HologramService.lockVirtual(player, 176, 80, 208, 1);
        HologramSmokeTest.flushTransport();
        action("build");
        require(
            capture.state.getBoolean("forcePlacementReady") && capture.state.getInteger("job") != 1
                && stock(Item.getItemFromBlock(Blocks.chest)) == 1
                && world.isAirBlock(178, 80, 208),
            "first_missing_material_build_requires_second_confirmation_and_changes_nothing");
        action("build");
        require(capture.state.getInteger("job") == 1, "second_missing_material_build_starts_existing_stock_job");
        settle();
        require(
            world.getBlock(178, 80, 208) == Blocks.chest && stock(Item.getItemFromBlock(Blocks.chest)) == 0,
            "existing_stock_job_places_chest_and_deducts_exactly_once");
        require(
            world.isAirBlock(179, 80, 208) && world.isAirBlock(190, 80, 208),
            "existing_stock_job_does_not_invent_missing_stone_or_second_controller");
        require(world.isAirBlock(180, 80, 208), "excluded_gold_cell_never_builds");
        verifyChest(208, true);
        require(stock(Items.diamond) == 0, "restored_chest_contents_cost_exactly_seven_real_diamonds");
        verifyController(208, true);
        require(stock(ItemList.Cover_Controller.get(1)) == 0, "retained_cover_deducts_real_cover_material_once");

        // A fresh placement must return to choose/confirm even with the same selected tool preset.
        supply(saved, false);
        beginPlacement(240, blueprintId);
        require(
            capture.state.getBoolean("blueprintAwaitingConfirmation") && !capture.state.getBoolean("virtualPlacement"),
            "already_selected_blueprint_still_requires_confirmation_on_every_placement");
        action("confirmBlueprintPlacement");
        HologramService.lockVirtual(player, 176, 80, 240, 1);
        HologramSmokeTest.flushTransport();
        PlaceVeto veto = new PlaceVeto(178, 80, 240);
        MinecraftForge.EVENT_BUS.register(veto);
        try {
            action("build");
            settle();
            require(veto.hits > 0, "real_forge_place_permission_veto_was_observed");
            require(veto.restoredTileObserved, "permission_event_observes_actual_restored_tile_before_veto");
            require(
                world.isAirBlock(178, 80, 240) && world.getTileEntity(178, 80, 240) == null,
                "permission_veto_restores_original_world_and_removes_restored_tile_NBT");
            require(
                stock(Item.getItemFromBlock(Blocks.chest)) == 1 && stock(Items.diamond) == 7,
                "permission_veto_restores_block_and_seven_contents_materials_without_duplication");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
        }
        action("build");
        settle();
        require(
            world.getBlock(178, 80, 240) == Blocks.chest && stock(Item.getItemFromBlock(Blocks.chest)) == 0,
            "retry_after_permission_veto_consumes_restored_chest_once");
        verifyChest(240, true);
        verifyController(240, true);
        require(
            world.getBlock(179, 80, 240) == Blocks.stone
                && world.getTileEntity(190, 80, 240) instanceof BaseMetaTileEntity
                && USER.equals(((BaseMetaTileEntity) world.getTileEntity(190, 80, 240)).getOwnerUuid()),
            "full_material_retry_builds_second_actual_controller_with_current_owner");
        int remaining = 0;
        for (int i = 1; i < player.inventory.mainInventory.length; i++)
            if (player.inventory.mainInventory[i] != null) remaining += player.inventory.mainInventory[i].stackSize;
        require(remaining == 0, "full_blueprint_block_and_cover_materials_are_consumed_exactly_once");

        supply(saved, false);
        ItemStack coverMaterial = ItemList.Cover_Controller.get(1);
        for (int i = 1; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == coverMaterial.getItem()
                && stack.getItemDamage() == coverMaterial.getItemDamage()) player.inventory.mainInventory[i] = null;
        }
        beginPlacement(336, blueprintId);
        require(capture.state.getInteger("missing") > 0, "real_missing_cover_is_in_material_preview");
        action("confirmBlueprintPlacement");
        HologramService.lockVirtual(player, 176, 80, 336, 1);
        HologramSmokeTest.flushTransport();
        action("build");
        require(capture.state.getBoolean("forcePlacementReady"), "cover_shortage_requires_second_build_confirmation");
        action("build");
        settle();
        require(
            world.isAirBlock(176, 80, 336) && stock(
                saved.cells.stream()
                    .filter(cell -> cell.controller)
                    .findFirst()
                    .get()
                    .stack())
                == 1,
            "missing_cover_cannot_create_controller_or_consume_its_block_material");
        player.inventory.mainInventory[35] = coverMaterial.copy();
        action("build");
        settle();
        verifyController(336, true);
        require(stock(coverMaterial) == 0, "supplying_real_cover_allows_retry_and_deducts_cover_once");

        NBTTagCompound retention = new NBTTagCompound();
        retention.setString("name", "Runtime no covers receipt");
        retention.setBoolean("retainNbt", true);
        retention.setBoolean("retainCovers", false);
        action("saveBlueprint", retention);
        String noCovers = capture.state.getString("virtualPresetId");
        supply(HologramPresets.get(noCovers), false);
        beginPlacement(272, noCovers);
        action("confirmBlueprintPlacement");
        HologramService.lockVirtual(player, 176, 80, 272, 1);
        HologramSmokeTest.flushTransport();
        action("build");
        settle();
        verifyChest(272, true);
        verifyController(272, false);

        NBTTagCompound original = new NBTTagCompound();
        original.setString("presetId", blueprintId);
        action("editPreset", original);
        require(
            blueprintId.equals(capture.state.getString("virtualPresetId")),
            "covers_only_storage_case_reloads_original_complete_blueprint");
        retention.setString("name", "Runtime no NBT receipt");
        retention.setBoolean("retainNbt", false);
        retention.setBoolean("retainCovers", true);
        action("saveBlueprint", retention);
        String noNbt = capture.state.getString("virtualPresetId");
        HologramPresets.Preset coversOnly = HologramPresets.get(noNbt);
        require(
            !coversOnly.retainNbt && coversOnly.retainCovers
                && HologramBlueprintNbt.decode(cell(coversOnly, 0, 0, 0).tileNbt)
                    .getTagList("gt.covers", 10)
                    .tagCount() == 1,
            "covers_only_variant_stores_real_cover_payload_from_original_blueprint");
        supply(coversOnly, false);
        beginPlacement(304, noNbt);
        action("confirmBlueprintPlacement");
        HologramService.lockVirtual(player, 176, 80, 304, 1);
        HologramSmokeTest.flushTransport();
        action("build");
        settle();
        verifyChest(304, false);
        verifyController(304, true);

        supply(saved, false);
        removeStock(Items.diamond);
        beginPlacement(368, blueprintId);
        require(
            capture.state.getInteger("missing") > 0,
            "unpaid_captured_inventory_is_reported_as_real_material_shortage");
        action("confirmBlueprintPlacement");
        HologramService.lockVirtual(player, 176, 80, 368, 1);
        HologramSmokeTest.flushTransport();
        action("build");
        require(
            capture.state.getBoolean("forcePlacementReady") && world.isAirBlock(178, 80, 368),
            "unpaid_inventory_first_build_requests_second_confirmation_without_world_change");
        action("build");
        settle();
        require(
            world.isAirBlock(178, 80, 368) && stock(Item.getItemFromBlock(Blocks.chest)) == 1
                && stock(Items.diamond) == 0,
            "forced_partial_build_cannot_generate_unpaid_inventory_or_consume_its_chest");

        supply(saved, false);
        removeStock(Items.diamond);
        beginPlacement(400, blueprintId);
        Set<String> rootKeys = new LinkedHashSet<>();
        for (HologramPresets.Cell entry : saved.cells) {
            NBTTagCompound data = HologramBlueprintNbt.decode(entry.tileNbt);
            if (data != null) for (Object key : data.func_150296_c()) rootKeys.add((String) key);
        }
        rootKeys.remove("Items");
        NBTTagList selectedKeys = new NBTTagList();
        for (String key : rootKeys) selectedKeys.appendTag(new NBTTagString(key));
        NBTTagCompound selection = new NBTTagCompound();
        selection.setBoolean("all", false);
        selection.setTag("keys", selectedKeys);
        action("configurePlacementNbt", selection);
        require(
            capture.state.getInteger("missing") == 0,
            "explicit_inventory_entry_cancellation_removes_contents_cost_for_this_placement");
        action("confirmBlueprintPlacement");
        HologramService.lockVirtual(player, 176, 80, 400, 1);
        HologramSmokeTest.flushTransport();
        action("build");
        settle();
        verifyChest(400, true, false);
        verifyController(400, true);
        require(
            stock(Items.diamond) == 0
                && HologramBlueprintNbt.decode(cell(HologramPresets.get(blueprintId), 2, 0, 0).tileNbt)
                    .getTagList("Items", 10)
                    .getCompoundTagAt(0)
                    .getByte("Count") == 7,
            "temporary_inventory_downgrade_keeps_original_saved_blueprint_contents_unchanged");
        require(
            world.getBlock(178, 80, 176) == Blocks.chest
                && ((TileEntityChest) world.getTileEntity(178, 80, 176)).getStackInSlot(4).stackSize == 7,
            "all_placement_and_rollback_tests_leave_source_NBT_unchanged");
    }

    private void beginPlacement(int z, String id) {
        player.setPosition(180.5, 80, z - 1.5);
        for (int x = 176; x <= 190; x++) {
            world.getChunkFromBlockCoords(x, z);
            require(world.isAirBlock(x, 80, z), "fresh_destination_air_" + x + "_" + z);
        }
        advance();
        HologramService.openVirtual(player, 176, 80, z, 1, id);
        HologramSmokeTest.flushTransport();
        require(
            capture.state.getBoolean("presetBrowseForPlacement") && !capture.state.getBoolean("moving"),
            "Alt_open_always_shows_blueprint_chooser_before_preview_" + z);
        NBTTagCompound choose = new NBTTagCompound();
        choose.setString("presetId", id);
        action("choosePlacementPreset", choose);
    }

    private void wirelessEnergy() throws Exception {
        int id = -1;
        for (int i = 1; i < GregTechAPI.METATILEENTITIES.length; i++)
            if (GregTechAPI.METATILEENTITIES[i] instanceof MTEHatchEnergy
                && ((MTEHatchEnergy) GregTechAPI.METATILEENTITIES[i]).maxEUStore() >= 5000) {
                    id = i;
                    break;
                }
        require(id > 0, "registered_energy_hatch_with_real_capacity_available");
        player.setPosition(180.5, 80, 430.5);
        world.getChunkFromBlockCoords(176, 432);
        require(world.isAirBlock(176, 80, 432), "fresh_energy_source_air");
        world.setBlock(176, 80, 432, GregTechAPI.sBlockMachines, 0, 3);
        BaseMetaTileEntity source = (BaseMetaTileEntity) world.getTileEntity(176, 80, 432);
        source.setInitialValuesAsNBT(null, (short) id);
        source.setFrontFacing(ForgeDirection.NORTH);
        source.setOwnerName("OriginalBlueprintOwner");
        source.setOwnerUuid(SOURCE_OWNER);
        require(source.setStoredEU(5000) && source.getStoredEU() == 5000, "real_source_energy_hatch_contains_5000_EU");
        HologramService.toggleAreaSelection(player);
        HologramSmokeTest.flushTransport();
        HologramService.areaPoint(player, 1, 176, 80, 432, false);
        HologramSmokeTest.flushTransport();
        HologramService.areaPoint(player, 2, 176, 80, 432, false);
        HologramSmokeTest.flushTransport();
        HologramService.toggleAreaSelection(player);
        HologramSmokeTest.flushTransport();
        settle();
        NBTTagCompound save = new NBTTagCompound();
        save.setString("name", "Runtime wireless energy receipt");
        action("saveBlueprint", save);
        String energyId = capture.state.getString("virtualPresetId");
        HologramPresets.Preset energy = HologramPresets.get(energyId);
        require(
            energy != null && energy.cells.size() == 1
                && HologramBlueprintNbt.decode(energy.cells.get(0).tileNbt)
                    .getLong("mStoredEnergy") == 5000,
            "default_blueprint_save_retains_actual_stored_energy_NBT");
        WirelessNetworkManager.strongCheckOrAddUser(USER);
        BigInteger previousBalance = WirelessNetworkManager.getUserEU(USER);
        try {
            WirelessNetworkManager.setUserEU(USER, BigInteger.valueOf(100000));
            supply(energy, false);
            beginPlacement(464, energyId);
            require(
                capture.state.getInteger("missing") == 0 && WirelessNetworkManager.getUserEU(USER)
                    .equals(BigInteger.valueOf(100000)),
                "real_wireless_balance_satisfies_preview_without_energy_deduction");
            action("confirmBlueprintPlacement");
            HologramService.lockVirtual(player, 176, 80, 464, 1);
            HologramSmokeTest.flushTransport();
            PlaceVeto veto = new PlaceVeto(176, 80, 464);
            MinecraftForge.EVENT_BUS.register(veto);
            try {
                action("build");
                settle();
                require(
                    veto.hits > 0 && veto.restoredEnergyObserved,
                    "permission_event_sees_actual_restored_5000_EU_hatch_before_veto");
                require(
                    world.isAirBlock(176, 80, 464) && stock(
                        energy.cells.get(0)
                            .stack())
                        == 1
                        && WirelessNetworkManager.getUserEU(USER)
                            .equals(BigInteger.valueOf(100000)),
                    "permission_veto_refunds_real_wireless_EU_and_machine_material");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(veto);
            }
            action("build");
            settle();
            require(
                world.getTileEntity(176, 80, 464) instanceof BaseMetaTileEntity
                    && ((BaseMetaTileEntity) world.getTileEntity(176, 80, 464)).getStoredEU() == 5000
                    && WirelessNetworkManager.getUserEU(USER)
                        .equals(BigInteger.valueOf(95000)),
                "successful_energy_placement_costs_exactly_5000_real_wireless_EU");
            require(
                USER.equals(((BaseMetaTileEntity) world.getTileEntity(176, 80, 464)).getOwnerUuid()),
                "paid_energy_hatch_is_owned_by_current_user");

            WirelessNetworkManager.setUserEU(USER, BigInteger.ZERO);
            supply(energy, false);
            beginPlacement(496, energyId);
            require(capture.state.getInteger("missing") > 0, "empty_real_wireless_account_is_reported_as_shortage");
            action("confirmBlueprintPlacement");
            HologramService.lockVirtual(player, 176, 80, 496, 1);
            HologramSmokeTest.flushTransport();
            action("build");
            require(capture.state.getBoolean("forcePlacementReady"), "wireless_shortage_requires_second_confirmation");
            action("build");
            settle();
            require(
                world.isAirBlock(176, 80, 496) && stock(
                    energy.cells.get(0)
                        .stack())
                    == 1
                    && WirelessNetworkManager.getUserEU(USER)
                        .signum() == 0,
                "forced_partial_build_cannot_generate_unpaid_stored_energy");
            WirelessNetworkManager.setUserEU(USER, BigInteger.valueOf(5000));
            action("build");
            settle();
            require(
                world.getTileEntity(176, 80, 496) instanceof BaseMetaTileEntity
                    && ((BaseMetaTileEntity) world.getTileEntity(176, 80, 496)).getStoredEU() == 5000
                    && WirelessNetworkManager.getUserEU(USER)
                        .signum() == 0,
                "supplying_wireless_EU_allows_retry_with_exact_net_payment");
            require(
                source.getStoredEU() == 5000 && SOURCE_OWNER.equals(source.getOwnerUuid()),
                "energy_import_and_placement_leave_source_energy_and_owner_unchanged");
        } finally {
            WirelessNetworkManager.setUserEU(USER, previousBalance);
        }
    }

    private void verifyChest(int z, boolean retained) {
        verifyChest(z, retained, retained);
    }

    private void verifyChest(int z, boolean retained, boolean inventoryRetained) {
        require(world.getTileEntity(178, 80, z) instanceof TileEntityChest, "actual_chest_tile_placed_" + z);
        TileEntityChest chest = (TileEntityChest) world.getTileEntity(178, 80, z);
        ItemStack contents = chest.getStackInSlot(4);
        NBTTagCompound data = new NBTTagCompound();
        chest.writeToNBT(data);
        require(
            retained ? "Blueprint chest configuration receipt".equals(data.getString("CustomName"))
                : data.getString("CustomName")
                    .isEmpty(),
            "actual_chest_configuration_NBT_retention_" + retained + "_" + z);
        require(
            inventoryRetained
                ? contents != null && contents.getItem() == Items.diamond
                    && contents.stackSize == 7
                    && "Blueprint NBT receipt".equals(contents.getDisplayName())
                : contents == null,
            "actual_chest_inventory_NBT_retention_" + inventoryRetained + "_" + z);
        require(
            chest.xCoord == 178 && chest.yCoord == 80 && chest.zCoord == z,
            "placed_tile_NBT_coordinates_rewritten_to_destination_" + z);
        require(world.getBlockMetadata(178, 80, z) == 5, "placed_chest_metadata_retains_blueprint_direction_" + z);
    }

    private void verifyController(int z, boolean covers) {
        require(world.getTileEntity(176, 80, z) instanceof BaseMetaTileEntity, "actual_GT_controller_placed_" + z);
        BaseMetaTileEntity base = (BaseMetaTileEntity) world.getTileEntity(176, 80, z);
        require(
            USER.equals(base.getOwnerUuid()) && player.getCommandSenderName()
                .equals(base.getOwnerName()),
            "actual_GT_owner_rewritten_to_current_user_" + z);
        NBTTagCompound actualNbt = new NBTTagCompound();
        base.writeToNBT(actualNbt);
        StringBuilder sides = new StringBuilder();
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (sides.length() > 0) sides.append(',');
            sides.append(side.name())
                .append(':')
                .append(
                    base.getCoverAtSide(side)
                        .getCoverID());
        }
        System.out.println(
            PREFIX + "COVER receipt z="
                + z
                + " expected="
                + covers
                + " preset="
                + capture.state.getString("virtualPresetId")
                + " storeNbt="
                + capture.state.getBoolean("blueprintRetainNbt")
                + " storeCovers="
                + capture.state.getBoolean("blueprintRetainCovers")
                + " placementCovers="
                + capture.state.getBoolean("placementRetainCovers")
                + " actualSides="
                + sides
                + " actualCoverData="
                + actualNbt.getTagList("gt.covers", 10));
        require(
            base.getCoverAtSide(ForgeDirection.UP)
                .isValid() == covers,
            "actual_GT_cover_retention_" + covers + "_" + z);
    }

    private void action(String op) {
        action(op, new NBTTagCompound());
    }

    private void action(String op, NBTTagCompound fields) {
        require(capture.state != null, "service_snapshot_available_before_" + op);
        NBTTagCompound request = (NBTTagCompound) fields.copy();
        request.setString("session", capture.state.getString("session"));
        request.setString("op", op);
        request.setLong("planRevision", capture.state.getLong("planRevision"));
        advance();
        HologramService.handle(player, request);
        HologramSmokeTest.flushTransport();
    }

    private void advance() {
        world.getWorldInfo()
            .incrementTotalWorldTime(world.getTotalWorldTime() + 20);
    }

    private void settle() {
        for (int tick = 0; tick < 128; tick++) {
            advance();
            HologramService.tick();
            HologramSmokeTest.flushTransport();
            if (capture.state != null && !capture.state.getBoolean("areaImporting")
                && !capture.state.getBoolean("capturePending")
                && capture.state.getInteger("job") != 1) return;
        }
        throw new AssertionError("bounded service job/import timeout");
    }

    private int stock(Item item) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack != null && stack.getItem() == item) count += stack.stackSize;
        return count;
    }

    private void removeStock(Item item) {
        for (int i = 1; i < player.inventory.mainInventory.length; i++)
            if (player.inventory.mainInventory[i] != null && player.inventory.mainInventory[i].getItem() == item)
                player.inventory.mainInventory[i] = null;
    }

    private int stock(ItemStack sample) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack != null && stack.getItem() == sample.getItem() && stack.getItemDamage() == sample.getItemDamage())
                count += stack.stackSize;
        return count;
    }

    private static HologramPresets.Cell cell(HologramPresets.Preset blueprint, int x, int y, int z) {
        for (HologramPresets.Cell cell : blueprint.cells) if (cell.x == x && cell.y == y && cell.z == z) return cell;
        throw new AssertionError("missing blueprint coordinate " + x + "," + y + "," + z);
    }

    private void supply(HologramPresets.Preset blueprint, boolean partial) throws Exception {
        for (int i = 1; i < player.inventory.mainInventory.length; i++) player.inventory.mainInventory[i] = null;
        int slot = 1;
        boolean controllerSupplied = false;
        for (HologramPresets.Cell cell : blueprint.cells) {
            if (cell.excluded) continue;
            if (partial && !"minecraft:chest".equals(cell.block)) {
                if (!cell.controller || controllerSupplied) continue;
                controllerSupplied = true;
            }
            require(slot < player.inventory.mainInventory.length, "fixture_material_inventory_capacity");
            ItemStack stack = cell.stack();
            require(stack != null, "fixture_material_has_registered_item");
            player.inventory.mainInventory[slot++] = stack;
            if (cell.tileNbt != null && !cell.tileNbt.isEmpty()) {
                NBTTagCompound data = HologramBlueprintNbt.decode(cell.tileNbt);
                NBTTagList covers = blueprint.retainCovers ? data.getTagList("gt.covers", 10) : new NBTTagList();
                for (int i = 0; i < covers.tagCount(); i++) {
                    ItemStack cover = GTUtility.intToStack(
                        covers.getCompoundTagAt(i)
                            .getInteger("id"));
                    require(
                        cover != null && slot < player.inventory.mainInventory.length,
                        "fixture_captured_cover_has_real_material_item");
                    cover.stackSize = 1;
                    player.inventory.mainInventory[slot++] = cover;
                }
                if (blueprint.retainNbt
                    && (blueprint.retainedNbtKeys == null || blueprint.retainedNbtKeys.contains("Items"))) {
                    NBTTagList contents = data.getTagList("Items", 10);
                    for (int i = 0; i < contents.tagCount(); i++) {
                        ItemStack material = ItemStack.loadItemStackFromNBT(contents.getCompoundTagAt(i));
                        require(
                            material != null && slot < player.inventory.mainInventory.length,
                            "fixture_captured_inventory_has_real_payment_item");
                        player.inventory.mainInventory[slot++] = material.copy();
                    }
                }
            }
        }
    }

    private NBTTagCompound rowAt(int x, int y, int z) {
        NBTTagList rows = capture.state.getTagList("cells", 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            if (row.getInteger("x") == x && row.getInteger("y") == y && row.getInteger("z") == z) return row;
        }
        throw new AssertionError("missing captured world position " + x + "," + y + "," + z);
    }

    private int indexAt(int x, int y, int z) {
        NBTTagList rows = capture.state.getTagList("cells", 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            if (row.getInteger("x") == x && row.getInteger("y") == y && row.getInteger("z") == z) return i;
        }
        throw new AssertionError("missing captured index " + x + "," + y + "," + z);
    }

    private void snapshotReceipt() throws Exception {
        try (FileOutputStream output = new FileOutputStream("hologram-blueprint-client-state.nbt")) {
            CompressedStreamTools.writeCompressed(capture.state, output);
        }
        System.out.println(PREFIX + "RECEIPT hologram-blueprint-client-state.nbt schema=2");
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(
            name + " job="
                + (capture.state == null ? -1 : capture.state.getInteger("job"))
                + " status="
                + (capture.state == null ? "no snapshot" : capture.state.getString("status")));
        assertions++;
        System.out.println(PREFIX + "PASS " + name);
    }

    private static final class CaptureNetwork extends SimpleNetworkWrapper {

        NBTTagCompound state;
        final HologramSnapshotAssembly assembly = new HologramSnapshotAssembly();

        CaptureNetwork() {
            super("gtit_blueprint_observer");
        }

        @Override
        public void sendTo(IMessage message, EntityPlayerMP recipient) {
            if (USER.equals(recipient.getUniqueID())) {
                NBTTagCompound decoded = HologramSmokeTest.decodeState(message, assembly);
                if (decoded != null) state = decoded;
            }
        }
    }

    public static final class PlaceVeto {

        int hits;
        int events;
        boolean restoredTileObserved;
        boolean restoredEnergyObserved;
        final int x, y, z;

        PlaceVeto(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @SubscribeEvent
        public void place(net.minecraftforge.event.world.BlockEvent.PlaceEvent event) {
            if (event.x == x && event.y == y && event.z == z) {
                events++;
                String tileType = "none", customName = "", owner = "";
                int contents = 0;
                long energy = 0;
                boolean restored = false;
                if (event.world.getTileEntity(x, y, z) instanceof TileEntityChest) {
                    tileType = "chest";
                    NBTTagCompound data = new NBTTagCompound();
                    event.world.getTileEntity(x, y, z)
                        .writeToNBT(data);
                    customName = data.getString("CustomName");
                    ItemStack treasure = ((TileEntityChest) event.world.getTileEntity(x, y, z)).getStackInSlot(4);
                    contents = treasure == null ? 0 : treasure.stackSize;
                    restored = "Blueprint chest configuration receipt".equals(customName) && treasure != null
                        && treasure.getItem() == Items.diamond
                        && contents == 7
                        && "Blueprint NBT receipt".equals(treasure.getDisplayName());
                    restoredTileObserved |= restored;
                }
                if (event.world.getTileEntity(x, y, z) instanceof BaseMetaTileEntity) {
                    tileType = "GT";
                    BaseMetaTileEntity tile = (BaseMetaTileEntity) event.world.getTileEntity(x, y, z);
                    energy = tile.getStoredEU();
                    owner = String.valueOf(tile.getOwnerUuid());
                    restored = energy == 5000 && USER.equals(tile.getOwnerUuid());
                    restoredEnergyObserved |= restored;
                }
                // ItemStack placement posts its native event before the service restores the blueprint.
                // Target the later service event so this fixture exercises rollback after actual NBT restoration.
                if (restored) {
                    hits++;
                    event.setCanceled(true);
                }
                int diamondStock = 0, chestStock = 0;
                for (ItemStack stack : event.player.inventory.mainInventory) if (stack != null) {
                    if (stack.getItem() == Items.diamond) diamondStock += stack.stackSize;
                    if (stack.getItem() == Item.getItemFromBlock(Blocks.chest)) chestStock += stack.stackSize;
                }
                System.out.println(
                    PREFIX + "EVENT receipt="
                        + events
                        + " at="
                        + x
                        + ","
                        + y
                        + ","
                        + z
                        + " tile="
                        + tileType
                        + " customName="
                        + customName
                        + " contents="
                        + contents
                        + " playerDiamonds="
                        + diamondStock
                        + " playerChests="
                        + chestStock
                        + " energy="
                        + energy
                        + " owner="
                        + owner
                        + " cancelledAfterRestore="
                        + restored);
            }
        }
    }
}
