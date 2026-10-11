package com.miaokatze.gtit.hologram;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.storage.MapStorage;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.miaokatze.gtit.testutil.SimpleAssert;

/** Production piece traversal batching and world-owned draft persistence, with world writes forbidden. */
final class HologramLargeStructureRegression {

    private HologramLargeStructureRegression() {}

    static void run() {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            HologramServiceEditRegression.ReadOnlyWorld world = (HologramServiceEditRegression.ReadOnlyWorld) unsafe
                .allocateInstance(HologramServiceEditRegression.ReadOnlyWorld.class);
            Field storage = net.minecraft.world.World.class.getDeclaredField("perWorldStorage");
            storage.setAccessible(true);
            storage.set(world, new MapStorage(null));
            captureBatches(unsafe, world);
            draftRoundTrip(unsafe, world);
        } catch (Throwable failure) {
            failure.printStackTrace();
            throw new AssertionError(failure);
        }
    }

    private static void captureBatches(sun.misc.Unsafe unsafe, HologramServiceEditRegression.ReadOnlyWorld world)
        throws ReflectiveOperationException {
        Object context = new Object();
        String[] rows = new String[60];
        java.util.Arrays.fill(rows, new String(new char[100]).replace('\0', 'A'));
        IStructureDefinition<Object> definition = StructureDefinition.<Object>builder()
            .addShape("main", new String[][] { rows })
            .addElement('A', StructureUtility.ofBlock(Blocks.stone, 0))
            .build();
        ItemStack trigger = new ItemStack(Blocks.stone);
        HologramCapture capture = new HologramCapture(context, trigger);
        capture.pieces.add(new HologramCapture.Piece("main", 0, 0, 0, trigger));
        Method walk = HologramCapture.class.getDeclaredMethod(
            "walkPieces",
            HologramCapture.class,
            IStructureDefinition.class,
            net.minecraft.world.World.class,
            ExtendedFacing.class,
            int.class,
            int.class,
            int.class);
        walk.setAccessible(true);
        Field activeField = HologramCapture.class.getDeclaredField("ACTIVE");
        activeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<HologramCapture> active = (ThreadLocal<HologramCapture>) activeField.get(null);
        int batches = 0;
        try {
            active.set(capture);
            do {
                int before = capture.cells.size();
                capture.deadline = System.nanoTime() + 100_000_000L;
                capture.pending = false;
                try {
                    walk.invoke(null, capture, definition, world, ExtendedFacing.DEFAULT, 0, 64, 0);
                } catch (java.lang.reflect.InvocationTargetException yielded) {
                    if (!yielded.getCause()
                        .getClass()
                        .getSimpleName()
                        .equals("CaptureYieldException")) throw yielded;
                }
                SimpleAssert
                    .that(capture.cells.size() > before || !capture.pending, "each continuation makes progress");
                batches++;
            } while (capture.pending && batches < 64);
        } finally {
            active.remove();
        }
        SimpleAssert.that(batches >= 2, "6000 cells span multiple production traversal batches");
        SimpleAssert.that(!capture.pending && !capture.incomplete, "full declared structure was collected");
        SimpleAssert.eq(6000, capture.cells.size(), "no cell-count truncation");
        SimpleAssert.eq(1, capture.pieces.size(), "continuation retains original declaration");
    }

    private static void draftRoundTrip(sun.misc.Unsafe unsafe, HologramServiceEditRegression.ReadOnlyWorld world)
        throws ReflectiveOperationException {
        EntityPlayerMP player = (EntityPlayerMP) unsafe.allocateInstance(EntityPlayerMP.class);
        player.worldObj = world;
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities();
        player.capabilities.isCreativeMode = true;
        player.capabilities.allowEdit = true;
        player.inventory.mainInventory[0] = new ItemStack(Blocks.stone);
        Class<?> type = Class.forName(HologramService.class.getName() + "$Session");
        Constructor<?> constructor = type
            .getDeclaredConstructor(EntityPlayerMP.class, int.class, int.class, int.class, boolean.class, int.class);
        constructor.setAccessible(true);
        Object session = constructor.newInstance(player, 0, 64, 0, false, 2);
        set(session, "target", new TileEntity());
        HologramCapture capture = new HologramCapture(null, new ItemStack(Blocks.stone));
        @SuppressWarnings("unchecked")
        Map<Integer, ItemStack> pins = (Map<Integer, ItemStack>) get(session, "pins");
        for (int i = 0; i < 300; i++) {
            HologramCapture.Cell cell = new HologramCapture.Cell(
                StructureUtility.ofBlock(Blocks.stone, 0),
                i + 1,
                64,
                0);
            cell.block = Blocks.stone;
            capture.cells.add(cell);
            pins.put(i, new ItemStack(Blocks.stone));
        }
        set(session, "capture", capture);
        HologramMaterials.Settings settings = (HologramMaterials.Settings) get(session, "materialSettings");
        settings.main = false;
        settings.containers = false;
        settings.me = false;
        method("writeDraft", type, ItemStack.class).invoke(null, session, player.getHeldItem());
        NBTTagCompound reference = player.getHeldItem()
            .getTagCompound()
            .getCompoundTag("gtitHologramDraft");
        SimpleAssert.that(reference.getBoolean("serverStored"), "large drafts use server storage references");
        SimpleAssert.eq(
            0,
            reference.getTagList("edits", 10)
                .tagCount(),
            "inventory NBT contains no large edits list");
        NBTTagCompound actual = (NBTTagCompound) method("storedDraft", type, ItemStack.class)
            .invoke(null, session, player.getHeldItem());
        SimpleAssert.eq(
            300,
            actual.getTagList("edits", 10)
                .tagCount(),
            "server reference resolves all edits");
        String key = (String) method("draftKey", type).invoke(null, session);
        NBTTagCompound serialized = new NBTTagCompound();
        HologramDraftStorage.get(world)
            .writeToNBT(serialized);
        HologramDraftStorage restored = new HologramDraftStorage("fixture");
        restored.readFromNBT(serialized);
        NBTTagList restoredEdits = restored.read(key)
            .getTagList("edits", 10);
        SimpleAssert.eq(300, restoredEdits.tagCount(), "world save/load retains all large draft edits");
        SimpleAssert.eq(
            300,
            restoredEdits.getCompoundTagAt(299)
                .getInteger("dx"),
            "last distant edit survives");
        NBTTagCompound action = new NBTTagCompound();
        action.setInteger("materialPriority", 2);
        method("applySettings", type, NBTTagCompound.class).invoke(null, session, action);
        actual = (NBTTagCompound) method("storedDraft", type, ItemStack.class)
            .invoke(null, session, player.getHeldItem());
        SimpleAssert
            .eq(2, actual.getInteger("materialPriority"), "global settings update the server draft, not its stub");
        // An unsupported/no-machine calibration must preserve pre-existing planning targets.
        method("calibrateCurrent", type).invoke(null, session);
        SimpleAssert.eq(300, pins.size(), "failed calibration preserves the original planned targets");
        set(session, "buildAfterCapture", true);
        set(session, "calibratePending", true);
        method("cancelCaptureRetry", type).invoke(null, session);
        SimpleAssert.eq(false, get(session, "buildAfterCapture"), "cancel removes deferred construction intent");
        SimpleAssert.eq(false, get(session, "calibratePending"), "cancel removes deferred calibration intent");
    }

    private static Method method(String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = HologramService.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object get(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(Object object, String name, Object value) throws ReflectiveOperationException {
        Field field = object.getClass()
            .getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

}
