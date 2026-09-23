package com.miaokatze.gtit.common.items.pocket;

import java.util.Collections;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;
import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.util.item.AEItemStack;

/**
 * 口袋通道的<b>源质支</b>（注入 + 抽取 + 通道单位换算）。
 * <p>
 * ★R87-A：从 {@code PocketAeChannelOps} 整体<b>纯搬移</b>而来（该类超非 UI 类 ~1000 行警戒线；
 * 源质支是其中自洽的一段——只依赖会话、批级元件/handler 缓存与动作源）。搬移不改任何行为：
 * 方法体逐字保留，只把宿主引用改成显式的 {@code PocketAeChannelOps ops} 首参（经该类新增的
 * 同包访问口 {@code session()} / {@code foundOfCached} / {@code handlerOfCached} /
 * {@code rememberPrototype} / {@code actionSource()}），{@code PocketAeChannelOps} 的
 * {@code inject}/{@code extract} 分派口委派过来。世界触点仍只在服务端一侧，本类不直接碰 tile。
 * <p>
 * ★★<b>R88 载体改判（用户裁定）：搬运载体 = TC 安瓿瓶 {@code ItemEssence}，一瓶 8 点</b>。
 * 本类的两条路与三段算术都随之换档，三条硬口径现在是：
 * <ul>
 * <li><b>不猜第三方 mod 的私有栈格式</b>（R31/R44a）：用 {@code IAEStackType.convertStackFromItem}
 * 以<b>自家装满的源质瓶</b>为探针反算"1 瓶 = 多少通道单位"；换不到 ⇒ 一次性 INFO 后按
 * "这条通道收不了源质"收口，绝不 {@code instanceof} 具体物品类。</li>
 * <li><b>单位语义</b>：口袋里"一点源质"经这条通道出去时不再是一枚晶折算的单位，而是
 * <b>一只满瓶（{@value TaumDistillRules#PHIAL_CAPACITY} 点）折算的单位</b>；实际倍率仍由对应 mod 的
 * {@code convertStackFromItem} 决定，本仓不写死。★代价（必须同时写进 tooltip 与交付说明）：
 * 走物品通道兜底时，"1 单位"从 1 点抬到 8 点，且<b>不足一瓶的零头两头都不搬</b>（下一条）。</li>
 * <li><b>整瓶粒度（裁定 C1）</b>：上传与下传的点数都先经 {@link TaumDistillRules#floorToPhialUnits(int)}
 * 向下取整到 8 的倍数 ⇒ 零头<b>留在原侧</b>（上传侧留盘、下传侧留元件），绝不为凑零头造半瓶；
 * 旧形状"零头不足 1 晶不扣自己的点"仍然是这条纪律的特例。</li>
 * </ul>
 * ★★<b>下传的落点也改了</b>（同一裁定第 2 条）：从"物化成整晶 → {@code session().depositItem} 进物品栏"
 * 改成"<b>读出容器点数 → 加回 72 格源质盘 → 容器就地消耗</b>"，因此本类不再向物品栏喷东西，
 * 也不再有任何产出晶的调用（裁定 C2）。
 * <p>
 * 纯 JVM 可测面只有那三段换算算术（{@link #carriersFromUnits}/{@link #unitsForCarriers}/
 * {@link TaumDistillRules#floorToPhialUnits(int)}）；其余属实机项（AE2 handler 拿不到），
 * 与 {@code PocketAeChannelOps} 同一条可测边界。
 */
final class PocketEssenceChannelOps {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 实验 E3 的降级日志去重集（"通道 × aspect tag"一条一行，随源质支搬来）。 */
    private static final java.util.Set<String> LOG_UNMATERIALIZED = new java.util.LinkedHashSet<>();

    private PocketEssenceChannelOps() {}

