package com.miaokatze.gtit.trade;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 猫猫币钱包管理器
 * 单例模式，管理所有玩家的猫猫币钱包
 * 存储位置：<world>/gtit_neko_wallets/<player_uuid>.dat
 */
public class NekoWalletManager {

    public static final NekoWalletManager INSTANCE = new NekoWalletManager();

    private final Map<UUID, NekoWallet> wallets = new HashMap<>();
    private File saveDir = null;

    private NekoWalletManager() {}

    /**
     * 初始化存储目录
     * 在 CommonProxy.serverStarted 中调用（需要 World 对象）
     */
    public void init(World world) {
        saveDir = new File(
            world.getSaveHandler()
                .getWorldDirectory(),
            "gtit_neko_wallets");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        GTInterestingThing.LOG.info("猫猫币钱包存储目录: {}", saveDir.getAbsolutePath());
    }

    /**
     * 获取玩家的钱包（如果内存中没有则从磁盘加载）
     */
    public NekoWallet getWallet(UUID playerId) {
        if (playerId == null) return null;
        NekoWallet wallet = wallets.get(playerId);
        if (wallet == null) {
            wallet = loadWallet(playerId);
            if (wallet == null) {
                wallet = new NekoWallet();
            }
            wallets.put(playerId, wallet);
        }
        return wallet;
    }

    /**
     * 保存玩家钱包到磁盘
     */
    public void saveWallet(UUID playerId) {
        if (playerId == null || saveDir == null) return;
        NekoWallet wallet = wallets.get(playerId);
        if (wallet == null) return;
        File file = new File(saveDir, playerId.toString() + ".dat");
        try {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setTag("wallet", wallet.writeToNBT());
            // 使用 CompressedStreamTools 保存
            CompressedStreamTools.safeWrite(nbt, file);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存猫猫币钱包失败: " + playerId, e);
        }
    }

    /**
     * 从磁盘加载玩家钱包
     */
    private NekoWallet loadWallet(UUID playerId) {
        if (saveDir == null) return null;
        File file = new File(saveDir, playerId.toString() + ".dat");
        if (!file.exists()) return null;
        try {
            NBTTagCompound nbt = CompressedStreamTools.read(file);
            if (nbt != null && nbt.hasKey("wallet")) {
                NekoWallet wallet = new NekoWallet();
                wallet.readFromNBT(nbt.getCompoundTag("wallet"));
                return wallet;
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("加载猫猫币钱包失败: " + playerId, e);
        }
        return null;
    }

    /**
     * 卸载玩家钱包（玩家下线时调用）
     */
    public void unloadWallet(UUID playerId) {
        saveWallet(playerId);
        wallets.remove(playerId);
    }

    /**
     * 保存所有钱包（服务器关闭时调用）
     */
    public void saveAll() {
        for (UUID playerId : wallets.keySet()) {
            saveWallet(playerId);
        }
    }
}
