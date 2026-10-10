package com.miaokatze.gtit.hologram;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** 真 NBT/StructureLib/网络边界回归。真实世界施工另由 HologramSmokeTest 检查。 */
public final class HologramRegressionTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(HologramRegressionTest.class, args)) return;
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("native_channels_fallback_and_arbitrary_keys", HologramRegressionTest::channels);
        cases.put("packet_snapshot_roundtrip_keeps_all_channel_values", HologramRegressionTest::packetRoundTrip);
        cases.put("hostile_packet_lengths_are_rejected", HologramRegressionTest::badLengths);
        cases.put("capture_hooks_are_inert_outside_collection", HologramRegressionTest::inertCapture);
        cases.put("server_plan_prepare_ack_roundtrip", HologramRegressionTest::planProtocol);
        cases.put("frame_factory_material_and_te_boundary", HologramRegressionTest::frames);
        cases.put("world_height_layers_and_tick_quotas", HologramRegressionTest::layerSchedule);
        TestRunner.run(HologramRegressionTest.class, cases);
    }

    private static void frames() {
        try {
            java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            // Avoid a global GameRegistry mutation: these tested methods use no instance fields.
            gregtech.common.blocks.BlockFrameBox frame = (gregtech.common.blocks.BlockFrameBox) unsafe
                .allocateInstance(gregtech.common.blocks.BlockFrameBox.class);
            com.gtnewhorizon.structurelib.structure.IStructureElement<Object> tiered = com.gtnewhorizon.structurelib.structure.StructureUtility
                .<Object, Integer>ofBlocksTiered(
                    (block, meta) -> meta,
                    java.util.Arrays.asList(
                        org.apache.commons.lang3.tuple.Pair.of(frame, 17),
                        org.apache.commons.lang3.tuple.Pair.of(frame, 18)),
                    -1,
                    (context, tier) -> {},
                    context -> -1);
            HologramReplacementFamily.Family family = HologramReplacementFamily.resolve(tiered, frame, 17);
            SimpleAssert.that(
                family != null && family.id.startsWith("frame:"),
                "real steam-style tiered factory resolves frame family above metadata fifteen");
            SimpleAssert.that(family.contains(frame, 17), "first declared frame material is accepted");
            SimpleAssert.that(family.contains(frame, 18), "second declared frame material is accepted");
            SimpleAssert.that(!family.contains(frame, 19), "unlisted frame material is protected");
            SimpleAssert.that(!family.contains(frame, 17 | 0x1000), "tiered TE frame is protected");
            SimpleAssert.that(!frame.hasTileEntity(0xFFF), "ordinary frame maximum metadata has no TE");
            SimpleAssert.that(frame.hasTileEntity(0x1000), "real GT frame TE boundary uses MTE bit");
            SimpleAssert
                .that(!family.contains(net.minecraft.init.Blocks.stone, 17), "another block never joins frame family");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void layerSchedule() {
        java.util.List<Integer> indices = java.util.Arrays.asList(0, 1, 2, 3, 4);
        int[] heights = { 100, 98, 100, 99, 98 };
        java.util.List<java.util.List<Integer>> build = HologramLayerSchedule.layers(indices, heights, false);
        SimpleAssert.eq(3, build.size(), "different world heights never merge");
        SimpleAssert.eq(
            1,
            build.get(0)
                .get(0)
                .intValue(),
            "build starts at lowest world height");
        SimpleAssert.eq(
            0,
            HologramLayerSchedule.layers(indices, heights, true)
                .get(0)
                .get(0)
                .intValue(),
            "removal starts at highest world height");
        SimpleAssert.eq(0, HologramLayerSchedule.quota(160, -1), "PREPARE makes no edits");
        for (int tick = 0; tick < 20; tick++) SimpleAssert
            .eq((tick + 1) * 8, HologramLayerSchedule.quota(160, tick), "large layer spreads over 20 ticks");
        SimpleAssert
            .eq(1, HologramLayerSchedule.quota(1, 0), "small layer may place early but retains its own duration");
        SimpleAssert.eq(160, HologramLayerSchedule.quota(160, 29), "CPU overrun stays bounded to the same layer");
        SimpleAssert.eq(20, HologramLayerSchedule.LAYER_TICKS, "layer lasts one second");
        SimpleAssert.eq(10, HologramLayerSchedule.GAP_TICKS, "gap lasts half a second");
        SimpleAssert.that(
            HologramLayerSchedule.nextLayerDue(119) == 130,
            "twenty active ticks 100..119, ten empty ticks 120..129, next layer starts 130");
        SimpleAssert.that(
            HologramLayerSchedule.nextLayerDue(145) == 156,
            "CPU overrun still preserves the full inter-layer gap");
        long due = 100, pausedAt = 107, resumedAt = 407;
        long shiftedDue = HologramLayerSchedule.resumeDue(due, pausedAt, resumedAt);
        SimpleAssert.eq(
            HologramLayerSchedule.quota(80, (int) (pausedAt - due)),
            HologramLayerSchedule.quota(80, (int) (resumedAt - shiftedDue)),
            "pause preserves layer elapsed ticks");
    }

    private static void channels() {
        ItemStack trigger = new ItemStack(Items.feather, 123);
        SimpleAssert.eq(123, ChannelDataAccessor.getChannelData(trigger, "custom.future_mod"), "未设置通道回退主值");
        ChannelDataAccessor.setChannelData(trigger, "custom.future_mod", 2048);
        SimpleAssert.eq(2048, ChannelDataAccessor.withChannel(trigger, "custom.future_mod").stackSize, "原版子通道构造器看到完整值");
        SimpleAssert.eq(123, trigger.stackSize, "子通道不污染主值");
        ChannelDataAccessor.unsetChannelData(trigger, "custom.future_mod");
        SimpleAssert.eq(123, ChannelDataAccessor.getChannelData(trigger, "custom.future_mod"), "删除通道重新回退主值");
    }

    private static void packetRoundTrip() {
        NBTTagCompound action = new NBTTagCompound();
        action.setInteger("main", 123);
        NBTTagCompound channels = new NBTTagCompound();
        channels.setInteger("custom.future_mod", 2048);
        action.setTag("channels", channels);
        HologramNetwork.Action packet = new HologramNetwork.Action(action);
        channels.setInteger("custom.future_mod", 2);
        action.setInteger("main", 1);
        ByteBuf bytes = Unpooled.buffer();
        try {
            packet.toBytes(bytes);
            HologramNetwork.Action decoded = new HologramNetwork.Action();
            decoded.fromBytes(bytes);
            SimpleAssert.eq(123, decoded.tag.getInteger("main"), "发送前快照隔离主值编辑");
            SimpleAssert.eq(
                2048,
                decoded.tag.getCompoundTag("channels")
                    .getInteger("custom.future_mod"),
                "未知通道经过真压缩NBT传输");
            SimpleAssert.eq(0, bytes.readableBytes(), "载荷完整读完");
        } finally {
            bytes.release();
        }
    }

    private static void badLengths() {
        for (int length : new int[] { -1, 1048577, 4 }) {
            ByteBuf bytes = Unpooled.buffer();
            try {
                bytes.writeInt(length);
                boolean rejected = false;
                try {
                    new HologramNetwork.Action().fromBytes(bytes);
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                }
                SimpleAssert.that(rejected, "恶意/截断长度被拒绝 " + length);
            } finally {
                bytes.release();
            }
        }
    }

    private static void planProtocol() {
        NBTTagCompound state = new NBTTagCompound();
        state.setString("generation", "server-generation");
        state.setLong("planRevision", 9007199254740993L);
        state.setLong("uiSequence", 4294967297L);
        state.setString("phase", "PREPARE");
        state.setIntArray("pending", new int[] { 35 });
        state.setLong("due", 4294967305L);
        state.setInteger("layerElapsed", 0);
        state.setInteger("layerLeadRemaining", 5);
        state.setIntArray("ackIndices", new int[] { 2, 8, 35 });
        state.setIntArray("ackSuccesses", new int[] { 1, 0, 1 });
        state.setInteger("job", 5);
        HologramNetwork.State packet = new HologramNetwork.State(state);
        state.setLong("planRevision", 1);
        ByteBuf bytes = Unpooled.buffer();
        try {
            packet.toBytes(bytes);
            HologramNetwork.State decoded = new HologramNetwork.State();
            decoded.fromBytes(bytes);
            SimpleAssert.that(
                decoded.tag.getLong("planRevision") == 9007199254740993L,
                "plan revision retains full long precision and sender snapshot");
            SimpleAssert
                .that(decoded.tag.getLong("uiSequence") == 4294967297L, "configuration receipt retains long sequence");
            SimpleAssert.eq(
                35,
                decoded.tag.getIntArray("pending")[0],
                "prepare carries final EBF cell index including controller anchor");
            SimpleAssert
                .that(decoded.tag.getLong("due") == 4294967305L, "prepare due tick retains full long precision");
            SimpleAssert.eq(5, decoded.tag.getInteger("layerLeadRemaining"), "paused PREPARE retains remaining lead");
            SimpleAssert.eq(3, decoded.tag.getIntArray("ackIndices").length, "ACK retains all same-tick cells");
            SimpleAssert.eq(0, decoded.tag.getIntArray("ackSuccesses")[1], "failed cell is not animated as success");
            SimpleAssert.eq(5, decoded.tag.getInteger("job"), "partial completion remains distinct from complete");
        } finally {
            bytes.release();
        }
    }

    private static void inertCapture() {
        SimpleAssert.that(
            !HologramCapture.piece(new Object(), "main", new ItemStack(Items.feather), true, 1, 1, 0),
            "普通construct没有被截获");
        SimpleAssert.that(!HologramCapture.hint(null, 0, 0, 0, net.minecraft.init.Blocks.stone, 0), "普通方块提示没有被吞");
        SimpleAssert.that(!HologramCapture.iconHint(), "普通图标提示没有被吞");
    }
}
