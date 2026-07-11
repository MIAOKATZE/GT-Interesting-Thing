package com.miaokatze.gtit.trade.v2;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 历史记录管理器单例
 * <p>
 * 管理所有玩家的交易历史，按双层 Map 组织：
 * 外层 key 为玩家 UUID，内层 key 为交易组 UUID，value 为交易历史记录。
 * 使用 ConcurrentHashMap 保证线程安全。
 * <p>
 * 持久化方案参考 {@link com.miaokatze.gtit.trade.NekoWalletManager}：
 * 每个玩家的历史记录保存到 <world>/gtit_neko_histories/<player_uuid>.dat，
 * 使用 NBT 序列化 + CompressedStreamTools 压缩写入。
 */
public class NekoHistoryManager {

    /** 单例实例 */
    public static final NekoHistoryManager INSTANCE = new NekoHistoryManager();

    /** 玩家交易历史：playerId -> (tradeGroupId -> history) */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, NekoTradeHistory>> histories;

    /** 历史记录存储目录（<world>/gtit_neko_histories/） */
    private File saveDir = null;

    private NekoHistoryManager() {
        this.histories = new ConcurrentHashMap<>();
    }

    /**
     * 初始化存储目录
     * <p>
     * 在 CommonProxy.serverStarted 中调用（需要 World 对象）。
     *
     * @param world 当前世界对象
     */
    public void init(World world) {
        saveDir = new File(
            world.getSaveHandler()
                .getWorldDirectory(),
            "gtit_neko_histories");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        GTInterestingThing.LOG.info("猫猫币交易历史存储目录: {}", saveDir.getAbsolutePath());
    }

    /**
     * 获取指定玩家对指定交易组的历史记录
     * <p>
     * 先从内存查找，内存中没有时尝试从磁盘懒加载该玩家的全部历史，
     * 仍未找到则自动创建空历史记录，保证永远不会返回 null。
     *
     * @param playerId     玩家 UUID
     * @param tradeGroupId 交易组 UUID
     * @return 交易历史记录（自动创建空历史，不为 null）
     */
    public NekoTradeHistory getHistory(UUID playerId, UUID tradeGroupId) {
        // 外层：按玩家查找内存中的历史 Map
        ConcurrentHashMap<UUID, NekoTradeHistory> playerHistories = histories.get(playerId);
        if (playerHistories == null) {
            // 内存中没有，尝试从磁盘懒加载
            playerHistories = loadHistory(playerId);
            if (playerHistories == null) {
                playerHistories = new ConcurrentHashMap<>();
            }
            // putIfAbsent 保证线程安全：若并发加载只保留首个
            ConcurrentHashMap<UUID, NekoTradeHistory> existing = histories.putIfAbsent(playerId, playerHistories);
            if (existing != null) {
                playerHistories = existing;
            }
        }
        // 内层：按交易组查找或创建空 NekoTradeHistory
        return playerHistories.computeIfAbsent(tradeGroupId, k -> new NekoTradeHistory());
    }

    /**
     * 重置指定玩家对指定交易组的历史记录
     *
     * @param playerId     玩家 UUID
     * @param tradeGroupId 交易组 UUID
     */
    public void resetHistory(UUID playerId, UUID tradeGroupId) {
        NekoTradeHistory history = getHistory(playerId, tradeGroupId);
        history.reset();
        markDirty(playerId);
    }

    /**
     * 重置指定玩家的所有交易历史记录
     * <p>
     * 遍历玩家名下的所有交易组历史，逐一调用 reset()。
     *
     * @param playerId 玩家 UUID
     */
    public void resetAllHistory(UUID playerId) {
        ConcurrentHashMap<UUID, NekoTradeHistory> playerHistories = histories.get(playerId);
        if (playerHistories != null) {
            for (NekoTradeHistory history : playerHistories.values()) {
                history.reset();
            }
        }
        markDirty(playerId);
    }

