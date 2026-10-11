package com.miaokatze.gtit.hologram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Records only the named channel API calls inside a preview capture. */
public final class HologramChannelTrace {

    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
    private static final AtomicLong GENERATIONS = new AtomicLong();

    private HologramChannelTrace() {}

    public static Scope begin(Object context) {
        Scope scope = new Scope(context, ACTIVE.get());
        ACTIVE.set(scope);
        return scope;
    }

    /** Call from capture's finally block, including failed and nested captures. */
    public static Report end(Scope scope) {
        if (ACTIVE.get() != scope) throw new IllegalStateException("channel capture scope mismatch");
        if (scope.previous == null) ACTIVE.remove();
        else ACTIVE.set(scope.previous);
        return new Report(scope);
    }

    public static void record(ItemStack stack, String id, int effective, int operation) {
        Scope scope = ACTIVE.get();
        if (scope == null || stack == null || !validId(id)) return;
        Observation old = scope.values.get(id);
        if (old == null && scope.values.size() >= 64) return;
        NBTTagCompound channels = stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag("channels") : null;
        boolean explicit = channels != null && channels.hasKey(id, 3);
        // Presence is an observation of the key, not an integer channel value.
        if (operation == 4) effective = explicit ? channels.getInteger(id) : stack.stackSize;
        scope.values.put(id, new Observation(id, effective, explicit, (old == null ? 0 : old.operations) | operation));
    }

    private static boolean validId(String id) {
        if (id == null || id.isEmpty() || id.length() > 64) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c != '_' && c != '.' && c != '-' && (c < 'a' || c > 'z') && (c < '0' || c > '9')) return false;
        }
        return true;
    }

    static void declareMaterials(String id, List<ItemStack> stacks) {
        Scope scope = ACTIVE.get();
        if (scope == null || !validId(id) || stacks.isEmpty()) return;
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : stacks) copy.add(stack.copy());
        scope.materials.put(id, Collections.unmodifiableList(copy));
    }

    static Report merge(Report previous, Report latest) {
        if (previous == null) return latest;
        if (latest == null) return previous;
        if (previous.context != latest.context) throw new IllegalArgumentException("channel report context mismatch");
        Map<String, Observation> merged = new LinkedHashMap<>(previous.observations);
        for (Observation observation : latest.observations.values()) {
            Observation old = merged.get(observation.id);
            merged.put(
                observation.id,
                new Observation(
                    observation.id,
                    observation.effective,
                    observation.explicit,
                    observation.operations | (old == null ? 0 : old.operations)));
        }
        Map<String, List<ItemStack>> materials = new LinkedHashMap<>(previous.materials);
        materials.putAll(latest.materials);
        return new Report(latest.context, latest.generation, merged, materials);
    }

    public static final class Scope {

        private final Scope previous;
        private final Object context;
        private final long generation = GENERATIONS.incrementAndGet();
        private final Map<String, Observation> values = new LinkedHashMap<>();
        private final Map<String, List<ItemStack>> materials = new LinkedHashMap<>();

        private Scope(Object context, Scope previous) {
            this.context = context;
            this.previous = previous;
        }
    }

    public static final class Report {

        public final Object context;
        public final long generation;
        public final Map<String, Observation> observations;
        final Map<String, List<ItemStack>> materials;

        private Report(Scope scope) {
            this(scope.context, scope.generation, scope.values, scope.materials);
        }

        private Report(Object context, long generation, Map<String, Observation> values,
            Map<String, List<ItemStack>> materials) {
            this.context = context;
            this.generation = generation;
            observations = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            this.materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
        }
    }

    public static final class Observation {

        public final String id;
        public final int effective, operations;
        public final boolean explicit;

        private Observation(String id, int effective, boolean explicit, int operations) {
            this.id = id;
            this.effective = effective;
            this.explicit = explicit;
            this.operations = operations;
        }
    }
}
