package com.miaokatze.gtit.trade.v2;

import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendError;
import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendSuccess;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.signin.DailySignInConfig;
import com.miaokatze.gtit.signin.SignInNetworkManager;
import com.miaokatze.gtit.signin.SignInReward;

/**
 * 编辑模式服务端操作处理器——签到域（O2-05 E3：自 {@link NekoEditActionHandler} 签到段逐字搬移）
 * <p>
 * 签到奖励编辑（v1.7.8 任务5+6 统一奖励模型）：连续/累计阶梯增删改、每月逐日覆盖、
 * 每月全局参数，加载/保存 {@link DailySignInConfig}。保存链为
 * {@code DailySignInConfig.saveConfig() 落盘 → SignInNetworkManager.sendSyncToAll() 广播}
 * （无注册表热重载），与其他域的后置动作不统一，本域自带。
 * <p>
 * <b>线程安全</b>：所有方法均在服务器主线程执行（由 {@code scheduleServerTask} 保证）。
 */
final class SignInEditActions {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private SignInEditActions() {
        // 静态工具类，禁止实例化
    }

    /**
     * 打开签到编辑面板（服务端）
     *
     * @param player 玩家
     * @param dayKey 签到天数标识
     */
    public static void openSignInEditor(EntityPlayerMP player, String dayKey) {
        LOG.info("[NekoEdit] 玩家 {} 打开签到编辑器: day={}", player.getCommandSenderName(), dayKey);
    }

    /**
     * 保存签到奖励编辑（服务端，v1.7.8 任务5+6：统一奖励模型 + 四类目标分派）
     * <p>
     * targetId 四种形态（旧 {@code "global"} 已废弃，由 {@code "monthly"} 取代——双端同版本部署）：
     * <ul>
     * <li>{@code "tier:<days>"}：连续签到阶梯增删改，载荷
     * {@code {"operation":"update|add|remove","days":<新天数>,"reward":{统一奖励模型}}}</li>
     * <li>{@code "cumtier:<days>"}：累计签到阶梯增删改，载荷同上</li>
     * <li>{@code "day:<n>"}：每月逐日覆盖奖励设置/清除，载荷
     * {@code {"operation":"update|remove","reward":{统一奖励模型}}}（remove=清除覆盖回退默认）</li>
     * <li>{@code "monthly"}：每月全局参数，载荷
     * {@code {"incrementEnabled":false,"consecutiveIncrement":1.0,"weekday":{奖励},"weekend":{奖励}}}</li>
     * </ul>
     * 统一奖励模型即 {@link SignInReward#toJson()} 结构：
     * {@code {"currency":"neko","amount":50,"items":[{"item":"modid:name","amount":1,"meta":0,"nbt":"..."}]}}。
     * 保存后 {@link DailySignInConfig#saveConfig()} 落盘并 {@link SignInNetworkManager#sendSyncToAll()}
     * 广播最新配置快照（对齐在线档位模式）。
     *
     * @param player      玩家
     * @param dayKey      目标标识（"tier:&lt;days&gt;" / "cumtier:&lt;days&gt;" / "day:&lt;n&gt;" / "monthly"）
     * @param jsonPayload JSON 序列化的奖励数据
     */
    public static void saveSignInReward(EntityPlayerMP player, String dayKey, String jsonPayload) {
        try {
            JsonObject json = new JsonParser().parse(jsonPayload)
                .getAsJsonObject();

            if ("monthly".equals(dayKey)) {
                saveSignInMonthly(player, json);
                return;
            }
            if (dayKey != null && dayKey.startsWith("tier:")) {
                saveSignInTier(player, dayKey.substring("tier:".length()), json, false);
                return;
            }
            if (dayKey != null && dayKey.startsWith("cumtier:")) {
                saveSignInTier(player, dayKey.substring("cumtier:".length()), json, true);
                return;
            }
            if (dayKey != null && dayKey.startsWith("day:")) {
                saveSignInDayOverride(player, dayKey.substring("day:".length()), json);
                return;
            }

            sendError(player, "未知的签到编辑目标: " + dayKey);
        } catch (Exception e) {
            sendError(player, "保存签到编辑失败: " + e.getMessage());
            LOG.error("[NekoEdit] 保存签到编辑异常", e);
        }
    }

