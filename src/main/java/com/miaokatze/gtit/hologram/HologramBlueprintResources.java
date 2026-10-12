package com.miaokatze.gtit.hologram;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** Costs of the selected, final payload. Inspection never edits the captured or placement NBT. */
public final class HologramBlueprintResources {

    private HologramBlueprintResources() {}

    public static final class FluidCost {

        public final FluidStack stack;
        public long amount;
        public final String key;

        private FluidCost(FluidStack stack, long amount, String key) {
            this.stack = stack.copy();
            this.stack.amount = 1;
            this.amount = amount;
            this.key = key;
        }
    }

    private static final class Entry {

        final String key;
        final ItemStack item;
        final FluidStack fluid;
        BigInteger amount;

        Entry(String key, ItemStack item, FluidStack fluid, BigInteger amount) {
            this.key = key;
            this.item = item == null ? null : item.copy();
            this.fluid = fluid == null ? null : fluid.copy();
            this.amount = amount;
        }
    }

    public static final class Plan {

        public final List<ItemStack> items = new ArrayList<>();
        public final List<FluidCost> fluids = new ArrayList<>();
        public BigInteger energy = BigInteger.ZERO;
        private final List<Entry> entries = new ArrayList<>();

        public Plan merge(Plan other) throws IOException {
            if (other == null) return this;
            addItems(other.items);
            for (FluidCost fluid : other.fluids) addFluid(fluid.stack, fluid.amount, fluid.key);
            energy = energy.add(other.energy);
            for (Entry entry : other.entries) addEntry(entry);
            return this;
        }

        private void addEntry(Entry entry) {
            for (Entry existing : entries) {
                if (!existing.key.equals(entry.key)) continue;
                boolean same = existing.item == null && entry.item == null
                    && existing.fluid == null
                    && entry.fluid == null;
                if (existing.item != null && entry.item != null) same = existing.item.isItemEqual(entry.item)
                    && ItemStack.areItemStackTagsEqual(existing.item, entry.item);
                if (existing.fluid != null && entry.fluid != null) same = existing.fluid.isFluidEqual(entry.fluid);
                if (same) {
                    existing.amount = existing.amount.add(entry.amount);
                    return;
                }
            }
            entries.add(new Entry(entry.key, entry.item, entry.fluid, entry.amount));
        }

        public Plan addItems(List<ItemStack> additions) throws IOException {
            if (additions != null) for (ItemStack stack : additions) addItem(stack);
            return this;
        }

        private void addItem(ItemStack stack) throws IOException {
            if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return;
            for (ItemStack existing : items) {
                if (!existing.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(existing, stack)) continue;
                if (stack.stackSize > Integer.MAX_VALUE - existing.stackSize) throw new IOException("蓝图库存数量过大");
                existing.stackSize += stack.stackSize;
                return;
            }
            items.add(stack.copy());
        }

        private void addFluid(FluidStack stack, long amount, String key) throws IOException {
            if (amount <= 0) return;
            for (FluidCost existing : fluids) {
                if (!existing.stack.isFluidEqual(stack)) continue;
                if (amount > Long.MAX_VALUE - existing.amount) throw unsupported(key, "流体数量超出上限");
                existing.amount += amount;
                return;
            }
            fluids.add(new FluidCost(stack, amount, key));
        }

        /** The service owns the world/main-inventory snapshot and must roll back the transaction on false. */
        public boolean reserve(HologramMaterials.Transaction transaction) {
            return reserve(new Payment() {

                @Override
                public boolean item(ItemStack stack, boolean simulate) {
                    return transaction.takeAll(stack, simulate);
                }

                @Override
                public boolean fluid(FluidStack stack, long amount, boolean simulate) {
                    return transaction.takeFluid(stack, amount, simulate);
                }

                @Override
                public boolean energy(BigInteger amount, boolean simulate) {
                    return transaction.takeEnergy(amount, simulate);
                }
            });
        }

        boolean reserve(Payment payment) {
            for (ItemStack item : items) if (!payment.item(item.copy(), true)) return false;
            for (FluidCost fluid : fluids) if (!payment.fluid(fluid.stack.copy(), fluid.amount, true)) return false;
            if (!payment.energy(energy, true)) return false;
            for (ItemStack item : items) if (!payment.item(item.copy(), false)) return false;
            for (FluidCost fluid : fluids) if (!payment.fluid(fluid.stack.copy(), fluid.amount, false)) return false;
            return payment.energy(energy, false);
        }

        public String missing(HologramMaterials.Context context) {
            for (ItemStack item : items) {
                int available = context.count(item, item.stackSize);
                if (available < item.stackSize)
                    return "库存材料不足：" + item.getDisplayName() + " 缺 " + (item.stackSize - available);
            }
            for (FluidCost fluid : fluids) {
                long available = context.fluidCount(fluid.stack, fluid.amount);
                if (available < fluid.amount)
                    return "AE流体不足：" + fluid.stack.getLocalizedName() + " 缺 " + (fluid.amount - available) + " mB";
            }
            BigInteger available = context.energyAvailable();
            return available.compareTo(energy) < 0 ? "无线电网不足：缺 " + energy.subtract(available) + " EU" : null;
        }

