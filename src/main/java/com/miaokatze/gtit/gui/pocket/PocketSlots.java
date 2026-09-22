package com.miaokatze.gtit.gui.pocket;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceStore;
import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;
import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;

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
 */
public final class PocketSlots {

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
     * 不加闩就是无限递归。服务端主线程单点访问 ⇒ 单布尔足够，不加锁。
     */
    private static boolean transferring;

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

    /** 12 格的分流结论。 */
    public enum IncomingAction {
        /** 普通物品：留在格内等 S7 的 100 tick 蒸馏节拍。 */
        DISTILL,
        /** 有内容的源质容器：当场走注入支（不进蒸馏判定，R44c 改述）。 */
        INJECT,
        /** 空栈或空容器：不收。 */
        REJECT
    }

    /**
     * <b>唯一</b>分流函数（R63b：禁止在 {@code distill/} 与 GUI 两侧各写一套判据）。
     * 判据走 {@link EssenceGate#isContainer(ItemStack)} 的<b>接口探测</b>
     * （{@code IEssentiaContainerItem} + 单 aspect 容器，R31/R44a）⇒
     * <b>不得</b> {@code instanceof} 任何具体物品类（TC 的晶/瓶与第三方罐子自动同判）。
     * <p>
     * 本重载是生产入口（{@link EssenceGate#TAUM} ⇒ 转 {@code TaumCompat}）；
     * 回归套件用 {@link #classifyIncoming(ItemStack, EssenceGate)} 传桩件，两者是<b>同一段代码</b>。
     */
    public static IncomingAction classifyIncoming(ItemStack stack) {
        return classifyIncoming(stack, EssenceGate.TAUM);
    }

    /** 分流判定的实现本体（见 {@link #classifyIncoming(ItemStack)}）。 */
    public static IncomingAction classifyIncoming(ItemStack stack, EssenceGate gate) {
        if (stack == null || gate == null) {
            return IncomingAction.REJECT;
        }
        if (!gate.isContainer(stack)) {
            return IncomingAction.DISTILL;
        }
        final TaumAspectAmounts content = gate.readContainer(stack);
        return content == null || content.isEmpty() ? IncomingAction.REJECT : IncomingAction.INJECT;
    }

    /** 一次注入的结论（三条互斥出口，对应三条玩家可读到的文案）。 */
    public enum Intake {
        /** 不是容器 / 容器为空：本轮无事发生（对应 {@code REJECT}，格内保持原样）。 */
        NOTHING,
        /** 源质格放不下：★容器<b>分毫未动</b>（{@code still.inject_full}，R29 全有全无的失败面）。 */
        STORE_FULL,
        /** 已抽干并入账（{@code still.injected}）；容器变空，由调用方按 R40a 非消耗退回。 */
        DRAINED
    }

    /** 注入结果：结论 + 实际入账点数。 */
    public static final class IntakeResult {

        public final Intake kind;
        public final int points;

        IntakeResult(Intake kind, int points) {
            this.kind = kind;
            this.points = points;
        }

        public boolean drained() {
            return kind == Intake.DRAINED;
        }
    }

    /**
     * 注入支本体：把容器内容抽进 {@code store}。
     * <p>
     * <b>全有全无</b>（R29）：{@code canAcceptAll(候选) → drainContainer → putAll(候选)} 三段，
     * 顺序不能换——<b>预检必须在抽取之前</b>，否则"装不下"就成了"抽出来却没地方放"= 销毁价值。
     * 装不下 ⇒ 一格都不动、<b>容器分毫未动</b>（对应 {@code still.inject_full}）。
     * ⚠ 禁止用逐 tag 截断的 {@code add()} 做消耗判定（R45c/FIX-6：{@code isFull()} 只服务 GUI 置灰）。
     *
     * @param container 玩家放进 12 格里的栈（就地被抽干；调用方负责退回）
     */
    public static IntakeResult injectContainer(ItemStack container, PocketEssenceStore store, EssenceGate gate) {
        if (container == null || store == null || gate == null) {
            return new IntakeResult(Intake.NOTHING, 0);
        }
        if (classifyIncoming(container, gate) != IncomingAction.INJECT) {
            return new IntakeResult(Intake.NOTHING, 0);
        }
        final Map<String, Integer> candidates = toMap(gate.readContainer(container));
        if (candidates.isEmpty()) {
            return new IntakeResult(Intake.NOTHING, 0);
        }
        if (!store.canAcceptAll(candidates)) {
            return new IntakeResult(Intake.STORE_FULL, 0);
        }
        final TaumAspectAmounts drained = gate.drainContainer(container);
        if (drained == null || drained.isEmpty()) {
            return new IntakeResult(Intake.NOTHING, 0);
        }
        final int points = store.putAll(toMap(drained));
        return new IntakeResult(points > 0 ? Intake.DRAINED : Intake.NOTHING, points);
    }

