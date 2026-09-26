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
import com.miaokatze.gtit.common.items.pocket.mage.PocketCoinChargeDriver;
import com.miaokatze.gtit.common.items.pocket.mage.PocketEssenceTransmuteDriver;
import com.miaokatze.gtit.common.items.pocket.mage.PocketWandChargeDriver;
import com.miaokatze.gtit.common.items.pocket.magnet.PocketMagnetDriver;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;
import com.miaokatze.gtit.gui.pocket.NekoPocketPanel;
import com.miaokatze.gtit.gui.pocket.NekoPocketStorageColumn;
import com.miaokatze.gtit.gui.pocket.PocketSlots;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.register.CreativeTabManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.metatileentity.BaseMetaTileEntity;

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
     * <p>
     * ★<b>失败必须可观测</b>：MUI2 的 {@code PlayerInventoryGuiFactory#getGuiHolder} 读的是
     * {@code data.getUsedItemStack()}，拿不到时走 {@code Objects.requireNonNull} 抛出；
     * 而在服务端 {@code openGui} 里抛出的异常会被 FML 咽进日志 ⇒ 玩家侧只看到"右键没反应"。
     * 因此这里捕获并一次性 WARN，把链路上的三个可分辨点（是否进来了 / 手持槽号 / 异常原文）写清楚。
     * <p>
     * ★<b>R91-⑧：潜行右击永不开屏</b>。本方法是全仓<b>唯一</b>的开屏入口，也是<b>唯一</b>有效的拦截点——
     * 潜行抽液那一次右击在客户端会产出<b>两条</b> C08（带坐标那条打到 {@link #onItemUseFirst} 完成抽液，
     * 补发的 side=255 那条打到本方法），而第一条<b>必须</b>是客户端放行才发得出去，
     * 故返回码无从取消第二条（机制详见 {@link #onItemUseFirst} 的 javadoc）。
     * 闸放在双端入口最前面（不只服务端）：{@code isSneaking()} 在服务端同样成立——
     * 功能 N 的抽液判定本身就依赖它，实机已证该标记在这条包路径上可信。
     * 本分支<b>不产生任何聊天输出</b>（R88「口袋域聊天零输出」裁定，门禁 {@code R88①}）。
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            logOnce("潜行右击 ⇒ 开屏入口放行（功能 N 的抽液手势专用，不开面板）");
            return stack;
        }
        if (!world.isRemote) {
            try {
                GuiFactories.playerInventory()
                    .openFromMainHand(player);
                if (!openLogged) {
                    openLogged = true;
                    GTInterestingThing.LOG
                        .info("[pocket] 右键开道已走到服务端 openFromMainHand（槽号 {}）", player.inventory.currentItem);
                }
            } catch (Throwable t) {
                if (!openLogged) {
                    openLogged = true;
                    GTInterestingThing.LOG.warn(
                        "[pocket] 右键开面板失败（槽号 " + player.inventory.currentItem
                            + "，物品 "
                            + Item.getIdFromItem(stack.getItem())
                            + "）：面板在服务端构建阶段抛错 ⇒ 界面无反应",
                        t);
                }
            }
        }
        return stack;
    }

    /** 一次性日志闩：只报第一次点击的结果，成功与失败各占一次判读面。 */
    private boolean openLogged = false;

    // ------------------------------------------------------------------ 世界交互入口（R90 S6 新功能 N）

    /**
     * ★新功能 N（AUQ-④=<b>b 潜行手势</b>）：<b>潜行</b>手持口袋右击 GT5U 机器 ⇒ 抽机器流体进流体槽。
     * <p>
     * 本方法是整个功能在 1.7.10 交互序里的<b>唯一正确拦截点</b>（反编译树 {@code ItemInWorldManager
     * #activateBlockOrUseItem}:393 最先调它，true 即终止后续、方块激活(:409)与 onItemUse(:422) 不再跑）。
     * 三条铁律（计划 §3-S6 + wiki 教训 {@code server-interaction-order-onitemusefirst}）：
     * <ol>
     * <li><b>客户端必须 {@code return false}</b>：拦截类物品客户端返回 true 则 C08 不发、服务端收不到交互；</li>
     * <li><b>非潜行一律 {@code return false} 永不拦截</b>：机器 GUI 原语义（开门/开盖 GUI）完整可达
     * （GT 机器对非潜行右击自己消费交互，写在 {@code onItemUse} 里的功能永不可达——那条老路不走）；</li>
     * <li>目标不是 GT 机器（非 {@code BaseMetaTileEntity}）⇒ {@code return false} 完整放行原方块交互。</li>
     * </ol>
     * 实收 &gt;0 才 {@code return true} 拦截；一切「没搬动」的结局都 {@code return false}（聊天判因回执
     * 由 {@link PocketWorldFluidTap} 发，键前缀 {@code gtit.pocket.world.}）。
     * ★<b>R91-c 纠正一条被证伪的旧表述</b>（原文写"服务端 true 后同一交互不会再触发 {@link #onItemRightClick}，
     * 后者只由空气点击触发"，与实机现象相反）：铁律 1 要求<b>客户端恒返 {@code false}</b> ⇒
     * 原版据此判定"这一击没被消费"，<b>必然</b>再补发一枚 side=255 的 C08 ⇒ 服务端 {@code tryUseItem}
     * 仍会打到 {@link #onItemRightClick} ⇒ "抽液成功"与"面板也开了"两件事同时发生。
     * ⇒ <b>本方法的三个 {@code return false} 一个都不许改成 true</b>（客户端放行 / 非潜行放行 / 非机器放行，
     * 改哪个都直接砍掉功能 N），拦截点只能落在 {@link #onItemRightClick} 的潜行闸上。
     * 持久化按 F1 双分支（活会话走会话模型 / 无会话一次性 NBT 读改写），见适配器类 javadoc。
     */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote || !player.isSneaking()) {
            return false;
        }
        if (!(world.getTileEntity(x, y, z) instanceof BaseMetaTileEntity tile)) {
            return false;
        }
        return PocketWorldFluidTap.tap(player, stack, tile);
    }

    /** ★定位"右键无反应"用的诊断计数（双端各走一遍 buildUI，故按总量限流）。 */
    private static int diagLogged = 0;

    private static void logOnce(String what) {
        if (diagLogged < 8) {
            diagLogged++;
            GTInterestingThing.LOG.info("[pocket][diag] " + what);
        }
    }

    /**
     * 双端构建主面板（R21：本方法是唯一的面板入口，面板本体见 {@code gui/pocket/NekoPocketPanel}）。
     * <p>
     * 槽位与 widget 树由面板内部<b>一份</b> {@code SlotGroupWidget.matrix(...)} 字面量描述（R41a），
     * 本方法不做任何分支。
     */
    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager syncManager, UISettings settings) {
        logOnce(
            "buildUI 到达 " + FMLCommonHandler.instance()
                .getEffectiveSide() + "，手持槽栈 " + (data.getUsedItemStack() == null ? "null" : "非 null"));
        return NekoPocketPanel.build(data, syncManager, settings);
    }

    /**
     * modid 必须传<b>本 mod</b>（{@code gtit}）：默认实现会传 {@code modularui} 的 id 并只打一条 WARN
     * （{@code IGuiHolder.java:26-30}），静默丢失"屏幕归属"判定。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel mainPanel) {
        logOnce("createScreen 到达（客户端已收到 OpenGui 并建好面板）");
        return new ModularScreen(GTInterestingThing.MODID, mainPanel);
    }

    // ------------------------------------------------------------------ tick 与倒计时（R24）

    // [GT-compat] onUpdate 形参元数兼容层（beta1/beta2/beta3/RC1）：GT5U 正式版发布时按当时映射复核形参元数并移除本口径
    /**
     * <b>形参必须逐字是 5 参</b>：RC-1 实证（{@code compileJava} 的 {@code @Override} 校验 + {@code runPocketTest} 套件）里
     * {@code Item.onUpdate} 的签名是 {@code onUpdate(ItemStack, World, Entity, int, boolean)}，无 4 参重载；其余三代
     * （b1 5.09.52.594 / b2 5.09.54.20 / b3 5.09.54.133）未实测，按 MC 1.7.10 + Forge 10.13.4.1614 四代同源<b>推断</b>为同一形态，
     * 故此处不得改用任何"仅高版本"形态——这是四代兼容判据下唯一安全的写法。
     * 实证口径已随换基线更新：旧文案把签名钉在「本映射（GTNH beta-3）」并引
     * {@code javap -p -cp build/rfg/recompiled_minecraft-1.7.10.jar net.minecraft.item.Item}，而该 recompiled jar 在 RC-1
     * 下会随
     * rc1 的 forge/mcp 与 {@code enableGenericInjection = true}（gradle.properties）重新生成 ⇒ 原实证对象不再等价；
     * 新实证 = 本轮 {@code compileJava}（RC-1 一格，见 plan/gtit-rc1-baseline-20260925/plan-rc1-baseline-apply.md §5-V2），
     * beta-3 腿由换基线前的 v1.8.38（commit 2636205）承担，b1/b2 依赖集未定义、不实测也不对外许诺（不冒充四代实测）。GT5U 正式版发布时复核本签名并届时删除本兼容口径。
     * 判据出处：plan/gtit-rc1-baseline-20260925/evidence/r2-compat-layer-inventory.md 与同目录
     * evidence/r5-survival-legs.md。
     * 写成常见的 4 参只会<b>多出一个永不被调用的重载</b>——不报错、不抛异常、不打日志，
     * 倒计时与动画全部静默不跑（仓内唯一可编译先例 {@code common/items/FloatCore.java:131}）。
     * <p>
     * 本方法内<b>只有</b>三件事（pocket-plan §5 第 4 条 + ★R95 磁力）：
     * <ol>
     * <li>递减 R24 的两条「剩余 tick」NBT（{@link PocketConstants#UI_WORK_TICKS} 与
     * {@link PocketConstants#UI_BURST_SHOW_TICKS}），归零 {@code removeTag} 自清理，
     * 口径照 {@code ItemGTToolbox.java:200-203}；</li>
     * <li>把通道与蒸馏两个宿主各自的一 tick 交给 driver 的静态入口（S6 / S7 实装）；</li>
     * <li>★R95：磁力驱动 {@link PocketMagnetDriver#onItemTick}（自带开关早退与节拍闸；★R96 S6 起这一拍
     * 内部含"每拍跨拍收口 + 每 5 tick 位移"两段，<b>不</b>新增第二个宿主入口）。</li>
     * </ol>
     * 计时一律走 NBT 剩余 tick，<b>不用</b>任何世界绝对时刻或实体存活 tick 计数
     * （不变量 G8 与 R59e：实体跨维度重建时后者不连续 ⇒ 计时会漂；计时源全仓只允许一处）；
     * 10 秒防刷冷却走墙钟（{@link #readDeviceLastBurstAtMs(ItemStack)}，R16 双维）。
     *
     * @param selected 本栈是否为主手持有物（{@code InventoryPlayer.java:347} 传的是
     *                 {@code this.currentItem == i} ⇒ 只有当前手持的那一格为真）。
     *                 ★<b>R95 门控放宽：本方法不再用 {@code !selected} 做早退</b>——背包 36 格
     *                 任意位都推进（通道/蒸馏/磁力；vanilla 证据 {@code InventoryPlayer.java:343-348}：
     *                 {@code decrementAnimations} 对 mainInventory 全部 36 格调 {@code updateAnimation}），
     *                 箱子/饰品栏/盔甲位 vanilla 不 tick 物品故停（盔甲位走 {@code onArmorTick}，
     *                 {@code InventoryPlayer.java:351-357}）。R66b 时代"selected 即门控"的旧口径
     *                 （与 {@code openFromMainHand} 自洽）随之作废；形参保留只为 5 参签名与
     *                 两个 driver 的既有透传。
     *                 ★笔误修正（R95）：旧句把「换手/收进背包即停摆」的声明引到
     *                 {@code gtit.pocket.held.note} <b>与</b> {@code tooltip.7} 两处——后者说的是
     *                 「内容随物品丢」（lang {@code item.neko_dimension_pocket.tooltip.7}），与主手
     *                 口径无关；主手口径的玩家声明只在 {@code gtit.pocket.held.note} 一处
     *                 （该文案自 R95 起口径过期，lang 翻新属 S2b）。
     */
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        // ★R95 门控放宽：只保留服务端与玩家检查（vanilla tick 链证据见 @param selected）
        if (world.isRemote || !(entity instanceof EntityPlayer player)) {
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
        PocketMagnetDriver.onItemTick(stack, world, player);
        // ★R96 S9a 魔法使三条被动（只加挂载行，本方法的门控与结构一字未动；抽取 runPassives 归 S11）。
        // 三条都在自己类内做 MAGE 组合谓词早退 + NBT 剩余 tick 节拍 ⇒ 关着开关时这里只花三次位图读。
        PocketWandChargeDriver.onItemTick(stack, world, player);
        PocketCoinChargeDriver.onItemTick(stack, world, player);
        PocketEssenceTransmuteDriver.onItemTick(stack, world, player);
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

    /**
     * {@code work} 位：短效通道剩余 tick 或 burst 显示窗剩余 tick 任一在跑（R37 的工作动画来源），
     * <b>或</b>通道持续化<b>当前生效</b>（R95 立的常亮腿：driver 在批边界把剩余批次回满、通道永不停，
     * 帧带跟着常亮；★R96 起"生效"按组合谓词读，见下面改口段）。只读腿：{@code isActive} 不建档
     * （R53c 读路径纪律），无档口袋照旧走倒计时两键。
     * <p>
     * ★<b>R96 S1 改口（P-1 正交 enabled 位图）</b>：常亮腿从"位图在场即恒真"改成
     * {@code PocketUpgradeSwitches.isActive(CHANNEL_PERSIST)} —— 玩家把持续化<b>关掉</b>之后帧带与光泽
     * 必须跟着停，否则"关了开关画面还亮着"就是开关对该路径根本无效（R57/C3 同族的静默失效）。
     * ★不主动 stop 已在跑的通道：关开关只让 driver 的批边界回满不再发生 ⇒ 自然衰减到 0，
     * 复用既有 {@code finishBatch} 的回收支，不建第二台状态机。
     */
    public static boolean isWorkActive(ItemStack stack) {
        final NBTTagCompound root = stack == null ? null : stack.getTagCompound();
        if (root == null) {
            return false;
        }
        if (PocketUpgradeSwitches.isActive(stack, PocketUpgradeType.CHANNEL_PERSIST)) {
            return true;
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
     * <p>
     * ★★<b>R96 S1 定案（附魔特效，不留待裁）</b>：判据 = {@code isWorkActive(stack) ||
     * PocketUpgradeSwitches.anyActive(stack)} —— <b>并集，只加不减</b>：R37 那条"正在作业就亮"的信号
     * 一个字不删（删它 = 改掉一条已被用例与玩家认知双重固定的既有行为），新增的是"有插件开着就亮"。
     * <p>
     * 取并集而不是"逐型光泽"的理由是<b>vanilla 的物理上限</b>：{@code Item.hasEffect} 是单个布尔，
     * 一件物品只有一个光泽位 ⇒ 五个开关共用它时，光泽只能表达
     * 「<b>有插件开着 ∨ 正在作业</b>」这一句话，<b>分辨不出是哪一型在亮</b>；
     * ★逐型状态的唯一读数是<b>配置面板</b>（R96 S2 的次级面板 + 升级格的关闭态视觉）。
     * 需求字面要的是"开时有附魔特效"，这条并集正好是它在单个布尔位上的最大可实现近似。
     * <p>
     * ★刻意<b>不</b>扩帧带表（{@code ICON_SUFFIXES} 本轮保持 4 档）：扩到 8 档要同步改
     * {@code gen_pocket.py} 的"四态 × 8 帧"自检判据与落地脚本，属材质轮工作，与本轮风险预算不成比例；
     * {@code pickIcon} 的下标回落（{@code index < icons.length ? … : icons[0]}，R48c）原样保留 ⇒
     * 将来扩表不改任何代码路径。
     */
    @Override
    public boolean hasEffect(ItemStack stack) {
        return isWorkActive(stack) || PocketUpgradeSwitches.anyActive(stack);
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return isWorkActive(stack) || PocketUpgradeSwitches.anyActive(stack);
    }

    // ------------------------------------------------------------------ 提示

    /**
     * tooltip 从 0 连续（{@code pocket-lang-keys.md} §1 的 7 行，R75 后为 10 行，R78 仍是 10 行；
     * ★新增的规格读数一律走已有的 {@code %N$d} 槽位扩到 {@code %12$d}，不加新行号 ⇒ 不断号）。
     * 连号行之外还有<b>一条追加行</b>（★R95 蒸馏加速的双口径行，见
     * {@link #appendDistillFastLine}，追加在连号循环之后、不在连号中间插行）。
     * <p>
     * 消费端是 {@code equals(key)} 即 break 的循环（先例 {@code common/items/NekoCoin.java:28-34}），
     * <b>跳号会静默截断后面的行</b>，其中 {@code tooltip.5}/{@code tooltip.6} 是 R28（5 秒是节拍不是产量）
     * 与 R39a（两排同权不是上下分流）的显式声明位，丢了就等于埋坑。
     * <p>
     * ★<b>行里不写规格数字</b>（R75 的 lang 契约第 5 条）：格数、行列、流体<b>组数与 tank 总数</b>、
     * 单槽容量与<b>总容量</b>（R78②）、源质盘格数（R78②）、面板内背包格数（R78①）、蒸馏节拍
     * 全部由 {@link #tooltipArgs()} 从常量填进 {@code %1$d…%12$d} 的<b>带位置下标</b>的占位。
     * 用下标而不是裸 {@code %d} 的理由：所有行走同一个实参数组，裸 {@code %d} 会一律取第 1 个实参
     * ⇒ "每槽 16,000,000" 会被填成"135"，而且不报错。
     */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean showAdvanced) {
        final Object[] args = tooltipArgs(stack);
        for (int i = 0;; i++) {
            final String key = TOOLTIP_PREFIX + i;
            final String template = StatCollector.translateToLocal(key);
            if (template.equals(key)) break;
            // 只在真的含占位时才格式化一次（口径同 NekoPocketPanel#receiptText）
            tooltip.add(
                EnumChatFormatting.LIGHT_PURPLE
                    + (template.indexOf('%') < 0 ? template : String.format(template, args)));
        }
        appendDistillFastLine(tooltip, args);
    }

    /**
     * ★R95 蒸馏加速的「双口径」追加行：消费 {@code PocketDistillDriver#TOOLTIP_DISTILL_FAST_KEY}
     * （{@code gtit.pocket.tooltip.distill_fast}）——动态文案由 lang 承担（S2b 落键），本侧只送实参；
     * 可用的占位与连号行同一份实参数组（基档秒数 {@code %6$d}、加速档秒数 {@code %12$d}）。
     * 追加在连号循环<b>之后</b>而不是往 {@code tooltip.N} 族里插行：连号族的消费端是
     * {@code equals(key)} 即 break 的循环，插行 = 给所有人断号。键字面量住在
     * {@code PocketDistillDriver}（R88① 门禁对白名单文件做 {@code "gtit.pocket.*"} 字面量的
     * world 前缀粗粒度检查，tooltip 键放本文件会被误判成越权聊天键）。键未落（S2b lang 时序未到）
     * 时 {@code translateToLocal} 原样返回键名 ⇒ 与连号循环同一个判据跳过，不把键名当文案展示。
     */
    private static void appendDistillFastLine(List tooltip, Object[] args) {
        final String key = PocketDistillDriver.TOOLTIP_DISTILL_FAST_KEY;
        final String template = StatCollector.translateToLocal(key);
        if (template.equals(key)) {
            return;
        }
        tooltip.add(
            EnumChatFormatting.LIGHT_PURPLE + (template.indexOf('%') < 0 ? template : String.format(template, args)));
    }

    /**
     * tooltip 的规格读数（<b>顺序即 {@code %1$d…%12$d}</b>，与 lang 里的下标一一对应）。
     * <p>
     * 全部取自常量与面板列类的几何单源，不在这里做任何算术以外的推导；
     * 蒸馏秒数的<b>双口径</b>由 {@code TaumDistillRules.distillIntervalTicks} 换算（节拍权威只有那一处：
     * 基档 {@code %6$d}、★R95 加速档 {@code %12$d}）。
     * <p>
     * ★R78 新增的四项（组数 / tank 总数 / 流体总容量 / 源质格数 / 背包格数）与它们替代的旧写法
     * 同一条纪律：这些数字只允许出现在这里一次，lang 与 README 都只引用不重抄
     * （R75 的"每槽 16M"双处声明 = 本方法 + README，本批把合计 288,000,000 也并进同一纪律）。
     */
    private static Object[] tooltipArgs(ItemStack carrier) {
        // ★R95 S5：单槽/合计容量按 CAPACITY 位动态（16M/16G、288M/288G）——升级侧喂 Long（%d 对
        // Long/Integer 同形，lang 键与 %5$d/%9$d 槽位零改动）；未升级喂 Integer（渲染与旧字面同源）。
        // ★R96 S1：读点换组合谓词 isActive ⇒ 关掉容量开关后 tooltip 的读数跟着回落到未升级那一档
        // （"关了开关读数还写 16G"是最容易被玩家当成"开关没生效"的一处，所以它必须在同一批里换）。
        final boolean capacityUpgraded = PocketUpgradeSwitches.isActive(carrier, PocketUpgradeType.CAPACITY);
        return new Object[] {
            // %1$d 中栏格数
            Integer.valueOf(PocketConstants.GHOST_ITEM_SLOT_LIMIT),
            // %2$d 行数 / %3$d 列数
            Integer.valueOf(NekoPocketStorageColumn.ROWS), Integer.valueOf(PocketSlots.STORAGE_COLUMNS),
            // %4$d 每组流体列数（R75①；★R78② 的组数与 tank 总数在下面两项）
            Integer.valueOf(PocketConstants.FLUID_COLUMN_COUNT),
            // %5$d 单槽容量 mB（★规格外自立项；★R95 S5 起按 CAPACITY 位 16M/16G 动态喂）
            capacityUpgraded ? Long.valueOf(PocketConstants.FLUID_BAR_CAPACITY_UPGRADED_ML)
                : Integer.valueOf(PocketConstants.FLUID_BAR_CAPACITY_ML),
            // %6$d 蒸馏一轮秒数·基档（★tick→秒的换算走 PocketConstants 单源，不再内联 20）
            Integer.valueOf(PocketConstants.ticksToSecondsCeil(TaumDistillRules.DISTILL_INTERVAL_TICKS)),
            // %7$d 流体组数（R78②）
            Integer.valueOf(PocketConstants.FLUID_GROUP_COUNT),
            // %8$d 独立流体 tank 总数（= 组数 × 每组列数 = 18）
            Integer.valueOf(PocketConstants.FLUID_TANK_TOTAL),
            // %9$d 流体总容量 mB（= tank 总数 × 单槽容量，★派生不是手抄；★R95 S5 起升级侧 18 × 16G = 288G）
            capacityUpgraded ? Long.valueOf(PocketConstants.fluidTotalCapacityMl(true))
                : Integer.valueOf(PocketConstants.FLUID_TOTAL_CAPACITY_ML),
            // %10$d 源质盘格数（R78②③：6×12 = 72，且 ≥ 实测 aspect 注册数）
            Integer.valueOf(PocketConstants.ESSENCE_DISPLAY_GRID),
            // %11$d 面板内玩家背包格数（R78①；★代价 = E4 包放大，见 PocketSlots 类注释）
            Integer.valueOf(PocketConstants.PLAYER_BACKPACK_SLOTS),
            // %12$d 蒸馏一轮秒数·加速档（★R95：装 MAGE 后 1 秒；与 %6$d 同一换算单源，
            // 真值只住 TaumDistillRules.distillIntervalTicks 一处）
            Integer.valueOf(PocketConstants.ticksToSecondsCeil(TaumDistillRules.distillIntervalTicks(true))) };
    }
}
