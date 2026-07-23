package com.miaokatze.gtit.client.gui;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.Icon;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;

/**
 * v1.7.6 G1 sub-page 标签页按钮（签到/抽奖/邮件三页左侧第二列标签栏）
 * <p>
 * 视觉风格与 {@link NekoMainTabButton}（主标签）/ {@link NekoPageButtonV2}（贸易分类标签）一致：
 * 16x16 图标 + 不对称 margin 悬停右移动画。
 * <p>
 * <b>与 NekoMainTabButton 的差异</b>：
 * <ul>
 * <li>图标来源为通用 {@link IDrawable}（UITexture/ItemDrawable 均可），便于 G2 池图标用物品图标扩展</li>
 * <li>不记录静态 lastPage（sub-page 恢复逻辑由 G2 各页自行处理）</li>
 * <li>内置<b>防崩守卫</b>：见 {@link #isControllerReady()}</li>
 * </ul>
 * <p>
 * <b>防崩守卫说明（v1.7.6 G1 过渡态）</b>：G1 阶段三页 sub-page Controller 尚未绑定 PagedWidget
 * （G2 才建对应 PagedWidget 页面并调用 {@code .controller(...)} 绑定）。父类
 * {@link PageButton#isActive()} 会调用 {@code Controller.getActivePageIndex()}，
 * {@link PageButton#onMousePressed(int)} 会调用 {@code Controller.setPage(int)}，
 * 未绑定时均抛 {@link IllegalStateException}——且 isActive() 在 draw 路径
 * （getBackground()/getActiveWidgetTheme()）也会被调用，故必须同时守卫绘制与点击两条路径。
 * G1 阶段未绑定时点击直接忽略；G2 绑定后守卫自动失效，无需再改本类。
 * <p>
 * <b>外部模式（v1.7.6 G2① 抽奖池标签列）</b>：调用 {@link #externalMode(Supplier)} 后，
 * 选中态由外部 Supplier 驱动、点击不再经过 Controller（直接触发 {@link #onSelected} 回调）——
 * 适用于「页面内容按外部客户端状态动态切换」的场景（抽奖页单页动态读
 * {@code LotteryClientData.getSelectedPool()}，池按钮只需写 selectedPoolId，
 * 无需为每池建一页 PagedWidget）。未调用 externalMode 的按钮行为不变（G1 守卫逻辑）。
 *
 * @see NekoMainTabButton
 * @see NekoPageButtonV2
 */
public class NekoSubTabButton extends PageButton {

    /** 图标左边距（悬停时减 1，产生轻微右移动画，与 NekoMainTabButton 一致） */
    private static final int ICON_MARGIN_LEFT = 7;
    /** 图标右边距（悬停时加 1，补偿左移，保持居中） */
    private static final int ICON_MARGIN_RIGHT = 5;
    /** 图标上下边距 */
    private static final int ICON_MARGIN_VERTICAL = 6;

    /** 标签页图标（margin 在 draw 中根据悬停状态动态调整） */
    private final Icon tabIcon;
    /** 点击选中后的回调（G2 挂扩展逻辑，如池切换重置动画） */
    private Runnable onSelected;
    /** 外部模式选中态 Supplier（非 null 时进入外部模式：选中态/点击均不经过 Controller） */
    private Supplier<Boolean> externalActiveSupplier;

    /**
     * 构造 sub-page 标签页按钮
     *
     * @param index      sub-page 索引（列内从 0 起）
     * @param controller 对应主标签页的 sub-page 分页控制器（G1 阶段可未绑定 PagedWidget）
     * @param icon       标签页图标（为 null 时显示空图标）
     */
    public NekoSubTabButton(int index, PagedWidget.Controller controller, IDrawable icon) {
        super(index, controller);
        IDrawable drawable = icon != null ? icon : IDrawable.EMPTY;
        this.tabIcon = new Icon(drawable).size(16, 16)
            .margin(ICON_MARGIN_LEFT, ICON_MARGIN_RIGHT, ICON_MARGIN_VERTICAL, ICON_MARGIN_VERTICAL)
            .center();
        this.overlay(this.tabIcon);
    }

