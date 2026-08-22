package com.miaokatze.gtit.trade.v2;

import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendError;
import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendInfo;
import static com.miaokatze.gtit.trade.v2.EditActionsCommon.sendSuccess;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.lottery.LotteryConfig;
import com.miaokatze.gtit.lottery.LotteryEntry;
import com.miaokatze.gtit.lottery.LotteryManager;
import com.miaokatze.gtit.lottery.LotteryNetworkManager;
import com.miaokatze.gtit.lottery.LotteryPool;
import com.miaokatze.gtit.lottery.LotteryRarity;
import com.miaokatze.gtit.lottery.PityConfig;
import com.miaokatze.gtit.util.NbtBase64Util;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 编辑模式服务端操作处理器——抽奖域（O2-05 E4：自 {@link NekoEditActionHandler} 抽奖段逐字搬移）
 * <p>
 * 抽奖条目编辑（v1.7.6 G2① 前身）与卡池增删改/新建（v1.7.6 G2①）：加载/保存抽奖配置。
 * 保存链为「{@link LotteryConfig#save} 落盘 → {@link LotteryManager#loadConfig()} 热重载 →
 * {@link LotteryNetworkManager#sendSyncToAll()} 全服广播最新卡池配置（v1.7.0 目标 5：
 * 卡池全服一致，逐玩家推送各自团队维度的保底/历史/余额，编辑者也在广播范围内）」，
 * 与其他域的后置动作不统一，本域自带。
 * <p>
 * <b>线程安全</b>：所有方法均在服务器主线程执行（由 {@code scheduleServerTask} 保证）。
 */
final class LotteryEditActions {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private LotteryEditActions() {
        // 静态工具类，禁止实例化
    }

    /**
     * 打开抽奖编辑面板（服务端）
     *
     * @param player   玩家
     * @param entryKey 抽奖条目标识
     */
    public static void openLotteryEditor(EntityPlayerMP player, String entryKey) {
        LOG.info("[NekoEdit] 玩家 {} 打开抽奖编辑器: entry={}", player.getCommandSenderName(), entryKey);
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
            LOG.info(
                "[NekoEdit] 玩家 {} 保存抽奖条目: pool={}, entry={}, currency={}, weight={}",
                player.getCommandSenderName(),
                poolId,
                entryId,
                currencyId.isEmpty() ? "(物品)" : currencyId,
                targetEntry.getWeight());

        } catch (Exception e) {
            sendError(player, "保存抽奖编辑失败: " + e.getMessage());
            LOG.error("[NekoEdit] 保存抽奖编辑异常", e);
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
            LOG.info("[NekoEdit] 玩家 {} 保存抽奖卡池: pool={}", player.getCommandSenderName(), poolId);
        } catch (Exception e) {
            sendError(player, "保存抽奖卡池失败: " + e.getMessage());
            LOG.error("[NekoEdit] 保存抽奖卡池异常", e);
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
            LOG.info("[NekoEdit] 玩家 {} 新建抽奖卡池: pool={}", player.getCommandSenderName(), id);
        } catch (Exception e) {
            sendError(player, "新建抽奖卡池失败: " + e.getMessage());
            LOG.error("[NekoEdit] 新建抽奖卡池异常", e);
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
            LOG.info("[NekoEdit] 玩家 {} 删除抽奖卡池: pool={}", player.getCommandSenderName(), poolId);
        } catch (Exception e) {
            sendError(player, "删除抽奖卡池失败: " + e.getMessage());
            LOG.error("[NekoEdit] 删除抽奖卡池异常", e);
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
}
