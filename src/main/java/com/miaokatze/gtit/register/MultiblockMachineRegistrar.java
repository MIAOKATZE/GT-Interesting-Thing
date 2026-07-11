package com.miaokatze.gtit.register;

import net.minecraft.util.StatCollector;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtit.common.machine.MTENekoVendingMachine;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;

/**
 * 多方块机器注册器
 * 负责注册猫猫售货机 V1 和 V2。
 * <p>
 * V1: 继承自 VM 模组的 MTEVendingMachine，保留原版多方块结构与交易逻辑。
 * V2: 独立化版本，继承 GT5U 的 MTEEnhancedMultiBlockBase，完全脱离 VM 模组依赖。
 */
public class MultiblockMachineRegistrar extends MachineRegistrar {

    @Override
    protected void setupRegistrations() {
        // === V1: 注册猫猫售货机 (继承自 VM 模组的 MTEVendingMachine) ===
        // 保留原版多方块结构、动画与交易逻辑，仅覆盖 GUI 与 Tooltip
        registerMachine(
            () -> new MTENekoVendingMachine(
                MetaTileEntityID.NEKO_VENDING_MACHINE.ID,
                "gtit.neko_vending_machine",
                StatCollector.translateToLocal("gtit.machine.neko_vending_machine")),
            GTITItemList.NekoVendingMachine);

        // === V2: 注册猫猫售货机 V2 (独立化版本，继承 GT5U 的 MTEEnhancedMultiBlockBase) ===
        // 完全脱离 VM 模组，交易逻辑委托给 NekoTradeExecutor，GUI 使用 NekoVMGuiV2
        registerMachine(
            () -> new MTENekoVendingMachineV2(
                MetaTileEntityID.NEKO_VENDING_MACHINE_V2.ID,
                "gtit.neko_vending_machine_v2",
                StatCollector.translateToLocal("gtit.machine.neko_vending_machine_v2")),
            GTITItemList.NekoVendingMachineV2);
    }
}
