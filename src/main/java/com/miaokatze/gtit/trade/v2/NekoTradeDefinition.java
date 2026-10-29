package com.miaokatze.gtit.trade.v2;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** A definition fence, independent of wallet/history state and either side's local registry counter. */
public final class NekoTradeDefinition {

    private NekoTradeDefinition() {}

    public static String fingerprint(NekoTradeGroup group, NekoTrade trade) {
        if (group == null || trade == null) return "";
        NBTTagCompound definition = new NBTTagCompound();
        definition.setTag("trade", trade.writeToNBT());
        definition.setInteger("cooldown", group.getCooldown());
        definition.setInteger("maxTrades", group.getMaxTrades());
        if (group.getBqQuestId() != null) definition.setString("quest", group.getBqQuestId());
        List<String> conditions = new ArrayList<>();
        for (NekoTradeCondition condition : group.getRequirements()) {
            NBTTagCompound data = new NBTTagCompound();
            data.setString("type", condition.getConditionName());
            data.setTag("data", condition.writeToNBT());
            conditions.add(digest(data));
        }
        Collections.sort(conditions);
        for (int i = 0; i < conditions.size(); i++) definition.setString("condition" + i, conditions.get(i));
        return digest(definition);
    }

    public static boolean matches(UUID groupId, int index, String fingerprint) {
        if (fingerprint == null || fingerprint.length() != 64) return false;
        NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(groupId);
        return group != null && index >= 0
            && index < group.getTrades()
                .size()
            && fingerprint.equals(
                fingerprint(
                    group,
                    group.getTrades()
                        .get(index)));
    }

    /** Editing can replace/delete the whole persisted group, including its page/order metadata. */
    public static String editFingerprint(NekoTradeGroup group) {
        if (group == null) return "";
        NBTTagCompound definition = new NBTTagCompound();
        definition.setString("id", group.getId().toString());
        definition.setInteger("tabId", group.getTabId());
        definition.setInteger("orderId", group.getOrderId());
        definition.setBoolean("defaultEntry", group.isDefaultEntry());
        int index = 0;
        for (NekoTrade trade : group.getTrades()) {
            definition.setString("trade" + index++, fingerprint(group, trade));
        }
        definition.setInteger("tradeCount", index);
        return digest(definition);
    }

    public static boolean matchesEdit(UUID groupId, int index, String expected) {
        if (expected == null || expected.length() != 64) return false;
        NekoTradeGroup group = NekoTradeDatabase.INSTANCE.getTradeGroup(groupId);
        return group != null && index >= 0 && index < group.getTrades().size()
            && expected.equals(editFingerprint(group));
    }

    private static String digest(NBTBase tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            canonical(tag, new DataOutputStream(bytes));
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(bytes.toByteArray());
            char[] hex = new char[hash.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int i = 0; i < hash.length; i++) {
                hex[2 * i] = alphabet[(hash[i] & 255) >>> 4];
                hex[2 * i + 1] = alphabet[hash[i] & 15];
            }
            return new String(hex);
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot fingerprint trade definition", failure);
        }
    }

    private static void canonical(NBTBase tag, DataOutputStream out) throws IOException {
        out.writeByte(tag.getId());
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            List<String> keys = new ArrayList<>(compound.func_150296_c());
            Collections.sort(keys);
            out.writeInt(keys.size());
            for (String key : keys) {
                out.writeUTF(key);
                canonical(compound.getTag(key), out);
            }
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag.copy();
            out.writeInt(list.tagCount());
            while (list.tagCount() > 0) canonical(list.removeTag(0), out);
        } else {
            // Vanilla writes exact primitive/array payloads; no hash-map ordering remains here.
            NBTTagCompound scalar = new NBTTagCompound();
            scalar.setTag("value", tag);
            CompressedStreamTools.write(scalar, out);
        }
    }
}
