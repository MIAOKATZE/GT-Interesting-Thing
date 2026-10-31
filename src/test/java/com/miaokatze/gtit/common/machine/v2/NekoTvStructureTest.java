package com.miaokatze.gtit.common.machine.v2;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementChain;
import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtit.common.blocks.BlockNekoVendingCasing;
import com.miaokatze.gtit.recipe.GTITRecipes;
import com.miaokatze.gtit.register.BlockRegistrar;
import com.miaokatze.gtit.register.TextureManager;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

/** Native ItemCasings/recipe and compiled shape checks; the full GT block constructor is not exercised. */
public final class NekoTvStructureTest {

    private static Block originalCasing;
    private static net.minecraft.item.Item uplinkItem;

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
        uplinkRecipe();
        connectedCasing();
        uplinkClone();
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
        check(candidates.length == 2, "dedicated casing plus optional dedicated ME hatch are the only candidates");
        ItemStack preferred = candidates[0].getBlocksToPlace(null, null, 0, 0, 0, null, null)
            .getStacks()
            .iterator()
            .next();
        check(
            preferred.getItem() == GTITItemList.NekoVendingCasing.getItem() && preferred.getItemDamage() == 0,
            "wooden casing precedes the hatch for default preview, hint and construction");
        check(
            !candidates[0].getBlocksToPlace(null, null, 0, 0, 0, null, null)
                .getPredicate()
                .test(new ItemStack(originalCasing, 1, 0)),
            "the original VM casing is not a construction candidate");
        check(
            candidates[1].getBlocksToPlace(null, null, 0, 0, 0, null, null)
                .getPredicate()
                .test(GTITItemList.NekoVendingUplinkHatch.get(1)),
            "only the dedicated ME hatch matches the optional hatch predicate");
        check(
            !candidates[1].getBlocksToPlace(null, null, 0, 0, 0, null, null)
                .getPredicate()
                .test(new ItemStack(uplinkItem, 1, 2742)),
            "the original VM hatch cannot substitute for the dedicated hatch");
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

    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return unsafe.getMethod("allocateInstance", Class.class)
            .invoke(singleton.get(null), type);
    }

    private static TileEntity tileFixture(Object machine) throws Exception {
        Class<?> type = Class.forName("gregtech.api.metatileentity.BaseMetaTileEntity");
        TileEntity tile = (TileEntity) allocate(type);
        Field coordinates = gregtech.api.metatileentity.BaseTileEntity.class.getDeclaredField("mReturnedCoordinates");
        coordinates.setAccessible(true);
        coordinates.set(tile, new net.minecraft.util.ChunkCoordinates());
        gregtech.api.interfaces.tileentity.IGregTechTileEntity base = (gregtech.api.interfaces.tileentity.IGregTechTileEntity) tile;
        ((gregtech.api.interfaces.metatileentity.IMetaTileEntity) machine).setBaseMetaTileEntity(base);
        check(base.getMetaTileEntity() == machine, "fixture obeys GT's bidirectional MTE ownership and validity");
        return tile;
    }

    private static void connectedCasing() throws Exception {
        Map<String, Block> blocks = new HashMap<>();
        Map<String, TileEntity> tiles = new HashMap<>();
        IBlockAccess world = (IBlockAccess) Proxy.newProxyInstance(
            IBlockAccess.class.getClassLoader(),
            new Class<?>[] { IBlockAccess.class },
            (proxy, method, args) -> {
                String key = args[0] + "," + args[1] + "," + args[2];
                if (method.getName()
                    .equals("getBlock")) return blocks.getOrDefault(key, net.minecraft.init.Blocks.air);
                if (method.getName()
                    .equals("getTileEntity")) return tiles.get(key);
                if (method.getName()
                    .equals("getBlockMetadata")) return 0;
                throw new AssertionError("unexpected block query " + method.getName());
            });
        Method maskMethod = BlockNekoVendingCasing.class
            .getDeclaredMethod("connectionMask", IBlockAccess.class, int.class, int.class, int.class, int.class);
        maskMethod.setAccessible(true);
        ForgeDirection[] right = { ForgeDirection.EAST, ForgeDirection.EAST, ForgeDirection.WEST, ForgeDirection.EAST,
            ForgeDirection.SOUTH, ForgeDirection.NORTH };
        for (int face = 0; face < 6; face++) {
            ForgeDirection up = face < 2 ? ForgeDirection.NORTH : ForgeDirection.UP;
            ForgeDirection[] edges = { up, right[face], up.getOpposite(), right[face].getOpposite() };
            for (int expected = 0; expected < 16; expected++) {
                blocks.clear();
                for (int edge = 0; edge < 4; edge++) if ((expected & (1 << edge)) != 0) {
                    ForgeDirection d = edges[edge];
                    blocks.put(d.offsetX + "," + d.offsetY + "," + d.offsetZ, BlockRegistrar.nekoVendingCasing);
                }
                check((int) maskMethod.invoke(null, world, 0, 0, 0, face) == expected, "all six CTM face masks");
            }
        }
        blocks.clear();
        blocks.put("1,0,0", originalCasing);
        check((int) maskMethod.invoke(null, world, 0, 0, 0, 3) == 0, "old VM casings do not connect");
        for (String type : new String[] { MTENekoVendingMachineV2.class.getName(),
            "com.miaokatze.gtit.common.machine.v2.MTENekoVendingUplinkHatch" }) {
            tiles.put("1,0,0", tileFixture(allocate(Class.forName(type))));
            check(
                (int) maskMethod.invoke(null, world, 0, 0, 0, 3) == 2,
                "dedicated controller and hatch join the wood");
        }
        tiles.put(
            "1,0,0",
            tileFixture(allocate(Class.forName("com.cubefury.vendingmachine.blocks.MTEVendingUplinkHatch"))));
        check(
            (int) maskMethod.invoke(null, world, 0, 0, 0, 3) == 0,
            "original and unrelated GT hatches do not connect");
    }

    private static void uplinkClone() throws Exception {
        Class<?> type = Class.forName("com.miaokatze.gtit.common.machine.v2.MTENekoVendingUplinkHatch");
        Object prototype = allocate(type);
        Field name = gregtech.api.metatileentity.CommonMetaTileEntity.class.getDeclaredField("mName");
        name.setAccessible(true);
        name.set(prototype, "neko_uplink_fixture");
        Method clone = type
            .getDeclaredMethod("newMetaEntity", gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        gregtech.api.interfaces.metatileentity.IMetaTileEntity first = (gregtech.api.interfaces.metatileentity.IMetaTileEntity) clone
            .invoke(prototype, new Object[] { null });
        gregtech.api.interfaces.metatileentity.IMetaTileEntity second = (gregtech.api.interfaces.metatileentity.IMetaTileEntity) clone
            .invoke(prototype, new Object[] { null });
        check(first.getClass() == type && second.getClass() == type, "cloning preserves the dedicated MTE type");
        Class<?> original = type.getSuperclass();
        for (Method method : type.getDeclaredMethods()) {
            check(
                !java.util.Arrays
                    .asList(
                        "isActive",
                        "getProxy",
                        "getTexturesActive",
                        "getTexturesInactive",
                        "saveNBTData",
                        "loadNBTData")
                    .contains(method.getName()),
                "original ME behavior, overlays and NBT remain inherited");
        }
        int casingIndex = BlockRegistrar.nekoVendingCasing.getTextureIndex(0);
        gregtech.api.util.GTUtility.addTexturePage((byte) (casingIndex >> 7));
        gregtech.api.interfaces.ITexture wood = gregtech.api.render.TextureFactory
            .of(TextureManager.NEKO_VENDING_CASING_SIDE);
        gregtech.api.enums.Textures.BlockIcons.setCasingTextureForId(casingIndex, wood);
        check(
            type.getDeclaredMethod("getCasingTexture")
                .invoke(first) == wood,
            "dedicated uplink uses the wooden casing slot");
        NBTTagCompound stored = new NBTTagCompound();
        NBTTagList pending = new NBTTagList();
        // Non-empty AEItemStack decoding requires the full AE registry lifecycle, outside this isolated suite.
        stored.setTag("pendingInject", pending);
        first.loadNBTData(stored);
        NBTTagCompound saved = new NBTTagCompound();
        first.saveNBTData(saved);
        check(
            saved.getTagList("pendingInject", 10)
                .tagCount() == 0 && saved.hasKey("pendingInject", 9),
            "original empty pending injection NBT format is preserved");
        NBTTagCompound empty = new NBTTagCompound();
        second.saveNBTData(empty);
        check(
            empty.getTagList("pendingInject", 10)
                .tagCount() == 0,
            "fresh clones retain an empty pending injection queue");
        Field queue = original.getDeclaredField("pendingItemInject");
        queue.setAccessible(true);
        check(queue.get(first) != queue.get(second), "clones do not share pending item ownership");
        uplinkPolicy(first, second, allocate(original));
    }

    private static gregtech.api.interfaces.tileentity.IGregTechTileEntity hatchTile(Object machine, int id)
        throws Exception {
        gregtech.api.interfaces.tileentity.IGregTechTileEntity tile = (gregtech.api.interfaces.tileentity.IGregTechTileEntity) tileFixture(
            machine);
        tile.setMetaTileID((short) id);
        return tile;
    }

    private static void uplinkPolicy(Object first, Object second, Object original) throws Exception {
        Class<?> tileType = gregtech.api.interfaces.tileentity.IGregTechTileEntity.class;
        Method add = MTENekoVendingMachineV2.class.getDeclaredMethod("addUplinkHatch", tileType, int.class);
        add.setAccessible(true);
        final ItemStack controllerIcon = new ItemStack(Items.iron_ingot);
        MTENekoVendingMachineV2 controller = new MTENekoVendingMachineV2("uplink_policy_fixture") {

            @Override
            public ItemStack getMachineCraftingIcon() {
                return controllerIcon;
            }
        };
        tileFixture(controller);
        int id = MetaTileEntityID.NEKO_VENDING_UPLINK_HATCH.ID;
        int texture = BlockRegistrar.nekoVendingCasing.getTextureIndex(0);
        check(!(boolean) add.invoke(controller, hatchTile(original, 2742), texture), "old VM hatch ID is rejected");
        check(
            !(boolean) add.invoke(controller, hatchTile(allocate(original.getClass()), id), texture),
            "dedicated ID requires the dedicated class");
        gregtech.api.interfaces.tileentity.IGregTechTileEntity firstTile = hatchTile(first, id);
        check((boolean) add.invoke(controller, firstTile, texture), "one dedicated hatch is accepted");
        check(
            !(boolean) add.invoke(controller, hatchTile(second, id), texture),
            "a second dedicated hatch is rejected");
        gregtech.api.metatileentity.implementations.MTEHatch hatch = (gregtech.api.metatileentity.implementations.MTEHatch) first;
        check(hatch.getMachineCraftingIcon() == controllerIcon, "uplink advertises its controller's crafting icon");
        controller.clearHatches();
        check(hatch.getMachineCraftingIcon() == null, "clearing a structure removes its owned icon");
        check((boolean) add.invoke(controller, firstTile, texture), "the same hatch can bind again after clearing");
        final ItemStack otherIcon = new ItemStack(Items.gold_ingot);
        MTENekoVendingMachineV2 other = new MTENekoVendingMachineV2("other_uplink_owner") {

            @Override
            public ItemStack getMachineCraftingIcon() {
                return otherIcon;
            }
        };
        TileEntity otherTile = tileFixture(other);
        otherTile.xCoord = 1;
        check((boolean) add.invoke(other, firstTile, texture), "a shared hatch can acquire another controller owner");
        controller.clearHatches();
        check(
            hatch.getMachineCraftingIcon() == otherIcon,
            "clearing the old controller preserves the new owner's icon");
        other.onStructureCheckFinished(other.getBaseMetaTileEntity());
        check(hatch.getMachineCraftingIcon() == null, "a failed structure check clears its newly registered icon");
        other.clearHatches();
        check((boolean) add.invoke(controller, hatchTile(second, id), texture), "a replacement uplink can bind");
        controller.clearHatches();
        check(
            ((gregtech.api.metatileentity.implementations.MTEHatch) second).getMachineCraftingIcon() == null,
            "replacement hatches use the same owner cleanup lifecycle");
    }

    private static void uplinkRecipe() throws Exception {
        int id = MetaTileEntityID.NEKO_VENDING_UPLINK_HATCH.ID;
        check(id == MetaTileEntityID.NEKO_VENDING_MACHINE_V2.ID + 1, "new hatch uses the next free GTIT ID");
        uplinkItem = new gregtech.common.blocks.ItemMachines(net.minecraft.init.Blocks.iron_block);
        GTITItemList.NekoVendingUplinkHatch.set(new ItemStack(uplinkItem, 1, id));
        gregtech.api.interfaces.metatileentity.IMetaTileEntity previous = gregtech.api.GregTechAPI.METATILEENTITIES[2742];
        gregtech.api.GregTechAPI.METATILEENTITIES[2742] = new MTENekoVendingMachineV2("original_uplink_fixture") {

            @Override
            public ItemStack getStackForm(long amount) {
                return new ItemStack(uplinkItem, (int) amount, 2742);
            }
        };
        List<?> recipes = CraftingManager.getInstance()
            .getRecipeList();
        int previousSize = recipes.size();
        try {
            Method add = GTITRecipes.class.getDeclaredMethod("addNekoVendingUplinkHatchRecipe");
            add.setAccessible(true);
            add.invoke(null);
            check(recipes.size() == previousSize + 1, "one dedicated uplink conversion is registered");
            IRecipe recipe = (IRecipe) recipes.get(previousSize);
            check(recipe instanceof ShapelessRecipes, "uplink conversion is native shapeless");
            InventoryCrafting grid = new InventoryCrafting(new Container() {

                @Override
                public boolean canInteractWith(EntityPlayer player) {
                    return true;
                }
            }, 3, 3);
            for (int slot = 0; slot < 9; slot++) {
                grid.setInventorySlotContents(slot, new ItemStack(uplinkItem, 1, 2742));
                check(recipe.matches(grid, null), "one original uplink matches in every slot");
                ItemStack output = recipe.getCraftingResult(grid);
                check(output.stackSize == 1 && output.getItemDamage() == id, "one dedicated uplink is returned");
                grid.setInventorySlotContents(slot, null);
            }
            grid.setInventorySlotContents(0, GTITItemList.NekoVendingUplinkHatch.get(1));
            check(!recipe.matches(grid, null), "no reverse uplink conversion");
            grid.setInventorySlotContents(0, new ItemStack(uplinkItem, 1, 2742));
            grid.setInventorySlotContents(1, new ItemStack(Items.stick));
            check(!recipe.matches(grid, null), "uplink conversion rejects extra materials");
        } finally {
            recipes.subList(previousSize, recipes.size())
                .clear();
            gregtech.api.GregTechAPI.METATILEENTITIES[2742] = previous;
        }
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
