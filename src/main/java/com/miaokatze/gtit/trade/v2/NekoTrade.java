package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;

/**
 * 单笔交易，替代 VM 的 Trade
 * <p>
 * 包含输入物品（fromItems）、输出物品（toItems）、猫猫币花费（currencyId/currencyCost），
 * 以及用于 GUI 显示的 displayItem。
 * <p>
 * <b>v1.7.6 G3② 货币解绑</b>：猫猫币不再依赖独立 currencyId/currencyCost 字段表达，
 * 而是作为普通物品条目混在 fromItems（需求扣钱包）/toItems（产出入钱包）中，
 * 通过 {@link NekoCurrencyRegistrar#getNekoCurrencyId} 实时识别。
 * 旧 JSON 的 currency 字段由 {@link NekoTradeRegistryV2} 加载时合成为 fromItems 货币条目，
 * 旧字段仅作防御兜底读取（见 {@link #getCurrencyCosts()}），统一单一路径。
 */
public class NekoTrade {

    /** 输入物品列表（玩家需要提供的物品） */
    private List<NekoBigItemStack> fromItems = new ArrayList<>();
    /** 输出物品列表（玩家获得的物品） */
    private List<NekoBigItemStack> toItems = new ArrayList<>();
    /** 显示物品（用于 GUI 图标，为 null 时取 toItems 首项） */
    private NekoBigItemStack displayItem;
    /** 猫猫币唯一标识 */
    private String currencyId;
    /** 猫猫币花费数量 */
    private int currencyCost;
    /**
     * 是否严格匹配 NBT（v1.7.6 G3⑤）
     * <p>
     * true = 需求物品按物品+NBT 精确匹配；false = 仅按物品匹配（忽略 NBT 差异）。
     * 统一默认 false（用户确认口径）：旧 JSON 无该字段也视为 false，
     * 旧带 NBT 需求的交易匹配变宽松，属行为变更（已写入 commit 说明）。
     */
    private boolean recordNBT = false;

    /**
     * 默认构造器，字段已在声明处初始化为空集合
     */
    public NekoTrade() {
        // 字段已在声明处初始化，无需额外操作
    }

    /**
     * 是否有猫猫币花费（v1.7.6 G3② 货币解绑：语义扩展为「含货币需求」）
     * <p>
     * 旧 currencyId/currencyCost 字段非空（防御兜底，正常加载已合成进 fromItems）
     * 或 fromItems 中含猫猫币货币条目时返回 true。
     *
     * @return 有货币需求返回 true
     */
    public boolean hasCurrencyCost() {
        if (currencyId != null && currencyCost > 0) {
            return true;
        }
        return !getCurrencyEntries().isEmpty();
    }

    /**
     * 是否有输入物品
     *
     * @return 有输入物品返回 true
     */
    public boolean hasFromItems() {
        return !fromItems.isEmpty();
    }

    /**
     * 是否为纯猫猫币交易（有货币花费但无普通输入物品）
     * <p>
     * v1.7.6 G3② 货币解绑：货币条目混入 fromItems，
     * 「纯货币」判定改为「无普通（非货币）需求条目」。
     *
     * @return 纯货币交易返回 true
     */
    public boolean isPureCurrencyTrade() {
        return hasCurrencyCost() && getNonCurrencyFromItems().isEmpty();
    }

    // ==================== v1.7.6 G3② 货币解绑：条目识别辅助 ====================

    /**
     * 获取 fromItems 中的猫猫币货币需求条目
     * <p>
     * 通过 {@link NekoCurrencyRegistrar#getNekoCurrencyId} 实时识别，
     * 货币 ID 信息不落盘于条目本身（NekoBigItemStack 仅承载物品）。
     *
     * @return 货币需求条目列表（新建列表，修改不影响内部数据）
     */
    public List<NekoBigItemStack> getCurrencyEntries() {
        List<NekoBigItemStack> list = new ArrayList<>();
        for (NekoBigItemStack item : fromItems) {
            if (item != null && NekoCurrencyRegistrar.getNekoCurrencyId(item.getBaseStack()) != null) {
                list.add(item);
            }
        }
        return list;
    }

