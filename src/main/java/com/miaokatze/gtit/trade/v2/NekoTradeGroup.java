package com.miaokatze.gtit.trade.v2;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 交易组，替代 VM 的 TradeGroup
 * <p>
 * 包含一组交易（trades）及其冷却时间（cooldown）、最大交易次数（maxTrades）、
 * 前置条件（requirementSet）、分类（category）、标签页（tabId）、排序（orderId）
 * 以及 BQ 任务绑定（bqQuestId）。
 * <p>
 * 使用 CopyOnWriteArrayList 保证线程安全，适用于多线程并发读场景。
 */
public class NekoTradeGroup {

    /** 交易组唯一标识 */
    private UUID id;
    /** 交易列表（线程安全，final 保证引用不变） */
    private final CopyOnWriteArrayList<NekoTrade> trades = new CopyOnWriteArrayList<>();
    /** 冷却时间（tick），-1 表示无冷却 */
    private int cooldown = -1;
    /** 最大交易次数，-1 表示无限制 */
    private int maxTrades = -1;
    /** 交易分类 */
    private NekoTradeCategory category;
    /** 标签页 ID，默认 3 */
    private int tabId = 3;
    /** 排序序号 */
    private int orderId;
    /** 关联的 BQ 任务 UUID */
    private String bqQuestId;
    /** 前置条件集合（线程安全，final 保证引用不变） */
    private final Set<NekoTradeCondition> requirementSet = ConcurrentHashMap.newKeySet();

    /**
     * 默认构造器，生成随机 UUID
     */
    public NekoTradeGroup() {
        this.id = UUID.randomUUID();
    }

    /**
     * 指定 UUID 构造器
     *
     * @param id 交易组 UUID
     */
    public NekoTradeGroup(UUID id) {
        this.id = id;
    }

    /**
     * 检查指定玩家是否满足所有前置条件
     *
     * @param playerId 玩家 UUID
     * @return 全部满足返回 true，任一不满足返回 false
     */
    public boolean isConditionsSatisfied(UUID playerId) {
        for (NekoTradeCondition condition : requirementSet) {
            if (!condition.isSatisfied(playerId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 添加前置条件
     *
     * @param condition 交易条件
     */
    public void addCondition(NekoTradeCondition condition) {
        requirementSet.add(condition);
    }

    /**
     * 清空所有前置条件
     */
    public void clearConditions() {
        requirementSet.clear();
    }

    /**
     * 是否没有前置条件
     *
     * @return 无条件返回 true
     */
    public boolean hasNoConditions() {
        return requirementSet.isEmpty();
    }

    /**
     * 获取前置条件集合
     *
     * @return 条件集合（直接返回引用，调用方可遍历）
     */
    public Set<NekoTradeCondition> getRequirements() {
        return requirementSet;
    }

    /**
     * 序列化到 NBT
     * <p>
     * 可选字段（null）不写入，节省存储空间。
     *
     * @return NBT 标签化合物
     */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        // 写入 UUID（高低位）
        nbt.setLong("idMsb", id.getMostSignificantBits());
        nbt.setLong("idLsb", id.getLeastSignificantBits());
        // 写入基本字段
        nbt.setInteger("cooldown", cooldown);
        nbt.setInteger("maxTrades", maxTrades);
        nbt.setString("category", category.getKey());
        nbt.setInteger("tabId", tabId);
        nbt.setInteger("orderId", orderId);
        // bqQuestId 仅在非 null 时写入
        if (bqQuestId != null) {
            nbt.setString("bqQuestId", bqQuestId);
        }
        // 写入交易列表
        if (!trades.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (NekoTrade trade : trades) {
                list.appendTag(trade.writeToNBT());
            }
            nbt.setTag("trades", list);
        }
        // 写入前置条件列表
        if (!requirementSet.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (NekoTradeCondition condition : requirementSet) {
                NBTTagCompound condNbt = new NBTTagCompound();
                // 存储条件名称，用于反序列化时通过工厂创建对应实例
                condNbt.setString("conditionName", condition.getConditionName());
                // 条件数据存储在 "data" 子标签中
                condNbt.setTag("data", condition.writeToNBT());
                list.appendTag(condNbt);
            }
            nbt.setTag("requirements", list);
        }
        return nbt;
    }

    /**
     * 从 NBT 反序列化
     * <p>
     * 加载前先清空 trades 和 requirementSet，确保重复加载时的幂等性。
     *
     * @param nbt NBT 标签化合物
     */
    public void loadFromNBT(NBTTagCompound nbt) {
        // 读取 UUID
        id = new UUID(nbt.getLong("idMsb"), nbt.getLong("idLsb"));
        // 读取基本字段
        cooldown = nbt.getInteger("cooldown");
        maxTrades = nbt.getInteger("maxTrades");
        category = NekoTradeCategory.ofString(nbt.getString("category"));
        tabId = nbt.getInteger("tabId");
        orderId = nbt.getInteger("orderId");
        // 空串视为 null
        String questId = nbt.getString("bqQuestId");
        bqQuestId = questId.isEmpty() ? null : questId;
        // 清空并读取交易列表
        trades.clear();
        if (nbt.hasKey("trades")) {
            NBTTagList list = nbt.getTagList("trades", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NekoTrade trade = new NekoTrade();
                trade.loadFromNBT(list.getCompoundTagAt(i));
                trades.add(trade);
            }
        }
        // 清空并读取前置条件
        requirementSet.clear();
        if (nbt.hasKey("requirements")) {
            NBTTagList list = nbt.getTagList("requirements", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound condNbt = list.getCompoundTagAt(i);
                String conditionName = condNbt.getString("conditionName");
                // 通过工厂创建对应类型的条件实例
                NekoTradeCondition condition = NekoConditionFactory.createCondition(conditionName);
                if (condition != null) {
                    // 从 "data" 子标签读取条件数据
                    condition.loadFromNBT(condNbt.getCompoundTag("data"));
                    requirementSet.add(condition);
                }
            }
        }
    }

    // --- Getters ---

    public UUID getId() {
        return id;
    }

    public List<NekoTrade> getTrades() {
        return trades;
    }

    public int getCooldown() {
        return cooldown;
    }

    public int getMaxTrades() {
        return maxTrades;
    }

    public NekoTradeCategory getCategory() {
        return category;
    }

    public int getTabId() {
        return tabId;
    }

    public int getOrderId() {
        return orderId;
    }

    public String getBqQuestId() {
        return bqQuestId;
    }

    // --- Setters ---

    public void setId(UUID id) {
        this.id = id;
    }

    public void setCategory(NekoTradeCategory category) {
        this.category = category;
    }

    public void setTabId(int tabId) {
        this.tabId = tabId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public void setBqQuestId(String bqQuestId) {
        this.bqQuestId = bqQuestId;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public void setMaxTrades(int maxTrades) {
        this.maxTrades = maxTrades;
    }
}
