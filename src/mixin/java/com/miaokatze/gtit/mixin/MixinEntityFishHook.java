package com.miaokatze.gtit.mixin;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.config.Config;

/**
 * 服务端 Mixin：玩家每次成功钓到鱼时，服务端掷概率额外附赠猫猫币/闪烁猫猫币。
 * <p>
 * <b>背景</b>：猫猫币原先通过 FishingHooks.addFish 进入全局钓鱼战利品表，
 * GT 工业鱼塘（FishPondRecipes 反射读该表）与 GT++ 鱼陷阱可复用该表批量刷币；
 * 现已从钓鱼表移除注册，改由此 Mixin 只对真人玩家的钓鱼结算掷概率发放，
 * 机器渔场天然不再产出。
 * <p>
 * <b>注入点（有据）</b>：1.7.10 收竿结算方法为 EntityFishHook.func_146034_e()
 * （MCP 名 catchFish，本项目 stable_12 映射未覆盖该类故保留 SRG 名）：
 * <ul>
 * <li>field_146043_c（caughtEntity）!= null 时为"仅拉拽实体"分支（源码 530-541 行）；</li>
 * <li>否则 field_146045_ax（ticksCatchable）&gt; 0 时为"成功钓到鱼"分支（源码 542-556 行），
 * 该分支内以 this.func_146033_f()（getFishingResult， FishingHooks 掷鱼类掉落）生成掉落物
 * 并生成经验球。</li>
 * </ul>
 * 本 Mixin 注入 func_146034_e 内对 func_146033_f 的 INVOKE 调用处
 * （build/rfg/minecraft-src/java/net/minecraft/entity/projectile/EntityFishHook.java:544），
 * 因此只有"成功钓到鱼类掉落"路径会触发，拉拽实体路径天然不会进入；
 * 客户端在 func_146034_e 开头即 return 0（源码 522-525 行），此处再显式判 isRemote 双保险。
 * <p>
 * <b>发放范式</b>对齐 StarterGift.onItemRightClick：inventory.addItemStackToInventory 优先，
 * 背包满则 EntityItem 掉落到玩家位置。只叠加发放，不取消/修改原方法行为（非 cancellable）。
 */
@Mixin(EntityFishHook.class)
public class MixinEntityFishHook {

    /**
     * 钓鱼者（原版字段 angler，SRG 名；本项目映射数据未覆盖 EntityFishHook）
     */
    @Shadow
    public EntityPlayer field_146042_b;

    @Inject(
        method = "func_146034_e",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/projectile/EntityFishHook;func_146033_f()Lnet/minecraft/item/ItemStack;"))
    private void gtit$onFishLootRolled(CallbackInfoReturnable<Integer> cir) {
        EntityPlayer angler = this.field_146042_b;
        // 只对服务端生效
        if (angler == null || angler.worldObj.isRemote) {
            return;
        }

        double shimmeringChance = Config.fishingShimmeringNekoCoinChance;
        double normalChance = Config.fishingNekoCoinChance;
        // 两项概率均为 0 时完全零开销路径
        if (shimmeringChance <= 0.0D && normalChance <= 0.0D) {
            return;
        }

        // 每次成功钓鱼先掷闪烁猫猫币、未中再掷普通猫猫币（两项独立配置，单位为百分比 0-100）
        if (shimmeringChance > 0.0D && angler.worldObj.rand.nextDouble() * 100.0D < shimmeringChance) {
            giveNekoCoin(angler, GTITItemList.ShimmeringNekoCoin);
        } else if (normalChance > 0.0D && angler.worldObj.rand.nextDouble() * 100.0D < normalChance) {
            giveNekoCoin(angler, GTITItemList.NekoCoin);
        }
    }

    /**
     * 发放范式对齐 StarterGift.onItemRightClick：背包优先，满了掉落到玩家位置
     */
    private static void giveNekoCoin(EntityPlayer angler, GTITItemList item) {
        ItemStack stack = item.get(1);
        if (stack == null) {
            return;
        }
        if (!angler.inventory.addItemStackToInventory(stack)) {
            angler.worldObj
                .spawnEntityInWorld(new EntityItem(angler.worldObj, angler.posX, angler.posY + 1, angler.posZ, stack));
        }
    }
}
