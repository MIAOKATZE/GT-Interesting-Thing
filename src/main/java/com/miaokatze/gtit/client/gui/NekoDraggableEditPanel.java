package com.miaokatze.gtit.client.gui;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.api.layout.IViewport;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.HoveredWidgetList;
import com.cleanroommc.modularui.widget.DragHandle;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widget.sizer.Area;

/**
 * 可拖拽的可视化编辑面板容器。
 * <p>
 * 顶部固定高度的 {@link DragHandle} 作为标题栏/拖拽柄，下方 {@code contentPanel}
 * 承载所有编辑内容。外部调用 {@link #child(IWidget)} 时会自动进入内容区，
 * 因此原有 8 个 {@code buildXxxEditPanel()} 方法无需调整子控件坐标。
 * <p>
 * 实现 {@link IDraggable} 与 {@link IViewport}，拖动时由 ModularUI2 的拖拽框架调用。
 *
 * @author miaokatze
 * @version 1.7.27
 */
public class NekoDraggableEditPanel extends ParentWidget<NekoDraggableEditPanel> implements IDraggable, IViewport {

    /** 标题栏/拖拽柄高度（像素） */
    public static final int DRAG_HANDLE_HEIGHT = 12;

    private final ParentWidget<?> contentPanel;
    private final Area movingArea;
    private int relativeClickX, relativeClickY;
    private int realX, realY;
    private boolean moving = false;

    public NekoDraggableEditPanel() {
        this.movingArea = getArea().createCopy();
        this.contentPanel = new ParentWidget<>();
        // 内容区位于标题栏下方，四边贴齐以自动填充剩余空间
        this.contentPanel.top(DRAG_HANDLE_HEIGHT)
            .left(0)
            .right(0)
            .bottom(0);
        // 先加内容区；标题栏在 onInit 中插到索引 0，确保始终在最上层
        super.child(this.contentPanel);
    }

    @Override
    public void onInit() {
        super.onInit();
        // 创建拖拽柄；DragHandle.onInit 会向上查找到本面板（IDraggable）作为被拖拽对象
        DragHandle dragHandle = new DragHandle();
        dragHandle.top(0)
            .left(0)
            .right(0)
            .height(DRAG_HANDLE_HEIGHT)
            .background(NekoGuiTextures.TEXT_FIELD_BACKGROUND);
        super.child(0, dragHandle);
    }

    /**
     * 外部添加的子控件全部进入内容区，避免与标题栏坐标冲突。
     */
    @Override
    public NekoDraggableEditPanel child(IWidget child) {
        this.contentPanel.child(child);
        return getThis();
    }

    /**
     * 外部按索引添加的子控件全部进入内容区。
     */
    @Override
    public NekoDraggableEditPanel child(int index, IWidget child) {
        this.contentPanel.child(index, child);
        return getThis();
    }

    // ==================== IDraggable ====================

    @Override
    public void drawMovingState(ModularGuiContext context, float partialTicks) {
        WidgetTree.drawTree(this, context, true, true);
    }

    @Override
    public boolean onDragStart(int mouseButton) {
        if (mouseButton == 0) {
            // 计算面板相对于父区域（editOverlayRoot，即屏幕）的当前位置
            this.realX = getContext().transformX(0, 0) - getParentArea().x;
            this.realY = getContext().transformY(0, 0) - getParentArea().y;
            this.movingArea.x = this.realX;
            this.movingArea.y = this.realY;
            this.relativeClickX = getContext().getAbsMouseX() - this.realX;
            this.relativeClickY = getContext().getAbsMouseY() - this.realY;
            return true;
        }
        return false;
    }

    @Override
    public void onDragEnd(boolean successful) {
        if (successful) {
            // 将最终位置写入 resizer，使面板固定在新位置
            resizer().top(getContext().getAbsMouseY() - this.relativeClickY)
                .left(getContext().getAbsMouseX() - this.relativeClickX);
            this.movingArea.x = getArea().x;
            this.movingArea.y = getArea().y;
            scheduleResize();
        }
    }

    @Override
    public void onDrag(int mouseButton, long timeSinceLastClick) {
        this.movingArea.x = getContext().getAbsMouseX() - this.relativeClickX;
        this.movingArea.y = getContext().getAbsMouseY() - this.relativeClickY;
    }

    @Override
    public @Nullable Area getMovingArea() {
        return this.movingArea;
    }

    @Override
    public boolean isMoving() {
        return this.moving;
    }

    @Override
    public void setMoving(boolean moving) {
        this.moving = moving;
        setEnabled(!moving);
    }

    // ==================== IViewport ====================

    @Override
    public void getSelfAt(IViewportStack stack, HoveredWidgetList widgets, int x, int y) {
        // v1.7.27 修正：面板背景不加入 hovered 列表，避免点击空白处触发整体拖动。
        // 拖动仅由顶部的 DragHandle 标题栏触发；面板本身仍通过 getWidgetsAt 将事件传给子控件。
    }

    @Override
    public void getWidgetsAt(IViewportStack stack, HoveredWidgetList widgets, int x, int y) {
        if (!isMoving() && hasChildren()) {
            IViewport.getChildrenAt(this, stack, widgets, x, y);
        }
    }

    @Override
    public void transform(IViewportStack stack) {
        super.transform(stack);
        if (isMoving()) {
            // 移除默认的相对位置变换
            stack.translate(-getArea().rx, -getArea().ry);
            // 从原始位置平移到拖动位置
            stack.translate(-this.realX, -this.realY);
            stack.translate(this.movingArea.x, this.movingArea.y);
        }
    }
}
