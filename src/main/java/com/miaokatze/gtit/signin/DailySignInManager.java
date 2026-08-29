package com.miaokatze.gtit.signin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.mail.BlessingManager;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.util.NbtBase64Util;
import com.miaokatze.gtit.util.PlayerLookup;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 签到管理器单例
 * <p>
 * 管理所有玩家的签到数据（个人维度，按 UUID 存储），处理签到逻辑、奖励发放与持久化。
 * 数据存储到 {@code <world>/gtit_signin/<player_uuid>.dat}（NBT 格式，
 * 参照 {@link NekoWalletManager} 的 CompressedStreamTools 模式）。
 * <p>
 * <b>断签/跨月修正</b>：登录、签到前、跨日 tick 三处都会调用 {@link #applyDateCorrections}：
 * <ul>
 * <li>跨月（上次签到月份 ≠ 当前月份）→ 清空当月签到日期列表（累计/连续天数不受影响）</li>
 * <li>断签（上次签到既非今天也非昨天）→ 连续天数归零</li>
 * </ul>
 */
public class DailySignInManager {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    public static final DailySignInManager INSTANCE = new DailySignInManager();

    /** 自定义纪念日数量上限（与「活跃」页 4 的 GUI 行数一致） */
    public static final int MAX_ANNIVERSARIES = 5;

    /** 在线奖励领取结果：成功 */
    public static final int CLAIM_ONLINE_SUCCESS = 0;
    /** 在线奖励领取结果：在线时长未达档位 */
    public static final int CLAIM_ONLINE_NOT_REACHED = 1;
    /** 在线奖励领取结果：今日已领取过该档位 */
    public static final int CLAIM_ONLINE_ALREADY_CLAIMED = 2;
    /** 在线奖励领取结果：数据/参数异常 */
    public static final int CLAIM_ONLINE_ERROR = 3;

    /** 已加载的玩家签到数据（仅在线玩家常驻内存） */
    private final ConcurrentHashMap<UUID, DailySignInData> signInDataMap;
    /** 存档目录（<world>/gtit_signin） */
    private File saveDir;
    /** saveDir 未初始化告警一次性标记（v1.7.48 缺口C：首次触发 WARN 后静默，成功保存一次后重置） */
    private boolean saveDirWarned = false;
    /** tickOnlineMinute 调用计数（每 5 次≈5 分钟触发一次 saveAll 周期落盘，防崩溃/定时重启回档） */
    private int onlineMinuteCounter = 0;
    /** 上次 tick 检查时的日期（用于跨日检测） */
    private String lastKnownDate = "";

    private DailySignInManager() {
        this.signInDataMap = new ConcurrentHashMap<>();
    }

    /**
     * 初始化存储目录（CommonProxy.serverStarted 调用，需要 World 对象）
     */
    public void init(World world) {
        saveDir = new File(
            world.getSaveHandler()
                .getWorldDirectory(),
            "gtit_signin");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        lastKnownDate = getToday();
        LOG.info("签到数据存储目录: {}", saveDir.getAbsolutePath());
    }

    // ==================== 签到核心逻辑 ====================

    /**
     * 判断玩家今日是否还能签到
     */
    public boolean canSignIn(UUID playerId) {
        DailySignInData data = getSignInData(playerId);
        return data != null && !data.hasSignedToday(getToday());
    }

    /**
     * 执行签到（服务端调用）
     * <p>
     * 流程：重复校验 → 断签/跨月修正 → 记录签到 → 发放每日奖励（货币入钱包 + 物品入背包，
     * v1.7.8 起按 {@link DailySignInConfig#getEffectiveDayReward} 区分工作日/周末/逐日覆盖）
     * → 检查连续阶梯奖励（当月每档限领一次）→ 检查累计阶梯奖励（永久每档限领一次）→ 持久化。
     *
     * @param playerId 玩家 UUID
     * @return 签到结果（含奖励明细，供网络包/指令反馈）
     */
    public SignInResult signIn(UUID playerId) {
        DailySignInData data = getSignInData(playerId);
        if (data == null) {
            return new SignInResult(SignInResult.Status.ERROR, 0, null, 0);
        }
        String today = getToday();
        if (data.hasSignedToday(today)) {
            return new SignInResult(SignInResult.Status.ALREADY_SIGNED, 0, null, data.getConsecutiveDays());
        }

        // 签到前兜底修正（防跨天未 tick、跨月未重置的边界情况）
        applyDateCorrections(data);

        // 记录签到并计算新连续天数
        int newConsecutive = calculateNewConsecutive(data.getLastSignInDate(), today, data.getConsecutiveDays());
        data.recordSignIn(today, getYesterday(), newConsecutive);

        // v1.7.8 任务6：每日奖励 = 生效奖励（覆盖优先，否则工作日/周末默认）
        // 货币量经 calculateDayCurrency 计算（递增仅作用默认奖励货币量；覆盖天不递增）
        SignInReward dayReward = DailySignInConfig.getEffectiveDayReward(today);
        int baseReward = DailySignInConfig.calculateDayCurrency(today, newConsecutive);
        if (baseReward > 0 && !dayReward.getCurrencyId()
            .isEmpty()) {
            // 货币入钱包（钱包自身处理团队/个人回退）
            grantCurrency(playerId, dayReward.getCurrencyId(), baseReward);
        }
        // 每日物品奖励：入背包（满则脚下掉落；离线跳过，与阶梯物品同口径）
        grantRewardItems(playerId, dayReward);

        // 连续阶梯奖励：达到配置天数且当月未领取时发放
        SignInRewardTier grantedTier = null;
        SignInRewardTier tier = DailySignInConfig.getTriggeredTier(newConsecutive);
        if (tier != null && !data.hasClaimedTier(tier.getRequiredDays(), getYearMonth())) {
            grantTierReward(playerId, tier);
            data.claimTierReward(tier.getRequiredDays(), getYearMonth());
            grantedTier = tier;
        }

        // v1.7.8 任务5：累计阶梯奖励——累计天数精确匹配档位且从未领取时发放（永久每档限领一次）
        SignInRewardTier grantedCumulativeTier = null;
        SignInRewardTier cumTier = DailySignInConfig.getTriggeredCumulativeTier(data.getTotalSignInDays());
        if (cumTier != null && !data.hasClaimedCumulativeTier(cumTier.getRequiredDays())) {
            grantTierReward(playerId, cumTier);
            data.claimCumulativeTier(cumTier.getRequiredDays());
            grantedCumulativeTier = cumTier;
        }

        saveSignInData(playerId);
        return new SignInResult(
            SignInResult.Status.SUCCESS,
            baseReward,
            grantedTier,
            newConsecutive,
            grantedCumulativeTier);
    }

    /**
     * 计算签到后的新连续天数
     *
     * @param lastDate           上次签到日期（空串表示从未签到）
     * @param today              今日日期（yyyy-MM-dd）
     * @param currentConsecutive 当前连续天数
     * @return 新连续天数：昨天签过 → +1；否则断签/首签 → 1
     */
    public int calculateNewConsecutive(String lastDate, String today, int currentConsecutive) {
        if (lastDate == null || lastDate.isEmpty()) return 1;
        if (getYesterday().equals(lastDate)) {
            return Math.max(0, currentConsecutive) + 1;
        }
        return 1;
    }

    /**
     * 发放阶梯奖励（货币 + 物品列表，v1.7.8 统一奖励模型）
     * <p>
     * 连续阶梯与累计阶梯共用本方法；物品奖励需要玩家在线才能入背包，
     * 离线时跳过物品部分（货币仍会入钱包）。
     */
    public void grantTierReward(UUID playerId, SignInRewardTier tier) {
        if (tier == null) return;
        SignInReward reward = tier.getReward();
        if (reward.hasCurrency()) {
            grantCurrency(playerId, reward.getCurrencyId(), reward.getCurrencyAmount());
        }
        grantRewardItems(playerId, reward);
    }

    /**
     * 发放奖励中的物品列表（v1.7.8 任务6：每日奖励/阶梯奖励共用）
     * <p>
     * 逐条调用 {@link #grantItemStack}（入背包，满则脚下掉落；玩家离线跳过）。
     *
     * @param playerId 玩家 UUID
     * @param reward   奖励（空条目已在内部过滤）
     */
    private void grantRewardItems(UUID playerId, SignInReward reward) {
        if (reward == null) return;
        for (RewardItem item : reward.getItems()) {
            if (item == null || item.isEmpty()) continue;
            grantItemStack(
                playerId,
                item.getItemId(),
                item.getAmount(),
                item.getMeta(),
                NbtBase64Util.nbtFromBase64(item.getNbtBase64()));
        }
    }

    /**
     * 断签/跨月修正（登录、签到前、跨日 tick 三处共用）
     *
     * @param data 玩家签到数据
     * @return true 表示数据被修改（需要保存）
     */
    public boolean applyDateCorrections(DailySignInData data) {
        if (data == null) return false;
        boolean dirty = false;
        String last = data.getLastSignInDate();
        if (last != null && !last.isEmpty()) {
            // 跨月：上次签到月份与当前不同 → 清空当月签到列表
            if (!last.startsWith(getYearMonth())) {
                data.resetMonthly();
                dirty = true;
            }
            // 断签：上次签到既非今天也非昨天 → 连续天数归零
            String today = getToday();
            if (!today.equals(last) && !getYesterday().equals(last) && data.getConsecutiveDays() > 0) {
                data.resetConsecutive();
                dirty = true;
            }
        }
        return dirty;
    }

    /**
     * 跨日检查（DailySignInHandler 的服务器 tick 周期性调用）
     * <p>
     * 日期变化时对已加载玩家执行断签/跨月修正，保存并向在线玩家重发同步包。
     */
    public void checkDailyReset() {
        String today = getToday();
        if (today.equals(lastKnownDate)) return;
        lastKnownDate = today;
        LOG.info("检测到跨日，执行签到数据修正（{} 名在线玩家）", signInDataMap.size());
        for (Map.Entry<UUID, DailySignInData> entry : signInDataMap.entrySet()) {
            UUID playerId = entry.getKey();
            if (applyDateCorrections(entry.getValue())) {
                saveSignInData(playerId);
            }
            // 向在线玩家重发最新状态，使 GUI 日历跨日自动刷新
            EntityPlayerMP player = getPlayerByUUID(playerId);
            if (player != null) {
                SignInNetworkManager.sendSyncToClient(player, entry.getValue());
            }
        }
        // v1.7.6 G5：跨日 tick 兜底——为挂机跨零点的在线玩家补测新一天的祝福邮件
        // （登录检测为主；此处仅覆盖「一直在线跨零点」场景，已投递的祝福由防重键拦截）
        try {
            BlessingManager.INSTANCE.checkAllOnlinePlayers();
        } catch (Throwable t) {
            LOG.error("跨日祝福检测失败", t);
        }
    }

    /**
     * 对所有已加载玩家强制月度重置（管理指令兜底用）
     */
    public void performMonthlyReset() {
        for (Map.Entry<UUID, DailySignInData> entry : signInDataMap.entrySet()) {
            entry.getValue()
                .resetMonthly();
            saveSignInData(entry.getKey());
        }
    }

    // ==================== v1.7.6 G2③：每日在线时间 ====================

    /**
     * 在线时间累计（DailySignInHandler 服务器 tick 每 1200 tick=1 分钟调用一次）
     * <p>
     * 为在线玩家累计 60 秒（B2-12：仅真实在线条目——admin 指令加载的离线数据驻留
     * 本 map 但不累计不推送），跨日重置由
     * {@link DailySignInData#addOnlineSeconds} 内部完成（与 applyDateCorrections 同挂载节奏）。
     * 累计后向各在线玩家推送一次全量同步，使 GUI「今日在线时长」每分钟自动刷新。
     * <p>
     * <b>落盘口径（v1.7.48 修订，对齐钱包 O2-17：周期 5 分钟 + 登出 + 停服三重兜底）</b>：
     * 本方法每 5 次调用（1200 tick × 5 ≈ 5 分钟）触发一次 {@link #saveAll()} 周期落盘，
     * 与登出落盘（unloadSignInData）、停服落盘（CommonProxy.serverStopping → saveAll /
     * serverStopped → unloadAll）共同承担持久化；崩溃/定时重启时在线玩家的回档窗口
     * 收敛为一个周期（最长 5 分钟）。v1.7.6 的“本方法不做磁盘 IO”口径已废止——
     * 该口径导致服务器重启后在线时长回档到上次登出点，跨零点则整日清零。
     */
    public void tickOnlineMinute() {
        String today = getToday();
        for (Map.Entry<UUID, DailySignInData> entry : signInDataMap.entrySet()) {
            // B2-12：admin 指令加载的离线签到数据会驻留本 map（仅登出路径卸载）——
            // 在线时长只对真实在线玩家累计，离线条目既不加分也不推送
            EntityPlayerMP player = getPlayerByUUID(entry.getKey());
            if (player == null) continue;
            entry.getValue()
                .addOnlineSeconds(today, 60);
            // 向在线玩家重发最新状态（GUI 在线时长/领取状态每分钟刷新）
            SignInNetworkManager.sendSyncToClient(player, entry.getValue());
        }
        // v1.7.48 缺口A兜底：每 5 次调用（1200 tick × 5 = 5 分钟）全量落盘一次，
        // 崩溃/定时重启场景在线时长回档窗口收敛为一个周期
        if (++onlineMinuteCounter >= 5) {
            onlineMinuteCounter = 0;
            saveAll();
        }
    }

    /**
     * 领取每日在线奖励（服务端权威，SignInRequestPacket type="claim_online" 触发）
     * <p>
     * 流程：跨日兜底修正 → 档位校验 → 时长校验 → 防重校验 → 发放（货币入钱包 + 可选物品）
     * → 记录领取 → 落盘。
     *
     * @param playerId  玩家 UUID
     * @param tierIndex 目标档位索引（{@link OnlineTimeConfig#getTiers()} 升序下标）
     * @return 领取结果码（{@link #CLAIM_ONLINE_SUCCESS} 等）
     */
    public int claimOnlineReward(UUID playerId, int tierIndex) {
        DailySignInData data = getSignInData(playerId);
        if (data == null) return CLAIM_ONLINE_ERROR;
        String today = getToday();
        // 兜底：跨日未 tick 时先按今天口径修正在线计数与领取记录（+0 秒仅触发重置检查）
        data.addOnlineSeconds(today, 0);

        List<OnlineTimeRewardTier> tiers = OnlineTimeConfig.getTiers();
        if (tierIndex < 0 || tierIndex >= tiers.size()) return CLAIM_ONLINE_ERROR;
        OnlineTimeRewardTier tier = tiers.get(tierIndex);

        if (data.getOnlineSecondsToday() < tier.getRequiredSeconds()) return CLAIM_ONLINE_NOT_REACHED;
        if (data.hasClaimedOnlineTier(today, tier.getRequiredSeconds())) return CLAIM_ONLINE_ALREADY_CLAIMED;

        // 发放：货币入钱包（复用签到奖励发放路径）；可选物品入背包（满则脚下掉落）
        if (tier.getCurrencyAmount() > 0) {
            grantCurrency(playerId, tier.getCurrencyId(), tier.getCurrencyAmount());
        }
        if (tier.hasItemReward()) {
            grantItemStack(
                playerId,
                tier.getItemRewardId(),
                tier.getItemRewardAmount(),
                tier.getItemRewardMeta(),
                NbtBase64Util.nbtFromBase64(tier.getItemNbt()));
        }
        data.claimOnlineTier(today, tier.getRequiredSeconds());
        saveSignInData(playerId);
        return CLAIM_ONLINE_SUCCESS;
    }

    // ==================== v1.7.6 G2③：生日 / 首登 / 纪念日 ====================

    /**
     * 确保首次进入日期已记录（登录事件调用）
     * <p>
     * {@link DailySignInData#getFirstJoinDate()} 为空（新玩家/旧存档升级）时写入今天，
     * 只写一次不覆盖。
     *
     * @param data 玩家签到数据
     * @return true 表示数据被修改（需要保存）
     */
    public boolean ensureFirstJoinDate(DailySignInData data) {
        if (data == null) return false;
        if (!data.getFirstJoinDate()
            .isEmpty()) {
            return false;
        }
        data.setFirstJoinDate(getToday());
        return true;
    }

    /**
     * 设置玩家生日（服务端权威，SignInRequestPacket type="set_birthday" 触发）
     *
     * @param playerId 玩家 UUID
     * @param monthDay "MM-dd"（空串=清除设置）
     * @return true 表示设置成功（格式合法）；false 表示格式非法被拒绝
     */
    public boolean setBirthday(UUID playerId, String monthDay) {
        DailySignInData data = getSignInData(playerId);
        if (data == null) return false;
        String normalized = monthDay == null ? "" : monthDay.trim();
        if (!normalized.isEmpty() && !AnniversaryEntry.isValidMonthDay(normalized)) {
            return false;
        }
        data.setBirthday(normalized);
        saveSignInData(playerId);
        return true;
    }

    /**
     * 添加自定义纪念日（服务端权威，SignInRequestPacket type="set_anniversary" 添加载荷触发）
     *
     * @param playerId 玩家 UUID
     * @param name     纪念日名称（trim 后非空，超长截断到 {@link AnniversaryEntry#MAX_NAME_LENGTH}）
     * @param monthDay 月日（"MM-dd"，须通过 {@link AnniversaryEntry#isValidMonthDay}）
     * @param year     年份（0=不记年份；>0 时须在 {@link AnniversaryEntry#MIN_YEAR}..{@link AnniversaryEntry#MAX_YEAR}）
     * @return 0=成功；1=名称/日期/年份非法；2=数量已达上限（{@link #MAX_ANNIVERSARIES}）
     */
    public int addAnniversary(UUID playerId, String name, String monthDay, int year) {
        DailySignInData data = getSignInData(playerId);
        if (data == null) return 1;
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.length() > AnniversaryEntry.MAX_NAME_LENGTH) {
            normalizedName = normalizedName.substring(0, AnniversaryEntry.MAX_NAME_LENGTH);
        }
        String normalizedMonthDay = monthDay == null ? "" : monthDay.trim();
        if (normalizedName.isEmpty() || !AnniversaryEntry.isValidMonthDay(normalizedMonthDay)) {
            return 1;
        }
        if (year != 0 && (year < AnniversaryEntry.MIN_YEAR || year > AnniversaryEntry.MAX_YEAR)) {
            return 1;
        }
        if (data.getAnniversaries()
            .size() >= MAX_ANNIVERSARIES) {
            return 2;
        }
        data.getAnniversaries()
            .add(new AnniversaryEntry(normalizedName, normalizedMonthDay, year));
        saveSignInData(playerId);
        return 0;
    }

    /**
     * 删除自定义纪念日（服务端权威，按列表索引——客户端列表为服务端全量同步镜像，索引一致）
     *
     * @param playerId 玩家 UUID
     * @param index    纪念日列表索引
     * @return true 表示删除成功；false 表示索引越界/数据异常
     */
    public boolean removeAnniversary(UUID playerId, int index) {
        DailySignInData data = getSignInData(playerId);
        if (data == null) return false;
        if (index < 0 || index >= data.getAnniversaries()
            .size()) {
            return false;
        }
        data.getAnniversaries()
            .remove(index);
        saveSignInData(playerId);
        return true;
    }

    // ==================== 数据存取 ====================

    /**
     * 获取玩家签到数据（优先内存缓存，未加载则从磁盘读取，均无则新建）
     */
    public DailySignInData getSignInData(UUID playerId) {
        if (playerId == null) return null;
        DailySignInData data = signInDataMap.get(playerId);
        if (data == null) {
            data = loadFromDisk(playerId);
            if (data == null) {
                data = new DailySignInData();
            }
            signInDataMap.put(playerId, data);
        }
        return data;
    }

    /**
     * 保存指定玩家数据到磁盘
     */
    public void saveSignInData(UUID playerId) {
        if (playerId == null) return;
        if (saveDir == null) {
            // v1.7.48 缺口C：init 失败时此前静默丢弃全部保存（字面“退出即重置”的唯一代码机制）——
            // 首次触发 WARN 提示，之后静默防周期落盘场景刷屏
            if (!saveDirWarned) {
                LOG.warn("签到存储目录未初始化（saveDir==null），签到数据保存被跳过；后续跳过不再重复告警");
                saveDirWarned = true;
            }
            return;
        }
        DailySignInData data = signInDataMap.get(playerId);
        if (data == null) return;
        File file = new File(saveDir, playerId.toString() + ".dat");
        try {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setTag("signin", data.writeToNBT());
            CompressedStreamTools.safeWrite(nbt, file);
            saveDirWarned = false;
        } catch (Exception e) {
            LOG.error("保存签到数据失败: " + playerId, e);
        }
    }

    /**
     * 从磁盘加载指定玩家数据到内存（若文件不存在则保持内存现状）
     */
    public void loadSignInData(UUID playerId) {
        if (playerId == null) return;
        DailySignInData data = loadFromDisk(playerId);
        if (data != null) {
            signInDataMap.put(playerId, data);
        }
    }

    /**
     * 玩家下线：保存并从内存卸载
     */
    public void unloadSignInData(UUID playerId) {
        saveSignInData(playerId);
        signInDataMap.remove(playerId);
    }

    /**
     * 保存所有已加载玩家数据（周期保存/服务器关闭）
     */
    public void saveAll() {
        for (UUID playerId : signInDataMap.keySet()) {
            saveSignInData(playerId);
        }
    }

    /**
     * 服务器停止收尾：全量落盘并清空内存缓存（CommonProxy.serverStopped 调用）。
     * <p>
     * 对齐 {@link NekoWalletManager#unloadAll()} 形态（先落盘后清空）：清空
     * {@link #signInDataMap} 防止单机连续切换世界时旧世界签到数据驻留内存、
     * 遮蔽新世界 {@code <world>/gtit_signin/<uuid>.dat}（登录加载仅在不命中
     * 内存缓存时才读盘）。saveDir 与 lastKnownDate 由下次 serverStarted 的
     * {@link #init} 重新赋值，无需在此复位。
     */
    public void unloadAll() {
        saveAll();
        signInDataMap.clear();
    }

    // ==================== 管理指令支持 ====================

    /**
     * 管理员设置玩家连续签到天数（/gtit signin admin set）
     * 设置后保存并向在线玩家重发同步。
     */
    public void adminSetConsecutiveDays(UUID playerId, int days) {
        DailySignInData data = getSignInData(playerId);
        if (data == null) return;
        data.setConsecutiveDays(days);
        saveSignInData(playerId);
        EntityPlayerMP player = getPlayerByUUID(playerId);
        if (player != null) {
            SignInNetworkManager.sendSyncToClient(player, data);
        }
    }

    /**
     * 管理员重置玩家签到数据（/gtit signin admin reset）
     */
    public void adminResetData(UUID playerId) {
        signInDataMap.put(playerId, new DailySignInData());
        saveSignInData(playerId);
        EntityPlayerMP player = getPlayerByUUID(playerId);
        if (player != null) {
            SignInNetworkManager.sendSyncToClient(player, signInDataMap.get(playerId));
        }
    }

    // ==================== 日期工具 ====================

    /** 今日日期（yyyy-MM-dd，服务器本地时区） */
    public static String getToday() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    /** 昨日日期（yyyy-MM-dd） */
    public static String getYesterday() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
    }

    /** 当前年月（yyyy-MM，用于月度重置与阶梯奖励领取记录） */
    public static String getYearMonth() {
        return new SimpleDateFormat("yyyy-MM").format(new Date());
    }

    // ==================== 内部辅助 ====================

    /** 从磁盘读取玩家签到数据（文件不存在或损坏时返回 null） */
    private DailySignInData loadFromDisk(UUID playerId) {
        if (saveDir == null) return null;
        File file = new File(saveDir, playerId.toString() + ".dat");
        if (!file.exists()) return null;
        try {
            NBTTagCompound nbt = CompressedStreamTools.read(file);
            if (nbt != null && nbt.hasKey("signin")) {
                DailySignInData data = new DailySignInData();
                data.readFromNBT(nbt.getCompoundTag("signin"));
                return data;
            }
        } catch (Exception e) {
            LOG.error("加载签到数据失败: " + playerId, e);
        }
        return null;
    }

    /**
     * 向玩家钱包发放货币
     * <p>
     * O2-17：addCount 即登记脏标记，落盘统一由周期（5 分钟）+ 登出 + 停服三重兜底承担，
     * 不再显式 saveWallet。
     */
    private void grantCurrency(UUID playerId, String currencyId, int amount) {
        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
        if (wallet == null) return;
        wallet.addCount(currencyId, amount);
    }

    /**
     * 发放物品奖励（v1.7.6 G2③ 抽取的通用路径：签到阶梯/每日在线共用）
     * <p>
     * 入玩家背包，背包满则掉落在玩家脚下；玩家离线时跳过物品部分（货币仍会入钱包）。
     * <p>
     * <b>v1.7.7 G5①</b>：新增 {@code nbt} 参数，支持还原物品 NBT。
     *
     * @param playerId 玩家 UUID
     * @param itemId   物品 ID（"modid:name"）
     * @param amount   数量（<1 时按 1 发放）
     * @param meta     物品 meta
     * @param nbt      物品 NBT（可为 null）
     */
    private void grantItemStack(UUID playerId, String itemId, int amount, int meta, NBTTagCompound nbt) {
        EntityPlayerMP player = getPlayerByUUID(playerId);
        if (player == null) {
            LOG.warn("玩家 {} 不在线，物品奖励 {} 已跳过", playerId, itemId);
            return;
        }
        String[] parts = itemId.split(":");
        if (parts.length != 2) return;
        Item item = GameRegistry.findItem(parts[0], parts[1]);
        if (item == null) {
            LOG.warn("奖励物品不存在: {}", itemId);
            return;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, amount), meta);
        if (nbt != null) {
            // v1.7.7 G5①：copy() 返回 NBTBase，需强转为 NBTTagCompound 再写入物品
            stack.setTagCompound((NBTTagCompound) nbt.copy());
        }
        if (!player.inventory.addItemStackToInventory(stack)) {
            // 背包已满：掉落在玩家位置，避免奖励丢失
            EntityItem drop = new EntityItem(player.worldObj, player.posX, player.posY, player.posZ, stack);
            player.worldObj.spawnEntityInWorld(drop);
        }
    }

    /** 按 UUID 查找在线玩家（未找到返回 null；O2-12 起委托 PlayerLookup 统一实现） */
    public static EntityPlayerMP getPlayerByUUID(UUID playerId) {
        return PlayerLookup.getOnlinePlayerByUuid(playerId);
    }

    // ==================== 签到结果 ====================

    /**
     * 签到结果（供网络包处理器/指令构建反馈消息）
     */
    public static class SignInResult {

        public enum Status {
            /** 签到成功 */
            SUCCESS,
            /** 今日已签到（重复点击/重复请求） */
            ALREADY_SIGNED,
            /** 数据异常（如玩家数据为空） */
            ERROR
        }

        private final Status status;
        /** 本次发放的每日奖励货币数量 */
        private final int baseReward;
        /** 本次触发的连续阶梯奖励（未触发为 null） */
        private final SignInRewardTier tierReward;
        /** 签到后的连续天数 */
        private final int consecutiveDays;
        /** 本次触发的累计阶梯奖励（v1.7.8 任务5；未触发为 null） */
        private final SignInRewardTier cumulativeTierReward;

        public SignInResult(Status status, int baseReward, SignInRewardTier tierReward, int consecutiveDays) {
            this(status, baseReward, tierReward, consecutiveDays, null);
        }

        public SignInResult(Status status, int baseReward, SignInRewardTier tierReward, int consecutiveDays,
            SignInRewardTier cumulativeTierReward) {
            this.status = status;
            this.baseReward = baseReward;
            this.tierReward = tierReward;
            this.consecutiveDays = consecutiveDays;
            this.cumulativeTierReward = cumulativeTierReward;
        }

        public Status getStatus() {
            return status;
        }

        public int getBaseReward() {
            return baseReward;
        }

        public SignInRewardTier getTierReward() {
            return tierReward;
        }

        public int getConsecutiveDays() {
            return consecutiveDays;
        }

        /** 本次触发的累计阶梯奖励（未触发返回 null） */
        public SignInRewardTier getCumulativeTierReward() {
            return cumulativeTierReward;
        }
    }
}
