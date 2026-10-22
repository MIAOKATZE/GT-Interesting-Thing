package com.miaokatze.gtit.trade.preset;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.config.DefaultContentConfig;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoTradeEntry;
import com.miaokatze.gtit.trade.TradeConfigPaths;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoBqBridge;
import com.miaokatze.gtit.trade.v2.NekoBqQuestIdParser;
import com.miaokatze.gtit.trade.v2.NekoTradeNetworkManager;
import com.miaokatze.gtit.trade.v2.NekoTradeRegistryV2;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;

/** Server authority for portable trade presets. Public DTOs are also the UI wire format. */
public final class TradePresetManager {

    private static final Logger LOG = LogManager.getLogger("gtit");
    private static TradePresetStore store;
    private static MinecraftServer activeServer;

    private TradePresetManager() {}

    /** Run before any startup trade/page writers so a pending rollback precedes default updates. */
    public static synchronized void recoverPendingChanges() {
        try {
            new PresetFileTransaction(TradeConfigPaths.gameRoot()).recover();
            new TradePresetStore(TradeConfigPaths.gameRoot());
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("贸易预设事务恢复失败；请先保留并检查 preset_transaction.json", failure);
        }
    }

    /** Called after trade/page initialization on serverStarted; files belong to the server instance. */
    public static synchronized void initialize(MinecraftServer server) {
        try {
            if (server == null) throw new IllegalStateException("服务器尚未启动");
            if (store != null && activeServer == server) return;
            Path root = TradeConfigPaths.gameRoot();
            store = new TradePresetStore(root);
            for (String notice : store.builtinNotices()) LOG.info("[TradePreset] {}", notice);
            activeServer = server;
            // Recovery may have rolled files back after the initial server registry load.
            refreshTrades();
        } catch (IOException | RuntimeException failure) {
            store = null;
            activeServer = null;
            LOG.error("[TradePreset] 初始化失败；已有贸易配置保持原样", failure);
        }
    }

    public static synchronized void shutdown() {
        store = null;
        activeServer = null;
    }

    public static boolean canConfigure(EntityPlayerMP player) {
        return canManageAtLevel(player, DefaultContentConfig.managementOpLevel());
    }

    /** Restoring bundled defaults retains its separate destructive-operation permission. */
    public static boolean canResetDefaults(EntityPlayerMP player) {
        return canManageAtLevel(player, 4);
    }

