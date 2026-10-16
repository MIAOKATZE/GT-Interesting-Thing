package com.miaokatze.gtit.gift;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import gregtech.common.misc.GlobalEnergyWorldSavedData;
import gregtech.common.misc.GlobalVariableStorage;

/**
 * Credits and idempotency receipts share GT5U's single serialized energy map. A separate receipt
 * file would permit duplication or loss when the server stops between the two saves.
 */
public final class GiftWirelessEU {

    private GiftWirelessEU() {}

    public static UUID receiptKey(String token) {
        UUID.fromString(token);
        return UUID.nameUUIDFromBytes(("gtit:gift:wireless-eu:" + token).getBytes(StandardCharsets.UTF_8));
    }

    static void credit(String token, UUID target, BigInteger amount) {
        if (amount.signum() < 0 || amount.toString()
            .length() > 128) throw new IllegalArgumentException("Invalid wireless EU");
        if (amount.signum() == 0) return;
        if (GlobalEnergyWorldSavedData.INSTANCE == null)
            throw new IllegalStateException("Wireless energy is unavailable");
        apply(GlobalVariableStorage.GlobalEnergy, token, target, amount);
        GlobalEnergyWorldSavedData.INSTANCE.markDirty();
    }

    /** Server-thread only. The target is captured in the immutable grant, before any team changes. */
    static boolean apply(Map<UUID, BigInteger> energy, String token, UUID target, BigInteger amount) {
        if (target == null || amount.signum() < 0) throw new IllegalArgumentException("Invalid wireless transaction");
        UUID receipt = receiptKey(token);
        if (receipt.equals(target)) throw new IllegalArgumentException("Invalid wireless target");
        if (energy.containsKey(receipt)) return false;
        BigInteger previous = energy.getOrDefault(target, BigInteger.ZERO);
        energy.put(target, previous.add(amount));
        energy.put(receipt, BigInteger.ZERO);
        return true;
    }
}
