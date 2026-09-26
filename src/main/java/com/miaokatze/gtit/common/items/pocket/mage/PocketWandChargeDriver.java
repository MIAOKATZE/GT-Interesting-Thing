package com.miaokatze.gtit.common.items.pocket.mage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketElementStore;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;

/**
 * 魔法使被动 ①「<b>缓慢充法杖</b>」的 tick 宿主本体（R96 S9a）。
 * <p>
 * <b>干什么</b>：把口袋 {@link PocketElementStore} 里的元素容量，按节拍一滴一滴灌进玩家够得着的
 * TC 法杖 —— 形状照 TC4 魔力石 {@code thaumcraft/common/items/baubles/ItemAmuletVis.java:73-91}
 * 的支 A（取证 {@code r96-eva3.md} §2.3）：<b>每 5 tick 一批、每批每个 tag 至多 5 点</b>，
 * 两枚节拍常量单源在 {@link PocketConstants#MAGE_WAND_INTERVAL_TICKS} /
 * {@link PocketConstants#MAGE_WAND_MAX_POINTS_PER_BATCH}，本类不抄数字。
 * <p>
 * <b>★方向与"先对方、再自己"</b>：法杖是<b>收</b>方、口袋容量是<b>掏</b>方。搬运量先问桥
 * （{@link WandVisGate#chargeWandVis}，由 TC 自己按 {@code getMaxVis} 钳），拿到<b>实收点数</b>之后
 * 才按同一数掏自己。反序（先掏 5 点、灌不进去 3 点）就是"容量被抹掉、法杖没涨"的凭空损失
 * ——R96 S9a 验收 4 那条纪律在出账侧的孪生形状。{@code 0}（法杖满了）与
 * {@link WandVisGate#NOT_CHARGEABLE}（不是法杖 / TC 缺席）都不掏一分。
 * <p>
 * <b>找法杖的口径</b>（eva3 §2.3 明文要求本仓自选并写进文案）：<b>主手优先、回落整个 36 格主背包</b>
 * （TC 的要素球侧只查 hotbar 9 格 {@code InventoryUtils.isWandInHotbarWithRoom}，魔力石只查主手；
 * 本仓取"主手 + 全 36 格"这条最宽的一档，代价见类注释末段）。一批里只喂<b>第一支</b>吃得下的法杖
 * ⇒ 多支法杖不会每拍轮流入账（那会把"每批 ≤5 点"的速率抬成"每批 ≤5×支数"）。
 * <p>
 * <b>早退与成本</b>（三条，顺序即成本序）：
 * <ol>
 * <li>{@code world.isRemote} / 非玩家 / 无栈 —— 宿主 {@code onUpdate} 已挡，这里是零成本双保险；</li>
 * <li>{@link PocketUpgradeSwitches#isActive} 的 {@code MAGE} 组合谓词（位图 ∧ ¬off-mask）——
 * ★<b>关掉开关 ⇒ 本类一次 NBT 都不摸</b>（R96 S1 的开关执法点，验收 1 的"关 ⇒ 零变化且零 NBT 写"）；</li>
 * <li>节拍闸 {@link PocketElementStore#tickDue} —— ★<b>不</b>照抄魔力石的
 * {@code ticksExisted % 5}（不变量 G8 / R59e：跨维重建实体 tick 不连续 ⇒ 节拍漂），
 * 改走 {@code elem} 内的剩余 tick。未到拍的一批只花一次 {@code getInteger} + 一次递减写，
 * 零候选（{@code elem} 一分没有）时连递减都没有。</li>
 * </ol>
 * 成本如实记：到拍的那一 tick 最多问 {@code 36 × 6} 次桥（每支栈、每个 tag 各一次），
 * 且只在<b>容量表非空</b>时发生 —— 表空 ⇒ 一分类都没有，直接不装拍也不问桥（下面的
 * {@code total() <= 0} 早退就是为这一条留的，它把"刚拿到口袋、还没充过元素"的常态压到每 5 tick 一次
 * {@code hasKey}，不是每 tick 36 次桥调用）。
 */
public final class PocketWandChargeDriver {

    private PocketWandChargeDriver() {}

    /**
     * 挂载点（由 {@code ItemNekoDimensionPocket.onUpdate} 的<b>服务端</b>分支每 tick 调一次；
     * ★R95 门控放宽后背包 36 格任意位都推进，形参 {@code slot}/{@code selected} 不需要透传）。
     */
    public static void onItemTick(ItemStack stack, World world, EntityPlayer player) {
        if (world == null || world.isRemote || player == null || stack == null) {
            return;
        }
        final NBTTagCompound root = stack.getTagCompound();
        if (!PocketUpgradeSwitches.isActive(root, PocketUpgradeType.MAGE) || root == null) {
            return;
        }
        final PocketElementStore elem = PocketElementStore.attach(root);
        if (elem.total() <= 0 || !elem.tickDue(PocketConstants.ELEMENT_TICK_WAND)) {
            // ★节拍没到就连"摆 36 格候选数组"这件事都不做（每 tick 分配一个数组是白付的）
            return;
        }
        chargeAndArm(elem, candidatesOf(player), WandVisGate.TAUM);
    }

