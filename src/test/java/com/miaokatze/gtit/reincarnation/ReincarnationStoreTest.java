package com.miaokatze.gtit.reincarnation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle.CycleState;
import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle.ItemRef;
import com.miaokatze.gtit.reincarnation.core.ReincarnationFingerprints;
import com.miaokatze.gtit.reincarnation.core.ReincarnationSaveGuard;
import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;
import com.miaokatze.gtit.reincarnation.storage.ReincarnationWorldData;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/**
 * 周目系统核心（ReincarnationCycle/ReincarnationStore/ReincarnationWorldData）的
 * 纯 JVM 测试：v2 许可格式往返（UUID/指纹/投胎信箱）、投胎信箱四语义
 * （写/读/成功清空/失败保留）与发放幂等（重复发放不复制、grantInFlight 标记）、
 * 最后仁慈路径（篡改/魔数/UUID 不匹配/删档）、旧全局 v1 格式的登录只读兼容视图与
 * consume-once 迁移（一次不重复/损坏走仁慈/UUID 隔离）、每存档 WorldData NBT 往返、
 * 单轮回状态机互斥与非法迁移、指纹稳定性、原子写。
 * <p>
 * 每用例使用独立临时目录（系统 temp，退出前尽力清理），互不串档。
 * 因 GTNH convention 未随 test source set 提供测试框架依赖（见
 * {@code SimpleAssert} javadoc），本类为零依赖断言套件，入口为 {@code main}。
 */
public class ReincarnationStoreTest {

    private static final String UUID_A = "11111111-2222-3333-4444-555555555555";
    private static final String UUID_B = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final ItemRef DIAMOND = new ItemRef("minecraft:diamond", 0);
    private static final ItemRef CIRCUIT = new ItemRef("gregtech:gt.metaitem.01", 32760);

