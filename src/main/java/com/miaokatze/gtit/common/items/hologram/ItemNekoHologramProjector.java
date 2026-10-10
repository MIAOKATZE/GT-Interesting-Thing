package com.miaokatze.gtit.common.items.hologram;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.item.ItemConstructableTrigger;
import com.miaokatze.gtit.common.util.GTITUtils;
import com.miaokatze.gtit.hologram.HologramService;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.register.CreativeTabManager;

/** 猫猫全息投影仪；扫描与会话状态统一由服务端管理。 */
public class ItemNekoHologramProjector extends ItemConstructableTrigger {

    private static final String ID = "neko_hologram_projector";

    public ItemNekoHologramProjector() {
        setUnlocalizedName(ID);
        setTextureName(GTInterestingThing.MODID + ":" + ID);
        setMaxStackSize(1);
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
    }

    public static boolean isProjector(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemNekoHologramProjector;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        // 客户端放行才能发送带坐标的 C08；服务端消费交互，避免打开控制器原 GUI。
        if (world.isRemote) return false;
        if (player instanceof EntityPlayerMP serverPlayer) {
            HologramService.open(serverPlayer, x, y, z, side);
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player instanceof EntityPlayerMP serverPlayer) {
            // 同次方块右击补发的 side=255 由服务端会话入口去重。
            HologramService.openAir(serverPlayer);
        }
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean showAdvanced) {
        for (int i = 0;; i++) {
            String key = "item." + ID + ".tooltip." + i;
            String line = StatCollector.translateToLocal(key);
            if (line.equals(key)) break;
            tooltip.add(EnumChatFormatting.AQUA + line);
        }
        int main = stack.hasTagCompound() ? Math.max(
            1,
            stack.getTagCompound()
                .getInteger("gtitHologramMain"))
            : 1;
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtit.hologram.main") + ": " + main);
        ChannelDataAccessor.iterateChannelData(stack)
            .limit(8)
            .forEach(entry -> tooltip.add(EnumChatFormatting.GRAY + entry.getKey() + ": " + entry.getValue()));
        tooltip.add(GTITUtils.getAddedByLine());
    }
}
