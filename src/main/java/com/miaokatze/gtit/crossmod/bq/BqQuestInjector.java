package com.miaokatze.gtit.crossmod.bq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.Tags;
import com.miaokatze.gtit.main.GTInterestingThing;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.storage.DBEntry;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.network.handlers.NetSettingSync;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.QuestLine;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.QuestLineEntry;
import bq_standard.tasks.TaskCheckbox;
import bq_standard.tasks.TaskCrafting;
import bq_standard.tasks.TaskRetrieval;
import cpw.mods.fml.common.Loader;

/**
 * BetterQuesting 运行时任务注入器（E4a，自 GTSWN 同名范本移植；T1 增补版本感知刷新与剪枝）。
 * <p>
 * 本 mod 内全部 betterquesting 类型引用都收敛在这一个类中，
 * 且只在 {@link BqCompat#isBqLoaded()} 为 true 时才会被类加载
 * （BQ 缺席环境下本类永不加载，无需 @Optional 字节码剥离）。
 * <p>
 * 挂载点：CommonProxy.serverStarted（FMLServerStartedEvent）。整波 ServerStarting——含 BQ default load
 * 与任何第三方（如 dreamcraft "Modpack has been updated"）整库重载——结束后、tick 循环与任何玩家登录
 * 之前执行；BQ 全源码零 FMLServerStartedEvent 订阅，该时点无清库者（规范.md §6 条款 1/11）。
 * 
 * @Mod after:betterquesting 仅保留纯排序语义。
 *      <p>
 *      与 GTSWN 范本的差异：资产根目录参数化（{@link #inject(String)}），
 *      支持 GTIT 自身包与 MIAO-GTNH 综合包两个资产根；世界侧版本戳为
 *      单文件双键（{@value #STAMP_FILE_NAME} 的 "gtit"/"miao" 键）按包独立对账；
 *      剪枝从线内范围放宽为按包 questIDHigh 常量的数据库级范围，任务线本体同口径剪枝
 *      （removeQuest 只摘条目不移线，BQ 3.8.72 已核）；
 *      index.json 缺失或资产不存在时 info 日志 + 静默返回
 *      （并行内容编辑尚未就位资源时零报错降级）。
 *      <p>
 *      流程（照抄 BQ 官方装载路径 QuestCommandDefaults.load 的同构形状）：
 *      <ol>
 *      <li>读 &lt;assetRoot&gt;/index.json 清单（规避 1.7.10 jar 目录枚举）</li>
 *      <li>逐文件 Gson 解析 → NBTConverter.JSONtoNBT_Object(format=true)</li>
 *      <li>任务与任务线幂等 get-or-create 装载（get(id)==null 才 new + readFromNBT；
 *      已存在且本包版本戳对账失败时走定义刷新：进度快照 → 重读定义整体替换 →
 *      merge 回填，完成/领取状态保留，挂线坐标用新条目覆盖）</li>
 *      <li>QuestLineEntry 挂线（未挂才 put）与 QuestLineDatabase.setOrderIndex</li>
 *      <li>前排有序：按 gtsr→gtswn→gtit 常量表对库内存在线依序 setOrderIndex（幂等收敛，禁 setOrderedEntries）</li>
 *      <li>剪枝（无条件执行，与版本无关）：任务数据库中属于该包（questIDHigh == 包常量）
 *      但不在 index.json 清单内的任务，摘线并移出数据库——删除自动传播到所有世界</li>
 *      <li>进度回填：只回填本次实际重建的已识别包内任务；保留已有任务的权威内存进度，
 *      旧版汇总先 merge=false、逐玩家后 merge=true，并合并原生 dirty-player 集合</li>
 *      <li>版本戳按包写回（刷新成功才写；写失败仅告警，下次启动重试定义刷新）</li>
 *      <li>同步四连（NetSettingSync / NetQuestSync.quickSync / NetChapterSync / markDirty）</li>
 *      </ol>
 */
public final class BqQuestInjector {

