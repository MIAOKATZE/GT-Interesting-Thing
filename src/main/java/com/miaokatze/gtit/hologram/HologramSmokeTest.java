package com.miaokatze.gtit.hologram;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.miaokatze.gtit.common.items.hologram.ItemNekoHologramProjector;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;

/** 显式属性门控的真专用服回归；仅在 hologram-smoke 命名的新世界工作，完成后自停。 */
public final class HologramSmokeTest {

    private static final String PREFIX = "[GTIT-HOLOGRAM-SMOKE] ";
    private int ticks;
    private boolean finished;
    private int assertions;

    public static void registerIfEnabled() {
        if (Boolean.getBoolean("gtit.hologram.smoketest")) {
            FMLCommonHandler.instance()
                .bus()
                .register(new HologramSmokeTest());
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.END || ++ticks < 40) return;
        finished = true;
        MinecraftServer server = MinecraftServer.getServer();
        try {
            run(server.worldServerForDimension(0));
            System.out.println(PREFIX + "FINAL PASS assertions=" + assertions);
        } catch (Throwable failure) {
            System.err.println(PREFIX + "FINAL FAIL assertions=" + assertions + " reason=" + failure);
            failure.printStackTrace();
        } finally {
            HologramService.clear();
            server.initiateShutdown();
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void run(WorldServer world) throws Exception {
        require(
            world.getWorldInfo()
                .getWorldName()
                .contains("hologram-smoke"),
            "isolated_world_guard");
        Item item = (Item) Item.itemRegistry.getObject("gtit:neko_hologram_projector");
        require(item instanceof ItemNekoHologramProjector, "registered_projector_real_registry");
        require(item.getItemStackLimit() == 1, "projector_physical_stack_limit");
        require(
            item instanceof com.gtnewhorizon.structurelib.item.ItemConstructableTrigger,
            "projector_preserves_native_middle_pick_trigger_type");
        var group = NekoTradeDatabase.INSTANCE.getTradeGroup(UUID.fromString("d7f5e8f2-247c-49af-b19b-d2f726ce34d0"));
        require(
            group != null && group.getTrades()
                .stream()
                .anyMatch(
                    t -> t.getToItems()
                        .stream()
                        .anyMatch(
                            output -> output.getBaseStack()
                                .getItem() == item)),
            "registered_trade_resolves_projector");

        int id = -1;
        for (int i = 0; i < GregTechAPI.METATILEENTITIES.length; i++) {
            if (GregTechAPI.METATILEENTITIES[i] instanceof MTEElectricBlastFurnace) {
                id = i;
                break;
            }
        }
        require(id > 0, "real_ebf_registered");
        int x = world.getSpawnPoint().posX + 48, y = 80, z = world.getSpawnPoint().posZ + 48;
        // 轮次必须换新世界；先只读整片预检，禁止清理旧轮控制器触发 GT onRemoval 副作用。
        boolean freshSite = true;
        for (int xx = x - 8; xx <= x + 8; xx++) for (int zz = z - 8; zz <= z + 8; zz++) {
            world.getChunkFromBlockCoords(xx, zz);
            for (int yy = y - 8; yy <= y + 8; yy++) if (world.getTileEntity(xx, yy, zz) != null) freshSite = false;
        }
        require(freshSite, "fresh_world_site_has_no_prior_tile_entities");
        for (int xx = x - 8; xx <= x + 8; xx++) for (int zz = z - 8; zz <= z + 8; zz++) {
            for (int yy = y - 8; yy <= y + 8; yy++) world.setBlockToAir(xx, yy, zz);
        }
        require(world.setBlock(x, y, z, GregTechAPI.sBlockMachines, 0, 3), "place_real_gt_base");
        BaseMetaTileEntity base = (BaseMetaTileEntity) world.getTileEntity(x, y, z);
        base.setInitialValuesAsNBT(null, (short) id);
        base.setFrontFacing(ForgeDirection.NORTH);
        MTEElectricBlastFurnace ebf = (MTEElectricBlastFurnace) base.getMetaTileEntity();
        FakePlayer player = FakePlayerFactory
            .get(world, new GameProfile(UUID.fromString("53ef3b84-b25b-4b81-a89f-88c27596992f"), "GTITHoloSmoke"));
        base.setOwnerName(player.getCommandSenderName());
        base.setOwnerUuid(player.getUniqueID());
        require(
            player.getUniqueID()
                .equals(base.getOwnerUuid()),
            "real_gt_controller_has_nonnull_owner_uuid");
        player.setPosition(x + 3, y, z + 3);
        player.inventory.currentItem = 0;
        player.inventory.mainInventory[0] = new ItemStack(item);

        long initialCaptureStarted = System.nanoTime();
        HologramCapture capture = HologramCapture.collect(ebf, new ItemStack(item), ebf.getExtendedFacing());
        System.out.println(
            PREFIX + "INITIAL_CAPTURE pieces="
                + capture.pieces.size()
                + " cells="
                + capture.cells.size()
                + " incomplete="
                + capture.incomplete
                + " failure="
                + capture.failure
                + " elapsedMs="
                + ((System.nanoTime() - initialCaptureStarted) / 1000000.0)
                + " actualFacing="
                + ebf.getExtendedFacing()
                + " baseFacing="
                + base.getFrontFacing());
        require(
            !capture.incomplete && !capture.pieces.isEmpty() && capture.cells.size() >= 30,
            "actual_buildpiece_mixin_and_structurelib_walker");
        HologramCapture.Cell coil = capture.cells.stream()
            .filter(c -> c.block == GregTechAPI.sBlockCasings5)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no real coil hints: hint mixin did not intercept"));
        require(
            capture.cells.stream()
                .filter(c -> c.block == GregTechAPI.sBlockCasings5)
                .count() == 16,
            "actual_hint_mixin_captures_sixteen_ebf_coils");
        require(!HologramCapture.iconHint(), "capture_threadlocal_cleared");
        require(world.isAirBlock(coil.x, coil.y, coil.z), "preview_does_not_build_world");
        int originalX = base.xCoord;
        try {
            base.xCoord = x + 50000;
            HologramCapture unloaded = HologramCapture.collect(ebf, new ItemStack(item), ebf.getExtendedFacing());
            require(unloaded.incomplete, "unloaded_structure_does_not_claim_full_support");
        } finally {
            base.xCoord = originalX;
        }

        Class<?> sessionClass = Class.forName(HologramService.class.getName() + "$Session");
        Constructor<?> constructor = sessionClass
            .getDeclaredConstructor(net.minecraft.entity.player.EntityPlayerMP.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        Object session = constructor.newInstance(player, x, y, z);
        put(session, "capture", capture);
        directionCases(player, session, base, ebf, capture, item);
        noHatchShellCases(player, session, ebf, capture, item);
        noHatchJobCases(player, session, ebf, item);
        put(session, "main", 123);
        NBTTagCompound channels = new NBTTagCompound();
        channels.setInteger("custom.future_mod", 2048);
        put(session, "channels", channels);
        ItemStack trigger = (ItemStack) call("trigger", session);
        require(
            trigger.stackSize == 123 && com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor
                .getChannelData(trigger, "custom.future_mod") == 2048,
            "local_trigger_main_and_unknown_channel");
        require(
            player.getHeldItem().stackSize == 1 && !player.getHeldItem()
                .hasTagCompound(),
            "local_trigger_does_not_pollute_physical_stack");
        put(session, "main", 1);
        put(session, "channels", new NBTTagCompound());
        require((Boolean) call("valid", session), "session_valid_control");
        int dimension = player.dimension;
        player.dimension = dimension + 1;
        require(!(Boolean) call("valid", session), "session_rejects_dimension_change");
        player.dimension = dimension;
        player.setPosition(x + 100, y, z);
        require(!(Boolean) call("valid", session), "session_rejects_distance_change");
        player.setPosition(x + 3, y, z + 3);
        player.inventory.currentItem = 1;
        require(!(Boolean) call("valid", session), "session_rejects_tool_slot_change");
        player.inventory.currentItem = 0;
        ItemStack heldIdentity = player.inventory.mainInventory[0];
        player.inventory.mainInventory[0] = heldIdentity.copy();
        require(!(Boolean) call("valid", session), "session_rejects_same_item_different_stack_identity");
        player.inventory.mainInventory[0] = heldIdentity;

        inventoryBudget(player);
        player.inventory.mainInventory[1] = new ItemStack(GregTechAPI.sBlockCasings5, 2, coil.meta);
        int index = capture.cells.indexOf(coil);
        require((Boolean) call("operate", session, coil, index), "survival_build_real_structure_element");
        require(
            world.getBlock(coil.x, coil.y, coil.z) == coil.block
                && world.getBlockMetadata(coil.x, coil.y, coil.z) == coil.meta
                && player.inventory.mainInventory[1].stackSize == 1,
            "survival_build_consumes_exactly_one");
        require(
            (Boolean) call("operate", session, coil, index) && player.inventory.mainInventory[1].stackSize == 1,
            "already_legal_coil_is_kept_without_consumption");
        HologramCapture.Cell retained = capture.cells.stream()
            .filter(c -> c != coil && c.block == coil.block)
            .findFirst()
            .orElseThrow(() -> new AssertionError("second coil missing"));
        require(
            (Boolean) call("operate", session, retained, capture.cells.indexOf(retained)),
            "second_completed_cell_control");

        int low = coil.meta;
        HologramCapture upgrade = HologramCapture.collect(ebf, new ItemStack(item, 2), ebf.getExtendedFacing());
        HologramCapture.Cell high = upgrade.cells.stream()
            .filter(c -> c.x == coil.x && c.y == coil.y && c.z == coil.z)
            .findFirst()
            .orElseThrow(() -> new AssertionError("upgrade coordinate missing"));
        require(high.block == coil.block && high.meta != low, "ebf_native_main_channel_changes_coil_tier");
        put(session, "capture", upgrade);
        put(session, "main", 2);
        put(session, "mode", 1);
        player.inventory.mainInventory[1] = null;
        require(
            !(Boolean) call("operate", session, high, upgrade.cells.indexOf(high))
                && world.getBlockMetadata(high.x, high.y, high.z) == low,
            "missing_upgrade_material_keeps_old_coil");
        player.inventory.mainInventory[1] = new ItemStack(high.block, 1, high.meta);
        PlaceVeto veto = new PlaceVeto(high.x, high.y, high.z);
        MinecraftForge.EVENT_BUS.register(veto);
        try {
            Object result = call("operate", session, high, upgrade.cells.indexOf(high));
            System.out.println(
                PREFIX + "EVENT_VETO result="
                    + result
                    + " cell="
                    + high.status
                    + " hits="
                    + veto.hits
                    + " status="
                    + get(session, "status"));
            require(
                veto.hits > 0 && !"placed".equals(high.status) && !"replaced".equals(high.status),
                "event_veto_is_observed_and_never_claims_placement");
            require(
                world.getBlockMetadata(high.x, high.y, high.z) == low && count(player, high.block, high.meta) == 1
                    && count(player, high.block, low) == 0,
                "event_veto_restores_world_and_inventory_exactly");
            require((Boolean) call("valid", session), "event_veto_preserves_session_tool_identity_for_resume");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
        }
        require((Boolean) call("operate", session, high, upgrade.cells.indexOf(high)), "explicit_real_coil_upgrade");
        require(world.getBlockMetadata(high.x, high.y, high.z) == high.meta, "upgrade_installs_requested_coil");
        require(count(player, high.block, low) == 1, "upgrade_returns_old_coil_exactly_once");

        put(session, "mode", 2);
        require(
            (Boolean) call("operate", session, high, upgrade.cells.indexOf(high))
                && world.isAirBlock(high.x, high.y, high.z),
            "explicit_real_coil_dismantle");
        require(count(player, high.block, high.meta) == 1, "dismantle_returns_new_coil_exactly_once");
        creativeStalePinCase(player, session, high, upgrade.cells.indexOf(high), low);
        world.setBlock(high.x, high.y, high.z, Blocks.stone);
        require(
            (Boolean) call("operate", session, high, upgrade.cells.indexOf(high))
                && world.getBlock(high.x, high.y, high.z) == Blocks.stone,
            "foreign_obstacle_is_protected");
        HologramCapture.Cell controller = new HologramCapture.Cell(high.element, x, y, z);
        controller.block = high.block;
        controller.meta = high.meta;
        require(
            (Boolean) call("operate", session, controller, 0) && world.getTileEntity(x, y, z) == base,
            "controller_tile_is_protected");

        // 相同元素位置设置未知，不能因提示列表/现有材料推定可施工。
        world.setBlockToAir(high.x, high.y, high.z);
        put(session, "mode", 0);
        player.capabilities.isCreativeMode = true;
        int materialBeforeCreative = count(player, high.block, high.meta);
        require(
            (Boolean) call("operate", session, high, upgrade.cells.indexOf(high))
                && world.getBlock(high.x, high.y, high.z) == high.block,
            "creative_build_without_material_source");
        require(count(player, high.block, high.meta) == materialBeforeCreative, "creative_build_preserves_inventory");
        world.setBlockToAir(high.x, high.y, high.z);
        player.capabilities.isCreativeMode = false;
        noHatchAndPinCases(player, session, high, upgrade.cells.indexOf(high));
        player.inventory.mainInventory[2] = new ItemStack(Items.iron_ingot, 3);
        HologramCapture.Cell rejected = new HologramCapture.Cell(new FailingElement(), high.x, high.y, high.z);
        rejected.block = Blocks.stone;
        require(!(Boolean) call("operate", session, rejected, 0), "failed_native_element_pauses_operation");
        require(
            world.isAirBlock(high.x, high.y, high.z) && player.inventory.mainInventory[2].stackSize == 3,
            "failed_native_element_restores_consumed_inventory_and_world");
        high.unknown = true;
        require(
            (Boolean) call("operate", session, high, upgrade.cells.indexOf(high))
                && world.isAirBlock(high.x, high.y, high.z)
                && "unsupported".equals(high.status),
            "unknown_element_stays_unsupported");
        upgrade.cells.clear();
        upgrade.cells.add(high);
        securityActions(player, session, retained);
        exportClientFixture(player, session, ebf, item);
    }

    @SuppressWarnings("unchecked")
    private void noHatchJobCases(FakePlayer player, Object session, MTEElectricBlastFurnace ebf, Item item)
        throws Exception {
        HologramCapture actual = HologramCapture.collect(ebf, new ItemStack(item), ebf.getExtendedFacing());
        HologramCapture.Cell coil = actual.cells.stream()
            .filter(c -> c.block == GregTechAPI.sBlockCasings5)
            .findFirst()
            .orElseThrow(() -> new AssertionError("real job coil missing"));
        int top = actual.cells.stream()
            .mapToInt(c -> c.y)
            .max()
            .orElseThrow(() -> new AssertionError("empty capture"));
        HologramCapture.Cell muffler = actual.cells.stream()
            .filter(c -> c.y == top && c.block != GregTechAPI.sBlockCasings1)
            .findFirst()
            .orElseThrow(() -> new AssertionError("real job fixed muffler missing"));
        ItemStack[] inventory = player.inventory.mainInventory.clone();
        Object previousCapture = get(session, "capture");
        NetHandlerPlayServer previousHandler = player.playerNetServerHandler;
        Field network = HologramNetwork.class.getDeclaredField("channel");
        network.setAccessible(true);
        Object previousNetwork = network.get(null);
        Field registry = HologramService.class.getDeclaredField("SESSIONS");
        registry.setAccessible(true);
        Map<UUID, Object> sessions = (Map<UUID, Object>) registry.get(null);
        EmbeddedChannel embedded = new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
        try {
            NetworkManager manager = new NetworkManager(false);
            for (Field field : NetworkManager.class.getDeclaredFields()) if (field.getType() == Channel.class) {
                field.setAccessible(true);
                field.set(manager, embedded);
            }
            player.playerNetServerHandler = new NetHandlerPlayServer(MinecraftServer.getServer(), manager, player);
            network.set(null, null);
            sessions.put(player.getUniqueID(), session);
            player.inventory.mainInventory[4] = new ItemStack(GregTechAPI.sBlockCasings1, 16, 11);
            player.inventory.mainInventory[5] = new ItemStack(coil.block, 16, coil.meta);
            put(session, "capture", actual);
            put(session, "main", 1);
            put(session, "noHatches", true);
            put(session, "mode", 0);
            put(session, "scope", 0);
            put(
                session,
                "facing",
                ebf.getExtendedFacing()
                    .ordinal());
            put(session, "cursor", 0);
            put(session, "completed", 0);
            put(session, "job", 1);
            for (int tick = 0; tick < 100 && (Integer) get(session, "job") == 1; tick++) HologramService.tick();
            long shells = actual.cells.stream()
                .filter(
                    c -> player.worldObj.getBlock(c.x, c.y, c.z) == GregTechAPI.sBlockCasings1
                        && player.worldObj.getBlockMetadata(c.x, c.y, c.z) == 11)
                .count();
            long coils = actual.cells.stream()
                .filter(
                    c -> player.worldObj.getBlock(c.x, c.y, c.z) == coil.block
                        && player.worldObj.getBlockMetadata(c.x, c.y, c.z) == coil.meta)
                .count();
            require(
                !actual.incomplete && !actual.pieces.isEmpty()
                    && shells == 16
                    && coils == 16
                    && actual.cells.stream()
                        .filter(c -> c.block == GregTechAPI.sBlockCasings1 || c.block == coil.block)
                        .allMatch(c -> player.worldObj.getTileEntity(c.x, c.y, c.z) == null),
                "bounded_no_hatch_job_builds_all_sixteen_shells_and_coils_without_tiles");
            require(
                (Integer) get(session, "job") == 2 && (Integer) get(session, "cursor") == actual.cells.size()
                    && player.worldObj.isAirBlock(muffler.x, muffler.y, muffler.z)
                    && count(player, GregTechAPI.sBlockCasings1, 11) == 0
                    && count(player, coil.block, coil.meta) == 0,
                "no_hatch_job_visits_entire_structure_keeps_fixed_gap_and_conserves_material_without_fake_completion");
        } finally {
            for (HologramCapture.Cell cell : actual.cells) if (cell.x != ebf.getBaseMetaTileEntity()
                .getXCoord() || cell.y
                    != ebf.getBaseMetaTileEntity()
                        .getYCoord()
                || cell.z != ebf.getBaseMetaTileEntity()
                    .getZCoord())
                player.worldObj.setBlockToAir(cell.x, cell.y, cell.z);
            System.arraycopy(inventory, 0, player.inventory.mainInventory, 0, inventory.length);
            put(session, "capture", previousCapture);
            put(session, "noHatches", false);
            put(session, "job", 0);
            put(session, "cursor", 0);
            put(session, "completed", 0);
            sessions.clear();
            network.set(null, previousNetwork);
            player.playerNetServerHandler = previousHandler;
            embedded.finish();
            Object pending;
            while ((pending = embedded.readInbound()) != null) io.netty.util.ReferenceCountUtil.release(pending);
            while ((pending = embedded.readOutbound()) != null) io.netty.util.ReferenceCountUtil.release(pending);
        }
    }

    private void noHatchShellCases(FakePlayer player, Object session, MTEElectricBlastFurnace ebf,
        HologramCapture capture, Item item) throws Exception {
        java.util.List<HologramCapture.Cell> shells = capture.cells.stream()
            .filter(c -> c.block == GregTechAPI.sBlockCasings1 && c.meta == 11)
            .collect(java.util.stream.Collectors.toList());
        require(shells.size() == 16, "real_ebf_fixed_factory_fallback_resolves_all_sixteen_shell_cells");
        int topY = capture.cells.stream()
            .mapToInt(c -> c.y)
            .max()
            .orElseThrow(() -> new AssertionError("empty EBF"));
        HologramCapture.Cell muffler = capture.cells.stream()
            .filter(c -> c.y == topY && !(c.block == GregTechAPI.sBlockCasings1 && c.meta == 11))
            .findFirst()
            .orElseThrow(() -> new AssertionError("real EBF muffler cell missing"));
        ItemStack[] before = player.inventory.mainInventory.clone();
        int previousMode = (Integer) get(session, "mode");
        boolean previousNoHatches = (Boolean) get(session, "noHatches");
        try {
            put(session, "mode", 0);
            put(session, "noHatches", true);
            player.inventory.mainInventory[4] = new ItemStack(GregTechAPI.sBlockCasings1, shells.size(), 11);
            int placed = 0;
            for (HologramCapture.Cell shell : shells) {
                require(
                    player.worldObj.isAirBlock(shell.x, shell.y, shell.z),
                    "shell_initial_position_empty_" + placed);
                require(
                    (Boolean) call("operate", session, shell, capture.cells.indexOf(shell))
                        && player.worldObj.getBlock(shell.x, shell.y, shell.z) == GregTechAPI.sBlockCasings1
                        && player.worldObj.getBlockMetadata(shell.x, shell.y, shell.z) == 11
                        && player.worldObj.getTileEntity(shell.x, shell.y, shell.z) == null,
                    "native_no_hatch_shell_places_exact_block_without_tile_" + placed);
                placed++;
                require(
                    count(player, GregTechAPI.sBlockCasings1, 11) == shells.size() - placed,
                    "native_shell_consumes_exactly_one_" + placed);
            }
            require(
                (Boolean) call("operate", session, muffler, capture.cells.indexOf(muffler))
                    && player.worldObj.isAirBlock(muffler.x, muffler.y, muffler.z)
                    && player.worldObj.getTileEntity(muffler.x, muffler.y, muffler.z) == null
                    && (muffler.status.equals("unsupported") || muffler.status.equals("protected")),
                "fixed_muffler_without_shell_factory_stays_empty_and_unsupported");
            HologramCapture rescanned = HologramCapture.collect(ebf, new ItemStack(item), ebf.getExtendedFacing());
            require(!rescanned.incomplete, "constructed_shells_rescan_real_walker_complete");
            java.util.List<HologramCapture.Cell> rescannedShells = rescanned.cells.stream()
                .filter(c -> c.block == GregTechAPI.sBlockCasings1 && c.meta == 11)
                .collect(java.util.stream.Collectors.toList());
            require(
                rescannedShells.size() == shells.size(),
                "rescan_keeps_all_fixed_shell_targets_instead_of_numeric_hints");
            put(session, "capture", rescanned);
            for (HologramCapture.Cell shell : rescannedShells) {
                require(
                    (Boolean) call("operate", session, shell, rescanned.cells.indexOf(shell))
                        && shell.status.equals("satisfied")
                        && player.worldObj.getBlock(shell.x, shell.y, shell.z) == shell.block,
                    "rescanned_legal_shell_kept_" + rescanned.cells.indexOf(shell));
            }
            require(
                count(player, GregTechAPI.sBlockCasings1, 11) == 0,
                "rescan_and_keep_do_not_consume_extra_shell_material");
            put(session, "mode", 2);
            int removed = 0;
            for (HologramCapture.Cell shell : rescannedShells) {
                require(
                    (Boolean) call("operate", session, shell, rescanned.cells.indexOf(shell))
                        && player.worldObj.isAirBlock(shell.x, shell.y, shell.z),
                    "explicit_factory_shell_dismantle_" + removed);
                removed++;
                require(
                    count(player, GregTechAPI.sBlockCasings1, 11) == removed,
                    "shell_dismantle_refunds_exactly_one_" + removed);
            }
        } finally {
            for (HologramCapture.Cell shell : shells) player.worldObj.setBlockToAir(shell.x, shell.y, shell.z);
            System.arraycopy(before, 0, player.inventory.mainInventory, 0, before.length);
            put(session, "capture", capture);
            put(session, "mode", previousMode);
            put(session, "noHatches", previousNoHatches);
        }
    }

    @SuppressWarnings("unchecked")
    private void creativeStalePinCase(FakePlayer player, Object session, HologramCapture.Cell high, int index, int low)
        throws Exception {
        Map<Integer, ItemStack> pins = (Map<Integer, ItemStack>) get(session, "pins");
        int previousMode = (Integer) get(session, "mode");
        boolean previousCreative = player.capabilities.isCreativeMode;
        ItemStack previousPin = pins.get(index);
        int lowBefore = count(player, high.block, low), highBefore = count(player, high.block, high.meta);
        try {
            player.worldObj.setBlock(high.x, high.y, high.z, high.block, low, 3);
            pins.put(index, new ItemStack(Items.diamond));
            put(session, "mode", 1);
            player.capabilities.isCreativeMode = true;
            require(
                (Boolean) call("operate", session, high, index)
                    && player.worldObj.getBlock(high.x, high.y, high.z) == high.block
                    && player.worldObj.getBlockMetadata(high.x, high.y, high.z) == high.meta,
                "creative_nonempty_coil_upgrade_ignores_stale_diamond_pin");
            require(
                count(player, high.block, low) == lowBefore && count(player, high.block, high.meta) == highBefore,
                "creative_upgrade_returns_no_old_drop_and_preserves_material_inventory");
            put(session, "mode", 0);
            require(
                (Boolean) call("operate", session, high, index)
                    && player.worldObj.getBlockMetadata(high.x, high.y, high.z) == high.meta,
                "stale_pin_cannot_change_already_legal_nonempty_coil");
        } finally {
            if (previousPin == null) pins.remove(index);
            else pins.put(index, previousPin);
            player.capabilities.isCreativeMode = previousCreative;
            put(session, "mode", previousMode);
            player.worldObj.setBlockToAir(high.x, high.y, high.z);
        }
    }

    @SuppressWarnings("unchecked")
    private void directionCases(FakePlayer player, Object session, BaseMetaTileEntity base, MTEElectricBlastFurnace ebf,
        HologramCapture originalCapture, Item item) throws Exception {
        var originalFacing = ebf.getExtendedFacing();
        var desiredFacing = com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
            originalFacing.getDirection()
                .getOpposite());
        require(
            ebf.getAlignmentLimits()
                .isNewExtendedFacingValid(desiredFacing),
            "rotation_requested_facing_is_real_ebf_legal");
        HologramCapture rotated = HologramCapture.collect(ebf, new ItemStack(item), desiredFacing);
        require(!rotated.incomplete, "rotation_new_footprint_real_walker_complete");
        HologramCapture.Cell oldOnly = originalCapture.cells.stream()
            .filter(
                old -> old.block == GregTechAPI.sBlockCasings5 && rotated.cells.stream()
                    .noneMatch(next -> old.x == next.x && old.y == next.y && old.z == next.z))
            .findFirst()
            .orElseThrow(() -> new AssertionError("opposite EBF has no exclusive old coil position"));
        Field network = HologramNetwork.class.getDeclaredField("channel");
        network.setAccessible(true);
        Object previousNetwork = network.get(null);
        network.set(null, null);
        Field registry = HologramService.class.getDeclaredField("SESSIONS");
        registry.setAccessible(true);
        Map<UUID, Object> sessions = (Map<UUID, Object>) registry.get(null);
        sessions.put(player.getUniqueID(), session);
        try {
            player.worldObj.setBlock(oldOnly.x, oldOnly.y, oldOnly.z, oldOnly.block, oldOnly.meta, 3);
            require(
                rotated.cells.stream()
                    .allMatch(
                        c -> (c.x == base.xCoord && c.y == base.yCoord && c.z == base.zCoord)
                            || player.worldObj.isAirBlock(c.x, c.y, c.z)),
                "rotation_new_footprint_empty_old_footprint_occupied_control");
            put(session, "capture", rotated);
            put(session, "facing", desiredFacing.ordinal());
            put(session, "job", 0);
            require(!(Boolean) call("emptyStructure", session), "rotation_guard_checks_occupied_old_footprint");
            NBTTagCompound start = new NBTTagCompound();
            start.setString("session", (String) get(session, "id"));
            start.setString("op", "start");
            HologramService.handle(player, start);
            require(
                (Integer) get(session, "job") != 1 && ebf.getExtendedFacing() == originalFacing
                    && player.worldObj.getBlock(oldOnly.x, oldOnly.y, oldOnly.z) == oldOnly.block,
                "rotation_start_rejects_old_structure_without_mutating_it");
            player.worldObj.setBlockToAir(oldOnly.x, oldOnly.y, oldOnly.z);
            require((Boolean) call("emptyStructure", session), "rotation_guard_accepts_both_empty_footprints");
            HologramService.handle(player, start);
            require(
                (Integer) get(session, "job") == 1 && base.getFrontFacing() == desiredFacing.getDirection()
                    && ebf.getExtendedFacing() == desiredFacing,
                "empty_ground_start_synchronizes_base_and_extended_facing");
            NBTTagCompound persisted = new NBTTagCompound();
            base.writeToNBT(persisted);
            BaseMetaTileEntity reloaded = new BaseMetaTileEntity();
            reloaded.setWorldObj(player.worldObj);
            reloaded.readFromNBT(persisted);
            require(
                reloaded.getMetaTileEntity() instanceof MTEElectricBlastFurnace
                    && reloaded.getFrontFacing() == desiredFacing.getDirection()
                    && ((MTEElectricBlastFurnace) reloaded.getMetaTileEntity()).getExtendedFacing() == desiredFacing,
                "rotated_gt_controller_real_nbt_reload_preserves_orientation");
        } finally {
            player.worldObj.setBlockToAir(oldOnly.x, oldOnly.y, oldOnly.z);
            base.setFrontFacing(originalFacing.getDirection());
            ebf.setExtendedFacing(originalFacing);
            put(session, "capture", originalCapture);
            put(session, "facing", originalFacing.ordinal());
            put(session, "job", 0);
            sessions.clear();
            network.set(null, previousNetwork);
        }
    }

    /** 独立测试工件，坐标、registry名称、候选和材料库存均取当前真 EBF/世界，不注入演示格。 */
    @SuppressWarnings("unchecked")
    private void exportClientFixture(FakePlayer player, Object session, MTEElectricBlastFurnace ebf, Item item)
        throws Exception {
        HologramCapture actual = HologramCapture.collect(ebf, new ItemStack(item), ebf.getExtendedFacing());
        require(!actual.incomplete, "client_fixture_uses_complete_real_ebf_capture");
        put(session, "capture", actual);
        put(session, "main", 1);
        put(session, "mode", 0);
        put(session, "job", 0);
        NBTTagCompound state = new NBTTagCompound();
        int x = (Integer) get(session, "x"), y = (Integer) get(session, "y"), z = (Integer) get(session, "z");
        state.setString("session", (String) get(session, "id"));
        state.setBoolean("target", true);
        state.setInteger("x", x);
        state.setInteger("y", y);
        state.setInteger("z", z);
        state.setInteger("dimension", player.dimension);
        state.setString("title", ebf.getLocalName());
        state.setInteger("main", 1);
        state.setTag("channels", ((NBTTagCompound) get(session, "channels")).copy());
        state.setInteger(
            "facing",
            ebf.getExtendedFacing()
                .ordinal());
        state.setInteger("selected", -1);
        state.setInteger("job", 0);
        state.setBoolean("supported", true);
        state.setString("status", "真实专用服电力高炉采集；客户端只读验证");
        state.setString("description", "候选、方块与材料库存均来自真实 EBF StructureLib 采集及服务器世界。");
        state.setIntArray(
            "facings",
            Arrays.stream(com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.values())
                .filter(
                    f -> ebf.getAlignmentLimits()
                        .isNewExtendedFacingValid(f))
                .mapToInt(Enum::ordinal)
                .toArray());
        NBTTagList rows = new NBTTagList(), materials = new NBTTagList();
        Map<String, NBTTagCompound> budget = new java.util.LinkedHashMap<>();
        int protectedCount = 0, missing = 0;
        for (HologramCapture.Cell cell : actual.cells) {
            NBTTagCompound row = new NBTTagCompound();
            row.setInteger("x", cell.x);
            row.setInteger("y", cell.y);
            row.setInteger("z", cell.z);
            row.setInteger("dx", cell.x - x);
            row.setInteger("dy", cell.y - y);
            row.setInteger("dz", cell.z - z);
            net.minecraft.block.Block present = player.worldObj.getBlock(cell.x, cell.y, cell.z);
            int meta = player.worldObj.getBlockMetadata(cell.x, cell.y, cell.z);
            row.setString("id", String.valueOf(net.minecraft.block.Block.blockRegistry.getNameForObject(present)));
            row.setInteger("meta", meta);
            row.setString(
                "wantId",
                cell.block == null ? ""
                    : String.valueOf(net.minecraft.block.Block.blockRegistry.getNameForObject(cell.block)));
            row.setInteger("wantMeta", cell.meta);
            String status = cell.unknown || cell.block == null ? "unsupported"
                : player.worldObj.getTileEntity(cell.x, cell.y, cell.z) != null ? "protected"
                    : present == cell.block && meta == cell.meta ? "satisfied"
                        : player.worldObj.isAirBlock(cell.x, cell.y, cell.z) ? "pending" : "protected";
            row.setString("status", status);
            row.setInteger("chosenChoice", -1);
            row.setString("pinScope", "newbuild");
            if (status.equals("protected") || status.equals("unsupported")) protectedCount++;
            NBTTagList choices = new NBTTagList();
            java.util.List<ItemStack> options = (java.util.List<ItemStack>) call("candidates", session, cell);
            for (ItemStack candidate : options) {
                NBTTagCompound tag = new NBTTagCompound();
                candidate.writeToNBT(tag);
                choices.appendTag(tag);
            }
            row.setTag("candidates", choices);
            rows.appendTag(row);
            if (status.equals("pending") && !options.isEmpty()) {
                ItemStack desired = options.get(0)
                    .copy();
                desired.stackSize = 1;
                String key = Item.itemRegistry.getNameForObject(desired.getItem()) + ":"
                    + desired.getItemDamage()
                    + ":"
                    + desired.getTagCompound();
                NBTTagCompound material = budget.get(key);
                if (material == null) {
                    material = new NBTTagCompound();
                    desired.writeToNBT(material);
                    int available = 0;
                    for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                        ItemStack held = player.inventory.mainInventory[i];
                        if (i != player.inventory.currentItem && held != null
                            && held.getItem() == desired.getItem()
                            && held.getItemDamage() == desired.getItemDamage()
                            && ItemStack.areItemStackTagsEqual(held, desired)) available += held.stackSize;
                    }
                    material.setInteger("available", available);
                    budget.put(key, material);
                }
                material.setInteger("required", material.getInteger("required") + 1);
            }
        }
        for (NBTTagCompound material : budget.values()) {
            materials.appendTag(material);
            missing += Math.max(0, material.getInteger("required") - material.getInteger("available"));
        }
        state.setTag("cells", rows);
        state.setTag("materials", materials);
        state.setInteger("total", rows.tagCount());
        state.setInteger("protected", protectedCount);
        state.setInteger("missing", missing);
        require(
            rows.tagCount() == actual.cells.size() && rows.tagCount() >= 30,
            "client_fixture_cells_equal_actual_capture");
        java.io.File target = new java.io.File("hologram-client-state.nbt");
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(target)) {
            CompressedStreamTools.writeCompressed(state, stream);
        }
        System.out.println(
            PREFIX + "CLIENT_FIXTURE path="
                + target.getCanonicalPath()
                + " cells="
                + rows.tagCount()
                + " materials="
                + materials.tagCount());
    }

