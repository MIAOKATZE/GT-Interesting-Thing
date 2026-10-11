package com.miaokatze.gtit.hologram;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.miaokatze.gtit.common.items.hologram.ItemNekoHologramProjector;
import com.miaokatze.gtit.testutil.SimpleAssert;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.ItemMachines;

/** Invokes the actual placement transform, movement, serializer, loader and controller placement setter. */
final class HologramVirtualRotationRegression {

    private static final int CONTROLLER = 32760, HATCH = 32761;

    private HologramVirtualRotationRegression() {}

    static void run() {
        IMetaTileEntity oldController = GregTechAPI.METATILEENTITIES[CONTROLLER];
        IMetaTileEntity oldHatch = GregTechAPI.METATILEENTITIES[HATCH];
        Object oldCache = null;
        Path directory = null;
        Field cache = null;
        String threadName = Thread.currentThread()
            .getName();
        Thread.currentThread()
            .setName("Server thread");
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
            AlignmentController controller = (AlignmentController) unsafe.allocateInstance(AlignmentController.class);
            alignmentLimits(controller, (d, r, f) -> f == Flip.NONE);
            field(MTEEnhancedMultiBlockBase.class, "mExtendedFacing").set(controller, ExtendedFacing.EAST_NORMAL_NONE);
            GregTechAPI.METATILEENTITIES[CONTROLLER] = controller;
            MTEHatchInput hatch = (MTEHatchInput) unsafe.allocateInstance(MTEHatchInput.class);
            GregTechAPI.METATILEENTITIES[HATCH] = hatch;
            Block machineBlock = new Block(net.minecraft.block.material.Material.iron) {};
            Block.blockRegistry.addObject(4093, "gtit:rotation_fixture_machines", machineBlock);
            ItemMachines machines = new ItemMachines(machineBlock);
            Item.itemRegistry.addObject(4093, "gtit:rotation_fixture_machines", machines);
            ItemNekoHologramProjector projector = (ItemNekoHologramProjector) unsafe
                .allocateInstance(ItemNekoHologramProjector.class);
            field(Item.class, "delegate").set(
                projector,
                ((cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry) Item.itemRegistry)
                    .getDelegate(projector, Item.class));
            Item.itemRegistry.addObject(31999, "gtit:rotation_fixture_projector", projector);
            HologramServiceEditRegression.ReadOnlyWorld world = (HologramServiceEditRegression.ReadOnlyWorld) unsafe
                .allocateInstance(HologramServiceEditRegression.ReadOnlyWorld.class);
            EntityPlayerMP player = (EntityPlayerMP) unsafe.allocateInstance(EntityPlayerMP.class);
            player.worldObj = world;
            player.posY = 64;
            player.inventory = new InventoryPlayer(player);
            player.capabilities = new PlayerCapabilities();
            player.capabilities.isCreativeMode = true;
            player.capabilities.allowEdit = true;
            player.inventory.mainInventory[0] = new ItemStack(projector);
            Object session = session(player);
            HologramPresets.Preset original = preset(machines, controller);
            set(session, "virtual", true);
            set(session, "virtualPlacement", true);
            set(session, "virtualPreset", original);
            HologramMaterials.Settings settings = (HologramMaterials.Settings) get(session, "materialSettings");
            settings.main = false;
            settings.containers = false;
            settings.me = false;
            set(session, "materialContext", new HologramMaterials.Context(player, settings));
            Method populate = method("populateVirtual", session.getClass());
            Method rotate = method("rotateVirtual", session.getClass(), boolean.class);
            Method move = method("moveVirtual", session.getClass(), int.class, int.class, int.class, int.class);
            Method serialize = method("editedVirtualPreset", session.getClass(), String.class);
            populate.invoke(null, session);
            // Index 0 is asymmetric stone, index 1 is a north-facing hatch, index 2 is the east-facing controller.
            HologramCapture.Cell manual = new HologramCapture.Cell(null, -4, 66, 5);
            manual.block = Blocks.cobblestone;
            manual.userPosition = true;
            manual.role = "prefab";
            HologramCapture capture = capture(session);
            capture.cells.add(manual);
            capture.positions.put(key(manual), manual);
            pins(session).put(3, new ItemStack(Blocks.cobblestone));
            coordinates(session).put(key(manual), new ItemStack(Blocks.cobblestone));
            map(session, "manualPositions").put(key(manual), manual);
            edits(session).add(key(manual));
            pins(session).put(0, new ItemStack(Blocks.dirt));
            coordinates(session).put("1,66,3", new ItemStack(Blocks.dirt));
            edits(session).add("1,66,3");
            map(session, "frozenAutoTargets").put(key(manual), new ItemStack(Blocks.cobblestone));
            set(session, "allocationsFrozen", true);
            set(session, "selected", 3);
            String initial = signature(session);
            ExtendedFacing initialAlignment = controllerFacing(session);
            SimpleAssert.eq(true, rotate.invoke(null, session, false), "horizontal production turn accepted");
            assertCell(session, 0, -3, 66, 1, -1);
            assertCell(session, 1, -2, 65, -2, 5);
            assertCell(session, 2, 0, 64, 0, 3);
            assertCell(session, 3, -5, 66, -4, -1);
            SimpleAssert.eq(3, get(session, "selected"), "selection follows the same manual cell");
            SimpleAssert.that(edits(session).contains("-3,66,1"), "edited marker rotates with target");
            SimpleAssert.that(map(session, "manualPositions").containsKey("-5,66,-4"), "manual coordinates rotate");
            SimpleAssert.that(map(session, "frozenAutoTargets").containsKey("-5,66,-4"), "frozen allocations rotate");
            SimpleAssert.eq(1, original.cells.get(0).x, "turn detaches preset rather than mutating saved template");
            for (int i = 0; i < 3; i++) SimpleAssert.eq(true, rotate.invoke(null, session, false), "horizontal cycle");
            SimpleAssert.eq(initial, signature(session), "four horizontal turns restore all cells and fronts");
            SimpleAssert.eq(initialAlignment, controllerFacing(session), "horizontal controller orientation restored");
            SimpleAssert.eq(true, rotate.invoke(null, session, true), "vertical production turn accepted");
            assertCell(session, 0, 1, 61, 2, -1);
            assertCell(session, 1, -2, 62, 1, 1);
            assertCell(session, 2, 0, 64, 0, 5);
            assertCell(session, 3, -4, 59, 2, -1);
            ExtendedFacing pitched = controllerFacing(session);
            SimpleAssert.eq(ForgeDirection.EAST, pitched.getDirection(), "east controller keeps front while pitching");
            SimpleAssert.that(pitched.getRotation() != Rotation.NORMAL, "pitch of east front retains nontrivial roll");
            placedControllerAlignment(unsafe, session, controller, pitched);
            for (int i = 0; i < 3; i++) SimpleAssert.eq(true, rotate.invoke(null, session, true), "vertical cycle");
            SimpleAssert.eq(initial, signature(session), "four vertical turns restore cells and fronts");
            SimpleAssert.eq(initialAlignment, controllerFacing(session), "vertical controller orientation restored");
            for (ExtendedFacing facing : ExtendedFacing.values()) {
                ExtendedFacing turned = facing;
                for (int i = 0; i < 4; i++) turned = HologramVirtualTransform.alignment(turned, true);
                SimpleAssert.eq(facing, turned, "all flip and roll combinations preserve identity after four turns");
            }
            gateRejections(session, rotate);
            // Restrict the real prototype alignment policy, not a copied test-side rotation formula.
            alignmentLimits(controller, (d, r, f) -> d != ForgeDirection.SOUTH);
            assertRejectedUnchanged(session, rotate, false, "illegal controller orientation rejects atomically");
            alignmentLimits(controller, (d, r, f) -> f == Flip.NONE);
            GregTechAPI.METATILEENTITIES[HATCH] = (IMetaTileEntity) Proxy.newProxyInstance(
                IMetaTileEntity.class.getClassLoader(),
                new Class<?>[] { IMetaTileEntity.class },
                (proxy, invoked, arguments) -> invoked.getName()
                    .equals("isFacingValid") ? arguments[0] != ForgeDirection.UP
                        : defaultValue(invoked.getReturnType()));
            assertRejectedUnchanged(session, rotate, true, "illegal hatch direction rejects atomically");
            GregTechAPI.METATILEENTITIES[HATCH] = hatch;
            SimpleAssert.eq(true, rotate.invoke(null, session, true), "turn after rejection accepted");
            SimpleAssert.eq(true, move.invoke(null, session, 10, 70, 20, 2), "rotated draft moves via production path");
            assertCell(session, 0, 11, 67, 22, -1);
            assertCell(session, 1, 8, 68, 21, 1);
            assertCell(session, 3, 6, 65, 22, -1);
            SimpleAssert.eq(
                Blocks.dirt,
                Block.getBlockFromItem(
                    pins(session).get(0)
                        .getItem()),
                "manual target retained");
            SimpleAssert.that(edits(session).contains("11,67,22"), "manual marker follows translation");
            SimpleAssert
                .that(map(session, "frozenAutoTargets").containsKey("6,65,22"), "frozen target follows translation");
            HologramPresets.Preset saved = (HologramPresets.Preset) serialize.invoke(null, session, "Rotated");
            saved.id = original.id;
            SimpleAssert.eq(pitched.ordinal(), saved.controllerFacing, "save includes full roll");
            Method validate = HologramPresets.class.getDeclaredMethod("validate", HologramPresets.Preset.class);
            validate.setAccessible(true);
            validate.invoke(null, saved);
            cache = field(HologramPresets.class, "cached");
            oldCache = cache.get(null);
            Method write = HologramPresets.class.getDeclaredMethod("write", Path.class, List.class);
            write.setAccessible(true);
            directory = Files.createTempDirectory("gtit-rotation-preset-");
            Path file = directory.resolve("presets.json");
            write.invoke(null, file, java.util.Collections.singletonList(saved));
            Method decode = HologramPresets.class.getDeclaredMethod("decode", String.class);
            decode.setAccessible(true);
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Object data = decode.invoke(null, json);
            @SuppressWarnings("unchecked")
            List<HologramPresets.Preset> reloaded = (List<HologramPresets.Preset>) field(data.getClass(), "presets")
                .get(data);
            HologramPresets.Preset loaded = reloaded.get(0);
            validate.invoke(null, loaded);
            set(session, "virtualPreset", loaded);
            populate.invoke(null, session);
            assertCell(session, 0, 11, 67, 22, -1);
            assertCell(session, 1, 8, 68, 21, 1);
            SimpleAssert.eq(pitched, controllerFacing(session), "JSON load preserves full controller orientation");
            SimpleAssert.eq(
                Blocks.dirt,
                Block.getBlockFromItem(
                    pins(session).get(0)
                        .getItem()),
                "JSON load retains edit");
            SimpleAssert.eq(4, capture(session).cells.size(), "JSON load retains added asymmetric cell");
            HologramPresets.Preset legacy = HologramPresets.copy(original);
            legacy.controllerFacing = null;
            validate.invoke(null, legacy);
            SimpleAssert
                .eq(initialAlignment, HologramVirtualTransform.controller(legacy), "schema 1 front-only compatibility");
            HologramPresets.Preset invalid = HologramPresets.copy(saved);
            invalid.controllerFacing = -1;
            expectIo(validate, invalid);
            invalid.controllerFacing = ExtendedFacing.values().length;
            expectIo(validate, invalid);
            invalid.controllerFacing = ExtendedFacing.NORTH_NORMAL_NONE.ordinal();
            expectIo(validate, invalid);
            for (String replacement : new String[] { "\"EAST_NORMAL_NONE\"", "\"16\"", "1.5", "true" }) {
                String hostile = json
                    .replaceFirst("\"controllerFacing\"\\s*:\\s*\\d+", "\"controllerFacing\": " + replacement);
                expectIo(decode, hostile);
            }
            Files.delete(file);
        } catch (Throwable failure) {
            failure.printStackTrace();
            throw new AssertionError("production virtual rotation regression", failure);
        } finally {
            Thread.currentThread()
                .setName(threadName);
            GregTechAPI.METATILEENTITIES[CONTROLLER] = oldController;
            GregTechAPI.METATILEENTITIES[HATCH] = oldHatch;
            try {
                if (cache != null) cache.set(null, oldCache);
                if (directory != null) Files.deleteIfExists(directory);
            } catch (Exception failure) {
                throw new AssertionError("rotation fixture cleanup", failure);
            }
        }
    }

    private static void placedControllerAlignment(sun.misc.Unsafe unsafe, Object session,
        AlignmentController controller, ExtendedFacing expected) throws Exception {
        AlignmentTile tile = (AlignmentTile) unsafe.allocateInstance(AlignmentTile.class);
        tile.front = ForgeDirection.NORTH;
        tile.setWorldObj(((EntityPlayerMP) get(session, "player")).worldObj);
        // Avoid alignment network sends; still execute the real machine setter and texture-update branch.
        GregTechAPI.addDummyWorld(
            tile.getWorld()
                .getClass());
        controller.setBaseMetaTileEntity(tile);
        method("applyVirtualControllerAlignment", session.getClass(), TileEntity.class).invoke(null, session, tile);
        SimpleAssert
            .eq(expected, controller.getExtendedFacing(), "production placement invokes real machine setter with roll");
        SimpleAssert.eq(expected.getDirection(), tile.front, "production placement keeps base front consistent");
    }

    /** No structure singleton dependencies; alignment methods remain the real GT enhanced-controller implementation. */
    static class AlignmentController extends MTEEnhancedMultiBlockBase<AlignmentController> {

        private AlignmentController() {
            super("rotation_fixture");
        }

        @Override
        public com.gtnewhorizon.structurelib.structure.IStructureDefinition<AlignmentController> getStructureDefinition() {
            return null;
        }

        @Override
        protected MultiblockTooltipBuilder createTooltip() {
            return null;
        }

        @Override
        public IMetaTileEntity newMetaEntity(IGregTechTileEntity base) {
            return null;
        }

        @Override
        public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, ForgeDirection front, int color,
            boolean active, boolean redstone) {
            return new ITexture[0];
        }

        @Override
        public void construct(ItemStack trigger, boolean hints) {}
    }

    // The base implements the actual GT interface while inert side effects isolate alignment from world mutation.
    static class AlignmentTile extends gregtech.api.metatileentity.BaseMetaTileEntity {

        ForgeDirection front;

        @Override
        public ForgeDirection getFrontFacing() {
            return front;
        }

        @Override
        public void setFrontFacing(ForgeDirection direction) {
            front = direction;
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    private static void gateRejections(Object session, Method rotate) throws Exception {
        for (String flag : new String[] { "virtual", "virtualPlacement" }) {
            set(session, flag, false);
            assertRejectedUnchanged(session, rotate, false, "pure editor rejects " + flag);
            set(session, flag, true);
        }
        for (String flag : new String[] { "virtualLocked", "capturePending", "cancelled" }) {
            set(session, flag, true);
            assertRejectedUnchanged(session, rotate, true, "gate rejects " + flag);
            set(session, flag, false);
        }
        for (int job : new int[] { 1, 2, 4 }) {
            set(session, "job", job);
            assertRejectedUnchanged(session, rotate, false, "gate rejects job " + job);
        }
        set(session, "job", 0);
        capture(session).incomplete = true;
        assertRejectedUnchanged(session, rotate, false, "incomplete geometry cannot turn");
        capture(session).incomplete = false;
        capture(session).pending = true;
        assertRejectedUnchanged(session, rotate, false, "pending geometry cannot turn");
        capture(session).pending = false;
        EntityPlayerMP player = (EntityPlayerMP) get(session, "player");
        ItemStack held = player.inventory.mainInventory[0];
        player.inventory.mainInventory[0] = new ItemStack(Blocks.stone);
        assertRejectedUnchanged(session, rotate, false, "invalid held tool cannot turn");
        player.inventory.mainInventory[0] = held;
    }

    private static void assertRejectedUnchanged(Object session, Method rotate, boolean vertical, String message)
        throws Exception {
        String before = signature(session);
        HologramCapture capture = capture(session);
        Object preset = get(session, "virtualPreset");
        Object revision = get(session, "planRevision");
        SimpleAssert.eq(false, rotate.invoke(null, session, vertical), message);
        SimpleAssert.eq(before, signature(session), message + " preserves geometry, pins and facing");
        SimpleAssert.that(
            capture == capture(session) && preset == get(session, "virtualPreset"),
            message + " no partial mutation");
        SimpleAssert.eq(revision, get(session, "planRevision"), message + " preserves plan revision");
    }

    private static HologramPresets.Preset preset(ItemMachines machines, AlignmentController controller) {
        HologramPresets.Preset preset = new HologramPresets.Preset();
        preset.id = "rotation_fixture";
        preset.name = "Asymmetric";
        preset.controllerId = CONTROLLER;
        preset.controllerType = controller.getClass()
            .getName() + ":"
            + CONTROLLER;
        preset.cells.add(HologramPresets.cell(new ItemStack(Blocks.stone), 1, 2, 3, -1, false));
        preset.cells.add(HologramPresets.cell(new ItemStack(machines, 1, HATCH), -2, 1, 2, 2, false));
        preset.cells.add(HologramPresets.cell(new ItemStack(machines, 1, CONTROLLER), 0, 0, 0, 5, true));
        for (HologramPresets.Cell cell : preset.cells) preset.palette.add(cell);
        preset.palette.add(HologramPresets.cell(new ItemStack(Blocks.dirt), 0, 0, 0, -1, false));
        preset.palette.add(HologramPresets.cell(new ItemStack(Blocks.cobblestone), 0, 0, 0, -1, false));
        return preset;
    }

    private static Object session(EntityPlayerMP player) throws Exception {
        Class<?> type = Class.forName(HologramService.class.getName() + "$Session");
        Constructor<?> constructor = type
            .getDeclaredConstructor(EntityPlayerMP.class, int.class, int.class, int.class, boolean.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(player, 0, 64, 0, false, 2);
    }

    private static void alignmentLimits(MTEEnhancedMultiBlockBase<?> controller, IAlignmentLimits limits)
        throws Exception {
        field(MTEEnhancedMultiBlockBase.class, "mLimits").set(controller, limits);
    }

    private static ExtendedFacing controllerFacing(Object session) throws Exception {
        return (ExtendedFacing) method("virtualControllerFacing", session.getClass()).invoke(null, session);
    }

    private static void assertCell(Object session, int index, int x, int y, int z, int front) throws Exception {
        HologramCapture.Cell cell = capture(session).cells.get(index);
        SimpleAssert.eq(x + "," + y + "," + z, key(cell), "cell coordinate at " + index);
        SimpleAssert.eq(front, map(session, "facingPins").getOrDefault(key(cell), -1), "cell facing at " + index);
    }

    private static String signature(Object session) throws Exception {
        List<String> result = new ArrayList<>();
        HologramCapture capture = capture(session);
        for (int i = 0; i < capture.cells.size(); i++) {
            HologramCapture.Cell cell = capture.cells.get(i);
            ItemStack stack = pins(session).get(i);
            result.add(
                key(cell) + ":"
                    + stack.getItem()
                    + ":"
                    + stack.getItemDamage()
                    + ":"
                    + map(session, "facingPins").get(key(cell)));
        }
        return result.toString();
    }

    private static void expectIo(Method method, Object argument) throws Exception {
        try {
            method.invoke(null, argument);
            throw new AssertionError("expected IOException from " + method.getName());
        } catch (InvocationTargetException failure) {
            if (!(failure.getCause() instanceof java.io.IOException)) throw failure;
        }
    }

    private static String key(HologramCapture.Cell cell) {
        return cell.x + "," + cell.y + "," + cell.z;
    }

    private static HologramCapture capture(Object session) throws Exception {
        return (HologramCapture) get(session, "capture");
    }

    private static Map<Integer, ItemStack> pins(Object session) throws Exception {
        return map(session, "pins");
    }

    private static Map<String, ItemStack> coordinates(Object session) throws Exception {
        return map(session, "coordinatePins");
    }

    @SuppressWarnings("unchecked")
    private static Set<String> edits(Object session) throws Exception {
        return (Set<String>) get(session, "virtualEdited");
    }

    private static Method method(String name, Class<?>... parameters) throws Exception {
        Method method = HologramService.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object get(Object instance, String name) throws Exception {
        return field(instance.getClass(), name).get(instance);
    }

    private static void set(Object instance, String name, Object value) throws Exception {
        field(instance.getClass(), name).set(instance, value);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(Object instance, String name) throws Exception {
        return (Map<K, V>) get(instance, name);
    }
}
