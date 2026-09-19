package com.miaokatze.gtit.trade.api;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.config.Config;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/**
 * 内置贸易组加载器（E4a）。
 * <p>
 * 从 jar 资产 {@code assets/gtit/bqtrades/} 读取内置组（由内容编辑并行维护，
 * 代码侧只消费——资产缺失时静默降级，不报错）：
 * <p>
 * 仅保留 base 组；启动时按记账一次性清理已落盘 gtit-miao
 * （用户拍板取消 miao 组，覆盖原“退出不卸载”决策）。
 * 应用走 {@link NekoTradeIntegrationAPI}（记账/版本/玩家文件尊重策略/落库同步全复用）。
 * <p>
 * v1.8.17 默认贸易体系：gtit-base 即"默认贸易组"，条目带 {@code defaultEntry} 标记，
 * 由本类管理版本同步——版本更新时玩家未改动默认条目则静默校准；已改动则保持现状，
 * 玩家打开猫猫贸易机时弹框询问是否复原（GUI 经 {@link #getPromptState()} 询问、
 * {@link #restoreDefaultGroup()} 等动作回调）。升自更新标签之前的存档强制询问。
 */
public final class BundledTradeGroups {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 内置基础组 ID（记账文件名） */
    public static final String BASE_GROUP_ID = "gtit-base";
    private static final String BASE_TRADES_RES = "assets/gtit/bqtrades/base_trades.json";
    private static final String BASE_PAGES_RES = "assets/gtit/bqtrades/pages_base.json";
    private static final Gson GSON = new Gson();

    /**
     * 更新标签（v1.8.18）：升自该版本之前的存档，打开猫猫贸易机时强制弹
     * "默认贸易组已更新，请同步。"（配置不可关闭）。发布推荐更新时由作者手动递改。
     */
    public static final String UPDATE_TAG = "1.8.18";

    /** GUI 询问状态：无 */
    public static final String PROMPT_NONE = "";
    /** GUI 询问状态：普通同步询问（三按钮，配置可关） */
    public static final String PROMPT_ASK = "ASK";
    /** GUI 询问状态：强制同步询问（升自更新标签之前，两按钮，配置不可关） */
    public static final String PROMPT_FORCE = "FORCE";

    /** prepare 阶段解析缓存的 base 组（null = 配置关/资产缺失/解析失败） */
    private static NekoTradeGroupDef baseDef;

    /**
     * 同步询问状态缓存（{@link #PROMPT_NONE}/{@link #PROMPT_ASK}/{@link #PROMPT_FORCE}）。
     * <p>
     * {@code getPromptState()} 经 GUI 同步值 getter 每同步 tick 被求值
     * （MUI2 detectAndSendChanges 链路），不能每次读记账文件——状态在应用门控
     * （{@link #applyBundledGroups()}）与玩家动作（复原/跳过/不再提醒/标记已处理）后重算入缓存。
     * 声明于 PROMPT_* 常量之后，保证静态初始化时引用已赋值。
     */
    private static volatile String cachedPromptState = PROMPT_NONE;

    private BundledTradeGroups() {}

