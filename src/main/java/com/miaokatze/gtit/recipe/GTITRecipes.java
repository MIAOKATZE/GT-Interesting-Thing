package com.miaokatze.gtit.recipe;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;

import com.miaokatze.gtit.common.api.enums.GTITItemList;

import cpw.mods.fml.common.registry.GameRegistry;
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
}
