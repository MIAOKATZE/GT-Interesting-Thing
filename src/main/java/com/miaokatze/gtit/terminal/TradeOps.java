package com.miaokatze.gtit.terminal;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtit.lottery.LotteryManager;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;
import com.miaokatze.gtit.trade.v2.NekoTradeNetworkManager;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;
import com.miaokatze.gtit.util.PlayerResolver;

/**
 * 管理终端-交易页服务端 processor（T4 实装）
 * <p>
 * 承接 {@link TerminalActionHandler} 分发的交易/抽奖域动作，业务语义对齐命令行路径
 * （{@code GTITGiftCommand} 的 nekovm/lottery 域）：
 * <ul>
 * <li>{@code ACTION_TRADE_RELOAD}（对齐 {@code /gtit nekovm reload}，G:608-619）：
 * {@link NekoTradeRegistryV2#reload()} 成功后必须补发
 * {@link NekoTradeNetworkManager#sendSyncToAll()} 全服同步（v1.7.0 目标 5），
 * 两步缺一不可</li>
 * <li>{@code ACTION_LOTTERY_RELOAD}（对齐 {@code /gtit lottery reload}，G:893-897）：
 * 单调用 {@link LotteryManager#loadConfig()}——其内部自行调 {@code LotteryConfig.load()}
 * 重建卡池；不照抄命令版的 {@code LotteryConfig.reload()} + {@code loadConfig()} 双读盘</li>
 * <li>{@code ACTION_TRADE_TIME_RESET}（对齐 {@code /gtit nekovm timereset}，G:628-638）：
 * 支持离线目标（{@link PlayerResolver#resolvePlayerUuid} 在线直取/离线查 usercache），
 * 与签到页仅限在线目标不同；{@link NekoHistoryManager#resetAllHistory} 玩家有团队
 * →重置整队共享历史，无团队→个人全部历史并落盘</li>
 * </ul>
 * <p>
 * 约定：仅服务器主线程执行（投递方保证）；所有分支必发
 * {@link TerminalNetworkManager#sendResult}；不吞异常判成功——任何 Throwable
 * 上抛由 {@link TerminalActionHandler#processAction} 统一回 INTERNAL_FAILURE（fail-closed）。
 */
public final class TradeOps {

    /** TRADE_RELOAD 失败回执 */
    private static final String MSG_TRADE_RELOAD_FAIL = "交易注册表重载失败，详见服务端日志";
    /** TRADE_RELOAD 成功回执（重载 + 全服同步两步齐备） */
    private static final String MSG_TRADE_RELOAD_OK = "交易注册表已重载并同步全服";
    /** LOTTERY_RELOAD 成功回执 */
    private static final String MSG_LOTTERY_RELOAD_OK = "抽奖配置已重载";
    /** TIME_RESET 未指定目标回执 */
    private static final String MSG_TIME_RESET_NO_TARGET = "请指定目标玩家";
    /** TIME_RESET 目标解析失败回执（在线 + usercache 离线均未命中） */
    private static final String MSG_TIME_RESET_NOT_FOUND = "目标玩家不存在";

    private TradeOps() {
        // 静态工具类，禁止实例化
    }

    /**
     * 交易/抽奖域动作统一入口（服务器主线程，已过 Handler 五步校验）
     *
     * @param player  发起玩家
     * @param message 动作请求包
     */
    public static void process(EntityPlayerMP player, TerminalActionPacket message) {
        switch (message.getAction()) {
            case TerminalActionHandler.ACTION_TRADE_RELOAD -> handleTradeReload(player, message);
            case TerminalActionHandler.ACTION_LOTTERY_RELOAD -> handleLotteryReload(player, message);
            case TerminalActionHandler.ACTION_TRADE_TIME_RESET -> handleTimeReset(player, message);
            default -> TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_INVALID_REQUEST,
                TerminalText.MSG_INVALID_REQUEST);
        }
    }

    /**
     * 热重载交易配置（对齐 {@code /gtit nekovm reload}）
     * <p>
     * {@link NekoTradeRegistryV2#reload()} 不自带广播，成功后必须补发
     * {@link NekoTradeNetworkManager#sendSyncToAll()} 刷新全服客户端缓存；
     * 重载失败（内部已记日志）回 BUSINESS_FAILURE，不发同步包。
     */
    private static void handleTradeReload(EntityPlayerMP player, TerminalActionPacket message) {
        boolean success = NekoTradeRegistryV2.reload();
        if (!success) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_BUSINESS_FAILURE,
                MSG_TRADE_RELOAD_FAIL);
            return;
        }
        // v1.7.0 目标 5：重载后广播服务端最新交易/标签页配置，刷新全服客户端缓存
        NekoTradeNetworkManager.sendSyncToAll();
        TerminalNetworkManager
            .sendResult(player, message.getAction(), TerminalActionHandler.STATUS_SUCCESS, MSG_TRADE_RELOAD_OK);
    }

    /**
     * 热重载抽奖配置（对齐 {@code /gtit lottery reload}）
     * <p>
     * 单调用 {@link LotteryManager#loadConfig()}：其内部自行调
     * {@code LotteryConfig.load()} 读取 JSON 并重建内存卡池表，
     * 已覆盖命令版 {@code LotteryConfig.reload()} 的职责，不重复调用避免双读盘。
     */
    private static void handleLotteryReload(EntityPlayerMP player, TerminalActionPacket message) {
        LotteryManager.INSTANCE.loadConfig();
        TerminalNetworkManager
            .sendResult(player, message.getAction(), TerminalActionHandler.STATUS_SUCCESS, MSG_LOTTERY_RELOAD_OK);
    }

    /**
     * 重置目标玩家的交易历史与冷却（对齐 {@code /gtit nekovm timereset}，目标参数化）
     * <p>
     * 与签到页不同：本动作允许离线目标——{@link PlayerResolver#resolvePlayerUuid}
     * 在线玩家直取 UUID，离线玩家查 usercache.json，均未命中返回 null。
     * {@link NekoHistoryManager#resetAllHistory} 玩家有团队→重置整队共享历史，
     * 无团队→个人全部历史并落盘。
     */
    private static void handleTimeReset(EntityPlayerMP player, TerminalActionPacket message) {
        String targetName = message.getTargetPlayer() == null ? ""
            : message.getTargetPlayer()
                .trim();
        if (targetName.isEmpty()) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_INVALID_REQUEST,
                MSG_TIME_RESET_NO_TARGET);
            return;
        }
        UUID targetId = PlayerResolver.resolvePlayerUuid(targetName);
        if (targetId == null) {
            TerminalNetworkManager.sendResult(
                player,
                message.getAction(),
                TerminalActionHandler.STATUS_TARGET_NOT_FOUND,
                MSG_TIME_RESET_NOT_FOUND);
            return;
        }
        NekoHistoryManager.INSTANCE.resetAllHistory(targetId);
        TerminalNetworkManager.sendResult(
            player,
            message.getAction(),
            TerminalActionHandler.STATUS_SUCCESS,
            "已重置 " + targetName + " 的交易历史与冷却（若其属于团队则影响整队）");
    }
}
