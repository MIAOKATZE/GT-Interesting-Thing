package com.miaokatze.gtit.common.items.pocket;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStackType;
import appeng.util.item.AEFluidStack;

/**
 * 口袋通道的<b>流体支</b>（注入 + 抽取 + 请求量/退回量算术）。
 * <p>
 * ★R90 T3：从 {@code PocketAeChannelOps} 整体<b>纯搬移</b>而来（该类超非 UI 类 ~1000 行警戒线；
 * 流体支是其中自洽的一段——只依赖会话、批级元件/handler 缓存与动作源，与源质支同构）。
 * 搬移不改任何行为：方法体逐字保留，只把宿主引用改成显式的 {@code PocketAeChannelOps ops}
 * 首参（经该类既有的同包访问口 {@code session()} / {@code foundOfCached} / {@code handlerOfCached} /
 * {@code rememberPrototype} / {@code actionSource()}——★R87-A 为源质支开的同一批口，流体支复用），
 * {@code PocketAeChannelOps} 的 {@code inject}/{@code extract} 分派口委派过来。
 * <p>
 * ★<b>可变状态归属（本拆分的显式裁决）</b>：批级缓存（元件解析 / handler / contentKey）与
 * {@code prototypes} 原型表<b>留在宿主实例里</b>，本类经 {@code ops} 首参读写<b>同一份</b>——
 * 一批内物品/流体/源质三支共用同一套缓存与失效点（批尾 announce、快照头部、tick 变化），
 * 各持副本会让"同批同元件只解析一次"的复用面与失效时序分叉 = 行为变更，故不拆；
 * {@code SHRINK_OUT_OF_RANGE_WARNED} 一次性闩同理留宿主（物品注入支独用，单一实例 =
 * "进程维只报一次"的既有语义逐字不变）。本类自身<b>无任何可变状态</b>（全静态、无私有字段）。
 * <p>
 * 纯 JVM 可测面只有两条算术（{@link #fluidRequestFor}/{@link #fluidFallback}）；其余属实机项
 * （AE2 handler 与 Forge 流体对象在纯 JVM 里拿不到），与 {@code PocketAeChannelOps} 同一条可测边界。
 */
final class PocketFluidChannelOps {

    private PocketFluidChannelOps() {}

    /**
     * ★R86（缺陷 3）：口袋流体条 → 元件的<b>流体</b>通道（推送向）。
     * <p>
     * 三条纪律与物品支同源：① <b>先对表再投</b>——现读 tank 的内容键与量，和快照比不上一句就不搬
     * （玩家在两拍之间把槽换成别的流体是常态）；② <b>只有流体通道才收流体</b>——
     * {@code typeId != FLUID_STACK_TYPE} 时按"无事可做"跳过（OK 才不会 break 掉后面的来源）；
     * ③ <b>元件真收了才算数</b>——自己槽里扣得比对面收的少 ⇒ 差额原路注回并按 {@code NO_ACCESS} 收口
     * （{@code PocketAeChannelOps#shrinkSource} 那一条纪律，绝不允许"元件收了货、来源没扣件"的复制）。
     * <p>
     * ★为什么这一支不会重演 R84 的崩溃（{@code AEItemStack→IAEFluidStack} @
     * {@code InfinityTypedCellInventory:101}）：本方法<b>只在</b>流体 typeId 下构造
     * {@code AEFluidStack}；反过来的组合由物品支 :457 那道早退挡住。★<b>两道早退是成对的</b>，
     * 删任何一道都会把 CCE 放回来。
     */
    static PocketChannelOps.Outcome injectFluidSource(PocketAeChannelOps ops, PocketChannelOps.SourceSlot source,
        String diskuuid, String typeId) {
        if (ops.session() == null || !InfinityStackTypes.FLUID_STACK_TYPE.getId()
            .equals(typeId)) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final FluidStack now = ops.session()
            .fluidInTank(source.slot);
        if (now == null || now.amount <= 0
            || now.getFluid() == null
            || !PocketFilterConfig.fluidKey(
                now.getFluid()
                    .getName())
                .equals(source.contentKey)) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final IAEStackType<?> type = InfinityStackTypes.byId(typeId);
        final PocketAeChannelOps.Found found = ops.foundOfCached(diskuuid);
        final IMEInventoryHandler handler = found == null || type == null ? null
            : ops.handlerOfCached(found, type, diskuuid);
        if (handler == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final int requested = Math.min(now.amount, source.count);
        final IAEFluidStack request = AEFluidStack.create(new FluidStack(now.getFluid(), requested));
        if (request == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.LOST, 0);
        }
        ops.rememberPrototype(source.contentKey, request);
        final boolean writable = handler.getAccess()
            .hasPermission(AccessRestriction.WRITE);
        final boolean acceptable = handler.canAccept(request);
        final IAEFluidStack leftover = (IAEFluidStack) handler
            .injectItems(request, Actionable.MODULATE, PocketAeChannelOps.actionSource());
        final long leftoverSize = leftover == null ? 0L : leftover.getStackSize();
        final PocketReceipt receipt = PocketReceipt.classify(true, writable, acceptable, requested, leftoverSize);
        final int moved = (int) Math.max(0L, requested - leftoverSize);
        if (moved > 0 && ops.session()
            .drainOwnTank(source.slot, moved) < moved) {
            final IAEFluidStack back = AEFluidStack.create(new FluidStack(now.getFluid(), moved));
            if (back != null) {
                handler.injectItems(back, Actionable.MODULATE, PocketAeChannelOps.actionSource());
            }
            return new PocketChannelOps.Outcome(PocketReceipt.NO_ACCESS, 0);
        }
        return new PocketChannelOps.Outcome(receipt, moved);
    }

