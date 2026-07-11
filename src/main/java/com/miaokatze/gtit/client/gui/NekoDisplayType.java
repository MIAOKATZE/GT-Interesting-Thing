package com.miaokatze.gtit.client.gui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Icon;
import com.cleanroommc.modularui.drawable.UITexture;

/**
 * 交易显示模式枚举
 * <p>
 * 完美复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.DisplayType} 枚举。
 * 包含 TILE（平铺）和 LIST（列表）两种显示模式，
 * 每种模式关联 {@link NekoGuiTextures} 中对应的纹理图标。
 * <p>
 * 本地化 key 从 {@code vendingmachine.gui.display_mode_*} 迁移到 {@code gtit.gui.display_mode_*}。
 */
public enum NekoDisplayType {

    /** 平铺模式 - 以方块按钮形式展示交易 */
    TILE("tile", NekoGuiTextures.MODE_TILE),

    /** 列表模式 - 以行列表形式展示交易 */
    LIST("list", NekoGuiTextures.MODE_LIST);

    /** 显示模式的内部标识名 */
    private final String type;

    /** 显示模式对应的纹理图标 */
    private final Icon texture;

    /**
     * 构造一个显示模式
     *
     * @param type    内部标识名（用于本地化 key 拼接）
     * @param texture 对应的 UITexture 纹理（将转换为 Icon）
     */
    NekoDisplayType(String type, UITexture texture) {
        this.type = type;
        this.texture = texture.asIcon();
    }

    /**
     * 获取显示模式的本地化名称
     * <p>
     * 本地化 key 格式：{@code gtit.gui.display_mode_<type>}
     *
     * @return 本地化后的显示模式名称
     */
    public String getLocalizedName() {
        return IKey.lang("gtit.gui.display_mode_" + this.type)
            .toString();
    }

    /**
     * 获取显示模式对应的纹理图标
     *
     * @return 纹理图标
     */
    public Icon getTexture() {
        return this.texture;
    }
}
