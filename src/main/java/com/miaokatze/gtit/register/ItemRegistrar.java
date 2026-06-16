package com.miaokatze.gtit.register;

import static com.miaokatze.gtit.common.api.enums.GTITItemList.*;

import com.miaokatze.gtit.common.items.ElectricFloatCore;
import com.miaokatze.gtit.common.items.FloatCore;
import com.miaokatze.gtit.common.items.NekoCoin;
import com.miaokatze.gtit.common.items.StarterGift;
import com.miaokatze.gtit.common.items.TelekinesisOreScannerCore;
import com.miaokatze.gtit.common.items.rings.RingDistantGrasp;
import com.miaokatze.gtit.common.items.rings.RingDragonBreath;
import com.miaokatze.gtit.common.items.rings.RingGluttony;
import com.miaokatze.gtit.common.items.rings.RingIronheart;
import com.miaokatze.gtit.common.items.rings.RingMountainbreaker;
import com.miaokatze.gtit.common.items.rings.RingSkywalk;
import com.miaokatze.gtit.common.items.rings.RingTempest;
import com.miaokatze.gtit.common.items.rings.RingWindrider;
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

        // 戒指系列
        registerRingDistantGrasp();
        registerRingSkywalk();
        registerRingWindrider();
        registerRingGluttony();
        registerRingIronheart();
        registerRingDragonBreath();
        registerRingMountainbreaker();
        registerRingTempest();

        // 新手宝箱
        registerStarterGift();

        // 猫猫币
        registerNekoCoin();

        GTInterestingThing.LOG.info("物品注册完成。");
    }

    private static void registerTestCoin() {
        TestCoin.setAndRegister(com.miaokatze.gtit.common.items.TestCoin::new);
    }

    private static void registerTestCoinE() {
        // TestCoinE.setAndRegister(com.miaokatze.gtit.common.items.TestCoinE::new);
    }

    private static void registerFloatCore() {
        FloatCore.setAndRegister(FloatCore::new);
    }

    private static void registerElectricFloatCore() {
        ElectricFloatCore.setAndRegister(ElectricFloatCore::new);
    }

    private static void registerTelekinesisOreScannerCore() {
        TelekinesisOreScannerCore.setAndRegister(TelekinesisOreScannerCore::new);
    }

    // ========== 戒指注册 ==========

    private static void registerRingDistantGrasp() {
        RingDistantGrasp.setAndRegister(RingDistantGrasp::new);
    }

    private static void registerRingSkywalk() {
        RingSkywalk.setAndRegister(RingSkywalk::new);
    }

    private static void registerRingWindrider() {
        RingWindrider.setAndRegister(RingWindrider::new);
    }

    private static void registerRingGluttony() {
        RingGluttony.setAndRegister(RingGluttony::new);
    }

    private static void registerRingIronheart() {
        RingIronheart.setAndRegister(RingIronheart::new);
    }

    private static void registerRingDragonBreath() {
        RingDragonBreath.setAndRegister(RingDragonBreath::new);
    }

    private static void registerRingMountainbreaker() {
        RingMountainbreaker.setAndRegister(RingMountainbreaker::new);
    }

    private static void registerRingTempest() {
        RingTempest.setAndRegister(RingTempest::new);
    }

    // ========== 新手宝箱注册 ==========

    private static void registerStarterGift() {
        StarterGift.setAndRegister(StarterGift::new);
    }

    // ========== 猫猫币注册 ==========

    private static void registerNekoCoin() {
        NekoCoin.setAndRegister(NekoCoin::new);
    }
}
