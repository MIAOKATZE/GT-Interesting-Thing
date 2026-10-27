package com.miaokatze.gtit.common.machine.v2;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.IGT_ItemWithMaterialRenderer;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;

/** Common-side geometry identities. Never loads a client renderer to choose a physical proxy. */
public final class NekoOutputModelShape {

    private static final ItemList[] ROTATING = { ItemList.Electric_Motor_UIV, ItemList.Electric_Piston_UIV,
        ItemList.Electric_Pump_UIV, ItemList.FluidRegulator_UIV, ItemList.Conveyor_Module_UIV, ItemList.Robot_Arm_UIV,
        ItemList.Emitter_UIV, ItemList.Sensor_UIV, ItemList.Field_Generator_UIV, ItemList.T4Sawblade };

    private NekoOutputModelShape() {}

    public static boolean tesseract(ItemStack stack) {
        return matches(ItemList.Tesseract, stack) || matches(ItemList.EnergisedTesseract, stack);
    }

    public static boolean rotatingMaterial(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        for (ItemList item : ROTATING) if (matches(item, stack)) return true;
        // Only GT material-dispatched items use the material renderer. A non-GT ore alias (or a GT tool
        // with its separate planar renderer) must not acquire a volume proxy from an ore association.
        if (!(stack.getItem() instanceof IGT_ItemWithMaterialRenderer)) return false;
        ItemData data = GTOreDictUnificator.getAssociation(stack);
        return data != null && data.mMaterial != null && data.mMaterial.mMaterial == Materials.TranscendentMetal;
    }

    public static boolean volumetric(ItemStack stack) {
        return stack != null && (stack.getItem() instanceof ItemBlock || tesseract(stack) || rotatingMaterial(stack));
    }

    private static boolean matches(ItemList item, ItemStack stack) {
        return stack != null && item.hasBeenSet() && item.isStackEqual(stack, false, true);
    }
}
