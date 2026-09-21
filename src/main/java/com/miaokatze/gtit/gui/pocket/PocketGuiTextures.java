package com.miaokatze.gtit.gui.pocket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.cleanroommc.modularui.drawable.UITexture;

/**
 * 猫猫次元口袋面板（风格 <b>C2</b>）的<b>贴图注册表</b>。
 * <p>
 * <b>本类不含任何契约数据</b>：名字、尺寸、9-slice 边距、平铺一律读
 * {@link PocketGuiTextureContract}（那才是 HTML 355–366 行那份契约的 Java 侧单源）。
 * 分成两个类的理由写在那一类的 javadoc 里（{@code UITexture} 构造触达 MUI2 纹理表，
 * 纯 JVM 的回归套件加载不了；而 R75 要求契约本身可机检）。
 * <p>
 * <b>缺图不崩</b>：{@link UITexture} 指向未落地的资源时该处只画空（MUI2 的既有行为，
 * 也是本仓"材质可晚于代码落地"的安全边界，pocket-plan §5 末条）⇒ 本片可以与贴图片
 * （{@code zcw-drawer}，只写 {@code tools/artgen_catalog/pocket_gui/**} 与
 * {@code src/main/resources/assets/gtit/textures/gui/pocket/**}）并行，
 * 贴图暂时不存在不算缺陷。
 * <p>
 * ★<b>{@link #SCROLLBAR} 目前只注册、无绘制点</b>：R74② 删掉的第四列是唯一的
 * {@code ScrollWidget} 使用者，R75 又裁定绑定列表改由 tooltip 承载（超出部分显式截断）。
 * 保留在这一张表里是因为它属于贴图片的交付面（图集必须齐），且日后真要用可滚列表时
 * 不得再"现编一个名字"。<b>若真引入 {@code ScrollWidget}，必须连主题的独立 scrollbar 子项
 * 一起覆写</b>——本仓踩过：只覆写 panel/textField 会让条不可见，表现为"没有滚动条"的假 bug。
 * <p>
 * 纯 common 件：只建 {@link UITexture}（MUI2 的 drawable 值对象，双端都可构造，
 * 真正的 GL 绑定发生在客户端绘制时），不含任何 {@code @SideOnly} 类型。
 */
public final class PocketGuiTextures {

    /** 资源域（转发给契约表，避免两处真相）。 */
    public static final String MOD_ID = PocketGuiTextureContract.MOD_ID;
    /** 贴图目录（同上）。 */
    public static final String FOLDER = PocketGuiTextureContract.FOLDER;

    /** 契约名 → 已注册贴图（顺序 = 契约表顺序）。 */
    private static final Map<String, UITexture> TEXTURES = build();

    // ---- 面板与装饰骨架 ----
    /** 布纹底（32×32 平铺铺满整块面板）。 */
    public static final UITexture CLOTH = TEXTURES.get("POCKET_C2_cloth");
    /** 面板框（64×64，9-slice N=10；中心 44 ⇒ 面板 {@code WIDTH×HEIGHT}（R81④ 后 398×360）远大于 2N+1）。 */
    public static final UITexture PANEL = TEXTURES.get("POCKET_C2_panel");
    /** 铜包角（14×14，<b>1:1 贴</b>四角各一次，不得拉伸）。 */
    public static final UITexture CORNER = TEXTURES.get("POCKET_C2_corner");
    /** 铆钉（6×6，同一个图标多点位复用；1:1 贴）。 */
    public static final UITexture RIVET = TEXTURES.get("POCKET_C2_rivet");
    /** 绳结虚线（8×8 平铺出横/竖分隔线）。 */
    public static final UITexture ROPE = TEXTURES.get("POCKET_C2_rope");

