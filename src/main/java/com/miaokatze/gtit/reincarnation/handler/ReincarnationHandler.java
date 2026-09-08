package com.miaokatze.gtit.reincarnation.handler;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.reincarnation.ReincarnationConfirmHook;
import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle.CycleState;
import com.miaokatze.gtit.reincarnation.core.ReincarnationFingerprints;
import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;
import com.miaokatze.gtit.reincarnation.entity.EntityAscensionCarrier;
import com.miaokatze.gtit.reincarnation.entity.ReincarnationEntities;
import com.miaokatze.gtit.reincarnation.network.GrantEffectPacket;
import com.miaokatze.gtit.reincarnation.network.ReincarnationNetwork;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * 周目系统服务端编排器（v1.9.0 S4 收口切片）。
 * <p>
 * 编排四条服务端世界侧链路（订阅 cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent /
 * PlayerLoggedOutEvent + TickEvent.ServerTickEvent（1.7.10 三者均在 FML 总线；任务包所写
 * "Forge PlayerLoggedInEvent" 在本 Forge 构建不存在，按语义取 FML gameevent 同名事件）+
 * 注入 {@link ReincarnationConfirmHook}）：
 * <ol>
 * <li><b>登录判定</b>：以 world seed 派生指纹（{@link ReincarnationFingerprints}）对照
 * 记录的永久指纹集合——命中（该时间线已轮回过）→ 下发倒计时（截止 = now+30s 墙钟毫秒），
 * 到点经 tick 调度执行升天（同确认路径的 {@link #beginAscension}）；<b>每次重进重置</b>
 * （deadline 按登录时刻重算下发，重登自然重发，倒计时期间退出即取消，不作持久化）。
 * 未命中且 {@code canClaimGrant()}（EXECUTED 待领取）→ 200 tick 延迟发放奖励；</li>
 * <li><b>奖励发放</b>：{@code claimGrant()}（EXECUTED→IDLE）→ save → 逐件
 * {@code addItemStackToInventory}（满则 {@code dropPlayerItemWithRandomChoice}）→
 * {@code sendGrantEffectToClient}（驱动客户端庆祝+螺旋降下演出）；</li>
 * <li><b>确认轮回</b>（{@link ReincarnationConfirmHook} 实现，S7 GUI 二次确认后回调）：
 * 校验 {@code state == DEPOSITED && pendingItems 非空 && server 线程} →
 * {@code confirmReincarnation(worldSeed)}（指纹入永久集合）→ save → 升天；</li>
 * <li><b>飞升编排</b>（{@link #beginAscension}）：{@code HardcoreEnforcer.beginEnforcement}
 * → 载具生成绑定（{@code ReincarnationEntities.spawnFor(player, 120)}）→
 * {@code sendAscensionStartToClient} → tick 轮询 {@code carrier.isAscensionComplete()}
 * → 击杀玩家（{@code outOfWorld} 足量伤害 + {@code setHealth(0)} 兜底）。死亡后删档由
 * {@link HardcoreEnforcer} 自理（60 tick 延时删档，本类不感知）。</li>
 * </ol>
 * <p>
 * <b>mutextLock 口径收束</b>（快照构造与本 javadoc 双落点）：只读锁 = EXECUTED
 * （存在未领取轮回，周目记录只读）；DEPOSITED 属"可调整可确认"态（清单可逐件增删，
 * 可确认轮回），不算只读锁。快照下发统一经 {@link #sendSyncSnapshot}。</li>
 * <p>
 * <b>侧与线程</b>：本类仅被物理客户端门控路径安装（CommonProxy.init 的
 * reincarnation 门控块内，物理专用服务器整体拒绝注册不可达）；世界侧逻辑统一以
 * {@code worldObj.isRemote == false} 判定（集成服线程上运行）；确认钩子额外校验
 * server 线程（MUI2 C2S synced action 回调）。全部调度状态仅 server 线程读写，无共享锁。
 * <p>
 * 飞升轮询单槽（对齐单机周目语义：同一时刻至多一名周目玩家，同 HardcoreEnforcer 口径），
 * 并附轮询硬上限防载具异常卸载导致任务滞留。
 */
public final class ReincarnationHandler {

    /** 轮回倒计时时长（毫秒）：登录下发与到点判定共用同一常量 */
    private static final long COUNTDOWN_MILLIS = 30_000L;

    /** 登录后奖励领取延迟（tick） */
    private static final int GRANT_DELAY_TICKS = 200;

    /** 飞升时长（tick）：与载具 spawnFor 参数同源（120 tick = 6 秒，总升程约 12 格） */
    private static final int ASCENSION_DURATION_TICKS = 120;

    /** 飞升完成轮询硬上限（tick）：防载具异常卸载/未完成导致收束路径滞留 */
    private static final int ASCENSION_POLL_CAP_TICKS = 600;

    /** 单例：事件监听器 + 调度状态持有者 */
    private static final ReincarnationHandler INSTANCE = new ReincarnationHandler();

    /** 安装幂等标志 */
    private static boolean installed;

    /** 周目记录仓库（懒建；baseDir 与 S7 GUI 会话构造同源：MC 运行目录） */
    private ReincarnationStore store;

    /** server tick 到期任务队列（登录延迟发放/倒计时到点/兜底死亡；仅 server 线程读写） */
    private final List<ScheduledAction> scheduledActions = new ArrayList<>();

    /** 进行中的飞升槽位（单槽；null = 无），见类 javadoc"单槽"说明 */
    private EntityAscensionCarrier activeCarrier;
    /** 飞升中的玩家（与 activeCarrier 同生命周期） */
    private EntityPlayerMP ascensionPlayer;
    /** 飞升轮询剩余硬上限（tick） */
    private int ascensionPollTicksLeft;

    private ReincarnationHandler() {}

    /**
     * 安装服务端编排器（幂等）：FML 总线（登录/登出事件 + server tick）+
     * 注入确认轮回钩子。自包含——调用方仅需一行接线，无需再注册任何监听。
     * <p>
     * 仅物理客户端门控路径调用（CommonProxy.init 的 reincarnation 门控块内）。
     */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        ReincarnationConfirmHook.Holder.setHook(INSTANCE::onConfirmRequested);
        GTInterestingThing.LOG.info("[reincarnation] 服务端编排器已安装（登录判定/倒计时/奖励发放/飞升编排 + 确认钩子注入）");
    }

    // ==================== 登录判定 ====================

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        // 仅服务端世界侧逻辑（集成服线程；客户端侧事件不编排）
        if (player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        try {
            ReincarnationCycle cycle = store().load(
                player.getUniqueID()
                    .toString());
            long seed = player.worldObj.getSeed();
            String fingerprint = ReincarnationFingerprints.fingerprintOf(seed);
            if (cycle.hasFingerprint(fingerprint)) {
                // 该时间线已轮回过：重发倒计时（每次重进重置——deadline 按本次登录重算，
                // 重登自然重发；见类 javadoc），到点经 tick 调度执行升天（同确认路径）
                long deadline = System.currentTimeMillis() + COUNTDOWN_MILLIS;
                ReincarnationNetwork.sendCountdownToClient(player, deadline);
                scheduledActions.add(new ScheduledAction(player, deadline, () -> executeCountdownDeadline(player)));
                GTInterestingThing.LOG.info(
                    "[reincarnation] 登录命中已轮回时间线：player=" + player.getCommandSenderName()
                        + "，倒计时已重发（deadline="
                        + deadline
                        + "）");
            } else if (cycle.canClaimGrant()) {
                // EXECUTED 待领取：延迟 200 tick 发放（等客户端就绪后再结算与演出）
                scheduledActions.add(new ScheduledAction(player, GRANT_DELAY_TICKS, () -> executeGrant(player)));
                GTInterestingThing.LOG.info(
                    "[reincarnation] 登录发现待领取轮回奖励：player=" + player.getCommandSenderName()
                        + "，"
                        + GRANT_DELAY_TICKS
                        + " tick 后发放");
            }
            sendSyncSnapshot(player, cycle);
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[reincarnation] 登录编排失败（不影响登录）", t);
        }
    }

    /**
     * 登出清扫：作废该玩家的全部到期任务（延迟发放/倒计时到点/兜底死亡），
     * 并清飞升槽位。倒计时/飞升均在下次登录时按"每次重进重置"口径重发（见类 javadoc）。
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        scheduledActions.removeIf(action -> action.player == player);
        if (ascensionPlayer == player) {
            ascensionPlayer = null;
            activeCarrier = null;
            GTInterestingThing.LOG.info("[reincarnation] 登出清空飞升槽位（下次登录按需重发）：player=" + player.getCommandSenderName());
        }
    }

    // ==================== server tick 调度 ====================

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tickScheduledActions();
        tickAscension();
    }

    /** 到期任务驱动：玩家死亡/登出即作废；墙钟 deadline 与 tick 倒数两种口径 */
    private void tickScheduledActions() {
        if (scheduledActions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<ScheduledAction> iterator = scheduledActions.iterator();
        while (iterator.hasNext()) {
            ScheduledAction action = iterator.next();
            if (action.player.isDead) {
                iterator.remove();
                GTInterestingThing.LOG.info("[reincarnation] 到期任务作废（玩家已死亡）");
                continue;
            }
            if (action.deadlineMillis >= 0) {
                if (now < action.deadlineMillis) {
                    continue;
                }
            } else if (action.ticksRemaining > 0) {
                action.ticksRemaining--;
                continue;
            }
            iterator.remove();
            try {
                action.action.run();
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("[reincarnation] 到期任务执行失败", t);
            }
        }
    }

    /** 飞升轮询：完成（或硬上限）即收束击杀；玩家死亡/登出即清槽防泄漏 */
    private void tickAscension() {
        if (activeCarrier == null) {
            return;
        }
        EntityPlayerMP player = ascensionPlayer;
        if (player == null || player.isDead) {
            activeCarrier = null;
            ascensionPlayer = null;
            GTInterestingThing.LOG.info("[reincarnation] 飞升轮询清槽（玩家已死亡）");
            return;
        }
        boolean completed = activeCarrier.isAscensionComplete();
        boolean timedOut = --ascensionPollTicksLeft <= 0;
        if (completed || timedOut) {
            activeCarrier = null;
            ascensionPlayer = null;
            killPlayer(player, completed ? "飞升完成" : "飞升轮询硬上限");
        }
    }

    // ==================== 确认轮回（ReincarnationConfirmHook 实现） ====================

    /**
     * S7 GUI 二次确认回调（经 {@link ReincarnationConfirmHook.Holder} 注入）。
     * 校验 DEPOSITED + 非空清单 + server 线程后收束本轮轮回并进入飞升。
     */
    private void onConfirmRequested(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP mp = (EntityPlayerMP) player;
        if (mp.worldObj == null || mp.worldObj.isRemote) {
            GTInterestingThing.LOG.warn("[reincarnation] 确认轮回回调要求服务端世界，忽略本次调用");
            return;
        }
        // server 线程校验：getEffectiveSide 以 "Server thread" 线程名判定，集成服线程
        // 上（物理客户端内）同样返回 SERVER；MUI2 C2S synced action 均在该线程回调
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.SERVER) {
            GTInterestingThing.LOG.warn("[reincarnation] 确认轮回回调不在 server 线程，忽略本次调用");
            return;
        }
        try {
            ReincarnationStore cycleStore = store();
            ReincarnationCycle cycle = cycleStore.load(
                mp.getUniqueID()
                    .toString());
            if (cycle.getCycleState() != CycleState.DEPOSITED || cycle.getPendingItems()
                .isEmpty()) {
                GTInterestingThing.LOG.warn(
                    "[reincarnation] 确认轮回被拒（状态=" + cycle.getCycleState()
                        + "，待领取="
                        + cycle.getPendingItems()
                            .size()
                        + " 件）：player="
                        + mp.getCommandSenderName());
                return;
            }
            long seed = mp.worldObj.getSeed();
            String fingerprint = cycle.confirmReincarnation(seed);
            cycleStore.save(cycle);
            GTInterestingThing.LOG.info(
                "[reincarnation] 轮回确认：DEPOSITED→EXECUTED，player=" + mp.getCommandSenderName()
                    + "，fingerprint="
                    + fingerprint
                    + "（该时间线已永久标记周目）");
            sendSyncSnapshot(mp, cycle);
            beginAscension(mp);
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[reincarnation] 轮回确认执行失败", t);
        }
    }

    // ==================== 飞升编排 ====================

    /**
     * 执行升天（确认路径与倒计时到点共用）。时序：
     * 极限强制 → 载具生成绑定 → 客户端演出信号 → tick 轮询完成 → 击杀（删档归 HardcoreEnforcer）。
     */
    private void beginAscension(EntityPlayerMP player) {
        if (activeCarrier != null) {
            GTInterestingThing.LOG.warn("[reincarnation] 前一飞升尚未收束，覆盖飞升槽位（单机周目语义）");
        }
        HardcoreEnforcer.beginEnforcement(player);
        EntityAscensionCarrier carrier = ReincarnationEntities.spawnFor(player, ASCENSION_DURATION_TICKS);
        if (carrier == null) {
            // 载具生成失败兜底：仍按演出时长走死亡路径，不让周目玩家滞留
            GTInterestingThing.LOG.error("[reincarnation] 飞升载具生成失败，回退为延时死亡路径：player=" + player.getCommandSenderName());
            ReincarnationNetwork.sendAscensionStartToClient(player, -1);
            scheduledActions
                .add(new ScheduledAction(player, ASCENSION_DURATION_TICKS, () -> killPlayer(player, "载具缺失兜底")));
            return;
        }
        ReincarnationNetwork.sendAscensionStartToClient(player, carrier.getEntityId());
        activeCarrier = carrier;
        ascensionPlayer = player;
        ascensionPollTicksLeft = ASCENSION_POLL_CAP_TICKS;
        GTInterestingThing.LOG.info(
            "[reincarnation] 飞升开始：player=" + player.getCommandSenderName()
                + "，carrierId="
                + carrier.getEntityId()
                + "，时长 "
                + ASCENSION_DURATION_TICKS
                + " tick（完成后执行时间线终结）");
    }

    /**
     * 时间线终结收口：足量 outOfWorld 伤害走原版死亡管线（掉落/统计/LivingDeathEvent，
     * 删档倒计时由 HardcoreEnforcer 经该事件承接）；{@code setHealth(0)} 兜底防伤害
     * 被免疫/拦截路径吃掉。死亡后删档由 HardcoreEnforcer 自理，本类不感知。
     */
    private void killPlayer(EntityPlayerMP player, String why) {
        if (player.isDead) {
            return;
        }
        GTInterestingThing.LOG.warn(
            "[reincarnation] 时间线终结（" + why + "）：player=" + player.getCommandSenderName() + "，执行升天击杀（死亡删档由极限强制器承接）");
        player.attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE);
        if (!player.isDead && player.getHealth() > 0.0F) {
            player.setHealth(0.0F);
            GTInterestingThing.LOG
                .warn("[reincarnation] 升天击杀兜底生效（伤害管线被拦截，setHealth(0)）：player=" + player.getCommandSenderName());
        }
    }

    // ==================== 奖励发放 / 倒计时到点 ====================

    /** EXECUTED → IDLE 领取奖励并发放入背包（满则随机掉落），随后下发演出包 */
    private void executeGrant(EntityPlayerMP player) {
        try {
            ReincarnationStore cycleStore = store();
            ReincarnationCycle cycle = cycleStore.load(
                player.getUniqueID()
                    .toString());
            if (!cycle.canClaimGrant()) {
                // 状态漂移（如确认路径先行收束）静默跳过
                GTInterestingThing.LOG.info(
                    "[reincarnation] 奖励领取跳过（当前状态 " + cycle.getCycleState()
                        + "）：player="
                        + player.getCommandSenderName());
                return;
            }
            List<ReincarnationCycle.ItemRef> items = cycle.claimGrant();
            cycleStore.save(cycle);
            List<GrantEffectPacket.ItemRef> fxItems = new ArrayList<>();
            for (ReincarnationCycle.ItemRef ref : items) {
                ItemStack stack = resolveStack(ref);
                if (stack == null) {
                    GTInterestingThing.LOG.warn("[reincarnation] 奖励物品无法解析 registry id，跳过发放：" + ref);
                    continue;
                }
                if (!player.inventory.addItemStackToInventory(stack)) {
                    // 背包满：原地随机掉落（发放不丢账）
                    player.dropPlayerItemWithRandomChoice(stack, false);
                }
                fxItems.add(new GrantEffectPacket.ItemRef(ref.getId(), ref.getMeta()));
            }
            ReincarnationNetwork.sendGrantEffectToClient(player, fxItems);
            GTInterestingThing.LOG.info(
                "[reincarnation] 轮回奖励发放完成：EXECUTED→IDLE，player=" + player.getCommandSenderName()
                    + "，items="
                    + fxItems.size()
                    + " 项（演出包已下发）");
            sendSyncSnapshot(player, cycle);
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[reincarnation] 轮回奖励发放失败", t);
        }
    }

    /** 倒计时到点：该时间线已轮回过，执行升天（同确认路径） */
    private void executeCountdownDeadline(EntityPlayerMP player) {
        if (player.isDead) {
            return;
        }
        GTInterestingThing.LOG
            .warn("[reincarnation] 轮回倒计时到点（该时间线已轮回过）：player=" + player.getCommandSenderName() + "，执行升天");
        beginAscension(player);
    }

    // ==================== 快照与工具 ====================

    /**
     * 下发周目只读快照（状态展示用）。
     * <p>
     * mutextLock 口径（S4 收束，与本类 javadoc 双落点）：<b>只读锁 = EXECUTED</b>；
     * DEPOSITED 属"可调整可确认"态，不算只读锁。
     */
    private static void sendSyncSnapshot(EntityPlayerMP player, ReincarnationCycle cycle) {
        try {
            ReincarnationNetwork.sendSyncToClient(
                player,
                cycle.getCycleState()
                    .ordinal(),
                cycle.getUnlockedRows(),
                cycle.getHullProgress(),
                cycle.getUnlockedColumns(),
                cycle.getPendingItems()
                    .size(),
                cycle.getCycleState() == CycleState.EXECUTED);
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[reincarnation] 周目快照下发失败（不影响主流程）", t);
        }
    }

    /** ItemRef → ItemStack（registry id 解析，与 S7 GUI 会话路径同款写法） */
    private static ItemStack resolveStack(ReincarnationCycle.ItemRef ref) {
        Item item = (Item) Item.itemRegistry.getObject(ref.getId());
        return item == null ? null : new ItemStack(item, 1, ref.getMeta());
    }

    /**
     * 周目记录仓库（懒建，幂等）。baseDir 与 S7 GUI（{@code ReincarnationCycleGui}
     * 会话构造 {@code new ReincarnationStore(new File("."))}）同源取 MC 运行目录。
     */
    private ReincarnationStore store() {
        if (store == null) {
            store = new ReincarnationStore(new File("."));
        }
        return store;
    }

    /**
     * server tick 到期任务（内部）。{@code deadlineMillis >= 0} 按墙钟绝对毫秒判定
     * （倒计时口径）；否则按 {@code ticksRemaining} 逐 tick 倒数（延迟任务口径）。
     */
    private static final class ScheduledAction {

        final EntityPlayerMP player;
        final long deadlineMillis;
        int ticksRemaining;
        final Runnable action;

        ScheduledAction(EntityPlayerMP player, int ticksRemaining, Runnable action) {
            this(player, -1L, ticksRemaining, action);
        }

        ScheduledAction(EntityPlayerMP player, long deadlineMillis, Runnable action) {
            this(player, deadlineMillis, 0, action);
        }

        private ScheduledAction(EntityPlayerMP player, long deadlineMillis, int ticksRemaining, Runnable action) {
            this.player = player;
            this.deadlineMillis = deadlineMillis;
            this.ticksRemaining = ticksRemaining;
            this.action = action;
        }
    }
}
