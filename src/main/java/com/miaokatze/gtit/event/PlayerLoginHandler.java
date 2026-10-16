package com.miaokatze.gtit.event;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.items.rings.BaseRing;
import com.miaokatze.gtit.gift.GiftService;

import baubles.api.BaublesApi;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * 玩家登录事件处理器
 * 首次进入世界时自动发放新手宝箱
 * 登录时刷新已装备指环的效果（防止断线后buff丢失）
 */
public class PlayerLoginHandler {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        if (player instanceof EntityPlayerMP serverPlayer) GiftService.INSTANCE.onLogin(serverPlayer);

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
            LOG.warn("[GTIT] 登录时刷新已装备指环效果失败（player={}）", player.getCommandSenderName(), e);
        }
    }
}
