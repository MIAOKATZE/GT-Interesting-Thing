package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;

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
 * {@code inject}/{@code extract} 分派口委派过来。世界触点仍只在宿主一侧，本类不直接碰 tile。
 * <p>
 * 三条硬口径随搬移原样保留：
 * <ul>
 * <li><b>不猜第三方 mod 的私有栈格式</b>（R31/R44a）：用 {@code IAEStackType.convertStackFromItem}
 * 以自家 {@code ItemCrystalEssence} 为探针反算"1 晶 = 多少通道单位"；换不到 ⇒ 一次性 INFO 后按
 * "这条通道收不了源质"收口，绝不 {@code instanceof} 具体物品类。</li>
 * <li><b>单位语义</b>：口袋里的"一点源质"经这条通道出去时是一枚晶化源质折算而来的通道单位，
 * 实际倍率由对应 mod 的 {@code convertStackFromItem} 决定，本仓不写死。</li>
 * <li><b>零头留在元件侧</b>：不足 1 晶的单位不扣自己的点（{@link #crystalsFromUnits} 向下取整，
 * {@link #unitsForCrystals} 是它的逆运算）。</li>
 * </ul>
 * 纯 JVM 可测面只有两条换算算术（{@link #crystalsFromUnits}/{@link #unitsForCrystals}）；
 * 其余属实机项（AE2 handler 拿不到），与 {@code PocketAeChannelOps} 同一条可测边界。
 */
final class PocketEssenceChannelOps {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 实验 E3 的降级日志去重集（"通道 × aspect tag"一条一行，随源质支搬来）。 */
    private static final java.util.Set<String> LOG_UNMATERIALIZED = new java.util.LinkedHashSet<>();

    private PocketEssenceChannelOps() {}

    /**
     * ★R86（缺陷 3）：口袋源质表 → 元件的<b>第三方源质</b>通道（推送向）。
     * 与 {@code extractEssence} 严格镜像：那边用 {@code IAEStackType#convertStackFromItem} 以一枚自家晶
     * 反算"1 晶 = 多少通道单位"，这边拿同一个单位量正向换算后投递；换算拿不到 ⇒ 判"这条通道收不了
     * 源质"，一次性 INFO 后按 {@code OK} 跳过（R31/R44a：不猜 AE2 私有格式、不 {@code instanceof} 具体物品类）。
     * <p>
     * ★单位语义（游戏内 tooltip 与交付说明双处声明过）：口袋里的"一点源质"经这条通道出去时是
     * <b>一枚晶化源质</b>折算而来的通道单位，实际倍率由对应 mod 的 {@code convertStackFromItem} 决定，
     * 本仓不写死（写死就是 R85 耦合审计点名的"把转储常量当真相"）。
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
        final ItemStack single = TaumCompat.newCrystalStack(tag, 1);
        final IAEStack<?> perPoint = essenceStackFor(type, single);
        if (perPoint == null || perPoint.getStackSize() <= 0L) {
            logUnmaterializableOnce(type.getId(), tag);
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final long unit = perPoint.getStackSize();
        final int wantPoints = Math.min(stock, source.count);
        final ItemStack probeStack = TaumCompat.newCrystalStack(tag, wantPoints);
        final IAEStack<?> probe = essenceStackFor(type, probeStack);
        if (probe == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        probe.setStackSize(unitsForCrystals(wantPoints, unit));
        final long requestedUnits = probe.getStackSize();
        ops.rememberPrototype(source.contentKey, probe);
        final boolean writable = handler.getAccess()
            .hasPermission(AccessRestriction.WRITE);
        final boolean acceptable = handler.canAccept(probe);
        final IAEStack<?> leftover = handler.injectItems(probe, Actionable.MODULATE, PocketAeChannelOps.actionSource());
        final long leftoverUnits = leftover == null ? 0L : leftover.getStackSize();
        final PocketReceipt receipt = PocketReceipt.classify(true, writable, acceptable, requestedUnits, leftoverUnits);
        // 零头必须留在元件侧（不足 1 晶的单位不扣自己的点）——与抽取支"零头留下"是同一条纪律的两面
        final int refused = crystalsFromUnits(leftoverUnits, unit, wantPoints);
        final int moved = Math.max(0, wantPoints - refused);
        if (moved > 0 && ops.session()
            .drainEssence(tag, moved) < moved) {
            final IAEStack<?> back = essenceStackFor(type, TaumCompat.newCrystalStack(tag, moved));
            if (back != null) {
                handler.injectItems(back, Actionable.MODULATE, PocketAeChannelOps.actionSource());
            }
            return new PocketChannelOps.Outcome(PocketReceipt.NO_ACCESS, 0);
        }
        return new PocketChannelOps.Outcome(receipt, moved);
    }

    /**
     * ★源质支（R45b 缺口之二，需求 4 的源质声明 + R15 的第三条路径之一）。
     * <p>
     * 元件侧的源质栈格式属对应 mod 私有（实验 E3 保留），因此<b>不猜格式</b>：
     * 用 {@code IAEStackType.convertStackFromItem}（AE2 自己给第三方通道留的"TC4 aspect item → 通道栈"
     * 换算口）以自家 {@code ItemCrystalEssence} 为探针反算数额，1 点 = 1 晶
     * （{@code TaumDistillRules.CRYSTAL_CAPACITY}）。换算拿不到 ⇒ 判"该通道不可物化"，
     * 一次性 INFO 后按 {@code NO_CHANNEL} 收口（R31/R44a：不 {@code instanceof} 任何具体物品类）。
     * <p>
     * 落点同样是"先问、后抽、抽了必须放得下、放不下就注回"（与流体支同一纪律）。
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
            // 源质支的落点是口袋真实栏（晶化源质是物品），没有活会话就没有落点 ⇒ 根本不抽
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        // 一次一条声明至多补满该条声明的组上限（未设时 = ESSENCE_CAP_PER_TAG 一整堆晶），剩下的顺延下一拍
        final int wantPoints = Math.min(count, PocketFilterConfig.resolveCap(filter, 0));
        final ItemStack single = TaumCompat.newCrystalStack(filter.tag, 1);
        final IAEStack<?> perPoint = essenceStackFor(type, single);
        if (perPoint == null || perPoint.getStackSize() <= 0L) {
            logUnmaterializableOnce(type.getId(), filter.tag);
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        final long unit = perPoint.getStackSize();
        final ItemStack probeStack = TaumCompat.newCrystalStack(filter.tag, wantPoints);
        final IAEStack<?> probe = essenceStackFor(type, probeStack);
        if (probe == null) {
            return new PocketChannelOps.Outcome(PocketReceipt.NO_CHANNEL, 0);
        }
        ops.rememberPrototype(filter.key(), probe);
        final IAEStack<?> simulated = handler
            .extractItems(probe, Actionable.SIMULATE, PocketAeChannelOps.actionSource());
        final long available = simulated == null ? 0L : simulated.getStackSize();
        final int points = crystalsFromUnits(available, unit, wantPoints);
        if (points <= 0) {
            // 元件里没有该 tag，或零头不足 1 晶：不动，避免"抽了通道单位却放不下整晶"
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        probe.setStackSize(unitsForCrystals(points, unit));
        final IAEStack<?> taken = handler.extractItems(probe, Actionable.MODULATE, PocketAeChannelOps.actionSource());
        final int takenPoints = crystalsFromUnits(taken == null ? 0L : taken.getStackSize(), unit, points);
        if (takenPoints <= 0) {
            return new PocketChannelOps.Outcome(PocketReceipt.OK, 0);
        }
        final ItemStack crystals = TaumCompat.newCrystalStack(filter.tag, takenPoints);
        final int moved = crystals == null ? 0
            : ops.session()
                .depositItem(crystals.copy());
        if (moved < takenPoints) {
            final int back = takenPoints - Math.max(0, moved);
            final ItemStack backStack = TaumCompat.newCrystalStack(filter.tag, back);
            final IAEStack<?> backRequest = essenceStackFor(type, backStack);
            if (backRequest != null) {
                handler.injectItems(backRequest, Actionable.MODULATE, PocketAeChannelOps.actionSource());
            }
        }
        return new PocketChannelOps.Outcome(moved >= takenPoints ? PocketReceipt.OK : PocketReceipt.PARTIAL, moved);
    }

    /**
     * ★<b>R86（审查 B1）</b>：把"一枚/一叠晶化源质"换算成<b>该通道自己</b>的堆栈形态；换不到 ⇒ null。
     * <p>
     * 物品通道必须开特判，不是可选优化：AE2 的 {@code AEItemStackType#convertStackFromItem} 是
     * <b>无条件 {@code return null}</b> 的实现（{@code appeng/util/item/AEItemStackType.java:90-92}，
     * rv3-beta-1050 与归档的 beta-1000 两份参考树逐字相同）——"物品 → 物品堆"那条换算它压根不走这个口。
     * 而 R86 把源质声明的 {@code typeId} 回落到 {@code ITEM_STACK_TYPE}（缺陷 4 乙，用户裁定），
     * 于是若仍按通用探针对待，结果就是<b>声明被接受、遮罩画出来、抽取与注入永远 {@code NO_CHANNEL}／
     * 无事发生</b>的谎报面。特判直接 {@code AEItemStack.create(晶)}，单位量天然为 1（一枚晶 = 一个物品堆
     * 单元），故 {@link #crystalsFromUnits} / {@link #unitsForCrystals} 两侧都退化成恒等，不需要新算式。
     */
    private static IAEStack<?> essenceStackFor(IAEStackType<?> type, ItemStack crystal) {
        if (type == null || crystal == null) {
            return null;
        }
        if (type == InfinityStackTypes.ITEM_STACK_TYPE) {
            return AEItemStack.create(crystal);
        }
        return type.convertStackFromItem(crystal);
    }

    /**
     * 通道单位 → 可物化的晶化源质个数（<b>向下取整</b>：零头必须留在元件侧，
     * 否则"抽得出通道单位、放不下整晶"就成了凭空销毁价值）。
     * <p>
     * 单独成函数的理由：AE2 handler 在本仓的零依赖套件里拿不到（源质支整体属实机项），但这条换算本身是
     * 纯算术，也是源质支<b>唯一</b>会静默吞点数的地方，因此由
     * {@code extract_essence_branch_yields_crystal} 直接钉住。
     *
     * @param availableUnits 元件侧该 tag 现有的通道单位（SIMULATE 回报）
     * @param unitPerCrystal 一枚晶化源质对应多少通道单位（由 {@code convertStackFromItem} 实测）
     * @param wantCrystals   本轮最多要几晶（含 {@code ESSENCE_CAP_PER_TAG} 自缚）
     */
    static int crystalsFromUnits(long availableUnits, long unitPerCrystal, int wantCrystals) {
        if (availableUnits <= 0L || unitPerCrystal <= 0L || wantCrystals <= 0) {
            return 0;
        }
        final long whole = availableUnits / unitPerCrystal;
        return whole <= 0L ? 0 : (int) Math.min((long) wantCrystals, whole);
    }

    /** 晶数 → 通道单位（{@link #crystalsFromUnits} 的逆运算；非正数一律 0）。 */
    static long unitsForCrystals(int crystals, long unitPerCrystal) {
        return crystals <= 0 || unitPerCrystal <= 0L ? 0L : (long) crystals * unitPerCrystal;
    }

    /** 实验 E3：某第三方通道的栈无法物化成晶化源质，只报一次（每次抽取都刷一行会淹掉日志）。 */
    private static void logUnmaterializableOnce(String typeId, String tag) {
        if (LOG_UNMATERIALIZED.add(typeId + '#' + tag)) {
            LOG.info("[gtit] 口袋源质支：通道 {} 的条目（tag={}）无法物化为晶化源质，该声明按不可物化跳过", typeId, tag);
        }
    }
}
