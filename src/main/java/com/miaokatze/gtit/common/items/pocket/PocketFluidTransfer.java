package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.cleanroommc.modularui.utils.fluid.FluidStackTank;

/**
 * 流体列「格内容器 ↔ 本列 tank」搬运的<b>算法核</b>（★S7/T1 自 {@code gui/pocket/PocketSlots}
 * 下沉，行为零变化：算术、分支与出口逐字保留）。
 * <p>
 * <b>接缝形态</b>：搬运算法本身是纯的，但它的落位 / 播报两侧天然长在面板的 UI 状态上
 * （播报抑制表 {@code fluidNoticeKeys}、槽件表 {@code fluidSlotsByIndex}、出格闩
 * {@code fluidOutputLatch} 与 {@code ModularSlot} 强同步）。这些<b>不随迁</b>——由调用方
 * （{@code PocketSlots}）实现 {@link Cells} 端口递进来；本类只经端口触达外部世界，
 * 自身<b>零 {@code gui.} 依赖</b>（common→gui 的反向 import 因此不新增）。
 * tank 与交互格的<b>索引真相</b>（{@code tankOfInteractionSlot} / {@code outputInteractionSlotOf}）
 * 也留在 gui 侧 {@code PocketInventory}（T3 才迁包），经端口转发。
 */
public final class PocketFluidTransfer {

    private PocketFluidTransfer() {}

    /**
     * 一次搬运所需的全部外部触点（由 GUI 侧实现；每个方法都只是对既有私有成员的转发，
     * 不携带自有逻辑 ⇒ 下沉前后行为逐字一致的机检口径）。
     */
    public interface Cells {

        /** 第 {@code tank} 号流体槽本体（{@code PocketInventory#tankAt(int)}）。 */
        FluidStackTank tank(int tank);

        /** 某个交互格当前的内容（{@code fluidInteraction().getStackInSlot(cell)}）。 */
        ItemStack cellStack(int cell);

        /** 源格的<b>本列出格</b>号（{@code PocketInventory#outputInteractionSlotOf(int)}，唯一映射点）。 */
        int outputCellOf(int sourceIndex);

        /** 余量落哪一格（gui 侧 {@code restCellOf}：源格自己就是出格 ⇒ 余量挪进格，其余留源格）。 */
        int restCellOf(int sourceIndex);

        /** 这一格的槽件是否已被本工厂装配（未装配 ⇒ 两格落位判否，防 NPE）。 */
        boolean hasSlot(int cell);

        /** 就某一格放弃一次搬运的播报（gui 侧 {@code notifyCell}：带同格同键只报一次的抑制）。 */
        void abandoned(int cell, String key, Object... args);

        /**
         * ★<b>R91-p（L 的流体执法腿，入口一问）</b>：玩家往第 {@code tank} 列<b>倒空容器</b>之前问一句
         * 「本列若挂了记忆，这份流体是不是它记住的那一种」。
         * <p>
         * ★判据本体住在 {@code PocketFilterConfig#memoryAllowsFluid}（三条口径的单源，与物品支
         * {@code allowsPlayerPlacement}、源质支 {@code memoryAllowsTag} 同一族）；本接口的 gui 侧实现
         * 只是<b>转发结论</b>（{@code inv.filters().memoryAllowsFluid(...)}），★不得含第二份 attr 读法。
         * ★只覆盖「灌入」这一向：灌装回抽（{@code fillFromTank}）与通道回滚走的是<b>抽取/归还</b>，
         * L 纯过滤、一条退液都不改变（与源质支"拦入账不拦取出"同形）。
         *
         * @param content 容器将要倒进本列的那份流体（调用方只在 {@code content != null && amount > 0} 时问）
         */
        boolean memoryAllowsFluid(int tank, FluidStack content);

