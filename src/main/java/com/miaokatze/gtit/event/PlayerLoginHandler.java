package com.miaokatze.gtit.event;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.items.rings.BaseRing;
import com.miaokatze.gtit.main.GTInterestingThing;

import baubles.api.BaublesApi;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * 玩家登录事件处理器
 * 首次进入世界时自动发放新手宝箱
 * 登录时刷新已装备指环的效果（防止断线后buff丢失）
 */
public class PlayerLoginHandler {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        NBTTagCompound playerData = player.getEntityData();
        NBTTagCompound persisted = playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);

        if (!persisted.getBoolean("gtit_received_starter_gift")) {
            persisted.setBoolean("gtit_received_starter_gift", true);
            ItemStack gift = GTITItemList.StarterGift.get(1);
            if (gift != null) {
                if (!player.inventory.addItemStackToInventory(gift)) {
                    player.worldObj.spawnEntityInWorld(
                        new EntityItem(player.worldObj, player.posX, player.posY + 1, player.posZ, gift));
                }
            }
        }

        // 刷新已装备指环的效果（防止断线后buff/属性丢失）
        refreshEquippedRings(player);
    }

    /**
     * 扫描玩家 Baubles 栏位，对已装备的指环调用 onEquipped 刷新效果。
     * 解决玩家重新登录后 buff 指环效果丢失、磐躯指环生命修饰器未应用、御风指环飞行能力未恢复等问题。
     */
    private void refreshEquippedRings(EntityPlayer player) {
        try {
            IInventory baubles = BaublesApi.getBaubles(player);
            for (int i = 0; i < baubles.getSizeInventory(); i++) {
                ItemStack stack = baubles.getStackInSlot(i);
                if (stack != null && stack.getItem() instanceof BaseRing ring) {
                    ring.onEquipped(stack, player);
                }
            }
        } catch (Exception e) {
            // 登录刷新指环效果失败会导致 buff/属性/飞行能力丢失（用户可见），记录便于诊断
            GTInterestingThing.LOG.warn("[GTIT] 登录时刷新已装备指环效果失败（player={}）", player.getCommandSenderName(), e);
        }
    }
}
