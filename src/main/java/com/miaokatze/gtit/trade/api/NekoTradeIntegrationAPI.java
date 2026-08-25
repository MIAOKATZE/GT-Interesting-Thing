package com.miaokatze.gtit.trade.api;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.v2.NekoTradeNetworkManager;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;

/**
 * 外部贸易组注册 API（E4a）。
 * <p>
 * 双通道注册，收敛到同一应用路径：
 * <ul>
 * <li>直连：其它 mod 类路径调用 {@link #registerTradeGroup(NekoTradeGroupDef)}
 * （线程安全——入队，服务器就绪时应用；运行期注册则立即应用）</li>
 * <li>IMC：{@code FMLInterModComms.sendMessage("gtit", "gtit:registerTradeGroup", nbt)}，
 * NBT 载荷含 {@code groupJson} 字符串（{@link NekoTradeGroupDef} 的 JSON），
 * 由 mod 主类的 IMC 事件处理器路由进 {@link #handleImcMessages}</li>
 * </ul>
 * jar 资产通道（E4b，BQ 式 index.json 清单）：{@link #registerTradeAssetsFromJar}
 * 读 {@code assets/<ownerModId>/gtit/trade/index.json} 逐条装载组定义，
 * 经 {@link #registerTradeAsset}（{@link TradeIntegrationAssetDef} 载荷模型）
 * 收敛回 {@link #registerTradeGroup} 同一管线。
 * 应用规则（玩家文件尊重策略，已拍板）：
 * <ul>
 * <li>组注册状态与版本记账在 {@code config/gtit/trade/integrated/<groupId>.json}
 * （记录源版本与本组注入的 tradeId/pageId 清单）</li>
 * <li>已注册且源 version 未变 → 跳过（玩家对 {@code tab_<id>.json} 的编辑保持权威）</li>
 * <li>源 version 变化（升或降）→ 按记账移除旧组（trades + 非默认 pages）后重注册</li>
 * <li>玩家手动删除记账文件 = 强制重注册</li>
 * </ul>
 * 落库路径与磁盘/编辑链完全一致：trades/pages 合并进磁盘配置文件 →
 * {@code NekoTradeRegistryV2.reload()} 重建 NekoTradeDatabase（version 随
 * clear/add 自动递增，GUI 检测刷新）→ {@code sendSyncToAll()} 推送在线客户端。
 */
public final class NekoTradeIntegrationAPI {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** IMC 消息 key（外部 mod 经 FMLInterModComms 注册贸易组） */
    public static final String IMC_KEY = "gtit:registerTradeGroup";

    /** IMC NBT 载荷中承载组定义 JSON 的字段名 */
    public static final String IMC_NBT_FIELD = "groupJson";

    /** IMC 消息 key（外部 mod 经 jar 资产清单注册贸易组，BQ 式 index.json 通道，E4b） */
    public static final String IMC_KEY_REGISTER_TRADE_ASSET = "gtit:registerTradeAsset";

    /** IMC NBT 载荷中承载资产组定义 JSON 的字段名（内容为 NekoTradeGroupDef JSON 文本） */
    public static final String IMC_NBT_FIELD_TRADE_ASSET_JSON = "tradeAssetJson";

    /** IMC NBT 载荷中可选的资产归属 mod 字段名（缺省取 IMC 发送方） */
    public static final String IMC_NBT_FIELD_OWNER_MOD_ID = "ownerModId";

    /** 记账文件目录（相对游戏根目录） */
    private static final String INTEGRATED_DIR = "config/gtit/trade/integrated";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
        .create();

    /** 待应用组队列（IMC/直连在服务器就绪前入队，serverStarted 统一消费） */
    private static final Queue<NekoTradeGroupDef> PENDING = new ConcurrentLinkedQueue<>();

    /** 应用过程互斥（reload + 磁盘合并的原子性由本锁串行化） */
    private static final Object APPLY_LOCK = new Object();

    private NekoTradeIntegrationAPI() {}

    // ==================== 注册入口（双通道） ====================

