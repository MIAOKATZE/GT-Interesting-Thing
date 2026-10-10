package com.miaokatze.gtit.hologram;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.structure.IStructureElement;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTStructureUtility;
import gregtech.common.blocks.BlockFrameBox;

/** Only the reviewed GT material frame factory grants authority; tinted hints do not encode metadata. */
final class HologramFrameSupport {

    private HologramFrameSupport() {}

    static Materials material(IStructureElement<?> element) {
        for (IStructureElement<?> part : HologramElementCatalog.parts(element)) {
            Method method = part.getClass()
                .getEnclosingMethod();
            if (method == null || method.getDeclaringClass() != GTStructureUtility.class
                || !"ofFrame".equals(method.getName())) continue;
            try {
                Field field = part.getClass()
                    .getDeclaredField("val$aFrameMaterial");
                if (!field.isSynthetic() || field.getType() != Materials.class) continue;
                field.setAccessible(true);
                return (Materials) field.get(part);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        }
        return null;
    }

    static ItemStack target(IStructureElement<?> element) {
        Materials material = material(element);
        if (material == null) return null;
        ItemStack stack = GTOreDictUnificator.get(OrePrefixes.frameGt, material, 1);
        return stack != null && Block.getBlockFromItem(stack.getItem()) instanceof BlockFrameBox
            && !Block.getBlockFromItem(stack.getItem())
                .hasTileEntity(stack.getItemDamage()) ? stack.copy() : null;
    }

    static boolean acceptsCovered(IStructureElement<?> element, Block block, int meta) {
        return accepts(element, block, meta & ~BlockFrameBox.MTE_BIT);
    }

    static boolean accepts(IStructureElement<?> element, Block block, int meta) {
        Materials material = material(element);
        return material != null && block instanceof BlockFrameBox
            && !block.hasTileEntity(meta)
            && BlockFrameBox.getMaterial(meta) == material;
    }
}
