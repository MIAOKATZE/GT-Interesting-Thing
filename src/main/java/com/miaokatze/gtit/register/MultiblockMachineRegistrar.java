package com.miaokatze.gtit.register;

import net.minecraft.util.StatCollector;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;

/**
 * 多方块机器注册器
 * <p>
 * V2 猫猫售货机继承 V1 的 ID(14610)、basicName、本地化键、物品容器，
 * 确保旧存档兼容（v1.6.19 未发布，无 V2 方块需保护，全部继承）。
 * V2 是独立化版本，继承 GT5U 的 MTEEnhancedMultiBlockBase，完全脱离 VM 模组依赖。
 * 交易逻辑委托给 NekoTradeExecutor，GUI 使用 NekoVMGuiV2。
 */
public class MultiblockMachineRegistrar extends MachineRegistrar {

    @Override
    protected void setupRegistrations() {
        // === 注册猫猫售货机 (V2 独立化版本，继承 GT5U 的 MTEEnhancedMultiBlockBase) ===
        // 完全脱离 VM 模组，交易逻辑委托给 NekoTradeExecutor，GUI 使用 NekoVMGuiV2
        // V2 接管 V1 的 ID(14610)、basicName、本地化键、物品容器
        // 使旧 V1 玩家更新后方块不丢失（v1.6.19 未发布，无 V2 方块需保护）
        registerMachine(
            () -> new MTENekoVendingMachineV2(
                MetaTileEntityID.NEKO_VENDING_MACHINE_V2.ID,
                "gtit.neko_vending_machine",
                StatCollector.translateToLocal("gtit.machine.neko_vending_machine")),
            GTITItemList.NekoVendingMachine);
    }
}
