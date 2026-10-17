package com.miaokatze.gtit.gift;

import java.util.ArrayList;
import java.util.List;

import com.miaokatze.gtit.config.GiftConfig;
import com.miaokatze.gtit.config.GiftConfig.ItemEntry;

/** Author-maintained defaults, isolated from custom definitions by the reserved built-in IDs. */
final class GiftDefaults {

    static final int VERSION = 1;
    static final int FORCED_VERSION = 0;
    static final String[] IDS = { "starter", "steam", "lv", "mv", "hv", "ev", "iv", "luv" };
    // Verified against the GTNH 2.9.0-RC-2 DefaultQuests tier-entry quests, not legacy numeric guesses.
    private static final int[] QUESTS = { 0, 44, 88, 1148, 160, 176, 213, 1493 };

    private GiftDefaults() {}

    static boolean isBuiltIn(String id) {
        for (String value : IDS) if (value.equals(id)) return true;
        return false;
    }

    static List<GiftDefinition> create() {
        return create(false);
    }

    static List<GiftDefinition> createMigratingLegacy() {
        return create(true);
    }

    private static List<GiftDefinition> create(boolean migrateLegacy) {
        List<GiftDefinition> result = new ArrayList<>();
        for (int i = 0; i < IDS.length; i++) {
            GiftDefinition gift = new GiftDefinition();
            gift.id = IDS[i];
            gift.iconKey = IDS[i];
            gift.shellKey = IDS[i];
            gift.icon = null;
            gift.name = new String[] { "新手礼包", "蒸汽时代礼包", "LV时代礼包", "MV时代礼包", "HV时代礼包", "EV时代礼包", "IV时代礼包",
                "LuV时代礼包" }[i];
            gift.builtIn = true;
            gift.trigger = i == 0 ? GiftDefinition.Trigger.FIRST_LOGIN : GiftDefinition.Trigger.BQ_COMPLETE;
            gift.questId = i == 0 ? "" : "0:" + QUESTS[i];
            if (i == 0) {
                gift.rewards.addAll(
                    migrateLegacy ? GiftConfig.getGuaranteedItems() : GiftConfig.createDefaultGuaranteedItems());
                gift.randomRewards
                    .addAll(migrateLegacy ? GiftConfig.getRandomItems() : GiftConfig.createDefaultRandomItems());
                gift.randomCount = Math.min(migrateLegacy ? GiftConfig.getRandomCount() : 2, gift.randomRewards.size());
            } else {
                gift.rewards.add(new ItemEntry("minecraft:bread", 16, 0));
                gift.rewards.add(new ItemEntry("minecraft:torch", 64, 0));
                gift.rewards.add(new ItemEntry("minecraft:ender_pearl", Math.min(16, i * 2), 0));
                gift.rewards.add(new ItemEntry("gtit:neko_coin", 1, 0));
            }
            result.add(gift);
        }
        return result;
    }
}
