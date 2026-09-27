package com.miaokatze.gtit.common.items.pocket;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 两条<b>关闭守卫</b>：把"玩家想把某个已固化的升级关掉"这一手势，换算成"关掉之后现有内容会不会装不下"
 * （R96 S1，P-1）。
 *
 * <h2>为什么是"预防式拒绝"而不是"移除时回流"</h2>
 * 容量与堆叠两把尺子都跟着开关缩（16G→16M、1024→64）。若允许"装着 5G 流体时关容量"，那 5G 要么在下一次
 * 落盘时被 tank 天花板截断、要么在读取时按头值收口 —— 两条都是<b>静默销毁玩家资产</b>。本轮明确不做
 * SB {@code StackUpgradeItem} 式的"移除/降级时回流或拆堆"（R96 计划 §1.2），取而代之就是这一处的<b>拒绝</b>：
 * 缩得下才让关。
 *
 * <h2>单源：GUI 腿与服务端腿共用同一个判据</h2>
 * 客户端的置灰/提示与服务端的实际拒绝<b>必须</b>调同一个方法，否则两侧各写一遍 {@code >} 就是两处真相 ——
 * 与 {@link PocketInventory#effectiveStorageLimit(boolean, ItemStack)} /
 * {@link PocketConstants#fluidTankCapacityMl(boolean)} 走单源是同一条纪律。服务端仍<b>必须</b>独立复检并
 * 拒绝写入（不是"写完回滚"：回滚会多打一包、留一帧脏态，且光泽会先亮一下）。
 *
 * <h2>★容量腿读的是 long 真值，不是 int 头（R95-W1 的读侧版本）</h2>
 * 有会话时读 {@link PocketInventory#tankTruthAt(int)}（long 真值），无会话时读根 NBT
 * {@link PocketConstants#FLUID_BAR} 且<b>优先 {@link PocketConstants#FLUID_BAR_AMOUNT_L}、缺键回落
 * {@link PocketConstants#FLUID_BAR_AMOUNT}</b>，数值的取舍与 {@code PocketInventory#loadTanks} 一致（含脏档按
 * {@code max(head, AmountL)} 收口）；★但不经 {@code FluidStack} 的 NBT 读法（理由与偏差方向写在
 * {@link #rootTruth} 上）。若这里改读头值，16G 档的罐子只会读成 int 顶、超出部分不存在，守卫就会放行
 * ⇒ 玩家"关掉了开关、还留着 5G 流体"，而所有离线断言与出图照样全绿。这条由用例
 * {@code capacity_off_guard_reads_long_truth_not_head} 的<b>阳性对照</b>钉住，不靠本注释自证。
 *
 * <h2>★堆叠腿的尺子 = {@code effectiveStorageLimit(false, …)}，且扫描面含玩家游标栈</h2>
 * 传 {@code false} 就是"关掉<b>之后</b>"的那把尺子，天然与执法点同值（同一份算式，不抄 64 字面量）。
 * 游标必须在扫描面里：代价清单挂着">64 的堆在游标上、关屏如何归还"未实机验 —— 守卫放行后游标那 1024 件
 * 在关屏时按 64 的尺子无处可去，只扫中栏等于给这条已知风险开门。
 *
 * <h2>★探针：守卫同时是"×16 到底有没有落到运行时"的实机裁判</h2>
 * 堆叠升级本轮被判为"实机未生效"的缺陷 ⇒ 若升级根本没生效，中栏永远不可能出现 {@code >64} 的堆 ⇒ 守卫
 * <b>永远不会触发</b>，看起来"实现了"却什么都没做（R57「写了没人调」同族）。故 {@link OffVerdict} 额外登记
 * <b>存储区（含游标）{@code >64} 的栈数</b>与<b>源质 {@code >256} 的 tag 数</b>，两者都是 0 时打一次性
 * INFO ⇒ "0"不再与"没东西可拦"混淆，比逐条人肉数格子的检查表强。
 *
 * <h2>MAGNET / CHANNEL_PERSIST / MAGE 恒允许关闭</h2>
 * 这三型需求没配守卫，且关闭只让各自的 driver 早退：<b>不主动 stop</b> 已在跑的通道状态（关掉
 * CHANNEL_PERSIST 后通道自然衰减到 0，复用既有 {@code finishBatch} 的回收支，不建第二台状态机）。
 */
public final class PocketUpgradeGuards {

    /** NBT 类型号（与 {@code PocketInventory} 的同一组常量同值，本类只用到三个）。 */
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_LIST = 9;

    /** 一次性 INFO 的闩（形状级问题，按进程一次；同一理由的第二次没有信息量）。 */
    private static boolean probeZeroLogged;

    private PocketUpgradeGuards() {}

    /** 拒绝理由；除 {@link #ALLOW} 之外都是"关掉就装不下"。 */
    public enum Reason {
        /** 允许关闭。 */
        ALLOW,
        /** 有 tank 的 long 真值超过关闭后的单 tank 容量。 */
        CAPACITY_OVER_OFF_LIMIT,
        /** 有存储栈（含玩家游标栈）超过关闭后的单格可叠上限。 */
        STACK_OVER_OFF_LIMIT
    }

    /** 守卫结论：一个理由 + 探针读数（探针与判据在同一条腿里算出来，不为它再扫一遍档）。 */
    public static final class OffVerdict {

        private final Reason reason;
        private final int overOffLimitStacks;
        private final int stacksOverBase;
        private final int essenceTagsOverBase;
        private final boolean storageScanned;

        OffVerdict(Reason reason, int overOffLimitStacks, int stacksOverBase, int essenceTagsOverBase,
            boolean storageScanned) {
            this.reason = reason;
            this.overOffLimitStacks = overOffLimitStacks;
            this.stacksOverBase = stacksOverBase;
            this.essenceTagsOverBase = essenceTagsOverBase;
            this.storageScanned = storageScanned;
        }

        public boolean allowed() {
            return reason == Reason.ALLOW;
        }

        public Reason reason() {
            return reason;
        }

        /** 关掉后装不下的存储栈条数（★含游标）。 */
        public int overOffLimitStacks() {
            return overOffLimitStacks;
        }

        /** 探针：中栏 + 游标里 {@code >}{@link PocketConstants#STORAGE_SLOT_LIMIT_BASE} 的栈数；未扫 ⇒ −1。 */
        public int stacksOverBase() {
            return stacksOverBase;
        }

        /** 探针：源质表里 {@code >}{@link PocketConstants#ESSENCE_CAP_PER_TAG} 的 tag 数。 */
        public int essenceTagsOverBase() {
            return essenceTagsOverBase;
        }

        /**
         * 中栏是否真参与了扫描。无会话时没有 handler 可扫 ⇒ {@code false} ⇒ 不许把 {@code ALLOW} 读成
         * "扫过且干净"（这一位就是防"探针恒 0 被当成恒安全"的第二种假绿）。
         */
        public boolean storageScanned() {
            return storageScanned;
        }
    }

    /**
     * 三参形态（计划 §3 的契约形状）：不交代游标 ⇒ 游标不参与堆叠扫描。
     * ★调用方手里有玩家游标时必须用四参版，否则本类上面那段"游标在扫描面里"就是空话。
     */
    public static OffVerdict canTurnOff(PocketUpgradeType type, PocketInventory inv, ItemStack carrier) {
        return canTurnOff(type, inv, carrier, null);
    }

    /**
     * 关闭 {@code type} 是否放行。
     *
     * @param inv     当前面板会话的数据面；{@code null} = 无会话（面板外）⇒ 容量腿回落读根 NBT，堆叠腿无法
     *                扫中栏（{@link OffVerdict#storageScanned()} 据实报 {@code false}）
     * @param carrier 载体口袋栈（无会话时的根 NBT 来源）
     * @param cursor  玩家游标栈（{@code EntityPlayer.inventory.getItemStack()}），可为 {@code null}
     */
    public static OffVerdict canTurnOff(PocketUpgradeType type, PocketInventory inv, ItemStack carrier,
        ItemStack cursor) {
        switch (type) {
            case CAPACITY:
            case STACK:
                break;
            default:
                // MAGNET / CHANNEL_PERSIST / MAGE：关闭只让 driver 早退，不主动 stop 现有状态
                return new OffVerdict(Reason.ALLOW, 0, -1, -1, false);
        }
        return type == PocketUpgradeType.CAPACITY ? capacityVerdict(inv, carrier) : stackVerdict(inv, carrier, cursor);
    }

    // ------------------------------------------------------------------ 容量腿

    private static OffVerdict capacityVerdict(PocketInventory inv, ItemStack carrier) {
        // 关闭后的容量 = 未升级那一档；选择点只有 fluidTankCapacityMl 这一处，本类不写第二个数
        final long offLimit = PocketConstants.fluidTankCapacityMl(false);
        long worst;
        if (inv != null) {
            worst = 0L;
            for (int tank = 0; tank < PocketInventory.FLUID_TANK_COUNT; tank++) {
                final long truth = inv.tankTruthAt(tank);
                if (truth > worst) {
                    worst = truth;
                }
            }
        } else {
            worst = rootTruth(carrier == null ? null : carrier.getTagCompound());
        }
        return new OffVerdict(
            worst > offLimit ? Reason.CAPACITY_OVER_OFF_LIMIT : Reason.ALLOW,
            0,
            -1,
            essenceTagsOverBase(inv, carrier),
            false);
    }

    /**
     * 无会话时的单罐真值上界：读根 NBT {@link PocketConstants#FLUID_BAR}，两代形状都认，优先
     * {@link PocketConstants#FLUID_BAR_AMOUNT_L}、缺键回落 {@link PocketConstants#FLUID_BAR_AMOUNT}、
     * 脏档按 {@code max(head, AmountL)} 收口 —— 数值口径与 {@code PocketInventory#loadTanks} 一致。
     * <p>
     * ★<b>刻意不经过 {@code FluidStack.loadFluidStackFromNBT}，两处差异都是有意的</b>：
     * <ol>
     * <li>那条读法会去查 {@code FluidRegistry}，而守卫的判据是"<b>数</b>装不装得下"，不是"是哪一种流体"⇒
     * 为一个数去依赖注册表状态是本末倒置（注册表还没起来的 JVM 里它甚至会把守卫直接打挂，
     * 让本条判据连离线回归都跑不动）；</li>
     * <li>因此"流体名当前解析不出来"的条目<b>仍然参与</b>判定（{@code loadTanks} 那种条目是不载入的）。
     * 偏差方向是<b>保守侧</b>：最多多拒一次"关不掉开关"，不会少拒 ⇒ 而少拒的那一侧是销毁玩家流体，
     * 多拒的那一侧只是让玩家先腾罐再关。用 {@link PocketConstants#FLUID_BAR_FLUID_NAME} 只判"有没有名"，
     * 与流体注册无关。</li>
     * </ol>
     */
    private static long rootTruth(NBTTagCompound root) {
        if (root == null || !root.hasKey(PocketConstants.FLUID_BAR)) {
            return 0L;
        }
        if (root.hasKey(PocketConstants.FLUID_BAR, TAG_LIST)) {
            long worst = 0L;
            final NBTTagList list = root.getTagList(PocketConstants.FLUID_BAR, TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                worst = Math.max(worst, truthOf(list.getCompoundTagAt(i)));
            }
            return worst;
        }
        if (root.hasKey(PocketConstants.FLUID_BAR, TAG_COMPOUND)) {
            // 旧档单 compound（那一代没有 AmountL）⇒ 头值即真值
            return truthOf(root.getCompoundTag(PocketConstants.FLUID_BAR));
        }
        return 0L;
    }

    /** 一条 fluidBar 条目的 long 真值：无名条目按"不是内容"跳过，其余 AmountL 优先、脏档取严。 */
    private static long truthOf(NBTTagCompound entry) {
        if (entry.getString(PocketConstants.FLUID_BAR_FLUID_NAME)
            .isEmpty()) {
            return 0L;
        }
        final long head = Math.max(0, entry.getInteger(PocketConstants.FLUID_BAR_AMOUNT));
        return entry.hasKey(PocketConstants.FLUID_BAR_AMOUNT_L)
            ? Math.max(head, entry.getLong(PocketConstants.FLUID_BAR_AMOUNT_L))
            : head;
    }

    // ------------------------------------------------------------------ 堆叠腿

    private static OffVerdict stackVerdict(PocketInventory inv, ItemStack carrier, ItemStack cursor) {
        int over = 0;
        int overBase = 0;
        if (inv == null) {
            // 无会话 ⇒ 中栏没有 handler 可扫（P-2 下关闭手势只发生在面板内，本支是防御性回落）。
            // 游标仍然扫：那是"关掉以后无处安放"最直接的形状。
            over = overOffLimitCount(cursor);
            overBase = -1;
        } else {
            final ItemStackHandler storage = inv.storage();
            for (int slot = 0; slot < storage.getSlots(); slot++) {
                final ItemStack stack = storage.getStackInSlot(slot);
                // ★getItem() 也要判：effectiveStorageLimit 会问 getMaxStackSize() ⇒ maxStackSize 又问
                // getItem().getItemStackLimit(stack)，外来/未注册的空气栈（有数无件）会当场 NPE
                if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
                    continue;
                }
                if (stack.stackSize > PocketInventory.effectiveStorageLimit(false, stack)) {
                    over++;
                }
                if (stack.stackSize > PocketConstants.STORAGE_SLOT_LIMIT_BASE) {
                    overBase++;
                }
            }
            over += overOffLimitCount(cursor);
            if (cursor != null && cursor.stackSize > PocketConstants.STORAGE_SLOT_LIMIT_BASE) {
                overBase++;
            }
        }
        final int tags = essenceTagsOverBase(inv, carrier);
        logProbeOnce(overBase == 0 && tags == 0, inv == null ? "无会话，中栏未参与扫描" : null);
        return new OffVerdict(over > 0 ? Reason.STACK_OVER_OFF_LIMIT : Reason.ALLOW, over, overBase, tags, inv != null);
    }

    /** 单条栈是否超过"关闭后"的尺子（{@code false} = 未升级那一档，与执法点同一份算式）。 */
    private static int overOffLimitCount(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
            return 0;
        }
        return stack.stackSize > PocketInventory.effectiveStorageLimit(false, stack) ? 1 : 0;
    }

    // ------------------------------------------------------------------ 探针

    /** 源质表里超过基值上限的 tag 数（会话在场读内存表，否则读根 NBT 的 {@code ess} 表）。 */
    private static int essenceTagsOverBase(PocketInventory inv, ItemStack carrier) {
        final int base = PocketConstants.ESSENCE_CAP_PER_TAG;
        int hits = 0;
        if (inv != null && inv.essence() != null) {
            for (Map.Entry<String, Integer> entry : inv.essence()
                .snapshot()
                .entrySet()) {
                if (entry.getValue() != null && entry.getValue() > base) {
                    hits++;
                }
            }
            return hits;
        }
        final NBTTagCompound root = carrier == null ? null : carrier.getTagCompound();
        if (root == null || !root.hasKey(PocketConstants.ESSENCE)) {
            return 0;
        }
        final NBTTagList list = root.getCompoundTag(PocketConstants.ESSENCE)
            .getTagList(PocketConstants.ASPECTS, TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            // ASPECT_AMOUNT 的落档形状是 Short（照 PocketEssenceStore#readFrom 的同一条读法）
            if (list.getCompoundTagAt(i)
                .getShort(PocketConstants.ASPECT_AMOUNT) > base) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 两个探针计数都是 0 ⇒ 一次性 INFO：这一次切换<b>没能</b>证明"×16 真落到了运行时"（守卫压根没被问到 =
     * 与 R95 C3「用例全绿而实机未生效」同形），把这条事实说在明处而不是留成假绿。
     */
    private static void logProbeOnce(boolean bothZero, String extraReason) {
        if (!bothZero || probeZeroLogged) {
            return;
        }
        probeZeroLogged = true;
        GTInterestingThing.LOG.info(
            "[pocket] 关闭守卫探针：本次切换时全区没有 >{} 的存储栈、也没有 >{} 的源质 tag{} ⇒ 本次不能据此判断"
                + "堆叠升级（×16）是否真的落到了运行时（守卫只在真有超基值内容时才会被问到；本条只报一次）",
            PocketConstants.STORAGE_SLOT_LIMIT_BASE,
            PocketConstants.ESSENCE_CAP_PER_TAG,
            extraReason == null ? "" : "（" + extraReason + "）");
    }
}
