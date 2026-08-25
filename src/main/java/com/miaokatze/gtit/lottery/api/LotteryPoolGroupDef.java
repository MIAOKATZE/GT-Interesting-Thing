package com.miaokatze.gtit.lottery.api;

import java.util.ArrayList;
import java.util.List;

import com.miaokatze.gtit.lottery.LotteryPool;

/**
 * 外部抽奖池组定义（BQ 式 jar 资产整合 API 的载荷模型，E4b）。
 * <p>
 * 一个池组 = 一组卡池（复用 {@code lottery.json} 的 {@link LotteryPool} JSON 模型）
 * + 版本号，由 {@link LotteryIntegrationAPI#registerLotteryPool} 注册后合并进
 * {@code config/gtit/lottery/lottery.json}，经 {@code LotteryManager.loadConfig()}
 * 重建内存卡池表并同步到客户端轮盘。
 * <p>
 * JSON 形状（IMC 载荷 {@code groupJson} 与 jar 资产 {@code pools/<group>.json} 共用本模型）：
 *
 * <pre>
 * {
 *   "groupId": "example.pools",   // 唯一，小写字母/数字/连字符/点，长度 1-64（与贸易组白名单一致）
 *   "version": 2,                 // 源版本：提升时按记账移除旧池重注册；不变时跳过（尊重玩家编辑）
 *   "pools": [ LotteryPool ... ], // 复用 lottery.json 的池模型（id/name/iconItem/costItems/entries/pityConfig）
 *   "requiresMods": ["gtsr"]      // 可选：任一 mod 缺席时整组跳过注册
 * }
 * </pre>
 * <p>
 * 稳定性边界：组内各池的 {@code id} 是记账与移除的凭据——升版本时旧池按记账 poolIds
 * 移除，池 id 跨版本保持稳定才能保证替换精准；重命名 id 等同"新增池 + 孤儿池残留"。
 */
public class LotteryPoolGroupDef {

    /** 组唯一标识（同时用作记账文件名 config/gtit/lottery/integrated/&lt;groupId&gt;.json） */
    private String groupId;

    /** 源版本号：与记账版本一致时跳过重注册（尊重玩家对 lottery.json 的编辑） */
    private int version = 1;

    /** 卡池列表（复用 lottery.json 的 LotteryPool JSON 模型） */
    private List<LotteryPool> pools = new ArrayList<>();

    /** 前置 mod 列表（可选）：任一缺席时整组跳过 */
    private String[] requiresMods;

    public LotteryPoolGroupDef() {}

    public LotteryPoolGroupDef(String groupId, int version) {
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

    public List<LotteryPool> getPools() {
        return pools;
    }

    public void setPools(List<LotteryPool> pools) {
        this.pools = pools;
    }

    public String[] getRequiresMods() {
        return requiresMods;
    }

    public void setRequiresMods(String[] requiresMods) {
        this.requiresMods = requiresMods;
    }
}
