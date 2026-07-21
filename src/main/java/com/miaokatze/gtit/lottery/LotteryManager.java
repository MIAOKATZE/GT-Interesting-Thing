package com.miaokatze.gtit.lottery;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.signin.DailySignInManager;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

/**
 * 抽奖管理器单例（团队维度）
 * <p>
 * 管理卡池配置（{@link LotteryConfig}）、团队保底计数与抽奖历史：
 * <ul>
 * <li><b>团队键</b>：保底计数与历史以「团队 UUID」为键存储——
 * 与贸易团队钱包同源（{@link TeamManager#getTeamByPlayer(UUID)} → {@link Team#getTeamId()}）；
 * GTNHLib Teams 不可用或玩家无团队时回退玩家自身 UUID（单人模式天然成立）</li>
 * <li><b>扣费</b>：走 {@link NekoWalletManager#getWallet(UUID)}（内部优先团队钱包）
 * + {@link NekoWallet#tryDeduct(String, int)} 原子操作，与交易执行器一致</li>
 * <li><b>出货</b>：物品奖品弹入触发机器的出货槽（{@link MTENekoVendingMachineV2#dispenseItemStack}），
 * 溢出退给玩家背包（再满则掉落脚下）；货币奖品直接入团队钱包</li>
 * <li><b>保底</b>：按「团队 × 卡池」计数「连续未出高稀有（≥RARE）次数」，
 * 软保底加权重、硬保底强制替换（{@link PityConfig}）</li>
 * </ul>
 * 持久化：{@code <world>/gtit_lottery/<teamKey>.dat}（NBT，参照 DailySignInManager 模式）。
 */
public class LotteryManager {

    public static final LotteryManager INSTANCE = new LotteryManager();

    /** 卡池表（poolId → pool，loadConfig 时重建） */
    private final ConcurrentHashMap<String, LotteryPool> pools;
    /** 保底计数（teamKey → (poolId → 连续未出高稀有次数)） */
    private final ConcurrentHashMap<UUID, Map<String, Integer>> pityCounters;
    /** 抽奖历史（teamKey → history） */
    private final ConcurrentHashMap<UUID, LotteryHistory> histories;
    /** 存档目录（<world>/gtit_lottery） */
    private File saveDir;
    /** 权重随机数生成器 */
    private final Random random = new Random();

    private LotteryManager() {
        this.pools = new ConcurrentHashMap<>();
        this.pityCounters = new ConcurrentHashMap<>();
        this.histories = new ConcurrentHashMap<>();
    }

    /**
     * 初始化存储目录并加载卡池配置（CommonProxy.serverStarted 调用）
     */
    public void init(World world) {
        saveDir = new File(
            world.getSaveHandler()
                .getWorldDirectory(),
            "gtit_lottery");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        loadConfig();
        GTInterestingThing.LOG.info("抽奖数据存储目录: {}", saveDir.getAbsolutePath());
    }

    /**
     * 加载/重载卡池配置（{@link LotteryConfig} JSON → 内存 pools 表）
     */
    public void loadConfig() {
        LotteryConfig.LotteryConfigData data = LotteryConfig.load();
        pools.clear();
        if (data != null && data.pools != null) {
            for (LotteryPool pool : data.pools) {
                if (pool != null && pool.getId() != null && pool.validate()) {
                    pools.put(pool.getId(), pool);
                }
            }
        }
        GTInterestingThing.LOG.info("抽奖卡池已加载: {}", pools.keySet());
    }

    // ==================== 团队键解析 ====================

