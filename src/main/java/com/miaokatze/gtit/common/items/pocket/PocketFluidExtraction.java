package com.miaokatze.gtit.common.items.pocket;

/**
 * ★新功能 N（S6，G-C，AUQ-④=b 潜行手势）的<b>决策核纯函数</b>：潜行右击 GT5U 机器抽流体进流体槽。
 * <p>
 * 本类<b>零 Minecraft / GT5U import</b>（只碰 {@code long}/{@code int}/{@code boolean} 与纯数据），
 * {@code NekoPocketModelTest} 可以直接 new 数组驱动；MC/GT5U 的调用（{@code BaseMetaTileEntity.drain}
 * 的模拟读数、口袋 tank 现值读数、灌入执行）全部收在世界站适配器
 * {@code PocketWorldFluidTap} 里。这条缝是计划 §3-S6 的「JVM 可测性接缝」：
 * 落点选择、容量算术、搬运量、回执分类、潜行分支都在这里可直测。
 * <p>
 * <b>GT5U 门禁链事实</b>（参考树 {@code BaseMetaTileEntity.java:1901-1911}，实读）：
 * {@code drain(side, maxDrain, doDrain)} 在
 * {@code mTickTimer > 5 && canAccessData() && (mRunningThroughTick || !mOutputDisabled)}
 * 全部通过时才转发 {@code mMetaTileEntity.drain}，否则返回 {@code null}；{@code UNKNOWN} 面
 * 免朝向检查、免 cover 检查。三条失败里只有「太新」（{@code getTimer()} 公开可读）能从外部
 * 直接分辨；「禁输出」（{@code mOutputDisabled}）<b>无公开读数</b> ⇒ 适配器用「绕过门禁直探 MTE
 * （{@code getMetaTileEntity().drain(UNKNOWN, …, false)}，{@code IMetaTileEntity} 本身是
 * {@code IFluidHandler}）」把「禁输出」与「机器无液」分开（{@link #machineGate} 的
 * {@code ungatedContent} 入参），探不到时退「无液或禁输出」合并口径——宁可并档也不瞎指。
 * <p>
 * <b>搬运契约（计划 §3-S6「不丢量 + 回执如实」）</b>：{@link #plan} 先按口袋侧余量求
 * {@code want = min(机器可抽量, 落点 tank 余量)}，真抽一律以 {@code want} 为上限 ⇒ 口袋必然装得下；
 * 适配器侧若仍出现「真抽 > 实收」（接口自反悔），差额必须原路注回机器（不丢弃），回执按实收报。
 * U7 门槛口径（tier≥4 数字罐 32M 起超单 tank 的未升级档 ⇒ ★R96 S3 后是超 20M；2G 档下同一条
 * 结论仍成立，只是门槛挪到 tier 更高的罐）⇒ {@link #remainingAfter} 供「部分搬运」回执。
 */
public final class PocketFluidExtraction {

    /**
     * GT5U drain 门禁的「太新」阈值：门禁式是 {@code mTickTimer > 5}，即
     * {@code timer <= 5} 一律放行失败（参考树 :1902）。阈值只允许活在这一处。
     */
    public static final long GATE_TICK_THRESHOLD = 5L;

    /** 回执分类（判因）。聊天键映射在适配器，本枚举只承载语义。 */
    public enum Reason {

        /** 门禁开着且模拟量 >0：可以进落点规划（不是回执分类）。 */
        GATE_OPEN,
        /** U5 太新：{@code mTickTimer <= 5}（刚放置/刚加载区块）。 */
        MACHINE_TOO_NEW,
        /** U6 禁输出：门禁量 0 但绕门禁直探有货（输出被螺丝刀禁用 / 门禁其它环节拒绝）。 */
        MACHINE_LOCKED,
        /** 机器无液：门禁量 0 且绕门禁直探也无货。 */
        MACHINE_EMPTY,
        /** 无液或禁输出（合并档）：绕门禁直探不可用（探针异常 / MTE 缺席），不瞎指。 */
        MACHINE_EMPTY_OR_LOCKED,
        /** 落点满或异流体：机器有得抽，但 18 个 tank 没有一个能收这种流体。 */
        POCKET_NO_ROOM
    }

    /** {@link #plan} 的结果：落点 tank 号 + 请求搬运量（{@code reason != GATE_OPEN} 时 tank=-1、want=0）。 */
    public static final class Plan {

        /** 分类（GATE_OPEN 之外的值都是「不搬」终态）。 */
        public final Reason reason;
        /** 请求真抽量（= min(机器模拟量, 落点余量)；恒 >0 当且仅当 GATE_OPEN）。 */
        public final int want;
        /** 落点 tank 号（0…17；不搬时 -1）。 */
        public final int tank;

        private Plan(Reason reason, int want, int tank) {
            this.reason = reason;
            this.want = want;
            this.tank = tank;
        }

        static Plan ok(int want, int tank) {
            return new Plan(Reason.GATE_OPEN, want, tank);
        }

        static Plan fail(Reason reason) {
            return new Plan(reason, 0, -1);
        }
    }

    private PocketFluidExtraction() {}