    private static boolean canManageAtLevel(EntityPlayerMP player, int level) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return false;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return false;
        if (server.isSinglePlayer()) return player.getCommandSenderName()
            .equals(server.getServerOwner());
        return player.canCommandSenderUseCommand(level, "gtit");
    }

    public static synchronized Snapshot snapshot(EntityPlayerMP player) {
        Snapshot result = new Snapshot();
        result.canManage = canConfigure(player);
        try {
            requireServer();
            result.revision = store.revision();
            result.appliedIds.addAll(store.selectedIds());
            result.appliedTradeCount = store.installedTradeCount();
            for (TradePresetDefinition definition : store.definitions()) {
                PresetSummary summary = new PresetSummary();
                summary.id = definition.id;
                summary.name = definition.name;
                summary.description = definition.description;
                summary.icon = TradePresetStore.GSON
                    .fromJson(TradePresetStore.GSON.toJson(definition.icon), Icon.class);
                summary.icon = displayIcon(definition, summary.icon);
                summary.tabCount = definition.tabs.size();
                summary.tradeCount = definition.tabs.stream()
                    .mapToInt(tab -> tab.trades.size())
                    .sum();
                summary.mutuallyExclusiveIds = new ArrayList<>(definition.mutuallyExclusiveIds);
                summary.selected = store.selected(definition.id);
                summary.builtin = TradePresetStore.BUILTIN_IDS.contains(definition.id);
                summary.sourceKind = summary.builtin ? "builtin" : "custom";
                summary.overridden = store.overridden(definition.id);
                summary.defaultUpdateAvailable = store.defaultUpdateAvailable(definition.id);
                summary.empty = definition.tabs.stream()
                    .allMatch(tab -> tab.trades.isEmpty());
                summary.tabIds = store.installedTabs(definition.id);
                result.presets.add(summary);
            }
            for (NekoPageEntry page : store.pages()) {
                TabSummary tab = new TabSummary();
                tab.id = page.getId();
                tab.name = page.getName();
                tab.sourcePresetId = page.getSourcePresetId();
                tab.sourceKind = page.isPresetBuiltin() ? "builtin" : "custom";
                result.tabs.add(tab);
            }
            result.errors.addAll(store.errors());
        } catch (IOException | RuntimeException failure) {
            result.errors.add(failure.getMessage());
        }
        return result;
    }

    public static synchronized Result select(EntityPlayerMP player, long expectedRevision, List<String> ids) {
        try {
            requirePermission(player);
            store.select(expectedRevision, ids, TradePresetManager::validateAvailable);
            refreshTrades();
            Result result = result(player, true, "贸易预设选择已应用");
            result.message += "：" + result.snapshot.appliedIds.size()
                + " 个贸易组，"
                + result.snapshot.appliedTradeCount
                + " 条贸易。"
                + (result.snapshot.appliedTradeCount == 0 ? "当前未应用有内容的贸易组；空模板不会生成贸易。" : "");
            return result;
        } catch (IOException | RuntimeException failure) {
            return result(player, false, failure.getMessage());
        }
    }

    public static synchronized Result save(EntityPlayerMP player, long expectedRevision, String id, String name,
        String description, Icon icon, List<String> mutuallyExclusiveIds, List<Integer> tabIds,
        boolean replaceExisting) {
        return save(
            player,
            expectedRevision,
            id,
            name,
            description,
            icon,
            mutuallyExclusiveIds,
            tabIds,
            replaceExisting,
            null,
            true);
    }

    public static synchronized Result save(EntityPlayerMP player, long expectedRevision, String id, String name,
        String description, Icon icon, List<String> mutuallyExclusiveIds, List<Integer> tabIds, boolean replaceExisting,
        String sourceId, boolean replaceTabs) {
        try {
            requirePermission(player);
            TradePresetDefinition definition = new TradePresetDefinition();
            definition.id = id;
            definition.name = name;
            definition.description = description == null ? "" : description;
            definition.icon = icon;
            definition.mutuallyExclusiveIds = mutuallyExclusiveIds == null ? new ArrayList<>()
                : new ArrayList<>(mutuallyExclusiveIds);
            store.save(
                expectedRevision,
                definition,
                tabIds,
                replaceExisting,
                sourceId,
                replaceTabs,
                TradePresetManager::validateAvailable);
            refreshTrades();
            return result(
                player,
                true,
                "预设已保存至服务器 MiaoTrade/" + id + ".json。" + (store.selected(id) ? "已更新正在使用的模板。" : "尚未自动启用，请勾选并应用。"));
        } catch (IOException | RuntimeException failure) {
            return result(player, false, failure.getMessage());
        }
    }

    public static Result export(EntityPlayerMP player, long expectedRevision, String id, String name,
        List<Integer> tabs) {
        return save(player, expectedRevision, id, name, "", new Icon(), new ArrayList<>(), tabs, false);
    }

    /** Reload only the catalog: editing files never silently overwrites installed or user-edited tabs. */
    public static synchronized Result reload(EntityPlayerMP player) {
        try {
            requirePermission(player);
            store.reload();
            Result result = result(player, true, "服务器预设目录已重新读取");
            result.message += "：" + result.snapshot.presets.size() + " 个贸易组。已应用贸易保持不变；修改的内容需点击应用选择。";
            if (!result.snapshot.errors.isEmpty())
                result.message += " 跳过/警告 " + result.snapshot.errors.size() + " 项，请查看提示。";
            return result;
        } catch (IOException | RuntimeException failure) {
            return result(player, false, failure.getMessage());
        }
    }

    public static synchronized Result resetDefaults(EntityPlayerMP player) {
        try {
            if (!canResetDefaults(player)) throw new IllegalArgumentException("仅单人存档所有者或专用服务器 OP 等级 4 可恢复默认贸易");
            requireServer();
            store.resetDefaults();
            refreshTrades();
            return result(player, true, "默认贸易已从 jar 恢复，自定义贸易与预设选择已保留");
        } catch (IOException | RuntimeException failure) {
            return result(player, false, failure.getMessage());
        }
    }

    /** Resolve stock and previous stock icons through the real AE definitions; custom icons remain untouched. */
    private static Icon displayIcon(TradePresetDefinition definition, Icon icon) {
        if (icon == null || icon.meta != 0 || !icon.nbtBase64.isEmpty()) return icon;
        if ("gtit-ae-basic".equals(definition.id) && ("minecraft:redstone".equals(icon.item)
            || "appliedenergistics2:item.ItemMultiMaterial".equals(icon.item))) {
            ItemStack stack = appeng.api.AEApi.instance()
                .definitions()
                .blocks()
                .controller()
                .maybeStack(1)
                .orNull();
            if (stack != null) return Icon.fromStack(stack);
        }
        if ("gtit-ae-advanced".equals(definition.id)
            && ("minecraft:comparator".equals(icon.item) || "appliedenergistics2:tile.BlockDrive".equals(icon.item))) {
            ItemStack stack = appeng.api.AEApi.instance()
                .definitions()
                .blocks()
                .drive()
                .maybeStack(1)
                .orNull();
            if (stack != null) return Icon.fromStack(stack);
        }
        return icon;
    }

    static void validateAvailable(TradePresetDefinition definition) {
        // The base library is always selected; missing optional rows use the registry's normal per-row filtering.
        if (DefaultTradeSources.BASE_ID.equals(definition.id)) return;
        if (TradePresetStore.BUILTIN_IDS.contains(definition.id) && !DefaultContentConfig.loadDefaultTradeGroups())
            return;
        for (TradePresetDefinition.Tab tab : definition.tabs) {
            for (NekoTradeEntry entry : tab.trades) {
                if (entry.getRequiresMods() != null) {
                    for (String mod : entry.getRequiresMods()) {
                        if (mod == null || mod.isEmpty() || !Loader.isModLoaded(mod)) {
                            throw new IllegalArgumentException("预设 " + definition.id + " 缺少 Mod: " + mod);
                        }
                    }
                }
                String quest = entry.getBqQuestId();
                if (quest != null && !quest.isEmpty()) {
                    UUID questId = NekoBqQuestIdParser.parse(quest);
                    if (questId == null || !NekoBqBridge.questExistsStrict(questId)) {
                        throw new IllegalArgumentException("预设 " + definition.id + " 的 BQ 任务不存在: " + quest);
                    }
                }
                if (entry.getCurrency() != null && entry.getCurrency()
                    .getAmount() > 0
                    && com.miaokatze.gtit.currency.NekoCurrencyRegistrar.getItemStack(
                        entry.getCurrency()
                            .getType(),
                        entry.getCurrency()
                            .getAmount())
                        == null) {
                    throw new IllegalArgumentException("预设包含未注册的货币: " + entry.getId());
                }
                if (entry.getFromItems() != null) {
                    for (NekoTradeEntry.ItemEntry item : entry.getFromItems()) {
                        if (item == null || NekoBigItemStack.fromItemEntry(item) == null) {
                            throw new IllegalArgumentException("预设需求物品无效: " + entry.getId());
                        }
                    }
                }
                for (NekoTradeEntry.ItemEntry item : entry.getToItems()) {
                    if (item == null || NekoBigItemStack.fromItemEntry(item) == null) {
                        throw new IllegalArgumentException("预设产出物品无效: " + entry.getId());
                    }
                }
            }
        }
    }

    private static void requireServer() {
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.SERVER) {
            throw new IllegalStateException("预设文件只能由服务器读取");
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (store == null || activeServer != server) initialize(server);
        if (store == null) throw new IllegalStateException("服务器预设系统不可用，请查看服务器日志");
    }

    private static void requirePermission(EntityPlayerMP player) {
        if (!canConfigure(player)) throw new IllegalArgumentException(
            "仅单人存档所有者或专用服务器 OP 等级 " + DefaultContentConfig.managementOpLevel() + " 可管理预设");
        requireServer();
    }

    private static void refreshTrades() {
        if (!NekoTradeRegistryV2.reload()) throw new IllegalStateException("预设文件已保存，但运行时重载失败，请查看服务器日志");
        NekoTradeNetworkManager.sendSyncToAll();
    }

    private static Result result(EntityPlayerMP player, boolean success, String message) {
        Result result = new Result();
        result.success = success;
        result.message = message == null ? "预设操作失败" : message;
        result.snapshot = snapshot(player);
        return result;
    }

    public static final class Icon {

        public String item = "minecraft:chest";
        public int meta;
        public String nbtBase64 = "";

        public static Icon fromStack(ItemStack stack) {
            Icon icon = new Icon();
            NekoPageEntry page = new NekoPageEntry();
            page.setIconFromItemStack(stack == null ? null : stack.copy());
            icon.item = page.getIconItem();
            icon.meta = page.getIconMeta();
            icon.nbtBase64 = page.getIconNbt() == null ? "" : page.getIconNbt();
            if (icon.nbtBase64.length() > 32768) throw new IllegalArgumentException("图标 NBT 超过 32 KiB 限制");
            return icon;
        }

        public ItemStack toStack() {
            if (item == null || item.length() > 256 || nbtBase64 == null || nbtBase64.length() > 32768) return null;
            NekoPageEntry page = new NekoPageEntry();
            page.setIconItem(item);
            page.setIconMeta(meta);
            page.setIconNbt(nbtBase64);
            return page.toIconItemStack();
        }
    }

    public static final class Snapshot {

        public long revision;
        public List<String> appliedIds = new ArrayList<>();
        public int appliedTradeCount;
        // Server-written presentation state survives rebuilding the immutable catalog widget tree.
        public String feedback = "";
        public List<String> pendingSelection;
        public boolean editorOpen;
        public boolean canManage;
        public List<PresetSummary> presets = new ArrayList<>();
        public List<TabSummary> tabs = new ArrayList<>();
        public List<String> errors = new ArrayList<>();
    }

    public static final class PresetSummary {

        public String id;
        public String name;
        public String description;
        public Icon icon;
        public List<String> mutuallyExclusiveIds = new ArrayList<>();
        public boolean selected;
        public boolean builtin;
        public String sourceKind;
        public boolean overridden;
        public boolean defaultUpdateAvailable;
        public boolean empty;
        public int tabCount;
        public int tradeCount;
        public List<Integer> tabIds = new ArrayList<>();
    }

    public static final class TabSummary {

        public int id;
        public String name;
        public String sourcePresetId;
        public String sourceKind;
    }

    public static final class Result {

        public boolean success;
        public String message;
        public Snapshot snapshot;
    }
}
