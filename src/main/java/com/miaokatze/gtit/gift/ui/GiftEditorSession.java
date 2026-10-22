package com.miaokatze.gtit.gift.ui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.miaokatze.gtit.config.DefaultContentConfig;
import com.miaokatze.gtit.config.GiftConfig.ItemEntry;
import com.miaokatze.gtit.gift.GiftDefinition;
import com.miaokatze.gtit.gift.GiftIconCatalog;
import com.miaokatze.gtit.gift.GiftNbt;
import com.miaokatze.gtit.gift.GiftRegistry;
import com.miaokatze.gtit.gift.GiftService;
import com.miaokatze.gtit.gift.GiftShellCatalog;

/** Per-window draft. Nothing here is persisted until the explicit save action. */
final class GiftEditorSession {

    static final int PAGE_SIZE = 4;
    static final int MAX_ITEMS = 128;
    static final int MAX_NBT_TEXT = 16384;
    static final int MAX_TEXT_BYTES = Short.MAX_VALUE - 74;
    static final String PRESERVED_NBT = "[NBT cannot be edited as text; original retained. Replace item or clear row.]";
    final EntityPlayer player;
    final ItemEntry[] unresolved = new ItemEntry[1 + MAX_ITEMS * 2];
    final ItemStackHandler items = new ItemStackHandler(1 + MAX_ITEMS * 2) {

        @Override
        public int getSlotLimit(int slot) {
            return 4096;
        }
    };
    final ItemStackHandler visibleItems = new ItemStackHandler(9) {

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(mapped(slot));
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (stack != null) {
                if (stack.stackSize < 1 || stack.stackSize > 4096)
                    throw new IllegalArgumentException("Amount must be 1..4096");
                if (stack.getItemDamage() < 0 || stack.getItemDamage() > 32767)
                    throw new IllegalArgumentException("Invalid gift item metadata");
                GiftNbt.encode(stack.getTagCompound());
            }
            items.setStackInSlot(mapped(slot), stack == null ? null : stack.copy());
            unresolved[mapped(slot)] = null;
            dirty = true;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 4096;
        }
    };
    GiftDefinition draft = new GiftDefinition();
    List<GiftDefinition> definitions = new ArrayList<>();
    int listPage;
    int requiredPage;
    int randomPage;
    int previewPage;
    String selectedId = "";
    String message = "";
    String updatePrompt = "";
    boolean defaultsEnabled;
    long registryRevision;
    long refreshedAt = Long.MIN_VALUE;
    long expectedRevision;
    volatile long editGeneration;
    boolean creating = true;
    boolean editing;
    boolean dirty;
    boolean leavingWindow;
    boolean closeRequested;
    String deleteTargetId = "";
    String deleteTargetName = "";
    long deleteTargetRevision;
    long deleteTargetGeneration;
    long defaultUpdateRevision = -1;

    GiftEditorSession(EntityPlayer player) {
        this.player = player;
        draft.id = "";
        draft.name = "";
    }

    boolean admin() {
        return player instanceof EntityPlayerMP && GiftAccessHandler.authorized((EntityPlayerMP) player);
    }

    int mapped(int slot) {
        if (slot == 0) return 0;
        if (slot <= PAGE_SIZE) return 1 + requiredPage * PAGE_SIZE + slot - 1;
        return 1 + MAX_ITEMS + randomPage * PAGE_SIZE + slot - 1 - PAGE_SIZE;
    }

    void refresh() {
        if (!(player instanceof EntityPlayerMP)) return;
        long now = player.worldObj.getTotalWorldTime();
        if (now == refreshedAt || now - refreshedAt >= 0 && now - refreshedAt < 20) return;
        refreshedAt = now;
        GiftRegistry.Snapshot snapshot = GiftRegistry.INSTANCE.snapshot();
        definitions = snapshot.definitions;
        registryRevision = snapshot.revision;
        defaultsEnabled = snapshot.defaultsEnabled;
        updatePrompt = snapshot.updatePrompt == null ? "" : snapshot.updatePrompt;
        listPage = Math.max(0, Math.min(listPage, Math.max(0, (definitions.size() - 1) / 6)));
    }

    GiftDefinition row(int index) {
        refresh();
        int i = listPage * 6 + index;
        return i < definitions.size() ? definitions.get(i) : null;
    }

    GiftDefinition selected() {
        refresh();
        for (GiftDefinition definition : definitions) {
            if (definition.id.equals(selectedId)) return definition;
        }
        return null;
    }

    String status(GiftDefinition definition) {
        return definition == null || !(player instanceof EntityPlayerMP) ? ""
            : GiftService.INSTANCE.status((EntityPlayerMP) player, definition.id)
                .name();
    }

    void select(GiftDefinition definition) {
        if (definition == null) return;
        selectedId = definition.id;
        previewPage = 0;
        load(GiftRegistry.INSTANCE.get(definition.id));
    }

    void create() {
        GiftDefinition definition = new GiftDefinition();
        definition.id = "";
        definition.name = "";
        definition.enabled = true;
        definition.shellKey = "starter";
        definition.icon = null;
        load(definition);
        selectedId = "";
    }

    String shellKey() {
        return GiftShellCatalog.effectiveKey(draft);
    }

    ItemStack shellStack() {
        return GiftShellCatalog.stack(shellKey());
    }

    void bindShell(String key) {
        requireAdmin();
        if (!GiftIconCatalog.contains(key)) throw new IllegalArgumentException("Unknown gift shell");
        draft.shellKey = key;
        draft.iconKey = key;
        dirty = true;
    }

    private void load(GiftDefinition definition) {
        editGeneration++;
        draft = definition.copy();
        expectedRevision = definition.revision;
        creating = definition.id == null || definition.id.isEmpty();
        requiredPage = 0;
        randomPage = 0;
        for (int i = 0; i < items.getSlots(); i++) {
            items.setStackInSlot(i, null);
            unresolved[i] = null;
        }
        if (definition.icon != null) {
            items.setStackInSlot(0, definition.icon.toItemStack());
            if (items.getStackInSlot(0) == null) unresolved[0] = definition.icon;
        }
        loadEntries(definition.rewards, 1);
        loadEntries(definition.randomRewards, 1 + MAX_ITEMS);
        message = "";
        dirty = false;
    }

    void discard() {
        GiftDefinition saved = selectedId.isEmpty() ? null : GiftRegistry.INSTANCE.get(selectedId);
        if (saved == null) create();
        else load(saved);
        editing = false;
    }

    static boolean canClaim(GiftDefinition definition, String status) {
        return definition != null && definition.trigger == GiftDefinition.Trigger.BQ_UNLOCK
            && ("AVAILABLE".equals(status) || "PENDING".equals(status));
    }

    boolean prepareDefaultUpdate() {
        requireAdmin();
        invalidate();
        if (!defaultsEnabled || !DefaultContentConfig.loadDefaultGifts() || !"ASK".equals(updatePrompt)) return false;
        defaultUpdateRevision = registryRevision;
        return true;
    }

    void resolveDefaultUpdate(boolean accept) {
        requireAdmin();
        if (defaultUpdateRevision < 0) throw new IllegalStateException("gtit.gift.ui.update_changed");
        GiftRegistry.INSTANCE.resolveDefaultUpdate(accept ? "APPLY" : "SKIP", defaultUpdateRevision);
        defaultUpdateRevision = -1;
        invalidate();
    }

    private void loadEntries(List<ItemEntry> entries, int offset) {
        if (entries == null) return;
        for (int i = 0; i < Math.min(MAX_ITEMS, entries.size()); i++) {
            items.setStackInSlot(
                offset + i,
                entries.get(i)
                    .toItemStack());
            if (items.getStackInSlot(offset + i) == null) unresolved[offset + i] = entries.get(i);
        }
    }

    void save() {
        requireAdmin();
        draft.icon = entryOrUnresolved(0);
        draft.rewards = entries(1);
        draft.randomRewards = entries(1 + MAX_ITEMS);
        GiftDefinition saved = GiftRegistry.INSTANCE.save(draft, expectedRevision);
        selectedId = saved.id;
        load(saved);
        invalidate();
    }

    void prepareDelete() {
        requireAdmin();
        if (creating) throw new IllegalStateException("Select a saved gift first");
        deleteTargetId = draft.id;
        deleteTargetName = draft.name;
        deleteTargetRevision = expectedRevision;
        deleteTargetGeneration = editGeneration;
    }

    void confirmDelete() {
        requireAdmin();
        if (deleteTargetId.isEmpty() || !deleteTargetId.equals(draft.id)
            || deleteTargetRevision != expectedRevision
            || deleteTargetGeneration != editGeneration) {
            throw new IllegalStateException("Gift selection changed; open the confirmation again");
        }
        delete();
        deleteTargetId = "";
    }

    void delete() {
        requireAdmin();
        if (creating) throw new IllegalStateException("Select a saved gift first");
        GiftRegistry.INSTANCE.delete(draft.id, expectedRevision);
        create();
        editing = false;
        invalidate();
    }

    void requireAdmin() {
        if (!admin())
            throw new IllegalStateException("OP level " + DefaultContentConfig.managementOpLevel() + " required");
    }

    void invalidate() {
        refreshedAt = Long.MIN_VALUE;
        refresh();
    }

    private List<ItemEntry> entries(int offset) {
        List<ItemEntry> entries = new ArrayList<>();
        for (int i = 0; i < MAX_ITEMS; i++) {
            ItemEntry entry = entryOrUnresolved(offset + i);
            if (entry != null) entries.add(entry);
        }
        return entries;
    }

    private static ItemEntry entry(ItemStack stack) {
        if (stack == null) return null;
        return new ItemEntry(
            (String) net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()),
            stack.stackSize,
            stack.getItemDamage(),
            stack.getTagCompound());
    }

    private ItemEntry entryOrUnresolved(int slot) {
        ItemEntry entry = entry(items.getStackInSlot(slot));
        return entry == null ? unresolved[slot] : entry;
    }

    void clear(int slot) {
        requireAdmin();
        int index = mapped(slot);
        items.setStackInSlot(index, null);
        unresolved[index] = null;
        dirty = true;
    }

    String amount(int slot) {
        ItemStack stack = visibleItems.getStackInSlot(slot);
        return stack == null ? "1" : Integer.toString(stack.stackSize);
    }

    String itemDescription(int slot) {
        int index = mapped(slot);
        ItemStack stack = items.getStackInSlot(index);
        if (stack != null)
            return net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()) + ":" + stack.getItemDamage();
        ItemEntry entry = unresolved[index];
        return entry == null ? "" : "[missing] " + entry.itemId + ":" + entry.meta;
    }

    void amount(int slot, String text) {
        requireAdmin();
        int amount;
        try {
            amount = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Amount must be 1..4096");
        }
        if (amount < 1 || amount > 4096) throw new IllegalArgumentException("Amount must be 1..4096");
        ItemStack stack = visibleItems.getStackInSlot(slot);
        if (stack != null) {
            stack = stack.copy();
            stack.stackSize = amount;
            visibleItems.setStackInSlot(slot, stack);
        }
    }

    String nbt(int slot) {
        ItemStack stack = visibleItems.getStackInSlot(slot);
        if (stack == null || stack.getTagCompound() == null) return "";
        NBTTagCompound tag = stack.getTagCompound();
        String text = tag.toString();
        if (!editableText(text)) return PRESERVED_NBT;
        try {
            // Vanilla's toString is lossy for byte arrays and some strings; never offer that as editable NBT.
            return tag.equals(JsonToNBT.func_150315_a(text)) ? text : PRESERVED_NBT;
        } catch (Exception e) {
            return PRESERVED_NBT;
        }
    }

    void nbt(int slot, String text) {
        requireAdmin();
        if (!editableText(text))
            throw new IllegalArgumentException("NBT text exceeds editor limits; original retained");
        ItemStack stack = visibleItems.getStackInSlot(slot);
        if (stack == null) return;
        String current = nbt(slot);
        if (current.equals(text)) return;
        if (PRESERVED_NBT.equals(current)) throw new IllegalArgumentException(PRESERVED_NBT);
        try {
            checkTextDepth(text);
            NBTTagCompound tag = text.trim()
                .isEmpty() ? null : (NBTTagCompound) JsonToNBT.func_150315_a(text);
            GiftNbt.encode(tag);
            stack = stack.copy();
            stack.setTagCompound(tag);
            visibleItems.setStackInSlot(slot, stack);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or oversized compound NBT; original retained", e);
        }
    }

    static boolean editableText(String text) {
        return text != null && text.length() <= MAX_NBT_TEXT
            && text.getBytes(StandardCharsets.UTF_8).length <= MAX_TEXT_BYTES;
    }

    private static void checkTextDepth(String text) {
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
            } else if (ch == '"') quoted = true;
            else if (ch == '{' || ch == '[') {
                if (++depth > 32) throw new IllegalArgumentException("NBT nesting limit exceeded");
            } else if (ch == '}' || ch == ']') depth--;
        }
    }
}