    /**
     * 默认交易抑制预探测（postInit，NekoPageRegistry/NekoTradeRegistryV2 客户端初始化之前）。
     * <p>
     * 单人存档下 postInit 的 initializeClient 会先于 serverStarted 触发
     * NekoTradeConfig.init() 的默认文件生成，故抑制标志必须在此前置设置。
     * base 组资产可用（配置开 + 解析成功）→ 置抑制标志；
     * 否则保持 false，旧 42 条默认照常兜底。
     */
    public static void prepareDefaultSuppression() {
        try {
            if (Config.enhancedDefaultTrades) {
                baseDef = parseBundledGroup(BASE_GROUP_ID, BASE_TRADES_RES, BASE_PAGES_RES);
            } else {
                LOG.info("[TradeAPI] enhancedDefaultTrades=false，内置基础贸易组停用，沿用旧默认交易");
            }
            if (baseDef != null) {
                NekoTradeConfig.setDefaultTradesSuppressed(true);
                LOG.info(
                    "[TradeAPI] 内置基础贸易组就绪（version={}，trades={}，pages={}），旧 42 条默认交易不再注入",
                    baseDef.getVersion(),
                    baseDef.getTrades()
                        .size(),
                    baseDef.getPages()
                        .size());
            } else if (Config.enhancedDefaultTrades) {
                LOG.info("[TradeAPI] 内置基础贸易组资产缺失或不可用，回落旧默认交易");
            }
        } catch (Throwable t) {
            // 预探测失败按资产缺失处理（抑制不生效，兜底链路完好）
            baseDef = null;
            LOG.error("[TradeAPI] 内置基础贸易组预探测失败，回落旧默认交易", t);
        }
    }

    /**
     * 应用内置组（serverStarted，NekoTradeRegistryV2.initialize 磁盘加载之后）。
     * <p>
     * 时序：initialize 先完成玩家磁盘配置的权威装载，本方法再做内置组的
     * 记账门控合并——已注册且版本未变的组直接跳过，不触碰玩家文件。
     * <p>
     * v1.8.17 默认贸易体系门控：版本未变保持现状；版本变化时若玩家未改动
     * 默认条目（内容哈希一致）则静默校准重注册，已改动则保持现状，改由
     * GUI 在玩家打开猫猫贸易机时询问是否复原（{@link #getPromptState()}）。
     */
    public static void applyBundledGroups() {
        try {
            // 一次性清理凭磁盘记账精准移除已合并交易/页并删记账文件，幂等（无记账时静默 false）；清理先于 base apply。
            NekoTradeIntegrationAPI.unregisterGroup("gtit-miao");
            if (baseDef != null) {
                NekoTradeIntegrationAPI.GroupRecord record = NekoTradeIntegrationAPI.GroupRecord.load(BASE_GROUP_ID);
                if (record == null) {
                    applyBase("首次注册默认贸易组");
                } else if (record.version == baseDef.getVersion()) {
                    // 版本未变：玩家对 tab 文件的编辑保持权威
                } else if (isDefaultContentModified(record)) {
                    LOG.info(
                        "[TradeAPI] 默认贸易组版本变化（{} -> {}）且玩家已改动默认条目，保持现状，待玩家在贸易机 GUI 中选择",
                        record.version,
                        baseDef.getVersion());
                } else {
                    applyBase("版本变化且玩家未改动默认条目，静默校准");
                }
            }
        } catch (Throwable t) {
            LOG.error("[TradeAPI] 内置贸易组应用失败（不影响磁盘玩家配置）", t);
        } finally {
            refreshPromptStateCache();
        }
    }

    /** 应用（重注册）默认贸易组并记录日志 */
    private static void applyBase(String reason) {
        LOG.info("[TradeAPI] 默认贸易组应用：{}（version={}）", reason, baseDef.getVersion());
        NekoTradeIntegrationAPI.applyGroup(baseDef);
    }

    /**
     * 判定玩家是否改动过默认贸易组条目
     * <p>
     * 旧记账无内容哈希（1.8.17 之前创建）按已改动处理——本版本起所有升旧存档
     * 均走 GUI 询问，不静默覆盖；此后哈希随每次注册入账。
     */
    private static boolean isDefaultContentModified(NekoTradeIntegrationAPI.GroupRecord record) {
        if (record.contentHash == null || record.contentHash.isEmpty()) {
            return true;
        }
        String hash = NekoTradeIntegrationAPI.computeContentHash(record, NekoTradeConfig.load());
        return hash == null || !hash.equals(record.contentHash);
    }

    // ==================== 默认贸易组同步（v1.8.17，GUI 同步值调用） ====================

