package com.miaokatze.gtit.hologram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.apache.commons.lang3.tuple.Pair;

import bartworks.common.loaders.ItemRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.common.blocks.BlockCasings5;
import gregtech.common.tileentities.machines.multi.MTEElectricImplosionCompressor;
import gregtech.common.tileentities.machines.multi.MTEIndustrialCokeOven;
import gtPlusPlus.core.block.ModBlocks;

/** Local, source-reviewed machine options. Global indicator registration is not a support directory. */
public final class HologramCapabilities {

    private static final String MACHINES = "gregtech.common.tileentities.machines.multi.";

    private HologramCapabilities() {}

    public static Result describe(Object context, ItemStack trigger, HologramChannelTrace.Report report) {
        Result result = new Result(trigger);
        Row main = result.add("main", "主信号（未设子信道时回退）", "master");
        for (int value = 1; value <= 64; value++) main.option(value, Integer.toString(value), null);
        if (report != null && report.context == context) {
            for (HologramChannelTrace.Observation observed : report.observations.values()) {
                Row row = result.add(observed.id, observed.id + "（值域未适配，只读）", "observed");
                row.operations = observed.operations;
            }
        }
        String type = context == null ? ""
            : context.getClass()
                .getName();
        boolean ebf = type.equals(MACHINES + "MTEElectricBlastFurnace");
        boolean extractor = type.equals(MACHINES + "MTEIndustrialExtractor");
        boolean eic = type.equals(MACHINES + "MTEElectricImplosionCompressor");
        boolean coke = type.equals(MACHINES + "MTEIndustrialCokeOven");
        if (ebf || coke) coil(result);
        if (ebf) {
            Row hatch = result.add("gt_hatch", "自动取用仓室（独立于无仓室施工）", "presence");
            hatch.option(1, "启用", null);
        }
        if (extractor) {
            Row pipe = result.add("item_pipe", "物品管道壳体", "material");
            for (int raw = 1; raw <= 8; raw++)
                pipe.option(raw, null, new ItemStack(GregTechAPI.sBlockCasings11, 1, raw - 1));
        }
        if (extractor || eic) {
            Row glass = result.add("glass", "玻璃目标（实际材料与档位）", "material");
            // chainAllGlasses caps the raw signal to the eleven primary HV..UXV glasses.
            // Copy this reviewed mapping locally; getGlassList's lazy global registration is unnecessary here.
            for (int i = 0; i < 11; i++) {
                ItemStack stack = new ItemStack(ItemRegistry.bw_realglas, 1, i);
                glass.option(i + 1, stack.getDisplayName() + " · 档位 " + (i + 3), stack);
            }
        }
        if (eic) {
            Row piston = result.add("piston_block", "活塞材料", "material");
            List<Pair<Block, Integer>> list = MTEElectricImplosionCompressor.getTierBlockList();
            for (int i = 0; i < list.size(); i++) {
                Pair<Block, Integer> entry = list.get(i);
                piston.option(i + 1, null, new ItemStack(entry.getLeft(), 1, entry.getRight()));
            }
        }
        if (type.equals(MACHINES + "MTEMegaDistillationTower")) range(result, "height", "塔身层数", 5, " 层");
        if (coke) {
            Row casing = result.add("coke_oven_casing", "焦炉壳体", "material");
            casing.option(1, null, new ItemStack(ModBlocks.blockCasingsMisc, 1, 2));
            casing.option(2, null, new ItemStack(ModBlocks.blockCasingsMisc, 1, 3));
            if (((MTEIndustrialCokeOven) context).getCoilTier() < HeatingCoilLevel.MAX.getTier() + 1) {
                range(result, "length", "焦炉构造分段参数", 16, " 段");
            } else {
                Row length = result.add("length", "长度（控制器实际线圈为 MAX，当前由主信号决定）", "inactive");
                length.editable = false;
                Row slices = result.add("main", "主信号（当前焦炉分段数量）", "master");
                for (int raw = 1; raw <= 16; raw++) slices.option(raw, raw + " 段", null);
            }
        }
        return result;
    }

    private static void range(Result result, String id, String label, int max, String unit) {
        Row row = result.add(id, label, "size");
        for (int raw = 1; raw <= max; raw++) row.option(raw, raw + unit, null);
    }

    private static void coil(Result result) {
        Row row = result.add("coil", "加热线圈目标", "coil");
        for (int raw = 1; raw <= 14; raw++) {
            HeatingCoilLevel level = HeatingCoilLevel.getFromTier((byte) (raw - 1));
            row.option(
                raw,
                level.getName() + " · " + level.getHeat() + " K",
                new ItemStack(GregTechAPI.sBlockCasings5, 1, BlockCasings5.getMetaFromCoilHeat(level)));
        }
    }

    public static final class Result {

