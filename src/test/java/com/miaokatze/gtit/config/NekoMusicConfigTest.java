package com.miaokatze.gtit.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import net.minecraftforge.common.config.Configuration;

/** 真实 GTNHLib 注册与保存回归，每个阶段使用独立 JVM 验证游戏重启后的偏好。 */
public final class NekoMusicConfigTest {

    private NekoMusicConfigTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            Path home = clientHome();
            Path otherHome = clientHome();
            for (String phase : new String[] { "mute", "other-client", "unmute", "enabled" }) {
                Process child = new ProcessBuilder(
                    new File(System.getProperty("java.home"), File.separatorChar == '\\' ? "bin/java.exe" : "bin/java")
                        .getAbsolutePath(),
                    "-Dfile.encoding=UTF-8",
                    "-cp",
                    System.getProperty("java.class.path"),
                    NekoMusicConfigTest.class.getName(),
                    phase,
                    ("other-client".equals(phase) ? otherHome : home).toAbsolutePath()
                        .toString()).inheritIO()
                            .start();
                check(child.waitFor() == 0, "独立 JVM 阶段失败: " + phase);
            }
            System.out.println(
                "NekoMusicConfigTest: legacy config, volume/mute/restart/unmute/restart, client isolation passed");
            return;
        }

        // 必须在首次引用 ConfigurationManager 或配置类之前指定隔离客户端目录。
        net.minecraft.launchwrapper.Launch.minecraftHome = new File(args[1]);
        java.lang.reflect.Field homeField = cpw.mods.fml.relauncher.FMLInjectionData.class
            .getDeclaredField("minecraftHome");
        homeField.setAccessible(true);
        homeField.set(null, net.minecraft.launchwrapper.Launch.minecraftHome);
        boolean expectedMuted = "unmute".equals(args[0]);
        float expectedVolume = "mute".equals(args[0]) || "other-client".equals(args[0]) ? 0.67f : 0.42f;
        check(NekoMusicConfig.music_muted == expectedMuted, "静态注册应读取重启前的静音偏好");
        check(Math.abs(NekoMusicConfig.music_volume - expectedVolume) < 0.0001f, "客户端重启应恢复各自保存的音量");
        check(
            !NekoMusicConfig.class.getField("music_volume")
                .getAnnotation(com.gtnewhorizon.gtnhlib.config.Config.Sync.class)
                .value(),
            "客户端音量不得被服务端配置同步覆盖");
        File file = new File(args[1], "config/gtit_music.cfg");
        if ("mute".equals(args[0])) {
            NekoMusicConfig.setMusicMuted(true);
            check(NekoMusicConfig.music_muted, "静音选择立即更新运行时状态");
            check(readMuted(file), "静音选择应在 GUI 关闭前写入配置");
            NekoMusicConfig.setMusicVolume(Float.NaN);
            NekoMusicConfig.setMusicVolume(Float.POSITIVE_INFINITY);
            check(Math.abs(NekoMusicConfig.music_volume - 0.67f) < 0.0001f, "非法浮点音量应被忽略");
            NekoMusicConfig.setMusicVolume(-1);
            check(Math.abs(NekoMusicConfig.music_volume - 0.01f) < 0.0001f, "音量下限应钳制为 1%");
            NekoMusicConfig.setMusicVolume(2);
            check(Math.abs(NekoMusicConfig.music_volume - 1f) < 0.0001f, "音量上限应钳制为 100%");
            NekoMusicConfig.setMusicVolume(0.42f);
            check(Math.abs(NekoMusicConfig.music_volume - 0.42f) < 0.0001f, "音量选择立即更新运行时状态");
            check(readMuted(file), "保存音量应保留静音偏好");
            expectedVolume = 0.42f;
        } else if ("unmute".equals(args[0])) {
            NekoMusicConfig.setMusicMuted(false);
            check(!NekoMusicConfig.music_muted, "恢复播放立即更新运行时状态");
            check(!readMuted(file), "恢复播放应立即写入配置");
        } else {
            check(!readMuted(file), "再次重启应保持恢复播放偏好");
        }
        Configuration saved = new Configuration(file);
        saved.load();
        check(
            Math.abs(
                saved.get("music", "music_volume", 0.3)
                    .getDouble() - expectedVolume)
                < 0.0001,
            "所选音量应在 GUI 关闭前真实写入配置，并在保存开关时保留");
        System.out.println("NekoMusicConfigTest: " + args[0] + " passed");
    }

    private static Path clientHome() throws Exception {
        Path home = Files.createTempDirectory(new File(".").toPath(), "music-client-");
        Files.createDirectories(home.resolve("config"));
        Files.write(
            home.resolve("config/gtit_music.cfg"),
            Arrays.asList("music {", "    D:music_volume=0.67", "}"),
            StandardCharsets.UTF_8);
        return home;
    }

    private static boolean readMuted(File file) {
        Configuration saved = new Configuration(file);
        saved.load();
        check(saved.hasKey("music", "music_muted"), "静音偏好必须真实存在于配置文件");
        return saved.get("music", "music_muted", false)
            .getBoolean();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
