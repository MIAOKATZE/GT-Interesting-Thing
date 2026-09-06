package com.miaokatze.gtit.terminal;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.util.PlayerLookup;
import com.miaokatze.gtit.util.PlayerResolver;

/**
 * 新手礼包领取审计工具（T1 自 {@code command/GTITGiftCommand} 原位迁移）
 * <p>
 * 迁移内容（逻辑逐行保持，行号对应迁移前 GTITGiftCommand）：
 * <ul>
 * <li>{@code GIFT_CLAIMED_KEY} 常量（原 :355）</li>
 * <li>{@link #hasGiftClaimedFlag}（原 :402）</li>
 * <li>{@link #collectOfflineClaimedPlayers}（原 :416）</li>
 * <li>{@link #offlineDatHasGiftClaimed}（原 :451）</li>
 * <li>{@link #resetOnlinePlayerGiftFlag}（原 :564）</li>
 * <li>{@link #resetOfflinePlayerGiftFlag}（原 :580，含写前在线复查与
 * {@code CompressedStreamTools.safeWrite} 竞态保护，逐行保持）</li>
 * <li>{@link #resetOfflinePlayerGiftFlagByName}（原 :640）</li>
 * <li>{@link #resetAllOfflinePlayerGiftFlags}（原 :695）</li>
 * </ul>
 * 原 GTITGiftCommand 内对应私有方法改为单行委托（签名不变，调用点零改动）；
 * 原私有辅助 {@code isPlayerOnline}（原 :630）随迁移一并复制为私有静态成员
 * （迁移后原类内已无调用点）。
 * <p>
 * 供管理终端礼包页（{@link GiftOps}，后续切片填充）与既有命令行路径共用。
 */
public final class StarterGiftAudit {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** NBT 键名：玩家已领取新手礼包的标记 */
    public static final String GIFT_CLAIMED_KEY = "gtit_received_starter_gift";

    private StarterGiftAudit() {
        // 静态工具类，禁止实例化
    }

    /**
     * 判断玩家数据中是否含新手礼包领取标记。
     * 接受两种输入：在线玩家的 getEntityData()（已处于 ForgeData 层级）或离线 .dat 的根 NBT。
     */
    public static boolean hasGiftClaimedFlag(NBTTagCompound dataTag) {
        if (dataTag == null) return false;
        // 在线路径：dataTag 即 ForgeData，直接取 PlayerPersisted
        if (dataTag.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            return dataTag.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                .hasKey(GIFT_CLAIMED_KEY);
        }
        return false;
    }

    /**
     * 扫描 playerdata/*.dat，收集已领取新手礼包的离线玩家，跳过当前在线玩家。
     * 玩家名优先从 usercache.json 反查，查不到则用 UUID。
     */
    public static void collectOfflineClaimedPlayers(Set<UUID> onlineUuids, List<String> outNames) {
        File worldDir = MinecraftServer.getServer()
            .getEntityWorld()
            .getSaveHandler()
            .getWorldDirectory();
        File playerdataDir = new File(worldDir, "playerdata");
        if (!playerdataDir.exists() || !playerdataDir.isDirectory()) {
            return;
        }
        File userCache = new File(worldDir.getParentFile(), "usercache.json");

        File[] datFiles = playerdataDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (datFiles == null) return;

        for (File datFile : datFiles) {
            try {
                String uuidStr = datFile.getName()
                    .replace(".dat", "");
                UUID fileUuid = UUID.fromString(uuidStr);
                if (onlineUuids.contains(fileUuid)) continue;

                if (offlineDatHasGiftClaimed(datFile)) {
                    String name = PlayerResolver.findNameFromUserCache(fileUuid, userCache);
                    outNames.add(name != null ? name : fileUuid.toString());
                }
            } catch (IllegalArgumentException ignored) {
                // 文件名不是合法 UUID，跳过
            }
        }
    }

    /**
     * 只读检查单个 .dat 文件中是否存在新手礼包领取标记（不修改文件）。
     * 路径：.dat 根 → ForgeData → PlayerPersisted → gtit_received_starter_gift
     */
    public static boolean offlineDatHasGiftClaimed(File datFile) {
        try {
            // 与在线玩家同侧的写操作冲突检查：再次确认不在线
            UUID fileUuid;
            try {
                fileUuid = UUID.fromString(
                    datFile.getName()
                        .replace(".dat", ""));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            if (isPlayerOnline(fileUuid)) return false;

            NBTTagCompound rootNbt = CompressedStreamTools.read(datFile);
            if (rootNbt == null) return false;
            if (!rootNbt.hasKey("ForgeData")) return false;
            NBTTagCompound forgeData = rootNbt.getCompoundTag("ForgeData");
            if (!forgeData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) return false;
            return forgeData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                .hasKey(GIFT_CLAIMED_KEY);
        } catch (Exception e) {
            LOG.error("检查离线玩家礼包标记失败: " + datFile.getName(), e);
            return false;
        }
    }

    /**
     * 重置在线玩家的礼包领取标记
     *
     * @return true 如果标记存在且被移除；false 如果标记不存在
     */
    public static boolean resetOnlinePlayerGiftFlag(EntityPlayerMP player) {
        NBTTagCompound playerData = player.getEntityData();
        NBTTagCompound persisted = playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.hasKey(GIFT_CLAIMED_KEY)) {
            persisted.removeTag(GIFT_CLAIMED_KEY);
            playerData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
            return true;
        }
        return false;
    }

