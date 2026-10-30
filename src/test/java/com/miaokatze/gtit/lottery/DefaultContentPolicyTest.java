package com.miaokatze.gtit.lottery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.Item;

import com.miaokatze.gtit.config.DefaultContentConfig;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.testutil.TestRunner;

/** File-backed regressions run only in Gradle's isolated build/content-policy-test directory. */
public final class DefaultContentPolicyTest {

    private static final Path LOTTERY = Paths.get("config/gtit/lottery/lottery.json");

    public static void main(String[] args) throws Exception {
        if (!com.miaokatze.gtit.testutil.MinecraftTestBootstrap.enter(DefaultContentPolicyTest.class, args)) return;
        Item previousNeko = NekoCurrencyRegistrar.nekoCoinItem;
        Item previousShimmering = NekoCurrencyRegistrar.shimmeringNekoCoinItem;
        try {
            // Headless bootstrap initializes vanilla registries, not GTIT's postInit coin bindings.
            NekoCurrencyRegistrar.nekoCoinItem = Items.gold_nugget;
            NekoCurrencyRegistrar.shimmeringNekoCoinItem = Items.emerald;
            runCases();
        } finally {
            NekoCurrencyRegistrar.nekoCoinItem = previousNeko;
            NekoCurrencyRegistrar.shimmeringNekoCoinItem = previousShimmering;
        }
    }

    private static void runCases() {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("managementPermissionDefaultsAndBounds", () -> {
            Path config = Paths.get("config/gtit/default_content.cfg");
            write(config, "defaults {\n}\n");
            DefaultContentConfig.init(
                Paths.get("config")
                    .toFile());
            check(DefaultContentConfig.managementOpLevel() == 2, "existing configs default to OP2");
            for (int configured : new int[] { 1, 2, 3, 4, 5 }) {
                write(config, "permissions {\nI:managementOpLevel=" + configured + "\n}\n");
                DefaultContentConfig.init(
                    Paths.get("config")
                        .toFile());
                check(
                    DefaultContentConfig.managementOpLevel() == Math.max(2, Math.min(4, configured)),
                    "management level must stay within OP2-4");
            }
            flags(true, true, true);
        });
        cases.put("threeIndependentSwitches", () -> {
            for (int mask = 0; mask < 8; mask++) {
                flags((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0);
                check(DefaultContentConfig.loadDefaultTradeGroups() == ((mask & 1) != 0), "trade switch");
                check(DefaultContentConfig.loadDefaultLottery() == ((mask & 2) != 0), "lottery switch");
                check(DefaultContentConfig.loadDefaultGifts() == ((mask & 4) != 0), "gift switch");
            }
        });
        cases.put("freshDisabledDoesNotSeedPools", () -> {
            flags(true, false, true);
            check(LotteryConfig.getDefaultConfig().pools.isEmpty(), "disabled defaults must be empty");
        });
        cases.put("explicitEmptyNeverRegenerates", () -> {
            flags(true, true, true);
            write(LOTTERY, "{\"pools\": []}\n");
            check(LotteryConfig.load().pools.isEmpty(), "intentional empty configuration stays empty");
            check(read(LOTTERY).equals("{\"pools\": []}\n"), "empty configuration is not rewritten");
        });
        cases.put("disabledDefaultsHiddenWithoutRewriting", () -> {
            flags(true, true, true);
            LotteryConfig.save(LotteryConfig.getDefaultConfig());
            String original = read(LOTTERY);
            flags(true, false, true);
            check(LotteryConfig.load().pools.isEmpty(), "shipped pools hidden");
            check(read(LOTTERY).equals(original), "disabled content remains on disk");
        });
        cases.put("legacyDefaultsRecognizedByWholeDefinition", () -> {
            flags(true, true, true);
            LotteryConfig.LotteryConfigData data = LotteryConfig.getDefaultConfig();
            for (LotteryPool pool : data.pools) pool.bundledDefaultSignature = "";
            flags(true, false, true);
            LotteryConfig.suppressBundledDefaults(data);
            check(data.pools.isEmpty(), "legacy shipped contents hidden");
        });
        cases.put("editedDefaultAndCustomPoolSurvive", () -> {
            flags(true, true, true);
            LotteryConfig.LotteryConfigData data = LotteryConfig.getDefaultConfig();
            data.pools.get(0)
                .getEntries()
                .get(0)
                .setWeight(217);
            LotteryPool custom = new LotteryPool("custom", "custom", "", 0, PityConfig.createDefault());
            custom.getEntries()
                .add(LotteryEntry.createItemPrize("x", "minecraft:bread", 0, 1, 1, 1, LotteryRarity.COMMON));
            data.pools.add(custom);
            flags(true, false, true);
            LotteryConfig.suppressBundledDefaults(data);
            check(data.pools.size() == 2, "custom and edited definition retained");
            check(
                data.pools.get(0)
                    .getEntries()
                    .get(0)
                    .getWeight() == 217,
                "edit remains exact");
            check(
                "custom".equals(
                    data.pools.get(1)
                        .getId()),
                "custom pool remains");
        });
        cases.put("legacyIdAloneNeverMarksCustomAsDefault", () -> {
            flags(true, true, true);
            LotteryConfig.LotteryConfigData data = LotteryConfig.getDefaultConfig();
            for (LotteryPool pool : data.pools) {
                pool.bundledDefaultSignature = "";
                pool.setName("customized " + pool.getId());
            }
            flags(true, false, true);
            LotteryConfig.suppressBundledDefaults(data);
            check(data.pools.size() == 2, "same IDs with custom names are retained");
        });
        cases.put("malformedConfigPreserved", () -> {
            flags(true, true, true);
            write(LOTTERY, "{broken-user-file\n");
            check(LotteryConfig.load().pools.isEmpty(), "malformed config cannot grant fallback prizes");
            check(read(LOTTERY).equals("{broken-user-file\n"), "malformed file is preserved for repair");
        });
        cases.put("reenablingRestoresUnmodifiedSavedDefaults", () -> {
            flags(true, true, true);
            LotteryConfig.save(LotteryConfig.getDefaultConfig());
            flags(true, false, true);
            check(LotteryConfig.load().pools.isEmpty(), "disabled on load");
            flags(true, true, true);
            check(LotteryConfig.load().pools.size() == 2, "reenabled from unchanged saved definitions");
        });
        TestRunner.run(DefaultContentPolicyTest.class, cases);
    }

    private static void flags(boolean trades, boolean lottery, boolean gifts) {
        write(
            Paths.get("config/gtit/default_content.cfg"),
            "defaults {\nB:loadDefaultTradeGroups=" + trades
                + "\nB:loadDefaultLottery="
                + lottery
                + "\nB:loadDefaultGifts="
                + gifts
                + "\n}\n");
        DefaultContentConfig.init(
            Paths.get("config")
                .toFile());
    }

    private static void write(Path path, String value) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
