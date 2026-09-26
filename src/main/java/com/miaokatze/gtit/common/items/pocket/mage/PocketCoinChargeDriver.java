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
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;

/**
 * 魔法使被动 ②「<b>猫猫币充能</b>」的 tick 宿主本体（R96 S9a，用户裁定 G-2 的执行面）。
 * <p>
 * <b>干什么</b>：把玩家身边的猫猫币折进口袋的元素容量 —— <b>1 秒恰 1 枚</b>，
 * 普通币六条 primal <b>各</b> {@code +1}、闪烁币各 {@code +10}
 * （价值单源 {@link PocketConstants#MAGE_COIN_VALUE_NORMAL} / {@code _SHIMMERING}，
 * 身份单源 {@link CoinGate#NEKO}，节拍单源 {@link PocketConstants#MAGE_SECOND_INTERVAL_TICKS}）。
 * <p>
 * <b>★三条口径按代码顺序排（每条都是验收 2 点名的判据）</b>：
 * <ol>
 * <li><b>先口袋内 135 格、后玩家背包 36 格</b>：{@link #holdersFor} 返回的就是<b>这个序</b>的数组，
 * 扫描按数组序取<b>第一枚</b>命中的币 ⇒ "口袋里的币先被吃"是数据结构本身，不是一句 if；</li>
 * <li><b>整笔预检在前</b>：一份候选是「6 条 tag 各 +价值」，任一 tag 余量不足 ⇒
 * {@link PocketElementStore#canAcceptAll} 判 false ⇒ <b>一枚都不扣</b>。
 * 造"一枚币 + 容量将满"的场景断言不会白吃币，钉的就是这一段；</li>
 * <li><b>先扣币、再加容量</b>：{@link CoinHolder#consumeOne} 返 1 之后才 {@code putAll}；
 * 反序 = "币没扣、容量白给"（可复制刷容量）。预检已经过了，所以"扣到币但进不了容量"这一格
 * 在本拍内不可达（同 tick 内没有第二个写者能改动 {@code elem} —— 见
 * {@link PocketElementStore} 类注释的活档视图那一段）。</li>
 * </ol>
 * <p>
 * <b>★计时走 NBT 剩余 tick（不变量 G8）</b>：节拍键 {@link PocketConstants#ELEMENT_TICK_COIN}
 * 住在 {@code elem} 里，由 {@link PocketElementStore#tickDue} 推进。
 * <b>不</b>用 {@code player.ticksExisted % 20}（磁力那条是既存例外，本片不扩散），也不用世界绝对时刻。
 * 装填时机刻意选在"这一拍真的扣了币"之后 ⇒ 连吃 N 枚币之间的间隔恰好是 N × 20 tick，
 * 而"包里没币"的常态<b>零 NBT 写</b>；"有币但容量放不下"也装填（把扫描率压到 1 次/秒，
 * 不是一枚币每 tick 被反复问一遍）。
 */
public final class PocketCoinChargeDriver {

    private PocketCoinChargeDriver() {}

    /**
     * 挂载点（{@code ItemNekoDimensionPocket.onUpdate} 的服务端分支每 tick 一次）。
     * <p>
     * 持久化按仓内既有的 <b>F1 双分支</b>（形状照 {@code magnet/PocketMagnetDriver}）：
     * 会话在场且承载<b>本栈</b> ⇒ 只改会话内存（中栏走 {@link CoinHolder#ofSession}）并 {@code markDirty}，
     * <b>绝不</b>整表回写这枚栈的 NBT（双写竞争）；无会话 ⇒ 一次性 {@code readFrom → 改 → writeTo}
     * （每<b>动作</b>至多一次读改写，不是每 tick）。
     * 元素容量本身两分支同一条路：{@link PocketElementStore} 是活档视图，写的就是载体根。
     */
    public static void onItemTick(ItemStack stack, World world, EntityPlayer player) {
        if (world == null || world.isRemote || player == null || stack == null) {
            return;
        }
        final NBTTagCompound root = stack.getTagCompound();
        if (root == null || !PocketUpgradeSwitches.isActive(root, PocketUpgradeType.MAGE)) {
            // 无档 = 什么都没有；关着 = 一次位图读就回（★零 NBT 写）
            return;
        }
        final PocketElementStore elem = PocketElementStore.attach(root);
        if (!elem.tickDue(PocketConstants.ELEMENT_TICK_COIN)) {
            // 节拍内：一次递减就回。★这一行在"装配来源"之前是有意的 —— 装配在无会话分支里是
            // 一次 PocketInventory.readFrom（整表反序列化），放到判拍之前就成了每 tick 一次（R53c 明禁）。
            return;
        }
        final UUID uuid = player.getGameProfile() == null ? null : player.getGameProfile()
            .getId();
        final PocketSession session = uuid == null ? null : PocketSessions.peek(uuid);
        final boolean viaSession = session != null && session.carrierStack() == stack;
        final PocketInventory oneshot = viaSession ? null : PocketInventory.readFrom(root);
        final CoinHolder[] holders = holdersFor(viaSession ? CoinHolder.ofSession(session)
            : CoinHolder.ofStorage(oneshot), player);
        final int consumed = chargeAndArm(elem, holders, CoinGate.NEKO);
        if (consumed <= 0) {
            return;
        }
        if (viaSession) {
            session.markDirty();
        } else {
            oneshot.writeTo(root);
        }
    }

