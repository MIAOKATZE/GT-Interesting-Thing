package com.miaokatze.gtit.common.items.hologram;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.item.ItemConstructableTrigger;
import com.miaokatze.gtit.common.util.GTITUtils;
import com.miaokatze.gtit.hologram.HologramNetwork;
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
    public boolean hasEffect(ItemStack stack, int pass) {
        return stack.hasTagCompound() && stack.getTagCompound()
            .getBoolean("gtitHologramAreaSelecting");
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player) {
        return stack.hasTagCompound() && stack.getTagCompound()
            .getBoolean("gtitHologramAreaSelecting");
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.onUpdate(stack, world, entity, slot, selected);
        if (!world.isRemote && entity instanceof EntityPlayer
            && stack.hasTagCompound()
            && stack.getTagCompound()
                .getBoolean("gtitHologramAreaSelecting")
            && !HologramService.isAreaSelecting((EntityPlayer) entity, stack)) {
            stack.getTagCompound()
                .removeTag("gtitHologramAreaSelecting");
            ((EntityPlayer) entity).inventory.markDirty();
        }
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        // 使用独立请求携带客户端修饰键，消费原交互，避免控制器 GUI 与空气补发双触发。
        if (world.isRemote) HologramNetwork.clientUse(x, y, z, side, false);
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) HologramNetwork.clientUse(0, 0, 0, 255, true);
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
