package com.miaokatze.gtit.hologram;

import java.util.Arrays;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.miaokatze.gtit.testutil.SimpleAssert;

import gregtech.api.util.HatchElementBuilder;

final class HologramCompatibilityRegression {

    static void run() {
        IStructureElement<Object> tiers = StructureUtility.<Object, Integer>ofBlocksTiered(
            (block, meta) -> { throw new AssertionError("must not convert controller tiers"); },
            Arrays.asList(Pair.of(Blocks.stone, 0), Pair.of(Blocks.dirt, 0), Pair.of(Blocks.cobblestone, 0)),
            -1,
            (context, tier) -> { throw new AssertionError("must not set controller tiers"); },
            context -> { throw new AssertionError("must not get controller tiers"); });
        IStructureElement<Object> hatch = HatchElementBuilder.<Object>builder()
            .adder((context, tile, index) -> { throw new AssertionError("must not add hatches"); })
            .casingIndex(1)
            .hint(1)
            .build();
        IStructureElement<Object> unit = StructureUtility.withChannel(
            "unit_casing",
            StructureUtility.ofChain(
                hatch,
                StructureUtility
                    .onElementPass(context -> { throw new AssertionError("must not count casings"); }, tiers)));
        ItemStack trigger = new ItemStack(Items.feather, 3);
        SimpleAssert.eq(
            Blocks.cobblestone,
            HologramCasingFallback.resolve(unit, trigger).block,
            "unset channel inherits master");
        ChannelDataAccessor.setChannelData(trigger, "unit_casing", 2);
        ChannelDataAccessor.setChannelData(trigger, "machine_casing", 1);
        SimpleAssert.eq(
            Blocks.dirt,
            HologramCasingFallback.resolve(unit, trigger).block,
            "independent unit channel selects casing");
        IStructureElement<Object> machine = StructureUtility.withChannel("machine_casing", tiers);
        SimpleAssert.eq(
            Blocks.stone,
            HologramCasingFallback.resolve(machine, trigger).block,
            "machine casing uses its own signal");
        IStructureElement<Object> nested = StructureUtility
            .withChannel("unit_casing", StructureUtility.withChannel("inner", tiers));
        SimpleAssert.eq(
            Blocks.dirt,
            HologramCasingFallback.resolve(nested, trigger).block,
            "nested unset channel inherits switched signal");
        HologramDeclarationCandidates declaration = new HologramDeclarationCandidates();
        declaration.collect(unit, null, null, 0, 0, 0, trigger, null, 0);
        SimpleAssert.eq(3, declaration.stacks.size(), "channel and hatch chain expose all declared tier alternatives");
        SimpleAssert.that(!declaration.complete, "unknown hatch predicate keeps alternatives partial");
        SimpleAssert.eq(
            Blocks.stone,
            HologramCasingFallback
                .resolve(StructureUtility.ofChain(hatch, StructureUtility.ofBlock(Blocks.stone, 0)), trigger).block,
            "non-GT fixed casing is authoritative declaration");
        SimpleAssert.that(
            HologramCasingFallback.resolve(StructureUtility.ofBlock(Blocks.chest, 0), trigger) == null,
            "tile entity casing stays protected");
        SimpleAssert.that(
            HologramCasingFallback.resolve(StructureUtility.ofBlock(Blocks.stone, 0, Blocks.dirt, 0), trigger) == null,
            "different accepted and default blocks stay protected");
        SimpleAssert.that(
            HologramCasingFallback.resolve(
                StructureUtility
                    .ofChain(StructureUtility.ofBlock(Blocks.stone, 0), StructureUtility.ofBlock(Blocks.dirt, 0)),
                trigger) == null,
            "ambiguous declarations stay protected");
        Object context = new Object();
        HologramChannelTrace.Scope first = HologramChannelTrace.begin(context);
        HologramDeclarationWrappers.observeChannels(unit, trigger);
        HologramChannelTrace.record(trigger, "unit_casing", 2, 2);
        HologramChannelTrace.Report a = HologramChannelTrace.end(first);
        HologramChannelTrace.Scope second = HologramChannelTrace.begin(context);
        ChannelDataAccessor.setChannelData(trigger, "unit_casing", 3);
        HologramDeclarationWrappers.observeChannels(nested, trigger);
        HologramDeclarationWrappers.observeChannels(machine, trigger);
        HologramChannelTrace.Report merged = HologramChannelTrace.merge(a, HologramChannelTrace.end(second));
        SimpleAssert.eq(3, merged.observations.get("unit_casing").effective, "later batch has latest effective value");
        SimpleAssert.eq(3, merged.observations.get("unit_casing").operations, "batch operations merge with OR");
        SimpleAssert
            .eq(3, merged.observations.get("inner").effective, "declaration trace preserves nested inheritance");
        SimpleAssert.that(!merged.observations.get("inner").explicit, "inherited channel is not made explicit");
        HologramChannelTrace.Scope presence = HologramChannelTrace.begin(context);
        ChannelDataAccessor.setChannelData(trigger, "custom", 7);
        HologramChannelTrace.record(trigger, "custom", 7, 2);
        HologramChannelTrace.record(trigger, "custom", 1, 4);
        ItemStack switched = ChannelDataAccessor.withChannel(trigger, "unit_casing");
        HologramChannelTrace.record(switched, "inherited", 0, 4);
        trigger.getTagCompound()
            .getCompoundTag("channels")
            .setInteger("zero", 0);
        HologramChannelTrace.record(trigger, "zero", 1, 4);
        HologramChannelTrace.Report presenceReport = HologramChannelTrace.end(presence);
        net.minecraft.nbt.NBTTagList rows = HologramCapabilities.describe(context, trigger, presenceReport)
            .toNBT();
        SimpleAssert.eq(7, row(rows, "custom").getInteger("current"), "presence keeps explicit integer display");
        SimpleAssert.eq(6, row(rows, "custom").getInteger("operations"), "presence retains operation bits");
        SimpleAssert.eq(3, row(rows, "inherited").getInteger("current"), "presence inherits current switched signal");
        SimpleAssert
            .that(!row(rows, "inherited").getBoolean("explicit"), "presence does not create an explicit channel");
        SimpleAssert.eq(0, row(rows, "zero").getInteger("current"), "explicit zero is not converted to presence one");
        SimpleAssert.that(row(rows, "zero").getBoolean("explicit"), "explicit zero remains an explicit integer");
        SimpleAssert.that(merged.observations.containsKey("machine_casing"), "later batch channel is retained");
        SimpleAssert.eq(
            3,
            merged.materials.get("unit_casing")
                .size(),
            "actual tier declaration supplies material options");
    }

    private static net.minecraft.nbt.NBTTagCompound row(net.minecraft.nbt.NBTTagList rows, String id) {
        for (int i = 0; i < rows.tagCount(); i++) {
            net.minecraft.nbt.NBTTagCompound row = rows.getCompoundTagAt(i);
            if (id.equals(row.getString("id"))) return row;
        }
        throw new AssertionError("Missing capability row: " + id);
    }

}
