// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.testlab;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.ICommandSender;

import cpw.mods.fml.common.Loader;

/** Sends the canonical host's actual run-all action through the real adapter and scheduler. */
public final class TestLabAllAliasTest {

    private TestLabAllAliasTest() {}

    public static void main(String[] args) throws Exception {
        TestLabRuntime runtime = TestLabRuntime.INSTANCE;
        Field problem = accessible(TestLabDimension.class, "problem");
        Field reportField = accessible(TestLabRuntime.class, "report");
        Field resetting = accessible(TestLabRuntime.class, "resetting");
        Field loaderInstance = accessible(Loader.class, "instance");
        Field unsafeField = accessible(sun.misc.Unsafe.class, "theUnsafe");
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Object previousLoader = loaderInstance.get(null);
        Object previousProblem = problem.get(null);
        boolean previousEnabled = TestLabConfig.enabled;
        @SuppressWarnings("unchecked")
        Deque<ProbeScenario> queue = (Deque<ProbeScenario>) accessible(TestLabRuntime.class, "remaining").get(runtime);
        if (reportField.get(runtime) != null || !queue.isEmpty() || resetting.getBoolean(runtime))
            throw new AssertionError("Contract requires an idle scheduler");
        try {
            // Only the environment inventory is empty; command parsing and task selection remain real.
            loaderInstance.set(null, unsafe.allocateInstance(Loader.class));
            TestLabConfig.enabled = true;
            problem.set(null, null);
            if (TestLabRuntime.scenarioIds()
                .isEmpty()) TestLabRuntime.registerScenarios();
            ICommandSender sender = (ICommandSender) java.lang.reflect.Proxy.newProxyInstance(
                TestLabAllAliasTest.class.getClassLoader(),
                new Class<?>[] { ICommandSender.class },
                (proxy, method, arguments) -> null);
            String request = UUID.randomUUID()
                .toString();
            new TestLabModule().execute(sender, new String[] { "run", "all" }, request);
            TestLabReport report = (TestLabReport) reportField.get(runtime);
            if (report == null || !"suite".equals(report.mode)) throw new AssertionError("all did not start suite");
            if (!request.equals(report.environment.get("requestId"))) throw new AssertionError("Request ID lost");
            List<String> expected = new ArrayList<>();
            for (ProbeScenario scenario : TestLabRuntime.scenarios())
                if (scenario.defaultEnabled() && !scenario.stress()) expected.add(scenario.id());
            List<String> actual = new ArrayList<>();
            for (ProbeScenario scenario : queue) actual.add(scenario.id());
            if (actual.size() != 8 || !actual.equals(expected)
                || !actual.equals(Arrays.asList((String[]) report.environment.get("selectedScenarios"))))
                throw new AssertionError("all must queue exactly the declared eight default cases: " + actual);
            if (!resetting.getBoolean(runtime)) throw new AssertionError("Initial owned cleanup must precede tasks");
        } finally {
            queue.clear();
            reportField.set(runtime, null);
            resetting.setBoolean(runtime, false);
            loaderInstance.set(null, previousLoader);
            problem.set(null, previousProblem);
            TestLabConfig.enabled = previousEnabled;
        }
        System.out.println("GTIT canonical all starts the real default suite with request correlation");
    }

    private static Field accessible(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
