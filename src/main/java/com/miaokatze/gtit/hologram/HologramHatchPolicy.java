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

import com.gtnewhorizon.structurelib.structure.IStructureElement;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.common.blocks.ItemMachines;

/** Captures declared hatch types at the original API, independently of automatic fill quotas. */
public final class HologramHatchPolicy {

    private static final Map<Object, BiFunction<Object, ItemStack, Predicate<ItemStack>>> BUILDERS = new WeakHashMap<>();
    private static final Map<IStructureElement<?>, BiFunction<Object, ItemStack, Predicate<ItemStack>>> ELEMENTS = new WeakHashMap<>();

    private HologramHatchPolicy() {}

    public static synchronized void declare(Object builder, Collection<? extends IHatchElement<?>> elements,
        boolean sharedBlacklist) {
        List<IHatchElement<?>> declared = new ArrayList<>(elements);
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
    }

    public static synchronized Predicate<ItemStack> predicate(IStructureElement<?> element, Object context,
        ItemStack trigger) {
        BiFunction<Object, ItemStack, Predicate<ItemStack>> rule = ELEMENTS.get(element);
        return rule == null ? null : rule.apply(context, trigger);
    }
}