    /**
     * ★R86（缺陷 3）：口袋源质表 → 元件的<b>第三方源质</b>通道（推送向）。
     * <p>
     * ★R88 换载体后与 {@code extractEssence} 仍严格镜像：两边都用
     * {@code IAEStackType#convertStackFromItem} 以<b>一只装满的源质瓶</b>（
     * {@link TaumCompat#newFilledContainer(String, int)}，一瓶
     * {@value TaumDistillRules#PHIAL_CAPACITY} 点）反算/正算同一个"1 瓶 = 多少通道单位"，
     * 换算拿不到 ⇒ 判"这条通道收不了源质"，一次性 INFO 后按 {@code OK} 跳过
     * （R31/R44a：不猜 AE2 私有格式、不 {@code instanceof} 具体物品类）。
     * <p>
     * ★<b>单位语义</b>（tooltip 与交付说明双处声明）：出去的是<b>整瓶折算的通道单位</b>，倍率由对应 mod
     * 决定、本仓不写死；★<b>整瓶粒度</b>（裁定 C1）：本轮要搬的点数先向下取整到 8 的倍数，
     * <b>余数留在盘里且不扣点</b>（旧形状"1 点 = 1 枚晶"下这条取整是恒等操作，所以本轮才第一次有实际后果）。
     */
    static PocketChannelOps.Outcome injectEssenceSource(PocketAeChannelOps ops, PocketChannelOps.SourceSlot source,
        String diskuuid, String typeId) {
        final IAEStackType<?> type = InfinityStackTypes.byId(typeId);
        if (type == null || ops.session() == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final PocketFilterConfig.Filter payload = PocketFilterConfig.parseKey(source.contentKey);
        if (!(payload instanceof PocketFilterConfig.EssenceFilter)) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final String tag = ((PocketFilterConfig.EssenceFilter) payload).tag;
        final Integer stock = ops.session()
            .essenceStock()
            .get(tag);
        if (stock == null || stock <= 0) {
            // 该 tag 已被玩家取空（快照之后就变了）⇒ 跳过，不搬不扣
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final PocketAeChannelOps.Found found = ops.foundOfCached(diskuuid);
        final IMEInventoryHandler handler = ops.handlerOfCached(found, type, diskuuid);
        if (handler == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        // ★C1：想搬的点数向下取整到整瓶；不足一瓶 ⇒ 这一轮整条跳过（不扣点、不动元件）
        final int wantPoints = TaumDistillRules.floorToPhialUnits(Math.min(stock, source.count));
        if (wantPoints <= 0) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final int wantCarriers = TaumDistillRules.phialCountFor(wantPoints);
        final ItemStack singleCarrier = newFilledPhial(tag);
        final IAEStack<?> perCarrier = essenceStackFor(type, singleCarrier);
        if (perCarrier == null || perCarrier.getStackSize() <= 0L) {
            logUnmaterializableOnce(type.getId(), tag);
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final long unit = perCarrier.getStackSize();
        final IAEStack<?> probe = essenceStackFor(type, newFilledPhial(tag));
        if (probe == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        probe.setStackSize(unitsForCarriers(wantCarriers, unit));
        final long requestedUnits = probe.getStackSize();
        ops.rememberPrototype(source.contentKey, probe);
        final boolean writable = handler.getAccess()
            .hasPermission(AccessRestriction.WRITE);
        final boolean acceptable = handler.canAccept(probe);
        final IAEStack<?> leftover = handler.injectItems(probe, Actionable.MODULATE, PocketAeChannelOps.actionSource());
        final long leftoverUnits = leftover == null ? 0L : leftover.getStackSize();
        final PocketReceipt receipt = PocketReceipt.classify(true, writable, acceptable, requestedUnits, leftoverUnits);
        // 零头必须留在元件侧（不足一瓶的单位不收）——与抽取支"零头留下"是同一条纪律的两面
        final int refusedCarriers = carriersFromUnits(leftoverUnits, unit, wantCarriers);
        final int moved = Math.max(0, wantPoints - refusedCarriers * PocketConstants.ESSENCE_OUT_UNIT_POINTS);
        final int drained = moved <= 0 ? 0
            : ops.session()
                .drainEssence(tag, moved);
        if (moved > 0 && drained < moved) {
            // 扣点少于搬运量 ⇒ 把<b>差额</b>原路注回元件。★R88 修正一处旧形状：旧代码回补的是整笔
            // {@code moved}（而不是 {@code moved - drained}），等于凭空多塞给元件一份源质；
            // 换成瓶之后这条更明显（回补单位必须按整瓶折算），故一并收口，见报告"自报偏离"。
            refundUnits(handler, type, tag, moved - drained, unit);
            return new PocketChannelOps.Outcome(PocketReceipt.NO_ACCESS, 0);
        }
        return new PocketChannelOps.Outcome(receipt, moved);
    }

    /**
     * 造一只<b>装满</b>的源质瓶 —— 本类唯一允许的物品形状出口（★R88：不再有任何 {@code newCrystalStack}
     * 调用，裁定 C2「晶只读不产」在本类的执行形态）。
     * <p>
     * 单独成函数是因为三条路（上传探针、下传探针、下传落盘时的读回）都必须拿<b>同一档</b>容器：
     * 粒度取 {@link PocketConstants#ESSENCE_OUT_UNIT_POINTS}，不许在这里出现字面 8。
     */
    private static ItemStack newFilledPhial(String tag) {
        return TaumCompat.newFilledContainer(tag, PocketConstants.ESSENCE_OUT_UNIT_POINTS);
    }

    /**
     * 把"已经在元件侧但口袋里没扣成"的点数按<b>整瓶</b>折算注回该通道。
     * <p>
     * 向下取整是刻意的：不足一瓶的零头<b>无法</b>用一只瓶表达（C1 不许半瓶），这部分只能显式承认，
     * 所以这里在回补之外必须留下一行 INFO（守恒类事件不得静默，但也不该每拍刷）。
     */
    private static void refundUnits(IMEInventoryHandler handler, IAEStackType<?> type, String tag, int points,
        long unitPerCarrier) {
        if (handler == null || points <= 0) {
            return;
        }
        final int carriers = TaumDistillRules.phialCountFor(points);
        final int refundable = carriers * PocketConstants.ESSENCE_OUT_UNIT_POINTS;
        if (refundable > 0) {
            final IAEStack<?> back = essenceStackFor(type, newFilledPhial(tag));
            if (back != null) {
                back.setStackSize(unitsForCarriers(carriers, unitPerCarrier));
                handler.injectItems(back, Actionable.MODULATE, PocketAeChannelOps.actionSource());
            }
        }
        final int lost = points - refundable;
        if (lost > 0) {
            LOG.info(
                "[gtit] 口袋源质支：通道 {} 回补 {} 点时不足一瓶（tag={}，本轮剩 {} 点无处回补），" + "已按整瓶向下取整",
                type == null ? "?" : type.getId(),
                points,
                tag,
                lost);
        }
    }

    /**
     * ★源质支（R45b 缺口之二，需求 4 的源质声明 + R15 的第三条路径之一）。
     * <p>
     * 元件侧的源质栈格式属对应 mod 私有（实验 E3 保留），因此<b>不猜格式</b>：
     * 用 {@code IAEStackType.convertStackFromItem}（AE2 自己给第三方通道留的"TC4 aspect item → 通道栈"
     * 换算口）以<b>一只装满的自家源质瓶</b>为探针反算数额，★R88 起 <b>1 只瓶 =
     * {@value TaumDistillRules#PHIAL_CAPACITY} 点</b>（旧形状是 1 点 = 1 晶，
     * {@code TaumDistillRules.CRYSTAL_CAPACITY}，按裁定 C2 只保留"读得回旧晶"的识别，不再有产出）。
     * 换算拿不到 ⇒ 判"该通道不可物化"，一次性 INFO 后按 {@code NO_CHANNEL} 收口
     * （R31/R44a：不 {@code instanceof} 任何具体物品类）。
     * <p>
     * ★★<b>R88 改判：落点不再是物品栏</b>。旧实现在这里 {@code newCrystalStack(...)} 物化整晶后调
     * {@code session().depositItem(...)}（＝用户报的"下传变成结晶落在格子里"）。现在是
     * <b>读出容器点数 → 加回 72 格源质盘 → 容器就地消耗</b>：容器是本地临时栈，读完即弃，
     * 全程不进任何库存、不掉脚下，所以也没有"放不下就注回"的物品槽那一层——<b>放得下的判据换成了
     * 源质盘自己的全有全无预检</b>（{@code PocketEssenceStore.canAcceptAll}，R29 纪律同源），
     * 预检不过 ⇒ 一克都不从元件抽、回执 {@code TARGET_FULL}。
     * <p>
     * 其余口径与流体支同源："先问、后抽、抽了必须落得下、落不下就注回"。
     */
    static PocketChannelOps.Outcome extractEssence(PocketAeChannelOps ops, PocketFilterConfig.EssenceFilter filter,
        String diskuuid, int count) {
        final IAEStackType<?> type = InfinityStackTypes.byId(filter.typeId);
        if (type == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final PocketAeChannelOps.Found found = ops.foundOfCached(diskuuid);
        final IMEInventoryHandler handler = ops.handlerOfCached(found, type, diskuuid);
        if (handler == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0); // ★R84：没有该通道 ≠ 元件失联
        }
        if (ops.session() == null) {
            // 源质支的落点是口袋的源质盘（挂在活会话上），没有活会话就没有落点 ⇒ 根本不抽
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final PocketEssenceStore store = ops.session()
            .essence();
        if (store == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        // 一次一条声明至多补满该条声明的组上限（未设时 = FILTER_CAP_CEILING_ESSENCE 点），剩下的顺延下一拍
        // ★C1：先向下取整到整瓶 ⇒ 组上限停在非 8 倍数时那一档天然搬不动（PocketConstants 里有同一条提醒）
        final int wantPoints = TaumDistillRules
            .floorToPhialUnits(Math.min(count, PocketFilterConfig.resolveCap(filter, 0)));
        if (wantPoints <= 0) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final ItemStack carrier = newFilledPhial(filter.tag);
        final IAEStack<?> perCarrier = essenceStackFor(type, carrier);
        if (perCarrier == null || perCarrier.getStackSize() <= 0L) {
            logUnmaterializableOnce(type.getId(), filter.tag);
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final long unit = perCarrier.getStackSize();
        // ★落点预检在抽取<b>之前</b>：先按"盘里塞得下几只整瓶"把本轮瓶数收口，
        // 才去问元件有多少 —— 反过来的话"抽出来了却放不下"就只能把瓶扔掉或喷进物品栏，两条都是销毁价值。
        int wantCarriers = TaumDistillRules.phialCountFor(wantPoints);
        while (wantCarriers > 0 && !store.canAcceptAll(
            Collections.singletonMap(filter.tag, wantCarriers * PocketConstants.ESSENCE_OUT_UNIT_POINTS))) {
            wantCarriers--;
        }
        if (wantCarriers <= 0) {
            return new PocketChannelOps.Outcome(PocketReceipt.TARGET_FULL, 0);
        }
        final IAEStack<?> probe = essenceStackFor(type, newFilledPhial(filter.tag));
        if (probe == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        probe.setStackSize(unitsForCarriers(wantCarriers, unit));
        ops.rememberPrototype(filter.key(), probe);
        final IAEStack<?> simulated = handler
            .extractItems(probe, Actionable.SIMULATE, PocketAeChannelOps.actionSource());
        final long available = simulated == null ? 0L : simulated.getStackSize();
        final int carriers = carriersFromUnits(available, unit, wantCarriers);
        if (carriers <= 0) {
            // 元件里没有该 tag，或零头不足一瓶：不动，避免"抽得出通道单位、却放不下整瓶"
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        probe.setStackSize(unitsForCarriers(carriers, unit));
        final IAEStack<?> taken = handler.extractItems(probe, Actionable.MODULATE, PocketAeChannelOps.actionSource());
        final int takenCarriers = carriersFromUnits(taken == null ? 0L : taken.getStackSize(), unit, carriers);
        if (takenCarriers <= 0) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        // ---- 读容器 → 入账 → 消耗容器（本地临时栈，读完即弃 ⇒ 不落物品槽）----
        final int pointsPerCarrier = pointsOf(newFilledPhial(filter.tag));
        if (pointsPerCarrier <= 0) {
            // 桥说容器装好了、读回来却是空 ⇒ TC 版本漂移，宁可把这批原路注回，也不凭空记点数
            refundUnits(handler, type, filter.tag, takenCarriers * PocketConstants.ESSENCE_OUT_UNIT_POINTS, unit);
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final int intendedPoints = takenCarriers * pointsPerCarrier;
        final int credited = store.putAll(Collections.singletonMap(filter.tag, intendedPoints));
        if (credited < intendedPoints) {
            refundUnits(handler, type, filter.tag, intendedPoints - credited, unit);
        }
        if (credited > 0) {
            // 源质盘变了 ⇒ 必须打脏，否则这一笔只活在内存里、关屏/落盘时丢掉（R53c 的一次读一次写）
            ops.session()
                .markDirty();
        }
        return new PocketChannelOps.Outcome(
            credited >= intendedPoints ? PocketReceipt.OK : PocketReceipt.PARTIAL,
            credited);
    }

    /**
     * 读回一只容器装着的总点数（★R88：下传入账的"读容器"那一步）。
     * <p>
     * <b>不写死 8</b>：瓶的真实承载量由 {@code TaumCompat.readContainer} 从 {@code AspectList} 里读，
     * 与 {@link TaumDistillRules#PHIAL_CAPACITY} 是否相等<b>不在这里判</b>——本仓的取出粒度另有
     * {@code PocketConstants.ESSENCE_OUT_UNIT_POINTS} 作为唯一执法口径；这里只负责"读不出来就别记账"。
     */
    private static int pointsOf(ItemStack carrier) {
        if (carrier == null) {
            return 0;
        }
        final TaumAspectAmounts content = TaumCompat.readContainer(carrier);
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int index = 0; index < content.size(); index++) {
            final int amount = content.amountAt(index);
            if (amount > 0) {
                total += amount;
            }
        }
        return total;
    }

    /**
     * ★<b>R86（审查 B1）／R88 换载体</b>：把"一只装满的源质瓶"换算成<b>该通道自己</b>的堆栈形态；
     * 换不到 ⇒ null。（旧实现探针物是"一枚/一叠晶化源质"，★R88 起本类不再产出晶，见类注释 C2。）
     * <p>
     * 物品通道必须开特判，不是可选优化：AE2 的 {@code AEItemStackType#convertStackFromItem} 是
     * <b>无条件 {@code return null}</b> 的实现（{@code appeng/util/item/AEItemStackType.java:90-92}，
     * rv3-beta-1050 与归档的 beta-1000 两份参考树逐字相同）——"物品 → 物品堆"那条换算它压根不走这个口。
     * 而 R86 把源质声明的 {@code typeId} 回落到 {@code ITEM_STACK_TYPE}（缺陷 4 乙，用户裁定），
     * 于是若仍按通用探针对待，结果就是<b>声明被接受、遮罩画出来、抽取与注入永远 {@code NO_CHANNEL}／
     * 无事发生</b>的谎报面。特判直接 {@code AEItemStack.create(瓶)}，单位量天然为 1
     * （一只瓶 = 一个物品堆单元），故 {@link #carriersFromUnits} / {@link #unitsForCarriers}
     * 两侧都退化成恒等，不需要新算式。★玩家可见代价（C2 明写）：走这条兜底时"1 单位"= 一只瓶 = 8 点。
     */
    private static IAEStack<?> essenceStackFor(IAEStackType<?> type, ItemStack carrier) {
        if (type == null || carrier == null) {
            return null;
        }
        if (type == InfinityStackTypes.ITEM_STACK_TYPE) {
            return AEItemStack.create(carrier);
        }
        return type.convertStackFromItem(carrier);
    }

    /**
     * 通道单位 → 可物化的<b>整瓶只数</b>（<b>向下取整</b>：零头必须留在元件侧，
     * 否则"抽得出通道单位、放不下整瓶"就成了凭空销毁价值）。★R88 前同名概念是"晶化源质个数"。
     * <p>
     * 单独成函数的理由：AE2 handler 在本仓的零依赖套件里拿不到（源质支整体属实机项），但这条换算本身是
     * 纯算术，也是源质支<b>唯一</b>会静默吞点数的地方，因此由
     * {@code extract_essence_branch_yields_crystal}（★R88 起该用例需重挂到瓶，E3 待办）直接钉住。
     *
     * @param availableUnits 元件侧该 tag 现有的通道单位（SIMULATE 回报）
     * @param unitPerCarrier 一只装满的瓶对应多少通道单位（由 {@code convertStackFromItem} 实测）
     * @param wantCarriers   本轮最多要几只瓶（含落点预检与 {@code ESSENCE_OUT_MAX_PHIALS_PER_ACTION} 自缚）
     */
    static int carriersFromUnits(long availableUnits, long unitPerCarrier, int wantCarriers) {
        if (availableUnits <= 0L || unitPerCarrier <= 0L || wantCarriers <= 0) {
            return 0;
        }
        final long whole = availableUnits / unitPerCarrier;
        return whole <= 0L ? 0 : (int) Math.min((long) wantCarriers, whole);
    }

    /** 瓶数 → 通道单位（{@link #carriersFromUnits} 的逆运算；非正数一律 0）。 */
    static long unitsForCarriers(int carriers, long unitPerCarrier) {
        return carriers <= 0 || unitPerCarrier <= 0L ? 0L : (long) carriers * unitPerCarrier;
    }

    /** 实验 E3：某第三方通道的栈无法物化成源质瓶（★R88 前的说法是"晶化源质"），只报一次（每次抽取都刷一行会淹掉日志）。 */
    private static void logUnmaterializableOnce(String typeId, String tag) {
        if (LOG_UNMATERIALIZED.add(typeId + '#' + tag)) {
            LOG.info("[gtit] 口袋源质支：通道 {} 的条目（tag={}）无法物化为源质瓶，该声明按不可物化跳过", typeId, tag);
        }
    }
}
