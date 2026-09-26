package com.miaokatze.gtit.mixin.thaum;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.items.pocket.ItemNekoDimensionPocket;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketElementStore;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;

import baubles.api.BaublesApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/**
 * ★R96 S10：TC 侧 mixin 的<b>唯一共享判据体</b>（口袋的「认栈 / 查余量 / 入账 / 掏账」四条原语）。
 *
 * <h2>为什么要有这个类，而不是把判据写进 mixin</h2>
 * 要素球那两枚（{@link MixinInventoryUtils_PocketHotbar} 与 {@link MixinEntityAspectOrb_PocketAbsorb}）
 * 宿主<b>不是同一个类</b>，{@code @Unique} 成员跨不过去；而它们在<b>同一次服务端 tick 内</b>必须给
 * <b>同一个答案</b>：TC 的 {@code EntityAspectOrb.func_70100_b_} 在偏移 19 调
 * {@code InventoryUtils.isWandInHotbarWithRoom}、偏移 63 就是一条
 * {@code checkcast ItemWandCasting}（生产 jar {@code javap -c} 实测，两处共 2 个调用点、全类只有这一条
 * {@code ItemWandCasting} 强转）。两边各写一遍判据就会出现「util 说有、碰撞腿说没有 ⇒ util 的答案被
 * 原方法体消费 ⇒ CCE」。故两侧都调本类同一个静态方法，判据只有一份。
 *
 * <h2>本类算不算撞「类污染红线」</h2>
 * 红线管的是<b>会被正常加载的常驻类</b>的常量池（{@code crossmod/taum/TaumCompat.java:16-27}）。
 * 本类引用了 {@code thaumcraft.api.aspects.*}，但只被 mixin 类调用，而 mixin 类只在 TC 在场、
 * 宿主类被加载时才经 transformer 通道施加 ⇒ 无 TC 的实例里本类永不会被加载（与 {@code TaumBridge}
 * 同一性质）。常驻侧的 {@code TaumCompat}/{@code PocketElementStore}/{@code ItemNekoDimensionPocket}
 * 签名<b>零改动</b>，也没有新增常驻文件。
 *
 * <h2>★入账口径：一律「整笔预检」，绝不吃一半</h2>
 * 与 {@link PocketElementStore} 类注释那条 R45c/FIX-6 同构：任何「先消耗别人（节点 / 要素球 /
 * 护盾节拍）再给自己入账」的动作，判 false 就一分不拿。基座是 1 点/5tick 的逐次搬运
 * （TC {@code TileWandPedestal.java:186}/{@code :231}），要素球是整球一笔（{@code aspectValue}），
 * 两者共用 {@link #credit} ⇒ 满足 {@code PocketElementStore} 类注释对「S10 的 mixin 腿接同一对原语」的要求。
 *
 * <h2>★量纲：口袋 1 点 ≡ TC 的「显示单位」，不是法杖 NBT 的 ×100 存储刻度</h2>
 * ① S9a 写侧 {@code TaumBridge#chargeWandVis}（{@code TaumBridge.java:463-483}）把口袋点数交给
 * {@code ItemWandCasting.addVis}，而 {@code addVis}（TC {@code ItemWandCasting.java:358-371}）内部就是
 * {@code amount * 100} 并按 {@code getMaxVis}（{@code :85-87} = 杖芯容量 ×100）钳制 ⇒ 口袋 1 点 = 法杖 1 显示点；
 * ② 基座 {@code TileWandPedestal.java:231-232} 每 5 tick 从节点取 <b>1 个原始 aspect 点</b>、给法杖
 * {@code addVis(...,1,...)} 的 <b>1 显示点</b> ⇒ 节点原始点数与口袋元素点数天然同量纲。
 * ⚠ 护盾那条不同：{@code WandManager.consumeVisFromInventory} 收到的 {@code AspectList} 是<b>存储刻度</b>
 * （{@code consumeAllVis:317-357} 直接拿它与 {@code getVis} 比、不除 100；{@code Config.shieldCost = 50}），
 * 故护盾腿按 {@code ceil(stored / 100)} 换算 —— {@link #costToPocketPoints(int)} 是 {@code addVis}
 * 那次 ×100 的<b>逆运算</b>，本仓不自造第三种刻度。
 */
final class PocketVisSupport {

    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 一次性取证日志：第一次真给口袋入账时打一行，证明这条腿活过（本仓对「mixin 静默不生效」的在产防护形状）。 */
    private static boolean loggedCredit = false;

    private PocketVisSupport() {}

    // ------------------------------------------------------------------ 认栈与闸

