package com.miaokatze.gtit.gui.pocket;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketFluidTransfer;
import com.miaokatze.gtit.common.items.pocket.PocketIntakeOps;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;

/**
 * 口袋全部<b>真实槽</b>的工厂（本仓侧的唯一构造点）。
 * <p>
 * <b>列文件内一律不内联构造槽</b>：这是 S5 能在不改列文件的前提下接管 ghost 语义的前提
 * （slice-s4-brief §6 第 9 条），也是「Container 槽数可机检」的前提。
 * <p>
 * 每造一个真实槽都记一次数，装配末尾必须 {@link #assertTotalRealSlots()} 命中
 * <b>{@value #TOTAL_REAL_SLOTS}</b>（R80 的口径，覆盖 R78 的 235、R75 的 175、R74 的 185、§14.3/R43b 的 149）：
 * 中栏 <b>135</b>（15 行 × 9 列）+ 流体交互 <b>36</b>（3 组 × 6 列 × 进/出）+ 蒸馏输入 12 + 绑定 1
 * = <b>184</b> 格由本工厂造，另有<b>玩家背包 36 格</b>由框架造 ⇒ 合计 <b>220</b>；
 * 72 源质格、18 个流体槽本体与全部 ghost 配置<b>不进 Container</b>（R35/R46d），不计入。
 * 偏大 = 有区域被重复接入，偏小 = 有区域漏接 ⇒ 两种都当场炸出来，不留到实机。
 * <p>
 * <b>★关于框架自己绑的玩家背包 36 格（R78① 撤销 R69-D2）</b>：MUI2 的
 * {@code ModularSyncManager#construct} 在<b>没有</b>人预先注册 {@code player_inventory} 槽组时会
 * {@code bindPlayerInventory}（{@code ISyncRegistrar.java:85-96}；字节码：循环 36 次
 * {@code itemSlot("player", i, …)} + 注册 {@link com.cleanroommc.modularui.widgets.slot.PlayerSlotGroup}）。
 * 旧实现预注册一个<b>空</b>的 {@code PlayerSlotGroup} 让那一支跳过（R69-D2，为的是 L2 档不显示背包
 * 并消掉包放大），R78① 按用户裁决<b>撤销</b>该招 ⇒ 那 36 格回到 Container 并由
 * {@link NekoPocketBottomBand} 画在底部带中间段（9 列 × 4 行）。
 * <p>
 * ★<b>连带代价（E4，R78① 明文要求不得静默）</b>：首开要同步 36 格，且 vanilla
 * {@code Container#detectAndSendChanges} 每 tick 对这 36 格做 {@code ItemStack} 相等比较
 * （<b>含整份 NBT 深比较</b>），玩家背包内容一变就把整枚口袋连同 184 格一起重发
 * （R53c 点名的包放大面）。这是用户为"要玩家背包"明确换回来的代价，README 与本注释同处点名。
 * 框架那一支的 {@code PlayerSlotGroup} rowSize=9、{@code allowShiftTransfer=true} ⇒
 * shift 点击在中栏与背包之间双向可用（旧 L2 档的"取出到背包"按钮因此是加速快捷键而非唯一出口）。
 * <p>
 * ★<b>S7/T1 下沉后的形态</b>：本类只留槽位工厂与 UI 外壳。12 格「蒸馏 / 注入」的分流与注入
 * <b>原语</b>（{@code classifyIncoming} / {@code injectContainer} / {@code injectCrystals} /
 * {@code scaledByStackSize} 与 {@code IncomingAction}/{@code Intake}/{@code IntakeResult} 词汇）
 * 已迁 {@link PocketIntakeOps}——本类 {@code extends} 之，既有调用点与回归套件里的限定名
 * {@code PocketSlots.xxx} 经成员继承<b>原样解析、零改动</b>；流体列搬运的算法核
 * （{@code drainIntoTank}/{@code fillFromTank}）已迁 {@link PocketFluidTransfer}，本类经
 * {@link PocketFluidTransfer.Cells} 端口把实例 UI 状态（播报抑制、槽件表、出格闩）递进去，
 * 行为零变化（判据、算术与出口逐字保留）。
 */
public final class PocketSlots extends PocketIntakeOps {

    /**
     * 本工厂造出的槽总数（R80：135 + 36 + 12 + 1 = <b>184</b>）。
     * <p>
     * ★与 {@link #TOTAL_REAL_SLOTS} 分开是有意的：那 36 格背包<b>不</b>经本工厂
     * （由框架造，见类 javadoc），把它们混进本计数就会变成"工厂自己数不出自己该造几个"。
     */
    public static final int FACTORY_REAL_SLOTS = PocketInventory.STORAGE_SLOTS + PocketInventory.FLUID_INTERACTION_SLOTS
        + PocketInventory.DISTILL_INPUT_SLOTS
        + PocketInventory.BIND_SLOTS;
    /**
     * 玩家背包进 Container 的槽数（★<b>转发</b> {@link PocketConstants#PLAYER_BACKPACK_SLOTS}，
     * 本类不另立一份：可见格数 9×4 与框架注册的 36 必须是同一个数）。
     */
    public static final int PLAYER_BACKPACK_SLOTS = PocketConstants.PLAYER_BACKPACK_SLOTS;
    /** R80 定稿的真实 Container 槽总数（184 + 36 = <b>220</b> = 135 + 36 + 12 + 1 + 36）。 */
    public static final int TOTAL_REAL_SLOTS = FACTORY_REAL_SLOTS + PLAYER_BACKPACK_SLOTS;

    // ----------------------------- 槽组名（矩阵侧与 Container 侧必须逐字相同，R41d）
    public static final String GROUP_STORAGE = "pocket_storage";
    public static final String GROUP_FLUID = "pocket_fluid";
    public static final String GROUP_DISTILL = "pocket_distill";
    public static final String GROUP_BIND = "pocket_bind";

