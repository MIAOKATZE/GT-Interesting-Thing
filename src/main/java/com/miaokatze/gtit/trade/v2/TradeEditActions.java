package com.miaokatze.gtit.trade.v2;

import static com.miaokatze.gtit.trade.v2.EditActionsCommon.parseItemEntries;
import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendError;
import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendSuccess;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/**
 * 编辑模式服务端操作处理器——交易域（O2-05 E2：自 {@link NekoEditActionHandler} 交易段逐字搬移）
 * <p>
 * 交易条目编辑的打开与保存、交易条目新建：加载/保存 {@link NekoTradeConfig} 中的交易配置。
 * 保存链为「配置落盘 → {@link NekoTradeRegistryV2} 热重载 → {@link NekoTradeNetworkManager#sendSyncToAll()}
 * 全服广播」，与其他域的后置动作不统一，本域自带（禁抽象成统一后置钩子）。
 * <p>
 * <b>线程安全</b>：所有方法均在服务器主线程执行（由 {@code scheduleServerTask} 保证）。
 */
final class TradeEditActions {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private TradeEditActions() {
        // 静态工具类，禁止实例化
    }

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
        LOG.info("[NekoEdit] 玩家 {} 打开交易编辑器: group={}, index={}", player.getCommandSenderName(), groupIdStr, tradeIndex);
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
            LOG.info(
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
                LOG.warn("[NekoEdit] 未找到目标交易条目: group={}", groupIdStr);
                return;
            }
            int oldFromSize = targetEntry.getFromItems() == null ? 0
                : targetEntry.getFromItems()
                    .size();
            int oldToSize = targetEntry.getToItems() == null ? 0
                : targetEntry.getToItems()
                    .size();
            LOG.info(
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

            LOG.info(
                "[NekoEdit] 服务端保存交易: group={}, fromItems={}, toItems={}",
                groupIdStr,
                fromItems.size(),
                toItems.size());

            // 防御性校验：客户端发来空 fromItems + toItems 但旧数据有物品时，拒绝保存
            // 原因：客户端 editItemHandler 同步可能尚未完成，此时保存会覆盖清空原有数据
            // （v1.7.33 修复交易条目保存丢失：服务端不信任客户端的空数据覆盖）
            if (fromItems.isEmpty() && toItems.isEmpty() && (oldFromSize > 0 || oldToSize > 0)) {
                LOG.warn(
                    "[NekoEdit] 拒绝保存：客户端 fromItems/toItems 均为空，但旧数据有 from={} to={}，疑似同步未完成。group={}",
                    oldFromSize,
                    oldToSize,
                    groupIdStr);
                sendError(player, "保存失败：物品数据为空（可能是同步未完成），请等待物品显示后再试");
                return;
            }

            // 防御性校验：toItems 为空但旧数据有 toItems 时，拒绝保存
            // 原因：toItems 为空的交易会被跳过注册，导致交易从用户视角"消失"
            if (toItems.isEmpty() && oldToSize > 0) {
                LOG.warn("[NekoEdit] 拒绝保存：客户端 toItems 为空，但旧数据有 {} 条产出，疑似同步未完成。group={}", oldToSize, groupIdStr);
                sendError(player, "保存失败：产物数据为空（可能是同步未完成），请等待物品显示后再试");
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
            LOG.info("[NekoEdit] 更新目标条目字段完成: group={}, newFrom={}, newTo={}", groupIdStr, newFromSize, newToSize);

            // 保存配置到文件
            NekoTradeConfig.save(data);
            LOG.info("[NekoEdit] 配置已落盘: group={}", groupIdStr);

            // 热重载交易注册表
            NekoTradeRegistryV2.initialize();

            // 检查该 groupId 是否重新注册成功
            NekoTradeGroup reloaded = NekoTradeDatabase.INSTANCE.getTradeGroup(UUID.fromString(groupIdStr));
            LOG.info("[NekoEdit] 服务端重载交易组完成: group={}, found={}", groupIdStr, reloaded != null);

            // v1.7.0 目标 5：向全体在线玩家广播服务端最新交易/标签页配置（S→C 只读同步，客户端不写盘）
            NekoTradeNetworkManager.sendSyncToAll();

            sendSuccess(player, "交易条目已保存 (ID: " + groupIdStr + ")");
            LOG.info(
                "[NekoEdit] 玩家 {} 保存交易编辑: group={}, index={}",
                player.getCommandSenderName(),
                groupIdStr,
                tradeIndex);

        } catch (Exception e) {
            sendError(player, "保存交易编辑失败: " + e.getMessage());
            LOG.error("[NekoEdit] 保存交易编辑异常", e);
        }
    }

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
            LOG.info("[NekoEdit] 玩家 {} 新建交易条目: tabId={}, id={}", player.getCommandSenderName(), tabId, entry.getId());
        } catch (Exception e) {
            sendError(player, "新建交易条目失败: " + e.getMessage());
            LOG.error("[NekoEdit] 新建交易条目异常", e);
        }
    }
}
