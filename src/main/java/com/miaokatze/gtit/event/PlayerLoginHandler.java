package com.miaokatze.gtit.event;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.common.api.enums.GTITItemList;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * 玩家登录事件处理器
 * 首次进入世界时自动发放新手宝箱
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
    }
}
