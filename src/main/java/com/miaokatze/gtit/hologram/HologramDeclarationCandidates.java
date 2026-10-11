package com.miaokatze.gtit.hologram;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementChain;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.HatchElementBuilder;

/** Reads exact declarations without running check, hatch adders or setters on the live controller. */
final class HologramDeclarationCandidates {

    final List<ItemStack> stacks = new ArrayList<>();
    final IdentityHashMap<IStructureElement<?>, Boolean> seen = new IdentityHashMap<>();
    boolean complete = true;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    void collect(IStructureElement element, Object context, World world, int x, int y, int z, ItemStack trigger,
        AutoPlaceEnvironment environment, int depth) {
        if (element == null || depth > 12 || seen.size() >= 128) {
            complete = false;
            return;
        }
        if (seen.put(element, Boolean.TRUE) != null) return;
        try {
            if (element instanceof IStructureElementChain) {
                IStructureElement<?>[] children = ((IStructureElementChain<?>) element).fallbacks();
                if (children == null || children.length > 64) complete = false;
                else for (IStructureElement<?> child : children)
                    collect(child, context, world, x, y, z, trigger, environment, depth + 1);
                return;
            }
            Method factory = element.getClass()
                .getEnclosingMethod();
            if (factory == null) {
                if (element != StructureUtility.isAir()) complete = false;
                return;
            }
            String name = factory.getName();
            if (factory.getDeclaringClass() == StructureUtility.class && "onElementPass".equals(name)) {
                collect(
                    (IStructureElement<?>) captured(element, "val$element"),
                    context,
                    world,
                    x,
                    y,
                    z,
                    trigger,
                    environment,
                    depth + 1);
                return;
            }
            if (factory.getDeclaringClass() == HatchElementBuilder.class && "build".equals(name)) {
                Predicate<ItemStack> declared = HologramHatchPolicy.predicate(element, context, trigger);
                if (declared == null) {
                    complete = false;
                    // A builder with a replacement filter has no recoverable full structural declaration.
                    // Do not expand a potentially broad predicate over the whole machine registry.
                    return;
                }
                for (IMetaTileEntity prototype : GregTechAPI.METATILEENTITIES) {
                    if (prototype == null) continue;
                    ItemStack stack = prototype.getStackForm(1);
                    if (stack != null && declared.test(stack)) add(stack);
                }
                return;
            }
            if (factory.getDeclaringClass() == StructureUtility.class && "ofBlocksTiered".equals(name)) {
                Object hints = captured(element, "val$hints");
                if (!(hints instanceof List) || ((List<?>) hints).isEmpty() || ((List<?>) hints).size() > 128) {
                    complete = false;
                    return;
                }
                for (Object hint : (List<?>) hints) {
                    if (!(hint instanceof Pair)) {
                        complete = false;
                        continue;
                    }
                    Pair<?, ?> pair = (Pair<?, ?>) hint;
                    if (pair.getLeft() instanceof Block && pair.getRight() instanceof Integer)
                        add(new ItemStack((Block) pair.getLeft(), 1, (Integer) pair.getRight()));
                    else complete = false;
                }
                // allKnownTiers describes known alternatives, not every value of an arbitrary tier extractor.
                complete = false;
                return;
            }
            boolean fixed = factory.getDeclaringClass() == StructureUtility.class && "ofBlock".equals(name);
            boolean frame = factory.getDeclaringClass() == GTStructureUtility.class && "ofFrame".equals(name)
                && HologramFrameSupport.target(element) != null;
            boolean coil = factory.getDeclaringClass() == GTStructureUtility.class && "ofCoil".equals(name);
            if (!fixed && !frame && !coil) {
                complete = false;
                return;
            }
            IStructureElement.BlocksToPlace blocks = element
                .getBlocksToPlace(context, world, x, y, z, trigger, environment);
            if (blocks == null || blocks.getStacks() == null) {
                complete = false;
                return;
            }
            int count = 0;
            for (ItemStack stack : blocks.getStacks()) {
                if (count++ >= 128) {
                    complete = false;
                    break;
                }
                add(stack);
            }
            if (coil) complete = false; // Trigger selects one coil grade; full coil alternatives are not enumerated.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            complete = false;
        }
    }

    private static Object captured(Object element, String name) throws ReflectiveOperationException {
        Field field = element.getClass()
            .getDeclaredField(name);
        if (!field.isSynthetic()) throw new IllegalArgumentException("Unexpected declaration field");
        field.setAccessible(true);
        return field.get(element);
    }

    private void add(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return;
        if (stack.getItemDamage() < 0 || stack.getItemDamage() == 32767) {
            complete = false;
            return;
        }
        for (ItemStack existing : stacks)
            if (existing.getItem() == stack.getItem() && existing.getItemDamage() == stack.getItemDamage()
                && ItemStack.areItemStackTagsEqual(existing, stack)) return;
        if (stacks.size() >= 512) {
            complete = false;
            return;
        }
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        stacks.add(copy);
    }
}
