package com.miaokatze.gtit.mixin.thaum;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtit.crossmod.taum.PocketVisSupport;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.lib.utils.InventoryUtils;

/**
 * ★R96 S10 · P4：让要素球<b>把口袋认作容器</b>（选主 + 「装得下这一整笔吗」的判据）。
 * <p>
 * <b>被改的原始行为</b>（TC 4.2.3.5 {@code thaumcraft/common/lib/utils/InventoryUtils.java:455-473}）：
 * {@code public static int isWandInHotbarWithRoom(Aspect, int, EntityPlayer)} 只扫快捷栏
 * {@code InventoryPlayer.getHotbarSize()} = 9 格里 {@code instanceof ItemWandCasting} 的栈，
 * 用 {@code wand.addVis(stack, aspect, amount, false) < amount} 做<b>整笔</b>能装下的预检，
 * 命中返槽号、否则 {@code :469} 返 -1。全 TC 只有两个调用方（本次直读生产 jar 字节码：
 * 引用该符号的类共 2 个 = {@code InventoryUtils} 自身 + {@code EntityAspectOrb}；
 * {@code EntityAspectOrb} 里 {@code invokestatic} 计数 = <b>2</b>，分别是
 * {@code func_70071_h_}(onUpdate) 偏移 338 与 {@code func_70100_b_}(onCollideWithPlayer) 偏移 19）。
 * <p>
 * <b>为什么注本体、不用 {@code @At("INVOKE")} locator</b>（{@code r96-plan.md} §5 S10 P4 的处置）：
 * backhand-1.8.15 的 {@code xonin/backhand/mixins/late/thaumcraft/MixinEntityAspectOrb} 已经把
 * <b>上面那两个调用点</b>各包了一次（{@code javap -v} 直读：
 * {@code @WrapOperation(method = ["onUpdate", "onCollideWithPlayer"],
 * at = @At("INVOKE", target = "Lthaumcraft/common/lib/utils/InventoryUtils;isWandInHotbarWithRoom(…"))}）。
 * 我们若也按 INVOKE locator 去抢同一条指令，抢到的那一枚之外会 {@code InvalidInjectionException}。
 * 注本体的 HEAD 就不与任何 locator 争同一条指令。
 * <p>
 * ★<b>本次新拿到的证据（把一条「真机未证」降级成源码级结论）</b>：Backhand 那个
 * {@code @WrapOperation} 的 handler {@code backhand$includeOffhandWand} 的字节码<b>第一件事</b>就是
 * {@code Operation.call(aspect, amount, player)}（偏移 0..21：{@code invokeinterface Operation.call}），
 * 只有返回值 {@code < 0} 才继续查副手。也就是说它<b>逐字委托回原方法</b> ⇒
 * 装了 Backhand 的实例上，本 mixin（注入在原方法体里）<b>照样被走到</b>，口袋不会被球这一侧绕过。
 * 这条推翻的是 {@code r96-ret10.md} §5.2 与 {@code r96-plan.md} §8 V-7 里
 * 「其 handler 是否内部委托回 {@code isWandInHotbarWithRoom} 未证」那一句（当时只看了常量池）。
 * ⚠ 但「两枚 mixin 在同一次 tick 里的实际施加顺序」仍属真机项，不据此宣称已实测。
 * <p>
 * ★P4 与 P5 <b>必须同轮</b>：{@code EntityAspectOrb.java:200} 拿本方法的返回值直接做
 * {@code checkcast ItemWandCasting}（生产 jar 里偏移 63 就是那条 {@code checkcast}，
 * 全类唯一），只做 P4 ⇒ 玩家走近球时抛 CCE。
 * <p>
 * 返回值口径：命中口袋就返回它的快捷栏槽号；不命中<b>不设返回值</b>（TC 原生 9 格法杖扫描照跑）。
 * {@code com.miaokatze.gtit.crossmod.taum.PocketVisSupport}（★R97 S1 起住 crossmod/taum，非 mixin 包）
 * 是 P4/P5 共用的唯一判据体（两侧答案必须同瞬时一致，理由见该类注释）。
 * <p>
 * 仅在 TC 已加载时由 {@code com.miaokatze.gtit.asm.GtitThaumLateMixinLoader} 条件施加。
 */
@Mixin(value = InventoryUtils.class, remap = false)
public class MixinInventoryUtils_PocketHotbar {

    /**
     * {@code isWandInHotbarWithRoom} 是 TC 自有静态方法（明文不混淆 ⇒ 类级 {@code remap = false} 正确，
     * 且 RC-1 全实例 refmap 里该符号条目数 = 0，与本判断互相印证）。
     * ★非 void 目标 ⇒ 回调形参 {@code CallbackInfoReturnable<Integer>}（返回值语义「槽号」，
     * 用 {@code CallbackInfo} 写它是构建期合法、运行期 FATAL 的错法）。
     * 目标是静态方法 ⇒ handler 也静态（同 SalisArcana 对静态目标的 {@code @WrapOperation} 写法）。
     */
    @Inject(method = "isWandInHotbarWithRoom", at = @At("HEAD"), cancellable = true)
    private static void gtit$pocketCountsAsContainer(Aspect aspect, int amount, EntityPlayer player,
        CallbackInfoReturnable<Integer> cir) {
        final int slot = PocketVisSupport.hotbarPocketSlotWithRoom(player, aspect, amount);
        if (slot >= 0) {
            cir.setReturnValue(Integer.valueOf(slot));
        }
    }
}