    // ---- 控件底 ----
    /** 金属凹槽槽位底（18×18，N=3 ⇒ 中心 12×12；★边距不得超过 3，见契约表）。 */
    public static final UITexture SLOT = TEXTURES.get("POCKET_C2_slot");
    /** 流体槽金属圈底（18×18，N=3；用于 6 个流体槽本体）。 */
    public static final UITexture SLOT_TALL = TEXTURES.get("POCKET_C2_slot_tall");
    /** 币值条（88×18，N=4）。 */
    public static final UITexture COIN_BAR = TEXTURES.get("POCKET_C2_coinbar");
    /** 通道按钮 未按下（88×18，N=4）。 */
    public static final UITexture BUTTON = TEXTURES.get("POCKET_C2_btn");
    /** 通道按钮 按下（88×18，N=4；★派生名，见契约表 javadoc）。 */
    public static final UITexture BUTTON_PRESSED = TEXTURES.get("POCKET_C2_btn_pressed");
    /** 绑定按钮（106×18，N=4）。 */
    public static final UITexture BIND_BUTTON = TEXTURES.get("POCKET_C2_bindbtn");
    /** ScrollWidget 的独立 scrollbar 子项（12×12，N=3）；★当前只注册未绘制，见类 javadoc。 */
    public static final UITexture SCROLLBAR = TEXTURES.get("POCKET_C2_scrollbar");

    private PocketGuiTextures() {}

    /** 按<b>契约名</b>（HTML 里那一列 token）取贴图；不认识的名字返回 {@code null}。 */
    public static UITexture byContractName(String contractName) {
        return contractName == null ? null : TEXTURES.get(contractName);
    }

    /** 契约名集合（对账用；数据本体住在 {@link PocketGuiTextureContract}）。 */
    public static Set<String> contractNames() {
        return Collections.unmodifiableSet(TEXTURES.keySet());
    }

    /** 某契约名的 9-slice 边距（非 9-slice 返回 {@code -1}）；转发给契约表。 */
    public static int sliceMarginOf(String contractName) {
        return PocketGuiTextureContract.sliceMarginOf(contractName);
    }

    /** 某契约名的原始宽度（转发给契约表：几何不得读贴图类）。 */
    public static int widthOf(String contractName) {
        return PocketGuiTextureContract.widthOf(contractName);
    }

    /** 某契约名的原始高度（同上）。 */
    public static int heightOf(String contractName) {
        return PocketGuiTextureContract.heightOf(contractName);
    }

    /** 契约表行数（= 兄弟片应交付的贴图张数，含派生的按下态一张）。 */
    public static int contractSize() {
        return PocketGuiTextureContract.contractSize();
    }

    /**
     * 逐行按契约建表。
     * <p>
     * ★"注册与查表共用同一份常量"的落点：这里<b>只读</b> {@link PocketGuiTextureContract}，
     * 任何一行建不出纹理（名字写错、builder 被误改）都在类初始化当场抛，
     * 而不是留一个 {@code null} 到某个格子不画。
     */
    private static Map<String, UITexture> build() {
        PocketGuiTextureContract.validate();
        final Map<String, UITexture> built = new LinkedHashMap<>();
        for (String name : PocketGuiTextureContract.names()) {
            if (built.containsKey(name)) {
                throw new IllegalStateException("[pocket] C2 契约表里名字重复: " + name);
            }
            final UITexture texture = toTexture(PocketGuiTextureContract.specOf(name));
            if (texture == null) {
                throw new IllegalStateException("[pocket] C2 契约行未能建出贴图: " + name);
            }
            built.put(name, texture);
        }
        return Collections.unmodifiableMap(built);
    }

    /**
     * 由契约行建 {@code UITexture}（路径、尺寸、边距全部取自契约行，本方法不做任何决定）。
     * <p>
     * 全部 {@code nonOpaque()}：面板底与槽位底都要让<b>底下/上面</b>的东西透出来
     * （布料平铺在框之下、物品图标在槽底之上），按不透明画会把下层整个盖掉。
     */
    private static UITexture toTexture(PocketGuiTextureContract.Spec spec) {
        if (spec == null) {
            return null;
        }
        final UITexture.Builder builder = UITexture.builder()
            .location(MOD_ID, PocketGuiTextureContract.resourcePathOf(spec.name))
            .imageSize(spec.width, spec.height)
            .nonOpaque();
        if (spec.sliceMargin >= 0) {
            // 9-slice：契约给的边距直接进 adaptable（MUI2 要求控件尺寸 >= 2N+1，几何侧已满足）
            builder.adaptable(spec.sliceMargin);
        } else if (spec.tiled) {
            // 平铺（cloth / rope）：一张小图铺满大块区域
            builder.tiled(spec.width, spec.height);
        }
        // 剩下的 non-tiled 非 9-slice 行（corner / rivet）保持原尺寸 1:1，不注册成可拉伸
        return builder.name("pocket_" + spec.name.toLowerCase(java.util.Locale.ROOT))
            .build();
    }
}
