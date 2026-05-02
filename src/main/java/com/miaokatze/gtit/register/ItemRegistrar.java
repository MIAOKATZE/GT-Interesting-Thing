package com.miaokatze.gtit.register;

import static com.miaokatze.gtit.common.api.enums.GTITItemList.ElectricFloatCore;
import static com.miaokatze.gtit.common.api.enums.GTITItemList.FloatCore;
import static com.miaokatze.gtit.common.api.enums.GTITItemList.TelekinesisOreScannerCore;
import static com.miaokatze.gtit.common.api.enums.GTITItemList.TestCoin;

import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 物品注册器
 * 负责模组内所有普通物品（非机器方块）的注册与初始化逻辑
 */
public class ItemRegistrar {

    /**
     * 初始化并注册所有物品
     */
    public static void init() {
        GTInterestingThing.LOG.info("开始通过 ItemRegistrar 注册物品...");
        // registerTestCoin(); // 取消测试物品注册，源码保留
        registerFloatCore();
        registerElectricFloatCore();
        registerTelekinesisOreScannerCore();
        // registerTestCoinE(); // 取消测试物品注册，源码保留
        GTInterestingThing.LOG.info("物品注册完成。");
    }

    /**
     * 注册测试硬币
     */
    private static void registerTestCoin() {
        TestCoin.setAndRegister(com.miaokatze.gtit.common.items.TestCoin::new);
    }

    /**
     * 注册电子测试硬币
     */
    private static void registerTestCoinE() {
        // TestCoinE.setAndRegister(com.miaokatze.gtit.common.items.TestCoinE::new); // 取消测试物品注册，源码保留
    }

    private static void registerFloatCore() {
        FloatCore.setAndRegister(com.miaokatze.gtit.common.items.FloatCore::new);
    }

    private static void registerElectricFloatCore() {
        ElectricFloatCore.setAndRegister(com.miaokatze.gtit.common.items.ElectricFloatCore::new);
    }

    private static void registerTelekinesisOreScannerCore() {
        TelekinesisOreScannerCore.setAndRegister(com.miaokatze.gtit.common.items.TelekinesisOreScannerCore::new);
    }
}
