package com.miaokatze.gtit.common.items.pocket;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtit.common.items.pocket.channel.PocketChannelSessions;
import com.miaokatze.gtit.main.GTInterestingThing;

/** Owns the live inventory and its carrier authority. No widgets or panel are retained after close. */
public final class PocketRuntimeSession implements PocketSession {

    private final Supplier<EntityPlayer> owner;
    private final Supplier<ItemStack> originalSlot;
    private final int slotIndex;
    private final PocketCarrierIdentity identity;
    private final PocketInventory inventory;
    private final boolean client;
    private ItemStack carrier;
    private boolean ambiguous;
    private boolean open = true;
    private boolean nestedWarned;
    private NBTBase pendingOutgoing;
    private PocketTransferReservation outgoing;
    private NBTTagCompound lastTransferResolution;

    public PocketRuntimeSession(Supplier<EntityPlayer> owner, Supplier<ItemStack> originalSlot, int slotIndex,
        ItemStack carrier, boolean client) {
        this(owner, originalSlot, slotIndex, carrier, client, true, null);
    }

    public PocketRuntimeSession(Supplier<EntityPlayer> owner, Supplier<ItemStack> originalSlot, int slotIndex,
        ItemStack carrier, boolean client, boolean open, PocketInventory inventory) {
        this.owner = owner;
        this.originalSlot = originalSlot;
        this.slotIndex = slotIndex;
        this.carrier = carrier;
        this.client = client;
        this.open = open;
        this.identity = PocketCarrierIdentity.capture(carrier, !client);
        this.inventory = inventory == null ? PocketChannelSessions.parseCarrierInventory(carrier) : inventory;
        NBTTagCompound initial = carrier == null ? null : carrier.getTagCompound();
        if (initial != null && initial.hasKey(PocketTransferReservation.NBT_KEY)) {
            pendingOutgoing = initial.getTag(PocketTransferReservation.NBT_KEY)
                .copy();
            if (pendingOutgoing instanceof NBTTagCompound saved)
                outgoing = PocketTransferReservation.knownRefund(saved);
        }
        this.inventory.setUpgradeProbes(
            () -> PocketUpgradeSwitches.isActive(carrierStack(), PocketUpgradeType.CAPACITY),
            this::storageStackUpgraded);
        this.inventory.setStackCountProbe(this::storageStackUpgradeCount);
        this.inventory.setCapacityCountProbe(
            () -> PocketUpgradeSwitches.isActive(carrierStack(), PocketUpgradeType.CAPACITY)
                ? PocketUpgrades.capacityUpgradeCount(carrierStack())
                : 0);
        this.inventory.essence()
            .setDeclaredTagProbe(tag -> PocketEssenceIntake.isDeclaredEssenceTag(filters(), tag));
    }

    public PocketInventory inventory() {
        return inventory;
    }

    public boolean isClient() {
        return client;
    }

    public ItemStack knownCarrier() {
        return carrier;
    }

    @Override
    public EntityPlayer player() {
        return owner.get();
    }

