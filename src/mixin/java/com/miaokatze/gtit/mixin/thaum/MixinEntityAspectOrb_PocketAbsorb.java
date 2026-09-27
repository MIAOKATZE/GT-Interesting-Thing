package com.miaokatze.gtit.mixin.thaum;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.entities.EntityAspectOrb;

/**
 * ★R96 S10 · P5：要素球<b>吸进口袋</b>的落地腿（与 P4 绑定，缺一必 CCE）。
 * <p>
 * <b>被改的原始行为</b>（TC 4.2.3.5 {@code thaumcraft/common/entities/EntityAspectOrb.java:196-208}
 * = {@code func_70100_b_(EntityPlayer)}）：
 * <ol>
 * <li>{@code :197} 服务端闸；</li>
 * <li>{@code :198} {@code slot = InventoryUtils.isWandInHotbarWithRoom(getAspect(), aspectValue, player)}；</li>
 * <li>{@code :199} 四道闸：{@code orbCooldown == 0}、{@code player.xpCooldown == 0}、
 * {@code getAspect().isPrimal()}、{@code slot >= 0}（⇒ <b>非原始要素球永不吸收</b>）；</li>
 * <li>{@code :200} {@code (ItemWandCasting) player.inventory.mainInventory[slot].getItem()} ←
 * <b>就是这条 {@code checkcast}</b>（生产 jar 偏移 63，全类唯一一处 {@code ItemWandCasting} 强转）；</li>
 * <li>{@code :201} {@code addVis(..., aspectValue, true)}、{@code :202} {@code xpCooldown = 2}、
 * {@code :203} 音效 {@code "random.orb"}、{@code :204} {@code setDead()}。</li>
 * </ol>
 * <b>本 mixin 的做法</b>：HEAD 注入，若这一笔属于口袋（判据与 P4 同一份，见
 * {@link PocketVisSupport}），就替玩家做完 {@code :202-204} 那三件事并 {@code ci.cancel()}，
 * 让含 {@code checkcast} 的原方法体<b>根本不执行</b>。这是本轮实测后唯一不给 CCE 的最小形状
 * （{@code @Redirect} 重定向不了 {@code checkcast}；{@code @ModifyVariable} 只能改 {@code slot}
 * 的值，改了反而更强转错对象）。
 * <p>
 * <b>为什么 {@code ci.cancel()} 而不是让原方法继续</b>：原方法体在 {@code :198} 会<b>再问一次</b>
 * {@code isWandInHotbarWithRoom} —— 那一次问的正是被我们 P4 改写过的版本，答案还是那个口袋槽号，
 * 于是 {@code :200} 直接对口袋栈做强转 ⇒ CCE。所以只要 P4 存在，P5 就必须把整个方法体取消掉。
 * <p>
 * <b>为什么不做 {@code @At("INVOKE")} 型 locator</b>：同 P4，Backhand 已包住这一句；且 HEAD 注入
 * 与它的 {@code @WrapOperation} 不是同一条指令（它包 {@code :198} 那个 {@code invokestatic}，
 * 我们在方法入口插入，互不覆盖）。若我们的 HEAD 注入取消了方法体，Backhand 那颗 wrap 就压根不参与。
 * <p>
 * <b>★与 TC 原生四道闸的关系（不接管、只借语义）</b>：{@code getAspect().isPrimal()} 这一条
 * 由 {@link PocketVisSupport#tagOf(Aspect)} 覆盖（元始 ∧ 6 条白名单<b>合取</b>）。
 * 拾取节流那一半：本腿只置 {@code player.xpCooldown = 2}（TC {@code :202} 逐字同值），
 * <b>不</b>碰 {@code orbCooldown}。理由不是省事，是实测：{@code orbCooldown} 在 TC 4.2.3.5 里
 * 只有三处出现——声明 {@code :26}（初值 0）、{@code :88-89} 的「{@code > 0} 才自减」、
 * {@code :199} 的读取，<b>全类没有任何一处把它写成非 0</b> ⇒ 那道闸恒真是死码，
 * 复刻它等于复刻一个常量，而 {@code :199} 本来就同时要求 {@code xpCooldown == 0}，
 * 我们置的正是它 ⇒ 「连吃两球」的节流与 TC 等价，无行为差。
 * <p>
 * 仅在 TC 已加载时由 {@code com.miaokatze.gtit.asm.GtitThaumLateMixinLoader} 条件施加。
 */
@Mixin(value = EntityAspectOrb.class, remap = false)
public abstract class MixinEntityAspectOrb_PocketAbsorb {

    /** TC 自有公开 getter（明文不混淆）⇒ {@code remap = false}。刻意不 shadow 私有的 {@code aspectValue} 字段。 */
    @Shadow(remap = false)
    public abstract Aspect getAspect();

    @Shadow(remap = false)
    public abstract int getAspectValue();

    /**
     * {@code onCollideWithPlayer} 是 {@code Entity} 声明的 MC 派生名（= {@code func_70100_b_}）
     * ⇒ {@code remap = true}。该映射有已知全绿构件背书：Backhand 的
     * {@code mixins.backhand.refmap.json} 里正是
     * {@code onCollideWithPlayer → Lthaumcraft/common/entities/EntityAspectOrb;func_70100_b_(Lnet/minecraft/entity/player/EntityPlayer;)V}
     * （本次直读该 refmap 所得，与同 jar 的 {@code onUpdate → func_70071_h_} 并列）。
     * <p>
     * 目标返回 void ⇒ 操 {@code CallbackInfo}（判据 2 只约束<b>非 void</b>目标）。
     */
    @Inject(method = "onCollideWithPlayer", at = @At("HEAD"), cancellable = true, remap = true)
    private void gtit$absorbOrbIntoPocket(EntityPlayer player, CallbackInfo ci) {
        // (Entity)(Object)this：口袋侧逻辑只需要 Entity 的公开面（worldObj / playSound / setDead），
        // 合并后的实例本来就是 EntityAspectOrb，强转成立；这样就不必为 MC 成员再开 shadow，也不必 extends Entity
        if (PocketVisSupport.absorbIntoPocket(
            (Entity) (Object) this,
            player,
            this.getAspect(),
            Integer.valueOf(this.getAspectValue())
                .intValue())) {
            ci.cancel();
        }
    }
}
