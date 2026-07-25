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

    /**
     * 客户端：发送保存抽奖卡池编辑请求（v1.7.6 G2①）
     *
     * @param poolId      卡池 ID（不允许改 id，仅定位用）
     * @param jsonPayload JSON 序列化的卡池数据（名字/图标/消耗需求/保底）
     */
    public static void sendSaveLotteryPool(String poolId, String jsonPayload) {
        sendToServer(NekoEditPacket.ACTION_SAVE_LOTTERY_POOL, poolId, 0, jsonPayload);
    }

    /**
     * 客户端：发送新建抽奖卡池请求（v1.7.6 G2①）
     *
     * @param jsonPayload JSON 序列化的卡池数据（含新池 id）
     */
    public static void sendCreateLotteryPool(String jsonPayload) {
        sendToServer(NekoEditPacket.ACTION_CREATE_LOTTERY_POOL, "", 0, jsonPayload);
    }

    /**
     * 客户端：发送删除抽奖卡池请求（v1.7.6 G2①）
     *
     * @param poolId 待删除卡池 ID
     */
    public static void sendDeleteLotteryPool(String poolId) {
        sendToServer(NekoEditPacket.ACTION_DELETE_LOTTERY_POOL, poolId, 0, "");
    }

    /**
     * 客户端：发送新建交易条目请求（v1.7.6 G3④）
     * <p>
     * 物品数据（需求 16 格+产物 16 格）通过 PhantomItemSlot 编辑缓冲区自动同步，
     * jsonPayload 仅携带非物品参数（冷却/次数/recordNBT 等）。
     *
     * @param tabId       目标 page 的 tabId 字符串（新条目挂到该 page）
     * @param jsonPayload JSON 序列化的编辑参数
     */
    public static void sendCreateTrade(String tabId, String jsonPayload) {
        sendToServer(NekoEditPacket.ACTION_CREATE_TRADE, tabId, 0, jsonPayload);
    }

    /**
     * 客户端：发送新建标签页 page 请求（v1.7.6 G3④）
     * <p>
     * 图标物品通过 PhantomItemSlot 编辑缓冲区自动同步，jsonPayload 携带名字。
     *
     * @param jsonPayload JSON 序列化的 page 数据（name）
     */
    public static void sendCreatePage(String jsonPayload) {
        sendToServer(NekoEditPacket.ACTION_CREATE_PAGE, "", 0, jsonPayload);
    }

    /**
     * 客户端：发送保存标签页 page 编辑请求（v1.7.6 G3④）
     * <p>
     * 图标物品通过 PhantomItemSlot 编辑缓冲区自动同步，jsonPayload 携带名字。
     *
     * @param pageId      待编辑 page 的 ID 字符串
     * @param jsonPayload JSON 序列化的 page 数据（name）
     */
    public static void sendSavePage(String pageId, String jsonPayload) {
        sendToServer(NekoEditPacket.ACTION_SAVE_PAGE, pageId, 0, jsonPayload);
    }

    /**
     * 客户端：发送删除标签页 page 请求（v1.7.6 G3④）
     *
     * @param pageId 待删除 page 的 ID 字符串（默认页 1-3 服务端拒绝）
     */
    public static void sendDeletePage(String pageId) {
        sendToServer(NekoEditPacket.ACTION_DELETE_PAGE, pageId, 0, "");
    }

    /**
     * 客户端：发送保存祝福预设编辑请求（v1.7.6 G5）
     * <p>
     * 附件物品由客户端序列化 PhantomItemSlot 缓冲区进 jsonPayload 的 items 数组。
     *
     * @param targetId    目标标识（"festival:&lt;序号&gt;" / "birthday" / "sender"）
     * @param jsonPayload JSON 序列化的祝福数据
     */
    public static void sendSaveBlessing(String targetId, String jsonPayload) {
        sendToServer(NekoEditPacket.ACTION_SAVE_BLESSING, targetId, 0, jsonPayload);
    }

    /**
     * 客户端：发送保存每日在线奖励档位编辑请求（v1.7.7 G5②）
     * <p>
     * targetId 为原档位所需秒数字符串（用于定位），jsonPayload 中 {@code operation} 字段为
     * update / add / remove，并携带新档位字段。
     *
     * @param targetId    目标档位秒数字符串（add/remove/update 均用此定位；add 时可传空串）
     * @param jsonPayload JSON 序列化的档位数据
     */
    public static void sendSaveOnlineTier(String targetId, String jsonPayload) {
        sendToServer(NekoEditPacket.ACTION_SAVE_ONLINE_TIER, targetId, 0, jsonPayload);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static SimpleNetworkWrapper getChannel() {
        return channel;
    }
}
