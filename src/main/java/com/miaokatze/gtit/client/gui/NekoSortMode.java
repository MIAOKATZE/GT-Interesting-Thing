package com.miaokatze.gtit.client.gui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Icon;
import com.cleanroommc.modularui.drawable.UITexture;

/**
 * 交易排序模式枚举
 * <p>
 * 完美复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.SortMode} 枚举。
 * 包含 SMART（智能排序）和 ALPHABET（字母排序）两种模式，
 * 每种模式关联 {@link NekoGuiTextures} 中对应的纹理图标。
 * <p>
 * 本地化 key 从 {@code vendingmachine.gui.display_sort_*} 迁移到 {@code gtit.gui.display_sort_*}。
 */
public enum NekoSortMode {

    /** 智能排序 - 根据交易状态动态排序 */
    SMART("smart", NekoGuiTextures.SORT_SMART),

    /** 字母排序 - 按物品名称字母顺序排序 */
    ALPHABET("alphabet", NekoGuiTextures.SORT_ALPHABET);

    /** 排序模式的内部标识名 */
    private final String mode;

    /** 排序模式对应的纹理图标 */
    private final Icon texture;

    /**
     * 构造一个排序模式
     *
     * @param mode    内部标识名（用于本地化 key 拼接）
     * @param texture 对应的 UITexture 纹理（将转换为 Icon）
     */
    NekoSortMode(String mode, UITexture texture) {
        this.mode = mode;
        this.texture = texture.asIcon();
    }

    /**
     * 获取排序模式的本地化名称
     * <p>
     * 本地化 key 格式：{@code gtit.gui.display_sort_<mode>}
     *
     * @return 本地化后的排序模式名称
     */
    public String getLocalizedName() {
        return IKey.lang("gtit.gui.display_sort_" + this.mode)
            .toString();
    }

    /**
     * 获取排序模式对应的纹理图标
     *
     * @return 纹理图标
     */
    public Icon getTexture() {
        return this.texture;
    }
}