    /**
     * 设置点击选中后的回调（在 super.onMousePressed 成功切换后触发）
     *
     * @param onSelected 回调 Runnable
     * @return this（链式调用）
     */
    public NekoSubTabButton onSelected(Runnable onSelected) {
        this.onSelected = onSelected;
        return this;
    }

    /**
     * 设置外部模式（v1.7.6 G2① 抽奖池标签列）
     * <p>
     * 外部模式下：{@link #isActive()} 返回 activeSupplier 求值结果；
     * 点击不再经过 Controller，直接触发 {@link #onSelected} 回调
     * （回调内部自行处理选中态切换或编辑分流）。
     *
     * @param activeSupplier 选中态 Supplier（每帧求值，须为纯客户端轻量读）
     * @return this（链式调用）
     */
    public NekoSubTabButton externalMode(Supplier<Boolean> activeSupplier) {
        this.externalActiveSupplier = activeSupplier;
        return this;
    }

    /**
     * 防崩守卫：Controller 是否已绑定有效 PagedWidget
     * <p>
     * G1 阶段三页 sub-page Controller 仅创建未绑定（返回 false），
     * G2 建对应 PagedWidget 并绑定后返回 true，按钮恢复正常切换行为。
     *
     * @return true 表示可安全调用父类的页面查询/切换方法
     */
    private boolean isControllerReady() {
        PagedWidget.Controller controller = getController();
        return controller != null && controller.isInitialised();
    }

    /**
     * 是否当前选中页（父类实现调用 controller.getActivePageIndex()，未绑定时抛异常，故先守卫）
     * <p>
     * 注意：本方法在 draw 路径（getBackground()/getActiveWidgetTheme()）也会被调用，
     * 未绑定时必须返回 false 而非抛异常，否则打开 GUI 即崩。
     * 外部模式下直接返回外部 Supplier 求值结果（不触碰 Controller）。
     */
    @Override
    public boolean isActive() {
        if (this.externalActiveSupplier != null) {
            return this.externalActiveSupplier.get();
        }
        return isControllerReady() && super.isActive();
    }

    /**
     * 鼠标按下：Controller 未绑定时直接忽略点击（G1 过渡态）；
     * 外部模式下点击直接触发 {@link #onSelected} 回调（不经过 Controller）
     */
    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        if (this.externalActiveSupplier != null) {
            // 外部模式：选中态切换/编辑分流全部由 onSelected 回调处理
            if (this.onSelected != null) {
                this.onSelected.run();
                Interactable.playButtonClickSound();
                return Interactable.Result.SUCCESS;
            }
            return Interactable.Result.ACCEPT;
        }
        if (!isControllerReady()) {
            // TODO G2②/G2③：Controller 绑定对应 PagedWidget 后此守卫自动失效，点击即切换 sub-page
            return Interactable.Result.ACCEPT;
        }
        Interactable.Result result = super.onMousePressed(mouseButton);
        if (result == Interactable.Result.SUCCESS && this.onSelected != null) {
            this.onSelected.run();
        }
        return result;
    }

    /**
     * 绘制：悬停时调整图标 margin，产生轻微右移动画（与 NekoMainTabButton/NekoPageButtonV2 一致）
     */
    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (this.isHovering()) {
            // 悬停：左 -1、右 +1，图标轻微右移
            this.tabIcon.marginLeft(ICON_MARGIN_LEFT - 1)
                .marginRight(ICON_MARGIN_RIGHT + 1);
        } else {
            // 非悬停：恢复基础 margin
            this.tabIcon.marginLeft(ICON_MARGIN_LEFT)
                .marginRight(ICON_MARGIN_RIGHT);
        }
        super.draw(context, widgetTheme);
    }
}
