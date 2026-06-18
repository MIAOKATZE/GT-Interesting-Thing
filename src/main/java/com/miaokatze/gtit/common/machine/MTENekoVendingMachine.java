package com.miaokatze.gtit.common.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import com.cubefury.vendingmachine.blocks.MTEVendingMachine;
import com.cubefury.vendingmachine.blocks.gui.MTEVendingMachineGui;
import com.cubefury.vendingmachine.blocks.gui.WalletMode;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.util.BigItemStack;
import com.miaokatze.gtit.common.machine.neko.NekoVendingMachineGui;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoTradeRegistry;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 猫猫售货机
 * <p>
 * 继承自 VM (VendingMachine) 模组的 {@link MTEVendingMachine}，保留原版的多方块结构、动画与交易处理逻辑，
 * 仅覆盖 GUI 与 Tooltip 显示。
 * <p>
 * 注意：{@link MTEVendingMachine} 已实现 ISurvivalConstructable、ISecondaryDescribable、IAlignment，
 * 无需在子类中重复声明。
 * <p>
 * 结构与父类一致：2x3x1 的 "cc", "c~", "cc" 形状，使用 Tin Item Pipe Casings 作为外壳。
 * <p>
 * 关键设计决策：使用 {@code @IMetaTileEntity.SkipGenerateDescription} 跳过 GregTech 的
 * MachineTooltipsLoader 描述注册，因为 MTEVendingMachine 的 getTooltip() 使用一次性构建器模式
 * （toolTipFinisher() 后 iLines 被置 null），子类在 super.getTooltip() 后再 addInfo() 会 NPE。
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTENekoVendingMachine extends MTEVendingMachine {

    /**
     * BGM 构建阶段标志位（已废弃）
     *
     * @deprecated 使用 {@link NekoVendingMachineGui#isNekoGuiOpen} 替代
     */
    @Deprecated
    public static boolean isBuildingNekoGui = false;

    public MTENekoVendingMachine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTENekoVendingMachine(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTENekoVendingMachine(this.mName);
    }

    @Override
    protected MTEVendingMachineGui getGui() {
        GTInterestingThing.LOG.info("[NEKO] getGui() called, creating NekoVendingMachineGui");
        return new NekoVendingMachineGui(this);
    }

    /**
     * 构建猫猫售货机的 Tooltip 信息
     * <p>
     * 不能在 super.getTooltip() 返回的 builder 上 addInfo()，因为父类的 getTooltip() 使用懒加载，
     * 第一次调用时已经执行了 toolTipFinisher()，此时 iLines 已被置 null。
     * 必须构建全新的 MultiblockTooltipBuilder。
     */
    @Override
    protected MultiblockTooltipBuilder getTooltip() {
        return new MultiblockTooltipBuilder().addMachineType("Neko Vending Machine")
            .addInfo("喵~ 猫猫售货机，用猫猫币购买物品！")
            .addInfo("结构、动画与交易逻辑继承自原版 VM 售货机。")
            .beginStructureBlock(2, 3, 1, false)
            .addController("Middle")
            .addOtherStructurePart("Tin Item Pipe Casings", "Everything except the controller")
            .addOtherStructurePart("ME Vending Uplink Hatch", "Any Pipe Casing, Optional")
            .addStructureInfo("Cannot be flipped onto its side")
            .toolTipFinisher();
    }

    /**
     * 覆盖 onRightclick 添加日志检查点
     */
    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        GTInterestingThing.LOG.info(
            "[NEKO] onRightclick: side={}, mMachine={}, player={}",
            aBaseMetaTileEntity.isServerSide() ? "server" : "client",
            this.mMachine,
            aPlayer.getCommandSenderName());
        boolean result = super.onRightclick(aBaseMetaTileEntity, aPlayer);
        GTInterestingThing.LOG.info("[NEKO] onRightclick result={}, getActive={}", result, this.getActive());
        return result;
    }

    @Override
    public boolean checkTrade(Trade trade, UUID player, WalletMode walletMode, boolean simulate) {
        NekoTradeRegistry.NekoTradeInfo nekoInfo = findNekoTradeInfo(trade);
        if (nekoInfo != null) {
            return checkNekoTrade(trade, player, nekoInfo, simulate);
        }
        return super.checkTrade(trade, player, walletMode, simulate);
    }

    private NekoTradeRegistry.NekoTradeInfo findNekoTradeInfo(Trade trade) {
        for (BigItemStack fromItem : trade.fromItems) {
            String currencyId = NekoCurrencyRegistrar.getNekoCurrencyId(fromItem.getBaseStack());
            if (currencyId != null) {
                return new NekoTradeRegistry.NekoTradeInfo(currencyId, fromItem.stackSize, null);
            }
        }
        return null;
    }

    private boolean checkNekoTrade(Trade trade, UUID player, NekoTradeRegistry.NekoTradeInfo nekoInfo,
        boolean simulate) {
        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(player);
        if (wallet == null) return false;

        int balance = wallet.getCount(nekoInfo.currencyId);
        if (balance < nekoInfo.cost) return false;

        if (!simulate) {
            wallet.addCount(nekoInfo.currencyId, -nekoInfo.cost);
            NekoWalletManager.INSTANCE.saveWallet(player);
            List<net.minecraft.item.ItemStack> toDispense = new ArrayList<>();
            for (BigItemStack toItem : trade.toItems) {
                toDispense.addAll(toItem.getCombinedStacks());
            }
            this.dispenseItemStacks(toDispense);
            this.playSoundEffect("vendingmachine:coin_insert");
        }
        return true;
    }
}
