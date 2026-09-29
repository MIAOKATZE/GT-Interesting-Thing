package com.miaokatze.gtit.gui.pocket;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.miaokatze.gtit.common.items.pocket.EssenceNativeChannels;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceIntake;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;
import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;
import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;
import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 右栏 72 格源质盘的单格（需求 4 的源质入口）：显示格 + 可被 NEI 拖入的 ghost 声明位。
 * <p>
 * <b>为什么必须换类而不是加东西</b>（R70）：原来这些格是裸 {@link ButtonWidget}，
 * 它不实现 {@link RecipeViewerGhostIngredientSlot} ⇒ 面板的拖入分发（按 hover 列表 +
 * {@code instanceof} 判定）根本看不到它 ⇒ 「源质格拖入」在游戏内零入口。本类
 * {@code extends ButtonWidget<NekoEssenceGhostCell>} <b>并</b>实现该接口：
 * 既有的点击取瓶、着色、数量浮层、tooltip 与 6×12 布局<b>一格不加不减</b>（R41b/R78②）。
 * <p>
 * <b>★内容层按库存开关（R78 D-1，本片修的上一片交付不实）</b>：旧实现把 aspect 图标
 * <b>无条件</b> {@code overlay(icon)} ⇒ 空格也画图标，与"无货不画内容"的裁定相反，
 * 而本类旧 javadoc 又写着"无货时只撤掉图标与文本"⇒ <b>注释声明了代码没做的事</b>。
 * 现在唯一写入口是 {@link #setCellContent(String, int)}：
 * <ul>
 * <li>{@code tag != null && 库存 > 0} ⇒ overlay = 该 tag 的图标（着色）、数量文本 = 点数；</li>
 * <li>否则 ⇒ {@code overlay()} 清空（MUI2 的 {@code IDrawable.of(无元素)} 返回 {@code null}，
 * {@link #hasContentLayer()} 读的就是这个 widget 真值而不是自报标记）、数量文本清空。</li>
 * </ul>
 * ★<b>槽位底与格位本体恒在</b>（R73②「空格子不显示源质，但是格子本身要显示」）：撤的是
 * <b>内容层</b>，不是格子。
 * <p>
 * <b>★声明语义 = 「确认把本格对应的 tag 声明为 ghost」</b>：格位归属由服务端算好并随
 * 同步 blob 落到本类（★R86 口径：格序 = 该 tag <b>首次入账</b>的顺序，但扣到 0 会<b>当场腾格</b>，
 * 所以"本格对应的 tag"是<b>现读</b>值而不是终身绑定；旧 R78③「撤空不回收」已作废），所以拖进来的
 * 东西不需要"是什么"，但<b>必须确实含本格的 tag</b>（{@link #carriesTag}：蒸馏产出或容器内容
 * 任一命中）。不含 ⇒ 返回 false，NEI 那边继续拖着、不吃栈 ⇒ 玩家拖来的任意东西不会被当成源质声明。
 * ★R90 E3（D3）补充：<b>本格无归属</b>时，拖入物<b>自带可读 tag</b>（容器内容非空，判据
 * {@link #tagOfCarrier}）也能组键发出<b>建档声明</b>——服务端对账（有归属不匹配 ⇒ 拒）后
 * {@code assignCell} 占格；无 NBT 裸栈/裸晶读不出 tag，仍一律拒收。
 * <p>
 * <b>★ghost 只原位改属性</b>（R41b）：实例从装配到关屏不换，声明态只影响遮罩与 tooltip；
 * 内容层同样走 {@link #setCellContent(String, int)} 原位切换 ⇒ 格数恒定 72、widget 树不因
 * 数据变化（R32 双端同树的前提）。72 格本就是显示侧、<b>不进 Container</b>（R35），
 * 因此既不占 220 也不动槽号。
 * <p>
 * <b>TC 不在场时一律不收</b>（R31 的整栏灰显由 {@code setEnabledIf} 给出，本类用
 * {@code IWidget#areAncestorsEnabled()} 读它，与 {@link NekoFilterSlot} 同一口径）。
 */
public class NekoEssenceGhostCell extends ButtonWidget<NekoEssenceGhostCell>
    implements RecipeViewerGhostIngredientSlot<ItemStack> {

    /** 虚化遮罩色（与 {@link NekoFilterSlot}、{@link NekoPocketFluidSlot} 同一 alpha 口径，R18）。 */
    private static final int GHOST_MASK = 0x80FFFFFF;

    /**
     * ★R91-⑤：中键按钮号（MUI2 原样透传 {@code Mouse.getEventButton()}，★不按 0/1 白名单过滤）。
     * <p>
     * ★数值刻意<b>在本类各写一份私有常量</b>而不是抽一个共享常量：三组格件的"早退理由"彼此不同
     * （中栏防原版创造取物、流体槽防 phantom 分支穿透、源质格防 ButtonWidget 谓词空转），
     * 抽在一起迟早有人拿"同源"的名头把三条不同的纪律并成一条注释。按钮号本身是 LWJGL2 的固定事实。
     */
    private static final int MOUSE_BUTTON_MIDDLE = 2;

    private NekoPocketPanel owner;
    /** 本格在 {@code Kind.ESSENCE} 索引空间里的槽号（= 格位，0…{@code ESSENCE_DISPLAY_GRID}−1）。 */
    private int cellIndex = -1;
    /**
     * 本格归属的 aspect tag（★<b>可变</b>：R78③ 后由服务端的格位归属表算出并随同步 blob 落到这里，
     * 不再是装配期一次定死的 {@code aspectOrder()[index]}；{@code null} = 该格从未被占过）。
     */
    private String tag;
    /** 本格当前的点数（★内容层的开关依据，只由 {@link #setCellContent(String, int)} 写入）。 */
    private int stock;
    /**
     * 数量浮层的当前文本（由 {@link ContentSink#showAmount(String)} 写；★空态是空串，
     * 与图标的撤除出自同一条 {@link #applyContentLayer} ⇒ 不会出现"撤了图标却留着旧数字"）。
     */
    private String amountText = "";
    private boolean ghost;
    /**
     * ★R83 C2：本格声明的组上限<b>原始值</b>（{@link PocketConstants#FILTER_CAP_UNSET} = 没人滚过轮）。
     * 权威值在服务端，经 ghost blob 落回本字段；本字段只用于显示与"下一次步进从哪走"。
     */
    private int declaredCap = PocketConstants.FILTER_CAP_UNSET;
    /**
     * ★★<b>R91-⑤</b>：本格的互斥属性（NONE / BIND / MEMORY）。数据源 = 面板上
     * {@code SYNC_GHOST_FLAGS} 那枚<b>独立</b>同步值的客户端镜像（经双源 accessor 刷进来）。
     * <p>
     * ★attr 挂在<b>格</b>上（手势点的就是这一格），而 ghost 声明按 <b>tag</b> 归位（R86 的遮罩归位口径）
     * —— 两件事刻意不同源，别把 attr 也一起按 tag 搬走。
     */
    private int ghostAttr = PocketConstants.GHOST_ATTR_NONE;
    /** ★R91-⑤：正交位 {@code P}（本格内容永不进注入向），可与任一 attr 并存。 */
    private boolean uploadBlocked;

    public NekoEssenceGhostCell() {
        super();
        // ghost 声明与内容层都随时可增删 ⇒ tooltip 每次重画都重建（否则解绑后"右键取消"那行会留在屏上）。
        // ★R83 B2：这一句过去是<b>空转</b> —— {@code RichTooltip.buildTooltip()} 只在
        // {@code tooltipBuilder != null} 时才重建文本，而装配侧当年走的是 {@code tooltip(Consumer)}
        // （一次性通道），于是 {@code markTooltipDirty()} 只翻了脏位、行集永远不变。
        // 现在装配侧改走 {@code tooltipDynamic}（{@code NekoPocketEssenceColumn#essenceCell}），
        // 本 flag 与下面两处 {@code markTooltipDirty()} 才真的有读者，二者必须成对存在。
        tooltip().setAutoUpdate(true);
    }

    /**
     * 装配期绑定「哪一格属于哪个会话」（双端各一次，格数与格序恒定 ⇒ 不引入数据驱动的
     * widget 树变化，与 {@link NekoFilterSlot#bindGhost} 同形）。归属 tag 与点数不在这一定死，
     * 由 {@link #setCellContent(String, int)} 原位写入。
     */
    NekoEssenceGhostCell bindCell(NekoPocketPanel panel, int index, String aspectTag) {
        this.owner = panel;
        this.cellIndex = index;
        return setCellContent(aspectTag, 0);
    }

    /**
     * 内容层的<b>绘制目标</b>（★薄到能被零依赖套件用记录型实现驱动 ⇒ 用例跑的就是<b>生产那条分支</b>，
     * 不是把判据抄第二遍）。
     * <p>
     * 为什么要有这一层：本 JVM 里构造任何 MUI2 widget 都会抛
     * {@code NoClassDefFoundError: it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap}
     * （实测，与 {@link PocketGuiTextures} 的类注释同一条限制）⇒ "库存 0 时到底撤没撤图标"
     * 这件事没法用真 widget 断言。把"撤/画"的选择收进 {@link #applyContentLayer} 这一条静态序列，
     * 就能在纯 JVM 里钉住它；widget 侧（{@link #setCellContent}）只剩把三个动作翻译成
     * {@code overlay(...)} / {@code overlay()} / 文本，那一段属实机项。
     */
    public interface ContentSink {

        /** 画该 tag 的图标（生产实现 = {@code overlay(icon)}）。 */
        void showIcon(String aspectTag);

        /** ★撤图标（生产实现 = {@code overlay()}，MUI2 的 {@code IDrawable.of(无元素)} 返回 null）。 */
        void clearIcon();

        /** 数量文本（★空态是空串，不是"不调用"——漏调就是把旧数字留在屏上）。 */
        void showAmount(String text);
    }

    /**
     * ★R78 D-1 的单点：内容层到底画什么（图标 + 数量文本，或两者都撤）。
     * <p>
     * 判据是 {@link #drawsContentLayer}，两条出口必须<b>成对</b>：空态同时撤图标与文本，
     * 有货同时给图标与文本。回归用例
     * {@code essence_cell_content_layer_follows_stock} 驱动的就是本方法。
     */
    public static void applyContentLayer(String aspectTag, int points, ContentSink sink) {
        if (sink == null) {
            return;
        }
        if (drawsContentLayer(aspectTag, points)) {
            sink.showIcon(aspectTag);
            sink.showAmount(String.valueOf(points));
        } else {
            sink.clearIcon();
            sink.showAmount("");
        }
    }

    /**
     * ★R78 D-1 的唯一写入口：按「本格归属哪个 tag + 该 tag 现在有多少点」原位刷新内容层。
     * <p>
     * 无货（或该格从未被占过）⇒ 只撤图标与数量文本，<b>槽位底与格位本体都还在</b>（R73②）。
     * 选择逻辑在 {@link #applyContentLayer}（生产与测试共用同一条）。
     *
     * @param aspectTag 本格归属的 tag（{@code null} = 空格）
     * @param points    该 tag 当前的点数
     */
    public NekoEssenceGhostCell setCellContent(String aspectTag, int points) {
        this.tag = aspectTag;
        this.stock = Math.max(0, points);
        applyContentLayer(this.tag, this.stock, new ContentSink() {

            @Override
            public void showIcon(String aspectTag) {
                final UITexture icon = UITexture.builder()
                    .location(TaumCompat.imageLocationOf(aspectTag))
                    .fullImage()
                    .nonOpaque()
                    .build();
                final int color = TaumCompat.colorOf(aspectTag);
                // ★图标进 overlay 而不是 background：background 已被"槽位底"占用，两者叠反会让凹槽消失
                NekoEssenceGhostCell.this
                    .overlay(color == TaumCompat.COLOR_UNKNOWN ? icon : icon.withColorOverride(color));
            }

            @Override
            public void clearIcon() {
                // IDrawable.of(无元素) 返回 null ⇒ getOverlay() 变 null，内容层真的没东西可画
                NekoEssenceGhostCell.this.overlay();
            }

            @Override
            public void showAmount(String text) {
                NekoEssenceGhostCell.this.amountText = text == null ? "" : text;
            }
        });
        markTooltipDirty();
        return this;
    }

    /**
     * 内容层判据（★单点，装配、同步与回归套件读的都是这一条）：只有「本格有归属 tag」且
     * 「该 tag 有点数」才画图标与数量文本。
     * <p>
     * 单独成静态方法而不是埋在绘制里，是为了让 R78 通则"凡注释声称某状态下不绘制 X，
     * 必须有一条 JVM 用例钉住该状态下的内容层为空"真的可执行。
     */
    public static boolean drawsContentLayer(String aspectTag, int points) {
        return aspectTag != null && !aspectTag.isEmpty() && points > 0;
    }

    /**
     * ★机检点（钉 D-1，实机侧）：内容层当前是否在场 = 图标 drawable 是否在场。
     * <p>
     * 读的是 {@link #getOverlay()} 这个 widget 真值而不是自报标记；本 JVM 造不出 widget，
     * 所以零依赖套件走 {@link #applyContentLayer} 的记录型断言，本方法留给游戏内与调试树。
     */
    public boolean hasContentLayer() {
        return getOverlay() != null;
    }

    /** 本格当前的数量文本（★空态一律空串，与 {@link #hasContentLayer()} 同一开关）。 */
    public String stockText() {
        return amountText;
    }

    /** 本格归属的 tag（{@code null} = 该格从未被占过 ⇒ 无内容可画、也无东西可声明）。 */
    public String aspectTag() {
        return tag;
    }

    /** 本格当前的点数（客户端只读同步来的值）。 */
    public int essenceStock() {
        return stock;
    }

    /** 当前是否处于 ghost（已声明要拉取本格源质）态。 */
    public boolean isGhost() {
        return ghost;
    }

    /**
     * 原位切换 ghost 态（{@link NekoPocketPanel#applyGhosts()} 的唯一写入口，双端各一份）。
     * <p>
     * 不重建 widget、不改 6×12 布局、不动内容层（R41b：ghost 遮罩与"有没有货"是两件事）。
     */
    NekoEssenceGhostCell setGhost(boolean ghost) {
        if (this.ghost == ghost) {
            return this;
        }
        this.ghost = ghost;
        if (!ghost) {
            // 解绑后本格不再归属任何声明 ⇒ 上限读数必须复位，否则下一条声明沿用上一条的数字
            this.declaredCap = PocketConstants.FILTER_CAP_UNSET;
        }
        markTooltipDirty();
        return this;
    }

    /** ★R83 C2：服务端 ghost blob 落回本格组上限的唯一写入口（与另两支同名同语义）。 */
    NekoEssenceGhostCell setDeclaredCap(int cap) {
        if (this.declaredCap == cap) {
            return this;
        }
        this.declaredCap = cap;
        markTooltipDirty();
        return this;
    }

    /**
     * 本格声明真正生效的组上限（没滚过轮 = 回落值）。
     * ★R92-②：这条注释在 R84～R91 期间是<b>不实的</b>（它自称回落 {@code ESSENCE_CAP_PER_TAG}，实现读的
     * 却是 {@code FILTER_CAP_CEILING_ESSENCE}=64）；本号起两者同值 ⇒ 注释转真，但仍以本行为准：
     * 回落值住在 {@link PocketFilterConfig#resolveRawCap} 那条链上，不是本类自己写的第二处真相。
     */
    public int ghostCap() {
        // ★R95 S5（STACK 位）：回落跟升级位走（256/4096，与源质盘每格上限同一把尺；
        // 未绑定面板 ⇒ 未升级口径，与纯 JVM 用例同解）
        return PocketFilterConfig.resolveRawCapStackAware(
            PocketFilterConfig.Kind.ESSENCE,
            declaredCap,
            0,
            owner != null && owner.storageStackUpgraded());
    }

    /** ★R91-⑤：本格 attr 的显示侧写入口（与 {@link #setDeclaredCap} 同一个应用点、同一条"同值即返回"纪律）。 */
    NekoEssenceGhostCell setGhostAttr(int attr) {
        final int next = PocketConstants.normalizeGhostAttr(attr);
        if (this.ghostAttr == next) {
            return this;
        }
        this.ghostAttr = next;
        markTooltipDirty();
        return this;
    }

    /** ★R91-⑤：正交位 {@code P} 的显示侧写入口（只画左下绿角标；★不动内容层、不动遮罩）。 */
    NekoEssenceGhostCell setUploadBlocked(boolean blocked) {
        if (this.uploadBlocked == blocked) {
            return this;
        }
        this.uploadBlocked = blocked;
        markTooltipDirty();
        return this;
    }

    /** 本格当前的 attr（服务端算好、经 {@code SYNC_GHOST_FLAGS} 同步来的读数）。 */
    public int ghostAttr() {
        return ghostAttr;
    }

    /**
     * ★R92-⑤：本格的显示盘格号（0 起、未绑定 = {@code -1}）。
     * <p>
     * 存在的理由只有一条：空格子 tooltip 要说"这一格是盘上第几格"，而换算成玩家可见的 1 起口径
     * 必须走 {@link PocketConstants#essenceCellNumberOf} 那<b>一处</b> ⇒ 本方法只交出内部索引，
     * ★不在这里 +1、也不在显示点各写一遍。
     */
    public int cellIndex() {
        return cellIndex;
    }

    /** 本格是否挂了 {@code P}。 */
    public boolean isUploadBlocked() {
        return uploadBlocked;
    }

    /**
     * ★★<b>R91-⑤⑧b 重排后的按键矩阵</b>（72 格源质盘）——★与另两组格件同一套手势：
     * <b>中键</b> = 请求绑定 BIND ／ <b>alt+左</b> = 记忆 {@code L} ／ <b>alt+右</b> = 阻拦上传 {@code P}
     * ／ 右键（已声明）= 解绑（★既有支，<b>被 alt+右排在后面</b>）／
     * 左键 + 非 alt + 游标持物 = {@code owner.dispatchEssenceCellPress}（★R91-e 起那条支<b>带 shift 位</b>）。
     * <p>
     * ★★<b>排序即判据</b>（取证 G3）：{@code alt+右} 必须排在 {@code ghost && mouseButton == 1} 那条
     * 解绑支<b>之前</b>，否则声明格上的 alt+右 会先被读成"解绑"，玩家按出来的就不是 {@code P}。
     * ★中键同样早退不调 {@code super}（{@code ButtonWidget} 把按钮号交给 builder 的谓词，默认那条只认
     * 0/1 ⇒ 不早退就是"事件被 ACCEPT 穿透、无事发生"的第二种手感，取证 §2②）。
     * <p>
     * ★★旧的两条 alt+左 支（"已是声明格 ⇒ 停住"与"alt+左 = 绑本格存量"）已随改判<b>合并成一条
     * alt+左 = 记忆</b>；"按本格已有内容建档"那层语义迁到<b>中键 BIND</b>，且那份内容读数改由<b>服务端</b>
     * 读真实格内容（{@code NekoPocketServerHandler#ghostPayloadAt}，★客户端不把载荷抄一遍送上来）
     * ⇒ {@code requestBindFromStock} <b>整体删除</b>、不留注释尸（R84 的 U2 同一条纪律）。
     */
    @Override
    public Result onMousePressed(int mouseButton) {
        if (mouseButton == MOUSE_BUTTON_MIDDLE) {
            requestGhostFlag(PocketConstants.GHOST_FLAG_BIND);
            return Result.SUCCESS;
        }
        if (mouseButton == 1 && Interactable.hasAltDown()) {
            requestGhostFlag(PocketConstants.GHOST_FLAG_UPLOAD_BLOCK);
            return Result.SUCCESS;
        }
        if (ghost && mouseButton == 1 && owner != null && cellIndex >= 0) {
            owner.requestGhostClear(PocketFilterConfig.Kind.ESSENCE, cellIndex);
            return Result.SUCCESS;
        }
        if (mouseButton == 0 && Interactable.hasAltDown()) {
            requestGhostFlag(PocketConstants.GHOST_FLAG_MEMORY);
            return Result.SUCCESS;
        }
        // ★★<b>R92-④（D4）：撤掉这里的 {@code !ghost}</b> —— 已声明（含"记忆档 pending"）的格也要能把
        // 满瓶点进去，否则"玩家直接放安瓿瓶上去配置"这一条在<b>属性闸之前</b>就不可达（取证 B2 点名的
        // 那个前置）。★取出向（游标持空瓶）在 {@code ghost == true} 时仍按 R91-④ 原样早退，
        // 由 {@code NekoPocketPanel#dispatchEssenceCellPress} 那一条分流负责，本方法不加第二份判据。
        if (mouseButton == 0 && owner != null && cellIndex >= 0 && owner.dispatchEssenceCellPress(cellIndex, ghost)) {
            return Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
    }

    /**
     * ★R91-⑤：三个手势共用的出口（与 {@code NekoFilterSlot#requestGhostFlag} 同形，零新动作码、零新同步键；
     * ★本地一个字节都不改 —— attr/P 的迁移真值在服务端算，经 {@code SYNC_GHOST_FLAGS} 推回来）。
     *
     * @return 是否真的发出了请求（未绑定面板 / 格号非法 / 整栏灰显（TC 缺席）⇒ {@code false}）
     */
    private boolean requestGhostFlag(String gesture) {
        if (owner == null || cellIndex < 0 || !areAncestorsEnabled()) {
            return false;
        }
        return owner.requestGhostFlag(PocketFilterConfig.Kind.ESSENCE, cellIndex, gesture);
    }

    /**
     * ★R83 C2（判据 4）：alt+滚轮 = 调本格声明的组上限；alt+ctrl 把<b>步进</b>放大 10 倍（D-6 裁定）。
     * <p>
     * 步进表、夹取、"未设从天花板起走"全在 {@link PocketGhostRequest#nextCap} 这一条纯函数里 ⇒ 步进只有一份真值；
     * 本方法只负责"这一格是不是声明格 + 修饰键读数"。不是声明格时把事件原样交回 {@code super}
     * （{@link ButtonWidget} 没有 {@code onMouseScroll} 覆写，默认 {@code false} ⇒ 不消费也不挡下层）。
     */
    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        if (!ghost || owner == null || cellIndex < 0 || !areAncestorsEnabled() || !Interactable.hasAltDown()) {
            return super.onMouseScroll(scrollDirection, amount);
        }
        // ★R95 S5：天花板按升级位现算（256/4096）——"未设从天花板起走"与 ghostCap 的显示读数同源连续
        final int next = PocketGhostRequest.nextCapEffective(
            PocketFilterConfig.Kind.ESSENCE,
            declaredCap,
            PocketConstants.essenceCapPerTag(owner != null && owner.storageStackUpgraded()),
            false,
            scrollDirection,
            Interactable.hasControlDown());
        if (!owner.requestGhost(cellIndex, PocketGhostRequest.capDirective(PocketFilterConfig.Kind.ESSENCE, next))) {
            return false;
        }
        // ★本地即时回显：不回显的话"滚了数字不动"会被读成功能没生效；回显值与服务端算的是同一条 nextCap
        setDeclaredCap(next);
        return true;
    }

    // ------------------------------------------------------------------ NEI 拖入 / 右键解绑

    /**
     * ★拖入入口：<b>只发请求</b>，本地不落档（R18/R19）；不收的一切情形都不吃栈。
     * <p>
     * 分发条件是库内的「hover + {@code instanceof RecipeViewerGhostIngredientSlot}」，
     * 与本列调过 {@code excludeAreaInRecipeViewer()} 无关（R70 实测）⇒ 与中栏物品拖入同一机制。
     * <p>
     * ★R90 E3（D3）：组键用的 tag 经 {@link #tagOfCarrier} 取「本格 tag 或（本格无归属时）拖入物
     * 自带的可读 tag」⇒ 拖一只<b>满瓶</b>到<b>无归属空格</b>也能组出键、发出建档声明
     * （服务端 {@code PocketGhostRequest#apply} 三参形态对账 + {@code assignCell} 落位）；无 NBT
     * 裸栈 / 裸晶仍组不出键（拒收面不变）。
     */
    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        if (owner == null || draggedStack == null || cellIndex < 0) {
            return false;
        }
        // ★★<b>R91-b</b>：只有 P（无 attr）的格 ⇒ 拖拽<b>不响应</b>。★判在 tagOfCarrier / 日志 /
        // 清 stackSize <b>之前</b>（"P 不携带内容"这条承诺的空内容钉 = 栈不被吃掉、请求也不发）。
        if (PocketGhostRequest.dragRouteOf(ghostAttr, uploadBlocked) == PocketGhostRequest.DragRoute.IGNORE) {
            return false;
        }
        // ★L8（[PocketR89]，客户端拖入入口）：拖入交付栈的完整读数（含 NBT）——U2（MUI2/NEI 拖拽交付
        // 栈是否保 NBT）的裁决证据<b>只在这里可见</b>（C2S 串只带载荷键，服务端永远看不到这栈）。
        // debug 每拖必记；首例升 INFO 一次（终验常规 jar 可直读；拖拽是玩家节拍事件，不构成刷屏面）。
        final TaumAspectAmounts draggedContainer = EssenceGate.TAUM.readContainer(draggedStack);
        logDragDump(draggedStack, draggedContainer);
        // ★R90 E3（D3）：组键用的 tag 改走 tagOfCarrier——本格有归属 ⇒ 本格 tag（行为与 R88 逐字一致）；
        // 本格无归属 ⇒ 拖入物<b>自带的可读 tag</b>（容器内容非空）⇒ 空格建档声明的客户端入口。
        final String carrierTag = tagOfCarrier(draggedStack, tag, EssenceGate.TAUM);
        final String typeId = channelTypeId(carrierTag);
        final String key = ghostKeyFor(
            button,
            areAncestorsEnabled(),
            carriesTag(draggedStack, carrierTag, EssenceGate.TAUM),
            carrierTag,
            typeId);
        if (key.isEmpty()) {
            // ★L9（[PocketR89]，客户端发送侧）：请求没发出去时的拒收回读——"无请求到达"（服务端 L8 静默）
            // 的判因就在这四个读数里：非左键 / 栏灰显（hover 分发面）/ 拖入物无可读 tag（无 NBT 裸栈·裸晶）/
            // 通道缺席。只打客户端发送侧读数（与 R89 L9 的"回查 requestGhost 前置"同一条）。
            GTInterestingThing.LOG.debug(
                "[PocketR89] L9 拖入未发请求：格 {} 本格tag={} 拖入tag={} 通道id={} 按键={} 栏可用={} 容器读数={}",
                cellIndex,
                tag,
                carrierTag,
                typeId,
                button,
                areAncestorsEnabled(),
                draggedContainer.size());
            return false;
        }
        draggedStack.stackSize = 0;
        return owner.requestGhost(cellIndex, key);
    }

    /**
     * ★L8 的读数体（独立成方法只为让 {@link #handleDragAndDrop} 的判定流保持可读）：
     * 物品 / meta / 叠数 / NBT 原文 / 双探针（容器 + 蒸馏）读数。首例 INFO 升级用本类自己的闩
     * （与 {@code NekoPocketPanel#logOnce} 同形态；首例打 INFO 后同键静默，后续样本走 debug 由调用方记录）。
     */
    private void logDragDump(ItemStack draggedStack, TaumAspectAmounts container) {
        final String nbt = draggedStack.getTagCompound() == null ? "无"
            : draggedStack.getTagCompound()
                .toString();
        final String line = "[PocketR89] L8 拖入栈读数：格 " + cellIndex
            + " 本格tag="
            + tag
            + " 栈="
            + draggedStack.getUnlocalizedName()
            + " meta="
            + draggedStack.getItemDamage()
            + " x"
            + draggedStack.stackSize
            + " NBT="
            + nbt
            + " 容器读数="
            + container
            + " 蒸馏读数="
            + EssenceGate.TAUM.aspectsOf(draggedStack);
        if (DRAG_DUMP_LOGGED.add("cell-dump")) {
            GTInterestingThing.LOG.info(line + "（U2 裁决首例升 INFO，后续样本走 debug）");
        }
        GTInterestingThing.LOG.debug(line);
    }

    /** {@link #logDragDump} 的首例 INFO 闩（见 {@code NekoPocketPanel#LOG_ONCE} 的同形说明）。 */
    private static final java.util.Set<String> DRAG_DUMP_LOGGED = java.util.Collections
        .newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    // ------------------------------------------------------------------ 纯判定（回归套件驱动这两段）

    /**
     * ★R90 E3（D3）：<b>组键该用哪个 tag</b>——本格 tag 优先；本格<b>无归属</b>（{@code null/空}）时
     * 回落<b>拖入物自带的可读 tag</b>（{@code gate.readContainer} 非空 ⇒ 取首个有量 tag，
     * 与点击入槽共读 {@link PocketEssenceIntake#firstTagOf} 这一条"自带 tag"判据单源）。
     * <ul>
     * <li>本格有归属 ⇒ 恒返本格 tag（与 R88 行为逐字一致，<b>不受拖入物影响</b>）；</li>
     * <li>本格无归属 + 拖入物容器读数非空 ⇒ 拖入物的 tag ⇒ {@link #handleDragAndDrop} 可在<b>空格</b>
     * 上组出键、发出建档声明（服务端对账 + 建档，见 {@code PocketGhostRequest#apply} 三参形态）；</li>
     * <li>本格无归属 + 拖入物是 <b>NEI 的源质伪物品</b>（★R91-⑦：ARI 塞进物品列表/书签面板的显示件，
     * 不是容器、也不在 TC 蒸馏表里 ⇒ 旧两条探针<b>结构性双空</b>）⇒ 由同一条单源判据的第三档
     * 给出它 NBT 里那个<b>已过 TC 复核</b>的 tag ⇒ 拖它也能建档（这正是用户报的「无法绑定标记」根-3）。
     * ★伪物品<b>只声明、绝不入库存计点</b>，边界表在
     * {@code PocketEssenceIntake#declarationTagOf}；</li>
     * <li>本格无归属 + 拖入物读不出 tag（无 NBT 裸栈 / 裸晶 / 非容器 / 空瓶）⇒ {@code null} ⇒
     * {@link #carriesTag} 对空 tag 的既有拒收入口生效（need_stock 文案面）。</li>
     * </ul>
     * 刻意<b>不</b>把蒸馏读数（{@code aspectsOf}）当"自带 tag"：多 tag 蒸馏产出没有唯一归属，
     * 建档声明的语义载体是<b>容器</b>（满瓶/带 NBT 旧晶/第三方罐），与 R88 载体口径同源。
     */
    public static String tagOfCarrier(ItemStack draggedStack, String cellTag, EssenceGate gate) {
        // ★R91-⑦：反解本体单源下沉到 PocketEssenceIntake#declarationTagOf（本格 tag 优先 → 真容器首 tag →
        // 白名单伪物品的 NBT tag）。GUI 这一层只留薄封装：本文件不得出现字面 NBT 键名或第三方注册名。
        return PocketEssenceIntake.declarationTagOf(draggedStack, cellTag, gate);
    }

    /**
     * 拖入物是否<b>含本格的 tag</b>：容器内容（{@code TaumCompat.readContainer} 口径）或蒸馏产出
     * （{@code TaumCompat.distill} 口径）任一命中即算含。
     * <p>
     * ★走 {@link EssenceGate} 而不是直调 {@link TaumCompat}：TC 缺席时三条读数<b>恒</b>为空，
     * 拿生产实现跑零依赖测试只会得到"永远不匹配"的假绿（同 R59b 偏离①、
     * {@code PocketSlots#classifyIncoming(ItemStack, EssenceGate)} 的既有口径）。
     * 本仓不引第二个探针接口，故复用 {@code EssenceGate}。
     * <p>
     * ★★<b>R88 改判（作废 R87-e 的"晶族恒真"特判）</b>：搬运载体是 <b>TC 安瓿瓶</b>，一瓶固定
     * {@value TaumDistillRules#PHIAL_CAPACITY} 点，而瓶的 tag <b>就写在它自己的 NBT 里</b>
     * （TC {@code ItemEssence} 的 {@code getSubItems} 实测逐 aspect 造满瓶并 {@code setAspects(add(tag,8))}
     * ⇒ NEI 物品面板里"每种源质一条"的那些条目<b>自带可读 NBT</b>，与旧晶那条无 NBT 的裸栈不同）。
     * 于是"拖瓶声明"走下面这条通用容器探针就能判，<b>不需要</b>任何按物品族的恒真特判。
     * <p>
     * ★自立口径 <b>C2（旧晶只读不产）</b>在本判据上的落点：旧晶不再被"族"放行，但<b>带着 NBT 的旧晶
     * 照旧命中</b>（{@code readContainer} 读得到 {@code add(tag,1)}）⇒ 识别留着、生产撤了。
     * ★如实登记的代价：NEI 里那条<b>无 NBT 的裸晶</b>（旧 R87-e 特判专门为之而加）现在一律判不出 tag
     * ⇒ 拖它声明不成立；玩家要声明/入槽请拖<b>瓶</b>。
     * ★★<b>R90 E3（D3）起"空格（{@code cellTag} 为 null/空）拒收"收窄</b>：本方法对空 cellTag 仍返
     * false（判据"含<b>这一格</b>的 tag"对空格无解，本签名与既有用例不动），但空格的建档入口改走
     * {@link #tagOfCarrier}（拖入物自带 tag ⇒ 可组键）——旧 R86"空格一律不收"只在"拖入物读不出
     * tag"那一半继续成立。
     * <p>
     * ★<b>R91-⑦ 增加第三条读数</b>：前两条（容器 / 蒸馏）之外，再认一次
     * {@code PocketEssenceIntake#declarationTagOf} 的<b>白名单伪物品</b>档 ⇒ 拖 ARI 的源质显示件到
     * 本格也能判"含"。★它<b>不是</b>本文件里的第二段解析：键名、注册名、TC 复核全在
     * {@code crossmod/taum/} 与 {@code PocketEssenceIntake} 各一份，本方法只调那一条判据。
     */
    public static boolean carriesTag(ItemStack draggedStack, String cellTag, EssenceGate gate) {
        if (draggedStack == null || gate == null || cellTag == null || cellTag.isEmpty()) {
            return false;
        }
        // ★R88：唯一判据 = 容器/蒸馏读数里真的出现本格 tag（瓶、带 NBT 的旧晶、第三方罐共用这一条）
        final TaumAspectAmounts container = gate.readContainer(draggedStack);
        if (container != null && container.getAmount(cellTag) > 0) {
            return true;
        }
        final TaumAspectAmounts distilled = gate.aspectsOf(draggedStack);
        if (distilled != null && distilled.getAmount(cellTag) > 0) {
            return true;
        }
        // ★R91-⑦：第三档 = <b>同一条</b>声明侧单源判据（白名单伪物品）。第二参传 null 是刻意的——
        // 本格 tag 优先那一档在这里不适用（本方法问的正是"这栈含不含<b>这一格</b>"），
        // 传 cellTag 进去会让任何栈都被第一档短路成"含本格 tag"，那是把判据自己废掉。
        return cellTag.equals(PocketEssenceIntake.declarationTagOf(draggedStack, null, gate));
    }

    /**
     * 拖入是否构成一条源质声明：左键 + 该栏可用（灰显一律不收，R31）+ <b>组键 tag 非空</b> +
     * 拖入物确实含该 tag + 解出了通道 id ⇒ 载荷键 {@code e:<typeId>:<tag>}；否则 {@code ""}。
     * <p>
     * 键的生成只走 {@link PocketFilterConfig#essenceKey(String, String)}（<b>不自造第四种键格式</b>）。
     * ★R90 E3（D3）：<b>组键 tag 的来源放宽到 {@link #tagOfCarrier}</b>——第四参不再必然是"本格归属"
     * （无归属空格上传入 {@code tagOfCarrier} 给出的拖入物自带 tag 也合法，服务端建档）；判据本体
     * （要不要发 SET、通道 id 缺席拒收）一条未动。
     */
    public static String ghostKeyFor(int button, boolean regionEnabled, boolean tagMatched, String cellTag,
        String typeId) {
        if (button != 0 || !regionEnabled || !tagMatched || cellTag == null || cellTag.isEmpty()) {
            return "";
        }
        if (typeId == null || typeId.isEmpty()) {
            // 没有任何已注册的第三方通道能吃下这一 tag ⇒ 声明无处可抽，不收（不写死声明再静默空转）
            return "";
        }
        return PocketFilterConfig.essenceKey(typeId, cellTag);
    }

    /**
     * 本格源质所在的<b>通道 id</b>（{@code IAEStackType.getId()} 字符串）。
     * <p>
     * ★R90 E2 判法已<b>单源下沉</b>到 {@link EssenceNativeChannels}（★R91-① 改口为 AE2 <b>容器契约</b>：
     * 满瓶探针经该通道的容器识别 + 容器→通道栈换算，含第三方实现的 {@code Throwable} 容错与 TC 缺场判空）——
     * 本方法只是声明侧的薄封装，不再内联探针循环。与消费端同一只探针：写进声明的 id 与
     * {@code InfinityStackTypes.byId} 能解析出的天然是同一个，不会出现两处真相。
     * <p>
     * ★★<b>R90（AUQ-①=B）起不再回落物品通道</b>（R86"缺陷 4 乙"的兜底随本次撤销）：没有任何
     * <b>源质原生通道</b>能吃下这一 tag ⇒ 返回 {@code ""} ⇒ {@link #ghostKeyFor} 对空通道 id 的
     * 既有拒收入口生效，<b>不写声明</b>（无处可抽的声明就是死声明）。内建流体通道照旧排除
     * （源质落在流体通道上没有任何读法成立）。旧口径"回落后 1 单位 = 一只安瓿瓶"的语义代价说明
     * 随之作废：上传/下传都只走源质原生通道，"1 单位"只由原生通道的探针倍率定义。
     * <p>
     * 边界如实声明：探针需要真实注册表与 TC，纯 JVM 里拿不到（与
     * {@code extract_essence_branch_yields_phials} 同一批未验面，实验 E3），因此本方法属<b>实机项</b>
     * （TC 缺席时恒 {@code ""}，与"没有源质可传"同向）；判据（含不含 tag、要不要发 SET、落到哪个
     * 索引空间）全在 {@link #carriesTag} 与 {@link #ghostKeyFor} 这两段纯函数里，由回归套件驱动。
     */
    static String channelTypeId(String cellTag) {
        return EssenceNativeChannels.nativeChannelTypeId(cellTag);
    }

    // ------------------------------------------------------------------ ghost 态的渲染

    /**
     * 声明态叠一层与中栏、流体条同色的弱遮罩（4×12 几何与着色/数量浮层一个字都不动）。
     * <p>
     * ★R84：与另两支同形修正——本格<b>已有存量</b>时不再叠遮罩（旧判据单比特 {@code if (ghost)}，
     * 于是"已经补到东西的格"和"一格都没有的格"长得一模一样，玩家读到"需求从来没有被满足过"）。
     */
    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.drawOverlay(context, widgetTheme);
        // ★★R91-⑤：遮罩"在场"判据 = ghost || attr != NONE（pending 绑定/记忆也要虚化，观感与已建档一致）；
        // ★"本格已有存量就不遮"那一条判据本体（stock <= 0）R84 口径<b>一字未动</b>。
        if (PocketGhostRequest.drawsGhostMask(ghost, ghostAttr) && stock <= 0) {
            GuiDraw.drawRect(1, 1, getArea().w() - 2, getArea().h() - 2, GHOST_MASK);
        }
        drawCapReadout();
        // ★R91-⑤：左上蓝 L / 左下绿 P。★角标与遮罩互不覆盖（遮罩是底色、角标画在它之上），
        // 且 L / P 分处上下两角 ⇒ 同一格同时挂 attr 与 P 时两个都读得到。
        // ★R100 片 F：绘制体走共享助手 PocketBadgeDrawer（原三份同形 private drawBadge 收拢）。
        PocketBadgeDrawer.drawBadge(
            PocketGhostRequest.memoryBadgeText(ghostAttr),
            PocketGhostRequest.memoryBadgeTop(),
            PocketGhostRequest.memoryBadgeColor());
        PocketBadgeDrawer.drawBadge(
            PocketGhostRequest.uploadBlockBadgeText(uploadBlocked),
            PocketGhostRequest.uploadBlockBadgeTop(getArea().h()),
            PocketGhostRequest.uploadBlockBadgeColor());
    }

    /**
     * ★R83 C2（判据 7）：声明格右上角的橙色组上限读数。
     * <p>
     * 色、缩放、右对齐算式与缩写全部取自 {@link PocketGhostRequest}（三类共用一份），本方法只负责"画"。
     * <p>
     * ★★R92-③：早退从"只看 {@code ghost}"改问 {@link PocketGhostRequest#capReadoutVisible}
     * （{@code declared && attr != MEMORY}）⇒ 源质支的 alt+左键记忆格也不再有那抹橙，三支同一条判据。
     */
    private void drawCapReadout() {
        final String text = PocketGhostRequest.capReadoutVisible(ghost, ghostAttr)
            ? PocketGhostRequest.capReadout(ghostCap())
            : "";
        if (text.isEmpty()) {
            return;
        }
        final float x = PocketGhostRequest.capReadoutX(getArea().w(), PocketGhostRequest.capReadoutWidth(text));
        // ★shadow=true：遮罩是接近白的浅色，不带阴影的橙字压上去就是糊成一片
        GuiDraw.drawText(
            text,
            x,
            PocketGhostRequest.CAP_READOUT_TOP,
            PocketGhostRequest.CAP_READOUT_SCALE,
            PocketGhostRequest.capReadoutColor(),
            true);
    }
}
