package com.miaokatze.gtit.gui.pocket;

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
 * <b>{@value #TOTAL_REAL_SLOTS}</b>（R78 的口径，覆盖 R75 的 175、R74 的 185、§14.3/R43b 的 149）：
 * 中栏 150 + 流体交互 <b>36</b>（3 组 × 6 列 × 进/出）+ 蒸馏输入 12 + 绑定 1
 * = <b>199</b> 格由本工厂造，另有<b>玩家背包 36 格</b>由框架造 ⇒ 合计 235；
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
 * （<b>含整份 NBT 深比较</b>），玩家背包内容一变就把整枚口袋连同 199 格一起重发
 * （R53c 点名的包放大面）。这是用户为"要玩家背包"明确换回来的代价，README 与本注释同处点名。
 * 框架那一支的 {@code PlayerSlotGroup} rowSize=9、{@code allowShiftTransfer=true} ⇒
 * shift 点击在中栏与背包之间双向可用（旧 L2 档的"取出到背包"按钮因此是加速快捷键而非唯一出口）。
 */
public final class PocketSlots {

    /**
     * 本工厂造出的槽总数（R78：150 + 36 + 12 + 1 = <b>199</b>）。
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
    /** R78 定稿的真实 Container 槽总数（199 + 36 = <b>235</b> = 150 + 36 + 12 + 1 + 36）。 */
    public static final int TOTAL_REAL_SLOTS = FACTORY_REAL_SLOTS + PLAYER_BACKPACK_SLOTS;

    // ----------------------------- 槽组名（矩阵侧与 Container 侧必须逐字相同，R41d）
    public static final String GROUP_STORAGE = "pocket_storage";
    public static final String GROUP_FLUID = "pocket_fluid";
    public static final String GROUP_DISTILL = "pocket_distill";
    public static final String GROUP_BIND = "pocket_bind";

    /** 中栏每行 10 列（R75②；矩阵列宽与 {@code SlotGroup.rowSize} 同源）。 */
    public static final int STORAGE_COLUMNS = 10;
    /** 中栏行数（R75 的"选项 A"：16→15，1080p / GUI Scale 3 的 360 逻辑高是硬天花板）。 */
    public static final int STORAGE_ROWS = 15;
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
     * 存在的理由：R75 的"128→150"与 R78 的"48→72 / 6→18 / 175→235"都是跨文件的批量口径变更，
     * 最坏的失败形态不是编译不过，而是"矩阵行数改了、常量没改"或"常量改了、Container 注册的
     * 行数没改"这类<b>各自都能编译</b>的半改。这里把三者的乘积关系变成一条构造期就抛的断言，
     * 配套的负控在回归套件里（{@code slot_math_235_and_row_column_products}）。
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

    public PocketSlots() {}

    /** 中栏 150 格之一（行主序 {@code 0..149}，与矩阵字符序一致）。 */
    public ModularSlot storage(PocketInventory inv, int index) {
        createdRealSlots++;
        return new ModularSlot(inv.storage(), index).slotGroup(GROUP_STORAGE);
    }

    /**
     * 流体列的交互格之一（R75① + R78②：3 组 × 6 列 × 输入/输出 = <b>36</b> 格，<b>两格同权</b>：
     * 方向由放入的容器当前有无流体决定，R39a 的口径一个字都没改）。
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
     * {@link #PLAYER_BACKPACK_SLOTS} 后必须是 {@link #TOTAL_REAL_SLOTS}（R78：199 + 36 = 235）。
     *
     * @throws IllegalStateException 计数不符（双端同抛 ⇒ 首开即暴露，不会静默错位）
     */
    public void assertTotalRealSlots() {
        assertTotalRealSlots(createdRealSlots, PLAYER_BACKPACK_SLOTS);
    }

    /**
     * 加总判据的<b>本体</b>（两个入参形态 ⇒ 回归套件能直接喂 234 / 236 两个负控，
     * 不必为了构造"少一格"去把 199 个槽真造一遍）。
     * <p>
     * ★两条判据都要，不能只判合计：只判合计的话"工厂少造一格 + 背包多绑一格"会互相抵消成 235
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
                    + " 个 (150 中栏 + 36 流体交互(3 组×6×2) + 12 蒸馏输入 + 1 绑定格)");
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

    // ------------------------------------------------------------------ 流体列搬运（R39a 的两格同权 + R75 的按列落点）

    /**
     * 单格单向量搬运（目标 = 该格所属流体列的那一个 tank）：
     * <ul>
     * <li>格内容器<b>有流体</b> ⇒ 把流体抽进该列的 tank（容器变空）；</li>
     * <li>格内容器<b>是空的</b> ⇒ 从该列的 tank 灌满它。</li>
     * </ul>
     * 容量口径只报真实容量（{@code FluidStackTank} 未开 overflow ⇒ {@code getCapacity()} 即真容量），
     * 与 GT5U 的 {@code RealCapacityFluidTank} 同形（R46c）。
     * <p>
     * <b>不含任何按键分支</b>：手持储罐点流体槽的语义与 Tooltip 由 MUI2 原生
     * {@code FluidSlot}/{@code FluidSlotSyncHandler} 与 {@code modularui2.fluid.*} 键提供
     * （R30/R46c/L7），本方法只负责"格内容器 ↔ 本列 tank"这一路。
     */
    private static void moveFluidBetweenTanks(PocketInventory inv, int interactionIndex, ModularSlot slot) {
        if (transferring) {
            return;
        }
        final ItemStack placed = slot.getStack();
        if (placed == null) {
            return;
        }
        // 槽号 → tank 号的映射只允许住在 PocketInventory 里（两处真相的第一候选点）
        final int tank = PocketInventory.tankOfInteractionSlot(interactionIndex);
        transferring = true;
        try {
            final ItemStack probe = placed.copy();
            probe.stackSize = 1;
            final FluidStack inContainer = FluidContainerRegistry.getFluidForFilledItem(probe);
            if (inContainer != null && inContainer.amount > 0) {
                drainIntoTank(inv, tank, slot, placed, inContainer);
                return;
            }
            fillFromTank(inv, tank, slot, placed);
        } finally {
            transferring = false;
        }
    }

    /** 容器 → 第 {@code tank} 号流体槽。 */
    private static void drainIntoTank(PocketInventory inv, int tank, ModularSlot slot, ItemStack placed,
        FluidStack content) {
        final FluidStackTank target = inv.tankAt(tank);
        final int room = target.getCapacity() - target.getFluidAmount();
        if (room <= 0) {
            return;
        }
        if (placed.getItem() instanceof IFluidContainerItem container) {
            // NBT 型容器：允许部分倒空并回写剩余（R31 对 TC amount>=8 口径的放宽）
            final FluidStack wanted = new FluidStack(content, Math.min(room, content.amount));
            final FluidStack drained = container.drain(placed, wanted.amount, false);
            if (drained == null || drained.amount <= 0) {
                return;
            }
            final int accepted = target.fill(drained, true);
            if (accepted > 0) {
                container.drain(placed, accepted, true);
                slot.putStack(placed.stackSize > 0 ? placed : null);
            }
            return;
        }
        if (content.amount > room) {
            // 注册表型容器不支持部分倒空 ⇒ 槽子装不下整份就不动，避免"扣了流体没回写容器"
            return;
        }
        final ItemStack emptied = FluidContainerRegistry.drainFluidContainer(placed.copy());
        if (emptied == null) {
            return;
        }
        if (target.fill(new FluidStack(content, content.amount), true) > 0) {
            slot.putStack(emptied);
        }
    }

    /** 第 {@code tank} 号流体槽 → 空容器。 */
    private static void fillFromTank(PocketInventory inv, int tank, ModularSlot slot, ItemStack placed) {
        final FluidStackTank source = inv.tankAt(tank);
        final FluidStack bar = source.getFluid();
        if (bar == null || bar.amount <= 0) {
            return;
        }
        if (placed.getItem() instanceof IFluidContainerItem container) {
            final int capacity = container.getCapacity(placed);
            final FluidStack already = container.getFluid(placed);
            final int space = capacity - (already == null ? 0 : already.amount);
            if (space <= 0) {
                return;
            }
            final int fitting = container.fill(placed, new FluidStack(bar, Math.min(space, bar.amount)), false);
            if (fitting <= 0) {
                return;
            }
            final FluidStack drained = source.drain(fitting, true);
            if (drained != null && drained.amount > 0) {
                container.fill(placed, drained, true);
                slot.putStack(placed);
            }
            return;
        }
        final ItemStack probe = placed.copy();
        probe.stackSize = 1;
        final int containerCapacity = FluidContainerRegistry.getContainerCapacity(probe);
        if (containerCapacity <= 0) {
            return;
        }
        final ItemStack filledStack = FluidContainerRegistry
            .fillFluidContainer(new FluidStack(bar, Math.min(containerCapacity, bar.amount)), probe);
        final FluidStack filledFluid = filledStack == null ? null
            : FluidContainerRegistry.getFluidForFilledItem(filledStack);
        if (filledFluid == null || filledFluid.amount <= 0) {
            return;
        }
        final FluidStack drained = source.drain(filledFluid.amount, true);
        if (drained != null && drained.amount > 0) {
            // 一次只灌一件（堆叠的其余部分留在格内等下一次点击）
            filledStack.stackSize = 1;
            slot.putStack(filledStack);
        }
    }
}