    /**
     * 解析玩家的团队键（保底/历史存储键）
     * <p>
     * 与贸易团队钱包同源：{@code TeamManager.getTeamByPlayer(playerId).getTeamId()}。
     * GTNHLib Teams 不可用或玩家无团队时回退玩家自身 UUID。
     *
     * @param playerId 玩家 UUID
     * @return 团队 UUID（或回退的玩家 UUID）
     */
    public static UUID resolveTeamKey(UUID playerId) {
        if (playerId == null) return null;
        try {
            Team team = TeamManager.getTeamByPlayer(playerId);
            if (team != null) {
                return team.getTeamId();
            }
        } catch (NoClassDefFoundError e) {
            // GTNHLib Teams API 不可用，回退个人维度
        } catch (Exception e) {
            GTInterestingThing.LOG.error("解析抽奖团队键失败: " + playerId, e);
        }
        return playerId;
    }

    // ==================== 抽奖核心 ====================

    /**
     * 执行抽奖（服务端权威）
     * <p>
     * 流程：校验卡池与机器 → 原子扣费（cost × count，团队钱包）→ 逐抽
     * （软保底加权 + 硬保底强制替换）→ 出货（机器出货槽/钱包）→ 记历史 → 落盘。
     * <p>
     * <b>原子性</b>：扣费一次性完成（count 连抽总价），任一抽出货失败不回滚已抽结果
     * （与交易 OUTPUT_FULL 回滚不同——抽奖出货溢出已退玩家背包，无丢失路径）。
     *
     * @param playerId 抽取玩家
     * @param poolId   卡池 ID
     * @param count    连抽次数（1 或 10）
     * @param machine  触发的售货机（物品奖品出货槽；为 null 时物品直接给玩家）
     * @return 抽取结果列表（size == count）；失败返回空列表
     */
    public List<LotteryDrawResult> drawLottery(UUID playerId, String poolId, int count,
        MTENekoVendingMachineV2 machine) {
        List<LotteryDrawResult> results = new ArrayList<>();
        if (playerId == null || count <= 0) return results;

        LotteryPool pool = pools.get(poolId);
        if (pool == null || !pool.validate()) {
            GTInterestingThing.LOG.warn("抽奖失败：卡池 {} 不存在或无有效条目", poolId);
            return results;
        }

        // 1. 原子扣费（团队钱包，与交易执行一致）
        int totalCost = pool.getCostPerDraw() * count;
        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
        if (wallet == null || !wallet.tryDeduct(pool.getNekoCurrencyId(), totalCost)) {
            return results; // 余额不足：空结果，由调用方提示
        }
        NekoWalletManager.INSTANCE.saveWallet(playerId);

        // 2. 逐抽（保底计数按「团队 × 卡池」维度推进）
        UUID teamKey = resolveTeamKey(playerId);
        for (int i = 0; i < count; i++) {
            LotteryDrawResult result = drawSingle(teamKey, pool);
            if (result == null) continue;
            results.add(result);

            // 3. 出货
            dispatchPrize(playerId, result.getEntry(), result.getAmount(), machine);
            // 4. 记历史
            recordHistory(teamKey, pool.getId(), result, getPlayerName(playerId));
        }

        // 5. 落盘保底与历史
        saveTeamData(teamKey);
        return results;
    }

    /**
     * 单抽（含保底判定，推进团队保底计数）
     * <p>
     * 判定顺序：硬保底触发 → 直接取保底条目（强制替换）；否则按权重随机（软保底对高稀有加权）。
     * 出货稀有度 ≥ RARE 时保底计数清零，否则 +1。
     *
     * @param teamKey 团队键
     * @param pool    卡池
     * @return 抽取结果；池为空返回 null
     */
    public LotteryDrawResult drawSingle(UUID teamKey, LotteryPool pool) {
        List<LotteryEntry> entries = pool.getEntries();
        if (entries.isEmpty()) return null;

        PityConfig pity = pool.getPityConfig();
        int currentCount = getPityCounter(teamKey, pool.getId());

        LotteryEntry selected;
        boolean isPity = false;
        if (pity.isHardPityTriggered(currentCount)) {
            // 硬保底：强制取「保底稀有度及以上」条目
            selected = pool.getPityPrizeEntry();
            isPity = true;
        } else {
            // 常规权重随机（软保底对 ≥RARE 条目加权）
            double bonus = pity.getSoftPityBonus(currentCount);
            selected = selectByWeight(entries, bonus);
        }
        if (selected == null) return null;

        int slotIndex = entries.indexOf(selected);
        int amount = selected.randomAmount();
        boolean highRarity = selected.getRarity()
            .isAtLeast(LotteryRarity.RARE);

        // 推进保底计数（本抽之后）
        setPityCounter(teamKey, pool.getId(), highRarity ? 0 : currentCount + 1);

        return new LotteryDrawResult(selected, isPity, highRarity, slotIndex, amount);
    }

