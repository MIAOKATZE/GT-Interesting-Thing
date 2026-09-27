package com.miaokatze.gtit.mixin.thaum;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.miaokatze.gtit.crossmod.taum.PocketVisSupport;

import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.wands.WandManager;
import thaumcraft.common.lib.events.EventHandlerRunic;

/**
 * ★R96 S10 · 第三枚护盾腿（G-1 扩权项）：让<b>符文护盾的回充节拍可以直接掏口袋元素容量</b>。
 *
 * <h2>被改的原始行为（file:line 依据）</h2>
 * TC 4.2.3.5 {@code thaumcraft/common/lib/events/EventHandlerRunic.java:128}（方法体 =
 * {@code :50 livingTick(LivingUpdateEvent)}）里那条 {@code else if} 链：
 * {@code charge < 上限 && nextCycle < now && WandManager.consumeVisFromInventory(player,
 * (new AspectList()).add(Aspect.AIR, Config.shieldCost).add(Aspect.EARTH, Config.shieldCost))}
 * ⇒ 只有这一个 {@code invokestatic} 为真，护盾才 {@code ++charge}。成本 {@code Config.shieldCost = 50}
 * （{@code config/Config.java:104}），节拍 {@code shieldRecharge = 2000} ms（{@code :102}）。
 * 而 {@code WandManager.consumeVisFromInventory}（{@code wands/WandManager.java:93-117}）只有两支：
 * {@code :97-104} 饰品栏 0..3 里的 {@code ItemAmuletVis}、{@code :106-114} 主背包倒序里的
 * {@code ItemWandCasting} ⇒ 口袋两支都不命中，返回 {@code false}，护盾永远回不满。
 *
 * <h2>为什么落点选 {@code EventHandlerRunic}，不选 {@code WandManager}</h2>
 * {@code WandManager.consumeVisFromInventory} 的<b>字面直调点数</b>本次用生产 jar
 * {@code javap -c} 全量实测（{@code build/s10b}）：
 * <ul>
 * <li>{@code EventHandlerRunic.livingTick} 偏移 861 ⇒ <b>1</b> 处（= 护盾回充，本片目标）；</li>
 * <li>{@code EventHandlerEntity} ⇒ <b>2</b> 处（{@code :224}/{@code :227}，法杖/护身符的<b>自动修复</b>抽能）；</li>
 * <li>{@code InternalMethodHandler} ⇒ <b>1</b> 处（{@code :59-60}），它经
 * {@code IInternalMethodHandler:27} 被 {@code ThaumcraftApiHelper:222} 的
 * {@code invokeinterface} 抬成<b>公开 API</b> ⇒ 全生态任何 mod 都能调。</li>
 * </ul>
 * 注在 {@code WandManager} 上（哪怕只在原路返回 false 的分支兜底）等于把「口袋是 vis 备付源」
 * 这件事同时推给<b>自动修复腿</b>与<b>别人 mod 的抽能语义</b>——那三处不是本片要的东西，
 * 一旦发出去就收不回来。注在 {@code EventHandlerRunic} 里那一条指令上，副作用面被压到
 * <b>只剩护盾回充一条腿</b>：修复腿与 {@code ThaumcraftApiHelper} 通道的字节码逐字不变。
 *
 * <h2>与既有注入的关系（离线读数，不是推测）</h2>
 * {@code @Redirect} 会独占它所钉的那条指令，所以必须先确认没人钉过。对 RC-1 实例 204 个 jar 做了两遍扫描：
 * <ul>
 * <li>全实例 47 份 {@code *.refmap.json} 里，符号 {@code consumeVisFromInventory} 与 owner
 * {@code WandManager} 的条目数都是 <b>0</b>；</li>
 * <li>按类名扫 mixin 类，命中 {@code WandManager} / {@code Runic} 的只有三枚：SalisArcana
 * {@code MixinWandManager_ExtendedBaublesSupport}（{@code javap -v} 直读注解 =
 * {@code @ModifyConstant(method="getTotalVisDiscount", constant=@Constant(intValue=4, ordinal=0))}，
 * <b>不碰</b> {@code consumeVisFromInventory}）、SalisArcana
 * {@code MixinEventHandlerRunic_ExtendedBaublesSupport}（{@code @ModifyConstant} intValue=4
 * ordinal=1，改的是 {@code :63}/{@code :72} 那两条 {@code < 4} 扫描上界，<b>不是</b>偏移 861 的
 * {@code invokestatic}）、Hodgepodge {@code MixinTileWandPedestal_VisDuplication}（宿主是基座）。</li>
 * </ul>
 * ⇒ 本枚钉的那一条指令在本包内无人争抢。<b>★但这只证到指令不重叠，不证两枚 mixin 在同一 tick 里的
 * 施加顺序</b>；SalisArcana 把 {@code EventHandlerRunic} 的 bauble 扫描上界从 4 放宽（{@code getSizeInventory()}），
 * 与本腿正交（一个改容量读数、一个改掏账来源），顺序风险仍列入未验项。
 *
 * <h2>★顺序硬要求「先原路、后口袋」由本 handler 逐字实现</h2>
 * handler 第一件事就是原样调一次 {@code WandManager.consumeVisFromInventory}，真出了就直接返回真；
 * 只有原路返回假才问口袋。这条顺序不是风格问题：原路成功时它已经改过法杖/魔力石的 NBT，
 * 再掏一遍口袋就是双付。而原路返回假是<b>零副作用</b>的——{@code ItemWandCasting.consumeAllVis}
 * （{@code :317-356}）在 {@code :335-340} 先把每条 aspect 与 {@code getVis} 比一遍，任何一条不够就
 * {@code :338 return false}，写入循环在 {@code :342} 之后 ⇒ 走到口袋这一支时谁的账本都没动过。
 *
 * <h2>量纲与全有全无</h2>
 * 传进来的 {@code AspectList} 是 <b>TC 存储刻度</b>（{@code shieldCost = 50} 直接与法杖 NBT 的
 * ×100 刻度相比，不除 100），故换算由
 * {@link PocketVisSupport#payShieldCycleFromPockets} 承担（{@code ceil(stored/100)}，
 * 即 {@code addVis} 那次 ×100 的逆运算）。默认配置下一拍 = Air 1 点 + Earth 1 点。
 * 该原语要求 bill 里<b>每一条</b>都在 6 条元始白名单内且<b>同一只</b>口袋付得起整笔，
 * 否则整笔不付（与 {@code consumeAllVis} 的「任一 aspect 不够就整笔 false」同形）。
 *
 * <h2>★本片只做了「消耗」这一半</h2>
 * 护盾<b>容量上限</b>由 {@code EventHandlerRunic:63-64}（盔甲 0..3 的 {@code IRunicArmor}）与
 * {@code :72-73}（bauble 0..3 的 {@code IRunicArmor}）算出 ⇒ 口袋<b>穿不上就等于不占容量</b>，
 * 本腿只是让它当「油箱」而不是「油箱大小」。让口袋计入容量需要可穿戴化，那是 S11 的活，
 * 已在报告里交叉登记。
 *
 * <p>
 * 仅在 TC 已加载时由 {@code com.miaokatze.gtit.asm.GtitThaumLateMixinLoader} 条件施加。
 * 目标是 TC 自有明文方法（{@code livingTick} / {@code consumeVisFromInventory} 均不混淆，
 * 生产 jar {@code javap} 实证）⇒ 类级 {@code remap = false}，且本枚<b>不需要</b> refmap 条目。
 */
