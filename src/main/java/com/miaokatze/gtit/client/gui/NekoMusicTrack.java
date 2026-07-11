package com.miaokatze.gtit.client.gui;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Icon;
import com.cleanroommc.modularui.drawable.UITexture;

/**
 * 背景音乐曲目枚举
 * <p>
 * 完美复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.MusicTrack} 枚举。
 * NONE 表示不播放音乐（显示关闭图标），LUNCH_BREAK 表示午休时间曲目（显示开启图标）。
 * <p>
 * 音频资源路径从 {@code vendingmachine:track.lunch_break} 迁移到 {@code gtit:track.lunch_break}。
 * 本地化 key 从 {@code vendingmachine.gui.display_track_*} 迁移到 {@code gtit.gui.display_track_*}。
 */
public enum NekoMusicTrack {

    /** 无音乐 - 显示音频关闭图标 */
    NONE("none", null, NekoGuiTextures.AUDIO_OFF),

    /** 午休时间曲目 - 显示音频开启图标 */
    LUNCH_BREAK("lunch_break", new ResourceLocation("gtit", "track.lunch_break"), NekoGuiTextures.AUDIO_ON);

    /** 曲目的内部标识名 */
    private final String name;

    /** 曲目对应的音频资源路径（NONE 时为 null） */
    private final ResourceLocation sound;

    /** 曲目对应的纹理图标 */
    private final Icon texture;

    /**
     * 构造一个音乐曲目
     *
     * @param name    内部标识名（用于本地化 key 拼接）
     * @param sound   音频资源路径（可为 null，表示无音频）
     * @param texture 对应的 UITexture 纹理（将转换为 Icon）
     */
    NekoMusicTrack(String name, @Nullable ResourceLocation sound, UITexture texture) {
        this.name = name;
        this.sound = sound;
        this.texture = texture.asIcon();
    }

    /**
     * 获取曲目的本地化名称
     * <p>
     * 本地化 key 格式：{@code gtit.gui.display_track_<name>}
     *
     * @return 本地化后的曲目名称
     */
    public String getLocalizedName() {
        return IKey.lang("gtit.gui.display_track_" + this.name)
            .toString();
    }

    /**
     * 获取曲目的音频资源路径
     *
     * @return 音频资源路径，NONE 时返回 null
     */
    @Nullable
    public ResourceLocation getSoundLoc() {
        return sound;
    }

    /**
     * 获取曲目对应的纹理图标
     *
     * @return 纹理图标
     */
    public Icon getTexture() {
        return this.texture;
    }
}
