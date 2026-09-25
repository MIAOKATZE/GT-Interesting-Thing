package com.miaokatze.gtit.gui.pocket;

import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 风格 <b>C2</b> 的<b>贴图契约表</b>（纯元数据：名字、尺寸、9-slice 边距、是否平铺）。
 * <p>
 * <b>单源</b>：本表逐行对应 {@code plan/assest/pocket-ui-mockups-2.html} 第 <b>355–366</b> 行
 * （R75 钉死的"C2 贴图契约单源"，账本与简报只引用不重抄）。Java 侧也照同一纪律：
 * 名字/尺寸/边距<b>只写在这里一次</b>，注册（{@link PocketGuiTextures}）与查表
 * （{@link #specOf(String)}、{@link #resourcePathOf(String)}）都读这一张表——
 * 比照物品侧 {@code ItemNekoDimensionPocket#ICON_SUFFIXES}（注册与选帧共用一张表）的同一条口径。
 * <p>
 * <b>为什么与 {@link PocketGuiTextures} 分成两个类</b>：{@code UITexture} 的构造会触达 MUI2 的
 * 纹理表与 fastutil，而本仓的零依赖回归套件跑在没有那些运行期依赖的 JVM 里（实测
 * {@code NoClassDefFoundError: it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap}）。
 * R75 要求"契约名与尺寸必须逐字对账"，所以把<b>可机检的那一半</b>（字符串与整数）放这里，
 * 把<b>只有游戏内才成立的那一半</b>（{@code UITexture} 注册）放 {@link PocketGuiTextures}。
 * 两张表之间没有第二份数据：后者只读前者。
 * <p>
 * <b>文件名 = 契约名本身</b>（如 {@code gtit:textures/gui/pocket/POCKET_C2_panel.png}）：
 * 契约里给出的就是这一串 token，取同一名可让"契约 ↔ 兄弟片落地文件"逐字对账；
 * 贴图片若要改名，改的是 {@link #FOLDER} 与本表，不会散到六个装配点。
 * <p>
 * ★<b>表尾的派生行是本仓对契约的补全，不是契约原文</b>：{@code POCKET_C2_btn_pressed}（HTML 的
 * btn 那一行写"未按下 / 按下 2 张"却只给了一个 token）、{@code POCKET_C2_progress}（契约没有进度件
 * 的 tex 行，只在 HTML 261 行给了 108×18 的槽位与 {@code prog} 配色）、以及 S1 的 5 行
 * {@code POCKET_C2_upg_*}（升级格灰化占位图案，契约同样没有 tex 行）。全部按同一命名法派生，
 * 已在回执里点名，等主代理与贴图片对账（对不上时只改这些行）。
 */
public final class PocketGuiTextureContract {

    /** 资源域（与全仓一致）。 */
    public static final String MOD_ID = GTInterestingThing.MODID;
    /** 贴图目录（{@code textures/} 之后的部分；兄弟片的落地目录）。 */
    public static final String FOLDER = "gui/pocket";
    /** 贴图扩展名（对账落地文件时用它拼出全名）。 */
    public static final String SUFFIX = ".png";

    /** 槽位类贴图的边距上限（R75：超过 3 就分不出空槽/有货槽 ⇒ 撞 R73②）。 */
    public static final int MAX_SLOT_SLICE_MARGIN = 3;

    /** 契约行（不可变值对象）。{@code sliceMargin < 0} = 非 9-slice（1:1 贴或平铺）。 */
    public static final class Spec {

        public final String name;
        public final int width;
        public final int height;
        public final int sliceMargin;
        public final boolean tiled;

        Spec(String name, int width, int height, int sliceMargin, boolean tiled) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.sliceMargin = sliceMargin;
            this.tiled = tiled;
        }
    }

    // ---- 契约表：顺序与 HTML 355–366 行逐字相同 ----
    private static final Spec[] CONTRACT = { new Spec("POCKET_C2_cloth", 32, 32, -1, true),
        new Spec("POCKET_C2_panel", 64, 64, 10, false), new Spec("POCKET_C2_corner", 14, 14, -1, false),
        new Spec("POCKET_C2_rivet", 6, 6, -1, false), new Spec("POCKET_C2_slot", 18, 18, 3, false),
        new Spec("POCKET_C2_slot_tall", 18, 18, 3, false), new Spec("POCKET_C2_coinbar", 88, 18, 4, false),
        new Spec("POCKET_C2_btn", 88, 18, 4, false), new Spec("POCKET_C2_scrollbar", 12, 12, 3, false),
        new Spec("POCKET_C2_bindbtn", 106, 18, 4, false), new Spec("POCKET_C2_rope", 8, 8, -1, true),
        // 派生行（见类 javadoc 的★）：按钮按下态
        new Spec("POCKET_C2_btn_pressed", 88, 18, 4, false),
        // ★第 13 行同样是补全而非契约原文（HTML 355-366 的 tex 数组没有进度件）：蒸馏横向进度条材质。
        // 高度 = 两根 18（HTML 261 行那条 role:'prog' kind:'bar' 的 108×18 槽）叠成一列，
        // 上半空槽、下半满条 —— 这是 MUI2 {@code ProgressWidget.texture(单张堆叠图, imageSize)}
        // 自己规定的排布（{@code getSubArea(0,0,1,0.5)} / {@code (0,0.5,1,1)}），不是本仓的偏好。
        // 非 9-slice：按 u 裁切时任何横向特征都会退化成"快满才出现"的伪影（生成脚本把"逐行横向均匀"
        // 钉成机检，见 tools/artgen_catalog/neko_dimension_pocket/gen_pocket_gui_progress.py）。
        new Spec("POCKET_C2_progress", 108, 36, -1, false),
        // ★第 14-18 行同样是补全而非契约原文（S1）：底部右段行 3 的 5 个升级格未放插件时的
        // 灰化图案占位。18×18 = 槽位栅格画布（与 POCKET_C2_slot 同尺寸），图案画在 16×16 语义区
        // （(1,1) 起，画布外圈 1px 让位）⇒ 16×16 的插件图标放入后逐像素遮盖图案。
        // 非 9-slice（1:1 贴，不平铺）；生成器 tools/artgen_catalog/pocket_gui/gen_pocket_gui_upgrades.py
        // （灰阶 = palette 冷钢三档 st_lo/st_mid/steel_lip，正文零字面 RGB）。
        new Spec("POCKET_C2_upg_capacity", 18, 18, -1, false), new Spec("POCKET_C2_upg_stack", 18, 18, -1, false),
        new Spec("POCKET_C2_upg_magnet", 18, 18, -1, false), new Spec("POCKET_C2_upg_channel", 18, 18, -1, false),
        new Spec("POCKET_C2_upg_distill", 18, 18, -1, false) };

    private PocketGuiTextureContract() {}

    /** 契约行数（= 应交付的贴图张数，含派生的按下态一张）。 */
    public static int contractSize() {
        return CONTRACT.length;
    }

    /** 按契约名取行；不认识的名字返回 {@code null}（调用方宁可显式处理，不要吞成默认值）。 */
    public static Spec specOf(String name) {
        if (name == null) {
            return null;
        }
        for (Spec spec : CONTRACT) {
            if (spec.name.equals(name)) {
                return spec;
            }
        }
        return null;
    }

    /** 某契约名的 9-slice 边距（非 9-slice 返回 {@code -1}）。 */
    public static int sliceMarginOf(String name) {
        final Spec spec = specOf(name);
        return spec == null ? -1 : spec.sliceMargin;
    }

    /** 某契约名的原始宽度（查不到返回 0）。 */
    public static int widthOf(String name) {
        final Spec spec = specOf(name);
        return spec == null ? 0 : spec.width;
    }

    /** 某契约名的原始高度（查不到返回 0）。 */
    public static int heightOf(String name) {
        final Spec spec = specOf(name);
        return spec == null ? 0 : spec.height;
    }

    /** 某契约名是否为 9-slice。 */
    public static boolean isNineSlice(String name) {
        return sliceMarginOf(name) >= 0;
    }

    /** 契约名清单（顺序 = 表顺序；{@link PocketGuiTextures} 逐行注册就按它走）。 */
    public static String[] names() {
        final String[] out = new String[CONTRACT.length];
        for (int index = 0; index < CONTRACT.length; index++) {
            out[index] = CONTRACT[index].name;
        }
        return out;
    }

    /** 资源路径（{@code textures/} 之后、不含扩展名）；契约名即文件基名。 */
    public static String resourcePathOf(String name) {
        return FOLDER + "/" + name;
    }

    /** 落地文件相对路径（贴图片对账用：{@code src/main/resources/assets/<域>/textures/<路径>.png}）。 */
    public static String assetPathOf(String name) {
        return "src/main/resources/assets/" + MOD_ID + "/textures/" + resourcePathOf(name) + SUFFIX;
    }

    /**
     * 表的自校验（{@link PocketGuiTextures} 注册前调用，回归套件也直调）。
     * <p>
     * 三条都是"写错就一定炸"的形状检查：名字重复、边距吃掉图心、槽位边距超过 R75 上限。
     * 槽位那一条尤其要紧：边距一旦写成 4，18px 的槽中心只剩 10px 且描边消失，
     * 空槽与有货槽就看不出差别——那正是 R73② 明令不许发生的退化。
     */
    public static void validate() {
        for (int a = 0; a < CONTRACT.length; a++) {
            final Spec left = CONTRACT[a];
            if (left.width <= 0 || left.height <= 0) {
                throw new IllegalStateException("[pocket] C2 契约行尺寸非法: " + left.name);
            }
            if (left.sliceMargin >= 0 && (left.width <= 2 * left.sliceMargin || left.height <= 2 * left.sliceMargin)) {
                throw new IllegalStateException("[pocket] C2 契约行的 9-slice 边距吃掉整张图: " + left.name);
            }
            if (isSlotTexture(left.name) && left.sliceMargin > MAX_SLOT_SLICE_MARGIN) {
                throw new IllegalStateException(
                    "[pocket] C2 槽位贴图边距超过 R75 上限 " + MAX_SLOT_SLICE_MARGIN + ": " + left.name);
            }
            for (int b = a + 1; b < CONTRACT.length; b++) {
                if (CONTRACT[b].name.equals(left.name)) {
                    throw new IllegalStateException("[pocket] C2 契约表里名字重复: " + left.name);
                }
            }
        }
    }

    /** 槽位类贴图（边距受 R75 的 ≤3 约束的那两张）。 */
    public static boolean isSlotTexture(String name) {
        return "POCKET_C2_slot".equals(name) || "POCKET_C2_slot_tall".equals(name);
    }
}