    /**
     * 载体栈是不是一只口袋。判据与常驻侧同形：{@code instanceof} 具体类
     * （TC 的基座/球本来也只认具体类，取证 {@code r96-ret4.md} §3.2/§3.3）。
     * ★刻意不查开关：开关只闸「动不动容量」，不闸「这是不是口袋」。
     */
    static boolean isPocket(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemNekoDimensionPocket;
    }

    /** 「魔法使」这一型被动此刻是否生效（S1 的位图 ∧ ¬off-mask 组合谓词）；三条腿共用这一道闸。 */
    static boolean mageActive(ItemStack pocket) {
        return isPocket(pocket) && PocketUpgradeSwitches.isActive(pocket, PocketUpgradeType.MAGE);
    }

    // ------------------------------------------------------------------ 读数

    /** 只读余量（<b>不建档</b>，R53c 读路径纪律）：无档 / 非白名单 tag 一律 0。 */
    static int roomFor(ItemStack pocket, String tag) {
        if (pocket == null) {
            return 0;
        }
        final NBTTagCompound root = pocket.getTagCompound();
        return root == null ? 0 : PocketElementStore.readFrom(root).roomFor(tag);
    }

    /**
     * TC 的 {@code Aspect} → 仓内 tag，并挡掉一切非元始 / 非白名单。
     * ★白名单腿（{@link PocketConstants#isPrimalTag(String)}，纯查表）与 TC 复核腿
     * （{@code Aspect#isPrimal()}，同 {@code EntityAspectOrb.java:199} 的原生判据）<b>合取</b>：
     * 容量表只有 6 行，addon 注册的无成分新 aspect 单独问 {@code isPrimal()} 会被判真。
     */
    static String tagOf(Aspect aspect) {
        if (aspect == null) {
            return null;
        }
        final boolean primal;
        try {
            primal = aspect.isPrimal();
        } catch (Throwable t) {
            return null;
        }
        if (!primal) {
            return null;
        }
        final String tag = aspect.getTag();
        return PocketConstants.isPrimalTag(tag) ? tag : null;
    }

