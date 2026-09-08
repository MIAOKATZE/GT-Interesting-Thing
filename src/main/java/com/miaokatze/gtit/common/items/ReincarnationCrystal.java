package com.miaokatze.gtit.common.items;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.register.CreativeTabManager;

/**
 * 轮回水晶 / Reincarnation Crystal
 * 周目系统核心物品：猫猫币与闪烁猫猫币环绕钻石合成而来
 * 物理专用服务器上周目系统整体拒绝注册（门控见 ItemRegistrar / GTITRecipes）
 * <p>
 * 右击打开周目 GUI（v1.9.0 S4 接线）：仅服务端分支（MUI2 同步 GUI 约束：
 * "Synced GUIs must be opened from server side"，客户端镜像由 OpenGuiPacket 自动完成），
 * 经 sided proxy（{@link GTInterestingThing#proxy}）分发——common 物品类零
 * client/gui import（类污染红线），GUI 打开器仅在 ClientProxy 的
 * {@code openReincarnationGui} 覆写内触达。
 */
public class ReincarnationCrystal extends Item {

    public ReincarnationCrystal() {
        super();
        setUnlocalizedName("reincarnation_crystal");
        setTextureName("gtit:reincarnation_crystal");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setMaxStackSize(64);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            // 物理客户端（集成服线程）→ ClientProxy 覆写打开 MUI2 周目 GUI；
            // 空实现（专用服不可达，周目系统整体门控）
            GTInterestingThing.proxy.openReincarnationGui(player);
        }
        return stack;
    }
}
