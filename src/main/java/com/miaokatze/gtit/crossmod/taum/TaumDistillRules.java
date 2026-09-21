package com.miaokatze.gtit.crossmod.taum;

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
 * 与"每格 64 点"等值）；</li>
 * <li>溢出 = <b>全有全无</b>：本轮任一 aspect 放不下 ⇒ 整轮零入账、零消耗（截断后照扣物品
 * = 静默销毁价值，不可接受）；全满时进度停在 100 不重跑。</li>
 * </ul>
 * 曾经的"每 aspect 各 +1 点"与"部分入账"两档已按裁定删除，不留死配置、不留两说并存。
 */
public final class TaumDistillRules {

    /** 单格（单 aspect）点数上限，对应 TC4 Warded Jar 的 {@code maxAmount=64} */
    public static final int MAX_CELL = 64;

    /** 蒸馏节拍：每 100 tick（5 秒）把输入槽各消耗 1 个（<b>5 秒是节拍不是产量</b>，须向玩家声明） */
    public static final int DISTILL_INTERVAL_TICKS = 100;

    /** 蒸馏输入槽数量（需求原文「3 个物品槽」） */
    public static final int DISTILL_INPUT_SLOTS = 3;

    /** 源质瓶（TC {@code ItemEssence}）单次装点数 */
    public static final int PHIAL_CAPACITY = 8;

    /** 晶化源质：1 点 = 1 个晶 */
    public static final int CRYSTAL_CAPACITY = 1;

    /** 容器容量未知（非 TC 瓶/晶的第三方容器） */
    public static final int CAPACITY_UNKNOWN = -1;

    /** 该 stack 根本不是源质容器 */
    public static final int CAPACITY_NOT_A_CONTAINER = 0;

    private TaumDistillRules() {}

    /**
     * 一轮蒸馏的入账换算（<b>原量 + 全有全无</b>，口径固定）。
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
