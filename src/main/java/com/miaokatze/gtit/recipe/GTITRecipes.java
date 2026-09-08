package com.miaokatze.gtit.recipe;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

public class GTITRecipes {

    public static void init() {
        addFloatCoreRecipe();
        addElectricFloatCoreRecipe();
        addTelekinesisOreScannerCoreRecipe();
        addRingWindriderRecipe();
        addNekoVendingMachineRecipe();
        addReincarnationCrystalRecipe();
    }

    private static void addFloatCoreRecipe() {
        if (GTITItemList.FloatCore.get(1) == null) {
            return;
        }

        GameRegistry.addShapedRecipe(
            GTITItemList.FloatCore.get(1),
            "FSF",
            "SBS",
            "FSF",
            'F',
            Items.feather,
            'S',
            Items.stick,
            'B',
            Items.cooked_beef);
    }

    private static void addElectricFloatCoreRecipe() {
        if (GTITItemList.ElectricFloatCore.get(1) == null || GTITItemList.FloatCore.get(1) == null) {
            return;
        }

        GameRegistry.addShapedRecipe(
            GTITItemList.ElectricFloatCore.get(1),
            "BSB",
            "SFS",
            "BSB",
            'B',
            ItemList.Battery_RE_LV_Lithium.get(1L),
            'S',
            GTOreDictUnificator.get(OrePrefixes.plateDouble, Materials.Steel, 1L),
            'F',
            GTITItemList.FloatCore.get(1));
    }

    private static void addTelekinesisOreScannerCoreRecipe() {
        if (GTITItemList.TelekinesisOreScannerCore.get(1) == null) {
            return;
        }

        GameRegistry.addShapedRecipe(
            GTITItemList.TelekinesisOreScannerCore.get(1),
            "FSF",
            "SBS",
            "FSF",
            'F',
            Items.flint,
            'S',
            Blocks.cobblestone,
            'B',
            Items.cooked_beef);
    }

    /**
     * 御风戒指合成配方
     * 3x3工作台：7种戒指 + 2猫猫币
     * 遥握 饕餮 凌步
     * 猫币 磐躯 猫币
     * 龙息 裂山 疾风
     */
    private static void addRingWindriderRecipe() {
        if (GTITItemList.RingWindrider.get(1) == null) return;

        GameRegistry.addShapedRecipe(
            GTITItemList.RingWindrider.get(1),
            "ABC",
            "EID",
            "FGH",
            'A',
            GTITItemList.RingDistantGrasp.get(1),
            'B',
            GTITItemList.RingGluttony.get(1),
            'C',
            GTITItemList.RingSkywalk.get(1),
            'D',
            GTITItemList.NekoCoin.get(1),
            'E',
            GTITItemList.NekoCoin.get(1),
            'F',
            GTITItemList.RingDragonBreath.get(1),
            'G',
            GTITItemList.RingMountainbreaker.get(1),
            'H',
            GTITItemList.RingTempest.get(1),
            'I',
            GTITItemList.RingIronheart.get(1));
    }

    /**
     * 猫猫售货机合成配方（无序合成）
     * 输入：1 VM 原版售货机 + 1 猫猫币 → 1 猫猫售货机
     * 无序合成便于玩家合成，降低门槛
     */
    private static void addNekoVendingMachineRecipe() {
        if (GTITItemList.NekoVendingMachine.get(1) == null) return;

        ItemStack vmStack = GregTechAPI.METATILEENTITIES[2741] != null
            ? GregTechAPI.METATILEENTITIES[2741].getStackForm(1L)
            : null;
        if (vmStack == null) return;

        // 无序合成：1 VM贸易机 + 1 猫猫币
        GameRegistry.addShapelessRecipe(GTITItemList.NekoVendingMachine.get(1), vmStack, GTITItemList.NekoCoin.get(1));
    }

    /**
     * 轮回水晶合成配方（周目系统）
     * 3x3工作台：ABA / BCB / ABA
     * A=闪烁猫猫币 B=猫猫币 C=钻石
     * 物理专用服务器上周目系统整体拒绝注册
     */
    private static void addReincarnationCrystalRecipe() {
        // 周目系统门控：物理专用服务器拒绝注册
        if (FMLCommonHandler.instance()
            .getSide() == Side.SERVER) {
            GTInterestingThing.LOG.info("[reincarnation] 物理专用服务器：周目系统拒绝注册（物品/配方）");
            return;
        }
        if (GTITItemList.ReincarnationCrystal.get(1) == null) return;

        GameRegistry.addShapedRecipe(
            GTITItemList.ReincarnationCrystal.get(1),
            "ABA",
            "BCB",
            "ABA",
            'A',
            GTITItemList.ShimmeringNekoCoin.get(1),
            'B',
            GTITItemList.NekoCoin.get(1),
            'C',
            new ItemStack(Items.diamond));
    }
}
