package com.miaokatze.gtit.common.items.pocket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 口袋的源质存储：{@code aspect tag → 点数} 的窄表。
 * <p>
 * <b>NBT 形状 = TC {@code AspectList} 的形状</b>（逐字对齐
 * {@code thaumcraft/api/aspects/AspectList.java:231-273} 的读写实现）：
 * {@code CompoundTag "ess" { NBTTagList "Aspects" : [ { key:String, amount:Short }, … ] }}。
 * 选它的两条理由：① TC 缺席时该形状仍是纯 NBT，不引私有类也能读写，存档不炸；
 * ② 与罐/瓶/晶化源质互译只需一次 {@code AspectList#readFromNBT}，不必逐形状适配。
 * 逐格存 <b>tag 字符串</b>，绝不存裸索引（{@code AEStackTypeRegistry} 与 aspect 注册序都会漂移）。
 * <p>
 * <b>0 值不落档</b>（与 TC 侧"扣到 0 即从表里摘掉"同构，避免存量档膨胀），读档逐条
 * {@code Math.min(amount, CAP)} 钳制（外来/手改档写进 70 点不能直接吃下）。
 * <p>
 * <b>刻意不 baked tag 清单与颜色</b>：GUI 的 12×4 行序取运行时 {@code Aspect.aspects} 的迭代序，
 * addon 可追加项 ⇒ 注册数不恒为 48。本类只认调用方给来的 tag 字符串，存储与显示解耦：
 * 多于 48 个 tag 仍照常入账，只是不显示（{@link PocketConstants#ESSENCE_DISPLAY_GRID}）。
 * <p>
 * <b>入账口径 = 全有全无</b>：一轮候选（一件物品蒸出的全部 aspect）必须<b>整体</b>放得下，
 * 任一 tag 空间不足 ⇒ 整轮零入账、上层零消耗。故走 {@link #canAcceptAll(Map)} →
 * {@link #putAll(Map)} 两段式；<b>不得</b>拿 {@link #add(String, int)} 的逐 tag 截断结果
 * 去决定"本轮要不要消耗物品"（截断后照扣 = 静默销毁价值）。
 */
public final class PocketEssenceStore {

    /** NBTTagList 里复合条目的 tag id（10）。 */
    private static final int TAG_COMPOUND = 10;
    /** NBT 的字符串 tag id（8）；TC 侧以 {@code hasKey("key")} 作条目有效性判据。 */
    private static final int TAG_STRING = 8;

    /** tag → 点数；只存在非 0 项，故表大小即"有货的格数"。 */
    private final Map<String, Integer> amounts = new LinkedHashMap<>();

    public static PocketEssenceStore readFrom(NBTTagCompound root) {
        final PocketEssenceStore store = new PocketEssenceStore();
        if (root == null) {
            return store;
        }
        final NBTTagCompound ess = root.getCompoundTag(PocketConstants.ESSENCE);
        final NBTTagList list = ess.getTagList(PocketConstants.ASPECTS, TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound entry = list.getCompoundTagAt(i);
            final String tag = entry.hasKey(PocketConstants.ASPECT_KEY, TAG_STRING)
                ? entry.getString(PocketConstants.ASPECT_KEY)
                : null;
            if (tag == null || tag.isEmpty()) {
                continue;
            }
            final int amount = entry.getShort(PocketConstants.ASPECT_AMOUNT);
            if (amount > 0) {
                store.amounts.put(tag, Math.min(amount, PocketConstants.ESSENCE_CAP_PER_TAG));
            }
        }
        return store;
    }

    public void writeTo(NBTTagCompound root) {
        final NBTTagList list = new NBTTagList();
        for (Map.Entry<String, Integer> entry : amounts.entrySet()) {
            final int amount = entry.getValue();
            // 0 值不写：与"读档时缺键即视为 0"配对，防止存量档里堆满零值条目
            if (amount > 0) {
                final NBTTagCompound item = new NBTTagCompound();
                item.setString(PocketConstants.ASPECT_KEY, entry.getKey());
                item.setShort(PocketConstants.ASPECT_AMOUNT, (short) amount);
                list.appendTag(item);
            }
        }
        final NBTTagCompound ess = new NBTTagCompound();
        ess.setTag(PocketConstants.ASPECTS, list);
        root.setTag(PocketConstants.ESSENCE, ess);
    }

    /** 某 tag 当前点数，缺席为 0。 */
    public int get(String tag) {
        final Integer value = tag == null ? null : amounts.get(tag);
        return value == null ? 0 : value;
    }

    /** 表里是否已有该 tag 的点数（>0 才算"含有"）。 */
    public boolean has(String tag) {
        return get(tag) > 0;
    }

    /** 该 tag 还能收多少点。 */
    public int roomFor(String tag) {
        return PocketConstants.ESSENCE_CAP_PER_TAG - get(tag);
    }

    /** 该 tag 是否已到 64 点上限。 */
    public boolean isFull(String tag) {
        return get(tag) >= PocketConstants.ESSENCE_CAP_PER_TAG;
    }

    /**
     * 表内已登记的 tag 是否全部到顶。<b>只供 GUI 置灰</b>（"看上去满了"），
     * 不代表"收不进"。
     * <p>
     * ⚠ <b>禁止用它参与"本轮要不要消耗物品"的判定</b>：存储与 48 格显示解耦，未登记的 tag
     * 永远收得进（表为空时本方法更是恒 false），所以它是<b>偏严又偏松</b>的双重错判来源。
     * 消耗判定只走 {@link #canAcceptAll(Map)} 或逐 tag {@link #add(String, int)} 的返回值。
     */
    public boolean isFull() {
        if (amounts.isEmpty()) {
            return false;
        }
        for (int amount : amounts.values()) {
            if (amount < PocketConstants.ESSENCE_CAP_PER_TAG) {
                return false;
            }
        }
        return true;
    }

    /**
     * 一轮候选能否<b>整体</b>入账（全有全无的"预检"段）。
     * <p>
     * 候选里任一 tag 的 {@code current + amount} 超过单格上限即整轮判 false；未登记的 tag
     * 视为从 0 起算（仍能收 {@code ESSENCE_CAP_PER_TAG} 点）。非正数条目天然"放得下"（不占空间），
     * 空候选恒为 true。
     *
     * @param candidates tag → 本轮应得点数；null/空视为无候选
     * @return true 表示整份候选都能入账，调用方随后可 {@link #putAll(Map)} 提交并据此消耗物品
     */
    public boolean canAcceptAll(Map<String, Integer> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Integer> candidate : candidates.entrySet()) {
            final String tag = candidate.getKey();
            final Integer amount = candidate.getValue();
            if (tag == null || tag.isEmpty() || amount == null || amount <= 0) {
                continue;
            }
            if (get(tag) + amount > PocketConstants.ESSENCE_CAP_PER_TAG) {
                return false;
            }
        }
        return true;
    }

    /**
     * 提交一轮候选（全有全无的"入账"段），须与 {@link #canAcceptAll(Map)} 成对使用。
     * <p>
     * 未预检直接调用时按逐格上限兜底截断（宁少不炸），但<b>消耗判定必须来自预检</b>，
     * 不允许"截断了还照扣物品"。0 值条目不落表。
     *
     * @return 实际入账总点数
     */
    public int putAll(Map<String, Integer> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int accepted = 0;
        for (Map.Entry<String, Integer> candidate : candidates.entrySet()) {
            final Integer amount = candidate.getValue();
            accepted += add(candidate.getKey(), amount == null ? 0 : amount);
        }
        return accepted;
    }

    /**
     * 单 tag 入账原语，按 64 点上限截断。
     * <p>
     * ⚠ 蒸馏/通道的<b>整轮</b>判定不得建立在本方法的截断行为上（见类注释与
     * {@link #canAcceptAll(Map)}）；它回报的是"这一格实际进了多少"这类局部事实。
     *
     * @return 实际入账点数（0 表示一格未进）
     */
    public int add(String tag, int amount) {
        if (tag == null || tag.isEmpty() || amount <= 0) {
            return 0;
        }
        final int current = get(tag);
        final int added = Math.min(amount, PocketConstants.ESSENCE_CAP_PER_TAG - current);
        if (added <= 0) {
            return 0;
        }
        amounts.put(tag, current + added);
        return added;
    }

    /**
     * 出账；扣到 0 时把该 tag 从表里摘掉（与"0 值不落档"同构）。
     *
     * @return 实际取出点数（请求量超过存量时按存量给）
     */
    public int extract(String tag, int amount) {
        final int current = get(tag);
        if (current <= 0 || amount <= 0) {
            return 0;
        }
        final int taken = Math.min(amount, current);
        final int left = current - taken;
        if (left <= 0) {
            amounts.remove(tag);
        } else {
            amounts.put(tag, left);
        }
        return taken;
    }

    /** 快照（不可变副本），GUI 同步与蒸馏侧读它，不暴露内部表。 */
    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
    }

    /** 当前有货的 tag 集合，按入账顺序。 */
    public Set<String> tags() {
        return Collections.unmodifiableSet(new LinkedHashMap<>(amounts).keySet());
    }

    public int totalPoints() {
        int total = 0;
        for (int amount : amounts.values()) {
            total += amount;
        }
        return total;
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public void clear() {
        amounts.clear();
    }
}