        /**
         * ★★<b>R92-④（D4，流体支的"放置即配置"落档腿）</b>：一次<b>成功的灌入</b>之后，若本列处于
         * 「记忆档（L）+ 尚无声明（pending）」⇒ 以这份流体为本列建档。
         * <p>
         * ★调用点唯一：{@code placeProcessed} 真的把流体送进槽<b>之后</b>（判据是"流体确实进来了"，
         * 不是"玩家点过了"）⇒ 中途作废、整笔退回、槽满那几条支都不会建档。
         * ★gui 侧实现只是将结论转发给 {@code PocketFilterConfig#declare} 那一条唯一写入口，
         * 本接口不含第二份 attr 位表读法与第二份键式样（与 {@link #memoryAllowsFluid} 同一条纪律）。
         * ★已定档列<b>不覆盖</b>（P2 裁定：换声明走 NEI 拖入或手势，放置不改配置）。
         */
        void memoryDeclareFromIntake(int tank, FluidStack content);

        /**
         * 一次搬运的两格落位（gui 侧 {@code placeProcessed}：产物进本列出格并上闩、余量留本列进格、
         * 强制同步与抑制键清理）。
         */
        void placeProcessed(int sourceIndex, ItemStack template, int moved, ItemStack unit, int restCount);
    }

    // ★R84 播报键（中文文案见回执，交给主代理落 lang；每条都是"整笔放弃"或"一件未成"的出口，
    // 缺一条就等于把用户报的"卡住不动"重新变成静默）
    /** 目标 tank 已无空余容量（旧写法在 {@code room <= 0} 处直接 return，玩家看不到任何提示）。 */
    static final String KEY_TANK_FULL = "gtit.pocket.fluid.tank_full";
    /**
     * ★<b>R91-p</b>：本列挂了记忆 {@code L} 且记的不是这一种流体 ⇒ 整列拒收这次倒空
     * （★与 {@code tank_full} 分开成两条键：那是"等余量"，这是"换东西 / 撤记忆"，修法不同——
     * 与源质支 {@code intake.memory_locked} 同一条"两态两键"纪律）。
     */
    static final String KEY_TANK_MEMORY_LOCKED = "gtit.pocket.fluid.tank_memory_locked";
    /** 该列的 tank 里没有流体，灌装支无事可做。 */
    static final String KEY_TANK_EMPTY = "gtit.pocket.fluid.tank_empty";
    /** 该容器一次要倒的流体比本 tank 的空余容量还多，且这类容器不支持部分倒空。 */
    static final String KEY_UNIT_TOO_LARGE = "gtit.pocket.fluid.unit_too_large";
    /** 出格被<b>异种</b>物品占住，或被<b>同种</b>叠满到 maxStackSize ⇒ 产物无处落位。 */
    static final String KEY_OUTPUT_BLOCKED = "gtit.pocket.fluid.output_blocked";
    /** 两格里<b>另一格</b>放不下（余量塞不回去，或按实际成交件数复核时撑爆）。 */
    static final String KEY_NO_CELL_SPACE = "gtit.pocket.fluid.no_cell_space";
    /** 容器与槽各自守不住"说得出多少就倒得出多少"的约定 ⇒ 一件都没成交。 */
    static final String KEY_TRANSFER_REFUSED = "gtit.pocket.fluid.transfer_refused";
    /** 这一格里的东西不是可灌排的流体容器（或该容器装不下这种流体）。 */
    static final String KEY_UNSUPPORTED_CONTAINER = "gtit.pocket.fluid.unsupported_container";
    /** 该容器已经没有空位，灌不进去。 */
    static final String KEY_CONTAINER_FULL = "gtit.pocket.fluid.container_full";
    /** 本 tank 的存量不够灌满这一件容器（注册表型容器是全有全无，不支持半瓶）。 */
    static final String KEY_NOT_ENOUGH_FLUID = "gtit.pocket.fluid.not_enough_fluid";

    /**
     * 两格落位的件数收敛结果：{@code count} = 本次实际可搬件数，{@code failKey} ≠ null 表示一件都搬不动。
     */
    private static final class Plan {

        final int count;
        final String failKey;

        Plan(int count, String failKey) {
            this.count = count;
            this.failKey = failKey;
        }
    }

