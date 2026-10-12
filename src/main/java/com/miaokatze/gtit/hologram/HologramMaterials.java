package com.miaokatze.gtit.hologram;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.util.InventoryUtility;
import com.gtnewhorizon.structurelib.util.InventoryUtility.ItemStackExtractor;
import com.gtnewhorizon.structurelib.util.InventoryUtility.ItemStackExtractor.APIType;
import com.gtnewhorizon.structurelib.util.ItemStackPredicate;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.stats.Stats;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;
import appeng.util.PlayerInventoryUtil;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import gregtech.common.blocks.ItemMachines;
import gregtech.common.misc.WirelessNetworkManager;

/** One batch's native container adapters and authenticated wireless terminal material source. */
public final class HologramMaterials {

    private static final String SETTINGS = "gtitHologramMaterials";
    private static final String RECOVERY = "gtitHologramMaterialRecovery";
    private static final String RECOVERY_ME = "gtitRecoveryMeFirst";
    private static final String RECOVERY_COUNT = "gtitRecoveryCount";
    private static final String RECOVERY_FLUID = "gtitHologramFluidRecovery";
    private static final String RECOVERY_ENERGY = "gtitHologramEnergyRecovery";
    private static final Logger LOG = LogManager.getLogger("gtit");

    private HologramMaterials() {}

    public static final class Settings {

        public boolean main = true, containers = true, me = true;
        public int priority;
        public int recoveryDestination;
    }

    public static Settings read(ItemStack tool) {
        Settings result = new Settings();
        if (tool == null || !tool.hasTagCompound()
            || !tool.getTagCompound()
                .hasKey(SETTINGS, 10))
            return result;
        NBTTagCompound tag = tool.getTagCompound()
            .getCompoundTag(SETTINGS);
        result.main = !tag.hasKey("main") || tag.getBoolean("main");
        result.containers = !tag.hasKey("containers") || tag.getBoolean("containers");
        result.me = !tag.hasKey("me") || tag.getBoolean("me");
        result.priority = Math.max(0, Math.min(2, tag.getInteger("priority")));
        result.recoveryDestination = Math.max(0, Math.min(1, tag.getInteger("recoveryDestination")));
        return result;
    }

    public static void write(ItemStack tool, Settings settings) {
        if (tool == null) return;
        if (!tool.hasTagCompound()) tool.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("main", settings.main);
        tag.setBoolean("containers", settings.containers);
        tag.setBoolean("me", settings.me);
        tag.setInteger("priority", Math.max(0, Math.min(2, settings.priority)));
        tag.setInteger("recoveryDestination", Math.max(0, Math.min(1, settings.recoveryDestination)));
        tool.getTagCompound()
            .setTag(SETTINGS, tag);
    }

    public static final class Context implements IItemSource {

        private final EntityPlayerMP player;
        private final Settings settings;
        private final List<Container> containers = new ArrayList<>();
        private final Map<Integer, ItemStack> retainedContainers = new LinkedHashMap<>();
        private ItemStack terminalStack;
        private WirelessTerminalGuiObject terminal;
        private IWirelessTermHandler terminalHandler;
        private String terminalKey;
        private boolean terminalInfiniteRange, terminalInfinitePower;
        private int terminalSlot = -1;

        public Context(EntityPlayerMP player, Settings settings) {
            this(player, settings, null);
        }

        Context(EntityPlayerMP player, Settings settings, List<ItemStackExtractor> suppliedAdapters) {
            this.player = player;
            this.settings = settings;
            List<ItemStackExtractor> adapters = new ArrayList<>();
            if (settings.containers) {
                if (suppliedAdapters != null) adapters.addAll(suppliedAdapters);
                else {
                    Iterator<? extends ItemStackExtractor> iterator = InventoryUtility.getStackExtractors(player);
                    while (iterator.hasNext()) adapters.add(iterator.next());
                }
                for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
                    if (slot == player.inventory.currentItem) continue;
                    ItemStack source = player.inventory.mainInventory[slot];
                    if (source == null || source.stackSize <= 0) continue;
                    if (suppliedAdapters == null && AEApi.instance()
                        .registries()
                        .wireless()
                        .isWirelessTerminal(source)) continue;
                    for (ItemStackExtractor adapter : adapters) {
                        if (!adapter.isValidSource(source.copy(), player)) continue;
                        containers.add(new Container(slot, source, adapter));
                        if (adapter.isAPIImplemented(APIType.IS_VALID_SOURCE)) break;
                    }
                }
            }
            refreshTerminal();
        }

