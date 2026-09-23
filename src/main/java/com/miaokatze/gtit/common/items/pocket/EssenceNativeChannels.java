package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;
import com.miaokatze.gtit.main.GTInterestingThing;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * 「某条通道是否为<b>源质原生通道</b>」的<b>唯一判据</b>（★R91-① 改口：AE2 <b>容器契约</b>）。
 * <p>
 * <b>判法</b>——照旧<b>不猜第三方 mod 的私有栈格式</b>（R31/R44a 一脉）：造一只<b>装满的源质瓶</b>
 * （{@link TaumCompat#newFilledContainer}，一瓶 {@link PocketConstants#ESSENCE_OUT_UNIT_POINTS} 点）
 * 作探针，走 AE2 为「容器」开的那一口：{@code isContainerItemForType} 认门 +
 * {@code getStackFromContainerItem} 换算，换得出<b>非零堆</b>（{@code getStackSize() > 0}）即「原生」，
 * 换不出（不认这个容器／null／零／抛 {@code Throwable}）即「这条通道收不了源质」。
 * <p>
 * ★★<b>R90 为什么全线假阴（本轮头号根因，取证 {@code plan/_taskpack/r91-ret-te-channel.md} 问题 2）</b>：
 * 旧判据喂的是同一只<b>正确的</b>瓶子，但问的是 {@code IAEStackType} 上<b>另一个</b>方法——AE2 的接口
 * javadoc 对那个口明写「入参 <b>should not be container</b>」，其定位是给 GT 流体显示与 TC4 aspect
 * 伪物品用的<b>非容器</b>换算口；ThaumicEnergistics 对它的实现只认那两家的 {@code ItemAspect}，
 * 其余一律 {@code return null}。⇒ <b>装了 TE 的实机必然判成"不是原生通道"</b>，与源质数量、元件容量、
 * 权限、AE 网络状态统统无关（日志 {@code L12 源质上传路由缺席} 即此）。容器口径才是 AE2 终端自己
 * 选路用的那一口（{@code ContainerMEMonitorable} 的容器选路与 {@code VirtualMEMonitorableSlot} 的
 * tooltip 判定两处同款，可交叉复核）。
 * <p>
 * <b>本类存在的意义</b>：把这条判法从 {@code NekoEssenceGhostCell#channelTypeId}（声明侧）下沉成单源，
 * 让<b>声明侧、上传侧、抽取侧读的都是同一只探针</b>——两处真相必然在"1 单位折多少"上分叉
 * （R88 那次 8 倍错位就是前车之鉴）。因此全仓的<b>容器换算</b>只出现在 {@link
 * #channelStackFromContainer} 一处，禁止第二判据。
 * <p>
 * <b>★AUQ-①=B 的政策四项（R90 立、R91 逐条保留——本轮翻的是<b>识别法</b>，不是取舍）</b>：
 * <ol>
 * <li>无源质原生通道 ⇒ 面板 {@code NO_CHANNEL} 拒收（不静默）；</li>
 * <li>该计数经 {@code PocketChannelRunner} 的 {@code essenceNoChannel} 进 {@code failures()}；</li>
 * <li><b>上传链路零瓶化</b>：任何形态的「物化成瓶塞进物品通道」的兜底都已删除，瓶只存在于玩家手动
 * 取用与 GUI 游标；</li>
 * <li><b>不回落 ITEM 通道</b>：内建物品/流体两道排除<b>一字未动</b>，位置仍在
 * {@link #nativeProbe} 的方法体首段（★R91-① 明令「不许动」；它现在是<b>冗余保险</b>——AE2 那两个
 * 实现的容器判定本来就不认源质瓶，但"上传永不回落物品通道"是<b>政策</b>而非实测结论，
 * 不得让上游一次改动把它变成第二判据）。</li>
 * </ol>
 * <b>★R91-② 单位</b>：探针栈的 {@code getStackSize()} 仍当 {@code unit} 用（{@code setStackSize(amount *
 * unit)} 的算术零改动）；新增 {@code IAEStackType#getAmountPerUnit()} 作<b>交叉断言</b>——期望 {@code
 * unit × getAmountPerUnit()} 等于本仓的整瓶点数，不符只留<b>一次性 WARN</b>：★<b>不硬编码常数、
 * 不把点数搬回本仓、不绑 ThaumicEnergistics 版本、更不改算式</b>。
 * <p>
 * <b>纯 JVM 行为如实声明</b>：TC 缺席时 {@code newFilledContainer} 恒 {@code null} ⇒ 探针恒空
 * ⇒ 判定恒为「无源质原生通道」。这与生产语义同向（没有 TC 就没有源质可传），但意味着本类的
 * "命中"分支属实机项，零依赖套件只消费其判据形状，不驱动命中路径。
 */
public final class EssenceNativeChannels {

    private EssenceNativeChannels() {}

    /**
     * 一只装满的源质瓶经<b>该通道</b>按 AE2 容器契约换算出的通道栈（即"1 瓶 = 多少通道单位"的探针读数）。
     * <p>
     * 返回值<b>就是</b>判定结果：非 {@code null} 且堆大于 0 ⇒ 该通道是此 tag 的源质原生通道，
     * {@code getStackSize()} 直接给上传/抽取两侧当 {@code unit} 用（同一只探针，不造第二只）；
     * 其余一切情形（内建物品/流体、不认这个容器、换不出、抛异常、TC 缺席）返回 {@code null} ⇒ 调用方按
     * 「这条通道收不了源质」收口（上传侧 {@code NO_CHANNEL} 拒收，声明侧不写死声明）。
     * <p>
     * ★签名与三条消费侧（声明 {@code nativeChannelTypeId}／上传 {@code injectEssenceSource}／
     * 抽取 {@code extractEssence}）<b>一字未改</b>——R91 翻的只是体内问的那一口。
     */
    public static IAEStack<?> nativeProbe(IAEStackType<?> type, String tag) {
        if (type == null || tag == null || tag.isEmpty()) {
            return null;
        }
        if (type == InfinityStackTypes.ITEM_STACK_TYPE || type == InfinityStackTypes.FLUID_STACK_TYPE) {
            return null;
        }
        final ItemStack probeStack = TaumCompat.newFilledContainer(tag, PocketConstants.ESSENCE_OUT_UNIT_POINTS);
        if (probeStack == null) {
            return null;
        }
        final IAEStack<?> probe = channelStackFromContainer(type, probeStack);
        if (probe == null) {
            return null;
        }
        try {
            // ★R91-② 交叉断言（只留痕，不参与判定、不改算式）：一「通道单位」自述折多少源质点，
            // 乘上探针读出的倍率应当正好等于本仓的整瓶点数；不等 ⇒ 一次性 WARN，算式照旧。
            final int pointsPerUnit = type.getAmountPerUnit();
            if (pointsPerUnit > 0
                && probe.getStackSize() * (long) pointsPerUnit != PocketConstants.ESSENCE_OUT_UNIT_POINTS) {
                warnUnitCrossMismatchOnce(type.getId(), tag, probe.getStackSize(), pointsPerUnit);
            }
        } catch (Throwable t) {
            // 第三方实现可以让 getAmountPerUnit 抛（它只是接口上的 @Range 声明）⇒ 交叉断言不得成为崩溃源；
            // 主判据（容器契约换出非零堆）此刻已经成立，连一次 WARN 都不必留。
        }
        return probe;
    }

    /**
     * <b>AE2 容器契约的单源转换</b>：非该通道的容器 / 换不出非零堆 / 抛 {@code Throwable} ⇒ {@code null}。
     * <p>
     * 本方法是全仓<b>唯一</b>调用「容器 → 通道栈」那两口的地方（{@code plan/_taskpack/verify-pocket.sh}
     * 的 {@code R91-a} 段把它钉成文件数 = 1）：{@link #nativeProbe} 与
     * {@code PocketEssenceChannelOps#essenceStackFor} 共读它 ⇒ 声明侧、上传侧、抽取侧、回补侧四处的
     * 倍率必然同源（★禁造第二判据）。
     * <p>
     * ★顺序是判据的一部分：先<b>认门</b>（{@code isContainerItemForType}）再<b>换算</b>
     * （{@code getStackFromContainerItem}）——与 AE2 终端的容器选路逻辑同形。
     * <p>
     * ★内建两席位的排除<b>不在这里</b>，仍原样住在 {@link #nativeProbe} 的位置上（★R91-①「不许动」）：
     * 撤掉它也不会放行——AE2 的 {@code AEItemStackType} 的容器判定恒假、{@code AEFluidStackType} 只认
     * 流体容器，源质瓶两条都过不了门（取证 {@code r91-ret-te-channel.md} 问题 2.3 的实测）。本方法被
     * 抽取/回补侧的 {@code PocketEssenceChannelOps#essenceStackFor} 直调，那条路径在 R90 就已经没有
     * 内建特判（靠 AE2 自己返 null），行为一字未变。
     */
    public static IAEStack<?> channelStackFromContainer(IAEStackType<?> type, ItemStack container) {
        if (type == null || container == null) {
            return null;
        }
        try {
            if (!type.isContainerItemForType(container)) {
                return null;
            }
            final IAEStack<?> converted = type.getStackFromContainerItem(container);
            return converted != null && converted.getStackSize() > 0L ? converted : null;
        } catch (Throwable t) {
            // 第三方通道的实现不得成为崩溃源（含其未声明依赖的 NoClassDefFoundError 类缺场，
            // 也含上游读空容器可能越界的已知风险）⇒ 视同收不了，问下一家
            return null;
        }
    }

    // ------------------------------------------------------------------ ★E-test（批 1 测试落地）：包私有测试种子口
    //
    // 存在理由：纯 JVM 零依赖套件里既没有 TC（{@code newFilledContainer} 恒 null ⇒ 探针恒空），
    // 也没有任何第三方通道注册——「源质原生通道<b>在场</b>」那一列（E2 路由矩阵）只能靠种子驱动。
    // 生产语义零变化的承诺：本组全部<b>包私有</b>且生产零调用方，{@link #SEEDED_NATIVE_TAGS} 恒空
    // ⇒ 下面的种子分支一步滑过，两个判法的既有分支逐字保留。内建物品/流体通道<b>不在种子的世界里有
    // 席位</b>：种子只顶替「运行时注册的第三方」那一席（{@link #channelStackFromContainer} 的内建排除
    // 对种子不生效，由回归用例钉住）。

    /** 种子：「typeId 是这些 tag 的源质原生通道」。生产恒空。 */
    static final java.util.Map<String, java.util.Set<String>> SEEDED_NATIVE_TAGS = new java.util.LinkedHashMap<>();

    /** 种子一条（幂等；空 typeId/tag 钝化）。 */
    static void seedNativeForTest(String typeId, String tag) {
        if (typeId == null || typeId.isEmpty() || tag == null || tag.isEmpty()) {
            return;
        }
        java.util.Set<String> tags = SEEDED_NATIVE_TAGS.get(typeId);
        if (tags == null) {
            tags = new java.util.LinkedHashSet<>();
            SEEDED_NATIVE_TAGS.put(typeId, tags);
        }
        tags.add(tag);
    }

    /** 清空全部种子（用例收尾必调——防跨用例污染）。 */
    static void unseedNativesForTest() {
        SEEDED_NATIVE_TAGS.clear();
    }

    /**
     * 种子读数（生产恒 {@code false}）：这条 {@code (typeId, tag)} 对是不是被种子判成
     * 「源质原生通道」。零依赖套件的桩件用它顶替「第三方席位」的探针判定——<b>不走</b>
     * {@link #nativeChannelTypeId}（未种子的 tag 会掉进注册表循环，而 {@code AEStackTypeRegistry}
     * 的类初始化引用 fastutil、不在 test classpath 上 ⇒ 实测 {@code NoClassDefFoundError}）。
     */
    static boolean isSeededNativeForTest(String typeId, String tag) {
        if (typeId == null || tag == null) {
            return false;
        }
        final java.util.Set<String> tags = SEEDED_NATIVE_TAGS.get(typeId);
        return tags != null && tags.contains(tag);
    }

    /**
     * 此 tag 的<b>第一条</b>源质原生通道的 id（{@code IAEStackType.getId()} 字符串）；
     * 一条都没有 ⇒ {@code ""}（调用方据此<b>不写</b>任何声明——不产生死声明，见
     * {@code NekoEssenceGhostCell#ghostKeyFor} 对空通道 id 的既有拒收）。
     * <p>
     * 候选序 = {@code InfinityStackTypes.allSupportedTypes()} 的稳定列表序（物品→流体→运行时注册的
     * 第三方；前两者在 {@link #channelStackFromContainer} 里被排除，实际只在第三方段里找）。
     * <p>
     * ★E-test：种子查在注册表循环<b>之前</b>——生产里 {@link #SEEDED_NATIVE_TAGS} 恒空，这个分支
     * 一步就滑过去（行为零变化）；零依赖套件里注册表循环因 fastutil 缺场而不可驱动（见
     * {@link #isSeededNativeForTest} 的说明），「在场」列只有种子能让本方法被驱动到。
     */
    public static String nativeChannelTypeId(String tag) {
        if (tag == null || tag.isEmpty()) {
            return "";
        }
        if (!SEEDED_NATIVE_TAGS.isEmpty()) {
            for (final java.util.Map.Entry<String, java.util.Set<String>> seeded : SEEDED_NATIVE_TAGS.entrySet()) {
                if (seeded.getValue()
                    .contains(tag)) {
                    return seeded.getKey();
                }
            }
        }
        for (IAEStackType<?> type : InfinityStackTypes.allSupportedTypes()) {
            if (type != null && nativeProbe(type, tag) != null) {
                return type.getId();
            }
        }
        return "";
    }

    /** ★R91-② 交叉断言的 WARN 去重集（「通道 × tag」一条一行；每秒的注入批都会撞同一对，必须去重）。 */
    private static final java.util.Set<String> UNIT_CROSS_WARNED = new java.util.LinkedHashSet<>();

    /**
     * ★R91-②：探针倍率与该通道自述的「每单位点数」乘不上时的<b>一次性留痕</b>——
     * 刻意<b>只报不改</b>：判定与算术仍以探针的 {@code getStackSize()} 为唯一 {@code unit}，
     * ★<b>不</b>在这里写死任何点数常数（那是 R88 8 倍错位的复现路径），也<b>不</b>因为
     * 某一家通道的自述值而回落物品通道或拒收。本方法在 {@code nativeProbe} 的
     * {@code try/catch Throwable} 内被调用（{@code getId()} 亦可抛 ⇒ 由那半段兜住）。
     */
    private static void warnUnitCrossMismatchOnce(String typeId, String tag, long unitsPerCarrier, int pointsPerUnit) {
        if (!UNIT_CROSS_WARNED.add(typeId + '#' + tag)) {
            return;
        }
        GTInterestingThing.LOG.warn(
            "[GTIT-Taum] 源质通道 {} 的单位交叉断言不上：探针 1 只瓶折 {} 通道单位 × 自述每单位 {} 点"
                + " != 本仓整瓶点数（tag={}）。★判定与算术照旧以探针读数为唯一 unit，本条只留痕；"
                + "多为该 mod 的容器换算与其 getAmountPerUnit 口径漂移所致（本条只报一次）",
            typeId,
            unitsPerCarrier,
            pointsPerUnit,
            tag);
    }
}
