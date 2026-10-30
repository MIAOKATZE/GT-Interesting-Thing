package com.miaokatze.gtit.crossmod.bq;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Filesystem-only regression; no Minecraft, BQ runtime, network or Gradle invocation is required. */
public final class BqStampFileTest {

    private static final String NAME = "gtit-injected.json";
    private static int checks;
    private static int skipped;

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("gtit-bq-stamp-regression-");
        freshParent(work.resolve("fresh/World/betterquesting"));
        dualKeys(work.resolve("gtit-first"), false);
        dualKeys(work.resolve("miao-first"), true);
        strictReadFailure(work.resolve("read-failure"));
        malformedStamp(work.resolve("malformed"), "{not valid JSON");
        malformedStamp(work.resolve("non-object"), "[]");
        fileParent(work.resolve("file-parent"));
        failedAtomicMove(work.resolve("atomic-failure"));
        failedNativePublication(work.resolve("native-failure"));
        deniedParent(work.resolve("denied-parent"));
        System.out.println("BQ stamp RESULT checks=" + checks + " failures=0 skipped=" + skipped + " work=" + work);
    }

    private static void freshParent(Path parent) throws Exception {
        File stamp = parent.resolve(NAME)
            .toFile();
        check(!Files.exists(parent), "fresh BQ parent really absent");
        BqStampFile.update(stamp, "gtit", "new-版本-\"quoted\"");
        check(Files.isDirectory(parent), "fresh write creates full parent chain");
        check(
            read(stamp).get("gtit")
                .getAsString()
                .equals("new-版本-\"quoted\""),
            "UTF-8/escaped value is intact");
        onlyStamp(parent);
    }

    private static void dualKeys(Path parent, boolean miaoFirst) throws Exception {
        File stamp = parent.resolve(NAME)
            .toFile();
        String first = miaoFirst ? "miao" : "gtit", second = miaoFirst ? "gtit" : "miao";
        JsonObject data = new JsonObject();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("keep", 7);
        data.add("metadata", metadata);
        data.addProperty(first, "first-version");
        BqStampFile.write(stamp, data);
        BqStampFile.update(stamp, second, "second-version");
        data = read(stamp);
        check(
            data.get(first)
                .getAsString()
                .equals("first-version")
                && data.get(second)
                    .getAsString()
                    .equals("second-version"),
            "atomic full-object replacement retains both pack keys, miaoFirst=" + miaoFirst);
        check(
            data.getAsJsonObject("metadata")
                .get("keep")
                .getAsInt() == 7,
            "unknown nested metadata survives");
        onlyStamp(parent);
    }

    private static void strictReadFailure(Path parent) throws Exception {
        Files.createDirectories(parent);
        File stamp = parent.resolve(NAME)
            .toFile();
        byte[] original = "{\"gtit\":\"old\",\"miao\":\"keep\",\"metadata\":{\"keep\":7}}"
            .getBytes(StandardCharsets.UTF_8);
        Files.write(stamp.toPath(), original);
        int[] reads = { 0 };
        expectIOException(() -> BqStampFile.update(stamp, "gtit", "new", target -> {
            reads[0]++;
            check(
                target.equals(
                    stamp.toPath()
                        .toAbsolutePath()),
                "strict reader targets the actual existing stamp");
            throw new IOException("synthetic unreadable existing stamp");
        }));
        check(reads[0] == 1, "read failure has no empty-object fallback or retry");
        check(
            Arrays.equals(original, Files.readAllBytes(stamp.toPath())),
            "read failure preserves both keys and metadata byte-for-byte");
        onlyStamp(parent);
    }

    private static void malformedStamp(Path parent, String original) throws Exception {
        Files.createDirectories(parent);
        File stamp = parent.resolve(NAME)
            .toFile();
        Files.write(stamp.toPath(), original.getBytes(StandardCharsets.UTF_8));
        expectIOException(() -> BqStampFile.update(stamp, "gtit", "new"));
        check(
            original.equals(new String(Files.readAllBytes(stamp.toPath()), StandardCharsets.UTF_8)),
            "malformed/non-object existing stamp is preserved, not replaced with an empty object");
        onlyStamp(parent);
    }

    private static void fileParent(Path parent) throws Exception {
        byte[] original = "not a directory".getBytes(StandardCharsets.UTF_8);
        Files.write(parent, original);
        expectIOException(
            () -> BqStampFile.write(
                parent.resolve(NAME)
                    .toFile(),
                new JsonObject()));
        check(Arrays.equals(original, Files.readAllBytes(parent)), "regular-file parent rejected unchanged");
    }

    private static void failedAtomicMove(Path parent) throws Exception {
        Files.createDirectories(parent);
        File stamp = parent.resolve(NAME)
            .toFile();
        byte[] original = "{\"gtit\":\"old\",\"miao\":\"preserve\",\"meta\":7}".getBytes(StandardCharsets.UTF_8);
        Files.write(stamp.toPath(), original);
        JsonObject replacement = new JsonObject();
        replacement.addProperty("gtit", "new");
        int[] attempts = { 0 };
        expectIOException(() -> BqStampFile.write(stamp, replacement, (temporary, target) -> {
            attempts[0]++;
            check(Files.isRegularFile(temporary), "complete sibling temp exists before publication");
            check(
                read(temporary.toFile()).get("gtit")
                    .getAsString()
                    .equals("new"),
                "temporary JSON fully written and closed");
            check(
                Arrays.equals(original, Files.readAllBytes(target)),
                "old regular stamp untouched before publication");
            throw new AtomicMoveNotSupportedException(
                temporary.toString(),
                target.toString(),
                "synthetic filesystem refusal");
        }));
        check(attempts[0] == 1, "unsupported atomic move has no non-atomic retry/fallback");
        check(
            Arrays.equals(original, Files.readAllBytes(stamp.toPath())),
            "failed publication preserves exact prior regular-file bytes");
        onlyStamp(parent);
    }

    private static void failedNativePublication(Path parent) throws Exception {
        Path stamp = parent.resolve(NAME), marker = stamp.resolve("keep");
        Files.createDirectories(stamp);
        byte[] original = "existing destination contents".getBytes(StandardCharsets.UTF_8);
        Files.write(marker, original);
        expectIOException(() -> BqStampFile.write(stamp.toFile(), new JsonObject()));
        check(
            Arrays.equals(original, Files.readAllBytes(marker)),
            "native atomic failure preserves nonempty destination directory");
        onlyStamp(parent);
    }

    private static void deniedParent(Path parent) throws Exception {
        Files.createDirectories(parent);
        File stamp = parent.resolve(NAME)
            .toFile();
        byte[] original = "{\"gtit\":\"old\",\"miao\":\"preserve\"}".getBytes(StandardCharsets.UTF_8);
        Files.write(stamp.toPath(), original);
        if (Files.getFileAttributeView(parent, PosixFileAttributeView.class) == null) {
            skipped++;
            System.out.println("SKIP parent permissions: no POSIX support");
            return;
        }
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(parent);
        try {
            Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("r-x------"));
            if (Files.isWritable(parent)) {
                skipped++;
                System.out.println("SKIP parent permissions: effective user bypasses directory permissions");
                return;
            }
            expectIOException(() -> BqStampFile.write(stamp, new JsonObject()));
            check(
                Arrays.equals(original, Files.readAllBytes(stamp.toPath())),
                "unwritable parent cannot truncate old stamp");
            onlyStamp(parent);
        } finally {
            Files.setPosixFilePermissions(parent, permissions);
        }
    }

    private static JsonObject read(File file) throws IOException {
        return new JsonParser().parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    private static void onlyStamp(Path parent) {
        check(
            Arrays.equals(
                new String[] { NAME },
                parent.toFile()
                    .list()),
            "no temporary publication files remain");
    }

    @FunctionalInterface
    private interface IoAction {

        void run() throws IOException;
    }

    private static void expectIOException(IoAction action) throws IOException {
        try {
            action.run();
        } catch (IOException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Expected publication to throw IOException");
    }

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }
}
