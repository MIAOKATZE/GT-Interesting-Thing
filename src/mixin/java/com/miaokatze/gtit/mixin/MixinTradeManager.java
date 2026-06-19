package com.miaokatze.gtit.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cubefury.vendingmachine.trade.TradeGroup;
import com.cubefury.vendingmachine.trade.TradeManager;

/**
 * Mixin 注入 TradeManager.getAvailableTradeGroups
 * <p>
 * 注意：不能过滤 getAvailableTradeGroups 的返回值！
 * 因为 canExecuteTrade 依赖 getAvailableTradeGroups(player).contains(tg) 来判断交易是否可用。
 * 如果过滤掉猫猫币交易，canExecuteTrade 会返回 false，导致猫猫机交易无法执行。
 * <p>
 * 原版VM中猫猫币交易的显示过滤由 MixinTradeMainPanel 在 formatTrades 层面处理。
 */
@Mixin(TradeManager.class)
public class MixinTradeManager {

    @Inject(
        method = "getAvailableTradeGroups(Ljava/util/UUID;)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false)
    private void filterNekoTrades(java.util.UUID player, CallbackInfoReturnable<List<TradeGroup>> cir) {
        // 不过滤 getAvailableTradeGroups 的返回值
        // 原因：canExecuteTrade 依赖 getAvailableTradeGroups(player).contains(tg)
    }
}