    /**
     * 注册一个外部贸易组（线程安全）。
     * <p>
     * 服务器未就绪（无世界）时仅入队，由 {@link #applyQueuedGroups()} 在
     * serverStarted 消费；服务器运行期调用则立即应用（热注册，
     * 在线玩家立即收到同步包）。
     *
     * @param def 组定义（null 或 groupId 非法时告警丢弃）
     */
    public static void registerTradeGroup(NekoTradeGroupDef def) {
        if (!isValidDef(def)) {
            LOG.warn("[TradeAPI] 贸易组定义无效（groupId 缺失或不合法），已丢弃");
            return;
        }
        PENDING.offer(def);
        LOG.info(
            "[TradeAPI] 贸易组已入队等待应用: {}（version={}，trades={}，pages={}）",
            def.getGroupId(),
            def.getVersion(),
            def.getTrades() == null ? 0
                : def.getTrades()
                    .size(),
            def.getPages() == null ? 0
                : def.getPages()
                    .size());
        if (isServerReady()) {
            applyQueuedGroups();
        }
    }

    // ==================== jar 资产通道（E4b，BQ 式 index.json 清单） ====================

    /**
     * 注册一个 jar 贸易资产（{@link TradeIntegrationAssetDef} 载荷模型）。
     * <p>
     * 解析 {@code groupJson} 为 {@link NekoTradeGroupDef} 后走既有
     * {@link #registerTradeGroup} 管线（版本记账/幂等/requiresMods/同步语义全部继承）；
     * {@code ownerModId} 仅用于日志溯源。清单声明的 groupId/version 与 groupJson 内
     * 不一致时以资产声明为准（记 warn——清单是身份权威）。
     *
     * @param def 资产定义（null 或 groupJson 非法时告警丢弃）
     */
    public static void registerTradeAsset(TradeIntegrationAssetDef def) {
        if (def == null) {
            LOG.warn("[TradeAPI] 贸易资产定义为 null，已丢弃");
            return;
        }
        NekoTradeGroupDef group;
        try {
            group = GSON.fromJson(def.getGroupJson(), NekoTradeGroupDef.class);
        } catch (Throwable t) {
            LOG.error(
                "[TradeAPI] 贸易资产 " + def.getGroupId() + " 的 groupJson 解析失败（owner: " + def.getOwnerModId() + "）",
                t);
            return;
        }
        if (!isValidDef(group)) {
            LOG.warn("[TradeAPI] 贸易资产组定义无效（groupId 缺失或不合法），已丢弃（owner: {}）", def.getOwnerModId());
            return;
        }
        if (JarAssetManifest.isValidGroupId(def.getGroupId()) && !def.getGroupId()
            .equals(group.getGroupId())) {
            LOG.warn(
                "[TradeAPI] 贸易资产清单 groupId（{}）与 groupJson 内 groupId（{}）不一致，以清单为准（owner: {}）",
                def.getGroupId(),
                group.getGroupId(),
                def.getOwnerModId());
            group.setGroupId(def.getGroupId());
        }
        if (def.getVersion() > 0 && def.getVersion() != group.getVersion()) {
            LOG.warn(
                "[TradeAPI] 贸易资产清单 version（{}）与 groupJson 内 version（{}）不一致，以清单为准（owner: {}）",
                def.getVersion(),
                group.getVersion(),
                def.getOwnerModId());
            group.setVersion(def.getVersion());
        }
        LOG.info("[TradeAPI] 收到 jar 贸易资产注册: {}（owner: {}）", group.getGroupId(), def.getOwnerModId());
        registerTradeGroup(group);
    }

