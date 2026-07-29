package com.miaokatze.gtit.trade.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
import com.miaokatze.gtit.lottery.PityConfig;
import com.miaokatze.gtit.mail.BlessingConfig;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.signin.AnniversaryEntry;
import com.miaokatze.gtit.signin.DailySignInConfig;
import com.miaokatze.gtit.signin.OnlineTimeConfig;
import com.miaokatze.gtit.signin.SignInNetworkManager;
import com.miaokatze.gtit.signin.SignInReward;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.util.NbtBase64Util;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 编辑模式服务端操作处理器
 * <p>
 * 处理 {@link NekoEditPacket} 中各类编辑操作的服务端逻辑：
 * <ul>
 * <li>交易条目编辑：加载/保存 {@link NekoTradeConfig} 中的交易配置，新建交易条目</li>
 * <li>标签页编辑：新建/保存/删除 {@link NekoPageConfig} 中的标签页（v1.7.6 G3④）</li>
 * <li>签到奖励编辑：加载/保存 {@link DailySignInConfig} 中的连续/累计阶梯奖励、逐日覆盖与每月全局参数</li>
 * <li>抽奖条目/卡池编辑：加载/保存抽奖配置</li>
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
     * JSON 载荷格式（v1.7.6 G3：货币解绑 + NBT 选框）：
     *
     * <pre>
     * {@code
     * {
     *   "tabId": 1,
     *   "orderId": 0,
     *   "cooldown": 300,
     *   "maxTrades": -1,
     *   "bqQuestId": "",
     *   "recordNBT": false,          // v1.7.6 G3⑤：严格匹配 NBT 开关
     *   "fromItems": [{"item":"modid:name","meta":0,"amount":1,"nbtBase64":"..."}],  // 含猫猫币条目=货币需求（G3②）
     *   "toItems": [{"item":"modid:name","meta":0,"amount":1,"nbtBase64":"..."}]     // 含猫猫币条目=产出入钱包（G3②）
     * }
     * }
     * </pre>
     *
     * v1.7.6 G3② 货币解绑：不再消费 currencyType/currencyAmount——货币由 fromItems 中的
     * 猫猫币物品条目表达；保存时无条件清除条目旧 currency 字段，防止「fromItems 货币条目 +
     * 旧 currency 字段」在重载时被 {@link NekoTradeRegistryV2} 二次合成导致货币翻倍。
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
            GTInterestingThing.LOG.info(
                "[NekoEdit] 加载后条目总数: {}",
                data.getTrades()
                    .size());

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
                GTInterestingThing.LOG.warn("[NekoEdit] 未找到目标交易条目: group={}", groupIdStr);
                return;
            }
            int oldFromSize = targetEntry.getFromItems() == null ? 0
                : targetEntry.getFromItems()
                    .size();
            int oldToSize = targetEntry.getToItems() == null ? 0
                : targetEntry.getToItems()
                    .size();
            GTInterestingThing.LOG.info(
                "[NekoEdit] 找到目标条目: group={}, tabId={}, orderId={}, oldFrom={}, oldTo={}",
                groupIdStr,
                targetEntry.getTabId(),
                targetEntry.getOrderId(),
                oldFromSize,
                oldToSize);

            // 解析输入/输出物品列表（用于保存与诊断日志）
            List<NekoTradeEntry.ItemEntry> fromItems = new ArrayList<>();
            List<NekoTradeEntry.ItemEntry> toItems = new ArrayList<>();
            if (json.has("fromItems")) {
                fromItems = parseItemEntries(json.getAsJsonArray("fromItems"));
            }
            if (json.has("toItems")) {
                toItems = parseItemEntries(json.getAsJsonArray("toItems"));
            }

            GTInterestingThing.LOG.info(
                "[NekoEdit] 服务端保存交易: group={}, fromItems={}, toItems={}",
                groupIdStr,
                fromItems.size(),
                toItems.size());

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
            // v1.7.6 G3⑤ NBT 选框
            if (json.has("recordNBT")) targetEntry.setRecordNBT(
                json.get("recordNBT")
                    .getAsBoolean());

            // v1.7.6 G3② 货币解绑：无条件清除旧 currency 字段（理由见方法注释）
            targetEntry.setCurrency(null);

            // 更新输入物品列表（含猫猫币条目=货币需求）
            if (json.has("fromItems")) {
                targetEntry.setFromItems(fromItems);
            }

            // 更新输出物品列表（含猫猫币条目 = 产出入钱包）
            if (json.has("toItems")) {
                targetEntry.setToItems(toItems);
            }
            int newFromSize = targetEntry.getFromItems() == null ? 0
                : targetEntry.getFromItems()
                    .size();
            int newToSize = targetEntry.getToItems() == null ? 0
                : targetEntry.getToItems()
                    .size();
            GTInterestingThing.LOG
                .info("[NekoEdit] 更新目标条目字段完成: group={}, newFrom={}, newTo={}", groupIdStr, newFromSize, newToSize);

            // 保存配置到文件
            NekoTradeConfig.save(data);
            GTInterestingThing.LOG.info("[NekoEdit] 配置已落盘: group={}", groupIdStr);

            // 热重载交易注册表
            NekoTradeRegistryV2.initialize();

            // 检查该 groupId 是否重新注册成功
            NekoTradeGroup reloaded = NekoTradeDatabase.INSTANCE.getTradeGroup(UUID.fromString(groupIdStr));
            GTInterestingThing.LOG.info("[NekoEdit] 服务端重载交易组完成: group={}, found={}", groupIdStr, reloaded != null);

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

    // ==================== 交易条目新建与标签页编辑（v1.7.6 G3④） ====================

    /**
     * 新建交易条目（服务端）
     * <p>
     * 在指定标签页下追加一条新交易：分配新 UUID，orderId 取该标签页现有最大 orderId + 1
     * （新条目排在页尾）。JSON 载荷与 {@link #saveTrade} 一致（物品 16+16 格由客户端
     * 序列化 PhantomItemSlot 缓冲区进 fromItems/toItems）；tabId 由 targetId 指定，
     * 载荷中的 tabId 字段忽略，防止客户端越权改挂其他页。
     * <p>
     * v1.7.6 G3② 货币解绑：新条目不写旧 currency 字段——货币需求/产出统一由
     * fromItems/toItems 中的猫猫币物品条目表达（执行器实时识别分流）。
     *
     * @param player      玩家
     * @param tabIdStr    目标标签页 ID 字符串
     * @param jsonPayload JSON 序列化的条目数据
     */
    public static void createTrade(EntityPlayerMP player, String tabIdStr, String jsonPayload) {
        try {
            int tabId;
            try {
                tabId = Integer.parseInt(tabIdStr);
            } catch (NumberFormatException e) {
                sendError(player, "标签页标识非法: " + tabIdStr);
                return;
            }
            if (!NekoPageRegistry.hasPage(tabId)) {
                sendError(player, "目标标签页不存在: " + tabId);
                return;
            }

            JsonObject json = new JsonParser().parse(jsonPayload)
                .getAsJsonObject();

            NekoTradeConfig.NekoTradeData data = NekoTradeConfig.load();
            if (data == null || data.getTrades() == null) {
                sendError(player, "交易配置为空，无法新建");
                return;
            }

            // 新建条目（构造器自动分配 UUID），挂到指定标签页
            NekoTradeEntry entry = new NekoTradeEntry();
            entry.setTabId(tabId);
            // orderId 取该标签页现有最大值 + 1
            int maxOrder = -1;
            for (NekoTradeEntry e : data.getTrades()) {
                if (e.getTabId() == tabId && e.getOrderId() > maxOrder) {
                    maxOrder = e.getOrderId();
                }
            }
            entry.setOrderId(maxOrder + 1);

            // 基础参数（缺省值沿用 NekoTradeEntry 构造器默认）
            if (json.has("cooldown")) entry.setCooldown(
                json.get("cooldown")
                    .getAsInt());
            if (json.has("maxTrades")) entry.setMaxTrades(
                json.get("maxTrades")
                    .getAsInt());
            if (json.has("bqQuestId")) entry.setBqQuestId(
                json.get("bqQuestId")
                    .getAsString());
            // v1.7.6 G3⑤ NBT 选框（缺省 false=仅按物品匹配）
            if (json.has("recordNBT")) entry.setRecordNBT(
                json.get("recordNBT")
                    .getAsBoolean());

            // v1.7.6 G3② 货币解绑：新条目不写旧 currency 字段（构造器默认 null）
            if (json.has("fromItems")) {
                entry.setFromItems(parseItemEntries(json.getAsJsonArray("fromItems")));
            }
            if (json.has("toItems")) {
                entry.setToItems(parseItemEntries(json.getAsJsonArray("toItems")));
            }

            data.getTrades()
                .add(entry);

            // 落盘 → 热重载 → 全服广播（与 saveTrade 同一权威链）
            NekoTradeConfig.save(data);
            NekoTradeRegistryV2.initialize();
            NekoTradeNetworkManager.sendSyncToAll();

            sendSuccess(player, "交易条目已新建（标签页 " + NekoPageRegistry.getPageName(tabId) + "）");
            GTInterestingThing.LOG
                .info("[NekoEdit] 玩家 {} 新建交易条目: tabId={}, id={}", player.getCommandSenderName(), tabId, entry.getId());
        } catch (Exception e) {
            sendError(player, "新建交易条目失败: " + e.getMessage());
            GTInterestingThing.LOG.error("[NekoEdit] 新建交易条目异常", e);
        }
    }

    /**
     * 新建标签页 page（服务端）
     * <p>
     * 分配 id = max(现有最大 id, 3) + 1（默认页 1-3 已占用，新页从 4 起），isDefault=false。
     * JSON 载荷见 {@link #applyPageEditJson}；图标由客户端序列化 PhantomItemSlot 内容进
     * icon 字段。保存后落盘 → 热重载标签页注册表 → 全服广播（同步包同时携带交易+标签页配置）。
     *
     * @param player      玩家
     * @param jsonPayload JSON 序列化的 page 数据
     */
    public static void createPage(EntityPlayerMP player, String jsonPayload) {
        try {
            JsonObject json = new JsonParser().parse(jsonPayload)
                .getAsJsonObject();

            NekoPageConfig.NekoPageData data = NekoPageConfig.load();
            if (data == null || data.getPages() == null) {
                sendError(player, "标签页配置为空，无法新建");
                return;
            }

            // 分配 id：默认页 1-3 已占用，新页取 max(现有最大 id, 3) + 1
            int maxId = 3;
            for (NekoPageEntry p : data.getPages()) {
                if (p != null && p.getId() > maxId) maxId = p.getId();
            }

            NekoPageEntry entry = new NekoPageEntry();
            entry.setId(maxId + 1);
            entry.setName("新标签页");
            entry.setDefault(false);
            applyPageEditJson(entry, json);

            data.getPages()
                .add(entry);

            NekoPageConfig.save(data);
            NekoPageRegistry.reload();
            NekoTradeNetworkManager.sendSyncToAll();

            sendSuccess(player, "标签页已新建（#" + entry.getId() + " " + entry.getName() + "）");
            GTInterestingThing.LOG.info(
                "[NekoEdit] 玩家 {} 新建标签页: id={}, name={}",
                player.getCommandSenderName(),
                entry.getId(),
                entry.getName());
        } catch (Exception e) {
            sendError(player, "新建标签页失败: " + e.getMessage());
            GTInterestingThing.LOG.error("[NekoEdit] 新建标签页异常", e);
        }
    }

    /**
     * 保存标签页 page 编辑（服务端）
     * <p>
     * targetId = pageId 字符串，JSON 载荷见 {@link #applyPageEditJson}（图标/名字）。
     * 默认页（1-3）允许改图标/名字；删除拦截在 {@link #deletePage}。
     *
     * @param player      玩家
     * @param pageIdStr   待编辑 page 的 ID 字符串
     * @param jsonPayload JSON 序列化的 page 数据
     */
    public static void savePage(EntityPlayerMP player, String pageIdStr, String jsonPayload) {
        try {
            int pageId;
            try {
                pageId = Integer.parseInt(pageIdStr);
            } catch (NumberFormatException e) {
                sendError(player, "标签页标识非法: " + pageIdStr);
                return;
            }

            JsonObject json = new JsonParser().parse(jsonPayload)
                .getAsJsonObject();

            NekoPageConfig.NekoPageData data = NekoPageConfig.load();
            if (data == null || data.getPages() == null) {
                sendError(player, "标签页配置为空，无法保存");
                return;
            }
            NekoPageEntry target = findPageEntry(data, pageId);
            if (target == null) {
                sendError(player, "未找到标签页: " + pageId);
                return;
            }

            applyPageEditJson(target, json);

            NekoPageConfig.save(data);
            NekoPageRegistry.reload();
            NekoTradeNetworkManager.sendSyncToAll();

            sendSuccess(player, "标签页已保存（#" + pageId + " " + target.getName() + "）");
            GTInterestingThing.LOG.info(
                "[NekoEdit] 玩家 {} 保存标签页: id={}, name={}",
                player.getCommandSenderName(),
                pageId,
                target.getName());
        } catch (Exception e) {
            sendError(player, "保存标签页失败: " + e.getMessage());
            GTInterestingThing.LOG.error("[NekoEdit] 保存标签页异常", e);
        }
    }

    /**
     * 删除标签页 page（服务端）
     * <p>
     * 默认页（isDefault 或 id≤3）拒绝删除；删除后该页交易条目整体迁往 tabId=3（其他页），
     * 与 {@link NekoPageRegistry#deletePage(int)} 既有口径一致（交易不删除仅改挂）。
     *
     * @param player    玩家
     * @param pageIdStr 待删除 page 的 ID 字符串
     */
    public static void deletePage(EntityPlayerMP player, String pageIdStr) {
        try {
            int pageId;
            try {
                pageId = Integer.parseInt(pageIdStr);
            } catch (NumberFormatException e) {
                sendError(player, "标签页标识非法: " + pageIdStr);
                return;
            }

            NekoPageConfig.NekoPageData data = NekoPageConfig.load();
            if (data == null || data.getPages() == null) {
                sendError(player, "标签页配置为空，无法删除");
                return;
            }
            NekoPageEntry target = findPageEntry(data, pageId);
            if (target == null) {
                sendError(player, "未找到标签页: " + pageId);
                return;
            }
            if (target.isDefault() || pageId <= 3) {
                sendError(player, "默认标签页（ID 1-3）不可删除");
                return;
            }
            if (data.getPages()
                .size() <= 1) {
                sendError(player, "至少保留一个标签页，无法删除");
                return;
            }

            data.getPages()
                .remove(target);

            // 该页交易迁往 tabId=3（其他页），与 NekoPageRegistry.deletePage 口径一致
            NekoTradeConfig.NekoTradeData tradeData = NekoTradeConfig.load();
            if (tradeData != null && tradeData.getTrades() != null) {
                boolean moved = false;
                for (NekoTradeEntry trade : tradeData.getTrades()) {
                    if (trade.getTabId() == pageId) {
                        trade.setTabId(3);
                        moved = true;
                    }
                }
                if (moved) NekoTradeConfig.save(tradeData);
            }

            NekoPageConfig.save(data);
            NekoPageRegistry.reload();
            NekoTradeRegistryV2.initialize();
            NekoTradeNetworkManager.sendSyncToAll();

            sendSuccess(player, "标签页已删除（#" + pageId + " " + target.getName() + "），其交易已移至\"其他\"标签页");
            GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 删除标签页: id={}", player.getCommandSenderName(), pageId);
        } catch (Exception e) {
            sendError(player, "删除标签页失败: " + e.getMessage());
            GTInterestingThing.LOG.error("[NekoEdit] 删除标签页异常", e);
        }
    }

    /**
     * 按 ID 查找标签页条目（不存在返回 null）
     */
    private static NekoPageEntry findPageEntry(NekoPageConfig.NekoPageData data, int pageId) {
        if (data == null || data.getPages() == null) return null;
        for (NekoPageEntry p : data.getPages()) {
            if (p != null && p.getId() == pageId) return p;
        }
        return null;
    }

    /**
     * 将 page 编辑 JSON 应用到标签页条目（savePage/createPage 共用）
     * <p>
     * JSON 载荷：
     *
     * <pre>
     * {@code
     * {
     *   "name": "页名",                                                       // 空名忽略（保持原名）
     *   "icon": { "item": "modid:name", "meta": 0, "nbtBase64": "..." }      // 缺省 = 清空图标
     * }
     * }
     * </pre>
     *
     * @param entry 目标标签页条目
     * @param json  编辑 JSON
     */
    private static void applyPageEditJson(NekoPageEntry entry, JsonObject json) {
        // 名字（空名忽略，保持原名）
        if (json.has("name")) {
            String name = json.get("name")
                .getAsString()
                .trim();
            if (!name.isEmpty()) entry.setName(name);
        }
        // 图标（无 icon 键 = 清空图标，GUI 回退默认图标）
        if (json.has("icon")) {
            JsonObject iconJson = json.getAsJsonObject("icon");
            entry.setIconItem(
                iconJson.has("item") ? iconJson.get("item")
                    .getAsString() : "");
            entry.setIconMeta(
                iconJson.has("meta") ? iconJson.get("meta")
                    .getAsInt() : 0);
            entry.setIconNbt(
                iconJson.has("nbtBase64") ? iconJson.get("nbtBase64")
                    .getAsString() : "");
        } else {
            entry.setIconItem("");
            entry.setIconMeta(0);
            entry.setIconNbt("");
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
            GTInterestingThing.LOG.error("[NekoEdit] 保存签到编辑异常", e);
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
        GTInterestingThing.LOG.info(
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
            GTInterestingThing.LOG
                .info("[NekoEdit] 玩家 {} 删除签到{}阶梯: days={}", player.getCommandSenderName(), label, originalDays);
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
            GTInterestingThing.LOG
                .info("[NekoEdit] 玩家 {} 新增签到{}阶梯: days={}", player.getCommandSenderName(), label, days);
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
        GTInterestingThing.LOG
            .info("[NekoEdit] 玩家 {} 保存签到{}阶梯: {}→{} 天", player.getCommandSenderName(), label, originalDays, days);
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
            GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 清除逐日覆盖: day={}", player.getCommandSenderName(), day);
            return;
        }

        // ---- 设置覆盖 ----
        SignInReward reward = parseReward(json, "reward");
        if (reward == null) reward = SignInReward.EMPTY;
        DailySignInConfig.setDayOverride(day, reward);
        DailySignInConfig.saveConfig();
        SignInNetworkManager.sendSyncToAll();
        sendSuccess(player, "每月 " + day + " 日的覆盖奖励已保存");
        GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 保存逐日覆盖: day={}", player.getCommandSenderName(), day);
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

    // ==================== 每日在线奖励档位编辑（v1.7.7 G5②） ====================

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
                GTInterestingThing.LOG
                    .info("[NekoEdit] 玩家 {} 删除在线档位: {}s", player.getCommandSenderName(), originalSeconds);
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
                GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 新增在线档位: {}s", player.getCommandSenderName(), seconds);
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
            GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 保存在线档位: {}s", player.getCommandSenderName(), seconds);
        } catch (Exception e) {
            sendError(player, "保存在线档位失败: " + e.getMessage());
            GTInterestingThing.LOG.error("[NekoEdit] 保存在线档位异常", e);
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

    // ==================== 抽奖卡池编辑（v1.7.6 G2①） ====================

    /**
     * 保存抽奖卡池编辑（服务端）
     * <p>
     * targetId = 卡池 ID（不允许改 id——保底计数/中奖历史按池 id 关联，改 id 会导致记录悬空，
     * 仅作定位用）。JSON 载荷格式见 {@link #applyPoolEditJson}。
     * 保存后落盘 → 热重载 → 全服广播最新卡池配置。
     *
     * @param player      玩家
     * @param poolId      卡池 ID
     * @param jsonPayload JSON 序列化的卡池数据
     */
    public static void saveLotteryPool(EntityPlayerMP player, String poolId, String jsonPayload) {
        try {
            if (poolId == null || poolId.isEmpty()) {
                sendError(player, "卡池 ID 为空，无法保存");
                return;
            }
            LotteryConfig.LotteryConfigData data = LotteryConfig.load();
            if (data == null || data.pools == null) {
                sendError(player, "抽奖配置为空，无法保存");
                return;
            }
            LotteryPool targetPool = findLotteryPool(data, poolId);
            if (targetPool == null) {
                sendError(player, "未找到抽奖卡池: " + poolId);
                return;
            }

            JsonObject json = new JsonParser().parse(jsonPayload)
                .getAsJsonObject();
            applyPoolEditJson(targetPool, json);

            LotteryConfig.save(data);
            LotteryManager.INSTANCE.loadConfig();
            LotteryNetworkManager.sendSyncToAll();

            sendSuccess(player, "抽奖卡池已保存（" + poolId + "）");
            if (!targetPool.validate()) {
                sendInfo(player, "警告：卡池 " + poolId + " 当前无有效条目，已暂时从抽奖中隐藏");
            }
            GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 保存抽奖卡池: pool={}", player.getCommandSenderName(), poolId);
        } catch (Exception e) {
            sendError(player, "保存抽奖卡池失败: " + e.getMessage());
            GTInterestingThing.LOG.error("[NekoEdit] 保存抽奖卡池异常", e);
        }
    }

    /**
     * 新建抽奖卡池（服务端）
     * <p>
     * id 规则：非空、仅字母/数字/下划线/连字符、不与现有池重复（id 创建后不可改）。
     * 新池自动种子一条默认奖品条目（minecraft:apple ×1，权重 100，COMMON）——
     * 空池 {@link LotteryPool#validate()} 为 false 会被抽奖隐藏，且轮盘无槽位可点击，
     * 种子条目保证新池立即可经条目编辑器继续配置奖品。
     *
     * @param player      玩家
     * @param jsonPayload JSON 序列化的卡池数据（含新池 id）
     */
    public static void createLotteryPool(EntityPlayerMP player, String jsonPayload) {
        try {
            JsonObject json = new JsonParser().parse(jsonPayload)
                .getAsJsonObject();
            String id = json.has("id") ? json.get("id")
                .getAsString()
                .trim() : "";
            if (!id.matches("[a-zA-Z0-9_-]+")) {
                sendError(player, "卡池 ID 非法（仅允许字母/数字/下划线/连字符）: " + id);
                return;
            }
            LotteryConfig.LotteryConfigData data = LotteryConfig.load();
            if (data == null || data.pools == null) {
                sendError(player, "抽奖配置为空，无法新建卡池");
                return;
            }
            if (findLotteryPool(data, id) != null) {
                sendError(player, "卡池 ID 已存在: " + id);
                return;
            }

            LotteryPool pool = new LotteryPool(id, id, "", 0, PityConfig.createDefault());
            applyPoolEditJson(pool, json);

            // 种子默认奖品条目（理由见方法注释）
            LotteryEntry seed = new LotteryEntry();
            seed.setId("entry_1");
            seed.setItem("minecraft:apple");
            seed.setMeta(0);
            seed.setMinAmount(1);
            seed.setMaxAmount(1);
            seed.setWeight(100);
            seed.setRarity(LotteryRarity.COMMON);
            pool.getEntries()
                .add(seed);
            data.pools.add(pool);

            LotteryConfig.save(data);
            LotteryManager.INSTANCE.loadConfig();
            LotteryNetworkManager.sendSyncToAll();

            sendSuccess(player, "抽奖卡池已创建（" + id + "），含 1 条种子奖品条目，可点击轮盘槽位继续编辑");
            GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 新建抽奖卡池: pool={}", player.getCommandSenderName(), id);
        } catch (Exception e) {
            sendError(player, "新建抽奖卡池失败: " + e.getMessage());
            GTInterestingThing.LOG.error("[NekoEdit] 新建抽奖卡池异常", e);
        }
    }

    /**
     * 删除抽奖卡池（服务端）
     * <p>
     * 至少保留一个卡池（全删会导致抽奖页无任何可用池）。
     * 该池的团队保底计数/中奖历史按池 id 关联，删池后旧记录不再展示（历史数据不物理删除）。
     *
     * @param player 玩家
     * @param poolId 待删除卡池 ID
     */
    public static void deleteLotteryPool(EntityPlayerMP player, String poolId) {
        try {
            if (poolId == null || poolId.isEmpty()) {
                sendError(player, "卡池 ID 为空，无法删除");
                return;
            }
            LotteryConfig.LotteryConfigData data = LotteryConfig.load();
            if (data == null || data.pools == null) {
                sendError(player, "抽奖配置为空，无法删除");
                return;
            }
            if (data.pools.size() <= 1) {
                sendError(player, "至少保留一个卡池，无法删除");
                return;
            }
            LotteryPool targetPool = findLotteryPool(data, poolId);
            if (targetPool == null) {
                sendError(player, "未找到抽奖卡池: " + poolId);
                return;
            }
            data.pools.remove(targetPool);

            LotteryConfig.save(data);
            LotteryManager.INSTANCE.loadConfig();
            LotteryNetworkManager.sendSyncToAll();

            sendSuccess(player, "抽奖卡池已删除（" + poolId + "）");
            GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 删除抽奖卡池: pool={}", player.getCommandSenderName(), poolId);
        } catch (Exception e) {
            sendError(player, "删除抽奖卡池失败: " + e.getMessage());
            GTInterestingThing.LOG.error("[NekoEdit] 删除抽奖卡池异常", e);
        }
    }

    /**
     * 按 ID 查找卡池（不存在返回 null）
     */
    private static LotteryPool findLotteryPool(LotteryConfig.LotteryConfigData data, String poolId) {
        if (data == null || data.pools == null || poolId == null) return null;
        for (LotteryPool pool : data.pools) {
            if (pool != null && poolId.equals(pool.getId())) return pool;
        }
        return null;
    }

    /**
     * 将池编辑 JSON 应用到卡池（save/create 共用）
     * <p>
     * JSON 载荷：
     * 
     * <pre>
     * {@code
     * {
     *   "name": "池名",
     *   "icon": { "item": "modid:name", "meta": 0, "nbtBase64": "..." },   // 缺省 = 清空图标
     *   "costItems": [ { "item": "modid:name", "meta": 0, "amount": 5, "nbtBase64": "..." } ],
     *   "pityEnabled": true,
     *   "softPityThreshold": 30,
     *   "hardPityThreshold": 50,
     *   "guaranteedRarity": "EPIC"
     * }
     * }
     * </pre>
     * 
     * 写回后按 costItems 中首个猫猫币条目同步重写旧字段（nekoCurrencyId/costPerDraw），
     * 保持旧口径展示（PoolSummary.currencyId/costPerDraw 回退路径）与新配置一致。
     *
     * @param pool 目标卡池
     * @param json 编辑 JSON
     */
    private static void applyPoolEditJson(LotteryPool pool, JsonObject json) {
        // 名字（空名忽略，保持原名）
        if (json.has("name")) {
            String name = json.get("name")
                .getAsString()
                .trim();
            if (!name.isEmpty()) pool.setName(name);
        }
        // 图标（无 icon 键 = 清空图标，GUI 回退货币图标）
        if (json.has("icon")) {
            JsonObject iconJson = json.getAsJsonObject("icon");
            pool.setIconItem(
                iconJson.has("item") ? iconJson.get("item")
                    .getAsString() : "");
            pool.setIconMeta(
                iconJson.has("meta") ? iconJson.get("meta")
                    .getAsInt() : 0);
            pool.setIconNbt(
                iconJson.has("nbtBase64") ? iconJson.get("nbtBase64")
                    .getAsString() : "");
        } else {
            pool.setIconItem("");
            pool.setIconMeta(0);
            pool.setIconNbt("");
        }
        // 消耗需求物品（costItems 为权威口径；解析失败的条目跳过）
        if (json.has("costItems")) {
            pool.setCostItems(parsePoolCostItems(json.getAsJsonArray("costItems")));
        }
        // 保底字段
        PityConfig pity = pool.getPityConfig();
        if (json.has("pityEnabled")) pity.setEnabled(
            json.get("pityEnabled")
                .getAsBoolean());
        if (json.has("softPityThreshold")) pity.setSoftPityThreshold(
            json.get("softPityThreshold")
                .getAsInt());
        if (json.has("hardPityThreshold")) pity.setHardPityThreshold(
            json.get("hardPityThreshold")
                .getAsInt());
        if (json.has("guaranteedRarity")) pity.setGuaranteedRarity(
            json.get("guaranteedRarity")
                .getAsString());
        // 旧字段同步重写（保持兼容展示口径一致）
        syncLegacyCurrencyFields(pool);
    }

    /**
     * 解析消耗需求物品列表（item/meta/amount/nbtBase64? 格式，与 LotteryConfig Gson 适配器一致）
     *
     * @param array JSON 数组
     * @return 需求物品列表（解析失败的条目已跳过）
     */
    private static List<NekoBigItemStack> parsePoolCostItems(JsonArray array) {
        List<NekoBigItemStack> costs = new ArrayList<>();
        if (array == null) return costs;
        for (int i = 0; i < array.size(); i++) {
            JsonObject itemJson = array.get(i)
                .getAsJsonObject();
            String itemId = itemJson.has("item") ? itemJson.get("item")
                .getAsString()
                .trim() : "";
            if (itemId.isEmpty()) continue;
            String[] parts = itemId.split(":", 2);
            if (parts.length < 2) continue;
            Item item = GameRegistry.findItem(parts[0], parts[1]);
            if (item == null) continue;
            int meta = itemJson.has("meta") ? itemJson.get("meta")
                .getAsInt() : 0;
            int amount = Math.max(
                1,
                itemJson.has("amount") ? itemJson.get("amount")
                    .getAsInt() : 1);
            ItemStack stack = new ItemStack(item, 1, meta);
            if (itemJson.has("nbtBase64")) {
                NBTTagCompound nbt = NbtBase64Util.nbtFromBase64(
                    itemJson.get("nbtBase64")
                        .getAsString());
                if (nbt != null) stack.setTagCompound(nbt);
            }
            costs.add(new NekoBigItemStack(amount, "", stack));
        }
        return costs;
    }

    /**
     * 按 costItems 中首个猫猫币条目重写旧字段（nekoCurrencyId/costPerDraw；无货币条目则清零）
     */
    private static void syncLegacyCurrencyFields(LotteryPool pool) {
        String currencyId = "";
        int costPerDraw = 0;
        for (NekoBigItemStack cost : pool.getCostItems()) {
            if (cost == null || cost.getBaseStack() == null) continue;
            String cid = NekoCurrencyRegistrar.getNekoCurrencyId(cost.getBaseStack());
            if (cid != null) {
                currencyId = cid;
                costPerDraw = cost.getStackSize();
                break;
            }
        }
        pool.setNekoCurrencyId(currencyId);
        pool.setCostPerDraw(costPerDraw);
    }

    // ==================== 祝福预设编辑（v1.7.6 G5） ====================

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
                GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 保存祝福发件人: {}", player.getCommandSenderName(), sender);
                return;
            }

            // ---- 生日模板 ----
            if ("birthday".equals(targetId)) {
                BlessingConfig.BirthdayBlessing birthday = BlessingConfig.getBirthday();
                applyBirthdayEditJson(birthday, json);
                BlessingConfig.saveConfig();
                sendSuccess(player, "生日祝福模板已保存");
                GTInterestingThing.LOG.info("[NekoEdit] 玩家 {} 保存生日祝福模板", player.getCommandSenderName());
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
                GTInterestingThing.LOG.info(
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
            GTInterestingThing.LOG.error("[NekoEdit] 保存祝福预设异常", e);
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
