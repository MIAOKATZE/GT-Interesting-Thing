package com.miaokatze.gtit.gui.pocket;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;

/**
 * 流体列的<b>流体槽本体</b>（R75① 的排法 a + R78② 的 3 组：面板左侧那 18 个槽之一，
 * 每组的纵向 = 输入行 / <b>本槽（18 宽 × 36 高）</b> / 输出行）：
 * 真实流体槽 + 一个可被 NEI 拖入的 ghost 声明位（需求 4 的流体入口）。
 * <p>
 * <b>★不调 {@code super.handleDragAndDrop}、也★不把本槽 handler 设成 phantom</b>
 * （R70 实测口径）：库内那一条的开头是 {@code if (!syncHandler.isPhantom()) return false} ——
 * 真实槽上它恒拒；而把 handler 改成 phantom 会让<b>服务端那一支把流体写进真实槽</b>
 * （{@code FluidSlotSyncHandler#readOnServer} 的 {@code id==4} 分支直接 {@code tryClickPhantom}），
 * 需求 2 的"从手上/仓里灌排流体、两格同权"就没了。⇒ 唯一正确做法是<b>子类在 phantom 门之前拦下</b>，
 * 只发本仓那一条 {@code SYNC_GHOST_REQUEST}（与 {@link NekoFilterSlot} 同形，R18/R19 不破）。
 * <p>
 * ★<b>R91-p（L 的执法腿·流体支）</b>："往本列<b>灌入</b>流体"这条玩家写入口的拦截<b>不在本 widget</b>——
 * 装配处（{@code NekoPocketLeftColumn#fluidSlots}）给 handler 挂库自带的 {@code filter} 谓词，
 * 记忆列被 {@code FluidSlotSyncHandler#fillFluid} 的预检整支拒掉（判据单源
 * {@code PocketFilterConfig#memoryAllowsFluid}，本类不含第二份 attr 读法；抽取方向与三个开关不动）。
 * <p>
 * <b>ghost 只原位改属性</b>（R41b）：本 widget 实例从装配到关屏不换，声明态只影响
 * 渲染样本、遮罩与 tooltip；几何（★R78② 由 18×18 拉长为 18×36）与 {@code alwaysShowFull(false)}
 * 的部分填充口径都由 {@link NekoPocketLeftColumn} 原样保留 ⇒ 双端同树（R32）不受影响。
 * ★拉长后的<b>液面比例</b>由库内绘制决定，纯 JVM 测不到 ⇒ 实机核验项（回执点名）。
 * <p>
 * <b>★槽号 = tank 号 = {@code Kind.FLUID} 的 ghost 索引</b>（R75 由 1 根竖条变
 * {@code PocketConstants.FLUID_COLUMN_COUNT} 个 tank，R78② 再变
 * {@code PocketConstants.FLUID_TANK_TOTAL} = 18 个，{@code GHOST_FLUID_SLOT_LIMIT} 与它同源）：
 * 不再有"恒为 0"的特例，
 * 列号由 {@link #bindBar(NekoPocketPanel, int)} 在装配期注入 ⇒
 * "这一列要拉哪种流体"与"拉进哪个 tank"共用同一个数，没有第二份映射。
 * {@code Kind.FLUID} 的索引空间与中栏 0…134、源质 0…71 各自独立，这正是 CLR 必须带区域字母的理由。
 */
public class NekoPocketFluidSlot extends FluidSlot {

    /** 旧的"唯一槽号"常量（流体侧有多个索引位后不再存在恒值；仅作为 0 号 tank 的可读写法）。 */
    public static final int FIRST_SLOT_INDEX = 0;

    /** 虚化遮罩色（与 {@link NekoFilterSlot} 同一 alpha 口径，R18）。 */
    private static final int GHOST_MASK = 0x80FFFFFF;

    /**
     * ★R91-⑤：中键按钮号（MUI2 原样透传 {@code Mouse.getEventButton()}，★不按 0/1 白名单过滤）。
     * <p>
     * ★数值刻意<b>在本类各写一份私有常量</b>而不是抽一个共享常量：三组格件的"早退理由"彼此不同
     * （中栏防原版创造取物、流体槽防 phantom 分支穿透、源质格防 ButtonWidget 谓词空转），
     * 抽在一起迟早有人拿"同源"的名头把三条不同的纪律并成一条注释。按钮号本身是 LWJGL2 的固定事实。
     */
    private static final int MOUSE_BUTTON_MIDDLE = 2;

