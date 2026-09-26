package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 升级<b>启用位图</b>（off-mask）的唯一读写面，与 {@link PocketUpgrades} 的<b>效果位图正交</b>（R96 S1，P-1）。
 *
 * <h2>两条裁定必须分开读（本轮第二容易翻车的地方）</h2>
 * <ul>
 * <li>{@link PocketConstants#UPGRADES_KEY 效果位图}：唯一回答"<b>这一型有没有被买到 / 固化</b>"。
 * R95 的语义一字未动 —— 只置不清、放入即固化、不可取出，写进口只有
 * {@link PocketUpgrades#install}；</li>
 * <li>{@link PocketConstants#UPGRADES_OFF_KEY 启用位图}（本类）：回答"<b>已经买到的这一型当前开不开</b>"。
 * 语义是<b>关闭</b>位掩码（bit=1 ⇒ 关），<b>缺键 = 五型全开</b>，可置可清。</li>
 * </ul>
 * 因此 R95 那句"效果查询的唯一真相一律读 {@code hasUpgrade}"从本轮起要读成：<b>位图仍是"买没买到"的唯一
 * 真相</b>，而<b>效果判据</b>是位图与启用位的合成 {@link #isActive}。本类不改写 {@code PocketUpgrades} 的
 * 任何一行，只是把"读效果"这件事从它那里接过来 —— 除本类之外，主源不得再直调
 * {@link PocketUpgrades#hasUpgrade} 来决定行为（门禁 {@code verify-pocket.sh} 钉这一点，唯一的合法豁免是
 * P-4 的源质钳制腿，见 {@link PocketInventory#readFrom}）。
 *
 * <h2>为什么不建 int/long 双轨</h2>
 * 这里是 5 个布尔量，单一 byte 表示即可；W1 的双轨是"<b>量</b>"的两份表示（long 真值 / int 头），根因是
 * {@code FluidStack.amount} 是 int —— 把那个形状照搬到布尔量上只会多出第二份真相。同理，本类<b>不</b>在
 * {@link PocketInventory} 里缓存任何开关状态：每次现读载体根（缺键即全开），写侧只有服务端一条路。
 *
 * <h2>光泽（{@code hasEffect}）为什么只能读 {@link #anyActive}</h2>
 * vanilla 的附魔光泽是<b>单个布尔</b>（一件物品只能"亮 / 不亮"），五个开关共用的就是这一个位 ⇒
 * 光泽判据取并集（{@code isWorkActive || anyActive}），<b>逐型开关状态的唯一读数是配置面板</b>。
 * 这条表达上限是渲染面的物理限制，不是实现偷懒，理由原文写在
 * {@code ItemNekoDimensionPocket#hasEffect(ItemStack)} 的 javadoc。
 *
 * <h2>读路径纪律（R53c）</h2>
 * 三个 {@code isOff}/{@code isActive}/{@code anyActive} 的读腿一律<b>不建档</b>：无栈 / 无 NBT / 无键
 * 都按"该位为 0 = 开着"处理，绝不在读路径上 {@code setByte} 或建 compound。
 * {@link #setOff} 是写腿，且<b>只在真的改变时</b>才写档（同值直接返回 false ⇒ 连点不会刷出 N 包整栈同步）。
 */
public final class PocketUpgradeSwitches {

    private PocketUpgradeSwitches() {}

    /**
     * 该型当前是否被关掉（只读，不建档）。无栈 / 无 NBT / 无 {@link PocketConstants#UPGRADES_OFF_KEY}
     * 键 ⇒ {@code false}（缺键 = 全开 = R95 之前的老档逐字不变的默认态）。
     */
    public static boolean isOff(ItemStack stack, PocketUpgradeType type) {
        return isOff(stack == null ? null : stack.getTagCompound(), type);
    }

    /** 根版 {@link #isOff(ItemStack, PocketUpgradeType)}（{@code PocketInventory} 只面对化合物）。 */
    public static boolean isOff(NBTTagCompound root, PocketUpgradeType type) {
        if (root == null || !root.hasKey(PocketConstants.UPGRADES_OFF_KEY)) {
            return false;
        }
        return (root.getByte(PocketConstants.UPGRADES_OFF_KEY) & 0xFF & bit(type)) != 0;
    }

    /**
     * <b>唯一的组合谓词</b>：该型当前是否生效 = 已固化 {@code &&} 未被关闭。
     * <p>
     * ★主源里所有"这一型的效果开不开"的读点都必须经它（漏一条 = 开关对那条路径根本无效 = 本仓 R57/C3
     * 那一族"写了没人调、用例全绿而实机什么都没发生"的复发形状）。
     */
    public static boolean isActive(ItemStack stack, PocketUpgradeType type) {
        return isActive(stack == null ? null : stack.getTagCompound(), type);
    }

    /** 根版 {@link #isActive(ItemStack, PocketUpgradeType)}：位图腿与 off-mask 腿都现读同一活根。 */
    public static boolean isActive(NBTTagCompound root, PocketUpgradeType type) {
        return PocketUpgrades.hasUpgrade(root, type) && !isOff(root, type);
    }

    /**
     * 五位里是否<b>至少一型生效</b>（供 {@code hasEffect} 的光泽腿消费；也供配置面板的"整只口袋开着没有"读数）。
     * <p>
     * 一次读两份 byte 而不是逐型问五遍 {@link #isActive} 的理由：光泽是<b>每帧每 pass</b> 被问的
     * （{@code RenderItem} 的 GUI 与手持两条路都在现读），五遍会把同一份 NBT 摸十次。
     */
    public static boolean anyActive(ItemStack stack) {
        return anyActive(stack == null ? null : stack.getTagCompound());
    }

    /** 根版 {@link #anyActive(ItemStack)}；无档 ⇒ false（从未固化过任何插件的口袋不亮）。 */
    public static boolean anyActive(NBTTagCompound root) {
        if (root == null || !root.hasKey(PocketConstants.UPGRADES_KEY)) {
            return false;
        }
        final int installed = root.getByte(PocketConstants.UPGRADES_KEY) & 0xFF;
        final int off = root.hasKey(PocketConstants.UPGRADES_OFF_KEY)
            ? root.getByte(PocketConstants.UPGRADES_OFF_KEY) & 0xFF
            : 0;
        return (installed & ~off) != 0;
    }

    /**
     * 写"这一型开 / 关"（★<b>仅服务端</b>：客户端私写的位不会被 1.7.10 的 NBT 同步纠正 ⇒ 会长成两处真相）。
     * <p>
     * 返回"<b>本次是否真的改变了</b>"（形状照 {@link PocketFilterConfig} 的属性写腿）：同值直接 {@code false}
     * 且<b>零写入</b> —— 连点同一格不会刷出一串整栈同步包。改变后若掩码归零则 {@code removeTag} 而不是留
     * 一个 0 值壳（口径同 {@code ItemNekoDimensionPocket#setOpenFlag} 与 {@code tickDown}：缺键与 0 值同义，
     * 少一个键就少一份 NBT 深比较的体积）。
     * <p>
     * ★本方法<b>不</b>判"这一型有没有固化"——那是服务端入口三判之一（持有者本人 / 该型已固化 / 守卫通过，
     * 见 R96 计划 §3 S2 段）。理由：给未固化的型写关闭位是无害的（{@link #isActive} 的位图腿本来就是
     * {@code false}），而在这里加一道"只许关不许清"的判据反而会让历史脏位再也清不掉。
     *
     * @param root 载体栈的 NBT 根（<b>活实例</b>：会话期内 {@code PocketInventory} 的探针读的就是它）；
     *             {@code null} ⇒ 不写、返回 {@code false}（建档责任在调用方，读路径与写路径都不越俎）
     * @param off  {@code true} = 关掉这一型，{@code false} = 打开
     */
    public static boolean setOff(NBTTagCompound root, PocketUpgradeType type, boolean off) {
        if (root == null) {
            return false;
        }
        final int mask = root.hasKey(PocketConstants.UPGRADES_OFF_KEY)
            ? root.getByte(PocketConstants.UPGRADES_OFF_KEY) & 0xFF
            : 0;
        final int next = off ? (mask | bit(type)) : (mask & ~bit(type));
        if (next == mask) {
            return false;
        }
        if (next == 0) {
            root.removeTag(PocketConstants.UPGRADES_OFF_KEY);
        } else {
            root.setByte(PocketConstants.UPGRADES_OFF_KEY, (byte) next);
        }
        return true;
    }

    /** 位 = {@code 1 << ordinal()}：与效果位图同一套位序，冻结的是 {@link PocketUpgradeType} 的声明序。 */
    private static int bit(PocketUpgradeType type) {
        return 1 << type.ordinal();
    }
}
