package com.miaokatze.gtit.trade.v2;

import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendError;
import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendSuccess;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.mail.BlessingConfig;
import com.miaokatze.gtit.signin.AnniversaryEntry;

/**
 * 编辑模式服务端操作处理器——祝福预设域（O2-05 E4：自 {@link NekoEditActionHandler} 祝福段逐字搬移，
 * v1.7.6 G5）
 * <p>
 * 祝福预设三目标编辑（生日模板 / 节日表条目 / 发件人显示名）：加载/保存 {@link BlessingConfig}。
 * 保存链为 {@link BlessingConfig#saveConfig()} 落盘即生效（内存静态字段直接生效，<b>无热重载</b>；
 * 单人存档下客户端静态配置同源即时生效，独立服务器下客户端本地预览可能滞后，祝福内容由
 * 服务端权威判定不受影响），与其他域的后置动作不统一，本域自带。
 * <p>
 * <b>线程安全</b>：所有方法均在服务器主线程执行（由 {@code scheduleServerTask} 保证）。
 */
final class BlessingEditActions {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private BlessingEditActions() {
        // 静态工具类，禁止实例化
    }

    /**
     * 保存祝福预设编辑（服务端）
     * <p>
     * targetId 三种形态：
     * <ul>
     * <li>{@code "festival:<index>"}：更新节日表第 index 条（0 起），JSON 载荷
     * {@code {"name":"元旦","monthDay":"01-01","title":"...","content":"...","currency":"neko","currencyAmount":5,"items":[{"item":"modid:name","meta":0,"amount":1}]}}</li>
     * <li>{@code "birthday"}：更新生日模板，JSON 载荷同上但无 name/monthDay</li>
     * <li>{@code "sender"}：更新发件人显示名，JSON 载荷 {@code {"sender":"猫猫售货机"}}</li>
     * </ul>
     * 保存后 {@link BlessingConfig#saveConfig()} 落盘（内存静态字段直接生效，无需 reload）。
     * 单人存档下客户端静态配置同源即时生效；独立服务器下客户端本地预览可能滞后，
     * 祝福内容由服务端权威判定不受影响。
     *
     * @param player      玩家
     * @param targetId    目标标识（"festival:&lt;index&gt;" / "birthday" / "sender"）
     * @param jsonPayload JSON 序列化的祝福数据
     */
    public static void saveBlessing(EntityPlayerMP player, String targetId, String jsonPayload) {
        try {
            JsonObject json = new JsonParser().parse(jsonPayload)
                .getAsJsonObject();

            // ---- 发件人显示名 ----
            if ("sender".equals(targetId)) {
                String sender = json.has("sender") ? json.get("sender")
                    .getAsString() : "";
                if (sender.isEmpty()) {
                    sendError(player, "发件人名称不能为空");
                    return;
                }
                BlessingConfig.setSender(sender);
                BlessingConfig.saveConfig();
                sendSuccess(player, "祝福邮件发件人已保存（" + sender + "）");
                LOG.info("[NekoEdit] 玩家 {} 保存祝福发件人: {}", player.getCommandSenderName(), sender);
                return;
            }

            // ---- 生日模板 ----
            if ("birthday".equals(targetId)) {
                BlessingConfig.BirthdayBlessing birthday = BlessingConfig.getBirthday();
                applyBirthdayEditJson(birthday, json);
                BlessingConfig.saveConfig();
                sendSuccess(player, "生日祝福模板已保存");
                LOG.info("[NekoEdit] 玩家 {} 保存生日祝福模板", player.getCommandSenderName());
                return;
            }

            // ---- 节日表条目 ----
            if (targetId != null && targetId.startsWith("festival:")) {
                int index;
                try {
                    index = Integer.parseInt(targetId.substring("festival:".length()));
                } catch (NumberFormatException e) {
                    sendError(player, "祝福节日标识非法: " + targetId);
                    return;
                }
                List<BlessingConfig.FestivalBlessing> festivals = BlessingConfig.getFestivals();
                if (index < 0 || index >= festivals.size()) {
                    sendError(player, "未找到节日表第 " + index + " 条（当前共 " + festivals.size() + " 条）");
                    return;
                }
                BlessingConfig.FestivalBlessing festival = festivals.get(index);
                if (json.has("name")) festival.name = json.get("name")
                    .getAsString();
                if (json.has("monthDay")) {
                    String monthDay = json.get("monthDay")
                        .getAsString();
                    // 触发日期必须合法 MM-dd，否则配置写死后永远无法触发
                    if (!AnniversaryEntry.isValidMonthDay(monthDay)) {
                        sendError(player, "触发日期格式非法（应为 MM-dd，如 01-01）: " + monthDay);
                        return;
                    }
                    festival.monthDay = monthDay;
                }
                applyFestivalEditJson(festival, json);
                BlessingConfig.saveConfig();
                sendSuccess(player, "节日祝福已保存（" + festival.name + " " + festival.monthDay + "）");
                LOG.info(
                    "[NekoEdit] 玩家 {} 保存节日祝福: index={}, name={}, monthDay={}",
                    player.getCommandSenderName(),
                    index,
                    festival.name,
                    festival.monthDay);
                return;
            }

            sendError(player, "未知的祝福编辑目标: " + targetId);
        } catch (Exception e) {
            sendError(player, "保存祝福预设失败: " + e.getMessage());
            LOG.error("[NekoEdit] 保存祝福预设异常", e);
        }
    }