    /**
     * 机器侧门禁判因（<b>不碰口袋</b>，机器抽不出时不必读口袋状态）。
     *
     * @param machineTimer   {@code BaseMetaTileEntity.getTimer()}（{@code mTickTimer} 的唯一公开读数）
     * @param gatedSimulated 走门禁的模拟量（{@code drain(UNKNOWN, 大数, false)} 的 amount；null=0）
     * @param ungatedContent 绕门禁直探 MTE 的存量（<b>-1 = 探针不可用</b>，0 = 确实无货，>0 = 有货）
     * @return {@link Reason#GATE_OPEN} 之外都是「不搬」终态分类
     */
    public static Reason machineGate(long machineTimer, int gatedSimulated, int ungatedContent) {
        if (machineTimer <= GATE_TICK_THRESHOLD) {
            return Reason.MACHINE_TOO_NEW;
        }
        if (gatedSimulated > 0) {
            return Reason.GATE_OPEN;
        }
        if (ungatedContent < 0) {
            return Reason.MACHINE_EMPTY_OR_LOCKED;
        }
        return ungatedContent > 0 ? Reason.MACHINE_LOCKED : Reason.MACHINE_EMPTY;
    }

    /**
     * 落点选择与搬运量（只在 {@link #machineGate} 返回 GATE_OPEN 后调用）。
     * <p>
     * 落点规则（实现片裁定，契约=不丢量+回执如实）：
     * <ol>
     * <li><b>同流体优先</b>：在「槽里已有这种流体」的 tank 里选<b>余量最大</b>的（同余量取最靠前）
     * —— 先合并、不散装，18 个 tank 不会一种流体碎成多份；</li>
     * <li>没有可合并的 ⇒ <b>第一个空 tank</b>（空 tank 余量恒=容量，取最靠前）；</li>
     * <li>既无可合并也无空 ⇒ {@link Reason#POCKET_NO_ROOM}（含「18 格全是别种流体」）。</li>
     * </ol>
     * {@code want = min(gatedSimulated, 落点余量)} —— 先按口袋容量求量再真抽（GT5U
     * {@code drain(true)} 一次全抽而口袋装不下会凭空抹掉差额，计划 §3-S6 明令禁止）。
     *
     * @param gatedSimulated 机器模拟量（>0）
     * @param tankAmounts    各 tank 现有量（{@code null} 或长度 0 ⇒ POCKET_NO_ROOM；负值按 0）
     * @param tankCompatible 各 tank 是否收这种流体（空槽必须传 true；与 amounts 等长，不等长按不搬处理）
     * @param tankCapacity   单 tank 容量（★由调用侧按 {@code PocketConstants.fluidTankCapacityMl(boolean)}
     *                       的<b>选择点</b>取值传入：未升级档 = {@code FLUID_BAR_CAPACITY_ML}（20M），
     *                       CAPACITY 位固化档 = {@code FLUID_BAR_CAPACITY_UPGRADED_ML}（2G，本形参是 int ⇒
     *                       适配器侧那道 {@code min(Integer.MAX_VALUE, …)} 是截断防线，不是第二处容量真相））
     */
    public static Plan plan(int gatedSimulated, int[] tankAmounts, boolean[] tankCompatible, int tankCapacity) {
        if (gatedSimulated <= 0 || tankAmounts == null
            || tankCompatible == null
            || tankAmounts.length == 0
            || tankAmounts.length != tankCompatible.length
            || tankCapacity <= 0) {
            return Plan.fail(Reason.POCKET_NO_ROOM);
        }
        // 同流体合并支与空槽支分开记：合并支优先（先合并、不散装），空槽只记最靠前的那个
        int mergeTank = -1;
        int mergeRoom = 0;
        int emptyTank = -1;
        for (int tank = 0; tank < tankAmounts.length; tank++) {
            if (!tankCompatible[tank]) {
                continue;
            }
            final int amount = Math.max(0, tankAmounts[tank]);
            if (amount > 0) {
                // 余量最大者胜；同余量取最靠前（严格大于才替换）
                final int room = tankRoom(tankCapacity, amount, true);
                if (room > mergeRoom) {
                    mergeTank = tank;
                    mergeRoom = room;
                }
            } else if (emptyTank < 0) {
                emptyTank = tank;
            }
        }
        if (mergeTank >= 0 && mergeRoom > 0) {
            return Plan.ok(Math.min(gatedSimulated, mergeRoom), mergeTank);
        }
        if (emptyTank >= 0) {
            return Plan.ok(Math.min(gatedSimulated, tankCapacity), emptyTank);
        }
        return Plan.fail(Reason.POCKET_NO_ROOM);
    }

    /**
     * 单 tank 余量的纯算术：不兼容（异流体）⇒ 0；兼容 ⇒ {@code max(0, capacity - 现有量)}。
     * <p>
     * 语义与 {@code PocketInventory#barRoom} 同源（该方法是 GUI 侧流体搬运的同一判据点），
     * 本类不 import gui 包、在这里自含实现（计划 §3-S6「复用语义而不 import」）。
     */
    public static int tankRoom(int capacity, int amount, boolean compatible) {
        if (!compatible || capacity <= 0) {
            return 0;
        }
        return Math.max(0, capacity - Math.max(0, amount));
    }

    /**
     * 搬完后机器侧还剩多少（「部分搬运」回执的未搬量；负数钳 0）。
     * <p>
     * U7 口径：tier≥4 数字罐（32M 起）超<b>未升级</b>单 tank（★R96 S3：20M）⇒ {@code received < 机器量} 是常态，
     * 回执必须如实报「实收 X、机器里还有约 Y」，不许把截断藏成「搬完了」。★装了 CAPACITY 的口袋按 2G 夹取
     * ⇒ 同一台机器可能从"部分搬运"变成"全搬"，这条差异正是 {@code PocketWorldFluidTap} 必须走选择点的原因。
     */
    public static int remainingAfter(int machineContent, int received) {
        final int left = machineContent - Math.max(0, received);
        return Math.max(0, left);
    }
}