@Mixin(value = EventHandlerRunic.class, remap = false)
public class MixinEventHandlerRunic_PocketShield {

    /**
     * 重定向 {@code livingTick} 里那唯一一处 {@code WandManager.consumeVisFromInventory}。
     * <p>
     * ★非 void 目标（{@code ()Z}）在 {@code @Redirect} 下的形状与 {@code @Inject} 不同：
     * 没有 {@code CallbackInfoReturnable}，<b>handler 自己的返回类型</b>必须等于被重定向调用的
     * 返回类型（这里 {@code boolean}），形参必须等于该调用的实参表（{@code EntityPlayer},
     * {@code AspectList}）。全量复核见报告判据 4。
     */
    @Redirect(
        method = "livingTick",
        at = @At(
            value = "INVOKE",
            target = "Lthaumcraft/common/items/wands/WandManager;consumeVisFromInventory(Lnet/minecraft/entity/player/EntityPlayer;Lthaumcraft/api/aspects/AspectList;)Z"))
    private boolean gtit$runicShieldMayPayFromPocket(EntityPlayer player, AspectList cost) {
        // 原路（bauble 栏魔力石 / 主背包法杖）先跑，逐字保持 TC 语义
        if (WandManager.consumeVisFromInventory(player, cost)) {
            return true;
        }
        // 原路整笔付不起 ⇒ 才问口袋（服务端独占，判据体里已带 isRemote 闸）
        return PocketVisSupport.payShieldCycleFromPockets(player, cost);
    }
}
