package com.miaokatze.gtit.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cubefury.vendingmachine.blocks.gui.MTEVendingMachineGui;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplay;
import com.cubefury.vendingmachine.blocks.gui.TradeMainPanel;
import com.cubefury.vendingmachine.trade.TradeCategory;
import com.miaokatze.gtit.trade.NekoTradeRegistry;

/**
 * Mixin 过滤原版VM GUI 中的猫猫币交易显示
 * <p>
 * 原版VM的 TradeMainPanel.formatTrades() 会将所有 tradeData 中的交易按分类分组显示，
 * 包括猫猫币交易。这个 Mixin 在 formatTrades 返回结果后，过滤掉猫猫币交易。
 * <p>
 * 注意：不能过滤 getAvailableTradeGroups，因为 canExecuteTrade 依赖它。
 * 只能在 GUI 显示层面过滤。
 */
@Mixin(TradeMainPanel.class)
public class MixinTradeMainPanel {

    @Shadow(remap = false)
    private MTEVendingMachineGui gui;

    /**
     * 在 formatTrades 返回后过滤猫猫币交易
     * <p>
     * 只在非猫猫机 GUI 中过滤。猫猫机使用 NekoVendingMachineGui，
     * 它覆盖了 updateTradeDisplay，不依赖 formatTrades 的结果。
     */
    @Inject(method = "formatTrades()Ljava/util/Map;", at = @At("RETURN"), cancellable = true, remap = false)
    private void filterNekoTradesFromDisplay(CallbackInfoReturnable<Map<TradeCategory, List<TradeItemDisplay>>> cir) {
        // 只在非猫猫机 GUI 中过滤
        // 猫猫机的 NekoVendingMachineGui 不使用 formatTrades 的结果
        // 它覆盖了 updateTradeDisplay，手动构建交易显示
        Map<TradeCategory, List<TradeItemDisplay>> trades = cir.getReturnValue();
        if (trades == null || trades.isEmpty()) return;

        boolean filtered = false;
        for (Map.Entry<TradeCategory, List<TradeItemDisplay>> entry : trades.entrySet()) {
            List<TradeItemDisplay> original = entry.getValue();
            List<TradeItemDisplay> filteredList = new ArrayList<>();
            for (TradeItemDisplay tid : original) {
                if (!NekoTradeRegistry.isNekoTradeGroup(tid.tgID)) {
                    filteredList.add(tid);
                } else {
                    filtered = true;
                }
            }
            entry.setValue(filteredList);
        }
    }
}
