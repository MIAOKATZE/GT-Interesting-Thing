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
        int[] overlayA = coordinates("NEKO_VM_X");
        int[] overlayB = coordinates("NEKO_VM_Y");
        check(overlayA.length == 19 && overlayB.length == 19, "nineteen front overlay faces");
        check(
            TextureManager.NEKOTV_OVERLAY.length == 6 && TextureManager.NEKOTV_OVERLAY_ACTIVE.length == 6,
            "legacy six-cell texture API is retained");
        check(
            TextureManager.NEKOTV_FULL_OVERLAY.length == 19 && TextureManager.NEKOTV_FULL_OVERLAY_ACTIVE.length == 19,
            "static and animated full-face tiles use the same nineteen-cell order");
        Set<String> overlayCells = new HashSet<>();
        int overlayIndex = 0;
        for (int row = 0; row < 4; row++) for (int col = 0; col < 5; col++) {
            if (col == 2 && row == 3) continue;
            check(
                overlayA[overlayIndex] == col - 2 && overlayB[overlayIndex] == row - 3,
                "row-major full-face ABC position skips controller");
            check(cells.contains(col + "," + row + ",0"), "overlay is on replaceable front casing");
            check(overlayCells.add(col + "," + row + ",0"), "no duplicate overlay coordinate");
            overlayIndex++;
        }
        for (String cell : cells) {
            check(overlayCells.contains(cell) == cell.endsWith(",0"), "every front casing has exactly one overlay");
        }
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            ExtendedFacing facing = ExtendedFacing.of(direction);
            Set<String> overlayWorld = new HashSet<>();
            for (int i = 0; i < overlayA.length; i++) {
                int[] world = new int[3];
                facing.getWorldOffset(new int[] { overlayA[i], overlayB[i], 0 }, world);
                check(overlayWorld.add(world[0] + "," + world[1] + "," + world[2]), "nineteen distinct overlay faces");
                check(world[0] != 0 || world[1] != 0 || world[2] != 0, "controller has no overlay in any facing");
                check(
                    world[0] * direction.offsetX + world[1] * direction.offsetY + world[2] * direction.offsetZ == 0,
                    "overlay stays on front plane in all six facings");
                if (direction.offsetY == 0) check(world[1] == -overlayB[i], "overlay sits at or above controller");
            }
        }
        System.out.println(
            "NekoTvStructureTest PASS: native 39-cell chain, controller, nineteen front overlays and six facings");
    }
}
