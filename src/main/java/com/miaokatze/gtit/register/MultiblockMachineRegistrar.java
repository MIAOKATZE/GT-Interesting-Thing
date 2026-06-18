package com.miaokatze.gtit.register;

import net.minecraft.util.StatCollector;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtit.common.machine.MTENekoVendingMachine;

/**
 * 多方块机器注册器
 * 负责注册猫猫售货机。
 */
public class MultiblockMachineRegistrar extends MachineRegistrar {

    @Override
    protected void setupRegistrations() {
        // 注册猫猫售货机 (继承自 VM 模组的 MTEVendingMachine)
        // 保留原版多方块结构、动画与交易逻辑，仅覆盖 GUI 与 Tooltip
        registerMachine(
            () -> new MTENekoVendingMachine(
                MetaTileEntityID.NEKO_VENDING_MACHINE.ID,
                "gtit.neko_vending_machine",
                StatCollector.translateToLocal("gtit.machine.neko_vending_machine")),
            GTITItemList.NekoVendingMachine);
    }
}