        public NBTTagList entries(HologramMaterials.Context context) {
            NBTTagList result = new NBTTagList();
            for (Entry entry : entries) {
                NBTTagCompound row = new NBTTagCompound();
                row.setString("key", entry.key);
                BigInteger available;
                if (entry.item != null) {
                    row.setString("type", "item");
                    row.setString("name", entry.item.getDisplayName());
                    available = BigInteger.valueOf(context.count(entry.item, entry.amount.intValue()));
                } else if (entry.fluid != null) {
                    row.setString("type", "fluid");
                    row.setString("name", entry.fluid.getLocalizedName());
                    available = BigInteger.valueOf(context.fluidCount(entry.fluid, entry.amount.longValue()));
                } else {
                    row.setString("type", "energy");
                    row.setString("name", "无线电网 EU");
                    available = context.energyAvailable();
                }
                row.setString("required", entry.amount.toString());
                row.setString("available", available.toString());
                row.setString(
                    "missing",
                    entry.amount.subtract(available)
                        .max(BigInteger.ZERO)
                        .toString());
                result.appendTag(row);
            }
            return result;
        }
    }

    interface Payment {

        boolean item(ItemStack stack, boolean simulate);

        boolean fluid(FluidStack stack, long amount, boolean simulate);

        boolean energy(BigInteger amount, boolean simulate);
    }

    public static Plan plan(NBTTagCompound selectedPayload) throws IOException {
        Plan result = new Plan();
        if (selectedPayload == null) return result;
        for (Object raw : selectedPayload.func_150296_c()) {
            String key = (String) raw;
            inspect(selectedPayload.getTag(key), key, key, result, 0, false, false);
        }
        return result;
    }

