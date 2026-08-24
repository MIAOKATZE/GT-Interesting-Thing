package com.miaokatze.gtit.trade.api;

import java.util.ArrayList;
import java.util.List;

import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/**
 * 外部贸易组定义（E4a 贸易集成 API 的载荷模型）。
 * <p>
 * 一个贸易组 = 一组页面定义 + 一组交易条目 + 版本号，由
 * {@link NekoTradeIntegrationAPI#registerTradeGroup} 注册后合并进
 * {@code config/gtit/trade/trades/tab_<id>.json} 与 {@code config/gtit/trade/pages.json}，
 * 经 {@code NekoTradeRegistryV2.reload()} 统一落库（NekoTradeDatabase）并同步到客户端 GUI。
 * <p>
 * JSON 形状（IMC 载荷 {@code groupJson} 与内置资产共用本模型）：
 * 
 * <pre>
 * {
 *   "groupId": "example.group",     // 唯一，小写字母/数字/连字符/点，长度 1-64
 *   "version": 2,                   // 源版本：提升时按记账移除旧组重注册；不变时跳过（尊重玩家编辑）
 *   "pages": [ NekoPageEntry ... ], // {id,name,iconItem,iconMeta,iconNbt,isDefault}，按 id upsert
 *   "trades": [ NekoTradeEntry ... ],// 复用交易 JSON 模型（id/tabId/orderId/currency/fromItems/toItems/cooldown/maxTrades/bqQuestId/recordNBT）
 *   "bqQuestId": "",                // 可选：组级 BQ 门控，条目自身无 bqQuestId 时继承
 *   "requiresMods": ["gtsr"]        // 可选：任一 mod 缺席时整组跳过注册
 * }
 * </pre>
 */
public class NekoTradeGroupDef {

    /** 组唯一标识（同时用作记账文件名 config/gtit/trade/integrated/&lt;groupId&gt;.json） */
    private String groupId;

    /** 源版本号：与记账版本一致时跳过重注册（尊重玩家对 tab 文件的编辑） */
    private int version = 1;

    /** 页面定义列表（复用 NekoPageEntry JSON 模型） */
    private List<NekoPageEntry> pages = new ArrayList<>();

    /** 交易条目列表（复用 NekoTradeEntry JSON 模型） */
    private List<NekoTradeEntry> trades = new ArrayList<>();

    /** 组级 BQ 任务门控（可选）：条目自身未绑定任务时继承此 ID */
    private String bqQuestId;

    /** 前置 mod 列表（可选）：任一缺席时整组跳过 */
    private String[] requiresMods;

    public NekoTradeGroupDef() {}

    public NekoTradeGroupDef(String groupId, int version) {
        this.groupId = groupId;
        this.version = version;
    }

    // --- Getters & Setters ---

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<NekoPageEntry> getPages() {
        return pages;
    }

    public void setPages(List<NekoPageEntry> pages) {
        this.pages = pages;
    }

    public List<NekoTradeEntry> getTrades() {
        return trades;
    }

    public void setTrades(List<NekoTradeEntry> trades) {
        this.trades = trades;
    }

    public String getBqQuestId() {
        return bqQuestId;
    }

    public void setBqQuestId(String bqQuestId) {
        this.bqQuestId = bqQuestId;
    }

    public String[] getRequiresMods() {
        return requiresMods;
    }

    public void setRequiresMods(String[] requiresMods) {
        this.requiresMods = requiresMods;
    }
}