    /**
     * 获取 fromItems 中的普通（非货币）需求条目
     * <p>
     * 执行器物品匹配/扣减必须使用本列表——防止猫猫币条目被当普通物品从输入槽扣除。
     *
     * @return 普通需求条目列表（新建列表，修改不影响内部数据）
     */
    public List<NekoBigItemStack> getNonCurrencyFromItems() {
        List<NekoBigItemStack> list = new ArrayList<>();
        for (NekoBigItemStack item : fromItems) {
            if (item != null && NekoCurrencyRegistrar.getNekoCurrencyId(item.getBaseStack()) == null) {
                list.add(item);
            }
        }
        return list;
    }

    /**
     * 获取 toItems 中的猫猫币货币产物条目（交易成功时入钱包而非输出槽）
     *
     * @return 货币产物条目列表（新建列表，修改不影响内部数据）
     */
    public List<NekoBigItemStack> getCurrencyToItems() {
        List<NekoBigItemStack> list = new ArrayList<>();
        for (NekoBigItemStack item : toItems) {
            if (item != null && NekoCurrencyRegistrar.getNekoCurrencyId(item.getBaseStack()) != null) {
                list.add(item);
            }
        }
        return list;
    }

    /**
     * 获取 toItems 中的普通（非货币）产物条目（交易成功时进输出槽）
     *
     * @return 普通产物条目列表（新建列表，修改不影响内部数据）
     */
    public List<NekoBigItemStack> getNonCurrencyToItems() {
        List<NekoBigItemStack> list = new ArrayList<>();
        for (NekoBigItemStack item : toItems) {
            if (item != null && NekoCurrencyRegistrar.getNekoCurrencyId(item.getBaseStack()) == null) {
                list.add(item);
            }
        }
        return list;
    }

    /**
     * 汇总货币需求：货币 ID → 总需求数量
     * <p>
     * 合并 fromItems 中的货币条目（同 ID 合计）与旧 currencyId/currencyCost 字段
     * （防御兜底，正常加载已合成进 fromItems）。支持需求格混放多种货币。
     *
     * @return 货币 ID → 需求数量（保持插入顺序；数量合计溢出时钳制到 int 上限）
     */
    public Map<String, Integer> getCurrencyCosts() {
        Map<String, Integer> costs = new LinkedHashMap<>();
        // 旧字段路径（防御：正常加载已合成进 fromItems，不会走到）
        if (currencyId != null && currencyCost > 0) {
            costs.put(currencyId, currencyCost);
        }
        // fromItems 货币条目按 ID 分组合计
        for (NekoBigItemStack entry : getCurrencyEntries()) {
            String id = NekoCurrencyRegistrar.getNekoCurrencyId(entry.getBaseStack());
            if (id != null) {
                Integer existing = costs.get(id);
                long sum = (existing != null ? existing : 0) + (long) entry.getStackSize();
                costs.put(id, (int) Math.min(Integer.MAX_VALUE, sum));
            }
        }
        return costs;
    }