    /**
     * <b>★C-1（R84）：把"两格有一处放不下"从一次判死改成上下界收敛。</b>
     * <p>
     * 旧写法是 {@code count = Math.min(流体侧允许件数, productRoom(出格))} 之后紧跟
     * {@code if (count <= 0 || !canPlacePair(...)) return;}——出格一旦放不下，进格里那一叠就
     * <b>永远留在那里</b>，流体不再进 tank、件数不再减、一行提示都不发（用户报的"卡输入槽"主因）。
     * 这里按三条同时算：
     * <ul>
     * <li>产物侧上界 {@code outRoom}：出格还收得下几件（{@link #productRoom}，本身已允许与同种合堆 ⇒
     * 出格"没满到 maxStackSize"时把那部分搬完，就是用户要的<b>部分搬运</b>）；</li>
     * <li>件数上界 {@code units} 与流体侧上界 {@code want}；</li>
     * <li>余量侧<b>下界</b>：只有当余量要挪到<b>另一格</b>（源格本身就是出格）时才有约束——
     * 搬得越少余量越大 ⇒ 至少得搬 {@code units - restRoom} 件。</li>
     * </ul>
     * 上界与下界无交集 ⇒ 放弃，并把放弃的原因交回调用方播报（不再静默）。
     */
    private static Plan planPlacement(Cells cells, int sourceIndex, int resultCell, ItemStack product, ItemStack unit,
        int units, int want) {
        if (want <= 0 || units <= 0) {
            return new Plan(0, null);
        }
        final int outRoom = productRoom(cells, resultCell, resultCell == sourceIndex, product);
        if (outRoom <= 0) {
            return new Plan(0, KEY_OUTPUT_BLOCKED);
        }
        final int byFluid = Math.min(want, units);
        final int count = Math.min(byFluid, outRoom);
        final int restCell = cells.restCellOf(sourceIndex);
        if (restCell != sourceIndex) {
            final int restRoom = productRoom(cells, restCell, false, sized(unit, 1));
            if (units - restRoom > count) {
                return new Plan(0, KEY_NO_CELL_SPACE);
            }
        }
        return new Plan(count, null);
    }