    private static void inspect(NBTBase value, String name, String rootKey, Plan plan, int depth,
        boolean inventoryContext, boolean fluidContext) throws IOException {
        if (depth > 64) throw unsupported(rootKey, "资源NBT嵌套过深");
        String lower = name.toLowerCase(Locale.ROOT);
        if (configurationName(lower)) return;
        inventoryContext |= inventoryContainer(lower);
        fluidContext |= fluidContainer(lower);
        if (name.equals("mStoredEnergy")) {
            long amount = number(value, rootKey);
            if (amount > 0) {
                BigInteger cost = BigInteger.valueOf(amount);
                plan.energy = plan.energy.add(cost);
                plan.addEntry(new Entry(rootKey, null, null, cost));
            }
            return;
        }
        if (name.equals("mStoredSteam")) {
            long stored = number(value, rootKey);
            if (stored == 0) return;
            if (stored > Long.MAX_VALUE / 2) throw unsupported(rootKey, "蒸汽数量超出上限");
            FluidStack steam = FluidRegistry.getFluidStack("steam", 1);
            if (steam == null) throw unsupported(rootKey, "蒸汽未注册");
            long amount = stored * 2;
            plan.addFluid(steam, amount, rootKey);
            plan.addEntry(new Entry(rootKey, null, steam, BigInteger.valueOf(amount)));
            return;
        }
        boolean storage = storageName(lower);
        if (value instanceof NBTTagCompound || value instanceof NBTTagList)
            storage |= lower.contains("fluid") || lower.contains("item")
                || lower.equals("contents")
                || lower.equals("stacks");
        if (value instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) value;
            if (compound.hasKey("Count") && compound.hasKey("id") && compound.hasKey("Damage")) {
                if (storage && !knownContainer(lower)) throw unsupported(rootKey, "未知库存存储格式");
                // Detached recipe/whitelist samples are configuration rather than real inventory slots.
                if (!inventoryContext) return;
                if (!compound.hasKey("Count", 99) || !compound.hasKey("Damage", 99)
                    || compound.getTag("Count")
                        .getId() > 4
                    || compound.getTag("Damage")
                        .getId() > 4)
                    throw unsupported(rootKey, "物品记录类型无效");
                if (compound.getBoolean(HologramBlueprintNbt.MISSING_ITEM)
                    || HologramBlueprintNbt.hasMissingItems(compound)) throw unsupported(rootKey, "库存物品未注册");
                ItemStack stack = ItemStack.loadItemStackFromNBT(HologramBlueprintNbt.nativePayload(compound));
                if (stack == null || stack.getItem() == null) throw unsupported(rootKey, "库存物品未注册");
                // GT writes Count as an int for large slots, while vanilla ItemStack's loader reads a byte.
                stack.stackSize = compound.getInteger("Count");
                if (stack.stackSize < 0) throw unsupported(rootKey, "库存物品数量无效");
                if (stack.stackSize > 0) {
                    plan.addItem(stack);
                    plan.addEntry(new Entry(rootKey, stack, null, BigInteger.valueOf(stack.stackSize)));
                }
                // The exact tagged item itself is paid for, including any of its nested contents.
                return;
            }
            if (compound.hasKey("FluidName") || compound.hasKey("Amount") && lower.contains("fluid")) {
                if (storage && !knownContainer(lower)) throw unsupported(rootKey, "未知流体存储格式");
                if (!fluidContext) return;
                if (!compound.hasKey("FluidName", 8) || !compound.hasKey("Amount", 99)
                    || compound.getTag("Amount")
                        .getId() > 4)
                    throw unsupported(rootKey, "流体记录类型无效");
                long amount = compound.getLong("Amount");
                if (amount < 0) throw unsupported(rootKey, "流体数量无效");
                if (amount == 0) return;
                FluidStack stack = FluidStack.loadFluidStackFromNBT(compound);
                if (stack == null || stack.getFluid() == null) throw unsupported(rootKey, "流体未注册");
                if (!HologramMaterials.supportsFluidNbt(stack)) throw unsupported(rootKey, "当前AE流体接口不能保留此流体NBT");
                plan.addFluid(stack, amount, rootKey);
                plan.addEntry(new Entry(rootKey, null, stack, BigInteger.valueOf(amount)));
                return;
            }
            if (storage && !compound.hasNoTags() && !knownContainer(lower)) throw unsupported(rootKey, "未知容器存储格式");
            for (Object raw : compound.func_150296_c()) {
                String key = (String) raw;
                inspect(compound.getTag(key), key, rootKey, plan, depth + 1, inventoryContext, fluidContext);
            }
            return;
        }
        if (value instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) value;
            if (storage && list.tagCount() > 0 && list.func_150303_d() != 10) throw unsupported(rootKey, "未知容器存储格式");
            if (storage && list.tagCount() > 0 && !knownContainer(lower)) throw unsupported(rootKey, "未知容器存储格式");
            if (list.func_150303_d() != 10) return;
            for (int i = 0; i < list.tagCount(); i++) {
                NBTBase entry = list.getCompoundTagAt(i);
                if (storage && entry instanceof NBTTagCompound) {
                    NBTTagCompound row = (NBTTagCompound) entry;
                    if (!row.hasNoTags() && !row.hasKey("FluidName")
                        && !(row.hasKey("id") && row.hasKey("Count") && row.hasKey("Damage")))
                        throw unsupported(rootKey, "未知容器条目格式");
                }
                inspect(entry, name, rootKey, plan, depth + 1, inventoryContext, fluidContext);
            }
            return;
        }
        if (storage && nonempty(value)) throw unsupported(rootKey, "未知资源存储格式");
    }

    private static boolean knownContainer(String name) {
        return inventoryContainer(name) || fluidContainer(name);
    }

    private static boolean inventoryContainer(String name) {
        return name.equals("inventory") || name.equals("items") || name.matches("moutputitem[0-9]+");
    }

    private static boolean fluidContainer(String name) {
        return name.equals("mfluid") || name.equals("fluid")
            || name.equals("fluids")
            || name.equals("mfluids")
            || name.matches("mfluid[0-9]+")
            || name.equals("moutputfluid")
            || name.equals("mfluidout");
    }

    private static boolean configurationName(String name) {
        return name.contains("recipe") || name.contains("filter")
            || name.contains("whitelist")
            || name.contains("blacklist")
            || name.startsWith("ghost")
            || name.equals("trade")
            || name.equals("trades")
            || name.equals("pattern")
            || name.equals("patterns");
    }

    private static boolean storageName(String name) {
        return name.equals("items") || name.equals("mitems")
            || name.contains("inventory")
            || name.contains("tank")
            || name.contains("itemhandler")
            || name.contains("itemstorage")
            || name.contains("storedenergy")
            || name.contains("storedeu")
            || name.equals("energy")
            || name.equals("menergy")
            || name.equals("eu")
            || name.equals("meu")
            || name.equals("battery")
            || name.equals("energybuffer")
            || name.equals("charge")
            || name.equals("mcharge")
            || name.equals("fluid")
            || name.equals("fluids")
            || name.equals("mfluid")
            || name.equals("mfluids")
            || name.matches("mfluid[0-9]+")
            || name.equals("storedsteam");
    }

    private static long number(NBTBase value, String key) throws IOException {
        if (!(value instanceof NBTBase.NBTPrimitive) || value.getId() > 4) throw unsupported(key, "资源数量类型无效");
        long amount = ((NBTBase.NBTPrimitive) value).func_150291_c();
        if (amount < 0) throw unsupported(key, "资源数量为负数");
        return amount;
    }

    private static boolean nonempty(NBTBase value) {
        if (value instanceof NBTBase.NBTPrimitive) return ((NBTBase.NBTPrimitive) value).func_150291_c() != 0;
        return !value.toString()
            .equals("\"\"");
    }

    private static IOException unsupported(String key, String detail) {
        return new IOException("不支持保留资源条目 " + key + "：" + detail + "；请取消此NBT条目后重试");
    }
}
