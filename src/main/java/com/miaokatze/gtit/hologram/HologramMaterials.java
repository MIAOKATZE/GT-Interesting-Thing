package com.miaokatze.gtit.hologram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.util.InventoryUtility;
import com.gtnewhorizon.structurelib.util.InventoryUtility.ItemStackExtractor;
import com.gtnewhorizon.structurelib.util.InventoryUtility.ItemStackExtractor.APIType;
import com.gtnewhorizon.structurelib.util.ItemStackPredicate;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;
import appeng.util.PlayerInventoryUtil;
import appeng.util.item.AEItemStack;
import gregtech.common.blocks.ItemMachines;

/** One batch's native container adapters and authenticated wireless terminal material source. */
public final class HologramMaterials {

    private static final String SETTINGS = "gtitHologramMaterials";
    private static final String RECOVERY = "gtitHologramMaterialRecovery";
    private static final Logger LOG = LogManager.getLogger("gtit");

    private HologramMaterials() {}

    public static final class Settings {

        public boolean main = true, containers = true, me = true;
        public int priority;
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
                take(
                    ItemStackPredicate.from(stack, ItemStackPredicate.NBTMode.EXACT),
                    stack,
                    true,
                    Math.min(4096, limit),
                    null));
        }

        /** Actual main inventory stock, even when that material source is disabled. Excludes the held tool slot. */
        public int mainCount(ItemStack stack, int limit) {
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
            if (predicate == null || count < 1 || count > 4096) return result;
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
                    extracted = Platform.poweredExtraction(
                        terminal,
                        monitor,
                        request,
                        action,
                        simulate ? Actionable.SIMULATE : Actionable.MODULATE);
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
                if (!simulate && transaction != null) transaction.meItems.add(extracted.copy());
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

        /** Persisted real items remain here until inventory space is available; no world drops are needed. */
        public void flushRecovery() {
            NBTTagCompound owner = recoveryOwner();
            NBTTagList stored = owner.getTagList(RECOVERY, 10);
            NBTTagList remaining = new NBTTagList();
            for (int i = 0; i < stored.tagCount(); i++) {
                ItemStack stack = ItemStack.loadItemStackFromNBT(stored.getCompoundTagAt(i));
                if (stack == null || stack.stackSize <= 0) continue;
                HologramMaterials.insertRecovery(
                    player.inventory.mainInventory,
                    player.inventory.currentItem,
                    player.inventory.getInventoryStackLimit(),
                    stack);
                if (stack.stackSize > 0) remaining.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
            owner.setTag(RECOVERY, remaining);
            player.inventory.markDirty();
            if (player.inventoryContainer != null) player.inventoryContainer.detectAndSendChanges();
        }

        private void recover(ItemStack stack) {
            if (stack == null || stack.stackSize <= 0) return;
            NBTTagCompound owner = recoveryOwner();
            NBTTagList stored = owner.getTagList(RECOVERY, 10);
            while (stack.stackSize > 0) {
                int size = Math.min(stack.stackSize, Math.max(1, stack.getMaxStackSize()));
                stored.appendTag(copy(stack, size).writeToNBT(new NBTTagCompound()));
                stack.stackSize -= size;
            }
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

        public void commit() {
            closed = true;
            containerItems.clear();
            containerStates.clear();
            meItems.clear();
            terminalState = null;
        }

        /** Main items are restored by the service snapshot; only external deductions are handled here. */
        public void rollbackExternal() {
            if (closed) return;
            closed = true;
            context.retainedContainers.putAll(containerStates);
            // SL's generic extractor has no insertion API. Keep its actual post-extraction state and
            // return its actual reported items to the player, rather than guessing its storage NBT.
            for (ItemStack stack : containerItems) context.recover(stack.copy());
            for (IAEItemStack stack : meItems) {
                IAEItemStack left = stack.copy();
                try {
                    if (context.terminalAvailable() && context.terminal.getItemInventory() != null)
                        left = Platform.poweredInsert(
                            context.terminal,
                            context.terminal.getItemInventory(),
                            left,
                            new PlayerSource(context.player, context.terminal));
                } catch (RuntimeException | LinkageError failure) {
                    // A broken third-party insertion must not discard later ledger entries.
                    LOG.warn(
                        "Hologram ME material return failed; preserving remaining ledger in player recovery",
                        failure);
                } finally {
                    if (left != null && left.getStackSize() > 0) context.recover(left.getItemStack());
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
        int maximum = Math.min(4096, limit);
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
        int maximum = Math.min(4096, limit);
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