    /**
     * 从本 mod jar 资产装载贸易组清单并逐条注册（BQ 式，E4b）。
     * <p>
     * 清单位于 {@code assets/<ownerModId>/gtit/trade/index.json}
     * （{@link JarAssetManifest} schema），每条 {@code path} 指向相对资产根的
     * NekoTradeGroupDef JSON。1.7.10 jar 目录不可枚举，故以显式清单驱动逐文件读取。
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
    public static void registerTradeAssetsFromJar(String ownerModId) {
        if (ownerModId == null || ownerModId.isEmpty()) {
            LOG.warn("[TradeAPI] registerTradeAssetsFromJar 需要 ownerModId，已忽略");
            return;
        }
        String root = "assets/" + ownerModId + "/gtit/trade/";
        JsonObject indexJson = JarAssetManifest.readJsonResource(root + "index.json");
        if (indexJson == null) {
            // jar 资产可选：清单缺失不算错误（mod 未随包分发资产时零噪音）
            LOG.info("[TradeAPI] 未找到贸易资产清单 {}index.json（jar 资产可选），跳过", root);
            return;
        }
        JarAssetManifest manifest = JarAssetManifest.parse(indexJson);
        if (manifest == null) {
            LOG.error(
                "[TradeAPI] 贸易资产清单 {}index.json 无效（formatVersion 必须为 {}，条目需含合法 groupId 与安全 path），拒绝整清单",
                root,
                JarAssetManifest.CURRENT_FORMAT_VERSION);
            return;
        }
        int applied = 0;
        for (JarAssetManifest.GroupEntry entry : manifest.getGroups()) {
            try {
                JsonObject groupJson = JarAssetManifest.readJsonResource(root + entry.getPath());
                if (groupJson == null) {
                    LOG.error(
                        "[TradeAPI] 贸易资产文件缺失或损坏，跳过该组: {}（owner: {}）",
                        root + entry.getPath(),
                        ownerModId);
                    continue;
                }
                registerTradeAsset(
                    new TradeIntegrationAssetDef(ownerModId, entry.getGroupId(), entry.getVersion(), groupJson.toString()));
                applied++;
            } catch (Throwable t) {
                LOG.error(
                    "[TradeAPI] 贸易资产 " + entry.getGroupId() + " 读取/注册失败，跳过不中断其余（owner: " + ownerModId + "）",
                    t);
            }
        }
        LOG.info("[TradeAPI] jar 贸易资产装载完成：{}/{} 组（owner: {}）", applied, manifest.getGroups()
            .size(), ownerModId);
    }

    /**
     * IMC 消息消费（由 mod 主类的 IMC 事件处理器委托）。
     * <p>
     * 识别两种 key（E4b 扩展，旧通道行为不变）：
     * <ul>
     * <li>{@link #IMC_KEY}：NBT 载荷 {@code groupJson}（NekoTradeGroupDef JSON）</li>
     * <li>{@link #IMC_KEY_REGISTER_TRADE_ASSET}：NBT 载荷 {@code tradeAssetJson}
     * （NekoTradeGroupDef JSON）+ 可选 {@code ownerModId}，走
     * {@link #registerTradeAsset} 同一路径</li>
     * </ul>
     *
     * @param messages 本次 IMC 事件投递的全部消息
     */
    public static void handleImcMessages(ImmutableList<FMLInterModComms.IMCMessage> messages) {
        if (messages == null || messages.isEmpty()) return;
        for (FMLInterModComms.IMCMessage msg : messages) {
            if (msg == null) continue;
            String sender = msg.getSender();
            try {
                if (IMC_KEY.equals(msg.key)) {
                    handleTradeGroupMessage(msg, sender);
                } else if (IMC_KEY_REGISTER_TRADE_ASSET.equals(msg.key)) {
                    handleTradeAssetMessage(msg, sender);
                }
            } catch (Throwable t) {
                LOG.error("[TradeAPI] IMC 贸易组消息处理失败（发送方: " + sender + "）", t);
            }
        }
    }

    /** {@link #IMC_KEY} 消息处理（单条） */
    private static void handleTradeGroupMessage(FMLInterModComms.IMCMessage msg, String sender) {
        if (!msg.isNBTMessage()) {
            LOG.warn("[TradeAPI] 忽略非 NBT 载荷的 IMC 消息（发送方: {}）", sender);
            return;
        }
        NBTTagCompound nbt = msg.getNBTValue();
        String json = nbt == null ? "" : nbt.getString(IMC_NBT_FIELD);
        if (json == null || json.isEmpty()) {
            LOG.warn("[TradeAPI] IMC 载荷缺少 {} 字段（发送方: {}）", IMC_NBT_FIELD, sender);
            return;
        }
        NekoTradeGroupDef def = GSON.fromJson(json, NekoTradeGroupDef.class);
        if (!isValidDef(def)) {
            LOG.warn("[TradeAPI] IMC 组定义无效，已丢弃（发送方: {}）", sender);
            return;
        }
        LOG.info("[TradeAPI] 收到 IMC 贸易组注册: {}（发送方: {}）", def.getGroupId(), sender);
        registerTradeGroup(def);
    }

