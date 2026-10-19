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
