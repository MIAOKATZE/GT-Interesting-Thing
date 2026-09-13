package com.miaokatze.gtit.reincarnation.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * 周目「许可字段」持久化仓库（混淆加密 + 原子写；v1.9.x D1 起仅承载跨存档字段）。
 * <p>
 * 文件路径固定为 {@code <minecraft dir>/} + {@link #RELATIVE_PATH}
 * （即 {@code config/gtit/reincarnation.dat}）。"config" 拼接只出现在
 * {@link #RELATIVE_PATH} 这一处常量；运行目录由构造参数
 * {@code baseDir}（Minecraft 运行目录）注入。
 * <p>
 * <b>v2 载荷切分（D1）</b>：每存档进度（15 列外壳/解锁标记/行数/寄存物品快照）已迁入
 * 每存档隔离的 {@code reincarnation/storage/ReincarnationWorldData}（overworld
 * MapStorage），本全局文件只留「许可字段」：
 * <ul>
 * <li>{@code uuid}：载荷归属玩家（防串档校验）；</li>
 * <li>{@code executedFingerprints}：已执行轮回的永久时间线指纹集合；</li>
 * <li>{@code mailbox}：<b>投胎信箱</b>（{@code items} + {@code grantInFlight} 幂等标记
 * + {@code delivered} 已交付前缀计数）。四语义：确认轮回推进 EXECUTED 的<b>同一事务</b>
 * （同一次原子写）写入物品快照（写）；发放路径经 {@link #load} 读出（读）；发放成功
 * （或按既有溢出落地）后才随 {@code claimGrant+save} 清空（成功清空）；发放未完成
 * （掉线/异常/崩溃）则保留（失败保留）。{@code grantInFlight} 在演出启动时置位、
 * 清信箱时复位——崩溃重进后发放幂等（已清空即不再发放，不复制）。
 * {@code delivered}（v2 可选字段，旧载荷缺省 0）承担<b>逐件发放</b>的续跑账本：信箱
 * 物品按前缀 [0, delivered) 视为已交付（演出中逐件发放的进度），掉线/崩溃重进从
 * delivered 续发剩余后缀，配合 {@link #markGrantDelivered} 单调推进保证
 * 「重复发放不复制、发放不丢账」；清信箱时随载荷整体复位。</li>
 * </ul>
 * <p>
 * <b>v1 只读兼容</b>：{@link #load} 对 v1 载荷提取许可字段（UUID/指纹；EXECUTED 态
 * pendingItems 映射为信箱）供登录编排正确工作，进度字段忽略——登录/倒计时/发放
 * 不依赖迁移先行完成。进度字段的一次性搬迁由
 * {@code reincarnation/storage/ReincarnationMigration} 在首次开 GUI 时 consume-once
 * 完成（经 {@link #consumeLegacyV1}）。
 * <p>
 * 混淆级加密（<b>非安全级</b>）：AES/CBC/PKCS5Padding，密钥与 IV 以常量内置在
 * 发行 jar 中。目的仅是防止玩家直接手改明文 JSON（软混淆），<b>不构成机密性边界</b>。
 * 玩家间防串档依赖解密后的 UUID 校验。
 * <p>
 * 最后仁慈路径（load/consume 的统一兜底语义）：文件不存在、魔数不符、解密失败、
 * JSON 结构损坏、formatVersion 不识别、载荷 UUID 与请求不匹配——一律视同
 * "文件不存在"，返回全新空记录/null，不抛出、不阻塞玩家进入下一周目。
 * 核心包刻意零日志依赖（失败路径语义已定义），保证纯 JVM 无头测试可直跑。
 * <p>
 * 写入原子性：先写同目录临时文件 {@code reincarnation.dat.tmp}，再
 * {@code ATOMIC_MOVE}（不支持时退化为普通 move）替换旧文件，进程崩溃不会留下
 * 半写状态损坏旧档。
 */
public final class ReincarnationStore {

    /** 相对 Minecraft 运行目录的固定存储路径（"config" 拼接唯一出现处） */
    public static final String RELATIVE_PATH = "config/gtit/reincarnation.dat";

    /**
     * 载荷格式版本（不识别即按最后仁慈路径兜底）。
     * v1 = 全字段混载（旧）；v2 = 仅许可字段（uuid/指纹/投胎信箱）。
     */
    static final int FORMAT_VERSION = 2;

    /** v1 历史格式版本号（{@link #load} 只读兼容 + {@link #consumeLegacyV1} 迁移识别） */
    static final int LEGACY_FORMAT_VERSION = 1;

    /** 临时文件后缀（同目录，保证 rename 原子性） */
    private static final String TEMP_SUFFIX = ".tmp";

    /** 文件魔数（"GTIR"）：快速识别本格式，不符即兜底 */
    private static final byte[] MAGIC = { 0x47, 0x54, 0x49, 0x52 };

    // 混淆级常量密钥/IV（各 16 字节 = AES-128）：非安全级，见类 javadoc
    private static final byte[] OBF_KEY = "gtit$r1ncarn@te!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OBF_IV = "GTIT#re#cycle#24".getBytes(StandardCharsets.UTF_8);

    /** 存储文件（baseDir 注入后唯一落点） */
    private final File file;

    /**
     * 旧 v1 全量载荷的一次性迁移视图（{@link #consumeLegacyV1} 返回值）：
     * 每存档进度字段 + 按 v1 状态拆分的寄存/信箱物品。
     */
    public static final class LegacyRecord {

        /** 15 列外壳进度 */
        public final int[] hullProgress;
        /** 15 列解锁标记 */
        public final boolean[] unlockedColumns;
        /** 全局行解锁数 */
        public final int unlockedRows;
        /** v1 状态为 DEPOSITED 的 pendingItems（寄存快照 → 当档 WorldData） */
        public final List<ReincarnationCycle.ItemRef> depositedItems;
        /** v1 状态为 EXECUTED 的 pendingItems（待领取 → v2 投胎信箱） */
        public final List<ReincarnationCycle.ItemRef> mailboxItems;

        LegacyRecord(int[] hullProgress, boolean[] unlockedColumns, int unlockedRows,
            List<ReincarnationCycle.ItemRef> depositedItems, List<ReincarnationCycle.ItemRef> mailboxItems) {
            this.hullProgress = hullProgress;
            this.unlockedColumns = unlockedColumns;
            this.unlockedRows = unlockedRows;
            this.depositedItems = depositedItems;
            this.mailboxItems = mailboxItems;
        }
    }

    /** 解密后的载荷视图（内部；任何结构异常即 null = 最后仁慈） */
    private static final class RecordView {

        final int formatVersion;
        final Set<String> fingerprints;
        /** v2 = mailbox.items；v1 = EXECUTED 态的 pendingItems（其余状态空） */
        final List<ReincarnationCycle.ItemRef> mailboxItems;
        final boolean grantInFlight;
        /** v2 = mailbox.delivered（逐件发放已交付前缀计数，旧载荷缺省 0）；v1 恒 0 */
        final int delivered;
        // —— 仅 v1 载荷填充（迁移路径）——
        final ReincarnationCycle.CycleState legacyState;
        final List<ReincarnationCycle.ItemRef> legacyPendingItems;
        final int[] legacyHullProgress;
        final boolean[] legacyUnlockedColumns;
        final int legacyUnlockedRows;

        /** v2 载荷视图（逐件发放账本齐备） */
        RecordView(int formatVersion, Set<String> fingerprints, List<ReincarnationCycle.ItemRef> mailboxItems,
            boolean grantInFlight, int delivered) {
            this(formatVersion, fingerprints, mailboxItems, grantInFlight, delivered, null, null, null, null, 1);
        }

        RecordView(int formatVersion, Set<String> fingerprints, List<ReincarnationCycle.ItemRef> mailboxItems,
            boolean grantInFlight, ReincarnationCycle.CycleState legacyState,
            List<ReincarnationCycle.ItemRef> legacyPendingItems, int[] legacyHullProgress,
            boolean[] legacyUnlockedColumns, int legacyUnlockedRows) {
            this(
                formatVersion,
                fingerprints,
                mailboxItems,
                grantInFlight,
                0,
                legacyState,
                legacyPendingItems,
                legacyHullProgress,
                legacyUnlockedColumns,
                legacyUnlockedRows);
        }

        RecordView(int formatVersion, Set<String> fingerprints, List<ReincarnationCycle.ItemRef> mailboxItems,
            boolean grantInFlight, int delivered, ReincarnationCycle.CycleState legacyState,
            List<ReincarnationCycle.ItemRef> legacyPendingItems, int[] legacyHullProgress,
            boolean[] legacyUnlockedColumns, int legacyUnlockedRows) {
            this.formatVersion = formatVersion;
            this.fingerprints = fingerprints;
            this.mailboxItems = mailboxItems;
            this.grantInFlight = grantInFlight;
            this.delivered = delivered;
            this.legacyState = legacyState;
            this.legacyPendingItems = legacyPendingItems;
            this.legacyHullProgress = legacyHullProgress;
            this.legacyUnlockedColumns = legacyUnlockedColumns;
            this.legacyUnlockedRows = legacyUnlockedRows;
        }
    }

    /**
     * @param baseDir Minecraft 运行目录（非 null）；实际文件为
     *                {@code new File(baseDir, RELATIVE_PATH)}
     */
    public ReincarnationStore(File baseDir) {
        if (baseDir == null) {
            throw new IllegalArgumentException("baseDir 不能为 null");
        }
        this.file = new File(baseDir, RELATIVE_PATH);
    }

    /** @return 存储文件落点（测试与运维检查用） */
    public File getFile() {
        return file;
    }

    // ==================== 读 ====================

    /**
     * 读取玩家周目「许可视图」。任何读取/解密/解析/UUID 不匹配失败一律返回全新空记录
     * （最后仁慈路径，见类 javadoc），不抛出。
     * <p>
     * 返回记录只含许可字段：状态仅 IDLE（无信箱）/ EXECUTED（信箱非空，pendingItems =
     * 信箱物品；互斥锁语义与 v1 状态机完全一致）；进度字段为缺省值——每存档进度由
     * {@code ReincarnationWorldData} 承载并在调用方合并。v1 载荷经只读兼容视图提取
     * 许可字段（不消费、不迁移）。
     *
     * @param uuid 玩家 UUID 字符串
     * @return 记录（永不返回 null）
     */
    public ReincarnationCycle load(String uuid) {
        ReincarnationCycle fresh = new ReincarnationCycle(uuid);
        RecordView view = readView(uuid);
        if (view == null) {
            return fresh;
        }
        return ReincarnationCycle.restore(
            uuid,
            view.mailboxItems.isEmpty() ? ReincarnationCycle.CycleState.IDLE : ReincarnationCycle.CycleState.EXECUTED,
            view.mailboxItems,
            new int[ReincarnationCycle.COLUMN_COUNT],
            new boolean[ReincarnationCycle.COLUMN_COUNT],
            ReincarnationCycle.MIN_UNLOCKED_ROWS,
            view.fingerprints);
    }

    /** @return 投胎信箱是否处于「发放已启动未完成」状态（无信箱/文件缺失/损坏一律 false） */
    public boolean isGrantInFlight(String uuid) {
        RecordView view = readView(uuid);
        return view != null && view.grantInFlight && !view.mailboxItems.isEmpty();
    }

    /**
     * @return 投胎信箱的逐件发放已交付前缀计数（信箱物品 {@code [0, delivered)} 已交付）。
     *         无信箱/文件缺失/损坏一律 0；计数可能超过信箱长度仅在载荷被手改时出现，
     *         调用方按 {@code min(delivered, size)} 收敛。
     */
    public int readGrantDelivered(String uuid) {
        RecordView view = readView(uuid);
        return view == null ? 0 : Math.max(0, view.delivered);
    }

    /**
     * 置投胎信箱「发放已启动未完成」幂等标记（幂等；重复置位安全）。
     * 无信箱/文件缺失/损坏时不产生任何副作用（失败不写口径）。
     */
    public void markGrantInFlight(String uuid) {
        RecordView view = readView(uuid);
        if (view == null || view.mailboxItems.isEmpty() || view.grantInFlight) {
            return;
        }
        writeLicense(uuid, view.fingerprints, view.mailboxItems, true, view.delivered);
    }

    /**
     * 单调推进逐件发放已交付前缀计数（逐件发放进度账本，持久化）。
     * <p>
     * 幂等 + 单调：仅当 {@code delivered} <b>严格大于</b>当前计数且信箱在场时才写盘
     * （回退值/等值重复推进一律无副作用，防演出重放或乱序调度把账本拨回）；
     * 计数超过信箱长度按长度封顶（前缀语义的自然上界）。
     * 无信箱/文件缺失/损坏时不产生任何副作用（失败不写口径）。
     */
    public void markGrantDelivered(String uuid, int delivered) {
        RecordView view = readView(uuid);
        if (view == null || view.mailboxItems.isEmpty()) {
            return;
        }
        int clamped = Math.min(Math.max(0, delivered), view.mailboxItems.size());
        if (clamped <= view.delivered) {
            return;
        }
        writeLicense(uuid, view.fingerprints, view.mailboxItems, true, clamped);
    }

    // ==================== 迁移（consume-once） ====================

    /**
     * 非破坏地读取旧 v1 全量载荷：返回进度/寄存/信箱拆分视图，但不改写全局文件。
     * 非 v1 载荷（已迁移/缺失/损坏/UUID 不匹配）返回 null，保持最后仁慈语义。
     */
    public LegacyRecord readLegacyV1(String uuid) {
        RecordView view = readView(uuid);
        return view == null || view.formatVersion != LEGACY_FORMAT_VERSION ? null : toLegacyRecord(view);
    }

    /**
     * 一次性消费旧 v1 全量载荷：返回进度/寄存/信箱拆分视图，并把全局文件改写为 v2
     * （指纹保留；EXECUTED 待领取转入信箱）。非 v1 载荷（已迁移/缺失/损坏/UUID 不匹配）
     * 返回 null 且不改写文件（最后仁慈 + consume-once，见类 javadoc）。
     */
    public LegacyRecord consumeLegacyV1(String uuid) {
        RecordView view = readView(uuid);
        if (view == null || view.formatVersion != LEGACY_FORMAT_VERSION) {
            return null;
        }
        LegacyRecord legacy = toLegacyRecord(view);
        // 改写为 v2：只留许可字段（信箱转入）；进度字段自此由每存档 WorldData 承载
        writeLicense(uuid, view.fingerprints, legacy.mailboxItems, false, 0);
        return legacy;
    }

    private static LegacyRecord toLegacyRecord(RecordView view) {
        List<ReincarnationCycle.ItemRef> deposited = view.legacyState == ReincarnationCycle.CycleState.DEPOSITED
            ? new ArrayList<>(view.legacyPendingItems)
            : new ArrayList<ReincarnationCycle.ItemRef>();
        List<ReincarnationCycle.ItemRef> mailbox = view.legacyState == ReincarnationCycle.CycleState.EXECUTED
            ? new ArrayList<>(view.legacyPendingItems)
            : new ArrayList<ReincarnationCycle.ItemRef>();
        return new LegacyRecord(
            view.legacyHullProgress.clone(),
            view.legacyUnlockedColumns.clone(),
            view.legacyUnlockedRows,
            deposited,
            mailbox);
    }

    // ==================== 写 ====================

    /**
     * 保存玩家周目许可字段（原子写：临时文件 + rename，见类 javadoc）。
     * <p>
     * 投胎信箱按模型状态收敛：EXECUTED（含非空待领取清单）写入信箱快照
     * （确认轮回推进 EXECUTED 的同一事务；已有 {@code grantInFlight} 幂等标记保留）；
     * 其余状态信箱为空 + 标记复位（claimGrant 后的成功清空）。
     *
     * @throws IllegalStateException 加密或磁盘写入失败（旧文件不受影响）
     */
    public void save(ReincarnationCycle cycle) {
        List<ReincarnationCycle.ItemRef> mailbox = new ArrayList<>();
        boolean grantInFlight = false;
        int delivered = 0;
        if (cycle.getCycleState() == ReincarnationCycle.CycleState.EXECUTED) {
            mailbox.addAll(cycle.getPendingItems());
            if (!mailbox.isEmpty()) {
                // 已在发放中的信箱保留幂等标记与逐件发放已交付账本（发放路径 markGrantInFlight /
                // markGrantDelivered 后的进度保存不丢标记、不回拨账本）
                RecordView view = readView(cycle.getUuid());
                grantInFlight = view != null && view.grantInFlight;
                delivered = view == null ? 0 : Math.max(0, view.delivered);
            }
        }
        writeLicense(cycle.getUuid(), cycle.getExecutedFingerprints(), mailbox, grantInFlight, delivered);
    }

    // ==================== JSON 载荷 ====================

    /** v2 许可载荷写盘（唯一写入口；原子 + 加密） */
    private void writeLicense(String uuid, Set<String> fingerprints, List<ReincarnationCycle.ItemRef> mailbox,
        boolean grantInFlight, int delivered) {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);
        root.addProperty("uuid", uuid);

        JsonArray fps = new JsonArray();
        for (String fingerprint : fingerprints) {
            fps.add(new JsonPrimitive(fingerprint));
        }
        root.add("executedFingerprints", fps);

        JsonObject box = new JsonObject();
        JsonArray items = new JsonArray();
        for (ReincarnationCycle.ItemRef item : mailbox) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("id", item.getId());
            itemJson.addProperty("meta", item.getMeta());
            items.add(itemJson);
        }
        box.add("items", items);
        box.addProperty("grantInFlight", grantInFlight);
        box.addProperty("delivered", Math.max(0, delivered));
        root.add("mailbox", box);

        writeEncrypted(
            root.toString()
                .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解密+解析载荷 → 视图；文件缺失/魔数不符/解密失败/结构损坏/formatVersion 不识别/
     * UUID 不匹配一律 null（最后仁慈）。
     */
    private RecordView readView(String requestUuid) {
        byte[] fileBytes = readAllQuiet();
        if (fileBytes == null || !startsWith(fileBytes, MAGIC)) {
            return null;
        }
        byte[] plain;
        try {
            Cipher cipher = newObfuscationCipher(Cipher.DECRYPT_MODE);
            plain = cipher.doFinal(fileBytes, MAGIC.length, fileBytes.length - MAGIC.length);
        } catch (GeneralSecurityException e) {
            // 解密失败（密文被篡改/截断/非本格式）→ 最后仁慈路径
            return null;
        }
        return parsePayload(requestUuid, plain);
    }

    /**
     * 解析明文 JSON 载荷；任何结构异常（JSON 损坏、字段缺失/类型错、长度非法、
     * UUID 不匹配）都按最后仁慈路径返回 null。
     * <p>
     * 刻意捕获 {@link RuntimeException}：本方法体内所有异常的语义都是"载荷不可信"，
     * 与逐个列举受检子类型相比不漏语义且不误伤（本方法无其他可抛副作用）。
     */
    private static RecordView parsePayload(String requestUuid, byte[] plain) {
        try {
            JsonElement rootElement = new JsonParser().parse(new String(plain, StandardCharsets.UTF_8));
            if (!rootElement.isJsonObject()) {
                return null;
            }
            JsonObject root = rootElement.getAsJsonObject();
            JsonElement version = root.get("formatVersion");
            if (version == null || !version.isJsonPrimitive()) {
                return null;
            }
            int formatVersion = version.getAsInt();
            JsonElement payloadUuid = root.get("uuid");
            if (payloadUuid == null || !payloadUuid.isJsonPrimitive()
                || !uuidMatches(payloadUuid.getAsString(), requestUuid)) {
                return null;
            }
            Set<String> fingerprints = readFingerprints(root.get("executedFingerprints"));
            if (formatVersion == FORMAT_VERSION) {
                JsonObject box = root.get("mailbox")
                    .getAsJsonObject();
                List<ReincarnationCycle.ItemRef> items = readPendingItems(box.get("items"));
                boolean inFlight = box.get("grantInFlight")
                    .getAsBoolean();
                // delivered 为逐件发放账本（可选字段）：旧 v2 载荷/手改缺失按 0，
                // 类型异常并入整体"结构不可信"仁慈路径，负值收敛为 0
                JsonElement deliveredElement = box.get("delivered");
                int delivered = deliveredElement == null || !deliveredElement.isJsonPrimitive() ? 0
                    : Math.max(0, deliveredElement.getAsInt());
                return new RecordView(formatVersion, fingerprints, items, inFlight, delivered);
            }
            if (formatVersion == LEGACY_FORMAT_VERSION) {
                // v1 只读兼容视图：许可字段提取；EXECUTED 态 pendingItems 映射为信箱
                ReincarnationCycle.CycleState state = ReincarnationCycle.CycleState.valueOf(
                    root.get("cycleState")
                        .getAsString());
                List<ReincarnationCycle.ItemRef> pending = readPendingItems(root.get("pendingItems"));
                List<ReincarnationCycle.ItemRef> mailbox = state == ReincarnationCycle.CycleState.EXECUTED ? pending
                    : new ArrayList<ReincarnationCycle.ItemRef>();
                return new RecordView(
                    formatVersion,
                    fingerprints,
                    mailbox,
                    false,
                    state,
                    pending,
                    readFixedLengthIntArray(root.get("hullProgress")),
                    readFixedLengthBooleanArray(root.get("unlockedColumns")),
                    root.get("unlockedRows")
                        .getAsInt());
            }
            return null; // 不识别的版本 → 最后仁慈
        } catch (RuntimeException e) {
            // 载荷任何结构异常 → 最后仁慈路径（见方法 javadoc）
            return null;
        }
    }

    private static List<ReincarnationCycle.ItemRef> readPendingItems(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("pendingItems 缺失或非数组");
        }
        List<ReincarnationCycle.ItemRef> items = new ArrayList<>();
        for (JsonElement itemElement : (JsonArray) element) {
            if (!itemElement.isJsonObject()) {
                throw new IllegalArgumentException("pendingItems 元素非对象");
            }
            JsonObject itemJson = itemElement.getAsJsonObject();
            JsonElement id = itemJson.get("id");
            JsonElement meta = itemJson.get("meta");
            if (id == null || !id.isJsonPrimitive() || meta == null || !meta.isJsonPrimitive()) {
                throw new IllegalArgumentException("pendingItems 元素字段缺失");
            }
            items.add(new ReincarnationCycle.ItemRef(id.getAsString(), meta.getAsInt()));
        }
        return items;
    }

    private static int[] readFixedLengthIntArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("hullProgress 缺失或非数组");
        }
        JsonArray array = (JsonArray) element;
        if (array.size() != ReincarnationCycle.COLUMN_COUNT) {
            throw new IllegalArgumentException("hullProgress 长度非法: " + array.size());
        }
        int[] values = new int[ReincarnationCycle.COLUMN_COUNT];
        for (int i = 0; i < array.size(); i++) {
            values[i] = array.get(i)
                .getAsInt();
        }
        return values;
    }

    private static boolean[] readFixedLengthBooleanArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("unlockedColumns 缺失或非数组");
        }
        JsonArray array = (JsonArray) element;
        if (array.size() != ReincarnationCycle.COLUMN_COUNT) {
            throw new IllegalArgumentException("unlockedColumns 长度非法: " + array.size());
        }
        boolean[] values = new boolean[ReincarnationCycle.COLUMN_COUNT];
        for (int i = 0; i < array.size(); i++) {
            values[i] = array.get(i)
                .getAsBoolean();
        }
        return values;
    }

    private static Set<String> readFingerprints(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("executedFingerprints 缺失或非数组");
        }
        Set<String> fingerprints = new HashSet<>();
        for (JsonElement fingerprint : (JsonArray) element) {
            fingerprints.add(fingerprint.getAsString());
        }
        return fingerprints;
    }

    // ==================== v1 测试夹具 ====================

    /**
     * 测试夹具：按 v1 全量格式写盘（仅供测试构造旧格式文件；生产路径禁止调用）。
     * 字段布局与 v1 完全一致（formatVersion/cycleState/pendingItems/hullProgress/
     * unlockedColumns/unlockedRows/executedFingerprints），加密与原子写复用生产管线。
     */
    public static void writeLegacyV1Fixture(File baseDir, String uuid, ReincarnationCycle.CycleState cycleState,
        List<ReincarnationCycle.ItemRef> pendingItems, int[] hullProgress, boolean[] unlockedColumns, int unlockedRows,
        Set<String> fingerprints) {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", LEGACY_FORMAT_VERSION);
        root.addProperty("uuid", uuid);
        root.addProperty("cycleState", cycleState.name());

        JsonArray pending = new JsonArray();
        for (ReincarnationCycle.ItemRef item : pendingItems) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("id", item.getId());
            itemJson.addProperty("meta", item.getMeta());
            pending.add(itemJson);
        }
        root.add("pendingItems", pending);

        JsonArray hull = new JsonArray();
        for (int value : hullProgress) {
            hull.add(new JsonPrimitive(value));
        }
        root.add("hullProgress", hull);

        JsonArray columns = new JsonArray();
        for (boolean unlocked : unlockedColumns) {
            columns.add(new JsonPrimitive(unlocked));
        }
        root.add("unlockedColumns", columns);

        root.addProperty("unlockedRows", unlockedRows);

        JsonArray fps = new JsonArray();
        for (String fingerprint : fingerprints) {
            fps.add(new JsonPrimitive(fingerprint));
        }
        root.add("executedFingerprints", fps);

        writeEncryptedTo(
            new File(baseDir, RELATIVE_PATH),
            root.toString()
                .getBytes(StandardCharsets.UTF_8));
    }

    // ==================== 加解密与文件工具 ====================

    private void writeEncrypted(byte[] json) {
        writeEncryptedTo(this.file, json);
    }

    private static void writeEncryptedTo(File target, byte[] json) {
        byte[] payload;
        try {
            Cipher cipher = newObfuscationCipher(Cipher.ENCRYPT_MODE);
            byte[] cipherText = cipher.doFinal(json);
            payload = new byte[MAGIC.length + cipherText.length];
            System.arraycopy(MAGIC, 0, payload, 0, MAGIC.length);
            System.arraycopy(cipherText, 0, payload, MAGIC.length, cipherText.length);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("周目记录加密失败", e);
        }
        try {
            if (target.getParentFile() != null) {
                Files.createDirectories(
                    target.getParentFile()
                        .toPath());
            }
            Path tempPath = target.toPath()
                .resolveSibling(target.getName() + TEMP_SUFFIX);
            Files.write(tempPath, payload);
            try {
                Files.move(
                    tempPath,
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // 文件系统不支持原子 rename（如某些网络盘）：退化为普通 move，仍先写临时文件
                Files.move(tempPath, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("周目记录写入失败: " + target, e);
        }
    }

    private static Cipher newObfuscationCipher(int opMode) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(opMode, new SecretKeySpec(OBF_KEY, "AES"), new IvParameterSpec(OBF_IV));
        return cipher;
    }

    private byte[] readAllQuiet() {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            // 文件不存在/不可读 → 按文件不存在语义兜底
            return null;
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean uuidMatches(String payloadUuid, String requestUuid) {
        try {
            return UUID.fromString(payloadUuid)
                .toString()
                .equals(
                    UUID.fromString(requestUuid)
                        .toString());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
