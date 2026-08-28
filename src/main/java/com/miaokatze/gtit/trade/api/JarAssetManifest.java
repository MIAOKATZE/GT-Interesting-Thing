package com.miaokatze.gtit.trade.api;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * jar 资产清单（index.json）模型与共用解析器（贸易/抽奖整合 API 共用，E4b）。
 * <p>
 * 1.7.10 的 jar 内目录不可枚举（{@code ClassLoader.getResource("dir/")} 行为不可靠），
 * 与 BetterQuesting 官方资源包同构：以一份显式清单声明全部组条目，
 * 逐条用 classloader 读具体文件，规避目录枚举。
 * <p>
 * 清单 schema（资产根 {@code assets/<ownerModId>/gtit/trade|lottery/index.json}）：
 *
 * <pre>
 * {
 *   "formatVersion": 1,
 *   "groups": [
 *     {"groupId": "example.group", "version": 1, "path": "groups/example.json"}
 *   ]
 * }
 * </pre>
 * <p>
 * 解析纪律：formatVersion 缺失或非 {@link #CURRENT_FORMAT_VERSION}、groups 缺失、
 * 任一条目缺 groupId/path 或含非法字符时整个清单判无效（返回 null，由调用方按
 * error 记录拒绝整清单——半份清单静默丢组比拒绝更危险）；groups 为空数组是合法
 * 清单（资产可选，装载为空集）。
 */
public final class JarAssetManifest {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 清单当前唯一合法 formatVersion */
    public static final int CURRENT_FORMAT_VERSION = 1;

    /** groupId 白名单：小写字母/数字/连字符/点，1-64 位，禁止路径穿越序列（与贸易组记账文件名共用） */
    public static final String GROUP_ID_PATTERN = "[a-z0-9][a-z0-9.-]{0,63}";

    /** 清单版本 */
    private final int formatVersion;

    /** 组条目（只读视图） */
    private final List<GroupEntry> groups;

    private JarAssetManifest(int formatVersion, List<GroupEntry> groups) {
        this.formatVersion = formatVersion;
        this.groups = groups;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    /** @return 组条目只读列表（空数组 = 合法空清单） */
    public List<GroupEntry> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    /**
     * 清单条目（groupId + 源版本 + 组定义文件相对路径）。
     */
    public static final class GroupEntry {

        /** 组 ID（记账文件名与身份，须过 {@link #isValidGroupId}） */
        private final String groupId;
        /** 源版本（缺省 1；提升时按记账移除旧组重注册） */
        private final int version;
        /** 组定义 JSON 的相对路径（相对清单所在资产根，如 {@code groups/xxx.json}） */
        private final String path;

        GroupEntry(String groupId, int version, String path) {
            this.groupId = groupId;
            this.version = version;
            this.path = path;
        }

        public String getGroupId() {
            return groupId;
        }

        public int getVersion() {
            return version;
        }

        public String getPath() {
            return path;
        }
    }

    // ==================== 解析与校验 ====================

    /**
     * 解析清单 JSON（结构见类 javadoc；任一结构违规整体判无效）。
     *
     * @param json 清单根对象
     * @return 清单；无效返回 null（日志交给调用方定级——清单缺失 info、清单损坏 error）
     */
    public static JarAssetManifest parse(JsonObject json) {
        if (json == null) return null;
        try {
            if (json.get("formatVersion") == null || !json.get("formatVersion")
                .isJsonPrimitive()
                || json.get("formatVersion")
                    .getAsInt() != CURRENT_FORMAT_VERSION) {
                return null;
            }
            if (json.get("groups") == null || !json.get("groups")
                .isJsonArray()) {
                return null;
            }
            JsonArray arr = json.get("groups")
                .getAsJsonArray();
            List<GroupEntry> entries = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                if (!arr.get(i)
                    .isJsonObject()) return null;
                JsonObject entry = arr.get(i)
                    .getAsJsonObject();
                String groupId = entry.has("groupId") && entry.get("groupId")
                    .isJsonPrimitive() ? entry.get("groupId")
                        .getAsString() : null;
                String path = entry.has("path") && entry.get("path")
                    .isJsonPrimitive() ? entry.get("path")
                        .getAsString() : null;
                int version = entry.has("version") && entry.get("version")
                    .isJsonPrimitive() ? entry.get("version")
                        .getAsInt() : 1;
                if (!isValidGroupId(groupId) || !isSafeRelativePath(path)) return null;
                entries.add(new GroupEntry(groupId, version, path));
            }
            return new JarAssetManifest(CURRENT_FORMAT_VERSION, entries);
        } catch (Exception e) {
            LOG.error("[AssetAPI] 资产清单结构解析异常，判无效", e);
            return null;
        }
    }

    /** groupId 白名单校验（小写字母/数字/连字符/点，1-64 位，禁止 {@code ..} 路径穿越） */
    public static boolean isValidGroupId(String groupId) {
        return groupId != null && groupId.matches(GROUP_ID_PATTERN) && !groupId.contains("..");
    }

    /**
     * 清单条目 path 安全校验：非空、相对路径、不含 {@code ..} 穿越、不含反斜杠/冒号。
     */
    public static boolean isSafeRelativePath(String path) {
        return path != null && !path.isEmpty()
            && !path.contains("..")
            && !path.startsWith("/")
            && !path.contains("\\")
            && !path.contains(":");
    }

    /**
     * 从 classloader 资源读 JsonObject（UTF-8 + 32KB 缓冲，
     * 与 {@code BqQuestInjector}/{@code BundledTradeGroups} 的读取方式对齐）。
     *
     * @param path 资源路径（完整 classpath 路径）
     * @return 解析结果，资源缺失或解析失败返回 null（缺失静默、损坏 error 日志）
     */
    public static JsonObject readJsonResource(String path) {
        InputStream is = JarAssetManifest.class.getClassLoader()
            .getResourceAsStream(path);
        if (is == null) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 32768)) {
            return new JsonParser().parse(br)
                .getAsJsonObject();
        } catch (Exception e) {
            LOG.error("[AssetAPI] 资源 JSON 解析失败: {}", path, e);
            return null;
        }
    }
}
