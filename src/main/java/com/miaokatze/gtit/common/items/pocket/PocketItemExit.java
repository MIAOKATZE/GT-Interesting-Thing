package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.crossmod.taum.TaumCompat;

/**
 * ★R96 S9b：结晶模式的<b>产物出包口</b> —— 把"交付给玩家"这一件事收在仓内<b>唯一</b>一处。
 *
 * <h2>为什么要有这个类，而不是就地写 vanilla 那两行</h2>
 * 需求给本模式留了一条硬边界：<b>背包满时要有明确出口，不许吞产物</b>。本仓早就有同一条口径的两件
 * 现成形状（{@code NekoPocketPanel#giveToPlayer} 的按天然满量拆块 + {@code PocketSlots#returnToPlayer}
 * 的"塞不进就掉脚下"），而 {@code verify-pocket.sh} 的 R96-S4 门 D 把口袋与 GUI 两个目录里的
 * {@code addItemStackToInventory} 钉成<b>恰 3</b>、{@code entityDropItem} 钉成<b>恰 2</b>
 * ——理由是"新增那条必须先按 maxStackSize 拆堆才允许存在"（NEI 把 &gt;100 当无限物品、vanilla 的
 * {@code EntityItem.Count} 是 byte，多一处落点就多一处踩回这两条的机会）。
 * 所以本模式<b>不新开第四条腿</b>，而是把 {@code PocketSlots} 那一条<b>整体上移</b>到本类
 * （★逐字搬，不重写、不改语义），原处改为转调 ⇒ 门 D 的两个数一字不动，而世界内 tick 的 driver
 * 与 GUI 侧的退件共用<b>同一个</b>交付本体。
 * <p>
 * ★顺带解决的是分层：driver 住在 {@code common/items/pocket}，让它反过来依赖 {@code gui.pocket.PocketSlots}
 * 就是让常驻逻辑去拉一个 ModularUI 类（{@code ItemNekoDimensionPocket:52-54} 那条"常驻类不引客户端依赖"
 * 的红线管的是 {@code thaumcraft.*} 与 lwjgl，但同一个方向感适用于这里）。本类只依赖 MC 与 TC 门面。
 *
 * <h2>★本类的交付判据</h2>
 * <ul>
 * <li>先 {@code addItemStackToInventory}：塞得下（可能塞掉一部分，余量仍在 {@code stack.stackSize} 上）⇒
 * 成功；</li>
 * <li>塞不下（返回 false，或整件未进）⇒ {@code entityDropItem} 掉到玩家脚下，★仍算交付成功
 * （东西在世界里，玩家捡得回来，不是"消失"）；</li>
 * <li>玩家为 {@code null} / 栈为 {@code null} 或空 ⇒ 返回 {@code false}，<b>调用方一分都不许扣</b>
 * （这是"不吞产物"的另一半：交付没成立就不能消耗来源）。</li>
 * </ul>
 * 一次只交<b>一枚栈</b>，且★不在这里拆堆：调用侧的产出量本身已被
 * {@link PocketConstants#MAGE_CRYSTAL_MAX_PER_BATCH} 与 {@link TaumCompat} 那侧的可堆上限钳在原版范围内，
 * 拆堆的尺子归 {@code NekoPocketPanel#giveToPlayer} 那条漏斗（两处各写一把尺就是 R96-S4 门 D 要防的事）。
 */
public final class PocketItemExit {

    private PocketItemExit() {}

    /**
     * 把一枚栈交到玩家手上，塞不进就掉脚下。
     *
     * @return {@code true} = 已交付（背包或脚下）；{@code false} = <b>没交付</b>（入参不合法），
     *         调用方必须按"未消耗"处理
     */
    public static boolean giveOrDrop(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.stackSize <= 0) {
            return false;
        }
        if (!player.inventory.addItemStackToInventory(stack)) {
            player.entityDropItem(stack, 0);
        }
        return true;
    }
}
