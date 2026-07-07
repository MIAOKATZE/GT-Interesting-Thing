package com.miaokatze.gtit.common.machine.neko;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.audio.SoundPoolEntry;
import net.minecraft.util.ResourceLocation;

import com.cubefury.vendingmachine.VMConfig;
import com.cubefury.vendingmachine.util.VMMusicManager;
import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 猫猫售货机 BGM 事件处理器
 * <p>
 * 为猫猫售货机播放自定义 BGM，支持：
 * - 淡入淡出效果（2秒，原版 VM 的 2 倍）
 * - 最大音量 50%（通过 SoundSystem.setVolume 精确控制）
 * - 退出 GUI 后自动停止（通过 isNekoGuiOpen 标志 + tick 检测）
 * - 防止叠加播放（先停止再播放）
 * - 接入 GUI 左上角的 BGM 切换按钮
 * <p>
 * 音量控制通过反射访问 SoundSystem 实现：
 * SoundHandler → SoundManager → SoundSystem.setVolume(sourceName, volume)
 * 声音 ID 通过 NekoSoundManagerMixin 在 SoundManager.playSound() 中捕获。
 */
@SideOnly(Side.CLIENT)
public class NekoMusicEventHandler {

    /** 猫猫 BGM 资源路径（对应 sounds.json 中的 track.neko_bgm，含 3 首随机变体） */
    public static final ResourceLocation NEKO_BGM = new ResourceLocation("gtit", "track.neko_bgm");

    /** 淡入淡出时长（2 秒 = 原版 VM 1 秒的 2 倍） */
    private static final long FADE_TIME_MS = 2000L;

    /** 最大音量（50% = 原音量的一半） */
    private static final float MAX_VOLUME = 0.5f;

    // 单例实例（由 ClientProxy 注册事件时创建）
    private static NekoMusicEventHandler instance;

    // 当前正在播放的 BGM 声音实例
    private ISound currentSound = null;

    // Mixin 捕获的声音源名称（用于 SoundSystem.setVolume）
    private String soundSourceName = null;

    // Mixin 捕获的声音信息（用于计算标准化音量）
    private ISound capturedSound = null;
    private SoundPoolEntry capturedEntry = null;
    private SoundCategory capturedCategory = null;

    // 淡入淡出状态
    private boolean fadingIn = false;
    private boolean fadingOut = false;
    private long fadeStartTime = 0;
    private float volumeAtFadeStart = 0.0f;

    // 当前音量（0.0 ~ MAX_VOLUME）
    private float currentVolume = 0.0f;

    // BGM 是否被用户手动关闭（通过切换按钮）
    private boolean bgmMutedByUser = false;

    // 反射缓存
    private static Field sndManagerField;
    private static Field sndSystemField;
    private static Method getNormalizedVolumeMethod;
    // v1.5.11+: setVolume/playing 此前每 tick 反射查找 Method，BGM 播放期间每秒 20 次查找。
    // 现缓存为 static 字段。运行时 sys 实际类型是 SoundManager.SoundSystemStarterThread，
    // 它继承自 paulscode.sound.SoundSystem，setVolume/playing 声明在父类，故针对父类缓存。
    private static Method setVolumeMethod;
    private static Method playingMethod;

    public NekoMusicEventHandler() {
        instance = this;
        initReflectionCache();
    }

    /** 获取单例实例 */
    public static NekoMusicEventHandler getInstance() {
        return instance;
    }

    // ==================== 反射访问 SoundSystem ====================

