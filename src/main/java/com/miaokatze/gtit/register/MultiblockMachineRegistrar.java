package com.miaokatze.gtit.register;

import net.minecraft.util.StatCollector;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;

/**
 * 多方块机器注册器
 * <p>
 * V1 猫猫售货机（继承自 VM 模组的 MTEVendingMachine）已彻底移除，
 * V2 完全替代了 V1 的功能：独立化版本，继承 GT5U 的 MTEEnhancedMultiBlockBase，
 * 完全脱离 VM 模组依赖。交易逻辑委托给 NekoTradeExecutor，GUI 使用 NekoVMGuiV2。
 * <p>
 * 注意：MetaTileEntityID.NEKO_VENDING_MACHINE 枚举值保留用于存档兼容性
 * （已放置的 V1 机器方块需要 ID 存在才能正确卸载），但不再注册新的 V1 机器实例。
 */
public class MultiblockMachineRegistrar extends MachineRegistrar {

    @Override
    protected void setupRegistrations() {
        // === V2: 注册猫猫售货机 V2 (独立化版本，继承 GT5U 的 MTEEnhancedMultiBlockBase) ===
        // 完全脱离 VM 模组，交易逻辑委托给 NekoTradeExecutor，GUI 使用 NekoVMGuiV2
        // V1 已移除，V2 是唯一的猫猫售货机实现
        registerMachine(
            () -> new MTENekoVendingMachineV2(
                MetaTileEntityID.NEKO_VENDING_MACHINE_V2.ID,
                "gtit.neko_vending_machine_v2",
                StatCollector.translateToLocal("gtit.machine.neko_vending_machine_v2")),
            GTITItemList.NekoVendingMachineV2);
    }
}
