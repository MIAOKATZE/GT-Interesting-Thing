package com.miaokatze.gtit.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * 在线玩家表访问工具（O2-12）
 * <p>
 * 收编原先分散在 9 个文件 18 处的 {@code playerEntityList} 线性扫描
 * （Tab 补全收集玩家名 / 收集在线 UUID 集 / 按业务遍历全体在线 /
 * 按 UUID 或名查单个玩家四种形态），统一 null 语义与服务器未启动的防御。
 * <p>
 * 先例原为 {@code NekoWalletManager.buildOnlinePlayerCache} 私有实现，本类是其上提泛化。
 * 与 {@code PlayerResolver}（O2-06 一期）同包规划，共同构成 util 的玩家访问设施。
 */
public final class PlayerLookup {

    private PlayerLookup() {}

    /**
     * 按 UUID 查找在线玩家
     *
     * @param uuid 玩家 UUID（null 或服务器未启动时返回 null）
     * @return 在线的该玩家；不在线返回 null
     */
    public static EntityPlayerMP getOnlinePlayerByUuid(UUID uuid) {
        if (uuid == null) return null;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) obj;
                if (uuid.equals(player.getUniqueID())) {
                    return player;
                }
            }
        }
        return null;
    }

    /**
     * 按玩家名查找在线玩家（大小写不敏感，语义同
     * {@code ServerConfigurationManager#func_152612_a}）
     *
     * @param name 玩家名（null/空或服务器未启动时返回 null）
     * @return 在线的该玩家；不在线返回 null
     */
    public static EntityPlayerMP getOnlinePlayerByName(String name) {
        if (name == null || name.isEmpty()) return null;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) obj;
                if (player.getCommandSenderName()
                    .equalsIgnoreCase(name)) {
                    return player;
                }
            }
        }
        return null;
    }

    /**
     * 收集全体在线玩家名（命令 Tab 补全等场景）
     *
     * @return 在线玩家名列表（服务器未启动时为空列表）
     */
    public static List<String> getOnlineNames() {
        List<String> names = new ArrayList<>();
        forEachOnlinePlayer(player -> names.add(player.getCommandSenderName()));
        return names;
    }

    /**
     * 遍历全体在线玩家（按业务遍历全体在线的场景）
     * <p>
     * 服务器未启动时静默跳过；消费方对单个玩家的异常处理自行包裹
     * （一个玩家的异常不影响其余玩家遍历）。
     *
     * @param action 逐玩家回调（null 直接忽略）
     */
    public static void forEachOnlinePlayer(Consumer<EntityPlayerMP> action) {
        if (action == null) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                action.accept((EntityPlayerMP) obj);
            }
        }
    }

    /**
     * 构建在线玩家 UUID 集（离线 .dat 扫描跳过在线玩家等场景）
     *
     * @return 在线玩家 UUID 集（服务器未启动时为空集）
     */
    public static Set<UUID> buildUuidSet() {
        Set<UUID> uuids = new HashSet<>();
        forEachOnlinePlayer(player -> {
            if (player.getUniqueID() != null) {
                uuids.add(player.getUniqueID());
            }
        });
        return uuids;
    }
}
