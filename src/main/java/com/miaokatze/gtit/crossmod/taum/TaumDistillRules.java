package com.miaokatze.gtit.crossmod.taum;

import com.miaokatze.gtit.common.items.pocket.PocketInventory;

/**
 * 蒸馏入账与容器装箱的<b>纯判定逻辑</b>（零 MC / 零 TC 依赖，可被
 * {@code src/test/java/com/miaokatze/gtit/crossmod/taum} 下的零依赖套件直接喂桩件断言）。
 * <p>
 * 本类<b>不持有存储</b>：口袋的 {@code tag → 数量} 表由 {@code PocketEssenceStore} 维护（单一真相），
 * 这里只做「给定的产出能不能整轮入账、入账多少、本轮要不要消耗物品」的换算，
 * 输入数组一律不被修改，结果以 delta 返回。
 * <p>
 * <b>口径只有一条，不提供可切换档位</b>：
 * <ul>
 * <li>产出量 = {@code AspectList} <b>原量</b>入账（与 TC 炼金炉 {@code TileAlchemyFurnace.smeltItem}
 * 的全量并入同构，可与炼金炉对账；{@code generateTags} 自带 {@code capAspects(ret,64)}
 * 与"每格 64 点"等值）；★<b>不乘 {@code stackSize}</b>（R83 A2 / D-3 β，理由见
 * {@code PocketDistillDriver} 的类注释：乘堆叠数会撞单格上限 + 全有全无 ⇒ 整堆永久卡死）；</li>
 * <li>溢出 = <b>全有全无，作用单位是"一格物品"</b>（★R84 由 R83 A2 的"一组"再收到"一格"，
 * 用户定档"对象应该是格子，每个格子蒸1件"）：
 * 一格放不下 ⇒ 只作废<b>这一格</b>（零入账、该格一件都不消耗），其它格照常；截断后照扣物品
 * = 静默销毁价值，不可接受。整轮一格都没收下且是被空间挡下时进度停在 100 不重跑。</li>
 * </ul>
 * 曾经的"每 aspect 各 +1 点"与"部分入账"两档已按裁定删除，不留死配置、不留两说并存。
 * <p>
 * ⚠ 本类的 {@link #credit} 是<b>与 {@code PocketEssenceStore} 同口径的另一份换算实现</b>，
 * 生产代码零调用方（蒸馏实际走 {@code PocketDistillDriver} → {@code PocketEssenceStore.putAll}）。
 * 它钉的是"原量入账 + 不许截断照扣 + 放不下就作废"这条算术口径；
 * ★"全有全无的作用单位"这一层它仍是<b>整轮</b>（R83 之前的形状），生产侧已在 R83 A2 收到一组、
 * ★R84 再收到<b>一格</b>，
 * 故本方法<b>不得</b>被当成现行粒度读。删它或把它抬成活码是独立裁决（见 r83-impl-a2.md 的待办）。
 */
public final class TaumDistillRules {

    /**
     * ★<b>这不是现行的每格上限</b>：现行值住在 {@code PocketConstants.ESSENCE_CAP_PER_TAG}（R84 起 = 256）。
     * 本常数只服务本类那条<b>零生产调用方</b>的离线 {@code credit} 换算与 {@code TaumDistillRulesTest}
     * 的字面量桩件，取值仍是 TC4 Warded Jar 的 {@code maxAmount=64}。
     * <p>
     * 两处不同值是<b>有意保留的既存分裂</b>：统一它要连带重写那份测试里 8 条按 64 摆的容量用例，
     * 属独立裁决（与上面"删 credit 或抬成活码"同一件待办）。★不得被读成"256 没生效"。
     */
    public static final int MAX_CELL = 64;