    private static void initReflectionCache() {
        // paulscode.sound.SoundSystem 是 MC 1.7.10 内置音频库，setVolume/playing 声明在此类。
        // SoundManager.SoundSystemStarterThread 继承自它，运行时 sys 的实际类型是后者。
        // 此处针对父类查找方法，调用时对子类实例 invoke（多态生效）。
        Class<?> soundSystemClass = null;
        try {
            soundSystemClass = Class.forName("paulscode.sound.SoundSystem");
        } catch (ClassNotFoundException e) {
            GTInterestingThing.LOG.warn("[NEKO] 未找到 paulscode.sound.SoundSystem 类，setVolume/playing 将回退到每次反射", e);
        }

        try {
            // 运行时使用 SRG 名称（GTNH 环境中字段名已被反混淆为 SRG 格式）
            // sndManager → field_147694_f, sndSystem → field_148620_e
            // getNormalizedVolume → func_148594_a
            sndManagerField = SoundHandler.class.getDeclaredField("field_147694_f");
            sndManagerField.setAccessible(true);

            sndSystemField = SoundManager.class.getDeclaredField("field_148620_e");
            sndSystemField.setAccessible(true);

            getNormalizedVolumeMethod = SoundManager.class
                .getDeclaredMethod("func_148594_a", ISound.class, SoundPoolEntry.class, SoundCategory.class);
            getNormalizedVolumeMethod.setAccessible(true);

            if (soundSystemClass != null) {
                setVolumeMethod = soundSystemClass.getMethod("setVolume", String.class, float.class);
                playingMethod = soundSystemClass.getMethod("playing", String.class);
            }

            GTInterestingThing.LOG.info("[NEKO] 反射缓存初始化成功");
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[NEKO] 反射缓存初始化失败! 尝试 MCP 名称...", e);
            // 回退到 MCP 名称（开发环境可能使用 MCP 名）
            try {
                sndManagerField = SoundHandler.class.getDeclaredField("sndManager");
                sndManagerField.setAccessible(true);

                sndSystemField = SoundManager.class.getDeclaredField("sndSystem");
                sndSystemField.setAccessible(true);

                getNormalizedVolumeMethod = SoundManager.class
                    .getDeclaredMethod("getNormalizedVolume", ISound.class, SoundPoolEntry.class, SoundCategory.class);
                getNormalizedVolumeMethod.setAccessible(true);

                if (soundSystemClass != null) {
                    setVolumeMethod = soundSystemClass.getMethod("setVolume", String.class, float.class);
                    playingMethod = soundSystemClass.getMethod("playing", String.class);
                }

                GTInterestingThing.LOG.info("[NEKO] 反射缓存初始化成功（MCP 名称回退）");
            } catch (Exception e2) {
                GTInterestingThing.LOG.error("[NEKO] 反射缓存初始化彻底失败! MCP 和 SRG 名称都无法找到字段", e2);
            }
        }
    }

