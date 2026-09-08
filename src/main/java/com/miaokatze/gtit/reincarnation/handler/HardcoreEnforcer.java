package com.miaokatze.gtit.reincarnation.handler;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

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
 * LivingDeathEvent 调用栈内直接删档）后调用
 * {@link MinecraftServer#deleteWorldAndStopServer()} 删除世界并停止服务器。</li>
 * </ol>
 * 事件注册自包含：{@link #beginEnforcement(EntityPlayerMP)} 内部懒执行
 * {@link #install()}，调用方无需再注册任何监听。
 * 玩家死亡（非单机路径）或世界卸载时自动停用，防泄漏。
 * <p>
 * 线程假设：beginEnforcement 与 tick/死亡事件均发生在 server 线程（单机场景），
 * 故不做同步。同时至多一名被强制玩家（单槽，单机周目语义）。
 */
public final class HardcoreEnforcer {

    /** 死亡后延时删档等待 tick 数（60 tick = 3 秒），规避在事件栈内直接删档（R4 风险规避） */
    private static final int DELETE_DELAY_TICKS = 60;

    /** 单例：事件监听器 + 单槽强制状态持有者 */
    private static final HardcoreEnforcer INSTANCE = new HardcoreEnforcer();

    /** 事件总线懒注册标记 */
    private static boolean installed;
    /** WorldInfo.hardcore 反射字段缓存（首次成功解析后复用） */
    private static Field hardcoreField;

    /** 当前被强制玩家（单槽；null = 无） */
    private EntityPlayerMP enforcedPlayer;
    /** 强制生效中（死亡删档倒计时期间保持 true，直至删档执行或强制终止） */
    private boolean active;
    /** 删档倒计时计数器；-1 = 未在倒计时 */
    private int deleteCountdownTicks = -1;

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

    /** 懒注册到 FML 总线（server tick）与 Forge 总线（LivingDeathEvent）；注册自包含，重复调用安全 */
    private static void install() {
        if (installed) {
            return;
        }
        installed = true;
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        GTInterestingThing.LOG.info("[reincarnation] 极限强制事件监听已注册（server tick + LivingDeath）");
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
            // 计数器归零：时间线终结，执行删档并停服（离开事件调用栈后延迟到达此点）
            deleteCountdownTicks = -1;
            active = false;
            enforcedPlayer = null;
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) {
                GTInterestingThing.LOG.error("[reincarnation] 删档倒计时归零但 MinecraftServer 为空，放弃删档");
                return;
            }
            GTInterestingThing.LOG.warn("[reincarnation] 时间线终结，删除世界并停止服务器");
            server.deleteWorldAndStopServer();
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
        deleteCountdownTicks = DELETE_DELAY_TICKS;
        GTInterestingThing.LOG
            .warn("[reincarnation] 被强制玩家死亡：60 tick 后删除世界并停止服务器，player=" + event.entity.getCommandSenderName());
    }
}
