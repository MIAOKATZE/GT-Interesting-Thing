package com.miaokatze.gtit.client.gui;

import com.cleanroommc.modularui.value.sync.ItemSlotSH;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

/**
 * 禁用 auto_sync 的 PhantomItemSlot（v1.7.16 根因修复）
 * <p>
 * <b>背景</b>：NekoVMGuiV2 将编辑面板（editOverlayRoot）内嵌到主面板后，53 个
 * {@link PhantomItemSlot} 仅在客户端 widget 树中创建（服务端不创建），用于编辑面板
 * 的物品拖放配置。ModularUI2 的 {@code WidgetTree.collectSyncValues} 按 BFS 层级遍历
 * widget 树，为所有 {@code isSynced()=true} 的 widget 分配 auto_sync int ID，双端凭
 * (panelName, id) 配对收发同步包。
 * <p>
 * <b>根因</b>：editOverlayRoot 内的 PhantomItemSlot 处于 BFS 第 3 层（L3），先于输入槽
 * （L4）和输出槽（L5）被收集。客户端因此多出 53 个 ISynced widget，auto_sync ID 整体
 * 偏移 53，服务端发给输出槽的同步包被客户端错误的 handler 接收——输出槽 changeListener
 * 永不触发，掉落动画不启动；输入槽同步也错位。日志中 106 条
 * {@code auto_sync:MTEMultiBlockBase:108~160 does not exist} 警告（53 个唯一 ID）即为此偏移。
 * <p>
 * <b>修复</b>：覆写 {@link #isSynced()} 返回 {@code false}，使 collectSyncValues 跳过
 * NekoPhantomItemSlot，不为其分配 auto_sync ID。客户端 ISynced widget 集合与服务端一致
 * （仅输入槽 + 输出槽），ID 不再偏移。
 * <p>
 * <b>零功能损失</b>：编辑面板的物品保存走显式 C2S sync value（按钮触发），不依赖 auto_sync。
 * PhantomItemSlot 原有的 auto_sync（服务端编辑缓冲区 ↔ 客户端 phantom 槽）本就因服务端
 * 无对应 handler 而不生效（见 NekoVMGuiV2 第 702-705 行 v1.7.7 注释），禁用它无副作用。
 * <p>
 * <b>协变返回</b>：覆写 {@link #slot(ModularSlot)} 与 {@link #syncHandler(ItemSlotSH)}
 * 返回 {@code NekoPhantomItemSlot}，沿用 PhantomItemSlot 继承 ItemSlot 时的协变返回模式，
 * 保证 {@code new NekoPhantomItemSlot().slot(...)} 链式调用可赋值给
 * {@code NekoPhantomItemSlot} 类型变量。
 */
public class NekoPhantomItemSlot extends PhantomItemSlot {

    /**
     * 禁用 auto_sync。
     * <p>
     * 返回 {@code false} 使 {@code WidgetTree.collectSyncValues} 跳过本 widget，
     * 不分配 auto_sync ID，避免客户端 ISynced widget 多于服务端导致 ID 偏移。
     * <p>
     * 注意：本方法不依赖 {@code syncHandler != null} 的默认判定。即使本 widget 绑定了
     * {@link com.cleanroommc.modularui.value.sync.PhantomItemSlotSH}（用于鼠标点击/滚轮
     * 的 C2S 同步），auto_sync 通道仍被禁用——PhantomItemSlotSH 的显式 C2S sync（如
     * {@code SYNC_CLICK}/{@code SYNC_SCROLL}）不受 isSynced() 影响，仍正常工作。
     *
     * @return 恒为 {@code false}，禁止进入 auto_sync 通道
     */
    @Override
    public boolean isSynced() {
        // v1.7.16: 禁用 auto_sync，避免 53 个 phantom 槽导致客户端 ID 偏移 53
        return false;
    }

    /**
     * 协变返回 {@code NekoPhantomItemSlot}，使链式调用可赋值给本类型变量。
     * <p>
     * 实际委托给 {@link PhantomItemSlot#slot(ModularSlot)}，运行时 {@code this} 即
     * {@code NekoPhantomItemSlot}，强转安全。
     *
     * @param slot 绑定的 ModularSlot
     * @return this（NekoPhantomItemSlot）
     */
    @Override
    public NekoPhantomItemSlot slot(ModularSlot slot) {
        return (NekoPhantomItemSlot) super.slot(slot);
    }

    /**
     * 协变返回 {@code NekoPhantomItemSlot}，使链式调用可赋值给本类型变量。
     * <p>
     * 实际委托给 {@link PhantomItemSlot#syncHandler(ItemSlotSH)}，运行时 {@code this} 即
     * {@code NekoPhantomItemSlot}，强转安全。
     *
     * @param syncHandler 同步处理器
     * @return this（NekoPhantomItemSlot）
     */
    @Override
    public NekoPhantomItemSlot syncHandler(ItemSlotSH syncHandler) {
        return (NekoPhantomItemSlot) super.syncHandler(syncHandler);
    }
}
