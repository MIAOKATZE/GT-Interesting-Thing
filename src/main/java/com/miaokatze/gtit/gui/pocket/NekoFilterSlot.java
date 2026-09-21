package com.miaokatze.gtit.gui.pocket;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.common.items.pocket.PocketAeChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 口袋中栏的<b>唯一</b>槽位 widget：真实态与 ghost（虚化配置）态是<b>同一个实例的两个状态</b>。
 * <p>
 * <b>为什么不能改用手机库那个 {@code phantom} 槽件当 ghost 态</b>（R46d）：它是另一个类
 * （{@code extends ItemSlot}，且 {@code handleAsVanillaSlot()} 恒 false），"转 ghost"就必然要
 * <b>替换 widget 实例</b> ⇒ {@code SlotGroupWidget} 字符矩阵换来的"双端同字面量"收益当场丢失，
 * R32（双端 widget 树失序）风险原样回来。计划 §14.5 也单独补了一条：ghost 切换必须<b>原位改属性</b>，
 * 禁止按 ghost 数据重建矩阵或替换 widget。
 * <p>
 * 因此 ghost 只动两件东西（R41b/R46a）：
 * <ol>
 * <li>{@link ModularSlot#accessibility(boolean, boolean)}（{@code ModularSlot.java:179}）——
 * 真实槽仍在、槽号不变（235 恒定，R78），但"禁放置禁取出"（需求 4「不允许玩家放置」）；</li>
 * <li>渲染层：{@link #getItemStackForRendering(ItemStack, boolean)}（{@code ItemSlot.java:230}）
 * 返回<b>样本栈</b>（底层真实槽保持为空），{@link #drawOverlay()} 叠 {@code 0x80FFFFFF} 虚化遮罩。
 * 两者是正交覆写点（R46a），<b>混用</b>会让 ghost 格的显示与真实栈分叉。</li>
 * </ol>
 * <p>
 * <b>本文件不含 ghost 的持久化语义</b>（slice-s3s4 任务包约束 12）：NEI 拖入写 filter 载荷、
 * 右键解绑的服务端判定都归 S5，本类只给出它们要用的状态机与覆写点。
 */
public class NekoFilterSlot extends ItemSlot implements RecipeViewerGhostIngredientSlot<ItemStack> {

    /** 虚化遮罩色（R18 的 alpha 口径，与 {@code ItemSlot.drawSlot} 的拖拽预览同源）。 */
    private static final int GHOST_MASK = 0x80FFFFFF;

    /** ghost 内部状态（{@code false} = 普通真实格）。 */
    private boolean ghost;
    /** ghost 样本栈：只用于渲染与 tooltip，<b>绝不</b>写进真实槽（R46a）。 */

    private ItemStack sample;
    /**
     * 本格所属的面板会话与槽号（装配期由 {@link NekoPocketStorageColumn} 一次性绑定）。
     * <p>
     * 槽号是 ghost 复合键 {@code (Kind.ITEM, slotIndex)} 的后半段——载荷键本身只描述"要什么"，
     * "占的是哪一格"必须由格子自己知道（R38 第 1 条 + R59b 偏离④：裸索引会让中栏第 0 格
     * 与流体条第 0 格互相覆盖，所以 kind 也在键里）。
     */
    private NekoPocketPanel owner;
    private int slotIndex = -1;

    public NekoFilterSlot() {
        super();
        // ItemSlot 构造器已经挂了一个 tooltipBuilder（读真实槽）；这里换成读"渲染态"的版本，
        // 使 ghost 格（真实槽为空）也能出 tooltip。
        itemTooltip().setAutoUpdate(true);
        itemTooltip().tooltipBuilder(tooltip -> {
            if (!isSynced()) {
                return;
            }
            buildTooltip(ghost ? sample : getSlot().getStack(), tooltip);
            if (ghost) {
                tooltip.addLine(IKey.lang("gtit.pocket.ghost.locked"));
                tooltip.addLine(IKey.lang("gtit.pocket.ghost.capacity_note"));
            }
        });
    }

    /** 当前是否处于 ghost（配置格）态。 */
    public boolean isGhost() {
        return ghost;
    }

    /** 当前样本栈（仅 ghost 态有意义；只读，调用方不得改）。 */

    public ItemStack ghostSample() {
        return sample;
    }

    /**
     * 原位切换 ghost 态（S5 的唯一入口，R41b）。
     * <p>
     * 真实槽<b>不会被本方法清空</b>：转 ghost 前 S5 必须先把格内物品挪走（R38 第 2 条：产物不能进自己），
     * 本方法只负责属性与渲染 ⇒ 槽号恒定，235 的 Container 口径不受影响（R78）。
     *
     * @param ghost  true = 虚化配置格（禁放置禁取出）
     * @param sample 声明样本；{@code null} 视为清空
     */
    public NekoFilterSlot setGhost(boolean ghost, ItemStack sample) {
        this.ghost = ghost;
        this.sample = sample == null ? null : sample.copy();
        final ModularSlot slot = getSlot();
        if (ghost) {
            // R46d/R41b 的字面口径：ghost 态就是「禁放置 + 禁取出」，且槽实例与槽号都不变
            slot.accessibility(false, false);
        } else {
            slot.accessibility(true, true);
        }
        slot.canDragInto(!ghost);
        markTooltipDirty();
        return this;
    }

    /** 只切渲染属性、不动样本（S5 的批量渲染路径）。 */
    public NekoFilterSlot setGhost(boolean ghost) {
        return setGhost(ghost, this.sample);
    }

    @Override
    public void buildTooltip(ItemStack stack, RichTooltip tooltip) {
        super.buildTooltip(stack, tooltip);
        if (ghost && stack != null) {
            tooltip.addLine(IKey.lang("gtit.pocket.ghost.on", stack.getDisplayName()));
        }
    }

    @Override
    protected void drawOverlay() {
        super.drawOverlay();
        if (ghost) {
            GuiDraw.drawRect(1, 1, 16, 16, GHOST_MASK);
        }
    }

    /**
     * ghost 态下把<b>样本栈</b>交给渲染层（真实槽仍为空，R46a 的"虚化首选钩子"）。
     * <p>
     * {@code @SideOnly(Side.CLIENT)} 必须跟着基类：本类双端都会被构造，标注只影响"这条实现
     * 在服务器侧是否存在"，方法体本身不触达任何客户端类型之外的东西。
     */
    @Override
    @SideOnly(Side.CLIENT)
    protected ItemStack getItemStackForRendering(ItemStack itemstack, boolean dragging) {
        if (ghost && itemstack == null && sample != null) {
            return sample;
        }
        return super.getItemStackForRendering(itemstack, dragging);
    }

    /**
     * 装配期绑定"哪一格属于哪个会话"（双端各调一次，格序与长度恒定 ⇒ 不引入数据驱动的树变化）。
     */
    public NekoFilterSlot bindGhost(NekoPocketPanel panel, int slotIndex) {
        this.owner = panel;
        this.slotIndex = slotIndex;
        return this;
    }

    /** 本格在中栏的槽号（复合键的后半段）。 */
    public int ghostSlotIndex() {
        return slotIndex;
    }

    /**
     * NEI 拖拽入口（同名覆写点在库内 phantom 槽件的 {@code :65}，R46b 实测表）。
     * <p>
     * <b>本方法只发请求，不落档、不改本地状态</b>（R18/R19）：载荷键由
     * {@link PocketAeChannelOps#contentKey(ItemStack)} 生成（{@code itemId+meta+base64NBT}，R17，
     * <b>不含任何通道索引</b>），连同槽号一起交给 {@code NekoPocketPanel#requestGhost}；
     * 真正的落档、{@code accessibility(false,false)} 与虚化广播全在服务端那一份执行。
     * <p>
     * {@code draggedStack.stackSize = 0} 是库内约定（{@code PhantomItemSlot.java:65-71} 同形）：
     * 返回 true 且数量归零 ⇒ 配方书的虚拟栈被"吃掉"，玩家背包里的原件不受影响。
     */
    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        if (draggedStack == null || owner == null || slotIndex < 0) {
            return false;
        }
        if (!areAncestorsEnabled()) {
            // 整栏被禁用（灰显）时不接受拖入 —— 与 R31 的"灰显即不可交互"口径一致
            return false;
        }
        if (button != 0) {
            // 只有左键拖入 = 设声明；右键的解绑走 onMousePressed 的 button==1 分支（服务端执行）
            return false;
        }
        final String payloadKey = PocketAeChannelOps.contentKey(draggedStack);
        if (payloadKey.isEmpty()) {
            return false;
        }
        draggedStack.stackSize = 0;
        return owner.requestGhost(slotIndex, payloadKey);
    }

    /**
     * ★ghost 格的右键解绑入口（<b>只发请求</b>）。
     * <p>
     * R18 的字面口径是"清空必须发生在服务端 {@code phantomClick} 的 {@code button == 1} 分支"，
     * 而本仓不许换用 {@code PhantomItemSlot}/{@code PhantomItemSlotSH}（R46d：那是另一个类，
     * 换它 = 换 widget = 破坏 R41b 的双端同树）。因此这里保留同一条安全语义、换同一套载体：
     * 客户端见到右键只往 {@code SYNC_GHOST_REQUEST} 塞一条 {@code CLR|slot|I} 包（中栏的区域字母
     * 是 {@code I}；三个区域的槽索引各从 0 起，裸 {@code CLR|0} 分不清中栏第 0 格与流体条第 0 格），
     * <b>本地一个字节都不清</b>；执行体是
     * {@code NekoPocketPanel#onServerGhostRequest} → {@code PocketGhostRequest#apply} 的
     * {@code GHOST_REQUEST_CLEAR} 分支（服务端）。
     * 非 ghost 态或左键一律交回 {@code super}（走 vanilla 槽点击，真实格行为不变）。
     */
    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        if (ghost && mouseButton == 1 && owner != null && slotIndex >= 0) {
            owner.requestGhostClear(PocketFilterConfig.Kind.ITEM, slotIndex);
            return Interactable.Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
    }

    /**
     * ghost 态下不接受玩家输入（防御②，服务端 {@code canPut} 已经是第一道，R38）。
     * <p>
     * 由 {@link NekoPocketStorageColumn} 在装配时挂到 {@code ModularSlot.filter} 上，
     * 这样"格子的可写性"始终由格子自身决定，S5 不必回头改列文件。
     */
    public boolean acceptsPlayerInput(ItemStack stack) {
        return !ghost && stack != null;
    }
}