        private void refreshTerminal() {
            terminalStack = settings.me ? PlayerInventoryUtil.getFirstWirelessTerminal(player) : null;
            terminalSlot = -1;
            if (terminalStack != null) for (int slot = 0; slot < player.inventory.mainInventory.length; slot++)
                if (player.inventory.mainInventory[slot] == terminalStack) terminalSlot = slot;
            IWirelessTermHandler handler = terminalStack == null ? null
                : AEApi.instance()
                    .registries()
                    .wireless()
                    .getWirelessTerminalHandler(terminalStack);
            terminalHandler = handler;
            terminalKey = handler == null ? null : handler.getEncryptionKey(terminalStack);
            terminalInfiniteRange = handler != null && handler.hasInfinityRange(terminalStack);
            terminalInfinitePower = handler != null && handler.hasInfinityPower(terminalStack);
            terminal = handler == null ? null
                : new WirelessTerminalGuiObject(handler, terminalStack, player, player.worldObj, -1, 0, 0);
        }

        public Transaction begin() {
            return new Transaction(this);
        }

        public int count(ItemStack stack, int limit) {
            if (stack == null || stack.getItem() == null || limit <= 0) return 0;
            return amount(
                take(ItemStackPredicate.from(stack, ItemStackPredicate.NBTMode.EXACT), stack, true, limit, null));
        }

        public BigInteger energyAvailable() {
            return WirelessNetworkManager.getUserEU(player.getUniqueID());
        }

        public long fluidCount(FluidStack stack, long limit) {
            return simulateFluid(fluidAccess(), stack, limit);
        }

        private FluidAccess fluidAccess() {
            return new FluidAccess() {

                @Override
                public boolean available() {
                    return terminalAvailable() && terminal.getFluidInventory() != null;
                }

                @Override
                public long poweredCount(IAEFluidStack stack) {
                    if (!available()) return 0;
                    double power = terminal.extractAEPower(
                        Platform.ceilDiv(stack.getStackSize(), stack.getAmountPerUnit()),
                        Actionable.SIMULATE,
                        PowerMultiplier.CONFIG);
                    return Math.min(stack.getStackSize(), (long) (power * stack.getAmountPerUnit() + 0.9));
                }

                @Override
                public IAEFluidStack extract(IAEFluidStack stack, Actionable mode) {
                    if (!available()) return null;
                    return terminal.getFluidInventory()
                        .extractItems(stack, mode, new PlayerSource(player, terminal));
                }

                @Override
                public IAEFluidStack inject(IAEFluidStack stack) {
                    if (!available()) return stack;
                    return terminal.getFluidInventory()
                        .injectItems(stack, Actionable.MODULATE, new PlayerSource(player, terminal));
                }

                @Override
                public void charge(long amount, int unit) {
                    Context.this.charge(amount, unit);
                }
            };
        }

        private boolean takeFluid(FluidStack stack, long amount, Transaction transaction) {
            try {
                return reserveFluid(fluidAccess(), stack, amount, transaction.meFluids::add);
            } finally {
                if (terminalSlot >= 0) {
                    transaction.terminalSlot = terminalSlot;
                    transaction.terminalState = terminalStack.copy();
                }
            }
        }

        private void returnFluid(IAEFluidStack stack) {
            refundFluid(fluidAccess(), stack);
        }

        private void recoverFluid(IAEFluidStack stack) {
            if (stack == null || stack.getStackSize() <= 0) return;
            NBTTagCompound owner = recoveryOwner();
            NBTTagList stored = owner.getTagList(RECOVERY_FLUID, 10);
            NBTTagCompound entry = fluidReceipt(stack);
            stored.appendTag(entry);
            owner.setTag(RECOVERY_FLUID, stored);
        }

        private void recoverEnergy(BigInteger amount) {
            if (amount.signum() <= 0) return;
            NBTTagCompound owner = recoveryOwner();
            BigInteger prior = owner.hasKey(RECOVERY_ENERGY, 8) ? new BigInteger(owner.getString(RECOVERY_ENERGY))
                : BigInteger.ZERO;
            owner.setString(
                RECOVERY_ENERGY,
                prior.add(amount)
                    .toString());
        }

        private void flushResourceRecovery() {
            NBTTagCompound owner = recoveryOwner();
            if (owner.hasKey(RECOVERY_ENERGY, 8)) {
                try {
                    BigInteger amount = new BigInteger(owner.getString(RECOVERY_ENERGY));
                    if (amount.signum() > 0
                        && WirelessNetworkManager.addEUToGlobalEnergyMap(player.getUniqueID(), amount))
                        owner.removeTag(RECOVERY_ENERGY);
                } catch (RuntimeException | LinkageError failure) {
                    LOG.warn("Hologram wireless energy refund remains queued", failure);
                }
            }
            NBTTagList stored = owner.getTagList(RECOVERY_FLUID, 10);
            NBTTagList remaining = new NBTTagList();
            for (int i = 0; i < stored.tagCount(); i++) {
                NBTTagCompound receipt = stored.getCompoundTagAt(i);
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(receipt);
                long amount = receipt.getLong("gtitRecoveryAmount");
                if (fluid == null || amount <= 0 || i >= 96 || !supportsFluidNbt(fluid)) {
                    remaining.appendTag(receipt.copy());
                    continue;
                }
                IAEFluidStack stack = AEFluidStack.create(fluid);
                stack.setStackSize(amount);
                try {
                    returnFluid(stack);
                } catch (RuntimeException | LinkageError failure) {
                    LOG.warn("Hologram AE fluid return failed; preserving known remainder", failure);
                } finally {
                    receipt.setLong("gtitRecoveryAmount", stack.getStackSize());
                }
                if (stack.getStackSize() > 0) remaining.appendTag(receipt.copy());
            }
            owner.setTag(RECOVERY_FLUID, remaining);
        }

