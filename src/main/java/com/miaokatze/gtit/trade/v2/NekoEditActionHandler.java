package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.lottery.LotteryConfig;
import com.miaokatze.gtit.lottery.LotteryEntry;
import com.miaokatze.gtit.lottery.LotteryManager;
import com.miaokatze.gtit.lottery.LotteryNetworkManager;
import com.miaokatze.gtit.lottery.LotteryPool;
import com.miaokatze.gtit.lottery.LotteryRarity;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.signin.DailySignInConfig;
import com.miaokatze.gtit.signin.SignInNetworkManager;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/**
 * 编辑模式服务端操作处理器
 * <p>
 * 处理 {@link NekoEditPacket} 中各类编辑操作的服务端逻辑：
 * <ul>
 * <li>交易条目编辑：加载/保存 {@link NekoTradeConfig} 中的交易配置</li>
 * <li>签到奖励编辑：加载/保存 {@link DailySignInConfig} 中的阶梯奖励与全局参数</li>
 * <li>抽奖条目编辑：加载/保存抽奖配置（待实现）</li>
 * </ul>
 * <p>
 * <b>线程安全</b>：所有方法均在服务器主线程执行（由 {@code scheduleServerTask} 保证）。
 * <p>
 * <b>编辑模式校验</b>：调用前已由 {@link NekoEditPacket.Handler#processAction} 验证玩家处于编辑模式。
 */
public class NekoEditActionHandler {

    private NekoEditActionHandler() {
        // 静态工具类，禁止实例化
    }

    // ==================== 交易编辑 ====================

    /**
     * 打开交易编辑面板（服务端）
     * <p>
     * 当前实现为无操作：客户端已从本地显示数据构建编辑面板，
     * 无需服务端额外推送数据。保留此方法用于未来扩展（如推送服务端权威数据）。
     *
     * @param player     玩家
     * @param groupIdStr 交易组 UUID 字符串
     * @param tradeIndex 交易在组内的索引
     */
    public static void openTradeEditor(EntityPlayerMP player, String groupIdStr, int tradeIndex) {
        // 客户端已从本地 NekoTradeItemDisplay 数据构建编辑面板
        // 服务端无需额外操作（数据已通过 GUI 同步机制推送到客户端）
        GTInterestingThing.LOG.info(
            "[NekoEdit] 玩家 {} 打开交易编辑器: group={}, index={}",
            player.getCommandSenderName(),
            groupIdStr,
            tradeIndex);
    }

