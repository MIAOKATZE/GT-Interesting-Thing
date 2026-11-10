package com.miaokatze.gtit.trade.preset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.miaokatze.gtit.trade.NekoTradeJson;

/** Write-ahead rollback journal. A crash before journal deletion rolls the whole operation back. */
public final class PresetFileTransaction {

    private static final Gson GSON = new GsonBuilder().serializeNulls()
        .create();
    private static final String JOURNAL = "config/gtit/trade/preset_transaction.json";
    private final Path root;

    public PresetFileTransaction(Path root) {
        this.root = root.toAbsolutePath()
            .normalize();
    }

    Path root() {
        return root;
    }

    Path checked(String relative) throws IOException {
        if (!(relative.matches("MiaoTrade/[a-z0-9_-]{1,64}\\.json") || relative.equals("config/gtit/trade/pages.json")
            || relative.matches(
                "MiaoTrade/builtin_updates/[0-9a-f]{8}-[0-9a-f]{4}-3[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/(gtit-ae-basic|gtit-ae-advanced|gtit-base-decoration)\\.json")
            || relative.equals("config/gtit/trade/preset_state.json")
            || relative.equals("config/gtit/trade/order_watermark.json")
            || relative.matches("config/gtit/trade/integrated/[a-z0-9][a-z0-9.-]{0,63}\\.json")
                && !relative.contains("..")
            || relative.equals("config/gtit/trade/default_storage_v3.json")
            || relative.equals("config/gtit/nekovm_trades.json")
            || relative.equals("config/gtit/nekovm_pages.json")
            || relative.matches(
                "config/gtit/trade/default_overrides/(gtit-ae-basic|gtit-ae-advanced|gtit-base-decoration|gtit-base)\\.json")
            || relative.matches("config/gtit/trade/default_migration_backup/[a-zA-Z0-9_.-]+\\.json")
            || relative.equals(TradePresetLayout.PAGES_PATH)
            || relative.equals(JOURNAL)
            || relative.matches("config/gtit/trade/preset_tabs/tab_-[1-9][0-9]{5,9}\\.json")
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

    public void recover() throws IOException {
        synchronized (PresetFileTransaction.class) {
            recoverLocked();
        }
    }

    private void recoverLocked() throws IOException {
        Path journal = checked(JOURNAL);
        if (!Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.size(journal) > 96L * 1024 * 1024) throw new IOException("预设事务日志过大，需人工检查");
        String json = new String(Files.readAllBytes(journal), StandardCharsets.UTF_8);
        NekoTradeJson.validateDepth(json);
        Journal data = GSON.fromJson(json, Journal.class);
        if (data == null || data.before == null) throw new IOException("预设事务日志损坏");
        // Validate all paths before touching any file, including a manually edited journal.
        if (data.before.size() > 4096) throw new IOException("Too many journal entries");
        long decodedTotal = 0;
        for (String path : data.before.keySet()) {
            if (path == null) throw new IOException("Null journal path");
            if (path.equals(JOURNAL)) throw new IOException("预设事务日志不能修改自身");
            checked(path);
            String value = data.before.get(path);
            if (value != null) {
                try {
                    byte[] bytes = Base64.getDecoder()
                        .decode(value);
                    decodedTotal += bytes.length;
                    if (bytes.length > 32L * 1024 * 1024 || decodedTotal > 64L * 1024 * 1024)
                        throw new IOException("Journal restoration exceeds bounded size");
                } catch (IllegalArgumentException malformed) {
                    throw new IOException("Invalid journal base64; no files were restored", malformed);
                }
            }
        }
        rollback(data);
        Files.delete(journal);
    }

    public void commit(Map<String, byte[]> changes) throws IOException {
        synchronized (PresetFileTransaction.class) {
            commitLocked(changes);
        }
    }

    private void commitLocked(Map<String, byte[]> changes) throws IOException {
        recoverLocked();
        if (changes.size() > 4096) throw new IOException("Too many transaction entries");
        Journal journal = new Journal();
        long beforeTotal = 0, afterTotal = 0;
        for (Map.Entry<String, byte[]> change : changes.entrySet()) {
            if (change.getValue() != null && change.getValue().length > 32L * 1024 * 1024)
                throw new IOException("Transaction target exceeds bounded size");
            if (change.getKey()
                .equals(JOURNAL)) throw new IOException("事务目标非法");
            Path path = checked(change.getKey());
            if (Files.exists(path) && Files.size(path) > 32L * 1024 * 1024) {
                throw new IOException("预设目标文件过大");
            }
            byte[] before = Files.exists(path) ? Files.readAllBytes(path) : null;
            beforeTotal += before == null ? 0 : before.length;
            afterTotal += change.getValue() == null ? 0 : change.getValue().length;
            if (beforeTotal > 64L * 1024 * 1024 || afterTotal > 64L * 1024 * 1024)
                throw new IOException("Transaction exceeds bounded restoration size");
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
        com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher.publish(path, bytes);
    }

    private static final class Journal {

        Map<String, String> before = new LinkedHashMap<>();
    }
}
