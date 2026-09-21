package com.miaokatze.gtit.crossmod.taum;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.Loader;

/**
 * Thaumcraft / Thaumic Tinkerer 可选集成的<b>门面</b>（源质蒸馏栏的对外唯一入口）。
 * <p>
 * 本类<b>不得</b> import 或以任何形式静态引用 {@code thaumcraft.*} / {@code thaumic.tinkerer.*}
 * 类型（对齐 {@code crossmod/bq/BqCompat} 的范式）：TC 不在场时本类必须能安全加载。
 * 全部 TC/TT 类型引用收敛在 {@link TaumBridge}，且只经 {@link TaumBridgeApi} 接口以反射方式装配，
 * 因此本类的常量池里没有 {@link TaumBridge} 的符号引用——TC 缺席环境连
 * 「验证 TaumBridge」这一步都不会发生，不会抛 {@code NoClassDefFoundError}。
 * <p>
 * <b>双哨兵延迟加载</b>（照 {@code BqCompat.java:30-39} + {@code BqQuestInjector.java:112}）：
 * <ol>
 * <li>哨兵一：{@code Loader.isModLoaded("Thaumcraft")}</li>
 * <li>哨兵二：{@code Class.forName("thaumcraft.api.aspects.Aspect")} 真探到类</li>
 * <li>两颗都命中才 {@code Class.forName("...TaumBridge").getDeclaredConstructor().newInstance()}</li>
 * </ol>
 * 探测默认<b>惰性</b>（首次调用任一 API 时完成），所以本切片不需要在 {@code CommonProxy} 里接线；
 * 若主代理希望在 preInit 早置探测标志（拿一条启动日志），在 {@code BqCompat.detect()} 旁边加
 * 一行 {@code TaumCompat.detect();} 即可，幂等且无副作用。
 * <p>
 * <b>降级承诺</b>：TC 缺席、装配失败或运行期 api 漂移时——
 * {@link #isAvailable()} 为 false；{@link #distill(ItemStack)} 与 {@link #readContainer(ItemStack)}
 * 返回 {@link TaumAspectAmounts#EMPTY}；{@link #aspectOrder()} 返回空数组；
 * {@link #colorOf(String)} 返回 -1；产出型 API 返回 null。<b>不抛异常、不崩溃</b>，
 * 上层据此把蒸馏栏整栏置灰（口径：灰显 + tooltip，不隐藏，避免面板宽度双分支）。
 */
public final class TaumCompat {

    /** TC 桥接实现类全名（仅出现在 Class.forName 的字符串里） */
    private static final String BRIDGE_CLASS = "com.miaokatze.gtit.crossmod.taum.TaumBridge";

    /** TC api 标志类（Class.forName 探测用；Aspect 的静态注册表在类初始化时就绪） */
    private static final String TC_FLAG_CLASS = "thaumcraft.api.aspects.Aspect";

    /** Thaumcraft modId（{@code thaumcraft/common/Thaumcraft.java:70} 的 @Mod modid 实测） */
    public static final String MODID_THAUMCRAFT = "Thaumcraft";

    /** Thaumic Tinkerer modId（其 mcmod.info 的 modid 实测；本仓不静态引用其任何类型） */
    public static final String MODID_THAUMIC_TINKERER = "ThaumicTinkerer";

    // ★R78 起本类不再有"显示格数"常量：口袋源质盘的格数权威只有一处 ——
    // {@code PocketConstants.ESSENCE_DISPLAY_GRID}（6 列 × 12 行 = 72，R78②）。
    // 旧这里的 {@code DISPLAY_CELLS = 48} 是第二份真相（改格数时它不会被编译器逼着改，
    // 而 GUI 读常量、测试读它，一旦漂移就变成"测过的数字没人用"），故随 R78 一并摘除；
    // 需要"这个包认识几个 aspect"时用下面的 {@link #aspectCount()}（运行时派生，本来就不恒为任何字面量）。