        /** Enabled main inventory stock. Disabled sources are not inspected; the held tool slot is excluded. */
        public int mainCount(ItemStack stack, int limit) {
            if (!settings.main) return 0;
            return HologramMaterials
                .mainCount(player.inventory.mainInventory, player.inventory.currentItem, stack, limit);
        }

        /** Bounded, read-only UI samples. Optimized-only container APIs cannot enumerate their inventory. */
        public List<ItemStack> catalog(int limit) {
            List<ItemStack> result = new ArrayList<>();
            int maximum = Math.min(96, Math.max(0, limit));
            long deadline = System.nanoTime() + 50_000_000L;
            int[] order = settings.priority == 1 ? new int[] { 1, 0, 2 }
                : settings.priority == 2 ? new int[] { 2, 0, 1 } : new int[] { 0, 1, 2 };
            for (int type : order) {
                if (result.size() >= maximum || System.nanoTime() > deadline) break;
                if (type == 0 && settings.main) {
                    for (int slot = 0; slot < player.inventory.mainInventory.length
                        && result.size() < maximum; slot++) {
                        if (slot != player.inventory.currentItem)
                            addChoice(result, player.inventory.mainInventory[slot]);
                    }
                } else if (type == 1 && settings.containers) {
                    for (Container container : containers) {
                        if (player.inventory.mainInventory[container.slot] != container.source
                            || !container.adapter.isAPIImplemented(APIType.MAIN)
                            || !container.adapter.isValidSource(container.source.copy(), player)) continue;
                        while (result.size() < maximum && System.nanoTime() <= deadline) {
                            int before = result.size();
                            container.adapter.takeFromStack(
                                stack -> placeable(stack) && !contains(result, stack),
                                true,
                                1,
                                (stack, number) -> {
                                    if (number > 0 && result.size() < maximum) addChoice(result, stack);
                                },
                                container.source.copy(),
                                null,
                                player);
                            if (result.size() == before) break;
                        }
                        if (result.size() >= maximum || System.nanoTime() > deadline) break;
                    }
                } else if (type == 2 && settings.me && terminalAvailable()) {
                    IMEMonitor<IAEItemStack> monitor = terminal.getItemInventory();
                    if (monitor == null) continue;
                    for (IAEItemStack entry : monitor.getStorageList()) {
                        if (result.size() >= maximum || System.nanoTime() > deadline) break;
                        ItemStack sample = entry.getItemStack();
                        if (entry.getStackSize() <= 0 || !placeable(sample) || contains(result, sample)) continue;
                        IAEItemStack request = entry.copy();
                        request.setStackSize(1);
                        if (terminalAvailable() && Platform.poweredExtraction(
                            terminal,
                            monitor,
                            request,
                            new PlayerSource(player, terminal),
                            Actionable.SIMULATE) != null) addChoice(result, sample);
                    }
                }
            }
            return result;
        }

        @Override
        public Map<ItemStack, Integer> take(Predicate<ItemStack> predicate, boolean simulate, int count) {
            return take(predicate, null, simulate, count, null);
        }

        @Override
        public boolean takeOne(ItemStack stack, boolean simulate) {
            requireOne(stack);
            return exact(stack, simulate, null);
        }

        @Override
        public boolean takeAll(ItemStack stack, boolean simulate) {
            return exact(stack, simulate, null);
        }

        private boolean exact(ItemStack stack, boolean simulate, Transaction transaction) {
            if (stack == null || stack.getItem() == null || stack.stackSize < 1) return false;
            Predicate<ItemStack> predicate = ItemStackPredicate.from(stack, ItemStackPredicate.NBTMode.EXACT);
            if (amount(take(predicate, stack, true, stack.stackSize, null)) < stack.stackSize) return false;
            return simulate || amount(take(predicate, stack, false, stack.stackSize, transaction)) == stack.stackSize;
        }

