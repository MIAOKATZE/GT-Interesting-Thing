package com.miaokatze.gtit.gui.pocket;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;

/**
 * 流体列的<b>流体槽本体</b>（R75①：面板左侧那 6 列之一，每列纵向 = 输入格 / <b>本槽</b> / 输出格）：
 * 真实流体槽 + 一个可被 NEI 拖入的 ghost 声明位（需求 4 的流体入口）。
 * <p>
 * <b>★不调 {@code super.handleDragAndDrop}、也★不把本槽 handler 设成 phantom</b>
 * （R70 实测口径）：库内那一条的开头是 {@code if (!syncHandler.isPhantom()) return false} ——
 * 真实槽上它恒拒；而把 handler 改成 phantom 会让<b>服务端那一支把流体写进真实槽</b>
 * （{@code FluidSlotSyncHandler#readOnServer} 的 {@code id==4} 分支直接 {@code tryClickPhantom}），
 * 需求 2 的"从手上/仓里灌排流体、两格同权"就没了。⇒ 唯一正确做法是<b>子类在 phantom 门之前拦下</b>，
 * 只发本仓那一条 {@code SYNC_GHOST_REQUEST}（与 {@link NekoFilterSlot} 同形，R18/R19 不破）。
 * <p>
 * <b>ghost 只原位改属性</b>（R41b）：本 widget 实例从装配到关屏不换，声明态只影响
 * 渲染样本、遮罩与 tooltip；18×18 几何与 {@code alwaysShowFull(false)} 的部分填充口径
 * 都由 {@link NekoPocketLeftColumn} 原样保留 ⇒ 双端同树（R32）不受影响。
 * <p>
 * <b>★槽号 = 列号 = tank 号</b>（R75：流体侧由 1 根竖条变 {@code PocketConstants.FLUID_COLUMN_COUNT}
 * 个独立 tank，{@code GHOST_FLUID_SLOT_LIMIT} 同值跟着变 6）：不再有"恒为 0"的特例，
 * 列号由 {@link #bindBar(NekoPocketPanel, int)} 在装配期注入 ⇒
 * "这一列要拉哪种流体"与"拉进哪个 tank"共用同一个数，没有第二份映射。
 * {@code Kind.FLUID} 的索引空间与中栏 0…149、源质 0…47 各自独立，这正是 CLR 必须带区域字母的理由。
 */
public class NekoPocketFluidSlot extends FluidSlot {

    /** 旧的"唯一槽号"常量（R75 后流体侧有 6 个索引位，不再存在恒值）。 */
    public static final int FIRST_SLOT_INDEX = 0;

    /** 虚化遮罩色（与 {@link NekoFilterSlot} 同一 alpha 口径，R18）。 */
    private static final int GHOST_MASK = 0x80FFFFFF;

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
     * </ol>
     * 两级都空 ⇒ 不收（返回 false 让 NEI 继续拖着，不吃栈）。
     */
    private static final FluidNameProbe[] PROBE_CHAIN = { NekoPocketFluidSlot::filledContainerName,
        NekoPocketFluidSlot::blockFluidName };

    private NekoPocketPanel owner;
    /** 本槽所在的流体列号 = {@code Kind.FLUID} 的 ghost 槽号 = tank 号（装配期注入，-1 = 未绑定）。 */
    private int slotIndex = FIRST_SLOT_INDEX;
    /** ghost 内部状态（与 {@link NekoFilterSlot#isGhost()} 同形：同一个 widget 的两个状态）。 */
    private boolean ghost;
    /** 声明的流体名（{@code ""} = 无声明）；只描述"要什么"，不描述"条里有什么"。 */
    private String declaredName = "";
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
        }
        markTooltipDirty();
        return this;
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
        final String key = ghostKeyFor(button, areAncestorsEnabled(), firstFluidName(draggedStack, PROBE_CHAIN));
        if (key.isEmpty()) {
            return false;
        }
        draggedStack.stackSize = 0;
        return owner.requestGhost(slotIndex, key);
    }

    /**
     * ghost 态下的右键 = 解绑（★只发本列自己的 {@code CLR|<列号>|F}，判定与执行在服务端）。
     * <p>
     * 非 ghost 态与左键一律交回 {@code super} ⇒ 手持储罐点条的按键组合与 tooltip 与 GT5U 逐字一致
     * （{@code modularui2.fluid.click_combined} / {@code _to_fill} / {@code _to_empty}，L7/R31 口径）。
     */
    @Override
    public Result onMousePressed(int mouseButton) {
        if (ghost && mouseButton == 1 && owner != null) {
            owner.requestGhostClear(PocketFilterConfig.Kind.FLUID, slotIndex);
            return Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
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

    /** 声明态下条子空着时不画数字（那个 mB 数是样本的假数，画出来就是误导）。 */
    @Override
    protected boolean displayAmountText() {
        final FluidStack real = super.getFluidStack();
        return super.displayAmountText() && (real != null && real.amount > 0);
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        super.drawOverlay(context, widgetTheme);
        if (ghost) {
            GuiDraw.drawRect(1, 1, getArea().w() - 2, getArea().h() - 2, GHOST_MASK);
        }
    }

    /**
     * ghost 态补两条 tooltip：声明了哪种流体（+ 右键取消）与"流体条仍可手动灌排"。
     * <p>
     * <b>不</b>复用 {@code gtit.pocket.ghost.locked}：那条写的是"配置格不接受玩家放入<b>或流体输入</b>"，
     * 而流体条恰恰仍然接受（需求 2 的灌排两排同权，本类一个字都没动它）⇒ 拿它贴到条上就是假话。
     */
    @Override
    protected void addToolTip(RichTooltip tooltip) {
        super.addToolTip(tooltip);
        if (!ghost) {
            return;
        }
        tooltip.addLine(IKey.lang("gtit.pocket.ghost.on", declaredFluidName()));
        tooltip.addLine(IKey.lang("gtit.pocket.ghost.fluid_bar"));
    }
}