    /** tag → TC 的 {@code Aspect} 实例（注册表查不到就 null；addon 改名不炸）。 */
    static Aspect aspectOf(String tag) {
        try {
            return Aspect.getAspect(tag);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 写账

    /**
     * 拿活档视图（<b>唯一</b>的建档点）：{@link PocketElementStore} 的纪律是「无根不写」，
     * 而这三条腿都是<b>外部主动给我们灌 / 替别人付</b>，没有「玩家打开过口袋」这种前置 ⇒
     * 根 compound 必须由这里补建，否则新做的口袋第一次上基座会一分不进（静默失效）。
     * ★只在真要写时建；读判据一律走 {@link #roomFor}，不建空壳。
     */
    private static PocketElementStore writableStore(ItemStack pocket) {
        NBTTagCompound root = pocket.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            pocket.setTagCompound(root);
        }
        return PocketElementStore.attach(root);
    }

    /**
     * 给口袋入账一笔（<b>整笔预检</b>后才写）。
     *
     * @return 实际入账点数；0 = 非白名单 / 开关关着 / 余量不够（★调用方据此<b>一分不拿</b>对方）
     */
    static int credit(ItemStack pocket, Aspect aspect, int points) {
        if (points <= 0 || !mageActive(pocket)) {
            return 0;
        }
        final String tag = tagOf(aspect);
        if (tag == null || roomFor(pocket, tag) < points) {
            return 0;
        }
        final PocketElementStore store = writableStore(pocket);
        final Map<String, Integer> candidate = new LinkedHashMap<String, Integer>();
        candidate.put(tag, Integer.valueOf(points));
        // ★与 PocketElementStore 的成对纪律：canAcceptAll → putAll（不拿 add 的截断结果当消耗量）
        if (!store.canAcceptAll(candidate)) {
            return 0;
        }
        final int landed = store.putAll(candidate);
        if (landed > 0 && !loggedCredit) {
            loggedCredit = true;
            LOG.info("[gtit] TC mixin 元素入账命中：口袋收到 " + landed + " 点 " + tag
                + "（基座 / 要素球 / 护盾三条腿共用的判据体在跑）");
        }
        return landed;
    }

    // ------------------------------------------------------------------ 要素球腿

    /**
     * 快捷栏里第一只「装得下这一整笔」的口袋槽号（P4 与 P5 共用的<b>唯一</b>扫描）。
     * 扫描面刻意与 TC 原语逐字对齐：{@code InventoryUtils.java:455-473} 只走
     * {@code InventoryPlayer.getHotbarSize()} 那 9 格，本仓不放宽到 36 格
     * （放宽 = TC 没有的新行为，代价文案里也不承诺它）。
     *
     * @return 槽号；没有符合条件的口袋 ⇒ -1（★-1 不是失败，是「让 TC 的原生法杖逻辑照跑」）
     */
    static int hotbarPocketSlotWithRoom(EntityPlayer player, Aspect aspect, int amount) {
        if (player == null || player.inventory == null || amount <= 0) {
            return -1;
        }
        final String tag = tagOf(aspect);
        if (tag == null) {
            return -1;
        }
        final ItemStack[] hotbar = player.inventory.mainInventory;
        if (hotbar == null) {
            return -1;
        }
        final int size = Math.min(hotbar.length, InventoryPlayer.getHotbarSize());
        for (int slot = 0; slot < size; slot++) {
            final ItemStack stack = hotbar[slot];
            if (!mageActive(stack)) {
                continue;
            }
            if (roomFor(stack, tag) >= amount) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * 要素球→口袋的吸收一笔（{@link MixinEntityAspectOrb_PocketAbsorb} 的本体）。
     * ★预检 + 整笔落地才返回 true；返回 true 时已替调用方做完 TC {@code :202-204} 的三件事
     * （冷却 2 tick、{@code random.orb} 音效、{@code setDead}），调用方只需 {@code ci.cancel()}
     * 让含 {@code checkcast} 的原方法体根本不执行。预检不过就返回 false ⇒ 原方法照跑、球不消失、
     * 玩家一分不亏（口径同 TC 原生「装不下就不收」）。
     */
    static boolean absorbIntoPocket(Entity orb, EntityPlayer player, Aspect aspect, int amount) {
        if (orb == null || player == null || player.inventory == null || amount <= 0 || orb.worldObj == null
            || orb.worldObj.isRemote) {
            return false;
        }
        final int slot = hotbarPocketSlotWithRoom(player, aspect, amount);
        if (slot < 0) {
            return false;
        }
        final ItemStack pocket = player.inventory.mainInventory[slot];
        if (credit(pocket, aspect, amount) != amount) {
            // 理论上到不了这里（同一 tick、同一份判据）；真到了也绝不吞球
            return false;
        }
        player.xpCooldown = 2;
        orb.playSound("random.orb", 0.1F, pitchFrom(orb.worldObj.rand));
        orb.setDead();
        return true;
    }

    /** TC 的音效抖动式：{@code 0.5F * ((rand.nextFloat() - rand.nextFloat()) * 0.7F + 1.8F)}（{@code :203}）。 */
    private static float pitchFrom(Random rand) {
        final float a = rand == null ? 0.5F : rand.nextFloat();
        final float b = rand == null ? 0.5F : rand.nextFloat();
        return 0.5F * ((a - b) * 0.7F + 1.8F);
    }

    // ------------------------------------------------------------------ 护盾腿

    /** TC 存储刻度 → 口袋点数（{@code addVis} 那次 ×100 的逆运算，向上取整）。 */
    static int costToPocketPoints(int storedVis) {
        return storedVis <= 0 ? 0 : (storedVis + 99) / 100;
    }

    /**
     * 护盾回充的口袋腿（第三枚 mixin 的本体）：TC 原路（bauble 栏魔力石 / 主背包法杖）
     * <b>整笔付不起</b>时才问口袋。
     * <p>
     * ★顺序硬要求「先原路、后口袋」：{@code WandManager.consumeVisFromInventory} 经
     * {@code ThaumcraftApiHelper:222} 暴露给全生态，先口袋会改掉别人 mod 的抽能语义；
     * 现在这条腿只在「原路本来返回 false」的分支上生效 ⇒ TC 已有行为逐字不变。
     * <p>
     * ★扫描面 = 饰品栏<b>全部槽位</b>（不写死 4：SalisArcana 的
     * {@code MixinWandManager_ExtendedBaublesSupport} 已用 {@code @ModifyConstant} 放宽 TC 那两条
     * {@code a < 4}，读死 4 会与它错位）+ 主背包 36 格；护盾上限本身要求 {@code IRunicArmor}
     * 被<b>穿上</b>才计入（{@code EventHandlerRunic.java:63-91}），所以饰品栏那一支才是主用例。
     * <p>
     * 全有全无：{@code cost} 里任何一条非白名单元始、或口袋凑不出整笔 ⇒ 整笔不付
     * （与 {@code consumeAllVis} 的「任一 aspect 不够就整笔 false」同形）。
     * <p>
     * ★★<b>R96 S9b 把这条"全有全无"从口径升级成了代码级保证</b>（补掉 S10 报告 U-8 那条登记）：
     * 预检与出账两道都换成存储层的 {@code canPayAll} / {@code payAll}（★问的是<b>存量</b>，
     * 不是 {@link #roomFor}；实掏 ≠ 应掏时把已掏的原样补回并整笔让给 TC）。两道加起来才是
     * "缺一即整笔不扣"，旧实现只有第一道、而且第一道问错了那个数。
     * <p>
     * ★<b>与 S11 的交接点</b>（= U-4 的交叉登记）：本腿只让口袋当<b>油箱</b>，不当<b>油箱大小</b>。
     * 护盾的容量上限由 {@code EventHandlerRunic.java:63-73} 扫<b>穿上的</b>盔甲 0..3 + bauble 0..3 里
     * 的 {@code IRunicArmor} 算出，口袋当前<b>不可穿戴</b> ⇒ 不占容量。让口袋计入容量需要先可穿戴化
     * = <b>S11 的活</b>，本片★不在此处做任何暗示（本方法一个 {@code IRunicArmor} 都不碰）。
     */
    static boolean payShieldCycleFromPockets(EntityPlayer player, AspectList cost) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote || cost == null
            || cost.size() <= 0) {
            return false;
        }
        final Map<String, Integer> bill = new LinkedHashMap<String, Integer>();
        for (Aspect aspect : cost.getAspects()) {
            final String tag = tagOf(aspect);
            if (tag == null) {
                // 含非元始（或注册表认不出的）条目 ⇒ 容量表没这一行，整笔让给 TC
                return false;
            }
            final int points = costToPocketPoints(cost.getAmount(aspect));
            if (points <= 0) {
                return false;
            }
            final Integer prev = bill.get(tag);
            bill.put(tag, prev == null ? Integer.valueOf(points) : Integer.valueOf(prev.intValue() + points));
        }
        final ItemStack pocket = firstPocketCovering(player, bill);
        if (pocket == null) {
            return false;
        }
        // ★★R96 S9b：整笔出账<b>整体下沉</b>到存储层那一对原语（{@code canPayAll} / {@code payAll}），
        // 本方法只负责"哪些 aspect、换算成几点、扫哪些槽"。理由不是省事：护盾这条腿要的
        // 「缺一即整笔不扣 + 数不上就回滚」是<b>容量表自己的</b>判据，写在本类里就只有 mixin 侧看得见、
        // 离线套件一次都跑不到（S10 的 U-8 之所以只能登记成"留待裁定"就是这个原因）。收进
        // {@code PocketElementStore} 之后它是常驻侧<b>可测的一等判据</b>，★且与 S9a 那三条被动吃的是
        // 同一份存量读数（get / 白名单 / 上限全在一处）—— 这条就是"护盾那条腿的容量侧是否闭合"的答案。
        return writableStore(pocket).payAll(bill) > 0;
    }

    private static ItemStack firstPocketCovering(EntityPlayer player, Map<String, Integer> bill) {
        final IInventory baubles = safeBaubles(player);
        if (baubles != null) {
            for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
                final ItemStack stack = baubles.getStackInSlot(slot);
                if (covers(stack, bill)) {
                    return stack;
                }
            }
        }
        final ItemStack[] main = player.inventory == null ? null : player.inventory.mainInventory;
        if (main != null) {
            for (int slot = 0; slot < main.length; slot++) {
                final ItemStack stack = main[slot];
                if (covers(stack, bill)) {
                    return stack;
                }
            }
        }
        return null;
    }