    /**
     * 标记指定玩家的历史数据为脏（需持久化）
     * <p>
     * 立即将该玩家的全部历史记录保存到磁盘文件。
     *
     * @param playerId 玩家 UUID
     */
    public void markDirty(UUID playerId) {
        saveHistory(playerId);
    }

    /**
     * 保存指定玩家的全部历史记录到磁盘
     * <p>
     * 将内存中该玩家的所有交易组历史序列化为 NBT，
     * 压缩写入 <saveDir>/<playerId>.dat 文件。
     * 若存储目录未初始化或玩家无历史记录，则跳过。
     *
     * @param playerId 玩家 UUID
     */
    private void saveHistory(UUID playerId) {
        if (saveDir == null || playerId == null) return;
        ConcurrentHashMap<UUID, NekoTradeHistory> playerHistories = histories.get(playerId);
        if (playerHistories == null || playerHistories.isEmpty()) return;

        File file = new File(saveDir, playerId.toString() + ".dat");
        try {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList historyList = new NBTTagList();
            for (java.util.Map.Entry<UUID, NekoTradeHistory> entry : playerHistories.entrySet()) {
                NBTTagCompound historyNbt = new NBTTagCompound();
                historyNbt.setString(
                    "groupId",
                    entry.getKey()
                        .toString());
                historyNbt.setTag(
                    "history",
                    entry.getValue()
                        .writeToNBT());
                historyList.appendTag(historyNbt);
            }
            root.setTag("histories", historyList);
            CompressedStreamTools.safeWrite(root, file);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存猫猫币交易历史失败: " + playerId, e);
        }
    }

    /**
     * 从磁盘加载指定玩家的全部历史记录
     * <p>
     * 读取 <saveDir>/<playerId>.dat 文件，反序列化为双层 Map。
     * 若文件不存在或读取失败，返回 null（调用方会创建空 Map）。
     *
     * @param playerId 玩家 UUID
     * @return 玩家的交易组历史 Map，加载失败返回 null
     */
    private ConcurrentHashMap<UUID, NekoTradeHistory> loadHistory(UUID playerId) {
        if (saveDir == null || playerId == null) return null;
        File file = new File(saveDir, playerId.toString() + ".dat");
        if (!file.exists()) return null;
        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            if (root == null || !root.hasKey("histories")) return null;
            NBTTagList historyList = root.getTagList("histories", 10);
            ConcurrentHashMap<UUID, NekoTradeHistory> result = new ConcurrentHashMap<>();
            for (int i = 0; i < historyList.tagCount(); i++) {
                NBTTagCompound historyNbt = historyList.getCompoundTagAt(i);
                try {
                    UUID groupId = UUID.fromString(historyNbt.getString("groupId"));
                    NekoTradeHistory history = new NekoTradeHistory();
                    history.loadFromNBT(historyNbt.getCompoundTag("history"));
                    result.put(groupId, history);
                } catch (IllegalArgumentException e) {
                    // groupId 格式无效，跳过该条记录
                    GTInterestingThing.LOG.warn("跳过无效的交易组ID: " + historyNbt.getString("groupId"));
                }
            }
            return result;
        } catch (Exception e) {
            GTInterestingThing.LOG.error("加载猫猫币交易历史失败: " + playerId, e);
            return null;
        }
    }

    /**
     * 卸载指定玩家的所有历史记录（玩家退出时调用）
     * <p>
     * 先保存到磁盘（触发持久化），再从内存中移除。
     *
     * @param playerId 玩家 UUID
     */
    public void unloadPlayer(UUID playerId) {
        markDirty(playerId);
        histories.remove(playerId);
    }

    /**
     * 保存所有内存中的历史记录（服务器关闭时调用）
     */
    public void saveAll() {
        for (UUID playerId : histories.keySet()) {
            saveHistory(playerId);
        }
    }

    /**
     * 清空所有历史记录
     */
    public void clearAll() {
        histories.clear();
    }
}
