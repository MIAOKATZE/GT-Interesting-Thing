package com.miaokatze.gtit.trade.api;

/**
 * jar 贸易资产定义（BQ 式 jar 资产整合 API 的载荷模型，E4b）。
 * <p>
 * 由 {@link NekoTradeIntegrationAPI#registerTradeAssetsFromJar} 从清单
 * {@code assets/<ownerModId>/gtit/trade/index.json} 逐条构造后经
 * {@link NekoTradeIntegrationAPI#registerTradeAsset} 注册；第三方 mod 也可自行构造直调。
 * <p>
 * {@code groupJson} 即 {@link NekoTradeGroupDef} 的 JSON 文本（与 IMC 载荷、内置资产
 * 共用同一模型），注册时解析后走既有 {@link NekoTradeIntegrationAPI#registerTradeGroup}
 * 管线（版本记账/幂等/requiresMods/同步语义全部继承）；{@code ownerModId} 仅用于日志
 * 溯源，不参与记账。清单声明的 groupId/version 与 groupJson 内不一致时以本定义为准
 * （清单是身份权威，内容文件只是载荷）。
 */
public class TradeIntegrationAssetDef {

    /** 资产归属 mod（仅日志溯源用，可为任意字符串） */
    private String ownerModId;

    /** 组 ID（清单身份；null/非法时以 groupJson 内 groupId 为准） */
    private String groupId;

    /** 源版本（清单身份；≤0 表示未声明，以 groupJson 内 version 为准） */
    private int version;

    /** 组定义 JSON 文本（NekoTradeGroupDef JSON） */
    private String groupJson;

    public TradeIntegrationAssetDef() {}

    public TradeIntegrationAssetDef(String ownerModId, String groupId, int version, String groupJson) {
        this.ownerModId = ownerModId;
        this.groupId = groupId;
        this.version = version;
        this.groupJson = groupJson;
    }

    // --- Getters & Setters ---

    public String getOwnerModId() {
        return ownerModId;
    }

    public void setOwnerModId(String ownerModId) {
        this.ownerModId = ownerModId;
    }

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

    public String getGroupJson() {
        return groupJson;
    }

    public void setGroupJson(String groupJson) {
        this.groupJson = groupJson;
    }
}
