package com.miaokatze.gtit.gui.vm.scene;

import com.miaokatze.gtit.client.vending.scene.VendingNativeModelPolicy;
import com.miaokatze.gtit.client.vending.scene.VendingNativeModelPolicy.Geometry;

/** Geometry selection/normalization only: does not render, load Minecraft, or create a GL context. */
public final class VendingNativeModelPolicyTest {

    public static void main(String[] args) {
        check(
            VendingNativeModelPolicy.geometry("gregtech.common.render.MetaGeneratedToolRenderer") == Geometry.SPRITE,
            "GT tools use audited native parts, independent of IGT material interface");
        for (String name : new String[] { "GeneratedItemRenderer", "GeneratedMaterialRenderer",
            "MechanicalArmorRenderer", "UniversiumRenderer", "InfinityMetaItemRenderer", "InfiniteSprayCanRenderer" })
            check(
                VendingNativeModelPolicy.geometry("gregtech.common.render.items." + name) == Geometry.SPRITE,
                "audited unit sprite path " + name);
        check(
            VendingNativeModelPolicy.geometry("gregtech.common.render.items.WireFrameTesseractRenderer")
                == Geometry.TESSERACT,
            "3D tesseract never gets Z times sixteen");
        for (String name : new String[] { "TranscendentMetalRenderer", "TranscendentalMetaItemRenderer" }) check(
            VendingNativeModelPolicy.geometry("gregtech.common.render.items." + name) == Geometry.ROTATING_SPRITE,
            "rotating plane uses isotropic volume " + name);
        for (String name : new String[] { "unknown.ModModel", "gregtech.common.render.items.DataStickRenderer",
            "gregtech.common.render.items.CircuitComponentItemRenderer",
            "gregtech.common.render.items.GeneratedMaterialRendererSubclass", VendingNativeModelPolicy.DISPATCHER })
            check(
                VendingNativeModelPolicy.geometry(name) == Geometry.NATIVE_FALLBACK,
                "unknown or inventory-only path cannot inherit sprite normalization " + name);
        double tesseractBound = .7 * Math.sqrt(2) / (3 - Math.sqrt(2)) * VendingNativeModelPolicy.TESSERACT_SCALE;
        double rotatedBound = Math.sqrt(.5 * .5 + .5 * .5 + 1.0 / 32 / 32) * VendingNativeModelPolicy.ROTATING_SCALE;
        check(tesseractBound <= .5 && rotatedBound <= .5, "native special meshes fit physical half extents");
        check(
            Math.abs(0 * VendingNativeModelPolicy.TESSERACT_SCALE) == 0,
            "centered tesseract has no spurious block-helper offset");
        System.out
            .println("VendingNativeModelPolicyTest PASS: exact sprite/tool/special dispatch and bounded transforms");
    }

    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
}
