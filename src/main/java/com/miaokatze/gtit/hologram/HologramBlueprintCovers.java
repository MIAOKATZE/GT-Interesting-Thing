package com.miaokatze.gtit.hologram;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.util.GTUtility;

/** Material accounting for GT's actual cover payload; never walks machine inventories or changes cover settings. */
final class HologramBlueprintCovers {

    private HologramBlueprintCovers() {}

    static List<ItemStack> costs(String tileNbt, boolean retainCovers) throws IOException {
        return retainCovers ? materials(tileNbt) : Collections.emptyList();
    }

    /** One detached item per installed side, including the NBT carried by facade cover items. */
    static List<ItemStack> materials(String tileNbt) throws IOException {
        return materials(HologramBlueprintNbt.decode(tileNbt), HologramBlueprintCovers::resolveRegisteredCover);
    }

    interface CoverResolver {

        ItemStack resolve(NBTTagCompound row, int side, int id) throws IOException;
    }

    /** Payload parsing is separate from GT registration so malformed data can be tested without a running mod. */
    static List<ItemStack> materials(NBTTagCompound tile, CoverResolver resolver) throws IOException {
        if (tile == null) return Collections.emptyList();
        for (String legacy : new String[] { "mCoverList", "mCoverIDs", "mCoverSides", "covers", "cover" }) {
            if (tile.hasKey(legacy)) throw new IOException("蓝图覆盖板格式不受支持：" + legacy);
        }
        if (!tile.hasKey("gt.covers")) return Collections.emptyList();
        if (!tile.hasKey("gt.covers", 9)) throw new IOException("蓝图覆盖板必须是列表");
        NBTTagList rows = (NBTTagList) tile.getTag("gt.covers");
        if (rows.tagCount() > 6) throw new IOException("蓝图覆盖板数量超过六个面");
        if (rows.tagCount() > 0 && rows.func_150303_d() != 10) throw new IOException("蓝图覆盖板列表项必须是NBT对象");
        List<ItemStack> materials = new ArrayList<>();
        int sides = 0;
        for (int index = 0; index < rows.tagCount(); index++) {
            NBTTagCompound row = rows.getCompoundTagAt(index);
            if (!row.hasKey("s", 1)) throw new IOException("蓝图覆盖板缺少有效面：" + index);
            int side = row.getByte("s");
            if (side < 0 || side >= 6 || (sides & (1 << side)) != 0) throw new IOException("蓝图覆盖板面无效或重复：" + side);
            sides |= 1 << side;
            if (!row.hasKey("id", 3) || row.getInteger("id") == 0) throw new IOException("蓝图覆盖板缺少有效ID：面 " + side);
            ItemStack material = resolver.resolve((NBTTagCompound) row.copy(), side, row.getInteger("id"));
            if (material == null || material.getItem() == null || material.stackSize <= 0)
                throw new IOException("蓝图覆盖板材料无法解析：面 " + side);
            ItemStack detached = material.copy();
            detached.stackSize = 1;
            materials.add(detached);
        }
        return materials;
    }

    private static ItemStack resolveRegisteredCover(NBTTagCompound row, int side, int id) throws IOException {
        ItemStack identity = GTUtility.intToStack(id);
        if (identity == null || identity.getItem() == null) throw new IOException("蓝图覆盖板ID不存在：" + id);
        try {
            // All supported 2.9 beta/RC baselines use these methods. Reflection avoids linking their Cover classes.
            Class<?> registry = Class.forName("gregtech.api.covers.CoverRegistry");
            Method isCover = registry.getMethod("isCover", ItemStack.class);
            if (!Boolean.TRUE.equals(isCover.invoke(null, identity))) throw new IOException("蓝图覆盖板尚未注册：" + id);
            Class<?> coverable = Class.forName("gregtech.api.interfaces.tileentity.ICoverable");
            Object cover = registry
                .getMethod("buildCoverFromNbt", NBTTagCompound.class, ForgeDirection.class, coverable)
                .invoke(null, row, ForgeDirection.getOrientation(side), null);
            Class<?> coverType = Class.forName("gregtech.common.covers.Cover");
            if (cover == null || !Boolean.TRUE.equals(
                coverType.getMethod("isValid")
                    .invoke(cover)))
                throw new IOException("蓝图覆盖板NBT无效：" + id);
            ItemStack material = (ItemStack) coverType.getMethod("asItemStack")
                .invoke(cover);
            if (material == null || material.getItem() == null
                || material.stackSize <= 0
                || GTUtility.stackToInt(material) != id
                || !Boolean.TRUE.equals(isCover.invoke(null, material))) throw new IOException("蓝图覆盖板材料与ID不匹配：" + id);
            return material;
        } catch (InvocationTargetException failure) {
            throw new IOException("蓝图覆盖板解析失败：" + id, failure.getCause());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            throw new IOException("当前GT无法解析蓝图覆盖板：" + id, failure);
        }
    }
}