    /**
     * 按权重随机选取条目（软保底倍率仅作用于 ≥RARE 条目）
     *
     * @param entries   条目列表
     * @param pityBonus 软保底权重倍率（1.0 = 无加成）
     * @return 选中条目；列表为空或总权重为 0 返回 null
     */
    public LotteryEntry selectByWeight(List<LotteryEntry> entries, double pityBonus) {
        if (entries == null || entries.isEmpty()) return null;
        double totalWeight = 0;
        for (LotteryEntry entry : entries) {
            if (entry == null || entry.getWeight() <= 0) continue;
            totalWeight += effectiveWeight(entry, pityBonus);
        }
        if (totalWeight <= 0) return null;

        double roll = random.nextDouble() * totalWeight;
        for (LotteryEntry entry : entries) {
            if (entry == null || entry.getWeight() <= 0) continue;
            roll -= effectiveWeight(entry, pityBonus);
            if (roll < 0) return entry;
        }
        // 浮点误差兜底：返回最后一个有效条目
        for (int i = entries.size() - 1; i >= 0; i--) {
            LotteryEntry entry = entries.get(i);
            if (entry != null && entry.getWeight() > 0) return entry;
        }
        return null;
    }

    /** 条目有效权重（软保底仅加成高稀有度条目） */
    private double effectiveWeight(LotteryEntry entry, double pityBonus) {
        double weight = entry.getWeight();
        if (pityBonus > 1.0 && entry.getRarity()
            .isAtLeast(LotteryRarity.RARE)) {
            weight *= pityBonus;
        }
        return weight;
    }

    // ==================== 出货 ====================

    /**
     * 发放奖品
     * <ul>
     * <li>货币奖品：直接入团队钱包（{@link NekoWalletManager} 内部路由）</li>
     * <li>物品奖品：弹入触发机器出货槽（掉落动画）→ 溢出退玩家背包 → 再满掉落脚下</li>
     * </ul>
     *
     * @param playerId 抽取玩家
     * @param entry    中奖条目
     * @param amount   出货数量
     * @param machine  触发机器（可为 null，此时物品直接给玩家）
     */
    public void dispatchPrize(UUID playerId, LotteryEntry entry, int amount, MTENekoVendingMachineV2 machine) {
        if (entry == null || amount <= 0) return;

        // 货币奖品：入团队钱包
        if (entry.isNekoPrize()) {
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet != null) {
                wallet.addCount(entry.getNekoCurrencyId(), amount);
                NekoWalletManager.INSTANCE.saveWallet(playerId);
            }
            return;
        }

        // 物品奖品：优先机器出货槽
        ItemStack stack = entry.toItemStack(amount);
        if (stack == null) {
            GTInterestingThing.LOG.warn("抽奖奖品物品无法构建: {}", entry.getItem());
            return;
        }
        ItemStack overflow = null;
        if (machine != null) {
            overflow = machine.dispenseItemStack(stack);
        } else {
            overflow = stack;
        }