        private Map<ItemStack, Integer> take(Predicate<ItemStack> predicate, ItemStack exact, boolean simulate,
            int count, Transaction transaction) {
            Map<ItemStack, Integer> result = new LinkedHashMap<>();
            // Anonymous predicates retain the construction batch budget. Explicit exact identities can pay
            // large GT inventory slots through one bounded adapter call without millions of takeOne calls.
            if (predicate == null || count < 1 || !simulate && exact == null && count > 4096) return result;
            int[] order = settings.priority == 1 ? new int[] { 1, 0, 2 }
                : settings.priority == 2 ? new int[] { 2, 0, 1 } : new int[] { 0, 1, 2 };
            for (int type : order) {
                int remaining = count - amount(result);
                if (remaining <= 0) break;
                if (type == 0 && settings.main) fromMain(predicate, simulate, remaining, result);
                else if (type == 1 && settings.containers)
                    fromContainers(predicate, exact, simulate, remaining, result, transaction);
                else if (type == 2 && settings.me) fromMe(predicate, exact, simulate, remaining, result, transaction);
            }
            if (!simulate) player.inventory.markDirty();
            return result;
        }

        private void fromMain(Predicate<ItemStack> predicate, boolean simulate, int count,
            Map<ItemStack, Integer> result) {
            for (int slot = 0; slot < player.inventory.mainInventory.length && count > 0; slot++) {
                if (slot == player.inventory.currentItem) continue;
                ItemStack stack = player.inventory.mainInventory[slot];
                if (stack == null || stack.stackSize <= 0 || !predicate.test(stack)) continue;
                int taken = Math.min(count, stack.stackSize);
                add(result, stack, taken);
                count -= taken;
                if (!simulate) {
                    stack.stackSize -= taken;
                    if (stack.stackSize <= 0) player.inventory.mainInventory[slot] = null;
                }
            }
        }

        private void fromContainers(Predicate<ItemStack> predicate, ItemStack exact, boolean simulate, int count,
            Map<ItemStack, Integer> result, Transaction transaction) {
            for (Container container : containers) {
                if (count <= 0) return;
                if (player.inventory.mainInventory[container.slot] != container.source
                    || !container.adapter.isValidSource(container.source.copy(), player)) continue;
                final int before = amount(result);
                final Container source = container;
                final ItemStack beforeSource = container.source.copy();
                // MAIN reports actual stack identities as they are extracted, including before a thrown exception.
                InventoryUtility.ItemStackCounter counter = (stack, number) -> {
                    if (number <= 0) return;
                    add(result, stack, number);
                    if (!simulate && transaction != null) transaction.containerItems.add(copy(stack, number));
                };
                try {
                    ItemStack supplied = simulate ? container.source.copy() : container.source;
                    if (container.adapter.isAPIImplemented(APIType.MAIN)) container.adapter.takeFromStack(
                        predicate,
                        simulate,
                        count,
                        counter,
                        supplied,
                        exact == null ? null : copy(exact, count),
                        player);
                    else if (exact != null && container.adapter.isAPIImplemented(APIType.EXTRACT_ONE_STACK)) {
                        ItemStack request = copy(exact, count);
                        int taken = container.adapter.getItem(supplied, request, simulate, player);
                        if (taken > 0) counter.add(exact, taken);
                    }
                } finally {
                    if (!simulate && transaction != null
                        && (amount(result) > before || !ItemStack.areItemStacksEqual(beforeSource, source.source)))
                        transaction.containerStates.put(source.slot, source.source.copy());
                }
                count -= amount(result) - before;
            }
        }

        private boolean terminalAvailable() {
            if (terminal == null) return false;
            if (!java.util.Objects.equals(terminalKey, terminalHandler.getEncryptionKey(terminalStack))
                || terminalInfiniteRange != terminalHandler.hasInfinityRange(terminalStack)
                || terminalInfinitePower != terminalHandler.hasInfinityPower(terminalStack)) return false;
            // A cached GUI object must never keep access after the carried terminal is removed.
            boolean carried = false;
            for (ItemStack stack : player.inventory.mainInventory) if (stack == terminalStack) carried = true;
            if (!carried && Platform.isBaublesLoaded)
                carried = PlayerInventoryUtil.getWirelessTerminalFromBaubles(player) == terminalStack;
            return carried && terminal.rangeCheck() && terminal.getActionableNode() != null;
        }

