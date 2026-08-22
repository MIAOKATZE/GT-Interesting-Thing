package com.miaokatze.gtit.trade.v2;

import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendError;
import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendSuccess;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.NekoTradeConfig;
import com.miaokatze.gtit.trade.NekoTradeEntry;

/**
 * 编辑模式服务端操作处理器——标签页域（O2-05 E2：自 {@link NekoEditActionHandler} page 段逐字搬移，
 * v1.7.6 G3④）
 * <p>
 * 标签页的新建/保存/删除：加载/保存 {@link NekoPageConfig}。保存链为
 * 「{@link NekoPageConfig} 落盘 → {@link NekoPageRegistry#reload()} 热重载 →
 * {@link NekoTradeNetworkManager#sendSyncToAll()} 全服广播」（删除页时交易条目整体
 * 迁往"其他"页并额外重载 {@link NekoTradeRegistryV2}），与其他域的后置动作不统一，本域自带。
 * <p>
 * <b>线程安全</b>：所有方法均在服务器主线程执行（由 {@code scheduleServerTask} 保证）。
 */
final class PageEditActions {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private PageEditActions() {
        // 静态工具类，禁止实例化
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
            LOG.info(
                "[NekoEdit] 玩家 {} 新建标签页: id={}, name={}",
                player.getCommandSenderName(),
                entry.getId(),
                entry.getName());
        } catch (Exception e) {
            sendError(player, "新建标签页失败: " + e.getMessage());
            LOG.error("[NekoEdit] 新建标签页异常", e);
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
            LOG.info("[NekoEdit] 玩家 {} 保存标签页: id={}, name={}", player.getCommandSenderName(), pageId, target.getName());
        } catch (Exception e) {
            sendError(player, "保存标签页失败: " + e.getMessage());
            LOG.error("[NekoEdit] 保存标签页异常", e);
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
            LOG.info("[NekoEdit] 玩家 {} 删除标签页: id={}", player.getCommandSenderName(), pageId);
        } catch (Exception e) {
            sendError(player, "删除标签页失败: " + e.getMessage());
            LOG.error("[NekoEdit] 删除标签页异常", e);
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
}
