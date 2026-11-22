// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.asm;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/** GTNH 2.9.0 backup completion hook. Unverified backup versions keep synchronous saves. */
@LateMixin
public final class GtitBackupLateMixinLoader implements ILateMixinLoader {

    private static final String MOD_ID = "serverutilities";
    private static boolean backupHookSelected;

    @Override
    public String getMixinConfig() {
        return "mixins.gtit.backup.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        final ModContainer backupMod = Loader.instance()
            .getIndexedModList()
            .get(MOD_ID);
        backupHookSelected = loadedMods.contains(MOD_ID) && backupMod != null
            && "2.4.22".equals(backupMod.getVersion());
        return backupHookSelected ? Collections.singletonList("backup.MixinBackupTaskWaitForInfinityCells")
            : Collections.emptyList();
    }

    public static boolean canSaveInBackground() {
        return !Loader.isModLoaded(MOD_ID) || backupHookSelected;
    }
}