    /**
     * 中栏每行列数（R80 定稿 10 → <b>9</b>；★<b>转发</b> {@link PocketConstants#STORAGE_COLUMNS}，
     * 本类不另立字面量：矩阵列宽与 {@code SlotGroup.rowSize} 必须是同一个数）。
     */
    public static final int STORAGE_COLUMNS = PocketConstants.STORAGE_COLUMNS;
    /** 中栏行数（★<b>转发</b> {@link PocketConstants#STORAGE_ROWS}；R75 的 15 行本轮未动）。 */
    public static final int STORAGE_ROWS = PocketConstants.STORAGE_ROWS;
    /** 流体<b>每组</b>列数 = 列数（每组的输入行 6 格一行、输出行 6 格一行，R75① + R78②）。 */
    public static final int FLUID_COLUMNS = PocketConstants.FLUID_COLUMN_COUNT;
    /** 流体<b>组数</b>（R78②：3 组；槽组 rowSize 仍是每组的列数，不是组数×列数）。 */
    public static final int FLUID_GROUPS = PocketConstants.FLUID_GROUP_COUNT;
    /** 蒸馏盘每行 6 列（R75：3 行 × 4 → 2 行 × 6，格数不变；R78 未动）。 */
    public static final int DISTILL_COLUMNS = 6;
    /** 蒸馏盘行数（R75 定稿 2 行；与 {@link NekoPocketEssenceColumn#DISTILL_ROWS} 同源判据）。 */
    public static final int DISTILL_ROWS = 2;

    /**
     * 格数与行列的<b>自证算式</b>（装配一进来就算，双端同一段代码）。
     * <p>
     * 存在的理由：R75 的"128→150"、R78 的"48→72 / 6→18 / 175→235"与 R80 的"10 列→9 列 / 235→220"
     * 都是跨文件的批量口径变更，
     * 最坏的失败形态不是编译不过，而是"矩阵行数改了、常量没改"或"常量改了、Container 注册的
     * 行数没改"这类<b>各自都能编译</b>的半改。这里把三者的乘积关系变成一条构造期就抛的断言，
     * 配套的负控在回归套件里（{@code slot_math_220_and_row_column_products}）。
     */
    static {
        if (STORAGE_ROWS * STORAGE_COLUMNS != PocketInventory.STORAGE_SLOTS) {
            throw new IllegalStateException(
                "[pocket] 中栏行列乘积不等于格数: " + STORAGE_ROWS
                    + "x"
                    + STORAGE_COLUMNS
                    + " != "
                    + PocketInventory.STORAGE_SLOTS);
        }
        if (FLUID_GROUPS * FLUID_COLUMNS * PocketConstants.FLUID_INTERACTION_PER_COLUMN
            != PocketInventory.FLUID_INTERACTION_SLOTS) {
            throw new IllegalStateException("[pocket] 流体组数×列数×每列格数不等于交互格数（R78② 的三级乘积）");
        }
        if (DISTILL_COLUMNS * DISTILL_ROWS != PocketInventory.DISTILL_INPUT_SLOTS) {
            throw new IllegalStateException("[pocket] 蒸馏盘行列乘积不等于格数");
        }
        if (PocketConstants.PLAYER_BACKPACK_COLUMNS * PocketConstants.PLAYER_BACKPACK_ROWS != PLAYER_BACKPACK_SLOTS) {
            throw new IllegalStateException("[pocket] 背包行列乘积不等于背包槽数");
        }
        if (FACTORY_REAL_SLOTS + PLAYER_BACKPACK_SLOTS != TOTAL_REAL_SLOTS) {
            throw new IllegalStateException("[pocket] 工厂产出 + 框架背包不等于 Container 口径");
        }
    }

    // ----------------------------- 同步键（每组一个，避免两个组各自从 0 编号互相覆盖）
    public static final String SYNC_STORAGE = "pocket_storage_slots";
    public static final String SYNC_FLUID = "pocket_fluid_slots";
    public static final String SYNC_DISTILL = "pocket_distill_slots";
    public static final String SYNC_BIND = "pocket_bind_slot";

    /** 本次装配造出的真实槽计数。 */
    private int createdRealSlots;

    /**
     * 流体搬运重入闩：{@link ModularSlot#putStack(ItemStack)} 会经
     * {@code ItemSlotSH.checkUpdate → onSlotChangedReal → changeListener} 回到本类，
     * 不加闩就是无限递归。
     * <p>
     * ★<b>R84 由 {@code static} 改为实例字段</b>（旧注释自认的口径只有"服务端主线程单点访问"）。
     * 本类是 <b>per-panel</b> 造的（一个口袋一块面板一个实例），static 的真实代价不是并发，而是
     * "同一 tick 内 A 口袋正在搬运的那一小段里，B 口袋（另一位玩家 / 另一枚口袋）的回调被整段吞掉"，
     * 而那一次<b>不会再来</b>——{@code ItemSlotSH#checkUpdate} 只在内容与缓存<b>不同</b>时才 announce
     * （{@code ItemSlotSH.java:67-69}），错过的那一拍没有补偿入口。
     * <p>
     * 改成实例字段仍然保住重入保护，理由是可达的再入路径全部落在 {@code this} 之内：
     * <ol>
     * <li>本方法写的格只来自 {@link #fluidSlotsByIndex}（本实例装配的 36 格）与本实例的
     * {@link PocketInventory}，{@code putStack → checkUpdate → onSlotChangedReal → changeListener}
     * 那一跳回到的是<b>同一个</b>实例的 lambda；</li>
     * <li>{@link PocketInventory#markDirty()} 只置一个布尔，不会回调任何面板；</li>
     * <li>{@link #returnToPlayer} 写玩家背包，而 {@code PlayerMainInvWrapper#setStackInSlot} 会顺手调
     * {@code player.openContainer.detectAndSendChanges()}——那位玩家同时只开着<b>这一块</b>面板 ⇒ 仍是本实例。</li>
     * </ol>
     * 换句话说 static 版多出来的那部分保护力覆盖不到任何真实路径，只会制造上面那种静默丢失。
     */
    private boolean transferring;

    /**
     * 本次装配造出的 36 个流体交互格（{@code handler} 索引 → 槽件）。
     * <p>
     * 存在的理由：R83 D-2 的落位要写<b>配对那一格</b>，而 {@code changeListener} 只拿得到当前槽件；
     * 用 {@code inv.fluidInteraction().setStackInSlot(...)} 直写 handler 会绕过那一格的
     * {@code ModularSlot.putStack}（同步与闩的登记都在那条支上），故装配期就把 36 个槽件记下来。
     */
    private final Map<Integer, ModularSlot> fluidSlotsByIndex = new HashMap<>();

