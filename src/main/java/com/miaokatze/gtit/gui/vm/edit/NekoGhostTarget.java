// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gui.vm.edit;

import net.minecraft.item.ItemStack;

/** Explicit allow-list for catalog copies. Implementations must only edit a draft, never inventory/cursor items. */
public interface NekoGhostTarget {

    boolean acceptNeiGhost(ItemStack stack, int button);
}
