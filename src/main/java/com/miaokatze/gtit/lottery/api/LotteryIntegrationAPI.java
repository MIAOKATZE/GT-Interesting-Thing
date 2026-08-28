package com.miaokatze.gtit.lottery.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.miaokatze.gtit.lottery.LotteryConfig;
import com.miaokatze.gtit.lottery.LotteryManager;
import com.miaokatze.gtit.lottery.LotteryNetworkManager;
import com.miaokatze.gtit.lottery.LotteryPool;
import com.miaokatze.gtit.lottery.LotteryRarity;
import com.miaokatze.gtit.trade.api.JarAssetManifest;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.util.NbtBase64Util;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 外部抽奖池组注册 API（E4b，BQ 式 jar 资产整合）。
 * <p>
 * 与贸易整合 API（{@code NekoTradeIntegrationAPI}）同构的三通道注册，收敛到同一应用路径：
 * <ul>
 * <li>直连：其它 mod 类路径调用 {@link #registerLotteryPool(LotteryPoolGroupDef)}
 * （线程安全——入队，服务器就绪时应用；运行期注册则立即应用）</li>
 * <li>IMC：{@code FMLInterModComms.sendMessage("gtit", "gtit:registerLotteryPool", nbt)}，
 * NBT 载荷含 {@code groupJson} 字符串（{@link LotteryPoolGroupDef} 的 JSON），
 * 由 mod 主类的 IMC 事件处理器路由进 {@link #handleImcMessages}</li>
 * <li>jar 资产：{@link #registerLotteryPoolsFromJar} 读
 * {@code assets/<ownerModId>/gtit/lottery/index.json} 清单逐条装载组定义
 * （1.7.10 jar 目录不可枚举，BQ 式显式清单驱动）</li>
 * </ul>
 * 应用规则（镜像贸易 API 的记账语义，适配抽奖）：
 * <ul>
 * <li>组注册状态与版本记账在 {@code config/gtit/lottery/integrated/<groupId>.json}
 * （记录源版本与本组注入的 poolId 清单）</li>
 * <li>已注册且源 version 未变 → 跳过（玩家对 {@code lottery.json} 的编辑保持权威）</li>
 * <li>源 version 变化（升或降）→ 按记账移除旧池（仅移除仍存在且属于记账证明的池）后重注册</li>
 * <li>目标池 id 已存在于当前配置且不在旧记账中 → 玩家自建冲突，该池 WARN 拒绝覆盖，
 * 其余池继续；玩家手动删除记账文件 = 强制重注册</li>
 * </ul>
 * 落库路径与磁盘链一致：池合并进 {@code LotteryConfig} 磁盘文件 →
 * {@code LotteryManager.loadConfig()} 重建内存卡池表 →
 * {@code LotteryNetworkManager.sendSyncToAll()} 推送在线客户端。
 */
public final class LotteryIntegrationAPI {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** IMC 消息 key（外部 mod 经 FMLInterModComms 注册抽奖池组） */
    public static final String IMC_KEY = "gtit:registerLotteryPool";

    /** IMC NBT 载荷中承载组定义 JSON 的字段名 */
    public static final String IMC_NBT_FIELD = "groupJson";

    /** 记账文件目录（相对游戏根目录） */
    private static final String INTEGRATED_DIR = "config/gtit/lottery/integrated";

    /**
     * 组定义/记账 Gson：池模型与 {@code LotteryConfig} 的 lottery.json 格式完全一致
     * （rarity 大小写不敏感 + NekoBigItemStack 的 item/meta/amount/nbtBase64/oreDict 适配器，
     * 适配器注册复制自 LotteryConfig 以保持 jar 资产 JSON 与磁盘配置互认；
     * 记账文件用紧凑输出，与贸易记账一致）。
     */
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(
            LotteryRarity.class,
            (JsonSerializer<LotteryRarity>) (src, typeOfSrc, context) -> context.serialize(src.name()))
        .registerTypeAdapter(
            LotteryRarity.class,
            (JsonDeserializer<LotteryRarity>) (json, typeOfT, context) -> LotteryRarity.fromString(json.getAsString()))
        .registerTypeAdapter(NekoBigItemStack.class, (JsonSerializer<NekoBigItemStack>) (src, typeOfSrc, context) -> {
            JsonObject obj = new JsonObject();
            ItemStack base = src.getBaseStack();
            if (base != null && base.getItem() != null) {
                obj.addProperty(
                    "item",
                    Item.itemRegistry.getNameForObject(base.getItem())
                        .toString());
                obj.addProperty("meta", base.getItemDamage());
                if (base.hasTagCompound() && base.getTagCompound() != null) {
                    obj.addProperty("nbtBase64", NbtBase64Util.nbtToBase64(base.getTagCompound()));
                }
            } else {
                obj.addProperty("item", "");
                obj.addProperty("meta", 0);
            }
            obj.addProperty("amount", src.getStackSize());
            if (src.hasOreDict()) {
                obj.addProperty("oreDict", src.getOreDict());
            }
            return obj;
        })
        .registerTypeAdapter(NekoBigItemStack.class, (JsonDeserializer<NekoBigItemStack>) (json, typeOfT, context) -> {
            JsonObject obj = json.getAsJsonObject();
            String itemId = obj.has("item") ? obj.get("item")
                .getAsString() : "";
            int meta = obj.has("meta") ? obj.get("meta")
                .getAsInt() : 0;
            int amount = obj.has("amount") ? obj.get("amount")
                .getAsInt() : 1;
            String oreDict = obj.has("oreDict") ? obj.get("oreDict")
                .getAsString() : "";
            String[] parts = itemId.split(":", 2);
            Item item = parts.length == 2 ? GameRegistry.findItem(parts[0], parts[1]) : null;
            if (item == null) return null; // 物品不存在：Gson 列表会保留 null，加载后由迁移步骤清理
            ItemStack stack = new ItemStack(item, 1, meta);
            if (obj.has("nbtBase64")) {
                NBTTagCompound nbt = NbtBase64Util.nbtFromBase64(
                    obj.get("nbtBase64")
                        .getAsString());
                if (nbt != null) stack.setTagCompound(nbt);
            }
            return new NekoBigItemStack(Math.max(1, amount), oreDict, stack);
        })
        .create();

    /** 待应用池组队列（IMC/直连在服务器就绪前入队，serverStarted 统一消费） */
    private static final Queue<LotteryPoolGroupDef> PENDING = new ConcurrentLinkedQueue<>();

    /** 应用过程互斥（配置合并 + 落盘 + 重载的原子性由本锁串行化） */
    private static final Object APPLY_LOCK = new Object();

    private LotteryIntegrationAPI() {}

    // ==================== 注册入口（三通道） ====================

    /**
     * 注册一个外部抽奖池组（线程安全）。
     * <p>
     * 服务器未就绪（无世界）时仅入队，由 {@link #applyQueuedPools()} 在
     * serverStarted（抽奖配置装载之后）消费；服务器运行期调用则立即应用（热注册，
     * 在线玩家立即收到全量同步）。
     *
     * @param def 组定义（null 或 groupId 非法时告警丢弃）
     */
    public static void registerLotteryPool(LotteryPoolGroupDef def) {
        if (!isValidDef(def)) {
            LOG.warn("[LotteryAPI] 抽奖池组定义无效（groupId 缺失或不合法），已丢弃");
            return;
        }
        PENDING.offer(def);
        LOG.info(
            "[LotteryAPI] 抽奖池组已入队等待应用: {}（version={}，pools={}）",
            def.getGroupId(),
            def.getVersion(),
            def.getPools() == null ? 0
                : def.getPools()
                    .size());
        if (isServerReady()) {
            applyQueuedPools();
        }
    }

    /**
     * IMC 消息消费（由 mod 主类的 IMC 事件处理器委托）。
     * <p>
     * 只认 {@link #IMC_KEY} 且为 NBT 载荷的消息；{@code groupJson} 字符串
     * 按 {@link LotteryPoolGroupDef} 反序列化后走 {@link #registerLotteryPool} 同一路径，
     * 单条消息异常逐条吞掉告警，不中断其余消息。
     *
     * @param messages 本次 IMC 事件投递的全部消息
     */
    public static void handleImcMessages(ImmutableList<FMLInterModComms.IMCMessage> messages) {
        if (messages == null || messages.isEmpty()) return;
        for (FMLInterModComms.IMCMessage msg : messages) {
            if (msg == null || !IMC_KEY.equals(msg.key)) continue;
            String sender = msg.getSender();
            try {
                if (!msg.isNBTMessage()) {
                    LOG.warn("[LotteryAPI] 忽略非 NBT 载荷的 IMC 消息（发送方: {}）", sender);
                    continue;
                }
                NBTTagCompound nbt = msg.getNBTValue();
                String json = nbt == null ? "" : nbt.getString(IMC_NBT_FIELD);
                if (json == null || json.isEmpty()) {
                    LOG.warn("[LotteryAPI] IMC 载荷缺少 {} 字段（发送方: {}）", IMC_NBT_FIELD, sender);
                    continue;
                }
                LotteryPoolGroupDef def = GSON.fromJson(json, LotteryPoolGroupDef.class);
                if (!isValidDef(def)) {
                    LOG.warn("[LotteryAPI] IMC 池组定义无效，已丢弃（发送方: {}）", sender);
                    continue;
                }
                LOG.info("[LotteryAPI] 收到 IMC 抽奖池组注册: {}（发送方: {}）", def.getGroupId(), sender);
                registerLotteryPool(def);
            } catch (Throwable t) {
                LOG.error("[LotteryAPI] IMC 抽奖池组消息处理失败（发送方: " + sender + "）", t);
            }
        }
    }

    /**
     * 消费待应用队列（serverStarted 在 {@code LotteryManager.init} 装载抽奖配置之后调用，
     * 或运行期注册时内部触发）。单组异常记 error，不中断队列、不炸服务器启动。
     */
    public static void applyQueuedPools() {
        if (PENDING.isEmpty()) return;
        synchronized (APPLY_LOCK) {
            LotteryPoolGroupDef def;
            while ((def = PENDING.poll()) != null) {
                try {
                    applyGroup(def);
                } catch (Throwable t) {
                    LOG.error("[LotteryAPI] 抽奖池组应用失败: " + def.getGroupId(), t);
                }
            }
        }
    }

    // ==================== 核心应用逻辑 ====================

    /**
     * 应用单个池组（记账门控 + 磁盘合并 + 落库同步）。
     *
     * @param def 组定义
     * @return true = 本次发生注册或版本更新；false = 跳过（版本未变/前置缺失/组无效）
     */
    static boolean applyGroup(LotteryPoolGroupDef def) {
        if (!isValidDef(def)) return false;
        // 前置 mod 短路：任一缺席整组跳过（不写记账，环境补齐后下次启动自动生效）
        if (def.getRequiresMods() != null) {
            for (String modId : def.getRequiresMods()) {
                if (modId != null && !modId.isEmpty() && !Loader.isModLoaded(modId)) {
                    LOG.info("[LotteryAPI] 抽奖池组 {} 的前置 mod {} 缺席，跳过注册", def.getGroupId(), modId);
                    return false;
                }
            }
        }
        synchronized (APPLY_LOCK) {
            // 组校验：pools 空整组拒绝；逐池过 LotteryPool.validate()（内建截断到 10 条，
            // 与 LotteryConfig 加载路径一致），不过的池 warn 跳过；全组池皆废则拒绝
            if (def.getPools() == null || def.getPools()
                .isEmpty()) {
                LOG.warn("[LotteryAPI] 抽奖池组 {} 缺少 pools，拒绝注册", def.getGroupId());
                return false;
            }
            List<LotteryPool> candidatePools = new ArrayList<>();
            for (LotteryPool src : def.getPools()) {
                if (src == null) continue;
                if (src.getId() == null || src.getId()
                    .isEmpty()) {
                    LOG.warn("[LotteryAPI] 抽奖池组 {} 内池缺少 id，跳过该池", def.getGroupId());
                    continue;
                }
                if (!src.validate()) {
                    LOG.warn("[LotteryAPI] 抽奖池组 {} 内池 {} 校验失败（条目为空或总权重 0），跳过该池", def.getGroupId(), src.getId());
                    continue;
                }
                candidatePools.add(src);
            }
            if (candidatePools.isEmpty()) {
                LOG.warn("[LotteryAPI] 抽奖池组 {} 全部池无效，拒绝注册", def.getGroupId());
                return false;
            }
            // 记账门控：已注册且源版本未变 → 跳过（玩家本地 lottery.json 编辑权威）
            PoolGroupRecord record = PoolGroupRecord.load(def.getGroupId());
            if (record != null && record.version == def.getVersion()) {
                LOG.info(
                    "[LotteryAPI] 抽奖池组 {} 已注册且源版本未变（{}），跳过（尊重玩家对 lottery.json 的编辑）",
                    def.getGroupId(),
                    def.getVersion());
                return false;
            }
            if (record != null) {
                LOG.info(
                    "[LotteryAPI] 抽奖池组 {} 源版本变化（{} -> {}），按记账移除旧池后重注册",
                    def.getGroupId(),
                    record.version,
                    def.getVersion());
            }
            // 磁盘合并：读全量 → 移除旧记账池（仅移除仍存在且属于记账证明的池）→ 冲突门控合并新池
            LotteryConfig.LotteryConfigData data = LotteryConfig.load();
            if (data.pools == null) {
                data.pools = new ArrayList<>();
            }
            if (record != null && !record.poolIds.isEmpty()) {
                data.pools.removeIf(pool -> pool != null && record.poolIds.contains(pool.getId()));
            }
            PoolGroupRecord newRecord = new PoolGroupRecord(def.getGroupId(), def.getVersion());
            Set<String> upsertedIds = new HashSet<>();
            for (LotteryPool src : candidatePools) {
                LotteryPool pool = deepCopy(src);
                String poolId = pool.getId();
                boolean ownedByRecord = record != null && record.poolIds.contains(poolId);
                boolean exists = false;
                for (LotteryPool existing : data.pools) {
                    if (existing != null && poolId.equals(existing.getId())) {
                        exists = true;
                        break;
                    }
                }
                if (exists && !ownedByRecord && !upsertedIds.contains(poolId)) {
                    LOG.warn("[LotteryAPI] 卡池 {} 已被玩家本地配置占用（非本组记账所有），拒绝覆盖该池（组 {}）", poolId, def.getGroupId());
                    continue;
                }
                // 写入/覆盖（记账所有或本组本轮已写入的重复 id 直接替换）
                boolean replaced = false;
                for (int i = 0; i < data.pools.size(); i++) {
                    if (data.pools.get(i) != null && poolId.equals(
                        data.pools.get(i)
                            .getId())) {
                        data.pools.set(i, pool);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    data.pools.add(pool);
                }
                upsertedIds.add(poolId);
                if (!newRecord.poolIds.contains(poolId)) {
                    newRecord.poolIds.add(poolId);
                }
            }
            if (newRecord.poolIds.isEmpty()) {
                LOG.warn("[LotteryAPI] 抽奖池组 {} 无可合并的有效池（全部冲突或无效），跳过落盘", def.getGroupId());
                return false;
            }
            // 落库链：磁盘保存 → 记账保存 → 内存重载 → 在线客户端全量同步（无在线玩家时空操作）
            LotteryConfig.save(data);
            newRecord.save();
            LotteryManager.INSTANCE.loadConfig();
            LotteryNetworkManager.sendSyncToAll();
            LOG.info(
                "[LotteryAPI] 抽奖池组 {} 注册完成：{} 个卡池（version={}）",
                def.getGroupId(),
                newRecord.poolIds.size(),
                def.getVersion());
            return true;
        }
    }

    /**
     * 按记账注销一个池组（无记账时静默返回 false）。
     * <p>
     * 移除范围：记账清单内仍存在的池（仅移除属于记账证明的池，玩家自建同名池不受影响——
     * 记账缺失时本就无从证明所有权）。
     *
     * @param groupId 组 ID
     * @return true = 本次发生移除并已落库同步
     */
    public static boolean unregisterLotteryPool(String groupId) {
        if (!JarAssetManifest.isValidGroupId(groupId)) return false;
        synchronized (APPLY_LOCK) {
            PoolGroupRecord record = PoolGroupRecord.load(groupId);
            if (record == null) {
                return false;
            }
            LotteryConfig.LotteryConfigData data = LotteryConfig.load();
            if (data.pools != null && !record.poolIds.isEmpty()) {
                data.pools.removeIf(pool -> pool != null && record.poolIds.contains(pool.getId()));
            }
            LotteryConfig.save(data);
            record.delete();
            LotteryManager.INSTANCE.loadConfig();
            LotteryNetworkManager.sendSyncToAll();
            LOG.info("[LotteryAPI] 抽奖池组 {} 已按记账注销（{} 个卡池）", groupId, record.poolIds.size());
            return true;
        }
    }

    // ==================== jar 资产通道（BQ 式 index.json 清单） ====================

    /**
     * 从本 mod jar 资产装载抽奖池组清单并逐条注册（BQ 式，E4b）。
     * <p>
     * 清单位于 {@code assets/<ownerModId>/gtit/lottery/index.json}
     * （{@link JarAssetManifest} schema），每条 {@code path} 指向相对资产根的
     * {@link LotteryPoolGroupDef} JSON（如 {@code pools/<group>.json}）。
     * 1.7.10 jar 目录不可枚举，故以显式清单驱动逐文件读取。
     * <ul>
     * <li>清单缺失：资产可选，info 静默返回</li>
     * <li>清单无效（formatVersion != 1 / 结构损坏）：error 拒绝整清单</li>
     * <li>单项资产损坏/缺失：error 跳过该组，不中断其余</li>
     * </ul>
     * 建议第三方 mod 在 {@code postInit}（物品注册完成后）调用；
     * 服务器未就绪时自动入队，serverStarted 统一应用。
     *
     * @param ownerModId 资产归属 mod id（决定资产根目录，空值告警忽略）
     */
    public static void registerLotteryPoolsFromJar(String ownerModId) {
        if (ownerModId == null || ownerModId.isEmpty()) {
            LOG.warn("[LotteryAPI] registerLotteryPoolsFromJar 需要 ownerModId，已忽略");
            return;
        }
        String root = "assets/" + ownerModId + "/gtit/lottery/";
        JsonObject indexJson = JarAssetManifest.readJsonResource(root + "index.json");
        if (indexJson == null) {
            // jar 资产可选：清单缺失不算错误（mod 未随包分发资产时零噪音）
            LOG.info("[LotteryAPI] 未找到抽奖池资产清单 {}index.json（jar 资产可选），跳过", root);
            return;
        }
        JarAssetManifest manifest = JarAssetManifest.parse(indexJson);
        if (manifest == null) {
            LOG.error(
                "[LotteryAPI] 抽奖池资产清单 {}index.json 无效（formatVersion 必须为 {}，条目需含合法 groupId 与安全 path），拒绝整清单",
                root,
                JarAssetManifest.CURRENT_FORMAT_VERSION);
            return;
        }
        int applied = 0;
        for (JarAssetManifest.GroupEntry entry : manifest.getGroups()) {
            try {
                JsonObject groupJson = JarAssetManifest.readJsonResource(root + entry.getPath());
                if (groupJson == null) {
                    LOG.error("[LotteryAPI] 抽奖池资产文件缺失或损坏，跳过该组: {}（owner: {}）", root + entry.getPath(), ownerModId);
                    continue;
                }
                LotteryPoolGroupDef def = GSON.fromJson(groupJson, LotteryPoolGroupDef.class);
                if (!isValidDef(def)) {
                    LOG.warn("[LotteryAPI] 抽奖池资产 {} 组定义无效，跳过（owner: {}）", entry.getGroupId(), ownerModId);
                    continue;
                }
                // 清单身份对齐：清单 groupId/version 与文件内不一致时以清单为准（同贸易语义）
                if (JarAssetManifest.isValidGroupId(entry.getGroupId()) && !entry.getGroupId()
                    .equals(def.getGroupId())) {
                    LOG.warn(
                        "[LotteryAPI] 抽奖池资产清单 groupId（{}）与文件内 groupId（{}）不一致，以清单为准（owner: {}）",
                        entry.getGroupId(),
                        def.getGroupId(),
                        ownerModId);
                    def.setGroupId(entry.getGroupId());
                }
                if (entry.getVersion() > 0 && entry.getVersion() != def.getVersion()) {
                    LOG.warn(
                        "[LotteryAPI] 抽奖池资产清单 version（{}）与文件内 version（{}）不一致，以清单为准（owner: {}）",
                        entry.getVersion(),
                        def.getVersion(),
                        ownerModId);
                    def.setVersion(entry.getVersion());
                }
                registerLotteryPool(def);
                applied++;
            } catch (Throwable t) {
                LOG.error(
                    "[LotteryAPI] 抽奖池资产 " + entry.getGroupId() + " 读取/注册失败，跳过不中断其余（owner: " + ownerModId + "）",
                    t);
            }
        }
        LOG.info(
            "[LotteryAPI] jar 抽奖池资产装载完成：{}/{} 组（owner: {}）",
            applied,
            manifest.getGroups()
                .size(),
            ownerModId);
    }

    // ==================== 辅助 ====================

    /** 服务器是否已具备应用条件（世界已装载；与贸易整合 API 的就绪判定一致） */
    private static boolean isServerReady() {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.getEntityWorld() != null;
    }

    /** 组定义基础校验（groupId 白名单与贸易组一致，规则收敛在 {@link JarAssetManifest}） */
    private static boolean isValidDef(LotteryPoolGroupDef def) {
        if (def == null) return false;
        return JarAssetManifest.isValidGroupId(def.getGroupId());
    }

    /** Gson 往返深拷贝（合并进磁盘配置前隔离调用方持有的 def 实例，与贸易 API 的 deepCopy 语义一致） */
    private static LotteryPool deepCopy(LotteryPool src) {
        return GSON.fromJson(GSON.toJson(src, LotteryPool.class), LotteryPool.class);
    }

    /** 测试与调试用：暴露与注册管线一致的 Gson 配置（包级可见） */
    static Gson gson() {
        return GSON;
    }

    /**
     * 组记账文件（config/gtit/lottery/integrated/&lt;groupId&gt;.json）。
     * <p>
     * 记录源版本与本组注入的 poolId 清单：版本未变跳过（尊重玩家编辑）、
     * 版本变化按清单精准移除、删除文件强制重注册。
     * <p>
     * 包级可见（非 private）供同包单元测试直接构造与读写。
     */
    static final class PoolGroupRecord {

        // 字段刻意包级可见：Gson 反序列化直接反射赋值（不依赖 Unsafe 构造），同包单元测试亦可直接读写
        String groupId;
        int version;
        final List<String> poolIds = new ArrayList<>();

        PoolGroupRecord(String groupId, int version) {
            this.groupId = groupId;
            this.version = version;
        }

        Path path() {
            return Paths.get(INTEGRATED_DIR, groupId + ".json");
        }

        void save() {
            try {
                Path path = path();
                Files.createDirectories(path.getParent());
                Files.write(
                    path,
                    GSON.toJson(this)
                        .getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                LOG.error("[LotteryAPI] 抽奖池组记账写入失败: " + groupId, e);
            }
        }

        void delete() {
            try {
                Files.deleteIfExists(path());
            } catch (Exception e) {
                LOG.error("[LotteryAPI] 抽奖池组记账删除失败: " + groupId, e);
            }
        }

        static PoolGroupRecord load(String groupId) {
            try {
                Path path = Paths.get(INTEGRATED_DIR, groupId + ".json");
                if (!Files.exists(path)) return null;
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                PoolGroupRecord record = GSON.fromJson(json, PoolGroupRecord.class);
                // 关键字段缺失或 groupId 不符视为无效记账（等同强制重注册）
                if (record == null || !groupId.equals(record.groupId) || record.poolIds == null) {
                    return null;
                }
                return record;
            } catch (Exception e) {
                LOG.warn("[LotteryAPI] 抽奖池组记账读取失败，按未注册处理: " + groupId, e);
                return null;
            }
        }
    }
}