    @SuppressWarnings("unchecked")
    private void noHatchAndPinCases(FakePlayer player, Object session, HologramCapture.Cell high, int index)
        throws Exception {
        Map<Integer, ItemStack> pins = (Map<Integer, ItemStack>) get(session, "pins");
        pins.put(index, new ItemStack(Items.diamond));
        int before = count(player, high.block, high.meta);
        require(
            !(Boolean) call("operate", session, high, index) && player.worldObj.isAirBlock(high.x, high.y, high.z)
                && count(player, high.block, high.meta) == before,
            "pin_material_mismatch_keeps_world_and_inventory");
        pins.clear();
        HologramCapture.Cell machineOnly = new HologramCapture.Cell(new MachineOnlyElement(), high.x, high.y, high.z);
        machineOnly.block = GregTechAPI.sBlockMachines;
        ItemStack machines = new ItemStack(GregTechAPI.sBlockMachines, 2, 0);
        player.inventory.mainInventory[3] = machines;
        put(session, "noHatches", true);
        try {
            require(!(Boolean) call("operate", session, machineOnly, index), "no_hatches_refuses_machine_only_source");
            require(
                player.worldObj.isAirBlock(high.x, high.y, high.z)
                    && player.worldObj.getTileEntity(high.x, high.y, high.z) == null
                    && player.inventory.mainInventory[3].stackSize == 2,
                "no_hatches_leaves_gap_without_consuming_machine");
        } finally {
            put(session, "noHatches", false);
            player.inventory.mainInventory[3] = null;
        }
        require((Boolean) call("valid", session), "failed_pin_and_no_hatch_preserve_resumable_identity");
    }