    /**
     * <b>★出格闩</b>：{@code handler} 索引 → 刚被搬运写进那一格的那一份。命中的那一格本次<b>不搬</b>。
     * <p>
     * 为什么必须有它（不是可选优化）：{@code ModularSlot.onSlotChanged()} 在库里是空实现，真正的触发链是
     * {@code ItemSlotSH.detectAndSendChanges → checkUpdate → changeListener}，它在服务端<b>每 tick</b>
     * 比内容。倒空空来的单元落进本列出格后，那一格的内容就是"空容器" ⇒ 没有闩就会被 {@code fillFromTank}
     * 当场反向灌回，玩家读到的是"流体绕一圈又回单元里"。
     * <p>
     * 命中判据用<b>整栈相等</b>（含件数与 NBT）：玩家从出格取走或补进任意一件就对不上 ⇒ 闩当场失效，
     * 之后照常搬运。<b>不落 NBT</b>：关屏期间没有任何触发源，重开首帧的回调一律是 {@code init=true}（被早退）。
     */
    private final Map<Integer, ItemStack> fluidOutputLatch = new HashMap<>();

    /**
     * <b>播报抑制表</b>：{@code handler} 索引 → 这一格上一次<b>放弃搬运</b>时发过的文案键。
     * <p>
     * 为什么必须有它：{@code changeListener} 的触发条件是"这一格的内容与上一拍不同"，而玩家从出格里
     * <b>取走一件</b>满容器也算内容变化 ⇒ 同一条"槽满了 / 出格占着"的失败会在每次手势上重发一次。
     * 同一格同一键只报一次；<b>搬运成功</b>（{@link #placeProcessed}）或那一格<b>清空</b>
     * （{@link #moveFluidBetweenTanks} 的空栈支）就把键清掉 ⇒ 条件真的变了才再报。
     * <p>
     * 不落 NBT：这是纯会话内的手感状态，关屏即无意义。
     */
    private final Map<Integer, String> fluidNoticeKeys = new HashMap<>();

    public PocketSlots() {}

    /** 中栏 {@link PocketConstants#GHOST_ITEM_SLOT_LIMIT} 格之一（行主序，与矩阵字符序一致）。 */
    public ModularSlot storage(PocketInventory inv, int index) {
        createdRealSlots++;
        return new ModularSlot(inv.storage(), index).slotGroup(GROUP_STORAGE);
    }

    /**
     * 流体列的交互格之一（R75① + R78②：3 组 × 6 列 × 输入/输出 = <b>36</b> 格）。
     * <p>
     * <b>R83 D-2 把这里的口径切成两半</b>：R39a 的"两格同权"<b>只</b>说输入侧（上下两格都能放容器、
     * 方向都由容器当前有无流体决定），这一条一个字都没改；它<b>从未</b>规定处理完的容器落在哪一格。
     * 落点自 R83 起不对称 —— 处理产物一律进本列<b>出格</b>（{@link PocketInventory#outputInteractionSlotOf(int)}）
     * 并上闩，未处理完的余量留本列<b>进格</b>（{@link PocketInventory#inputInteractionSlotOf(int)}）。
     * <p>
     * ★与旧口径唯一的差别：搬运的<b>落点</b>由"该格取模出的列"变成
     * "该格所属那一组的那一列"（{@link PocketInventory#tankOfInteractionSlot(int)}，
     * 组数=1 时两式同解）。这是 R78② 的实质——18 个 tank 各拉各的、各灌各的，不再互相挤容量。
     * <p>
     * 搬运触发点 = {@code ModularSlot.changeListener}（{@code ModularSlot.java:165-169}），
     * <b>只在服务端的非 init 回调里执行</b>（客户端那份 handler 由 vanilla 槽同步喂显示）。
     */
    public ModularSlot fluidInteraction(PocketInventory inv, int index) {
        createdRealSlots++;
        final ModularSlot slot = new ModularSlot(inv.fluidInteraction(), index).slotGroup(GROUP_FLUID);
        fluidSlotsByIndex.put(index, slot);
        slot.changeListener((newItem, onlyAmountChanged, client, init) -> {
            if (client || init) {
                return;
            }
            // ★用装配期传入的 handler 索引，不用 slot.getSlotIndex()：后者是 Container 里的全局槽号，
            // 与中栏/蒸馏格交错，拿它取模会取到别的列（这是"十八个 tank 各灌各的"唯一会算错的地方，
            // 落点映射单源在 PocketInventory#tankOfInteractionSlot）
            moveFluidBetweenTanks(inv, index, slot);
        });
        return slot;
    }

    /**
     * 蒸馏盘 12 格之一：需求 2 的「蒸馏 / 注入」<b>双用</b>输入区（R63b，改写
     * {@code slice-s3s4} 任务包约束 8 里那句字面的「蒸馏输入槽必须拒绝源质容器」）。
     * <p>
     * R44c 要关的危害照旧关闭，但改述为「<b>容器不得进入蒸馏判定路径</b>」而不是"不得进入这 12 格"：
     * 分流由 {@link #classifyIncoming(ItemStack)} <b>单点</b>完成，容器走注入支且当场被排空退回，
     * 永远到不了 {@code TaumCompat.distill} ⇒ {@code getBonusTags} 把栈内已有源质重复计入产出的
     * 回路从入口就断了。需求 2 末句「源质罐子可取/放」也因此有了落点（R63a）。
     */
    public ModularSlot distillInput(PocketInventory inv, int index) {
        createdRealSlots++;
        final ModularSlot slot = new ModularSlot(inv.distillInput(), index).slotGroup(GROUP_DISTILL);
        slot.filter(PocketSlots::acceptsDistillSlot);
        slot.changeListener((newItem, onlyAmountChanged, client, init) -> {
            if (client || init) {
                return;
            }
            injectContainerIfAny(inv, slot);
        });
        return slot;
    }

    /** 12 格的可入性：两种分支都收（普通物品 / 有内容的源质容器），空槽与空容器不收。 */
    private static boolean acceptsDistillSlot(ItemStack stack) {
        return classifyIncoming(stack) != IncomingAction.REJECT;
    }

    /*
     * ★S7/T1 下沉清单（行为零变化，逐字迁往 {@link PocketIntakeOps}）：
     * enum IncomingAction、classifyIncoming ×2、enum Intake、IntakeResult、injectContainer、
     * injectCrystals、scaledByStackSize、toMap（私有）。本类 {@code extends PocketIntakeOps} ⇒
     * 上面与既有调用方（PocketDistillDriver、回归套件的 {@code PocketSlots.xxx} 限定名）
     * 经成员继承原样解析，零改动。
     */