    /** 反解流体名的探针（一格一个 {@code ItemStack -> 流体名或 null}）。 */
    public interface FluidNameProbe {

        /** @return 该拖入物的流体名；解不出返回 {@code null}（不得抛，抛由 {@link #firstFluidName} 兜住） */
        String fluidName(ItemStack draggedStack);
    }

    /**
     * 生产探针链（<b>按序兜底</b>，第一个给出非空名的即命中）：
     * <ol>
     * <li>{@link FluidContainerRegistry#getFluidForFilledItem(ItemStack)} —— <b>注册过的满容器</b>：
     * 水桶 / 岩浆桶、其它 mod 注册进 Forge 容器表的罐/瓶/桶。这是绝大多数拖入的场景。</li>
     * <li>{@link FluidRegistry#lookupFluidForBlock(Block)}（{@code Block.getBlockFromItem} 先反查方块）——
     * <b>流体方块本身</b>：创造模式拿到的水/岩浆方块，以及 molten metal 一类"源方块即流体"的 mod 方块。
     * 这一级兜的是"物品不是容器、而是流体方块"的漏网面。</li>
     * <li>★R86（缺陷 4 甲）{@link IFluidContainerItem#getFluid(ItemStack, boolean)} —— <b>条目自己装着什么
     * 自己说</b>：GT5U 的大型存储单元与各家自定义罐/瓶<b>从不</b>登记进 {@code FluidContainerRegistry}
     * （那条表只收录注册过的桶/罐），也不是流体方块，于是前两级一律解不出 ⇒ 玩家拖一只装了岩浆的
     * 大单元过来，栈被原样退回、界面一句话都不说。本级只取<b>流体名</b>，不取量、不改栈
     * （携液量是条目自带值，见 wiki 的 1.7.10 流体容器三面割裂）。</li>
     * </ol>
     * 三级都空 ⇒ 不收（返回 false 让 NEI 继续拖着，不吃栈）。
     */
    private static final FluidNameProbe[] PROBE_CHAIN = { NekoPocketFluidSlot::filledContainerName,
        NekoPocketFluidSlot::blockFluidName, NekoPocketFluidSlot::containerItemFluidName };

    private NekoPocketPanel owner;
    /** 本槽所在的流体列号 = {@code Kind.FLUID} 的 ghost 槽号 = tank 号（装配期注入，-1 = 未绑定）。 */
    private int slotIndex = FIRST_SLOT_INDEX;
    /** ghost 内部状态（与 {@link NekoFilterSlot#isGhost()} 同形：同一个 widget 的两个状态）。 */
    private boolean ghost;
    /** 声明的流体名（{@code ""} = 无声明）；只描述"要什么"，不描述"条里有什么"。 */
    private String declaredName = "";
    /**
     * ★R83 C2：本列声明的<b>组上限原始值</b>（服务端权威值经面板的 ghost 应用落进来，见 Panel 待办 P-3；
     * {@link PocketConstants#FILTER_CAP_UNSET} = 从没调过 ⇒ 读数与生效都回落到
     * {@link PocketConstants#FLUID_BAR_CAPACITY_ML}，即"一拍把本 tank 填到自然满量"的现行为）。
     */
    private int declaredCap = PocketConstants.FILTER_CAP_UNSET;
    /**
     * ★★<b>R91-⑤</b>：本列的互斥属性（NONE / BIND / MEMORY）。数据源是面板上
     * {@code SYNC_GHOST_FLAGS} 那枚<b>独立</b>同步值的客户端镜像（经双源 accessor 刷进来），
     * ★<b>不是</b>从 {@link #ghost} 派生 —— 空格也要能挂 L/P（R91-b）。
     */
    private int ghostAttr = PocketConstants.GHOST_ATTR_NONE;
    /** ★R91-⑤：正交位 {@code P}（本格内容永不进注入向），可与任一 attr 并存。 */
    private boolean uploadBlocked;
    /** 渲染样本缓存（只在客户端渲染路径上按需解析，服务端与解不出名字时保持 null）。 */
    private String sampleName;
    private FluidStack sample;

