package com.miaokatze.gtit.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cubefury.vendingmachine.blocks.MTEVendingMachine;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.trade.TradeGroup;
import com.cubefury.vendingmachine.trade.TradeRequest;
import com.cubefury.vendingmachine.util.BigItemStack;
import com.miaokatze.gtit.trade.NekoTradeRegistry;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

/**
 * Mixin 拦截 MTEVendingMachine.processTradeOnServer
 * <p>
 * processTradeOnServer 是 private 方法，无法在子类中覆盖。
 * 通过 Mixin 在方法开头注入猫猫币交易逻辑：
 * - 如果是猫猫币交易，扣减 NekoWallet 余额，出货，返回 true
 * - 如果不是猫猫币交易，继续执行原方法
 */
@Mixin(MTEVendingMachine.class)
public class MixinMTEVendingMachine {

    @Inject(method = "processTradeOnServer", at = @At("HEAD"), cancellable = true, remap = false)
    private void onProcessTradeOnServer(TradeRequest tradeRequest, CallbackInfoReturnable<Boolean> cir) {
        if (tradeRequest == null) return;

        UUID tgId = tradeRequest.tradeGroup;
        NekoTradeRegistry.NekoTradeInfo nekoInfo = NekoTradeRegistry.NEKO_TRADES.get(tgId);

        if (nekoInfo == null) {
            // 非猫猫币交易，继续执行原方法
            return;
        }

        // 纯物品交换交易（currencyId=null, cost=0），走原版逻辑
        if (nekoInfo.currencyId == null || nekoInfo.cost <= 0) {
            return;
        }

        // 猫猫币交易
        MTEVendingMachine self = (MTEVendingMachine) (Object) this;
        UUID playerId = tradeRequest.player.getUniqueID();

        // 检查 NekoWallet 余额
        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
        if (wallet == null) {
            cir.setReturnValue(false);
            return;
        }

        int balance = wallet.getCount(nekoInfo.currencyId);
        if (balance < nekoInfo.cost) {
            cir.setReturnValue(false);
            return;
        }

        // 获取 TradeGroup 和 Trade
        TradeGroup tg = TradeDatabase.INSTANCE.getTradeGroupFromId(tgId);
        if (tg == null || tradeRequest.tradeGroupOrder >= tg.getTrades()
            .size()) {
            cir.setReturnValue(false);
            return;
        }

        Trade trade = tg.getTrades()
            .get(tradeRequest.tradeGroupOrder);

        // 混合交易：先扣减猫猫币，然后走原版逻辑处理 fromItems
        if (!trade.fromItems.isEmpty()) {
            wallet.addCount(nekoInfo.currencyId, -nekoInfo.cost);
            NekoWalletManager.INSTANCE.saveWallet(playerId);
            return;
        }

        // 纯猫猫币交易：扣减猫猫币，出货
        wallet.addCount(nekoInfo.currencyId, -nekoInfo.cost);
        NekoWalletManager.INSTANCE.saveWallet(playerId);

        for (BigItemStack toItem : trade.toItems) {
            if (toItem == null) continue;
            self.dispenseItemStacks(toItem.getCombinedStacks());
        }

        // 更新交易历史
        com.cubefury.vendingmachine.trade.TradeManager.INSTANCE.executeTrade(playerId, tg);
        self.sendTradeUpdate();
        self.markDirty();

        self.playSoundEffect("vendingmachine:coin_insert");

        cir.setReturnValue(true);
    }
}
