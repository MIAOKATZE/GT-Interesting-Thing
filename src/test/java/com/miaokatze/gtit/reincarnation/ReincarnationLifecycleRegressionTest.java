package com.miaokatze.gtit.reincarnation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.core.ReincarnationGrantProgress;
import com.miaokatze.gtit.reincarnation.core.ReincarnationSaveGuard;
import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;
import com.miaokatze.gtit.reincarnation.core.ReincarnationWorldDeletion;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.SymlinkFixture;
import com.miaokatze.gtit.testutil.TestRunner;

/** Offline regressions for normal claim races, recoverable handoff failures, and save-tree isolation. */
public final class ReincarnationLifecycleRegressionTest {

    private static final String UUID = "11111111-2222-3333-4444-555555555555";

    public static void main(String[] args) throws IOException {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("claimedMailboxCannotBeResurrected", () -> checked(() -> claimedMailboxCannotBeResurrected()));
        cases.put("missingLicenseDoesNotErasePendingGrant", () -> missingLicenseDoesNotErasePendingGrant());
        cases.put("failedHandoffCannotSkipPrefix", () -> failedHandoffCannotSkipPrefix());
        cases.put("failedCheckpointRetriesBeforeNextHandoff", () -> failedCheckpointRetriesBeforeNextHandoff());
        cases.put("suffixFailureResumesAfterSuccessfulItems", () -> suffixFailureResumesAfterSuccessfulItems());
        cases.put("saveDeletionRemovesNestedFiles", () -> checked(() -> saveDeletionDoesNotFollowChildLinks(false)));
        if (SymlinkFixture.available("saveDeletionDoesNotFollowChildLinks", "saveDeletionDoesNotFollowRootLink")) {
            cases.put(
                "saveDeletionDoesNotFollowChildLinks",
                () -> checked(() -> saveDeletionDoesNotFollowChildLinks(true)));
            cases.put("saveDeletionDoesNotFollowRootLink", () -> checked(() -> saveDeletionDoesNotFollowRootLink()));
        }
        TestRunner.run(ReincarnationLifecycleRegressionTest.class, cases);
    }

    private static ReincarnationCycle pending() {
        ReincarnationCycle cycle = new ReincarnationCycle(UUID);
        cycle.deposit(Collections.singletonList(new ReincarnationCycle.ItemRef("minecraft:diamond", 0)));
        cycle.confirmReincarnation(42L);
        return cycle;
    }

    private static void claimedMailboxCannotBeResurrected() throws Exception {
        Path base = Files.createTempDirectory("gtit-claim-race-");
        try {
            ReincarnationStore store = new ReincarnationStore(base.toFile());
            store.save(pending());
            ReincarnationCycle openGui = store.load(UUID);
            ReincarnationCycle handler = store.load(UUID);
            handler.claimGrant();
            store.save(handler);
            ReincarnationCycle disk = store.load(UUID);
            SimpleAssert.that(ReincarnationSaveGuard.needsReloadBeforeSave(openGui, disk), "claimed mailbox reloads");
            if (ReincarnationSaveGuard.needsReloadBeforeSave(openGui, disk)) {
                openGui = disk;
            }
            store.save(openGui);
            SimpleAssert.that(
                !store.load(UUID)
                    .canClaimGrant(),
                "closing old GUI cannot recreate claimed reward");
        } finally {
            ReincarnationWorldDeletion.deleteTree(base);
        }
    }

    private static void missingLicenseDoesNotErasePendingGrant() {
        SimpleAssert.that(
            !ReincarnationSaveGuard.needsReloadBeforeSave(pending(), new ReincarnationCycle(UUID)),
            "missing/corrupt license has no fingerprint evidence of a completed claim");
    }

    private static void failedHandoffCannotSkipPrefix() {
        ReincarnationGrantProgress progress = new ReincarnationGrantProgress(0);
        int[] checkpoint = { 0 };
        int[] deliveries = { 0 };
        expectFailure(
            () -> progress.deliver(0, () -> { throw new IllegalStateException("handoff"); }, n -> checkpoint[0] = n));
        progress.deliver(1, () -> deliveries[0]++, n -> checkpoint[0] = n);
        SimpleAssert.eq(0, progress.getDelivered(), "later scheduled item cannot skip failed item");
        SimpleAssert.eq(0, deliveries[0], "out-of-order item not delivered");
        SimpleAssert.eq(0, checkpoint[0], "durable prefix does not skip failed handoff");
        progress.deliver(0, () -> deliveries[0]++, n -> checkpoint[0] = n);
        progress.deliver(1, () -> deliveries[0]++, n -> checkpoint[0] = n);
        progress.deliver(1, () -> deliveries[0]++, n -> checkpoint[0] = n);
        SimpleAssert.eq(2, deliveries[0], "recovery hands off each item once");
        SimpleAssert.eq(2, checkpoint[0], "contiguous recovery checkpoint");
    }