    /**
     * 注入支的 GUI 外壳：把 {@link #injectContainer} 的结论翻译成槽位动作与玩家反馈。
     * <p>
     * 装得下 ⇒ 排空后的容器按 R40a 同法<b>非消耗</b>地退回玩家处（背包满则掉脚下），格清空。
     * 装不下 ⇒ 容器原样留在格内（一格都没动）。
     * <p>
     * ★R88 换载体后多一种收尾：<b>部分抽干</b>（一叠瓶里只吃下了其中几只）——被抽干的那几只空壳退回
     * 玩家，<b>没抽干的余量必须写回格子</b>等下一拍，两步顺序不能换（{@link #returnToPlayer} 自己会
     * {@code putStack(null)}）。晶支（{@link Intake#CONSUMED}）照旧整叠销毁、绝不退回空壳。
     * <p>
     * ★R84：本方法读 {@link #transferring}，随该闩一起由 static 改为实例方法（判据本身未变）。
     */
    private void injectContainerIfAny(PocketInventory inv, ModularSlot slot) {
        if (transferring) {
            return;
        }
        final ItemStack placed = slot.getStack();
        if (classifyIncoming(placed) != IncomingAction.INJECT) {
            return;
        }
        transferring = true;
        try {
            final IntakeResult result = injectContainer(placed, inv.essence(), EssenceGate.TAUM);
            if (result.kind == Intake.STORE_FULL) {
                // R29 全有全无的失败面：一格都不动、容器分毫未动，必须可观测（still.inject_full）
                putSlotReceipt(slot, "gtit.pocket.still.inject_full");
                return;
            }
            if (!result.drained() && !result.consumed()) {
                return;
            }
            inv.markDirty();
            putSlotReceipt(slot, "gtit.pocket.still.injected", result.points);
            if (result.consumed()) {
                // ★R86（实机缺陷 2）：晶是<b>整叠销毁</b>，绝不能走下面那条退回 —— 空壳晶留在玩家身上
                // 或格内，会被 TC 服务端 {@code onItemUpdate} 随机重赋型（这正是 drainAll 对晶早退的理由）。
                slot.putStack(null);
            } else {
                returnToPlayer(slot, result.returnedCarrier != null ? result.returnedCarrier : placed);
                if (result.remainder != null) {
                    // ★R88 部分抽干：returnToPlayer 刚把格子清空，余量要在这之后写回（顺序反了就是丢件）
                    slot.putStack(result.remainder);
                }
            }
            // ★R86（实机「残影」条）：{@link #returnToPlayer} 里那句 {@code putStack(null)} 只会发出
            // 非 force 的 SYNC_ITEM（{@code ModularSlot.java:101-106} → {@code ItemSlotSH.java:77}），
            // 客户端 handler 仍持放进来那一叠 ⇒ 图标不消失，直到开屏全量重发。12 格这一面此前
            // <b>连 R84 的 checkUpdate 补丁都没有</b>，是残影最大的一面。
            forceSyncSlot(slot);
        } finally {
            transferring = false;
        }
    }

    /**
     * 单点反馈（{@code still.injected} / {@code still.inject_full}，契约 §5；R84 起流体侧的放弃搬运也走这里）。
     * <p>
     * ★<b>R84 修的那条通道本身</b>：旧写法只认 {@code ModularSlot#getPlayerSlotPlayer(SlotItemHandler)}，
     * 而那一支的判据是"槽件包的 handler 是 {@code PlayerInvWrapper} / {@code PlayerMainInvWrapper} /
     * {@code PlayerArmorInvWrapper}"（{@code ModularSlot.java:278-294}），另一支 {@code getPlayerSlotPlayer(Slot)}
     * 要 {@code slot.inventory instanceof InventoryPlayer}，而 {@code SlotItemHandler} 给父类传的是
     * <b>{@code InventoryBasic("[Null]", true, 0)}</b>（{@code SlotItemHandler.java:17-24}）。
     * 本仓的 36 个流体交互格与 12 个蒸馏格包的都是 {@link PocketInventory} 自己的 {@code ItemStackHandler}
     * ⇒ <b>两条判据都不成立 ⇒ 旧代码里这几处的回执一条都到不了玩家</b>（含 R29 就约定的
     * {@code still.inject_full}）。所以退回框架自己记着的那位玩家：{@code ModularSlot#getPlayer} 用的正是
     * 同一条路（{@code ModularSlot.java:147-149}），本文件只是没用它。
     * <p>
     * ★★<b>R88 C3 已闭合：本方法不再进聊天框</b>。裁定是「源质与蒸馏这类操作性回执一律留在面板内」，
     * 而 pocket 域此前只剩这一个 {@code addChatMessage} 出口（调用点 {@link #injectContainerIfAny} 的两条 +
     * {@link #notifyCell} 的流体那条）。槽件侧手里只有 {@code ModularSlot → SyncHandler → PanelSyncManager}
     * 这一条链，拿不到面板对象，因此写入口开在面板那侧：{@link NekoPocketPanel#recordSlotReceipt} 按现役
     * {@code PanelSyncManager} 实例反查面板，查不到（未装配完／已关屏）就丢弃这条回执。
     * ★面板回执的载荷是「键 + 两个数」且本地化在客户端做 ⇒ 这里只把 {@code args} 里第一个整数当
     * 「动了多少」带过去，其余格式化实参随聊天框一起退役；本方法因此不再需要玩家对象，而
     * {@link #resolveSlotPlayer} 仍服务归还件那条路，未随之删除。
     */
    private static void putSlotReceipt(ModularSlot slot, String key, Object... args) {
        if (slot == null || !slot.isInitialized()) {
            return;
        }
        NekoPocketPanel.recordSlotReceipt(
            slot.getSyncHandler()
                .getSyncManager(),
            key,
            firstIntArg(args),
            0);
    }

    /** 旧聊天回执的格式化实参里最多带一个数（点数／格数）；面板回执的载荷只有「键 + 两个数」的位置。 */
    private static int firstIntArg(Object... args) {
        if (args == null) {
            return 0;
        }
        for (final Object arg : args) {
            if (arg instanceof Number) {
                return ((Number) arg).intValue();
            }
        }
        return 0;
    }

