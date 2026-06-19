package com.miaokatze.gtit.common.machine;

import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import com.cubefury.vendingmachine.blocks.MTEVendingMachine;
import com.cubefury.vendingmachine.blocks.gui.MTEVendingMachineGui;
import com.cubefury.vendingmachine.blocks.gui.WalletMode;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.trade.TradeGroup;
import com.miaokatze.gtit.common.machine.neko.NekoVendingMachineGui;
import com.miaokatze.gtit.main.GTInterestingThing;
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
 * 猫猫币交易机制：
 * - 猫猫币不放入 Trade.fromItems，只记录在 NekoTradeInfo 中
 * - checkTrade 覆盖：对猫猫币交易，先检查 NekoWallet 余额，再检查 fromItems 中的普通物品
 * - processTradeOnServer 是 private 方法，通过 MixinMTEVendingMachine 拦截，扣减 NekoWallet
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

    /**
     * 覆盖 checkTrade，对猫猫币交易进行特殊处理
     * <p>
     * 猫猫币交易的检查逻辑：
     * 1. 通过 NEKO_TRADES 遍历，用 tradeIndex 匹配猫猫币交易
     * 2. 对猫猫币交易，先检查 NekoWallet 余额是否足够
     * 3. 再调用 super.checkTrade 检查 fromItems 中的普通物品需求
     * <p>
     * 注意：Trade 对象没有 equals/hashCode，NBT 重新加载后引用会变，
     * 所以不能使用 tg.getTrades().contains(trade)。
     * 改用 tradeIndex（在 TradeGroup 中的索引）来匹配。
     */
    @Override
    public boolean checkTrade(Trade trade, UUID player, WalletMode walletMode, boolean simulate) {
        // 遍历 NEKO_TRADES，查找匹配的猫猫币交易
        for (Map.Entry<UUID, NekoTradeRegistry.NekoTradeInfo> entry : NekoTradeRegistry.NEKO_TRADES.entrySet()) {
            UUID tgId = entry.getKey();
            NekoTradeRegistry.NekoTradeInfo nekoInfo = entry.getValue();
            TradeGroup tg = TradeDatabase.INSTANCE.getTradeGroupFromId(tgId);

            if (tg == null) continue;

            // 通过索引遍历匹配（避免引用比较问题）
            for (int i = 0; i < tg.getTrades()
                .size(); i++) {
                Trade tgTrade = tg.getTrades()
                    .get(i);
                if (tgTrade == trade) {
                    // 找到猫猫机交易（猫猫币或物品交换）
                    if (nekoInfo.currencyId != null && nekoInfo.cost > 0) {
                        // 猫猫币交易：检查 NekoWallet 余额
                        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(player);
                        if (wallet == null) {
                            GTInterestingThing.LOG.info("[NEKO] checkTrade: 猫猫币交易, 无钱包, simulate={}", simulate);
                            return false;
                        }
                        int balance = wallet.getCount(nekoInfo.currencyId);
                        boolean canAfford = balance >= nekoInfo.cost;
                        if (!canAfford) {
                            GTInterestingThing.LOG.info(
                                "[NEKO] checkTrade: 猫猫币余额不足, balance={}, cost={}, simulate={}",
                                balance,
                                nekoInfo.cost,
                                simulate);
                            return false;
                        }
                    }

                    // 猫猫币余额足够（或无猫猫币需求），继续检查 fromItems
                    if (trade.fromItems.isEmpty()) {
                        // 纯猫猫币交易，直接返回 true
                        return true;
                    }
                    // 有普通物品需求，调用 super.checkTrade 检查
                    boolean superResult = super.checkTrade(trade, player, walletMode, simulate);
                    // [DEBUG LOG] 诊断物品交换交易失败原因
                    GTInterestingThing.LOG.info(
                        "[NEKO] checkTrade: 猫猫机交易(有fromItems), superResult={}, fromItems.size={}, simulate={}, currencyId={}",
                        superResult,
                        trade.fromItems.size(),
                        simulate,
                        nekoInfo.currencyId);
                    return superResult;
                }
            }
        }

        // 非猫猫机交易：走原版逻辑
        return super.checkTrade(trade, player, walletMode, simulate);
    }
}
