package com.miaokatze.gtit.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 机器工作音效静音配置管理
 * 配置文件路径: config/gtit/gtit_mute.json
 * <p>
 * 启用后，所有 GT 系列机器（单方块 + 多方块）的工作音效将被禁用，
 * 包括：进程开始音、中断音、循环工作音、蒸汽排放音等。
 * 不影响：工具右击音效、机器开关机音效、合成台音效等交互音效。
 */
public class MuteConfig {

    private static final String CONFIG_DIR = "config" + File.separator + "gtit";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "gtit_mute.json";

    /**
     * 是否禁用 GT 机器工作音效
     * false: 保持原版行为（默认）
     * true: 禁用所有 GT 机器工作音效
     */
    private static boolean muteMachineWorkingSounds = false;

    public static void init() {
        loadConfig();
    }

    public static boolean isMuteMachineWorkingSounds() {
        return muteMachineWorkingSounds;
    }

    public static void setMuteMachineWorkingSounds(boolean value) {
        muteMachineWorkingSounds = value;
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
                GTInterestingThing.LOG.info("机器静音配置已加载 (mute_machine_working_sounds=" + muteMachineWorkingSounds + ")");
                return;
            } catch (IOException e) {
                GTInterestingThing.LOG.error("加载机器静音配置失败，使用默认配置", e);
            }
        }
        // 首次运行或加载失败，使用默认配置并保存
        muteMachineWorkingSounds = false;
        saveConfig();
    }

    public static void saveConfig() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            writer.write(serializeConfig());
            GTInterestingThing.LOG.info("机器静音配置已保存");
        } catch (IOException e) {
            GTInterestingThing.LOG.error("保存机器静音配置失败", e);
        }
    }

    private static String serializeConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(
            "  \"_comment\": \"mute_machine_working_sounds=true 时：新放置机器默认静音（GUI 按钮可单独取消）；锅炉蒸汽满罐排放音效（ventSteamIfTankIsFull）额外强制禁用，不受 GUI 按钮控制。\",\n");
        sb.append("  \"mute_machine_working_sounds\": ")
            .append(muteMachineWorkingSounds)
            .append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void parseConfig(String json) {
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            muteMachineWorkingSounds = false;
            return;
        }

        String value = extractJsonValue(trimmed, "mute_machine_working_sounds");
        if (value != null) {
            muteMachineWorkingSounds = Boolean.parseBoolean(value.trim());
        } else {
            muteMachineWorkingSounds = false;
        }
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