    /**
     * 保存交易编辑（服务端）
     * <p>
     * 解析 JSON 载荷，更新 {@link NekoTradeConfig} 中对应的交易条目，
     * 保存配置文件并热重载交易注册表。
     * <p>
     * JSON 载荷格式：
     * 
     * <pre>
     * {@code
     * {
     *   "tabId": 1,
     *   "orderId": 0,
     *   "cooldown": 300,
     *   "maxTrades": -1,
     *   "bqQuestId": "",
     *   "currencyType": "neko",
     *   "currencyAmount": 10,
     *   "fromItems": [{"item":"modid:name","meta":0,"amount":1,"nbtBase64":"..."}],
     *   "toItems": [{"item":"modid:name","meta":0,"amount":1,"nbtBase64":"..."}]
     * }
     * }
     * </pre>
     *
     * @param player      玩家
     * @param groupIdStr  交易组 UUID 字符串
     * @param tradeIndex  交易在组内的索引
     * @param jsonPayload JSON 序列化的编辑参数
     */
    public static void saveTrade(EntityPlayerMP player, String groupIdStr, int tradeIndex, String jsonPayload) {
        try {
            // 解析 JSON 载荷
            JsonObject json = new JsonParser().parse(jsonPayload)
                .getAsJsonObject();

            // 加载当前配置
            NekoTradeConfig.NekoTradeData data = NekoTradeConfig.load();
            if (data == null || data.getTrades() == null) {
                sendError(player, "交易配置为空，无法保存");
                return;
            }

            // 查找目标交易条目（按 ID 匹配）
            NekoTradeEntry targetEntry = null;
            for (NekoTradeEntry entry : data.getTrades()) {
                if (entry.getId() != null && entry.getId()
                    .equals(groupIdStr)) {
                    targetEntry = entry;
                    break;
                }
            }

            if (targetEntry == null) {
                sendError(player, "未找到交易条目: " + groupIdStr);
                return;
            }

            // 更新基础字段
            if (json.has("tabId")) targetEntry.setTabId(
                json.get("tabId")
                    .getAsInt());
            if (json.has("orderId")) targetEntry.setOrderId(
                json.get("orderId")
                    .getAsInt());
            if (json.has("cooldown")) targetEntry.setCooldown(
                json.get("cooldown")
                    .getAsInt());
            if (json.has("maxTrades")) targetEntry.setMaxTrades(
                json.get("maxTrades")
                    .getAsInt());
            if (json.has("bqQuestId")) targetEntry.setBqQuestId(
                json.get("bqQuestId")
                    .getAsString());

            // 更新货币花费
            if (json.has("currencyType") && json.has("currencyAmount")) {
                String currencyType = json.get("currencyType")
                    .getAsString();
                int currencyAmount = json.get("currencyAmount")
                    .getAsInt();
                if (currencyType != null && !currencyType.isEmpty() && currencyAmount > 0) {
                    targetEntry.setCurrency(new NekoTradeEntry.NekoCurrencyCost(currencyType, currencyAmount));
                } else {
                    targetEntry.setCurrency(null);
                }
            }

            // 更新输入物品列表
            if (json.has("fromItems")) {
                List<NekoTradeEntry.ItemEntry> fromItems = parseItemEntries(json.getAsJsonArray("fromItems"));
                targetEntry.setFromItems(fromItems);
            }

            // 更新输出物品列表
            if (json.has("toItems")) {
                List<NekoTradeEntry.ItemEntry> toItems = parseItemEntries(json.getAsJsonArray("toItems"));
                targetEntry.setToItems(toItems);
            }

            // 保存配置到文件
            NekoTradeConfig.save(data);

            // 热重载交易注册表
            NekoTradeRegistryV2.initialize();

            // v1.7.0 目标 5：向全体在线玩家广播服务端最新交易/标签页配置（S→C 只读同步，客户端不写盘）
            NekoTradeNetworkManager.sendSyncToAll();

            sendSuccess(player, "交易条目已保存 (ID: " + groupIdStr + ")");
            GTInterestingThing.LOG.info(
                "[NekoEdit] 玩家 {} 保存交易编辑: group={}, index={}",
                player.getCommandSenderName(),
                groupIdStr,
                tradeIndex);

        } catch (Exception e) {
            sendError(player, "保存交易编辑失败: " + e.getMessage());
            GTInterestingThing.LOG.error("[NekoEdit] 保存交易编辑异常", e);
        }
    }

    // ==================== 签到编辑 ====================

    /**
     * 打开签到编辑面板（服务端）
     *
     * @param player 玩家
     * @param dayKey 签到天数标识
     */
    public static void openSignInEditor(EntityPlayerMP player, String dayKey) {
        GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 打开签到编辑器: day={}", player.getCommandSenderName(), dayKey);
    }

    /**
     * 保存签到奖励编辑（服务端）
     * <p>
     * targetId 两种形态：
     * <ul>
     * <li>{@code "tier:<days>"}：更新指定连续天数的阶梯奖励，
     * JSON 载荷 {@code {"currency":"neko","amount":50,"item":"modid:name"|"","itemAmount":1,"itemMeta":0}}</li>
     * <li>{@code "global"}：更新全局参数，
     * JSON 载荷 {@code {"baseReward":10,"consecutiveIncrement":1.0}}</li>
     * </ul>
     * 保存后 {@link DailySignInConfig#saveConfig()} 落盘。单人存档下客户端静态配置同源即时生效；
     * 独立服务器下客户端本地配置预览的刷新属目标 5（配置同步），签到结果由服务端权威不受影响。
     *
     * @param player      玩家
     * @param dayKey      目标标识（"tier:&lt;days&gt;" / "global"）
     * @param jsonPayload JSON 序列化的奖励数据
     */
    public static void saveSignInReward(EntityPlayerMP player, String dayKey, String jsonPayload) {
        try {
            JsonObject json = new JsonParser().parse(jsonPayload)
                .getAsJsonObject();

            if ("global".equals(dayKey)) {
                // ---- 全局参数：每日基础奖励 + 连续递增系数 ----
                int baseReward = json.has("baseReward") ? json.get("baseReward")
                    .getAsInt() : 0;
                double increment = json.has("consecutiveIncrement") ? json.get("consecutiveIncrement")
                    .getAsDouble() : 0.0;
                DailySignInConfig.setGlobalRewards(baseReward, increment);
                DailySignInConfig.saveConfig();
                // v1.7.0 目标 5：广播签到同步包（携带最新配置快照），刷新全服客户端配置缓存
                SignInNetworkManager.sendSyncToAll();
                sendSuccess(player, "签到全局配置已保存（基础奖励 " + baseReward + "，系数 " + increment + "）");
                GTInterestingThing.LOG.info(
                    "[NekoEdit] 玩家 {} 保存签到全局配置: baseReward={}, increment={}",
                    player.getCommandSenderName(),
                    baseReward,
                    increment);
                return;
            }

            if (dayKey != null && dayKey.startsWith("tier:")) {
                // ---- 阶梯奖励：按天数整体替换 ----
                int days;
                try {
                    days = Integer.parseInt(dayKey.substring("tier:".length()));
                } catch (NumberFormatException e) {
                    sendError(player, "签到阶梯标识非法: " + dayKey);
                    return;
                }
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

                if (!DailySignInConfig.updateTier(days, currency, amount, item, itemAmount, itemMeta)) {
                    sendError(player, "未找到连续 " + days + " 天的签到阶梯");
                    return;
                }
                DailySignInConfig.saveConfig();
                // v1.7.0 目标 5：广播签到同步包（携带最新配置快照），刷新全服客户端配置缓存
                SignInNetworkManager.sendSyncToAll();
                sendSuccess(player, "签到阶梯奖励已保存（连续 " + days + " 天）");
                GTInterestingThing.LOG.info(
                    "[NekoEdit] 玩家 {} 保存签到阶梯: days={}, currency={}x{}, item={}x{}:{}",
                    player.getCommandSenderName(),
                    days,
                    currency,
                    amount,
                    item,
                    itemAmount,
                    itemMeta);
                return;
            }

            sendError(player, "未知的签到编辑目标: " + dayKey);
        } catch (Exception e) {
            sendError(player, "保存签到编辑失败: " + e.getMessage());
            GTInterestingThing.LOG.error("[NekoEdit] 保存签到编辑异常", e);
        }
    }

