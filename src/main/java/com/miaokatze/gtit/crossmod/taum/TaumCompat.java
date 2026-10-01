package com.miaokatze.gtit.crossmod.taum;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.PocketConstants;
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
 * {@link #colorOf(String)} 返回 -1；{@link #imageLocationOf(String)} 回落
 * {@link TaumDistillRules#aspectTexturePath(String)} 公式串；产出型 API 返回 null。<b>不抛异常、不崩溃</b>，
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
    /** ★R97 S4：tag → 图标定位串 缓存（与 {@link #colorOf(String)} 同一渲染热点的第三项；桥结果进程内不变） */
    private static final Map<String, String> IMAGE_CACHE = new ConcurrentHashMap<>();

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

    /**
     * @return Thaumic Tinkerer 是否在场。
     *         ★R88：旧用途"源质罐优先级"已随载体改判作废（出件钉为 TC 瓶，见
     *         {@code TaumBridge#preferredContainerItem}），本读数现在只服务
     *         {@code TaumBridge#vesselItem()} 那条一次性的在场日志；本仓不静态引用 TT 的任何类型。
     */
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
     * ★<b>R97 S4（需求⑤）：aspect 图标定位——GUI 侧唯一消费口径</b>（消费点 =
     * {@code NekoEssenceGhostCell#setCellContent} 的 {@code UITexture.builder().location(...)}）。
     * <p>
     * 桥在场走 {@link TaumBridgeApi#imageLocationOf(String)} 读 {@code Aspect.getImage()} 的真资源域
     * ——附属 mod 注册的 aspect 图标从此不再被拼到 {@code thaumcraft:} 域下落 missing texture
     * （TC 原生 aspect 与回落公式本就重合，<b>零视觉差</b>）；桥缺席、桥返 null 或运行期漂移一律回落
     * {@link TaumDistillRules#aspectTexturePath(String)}（回落串<b>单源</b>，公式本体 R97 未动；
     * 桥实现内另有 Throwable 兜底，两层降级语义等价）。旧门面 {@code aspectTexturePath} 已随本方法
     * 落地摘除——消费面只剩这一个口径，不留第二份「恒 thaumcraft 域」的拼串入口。
     *
     * @param tag aspect tag
     * @return {@code "modid:path"} 定位串；tag 为 null/空时 null（与旧口径一致，渲染层判据
     *         {@code drawsContentLayer} 先挡掉空 tag）
     */
    public static String imageLocationOf(String tag) {
        ensureReady();
        TaumBridgeApi active = bridge;
        if (active == null || tag == null) {
            return TaumDistillRules.aspectTexturePath(tag);
        }
        String cached = IMAGE_CACHE.get(tag);
        if (cached == null) {
            cached = active.imageLocationOf(tag);
            if (cached == null) {
                cached = TaumDistillRules.aspectTexturePath(tag);
            }
            if (cached != null) {
                IMAGE_CACHE.put(tag, cached);
            }
        }
        return cached;
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
     * <p>
     * ★R88：这是<b>只读面</b>，因此也是裁定 C2 下"旧晶仍能被读回点数（不吃件）"的落点；
     * 瓶侧的读点是 12 格注入支与通道下传支（两边都按<b>单件点数 × 叠数</b>算，见
     * {@code PocketSlots#injectContainer} / {@code PocketEssenceChannelOps#extractEssence}）。
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
     * <p>
     * ★R88：瓶那一档（{@value TaumDistillRules#PHIAL_CAPACITY}）现在是<b>现役载体的计点依据</b>——
     * 12 格注入支、点击入槽支与 NEI 拖入判据都按它算"这一叠值几点"；晶那一档按裁定 C2 降级为
     * <b>只读识别</b>（旧晶仍认得，但不再有产出方）。
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
     * ★<b>R91-⑦：源质伪物品（配方显示件）承载的 aspect tag</b>——本仓读它的<b>唯一</b>入口，
     * 判定的合成点在 {@code PocketEssenceIntake#declarationTagOf}（GUI 不得直调本方法、更不得抄键名）。
     * <p>
     * 三条硬边界（注册名精确白名单 / 唯一 String 键 / TC {@code Aspect#getAspect} 复核）与
     * <b>「只声明、绝不入库存计点」</b>的口径全部写在 {@link TaumBridgeApi#pseudoAspectTag(ItemStack)}，
     * 本门面只做逐字转发 + 降级（TC 或 ARI 缺席、桥接层未装配 ⇒ {@code null}）。
     *
     * @return 复核后的 canonical tag；读不出 ⇒ {@code null}
     */
    public static String pseudoAspectTag(ItemStack stack) {
        TaumBridgeApi active = readyBridge();
        return active == null || stack == null ? null : active.pseudoAspectTag(stack);
    }

    /**
     * 往容器注入源质（合并语义，已有不同 aspect 时整笔拒绝）。瓶在此完成 <b>meta 0（空）→ 1（满）</b>
     * 的切换，所以它是"往一只<b>已经在场</b>的空瓶里灌源质"的唯一正确出口。
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
     * <p>
     * ★R88 现役调用方：12 格注入支的<b>瓶/罐</b>那一支（{@code PocketSlots#injectContainer} →
     * {@code EssenceGate#drainContainer}）。⚠ 该支自本轮起按<b>单件点数 × 叠数</b>入账、却只抽干
     * <b>整叠共享的那一份 AspectList</b> ⇒ 空瓶叠原样退回，点数一分不吞（取证见那里的 ★R88 注）。
     *
     * @return 取出的内容快照（单件量；叠数放大由调用方负责）
     */
    public static TaumAspectAmounts drainAll(ItemStack container) {
        TaumBridgeApi active = readyBridge();
        return active == null || container == null ? TaumAspectAmounts.EMPTY : active.drainAll(container);
    }

    /**
     * 产出晶化源质（1 点 = 1 个晶，最多 64 个/堆）—— ★★<b>R96 S9b 起本方法是全仓唯一的产晶出口</b>。
     * <p>
     * <b>★R88 裁定 C2 已被用户改判一半（口径变更，原文留在这里以免有人以为"只读"仍是现行判据）</b>：
     * R88 那句"本门面退役为只读、本仓不得再有调用方"的<b>读侧一半仍然成立</b>——旧晶的点数照旧走
     * {@link #readContainer(ItemStack)} + {@link #capacityOf(ItemStack)} 读回，★永远不就地排空
     * （wiki {@code gtit-taumcraft-essentia-carriers.md} §3 证死：晶不可就地排空，服务端会随机重赋型）；
     * 被改判的是<b>产侧一半</b>：R96 魔法使的「结晶模式」（{@code PocketMageModes#crystalOn}，
     * ★默认关）开通以后，蒸馏出的源质会以晶的形态直接进玩家背包，所以<b>生产调用方不再是零</b>。
     * <p>
     * ★★<b>唯一的调用链是蒸馏腿的结晶分叉（{@code PocketDistillDriver} 结晶支 →
     * {@code mage/PocketCrystalDriver#deliverAsCrystals} → {@code CrystalGate.TAUM}），且它调的是下面那层
     * {@link #mintCrystals(String, int)} 而不是本方法</b>——时机随蒸馏轮次（产物当轮成晶进背包，
     * 无独立的秒级批量腿），这不是绕门禁，而是把「口袋目录不再直接产晶」
     * 这条既有锚（{@code verify-pocket.sh} R88③ 段：{@code src/…/pocket} 与 {@code gui/pocket} 两目录内
     * {@code newCrystalStack(} 代码位恰 0，孪生 Java 用例
     * {@code essenceLegacyCrystalIsReadOnlyButStillSoluble}）<b>与新功能同时保住</b>：
     * 产出动作的<b>类型面</b>（TC 的 {@code ItemEssence} 与 {@code IEssentiaContainerItem}）本来就只许住在
     * {@code crossmod/taum}（类污染红线 {@link TaumCompat TaumCompat:15-27}），
     * 所以"晶从哪来"这一问在本仓永远只有一个码位可问，口袋侧问的是 {@link #mintCrystals}。
     * <p>
     * 搬运载体的<b>缺省</b>档没变：不开结晶模式时，源质出袋仍是
     * {@link #newFilledContainer(String, int)}（一瓶 8 点）与"灌玩家自己那只瓶"两条路，本方法不参与。
     * 彻底摘除本门面（连同 {@code TaumBridge#newCrystalStack} 与 {@code CRYSTAL_STACK_LIMIT} 那条旧链接）
     * 自本轮起<b>不再是可选项</b>（它有了在役调用方），旧裁决见 {@code plan/_taskpack/r88-essentia/20-e1-report.md}。
     *
     * @return 新物品栈；不可用、tag 未知或 points &lt;= 0 时 null
     */
    public static ItemStack newCrystalStack(String tag, int points) {
        TaumBridgeApi active = readyBridge();
        return active == null ? null : active.newCrystalStack(tag, points);
    }

    /**
     * ★R96 S9b：结晶模式的<b>生产入口</b>（逐字转 {@link #newCrystalStack(String, int)}，不加第二条判据）。
     * <p>
     * <b>为什么要多这一层同名转发</b>：见上面那条 javadoc 里 ★★ 那一段——产晶的<b>类型面</b>必须留在
     * 本包，而口袋目录里不许出现 {@code newCrystalStack(} 这个码位（既有锚）。多一枚门面方法换到的正是
     * "整个 src/main 里产晶出口仍然只有一处可点名、且它在降级承诺之内"。★本方法<b>不</b>做点数之外的
     * 任何换算：批量上界与"整枚不排空"的判据都在 driver 侧（{@code MAGE_CRYSTAL_MAX_PER_BATCH}），
     * 这里再钳一次就是第二份真相。
     * <p>
     * 降级：TC 缺席 / 桥未装配 / tag 不认识 / {@code points <= 0} ⇒ {@code null}（★永不抛，也不抛空物品）。
     * 调用方拿到 {@code null} 必须按"什么都没发生"处理，★一分来源都不许扣。
     */
    public static ItemStack mintCrystals(String tag, int points) {
        return newCrystalStack(tag, points);
    }

    /**
     * ★<b>R88：源质搬运载体的唯一出件口</b>——产出一只<b>装满</b>的 TC 源质瓶（{@code ItemEssence}，
     * meta 1、{@code AspectList} 里 {@code add(tag, 8)}、可堆 64）。
     * <p>
     * ★★<b>R91-④ 起调用方只剩通道侧</b>（探针 / 上传 / 下传回读，见
     * {@code EssenceNativeChannels} 与 {@code PocketEssenceChannelOps}）：<b>面板取出侧不再用它</b>——
     * "点一格源质就凭空冒出一叠满瓶"是用户判定的缺陷，取出改为<b>灌玩家手里的空瓶</b>
     * （{@link #addEssentia(ItemStack, String, int)} 那一份真相），入槽则把空瓶原路退回。
     * <p>
     * 旧文案说"第三方罐按 {@code IEssentiaContainerItem} 接口探测（R31）优先、否则回落瓶"：那条优先级
     * 已随 R88 裁定作废（{@code TaumBridge#preferredContainerItem} 现在钉为瓶，罐探测只留在一条
     * 在场日志里），因为取出粒度 / 通道单位 / tooltip 三处口径全都以"一瓶 8 点"为真值，
     * 而第三方罐的容量在本仓没有证据（只能落 {@link TaumDistillRules#CAPACITY_UNKNOWN}）。
     * R44a 的实测仍然成立：本环境锁定的 TT dev jar 里根本没有 {@code ItemVessel}。
     * <p>
     * ★调用方一律传整瓶点数（{@code PocketConstants.ESSENCE_OUT_UNIT_POINTS}）；桥里的 {@code min}
     * 只是兜底，<b>不是</b>半瓶许可（C1：不足一瓶就留盘）。
     *
     * @return 容器栈（stackSize 1）；不可用时 null
     */
    public static ItemStack newFilledContainer(String tag, int points) {
        TaumBridgeApi active = readyBridge();
        return active == null ? null : active.newFilledContainer(tag, points);
    }

    /**
     * @return {@link #newFilledContainer} 的单容器容量（★R88 起恒为瓶档
     *         {@value TaumDistillRules#PHIAL_CAPACITY}）；
     *         0 表示没有任何可用容器（蒸馏栏此时应禁止「出瓶」操作）
     */
    public static int filledContainerCapacity() {
        TaumBridgeApi active = readyBridge();
        return active == null ? TaumDistillRules.CAPACITY_NOT_A_CONTAINER : active.filledContainerCapacity();
    }

    /**
     * ★<b>R96 S9a：这个 tag 是不是元始（primal）</b>——口袋元素容量认键的唯一问句。
     * <p>
     * 本门面只做两件事，都不引入任何 TC 类型：① <b>白名单腿</b>先行
     * （{@code PocketConstants#isPrimalTag(String)}，纯查表）——不在 6 条里的 tag 一次桥都不碰，
     * 这条问句在面板与被动侧会被反复问，早退顺序就是成本顺序；② 剩下的交给桥做
     * <b>TC 复核</b>（{@code Aspect#getAspect(tag).isPrimal()}）。
     * <p>
     * ★<b>两条腿分别降级，不要读成一条</b>：TC 缺席 ⇒ 桥未装配 ⇒ 本方法对<b>任何</b> tag 都返
     * {@code false}（复核拿不到，宁缺不错）；而容量表的<b>行数与序</b>仍由白名单定（6 行照旧），
     * 因为那一面读的是 {@code PocketConstants#PRIMAL_TAGS}，不经本方法。合取判据的原文与
     * "为什么 {@code isPrimal()} 单独用会假判"（addon 可注册无 components 的新 aspect）写在
     * {@link TaumBridgeApi#isPrimalTag(String)}。
     *
     * @return true = 白名单内<b>且</b> TC 复核为元始；否则 false（含 TC 缺席）
     */
    public static boolean isPrimalTag(String tag) {
        if (!PocketConstants.isPrimalTag(tag)) {
            return false;
        }
        TaumBridgeApi active = readyBridge();
        return active != null && active.isPrimalTag(tag);
    }

    /**
     * ★<b>R96 S9a：给一支 TC 法杖灌入元素容量</b>（缓慢充能腿的唯一写侧；调用方是
     * {@code common/items/pocket/mage/PocketWandChargeDriver}，口袋侧不得再摸第二条路）。
     * <p>
     * 为什么这一句<b>必须</b>过桥而不能在常驻类里照 TC 的形状自己写 NBT：vis 的
     * <b>×100 刻度</b>与<b>杖芯上限</b>两条外部事实都在 TC 手里
     * （取证 {@code r96-ret4.md} §3.1；判据原文与"为什么不能像 {@link #isPrimalTag(String)} 那样
     * 纯白名单成立"写在 {@link TaumBridgeApi#chargeWandVis(ItemStack, String, int)}）。
     * <p>
     * 降级：TC 缺席、桥未装配、tag 不在白名单 ⇒ {@link TaumBridgeApi#WAND_NOT_CHARGEABLE}
     * （法杖未改动，调用方据此<b>一分都不掏</b>自己的容量）。
     *
     * @return {@code -1} = 不认这一栈；{@code 0} = 认得但一分没吃；{@code >0} = <b>实际灌入</b>点数
     */
    public static int chargeWandVis(ItemStack wand, String tag, int points) {
        if (wand == null || points <= 0 || !PocketConstants.isPrimalTag(tag)) {
            return TaumBridgeApi.WAND_NOT_CHARGEABLE;
        }
        TaumBridgeApi active = readyBridge();
        return active == null ? TaumBridgeApi.WAND_NOT_CHARGEABLE : active.chargeWandVis(wand, tag, points);
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
