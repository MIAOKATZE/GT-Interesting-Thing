package com.miaokatze.gtit.trade;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Exact nonnegative currency ledger. The legacy GUI/network API exposes at most
 * {@link Integer#MAX_VALUE}; excess remains owned and spendable through repeated int-sized operations.
 * Physical imports retain their int-sized capacity limit, but committed rewards and refunds never lose value.
 */
public class NekoWallet {

    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final String EXACT_TAG = "__gtit_exact_balances_v1";
    private static final String VERSION_TAG = "__gtit_wallet_version";
    /** Only team merges acquire two wallet locks; serialize their directed source-to-survivor ordering. */
    private static final Object MERGE_LOCK = new Object();

    private final Map<String, BigInteger> balances = new HashMap<>();
    /** A consumed team's live references must still deliver late refunds to the surviving wallet. */
    private NekoWallet successor;
    private UUID playerId;
    private UUID teamId;

    public synchronized void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public synchronized void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public synchronized boolean isTeamWallet() {
        return teamId != null;
    }

    private void notifyChanged() {
        if (playerId != null || teamId != null) {
            NekoWalletManager.INSTANCE.notifyWalletChanged(playerId, teamId);
        }
    }

    /** Bounded compatibility view, not the exact ledger total. Never use this to copy/merge a wallet. */
    public synchronized int getCount(String currencyId) {
        return getExactCount(currencyId).min(INT_MAX)
            .intValue();
    }

    public synchronized BigInteger getExactCount(String currencyId) {
        if (successor != null) return successor.getExactCount(currencyId);
        return balances.getOrDefault(currencyId, BigInteger.ZERO);
    }

    /** Exact reward/refund credit; negative legacy adjustments retain their floor-at-zero semantics. */
    public synchronized void addCount(String currencyId, int amount) {
        if (successor != null) {
            successor.addCount(currencyId, amount);
            return;
        }
        if (currencyId == null) throw new IllegalArgumentException("currencyId");
        BigInteger current = getExactCount(currencyId);
        BigInteger next = current.add(BigInteger.valueOf(amount))
            .max(BigInteger.ZERO);
        if (next.equals(current)) return;
        balances.put(currencyId, next);
        notifyChanged();
    }

    /**
     * Advisory physical-import headroom. A consumed alias monitor does not reserve its survivor's room;
     * callers must use atomic bounded credit, or exact addCount for an already committed extraction.
     */
    public synchronized int getCreditCapacity(String currencyId) {
        if (successor != null) return successor.getCreditCapacity(currencyId);
        return INT_MAX.subtract(getExactCount(currencyId))
            .max(BigInteger.ZERO)
            .intValue();
    }

    /** Credit at most available physical-import capacity; the caller retains every unaccepted coin. */
    public synchronized int creditUpToCapacity(String currencyId, int requested) {
        if (successor != null) return successor.creditUpToCapacity(currencyId, requested);
        if (requested <= 0) return 0;
        int accepted = Math.min(requested, getCreditCapacity(currencyId));
        if (accepted > 0) addCount(currencyId, accepted);
        return accepted;
    }

    /** All-or-nothing physical slot deposit, including when this object is a consumed team alias. */
    public synchronized boolean tryCreditWithinCapacity(String currencyId, int requested) {
        if (successor != null) return successor.tryCreditWithinCapacity(currencyId, requested);
        if (requested <= 0 || requested > getCreditCapacity(currencyId)) return false;
        addCount(currencyId, requested);
        return true;
    }

    /** Atomic sufficient-balance check and exact debit. */
    public synchronized boolean tryDeduct(String currencyId, int amount) {
        if (successor != null) return successor.tryDeduct(currencyId, amount);
        if (amount <= 0) return true;
        BigInteger current = getExactCount(currencyId);
        BigInteger debit = BigInteger.valueOf(amount);
        if (current.compareTo(debit) < 0) return false;
        balances.put(currencyId, current.subtract(debit));
        notifyChanged();
        return true;
    }

    public synchronized void resetCount(String currencyId) {
        if (successor != null) {
            successor.resetCount(currencyId);
            return;
        }
        if (balances.remove(currencyId) != null) notifyChanged();
    }

    public synchronized void resetAll() {
        if (successor != null) {
            successor.resetAll();
            return;
        }
        if (balances.isEmpty()) return;
        balances.clear();
        notifyChanged();
    }

    /**
     * Consume a team's exact ledger once. Call without holding either wallet monitor.
     * Live references to the consumed wallet forward to the survivor, including late rollback credits.
     * Its own NBT stays empty, so saving consumed team data cannot duplicate the transferred funds.
     */
    public void transferAllTo(NekoWallet destination) {
        if (destination == null) throw new IllegalArgumentException("destination");
        synchronized (MERGE_LOCK) {
            // A consumed source is never drained again, even if a stale callback names a new destination.
            synchronized (this) {
                if (successor != null) return;
            }
            NekoWallet source = this;
            NekoWallet target = destination.root();
            if (source == target) return;
            synchronized (source) {
                synchronized (target) {
                    for (Map.Entry<String, BigInteger> entry : source.balances.entrySet()) {
                        target.balances.merge(entry.getKey(), entry.getValue(), BigInteger::add);
                    }
                    source.balances.clear();
                    source.successor = target;
                    source.notifyChanged();
                    target.notifyChanged();
                }
            }
        }
    }

    /** Only called under MERGE_LOCK; successor links are immutable once installed. */
    private synchronized NekoWallet root() {
        return successor == null ? this : successor.root();
    }

    /** Ordinary balances retain legacy int NBT; only overflow adds a versioned exact side compound. */
    public synchronized NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagCompound exact = new NBTTagCompound();
        boolean overflow = false;
        for (Map.Entry<String, BigInteger> entry : balances.entrySet()) {
            BigInteger value = entry.getValue();
            boolean reservedKey = EXACT_TAG.equals(entry.getKey()) || VERSION_TAG.equals(entry.getKey());
            if (!reservedKey) nbt.setInteger(
                entry.getKey(),
                value.min(INT_MAX)
                    .intValue());
            if (reservedKey || value.compareTo(INT_MAX) > 0) {
                exact.setString(entry.getKey(), value.toString());
                overflow = true;
            }
        }
        if (overflow) {
            nbt.setInteger(VERSION_TAG, 1);
            nbt.setTag(EXACT_TAG, exact);
        }
        return nbt;
    }

    /** Load transactionally: malformed exact data must not silently replace owned funds with zero. */
    public synchronized void readFromNBT(NBTTagCompound nbt) {
        if (successor != null) throw new IllegalStateException("Cannot reload a consumed team wallet");
        Map<String, BigInteger> loaded = new HashMap<>();
        if (nbt != null) {
            // Legacy wallets used every top-level numeric key as a currency ID, including these
            // newly reserved names. Only a compound can distinguish exact metadata from old money.
            boolean exactFormat = nbt.hasKey(EXACT_TAG, 10);
            if (exactFormat && (!nbt.hasKey(VERSION_TAG, 3) || nbt.getInteger(VERSION_TAG) != 1)) {
                throw new IllegalArgumentException("Unsupported exact wallet format");
            }
            NBTTagCompound exact = exactFormat ? nbt.getCompoundTag(EXACT_TAG) : new NBTTagCompound();
            // Preserve every actual currency key, not only today's registered denominations.
            Set<String> currencyIds = new HashSet<>(nbt.func_150296_c());
            if (exactFormat) {
                currencyIds.remove(EXACT_TAG);
                currencyIds.remove(VERSION_TAG);
            }
            currencyIds.addAll(exact.func_150296_c());
            for (String currencyId : currencyIds) {
                boolean reservedKey = exactFormat && (EXACT_TAG.equals(currencyId) || VERSION_TAG.equals(currencyId));
                if (!reservedKey && (!nbt.hasKey(currencyId, 1) && !nbt.hasKey(currencyId, 2)
                    && !nbt.hasKey(currencyId, 3)
                    && !nbt.hasKey(currencyId, 4))) {
                    throw new IllegalArgumentException("Invalid legacy wallet balance");
                }
                long legacy = reservedKey ? 0 : nbt.getLong(currencyId);
                if (legacy < 0) throw new IllegalArgumentException("Negative wallet balance");
                BigInteger value = BigInteger.valueOf(legacy);
                if (exact.hasKey(currencyId)) {
                    if (!exact.hasKey(currencyId, 8))
                        throw new IllegalArgumentException("Invalid exact wallet balance");
                    value = new BigInteger(exact.getString(currencyId));
                    if (value.signum() < 0 || (!reservedKey
                        && (value.compareTo(INT_MAX) <= 0 || nbt.getLong(currencyId) != Integer.MAX_VALUE))) {
                        throw new IllegalArgumentException("Inconsistent exact wallet balance");
                    }
                }
                if (value.signum() > 0) loaded.put(currencyId, value);
            }
        }
        balances.clear();
        balances.putAll(loaded);
    }

    /** Detached key snapshot: callers cannot mutate the ledger through its key set. */
    public synchronized Set<String> getCurrencyIds() {
        if (successor != null) return successor.getCurrencyIds();
        return new HashSet<>(balances.keySet());
    }

    public synchronized NekoWallet copy() {
        if (successor != null) return successor.copy();
        NekoWallet wallet = new NekoWallet();
        wallet.balances.putAll(balances);
        return wallet;
    }
}
