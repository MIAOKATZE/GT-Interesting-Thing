package com.miaokatze.gtit.hologram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.structure.IStructureElement;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.common.blocks.ItemMachines;

/** Captures declared hatch types at the original API, independently of automatic fill quotas. */
public final class HologramHatchPolicy {

    private static final Map<Object, BiFunction<Object, ItemStack, Predicate<ItemStack>>> BUILDERS = new WeakHashMap<>();
    private static final Map<IStructureElement<?>, BiFunction<Object, ItemStack, Predicate<ItemStack>>> ELEMENTS = new WeakHashMap<>();
    private static final Map<Object, Map<IHatchElement<?>, Integer>> REQUIREMENTS = new WeakHashMap<>();
    private static final Map<IStructureElement<?>, Map<IHatchElement<?>, Integer>> ELEMENT_REQUIREMENTS = new WeakHashMap<>();
    private static final Map<IStructureElement<?>, java.util.EnumSet<ForgeDirection>> DISALLOWED = new WeakHashMap<>();
    private static final Map<Object, List<IHatchElement<?>>> DECLARED = new WeakHashMap<>();
    private static final Map<IStructureElement<?>, List<IHatchElement<?>>> ELEMENT_DECLARED = new WeakHashMap<>();

    public static synchronized List<IHatchElement<?>> declared(IStructureElement<?> element) {
        List<IHatchElement<?>> types = ELEMENT_DECLARED.get(element);
        return types == null ? java.util.Collections.emptyList() : new ArrayList<>(types);
    }

    public static synchronized void directions(IStructureElement<?> element,
        java.util.EnumSet<ForgeDirection> disallowed) {
        DISALLOWED.put(element, disallowed.clone());
    }

    public static synchronized boolean allowed(IStructureElement<?> element,
        com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing facing, ForgeDirection worldDirection) {
        java.util.EnumSet<ForgeDirection> excluded = DISALLOWED.get(element);
        if (excluded == null) return true;
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (excluded.contains(direction)) continue;
            ForgeDirection mapped = facing.getWorldDirection(
                direction == ForgeDirection.UP || direction == ForgeDirection.DOWN ? direction.getOpposite()
                    : direction);
            if (mapped == worldDirection) return true;
        }
        return false;
    }

    public static synchronized void requirements(Object builder,
        Map<? extends IHatchElement<?>, ? extends Number> entries) {
        Map<IHatchElement<?>, Integer> values = new java.util.LinkedHashMap<>();
        entries.forEach((type, count) -> values.put(type, Math.max(0, count.intValue())));
        REQUIREMENTS.put(builder, values);
    }

    public static synchronized Map<IHatchElement<?>, Integer> requirements(IStructureElement<?> element) {
        Map<IHatchElement<?>, Integer> values = ELEMENT_REQUIREMENTS.get(element);
        return values == null ? java.util.Collections.emptyMap() : new java.util.LinkedHashMap<>(values);
    }

    private HologramHatchPolicy() {}

    public static synchronized void declare(Object builder, Collection<? extends IHatchElement<?>> elements,
        boolean sharedBlacklist) {
        List<IHatchElement<?>> declared = new ArrayList<>(elements);
        DECLARED.put(builder, declared);
        if (!sharedBlacklist) {
            Map<IHatchElement<?>, Integer> values = new java.util.LinkedHashMap<>();
            if (declared.size() == 1) values.put(declared.get(0), 1);
            REQUIREMENTS.put(builder, values);
        }
        BUILDERS.put(builder, (context, trigger) -> stack -> {
            IMetaTileEntity tile = ItemMachines.getMetaTileEntity(stack);
            if (tile == null) return false;
            if (sharedBlacklist) for (IHatchElement<?> element : declared) if (element.mteBlacklist()
                .contains(tile.getClass())) return false;
            for (IHatchElement<?> element : declared) {
                if (!element.mteBlacklist()
                    .contains(tile.getClass()) && element.matchesHatch(tile)) return true;
            }
            return false;
        });
    }

    public static synchronized void reset(Object builder) {
        BUILDERS.remove(builder);
        REQUIREMENTS.remove(builder);
        DECLARED.remove(builder);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static synchronized void andFunction(Object builder, Function filter) {
        BiFunction<Object, ItemStack, Predicate<ItemStack>> old = BUILDERS.get(builder);
        if (old != null) BUILDERS.put(
            builder,
            (context, trigger) -> old.apply(context, trigger)
                .and((Predicate<ItemStack>) filter.apply(context)));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static synchronized void andBiFunction(Object builder, BiFunction filter) {
        BiFunction<Object, ItemStack, Predicate<ItemStack>> old = BUILDERS.get(builder);
        if (old != null) BUILDERS.put(
            builder,
            (context, trigger) -> old.apply(context, trigger)
                .and((Predicate<ItemStack>) filter.apply(context, trigger)));
    }

    public static synchronized void built(Object builder, IStructureElement<?> element) {
        BiFunction<Object, ItemStack, Predicate<ItemStack>> rule = BUILDERS.get(builder);
        if (rule != null && element != null) ELEMENTS.put(element, rule);
        if (element != null && REQUIREMENTS.containsKey(builder))
            ELEMENT_REQUIREMENTS.put(element, REQUIREMENTS.get(builder));
        if (element != null && DECLARED.containsKey(builder)) ELEMENT_DECLARED.put(element, DECLARED.get(builder));
    }

    public static synchronized Predicate<ItemStack> predicate(IStructureElement<?> element, Object context,
        ItemStack trigger) {
        BiFunction<Object, ItemStack, Predicate<ItemStack>> rule = ELEMENTS.get(element);
        return rule == null ? null : rule.apply(context, trigger);
    }
}