    /** 槽件 → 玩家：先看是不是玩家背包的槽，否则取该槽所在面板的那位玩家（未装配完 ⇒ null）。 */
    private static EntityPlayer resolveSlotPlayer(ModularSlot slot) {
        if (slot == null) {
            return null;
        }
        final EntityPlayer direct = ModularSlot.getPlayerSlotPlayer(slot);
        if (direct != null) {
            return direct;
        }
        // getSyncHandler() 在槽件还没被框架装配时会抛 IllegalStateException（ModularSlot.java:140-145），
        // 而本方法可能被装配期之外的路径碰到 ⇒ 先用 isInitialized() 挡一道
        return slot.isInitialized() ? slot.getSyncHandler()
            .getSyncManager()
            .getPlayer() : null;
    }

    /**
     * 排空后的容器非消耗地退回（R40a：背包满则掉到玩家脚下，遵循原版 {@code transferStackInSlot} 语义）。
     * <p>
     * ★R85 修：玩家必须走 {@link #resolveSlotPlayer}。旧写法直接用 {@code getPlayerSlotPlayer}，而它对
     * 本仓这类包 {@code PocketInventory} handler 的槽<b>恒返 null</b>（R84 已为 {@link #putSlotReceipt} 证死同一条），
     * 又因本方法<b>先</b> {@code putStack(null)} 清空了格子 ⇒ 拿不到玩家就 return ⇒ 容器既不归也不掉落＝
     * <b>静默销毁玩家的容器</b>（12 格注入支排空后必踩，可达路径见 {@link #injectContainerIfAny}）。
     */
    private static void returnToPlayer(ModularSlot slot, ItemStack leftover) {
        slot.putStack(null);
        if (leftover == null) {
            return;
        }
        final EntityPlayer player = resolveSlotPlayer(slot);
        if (player == null) {
            return;
        }
        if (!player.inventory.addItemStackToInventory(leftover)) {
            player.entityDropItem(leftover, 0);
        }
    }

    /** 底部带绑定格（1 格，瞬时入口）。 */
    public ModularSlot bind(PocketInventory inv) {
        createdRealSlots++;
        return new ModularSlot(inv.bindSlot(), 0).slotGroup(GROUP_BIND);
    }

    /** 本工厂已造出的真实槽数（断言与调试用）。 */
    public int createdRealSlots() {
        return createdRealSlots;
    }

    /**
     * 面板装配末尾调用：本工厂的产出必须是 {@link #FACTORY_REAL_SLOTS}，且加上框架绑的
     * {@link #PLAYER_BACKPACK_SLOTS} 后必须是 {@link #TOTAL_REAL_SLOTS}（R80：184 + 36 = 220）。
     *
     * @throws IllegalStateException 计数不符（双端同抛 ⇒ 首开即暴露，不会静默错位）
     */
    public void assertTotalRealSlots() {
        assertTotalRealSlots(createdRealSlots, PLAYER_BACKPACK_SLOTS);
    }

    /**
     * 加总判据的<b>本体</b>（两个入参形态 ⇒ 回归套件能直接喂 234 / 236 两个负控，
     * 不必为了构造"少一格"去把 184 个槽真造一遍）。
     * <p>
     * ★两条判据都要，不能只判合计：只判合计的话"工厂少造一格 + 背包多绑一格"会互相抵消成 220
     * 而静默放过（两种错各有独立的玩家可见症状：前者是某块区域点不动，后者是隐形槽）。
     *
     * @param factoryCreated 本工厂实际造出的槽数
     * @param playerBackpack 框架那一条支实际注册进 Container 的背包槽数
     */
    public static void assertTotalRealSlots(int factoryCreated, int playerBackpack) {
        if (factoryCreated != FACTORY_REAL_SLOTS) {
            throw new IllegalStateException(
                "[pocket] 真实 Container 槽数口径被破坏: 工厂装配出 " + factoryCreated
                    + " 个, 应为 "
                    + FACTORY_REAL_SLOTS
                    + " 个 ("
                    + PocketInventory.STORAGE_SLOTS
                    + " 中栏("
                    + STORAGE_ROWS
                    + " 行×"
                    + STORAGE_COLUMNS
                    + " 列)"
                    + " + "
                    + PocketInventory.FLUID_INTERACTION_SLOTS
                    + " 流体交互("
                    + FLUID_GROUPS
                    + " 组×"
                    + FLUID_COLUMNS
                    + "×2)"
                    + " + "
                    + PocketInventory.DISTILL_INPUT_SLOTS
                    + " 蒸馏输入 + "
                    + PocketInventory.BIND_SLOTS
                    + " 绑定格)");
        }
        if (playerBackpack != PLAYER_BACKPACK_SLOTS) {
            throw new IllegalStateException(
                "[pocket] 玩家背包槽数不是 " + PLAYER_BACKPACK_SLOTS
                    + " 个（读到 "
                    + playerBackpack
                    + "）：不等于 9×4 ⇒ 要么有一部分背包格只存在于 Container 而画不出来（隐形槽），"
                    + "要么框架那一条支被重复接入");
        }
        if (factoryCreated + playerBackpack != TOTAL_REAL_SLOTS) {
            throw new IllegalStateException(
                "[pocket] Container 总口径被破坏: " + factoryCreated
                    + " + "
                    + playerBackpack
                    + " = "
                    + (factoryCreated + playerBackpack)
                    + ", 应为 "
                    + TOTAL_REAL_SLOTS);
        }
    }

    // ---------------- 流体列搬运（R39a 输入侧两格同权 + R83 D-2 输出侧不对称落位 / D-9 不吃件）

    /*
     * ★S7/T1 下沉清单（行为零变化，逐字迁往 {@link PocketFluidTransfer}）：drainIntoTank /
     * fillFromTank 的算法核、KEY_ 播报键 ×9、Plan、planPlacement、productRoom、canPlacePair、
     * canMoveIntoBothCells、restOf。本类只留 UI 外壳（notifyCell / placeProcessed / merged /
     * forceSyncSlot 等）与两条薄委托，经 {@link PocketFluidTransfer.Cells} 端口把实例状态递进去。
     */

    /**
     * 就某一格放弃一次搬运的播报（带 {@link #fluidNoticeKeys} 抑制：同一格同一原因只报一次）。
     * <p>
     * 键记在<b>触发搬运的那一格</b>上（{@code cell} 即 {@code sourceIndex}），与"这一格的东西搬不动"
     * 的语义一致；产物落位成功后由 {@link #placeProcessed} 清键。
     * <p>
     * {@code key == null} 表示"这一趟不是落位问题、已由调用方自己的分支报过" ⇒ 不播也不记。
     */
    private void notifyCell(int cell, String key, Object... args) {
        if (key == null || key.equals(fluidNoticeKeys.get(cell))) {
            return;
        }
        final ModularSlot slot = fluidSlotsByIndex.get(cell);
        if (slot == null) {
            return;
        }
        fluidNoticeKeys.put(cell, key);
        putSlotReceipt(slot, key, args);
    }