    /**
     * 装配期绑定面板会话（双端各一次，与 {@link NekoFilterSlot#bindGhost} 同形）。
     * 登记表长度与格序恒定 ⇒ 不引入数据驱动的 widget 树变化。
     */
    NekoPocketFluidSlot bindBar(NekoPocketPanel panel, int slotIndex) {
        this.owner = panel;
        this.slotIndex = slotIndex;
        return this;
    }

    /** 本槽的列号（同时是 ghost 槽号与 tank 号）。 */
    public int ghostSlotIndex() {
        return slotIndex;
    }

    /** 当前是否处于 ghost（已声明要拉取某种流体）态。 */
    public boolean isGhost() {
        return ghost;
    }

    /** 当前声明的流体名（无声明为 {@code ""}；只读）。 */
    public String declaredFluidName() {
        return declaredName;
    }

    /**
     * 原位切换 ghost 态（{@link NekoPocketPanel#applyGhosts()} 的唯一写入口，双端各一份）。
     * <p>
     * <b>不动 tank、不动 {@code FluidSlotSyncHandler} 的任何开关</b>：流体条是真实条，
     * 灌排语义（需求 2 两排同权）与部分填充必须原样保留。
     */
    NekoPocketFluidSlot setGhost(boolean ghost, String declaredName) {
        final String name = declaredName == null ? "" : declaredName;
        if (this.ghost == ghost && this.declaredName.equals(name)) {
            return this;
        }
        this.ghost = ghost;
        this.declaredName = ghost ? name : "";
        if (!ghost) {
            this.sample = null;
            this.sampleName = null;
            // ★解绑即"没有这条声明了"：上限读数必须复位，否则下一次声明沿用上一列的数字
            this.declaredCap = PocketConstants.FILTER_CAP_UNSET;
        }
        markTooltipDirty();
        return this;
    }

    /**
     * 本列声明当前<b>真正生效</b>的组上限（未调过 = {@code FLUID_BAR_CAPACITY_ML}）。
     * ★R95 S5：显示与生效都改为 <b>min(声明档天花板, tank 容量)</b> 的"自然满量"读数——声明档天花板
     * 已钉 {@code Integer.MAX_VALUE}（一批 ≤ 2.147G、多批灌满），但未升级条只有 16M ⇒ 显示
     * {@code min(INT_MAX, 16M)} = 16M（与旧读数同字面）；升级条 = {@code min(INT_MAX, 16G)} = 16G
     * （long 域，经 {@link #ghostCapL()} 出）。容量按 CAPACITY 位现读（面板探针）。
     */
    NekoPocketFluidSlot setDeclaredCap(int cap) {
        if (this.declaredCap == cap) {
            return this;
        }
        this.declaredCap = cap;
        markTooltipDirty();
        return this;
    }

    /** 本列声明当前真正生效的组上限（<b>long</b>：升级条的自然满量 16G 不进 int）。 */
    public long ghostCapL() {
        final long naturalFull = owner == null ? PocketConstants.FLUID_BAR_CAPACITY_ML : owner.fluidTankCapacityNow();
        final long ceiling = Math.min(PocketConstants.FILTER_CAP_CEILING_FLUID, naturalFull);
        return declaredCap == PocketConstants.FILTER_CAP_UNSET ? ceiling : Math.min(declaredCap, ceiling);
    }

    /**
     * ★R95 S5：本列"未调过"时滚轮起步的<b>有效天花板</b>（int 域——声明档本身是 int）：
     * {@code min(声明档天花板 INT_MAX, tank 容量)}。未升级 = 16M（与显示读数连续，旧手感逐字不变）；
     * 升级 = int 顶（16G 容量对 int 声明档的钳制）⇒ 首滚落在 int 顶 − 一档，属声明档 int 形状的
     * 已知边界（见 {@code PocketConstants#FILTER_CAP_CEILING_FLUID} 的双口径注释）。
     */
    private int effectiveScrollCeiling() {
        final long naturalFull = owner == null ? PocketConstants.FLUID_BAR_CAPACITY_ML : owner.fluidTankCapacityNow();
        return (int) Math.min(PocketConstants.FILTER_CAP_CEILING_FLUID, naturalFull);
    }

    /**
     * ★R91-⑤：面板把服务端属性位表的 attr 刷进本列显示侧（与 {@link #setDeclaredCap} 同一个应用点、
     * 同一条"同值即返回"纪律）。★只影响遮罩在场判据与左上蓝 {@code L}，★不动 tank、不动灌排。
     */
    NekoPocketFluidSlot setGhostAttr(int attr) {
        final int next = PocketConstants.normalizeGhostAttr(attr);
        if (this.ghostAttr == next) {
            return this;
        }
        this.ghostAttr = next;
        markTooltipDirty();
        return this;
    }

