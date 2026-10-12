package com.miaokatze.gtit.hologram;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import net.minecraft.item.Item;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

/** Bounded binary NBT transport and detached ownership rewriting for shared blueprints. */
final class HologramBlueprintNbt {

    static final int MAX_COMPRESSED = 1024 * 1024;
    static final int MAX_EXPANDED = 4 * 1024 * 1024;
    static final String ITEM_REGISTRY = "gtitBlueprintItemRegistry";
    static final String MISSING_ITEM = "gtitBlueprintMissingItem";
    static final String COVER_REGISTRY = "gtitBlueprintCoverRegistry";
    static final String COVER_DAMAGE = "gtitBlueprintCoverDamage";
    static final String MISSING_COVER = "gtitBlueprintMissingCover";
    private static final Field LIST_ENTRIES = listEntriesField();

    private HologramBlueprintNbt() {}

    static String encodeCaptured(NBTTagCompound tag) throws IOException {
        if (tag == null) return null;
        NBTTagCompound copy = (NBTTagCompound) tag.copy();
        rewrite(copy, null, "", false, false);
        stableItems(copy, true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(
            new LimitedOutput(new GZIPOutputStream(new LimitedOutput(bytes, MAX_COMPRESSED)), MAX_EXPANDED))) {
            CompressedStreamTools.write(copy, output);
        }
        return Base64.getEncoder()
            .encodeToString(bytes.toByteArray());
    }

    static NBTTagCompound decode(String value) throws IOException {
        if (value == null || value.isEmpty()) return null;
        if (value.length() > ((MAX_COMPRESSED + 2) / 3) * 4) throw new IOException("蓝图压缩NBT超出上限");
        try {
            byte[] bytes = Base64.getDecoder()
                .decode(value);
            if (bytes.length > MAX_COMPRESSED) throw new IOException("蓝图压缩NBT超出上限");
            NBTTagCompound tag = HologramSnapshotCodec.decode(bytes, MAX_EXPANDED, 16L * 1024 * 1024);
            stableItems(tag, false);
            return tag;
        } catch (RuntimeException invalid) {
            throw new IOException("蓝图NBT编码无效", invalid);
        }
    }

    static String sanitize(String value) throws IOException {
        return value == null ? null : encodeCaptured(decode(value));
    }

    static NBTTagCompound forPlacement(String value, UUID ownerUuid, String ownerName, int x, int y, int z,
        boolean retainNbt, boolean retainCovers) throws IOException {
        return forPlacement(value, ownerUuid, ownerName, x, y, z, retainNbt, retainCovers, null);
    }

    static NBTTagCompound forPlacement(String value, UUID ownerUuid, String ownerName, int x, int y, int z,
        boolean retainNbt, boolean retainCovers, List<String> keys) throws IOException {
        if (!retainNbt && !retainCovers) return null;
        NBTTagCompound result = decode(value);
        if (result == null) return null;
        result = filter(result, retainNbt ? keys : java.util.Collections.emptyList(), retainCovers);
        rewrite(result, ownerUuid, ownerName == null ? "" : ownerName, !retainCovers, false);
        result.setInteger("x", x);
        result.setInteger("y", y);
        result.setInteger("z", z);
        return result;
    }

    static NBTTagCompound itemForPlacement(String value, UUID ownerUuid, String ownerName, boolean retainNbt,
        boolean retainCovers) throws IOException {
        return itemForPlacement(value, ownerUuid, ownerName, retainNbt, retainCovers, null);
    }

    static NBTTagCompound itemForPlacement(String value, UUID ownerUuid, String ownerName, boolean retainNbt,
        boolean retainCovers, List<String> keys) throws IOException {
        if (!retainNbt && !retainCovers) return null;
        NBTTagCompound result = decode(value);
        if (result != null) {
            result = filter(result, retainNbt ? keys : java.util.Collections.emptyList(), retainCovers);
            rewrite(result, ownerUuid, ownerName == null ? "" : ownerName, !retainCovers, false);
        }
        return result;
    }

    static String filterCaptured(String value, List<String> keys, boolean retainCovers) throws IOException {
        NBTTagCompound source = decode(value);
        if (source == null) return null;
        NBTTagCompound filtered = filter(source, keys, retainCovers);
        rewrite(filtered, null, "", !retainCovers, false);
        return encodeCaptured(filtered);
    }

    private static NBTTagCompound filter(NBTTagCompound source, List<String> keys, boolean retainCovers) {
        if (keys == null && retainCovers) return source;
        Set<String> selected = keys == null ? null : new HashSet<>(keys);
        NBTTagCompound result = new NBTTagCompound();
        for (Object raw : source.func_150296_c()) {
            String key = (String) raw;
            String normalized = key.toLowerCase(Locale.ROOT);
            boolean cover = coverKey(normalized);
            if ((cover && retainCovers)
                || (!cover && (selected == null || selected.contains(key) || essentialKey(normalized))))
                result.setTag(
                    key,
                    source.getTag(key)
                        .copy());
        }
        return result;
    }

