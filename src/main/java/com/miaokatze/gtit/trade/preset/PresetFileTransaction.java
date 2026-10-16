package com.miaokatze.gtit.trade.preset;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.miaokatze.gtit.trade.NekoTradeJson;

/** Write-ahead rollback journal. A crash before journal deletion rolls the whole operation back. */
final class PresetFileTransaction {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final String JOURNAL = "config/gtit/trade/preset_transaction.json";
    private final Path root;

    PresetFileTransaction(Path root) {
        this.root = root.toAbsolutePath()
            .normalize();
    }

    Path checked(String relative) throws IOException {
        if (!(relative.matches("MiaoTrade/[a-z0-9_-]{1,64}\\.json") || relative.equals("config/gtit/trade/pages.json")
            || relative.equals("config/gtit/trade/preset_state.json")
            || relative.equals(JOURNAL)
            || relative.matches("config/gtit/trade/trades/tab_[1-9][0-9]*\\.json"))) {
            throw new IOException("不允许的预设文件路径");
        }
        Path target = root.resolve(relative)
            .normalize();
        if (!target.startsWith(root)) throw new IOException("预设路径越界");
        Path cursor = target;
        while (cursor != null && cursor.startsWith(root)) {
            if (Files.isSymbolicLink(cursor)) throw new IOException("预设路径不允许符号链接: " + relative);
            cursor = cursor.getParent();
        }
        return target;
    }

    void recover() throws IOException {
        Path journal = checked(JOURNAL);
        if (!Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.size(journal) > 96L * 1024 * 1024) throw new IOException("预设事务日志过大，需人工检查");
        String json = new String(Files.readAllBytes(journal), StandardCharsets.UTF_8);
        NekoTradeJson.validateDepth(json);
        Journal data = GSON.fromJson(json, Journal.class);
        if (data == null || data.before == null) throw new IOException("预设事务日志损坏");
        // Validate all paths before touching any file, including a manually edited journal.
        for (String path : data.before.keySet()) {
            if (path.equals(JOURNAL)) throw new IOException("预设事务日志不能修改自身");
            checked(path);
        }
        rollback(data);
        Files.delete(journal);
    }

    void commit(Map<String, byte[]> changes) throws IOException {
        recover();
        Journal journal = new Journal();
        for (Map.Entry<String, byte[]> change : changes.entrySet()) {
            if (change.getKey()
                .equals(JOURNAL)) throw new IOException("事务目标非法");
            Path path = checked(change.getKey());
            if (Files.exists(path) && Files.size(path) > 32L * 1024 * 1024) {
                throw new IOException("预设目标文件过大");
            }
            byte[] before = Files.exists(path) ? Files.readAllBytes(path) : null;
            journal.before.put(
                change.getKey(),
                before == null ? null
                    : Base64.getEncoder()
                        .encodeToString(before));
        }
        byte[] journalBytes = GSON.toJson(journal)
            .getBytes(StandardCharsets.UTF_8);
        if (journalBytes.length > 96L * 1024 * 1024) throw new IOException("预设事务过大，未写入任何文件");
        writeAtomic(checked(JOURNAL), journalBytes);
        try {
            for (Map.Entry<String, byte[]> change : changes.entrySet()) {
                replace(checked(change.getKey()), change.getValue());
            }
            Files.delete(checked(JOURNAL));
        } catch (IOException | RuntimeException failure) {
            try {
                rollback(journal);
                Files.deleteIfExists(checked(JOURNAL));
            } catch (IOException | RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private void rollback(Journal journal) throws IOException {
        List<Map.Entry<String, String>> entries = new ArrayList<>(journal.before.entrySet());
        IOException failure = null;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Map.Entry<String, String> entry = entries.get(i);
            try {
                replace(
                    checked(entry.getKey()),
                    entry.getValue() == null ? null
                        : Base64.getDecoder()
                            .decode(entry.getValue()));
            } catch (IOException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private static void replace(Path path, byte[] bytes) throws IOException {
        if (bytes == null) {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) Files.delete(path);
        } else writeAtomic(path, bytes);
    }

    static void writeAtomic(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".gtit-preset-", ".tmp");
        try {
            try (FileChannel output = FileChannel
                .open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static final class Journal {

        Map<String, String> before = new LinkedHashMap<>();
    }
}