    // ==================== 抽奖编辑 ====================

    /**
     * 打开抽奖编辑面板（服务端）
     *
     * @param player   玩家
     * @param entryKey 抽奖条目标识
     */
    public static void openLotteryEditor(EntityPlayerMP player, String entryKey) {
        GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 打开抽奖编辑器: entry={}", player.getCommandSenderName(), entryKey);
    }

    /**
     * 保存抽奖条目编辑（服务端）
     * <p>
     * targetId 格式：{@code "<poolId>:<entryId>"}（按首个冒号切分）。
     * JSON 载荷：
     * 
     * <pre>
     * {@code
     * {
     *   "nekoCurrencyId": "",        // 非空 = 货币奖品（物品字段忽略）
     *   "item": "modid:name",        // 物品奖品（货币奖品时忽略）
     *   "meta": 0,
     *   "nbtBase64": "...",          // 可选物品 NBT
     *   "minAmount": 1,
     *   "maxAmount": 1,
     *   "weight": 100,
     *   "rarity": "COMMON"           // 大小写不敏感，未知回退 COMMON
     * }
     * }
     * </pre>
     * 
     * 保存后 {@link LotteryConfig#save} 落盘 → {@link LotteryManager#loadConfig()} 热重载
     * → {@link LotteryNetworkManager#sendSyncToClient} 推送最新卡池到编辑者客户端。
     *
     * @param player      玩家
     * @param entryKey    条目标识（"&lt;poolId&gt;:&lt;entryId&gt;"）
     * @param jsonPayload JSON 序列化的条目数据
     */
    public static void saveLotteryEntry(EntityPlayerMP player, String entryKey, String jsonPayload) {
        try {
            // 按首个冒号切分 poolId / entryId
            int sep = entryKey == null ? -1 : entryKey.indexOf(':');
            if (sep <= 0 || sep >= entryKey.length() - 1) {
                sendError(player, "抽奖条目标识非法: " + entryKey);
                return;
            }
            String poolId = entryKey.substring(0, sep);
            String entryId = entryKey.substring(sep + 1);

            JsonObject json = new JsonParser().parse(jsonPayload)
                .getAsJsonObject();

            // 加载当前配置并定位条目
            LotteryConfig.LotteryConfigData data = LotteryConfig.load();
            if (data == null || data.pools == null) {
                sendError(player, "抽奖配置为空，无法保存");
                return;
            }
            LotteryPool targetPool = null;
            for (LotteryPool pool : data.pools) {
                if (pool != null && poolId.equals(pool.getId())) {
                    targetPool = pool;
                    break;
                }
            }
            if (targetPool == null) {
                sendError(player, "未找到抽奖卡池: " + poolId);
                return;
            }
            LotteryEntry targetEntry = targetPool.getEntryById(entryId);
            if (targetEntry == null) {
                sendError(player, "未找到抽奖条目: " + entryId + "（卡池 " + poolId + "）");
                return;
            }

            // 奖品类型：货币 ID 非空 = 货币奖品，否则物品奖品
            String currencyId = json.has("nekoCurrencyId") ? json.get("nekoCurrencyId")
                .getAsString()
                .trim() : "";
            if (!currencyId.isEmpty()) {
                // 货币奖品：清物品字段
                targetEntry.setNekoCurrencyId(currencyId);
                targetEntry.setItem(null);
                targetEntry.setNbtBase64(null);
            } else {
                // 物品奖品：清货币字段，物品 ID 必填校验
                String item = json.has("item") ? json.get("item")
                    .getAsString()
                    .trim() : "";
                if (item.isEmpty()) {
                    sendError(player, "物品奖品必须提供物品 ID（或在货币 ID 栏填写货币）");
                    return;
                }
                targetEntry.setNekoCurrencyId(null);
                targetEntry.setItem(item);
                targetEntry.setMeta(
                    json.has("meta") ? json.get("meta")
                        .getAsInt() : 0);
                String nbt = json.has("nbtBase64") ? json.get("nbtBase64")
                    .getAsString() : null;
                targetEntry.setNbtBase64(nbt == null || nbt.isEmpty() ? null : nbt);
            }

            // 数量区间 / 权重 / 稀有度
            if (json.has("minAmount")) targetEntry.setMinAmount(
                json.get("minAmount")
                    .getAsInt());
            if (json.has("maxAmount")) targetEntry.setMaxAmount(
                json.get("maxAmount")
                    .getAsInt());
            if (json.has("weight")) targetEntry.setWeight(
                Math.max(
                    0,
                    json.get("weight")
                        .getAsInt()));
            if (json.has("rarity")) targetEntry.setRarity(
                LotteryRarity.fromString(
                    json.get("rarity")
                        .getAsString()));

            // 落盘 + 热重载 + 广播最新卡池配置（v1.7.0 目标 5：卡池全服一致，
            // 逐玩家推送各自团队维度的保底/历史/余额，编辑者也在广播范围内）
            LotteryConfig.save(data);
            LotteryManager.INSTANCE.loadConfig();
            LotteryNetworkManager.sendSyncToAll();

            sendSuccess(player, "抽奖条目已保存（卡池 " + poolId + "，条目 " + entryId + "）");
            if (!targetPool.validate()) {
                sendInfo(player, "警告：卡池 " + poolId + " 当前总权重为 0 或无有效条目，已暂时从抽奖中隐藏");
            }
            GTInterestingThing.LOG.info(
                "[NekoEdit] 玩家 {} 保存抽奖条目: pool={}, entry={}, currency={}, weight={}",
                player.getCommandSenderName(),
                poolId,
                entryId,
                currencyId.isEmpty() ? "(物品)" : currencyId,
                targetEntry.getWeight());

        } catch (Exception e) {
            sendError(player, "保存抽奖编辑失败: " + e.getMessage());
            GTInterestingThing.LOG.error("[NekoEdit] 保存抽奖编辑异常", e);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 解析 JSON 数组为 ItemEntry 列表
     *
     * @param array JSON 数组
     * @return ItemEntry 列表
     */
    private static List<NekoTradeEntry.ItemEntry> parseItemEntries(JsonArray array) {
        List<NekoTradeEntry.ItemEntry> items = new ArrayList<>();
        if (array == null) return items;

        for (int i = 0; i < array.size(); i++) {
            JsonObject itemJson = array.get(i)
                .getAsJsonObject();
            NekoTradeEntry.ItemEntry entry = new NekoTradeEntry.ItemEntry();
            if (itemJson.has("item")) entry.setItem(
                itemJson.get("item")
                    .getAsString());
            if (itemJson.has("meta")) entry.setMeta(
                itemJson.get("meta")
                    .getAsInt());
            if (itemJson.has("amount")) entry.setAmount(
                itemJson.get("amount")
                    .getAsInt());
            if (itemJson.has("nbtBase64")) entry.setNbtBase64(
                itemJson.get("nbtBase64")
                    .getAsString());
            items.add(entry);
        }
        return items;
    }

    private static void sendSuccess(EntityPlayerMP player, String message) {
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "[编辑模式] " + message));
    }

    private static void sendError(EntityPlayerMP player, String message) {
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[编辑模式] " + message));
    }

    private static void sendInfo(EntityPlayerMP player, String message) {
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "[编辑模式] " + message));
    }
}
