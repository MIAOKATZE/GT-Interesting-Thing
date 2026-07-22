package com.miaokatze.gtit.trade.v2;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * 编辑模式管理器（v1.7.0 目标 4）
 * <p>
 * 服务端权威记录处于「可视化配置编辑模式」的玩家 UUID 集合。
 * 编辑模式下，玩家打开猫猫售货机 GUI 时：
 * <ul>
 * <li>显示所有交易条目（绕过 BQ 锁定/冷却/可交易检查）</li>
 * <li>禁止交易与收藏等常规交互</li>
 * <li>左键点击条目弹出编辑面板（物品拖放 + 参数配置）</li>
 * <li>保存后直接写入服务端 JSON 配置文件</li>
 * </ul>
 * <p>
 * <b>线程安全</b>：使用 synchronized 保证并发安全（服务端主线程 + 网络线程可能并发访问）。
 * <p>
 * <b>自动清理</b>：玩家退出服务器时自动移出编辑模式（防止幽灵状态）。
 */
public class NekoEditModeManager {

    /** 单例实例 */
    public static final NekoEditModeManager INSTANCE = new NekoEditModeManager();

    /** 处于编辑模式的玩家 UUID 集合（服务端权威） */
    private final Set<UUID> editingPlayers = Collections.synchronizedSet(new HashSet<>());

    private NekoEditModeManager() {}

    /**
     * 初始化：注册玩家退出事件监听（用于自动清理）
     * <p>
     * 在 CommonProxy.init() 中调用。
     */
    public static void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(new PlayerLogoutListener());
    }

    /**
     * 进入编辑模式
     *
     * @param playerId 玩家 UUID
     */
    public void enterEditMode(UUID playerId) {
        if (playerId != null) {
            editingPlayers.add(playerId);
        }
    }

    /**
     * 退出编辑模式
     *
     * @param playerId 玩家 UUID
     */
    public void exitEditMode(UUID playerId) {
        if (playerId != null) {
            editingPlayers.remove(playerId);
        }
    }

    /**
     * 切换编辑模式
     *
     * @param playerId 玩家 UUID
     * @return 切换后的状态（true=进入编辑模式）
     */
    public boolean toggleEditMode(UUID playerId) {
        if (playerId == null) return false;
        if (editingPlayers.contains(playerId)) {
            editingPlayers.remove(playerId);
            return false;
        } else {
            editingPlayers.add(playerId);
            return true;
        }
    }

    /**
     * 检查玩家是否在编辑模式
     *
     * @param playerId 玩家 UUID
     * @return true 表示在编辑模式
     */
    public boolean isInEditMode(UUID playerId) {
        return playerId != null && editingPlayers.contains(playerId);
    }

    /**
     * 获取当前编辑模式玩家数量（调试用）
     *
     * @return 编辑模式玩家数
     */
    public int getEditingCount() {
        return editingPlayers.size();
    }

    /**
     * 清空所有编辑模式状态（服务器关闭时调用）
     */
    public void clearAll() {
        editingPlayers.clear();
    }

    /**
     * 玩家退出监听器：自动移出编辑模式
     */
    public static class PlayerLogoutListener {

        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.player instanceof EntityPlayerMP) {
                INSTANCE.exitEditMode(event.player.getUniqueID());
            }
        }
    }
}
