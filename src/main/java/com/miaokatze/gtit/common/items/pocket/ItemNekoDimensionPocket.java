package com.miaokatze.gtit.common.items.pocket;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtit.common.items.pocket.channel.PocketChannelDriver;
import com.miaokatze.gtit.common.items.pocket.distill.PocketDistillDriver;
import com.miaokatze.gtit.gui.pocket.NekoPocketPanel;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.register.CreativeTabManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 猫猫次元口袋（需求 1–5 的载体物品）。
 * <p>
 * <b>GUI 承载 = 范式 B「零注册」（R21）</b>：本类实现 {@link IGuiHolder}，打开走
 * {@code GuiFactories.playerInventory().openFromMainHand(player)}，工厂 {@code mui:player_inv}
 * 由 MUI2 自己在 preInit 注册 ⇒ <b>全仓不得出现任何 {@code registerFactory} 调用</b>（不变量 G1）。
 * v1.9 那次集成服崩溃的根因就是自建工厂与 FML 的 {@code int[]} 握手承载不下命名空间，
 * 本路线从结构上让它不可达，因此「零注册」是<b>回退线</b>而不是风格偏好。
 * <p>
 * <b>图标 = 2 布尔位 4 组合（R37）</b>：{@code open}（GUI 打开期）× {@code work}（任一通道活跃期），
 * 选帧由 {@link #pickIcon(boolean, boolean)} 单点承担，注册表与查表共用同一张后缀常量表。
 * 基名不带家族号（R54c）：家族号只活在 {@code tools/artgen_catalog/**} 的 {@code out/} 侧，
 * 落地名（S9 幂等脚本的命名映射）就是下面这一个常量 + 四个后缀。
 * <p>
 * <b>类污染红线</b>：{@link #registerIcons(IIconRegister)} 的参数是客户端类，必须标
 * {@code @SideOnly(Side.CLIENT)}；{@link #getIconIndex(ItemStack)} <b>双端都会被调</b>，
 * 其体内不得触达任何客户端类型（只从 {@link #icons} 取），否则专用服 {@code NoClassDefFoundError}。
 */
public class ItemNekoDimensionPocket extends Item implements IGuiHolder<PlayerInventoryGuiData> {

    /**
     * 物品 ID 单源：{@code unlocalized}、{@code setTextureName} 的默认贴图名、四态图标基名<b>全部</b>
     * 由它派生 ⇒ 全类只有这一处 {@code neko_dimension_pocket} 字面量（判据 5），且 lang 键前缀
     * {@code item.neko_dimension_pocket.*} 与之天然一致（{@code pocket-lang-keys.md} 单源契约）。
     */
    private static final String ID = "neko_dimension_pocket";

    /**
     * 四态后缀表（R48c）：<b>顺序即分派位组合</b>，注册与查表共用这一张表。
     * <p>
     * 分派键 {@code (open ? 1 : 0) | (work ? 2 : 0)} 恰好落到本表下标 0..3；材质若回落到 2 张底态
     * （R37 的 A 路），本表退化为长度 2，{@link #pickIcon} 走同一条代码（"2 张时选 2、4 张时选 4"），
     * 不需要第二处选帧逻辑。
     */
    private static final String[] ICON_SUFFIXES = { "", "_open", "_work", "_work_open" };

    /** 四态图标（下标语义同 {@link #ICON_SUFFIXES}）。{@code IIcon} 是 common 接口，双端可触达。 */
    protected IIcon[] icons = new IIcon[ICON_SUFFIXES.length];

    /** tooltip 行前缀（消费端是 {@code equals(key)} 即 break 的循环，跳号会静默截断）。 */
    private static final String TOOLTIP_PREFIX = "item." + ID + ".tooltip.";

    public ItemNekoDimensionPocket() {
        super();
        setUnlocalizedName(ID);
        setTextureName(GTInterestingThing.MODID + ":" + ID);
        setMaxStackSize(1);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
    }

    // ------------------------------------------------------------------ GUI 入口（R21）

    /**
     * 右键打开三栏面板。
     * <p>
     * 只在服务端发起（{@code openFromMainHand} 内部会 {@code verifyServerSide}）；返回值是<b>原栈本身</b>，
     * 既不消耗也不复制（1.7.10 的 {@code onItemRightClick} 语义）。副手入口按 R21 走
     * {@code openFromPlayerInventory(player, index)}，本片不做（slice-s3-brief §2.1）。
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            GuiFactories.playerInventory()
                .openFromMainHand(player);
        }
        return stack;
    }

    /**
     * 双端构建主面板（R21：本方法是唯一的面板入口，面板本体见 {@code gui/pocket/NekoPocketPanel}）。
     * <p>
     * 槽位与 widget 树由面板内部<b>一份</b> {@code SlotGroupWidget.matrix(...)} 字面量描述（R41a），
     * 本方法不做任何分支。
     */
    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return NekoPocketPanel.build(data, syncManager, settings);
    }

    /**
     * modid 必须传<b>本 mod</b>（{@code gtit}）：默认实现会传 {@code modularui} 的 id 并只打一条 WARN
     * （{@code IGuiHolder.java:26-30}），静默丢失"屏幕归属"判定。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(GTInterestingThing.MODID, mainPanel);
    }

    // ------------------------------------------------------------------ tick 与倒计时（R24）

    /**
     * <b>形参必须逐字是 5 参</b>：本映射（GTNH beta-3）里 {@code Item.onUpdate} 的签名是
     * {@code onUpdate(ItemStack, World, Entity, int, boolean)}（实证：
     * {@code javap -p -cp build/rfg/recompiled_minecraft-1.7.10.jar net.minecraft.item.Item}）。
     * 写成常见的 4 参只会<b>多出一个永不被调用的重载</b>——不报错、不抛异常、不打日志，
     * 倒计时与动画全部静默不跑（仓内唯一可编译先例 {@code common/items/FloatCore.java:131}）。
     * <p>
     * 本方法内<b>只有</b>两件事（pocket-plan §5 第 4 条）：
     * <ol>
     * <li>递减 R24 的两条「剩余 tick」NBT（{@link PocketConstants#UI_WORK_TICKS} 与
     * {@link PocketConstants#UI_BURST_SHOW_TICKS}），归零 {@code removeTag} 自清理，
     * 口径照 {@code ItemGTToolbox.java:200-203}；</li>
     * <li>把通道与蒸馏两个宿主各自的一 tick 交给 driver 的静态入口（S6 / S7 实装）。</li>
     * </ol>
     * 计时一律走 NBT 剩余 tick，<b>不用</b>任何世界绝对时刻或实体存活 tick 计数
     * （不变量 G8 与 R59e：实体跨维度重建时后者不连续 ⇒ 计时会漂；计时源全仓只允许一处）；
     * 10 秒防刷冷却走墙钟（{@link #readDeviceLastBurstAtMs(ItemStack)}，R16 双维）。
     *
     * @param selected 本栈是否为主手持有物。<b>本轮已用可读源码定死其语义</b>（R66b 的待证项）：
     *                 {@code InventoryPlayer.java:347} 传的是 {@code this.currentItem == i}
     *                 ⇒ 只有玩家<b>当前手持的那一格</b>为真，其余 35 格恒 false。
     *                 因此本方法的门控确实等于 R24 的裁定口径「要求主手持有」
     *                 （与 {@code openFromMainHand} 自洽，且天然杜绝塞进箱子后继续跑），
     *                 <b>不摘</b>；代价（换手/收进背包即停摆）由 {@code gtit.pocket.held.note}
     *                 与 {@code tooltip.7} 对玩家显式声明。
     */
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isRemote || !selected || !(entity instanceof EntityPlayer player)) {
            return;
        }
        final NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            // 无档 = 两条计时都不在跑；此处故意不建空 compound（R53c：禁每 tick 写档）
            return;
        }
        tickDown(root, PocketConstants.UI_WORK_TICKS);
        tickDown(root, PocketConstants.UI_BURST_SHOW_TICKS);
        PocketChannelDriver.onItemTick(stack, world, player, slot, selected);
        PocketDistillDriver.onItemTick(stack, world, player, slot, selected);
    }

    /**
     * 剩余 tick 递减：键不在即不建；减到 0 以下<b>{@code removeTag}</b>（不留 0 值，
     * 也让 {@code hasKey} 直接判"未在跑"）。
     */
    private static void tickDown(NBTTagCompound root, String key) {
        if (!root.hasKey(key)) {
            return;
        }
        final int left = root.getInteger(key) - 1;
        if (left > 0) {
            root.setInteger(key, left);
        } else {
            root.removeTag(key);
        }
    }

    // ------------------------------------------------------------------ 状态位与冷却（R37/R16/R24）

    /** {@code work} 位：短效通道剩余 tick 或 burst 显示窗剩余 tick 任一在跑（R37 的工作动画来源）。 */
    public static boolean isWorkActive(ItemStack stack) {
        final NBTTagCompound root = stack == null ? null : stack.getTagCompound();
        if (root == null) {
            return false;
        }
        return root.hasKey(PocketConstants.UI_WORK_TICKS) || root.hasKey(PocketConstants.UI_BURST_SHOW_TICKS);
    }

    /** {@code open} 位：GUI 打开期（写入点见 {@code NekoPocketPanel}，清零点在关屏钩子，R35/R37）。 */
    public static boolean isOpenFlag(ItemStack stack) {
        final NBTTagCompound root = stack == null ? null : stack.getTagCompound();
        return root != null && root.hasKey(PocketConstants.UI_OPEN)
            && root.getByte(PocketConstants.UI_OPEN) != (byte) 0;
    }

    /**
     * 写 {@code open} 位（<b>仅服务端</b>调用；关屏写 0 与打开写 1 都走这里）。
     * <p>
     * ★与 {@link #tickDown} 相反：<b>置 1 时要把缺失的 compound 建出来</b>。关屏落点
     * （{@code NekoPocketPanel#relocateCarrier}）与 {@code open} 位的读回都靠这个标记认人，
     * 全新口袋（从未写过档）若"不建空 compound"就永远标不上 ⇒ 第一次开界面就往背包里放东西、
     * 关屏 ⇒ 找不到承载栈 ⇒ 只 WARN 不落盘 = 静默丢件。R53c 禁的是<b>每 tick</b>建/写档，
     * 一次会话一次的建档不在禁令内。清零时仍不主动建（无档即无需清）。
     */
    public static void setOpenFlag(ItemStack stack, boolean open) {
        if (stack == null) {
            return;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            if (!open) {
                return;
            }
            root = new NBTTagCompound();
            stack.setTagCompound(root);
        }
        if (open) {
            root.setByte(PocketConstants.UI_OPEN, (byte) 1);
        } else {
            root.removeTag(PocketConstants.UI_OPEN);
        }
    }

    /**
     * 由 S6 在激活/每批边界写入两条「剩余 tick」（{@code work} 位与 burst 显示窗各一，R24/R37）。
     *
     * @param workTicks  短效通道剩余 tick；{@code <=0} 表示清掉该键
     * @param burstTicks burst 显示窗剩余 tick；{@code <=0} 表示清掉该键
     */
    public static void startWorkAnimation(ItemStack stack, int workTicks, int burstTicks) {
        writeTicks(stack, PocketConstants.UI_WORK_TICKS, workTicks);
        writeTicks(stack, PocketConstants.UI_BURST_SHOW_TICKS, burstTicks);
    }

    /**
     * 只续 {@code work} 位、<b>不碰</b> burst 显示窗（S6 每批边界用）。
     * <p>
     * 为什么不再开一个"两个都写"的入口：burst 键的寿命由激活那一次决定，
     * 每批边界顺手把它重写一遍会把 5 秒窗口无限延长（= 通道跑多久闪光亮多久）。
     */
    public static void startWorkTicks(ItemStack stack, int workTicks) {
        writeTicks(stack, PocketConstants.UI_WORK_TICKS, workTicks);
    }

    private static void writeTicks(ItemStack stack, String key, int ticks) {
        final NBTTagCompound root = stack == null ? null : stack.getTagCompound();
        if (root == null) {
            return;
        }
        if (ticks > 0) {
            root.setInteger(key, ticks);
        } else {
            root.removeTag(key);
        }
    }

    /** 设备维 burst 冷却起点（口袋 NBT，R16）；缺档即 0（未触发过）。 */
    public static long readDeviceLastBurstAtMs(ItemStack stack) {
        return PocketChannelState.readDeviceLastBurstAtMs(stack == null ? null : stack.getTagCompound());
    }

    /** 写设备维 burst 冷却起点（<b>仅服务端</b>，一次 burst 成功后；墙钟 ms，跨重启有效，R16/R24）。 */
    public static void writeDeviceLastBurstAtMs(ItemStack stack, long nowMs) {
        final NBTTagCompound root = stack == null ? null : stack.getTagCompound();
        if (root != null) {
            PocketChannelState.writeDeviceLastBurstAtMs(root, nowMs);
        }
    }

    // ------------------------------------------------------------------ 图标（R37/R48c/R50d/R54c）

    /**
     * 注册四态帧带（R48c 的命名 = {@code gtit:neko_dimension_pocket} + 后缀表）。
     * <p>
     * <b>侧标必须给</b>：参数类型 {@link IIconRegister} 是客户端类，漏标 {@code @SideOnly(Side.CLIENT)}
     * 是专用服 {@code NoClassDefFoundError} 的经典成因。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        this.icons = new IIcon[ICON_SUFFIXES.length];
        for (int i = 0; i < ICON_SUFFIXES.length; i++) {
            this.icons[i] = register.registerIcon(GTInterestingThing.MODID + ":" + ID + ICON_SUFFIXES[i]);
        }
    }

    @Override
    public IIcon getIconIndex(ItemStack stack) {
        return pickIcon(isOpenFlag(stack), isWorkActive(stack));
    }

    /**
     * 手持/工作台等"渲染通道敏感"入口（1.7.10 的 {@code RenderItem} 走 {@code getIcon(stack, pass)}
     * 而非 {@code getIconIndex}，只覆写后者会有一类界面停在默认帧）；
     * 五参重载 {@code Item.getIcon(stack, pass, player, using, remaining)} 默认回落本方法，无需再覆写。
     */
    @Override
    public IIcon getIcon(ItemStack stack, int pass) {
        return pickIcon(isOpenFlag(stack), isWorkActive(stack));
    }

    /**
     * 唯一的选帧函数（R37 的 2 布尔位 → R48c 的表下标）。
     * <p>
     * 材质不足（家族改动、S9 未落地）时回落 {@code icons[0]}，且 {@code icons[0]} 本身可能为
     * {@code null}——vanilla 对 {@code null} 图标按缺省处理，不 NPE，这也是"贴图可晚于代码落地"
     * 的安全边界（pocket-plan §5 成功判据末条）。
     */
    private IIcon pickIcon(boolean open, boolean work) {
        if (icons.length == 0) {
            return null;
        }
        final int index = (open ? 1 : 0) | (work ? 2 : 0);
        return index < icons.length ? icons[index] : icons[0];
    }

    /**
     * 「工作动画」的闪光叠加（R37 的 A 路兜底）。<b>两个重载都覆写</b>：1.7.10 的
     * {@code hasEffect(ItemStack, int)} 默认实现会回调 {@code hasEffect(ItemStack)}，
     * 但覆写点各渲染路径都会走到（{@code ShimmeringNekoCoin.java:26-29} 覆写的是带 {@code int} 的那个），
     * 只覆一个存在"完全无效"的风险。
     */
    @Override
    public boolean hasEffect(ItemStack stack) {
        return isWorkActive(stack);
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return isWorkActive(stack);
    }

    // ------------------------------------------------------------------ 提示

    /**
     * tooltip 从 0 连续（{@code pocket-lang-keys.md} §1 的 7 行）。
     * <p>
     * 消费端是 {@code equals(key)} 即 break 的循环（先例 {@code common/items/NekoCoin.java:28-34}），
     * <b>跳号会静默截断后面的行</b>，其中 {@code tooltip.5}/{@code tooltip.6} 是 R28（5 秒是节拍不是产量）
     * 与 R39a（两排同权不是上下分流）的显式声明位，丢了就等于埋坑。
     */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean showAdvanced) {
        for (int i = 0;; i++) {
            String key = TOOLTIP_PREFIX + i;
            String line = StatCollector.translateToLocal(key);
            if (line.equals(key)) break;
            tooltip.add(EnumChatFormatting.LIGHT_PURPLE + line);
        }
    }
}