    /**
     * 重置单个 .dat 文件中的礼包领取标记
     *
     * @return true 成功重置；false 标记不存在或操作失败
     */
    public static boolean resetOfflinePlayerGiftFlag(File datFile) {
        try {
            // 安全检查：操作 .dat 前再次确认玩家不在线，避免内存/文件数据冲突。
            // 外层批量方法虽收集过 onlineUuids，但收集与写入之间存在时间窗口；
            // 按名查找路径的二次确认也在更早时刻，无法覆盖真正落盘前的那一刻。
            UUID fileUuid;
            try {
                fileUuid = UUID.fromString(
                    datFile.getName()
                        .replace(".dat", ""));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            if (isPlayerOnline(fileUuid)) {
                LOG.warn("跳过在线玩家的 .dat 文件操作，避免内存/文件数据冲突: {}", datFile.getName());
                return false;
            }

            NBTTagCompound rootNbt = CompressedStreamTools.read(datFile);
            if (rootNbt == null) return false;

            // Forge 持久化自定义 entity 数据位于 .dat 根 NBT 的 "ForgeData" 子标签下，
            // 而 EntityPlayer.PERSISTED_NBT_TAG（"PlayerPersisted"）又在 ForgeData 之内：
            // .dat 根 → ForgeData → PlayerPersisted → gtit_received_starter_gift
            // 在线路径 player.getEntityData() 返回的正是这个 ForgeData 子标签，故在线逻辑正确；
            // 此前离线代码直接在根上找 PlayerPersisted，永远命中失败 → 离线重置一直无效。
            if (rootNbt.hasKey("ForgeData")) {
                NBTTagCompound forgeData = rootNbt.getCompoundTag("ForgeData");
                if (forgeData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
                    NBTTagCompound persisted = forgeData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
                    if (persisted.hasKey(GIFT_CLAIMED_KEY)) {
                        persisted.removeTag(GIFT_CLAIMED_KEY);
                        // getCompoundTag 不会自动把修改后的 tag 写回父节点，须逐层 setTag 回写
                        forgeData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
                        rootNbt.setTag("ForgeData", forgeData);
                        CompressedStreamTools.safeWrite(rootNbt, datFile);
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            LOG.error("重置离线玩家礼包标记失败: " + datFile.getName(), e);
            return false;
        }
    }

    /**
     * 通过玩家名重置离线玩家的礼包领取标记
     * 使用 usercache.json 查找 UUID，回退到扫描 .dat 文件
     *
     * @return true 成功重置；false 找不到玩家或操作失败
     */
    public static boolean resetOfflinePlayerGiftFlagByName(String playerName) {
        File worldDir = MinecraftServer.getServer()
            .getEntityWorld()
            .getSaveHandler()
            .getWorldDirectory();
        File playerdataDir = new File(worldDir, "playerdata");
        if (!playerdataDir.exists() || !playerdataDir.isDirectory()) {
            return false;
        }

        // 二次确认该玩家不在线
        EntityPlayerMP onlinePlayer = MinecraftServer.getServer()
            .getConfigurationManager()
            .func_152612_a(playerName);
        if (onlinePlayer != null) {
            return resetOnlinePlayerGiftFlag(onlinePlayer);
        }

        // 尝试通过 usercache.json 查找 UUID
        File userCache = new File(worldDir.getParentFile(), "usercache.json");
        UUID targetUuid = PlayerResolver.findUuidFromUserCache(playerName, userCache);
        if (targetUuid != null) {
            File datFile = new File(playerdataDir, targetUuid.toString() + ".dat");
            if (datFile.exists()) {
                return resetOfflinePlayerGiftFlag(datFile);
            }
        }

        // usercache 中找不到，扫描所有 .dat 文件（通过 usercache 反查 UUID 对应的玩家名）
        File[] datFiles = playerdataDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (datFiles == null) return false;

        for (File datFile : datFiles) {
            try {
                String uuidStr = datFile.getName()
                    .replace(".dat", "");
                UUID fileUuid = UUID.fromString(uuidStr);
                String cachedName = PlayerResolver.findNameFromUserCache(fileUuid, userCache);
                if (playerName.equalsIgnoreCase(cachedName)) {
                    return resetOfflinePlayerGiftFlag(datFile);
                }
            } catch (IllegalArgumentException ignored) {
                // 文件名不是合法 UUID，跳过
            }
        }

        return false;
    }

    /**
     * 重置所有离线玩家的礼包领取标记
     * 扫描 playerdata 目录，跳过当前在线的玩家，避免内存/文件数据冲突
     *
     * @return 成功重置的离线玩家数量
     */
    public static int resetAllOfflinePlayerGiftFlags() {
        File worldDir = MinecraftServer.getServer()
            .getEntityWorld()
            .getSaveHandler()
            .getWorldDirectory();
        File playerdataDir = new File(worldDir, "playerdata");
        if (!playerdataDir.exists() || !playerdataDir.isDirectory()) {
            return 0;
        }

        // 收集在线玩家的 UUID，跳过在线玩家的 .dat 文件（O2-12）
        Set<UUID> onlineUuids = PlayerLookup.buildUuidSet();

        int count = 0;
        File[] datFiles = playerdataDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (datFiles == null) return 0;

        for (File datFile : datFiles) {
            try {
                String uuidStr = datFile.getName()
                    .replace(".dat", "");
                UUID fileUuid = UUID.fromString(uuidStr);
                // 跳过在线玩家
                if (onlineUuids.contains(fileUuid)) continue;
                if (resetOfflinePlayerGiftFlag(datFile)) {
                    count++;
                }
            } catch (IllegalArgumentException ignored) {
                // 文件名不是合法 UUID，跳过
            }
        }
        return count;
    }

    /**
     * 检查指定 UUID 的玩家当前是否在线（O2-12：PlayerLookup 统一查询；
     * 自 GTITGiftCommand 原 :630 一并迁移）
     */
    private static boolean isPlayerOnline(UUID uuid) {
        return uuid != null && PlayerLookup.getOnlinePlayerByUuid(uuid) != null;
    }
}
