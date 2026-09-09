package com.miaokatze.gtit.reincarnation.handler;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * 周目极限模式强制器（B批：轮回确认后的强制极限 + 死亡删档）。
 * <p>
 * S4 轮回确认流程调用 {@link #beginEnforcement(EntityPlayerMP)} 后本类自包含运作：
 * <ol>
 * <li>反射置位 {@code worldObj.getWorldInfo()} 的 {@code hardcore=true}
 * （字段为 private 且无 setter，SRG=field_76111_s / dev=hardcore 双候选）；</li>
 * <li>每 server tick 校验：hardcore 标志被改回 → 重新置位并 log；
 * 游戏模式被切离 SURVIVAL → 强制切回并 log（防止切创造飞行逃逸）；</li>
 * <li>被强制玩家死亡且为单机（integrated server）→ 延时 60 tick（计数器，不在
 * LivingDeathEvent 调用栈内直接删档）后发起<b>时间线终结</b>（A2 时序防御）：踢出
 * 全部在线玩家 → 记录待删世界目录（overworld SaveHandler#getWorldDirectory）→
 * {@code initiateShutdown()} 优雅停服；目录删除只发生在服务器完全停止后
 * （见 {@link #onServerStopped()}）。</li>
 * </ol>
 * 事件注册自包含：{@link #beginEnforcement(EntityPlayerMP)} 内部懒执行
 * {@link #install()}，调用方无需再注册任何监听。
 * 玩家死亡（非单机路径）或世界卸载时自动停用，防泄漏。
 * <p>
 * <b>A1 跨档泄漏止血</b>：静态单例的删档倒计时若在玩家退出/停转时未复位，会在
 * 同 JVM 下一个新服务器恢复 tick 时继续走完并误删新档（实证崩溃）。两道复位：
 * <ul>
 * <li>overworld {@code WorldEvent.Unload}（Forge 总线，1.7.10 stopServer 逐世界
 * 投递）→ {@link #resetEnforcement(String)}——事件总线可达，自包含生效；</li>
 * <li>{@link #onServerStopping()}/{@link #onServerStopped()}（幂等）——
 * FMLServerStoppingEvent/FMLServerStoppedEvent 在 1.7.10 <b>只投递给
 * &#64;Mod.EventHandler，不投递事件总线</b>（与 NekoWalletHandler 口径一致），
 * 由 mod 生命周期链路（CommonProxy.serverStopping/serverStopped）转发调用，
 * 其中 Stopped 链路同时承接待删目录的删除执行。</li>
 * </ul>
 * 另有 JVM 退出兜底钩子：停服收尾事件未达时尽力删除待删目录；JVM 被强杀则
 * 目录保留不删（宁漏删不错删）。
 * <p>
 * 线程假设：beginEnforcement 与 tick/死亡事件均发生在 server 线程（单机场景），
 * 故不做同步（待删标记 volatile：server 线程写、退出钩子线程读）。
 * 同时至多一名被强制玩家（单槽，单机周目语义）。
 */
public final class HardcoreEnforcer {

    /** 死亡后延时删档等待 tick 数（60 tick = 3 秒），规避在事件栈内直接删档（R4 风险规避） */
    private static final int DELETE_DELAY_TICKS = 60;

    /** lang 键：时间线终结踢出提示（双语 lang 文本由 lang 文件承载，此处只引用键名） */
    private static final String KEY_TIMELINE_KICK = "gtit.reincarnation.timeline.kick";

    /** 单例：事件监听器 + 单槽强制状态持有者 */
    private static final HardcoreEnforcer INSTANCE = new HardcoreEnforcer();

    /** 事件总线懒注册标记 */
    private static boolean installed;
    /** JVM 退出兜底钩子懒注册标记 */
    private static boolean shutdownHookInstalled;
    /** WorldInfo.hardcore 反射字段缓存（首次成功解析后复用） */
    private static Field hardcoreField;

    /** 当前被强制玩家（单槽；null = 无） */
    private EntityPlayerMP enforcedPlayer;
    /** 强制生效中（死亡删档倒计时期间保持 true，直至删档执行或强制终止） */
    private boolean active;
    /** 删档倒计时计数器；-1 = 未在倒计时 */
    private int deleteCountdownTicks = -1;

    /**
     * A1/A2：待删世界目录（时间线终结发起时经 overworld SaveHandler 的
     * {@code getWorldDirectory()} 记录，避免手工拼路径；null = 无待删档）。
     * 只在服务器完全停止后（{@link #onServerStopped()} / JVM 退出兜底钩子）
     * 递归删除并置 null；倒计时复位（止血）不清此标记，否则删档会静默丢失。
     */
    private static volatile File pendingDeleteDir;

    private HardcoreEnforcer() {}

    /**
     * 对指定玩家开启极限模式强制（幂等：同一玩家重复调用直接返回）。
     * <p>
     * 失败路径：反射定位/置位 hardcore 字段失败 → 记 ERROR 且不进入 active
     * （不静默假成功）；删档倒计时进行中 → 拒绝新的强制（避免取消待执行删档）。
     *
     * @param player 轮回确认后的玩家（须在 server 线程调用）
     */
    public static void beginEnforcement(EntityPlayerMP player) {
        install();
        if (player == null || player.worldObj == null) {
            GTInterestingThing.LOG.warn("[reincarnation] 极限强制开启失败：玩家或世界为空");
            return;
        }
        if (INSTANCE.deleteCountdownTicks >= 0) {
            GTInterestingThing.LOG.warn("[reincarnation] 删档倒计时进行中，拒绝新的极限强制：player=" + player.getCommandSenderName());
            return;
        }
        if (INSTANCE.active) {
            if (INSTANCE.enforcedPlayer == player) {
                // 幂等：同一玩家已在强制中
                return;
            }
            GTInterestingThing.LOG.warn(
                "[reincarnation] 极限强制目标替换：old=" + INSTANCE.enforcedPlayer.getCommandSenderName()
                    + "，new="
                    + player.getCommandSenderName());
        }
        WorldInfo info = player.worldObj.getWorldInfo();
        try {
            Field field = resolveHardcoreField();
            if (!field.getBoolean(info)) {
                field.setBoolean(info, true);
            }
        } catch (IllegalAccessException | ReflectionHelper.UnableToFindFieldException e) {
            GTInterestingThing.LOG.error("[reincarnation] 极限强制：反射置位 WorldInfo.hardcore 失败", e);
            return;
        }
        INSTANCE.enforcedPlayer = player;
        INSTANCE.active = true;
        GTInterestingThing.LOG
            .info("[reincarnation] 极限强制开启：hardcore=true，游戏模式锁定 SURVIVAL，player=" + player.getCommandSenderName());
    }

    /**
     * 强制是否生效中（含死亡删档倒计时期间）。
     *
     * @return true = 已有玩家处于极限强制生命周期内
     */
    public static boolean isActive() {
        return INSTANCE.active;
    }

    /** 懒注册到 FML 总线（server tick）与 Forge 总线（LivingDeathEvent/WorldEvent.Unload）；注册自包含，重复调用安全 */
    private static void install() {
        if (installed) {
            return;
        }
        installed = true;
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        installShutdownHook();
        GTInterestingThing.LOG
            .info("[reincarnation] 极限强制事件监听已注册（server tick + LivingDeath + WorldEvent.Unload + 退出兜底钩子）");
    }

    /** JVM 退出兜底钩子（懒注册一次）：停服收尾事件未达时尽力删除待删目录（宁漏删不错删） */
    private static void installShutdownHook() {
        if (shutdownHookInstalled) {
            return;
        }
        shutdownHookInstalled = true;
        Runtime.getRuntime()
            .addShutdownHook(
                new Thread(() -> deletePendingDirQuietly("JVM 退出兜底"), "gtit-reincarnation-pending-delete"));
    }

    /**
     * 解析 WorldInfo.hardcore 字段（缓存）。
     * SRG 名 field_76111_s 在 prod 环境命中；dev 名 hardcore 在 dev 环境命中。
     */
    private static Field resolveHardcoreField() {
        if (hardcoreField == null) {
            hardcoreField = ReflectionHelper.findField(WorldInfo.class, "field_76111_s", "hardcore");
        }
        return hardcoreField;
    }

    /** 每 server tick：先处理删档倒计时，再在 active 时强制 hardcore 标志与 SURVIVAL 游戏模式 */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (deleteCountdownTicks >= 0) {
            if (deleteCountdownTicks > 0) {
                deleteCountdownTicks--;
                return;
            }
            // 计数器归零：时间线终结发起（A2 时序防御：不在玩家在线态直接删档，
            // 改为踢人 → 记录待删目录 → 优雅停服，删除推迟到服务器完全停止后）
            deleteCountdownTicks = -1;
            active = false;
            enforcedPlayer = null;
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) {
                GTInterestingThing.LOG.error("[reincarnation] 删档倒计时归零但 MinecraftServer 为空，放弃删档");
                return;
            }
            initiateTimelineEnd(server);
            return;
        }
        if (!active) {
            return;
        }
        EntityPlayerMP player = enforcedPlayer;
        if (player == null || player.isDead || player.worldObj == null) {
            // 玩家死亡（非单机路径已先行停用）或世界卸载 → 自动停用，防泄漏
            active = false;
            enforcedPlayer = null;
            GTInterestingThing.LOG.info("[reincarnation] 极限强制停用：玩家死亡或世界卸载");
            return;
        }
        if (player.getHealth() <= 0.0F) {
            // P2（S11 审查）：伤害管线被第三方 mod 全量取消时 setHealth(0) 兜底不触发
            // LivingDeathEvent，此处 0 血检测直启删档倒计时，防周目删档语义被静默绕过
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && server.isSinglePlayer()) {
                kickImmediately(server);
                deleteCountdownTicks = DELETE_DELAY_TICKS;
                GTInterestingThing.LOG.warn(
                    "[reincarnation] 被强制玩家 0 血滞留（伤害管线被拦截）：已踢出并武装删档倒计时（60 tick 兜底），player="
                        + player.getCommandSenderName());
            } else {
                active = false;
                enforcedPlayer = null;
                GTInterestingThing.LOG.warn("[reincarnation] 极限强制：非单机环境 0 血滞留不触发删档，停用强制");
            }
            return;
        }
        try {
            WorldInfo info = player.worldObj.getWorldInfo();
            Field field = resolveHardcoreField();
            if (!field.getBoolean(info)) {
                field.setBoolean(info, true);
                GTInterestingThing.LOG.warn("[reincarnation] 极限强制：hardcore 标志被外部改回，已重新置位");
            }
        } catch (IllegalAccessException | ReflectionHelper.UnableToFindFieldException e) {
            GTInterestingThing.LOG.error("[reincarnation] 极限强制：tick 校验 WorldInfo.hardcore 失败", e);
        }
        if (player.theItemInWorldManager.getGameType() != WorldSettings.GameType.SURVIVAL) {
            player.theItemInWorldManager.setGameType(WorldSettings.GameType.SURVIVAL);
            GTInterestingThing.LOG.warn("[reincarnation] 极限强制：游戏模式被切离 SURVIVAL，已强制切回");
        }
    }

    /**
     * A1 止血（overworld 卸载 = 服务器停转开始）：复位删档倒计时与强制态，防静态
     * 倒计时冻结跨档（同 JVM 下一个新服务器恢复 tick 时误删新档的实证崩溃）。
     * <p>
     * 订阅口径：FMLServerStoppingEvent 在 1.7.10 不投递事件总线（只投递
     * &#64;Mod.EventHandler），故止血改走 Forge 总线可收的 {@code WorldEvent.Unload}
     * ——1.7.10 {@code MinecraftServer.stopServer()} 对每个世界投递后 flush，overworld
     * 卸载即停转信号；其他维度动态卸载不触发。{@link #onServerStopping()} 为同语义的
     * 生命周期转发入口，两者幂等。本处理器不清 {@code pendingDeleteDir}（删除见
     * {@link #onServerStopped()}）。
     */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null || event.world == null || event.world.isRemote) {
            return;
        }
        if (event.world != server.worldServers[0]) {
            return; // 仅 overworld 卸载视为停转信号
        }
        resetEnforcement("overworld 卸载（服务器停转，A1 止血）");
    }

    /**
     * 服务器停止中（{@code FMLServerStoppingEvent} 生命周期转发入口；幂等）。
     * <p>
     * 1.7.10 该事件只投递给 &#64;Mod.EventHandler、不投递事件总线，故本方法由 mod
     * 生命周期链路（{@code CommonProxy.serverStopping}）转发调用（接线与
     * NekoWalletManager.saveAll 同款转发形态）。A1 止血：复位倒计时与强制态。
     */
    public static void onServerStopping() {
        resetEnforcement("FMLServerStoppingEvent");
    }

    /**
     * 服务器已停止（{@code FMLServerStoppedEvent} 生命周期转发入口；幂等）。
     * <p>
     * 同上由 {@code CommonProxy.serverStopped} 转发调用。兜底复位 + 若存在待删目录
     * （{@link #initiateTimelineEnd(MinecraftServer)} 发起时记录）则递归删除并清空
     * 标记——此刻服务器已完全停止、世界句柄已关闭，删除才安全（Windows 文件锁）。
     * JVM 被强杀致本入口未达时由退出兜底钩子接力，仍未达则目录保留不删
     * （宁漏删不错删）。
     */
    public static void onServerStopped() {
        resetEnforcement("FMLServerStoppedEvent");
        deletePendingDirQuietly("FMLServerStoppedEvent");
    }

    /** 复位删档倒计时与强制态（止血；幂等；不清 {@code pendingDeleteDir}——删除见 deletePendingDirQuietly） */
    private static void resetEnforcement(String trigger) {
        boolean hadState = INSTANCE.active || INSTANCE.deleteCountdownTicks >= 0 || INSTANCE.enforcedPlayer != null;
        INSTANCE.deleteCountdownTicks = -1;
        INSTANCE.active = false;
        INSTANCE.enforcedPlayer = null;
        if (hadState) {
            GTInterestingThing.LOG.warn("[reincarnation] 极限强制/删档倒计时已复位（A1 止血，trigger=" + trigger + "）");
        }
    }

    /**
     * 时间线终结发起（A2 时序防御）：①踢出全部在线玩家（lang 键
     * {@code gtit.reincarnation.timeline.kick}，双语由 lang 文件承载）②记录待删
     * 世界目录（overworld SaveHandler 的 {@code getWorldDirectory()}，避免手工拼路径）
     * ③{@code initiateShutdown()} 优雅停服。目录删除只发生在服务器完全停止后
     * （{@link #onServerStopped()} / 退出兜底钩子）——原在线态直接
     * {@code deleteWorldAndStopServer()} 的路径废除（玩家中途退出会把静态倒计时
     * 冻结到下一个档，实证崩溃）。
     */
    private static void initiateTimelineEnd(MinecraftServer server) {
        try {
            IChatComponent kickMessage = new ChatComponentTranslation(KEY_TIMELINE_KICK);
            List<EntityPlayerMP> online = new ArrayList<>(server.getConfigurationManager().playerEntityList);
            for (EntityPlayerMP player : online) {
                player.playerNetServerHandler.kickPlayerFromServer(kickMessage.getFormattedText());
            }
            if (!online.isEmpty()) {
                GTInterestingThing.LOG.warn("[reincarnation] 时间线终结：已踢出 " + online.size() + " 名在线玩家");
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("[reincarnation] 时间线终结踢人失败（继续停服流程）", t);
        }
        try {
            pendingDeleteDir = server.getEntityWorld()
                .getSaveHandler()
                .getWorldDirectory();
        } catch (Throwable t) {
            // 目录记录失败：不删（宁漏删不错删），仍优雅停服收束时间线
            GTInterestingThing.LOG.error("[reincarnation] 时间线终结记录待删世界目录失败（宁漏删不错删，仅停服不删档）", t);
            server.initiateShutdown();
            return;
        }
        GTInterestingThing.LOG
            .warn("[reincarnation] 时间线终结已发起：待删目录=" + pendingDeleteDir + "（优雅停服后删除；若 JVM 被强杀致停服收尾未达则保留不删——宁漏删不错删）");
        server.initiateShutdown();
    }

    /**
     * 递归删除待删目录（幂等、尽力而为）：标记置 null 后重复调用为 no-op；
     * 删除不完全保留残留不重试（宁漏删不错删）。
     */
    private static void deletePendingDirQuietly(String trigger) {
        File dir = pendingDeleteDir;
        if (dir == null) {
            return;
        }
        pendingDeleteDir = null;
        if (!dir.exists()) {
            GTInterestingThing.LOG.info("[reincarnation] 待删世界目录已不存在（幂等跳过）：trigger=" + trigger + "，dir=" + dir);
            return;
        }
        if (deleteRecursively(dir)) {
            GTInterestingThing.LOG.warn("[reincarnation] 时间线终结收口：世界目录已删除（trigger=" + trigger + "），dir=" + dir);
        } else {
            GTInterestingThing.LOG
                .error("[reincarnation] 时间线终结：世界目录删除不完全（宁漏删不错删，保留残留）：trigger=" + trigger + "，dir=" + dir);
        }
    }

    /** 递归删除（文件/空目录直接删，目录先递归子项；全部成功返回 true） */
    private static boolean deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!deleteRecursively(child)) {
                    return false;
                }
            }
        }
        return file.delete();
    }

    /**
     * 被强制玩家死亡：单机环境下启动 60 tick 删档倒计时（不在本事件栈内直接删档）。
     * 非单机环境跳过删档并停用强制。
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!active || event.entity != enforcedPlayer) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || !server.isSinglePlayer()) {
            active = false;
            enforcedPlayer = null;
            GTInterestingThing.LOG.warn("[reincarnation] 极限强制：非单机环境死亡不触发删档，停用强制");
            return;
        }
        kickImmediately(server);
        deleteCountdownTicks = DELETE_DELAY_TICKS;
        GTInterestingThing.LOG
            .warn("[reincarnation] 被强制玩家死亡：已踢出并武装删档倒计时（60 tick 兜底），player=" + event.entity.getCommandSenderName());
    }

    /**
     * 死亡即踢出 + 立即登记待删目录（v1.8.5 修订轮，崩溃⑤/⑥防御）：
     * <p>
     * ①踢出：极限死亡屏按钮会走原版 {@code processClientStatus} 对单机房主的
     * {@code deleteWorldAndStopServer()} 在线删档脏路径，其卸载阶段 BetterAchievements
     * 在服务端线程保存全局配置与客户端拆除线程并发改 {@code Property.comment}（Forge
     * ConfigCategory.write:297 split(null) NPE，crash-2026-09-10_00.02.47 实证）。
     * ②立即登记 {@code pendingDeleteDir}：踢出会终结客户端会话，集成服随之立即停转
     * （00:53:36 实证：踢出同秒 FMLServerStopping，60t 倒计时走不完即被 A1 复位清掉，
     * 死亡档因此滞留、被重进后再次触发原版删档脏路径，crash-2026-09-10_00.54.39）——
     * 待删目录必须在踢出同刻登记，删除由 {@link #onServerStopped()} 收尾，无论服务器
     * 因踢出停转还是因倒计时停转都必然执行；60t 倒计时降级为踢出失败（服务器未停）
     * 时的兜底停服路径。
     */
    private static void kickImmediately(MinecraftServer server) {
        EntityPlayerMP player = INSTANCE.enforcedPlayer;
        if (player == null || player.playerNetServerHandler == null) {
            return;
        }
        try {
            IChatComponent kickMessage = new ChatComponentTranslation(KEY_TIMELINE_KICK);
            player.playerNetServerHandler.kickPlayerFromServer(kickMessage.getFormattedText());
            GTInterestingThing.LOG.info("[reincarnation] 死亡即踢出（死亡屏原版删档路径已绕开）：player=" + player.getCommandSenderName());
        } catch (Throwable t) {
            GTInterestingThing.LOG.warn("[reincarnation] 死亡即踢出失败（倒计时路径继续兜底）", t);
        }
        try {
            pendingDeleteDir = server.getEntityWorld()
                .getSaveHandler()
                .getWorldDirectory();
            GTInterestingThing.LOG.info(
                "[reincarnation] 待删世界目录已登记（停服后立即删除）："
                    + (pendingDeleteDir == null ? "null" : pendingDeleteDir.getAbsolutePath()));
        } catch (Throwable t) {
            GTInterestingThing.LOG.warn("[reincarnation] 待删目录登记失败（踢出已发生，停服后无删档）", t);
        }
    }
}
