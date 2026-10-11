package com.miaokatze.gtit.hologram;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
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