    /**
     * ★流体支（R45b 缺口之一，需求 4「按配置补满流体」的唯一通路）。
     * <p>
     * 三步固定顺序，<b>顺序本身就是不丢件的保证</b>：
     * <ol>
     * <li>先问落点还能收多少（{@code PocketSession#fluidBarRoom(int, FluidStack)}）——
     * 先抽后放会把超出部分凭空抹掉，AE2 侧已经扣了；</li>
     * <li>{@code SIMULATE} 出这一份，把请求量钳到"落点空间"与"元件可得"的较小值；</li>
     * <li>{@code MODULATE} 抽出来 → 灌进流体槽；<b>灌不进的部分立刻原路注回元件</b>
     * （同一 handler、同一 SOURCE，语义上就是"这一拍没发生过"）。落点完全不可用（会话已丢）
     * ⇒ 根本不抽，直接 {@code TARGET_FULL}。</li>
     * </ol>
     */
    static PocketChannelOps.Outcome extractFluid(PocketAeChannelOps ops, PocketFilterConfig.FluidFilter filter,
        String diskuuid, int count) {
        final Fluid fluid = FluidRegistry.getFluid(filter.fluidName);
        if (fluid == null) {
            // 声明里的流体已从注册表消失（整合包变更）：按"无事可做"继续下一条，不判失联
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final PocketAeChannelOps.Found found = ops.foundOfCached(diskuuid);
        final IMEInventoryHandler handler = ops.handlerOfCached(found, InfinityStackTypes.FLUID_STACK_TYPE, diskuuid);
        if (handler == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0); // ★R84：没有该通道 ≠ 元件失联（见物品支同处注释）
        }
        // ★落点 = 本条声明自己那一个 tank（R75①：ghost 的 slotIndex 就是 tank 号；R78② 后共 18 个 tank 各拉各的）
        final int tank = filter.slotIndex();
        final int room = ops.session() == null ? 0
            : ops.session()
                .fluidBarRoom(tank, new FluidStack(fluid, 1));
        if (room <= 0) {
            return new PocketChannelOps.Outcome(
                ops.session() == null ? PocketReceipt.NO_CHANNEL : PocketReceipt.TARGET_FULL,
                0);
        }
        final int request = fluidRequestFor(room, count);
        final IAEFluidStack probe = AEFluidStack.create(new FluidStack(fluid, request));
        if (probe == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.LOST, 0);
        }
        ops.rememberPrototype(filter.key(), probe);
        final IAEFluidStack simulated = (IAEFluidStack) handler
            .extractItems(probe, Actionable.SIMULATE, PocketAeChannelOps.actionSource());
        final long available = simulated == null ? 0L : simulated.getStackSize();
        if (available <= 0L) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final int want = fluidRequestFor((int) available, Math.min(room, PocketFilterConfig.resolveCap(filter, 0)));
        probe.setStackSize(want);
        final IAEFluidStack taken = (IAEFluidStack) handler
            .extractItems(probe, Actionable.MODULATE, PocketAeChannelOps.actionSource());
        final long takenAmount = taken == null ? 0L : taken.getStackSize();
        if (takenAmount <= 0L) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final int moved = ops.session()
            .depositFluid(tank, new FluidStack(fluid, (int) takenAmount));
        final int fallback = fluidFallback(takenAmount, moved);
        if (fallback > 0) {
            // 落点在抽取瞬间又变小了：把差额原路注回，绝不让流体凭空消失
            handler.injectItems(
                AEFluidStack.create(new FluidStack(fluid, fallback)),
                Actionable.MODULATE,
                PocketAeChannelOps.actionSource());
        }
        return new PocketChannelOps.Outcome(moved >= takenAmount ? PocketReceipt.OK : PocketReceipt.PARTIAL, moved);
    }

    /**
     * 流体支的请求量钳制：一次最多要"落点空间"与"本轮配额"的较小值。
     * <p>
     * 拉取模式的配额是 {@link PocketConstants#REFILL_AMOUNT_PER_FILTER_UNBOUNDED}（= 不设限），
     * 所以实际值恒等于落点空间 —— 这条算式是"先问落点再抽"的实现，写错一次就会超发；
     * 单独成函数是为了让零依赖套件能直接钉住它（AE2 handler 与 Forge 流体对象在纯 JVM 里都拿不到）。
     */
    static int fluidRequestFor(int room, int quota) {
        if (room <= 0 || quota <= 0) {
            return 0;
        }
        return Math.min(room, quota);
    }

    /**
     * 流体支的退回量：抽出来却没能落进条子的部分（必须原路注回元件）。
     * 负数与零一律返回 0（"全部落下"是正常路径，不该触发任何回滚）。
     */
    static int fluidFallback(long takenAmount, int moved) {
        if (takenAmount <= 0L) {
            return 0;
        }
        final long left = takenAmount - Math.max(0, moved);
        return left <= 0L ? 0 : (int) Math.min(left, Integer.MAX_VALUE);
    }
}
