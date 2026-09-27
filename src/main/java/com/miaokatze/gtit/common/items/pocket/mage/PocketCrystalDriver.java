package com.miaokatze.gtit.common.items.pocket.mage;

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
import com.miaokatze.gtit.common.items.pocket.PocketMageModes;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;

/**
 * ★R96 S9b 结晶模式「<b>源质直接出晶</b>」的 tick 宿主本体 —— 魔法使四条控件里的第二条。
 *
 * <h2>干什么</h2>
 * 玩家开了「结晶模式」后，蒸馏进源质盘的内容<b>不再只等玩家取瓶</b>：本类按秒把盘里的点数换成
 * <b>源质结晶</b>直接交到玩家手上（背包优先、满则掉脚下）。★缺省<b>关</b>（用户裁定），
 * 且它是 S9a 那三条之外的<b>第四条被动</b>，与「猫猫币充能」「源质转换」共用同一个主开关
 * {@code PocketUpgradeType.MAGE}。
 *
 * <h2>★三条口径纪律（本轮最容易翻车的三处，逐条对着禁令写）</h2>
 * <ol>
 * <li><b>不复用 {@code TaumDistillRules#credit} 的批量语义做逐点入账</b>：那条是"一整轮蒸馏的入账换算"
 * （★它自己注释里明令"不得被当成现行粒度读"，且生产零调用方）。本类<b>一次都不问它</b>，
 * 走的是下面那条三段式；</li>
 * <li><b>读数 + 消耗整件，★不就地排空</b>：先 {@code ess.snapshot()} 读存量 → 按
 * {@link PocketConstants#MAGE_CRYSTAL_MAX_PER_BATCH} 取整批枚数 → {@link CrystalGate#mint} 出<b>新晶</b>
 * → 交付 → 最后才 {@code ess.extract} 扣掉<b>实际交付的枚数 × {@code CRYSTAL_CAPACITY}</b>。
 * 晶自身的 {@code AspectList} 从头到尾没被改过一次（wiki
 * {@code gtit-taumcraft-essentia-carriers.md} §3 证死：就地排空会让服务端随机重赋型）；</li>
 * <li><b>背包满有明确出口、不吞产物</b>：交付走 {@link CrystalSink}（生产实现
 * {@link com.miaokatze.gtit.common.items.pocket.PocketItemExit#giveOrDrop} = 背包→脚下两级兜底）。
 * 交付返 {@code false}（玩家为 null 一类）⇒ ★这一批<b>一分不扣</b>，源质留在盘里等下一拍；
 * 出晶返 {@code null}（TC 缺席）⇒ 同样一分不扣。</li>
 * </ol>
 *
 * <h2>消耗量为什么取"实交付枚数"而不是"请求枚数"</h2>
 * 桥侧 {@code newCrystalStack} 自带 {@code min(points, 64)} 的钳制，返回的 {@code stackSize} 才是
 * 真的到了玩家手里多少。按请求量记账就会出现"请求 70、到手 64、盘里扣 70"那 6 点的空洞 ——
 * 与 S9a 法杖那条「按对方实收量掏自己」是同一条纪律的镜像（那侧是收方钳，这侧是出件钳）。
 *
 * <h2>节拍与早退（顺序即成本序）</h2>
 * ① 宿主 {@code onUpdate} 已挡服务端与非玩家，这里零成本双保险；② 主开关
 * {@link PocketUpgradeSwitches#isActive}（★S9a 的既有锚，本类保留自己这一读，
 * 与子模式位的<b>合取点就在本行</b>，理由见 {@link PocketMageModes} 类注释"组合点不在本类"那节）；
 * ③ 子模式位 {@link PocketMageModes#crystalOn}；④ 节拍 {@link PocketConstants#ELEMENT_TICK_CRYSTAL}
 * （NBT 剩余 tick，★不用 {@code ticksExisted}，不变量 G8）。
 * ★关着（主开关与本模式任一关）⇒ ②③ 之后一句都不执行、<b>零 NBT 写</b>；开着则<b>无条件</b>装下一拍
 * （⇒ 无会话分支的那次装配至多每秒一次）。这一条与 {@link PocketEssenceTransmuteDriver}
 * "搬到过东西或有货才装拍"<b>刻意不同</b>，取舍与成本序写在 {@link #crystallizeAndArm} 的注释里。
 *
 * <h2>与「源质转换」同吃一盘的关系（★代价，不是缺陷）</h2>
 * 两条都从 {@code ess} 取货：转换每秒每 tag 折 1 点进元素容量，结晶每秒每 tag 至多出 64 枚。
 * 同时开着时结晶会<b>先</b>把盘清空（本类在 {@code onUpdate} 的挂载序在转换之后，但两者各自的
 * 节拍独立），玩家要"留源质在盘里折元素"就得关掉结晶模式。这条写在 README 代价清单里，
 * ★不做仲裁（自动互斥会让玩家看到"我开了它却没跑"，比抢得更难看）。
 */