    /**
     * 这只口袋是否付得起整笔 bill（★只读、不建档、不掏；判据本体在 {@code PocketElementStore#canPayAll}，
     * ★本类不抄第二遍"逐条比存量"）。
     * <p>
     * ★★<b>R96 S9b 修正</b>：旧写法在这里逐条问的是 {@link #roomFor}（「还能收多少」），而本方法的语义是
     * 「付得起吗」——消耗侧要问的是<b>存量</b>。两者在 500 上限下互为补数 ⇒ 拿错不是"稍严/稍宽"，
     * 是<b>方向反了</b>：一只空口袋的余量恒等于上限，"付得起"判成 true。旧实现靠末尾那句
     * {@code return paid > 0} 侥幸挡掉了"整只都空"这一格，但只要<b>一条够、一条不够</b>
     * （如 aer 空、terra 有 300），terra 那条就会被真掏走而整笔没付齐 ⇒ 护盾回充了、口袋只付了一半。
     * ★这就是 S10 报告 U-8 登的那格，且它<b>不是"理论不可达"而是已达</b>——预检从一开始就问错了那个数。
     */
    private static boolean covers(ItemStack pocket, Map<String, Integer> bill) {
        if (!mageActive(pocket)) {
            return false;
        }
        return PocketElementStore.readFrom(pocket.getTagCompound()).canPayAll(bill);
    }

    private static IInventory safeBaubles(EntityPlayer player) {
        try {
            return BaublesApi.getBaubles(player);
        } catch (Throwable t) {
            // Baubles 侧任何漂移都不允许成为护盾腿的崩溃源：退化成「只扫主背包」
            return null;
        }
    }
}