    /**
     * 获取 SoundSystem 实例（通过反射访问 SoundHandler → SoundManager → SoundSystem）
     * <p>
     * SoundManager.SoundSystemStarterThread 是包私有的，无法直接引用，
     * 因此使用 Object 类型并通过反射调用其方法。
     */
    private Object getSoundSystem() {
        try {
            SoundHandler sh = Minecraft.getMinecraft()
                .getSoundHandler();
            SoundManager sm = (SoundManager) sndManagerField.get(sh);
            return sndSystemField.get(sm);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取声音的标准化音量（考虑 MC 音量设置和距离衰减）
     */
    private float getNormalizedVolume() {
        if (capturedSound == null || capturedEntry == null || capturedCategory == null) {
            return 1.0f;
        }
        try {
            SoundHandler sh = Minecraft.getMinecraft()
                .getSoundHandler();
            SoundManager sm = (SoundManager) sndManagerField.get(sh);
            return (float) getNormalizedVolumeMethod.invoke(sm, capturedSound, capturedEntry, capturedCategory);
        } catch (Exception e) {
            return 1.0f;
        }
    }

    /**
     * 设置声音音量（通过 SoundSystem.setVolume）
     * <p>
     * SoundSystemStarterThread 继承自 paulscode.sound.SoundSystem，
     * 使用反射调用 setVolume 和 playing 方法。
     */
    private void setSoundVolume(float volume) {
        if (soundSourceName == null) return;
        Object sys = getSoundSystem();
        if (sys == null) {
            GTInterestingThing.LOG.warn("[NEKO] setSoundVolume: SoundSystem 为 null, 无法设置音量");
            return;
        }
        try {
            float normalizedVol = getNormalizedVolume();
            float finalVolume = volume * normalizedVol;
            // 反射调用 sys.setVolume(sourceName, finalVolume)
            // 优先使用缓存的 Method（针对 paulscode.sound.SoundSystem 父类查找），
            // 缓存缺失时回退到运行时类查找（兼容 SoundSystem 类不可用的环境）
            Method m = setVolumeMethod;
            if (m == null) {
                m = sys.getClass()
                    .getMethod("setVolume", String.class, float.class);
            }
            m.invoke(sys, soundSourceName, finalVolume);
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("[NEKO] setSoundVolume 失败: {}", e.getMessage());
        }
    }

    // ==================== Mixin 回调 ====================

    /**
     * Mixin 回调：当猫猫 BGM 声音被创建时调用
     * <p>
     * 由 NekoSoundManagerMixin 在 SoundManager.playSound() 中调用，
     * 捕获声音源名称和相关信息。
     */
    public static void onSoundCreated(String sourceName, ISound sound, SoundPoolEntry entry, SoundCategory category) {
        if (instance != null) {
            instance.handleSoundCreated(sourceName, sound, entry, category);
        }
    }

    private void handleSoundCreated(String sourceName, ISound sound, SoundPoolEntry entry, SoundCategory category) {
        this.soundSourceName = sourceName;
        this.capturedSound = sound;
        this.capturedEntry = entry;
        this.capturedCategory = category;

        // 开始淡入
        this.fadingIn = true;
        this.fadingOut = false;
        this.fadeStartTime = System.currentTimeMillis();
        this.currentVolume = 0.0f;

        // 立即将音量设为 0（开始淡入的起点）
        setSoundVolume(0.0f);
    }

    // ==================== GUI 事件回调 ====================

    /**
     * GUI 打开时调用（由 NekoVendingMachineGui.build() 调用）
     */
    public static void onGuiOpened() {
        if (instance != null) {
            instance.handleGuiOpened();
        }
    }

    /**
     * GUI 关闭时调用（由 NekoVendingMachineGui.onCloseAction 调用）
     */
    public static void onGuiClosed() {
        if (instance != null) {
            instance.handleGuiClosed();
        }
    }

    private void handleGuiOpened() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        // 如果用户手动关闭了 BGM，不自动播放
        if (this.bgmMutedByUser) {
            GTInterestingThing.LOG.info("[NEKO] handleGuiOpened: BGM 被用户手动关闭，跳过播放");
            return;
        }

        // 停止 VM 原版 BGM（防止叠加）
        VMMusicManager.stopVendingMachineMusic();

        // 停止已有猫猫 BGM（防止叠加）
        if (this.currentSound != null) {
            GTInterestingThing.LOG.info("[NEKO] handleGuiOpened: 停止已有猫猫 BGM（防叠加）");
            stopSound(mc);
        }

        // 播放猫猫 BGM
        try {
            this.currentSound = PositionedSoundRecord.func_147673_a(NEKO_BGM);
            mc.getSoundHandler()
                .playSound(this.currentSound);
            GTInterestingThing.LOG.info("[NEKO] handleGuiOpened: 已请求播放猫猫 BGM，等待 Mixin 回调...");
            // 淡入由 Mixin 回调 onSoundCreated 触发
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("[NEKO] 无法播放猫猫售货机 BGM: {}", e.getMessage());
            this.currentSound = null;
        }
    }

    private void handleGuiClosed() {
        if (this.currentSound == null) return;
        if (this.soundSourceName == null) {
            // 没有捕获到声音 ID，直接停止
            stopSound(Minecraft.getMinecraft());
            return;
        }

        // 开始淡出
        this.fadingIn = false;
        this.fadingOut = true;
        this.fadeStartTime = System.currentTimeMillis();
        this.volumeAtFadeStart = this.currentVolume;
    }

    // ==================== Tick 处理 ====================

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        // 安全检查：如果 GUI 已关闭但 BGM 还在播放且没有在淡出，触发淡出
        boolean isOpen = NekoVendingMachineGui.isNekoGuiOpen;
        if (!isOpen && this.currentSound != null && !this.fadingOut) {
            handleGuiClosed();
        }

        // 处理淡入淡出
        if ((this.fadingIn || this.fadingOut) && this.soundSourceName != null) {
            updateFade();
        } else if (this.currentSound != null && this.soundSourceName != null) {
            // BGM 正在播放但不在淡入淡出中，持续应用音量（响应用户音量调节）
            float effectiveVolume = this.currentVolume * VMConfig.music.music_volume;
            setSoundVolume(effectiveVolume);
        }
    }