    /**
     * 单格一次搬运（目标 = 该格所属那一列的那一个 tank）。R83 一次改掉三段各自一处死路，缺一处都会被读成"没修"：
     * <ol>
     * <li><b>判容器</b>：{@link #readFluidFromContainer(ItemStack)} —— 接口优先、注册表并列、首个非空命中。
     * 旧写法只看 {@code FluidContainerRegistry.getFluidForFilledItem}，而 Forge 的那一个方法<b>不查</b>
     * {@code IFluidContainerItem} ⇒ GT5U 大型单元这类"NBT 里带流体、从不进注册表"的容器被系统性判成空容器，
     * 旧代码里那条 {@code IFluidContainerItem} 排空支对它们永不可达；</li>
     * <li><b>算件数</b>：一次处理整叠里放得下的<b>全部</b>件（旧写法整段没有任何按 {@code stackSize} 的累加），
     * 且每一件只把 {@code stackSize==1} 的副本交给 {@code fill/drain}；</li>
     * <li><b>落两格</b>：产物进本列出格并上闩 {@link #fluidOutputLatch}，没处理完的余量留本列进格；
     * 件数由 {@code PocketFluidTransfer} 内部的 {@code planPlacement} 在"流体侧 / 出格收得下产物 / 进格塞得回余量"三条之间取交集算出来，
     * ⇒ 出格只够放一部分时就<b>搬那一部分</b>（R84②），三条彻底无交集时才放弃，且<b>必定</b>发一条玩家
     * 看得见的回执（R84①，旧写法在这里是 {@code return} 了事）。
     * ★旧写法四条出口全用 {@code slot.putStack(...)} 写回<b>同一格</b>，
     * 而 {@code putStack} = {@code setStackInSlot} 无条件覆写 ⇒ 一叠 N 件只搬 1 件、余 N−1 件被 1 个空格顶掉。</li>
     * </ol>
     * 一次能提多少由<b>该容器条目自带</b>的携液量决定（桶 / GT 普通单元 1000、瓶 250、打火机 100、大型单元按容量档），
     * 那不是口袋的提取档位、改数字无效；槽快满时接口型容器允许部分倒空。
     * <p>
     * 容量口径只报真实容量（{@code FluidStackTank} 未开 overflow ⇒ {@code getCapacity()} 即真容量），
     * 与 GT5U 的 {@code RealCapacityFluidTank} 同形（R46c）。
     * <p>
     * <b>不含任何按键分支</b>：手持储罐点流体槽的语义与 Tooltip 由 MUI2 原生
     * {@code FluidSlot}/{@code FluidSlotSyncHandler} 与 {@code modularui2.fluid.*} 键提供
     * （R30/R46c/L7），本方法只负责"格内容器 ↔ 本列 tank"这一路。
     */
    private void moveFluidBetweenTanks(PocketInventory inv, int interactionIndex, ModularSlot slot) {
        if (transferring) {
            return;
        }
        final ItemStack placed = slot.getStack();
        if (placed == null || placed.stackSize <= 0) {
            fluidOutputLatch.remove(interactionIndex);
            // 清空 ⇒ 上一轮"这一格搬不动"的原因作废，下次真的卡住要能再报一次
            fluidNoticeKeys.remove(interactionIndex);
            return;
        }
        if (hitFluidOutputLatch(interactionIndex, placed)) {
            return;
        }
        // 槽号 → tank 号的映射只允许住在 PocketInventory 里（两处真相的第一候选点）
        final int tank = PocketInventory.tankOfInteractionSlot(interactionIndex);
        transferring = true;
        try {
            // ★每一件灌排都用 size==1 副本：接口两侧的通行约定是"多件直接拒收"
            // （GT5U gregtech/api/items/MetaBaseItem.java:521/574 第一行就是 stackSize != 1 return、
            // ItemVolumetricFlask.java:159/179 同形），把格内的整叠原始栈递过去必然静默返回 0
            // —— 这是"不能装载 / 不能提取"的第二条独立死路。
            final ItemStack unit = placed.copy();
            unit.stackSize = 1;
            final FluidStack content = readFluidFromContainer(unit);
            if (content != null && content.amount > 0) {
                drainIntoTank(inv, interactionIndex, tank, unit, placed.stackSize, content);
            } else {
                fillFromTank(inv, interactionIndex, tank, unit, placed.stackSize);
            }
        } finally {
            transferring = false;
        }
    }

    /**
     * 这一格里到底有没有流体：<b>接口优先 → 注册表并列 → 首个非空命中</b>（R83 D-1 的兼容面，
     * 同时是"提取 / 装载"方向的唯一分流点）。
     * <p>
     * ★入参必须是 {@code stackSize==1} 的副本（理由见 {@link #moveFluidBetweenTanks} 里那条注释）。
     * <p>
     * <b>不接</b> MUI2 {@code FluidInteractions} 第三级 NEI {@code StackInfo.getFluid}，也不接"流体展示 NBT"
     * 那一级：那两级<b>只给读不给写</b>，在这里报"有流体"只会让提取支空转、而玩家再也走不到灌装支（比现状更糟）。
     * 本仓自有的无尽流体单元属 AE2 元件族（物品侧根本没有灌排面）⇒ 走 ME 通道，见 D-1 那句"不纳入物品灌排面"。
     */
    private static FluidStack readFluidFromContainer(ItemStack unit) {
        if (unit == null || unit.getItem() == null) {
            return null;
        }
        if (unit.getItem() instanceof IFluidContainerItem container) {
            final FluidStack stored = container.getFluid(unit);
            if (stored != null && stored.amount > 0) {
                return stored.copy();
            }
        }
        return FluidContainerRegistry.getFluidForFilledItem(unit);
    }

    /**
     * 闩判定：内容与"刚被搬运写进这一格的那一份"逐字相等（含件数与 NBT）⇒ 本次不动；对不上 ⇒ 清闩。
     * <p>
     * 玩家从出格取走或补进任意一件都会让件数对不上 ⇒ 闩当场失效、之后照常搬运，不需要额外的解闩手势。
     */
    private boolean hitFluidOutputLatch(int index, ItemStack current) {
        final ItemStack written = fluidOutputLatch.get(index);
        if (written == null) {
            return false;
        }
        if (ItemStack.areItemStacksEqual(written, current)) {
            return true;
        }
        fluidOutputLatch.remove(index);
        return false;
    }

