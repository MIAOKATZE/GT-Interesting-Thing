package com.miaokatze.gtit.common.loot;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.TestRunner;

/** 使用真实 Forge 池替换和原版箱子抽取，复现 TooMuchLoot 的 ServerStarting 覆盖。 */
public final class LootRegistrarTest {

    private static final String[] CATEGORIES = { ChestGenHooks.DUNGEON_CHEST, ChestGenHooks.VILLAGE_BLACKSMITH,
        ChestGenHooks.MINESHAFT_CORRIDOR, ChestGenHooks.PYRAMID_DESERT_CHEST, ChestGenHooks.PYRAMID_JUNGLE_CHEST,
        ChestGenHooks.STRONGHOLD_CORRIDOR, ChestGenHooks.STRONGHOLD_CROSSING };

    private LootRegistrarTest() {}

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(LootRegistrarTest.class, args)) return;
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("seven original categories and counts", LootRegistrarTest::originalCategories);
        cases.put("repeat registration does not increase weight", LootRegistrarTest::repeatRegistration);
        cases.put("replacement pools recover coin and seven rings", LootRegistrarTest::replacementPools);
        cases.put("existing configured quantities and weight survive", LootRegistrarTest::configuredEntry);
        cases.put("metadata and NBT variants remain distinct", LootRegistrarTest::distinctVariants);
        cases.put("same pool can recover a removed entry", LootRegistrarTest::removedEntry);
        cases.put("real chest generation keeps inclusive 2 to 8 range", LootRegistrarTest::generateChest);
        TestRunner.run(LootRegistrarTest.class, cases);
    }

    private static void originalCategories() {
        resetPools();
        ItemStack coin = new ItemStack(new Item());
        LootRegistrar.addChestLootToAll(loot(coin));
        for (String category : CATEGORIES) {
            ChestGenHooks pool = ChestGenHooks.getInfo(category);
            require(entries(pool).length == 1, category + " must contain one coin entry");
            require(pool.getMin() == 4 && pool.getMax() == 9, "configured chest roll counts must survive");
        }
        for (WeightedRandomChestContent entry : entries(ChestGenHooks.getInfo(ChestGenHooks.STRONGHOLD_LIBRARY))) {
            require(!entry.theItemId.isItemEqual(coin), "must not expand into a new chest category");
        }
    }

    private static void repeatRegistration() {
        resetPools();
        ItemStack coin = new ItemStack(new Item());
        for (int i = 0; i < 5; i++) LootRegistrar.addChestLootToAll(loot(coin.copy()));
        for (String category : CATEGORIES) {
            WeightedRandomChestContent[] items = entries(ChestGenHooks.getInfo(category));
            require(items.length == 1 && items[0].itemWeight == 3, "repeat registration increased weight");
        }
    }

    private static void replacementPools() {
        resetPools();
        ItemStack[] items = new ItemStack[8];
        for (int i = 0; i < items.length; i++) {
            items[i] = new ItemStack(new Item());
            LootRegistrar.addChestLootToAll(entry(items[i], i));
        }
        // 同 TML 的 OVERRIDE：整个 map 值换为新池，不能靠已注册 boolean 或旧池引用去重。
        resetPools();
        for (int i = 0; i < items.length; i++) LootRegistrar.addChestLootToAll(entry(items[i], i));
        for (String category : CATEGORIES) {
            WeightedRandomChestContent[] actual = entries(ChestGenHooks.getInfo(category));
            require(actual.length == 8, category + " lost a coin or ring after replacement");
            require(
                actual[0].theMinimumChanceToGenerateItem == 2 && actual[0].theMaximumChanceToGenerateItem == 8
                    && actual[0].itemWeight == 3,
                "coin rule changed");
            for (int i = 1; i < actual.length; i++) {
                require(
                    actual[i].theMinimumChanceToGenerateItem == 1 && actual[i].theMaximumChanceToGenerateItem == 1
                        && actual[i].itemWeight == 1,
                    "ring rule changed");
            }
        }
    }

    private static void configuredEntry() {
        resetPools();
        ItemStack coin = new ItemStack(new Item());
        WeightedRandomChestContent configured = new WeightedRandomChestContent(coin, 11, 19, 27);
        ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST)
            .addItem(configured);
        LootRegistrar.addChestLootToAll(loot(coin));
        WeightedRandomChestContent[] actual = entries(ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST));
        require(actual.length == 1 && actual[0] == configured, "existing pack entry must retain its rule");
    }

    private static void distinctVariants() {
        resetPools();
        Item item = new Item();
        ItemStack plain = new ItemStack(item, 1, 0);
        ItemStack metadata = new ItemStack(item, 1, 1);
        ItemStack tagged = plain.copy();
        tagged.setTagCompound(new NBTTagCompound());
        tagged.getTagCompound()
            .setString("variant", "custom");
        LootRegistrar.addChestLootToAll(loot(metadata));
        LootRegistrar.addChestLootToAll(loot(tagged));
        LootRegistrar.addChestLootToAll(loot(plain));
        LootRegistrar.addChestLootToAll(loot(tagged.copy()));
        require(entries(ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST)).length == 3, "variant identity mismatch");
    }

    private static void removedEntry() {
        resetPools();
        ItemStack coin = new ItemStack(new Item());
        LootRegistrar.addChestLootToAll(loot(coin));
        ChestGenHooks pool = ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST);
        pool.removeItem(coin);
        require(entries(pool).length == 0, "fixture removal failed");
        LootRegistrar.addChestLootToAll(loot(coin));
        require(entries(pool).length == 1, "in-place removal must also recover");
    }

    private static void generateChest() {
        resetPools();
        ItemStack coin = new ItemStack(new Item());
        LootRegistrar.addChestLootToAll(loot(coin));
        WeightedRandomChestContent[] items = entries(ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST));
        Random random = new Random(20261022L);
        boolean sawMin = false;
        boolean sawMax = false;
        for (int i = 0; i < 200; i++) {
            TileEntityChest chest = new TileEntityChest();
            WeightedRandomChestContent.generateChestContents(random, items, chest, 1);
            int count = 0;
            for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
                ItemStack stack = chest.getStackInSlot(slot);
                if (stack != null) {
                    require(stack.isItemEqual(coin), "unexpected generated item");
                    count += stack.stackSize;
                }
            }
            require(count >= 2 && count <= 8, "generated quantity outside 2..8: " + count);
            sawMin |= count == 2;
            sawMax |= count == 8;
        }
        require(sawMin && sawMax, "inclusive quantity bounds not reached");
    }

    private static WeightedRandomChestContent entry(ItemStack item, int index) {
        return index == 0 ? loot(item) : new WeightedRandomChestContent(item, 1, 1, 1);
    }

    private static WeightedRandomChestContent loot(ItemStack stack) {
        return new WeightedRandomChestContent(stack, 2, 8, 3);
    }

    private static WeightedRandomChestContent[] entries(ChestGenHooks pool) {
        return pool.getItems(new Random(0));
    }

    @SuppressWarnings("unchecked")
    private static void resetPools() {
        try {
            Field field = ChestGenHooks.class.getDeclaredField("chestInfo");
            field.setAccessible(true);
            Map<String, ChestGenHooks> pools = (Map<String, ChestGenHooks>) field.get(null);
            for (String category : CATEGORIES) {
                pools.put(category, new ChestGenHooks(category, new WeightedRandomChestContent[0], 4, 9));
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("cannot replace Forge chest pools", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