    /**
     * 注入支的 GUI 外壳：把 {@link #injectContainer} 的结论翻译成槽位动作与玩家反馈。
     * <p>
     * 装得下 ⇒ 排空后的容器按 R40a 同法<b>非消耗</b>地退回玩家处（背包满则掉脚下），格清空。
     * 装不下 ⇒ 容器原样留在格内（一格都没动）。
     */
    private static void injectContainerIfAny(PocketInventory inv, ModularSlot slot) {
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
                tellPlayer(slot, "gtit.pocket.still.inject_full");
                return;
            }
            if (!result.drained()) {
                return;
            }
            inv.markDirty();
            tellPlayer(slot, "gtit.pocket.still.injected", result.points);
            returnToPlayer(slot, placed);
        } finally {
            transferring = false;
        }
    }

    /** 单点反馈（{@code still.injected} / {@code still.inject_full}，契约 §5）。 */
    private static void tellPlayer(ModularSlot slot, String key, Object... args) {
        final EntityPlayer player = ModularSlot.getPlayerSlotPlayer(slot);
        if (player == null) {
            return;
        }
        player.addChatMessage(
            new ChatComponentText(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted(key, args)));
    }

    private static Map<String, Integer> toMap(TaumAspectAmounts amounts) {
        final Map<String, Integer> map = new LinkedHashMap<>();
        if (amounts == null) {
            return map;
        }
        for (int index = 0; index < amounts.size(); index++) {
            final String tag = amounts.tagAt(index);
            final int amount = amounts.amountAt(index);
            if (tag != null && amount > 0) {
                map.merge(tag, amount, Integer::sum);
            }
        }
        return map;
    }

    /** 排空后的容器非消耗地退回（R40a：背包满则掉到玩家脚下，遵循原版 {@code transferStackInSlot} 语义）。 */
    private static void returnToPlayer(ModularSlot slot, ItemStack leftover) {
        slot.putStack(null);
        if (leftover == null) {
            return;
        }
        final EntityPlayer player = ModularSlot.getPlayerSlotPlayer(slot);
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
     * 两格任一放不下就<b>一格都不写</b>。★旧写法四条出口全用 {@code slot.putStack(...)} 写回<b>同一格</b>，
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
            // （GT5U MetaBaseItem.java:520/573、ItemVolumetricFlask.java:159/179 第一行就是 stackSize != 1 return），
            // 把格内的整叠原始栈递过去必然静默返回 0 —— 这是"不能装载 / 不能提取"的第二条独立死路。
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
     */
    private void drainIntoTank(PocketInventory inv, int sourceIndex, int tank, ItemStack unit, int units,
        FluidStack content) {
        final FluidStackTank target = inv.tankAt(tank);
        final int room = target.getCapacity() - target.getFluidAmount();
        if (room <= 0 || units <= 0 || content.amount <= 0) {
            return;
        }
        final int resultCell = PocketInventory.outputInteractionSlotOf(sourceIndex);
        if (unit.getItem() instanceof IFluidContainerItem container) {
            final int perUnit = content.amount;
            int count = (int) Math.min((long) units, (long) room / perUnit);
            int wantPerUnit = perUnit;
            if (count <= 0) {
                // 一整件都装不进槽（Iridium 8,192,000 起就可能出现，Osmium / Neutronium 两档更是直接大于 16M 槽）
                // ⇒ 只倒这一件的部分量；不允许部分倒空就等于这一档永远提不动
                count = 1;
                wantPerUnit = room;
            }
            final ItemStack product = unit.copy();
            product.stackSize = 1;
            final FluidStack probeDrained = container.drain(product, wantPerUnit, true);
            if (probeDrained == null || probeDrained.amount <= 0) {
                return;
            }
            product.stackSize = 1;
            count = Math.min(count, productRoom(inv, resultCell, resultCell == sourceIndex, product));
            if (count <= 0 || !canPlacePair(inv, sourceIndex, sized(product, count), restOf(unit, units, count))) {
                return;
            }
            int done = 0;
            int movedFluid = 0;
            for (int index = 0; index < count; index++) {
                final ItemStack each = unit.copy();
                each.stackSize = 1;
                final FluidStack got = container.drain(each, index == 0 ? wantPerUnit : perUnit, true);
                if (got == null || got.amount <= 0) {
                    break;
                }
                final int filled = target.fill(got, true);
                if (filled != got.amount) {
                    // 槽没全收下（该容器不守"说得出多少就倒得出多少"）⇒ 这一件作废：已进槽的退回槽、副本直接丢
                    // ★源栈全程未动，所以作废一件的代价是零 —— 反过来（先扣件再进槽）才会造出两处真相
                    if (filled > 0) {
                        target.drain(filled, true);
                    }
                    break;
                }
                done++;
                movedFluid += got.amount;
            }
            if (done > 0 && canMoveIntoBothCells(inv, sourceIndex, product, done, unit, units)) {
                placeProcessed(inv, sourceIndex, product, done, unit, units - done);
            } else if (movedFluid > 0) {
                // 实际成交件数比预检时小 ⇒ 余量比预检时大，可能撑爆配对那一格：整笔退回槽，本次一件不搬
                target.drain(movedFluid, true);
            }
            return;
        }
        // 注册表型满容器：满 / 空是两个不同 item，Forge 只有全有全无（drainFluidContainer 不接受量）
        final int perUnit = content.amount;
        final int fitableUnits = Math.min(units, room / perUnit);
        if (fitableUnits <= 0) {
            // 槽子装不下整份就不动（注册表型不支持部分倒空）——旧注释这条仍成立，只是它前面那道门修好才轮得到这里
            return;
        }
        final ItemStack product = FluidContainerRegistry.drainFluidContainer(unit);
        if (product == null) {
            return;
        }
        product.stackSize = 1;
        final int count = Math.min(fitableUnits, productRoom(inv, resultCell, resultCell == sourceIndex, product));
        if (count <= 0 || !canPlacePair(inv, sourceIndex, sized(product, count), restOf(unit, units, count))) {
            return;
        }
        final int accepted = target.fill(new FluidStack(content.getFluid(), perUnit * count), true);
        final int done = accepted / perUnit;
        if (done > 0 && canMoveIntoBothCells(inv, sourceIndex, product, done, unit, units)) {
            if (accepted > done * perUnit) {
                target.drain(accepted - done * perUnit, true);
            }
            placeProcessed(inv, sourceIndex, product, done, unit, units - done);
            return;
        }
        // 一件整份都换不来，或实际件数撑不下两格 ⇒ 已进槽的流体整笔退回槽、容器原样不动
        if (accepted > 0) {
            target.drain(accepted, true);
        }
    }

    /**
     * 第 {@code tank} 号槽 → 空容器（灌装）。满容器落本列出格；一次处理放得下的全部件。
     * <p>
     * ★注册表型空容器必须用<b>双参</b> {@code getContainerCapacity(FluidStack, ItemStack)}：单参在 Forge 里
     * 转成 {@code getContainerCapacity(null, container)}，而它先查的 {@code containerFluidMap} 是按<b>满容器</b>
     * 为键、第二段又因传入流体为 null 被跳过，空容器的键其实住在 {@code filledContainerMap}
     * （{@code FluidContainerRegistry.java:281-305}）⇒ 单参对一切注册表型空容器<b>恒返回 0</b>，
     * 旧代码那句 {@code containerCapacity <= 0 → return} 就是"不能装载"的第二条独立死路。
     */
    private void fillFromTank(PocketInventory inv, int sourceIndex, int tank, ItemStack unit, int units) {
        final FluidStackTank source = inv.tankAt(tank);
        final FluidStack bar = source.getFluid();
        if (bar == null || bar.amount <= 0 || units <= 0) {
            return;
        }
        final int available = bar.amount;
        final int resultCell = PocketInventory.outputInteractionSlotOf(sourceIndex);
        if (unit.getItem() instanceof IFluidContainerItem container) {
            final int capacity = container.getCapacity(unit);
            final FluidStack already = container.getFluid(unit);
            final int space = capacity - (already == null ? 0 : already.amount);
            if (space <= 0) {
                return;
            }
            int count = (int) Math.min((long) units, (long) available / space);
            int wantPerUnit = space;
            if (count <= 0) {
                // 槽里只剩不足一整件的零头 ⇒ 灌一件半满的，别把零头永远锁死在槽里
                count = 1;
                wantPerUnit = Math.min(space, available);
            }
            final ItemStack product = unit.copy();
            product.stackSize = 1;
            if (container.fill(product, new FluidStack(bar.getFluid(), wantPerUnit), true) <= 0) {
                return;
            }
            product.stackSize = 1;
            count = Math.min(count, productRoom(inv, resultCell, resultCell == sourceIndex, product));
            if (count <= 0 || !canPlacePair(inv, sourceIndex, sized(product, count), restOf(unit, units, count))) {
                return;
            }
            int done = 0;
            int movedFluid = 0;
            for (int index = 0; index < count; index++) {
                final FluidStack taken = source.drain(index == 0 ? wantPerUnit : space, true);
                if (taken == null || taken.amount <= 0) {
                    break;
                }
                final ItemStack each = unit.copy();
                each.stackSize = 1;
                final int got = container.fill(each, taken, true);
                if (got != taken.amount) {
                    // 该件不守"给多少进多少"⇒ 这一件作废：副本里的零头抽回、整份原路退回槽（源栈未动 ⇒ 零损耗）
                    if (got > 0) {
                        container.drain(each, got, true);
                    }
                    source.fill(taken, true);
                    break;
                }
                done++;
                movedFluid += taken.amount;
            }
            if (done > 0 && canMoveIntoBothCells(inv, sourceIndex, product, done, unit, units)) {
                placeProcessed(inv, sourceIndex, product, done, unit, units - done);
            } else if (movedFluid > 0) {
                // 实际成交件数比预检时小 ⇒ 余量比预检时大：已灌进件的流体抽回槽，本次一件不搬
                source.fill(new FluidStack(bar.getFluid(), movedFluid), true);
            }
            return;
        }
        final int perUnit = FluidContainerRegistry.getContainerCapacity(new FluidStack(bar.getFluid(), 1), unit);
        if (perUnit <= 0) {
            return;
        }
        // fillFluidContainer 是纯查询（返回注册表里那份满容器的克隆），不消耗入参 ⇒ 可以先拿它探形状
        final ItemStack product = FluidContainerRegistry
            .fillFluidContainer(new FluidStack(bar.getFluid(), perUnit), unit);
        if (product == null) {
            return;
        }
        product.stackSize = 1;
        final int count = Math.min(
            Math.min(units, available / perUnit),
            productRoom(inv, resultCell, resultCell == sourceIndex, product));
        if (count <= 0 || !canPlacePair(inv, sourceIndex, sized(product, count), restOf(unit, units, count))) {
            return;
        }
        final int need = perUnit * count;
        final FluidStack taken = source.drain(need, true);
        if (taken == null || taken.amount <= 0) {
            return;
        }
        final int done = taken.amount / perUnit;
        // 成批灌装：整叠里灌得进几件就写几件，没灌到的余量退回本列进格（旧写法是把整叠覆写成 1 件，
        // 余量不是"等下一次点击"而是当场被顶掉 —— D-9 的吃件面就在这条覆写上）
        if (done > 0 && canMoveIntoBothCells(inv, sourceIndex, product, done, unit, units)) {
            if (taken.amount > done * perUnit) {
                source.fill(new FluidStack(taken.getFluid(), taken.amount - done * perUnit), true);
            }
            placeProcessed(inv, sourceIndex, product, done, unit, units - done);
            return;
        }
        // 凑不成整件、或实际件数撑不下两格 ⇒ 从槽里取出的那一整份原路退回，容器一件没动
        source.fill(taken, true);
    }

    /** 某一格还收得下几件"与该产物同形"的栈（被异物占住 ⇒ 0）。 */
    private int productRoom(PocketInventory inv, int cell, boolean replaceable, ItemStack product) {
        if (replaceable) {
            // 该格此刻装的正是本次要被处理掉的那一叠 ⇒ 整格让位给产物，不合堆
            return product.getMaxStackSize();
        }
        final ItemStack current = inv.fluidInteraction()
            .getStackInSlot(cell);
        if (current == null) {
            return product.getMaxStackSize();
        }
        if (!current.isItemEqual(product) || !ItemStack.areItemStackTagsEqual(current, product)) {
            return 0;
        }
        return Math.max(0, Math.min(current.getMaxStackSize(), product.getMaxStackSize()) - current.stackSize);
    }

    /**
     * 出格与进格<b>两处都</b>放得下才动手。{@code putStack} 是无条件覆写，只写一半就是吃件 ⇒
     * 这一判必须发生在扣流体之前（判完就可以直接写，{@link #placeProcessed} 不再复核）。
     */
    private boolean canPlacePair(PocketInventory inv, int sourceIndex, ItemStack product, ItemStack rest) {
        final int resultCell = PocketInventory.outputInteractionSlotOf(sourceIndex);
        final int restCell = restCellOf(sourceIndex);
        if (fluidSlotsByIndex.get(resultCell) == null || fluidSlotsByIndex.get(restCell) == null) {
            return false;
        }
        if (productRoom(inv, resultCell, resultCell == sourceIndex, product) < product.stackSize) {
            return false;
        }
        return rest == null || restCell == sourceIndex || productRoom(inv, restCell, false, rest) >= rest.stackSize;
    }

    /**
     * 用<b>实际成交件数</b>再判一次两格落位。
     * <p>
     * 必需的理由：预检是按计划件数算的，而逐件循环可能提前收尾（某件不守约定、槽半路拒收）⇒
     * <b>余量会比预检时大</b>，直接写就可能把配对那一格撑成超叠。撑不下就把流体整笔退回、本次一件不搬。
     */
    private boolean canMoveIntoBothCells(PocketInventory inv, int sourceIndex, ItemStack template, int moved,
        ItemStack unit, int units) {
        return canPlacePair(inv, sourceIndex, sized(template, moved), restOf(unit, units, moved));
    }

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
        } else if (restCount > 0) {
            restSlot.putStack(merged(inv, restCell, false, sized(unit, restCount)));
        }
    }

    /** 与那一格已有的同类同 NBT 内容合堆（放不放得下由 {@link #canPlacePair} 判过）。 */
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

    /** 一件形状的栈按件数放大（只用于"放不放得下"的预检，不直接写进格子）。 */
    private static ItemStack sized(ItemStack template, int count) {
        final ItemStack stack = template.copy();
        stack.stackSize = count;
        return stack;
    }

    /** 没被处理的余量（与源容器同形同 NBT）；{@code null} = 整叠都处理完了。 */
    private static ItemStack restOf(ItemStack unit, int units, int moved) {
        return units - moved <= 0 ? null : sized(unit, units - moved);
    }
}