        private void fromMe(Predicate<ItemStack> predicate, ItemStack exact, boolean simulate, int count,
            Map<ItemStack, Integer> result, Transaction transaction) {
            if (!terminalAvailable()) return;
            IMEMonitor<IAEItemStack> monitor = terminal.getItemInventory();
            if (monitor == null) return;
            List<IAEItemStack> requests = new ArrayList<>();
            if (exact != null) requests.add(AEItemStack.create(exact));
            else for (IAEItemStack entry : monitor.getStorageList())
                if (entry.getStackSize() > 0 && predicate.test(entry.getItemStack())) requests.add(entry.copy());
            PlayerSource action = new PlayerSource(player, terminal);
            for (IAEItemStack request : requests) {
                if (count <= 0 || !terminalAvailable()) break;
                request.setStackSize(count);
                IAEItemStack extracted;
                try {
                    if (simulate)
                        extracted = Platform.poweredExtraction(terminal, monitor, request, action, Actionable.SIMULATE);
                    else {
                        int offered = poweredCount(request);
                        if (offered <= 0) continue;
                        request.setStackSize(offered);
                        extracted = monitor.extractItems(request, Actionable.MODULATE, action);
                        // Record the actual extraction before energy charging, which may throw in an adapter.
                        if (extracted != null && extracted.getStackSize() > 0) {
                            if (transaction != null) transaction.meItems.add(extracted.copy());
                            charge(extracted.getStackSize(), extracted.getAmountPerUnit());
                            Stats.ItemsExtracted.addToPlayer(player, (int) extracted.getStackSize());
                        }
                    }
                } finally {
                    if (!simulate && transaction != null && terminalSlot >= 0) {
                        transaction.terminalSlot = terminalSlot;
                        transaction.terminalState = terminalStack.copy();
                    }
                }
                if (extracted == null || extracted.getStackSize() <= 0) continue;
                int taken = (int) extracted.getStackSize();
                add(result, extracted.getItemStack(), taken);
                count -= taken;
            }
        }

        private int poweredCount(IAEItemStack stack) {
            double available = terminal.extractAEPower(
                Platform.ceilDiv(stack.getStackSize(), stack.getAmountPerUnit()),
                Actionable.SIMULATE,
                PowerMultiplier.CONFIG);
            return (int) Math.min(stack.getStackSize(), (long) (available * stack.getAmountPerUnit() + 0.9));
        }

        private void charge(long items, int amountPerUnit) {
            terminal
                .extractAEPower(Platform.ceilDiv(items, amountPerUnit), Actionable.MODULATE, PowerMultiplier.CONFIG);
        }

        /** Update the known remainder before charging so later failures cannot refund already inserted items. */
        private void returnToMe(ItemStack stack) {
            if (!settings.me || !terminalAvailable()) return;
            IMEMonitor<IAEItemStack> monitor = terminal.getItemInventory();
            if (monitor == null) return;
            IAEItemStack offered = AEItemStack.create(stack);
            int count = poweredCount(offered);
            if (count <= 0) return;
            offered.setStackSize(count);
            IAEItemStack left = monitor.injectItems(offered, Actionable.MODULATE, new PlayerSource(player, terminal));
            int previous = stack.stackSize;
            stack.stackSize = recoveryRemainder(previous, count, left == null ? 0 : left.getStackSize());
            int inserted = previous - stack.stackSize;
            if (inserted > 0) {
                charge(inserted, offered.getAmountPerUnit());
                Stats.ItemsInserted.addToPlayer(player, inserted);
            }
        }

        /** Must follow the service's main inventory snapshot restore, before returning fallback materials. */
        public void afterInventoryRestore() {
            for (Map.Entry<Integer, ItemStack> entry : retainedContainers.entrySet()) {
                ItemStack restored = entry.getValue()
                    .copy();
                player.inventory.mainInventory[entry.getKey()] = restored;
                for (Container container : containers)
                    if (container.slot == entry.getKey()) container.source = restored;
            }
            retainedContainers.clear();
            for (Container container : containers) {
                ItemStack current = player.inventory.mainInventory[container.slot];
                if (current != null && current.isItemEqual(container.source)
                    && ItemStack.areItemStackTagsEqual(current, container.source)) container.source = current;
            }
            refreshTerminal();
            flushRecovery();
        }

        /** Enqueue only committed world drops. Delivery must never trigger a world/inventory rollback. */
        public void recoverWorld(List<ItemStack> stacks) {
            for (ItemStack stack : stacks) if (stack != null) recover(stack.copy(), settings.recoveryDestination == 1);
        }