    /**
     * 当前是否需要弹默认贸易组同步询问（猫猫贸易机 GUI 打开时经同步值调用，服务端执行）
     * <p>
     * 返回缓存状态（纯内存，getter 每同步 tick 被求值）：
     * <ul>
     * <li>升自更新标签之前（记账 {@code handledUpdateTag} 低于 {@link #UPDATE_TAG}）→
     * {@link #PROMPT_FORCE}，配置不可关闭</li>
     * <li>默认组版本未同步、配置允许询问、未选"不再提醒"、本版本未选过"否" →
     * {@link #PROMPT_ASK}</li>
     * <li>其余 → {@link #PROMPT_NONE}</li>
     * </ul>
     */
    public static String getPromptState() {
        return cachedPromptState;
    }

    /** 重算同步询问状态入缓存（应用门控与玩家动作后调用；磁盘读取仅在此路径发生） */
    private static void refreshPromptStateCache() {
        try {
            cachedPromptState = computePromptState();
        } catch (Throwable t) {
            LOG.error("[TradeAPI] 默认贸易组同步状态计算失败，按无需询问处理", t);
            cachedPromptState = PROMPT_NONE;
        }
    }

    /** 计算同步询问状态（{@code refreshPromptStateCache} 专属，含记账读取） */
    private static String computePromptState() {
        if (baseDef == null) return PROMPT_NONE;
        NekoTradeIntegrationAPI.GroupRecord record = NekoTradeIntegrationAPI.GroupRecord.load(BASE_GROUP_ID);
        if (record == null) return PROMPT_NONE;
        if (compareVersions(record.handledUpdateTag, UPDATE_TAG) < 0) {
            return PROMPT_FORCE;
        }
        if (!Config.defaultTradeUpdateNotice) {
            return PROMPT_NONE;
        }
        if (record.version == baseDef.getVersion() || record.dismissedVersion == baseDef.getVersion()) {
            return PROMPT_NONE;
        }
        return PROMPT_ASK;
    }

    /**
     * 复原默认贸易组（玩家在同步询问中选"是"）：按记账移除现组后重注册当前资产内容
     *
     * @return true = 重注册完成
     */
    public static boolean restoreDefaultGroup() {
        if (baseDef == null) return false;
        NekoTradeIntegrationAPI.unregisterGroup(BASE_GROUP_ID);
        boolean applied = NekoTradeIntegrationAPI.applyGroup(baseDef);
        refreshPromptStateCache();
        LOG.info("[TradeAPI] 玩家确认复原默认贸易组：applied={}", applied);
        return applied;
    }

    /** 玩家对当前默认组版本选"否"：该版本不再打扰（默认组保持玩家现状） */
    public static void dismissDefaultGroupUpdate() {
        NekoTradeIntegrationAPI.GroupRecord record = NekoTradeIntegrationAPI.GroupRecord.load(BASE_GROUP_ID);
        if (record == null || baseDef == null) return;
        record.dismissedVersion = baseDef.getVersion();
        record.save();
        refreshPromptStateCache();
        LOG.info("[TradeAPI] 玩家跳过默认贸易组版本 {} 的复原询问", record.dismissedVersion);
    }

    /**
     * 玩家选"不再提醒"（v1.8.18 语义修正）：持久化等效于配置关闭更新通知——
     * 直接把 {@code defaultTradeUpdateNotice} 写为 false 并落配置文件，
     * 后续默认组版本更新不再弹普通同步询问（强制询问不受影响）。
     */
    public static void neverAskDefaultGroup() {
        Config.disableDefaultTradeUpdateNoticeAndSave();
        refreshPromptStateCache();
        LOG.info("[TradeAPI] 玩家选择不再提醒默认贸易组同步（配置 defaultTradeUpdateNotice=false 已写回）");
    }

    /** 标记当前更新标签已处理（强制同步询问收束） */
    public static void markUpdateTagHandled() {
        NekoTradeIntegrationAPI.GroupRecord record = NekoTradeIntegrationAPI.GroupRecord.load(BASE_GROUP_ID);
        if (record == null) return;
        record.handledUpdateTag = UPDATE_TAG;
        record.save();
        refreshPromptStateCache();
        LOG.info("[TradeAPI] 更新标签 {} 已处理", UPDATE_TAG);
    }

