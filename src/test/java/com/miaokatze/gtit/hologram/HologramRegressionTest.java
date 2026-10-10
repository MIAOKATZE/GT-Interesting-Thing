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
        cases.put("unadapted_channels_are_editable_and_reach_native_trigger", HologramRegressionTest::openChannels);
        cases.put("packet_snapshot_roundtrip_keeps_all_channel_values", HologramRegressionTest::packetRoundTrip);
        cases.put("hostile_packet_lengths_are_rejected", HologramRegressionTest::badLengths);
        cases.put("capture_hooks_are_inert_outside_collection", HologramRegressionTest::inertCapture);
        cases.put("server_batch_receipt_roundtrip", HologramRegressionTest::planProtocol);
        cases.put("frame_factory_material_and_te_boundary", HologramRegressionTest::frames);
        cases.put("sealed_gt_inventory_fluid_and_position_roundtrip", HologramRegressionTest::sealedRecovery);
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

    private static void channels() {
        ItemStack trigger = new ItemStack(Items.feather, 123);
        SimpleAssert.eq(123, ChannelDataAccessor.getChannelData(trigger, "custom.future_mod"), "未设置通道回退主值");
        ChannelDataAccessor.setChannelData(trigger, "custom.future_mod", 2048);
        SimpleAssert.eq(2048, ChannelDataAccessor.withChannel(trigger, "custom.future_mod").stackSize, "原版子通道构造器看到完整值");
        SimpleAssert.eq(123, trigger.stackSize, "子通道不污染主值");
        ChannelDataAccessor.unsetChannelData(trigger, "custom.future_mod");
        SimpleAssert.eq(123, ChannelDataAccessor.getChannelData(trigger, "custom.future_mod"), "删除通道重新回退主值");
    }

    private static void openChannels() {
        ItemStack trigger = new ItemStack(Items.feather, 123);
        HologramChannelTrace.Scope scope = HologramChannelTrace.begin(null);
        HologramChannelTrace.record(trigger, "custom.future_mod", 123, 1);
        HologramCapabilities.Result capabilities = HologramCapabilities
            .describe(null, trigger, HologramChannelTrace.end(scope));
        NBTTagCompound channels = new NBTTagCompound();
        channels.setInteger("custom.future_mod", 2048);
        channels.setInteger("new_channel", Integer.MAX_VALUE);
        SimpleAssert.that(capabilities.validConfiguration(123, channels), "未知及新建信道允许完整正整数值");
        NBTTagCompound active = capabilities.sanitizeChannels(channels);
        SimpleAssert.eq(2048, active.getInteger("custom.future_mod"), "未适配的已观测信道不被过滤");
        SimpleAssert.eq(Integer.MAX_VALUE, active.getInteger("new_channel"), "新建信道不被静默过滤或截断");
        for (Object id : active.func_150296_c())
            ChannelDataAccessor.setChannelData(trigger, (String) id, active.getInteger((String) id));
        SimpleAssert.eq(2048, ChannelDataAccessor.withChannel(trigger, "custom.future_mod").stackSize, "实际原生构造信号保留手动值");
        SimpleAssert.that(capabilities.encodeOption(channels, "custom.future_mod", 4096), "观测信道可设原始值");
        SimpleAssert.that(capabilities.encodeOption(channels, "custom.future_mod", 0), "删除观测信道恢复继承");
        SimpleAssert.that(!channels.hasKey("custom.future_mod"), "继承不保留显式零值");
        for (int value : new int[] { 0, -1 }) {
            NBTTagCompound invalid = new NBTTagCompound();
            invalid.setInteger("custom.future_mod", value);
            SimpleAssert.that(!capabilities.validConfiguration(1, invalid), "非正整数配置被拒绝");
        }
        NBTTagCompound invalid = new NBTTagCompound();
        invalid.setString("custom.future_mod", "2048");
        SimpleAssert.that(!capabilities.validConfiguration(1, invalid), "字符串不能冒充整数");
        invalid = new NBTTagCompound();
        invalid.setInteger("Bad Name", 1);
        SimpleAssert.that(!capabilities.validConfiguration(1, invalid), "无效名称不进入原生API");
        invalid = new NBTTagCompound();
        for (int i = 0; i < 65; i++) invalid.setInteger("channel_" + i, 1);
        SimpleAssert.that(!capabilities.validConfiguration(1, invalid), "超出64个信道不接受部分配置");
    }

    private static void sealedRecovery() {
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "GT_TileEntityMetaID_Machine");
        tile.setInteger("mID", 25);
        tile.setInteger("x", 100);
        tile.setInteger("y", 40);
        tile.setInteger("z", -100);
        net.minecraft.nbt.NBTTagList inventory = new net.minecraft.nbt.NBTTagList();
        NBTTagCompound contents = new ItemStack(Items.feather, 1).writeToNBT(new NBTTagCompound());
        contents.setInteger("IntSlot", 3);
        contents.setInteger("Count", 512);
        inventory.appendTag(contents);
        tile.setTag("Inventory", inventory);
        NBTTagCompound fluid = new NBTTagCompound();
        fluid.setString("FluidName", "water");
        fluid.setInteger("Amount", 32000);
        tile.setTag("mFluid", fluid);
        NBTTagCompound cover = new NBTTagCompound();
        cover.setInteger("coverID", 120);
        cover.setInteger("coverData", 987);
        tile.setTag("cover", cover);
        NBTTagCompound sealed = HologramRecovery.sealNBT(tile, "gregtech:machine", 0, "gregtech:machine", 25, 25);
        // The live tile and item payload must never share mutable inventory/tank compounds.
        contents.setInteger("Count", 1);
        fluid.setInteger("Amount", 0);
        SimpleAssert.that(
            HologramRecovery.matchesEnvelope(sealed, "gregtech:machine", "gregtech:machine", 25),
            "匹配原设备的完整封存载荷有效");
        NBTTagCompound relocated = HologramRecovery.relocateNBT(sealed, -1, 65, 2);
        SimpleAssert.eq(-1, relocated.getInteger("x"), "放回新位置不能沿用旧坐标");
        SimpleAssert.eq(65, relocated.getInteger("y"), "放回新高度");
        SimpleAssert.eq(2, relocated.getInteger("z"), "放回新Z");
        SimpleAssert.eq(
            512,
            relocated.getTagList("Inventory", 10)
                .getCompoundTagAt(0)
                .getInteger("Count"),
            "大数量库存未被字节截断");
        SimpleAssert.eq(
            32000,
            relocated.getCompoundTag("mFluid")
                .getInteger("Amount"),
            "流体量独立封存");
        SimpleAssert.eq(
            987,
            relocated.getCompoundTag("cover")
                .getInteger("coverData"),
            "覆盖板配置独立封存");
        relocated.getCompoundTag("mFluid")
            .setInteger("Amount", 1);
        SimpleAssert.eq(
            32000,
            sealed.getCompoundTag("tile")
                .getCompoundTag("mFluid")
                .getInteger("Amount"),
            "恢复不能修改库存中物品载荷");
        SimpleAssert.eq(
            100,
            sealed.getCompoundTag("tile")
                .getInteger("x"),
            "原封存坐标只读");
        SimpleAssert
            .that(!HologramRecovery.matchesEnvelope(sealed, "gregtech:frame", "gregtech:machine", 25), "不将机器封存载荷放到框架");
        SimpleAssert
            .that(!HologramRecovery.matchesEnvelope(sealed, "gregtech:machine", "gregtech:machine", 26), "设备ID不同不恢复载荷");
        NBTTagCompound corrupt = (NBTTagCompound) sealed.copy();
        corrupt.getCompoundTag("tile")
            .setInteger("mID", 26);
        SimpleAssert.that(
            !HologramRecovery.matchesEnvelope(corrupt, "gregtech:machine", "gregtech:machine", 25),
            "封存头与实体ID不符拒绝");
        corrupt = (NBTTagCompound) sealed.copy();
        corrupt.getCompoundTag("tile")
            .removeTag("Inventory");
        SimpleAssert.that(
            !HologramRecovery.matchesEnvelope(corrupt, "gregtech:machine", "gregtech:machine", 25),
            "缺库存载荷不作为空库存恢复");
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
        state.setString("phase", "COMPLETE");
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
            SimpleAssert.that("COMPLETE".equals(decoded.tag.getString("phase")), "批量完成回执无需预告或动画状态");
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
