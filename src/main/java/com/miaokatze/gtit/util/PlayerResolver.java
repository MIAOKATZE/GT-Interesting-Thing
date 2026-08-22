package com.miaokatze.gtit.util;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 玩家名 ⇄ UUID 解析器（O2-06 一期）
 * <p>
 * 自 {@code GTITGiftCommand} 合并移入（原 usercache 手写 JSON 解析器
 * findUuidFromUserCache/findNameFromUserCache 全簇 + resolvePlayerUuid），
 * 消除邮件模块运行期反向依赖 command 类（{@code MailManager.sendPlayerMail}
 * 复用同一份名解析逻辑，避免两处 JSON 解析实现漂移）。
 * <p>
 * 与 {@link PlayerLookup}（O2-12）同包规划，共同构成 util 的玩家访问设施；
 * 在线查询委托 {@link PlayerLookup#getOnlinePlayerByName}。
 * GTITGiftCommand 拆分第二步（五组子命令拆文件）另见登记册 O2-06 后续批次。
 */
public final class PlayerResolver {

    /** 统一 logger（O2-B02：去中心化，名称与 LOG 同为 "gtit"，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private PlayerResolver() {}

    /**
     * 按玩家名解析 UUID（在线玩家直接取；离线玩家查 usercache.json，查不到返回 null）
     * <p>
     * v1.7.6 G2② 起 public：邮件玩家互寄（{@code MailManager.sendPlayerMail}）复用本方法。
     */
    public static UUID resolvePlayerUuid(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        EntityPlayerMP online = PlayerLookup.getOnlinePlayerByName(playerName);
        if (online != null) {
            return online.getUniqueID();
        }
        return findUuidFromUserCache(playerName, getUserCacheFile());
    }

    /**
     * 当前世界的 usercache.json 文件（世界目录的上级 = 服务器根目录）
     */
    public static File getUserCacheFile() {
        File worldDir = MinecraftServer.getServer()
            .getEntityWorld()
            .getSaveHandler()
            .getWorldDirectory();
        return new File(worldDir.getParentFile(), "usercache.json");
    }

    /**
     * 从 usercache.json 中查找玩家名对应的 UUID
     * usercache.json 格式: [{"name":"xxx","uuid":"xxx","expiresOn":"xxx"}, ...]
     */
    public static UUID findUuidFromUserCache(String playerName, File userCacheFile) {
        if (!userCacheFile.exists()) return null;
        try {
            String content = new String(Files.readAllBytes(userCacheFile.toPath()), "UTF-8");
            return parseUuidFromUserCacheJson(content, playerName);
        } catch (Exception e) {
            LOG.warn("读取 usercache.json 失败", e);
            return null;
        }
    }

    /**
     * 从 usercache.json 中查找 UUID 对应的玩家名
     */
    public static String findNameFromUserCache(UUID uuid, File userCacheFile) {
        if (!userCacheFile.exists()) return null;
        try {
            String content = new String(Files.readAllBytes(userCacheFile.toPath()), "UTF-8");
            return parseNameFromUserCacheJson(content, uuid.toString());
        } catch (Exception e) {
            LOG.warn("读取 usercache.json 失败", e);
            return null;
        }
    }

    /**
     * 从 usercache.json 内容中解析玩家名对应的 UUID
     * 简易 JSON 解析，不依赖 Gson
     */
    private static UUID parseUuidFromUserCacheJson(String json, String playerName) {
        String lowerName = playerName.toLowerCase();
        // 查找 "name":"<playerName>" 条目
        String namePattern = "\"name\"";
        int idx = 0;
        while ((idx = json.indexOf(namePattern, idx)) >= 0) {
            // 找到 name 键，提取其值
            int colonPos = json.indexOf(':', idx + namePattern.length());
            if (colonPos < 0) break;
            int valueStart = json.indexOf('"', colonPos + 1);
            if (valueStart < 0) break;
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd < 0) break;
            String name = json.substring(valueStart + 1, valueEnd);

            if (name.equalsIgnoreCase(lowerName)) {
                // 找到匹配的玩家名，在同一对象中查找 uuid
                String uuidStr = extractUuidFromObject(json, idx);
                if (uuidStr != null) {
                    try {
                        return UUID.fromString(uuidStr);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            idx = valueEnd + 1;
        }
        return null;
    }

    /**
     * 从 usercache.json 内容中解析 UUID 对应的玩家名
     */
    private static String parseNameFromUserCacheJson(String json, String uuidStr) {
        // 查找 "uuid":"<uuidStr>" 条目
        String uuidPattern = "\"uuid\"";
        int idx = 0;
        while ((idx = json.indexOf(uuidPattern, idx)) >= 0) {
            int colonPos = json.indexOf(':', idx + uuidPattern.length());
            if (colonPos < 0) break;
            int valueStart = json.indexOf('"', colonPos + 1);
            if (valueStart < 0) break;
            int valueEnd = json.indexOf('"', valueStart + 1);
            if (valueEnd < 0) break;
            String foundUuid = json.substring(valueStart + 1, valueEnd);

            if (foundUuid.equalsIgnoreCase(uuidStr)) {
                // 在同一对象中查找 name
                String name = extractNameFromObject(json, idx);
                if (name != null) return name;
            }
            idx = valueEnd + 1;
        }
        return null;
    }

    /**
     * 从 JSON 对象中提取 uuid 值（向前和向后搜索同一 {} 块内的键）
     */
    private static String extractUuidFromObject(String json, int startIdx) {
        // 找到包含 startIdx 的 {} 块
        int objStart = json.lastIndexOf('{', startIdx);
        int objEnd = json.indexOf('}', startIdx);
        if (objStart < 0 || objEnd < 0) return null;

        String obj = json.substring(objStart, objEnd + 1);
        return extractJsonValue(obj, "uuid");
    }

    /**
     * 从 JSON 对象中提取 name 值
     */
    private static String extractNameFromObject(String json, int startIdx) {
        int objStart = json.lastIndexOf('{', startIdx);
        int objEnd = json.indexOf('}', startIdx);
        if (objStart < 0 || objEnd < 0) return null;

        String obj = json.substring(objStart, objEnd + 1);
        return extractJsonValue(obj, "name");
    }

    /**
     * 从 JSON 字符串中提取指定键的值
     */
    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonPos = json.indexOf(':', idx + pattern.length());
        if (colonPos < 0) return null;
        int valueStart = json.indexOf('"', colonPos + 1);
        if (valueStart < 0) return null;
        int valueEnd = json.indexOf('"', valueStart + 1);
        if (valueEnd < 0) return null;
        return json.substring(valueStart + 1, valueEnd);
    }
}