    /**
     * 点分段版本号比较（"1.8.16" &lt; "1.8.17"；null/空按 0 处理；非数字段按 0）
     *
     * @return 负数 a&lt;b，0 相等，正数 a&gt;b
     */
    static int compareVersions(String a, String b) {
        int[] pa = parseVersion(a);
        int[] pb = parseVersion(b);
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? pa[i] : 0;
            int vb = i < pb.length ? pb[i] : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int[] parseVersion(String v) {
        if (v == null || v.isEmpty()) return new int[] { 0 };
        String[] parts = v.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    /**
     * 解析一对内置资产（trades + pages）为组定义。
     * <p>
     * trades 文件形状（顶层 version 即组源版本，trades 条目复用 NekoTradeEntry JSON 模型）：
     * {@code {"version":1,"trades":[{id,tabId,orderId,currency,fromItems,toItems,cooldown,maxTrades,bqQuestId,recordNBT},...]}}
     * <p>
     * pages 文件形状（同 nekovm_pages schema）：
     * {@code {"version":1,"pages":[{id,name,iconItem,iconMeta,iconNbt,isDefault},...]}}
     *
     * @return 组定义；任一资产缺失或解析失败返回 null（info 级静默降级）
     */
    private static NekoTradeGroupDef parseBundledGroup(String groupId, String tradesRes, String pagesRes) {
        JsonObject tradesJson = readJsonResource(tradesRes);
        if (tradesJson == null) {
            LOG.info("[TradeAPI] 内置贸易组资产不存在: {}", tradesRes);
            return null;
        }
        JsonObject pagesJson = readJsonResource(pagesRes);
        if (pagesJson == null) {
            LOG.info("[TradeAPI] 内置贸易组页面资产不存在: {}", pagesRes);
            return null;
        }
        try {
            BundledTradesData trades = GSON.fromJson(tradesJson, BundledTradesData.class);
            NekoPageConfig.NekoPageData pages = GSON.fromJson(pagesJson, NekoPageConfig.NekoPageData.class);
            if (trades == null || trades.trades == null || trades.trades.isEmpty()) {
                LOG.warn("[TradeAPI] 内置贸易组 {} 的 trades 为空，忽略该组", groupId);
                return null;
            }
            NekoTradeGroupDef def = new NekoTradeGroupDef(groupId, trades.version);
            // gtit-base 即默认贸易组：条目落盘时打 defaultEntry 标记并参与默认组同步
            if (BASE_GROUP_ID.equals(groupId)) {
                def.setDefaultGroup(true);
            }
            def.setTrades(trades.trades);
            def.setPages(pages != null && pages.getPages() != null ? pages.getPages() : new java.util.ArrayList<>());
            return def;
        } catch (Exception e) {
            LOG.error("[TradeAPI] 内置贸易组 {} 资产解析失败", groupId, e);
            return null;
        }
    }

    /** trades 资产文件形状（顶层 version 为组源版本） */
    private static final class BundledTradesData {

        private int version = 1;
        private java.util.List<NekoTradeEntry> trades;
    }

    /**
     * 从 classloader 资源读 JsonObject（UTF-8 + 32KB 缓冲，与 BQ JsonHelper 解析方式对齐）。
     *
     * @param path 资源路径
     * @return 解析结果，资源缺失或解析失败返回 null
     */
    private static JsonObject readJsonResource(String path) {
        InputStream is = BundledTradeGroups.class.getClassLoader()
            .getResourceAsStream(path);
        if (is == null) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 32768)) {
            return new JsonParser().parse(br)
                .getAsJsonObject();
        } catch (Exception e) {
            LOG.error("[TradeAPI] 资源 JSON 解析失败: {}", path, e);
            return null;
        }
    }
}
