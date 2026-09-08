package com.miaokatze.gtit.reincarnation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle.CycleState;
import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle.ItemRef;
import com.miaokatze.gtit.reincarnation.core.ReincarnationFingerprints;
import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/**
 * 周目系统核心（ReincarnationCycle/ReincarnationStore/ReincarnationFingerprints）
 * 的纯 JVM 测试：加解密往返、最后仁慈路径（篡改/魔数/UUID 不匹配/删档）、
 * 单轮回状态机互斥与非法迁移、指纹稳定性、进度字段持久化、原子写。
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
        cases.put("roundTripPreservesFields", () -> runChecked(ReincarnationStoreTest::roundTripPreservesFields));
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
        cases.put("progressFieldsRoundTrip", () -> runChecked(ReincarnationStoreTest::progressFieldsRoundTrip));
        cases.put(
            "atomicWriteNoTempLeftAndOverwriteSafe",
            () -> runChecked(ReincarnationStoreTest::atomicWriteNoTempLeftAndOverwriteSafe));
        cases.put("withdrawAdjustsPendingItems", () -> runChecked(ReincarnationStoreTest::withdrawAdjustsPendingItems));
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

    // ==================== 用例 ====================

    static void roundTripPreservesFields() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        cycle.deposit(Arrays.asList(DIAMOND, CIRCUIT));
        cycle.recordHullConsumption(2, 9);
        cycle.unlockColumn(2);
        cycle.setUnlockedRows(2);
        store.save(cycle);

        ReincarnationCycle loaded = store.load(UUID_A);
        SimpleAssert.eq(cycle.getUuid(), loaded.getUuid(), "uuid round-trip");
        SimpleAssert.eq(CycleState.DEPOSITED, loaded.getCycleState(), "cycleState round-trip");
        SimpleAssert.eq(
            2,
            loaded.getPendingItems()
                .size(),
            "待领取物品条数 round-trip");
        SimpleAssert.eq(
            DIAMOND,
            loaded.getPendingItems()
                .get(0),
            "待领取物品[0] round-trip");
        SimpleAssert.eq(
            CIRCUIT,
            loaded.getPendingItems()
                .get(1),
            "待领取物品[1] round-trip");
        SimpleAssert.eq(9, loaded.getHullProgress()[2], "外壳进度 round-trip");
        SimpleAssert.that(loaded.isColumnUnlocked(2), "列解锁标记 round-trip");
        SimpleAssert.eq(2, loaded.getUnlockedRows(), "行解锁数 round-trip");

        // 回读实例可继续状态机：DEPOSITED → EXECUTED 后再次往返
        String fingerprint = loaded.confirmReincarnation(42L);
        store.save(loaded);
        ReincarnationCycle reloaded = store.load(UUID_A);
        SimpleAssert.eq(CycleState.EXECUTED, reloaded.getCycleState(), "EXECUTED 往返");
        SimpleAssert.that(reloaded.hasFingerprint(fingerprint), "EXECUTED 往返后指纹保留");
    }

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
        store.save(cycle);

        ReincarnationCycle loadedForB = store.load(UUID_B);
        assertFresh(loadedForB, "UUID 不匹配");
        SimpleAssert.eq(UUID_B, loadedForB.getUuid(), "兜底记录归属请求 UUID");
    }

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

        // 永久指纹持久化：再次保存加载仍在
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
    }

    static void progressFieldsRoundTrip() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle cycle = new ReincarnationCycle(UUID_A);
        cycle.recordHullConsumption(0, 5);
        cycle.recordHullConsumption(0, 3);
        cycle.recordHullConsumption(14, 1);
        cycle.unlockColumn(0);
        cycle.unlockColumn(14);
        cycle.setUnlockedRows(3);
        store.save(cycle);

        ReincarnationCycle loaded = store.load(UUID_A);
        SimpleAssert.that(
            Arrays.equals(new int[] { 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 }, loaded.getHullProgress()),
            "15 列外壳进度逐位 round-trip");
        boolean[] expectedColumns = new boolean[ReincarnationCycle.COLUMN_COUNT];
        expectedColumns[0] = true;
        expectedColumns[14] = true;
        SimpleAssert.that(Arrays.equals(expectedColumns, loaded.getUnlockedColumns()), "15 列解锁标记逐位 round-trip");
        SimpleAssert.eq(3, loaded.getUnlockedRows(), "行解锁数 round-trip");

        // 入参边界：行数钳制 1..3，列下标越界拒绝
        ReincarnationCycle bounds = new ReincarnationCycle(UUID_B);
        bounds.setUnlockedRows(99);
        SimpleAssert.eq(3, bounds.getUnlockedRows(), "行解锁数上钳 3");
        bounds.setUnlockedRows(0);
        SimpleAssert.eq(1, bounds.getUnlockedRows(), "行解锁数下钳 1");
        expectIllegalArgument("列下标 15 越界", () -> bounds.recordHullConsumption(15, 1));
        expectIllegalArgument("列下标 -1 越界", () -> bounds.unlockColumn(-1));
        expectIllegalArgument("外壳消耗数为负", () -> bounds.recordHullConsumption(0, -1));
    }

    static void atomicWriteNoTempLeftAndOverwriteSafe() throws Exception {
        ReincarnationStore store = newStore();
        ReincarnationCycle first = new ReincarnationCycle(UUID_A);
        first.deposit(Arrays.asList(DIAMOND));
        store.save(first);

        File parent = store.getFile()
            .getParentFile();
        String[] siblings = parent.list();
        SimpleAssert.that(siblings != null && siblings.length == 1, "原子写后目录仅剩正式文件，无 .tmp 残留");

        // 覆盖写：旧档被原子替换且新内容可读
        ReincarnationCycle second = new ReincarnationCycle(UUID_A);
        second.deposit(Arrays.asList(CIRCUIT));
        second.recordHullConsumption(7, 4);
        store.save(second);

        ReincarnationCycle loaded = store.load(UUID_A);
        SimpleAssert.eq(
            1,
            loaded.getPendingItems()
                .size(),
            "覆盖写后读取到最新内容");
        SimpleAssert.eq(
            CIRCUIT,
            loaded.getPendingItems()
                .get(0),
            "覆盖写后内容为新档");
        SimpleAssert.eq(4, loaded.getHullProgress()[7], "覆盖写后进度为新档");
    }

    /**
     * v1.9.0 S4 契约：待领取清单逐件调整（withdraw/addPendingItem）。
     * 调整口径"只读锁 = EXECUTED"：IDLE/DEPOSITED 可增删（DEPOSITED 由原"仅 IDLE"放宽），
     * EXECUTED 锁定拒绝；不存在返回 false；调整不影响状态机迁移。
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

        // DEPOSITED：新语义——寄存收束后仍可逐件增删（原"仅 IDLE"放宽）
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
        // 调整结果可持久化往返
        store.save(cycle);
        ReincarnationCycle loaded = store.load(UUID_A);
        SimpleAssert.eq(
            1,
            loaded.getPendingItems()
                .size(),
            "DEPOSITED 调整后条数 round-trip");
        SimpleAssert.eq(
            CIRCUIT,
            loaded.getPendingItems()
                .get(0),
            "DEPOSITED 调整后内容 round-trip");

        // EXECUTED：只读锁——withdraw/addPendingItem 一律拒绝
        String fingerprint = loaded.confirmReincarnation(11L);
        SimpleAssert.eq(CycleState.EXECUTED, loaded.getCycleState(), "确认后进入 EXECUTED");
        expectIllegalState("EXECUTED 期间 withdraw 拒绝", () -> loaded.withdraw(CIRCUIT));
        expectIllegalState("EXECUTED 期间 addPendingItem 拒绝", () -> loaded.addPendingItem(DIAMOND));
        SimpleAssert.eq(
            1,
            loaded.getPendingItems()
                .size(),
            "被拒的移除/追加不产生副作用");
        SimpleAssert.that(loaded.hasFingerprint(fingerprint), "EXECUTED 锁定期指纹不受调整影响");

        // 领取回 IDLE：清单清空，调整权限恢复（withdraw 空清单返回 false）
        loaded.claimGrant();
        SimpleAssert.eq(CycleState.IDLE, loaded.getCycleState(), "领取后回到 IDLE");
        SimpleAssert.that(!loaded.withdraw(CIRCUIT), "领取回 IDLE 后 withdraw 空清单返回 false");
        loaded.addPendingItem(DIAMOND);
        SimpleAssert.that(loaded.withdraw(DIAMOND), "IDLE 恢复后可再次增删");
    }

    // ==================== 工具 ====================
    /** 断言为"全新空记录"（最后仁慈路径的完整形态） */
    private static void assertFresh(ReincarnationCycle cycle, String label) {
        SimpleAssert.eq(CycleState.IDLE, cycle.getCycleState(), label + " → 状态 IDLE");
        SimpleAssert.eq(
            0,
            cycle.getPendingItems()
                .size(),
            label + " → 待领取清空");
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
    private static ReincarnationStore newStore() throws IOException {
        Path dir = Files.createTempDirectory("gtit-reincarnation-test");
        TEMP_DIRS.add(dir);
        return new ReincarnationStore(dir.toFile());
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
