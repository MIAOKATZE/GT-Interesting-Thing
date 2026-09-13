package com.miaokatze.gtit.client.gui.reincarnation;

/**
 * 周目轮回 GUI 文字/GL 色 token 单源（v1.8.8，契约 §1 色板「Java」行 + §4-4 按钮文字色提案）。
 * <p>
 * ARGB 记法；贴图像素色不落此处（以 PNG 为权威），本类只收 Java 直绘消费的色值，
 * 替代散落在旧渲染层内的魔数（0xFFFFFF / 0x777788 / 0x555555 等，§5 新旧色映射表）。
 */
public final class ReincarnationGuiPalette {

    /** 暖白文字（banner/消息/标题/角标数字），替代旧 0xFFFFFF */
    public static final int TEXT_WARM = 0xFFFFF8E7;
    /** footer 提示暖灰，替代旧 0x777788；v1.8.12 起亦作列头锁定列提亮色（原 TEXT_LOCKED_WARM 并入本 token） */
    public static final int TEXT_MUTED_WARM = 0xFFA89F8A;
    /** 已解锁列名猫金（与旧 COLUMN_UNLOCKED_COLOR 同值，§5 标注不变） */
    public static final int TEXT_ACCENT = 0xFFFFAA00;

    /** 按钮 disabled 文字（无阴影直绘；契约 §4-4 提案 0xA0A0A0） */
    public static final int BUTTON_TEXT_DISABLED = 0xFFA0A0A0;
    /** 按钮 hover 文字（带阴影；契约 §4-4 提案） */
    public static final int BUTTON_TEXT_HOVER = 0xFFFFF8E7;
    /** 按钮 normal 文字（带阴影；契约 §4-4 提案 0xFFE0E0E0） */
    public static final int BUTTON_TEXT_NORMAL = 0xFFE0E0E0;

    private ReincarnationGuiPalette() {}
}
