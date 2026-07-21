package com.miaokatze.gtit.signin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

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

    public static final DailySignInManager INSTANCE = new DailySignInManager();

    /** 已加载的玩家签到数据（仅在线玩家常驻内存） */
    private final ConcurrentHashMap<UUID, DailySignInData> signInDataMap;
    /** 存档目录（<world>/gtit_signin） */
    private File saveDir;
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
        GTInterestingThing.LOG.info("签到数据存储目录: {}", saveDir.getAbsolutePath());
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
     * 流程：重复校验 → 断签/跨月修正 → 记录签到 → 发放基础奖励（猫猫币入钱包）
     * → 检查阶梯奖励（当月每档限领一次）→ 持久化。
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

        // 发放基础奖励（猫猫币入钱包，钱包自身处理团队/个人回退）
        int baseReward = DailySignInConfig.calculateBaseReward(newConsecutive);
        grantCurrency(playerId, com.miaokatze.gtit.trade.NekoCurrencyRegistrar.NEKO_ID, baseReward);

        // 阶梯奖励：达到配置天数且当月未领取时发放
        SignInRewardTier grantedTier = null;
        SignInRewardTier tier = DailySignInConfig.getTriggeredTier(newConsecutive);
        if (tier != null && !data.hasClaimedTier(tier.getRequiredDays(), getYearMonth())) {
            grantTierReward(playerId, tier);
            data.claimTierReward(tier.getRequiredDays(), getYearMonth());
            grantedTier = tier;
        }

        saveSignInData(playerId);
        return new SignInResult(SignInResult.Status.SUCCESS, baseReward, grantedTier, newConsecutive);
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
     * 发放阶梯奖励（货币 + 可选物品）
     * <p>
     * 物品奖励需要玩家在线才能入背包；离线时跳过物品部分（货币仍会入钱包）。
     */
    public void grantTierReward(UUID playerId, SignInRewardTier tier) {
        if (tier == null) return;
        if (tier.getCurrencyAmount() > 0) {
            grantCurrency(playerId, tier.getCurrencyId(), tier.getCurrencyAmount());
        }
        if (tier.hasItemReward()) {
            grantItemReward(playerId, tier);
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
        GTInterestingThing.LOG.info("检测到跨日，执行签到数据修正（{} 名在线玩家）", signInDataMap.size());
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
        if (playerId == null || saveDir == null) return;
        DailySignInData data = signInDataMap.get(playerId);
        if (data == null) return;
        File file = new File(saveDir, playerId.toString() + ".dat");
        try {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setTag("signin", data.writeToNBT());
            CompressedStreamTools.safeWrite(nbt, file);
        } catch (Exception e) {
            GTInterestingThing.LOG.error("保存签到数据失败: " + playerId, e);
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
            GTInterestingThing.LOG.error("加载签到数据失败: " + playerId, e);
        }
        return null;
    }

    /** 向玩家钱包发放货币并立即持久化 */
    private void grantCurrency(UUID playerId, String currencyId, int amount) {
        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(playerId);
        if (wallet == null) return;
        wallet.addCount(currencyId, amount);
        NekoWalletManager.INSTANCE.saveWallet(playerId);
    }

    /** 发放阶梯物品奖励：入玩家背包，背包满则掉落在玩家脚下 */
    private void grantItemReward(UUID playerId, SignInRewardTier tier) {
        EntityPlayerMP player = getPlayerByUUID(playerId);
        if (player == null) {
            GTInterestingThing.LOG.warn("玩家 {} 不在线，阶梯物品奖励 {} 已跳过", playerId, tier.getItemRewardId());
            return;
        }
        String[] parts = tier.getItemRewardId()
            .split(":");
        if (parts.length != 2) return;
        Item item = GameRegistry.findItem(parts[0], parts[1]);
        if (item == null) {
            GTInterestingThing.LOG.warn("阶梯奖励物品不存在: {}", tier.getItemRewardId());
            return;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, tier.getItemRewardAmount()), tier.getItemRewardMeta());
        if (!player.inventory.addItemStackToInventory(stack)) {
            // 背包已满：掉落在玩家位置，避免奖励丢失
            EntityItem drop = new EntityItem(player.worldObj, player.posX, player.posY, player.posZ, stack);
            player.worldObj.spawnEntityInWorld(drop);
        }
    }

    /** 按 UUID 查找在线玩家（未找到返回 null） */
    public static EntityPlayerMP getPlayerByUUID(UUID playerId) {
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
        /** 本次发放的基础猫猫币数量 */
        private final int baseReward;
        /** 本次触发的阶梯奖励（未触发为 null） */
        private final SignInRewardTier tierReward;
        /** 签到后的连续天数 */
        private final int consecutiveDays;

        public SignInResult(Status status, int baseReward, SignInRewardTier tierReward, int consecutiveDays) {
            this.status = status;
            this.baseReward = baseReward;
            this.tierReward = tierReward;
            this.consecutiveDays = consecutiveDays;
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
    }
}
