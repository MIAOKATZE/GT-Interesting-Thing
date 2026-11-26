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

/** Owns the reviewed ServerUtilities backup barrier and the resulting background-save capability. */
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
        // 2.4.22 was reviewed for ordering: saves are suspended, queued writes drain, then copying starts.
        // Matching drainQueuedWrites() alone does not prove that barrier; other versions stay synchronous
        // until their completion/copy ordering is reviewed, even when their method descriptors still match.
        backupHookSelected = loadedMods.contains(MOD_ID) && backupMod != null
            && "2.4.22".equals(backupMod.getVersion());
        return backupHookSelected ? Collections.singletonList("backup.MixinBackupTaskWaitForInfinityCells")
            : Collections.emptyList();
    }

    public static boolean canSaveInBackground() {
        // Without ServerUtilities there is no external backup copy to coordinate. When it is installed,
        // only the reviewed hook permits async writes; callers consume this capability, not version strings.
        return !Loader.isModLoaded(MOD_ID) || backupHookSelected;
    }
}
