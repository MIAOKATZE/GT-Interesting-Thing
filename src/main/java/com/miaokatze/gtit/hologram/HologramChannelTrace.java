package com.miaokatze.gtit.hologram;

import java.util.Collections;
import java.util.LinkedHashMap;
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

    public static final class Scope {

        private final Scope previous;
        private final Object context;
        private final long generation = GENERATIONS.incrementAndGet();
        private final Map<String, Observation> values = new LinkedHashMap<>();

        private Scope(Object context, Scope previous) {
            this.context = context;
            this.previous = previous;
        }
    }

    public static final class Report {

        public final Object context;
        public final long generation;
        public final Map<String, Observation> observations;

        private Report(Scope scope) {
            context = scope.context;
            generation = scope.generation;
            observations = Collections.unmodifiableMap(new LinkedHashMap<>(scope.values));
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