    /**
     * 容器 → 第 {@code tank} 号槽（倒空）。一次处理 {@code units} 件里放得下的件数，空容器落本列出格。
     * <p>
     * ★顺序纪律：流体先进槽、容器后扣件，任何一步对不上就把已进槽的退回槽。源栈
     * （{@code unit} 的原件）全程未动，被改的永远是副本 ⇒ 回滚只需要还槽，不存在"扣了件没进槽"的两处真相。
     * <p>
     * ★S7/T1：算法核已下沉 {@link PocketFluidTransfer#drainIntoTank}（判据、算术与出口逐字保留），
     * 本方法是薄委托——经 {@link #fluidCells} 把本实例的 UI 触点递进去。
     */
    private void drainIntoTank(PocketInventory inv, int sourceIndex, int tank, ItemStack unit, int units,
        FluidStack content) {
        PocketFluidTransfer.drainIntoTank(fluidCells(inv), sourceIndex, tank, unit, units, content);
    }

    /**
     * 第 {@code tank} 号槽 → 空容器（灌装）。满容器落本列出格；一次处理放得下的全部件。
     * <p>
     * ★注册表型空容器必须用<b>双参</b> {@code getContainerCapacity(FluidStack, ItemStack)}：单参在 Forge 里
     * 转成 {@code getContainerCapacity(null, container)}，而它先查的 {@code containerFluidMap} 是按<b>满容器</b>
     * 为键、第二段又因传入流体为 null 被跳过，空容器的键其实住在 {@code filledContainerMap}
     * （{@code FluidContainerRegistry.java:281-305}）⇒ 单参对一切注册表型空容器<b>恒返回 0</b>，
     * 旧代码那句 {@code containerCapacity <= 0 → return} 就是"不能装载"的第二条独立死路。
     * <p>
     * ★S7/T1：算法核已下沉 {@link PocketFluidTransfer#fillFromTank}（判据、算术与出口逐字保留），
     * 本方法是薄委托。
     */
    private void fillFromTank(PocketInventory inv, int sourceIndex, int tank, ItemStack unit, int units) {
        PocketFluidTransfer.fillFromTank(fluidCells(inv), sourceIndex, tank, unit, units);
    }

    /**
     * 把一次搬运所需的全部外部触点打包给下沉的算法核（{@link PocketFluidTransfer}）：实现侧
     * 只做对既有私有成员 / {@link PocketInventory} 静态映射的<b>纯转发</b>，不含自有逻辑 ⇒
     * 下沉前后行为逐字一致（S7/T1 行为零变化重构的接缝；这也是 common 侧不产生 gui 反向
     * import 的原因——索引真相与 UI 状态都留在 gui，经端口递入）。
     */
    private PocketFluidTransfer.Cells fluidCells(PocketInventory inv) {
        return new PocketFluidTransfer.Cells() {

            @Override
            public FluidStackTank tank(int tank) {
                return inv.tankAt(tank);
            }

            @Override
            public ItemStack cellStack(int cell) {
                return inv.fluidInteraction()
                    .getStackInSlot(cell);
            }

            @Override
            public int outputCellOf(int sourceIndex) {
                return PocketInventory.outputInteractionSlotOf(sourceIndex);
            }

            @Override
            public int restCellOf(int sourceIndex) {
                return PocketSlots.restCellOf(sourceIndex);
            }

            @Override
            public boolean hasSlot(int cell) {
                return fluidSlotsByIndex.get(cell) != null;
            }

            @Override
            public void abandoned(int cell, String key, Object... args) {
                notifyCell(cell, key, args);
            }

            @Override
            public void placeProcessed(int sourceIndex, ItemStack template, int moved, ItemStack unit, int restCount) {
                PocketSlots.this.placeProcessed(inv, sourceIndex, template, moved, unit, restCount);
            }
        };
    }

    /**
     * 用<b>实际成交件数</b>再判一次两格落位。
     * <p>
     * 必需的理由：预检是按计划件数算的，而逐件循环可能提前收尾（某件不守约定、槽半路拒收）⇒
     * <b>余量会比预检时大</b>，直接写就可能把配对那一格撑成超叠。撑不下就把流体整笔退回、本次一件不搬。
     * ★S7/T1：本判据随算法核下沉（{@code PocketFluidTransfer#canMoveIntoBothCells}，经 Cells 端口
     * 复用本类的槽件表与格读数），此处只留说明锚点。
     */