    private void inventoryBudget(FakePlayer player) throws Exception {
        ItemStack[] before = player.inventory.mainInventory.clone();
        try {
            for (int i = 1; i < player.inventory.mainInventory.length; i++)
                player.inventory.mainInventory[i] = new ItemStack(Items.stick, 64);
            player.inventory.mainInventory[1] = new ItemStack(Items.iron_ingot, 63);
            require(
                (Boolean) call("canFit", player, Collections.singletonList(new ItemStack(Items.iron_ingot))),
                "return_budget_existing_stack_room");
            require(
                !(Boolean) call("canFit", player, Collections.singletonList(new ItemStack(Items.iron_ingot, 2))),
                "return_budget_rejects_overflow");
            require(player.inventory.mainInventory[1].stackSize == 63, "return_budget_is_read_only");
        } finally {
            System.arraycopy(before, 0, player.inventory.mainInventory, 0, before.length);
        }
    }

    @SuppressWarnings("unchecked")
    private void securityActions(FakePlayer player, Object session, HologramCapture.Cell retained) throws Exception {
        Field network = HologramNetwork.class.getDeclaredField("channel");
        network.setAccessible(true);
        Object channel = network.get(null);
        network.set(null, null);
        Field sessionsField = HologramService.class.getDeclaredField("SESSIONS");
        sessionsField.setAccessible(true);
        Map<UUID, Object> sessions = (Map<UUID, Object>) sessionsField.get(null);
        sessions.put(player.getUniqueID(), session);
        try {
            NBTTagCompound action = new NBTTagCompound();
            action.setString("op", "cancel");
            action.setString("session", "forged");
            put(session, "job", 2);
            HologramService.handle(player, action);
            require((Integer) get(session, "job") == 2, "forged_session_cannot_cancel");
            action.setString("session", (String) get(session, "id"));
            FakePlayer stranger = FakePlayerFactory.get(
                (WorldServer) player.worldObj,
                new GameProfile(UUID.fromString("7d36e922-3b19-4f92-a5e8-bd5c71614202"), "GTITHoloOther"));
            HologramService.handle(stranger, action);
            require((Integer) get(session, "job") == 2, "other_owner_cannot_cancel_known_session_id");
            HologramService.handle(player, action);
            require((Integer) get(session, "job") == 4, "owner_can_cancel");
            require((Integer) get(session, "main") == 2, "cancel_keeps_local_configuration");
            NBTTagCompound invalid = new NBTTagCompound();
            invalid.setString("session", (String) get(session, "id"));
            invalid.setString("op", "configure");
            invalid.setInteger("main", -1);
            put(session, "job", 0);
            HologramService.handle(player, invalid);
            require((Integer) get(session, "main") == 2, "illegal_config_is_rejected_atomically");
            invalid.setInteger("main", 9);
            NBTTagCompound badChannels = new NBTTagCompound();
            badChannels.setInteger("future", 0);
            invalid.setTag("channels", badChannels);
            HologramService.handle(player, invalid);
            require((Integer) get(session, "main") == 2, "illegal_subchannel_cannot_change_main");

            // 真网络管理器配内存channel只用于保持会话在线，让生产tick执行完工判据。
            NetHandlerPlayServer oldHandler = player.playerNetServerHandler;
            EmbeddedChannel embedded = new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter());
            try {
                NetworkManager manager = new NetworkManager(false);
                for (Field field : NetworkManager.class.getDeclaredFields()) if (field.getType() == Channel.class) {
                    field.setAccessible(true);
                    field.set(manager, embedded);
                }
                player.playerNetServerHandler = new NetHandlerPlayServer(MinecraftServer.getServer(), manager, player);
                put(session, "job", 1);
                put(session, "cursor", 0);
                put(session, "scope", 0);
                HologramService.tick();
                require((Integer) get(session, "job") == 2, "unknown_protected_cell_cannot_fake_completion");
                put(session, "job", 1);
                action.setString("op", "pause");
                HologramService.handle(player, action);
                int cursor = (Integer) get(session, "cursor");
                HologramService.tick();
                require(
                    (Integer) get(session, "job") == 2 && (Integer) get(session, "cursor") == cursor,
                    "paused_job_does_not_advance");
                action.setString("op", "cancel");
                HologramService.handle(player, action);
                require(
                    (Integer) get(session, "job") == 4 && (Integer) get(session, "cursor") == cursor,
                    "cancel_does_not_rewind_progress");
                require(
                    player.worldObj.getBlock(retained.x, retained.y, retained.z) == retained.block
                        && player.worldObj.getBlockMetadata(retained.x, retained.y, retained.z) == retained.meta,
                    "pause_and_cancel_never_rollback_completed_world_cells");
            } finally {
                player.playerNetServerHandler = oldHandler;
                embedded.finish();
                Object pending;
                while ((pending = embedded.readInbound()) != null) io.netty.util.ReferenceCountUtil.release(pending);
                while ((pending = embedded.readOutbound()) != null) io.netty.util.ReferenceCountUtil.release(pending);
            }
        } finally {
            sessions.clear();
            network.set(null, channel);
        }
    }

    public static final class PlaceVeto {

        private final int x, y, z;
        private int hits;

        PlaceVeto(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @SubscribeEvent
        public void onPlace(BlockEvent.PlaceEvent event) {
            if (event.x == x && event.y == y && event.z == z) {
                hits++;
                event.setCanceled(true);
            }
        }
    }

    private static final class FailingElement implements IStructureElement<Object> {

        public boolean check(Object context, World world, int x, int y, int z) {
            throw new AssertionError("capture/transaction must never call stateful element.check");
        }

        public boolean spawnHint(Object context, World world, int x, int y, int z, ItemStack trigger) {
            return false;
        }

        public boolean placeBlock(Object context, World world, int x, int y, int z, ItemStack trigger) {
            return false;
        }

        public PlaceResult survivalPlaceBlock(Object context, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            env.getSource()
                .takeOne(stack -> stack.getItem() == Items.iron_ingot, false);
            world.setBlock(x, y, z, Blocks.stone);
            return PlaceResult.REJECT;
        }
    }

    private static final class MachineOnlyElement implements IStructureElement<Object> {

        public boolean check(Object context, World world, int x, int y, int z) {
            throw new AssertionError("stateful check");
        }

        public boolean spawnHint(Object context, World world, int x, int y, int z, ItemStack trigger) {
            return false;
        }

        public boolean placeBlock(Object context, World world, int x, int y, int z, ItemStack trigger) {
            return false;
        }

        public PlaceResult survivalPlaceBlock(Object context, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            ItemStack machine = env.getSource()
                .takeOne(stack -> stack.getItem() == Item.getItemFromBlock(GregTechAPI.sBlockMachines), false);
            if (machine == null) return PlaceResult.REJECT;
            world.setBlock(x, y, z, GregTechAPI.sBlockMachines);
            return PlaceResult.ACCEPT;
        }
    }

    private static int count(FakePlayer player, net.minecraft.block.Block block, int meta) {
        return Arrays.stream(player.inventory.mainInventory)
            .filter(s -> s != null && s.getItem() == Item.getItemFromBlock(block) && s.getItemDamage() == meta)
            .mapToInt(s -> s.stackSize)
            .sum();
    }

    private static Object call(String name, Object... args) throws Exception {
        for (Method method : HologramService.class.getDeclaredMethods()) if (method.getName()
            .equals(name) && method.getParameterCount() == args.length
            && accepts(method.getParameterTypes(), args)) {
                method.setAccessible(true);
                return method.invoke(null, args);
            }
        throw new NoSuchMethodException(name);
    }

    private static boolean accepts(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (type == int.class) type = Integer.class;
            else if (type == boolean.class) type = Boolean.class;
            else if (type == long.class) type = Long.class;
            if (args[i] != null && !type.isInstance(args[i])) return false;
            if (args[i] == null && parameterTypes[i].isPrimitive()) return false;
        }
        return true;
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void put(Object object, String name, Object value) throws Exception {
        Field field = object.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
        System.out.println(PREFIX + "ASSERT PASS " + label);
    }
}
