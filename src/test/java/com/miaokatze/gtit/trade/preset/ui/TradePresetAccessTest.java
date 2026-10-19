package com.miaokatze.gtit.trade.preset.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;
import com.miaokatze.gtit.trade.preset.TradePresetManager;

import io.netty.buffer.Unpooled;

/** Regression coverage for the per-save reminder and minimal key request boundary. */
public final class TradePresetAccessTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(TradePresetAccessTest.class, args)) return;
        Map<String, Runnable> cases = new LinkedHashMap<>();
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
        cases.put("unseenSaveDefaultsToReminderEligible", () -> {
            TradePresetAccessHandler.NoticeData data = new TradePresetAccessHandler.NoticeData();
            data.readFromNBT(new NBTTagCompound());
            SimpleAssert.that(data.shouldNotify(alice), "A new save must retain its first reminder");
        });
        cases.put("shownReminderSurvivesSaveReload", () -> {
            TradePresetAccessHandler.NoticeData before = new TradePresetAccessHandler.NoticeData();
            before.markShown(alice);
            NBTTagCompound saved = new NBTTagCompound();
            before.writeToNBT(saved);
            TradePresetAccessHandler.NoticeData after = new TradePresetAccessHandler.NoticeData();
            after.readFromNBT(saved);
            SimpleAssert.that(!after.shouldNotify(alice), "Reloading the save must not re-enable the reminder");
            SimpleAssert.that(after.shouldNotify(bob), "One OP must not consume a second OP's reminder");
        });
        cases.put("aDifferentSaveHasIndependentRecipients", () -> {
            TradePresetAccessHandler.NoticeData first = new TradePresetAccessHandler.NoticeData();
            first.markShown(alice);
            TradePresetAccessHandler.NoticeData second = new TradePresetAccessHandler.NoticeData();
            SimpleAssert.that(second.shouldNotify(alice), "Reminder recipients belong to the current save");
        });
        cases.put("legacyGlobalFlagSeedsOnlineManagersOnlyOnce", () -> {
            NBTTagCompound legacy = new NBTTagCompound();
            legacy.setBoolean("shown", true);
            TradePresetAccessHandler.NoticeData data = new TradePresetAccessHandler.NoticeData();
            data.readFromNBT(legacy);
            data.migrateLegacy(Collections.singleton(alice));
            SimpleAssert.that(!data.shouldNotify(alice), "Do not burst reminders to current OPs on upgrade");
            SimpleAssert.that(data.shouldNotify(bob), "Future OPs still receive their own first reminder");
            data.migrateLegacy(Collections.singleton(bob));
            SimpleAssert.that(data.shouldNotify(bob), "Legacy suppression must apply only once");
        });
        cases.put("unprocessedLegacyMigrationSurvivesSaving", () -> {
            NBTTagCompound legacy = new NBTTagCompound();
            legacy.setBoolean("shown", true);
            TradePresetAccessHandler.NoticeData before = new TradePresetAccessHandler.NoticeData();
            before.readFromNBT(legacy);
            NBTTagCompound saved = new NBTTagCompound();
            before.writeToNBT(saved);
            TradePresetAccessHandler.NoticeData after = new TradePresetAccessHandler.NoticeData();
            after.readFromNBT(saved);
            after.migrateLegacy(Collections.singleton(alice));
            SimpleAssert.that(!after.shouldNotify(alice), "Pending legacy migration must survive an early save");
        });
        cases.put("openRequestCannotContainClientCatalogOrPermissions", () -> {
            new TradePresetAccessHandler.Open().fromBytes(Unpooled.EMPTY_BUFFER);
            try {
                new TradePresetAccessHandler.Open().fromBytes(Unpooled.wrappedBuffer(new byte[] { 1 }));
                throw new AssertionError("Client payload must be rejected");
            } catch (IllegalArgumentException expected) {
                // Only the identity of the authenticated connection is used by the server.
            }
        });
        cases.put("reopenedCatalogRetainsFeedbackAndPendingSelectionOnWire", () -> {
            TradePresetManager.Snapshot snapshot = new TradePresetManager.Snapshot();
            snapshot.revision = 73;
            snapshot.feedback = "目录已刷新；已应用内容保持不变。";
            snapshot.appliedIds.add("gtit-ae-basic");
            snapshot.pendingSelection = java.util.Arrays.asList("gtit-base-decoration");
            snapshot.appliedTradeCount = 7;
            snapshot.editorOpen = true;
            PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
            try {
                TradePresetPayloadCodec.writePayload(snapshot, "my_preset", buffer);
                TradePresetPayloadCodec.Payload read = TradePresetPayloadCodec.readPayload(buffer);
                SimpleAssert.that(buffer.readableBytes() == 0, "Factory payload framing must be symmetric");
                SimpleAssert.that(
                    read.snapshot.revision == 73 && read.snapshot.editorOpen && "my_preset".equals(read.editId),
                    "Reopen keeps revision and editor source");
                SimpleAssert
                    .that(snapshot.feedback.equals(read.snapshot.feedback), "Completion feedback survives reopen");
                SimpleAssert.that(
                    snapshot.pendingSelection.equals(read.snapshot.pendingSelection)
                        && snapshot.appliedIds.equals(read.snapshot.appliedIds)
                        && read.snapshot.appliedTradeCount == 7,
                    "Pending checks remain separate from applied content");
            } finally {
                buffer.release();
            }
        });
        TestRunner.run(TradePresetAccessTest.class, cases);
    }
}
