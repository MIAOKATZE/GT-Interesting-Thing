package com.miaokatze.gtit.trade;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 猫猫售货机交易配置管理
 * 负责读写 config/gtit/nekovm_trades.json
 */
public class NekoTradeConfig {

    private static final String CONFIG_SUB_PATH = "config/gtit/nekovm_trades.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create();

    // --- 内部数据类 ---

    /**
     * 交易配置数据
     */
    public static class NekoTradeData {

        private int version = 1;
        private List<NekoTradeEntry> trades = new ArrayList<>();

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public List<NekoTradeEntry> getTrades() {
            return trades;
        }

        public void setTrades(List<NekoTradeEntry> trades) {
            this.trades = trades;
        }
    }

    // --- 核心方法 ---

    /**
     * 初始化配置，如果配置文件不存在则生成默认配置
     */
    public static synchronized void init() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                save(getDefaultTrades());
                GTInterestingThing.LOG.info("猫猫售货机交易配置已生成默认文件: {}", path);
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫售货机交易配置初始化失败", e);
        }
    }

    /**
     * 从文件加载交易数据，文件不存在时返回默认数据
     */
    public static synchronized NekoTradeData load() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                GTInterestingThing.LOG.info("猫猫售货机交易配置文件不存在，返回默认数据");
                return getDefaultTrades();
            }
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            NekoTradeData data = GSON.fromJson(json, NekoTradeData.class);
            if (data == null) {
                GTInterestingThing.LOG.warn("猫猫售货机交易配置文件为空，返回默认数据");
                return getDefaultTrades();
            }
            GTInterestingThing.LOG.info("猫猫售货机交易配置已加载");
            return data;
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫售货机交易配置加载失败，返回默认数据", e);
            return getDefaultTrades();
        }
    }

    /**
     * 保存交易数据到文件
     */
    public static synchronized void save(NekoTradeData data) {
        try {
            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            String json = GSON.toJson(data);
            Files.write(path, json.getBytes(StandardCharsets.UTF_8));
            GTInterestingThing.LOG.info("猫猫售货机交易配置已保存");
        } catch (Exception e) {
            GTInterestingThing.LOG.error("猫猫售货机交易配置保存失败", e);
        }
    }

    /**
     * 生成默认交易数据
     */
    public static NekoTradeData getDefaultTrades() {
        NekoTradeData data = new NekoTradeData();
        List<NekoTradeEntry> trades = new ArrayList<>();

        // 标签页3 (GTIT) 默认交易
        trades.add(
            createDefaultTrade(
                "110fef48-6d6e-43d0-b0da-d4616062c086",
                3,
                1,
                "shimmeringNeko",
                8,
                null,
                "gtit:ring_distant_grasp",
                0,
                1,
                null,
                0,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "60189809-61fe-4c58-b7e9-6a75822a1d95",
                3,
                2,
                "shimmeringNeko",
                8,
                null,
                "gtit:ring_skywalk",
                0,
                1,
                null,
                0,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "626f57f4-f7a8-4fc0-8342-25a22a416600",
                3,
                3,
                "shimmeringNeko",
                12,
                "harvestcraft:delightedmealItem:0:16",
                "gtit:ring_gluttony",
                0,
                1,
                null,
                0,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "9b41d48a-04e1-42ff-8740-2d033058bff8",
                3,
                4,
                "shimmeringNeko",
                8,
                null,
                "gtit:ring_ironheart",
                0,
                1,
                null,
                0,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "c9cca16c-a897-4151-b014-7e10c27b6107",
                3,
                5,
                "shimmeringNeko",
                12,
                null,
                "gtit:ring_dragon_breath",
                0,
                1,
                null,
                0,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "62c2e2dd-0d43-4470-813e-05ea97c0361f",
                3,
                6,
                "shimmeringNeko",
                12,
                null,
                "gtit:ring_mountainbreaker",
                0,
                1,
                null,
                0,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "c49c7365-a3fd-462a-938a-e1adc8cccdab",
                3,
                7,
                "shimmeringNeko",
                12,
                null,
                "gtit:ring_tempest",
                0,
                1,
                null,
                0,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "549030c9-b3f4-4c9d-9a99-eed53ab851ce",
                3,
                8,
                "shimmeringNeko",
                256,
                "appliedenergistics2:item.ItemAdvancedStorageCell.16384k:0:4",
                "gtit:gtit.infinity_cell",
                0,
                1,
                "CgAAAA==",
                0,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "7c87fe3f-7ed6-467b-8646-5da3e5937ebe",
                3,
                9,
                "shimmeringNeko",
                256,
                "ae2fc:multi_fluid_storage16384:0:4",
                "gtit:gtit.infinity_fluid_cell",
                0,
                1,
                "CgAAAA==",
                0,
                -1,
                ""));

        // 标签页1 (猫猫币) 默认交易 — 物品兑换猫猫币
        trades.add(
            createDefaultTrade(
                "0deb35e7-4451-42be-a5f4-2e9654a7cd97",
                1,
                1,
                null,
                0,
                "minecraft:cobblestone:0:512",
                "gtit:neko_coin",
                0,
                1,
                null,
                21600,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "a2b70611-2f52-4e94-bd0b-37cb3bcef55b",
                1,
                2,
                null,
                0,
                "minecraft:iron_ingot:0:128",
                "gtit:neko_coin",
                0,
                1,
                null,
                21600,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "bf6eff9e-5251-4c7f-add6-435bcf36ba3e",
                1,
                3,
                null,
                0,
                "gregtech:gt.metaitem.01:11035:128",
                "gtit:neko_coin",
                0,
                1,
                null,
                21600,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "d394dcd4-df58-47fc-ba23-f9a54f20ba8f",
                1,
                4,
                null,
                0,
                "gregtech:gt.metaitem.01:11057:128",
                "gtit:neko_coin",
                0,
                1,
                null,
                21600,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "1584789d-6e9a-4551-9ef6-6317976d2c09",
                1,
                5,
                null,
                0,
                "minecraft:wheat:0:128",
                "gtit:neko_coin",
                0,
                1,
                null,
                21600,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "ec3da15d-f7eb-4032-8357-bf57bb8a2ded",
                1,
                6,
                null,
                0,
                "Natura:barleyFood:3:128",
                "gtit:neko_coin",
                0,
                1,
                null,
                21600,
                -1,
                ""));

        // 标签页1 (猫猫币) — 多需求物品交易
        trades.add(
            createDefaultTrade(
                "aa9ba48b-fad0-4b04-9fa8-92ccf4780b0a",
                1,
                7,
                null,
                0,
                "minecraft:bone:0:64;minecraft:gunpowder:0:64;minecraft:rotten_flesh:0:64",
                "gtit:neko_coin",
                0,
                3,
                null,
                14400,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "88cd599d-9a36-49b4-878a-24f38644074b",
                1,
                8,
                null,
                0,
                "minecraft:blaze_rod:0:256",
                "gtit:neko_coin",
                0,
                8,
                null,
                14400,
                -1,
                ""));

        // 标签页2 (闪烁猫猫币) 默认交易
        trades.add(
            createDefaultTrade(
                "63545a4e-28aa-4928-bf52-63ae039bf312",
                2,
                1,
                "neko",
                64,
                null,
                "gtit:shimmering_neko_coin",
                0,
                1,
                null,
                0,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "7c0fa8ce-9288-4408-a5f0-7e05b1c485dd",
                2,
                2,
                null,
                0,
                "DraconicEvolution:dezilsMarshmallow:0:1",
                "gtit:shimmering_neko_coin",
                0,
                1,
                null,
                3600,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "f123df0a-5d35-4a1e-9ba5-21aff513e39d",
                2,
                3,
                null,
                0,
                "EMT:TaintedMjolnir:0:1",
                "gtit:shimmering_neko_coin",
                0,
                1,
                null,
                3600,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "b6f3f205-bf18-47be-9c44-e4c4d0a95127",
                2,
                4,
                null,
                0,
                "minecraft:nether_star:0:1",
                "gtit:shimmering_neko_coin",
                0,
                1,
                null,
                3600,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "8ad43f0d-4ffa-4682-940a-cd7922613e53",
                2,
                5,
                null,
                0,
                "Thaumcraft:ItemLootBag:2:8;Thaumcraft:ItemLootBag:1:2;Thaumcraft:ItemLootBag:0:16",
                "gtit:shimmering_neko_coin",
                0,
                1,
                null,
                3600,
                -1,
                ""));

        // 标签页4 (周期领取) 默认交易
        trades.add(
            createDefaultTrade(
                "27d89836-e791-44cd-b8d5-4db8dcc83df0",
                4,
                1,
                null,
                0,
                null,
                "gtit:neko_coin",
                0,
                4,
                null,
                43200,
                -1,
                ""));
        trades.add(
            createDefaultTrade(
                "ae6f275f-94f0-44e1-bd94-7f9f6edc9b0b",
                4,
                2,
                null,
                0,
                null,
                "gtit:shimmering_neko_coin",
                0,
                1,
                null,
                86400,
                -1,
                ""));

        data.setTrades(trades);
        return data;
    }

    /**
     * 创建默认交易条目
     *
     * @param id           条目ID
     * @param tabId        标签页ID
     * @param orderId      顺序ID
     * @param currencyType 货币类型（null=无货币）
     * @param currencyAmt  货币数量
     * @param fromItemStr  需求物品（格式 "modid:item:meta:amount"，null=无需求物品）
     * @param toItem       产物物品注册名
     * @param toMeta       产物metadata
     * @param toAmount     产物数量
     * @param toNbtBase64  产物NBT Base64（null=无NBT）
     * @param cooldown     冷却时间
     * @param maxTrades    最大交易次数
     * @param bqQuestId    绑定任务ID
     */
    private static NekoTradeEntry createDefaultTrade(String id, int tabId, int orderId, String currencyType,
        int currencyAmt, String fromItemStr, String toItem, int toMeta, int toAmount, String toNbtBase64, int cooldown,
        int maxTrades, String bqQuestId) {
        NekoTradeEntry entry = new NekoTradeEntry();
        entry.setId(id);
        entry.setTabId(tabId);
        entry.setOrderId(orderId);
        if (currencyType != null) {
            entry.setCurrency(new NekoTradeEntry.NekoCurrencyCost(currencyType, currencyAmt));
        }
        if (fromItemStr != null) {
            List<NekoTradeEntry.ItemEntry> fromItems = new ArrayList<>();
            // 支持多 fromItems，用分号分隔：item1:meta1:amount1;item2:meta2:amount2
            String[] itemStrs = fromItemStr.split(";");
            for (String itemStr : itemStrs) {
                String[] parts = itemStr.split(":");
                String fromItemName = parts[0] + ":" + parts[1];
                int fromMeta = Integer.parseInt(parts[2]);
                int fromAmount = Integer.parseInt(parts[3]);
                fromItems.add(new NekoTradeEntry.ItemEntry(fromItemName, fromMeta, fromAmount));
            }
            entry.setFromItems(fromItems);
        }
        List<NekoTradeEntry.ItemEntry> toItems = new ArrayList<>();
        NekoTradeEntry.ItemEntry toEntry = new NekoTradeEntry.ItemEntry(toItem, toMeta, toAmount);
        if (toNbtBase64 != null) {
            toEntry.setNbtBase64(toNbtBase64);
        }
        toItems.add(toEntry);
        entry.setToItems(toItems);
        entry.setCooldown(cooldown);
        entry.setMaxTrades(maxTrades);
        entry.setBqQuestId(bqQuestId);
        return entry;
    }

    // --- 辅助方法 ---

    /**
     * 获取配置文件路径
     * <p>
     * Minecraft 服务器的工作目录即为服务器根目录，
     * 因此使用相对路径 "config/gtit/nekovm_trades.json" 即可正确定位。
     */
    private static Path getConfigPath() {
        return Paths.get(CONFIG_SUB_PATH);
    }
}