        /** Persisted real items remain here until a configured destination has room; no world drops are needed. */
        public void flushRecovery() {
            flushResourceRecovery();
            NBTTagCompound owner = recoveryOwner();
            NBTTagList stored = owner.getTagList(RECOVERY, 10);
            NBTTagList remaining = new NBTTagList();
            long deadline = System.nanoTime() + 10_000_000L;
            for (int i = 0; i < stored.tagCount(); i++) {
                if (i >= 96 || System.nanoTime() > deadline) {
                    remaining.appendTag(
                        stored.getCompoundTagAt(i)
                            .copy());
                    continue;
                }
                ItemStack stack = recoveryItem(stored.getCompoundTagAt(i));
                if (stack == null || stack.stackSize <= 0) continue;
                boolean meFirst = stored.getCompoundTagAt(i)
                    .getBoolean(RECOVERY_ME);
                if (meFirst) {
                    try {
                        returnToMe(stack);
                    } catch (RuntimeException | LinkageError failure) {
                        LOG.warn("Hologram ME recovery failed; preserving known remainder", failure);
                    }
                }
                HologramMaterials.insertRecovery(
                    player.inventory.mainInventory,
                    player.inventory.currentItem,
                    player.inventory.getInventoryStackLimit(),
                    stack);
                // Persist this receipt immediately, before attempting the next queue entry.
                stored.getCompoundTagAt(i)
                    .setInteger(RECOVERY_COUNT, stack.stackSize);
                if (stack.stackSize > 0) {
                    NBTTagCompound entry = itemReceipt(stack);
                    entry.setBoolean(RECOVERY_ME, meFirst);
                    remaining.appendTag(entry);
                }
            }
            owner.setTag(RECOVERY, remaining);
            player.inventory.markDirty();
            if (player.inventoryContainer != null) player.inventoryContainer.detectAndSendChanges();
        }

        private void recover(ItemStack stack) {
            recover(stack, false);
        }

        private void recover(ItemStack stack, boolean meFirst) {
            if (stack == null || stack.stackSize <= 0) return;
            NBTTagCompound owner = recoveryOwner();
            NBTTagList stored = owner.getTagList(RECOVERY, 10);
            NBTTagCompound entry = itemReceipt(stack);
            entry.setBoolean(RECOVERY_ME, meFirst);
            stored.appendTag(entry);
            stack.stackSize = 0;
            owner.setTag(RECOVERY, stored);
        }

