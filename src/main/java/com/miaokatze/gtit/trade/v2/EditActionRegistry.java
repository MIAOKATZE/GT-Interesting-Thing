package com.miaokatze.gtit.trade.v2;

import java.util.HashMap;
import java.util.Map;

/**
 * 编辑 ACTION 策略注册表（O2-05 策略表化：替换 {@code NekoEditPacket} 的
 * 15 case switch 与 15 个 handleXxx 转发方法）
 * <p>
 * 新增 ACTION 的联动面从「常量 + switch + 转发方法 + handler 入口」四处降为
 * 「{@code NekoEditPacket} 常量 + 域类方法 + 本注册表一行」三处且全部同域；
 * 与 {@link EditAction} 4 参签名完全一致的方法用方法引用注册，其余用 lambda
 * 适配（targetId/targetIndex/jsonPayload 的取用差异显式落在注册行）。
 * <p>
 * {@link #get(int)} 返回 null 即未知 ACTION——统一校验入口
 * {@link NekoEditActionHandler#validateEditRequest} 的白名单闸按注册表存在性判定
 * （原 0..14 连续区间判定收编，消除对未来非连续 ACTION 号的隐性约束）。
 */
final class EditActionRegistry {

    private static final Map<Integer, EditAction> ACTIONS = new HashMap<>();

    static {
        // ---- 交易域（O2-05 E2 迁入 TradeEditActions） ----
        register(
            NekoEditPacket.ACTION_OPEN_TRADE_EDITOR,
            (player, targetId, targetIndex, jsonPayload) -> TradeEditActions
                .openTradeEditor(player, targetId, targetIndex));
        register(NekoEditPacket.ACTION_SAVE_TRADE, TradeEditActions::saveTrade);
        register(
            NekoEditPacket.ACTION_CREATE_TRADE,
            (player, targetId, targetIndex, jsonPayload) -> TradeEditActions
                .createTrade(player, targetId, jsonPayload));
        // ---- 标签页域（O2-05 E2 迁入 PageEditActions） ----
        register(
            NekoEditPacket.ACTION_CREATE_PAGE,
            (player, targetId, targetIndex, jsonPayload) -> PageEditActions.createPage(player, jsonPayload));
        register(
            NekoEditPacket.ACTION_SAVE_PAGE,
            (player, targetId, targetIndex, jsonPayload) -> PageEditActions.savePage(player, targetId, jsonPayload));
        register(
            NekoEditPacket.ACTION_DELETE_PAGE,
            (player, targetId, targetIndex, jsonPayload) -> PageEditActions.deletePage(player, targetId));
        // ---- 签到域（O2-05 E3 迁入 SignInEditActions） ----
        register(
            NekoEditPacket.ACTION_OPEN_SIGNIN_EDITOR,
            (player, targetId, targetIndex, jsonPayload) -> SignInEditActions.openSignInEditor(player, targetId));
        register(
            NekoEditPacket.ACTION_SAVE_SIGNIN_REWARD,
            (player, targetId, targetIndex, jsonPayload) -> SignInEditActions
                .saveSignInReward(player, targetId, jsonPayload));
        // ---- 在线档位域（O2-05 E3 迁入 OnlineTierEditActions） ----
        register(
            NekoEditPacket.ACTION_SAVE_ONLINE_TIER,
            (player, targetId, targetIndex, jsonPayload) -> OnlineTierEditActions
                .saveOnlineTier(player, targetId, jsonPayload));
        // ---- 抽奖域（O2-05 E4 迁入 LotteryEditActions） ----
        register(
            NekoEditPacket.ACTION_OPEN_LOTTERY_EDITOR,
            (player, targetId, targetIndex, jsonPayload) -> LotteryEditActions.openLotteryEditor(player, targetId));
        register(
            NekoEditPacket.ACTION_SAVE_LOTTERY_ENTRY,
            (player, targetId, targetIndex, jsonPayload) -> LotteryEditActions
                .saveLotteryEntry(player, targetId, jsonPayload));
        register(
            NekoEditPacket.ACTION_SAVE_LOTTERY_POOL,
            (player, targetId, targetIndex, jsonPayload) -> LotteryEditActions
                .saveLotteryPool(player, targetId, jsonPayload));
        register(
            NekoEditPacket.ACTION_CREATE_LOTTERY_POOL,
            (player, targetId, targetIndex, jsonPayload) -> LotteryEditActions.createLotteryPool(player, jsonPayload));
        register(
            NekoEditPacket.ACTION_DELETE_LOTTERY_POOL,
            (player, targetId, targetIndex, jsonPayload) -> LotteryEditActions.deleteLotteryPool(player, targetId));
        // ---- 祝福域（O2-05 E4 迁入 BlessingEditActions） ----
        register(
            NekoEditPacket.ACTION_SAVE_BLESSING,
            (player, targetId, targetIndex, jsonPayload) -> BlessingEditActions
                .saveBlessing(player, targetId, jsonPayload));
    }

    private EditActionRegistry() {
        // 静态工具类，禁止实例化
    }

    private static void register(int action, EditAction impl) {
        ACTIONS.put(action, impl);
    }

    /**
     * 查询 ACTION 策略
     *
     * @param action 操作类型常量（{@link NekoEditPacket} ACTION_*）
     * @return 注册的策略；null=未知 ACTION（与统一校验入口白名单闸共用判定）
     */
    static EditAction get(int action) {
        return ACTIONS.get(action);
    }
}
