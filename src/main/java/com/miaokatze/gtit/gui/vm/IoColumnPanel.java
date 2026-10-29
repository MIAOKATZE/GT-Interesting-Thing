package com.miaokatze.gtit.gui.vm;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.client.gui.NekoVendingTheme;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.common.machine.v2.MeTransferEntry;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Shared native inputs and the real 100-slot output handler registrations. Visual scene children are
 * attached separately after every synced widget has been assembled identically on both sides.
 */
public final class IoColumnPanel {

    /** 宿主引用（跨域触点：coinOps 通道 getter/余额同步值/取回通道/isClient/getPlayerId） */
    private final NekoVMGuiV2 gui;
    /** 机器引用（输入/出货槽 ItemStackHandler） */
    private final MTENekoVendingMachineV2 multiblock;
    /** 基础 TE 引用（投币音效世界坐标） */
    private final IGregTechTileEntity baseMetaTileEntity;
    /** 客户端 ME 传输队列缓存（宿主 parseMeTransferQueue 写入，粒子 Widget 消费，共享实例） */
    private final List<MeTransferEntry> clientMeTransferQueue;

    public IoColumnPanel(NekoVMGuiV2 gui, MTENekoVendingMachineV2 multiblock, IGregTechTileEntity baseMetaTileEntity,
        List<MeTransferEntry> clientMeTransferQueue) {
        this.gui = gui;
        this.multiblock = multiblock;
        this.baseMetaTileEntity = baseMetaTileEntity;
        this.clientMeTransferQueue = clientMeTransferQueue;
    }

    private boolean depositCoins(net.minecraft.item.ItemStack stack) {
        if (gui.isClient()) return false;
        UUID playerId = gui.getPlayerId();
        String currencyId = NekoCurrencyRegistrar.getNekoCurrencyId(stack);
        if (playerId == null || currencyId == null) return false;
        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
        if (wallet == null) return false;
        if (!wallet.tryCreditWithinCapacity(currencyId, stack.stackSize)) return false;
        multiblock.markTradeStateChanged();
        IntSyncValue balance = gui.coinAmountSyncs.get(currencyId);
        if (balance != null) balance.setValue(wallet.getCount(currencyId));
        if (baseMetaTileEntity != null) {
            World world = baseMetaTileEntity.getWorld();
            if (world != null && !world.isRemote) world.playSoundEffect(
                baseMetaTileEntity.getXCoord() + 0.5,
                baseMetaTileEntity.getYCoord() + 0.5,
                baseMetaTileEntity.getZCoord() + 0.5,
                "vendingmachine:coin_insert",
                1.0f,
                1.0f);
        }
        return true;
    }

    private final ParentWidget<?> sceneMount = new ParentWidget<>();
    private final ParentWidget<?> outputStatsMount = new ParentWidget<>();
    private final java.util.List<ItemSlot> outputSlotRefs = new java.util.ArrayList<>();

    public ParentWidget<?> getSceneMount() {
        return sceneMount;
    }

    public ParentWidget<?> getOutputStatsMount() {
        return outputStatsMount;
    }

    public java.util.List<ItemSlot> getOutputSlotRefs() {
        return java.util.Collections.unmodifiableList(outputSlotRefs);
    }

    public IWidget createIOColumn() {
        ParentWidget<?> root = new ParentWidget<>().background(NekoVendingTheme.PANEL);
        // Fixed dual-side registrations are separate from the client scene. There is only one output handler.
        ParentWidget<?> synchronizedSlots = new ParentWidget<>().pos(-20000, -20000)
            .size(18, 18);
        for (int i = 0; i < MTENekoVendingMachineV2.OUTPUT_SLOTS; i++) {
            ItemSlot slot = new ItemSlot().slot(
                new ModularSlot(multiblock.outputItems, i).accessibility(false, true)
                    .slotGroup("outputSlotGroup"));
            outputSlotRefs.add(slot);
            synchronizedSlots.child(slot);
        }
        root.child(synchronizedSlots);

        ParentWidget<?> input = gui.place(new ParentWidget<>(), () -> local(gui.vendingLayout().input));
        input.child(
            IKey.str("IN · 实体输入")
                .color(NekoVendingTheme.MUTED)
                .asWidget()
                .pos(0, 4));
        for (int i = 0; i < MTENekoVendingMachineV2.INPUT_SLOTS; i++) {
            ModularSlot slot = new VendingInputSlot(
                multiblock.inputItems,
                i,
                this::depositCoins,
                multiblock::markTradeStateChanged).slotGroup("inputSlotGroup");
            final int index = i;
            ItemSlot itemSlot = new ItemSlot().slot(slot)
                .background(NekoVendingTheme.SLOT);
            gui.place(
                itemSlot,
                () -> new NekoVendingLayout.Rect(
                    (index % 16) * (gui.vendingLayout().input.width - 18) / 15,
                    24 + (index / 16) * 19,
                    18,
                    18));
            slot.changeListener(
                (newItem, onlyAmountChanged, client, init) -> {
                    if (!client && !init) gui.markTradeableStatusDirty();
                });
            gui.coinOps.getInputSlotRefs()
                .add(itemSlot);
            input.child(itemSlot);
        }
        input.child(
            NekoVendingTheme.button(
                () -> "弹出输入",
                () -> gui.coinOps.getEjectItemsSync()
                    .setValue(true))
                .right(72)
                .top(0)
                .size(68, 19)
                .addTooltipLine("把输入物品送往出货流程；不清空输出库存"));
        input.child(
            NekoVendingTheme.button(
                () -> "弹出两币",
                () -> gui.coinOps.getEjectAllCoinsSync()
                    .setValue(true))
                .right(0)
                .top(0)
                .size(68, 19)
                .addTooltipLine("弹出当前钱包的所有猫猫币"));
        input.setEnabledIf(w -> !gui.isEditModeActive());
        root.child(input);
        root.child(
            IKey.str("MiaoVision · 出货展示")
                .color(NekoVendingTheme.INK)
                .asWidget()
                .pos(8, 73)
                .setEnabledIf(w -> !gui.isEditModeActive()));
        sceneMount.setEnabledIf(w -> !gui.isEditModeActive());
        outputStatsMount.setEnabledIf(w -> !gui.isEditModeActive());
        root.child(sceneMount);
        root.child(outputStatsMount);
        root.child(
            IKey.str("选择左侧交易，编辑当前真实页面")
                .color(NekoVendingTheme.MUTED)
                .asWidget()
                .pos(9, 8)
                .setEnabledIf(w -> gui.isEditModeActive() && !gui.isEditOverlayOpen()));
        gui.place(sceneMount, () -> local(gui.vendingLayout().scene));
        gui.place(outputStatsMount, () -> local(gui.vendingLayout().outputStats));
        // Exclusions are individual non-catalog rectangles, never the enclosing right pane.
        input.excludeAreaInRecipeViewer();
        sceneMount.excludeAreaInRecipeViewer();
        outputStatsMount.excludeAreaInRecipeViewer();
        return gui.place(root, () -> gui.vendingLayout().rightPane);
    }

    private NekoVendingLayout.Rect local(NekoVendingLayout.Rect rect) {
        NekoVendingLayout.Rect parent = gui.vendingLayout().rightPane;
        return new NekoVendingLayout.Rect(rect.x - parent.x, rect.y - parent.y, rect.width, rect.height);
    }

}