    @Override
    public UUID playerId() {
        EntityPlayer player = player();
        return player == null || player.getGameProfile() == null ? null
            : player.getGameProfile()
                .getId();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public ItemStack carrierStack() {
        if (client) return originalSlot.get();
        EntityPlayer player = player();
        ambiguous = false;
        if (player == null || player.inventory == null) return null;
        IInventory baubles = PocketWornTapHandler.safeBaubles(player);
        PocketCarrierIdentity.Resolution found = identity
            .locate(originalSlot.get(), player.inventory.getItemStack(), player.inventory.mainInventory, baubles);
        ambiguous = found.ambiguous;
        if (found.carrier != null) carrier = found.carrier;
        return found.carrier;
    }

    public boolean canInteract(EntityPlayer actor) {
        if (!open || actor == null || carrier == null) return false;
        if (client) return true;
        EntityPlayer player = player();
        if (player != null && (player.getUniqueID() == null || !player.getUniqueID()
            .equals(actor.getUniqueID()))) return false;
        return carrierStack() != null;
    }

    @Override
    public NBTTagCompound carrierTag() {
        ItemStack stack = carrierStack();
        return stack == null ? null : stack.getTagCompound();
    }

    @Override
    public boolean isHeldByOwner() {
        EntityPlayer player = player();
        ItemStack stack = carrierStack();
        return player != null && player.inventory != null
            && stack != null
            && PocketCarrierIdentity
                .isHeld(stack, player.inventory.mainInventory, PocketWornTapHandler.safeBaubles(player));
    }

    @Override
    public void markDirty() {
        inventory.markDirty();
    }

    @Override
    public void persistIdle() {
        if (!open) persistFinal();
    }

    public void close() {
        if (!open) return;
        ItemStack stack = carrierStack();
        if (stack != null) ItemNekoDimensionPocket.setOpenFlag(stack, false);
        open = false;
        persistFinal();
    }

    public boolean persistBeforeCursorDrop(EntityPlayer actor) {
        if (client || !open || actor != player() || actor.inventory == null) return true;
        if (!identity.matches(actor.inventory.getItemStack())) return true;
        if (carrierStack() != actor.inventory.getItemStack()) return false;
        persistFinal();
        return !inventory.isDirty();
    }

    @Override
    public void persistFinal() {
        if (client) return;
        settleKnownRefund();
        if (!inventory.isDirty()) return;
        ItemStack stack = carrierStack();
        if (stack == null && !ambiguous && identity.matches(carrier)) stack = carrier;
        if (stack == null) {
            rollbackEssenceToPersistedBaseline();
            return;
        }
        for (int index = 0; index < inventory.storage()
            .getSlots(); index++) {
            if (inventory.storage()
                .getStackInSlot(index) == stack) {
                if (!nestedWarned) {
                    nestedWarned = true;
                    GTInterestingThing.LOG
                        .warn("[pocket] Refusing nested carrier save at slot {}; retaining dirty state", slotIndex);
                }
                return;
            }
        }
        // Prepare detached NBT first: a serialization failure must leave the accepted carrier baseline intact.
        NBTTagCompound root = stack.getTagCompound() == null ? new NBTTagCompound()
            : (NBTTagCompound) stack.getTagCompound()
                .copy();
        inventory.writeTo(root);
        if (pendingOutgoing == null) root.removeTag(PocketTransferReservation.NBT_KEY);
        else root.setTag(PocketTransferReservation.NBT_KEY, pendingOutgoing.copy());
        if (lastTransferResolution != null)
            root.setTag("gtitPocketOutgoingTransferLastResolution", lastTransferResolution.copy());
        NBTTagCompound live = stack.getTagCompound();
        if (live == null) stack.setTagCompound(root);
        else {
            if (pendingOutgoing == null) live.removeTag(PocketTransferReservation.NBT_KEY);
            // ElementStore intentionally remains attached to the existing root used by passive drivers.
            for (Object key : root.func_150296_c()) live.setTag((String) key, root.getTag((String) key));
        }
        carrier = stack;
        inventory.markClean();
    }

    public boolean hasPendingOutgoingTransfer() {
        return pendingOutgoing != null;
    }

    /** The receipt is diagnostic only. Its resource payload never enters the available inventory. */
    public String outgoingTransferDiagnostic() {
        if (pendingOutgoing == null) return "none";
        if (!(pendingOutgoing instanceof NBTTagCompound tag))
            return "damaged outgoing transfer receipt; inspect carrier NBT";
        return "token=" + tag.getString("token")
            + " state="
            + tag.getString("state")
            + " kind="
            + tag.getString("kind")
            + " amount="
            + tag.getLong("amount")
            + " destination="
            + tag.getString("destination");
    }

    public PocketTransferReservation reserveOutgoingItem(int slot, ItemStack expected, int amount, String destination) {
        if (!canReserve() || expected == null
            || expected == carrier
            || storageStackAt(slot) != expected
            || amount <= 0
            || amount > expected.stackSize) return null;
        PocketTransferReservation ticket = PocketTransferReservation.item(slot, expected, amount, destination);
        ItemStack original = expected.copy();
        ItemStack rest = amount == expected.stackSize ? null : expected.copy();
        if (rest != null) rest.stackSize -= amount;
        setStorageStackAt(slot, rest);
        return publishReservation(ticket, () -> setStorageStackAt(slot, original));
    }

    public PocketTransferReservation reserveOutgoingFluid(int tank, FluidStack expected, long amount,
        String destination) {
        if (!canReserve()) return null;
        FluidStack current = fluidInTank(tank);
        if (current == null || expected == null
            || !current.isFluidEqual(expected)
            || amount <= 0
            || amount > inventory.tankTruthAt(tank)) return null;
        PocketTransferReservation ticket = PocketTransferReservation.fluid(tank, current, amount, destination);
        long taken = drainOwnTankL(tank, amount);
        if (taken != amount) {
            if (taken > 0) fillReservedFluid(tank, expected, taken);
            return null;
        }
        return publishReservation(ticket, () -> fillReservedFluid(tank, expected, amount));
    }

    public PocketTransferReservation reserveOutgoingEssence(String tag, int amount, String destination) {
        if (!canReserve() || amount <= 0 || essence().get(tag) < amount) return null;
        PocketTransferReservation ticket = PocketTransferReservation.essence(tag, amount, destination);
        if (drainEssence(tag, amount) != amount) return null;
        return publishReservation(ticket, () -> essence().add(tag, amount));
    }

    private boolean canReserve() {
        if (client || carrierStack() == null) return false;
        settleKnownRefund();
        return pendingOutgoing == null;
    }

    private PocketTransferReservation publishReservation(PocketTransferReservation ticket, Runnable undo) {
        outgoing = ticket;
        pendingOutgoing = ticket.record;
        markDirty();
        try {
            persistFinal();
            if (!inventory.isDirty()) return ticket;
        } catch (RuntimeException failure) {
            GTInterestingThing.LOG
                .error("[pocket] Outgoing reservation could not be published; target was not called", failure);
        }
        // No external target was called, so restoring this reservation is known safe.
        pendingOutgoing = null;
        outgoing = null;
        undo.run();
        markDirty();
        return null;
    }

    public void completeOutgoing(PocketTransferReservation ticket, long knownRemainder) {
        if (ticket == null || outgoing != ticket) throw new IllegalStateException("Stale outgoing transfer receipt");
        ticket.knownRemainder(knownRemainder);
        settleKnownRefund();
        markDirty();
        persistFinal();
    }

    /** A target exception or invalid response leaves the original pre-call unknown receipt quarantined. */
    public void retainUnknownOutgoing(PocketTransferReservation ticket, Throwable failure) {
        GTInterestingThing.LOG.error(
            "[pocket] Outgoing transfer requires operator verification: " + outgoingTransferDiagnostic(),
            failure);
        markDirty();
        try {
            persistFinal();
        } catch (RuntimeException saveFailure) {
            if (failure != saveFailure) failure.addSuppressed(saveFailure);
        }
    }

    private void settleKnownRefund() {
        if (outgoing == null || !"refund".equals(outgoing.record.getString("state"))) return;
        long amount = outgoing.record.getLong("amount"), refunded = 0;
        if (amount > 0 && outgoing.item != null) {
            ItemStack rest = outgoing.item.copy();
            rest.stackSize = (int) amount;
            if (outgoing.sourceSlot >= 0 && outgoing.sourceSlot < storageSlots()) rest = inventory.storage()
                .insertItem(outgoing.sourceSlot, rest, false);
            int left = rest == null ? 0 : rest.stackSize;
            if (left > 0) left -= inventory.depositIntoStorage(rest);
            refunded = amount - left;
        } else if (amount > 0 && outgoing.fluid != null) {
            refunded = fillReservedFluid(outgoing.sourceSlot, outgoing.fluid, amount);
            for (int tank = 0; tank < fluidTankCount() && refunded < amount; tank++) {
                if (tank != outgoing.sourceSlot) refunded += fillReservedFluid(tank, outgoing.fluid, amount - refunded);
            }
        } else if (amount > 0 && outgoing.essenceTag != null) {
            refunded = essence().add(outgoing.essenceTag, (int) amount);
        }
        if (refunded > 0 || amount == 0) {
            outgoing.record.setLong("amount", amount - refunded);
            markDirty();
        }
        if (amount == refunded) {
            pendingOutgoing = null;
            outgoing = null;
        }
    }

    private long fillReservedFluid(int tank, FluidStack sample, long amount) {
        if (amount <= 0 || amount > Integer.MAX_VALUE) return 0;
        FluidStack request = sample.copy();
        request.amount = (int) amount;
        return inventory.fillOwnTank(tank, request);
    }

    /**
     * Operator-confirmed consumption only; there is deliberately no automatic or command refund for unknown outcomes.
     */
    public void acknowledgeOutgoingConsumed(String token, String operator) {
        if (client || carrierStack() == null
            || !(pendingOutgoing instanceof NBTTagCompound saved)
            || !"unknown".equals(saved.getString("state"))
            || !saved.getString("token")
                .equals(token))
            throw new IllegalStateException("Current carrier and exact unknown receipt token required");
        UUID.fromString(token);
        NBTBase previous = pendingOutgoing;
        PocketTransferReservation previousOutgoing = outgoing;
        NBTTagCompound previousAudit = lastTransferResolution;
        NBTTagCompound audit = (NBTTagCompound) saved.copy();
        audit.setString("decision", "consumed-by-operator");
        audit.setString(
            "operator",
            operator == null ? "console" : operator.substring(0, Math.min(operator.length(), 64)));
        audit.setLong("resolvedAt", System.currentTimeMillis());
        lastTransferResolution = audit;
        pendingOutgoing = null;
        outgoing = null;
        markDirty();
        try {
            persistFinal();
            if (inventory.isDirty())
                throw new IllegalStateException("Resolution could not be persisted; receipt retained");
        } catch (RuntimeException failure) {
            pendingOutgoing = previous;
            outgoing = previousOutgoing;
            lastTransferResolution = previousAudit;
            markDirty();
            throw failure;
        }
    }

    private void rollbackEssenceToPersistedBaseline() {
        Map<String, Integer> deltas = inventory.essenceDeltas();
        if (deltas.isEmpty()) return;
        for (Map.Entry<String, Integer> entry : deltas.entrySet()) {
            int delta = entry.getValue();
            if (delta < 0) inventory.essence()
                .add(entry.getKey(), -delta);
            else if (delta > 0) inventory.essence()
                .extract(entry.getKey(), delta);
        }
        inventory.clearEssenceDeltas();
        GTInterestingThing.LOG
            .warn("[pocket] Carrier authority lost; reverted unpersisted essence deltas, retaining dirty state");
    }

    @Override
    public PocketCellBindings bindings() {
        return inventory.bindings();
    }

    @Override
    public PocketFilterConfig filters() {
        return inventory.filters();
    }

    @Override
    public PocketEssenceStore essence() {
        return inventory.essence();
    }

    @Override
    public ItemStack distillInputStack(int index) {
        return index < 0 || index >= distillInputSlots() ? null
            : inventory.distillInput()
                .getStackInSlot(index);
    }

    @Override
    public int distillInputSlots() {
        return inventory.distillInput()
            .getSlots();
    }

    @Override
    public void consumeOneDistillInput(int index) {
        ItemStack stack = distillInputStack(index);
        if (stack == null) return;
        ItemStack rest = stack.stackSize <= 1 ? null : stack.copy();
        if (rest != null) rest.stackSize--;
        inventory.distillInput()
            .setStackInSlot(index, rest);
        inventory.markDirty();
    }

    @Override
    public int depositItem(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return 0;
        int want = stack.stackSize;
        int moved = inventory.depositIntoStorage(stack);
        if (moved >= want) return moved;
        ItemStack rest = stack.copy();
        rest.stackSize = want - moved;
        return moved + moveToPlayer(rest);
    }

    public int moveToPlayer(ItemStack stack) {
        EntityPlayer player = player();
        if (player == null || stack == null || stack.stackSize <= 0) return 0;
        int want = stack.stackSize;
        boolean accepted = player.inventory.addItemStackToInventory(stack);
        return movedByVanillaContract(accepted, want, stack.stackSize);
    }

    public int depositToPlayerFirst(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return 0;
        int moved = moveToPlayer(stack.copy());
        if (moved >= stack.stackSize) return stack.stackSize;
        ItemStack rest = stack.copy();
        rest.stackSize -= moved;
        return moved + depositItem(rest);
    }

    public static int movedByVanillaContract(boolean accepted, int want, int rest) {
        return want <= 0 ? 0 : accepted ? want : Math.max(0, want - Math.max(0, rest));
    }

    @Override
    public int fluidBarRoom(int tank, FluidStack probe) {
        return inventory.fluidBarRoom(tank, probe);
    }

    @Override
    public int depositFluid(int tank, FluidStack stack) {
        return inventory.depositFluidIntoBar(tank, stack);
    }

    @Override
    public long fluidBarRoomL(int tank, FluidStack probe) {
        return inventory.fluidBarRoomL(tank, probe);
    }

    @Override
    public long depositFluidL(int tank, Fluid fluid, long amount) {
        return inventory.fillOwnTankL(tank, fluid, amount);
    }

    @Override
    public long drainOwnTankL(int tank, long amount) {
        return inventory.drainOwnTankL(tank, amount);
    }

    @Override
    public boolean storageStackUpgraded() {
        return PocketUpgradeSwitches.isActive(carrierStack(), PocketUpgradeType.STACK);
    }

    @Override
    public int storageStackUpgradeCount() {
        return storageStackUpgraded() ? PocketUpgrades.stackUpgradeCount(carrierStack()) : 0;
    }

    @Override
    public int fluidTankCount() {
        return PocketInventory.tankCount();
    }

    @Override
    public FluidStack fluidInTank(int tank) {
        return inventory.ownTankFluid(tank);
    }

    @Override
    public int drainOwnTank(int tank, int amount) {
        return inventory.drainOwnTank(tank, amount);
    }

    @Override
    public Map<String, Integer> essenceStock() {
        return inventory.essence()
            .snapshot();
    }

    @Override
    public int drainEssence(String tag, int points) {
        int removed = inventory.essence()
            .extract(tag, points);
        if (removed > 0) {
            inventory.recordEssenceDelta(tag, -removed);
            inventory.markDirty();
        }
        return removed;
    }

    @Override
    public int storageSlots() {
        return inventory.storage()
            .getSlots();
    }

    @Override
    public ItemStack storageStackAt(int slot) {
        return slot < 0 || slot >= storageSlots() ? null
            : inventory.storage()
                .getStackInSlot(slot);
    }

    @Override
    public void setStorageStackAt(int slot, ItemStack stack) {
        if (slot < 0 || slot >= storageSlots()) return;
        inventory.storage()
            .setStackInSlot(slot, stack);
        inventory.markDirty();
    }

    @Override
    public boolean isStorageGhostDeclared(int slot) {
        return inventory.isGhostItemSlot(slot);
    }
}
