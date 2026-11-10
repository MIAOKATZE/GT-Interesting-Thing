package com.miaokatze.gtit.testlab;

import java.lang.reflect.Field;
import java.util.Deque;

/** Exercises cancellation of the real scheduler without constructing a world or mirroring business logic. */
public final class TestLabCancellationTest {

    private TestLabCancellationTest() {}

    public static void main(String[] args) throws Exception {
        TestLabRuntime runtime = TestLabRuntime.INSTANCE;
        Field reportField = field("report");
        Field taskField = field("task");
        Field resettingField = field("resetting");
        @SuppressWarnings("unchecked")
        Deque<ProbeScenario> remaining = (Deque<ProbeScenario>) field("remaining").get(runtime);
        boolean[] closed = { false };
        TestLabReport report = new TestLabReport("suite", false);
        Field pocketRequest = TestLabUiNetwork.class.getDeclaredField("requestedPlayer");
        Field vendingRequest = TestLabVendingFixture.class.getDeclaredField("requestedPlayer");
        pocketRequest.setAccessible(true);
        vendingRequest.setAccessible(true);
        try {
            pocketRequest.set(null, java.util.UUID.randomUUID());
            vendingRequest.set(null, java.util.UUID.randomUUID());
            reportField.set(runtime, report);
            taskField.set(runtime, new ProbeTask() {

                public boolean tick() {
                    throw new AssertionError("Cancelled task was ticked");
                }

                public void close() {
                    closed[0] = true;
                }
            });
            remaining.add(new ProbeScenario() {

                public String id() {
                    return "must-not-start";
                }

                public ProbeTask start(ProbeContext context) {
                    throw new AssertionError("Cancelled queue started");
                }
            });
            String request = java.util.UUID.randomUUID()
                .toString();
            String result = runtime.cancel(request);
            if (!closed[0] || taskField.get(runtime) != null || !remaining.isEmpty())
                throw new AssertionError("Stop must close the exact active task and discard queued tasks");
            if (pocketRequest.get(null) != null || vendingRequest.get(null) != null)
                throw new AssertionError("Cancelled real-client requests must not survive instance reuse");
            if (!result.contains("cleanup pending") || !Boolean.TRUE.equals(
                runtime.snapshot()
                    .get("cleanupPending")))
                throw new AssertionError("Accepted stop must retain pending cleanup");
            if (!request.equals(report.environment.get("cancelRequestId")))
                throw new AssertionError("Stop request correlation lost");
            report.finish();
            if (!"CANCELLED".equals(report.status)) throw new AssertionError("Stop became success");
        } finally {
            reportField.set(runtime, null);
            taskField.set(runtime, null);
            resettingField.setBoolean(runtime, false);
            remaining.clear();
            pocketRequest.set(null, null);
            vendingRequest.set(null, null);
        }
        System.out.println("GTIT active scheduler cancellation passed");
    }

    private static Field field(String name) throws Exception {
        Field field = TestLabRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