    /** 颜色未知标记 */
    public static final int COLOR_UNKNOWN = -1;

    /**
     * 状态位一律 volatile：集成服下口袋逻辑可能同时被客户端线程与服务端线程触达
     * （本仓 v1.8.3 记录过集成服双线程重复执行的坑），保证安全发布。
     */
    private static volatile boolean initialized = false;
    private static volatile boolean thaumcraftLoaded = false;
    private static volatile boolean thaumicTinkererLoaded = false;
    private static volatile TaumBridgeApi bridge = null;

    /** aspectOrder() 的进程内缓存（注册表运行期不再变化；换档重启即重置） */
    private static volatile String[] orderCache;
    /** tag → 显示名 缓存（渲染每帧调用，避免重复走 TC；并发读写用 CHM 而非 LinkedHashMap） */
    private static final Map<String, String> NAME_CACHE = new ConcurrentHashMap<>();
    /** tag → 颜色 缓存 */
    private static final Map<String, Integer> COLOR_CACHE = new ConcurrentHashMap<>();

    private TaumCompat() {}

    /**
     * 幂等探测 + 装配（线程安全）。可安全地在 preInit 调用，也可完全不接线（首次使用时自动执行）。
     */
    public static synchronized void detect() {
        if (initialized) {
            return;
        }
        initialized = true;
        thaumcraftLoaded = isModLoadedSafely(MODID_THAUMCRAFT);
        thaumicTinkererLoaded = isModLoadedSafely(MODID_THAUMIC_TINKERER);
        if (!thaumcraftLoaded) {
            GTInterestingThing.LOG.info("[GTIT-Taum] 未检测到 Thaumcraft，源质蒸馏栏整栏降级为不可用");
            return;
        }
        try {
            Class.forName(TC_FLAG_CLASS);
        } catch (Throwable t) {
            thaumcraftLoaded = false;
            GTInterestingThing.LOG.warn("[GTIT-Taum] Loader 报告 Thaumcraft 在场但 {} 不可达，源质栏降级", TC_FLAG_CLASS, t);
            return;
        }
        try {
            Object created = Class.forName(BRIDGE_CLASS)
                .getDeclaredConstructor()
                .newInstance();
            bridge = (TaumBridgeApi) created;
            GTInterestingThing.LOG.info(
                "[GTIT-Taum] 检测到 Thaumcraft（TT 在场={}），源质桥接层已装配；aspect 注册数 {}",
                thaumicTinkererLoaded,
                bridge.aspectOrder().length);
        } catch (Throwable t) {
            bridge = null;
            GTInterestingThing.LOG.warn("[GTIT-Taum] 源质桥接层装配失败，蒸馏栏降级为不可用", t);
        }
    }

    /** @return Thaumcraft 是否在场（双哨兵的第一颗，惰性判定） */
    public static boolean isThaumcraftLoaded() {
        ensureReady();
        return thaumcraftLoaded;
    }

    /** @return Thaumic Tinkerer 是否在场（源质罐优先级用；本仓不静态引用其类型） */
    public static boolean isThaumicTinkererLoaded() {
        ensureReady();
        return thaumicTinkererLoaded;
    }

    /** @return 桥接层是否可用（false ⇒ 上层把蒸馏栏整栏置灰） */
    public static boolean isAvailable() {
        ensureReady();
        return bridge != null;
    }