    /**
     * 容器 → 第 {@code tank} 号槽（倒空）。一次处理 {@code units} 件里放得下的件数，空容器落本列出格。
     * <p>
     * ★顺序纪律：流体先进槽、容器后扣件，任何一步对不上就把已进槽的退回槽。源栈
     * （{@code unit} 的原件）全程未动，被改的永远是副本 ⇒ 回滚只需要还槽，不存在"扣了件没进槽"的两处真相。
     */
    public static void drainIntoTank(Cells cells, int sourceIndex, int tank, ItemStack unit, int units,
        FluidStack content) {
        // ★★<b>R91-p（L 的执法腿，流体支·入口一）</b>：记忆列 = "本列只能收它记住的那一种流体"。
        // 问在<b>room 计算与任何容器副本被碰之前</b>：拒 ⇒ 槽分毫未动、格内容器原样躺着（一件不扣）、
        // 出格零产物零闩 ⇒ 与 {@code KEY_TANK_FULL} 那一条"给整入口一个会说话的理由"同形。
        // ★判据不在此处：整条住在 PocketFilterConfig#memoryAllowsFluid，cells 那一步只转发结论。
        if (!cells.memoryAllowsFluid(tank, content)) {
            cells.abandoned(sourceIndex, KEY_TANK_MEMORY_LOCKED);
            return;
        }
        final FluidStackTank target = cells.tank(tank);
        final int room = target.getCapacity() - target.getFluidAmount();
        if (room <= 0) {
            // ★C-1 回执（旧写法：整条 if 直接 return，玩家只看到容器躺在进格里）
            cells.abandoned(sourceIndex, KEY_TANK_FULL);
            return;
        }
        if (units <= 0 || content.amount <= 0) {
            return;
        }
        final int resultCell = cells.outputCellOf(sourceIndex);
        if (unit.getItem() instanceof IFluidContainerItem container) {
            final int perUnit = content.amount;
            int count = (int) Math.min((long) units, (long) room / perUnit);
            int wantPerUnit = perUnit;
            final boolean probeIsPartial;
            if (count <= 0) {
                // 一整件都装不进<b>剩余</b>空间（Iridium 档单件 8,192,000 mB，Osmium / Neutronium 两档更大；
                // ★R96 S3：槽容量已换成未升级 20M / 升级 2G 两档 ⇒ "整件超一槽"的档位对照要按
                // fluidTankCapacityMl(当前档) 重读，不许照旧 16M 那一档的名单想当然）
                // ⇒ 只倒这一件的部分量；不允许部分倒空就等于这一档永远提不动
                count = 1;
                wantPerUnit = room;
                probeIsPartial = true;
            } else {
                probeIsPartial = false;
            }
            final ItemStack product = unit.copy();
            product.stackSize = 1;
            final FluidStack probeDrained = container.drain(product, wantPerUnit, true);
            if (probeDrained == null || probeDrained.amount <= 0) {
                // ★新死路（R84 取证）：这一支预设"接口型容器允许部分倒空"，但 GT5U
                // gregtech/api/items/MetaBaseItem.java:576-589 的那条注册表型满容器快路径要求
                // maxDrain >= tFluid.amount（:577），不满足才穿到 :590-605 的 GT.FluidContent NBT
                // （那一级才支持部分倒空）⇒ 携液量 > 本 tank 剩余容量的大型单元在这里拿到 null，
                // 旧写法静默 return。语义 = "这一件一次倒的比槽能装的还多，且它不给半瓶"。
                cells.abandoned(sourceIndex, probeIsPartial ? KEY_UNIT_TOO_LARGE : KEY_TRANSFER_REFUSED);
                return;
            }
            product.stackSize = 1;
            final Plan plan = planPlacement(cells, sourceIndex, resultCell, product, unit, units, count);
            if (plan.count <= 0) {
                cells.abandoned(sourceIndex, plan.failKey);
                return;
            }
            count = plan.count;
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
            if (done > 0 && canMoveIntoBothCells(cells, sourceIndex, product, done, unit, units)) {
                cells.placeProcessed(sourceIndex, product, done, unit, units - done);
                // ★★R92-④：流体<b>确实进槽</b>之后才谈建档（中途作废 / 整笔退回那两条支到不了这里）。
                cells.memoryDeclareFromIntake(tank, content);
            } else {
                if (movedFluid > 0) {
                    // 实际成交件数比预检时小 ⇒ 余量比预检时大，可能撑爆配对那一格：整笔退回槽，本次一件不搬
                    target.drain(movedFluid, true);
                }
                // ★C-1 回执：一件都没成交（容器中途反悔）与成交了但两格塞不下，是两种不同的玩家可见事实
                cells.abandoned(sourceIndex, done > 0 ? KEY_NO_CELL_SPACE : KEY_TRANSFER_REFUSED);
            }
            return;
        }
        // 注册表型满容器：满 / 空是两个不同 item，Forge 只有全有全无（drainFluidContainer 不接受量）
        final int perUnit = content.amount;
        final int fitableUnits = Math.min(units, room / perUnit);
        if (fitableUnits <= 0) {
            // 槽子装不下整份就不动（注册表型不支持部分倒空）——旧注释这条仍成立，只是它前面那道门修好才轮得到这里
            // ★C-1 回执：这条支的"装不下整份"就是 unit_too_large 的语义（room > 0 但 room < 一件的携液量）
            cells.abandoned(sourceIndex, KEY_UNIT_TOO_LARGE);
            return;
        }
        final ItemStack product = FluidContainerRegistry.drainFluidContainer(unit);
        if (product == null) {
            cells.abandoned(sourceIndex, KEY_TRANSFER_REFUSED);
            return;
        }
        product.stackSize = 1;
        final Plan plan = planPlacement(cells, sourceIndex, resultCell, product, unit, units, fitableUnits);
        if (plan.count <= 0) {
            cells.abandoned(sourceIndex, plan.failKey);
            return;
        }
        final int count = plan.count;
        final int accepted = target.fill(new FluidStack(content.getFluid(), perUnit * count), true);
        final int done = accepted / perUnit;
        if (done > 0 && canMoveIntoBothCells(cells, sourceIndex, product, done, unit, units)) {
            if (accepted > done * perUnit) {
                target.drain(accepted - done * perUnit, true);
            }
            cells.placeProcessed(sourceIndex, product, done, unit, units - done);
            // ★★R92-④：注册表型/NBT 型容器这一条支也是"流体确实进槽"的成功点 ⇒ 同样只在这里建档
            cells.memoryDeclareFromIntake(tank, content);
            return;
        }
        // 一件整份都换不来，或实际件数撑不下两格 ⇒ 已进槽的流体整笔退回槽、容器原样不动
        if (accepted > 0) {
            target.drain(accepted, true);
        }
        cells.abandoned(sourceIndex, done > 0 ? KEY_NO_CELL_SPACE : KEY_TRANSFER_REFUSED);
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
    public static void fillFromTank(Cells cells, int sourceIndex, int tank, ItemStack unit, int units) {
        final FluidStackTank source = cells.tank(tank);
        final FluidStack bar = source.getFluid();
        if (bar == null || bar.amount <= 0 || units <= 0) {
            // ★C-1 回执（旧写法：tank 是空的时候往这一格放东西，一句"这里没流体"都没有）
            if (units > 0) {
                cells.abandoned(sourceIndex, KEY_TANK_EMPTY);
            }
            return;
        }
        final int available = bar.amount;
        final int resultCell = cells.outputCellOf(sourceIndex);
        if (unit.getItem() instanceof IFluidContainerItem container) {
            final int capacity = container.getCapacity(unit);
            final FluidStack already = container.getFluid(unit);
            final int space = capacity - (already == null ? 0 : already.amount);
            if (space <= 0) {
                cells.abandoned(sourceIndex, KEY_CONTAINER_FULL);
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
                // 灌不进去：接口自己反悔（流体不受 / 该容器不给写；含"零头灌不进半瓶"那一探）
                cells.abandoned(sourceIndex, KEY_TRANSFER_REFUSED);
                return;
            }
            product.stackSize = 1;
            final Plan plan = planPlacement(cells, sourceIndex, resultCell, product, unit, units, count);
            if (plan.count <= 0) {
                cells.abandoned(sourceIndex, plan.failKey);
                return;
            }
            count = plan.count;
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
            if (done > 0 && canMoveIntoBothCells(cells, sourceIndex, product, done, unit, units)) {
                cells.placeProcessed(sourceIndex, product, done, unit, units - done);
            } else {
                if (movedFluid > 0) {
                    // 实际成交件数比预检时小 ⇒ 余量比预检时大：已灌进件的流体抽回槽，本次一件不搬
                    source.fill(new FluidStack(bar.getFluid(), movedFluid), true);
                }
                cells.abandoned(sourceIndex, done > 0 ? KEY_NO_CELL_SPACE : KEY_TRANSFER_REFUSED);
            }
            return;
        }
        final int perUnit = FluidContainerRegistry.getContainerCapacity(new FluidStack(bar.getFluid(), 1), unit);
        if (perUnit <= 0) {
            // 双参都问不出容量 ⇒ 这一格里的东西根本不是你 Swap 得动的注册表型空容器
            cells.abandoned(sourceIndex, KEY_UNSUPPORTED_CONTAINER);
            return;
        }
        // fillFluidContainer 是纯查询（返回注册表里那份满容器的克隆），不消耗入参 ⇒ 可以先拿它探形状
        final ItemStack product = FluidContainerRegistry
            .fillFluidContainer(new FluidStack(bar.getFluid(), perUnit), unit);
        if (product == null) {
            // 注册表里没有"该流体 + 该空容器"这一对 ⇒ 换一种流体或换容器
            cells.abandoned(sourceIndex, KEY_UNSUPPORTED_CONTAINER);
            return;
        }
        product.stackSize = 1;
        if (available < perUnit) {
            // 注册表型是全有全无：槽里的存量灌不满一件整的，且它不支持半瓶（接口型那一支才支持）
            cells.abandoned(sourceIndex, KEY_NOT_ENOUGH_FLUID);
            return;
        }
        final Plan plan = planPlacement(
            cells,
            sourceIndex,
            resultCell,
            product,
            unit,
            units,
            Math.min(units, available / perUnit));
        if (plan.count <= 0) {
            cells.abandoned(sourceIndex, plan.failKey);
            return;
        }
        final int count = plan.count;
        final int need = perUnit * count;
        final FluidStack taken = source.drain(need, true);
        if (taken == null || taken.amount <= 0) {
            cells.abandoned(sourceIndex, KEY_TRANSFER_REFUSED);
            return;
        }
        final int done = taken.amount / perUnit;
        // 成批灌装：整叠里灌得进几件就写几件，没灌到的余量退回本列进格（旧写法是把整叠覆写成 1 件，
        // 余量不是"等下一次点击"而是当场被顶掉 —— D-9 的吃件面就在这条覆写上）
        if (done > 0 && canMoveIntoBothCells(cells, sourceIndex, product, done, unit, units)) {
            if (taken.amount > done * perUnit) {
                source.fill(new FluidStack(taken.getFluid(), taken.amount - done * perUnit), true);
            }
            cells.placeProcessed(sourceIndex, product, done, unit, units - done);
            return;
        }
        // 凑不成整件、或实际件数撑不下两格 ⇒ 从槽里取出的那一整份原路退回，容器一件没动
        source.fill(taken, true);
        cells.abandoned(sourceIndex, done > 0 ? KEY_NO_CELL_SPACE : KEY_TRANSFER_REFUSED);
    }

    /** 某一格还收得下几件"与该产物同形"的栈（被异物占住 ⇒ 0）。 */
    private static int productRoom(Cells cells, int cell, boolean replaceable, ItemStack product) {
        if (replaceable) {
            // 该格此刻装的正是本次要被处理掉的那一叠 ⇒ 整格让位给产物，不合堆
            return product.getMaxStackSize();
        }
        final ItemStack current = cells.cellStack(cell);
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
     * 这一判必须发生在扣流体之前（判完就可以直接写，落位端口不再复核）。
     */
    private static boolean canPlacePair(Cells cells, int sourceIndex, ItemStack product, ItemStack rest) {
        final int resultCell = cells.outputCellOf(sourceIndex);
        final int restCell = cells.restCellOf(sourceIndex);
        if (!cells.hasSlot(resultCell) || !cells.hasSlot(restCell)) {
            return false;
        }
        if (productRoom(cells, resultCell, resultCell == sourceIndex, product) < product.stackSize) {
            return false;
        }
        return rest == null || restCell == sourceIndex || productRoom(cells, restCell, false, rest) >= rest.stackSize;
    }

    /**
     * 用<b>实际成交件数</b>再判一次两格落位。
     * <p>
     * 必需的理由：预检是按计划件数算的，而逐件循环可能提前收尾（某件不守约定、槽半路拒收）⇒
     * <b>余量会比预检时大</b>，直接写就可能把配对那一格撑成超叠。撑不下就把流体整笔退回、本次一件不搬。
     */
    private static boolean canMoveIntoBothCells(Cells cells, int sourceIndex, ItemStack template, int moved,
        ItemStack unit, int units) {
        return canPlacePair(cells, sourceIndex, sized(template, moved), restOf(unit, units, moved));
    }

    /**
     * 一件形状的栈按件数放大（只用于"放不放得下"的预检与落位写格，不改变 NBT）。
     * <p>
     * ★S7/T1 起 gui 侧落位（{@code PocketSlots#placeProcessed}）与算法核共用这一条算式，
     * 别在两侧各写一份。
     */
    public static ItemStack sized(ItemStack template, int count) {
        final ItemStack stack = template.copy();
        stack.stackSize = count;
        return stack;
    }

    /** 没被处理的余量（与源容器同形同 NBT）；{@code null} = 整叠都处理完了。 */
    private static ItemStack restOf(ItemStack unit, int units, int moved) {
        return units - moved <= 0 ? null : sized(unit, units - moved);
    }
}
