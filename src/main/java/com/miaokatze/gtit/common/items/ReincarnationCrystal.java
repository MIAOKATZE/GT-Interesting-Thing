package com.miaokatze.gtit.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.miaokatze.gtit.common.util.GTITUtils;
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
 * v1.8.2→v1.8.3 迁移说明：原 MUI2 链路在集成服环境抛 IllegalArgumentException 崩溃，
 * 改 Forge 原版 IGuiHandler 链路后注册收束在 {@code ClientProxy.init} 单线程单侧执行。
 * <p>
 * 更正（本仓 v1.9 复查）：此处曾记录的根因「onItemRightClick 双线程各执行一次、服务端线程
 * 第二次执行 MUI2 工厂注册」是误诊。真实机制是单线程内对同一工厂实例注册两次——
 * {@code GuiFactories.createSimple} 的构造器已自注册，旧 ReincarnationGuiOpener 又手写了一次
 * {@code registerFactory}，首次右击即抛 GuiManager 的 dup-IAE（客户端线程从未触达注册代码）。
 * 证据：plan/_taskpack/ultra-07-mui2-open-crash.md §1.2、§2.1-§2.2。
 * MUI2 本身对物品 GUI 可用，故本类的 IGuiHandler 形态是「可用的历史选择」而非「MUI2 不可用的结论」；
 * 新物品 GUI 的选型见 plan/_taskpack/decision-ledger.md R20/R21。
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
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean showAdvanced) {
        tooltip.add(GTITUtils.getAddedByLine());
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
