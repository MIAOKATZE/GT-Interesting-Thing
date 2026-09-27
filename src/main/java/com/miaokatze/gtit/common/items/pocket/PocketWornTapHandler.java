package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.miaokatze.gtit.main.GTInterestingThing;

import baubles.api.BaublesApi;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * ★R96 S11（需求 8 第二件）：<b>穿戴后</b>潜行 + 空手右击 GT 机器 ⇒ 抽机器流体进口袋。
 * <p>
 * <b>为什么这是一条纯服务端的腿</b>（裁定口径：AUQ / 计划 P-10，★不用 alt 修饰键）：
 * {@code PlayerInteractEvent} 的 {@code RIGHT_CLICK_BLOCK} 在服务端由
 * {@code ItemInWorldManager.activateBlockOrUseItem}（{@code :386}）发射，自带 x/y/z/face/world
 * 与 {@code getTileEntity} 的一切条件；而 reach 与 {@code isBlockProtected} 在它<b>上游</b>
 * （{@code NetHandlerPlayServer:588-590}）已经查过 ⇒ 走这条腿<b>自动继承</b>这两道防护。
 * ★反过来若做成"客户端轮询 + 自发包"，这两道防护就被旁路，还要多养一条真相 —— 那是本片的禁止项，
 * 仓内也确实<b>没有</b>任何为此新写的 C2S 包（由门禁钉住）。
 * <p>
 * <b>与既有那条腿的关系（★一条都不改）</b>：不戴口袋时抽液仍走
 * {@code ItemNekoDimensionPocket#onItemUseFirst}（潜行 + 手持右击），那两段的源码文本
 * （潜行闸与三个 {@code return false}）逐字不变，由 ★R96-S11 门禁钉住。两条腿<b>互斥</b>：
 * 本腿的硬门是 {@code getCurrentEquippedItem() == null}（空手），手持口袋时本腿一个字都不做。
 * <p>
 * <b>空手这道硬门不是保守，是避撞</b>：GT 的机器方块对"手持螺丝刀/扳手一类工具"的右击有自己的
 * 白名单语义（{@code BlockMachines.java:412-419}），潜行 + 手持工具是玩家配置机器的常规手势；
 * 本腿若放过任何手持物，就会把那些手势按下去顺手掏空机器。用例钉这一条。
 * <p>
 * <b>{@code setCanceled(true)} 的位置</b>：只在<b>全部</b>守卫通过<b>且</b> {@link PocketWorldFluidTap#tap}
 * 真搬动了流体（返回 true）之后才取消。取消带来的唯一连带是服务端回一枚 {@code S23PacketBlockChange}
 * （原版 {@code ItemInWorldManager:387-391} 的既定行为，无副作用）；"没搬动"的结局一律不取消，
 * 机器 GUI 与方块交互原样可达。判因回执仍由 {@code PocketWorldFluidTap} 发（{@code gtit.pocket.world.} 键族，
 * ★本文件不发聊天，也不新增聊天键）。
 */
public final class PocketWornTapHandler {

    /** 单例注册（口径同 {@code PocketLifecycleHandler.INSTANCE}：由 {@code CommonProxy} 显式挂上 Forge 总线）。 */
    public static final PocketWornTapHandler INSTANCE = new PocketWornTapHandler();

    private PocketWornTapHandler() {}

    /**
     * 服务端 {@code RIGHT_CLICK_BLOCK} 腿。守卫顺序即判读顺序，★每一道都在<b>任何写入之前</b>：
     * 动作 → 玩家 → 侧 → 空手 → 潜行 → 穿戴的口袋 → 目标是 GT 机器 → 真搬动了流体。
     */
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final EntityPlayer player = event.entityPlayer;
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            // ★只跑服务端：客户端那一遍读到的是同步镜像，抽液是服务端权威账
            return;
        }
        if (player.getCurrentEquippedItem() != null) {
            // ★空手硬门（见类 javadoc：与手持工具的既有语义避撞）
            return;
        }
        if (!player.isSneaking()) {
            return;
        }
        final ItemStack pocket = findWorn(player);
        if (pocket == null) {
            return;
        }
        final TileEntity at = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(at instanceof BaseMetaTileEntity tile)) {
            // 不是 GT 机器 ⇒ 完整放行原方块交互（★与既有那条腿同一个"非机器不拦"的口径）
            return;
        }
        if (!PocketWorldFluidTap.tap(player, pocket, tile)) {
            return;
        }
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------ bauble 栏的遍历（★面板与抽液共用这一份）

    /**
     * 取该玩家的 bauble 栏；★Baubles 侧任何漂移都不允许成为崩溃源 ⇒ 拿不到就返回 {@code null}
     * （调用方必须判空，这是门禁与用例都点名过的那道守卫）。
     */
    public static IInventory safeBaubles(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            return BaublesApi.getBaubles(player);
        } catch (Throwable t) {
            GTInterestingThing.LOG.warn("[pocket] 取不到 bauble 栏 ⇒ 按「没穿戴」处理（不崩，也不抽液）", t);
            return null;
        }
    }

    /**
     * 找到穿在该玩家饰品栏里的那一枚口袋（★扫描面 = 全部槽位，不写死 0..3：本仓自己的读法与
     * SalisArcana 那类放宽 bauble 栏的 mod 共存时，写死上界会把"确实穿着"读成"没穿"）。
     *
     * @return 那一枚栈，没穿则 {@code null}
     */
    public static ItemStack findWorn(EntityPlayer player) {
        final IInventory baubles = safeBaubles(player);
        if (baubles == null) {
            return null;
        }
        for (int i = 0; i < baubles.getSizeInventory(); i++) {
            final ItemStack at = baubles.getStackInSlot(i);
            if (at == null) {
                continue;
            }
            if (at.getItem() instanceof ItemNekoDimensionPocket) {
                return at;
            }
        }
        return null;
    }

    /** 同 {@link #findWorn}，但要的是<b>槽号</b>（客户端开饰品背包那一路要用它当 {@code openFromBaubles} 的索引）。 */
    public static int findWornSlot(EntityPlayer player) {
        final IInventory baubles = safeBaubles(player);
        if (baubles == null) {
            return -1;
        }
        for (int i = 0; i < baubles.getSizeInventory(); i++) {
            final ItemStack at = baubles.getStackInSlot(i);
            if (at != null && at.getItem() instanceof ItemNekoDimensionPocket) {
                return i;
            }
        }
        return -1;
    }
}