    /** GTIT 自身包身份（questIdHigh 与 assets/gtit/bqquests/index.json 的 questLines[].idHigh 一致） */
    private static final PackSpec PACK_GTIT = new PackSpec("gtit", 5139813699862659072L);

    /** MIAO-GTNH 综合包身份（questIdHigh 与 assets/gtit/bqquests/miao/index.json 的 questLines[].idHigh 一致） */
    private static final PackSpec PACK_MIAO = new PackSpec("miao", 5569054221778550784L);

    /** 前排有序表（规范.md §6 条款 10，2026-09-07 拍板）：gtsr→gtswn→gtit 依序占据任务书前排槽位 */
    private static final long[] FRONT_ROW_LINE_HIGH = { 0x4754535200000000L, 0x4754574E00000000L, 0x4754495400000000L };

    /** 世界侧版本戳文件名（位于 BQ_Settings.curWorldDir 下），双键 {"gtit":...,"miao":...} 各包独立对账 */
    private static final String STAMP_FILE_NAME = "gtit-injected.json";

    private BqQuestInjector() {}

    /**
     * 任务注入入口（serverStarted，整波 ServerStarting 结束后）。
     * <p>
     * 双哨兵加固：BqCompat 探测标志 + Loader.isModLoaded 二次确认；
     * {@code BQ_Settings.curWorldDir != null} 表示 BQ 已完成世界装载流程，
     * 任何一项不满足即静默返回（BQ 缺席环境零副作用）。
     * 整体 try-catch 兜底：注入失败不影响 GTIT 主功能。
     *
     * @param assetRoot jar 内资产根目录（必须以 / 结尾，如 {@code assets/gtit/bqquests/}），
     *                  其下需存在 index.json 清单；须与 {@link #resolvePack(String)} 可识别的根一致，
     *                  否则版本戳/定义刷新/剪枝降级跳过（仅保留 get-or-create 装载）
     */
    public static void inject(String assetRoot) {
        if (!BqCompat.isBqLoaded() || !Loader.isModLoaded("betterquesting")) {
            return;
        }
        if (BQ_Settings.curWorldDir == null) {
            GTInterestingThing.LOG.warn("[GTIT-BQ] curWorldDir 未就绪（BQ 世界装载未完成），跳过本次任务注入: {}", assetRoot);
            return;
        }
        try {
            String indexResource = assetRoot + "index.json";
            JsonObject index = readJsonResource(indexResource);
            if (index == null) {
                // 资产尚未由内容编辑就位（或包未随版本发布）——info 级静默降级，不算错误
                GTInterestingThing.LOG.info("[GTIT-BQ] 未找到任务清单 {}，跳过任务注入", indexResource);
                return;
            }
            JsonArray lines = index.getAsJsonArray("questLines");
            if (lines == null) {
                GTInterestingThing.LOG.info("[GTIT-BQ] 任务清单缺少 questLines 数组，跳过任务注入: {}", indexResource);
                return;
            }
            // Missing/corrupt resources are not deletions. Validate the entire pack before replacing
            // any live definitions, pruning owned quests, or writing a successful version stamp.
            PackSpec pack = resolvePack(assetRoot);
            Map<String, NBTTagCompound> resources = prepareResources(
                lines,
                BqQuestInjector::readNbtResource,
                pack == null ? null : pack.questIdHigh);
            if (pack == null) {
                GTInterestingThing.LOG.warn("[GTIT-BQ] 无法识别任务包身份，版本戳/定义刷新/剪枝本次跳过: {}", assetRoot);
            }
            // 版本戳对账（按包）：该包戳缺失或版本不一致（含老世界无戳）→ 刷新全部已存在任务的定义与挂线坐标
            boolean refresh = pack != null && isDefinitionRefreshNeeded(pack.stampKey);
            int questCount = 0;
            int refreshedCount = 0;
            int skippedCount = 0;
            Set<UUID> expected = new HashSet<>();
            Set<UUID> expectedLines = new HashSet<>();
            Set<UUID> recreatedOwnedQuests = new HashSet<>();
            for (int i = 0; i < lines.size(); i++) {
                int[] r = loadQuestLine(
                    lines.get(i)
                        .getAsJsonObject(),
                    refresh,
                    expected,
                    expectedLines,
                    resources,
                    pack,
                    recreatedOwnedQuests);
                questCount += r[0];
                refreshedCount += r[1];
                skippedCount += r[2];
            }
            int prunedCount = 0;
            int prunedLineCount = 0;
            if (pack != null) {
                // 剪枝（无条件执行，与版本无关）：属于该包但清单中已删除的任务，摘线并移出任务数据库
                prunedCount = pruneDeletedQuests(pack, expected);
                prunedLineCount = pruneDeletedQuestLines(pack, expectedLines);
            }
            restoreProgress(recreatedOwnedQuests);
            if (refresh) {
                writeStamp(pack.stampKey);
            }
            int orderFixedCount = applyFrontRowOrdering();
            // 同步四连（与 QuestCommandDefaults.load 尾部同款）
            NetSettingSync.sendSync(null);
            NetQuestSync.quickSync(null, true, true);
            NetChapterSync.sendSync(null, null);
            SaveLoadHandler.INSTANCE.markDirty();
            GTInterestingThing.LOG.info(
                "[GTIT-BQ] 任务注入完成：{} 条任务线，{} 个新任务，{} 个定义刷新{}，{} 个已存在跳过，{} 个已删除任务清理，{} 条任务线清理，前排有序 {} 条 ({})",
                lines.size(),
                questCount,
                refreshedCount,
                refresh ? "（对齐版本 " + Tags.VERSION + "）" : "",
                skippedCount,
                prunedCount,
                prunedLineCount,
                orderFixedCount,
                assetRoot);
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[GTIT-BQ] 任务注入失败（不影响 GTIT 主功能）: " + assetRoot, t);
        }
    }