    /**
     * 纯逻辑一拍（回归套件的入口：{@code root} 是真档、{@code candidates} 与 {@code gate} 是桩件）。
     * <p>
     * ★与 {@link #onItemTick} 共用同一个 {@link #chargeAndArm}，两处只做"判开关 + 判拍"这三行 ——
     * 判据不在两处各写一遍是刻意的（各写一遍就会出现"套件里绿、宿主里不跑"的 R57/C3 同族形状）。
     *
     * @return 本拍实际搬进法杖的总点数（0 = 什么都没动，且<b>零 NBT 写</b>）
     */
    public static int tick(NBTTagCompound root, ItemStack[] candidates, WandVisGate gate) {
        if (!PocketUpgradeSwitches.isActive(root, PocketUpgradeType.MAGE)) {
            return 0;
        }
        if (root == null || gate == null || candidates == null || candidates.length == 0) {
            return 0;
        }
        final PocketElementStore elem = PocketElementStore.attach(root);
        if (elem.total() <= 0) {
            // 一分容量都没有：不问桥、不摸档（上面类注释的「成本如实记」那一条的执法点）
            return 0;
        }
        if (!elem.tickDue(PocketConstants.ELEMENT_TICK_WAND)) {
            return 0;
        }
        return chargeAndArm(elem, candidates, gate);
    }

    /**
     * 到拍之后真正动手：搬一批，然后决定<b>要不要装下一拍</b>。
     * <p>
     * 装拍条件是「搬到了东西」<b>或</b>「有容量但一支都没喂进去」（后者 = 玩家根本没带法杖，
     * 或所有法杖都满了）。★这一条"空手也装拍"是成本判据不是省事：不装拍的话，只要玩家带着
     * 有容量的口袋，本方法就会<b>每 tick</b> 把 36 格候选 × 6 条 tag 问一遍桥。
     * 反之"一分容量都没有"（{@code total() <= 0}）时上面已经早退且不摸档，所以常态口袋里
     * 既没有币也没有容量时是零写的。
     */
    private static int chargeAndArm(PocketElementStore elem, ItemStack[] candidates, WandVisGate gate) {
        final int moved = chargeOnce(elem, candidates, gate);
        if (moved > 0 || elem.total() > 0) {
            elem.armTick(PocketConstants.ELEMENT_TICK_WAND, PocketConstants.MAGE_WAND_INTERVAL_TICKS);
        }
        return moved;
    }

    /**
     * 一批的搬运（<b>已到拍之后</b>才调；节拍不在这里，防"节拍常量对、但每 tick 都在搬"）。
     * <p>
     * 遍历序确定：候选数组序（主手在前）× {@link PocketConstants#PRIMAL_TAGS} 声明序，同输入必同结论。
     * 每个 tag 的期望量 = {@code min(MAGE_WAND_MAX_POINTS_PER_BATCH, 该 tag 存量)}，
     * 实掏量 = 桥回报的<b>实收</b>点数。
     *
     * @return 本批实际搬进法杖的总点数
     */
    public static int chargeOnce(PocketElementStore elem, ItemStack[] candidates, WandVisGate gate) {
        if (elem == null || gate == null || candidates == null) {
            return 0;
        }
        int movedTotal = 0;
        for (ItemStack wand : candidates) {
            if (wand == null) {
                continue;
            }
            int batch = 0;
            for (String tag : PocketConstants.PRIMAL_TAGS) {
                final int stock = elem.get(tag);
                if (stock <= 0) {
                    continue;
                }
                final int want = Math.min(PocketConstants.MAGE_WAND_MAX_POINTS_PER_BATCH, stock);
                final int landed = gate.chargeWandVis(wand, tag, want);
                if (landed <= 0) {
                    // 0 = 这支这个 tag 满着；NOT_CHARGEABLE = 这支不是可充法杖。都不掏自己。
                    continue;
                }
                // ★先对方、再自己：掏的量恒等于实收量（landed 超库存时按库存给，取严不取宽）
                movedTotal += Math.min(landed, elem.extract(tag, landed));
                batch += landed;
            }
            if (batch > 0) {
                // 一批只喂第一支吃得下的法杖（速率单源，见类注释"找法杖的口径"）
                return movedTotal;
            }
        }
        return movedTotal;
    }

    /**
     * 候选法杖的枚举序 = <b>主手在前 + 其余按背包槽号升序</b>（去重：主手那一格不在尾部重复一遍）。
     * 返回长度 36（含 {@code null} 条目，由 {@link #chargeOnce} 跳过）。
     */
    static ItemStack[] candidatesOf(EntityPlayer player) {
        final ItemStack[] held = player.inventory.mainInventory;
        final int size = held == null ? 0 : held.length;
        final ItemStack[] out = new ItemStack[size == 0 ? 1 : size];
        if (size == 0) {
            return out;
        }
        final int current = player.inventory.currentItem;
        final int first = current >= 0 && current < size ? current : -1;
        int at = 0;
        if (first >= 0) {
            out[at++] = held[first];
        }
        for (int slot = 0; slot < size; slot++) {
            if (slot == first) {
                continue;
            }
            out[at++] = held[slot];
        }
        return out;
    }
}
