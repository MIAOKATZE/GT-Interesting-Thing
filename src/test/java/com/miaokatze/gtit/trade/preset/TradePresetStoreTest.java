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
            Path runtime = fixture.resolve("runtime-config-fixture");
            Files.createDirectories(runtime);
            Files.write(runtime.resolve("preset-runtime-fixture.marker"), new byte[0]);
            String classpath = java.util.Arrays.stream(
                System.getProperty("java.class.path")
                    .split(java.io.File.pathSeparator))
                .map(
                    path -> java.nio.file.Paths.get(path)
                        .toAbsolutePath()
                        .toString())
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
            String javaExecutable = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java")
                .toString();
            Process process = new ProcessBuilder(
                javaExecutable,
                "-cp",
                classpath,
                TradePresetRuntimeTest.class.getName()).directory(runtime.toFile())
                    .inheritIO()
                    .start();
            if (process.waitFor() != 0) throw new AssertionError("TradePresetRuntimeTest failed");
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
