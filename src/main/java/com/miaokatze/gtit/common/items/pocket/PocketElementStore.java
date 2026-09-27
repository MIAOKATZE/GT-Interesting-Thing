package com.miaokatze.gtit.common.items.pocket;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

/**
 * ★R96 S9a：口袋的<b>元素容量</b>载体 —— {@code primal tag → int} 的窄表，落在根层独立 compound
 * {@link PocketConstants#ELEMENTS} 里（R96 P-7：6 条 tag、各 {@link PocketConstants#ELEMENT_CAP_PER_TAG} 上限）。
 *
 * <h2>★本类是「活档视图」，不是快照缓存（这一条决定了全部接线形状）</h2>
 * 实例只持<b>载体栈 NBT 根的引用</b>，每次 {@link #get(String)} 现读、每次 {@link #add(String, int)} 现写。
 * 这不是偷懒复用 {@code NBTTagCompound}，而是刻意让"元素容量"这一族<b>只剩一个写者</b>：
 * <ul>
 * <li>{@code PocketEssenceStore} 那类快照形状在 {@code PocketInventory} 里持有一份内存表，
 * 面板关屏时整份回写 —— 源质表的写者全在会话可达面内，所以那份缓存不会被人绕过；</li>
 * <li>元素容量<b>不</b>是这样：{@code mage/} 下三条被动由 {@code Item.onUpdate} 驱动，
 * 面板开着、关着都要写它（猫猫币 1 秒 1 枚不会因为玩家挂着界面就停）。若本类也做成快照，
 * 就会同时存在"会话里那份"与"NBT 里那份"两把真相，而面板关屏那一次整表回写会把期间被动
 * 刚加进去的容量<b>抹回去</b> —— 币已扣、容量回到旧值 = 玩家凭空损失，正是 R96 计划 §7.1
 * 那一族「绿≠有效 / 双轨旁路」的复发形状。做成活档视图后，"两把真相"在结构上不可表示。</li>
 * </ul>
 * 代价如实记：一次读 = 一次 {@code getInteger}（纳秒级，无分配），一次入账 = 一次 compound 触碰；
 * 三条被动都只在<b>到拍的那一 tick</b> 动手（节拍见 {@link #tickDue}），所以序列化上界 = 节拍率
 * （每 5 tick / 每 20 tick 各一次），不是每 tick（R53c）。
 *
 * <h2>★不建 long/int 双轨</h2>
 * 单 tag 上限 500、总量 {@link PocketConstants#ELEMENT_TOTAL_CAP}=3000，int 恒不越界；
 * 流体那条 {@code AmountL} 双轨的根因（{@code FluidStack.amount} 是 int 而真值要冲到 16G）在这里
 * <b>结构性不存在</b> ⇒ 加第二轨只会多出一个可以对不齐的地方（R96 P-1 的同一理由，
 * 与 {@code PocketUpgradeSwitches} 类注释里"5 个布尔量不建双轨"同源）。
 *
 * <h2>★6 条 tag 绝不写在栈根</h2>
 * 栈根已经住着 {@link PocketConstants#ESSENCE}（源质）与 {@code essCellOrder} 一族，条目里同样
 * 以 aspect tag 为标识；把 {@code aer/terra/…} 平铺到根层会让"源质点数"和"元素容量"共用一套键名空间
 * （R96 计划 §5 S9 禁止项）。本类的所有取值都只经 {@link PocketConstants#PRIMAL_TAGS} 白名单
 * <b>点查</b> compound 内的 6 个键，<b>从不</b>扫全条目 ⇒ 同 compound 内的四条节拍键与一枚模式位图
 * （{@link PocketConstants#ELEMENT_TICK_WAND} 等）与 tag 键天然不可能混淆。
 *
 * <h2>入账口径 = 整笔预检（R96 S9a 验收 4）</h2>
 * 任何"要消耗别人（币 / 源质 / 要素球 / 基座节点）"的入账都必须先过 {@link #canAcceptAll(Map)}，
 * 判 false 就<b>一分不吃、一件不扣</b>；不允许拿 {@link #add(String, int)} 的截断结果去决定消耗量。
 * 理由与 {@code PocketEssenceStore} 那条 R45c/FIX-6 完全同构：吃一半 + 对方消失 = 静默销毁玩家价值。
 * 基座与要素球的入账（R96 S10 的 mixin 腿）接的也是<b>同一对</b>原语，不得另建第二套判据。
 *
 * <h2>读路径纪律（R53c）</h2>
 * {@code root == null}（从未写过档的口袋）或 {@link PocketConstants#ELEMENTS} 缺键时，一切读数按 0 给，
 * 且<b>不建档</b>；只有真的写东西（入账 / 装节拍）才把 compound 建出来。
 * 0 值条目不落档、整表空则摘掉 {@code elem} 键（与"空区不留壳"同口径）。
 */
