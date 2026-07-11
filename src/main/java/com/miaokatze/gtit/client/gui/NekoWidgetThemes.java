package com.miaokatze.gtit.client.gui;

import com.cleanroommc.modularui.api.IThemeApi;
import com.cleanroommc.modularui.theme.TextFieldTheme;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeKey;
import com.cleanroommc.modularui.utils.Color;

/**
 * 猫猫售货机 GUI Widget 主题注册
 * <p>
 * 完美复刻 VM mod 的 {@code com.cubefury.vendingmachine.gui.WidgetThemes}，
 * 使用 {@link NekoGuiTextures} 的纹理资源。
 * <p>
 * 共 3 个主题：侧边面板背景、搜索栏背景、交易按钮主题。
 */
public final class NekoWidgetThemes {

    public static void init() {}

    private static final IThemeApi themeApi = IThemeApi.get();

    /** 侧边面板背景主题（使用 SIDE_PANEL_BACKGROUND 纹理） */
    public static final WidgetThemeKey<WidgetTheme> BACKGROUND_SIDEPANEL = themeApi
        .widgetThemeKeyBuilder("background_side_panel", WidgetTheme.class)
        .defaultTheme(
            new WidgetTheme(0, 0, NekoGuiTextures.SIDE_PANEL_BACKGROUND, Color.WHITE.main, 0xFF404040, false, 0))
        .defaultHoverTheme(null)
        .register();

    /** 搜索栏背景主题（使用 TEXT_FIELD_BACKGROUND 纹理） */
    public static final WidgetThemeKey<TextFieldTheme> BACKGROUND_SEARCH_BAR = themeApi
        .widgetThemeKeyBuilder("background_search_bar", TextFieldTheme.class)
        .defaultTheme(
            new TextFieldTheme(
                0,
                0,
                NekoGuiTextures.TEXT_FIELD_BACKGROUND,
                Color.WHITE.main,
                0xFF404040,
                false,
                0,
                0,
                0xFF404040))
        .defaultHoverTheme(null)
        .register();

    /** 交易按钮主题（无背景纹理，与 VM 一致） */
    public static final WidgetThemeKey<WidgetTheme> THEME_TRADE_BUTTON = themeApi
        .widgetThemeKeyBuilder("background_tile_trade_button", WidgetTheme.class)
        .defaultTheme(new WidgetTheme(0, 0, null, Color.WHITE.main, 0xFF404040, false, 0))
        .defaultHoverTheme(null)
        .register();

    private NekoWidgetThemes() {}
}
