package com.miaokatze.gtit.testlab.scenario;

import com.miaokatze.gtit.testlab.TestLabRuntime;

/** Opt-in server probes. No scenario mutates production configuration or uses an online player's identity. */
public final class NativeProbeScenarios {

    private NativeProbeScenarios() {}

    public static void register() {
        TestLabRuntime.register(new PocketNativeScenario());
        TestLabRuntime.register(new MachineNativeScenario());
        TestLabRuntime.register(new TeamWalletNativeScenario());
        TestLabRuntime.register(new BusinessLifecycleScenario());
        TestLabRuntime.register(new NativeStressScenario());
        TestLabRuntime.register(new NativePersistenceScenario(true));
        TestLabRuntime.register(new NativePersistenceScenario(false));
    }
}
