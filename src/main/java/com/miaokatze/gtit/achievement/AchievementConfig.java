package com.miaokatze.gtit.achievement;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 成就配置加载
 */
public class AchievementConfig {

    private static final String CONFIG_PATH = "config/gtit/achievements.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    public static class AchievementConfigData {

        private int version = 1;
        private List<Achievement> achievements = new ArrayList<>();

        public List<Achievement> getAchievements() {
            return achievements;
        }
    }

    public static void init() {
        // TODO: v1.6.5 实现
    }

    public static AchievementConfigData load() {
        // TODO: v1.6.5 实现
        return null;
    }

    public static void save(AchievementConfigData data) {
        // TODO: v1.6.5 实现
    }

    public static void reload() {
        // TODO: v1.6.5 实现
    }
}