    /**
     * 保存每月签到全局参数（targetId="monthly"）
     * <p>
     * 载荷：{@code {"incrementEnabled":false,"consecutiveIncrement":1.0,"weekday":{奖励},"weekend":{奖励}}}；
     * weekday/weekend 缺省时保持原值（{@link DailySignInConfig#setMonthlyGlobal} 对 null 忽略）。
     */
    private static void saveSignInMonthly(EntityPlayerMP player, JsonObject json) {
        boolean incrementEnabled = json.has("incrementEnabled") && json.get("incrementEnabled")
            .getAsBoolean();
        double increment = json.has("consecutiveIncrement") ? json.get("consecutiveIncrement")
            .getAsDouble() : 1.0;
        SignInReward weekday = parseReward(json, "weekday");
        SignInReward weekend = parseReward(json, "weekend");

        DailySignInConfig.setMonthlyGlobal(incrementEnabled, increment, weekday, weekend);
        DailySignInConfig.saveConfig();
        // 广播签到同步包（携带最新配置快照），刷新全服客户端配置缓存
        SignInNetworkManager.sendSyncToAll();
        sendSuccess(player, "每月签到全局配置已保存（递增 " + (incrementEnabled ? "开启" : "关闭") + "，系数 " + increment + "）");
        LOG.info(
            "[NekoEdit] 玩家 {} 保存每月签到全局配置: incrementEnabled={}, increment={}",
            player.getCommandSenderName(),
            incrementEnabled,
            increment);
    }

    /**
     * 连续/累计签到阶梯增删改（targetId="tier:&lt;days&gt;" / "cumtier:&lt;days&gt;"）
     * <p>
     * 载荷：{@code {"operation":"update|add|remove","days":<新天数>,"reward":{统一奖励模型}}}；
     * operation 缺省按 update 处理；update 允许同时修改天数（originalDays 仅作定位）。
     *
     * @param player     玩家
     * @param daysStr    原阶梯天数字符串（定位用）
     * @param json       载荷
     * @param cumulative true=累计阶梯；false=连续阶梯
     */
    private static void saveSignInTier(EntityPlayerMP player, String daysStr, JsonObject json, boolean cumulative) {
        String label = cumulative ? "累计" : "连续";
        int originalDays;
        try {
            originalDays = Integer.parseInt(daysStr);
        } catch (NumberFormatException e) {
            sendError(player, "签到" + label + "阶梯标识非法: " + daysStr);
            return;
        }
        String operation = json.has("operation") ? json.get("operation")
            .getAsString()
            .trim() : "update";

        // ---- 删除 ----
        if ("remove".equals(operation)) {
            boolean removed = cumulative ? DailySignInConfig.removeCumulativeTier(originalDays)
                : DailySignInConfig.removeTier(originalDays);
            if (!removed) {
                sendError(player, "未找到" + label + " " + originalDays + " 天的签到阶梯");
                return;
            }
            DailySignInConfig.saveConfig();
            SignInNetworkManager.sendSyncToAll();
            sendSuccess(player, "签到" + label + "阶梯已删除（" + originalDays + " 天）");
            LOG.info("[NekoEdit] 玩家 {} 删除签到{}阶梯: days={}", player.getCommandSenderName(), label, originalDays);
            return;
        }

        // update / add 公用字段解析
        int days = json.has("days") ? json.get("days")
            .getAsInt() : originalDays;
        if (days <= 0) {
            sendError(player, "阶梯天数非法: " + days);
            return;
        }
        SignInReward reward = parseReward(json, "reward");
        if (reward == null) reward = SignInReward.EMPTY;

        // ---- 新增 ----
        if ("add".equals(operation)) {
            if (cumulative) {
                DailySignInConfig.addCumulativeTier(days, reward);
            } else {
                DailySignInConfig.addTier(days, reward);
            }
            DailySignInConfig.saveConfig();
            SignInNetworkManager.sendSyncToAll();
            sendSuccess(player, "签到" + label + "阶梯已新增（" + days + " 天）");
            LOG.info("[NekoEdit] 玩家 {} 新增签到{}阶梯: days={}", player.getCommandSenderName(), label, days);
            return;
        }

        // ---- 更新（允许同时改天数） ----
        boolean updated = cumulative ? DailySignInConfig.updateCumulativeTier(originalDays, days, reward)
            : DailySignInConfig.updateTier(originalDays, days, reward);
        if (!updated) {
            sendError(player, "未找到" + label + " " + originalDays + " 天的签到阶梯");
            return;
        }
        DailySignInConfig.saveConfig();
        SignInNetworkManager.sendSyncToAll();
        sendSuccess(player, "签到" + label + "阶梯奖励已保存（" + days + " 天）");
        LOG.info("[NekoEdit] 玩家 {} 保存签到{}阶梯: {}→{} 天", player.getCommandSenderName(), label, originalDays, days);
    }