    /**
     * 更新淡入淡出音量（每 tick 调用）
     * <p>
     * 通过 SoundSystem.setVolume() 逐帧调整音量，实现平滑淡入淡出。
     * 淡入：0.0 → MAX_VOLUME（2 秒）
     * 淡出：currentVolume → 0.0（2 秒）
     */
    private void updateFade() {
        if (this.soundSourceName == null) {
            this.fadingIn = false;
            this.fadingOut = false;
            return;
        }

        // 检查声音是否还在播放
        Object sys = getSoundSystem();
        if (sys != null) {
            try {
                Method m = playingMethod;
                if (m == null) {
                    m = sys.getClass()
                        .getMethod("playing", String.class);
                }
                boolean isPlaying = (boolean) m.invoke(sys, this.soundSourceName);
                if (!isPlaying) {
                    // 声音已停止（可能播放完毕），清理状态
                    this.fadingIn = false;
                    this.fadingOut = false;
                    this.currentSound = null;
                    this.soundSourceName = null;
                    this.currentVolume = 0.0f;
                    return;
                }
            } catch (Exception e) {
                // 无法检查播放状态，继续处理
            }
        }

        long elapsed = System.currentTimeMillis() - this.fadeStartTime;
        float progress = Math.min((float) elapsed / FADE_TIME_MS, 1.0f);

        if (this.fadingIn) {
            this.currentVolume = progress * MAX_VOLUME;
            if (progress >= 1.0f) {
                this.fadingIn = false;
                this.currentVolume = MAX_VOLUME;
            }
        } else if (this.fadingOut) {
            this.currentVolume = (1.0f - progress) * this.volumeAtFadeStart;
            if (progress >= 1.0f) {
                this.fadingOut = false;
                this.currentVolume = 0.0f;
                stopSound(Minecraft.getMinecraft());
                return;
            }
        }

        // 应用音量（考虑用户配置的音量倍率）
        float effectiveVolume = this.currentVolume * VMConfig.music.music_volume;
        setSoundVolume(effectiveVolume);
    }

    // ==================== 声音控制 ====================

    /**
     * 停止声音
     */
    private void stopSound(Minecraft mc) {
        if (this.currentSound != null) {
            try {
                mc.getSoundHandler()
                    .stopSound(this.currentSound);
            } catch (Exception e) {
                // 静默忽略
            }
        }
        this.currentSound = null;
        this.soundSourceName = null;
        this.capturedSound = null;
        this.capturedEntry = null;
        this.capturedCategory = null;
        this.currentVolume = 0.0f;
        this.fadingIn = false;
        this.fadingOut = false;
    }

    /**
     * 外部调用：立即停止 BGM（用于 BGM 切换按钮关闭时）
     */
    public void forceStopBGM() {
        this.fadingIn = false;
        this.fadingOut = false;
        this.bgmMutedByUser = true;
        stopSound(Minecraft.getMinecraft());
    }

    /**
     * 外部调用：启动 BGM（用于 BGM 切换按钮开启时）
     */
    public void forceStartBGM() {
        this.bgmMutedByUser = false;
        handleGuiOpened();
    }

    /**
     * 检查 BGM 是否正在播放
     */
    public boolean isPlaying() {
        return this.currentSound != null;
    }

    /**
     * 检查 BGM 是否被用户手动关闭
     */
    public boolean isMutedByUser() {
        return this.bgmMutedByUser;
    }

    /**
     * 获取当前音量
     */
    public float getCurrentVolume() {
        return this.currentVolume;
    }
}
