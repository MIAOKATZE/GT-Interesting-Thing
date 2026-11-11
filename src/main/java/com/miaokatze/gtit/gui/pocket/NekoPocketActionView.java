// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gui.pocket;

import net.minecraft.item.ItemStack;

/** Open-container effects of a validated action; authoritative storage belongs to the runtime. */
interface NekoPocketActionView {

    boolean isCurrent();

    void report(String key, int moved, int refused);

    default void report(String key, int moved) {
        report(key, moved, 0);
    }

    void refreshGhosts();

    void magnetChanged();

    void giveAway(ItemStack stack);

    ItemStack cursor();

    void setCursor(ItemStack stack);
}
