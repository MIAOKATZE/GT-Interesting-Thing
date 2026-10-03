package com.miaokatze.gtit.common.items.pocket.mage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.PocketItemExit;
import com.miaokatze.gtit.common.items.pocket.PocketSession;

/**
 * 结晶产物的<b>落点</b>抽象（"这枚栈实际交出去多少"的唯一问句）。
 * <p>
 * 与 {@link CrystalGate} 分成两层的理由：出件与交付是<b>两个可能各自失败</b>的动作，并成一个就会
 * 分不清"TC 不在场所以没晶"与"包满了所以交不出去"——而这两种失败在玩家侧的说法完全不同
 * （前者是这条腿整个不可用，后者是产物要另找出路）。
 * <p>
 * ★交付语义（★R106-① 落点改判后写明的唯一契约）：{@code deliver} 的<b>唯一契约 = 返回实付枚数</b>。
 * 未付余量的去向由<b>落点实现</b>决定，调用方不回读 {@code crystals.stackSize}：
 * {@link #ofPlayer} 把余量留在栈上、{@link #ofSession} 不留（入参栈不被它改写语义所约束），
 * 两者对调用方等价——{@code PocketCrystalDriver#deliverAsCrystals} 正是只认返回值记账
 * （refund = 请求 − Σ实付）。掉地兜底<b>不在本层</b>：未交付的点数由蒸馏腿整份进 spill、
 * 经兜底落点二次出晶（裁决点住在调用侧 {@code PocketDistillDriver}，本层只报告事实，
 * 不替调用方决定余量去向）。
 */
public interface CrystalSink {

    /**
     * 交付一枚栈（{@code deliver} 的<b>唯一契约 = 返回实付枚数</b>；余量去向由落点实现决定，
     * 调用方只认返回值，不回读栈）。
     *
     * @return 实付枚数（&le; 入参 {@code stackSize}）
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
     * 会话落点（★R106-① 结晶主落点）：先口袋中栏（跳 ghost 声明格）、再玩家背包兜底；不掉地。
     * <p>
     * <b>薄适配器，不是新落点算法</b>：整个落点序（找格 + 合堆 + 跳 ghost + 背包兜底）单源住在
     * {@link PocketSession#depositItem} 那一条腿上，本变体只把 {@code deliver} 转发过去
     * （{@code depositItem} 返回实付件数、不改写入参栈，与本接口"只认返回值"的契约完全相容）。
     * 中栏与背包两级都放不下的余量由调用方（蒸馏腿的 spill 支）走脚下兜底，本层不掉地。
     */
    static CrystalSink ofSession(PocketSession session) {
        return new CrystalSink() {

            @Override
            public int deliver(ItemStack crystals) {
                return session == null ? 0 : session.depositItem(crystals);
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
