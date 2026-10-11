package com.miaokatze.gtit.hologram;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.miaokatze.gtit.testutil.SimpleAssert;

/** Exercises the production editor and allocation refresh against a read-only world fixture. */
final class HologramServiceEditRegression {

    private HologramServiceEditRegression() {}

    static void run() {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            ReadOnlyWorld world = (ReadOnlyWorld) unsafe.allocateInstance(ReadOnlyWorld.class);
            EntityPlayerMP player = (EntityPlayerMP) unsafe.allocateInstance(EntityPlayerMP.class);
            player.worldObj = world;
            player.inventory = new InventoryPlayer(player);
            player.capabilities = new PlayerCapabilities();
            player.capabilities.isCreativeMode = true;
            player.capabilities.allowEdit = true;
            player.inventory.mainInventory[0] = new ItemStack(Blocks.stone);
            Class<?> sessionType = Class.forName(HologramService.class.getName() + "$Session");
            Constructor<?> constructor = sessionType.getDeclaredConstructor(
                EntityPlayerMP.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                int.class);
            constructor.setAccessible(true);
            Object session = constructor.newInstance(player, 0, 64, 0, false, 2);
            HologramCapture capture = new HologramCapture(null, new ItemStack(Blocks.stone));
            for (int x = 1; x <= 4; x++) {
                HologramCapture.Cell cell = new HologramCapture.Cell(
                    StructureUtility.ofBlock(Blocks.stone, 0),
                    x,
                    64,
                    0);
                cell.block = Blocks.stone;
                capture.cells.add(cell);
            }
            set(session, "capture", capture);
            set(session, "mode", 0);
            set(session, "scope", 0);
            set(session, "selected", 0);
            HologramMaterials.Settings settings = (HologramMaterials.Settings) get(session, "materialSettings");
            settings.main = false;
            settings.containers = false;
            settings.me = false;
            set(session, "materialContext", new HologramMaterials.Context(player, settings));
            Map<Integer, ItemStack> automatic = map(session, "autoTargets");
            automatic.put(0, new ItemStack(Blocks.dirt));
            automatic.put(1, new ItemStack(Blocks.cobblestone));
            List<ItemStack> targets = list(session, "planTargets");
            targets.add(new ItemStack(Blocks.dirt));
            targets.add(new ItemStack(Blocks.cobblestone));
            targets.add(new ItemStack(Blocks.stone));
            targets.add(new ItemStack(Blocks.stone));
            Method edit = HologramService.class
                .getDeclaredMethod("edit", sessionType, NBTTagCompound.class, String.class);
            edit.setAccessible(true);
            Method refresh = HologramService.class.getDeclaredMethod("refreshPlan", sessionType);
            refresh.setAccessible(true);
            Class<?> stateType = Class.forName(HologramService.class.getName() + "$EditState");
            Constructor<?> snapshot = stateType.getDeclaredConstructor(sessionType);
            snapshot.setAccessible(true);
            Object original = snapshot.newInstance(session);
            action(edit, session, "editCopy", 0);
            assertTarget(session, 0, Blocks.dirt);
            assertTarget(session, 1, Blocks.cobblestone);
            SimpleAssert.eq(0, get(session, "mode"), "copy cannot change construction mode");
            SimpleAssert.eq(0, get(session, "scope"), "copy cannot change scope");
            SimpleAssert.that(!(Boolean) get(session, "allocationsFrozen"), "copy cannot mutate allocation state");
            ItemStack clipboard = (ItemStack) get(session, "editClipboard");
            SimpleAssert.that(!clipboard.hasTagCompound(), "clipboard only stores clean item identity");
            action(edit, session, "editPaste", 2);
            SimpleAssert.eq(0, get(session, "mode"), "paste preserves construction mode");
            SimpleAssert.eq(0, get(session, "scope"), "paste preserves scope");
            assertTarget(session, 0, Blocks.dirt);
            assertTarget(session, 1, Blocks.cobblestone);
            assertTarget(session, 2, Blocks.dirt);
            SimpleAssert.eq(1, map(session, "pins").size(), "paste only pins its destination");
            SimpleAssert.eq(1, map(session, "coordinatePins").size(), "coordinate plan only changes destination");
            refresh.invoke(null, session);
            assertTarget(session, 0, Blocks.dirt);
            assertTarget(session, 1, Blocks.cobblestone);
            Object pasted = snapshot.newInstance(session);
            action(edit, session, "editCut", 0);
            assertTarget(session, 0, Blocks.stone);
            assertTarget(session, 1, Blocks.cobblestone);
            assertTarget(session, 2, Blocks.dirt);
            SimpleAssert.eq(2, map(session, "pins").size(), "cut only restores source shell");
            Method configure = HologramService.class.getDeclaredMethod("configure", sessionType, NBTTagCompound.class);
            configure.setAccessible(true);
            NBTTagCompound selection = new NBTTagCompound();
            selection.setInteger("main", 1);
            selection.setTag("channels", new NBTTagCompound());
            selection.setInteger("mode", 1);
            selection.setInteger("scope", 2);
            selection.setInteger("selected", 3);
            selection.setInteger("layer", 1);
            SimpleAssert.that((Boolean) configure.invoke(null, session, selection), "selection configure accepted");
            SimpleAssert.that(
                (Boolean) get(session, "allocationsFrozen"),
                "changing selection/scope/layer keeps allocations frozen");
            assertTarget(session, 0, Blocks.stone);
            assertTarget(session, 1, Blocks.cobblestone);
            assertTarget(session, 2, Blocks.dirt);
            action(edit, session, "editPaste", 3);
            assertTarget(session, 0, Blocks.stone);
            assertTarget(session, 1, Blocks.cobblestone);
            assertTarget(session, 2, Blocks.dirt);
            assertTarget(session, 3, Blocks.dirt);
            List<Object> undo = list(session, "undoEdits");
            undo.add(pasted);
            action(edit, session, "editUndo", 0);
            assertTarget(session, 0, Blocks.dirt);
            assertTarget(session, 1, Blocks.cobblestone);
            assertTarget(session, 2, Blocks.dirt);
            assertTarget(session, 3, Blocks.stone);
            action(edit, session, "editRedo", 0);
            assertTarget(session, 0, Blocks.stone);
            assertTarget(session, 1, Blocks.cobblestone);
            assertTarget(session, 3, Blocks.dirt);
            Method apply = stateType.getDeclaredMethod("apply", sessionType);
            apply.setAccessible(true);
            apply.invoke(original, session);
            refresh.invoke(null, session);
            assertTarget(session, 0, Blocks.dirt);
            assertTarget(session, 1, Blocks.cobblestone);
            SimpleAssert.eq(0, map(session, "coordinatePins").size(), "undo restores original explicit pins");
            set(session, "job", 1);
            action(edit, session, "editCut", 0);
            assertTarget(session, 0, Blocks.dirt);
            SimpleAssert.eq(1, get(session, "job"), "active construction rejects clipboard edits");
            Method classify = HologramService.class
                .getDeclaredMethod("classify", sessionType, HologramCapture.Cell.class, int.class);
            Method operate = HologramService.class
                .getDeclaredMethod("operateChecked", sessionType, HologramCapture.Cell.class, int.class);
            Method build = HologramService.class.getDeclaredMethod("directBuild", sessionType, boolean.class);
            Method nativeBuild = HologramService.class.getDeclaredMethod("nativeBuild", sessionType);
            Method member = HologramService.class
                .getDeclaredMethod("originalMember", sessionType, HologramCapture.Cell.class);
            for (Method method : new Method[] { classify, operate, build, nativeBuild, member })
                method.setAccessible(true);
            set(session, "job", 0);
            set(session, "mode", 0);
            set(session, "scope", 0);
            HologramCapture.Cell cell = capture.cells.get(0);
            world.occupiedX = cell.x;
            world.occupied = Blocks.stone;
            map(session, "pins").put(0, new ItemStack(Blocks.dirt));
            HologramServiceEditRegression.<ItemStack>list(session, "planTargets")
                .set(0, new ItemStack(Blocks.dirt));
            player.capabilities.isCreativeMode = false;
            HologramServiceEditRegression.<ItemStack>list(session, "planTargets")
                .set(0, new ItemStack(Blocks.stone));
            map(session, "facingPins").put(cell.x + "," + cell.y + "," + cell.z, 3);
            SimpleAssert.eq(
                "satisfied",
                classify.invoke(null, session, cell, 0),
                "construction ignores facing-only differences");
            SimpleAssert
                .eq(true, operate.invoke(null, session, cell, 0), "matching construction preserves the existing block");
            map(session, "facingPins").clear();
            HologramServiceEditRegression.<ItemStack>list(session, "planTargets")
                .set(0, new ItemStack(Blocks.dirt));
            SimpleAssert
                .eq("blocked", classify.invoke(null, session, cell, 0), "survival explicit target stays blocked");
            SimpleAssert.eq(false, operate.invoke(null, session, cell, 0), "blocked operation cannot write or consume");
            build.invoke(null, session, false);
            SimpleAssert.eq(0, get(session, "job"), "obstruction rejects the whole selected range");
            SimpleAssert.that(((String) get(session, "status")).contains("1 格结构占位被遮挡"), "blocked count is visible");
            player.capabilities.isCreativeMode = true;
            SimpleAssert.eq(
                "pending",
                classify.invoke(null, session, cell, 0),
                "creative ordinary obstruction can be overwritten");
            world.occupant = new TileEntity();
            SimpleAssert
                .eq("protected", classify.invoke(null, session, cell, 0), "creative unknown tile stays protected");
            world.occupant = null;
            SimpleAssert.eq(true, member.invoke(null, session, cell), "declared existing block is an original member");
            cell.userPosition = true;
            SimpleAssert.eq(false, member.invoke(null, session, cell), "manual positions cannot authorize replacement");
            cell.userPosition = false;
            cell.block = Blocks.air;
            SimpleAssert.eq(false, member.invoke(null, session, cell), "declared air cannot authorize replacement");
            player.capabilities.isCreativeMode = false;
            HologramServiceEditRegression.<ItemStack>list(session, "planTargets")
                .set(0, null);
            SimpleAssert.eq(
                "blocked",
                classify.invoke(null, session, cell, 0),
                "occupied declared air blocks survival construction");
            HologramServiceEditRegression.<ItemStack>list(session, "planTargets")
                .set(0, new ItemStack(Blocks.dirt));
            player.capabilities.isCreativeMode = true;
            cell.block = Blocks.stone;
            set(session, "mode", 1);
            set(session, "replacementReady", true);
            world.occupied = Blocks.air;
            SimpleAssert
                .eq("protected", classify.invoke(null, session, cell, 0), "replacement cannot fill empty space");
            world.occupied = Blocks.stone;
            set(session, "replacementAuthorized", false);
            operate.invoke(null, session, cell, 0);
            SimpleAssert.eq("protected", cell.status, "replacement needs start authorization");
            set(session, "mode", 0);
            capture.incomplete = true;
            nativeBuild.invoke(null, session);
            SimpleAssert
                .that(((String) get(session, "status")).contains("遮挡"), "incomplete native fallback is rejected");
            capture.incomplete = false;
            action(edit, session, "restoreCell", 0);
            SimpleAssert.eq(0, get(session, "mode"), "restoring the planned shell cannot switch construction mode");
            calibrationRegression(session, capture, refresh);
            virtualPresetEditingRegression(session, capture);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void calibrationRegression(Object session, HologramCapture capture, Method refresh)
        throws ReflectiveOperationException {
        NBTTagList rows = new NBTTagList();
        NBTTagCompound row = new NBTTagCompound();
        row.setString("id", "coil");
        row.setString("kind", "coil");
        NBTTagList options = new NBTTagList();
        for (int i = 0; i < 2; i++) {
            NBTTagCompound option = new NBTTagCompound();
            option.setInteger("value", i + 1);
            option.setTag("item", new ItemStack(i == 0 ? Blocks.stone : Blocks.dirt).writeToNBT(new NBTTagCompound()));
            options.appendTag(option);
        }
        row.setTag("options", options);
        rows.appendTag(row);
        capture.cells.get(0).candidates = new ArrayList<>(
            Arrays.asList(new ItemStack(Blocks.stone), new ItemStack(Blocks.dirt)));
        capture.cells.get(1).candidates = new ArrayList<>(capture.cells.get(0).candidates);
        capture.cells.get(2).candidates = new ArrayList<>();
        capture.cells.get(3).candidates = new ArrayList<>();
        Map<Integer, ItemStack> pins = map(session, "pins");
        Map<String, ItemStack> coordinates = map(session, "coordinatePins");
        pins.clear();
        coordinates.clear();
        for (int i = 0; i < 3; i++) {
            ItemStack target = new ItemStack(i == 2 ? Blocks.cobblestone : Blocks.dirt);
            pins.put(i, target);
            coordinates.put(calibrationPosition(capture.cells.get(i)), target.copy());
        }
        NBTTagCompound channels = new NBTTagCompound();
        channels.setInteger("coil", 14);
        channels.setInteger("unreviewed_geometry", 7);
        HologramService.calibrateMaterialChannels(capture, rows, channels, pins, coordinates);
        SimpleAssert
            .eq(2, channels.getInteger("coil"), "calibration infers the actual uniform material instead of old plan");
        SimpleAssert.eq(7, channels.getInteger("unreviewed_geometry"), "unknown geometry is not fabricated or reset");
        SimpleAssert.eq(1, pins.size(), "only the independent installed target remains pinned");
        SimpleAssert.eq(1, coordinates.size(), "material coordinates cannot shadow a subsequent channel recapture");
        SimpleAssert.that(pins.containsKey(2), "independent hatch-like target is retained");
        // A changed signal causes StructureLib to capture a different default. Exercise the real plan refresh
        // with that capture result, proving the calibrated old material no longer wins target precedence.
        capture.cells.get(0).block = Blocks.stone;
        capture.cells.get(1).block = Blocks.stone;
        map(session, "autoTargets").clear();
        map(session, "frozenAutoTargets").clear();
        set(session, "allocationsFrozen", false);
        set(session, "selected", -1);
        refresh.invoke(null, session);
        assertTarget(session, 0, Blocks.stone);
        assertTarget(session, 1, Blocks.stone);
        assertTarget(session, 2, Blocks.cobblestone);
        Map<Integer, ItemStack> mixed = new HashMap<>();
        Map<String, ItemStack> actual = new HashMap<>();
        mixed.put(0, new ItemStack(Blocks.stone));
        mixed.put(1, new ItemStack(Blocks.dirt));
        actual.put(calibrationPosition(capture.cells.get(0)), mixed.get(0));
        actual.put(calibrationPosition(capture.cells.get(1)), mixed.get(1));
        channels.setInteger("coil", 14);
        HologramService.calibrateMaterialChannels(capture, rows, channels, mixed, actual);
        SimpleAssert.that(!channels.hasKey("coil"), "mixed materials cannot preserve or invent a uniform signal");
        SimpleAssert.eq(2, mixed.size(), "legal mixed targets remain independent");
        actual.remove(calibrationPosition(capture.cells.get(1)));
        channels.setInteger("coil", 14);
        HologramService.calibrateMaterialChannels(capture, rows, channels, mixed, actual);
        SimpleAssert.that(!channels.hasKey("coil"), "missing or rejected material cannot establish a signal");
        SimpleAssert.eq(2, mixed.size(), "failed inference does not remove independent targets");
    }

    private static String calibrationPosition(HologramCapture.Cell cell) {
        return cell.x + "," + cell.y + "," + cell.z;
    }

    private static void virtualPresetEditingRegression(Object session, HologramCapture capture)
        throws ReflectiveOperationException {
        HologramPresets.Preset original = new HologramPresets.Preset();
        original.id = "existing-template";
        original.name = "Original";
        original.controllerId = 123;
        original.controllerType = "server-known-controller:123";
        for (Block block : new Block[] { Blocks.stone, Blocks.dirt, Blocks.cobblestone })
            original.palette.add(HologramPresets.cell(new ItemStack(block), 0, 0, 0, -1, false));
        for (HologramCapture.Cell cell : capture.cells) {
            HologramPresets.Cell saved = HologramPresets.cell(new ItemStack(Blocks.stone), cell.x, 0, 0, -1, false);
            saved.defaultItem = original.palette.get(2).item;
            original.cells.add(saved);
        }
        set(session, "virtual", true);
        set(session, "virtualPreset", original);
        Map<Integer, ItemStack> pins = map(session, "pins");
        pins.clear();
        for (int i = 0; i < capture.cells.size(); i++) pins.put(i, new ItemStack(Blocks.dirt));
        @SuppressWarnings("unchecked")
        java.util.Set<String> edited = (java.util.Set<String>) get(session, "virtualEdited");
        edited.add(calibrationPosition(capture.cells.get(1)));
        Method serialize = HologramService.class
            .getDeclaredMethod("editedVirtualPreset", session.getClass(), String.class);
        serialize.setAccessible(true);
        HologramPresets.Preset result = (HologramPresets.Preset) serialize.invoke(null, session, "Edited");
        SimpleAssert
            .eq(original.controllerType, result.controllerType, "serialization retains server controller identity");
        SimpleAssert.eq(original.controllerId, result.controllerId, "serialization retains server controller id");
        SimpleAssert.eq(
            Blocks.stone,
            Block.getBlockFromItem(
                result.cells.get(0)
                    .stack()
                    .getItem()),
            "inventory allocation cannot silently rewrite untouched template targets");
        SimpleAssert.eq(
            Blocks.dirt,
            Block.getBlockFromItem(
                result.cells.get(1)
                    .stack()
                    .getItem()),
            "explicit template edits are serialized");
        SimpleAssert.eq(
            original.cells.get(1).defaultItem,
            result.cells.get(1).defaultItem,
            "template shell fallback survives target editing");
        SimpleAssert.eq(3, result.palette.size(), "unused editing candidates survive serialization");
        SimpleAssert.eq(
            Blocks.stone,
            Block.getBlockFromItem(
                original.cells.get(1)
                    .stack()
                    .getItem()),
            "serialization leaves the saved original unchanged before persistence succeeds");
        ItemStack sealed = pins.get(1);
        sealed.setTagCompound(new NBTTagCompound());
        sealed.getTagCompound()
            .setString("deviceInventory", "must-not-save");
        try {
            serialize.invoke(null, session, "Unsafe");
            throw new AssertionError("device NBT must not be serialized into a template");
        } catch (java.lang.reflect.InvocationTargetException rejected) {
            SimpleAssert
                .that(rejected.getCause() instanceof java.io.IOException, "unsafe template returns a save failure");
        }
    }

    private static void action(Method edit, Object session, String op, int selected)
        throws ReflectiveOperationException {
        NBTTagCompound action = new NBTTagCompound();
        action.setInteger("selected", selected);
        edit.invoke(null, session, action, op);
    }

    private static void assertTarget(Object session, int index, Block block) throws ReflectiveOperationException {
        ItemStack target = HologramServiceEditRegression.<ItemStack>list(session, "planTargets")
            .get(index);
        SimpleAssert.that(target != null && Block.getBlockFromItem(target.getItem()) == block, "target at " + index);
    }

    private static Object get(Object instance, String name) throws ReflectiveOperationException {
        Field field = instance.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void set(Object instance, String name, Object value) throws ReflectiveOperationException {
        Field field = instance.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(Object instance, String name) throws ReflectiveOperationException {
        return (Map<K, V>) get(instance, name);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(Object instance, String name) throws ReflectiveOperationException {
        return (List<T>) get(instance, name);
    }

    static final class ReadOnlyWorld extends WorldServer {

        Block occupied;
        int occupiedX;
        TileEntity occupant;

        private ReadOnlyWorld() {
            super(null, null, "fixture", 0, null, null);
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return true;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            return occupied != null && x == occupiedX ? occupied : Blocks.air;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return 0;
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return x == occupiedX ? occupant : null;
        }

        @Override
        public boolean canMineBlock(EntityPlayer player, int x, int y, int z) {
            return true;
        }

        @Override
        public int getHeight() {
            return 256;
        }

        @Override
        public long getTotalWorldTime() {
            return 0;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Block block, int meta, int flags) {
            throw new AssertionError("planning editor must never write world blocks");
        }

        @Override
        public void setTileEntity(int x, int y, int z, TileEntity tile) {
            throw new AssertionError("planning editor must never write world tiles");
        }
    }
}
