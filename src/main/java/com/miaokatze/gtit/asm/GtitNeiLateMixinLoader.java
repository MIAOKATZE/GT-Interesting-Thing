package com.miaokatze.gtit.asm;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

/** Client-only optional NEI layout hook. No NEI or Minecraft client class is resolved on a dedicated server. */
@LateMixin
public final class GtitNeiLateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.gtit.nei.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        if (FMLLaunchHandler.side()
            .isClient() && loadedMods != null
            && loadedMods.contains("NotEnoughItems")) {
            return Collections.singletonList("nei.MixinLayoutManagerEmbeddedCatalog");
        }
        return Collections.emptyList();
    }
}
