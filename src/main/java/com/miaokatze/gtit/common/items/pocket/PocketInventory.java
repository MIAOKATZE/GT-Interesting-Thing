package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 一次 GUI 会话的口袋内容（内存 {@code ItemStack[]} ↔ 物品 NBT）。
 * <p>
 * <b>三条硬口径</b>（R22 + R53c，不是偏好）：
 * <ol>
 * <li><b>绝不用 MUI2 自带的那个「按 NBT 现取现解」handler</b>（R22）——其上游源码里自带
 * 一句 {@code "this doesn't work"} 的 TODO 注释（原因：它持有的 container 与真实槽里的不是同一对象），
 * 且每次读格都现场反序列化。这里用内存数组
 * （{@link ItemStackHandler} 的 {@code ItemStack[]} 形态），仓内同口径先例
 * {@code reincarnation/gui/ReincarnationContainer.java:128-139} 的 {@code SessionInventory}。</li>
 * <li><b>NBT 只在打开时读一次</b>（{@link #readFrom(NBTTagCompound)}），<b>写只发生在关屏</b>
 * （{@code NekoPocketContainer.onModularContainerClosed} → {@link #writeTo(NBTTagCompound)}）。</li>
 * <li><b>脏标记才序列化</b>（{@link #isDirty()}）：禁止每 tick {@code setTagCompound}，
 * 否则 vanilla {@code Container.detectAndSendChanges} 会拿整份 NBT 做相等比较并在变化时重发整枚口袋
 * （R53c 的包放大面）。</li>
 * </ol>
 * <p>
 * 本对象<b>双端各持一份</b>（面板树双端同构构建）。中栏 135 格的<b>内容</b>由 vanilla 的
 * {@code Packet103SetSlot} → {@code Slot.putStack} 喂给客户端那份 handler，
 * 因此客户端只负责显示；序列化只从服务端那份发生（关屏钩子按 mixin 只跑在 {@code EntityPlayerMP}）。
 * <p>
 * <b>形状变更与旧档兼容</b>：中栏 128→150（R75）→<b>135</b>（R80①，★方向相反：<b>收缩</b>）、
 * 流体 1 tank→{@link #FLUID_TANK_COUNT} tank。读侧<b>格数永远由构造期决定</b>（{@link #loadGroup}
 * 忽略档里的 {@code Size} ⇒ "只增不减"那条老话在收缩场景下的正确表述是
 * <b>"handler 绝不跟着档缩小，也绝不跟着档变大"</b>），且旧单 tank 内容落到 0 号（{@link #loadTanks}），
 * 写侧一律新形状。
 * <p>
 * ★<b>R80① 收缩兼容的三个后果（本轮新增，逐条有回归用例）</b>：
 * ① 150 格时代写的档，其 135…149 号条目<b>槽号越界</b> ⇒ ② 越界条目<b>丢弃 + 一次性 WARN</b>
 * （报出条数与现有格数；既不静默丢件，也不像上游 {@code deserializeNBT} 那样把 handler 缩成 150 格
 * 再去点越界槽号炸容器）⇒ ③ 0…134 号条目<b>原样落位</b>（同一批槽号在收缩前后指的是同一格，
 * 不重排、不搬移），玩家侧表现为"最后 15 格的东西不见了 + 日志一条 WARN"，
 * <b>不是</b>面板打不开、也<b>不是</b>整档清零。
 * <p>
 * 纯数据件：不持 {@code EntityPlayer}、不持 {@code World}，也不做任何搬运决策
 * （流体搬运在 {@code gui.pocket.PocketSlots}，通道与蒸馏归 S6/S7）。
 * <p>
 * ★R90 T3：从 {@code gui.pocket} 迁包而来（零行为变更的纯搬移——GUI 装配侧与 world 侧共用的
 * 纯数据件不依赖任何 gui 类，落在 common 后 world 侧不必再反向 import gui 包）。
 */
public final class PocketInventory {

    /** 中栏格数 = 9 列 × 15 行 = 135（R80①；单源取 {@link PocketConstants#GHOST_ITEM_SLOT_LIMIT}）。 */
    public static final int STORAGE_SLOTS = PocketConstants.GHOST_ITEM_SLOT_LIMIT;
    /**
     * 流体交互格数 = {@link PocketConstants#FLUID_TANK_TOTAL} 个 tank ×
     * {@link PocketConstants#FLUID_INTERACTION_PER_COLUMN} 格 = <b>36</b>（R78②：3 组 × 6 列 × 进/出）。
     * <p>
     * ★ handler 索引与 tank 的对应关系住在 {@link #tankOfInteractionSlot(int)}（唯一映射点）：
     * 矩阵按组产出索引，一组内"先铺满 6 个上格、再铺 6 个下格"⇒
     * {@code 0…5} 是第 1 组的进格、{@code 6…11} 是第 1 组的出格、{@code 12…17} 第 2 组进格…
     * 旧 12 格档读进新 handler 的前 12 格<b>恰好还是同一批 tank</b>（见该方法的兼容说明）。
     */
    public static final int FLUID_INTERACTION_SLOTS = PocketConstants.FLUID_INTERACTION_TOTAL;
    /** 蒸馏输入格数 = 2 行 × 6 列（R75 换排布不换格数；§14.3 覆盖计划 §6 的「3 个槽」旧口径）。 */
    public static final int DISTILL_INPUT_SLOTS = 12;
    /** 绑定格数（需求 5，R43a 的瞬时入口；R75 后落在底部带）。 */
    public static final int BIND_SLOTS = 1;
    /**
     * 升级插件格数（R95：<b>转发</b> {@link PocketConstants#UPGRADE_SLOTS}，槽号 = 效果位图位 =
     * {@code PocketUpgradeType#ordinal()}——三个空间是同一个数，读法见 {@link #newUpgradeGroup(int)}）。
     */
    public static final int UPGRADE_SLOTS = PocketConstants.UPGRADE_SLOTS;
    /**
     * 独立流体 tank 数（= 组数 × 每组列数 = {@code 3 × 6 = 18}，R78②；
     * 单源同 {@link PocketConstants#FLUID_TANK_TOTAL}，也是 {@code Kind.FLUID} 的 ghost 索引空间）。
     */
    public static final int FLUID_TANK_COUNT = PocketConstants.FLUID_TANK_TOTAL;
    /**
     * 每组流体侧的交互格数（= 该组的 tank 数 × 每 tank 格数 = {@code 6 × 2 = 12}）。
     * <p>
     * ★这一层是 R78 新增的"组"维度：旧口径（1 组）下它等于全部交互格数，
     * 所以 {@link #tankOfInteractionSlot(int)} 的"取模"读法在旧档上与新读法<b>逐字同解</b>。
     */
    public static final int FLUID_INTERACTION_PER_GROUP = PocketConstants.FLUID_COLUMN_COUNT
        * PocketConstants.FLUID_INTERACTION_PER_COLUMN;

    /**
     * {@code ItemStackHandler} 自己的落档形状键（{@code Items} / {@code Slot} / {@code Count} / {@code Size}）。
     * <p>
     * ★<b>不是本 mod 的 NBT 键名</b>，而是上游 handler 的内部形状；列在这里只有一个理由：
     * {@link #loadGroup} 必须<b>绕开</b> {@code handler.deserializeNBT}（见该方法），而要绕开就得自己
     * 读这一层。形状一旦被上游改动，{@code NekoPocketModelTest#storage_group_shape_roundtrip_pins_library_keys}
     * 会立刻红（它用 handler 自己的 {@code serializeNBT} 产出输入，再喂回 {@link #readFrom}），
     * 而不是留一个"越界键被静默吞掉"的哑洞。
     */
    private static final String LIB_ITEMS = "Items";
    private static final String LIB_SLOT = "Slot";
    private static final String LIB_COUNT = "Count";
    /** NBT 的 compound / int / list tag id（与 {@code PocketCellBindings} 同一口径的字面量）。 */
    private static final int LIB_TAG_COMPOUND = 10;
    private static final int LIB_TAG_INT = 3;
    private static final int LIB_TAG_LIST = 9;

    /** 脏标记：只有内容真的变过才序列化（R53c 第 1 条）。 */
    private boolean dirty;
    /**
     * ★★<b>R92-④（审查 B2 修）：玩家放置意图的一次性登记 —— 槽号 + 那一次要放的东西的载荷键</b>。
     * <p>
     * 为什么不能只看 {@code onContentsChanged}：那条回调对<b>所有</b>写入都响，而中栏的程序化写入面比
     * 玩家路径多得多——{@code loadGroup}（读档）、{@code NekoPocketServerHandler#performSort}（整理，
     * 先清空再回写）、{@code NekoPocketPanel#evictFromSlot}（ghost 产物重塞）、{@code depositItem} /
     * {@link #depositIntoStorage}（通道拉取落点）。★它们全都不经过 {@code isItemValid}
     * （本文件那条 javadoc 早就写明"程序化写入不经过这里"）⇒ 把"定档"挂在回调上，就等于让一次自动落点
     * 把无关物品记成该格的记忆；之后 P2 那条"已定档拒异类"反而会把玩家<b>真想放</b>的东西拒掉。
     * <p>
     * ⇒ 准入信号只有一个来源：{@code isItemValid}（原版六条放入分支唯一的公共闸）。它<b>登记意图</b>，
     * 回调只<b>消费</b>意图，且消费一次即清（★绝不留悬垂状态给下一次写入）。落进来的东西与登记的键
     * 不一致 ⇒ 同样不建档。
     * <p>
     * ★残余窗口如实写明：若玩家对某格点过一次合法放置但<b>没放成</b>（意图留在槽里），随后程序化落点
     * 又把<b>同一种</b>物品送进<b>同一格</b>，会定档。★不会错配到"另一种东西"上（键必须相等），
     * 而那一格本来就挂着"记住这一种"的 {@code L} ⇒ 该结果与玩家已表达过的意图一致，不是新的错误面。
     */
    private int placementIntentSlot = -1;
    /** ★R92-④：与 {@link #placementIntentSlot} 同生同灭的那一次的载荷键（空串 = 无意图）。 */
    private String placementIntentKey = null;

    private final ItemStackHandler storage = newStorageGroup(STORAGE_SLOTS);
    private final ItemStackHandler fluidInteraction = newSlotGroup(FLUID_INTERACTION_SLOTS);
    private final ItemStackHandler distillInput = newSlotGroup(DISTILL_INPUT_SLOTS);
    private final ItemStackHandler bindSlot = newSlotGroup(BIND_SLOTS);
    private final ItemStackHandler upgradeCells = newUpgradeGroup(UPGRADE_SLOTS);

    /**
     * {@link #FLUID_TANK_COUNT} 个<b>互相独立</b>的流体 tank（R75①：每列一个；★R95 S5 起容量按
     * CAPACITY 位 16M/16G 双档）。
     * <p>
     * ★★<b>R95 S5：双轨计数</b>——16G（16,000,000,000）不进 int，而 MUI2 的
     * {@link FluidStackTank} 是<b>全 int API</b>（{@code fill/drain/getCapacity} 的字节码实证）⇒
     * tank 拆成两层：
     * <ul>
     * <li><b>真值层</b> {@link #tankTruth}（{@code long[]}，0…16G，权威）；</li>
     * <li><b>头层</b> {@link #tankFluid} + {@link #tanks}（现有 {@code FluidStackTank} 原样保留，
     * 作「<b>身份 + 交互头</b>」：GUI 的 {@code FluidSlotSyncHandler}、玩家容器灌排、
     * {@code PocketFluidTransfer} 都继续只碰它）。头容量经 {@link IntSupplier} 现读
     * {@link #fluidTankCapacity()}（未升级 16M / 升级后 int 顶 {@code Integer.MAX_VALUE}）。</li>
     * </ul>
     * <b>不变式：头 ≡ min(真值, Integer.MAX_VALUE)</b>。★<b>R95-W1（审查修复）起这条由两处共同执法，
     * 不再是一句"setter 钩子=唯一进口"</b>：头类（MUI2 2.3.91 {@code FluidStackTank}，字节码实证）的
     * {@code fill} 在<b>非空同流体合并支</b>与 {@code drain} 在<b>部分抽出支</b>都对 getter 返回的栈
     * 直接 {@code amount ±=}，<b>不触发 setter 钩子</b> ⇒ 钩子只捕获「整替换」写（空灌入 / 抽空置 null）。
     * 于是：① 增量写的回同步落在<b>调用点/原语</b>——构造期把 {@code tanks[i]} 覆写成把 fill/drain
     * 转发到真值域原语的子类（外部头轨调用者：{@code FluidSlotSyncHandler} 的点击灌排、
     * {@code PocketFluidTransfer} 经 {@link #tankAt(int)}，都自动走执法入口）；② 所有头 amount 的落钳
     * 收口在唯一函数 {@link #syncHead(int)}（原语推完真值后立即回同步）。
     * 数组下标 = tank 号 = {@code Kind.FLUID} 的 ghost 槽号 = 流体列号，三者同一个数。
     */
    private final FluidStack[] tankFluid = new FluidStack[FLUID_TANK_COUNT];
    private final FluidStackTank[] tanks = new FluidStackTank[FLUID_TANK_COUNT];
    /** ★R95 S5：每个 tank 的 <b>long 真值</b>（mB，双轨计数的权威侧；头层见 {@link #tankFluid} 的 javadoc）。 */
    private final long[] tankTruth = new long[FLUID_TANK_COUNT];
    /**
     * ★R95 S5：CAPACITY 升级位的<b>查询式探针</b>（默认 {@code false}）。{@code readFrom} 用档内
     * 位图自播种，面板（GUI 装配侧）再换成「活查载体栈」版（会话期内放入容量插件即生效，
     * 见 {@link #setUpgradeProbes(BooleanSupplier, BooleanSupplier)} 的接线说明）。
     */
    private java.util.function.BooleanSupplier capacityProbe = () -> false;
    /** ★R95 S5：STACK 升级位的查询式探针（管存储格堆叠与源质每格上限两处，同一位）。 */
    private java.util.function.BooleanSupplier stackProbe = () -> false;

    private PocketEssenceStore essence;
    private PocketCellBindings bindings;
    private PocketFilterConfig filters;

    /**
     * ★R90 S1（D2「上传变瓶+刷源质」修复面）：源质<b>增量日志</b> —— tag → 自上一次持久化边界以来的
     * 增减<b>代数和</b>（非 0 才有条目）。<b>不持久化</b>（随本实例生命周期），唯一读者是
     * {@code NekoPocketPanel#writeSessionToCarrier} 的终态回滚（S2）。
     */
    private final java.util.Map<String, Integer> essenceDeltas = new java.util.LinkedHashMap<>();
    /**
     * 增量日志的<b>对表基点</b>：上一次已并入日志的源质快照。
     * <p>
     * ★为什么需要它（单一真相论证）：源质表的写原语（{@code putAll}/{@code add}/{@code extract}）是
     * {@code PocketEssenceStore} 自己的，通道下传（{@code PocketEssenceChannelOps}）、蒸馏入账
     * （{@code PocketDistillDriver}）、注入支（{@code PocketSlots}）都<b>绕过本类直接改 store</b>，
     * 而 store 是 {@code final} 类、那些调用文件也不在本修复片的可写面里 ⇒ 打点式登记无法覆盖全部路径。
     * 因此日志的完整性由「{@link #reconcileEssenceDeltas()} 对表 store 现值 vs 基点的漂移」兜底：
     * <b>任何</b>代码改了 store，下一次读日志/落盘时漂移都会被并入 —— 配合 {@link #recordEssenceDelta}
     * 的"登记时同步前移基点"，同一笔变更不会双重计入（两者恒等式：日志累计 ≡ store 现值 − 基点）。
     */
    private final java.util.Map<String, Integer> essenceLastSeen = new java.util.LinkedHashMap<>();

    private PocketInventory() {
        for (int index = 0; index < FLUID_TANK_COUNT; index++) {
            final int tank = index;
            // ★R95 S5：头容量走 IntSupplier 现读（未升级 16M / 升级 int 顶）⇒ 会话期内固化 CAPACITY 位
            // 之后头容量即时换档，不需要重建 tank（FluidStackTank 构造子原生支持 supplier 形态）。
            // ★★R95-W1（审查修复）：fill/drain 整体覆写成「真值域原语」转发 —— 头类的非空同流体合并支
            // 与部分抽出支不触发 setter 钩子（字节码实证，2.3.91），留头类原样就是让外部头轨调用者
            // （FluidSlotSyncHandler 点击灌排 / PocketFluidTransfer 经 tankAt）只动头不动真值 ⇒
            // saveTanks 写前钳回 = 入侧蒸发 / 出侧复制。覆写后这些调用者与四个公开原语走同一条
            // 「改真值 → syncHead 回同步」算式，simulate 支（doFill/doDrain=false）保持不触碰双轨。
            tanks[index] = new FluidStackTank(
                () -> tankFluid[tank],
                fluid -> applyHeadWrite(tank, fluid),
                (java.util.function.IntSupplier) () -> (int) Math.min(fluidTankCapacity(), Integer.MAX_VALUE)) {

                @Override
                public int fill(FluidStack resource, boolean doFill) {
                    if (resource == null || resource.amount <= 0) {
                        return 0;
                    }
                    final FluidStack head = tankFluid[tank];
                    if (head != null && head.amount > 0 && head.getFluid() != resource.getFluid()) {
                        return 0;
                    }
                    if (!doFill) {
                        // simulate：按真值域给预计收量，不触碰任何一侧
                        final long room = Math.max(0L, fluidTankCapacity() - Math.max(0L, tankTruth[tank]));
                        return (int) Math.min((long) resource.amount, room);
                    }
                    return fillOwnTank(tank, resource);
                }

                @Override
                public FluidStack drain(int maxDrain, boolean doDrain) {
                    final FluidStack head = tankFluid[tank];
                    if (head == null || head.amount <= 0 || maxDrain <= 0) {
                        return null;
                    }
                    final Fluid fluid = head.getFluid();
                    final long available = Math.min((long) maxDrain, Math.max(0L, tankTruth[tank]));
                    if (available <= 0L) {
                        return null;
                    }
                    if (!doDrain) {
                        return new FluidStack(fluid, (int) available);
                    }
                    final int moved = drainOwnTank(tank, maxDrain);
                    return moved <= 0 ? null : new FluidStack(fluid, moved);
                }
            };
        }
        this.essence = PocketEssenceStore.readFrom(new NBTTagCompound());
        this.bindings = PocketCellBindings.readFrom(new NBTTagCompound());
        this.filters = PocketFilterConfig.readFrom(new NBTTagCompound());
    }

    /**
     * ★R95 S5：tank 头层的 setter 钩子（{@link FluidStackTank} 构造子接线）。★<b>R95-W1（审查修复）
     * 改口，如实口径如下</b>：本钩子<b>只捕获「整替换」写</b>——头类字节码实证，其 {@code fill} 的
     * 非空同流体合并支与 {@code drain} 的部分抽出支都是对 getter 返回栈的直接 {@code amount ±=}，
     * <b>不经过这里</b>（只有空灌入与抽空置 null 才 {@code setter.accept}）。增量旁路由构造期的
     * fill/drain 覆写收进真值域原语（见构造函数注释），所有头 amount 的落钳统一在 {@link #syncHead(int)}
     * ——本方法<b>不再是、也不可能是全部头写入的总进口</b>（旧承诺与字节码不符，已收回）。仍保留的算术：
     * <ol>
     * <li>按「新头值 − 旧头值（= min(旧真值, int 顶)）」的<b>差额</b>推进真值（整替换语义下差额即
     * 真值的增减）；</li>
     * <li>存回头引用后经 {@link #syncHead(int)} 把头钳回不变式。</li>
     * </ol>
     * 旧档/未升级口袋：真值恒 ≤ 16M ≤ int 顶 ⇒ 头逐字镜像真值，行为与 R95 之前<b>逐字相同</b>。
     */
    private void applyHeadWrite(int tank, FluidStack fluid) {
        final long incoming = fluid == null || fluid.amount <= 0 ? 0L : fluid.amount;
        final long before = tankTruth[tank];
        tankTruth[tank] = Math.max(0L, before + (incoming - headAmountOf(before)));
        this.tankFluid[tank] = incoming <= 0L ? null : fluid;
        syncHead(tank);
        this.dirty = true;
    }

    /**
     * ★★<b>R95-W1（审查修复）：「真值 → 头」回同步的唯一落点</b>——双轨不变式 {@code 头 ≡ min(真值,
     * Integer.MAX_VALUE)} 的钳制只在这一处执行（外加 {@link #loadTanks}/{@link #saveTanks} 两侧对
     * 档案的读钳/写前防御钳）。任何<b>直接推进真值</b>的原语（{@code fillOwnTank}/{@code drainOwnTank}
     * 及其 long 版）写完真值必须<b>立即</b>调用本方法，不依赖 {@link #applyHeadWrite(int, FluidStack)}
     * 的 setter 钩子捕获（该钩子只捕获「整替换」写，见其 javadoc）；头轨永不充当存储轨。
     */
    private void syncHead(int tank) {
        final long truth = Math.max(0L, tankTruth[tank]);
        final FluidStack head = this.tankFluid[tank];
        if (truth <= 0L) {
            this.tankFluid[tank] = null;
        } else if (head != null) {
            head.amount = headAmountOf(truth);
        }
    }

    /** 双轨不变式的钳制算式：<b>头 ≡ min(真值, Integer.MAX_VALUE)</b>（唯一落点）。 */
    private static int headAmountOf(long truth) {
        return (int) Math.min(truth, Integer.MAX_VALUE);
    }

    /**
     * 打开会话：<b>整个面板生命周期内只调用一次</b>（R53c 第 2 条）。
     *
     * @param root 口袋物品的 NBT 根；{@code null}（新物品）即全空会话
     */
    public static PocketInventory readFrom(NBTTagCompound root) {
        final PocketInventory inventory = new PocketInventory();
        if (root == null) {
            return inventory;
        }
        // ★R95 S5：升级位探针先于一切内容读取播种——tank 头容量、源质每格上限、存储格堆叠上限
        // 三处都按它现读；捕获的 root 是<b>活实例</b>（install 原地写位图 ⇒ 会话期内固化即生效）。
        inventory.capacityProbe = () -> PocketUpgrades.hasUpgrade(root, PocketUpgradeType.CAPACITY);
        inventory.stackProbe = () -> PocketUpgrades.hasUpgrade(root, PocketUpgradeType.STACK);
        inventory.syncEssenceCapProbe();
        // ★R92-④：读档不再需要专门的闭闸——"放置即配置"的准入信号是 isItemValid 登记的<b>意图</b>，
        // 而 loadGroup 走 setStackInSlot、★不经过 isItemValid ⇒ 新建的 inventory 意图恒空，读档必然不定档
        // （理由与残余窗口见 placementIntentSlot 的 javadoc；用例 ghost_memory_placement_declares 钉这条）。
        loadGroup(root, PocketConstants.ITEM_CONTENTS, inventory.storage, "中栏");
        loadGroup(root, PocketConstants.FLUID_INTERACTION_SLOTS, inventory.fluidInteraction, "流体交互格");
        loadGroup(root, PocketConstants.DISTILL_INPUT_SLOTS, inventory.distillInput, "蒸馏输入");
        loadGroup(root, PocketConstants.BIND_SLOT, inventory.bindSlot, "绑定格");
        loadGroup(root, PocketConstants.UPGRADE_SLOT_GROUP, inventory.upgradeCells, "升级插件格");
        inventory.loadTanks(root);
        // ★R87-f：声明表必须先于源质表读出——保格谓词以它为输入，「有格位无库存」的空洞折叠只对无声明者生效
        inventory.filters = PocketFilterConfig.readFrom(root);
        // ★R95 S5：源质表读档带动态每格上限（STACK 位在 ⇒ 读档钳制按 4096；单源见 PocketEssenceStore#setCapPerTag）
        inventory.essence = PocketEssenceStore.readFrom(
            root,
            tag -> PocketEssenceIntake.isDeclaredEssenceTag(inventory.filters, tag),
            inventory::essenceCapPerTag);
        inventory.bindings = PocketCellBindings.readFrom(root);
        // ★R90 S1：读档即持久化边界 —— 换上的这份 store 就是新基线，增量日志从零起算
        inventory.clearEssenceDeltas();
        // 读档过程本身不算"内容变了"（handler 反序列化会回调脏标记）
        inventory.dirty = false;
        return inventory;
    }

    /**
     * 关屏写档（<b>仅服务端</b>被调用；见 {@code NekoPocketContainer.onModularContainerClosed}）。
     * <p>
     * 空区一律 {@code removeTag} 而不是写空 compound：口袋会跟着玩家到处走，留空壳档会让
     * {@code detectAndSendChanges} 的 NBT 比较与存档体积都白付一遍。
     * <p>
     * ★写档<b>一律按新形状</b>（R75 的存档兼容口径）：中栏写出 135 格的 {@code Size}，
     * 流体写出 {@link PocketConstants#FLUID_BAR_TANK} 编号的列表；读侧的旧形状兼容只在
     * {@link #readFrom} 那一边。
     */
    public void writeTo(NBTTagCompound root) {
        if (root == null) {
            return;
        }
        saveGroup(root, PocketConstants.ITEM_CONTENTS, storage);
        saveGroup(root, PocketConstants.FLUID_INTERACTION_SLOTS, fluidInteraction);
        saveGroup(root, PocketConstants.DISTILL_INPUT_SLOTS, distillInput);
        saveGroup(root, PocketConstants.BIND_SLOT, bindSlot);
        saveGroup(root, PocketConstants.UPGRADE_SLOT_GROUP, upgradeCells);
        saveTanks(root);
        essence.writeTo(root);
        bindings.writeTo(root);
        filters.writeTo(root);
        // ★R90 S1：写档即持久化边界 —— 内存与 NBT 重新一致，增量日志清零（否则终态回滚会把
        // 已落盘的那部分变更也逆施掉，凭空造回点数）
        clearEssenceDeltas();
    }

    /**
     * 读一个槽组。<b>不用</b> {@code handler.deserializeNBT}，理由是硬性的（不是风格）：
     * <p>
     * 上游那份实现开头就 {@code setSize(nbt.getInteger("Size"))}，而 {@code setSize} 会把整个
     * {@code stacks} 列表<b>换成一个新数组</b>（字节码实证：{@code Arrays.fill} + {@code Arrays.asList}
     * 后 {@code putfield}）⇒ 拿旧档（{@code Size=128}）读进 135 格的 handler，会把 handler <b>缩成 128 格</b>，
     * 而 Container 那边 150 个 {@code ModularSlot} 仍会去点 128…149 号（★R80 后同理：旧档 135…149 号会点到只有 135 格的 handler） ⇒
     * {@code validateSlotIndex} 抛越界，玩家侧表现为"点后面几格没反应 / 面板炸"。
     * 本方法因此：① 忽略 {@code Size}（格数永远由构造期决定 ⇒ <b>只增不减</b>）；
     * ② 越出当前格数的条目<b>丢弃并一次性 WARN</b>（旧实现是静默跳过 = 丢件无痕）。
     */
    private static void loadGroup(NBTTagCompound root, String key, ItemStackHandler handler, String label) {
        if (!root.hasKey(key)) {
            return;
        }
        final NBTTagCompound group = root.getCompoundTag(key);
        if (group == null) {
            return;
        }
        final NBTTagList items = group.getTagList(LIB_ITEMS, LIB_TAG_COMPOUND);
        int dropped = 0;
        for (int i = 0; i < items.tagCount(); i++) {
            final NBTTagCompound entry = items.getCompoundTagAt(i);
            final int slot = entry.getInteger(LIB_SLOT);
            if (slot < 0 || slot >= handler.getSlots()) {
                dropped++;
                continue;
            }
            final ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
            if (stack == null) {
                continue;
            }
            if (entry.hasKey(LIB_COUNT, LIB_TAG_INT)) {
                stack.stackSize = entry.getInteger(LIB_COUNT);
            }
            handler.setStackInSlot(slot, stack);
        }
        if (dropped > 0) {
            warnOutOfRangeOnce(label, key, handler.getSlots(), dropped);
        }
    }

    /**
     * "越界槽号被丢弃"的一次性 WARN（R75：不许静默丢件）。
     * <p>
     * ★R80① 将闩从"整进程一次"改为"<b>每个槽组键一次</b>"：收缩场景（150 → 135）下同一枚口袋
     * 一次读档就可能让 {@code contents}、{@code interactionSlots} 两个区各自丢条目，
     * 整进程只报一次就等于第二个区<b>静默丢件</b>（用户那句"不许静默丢件"不许）。
     * 仍然不会成为日志洪水：键集合是固定的五个区（R95 起含升级插件格）+ 流体 tank，面板反复开关也只各报一次。
     */
    private static void warnOutOfRangeOnce(String label, String key, int slots, int dropped) {
        if (!outOfRangeWarnedKeys.add(key)) {
            return;
        }
        GTInterestingThing.LOG.warn(
            "[pocket] 存档里 {}（键 {}）有 {} 条槽号越出当前形状（现有 {} 格），已丢弃这些条目" + "（面板形状变更后的旧/外来档；本条按区只报一次）",
            label,
            key,
            dropped,
            slots);
    }

    /** 越界槽号 WARN 的"每个区一次"闩（★R80①；用 LinkedHashSet 保首次出现的顺序，日志可读）。 */
    private static final java.util.Set<String> outOfRangeWarnedKeys = new java.util.LinkedHashSet<>();

    /**
     * 读 {@link PocketConstants#FLUID_BAR}：两代形状都在这里分流（见该键的 javadoc）。
     * <p>
     * 旧档的单 compound ⇒ <b>整份落到 0 号 tank</b>；新档的列表 ⇒ 按 {@link PocketConstants#FLUID_BAR_TANK}
     * 归位。缺 tank 键按 0 读（与旧档同义）并计入一次性 WARN；tank 号越界的条目丢弃（同一个 WARN）。
     * <p>
     * ★R95 S5：每条目<b>优先读 {@link PocketConstants#FLUID_BAR_AMOUNT_L}</b>（long 真值），无该键的
     * 老档回退 {@code FluidStack} 自带的 {@code Amount}（int 头值）——16G 之前的老档真值 ≤ 16M ≤ 头值，
     * 回退零损失；AmountL 比 Amount 小的脏档按<b>头值</b>收口（头是真值的最小可信下界，宁多不吞）。
     */
    private void loadTanks(NBTTagCompound root) {
        if (!root.hasKey(PocketConstants.FLUID_BAR)) {
            return;
        }
        if (root.hasKey(PocketConstants.FLUID_BAR, LIB_TAG_LIST)) {
            final NBTTagList list = root.getTagList(PocketConstants.FLUID_BAR, LIB_TAG_COMPOUND);
            int dropped = 0;
            for (int i = 0; i < list.tagCount(); i++) {
                final NBTTagCompound entry = list.getCompoundTagAt(i);
                int tank = 0;
                if (entry.hasKey(PocketConstants.FLUID_BAR_TANK, LIB_TAG_INT)) {
                    tank = entry.getInteger(PocketConstants.FLUID_BAR_TANK);
                } else {
                    dropped++;
                }
                if (tank < 0 || tank >= FLUID_TANK_COUNT) {
                    dropped++;
                    continue;
                }
                final FluidStack fluid = FluidStack.loadFluidStackFromNBT(entry);
                if (fluid != null && fluid.amount > 0) {
                    tankFluid[tank] = fluid;
                    // ★R95 S5 双轨：真值优先 AmountL；头值同步钳到不变式（老档 Amount 即真值）
                    final long head = fluid.amount;
                    final long truth = entry.hasKey(PocketConstants.FLUID_BAR_AMOUNT_L)
                        ? Math.max(head, entry.getLong(PocketConstants.FLUID_BAR_AMOUNT_L))
                        : head;
                    tankTruth[tank] = truth;
                    fluid.amount = headAmountOf(truth);
                }
            }
            if (dropped > 0) {
                GTInterestingThing.LOG
                    .warn("[pocket] 流体档里有 {} 条缺 tank 号或越出 {} 个 tank 的范围（已按 0 号 tank 或丢弃处理）", dropped, FLUID_TANK_COUNT);
            }
            return;
        }
        if (root.hasKey(PocketConstants.FLUID_BAR, LIB_TAG_COMPOUND)) {
            final FluidStack legacy = FluidStack.loadFluidStackFromNBT(root.getCompoundTag(PocketConstants.FLUID_BAR));
            if (legacy != null && legacy.amount > 0) {
                tankFluid[0] = legacy;
                tankTruth[0] = legacy.amount;
            }
        }
    }

    /** {@link #saveTanks} 里"真值超头 ⇒ 旧版降级读会丢超头部分"的一次性 WARN 闩（形状级问题，按进程一次）。 */
    private static boolean downgradeWarned;

    /** 写档：只写非空 tank，每条自带 tank 号；全空即 {@code removeTag}。 */
    private void saveTanks(NBTTagCompound root) {
        final NBTTagList list = new NBTTagList();
        boolean overHead = false;
        for (int tank = 0; tank < FLUID_TANK_COUNT; tank++) {
            final FluidStack fluid = tankFluid[tank];
            if (fluid == null || fluid.amount <= 0 || tankTruth[tank] <= 0) {
                continue;
            }
            final NBTTagCompound entry = new NBTTagCompound();
            // ★R95 S5 双写：Amount（int 头值，FluidStack.writeToNBT 原样写出 = 旧版读侧来源）
            // + AmountL（long 真值，本版起的权威）。写前把头的 amount 对齐不变式（防御头被外部直改）。
            fluid.amount = headAmountOf(tankTruth[tank]);
            fluid.writeToNBT(entry);
            entry.setLong(PocketConstants.FLUID_BAR_AMOUNT_L, tankTruth[tank]);
            if (tankTruth[tank] > fluid.amount) {
                overHead = true;
            }
            entry.setInteger(PocketConstants.FLUID_BAR_TANK, tank);
            list.appendTag(entry);
        }
        if (overHead && !downgradeWarned) {
            downgradeWarned = true;
            GTInterestingThing.LOG
                .warn("[pocket] 流体条真值超过 int 头（16G 档）：已双写 Amount（头值）与 AmountL（真值），" + "旧版本 jar 读这份档只能看到头值部分（本条只报一次）");
        }
        if (list.tagCount() == 0) {
            root.removeTag(PocketConstants.FLUID_BAR);
            return;
        }
        root.setTag(PocketConstants.FLUID_BAR, list);
    }

    private static void saveGroup(NBTTagCompound root, String key, ItemStackHandler handler) {
        // 只在"真的有货"时落档；空区一律 removeTag（不依赖 handler 自己的 NBT 内部键名形状）
        if (!hasAnyStack(handler)) {
            root.removeTag(key);
            return;
        }
        final NBTTagCompound group = handler.serializeNBT();
        if (group != null) {
            root.setTag(key, group);
        }
    }

    private static boolean hasAnyStack(ItemStackHandler handler) {
        for (int index = 0; index < handler.getSlots(); index++) {
            if (handler.getStackInSlot(index) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 带脏标记回调的槽组（{@code ItemStackHandler} 的 {@code onContentsChanged} 是唯一能同时覆盖
     * 「玩家点击」「{@code putStack} 同步」「程序内 insert/extract」三条写入路径的钩子）。
     */
    private ItemStackHandler newSlotGroup(final int size) {
        return new ItemStackHandler(size) {

            @Override
            protected void onContentsChanged(int slot) {
                PocketInventory.this.dirty = true;
            }
        };
    }

    /**
     * 中栏那一组专用：除脏标记外，还要把"绑定格不接受玩家放入"落到<b>执法点</b>上。
     * <p>
     * ★为什么不复用 {@code NekoFilterSlot.setGhost} 那条（它改的是 {@code ModularSlot.canPut}）：
     * ghost 视图只在客户端装配尾部按 blob 刷一遍（{@code NekoPocketPanel#assemble} 的
     * {@code if (syncManager.isClient())} 分支），服务端那份 {@code applyGhosts()} 只在收到
     * ghost 请求后才跑 ⇒ <b>重开 GUI 时存档里已有的 ghost 格在服务端仍是 canPut=true</b>，
     * 玩家点击能直接往虚化格里放件，而"绑定格不参与整理/落位"的口径也会连带被突破。
     * 拦在 handler 这一层则<b>开屏即生效</b>（判据现读 {@link #filters()}），且不需要面板配合。
     * <p>
     * 执法面为什么够用：{@code ModularSlot.isItemValid} 的最后一环就是
     * {@code super.isItemValid} → {@code SlotItemHandler.isItemValid} →
     * {@code itemHandler.isItemValid(index, stack)}（MUI2 {@code utils/item/SlotItemHandler.java:27-42}），
     * 而原版 {@code Container} 的六条放入/搬运分支（点击放入、Shift 快速移动、拖拽分堆的两次扫描、
     * 连点收集：{@code net/minecraft/inventory/Container.java:180,197,314,349,410,424,441}）
     * 全都只看 {@code Slot.isItemValid}。程序化写入（{@code insertItem}/{@code setStackInSlot}）
     * 不经过这里 —— 那是通道回写与 ghost 搬空自己要用的路，ghost 格的跳过判定另有
     * {@link #depositIntoStorage(ItemStack)} 那一道显式判据。
     */
    private ItemStackHandler newStorageGroup(final int size) {
        return new ItemStackHandler(size) {

            @Override
            protected void onContentsChanged(int slot) {
                PocketInventory.this.dirty = true;
                // ★★R92-④（D4）：记忆档（L）的<b>空格</b>被放进东西 ⇒ 以那一件的实际内容为本格建档。
                // ★判据一条都不在这里重写：attr 走单源 accessor、写入走 PocketFilterConfig#declare。
                PocketInventory.this.declareMemoryFromPlacement(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                // ★R88 B4（实机缺陷 1 的入口侧收口）：口袋自身不得进自家存储格。放进去之后，关屏时
                // "承载口袋的栈"就在这个格里，三级归还（原格／按对象身份／自嵌）全落空 ⇒ 扣点只能 WARN
                // 不落盘。E2 已给 writeSessionToCarrier 补了两档兜底，但兜底的前提是"事后找得到"，
                // 入口放开就是继续留一条"能不能落盘看运气"的路。程序化写入（通道回写／ghost 搬空）
                // 不经过 isItemValid，因此不受本条影响。
                if (stack != null && stack.getItem() instanceof ItemNekoDimensionPocket) {
                    return false;
                }
                // ★★R92-④：定档的<b>唯一</b>准入信号就是这一行——本方法是原版六条玩家放入分支共同的闸，
                // 而整理 / 产物重塞 / 拉取落点 / 读档四条程序化路径★都不经过这里。
                // ★必须排在下面那条 {@code !isGhostItemSlot} 早退<b>之前</b>：记忆档 pending 格按定义
                // 就是"没有声明"的格，走到早退就再也拿不到这次机会了。
                PocketInventory.this.recordPlacementIntent(slot, stack);
                if (!isGhostItemSlot(slot)) {
                    return true;
                }
                // ★★<b>R91-⑤ 的 L 执法腿</b>（记忆 = "本格只能放该种东西"）。三条口径：
                // ① 判据<b>单源</b>在 PocketFilterConfig#allowsPlayerPlacement —— 本方法<b>不含第二份
                // 位表实现</b>，只经那一条 accessor 问 attr（★验收判据"attr 查询走单源 accessor"）；
                // ② ★只约束「放入本格」这一条写入口：普通格照旧可放、BIND / 无属性的声明格照旧<b>禁放</b>
                // （R84 的"声明格是抽取落点、不是玩家输入口"一字不改），L 只是把"这一格允许放那一种"
                // 的场合里<b>放错东西</b>的那一击挡掉；
                // ③ ★补货行为仍只认 BIND（见 PocketChannelRunner#refillPhase 的闸门）——L 纯过滤、
                // 不参与抽取，否则等于替玩家发明需求外的自动拉货。
                // 成本如实说明：只在"声明格 + 有人往它放东西"这一条支上算一次 contentKey（含 NBT base64），
                // ★不在每拍、也不在未声明的 135 格上算 ⇒ 与 NEI 拖入同一只键函数、不造第二份键式样。
                final String contentKey = stack == null ? "" : PocketAeChannelOps.contentKey(stack);
                return filters.allowsPlayerPlacement(PocketFilterConfig.Kind.ITEM, slot, contentKey);
            }

            /**
             * ★R95 S5（STACK 位）：中栏槽位上限两档——未升级 64（上游默认，现状逐字不变）、
             * 升级 1024（= 64 × 16）。{@code insertItem} 与 vanilla 槽交互都经
             * {@link #getStackLimit(int, ItemStack)}（其内调本方法）⇒ 覆写这两点即覆盖全部写入面。
             */
            @Override
            public int getSlotLimit(int slot) {
                return storageStackUpgraded() ? PocketConstants.STORAGE_SLOT_LIMIT_UPGRADED
                    : PocketConstants.STORAGE_SLOT_LIMIT_BASE;
            }

            /**
             * ★R95 S5（STACK 位）：单格可叠上限的<b>唯一执法点</b>——算式单源在
             * {@link #effectiveStorageLimit(boolean, ItemStack)}（通道消费侧与 ghost 读数侧共读它，
             * 本文件不含第二份三元）。
             */
            @Override
            protected int getStackLimit(int slot, ItemStack stack) {
                return PocketInventory.effectiveStorageLimit(storageStackUpgraded(), stack);
            }
        };
    }

    /**
     * ★R95 S5：<b>STACK 位是否固化</b>（存储格堆叠 ×16 与源质每格上限 256→4096 共用这一位）。
     * handler 内部读点（{@code getSlotLimit}/{@code getStackLimit}）与源质上限选择都经它。
     */
    boolean storageStackUpgraded() {
        return stackProbe.getAsBoolean();
    }

    /**
     * ★R95 S5：中栏单格<b>可叠上限的单源算式</b>。
     * <ul>
     * <li><b>未升级</b> = {@code min(64, maxStackSize)}（= 上游 {@code ItemStackHandler#getStackLimit}
     * 的既有行为，逐字不变）；</li>
     * <li><b>升级后</b> = {@code min(1024, maxStackSize == 1 ? 1 : maxStackSize × 16)}——不可叠物品
     * （max=1）保持 1，其余 ×16 后以 1024 封顶。乘法按 long 做（防超大 maxStackSize 的 int 溢出）。</li>
     * </ul>
     * 消费点三处共读：本类的 {@code getStackLimit} 覆写、{@code PocketAeChannelOps#extractItem} 的
     * room/wantedSize/mergeInto 钳、{@code NekoFilterSlot#naturalMaxStackSize} 的显示回落。
     *
     * @param stack 待判栈；{@code null} ⇒ 0（与上游 {@code getStackLimit} 同一返回口径）
     */
    public static int effectiveStorageLimit(boolean stackUpgraded, ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        final int max = stack.getMaxStackSize();
        if (!stackUpgraded) {
            return Math.min(PocketConstants.STORAGE_SLOT_LIMIT_BASE, max);
        }
        if (max == 1) {
            return 1;
        }
        return (int) Math.min(
            (long) PocketConstants.STORAGE_SLOT_LIMIT_UPGRADED,
            (long) max * PocketConstants.UPGRADE_STACK_MULTIPLIER);
    }

    /**
     * ★R95 S5：CAPACITY 位是否固化（16M/16G 的选择输入；GUI 装配侧的容量读数也经它）。
     */
    boolean capacityUpgradeActive() {
        return capacityProbe.getAsBoolean();
    }

    /**
     * ★R95 S5：单 tank 容量（mB，long）——{@link PocketConstants#fluidTankCapacityMl(boolean)} 的
     * 本实例读法（探针默认 false ⇒ 未升级口径）。
     */
    long fluidTankCapacity() {
        return PocketConstants.fluidTankCapacityMl(capacityUpgradeActive());
    }

    /** ★R95 S5：源质每格上限的本实例读法（STACK 位在 ⇒ 4096；喂给 {@link PocketEssenceStore} 的动态上限）。 */
    int essenceCapPerTag() {
        return PocketConstants.essenceCapPerTag(storageStackUpgraded());
    }

    /**
     * ★R95 S5：升级位探针的<b>注入点</b>（GUI 装配侧接线）。
     * <p>
     * 本类是纯数据件（只面对 NBT），结构性拿不到载体栈；{@code readFrom} 已用<b>档内位图</b>自播种
     * （活 NBT 实例 ⇒ 会话期内 install 写位图后下一次查询即生效），但"口袋原本无 NBT、会话期内才
     * 第一次装插件"的那一支没有根实例可捕获 ⇒ 面板构造完 {@code readFrom} 后应即时注入
     * 「活查载体栈」版探针（先例：{@code NekoPocketPanel#carrierStackLive} 那条活查表通道）：
     *
     * <pre>
     * {@code
     * inventory.setUpgradeProbes(
     *     () -> PocketUpgrades.hasUpgrade(carrierStackLive(), PocketUpgradeType.CAPACITY),
     *     () -> PocketUpgrades.hasUpgrade(carrierStackLive(), PocketUpgradeType.STACK));
     * }
     * </pre>
     *
     * <p>
     * {@code null} 入参视为回落默认（false）。注入同时把源质表的动态上限一并接上（单源转发）。
     */
    public void setUpgradeProbes(java.util.function.BooleanSupplier capacity,
        java.util.function.BooleanSupplier stack) {
        this.capacityProbe = capacity == null ? () -> false : capacity;
        this.stackProbe = stack == null ? () -> false : stack;
        syncEssenceCapProbe();
    }

    /** 把 {@link #stackProbe} 接到源质表的动态每格上限（读档、注入、换探针后都要重接一次）。 */
    private void syncEssenceCapProbe() {
        if (essence != null) {
            essence.setCapPerTag(this::essenceCapPerTag);
        }
    }

    /**
     * 升级插件格那一组专用（R95，第 5 组）：{@link PocketConstants#UPGRADE_SLOTS} 格，
     * <b>槽号 = 效果位图位 = {@code PocketUpgradeType#ordinal()}</b>——三个空间共用同一套下标，
     * 这是"第 N 格只收第 N 型插件"判据能单源成立的原因。
     * <p>
     * ★准入判据单源在 {@link #acceptsUpgradeCell(int, ItemStack)}（本 handler 的
     * {@code isItemValid} 与 GUI 装配侧 {@code PocketSlots#upgradeCell} 的 {@code filter}
     * 都只经它问，<b>不含第二份 instanceof/ordinal 实现</b>；执法放 handler 这一层则开屏即生效，
     * 口径与 {@link #newStorageGroup(int)} 把"绑定格不放"落在执法点的论证同一条）。
     * <p>
     * ★★<b>固化写点（install 的调用点）归 GUI 装配片 S4，本片不落调用</b>：本类是纯数据件
     * （类 javadoc 明写"不持 EntityPlayer、不持 World"），<b>结构性拿不到口袋载体栈</b>——
     * 效果位图写在「承载口袋的那一栈」的 NBT 根层，而 {@code readFrom/writeTo} 只面对
     * NBT 化合物，无从回指栈本体。S4 的落点设计（本片按此移交）：
     * 在 {@code gui.pocket.PocketSlots#upgradeCell} 造出的槽件上挂服务端 changeListener
     * （先例：流体交互格 {@code fluidInteraction} 的 {@code changeListener}，签名
     * {@code (newItem, onlyAmountChanged, client, init) -> { if (client || init) return; ... }}），
     * {@code newItem != null} 时以 {@code PlayerInventoryGuiData#getUsedItemStack()} 取载体
     * （同 {@code NekoPocketPanel} 构造器取 {@code this.pocket} 的那条路），调
     * {@code PocketUpgrades.install(载体, PocketUpgradeType.values()[slotIndex])}。
     * 一格一型 ⇒ 放对格才进得来（准入判据先挡），放进来即固化（install 只置不清，天然不可逆）。
     */
    private ItemStackHandler newUpgradeGroup(final int size) {
        return new ItemStackHandler(size) {

            @Override
            protected void onContentsChanged(int slot) {
                PocketInventory.this.dirty = true;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return acceptsUpgradeCell(slot, stack);
            }
        };
    }

    /**
     * 升级插件格的准入判据（<b>单源</b>，R95）：第 {@code slot} 格只收
     * {@code type().ordinal() == slot} 的那一件插件。
     *
     * @return {@code true} 仅当 {@code stack} 是 {@link ItemPocketUpgrade} 且其效果位恰好是这一格
     */
    public static boolean acceptsUpgradeCell(int slot, ItemStack stack) {
        if (slot < 0 || slot >= PocketConstants.UPGRADE_SLOTS) {
            return false;
        }
        final PocketUpgradeType type = ItemPocketUpgrade.getType(stack);
        return type != null && type.ordinal() == slot;
    }

    /**
     * ★★<b>R92-④（D4）：物品支"放置即配置"的落档腿</b>。
     * <p>
     * 用户口径："Alt 锁定后，可以用 NEI 配置锁定，也可以玩家直接放东西上去配置"。取证证死现状是
     * <b>没被拦、但根本没有配置口</b>：全仓只有 NEI 拖入与手势两条路会写声明，放置/灌流体/入瓶三条
     * 路径一行都不写 ⇒ 记忆档空格永远停在 pending（有 L、无载荷），玩家看着"锁上了却配不了"。
     * <p>
     * 四条口径（★与另两条腿逐字同形，判据只在 {@link PocketFilterConfig} 那一侧）：
     * <ol>
     * <li>★<b>只开 MEMORY 档</b>：NONE / BIND 格放置既不放行（{@code allowsPlayerPlacement} 那条门未动）
     * 也不产生声明；</li>
     * <li>★<b>只补 pending</b>：该格已有声明 ⇒ 一个字都不改（P2 裁定：换声明走 NEI 拖入或手势，
     * 放置不覆盖）⇒ "放错东西就把配置改了"这条误操作面不存在。★这两条合成一条判据、单源在
     * {@link PocketFilterConfig#memoryPendingForPlacement} —— 本类<b>不直接读 attr 位表</b>，
     * 与 {@code allowsPlayerPlacement} 同一条纪律（★V/用例都钉着"本文件 {@code attrAt(} 命中恒 0"）；</li>
     * <li>载荷键由<b>服务端自己</b>从格内实栈算出（{@link PocketAeChannelOps#contentKey}，与
     * {@code isItemValid} 那一条同一只键函数）⇒ 不存在伪造输入，也不需要 C2S 那四道请求侧门；</li>
     * <li>★程序化写入面（读档 / 整理 / 产物重塞 / 拉取落点）<b>结构性拿不到意图</b>：它们都不经过
     * {@code isItemValid}，而意图是唯一的准入信号（★审查 B2 修，详见 {@link #placementIntentSlot}）。</li>
     * </ol>
     * 成本如实说明：只在"该格处于记忆档 pending"成立时才走，★不在 135 格每次变化上算 base64
     * （准入判据是一次 map 取值，键计算排在它后面）。
     */
    private void declareMemoryFromPlacement(int slot) {
        final int intentSlot = placementIntentSlot;
        final String intentKey = placementIntentKey;
        // ★一次性：无论这次消费不消费都清掉，绝不留悬垂意图给下一次写入
        placementIntentSlot = -1;
        placementIntentKey = null;
        if (slot != intentSlot || intentKey == null || intentKey.isEmpty()) {
            return;
        }
        if (!filters.memoryPendingForPlacement(PocketFilterConfig.Kind.ITEM, slot)) {
            return;
        }
        final ItemStack placed = storage.getStackInSlot(slot);
        if (placed == null || placed.stackSize <= 0) {
            return;
        }
        // ★落进来的必须<b>就是</b>玩家那一次要放的东西：不等说明这一格是被程序化落点填的
        final String actualKey = PocketAeChannelOps.contentKey(placed);
        if (!intentKey.equals(actualKey)) {
            return;
        }
        final PocketFilterConfig.Filter payload = PocketFilterConfig.parseKey(actualKey);
        // declare 内部会复核"这一格现在到底是不是这条"，失败返回 null ⇒ 本方法不留半档
        filters.declare(PocketFilterConfig.Kind.ITEM, slot, payload);
    }

    /**
     * ★R92-④：登记"玩家这一次确实想把 {@code stack} 放进 {@code slot}"（★只在记忆档 pending 格上做）。
     * <p>
     * 成本如实说明：普通格与已声明格<b>一次键计算都不做</b>（判据是一次 map 取值，{@code contentKey}
     * 含 NBT base64，排在它后面），★不在每拍、也不在未声明的 135 格上算。
     */
    private void recordPlacementIntent(int slot, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return;
        }
        if (!filters.memoryPendingForPlacement(PocketFilterConfig.Kind.ITEM, slot)) {
            return;
        }
        placementIntentSlot = slot;
        placementIntentKey = PocketAeChannelOps.contentKey(stack);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void markClean() {
        this.dirty = false;
    }

    /** 中栏 135 格（行主序 0..134，与 {@code SlotGroupWidget} 矩阵的字符序天然一致）。 */
    public ItemStackHandler storage() {
        return storage;
    }

    /** 流体列的 12 个同权交互格（6 列 × 输入/输出；tank 号见 {@link #tankOfInteractionSlot(int)}）。 */
    public ItemStackHandler fluidInteraction() {
        return fluidInteraction;
    }

    /** 右栏 12 个蒸馏输入格（R44c 的入口拒容器在 {@code gui.pocket.PocketSlots} 的槽过滤里）。 */
    public ItemStackHandler distillInput() {
        return distillInput;
    }

    /** 底部带绑定格（1 格，瞬时入口）。 */
    public ItemStackHandler bindSlot() {
        return bindSlot;
    }

    /**
     * 升级插件格（R95：{@link #UPGRADE_SLOTS} 格；槽号=位图位=ordinal，准入判据
     * {@link #acceptsUpgradeCell(int, ItemStack)}，固化写点设计见 {@link #newUpgradeGroup(int)}）。
     */
    public ItemStackHandler upgradeGroup() {
        return upgradeCells;
    }

    public PocketEssenceStore essence() {
        return essence;
    }

    // ------------------------------------------------------------------ ★R90 S1：源质增量日志 API（D2 修复面）
    //
    // 语义 = 「可逐 tag 逆施」：任意时刻读出的日志，逐条按相反符号施回 store（负 ⇒ add 回补、
    // 正 ⇒ extract 回扣），内存源质即回到上一次持久化边界（读档完成 / writeTo 成功）的基线。
    // ± 两侧的登记点：Panel 可达的三个点（drainEssence 扣点、performEssenceOut 取出与退点）走
    // recordEssenceDelta 显式打点；通道下传 / 蒸馏 / 注入支直改 store 的路径由 reconcile 对表兜底。

    /**
     * 显式登记一笔源质增减（tag，代数和累计）。
     * <p>
     * ★契约：调用方<b>刚在 {@link #essence()} 上造成了一笔等量变更</b>（如 {@code extract} 实移除
     * {@code removed} 点后登记 {@code -removed}）。登记的同时把对表基点前移同一笔，保证
     * {@link #reconcileEssenceDeltas()} 不会把同一笔变更二次并入。高频率事件 ⇒ L11 打点走 debug 级。
     */
    public void recordEssenceDelta(String tag, int delta) {
        if (tag == null || tag.isEmpty() || delta == 0) {
            return;
        }
        mergeEssenceDelta(tag, delta);
        final int seen = essenceLastSeen.get(tag) == null ? 0 : essenceLastSeen.get(tag);
        essenceLastSeen.put(tag, seen + delta);
        GTInterestingThing.LOG
            .debug("[PocketR89] 源质增量登记：tag={} delta={}（该 tag 累计 {}）", tag, delta, essenceDeltas.get(tag));
    }

    /**
     * 当前增量日志（只读副本；tag → 代数和，非 0 条目）。
     * <p>
     * 读取前先 {@link #reconcileEssenceDeltas()} 对表 —— 保证返回值覆盖<b>全部</b>源质增减
     * （含未打点的外部直改 store 路径），这正是 S2 终态回滚的输入。
     */
    public java.util.Map<String, Integer> essenceDeltas() {
        reconcileEssenceDeltas();
        return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(essenceDeltas));
    }

    /** 清空增量日志并把基点重置为 store 现值（持久化边界 / 回滚完成后调用）。 */
    public void clearEssenceDeltas() {
        essenceDeltas.clear();
        essenceLastSeen.clear();
        essenceLastSeen.putAll(essence.snapshot());
    }

    /**
     * 对表兜底：把「store 现值 − 基点」的漂移并入日志并重置基点。
     * <p>
     * 捕捉的是<b>未打点</b>的 store 变更 —— 通道下传入账（{@code PocketEssenceChannelOps#extractEssence}
     * 直接 {@code store.putAll}）、蒸馏入账（{@code PocketDistillDriver}）、注入支（{@code PocketSlots}）
     * 都在不可改文件里直改 store，唯有"store 本身是唯一真相"这一事实能把它们全部收进日志。
     */
    private void reconcileEssenceDeltas() {
        final java.util.Map<String, Integer> now = essence.snapshot();
        final java.util.Set<String> tags = new java.util.LinkedHashSet<>();
        tags.addAll(now.keySet());
        tags.addAll(essenceLastSeen.keySet());
        boolean drifted = false;
        for (final String tag : tags) {
            final int current = now.get(tag) == null ? 0 : now.get(tag);
            final int seen = essenceLastSeen.get(tag) == null ? 0 : essenceLastSeen.get(tag);
            final int drift = current - seen;
            if (drift != 0) {
                mergeEssenceDelta(tag, drift);
                drifted = true;
            }
        }
        essenceLastSeen.clear();
        essenceLastSeen.putAll(now);
        if (drifted) {
            // L11：对表属低频（只在读日志/落盘时发生）但可能有批量条目，仍走 debug 防刷屏
            GTInterestingThing.LOG.debug("[PocketR89] 源质增量对表：并入未打点的 store 变更（通道下传/蒸馏/注入支）");
        }
    }

    /** 按 tag 累计代数和；净 0 的条目移除（日志里恒为非 0 条目）。 */
    private void mergeEssenceDelta(String tag, int delta) {
        final int merged = (essenceDeltas.get(tag) == null ? 0 : essenceDeltas.get(tag)) + delta;
        if (merged == 0) {
            essenceDeltas.remove(tag);
        } else {
            essenceDeltas.put(tag, merged);
        }
    }

    public PocketCellBindings bindings() {
        return bindings;
    }

    public PocketFilterConfig filters() {
        return filters;
    }

    /**
     * 源质存储换实例。
     * <p>
     * ★D 批的蒸馏入账<b>不</b>走这里：{@code PocketDistillDriver} 拿的是 {@code essence()} 返回的
     * 同一实例，按 {@code canAcceptAll → putAll} 就地提交（换实例会造成"表被替换后旧引用仍被
     * 面板/通道持有着"的两处真相）。本方法保留给"整表导入/导出"一类外部操作，当前零调用方。
     */
    public void replaceEssence(PocketEssenceStore store) {
        this.essence = store == null ? PocketEssenceStore.readFrom(new NBTTagCompound()) : store;
        this.dirty = true;
        // ★R90 S1：整表换实例 ⇒ 旧表的增量与新表不可通约，基线跟着新表重置
        clearEssenceDeltas();
        // ★R95 S5：新实例要把动态每格上限探针重新接上（setCapPerTag 是实例级的）
        syncEssenceCapProbe();
    }

    /** 绑定表换实例（S6 的绑定/解绑动作写完后放回；{@code null} 视为空表）。 */
    public void replaceBindings(PocketCellBindings table) {
        this.bindings = table == null ? PocketCellBindings.readFrom(new NBTTagCompound()) : table;
        this.dirty = true;
    }

    /** ghost 配置换实例（S5 的就地转换写入；{@code null} 视为空表）。 */
    public void replaceFilters(PocketFilterConfig config) {
        this.filters = config == null ? PocketFilterConfig.readFrom(new NBTTagCompound()) : config;
        this.dirty = true;
    }

    /** 中栏指定格当前内容（可能为 {@code null}）。 */
    public ItemStack storageStack(int index) {
        return storage.getStackInSlot(index);
    }

    /**
     * 某个流体交互格属于哪个 tank（<b>唯一</b>映射点，R78② 的"组"维度收在这一个函数里）。
     * <p>
     * 矩阵按"组"顺序产出索引，一组内先铺完 6 个上格再铺 6 个下格 ⇒
     * 
     * <pre>
     *   组号   = index / FLUID_INTERACTION_PER_GROUP      （每组 12 格）
     *   组内列 = index % FLUID_COLUMN_COUNT               （0…5）
     *   tank   = 组号 × FLUID_COLUMN_COUNT + 组内列        （0…17）
     * </pre>
     * 
     * ★<b>旧档同解</b>：组数=1 时本式退化为 {@code index % 6}，与 R75 那版取模逐字一致 ⇒
     * 12 格老档的进/出格仍然落在同一批 tank，不需要任何迁移代码。
     * <p>
     * 越界入参原样返回（调用方是槽号，不该越界；真越界了就让上层的数组访问炸出来，
     * 而不是静默映射到 0 号 tank 去动别人的液体）。
     */
    public static int tankOfInteractionSlot(int interactionIndex) {
        final int group = interactionIndex / FLUID_INTERACTION_PER_GROUP;
        final int columnInGroup = interactionIndex % PocketConstants.FLUID_COLUMN_COUNT;
        return group * PocketConstants.FLUID_COLUMN_COUNT + columnInGroup;
    }

    /** 交互格属于哪一组（0…{@link PocketConstants#FLUID_GROUP_COUNT}−1；GUI 的分组渲染与 tooltip 用）。 */
    public static int groupOfInteractionSlot(int interactionIndex) {
        return interactionIndex / FLUID_INTERACTION_PER_GROUP;
    }

    /**
     * 某一组内该交互格是"上格（进）"还是"下格（出）"（★<b>不</b>改变 R39a 的双用语义，
     * 只描述它画在流体槽的哪一侧，供 tooltip 选 {@code legend.input} / {@code legend.output}）。
     */
    public static boolean isLowerInteractionRow(int interactionIndex) {
        return interactionIndex % FLUID_INTERACTION_PER_GROUP >= PocketConstants.FLUID_COLUMN_COUNT;
    }

    /**
     * 同组同列里<b>配对</b>的那一格（进 ↔ 出互换）。
     * <p>
     * ★与 {@link #tankOfInteractionSlot(int)} 同住一个文件：一格属于哪一列、与谁配对，是同一份索引真相，
     * 拆到 {@code gui.pocket.PocketSlots} 里就会长出第二处"取模口径"。
     */
    public static int partnerInteractionSlotOf(int interactionIndex) {
        final int groupStart = interactionIndex - interactionIndex % FLUID_INTERACTION_PER_GROUP;
        final int columnInGroup = interactionIndex % PocketConstants.FLUID_COLUMN_COUNT;
        return isLowerInteractionRow(interactionIndex) ? groupStart + columnInGroup
            : groupStart + PocketConstants.FLUID_COLUMN_COUNT + columnInGroup;
    }

    /**
     * 本列的<b>出格</b>（下行格）—— R83 D-2 的不对称落位里"处理产物"唯一的落点。
     * <p>
     * ★它同时是闩的作用对象：产物落进这一格后不能再被自动搬（{@code ItemSlotSH.detectAndSendChanges}
     * 每 tick 比内容 ⇒ 只挪不闩就会被反向灌回）。
     */
    public static int outputInteractionSlotOf(int interactionIndex) {
        return isLowerInteractionRow(interactionIndex) ? interactionIndex : partnerInteractionSlotOf(interactionIndex);
    }

    /** 本列的<b>进格</b>（上行格）—— R39a 的"两格同权"说的是这一格与出格<b>都可放入</b>，与落点无关。 */
    public static int inputInteractionSlotOf(int interactionIndex) {
        return isLowerInteractionRow(interactionIndex) ? partnerInteractionSlotOf(interactionIndex) : interactionIndex;
    }

    /** tank 号是否合法（GUI 与通道侧共用的这一道界）。 */
    public static boolean isValidTank(int tank) {
        return tank >= 0 && tank < FLUID_TANK_COUNT;
    }

    /**
     * 第 {@code tank} 号流体槽本体（{@code FluidStackTank}：内存 FluidStack + 只报真实容量）。
     * <p>
     * ★★R95-W1（审查修复）：它的 {@code fill/drain} 已被构造期覆写为真值域原语的转发 ⇒ 一切经本出口
     * 拿头的调用者（GUI 点击灌排、{@code PocketFluidTransfer} 容器搬运）改动的都是「真值 + 头」
     * 一对账，头轨单独漂移在结构上不可能。只读面（{@code getFluid/getFluidAmount/getCapacity/getInfo}）
     * 仍读头（不变式 头 ≡ min(真值, int 顶)）。
     * <p>
     * ★GUI 侧的 {@code FluidSlotSyncHandler} 直接挂它 ⇒ 6 个槽各有一根同步通道，
     * 上游那份 handler 只在<b>内容与缓存不等</b>时才发更新（{@code needsSync} 走
     * {@code FluidStack#isFluidEqual} + 数量比较，实证自 dev jar 字节码），
     * 不会把 {@code FluidStack} 的 NBT 塞进每 tick 包（R75 §3 的顾虑点）。
     */
    public FluidStackTank tankAt(int tank) {
        return tanks[tank];
    }

    /** tank 总数（GUI 装配循环用它，别处不得内联 6）。 */
    public static int tankCount() {
        return FLUID_TANK_COUNT;
    }

    // ------------------------------------------------------------------ S6/S7 的落点出口
    //
    // 拉取模式（需求 4）与"要素栏取出→晶化源质"（需求 2）都要往口袋里放东西，而放东西的
    // 三条纪律都只能在这一处实现：ghost 格不是落点（R38 第 2 条）、先合堆再占空槽、
    // 装不下就回报装不下（绝不凭空造件也绝不吃件）。

    /**
     * 中栏某一格是否已被就地转成 ghost 配置格。
     * <p>
     * 判据只读服务端的 {@link PocketFilterConfig}（{@code Kind.ITEM} 的槽索引集合），
     * <b>不</b>读 widget 状态 —— widget 双端各一份，读它就是把显示层当真相。
     */
    public boolean isGhostItemSlot(int index) {
        return filters.at(PocketFilterConfig.Kind.ITEM, index) != null;
    }

    /**
     * 从 {@code from} 起的第一个<b>非绑定</b>中栏槽号（整理与落点共用的游标；越界时返回格数本身，
     * 调用方按 {@code == storage().getSlots()} 判"没有可用格"）。
     * <p>
     * ★需求 5 的"绑定物品的格子除外"在服务端只有这一个判据来源（{@link #isGhostItemSlot(int)}
     * 读 {@link PocketFilterConfig}，不读 widget 状态）。中栏的"箱子式整理"是自研手势 + 自研算法，
     * 不是通用箱子整理；游标算式收在这一层，面板与落点共用一份。
     */
    public int nextSortableStorageSlot(int from) {
        final int size = storage.getSlots();
        int index = Math.max(0, from);
        while (index < size && isGhostItemSlot(index)) {
            index++;
        }
        return index;
    }

    /**
     * 把一件物品尽力安置进中栏（先合并同类、再占空槽；ghost 格跳过）。
     *
     * @return 实际放下的个数（{@code 0} = 无处可放；调用方据此发 {@code TARGET_FULL} 并回滚源侧）
     */
    public int depositIntoStorage(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return 0;
        }
        final int size = storage.getSlots();
        int left = stack.stackSize;
        int index = nextSortableStorageSlot(0);
        while (index < size && left > 0) {
            final ItemStack attempt = stack.copy();
            attempt.stackSize = left;
            final ItemStack rest = storage.insertItem(index, attempt, false);
            left = rest == null ? 0 : rest.stackSize;
            index = nextSortableStorageSlot(index + 1);
        }
        final int moved = stack.stackSize - left;
        if (moved > 0) {
            dirty = true;
        }
        return moved;
    }

    /**
     * 第 {@code tank} 号流体槽还能收这一份多少 mB（<b>int 口径</b>，头层交互面；槽内已有别的流体 ⇒ 0；
     * tank 号非法 ⇒ 0）。
     * <p>
     * ★拉取模式下的落点就是"该流体列自己那一格"：ghost 声明的 {@code slotIndex} 即 tank 号，
     * 所以十八个 tank 各拉各的（R78②：3 组 × 6 列），不会像旧单条那样"第一格满了后面全满"。
     * <p>
     * ★R95 S5：本方法仍按<b>头容量</b>（16M/int 顶）收口——服务它的都是 int 交互面（世界侧 tap 的
     * 计划量）；通道抽取（16G 真值域）走 {@link #fluidBarRoomL(int, FluidStack)}。
     */
    public int fluidBarRoom(int tank, FluidStack probe) {
        if (probe == null || probe.amount <= 0 || !isValidTank(tank)) {
            return 0;
        }
        final FluidStack current = tankFluid[tank];
        final int currentAmount = current == null || current.amount <= 0 ? 0 : current.amount;
        final boolean compatible = currentAmount == 0 || current.getFluid() == probe.getFluid();
        return barRoom((int) Math.min(fluidTankCapacity(), Integer.MAX_VALUE), currentAmount, compatible);
    }

    /**
     * ★R95 S5：第 {@code tank} 号流体槽的 <b>long 余量</b>（按真值与动态容量算，16G 域的通道抽取用）：
     * 异种流体 ⇒ 0；同种/空槽 ⇒ {@link #fluidTankCapacity()} − 真值。
     */
    public long fluidBarRoomL(int tank, FluidStack probe) {
        if (probe == null || !isValidTank(tank)) {
            return 0L;
        }
        final FluidStack current = tankFluid[tank];
        final long currentAmount = current == null || current.amount <= 0 ? 0L : tankTruth[tank];
        final boolean compatible = currentAmount == 0L || current.getFluid() == probe.getFluid();
        if (!compatible) {
            return 0L;
        }
        return Math.max(0L, fluidTankCapacity() - Math.max(0L, currentAmount));
    }

    /**
     * 流体槽空间的纯算术（<b>不碰任何 {@code Fluid} 实例</b>）：
     * 异种流体 ⇒ 0（一个 tank 只装一种），同种/空槽 ⇒ 容量减现有量。
     * <p>
     * 单独成函数并由零依赖套件直接驱动的理由：Forge 的 {@code Fluid}/{@code FluidRegistry}
     * 在纯 JVM 里连类初始化都过不去（实测 {@code ExceptionInInitializerError}），
     * 而"抽取前先算准能收多少"正是流体支唯一会静默吞流体的判据点（R45b）；
     * ★R95-W1（审查修复）后灌入截断住在 {@link #advanceTruthFill} 自己的算式里（头类 fill 已被构造期
     * 覆写接管），其守恒行为半边随流体可构造性走（用例 fluid_head_truth_single_sync_enforcement）。
     */
    public static int barRoom(int capacity, int currentAmount, boolean compatible) {
        if (!compatible || capacity <= 0) {
            return 0;
        }
        return Math.max(0, capacity - Math.max(0, currentAmount));
    }

    /**
     * 往第 {@code tank} 号流体槽灌入；返回实际接收 mB。
     * <p>
     * ★新功能 N 起灌入原语收口在 {@link #fillOwnTank(int, FluidStack)}（同语义单源），本方法保留给
     * 既有调用方（GUI 侧 {@code depositFluid} / 通道侧）——不改变任何既有行为。
     * ★R95-W1（审查修复）：int 档与 long 档（{@link #fillOwnTankL(int, Fluid, long)}）共用
     * {@link #advanceTruthFill} 一条真值域算式——按动态容量钳实收、推真值、{@link #syncHead(int)}
     * 回同步头；单笔量 ≤ int 顶但<b>累加可越 int 顶</b>（越顶部分活在真值，头钳在顶，账目闭合）。
     */
    public int depositFluidIntoBar(int tank, FluidStack fluid) {
        return fillOwnTank(tank, fluid);
    }

    /**
     * ★新功能 N（S6，G-C）世界站抽液的口袋侧灌入口：往第 {@code tank} 号槽灌入 {@code fluid}。
     * <p>
     * ★★<b>R95-W1（审查修复）</b>：不再经头类 {@code FluidStackTank.fill}（其非空同流体合并支直改
     * 头 amount 不触发 setter 钩子 ⇒ 真值不动、saveTanks 钳回 = 差额静默蒸发）——守卫语义逐条保留
     * （同流体合并 / 异流体拒 / 动态容量夹取），落算式统一进 {@link #advanceTruthFill}：按真值域算
     * 实收、推真值、{@link #syncHead(int)} 回同步头。补上「实收 &gt;0 ⇒ {@code dirty=true}」（世界侧
     * 没有关屏钩子可依赖，脏标记必须在灌入点自含——F1 双分支里「无活会话」那支按 {@link #isDirty()}
     * 决定是否一次性 {@link #writeTo}；「有活会话」那支经 {@code PocketSession#depositFluid} 落到同一实例）。
     *
     * @return 实际接收量（mB；供聊天回执如实报「实收」）
     */
    public int fillOwnTank(int tank, FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0 || !isValidTank(tank)) {
            return 0;
        }
        final FluidStack current = tankFluid[tank];
        if (current != null && current.amount > 0 && current.getFluid() != fluid.getFluid()) {
            return 0;
        }
        // 空头的身份用调用方栈的拷贝（保 tag）；量由 advanceTruthFill 的容量钳 + syncHead 定
        return (int) advanceTruthFill(tank, fluid.amount, fluid::copy);
    }

    /**
     * ★R86（缺陷 3）：本 tank 当前那份流体。非法 tank ⇒ {@code null}。
     * <p>
     * ⚠ 返回的是<b>槽内那份的引用</b>（与 {@link #fluidBarRoom} 读的是同一个数组），调用方只读不改写；
     * 注入支拿它做"投递前现读对表"，比对的是内容键与量，绝不拿它去 {@code setFluid}。
     */
    public FluidStack ownTankFluid(int tank) {
        return isValidTank(tank) ? tankFluid[tank] : null;
    }

    /**
     * ★R86（缺陷 3）：从本 tank 抽走 {@code milliBuckets} mB —— 只在元件那边<b>真的收了货</b>之后调。
     *
     * @return 实际抽走量（0 ⇒ 一格都没动）；小于请求量时调用方必须把差额<b>原路注回元件</b>
     *         （R85 小项 2 那条"落点又变小了就原路注回"的纪律，同一条）
     */
    public int drainOwnTank(int tank, int milliBuckets) {
        if (!isValidTank(tank) || milliBuckets <= 0 || tankFluid[tank] == null) {
            return 0;
        }
        // ★R95-W1：不再经头类 drain（部分抽出支直改头 amount 不触发 setter ⇒ 真值不动、头被钳回 =
        // 流体复制）；抽出量、头、真值由同一条算式对账。
        return (int) advanceTruthDrain(tank, milliBuckets);
    }

    /**
     * ★★<b>R95-W1（审查修复）：灌入侧「改真值 → 同步头」的唯一真值域算式</b>（int 档
     * {@link #fillOwnTank(int, FluidStack)} 与 long 档 {@link #fillOwnTankL(int, Fluid, long)} 共用）。
     * 实收 = min(请求量, 动态容量 − 真值)；实收&gt;0 才动轨：<b>真值 += 实收，随后 {@link #syncHead(int)}
     * 立即把头重写为 min(真值, int 顶)</b>，再置脏。空头灌入时以 {@code emptyHeadFactory} 立头身份
     * （量随后由同一落点定）。守卫（异种拒 / 非法 tank）留在各公开原语，与 R95-W1 之前逐条同语义。
     *
     * @param emptyHeadFactory 仅在头空时调用一次，产出携带流体身份的新头
     * @return 实际接收量（mB，long 域；0 = 一滴没进 ⇒ 双轨与脏标记全不碰）
     */
    private long advanceTruthFill(int tank, long amount, java.util.function.Supplier<FluidStack> emptyHeadFactory) {
        final long room = Math.max(0L, fluidTankCapacity() - Math.max(0L, tankTruth[tank]));
        final long moved = Math.min(Math.max(0L, amount), room);
        if (moved <= 0L) {
            return 0L;
        }
        tankTruth[tank] = Math.max(0L, tankTruth[tank]) + moved;
        final FluidStack head = tankFluid[tank];
        if (head == null || head.amount <= 0) {
            tankFluid[tank] = emptyHeadFactory.get();
        }
        syncHead(tank);
        dirty = true;
        return moved;
    }

    /**
     * ★★<b>R95-W1（审查修复）：抽出侧「改真值 → 同步头」的唯一真值域算式</b>（int 档
     * {@link #drainOwnTank(int, int)} 与 long 档 {@link #drainOwnTankL(int, long)} 共用）。
     * 实抽 = min(请求量, 真值)；实抽&gt;0 才动轨：真值 −= 实抽，{@link #syncHead(int)} 重写头
     * （真值归零 ⇒ 头置 null；真值仍超 int 顶 ⇒ 头保持 int 顶，玩家拿走的量已从真值扣除，账目闭合）。
     *
     * @return 实际抽走量（mB，long 域；小于请求量 ⇒ 调用方必须把差额原路注回）
     */
    private long advanceTruthDrain(int tank, long amount) {
        final long moved = Math.min(Math.max(0L, amount), Math.max(0L, tankTruth[tank]));
        if (moved <= 0L) {
            return 0L;
        }
        tankTruth[tank] = Math.max(0L, tankTruth[tank]) - moved;
        syncHead(tank);
        dirty = true;
        return moved;
    }

    /**
     * ★R95 S5：往第 {@code tank} 号槽灌入 {@code amount} mB 的 <b>long 原语</b>（真值域直达 16G）。
     * <p>
     * 不经头层 {@code fill} ⇒ 三件事：异种拒收 / 按动态容量钳接收量 / 双轨同推。★R95-W1（审查修复）
     * 起与 int 档 {@link #fillOwnTank(int, FluidStack)} 共用 {@link #advanceTruthFill} 同一条算式
     * （头重钳统一走 {@link #syncHead(int)}），本方法只保留守卫腿。
     * 调用方是 AE2 通道抽取（元件侧一次可给出超 int 的长整批量）。
     *
     * @return 实际接收量（mB，long；0 = 一滴没进——异种 / 满 / 非法 tank）
     */
    public long fillOwnTankL(int tank, Fluid fluid, long amount) {
        if (fluid == null || amount <= 0 || !isValidTank(tank)) {
            return 0L;
        }
        final FluidStack current = tankFluid[tank];
        if (current != null && current.amount > 0 && current.getFluid() != fluid) {
            return 0L;
        }
        return advanceTruthFill(tank, amount, () -> new FluidStack(fluid, 0));
    }

    /**
     * ★R95 S5：从第 {@code tank} 号槽抽走 {@code amount} mB 的 <b>long 原语</b>（真值域）。
     * <p>
     * 与 {@link #drainOwnTank(int, int)} 同一条"只在元件真收了货之后调"的纪律；★R95-W1（审查修复）
     * 起两档共用 {@link #advanceTruthDrain} 同一条算式：真值不足按存量给，头同步经
     * {@link #syncHead(int)} 重钳（真值归零 ⇒ 头置 null；真值仍超 int 顶 ⇒ 头保持 int 顶）。
     *
     * @return 实际抽走量（long；小于请求量 ⇒ 调用方必须把差额原路注回元件）
     */
    public long drainOwnTankL(int tank, long amount) {
        if (!isValidTank(tank) || amount <= 0L || tankFluid[tank] == null || tankTruth[tank] <= 0L) {
            return 0L;
        }
        return advanceTruthDrain(tank, amount);
    }

    /**
     * ★R95 S5：第 {@code tank} 号槽的 <b>long 真值</b>（mB；GUI 的 long 同步值与自绘读数读它；
     * 非法 tank ⇒ 0）。真值 ≤ int 顶时与头层读数恒等（双轨不变式的直接推论）。
     */
    public long tankTruthAt(int tank) {
        return isValidTank(tank) ? Math.max(0L, tankTruth[tank]) : 0L;
    }
}
