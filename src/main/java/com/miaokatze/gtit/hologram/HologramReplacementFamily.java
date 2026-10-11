package com.miaokatze.gtit.hologram;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import gregtech.api.interfaces.IHeatingCoil;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.GlassTier;
import gregtech.common.blocks.BlockFrameBox;

/** Exact factory provenance plus enumerated old states. A texture or coincident block ID is never a family. */
final class HologramReplacementFamily {

    private HologramReplacementFamily() {}

    static Family resolve(IStructureElement<?> element, Block target, int targetMeta) {
        if (element == null || target == null || target.hasTileEntity(targetMeta)) return null;
        Family found = null;
        for (IStructureElement<?> part : HologramElementCatalog.parts(element)) {
            Method method = part.getClass()
                .getEnclosingMethod();
            if (method == null) continue;
            Family candidate = null;
            if (method.getDeclaringClass() == GTStructureUtility.class && "ofCoil".equals(method.getName())
                && target instanceof IHeatingCoil) {
                candidate = new Family("coil", null, true);
            } else
                if (method.getDeclaringClass() == StructureUtility.class && "ofBlocksTiered".equals(method.getName())) {
                    candidate = tiered(part);
                }
            if (candidate == null || !candidate.contains(target, targetMeta)) continue;
            if (found != null && !found.id.equals(candidate.id)) return null;
            found = candidate;
        }
        return found;
    }

    private static Family tiered(IStructureElement<?> part) {
        try {
            Field field = part.getClass()
                .getDeclaredField("val$hints");
            if (!field.isSynthetic() || !List.class.isAssignableFrom(field.getType())) return null;
            field.setAccessible(true);
            List<?> raw = (List<?>) field.get(part);
            if (raw == null || raw.isEmpty() || raw.size() > 256) return null;
            List<Pair<Block, Integer>> states = new ArrayList<>();
            boolean casings = true, glasses = true, frames = true;
            StringBuilder identity = new StringBuilder();
            for (Object value : raw) {
                if (!(value instanceof Pair)) return null;
                Pair<?, ?> pair = (Pair<?, ?>) value;
                if (!(pair.getLeft() instanceof Block) || !(pair.getRight() instanceof Integer)) return null;
                Block block = (Block) pair.getLeft();
                int meta = (Integer) pair.getRight();
                if (meta < 0 || block.hasTileEntity(meta)) return null;
                if (block instanceof BlockFrameBox) {
                    if (meta > 0xFFF) return null;
                } else if (meta > 15) return null;
                frames &= block instanceof BlockFrameBox;
                casings &= block.getClass()
                    .getName()
                    .startsWith("gregtech.common.blocks.BlockCasings");
                if (block instanceof BlockFrameBox) glasses = false;
                else glasses &= GlassTier.getGlassBlockTier(block, meta) != null;
                states.add(Pair.of(block, meta));
                identity.append(Block.blockRegistry.getNameForObject(block))
                    .append('@')
                    .append(meta)
                    .append(';');
            }
            if (!casings && !glasses && !frames) return null;
            return new Family((frames ? "frame:" : glasses ? "glass:" : "tiered:") + identity, states, false);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static final class Family {

        final String id;
        private final List<Pair<Block, Integer>> states;
        private final boolean coil;

        Family(String id, List<Pair<Block, Integer>> states, boolean coil) {
            this.id = id;
            this.states = states;
            this.coil = coil;
        }

        boolean contains(Block block, int meta) {
            if (block == null || block.hasTileEntity(meta)) return false;
            if (coil) return block instanceof IHeatingCoil
                && ((IHeatingCoil) block).getCoilHeat(meta) != gregtech.api.enums.HeatingCoilLevel.None;
            for (Pair<Block, Integer> state : states)
                if (state.getLeft() == block && state.getRight() == meta) return true;
            return false;
        }

        boolean canTarget(ItemStack stack) {
            if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
            ItemBlock item = (ItemBlock) stack.getItem();
            return contains(Block.getBlockFromItem(item), item.getMetadata(stack.getItemDamage()));
        }
    }
}