    /**
     * 蒸馏节拍<b>基档</b>：每 100 tick（5 秒）一轮，一轮里<b>每个非空且含源质的格各消耗 1 件</b>
     * （★R84：对象是格不是组；5 秒是节拍不是产量，须向玩家声明）。
     * <p>
     * ★<b>R95 蒸馏加速：本常数不再是独立真值</b>——它由 {@link #distillIntervalTicks(boolean)} 的
     * {@code fast=false} 档<b>派生</b>；100/20 两档字面量<b>只</b>活在那个方法一处（节拍权威单点纪律，
     * 即旧「本类与 GUI 两侧都没有第二个 100」那条纪律的 R95 延伸：档位变多，真值点仍只有一个）。
     */
    public static final int DISTILL_INTERVAL_TICKS = distillIntervalTicks(false);

    /**
     * 蒸馏节拍（加速档的唯一真值点，★R95 建双口径、★R96 S8 定速到 1 秒）：{@code fast=false} 基档
     * 100 tick（5 秒/轮），{@code fast=true} 加速档 20 tick（1 秒/轮，载体固化
     * {@code PocketUpgradeType.MAGE}——★R96 S8 前名为「蒸馏加速」——时启用）。
     * <p>
     * 100/20 两个字面量<b>只</b>出现在本方法体内；一切消费侧（{@code PocketDistillDriver} 的
     * 装填与进度分母、tooltip 的秒数换算）一律经本方法或其派生常量 {@link #DISTILL_INTERVAL_TICKS}，
     * 不得自抄数字。中途安装的行为口径（当前轮旧间隔跑完、下一轮生效）住在消费侧
     * {@code PocketDistillDriver#distillIntervalOf} 的装填点，不在本层。
     * <p>
     * ★20 tick 恰是 {@code PocketConstants.TICKS_PER_SECOND} 的整秒点 ⇒ tooltip 的 {@code %12$d}
     * （{@code PocketConstants.ticksToSecondsCeil} 换算）零改动即读 "1"。
     */
    public static int distillIntervalTicks(boolean fast) {
        return fast ? 20 : 100;
    }

    /**
     * 蒸馏输入格数。★R83 A2 收单源：真值住在<b>被消费的那一侧</b>——
     * {@code PocketInventory#DISTILL_INPUT_SLOTS}（GUI 格网、{@code PocketSlots} 的槽数加总、
     * {@code verify-pocket.sh} 与回归套件读的都是它），本行只是<b>转发</b>，不再自己留一个数字。
     * <p>
     * 旧值 {@code 3}（"需求原文 3 个物品槽"）与实况 12 格各说各话，本轮作废：它的唯一读者就是
     * {@code TaumDistillRulesTest} 的那条字面量断言，留着一个没人用的数字即是两处真相。
     * <p>
     * 本行是编译期常量内联（{@code PocketInventory} 那一行是常量表达式），所以"零 MC / 零 TC 依赖"
     * 这条运行时承诺不受影响：零依赖套件加载本类不会牵进 GUI。
     */
    public static final int DISTILL_INPUT_SLOTS = PocketInventory.DISTILL_INPUT_SLOTS;

    /**
     * 源质瓶（TC {@code ItemEssence}）单瓶点数 = <b>8</b>（{@code ItemEssence.java:109-185}）。
     * <p>
     * ★<b>R88：本行是"源质搬运载体"这一档的仓内唯一真值</b>。口袋侧的取出粒度、通道单位语义与
     * 折算瓶数都从它派生（{@code PocketConstants.ESSENCE_OUT_UNIT_POINTS} 是<b>转发</b>，
     * {@code ESSENCE_OUT_MAX_PHIALS_PER_ACTION} / {@code ESSENCE_MAX_PHIALS_PER_TAG} 是它的商），
     * 所以下面任何一处改动都要同时核对那三条派生式与 {@code TaumBridge#capacityOf} 给瓶的档位。
     */
    public static final int PHIAL_CAPACITY = 8;

