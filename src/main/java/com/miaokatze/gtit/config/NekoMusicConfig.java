package com.miaokatze.gtit.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

/**
 * 猫猫售货机音乐配置类
 * <p>
 * 使用 GTNHLib 的 @Config 注解方式管理配置（与 VM mod 的 VMConfig 一致），
 * 配置文件位于 config/gtit_music.cfg，支持运行时实时保存。
 * <p>
 * 当音量滑块被调整时，通过 {@link ConfigurationManager#save} 即时保存到磁盘。
 * NekoMusicEventHandler 在 tick 处理中读取此配置值作为音量倍率。
 * <p>
 * 注意：此配置类替代了原先对 VMConfig.music.music_volume 的依赖，
 * 是步骤10「移除 VM mod 依赖」的核心组件之一。
 */
@Config(modid = "gtit", category = "music", filename = "gtit_music")
public class NekoMusicConfig {

    /** 日志记录器（独立于 LOG，避免类加载顺序依赖） */
    @Config.Ignore
    private static final Logger LOG = LogManager.getLogger("GTIT");

    /**
     * 音乐音量倍率
     * <p>
     * 取值范围 0.01~1.0，默认 0.3（v1.6.24 从 0.5 调整为 0.3，避免新用户首次进入被高音量惊吓）。
     * <p>
     * 此值作为 BGM 实际播放音量的倍率：
     * 实际音量 = 淡入淡出音量 × music_volume。
     * NekoMusicEventHandler 每 tick 读取此值，通过 SoundSystem.setVolume() 应用到正在播放的 BGM。
     */
    @Config.LangKey("gtit.config.music.volume")
    @Config.DefaultFloat(0.3f)
    @Config.RangeFloat(min = 0.01f, max = 1.0f)
    @Config.Comment("猫猫售货机 BGM 音量倍率 (0.01~1.0)")
    public static float music_volume = 0.3f;

    /*
     * 静态初始化块：注册配置到 GTNHLib ConfigurationManager
     * <p>
     * ConfigurationManager.registerConfig() 会：
     * 1. 读取 config/gtit_music.cfg 配置文件（不存在则创建）
     * 2. 将文件中的值加载到 music_volume 字段
     * 3. 保存配置文件（写入默认值或更新后的值）
     * <p>
     * ConfigException 是 RuntimeException 的子类，但仍用 try-catch 防止注册失败导致类加载崩溃。
     * 此类首次被引用时（从 GUI 或事件处理器）触发静态块，此时游戏已启动，config 目录已就绪。
     * <p>
     * 与 VM 的注册方式差异：VM 在 VendingMachine.java 的 preInit 中注册 VMConfig，
     * 本类采用静态块自注册，使配置类自包含，无需修改 main 类。
     */
    static {
        try {
            ConfigurationManager.registerConfig(NekoMusicConfig.class);
        } catch (Exception e) {
            LOG.error("[NekoMusicConfig] 配置注册失败，将使用默认值", e);
        }
    }
}
