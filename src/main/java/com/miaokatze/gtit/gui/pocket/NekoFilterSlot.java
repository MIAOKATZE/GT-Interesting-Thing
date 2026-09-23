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

    /**
     * ★★<b>R91-⑤</b>：本格的互斥属性（{@link PocketConstants#GHOST_ATTR_NONE} /
     * {@code _BIND} / {@code _MEMORY}）。由面板经<b>双源 accessor</b>
     * （{@code NekoPocketPanel#ghostAttrAt}）从 {@code SYNC_GHOST_FLAGS} 的镜像刷进来，
     * ★<b>不是</b>客户端自己推断，也<b>不是</b>从 {@code ghost} 那一个比特派生
     * （R91-b：空格的 attr 无法从载荷表表达 ⇒ 必须独立一格状态）。
     */
    private int ghostAttr = PocketConstants.GHOST_ATTR_NONE;
    /** ★R91-⑤：正交位 {@code P}（阻拦上传），可与任一 {@code ghostAttr} 并存。 */
    private boolean uploadBlocked;

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
        applyAccessibility();
        markBothTooltipsDirty();
        return this;
    }

    /**
     * ★★<b>R91-⑤</b>：本格可写性的<b>唯一</b>落点（{@link #setGhost} 与 {@link #setGhostAttr} 都走这里，
     * 否则"载荷换了 / 属性换了"两条路径各设一遍 accessibility 迟早分叉）。
     * <p>
     * 三档真值（★与 {@code PocketInventory#isItemValid} 的 L 执法腿<b>同一条</b>口径，两端不各自判）：
     * <ol>
     * <li>不是声明格 ⇒ 完全正常格（放与取都开）；</li>
     * <li>是声明格且 attr = <b>MEMORY</b> ⇒ 放置<b>开</b>（"本格只能放那一种东西"里的"那一种"总得放得进去），
     * ★<b>放错东西那一击由服务端 {@code isItemValid} 挡</b>（R83-B1：执法在 handler 链，不在 widget 里猜）；
     * 这条同时是 {@code PocketInventory#isGhostItemSlot} 那一批"真实落点"判据的<b>唯一</b>例外，
     * 别的属性（BIND / NONE）仍然禁放置；</li>
     * <li>是声明格且 attr = BIND / NONE ⇒ 禁放置、可取出（★R84 的既有口径，一个字没改：旧
     * 「禁放置 + 禁取出」的教训与"补进来的产物不许永久关在格里"那条理由原样保留）。</li>
     * </ol>
     */
    private void applyAccessibility() {
        final ModularSlot slot = getSlot();
        if (!ghost || ghostAttr == PocketConstants.GHOST_ATTR_MEMORY) {
            slot.accessibility(true, true);
        } else {
            slot.accessibility(false, true);
        }
        slot.canDragInto(!ghost);
    }

    /**
     * ★R91-⑤：面板把服务端那份<b>属性位表</b>的 attr 刷进显示侧（与 {@link #setDeclaredCap} 同一个应用点，
     * 数据源是 {@code SYNC_GHOST_FLAGS} 那枚<b>独立</b>同步值的客户端镜像，★不是从 {@code ghost} 派生）。
     * <p>
     * 一次做齐三件：可写性（{@link #applyAccessibility()}）、遮罩在场判据、左上蓝 {@code L} 角标。
     * ★同值即返回 ⇒ 每拍调不产生额外脏标记（与 cap 那条同一纪律）。
     */
    public NekoFilterSlot setGhostAttr(int attr) {
        final int next = PocketConstants.normalizeGhostAttr(attr);
        if (this.ghostAttr == next) {
            return this;
        }
        this.ghostAttr = next;
        applyAccessibility();
        markBothTooltipsDirty();
        return this;
    }

    /** ★R91-⑤：正交位 {@code P} 的显示侧写入口（只画左下绿角标；★不改可写性、★不改遮罩）。 */
    public NekoFilterSlot setUploadBlocked(boolean blocked) {
        if (this.uploadBlocked == blocked) {
            return this;
        }
        this.uploadBlocked = blocked;
        markBothTooltipsDirty();
        return this;
    }

    /** 本格当前的 attr（服务端算好、经 {@code SYNC_GHOST_FLAGS} 同步来的读数）。 */
    public int ghostAttr() {
        return ghostAttr;
    }

    /** 本格是否挂了 {@code P}（阻拦上传）。 */
    public boolean isUploadBlocked() {
        return uploadBlocked;
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
        // ★★<b>R91-⑤</b>：遮罩"在场"的判据从单比特 ghost 放宽到 <b>ghost || attr != NONE</b>
        // （pending 绑定 / pending 记忆都是"格内还没有东西的需求态"，观感必须与已建档的 BIND 一致）。
        // ★★但"真实内容为空才遮"这一条<b>判据本体一个字没动</b>（仍是 storedSize() <= 0，R84 口径）：
        // attr 只回答"这一格算不算需求态"，★绝不放宽"有实物就不许遮"。
        if (PocketGhostRequest.drawsGhostMask(ghost, ghostAttr) && storedSize() <= 0) {
            GuiDraw.drawRect(1, 1, 16, 16, GHOST_MASK);
        }
        // ★右上橙 cap（只有声明格有，capReadoutText 自己早退）与两个角标各占一角：
        // L = 左上、P = 左下、cap = 右上 ⇒ 同一格同时有 attr 与 P 时也互不覆盖。
        drawCapReadout();
        drawAttrBadout();
    }

    /**
     * ★R91-⑤：本格的两个属性角标（左上蓝 {@code L} = 记忆 / 左下绿 {@code P} = 阻拦上传）。
     * <p>
     * 几何、色与"该不该画"全部取自 {@link PocketGhostRequest}（三组格件共用一份，★不在这里另算一遍）；
     * 本方法只负责"画"。★文本为空 ⇒ 一条像素都不画（⇒ 用例
     * {@code ghost_badge_readouts_are_empty_when_not_applicable} 钉的就是这里的前置判据）。
     * ★{@code shadow=true} 与本类 {@link #drawCapReadout()} 同一条理由：遮罩是接近白的浅色。
     */
    private void drawAttrBadout() {
        drawBadge(
            PocketGhostRequest.memoryBadgeText(ghostAttr),
            PocketGhostRequest.memoryBadgeTop(),
            PocketGhostRequest.memoryBadgeColor());
        drawBadge(
            PocketGhostRequest.uploadBlockBadgeText(uploadBlocked),
            PocketGhostRequest.uploadBlockBadgeTop(getArea().h()),
            PocketGhostRequest.uploadBlockBadgeColor());
    }

    /** 一个角标的绘制体（左对齐 + 内缩；★空文本 = 不画，三组格件同形）。 */
    private void drawBadge(String text, float top, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GuiDraw.drawText(text, PocketGhostRequest.badgeLeftX(), top, PocketGhostRequest.CAP_READOUT_SCALE, color, true);
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
        // ★★<b>R91-b 的 NEI 拖拽分派</b>（唯一判据住在 PocketGhostRequest#dragRouteOf，★三个格件共用它）：
        // 只有 P（无 attr）的格 ⇒ <b>不响应</b>：★在<b>动 draggedStack 之前</b>就返回，
        // 所以既不"吃栈"（数量不清零 ⇒ NEI 那边继续拖着）、也不发请求（用户原话
        // "alt 右键的锁格子是没效果的"）。这条就是本类 javadoc 上"P 格对拖拽无效"的空内容钉。
        final PocketGhostRequest.DragRoute route = PocketGhostRequest.dragRouteOf(ghostAttr, uploadBlocked);
        if (route == PocketGhostRequest.DragRoute.IGNORE) {
            return false;
        }
        final String payloadKey = PocketAeChannelOps.contentKey(draggedStack);
        if (payloadKey.isEmpty()) {
            return false;
        }
        // ★route 的三档（EXPLICIT_BIND / EXPLICIT_MEMORY / IMPLICIT_BIND）在这里<b>都发同一条 SET</b>：
        // 载荷键只有一个去处（PocketFilterConfig 的声明表），差别在<b>attr 已在位表上是什么</b>——
        // attr 由手势（FLG）改、SET 永不顺手改 attr（R91-b："已有显式 attr 时 attr 优先"）。
        // ★IMPLICIT_BIND = 需求 4 的既有建档语义，★一字未回退（无 attr 也无 P 的空格照旧建档）。
        draggedStack.stackSize = 0;
        return owner.requestGhost(slotIndex, payloadKey);
    }

    /**
     * ★★<b>R91-⑤⑧b 重排后的按键矩阵</b>（中栏真实格）——三个手势都只<b>发请求</b>，
     * 迁移真值与落档在服务端（{@code PocketGhostRequest#applyFlag}，R18/R19）：
     * <table border="1">
     * <tr>
     * <th>手势</th>
     * <th>属性</th>
     * <th>说明</th>
     * </tr>
     * <tr>
     * <td><b>中键</b></td>
     * <td>BIND（请求绑定）</td>
     * <td>★从旧的 alt+左键<b>改派</b>而来（R91-⑤）；
     * ★中键原本是"整理"，现已让位，整理仍由 <b>R 键</b>承担（{@code onKeyPressed}，R91-⑧b）</td>
     * </tr>
     * <tr>
     * <td><b>alt+左键</b></td>
     * <td>MEMORY（记忆 {@code L}）</td>
     * <td>纯过滤：本格只能放那一种东西，
     * ★不产生任何 AE 拉取行为</td>
     * </tr>
     * <tr>
     * <td><b>alt+右键</b></td>
     * <td>P（阻拦上传 {@code P}）</td>
     * <td>正交位，可与任一 attr 并存</td>
     * </tr>
     * <tr>
     * <td>右键（已声明）</td>
     * <td>—</td>
     * <td>解绑（★既有支，位置被 alt+右 <b>排在后面</b>）</td>
     * </tr>
     * </table>
     * ★★<b>排序是判据的一部分</b>（取证 G3）：{@code alt+右} 必须排在 {@code ghost && mouseButton == 1}
     * 那条解绑支<b>之前</b>，否则声明格上的 alt+右会先被读成解绑。
     * ★★<b>中键支必须早退且不调 {@code super}</b>（R91-⑧b 的硬约束，理由随迁）：
     * {@code ItemSlot.onMousePressed} 把按钮号原样喂回原版 {@code GuiContainer.mouseClicked}，
     * 而原版把 {@code button 2} 读成 {@code keyBindPickBlock}（{@code GuiContainer.java:326/375-377}）
     * ⇒ {@code ClickType.CREATIVE} = <b>创造模式取物</b>。这条安全语义与"整理走自家动作码"无关，
     * 让位给 BIND 之后<b>仍然</b>成立 ⇒ 由机检 {@code R91-c} 段钉"中键支不含 super"。
     * <p>
     * 其余（普通左键、非 ghost 的右键等）一律交回 {@code super} ⇒ vanilla 槽点击行为一个字不改。
     */
    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        if (mouseButton == MOUSE_BUTTON_MIDDLE) {
            // ★R91-⑤：中键 = 请求绑定（★从 alt+左键改派；旧"中键=整理"已让位给 R 键）
            // ★仍然必须早退且不调 super（原版把 button 2 读成 keyBindPickBlock ⇒ 创造取物，见本方法 javadoc）
            requestGhostFlag(PocketConstants.GHOST_FLAG_BIND);
            return Interactable.Result.SUCCESS;
        }
        if (mouseButton == 1 && Interactable.hasAltDown()) {
            // ★R91-⑤：alt+右 = 阻拦上传 P（★★必须排在下面那条 ghost+右键解绑支之前，G3 的排序判据）
            requestGhostFlag(PocketConstants.GHOST_FLAG_UPLOAD_BLOCK);
            return Interactable.Result.SUCCESS;
        }
        if (ghost && mouseButton == 1 && owner != null && slotIndex >= 0) {
            owner.requestGhostClear(PocketFilterConfig.Kind.ITEM, slotIndex);
            return Interactable.Result.SUCCESS;
        }
        if (mouseButton == 0 && Interactable.hasAltDown()) {
            // ★R91-⑤：alt+左 = 记忆 L（★取代旧的"alt+左 = 把本格存量声明为需求"，那个语义已迁到中键）
            // ★无条件停住：即使这一格当前不可请求（灰显 / 未绑定），也不把 alt+左 交回原版读成普通左键放置
            requestGhostFlag(PocketConstants.GHOST_FLAG_MEMORY);
            return Interactable.Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
    }

    /**
     * ★R91-⑤：三个手势共用的出口（本格 → 面板的 {@code SYNC_GHOST_REQUEST} 通道，零新动作码、零新同步键）。
     * <p>
     * ★本地一个字节都不改：attr / P 的迁移真值由服务端算好后经 {@code SYNC_GHOST_FLAGS} 推回来
     * （客户端直改就是第二处真相，且关屏不落地）。
     *
     * @return 是否真的发出了请求（未绑定面板 / 槽号非法 / 整栏灰显 ⇒ false）
     */
    private boolean requestGhostFlag(String gesture) {
        if (owner == null || slotIndex < 0 || !areAncestorsEnabled() || !isSynced()) {
            return false;
        }
        return owner.requestGhostFlag(PocketFilterConfig.Kind.ITEM, slotIndex, gesture);
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