    /**
     * 来源序：<b>口袋 135 格在前</b>（用户裁定 G-2 的第一档），玩家主背包在后。
     * 抽成独立方法是为了让"顺序"这一件事在测试里可断言、在改动时只有一处。
     */
    static CoinHolder[] holdersFor(CoinHolder pocketHolder, EntityPlayer player) {
        return new CoinHolder[] { pocketHolder, CoinHolder.ofPlayer(player) };
    }

    /**
     * 纯逻辑一拍（回归套件入口）：判开关 → 判拍 → 找币 → 整笔预检 → 扣币 → 入账 → 装拍。
     *
     * @param holders 有序来源（{@link #holdersFor} 的形状；测试传两个桩件即可验"先口袋后背包"）
     * @param gate    币身份与价值判据（生产 {@link CoinGate#NEKO}）
     * @return 本拍<b>扣掉</b>的币数（0 或 1 —— 一秒一枚是硬上界，不是统计结果）
     */
    public static int tick(NBTTagCompound root, CoinHolder[] holders, CoinGate gate) {
        if (!PocketUpgradeSwitches.isActive(root, PocketUpgradeType.MAGE)) {
            return 0;
        }
        if (root == null || gate == null || holders == null || holders.length == 0) {
            return 0;
        }
        final PocketElementStore elem = PocketElementStore.attach(root);
        if (!elem.tickDue(PocketConstants.ELEMENT_TICK_COIN)) {
            return 0;
        }
        return chargeAndArm(elem, holders, gate);
    }

    /**
     * 到拍之后的真动作：找币 → 整笔预检 → 扣币 → 入账 → 装下一拍。
     * <p>
     * ★与 {@link #onItemTick} 共用这一份本体（宿主侧只做"判开关 + 判拍 + 装配来源 + 落盘"四件事），
     * 免得算式在两处各写一遍、套件绿而实机不跑（R57/C3 同族）。
     */
    private static int chargeAndArm(PocketElementStore elem, CoinHolder[] holders, CoinGate gate) {
        CoinHolder hit = null;
        int hitSlot = -1;
        int value = 0;
        for (CoinHolder holder : holders) {
            final int found = firstCoinSlot(holder, gate);
            if (found < 0) {
                continue;
            }
            hit = holder;
            hitSlot = found;
            value = gate.valueOf(holder.stackAt(found));
            break;
        }
        if (hit == null || value <= 0) {
            // 一档都没有币：不装拍、不写档（零 NBT 写）
            return 0;
        }
        final Map<String, Integer> candidate = candidateFor(value);
        if (!elem.canAcceptAll(candidate)) {
            // ★整笔预检挡下：币原样留在原地，一分容量不给（宁不吃，不吃一半再报"满了"）
            elem.armTick(PocketConstants.ELEMENT_TICK_COIN, PocketConstants.MAGE_SECOND_INTERVAL_TICKS);
            return 0;
        }
        if (hit.consumeOne(hitSlot) <= 0) {
            // 防御：那一格在扫描与扣减之间被别处清空 ⇒ 不给容量
            return 0;
        }
        elem.putAll(candidate);
        elem.armTick(PocketConstants.ELEMENT_TICK_COIN, PocketConstants.MAGE_SECOND_INTERVAL_TICKS);
        return 1;
    }

    /**
     * 一份"六条 primal 各加 {@code value} 点"的入账候选（序 = {@link PocketConstants#PRIMAL_TAGS} 声明序）。
     * <p>
     * ★刻意<b>不</b>跳过"某条已满"的 tag 而只收其余五条：那正是被禁止的"吃一半"形状
     * （币扣了、只涨五条 ⇒ 玩家看到的总量与预期差一条，且差哪条取决于历史）。要收就六条一起收。
     */
    public static Map<String, Integer> candidateFor(int value) {
        final Map<String, Integer> out = new LinkedHashMap<>();
        if (value <= 0) {
            return out;
        }
        for (String tag : PocketConstants.PRIMAL_TAGS) {
            out.put(tag, value);
        }
        return out;
    }

    /**
     * 本档里<b>第一枚</b>可用币的格号（槽号升序，同输入必同结论）；没有则 −1。
     * ★只问身份、<b>不</b>扣减 —— 扣减必须等到整笔预检之后。
     */
    private static int firstCoinSlot(CoinHolder holder, CoinGate gate) {
        if (holder == null || gate == null) {
            return -1;
        }
        final int size = holder.slots();
        for (int slot = 0; slot < size; slot++) {
            final ItemStack stack = holder.stackAt(slot);
            if (stack != null && gate.valueOf(stack) > 0) {
                return slot;
            }
        }
        return -1;
    }
}
