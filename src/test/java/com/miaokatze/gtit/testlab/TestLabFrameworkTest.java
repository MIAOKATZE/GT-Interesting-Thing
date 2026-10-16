package com.miaokatze.gtit.testlab;

import java.util.LinkedHashMap;
import java.util.Map;

import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/** Framework invariants that do not require a running Minecraft server. Real world/ticket/UI probes run in the lab. */
public final class TestLabFrameworkTest {

    private TestLabFrameworkTest() {}

    public static void main(String[] args) throws Exception {
        Map<String, Runnable> tests = new LinkedHashMap<>();
        tests.put("disabled by default and cannot run", () -> {
            SimpleAssert.that(!TestLabConfig.enabled && !TestLabConfig.clientUiEnabled, "Default opt-in switches");
            SimpleAssert.that(
                TestLabRuntime.scenarioIds()
                    .isEmpty(),
                "Disabled bootstrap has no registered scenarios");
            try {
                TestLabRuntime.INSTANCE.run("suite", null, 1);
                throw new AssertionError("Disabled lab accepted a run");
            } catch (IllegalStateException expected) {
                SimpleAssert.that(
                    expected.getMessage()
                        .contains("disabled"),
                    "Explicit disabled failure");
            }
        });
        tests.put("manifest confines both canvases and handles integer overflow", () -> {
            SimpleAssert.that(TestLabBounds.contains(-256, 1, 256), "Minimal canvas inclusive boundary");
            SimpleAssert.that(TestLabBounds.contains(1048576 + 256, 255, -256), "Stress canvas inclusive boundary");
            SimpleAssert.that(!TestLabBounds.contains(257, 64, 0), "Between canvases");
            SimpleAssert.that(!TestLabBounds.contains(Integer.MIN_VALUE, 64, 0), "MIN X cannot overflow abs");
            SimpleAssert.that(!TestLabBounds.contains(0, 64, Integer.MIN_VALUE), "MIN Z cannot overflow abs");
            SimpleAssert.that(!TestLabBounds.contains(0, 0, 0), "No bedrock boundary");
            for (int offset : new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE, -64, 192 }) {
                try {
                    TestLabBounds.height(offset);
                    throw new AssertionError("Height offset accepted: " + offset);
                } catch (IllegalArgumentException expected) {}
            }
            SimpleAssert.eq(64, TestLabBounds.height(0), "Valid origin height");
        });
        tests.put("zero assertions and skipped observations cannot pass", () -> {
            TestLabReport report = new TestLabReport("ui", false);
            report.finish();
            SimpleAssert.eq("SKIP", report.status, "Empty report");
            report.add("ui", "screen", "SKIP", "No client");
            report.finish();
            SimpleAssert.eq("SKIP", report.status, "Skipped report");
            SimpleAssert.eq(1.0, report.metrics.get("skipped"), "Skip count");
        });
        tests.put("cleanup and cancellation failures override passing assertions", () -> {
            for (String failure : new String[] { "cleanup", "interrupted", "timeout" }) {
                TestLabReport report = new TestLabReport("suite", false);
                report.add("native", "behavior", "PASS", "Actual assertion");
                report.add("harness", failure, "FAIL", "Lifecycle failure");
                report.finish();
                SimpleAssert.eq("FAIL", report.status, failure + " must fail run");
                SimpleAssert.eq(1.0, report.metrics.get("failed"), "Failure count");
            }
        });
        tests.put("one passing scenario cannot hide selected scenario with no executed check", () -> {
            TestLabReport report = new TestLabReport("suite", false);
            report.environment.put("selectedScenarios", new String[] { "actual", "missing" });
            report.add("actual", "behavior", "PASS", "Actual assertion");
            report.add("missing", "optional", "SKIP", "Dependency unavailable");
            report.finish();
            SimpleAssert.eq("SKIP", report.status, "Incomplete scenario coverage");
            report.add("missing", "behavior", "PASS", "Executed after dependency became available");
            report.finish();
            SimpleAssert.eq("PASS", report.status, "Complete selected coverage");
        });
        tests.put("run identities are unique and report checks retain scenario names", () -> {
            TestLabReport first = new TestLabReport("suite", false);
            TestLabReport second = new TestLabReport("suite", false);
            SimpleAssert.that(!first.runId.equals(second.runId), "Unique run UUID");
            first.add("native-machine", "transaction", "PASS", "one successful native transaction");
            first.finish();
            SimpleAssert.eq("PASS", first.status, "Executed assertion");
            SimpleAssert.eq(
                "native-machine",
                first.cases.get(0)
                    .get("scenario"),
                "Attributable case");
        });
        TestRunner.run(TestLabFrameworkTest.class, tests);
        com.miaokatze.gtit.testlab.scenario.NativeTeamCreationFailureTest.main(args);
        com.miaokatze.gtit.testlab.scenario.NativePocketFixtureTest.main(args);
    }
}