    /**
     * 运行时派生的 aspect 注册序（{@code Aspect.aspects} 的 {@code LinkedHashMap} 迭代序）。
     * <p>
     * <b>不假设恰为任何数</b>：addon 与 GT5U 的 {@code TCAspects} 会追加条目。
     * ★<b>R78③ 起这一序不再是口袋源质盘的格序</b>：格序 = "该 tag 首次入账的顺序"，
     * 由服务端算并落 NBT（{@code PocketEssenceStore} 的格位归属表），GUI 只读那份同步值；
     * 本序现在只服务两件事：① {@link #aspectCount()} 的注册数读数，② 给"这个包一共认识
     * 几个 aspect"一个参考。<b>不得</b>再拿它排格子（两端各算一次就是 R32 的头号风险）。
     *
     * @return tag 数组快照；不可用时空数组
     */
    public static String[] aspectOrder() {
        ensureReady();
        TaumBridgeApi active = bridge;
        if (active == null) {
            return new String[0];
        }
        String[] cached = orderCache;
        if (cached == null) {
            cached = active.aspectOrder();
            orderCache = cached;
        }
        // 返回防御性副本：上层排序/裁剪不得污染缓存
        String[] copy = new String[cached.length];
        System.arraycopy(cached, 0, copy, 0, cached.length);
        return copy;
    }

    /** @return aspect 注册总数（不假设 48）；不可用时 0 */
    public static int aspectCount() {
        ensureReady();
        TaumBridgeApi active = bridge;
        if (active == null) {
            return 0;
        }
        String[] cached = orderCache;
        if (cached == null) {
            cached = aspectOrder();
        }
        return cached.length;
    }

    /**
     * aspect 染色（运行时取，绝不 baked：反编译转储的 int 常量已实测被写坏）。
     *
     * @param tag aspect tag
     * @return RGB int；未知或不可用时 {@link #COLOR_UNKNOWN}
     */
    public static int colorOf(String tag) {
        ensureReady();
        TaumBridgeApi active = bridge;
        if (active == null || tag == null) {
            return COLOR_UNKNOWN;
        }
        Integer cached = COLOR_CACHE.get(tag);
        if (cached == null) {
            cached = active.colorOf(tag);
            COLOR_CACHE.put(tag, cached);
        }
        return cached;
    }

    /**
     * aspect 显示名（{@code Aspect.getName()}）。
     *
     * @param tag aspect tag
     * @return 显示名；不可用时回落为 tag 本身
     */
    public static String nameOf(String tag) {
        ensureReady();
        TaumBridgeApi active = bridge;
        if (active == null || tag == null) {
            return tag;
        }
        String cached = NAME_CACHE.get(tag);
        if (cached == null) {
            cached = active.nameOf(tag);
            if (cached == null) {
                cached = tag;
            }
            NAME_CACHE.put(tag, cached);
        }
        return cached;
    }

    /**
     * aspect 图标资源路径（{@code thaumcraft:textures/aspects/<tag小写>.png}）。
     * 纯字符串，不依赖 TC 是否在场；TC 缺席时该资源域不存在，渲染方须自行回落。
     */
    public static String aspectTexturePath(String tag) {
        return TaumDistillRules.aspectTexturePath(tag);
    }

    /**
     * 蒸馏判据 + 产出集合。
     * <p>
     * 返回<b>空即「不可蒸馏」</b>（对应 TC4 {@code TileAlchemyFurnace.canSmelt():307-325}：
     * AspectList 为空 ⇒ 不烧、进度条不推进），也对应「TC 缺席」与「入参为 null」。
     * 需要区分「无 aspect」与「TC 不在场」时查 {@link #isAvailable()}。
     *
     * @param stack 待蒸馏物品
     * @return aspect → <b>原量</b>点数（唯一口径，与 {@code AspectList} 全量并入同构）；
     *         能否入账由 {@link TaumDistillRules#credit} 的全有全无预检决定
     */
    public static TaumAspectAmounts distill(ItemStack stack) {
        TaumBridgeApi active = readyBridge();
        return active == null || stack == null ? TaumAspectAmounts.EMPTY : active.distill(stack);
    }

    /**
     * 读任意源质容器（晶化源质 / 源质瓶 / 实现 {@code IEssentiaContainerItem} 的第三方罐）。
     *
     * @return 内容快照；空表示非容器、空容器或不可用
     */
    public static TaumAspectAmounts readContainer(ItemStack stack) {
        TaumBridgeApi active = readyBridge();
        return active == null || stack == null ? TaumAspectAmounts.EMPTY : active.readContainer(stack);
    }

