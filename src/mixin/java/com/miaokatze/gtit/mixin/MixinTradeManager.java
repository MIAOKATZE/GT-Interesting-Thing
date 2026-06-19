package com.miaokatze.gtit.mixin;

import java.util.List;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cubefury.vendingmachine.trade.TradeGroup;
import com.cubefury.vendingmachine.trade.TradeManager;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoTradeRegistry;

/**
 * Mixin 过滤原版VM中的猫猫币交易
 * <p>
 * 注意：不能过滤 getAvailableTradeGroups 的返回值！
 * 因为 canExecuteTrade 依赖 getAvailableTradeGroups(player).contains(tg) 来判断交易是否可用。
 * 如果过滤掉猫猫币交易，canExecuteTrade 会返回 false，导致猫猫机交易无法执行。
 * <p>
 * 正确的做法：在原版VM的 GUI 显示时过滤（MTEVendingMachineGui.updateTradeDisplay），
 * 而不是在 getAvailableTradeGroups 中过滤。
 * <p>
 * 但由于我们无法修改 MTEVendingMachineGui，所以这里保留过滤逻辑，
 * 但改为：只在非猫猫机上下文中过滤。
 * <p>
 * 当前方案：不过滤 getAvailableTradeGroups，让猫猫币交易保留在返回值中。
 * 原版VM的 GUI 通过 TradeCategory 分组显示，猫猫币交易的 category 是自定义的，
 * 不会与原版VM的交易混淆。猫猫机的 GUI 通过 NekoVendingMachineGui.updateTradeDisplay
 * 手动构建交易显示。
 */
@Mixin(TradeManager.class)
public class MixinTradeManager {

    @Inject(
        method = "getAvailableTradeGroups(Ljava/util/UUID;)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false)
    private void filterNekoTrades(UUID player, CallbackInfoReturnable<List<TradeGroup>> cir) {
        // 不再过滤 getAvailableTradeGroups 的返回值
        // 原因：canExecuteTrade 依赖 getAvailableTradeGroups(player).contains(tg)
        // 如果过滤掉猫猫币交易，canExecuteTrade 返回 false，猫猫机交易无法执行
        // 猫猫币交易在原版VM中的显示由 TradeCategory 分组控制
        List<TradeGroup> result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;

        // [DEBUG LOG] 检查猫猫币交易是否在 availableTradeGroups 中
        long nekoCount = result.stream()
            .filter(tg -> NekoTradeRegistry.isNekoTradeGroup(tg.getId()))
            .count();
        if (nekoCount > 0) {
            GTInterestingThing.LOG
                .info("[NEKO] getAvailableTradeGroups: 猫猫币交易组数量={}, 总交易组数量={}", nekoCount, result.size());
        }
    }
}
