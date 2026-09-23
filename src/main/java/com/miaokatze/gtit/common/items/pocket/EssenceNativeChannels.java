package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.infinitycell.InfinityStackTypes;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * 「某条通道是否为<b>源质原生通道</b>」的<b>唯一判据</b>（★R90 E2，AUQ-①=B 裁定：上传永不产瓶）。
 * <p>
 * 判法与 R31/R44a 一脉相承——<b>不猜第三方 mod 的私有栈格式</b>：造一只<b>装满的源质瓶</b>
 * （{@link TaumCompat#newFilledContainer}，一瓶 {@link PocketConstants#ESSENCE_OUT_UNIT_POINTS} 点）
 * 作探针，经 {@code IAEStackType.convertStackFromItem}（AE2 留给第三方通道的"物品 → 通道栈"换算口）
 * 反算；换得出<b>非零堆</b>（{@code getStackSize() > 0}）即「原生」，换不出（null／零／抛
 * {@code Throwable}）即「这条通道收不了源质」。本类存在的意义是把这条判法从
 * {@code NekoEssenceGhostCell#channelTypeId}（声明侧）下沉成单源，让<b>声明侧、上传侧、抽取侧
 * 读的都是同一只探针</b>——两处真相必然在"1 单位折多少"上分叉（R88 那次 8 倍错位就是前车之鉴）。
 * <ul>
 * <li><b>内建物品通道排除</b>（★R90 起<b>不再作兜底</b>）：源质上传不允许物化成瓶堆塞物品通道——
 * AE2 的 {@code AEItemStackType#convertStackFromItem} 本就无条件 {@code return null}
 * （"物品 → 物品堆"不走这个口），旧实现的 {@code AEItemStack.create(瓶)} 特判已随 E2 删除；</li>
 * <li><b>内建流体通道排除</b>（R86 口径不变）：把源质瓶声明成流体通道没有任何读法成立；</li>
 * <li><b>容错形态</b>：{@code convertStackFromItem} 包 {@code try/catch Throwable} 后问下一家
 * ——第三方通道的实现（含其未声明依赖的 {@code NoClassDefFoundError} 类缺场）不得成为崩溃源。</li>
 * </ul>
 * <b>纯 JVM 行为如实声明</b>：TC 缺席时 {@code newFilledContainer} 恒 {@code null} ⇒ 探针恒空
 * ⇒ 判定恒为「无源质原生通道」。这与生产语义同向（没有 TC 就没有源质可传），但意味着本类的
 * "命中"分支属实机项，零依赖套件只消费其判据形状，不驱动命中路径。
 */
public final class EssenceNativeChannels {

    private EssenceNativeChannels() {}

    /**
     * 一只装满的源质瓶经<b>该通道</b>反算出的通道栈（即"1 瓶 = 多少通道单位"的探针读数）。
     * <p>
     * 返回值<b>就是</b>判定结果：非 {@code null} 且堆大于 0 ⇒ 该通道是此 tag 的源质原生通道，
     * {@code getStackSize()} 直接给上传/抽取两侧当 {@code unit} 用（同一只探针，不造第二只）；
     * 其余一切情形（内建物品/流体、换不出、抛异常、TC 缺席）返回 {@code null} ⇒ 调用方按
     * 「这条通道收不了源质」收口（上传侧 {@code NO_CHANNEL} 拒收，声明侧不写死声明）。
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
        try {
            final IAEStack<?> converted = type.convertStackFromItem(probeStack);
            return converted != null && converted.getStackSize() > 0L ? converted : null;
        } catch (Throwable t) {
            // 第三方通道的实现不得成为崩溃源（含其依赖类缺场的 NoClassDefFoundError）⇒ 视同收不了
            return null;
        }
    }

    // ------------------------------------------------------------------ ★E-test（批 1 测试落地）：包私有测试种子口
    //
    // 存在理由：纯 JVM 零依赖套件里既没有 TC（{@code newFilledContainer} 恒 null ⇒ 探针恒空），
    // 也没有任何第三方通道注册——「源质原生通道<b>在场</b>」那一列（E2 路由矩阵）只能靠种子驱动。
    // 生产语义零变化的承诺：本组全部<b>包私有</b>且生产零调用方，{@link #SEEDED_NATIVE_TAGS} 恒空
    // ⇒ 下面的种子分支一步滑过，两个判法的既有分支逐字保留。内建物品/流体通道<b>不在种子的世界里有
    // 席位</b>：种子只顶替「运行时注册的第三方」那一席（{@link #nativeProbe} 的内建排除对种子不生效，
    // 由回归用例钉住）。

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
     * 第三方；前两者在 {@link #nativeProbe} 里被排除，实际只在第三方段里找）。
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
}