    /**
     * 将 JSON 编辑字段应用到生日模板（标题/正文/附件/猫猫币）
     *
     * @param target 目标生日模板
     * @param json   JSON 载荷
     */
    private static void applyBirthdayEditJson(BlessingConfig.BirthdayBlessing target, JsonObject json) {
        if (json.has("title")) target.title = json.get("title")
            .getAsString();
        if (json.has("content")) target.content = json.get("content")
            .getAsString();
        if (json.has("currency")) target.currency = json.get("currency")
            .getAsString();
        if (json.has("currencyAmount")) target.currencyAmount = Math.max(
            0,
            json.get("currencyAmount")
                .getAsInt());
        if (json.has("items")) target.items = parseBlessingItems(json.getAsJsonArray("items"));
    }

    /**
     * 将 JSON 编辑字段应用到节日条目（标题/正文/附件/猫猫币；名称/日期由调用方单独处理）
     */
    private static void applyFestivalEditJson(BlessingConfig.FestivalBlessing target, JsonObject json) {
        if (json.has("title")) target.title = json.get("title")
            .getAsString();
        if (json.has("content")) target.content = json.get("content")
            .getAsString();
        if (json.has("currency")) target.currency = json.get("currency")
            .getAsString();
        if (json.has("currencyAmount")) target.currencyAmount = Math.max(
            0,
            json.get("currencyAmount")
                .getAsInt());
        if (json.has("items")) target.items = parseBlessingItems(json.getAsJsonArray("items"));
    }

    /**
     * 解析 JSON 数组为祝福附件物品列表（item/meta/amount，逐条容错跳过坏条目）
     */
    private static List<BlessingConfig.BlessingItem> parseBlessingItems(JsonArray array) {
        List<BlessingConfig.BlessingItem> items = new ArrayList<>();
        if (array == null) return items;
        for (int i = 0; i < array.size(); i++) {
            try {
                JsonObject itemJson = array.get(i)
                    .getAsJsonObject();
                String item = itemJson.has("item") ? itemJson.get("item")
                    .getAsString() : "";
                if (item.isEmpty()) continue;
                int meta = itemJson.has("meta") ? itemJson.get("meta")
                    .getAsInt() : 0;
                int amount = itemJson.has("amount") ? itemJson.get("amount")
                    .getAsInt() : 1;
                items.add(new BlessingConfig.BlessingItem(item, meta, amount));
            } catch (Exception ignored) {
                // 单条损坏不阻塞其余条目
            }
        }
        return items;
    }
}