public final class PocketCrystalDriver {

    private PocketCrystalDriver() {}

    /**
     * 挂载点（由 {@code ItemNekoDimensionPocket.onUpdate} 的<b>服务端</b>分支每 tick 调一次）。
     * <p>
     * 源质表按 F1 双分支取（形状与 {@link PocketEssenceTransmuteDriver#onItemTick} 逐字同形：
     * 会话在场只有会话那一份是真相、无会话才允许一次性 {@code readFrom → 改 → writeTo}），
     * 元素容量侧不需要分支（{@link PocketElementStore} 是活档视图，节拍键直接落载体根）。
     */
    public static void onItemTick(ItemStack stack, World world, EntityPlayer player) {
        if (world == null || world.isRemote || player == null || stack == null) {
            return;
        }
        final NBTTagCompound root = stack.getTagCompound();
        if (root == null || !PocketUpgradeSwitches.isActive(root, PocketUpgradeType.MAGE)
            || !PocketMageModes.crystalOn(root)) {
            return;
        }
        final PocketElementStore elem = PocketElementStore.attach(root);
        if (!elem.tickDue(PocketConstants.ELEMENT_TICK_CRYSTAL)) {
            // ★判拍在前、装配在后：无会话分支那次 PocketInventory.readFrom 因此<b>至多每秒一次</b>（R53c）
            return;
        }
        final UUID uuid = uuidOf(player);
        final PocketSession session = uuid == null ? null : PocketSessions.peek(uuid);
        final boolean viaSession = session != null && session.carrierStack() == stack;
        final PocketInventory oneshot = viaSession ? null : PocketInventory.readFrom(root);
        final PocketEssenceStore ess = viaSession ? session.essence() : oneshot.essence();
        final int moved = crystallizeAndArm(elem, ess, CrystalSink.ofPlayer(player), CrystalGate.TAUM);
        if (moved <= 0) {
            return;
        }
        if (viaSession) {
            session.markDirty();
        } else {
            oneshot.writeTo(root);
        }
    }

    /**
     * 纯逻辑一拍（回归套件入口）：主开关 → 子模式 → 判拍 → 逐 tag 出晶并交付 → 有货才装下一拍。
     * <p>
     * ★与 {@link #onItemTick} 共用同一个 {@link #crystallizeAndArm}：判据只写一遍，
     * 免得"套件绿而实机不跑"（R57/C3 那一族的形状）。
     *
     * <p>
     * ★{@code root} 关着（主开关或结晶位任一关）⇒ 本方法连 {@link PocketElementStore} 都不碰，
     * 返回 0 且档面逐字节不变（"关 ⇒ 零变化且零 NBT 写"那条验收在第四模上的形状）。
     *
     * @return 本拍交付出去的<b>源质点数</b>（0 = 什么都没搬；开着的 0 仍会装下一拍，见上面那条口径差）
     */
    public static int tick(NBTTagCompound root, PocketEssenceStore ess, CrystalSink sink, CrystalGate gate) {
        if (root == null || !PocketUpgradeSwitches.isActive(root, PocketUpgradeType.MAGE)
            || !PocketMageModes.crystalOn(root)) {
            return 0;
        }
        if (ess == null || sink == null || gate == null) {
            return 0;
        }
        final PocketElementStore elem = PocketElementStore.attach(root);
        if (!elem.tickDue(PocketConstants.ELEMENT_TICK_CRYSTAL)) {
            return 0;
        }
        return crystallizeAndArm(elem, ess, sink, gate);
    }

