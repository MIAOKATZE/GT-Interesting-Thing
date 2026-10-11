package com.miaokatze.gtit.hologram;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;

import net.minecraft.block.Block;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElementChain;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

/** Recognizes the exact fixed casing fallback; neither visual markers nor advisory candidates grant this authority. */
final class HologramCasingFallback {

    private HologramCasingFallback() {}

    static Target resolve(IStructureElement<?> element) {
        Search search = new Search();
        search.visit(element, 0);
        Target target = search.target;
        if (search.ambiguous || target == null
            || target.meta < 0
            || target.meta > 15
            || !target.block.getClass()
                .getName()
                .startsWith("gregtech.common.blocks.BlockCasings")
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

        void visit(IStructureElement<?> element, int depth) {
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
                if (element instanceof IStructureElementChain) {
                    IStructureElement<?>[] fallbacks = ((IStructureElementChain<?>) element).fallbacks();
                    if (fallbacks == null || fallbacks.length > 64) {
                        ambiguous = true;
                        return;
                    }
                    for (IStructureElement<?> fallback : fallbacks) visit(fallback, depth + 1);
                    return;
                }
                String name = type.getName();
                if (!name.startsWith("com.gtnewhorizon.structurelib.structure.")
                    && !name.startsWith("gregtech.api.util.")) return;
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                        || !IStructureElement.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    visit((IStructureElement<?>) field.get(element), depth + 1);
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
