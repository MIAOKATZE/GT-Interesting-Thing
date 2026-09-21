package com.miaokatze.gtit.gui.pocket;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.ParentWidget;

/**
 * 面板的 C2 <b>装饰层</b>（铜包角 + 铆钉 + 布纹底 + 木框铜线）。
 * <p>
 * <b>必须是面板的第一个 child</b>（{@link NekoPocketPanel#assemble()} 里排第一）：MUI2 按
 * child 顺序绘制，装饰排最底才不会盖住槽位与文本。本层<b>不接受任何点击</b>
 * （纯 {@code ParentWidget}，没有 {@code Interactable} ⇒ 不会把点击吃掉，
 * 也就不会出现"ButtonWidget 返 false 仍 ACCEPT 而穿透到下层槽"那一族老问题）。
 * <p>
 * <b>四类贴法的分工</b>（契约见 {@code plan/assest/pocket-ui-mockups-2.html} 第 355–366 行，
 * 名称与尺寸一律经 {@link PocketGuiTextures} 单源取，本文件不写路径也不写字面尺寸）：
 * <ul>
 * <li>{@code cloth}（32×32）：<b>平铺</b>铺满整块面板（一张小图铺满
 * {@code NekoPocketPanel.WIDTH × HEIGHT}，★本文件不写字面面板宽，省内存）；</li>
 * <li>{@code panel}（64×64，N=10）：<b>9-slice</b> 拉伸成木框 + 内圈铜线（面板远大于 2N+1 ⇒ 安全）；</li>
 * <li>{@code corner}（14×14）：★<b>非 9-slice ⇒ 1:1 贴</b>四角各一次，不得拉伸；</li>
 * <li>{@code rivet}（6×6）：★同样 1:1，<b>同一个图标多点位复用</b>（铆钉落两条列分隔缝的两端）。</li>
 * </ul>
 * <b>缺图即退化，不崩</b>：{@code UITexture} 指向未落地的资源时该处只画空 ⇒
 * 本层可以先于贴图存在而上线（兄弟片 {@code zcw-drawer} 在写
 * {@code src/main/resources/assets/gtit/textures/gui/pocket/**}），
 * 零贴图的退化形态就是提案里那句"只剩黄褐底 + 铜色描边"。
 */
final class NekoPocketDecoration {

    /** 面板主区高（三段之外还要给底部带与外边距，全部由面板常量派生）。 */
    private static final int MAIN_HEIGHT = NekoPocketStorageColumn.HEIGHT;
    /** 铆钉边长（单源取契约表里的原生尺寸；几何不读贴图类）。 */
    private static final int RIVET = PocketGuiTextureContract.widthOf("POCKET_C2_rivet");
    /** 包角边长（同上）。 */
    private static final int CORNER = PocketGuiTextureContract.widthOf("POCKET_C2_corner");

    private NekoPocketDecoration() {}

    /** 装配装饰层（一次调用产出<b>一棵</b>子树 ⇒ 双端同树，R32）。 */
    static IWidget build() {

        final ParentWidget<?> root = new ParentWidget<>().pos(0, 0)
            .size(NekoPocketPanel.WIDTH, NekoPocketPanel.HEIGHT)
            .name("pocket_decoration");
        root.child(
            (IWidget) new ParentWidget<>().pos(0, 0)
                .size(NekoPocketPanel.WIDTH, NekoPocketPanel.HEIGHT)
                .name("pocket_decoration_cloth")
                .background(PocketGuiTextures.CLOTH));
        root.child(
            (IWidget) new ParentWidget<>().pos(0, 0)
                .size(NekoPocketPanel.WIDTH, NekoPocketPanel.HEIGHT)
                .name("pocket_decoration_panel")
                .background(PocketGuiTextures.PANEL));
        // 铜包角：四角各一枚，1:1（左上/右上/左下/右下）
        final int panelHeight = NekoPocketPanel.HEIGHT;
        root.child(corner(0, 0));
        root.child(corner(NekoPocketPanel.WIDTH - CORNER, 0));
        root.child(corner(0, panelHeight - CORNER));
        root.child(corner(NekoPocketPanel.WIDTH - CORNER, panelHeight - CORNER));
        // 铆钉：两条列分隔缝（流体列|中栏、中栏|源质列）的上下两端，共 4 枚
        root.child(rivet(firstSeamX(), NekoPocketPanel.MARGIN));
        root.child(rivet(secondSeamX(), NekoPocketPanel.MARGIN));
        root.child(rivet(firstSeamX(), NekoPocketPanel.MARGIN + MAIN_HEIGHT - RIVET));
        root.child(rivet(secondSeamX(), NekoPocketPanel.MARGIN + MAIN_HEIGHT - RIVET));
        // 绳结虚线（契约里的 rope，8×8 平铺）：底部带里"背包块 / 绑定块"的分隔缝。
        // ★R81④：这条缝现在**归右段自己的账**（右段 = 绳缝 4 + 内容 108，见
        // NekoPocketBottomBand#BIND_ROPE_WIDTH），起点因此是 BIND_X 而不是旧口径的
        // {@code BIND_X − COLUMN_GAP}——后者在三段改紧挨之后会把 4px 画到背包段最后一列上
        // （那一列是真实交互槽，装饰压上去读成"格子被切了一刀"）。坐标全部派生自
        // NekoPocketPanel.WIDTH / 带段常量，★不留字面面板宽。
        root.child(
            (IWidget) PocketGuiTextures.ROPE.asWidget()
                .pos(NekoPocketBottomBand.BIND_X, NekoPocketBottomBand.Y)
                .size(NekoPocketBottomBand.BIND_ROPE_WIDTH, NekoPocketBottomBand.HEIGHT)
                .name("pocket_decoration_rope"));
        return root;
    }

    /** 第一条列分隔缝的左边界（流体列右侧）。 */
    private static int firstSeamX() {
        return seamCenter(NekoPocketLeftColumn.X + NekoPocketLeftColumn.WIDTH);
    }

    /** 第二条列分隔缝的左边界（中栏右侧）。 */
    private static int secondSeamX() {
        return seamCenter(NekoPocketStorageColumn.X + NekoPocketStorageColumn.WIDTH);
    }

    /**
     * 铆钉 x：让 6px 的圆点<b>居中</b>落在 4px 的缝上（左右各溢出 1px，是刻意的——
     * 缝宽小于钉宽时"贴边"会读成"贴歪"，居中才有铆在框上的感觉）。
     */
    private static int seamCenter(int seamLeft) {
        return seamLeft + (NekoPocketPanel.COLUMN_GAP - RIVET) / 2;
    }

    private static IWidget corner(int x, int y) {
        return (IWidget) PocketGuiTextures.CORNER.asWidget()
            .pos(x, y)
            .size(CORNER, CORNER)
            .name("pocket_decoration_corner");
    }

    private static IWidget rivet(int x, int y) {
        return (IWidget) PocketGuiTextures.RIVET.asWidget()
            .pos(x, y)
            .size(RIVET, RIVET)
            .name("pocket_decoration_rivet");
    }
}
