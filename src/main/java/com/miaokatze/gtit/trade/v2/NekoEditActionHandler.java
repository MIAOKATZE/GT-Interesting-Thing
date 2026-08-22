package com.miaokatze.gtit.trade.v2;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 编辑模式服务端操作处理器——统一校验入口（O2-05 策略表化后壳）
 * <p>
 * 15 个编辑 ACTION 已按域拆至 {@code *EditActions} 域类并经 {@link EditActionRegistry}
 * 注册分发：交易 {@link TradeEditActions}、标签页 {@link PageEditActions}（E2）、
 * 签到 {@link SignInEditActions}、在线档位 {@link OnlineTierEditActions}（E3）、
 * 抽奖 {@link LotteryEditActions}、祝福 {@link BlessingEditActions}（E4）；
 * 公共工具（parseItemEntries/sendXxx 三件）在 {@link EditActionsCommon}。
 * 本类只保留 {@link #validateEditRequest} 统一校验入口与权限/限长常量。
 * <p>
 * <b>线程安全</b>：所有编辑方法均在服务器主线程执行（由 {@code scheduleServerTask} 保证）。
 * <p>
 * <b>统一校验入口</b>：{@link NekoEditPacket.Handler#processAction} 分发任何 ACTION 前
 * 先经 {@link #validateEditRequest} 校验（编辑模式 + 实时权限复核 + ACTION 白名单 +
 * payload 限长）。后续新增编辑动作须同时登记 {@link NekoEditPacket} 常量与
 * {@link EditActionRegistry} 注册行（白名单闸即注册表存在性判定）。
 */
public class NekoEditActionHandler {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private NekoEditActionHandler() {
        // 静态工具类，禁止实例化
    }

    // ==================== 统一校验入口（BUG B2 + 优化建议 一.3 校验入口子项） ====================

    /** 编辑入口要求的实时权限等级（与 /gtit nekovm edit 的 getRequiredPermissionLevel=2 对齐） */
    public static final int REQUIRED_PERMISSION_LEVEL = 2;
    /** jsonPayload 业务层长度上限（字符）：正常编辑载荷（含物品 NBT Base64）远小于此值，仅拦截滥用/恶意包 */
    public static final int MAX_JSON_PAYLOAD_LENGTH = 16384;
    /** targetId 业务层长度上限（字符）：交易组 UUID/天数/卡池 ID/页面 ID 等均远小于此值 */
    public static final int MAX_TARGET_ID_LENGTH = 256;

    /**
     * 编辑请求统一校验入口（BUG B2 修复：编辑模式标志不再是唯一闸门）。
     * <p>
     * 服务端在分发 {@link NekoEditPacket} 的任何 ACTION 之前调用，任一检查失败即拒绝：
     * <ul>
     * <li>编辑模式：玩家仍处于 {@link NekoEditModeManager} 编辑模式（原有闸门保留）</li>
     * <li>实时权限复核：{@code canCommandSenderUseCommand(2)}——编辑模式开启期间
     * OP 被降权/移除的玩家立即失去编辑能力（标志曾长期有效直至登出，是原漏洞核心）</li>
     * <li>ACTION 白名单：action 必须是 {@link EditActionRegistry} 已注册的操作类型
     * （O2-05：原"NekoEditPacket 已定义的 15 个常量之一"的 0..14 连续区间判定
     * 收编为注册表存在性判定，非连续 ACTION 号不再隐性受限）</li>
     * <li>payload 限长：targetId/jsonPayload 不超过业务层上限（FML 1.7.10 默认包长
     * 上限之内的第二道闸，拦截无意义巨型载荷进入 JSON 解析）</li>
     * </ul>
     *
     * @param player  发起玩家（服务器主线程）
     * @param message 编辑请求包
     * @return null=校验通过；非 null=拒绝原因（调用方直接以红色聊天展示给玩家）
     */
    public static String validateEditRequest(EntityPlayerMP player, NekoEditPacket message) {
        if (player == null || message == null) return "编辑请求无效";
        // 1. 编辑模式检查（原有闸门保留）
        if (!NekoEditModeManager.INSTANCE.isInEditMode(player.getUniqueID())) {
            return "你不在编辑模式中，无法执行编辑操作";
        }
        // 2. 实时权限复核：编辑模式入口 /gtit nekovm edit on 要求权限等级 2，
        // 开启期间被降权/移除 OP 的玩家不能凭旧标志继续改写全域配置
        try {
            if (!player.canCommandSenderUseCommand(REQUIRED_PERMISSION_LEVEL, "gtit")) {
                LOG.warn(
                    "[NekoEdit] 拒绝编辑请求：玩家 {} 实时权限不足（需要等级 {}）",
                    player.getCommandSenderName(),
                    REQUIRED_PERMISSION_LEVEL);
                return "你没有执行编辑操作的权限";
            }
        } catch (Throwable t) {
            // 权限复核本身异常时按拒绝处理（fail-closed）
            LOG.error("[NekoEdit] 权限复核异常，拒绝编辑请求", t);
            return "权限校验失败，无法执行编辑操作";
        }
        // 3. ACTION 白名单：只接受 EditActionRegistry 已注册的操作类型
        // （O2-05：原 0..14 连续区间判定收编为注册表存在性判定，非连续 ACTION 号不再隐性受限）
        int action = message.getAction();
        if (EditActionRegistry.get(action) == null) {
            LOG.warn("[NekoEdit] 拒绝未知编辑操作类型: {}（玩家 {}）", action, player.getCommandSenderName());
            return "未知的编辑操作类型";
        }
        // 4. payload 限长
        String targetId = message.getTargetId();
        if (targetId != null && targetId.length() > MAX_TARGET_ID_LENGTH) {
            return "编辑目标标识过长";
        }
        String jsonPayload = message.getJsonPayload();
        if (jsonPayload != null && jsonPayload.length() > MAX_JSON_PAYLOAD_LENGTH) {
            LOG.warn(
                "[NekoEdit] 拒绝超长编辑载荷: {} 字符（玩家 {}，上限 {}）",
                jsonPayload.length(),
                player.getCommandSenderName(),
                MAX_JSON_PAYLOAD_LENGTH);
            return "编辑数据载荷过长";
        }
        return null;
    }
}