    /**
     * 晶化源质：1 点 = 1 个晶（{@code TileEssentiaCrystalizer.java:293}）。
     * <p>
     * ★<b>R88 裁定 C2「退役为只读档位判据」→ ★R96 S9b 按用户裁定改判一半</b>。本常量一直是双身份，
     * 两个身份现在<b>都在役</b>，读的时候别只读一半：
     * <ul>
     * <li><b>识别面（R88 起就在役，一字未改）</b>：{@code TaumBridge#capacityOf} 给晶返它，
     * {@code PocketSlots}/{@code PocketEssenceIntake}/{@code NekoEssenceGhostCell} 拿它做
     * "这是不是旧晶"的分流；旧晶的点数照旧读得回来（不吃件）。★这条判据与 {@code CRYSTAL_STACK_LIMIT}
     * <b>不得</b>删或改值：那会让存量旧晶被判成"非容器/普通物品"，直接踩回 R86 那条"整叠销毁"的危险面。</li>
     * <li><b>生产面（★R96 S9b 改判：从"任何路径都不再产出晶"变成"结晶模式这条路径产出晶"）</b>：
     * 魔法使的「结晶模式」（{@code PocketMageModes#crystalOn}，★默认关）开通后，蒸馏产物<b>不经源质盘</b>、
     * 按<b>本档位</b>直接成晶进玩家背包（1 点/枚、每堆 ≤ {@code MAGE_CRYSTAL_MAX_PER_BATCH} 枚）；
     * 盘内存量原地不动，仍归取瓶与源质转换。
     * ★改判<b>没有</b>碰的两件事：① 晶<b>永不就地排空 / 就地灌入</b>（wiki
     * {@code gtit-taumcraft-essentia-carriers.md} §3 已证死：服务端会随机重赋型），产晶一律是
     * "读数 + 消耗整件 + 出新晶"；② 不开结晶模式时，源质出袋的载体<b>仍然</b>是瓶
     * （{@link #PHIAL_CAPACITY}，一瓶 8 点）——本模式是<b>新增的第三条出口</b>，不是把瓶换回晶。</li>
     * </ul>
     */
    public static final int CRYSTAL_CAPACITY = 1;

    /** 容器容量未知（非 TC 瓶/晶的第三方容器） */
    public static final int CAPACITY_UNKNOWN = -1;

    /** 该 stack 根本不是源质容器 */
    public static final int CAPACITY_NOT_A_CONTAINER = 0;

    private TaumDistillRules() {}

    /**
     * 一轮蒸馏的入账换算（<b>原量 + 全有全无</b>，口径固定）。
     * <p>
     * ⚠ 粒度是<b>整轮</b>（R83 A2 之前的形状）且生产零调用方；现行蒸馏按"一格物品"全有全无（★R84），
     * 本体在 {@code PocketDistillDriver#planDistillBatch}。本方法只承担"原量 + 不许截断照扣"
     * 这条算术口径的机检，见类注释。
     *
     * @param stored    当前每格点数（与 {@code order} 平行；null 视为全 0）
     * @param order     每格对应的 aspect tag（口袋的显示/存储序，来自 {@link TaumCompat#aspectOrder()}）
     * @param distilled 本轮各槽蒸馏出的 aspect 集合（多槽合并后的结果；null/空 ⇒ 本轮无事发生）
     * @param cap       单格上限（正常传 {@link #MAX_CELL}）
     * @return 入账结果，含每格 delta 与「本轮是否消耗物品」
     */
    public static Credit credit(int[] stored, String[] order, TaumAspectAmounts distilled, int cap) {
        if (order == null || order.length == 0 || distilled == null || distilled.isEmpty()) {
            return Credit.none();
        }
        int n = distilled.size();
        int[] from = stored != null && stored.length == order.length ? stored : new int[order.length];

        // 预检 + 换算：tag 不在格表内（超出显示/存储范围的 addon aspect）与放不下的都算「击不中」
        int[] delta = new int[order.length];
        int[] acceptedPerTag = new int[n];
        boolean allFit = true;
        int acceptedTotal = 0;
        int wantedTotal = 0;
        for (int i = 0; i < n; i++) {
            final int want = Math.max(0, distilled.amountAt(i));
            if (want <= 0) {
                // 0 产出条目天然「放得下」，不影响 allFit（TaumAspectAmounts 已过滤 <=0，这里只兜底）
                continue;
            }
            wantedTotal += want;
            final int idx = indexOf(order, distilled.tagAt(i));
            if (idx < 0 || from[idx] + want > cap) {
                allFit = false;
                continue;
            }
            delta[idx] += want;
            acceptedPerTag[i] = want;
            acceptedTotal += want;
        }
        if (!allFit) {
            // 全有全无：整轮零入账、零消耗，进度停在 100 不重跑
            return Credit.rejected(wantedTotal);
        }
        if (acceptedTotal <= 0) {
            return Credit.none();
        }
        return new Credit(delta, acceptedPerTag, acceptedTotal, true, true, wantedTotal - acceptedTotal);
    }

