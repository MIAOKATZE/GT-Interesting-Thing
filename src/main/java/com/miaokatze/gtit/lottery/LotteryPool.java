package com.miaokatze.gtit.lottery;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.util.NbtBase64Util;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 奖池
 * <p>
 * v1.7.6 起卡池对应一组需求物品（{@link #costItems}，猫猫币条目走钱包扣款、普通物品从机器输入槽扣除），
 * 包含若干奖品条目（{@link #entries}，按权重随机）与保底配置（{@link #pityConfig}）。
 * <p>
 * 兼容：旧字段 {@link #nekoCurrencyId} × {@link #costPerDraw} 保留——JSON 无 costItems 时
 * 由 {@link #synthesizeCostItemsFromLegacy()} 合成一条猫猫币货币条目（向后兼容）。
 * <p>
 * 字段与 Gson 直接映射（lottery.json 的 pools[] 元素）；costItems 的序列化适配器注册于
 * {@link LotteryConfig} 的 Gson 实例（item/meta/amount/nbtBase64/oreDict 格式）。
 */
public class LotteryPool {

    /** 卡池 ID（如 "neko"/"shimmering"） */
    private String id;
    /** 显示名称 */
    private String name;
    /** 消耗货币 ID（neko / shimmeringNeko）——旧字段，costItems 为空时用于兼容合成 */
    private String nekoCurrencyId;
    /** 单次抽取消耗数量——旧字段，costItems 为空时用于兼容合成 */
    private int costPerDraw;
    /** page 图标物品 ID（modid:name 格式），空串表示未设置（GUI 回退货币图标） */
    private String iconItem = "";
    /** page 图标物品 meta */
    private int iconMeta;
    /** page 图标物品 NBT（Base64 编码，可选） */
    private String iconNbt = "";
    /** 单次抽取消耗的需求物品列表（v1.7.6 新增，货币解绑落点） */
    private List<NekoBigItemStack> costItems;
    /** 奖品条目列表 */
    private List<LotteryEntry> entries;
    /** 保底配置 */
    private PityConfig pityConfig;
    /** 权重随机数生成器（transient，不参与 Gson） */
    private transient final Random random = new Random();

    public LotteryPool() {
        this.entries = new ArrayList<>();
        this.costItems = new ArrayList<>();
    }

    public LotteryPool(String id, String name, String nekoCurrencyId, int costPerDraw, PityConfig pityConfig) {
        this();
        this.id = id;
        this.name = name;
        this.nekoCurrencyId = nekoCurrencyId;
        this.costPerDraw = costPerDraw;
        this.pityConfig = pityConfig;
    }

    /**
     * 旧配置兼容合成（加载 lottery.json 后调用）
     * <p>
     * 当 costItems 为空且旧字段（nekoCurrencyId × costPerDraw）有效时，
     * 合成一条猫猫币货币需求条目，保证旧配置行为不变。
     */
    public void synthesizeCostItemsFromLegacy() {
        if (costItems != null && !costItems.isEmpty()) return;
        if (nekoCurrencyId == null || nekoCurrencyId.isEmpty() || costPerDraw <= 0) return;
        ItemStack currencyStack = NekoCurrencyRegistrar.getItemStack(nekoCurrencyId, costPerDraw);
        if (currencyStack == null) return;
        if (costItems == null) costItems = new ArrayList<>();
        costItems.add(new NekoBigItemStack(currencyStack));
    }

    /**
     * 全部条目的权重总和（软保底加成后的动态权重不在此计算）
     */
    public int getTotalWeight() {
        int total = 0;
        for (LotteryEntry entry : entries) {
            if (entry != null) total += Math.max(0, entry.getWeight());
        }
        return total;
    }

    /**
     * 从本池中随机选取一个「保底稀有度及以上」的条目（硬保底强制替换用）
     * <p>
     * 在满足 {@code rarity ≥ pityConfig.guaranteedRarity} 的条目子集中按权重随机；
     * 无满足条目时回退到全池随机（配置错误的兜底，避免抽奖死锁）。
     *
     * @return 保底条目；池为空时返回 null
     */
    public LotteryEntry getPityPrizeEntry() {
        if (entries.isEmpty()) return null;
        LotteryRarity guaranteed = pityConfig != null ? pityConfig.getGuaranteedRarity() : LotteryRarity.EPIC;
        List<LotteryEntry> candidates = new ArrayList<>();
        int totalWeight = 0;
        for (LotteryEntry entry : entries) {
            if (entry != null && entry.getRarity()
                .isAtLeast(guaranteed) && entry.getWeight() > 0) {
                candidates.add(entry);
                totalWeight += entry.getWeight();
            }
        }
        // 无保底稀有度条目：回退全池（配置容错）
        if (candidates.isEmpty()) {
            return entries.get(random.nextInt(entries.size()));
        }
        int roll = random.nextInt(totalWeight);
        for (LotteryEntry entry : candidates) {
            roll -= entry.getWeight();
            if (roll < 0) return entry;
        }
        return candidates.get(candidates.size() - 1);
    }

    /**
     * 校验本池是否可抽取：条目非空且总权重 > 0
     */
    public boolean validate() {
        return entries != null && !entries.isEmpty() && getTotalWeight() > 0;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNekoCurrencyId() {
        return nekoCurrencyId;
    }

    public int getCostPerDraw() {
        return costPerDraw;
    }

    public List<LotteryEntry> getEntries() {
        if (entries == null) entries = new ArrayList<>();
        return entries;
    }

    public PityConfig getPityConfig() {
        if (pityConfig == null) pityConfig = PityConfig.createDefault();
        return pityConfig;
    }

    /** 按条目 ID 查找条目（历史/结果同步展示用） */
    public LotteryEntry getEntryById(String entryId) {
        if (entryId == null) return null;
        for (LotteryEntry entry : getEntries()) {
            if (entry != null && entryId.equals(entry.getId())) return entry;
        }
        return null;
    }

    // ==================== 图标（复用 NekoPageEntry 模式） ====================

    /**
     * 从 ItemStack 提取图标信息并设置到本池
     * <p>
     * 传入 null 时清空图标（GUI 回退货币图标显示）。
     */
    public void setIconFromItemStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            this.iconItem = "";
            this.iconMeta = 0;
            this.iconNbt = "";
            return;
        }
        this.iconItem = Item.itemRegistry.getNameForObject(stack.getItem())
            .toString();
        this.iconMeta = stack.getItemDamage();
        if (stack.hasTagCompound() && stack.getTagCompound() != null) {
            this.iconNbt = NbtBase64Util.nbtToBase64(stack.getTagCompound());
        } else {
            this.iconNbt = "";
        }
    }

    /**
     * 将图标信息转换回 ItemStack（GUI 渲染/编辑面板展示用）
     *
     * @return 图标物品堆；未设置或物品无法解析时返回 null
     */
    public ItemStack toIconItemStack() {
        if (iconItem == null || iconItem.isEmpty()) return null;
        String[] parts = iconItem.split(":", 2);
        if (parts.length < 2) return null;
        Item item = GameRegistry.findItem(parts[0], parts[1]);
        if (item == null) return null;
        ItemStack stack = new ItemStack(item, 1, iconMeta);
        if (iconNbt != null && !iconNbt.isEmpty()) {
            NBTTagCompound nbt = NbtBase64Util.nbtFromBase64(iconNbt);
            if (nbt != null) {
                stack.setTagCompound(nbt);
            }
        }
        return stack;
    }

    // ==================== Getter / Setter ====================

    public String getIconItem() {
        return iconItem == null ? "" : iconItem;
    }

    public void setIconItem(String iconItem) {
        this.iconItem = iconItem == null ? "" : iconItem;
    }

    public int getIconMeta() {
        return iconMeta;
    }

    public void setIconMeta(int iconMeta) {
        this.iconMeta = iconMeta;
    }

    public String getIconNbt() {
        return iconNbt == null ? "" : iconNbt;
    }

    public void setIconNbt(String iconNbt) {
        this.iconNbt = iconNbt == null ? "" : iconNbt;
    }

    /**
     * 单次抽取消耗的需求物品列表（永不为 null）
     * <p>
     * 猫猫币条目由执行时实时识别（{@code NekoCurrencyRegistrar.getNekoCurrencyId(stack) != null}），
     * 不在此落盘货币 ID。
     */
    public List<NekoBigItemStack> getCostItems() {
        if (costItems == null) costItems = new ArrayList<>();
        return costItems;
    }

    public void setCostItems(List<NekoBigItemStack> costItems) {
        this.costItems = costItems == null ? new ArrayList<>() : costItems;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * 旧字段写回（v1.7.6 池编辑保存时按 costItems 首个猫猫币条目同步重写，保持兼容展示口径一致）
     */
    public void setNekoCurrencyId(String nekoCurrencyId) {
        this.nekoCurrencyId = nekoCurrencyId;
    }

    /**
     * 旧字段写回（同上；0 表示无货币消耗）
     */
    public void setCostPerDraw(int costPerDraw) {
        this.costPerDraw = Math.max(0, costPerDraw);
    }

    public void setPityConfig(PityConfig pityConfig) {
        this.pityConfig = pityConfig;
    }
}
