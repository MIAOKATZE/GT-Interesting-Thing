package com.miaokatze.gtit.hologram;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementChain;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

/** Recognizes the exact fixed casing fallback; neither visual markers nor advisory candidates grant this authority. */
final class HologramCasingFallback {

    private HologramCasingFallback() {}

    static Target resolve(IStructureElement<?> element) {
        return resolve(element, null);
    }

    static Target resolve(IStructureElement<?> element, ItemStack trigger) {
        Search search = new Search();
        search.visit(element, trigger, 0);
        Target target = search.target;
        if (search.ambiguous || target == null
            || target.meta < 0
            || target.meta > 15
            || target.block.hasTileEntity(target.meta)) return null;
        return target;
    }

    static final class Target {

        final Block block;
        final int meta;

        Target(Block block, int meta) {
            this.block = block;
            this.meta = meta;
        }
    }

    private static final class Search {

        final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        Target target;
        boolean ambiguous;

        void visit(IStructureElement<?> element, ItemStack trigger, int depth) {
            if (element == null || ambiguous || seen.containsKey(element)) return;
            if (depth > 12 || seen.size() >= 128) {
                ambiguous = true;
                return;
            }
            seen.put(element, Boolean.TRUE);
            try {
                Class<?> type = element.getClass();
                Method enclosing = type.getEnclosingMethod();
                if (enclosing != null && enclosing.getDeclaringClass() == StructureUtility.class
                    && enclosing.getName()
                        .equals("ofBlock")) {
                    Class<?>[] parameters = enclosing.getParameterTypes();
                    if (parameters.length != 4 || parameters[0] != Block.class
                        || parameters[1] != int.class
                        || parameters[2] != Block.class
                        || parameters[3] != int.class) {
                        ambiguous = true;
                        return;
                    }
                    // The two-argument factory delegates to this four-argument implementation with identical pairs.
                    Block accepted = (Block) captured(type, element, "val$block", Block.class);
                    int acceptedMeta = (Integer) captured(type, element, "val$meta", int.class);
                    Block placed = (Block) captured(type, element, "val$defaultBlock", Block.class);
                    int placedMeta = (Integer) captured(type, element, "val$defaultMeta", int.class);
                    if (accepted == null || accepted != placed || acceptedMeta != placedMeta) {
                        ambiguous = true;
                        return;
                    }
                    if (target == null) target = new Target(accepted, acceptedMeta);
                    else if (target.block != accepted || target.meta != acceptedMeta) ambiguous = true;
                    return;
                }
                IStructureElement<?> backing = HologramDeclarationWrappers.backing(element);
                if (backing != null) {
                    visit(backing, HologramDeclarationWrappers.trigger(element, trigger), depth + 1);
                    return;
                }
                if (enclosing != null && enclosing.getDeclaringClass() == StructureUtility.class
                    && "ofBlocksTiered".equals(enclosing.getName())) {
                    Object raw = HologramDeclarationWrappers.captured(element, "val$hints");
                    if (trigger == null || !(raw instanceof List)
                        || ((List<?>) raw).isEmpty()
                        || ((List<?>) raw).size() > 128) {
                        ambiguous = true;
                        return;
                    }
                    List<?> hints = (List<?>) raw;
                    Object selected = hints.get(Math.min(Math.max(trigger.stackSize, 1), hints.size()) - 1);
                    if (!(selected instanceof Pair)) {
                        ambiguous = true;
                        return;
                    }
                    Pair<?, ?> pair = (Pair<?, ?>) selected;
                    if (!(pair.getLeft() instanceof Block) || !(pair.getRight() instanceof Integer)) {
                        ambiguous = true;
                        return;
                    }
                    Target selectedTarget = new Target((Block) pair.getLeft(), (Integer) pair.getRight());
                    if (target == null) target = selectedTarget;
                    else if (target.block != selectedTarget.block || target.meta != selectedTarget.meta)
                        ambiguous = true;
                    return;
                }
                if (element instanceof IStructureElementChain) {
                    IStructureElement<?>[] fallbacks = ((IStructureElementChain<?>) element).fallbacks();
                    if (fallbacks == null || fallbacks.length > 64) {
                        ambiguous = true;
                        return;
                    }
                    for (IStructureElement<?> fallback : fallbacks) visit(fallback, trigger, depth + 1);
                    return;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                ambiguous = true;
            }
        }

        private static Object captured(Class<?> type, Object element, String name, Class<?> expected)
            throws ReflectiveOperationException {
            Field field = type.getDeclaredField(name);
            if (field.getType() != expected || Modifier.isStatic(field.getModifiers()) || !field.isSynthetic()) {
                throw new IllegalArgumentException("Unexpected fixed-block factory field");
            }
            field.setAccessible(true);
            return field.get(element);
        }
    }
}