    /**
     * 按 tag 查格序号。
     *
     * @return 命中的下标，未命中 -1
     */
    public static int indexOf(String[] order, String tag) {
        if (order == null || tag == null) {
            return -1;
        }
        for (int i = 0; i < order.length; i++) {
            if (tag.equals(order[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 装箱：装满若干容器需要几个（最后一个装余数）。
     *
     * @param points   待出点数
     * @param capacity 单容器容量（瓶 8、罐未知时由调用方给出其档位）
     * @return 需要的容器数；{@code points<=0} 或 {@code capacity<=0} 时为 0
     */
    public static int containerCountFor(int points, int capacity) {
        if (points <= 0 || capacity <= 0) {
            return 0;
        }
        return (points + capacity - 1) / capacity;
    }

    /**
     * 装箱：单个容器本轮应装多少点（不足一容量按实际点数装）。
     * <p>
     * ★R88 提醒：本方法是<b>余数瓶</b>那一支（末瓶装 4 点），只在"给第三方/罐一类按余数收尾"时才是想要的
     * 形状。口袋侧的<b>取瓶路</b>（面板游标 / Shift 进背包 / 入槽算式）走的是裁定 C1 的<b>整瓶</b>粒度
     * ⇒ 请用 {@link #floorToPhialUnits(int)}，不要把这里当"取多少点"的口径读。
     * ★<b>通道搬运路自 R92-① 起不再走这里</b>（改按 1 点，单源在
     * {@code PocketEssenceChannelOps#channelUnitsForPoints}），只有该通道倍率落不到通道档时的
     * <b>降级档</b>才回落到瓶档。
     *
     * @param remaining 该 aspect 剩余可出点数
     * @param capacity  单容器容量
     * @return 实际装点数，剩余为 0 时为 0
     */
    public static int unitFor(int remaining, int capacity) {
        if (remaining <= 0) {
            return 0;
        }
        return capacity <= 0 ? remaining : Math.min(remaining, capacity);
    }

    /**
     * ★<b>R88 裁定 C1 的唯一实现点：把"想取的点数"向下取整到整瓶，余数留盘</b>。
     * <p>
     * 存在的理由不是省一行算式，而是<b>同一条取瓶路上的各站必须取同一个整</b>：面板取出（游标 / Shift 进
     * 背包）与入槽算式都走这里。任何一处自己写 {@code / 8 * 8}，另一处改了粒度就会出现"扣了 3 点、只出
     * 0 瓶"或"瓶数与点数对不上"的净吞点数。
     * <p>
     * ★<b>R92-① 收窄执法面（不改判 C1）</b>：旧句里的"三条搬运路"包含通道上传与通道下传两条，那两条
     * 自 R92-① 起改按<b>通道档 1 点</b>量化（单源 {@code PocketEssenceChannelOps#channelUnitsForPoints}），
     * 只有该通道倍率落不到通道档时的<b>降级档</b>才回落到本函数所服务的瓶档 ⇒ 本函数现役是
     * <b>取瓶路</b>的口径，不再被读成"源质所有搬运都是整瓶"。
     * <p>
     * 粒度取 {@link #PHIAL_CAPACITY}（现役载体的真实容量），<b>不是</b> {@code PocketConstants} 里
     * 复制一份 8 —— 那条 {@code ESSENCE_OUT_UNIT_POINTS} 是本常量的转发，两者同源。
     *
     * @param points 玩家/声明想要的点数（{@code <= 0} 原样给 0）
     * @return 不超过 {@code points} 的最大 8 的倍数
     */
    public static int floorToPhialUnits(int points) {
        return points <= 0 ? 0 : points - (points % PHIAL_CAPACITY);
    }

    /**
     * 点数 → <b>整瓶</b>只数（C1 的另一半：{@link #floorToPhialUnits(int)} 的商）。
     * <p>
     * 与 {@link #containerCountFor(int, int)} 的区别是刻意的：那条<b>向上</b>取整（末瓶装余数），
     * 服务"这堆点要几只容器装"；本条<b>向下</b>取整，服务"这一次动作实际出几只满瓶"。
     * 两者混用就是把余数瓶与整瓶混在一起 ⇒ 净吞点数。
     */
    public static int phialCountFor(int points) {
        return points <= 0 ? 0 : points / PHIAL_CAPACITY;
    }

    /**
     * TC 的 aspect 图标资源路径（{@code Aspect.java:84-88} 的 image 字段形状）。
     * <p>
     * TC 缺席时该资源域不存在，渲染方须自行回落（染色方块 + 文本）。
     *
     * @param tag aspect tag
     * @return 形如 {@code thaumcraft:textures/aspects/aer.png}；tag 为 null/空时返回 null
     */
    public static String aspectTexturePath(String tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        return "thaumcraft:textures/aspects/" + tag.toLowerCase() + ".png";
    }

    /** 一轮入账的结果（值对象） */
    public static final class Credit {

        /** 每格应加的点数（与调用方的格表平行；未变化的格为 0） */
        public final int[] delta;
        /** 每个蒸馏条目实际入账的点数（与传入 distilled 的条目同序；未入账为 0） */
        public final int[] acceptedPerTag;
        /** 本轮实际入账总点数 */
        public final int acceptedTotal;
        /** 本轮是否消耗输入物品（false ⇒ 进度条与消耗都不动） */
        public final boolean consume;
        /**
         * 整份候选是否都完整放下。
         * <p>
         * 单一口径（全有全无）下它<b>恒等于</b> {@link #consume}：保留两个名字是为了让上层
         * 读代码时清楚"消耗"的依据就是"全放得下"，而不是任何截断结果。
         */
        public final boolean fullyAccepted;
        /** 本轮因放不下而被放弃的点数（全有全无 ⇒ 整份产量，落账成功时为 0） */
        public final int discarded;

        Credit(int[] delta, int[] acceptedPerTag, int acceptedTotal, boolean consume, boolean fullyAccepted,
            int discarded) {
            this.delta = delta;
            this.acceptedPerTag = acceptedPerTag;
            this.acceptedTotal = acceptedTotal;
            this.consume = consume;
            this.fullyAccepted = fullyAccepted;
            this.discarded = discarded;
        }

        /** 无事发生（无 aspect 可入账，不消耗物品） */
        public static Credit none() {
            return new Credit(new int[0], new int[0], 0, false, false, 0);
        }

        /** 整轮作废（有 aspect 放不下 ⇒ 不落账、不消耗，进度停在 100 不重跑） */
        public static Credit rejected(int wouldAccept) {
            return new Credit(new int[0], new int[0], 0, false, false, wouldAccept);
        }

        @Override
        public String toString() {
            return "Credit{accepted=" + acceptedTotal
                + ", consume="
                + consume
                + ", full="
                + fullyAccepted
                + ", discarded="
                + discarded
                + "}";
        }
    }
}