        // 溢出处理：退玩家背包，背包满则掉落脚下（参照签到阶梯物品奖励）
        if (overflow != null && overflow.stackSize > 0) {
            EntityPlayerMP player = DailySignInManager.getPlayerByUUID(playerId);
            if (player != null) {
                if (!player.inventory.addItemStackToInventory(overflow)) {
                    EntityItem drop = new EntityItem(player.worldObj, player.posX, player.posY, player.posZ, overflow);
                    player.worldObj.spawnEntityInWorld(drop);
                }
            } else if (machine != null) {
                // 玩家离线（理论不会发生：抽奖请求来自在线玩家）：掉落机器旁
                GTInterestingThing.LOG.warn("抽奖出货溢出但玩家离线，物品已丢弃: {}", overflow);
            }
        }
    }

    // ==================== 历史与保底计数 ====================

    /**
     * 记录一条抽奖历史（团队共享）
     */
    public void recordHistory(UUID teamKey, String poolId, LotteryDrawResult result, String playerName) {
        if (teamKey == null || result == null || result.getEntry() == null) return;
        LotteryHistory history = getHistory(teamKey);
        LotteryEntry entry = result.getEntry();
        history.addRecord(
            new LotteryHistory.HistoryEntry(
                poolId,
                entry.getId(),
                entry.getRarity()
                    .name(),
                result.getAmount(),
                playerName,
                System.currentTimeMillis()));
    }

    /**
     * 查询团队某池的保底计数（连续未出高稀有次数）
     */
    public int getPityCounter(UUID teamKey, String poolId) {
        if (teamKey == null || poolId == null) return 0;
        Map<String, Integer> poolCounters = pityCounters.get(teamKey);
        if (poolCounters == null) return 0;
        return poolCounters.getOrDefault(poolId, 0);
    }

    /**
     * 查询团队全部卡池的保底计数快照（poolId → 次数，同步包构建用）
     * <p>
     * 仅返回已加载卡池的计数（键不存在的池按 0 由客户端兜底）。
     *
     * @param teamKey 团队键
     * @return 保底计数快照（永不为 null）
     */
    public Map<String, Integer> getPityCounters(UUID teamKey) {
        Map<String, Integer> snapshot = new java.util.HashMap<>();
        if (teamKey == null) return snapshot;
        Map<String, Integer> poolCounters = pityCounters.get(teamKey);
        if (poolCounters != null) {
            snapshot.putAll(poolCounters);
        }
        return snapshot;
    }

    /** 设置团队某池的保底计数 */
    private void setPityCounter(UUID teamKey, String poolId, int value) {
        if (teamKey == null || poolId == null) return;
        pityCounters.computeIfAbsent(teamKey, k -> new ConcurrentHashMap<>())
            .put(poolId, value);
    }

    /**
     * 获取团队抽奖历史（懒加载：内存无则从磁盘读，均无则新建）
     */
    public LotteryHistory getHistory(UUID teamKey) {
        if (teamKey == null) return new LotteryHistory();
        LotteryHistory history = histories.get(teamKey);
        if (history == null) {
            loadTeamData(teamKey);
            history = histories.computeIfAbsent(teamKey, k -> new LotteryHistory());
        }
        return history;
    }

    // ==================== 持久化 ====================

    /**
     * 保存指定团队的保底计数与历史到磁盘
     */
    public void saveTeamData(UUID teamKey) {
        if (teamKey == null || saveDir == null) return;
        File file = new File(saveDir, teamKey.toString() + ".dat");
        try {
            NBTTagCompound nbt = new NBTTagCompound();
            // 保底计数（poolId → count）
            Map<String, Integer> poolCounters = pityCounters.get(teamKey);
            if (poolCounters != null) {
                NBTTagCompound pityTag = new NBTTagCompound();
                for (Map.Entry<String, Integer> e : poolCounters.entrySet()) {
                    pityTag.setInteger(e.getKey(), e.getValue());
                }
                nbt.setTag("pity", pityTag);
            }
            // 历史
            LotteryHistory history = histories.get(teamKey);
            if (history != null) {
                nbt.setTag("history", history.writeToNBT());
            }
            CompressedStreamTools.safeWrite(nbt, file);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存抽奖数据失败: " + teamKey, e);
        }
    }

    /**
     * 从磁盘加载指定团队的保底计数与历史（文件不存在时保持内存现状）
     */
    public void loadTeamData(UUID teamKey) {
        if (teamKey == null || saveDir == null) return;
        File file = new File(saveDir, teamKey.toString() + ".dat");
        if (!file.exists()) return;
        try {
            NBTTagCompound nbt = CompressedStreamTools.read(file);
            if (nbt == null) return;
            // 保底计数
            if (nbt.hasKey("pity")) {
                NBTTagCompound pityTag = nbt.getCompoundTag("pity");
                Map<String, Integer> poolCounters = pityCounters
                    .computeIfAbsent(teamKey, k -> new ConcurrentHashMap<>());
                for (String key : pityTag.func_150296_c()) { // getKeySet 的 SRG 名
                    poolCounters.put(key, pityTag.getInteger(key));
                }
            }
            // 历史
            if (nbt.hasKey("history")) {
                LotteryHistory history = new LotteryHistory();
                history.readFromNBT(nbt.getCompoundTag("history"));
                histories.put(teamKey, history);
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("加载抽奖数据失败: " + teamKey, e);
        }
    }

    /**
     * 玩家登录：预加载其团队抽奖数据（含保底/历史），供同步与查询
     */
    public void loadPlayerTeamData(UUID playerId) {
        UUID teamKey = resolveTeamKey(playerId);
        if (teamKey != null) {
            loadTeamData(teamKey);
        }
    }

    /**
     * 玩家下线卸载（团队数据由团队共享，不随个人下线清除；
     * 此处仅落盘保底数据，历史常驻内存至服务器关闭——上限 200 条，内存可控）
     */
    public void unloadPlayer(UUID playerId) {
        UUID teamKey = resolveTeamKey(playerId);
        if (teamKey != null) {
            saveTeamData(teamKey);
        }
    }

    /**
     * 保存所有已加载团队数据（周期保存/服务器关闭）
     */
    public void saveAll() {
        for (UUID teamKey : pityCounters.keySet()) {
            saveTeamData(teamKey);
        }
        for (UUID teamKey : histories.keySet()) {
            saveTeamData(teamKey);
        }
    }

    /**
     * 热重载卡池配置（预留指令入口，目标 4/5）
     */
    public void reload() {
        loadConfig();
    }

    // ==================== 查询 ====================

    /**
     * 获取卡池
     */
    public LotteryPool getPool(String poolId) {
        return pools.get(poolId);
    }

    /**
     * 获取全部卡池（顺序按 Map 迭代；GUI 展示用）
     */
    public List<LotteryPool> getAllPools() {
        return new ArrayList<>(pools.values());
    }

    /**
     * 离线模拟抽取（不扣费/不出货/不动保底计数，调试与概率验证用）
     */
    public List<LotteryDrawResult> testDraw(String poolId, int count) {
        List<LotteryDrawResult> results = new ArrayList<>();
        LotteryPool pool = pools.get(poolId);
        if (pool == null) return results;
        for (int i = 0; i < count; i++) {
            LotteryEntry entry = selectByWeight(pool.getEntries(), 1.0);
            if (entry != null) {
                int idx = pool.getEntries()
                    .indexOf(entry);
                results.add(
                    new LotteryDrawResult(
                        entry,
                        false,
                        entry.getRarity()
                            .isAtLeast(LotteryRarity.RARE),
                        idx,
                        entry.randomAmount()));
            }
        }
        return results;
    }

    // ==================== 内部辅助 ====================

    /** 取玩家名（在线取真名，离线退 UUID 前 8 位） */
    private String getPlayerName(UUID playerId) {
        EntityPlayerMP player = DailySignInManager.getPlayerByUUID(playerId);
        if (player != null) {
            return player.getCommandSenderName();
        }
        return playerId == null ? "?"
            : playerId.toString()
                .substring(0, 8);
    }
}