    /** {@link #IMC_KEY_REGISTER_TRADE_ASSET} 消息处理（单条，E4b） */
    private static void handleTradeAssetMessage(FMLInterModComms.IMCMessage msg, String sender) {
        if (!msg.isNBTMessage()) {
            LOG.warn("[TradeAPI] 忽略非 NBT 载荷的资产 IMC 消息（发送方: {}）", sender);
            return;
        }
        NBTTagCompound nbt = msg.getNBTValue();
        String json = nbt == null ? "" : nbt.getString(IMC_NBT_FIELD_TRADE_ASSET_JSON);
        if (json == null || json.isEmpty()) {
            LOG.warn("[TradeAPI] 资产 IMC 载荷缺少 {} 字段（发送方: {}）", IMC_NBT_FIELD_TRADE_ASSET_JSON, sender);
            return;
        }
        String ownerModId = nbt.getString(IMC_NBT_FIELD_OWNER_MOD_ID);
        registerTradeAsset(
            new TradeIntegrationAssetDef(
                ownerModId == null || ownerModId.isEmpty() ? sender : ownerModId,
                null,
                0,
                json));
    }

    /**
     * 消费待应用队列（serverStarted 在内置组之后调用，或运行期注册时内部触发）。
     */
    public static void applyQueuedGroups() {
        if (PENDING.isEmpty()) return;
        synchronized (APPLY_LOCK) {
            NekoTradeGroupDef def;
            while ((def = PENDING.poll()) != null) {
                try {
                    applyGroup(def);
                } catch (Throwable t) {
                    LOG.error("[TradeAPI] 贸易组应用失败: " + def.getGroupId(), t);
                }
            }
        }
    }

    // ==================== 核心应用逻辑 ====================

    /**
     * 应用单个组（记账门控 + 磁盘合并 + 落库同步）。
     * <p>
     * 供内置组加载器（BundledTradeGroups）与队列消费共用。
     *
     * @param def 组定义
     * @return true = 本次发生注册或版本更新；false = 跳过（版本未变/前置缺失/注册失败）
     */
    static boolean applyGroup(NekoTradeGroupDef def) {
        if (!isValidDef(def)) return false;
        // 前置 mod 短路：任一缺席整组跳过（不写记账，环境补齐后下次启动自动生效）
        if (def.getRequiresMods() != null) {
            for (String modId : def.getRequiresMods()) {
                if (modId != null && !modId.isEmpty() && !Loader.isModLoaded(modId)) {
                    LOG.info("[TradeAPI] 贸易组 {} 的前置 mod {} 缺席，跳过注册", def.getGroupId(), modId);
                    return false;
                }
            }
        }
        synchronized (APPLY_LOCK) {
            GroupRecord record = GroupRecord.load(def.getGroupId());
            if (record != null && record.version == def.getVersion()) {
                LOG.info("[TradeAPI] 贸易组 {} 已注册且源版本未变（{}），跳过（尊重玩家对 tab 文件的编辑）", def.getGroupId(), def.getVersion());
                return false;
            }
            if (record != null) {
                LOG.info(
                    "[TradeAPI] 贸易组 {} 源版本变化（{} -> {}），按记账移除旧组后重注册",
                    def.getGroupId(),
                    record.version,
                    def.getVersion());
                unregisterFromDisk(record);
            }
            GroupRecord newRecord = registerToDisk(def);
            if (newRecord == null) return false;
            newRecord.save();
            reloadAndSync();
            LOG.info(
                "[TradeAPI] 贸易组 {} 注册完成：{} 条交易，{} 个页面（version={}）",
                def.getGroupId(),
                newRecord.tradeIds.size(),
                newRecord.pageIds.size(),
                def.getVersion());
            return true;
        }
    }

