package com.miaokatze.gtit.common.items.pocket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * 口袋的源质存储：{@code aspect tag → 点数} 的窄表 + <b>格位归属表</b>（R78③）。
 * <p>
 * <b>NBT 形状 = TC {@code AspectList} 的形状</b>（逐字对齐
 * {@code thaumcraft/api/aspects/AspectList.java:231-273} 的读写实现）：
 * {@code CompoundTag "ess" { NBTTagList "Aspects" : [ { key:String, amount:Short }, … ] }}。
 * 选它的两条理由：① TC 缺席时该形状仍是纯 NBT，不引私有类也能读写，存档不炸；
 * ② 与罐/瓶/晶化源质互译只需一次 {@code AspectList#readFromNBT}，不必逐形状适配。
 * 逐格存 <b>tag 字符串</b>，绝不存裸索引（{@code AEStackTypeRegistry} 与 aspect 注册序都会漂移）。
 * <p>
 * <b>★R78③：格序 = 该 tag 首次入账的顺序</b>（用户需求"有之后按出现顺序排"）。第二根键
 * {@link PocketConstants#ESSENCE_CELL_ORDER}（挂在<b>根</b>上，与 {@code ess} 平级，
 * 这样 {@code ess} 本身仍是纯粹的 TC 形状）存"格号 → tag"的归属表，三条口径：
 * <ol>
 * <li><b>入账时占格</b>：{@link #add(String, int)} 真的进了点数才占格（{@link #assignCell(String)}），
 * 占<b>最小空位</b> ⇒ 先入账的落前面的格；</li>
 * <li><b>持久化</b>：{@link #writeTo(NBTTagCompound)} 原样写出，重开面板/重进世界不重排；</li>
 * <li>★<b>撤空不回收</b>：{@link #extract(String, int)} 把点数扣到 0 只摘 {@code amounts} 条目，
 * <b>不动</b>格位归属 ⇒ 再灌同一种源质回到<b>原来那一格</b>，也不会因一次灌入把全部格重排
 * （用户裁定"保留"；且 ghost 声明是按<b>格号</b>索引的，回收格位会让已声明的格拉错东西）。</li>
 * </ol>
 * 旧档（无该键）由 {@link #readFrom(NBTTagCompound)} 按 {@code Aspects} 的<b>条目顺序</b>补出
 * 归属（LinkedHashMap 的插入序即"首次入账序"，且写入端按同一顺序落档）⇒ 老档不炸、不丢格、
 * 也不会"重开一次就换一套格序"。
 * <p>
 * <b>0 值不落档</b>（与 TC 侧"扣到 0 即从表里摘掉"同构，避免存量档膨胀），读档逐条
 * {@code Math.min(amount, CAP)} 钳制（外来/手改档写进 70 点不能直接吃下）。
 * <p>
 * <b>刻意不 baked tag 清单与颜色</b>：GUI 的行序取本表的格位归属（运行期数据，addon 可追加项
 * ⇒ 注册数不恒为 {@link PocketConstants#ESSENCE_DISPLAY_GRID}）。本类只认调用方给来的 tag 字符串，
 * 存储与显示解耦：多于 72 个 tag 仍照常入账，只是<b>没有格位可占</b>（{@link #assignCell(String)}
 * 返回 −1，显示侧据此走 {@code aspect.overflow_note} 兜底）。
 * <p>
 * <b>入账口径 = 全有全无，作用单位是「一格物品」</b>（★R84：对象是格；R83 A2 / D-3 β 的旧口径原文为
 * "<b>作用单位是「一组物品」</b>：一组（同一 item + 同一 damage 散在多格也算一组）蒸出的全部 aspect
 * 必须整体放得下"——其中"多格折成一组"已被 R84 作废，"整体放得下否则零入账、零消耗"这条纪律不变）：
 * 一格蒸出的全部 aspect 必须<b>整体</b>放得下，任一 tag 空间不足 ⇒ <b>该格</b>零入账、
 * 上层对该格零消耗，其它格照常入账。故走 {@link #canAcceptAll(Map, Map)}（第二参是本轮已排给其它格的
 * 预留量）→ {@link #putAll(Map)} 两段式；<b>不得</b>拿 {@link #add(String, int)} 的逐 tag 截断结果
 * 去决定"本轮要不要消耗物品"（截断后照扣 = 静默销毁价值）。
 * <p>
 * ★两种"放不下"必须分开（R83 偏差 3c）：{@link #exceedsCellCap(Map)} 为真 = 单件原量本身就超
 * {@link PocketConstants#ESSENCE_CAP_PER_TAG} ⇒ <b>再怎么腾格也放不下</b>，只能放弃该格并留下读数；
 * 只有"当前存量 + 预留量装得下与否"这一种才是可解卡的"需要空间"（停在满格不重跑）。
 * ★R84 逐格后的算术提醒：同一 tag 的 12 份候选<b>共享</b>同一个 {@code ESSENCE_CAP_PER_TAG}，
 * 越靠后的格越可能被整体放弃（这不是缺陷，是全有全无的必然；被挡下的格下一轮重试）。
 */
public final class PocketEssenceStore {

    /** NBTTagList 里复合条目的 tag id（10）。 */
    private static final int TAG_COMPOUND = 10;
    /** NBT 的字符串 tag id（8）；TC 侧以 {@code hasKey("key")} 作条目有效性判据。 */
    private static final int TAG_STRING = 8;

    /** tag → 点数；只存在非 0 项，故表大小即"有货的格数"。 */
    private final Map<String, Integer> amounts = new LinkedHashMap<>();
    /**
     * 格位归属表：下标 = 显示格号 {@code 0…ESSENCE_DISPLAY_GRID−1}，值 = 该格归属的 tag
     * （{@code null} = 该格从未被占过）。★长度<b>恒定</b>（R32 双端同树的前提），
     * 变的只是"哪个 tag 落在第几格"。
     */
    private final String[] cellTags = new String[PocketConstants.ESSENCE_DISPLAY_GRID];

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
        store.readCellOrder(root);
        return store;
    }

    /**
     * 读格位归属（{@link PocketConstants#ESSENCE_CELL_ORDER}）。
     * <p>
     * 三条回落：① 越出格数的条目丢弃；② 重复 tag 只认第一次出现的位置（后续条目忽略）；
     * ③ ★<b>有货但无格位</b>的 tag（旧档／外来档）按 {@code amounts} 的插入序补占最小空位
     * ——插入序就是首次入账序，所以这条回落给出的正是需求要的那个格序，不是随机序。
     */
    private void readCellOrder(NBTTagCompound root) {
        if (root.hasKey(PocketConstants.ESSENCE_CELL_ORDER)) {
            final NBTTagList order = root.getTagList(PocketConstants.ESSENCE_CELL_ORDER, TAG_STRING);
            for (int cell = 0; cell < cellTags.length && cell < order.tagCount(); cell++) {
                final String tag = order.getStringTagAt(cell);
                if (tag == null || tag.isEmpty()) {
                    continue;
                }
                if (cellOf(tag) >= 0) {
                    continue;
                }
                cellTags[cell] = tag;
            }
        }
        for (String tag : amounts.keySet()) {
            if (cellOf(tag) < 0) {
                assignCell(tag);
            }
        }
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
        writeCellOrder(root);
    }

    /**
     * 写格位归属：定长写到最后<b>一个</b>非空格为止（尾部空位由读侧默认补 {@code null}），
     * 完全没有任何格位时 {@code removeTag}（与"空区不留壳"同口径）。
     * <p>
     * ★空位写<b>空串占位</b>而不是跳过——跳过会让后面所有 tag 的格号整体前移，
     * 那正是"重开一次就换一套格序"的形态。
     */
    private void writeCellOrder(NBTTagCompound root) {
        int lastFilled = -1;
        for (int cell = cellTags.length - 1; cell >= 0; cell--) {
            if (cellTags[cell] != null) {
                lastFilled = cell;
                break;
            }
        }
        if (lastFilled < 0) {
            root.removeTag(PocketConstants.ESSENCE_CELL_ORDER);
            return;
        }
        final NBTTagList order = new NBTTagList();
        for (int cell = 0; cell <= lastFilled; cell++) {
            order.appendTag(new NBTTagString(cellTags[cell] == null ? "" : cellTags[cell]));
        }
        root.setTag(PocketConstants.ESSENCE_CELL_ORDER, order);
    }

    /** 某 tag 当前点数，缺席为 0。 */
    public int get(String tag) {
        final Integer value = tag == null ? null : amounts.get(tag);
        return value == null ? 0 : value;
    }

    // ------------------------------------------------------------------ R78③ 格位归属（首次入账序 = 格序）

    /**
     * 该 tag 当前占用的格号。
     *
     * @return {@code 0…ESSENCE_DISPLAY_GRID−1}；从未占格（或入过账但格位已满）返回 −1
     */
    public int cellOf(String tag) {
        if (tag == null || tag.isEmpty()) {
            return -1;
        }
        for (int cell = 0; cell < cellTags.length; cell++) {
            if (tag.equals(cellTags[cell])) {
                return cell;
            }
        }
        return -1;
    }

    /**
     * 第 {@code cell} 格归属的 tag。
     *
     * @return tag 字符串；空格、越界格一律 {@code null}（显示侧据此<b>不画内容</b>）
     */
    public String tagAtCell(int cell) {
        return cell < 0 || cell >= cellTags.length ? null : cellTags[cell];
    }

    /**
     * ★占格（幂等）：已占过就返回<b>原来那一格</b>，否则占<b>最小空位</b>。
     * <p>
     * 只在"真的进了点数"之后调用（见 {@link #add(String, int)}），所以"格序 = 首次入账顺序"
     * 与"这格有没有货"是两件事：前者由本方法一次性钉死并持久化，后者按 {@link #get(String)} 现读。
     *
     * @return 该 tag 的格号；{@code −1} = 72 格已满（只存不显，走 {@code aspect.overflow_note}）
     */
    public int assignCell(String tag) {
        if (tag == null || tag.isEmpty()) {
            return -1;
        }
        final int existing = cellOf(tag);
        if (existing >= 0) {
            return existing;
        }
        for (int cell = 0; cell < cellTags.length; cell++) {
            if (cellTags[cell] == null) {
                cellTags[cell] = tag;
                return cell;
            }
        }
        return -1;
    }

    /** 已占格的数量（含"曾经占过、现在空着"的格；显示侧用它判"还有没有空格可给新 tag"）。 */
    public int assignedCellCount() {
        int total = 0;
        for (String tag : cellTags) {
            if (tag != null) {
                total++;
            }
        }
        return total;
    }

    /**
     * 有货但<b>没有格位</b>的 tag 数（R26 的"只存不显"在 R78 下的新判据：不再是"注册数 &gt; 格数"，
     * 而是"入账过的 tag 比格位多"）。显示侧的 {@code aspect.overflow_note} 由它驱动。
     */
    public int unplacedTagCount() {
        int total = 0;
        for (String tag : amounts.keySet()) {
            if (cellOf(tag) < 0) {
                total++;
            }
        }
        return total;
    }

    /** 表里是否已有该 tag 的点数（>0 才算"含有"）。 */
    public boolean has(String tag) {
        return get(tag) > 0;
    }

    /** 该 tag 还能收多少点。 */
    public int roomFor(String tag) {
        return PocketConstants.ESSENCE_CAP_PER_TAG - get(tag);
    }

    /** 该 tag 是否已到 {@link PocketConstants#ESSENCE_CAP_PER_TAG} 上限。 */
    public boolean isFull(String tag) {
        return get(tag) >= PocketConstants.ESSENCE_CAP_PER_TAG;
    }

    /**
     * 表内已登记的 tag 是否全部到顶。<b>只供 GUI 置灰</b>（"看上去满了"），
     * 不代表"收不进"。
     * <p>
     * ⚠ <b>禁止用它参与"本轮要不要消耗物品"的判定</b>：存储与 72 格显示解耦（R78②），未登记的 tag
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
     * 一份候选能否<b>整体</b>入账（全有全无的"预检"段）。
     * <p>
     * 候选里任一 tag 的 {@code current + amount} 超过单格上限即这份候选判 false；未登记的 tag
     * 视为从 0 起算（仍能收 {@code ESSENCE_CAP_PER_TAG} 点）。非正数条目天然"放得下"（不占空间），
     * 空候选恒为 true。
     * <p>
     * 注入支（一次一份容器）用它；蒸馏侧一轮要逐组装箱，用 {@link #canAcceptAll(Map, Map)}
     * 把已许诺给前面组的量一起算上。
     *
     * @param candidates tag → 应得点数；null/空视为无候选
     * @return true 表示整份候选都能入账，调用方随后可 {@link #putAll(Map)} 提交并据此消耗物品
     */
    public boolean canAcceptAll(Map<String, Integer> candidates) {
        return canAcceptAll(candidates, null);
    }

    /**
     * 同上，但把<b>本轮已排给其它格的预留量</b>一起算进占用（★R84：一轮里<b>逐格</b>装箱，
     * 后装的格不能把已经许给前一格的空间再许一遍；旧口径"逐组装箱 / 前一组"中的"组"已作废）。
     * <p>
     * ★预留量只加在判据上，<b>不改</b>库存：真正的入账仍是那一次 {@link #putAll(Map)}。
     *
     * @param reserved tag → 本轮已许诺的点数；null/空 = 无预留（退化成 {@link #canAcceptAll(Map)}）
     */
    public boolean canAcceptAll(Map<String, Integer> candidates, Map<String, Integer> reserved) {
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Integer> candidate : candidates.entrySet()) {
            final String tag = candidate.getKey();
            final Integer amount = candidate.getValue();
            if (tag == null || tag.isEmpty() || amount == null || amount <= 0) {
                continue;
            }
            final int promised = reserved == null ? 0 : intOf(reserved.get(tag));
            if (get(tag) + promised + amount > PocketConstants.ESSENCE_CAP_PER_TAG) {
                return false;
            }
        }
        return true;
    }

    /**
     * 这份候选里是否存在<b>单格永远装不下</b>的条目（某 tag 的点数本身 &gt; 单格上限）。
     * <p>
     * 存在的理由（R83 偏差 3c）：这类条目与"格内已有存量把空间挤掉了"是两件事——后者玩家取走晶就解卡，
     * 前者<b>无论怎么腾都放不下</b>（TC 的 {@code getBonusTags} 在 {@code capAspects} 之后继续累加 ⇒
     * 护甲/工具/武器类可越 {@link PocketConstants#ESSENCE_CAP_PER_TAG}）。把它单独判出来，上层才能
     * "放弃这<b>一格</b>并留下读数"（★R84：单位是格，旧句"放弃这一组"作废），而不是拿
     * {@link #canAcceptAll(Map)} 的一个 false 把整轮永久冻住。
     *
     * @return true = 至少一个条目单独就超上限
     */
    public boolean exceedsCellCap(Map<String, Integer> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (final Integer amount : candidates.values()) {
            if (amount != null && amount > PocketConstants.ESSENCE_CAP_PER_TAG) {
                return true;
            }
        }
        return false;
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
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
     * 单 tag 入账原语，按 {@link PocketConstants#ESSENCE_CAP_PER_TAG} 上限截断。
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
        // ★R78③：首次真的入账才占格（占格动作与"进了多少点"无关，只与"进没进"有关）；
        // 已占过格的 tag 走 assignCell 的幂等分支，落回原来那一格
        assignCell(tag);
        return added;
    }

    /**
     * 出账；扣到 0 时把该 tag 从表里摘掉（与"0 值不落档"同构）。
     * <p>
     * ★<b>不动格位归属</b>（R78③ 的用户裁定"撤空不回收"）：摘掉的是<b>点数条目</b>，
     * {@code cellTags} 里那一格仍然属于这个 tag，显示侧据 {@link #get(String)} 归 0 撤掉图标与
     * 文本（R73② 的"留格不画内容"），再灌回来还是原来那一格。回收会造成"一次灌入全部重排"，
     * 而且会让按<b>格号</b>索引的 ghost 声明指向别的 tag。
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

    /**
     * 整表重置：点数与<b>格位归属一起清</b>。
     * <p>
     * ★这与 {@link #extract(String, int)} 不是一回事：extract 是"玩家拿走这一格的货"，
     * 按 R78③ 必须<b>保留</b>格位；clear 是"这口袋的源质整表作废"（外部导入/重置一类操作），
     * 保留一套指向空气的格位没有意义。当前生产代码零调用方（保留给"整表导入/导出"一类外部操作）。
     */
    public void clear() {
        amounts.clear();
        java.util.Arrays.fill(cellTags, null);
    }
}