    /** ★R91-⑤：正交位 {@code P} 的显示侧写入口（只画左下绿角标）。 */
    NekoPocketFluidSlot setUploadBlocked(boolean blocked) {
        if (this.uploadBlocked == blocked) {
            return this;
        }
        this.uploadBlocked = blocked;
        markTooltipDirty();
        return this;
    }

    /** 本列当前的 attr（服务端算好、经 {@code SYNC_GHOST_FLAGS} 同步来的读数）。 */
    public int ghostAttr() {
        return ghostAttr;
    }

    /** 本列是否挂了 {@code P}（阻拦上传）。 */
    public boolean isUploadBlocked() {
        return uploadBlocked;
    }

    /**
     * 右上角橙色读数的文本（缩写口径三类同源）。
     * <p>
     * ★★R92-③：早退改问 {@link PocketGhostRequest#capReadoutVisible}（{@code declared && attr != MEMORY}）
     * ⇒ alt+左键的记忆格不再唤出橙字；NEI 拖入建档（attr=NONE）与中键绑定照旧显示。
     */
    private String capReadoutText() {
        // ★R95 S5：long 重载——升级条的自然满量 16G 只在 long 梯子里画得对（16G → "16G"）
        return PocketGhostRequest.capReadoutVisible(ghost, ghostAttr) ? PocketGhostRequest.capReadout(ghostCapL()) : "";
    }

    // ------------------------------------------------------------------ NEI 拖入 / 右键解绑