    /**
     * 按记账注销一个组（miao 替换 base 的实现基础，也可供管理侧调用）。
     * <p>
     * 无记账（从未注册或已注销）时静默跳过。移除范围：记账清单内的交易条目
     * 与非默认页面（默认页 1-3 与仓库"默认标签页不可删除"不变量对齐）。
     *
     * @param groupId 组 ID
     * @return true = 本次发生移除并已落库同步
     */
    public static boolean unregisterGroup(String groupId) {
        if (!JarAssetManifest.isValidGroupId(groupId)) return false;
        synchronized (APPLY_LOCK) {
            GroupRecord record = GroupRecord.load(groupId);
            if (record == null) {
                return false;
            }
            unregisterFromDisk(record);
            record.delete();
            reloadAndSync();
            LOG.info("[TradeAPI] 贸易组 {} 已按记账注销", groupId);
            return true;
        }
    }

    /**
     * 将组内容合并进磁盘配置（tab 文件 + pages 文件）。
     * <p>
     * 交易按 id 去重（同 id 旧条目视为本组残留，移除后以新定义覆盖——
     * 记账文件被删除时的强制重注册语义）。条目深拷贝后合入，调用方后续
     * 修改 def 不影响已落盘内容。组级 bqQuestId 作为条目级缺省继承。
     *
     * @return 新记账（失败返回 null）
     */
    private static GroupRecord registerToDisk(NekoTradeGroupDef def) {
        if (def.getTrades() == null || def.getTrades()
            .isEmpty()) {
            LOG.warn("[TradeAPI] 贸易组 {} 缺少 trades，拒绝注册", def.getGroupId());
            return null;
        }
        GroupRecord record = new GroupRecord(def.getGroupId(), def.getVersion());

        // 1. 交易合并：读磁盘全量 → 去重覆盖 → 写回
        NekoTradeConfig.NekoTradeData data = NekoTradeConfig.load();
        if (data.getTrades() == null) {
            data.setTrades(new ArrayList<>());
        }
        for (NekoTradeEntry src : def.getTrades()) {
            if (src == null) continue;
            NekoTradeEntry entry = deepCopy(src, NekoTradeEntry.class);
            if (entry.getId() == null || entry.getId()
                .isEmpty()) {
                entry.setId(
                    java.util.UUID.randomUUID()
                        .toString());
            }
            // 组级 BQ 门控继承：条目自身未绑定时回填组 bqQuestId
            if ((entry.getBqQuestId() == null || entry.getBqQuestId()
                .isEmpty()) && def.getBqQuestId() != null
                && !def.getBqQuestId()
                    .isEmpty()) {
                entry.setBqQuestId(def.getBqQuestId());
            }
            removeTradeById(data, entry.getId());
            data.getTrades()
                .add(entry);
            record.tradeIds.add(entry.getId());
        }
        NekoTradeConfig.save(data);

        // 2. 页面合并：按 id upsert（默认页 1-3 的定义覆盖同样允许，仅删除受限）
        if (def.getPages() != null && !def.getPages()
            .isEmpty()) {
            NekoPageConfig.NekoPageData pageData = NekoPageConfig.load();
            if (pageData.getPages() == null) {
                pageData.setPages(new ArrayList<>());
            }
            for (NekoPageEntry srcPage : def.getPages()) {
                if (srcPage == null) continue;
                NekoPageEntry page = deepCopy(srcPage, NekoPageEntry.class);
                if (page.getId() <= 0) continue;
                boolean replaced = false;
                for (int i = 0; i < pageData.getPages()
                    .size(); i++) {
                    if (pageData.getPages()
                        .get(i) != null
                        && pageData.getPages()
                            .get(i)
                            .getId() == page.getId()) {
                        pageData.getPages()
                            .set(i, page);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    pageData.getPages()
                        .add(page);
                }
                if (!record.pageIds.contains(page.getId())) {
                    record.pageIds.add(page.getId());
                }
            }
            NekoPageConfig.save(pageData);
        }
        return record;
    }

    /**
     * 按记账移除磁盘上的组内容（trades 按 id，pages 按 id 且跳过默认页）。
     */
    private static void unregisterFromDisk(GroupRecord record) {
        // 交易移除
        NekoTradeConfig.NekoTradeData data = NekoTradeConfig.load();
        if (data.getTrades() != null && !record.tradeIds.isEmpty()) {
            boolean removed = data.getTrades()
                .removeIf(entry -> entry != null && record.tradeIds.contains(entry.getId()));
            if (removed) {
                NekoTradeConfig.save(data);
            }
        }
        // 页面移除（默认页不可删除，与 NekoPageRegistry.deletePage 不变量对齐）
        NekoPageConfig.NekoPageData pageData = NekoPageConfig.load();
        if (pageData.getPages() != null && !record.pageIds.isEmpty()) {
            boolean removed = false;
            Iterator<NekoPageEntry> it = pageData.getPages()
                .iterator();
            while (it.hasNext()) {
                NekoPageEntry page = it.next();
                if (page != null && record.pageIds.contains(page.getId()) && !page.isDefault()) {
                    it.remove();
                    removed = true;
                }
            }
            if (removed) {
                NekoPageConfig.save(pageData);
            }
        }
    }

    /**
     * 磁盘变更后的统一落库：reload 重建数据库与页面注册表
     * （NekoTradeDatabase.version 随 clear/add 自动递增，GUI 据此刷新），
     * 再向在线客户端推送全量同步（无在线玩家时为无害空操作）。
     */
    private static void reloadAndSync() {
        NekoTradeRegistryV2.reload();
        NekoTradeNetworkManager.sendSyncToAll();
    }

    // ==================== 辅助 ====================

    /** 服务器是否已具备应用条件（世界已装载；preInit/IMC 阶段为 false，只入队） */
    private static boolean isServerReady() {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.getEntityWorld() != null;
    }

    /** 组定义基础校验（groupId 白名单 + 防路径穿越，规则收敛在 {@link JarAssetManifest}） */
    private static boolean isValidDef(NekoTradeGroupDef def) {
        if (def == null) return false;
        return JarAssetManifest.isValidGroupId(def.getGroupId());
    }

    /** Gson 往返深拷贝（运行时 transient NBT 不随拷贝， toItemStack 走 nbtBase64 回退，语义不变） */
    private static <T> T deepCopy(T src, Class<T> type) {
        return GSON.fromJson(GSON.toJson(src, type), type);
    }

    /** 从磁盘数据中移除指定 id 的交易条目 */
    private static void removeTradeById(NekoTradeConfig.NekoTradeData data, String id) {
        if (data.getTrades() == null) return;
        Iterator<NekoTradeEntry> it = data.getTrades()
            .iterator();
        while (it.hasNext()) {
            NekoTradeEntry entry = it.next();
            if (entry != null && id.equals(entry.getId())) {
                it.remove();
            }
        }
    }

    /**
     * 组记账文件（config/gtit/trade/integrated/&lt;groupId&gt;.json）。
     * <p>
     * 记录源版本与本组注入的 tradeId/pageId 清单：
     * 版本未变跳过（尊重玩家编辑）、版本变化按清单精准移除、
     * 删除文件强制重注册。
     * <p>
     * 包级可见（非 private）供同包单元测试直接构造与读写。
     */
    static final class GroupRecord {

        // 字段刻意包级可见：Gson 反序列化直接反射赋值（不依赖 Unsafe 构造），同包单元测试亦可直接读写
        String groupId;
        int version;
        final List<String> tradeIds = new ArrayList<>();
        final List<Integer> pageIds = new ArrayList<>();

        GroupRecord(String groupId, int version) {
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
                LOG.error("[TradeAPI] 贸易组记账写入失败: " + groupId, e);
            }
        }

        void delete() {
            try {
                Files.deleteIfExists(path());
            } catch (Exception e) {
                LOG.error("[TradeAPI] 贸易组记账删除失败: " + groupId, e);
            }
        }

        static GroupRecord load(String groupId) {
            try {
                Path path = Paths.get(INTEGRATED_DIR, groupId + ".json");
                if (!Files.exists(path)) return null;
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                GroupRecord record = GSON.fromJson(json, GroupRecord.class);
                // 关键字段缺失视为无效记账（等同强制重注册）
                if (record == null || !groupId.equals(record.groupId)
                    || record.tradeIds == null
                    || record.pageIds == null) {
                    return null;
                }
                return record;
            } catch (Exception e) {
                LOG.warn("[TradeAPI] 贸易组记账读取失败，按未注册处理: " + groupId, e);
                return null;
            }
        }
    }
}
