package com.miaokatze.gtit.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 机器工作音效静音配置管理
 * 配置文件路径: config/gtit/gtit_mute.json
 * <p>
 * 两项配置：
 * - mute_machine_working_sounds: 新放置机器默认静音（有 GUI 按钮的机器可单独取消）
 * - extra_mute: 额外强制拦截无静音按钮机器的音效（锅炉蒸汽排放/沸腾加热循环音/管道蒸汽泄漏音），不受 GUI 控制
 */
public class MuteConfig {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private static final String CONFIG_DIR = "config" + File.separator + "gtit";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "gtit_mute.json";

    private static boolean muteMachineWorkingSounds = false;
    private static boolean extraMute = false;

    public static void init() {
        loadConfig();
    }

    public static boolean isMuteMachineWorkingSounds() {
        return muteMachineWorkingSounds;
    }

    public static void setMuteMachineWorkingSounds(boolean value) {
        muteMachineWorkingSounds = value;
    }

    public static boolean isExtraMute() {
        return extraMute;
    }

    public static void setExtraMute(boolean value) {
        extraMute = value;
    }

    public static void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                StringBuilder sb = new StringBuilder();
                int ch;
                while ((ch = reader.read()) != -1) {
                    sb.append((char) ch);
                }
                parseConfig(sb.toString());
                LOG.info(
                    "机器静音配置已加载 (mute_machine_working_sounds=" + muteMachineWorkingSounds
                        + ", extra_mute="
                        + extraMute
                        + ")");
                return;
            } catch (IOException e) {
                LOG.error("加载机器静音配置失败，使用默认配置", e);
            }
        }
        muteMachineWorkingSounds = false;
        extraMute = false;
        saveConfig();
    }

    public static void saveConfig() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            writer.write(serializeConfig());
            LOG.info("机器静音配置已保存");
        } catch (IOException e) {
            LOG.error("保存机器静音配置失败", e);
        }
    }

    private static String serializeConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"_comment_mute\": \"mute_machine_working_sounds=true 时：新放置机器默认静音（有 GUI 按钮的机器可单独取消静音）。\",\n");
        sb.append("  \"mute_machine_working_sounds\": ")
            .append(muteMachineWorkingSounds)
            .append(",\n");
        sb.append(
            "  \"_comment_extra_mute\": \"extra_mute=true 时：额外强制拦截无静音按钮机器的音效（锅炉蒸汽排放音/锅炉沸腾加热循环音/管道蒸汽泄漏音），不受 GUI 按钮控制。\",\n");
        sb.append("  \"extra_mute\": ")
            .append(extraMute)
            .append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void parseConfig(String json) {
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            muteMachineWorkingSounds = false;
            extraMute = false;
            return;
        }

        String muteValue = extractJsonValue(trimmed, "mute_machine_working_sounds");
        muteMachineWorkingSounds = muteValue != null && Boolean.parseBoolean(muteValue.trim());

        String extraValue = extractJsonValue(trimmed, "extra_mute");
        extraMute = extraValue != null && Boolean.parseBoolean(extraValue.trim());
    }

    private static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int start = json.indexOf(searchKey);
        if (start < 0) return null;
        start += searchKey.length();
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != '\n') {
            end++;
        }
        return json.substring(start, end)
            .trim();
    }
}
