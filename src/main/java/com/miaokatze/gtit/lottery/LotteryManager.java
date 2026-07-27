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
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.signin.DailySignInManager;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoTradeExecutor;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 抽奖管理器单例（团队维度）
 * <p>
 * 管理卡池配置（{@link LotteryConfig}）与团队保底计数：
 * <ul>
 * <li><b>团队键</b>：保底计数以「团队 UUID」为键存储——
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
    /** 存档目录（<world>/gtit_lottery） */
    private File saveDir;
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
     * 解析玩家的团队键（保底计数存储键）
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
     * 流程：校验卡池与机器 → 扣费分流（{@link #deductCostItems}：costItems 中猫猫币条目走
     * 团队钱包、普通物品条目从机器输入槽扣除，校验通过才实际扣减）→ 逐抽
     * （软保底加权 + 硬保底强制替换）→ 落盘保底 → 调度延迟出货
     * （{@link #scheduleDelayedDispatch}：等客户端轮盘动画播完再落槽/入钱包）。
     * <p>
     * <b>原子性</b>：扣费一次性完成（count 连抽全部需求），任一抽出货失败不回滚已抽结果
     * （与交易 OUTPUT_FULL 回滚不同——抽奖出货溢出已退玩家背包，无丢失路径）。
     * <p>
     * <b>延迟出货</b>（v1.7.8 起）：奖品不再立即发放——结果包先行下发驱动客户端轮盘动画，
     * 出货延迟「动画时长 + 150ms」由 {@link LotteryHandler} 延迟任务队列执行，
     * 产物落输出槽时客户端 {@code NekoFallingItemSlotFactory} 自然播下落动画，
     * 全量同步（保底/余额）也随之推迟到出货后，避免动画期间剧透。
     *
     * @param playerId 抽取玩家
     * @param poolId   卡池 ID
     * @param count    连抽次数（1 或 10）
     * @param machine  触发的售货机（物品奖品出货槽 + 物品消耗来源；为 null 时物品直接给玩家，
     *                 但含物品消耗的池会被拒）
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

        // 1. 扣费分流（v1.7.6 costItems：货币条目→团队钱包，物品条目→机器输入槽；全量校验后原子扣减）
        if (!deductCostItems(playerId, pool, count, machine)) {
            return results; // 余额/物品不足：空结果，由调用方提示
        }

        // 2. 逐抽（保底计数按「团队 × 卡池」维度推进）
        // v1.7.9：中奖记录功能已移除，不再记历史
        UUID teamKey = resolveTeamKey(playerId);
        for (int i = 0; i < count; i++) {
            LotteryDrawResult result = drawSingle(teamKey, pool);
            if (result == null) continue;
            results.add(result);
        }

        // 3. 落盘保底（立即：保底数据落盘不等待出货）
        saveTeamData(teamKey);

        // 4. 调度延迟出货（动画时长 + 150ms 后执行 dispatchAll；奖品列表快照随任务携带）
        scheduleDelayedDispatch(playerId, results, machine);
        return results;
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
        for (NekoBigItemStack cost : pool.getCostItems()) {
            if (cost == null || cost.getBaseStack() == null || cost.getStackSize() <= 0) continue;
            int total = cost.getStackSize() * Math.max(1, count);
            String cid = NekoCurrencyRegistrar.getNekoCurrencyId(cost.getBaseStack());
            if (cid != null) {
                // 猫猫币条目：按币种聚合（同币种多条目合并扣款）
                currencyNeeds.merge(cid, total, Integer::sum);
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
        if (playerId == null || pool == null || count <= 0) return false;
        Map<String, Integer> currencyNeeds = new java.util.LinkedHashMap<>();
        List<NekoBigItemStack> itemNeeds = new ArrayList<>();
        collectCostNeeds(pool, count, currencyNeeds, itemNeeds);

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
        collectCostNeeds(pool, count, currencyNeeds, itemNeeds);
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
            NekoWalletManager.INSTANCE.saveWallet(playerId);
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

    // ==================== 出货（v1.7.8 服务端延迟出货） ====================

    /** 延迟出货在动画时长之外的安全余量（毫秒）：保证客户端轮盘停格后产物才落槽 */
    private static final long DISPATCH_DELAY_MARGIN_MS = 150L;

    /**
     * 调度延迟出货
     * <p>
     * 快照机器「维度 + 坐标」（延迟期间机器可能被拆除/卸载，届时按坐标重解析，
     * 解析失败退玩家背包），延迟 = 动画时长（单抽 {@link LotteryAnimationController#DURATION_SINGLE_MS} /
     * 10 连快闪 {@link LotteryAnimationController#DURATION_QUICK_MS}）+ {@link #DISPATCH_DELAY_MARGIN_MS}。
     * 到期由 {@link LotteryHandler} 延迟任务队列在服务器主线程执行 {@link #dispatchAll}。
     *
     * @param playerId 抽取玩家
     * @param results  抽取结果列表（奖品快照；本方法内部防御性复制）
     * @param machine  触发机器（可为 null，此时物品直接退玩家背包）
     */
    private void scheduleDelayedDispatch(UUID playerId, List<LotteryDrawResult> results,
        MTENekoVendingMachineV2 machine) {
        if (results == null || results.isEmpty()) return;

        // 快照机器维度与坐标（延迟执行时按坐标重解析，避免持有机器引用跨 tick 失效）
        int dim = 0;
        int x = 0;
        int y = 0;
        int z = 0;
        boolean hasMachine = machine != null && machine.getBaseMetaTileEntity() != null;
        if (hasMachine) {
            IGregTechTileEntity base = machine.getBaseMetaTileEntity();
            dim = base.getWorld() != null ? base.getWorld().provider.dimensionId : 0;
            x = base.getXCoord();
            y = base.getYCoord();
            z = base.getZCoord();
        }
        final int fDim = dim;
        final int fX = x;
        final int fY = y;
        final int fZ = z;
        final boolean fHasMachine = hasMachine;

        // 延迟 = 客户端动画时长 + 余量（与轮盘停格对齐；10 连走快闪时长）
        long animMs = results.size() > 1 ? LotteryAnimationController.DURATION_QUICK_MS
            : LotteryAnimationController.DURATION_SINGLE_MS;
        final List<LotteryDrawResult> snapshot = new ArrayList<>(results);
        LotteryHandler.scheduleDelayedTask(
            animMs + DISPATCH_DELAY_MARGIN_MS,
            () -> dispatchAll(playerId, snapshot, fHasMachine, fDim, fX, fY, fZ));
    }

    /**
     * 延迟出货执行（服务器主线程，{@link LotteryHandler} 延迟任务队列驱动）
     * <ul>
     * <li>物品奖品：按快照坐标重解析机器 → {@code startBatch(物品总数)} +
     * {@code dispenseItemStack} 逐件落输出缓冲（onPostTick 逐 tick 投放，
     * 客户端 {@code NekoFallingItemSlotFactory} 自然触发下落动画）；
     * 机器失效（拆除/卸载/未找到）则全部退玩家背包</li>
     * <li>货币奖品：直接入团队钱包（无动画，随本批次一并入账）</li>
     * <li>溢出：玩家在线退背包（满则掉脚下）；玩家离线且有机器坐标则在机器处生成
     * {@link EntityItem}，无坐标兜底记警告</li>
     * <li>末尾 {@link LotteryNetworkManager#sendSyncToClient}：保底/钱包余额
     * 在动画停格后才刷新（不提前剧透）</li>
     * </ul>
     *
     * @param playerId   抽取玩家
     * @param results    奖品快照
     * @param hasMachine 抽取时是否存在有效机器坐标快照
     * @param dim/x/y/z  机器坐标快照（hasMachine=false 时无意义）
     */
    private void dispatchAll(UUID playerId, List<LotteryDrawResult> results, boolean hasMachine, int dim, int x, int y,
        int z) {
        if (results == null || results.isEmpty()) return;

        // 重解析机器（延迟期间可能已拆除/卸载）
        MTENekoVendingMachineV2 machine = hasMachine ? findMachine(dim, x, y, z) : null;
        EntityPlayerMP player = DailySignInManager.getPlayerByUUID(playerId);

        // 1. 汇总物品奖品（货币奖品在下一步入钱包）
        List<ItemStack> itemPrizes = new ArrayList<>();
        for (LotteryDrawResult result : results) {
            if (result == null || result.getEntry() == null) continue;
            LotteryEntry entry = result.getEntry();
            if (entry.isNekoPrize()) continue;
            ItemStack stack = entry.toItemStack(result.getAmount());
            if (stack == null) {
                GTInterestingThing.LOG.warn("抽奖奖品物品无法构建: {}", entry.getItem());
                continue;
            }
            itemPrizes.add(stack);
        }

        // 2. 货币奖品入团队钱包（合并保存一次）
        boolean walletDirty = false;
        for (LotteryDrawResult result : results) {
            if (result == null || result.getEntry() == null || result.getAmount() <= 0) continue;
            LotteryEntry entry = result.getEntry();
            if (!entry.isNekoPrize()) continue;
            NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
            if (wallet != null) {
                wallet.addCount(entry.getNekoCurrencyId(), result.getAmount());
                walletDirty = true;
            }
        }
        if (walletDirty) {
            NekoWalletManager.INSTANCE.saveWallet(playerId);
        }

        // 3. 物品奖品落机器出货槽（批次模式：分档延迟逐件下落，触发客户端下落动画）；
        // 机器失效则全部作为溢出退玩家背包
        if (machine != null && !itemPrizes.isEmpty()) {
            machine.startBatch(itemPrizes.size());
            for (ItemStack stack : itemPrizes) {
                ItemStack overflow = machine.dispenseItemStack(stack);
                handleOverflow(overflow, player, hasMachine, dim, x, y, z);
            }
        } else {
            for (ItemStack stack : itemPrizes) {
                handleOverflow(stack, player, hasMachine, dim, x, y, z);
            }
        }

        // 4. 出货完成后全量同步（保底计数/钱包余额刷新与轮盘停格对齐）
        if (player != null) {
            LotteryNetworkManager.sendSyncToClient(player);
        }
    }

    /**
     * 出货溢出处理：玩家在线退背包（满则掉脚下）；玩家离线且有机器坐标快照时
     * 在机器坐标生成 {@link EntityItem}，否则记警告丢弃（防静默丢失）。
     */
    private void handleOverflow(ItemStack overflow, EntityPlayerMP player, boolean hasMachine, int dim, int x, int y,
        int z) {
        if (overflow == null || overflow.stackSize <= 0) return;
        if (player != null) {
            // 在线：退背包，背包满则掉落脚下（参照签到阶梯物品奖励）
            if (!player.inventory.addItemStackToInventory(overflow)) {
                EntityItem drop = new EntityItem(player.worldObj, player.posX, player.posY, player.posZ, overflow);
                player.worldObj.spawnEntityInWorld(drop);
            }
            return;
        }
        // 离线：在机器坐标生成掉落物（延迟出货期间玩家可能已下线）
        if (hasMachine) {
            try {
                MinecraftServer server = MinecraftServer.getServer();
                World world = server != null ? server.worldServerForDimension(dim) : null;
                if (world != null) {
                    EntityItem drop = new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, overflow);
                    world.spawnEntityInWorld(drop);
                    return;
                }
            } catch (Exception e) {
                GTInterestingThing.LOG.error("抽奖出货离线掉落失败", e);
            }
        }
        GTInterestingThing.LOG.warn("抽奖出货溢出但玩家离线且无机器坐标，物品已丢弃: {}", overflow);
    }

    /**
     * 按维度 + 坐标定位猫猫售货机 V2
     * <p>
     * 抽奖请求包（{@code LotteryRequestPacket}）与延迟出货（{@link #dispatchAll}）共用。
     * 坐标无效/机器不存在/类型不符时返回 null（调用方退化为直接给玩家处理）。
     *
     * @param dim   维度 ID
     * @param x/y/z 机器坐标
     * @return 机器实例；未找到返回 null
     */
    public static MTENekoVendingMachineV2 findMachine(int dim, int x, int y, int z) {
        try {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) return null;
            World world = server.worldServerForDimension(dim);
            if (world == null) return null;
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IGregTechTileEntity) {
                if (((IGregTechTileEntity) te).getMetaTileEntity() instanceof MTENekoVendingMachineV2) {
                    return (MTENekoVendingMachineV2) ((IGregTechTileEntity) te).getMetaTileEntity();
                }
            }
        } catch (Exception e) {
            GTInterestingThing.LOG.error("定位抽奖触发机器失败", e);
        }
        return null;
    }

    // ==================== 保底计数 ====================

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

    // ==================== 持久化 ====================

    /**
     * 保存指定团队的保底计数到磁盘
     * <p>
     * v1.7.9 起中奖记录功能移除，仅写保底计数；旧存档中残留的
     * {@code history} NBT 键在下次保存时被整体覆写自然清除。
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
            CompressedStreamTools.safeWrite(nbt, file);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存抽奖数据失败: " + teamKey, e);
        }
    }

    /**
     * 从磁盘加载指定团队的保底计数（文件不存在时保持内存现状）
     * <p>
     * v1.7.9 起不再读取旧存档中的 {@code history} 键（中奖记录功能已移除），
     * 该键在下次 {@link #saveTeamData} 覆写时自然清除。
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
        } catch (Exception e) {
            GTInterestingThing.LOG.error("加载抽奖数据失败: " + teamKey, e);
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
}
