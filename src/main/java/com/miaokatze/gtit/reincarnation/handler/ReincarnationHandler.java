package com.miaokatze.gtit.reincarnation.handler;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.StatList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.reincarnation.ReincarnationConfirmHook;
import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle.CycleState;
import com.miaokatze.gtit.reincarnation.core.ReincarnationFingerprints;
import com.miaokatze.gtit.reincarnation.core.ReincarnationGrantGate;
import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;
import com.miaokatze.gtit.reincarnation.entity.EntityAscensionCarrier;
import com.miaokatze.gtit.reincarnation.entity.ReincarnationEntities;
import com.miaokatze.gtit.reincarnation.network.GrantEffectPacket;
import com.miaokatze.gtit.reincarnation.network.ReincarnationNetwork;
import com.miaokatze.gtit.reincarnation.storage.ReincarnationWorldData;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * 周目系统服务端编排器（v1.9.0 S4 收口切片）。
 * <p>
 * 编排五条服务端世界侧链路（订阅 cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent /
 * PlayerLoggedOutEvent + TickEvent.ServerTickEvent（1.7.10 三者均在 FML 总线；任务包所写
 * "Forge PlayerLoggedInEvent" 在本 Forge 构建不存在，按语义取 FML gameevent 同名事件）+
 * 注入 {@link ReincarnationConfirmHook}）：
 * <ol>
 * <li><b>登录判定</b>：以 world seed 派生指纹（{@link ReincarnationFingerprints}）对照
 * 记录的永久指纹集合——命中（该时间线已轮回过）→ 下发倒计时（截止 = now+30s 墙钟毫秒），
 * 到点经 tick 调度执行升天（同确认路径的 {@link #beginAscension}）；<b>每次重进重置</b>
 * （deadline 按登录时刻重算下发，重登自然重发，倒计时期间退出即取消，不作持久化）。
 * 未命中且 {@code canClaimGrant()}（EXECUTED 待领取）→ 100 tick 延迟发放奖励；</li>
 * <li><b>奖励发放</b>（D3 时序）：启动（{@code executeGrant}：①确认可领 ①'不变量复核
 * （可领取 + 本档未领取；门控形态判定在登录瞬间一次性完成，复核不重读时变信号）+
 * 承诺点（置每存档 {@code GrantStarted} 并立即落盘，崩溃续跑旁路）②投胎信箱
 * {@code grantInFlight} 幂等标记 ②'逐件发放账本（信箱 {@code delivered} 已交付前缀
 * 计数，续跑从剩余后缀继续，不重复发放）③演出包（只发剩余后缀）④逐件调度发放）→
 * 收束（{@code completeGrant}：⑤再确认在线+未完成 ⑥清扫未发放件
 * {@code addItemStackToInventory}（满则 {@code dropPlayerItemWithRandomChoice}）
 * ⑦成功或落地后才 claimGrant 清信箱+复位标记）；逐件发放按降落节奏错峰：第 i 件在
 * {@code i*5+54} tick（降落末段接近玩家）入库并单调持久化账本；异常/掉线信箱与
 * 账本保留，登录链路自动续跑（崩溃重进不重复发放）；
 * 客户端只显示，结束以服务端计时为准；</li>
 * <li><b>确认轮回</b>（{@link ReincarnationConfirmHook} 实现，S7 GUI 二次确认后回调）：
 * 校验 {@code state == DEPOSITED && pendingItems 非空 && server 线程} →
 * {@code confirmReincarnation(worldSeed)}（指纹入永久集合）→ save → 升天；</li>
 * <li><b>飞升编排</b>（{@link #beginAscension}）：{@code HardcoreEnforcer.beginEnforcement}
 * → 载具生成绑定（{@code ReincarnationEntities.spawnFor(player, 120)}）→
 * {@code sendAscensionStartToClient} → tick 轮询 {@code carrier.isAscensionComplete()}
 * → 击杀玩家（{@code outOfWorld} 足量伤害 + {@code setHealth(0)} 兜底）。死亡后删档由
 * {@link HardcoreEnforcer} 自理（60 tick 延时删档，本类不感知）。</li>
 * <li><b>解锁连掷会话</b>（v1.9.0 连掷重构）：解锁按钮 toggle 的会话注册表
 * （{@code rollSessions}，player UUID → shimmer/normal 两独立槽，仅 server 线程读写、
 * 不持久化）+ ServerTickEvent END 逐 tick 驱动单掷（扣币与概率判定在容器侧权威模型执行）。
 * 停止条件：① 无剩余可解锁目标 → 静默；② 背包无该币种 → no_coin 消息（①②由单掷闭包
 * 返回 false 表达）；③ 登出/死亡/GUI 关闭 → 静默摘除；④ 再按同按钮 → 即时摘除。</li>
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
 * <b>v1.8.2 严格单机门控</b>：登录判定/确认轮回/倒计时到点三触点均先过
 * {@link #isStrictSinglePlayer()}（isSinglePlayer + 开放 LAN getPublic 反射检测——
 * 1.7.10 {@code isSinglePlayer()} 实现为 {@code serverOwner != null}，集成服含开放
 * LAN 恒为 true，单靠它抓不住 LAN）；非单机环境登录整体跳过、确认拒绝（chat 提示
 * {@code gtit.reincarnation.single_player_only}）、到点拒绝升天，杜绝开放 LAN 下
 * 宾客轮回触发删档的"半删档死锁"。{@link com.miaokatze.gtit.main.ClientProxy}
 * openReincarnationGui 复用同判定。
 * </p>
 * <p>
 * 飞升轮询单槽（对齐单机周目语义：同一时刻至多一名周目玩家，同 HardcoreEnforcer 口径），
 * 并附轮询硬上限防载具异常卸载导致任务滞留。
 */
public final class ReincarnationHandler {

    /** 轮回倒计时时长（毫秒）：登录下发与到点判定共用同一常量 */
    private static final long COUNTDOWN_MILLIS = 30_000L;

    /** 登录后奖励领取延迟（tick）；v1.8.13 起由 200（10 秒）收紧为 100（5 秒） */
    private static final int GRANT_DELAY_TICKS = 100;

    /**
     * 发放演出收束时长（tick）：演出包下发后经本服务端计时清扫未发放件并清信箱
     * （D3 时序：客户端只显示，结束以服务端计时为准；与客户端 GRANT_DESCENT_TICKS=100
     * 同步取 100，恒晚于末件逐件发放时点）。
     */
    private static final int GRANT_PERFORMANCE_TICKS = 100;

    /** 逐件发放错峰间隔（tick）：与客户端 GRANT_STAGGER_TICKS 同步取 5（"依次"降落节奏） */
    private static final int GRANT_STAGGER_TICKS = 5;

    /** 单件自降下起点到玩家处的时长（tick）：与客户端 GRANT_PER_ITEM_TICKS 同步取 60 */
    private static final int GRANT_PER_ITEM_TICKS = 60;

    /**
     * 单件可拾取降落末段比例（用户口径"末段 10% 即可拾取"）：第 i 件在
     * {@code i * GRANT_STAGGER_TICKS + (int)(GRANT_PER_ITEM_TICKS * (1 - 本值))} tick
     * 入库——此时该件距玩家约 0.4 格（smoothstep 末段），接近即得。
     */
    private static final float GRANT_PICKUP_LAST_FRACTION = 0.1F;

    /**
     * 演出逐件发放物品数上限：与客户端 GRANT_MAX_ANIMATED_ITEMS 同步取 7；
     * 超出部分无演出对应件，随收束清扫一次性发放。
     */
    private static final int GRANT_ANIMATED_ITEM_CAP = 7;

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

    /**
     * 连掷会话注册表（player UUID → 会话槽；v1.9.0 连掷重构）。
     * 会话形态 {coinType, active}：active 以「槽位在场」表达（{@link PlayerRollSessions}
     * 两槽非 null 即激活）。仅 server 线程读写、不持久化（登出/GUI 关闭/死亡即弃）。
     */
    private final Map<UUID, PlayerRollSessions> rollSessions = new HashMap<>();

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

    // ==================== v1.8.2 严格单机门控 ====================

    /**
     * 严格单机判定（v1.8.2，登录/确认/倒计时三触点与 ClientProxy.openReincarnationGui 共用）。
     * <p>
     * 仅靠 {@code MinecraftServer.isSinglePlayer()} 不够：1.7.10 实现为
     * {@code serverOwner != null}（本项目 build/rfg 反编译源 MinecraftServer.java:999），
     * 集成服构造时恒置 serverOwner（IntegratedServer 构造器 47 行），开放 LAN
     * （shareToLan 仅置 isPublic）不清除——LAN 上 isSinglePlayer() 仍返回 true。
     * 故追加反射读取 {@code IntegratedServer.getPublic()}（dev MCP 名 / prod SRG 成员名
     * {@code func_71344_c}，见 forge conf methods.csv；1.7.10 prod 运行时类名保持 MCP
     * 形态、仅成员名为 SRG，故只做方法名双候选、不做类名前置判断；双名均缺失即按
     * 非单机保守拒绝）：已发布 LAN 即按非单机拒绝。
     * 双名解析写法对齐 {@code HardcoreEnforcer.resolveHardcoreField}（SRG 在 prod 命中，
     * dev 名在 dev 命中）。
     * <p>
     * 反射走字符串方法名，零 {@code IntegratedServer} 类型引用（本类仅运行于物理客户端
     * JVM，serverOwner 非空 ⇒ server 必为 IntegratedServer）；反射失败按<b>非单机</b>
     * 保守拒绝（fail-closed：周目含删档语义，宁可停用不可半删档）。
     *
     * @return true = 未发布 LAN 的集成服（真单机）
     */
    public static boolean isStrictSinglePlayer() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || !server.isSinglePlayer()) {
            return false;
        }
        // dev 运行时方法名 MCP（getPublic）；prod 运行时 SRG（func_71344_c）
        for (String methodName : new String[] { "getPublic", "func_71344_c" }) {
            try {
                Object isPublic = server.getClass()
                    .getMethod(methodName)
                    .invoke(server);
                if (!(isPublic instanceof Boolean)) {
                    // invoke 对 boolean 返回类型按规范恒装箱 Boolean；非 Boolean 视为异常信号，
                    // 按 LAN 保守拒绝（fail-closed 口径，S11 复审 P3 修正原放行写法）
                    GTInterestingThing.LOG.error("[reincarnation] LAN 发布探测返回异常类型（非 Boolean），按非单机环境保守拒绝周目机制");
                    return false;
                }
                return !((Boolean) isPublic);
            } catch (NoSuchMethodException ignored) {
                // 换下一候选名（dev MCP 名 / prod SRG 名互斥存在）
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("[reincarnation] LAN 发布探测失败（" + methodName + "），按非单机环境保守拒绝周目机制", t);
                return false;
            }
        }
        // 双名均未命中（运行时被非常规改写）：按非单机保守拒绝
        GTInterestingThing.LOG.error("[reincarnation] LAN 发布探测不可用（getPublic/func_71344_c 均缺失），按非单机环境保守拒绝周目机制");
        return false;
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
        // v1.8.2 严格单机门控：非单机（开放 LAN / 防御纵深）登录编排整体跳过——
        // 不检测指纹、不发倒计时、不发奖励、不下发快照
        if (!isStrictSinglePlayer()) {
            GTInterestingThing.LOG.info("[reincarnation] 非单机环境，周目机制停用（本次登录不检测）");
            return;
        }
        // v1.8.5 修订轮：LogisticsPipes 1.5.35-GTNH 上游缺陷兜底——PlayerConfig.writeToFile
        // 全程无 mkdirs（字节码实证：mkdirs 仅存在于 readFromFile，writeToFile 于 :190 直接
        // new FileOutputStream），全新存档（logisticspipes/names 目录从未被创建，如轮回死亡
        // 后立即退档的短会话）在 FMLServerStopping 写玩家配置即 FileNotFoundException 崩服
        // （crash-2026-09-09_23.32.10-server 实证）。登录时幂等预建该标准目录：空目录对 LP
        // 无副作用，目录已存在时 mkdirs 返回 false 由 isDirectory 放行。
        try {
            File lpNamesDir = new File(
                player.worldObj.getSaveHandler()
                    .getWorldDirectory(),
                "logisticspipes/names");
            if (lpNamesDir.mkdirs() || lpNamesDir.isDirectory()) {
                GTInterestingThing.LOG.info("[reincarnation] LP names 目录已就绪： " + lpNamesDir.getAbsolutePath());
            } else {
                GTInterestingThing.LOG
                    .warn("[reincarnation] LP names 目录创建失败（LP 停服写配置可能 FNF）： " + lpNamesDir.getAbsolutePath());
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.warn("[reincarnation] LP names 目录预建跳过（不影响登录）", t);
        }
        try {
            ReincarnationCycle cycle = loadMerged(
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
                // v1.8.6 发放门控修订（严格单机已在上方把关）：门控信号在登录瞬间一次性
                // 读取、只判定这一次——playTicks（stat.playOneMinute）登录后每 tick 累计，
                // 属时变信号，延迟复核点重读会漂移误拦（实测 +200t 复核读到 198）。
                // totalWorldTime 仅作审计观测值，不参与判定；拦截只审计不调度、不 chat
                // （信箱保留，待真正符合形态的存档/登录）
                ReincarnationWorldData worldData = worldData();
                boolean grantClaimed = worldData != null && worldData.isGrantClaimed();
                boolean grantStarted = worldData != null && worldData.isGrantStarted();
                long playTicks = readTotalPlayTicks(player);
                long overworldTotalWorldTime = readOverworldTotalWorldTime();
                if (ReincarnationGrantGate.shouldGrant(true, grantClaimed, grantStarted, playTicks)) {
                    // EXECUTED 待领取：延迟 100 tick 发放（等客户端就绪后再结算与演出）
                    scheduledActions.add(new ScheduledAction(player, GRANT_DELAY_TICKS, () -> executeGrant(player)));
                    GTInterestingThing.LOG.info(
                        "[reincarnation] 登录发现待领取轮回奖励：player=" + player.getCommandSenderName()
                            + "，"
                            + GRANT_DELAY_TICKS
                            + " tick 后发放");
                } else {
                    GTInterestingThing.LOG.info(
                        "[reincarnation] 奖励发放门控拦截（非新档首次登录且非续跑，或已领取，信箱保留）：player=" + player.getCommandSenderName()
                            + "，playTicks="
                            + playTicks
                            + "，totalWorldTime="
                            + overworldTotalWorldTime
                            + "，grantClaimed="
                            + grantClaimed
                            + "，grantStarted="
                            + grantStarted);
                }
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
        rollSessions.remove(player.getUniqueID()); // 连掷会话不持久化：登出即弃（停止条件③，静默）
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
        tickRollSessions();
    }

    /** 到期任务驱动：玩家死亡/登出即作废；墙钟 deadline 与 tick 倒数两种口径 */
    private void tickScheduledActions() {
        if (scheduledActions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        // v1.8.5 修订轮 CME 修复：到期任务的 run() 允许再入队（实证链路：executeGrant
        // 运行中向本表 add completeGrant → ArrayList 迭代器 next() 抛
        // ConcurrentModificationException，crash-2026-09-09_23.50.37 实证）。口径改为
        // "先摘除后执行"：迭代阶段只做到期判定与摘除，run() 统一放到迭代结束后——
        // 此时再入队的动作落入活动表尾部，下一 tick 正常驱动，不破坏本轮迭代。
        List<ScheduledAction> dueActions = null;
        Iterator<ScheduledAction> iterator = scheduledActions.iterator();
        while (iterator.hasNext()) {
            ScheduledAction action = iterator.next();
            if (action.player.isDead) {
                iterator.remove();
                GTInterestingThing.LOG.info("[reincarnation] 到期任务作废（玩家已死亡）");
                continue;
            }
            boolean dueNow;
            if (action.deadlineMillis >= 0) {
                dueNow = now >= action.deadlineMillis;
            } else if (action.ticksRemaining > 0) {
                action.ticksRemaining--;
                dueNow = false;
            } else {
                dueNow = true;
            }
            if (!dueNow) {
                continue;
            }
            iterator.remove();
            if (dueActions == null) {
                dueActions = new ArrayList<>(2);
            }
            dueActions.add(action);
        }
        if (dueActions == null) {
            return;
        }
        for (ScheduledAction action : dueActions) {
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

    // ==================== 解锁连掷会话（v1.9.0 连掷重构） ====================

    /**
     * 连掷会话驱动（ServerTickEvent END 订阅内每 tick 一次）：对每个激活会话执行一次单掷闭包。
     * 停止条件落点：③死亡（isDead，静默摘除）在此；①无剩余可解锁目标/②背包无该币种由闭包
     * 返回 {@code false} 表达（容器侧已处理 no_coin 消息与静默语义）；④再按同按钮经
     * {@link #stopRollSession} 即时摘除。闭包异常按停止处理（fail-safe，不阻断其余会话）。
     */
    private void tickRollSessions() {
        if (rollSessions.isEmpty()) {
            return;
        }
        Iterator<PlayerRollSessions> iterator = rollSessions.values()
            .iterator();
        while (iterator.hasNext()) {
            PlayerRollSessions sessions = iterator.next();
            if (sessions.player.isDead) {
                iterator.remove(); // 停止条件③：死亡静默停止
                continue;
            }
            sessions.shimmer = driveOneRoll(sessions.shimmer);
            sessions.normal = driveOneRoll(sessions.normal);
            if (sessions.shimmer == null && sessions.normal == null) {
                iterator.remove();
            }
        }
    }

    /** 单会话单掷：空槽直通；闭包返回 false（或抛异常）即摘除该槽 */
    private RollSession driveOneRoll(RollSession session) {
        if (session == null) {
            return null;
        }
        boolean keep;
        try {
            keep = session.rollTick.getAsBoolean();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[reincarnation] 连掷单掷异常（会话停止）", t);
            keep = false;
        }
        return keep ? session : null;
    }

    /**
     * 开启连掷会话（ReincarnationContainer.toggleUnlock 经 enchantItem 的
     * {@code scheduleServerTask} 到达，恒在 server 线程）。严格单机门控（v1.8.2 同款）
     * 在连掷入口保留：非单机 fail-closed 不建会话。会话激活期间由 {@link #tickRollSessions}
     * 每 tick 驱动一次单掷；成功不停止，直至停止条件命中。
     */
    public static void startRollSession(EntityPlayer player, boolean shimmer, GTITItemList coinType,
        BooleanSupplier rollTick) {
        if (!(player instanceof EntityPlayerMP) || player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        if (!isStrictSinglePlayer()) {
            GTInterestingThing.LOG.info("[reincarnation] 非单机环境，拒绝开启连掷会话：player=" + player.getCommandSenderName());
            return;
        }
        EntityPlayerMP mp = (EntityPlayerMP) player;
        PlayerRollSessions sessions = INSTANCE.sessionsOf(mp, true);
        if ((shimmer ? sessions.shimmer : sessions.normal) != null) {
            // 防御路径（唯一调用方已先查 isRollSessionActive）：重复开启保持既有会话，不重复建
            GTInterestingThing.LOG.warn("[reincarnation] 连掷会话重复开启（保持既有会话）：player=" + mp.getCommandSenderName());
            return;
        }
        if (shimmer) {
            sessions.shimmer = new RollSession(coinType, rollTick);
        } else {
            sessions.normal = new RollSession(coinType, rollTick);
        }
        GTInterestingThing.LOG.info(
            "[reincarnation] 连掷会话开启：player=" + mp.getCommandSenderName()
                + "，coin="
                + coinType
                + "（每 tick 扣 1 币掷 1 次，成功不停掷）");
    }

    /** 停止单币种连掷会话（停止条件④：再按同按钮立即停止；静默，幂等） */
    public static void stopRollSession(EntityPlayer player, boolean shimmer) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        PlayerRollSessions sessions = INSTANCE.rollSessions.get(player.getUniqueID());
        if (sessions == null) {
            return;
        }
        if (shimmer) {
            sessions.shimmer = null;
        } else {
            sessions.normal = null;
        }
        if (sessions.shimmer == null && sessions.normal == null) {
            INSTANCE.rollSessions.remove(player.getUniqueID());
        }
    }

    /**
     * 停止该玩家全部连掷会话（静默、幂等）：GUI 关闭（onContainerClosed 停止条件③）与
     * 登出清扫共用；会话不持久化，登出即弃。
     */
    public static void stopRollSessions(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        INSTANCE.rollSessions.remove(player.getUniqueID());
    }

    /** 该玩家该币种连掷会话是否激活（服务端注册表实况；GUI 激活位推送与 toggle 方向判定经此读取） */
    public static boolean isRollSessionActive(EntityPlayer player, boolean shimmer) {
        if (!(player instanceof EntityPlayerMP)) {
            return false;
        }
        PlayerRollSessions sessions = INSTANCE.rollSessions.get(player.getUniqueID());
        return sessions != null && (shimmer ? sessions.shimmer : sessions.normal) != null;
    }

    /**
     * 该玩家连掷激活位（bit 0 = 闪烁币会话，bit 1 = 普通币会话；编码唯一单源见
     * {@code ReincarnationLayout#BAR_ROLL_ACTIVE}）。
     */
    public static int rollActiveBits(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return 0;
        }
        PlayerRollSessions sessions = INSTANCE.rollSessions.get(player.getUniqueID());
        if (sessions == null) {
            return 0;
        }
        return (sessions.shimmer != null ? 1 : 0) | (sessions.normal != null ? 2 : 0);
    }

    /** 取（或按需建）该玩家会话槽；forceCreate=false 时缺失返回 null */
    private PlayerRollSessions sessionsOf(EntityPlayerMP player, boolean forceCreate) {
        UUID id = player.getUniqueID();
        PlayerRollSessions sessions = rollSessions.get(id);
        if (sessions == null && forceCreate) {
            sessions = new PlayerRollSessions(player);
            rollSessions.put(id, sessions);
        }
        return sessions;
    }

    /** 单币种连掷会话（{coinType, active} 形态；active 以注册表槽位在场表达） */
    private static final class RollSession {

        final GTITItemList coinType;
        /** 单掷闭包（容器侧权威模型执行扣币与概率判定）；返回 false = 停止条件①/②命中 */
        final BooleanSupplier rollTick;

        RollSession(GTITItemList coinType, BooleanSupplier rollTick) {
            this.coinType = coinType;
            this.rollTick = rollTick;
        }
    }

    /** 单玩家连掷会话槽（shimmer/normal 两会话相互独立，可同时激活） */
    private static final class PlayerRollSessions {

        final EntityPlayerMP player;
        RollSession shimmer;
        RollSession normal;

        PlayerRollSessions(EntityPlayerMP player) {
            this.player = player;
        }
    }

    // ==================== 确认轮回（ReincarnationConfirmHook 实现） ====================

    /**
     * S7 GUI 二次确认回调（经 {@link ReincarnationConfirmHook.Holder} 注入）。
     * 校验 DEPOSITED + 非空清单 + server 线程 + 背包轮回水晶门槛（C4）后收束本轮
     * 轮回并进入飞升。
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
        // v1.8.2 严格单机门控：非单机（开放 LAN）直接拒绝确认轮回，给玩家 lang 提示，
        // 不进入 beginAscension（杜绝 LAN 宾客轮回 → 集成服删档死锁）
        if (!isStrictSinglePlayer()) {
            GTInterestingThing.LOG.info("[reincarnation] 非单机环境，拒绝轮回确认：player=" + mp.getCommandSenderName());
            mp.addChatMessage(new ChatComponentTranslation("gtit.reincarnation.single_player_only"));
            return;
        }
        try {
            ReincarnationCycle cycle = loadMerged(
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
            // C4 轮回水晶门槛：全部确认校验（EXECUTED 锁等）通过后、推进状态前，
            // 以背包实时扫描为准（容器打开期间水晶可能已移位）检查持有 ≥1 枚；
            // 不足 → lang 提示并拒绝（不推进、不写信箱/指纹、不 beginAscension）
            if (countReincarnationCrystals(mp) < 1) {
                mp.addChatMessage(new ChatComponentTranslation("gtit.reincarnation.confirm.no_crystal"));
                GTInterestingThing.LOG.info("[reincarnation] 轮回确认被拒（背包无轮回水晶）：player=" + mp.getCommandSenderName());
                return;
            }
            long seed = mp.worldObj.getSeed();
            String fingerprint = cycle.confirmReincarnation(seed);
            // D1：确认推进 EXECUTED 与投胎信箱写入同一事务（save 内同一次原子写；
            // 失败抛出即整体未写，信箱不会半份落盘）
            saveSplit(cycle);
            // C4 水晶扣除置于推进+落盘成功之后：confirm/save 异常路径不多扣（扣而不
            // 推进的窗口已消除；执行失败 catch 分支此时无库存副作用）
            consumeReincarnationCrystal(mp);
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

    /**
     * 背包轮回水晶计数（C4；主背包 36 格含快捷栏，实时扫描为准——容器打开期间
     * 水晶可能已移位）。物品引用与猫猫币同款 {@code GTITItemList} 容器口径
     * （{@code isStackEqual}，与 {@code ReincarnationContainer.isNekoCoin} 一致）。
     */
    private static int countReincarnationCrystals(EntityPlayerMP player) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (GTITItemList.ReincarnationCrystal.isStackEqual(stack)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    /** 扣除 1 枚轮回水晶（C4；多格时按首个命中格经 {@code decrStackSize} 扣减，空则清格） */
    private static void consumeReincarnationCrystal(EntityPlayerMP player) {
        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            if (!GTITItemList.ReincarnationCrystal.isStackEqual(player.inventory.mainInventory[slot])) {
                continue;
            }
            player.inventory.decrStackSize(slot, 1);
            return;
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

    /**
     * 奖励发放启动（D3 时序 ①②③④；登录延迟触发与崩溃重进续跑共用）：
     * <ol>
     * <li>① 确认可领（EXECUTED；已完成后幂等跳过——「重复发放不复制」）；</li>
     * <li>①' 不变量复核（可领取 + 本档未领取；门控修订后不再重读时变信号——形态判定
     * 已在登录瞬间一次性完成，v1.8.6 的 +200t 全量门控复核因 playTicks 漂移到 198
     * 误拦新档首登）+ 承诺点（置每存档 {@code GrantStarted} 并立即落盘——写点即承诺点，
     * 此后同档崩溃重进经登录门控 {@code grantStartedHere} 旁路续跑）；</li>
     * <li>② 幂等标记启动未完成（投胎信箱 {@code grantInFlight}，持久化；失败不启动演出）+
     * ②' 逐件发放账本（信箱 {@code delivered} 已交付前缀计数）：读已交付数，
     * 演出与逐件发放只覆盖剩余后缀——掉线/崩溃重进按账本续发，不重复发放已交付前缀；</li>
     * <li>③ 起演出（客户端只显示，发放以服务端计时为准；演出包只携带剩余后缀）；</li>
     * <li>④ 逐件发放调度：剩余第 j 件在 {@code j * GRANT_STAGGER_TICKS +
     * (int)(GRANT_PER_ITEM_TICKS * (1 - GRANT_PICKUP_LAST_FRACTION))} tick——即该件
     * 降落末段接近玩家时——经 {@link #grantShowItem} 入库（满包落地）并单调持久化账本；
     * 超过 {@link #GRANT_ANIMATED_ITEM_CAP} 的件无演出对应，随收束清扫发放；</li>
     * <li>⑤ 服务端计时 {@link #GRANT_PERFORMANCE_TICKS} 后经 {@link #completeGrant} 收束。</li>
     * </ol>
     * 崩溃/登出重进：信箱与账本仍在 → 本方法重入（标记已置位则不重复置），演出重放剩余
     * 后缀后逐件续发——信箱只在收束成功后才清空，重进不重复发放已交付前缀。
     */
    private void executeGrant(EntityPlayerMP player) {
        try {
            ReincarnationStore cycleStore = store();
            ReincarnationCycle cycle = loadMerged(
                player.getUniqueID()
                    .toString());
            if (!cycle.canClaimGrant()) {
                // ① 状态漂移 / 已完成（幂等）：静默跳过，不重复发放
                GTInterestingThing.LOG.info(
                    "[reincarnation] 奖励领取跳过（当前状态 " + cycle.getCycleState()
                        + "）：player="
                        + player.getCommandSenderName());
                return;
            }
            // ①' 不变量复核（门控修订）：只复核与"重复发放"相关的不变量（可领取 +
            // 本档未领取），不再重读 playTicks/totalWorldTime 时变信号（形态判定已在
            // 登录瞬间一次性完成）。拦截即审计并保留信箱——不置 GrantStarted、
            // 不置 grantInFlight、不起演出、不调度收束。
            ReincarnationWorldData worldData = worldData();
            boolean grantClaimed = worldData != null && worldData.isGrantClaimed();
            if (!(cycle.canClaimGrant() && !grantClaimed)) {
                GTInterestingThing.LOG.info(
                    "[reincarnation] 奖励发放复核拦截（不变量复核失败：可领取=" + cycle
                        .canClaimGrant() + "，已领取=" + grantClaimed + "，信箱保留）：player=" + player.getCommandSenderName());
                return;
            }
            // 承诺点：置每存档"发放已启动"标记并立即落盘（写点即承诺点）——此后同档
            // 崩溃重进经登录门控 grantStartedHere 旁路续跑，不再依赖时变信号。
            // overworld 缺失时跳过标记写入（信箱未清，行为安全：最坏情形是下次登录重走门控）。
            World grantOverworld = overworld();
            if (worldData != null && grantOverworld != null) {
                worldData.setGrantStarted(true);
                worldData.saveImmediately(grantOverworld);
            }
            // ② 幂等标记（持久化；已在发放中则保持，续跑不重复置位）
            cycleStore.markGrantInFlight(
                player.getUniqueID()
                    .toString());
            // ②' 逐件发放账本：读已交付前缀计数（掉线/崩溃重进续跑从剩余后缀继续，
            // 不重复发放已交付前缀——「重复发放不复制」不变量；计数超长按信箱长度收敛）
            List<ReincarnationCycle.ItemRef> pendingItems = cycle.getPendingItems();
            int deliveredBase = Math.min(
                cycleStore.readGrantDelivered(
                    player.getUniqueID()
                        .toString()),
                pendingItems.size());
            GrantShowContext showContext = new GrantShowContext(deliveredBase);
            // ③ 起演出（演出包只驱动客户端展示；只发剩余后缀——已交付前缀不再演出）
            List<GrantEffectPacket.ItemRef> fxItems = new ArrayList<>();
            for (int i = deliveredBase; i < pendingItems.size(); i++) {
                ReincarnationCycle.ItemRef ref = pendingItems.get(i);
                fxItems.add(new GrantEffectPacket.ItemRef(ref.getId(), ref.getMeta()));
            }
            if (!fxItems.isEmpty()) {
                ReincarnationNetwork.sendGrantEffectToClient(player, fxItems);
            }
            // ④ 逐件发放调度：剩余第 j 件在其降落末段（接近玩家）入库并持久化账本
            int perItemGrantDelay = (int) (GRANT_PER_ITEM_TICKS * (1.0F - GRANT_PICKUP_LAST_FRACTION));
            int staggeredCount = Math.min(fxItems.size(), GRANT_ANIMATED_ITEM_CAP);
            for (int j = 0; j < staggeredCount; j++) {
                final int itemIndex = deliveredBase + j;
                ReincarnationCycle.ItemRef ref = pendingItems.get(itemIndex);
                scheduledActions.add(
                    new ScheduledAction(
                        player,
                        j * GRANT_STAGGER_TICKS + perItemGrantDelay,
                        () -> grantShowItem(player, showContext, itemIndex, ref)));
            }
            // ⑤ 服务端计时收束（清扫未发放件 + 清信箱 + 完成幂等标记）
            scheduledActions
                .add(new ScheduledAction(player, GRANT_PERFORMANCE_TICKS, () -> completeGrant(player, showContext)));
            GTInterestingThing.LOG.info(
                "[reincarnation] 轮回奖励演出启动（逐件发放：已交付 " + deliveredBase
                    + "/"
                    + pendingItems.size()
                    + "，剩余 "
                    + fxItems.size()
                    + " 件按降落末段依次入库（演出上限 "
                    + staggeredCount
                    + " 件），收束于 "
                    + GRANT_PERFORMANCE_TICKS
                    + " tick）：player="
                    + player.getCommandSenderName());
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[reincarnation] 轮回奖励发放启动失败（信箱保留，重进后自动续跑）", t);
        }
    }

    /**
     * 演出中单件发放（逐件拾取）：该件降落末段接近玩家时入库（满包按既有溢出落地），
     * 并单调持久化信箱 {@code delivered} 已交付前缀计数——掉线/崩溃重进按账本续发剩余
     * 后缀，不重复发放。失效 registry id 条目审计隔离（收集至收束统一 chat 清账），
     * 随账本推进视同交付（不阻塞后续件节奏）。
     */
    private void grantShowItem(EntityPlayerMP player, GrantShowContext showContext, int itemIndex,
        ReincarnationCycle.ItemRef ref) {
        if (player.isDead) {
            return; // 玩家已死亡：调度表已作废，账本保持，重进后登录链路续跑
        }
        if (itemIndex < showContext.deliveredCount) {
            return; // 该件本轮已交付（防御重复调度）：不动账本，不重复发放
        }
        ItemStack stack = resolveStack(ref);
        if (stack == null) {
            String id = ref.getId() + ":" + ref.getMeta();
            showContext.droppedIds.add(id);
            GTInterestingThing.LOG.warn(
                "[reincarnation] 奖励物品无法解析 registry id，审计隔离（收束统一清账）：player=" + player.getCommandSenderName()
                    + ", item="
                    + id);
        } else if (!player.inventory.addItemStackToInventory(stack)) {
            // 背包满：按既有溢出口径随机落地——发放不丢账，落地即视同交付
            player.dropPlayerItemWithRandomChoice(stack, false);
        }
        showContext.deliveredCount = itemIndex + 1;
        store().markGrantDelivered(
            player.getUniqueID()
                .toString(),
            showContext.deliveredCount);
    }

    /**
     * 奖励发放收束（D3 时序 ⑤⑥⑦）：再确认在线 + 未完成 → 清扫逐件发放窗口内未入库的
     * 剩余件（超过演出上限的件、账本推进前的遗留件等）一次性发放
     * {@code addItemStackToInventory}（满则按既有溢出落地）→ <b>成功或落地后</b>才
     * {@code claimGrant+save} 清投胎信箱并完成幂等标记。异常路径不清信箱——登录链路
     * 重新触发 {@link #executeGrant}（失败保留语义，从持久化账本续发剩余后缀）。
     */
    private void completeGrant(EntityPlayerMP player, GrantShowContext showContext) {
        try {
            if (player.isDead) {
                return; // 玩家已死亡：信箱保留，死亡重生/重进后登录链路续跑
            }
            ReincarnationCycle cycle = loadMerged(
                player.getUniqueID()
                    .toString());
            // ⑤ 再确认未完成（防计时任务与登录链路双发）
            if (!cycle.canClaimGrant()) {
                return;
            }
            // ⑥ 清扫发放：已交付前缀之外的剩余件一次性入库（背包满则随机掉落——
            // 发放不丢账，落地即视同交付）
            List<ReincarnationCycle.ItemRef> pendingItems = cycle.getPendingItems();
            for (int i = Math.min(showContext.deliveredCount, pendingItems.size()); i < pendingItems.size(); i++) {
                ReincarnationCycle.ItemRef ref = pendingItems.get(i);
                ItemStack stack = resolveStack(ref);
                if (stack == null) {
                    String id = ref.getId() + ":" + ref.getMeta();
                    showContext.droppedIds.add(id);
                    GTInterestingThing.LOG.warn(
                        "[reincarnation] 奖励物品无法解析 registry id，隔离并清账：player=" + player.getCommandSenderName()
                            + ", item="
                            + id);
                    continue;
                }
                if (!player.inventory.addItemStackToInventory(stack)) {
                    player.dropPlayerItemWithRandomChoice(stack, false);
                }
            }
            if (!showContext.droppedIds.isEmpty()) {
                String dropped = joinIds(showContext.droppedIds);
                player.addChatMessage(new ChatComponentTranslation("gtit.reincarnation.grant.dropped", dropped));
                GTInterestingThing.LOG.warn(
                    "[reincarnation] 奖励失效条目已审计隔离并收束清账：player=" + player.getCommandSenderName() + ", ids=" + dropped);
            }
            // ⑦ 成功/落地/失效条目审计隔离后才清信箱 + 完成幂等标记
            cycle.claimGrant();
            saveSplit(cycle);
            // v1.8.6 收束写"已领取"标记。顺序铁律：先清信箱（上方 claimGrant+saveSplit）
            // 后写标记——两步之间崩溃时信箱已清而标记未写，下次登录 canClaimGrant=false
            // 触发不变量拦截自然不再发放（GrantStarted 承诺点已在 executeGrant 置位，
            // 该窗口无需时变信号兜底）；反向顺序会出现
            // "标记已写、信箱未清"，把待发奖励永久吞掉。
            ReincarnationWorldData grantedData = worldData();
            if (grantedData != null) {
                World grantedOverworld = overworld();
                if (grantedOverworld != null) {
                    grantedData.setGrantClaimed(true);
                    grantedData.saveImmediately(grantedOverworld);
                }
            }
            GTInterestingThing.LOG.info(
                "[reincarnation] 轮回奖励发放完成：EXECUTED→IDLE，player=" + player.getCommandSenderName()
                    + "，items="
                    + showContext.deliveredCount
                    + " 项（逐件发放账本收束，投胎信箱已清空，幂等标记复位）");
            sendSyncSnapshot(player, cycle);
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[reincarnation] 轮回奖励发放收束失败（信箱保留，重进后自动续跑）", t);
        }
    }

    private static String joinIds(List<String> ids) {
        StringBuilder joined = new StringBuilder();
        for (String id : ids) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(id);
        }
        return joined.toString();
    }

    /** 倒计时到点：该时间线已轮回过，执行升天（同确认路径） */
    private void executeCountdownDeadline(EntityPlayerMP player) {
        // v1.8.2 严格单机门控（防御纵深）：登录编排已在非单机环境整体跳过；但真单机登录
        // 放行后、30 秒倒计时窗口内对局域网开放（shareToLan）仍会走到本路径，故保留门控，
        // 异常路径到达亦拒绝升天
        if (!isStrictSinglePlayer()) {
            GTInterestingThing.LOG
                .info("[reincarnation] 非单机环境，拒绝倒计时到点升天（防御纵深）：player=" + player.getCommandSenderName());
            return;
        }
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
     * 每存档进度载体（懒取，幂等；overworld MapStorage，D1 每存档隔离）。
     * 集成服未运行/世界侧异常时返回 null（调用方按"仅许可字段"降级——
     * 进度展示空、进度保存跳过，不影响许可链路）。
     */
    private static ReincarnationWorldData worldData() {
        try {
            net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
            if (server == null) {
                return null;
            }
            return ReincarnationWorldData.get(server.getEntityWorld());
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[reincarnation] 每存档进度载体获取失败（按仅许可字段降级）", t);
            return null;
        }
    }

    /**
     * 玩家累计游玩 tick（v1.8.6 门控信号①）。映射名实证（本项目 build/rfg 反编译源）：
     * 注册字段为 MCP 名 {@code StatList.minutesPlayedStat}（StatList.java:32，stat id
     * {@code stat.playOneMinute}）；增量来源为 EntityPlayer.onUpdate 服务端每 tick
     * {@code addStat(minutesPlayedStat, 1)}（EntityPlayer.java:390），故存储原始值为
     * <b>累计 tick</b>。访问器取 compile classpath 实际形态 {@code EntityPlayerMP.func_147099_x()}
     * （返回 StatisticsFile/StatFileWriter；该反编译源未映射 MCP 名 getStatFile，全源无
     * getStatFile 符号，SRG 名 dev/prod 运行时一致）；读值用
     * {@code StatFileWriter.writeStat(StatBase)}（StatFileWriter.java:73，条目缺失返回 0
     * ——统计文件丢失/旧档缺统计即 0，误判形态兜底由每存档 GrantStarted 续跑标记承担）。
     */
    private static long readTotalPlayTicks(EntityPlayerMP player) {
        try {
            return player.func_147099_x()
                .writeStat(StatList.minutesPlayedStat);
        } catch (Throwable t) {
            GTInterestingThing.LOG.warn("[reincarnation] 玩家游玩统计读取失败（按 0 兜底，误判形态由 GrantStarted 续跑标记兜底）", t);
            return 0L;
        }
    }

    /**
     * 服务端 overworld（发放门控修订：GrantStarted 承诺点/已领取标记 saveImmediately
     * 落盘与审计时间观测共用；与 {@link #worldData()} 同源同降级口径——server 缺失/异常即 null）。
     */
    private static World overworld() {
        try {
            net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
            return server == null ? null : server.getEntityWorld();
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[reincarnation] overworld 获取失败（计时读取/标记落盘按降级处理）", t);
            return null;
        }
    }

    /**
     * overworld 累计运行 tick（{@code World.getTotalWorldTime()}，World.java:3949）。
     * 门控修订后不再参与发放判定（时变信号删除：+200t 复核点重读实测 playTicks 漂移
     * 198 误拦新档首登），仅作登录拦截审计日志的观测值；overworld 不可用按 0 兜底
     * （登录编排本身要求 server 在场，该分支仅防异常路径）。
     */
    private static long readOverworldTotalWorldTime() {
        World currentOverworld = overworld();
        return currentOverworld == null ? 0L : currentOverworld.getTotalWorldTime();
    }

    /**
     * D1 合并读：全局许可（指纹/投胎信箱）+ 每存档进度（WorldData 缺失时仅许可字段）。
     */
    private ReincarnationCycle loadMerged(String uuid) {
        ReincarnationCycle cycle = store().load(uuid);
        ReincarnationWorldData data = worldData();
        if (data != null) {
            data.applyTo(cycle);
        }
        return cycle;
    }

    /**
     * D1 拆分写：先全局许可（指纹/投胎信箱——失败抛出，确认与信箱同事务、失败不写半份），
     * 再每存档进度（WorldData；载体缺失时跳过进度落盘）。
     */
    private void saveSplit(ReincarnationCycle cycle) {
        store().save(cycle);
        ReincarnationWorldData data = worldData();
        if (data != null) {
            data.saveFrom(cycle);
        }
    }

    /**
     * 单次奖励演出逐件发放上下文（同一次 {@code executeGrant} 调度的各到期任务共享；
     * 仅 server 线程读写）：{@code deliveredCount} = 已交付前缀计数（信箱物品绝对下标
     * 口径，随逐件发放推进并与持久化账本同步）；{@code droppedIds} = 失效 registry id
     * 审计隔离清单（收束统一 chat 清账）。跨会话续跑账本以信箱持久化 {@code delivered}
     * 为准，本上下文仅覆盖单次演出窗口。
     */
    private static final class GrantShowContext {

        int deliveredCount;
        final List<String> droppedIds = new ArrayList<>();

        GrantShowContext(int deliveredCount) {
            this.deliveredCount = deliveredCount;
        }
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
