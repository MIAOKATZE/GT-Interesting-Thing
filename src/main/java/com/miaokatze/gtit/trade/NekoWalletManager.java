package com.miaokatze.gtit.trade;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.teams.Team;
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

    // ==================== 脏标记（BUG B1 生命周期兜底 + 余额推送节流） ====================

    /** 有未落盘变动的个人钱包（余额变化时标记，saveWallet 成功后清除） */
    private final Set<UUID> dirtyPersonalWallets = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /** 待推送余额的钱包键（"team:&lt;uuid&gt;" / "player:&lt;uuid&gt;"），由服务器 tick 每 ~100ms 冲刷一批 */
    private final Set<String> dirtyBalanceKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /** 上次余额冲刷时间（System.currentTimeMillis，节流用） */
    private long lastBalanceFlushMs = 0L;
    /** 余额推送最小间隔（毫秒）：批量投币/连抽场景合帧，避免全队全量包风暴（优化建议 三.1） */
    private static final long BALANCE_FLUSH_INTERVAL_MS = 100L;
    /** 团队钱包余额键前缀 */
    private static final String TEAM_KEY_PREFIX = "team:";
    /** 个人钱包余额键前缀 */
    private static final String PLAYER_KEY_PREFIX = "player:";

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
     * <p>
     * <b>团队切换语义（B2-06 文档化决策，与 O2-18(a) 同一结论）</b>：玩家入队后本方法
     * 即路由到团队钱包，个人钱包余额在入队时刻冻结（不再读写）；退队后回到冻结前快照。
     * 个人 .dat 文件不做删除，保留作退队恢复点——这是有意行为而非泄漏。
     */
    public NekoWallet getWallet(UUID playerId) {
        if (playerId == null) return null;

        // 优先尝试团队钱包（O2-04：Teams 探测/降级统一走 TeamDataProvider 门面）
        NekoWallet teamWallet = getTeamWallet(playerId);
        if (teamWallet != null) {
            Team team = TeamDataProvider.getTeam(playerId);
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
     * 获取团队共享钱包（O2-04：Teams 不可用时 getTeam/getData 返回 null，本方法随之返回 null）
     *
     * @return 团队钱包，如果团队不可用返回 null
     */
    private NekoWallet getTeamWallet(UUID playerId) {
        NekoTeamData teamData = TeamDataProvider.getData(TeamDataProvider.getTeam(playerId));
        return teamData != null ? teamData.getWallet() : null;
    }

    /**
     * 保存玩家钱包
     * 如果是团队钱包，标记团队数据为脏；如果是个人钱包，保存到磁盘
     * <p>
     * <b>B2-06 文档化决策</b>：团队分支仅 markDirty + 清个人脏标记后直接返回——
     * 个人 .dat 不在此路径清理（保留作退队恢复点），余额也不做合并（退队即回冻结快照）。
     */
    public void saveWallet(UUID playerId) {
        if (playerId == null) return;

        // 检查是否使用团队钱包（O2-04：Teams 探测/降级统一走 TeamDataProvider 门面）
        Team team = TeamDataProvider.getTeam(playerId);
        if (TeamDataProvider.getData(team) != null) {
            TeamDataProvider.markTeamDirty(team);
            // 玩家已转入团队钱包：个人钱包不再使用，同时清掉遗留脏标记
            dirtyPersonalWallets.remove(playerId);
            return;
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
            // 落盘成功后清除脏标记（保留失败时的标记，下个周期重试）
            dirtyPersonalWallets.remove(playerId);
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

    // ==================== 钱包余额变化通知（脏标记 + 节流冲刷） ====================

    /**
     * 钱包余额变化时登记待推送/待落盘标记（不再直接推送全量同步包）。
     * <p>
     * 由 {@link NekoWallet} 在余额实际变化后调用。个人钱包（teamId 为 null）登记
     * "player:&lt;uuid&gt;" 键并标记钱包脏（周期/登出/停服兜底落盘）；团队钱包登记
     * "team:&lt;uuid&gt;" 键。实际推送由 {@link #flushBalanceNotifications()} 在服务器
     * tick 上以约 100ms 间隔冲刷——只发余额轻量包
     * （{@link LotteryNetworkManager#sendBalanceToClient}），不发卡池摘要/保底全量包，
     * 消除批量投币/连抽场景对全队在线成员的 m×n 全量包风暴（优化建议 三.1）。
     *
     * @param playerId 个人钱包玩家 UUID（团队钱包时可为 null）
     * @param teamId   团队钱包团队 UUID（个人钱包时 null）
     */
    public void notifyWalletChanged(UUID playerId, UUID teamId) {
        // 服务端检查（钱包余额变化只发生在服务端）
        if (FMLCommonHandler.instance()
            .getEffectiveSide() != Side.SERVER) return;

        if (teamId != null) {
            // 团队钱包：登记待推送键（冲刷时对全体在线队员发轻量包并 team.markDirty()）
            dirtyBalanceKeys.add(TEAM_KEY_PREFIX + teamId);
        } else if (playerId != null) {
            // 个人钱包：标记脏（生命周期兜底落盘）+ 登记待推送键
            dirtyPersonalWallets.add(playerId);
            dirtyBalanceKeys.add(PLAYER_KEY_PREFIX + playerId);
        }
    }

    /**
     * 冲刷待推送的钱包余额（由 NekoWalletHandler 在服务器 tick 末尾调用）。
     * <p>
     * 节流：距上次冲刷不足 {@link #BALANCE_FLUSH_INTERVAL_MS} 毫秒时跳过（同一批
     * 连续变动合帧为最后一次快照）。冲刷时单趟扫描在线玩家表构建 UUID→player
     * 缓存，全体团队广播复用，替代逐成员 O(n) 线性扫描（m×n → m+n）。
     */
    public void flushBalanceNotifications() {
        if (dirtyBalanceKeys.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastBalanceFlushMs < BALANCE_FLUSH_INTERVAL_MS) return;
        lastBalanceFlushMs = now;

        // 取出本批待推送键（冲刷期间新到的键留给下一批）
        List<String> keys = new ArrayList<>(dirtyBalanceKeys);
        dirtyBalanceKeys.removeAll(keys);

        // 网络未初始化（理论上不会发生：通道在 init 阶段注册）时只处理团队脏标记
        boolean networkReady = LotteryNetworkManager.isInitialized();
        Map<UUID, EntityPlayerMP> onlineByUuid = networkReady ? buildOnlinePlayerCache()
            : Collections.<UUID, EntityPlayerMP>emptyMap();

        for (String key : keys) {
            try {
                if (key.startsWith(TEAM_KEY_PREFIX)) {
                    flushTeamBalance(key.substring(TEAM_KEY_PREFIX.length()), onlineByUuid);
                } else if (key.startsWith(PLAYER_KEY_PREFIX)) {
                    flushPersonalBalance(key.substring(PLAYER_KEY_PREFIX.length()), onlineByUuid);
                }
            } catch (Throwable t) {
                GTInterestingThing.LOG.error("推送钱包余额失败: " + key, t);
            }
        }
    }

    /** 团队钱包余额冲刷：team.markDirty() + 轻量包推送给全体在线队员（O2-04：Teams 访问走门面） */
    private void flushTeamBalance(String teamIdStr, Map<UUID, EntityPlayerMP> onlineByUuid) {
        UUID teamId;
        try {
            teamId = UUID.fromString(teamIdStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        try {
            Team team = TeamDataProvider.getTeamById(teamId);
            if (team == null) return;
            // 团队钱包变动标记团队数据脏（GTNHLib 随世界存档落盘，与 saveWallet 团队路径同语义）
            TeamDataProvider.markTeamDirty(team);
            NekoTeamData teamData = TeamDataProvider.getData(team);
            NekoWallet wallet = teamData != null ? teamData.getWallet() : null;
            if (wallet == null) return;
            Map<String, Integer> balances = snapshotBalances(wallet);
            for (UUID member : team.getMembers()) {
                EntityPlayerMP player = onlineByUuid.get(member);
                if (player != null) {
                    LotteryNetworkManager.sendBalanceToClient(player, balances);
                }
            }
        } catch (Throwable t) {
            GTInterestingThing.LOG.error("推送团队钱包余额失败: " + teamId, t);
        }
    }

    /** 个人钱包余额冲刷：轻量包推送给该玩家（若仍使用个人钱包且在线） */
    private void flushPersonalBalance(String playerIdStr, Map<UUID, EntityPlayerMP> onlineByUuid) {
        UUID playerId;
        try {
            playerId = UUID.fromString(playerIdStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        NekoWallet wallet = personalWallets.get(playerId);
        // 已转入团队钱包（teamId 置位）时不再按个人口径推送
        if (wallet == null || wallet.isTeamWallet()) return;
        EntityPlayerMP player = onlineByUuid.get(playerId);
        if (player == null) return;
        LotteryNetworkManager.sendBalanceToClient(player, snapshotBalances(wallet));
    }

    /** 钱包余额快照（currencyId → 数量；在钱包锁内取一致快照） */
    private static Map<String, Integer> snapshotBalances(NekoWallet wallet) {
        Map<String, Integer> balances = new HashMap<>();
        // NekoWallet 的读写方法均以自身为锁，外层同步保证键集与余额的一致快照
        // noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (wallet) {
            for (String currencyId : wallet.getCurrencyIds()) {
                if (currencyId != null) {
                    balances.put(currencyId, wallet.getCount(currencyId));
                }
            }
        }
        return balances;
    }

    /** 单趟扫描在线玩家表构建 UUID→player 缓存（本批冲刷内复用） */
    private static Map<UUID, EntityPlayerMP> buildOnlinePlayerCache() {
        Map<UUID, EntityPlayerMP> cache = new HashMap<>();
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return cache;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) obj;
                if (player.getUniqueID() != null) {
                    cache.put(player.getUniqueID(), player);
                }
            }
        }
        return cache;
    }

    // ==================== 生命周期 ====================

    /**
     * 卸载玩家个人钱包（玩家下线时由 NekoWalletHandler 调用）
     * 团队钱包由 GTNHLib Teams 管理，无需卸载
     */
    public void unloadWallet(UUID playerId) {
        saveWallet(playerId);
        personalWallets.remove(playerId);
    }

    /**
     * 落盘所有脏个人钱包（周期保存兜底，由 NekoWalletHandler 每 5 分钟调用）。
     * <p>
     * O2-17 口径统一后，余额变化即标记脏（{@link #notifyWalletChanged}）是全部写路径的
     * 唯一落盘触发口径——写路径只管改余额（addCount/tryDeduct/resetCount/resetAll
     * 均在余额实际变化后自动登记脏标记），落盘一律由本周期兜底 + 登出（unloadWallet）
     * + 停服（saveAll）三重承担；崩溃场景最长丢账窗口为一个周期（5 分钟）。
     * 团队钱包由 GTNHLib team.markDirty() 托管（余额冲刷时标记），无需此处处理。
     *
     * @return 本次落盘的钱包数
     */
    public int saveDirtyWallets() {
        if (dirtyPersonalWallets.isEmpty()) return 0;
        List<UUID> dirty = new ArrayList<>(dirtyPersonalWallets);
        int saved = 0;
        for (UUID playerId : dirty) {
            if (personalWallets.containsKey(playerId)) {
                saveWallet(playerId);
                saved++;
            } else {
                // 钱包已卸载（saveWallet 内部已落盘或不存在），清掉孤儿标记
                dirtyPersonalWallets.remove(playerId);
            }
        }
        if (saved > 0) {
            GTInterestingThing.LOG.info("[NekoWallet] 周期落盘脏钱包: {} 个", saved);
        }
        return saved;
    }

    /**
     * 保存所有个人钱包（服务器关闭时由生命周期钩子调用）
     * 团队钱包由 GTNHLib Teams 自动管理
     */
    public void saveAll() {
        for (UUID playerId : new ArrayList<>(personalWallets.keySet())) {
            saveWallet(playerId);
        }
    }

    /**
     * 服务器停止收尾：全量落盘并清空内存缓存（FMLServerStoppedEvent 调用）。
     * <p>
     * 清空缓存防止单机连续切换世界时，旧世界的钱包对象驻留内存遮蔽新世界的
     * .dat 文件（个人钱包仅在不命中缓存时才从磁盘加载）。
     */
    public void unloadAll() {
        saveAll();
        personalWallets.clear();
        dirtyPersonalWallets.clear();
        dirtyBalanceKeys.clear();
    }
}
