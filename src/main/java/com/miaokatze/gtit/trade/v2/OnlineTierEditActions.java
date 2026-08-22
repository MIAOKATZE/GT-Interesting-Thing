package com.miaokatze.gtit.trade.v2;

import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendError;
import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendSuccess;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.signin.OnlineTimeConfig;
import com.miaokatze.gtit.signin.SignInNetworkManager;

/**
 * 编辑模式服务端操作处理器——每日在线奖励档位域（O2-05 E3：自 {@link NekoEditActionHandler}
 * 在线档位段逐字搬移并独立成域，v1.7.7 G5②）
 * <p>
 * 在线时长奖励档位的增删改：加载/保存 {@link OnlineTimeConfig}。保存链为
 * {@code OnlineTimeConfig.saveConfig() 落盘 → SignInNetworkManager.sendSyncToAll() 广播}
 * （档位数据搭载签到同步包下发，与其他域的后置动作不统一，本域自带）。
 * <p>
 * <b>线程安全</b>：所有方法均在服务器主线程执行（由 {@code scheduleServerTask} 保证）。
 */
final class OnlineTierEditActions {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private OnlineTierEditActions() {
        // 静态工具类，禁止实例化
    }

    /**
     * 保存每日在线奖励档位编辑（服务端）
     * <p>
     * targetId 为原档位所需秒数字符串（用于定位）。JSON 载荷中 {@code operation} 字段指定动作：
     * <ul>
     * <li>{@code "update"}：更新现有档位，json 需含 {@code seconds/currency/amount/item/itemAmount/itemMeta/itemNbt}</li>
     * <li>{@code "add"}：新增档位，字段同 update</li>
     * <li>{@code "remove"}：删除 targetId 对应的档位</li>
     * </ul>
     * 保存后 {@link OnlineTimeConfig#saveConfig()} 落盘，并广播签到同步包刷新客户端。
     *
     * @param player      玩家
     * @param targetId    目标档位秒数字符串
     * @param jsonPayload JSON 序列化的档位数据
     */
    public static void saveOnlineTier(EntityPlayerMP player, String targetId, String jsonPayload) {
        try {
            JsonObject json = new JsonParser().parse(jsonPayload)
                .getAsJsonObject();
            String operation = json.has("operation") ? json.get("operation")
                .getAsString()
                .trim() : "update";

            int originalSeconds = 0;
            try {
                originalSeconds = Integer.parseInt(targetId);
            } catch (NumberFormatException ignored) {
                // add 时 targetId 可能为空串
            }

            if ("remove".equals(operation)) {
                if (!OnlineTimeConfig.removeTier(originalSeconds)) {
                    sendError(player, "未找到在线档位: " + originalSeconds + " 秒");
                    return;
                }
                OnlineTimeConfig.saveConfig();
                SignInNetworkManager.sendSyncToAll();
                sendSuccess(player, "在线档位已删除（" + formatDuration(originalSeconds) + "）");
                LOG.info("[NekoEdit] 玩家 {} 删除在线档位: {}s", player.getCommandSenderName(), originalSeconds);
                return;
            }

            // update / add 公用字段解析
            int seconds = json.has("seconds") ? json.get("seconds")
                .getAsInt() : originalSeconds;
            String currency = json.has("currency") ? json.get("currency")
                .getAsString() : NekoCurrencyRegistrar.NEKO_ID;
            int amount = json.has("amount") ? json.get("amount")
                .getAsInt() : 0;
            String item = json.has("item") ? json.get("item")
                .getAsString() : "";
            int itemAmount = json.has("itemAmount") ? json.get("itemAmount")
                .getAsInt() : 0;
            int itemMeta = json.has("itemMeta") ? json.get("itemMeta")
                .getAsInt() : 0;
            String itemNbt = json.has("itemNbt") ? json.get("itemNbt")
                .getAsString() : "";

            if ("add".equals(operation)) {
                OnlineTimeConfig.addTier(seconds, currency, amount, item, itemAmount, itemMeta, itemNbt);
                OnlineTimeConfig.saveConfig();
                SignInNetworkManager.sendSyncToAll();
                sendSuccess(player, "在线档位已新增（" + formatDuration(seconds) + "）");
                LOG.info("[NekoEdit] 玩家 {} 新增在线档位: {}s", player.getCommandSenderName(), seconds);
                return;
            }

            // update
            if (!OnlineTimeConfig
                .updateTier(originalSeconds, seconds, currency, amount, item, itemAmount, itemMeta, itemNbt)) {
                sendError(player, "未找到在线档位: " + originalSeconds + " 秒");
                return;
            }
            OnlineTimeConfig.saveConfig();
            SignInNetworkManager.sendSyncToAll();
            sendSuccess(player, "在线档位已保存（" + formatDuration(seconds) + "）");
            LOG.info("[NekoEdit] 玩家 {} 保存在线档位: {}s", player.getCommandSenderName(), seconds);
        } catch (Exception e) {
            sendError(player, "保存在线档位失败: " + e.getMessage());
            LOG.error("[NekoEdit] 保存在线档位异常", e);
        }
    }

    /** 将秒数格式化为 "Xh Xm" / "Xm" 等可读文本（仅用于聊天反馈） */
    private static String formatDuration(int seconds) {
        if (seconds <= 0) return "0 秒";
        int hours = seconds / 3600;
        int minutes = seconds % 3600 / 60;
        if (hours <= 0) return minutes + " 分钟";
        if (minutes == 0) return hours + " 小时";
        return hours + " 小时 " + minutes + " 分钟";
    }
}
