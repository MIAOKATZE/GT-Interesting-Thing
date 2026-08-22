package com.miaokatze.gtit.gui.vm;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.client.gui.NekoFallingItemSlotFactory;
import com.miaokatze.gtit.client.gui.NekoGuiTextures;
import com.miaokatze.gtit.client.gui.NekoMeTransferParticleWidget;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 右侧 IO 列面板（A01 蓝图 G6 抽取自 NekoVMGuiV2，方法体逐字搬移）
 * <p>
 * IO 列装配（输入槽投币 changeListener + 弹射按钮行 + 出货槽 100 掉落动画槽 + ME 粒子
 * Widget）。<b>双端构建且必须先于一切仅客户端子树挂载</b>（v1.7.17 双端镜像纪律）：
 * 输入槽/掉落槽为 ISynced 槽位参与 auto_sync ID 分配，宿主 build() 挂载位点与顺序不变。
 * 客户端 ME 队列缓存列表与宿主 parseMeTransferQueue 共享同一实例。
 */
public final class IoColumnPanel {

    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("gtit");

    /** 宿主引用（跨域触点：coinOps 通道 getter/余额同步值/取回通道/isClient/getPlayerId） */
    private final NekoVMGuiV2 gui;
    /** 机器引用（输入/出货槽 ItemStackHandler） */
    private final MTENekoVendingMachineV2 multiblock;
    /** 基础 TE 引用（投币音效世界坐标） */
    private final IGregTechTileEntity baseMetaTileEntity;
    /** 客户端 ME 传输队列缓存（宿主 parseMeTransferQueue 写入，粒子 Widget 消费，共享实例） */
    private final List<MTENekoVendingMachineV2.MeTransferEntry> clientMeTransferQueue;

    public IoColumnPanel(NekoVMGuiV2 gui, MTENekoVendingMachineV2 multiblock, IGregTechTileEntity baseMetaTileEntity,
        List<MTENekoVendingMachineV2.MeTransferEntry> clientMeTransferQueue) {
        this.gui = gui;
        this.multiblock = multiblock;
        this.baseMetaTileEntity = baseMetaTileEntity;
        this.clientMeTransferQueue = clientMeTransferQueue;
    }