        private NBTTagCompound recoveryOwner() {
            NBTTagCompound data = player.getEntityData();
            if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10))
                data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
            return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        }
    }

    public static final class Transaction implements IItemSource {

        private final Context context;
        private final List<ItemStack> containerItems = new ArrayList<>();
        private final List<IAEItemStack> meItems = new ArrayList<>();
        private final List<IAEFluidStack> meFluids = new ArrayList<>();
        private BigInteger energyTaken = BigInteger.ZERO;
        private final Map<Integer, ItemStack> containerStates = new LinkedHashMap<>();
        private int terminalSlot = -1;
        private ItemStack terminalState;
        private boolean closed;

        private Transaction(Context context) {
            this.context = context;
        }

        @Override
        public Map<ItemStack, Integer> take(Predicate<ItemStack> predicate, boolean simulate, int count) {
            return closed ? Collections.emptyMap() : context.take(predicate, null, simulate, count, this);
        }

        @Override
        public boolean takeOne(ItemStack stack, boolean simulate) {
            requireOne(stack);
            return !closed && context.exact(stack, simulate, this);
        }

        @Override
        public boolean takeAll(ItemStack stack, boolean simulate) {
            return !closed && context.exact(stack, simulate, this);
        }

        public boolean takeFluid(FluidStack stack, long amount, boolean simulate) {
            return !closed
                && (simulate ? context.fluidCount(stack, amount) >= amount : context.takeFluid(stack, amount, this));
        }

        public boolean takeEnergy(BigInteger amount, boolean simulate) {
            if (closed || amount == null || amount.signum() < 0) return false;
            if (amount.signum() == 0) return true;
            if (simulate) return context.energyAvailable()
                .compareTo(amount) >= 0;
            if (!WirelessNetworkManager.addEUToGlobalEnergyMap(context.player.getUniqueID(), amount.negate()))
                return false;
            energyTaken = energyTaken.add(amount);
            return true;
        }

        public void commit() {
            closed = true;
            containerItems.clear();
            containerStates.clear();
            meItems.clear();
            meFluids.clear();
            energyTaken = BigInteger.ZERO;
            terminalState = null;
        }

        /** Main items are restored by the service snapshot; only external deductions are handled here. */
        public void rollbackExternal() {
            if (closed) return;
            closed = true;
            context.retainedContainers.putAll(containerStates);
            if (energyTaken.signum() > 0) {
                try {
                    if (!WirelessNetworkManager.addEUToGlobalEnergyMap(context.player.getUniqueID(), energyTaken))
                        context.recoverEnergy(energyTaken);
                } catch (RuntimeException | LinkageError failure) {
                    context.recoverEnergy(energyTaken);
                    LOG.warn("Hologram wireless energy refund queued", failure);
                }
            }
            for (IAEFluidStack stack : meFluids) {
                IAEFluidStack left = stack.copy();
                try {
                    context.returnFluid(left);
                } catch (RuntimeException | LinkageError failure) {
                    LOG.warn("Hologram AE fluid refund queued", failure);
                } finally {
                    context.recoverFluid(left);
                }
            }
            // SL's generic extractor has no insertion API. Keep its actual post-extraction state and
            // return its actual reported items to the player, rather than guessing its storage NBT.
            for (ItemStack stack : containerItems) context.recover(stack.copy());
            for (IAEItemStack stack : meItems) {
                ItemStack left = stack.getItemStack();
                try {
                    context.returnToMe(left);
                } catch (RuntimeException | LinkageError failure) {
                    // A broken third-party insertion must not discard later ledger entries.
                    LOG.warn(
                        "Hologram ME material return failed; preserving remaining ledger in player recovery",
                        failure);
                } finally {
                    if (left != null && left.stackSize > 0) context.recover(left);
                }
            }
            if (terminalState != null && terminalSlot >= 0) {
                // Main snapshots must not undo extraction/refund energy already charged by AE.
                ItemStack latest = context.player.inventory.mainInventory[terminalSlot] == context.terminalStack
                    ? context.terminalStack.copy()
                    : terminalState;
                context.retainedContainers.put(terminalSlot, latest);
            }
            containerItems.clear();
            containerStates.clear();
            meItems.clear();
            meFluids.clear();
            energyTaken = BigInteger.ZERO;
            terminalState = null;
        }
    }

    private static final class Container {

        final int slot;
        ItemStack source;
        final ItemStackExtractor adapter;

        Container(int slot, ItemStack source, ItemStackExtractor adapter) {
            this.slot = slot;
            this.source = source;
            this.adapter = adapter;
        }
    }

    private static int amount(Map<ItemStack, Integer> stacks) {
        int total = 0;
        for (int count : stacks.values()) total += count;
        return total;
    }

    /** AE returns only the unaccepted portion of the offered amount; unoffered items remain ours. */
    static int recoveryRemainder(int requested, int offered, long unaccepted) {
        int bounded = Math.min(Math.max(0, requested), Math.max(0, offered));
        return Math.max(0, requested) - bounded + (int) Math.min(bounded, Math.max(0, unaccepted));
    }

    static long fluidRecoveryRemainder(long requested, long offered, long unaccepted) {
        long bounded = Math.min(Math.max(0, requested), Math.max(0, offered));
        return Math.max(0, requested) - bounded + Math.min(bounded, Math.max(0, unaccepted));
    }

    /** One implementation is used by the authenticated production terminal and bounded regression fixtures. */
    interface FluidAccess {

        boolean available();

        long poweredCount(IAEFluidStack stack);

        IAEFluidStack extract(IAEFluidStack stack, Actionable mode);

        IAEFluidStack inject(IAEFluidStack stack);

        void charge(long amount, int unit);
    }

    static long simulateFluid(FluidAccess access, FluidStack stack, long amount) {
        if (stack == null || stack.getFluid() == null || amount <= 0 || !access.available() || !supportsFluidNbt(stack))
            return 0;
        IAEFluidStack request = AEFluidStack.create(stack);
        request.setStackSize(amount);
        long powered = Math.max(0, Math.min(amount, access.poweredCount(request)));
        if (powered == 0) return 0;
        request.setStackSize(powered);
        IAEFluidStack result = access.extract(request, Actionable.SIMULATE);
        return result == null ? 0 : Math.max(0, Math.min(amount, result.getStackSize()));
    }

    static boolean reserveFluid(FluidAccess access, FluidStack stack, long amount, Consumer<IAEFluidStack> ledger) {
        if (amount == 0) return true;
        if (amount < 0 || simulateFluid(access, stack, amount) < amount || !access.available()) return false;
        IAEFluidStack request = AEFluidStack.create(stack);
        request.setStackSize(amount);
        long offered = Math.max(0, Math.min(amount, access.poweredCount(request)));
        if (offered == 0) return false;
        request.setStackSize(offered);
        IAEFluidStack taken = access.extract(request, Actionable.MODULATE);
        if (taken == null || taken.getStackSize() <= 0) return false;
        ledger.accept(taken.copy());
        access.charge(taken.getStackSize(), taken.getAmountPerUnit());
        return taken.getStackSize() == amount;
    }

    static void refundFluid(FluidAccess access, IAEFluidStack stack) {
        if (stack == null || stack.getStackSize() <= 0 || !access.available()) return;
        long previous = stack.getStackSize();
        long offered = Math.max(0, Math.min(previous, access.poweredCount(stack)));
        if (offered == 0) return;
        IAEFluidStack request = stack.copy();
        request.setStackSize(offered);
        IAEFluidStack left = access.inject(request);
        stack.setStackSize(fluidRecoveryRemainder(previous, offered, left == null ? 0 : left.getStackSize()));
        if (previous > stack.getStackSize()) access.charge(previous - stack.getStackSize(), stack.getAmountPerUnit());
    }

    static NBTTagCompound fluidReceipt(IAEFluidStack stack) {
        NBTTagCompound entry = stack.getFluidStack()
            .writeToNBT(new NBTTagCompound());
        entry.setLong("gtitRecoveryAmount", stack.getStackSize());
        return entry;
    }

    static boolean supportsFluidNbt(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) return false;
        if (stack.tag == null || stack.tag.hasNoTags()) return true;
        IAEFluidStack converted = AEFluidStack.create(stack);
        // The bound AE2 version discards fluid tags. Probe the actual adapter so a future implementation
        // that preserves exact tags can be used, while today's tagged fluid is never replaced by plain fluid.
        return converted != null && converted.hasTagCompound()
            && converted.getTagCompound() != null
            && stack.tag.equals(
                converted.getTagCompound()
                    .getNBTTagCompoundCopy());
    }

    static NBTTagCompound itemReceipt(ItemStack stack) {
        NBTTagCompound entry = stack.writeToNBT(new NBTTagCompound());
        entry.setInteger(RECOVERY_COUNT, stack.stackSize);
        return entry;
    }

    static ItemStack recoveryItem(NBTTagCompound receipt) {
        ItemStack stack = ItemStack.loadItemStackFromNBT(receipt);
        if (stack != null && receipt.hasKey(RECOVERY_COUNT, 3)) stack.stackSize = receipt.getInteger(RECOVERY_COUNT);
        return stack;
    }

    private static ItemStack copy(ItemStack stack, int count) {
        ItemStack copied = stack.copy();
        copied.stackSize = count;
        return copied;
    }

    private static void add(Map<ItemStack, Integer> result, ItemStack stack, int count) {
        result.put(copy(stack, count), count);
    }

    /** Display only: a controller's own inventory never becomes a construction material source. */
    public static int controllerCount(IInventory controller, ItemStack stack, int limit) {
        if (controller == null || stack == null || stack.getItem() == null || limit <= 0) return 0;
        int maximum = limit;
        int total = 0;
        for (int slot = 0; slot < controller.getSizeInventory() && total < maximum; slot++) {
            if (controller instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity) {
                gregtech.api.interfaces.metatileentity.IMetaTileEntity machine = ((gregtech.api.interfaces.tileentity.IGregTechTileEntity) controller)
                    .getMetaTileEntity();
                if (machine == null || !machine.isValidSlot(slot)) continue;
            }
            total += matchingCount(controller.getStackInSlot(slot), stack, maximum - total);
        }
        return total;
    }

    static int mainCount(ItemStack[] inventory, int heldSlot, ItemStack stack, int limit) {
        if (inventory == null || stack == null || stack.getItem() == null || limit <= 0) return 0;
        int maximum = limit;
        int total = 0;
        for (int slot = 0; slot < inventory.length && total < maximum; slot++) {
            if (slot == heldSlot) continue;
            total += matchingCount(inventory[slot], stack, maximum - total);
        }
        return total;
    }

    private static int matchingCount(ItemStack candidate, ItemStack stack, int remaining) {
        return candidate != null && candidate.stackSize > 0
            && candidate.isItemEqual(stack)
            && ItemStack.areItemStackTagsEqual(candidate, stack) ? Math.min(remaining, candidate.stackSize) : 0;
    }

    static void insertRecovery(ItemStack[] inventory, int heldSlot, int inventoryLimit, ItemStack stack) {
        // InventoryPlayer's creative overflow path discards leftovers. Refunds must retain them.
        for (int slot = 0; slot < inventory.length && stack.stackSize > 0; slot++) {
            if (slot == heldSlot) continue;
            ItemStack target = inventory[slot];
            if (target == null || !target.isStackable()
                || !target.isItemEqual(stack)
                || !ItemStack.areItemStackTagsEqual(target, stack)) continue;
            int space = Math.min(target.getMaxStackSize(), inventoryLimit) - target.stackSize;
            int moved = Math.min(stack.stackSize, Math.max(0, space));
            target.stackSize += moved;
            stack.stackSize -= moved;
        }
        for (int slot = 0; slot < inventory.length && stack.stackSize > 0; slot++) {
            if (slot == heldSlot || inventory[slot] != null) continue;
            int moved = Math.min(stack.stackSize, Math.min(Math.max(1, stack.getMaxStackSize()), inventoryLimit));
            inventory[slot] = copy(stack, moved);
            stack.stackSize -= moved;
        }
    }

    private static void requireOne(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize != 1) throw new IllegalArgumentException();
    }

    private static boolean placeable(ItemStack stack) {
        return stack != null && stack.stackSize > 0
            && (stack.getItem() instanceof ItemBlock || stack.getItem() instanceof ItemMachines);
    }

    private static boolean contains(List<ItemStack> items, ItemStack stack) {
        for (ItemStack existing : items)
            if (existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)) return true;
        return false;
    }

    private static void addChoice(List<ItemStack> items, ItemStack stack) {
        if (placeable(stack) && !contains(items, stack)) items.add(copy(stack, 1));
    }
}
