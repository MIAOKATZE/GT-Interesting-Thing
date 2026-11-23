// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.infinitycell;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

/** Anchors the native AE payload to the world registry's stable item name. */
final class InfinityItemIdentity {

    private static final String VERSION = "entry_version";
    private static final String NAME = "registry_name";
    private static final String PAYLOAD = "ae_payload";
    private static final String LEGACY = "legacy_payload";

    private InfinityItemIdentity() {}

    static NBTTagCompound write(IAEItemStack stack, NBTTagCompound payload) {
        final Item item = stack.getItem();
        final String name = Item.itemRegistry.getNameForObject(item);
        if (name == null || name.isEmpty() || Item.itemRegistry.getObject(name) != item) {
            throw new IllegalStateException("Cannot resolve a registered name for an infinity cell item");
        }
        // AE2 writes a short ID itself. Use the runtime's vanilla/EndlessIDs writer for identity fields.
        applyCurrentId(item, payload);
        final NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(VERSION, 1);
        entry.setString(NAME, name);
        entry.setTag(PAYLOAD, payload);
        return entry;
    }

    static IAEItemStack read(NBTTagCompound raw, boolean legacyInput) {
        final NBTTagCompound payload;
        if (legacyInput) {
            payload = (NBTTagCompound) raw.copy();
        } else {
            if (!raw.hasKey(VERSION, 3) || raw.getInteger(VERSION) != 1) return null;
            if (raw.hasKey(LEGACY)) {
                if (!raw.hasKey(LEGACY, 10) || raw.hasKey(NAME) || raw.hasKey(PAYLOAD)) return null;
                payload = (NBTTagCompound) raw.getCompoundTag(LEGACY)
                    .copy();
            } else {
                if (!raw.hasKey(NAME, 8) || !raw.hasKey(PAYLOAD, 10)) return null;
                final String name = raw.getString(NAME);
                if (name.isEmpty() || !Item.itemRegistry.containsKey(name)
                    || !(Item.itemRegistry.getObject(name) instanceof Item item)
                    || !name.equals(Item.itemRegistry.getNameForObject(item))) return null;
                payload = (NBTTagCompound) raw.getCompoundTag(PAYLOAD)
                    .copy();
                applyCurrentId(item, payload);
            }
        }
        if (!payload.hasKey("Damage", 2) || !payload.hasKey("Cnt", 4) || id(payload) < 0) return null;
        final IAEItemStack stack = AEItemStack.loadItemStackFromNBT(payload);
        // Postea may intentionally rewrite the private payload in ItemStack.readFromNBT.
        // Require the result to match that explicit identity, including after any native migration.
        return stack != null && id(payload) == Item.getIdFromItem(stack.getItem()) ? stack : null;
    }

    static NBTTagCompound preserveLegacy(NBTTagCompound raw) {
        final NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(VERSION, 1);
        entry.setTag(LEGACY, raw.copy());
        return entry;
    }

    private static void applyCurrentId(Item item, NBTTagCompound payload) {
        final NBTTagCompound identity = new ItemStack(item).writeToNBT(new NBTTagCompound());
        final int numericId = Item.getIdFromItem(item);
        if (numericId < 0 || id(identity) != numericId || Item.getItemById(numericId) != item) {
            throw new IllegalStateException("Native codec cannot represent infinity cell item ID " + numericId);
        }
        payload.removeTag("idExt");
        payload.setTag("id", identity.getTag("id"));
        if (identity.hasKey("idExt", 3)) payload.setTag("idExt", identity.getTag("idExt"));
    }

    private static int id(NBTTagCompound tag) {
        if (!tag.hasKey("id", 2) && !tag.hasKey("id", 3)) return -1;
        if (tag.hasKey("idExt")) return tag.hasKey("idExt", 3) ? tag.getInteger("idExt") : -1;
        return tag.getInteger("id");
    }
}
