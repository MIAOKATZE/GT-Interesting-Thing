package com.miaokatze.gtit.trade.preset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/** Standalone regression entry; the same isolated fixture also runs in the native testlab. */
public final class TradePresetStoreTest {

    public static void main(String[] args) throws Exception {
        Path fixture = Files.createTempDirectory("gtit-trade-presets-")
            .toAbsolutePath()
            .normalize();
        try {
            Map<String, Boolean> checks = TradePresetProbe.run(fixture, UUID.randomUUID());
            for (Map.Entry<String, Boolean> check : checks.entrySet()) {
                if (!Boolean.TRUE.equals(check.getValue())) throw new AssertionError(check.getKey());
                System.out.println("PASS " + check.getKey());
            }
            System.out.println("TradePresetStoreTest: " + checks.size() + " checks passed");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(fixture)) {
                for (Path path : paths.sorted(Comparator.reverseOrder())
                    .collect(java.util.stream.Collectors.toList())) {
                    if (!path.toAbsolutePath()
                        .normalize()
                        .startsWith(fixture)) throw new IllegalStateException("fixture cleanup escaped root");
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
