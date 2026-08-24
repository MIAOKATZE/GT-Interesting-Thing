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
import com.miaokatze.gtit.crossmod.miaogtnh.MiaoGtnhHost;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/**
 * 内置贸易组加载器（E4a）。
 * <p>
 * 从 jar 资产 {@code assets/gtit/bqtrades/} 读取内置组（由内容编辑并行维护，
 * 代码侧只消费——资产缺失时静默降级，不报错）：
 * <ul>
 * <li>{@code base_trades.json} + {@code pages_base.json}：基础贸易组（groupId=gtit-base）。
 * {@code Config.enhancedDefaultTrades} 开启且资产可用时注册，并抑制
 * {@code NekoTradeConfig.getDefaultTrades()} 旧 42 条默认的注入
 * （资产缺失/解析失败/配置关闭时回落旧默认——方法保留为兜底）。</li>
 * <li>{@code miao_trades.json} + {@code pages_miao.json}：MIAO 综合贸易组（groupId=gtit-miao）。
 * {@code MiaoGtnhHost.shouldLoadMiaoPack()}（gtsr+gtswn+bq 齐备且配置开）且资产可用时注册，
 * 并【替换】base 组——按记账移除 base 的 trades/pages 后 miao 顶上；
 * base 从未注册时直接只注册 miao。</li>
 * </ul>
 * 应用走 {@link NekoTradeIntegrationAPI}（记账/版本/玩家文件尊重策略/落库同步全复用）。
 * <p>
 * 注意（设计决策）：miao 退出（卸载 gtsr/gtswn 或关闭配置）不自动卸载已落盘的
 * miao 交易——避免配置误操作静默销毁玩家可见内容；恢复条件后按记账幂等跳过，
 * 彻底清理可手删 {@code config/gtit/trade/integrated/gtit-miao.json} 强制重注册或直接删 tab 文件。
 */
public final class BundledTradeGroups {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 内置基础组 ID（记账文件名） */
    public static final String BASE_GROUP_ID = "gtit-base";
    /** MIAO 综合组 ID（记账文件名） */
    public static final String MIAO_GROUP_ID = "gtit-miao";

    private static final String BASE_TRADES_RES = "assets/gtit/bqtrades/base_trades.json";
    private static final String BASE_PAGES_RES = "assets/gtit/bqtrades/pages_base.json";
    private static final String MIAO_TRADES_RES = "assets/gtit/bqtrades/miao_trades.json";
    private static final String MIAO_PAGES_RES = "assets/gtit/bqtrades/pages_miao.json";

    private static final Gson GSON = new Gson();

    /** prepare 阶段解析缓存的 base 组（null = 配置关/资产缺失/解析失败） */
    private static NekoTradeGroupDef baseDef;
    /** 首次需要时解析缓存的 miao 组 */
    private static NekoTradeGroupDef miaoDef;

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
     */
    public static void applyBundledGroups() {
        try {
            if (MiaoGtnhHost.shouldLoadMiaoPack()) {
                if (miaoDef == null) {
                    miaoDef = parseBundledGroup(MIAO_GROUP_ID, MIAO_TRADES_RES, MIAO_PAGES_RES);
                }
                if (miaoDef != null) {
                    // miao 顶上：按记账移除已注册的 base（未注册过则无操作），再注册 miao
                    NekoTradeIntegrationAPI.unregisterGroup(BASE_GROUP_ID);
                    NekoTradeIntegrationAPI.applyGroup(miaoDef);
                    return;
                }
                LOG.info("[TradeAPI] MIAO 综合贸易组资产缺失或不可用，回落基础贸易组");
            }
            if (baseDef != null) {
                NekoTradeIntegrationAPI.applyGroup(baseDef);
            }
        } catch (Throwable t) {
            LOG.error("[TradeAPI] 内置贸易组应用失败（不影响磁盘玩家配置）", t);
        }
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
