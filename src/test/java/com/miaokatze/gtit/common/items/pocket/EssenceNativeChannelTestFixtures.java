package com.miaokatze.gtit.common.items.pocket;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** JVM-only third-party channel fixtures. Production contains only the native lookup adapter port. */
final class EssenceNativeChannelTestFixtures {

    private static final Map<String, Set<String>> SEEDED_NATIVE_TAGS = new LinkedHashMap<>();
    private static Function<String, String> originalLookup;

    private EssenceNativeChannelTestFixtures() {}

    static void seedNativeForTest(String typeId, String tag) {
        if (typeId == null || typeId.isEmpty() || tag == null || tag.isEmpty()) return;
        if (originalLookup == null) {
            originalLookup = EssenceNativeChannels.nativeChannelLookup;
            EssenceNativeChannels.nativeChannelLookup = EssenceNativeChannelTestFixtures::lookup;
        }
        SEEDED_NATIVE_TAGS.computeIfAbsent(typeId, ignored -> new LinkedHashSet<>())
            .add(tag);
    }

    static void unseedNativesForTest() {
        SEEDED_NATIVE_TAGS.clear();
        if (originalLookup != null) {
            EssenceNativeChannels.nativeChannelLookup = originalLookup;
            originalLookup = null;
        }
    }

    static boolean isSeededNativeForTest(String typeId, String tag) {
        Set<String> tags = SEEDED_NATIVE_TAGS.get(typeId);
        return tags != null && tags.contains(tag);
    }

    private static String lookup(String tag) {
        for (Map.Entry<String, Set<String>> entry : SEEDED_NATIVE_TAGS.entrySet()) if (entry.getValue()
            .contains(tag)) return entry.getKey();
        return originalLookup.apply(tag);
    }
}