public final class PocketElementStore {

    /** 载体栈的 NBT 根（活引用；{@code null} = 无档 ⇒ 一切写都是 no-op，读一律 0）。 */
    private final NBTTagCompound root;

    private PocketElementStore(NBTTagCompound root) {
        this.root = root;
    }

    /**
     * 接到一枚载体根上（<b>唯一</b>构造口）。不复制、不快照：返回的实例与调用方共享同一份 NBT，
     * 因此"谁在什么时候建的这个实例"不影响读数 —— 这是上面「活档视图」那一节的直接兑现。
     */
    public static PocketElementStore attach(NBTTagCompound root) {
        return new PocketElementStore(root);
    }

    /**
     * 接线形状用的别名（{@code PocketInventory#readFrom} 与 {@code PocketEssenceStore.readFrom} 同形调用）。
     * <p>
     * ★刻意<b>不</b>做成"读出 6 个值存进字段"的快照：见类注释的两把真理论证。名字保留 {@code readFrom}
     * 只为了让装配侧的调用形状与源质表逐字对齐，不额外发明一套接线词汇。
     */
    public static PocketElementStore readFrom(NBTTagCompound root) {
        return attach(root);
    }

    /** 该 tag 是否在容量表的白名单内（非白名单一律不收、不存 —— 存储层自己就挡一次）。 */
    public static boolean isTrackedTag(String tag) {
        return PocketConstants.isPrimalTag(tag);
    }

    /** 单 tag 上限（唯一读点；本类所有钳制都经它，别处不得再抄 500）。 */
    public static int capPerTag() {
        return PocketConstants.ELEMENT_CAP_PER_TAG;
    }

    // ------------------------------------------------------------------ 读（零写入）

    /**
     * 某 tag 当前容量。非白名单 tag、无档、缺键一律 0；档里被手改成负数或越上限的条目在
     * {@link #clamp(int)} 里收口（宁少不炸，与源质表的读档钳制同一姿势）。
     */
    public int get(String tag) {
        if (!isTrackedTag(tag)) {
            return 0;
        }
        final NBTTagCompound elem = raw(false);
        if (elem == null || !elem.hasKey(tag)) {
            return 0;
        }
        return clamp(elem.getInteger(tag));
    }

    /** 该 tag 还能收多少点（<b>整笔预检的唯一余量读数</b>，与 {@link #canAcceptAll(Map)} 同一口径）。 */
    public int roomFor(String tag) {
        if (!isTrackedTag(tag)) {
            return 0;
        }
        return capPerTag() - get(tag);
    }

    /** 该 tag 是否已到顶（非白名单 tag 恒 true —— 它本来就收不进任何东西）。 */
    public boolean isFull(String tag) {
        return !isTrackedTag(tag) || get(tag) >= capPerTag();
    }

    /** 六条 tag 的合计（面板总量读数、{@link PocketConstants#ELEMENT_TOTAL_CAP} 的对照面）。 */
    public int total() {
        int sum = 0;
        for (String tag : PocketConstants.PRIMAL_TAGS) {
            sum += get(tag);
        }
        return sum;
    }

    /** 整表是否一条都没有（判"该不该摘掉 {@code elem} 键"用）。 */
    public boolean isEmpty() {
        return total() <= 0;
    }

