package com.miaokatze.gtit.common.machine.v2;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementChain;
import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.blocks.BlockNekoVendingCasing;
import com.miaokatze.gtit.recipe.GTITRecipes;
import com.miaokatze.gtit.register.BlockRegistrar;
import com.miaokatze.gtit.register.TextureManager;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

/** Native ItemCasings/recipe and compiled shape checks; the full GT block constructor is not exercised. */
public final class NekoTvStructureTest {

    private static Block originalCasing;

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
        Field modController = cpw.mods.fml.common.Loader.class.getDeclaredField("modController");
        modController.setAccessible(true);
        modController.set(
            cpw.mods.fml.common.Loader.instance(),
            new cpw.mods.fml.common.LoadController(cpw.mods.fml.common.Loader.instance()));
        cpw.mods.fml.server.FMLServerHandler.instance();
        Thread.currentThread()
            .setName("Server thread");
        originalCasing = casingFixture(
            Class.forName("com.cubefury.vendingmachine.blocks.VendingMachineCasing"),
            "vm_recipe_fixture");
        Class.forName("com.cubefury.vendingmachine.blocks.VendingMachineBlocks")
            .getField("casingBlock")
            .set(null, originalCasing);
        BlockRegistrar.nekoVendingCasing = (BlockNekoVendingCasing) casingFixture(
            BlockNekoVendingCasing.class,
            "neko_vending_casing");
        GTITItemList.NekoVendingCasing.set(new ItemStack(BlockRegistrar.nekoVendingCasing, 1, 0));
        check(GTITItemList.NekoVendingCasing.getItem() != null, "new casing has its registered native ItemBlock");
        check(
            "neko_vending_casing.0".equals(
                GTITItemList.NekoVendingCasing.get(1)
                    .getUnlocalizedName()),
            "casing ItemBlock uses its own localization key");
        casingRecipe();
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
        IStructureElement<MTENekoVendingMachineV2>[] candidates = ((IStructureElementChain<MTENekoVendingMachineV2>) casing)
            .fallbacks();
        check(candidates.length == 3, "ME hatch plus new and legacy casings are the only candidates");
        ItemStack preferred = candidates[1].getBlocksToPlace(null, null, 0, 0, 0, null, null)
            .getStacks()
            .iterator()
            .next();
        ItemStack legacy = candidates[2].getBlocksToPlace(null, null, 0, 0, 0, null, null)
            .getStacks()
            .iterator()
            .next();
        check(
            preferred.getItem() == GTITItemList.NekoVendingCasing.getItem() && preferred.getItemDamage() == 0,
            "new wooden casing is the preferred casing candidate");
        check(
            legacy.getItem() == net.minecraft.item.Item.getItemFromBlock(originalCasing) && legacy.getItemDamage() == 0,
            "the original casing remains available for existing worlds");
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
            "NekoTvStructureTest PASS: native ItemCasings/recipe, 39-cell chain, nineteen front overlays and six facings");
    }

    private static Block casingFixture(Class<?> type, String name) throws Exception {
        // Keep the real block and ItemCasings classes, without loading GT's unrelated CTM runtime dependency.
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Block block = (Block) unsafe.getMethod("allocateInstance", Class.class)
            .invoke(singleton.get(null), type);
        Field delegate = Block.class.getField("delegate");
        delegate.setAccessible(true);
        delegate.set(block, new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(block, Block.class));
        Field unlocalizedName = gregtech.api.items.GTGenericBlock.class.getDeclaredField("mUnlocalizedName");
        unlocalizedName.setAccessible(true);
        unlocalizedName.set(block, name);
        cpw.mods.fml.common.registry.GameRegistry.registerBlock(block, gregtech.common.blocks.ItemCasings.class, name);
        return block;
    }

    private static void casingRecipe() throws Exception {
        List<?> recipes = CraftingManager.getInstance()
            .getRecipeList();
        int previousSize = recipes.size();
        try {
            Method add = GTITRecipes.class.getDeclaredMethod("addNekoVendingCasingRecipe");
            add.setAccessible(true);
            add.invoke(null);
            check(recipes.size() == previousSize + 1, "exactly one casing recipe is registered");
            IRecipe recipe = (IRecipe) recipes.get(previousSize);
            check(recipe instanceof ShapelessRecipes, "casing conversion uses the native shapeless recipe");
            InventoryCrafting grid = new InventoryCrafting(new Container() {

                @Override
                public boolean canInteractWith(EntityPlayer player) {
                    return true;
                }
            }, 3, 3);
            for (int slot = 0; slot < 9; slot++) {
                grid.setInventorySlotContents(slot, new ItemStack(originalCasing, 1, 0));
                check(recipe.matches(grid, null), "one original casing matches in every crafting slot");
                ItemStack output = recipe.getCraftingResult(grid);
                check(
                    output.getItem() == GTITItemList.NekoVendingCasing.getItem() && output.stackSize == 1
                        && output.getItemDamage() == 0,
                    "conversion returns exactly one new casing");
                grid.setInventorySlotContents(slot, null);
            }
            grid.setInventorySlotContents(0, new ItemStack(originalCasing, 1, 1));
            check(!recipe.matches(grid, null), "incorrect original casing metadata is rejected");
            grid.setInventorySlotContents(0, new ItemStack(originalCasing, 1, 0));
            grid.setInventorySlotContents(1, new ItemStack(Items.stick));
            check(!recipe.matches(grid, null), "extra materials are rejected");
            grid.setInventorySlotContents(0, GTITItemList.NekoVendingCasing.get(1));
            grid.setInventorySlotContents(1, null);
            check(!recipe.matches(grid, null), "the casing conversion is not reversible");
        } finally {
            recipes.subList(previousSize, recipes.size())
                .clear();
        }
    }
}
