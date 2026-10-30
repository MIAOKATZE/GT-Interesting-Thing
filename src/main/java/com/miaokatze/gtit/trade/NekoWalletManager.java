package com.miaokatze.gtit.trade;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.miaokatze.gtit.util.AtomicNbtFile;
import com.miaokatze.gtit.util.PlayerLookup;

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

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    public static final NekoWalletManager INSTANCE = new NekoWalletManager();

    private final Map<UUID, NekoWallet> personalWallets = new ConcurrentHashMap<>();
    /** Bound directory remains attached to pending old-world balances after a failed shutdown save. */
    private File saveDir = null;
    private volatile boolean acceptingWallets;

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
        bindSaveDirectory(
            new File(
                world.getSaveHandler()
                    .getWorldDirectory(),
                "gtit_neko_wallets"));
    }

    /** World-session boundary. A failed old-world save must never become a new-world ledger. */
    private synchronized void bindSaveDirectory(File directory) {
        acceptingWallets = false;
        try {
            File target = directory.getCanonicalFile();
            boolean saved = true;
            for (UUID playerId : new ArrayList<>(personalWallets.keySet())) {
                saved &= savePersonalWallet(playerId); // Always uses the OLD bound directory.
            }
            if (!saved || !dirtyPersonalWallets.isEmpty()) {
                throw new IllegalStateException("Previous world's wallets remain unsaved: " + saveDir);
            }
            Files.createDirectories(target.toPath());
            personalWallets.clear();
            dirtyPersonalWallets.clear();
            dirtyBalanceKeys.clear();
            lastBalanceFlushMs = 0L;
            saveDir = target;
            acceptingWallets = true;
            LOG.info("猫猫币钱包存储目录: {}", saveDir.getAbsolutePath());
        } catch (Exception failure) {
            // CommonProxy may continue after init failure. Every lookup must still fail closed.
            throw new IllegalStateException("Wallet storage is not ready for this world", failure);
        }
    }

    /**
     * 获取玩家的钱包
     * 优先返回团队共享钱包，如果团队不可用则回退到个人钱包
     * <p>
     * <b>团队切换语义（B2-06 文档化决策，与 O2-18(a) 同一结论）</b>：玩家入队后本方法
     * 即路由到团队钱包，个人钱包余额在入队时刻冻结（不再读写）；退队后回到冻结前快照。
     * 个人 .dat 文件不做删除，保留作退队恢复点——这是有意行为而非泄漏。
     */
    public synchronized NekoWallet getWallet(UUID playerId) {
        if (!acceptingWallets) throw new IllegalStateException("Wallet storage is not ready for this world");
        if (playerId == null) return null;

        // Resolve the team once: a concurrent membership change must not relabel another team's wallet.
        Team team = TeamDataProvider.getTeam(playerId);
        NekoTeamData teamData = TeamDataProvider.getData(team);
        if (teamData != null) {
            NekoWallet wallet = teamData.getWallet();
            wallet.setTeamId(team.getTeamId());
            return wallet;
        }
        return personalWallets.computeIfAbsent(playerId, id -> {
            NekoWallet wallet = loadWallet(id);
            if (wallet == null) wallet = new NekoWallet();
            wallet.setPlayerId(id);
            return wallet;
        });
    }

    /**
     * 保存玩家钱包
     * 如果是团队钱包，标记团队数据为脏；如果是个人钱包，保存到磁盘
     * <p>
     * Team routing does not discard the frozen personal balance: save that cache too when present.
     * Personal .dat files remain the restore point after leaving the team; balances are not merged.
     */
    public synchronized void saveWallet(UUID playerId) {
        if (playerId == null) return;
        if (acceptingWallets) {
            Team team = TeamDataProvider.getTeam(playerId);
            if (TeamDataProvider.getData(team) != null) TeamDataProvider.markTeamDirty(team);
        }
        // A frozen personal wallet still owns money. Joining a team must not discard its pending save.
        savePersonalWallet(playerId);
    }

    private boolean savePersonalWallet(UUID playerId) {
        NekoWallet wallet = personalWallets.get(playerId);
        if (wallet == null) return true;
        if (saveDir == null) return false;
        File file = new File(saveDir, playerId.toString() + ".dat");
        synchronized (wallet) {
            try {
                NBTTagCompound nbt = new NBTTagCompound();
                nbt.setTag("wallet", wallet.writeToNBT());
                AtomicNbtFile.write(nbt, file);
                // The mutation notifier takes the same wallet lock. Never clear a newer dirty mark.
                dirtyPersonalWallets.remove(playerId);
                return true;
            } catch (Exception e) {
                dirtyPersonalWallets.add(playerId);
                LOG.error("保存猫猫币钱包失败: " + playerId, e);
                return false;
            }
        }
    }

    /**
     * 从磁盘加载玩家个人钱包
     */
    private NekoWallet loadWallet(UUID playerId) {
        if (saveDir == null) return null;
        File file = new File(saveDir, playerId.toString() + ".dat");
        // A permissions/I/O failure is not evidence that a wallet does not exist.
        if (Files.notExists(file.toPath())) return null;
        try {
            NBTTagCompound nbt = CompressedStreamTools.read(file);
            if (nbt == null || !nbt.hasKey("wallet", 10)) {
                throw new IllegalArgumentException("Missing wallet compound");
            }
            NekoWallet wallet = new NekoWallet();
            wallet.readFromNBT(nbt.getCompoundTag("wallet"));
            return wallet;
        } catch (Exception e) {
            LOG.error("加载猫猫币钱包失败，保留原存档并拒绝覆盖: " + playerId, e);
            // Creating a fresh zero wallet here would overwrite the only recoverable copy on logout.
            throw new IllegalStateException("Cannot load wallet " + playerId, e);
        }
    }

    // ==================== 钱包余额变化通知（脏标记 + 节流冲刷） ====================

    /**
     * 钱包余额变化时登记待推送/待落盘标记（不再直接推送全量同步包）。
     * <p>
     * 由 {@link NekoWallet} 在余额实际变化后调用。个人钱包（teamId 为 null）登记
     * "player:&lt;uuid&gt;" 键并标记钱包脏（周期/登出/停服兜底落盘）；团队钱包登记
     * "team:&lt;uuid&gt;" 键。实际推送由 {@link #flushBalanceNotifications()} 在服务器
     * tick 上以约 100ms 间隔冲刷——只发余额轻量包
     * （{@link WalletNetworkManager#sendBalanceToClient}），不发卡池摘要/保底全量包，
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
        if (!acceptingWallets || dirtyBalanceKeys.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastBalanceFlushMs < BALANCE_FLUSH_INTERVAL_MS) return;
        lastBalanceFlushMs = now;

        // 取出本批待推送键（O2-21：迭代器单趟直取，替代"复制快照 + removeAll 双扫"；
        // ConcurrentHashMap 弱一致迭代器下语义等价——冲刷期间新到的键留给下一批）
        List<String> keys = new ArrayList<>(dirtyBalanceKeys.size());
        Iterator<String> keyIterator = dirtyBalanceKeys.iterator();
        while (keyIterator.hasNext()) {
            keys.add(keyIterator.next());
            keyIterator.remove();
        }

        // 网络未初始化（理论上不会发生：通道在 init 阶段注册）时只处理团队脏标记
        boolean networkReady = WalletNetworkManager.isInitialized();
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
                LOG.error("推送钱包余额失败: " + key, t);
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
                    WalletNetworkManager.sendBalanceToClient(player, balances);
                }
            }
        } catch (Throwable t) {
            LOG.error("推送团队钱包余额失败: " + teamId, t);
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
        // Frozen personal wallets must not overwrite the active team balance on the client.
        if (wallet == null || TeamDataProvider.getData(TeamDataProvider.getTeam(playerId)) != null) return;
        EntityPlayerMP player = onlineByUuid.get(playerId);
        if (player == null) return;
        WalletNetworkManager.sendBalanceToClient(player, snapshotBalances(wallet));
    }

    /** 钱包余额快照（currencyId → 数量；在钱包锁内取一致快照） */
    static Map<String, Integer> snapshotBalances(NekoWallet wallet) {
        Map<String, Integer> balances = new HashMap<>();
        // copy() follows aliases while holding the actual survivor's lock for the whole ledger copy.
        NekoWallet snapshot = wallet.copy();
        for (String currencyId : snapshot.getCurrencyIds()) {
            if (currencyId != null) balances.put(currencyId, snapshot.getCount(currencyId));
        }
        return balances;
    }

    /** 单趟扫描在线玩家表构建 UUID→player 缓存（本批冲刷内复用；O2-12 起委托 PlayerLookup） */
    private static Map<UUID, EntityPlayerMP> buildOnlinePlayerCache() {
        Map<UUID, EntityPlayerMP> cache = new HashMap<>();
        PlayerLookup.forEachOnlinePlayer(player -> {
            if (player.getUniqueID() != null) {
                cache.put(player.getUniqueID(), player);
            }
        });
        return cache;
    }

    // ==================== 生命周期 ====================

    /**
     * Save on logout, retaining the canonical ledger until world stop. Outstanding operations may
     * still hold this object for refunds; eviction could recreate a second ledger and orphan money.
     * The cache retains UUIDs and balances only (no player/world references), at one entry per participant.
     */
    public void unloadWallet(UUID playerId) {
        saveWallet(playerId);
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
    public synchronized int saveDirtyWallets() {
        if (dirtyPersonalWallets.isEmpty()) return 0;
        List<UUID> dirty = new ArrayList<>(dirtyPersonalWallets);
        int saved = 0;
        for (UUID playerId : dirty) {
            if (personalWallets.containsKey(playerId)) {
                if (savePersonalWallet(playerId)) saved++;
            } else {
                // 钱包已卸载（saveWallet 内部已落盘或不存在），清掉孤儿标记
                dirtyPersonalWallets.remove(playerId);
            }
        }
        if (saved > 0) {
            LOG.info("[NekoWallet] 周期落盘脏钱包: {} 个", saved);
        }
        return saved;
    }

    /**
     * 保存所有个人钱包（服务器关闭时由生命周期钩子调用）
     * 团队钱包由 GTNHLib Teams 自动管理
     */
    public synchronized void saveAll() {
        for (UUID playerId : new ArrayList<>(personalWallets.keySet())) {
            savePersonalWallet(playerId);
        }
        // A failed old-world transition may retain only personal ledgers; do not route old team keys
        // through the new world's provider while storage is unavailable.
        if (!acceptingWallets) return;
        // Server stopping may happen inside the notification throttle window.
        for (String key : new ArrayList<>(dirtyBalanceKeys)) {
            if (key.startsWith(TEAM_KEY_PREFIX)) {
                Team team = TeamDataProvider.getTeamById(UUID.fromString(key.substring(TEAM_KEY_PREFIX.length())));
                TeamDataProvider.markTeamDirty(team);
            }
        }
    }

    /**
     * 服务器停止收尾：全量落盘并清空内存缓存（FMLServerStoppedEvent 调用）。
     * <p>
     * Successful saves clear the old session. Failed saves retain their OLD directory binding and
     * disable lookups; init must save that pending data before binding any subsequent world.
     */
    public synchronized void unloadAll() {
        saveAll();
        acceptingWallets = false;
        if (!dirtyPersonalWallets.isEmpty()) {
            LOG.error("服务器停止时仍有 {} 个猫猫币钱包未能保存；保留旧世界绑定并禁止新钱包访问", dirtyPersonalWallets.size());
            return;
        }
        personalWallets.clear();
        dirtyPersonalWallets.clear();
        dirtyBalanceKeys.clear();
        saveDir = null;
        lastBalanceFlushMs = 0L;
    }
}