    /** 白名单序的不可变快照（面板/tooltip 显示用，★不是入账依据 —— 入账只认现读）。 */
    public Map<String, Integer> snapshot() {
        final Map<String, Integer> out = new LinkedHashMap<>();
        for (String tag : PocketConstants.PRIMAL_TAGS) {
            final int amount = get(tag);
            if (amount > 0) {
                out.put(tag, amount);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    // ------------------------------------------------------------------ 写

    /**
     * 整笔预检：这份候选（{@code tag → 点数}）能否<b>全部</b>放得下。
     * <p>
     * ★消耗任何外部资源（猫猫币、源质点、要素球、基座节点）之前都必须先问它；判 false 时调用方
     * <b>一分不吃、一件不扣</b>。非白名单条目直接判 false（不是"跳过"——跳过等于半收，正是禁令形状）。
     * 空候选恒 true（无事发生）。
     */
    public boolean canAcceptAll(Map<String, Integer> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Integer> entry : candidates.entrySet()) {
            final String tag = entry.getKey();
            final Integer amount = entry.getValue();
            if (amount == null || amount <= 0) {
                continue;
            }
            if (!isTrackedTag(tag) || amount > roomFor(tag)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 提交一份候选（须与 {@link #canAcceptAll(Map)} 成对使用）。
     * <p>
     * 未预检直接调用时按上限逐条兜底截断（宁少不炸），但<b>消耗判据必须来自预检</b>；
     * 白名单外的条目一分不收。
     *
     * @return 实际入账总点数
     */
    public int putAll(Map<String, Integer> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int accepted = 0;
        for (Map.Entry<String, Integer> entry : candidates.entrySet()) {
            final Integer amount = entry.getValue();
            accepted += add(entry.getKey(), amount == null ? 0 : amount);
        }
        return accepted;
    }

    /**
     * 单 tag 入账原语（钳到 {@link PocketConstants#ELEMENT_CAP_PER_TAG}；0 值与非白名单都不动档）。
     *
     * @return 实际进了多少点（0 = 一格未进）
     */
    public int add(String tag, int amount) {
        if (!isTrackedTag(tag) || amount <= 0) {
            return 0;
        }
        final NBTTagCompound elem = raw(true);
        if (elem == null) {
            // 无根可写（从未建过档的口袋）⇒ 一分不进。建空档的责任在调用方（R53c 读路径纪律）。
            return 0;
        }
        final int current = clamp(elem.hasKey(tag) ? elem.getInteger(tag) : 0);
        final int added = Math.min(amount, capPerTag() - current);
        if (added <= 0) {
            return 0;
        }
        elem.setInteger(tag, current + added);
        return added;
    }

    /**
     * 单 tag 出账（扣到 0 即摘键）。给"缓慢充法杖"这类<b>掏自己</b>的腿用，
     * 调用方必须按"对方实际吃了多少"传量，不得先掏再灌（掏了灌不进去 = 凭空损失）。
     *
     * @return 实际取出点数（不足按现存给；0 = 未动）
     */
    public int extract(String tag, int amount) {
        final NBTTagCompound elem = raw(false);
        if (elem == null || !isTrackedTag(tag) || amount <= 0 || !elem.hasKey(tag)) {
            return 0;
        }
        final int current = clamp(elem.getInteger(tag));
        if (current <= 0) {
            return 0;
        }
        final int taken = Math.min(amount, current);
        final int left = current - taken;
        if (left <= 0) {
            elem.removeTag(tag);
            pruneIfEmpty();
        } else {
            elem.setInteger(tag, left);
        }
        return taken;
    }

    /**
     * ★R96 S9b：<b>消耗</b>侧的整笔预检 —— 这份账单（{@code tag → 要掏多少点}）能不能<b>全部</b>付得起。
     * <p>
     * ★与 {@link #canAcceptAll(Map)} 是<b>两条不同的判据</b>，不是同一个数换个问法：
     * 本方法问 {@link #get(String)}（<b>存量</b>），那条问 {@link #roomFor(String)}（<b>余量</b>）；
     * 两者在 500 上限下互为补数，⇒ <b>拿错一条不是"稍严/稍宽"，是方向反了</b>
     * （空口袋的余量恒等于上限 ⇒ 用余量判"付得起吗"会把一只一分没有的口袋判成"随便付"）。
     * 这Exactly是 R96 S10 报告 U-8 那一格的根因，S9b 把它收在原语层而不是收在调用方。
     * <p>
     * 非白名单条目判 {@code false} 而不是跳过（跳过 = 半收，与 {@link #canAcceptAll} 同一条禁令）；
     * 空账单恒 {@code true}。
     */
    public boolean canPayAll(Map<String, Integer> bill) {
        if (bill == null || bill.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Integer> entry : bill.entrySet()) {
            final Integer amount = entry.getValue();
            if (amount == null || amount <= 0) {
                continue;
            }
            if (!isTrackedTag(entry.getKey()) || amount > get(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    /**
     * ★R96 S9b：<b>全有全无</b>的出账（护盾回充那条腿要的正是这一枚，★不是逐条 {@link #extract}）。
     * <p>
     * 两道闸：① 先 {@link #canPayAll(Map)}，付不起 ⇒ 一分不动、返 {@code 0}；
     * ② 逐条 {@code extract} 之后<b>实掏数 ≠ 应掏数</b> ⇒ 把已经掏出去的<b>原样补回</b>并返 {@code 0}。
     * 第②道在默认配置下不可达（同一 tick、同一份活档判据），★但它不是"理论保证"而是"代码保证"：
     * {@code shieldCost} 被配置调大、或有别人的 mixin 在两步之间改了容量时，兜住的就是这一道。
     * 旧实现（S10 的 {@code PocketVisSupport}）只有第①道的<b>问错版本</b>且完全没有第②道。
     * <p>
     * ★回滚只用 {@link #add(String, int)}（它会钳到上限）：回滚发生在同一 tick 的同一份账上，
     * 刚掏多少就有多少个空位，钳制不会被触发；真触发了说明有别的东西在这两步之间进了账，
     * 那时"少补一点"也比"整笔按已付处理"安全（后者会让玩家白亏一半账单）。
     *
     * @return 实际付出去的点数（{@code 0} = 整笔未付，档面逐字节不变）
     */
    public int payAll(Map<String, Integer> bill) {
        if (bill == null || bill.isEmpty() || !canPayAll(bill)) {
            return 0;
        }
        int want = 0;
        for (Integer amount : bill.values()) {
            want += amount == null || amount.intValue() <= 0 ? 0 : amount.intValue();
        }
        if (want <= 0) {
            return 0;
        }
        int paid = 0;
        final Map<String, Integer> takenPerTag = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : bill.entrySet()) {
            final Integer amount = entry.getValue();
            final int taken = extract(entry.getKey(), amount == null ? 0 : amount.intValue());
            if (taken > 0) {
                takenPerTag.put(entry.getKey(), Integer.valueOf(taken));
                paid += taken;
            }
        }
        if (paid == want) {
            return paid;
        }
        // 数不上 ⇒ <b>按每条实际掏走多少</b>原样补回（★不是"总共差多少补到某一条上"——
        // 摊到一条上会造出 TC 侧看不见的错账：aer 少的那两点被补进 terra，玩家读到的是"容量莫名其妙多了"）
        for (Map.Entry<String, Integer> entry : takenPerTag.entrySet()) {
            add(
                entry.getKey(),
                entry.getValue()
                    .intValue());
        }
        return 0;
    }

    /**
     * 把本表（连同四条节拍键与 ★R96 S9b 的模式位图）落到<b>目标根</b>——{@code PocketInventory#writeTo} 的接线形状。
     * <p>
     * ★目标与实例所接的根<b>是同一个对象</b>时本方法是幂等 no-op（值本来就现读现写），
     * 这正是"活档视图"允许它出现在整表回写路径里的原因：它不可能用陈旧副本盖掉新值。
     * 目标不同（面板按 {@code open} 位重定位到另一枚栈）时才是真正的一次搬运。
     * 空表在目标上也是空（{@code removeTag}），不留空壳。
     */
    public void writeTo(NBTTagCompound target) {
        if (target == null) {
            return;
        }
        final Map<String, Integer> values = snapshot();
        final Map<String, Integer> ticks = tickSnapshot();
        final boolean hasModes = hasModeKey();
        if (values.isEmpty() && ticks.isEmpty() && !hasModes) {
            if (target != root) {
                target.removeTag(PocketConstants.ELEMENTS);
            }
            return;
        }
        if (target == root) {
            // 同一份档：值与节拍都已经现写到位，重抄一遍只会多摸一次 NBT，不做。
            return;
        }
        final NBTTagCompound elem = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            elem.setInteger(
                entry.getKey(),
                entry.getValue()
                    .intValue());
        }
        for (Map.Entry<String, Integer> entry : ticks.entrySet()) {
            elem.setInteger(
                entry.getKey(),
                entry.getValue()
                    .intValue());
        }
        // ★R96 S9b：模式位图必须跟着搬，且★按 byte 搬（与 {@link #setMode} 的写出形状同型）。
        // 漏这一趟的后果不是"少一个键"而是"面板关屏那一次整表回写把玩家刚配的模式抹回缺省"——
        // modes 与 tag 同住 elem，而上面建的是<b>新</b> compound。
        if (hasModes) {
            elem.setByte(PocketConstants.ELEMENT_MODES, (byte) modeMask());
        }
        target.setTag(PocketConstants.ELEMENTS, elem);
    }

    // ------------------------------------------------------------------ 节拍（不变量 G8：NBT 剩余 tick）

    /**
     * 问一句"这一拍到了吗"，并把倒计时推进一格。三条被动各自持有自己的键，互不牵制。
     * <ol>
     * <li>缺键 = <b>不在节拍上</b> ⇒ 返 {@code true}（到点），且<b>零写入</b>（读路径不建档，R53c）；</li>
     * <li>剩余 &gt; 1 ⇒ 写回 {@code left - 1}（一次 int 写）并返 {@code false}；</li>
     * <li>剩余 == 1 ⇒ 摘键并返 {@code true}（不留 0 值壳，口径同 {@code ItemNekoDimensionPocket#tickDown}）。</li>
     * </ol>
     * ★<b>不</b>用 {@code entity.ticksExisted % n}（不变量 G8 与 R59e：跨维重建实体 tick 不连续 ⇒
     * 节拍会漂；磁力那条是既存例外，单独记账，不得扩散到新代码），也不用世界绝对时刻。
     * 装填由 {@link #armTick(String, int)} 在<b>真的搬动过东西</b>之后做 —— 无事可做的口袋既不写档
     * 也不占节拍，"关掉开关 ⇒ 零 NBT 写"与"开着但包里没币 ⇒ 零 NBT 写"两条因此同时成立。
     *
     * @param key {@link PocketConstants#ELEMENT_TICK_WAND} 一族的某个节拍键
     */
    public boolean tickDue(String key) {
        if (key == null || key.isEmpty()) {
            return true;
        }
        final NBTTagCompound elem = raw(false);
        if (elem == null || !elem.hasKey(key)) {
            return true;
        }
        final int left = elem.getInteger(key);
        if (left > 1) {
            elem.setInteger(key, left - 1);
            return false;
        }
        elem.removeTag(key);
        pruneIfEmpty();
        return true;
    }

    /**
     * 装下一拍的倒计时（{@code period <= 0} 视作"不装填"⇒ 下一 tick 仍是到点态，
     * 这是给"节拍常量被手改成 0"留的降级腿，不是给调用方省一次调用的口子）。
     */
    public void armTick(String key, int period) {
        if (key == null || key.isEmpty() || period <= 0) {
            return;
        }
        final NBTTagCompound elem = raw(true);
        if (elem != null) {
            elem.setInteger(key, period);
        }
    }

    /** 节拍键的当前剩余 tick（缺键 0；面板若将来要显示"还剩几秒"读它，不做第二份缓存）。 */
    public int tickLeft(String key) {
        final NBTTagCompound elem = raw(false);
        return elem == null || key == null || !elem.hasKey(key) ? 0 : Math.max(0, elem.getInteger(key));
    }

    /** 四条节拍键的<b>携带侧</b>表（★不是 tag：{@link #get(String)} 一族只认白名单 tag）。 */
    private static final String[] STATE_KEYS = { PocketConstants.ELEMENT_TICK_WAND, PocketConstants.ELEMENT_TICK_COIN,
        PocketConstants.ELEMENT_TICK_TRANSMUTE, PocketConstants.ELEMENT_TICK_CRYSTAL };

    private Map<String, Integer> tickSnapshot() {
        final Map<String, Integer> out = new LinkedHashMap<>();
        final NBTTagCompound elem = raw(false);
        if (elem == null) {
            return out;
        }
        for (String key : STATE_KEYS) {
            if (elem.hasKey(key)) {
                out.put(key, elem.getInteger(key));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ ★R96 S9b 模式位图（同 compound、另一族键）

    /**
     * 模式位图的<b>读</b>腿（只读、不建档，R53c）：缺键 ⇒ {@link PocketConstants#MAGE_MODES_DEFAULT}。
     * <p>
     * ★本方法是"缺省怎么读"的<b>唯一</b>落点：{@code PocketMageModes} 的三个谓词与写腿的"回到缺省即摘键"
     * 都经它，别处不得再抄一遍 {@code MAGE_MODES_DEFAULT}（抄第二处 = 读写两侧的缺省可以对不齐）。
     */
    public int modeMask() {
        final NBTTagCompound elem = raw(false);
        if (elem == null || !elem.hasKey(PocketConstants.ELEMENT_MODES)) {
            return PocketConstants.MAGE_MODES_DEFAULT;
        }
        return elem.getByte(PocketConstants.ELEMENT_MODES) & 0xFF;
    }

    /** 某个模式位当前是否开（{@code bit} 必须是 {@link PocketConstants#MAGE_MODE_BITS} 里的一枚，否则 false）。 */
    public boolean modeOn(int bit) {
        return PocketConstants.isMageModeBit(bit) && (modeMask() & bit) != 0;
    }

    /**
     * 模式位的<b>唯一写</b>腿（同值 ⇒ {@code false} 且零写入，形状照
     * {@code PocketUpgradeSwitches#setOff}：连点同一模式不会刷出一串整栈同步包）。
     * <p>
     * 写完若掩码回到缺省就 {@code removeTag}（缺键与缺省同义，少一个键少一份 NBT 深比较的体积；
     * ★也顺带保证"从来没动过模式的档"在本方法被误调一次之后仍然是<b>零新增键</b>）。
     *
     * @return 本次是否真的改变了档
     */
    public boolean setMode(int bit, boolean on) {
        if (!PocketConstants.isMageModeBit(bit)) {
            return false;
        }
        final int current = modeMask();
        final int next = on ? (current | bit) : (current & ~bit);
        if (next == current) {
            return false;
        }
        final NBTTagCompound elem = raw(true);
        if (elem == null) {
            // 无根可写：建档责任在调用方（口径同 {@link #add(String, int)}）
            return false;
        }
        if (next == PocketConstants.MAGE_MODES_DEFAULT) {
            elem.removeTag(PocketConstants.ELEMENT_MODES);
        } else {
            elem.setByte(PocketConstants.ELEMENT_MODES, (byte) next);
        }
        pruneIfEmpty();
        return true;
    }

    // ------------------------------------------------------------------ 内部

    /** 只给 {@link #get(String)} 与写路径用：负数归 0、越上限归上限（外来/手改档不吃亏也不占便宜）。 */
    private static int clamp(int value) {
        if (value <= 0) {
            return 0;
        }
        return Math.min(value, capPerTag());
    }

    /**
     * 取 {@link PocketConstants#ELEMENTS} compound。
     *
     * @param create {@code false} = 纯读（缺档返 {@code null}，绝不建档）；{@code true} = 写路径，
     *               必要时把 compound 建出来（{@code root == null} 时仍返 {@code null} ⇒ 写落空）
     */
    private NBTTagCompound raw(boolean create) {
        if (root == null) {
            return null;
        }
        if (!root.hasKey(PocketConstants.ELEMENTS)) {
            if (!create) {
                return null;
            }
            root.setTag(PocketConstants.ELEMENTS, new NBTTagCompound());
        }
        final Object held = root.getTag(PocketConstants.ELEMENTS);
        if (held instanceof NBTTagCompound) {
            return (NBTTagCompound) held;
        }
        // 外来/手改档把 elem 写成了别的类型：整族按空处理并在写路径上换回正确形状（不让脏键永久卡死）。
        if (!create) {
            return null;
        }
        final NBTTagCompound fresh = new NBTTagCompound();
        root.setTag(PocketConstants.ELEMENTS, fresh);
        return fresh;
    }

    /** 档里是否真的存在 {@link PocketConstants#ELEMENT_MODES} 键（★"缺省"与"缺键"必须分得开，见 writeTo）。 */
    private boolean hasModeKey() {
        final NBTTagCompound elem = raw(false);
        return elem != null && elem.hasKey(PocketConstants.ELEMENT_MODES);
    }

    /** 整族空（6 条 tag、4 个节拍键与模式位图都不在）⇒ 摘掉 {@code elem} 键，与"空区不留壳"同口径。 */
    private void pruneIfEmpty() {
        final NBTTagCompound elem = raw(false);
        if (elem != null && elem.hasNoTags()) {
            root.removeTag(PocketConstants.ELEMENTS);
        }
    }
}
