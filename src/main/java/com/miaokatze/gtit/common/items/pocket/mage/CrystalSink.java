package com.miaokatze.gtit.common.items.pocket.mage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.PocketItemExit;

/**
 * 结晶产物的<b>落点</b>抽象（"这枚栈实际交出去多少"的唯一问句）。
 * <p>
 * 与 {@link CrystalGate} 分成两层的理由：出件与交付是<b>两个可能各自失败</b>的动作，并成一个就会
 * 分不清"TC 不在场所以没晶"与"包满了所以交不出去"——而这两种失败在玩家侧的说法完全不同
 * （前者是这条腿整个不可用，后者是产物要另找出路）。
 * <p>
 * ★交付语义（结晶并入蒸馏腿分叉后）：{@code deliver} 返回<b>实付枚数</b>，未付的余量留在
 * {@code crystals.stackSize} 上——掉地兜底<b>不在本层</b>：未交付的点数由蒸馏腿回退源质盘，
 * 盘也满才经 spill 支掉脚下（两级兜底的裁决点住在调用侧 {@code PocketDistillDriver}，
 * 本层只报告事实，不替调用方决定余量去向）。
 */
public interface CrystalSink {

    /**
     * 交付一枚栈（只进背包、不掉地）。
     *
     * @return 实付枚数；未付的余量留在 {@code crystals.stackSize} 上，由调用方按点数回退
     */
    int deliver(ItemStack crystals);

    /** 玩家落点（背包实付、余量留栈）。 */
    static CrystalSink ofPlayer(EntityPlayer player) {
        return new CrystalSink() {

            @Override
            public int deliver(ItemStack crystals) {
                return PocketItemExit.giveIntoInventory(player, crystals);
            }
        };
    }

    /**
     * spill 兜底落点（背包或脚下，恒全额实付）：背包与源质盘两级都装满之后的最后出口，
     * 由蒸馏腿的 spill 支专用。{@code giveOrDrop} 自带掉地兜底 ⇒ 只要 mint 出了晶，
     * before 枚就全部有了着落（部分进背包、余量掉脚下），余量恒为零。
     */
    static CrystalSink ofPlayerDropping(EntityPlayer player) {
        return new CrystalSink() {

            @Override
            public int deliver(ItemStack crystals) {
                final int before = crystals.stackSize;
                return PocketItemExit.giveOrDrop(player, crystals) ? before : 0;
            }
        };
    }
}
