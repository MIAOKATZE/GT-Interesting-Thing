package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * ★R96 S9b：魔法使<b>四模式</b>里那三条子模式的<b>唯一谓词面</b>（{@code 开关 / 结晶 / 猫猫币充能 / 源质转换}
 * 中的后三条 —— 第一条是 S1 的 {@link PocketUpgradeSwitches}，本类<b>不</b>重复它、也不代它写档）。
 *
 * <h2>为什么模式状态另立一枚 byte，而不塞进 {@code upgradesOff}</h2>
 * 那一位回答的是"<b>这一型</b>买到了、开不开"，一共五位、位序 = {@code PocketUpgradeType#ordinal()}；
 * 本类回答的是"<b>型内的这一条被动</b>开不开"。并成一位会出现两件实际会坏的事：
 * ① 关掉「魔法使」与只关「结晶模式」在档上是同一个数 ⇒ 面板与 {@code hasEffect} 的光泽再也分不开这两态；
 * ② {@code upgradesOff} 的写入口被门禁钉成"类外恰 1 且点名为 {@code PocketConfigPanel#commitSwitch}"
 * （R96-S1b 门 D），多一条写腿要么红要么得改旧门 ⇒ 后者明令禁止。
 *
 * <h2>★缺省态不对称，且只在这里解释一次</h2>
 * {@link PocketConstants#MAGE_MODES_DEFAULT} = 猫猫币 + 源质转换<b>开</b>、结晶<b>关</b>。
 * 前两档开是<b>不改变 S9a 已落地行为</b>的唯一取法（S9a 那三条被动本来就是"MAGE 一开就跑"，
 * 若这里默认关，玩家升级照旧、被动却全体停摆 = 静默回归）；结晶默认关是用户对本模式的<b>显式裁定</b>
 * （它是改变源质去向的功能，开着会悄悄把玩家盘里的源质换成物品）。
 *
 * <h2>★组合点在哪：不在本类</h2>
 * 本类的方法<b>只</b>答"这一枚位开不开"，★<b>不</b>把 {@link PocketUpgradeSwitches#isActive} 折进来。
 * 理由：主开关与子模式的<b>合取点</b>只许住在被闸的那条腿里（三条 driver 与面板的提交腿），
 * 折进本类就会同时存在"driver 里读一次 isActive（门禁钉着的锚）"与"本类里再读一次"两份读数 ——
 * 那不是两处真相，但是<b>一条判据被读两遍</b>，正是本仓反复登记的那一族形状。
 * 调用点的正确写法：{@code PocketUpgradeSwitches.isActive(root, MAGE) && PocketMageModes.crystalOn(root)}。
 *
 * <h2>读路径纪律（R53c）</h2>
 * 一切读经 {@link PocketElementStore}（{@code elem} compound 的唯一写者与唯一持有者），
 * 无档 / 缺键 ⇒ 按 {@link PocketConstants#MAGE_MODES_DEFAULT} 给，★绝不建档。
 */
public final class PocketMageModes {

    private PocketMageModes() {}

    /** 结晶模式是否开（★只看子模式位；与主开关的合取在 {@code mage/PocketCrystalDriver}）。 */
    public static boolean crystalOn(NBTTagCompound root) {
        return PocketElementStore.attach(root).modeOn(PocketConstants.MAGE_MODE_CRYSTAL);
    }

    /** 结晶模式是否开（栈版，面板与 tooltip 用）。 */
    public static boolean crystalOn(ItemStack carrier) {
        return crystalOn(carrier == null ? null : carrier.getTagCompound());
    }

    /** 猫猫币充能是否开（★只看子模式位；合取在 {@code mage/PocketCoinChargeDriver}，S9a 的既有锚不动）。 */
    public static boolean coinOn(NBTTagCompound root) {
        return PocketElementStore.attach(root).modeOn(PocketConstants.MAGE_MODE_COIN);
    }

    /** 源质转换是否开（★只看子模式位；合取在 {@code mage/PocketEssenceTransmuteDriver}）。 */
    public static boolean transmuteOn(NBTTagCompound root) {
        return PocketElementStore.attach(root).modeOn(PocketConstants.MAGE_MODE_TRANSMUTE);
    }

    /**
     * 位 → 谓词的唯一分派表（★面板与提交腿都经它，别处不得再写一遍"哪个位读哪个方法"）。
     * 非法位一律 {@code false}（不是"当缺省处理"——那会让一个没定义的位在档上被读成开）。
     */
    public static boolean on(NBTTagCompound root, int bit) {
        if (bit == PocketConstants.MAGE_MODE_CRYSTAL) {
            return crystalOn(root);
        }
        if (bit == PocketConstants.MAGE_MODE_COIN) {
            return coinOn(root);
        }
        if (bit == PocketConstants.MAGE_MODE_TRANSMUTE) {
            return transmuteOn(root);
        }
        return false;
    }

    /** 写某一枚模式位（★唯一写腿，返回值 = 本次是否真的改了档；同值零写入 ⇒ 连点不刷整栈同步包）。 */
    public static boolean write(NBTTagCompound root, int bit, boolean wantOn) {
        return root != null && PocketElementStore.attach(root).setMode(bit, wantOn);
    }

    /**
     * 「魔法使」这一型<b>在不在档上</b>（★模式提交腿的第一判，口径与
     * {@code PocketConfigPanel#switchState} 那条"isActive ∨ isOff"完全同形，只是宿主换成本类，
     * 免得 GUI 侧再添一条 {@code PocketUpgradeSwitches.} 直读 —— 那条被 R96-S1b 门 F 钉成恰 4 处）。
     * <p>
     * 给一型从未买过的插件写模式位是无害的（{@code isActive} 的组合谓词本来就是 false），但它是<b>假读数</b>：
     * 那一行会显示"结晶：开"，而它对应的是"没有这个升级"。⇒ 提交腿据此拒绝。
     */
    public static boolean masterOnRecord(ItemStack carrier) {
        if (carrier == null) {
            return false;
        }
        final NBTTagCompound root = carrier.getTagCompound();
        return PocketUpgradeSwitches.isActive(root, PocketUpgradeType.MAGE)
            || PocketUpgradeSwitches.isOff(root, PocketUpgradeType.MAGE);
    }
}
