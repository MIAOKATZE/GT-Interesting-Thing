package com.miaokatze.gtit.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

/**
 * 交易显示数据类
 * <p>
 * 完美复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.TradeItemDisplay}，
 * 作为 {@link NekoTradeItemDisplayWidget} 的数据源。
 * <p>
 * 与 VM 版本的关键差异：
 * <ul>
 * <li>使用 V2 的 {@link NekoBigItemStack} 替代 VM 的 {@code BigItemStack}</li>
 * <li>移除 VM 的 {@code CurrencyItem} 依赖，改用 {@code currencyId} + {@code cost} 直接表示猫猫币花费</li>
 * <li>移除 VM 的 {@code WalletMode} / {@code tradeableNowPersonal} / {@code tradeableNowTeam}，
 * 统一用单个 {@code tradeable} 字段表示可交易状态</li>
 * <li>移除 VM 的 {@code ncItems}（NEI物品）和 {@code cdTradeCount}（团队冷却计数），
 * V2 不需要这些功能</li>
 * <li>新增 {@code bqLocked} 字段表示 BQ 前置任务锁定状态</li>
 * </ul>
 * <p>
 * <b>类型说明</b>：{@code currencyId} 为 {@code String} 类型（而非 int），
 * 因为 V2 的 {@link NekoCurrencyRegistrar} 使用字符串 ID（如 "neko"、"shimmeringNeko"），
 * {@link NekoTrade#getCurrencyId()} 也返回 String，无法转为 int。
 *
 * @see NekoTradeItemDisplayWidget
 * @see NekoTrade
 * @see NekoTradeGroup
 */
public class NekoTradeItemDisplay {

    // ==================== 字段 ====================

    /** 交易在交易组内的索引（0-based） */
    private final int tradeIndex;

    /** 交易组 UUID */
    private final UUID groupId;

    /** 显示物品（用于 GUI 主图标，为 null 时取 outputs 首项） */
    private NekoBigItemStack displayItem;

    /** 产物列表（玩家获得的物品） */
    private List<NekoBigItemStack> outputs;

    /** 输入物品列表（玩家需要提供的物品） */
    private List<NekoBigItemStack> inputs;

    /**
     * 猫猫币唯一标识
     * <p>
     * V2 使用 String 类型（如 "neko"、"shimmeringNeko"），
     * 非 null 且 {@code cost > 0} 时表示有猫猫币花费。
     */
    private String currencyId;

    /**
     * 猫猫币花费数量
     * <p>
     * 使用 long 类型以支持大数值，工厂方法从 {@link NekoTrade#getCurrencyCost()}（int）安全拓宽。
     */
    private long cost;

    /** BQ 前置任务是否未满足（true=锁定，不可交易） */
    private boolean bqLocked;

    /** 冷却剩余时间（秒），0 表示无冷却 */
    private long cooldownRemaining;

    /** 是否可交易（综合判断：非锁定、非冷却中） */
    private boolean tradeable;

    /** 是否已收藏 */
    private boolean favourite;

    // ==================== 构造器 ====================