    /**
     * 每月逐日覆盖奖励设置/清除（targetId="day:&lt;n&gt;"，v1.7.8 任务6）
     * <p>
     * 载荷：{@code {"operation":"update|remove","reward":{统一奖励模型}}}；
     * remove（或缺省 operation 且无 reward）= 清除该日覆盖，回退工作日/周末默认。
     *
     * @param player 玩家
     * @param dayStr 月内日号字符串（1..31）
     * @param json   载荷
     */
    private static void saveSignInDayOverride(EntityPlayerMP player, String dayStr, JsonObject json) {
        int day;
        try {
            day = Integer.parseInt(dayStr);
        } catch (NumberFormatException e) {
            sendError(player, "逐日覆盖日号非法: " + dayStr);
            return;
        }
        if (day < 1 || day > 31) {
            sendError(player, "逐日覆盖日号超出范围（1..31）: " + day);
            return;
        }
        String operation = json.has("operation") ? json.get("operation")
            .getAsString()
            .trim() : "update";

        // ---- 清除覆盖（回退默认） ----
        if ("remove".equals(operation)) {
            DailySignInConfig.removeDayOverride(day);
            DailySignInConfig.saveConfig();
            SignInNetworkManager.sendSyncToAll();
            sendSuccess(player, "每月 " + day + " 日的覆盖奖励已清除（回退默认）");
            LOG.info("[NekoEdit] 玩家 {} 清除逐日覆盖: day={}", player.getCommandSenderName(), day);
            return;
        }

        // ---- 设置覆盖 ----
        SignInReward reward = parseReward(json, "reward");
        if (reward == null) reward = SignInReward.EMPTY;
        DailySignInConfig.setDayOverride(day, reward);
        DailySignInConfig.saveConfig();
        SignInNetworkManager.sendSyncToAll();
        sendSuccess(player, "每月 " + day + " 日的覆盖奖励已保存");
        LOG.info("[NekoEdit] 玩家 {} 保存逐日覆盖: day={}", player.getCommandSenderName(), day);
    }

    /**
     * 从载荷中解析统一奖励模型（{@link SignInReward#fromJson} 的键缺省安全封装）
     *
     * @param json 载荷
     * @param key  奖励对象键名
     * @return 解析出的奖励；键缺失或非对象时返回 null（调用方按「不修改」或 EMPTY 处理）
     */
    private static SignInReward parseReward(JsonObject json, String key) {
        if (json == null || key == null
            || !json.has(key)
            || !json.get(key)
                .isJsonObject()) {
            return null;
        }
        return SignInReward.fromJson(json.getAsJsonObject(key));
    }
}
