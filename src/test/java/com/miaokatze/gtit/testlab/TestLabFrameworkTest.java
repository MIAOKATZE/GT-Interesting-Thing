// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

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
        tests.put("canonical adapter propagates rejection instead of acknowledging a rejected run", () -> {
            try {
                new TestLabModule().execute(
                    null,
                    new String[] { "run", "missing" },
                    java.util.UUID.randomUUID()
                        .toString());
                throw new AssertionError("Disabled run returned as accepted");
            } catch (IllegalStateException expected) {
                SimpleAssert.that(
                    expected.getMessage()
                        .contains("disabled"),
                    "Authoritative rejection reaches driver");
            }
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
        tests.put("stop is separate from reset and cancelled reports cannot pass", () -> {
            SimpleAssert.eq("No active run", TestLabRuntime.INSTANCE.cancel(null), "Idle stop is explicit");
            TestLabReport report = new TestLabReport("suite", false);
            report.add("native", "behavior", "PASS", "Actual assertion");
            report.add("harness", "cancelled", "CANCELLED", "Explicit stop");
            report.finish();
            SimpleAssert.eq("CANCELLED", report.status, "Stop cannot become PASS");
            report.add("harness", "cleanup", "FAIL", "Cleanup failed");
            report.finish();
            SimpleAssert.eq("FAIL", report.status, "Cleanup failure remains visible");
        });
        tests.put("all 18 native cases retain lanes defaults and real actor requirements", () -> {
            TestLabRuntime.registerScenarios();
            java.util.List<com.miaokatze.miaokatzecore.api.testlab.LabCase> catalog = new TestLabModule().cases();
            SimpleAssert.eq(18, catalog.size(), "Complete catalog");
            SimpleAssert.eq(
                8L,
                catalog.stream()
                    .filter(c -> c.defaultSuite())
                    .count(),
                "Suite retains eight cases");
            SimpleAssert.eq(
                4L,
                catalog.stream()
                    .filter(c -> c.requiresRealActor())
                    .count(),
                "Two UI and two host phases");
            for (String id : new String[] { "native-gift-host-stage", "native-gift-host-resume", "client-ui-pocket",
                "client-ui-vending" }) {
                SimpleAssert.that(
                    catalog.stream()
                        .anyMatch(c -> id.equals(c.id()) && c.requiresRealActor()),
                    id);
            }
            SimpleAssert.that(!new TestLabModule().ownsWorld(null), "Never claim unrelated/null world");
        });
        TestRunner.run(TestLabFrameworkTest.class, tests);
        java.nio.file.Path catalogPath = java.nio.file.Paths.get("build", "testlab", "cases.json");
        java.nio.file.Files.createDirectories(catalogPath.getParent());
        Map<String, Object> declaredCatalog = new LinkedHashMap<>();
        declaredCatalog.put("module", "gtit");
        declaredCatalog.put("evidence", "declared");
        declaredCatalog.put("cases", new TestLabModule().cases());
        java.nio.file.Files.write(
            catalogPath,
            new com.google.gson.GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(declaredCatalog)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        TestLabCancellationTest.main(args);
        TestLabAllAliasTest.main(args);
        TestLabUiRenderGateTest.main(args);
        com.miaokatze.gtit.main.TestLabBootstrapTest.main(args);
        com.miaokatze.gtit.testlab.scenario.NativeTeamCreationFailureTest.main(args);
        com.miaokatze.gtit.testlab.scenario.NativePocketFixtureTest.main(args);
    }
}