    /**
     * ★拖入入口：<b>只发请求</b>，本地不落档、不清条（R18/R19）。
     * <p>
     * 返回 false 的一切情形（右键、整栏禁用、解不出流体）都<b>不吃栈</b>，NEI 那边继续拖着。
     */
    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        if (owner == null || draggedStack == null) {
            return false;
        }
        // ★★<b>R91-b</b>：只有 P（无 attr）的条 ⇒ 拖拽<b>不响应</b>，且★必须在清
        // {@code draggedStack.stackSize} <b>之前</b>判（"P 不携带内容"这条 javadoc 承诺的空内容钉：
        // 栈不被吃掉 ⇒ NEI 那边继续拖着，玩家看得见"没反应"而不是"东西没了"）。
        if (PocketGhostRequest.dragRouteOf(ghostAttr, uploadBlocked) == PocketGhostRequest.DragRoute.IGNORE) {
            return false;
        }
        final String key = ghostKeyFor(button, areAncestorsEnabled(), firstFluidName(draggedStack, PROBE_CHAIN));
        if (key.isEmpty()) {
            return false;
        }
        draggedStack.stackSize = 0;
        return owner.requestGhost(slotIndex, key);
    }

    /**
     * ★★<b>R91-⑤ 重排后的流体列按键矩阵</b>（★与 {@link NekoFilterSlot} / {@link NekoEssenceGhostCell}
     * 三组格件<b>同一套</b>手势，一条都不许多、也不许少）：
     * <b>中键</b> = 请求绑定 BIND（★早退、不调 {@code super}）／
     * <b>alt+左</b> = 记忆 {@code L} ／ <b>alt+右</b> = 阻拦上传 {@code P}
     * （★★排在既有"ghost + 右键 = 解绑"那条<b>之前</b>，否则声明条上的 alt+右 会先被读成解绑）／
     * 其余（含手持储罐点条的普通左/右键）一律交回 {@code super} ⇒ 需求 2 的"两格同权灌排"与
     * GT5U 那套按键组合、tooltip（{@code modularui2.fluid.click_combined} / {@code _to_fill} /
     * {@code _to_empty}）一个字都不改（L7/R31 口径）。
     * <p>
     * ★旧那条 <b>alt+左 = 对本列现在装的流体直接声明需求</b>（{@code requestBindFromTank}）已随
     * R91-⑤ <b>改派到中键</b>（"格内有物 ⇒ 按该物记录"），且那份内容读数改由<b>服务端</b>读真实 tank
     * （{@code NekoPocketServerHandler#ghostPayloadAt}，★客户端不把载荷抄一遍送上来 = R18/R19）
     * ⇒ 被改派的两个方法<b>整体删除</b>，不留注释尸（R84 的 U2 同一条纪律）。
     */
    @Override
    public Result onMousePressed(int mouseButton) {
        if (mouseButton == MOUSE_BUTTON_MIDDLE) {
            // ★中键必须在这里早退、不调 super：FluidSlot 那一条把按钮号交给 FluidSlotSyncHandler 的
            // 灌排/phantom 分支，而本槽刻意不是 phantom ⇒ button 2 进去就是"没人消费、事件穿透"
            // （取证 §2：MUI2 不按按钮号过滤，防的是手感分叉，与中栏那条创造取物同源）。
            requestGhostFlag(PocketConstants.GHOST_FLAG_BIND);
            return Result.SUCCESS;
        }
        if (mouseButton == 1 && Interactable.hasAltDown()) {
            // ★R91-⑤：alt+右 = P（★★必须排在下面那条 ghost+右键解绑支之前）
            requestGhostFlag(PocketConstants.GHOST_FLAG_UPLOAD_BLOCK);
            return Result.SUCCESS;
        }
        if (ghost && mouseButton == 1 && owner != null) {
            owner.requestGhostClear(PocketFilterConfig.Kind.FLUID, slotIndex);
            return Result.SUCCESS;
        }
        if (mouseButton == 0 && Interactable.hasAltDown()) {
            // ★R91-⑤：alt+左 = 记忆 L（无条件停住：不让原版把 alt+左 读成普通左键去灌那一格流体）
            requestGhostFlag(PocketConstants.GHOST_FLAG_MEMORY);
            return Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
    }

    /**
     * ★R91-⑤：三个手势共用的出口（与 {@code NekoFilterSlot#requestGhostFlag} 同形，零新动作码、零新同步键；
     * ★本地一个字节都不改 —— attr/P 的迁移真值在服务端算好后经 {@code SYNC_GHOST_FLAGS} 推回来）。
     *
     * @return 是否真的发出了请求（未绑定面板 / 槽号非法 / 整栏灰显 ⇒ {@code false}）
     */
    private boolean requestGhostFlag(String gesture) {
        if (owner == null || slotIndex < 0 || !areAncestorsEnabled()) {
            return false;
        }
        return owner.requestGhostFlag(PocketFilterConfig.Kind.FLUID, slotIndex, gesture);
    }

    /**
     * ★R83 C2（判据 4）：<b>alt+滚轮</b>每次调 {@link PocketConstants#FILTER_CAP_STEP_FLUID}
     * （= 单 tank 容量的 1% = 160,000 mB，D-6 的分母口径），<b>alt+ctrl+滚轮</b>把步进抬到 ×10。
     * <p>
     * 先问 {@code super}：库的 {@code FluidSlot.onMouseScroll} 只在 {@code isPhantom()} 为真时消费滚轮
     * （发 {@code SYNC_SCROLL}），本槽刻意不是 phantom（见类 javadoc）⇒ 那里恒返回 false，不与我们抢；
     * 万一将来有人把 handler 改成 phantom，那一条支路仍然优先（不静默改变库语义）。
     */
    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        if (super.onMouseScroll(scrollDirection, amount)) {
            return true;
        }
        if (!ghost || owner == null || slotIndex < 0 || !areAncestorsEnabled() || !Interactable.hasAltDown()) {
            return false;
        }
        // ★R95 S5：滚轮走升级位感知的算式——天花板 = min(声明档天花板, tank 容量)（未升级 16M ⇒
        // 与显示的默认读数连续，旧手感逐字不变）；步进跟容量走（未升级 160K / 升级 160M）。
        final int next = PocketGhostRequest.nextCapEffective(
            PocketFilterConfig.Kind.FLUID,
            declaredCap,
            effectiveScrollCeiling(),
            owner != null && owner.capacityUpgradeActiveNow(),
            scrollDirection,
            Interactable.hasControlDown());
        if (!owner.requestGhost(slotIndex, PocketGhostRequest.capDirective(PocketFilterConfig.Kind.FLUID, next))) {
            return false;
        }
        setDeclaredCap(next);
        return true;
    }

    // ------------------------------------------------------------------ 纯判定与纯编排（回归套件驱动这两段）

    /**
     * 生产探针链的<b>只读</b>快照（顺序 = 命中优先级）。
     * <p>
     * 给回归套件的理由：真探针在纯 JVM 里必然触达 {@code FluidRegistry}（类初始化即失败），
     * 用例因此不能断言"解出了 water"，但<b>可以也应该</b>断言"整条链跑完既不抛也不吞栈" ——
     * 这条防线只有拿真探针才测得到（桩件抛的异常是自己造的）。
     */
    public static FluidNameProbe[] productionProbes() {
        return PROBE_CHAIN.clone();
    }

    /**
     * 按序问探针链，<b>第一个</b>给出非空流体名的即命中；全空 ⇒ {@code ""}（不收）。
     * <p>
     * 单独成函数并由零依赖套件用桩件驱动的理由：{@code FluidContainerRegistry} /
     * {@code FluidRegistry} 在纯 JVM 里连类初始化都过不去（实测 {@code FluidRegistry.<clinit>}
     * {@code NullPointerException} ⇒ {@code ExceptionInInitializerError}），拿真探针跑测试只会得到
     * 一片红或一片假绿（R59b 偏离①同形）。探针<b>顺序</b>与"抛异常/返回 null 都要继续兜下一级、
     * 且绝不吞栈"这两条判据本身是纯逻辑，故在此收口；真探针那两级属实机项。
     * <p>
     * 第三方容器的 {@code getFluidForFilledItem} 实现有可能抛（非注册物品、mod 版本漂移），
     * 因此这里吞 {@code Throwable} 而不是让它冒到点击分发里。
     */
    public static String firstFluidName(ItemStack draggedStack, FluidNameProbe... probes) {
        if (draggedStack == null || probes == null) {
            return "";
        }
        for (FluidNameProbe probe : probes) {
            if (probe == null) {
                continue;
            }
            final String name;
            try {
                name = probe.fluidName(draggedStack);
            } catch (Throwable t) {
                continue;
            }
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return "";
    }

    /**
     * 拖入是否构成一条流体声明：左键 + 该区域可用（灰显一律不收，R31）+ 解出了流体名
     * ⇒ 载荷键 {@code f:<fluidName>}；否则 {@code ""}（= 不收，不吃栈）。
     * <p>
     * 键的生成只走 {@link PocketFilterConfig#fluidKey(String)}（<b>不自造第四种键格式</b>）。
     */
    public static String ghostKeyFor(int button, boolean regionEnabled, String fluidName) {
        if (button != 0 || !regionEnabled || fluidName == null || fluidName.isEmpty()) {
            return "";
        }
        return PocketFilterConfig.fluidKey(fluidName);
    }

    /** 探针 1：注册过的满容器（水桶 / 岩浆桶 / 其它 mod 的罐瓶桶）。 */
    private static String filledContainerName(ItemStack draggedStack) {
        final FluidStack filled = FluidContainerRegistry.getFluidForFilledItem(draggedStack);
        if (filled == null) {
            return null;
        }
        final Fluid fluid = filled.getFluid();
        return fluid == null ? null : fluid.getName();
    }

    /** 探针 2：拖入物对应的<b>流体方块</b>（创造取到的水/岩浆方块、molten metal 一类源方块）。 */
    private static String blockFluidName(ItemStack draggedStack) {
        if (draggedStack.getItem() == null) {
            return null;
        }
        final Block block = Block.getBlockFromItem(draggedStack.getItem());
        if (block == null) {
            return null;
        }
        final Fluid fluid = FluidRegistry.lookupFluidForBlock(block);
        return fluid == null ? null : fluid.getName();
    }

    /**
     * ★R86（缺陷 4 甲）：第三级探针 —— {@link IFluidContainerItem} 自报内容。
     * <p>
     * ⚠ {@code getFluid(ItemStack)} 是<b>单参只读</b>面（Forge 那个接口里带 {@code doX} 布尔的是
     * {@code fill}/{@code drain}），所以这一级天然不会抽走玩家的流体；但也<b>没有</b>"空容器返回 0"
     * 那一类坑可避 —— 容器自己说没有就是没有，判 null 不收。
     * ⚠ 只取名字不取量 —— 声明本身要的就是"这一列要哪一种"，而各家的容量口径是条目自带值。
     */
    private static String containerItemFluidName(ItemStack draggedStack) {
        if (draggedStack == null || !(draggedStack.getItem() instanceof IFluidContainerItem)) {
            return null;
        }
        final FluidStack content = ((IFluidContainerItem) draggedStack.getItem()).getFluid(draggedStack);
        if (content == null || content.amount <= 0 || content.getFluid() == null) {
            return null;
        }
        return content.getFluid()
            .getName();
    }

    // ------------------------------------------------------------------ ghost 态的渲染与 tooltip

    /**
     * 条子空着但已声明流体 ⇒ 把<b>声明样本</b>交给渲染层（真实 tank 一个 mB 都没动，R46a 的
     * "虚化首选钩子"，与 {@link NekoFilterSlot#getItemStackForRendering} 同一形状）。
     * <p>
     * 条子里有真流体时一律按真流体画：声明只是"还要这一种"，不得盖掉实际库存的读数。
     */
    @Override
    public FluidStack getFluidStack() {
        final FluidStack real = super.getFluidStack();
        if (real != null && real.amount > 0) {
            return real;
        }
        return ghostSample();
    }

    /** 声明样本（客户端按需解析；名字在本端注册表里查不到 ⇒ null，只剩遮罩与 tooltip）。 */
    private FluidStack ghostSample() {
        if (!ghost || declaredName.isEmpty()) {
            return null;
        }
        if (sample != null && declaredName.equals(sampleName)) {
            return sample;
        }
        final Fluid fluid = FluidRegistry.getFluid(declaredName);
        if (fluid == null) {
            sample = null;
            sampleName = declaredName;
            return null;
        }
        // 画满整条：这条 fluid 是"要拉满的目标"，不是"条里现有的量"；弱化由整条遮罩承担
        sample = new FluidStack(fluid, Math.max(1, getCapacitySafe()));
        sampleName = declaredName;
        return sample;
    }

    /** {@code getFluidTank().getCapacity()} 的吞异常版本（样本解析不得成为崩溃源）。 */
    private int getCapacitySafe() {
        try {
            return getFluidTank().getCapacity();
        } catch (Throwable t) {
            return 1;
        }
    }

    /**
     * ★R95 S5：<b>原生数量文字压掉</b>——库那份画的是 int 头值（真值超 int 顶后头饱和在
     * {@code Integer.MAX_VALUE}，画出来就是错的），且单位是"桶"口径；真值的读数改由
     * {@link #drawTruthAmountText()} 自绘（{@code PocketGhostRequest#capReadout(long)} 的 K/M/G 梯子，
     * 16,000,000,000 → "16G"）。恒 {@code false} ⇒ 库的 {@code drawOverlay} 一条像素都不画，
     * ghost 样本那半条旧判据也一并由自绘的"有真流体才画"承接。
     */
    @Override
    protected boolean displayAmountText() {
        return false;
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.drawOverlay(context, widgetTheme);
        // ★★R91-⑤：遮罩"在场"判据放宽到 ghost || attr != NONE（pending 态也要虚化），
        // ★但"条子里没有真流体才遮"那一条<b>判据本体一字未动</b>（R84 口径仍在下面三行）。
        final FluidStack real = super.getFluidStack();
        if (PocketGhostRequest.drawsGhostMask(ghost, ghostAttr) && (real == null || real.amount <= 0)) {
            GuiDraw.drawRect(1, 1, getArea().w() - 2, getArea().h() - 2, GHOST_MASK);
        }
        // ★R95 S5：真值读数自绘（原生数量文字已被 displayAmountText 压掉），与右上橙 cap 互不相干
        drawTruthAmountText();
        drawCapReadout();
        // ★R91-⑤：左上蓝 L / 左下绿 P（几何与色单源在 PocketGhostRequest，★空文本不画；
        // ★R100 片 F：绘制体走共享助手 PocketBadgeDrawer，原三份同形 private drawBadge 收拢）
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
     * ★R95 S5：本条 <b>long 真值</b>的自绘读数（右下角，顶替被压掉的库数量文字）。
     * <p>
     * 真值从面板的 {@code LongSyncValue} 镜像现读（每 tank 一根，服务端权威），缩写走
     * {@link PocketGhostRequest#capReadout(long)}（16G 档唯一能画对的那条梯子）；
     * 落点/对齐用库自己的 {@code drawScaledAlignedTextInBox}（BottomRight，与被顶替的那份同几何）。
     * 条里<b>没有真流体</b>（含 ghost 样本态）一条不画——样本是"要拉满的目标"，量是假数。
     */
    private void drawTruthAmountText() {
        if (owner == null) {
            return;
        }
        final FluidStack real = super.getFluidStack();
        final long truth = owner.tankAmountTruth(slotIndex);
        if (real == null || real.amount <= 0 || truth <= 0L) {
            return;
        }
        GuiDraw.drawScaledAlignedTextInBox(
            PocketGhostRequest.capReadout(truth),
            0,
            0,
            getArea().w(),
            getArea().h(),
            com.cleanroommc.modularui.utils.Alignment.BottomRight);
    }

    /**
     * ★R83 C2（判据 7）：声明条右上角的橙色组上限读数。
     * <p>
     * 缩写、色、缩放与右对齐算式三类共用一份，全在 {@link PocketGhostRequest} 里，本方法只负责"画"。
     */
    private void drawCapReadout() {
        final String text = capReadoutText();
        if (text.isEmpty()) {
            return;
        }
        final float x = PocketGhostRequest.capReadoutX(getArea().w(), PocketGhostRequest.capReadoutWidth(text));
        GuiDraw.drawText(
            text,
            x,
            PocketGhostRequest.CAP_READOUT_TOP,
            PocketGhostRequest.CAP_READOUT_SCALE,
            PocketGhostRequest.capReadoutColor(),
            true);
    }

    /**
     * ★R78 D-2：本槽 tooltip 现在承担两行原本常驻在左列的文字 —— "这一格是本列的流体槽"
     * （{@code legend.tank}）与<b>每槽容量读数</b>（{@code fluid.capacity}，数字由
     * {@code PocketConstants} 填，见 {@link NekoPocketPanel#capacityReadoutText()}）。
     * <p>
     * ghost 态再补两条：声明了哪种流体（+ 右键取消）与"流体槽仍可手动灌排"。
     * <p>
     * <b>不</b>复用 {@code gtit.pocket.ghost.locked}：那条写的是"配置格不接受玩家放入<b>或流体输入</b>"，
     * 而流体槽恰恰仍然接受（需求 2 的灌排两格同权，本类一个字都没动它）⇒ 拿它贴到条上就是假话。
     */
    @Override
    protected void addToolTip(RichTooltip tooltip) {
        super.addToolTip(tooltip);
        tooltip.addLine(IKey.lang("gtit.pocket.legend.tank"));
        // ★R80②：随 18 条"列标题边条"撤下来的组号/列号落点（不删信息，R36）
        tooltip.addLine(IKey.dynamic(() -> owner == null ? "" : owner.tankOwnLabelText(slotIndex)));
        tooltip.addLine(IKey.dynamic(() -> owner == null ? "" : owner.capacityReadoutText()));
        if (!ghost) {
            // ★R86（缺陷 4 甲）：没声明的那一条才需要"怎么声明"的读法。此前这里一句都不说，而 NEI 的
            // 拖物面有两处坑（配方窗的流体行根本不可拖、GT 大型单元不在 Forge 容器表里），玩家看到的
            // 全部现象就是"拖上去又弹回来，什么也没发生"。
            tooltip.addLine(IKey.lang("gtit.pocket.ghost.drag_hint"));
            // ★★R92-⑤（D5）：空格子补"三个功能键"三行。身份行本支<b>本来就有</b>（上面 legend.tank +
            // tankOwnLabelText 那两条，★H7 零余量族一字未动）⇒ 本号只加键说明，不重复报身份。
            // ★★审查 B1 修：判据同样不能只写 {@code !ghost}（ghost = 有没有<b>声明</b>）——
            // "槽里真有流体但没声明"的列也是 {@code !ghost} ⇒ 只问 ghost 就把三行加到有内容槽上了。
            // ⇒ 取交"条子里没有真流体"那一条判据（★与上面遮罩那条用的是同一个 {@code super.getFluidStack()}，
            // ★不复用 {@code displayAmountText()}，那条还带着库自己的开关）。
            final FluidStack realForHint = super.getFluidStack();
            if (realForHint == null || realForHint.amount <= 0) {
                PocketCellIdentity.addKeyHints(tooltip);
            }
            return;
        }
        tooltip.addLine(IKey.lang("gtit.pocket.ghost.on", declaredFluidName()));
        tooltip.addLine(IKey.lang("gtit.pocket.ghost.fluid_bar"));
    }
}
