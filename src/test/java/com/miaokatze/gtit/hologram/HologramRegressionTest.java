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
        TestRunner.run(HologramRegressionTest.class, cases);
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
