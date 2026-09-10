package com.miaokatze.gtit.client.gui.reincarnation;

import net.minecraft.util.ResourceLocation;

/**
 * 周目轮回 GUI 贴图资源与真实尺寸唯一权威（v1.8.8，契约
 * {@code plan/gui-texture-v19/texture-list.md} §3/§4）。
 * <p>
 * 两张贴图：{@link #RL_PANEL} 整幅面板（320×258，Tessellator 直绘——256 分母假设
 * 覆盖不了 320 宽）与 {@link #RL_ATLAS} 小件图集（256×256，整数 UV 可走
 * {@code drawTexturedModalRect} 的 256 分母假设）。UV/尺寸常量一经定稿不得口头变更，
 * 只允许随契约整版重排（单一权威 = 契约文档与生成脚本，本类是 Java 消费单源）。
 * <p>
 * 侧与加载：纯常量类，零服务端引用，仅 client/gui 包（物理客户端）消费。
 */
public final class ReincarnationGuiTextures {

    /** 面板整幅：深炭底 + 金纹雕边框 + 左右猫耳 + 底角金括弧 + 横幅/标题带 + 金分隔线 + footer 带 + 烘焙爪印一对 */
    public static final ResourceLocation RL_PANEL = new ResourceLocation(
        "gtit",
        "textures/gui/reincarnation/panel.png");
    /** 面板真实宽（像素；Tessellator 直绘 UV 分母 u = px/320.0F） */
    public static final int PANEL_W = 320;
    /** 面板真实高（像素；Tessellator 直绘 UV 分母 v = py/258.0F） */
    public static final int PANEL_H = 258;

    /** 小件图集（256×256；占用 x∈[0,149] y∈[0,151]，其余全透明，由生成脚本自校验断言） */
    public static final ResourceLocation RL_ATLAS = new ResourceLocation(
        "gtit",
        "textures/gui/reincarnation/atlas.png");
    /** 图集边长（像素；drawTexturedModalRect 的 256 分母假设） */
    public static final int ATLAS_SIZE = 256;

    // ==================== 图集 UV（整数偏移；契约 §3 家族表 / §4 常量契约） ====================

    /** slot_frame 金沿凹格 18×18 @(0,0)：外壳行/物品格/玩家背包/快捷栏共用，1:1 无拉伸 */
    public static final int SLOT_FRAME_U = 0, SLOT_FRAME_V = 0;
    /** slot_frame_locked 灰化凹格 18×18 @(20,0)：锁定列/未解锁行（取代旧半透明叠罩） */
    public static final int SLOT_LOCKED_U = 20, SLOT_LOCKED_V = 0;
    /** chip_count 计数角标底衬 @(40,0)：半透明（fill α0xC8 / rim α0x99），消费须显式成对 blend */
    public static final int CHIP_U = 40, CHIP_V = 0;
    /** chip_count 真实尺寸（1:1 叠于计数文字之下） */
    public static final int CHIP_W = 12, CHIP_H = 10;
    /** label_strip 条衬 @(54,0)：上发丝金线/主体/下阴影，nine-slice slice=4 */
    public static final int STRIP_U = 54, STRIP_V = 0;
    /** label_strip 真实尺寸与切片（9-slice 目标 = (GRID_X, LABELS_Y, 298, 10)） */
    public static final int STRIP_W = 20, STRIP_H = 10, STRIP_SLICE = 4;
    /** divider_gold 可复用分隔线件 32×2 @(76,0)：保留件（面板内分隔线已烘焙，接线非必需） */
    public static final int DIVIDER_U = 76, DIVIDER_V = 0;
    /** paw_watermark 独立爪印件 20×20 @(76,4)：保留件（面板已烘焙等价爪印，接线非必需），含 α<255 像素 */
    public static final int PAWMARK_U = 76, PAWMARK_V = 4;

    // ==================== 按钮三态（整件纵向平铺，禁拉伸；态序 normal/hover/disabled） ====================

    /** 解锁按钮几何（= ReincarnationLayout.UNLOCK_BUTTON_WIDTH × SLOT_SIZE，宽高即布局定值） */
    public static final int BTN_UNLOCK_W = 136, BTN_UNLOCK_H = 18;
    /** 解锁按钮三态 v：normal @(0,48) / hover @(0,66) / disabled @(0,84) */
    public static final int BTN_UNLOCK_NORMAL_V = 48, BTN_UNLOCK_HOVER_V = 66, BTN_UNLOCK_DISABLED_V = 84;
    /** 确认按钮几何（= ReincarnationLayout.CONFIRM_BUTTON_WIDTH × 16） */
    public static final int BTN_CONFIRM_W = 150, BTN_CONFIRM_H = 16;
    /** 确认按钮三态 v：normal @(0,104) / hover @(0,120) / disabled @(0,136)；hover 描边暗粉（危险动作语义） */
    public static final int BTN_CONFIRM_NORMAL_V = 104, BTN_CONFIRM_HOVER_V = 120, BTN_CONFIRM_DISABLED_V = 136;

    private ReincarnationGuiTextures() {}
}