    /** @return 该物品是否是源质容器（TC 缺席时恒 false） */
    public static boolean isContainer(ItemStack stack) {
        return capacityOf(stack) != TaumDistillRules.CAPACITY_NOT_A_CONTAINER;
    }

    /**
     * 单容器容量档位。
     *
     * @return 晶 {@value TaumDistillRules#CRYSTAL_CAPACITY} / 瓶
     *         {@value TaumDistillRules#PHIAL_CAPACITY} / 其他容器
     *         {@value TaumDistillRules#CAPACITY_UNKNOWN}（调用方以口袋单格上限自缚）/
     *         非容器或不可用 {@value TaumDistillRules#CAPACITY_NOT_A_CONTAINER}
     */
    public static int capacityOf(ItemStack stack) {
        TaumBridgeApi active = readyBridge();
        return active == null || stack == null ? TaumDistillRules.CAPACITY_NOT_A_CONTAINER : active.capacityOf(stack);
    }

    /**
     * 往容器注入源质（合并语义，已有不同 aspect 时整笔拒绝）。
     * <p>
     * 单容器语义：不拆堆、不改 {@code stackSize}，{@code stackSize > 1} 请先自行拆成 1。
     *
     * @return 实际注入点数（0 表示容器未改动）
     */
    public static int addEssentia(ItemStack container, String tag, int points) {
        TaumBridgeApi active = readyBridge();
        return active == null || container == null ? 0 : active.addEssentia(container, tag, points);
    }

    /**
     * 取空一个容器（读出并清空；瓶退回 meta 0 空瓶态）。
     * 晶化源质不适用（返回 EMPTY），请上层「读出点数 + 消耗整件」。
     *
     * @return 取出的内容快照
     */
    public static TaumAspectAmounts drainAll(ItemStack container) {
        TaumBridgeApi active = readyBridge();
        return active == null || container == null ? TaumAspectAmounts.EMPTY : active.drainAll(container);
    }

    /**
     * 产出晶化源质（1 点 = 1 个晶，最多 64 个/堆）。
     *
     * @return 新物品栈；不可用、tag 未知或 points &lt;= 0 时 null
     */
    public static ItemStack newCrystalStack(String tag, int points) {
        TaumBridgeApi active = readyBridge();
        return active == null ? null : active.newCrystalStack(tag, points);
    }

    /**
     * 产出一个装好源质的容器：第三方罐按 {@code IEssentiaContainerItem} 接口探测（R31；R44a 实测本环境无 TT ItemVessel），
     * 否则回落 TC 源质瓶（一次 8 点，不足按实际点数装）。
     *
     * @return 容器栈（stackSize 1）；不可用时 null
     */
    public static ItemStack newFilledContainer(String tag, int points) {
        TaumBridgeApi active = readyBridge();
        return active == null ? null : active.newFilledContainer(tag, points);
    }

    /**
     * @return {@link #newFilledContainer} 的单容器容量；0 表示没有任何可用容器
     *         （蒸馏栏此时应禁止「出罐」操作）
     */
    public static int filledContainerCapacity() {
        TaumBridgeApi active = readyBridge();
        return active == null ? TaumDistillRules.CAPACITY_NOT_A_CONTAINER : active.filledContainerCapacity();
    }

    // ------------------------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------------------------

    private static void ensureReady() {
        if (!initialized) {
            detect();
        }
    }

    /**
     * {@code Loader.isModLoaded} 的不可抛封装。
     * <p>
     * FML 尚未构造 Loader（或该 api 在未来版本漂移）时视为「不在场」而不是把异常抛进
     * 口袋物品的首次蒸馏/渲染调用——桥接层的任何触点都不允许成为崩溃源。
     */
    private static boolean isModLoadedSafely(String modId) {
        try {
            return Loader.isModLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    private static TaumBridgeApi readyBridge() {
        ensureReady();
        return bridge;
    }
}
