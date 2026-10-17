package com.miaokatze.gtit.gift;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.miaokatze.gtit.config.GiftConfig.ItemEntry;
import com.miaokatze.gtit.trade.v2.NekoBqQuestIdParser;

/** Server-owned, versioned gift definition. IDs remain stable across edits and team changes. */
public final class GiftDefinition {

    public enum Trigger {
        FIRST_LOGIN,
        BQ_UNLOCK,
        BQ_COMPLETE
    }

    public enum UpdatePolicy {
        FORCED,
        RECOMMENDED
    }

    public String id = "";
    public long revision;
    public String name = "";
    public ItemEntry icon = new ItemEntry("minecraft:chest", 1, 0);
    public String iconKey = "gift";
    /** Null marks a definition written before physical gift-shell bindings existed. */
    public String shellKey;
    public Trigger trigger = Trigger.BQ_UNLOCK;
    public String questId = "";
    public List<ItemEntry> rewards = new ArrayList<>();
    public List<ItemEntry> randomRewards = new ArrayList<>();
    public int randomCount;
    public String wirelessEU = "0";
    public UpdatePolicy updatePolicy = UpdatePolicy.RECOMMENDED;
    public boolean enabled = true;
    public boolean builtIn;

    public GiftDefinition copy() {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(this), GiftDefinition.class);
    }

    public void validate() {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_.-]{0,63}")) fail("Invalid gift ID");
        if (name == null || name.trim()
            .isEmpty() || name.length() > 128) fail("Invalid gift name");
        if (trigger == null || updatePolicy == null) fail("Missing gift mode");
        if (questId == null || questId.length() > 128) fail("Invalid quest ID");
        if (enabled && trigger != Trigger.FIRST_LOGIN && NekoBqQuestIdParser.parse(questId) == null) {
            fail("A valid BetterQuesting ID is required");
        }
        if (rewards == null || randomRewards == null || rewards.size() > 128 || randomRewards.size() > 128) {
            fail("Too many gift rewards");
        }
        if (randomCount < 0 || randomCount > randomRewards.size()) fail("Invalid random reward count");
        if (wirelessEU == null || !wirelessEU.matches("[0-9]{1,128}")) fail("Invalid wireless EU");
        if (new BigInteger(wirelessEU).signum() < 0) fail("Negative wireless EU");
        if (iconKey == null || iconKey.length() > 64) fail("Invalid gift icon");
        if (shellKey != null && !GiftIconCatalog.contains(shellKey)) fail("Invalid gift shell");
        if (icon != null) validateItem(icon);
        for (ItemEntry entry : rewards) validateItem(entry);
        for (ItemEntry entry : randomRewards) validateItem(entry);
    }

    private static void validateItem(ItemEntry entry) {
        if (entry == null || entry.itemId == null
            || entry.itemId.length() > 256
            || !entry.itemId.matches("[^: \\s]+:[^: \\s]+")
            || entry.amount < 1
            || entry.amount > 4096
            || entry.meta < 0
            || entry.meta > 32767) fail("Invalid gift item");
        String nbt = entry.getNbtBase64();
        if (nbt != null) GiftNbt.decode(nbt);
    }

    private static void fail(String message) {
        throw new IllegalArgumentException(message);
    }
}