    /**
     * 创建右侧 IO 列
     * <p>
     * 完全模仿 V1（VM 的 {@code MTEVendingMachineGui.createIOColumn}）的布局：
     * <ul>
     * <li>顶部：INPUT_SPRITE 图标 + "IN" 文字</li>
     * <li>2x4 输入槽（带自动导入猫猫币 changeListener）</li>
     * <li>物品弹射按钮（EJECT_SLOTS）+ 货币弹射按钮（EJECT_COINS）</li>
     * <li>底部：出货槽（带 DISPENSER_BACKGROUND/OVERHANG + 100 个掉落动画槽）</li>
     * </ul>
     * 不再放置电源开关、结构更新、音量按钮，这些功能分别由 GT5U 标准交互
     * （扳手右键等）与 QoL 按钮列承担，与 V1 行为一致。
     *
     * @return IO 列 Widget
     */
    public IWidget createIOColumn() {
        ParentWidget<?> ioColumn = new ParentWidget<>().size(50, 214)
            .right(-48)
            .top(40)
            .background(NekoGuiTextures.SIDE_PANEL_BACKGROUND);

        // v1.7.6 G1：IO 列四页恒显示（输入槽投币/取物、出货槽在四页均可用）——
        // 移除原 v1.7.0「仅贸易页」的 setEnabledIf；槽位双端注册本就不受 setEnabledIf 影响，此改动仅扩大可见范围

        // --- 顶部：INPUT_SPRITE 图标 + "IN" 文字 ---
        ioColumn.child(
            NekoGuiTextures.INPUT_SPRITE.asWidget()
                .leftRel(0.5f)
                .top(8)
                .width(30)
                .height(20));
        ioColumn.child(
            (IWidget) new TextWidget(IKey.str("IN")).textAlign(Alignment.CENTER)
                .top(8)
                .widthRel(1.0f));

        // --- 输入槽（2x4，带自动导入猫猫币 changeListener）---
        SlotGroupWidget inputSlots = SlotGroupWidget.builder()
            .matrix("II", "II", "II", "II")
            .key('I', index -> {
                ModularSlot slot = new ModularSlot(multiblock.inputItems, index).slotGroup("inputSlotGroup");
                // 持有 ItemSlot 引用，便于服务端在入账后强制同步槽位状态到客户端
                final ItemSlot itemSlot = new ItemSlot().slot(slot);
                // 自动导入猫猫币：识别到猫猫币后放入玩家钱包并立即同步客户端
                slot.changeListener((newItem, onlyAmountChanged, client, init) -> {
                    // [NekoInput] 诊断日志：lambda 入口，输出关键参数与线程信息
                    LOG.debug(
                        "[NekoInput] changeListener 入口: slotIdx=" + index
                            + " thread="
                            + Thread.currentThread()
                                .getName()
                            + " client="
                            + client
                            + " init="
                            + init
                            + " onlyAmountChanged="
                            + onlyAmountChanged
                            + " newItem="
                            + (newItem == null ? "null" : newItem.getDisplayName())
                            + " stackSize="
                            + (newItem == null ? 0 : newItem.stackSize));
                    if (init || newItem == null) {
                        LOG.debug("[NekoInput] 提前返回: init=" + init + " newItemNull=" + (newItem == null));
                        return;
                    }
                    String currencyId = NekoCurrencyRegistrar.getNekoCurrencyId(newItem);
                    if (currencyId == null) {
                        LOG.debug("[NekoInput] 非猫猫币，跳过: slotIdx=" + index);
                        return;
                    }
                    LOG.debug("[NekoInput] 识别猫猫币: slotIdx=" + index + " currencyId=" + currencyId);
                    // 客户端：立即视觉清槽，真实数据以服务端同步为准
                    if (client) {
                        LOG.debug("[NekoInput] 客户端分支: 清槽前 slotIdx=" + index);
                        slot.putStack(null);
                        LOG.debug("[NekoInput] 客户端分支: 清槽后 slotIdx=" + index);
                        return;
                    }
                    UUID playerId = gui.getPlayerId();
                    if (playerId == null) {
                        LOG.debug("[NekoInput] 服务端分支: playerId 为 null, 跳过");
                        return;
                    }
                    NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
                    if (wallet == null) {
                        LOG.debug("[NekoInput] 服务端分支: wallet 为 null, 跳过");
                        return;
                    }
                    // 先入账，再清槽，避免异常导致丢币（O2-17：落盘统一走脏标记兜底）
                    wallet.addCount(currencyId, newItem.stackSize);
                    int newCount = wallet.getCount(currencyId);
                    LOG.debug(
                        "[NekoInput] 服务端分支: addCount 完成 currencyId=" + currencyId
                            + " added="
                            + newItem.stackSize
                            + " newCount="
                            + newCount);
                    LOG.debug("[NekoInput] 服务端分支: 清槽前 slotIdx=" + index);
                    slot.putStack(null);
                    LOG.debug("[NekoInput] 服务端分支: 清槽后 slotIdx=" + index);
                    // 强制同步槽位到客户端，使玩家立即看到槽位清空
                    itemSlot.getSyncHandler()
                        .forceSyncItem();
                    LOG.debug("[NekoInput] 服务端分支: forceSyncItem 已调用 slotIdx=" + index);
                    // 强制刷新对应货币余额同步值，使余额显示立即更新
                    IntSyncValue coinSync = gui.coinAmountSyncs.get(currencyId);
                    if (coinSync != null) {
                        coinSync.setValue(wallet.getCount(currencyId));
                    }
                    // 服务端播放投币音效，会自动广播给附近客户端
                    if (baseMetaTileEntity != null) {
                        World world = baseMetaTileEntity.getWorld();
                        if (world != null && !world.isRemote) {
                            world.playSoundEffect(
                                baseMetaTileEntity.getXCoord() + 0.5,
                                baseMetaTileEntity.getYCoord() + 0.5,
                                baseMetaTileEntity.getZCoord() + 0.5,
                                "vendingmachine:coin_insert",
                                1.0f,
                                1.0f);
                        }
                    }
                });
                // 收集输入槽引用，供服务端在弹出物品等操作后强制同步
                gui.coinOps.getInputSlotRefs()
                    .add(itemSlot);
                return itemSlot;
            })
            .build();
        ioColumn.child(
            Flow.row()
                .child(inputSlots.center())
                .top(20)
                .height(18 * 4));

        // --- 弹射按钮行：物品弹射 + 货币弹射 ---
        ButtonWidget<?> ejectItemsButton = new ButtonWidget<>().size(16, 16)
            .overlay(
                NekoGuiTextures.EJECT_SLOTS.asIcon()
                    .size(16))
            .onMousePressed(btn -> {
                gui.coinOps.getEjectItemsSync()
                    .setValue(true);
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("弹出物品")));
        ButtonWidget<?> ejectCoinsButton = new ButtonWidget<>().size(16, 16)
            .overlay(
                NekoGuiTextures.EJECT_COINS.asIcon()
                    .size(16))
            .onMousePressed(btn -> {
                gui.coinOps.getEjectAllCoinsSync()
                    .setValue(true);
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.str("弹出所有猫猫币")));
        ioColumn.child(
            Flow.row()
                .child(ejectItemsButton.right(6))
                .child(ejectCoinsButton.left(6))
                .top(98)
                .height(18));

        // --- 底部：出货槽（带 dispenser 背景与悬垂 + 100 个掉落动画槽）---
        ParentWidget<?> dispenserChute = new ParentWidget<>().fullHeight()
            .fullWidth()
            .marginLeft(5)
            .marginRight(4)
            .background(NekoGuiTextures.DISPENSER_BACKGROUND)
            .child(getFillPlayerInventoryButton());
        // 100 个掉落动画槽（fallDistance=72 与 V1 一致，4 行高度）
        NekoFallingItemSlotFactory fallingFactory = new NekoFallingItemSlotFactory(
            multiblock.outputItems,
            18 * 4,
            MTENekoVendingMachineV2.OUTPUT_SLOTS);
        for (int i = 0; i < MTENekoVendingMachineV2.OUTPUT_SLOTS; i++) {
            dispenserChute.child(fallingFactory.getFallingItemSlot(i));
        }
        // v1.6.23: ME 传输粒子动画 Widget（围绕出货槽中的物品渲染粒子）
        // 传入 fallingFactory 供粒子定位槽位坐标；点击穿透到出货槽（不拦截鼠标）
        // v1.7.5 修复：仅客户端创建——NekoMeTransferParticleWidget 带 @SideOnly(Side.CLIENT)，
        // 专用服务器类被剥离，双端执行会 NoClassDefFoundError（createIOColumn 双端调用）。
        if (gui.isClient()) {
            NekoMeTransferParticleWidget particleWidget = new NekoMeTransferParticleWidget(
                clientMeTransferQueue,
                fallingFactory).onRetrieve(() -> {
                    if (gui.retrieveMeItemSync != null) {
                        gui.retrieveMeItemSync.setValue(true);
                    }
                });
            particleWidget.fullWidth()
                .fullHeight();
            // v1.6.24: 移除 setEnabledIf（ModularUI2 此版本中 setEnabledIf(false) 会阻止 widget 的 draw() 被调用，
            // 导致 clientMeTransferQueue 同步到达后粒子仍不渲染）。draw() 方法内已有空队列守卫
            // (if (queueRef.isEmpty()) return;)，无需额外控制可见性。
            dispenserChute.child(particleWidget);
        }
        // 顶部悬垂装饰：必须最后添加，覆盖掉落槽起始位置（顶部），形成"物品从悬垂后面掉落"的图层效果
        // 与 VM 原版 MteVendingMachineGui.createDispenserChute 顺序一致（掉落槽 → OVERHANG 最后）
        dispenserChute.child(
            NekoGuiTextures.DISPENSER_OVERHANG.asWidget()
                .top(0)
                .fullWidth());
        ioColumn.child(
            Flow.row()
                .child(dispenserChute)
                .bottom(6)
                .height(18 * 5));

        // 在 NEI/HEI 中排除右侧 IO 列区域，避免配方查看器遮挡输入/输出槽
        return ioColumn.excludeAreaInRecipeViewer();
    }

    /**
     * 获取"填充玩家背包"按钮
     * <p>
     * 复刻 V1 的 getNekoFillPlayerInventoryButton：
     * 使用一个铺满出货槽区域的不可见 ButtonWidget，
     * Shift+左键点击时通过 fillPlayerInventorySync 触发服务端方法，
     * 将出货槽的物品快速移到玩家背包。
     *
     * @return 不可见的满覆盖按钮 Widget
     */
    private IWidget getFillPlayerInventoryButton() {
        return new ButtonWidget<>().fullHeight()
            .fullWidth()
            .invisible()
            .playClickSound(false)
            .onMousePressed(btn -> {
                // 复刻 V1：仅 Shift+左键触发
                if (Interactable.hasShiftDown()) {
                    gui.coinOps.getFillPlayerInventorySync()
                        .setValue(true);
                }
                return true;
            });
    }
}
