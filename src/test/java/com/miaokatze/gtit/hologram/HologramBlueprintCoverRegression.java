package com.miaokatze.gtit.hologram;

import java.io.IOException;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;

/** Independent coverage of cover accounting, malformed payload rejection and facade material NBT. */
public final class HologramBlueprintCoverRegression {

    private HologramBlueprintCoverRegression() {}

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(HologramBlueprintCoverRegression.class, args)) return;
        run();
        System.out.println("Hologram blueprint cover regressions passed");
    }

    public static void run() {
        try {
            retainedCoverCosts();
            invalidPayloads();
            togglesAndInventory();
        } catch (Exception failure) {
            throw new AssertionError("blueprint cover regression", failure);
        }
    }

    private static void retainedCoverCosts() throws Exception {
        NBTTagCompound first = row(0, 41);
        NBTTagCompound second = row(5, 41);
        second.setInteger("coverData", 987);
        NBTTagCompound tile = tile(first, second);
        ItemStack facade = new ItemStack(Items.feather, 37, 8);
        NBTTagCompound appearance = new NBTTagCompound();
        appearance.setString("fixtureAppearance", "stone_variant");
        facade.setTagCompound(appearance);
        final int[] resolved = { 0 };
        List<ItemStack> costs = HologramBlueprintCovers.materials(tile, (payload, side, id) -> {
            SimpleAssert.eq(41, id, "resolver receives actual cover identity");
            if (side == 5)
                SimpleAssert.eq(987, payload.getInteger("coverData"), "cover settings reach material resolver");
            payload.setInteger("coverData", -1);
            resolved[0]++;
            return facade;
        });
        SimpleAssert.eq(2, resolved[0], "each installed side is resolved independently");
        SimpleAssert.eq(2, costs.size(), "same cover on two sides costs two items");
        SimpleAssert.eq(1, costs.get(0).stackSize, "one item is consumed per installed side");
        SimpleAssert.eq(
            8,
            costs.get(0)
                .getItemDamage(),
            "material damage is preserved");
        SimpleAssert.eq(
            "stone_variant",
            costs.get(0)
                .getTagCompound()
                .getString("fixtureAppearance"),
            "facade item NBT stays part of material identity");
        costs.get(0)
            .getTagCompound()
            .setString("fixtureAppearance", "changed");
        SimpleAssert.eq(
            "stone_variant",
            costs.get(1)
                .getTagCompound()
                .getString("fixtureAppearance"),
            "duplicate material entries have detached tags");
        SimpleAssert.eq(37, facade.stackSize, "resolver-owned material count is never modified");
        SimpleAssert.eq(
            "stone_variant",
            facade.getTagCompound()
                .getString("fixtureAppearance"),
            "resolver-owned material tags are never modified");
        SimpleAssert.eq(
            987,
            tile.getTagList("gt.covers", 10)
                .getCompoundTagAt(1)
                .getInteger("coverData"),
            "material inspection preserves placement cover NBT");
    }

    private static void invalidPayloads() throws Exception {
        NBTTagCompound missingId = row(0, 41);
        missingId.removeTag("id");
        reject(tile(missingId));
        reject(tile(row(0, 0)));
        NBTTagCompound wrongIdType = row(0, 41);
        wrongIdType.setString("id", "41");
        reject(tile(wrongIdType));
        NBTTagCompound missingSide = row(0, 41);
        missingSide.removeTag("s");
        reject(tile(missingSide));
        reject(tile(row(-1, 41)));
        reject(tile(row(6, 41)));
        reject(tile(row(2, 41), row(2, 42)));
        reject(tile(row(0, 41), row(1, 41), row(2, 41), row(3, 41), row(4, 41), row(5, 41), row(0, 41)));
        NBTTagCompound wrongContainer = new NBTTagCompound();
        wrongContainer.setString("gt.covers", "not a cover list");
        reject(wrongContainer);
        NBTTagCompound wrongEntry = new NBTTagCompound();
        NBTTagList strings = new NBTTagList();
        strings.appendTag(new NBTTagString("not a cover object"));
        wrongEntry.setTag("gt.covers", strings);
        reject(wrongEntry);
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setIntArray("mCoverIDs", new int[] { 41, 0, 0, 0, 0, 0 });
        reject(legacy);
        expectIo(() -> HologramBlueprintCovers.materials(tile(row(0, 41)), (row, side, id) -> null));
        expectIo(
            () -> HologramBlueprintCovers
                .materials(tile(row(0, 41)), (row, side, id) -> { throw new IOException("cover is not registered"); }));
        // The real decoder must reject an absent registry item before any cover factory can silently substitute none.
        expectIo(() -> HologramBlueprintCovers.materials(HologramBlueprintNbt.encodeCaptured(tile(row(0, 65535)))));
    }

    private static void togglesAndInventory() throws Exception {
        SimpleAssert.eq(
            0,
            HologramBlueprintCovers.costs("invalid disabled payload", false)
                .size(),
            "disabled cover retention requires no material or payload decode");
        SimpleAssert.eq(
            0,
            HologramBlueprintCovers.materials((String) null)
                .size(),
            "absent payload has no cover costs");
        NBTTagCompound tile = new NBTTagCompound();
        NBTTagList inventory = new NBTTagList();
        inventory.appendTag(tile(row(0, 41)));
        tile.setTag("Inventory", inventory);
        tile.setInteger("Count", 1000);
        SimpleAssert.eq(
            0,
            HologramBlueprintCovers
                .materials(
                    tile,
                    (row, side, id) -> {
                        throw new AssertionError("inventory contents must not be examined for cover costs");
                    })
                .size(),
            "only tile-level cover payload is material");
    }

    private static NBTTagCompound row(int side, int id) {
        NBTTagCompound row = new NBTTagCompound();
        row.setByte("s", (byte) side);
        row.setInteger("id", id);
        return row;
    }

    private static NBTTagCompound tile(NBTTagCompound... rows) {
        NBTTagCompound tile = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound row : rows) list.appendTag(row);
        tile.setTag("gt.covers", list);
        return tile;
    }

    private static void reject(NBTTagCompound tile) throws Exception {
        expectIo(
            () -> HologramBlueprintCovers.materials(tile, (row, side, id) -> { return new ItemStack(Items.feather); }));
    }

    private interface IoAction {

        void run() throws Exception;
    }

    private static void expectIo(IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected invalid cover payload to be rejected");
        } catch (IOException expected) {
            // Required rejection before placement or inventory consumption.
        }
    }
}
