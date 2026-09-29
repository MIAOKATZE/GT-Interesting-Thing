package com.miaokatze.gtit.common.items.pocket;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
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
import com.miaokatze.gtit.common.items.pocket.mage.PocketCrystalDriver;
import com.miaokatze.gtit.common.items.pocket.mage.PocketEssenceTransmuteDriver;
import com.miaokatze.gtit.common.items.pocket.mage.PocketWandChargeDriver;
import com.miaokatze.gtit.common.items.pocket.magnet.PocketMagnetDriver;
import com.miaokatze.gtit.common.util.GTITUtils;
import com.miaokatze.gtit.crossmod.taum.TaumCompat;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.register.CreativeTabManager;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Optional;
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
 * <p>
 * <b>★R96 S11 可穿戴化（需求 8）</b>：本类同时是 Baubles 饰品（{@link IBauble}，
 * 槽型 {@link BaubleType#UNIVERSAL} —— ★刻意不是 {@code RING}：仓内已有七枚指环在抢那两个
 * 戒指格，UNIVERSAL 才让它落到空着的格上）。渲染零成本：本仓的 Baubles fork（Baubles-Expanded）
 * 已删 {@code IBaubleRender}，穿上不需要任何客户端渲染器。
 * <p>
 * <b>★R96 S11-fix：需求 8 的「符文护盾 +20」那一半不在本类（S11 原写法已撤销）</b>。
 * S11 原本让常驻类直接 {@code implements} 神秘时代的护盾接口，判据是「{@code @Optional.Interface}
 * 会让 FML 在 mod 缺席时把接口从 {@code interfaces} 数组里擦掉」——★这条兜底<b>靠不住</b>：
 * 神秘时代在本仓是 {@code compileOnly}（{@code dependencies.gradle:73}），把它的类型写进
 * {@code implements} 位就是让<b>常驻类</b>的类型层次在运行期要求一个可选件在场，而接口解析发生在
 * JVM 加载本类的那一刻；写全限定名只绕得过 {@code import} 行的文本检查，绕不过类型层次。
 * 于是「没装神秘时代的实例」这一格上，本类一被加载就 {@code NoClassDefFoundError} ⇒ 整个 mod 起不来，
 * 撞的正是 {@code TaumCompat:15-19} 那条「会被正常加载的类不得静态引用 TC」的类污染红线。
 * <p>
 * ⇒ 那一半现在住在 {@code mixin/thaum/MixinItemNekoDimensionPocket_RunicArmor}：mixin 类只由
 * Mixin transformer 通道消费、mod 的常规类加载器按名不可见（口径与取证见 {@code r96-ret10.md §6}），
 * 所以 TC 类型进 mixin 不算进常驻面。注入走 {@code GtitThaumLateMixinLoader} 的 LateMixin，
 * <b>施加条件 = TC 在场</b> ⇒ ★没装神秘时代时这一半<b>自然缺席</b>：口袋照常可穿戴、照常跑八条被动，
 * 只是不给符文护盾加容量（这正是需求 8 想要的「可选」语义，也是 {@code @Optional} 本来该给的行为）。
 * <p>
 * <b>★R96 S11 双宿主桥（硬要求 7）</b>：穿戴态下 vanilla <b>不再</b>调 {@code Item.onUpdate}
 * （{@code InventoryPlayer.java:341-348} 只扫主背包 36 格），被动改由 {@link #onWornTick} 驱动
 * （{@code EventHandlerEntity.playerTick}，★双端都发）。两枚宿主共用同一段
 * {@link #runPassives} ⇒ ★加一条被动只需要改一处，结构上不存在"只挂了一个宿主"的那种漏。
 */
@Optional.InterfaceList(value = { @Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles") })
public class ItemNekoDimensionPocket extends Item implements IGuiHolder<PlayerInventoryGuiData>, IBauble {

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
     * <p>
     * ★R100 片 F（架构解耦）：gui 侧实现经 {@link PocketPanelBridge} 注入（注册点 =
     * 双端都跑的 {@code CommonProxy#init}），common 不再静态 import {@code gui.pocket} ——
     * 委托目标就是原来那次静态调用本身，行为零变化。
     */
    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager syncManager, UISettings settings) {
        logOnce(
            "buildUI 到达 " + FMLCommonHandler.instance()
                .getEffectiveSide() + "，手持槽栈 " + (data.getUsedItemStack() == null ? "null" : "非 null"));
        return PocketPanelBridge.build(data, syncManager, settings);
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
     *                 ★<b>R98 S2 后该键号已漂移</b>：激进裁剪把连号族重排成 {@code 0..3}，那句
     *                 「内容随物品丢」现在住在 {@code item.neko_dimension_pocket.tooltip.2}，
     *                 旧号 {@code .7} 在两份 lang 里都不再存在 ⇒ 引用本段处请按<b>内容</b>认，别照号抄。
     *                 ★R100 片 E 后再漂移一次（4 → 3 删行重排）：那句话现在住在
     *                 {@code tooltip.1}，同一条纪律——按内容认。
     */
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        // ★R96 S11：体段抽出（★一字未改，只是搬家）⇒ 宿主从一枚变两枚，见 runPassives 的 javadoc。
        // 本方法只保留 5 参签名与逐字透传（形参元数是 GT-compat 判据，见上面的 javadoc）。
        runPassives(stack, world, entity, slot, selected);
    }

    /**
     * ★R96 S11（硬要求 7）：<b>被动段落的唯一载体</b>，{@link #onUpdate}（背包态，vanilla 只 tick 主背包
     * 36 格）与 {@link #onWornTick}（穿戴态，Baubles 的 {@code EventHandlerEntity.playerTick} 驱动）
     * <b>共用</b>这一段。
     * <p>
     * ★这条桥不是风格问题而是<b>结构问题</b>：穿戴态下 {@code Item.onUpdate} 根本不被调用
     * （{@code InventoryPlayer.java:341-348} 的循环只走 {@code mainInventory}），而 {@code onWornTick}
     * 在没穿上的时候又不存在 ⇒ 只挂一侧就必然出现「放背包里会动、穿在身上全停」或者反过来。
     * 本轮在册的被动共 <b>八条</b>：两条 {@code tickDown} 倒计时 + 通道 + 蒸馏 + 磁力 +
     * 魔法使四条（法杖 / 猫猫币 / 源质转换 / 结晶）——★摘掉任何一条都是 R57 的同族形状。
     * <p>
     * ★形参 {@code slot} / {@code selected} 是 onUpdate 的逐字透传：R95 起本段<b>不</b>用它们做门控
     * （背包任意格都推进），穿戴侧传的是 {@code (-1, true)} —— 没有主手槽号，而 {@code true}
     * 是刻意给的（★不是笔误）：将来若有人重新拿 {@code selected} 加门，给 {@code false} 就等于
     * 把穿戴这一侧的八条被动全关掉且不报错。
     */
    private void runPassives(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
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
        // ★R96 S9b 第四条（只加挂载行，本方法的门控与结构一字未动）：结晶模式，★缺省关。
        // 它自带「主开关 + 子模式位」两道早退与 NBT 剩余 tick 节拍 ⇒ 两种关态在这里都只花一次位读。
        PocketCrystalDriver.onItemTick(stack, world, player);
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

    // ------------------------------------------------------------------ 穿戴面（R96 S11 · 需求 8）

    // ★R96 S11-fix：本段现在<b>只有 Baubles 那一侧</b>（六法 + 槽型）。需求 8 的另一半「符文护盾 +20」
    // 原先也住这里（常驻类 implements 神秘时代的护盾接口 + getRunicCharge 返回 20），★已整段搬进
    // mixin/thaum/MixinItemNekoDimensionPocket_RunicArmor —— 撤销理由（TC 缺席时常驻类的类型层次
    // 解析不出那个接口 ⇒ 整个 mod 起不来）与「不穿戴不计入 / 只数前四格」那两条消费端事实，
    // 都写在那枚 mixin 的类注释里（★判据跟着接口一起搬家，不留第二处真相）。
    // ★本类因此不含任何 TC 类型引用：implements 位 / 字段类型 / 方法签名 / 返回类型 / 局部变量
    // 五类位置全零，这条由 verify-pocket.sh 的 ★R96-S11-fix 门（整棵 src/main/java 除
    // TaumBridge.java 外的 thaumcraft. 代码位计数）与用例
    // wearable_mount_is_optional_universal_and_honest 各钉一遍。

    /** 穿戴侧没有"主手槽号"这个东西（{@code slot} 形参自 R95 起不参与任何门控，见 {@link #runPassives}）。 */
    private static final int WORN_SLOT = -1;

    @Override
    @Optional.Method(modid = "Baubles")
    public BaubleType getBaubleType(ItemStack stack) {
        // ★UNIVERSAL，不是 RING：仓内在册的七枚指环（common/items/rings/）已经在抢戒指格，
        // 给 RING 等于让口袋与它们互相挤槽；UNIVERSAL 由 Baubles 自己按空位落格。
        return BaubleType.UNIVERSAL;
    }

    /**
     * ★穿戴态的宿主（R96 S11 双宿主桥的另一枚）。驱动点是 Baubles 的
     * {@code EventHandlerEntity.playerTick}（{@code LivingUpdateEvent}），★<b>双端都发</b> ⇒
     * ★首行这道 {@code isRemote} 早退不是收尾工作，而是让这一侧与服务端那一侧<b>同形</b>的唯一保证：
     * 没有它，客户端会真的跑一遍 {@link #runPassives}（driver 们在客户端读到的是同步镜像，
     * 写出去的就是"客户端改服务端数据"那类静默错账）。
     * <p>
     * ★入参 {@code stack} 的非空由 Baubles 自己的循环保证（它先 {@code stack != null &&
     * stack.getItem() instanceof IBauble} 才发这一拍），本方法不重复那道判据。
     */
    @Override
    @Optional.Method(modid = "Baubles")
    public void onWornTick(ItemStack stack, EntityLivingBase entity) {
        if (entity.worldObj.isRemote) {
            return;
        }
        runPassives(stack, entity.worldObj, entity, WORN_SLOT, true);
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public void onEquipped(ItemStack stack, EntityLivingBase entity) {}

    @Override
    @Optional.Method(modid = "Baubles")
    public void onUnequipped(ItemStack stack, EntityLivingBase entity) {}

    /**
     * ★允许随时穿上：不额外设门槛（穿脱与"内容还在不在"是两件事 —— 内容在栈 NBT 里，
     * 而 {@code relocateCarrier} 那一双判据自 R96 S11 起<b>多了一条 bauble 腿</b>，见
     * {@code NekoPocketPanel}，所以从饰品栏关屏也能落盘）。
     */
    @Override
    @Optional.Method(modid = "Baubles")
    public boolean canEquip(ItemStack stack, EntityLivingBase entity) {
        return true;
    }

    @Override
    @Optional.Method(modid = "Baubles")
    public boolean canUnequip(ItemStack stack, EntityLivingBase entity) {
        return true;
    }

    // ------------------------------------------------------------------ 状态位与冷却（R37/R16/R24）

    /**
     * {@code work} 位：短效通道剩余 tick 或 burst 显示窗剩余 tick 任一在跑（R37 的工作动画来源）；
     * ★R96 S5 起<b>持续化那一腿也走真读数</b>（见 {@link #isChannelWorkLive}）。只读腿：不建档
     * （R53c 读路径纪律），无档口袋照旧走倒计时两键。
     * <p>
     * ★★<b>R96 S1 改口（P-1 正交 enabled 位图）</b>：常亮腿从"位图在场即恒真"改成
     * {@code PocketUpgradeSwitches.isActive(CHANNEL_PERSIST)} —— 玩家把持续化<b>关掉</b>之后帧带与光泽
     * 必须跟着停，否则"关了开关画面还亮着"就是开关对该路径根本无效（R57/C3 同族的静默失效）。
     * ★不主动 stop 已在跑的通道：关开关只让 driver 的批边界回满不再发生 ⇒ 自然衰减到 0，
     * 复用既有 {@code finishBatch} 的回收支，不建第二台状态机。<b>（★这一句"不主动 stop / 自然衰减"
     * 自 R98 起作废，史实留在原处不删；现行口径见本节末尾的 R98 段。）</b>
     * <p>
     * ★★★<b>R96 S5 再改口（验收 B4：假读数门）</b>：位图单独<b>不再点亮任何东西</b>。S5 之前这一腿
     * 写作「位生效 ⇒ 常亮」，而那一版的持续化<b>谁都开不起通道</b>（激活真空，取证 r96-ret1 §2.5）⇒
     * 帧带亮着、状态行写着"通道常开中"、通道一批都没走过 = 本轮两处「记为完成而实机未生效」之一。
     * ★下面这条腿因此除位图外<b>再问一次"有活通道在场"</b>；两半在这一版里读数的确会重合
     * （driver 每批边界把 work 位续到 {@code 30×20+20} 拍 ⇒ 活通道在跑时它必然在场），保留位图这一半
     * 的理由是<b>身份</b>：这一族的常亮属于持续化，摘掉它 {@code isWorkActive} 就退化成纯倒计时读数，
     * 帧带不再声明它代表什么。真正被修掉的是「位在场即恒真」那半句 —— 现在它单独一律不成立。
     * <p>
     * ★★★<b>R98 改判（需求 3）：上面 R96 S1 那句「★不主动 stop 已在跑的通道 ⇒ 自然衰减到 0」作废</b>。
     * 现行口径：关掉持续化的<b>那一个边沿</b>由服务端开关写点（
     * {@code NekoPocketServerHandler#performUpgradeSwitchToggle} 里 {@code type + Outcome.TURNED_OFF}
     * 那一支）当场调 {@code PocketChannelManager#stopChannel} 停道，并立刻调
     * {@link #startWorkTicks(ItemStack, int)} 写 {@code -1} 摘掉 {@code UI_WORK_TICKS} ⇒
     * <b>本类的帧带与光泽在同一拍转暗</b>，不再存在"开关已关、画面还亮着、货还在白搬最长 30 秒"那一段。
     * ★<b>上面那句"同一拍转暗"的射程必须写明 —— 本方法是两条腿的合取，不是单因，别把它读成"整块画面必然熄灭"</b>：
     * ① 另有 {@code UI_BURST_SHOW_TICKS} 那一腿（≤5 秒，代表<b>一次已经发生完的瞬时通道</b>，与持续化无关），
     * ★边沿腿<b>不碰它</b>（{@code -1} 只走 {@code removeTag(UI_WORK_TICKS)}）⇒ burst 显示窗内画面照旧会亮到
     * 它自己归零，那<b>不是</b>停道失效；② 光泽读的是 {@code isWorkActive() || anyActive(...)} ⇒
     * <b>任一其它升级插件开着本就该亮</b>，同样不在本判射程。⇒ 精确表述是「<b>work 腿同拍清零</b>」；
     * 至于三腿在玩家眼里是否<b>视觉上同拍</b>翻转，属实机项（检查表 §二十二 X-4，本轮离线不可证）。
     * 旧判的另一半（"<b>不建第二台状态机</b>"）不仅仍然成立、还被加强：停道用的就是既有
     * {@code PocketChannelState#stop()} 原语与既有 {@code PocketChannelManager#forget} 的成对形状
     * （driver 自己那两处就地停道早就是这个形状），零新增状态、零新增计时真相。
     * 下面 {@link #isChannelWorkLive(ItemStack)} 这条判据<b>一字未改</b> —— 组合谓词与"在场"两半都还在，
     * 只是"在场"这一半现在可能由边沿腿提前清零，而不是只能等 {@code tickDown} 归零。
     */
    public static boolean isWorkActive(ItemStack stack) {
        final NBTTagCompound root = stack == null ? null : stack.getTagCompound();
        if (root == null) {
            return false;
        }
        if (isChannelWorkLive(stack)) {
            return true;
        }
        return root.hasKey(PocketConstants.UI_WORK_TICKS) || root.hasKey(PocketConstants.UI_BURST_SHOW_TICKS);
    }

    /**
     * ★★R96 S5（验收 B4）：玩家可见的「通道常开中」三处承诺（面板状态行 / 通道按钮 tooltip /
     * 物品帧带与光泽）共用的<b>唯一</b>判据 = 持续化组合谓词生效 <b>且</b> 有活通道在场。
     * <p>
     * <b>"在场"读的是 {@code UI_WORK_TICKS} 那一条同步下来的相对倒计时</b>，不是
     * {@code PocketChannelManager#peek}。★这条选择不是风格问题：那台状态机是<b>服务端内存</b>条目，
     * 而这三个承诺面全在客户端被问（{@code hasEffect}/{@code pickIcon} 每帧、{@code tooltipDynamic}
     * 每次打开、状态行走 {@code StringSyncValue} 的客户端侧）⇒ 问 {@code peek} 恒读到"没通道"，
     * 会把常亮改成<b>常暗</b>（恒假显示，比假读数更坏），还要为显示新建第二条同步 = 两处真相。
     * work 位由 {@code PocketChannelDriver} 在<b>每个批边界</b>续到"本通道剩余总长"（≥ 一整拍 ⇒
     * 活通道在跑时永不 lapse；通道没跑时它必然归零并被 {@code tickDown} 摘键），因此它正是
     * "有活通道在场"的客户端镜像 —— 也就是 S1 定案里那句「常亮 = 真读数的等价物」的落地形状。
     * <p>
     * ★只读腿：不建档、不碰状态机（R53c 读路径纪律）。
     */
    public static boolean isChannelWorkLive(ItemStack stack) {
        final NBTTagCompound root = stack == null ? null : stack.getTagCompound();
        return root != null && PocketUpgradeSwitches.isActive(stack, PocketUpgradeType.CHANNEL_PERSIST)
            && root.hasKey(PocketConstants.UI_WORK_TICKS);
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
     * ★R98 S2 起为 4 行——用户裁定"大幅度简化 Tooltip"，激进档 −6：几何说明书 / 绑定流程 /
     * 有电前提 / NEI 虚化格四行删除、原 {@code tooltip.5} 与蒸馏双口径行合并成一条追加行、
     * 原 {@code tooltip.9}（每槽与合计容量）让位给面板的 {@code gtit.pocket.fluid.capacity}；
     * ★<b>R100 片 E 起为 3 行</b>——描述精简：删 R39a 流体列方向行、剩余三行文案口语化压短，
     * 整体重排成 {@code 0..2}）。
     * 连号行之外还有<b>两类追加行</b>：★R95 蒸馏双口径行（{@link #appendDistillFastLine}；R100 片 E
     * 同批删冗词，键名冻结、{@code %6$d}/{@code %12$d} 注入形态不动）与 ★R98 的元素储量
     * （{@link #appendElementReserveLines}），都追加在连号循环<b>之后</b>、★不在连号中间插行
     * ⇒ 恒定 4 行（3 + 蒸馏）。
     * ★★R99 P4-D 改判：储量六行<b>恒出</b>（空位显 0）⇒ 连号 + 蒸馏 + 储量恒 <b>10 行</b>
     * （R98/R99 期"恒 11 行"的口径到本批为止），再加片 A 的品牌尾行 ⇒ addInformation 总产出
     * 恒 <b>11 行</b>；检查表 X-1 的行数判读挂账由片 G 翻新。
     * <p>
     * 消费端是 {@code equals(key)} 即 break 的循环（先例 {@code common/items/NekoCoin.java:28-34}），
     * <b>跳号会静默截断后面的行</b> ⇒ 每次裁剪都是<b>整体重编号</b>（R98 重排成 {@code 0..3}，
     * R100 片 E 重排成 {@code 0..2}），不是"删几条留几条"。
     * ★★<b>R100 片 E 翻案（R39a 的原声明位就是本段）</b>：R39a 的"两排同权不是上下分流"声明行
     * （R98 期为 {@code tooltip.1}、更早的旧 {@code .6}）<b>删除</b>——用户裁定描述"简洁明了不废话"，
     * 该行防的误读（以为上下两排分工不同）在面板逐列同形的现状下已无落点；翻案按仓内纪律
     * 「显式记档 + 原位注释 + 台账双落」执行，台账由片 G 记，新增用例
     * {@code pocket_tooltip_r39a_row_removed_with_reversal_note} 把"行已删 + 本注释在位"钉成机检。
     * ★留下的两处声明位只换号不动语义：新 {@code tooltip.1}（旧 {@code .2}、更早 {@code .7}）=
     * R53b 的后果声明、新 {@code tooltip.2}（旧 {@code .3}、更早 {@code .8}）= 合成一次性代价声明；
     * 旧 {@code .5} 那句"5 秒是节拍不是产量"（R28）随合并活在新追加行里。
     * 本文件的 javadoc 曾经点名 {@code tooltip.5}/{@code tooltip.6}，R98 后按<b>新号</b>重述，
     * 用例 {@code pocket_tooltip_family_is_contiguous_and_three} 把"恰 3 条、连号、两份同键集"钉成机检。
     * <p>
     * ★<b>行里不写规格数字</b>（R75 的 lang 契约第 5 条）：格数、行列、流体<b>组数与 tank 总数</b>、
     * 单槽容量与<b>总容量</b>（R78②）、源质盘格数（R78②）、面板内背包格数（R78①）、蒸馏节拍
     * 全部由 {@link #tooltipArgs()} 从常量填进 {@code %1$d…%12$d} 的<b>带位置下标</b>的占位。
     * 用下标而不是裸 {@code %d} 的理由：所有行走同一个实参数组，裸 {@code %d} 会一律取第 1 个实参
     * ⇒ "每槽 16,000,000" 会被填成"135"，而且不报错。
     * <p>
     * ⚠ <b>备而未用的槽清单（R98 裁剪的直接后果，★不是死代码）</b>：{@code %1,%2,%3,%4,%7,%8,%10,%11}
     * 八项仍由 {@link #tooltipArgs()} 派生、但 lang 停止引用（几何行与容量行都撤了）；
     * {@code %5$d}/{@code %9$d} 同样不再被 lang 念到，★却<b>必须</b>留在数组里 ——
     * 它们的读点 {@code PocketUpgradeSwitches.isActive(carrier, PocketUpgradeType.CAPACITY)} 被
     * {@code NekoPocketModelTest} 按签名硬钉（"关掉容量开关 tooltip 读数跟着回落"这条承诺的载体），
     * 而且面板的 {@code gtit.pocket.fluid.capacity} 随时可能把这两个数再念回来。
     * ★新增数字一律从 {@code %13$d} 起槽并同步扩数组（越界 = {@code MissingFormatArgumentException} =
     * 悬停即崩）；R98 的六行储量<b>不占</b>这个族的槽，它走独立的两个实参，见 {@link #appendElementReserveLines}。
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
        appendElementReserveLines(tooltip, stack);
        tooltip.add(GTITUtils.getAddedByLine());
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
     * <p>
     * ★<b>R98 S2 起本行同时是原 {@code tooltip.5}（"蒸馏每 %6$d 秒一轮，产出量按物品自身源质原量入账"）
     * 的替代者</b>：两档口径合并成一条，★正好吃掉 {@code %12$d} 这个此前备而未用的槽（零扩槽 ⇒
     * 键数 −6 + 1，行数 −6），键名与 {@link PocketConstants#ticksToSecondsCeil} 的换算单源都没动。
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
     * ★R98 S2（需求 1 的"腾位置"那一半）：<b>六行元素储量</b>，形状照 TC4 魔力石
     * {@code thaumcraft/common/items/baubles/ItemAmuletVis.java:127-137} 的逐行储量。
     * 生产入口：解析 lang 模板后把排版交给 {@link #appendElementReserveLines(List, String, PocketElementStore)}
     * （★拆两段是为了可测：测试 JVM 里没有加载本 mod 的 lang，{@code translateToLocal} 会原样返键 ⇒
     * 若把取模板与排版缝在一起，那六行的排版就只能实机目检。拆开后测试拿<b>两份 lang 的真模板</b>
     * 驱动同一段代码，钉的就是上线的那段）。
     */
    private static void appendElementReserveLines(List tooltip, ItemStack carrier) {
        final String key = PocketElementStore.TOOLTIP_ELEMENT_KEY;
        final String template = StatCollector.translateToLocal(key);
        if (template.equals(key)) {
            // 键未落 ⇒ 与连号循环、distill 行同一个判据跳过，不把键名当文案展示
            return;
        }
        // ★attach 只持根引用、读腿走 raw(false) ⇒ 读侧零建档（R53c）；★R99 P4-D 起六行恒出、
        // 空位显 0 —— 「没吸进来」与「没同步」靠这六行分得开
        appendElementReserveLines(tooltip, template, PocketElementStore.attach(carrier.getTagCompound()));
    }

    /**
     * 储量行的<b>排版腿</b>（包级可见只为测试可驱动，★生产唯一调用点是上面那个重载）。
     * <p>
     * 四条口径，逐条都是需求/裁定落下来的：
     * <ul>
     * <li><b>行序 = {@link PocketConstants#PRIMAL_TAGS} 的白名单序</b>（aer/terra/ignis/aqua/ordo/perditio），
     * 与 TC 的 {@code Aspect.aspects}（{@code LinkedHashMap}）插入序逐字一致；★不由数量、不由色、
     * 不由玩家操作决定 ⇒ <b>TC 缺席照样出这六行</b>，回落的只有名字（{@link TaumCompat#nameOf(String)}
     * 返 tag 本身）。</li>
     * <li><b>★恒六行、空位显 0（R99 P4-D 改判，用户 2026-09-28 拍板）</b>：取数走逐 tag 的
     * {@link PocketElementStore#get(String)}（缺键即 0）⇒ 「没吸进来」与「吸了但没同步」在画面上
     * <b>不再长得一样</b>——六个 0 是"这张表活着且真是 0"，整块消失才是"坏了"。R98 DP-2 的
     * 「非零才出行」（走 {@code snapshot()}）作废，当时的痛点正是全 0 时六行一行都不出、
     * 玩家无法判读吸收有没有工作。★读侧仍零建档：{@code get} 走 {@code raw(false)}，
     * 为显示 0 也绝不写 {@code elem} 键（R53c 一字未动）。</li>
     * <li><b>★不带 {@code /100}、不带小数</b>：魔力石那层 {@code / 100.0F} + {@code DecimalFormat}
     * 是 TC 的 <b>vis ×100 存档刻度</b>（{@code ItemAmuletVis#addVis} 写入时 {@code amount * 100}），
     * ★不是显示风格；本仓 {@code elem} 存的<b>就是点数</b>（{@code PocketElementStore#add} 直存
     * {@code setInteger}、上限 {@link PocketConstants#ELEMENT_CAP_PER_TAG}=500 直接是点）
     * ⇒ 抄过来会把"500"显示成"5"，是量纲错，不是美化（取证 {@code 01b-essence-ref.md} §3.7）。</li>
     * <li><b>独立实参，★不占 {@link #tooltipArgs()} 那个族</b>：那 12 项是<b>恒定规格读数</b>，
     * 混进"随存档内容浮动的六项"会同时造出第二份真相与越界风险。本行只有两个实参
     * （{@code %1$s} 名字、{@code %2$d} 数量），模板与数组都是这一条行专用。</li>
     * </ul>
     * 前导那一个半角空格★必须在 Java 侧加：{@code java.util.Properties} 会吃掉 lang 值的行首空白，
     * 写进 lang 等于没写（魔力石也是把空格放在 {@code list.add} 的字面量里，见 {@code :134}）。
     * 色码用 {@link PocketConstants#PRIMAL_CHAT_CODES}（本地表、零 TC 依赖，★有意的第二份真相，
     * 立法理由写在该常量的 javadoc 里）；{@code §r} 复位住在 lang 模板里，与魔力石的
     * {@code "§r x "} 同形。★行内不加 {@code LIGHT_PURPLE}：那会盖掉 aspect 自己的颜色。
     */
    static void appendElementReserveLines(List tooltip, String template, PocketElementStore store) {
        // ★R99 P4-D：六行恒出、逐 tag 点查（缺键即 0）。「非零才出」的旧裁定由 snapshot 持有、
        // 这里不判第二次的口径随之作废——现在的口径就一条：白名单序 × 恒六行 × 空位显 0。
        for (int i = 0; i < PocketConstants.PRIMAL_TAGS.length; i++) {
            final String tag = PocketConstants.PRIMAL_TAGS[i];
            tooltip.add(
                " " + PocketConstants.PRIMAL_CHAT_CODES[i]
                    + String.format(template, TaumCompat.nameOf(tag), store.get(tag)));
        }
    }

    /**
     * tooltip 的规格读数（<b>顺序即 {@code %1$d…%12$d}</b>，与 lang 里的下标一一对应）。
     * <p>
     * 全部取自 {@link PocketConstants} 常量单源（★R100 片 F 起行列两项也读它：中栏行/列在
     * gui 侧本就是它的转发/派生，见 {@code PocketSlots.STORAGE_COLUMNS} 与
     * {@code NekoPocketStorageColumn.ROWS} 的声明，common 侧不再为此 import gui 包），
     * 不在这里做任何算术以外的推导；
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
            // %2$d 行数 / %3$d 列数（★R100 片 F：读 PocketConstants 单源；面板侧 15 行=135/9、
            // 9 列=同源转发，两处等值由回归套件钉住）
            Integer.valueOf(PocketConstants.STORAGE_ROWS), Integer.valueOf(PocketConstants.STORAGE_COLUMNS),
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
            // %11$d 面板内玩家背包格数（R78①；★代价 = E4 包放大，见 gui 侧 PocketSlots 类注释）
            Integer.valueOf(PocketConstants.PLAYER_BACKPACK_SLOTS),
            // %12$d 蒸馏一轮秒数·加速档（★R95：装 MAGE 后 1 秒；与 %6$d 同一换算单源，
            // 真值只住 TaumDistillRules.distillIntervalTicks 一处）
            Integer.valueOf(PocketConstants.ticksToSecondsCeil(TaumDistillRules.distillIntervalTicks(true))) };
    }
}
