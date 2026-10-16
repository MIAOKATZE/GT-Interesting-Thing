package com.miaokatze.gtit.testlab.scenario;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;
import com.miaokatze.gtit.trade.preset.TradePresetManager;
import com.miaokatze.gtit.trade.preset.TradePresetProbe;
import com.miaokatze.gtit.trade.preset.ui.TradePresetUI;
import com.miaokatze.gtit.trade.v2.NekoBqBridge;
import com.miaokatze.gtit.trade.v2.NekoBqCondition;

/** Real file transactions in a disposable root; production trade refresh is never invoked. */
final class TradePresetsNativeScenario implements ProbeScenario {

    @Override
    public String id() {
        return "native-trade-presets";
    }

    @Override
    public ProbeTask start(ProbeContext context) {
        return () -> {
            Path root = IsolatedProbeFiles.directory(context, "preset");
            FakePlayer player = context.player("preset-unprivileged");
            context.check(
                "unprivileged-manager-permission",
                !TradePresetManager.canConfigure(player),
                "Real disconnected synthetic player has no OP4 permission or singleplayer owner identity");
            net.minecraft.inventory.Container beforeUi = player.openContainer;
            try {
                TradePresetUI.open(player);
                context.check(
                    "preset-factory-unprivileged-open-refused",
                    player.openContainer == beforeUi,
                    "Actual production UI open facade rejects the unprivileged synthetic player before building or opening");
            } finally {
                player.closeScreen();
            }
            UUID missing = UUID
                .nameUUIDFromBytes((context.runId() + ":missing-bq").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            context.check(
                "missing-bq-strict-query",
                !NekoBqBridge.questExistsStrict(missing),
                "Real BetterQuesting strict bridge rejects a run-specific absent quest UUID");
            context.check(
                "missing-bq-strict-condition",
                !new NekoBqCondition(missing, true).isSatisfied(player.getUniqueID()),
                "Actual strict trade condition denies an absent quest for the synthetic actor");
            ItemStack original = NativeFixtures.specialStack(context, 7);
            player.inventory.setInventorySlotContents(0, original);
            TradePresetManager.Icon icon = TradePresetManager.Icon.fromStack(original);
            ItemStack ghost = icon.toStack();
            if (ghost != null) ghost.stackSize = 1;
            ItemStack expected = original.copy();
            expected.stackSize = 1;
            context.check(
                "ghost-icon-copy-no-consumption",
                NativeFixtures.sameStack(expected, ghost) && player.inventory.getStackInSlot(0) == original
                    && original.stackSize == 7,
                "Production Icon DTO copies exact item/NBT without consuming the source inventory stack");
            if (ghost != null && ghost.hasTagCompound()) ghost.getTagCompound()
                .setBoolean("changedProbeCopy", true);
            context.check(
                "ghost-icon-nbt-isolation",
                !original.getTagCompound()
                    .hasKey("changedProbeCopy"),
                "Mutating deserialized icon NBT cannot mutate the source stack");
            Map<String, Boolean> checks = TradePresetProbe.run(root, UUID.fromString(context.runId()));
            context.check(
                "isolated-store-checks-present",
                checks != null && !checks.isEmpty(),
                "Core probe must report its real TradePresetStore operations");
            if (checks != null) checks.forEach(
                (name, passed) -> context.check(
                    name,
                    Boolean.TRUE.equals(passed),
                    "Real TradePresetStore in this run's scratch root; production trade data is untouched"));
            context.skip(
                "physical-client-phantom-slots-and-presets-ui",
                "Ghost icon data isolation is tested; client PhantomItemSlot mouse behavior, editor tabs and visual layout require a physical client");
            context.skip(
                "authorized-op4-and-singleplayer-owner-network",
                "Synthetic actor verifies denial; no real player's OP list or server owner identity is changed");
            return true;
        };
    }
}
