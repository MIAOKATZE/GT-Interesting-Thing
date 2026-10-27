package com.miaokatze.gtit.client.vending.scene;

/** Exact GT5 5.09.54.205 geometry contracts, not an instanceof assumption about every custom material. */
public final class VendingNativeModelPolicy {

    public enum Geometry {
        SPRITE,
        TESSERACT,
        ROTATING_SPRITE,
        NATIVE_FALLBACK
    }

    public static final String DISPATCHER = "gregtech.common.render.items.MetaGeneratedItemRenderer";
    public static final double TESSERACT_SCALE = .8, ROTATING_SCALE = .65;

    private static final java.util.Set<String> SPRITES = new java.util.HashSet<>(
        java.util.Arrays.asList(
            "gregtech.common.render.MetaGeneratedToolRenderer",
            "gregtech.common.render.items.GeneratedItemRenderer",
            "gregtech.common.render.items.GeneratedMaterialRenderer",
            "gregtech.common.render.items.CosmicNeutroniumMetaItemRenderer",
            "gregtech.common.render.items.CosmicNeutroniumRenderer",
            "gregtech.common.render.items.InfinityMetaItemRenderer",
            "gregtech.common.render.items.InfinityRenderer",
            "gregtech.common.render.items.GaiaSpiritRenderer",
            "gregtech.common.render.items.RainbowOverlayMetaItemRenderer",
            "gregtech.common.render.items.RainbowOverlayRenderer",
            "gregtech.common.render.items.GlitchEffectMetaItemRenderer",
            "gregtech.common.render.items.GlitchEffectRenderer",
            "gregtech.common.render.items.UniversiumMetaItemRenderer",
            "gregtech.common.render.items.UniversiumRenderer",
            "gregtech.common.render.items.InfiniteSprayCanRenderer",
            "gregtech.common.render.items.MechanicalArmorRenderer"));

    private VendingNativeModelPolicy() {}

    public static Geometry geometry(String name) {
        if ("gregtech.common.render.items.WireFrameTesseractRenderer".equals(name)) return Geometry.TESSERACT;
        if ("gregtech.common.render.items.TranscendentalMetaItemRenderer".equals(name)
            || "gregtech.common.render.items.TranscendentMetalRenderer".equals(name)) return Geometry.ROTATING_SPRITE;
        if (SPRITES.contains(name)) return Geometry.SPRITE;
        return Geometry.NATIVE_FALLBACK;
    }
}
