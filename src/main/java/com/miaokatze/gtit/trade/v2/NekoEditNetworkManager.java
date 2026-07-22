package com.miaokatze.gtit.trade.v2;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * 编辑模式网络包管理器
 * <p>
 * 管理可视化配置编辑模式的客户端-服务端通信（FML SimpleNetworkWrapper 模式）：
 * <ul>
 * <li>{@link NekoEditPacket}（id=0，C→S）：编辑模式下的配置编辑请求</li>
 * </ul>
 * 在 {@code CommonProxy.init()} 中调用 {@link #init()} 注册。
 * <p>
 * 参照 {@code MailNetworkManager} 的通道注册模式。
 */
public class NekoEditNetworkManager {

    private static SimpleNetworkWrapper channel;
    private static final String CHANNEL_NAME = "gtit_neko_edit";
    private static boolean initialized = false;

    /** 包 ID：编辑操作请求（客户端→服务端） */
    private static final int ID_EDIT_ACTION = 0;

    /**
     * 注册网络通道与消息（幂等）
     */
    public static void init() {
        if (initialized) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        channel.registerMessage(NekoEditPacket.Handler.class, NekoEditPacket.class, ID_EDIT_ACTION, Side.SERVER);
        initialized = true;
    }

    /**
     * 客户端：向服务端发送编辑操作请求
     * <p>
     * 内部做侧检查，服务端误触不会发包。
     *
     * @param action      操作类型（{@link NekoEditPacket#ACTION_SAVE_TRADE} 等）
     * @param targetId    目标标识（交易组 UUID / 签到天数 / 抽奖条目索引）
     * @param targetIndex 目标索引（交易在组内的索引）
     * @param jsonPayload JSON 序列化的编辑参数
     */
    public static void sendToServer(int action, String targetId, int targetIndex, String jsonPayload) {
        if (!initialized || channel == null) return;
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.CLIENT) return;
        channel.sendToServer(new NekoEditPacket(action, targetId, targetIndex, jsonPayload));
    }

    /**
     * 客户端：请求打开交易编辑面板
     *
     * @param tradeGroupId 交易组 UUID 字符串
     * @param tradeIndex   交易在组内的索引
     */
    public static void sendOpenTradeEditor(String tradeGroupId, int tradeIndex) {
        sendToServer(NekoEditPacket.ACTION_OPEN_TRADE_EDITOR, tradeGroupId, tradeIndex, "");
    }

    /**
     * 客户端：发送保存交易编辑请求
     *
     * @param tradeGroupId 交易组 UUID 字符串
     * @param tradeIndex   交易在组内的索引
     * @param jsonPayload  JSON 序列化的编辑参数
     */
    public static void sendSaveTrade(String tradeGroupId, int tradeIndex, String jsonPayload) {
        sendToServer(NekoEditPacket.ACTION_SAVE_TRADE, tradeGroupId, tradeIndex, jsonPayload);
    }

    /**
     * 客户端：请求打开签到编辑面板
     *
     * @param dayKey 签到天数标识（如 "1"~"7" 或 "tier_1" 等）
     */
    public static void sendOpenSignInEditor(String dayKey) {
        sendToServer(NekoEditPacket.ACTION_OPEN_SIGNIN_EDITOR, dayKey, 0, "");
    }

    /**
     * 客户端：发送保存签到奖励编辑请求
     *
     * @param dayKey      签到天数标识
     * @param jsonPayload JSON 序列化的奖励数据
     */
    public static void sendSaveSignInReward(String dayKey, String jsonPayload) {
        sendToServer(NekoEditPacket.ACTION_SAVE_SIGNIN_REWARD, dayKey, 0, jsonPayload);
    }

    /**
     * 客户端：请求打开抽奖编辑面板
     *
     * @param entryKey 抽奖条目标识
     */
    public static void sendOpenLotteryEditor(String entryKey) {
        sendToServer(NekoEditPacket.ACTION_OPEN_LOTTERY_EDITOR, entryKey, 0, "");
    }

    /**
     * 客户端：发送保存抽奖条目编辑请求
     *
     * @param entryKey    抽奖条目标识
     * @param jsonPayload JSON 序列化的条目数据
     */
    public static void sendSaveLotteryEntry(String entryKey, String jsonPayload) {
        sendToServer(NekoEditPacket.ACTION_SAVE_LOTTERY_ENTRY, entryKey, 0, jsonPayload);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static SimpleNetworkWrapper getChannel() {
        return channel;
    }
}
