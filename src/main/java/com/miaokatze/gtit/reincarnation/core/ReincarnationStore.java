package com.miaokatze.gtit.reincarnation.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * 周目记录持久化仓库（混淆加密 + 原子写）。
 * <p>
 * 文件路径固定为 {@code <minecraft dir>/} + {@link #RELATIVE_PATH}
 * （即 {@code config/gtit/reincarnation.dat}）。"config" 拼接只出现在
 * {@link #RELATIVE_PATH} 这一处常量；运行目录由构造参数
 * {@code baseDir}（Minecraft 运行目录）注入，后续 Handler/GUI 切片从
 * 服务端环境取目录后传入，本类不感知 FML。
 * <p>
 * 混淆级加密（<b>非安全级</b>）：AES/CBC/PKCS5Padding，密钥与 IV 以常量内置在
 * 发行 jar 中。目的仅是防止玩家直接手改明文 JSON（软混淆），<b>不构成机密性边界</b>
 * ——拿到 jar 的人都能解密；不得用于任何需要保密的数据。玩家间防串档依赖解密后
 * 的 UUID 校验。
 * <p>
 * 最后仁慈路径（load 的统一兜底语义）：文件不存在、魔数不符、解密失败、
 * JSON 结构损坏、formatVersion 不识别、载荷 UUID 与请求不匹配——一律视同
 * "文件不存在"，返回全新空记录，不抛出、不阻塞玩家进入下一周目。
 * 核心包刻意零日志依赖（失败路径语义已定义），保证纯 JVM 无头测试可直跑。
 * <p>
 * 写入原子性：先写同目录临时文件 {@code reincarnation.dat.tmp}，再
 * {@code ATOMIC_MOVE}（不支持时退化为普通 move）替换旧文件，进程崩溃不会留下
 * 半写状态损坏旧档。
 */
public final class ReincarnationStore {

    /** 相对 Minecraft 运行目录的固定存储路径（"config" 拼接唯一出现处） */
    public static final String RELATIVE_PATH = "config/gtit/reincarnation.dat";

    /** 载荷格式版本（不识别即按最后仁慈路径兜底） */
    static final int FORMAT_VERSION = 1;

    /** 临时文件后缀（同目录，保证 rename 原子性） */
    private static final String TEMP_SUFFIX = ".tmp";

    /** 文件魔数（"GTIR"）：快速识别本格式，不符即兜底 */
    private static final byte[] MAGIC = { 0x47, 0x54, 0x49, 0x52 };

    // 混淆级常量密钥/IV（各 16 字节 = AES-128）：非安全级，见类 javadoc
    private static final byte[] OBF_KEY = "gtit$r1ncarn@te!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OBF_IV = "GTIT#re#cycle#24".getBytes(StandardCharsets.UTF_8);

    /** 存储文件（baseDir 注入后唯一落点） */
    private final File file;

    /**
     * @param baseDir Minecraft 运行目录（非 null）；实际文件为
     *                {@code new File(baseDir, RELATIVE_PATH)}
     */
    public ReincarnationStore(File baseDir) {
        if (baseDir == null) {
            throw new IllegalArgumentException("baseDir 不能为 null");
        }
        this.file = new File(baseDir, RELATIVE_PATH);
    }

    /** @return 存储文件落点（测试与运维检查用） */
    public File getFile() {
        return file;
    }

    // ==================== 读 ====================

    /**
     * 读取玩家周目记录。任何读取/解密/解析/UUID 不匹配失败一律返回全新空记录
     * （最后仁慈路径，见类 javadoc），不抛出。
     *
     * @param uuid 玩家 UUID 字符串
     * @return 记录（永不返回 null）
     */
    public ReincarnationCycle load(String uuid) {
        ReincarnationCycle fresh = new ReincarnationCycle(uuid);
        byte[] fileBytes = readAllQuiet();
        if (fileBytes == null || !startsWith(fileBytes, MAGIC)) {
            return fresh;
        }
        byte[] plain;
        try {
            Cipher cipher = newObfuscationCipher(Cipher.DECRYPT_MODE);
            plain = cipher.doFinal(fileBytes, MAGIC.length, fileBytes.length - MAGIC.length);
        } catch (GeneralSecurityException e) {
            // 解密失败（密文被篡改/截断/非本格式）→ 最后仁慈路径
            return fresh;
        }
        return parsePayload(uuid, plain, fresh);
    }

    /**
     * 解析明文 JSON 载荷并重建记录；任何结构异常（JSON 损坏、字段缺失/类型错、
     * 长度非法、UUID 不匹配）都按最后仁慈路径返回全新记录。
     * <p>
     * 刻意捕获 {@link RuntimeException}：本方法体内所有异常的语义都是"载荷不可信"，
     * 与逐个列举受检子类型相比不漏语义且不误伤（本方法无其他可抛副作用）。
     */
    private static ReincarnationCycle parsePayload(String requestUuid, byte[] plain, ReincarnationCycle fresh) {
        try {
            JsonElement rootElement = new JsonParser().parse(new String(plain, StandardCharsets.UTF_8));
            if (!rootElement.isJsonObject()) {
                return fresh;
            }
            JsonObject root = rootElement.getAsJsonObject();
            JsonElement version = root.get("formatVersion");
            if (version == null || !version.isJsonPrimitive() || version.getAsInt() != FORMAT_VERSION) {
                return fresh;
            }
            JsonElement payloadUuid = root.get("uuid");
            if (payloadUuid == null || !payloadUuid.isJsonPrimitive()
                || !uuidMatches(payloadUuid.getAsString(), requestUuid)) {
                return fresh;
            }
            ReincarnationCycle.CycleState state = ReincarnationCycle.CycleState.valueOf(
                root.get("cycleState")
                    .getAsString());
            return ReincarnationCycle.restore(
                requestUuid,
                state,
                readPendingItems(root.get("pendingItems")),
                readFixedLengthIntArray(root.get("hullProgress")),
                readFixedLengthBooleanArray(root.get("unlockedColumns")),
                root.get("unlockedRows")
                    .getAsInt(),
                readFingerprints(root.get("executedFingerprints")));
        } catch (RuntimeException e) {
            // 载荷任何结构异常 → 最后仁慈路径（见方法 javadoc）
            return fresh;
        }
    }

    // ==================== 写 ====================

    /**
     * 保存玩家周目记录（原子写：临时文件 + rename，见类 javadoc）。
     *
     * @throws IllegalStateException 加密或磁盘写入失败（旧文件不受影响）
     */
    public void save(ReincarnationCycle cycle) {
        byte[] payload;
        try {
            byte[] json = toJson(cycle).toString()
                .getBytes(StandardCharsets.UTF_8);
            Cipher cipher = newObfuscationCipher(Cipher.ENCRYPT_MODE);
            byte[] cipherText = cipher.doFinal(json);
            payload = new byte[MAGIC.length + cipherText.length];
            System.arraycopy(MAGIC, 0, payload, 0, MAGIC.length);
            System.arraycopy(cipherText, 0, payload, MAGIC.length, cipherText.length);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("周目记录加密失败", e);
        }
        try {
            if (file.getParentFile() != null) {
                Files.createDirectories(
                    file.getParentFile()
                        .toPath());
            }
            Path tempPath = file.toPath()
                .resolveSibling(file.getName() + TEMP_SUFFIX);
            Files.write(tempPath, payload);
            try {
                Files
                    .move(tempPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // 文件系统不支持原子 rename（如某些网络盘）：退化为普通 move，仍先写临时文件
                Files.move(tempPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("周目记录写入失败: " + file, e);
        }
    }

    // ==================== JSON 载荷 ====================

    private static JsonObject toJson(ReincarnationCycle cycle) {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);
        root.addProperty("uuid", cycle.getUuid());
        root.addProperty(
            "cycleState",
            cycle.getCycleState()
                .name());

        JsonArray pending = new JsonArray();
        for (ReincarnationCycle.ItemRef item : cycle.getPendingItems()) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("id", item.getId());
            itemJson.addProperty("meta", item.getMeta());
            pending.add(itemJson);
        }
        root.add("pendingItems", pending);

        JsonArray hull = new JsonArray();
        for (int value : cycle.getHullProgress()) {
            hull.add(new JsonPrimitive(value));
        }
        root.add("hullProgress", hull);

        JsonArray columns = new JsonArray();
        for (boolean unlocked : cycle.getUnlockedColumns()) {
            columns.add(new JsonPrimitive(unlocked));
        }
        root.add("unlockedColumns", columns);

        root.addProperty("unlockedRows", cycle.getUnlockedRows());

        JsonArray fingerprints = new JsonArray();
        for (String fingerprint : cycle.getExecutedFingerprints()) {
            fingerprints.add(new JsonPrimitive(fingerprint));
        }
        root.add("executedFingerprints", fingerprints);
        return root;
    }

    private static List<ReincarnationCycle.ItemRef> readPendingItems(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("pendingItems 缺失或非数组");
        }
        List<ReincarnationCycle.ItemRef> items = new ArrayList<>();
        for (JsonElement itemElement : (JsonArray) element) {
            if (!itemElement.isJsonObject()) {
                throw new IllegalArgumentException("pendingItems 元素非对象");
            }
            JsonObject itemJson = itemElement.getAsJsonObject();
            JsonElement id = itemJson.get("id");
            JsonElement meta = itemJson.get("meta");
            if (id == null || !id.isJsonPrimitive() || meta == null || !meta.isJsonPrimitive()) {
                throw new IllegalArgumentException("pendingItems 元素字段缺失");
            }
            items.add(new ReincarnationCycle.ItemRef(id.getAsString(), meta.getAsInt()));
        }
        return items;
    }

    private static int[] readFixedLengthIntArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("hullProgress 缺失或非数组");
        }
        JsonArray array = (JsonArray) element;
        if (array.size() != ReincarnationCycle.COLUMN_COUNT) {
            throw new IllegalArgumentException("hullProgress 长度非法: " + array.size());
        }
        int[] values = new int[ReincarnationCycle.COLUMN_COUNT];
        for (int i = 0; i < array.size(); i++) {
            values[i] = array.get(i)
                .getAsInt();
        }
        return values;
    }

    private static boolean[] readFixedLengthBooleanArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("unlockedColumns 缺失或非数组");
        }
        JsonArray array = (JsonArray) element;
        if (array.size() != ReincarnationCycle.COLUMN_COUNT) {
            throw new IllegalArgumentException("unlockedColumns 长度非法: " + array.size());
        }
        boolean[] values = new boolean[ReincarnationCycle.COLUMN_COUNT];
        for (int i = 0; i < array.size(); i++) {
            values[i] = array.get(i)
                .getAsBoolean();
        }
        return values;
    }

    private static Set<String> readFingerprints(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("executedFingerprints 缺失或非数组");
        }
        Set<String> fingerprints = new HashSet<>();
        for (JsonElement fingerprint : (JsonArray) element) {
            fingerprints.add(fingerprint.getAsString());
        }
        return fingerprints;
    }

    // ==================== 加解密与文件工具 ====================

    private static Cipher newObfuscationCipher(int opMode) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(opMode, new SecretKeySpec(OBF_KEY, "AES"), new IvParameterSpec(OBF_IV));
        return cipher;
    }

    private byte[] readAllQuiet() {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            // 文件不存在/不可读 → 按文件不存在语义兜底
            return null;
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean uuidMatches(String payloadUuid, String requestUuid) {
        try {
            return UUID.fromString(payloadUuid)
                .toString()
                .equals(
                    UUID.fromString(requestUuid)
                        .toString());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
