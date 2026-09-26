package com.miaokatze.gtit.common.items.pocket.mage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.PocketItemExit;

/**
 * 结晶产物的<b>落点</b>抽象（"这枚栈到底交出去没有"的唯一问句，R96 S9b 的"背包满不吞产物"判据载体）。
 * <p>
 * 与 {@link CrystalGate} 分成两层的理由：出件与交付是<b>两个可能各自失败</b>的动作，并成一个就会
 * 分不清"TC 不在场所以没晶"与"包满了所以交不出去"——而这两种失败在玩家侧的说法完全不同
 * （前者是这条腿整个不可用，后者是产物掉到脚下）。消耗判据两处都要求<b>零消耗</b>，
 * 但只有交付那一处是玩家能自己解决的（腾个格子）。
 * <p>
 * ★生产实现只有一个落点：{@link PocketItemExit#giveOrDrop(EntityPlayer, ItemStack)} ——
 * 背包塞不下就掉到玩家脚下（★不是"再塞一次"、更不是丢掉），与 {@code PocketSlots#returnToPlayer}
 * 那一条既有退件腿<b>同一个本体</b>（S9b 把它整体上移，口袋目录内的
 * {@code addItemStackToInventory}/{@code entityDropItem} 计数因此一字未动，见 R96-S4 门 D）。
 */
public interface CrystalSink {

    /**
     * 交付一枚栈。
     *
     * @return {@code true} = 已交付（背包<b>或</b>脚下）；{@code false} = 没交付成，
     *         ★调用方必须按"什么都没发生"处理，一分来源都不许扣
     */
    boolean deliver(ItemStack crystals);

    /** 玩家落点（背包优先、满则掉脚下）。 */
    static CrystalSink ofPlayer(EntityPlayer player) {
        return new CrystalSink() {

            @Override
            public boolean deliver(ItemStack crystals) {
                return PocketItemExit.giveOrDrop(player, crystals);
            }
        };
    }
}
