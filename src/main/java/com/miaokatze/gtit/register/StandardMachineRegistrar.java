package com.miaokatze.gtit.register;

import net.minecraft.util.StatCollector;

/**
 * 标准机器注册器
 * 继承自 MachineRegistrar，负责具体定义并注册 EV、IV 和 LuV 等级的单方块测试机器。
 */
public class StandardMachineRegistrar extends MachineRegistrar {

    /**
     * 设置测试机器的注册项
     * 在此处定义每台机器的 ID、名称、本地化显示名以及对应的物品索引
     */
    @Override
    protected void setupRegistrations() {
        // 注册 EV 等级测试机器 (Tier 4)
        // registerMachine(
        // () -> new MTETestMachine(
        // MTETEST_EV.ID,
        // "gtit.mtetest.ev",
        // StatCollector.translateToLocal("gtit.machine.test.ev"),
        // 4),
        // Test_Machine_EV);

        // 注册 IV 等级测试机器 (Tier 5)
        // registerMachine(
        // () -> new MTETestMachine(
        // MTETEST_IV.ID,
        // "gtit.mtetest.iv",
        // StatCollector.translateToLocal("gtit.machine.test.iv"),
        // 5),
        // Test_Machine_IV);

        // 注册 LuV 等级测试机器 (Tier 6)
        // registerMachine(
        // () -> new MTETestMachine(
        // MTETEST_LuV.ID,
        // "gtit.mtetest.luv",
        // StatCollector.translateToLocal("gtit.machine.test.luv"),
        // 6),
        // Test_Machine_LuV);
    }
}