    /**
     * 完整构造器
     *
     * @param tradeIndex        交易在交易组内的索引
     * @param groupId           交易组 UUID
     * @param displayItem       显示物品（可为 null）
     * @param outputs           产物列表
     * @param inputs            输入物品列表
     * @param currencyId        猫猫币 ID（可为 null）
     * @param cost              猫猫币花费数量
     * @param bqLocked          BQ 锁定状态
     * @param cooldownRemaining 冷却剩余秒数
     * @param tradeable         是否可交易
     * @param favourite         是否已收藏
     */
    public NekoTradeItemDisplay(int tradeIndex, UUID groupId, NekoBigItemStack displayItem,
        List<NekoBigItemStack> outputs, List<NekoBigItemStack> inputs, String currencyId, long cost, boolean bqLocked,
        long cooldownRemaining, boolean tradeable, boolean favourite) {
        this.tradeIndex = tradeIndex;
        this.groupId = groupId;
        this.displayItem = displayItem;
        this.outputs = outputs != null ? outputs : new ArrayList<>();
        this.inputs = inputs != null ? inputs : new ArrayList<>();
        this.currencyId = currencyId;
        this.cost = cost;
        this.bqLocked = bqLocked;
        this.cooldownRemaining = cooldownRemaining;
        this.tradeable = tradeable;
        this.favourite = favourite;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 从 V2 交易数据构建显示对象
     * <p>
     * 从 {@link NekoTrade} 和 {@link NekoTradeGroup} 提取所有显示所需信息：
     * <ul>
     * <li>显示物品取自 {@link NekoTrade#getDisplayItem()}</li>
     * <li>产物列表取自 {@link NekoTrade#getToItems()}</li>
     * <li>输入物品列表取自 {@link NekoTrade#getFromItems()}</li>
     * <li>猫猫币信息取自 {@link NekoTrade#getCurrencyId()} 和 {@link NekoTrade#getCurrencyCost()}</li>
     * <li>交易组 UUID 取自 {@link NekoTradeGroup#getId()}</li>
     * </ul>
     * <p>
     * 初始状态：bqLocked=false, cooldownRemaining=0, tradeable=true, favourite=false。
     * 这些状态字段应由 GUI 层在后续更新中设置。
     *
     * @param trade      V2 交易数据
     * @param group      V2 交易组
     * @param tradeIndex 交易在组内的索引
     * @return 构建好的显示对象
     */
    public static NekoTradeItemDisplay fromNekoTrade(NekoTrade trade, NekoTradeGroup group, int tradeIndex) {
        // 获取显示物品（getDisplayItem 内部已处理 null 回退到 toItems 首项的逻辑）
        NekoBigItemStack displayItem = trade.getDisplayItem();

        // 拷贝产物和输入列表，避免外部修改影响显示数据
        List<NekoBigItemStack> outputs = new ArrayList<>(trade.getToItems());
        List<NekoBigItemStack> inputs = new ArrayList<>(trade.getFromItems());

        // 猫猫币信息
        String currencyId = trade.getCurrencyId();
        long cost = trade.getCurrencyCost();

        // 交易组 UUID
        UUID groupId = group.getId();

        // 初始状态：默认可交易，无锁定，无冷却，未收藏
        return new NekoTradeItemDisplay(
            tradeIndex,
            groupId,
            displayItem,
            outputs,
            inputs,
            currencyId,
            cost,
            false, // bqLocked - 由 GUI 层后续更新
            0, // cooldownRemaining - 由 GUI 层后续更新
            true, // tradeable - 默认可交易
            false); // favourite - 默认未收藏
    }

    // ==================== 搜索方法 ====================

    /**
     * 检查交易是否匹配搜索文本
     * <p>
     * 匹配范围：显示物品名、产物名、输入物品名、猫猫币名称。
     * 不区分大小写。
     *
     * @param searchText 搜索文本（已转为小写）
     * @return 匹配返回 true，否则 false
     */
    public boolean satisfiesSearch(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return true;
        }

        // 检查显示物品名
        if (displayItem != null && displayItem.getBaseStack() != null) {
            String name = displayItem.getBaseStack()
                .getDisplayName();
            if (name != null && name.toLowerCase()
                .contains(searchText)) {
                return true;
            }
        }

        // 检查产物名
        for (NekoBigItemStack output : outputs) {
            if (output.getBaseStack() != null) {
                String name = output.getBaseStack()
                    .getDisplayName();
                if (name != null && name.toLowerCase()
                    .contains(searchText)) {
                    return true;
                }
            }
        }

        // 检查输入物品名
        for (NekoBigItemStack input : inputs) {
            if (input.getBaseStack() != null) {
                String name = input.getBaseStack()
                    .getDisplayName();
                if (name != null && name.toLowerCase()
                    .contains(searchText)) {
                    return true;
                }
            }
        }

        return false;
    }

    // ==================== 辅助方法 ====================

    /**
     * 是否有猫猫币花费
     *
     * @return 有花费返回 true
     */
    public boolean hasCurrencyCost() {
        return currencyId != null && cost > 0;
    }

    /**
     * 是否有输入物品
     *
     * @return 有输入物品返回 true
     */
    public boolean hasInputs() {
        return !inputs.isEmpty();
    }

    /**
     * 获取显示物品的基础 ItemStack
     * <p>
     * 优先返回 displayItem 的 baseStack；未设置时返回 null。
     *
     * @return 显示物品 ItemStack，可能为 null
     */
    public ItemStack getDisplayStack() {
        if (displayItem != null) {
            return displayItem.getBaseStack();
        }
        return null;
    }

    /**
     * 获取显示物品的数量
     *
     * @return 数量，无显示物品时返回 0
     */
    public int getDisplayStackSize() {
        if (displayItem != null) {
            return displayItem.getStackSize();
        }
        return 0;
    }

    /**
     * 获取显示物品的显示名称
     *
     * @return 名称，无显示物品时返回空字符串
     */
    public String getDisplayItemName() {
        if (displayItem != null && displayItem.getBaseStack() != null) {
            String name = displayItem.getBaseStack()
                .getDisplayName();
            return name != null ? name : "";
        }
        return "";
    }

    // ==================== Getter / Setter ====================

    public int getTradeIndex() {
        return tradeIndex;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public NekoBigItemStack getDisplayItem() {
        return displayItem;
    }

    public void setDisplayItem(NekoBigItemStack displayItem) {
        this.displayItem = displayItem;
    }

    public List<NekoBigItemStack> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<NekoBigItemStack> outputs) {
        this.outputs = outputs != null ? outputs : new ArrayList<>();
    }

    public List<NekoBigItemStack> getInputs() {
        return inputs;
    }

    public void setInputs(List<NekoBigItemStack> inputs) {
        this.inputs = inputs != null ? inputs : new ArrayList<>();
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(String currencyId) {
        this.currencyId = currencyId;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public boolean isBqLocked() {
        return bqLocked;
    }

    public void setBqLocked(boolean bqLocked) {
        this.bqLocked = bqLocked;
    }

    public long getCooldownRemaining() {
        return cooldownRemaining;
    }

    public void setCooldownRemaining(long cooldownRemaining) {
        this.cooldownRemaining = cooldownRemaining;
    }

    public boolean isTradeable() {
        return tradeable;
    }

    public void setTradeable(boolean tradeable) {
        this.tradeable = tradeable;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
    }
}
