package com.miaokatze.gtit.trade;

import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;

/**
 * GTNHLib Teams 统一门面（O2-04）
 * <p>
 * 收编此前散落在 7 个业务文件、11 个调用点的 {@code try-catch (NoClassDefFoundError)} 降级样板：
 * 一次可用性探测（{@link #isAvailable()}，首个调用方 CommonProxy init 期注册团队数据时触发），
 * 之后所有 Teams 访问由布尔驱动，异常统一在本类捕获并按统一格式记录日志。
 * <p>
 * 各业务点的回退值语义逐一保留，不因收编而改变：
 * <ul>
 * <li>钱包 → 回退个人钱包（getTeam/getData 返回 null）</li>
 * <li>抽奖保底键 → 回退玩家自身 UUID（{@link #resolveTeamKey}）</li>
 * <li>交易限额 → 回退 1（getTeam 返回 null，调用方自行取默认）</li>
 * <li>钱包模式 → 回退 PERSONAL（客户端路径 NekoVMGuiV2#getWalletMode 维持异常驱动不变，不经本门面）</li>
 * <li>交易历史 → 回退个人文件（getTeam/getData 返回 null）</li>
 * </ul>
 * <p>
 * 实现注意：本类方法签名引用的 {@link Team} 等类型在 GTNHLib 缺席时按 HotSpot 惰性解析规则
 * 不会在类加载期触发解析；即使个别环境在探测后仍抛 {@link NoClassDefFoundError}，
 * 各方法体内的 {@code catch (Throwable)} 也会兜底返回与既有降级路径一致的结果。
 */
public final class TeamDataProvider {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private TeamDataProvider() {}

    /** Teams API 可用性缓存（null = 尚未探测；探测只做一次） */
    private static volatile Boolean available = null;

    /**
     * GTNHLib Teams API 是否可用（首个调用触发一次探测并缓存）
     * <p>
     * 探测只加载 {@code TeamManager} 类（不初始化，不触发其静态注册表），
     * 足以判断运行环境是否携带 GTNHLib。
     */
    public static boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) return cached;
        try {
            Class.forName("com.gtnewhorizon.gtnhlib.teams.TeamManager", false, TeamDataProvider.class.getClassLoader());
            cached = Boolean.TRUE;
        } catch (Throwable t) {
            LOG.warn("[TeamDataProvider] GTNHLib Teams API 不可用，团队功能统一降级为个人模式", t);
            cached = Boolean.FALSE;
        }
        available = cached;
        return cached;
    }

    /**
     * 解析玩家所在团队
     *
     * @return 团队；玩家无团队、Teams 不可用或查询异常时返回 null（调用方按既有口径降级）
     */
    public static Team getTeam(UUID playerId) {
        if (playerId == null || !isAvailable()) return null;
        try {
            return TeamManager.getTeamByPlayer(playerId);
        } catch (Throwable t) {
            LOG.error("[TeamDataProvider] 解析玩家团队失败: " + playerId, t);
            return null;
        }
    }

    /**
     * 按团队 ID 解析团队
     *
     * @return 团队；团队不存在、Teams 不可用或查询异常时返回 null
     */
    public static Team getTeamById(UUID teamId) {
        if (teamId == null || !isAvailable()) return null;
        try {
            return TeamManager.getTeamById(teamId);
        } catch (Throwable t) {
            LOG.error("[TeamDataProvider] 解析团队失败: " + teamId, t);
            return null;
        }
    }

    /**
     * 取本模组挂载在团队上的 GTIT 数据（{@link NekoTeamData}）
     *
     * @return 团队数据；团队为 null、数据未注册到该团队、Teams 不可用或查询异常时返回 null
     */
    public static NekoTeamData getData(Team team) {
        if (team == null || !isAvailable()) return null;
        try {
            ITeamData data = team.getData(NekoTeamData.ID);
            return data instanceof NekoTeamData ? (NekoTeamData) data : null;
        } catch (Throwable t) {
            LOG.error("[TeamDataProvider] 解析 GTIT 团队数据失败", t);
            return null;
        }
    }

    /**
     * 解析团队维度存储键：玩家所在团队的 teamId；无团队、Teams 不可用或异常时回退玩家自身 UUID
     */
    public static UUID resolveTeamKey(UUID playerId) {
        if (playerId == null) return null;
        Team team = getTeam(playerId);
        return team != null ? team.getTeamId() : playerId;
    }

    /**
     * 遍历团队全体在线成员（Teams 不可用或异常时 no-op，调用方自行决定是否回退个人路径）
     */
    public static void forEachOnlineMember(Team team, Consumer<EntityPlayerMP> action) {
        if (team == null || action == null || !isAvailable()) return;
        try {
            TeamManager.forEachOnlineTeamMember(team, action);
        } catch (Throwable t) {
            LOG.error("[TeamDataProvider] 遍历在线团队成员失败", t);
        }
    }

    /**
     * 标记团队数据脏（GTNHLib 随世界存档落盘；Teams 不可用或异常时 no-op）
     */
    public static void markTeamDirty(Team team) {
        if (team == null || !isAvailable()) return;
        try {
            team.markDirty();
        } catch (Throwable t) {
            LOG.error("[TeamDataProvider] 标记团队数据脏失败", t);
        }
    }

    /**
     * 注册 GTIT 团队数据工厂（CommonProxy init 期调用）
     * <p>
     * Teams 不可用时打统一降级日志并跳过（钱包/历史/通知等全部功能按既有口径回退个人模式）。
     */
    public static void registerTeamData() {
        if (!isAvailable()) {
            LOG.warn("[2/3] GTNHLib Teams API 不可用，猫猫币钱包将回退到个人模式");
            return;
        }
        try {
            com.gtnewhorizon.gtnhlib.teams.TeamDataRegistry.register(NekoTeamData.ID, NekoTeamData::new);
            LOG.info("[2/3] 猫猫币团队数据已注册到 GTNHLib Teams");
        } catch (Throwable t) {
            LOG.error("[2/3] 注册猫猫币团队数据失败", t);
        }
    }
}
