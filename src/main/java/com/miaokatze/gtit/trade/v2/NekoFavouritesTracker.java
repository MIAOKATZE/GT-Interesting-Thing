package com.miaokatze.gtit.trade.v2;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 收藏追踪器单例
 * <p>
 * 完美复刻 VM mod 的 {@code com.cubefury.vendingmachine.trade.FavouritesTracker}，
 * 管理所有玩家收藏的交易。
 * <p>
 * 使用 V2 格式 {@code groupId:tradeIndex} 标识收藏的交易：
 * <ul>
 * <li>{@code groupId} - 交易组 UUID（{@link NekoTradeGroup#getId()}）</li>
 * <li>{@code tradeIndex} - 交易在交易组内的索引（int）</li>
 * </ul>
 * <p>
 * 持久化方案参考 {@link NekoHistoryManager}：
 * 每个玩家的收藏保存到 {@code <world>/gtit_neko_favourites/<player_uuid>.dat}，
 * 使用 NBT 序列化 + CompressedStreamTools 压缩写入。
 * <p>
 * 线程安全：使用 ConcurrentHashMap + CopyOnWriteArraySet 保证并发读写安全。
 */
public class NekoFavouritesTracker {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 单例实例 */
    public static final NekoFavouritesTracker INSTANCE = new NekoFavouritesTracker();

    /**
     * 玩家收藏集合：playerId -> Set of (groupId, tradeIndex) pairs
     * <p>
     * 使用 ConcurrentHashMap 保证外层线程安全，
     * 内层使用 ConcurrentHashMap.newKeySet() 保证集合操作线程安全。
     */
    private final ConcurrentHashMap<UUID, Set<Pair<UUID, Integer>>> favourites;

    /** 收藏存储目录（{@code <world>/gtit_neko_favourites/}） */
    private File saveDir = null;

    private NekoFavouritesTracker() {
        this.favourites = new ConcurrentHashMap<>();
    }

    /**
     * 初始化存储目录
     * <p>
     * 在 CommonProxy.serverStarted 中调用（需要 World 对象）。
     * 创建 {@code <world>/gtit_neko_favourites/} 目录。
     *
     * @param world 当前世界对象
     */
    public void init(World world) {
        saveDir = new File(
            world.getSaveHandler()
                .getWorldDirectory(),
            "gtit_neko_favourites");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        LOG.info("猫猫币收藏存储目录: {}", saveDir.getAbsolutePath());
    }

    // ==================== 收藏操作 ====================

    /**
     * 添加收藏
     * <p>
     * 将指定的交易标记为收藏。如果已经收藏则不重复添加。
     *
     * @param playerId   玩家 UUID
     * @param groupId    交易组 UUID
     * @param tradeIndex 交易在组内的索引
     */
    public void addFavourite(UUID playerId, UUID groupId, int tradeIndex) {
        Set<Pair<UUID, Integer>> playerFaves = getPlayerFavourites(playerId);
        playerFaves.add(new ImmutablePair<>(groupId, tradeIndex));
        saveFavourites(playerId);
    }

    /**
     * 移除收藏
     * <p>
     * 取消指定交易的收藏标记。如果未收藏则无操作。
     *
     * @param playerId   玩家 UUID
     * @param groupId    交易组 UUID
     * @param tradeIndex 交易在组内的索引
     */
    public void removeFavourite(UUID playerId, UUID groupId, int tradeIndex) {
        Set<Pair<UUID, Integer>> playerFaves = favourites.get(playerId);
        if (playerFaves != null) {
            playerFaves.remove(new ImmutablePair<>(groupId, tradeIndex));
            saveFavourites(playerId);
        }
    }

    /**
     * 切换收藏状态
     * <p>
     * 已收藏则取消收藏，未收藏则添加收藏。
     *
     * @param playerId   玩家 UUID
     * @param groupId    交易组 UUID
     * @param tradeIndex 交易在组内的索引
     */
    public void toggleFavourite(UUID playerId, UUID groupId, int tradeIndex) {
        Set<Pair<UUID, Integer>> playerFaves = getPlayerFavourites(playerId);
        Pair<UUID, Integer> pair = new ImmutablePair<>(groupId, tradeIndex);
        if (playerFaves.contains(pair)) {
            playerFaves.remove(pair);
        } else {
            playerFaves.add(pair);
        }
        saveFavourites(playerId);
    }

    /**
     * 检查是否已收藏
     *
     * @param playerId   玩家 UUID
     * @param groupId    交易组 UUID
     * @param tradeIndex 交易在组内的索引
     * @return true 表示已收藏
     */
    public boolean isFavourite(UUID playerId, UUID groupId, int tradeIndex) {
        Set<Pair<UUID, Integer>> playerFaves = favourites.get(playerId);
        if (playerFaves == null) return false;
        return playerFaves.contains(new ImmutablePair<>(groupId, tradeIndex));
    }

    /**
     * 获取指定玩家的所有收藏
     * <p>
     * 返回不可修改的视图，防止外部直接修改集合。
     *
     * @param playerId 玩家 UUID
     * @return 收藏集合的不可修改视图（不为 null，未收藏时返回空集）
     */
    public Set<Pair<UUID, Integer>> getFavourites(UUID playerId) {
        Set<Pair<UUID, Integer>> playerFaves = favourites.get(playerId);
        if (playerFaves == null) return Collections.emptySet();
        return Collections.unmodifiableSet(playerFaves);
    }

    /**
     * 清空指定玩家的所有收藏
     *
     * @param playerId 玩家 UUID
     */
    public void clearFavourites(UUID playerId) {
        Set<Pair<UUID, Integer>> playerFaves = favourites.get(playerId);
        if (playerFaves != null) {
            playerFaves.clear();
            saveFavourites(playerId);
        }
    }

    // ==================== 持久化 ====================

    /**
     * 获取或加载玩家的收藏集合
     * <p>
     * 先从内存查找，内存中没有时尝试从磁盘懒加载，
     * 仍未找到则创建空集合并放入内存。
     *
     * @param playerId 玩家 UUID
     * @return 玩家的收藏集合（不为 null）
     */
    private Set<Pair<UUID, Integer>> getPlayerFavourites(UUID playerId) {
        Set<Pair<UUID, Integer>> playerFaves = favourites.get(playerId);
        if (playerFaves == null) {
            // 内存中没有，尝试从磁盘懒加载
            playerFaves = loadFavourites(playerId);
            if (playerFaves == null) {
                playerFaves = ConcurrentHashMap.newKeySet();
            }
            // putIfAbsent 保证线程安全：若并发加载只保留首个
            Set<Pair<UUID, Integer>> existing = favourites.putIfAbsent(playerId, playerFaves);
            if (existing != null) {
                playerFaves = existing;
            }
        }
        return playerFaves;
    }

    /**
     * 保存指定玩家的收藏到磁盘
     * <p>
     * 将内存中该玩家的所有收藏序列化为 NBT，
     * 压缩写入 {@code <saveDir>/<playerId>.dat} 文件。
     * 若存储目录未初始化或玩家无收藏，则跳过。
     *
     * @param playerId 玩家 UUID
     */
    private void saveFavourites(UUID playerId) {
        if (saveDir == null || playerId == null) return;
        Set<Pair<UUID, Integer>> playerFaves = favourites.get(playerId);
        if (playerFaves == null) return;

        File file = new File(saveDir, playerId.toString() + ".dat");
        try {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList faveList = new NBTTagList();
            for (Pair<UUID, Integer> fave : playerFaves) {
                NBTTagCompound faveNbt = new NBTTagCompound();
                faveNbt.setString(
                    "groupId",
                    fave.getLeft()
                        .toString());
                faveNbt.setInteger("tradeIndex", fave.getRight());
                faveList.appendTag(faveNbt);
            }
            root.setTag("favourites", faveList);
            CompressedStreamTools.safeWrite(root, file);
        } catch (Exception e) {
            LOG.error("保存猫猫币收藏失败: " + playerId, e);
        }
    }

    /**
     * 从磁盘加载指定玩家的收藏
     * <p>
     * 读取 {@code <saveDir>/<playerId>.dat} 文件，反序列化为收藏集合。
     * 若文件不存在或读取失败，返回 null（调用方会创建空集合）。
     *
     * @param playerId 玩家 UUID
     * @return 玩家的收藏集合，加载失败返回 null
     */
    private Set<Pair<UUID, Integer>> loadFavourites(UUID playerId) {
        if (saveDir == null || playerId == null) return null;
        File file = new File(saveDir, playerId.toString() + ".dat");
        if (!file.exists()) return null;
        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            if (root == null || !root.hasKey("favourites")) return null;
            NBTTagList faveList = root.getTagList("favourites", 10);
            Set<Pair<UUID, Integer>> result = ConcurrentHashMap.newKeySet();
            for (int i = 0; i < faveList.tagCount(); i++) {
                NBTTagCompound faveNbt = faveList.getCompoundTagAt(i);
                try {
                    UUID groupId = UUID.fromString(faveNbt.getString("groupId"));
                    int tradeIndex = faveNbt.getInteger("tradeIndex");
                    result.add(new ImmutablePair<>(groupId, tradeIndex));
                } catch (IllegalArgumentException e) {
                    // groupId 格式无效，跳过该条记录
                    LOG.warn("跳过无效的收藏记录 groupId: " + faveNbt.getString("groupId"));
                }
            }
            LOG.info("已加载 {} 条收藏记录: {}", result.size(), playerId);
            return result;
        } catch (Exception e) {
            LOG.error("加载猫猫币收藏失败: " + playerId, e);
            return null;
        }
    }

    /**
     * 卸载指定玩家的收藏数据（玩家退出时调用）
     * <p>
     * 先保存到磁盘（触发持久化），再从内存中移除。
     *
     * @param playerId 玩家 UUID
     */
    public void unloadPlayer(UUID playerId) {
        saveFavourites(playerId);
        favourites.remove(playerId);
    }

    /**
     * 保存所有内存中的收藏数据（服务器关闭时调用）
     */
    public void saveAll() {
        for (UUID playerId : favourites.keySet()) {
            saveFavourites(playerId);
        }
    }

    /**
     * 清空所有收藏数据（仅内存，不删除磁盘文件）
     */
    public void clearAll() {
        favourites.clear();
    }
}