    private static boolean essentialKey(String key) {
        return key.equals("id") || key
            .equals("mid") || key.equals("x") || key.equals("y") || key.equals("z") || uuidKey(key) || nameKey(key);
    }

    /** ItemStack.writeToNBT in local MCP writes numeric id, Count and Damage; tile/cover ids do not have this shape. */
    private static boolean itemCompound(NBTTagCompound tag) {
        return tag.hasKey("id", 99) && tag.hasKey("Count", 99) && tag.hasKey("Damage", 99);
    }

    static Item stableItem(NBTTagCompound stack) {
        if (stack.hasKey(ITEM_REGISTRY, 8)) {
            Object found = Item.itemRegistry.getObject(stack.getString(ITEM_REGISTRY));
            return found instanceof Item ? (Item) found : null;
        }
        return stack.hasKey("id", 99) ? Item.getItemById(stack.getInteger("id")) : null;
    }

    static boolean hasMissingItems(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            if (compound.getBoolean(MISSING_COVER)) return true;
            if (itemCompound(compound) && compound.hasKey(ITEM_REGISTRY, 8) && stableItem(compound) == null)
                return true;
            for (Object raw : compound.func_150296_c()) if (hasMissingItems(compound.getTag((String) raw))) return true;
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            for (NBTBase entry : listEntries(list)) if (hasMissingItems(entry)) return true;
        }
        return false;
    }

    /** Payment comparisons and native tile readers must never receive blueprint-only transport markers. */
    static NBTTagCompound nativePayload(NBTTagCompound tag) throws IOException {
        if (tag == null) return null;
        if (hasMissingItems(tag)) throw new IOException("蓝图NBT包含缺失物品，不能恢复或扣除资源");
        NBTTagCompound copy = (NBTTagCompound) tag.copy();
        removeMarkers(copy);
        return copy;
    }

    private static void removeMarkers(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            compound.removeTag(ITEM_REGISTRY);
            compound.removeTag(MISSING_ITEM);
            compound.removeTag(COVER_REGISTRY);
            compound.removeTag(COVER_DAMAGE);
            compound.removeTag(MISSING_COVER);
            for (Object raw : compound.func_150296_c()) removeMarkers(compound.getTag((String) raw));
        } else if (tag instanceof NBTTagList) {
            for (NBTBase entry : listEntries((NBTTagList) tag)) removeMarkers(entry);
        }
    }

    private static void stableItems(NBTBase tag, boolean capture) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            if (compound.hasKey("gt.covers", 9)) {
                NBTTagList covers = (NBTTagList) compound.getTag("gt.covers");
                if (covers.func_150303_d() == 10) {
                    for (int i = 0; i < covers.tagCount(); i++) stableCover(covers.getCompoundTagAt(i), capture);
                }
            }
            if (itemCompound(compound)) {
                if (capture && !compound.hasKey(ITEM_REGISTRY, 8)) {
                    Item item = Item.getItemById(compound.getInteger("id"));
                    String registry = item == null ? "unresolved:" + compound.getInteger("id")
                        : String.valueOf(Item.itemRegistry.getNameForObject(item));
                    compound.setString(ITEM_REGISTRY, registry);
                }
                if (compound.hasKey(ITEM_REGISTRY, 8)) {
                    Item item = stableItem(compound);
                    compound.setShort("id", (short) (item == null ? -1 : Item.getIdFromItem(item)));
                    if (item == null) compound.setBoolean(MISSING_ITEM, true);
                    else compound.removeTag(MISSING_ITEM);
                }
            }
            for (Object raw : new ArrayList<>(compound.func_150296_c()))
                stableItems(compound.getTag((String) raw), capture);
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            for (NBTBase entry : listEntries(list)) stableItems(entry, capture);
        }
    }

    private static void stableCover(NBTTagCompound row, boolean capture) {
        if (!row.hasKey("id", 3) || row.getInteger("id") == 0 && !row.hasKey(COVER_REGISTRY, 8)) return;
        // GT CoverRegistry NBT_ID uses GTUtility.stackToInt: low 16 bits item ID, upper 16 bits damage.
        if (capture && !row.hasKey(COVER_REGISTRY, 8)) {
            int packed = row.getInteger("id");
            Item item = Item.getItemById(packed & 0xFFFF);
            row.setString(
                COVER_REGISTRY,
                item == null ? "unresolved:" + (packed & 0xFFFF)
                    : String.valueOf(Item.itemRegistry.getNameForObject(item)));
            row.setInteger(COVER_DAMAGE, packed >>> 16);
        }
        if (row.hasKey(COVER_REGISTRY, 8)) {
            Object found = Item.itemRegistry.getObject(row.getString(COVER_REGISTRY));
            if (!(found instanceof Item)) {
                row.setInteger("id", 0);
                row.setBoolean(MISSING_COVER, true);
            } else {
                row.setInteger("id", Item.getIdFromItem((Item) found) | row.getInteger(COVER_DAMAGE) << 16);
                row.removeTag(MISSING_COVER);
            }
        }
    }

    static Set<String> itemRegistries(NBTBase tag) {
        return resourceRegistries(tag);
    }

    /** Registry identities plus Forge's registration-time provider identity for each selected fluid. */
    static Set<String> resourceRegistries(NBTBase tag) {
        Set<String> result = new HashSet<>();
        collectRegistries(tag, result);
        return result;
    }

    private static void collectRegistries(NBTBase tag, Set<String> result) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            if (compound.hasKey(ITEM_REGISTRY, 8)) result.add(compound.getString(ITEM_REGISTRY));
            if (compound.hasKey(COVER_REGISTRY, 8)) result.add(compound.getString(COVER_REGISTRY));
            if (compound.hasKey("FluidName", 8)) {
                Fluid fluid = FluidRegistry.getFluid(compound.getString("FluidName"));
                if (fluid != null) result.add(FluidRegistry.getDefaultFluidName(fluid));
            }
            for (Object raw : compound.func_150296_c()) collectRegistries(compound.getTag((String) raw), result);
        } else if (tag instanceof NBTTagList) {
            for (NBTBase entry : listEntries((NBTTagList) tag)) collectRegistries(entry, result);
        }
    }

    private static void rewrite(NBTBase tag, UUID uuid, String name, boolean stripCovers, boolean ownerContext) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            for (Object raw : new ArrayList<>(compound.func_150296_c())) {
                String key = (String) raw;
                String normalized = key.toLowerCase(Locale.ROOT);
                NBTBase original = compound.getTag(key);
                if (stripCovers && coverKey(normalized)) {
                    compound.removeTag(key);
                } else if (original instanceof NBTTagCompound || original instanceof NBTTagList) {
                    rewrite(
                        original,
                        uuid,
                        name,
                        stripCovers,
                        ownerContext || nameKey(normalized) || uuidKey(normalized));
                } else if (nameKey(normalized) || (ownerContext && normalized.equals("name"))) {
                    compound.setString(key, name);
                } else if (uuidKey(normalized) || (ownerContext && (normalized.equals("id") || normalized.equals("uuid")
                    || normalized.equals("uuidmost")
                    || normalized.equals("uuidleast")))) {
                        if (normalized.endsWith("most"))
                            compound.setLong(key, uuid == null ? 0 : uuid.getMostSignificantBits());
                        else if (normalized.endsWith("least"))
                            compound.setLong(key, uuid == null ? 0 : uuid.getLeastSignificantBits());
                        else if (original.getId() == 11) compound.setIntArray(
                            key,
                            uuid == null ? new int[4]
                                : new int[] { (int) (uuid.getMostSignificantBits() >>> 32),
                                    (int) uuid.getMostSignificantBits(), (int) (uuid.getLeastSignificantBits() >>> 32),
                                    (int) uuid.getLeastSignificantBits() });
                        else compound.setString(key, uuid == null ? "" : uuid.toString());
                    } else rewrite(original, uuid, name, stripCovers, ownerContext);
            }
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            for (NBTBase entry : listEntries(list)) rewrite(entry, uuid, name, stripCovers, ownerContext);
        }
    }

    /** Local MCP has no generic list getter; the sole List field is stable across its mapped/SRG names. */
    private static Field listEntriesField() {
        Field selected = null;
        for (Field field : NBTTagList.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) continue;
            if (selected != null) throw new IllegalStateException("NBTTagList has multiple List fields");
            selected = field;
        }
        if (selected == null) throw new IllegalStateException("NBTTagList List field unavailable");
        selected.setAccessible(true);
        return selected;
    }

    @SuppressWarnings("unchecked")
    private static List<NBTBase> listEntries(NBTTagList list) {
        try {
            return (List<NBTBase>) LIST_ENTRIES.get(list);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("NBTTagList entries inaccessible", failure);
        }
    }

    private static boolean uuidKey(String key) {
        return key.equals("ownerid") || key.equals("owneruuid")
            || key.equals("mowneruuid")
            || key.equals("owneruuidmost")
            || key.equals("owneruuidleast")
            || key.equals("mowneruuidmost")
            || key.equals("mowneruuidleast")
            || key.equals("owner_id")
            || key.equals("owner_uuid");
    }

    private static boolean nameKey(String key) {
        return key.equals("owner") || key.equals("ownername") || key.equals("mownername") || key.equals("owner_name");
    }

    private static boolean coverKey(String key) {
        return key.equals("gt.covers") || key.equals("covers")
            || key.equals("cover")
            || key.equals("mcoverlist")
            || key.equals("mcoverids")
            || key.equals("mcoverdata")
            || key.matches("mcoverdata[0-5]");
    }

    private static final class LimitedOutput extends FilterOutputStream {

        private final int limit;
        private int written;

        LimitedOutput(OutputStream output, int limit) {
            super(output);
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            reserve(1);
            out.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            reserve(length);
            out.write(bytes, offset, length);
        }

        private void reserve(int count) throws IOException {
            if (count < 0 || count > limit - written) throw new IOException("蓝图NBT超出字节上限");
            written += count;
        }
    }
}