    private static void failedCheckpointRetriesBeforeNextHandoff() {
        ReincarnationGrantProgress progress = new ReincarnationGrantProgress(0);
        int[] deliveries = { 0 };
        expectFailure(
            () -> progress.deliver(
                0,
                () -> deliveries[0]++,
                n -> { if (n > 0) throw new IllegalStateException("disk failure"); }));
        SimpleAssert
            .eq(1, progress.getDelivered(), "successful handoff retained in session despite checkpoint failure");
        expectFailure(
            () -> progress.deliver(
                1,
                () -> deliveries[0]++,
                n -> { throw new IllegalStateException("disk still unavailable"); }));
        SimpleAssert.eq(1, deliveries[0], "no further handoff before previous checkpoint is durable");
        int[] checkpoint = { 0 };
        progress.deliver(1, () -> {
            SimpleAssert.eq(1, checkpoint[0], "previous item checkpoint retried first");
            deliveries[0]++;
        }, n -> checkpoint[0] = n);
        SimpleAssert.eq(2, deliveries[0], "same-session retry did not duplicate first item");
        SimpleAssert.eq(2, checkpoint[0], "both handoffs checkpointed");
    }

    private static void suffixFailureResumesAfterSuccessfulItems() {
        ReincarnationGrantProgress progress = new ReincarnationGrantProgress(7);
        int[] checkpoint = { 7 };
        int[] deliveries = { 0 };
        progress.deliver(7, () -> deliveries[0]++, n -> checkpoint[0] = n);
        expectFailure(
            () -> progress
                .deliver(8, () -> { throw new IllegalStateException("suffix failure"); }, n -> checkpoint[0] = n));
        ReincarnationGrantProgress resumed = new ReincarnationGrantProgress(checkpoint[0]);
        SimpleAssert.eq(8, resumed.getDelivered(), "unanimated suffix success is checkpointed before failure");
        resumed.deliver(8, () -> deliveries[0]++, n -> checkpoint[0] = n);
        SimpleAssert.eq(2, deliveries[0], "resume does not repeat successful suffix item");
    }

    private static void saveDeletionDoesNotFollowChildLinks(boolean links) throws Exception {
        Path base = Files.createTempDirectory("gtit-save-delete-");
        try {
            Path outside = Files.createDirectory(base.resolve("other-save"));
            Path sentinel = outside.resolve("level.dat");
            Files.write(sentinel, "keep".getBytes(StandardCharsets.UTF_8));
            Path save = Files.createDirectories(base.resolve("selected-save/region"));
            Files.write(save.resolve("r.0.0.mca"), new byte[] { 1 });
            Path root = save.getParent();
            if (links) {
                Files.createSymbolicLink(root.resolve("external"), outside);
                Files.createSymbolicLink(root.resolve("cycle"), root);
                Files.createSymbolicLink(root.resolve("dangling"), base.resolve("missing"));
            }
            ReincarnationWorldDeletion.deleteTree(root);
            SimpleAssert.that(!Files.exists(root), "selected save deleted including links and nested files");
            SimpleAssert
                .eq("keep", new String(Files.readAllBytes(sentinel), StandardCharsets.UTF_8), "linked save unchanged");
        } finally {
            ReincarnationWorldDeletion.deleteTree(base);
        }
    }

    private static void saveDeletionDoesNotFollowRootLink() throws Exception {
        Path base = Files.createTempDirectory("gtit-root-link-");
        try {
            Path outside = Files.createDirectory(base.resolve("real-save"));
            Path sentinel = Files.write(outside.resolve("level.dat"), new byte[] { 42 });
            Path link = Files.createSymbolicLink(base.resolve("selected-link"), outside);
            ReincarnationWorldDeletion.deleteTree(link);
            SimpleAssert.that(Files.exists(sentinel), "linked root target is not traversed");
        } finally {
            ReincarnationWorldDeletion.deleteTree(base);
        }
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected injected failure");
    }

    private static void checked(Checked action) {
        try {
            action.run();
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private interface Checked {

        void run() throws Exception;
    }
}