    private static int crystallizeAndArm(PocketElementStore elem, PocketEssenceStore ess, CrystalSink sink,
        CrystalGate gate) {
        final int moved = crystallizeOnce(ess, sink, gate);
        // ★<b>无条件</b>装下一拍 —— 与 S9a 那三条"搬到过东西或有货才装拍"的形状<b>刻意不同</b>：
        // 本腿没有便宜的「盘里有货」判据（源质表是快照，问它一次就得先装配一次，而 S9a 报告 §4③
        // 点名的那条读侧成本债正是"拿装配当代价去省一次写"），于是无事可做时只剩两档：
        // <b>A</b> 每秒一次 4 字节节拍写 / <b>B</b> 每 tick 一次整棵 135 格的装配（= S9a 转换腿在空盘时的现状）。
        // 本腿取 A。★关态（主开关与本模式任一关）仍然连本方法都进不来 ⇒ 零 NBT 写，那条判据由用例钉。
        elem.armTick(PocketConstants.ELEMENT_TICK_CRYSTAL, PocketConstants.MAGE_SECOND_INTERVAL_TICKS);
        return moved;
    }

    /**
     * 一批的出晶与交付（<b>已到拍之后</b>才调）。遍历序 = 源质表的入账序（{@code snapshot()} 已是
     * 不可变有序副本），同输入必同结论；★不做"只处理 primal"的过滤 —— 盘里有什么就出什么晶
     * （TC 的晶本来就允许非元始 aspect，"6 条白名单"那套判据管的是<b>元素容量表</b>有几行，
     * 与本出口无关，别把两条判据混成一条）。
     *
     * @return 本批实际交付出去的<b>源质点数</b>（= 交付枚数 × {@code CRYSTAL_CAPACITY} 的累加）
     */
    public static int crystallizeOnce(PocketEssenceStore ess, CrystalSink sink, CrystalGate gate) {
        if (ess == null || sink == null || gate == null) {
            return 0;
        }
        final int perCrystal = TaumDistillRules.CRYSTAL_CAPACITY;
        if (perCrystal <= 0) {
            return 0;
        }
        int deliveredPoints = 0;
        final Map<String, Integer> stock = ess.snapshot();
        for (Map.Entry<String, Integer> entry : stock.entrySet()) {
            final Integer held = entry.getValue();
            if (held == null || held.intValue() <= 0) {
                continue;
            }
            final int want = Math.min(held.intValue(), PocketConstants.MAGE_CRYSTAL_MAX_PER_BATCH);
            final ItemStack crystals = gate.mint(entry.getKey(), want);
            if (crystals == null || crystals.stackSize <= 0) {
                // ★TC 缺席 / tag 不认识 / 桥没装配：这一 tag 一分不动（宁缺不错，下一拍再问）
                continue;
            }
            if (!sink.deliver(crystals)) {
                // ★交付没成立 ⇒ 不消耗。产物此时仍只在内存里（从未进世界），丢弃引用即等于什么都没发生
                continue;
            }
            // ★按"实际交付的枚数"折算消耗量（桥侧 min(points,64) 之后的真值），★不按请求量记账
            final int points = crystals.stackSize * perCrystal;
            deliveredPoints += ess.extract(entry.getKey(), points);
        }
        return deliveredPoints;
    }

    private static UUID uuidOf(EntityPlayer player) {
        return player.getGameProfile() == null ? null
            : player.getGameProfile()
                .getId();
    }
}
