// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.testlab;

/** Extension point for native business probes. Register during enabled test-lab initialization. */
public interface ProbeScenario {

    String id();

    default boolean stress() {
        return false;
    }

    default boolean defaultEnabled() {
        return true;
    }

    ProbeTask start(ProbeContext context) throws Exception;
}
