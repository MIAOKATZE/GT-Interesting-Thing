package com.miaokatze.gtit.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cubefury.vendingmachine.trade.TradeGroup;
import com.cubefury.vendingmachine.trade.TradeManager;
import com.miaokatze.gtit.trade.NekoTradeRegistry;

/**
 * Mixin 过滤原版VM中的猫猫币交易
 * <p>
 * 在 TradeManager.getAvailableTradeGroups() 返回结果后，
 * 移除猫猫币交易组，使其不出现在原版VM贸易机中。
 * 猫猫机通过 NekoVendingMachineGui.updateTradeDisplay() 手动构建交易显示。
 */
@Mixin(TradeManager.class)
public class MixinTradeManager {

    @Inject(
        method = "getAvailableTradeGroups(Ljava/util/UUID;)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false)
    private void filterNekoTrades(UUID player, CallbackInfoReturnable<List<TradeGroup>> cir) {
        List<TradeGroup> result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;

        // 过滤掉猫猫币交易
        List<TradeGroup> filtered = new ArrayList<>();
        for (TradeGroup tg : result) {
            if (!NekoTradeRegistry.isNekoTradeGroup(tg.getId())) {
                filtered.add(tg);
            }
        }

        // 如果有过滤掉的条目，替换返回值
        if (filtered.size() != result.size()) {
            cir.setReturnValue(filtered);
        }
    }
}