    /**
     * 序列化到 NBT
     * <p>
     * 可选字段（null/空/0）不写入，节省存储空间。
     *
     * @return NBT 标签化合物
     */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        // 写入猫猫币信息（仅在有值时写入）
        if (currencyId != null) {
            nbt.setString("currencyId", currencyId);
        }
        if (currencyCost > 0) {
            nbt.setInteger("currencyCost", currencyCost);
        }
        // 写入 NBT 严格匹配开关（false 也写入，保持显式语义，避免旧档读取歧义）
        nbt.setBoolean("recordNBT", recordNBT);
        // 写入显示物品
        if (displayItem != null) {
            nbt.setTag("displayItem", displayItem.writeToNBT());
        }
        // 写入输入物品列表
        if (!fromItems.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (NekoBigItemStack item : fromItems) {
                list.appendTag(item.writeToNBT());
            }
            nbt.setTag("fromItems", list);
        }
        // 写入输出物品列表
        if (!toItems.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (NekoBigItemStack item : toItems) {
                list.appendTag(item.writeToNBT());
            }
            nbt.setTag("toItems", list);
        }
        return nbt;
    }

    /**
     * 从 NBT 反序列化
     * <p>
     * 加载前先清空现有集合，确保重复加载时的幂等性。
     *
     * @param nbt NBT 标签化合物
     */
    public void loadFromNBT(NBTTagCompound nbt) {
        // 先清空集合，避免重复加载时数据残留
        fromItems.clear();
        toItems.clear();
        // 读取猫猫币信息（空串视为 null）
        String id = nbt.getString("currencyId");
        currencyId = id.isEmpty() ? null : id;
        currencyCost = nbt.getInteger("currencyCost");
        // 读取 NBT 严格匹配开关（旧档无该键时 getBoolean 返回 false，符合「统一默认不勾」口径）
        recordNBT = nbt.getBoolean("recordNBT");
        // 读取显示物品
        if (nbt.hasKey("displayItem")) {
            displayItem = NekoBigItemStack.loadFromNBT(nbt.getCompoundTag("displayItem"));
        }
        // 读取输入物品列表
        if (nbt.hasKey("fromItems")) {
            NBTTagList list = nbt.getTagList("fromItems", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NekoBigItemStack item = NekoBigItemStack.loadFromNBT(list.getCompoundTagAt(i));
                if (item != null) {
                    fromItems.add(item);
                }
            }
        }
        // 读取输出物品列表
        if (nbt.hasKey("toItems")) {
            NBTTagList list = nbt.getTagList("toItems", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NekoBigItemStack item = NekoBigItemStack.loadFromNBT(list.getCompoundTagAt(i));
                if (item != null) {
                    toItems.add(item);
                }
            }
        }
    }

    // --- Getters ---

    public List<NekoBigItemStack> getFromItems() {
        return fromItems;
    }

    public List<NekoBigItemStack> getToItems() {
        return toItems;
    }

    /**
     * 获取显示物品
     * <p>
     * 优先返回显式设置的 displayItem；未设置时取 toItems 首项；均无则返回 null。
     *
     * @return 显示物品，可能为 null
     */
    public NekoBigItemStack getDisplayItem() {
        if (displayItem != null) {
            return displayItem;
        }
        if (!toItems.isEmpty()) {
            return toItems.get(0);
        }
        return null;
    }

    /**
     * 获取货币 ID（显示兼容用）
     * <p>
     * v1.7.6 G3② 货币解绑：旧字段优先；旧字段为空时返回 fromItems 首个货币条目的货币 ID。
     * 多货币混放时仅返回首种——完整货币需求请用 {@link #getCurrencyCosts()}。
     *
     * @return 货币 ID，无货币需求时返回 null
     */
    public String getCurrencyId() {
        if (currencyId != null) {
            return currencyId;
        }
        for (NekoBigItemStack entry : getCurrencyEntries()) {
            String id = NekoCurrencyRegistrar.getNekoCurrencyId(entry.getBaseStack());
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /**
     * 获取货币花费数量（显示兼容用）
     * <p>
     * v1.7.6 G3② 货币解绑：旧字段优先（&gt;0）；否则返回 {@link #getCurrencyId()}
     * 对应货币的需求合计数量。
     *
     * @return 货币花费数量，无货币需求时返回 0
     */
    public int getCurrencyCost() {
        if (currencyId != null && currencyCost > 0) {
            return currencyCost;
        }
        String id = getCurrencyId();
        if (id == null) {
            return 0;
        }
        Integer cost = getCurrencyCosts().get(id);
        return cost != null ? cost : 0;
    }

    /**
     * 是否严格匹配 NBT（v1.7.6 G3⑤）
     *
     * @return true = 需求物品按物品+NBT 精确匹配；false = 仅按物品匹配
     */
    public boolean isRecordNBT() {
        return recordNBT;
    }

    // --- Setters ---

    public void setCurrencyId(String currencyId) {
        this.currencyId = currencyId;
    }

    public void setCurrencyCost(int currencyCost) {
        this.currencyCost = currencyCost;
    }

    public void setDisplayItem(NekoBigItemStack displayItem) {
        this.displayItem = displayItem;
    }

    /**
     * 设置是否严格匹配 NBT（v1.7.6 G3⑤）
     *
     * @param recordNBT true = 需求物品按物品+NBT 精确匹配
     */
    public void setRecordNBT(boolean recordNBT) {
        this.recordNBT = recordNBT;
    }
}
