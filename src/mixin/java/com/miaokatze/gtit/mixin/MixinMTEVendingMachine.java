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
import com.cubefury.vendingmachine.trade.TradeHistory;
import com.cubefury.vendingmachine.trade.TradeManager;
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
 * <p>
 * 注意：不直接 import BqAdapter/BqCondition，避免类加载时 BQ 未就绪导致崩溃。
 * quest 条件检查通过反射调用。
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

        // 检查 BetterQuesting 任务条件（必须在 currency 检查之前，确保对所有猫猫币交易生效）
        // 注意：不直接 import BqAdapter/BqCondition，避免类加载时 BQ 未就绪导致崩溃。
        // quest 条件检查通过 NekoTradeRegistry.checkBqQuestCompleted 反射调用 BqAdapter。
        if (nekoInfo.bqQuestId != null && !nekoInfo.bqQuestId.isEmpty()) {
            if (!NekoTradeRegistry.checkBqQuestCompleted(nekoInfo.bqQuestId, tradeRequest.player.getUniqueID())) {
                // 玩家未完成所需任务，拒绝交易
                cir.setReturnValue(false);
                return;
            }
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

        // 检查冷却（冷却随在线团队成员数缩放）
        // getMaxTradesInCooldown 返回在线团队成员数，作为冷却期内允许的最大交易次数
        if (tg.cooldown != -1) {
            TradeHistory history = TradeManager.INSTANCE.getTradeState(playerId, tg);
            long currentTimestamp = System.currentTimeMillis();
            long lastTradeTime = history.lastTrade;
            if (lastTradeTime != -1L
                && (currentTimestamp - lastTradeTime) / 1000L < tg.cooldown
                && history.cooldownTradeCount >= TradeManager.INSTANCE.getMaxTradesInCooldown(playerId)) {
                cir.setReturnValue(false);
                return;
            }
        }

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
        TradeManager.INSTANCE.executeTrade(playerId, tg);
        self.sendTradeUpdate();
        self.markDirty();

        self.playSoundEffect("vendingmachine:coin_insert");

        cir.setReturnValue(true);
    }
}
