package com.miaokatze.gtit.common.items.pocket.mage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketElementStore;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceStore;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;

/**
 * 魔法使被动 ③「<b>源质转换</b>」的 tick 宿主本体（R96 S9a）：把口袋源质盘里的 6 种 primal
 * 折成元素容量，<b>1 元素/秒、6 种同批并行</b>。
 * <p>
 * <b>为什么这条是"第三条路"</b>（取证 {@code r96-ret4.md} §5.4）：源质离开口袋现有两条正门 ——
 * 取瓶（{@code ESSENCE_OUT_UNIT_POINTS} 的整瓶粒度）与通道搬运（{@code ESSENCE_CHANNEL_UNIT_POINTS}=1 点档），
 * 两条都面向<b>外部容器/元件</b>。本类既不出瓶也不开通道，只在<b>同一枚栈的两张表之间</b>搬
 * （{@code ess → elem}），所以两档粒度都不适用，速率由本类自己的单源
 * {@link PocketConstants#MAGE_TRANSMUTE_POINTS_PER_BATCH} × {@link PocketConstants#MAGE_SECOND_INTERVAL_TICKS}
 * 决定 = 每 tag 每秒 1 点。★<b>不</b>复用取瓶的"整瓶"语义做逐点入账（R96 计划 §5 S9 禁止项）。
 * <p>
 * <b>逐 tag 原子 + 同口径预检</b>（验收 4 在本腿上的形状）：每个 tag 都先问
 * {@code ess.get(tag) >= 本批量} 与 {@code elem.roomFor(tag) >= 本批量}，<b>两条都过</b>才
 * {@code extract} 后再 {@code add}，且 {@code add} 的量取 {@code extract} 的<b>实得</b>量。
 * 于是"抽了源质、容量装不下"这一格在结构上不可达 ⇒ 不会出现"源质消失、容量没涨"的凭空损失。
 * <p>
 * ★刻意<b>不</b>把 6 条 tag 绑成"一整笔全有全无"（猫猫币那条才绑）：这里的"对方"是同一枚口袋里
 * 的另一张表，任一 tag 装不下就只放弃<b>那个 tag</b>，其余五种继续折 —— 绑成整笔会让"ignis 满了"
 * 把另外五条的转化一起冻住，直接违反需求那句"6 种可同时"。两种口径的差别写在
 * {@link PocketCoinChargeDriver} 的 {@code candidateFor} 注释里，不是一处忘了另一处。
 * <p>
 * <b>非 primal 的源质永不被碰</b>：取值循环以 {@link PocketConstants#PRIMAL_TAGS} 为输入（不是
 * "扫 {@code ess} 的全部 tag"），所以复合源质（{@code lux/vitreus/…}）留在盘里等玩家取瓶，
 * 不会被这条被动悄悄折价 —— 复合→元始的折算是 TC 的 {@code ResearchManager.reduceToPrimals}
 * 语义（{@code r96-ret4.md} §3.2），本仓不做第二次裁决。
 * <p>
 * <b>节拍与早退</b>：{@link PocketConstants#ELEMENT_TICK_TRANSMUTE} 走 NBT 剩余 tick（不变量 G8）；
 * {@link PocketUpgradeSwitches#isActive} 的 {@code MAGE} 组合谓词在最前，关着 ⇒ 一次位图读就回、
 * <b>零 NBT 写</b>。装填只在"本拍真的折了东西"之后 ⇒ 盘里没有 primal 的常态零写。
 */
public final class PocketEssenceTransmuteDriver {

    private PocketEssenceTransmuteDriver() {}

