package com.miaokatze.gtit.trade;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.miaokatze.gtit.lottery.LotteryNetworkManager;
import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * 猫猫币钱包管理器
 * 单例模式，管理所有玩家的猫猫币钱包
 * <p>
 * 优先使用团队共享钱包（通过 GTNHLib Teams API）
 * 如果团队不可用，回退到个人钱包（存储在 <world>/gtit_neko_wallets/<player_uuid>.dat）
 */
public class NekoWalletManager {

    public static final NekoWalletManager INSTANCE = new NekoWalletManager();

    private final Map<UUID, NekoWallet> personalWallets = new HashMap<>();
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
     * 获取玩家的钱包
     * 优先返回团队共享钱包，如果团队不可用则回退到个人钱包
     */
    public NekoWallet getWallet(UUID playerId) {
        if (playerId == null) return null;

        // 优先尝试团队钱包
        NekoWallet teamWallet = getTeamWallet(playerId);
        if (teamWallet != null) {
            Team team = TeamManager.getTeamByPlayer(playerId);
            if (team != null) {
                teamWallet.setTeamId(team.getTeamId());
            }
            return teamWallet;
        }

        // 回退到个人钱包
        NekoWallet wallet = personalWallets.get(playerId);
        if (wallet == null) {
            wallet = loadWallet(playerId);
            if (wallet == null) {
                wallet = new NekoWallet();
            }
            wallet.setPlayerId(playerId);
            personalWallets.put(playerId, wallet);
        }
        return wallet;
    }

    /**
     * 获取团队共享钱包
     *
     * @return 团队钱包，如果团队不可用返回 null
     */
    private NekoWallet getTeamWallet(UUID playerId) {
        try {
            Team team = TeamManager.getTeamByPlayer(playerId);
            if (team == null) return null;
            ITeamData teamData = team.getData(NekoTeamData.ID);
            if (teamData instanceof NekoTeamData) {
                return ((NekoTeamData) teamData).getWallet();
            }
        } catch (NoClassDefFoundError e) {
            // GTNHLib Teams API 不可用
        } catch (Exception e) {
            GTInterestingThing.LOG.error("获取团队钱包失败: " + playerId, e);
        }
        return null;
    }

    /**
     * 保存玩家钱包
     * 如果是团队钱包，标记团队数据为脏；如果是个人钱包，保存到磁盘
     */
    public void saveWallet(UUID playerId) {
        if (playerId == null) return;

        // 检查是否使用团队钱包
        try {
            Team team = TeamManager.getTeamByPlayer(playerId);
            if (team != null) {
                ITeamData teamData = team.getData(NekoTeamData.ID);
                if (teamData instanceof NekoTeamData) {
                    team.markDirty();
                    return;
                }
            }
        } catch (NoClassDefFoundError e) {
            // GTNHLib Teams API 不可用，继续使用个人钱包
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存团队钱包失败: " + playerId, e);
        }

        // 保存个人钱包到磁盘
        if (saveDir == null) return;
        NekoWallet wallet = personalWallets.get(playerId);
        if (wallet == null) return;
        File file = new File(saveDir, playerId.toString() + ".dat");
        try {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setTag("wallet", wallet.writeToNBT());
            CompressedStreamTools.safeWrite(nbt, file);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存猫猫币钱包失败: " + playerId, e);
        }
    }

    /**
     * 从磁盘加载玩家个人钱包
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

    // ==================== 钱包余额变化通知 ====================

    /**
     * 钱包余额变化时推送抽奖全量同步（保底/钱包余额）给相关玩家。
     * <p>
     * 由 {@link NekoWallet} 在余额实际变化后调用。个人钱包（teamId 为 null）推送给
     * playerId 对应玩家；团队钱包推送给团队成员全体在线玩家——保证抽奖界面
     * {@link com.miaokatze.gtit.lottery.LotteryClientData} 的余额缓存与背包 tooltip
     * 及时刷新。
     *
     * @param playerId 个人钱包玩家 UUID（团队钱包时可为 null）
     * @param teamId   团队钱包团队 UUID（个人钱包时 null）
     */
    public void notifyWalletChanged(UUID playerId, UUID teamId) {
        // 服务端检查（钱包余额变化只发生在服务端）
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.SERVER) return;
        if (!LotteryNetworkManager.isInitialized()) return;

        if (teamId != null) {
            // 团队钱包：推送给全体在线队员
            Team team = TeamManager.getTeamById(teamId);
            if (team == null) return;
            for (UUID member : team.getMembers()) {
                EntityPlayerMP player = getPlayerByUUID(member);
                if (player != null) {
                    LotteryNetworkManager.sendSyncToClient(player);
                }
            }
            return;
        }
        // 个人钱包：推送给该玩家
        if (playerId != null) {
            EntityPlayerMP player = getPlayerByUUID(playerId);
            if (player != null) {
                LotteryNetworkManager.sendSyncToClient(player);
            }
        }
    }

    /** 按 UUID 查找在线玩家（参照 DailySignInManager 模式） */
    private static EntityPlayerMP getPlayerByUUID(UUID playerId) {
        if (playerId == null) return null;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) obj;
            if (playerId.equals(player.getUniqueID())) {
                return player;
            }
        }
        return null;
    }

    // ==================== 生命周期 ====================

    /**
     * 卸载玩家个人钱包（玩家下线时调用）
     * 团队钱包由 GTNHLib Teams 管理，无需卸载
     */
    public void unloadWallet(UUID playerId) {
        saveWallet(playerId);
        personalWallets.remove(playerId);
    }

    /**
     * 保存所有个人钱包（服务器关闭时调用）
     * 团队钱包由 GTNHLib Teams 自动管理
     */
    public void saveAll() {
        for (UUID playerId : personalWallets.keySet()) {
            saveWallet(playerId);
        }
    }
}
