package com.miaokatze.gtit.asm;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public final class GtitHologramLateMixinLoader implements ILateMixinLoader {

    public String getMixinConfig() {
        return "mixins.gtit.hologram.json";
    }

    public List<String> getMixins(Set<String> mods) {
        return Arrays.asList(
            "hologram.MixinHologramBuildPiece",
            "hologram.MixinHologramHints",
            "hologram.MixinHologramChannels");
    }
}