        private final Map<String, Row> rows = new LinkedHashMap<>();
        private final NBTTagCompound previous;
        private final int main;

        private Result(ItemStack trigger) {
            main = trigger == null ? 1 : trigger.stackSize;
            previous = trigger != null && trigger.hasTagCompound() ? (NBTTagCompound) trigger.getTagCompound()
                .getCompoundTag("channels")
                .copy() : new NBTTagCompound();
        }

        private Row add(String id, String label, String kind) {
            Row row = new Row(
                id,
                label,
                kind,
                "main".equals(id) ? main : previous.hasKey(id, 3) ? previous.getInteger(id) : main,
                previous.hasKey(id, 3));
            Row old = rows.put(id, row);
            if (old != null) row.operations = old.operations;
            return row;
        }

        public NBTTagList toNBT() {
            NBTTagList list = new NBTTagList();
            for (Row row : rows.values()) list.appendTag(row.toNBT());
            return list;
        }

        /** Saved keys for other machines may be retained or removed, but cannot be introduced or changed. */
        public boolean validConfiguration(int requestedMain, NBTTagCompound channels) {
            if (!rows.get("main")
                .has(requestedMain) || channels == null
                || channels.func_150296_c()
                    .size() > 64)
                return false;
            for (Object key : channels.func_150296_c()) {
                String id = (String) key;
                Row row = rows.get(id);
                if ("main".equals(id) || !channels.hasKey(id, 3)) return false;
                int raw = channels.getInteger(id);
                if (row != null && row.editable) {
                    if (!row.has(raw)) return false;
                } else if (!previous.hasKey(id, 3) || previous.getInteger(id) != raw) return false;
            }
            return true;
        }

        /** Filters a temporary construction trigger; callers must retain the complete tool configuration separately. */
        public NBTTagCompound sanitizeChannels(NBTTagCompound input) {
            NBTTagCompound safe = new NBTTagCompound();
            if (input == null) return safe;
            for (Row row : rows.values()) {
                if ("main".equals(row.id) || !input.hasKey(row.id, 3)) continue;
                int value = input.getInteger(row.id);
                if (value > 0 && (!row.editable || row.has(value))) safe.setInteger(row.id, value);
            }
            return safe;
        }

        /** Bind the complete saved configuration when capture uses a filtered temporary trigger. */
        public Result withSavedChannels(NBTTagCompound saved) {
            for (Object key : previous.func_150296_c()
                .toArray()) previous.removeTag((String) key);
            if (saved != null) {
                for (Object key : saved.func_150296_c()) {
                    String id = (String) key;
                    if (saved.hasKey(id, 3)) previous.setInteger(id, saved.getInteger(id));
                }
            }
            return this;
        }

        /** Encodes a UI option as its original channel signal; zero means remove the named key. */
        public boolean encodeOption(NBTTagCompound channels, String id, int value) {
            Row row = rows.get(id);
            if (channels == null || row == null || !row.editable || "main".equals(id)) return false;
            if (value == 0) channels.removeTag(id);
            else if (row.has(value)) channels.setInteger(id, value);
            else return false;
            return true;
        }
    }

    private static final class Row {

        private final String id, label, kind;
        private final int current;
        private final boolean explicit;
        private final List<NBTTagCompound> options = new ArrayList<>();
        private boolean editable;
        private int operations;

        private Row(String id, String label, String kind, int current, boolean explicit) {
            this.id = id;
            this.label = label;
            this.kind = kind;
            this.current = current;
            this.explicit = explicit;
        }

        private void option(int value, String name, ItemStack stack) {
            if (stack != null && stack.getItem() == null) return;
            NBTTagCompound option = new NBTTagCompound();
            option.setInteger("value", value);
            option.setBoolean("present", true);
            option.setString("label", name == null ? stack.getDisplayName() : name);
            if (stack != null) option.setTag("item", stack.writeToNBT(new NBTTagCompound()));
            options.add(option);
            editable = true;
        }

        private boolean has(int value) {
            for (NBTTagCompound option : options) if (option.getInteger("value") == value) return true;
            return false;
        }

        private NBTTagCompound toNBT() {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("id", id);
            row.setString("label", label);
            row.setString("kind", kind);
            row.setBoolean("editable", editable);
            row.setBoolean("explicit", explicit);
            row.setInteger("current", "presence".equals(kind) ? explicit ? 1 : 0 : current);
            row.setInteger("operations", operations);
            NBTTagList values = new NBTTagList();
            if (!"main".equals(id) && editable) {
                NBTTagCompound unset = new NBTTagCompound();
                unset.setInteger("value", 0);
                unset.setBoolean("present", false);
                unset.setString("label", "presence".equals(kind) ? "关闭" : "继承主信号");
                values.appendTag(unset);
            }
            for (NBTTagCompound option : options) values.appendTag(option.copy());
            row.setTag("options", values);
            return row;
        }
    }
}
