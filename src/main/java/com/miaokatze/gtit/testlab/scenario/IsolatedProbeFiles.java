package com.miaokatze.gtit.testlab.scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import com.miaokatze.gtit.testlab.ProbeContext;

/** Only newly-created, run-owned scratch directories can be removed by this helper. */
final class IsolatedProbeFiles {

    private IsolatedProbeFiles() {}

    static Path directory(ProbeContext context, String role) throws IOException {
        Path parent = context.world()
            .getSaveHandler()
            .getWorldDirectory()
            .toPath()
            .toAbsolutePath()
            .normalize();
        Path root = Files.createTempDirectory(parent, "gtit-lab-" + role + "-" + context.runId() + "-")
            .toAbsolutePath()
            .normalize();
        if (!root.getParent()
            .equals(parent)) throw new IllegalStateException("Scratch escaped its world directory");
        context.onCleanup(() -> {
            try (Stream<Path> paths = Files.walk(root)) {
                Path[] owned = paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new);
                for (Path path : owned) {
                    if (!path.toAbsolutePath()
                        .normalize()
                        .startsWith(root)) throw new IllegalStateException("Scratch path escaped root");
                    Files.delete(path);
                }
                context.check(
                    "cleanup-" + role + "-scratch",
                    !Files.exists(root),
                    "Only the newly-created run-owned scratch directory was removed");
            } catch (IOException error) {
                throw new IllegalStateException("Cannot remove isolated fixture directory", error);
            }
        });
        return root;
    }
}