    private static final List<Path> TEMP_DIRS = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("newFormatLicenseRoundTrip", () -> runChecked(ReincarnationStoreTest::newFormatLicenseRoundTrip));
        cases.put(
            "globalKeepsOnlyLicenseFields",
            () -> runChecked(ReincarnationStoreTest::globalKeepsOnlyLicenseFields));
        cases.put("mailboxFourSemantics", () -> runChecked(ReincarnationStoreTest::mailboxFourSemantics));
        cases.put("grantIdempotentNoDuplicate", () -> runChecked(ReincarnationStoreTest::grantIdempotentNoDuplicate));
        cases.put("grantInFlightMarker", () -> runChecked(ReincarnationStoreTest::grantInFlightMarker));
        cases.put("legacyLoginLicenseView", () -> runChecked(ReincarnationStoreTest::legacyLoginLicenseView));
        cases.put("legacyMigrationConsumeOnce", () -> runChecked(ReincarnationStoreTest::legacyMigrationConsumeOnce));
        cases.put(
            "legacyMigrationDepositedAndMercy",
            () -> runChecked(ReincarnationStoreTest::legacyMigrationDepositedAndMercy));
        cases.put(
            "legacyReadV1NonDestructiveThenConsume",
            () -> runChecked(ReincarnationStoreTest::legacyReadV1NonDestructiveThenConsume));
        cases.put(
            "tamperedCiphertextTreatedAsAbsent",
            () -> runChecked(ReincarnationStoreTest::tamperedCiphertextTreatedAsAbsent));
        cases.put("nonMagicFileTreatedAsFresh", () -> runChecked(ReincarnationStoreTest::nonMagicFileTreatedAsFresh));
        cases.put("uuidMismatchTreatedAsAbsent", () -> runChecked(ReincarnationStoreTest::uuidMismatchTreatedAsAbsent));
        cases.put(
            "fingerprintStableAndDistinct",
            () -> runChecked(ReincarnationStoreTest::fingerprintStableAndDistinct));
        cases.put("illegalTransitionsRejected", () -> runChecked(ReincarnationStoreTest::illegalTransitionsRejected));
        cases.put("pendingCycleMutex", () -> runChecked(ReincarnationStoreTest::pendingCycleMutex));
        cases.put(
            "claimGrantClearsPendingKeepsFingerprints",
            () -> runChecked(ReincarnationStoreTest::claimGrantClearsPendingKeepsFingerprints));
        cases.put("deletedFileLoadsFresh", () -> runChecked(ReincarnationStoreTest::deletedFileLoadsFresh));
        cases.put("worldDataNbtRoundTrip", () -> runChecked(ReincarnationStoreTest::worldDataNbtRoundTrip));
        cases.put(
            "atomicWriteNoTempLeftAndOverwriteSafe",
            () -> runChecked(ReincarnationStoreTest::atomicWriteNoTempLeftAndOverwriteSafe));
        cases.put("withdrawAdjustsPendingItems", () -> runChecked(ReincarnationStoreTest::withdrawAdjustsPendingItems));
        cases.put(
            "confirmExecutedThenStaleCloseSaveKeepsLicense",
            () -> runChecked(ReincarnationStoreTest::confirmExecutedThenStaleCloseSaveKeepsLicense));
        try {
            TestRunner.run(ReincarnationStoreTest.class, cases);
        } finally {
            cleanup();
        }
    }

    /** 用例体允许抛受检异常（文件写入） */
    private static void runChecked(CheckedCase c) {
        try {
            c.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private interface CheckedCase {

        void run() throws Exception;
    }

    // ==================== v2 许可格式与投胎信箱 ====================

    /**
     * D1 新格式往返：EXECUTED（投胎信箱）+ 指纹 + 每存档进度分离——
     * 全局文件往返保留许可字段；进度字段不再进全局文件（归 WorldData）。
     */
    static void newFormatLicenseRoundTrip() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        cycle.deposit(Arrays.asList(DIAMOND, CIRCUIT));
        // 确认推进 EXECUTED：与写信箱同一 save 事务（容器/Handler 路径的模型等价）
        String fingerprint = cycle.confirmReincarnation(42L);
        cycle.recordHullConsumption(2, 9);
        cycle.unlockColumn(2);
        cycle.setUnlockedRows(2);
        store.save(cycle);

        ReincarnationCycle loaded = store.load(UUID_A);
        SimpleAssert.eq(cycle.getUuid(), loaded.getUuid(), "uuid round-trip");
        SimpleAssert.eq(CycleState.EXECUTED, loaded.getCycleState(), "投胎信箱在档 → EXECUTED");
        SimpleAssert.that(loaded.canClaimGrant(), "EXECUTED 可领取（信箱即待发放）");
        assertMailbox(loaded, DIAMOND, CIRCUIT);
        SimpleAssert.that(loaded.hasFingerprint(fingerprint), "永久指纹 round-trip");
        // D1：进度字段不在全局文件——store 单独读回为缺省值（由 WorldData 承载，另测）
        SimpleAssert.that(allZero(loaded.getHullProgress()), "进度不随全局文件往返（每存档隔离）");
        SimpleAssert.eq(1, loaded.getUnlockedRows(), "行解锁不随全局文件往返");

        // 成功清空（claimGrant + save）：信箱清空、指纹保留，可继续下一轮状态机
        loaded.claimGrant();
        store.save(loaded);
        ReincarnationCycle reloaded = store.load(UUID_A);
        SimpleAssert.eq(CycleState.IDLE, reloaded.getCycleState(), "信箱清空后回 IDLE");
        SimpleAssert.eq(
            0,
            reloaded.getPendingItems()
                .size(),
            "信箱清空后待发放为空");
        SimpleAssert.that(reloaded.hasFingerprint(fingerprint), "信箱清空后永久指纹保留");
    }

    /** D1：IDLE/DEPOSITED 周目不向全局文件写任何待发放/进度内容 */
    static void globalKeepsOnlyLicenseFields() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        cycle.addFingerprint(ReincarnationFingerprints.fingerprintOf(7L));
        cycle.deposit(Arrays.asList(DIAMOND));
        cycle.recordHullConsumption(5, 16);
        cycle.unlockColumn(5);
        store.save(cycle); // DEPOSITED：寄存快照归 WorldData，全局不写

        ReincarnationCycle loaded = store.load(UUID_A);
        SimpleAssert.eq(CycleState.IDLE, loaded.getCycleState(), "全局文件无 DEPOSITED 态");
        SimpleAssert.eq(
            0,
            loaded.getPendingItems()
                .size(),
            "全局文件无寄存物品（归每存档 WorldData）");
        SimpleAssert.that(allZero(loaded.getHullProgress()), "全局文件无外壳进度");
        SimpleAssert.that(noneUnlocked(loaded.getUnlockedColumns()), "全局文件无列解锁");
        SimpleAssert.that(loaded.hasFingerprint(ReincarnationFingerprints.fingerprintOf(7L)), "许可字段（指纹）保留");
    }

    /** 投胎信箱四语义：写（确认同事务）/读/成功清空/失败保留（发放清空只能晚于成功发放） */
    static void mailboxFourSemantics() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);

        // —— 写：确认轮回推进 EXECUTED，同一 save 事务写入物品快照（写）——
        cycle.deposit(Arrays.asList(DIAMOND));
        cycle.confirmReincarnation(11L);
        store.save(cycle);

        // —— 读：load 读出信箱物品（读）——
        ReincarnationCycle read = store.load(UUID_A);
        assertMailbox(read, DIAMOND);

        // —— 失败保留：发放收束（claimGrant）后未落盘（发放未完成/崩溃窗口），磁盘信箱仍在 ——
        read.claimGrant(); // 仅内存清空，未 save
        ReincarnationCycle afterFailed = store.load(UUID_A);
        SimpleAssert.eq(CycleState.EXECUTED, afterFailed.getCycleState(), "失败保留：未落盘的发放不影响磁盘信箱");
        assertMailbox(afterFailed, DIAMOND);

        // —— 成功清空：成功发放（交付/溢出落地）后才 claimGrant + save 清空 ——
        afterFailed.claimGrant();
        store.save(afterFailed);
        ReincarnationCycle afterSuccess = store.load(UUID_A);
        SimpleAssert.eq(CycleState.IDLE, afterSuccess.getCycleState(), "成功清空：发放完成后信箱清空");
        SimpleAssert.eq(
            0,
            afterSuccess.getPendingItems()
                .size(),
            "成功清空：信箱为空");
    }

    /** 幂等防复制：重复保存同一 EXECUTED 信箱不复制条目；发放完成后再次执行领取路径跳过 */
    static void grantIdempotentNoDuplicate() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        cycle.deposit(Arrays.asList(DIAMOND));
        cycle.confirmReincarnation(21L);
        store.save(cycle);
        // 重复写同一信箱（模拟登录链路/续跑重复触发保存）
        store.save(cycle);
        store.save(store.load(UUID_A));
        assertMailbox(store.load(UUID_A), DIAMOND); // 恰 1 条，无复制

        // 发放完成（清信箱）后：可领取消失 → 发放路径 ① 确认可领 即幂等跳过，不再复制发放
        ReincarnationCycle done = store.load(UUID_A);
        done.claimGrant();
        store.save(done);
        ReincarnationCycle after = store.load(UUID_A);
        SimpleAssert.that(!after.canClaimGrant(), "发放完成后无可领取（重复发放路径幂等跳过）");
        SimpleAssert.eq(
            0,
            after.getPendingItems()
                .size(),
            "发放完成后信箱为空（不复制）");
    }

    /** grantInFlight 幂等标记：无信箱不可置位；有信箱可置位/查询；清信箱自动复位 */
    static void grantInFlightMarker() throws Exception {
        ReincarnationStore store = newStore();
        // 无信箱（文件从未存在）：查询 false，置位无副作用
        SimpleAssert.that(!store.isGrantInFlight(UUID_A), "无信箱时标记为 false");
        store.markGrantInFlight(UUID_A);
        SimpleAssert.that(!store.isGrantInFlight(UUID_A), "无信箱置位无副作用（失败不写口径）");

        // 有信箱：置位 → true（幂等置位）
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        cycle.deposit(Arrays.asList(CIRCUIT));
        cycle.confirmReincarnation(31L);
        store.save(cycle);
        SimpleAssert.that(!store.isGrantInFlight(UUID_A), "刚写入的信箱未置位");
        store.markGrantInFlight(UUID_A);
        store.markGrantInFlight(UUID_A);
        SimpleAssert.that(store.isGrantInFlight(UUID_A), "置位后标记为 true（幂等重复置位）");
        // 标记置位不影响信箱物品
        assertMailbox(store.load(UUID_A), CIRCUIT);

        // 进度类保存（EXECUTED 态再 save）不丢标记
        store.save(store.load(UUID_A));
        SimpleAssert.that(store.isGrantInFlight(UUID_A), "发放中保存不丢幂等标记");

        // 清信箱（成功发放）→ 标记复位
        ReincarnationCycle done = store.load(UUID_A);
        done.claimGrant();
        store.save(done);
        SimpleAssert.that(!store.isGrantInFlight(UUID_A), "清信箱后标记复位（完成标记）");
    }

    // ==================== 旧全局 v1：登录只读兼容 + consume-once 迁移 ====================

    /**
     * v1 登录只读兼容视图：未迁移的旧档在登录链路（load）即可读到许可字段——
     * 指纹命中（倒计时判定）与 EXECUTED 信箱（发放判定）不依赖迁移先行完成。
     */
    static void legacyLoginLicenseView() throws Exception {
        Path dir = tempDir();
        ReincarnationStore store = new ReincarnationStore(dir.toFile());
        String fp = ReincarnationFingerprints.fingerprintOf(63L);
        Set<String> fps = new LinkedHashSet<>();
        fps.add(fp);
        ReincarnationStore.writeLegacyV1Fixture(
            dir.toFile(),
            UUID_A,
            CycleState.EXECUTED,
            Arrays.asList(DIAMOND, CIRCUIT),
            new int[] { 1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            boolArray(0),
            2,
            fps);

        ReincarnationCycle viewed = store.load(UUID_A); // 不消费（登录视角）
        SimpleAssert.eq(CycleState.EXECUTED, viewed.getCycleState(), "v1 EXECUTED → 许可视图 EXECUTED");
        assertMailbox(viewed, DIAMOND, CIRCUIT);
        SimpleAssert.that(viewed.hasFingerprint(fp), "v1 指纹经登录视图可见（倒计时判定可用）");
        SimpleAssert.that(allZero(viewed.getHullProgress()), "登录视图不带进度字段（进度归 WorldData/迁移）");
        // 只读：未触发迁移，文件仍为 v1（迁移后 consume 返回非 null）
        SimpleAssert.that(store.consumeLegacyV1(UUID_A) != null, "登录视图未消费旧档（迁移仍可一次性完成）");
    }

    /** consume-once 迁移：EXECUTED 旧档 → 进度拆出 + 信箱转入 v2；二次消费返回 null（不重复） */
    static void legacyMigrationConsumeOnce() throws Exception {
        Path dir = tempDir();
        ReincarnationStore store = new ReincarnationStore(dir.toFile());
        String fp = ReincarnationFingerprints.fingerprintOf(99L);
        Set<String> fps = new LinkedHashSet<>();
        fps.add(fp);
        int[] hull = new int[15];
        hull[3] = 16;
        ReincarnationStore.writeLegacyV1Fixture(
            dir.toFile(),
            UUID_A,
            CycleState.EXECUTED,
            Arrays.asList(DIAMOND),
            hull,
            boolArray(3),
            3,
            fps);

        // 首次消费：返回 v1 拆分视图（进度 → WorldData；EXECUTED pending → 信箱）
        ReincarnationStore.LegacyRecord legacy = store.consumeLegacyV1(UUID_A);
        SimpleAssert.that(legacy != null, "v1 载荷首次消费返回迁移视图");
        SimpleAssert.that(Arrays.equals(hull, legacy.hullProgress), "迁移视图携带 15 列外壳进度");
        SimpleAssert.that(legacy.unlockedColumns[3], "迁移视图携带列解锁");
        SimpleAssert.eq(3, legacy.unlockedRows, "迁移视图携带行解锁数");
        SimpleAssert.eq(0, legacy.depositedItems.size(), "EXECUTED 旧档无寄存快照（信箱转入 v2）");
        SimpleAssert.eq(1, legacy.mailboxItems.size(), "EXECUTED pendingItems 转入投胎信箱");
        SimpleAssert.eq(DIAMOND, legacy.mailboxItems.get(0), "信箱转入条目内容一致");

        // 全局文件已改写 v2：许可字段保留（指纹/信箱），进度字段剥离
        ReincarnationCycle after = store.load(UUID_A);
        SimpleAssert.eq(CycleState.EXECUTED, after.getCycleState(), "迁移后信箱在档（EXECUTED）");
        assertMailbox(after, DIAMOND);
        SimpleAssert.that(after.hasFingerprint(fp), "迁移保留永久指纹");
        SimpleAssert.that(allZero(after.getHullProgress()), "迁移后全局无进度字段");

        // 二次消费：返回 null（consume-once，不重复合并）
        SimpleAssert.that(store.consumeLegacyV1(UUID_A) == null, "第二次消费返回 null（consume-once）");
        assertMailbox(store.load(UUID_A), DIAMOND); // 重复消费尝试不破坏 v2 信箱
    }

    /**
     * 两阶段迁移的 Store 侧时序：readLegacyV1 非破坏可重复（模拟落盘前崩溃可重试），
     * consumeLegacyV1 之后 v1 载荷不再可读、全局收敛为 v2 许可字段。
     */
    static void legacyReadV1NonDestructiveThenConsume() throws Exception {
        Path dir = tempDir();
        ReincarnationStore store = new ReincarnationStore(dir.toFile());
        int[] hull = new int[15];
        hull[0] = 16;
        hull[1] = 7;
        ReincarnationStore.writeLegacyV1Fixture(
            dir.toFile(),
            UUID_A,
            CycleState.DEPOSITED,
            Arrays.asList(DIAMOND, CIRCUIT),
            hull,
            boolArray(0),
            2,
            new LinkedHashSet<String>());

        ReincarnationStore.LegacyRecord first = store.readLegacyV1(UUID_A);
        SimpleAssert.that(first != null, "readLegacyV1 读到 v1 载荷");
        SimpleAssert.eq(16, first.hullProgress[0], "非破坏读携带列0外壳进度");
        SimpleAssert.eq(7, first.hullProgress[1], "非破坏读携带列1外壳进度");
        SimpleAssert.that(first.unlockedColumns[0], "非破坏读携带列0解锁");
        SimpleAssert.eq(2, first.depositedItems.size(), "DEPOSITED 快照进当档寄存视图");
        SimpleAssert.eq(0, first.mailboxItems.size(), "DEPOSITED 不进信箱");

        // 模拟"WorldData 落盘前崩溃"：全局文件未动，重复非破坏读仍可重试且视图一致
        ReincarnationStore.LegacyRecord retry = store.readLegacyV1(UUID_A);
        SimpleAssert.that(retry != null, "重复非破坏读仍返回 v1（崩溃窗口可重试）");
        SimpleAssert.eq(2, retry.depositedItems.size(), "重试读视图与首读一致");
        SimpleAssert.eq(16, retry.hullProgress[0], "重试读携带进度一致");

        // 落盘后破坏性消费：此后 readLegacyV1 返回 null，全局只剩许可字段
        SimpleAssert.that(store.consumeLegacyV1(UUID_A) != null, "consumeLegacyV1 正常消费");
        SimpleAssert.that(store.readLegacyV1(UUID_A) == null, "消费后 v1 载荷不再可读");
        ReincarnationCycle after = store.load(UUID_A);
        SimpleAssert.eq(CycleState.IDLE, after.getCycleState(), "DEPOSITED 消费后全局不再持有寄存状态");
        SimpleAssert.that(allZero(after.getHullProgress()), "消费后全局无进度字段");
    }

    /** 迁移的寄存拆分与仁慈边界：DEPOSITED 快照归当档；损坏/UUID 不匹配返回 null 且不改写文件 */
    static void legacyMigrationDepositedAndMercy() throws Exception {
        // DEPOSITED：pendingItems → 寄存快照（WorldData 侧），不进信箱
        Path dir = tempDir();
        ReincarnationStore store = new ReincarnationStore(dir.toFile());
        ReincarnationStore.writeLegacyV1Fixture(
            dir.toFile(),
            UUID_A,
            CycleState.DEPOSITED,
            Arrays.asList(CIRCUIT),
            new int[15],
            boolArray(-1),
            1,
            new LinkedHashSet<String>());
        ReincarnationStore.LegacyRecord legacy = store.consumeLegacyV1(UUID_A);
        SimpleAssert.eq(1, legacy.depositedItems.size(), "DEPOSITED pendingItems 转入寄存快照");
        SimpleAssert.eq(0, legacy.mailboxItems.size(), "DEPOSITED 旧档无信箱");
        SimpleAssert.eq(
            CycleState.IDLE,
            store.load(UUID_A)
                .getCycleState(),
            "迁移后全局无 DEPOSITED 态");

        // 损坏 v1（篡改密文）：最后仁慈 → null 且文件保持原样（不删除、不改写）
        Path damagedDir = tempDir();
        ReincarnationStore damaged = new ReincarnationStore(damagedDir.toFile());
        ReincarnationStore.writeLegacyV1Fixture(
            damagedDir.toFile(),
            UUID_A,
            CycleState.IDLE,
            new ArrayList<ItemRef>(),
            new int[15],
            boolArray(-1),
            1,
            new LinkedHashSet<String>());
        Path file = damaged.getFile()
            .toPath();
        byte[] bytes = Files.readAllBytes(file);
        bytes[4] ^= 0x5A;
        Files.write(file, bytes);
        SimpleAssert.that(damaged.consumeLegacyV1(UUID_A) == null, "损坏 v1 走最后仁慈（null）");
        SimpleAssert.that(Files.exists(file), "损坏文件不被迁移删除/改写（由下次 save 原子覆盖）");

        // UUID 不匹配：按 UUID 隔离 → null（其他玩家的旧档不迁移进本档）
        Path mismatchDir = tempDir();
        ReincarnationStore mismatch = new ReincarnationStore(mismatchDir.toFile());
        ReincarnationStore.writeLegacyV1Fixture(
            mismatchDir.toFile(),
            UUID_A,
            CycleState.IDLE,
            new ArrayList<ItemRef>(),
            new int[15],
            boolArray(-1),
            1,
            new LinkedHashSet<String>());
        SimpleAssert.that(mismatch.consumeLegacyV1(UUID_B) == null, "UUID 不匹配不迁移（按 UUID 隔离）");
    }

    // ==================== 最后仁慈路径 / 原子写 ====================

    static void tamperedCiphertextTreatedAsAbsent() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        cycle.deposit(Arrays.asList(DIAMOND));
        cycle.unlockColumn(5);
        store.save(cycle);

        // 篡改首个密文字节（跳过 4 字节魔数）
        Path file = store.getFile()
            .toPath();
        byte[] bytes = Files.readAllBytes(file);
        bytes[4] ^= 0x5A;
        Files.write(file, bytes);

        assertFresh(store.load(UUID_A), "篡改密文后");
        SimpleAssert.that(
            store.getFile()
                .exists(),
            "篡改后的旧文件不被 load 删除（由下次 save 原子覆盖）");
    }

    static void nonMagicFileTreatedAsFresh() throws Exception {
        ReincarnationStore store = newStore();
        Files.createDirectories(
            store.getFile()
                .toPath()
                .getParent());
        Files.write(
            store.getFile()
                .toPath(),
            "plain json hand-edited by player".getBytes(StandardCharsets.UTF_8));
        assertFresh(store.load(UUID_A), "魔数不符（明文手改文件）");
    }

    static void uuidMismatchTreatedAsAbsent() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        cycle.deposit(Arrays.asList(CIRCUIT));
        cycle.confirmReincarnation(5L);
        store.save(cycle);

        ReincarnationCycle loadedForB = store.load(UUID_B);
        assertFresh(loadedForB, "UUID 不匹配");
        SimpleAssert.eq(UUID_B, loadedForB.getUuid(), "兜底记录归属请求 UUID");
        SimpleAssert.that(!store.isGrantInFlight(UUID_B), "UUID 不匹配时幂等标记按无信箱处理");
    }

    // ==================== 状态机（红线：EXECUTED 锁语义不变） ====================

    static void fingerprintStableAndDistinct() {
        String fp1 = ReincarnationFingerprints.fingerprintOf(42L);
        String fp2 = ReincarnationFingerprints.fingerprintOf(42L);
        String fp3 = ReincarnationFingerprints.fingerprintOf(43L);
        String fpMax = ReincarnationFingerprints.fingerprintOf(Long.MAX_VALUE);
        SimpleAssert.eq(fp1, fp2, "相同 seed 指纹稳定");
        SimpleAssert.that(!fp1.equals(fp3), "不同 seed 指纹不同");
        SimpleAssert.that(!fp1.equals(fpMax), "不同 seed（边界值）指纹不同");
        SimpleAssert.that(fp1.matches("[0-9a-f]{64}"), "指纹为 64 位小写 hex");
    }

    static void illegalTransitionsRejected() {
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        SimpleAssert.that(cycle.canDeposit(), "新记录 IDLE 可寄存");
        SimpleAssert.that(!cycle.canConfirmReincarnation(), "IDLE 不可确认轮回");
        SimpleAssert.that(!cycle.canClaimGrant(), "IDLE 不可领取奖励");

        expectIllegalState("IDLE 直接领取奖励", () -> cycle.claimGrant());
        expectIllegalState("IDLE 直接确认轮回", () -> cycle.confirmReincarnation(42L));
        expectIllegalArgument("寄存 null 清单", () -> cycle.deposit(null));
        expectIllegalArgument("寄存空清单", () -> cycle.deposit(new ArrayList<ItemRef>()));
        expectIllegalArgument("寄存含 null 元素", () -> cycle.deposit(Arrays.asList(DIAMOND, null)));
        // 非法迁移后状态未被破坏，仍可正常寄存
        SimpleAssert.that(cycle.canDeposit(), "非法迁移被拒后仍保持 IDLE 可寄存");
    }

    static void pendingCycleMutex() {
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        cycle.deposit(Arrays.asList(DIAMOND));

        SimpleAssert.that(!cycle.canDeposit(), "DEPOSITED 不可再次寄存（单轮回互斥）");
        expectIllegalState("DEPOSITED 期间再次 deposit", () -> cycle.deposit(Arrays.asList(CIRCUIT)));

        String fingerprint = cycle.confirmReincarnation(100L);
        SimpleAssert.eq(CycleState.EXECUTED, cycle.getCycleState(), "DEPOSITED → EXECUTED");

        SimpleAssert.that(!cycle.canConfirmReincarnation(), "EXECUTED 不可再次确认");
        expectIllegalState("EXECUTED 期间再次 confirmReincarnation", () -> cycle.confirmReincarnation(101L));
        SimpleAssert.that(!cycle.canDeposit(), "EXECUTED 不可寄存（单轮回互斥）");
        expectIllegalState("EXECUTED 期间 deposit", () -> cycle.deposit(Arrays.asList(DIAMOND)));
        SimpleAssert.that(cycle.hasFingerprint(fingerprint), "被拒的第二次确认不产生副作用（指纹仍只有首轮）");

        cycle.claimGrant();
        SimpleAssert.that(cycle.canDeposit(), "领取后回到 IDLE，互斥解除");
        cycle.deposit(Arrays.asList(CIRCUIT));
        SimpleAssert.eq(CycleState.DEPOSITED, cycle.getCycleState(), "互斥解除后可重新寄存");
    }

    static void claimGrantClearsPendingKeepsFingerprints() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        cycle.deposit(Arrays.asList(DIAMOND, CIRCUIT));

        String fingerprint = cycle.confirmReincarnation(7L);
        List<ItemRef> grant = cycle.claimGrant();
        SimpleAssert.eq(2, grant.size(), "领取返回本轮全部寄存物品");
        SimpleAssert.eq(DIAMOND, grant.get(0), "领取物品[0]");
        SimpleAssert.eq(CIRCUIT, grant.get(1), "领取物品[1]");
        SimpleAssert.eq(CycleState.IDLE, cycle.getCycleState(), "领取后回到 IDLE");
        SimpleAssert.eq(
            0,
            cycle.getPendingItems()
                .size(),
            "领取后待领取清空");
        SimpleAssert.that(cycle.hasFingerprint(fingerprint), "领取后永久指纹保留（标记永久化）");

        // 永久指纹持久化（v2 全局仅许可字段）：再次保存加载仍在
        store.save(cycle);
        ReincarnationCycle loaded = store.load(UUID_A);
        SimpleAssert.that(loaded.hasFingerprint(fingerprint), "永久指纹跨存取保留");
        SimpleAssert.eq(
            0,
            loaded.getPendingItems()
                .size(),
            "跨存取待领取仍为空");
        // 同一时间线不可因重复轮回刷奖励：指纹永久集合可判定
        SimpleAssert.that(!loaded.canClaimGrant(), "领取后无可领取奖励");
    }

    static void deletedFileLoadsFresh() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        cycle.deposit(Arrays.asList(DIAMOND));
        cycle.confirmReincarnation(99L);
        store.save(cycle);
        SimpleAssert.that(
            store.getFile()
                .exists(),
            "save 后文件存在");

        Files.delete(
            store.getFile()
                .toPath());
        assertFresh(store.load(UUID_A), "删除文件后（最后仁慈路径）");

        // 从未存在过的文件同样返回全新记录
        ReincarnationStore neverExisted = newStore();
        assertFresh(neverExisted.load(UUID_B), "文件从未存在");
        SimpleAssert.that(!neverExisted.isGrantInFlight(UUID_B), "文件从未存在时幂等标记为 false");
    }

    // ==================== 每存档 WorldData（NBT 往返 + 快照收敛） ====================

    /**
     * D1 每存档载体：NBT 往返（进度/列/行/寄存快照/迁移标记）+ 与 Cycle 的
     * applyTo/saveFrom 双向搬运（含 EXECUTED 时寄存快照清空的收敛语义）。
     */
    static void worldDataNbtRoundTrip() throws Exception {
        ReincarnationWorldData data = new ReincarnationWorldData(ReincarnationWorldData.DATA_NAME);
        int[] hull = new int[ReincarnationCycle.COLUMN_COUNT];
        hull[0] = 8;
        hull[14] = 1;
        data.setHullProgress(hull);
        boolean[] columns = boolArray(0);
        columns[14] = true;
        data.setUnlockedColumns(columns);
        data.setUnlockedRows(3);
        data.setDepositedItems(Arrays.asList(DIAMOND, CIRCUIT));
        SimpleAssert.that(!data.isMigrated(), "新载体迁移标记缺省 false");

        NBTTagCompound tag = new NBTTagCompound();
        data.writeToNBT(tag);
        ReincarnationWorldData restored = new ReincarnationWorldData(ReincarnationWorldData.DATA_NAME);
        restored.readFromNBT(tag);

        SimpleAssert
            .that(Arrays.equals(data.getHullProgress(), restored.getHullProgress()), "15 列外壳进度逐位 NBT round-trip");
        SimpleAssert
            .that(Arrays.equals(data.getUnlockedColumns(), restored.getUnlockedColumns()), "15 列解锁标记逐位 NBT round-trip");
        SimpleAssert.eq(3, restored.getUnlockedRows(), "行解锁数 NBT round-trip");
        SimpleAssert.eq(
            2,
            restored.getDepositedItems()
                .size(),
            "寄存快照条数 NBT round-trip");
        SimpleAssert.eq(
            DIAMOND,
            restored.getDepositedItems()
                .get(0),
            "寄存快照[0] NBT round-trip");
        SimpleAssert.eq(
            CIRCUIT,
            restored.getDepositedItems()
                .get(1),
            "寄存快照[1] NBT round-trip");
        SimpleAssert.that(!restored.isMigrated(), "迁移标记 NBT round-trip");

        // v1.8.6 发放门控：已领取标记缺省 false + true/false 往返（新键，旧档缺省 false 无迁移）
        SimpleAssert.that(!restored.isGrantClaimed(), "发放已领取标记缺省 false（新档形态）");
        data.setGrantClaimed(true);
        SimpleAssert.that(data.isGrantClaimed(), "setter 置位生效");
        NBTTagCompound claimedTag = new NBTTagCompound();
        data.writeToNBT(claimedTag);
        ReincarnationWorldData claimed = new ReincarnationWorldData(ReincarnationWorldData.DATA_NAME);
        claimed.readFromNBT(claimedTag);
        SimpleAssert.that(claimed.isGrantClaimed(), "发放已领取标记 true NBT round-trip");
        claimed.setGrantClaimed(false);
        NBTTagCompound clearedTag = new NBTTagCompound();
        claimed.writeToNBT(clearedTag);
        ReincarnationWorldData cleared = new ReincarnationWorldData(ReincarnationWorldData.DATA_NAME);
        cleared.readFromNBT(clearedTag);
        SimpleAssert.that(!cleared.isGrantClaimed(), "发放已领取标记 false NBT round-trip");

        // applyTo：进度灌入模型 + IDLE 可寄存时 deposit 快照
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        restored.applyTo(cycle);
        SimpleAssert.that(Arrays.equals(restored.getHullProgress(), cycle.getHullProgress()), "applyTo 灌入外壳进度");
        SimpleAssert.that(cycle.isColumnUnlocked(0) && cycle.isColumnUnlocked(14), "applyTo 灌入列解锁（0 与 14 两列）");
        SimpleAssert.eq(3, cycle.getUnlockedRows(), "applyTo 灌入行解锁数");
        SimpleAssert.eq(CycleState.DEPOSITED, cycle.getCycleState(), "applyTo 将寄存快照 deposit 进模型");
        assertMailbox(cycle, DIAMOND, CIRCUIT);

        // saveFrom：EXECUTED（信箱在全局文件）时寄存快照清空（收敛语义）
        cycle.confirmReincarnation(17L);
        restored.saveFrom(cycle);
        SimpleAssert.eq(
            0,
            restored.getDepositedItems()
                .size(),
            "EXECUTED 时寄存快照清空（信箱归全局文件）");
        SimpleAssert.that(Arrays.equals(cycle.getHullProgress(), restored.getHullProgress()), "saveFrom 回写外壳进度");

        // 损坏防御：短数组按前缀吸收（余位缺省）、越界行数收敛，不越界不抛错
        ReincarnationWorldData defensive = new ReincarnationWorldData(ReincarnationWorldData.DATA_NAME);
        NBTTagCompound bad = new NBTTagCompound();
        bad.setIntArray("HullProgress", new int[] { 1, 2, 3 });
        bad.setByteArray("UnlockedColumns", new byte[] { 1 });
        bad.setInteger("UnlockedRows", 99);
        defensive.readFromNBT(bad);
        int[] partial = defensive.getHullProgress();
        SimpleAssert.that(
            partial[0] == 1 && partial[1] == 2 && partial[2] == 3 && allZero(Arrays.copyOfRange(partial, 3, 15)),
            "短数组按前缀吸收、余位缺省（不越界）");
        SimpleAssert.that(defensive.getUnlockedColumns()[0], "短布尔数组按前缀吸收");
        SimpleAssert.eq(1, defensive.getUnlockedRows(), "越界行数按缺省收敛");
    }

    // ==================== 原子写 / 待领取清单调整 ====================

    static void atomicWriteNoTempLeftAndOverwriteSafe() throws Exception {
        ReincarnationStore store = newStore();
        // 内容断言走 EXECUTED（信箱是 v2 全局文件唯一物品载荷）
        ReincarnationCycle first = new ReincarnationCycle(UUID_A);
        first.deposit(Arrays.asList(DIAMOND));
        first.confirmReincarnation(101L);
        store.save(first);

        File parent = store.getFile()
            .getParentFile();
        String[] siblings = parent.list();
        SimpleAssert.that(siblings != null && siblings.length == 1, "原子写后目录仅剩正式文件，无 .tmp 残留");

        // 覆盖写：旧档被原子替换且新内容可读
        ReincarnationCycle second = new ReincarnationCycle(UUID_A);
        second.deposit(Arrays.asList(CIRCUIT));
        second.confirmReincarnation(102L);
        store.save(second);

        ReincarnationCycle loaded = store.load(UUID_A);
        assertMailbox(loaded, CIRCUIT); // 覆盖写后读到最新信箱（恰 1 条，旧档被替换）
        SimpleAssert.eq(
            1,
            loaded.getExecutedFingerprints()
                .size(),
            "save 为整档原子替换（按模型全量写许可字段，游戏路径恒先 load 后 save）");
    }

    /**
     * v1.9.0 S4 契约：待领取清单逐件调整（withdraw/addPendingItem）。
     * 调整口径"只读锁 = EXECUTED"：IDLE/DEPOSITED 可增删，EXECUTED 锁定拒绝；
     * 不存在返回 false；调整不影响状态机迁移。D1 起寄存快照经 WorldData 承载
     * （NBT 往返见 {@link #worldDataNbtRoundTrip()}），本用例聚焦模型行为。
     */
    static void withdrawAdjustsPendingItems() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);

        // IDLE：经 addPendingItem 逐件寄存后可移除；不存在返回 false
        cycle.addPendingItem(DIAMOND);
        SimpleAssert.eq(
            1,
            cycle.getPendingItems()
                .size(),
            "IDLE 追加物品已入清单");
        SimpleAssert.that(cycle.withdraw(DIAMOND), "IDLE 移除已寄存物品");
        SimpleAssert.eq(
            0,
            cycle.getPendingItems()
                .size(),
            "IDLE 移除后清单清空");
        SimpleAssert.that(!cycle.withdraw(CIRCUIT), "IDLE 移除不存在的物品返回 false");
        SimpleAssert.eq(CycleState.IDLE, cycle.getCycleState(), "withdraw 不改变状态机状态");
        expectIllegalArgument("withdraw null 拒绝", () -> cycle.withdraw(null));

        // DEPOSITED：寄存收束后仍可逐件增删（原"仅 IDLE"放宽）
        cycle.deposit(Arrays.asList(DIAMOND));
        cycle.addPendingItem(CIRCUIT);
        SimpleAssert.eq(CycleState.DEPOSITED, cycle.getCycleState(), "DEPOSITED 期间追加物品（新语义）");
        SimpleAssert.eq(
            2,
            cycle.getPendingItems()
                .size(),
            "DEPOSITED 追加后清单 2 件");
        SimpleAssert.that(cycle.withdraw(DIAMOND), "DEPOSITED 移除已寄存物品");
        SimpleAssert.eq(
            1,
            cycle.getPendingItems()
                .size(),
            "DEPOSITED 移除后剩余 1 件");
        SimpleAssert.eq(
            CIRCUIT,
            cycle.getPendingItems()
                .get(0),
            "剩余物品为未移除那件");
        SimpleAssert.that(!cycle.withdraw(DIAMOND), "DEPOSITED 移除不存在物品返回 false");

        // EXECUTED：只读锁——withdraw/addPendingItem 一律拒绝（红线语义）
        String fingerprint = cycle.confirmReincarnation(11L);
        SimpleAssert.eq(CycleState.EXECUTED, cycle.getCycleState(), "确认后进入 EXECUTED");
        expectIllegalState("EXECUTED 期间 withdraw 拒绝", () -> cycle.withdraw(CIRCUIT));
        expectIllegalState("EXECUTED 期间 addPendingItem 拒绝", () -> cycle.addPendingItem(DIAMOND));
        SimpleAssert.eq(
            1,
            cycle.getPendingItems()
                .size(),
            "被拒的移除/追加不产生副作用");
        SimpleAssert.that(cycle.hasFingerprint(fingerprint), "EXECUTED 锁定期指纹不受调整影响");
        store.save(cycle);
        assertMailbox(store.load(UUID_A), CIRCUIT); // EXECUTED 锁定期清单经信箱持久化

        // 领取回 IDLE：清单清空，调整权限恢复（withdraw 空清单返回 false）
        cycle.claimGrant();
        SimpleAssert.eq(CycleState.IDLE, cycle.getCycleState(), "领取后回到 IDLE");
        SimpleAssert.that(!cycle.withdraw(CIRCUIT), "领取回 IDLE 后 withdraw 空清单返回 false");
        cycle.addPendingItem(DIAMOND);
        SimpleAssert.that(cycle.withdraw(DIAMOND), "IDLE 恢复后可再次增删");
    }

    /**
     * A4 丢更新覆写防御（container 确认后陈旧模型关窗保存）：store 先写入
     * EXECUTED+投胎信箱（含物品）+executedFingerprints（等价 Handler.saveSplit 确认
     * 事务），模拟容器仍持陈旧 DEPOSITED/IDLE 模型，经修复后的防御路径
     * （{@link ReincarnationSaveGuard#needsReloadBeforeSave} 判定 → 先重载再保存）
     * 完成关窗保存 → 断言 EXECUTED 状态、信箱物品、指纹三者全部保留且无重复；
     * 对照组固化"陈旧全量覆写抹掉许可"的 bug 形态与反例（磁盘非 EXECUTED 不触发）。
     */
    static void confirmExecutedThenStaleCloseSaveKeepsLicense() throws Exception {
        ReincarnationStore store = newStore();
        // 权威路径：确认推进 EXECUTED + 信箱 + 指纹，同一 save 事务落盘（等价确认链路）
        ReincarnationCycle authoritative = new ReincarnationCycle(UUID_A);
        authoritative.deposit(Arrays.asList(DIAMOND, CIRCUIT));
        String fingerprint = authoritative.confirmReincarnation(42L);
        store.save(authoritative);

        // 模拟容器陈旧模型（doConfirm 返回后容器仍持确认前快照）：DEPOSITED 形态
        ReincarnationCycle stale = new ReincarnationCycle(UUID_A);
        stale.deposit(Arrays.asList(CIRCUIT));
        SimpleAssert.eq(CycleState.DEPOSITED, stale.getCycleState(), "前置：陈旧模型为 DEPOSITED");

        // 防御判定：磁盘 EXECUTED + 内存非 EXECUTED → 先重载再保存（修复后的关窗路径）
        SimpleAssert.that(
            ReincarnationSaveGuard.needsReloadBeforeSave(stale, store.load(UUID_A)),
            "防御判定：磁盘 EXECUTED、内存 DEPOSITED → 先重载");
        stale = store.load(UUID_A); // 重载（容器按打开同款路径替换内存模型）
        store.save(stale); // 关窗保存（saveQuietly 尾段）

        // 断言：EXECUTED 状态、信箱物品（恰为原两件、无重复）、指纹三者全部保留
        ReincarnationCycle afterClose = store.load(UUID_A);
        SimpleAssert.eq(CycleState.EXECUTED, afterClose.getCycleState(), "关窗保存后许可仍为 EXECUTED");
        assertMailbox(afterClose, DIAMOND, CIRCUIT);
        SimpleAssert.eq(
            1,
            afterClose.getExecutedFingerprints()
                .size(),
            "指纹恰 1 条（无重复）");
        SimpleAssert.that(afterClose.hasFingerprint(fingerprint), "指纹保留");
        SimpleAssert.that(afterClose.canClaimGrant(), "EXECUTED 待领取（信箱未被陈旧覆写抹除）");

        // 陈旧 IDLE 形态同样命中防御判定
        ReincarnationCycle staleIdle = new ReincarnationCycle(UUID_A);
        SimpleAssert.eq(CycleState.IDLE, staleIdle.getCycleState(), "前置：陈旧模型为 IDLE");
        SimpleAssert.that(
            ReincarnationSaveGuard.needsReloadBeforeSave(staleIdle, store.load(UUID_A)),
            "防御判定：磁盘 EXECUTED、内存 IDLE → 先重载");

        // 反例：磁盘非 EXECUTED（新档 IDLE）不触发重载，正常保存路径不受影响
        ReincarnationStore freshStore = newStore();
        SimpleAssert.that(
            !ReincarnationSaveGuard.needsReloadBeforeSave(new ReincarnationCycle(UUID_A), freshStore.load(UUID_A)),
            "反例：磁盘非 EXECUTED → 不触发重载");

        // 对照组（固化 bug 形态）：陈旧模型不经防御直接全量覆写 → 信箱/指纹被抹
        ReincarnationCycle staleDirect = new ReincarnationCycle(UUID_A);
        staleDirect.deposit(Arrays.asList(DIAMOND));
        store.save(staleDirect);
        ReincarnationCycle wiped = store.load(UUID_A);
        SimpleAssert.eq(CycleState.IDLE, wiped.getCycleState(), "对照组：陈旧覆写抹掉 EXECUTED（防御针对的 bug 形态）");
        SimpleAssert.eq(
            0,
            wiped.getPendingItems()
                .size(),
            "对照组：陈旧覆写抹掉信箱物品");
        SimpleAssert.eq(
            0,
            wiped.getExecutedFingerprints()
                .size(),
            "对照组：陈旧覆写抹掉指纹");
    }

    // ==================== 工具 ====================

    /** 断言信箱（待发放清单）恰为期望序列（幂等防复制的核心断言） */
    private static void assertMailbox(ReincarnationCycle cycle, ItemRef... expected) {
        List<ItemRef> pending = cycle.getPendingItems();
        SimpleAssert.eq(expected.length, pending.size(), "信箱条目数恰为 " + expected.length + "（不复制）");
        for (int i = 0; i < expected.length; i++) {
            SimpleAssert.eq(expected[i], pending.get(i), "信箱条目[" + i + "]");
        }
    }

    /** 15 列布尔数组的快捷构造（unlockedIndex < 0 表示全 false） */
    private static boolean[] boolArray(int unlockedIndex) {
        boolean[] values = new boolean[ReincarnationCycle.COLUMN_COUNT];
        if (unlockedIndex >= 0) {
            values[unlockedIndex] = true;
        }
        return values;
    }

    /** 断言为"全新空记录"（最后仁慈路径的完整形态；v2 全局文件天然无进度/信箱） */
    private static void assertFresh(ReincarnationCycle cycle, String label) {
        SimpleAssert.eq(CycleState.IDLE, cycle.getCycleState(), label + " → 状态 IDLE");
        SimpleAssert.eq(
            0,
            cycle.getPendingItems()
                .size(),
            label + " → 信箱清空");
        SimpleAssert.eq(
            0,
            cycle.getExecutedFingerprints()
                .size(),
            label + " → 指纹清空");
        SimpleAssert.eq(1, cycle.getUnlockedRows(), label + " → 行解锁复位");
        SimpleAssert.that(allZero(cycle.getHullProgress()), label + " → 外壳进度复位");
        SimpleAssert.that(noneUnlocked(cycle.getUnlockedColumns()), label + " → 列解锁复位");
    }

    private static boolean allZero(int[] values) {
        for (int value : values) {
            if (value != 0) return false;
        }
        return true;
    }

    private static boolean noneUnlocked(boolean[] values) {
        for (boolean value : values) {
            if (value) return false;
        }
        return true;
    }

    private static void expectIllegalState(String label, Runnable action) {
        expectException(label, IllegalStateException.class, action);
    }

    private static void expectIllegalArgument(String label, Runnable action) {
        expectException(label, IllegalArgumentException.class, action);
    }

    private static void expectException(String label, Class<? extends RuntimeException> expected, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            SimpleAssert.that(
                expected.isInstance(e),
                label + " 应抛 "
                    + expected.getSimpleName()
                    + "，实际 "
                    + e.getClass()
                        .getSimpleName());
            return;
        }
        throw new AssertionError(label + " 未抛出 " + expected.getSimpleName());
    }

    /** 每用例独立临时目录，互不串档 */
    private static Path tempDir() throws IOException {
        Path dir = Files.createTempDirectory("gtit-reincarnation-test");
        TEMP_DIRS.add(dir);
        return dir;
    }

    private static ReincarnationStore newStore() throws IOException {
        return new ReincarnationStore(tempDir().toFile());
    }

    private static void cleanup() {
        for (Path dir : TEMP_DIRS) {
            deleteRecursivelyQuiet(dir);
        }
        TEMP_DIRS.clear();
    }

    private static void deleteRecursivelyQuiet(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (java.util.stream.Stream<Path> children = Files.list(dir)) {
                    for (Path child : children.toArray(Path[]::new)) {
                        deleteRecursivelyQuiet(child);
                    }
                }
            }
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // 尽力清理：临时目录遗留不影响判定
        }
    }
}
