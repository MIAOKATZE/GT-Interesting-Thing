// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gui.vm.scene;

/** Initial and explicit reset pose only; drawing/resizing must not replace a user's dragged view. */
public final class VendingSceneViewDefaults {

    public static final double YAW = -20, PITCH = 27, ZOOM = 1.2;

    private VendingSceneViewDefaults() {}
}
