package com.miaokatze.gtit.register;

import static com.miaokatze.gtit.common.api.enums.GTITItemList.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.items.ElectricFloatCore;
import com.miaokatze.gtit.common.items.FloatCore;
import com.miaokatze.gtit.common.items.NekoCoin;
import com.miaokatze.gtit.common.items.ReincarnationCrystal;
import com.miaokatze.gtit.common.items.ShimmeringNekoCoin;
import com.miaokatze.gtit.common.items.StarterGift;
import com.miaokatze.gtit.common.items.TelekinesisOreScannerCore;
import com.miaokatze.gtit.common.items.infinitycell.ItemInfinityStorageCell;
import com.miaokatze.gtit.common.items.infinitycell.ItemInfinityStorageFluidCell;
import com.miaokatze.gtit.common.items.infinitycell.ItemNekoInfinityStorageUnit;
import com.miaokatze.gtit.common.items.pocket.ItemNekoDimensionPocket;
import com.miaokatze.gtit.common.items.pocket.ItemPocketUpgrade;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.common.items.rings.RingDistantGrasp;
import com.miaokatze.gtit.common.items.rings.RingDragonBreath;
import com.miaokatze.gtit.common.items.rings.RingGluttony;
import com.miaokatze.gtit.common.items.rings.RingIronheart;
import com.miaokatze.gtit.common.items.rings.RingMountainbreaker;
import com.miaokatze.gtit.common.items.rings.RingSkywalk;
import com.miaokatze.gtit.common.items.rings.RingTempest;
import com.miaokatze.gtit.common.items.rings.RingWindrider;
import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

/**
 * 物品注册器
 * 负责模组内所有普通物品（非机器方块）的注册与初始化逻辑
 */
public class ItemRegistrar {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /**
     * 初始化并注册所有物品
     */
    public static void init() {
        LOG.info("开始通过 ItemRegistrar 注册物品...");
        registerFloatCore();
        registerElectricFloatCore();
        registerTelekinesisOreScannerCore();

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

        // 闪烁猫猫币
        registerShimmeringNekoCoin();

        // 轮回水晶（周目系统）
        registerReincarnationCrystal();

        // Infinity Cell 系列
        registerInfinityCell();
        registerInfinityFluidCell();
        registerNekoInfinityStorageUnit();

        // 猫猫次元口袋
        registerNekoDimensionPocket();

        // 口袋升级插件（R95）
        registerNekoPocketUpgrades();

        LOG.info("物品注册完成。");
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

    private static void registerShimmeringNekoCoin() {
        ShimmeringNekoCoin.setAndRegister(ShimmeringNekoCoin::new);
    }

    // ========== 轮回水晶注册（周目系统） ==========

    private static void registerReincarnationCrystal() {
        // 周目系统门控：物理专用服务器拒绝注册（物品注册与创造标签页加入均在下方这一句内完成）
        if (FMLCommonHandler.instance()
            .getSide() == Side.SERVER) {
            GTInterestingThing.LOG.info("[reincarnation] 物理专用服务器：周目系统拒绝注册（物品/配方）");
            return;
        }
        ReincarnationCrystal.setAndRegister(ReincarnationCrystal::new);
    }

    // ========== Infinity Cell 注册 ==========

    private static void registerInfinityCell() {
        InfinityCell.setAndRegister(ItemInfinityStorageCell::new);
    }

    private static void registerInfinityFluidCell() {
        InfinityFluidCell.setAndRegister(ItemInfinityStorageFluidCell::new);
    }

    private static void registerNekoInfinityStorageUnit() {
        NekoInfinityStorageUnit.setAndRegister(ItemNekoInfinityStorageUnit::new);
    }

    // ========== 猫猫次元口袋注册 ==========

    private static void registerNekoDimensionPocket() {
        NekoDimensionPocket.setAndRegister(ItemNekoDimensionPocket::new);
    }

    // ========== 口袋升级插件注册（R95 升级插件体系） ==========

    /**
     * ★R96 S8：魔法使型插件的<b>旧注册名</b>（改名前的 token 就是下面这串字面量去掉族前缀的那一截）。
     * <p>
     * 全仓<b>唯一</b>一处这个字面量，且只喂给 {@code GameRegistry.registerItem} 的注册名参数——它的作用是
     * 占住 Forge ID 表里那个旧键，让改名不释放、不旁落旧数字 ID（存档里存的是数字 ID，
     * {@code ItemStack.java:177}；注册名才是 ID 表的键，{@code GTITItemList:177-181}）。
     * 它<b>不是</b> lang 键、<b>不是</b>贴图名：影子实例的 unlocalized 与贴图都走 {@code mage} 那一族。
     */
    private static final String LEGACY_REGISTER_NAME_MAGE = "neko_pocket_upgrade_distill_fast";

    /**
     * 五型插件各注册一件（{@code ItemPocketUpgrade} 构造注入 {@code PocketUpgradeType}）。
     * <p>
     * ★注册名由物品自己的 unlocalized 派生（{@code neko_pocket_upgrade_<token>}，
     * {@code setAndRegister} 的 null 分支），本方法不另传注册名——两处名字就是一处真相。
     * <p>
     * ★<b>尾巴那一支是影子注册，不许改成 {@code setAndRegister}</b>：{@code GTITItemList:185} 的
     * {@code CreativeTabManager.addItemToTab(get(1))} 是<b>无条件加创造栏</b>，走 {@code setAndRegister}
     * 会让创造页多出一件重复物品（新缺陷），且 {@code set(item)} 还会把枚举项指到影子实例上。
     */
    private static void registerNekoPocketUpgrades() {
        NekoPocketUpgradeCapacity.setAndRegister(() -> new ItemPocketUpgrade(PocketUpgradeType.CAPACITY));
        NekoPocketUpgradeStack.setAndRegister(() -> new ItemPocketUpgrade(PocketUpgradeType.STACK));
        NekoPocketUpgradeMagnet.setAndRegister(() -> new ItemPocketUpgrade(PocketUpgradeType.MAGNET));
        NekoPocketUpgradeChannelPersist.setAndRegister(() -> new ItemPocketUpgrade(PocketUpgradeType.CHANNEL_PERSIST));
        NekoPocketUpgradeMage.setAndRegister(() -> new ItemPocketUpgrade(PocketUpgradeType.MAGE));
        // ★R96 S8（EVA-3 候选 C）：改名破档的防护腿。旧注册名由这一支钉住 ⇒ Forge 的 ID 键还在、
        // 旧数字 ID 不被释放给别的 mod 复用；影子实例的 type 同样是 MAGE，而槽位识别走
        // ItemPocketUpgrade#getType 的实例 type 字段（不看注册名）⇒ 旧档那一栈读回来还是"这一型插件"，
        // 名字与像素都显示成魔法使（它的 unlocalized 就是 mage 那一族的键，不需要额外的别名文案）。
        GameRegistry.registerItem(new ItemPocketUpgrade(PocketUpgradeType.MAGE), LEGACY_REGISTER_NAME_MAGE);
    }
}