    /**
     * 挂载点（{@code ItemNekoDimensionPocket.onUpdate} 的服务端分支每 tick 一次）。
     * <p>
     * 持久化按 F1 双分支（形状照 {@code magnet/PocketMagnetDriver}：源质表是<b>快照</b>，
     * 会话在场时只有会话那一份是真相，无会话才允许一次性 {@code readFrom → 改 → writeTo}）；
     * 元素容量侧不分支 —— {@link PocketElementStore} 是活档视图，写的就是载体根本身。
     */
    public static void onItemTick(ItemStack stack, World world, EntityPlayer player) {
        if (world == null || world.isRemote || player == null || stack == null) {
            return;
        }
        final NBTTagCompound root = stack.getTagCompound();
        if (root == null || !PocketUpgradeSwitches.isActive(root, PocketUpgradeType.MAGE)) {
            return;
        }
        final PocketElementStore elem = PocketElementStore.attach(root);
        if (!elem.tickDue(PocketConstants.ELEMENT_TICK_TRANSMUTE)) {
            // 节拍内一次递减就回；★装配（无会话分支里的一次 PocketInventory.readFrom）必须在判拍之后
            return;
        }
        final UUID uuid = uuidOf(player);
        final PocketSession session = uuid == null ? null : PocketSessions.peek(uuid);
        final boolean viaSession = session != null && session.carrierStack() == stack;
        final PocketInventory oneshot = viaSession ? null : PocketInventory.readFrom(root);
        final PocketEssenceStore ess = viaSession ? session.essence() : oneshot.essence();
        final int moved = transmuteAndArm(elem, ess);
        if (moved <= 0) {
            return;
        }
        if (viaSession) {
            session.markDirty();
        } else {
            oneshot.writeTo(root);
        }
    }

    private static java.util.UUID uuidOf(EntityPlayer player) {
        return player.getGameProfile() == null ? null : player.getGameProfile()
            .getId();
    }

    /**
     * 纯逻辑一拍（回归套件入口）：判开关 → 判拍 → 逐 primal tag 原子折算 → 有货才装下一拍。
     *
     * @param root 载体栈的活 NBT 根（元素容量直接落它）
     * @param ess  源质表（会话内实例或一次性实例，本方法只 {@code extract}）
     * @return 本拍折算成功的<b>总点数</b>（0 = 什么都没动，且零 NBT 写）
     */
    public static int tick(NBTTagCompound root, PocketEssenceStore ess) {
        if (!PocketUpgradeSwitches.isActive(root, PocketUpgradeType.MAGE)) {
            return 0;
        }
        if (root == null || ess == null) {
            return 0;
        }
        final PocketElementStore elem = PocketElementStore.attach(root);
        if (!elem.tickDue(PocketConstants.ELEMENT_TICK_TRANSMUTE)) {
            return 0;
        }
        return transmuteAndArm(elem, ess);
    }

    /**
     * 到拍之后的真动作：逐 tag 折算 + 决定要不要装下一拍。
     * <p>
     * ★"本批一分没折但盘里确实有 primal"（六条容量全满）也装拍 —— 不装的话这一分支会
     * <b>每 tick</b> 重问一次六条 get + 六条 roomFor，并牵住宿主那条 readFrom（R53c 要压的就是这个）。
     */
    private static int transmuteAndArm(PocketElementStore elem, PocketEssenceStore ess) {
        final int moved = transmuteOnce(elem, ess);
        if (moved > 0 || hasPrimalStock(ess)) {
            elem.armTick(PocketConstants.ELEMENT_TICK_TRANSMUTE, PocketConstants.MAGE_SECOND_INTERVAL_TICKS);
        }
        return moved;
    }

    /** 盘里是否有任一 primal（装不装拍的判据；与折算共用同一份白名单点查）。 */
    private static boolean hasPrimalStock(PocketEssenceStore ess) {
        for (String tag : PocketConstants.PRIMAL_TAGS) {
            if (ess.get(tag) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一批的折算（<b>已到拍之后</b>才调）。遍历序 = {@link PocketConstants#PRIMAL_TAGS} 声明序，
     * 同输入必同结论。
     *
     * @return 本批实际折算的总点数（每 tag 至多 {@link PocketConstants#MAGE_TRANSMUTE_POINTS_PER_BATCH}）
     */
    public static int transmuteOnce(PocketElementStore elem, PocketEssenceStore ess) {
        if (elem == null || ess == null) {
            return 0;
        }
        final int batch = PocketConstants.MAGE_TRANSMUTE_POINTS_PER_BATCH;
        if (batch <= 0) {
            return 0;
        }
        int moved = 0;
        for (String tag : PocketConstants.PRIMAL_TAGS) {
            if (ess.get(tag) < batch || elem.roomFor(tag) < batch) {
                // 源质不够、或这个 tag 的容量放不下：跳过它（其余五种照旧并行），一分都不动
                continue;
            }
            final int taken = ess.extract(tag, batch);
            if (taken <= 0) {
                continue;
            }
            // ★add 的量取 extract 的实得量：两头都以"真的动了多少"为准，不按请求量记账
            moved += elem.add(tag, taken);
        }
        return moved;
    }
}
