package com.miaokatze.gtit.gui.vm.edit;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.ParentWidget;

/**
 * 编辑覆盖层控制器（v1.7.7 G2① 引入，A01 蓝图 G1 抽取自 NekoVMGuiV2）
 * <p>
 * 持有编辑覆盖层的公共基础设施：覆盖层类型枚举、当前打开状态、
 * 覆盖层根节点与 8 个编辑面板的注册表。各编辑面板本体（buildXxxEditPanel）
 * 仍由 NekoVMGuiV2 构建，经 {@link #registerPanel} 以回调形式注册到本控制器。
 * <p>
 * <b>双端镜像纪律（v1.7.17，最高风险约束）</b>：ModularUI2 自动同步按 BFS 遍历
 * widget 树的顺序为 isSynced() 的 handler 分配 auto_sync ID，双端凭 (panelName, id)
 * 配对收发。本控制器注册的 8 个面板<b>顺序冻结</b>：
 * TRADE → SIGNIN → SIGNIN_DAY → ONLINE_TIER → LOTTERY → LOTTERY_POOL → PAGE → BLESSING，
 * 不得增删、不得重排；覆盖层根节点必须在主内容 PagedWidget 之后挂到主面板（v1.7.20 渲染顺序）。
 * <p>
 * <b>双端安全</b>：本类与全部注册的面板构建回调内不得出现客户端专属 API
 * （双端镜像构建要求，见 NekoVMGuiV2.build 注释）。
 */
public final class EditOverlayController {

    /** v1.7.7 G2① 当前打开的编辑覆盖层类型（NONE=未打开） */
    public enum EditOverlayType {
        NONE,
        TRADE,
        SIGNIN,
        /** v1.7.8 任务6：逐日覆盖编辑（每月签到日期格） */
        SIGNIN_DAY,
        ONLINE_TIER,
        LOTTERY,
        LOTTERY_POOL,
        PAGE,
        BLESSING
    }

    /** v1.7.7 G2① 当前打开的编辑覆盖层类型（客户端状态，控制覆盖层显隐） */
    private EditOverlayType currentEditOverlay = EditOverlayType.NONE;

    /** v1.7.7 G2① 编辑覆盖层根节点（各编辑面板的公共父级，双端镜像构建） */
    private final ParentWidget<?> editOverlayRoot;

    /** 已注册的覆盖层类型（用于重复注册防御） */
    private final java.util.Set<EditOverlayType> registered = new java.util.HashSet<>();

    public EditOverlayController() {
        editOverlayRoot = new ParentWidget<>();
        editOverlayRoot.relativeToScreen()
            .full();
        editOverlayRoot.setEnabledIf(w -> currentEditOverlay != EditOverlayType.NONE);
    }

    /**
     * 注册一个编辑面板（按调用顺序追加为覆盖层根节点的 child）
     * <p>
     * 注册顺序即 widget 树 BFS 顺序的一部分，<b>顺序冻结</b>（见类注释双端镜像纪律）；
     * NekoVMGuiV2.build 中必须按 TRADE → SIGNIN → SIGNIN_DAY → ONLINE_TIER → LOTTERY
     * → LOTTERY_POOL → PAGE → BLESSING 的固定顺序调用本方法。
     *
     * @param type         覆盖层类型（注册键，禁止重复注册）
     * @param panelBuilder 面板构建回调（注册时立即求值，与原内联构建时机一致）
     * @return 本控制器（链式）
     */
    public EditOverlayController registerPanel(EditOverlayType type, Supplier<IWidget> panelBuilder) {
        if (!registered.add(type)) {
            throw new IllegalStateException("编辑覆盖层类型重复注册: " + type);
        }
        editOverlayRoot.child(panelBuilder.get());
        return this;
    }

    /** 获取覆盖层根节点（由 NekoVMGuiV2.build 在主内容之后挂到主面板） */
    public ParentWidget<?> getRoot() {
        return editOverlayRoot;
    }

    /**
     * 打开指定类型的编辑覆盖层（v1.7.7 G2①）
     * <p>
     * 覆盖层作为主面板的直接 child，统一坐标系；
     * 打开后覆盖层根节点 setEnabledIf 生效，拦截对主内容的点击。
     *
     * @param type 要打开的编辑覆盖层类型
     */
    public void open(EditOverlayType type) {
        this.currentEditOverlay = type;
    }

    /** 检查当前是否有编辑覆盖层处于打开状态（v1.7.27） */
    public boolean isOpen() {
        return this.currentEditOverlay != EditOverlayType.NONE;
    }

    /** 获取当前打开的编辑覆盖层类型（NONE=未打开） */
    public EditOverlayType getCurrent() {
        return this.currentEditOverlay;
    }

    /** 判断当前打开的覆盖层是否为指定类型（供各编辑面板 setEnabledIf 使用） */
    public boolean isCurrent(EditOverlayType type) {
        return this.currentEditOverlay == type;
    }

    /**
     * 关闭当前编辑覆盖层（v1.7.7 G2①）
     * <p>
     * 仅重置打开状态；类型相关的客户端残留清理（如交易编辑面板的 32 槽缓冲）
     * 由调用方按 {@link #getCurrent()} 的先前值自行处理。
     */
    public void close() {
        this.currentEditOverlay = EditOverlayType.NONE;
    }
}
