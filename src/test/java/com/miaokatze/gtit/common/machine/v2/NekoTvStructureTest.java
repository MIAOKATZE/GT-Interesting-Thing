package com.miaokatze.gtit.common.machine.v2;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.miaokatze.gtit.register.TextureManager;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

/** Native compiled shape and facing geometry checks; no running world or visual acceptance claim. */
public final class NekoTvStructureTest {

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static int[] coordinates(String name) throws Exception {
        Field field = MTENekoVendingMachineV2.class.getDeclaredField(name);
        field.setAccessible(true);
        return (int[]) field.get(null);
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(NekoTvStructureTest.class, args)) return;
        Field namedMods = cpw.mods.fml.common.Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        namedMods.set(cpw.mods.fml.common.Loader.instance(), new java.util.HashMap<>());
        cpw.mods.fml.server.FMLServerHandler.instance();
        Thread.currentThread()
            .setName("Server thread");
        // The geometry suite needs the real casing type, but does not register a mod block with Forge.
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object casingBlock = unsafe.getMethod("allocateInstance", Class.class)
            .invoke(singleton.get(null), Class.forName("com.cubefury.vendingmachine.blocks.VendingMachineCasing"));
        Class.forName("com.cubefury.vendingmachine.blocks.VendingMachineBlocks")
            .getField("casingBlock")
            .set(null, casingBlock);
        IStructureDefinition<MTENekoVendingMachineV2> definition = new MTENekoVendingMachineV2("tv_structure_test")
            .getStructureDefinition();
        Set<String> cells = new HashSet<>();
        int a = 0, b = 0, c = 0;
        IStructureElement<MTENekoVendingMachineV2> casing = null;
        for (IStructureElement<MTENekoVendingMachineV2> element : definition.getStructureFor("main")) {
            if (element.isNavigating()) {
                a = (element.resetA() ? 0 : a) + element.getStepA();
                b = (element.resetB() ? 0 : b) + element.getStepB();
                c = (element.resetC() ? 0 : c) + element.getStepC();
            } else {
                if (casing == null) casing = element;
                check(element == casing, "all casing cells share the existing casing/ME hatch chain");
                check(cells.add(a + "," + b + "," + c), "no duplicate casing coordinate");
                a++;
            }
        }
        check(cells.size() == 39, "exactly 39 replaceable casing cells");
        for (int depth = 0; depth < 2; depth++) for (int row = 0; row < 4; row++) for (int col = 0; col < 5; col++) {
            check(definition.isContainedInStructure("main", col, row, depth), "solid 5x4x2 footprint");
            boolean controller = col == 2 && row == 3 && depth == 0;
            check(cells.contains(col + "," + row + "," + depth) != controller, "sole controller exclusion");
        }
        int[] screenA = coordinates("NEKO_VM_X");
        int[] screenB = coordinates("NEKO_VM_Y");
        check(screenA.length == 6 && screenB.length == 6, "six screen faces");
        check(
            TextureManager.NEKOTV_OVERLAY.length == 6 && TextureManager.NEKOTV_OVERLAY_ACTIVE.length == 6,
            "static and animated tiles use the same six-cell order");
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            ExtendedFacing facing = ExtendedFacing.of(direction);
            Set<String> screenWorld = new HashSet<>();
            for (int i = 0; i < 6; i++) {
                int col = i % 3 + 1;
                int row = i / 3 + 1;
                check(screenA[i] == col - 2 && screenB[i] == row - 3, "row-major screen ABC position");
                check(cells.contains(col + "," + row + ",0"), "screen is on replaceable front casing");
                int[] world = new int[3];
                facing.getWorldOffset(new int[] { screenA[i], screenB[i], 0 }, world);
                check(screenWorld.add(world[0] + "," + world[1] + "," + world[2]), "six distinct screen faces");
                if (direction.offsetY == 0) check(world[1] == -screenB[i], "screen sits above controller");
            }
        }
        System.out
            .println("NekoTvStructureTest PASS: native 39-cell chain, controller, six screen faces and six facings");
    }
}
