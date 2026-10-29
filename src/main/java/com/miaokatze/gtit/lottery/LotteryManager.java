package com.miaokatze.gtit.lottery;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.common.machine.v2.NekoOutputCapacity;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.signin.DailySignInManager;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.TeamDataProvider;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoTradeExecutor;
import com.miaokatze.gtit.util.RewardNbt;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 抽奖管理器单例（团队维度）
 * <p>
 * 管理卡池配置（{@link LotteryConfig}）与团队保底计数：
 * <ul>
 * <li><b>团队键</b>：保底计数以「团队 UUID」为键存储——
 * 与贸易团队钱包同源（{@code TeamDataProvider.getTeam(UUID)} → {@code getTeamId()}，O2-04 门面统一）；
 * GTNHLib Teams 不可用或玩家无团队时回退玩家自身 UUID（单人模式天然成立）</li>
 * <li><b>扣费</b>：走 {@link NekoWalletManager#getWallet(UUID)}（内部优先团队钱包）
 * + {@link NekoWallet#tryDeduct(String, int)} 原子操作，与交易执行器一致</li>
 * <li><b>出货</b>：物品奖品弹入触发机器的出货槽（{@link MTENekoVendingMachineV2#dispenseItemStack}），
 * 扣费前预留完整容量，已付款奖品立即进入持久化队列，动画结束后落槽；
 * 货币奖品同样构建为实物，注册无效或容量不足时拒绝抽取，不退化成背包/钱包发奖</li>
 * <li><b>保底</b>：按「团队 × 卡池」计数「连续未出高稀有（≥RARE）次数」，
 * 软保底加权重、硬保底强制替换（{@link PityConfig}）</li>
 * </ul>
 * 持久化：{@code <world>/gtit_lottery/<teamKey>.dat}（NBT，参照 DailySignInManager 模式）。
 */
public class LotteryManager {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    public static final LotteryManager INSTANCE = new LotteryManager();

    /** 卡池表（poolId → pool，loadConfig 时重建） */
    private final ConcurrentHashMap<String, LotteryPool> pools;
    /** 保底计数（teamKey → (poolId → 连续未出高稀有次数)） */
    private final ConcurrentHashMap<UUID, Map<String, Integer>> pityCounters;
    private final java.util.Set<UUID> unreadableTeams = ConcurrentHashMap.newKeySet();
    /** 存档目录（<world>/gtit_lottery） */
    private File saveDir;
    private boolean sessionUnavailable = true;
    /** 权重随机数生成器 */
    private final Random random = new Random();

    private LotteryManager() {
        this.pools = new ConcurrentHashMap<>();
        this.pityCounters = new ConcurrentHashMap<>();
    }

    /**
     * 初始化存储目录并加载卡池配置（CommonProxy.serverStarted 调用）
     */
    public void init(World world) {
        sessionUnavailable = true;
        // Team IDs can repeat across integrated-server worlds; never reuse the previous world's pity.
        saveAll();
        pityCounters.clear();
        unreadableTeams.clear();
        saveDir = new File(
            world.getSaveHandler()
                .getWorldDirectory(),
            "gtit_lottery");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        loadConfig();
        sessionUnavailable = false;
        LOG.info("抽奖数据存储目录: {}", saveDir.getAbsolutePath());
    }

    /**
     * 加载/重载卡池配置（{@link LotteryConfig} JSON → 内存 pools 表）
     */
    public void loadConfig() {
        LotteryConfig.LotteryConfigData data = LotteryConfig.load();
        pools.clear();
        if (data != null && data.pools != null) {
            for (LotteryPool pool : data.pools) {
                if (pool != null && pool.getId() != null
                    && !pool.getId()
                        .isEmpty()
                    && !pool.getEntries()
                        .isEmpty()) {
                    pools.put(pool.getId(), pool);
                }
            }
        }
        LOG.info("抽奖卡池已加载: {}", pools.keySet());
    }

    // ==================== 团队键解析 ====================

    /**
     * 解析玩家的团队键（保底计数存储键）
     * <p>
     * 与贸易团队钱包同源：{@code TeamDataProvider.getTeam(playerId).getTeamId()}（O2-04 门面统一）。
     * GTNHLib Teams 不可用或玩家无团队时回退玩家自身 UUID。
     *
     * @param playerId 玩家 UUID
     * @return 团队 UUID（或回退的玩家 UUID）
     */
    public static UUID resolveTeamKey(UUID playerId) {
        return TeamDataProvider.resolveTeamKey(playerId);
    }

    // ==================== 抽奖核心 ====================

    /**
     * 执行抽奖（服务端权威）
     * <p>
     * 流程：校验卡池与机器 → 预留最大可能出货容量 → 校验余额/材料 → 逐抽并验证奖品
     * （软保底加权 + 硬保底强制替换）→ 扣费分流（{@link #deductCostItems}）→
     * 立即提交已付款队列 → 落盘保底。拒绝扣费或无效奖品恢复原保底；轮盘动画只延迟落槽。
     * <p>
     * <b>原子性</b>：扣费前按池内最大合法奖品预留全部槽位；空间不足不扣费、不推进保底。
     * 奖品在同一服务端调用中进入既有持久化队列，不存在延迟任务持有物品的丢失窗口。
     * <p>
     * <b>延迟出货</b>（v1.7.8 起）：奖品先存入机器，结果包驱动客户端轮盘动画；
     * 出货延迟「动画时长 + 150ms」由机器已付款队列元数据执行，
     * 产物落输出槽时客户端 {@code NekoFallingItemSlotFactory} 自然播下落动画，
     * 结果/余额立即同步，动画结束后另补一次同步；延迟任务不持有奖品。
     *
     * @param playerId 抽取玩家
     * @param poolId   卡池 ID
     * @param count    连抽次数（1 或 10）
     * @param machine  触发的售货机（物品奖品出货槽 + 物品消耗来源；为 null 时拒绝）
     * @return 抽取结果列表（size == count）；失败返回空列表
     */
    public List<LotteryDrawResult> drawLottery(UUID playerId, String poolId, int count,
        MTENekoVendingMachineV2 machine) {
        List<LotteryDrawResult> results = new ArrayList<>();
        if (playerId == null || count <= 0) return results;

        LotteryPool pool = pools.get(poolId);
        if (pool == null || !pool.validate()) {
            LOG.warn("抽奖失败：卡池 {} 不存在或无有效条目", poolId);
            return results;
        }

        if (machine == null) return results;
        LotteryDeliveryPlan delivery = LotteryDeliveryPlan.prepare(pool.getEntries(), count);
        if (delivery == null) return results;
        // Reserve the largest possible result before charging OR advancing RNG/pity. Reservations live only
        // during this server-thread call; committed prizes immediately belong to the persistent machine queue.
        try (NekoOutputCapacity.Reservation reservation = machine.reserveOutputCapacity(delivery.reservedSlots())) {
            if (reservation == null || !canAfford(playerId, pool, count, machine)) return results;
            UUID teamKey = resolveTeamKey(playerId);
            int previousPity = getPityCounter(teamKey, poolId);
            List<ItemStack> prizes = new ArrayList<>();
            List<Boolean> rearRoutes = new ArrayList<>();
            // Materialize under the reservation before payment. Bad configuration cannot charge for a
            // missing reward; the tentative pity progression is restored on every pre-commit rejection.
            try {
                for (int i = 0; i < count; i++) {
                    LotteryDrawResult result = drawSingle(teamKey, pool);
                    for (ItemStack stack : delivery.splitPrize(result)) {
                        prizes.add(stack);
                        rearRoutes.add(
                            result.getEntry()
                                .getRarity() == LotteryRarity.LEGENDARY);
                    }
                    results.add(result);
                }
                if (!reservation.canCommit(prizes.size()))
                    throw new IllegalArgumentException("actual reward stack limit exceeds reserved capacity");
            } catch (RuntimeException invalidPrize) {
                setPityCounter(teamKey, poolId, previousPity);
                LOG.error("抽奖奖品校验失败，未扣费: " + poolId, invalidPrize);
                results.clear();
                return results;
            }
            if (!deductCostItems(playerId, pool, count, machine)) {
                setPityCounter(teamKey, poolId, previousPity);
                results.clear();
                return results;
            }
            long animationMs = results.size() > 1 ? LotteryAnimationController.DURATION_QUICK_MS
                : LotteryAnimationController.DURATION_SINGLE_MS;
            machine.commitLotteryOutputs(reservation, prizes, rearRoutes, animationMs + DISPATCH_DELAY_MARGIN_MS);
            saveTeamData(teamKey);
            scheduleDelayedSync(playerId, animationMs + DISPATCH_DELAY_MARGIN_MS);
        }
        return results;
    }

    /** The UI uses the same conservative capacity/configuration check as the charging transaction. */
    public boolean canQueueRewards(LotteryPool pool, int count, MTENekoVendingMachineV2 machine) {
        if (pool == null || machine == null) return false;
        LotteryDeliveryPlan delivery = LotteryDeliveryPlan.prepare(pool.getEntries(), count);
        return delivery != null && delivery.reservedSlots() <= machine.getAvailableOutputCapacity();
    }

    // ==================== 扣费分流（v1.7.6） ====================

    /**
     * 汇总卡池 count 连抽的全部需求（货币按币种聚合、物品按条目 ×count）
     *
     * @param pool          卡池
     * @param count         连抽次数
     * @param currencyNeeds 输出：货币需求（currencyId → 总量）
     * @param itemNeeds     输出：物品需求（NekoBigItemStack 列表）
     */
    private static void collectCostNeeds(LotteryPool pool, int count, Map<String, Integer> currencyNeeds,
        List<NekoBigItemStack> itemNeeds) {
        if (!pool.hasValidCosts()) throw new IllegalArgumentException("Invalid lottery payment: " + pool.getId());
        for (NekoBigItemStack cost : pool.getCostItems()) {
            int total = Math.multiplyExact(cost.getStackSize(), Math.max(1, count));
            String cid = NekoCurrencyRegistrar.getNekoCurrencyId(cost.getBaseStack());
            if (cid != null) {
                // 猫猫币条目：按币种聚合（同币种多条目合并扣款）
                currencyNeeds.merge(cid, total, Math::addExact);
            } else {
                // 普通物品条目：从机器输入槽扣除
                itemNeeds.add(
                    new NekoBigItemStack(
                        total,
                        cost.getOreDict(),
                        cost.getBaseStack()
                            .copy()));
            }
        }
    }

    /**
     * 消耗预校验（不实际扣减）
     * <p>
     * 货币条目校验团队钱包余额；物品条目在机器输入槽副本上模拟扣除。
     * 抽奖请求包（{@link LotteryRequestPacket}）据此给出明确的失败提示。
     *
     * @param playerId 抽取玩家
     * @param pool     卡池
     * @param count    连抽次数
     * @param machine  触发机器（含物品消耗时必需）
     * @return true 表示全部需求可满足
     */
    public boolean canAfford(UUID playerId, LotteryPool pool, int count, MTENekoVendingMachineV2 machine) {
        requireSession();
        if (playerId == null || pool == null || count <= 0) return false;
        Map<String, Integer> currencyNeeds = new java.util.LinkedHashMap<>();
        List<NekoBigItemStack> itemNeeds = new ArrayList<>();
        try {
            collectCostNeeds(pool, count, currencyNeeds, itemNeeds);
        } catch (ArithmeticException | IllegalArgumentException e) {
            LOG.warn("抽奖消耗配置无效或数量溢出，拒绝扣费: {}", pool.getId());
            return false;
        }

        // 货币余额校验
        if (!currencyNeeds.isEmpty()) {
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null) return false;
            for (Map.Entry<String, Integer> e : currencyNeeds.entrySet()) {
                if (wallet.getCount(e.getKey()) < e.getValue()) return false;
            }
        }
        // 物品模拟扣除（副本，不影响实际槽位）
        if (!itemNeeds.isEmpty()) {
            if (machine == null) return false;
            ItemStack[] inputs = machine.createInputSlotAccessor()
                .getCopyOfInputs();
            if (!NekoTradeExecutor.INSTANCE.removeItems(inputs, itemNeeds)
                .isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 扣费分流（全量校验 → 原子扣减）
     * <p>
     * ① 汇总 count 连抽需求；② 货币条目校验钱包余额、物品条目在输入槽副本模拟扣除，
     * 全部满足后；③ 钱包逐币种扣款 + 输入槽写回。
     * 钱包扣款在服务端主线程执行（与交易执行器一致），已校验余额故 tryDeduct 不会失败；
     * 防御性回滚：极端并发下部分扣款失败时还原已扣币种。
     *
     * @param playerId 抽取玩家
     * @param pool     卡池
     * @param count    连抽次数
     * @param machine  触发机器（含物品消耗时必需）
     * @return true 表示扣费成功
     */
    private boolean deductCostItems(UUID playerId, LotteryPool pool, int count, MTENekoVendingMachineV2 machine) {
        Map<String, Integer> currencyNeeds = new java.util.LinkedHashMap<>();
        List<NekoBigItemStack> itemNeeds = new ArrayList<>();
        try {
            collectCostNeeds(pool, count, currencyNeeds, itemNeeds);
        } catch (ArithmeticException | IllegalArgumentException e) {
            LOG.warn("抽奖消耗配置无效或数量溢出，拒绝扣费: {}", pool.getId());
            return false;
        }
        // 免费池（无需求条目）：直接放行
        if (currencyNeeds.isEmpty() && itemNeeds.isEmpty()) return true;

        // ---- 全量校验 ----
        NekoWallet wallet = null;
        if (!currencyNeeds.isEmpty()) {
            wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet == null) return false;
            for (Map.Entry<String, Integer> e : currencyNeeds.entrySet()) {
                if (wallet.getCount(e.getKey()) < e.getValue()) return false;
            }
        }
        NekoTradeExecutor.InputSlotAccessor inputAccessor = null;
        ItemStack[] newInputs = null;
        if (!itemNeeds.isEmpty()) {
            if (machine == null) return false;
            inputAccessor = machine.createInputSlotAccessor();
            newInputs = inputAccessor.getCopyOfInputs();
            if (!NekoTradeExecutor.INSTANCE.removeItems(newInputs, itemNeeds)
                .isEmpty()) {
                return false; // 输入槽物品不足（副本已被修改，未写回不影响实际槽位）
            }
        }

        // ---- 原子扣减 ----
        // O2-17：扣减走 tryDeduct/addCount（余额变化即登记脏标记），落盘统一由
        // 周期（5 分钟）+ 登出 + 停服三重兜底承担，不再显式 saveWallet
        if (wallet != null) {
            Map<String, Integer> deductedMap = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : currencyNeeds.entrySet()) {
                if (!wallet.tryDeduct(e.getKey(), e.getValue())) {
                    // 防御性回滚（理论不可达：主线程已校验余额）——还原全部已扣币种
                    for (Map.Entry<String, Integer> d : deductedMap.entrySet()) {
                        wallet.addCount(d.getKey(), d.getValue());
                    }
                    return false;
                }
                deductedMap.put(e.getKey(), e.getValue());
            }
        }
        if (inputAccessor != null) {
            inputAccessor.setInputs(newInputs);
        }
        return true;
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
            isPity = selected != null && selected.getRarity()
                .isAtLeast(pity.getGuaranteedRarity());
        } else {
            // 常规权重随机（软保底对 ≥RARE 条目加权）
            double bonus = pity.getSoftPityBonus(currentCount);
            selected = selectByWeight(entries, bonus);
        }
        if (selected == null) return null;

        int slotIndex = entries.indexOf(selected);
        int amount = selected.randomAmount();
        LotteryRarity guaranteedRarity = pity.getGuaranteedRarity();
        // v1.7.36：保底充值仅当抽到保底品质（guaranteedRarity，默认 EPIC）及以上才重置；
        // 此前的 RARE 判定导致「抽到非普通即重置」，早于设计预期。
        boolean highRarity = selected.getRarity()
            .isAtLeast(guaranteedRarity);

        // 推进保底计数（本抽之后）—— 抽到保底品质及以上则清零，否则 +1
        setPityCounter(teamKey, pool.getId(), highRarity ? 0 : (int) Math.min(Integer.MAX_VALUE, (long) currentCount + 1));

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
            if (entry == null || !entry.isDrawable()) continue;
            totalWeight += effectiveWeight(entry, pityBonus);
        }
        if (totalWeight <= 0) return null;

        double roll = random.nextDouble() * totalWeight;
        for (LotteryEntry entry : entries) {
            if (entry == null || !entry.isDrawable()) continue;
            roll -= effectiveWeight(entry, pityBonus);
            if (roll < 0) return entry;
        }
        // 浮点误差兜底：返回最后一个有效条目
        for (int i = entries.size() - 1; i >= 0; i--) {
            LotteryEntry entry = entries.get(i);
            if (entry != null && entry.isDrawable()) return entry;
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

    // ==================== 出货（v1.7.8 服务端延迟出货） ====================

    /** 延迟出货在动画时长之外的安全余量（毫秒）：保证客户端轮盘停格后产物才落槽 */
    private static final long DISPATCH_DELAY_MARGIN_MS = 150L;

    /** Only UI refresh is delayed. No paid item, machine lookup, or fallback payout lives in this task. */
    private void scheduleDelayedSync(UUID playerId, long delayMs) {
        EntityPlayerMP actor = DailySignInManager.getPlayerByUUID(playerId);
        if (actor == null) return;
        LotteryHandler.scheduleDelayedTask(delayMs, () -> {
            EntityPlayerMP player = DailySignInManager.getPlayerByUUID(playerId);
            if (player == actor) LotteryNetworkManager.sendSyncToClient(player);
        });
    }

    /**
     * 按维度 + 坐标定位猫猫售货机 V2
     * <p>
     * 抽奖请求包（{@code LotteryRequestPacket}）使用。
     * 坐标无效/机器不存在/类型不符时返回 null，调用方必须拒绝，不能绕过机器发奖。
     *
     * @param dim   维度 ID
     * @param x/y/z 机器坐标
     * @return 机器实例；未找到返回 null
     */
    public static MTENekoVendingMachineV2 findMachine(int dim, int x, int y, int z) {
        try {
            World world = DimensionManager.getWorld(dim);
            if (world == null || !world.getChunkProvider()
                .chunkExists(x >> 4, z >> 4)) return null;
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IGregTechTileEntity) {
                if (((IGregTechTileEntity) te).getMetaTileEntity() instanceof MTENekoVendingMachineV2) {
                    return (MTENekoVendingMachineV2) ((IGregTechTileEntity) te).getMetaTileEntity();
                }
            }
        } catch (Exception e) {
            LOG.error("定位抽奖触发机器失败", e);
        }
        return null;
    }

    // ==================== 保底计数 ====================

    /**
     * 查询团队某池的保底计数（连续未出高稀有次数）
     */
    public int getPityCounter(UUID teamKey, String poolId) {
        requireSession();
        if (teamKey == null || poolId == null) return 0;
        if (unreadableTeams.contains(teamKey)) throw new IllegalStateException("Lottery team data is unreadable");
        loadTeamData(teamKey);
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
        requireSession();
        Map<String, Integer> snapshot = new java.util.HashMap<>();
        if (teamKey == null) return snapshot;
        if (unreadableTeams.contains(teamKey)) throw new IllegalStateException("Lottery team data is unreadable");
        loadTeamData(teamKey);
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

    // ==================== 持久化 ====================

    /**
     * 保存指定团队的保底计数到磁盘
     * <p>
     * v1.7.9 起中奖记录功能移除，仅写保底计数；旧存档中残留的
     * {@code history} NBT 键在下次保存时被整体覆写自然清除。
     */
    public void saveTeamData(UUID teamKey) {
        if (teamKey == null || saveDir == null) return;
        if (unreadableTeams.contains(teamKey)) throw new IllegalStateException("Unreadable lottery data cannot be overwritten");
        // A logout may resolve a newly joined team that was never used in this session. No cache means
        // nothing is owned to save; writing an empty record would destroy that team's existing pity.
        if (!pityCounters.containsKey(teamKey)) return;
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
            com.miaokatze.gtit.util.AtomicNbtFile.write(nbt, file);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save lottery team data: " + teamKey, e);
        }
    }

    /**
     * 从磁盘加载指定团队的保底计数（文件不存在时保持内存现状）
     * <p>
     * v1.7.9 起不再读取旧存档中的 {@code history} 键（中奖记录功能已移除），
     * 该键在下次 {@link #saveTeamData} 覆写时自然清除。
     */
    public void loadTeamData(UUID teamKey) {
        requireSession();
        if (teamKey == null || saveDir == null) return;
        // Once loaded, this world-session cache owns the latest state, including a failed-save retry.
        // Teammate logins must never replay an older file over it.
        if (pityCounters.containsKey(teamKey)) return;
        File file = new File(saveDir, teamKey.toString() + ".dat");
        try {
            Map<String, Integer> decoded = new ConcurrentHashMap<>();
            if (file.exists()) {
                NBTTagCompound nbt = CompressedStreamTools.read(file);
                if (nbt == null) throw new IllegalStateException("Empty lottery team data");
                RewardNbt.optionalType(nbt, "pity", 10);
                NBTTagCompound pityTag = nbt.getCompoundTag("pity");
                for (String key : pityTag.func_150296_c()) {
                    RewardNbt.optionalType(pityTag, key, 3);
                    int count = pityTag.getInteger(key);
                    if (count < 0) throw new IllegalArgumentException("Negative saved lottery pity");
                    decoded.put(key, count);
                }
            }
            // Publish only a fully validated record, including the known-empty first-use case.
            pityCounters.put(teamKey, decoded);
            unreadableTeams.remove(teamKey);
        } catch (Exception e) {
            unreadableTeams.add(teamKey);
            throw new IllegalStateException("Unable to load existing lottery team data: " + teamKey, e);
        }
    }

    /**
     * 玩家登录：预加载其团队抽奖数据（保底），供同步与查询
     */
    public void loadPlayerTeamData(UUID playerId) {
        UUID teamKey = resolveTeamKey(playerId);
        if (teamKey != null) {
            loadTeamData(teamKey);
        }
    }

    /**
     * 玩家下线卸载（团队数据由团队共享，不随个人下线清除；此处仅落盘保底数据）
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
    }

    /** Stop authority before saving. A failed flush retains its original world and retryable state. */
    public void unloadAll() {
        sessionUnavailable = true;
        saveAll();
        pityCounters.clear();
        unreadableTeams.clear();
        pools.clear();
        saveDir = null;
    }

    /**
     * 热重载卡池配置（预留指令入口，目标 4/5）
     */
    public void reload() {
        loadConfig();
    }

    private void requireSession() {
        if (sessionUnavailable) throw new IllegalStateException("Lottery world session is unavailable");
    }

    // ==================== 查询 ====================

    /**
     * 获取卡池
     */
    public LotteryPool getPool(String poolId) {
        requireSession();
        return pools.get(poolId);
    }

    /**
     * 获取全部卡池（顺序按 Map 迭代；GUI 展示用）
     */
    public List<LotteryPool> getAllPools() {
        requireSession();
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
}