    /** Read once before mutation; an explicitly empty manifest remains a valid pack-removal request. */
    static Map<String, NBTTagCompound> prepareResources(JsonArray lines, Function<String, NBTTagCompound> reader) {
        return prepareResources(lines, reader, null);
    }

    static Map<String, NBTTagCompound> prepareResources(JsonArray lines, Function<String, NBTTagCompound> reader,
        Long ownedHigh) {
        // Exported assets carry explicit long UUID pairs. Recognized packs own their high bits;
        // unknown roots retain arbitrary UUIDs, but manifest/line/entry identities must still agree.
        Map<String, NBTTagCompound> resources = new HashMap<>();
        Set<UUID> lineIds = new HashSet<>();
        Map<UUID, String> questFiles = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            JsonObject line = lines.get(i)
                .getAsJsonObject();
            // Validate every manifest field consumed by loadQuestLine before it can change a database.
            UUID lineId = new UUID(requireLong(line, "idHigh"), requireLong(line, "idLow"));
            Math.toIntExact(requireLong(line, "orderIndex"));
            requireOwned(lineId, ownedHigh);
            if (!lineIds.add(lineId)) throw new IllegalStateException("Duplicate BQ quest line: " + lineId);
            NBTTagCompound lineTag = requireResource(
                resources,
                reader,
                line.get("lineFile")
                    .getAsString());
            if (!lineId.equals(requireId(lineTag, "questLineID"))) {
                throw new IllegalStateException("BQ quest line identity disagrees with manifest: " + lineId);
            }
            Set<UUID> entriesSeen = new HashSet<>();
            JsonArray entries = line.getAsJsonArray("entries");
            for (int j = 0; j < entries.size(); j++) {
                JsonObject entry = entries.get(j)
                    .getAsJsonObject();
                String questFile = entry.get("questFile")
                    .getAsString();
                NBTTagCompound quest = requireResource(resources, reader, questFile);
                UUID questId = requireId(quest, "questID");
                requireOwned(questId, ownedHigh);
                String priorFile = questFiles.putIfAbsent(questId, questFile);
                if ((priorFile != null && !priorFile.equals(questFile)) || !entriesSeen.add(questId)) {
                    throw new IllegalStateException("Conflicting BQ quest definition/entry: " + questId);
                }
                NBTTagCompound entryTag = requireResource(
                    resources,
                    reader,
                    entry.get("entryFile")
                        .getAsString());
                if (!questId.equals(requireId(entryTag, "questID"))) {
                    throw new IllegalStateException("BQ quest entry identity disagrees with definition: " + questId);
                }
            }
        }
        return resources;
    }

    private static long requireLong(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()
            || !value.getAsJsonPrimitive()
                .isNumber()) {
            throw new IllegalStateException("Missing/non-numeric BQ manifest field: " + key);
        }
        // Gson getAsLong silently truncates fractional and overflowing numbers, changing identity.
        return new BigDecimal(value.getAsString()).longValueExact();
    }

    private static UUID requireId(NBTTagCompound tag, String prefix) {
        // BQ readId/getLong silently substitutes zero for absent or incorrectly typed fields.
        // Bundled BQ UUID assets use two long tags; do not turn corruption into a different quest.
        if (!tag.hasKey(prefix + "High", Constants.NBT.TAG_LONG)
            || !tag.hasKey(prefix + "Low", Constants.NBT.TAG_LONG)) {
            throw new IllegalStateException("Missing/invalid BQ identity: " + prefix);
        }
        return new UUID(tag.getLong(prefix + "High"), tag.getLong(prefix + "Low"));
    }

    private static void requireOwned(UUID id, Long ownedHigh) {
        if (ownedHigh != null && id.getMostSignificantBits() != ownedHigh.longValue()) {
            throw new IllegalStateException("BQ pack references a foreign definition: " + id);
        }
    }

    private static NBTTagCompound requireResource(Map<String, NBTTagCompound> resources,
        Function<String, NBTTagCompound> reader, String path) {
        NBTTagCompound tag = resources.get(path);
        if (tag == null) {
            tag = reader.apply(path);
            if (tag == null) throw new IllegalStateException("Incomplete BQ quest pack: " + path);
            resources.put(path, tag);
        }
        return tag;
    }

    /**
     * 装载单条任务线及其下所有任务（全幂等 + 版本化定义刷新）。
     * <p>
     * 装载规则：线 get(id)==null 才 new QuestLine + readFromNBT + put；
     * 任务 get(id)==null 才 new QuestInstance + readFromNBT + put；
     * 线内条目 line.get(questId)==null 才 put(new QuestLineEntry)。
     * <p>
     * 刷新规则（{@code refresh==true}，本包版本戳对账失败时）：已存在任务重读定义
     * （进度快照→readFromNBT→merge 回填，完成/领取保留），挂线坐标用
     * {@code line.put} 直接替换（UuidDatabase map 语义）。
     *
     * @param lineSpec      index.json 中该线的声明对象
     * @param refresh       是否对已存在任务执行定义刷新
     * @param expected      该包全部清单任务 UUID 的累积集（跨线共享，供剪枝比对）
     * @param expectedLines 该包清单任务线 UUID 的累积集（跨线共享，供任务线剪枝比对）
     * @return int[]{本次新建任务数, 本次刷新定义任务数, 已存在且未刷新的跳过任务数}
     */
    private static int[] loadQuestLine(JsonObject lineSpec, boolean refresh, Set<UUID> expected,
        Set<UUID> expectedLines, Map<String, NBTTagCompound> resources, PackSpec pack, Set<UUID> recreatedOwnedQuests) {
        UUID lineId = new UUID(
            lineSpec.get("idHigh")
                .getAsLong(),
            lineSpec.get("idLow")
                .getAsLong());
        int orderIndex = lineSpec.get("orderIndex")
            .getAsInt();
        expectedLines.add(lineId);

        IQuestLine line = QuestLineDatabase.INSTANCE.get(lineId);
        boolean lineCreated = false;
        if (line == null) {
            NBTTagCompound lineTag = resources.get(
                lineSpec.get("lineFile")
                    .getAsString());
            line = new QuestLine();
            line.readFromNBT(lineTag);
            QuestLineDatabase.INSTANCE.put(lineId, line);
            lineCreated = true;
        }
        // setOrderIndex 幂等（仅重排 lineOrder 列表），无条件执行
        QuestLineDatabase.INSTANCE.setOrderIndex(lineId, orderIndex);

        int created = 0;
        int refreshed = 0;
        int skipped = 0;
        JsonArray entries = lineSpec.getAsJsonArray("entries");
        for (int i = 0; i < entries.size(); i++) {
            JsonObject entry = entries.get(i)
                .getAsJsonObject();
            NBTTagCompound questTag = resources.get(
                entry.get("questFile")
                    .getAsString());
            UUID questId = NBTConverter.UuidValueType.QUEST.readId(questTag);
            expected.add(questId);
            IQuest existing = QuestDatabase.INSTANCE.get(questId);
            if (existing == null) {
                IQuest quest = new QuestInstance();
                quest.readFromNBT(questTag);
                QuestDatabase.INSTANCE.put(questId, quest);
                // Unknown roots remain get-or-create only: never replay disk into an unowned namespace.
                if (pack != null && questId.getMostSignificantBits() == pack.questIdHigh) {
                    recreatedOwnedQuests.add(questId);
                }
                created++;
            } else if (refresh) {
                // 版本升级：重读定义（名称/描述/前置/奖励/任务整体替换），玩家进度按快照回填保留
                refreshDefinition(existing, questTag);
                refreshed++;
            } else {
                skipped++;
            }
            // 任务必须挂线（/bq_admin purge_hidden_quests 会清未挂线任务）：未挂才 put；
            // 已挂线且刷新中则用新条目整体替换（map 语义覆盖），同步编辑器坐标
            if (line.get(questId) == null) {
                NBTTagCompound entryTag = resources.get(
                    entry.get("entryFile")
                        .getAsString());
                line.put(questId, new QuestLineEntry(entryTag));
            } else if (refresh) {
                NBTTagCompound entryTag = resources.get(
                    entry.get("entryFile")
                        .getAsString());
                line.put(questId, new QuestLineEntry(entryTag));
            }
        }
        if (lineCreated || created > 0 || refreshed > 0) {
            GTInterestingThing.LOG.info(
                "[GTIT-BQ] 任务线 {} 装载：线新建={}，新任务={}，刷新定义={}，跳过={}",
                lineId,
                lineCreated,
                created,
                refreshed,
                skipped);
        }
        return new int[] { created, refreshed, skipped };
    }

    /**
     * 定义刷新（新任务覆盖老任务的核心步骤）：先快照进度，再重读定义，最后 merge 回填。
     * <p>
     * 完成状态与奖励领取标记整体保留在 completeUsers；子任务进度随快照回填，
     * 若新版定义的任务数量/顺序变化，超出部分的子任务细粒度进度被丢弃
     * （任务级完成与奖励领取不受影响）。
     */
    private static void refreshDefinition(IQuest quest, NBTTagCompound defTag) {
        NBTTagCompound progress = quest.writeProgressToNBT(new NBTTagCompound(), null);
        quest.readFromNBT(defTag);
        quest.readProgressFromNBT(progress, true);
    }

    /**
     * 剪枝（无条件执行，与版本戳无关）：任务数据库中 UUID high 等于该包常量、
     * 但不在本包 index.json 清单内的任务视为已删除——先摘线（从所有任务线移除条目）
     * 再移出任务数据库（进度文件残留无害，进度回放只更新库内任务）。
     * <p>
     * 与 GTSWN 范本的差异：GTSWN 为线内剪枝（只处理本线已挂条目），此处放宽为
     * 按 {@link PackSpec#questIdHigh} 的数据库级剪枝，可覆盖未挂线/挂错线的遗留任务。
     *
     * @return 本次剪枝删除的任务数
     */
    private static int pruneDeletedQuests(PackSpec pack, Set<UUID> expected) {
        List<UUID> stale = QuestDatabase.INSTANCE.orderedEntries()
            .map(Map.Entry::getKey)
            .filter(id -> id.getMostSignificantBits() == pack.questIdHigh && !expected.contains(id))
            .collect(Collectors.toList());
        for (UUID id : stale) {
            QuestLineDatabase.INSTANCE.removeQuest(id);
            QuestDatabase.INSTANCE.remove(id);
        }
        if (!stale.isEmpty()) {
            GTInterestingThing.LOG.info("[GTIT-BQ] 包 {} 清理已删除任务 {} 个: {}", pack.stampKey, stale.size(), stale);
        }
        return stale.size();
    }

    /**
     * 清理属于该包但不在清单中的任务线本体。removeQuest 只摘条目不移线，
     * 因此需直接从任务线数据库移除，先收集再逐个 remove 避免并发修改。
     *
     * @return 本次剪枝删除的任务线数量
     */
    private static int pruneDeletedQuestLines(PackSpec pack, Set<UUID> expectedLines) {
        List<UUID> staleLines = QuestLineDatabase.INSTANCE.orderedEntries()
            .map(Map.Entry::getKey)
            .filter(id -> id.getMostSignificantBits() == pack.questIdHigh && !expectedLines.contains(id))
            .collect(Collectors.toList());
        for (UUID lineId : staleLines) {
            QuestLineDatabase.INSTANCE.remove(lineId);
        }
        if (!staleLines.isEmpty()) {
            GTInterestingThing.LOG
                .info("[GTIT-BQ] 包 {} 清理已删除任务线 {} 条: {}", pack.stampKey, staleLines.size(), staleLines);
        }
        return staleLines.size();
    }

    /**
     * 前排有序规则（规范.md §6 条款 10）：按 gtsr→gtswn→gtit 的完整 UUID high（ASCII 标识左移 32 位）
     * 顺序，对库内存在（UUID high 位命中）的线依序 setOrderIndex(lineId, slot)（slot=存在线相对序位）。
     * setOrderIndex 幂等（remove+clamp 插入），与跨 mod 注入次序无关，最后执行者定最终序；
     * 禁用 setOrderedEntries（其首行整库 clear）。
     *
     * @return 本次执行 setOrderIndex 的线数
     */
    private static int applyFrontRowOrdering() {
        int applied = 0;
        int slot = 0;
        for (long high : FRONT_ROW_LINE_HIGH) {
            UUID lineId = QuestLineDatabase.INSTANCE.orderedEntries()
                .map(Map.Entry::getKey)
                .filter(id -> id.getMostSignificantBits() == high)
                .findFirst()
                .orElse(null);
            if (lineId == null) {
                continue;
            }
            QuestLineDatabase.INSTANCE.setOrderIndex(lineId, slot++);
            applied++;
        }
        if (applied > 0) {
            GTInterestingThing.LOG.info("[GTIT-BQ] 前排有序完成：依序 setOrderIndex {} 条（gtsr→gtswn→gtit）", applied);
        }
        return applied;
    }

    /**
     * 按资产根解析包身份（世界侧版本戳键 + 剪枝用 questIDHigh 常量）。
     * 取值与 {@code MiaoGtnhHost.GTIT_PACK_ROOT}/{@code MIAO_PACK_ROOT} 对齐；
     * 未知资产根返回 null——调用方按 get-or-create 降级并跳过戳/刷新/剪枝（warn 可见）。
     */
    private static PackSpec resolvePack(String assetRoot) {
        if ("assets/gtit/bqquests/miao/".equals(assetRoot)) {
            return PACK_MIAO;
        }
        if ("assets/gtit/bqquests/".equals(assetRoot)) {
            return PACK_GTIT;
        }
        return null;
    }

    /**
     * 版本戳判定（按包）：世界目录缺少版本戳文件、缺该包键或键值与当前构建
     * {@link Tags#VERSION} 不同时返回 true（老世界无戳视为待升级刷一次）。
     */
    private static boolean isDefinitionRefreshNeeded(String stampKey) {
        JsonObject root = readStampFile();
        if (root == null || !root.has(stampKey)
            || !root.get(stampKey)
                .isJsonPrimitive()) {
            return true;
        }
        String stamped = root.get(stampKey)
            .getAsString();
        return !stamped.equals(Tags.VERSION);
    }

    /**
     * 读取世界侧版本戳文件（{@value #STAMP_FILE_NAME}），返回其 JSON 根对象；
     * 缺失或解析失败返回 null（调用方按需要刷新处理）。
     */
    private static JsonObject readStampFile() {
        File f = new File(BQ_Settings.curWorldDir, STAMP_FILE_NAME);
        if (!f.isFile()) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8),
            1024)) {
            return new JsonParser().parse(br)
                .getAsJsonObject();
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("[GTIT-BQ] 版本戳读取失败，本次按需要刷新处理", e);
            return null;
        }
    }

    /**
     * 写入世界侧版本戳（双键读改写：只更新本包键，保留另一包键）。
     * 失败仅告警不阻断；旧戳不截断，下次启动重试定义刷新。
     */
    private static void writeStamp(String stampKey) {
        File f = new File(BQ_Settings.curWorldDir, STAMP_FILE_NAME);
        try {
            BqStampFile.update(f, stampKey, Tags.VERSION);
        } catch (Exception e) {
            GTInterestingThing.LOG.warn("[GTIT-BQ] 版本戳写入失败（下次启动可能重复刷新一次定义）", e);
        }
    }

    /**
     * 只恢复本次调用实际重建的已识别包内任务在 default load 缺失定义窗口内丢弃的进度。
     * <p>
     * 每个包独立持有恢复白名单，空 miao 包及未知资产根不会重放任何进度。
     * 已有任务（包括定义刷新过的任务）持有 BQ 恢复后的权威内存进度；merge=true
     * 仍会替换完成/领取记录，不能用磁盘旧值覆盖。旧版汇总文件先读，逐玩家文件后读，
     * 与 BQ SaveLoadHandler.loadProgress 的格式及优先级一致。只通过原生 dirty-player
     * API 安排恢复后的用户在正常存盘时持久化；本读取过程不修改进度文件。
     */
    private static void restoreProgress(Set<UUID> recreatedOwnedQuests) {
        if (recreatedOwnedQuests.isEmpty()) {
            return;
        }
        File legacy = new File(BQ_Settings.curWorldDir, "QuestProgress.json");
        if (legacy.isFile()) {
            restoreProgressFile(legacy, recreatedOwnedQuests, false);
        }
        File dir = new File(BQ_Settings.curWorldDir, "QuestProgress");
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isFile() && f.getName()
                .endsWith(".json")) {
                restoreProgressFile(f, recreatedOwnedQuests, true);
            }
        }
    }

    /** 仅识别本包使用的三个原生任务类的已知用户字段，不遍历自定义任务或任务数据。 */
    private static void collectRestoredUsers(QuestInstance quest, Set<UUID> users) {
        quest.getUsersWithCompletionData(users);
        for (DBEntry<ITask> entry : quest.getTasks()
            .getEntries()) {
            ITask task = entry.getValue();
            Class<?> type = task.getClass();
            if (type != TaskCheckbox.class && type != TaskRetrieval.class && type != TaskCrafting.class) {
                continue;
            }
            // 原生 TaskBase / TaskProgressableBase 输出的顶层用户列表；不读取 data 载荷。
            NBTTagCompound progress = task.writeProgressToNBT(new NBTTagCompound(), null);
            NBTTagList completed = progress.getTagList("completeUsers", Constants.NBT.TAG_STRING);
            for (int i = 0; i < completed.tagCount(); i++) {
                users.add(UUID.fromString(completed.getStringTagAt(i)));
            }
            NBTTagList partial = progress.getTagList("userProgress", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < partial.tagCount(); i++) {
                users.add(
                    UUID.fromString(
                        partial.getCompoundTagAt(i)
                            .getString("uuid")));
            }
        }
    }

    private static void restoreProgressFile(File file, Set<UUID> recreatedOwnedQuests, boolean merge) {
        // JsonHelper.ReadFromFile 会备份损坏文件；这里只读，不改写任何进度文件。
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8),
            32768)) {
            JsonObject json = new JsonParser().parse(reader)
                .getAsJsonObject();
            NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
            NBTTagList progress = nbt.getTagList("questProgress", Constants.NBT.TAG_COMPOUND);
            NBTTagList selected = new NBTTagList();
            Set<UUID> selectedIds = new HashSet<>();
            for (int i = 0; i < progress.tagCount(); i++) {
                NBTTagCompound entry = progress.getCompoundTagAt(i);
                // 与原生 QuestDatabase.readProgressFromNBT 一样支持 UUID 和旧整数 ID。
                UUID questId = NBTConverter.UuidValueType.QUEST.tryReadId(entry)
                    .orElse(null);
                if (questId == null && entry.hasKey("questID", Constants.NBT.TAG_ANY_NUMERIC)) {
                    questId = UuidConverter.convertLegacyId(entry.getInteger("questID"));
                }
                if (recreatedOwnedQuests.contains(questId)) {
                    selected.appendTag(entry);
                    selectedIds.add(questId);
                }
            }
            QuestDatabase.INSTANCE.readProgressFromNBT(selected, merge);
            // 与 BQ 旧版进度迁移相同，通过原生 API 合并 dirty 用户，不清空已有集合。
            Set<UUID> restoredUsers = new HashSet<>();
            for (UUID id : selectedIds) {
                IQuest quest = QuestDatabase.INSTANCE.get(id);
                if (quest instanceof QuestInstance) {
                    collectRestoredUsers((QuestInstance) quest, restoredUsers);
                }
            }
            SaveLoadHandler.INSTANCE.addDirtyPlayers(restoredUsers);
        } catch (Throwable t) {
            GTInterestingThing.LOG.warn("[GTIT-BQ] 进度回填跳过无法解析的文件: {}", file.getName(), t);
        }
    }

    /**
     * 从 classloader 资源读 JSON 并转为 NBTTagCompound。
     * <p>
     * 与 BQ 官方装载同构：{@code NBTConverter.JSONtoNBT_Object(json, tag, format=true)}
     * （"键:类型ID" 后缀在此剥离）。dev（classes 目录）与 prod（jar）行为一致。
     *
     * @param path 资源路径（完整 classpath 路径）
     * @return NBT 化的定义，资源缺失或解析失败返回 null
     */
    private static NBTTagCompound readNbtResource(String path) {
        JsonObject json = readJsonResource(path);
        if (json == null) {
            return null;
        }
        validateResourceIds(json);
        return NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
    }

    static void validateResourceIds(JsonObject json) {
        // The native converter uses Number.longValue(), which loses fractions/overflow before
        // NBT identity validation can see it. Check declared UUID longs while still in JSON form.
        for (String key : new String[] { "questIDHigh:4", "questIDLow:4", "questLineIDHigh:4", "questLineIDLow:4" }) {
            if (json.has(key)) requireLong(json, key);
        }
    }

    /**
     * 从 classloader 资源读 JsonObject（Gson 用 MC 1.7.10 自带 com.google.gson，
     * 与 BQ JsonHelper.ReadFromFile 的解析方式对齐：UTF-8 + 32KB 缓冲）。
     *
     * @param path 资源路径
     * @return 解析结果，资源缺失或解析失败返回 null
     */
    private static JsonObject readJsonResource(String path) {
        InputStream is = BqQuestInjector.class.getClassLoader()
            .getResourceAsStream(path);
        if (is == null) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 32768)) {
            return new JsonParser().parse(br)
                .getAsJsonObject();
        } catch (Exception e) {
            GTInterestingThing.LOG.error("[GTIT-BQ] 资源 JSON 解析失败: {}", path, e);
            return null;
        }
    }

    /**
     * 单包身份：世界侧版本戳键名 + 该包全部任务共用的 questIDHigh 常量（剪枝判据，
     * 即 index.json questLines[].idHigh——任务 UUID 与任务线 UUID 共用同一 high 位）。
     */
    private static final class PackSpec {

        final String stampKey;
        final long questIdHigh;

        PackSpec(String stampKey, long questIdHigh) {
            this.stampKey = stampKey;
            this.questIdHigh = questIdHigh;
        }
    }
}
