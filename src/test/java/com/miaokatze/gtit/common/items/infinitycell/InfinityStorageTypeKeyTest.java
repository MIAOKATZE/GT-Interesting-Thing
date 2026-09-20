package com.miaokatze.gtit.common.items.infinitycell;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/**
 * 无限元件外置存储的通道分桶与旧档兼容回归套件，零依赖纯 Java 断言，入口为 {@code main}
 * （与 {@code DefaultTradeSyncTest}、{@code InfinityNestedCellProbeTest} 同模式）。
 * <p>
 * 覆盖：
 * <ul>
 * <li>同一 UUID 的不同通道落在不同桶上（多通道单元的前提）</li>
 * <li>旧存档外层键 disklist / fluid_disklist 与 v2 键同时被写出（玩家回滚旧 jar 仍能读回物品/流体）</li>
 * <li>读档时通道尚未注册的第三方桶原样保留到写档，不在 mod 缺席期间被抹掉</li>
 * </ul>
 * <p>
 * 可测边界：本仓 test source set 以 {@code transitive = false} 引入 AE2，
 * {@code IAEStackType} 的签名依赖 fastutil 而无法被实例化或代理（同 InfinityNestedCellProbeTest 的实测结论），
 * 且物品/流体条目反序列化需要真实 ItemRegistry。因此这里只喂空条目与不透明 payload，
 * 断言「键与桶形状」；真实存量往返与三通道端到端行为由实机验证。
 */
public class InfinityStorageTypeKeyTest {

    private static final String MARKER = "marker";

    public static void main(String[] args) {
        final Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("同一UUID不同通道分桶", InfinityStorageTypeKeyTest::channelKeysAreDistinct);
        cases.put("写出同时携带三个外层键", InfinityStorageTypeKeyTest::writesAllThreeOuterKeys);
        cases.put("未注册通道原样保留", InfinityStorageTypeKeyTest::keepsUnresolvedTypedBucket);
        cases.put("旧档键名字面量未被改动", InfinityStorageTypeKeyTest::legacyKeyLiteralsUnchanged);
        TestRunner.run(InfinityStorageTypeKeyTest.class, cases);
    }

    private static void channelKeysAreDistinct() {
        final String uuid = UUID.randomUUID()
            .toString();
        final String item = StorageManager.keyOf(uuid, "item");
        final String fluid = StorageManager.keyOf(uuid, "fluid");
        final String essentia = StorageManager.keyOf(uuid, "essentia");
        SimpleAssert.that(!item.equals(fluid), "物品桶与流体桶必须分开");
        SimpleAssert.that(!fluid.equals(essentia), "流体桶与第三方桶必须分开");
        SimpleAssert.eq(item, StorageManager.keyOf(uuid, "item"), "键派生必须稳定");
        SimpleAssert.that(item.startsWith(uuid), "键以 UUID 打头，便于按元件聚合");
    }

    private static void writesAllThreeOuterKeys() {
        final StorageManager manager = new StorageManager("gtit-test");
        manager.readFromNBT(new NBTTagCompound());
        final NBTTagCompound out = new NBTTagCompound();
        manager.writeToNBT(out);
        SimpleAssert.that(out.hasKey(InfinityCellConstants.DISKLIST), "旧存档必须继续带 disklist");
        SimpleAssert.that(out.hasKey(InfinityCellConstants.FLUID_DISKLIST), "旧存档必须继续带 fluid_disklist");
        SimpleAssert.that(out.hasKey(InfinityCellConstants.TYPED_DISKLIST), "第三方通道必须有 disklist_v2 落点");
    }

    private static void keepsUnresolvedTypedBucket() {
        final String uuid = UUID.randomUUID()
            .toString();
        final StorageManager manager = new StorageManager("gtit-test");
        manager.readFromNBT(v2Tag(uuid, "nosuchchannel"));

        SimpleAssert.eq(1, manager.unresolvedCount(), "未注册通道应进入待保留桶");

        final NBTTagCompound out = new NBTTagCompound();
        manager.writeToNBT(out);
        final NBTTagList written = out.getTagList(InfinityCellConstants.TYPED_DISKLIST, 10);
        SimpleAssert.eq(1, written.tagCount(), "待保留桶必须原样写出");
        final NBTTagCompound disk = written.getCompoundTagAt(0);
        SimpleAssert.eq(uuid, disk.getString(InfinityCellConstants.DISKUUID), "UUID 不得改变");
        SimpleAssert.eq("nosuchchannel", disk.getString(InfinityCellConstants.DISK_TYPEID), "typeId 不得丢失");
        SimpleAssert.eq(
            1,
            disk.getTagList(InfinityCellConstants.DISKDATA, 10)
                .tagCount(),
            "条目数量不得丢失");
        SimpleAssert.eq(
            7,
            disk.getTagList(InfinityCellConstants.DISKDATA, 10)
                .getCompoundTagAt(0)
                .getInteger(MARKER),
            "条目内容不得丢失");

        // 再读一次仍应在（写→读→写闭环，防mod 反复缺席时数据被逐步抹平）
        manager.readFromNBT(out);
        final NBTTagCompound again = new NBTTagCompound();
        manager.writeToNBT(again);
        SimpleAssert.eq(
            1,
            again.getTagList(InfinityCellConstants.TYPED_DISKLIST, 10)
                .tagCount(),
            "二次闭环后仍未注册通道不得消失");
    }

    /**
     * 键名一旦改动，v1.8.21 及更早存档就直接读空 —— 这里按字面量钉死。
     * 真实条目（物品/流体堆栈）的读→写→读往返需要 ItemRegistry，纯 JVM 下不可得，由实机验证。
     */
    private static void legacyKeyLiteralsUnchanged() {
        SimpleAssert.eq("diskuuid", InfinityCellConstants.DISKUUID, "UUID 键名");
        SimpleAssert.eq("disklist", InfinityCellConstants.DISKLIST, "物品外层键名");
        SimpleAssert.eq("diskdata", InfinityCellConstants.DISKDATA, "物品条目内层键名");
        SimpleAssert.eq("fluid_disklist", InfinityCellConstants.FLUID_DISKLIST, "流体内外层同名是历史形状，改不得");
        SimpleAssert.eq("disklist_v2", InfinityCellConstants.TYPED_DISKLIST, "第三方通道外层键名");
        SimpleAssert.eq("typeid", InfinityCellConstants.DISK_TYPEID, "第三方通道归属标记键名");
    }

    private static NBTTagCompound v2Tag(String uuid, String typeId) {
        final NBTTagList payload = new NBTTagList();
        final NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(MARKER, 7);
        payload.appendTag(entry);

        final NBTTagCompound disk = new NBTTagCompound();
        disk.setString(InfinityCellConstants.DISKUUID, uuid);
        disk.setString(InfinityCellConstants.DISK_TYPEID, typeId);
        disk.setTag(InfinityCellConstants.DISKDATA, payload);

        final NBTTagList list = new NBTTagList();
        list.appendTag(disk);

        final NBTTagCompound root = new NBTTagCompound();
        root.setTag(InfinityCellConstants.TYPED_DISKLIST, list);
        return root;
    }
}