    /**
     * 一次搬运的两格落位：产物进本列出格（并上闩），余量进本列进格。
     * <p>
     * ★先写产物、后扣源格：真在中途出异常时最坏是"多一件产物"，反过来才是凭空少件。
     *
     * @param template  一件形状的产物（已由 {@code stackSize==1} 副本灌排出来）
     * @param moved     实际成交的件数
     * @param unit      源容器那一件的<b>未改动</b>形状副本，用于还原余量
     * @param restCount 没被处理的余量件数（0 ⇒ 整叠都处理完，进格清空）
     */
    private void placeProcessed(PocketInventory inv, int sourceIndex, ItemStack template, int moved, ItemStack unit,
        int restCount) {
        if (moved <= 0) {
            return;
        }
        final int resultCell = PocketInventory.outputInteractionSlotOf(sourceIndex);
        final int restCell = restCellOf(sourceIndex);
        final ModularSlot resultSlot = fluidSlotsByIndex.get(resultCell);
        final ModularSlot restSlot = fluidSlotsByIndex.get(restCell);
        if (resultSlot == null || restSlot == null) {
            return;
        }
        final ItemStack product = sized(template, moved);
        final ItemStack written = merged(inv, resultCell, resultCell == sourceIndex, product);
        fluidOutputLatch.put(resultCell, written.copy());
        resultSlot.putStack(written);
        if (restCell == sourceIndex) {
            restSlot.putStack(restCount > 0 ? sized(unit, restCount) : null);
            forceSyncSlot(restSlot);
        } else if (restCount > 0) {
            restSlot.putStack(merged(inv, restCell, false, sized(unit, restCount)));
            forceSyncSlot(restSlot);
        }
        // ★R87-g（O-缺口 2）：被写的<b>出格</b>同样要补强制同步——上面的 putStack 只发
        // 非 force 的 SYNC_ITEM（客户端不写 handler），vanilla 差分通路在"客户端曾本地模拟出同值"
        // （点击目标就是出格、或同拍"点击-搬运-回流"）时净差为零、整包不发 ⇒ 出格冻结到重开。
        // 余格的两支已在上面各自补过；与源格同一条正门（{@link #forceSyncSlot}）；重入已由
        // {@link #transferring} 闩覆盖（同下方 syncCellAfterRewrite 那条自证口径）；
        // forceSyncItem 活引用边角的「写格一律换引用」纪律保持。
        forceSyncSlot(resultSlot);
        // ★成交了 ⇒ 上一轮"这一格搬不动"的原因不再是事实，清掉抑制键；下一次真卡住要能再报
        fluidNoticeKeys.remove(sourceIndex);
        // ★<b>A（R84）</b>：被监听的这一格（{@code sourceIndex}）在上面被改写过，而 {@code placeProcessed}
        // 全程嵌在<b>那一格自己的</b> {@code ItemSlotSH#checkUpdate} 帧里（{@code onSlotUpdate} 在
        // {@code ItemSlotSH.java:70}，缓存与 announce 在它<b>返回之后</b>的 :74 与 :78-83）⇒
        // 那一帧手里的是<b>进 listener 之前</b>拍下的旧栈（{@code SlotItemHandler#getStack} 给的是
        // handler 里那个对象，我们随后用 {@code setStackInSlot} 换了引用，旧引用没有被改写），
        // 于是它把旧值又 announce + 缓存一遍，盖掉 {@code ModularSlot#putStack} 末尾那次内层 checkUpdate
        // （{@code ModularSlot.java:105}）已经发出去的真值 ⇒ 客户端当拍读到的仍是搬运前那一叠（幻象）。
        // ★R86 改判（实机「残影」条）：上面那句"客户端的显示走 vanilla 活值"是<b>错的</b>——vanilla 的基线是
        // <b>上次发包快照</b>而不是客户端信念（{@code Container.java:85-103}），而放入那一叠是客户端
        // {@code PlayerControllerMP.windowClick:475-481} <b>先本地模拟</b>出来的、服务端从未发过这一格 ⇒
        // 同拍内源格回到点击前的值 ⇒ 净差为零、整包不发；MUI 常规 {@code SYNC_ITEM} 的 {@code forceSync}
        // 恒 {@code false}（{@code ItemSlotSH.java:77}），客户端收到也只回缓存<b>不写 handler</b>；
        // 1.7.10 的 transaction 救援只比游标而 MUI2 的 {@code slotClick} 恒返 null
        // （{@code ModularContainer.java:361}）⇒ 恒 accept。<b>三条通道全失明</b>，残影冻结到开屏全量
        // {@code sendContainerAndContentsToPlayer} 才修——正是"打开关闭后才消失并更新"。
        // 正门是上游为同型问题准备的 {@code ItemSlotSH#forceSyncItem()}（{@code ItemSlotSH.java:120-133}，
        // 先例 {@code ModularCraftingSlot.java:155}）：只有它带 {@code forceSync = true}，客户端那一支才会
        // 真的 {@code slot.putStack(...)} 把 handler 改回真值。
        // ★重入自证：forceSyncItem → onSlotUpdate → onSlotChangedReal → changeListener 这一跳回到本类时
        // 全程在 {@link #moveFluidBetweenTanks} 的 {@code transferring = true} 之内 ⇒ 直接被闩挡住，不会递归
        // （{@code ModularContainer#onSlotChanged} 是空实现 {@code :231}，那一跳不产生副作用）。
        // ★已知边角：{@code forceSyncItem} 把 {@code lastStoredItem} 存成<b>活引用</b>而非 copy（上游实现如此）
        // ⇒ 后续若原地改同一对象的件数，下一次 {@code checkUpdate} 的差分会看不见；本仓写格一律换引用。
        syncCellAfterRewrite(sourceIndex);
    }

    /**
     * 见 {@link #placeProcessed} 末尾那条 ★A/★R86：对被改写的格补一次<b>强制</b>同步。
     * <p>
     * ★R86 起从只 announce 的 {@code checkUpdate()} 升级为 {@code forceSyncItem()}——非 force 的包
     * 客户端收下后<b>不写 handler</b>，而图标/件数恰恰是从 handler 读的（{@code ItemSlot.java:235-241}）。
     */
    private void syncCellAfterRewrite(int index) {
        forceSyncSlot(fluidSlotsByIndex.get(index));
    }

    /**
     * 装配前（{@code isInitialized()} 为假）不能碰：{@code forceSyncItem()} 内部<b>没有</b>
     * {@code checkUpdate()} 那两道 {@code isValid()}/{@code isClient} 守卫，早到会在
     * {@code getSyncManager()} 抛 {@code IllegalStateException}。
     */
    private static void forceSyncSlot(ModularSlot slot) {
        if (slot == null || !slot.isInitialized()) {
            return;
        }
        slot.getSyncHandler()
            .forceSyncItem();
    }

    /** 与那一格已有的同类同 NBT 内容合堆（放不放得下由 {@code PocketFluidTransfer} 内部的 {@code canPlacePair} 判过）。 */
    private static ItemStack merged(PocketInventory inv, int cell, boolean replaceable, ItemStack product) {
        if (replaceable) {
            return product;
        }
        final ItemStack current = inv.fluidInteraction()
            .getStackInSlot(cell);
        if (current == null) {
            return product;
        }
        final ItemStack mergedStack = current.copy();
        mergedStack.stackSize += product.stackSize;
        return mergedStack;
    }

    /** 余量落哪一格：源格自己就是出格 ⇒ 产物原地、余量挪进格；其余情形余量留在源格。 */
    private static int restCellOf(int sourceIndex) {
        return PocketInventory.outputInteractionSlotOf(sourceIndex) == sourceIndex
            ? PocketInventory.inputInteractionSlotOf(sourceIndex)
            : sourceIndex;
    }

    /**
     * 一件形状的栈按件数放大（只用于"放不放得下"的预检与落位写格，不改变 NBT）。
     * ★S7/T1：算式已下沉（{@link PocketFluidTransfer#sized}，算法核与落位两侧共用），此处保留薄转发
     * 以零改动 {@link #placeProcessed}。
     */
    private static ItemStack sized(ItemStack template, int count) {
        return PocketFluidTransfer.sized(template, count);
    }
}
