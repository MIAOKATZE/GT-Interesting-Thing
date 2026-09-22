package com.miaokatze.gtit.gui.pocket;

import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.common.items.pocket.PocketAeChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
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
 * 真实槽仍在、槽号不变（★R80 的 220 恒定，与 R78 的 235 一样不受 ghost 影响），但"禁放置禁取出"（需求 4「不允许玩家放置」）；</li>
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

    /** 中键按钮号（MUI2 原样透传 {@code Mouse.getEventButton()}，不做 0/1 白名单）。 */
    private static final int MOUSE_BUTTON_MIDDLE = 2;

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

    /**
     * ★R83 C2：本格声明的<b>组上限原始值</b>（服务端权威值经面板的 ghost 应用落到这里；
     * {@link PocketConstants#FILTER_CAP_UNSET} = 玩家从没调过 ⇒ 显示与生效都回落到"该物品自己的
     * {@code maxStackSize}"，与本轮之前的行为逐字相同）。
     * <p>
     * 只是<b>显示与步进起点</b>，不是第二处真相：拉取量由服务端读自己那份 {@code PocketFilterConfig}
     * （本片在 {@code PocketFilterConfig#cap()}），这里为空/陈旧时最多少画一个数字，不会多拿一件。
     */
    private int declaredCap = PocketConstants.FILTER_CAP_UNSET;

    public NekoFilterSlot() {
        super();
        // ★两份 RichTooltip 各挂一条动态构建器，一条都不许多挂：
        // ① 有真实栈时 ItemSlot.getTooltip() 返回 Widget 那一份（itemTooltip()），库的构造器已经给它
        // 挂了一条（调 buildTooltip(真实栈)）；RichTooltip.tooltipBuilder 是追加而非替换
        // （RichTooltip.java:338-347 会把已有构建器组合起来），旧写法在此再挂一条就让 super.buildTooltip
        // 跑两遍 ⇒ 物品行整段重复。本类因此不再碰①，只把它喂给①的内容收进 buildTooltip。
        // ② 空槽（ghost 格的常态）时 getTooltip() 返回的是 ItemSlot 自己那一份，库从来没给它挂过构建器
        // ⇒ 虚化格一直没有 tooltip，ghost.locked / capacity_note / ghost.on 三条文案是死字。
        // 下面这条 tooltipDynamic 就是补这一份（tooltip() 经 ItemSlot 的覆写解析到②那一份）。
        // ①那一份的 setAutoUpdate 库构造器已经设过（ItemSlot.java:57），这里不再重复。
        tooltipDynamic(tooltip -> {
            if (!isSynced()) {
                return;
            }
            buildTooltip(getSlot().getStack(), tooltip);
        });
        tooltip().setAutoUpdate(true);
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
     * 真实槽<b>不会被本方法清空</b>，本方法只负责属性与渲染 ⇒ 槽号恒定，220 的 Container 口径不受影响（R78）。
     * ★R84 改口径：声明格<b>就是</b>本条需求的抽取落点（用户裁定"物品应该落到物品格"），所以格内出现真实
     * 产物是常态；只有"内容与声明不是同一种"才由 {@code NekoPocketPanel#applyItemGhosts} 搬空。
     *
     * @param ghost  true = 需求格（禁放置、<b>可取出</b>，R84）
     * @param sample 声明样本；{@code null} 视为清空
     */
    public NekoFilterSlot setGhost(boolean ghost, ItemStack sample) {
        this.ghost = ghost;
        this.sample = sample == null ? null : sample.copy();
        if (!ghost) {
            // ★解绑后本格不再是"某条声明"，上限自然也没有归属 ⇒ 必须复位，否则下一次声明会沿用上一次的读数
            this.declaredCap = PocketConstants.FILTER_CAP_UNSET;
        }
        final ModularSlot slot = getSlot();
        if (ghost) {
            // ★R84：旧口径是「禁放置 + 禁取出」（R46d/R41b），但声明格成为落点后"禁取出"等于把补进来的
            // 产物永久关在格里 ⇒ 改为禁放置、可取出。放置这一侧另有 handler 的 isItemValid 兜底（R83 B1）。
            slot.accessibility(false, true);
        } else {
            slot.accessibility(true, true);
        }
        slot.canDragInto(!ghost);
        markBothTooltipsDirty();
        return this;
    }

    /** 只切渲染属性、不动样本（S5 的批量渲染路径）。 */
    public NekoFilterSlot setGhost(boolean ghost) {
        return setGhost(ghost, this.sample);
    }

    /**
     * 两份 RichTooltip 都要置脏：{@code Widget.markTooltipDirty()} 只碰得到 Widget 那一份
     * （{@code Widget.java:342-346} 读的是它自己的私有字段），空槽态用的那一份在 {@code ItemSlot} 里。
     */
    private void markBothTooltipsDirty() {
        markTooltipDirty();
        tooltip().markDirty();
    }

    @Override
    public void buildTooltip(ItemStack stack, RichTooltip tooltip) {
        // ghost 格的真实槽是空的（R46a：样本栈绝不写进真实槽），所以显示与 tooltip 都用样本
        final ItemStack shown = stack == null && ghost ? sample : stack;
        super.buildTooltip(shown, tooltip);
        if (ghost) {
            if (shown != null) {
                tooltip.addLine(IKey.lang("gtit.pocket.ghost.on", shown.getDisplayName()));
            }
            tooltip.addLine(IKey.lang("gtit.pocket.ghost.locked"));
            tooltip.addLine(IKey.lang("gtit.pocket.ghost.capacity_note"));
            // ★R84：读数改双形态——格内已补到实物时报「已补 x / 上限 y」，仍空着时只报上限。
            // （R83 那条"lang 未落地前先显示键名"的过渡口径已收掉：两个键都在两份 lang 里。）
            final int stored = storedSize();
            tooltip.addLine(
                stored > 0
                    ? IKey.lang(
                        "gtit.pocket.cap.readout.stored",
                        PocketGhostRequest.capReadout(stored),
                        PocketGhostRequest.capReadout(displayStopAmount()))
                    : IKey.lang("gtit.pocket.cap.readout", PocketGhostRequest.capReadout(displayStopAmount())));
        }
    }

    @Override
    protected void drawOverlay() {
        super.drawOverlay();
        if (!ghost) {
            return;
        }
        // ★R84（用户原话"获取物品后虚化应该被覆盖，只留下右上角的橙色数字"）：遮罩只在<b>格内没有实物</b>
        // 时画。旧判据是单比特 {@code if (ghost)} ⇒ 产物已经补进来了却仍然整格蒙着，玩家读到"都是虚化"。
        // 实物本体与件数由渲染层自己画（getItemStackForRendering 的样本支本就只在空格生效）。
        if (storedSize() <= 0) {
            GuiDraw.drawRect(1, 1, 16, 16, GHOST_MASK);
        }
        drawCapReadout();
    }

    /**
     * ★R85 A2：角标与 tooltip 的分母必须是<b>停止量</b> = {@code min(组上限, 该物品自己的堆叠上限)}。
     * {@link #ghostCap()} 是"每批额度"（消费侧还要再与落点 room 与 maxStackSize 取小，见
     * {@code PocketAeChannelOps#extractItem}），直接拿它当分母就会出现「已补 64 / 上限 8」这种自相矛盾读数。
     */
    private int displayStopAmount() {
        final int natural = naturalMaxStackSize();
        return natural <= 0 ? ghostCap() : Math.min(ghostCap(), natural);
    }

    /** ★R84：本格已经补到的实际件数（真实槽读数，不是样本）。 */
    private int storedSize() {
        final ItemStack held = getSlot().getStack();
        return held == null ? 0 : held.stackSize;
    }

    /**
     * ★R83 C2（判据 7）：虚像<b>右上角</b>的橙色组上限读数（用户原话"默认绑定虚像右上角会显示组上限
     * （橙色）"）。
     * <p>
     * ★为什么落在这里而不是新加一个 {@code TextWidget} 子件：{@link ItemSlot} 不是容器件，加不了孩子；
     * 而 {@code drawOverlay} 是这条格子的<b>最后一次</b>绘制（库的顺序是 draw → drawSlot → drawOverlay），
     * 于是数字压在遮罩之上而不会被遮罩洗白。库自己的数量文字在 <b>BottomRight</b>
     * （{@code ItemSlot.drawSlotAmountText} → {@code GuiDraw.drawStandardSlotAmountText}），
     * 且 ghost 格的样本恒为 1 件 ⇒ 那一条根本不画 ⇒ 右上与右下互不相干（实测见 R83 C2 记录）。
     * <p>
     * 色、缩放、右对齐算式与缩写一律取自 {@link PocketGhostRequest}（三类共用一份），本方法只负责"画"。
     */
    private void drawCapReadout() {
        final String text = capReadoutText();
        if (text.isEmpty()) {
            return;
        }
        final float x = PocketGhostRequest.capReadoutX(getArea().w(), PocketGhostRequest.capReadoutWidth(text));
        // ★shadow=true：遮罩是 0x80FFFFFF（接近白的浅色），不带阴影的橙字压上去就是糊成一片
        GuiDraw.drawText(
            text,
            x,
            PocketGhostRequest.CAP_READOUT_TOP,
            PocketGhostRequest.CAP_READOUT_SCALE,
            PocketGhostRequest.capReadoutColor(),
            true);
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
     * ★R83 C2：面板把服务端那份声明的<b>原始</b>上限刷进显示侧（与 {@code setGhost} 同一个应用点，
     * 见 Panel 待办 P-3）。传 {@link PocketConstants#FILTER_CAP_UNSET} = 这条从没被调过 ⇒ 读数回落
     * 到该物品自己的堆叠上限（判据 2 的"旧档行为逐字不变"在显示侧的兑现）。
     */
    public NekoFilterSlot setDeclaredCap(int cap) {
        if (this.declaredCap == cap) {
            return this;
        }
        this.declaredCap = cap;
        markBothTooltipsDirty();
        return this;
    }

    /** 本格声明当前<b>真正生效</b>的组上限（未调过 = 该物品的 {@code maxStackSize}）。 */
    public int ghostCap() {
        return PocketFilterConfig.resolveRawCap(PocketFilterConfig.Kind.ITEM, declaredCap, naturalMaxStackSize());
    }

    /** 物品支的天然满量 = 声明样本自己的堆叠上限（★不是常数 64，见 {@link PocketConstants#FILTER_CAP_CEILING_ITEM_SERVER} 的理由）。 */
    private int naturalMaxStackSize() {
        return sample == null ? 0 : sample.getMaxStackSize();
    }

    /**
     * 右上角橙色读数的文本（缩写口径与另两支同源，全在 {@link PocketGhostRequest#capReadout}）。
     * <p>
     * ★R84：由"只报组上限"改为<b>「实际库存/上限」</b>——声明格成为落点后，这一个读数位是用户唯一
     * 能同时看到"补到哪了"和"要到多少为止"的地方（未补到任何件时仍只报上限，与旧读数逐字同形）。
     */
    private String capReadoutText() {
        if (!ghost) {
            return "";
        }
        final String cap = PocketGhostRequest.capReadout(displayStopAmount());
        final int stored = storedSize();
        return stored <= 0 ? cap : PocketGhostRequest.capReadout(stored) + "/" + cap;
    }

    /**
     * NEI 拖拽入口（同名覆写点在库内 phantom 槽件的 {@code :65}，R46b 实测表）。
     * <p>
     * <b>本方法只发请求，不落档、不改本地状态</b>（R18/R19）：载荷键由
     * {@link PocketAeChannelOps#contentKey(ItemStack)} 生成（{@code itemId+meta+base64NBT}，R17，
     * <b>不含任何通道索引</b>），连同槽号一起交给 {@code NekoPocketPanel#requestGhost}；
     * 真正的落档、{@code accessibility(false,true)}（★R84：禁放置、可取出）与虚化广播全在服务端那一份执行。
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
     * ★ghost 格的右键解绑入口（<b>只发请求</b>）+ 中键整理入口 + ★R83 C2 的 alt+左键直接绑定。
     * <p>
     * 按键矩阵（本格）：中键 = 整理（早退，见下）；右键且已声明 = 解绑请求；<b>alt+左键</b> =
     * 把本格<b>已有的物品</b>直接声明为需求（等价于 NEI 把同一个东西拖进来）；其余（含普通左键）
     * 一律交回 {@code super} 走 vanilla 槽点击，真实格行为不变。
     * <p>
     * R18 的字面口径是"清空必须发生在服务端 {@code phantomClick} 的 {@code button == 1} 分支"，
     * 而本仓不许换用 {@code PhantomItemSlot}/{@code PhantomItemSlotSH}（R46d：那是另一个类，
     * 换它 = 换 widget = 破坏 R41b 的双端同树）。因此这里保留同一条安全语义、换同一套载体：
     * 客户端见到右键只往 {@code SYNC_GHOST_REQUEST} 塞一条 {@code CLR|slot|I} 包（中栏的区域字母
     * 是 {@code I}；三个区域的槽索引各从 0 起，裸 {@code CLR|0} 分不清中栏第 0 格与流体条第 0 格），
     * <b>本地一个字节都不清</b>；执行体是
     * {@code NekoPocketPanel#onServerGhostRequest} → {@code PocketGhostRequest#apply} 的
     * {@code GHOST_REQUEST_CLEAR} 分支（服务端）。
     * 左键一律交回 {@code super}（走 vanilla 槽点击，真实格行为不变）。
     */
    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        if (mouseButton == MOUSE_BUTTON_MIDDLE) {
            // ★中键必须在这里早退、且不调 super：super（ItemSlot.onMousePressed）把按钮号原样喂给
            // 原版 GuiContainer.mouseClicked，而原版把 button 2 当 keyBindPickBlock（默认 -98 ⇒ +100=2）
            // 走 clickType 3 的创造取物。整理走自家 C2S 动作码（ACTION_SORT=2 → SYNC_ACTION →
            // 服务端 performSort），因为 MUI2 的服务端窗口点击只认 0/1（ModularContainer.java:249-254）。
            requestSortFromWidget();
            return Interactable.Result.SUCCESS;
        }
        if (ghost && mouseButton == 1 && owner != null && slotIndex >= 0) {
            owner.requestGhostClear(PocketFilterConfig.Kind.ITEM, slotIndex);
            return Interactable.Result.SUCCESS;
        }
        if (mouseButton == 0 && ghost && Interactable.hasAltDown()) {
            // alt+左键在<b>已经是声明格</b>的格子上没有可绑的东西（要绑的东西不在这格），
            // 但也不能让原版把它读成"拿不起来就算了"的正常路径 ⇒ 明确停住，不产生第二种手感
            return Interactable.Result.SUCCESS;
        }
        if (mouseButton == 0 && Interactable.hasAltDown() && requestBindFromContents()) {
            return Interactable.Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
    }

    /**
     * ★R83 C2（判据 3）：alt+左键 = 对<b>这一格里已有的物品</b>直接声明需求（用户原话"还可以采用对已有
     * 物品按下 alt"），走的与 NEI 拖入<b>同一条</b> {@code owner.requestGhost} 请求、同一套四态回执
     * ⇒ 零新动作码、零新同步键，判定与落档仍在服务端（R18/R19）。
     *
     * @return 是否真的发出了请求；{@code false}（灰显、空格、未绑定面板、解不出载荷键）一律交回
     *         {@code super} ⇒ 正常取放行为一个字都不改
     */
    private boolean requestBindFromContents() {
        if (ghost || owner == null || slotIndex < 0 || !areAncestorsEnabled() || !isSynced()) {
            return false;
        }
        final ItemStack stack = getSlot().getStack();
        if (stack == null) {
            return false;
        }
        final String payloadKey = PocketAeChannelOps.contentKey(stack);
        return !payloadKey.isEmpty() && owner.requestGhost(slotIndex, payloadKey);
    }

    /**
     * ★R83 C2（判据 4）：<b>alt+滚轮</b>调本条声明的组上限、<b>alt+ctrl+滚轮</b>把步进抬到 ×10
     * （D-6：×10 作用在步进上，不是第二处通道速率）。
     * <p>
     * 三档步进（物品 1 件 / 流体 160,000 mB / 源质 1 点）与收口都在
     * {@link PocketGhostRequest#nextCap} 这一条纯函数里；本方法只做"这一格是不是声明格 + 修饰键读数"。
     * 不是声明格（或整栏灰显）时把事件原样交回 {@code super} —— {@link ItemSlot} 没覆写
     * {@code onMouseScroll}（默认 {@code false} ⇒ 不消费、不挡下层），所以正常滚动手感不受影响。
     */
    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        if (!ghost || owner == null || slotIndex < 0 || !areAncestorsEnabled() || !Interactable.hasAltDown()) {
            return super.onMouseScroll(scrollDirection, amount);
        }
        final int next = PocketGhostRequest.nextCap(
            PocketFilterConfig.Kind.ITEM,
            declaredCap,
            naturalMaxStackSize(),
            scrollDirection,
            Interactable.hasControlDown());
        if (!owner.requestGhost(slotIndex, PocketGhostRequest.capDirective(PocketFilterConfig.Kind.ITEM, next))) {
            return false;
        }
        // ★本地即时回显：权威值仍由服务端经 blob 落回同一字段（Panel 待办 P-2 + P-3）。不回显的话
        // "滚了数字不动"会被读成功能没生效；回显值与服务端算的是<b>同一条</b> nextCap ⇒ 不会分叉。
        setDeclaredCap(next);
        return true;
    }

    /**
     * ★R 键整理（需求 5 的"中栏本身具有箱子属性"里唯一能自证的那半：手势与算法都是自家的）。
     * <p>
     * 挂点是 widget 级 {@code onKeyPressed}（{@code ModularPanel.java:518-549} 遍历
     * {@code hovering} 调 {@code Interactable.onKeyPressed}），口袋面板不是 {@code ModularPanel}
     * 子类所以拿不到面板级覆写。返回 {@code SUCCESS}（stops）⇒ 同一格上不再往下传。
     * 与 NEI 的"R = 查看配方"是否共存只能实机判。
     */
    @Override
    public Interactable.Result onKeyPressed(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_R) {
            requestSortFromWidget();
            return Interactable.Result.SUCCESS;
        }
        return super.onKeyPressed(typedChar, keyCode);
    }

    /** 两个手势共用同一个入口：只发请求，搬不搬、搬哪几格全在服务端那一份 {@code performSort} 里。 */
    private void requestSortFromWidget() {
        if (owner != null) {
            owner.requestSort();
        }
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
