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
 * 右击打开周目 GUI（v1.8.3 链路）：仅服务端分支（原版 GUI 语义：服务端
 * {@code EntityPlayer.openGui} → FML → {@code ReincarnationGuiHandler}，双端各建
 * Container/GuiContainer，客户端由 FML OpenGuiPacket 自动镜像），
 * 经 sided proxy（{@link GTInterestingThing#proxy}）分发——common 物品类零
 * client/gui import（类污染红线），GUI 打开仅在 ClientProxy 的
 * {@code openReincarnationGui} 覆写内触达。
 * <p>
 * v1.8.2→v1.8.3 迁移说明：原 v1.9.0 MUI2 链路在集成服环境 onItemRightClick 双线程
 * 各执行一次，服务端线程第二次执行 MUI2 工厂注册（"gtit:reincarnation_gui"）
 * 抛 IllegalArgumentException 崩溃；改 Forge 原版 IGuiHandler 链路后注册收束在
 * {@code ClientProxy.init} 单线程单侧执行，根因消除。
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
            // 物理客户端（集成服线程）→ ClientProxy 覆写经 EntityPlayer.openGui 打开周目 GUI；
            // 空实现（专用服不可达，周目系统整体门控）
            GTInterestingThing.proxy.openReincarnationGui(player);
        }
        return stack;
    }
}
