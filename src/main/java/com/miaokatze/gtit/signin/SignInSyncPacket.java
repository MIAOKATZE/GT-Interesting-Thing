package com.miaokatze.gtit.signin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 签到数据同步包（服务端→客户端）
 * <p>
 * 携带完整的 {@link DailySignInData}（NBT 序列化）+ 服务端当前日期 + 可选的签到结果反馈
 * + 服务端签到配置快照。
 * 登录、签到后、跨日修正、管理员指令修改、配置编辑保存、/gtit nekovm sync 后均由服务端主动推送，
 * 客户端写入 {@link SignInClientData} 缓存供 {@link SignInCalendarGui} 渲染。
 * <p>
 * <b>v1.7.8 任务5+6 配置快照结构</b>（奖励内容统一由 {@link SignInReward} 网络序列化表达）：
 * <ul>
 * <li>每月块：递增开关 + 递增系数 + 工作日/周末默认奖励 + 逐日覆盖表</li>
 * <li>连续阶梯列表 + 累计阶梯列表（均为 天数 + {@link SignInReward}）</li>
 * <li>在线时长档位列表（v1.7.6 G2③ 既有结构，物品字段保持旧序列化）</li>
 * </ul>
 */
public class SignInSyncPacket implements IMessage {

    /** 签到数据 NBT（{@link DailySignInData#writeToNBT()} 产物） */
    private NBTTagCompound dataTag;
    /** 服务端「今天」（yyyy-MM-dd，统一客户端日期口径） */
    private String serverToday = "";
    /** 签到结果（{@link SignInClientData#RESULT_NONE} 表示纯状态刷新） */
    private int result = SignInClientData.RESULT_NONE;
    /** 本次签到每日奖励货币量（result 为 SUCCESS 时有效） */
    private int baseReward;
    /** 本次触发的连续阶梯奖励天数（0 表示未触发） */
    private int tierDays;

    // ==================== 服务端配置快照 ====================

    /** 服务端每日默认奖励是否随连续天数递增（v1.7.8 任务6） */
    private boolean cfgIncrementEnabled;
    /** 服务端连续天数奖励系数 */
    private double cfgIncrement;
    /** 服务端工作日默认奖励（v1.7.8 任务6） */
    private SignInReward cfgWeekday = SignInReward.EMPTY;
    /** 服务端周末默认奖励（v1.7.8 任务6） */
    private SignInReward cfgWeekend = SignInReward.EMPTY;
    /** 服务端逐日覆盖奖励表（键=月内日号，v1.7.8 任务6） */
    private Map<Integer, SignInReward> cfgDayOverrides = new HashMap<>();
    /** 服务端连续阶梯奖励列表 */
    private List<SignInRewardTier> cfgTiers = new ArrayList<>();
    /** 服务端累计阶梯奖励列表（v1.7.8 任务5） */
    private List<SignInRewardTier> cfgCumulativeTiers = new ArrayList<>();

    // ==================== 在线时长配置快照（v1.7.6 G2③） ====================

    /** 服务端在线时长奖励档位列表 */
    private List<OnlineTimeRewardTier> cfgOnlineTiers = new ArrayList<>();

    public SignInSyncPacket() {
        // 反序列化需要无参构造
    }

    /**
     * 构造同步包（服务端调用，配置快照取自服务端权威的 {@link DailySignInConfig} / {@link OnlineTimeConfig}）
     */
    public SignInSyncPacket(DailySignInData data, String serverToday, int result, int baseReward, int tierDays) {
        this.dataTag = data != null ? data.writeToNBT() : new NBTTagCompound();
        this.serverToday = serverToday == null ? "" : serverToday;
        this.result = result;
        this.baseReward = baseReward;
        this.tierDays = tierDays;
        // 配置快照：服务端权威值
        this.cfgIncrementEnabled = DailySignInConfig.isIncrementEnabled();
        this.cfgIncrement = DailySignInConfig.getConsecutiveIncrement();
        this.cfgWeekday = DailySignInConfig.getWeekdayDefault();
        this.cfgWeekend = DailySignInConfig.getWeekendDefault();
        this.cfgDayOverrides = new HashMap<>(DailySignInConfig.getDayOverrides());
        this.cfgTiers = new ArrayList<>(DailySignInConfig.getRewardTiers());
        this.cfgCumulativeTiers = new ArrayList<>(DailySignInConfig.getCumulativeTiers());
        // 在线时长配置快照（v1.7.6 G2③）
        this.cfgOnlineTiers = new ArrayList<>(OnlineTimeConfig.getTiers());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.dataTag = ByteBufUtils.readTag(buf);
        this.serverToday = ByteBufUtils.readUTF8String(buf);
        this.result = buf.readInt();
        this.baseReward = buf.readInt();
        this.tierDays = buf.readInt();
        // 每月块配置快照（v1.7.8 任务6）
        this.cfgIncrementEnabled = buf.readBoolean();
        this.cfgIncrement = buf.readDouble();
        this.cfgWeekday = SignInReward.readFromByteBuf(buf);
        this.cfgWeekend = SignInReward.readFromByteBuf(buf);
        int overrideCount = buf.readInt();
        this.cfgDayOverrides = new HashMap<>(Math.max(0, overrideCount));
        for (int i = 0; i < overrideCount; i++) {
            int day = buf.readInt();
            this.cfgDayOverrides.put(day, SignInReward.readFromByteBuf(buf));
        }
        // 连续阶梯配置快照（天数 + 统一奖励模型）
        int tierCount = buf.readInt();
        this.cfgTiers = new ArrayList<>(Math.max(0, tierCount));
        for (int i = 0; i < tierCount; i++) {
            int days = buf.readInt();
            this.cfgTiers.add(new SignInRewardTier(days, SignInReward.readFromByteBuf(buf)));
        }
        // 累计阶梯配置快照（v1.7.8 任务5）
        int cumTierCount = buf.readInt();
        this.cfgCumulativeTiers = new ArrayList<>(Math.max(0, cumTierCount));
        for (int i = 0; i < cumTierCount; i++) {
            int days = buf.readInt();
            this.cfgCumulativeTiers.add(new SignInRewardTier(days, SignInReward.readFromByteBuf(buf)));
        }
        // 在线时长配置快照（v1.7.6 G2③；v1.7.7 G5② 新增物品奖励字段，保持旧序列化）
        int onlineCount = buf.readInt();
        this.cfgOnlineTiers = new ArrayList<>(Math.max(0, onlineCount));
        for (int i = 0; i < onlineCount; i++) {
            int seconds = buf.readInt();
            String currencyId = ByteBufUtils.readUTF8String(buf);
            int amount = buf.readInt();
            String itemId = ByteBufUtils.readUTF8String(buf);
            int itemAmount = buf.readInt();
            int itemMeta = buf.readInt();
            String itemNbt = ByteBufUtils.readUTF8String(buf);
            this.cfgOnlineTiers
                .add(new OnlineTimeRewardTier(seconds, currencyId, amount, itemId, itemAmount, itemMeta, itemNbt));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, this.dataTag == null ? new NBTTagCompound() : this.dataTag);
        ByteBufUtils.writeUTF8String(buf, this.serverToday == null ? "" : this.serverToday);
        buf.writeInt(this.result);
        buf.writeInt(this.baseReward);
        buf.writeInt(this.tierDays);
        // 每月块配置快照（v1.7.8 任务6）
        buf.writeBoolean(this.cfgIncrementEnabled);
        buf.writeDouble(this.cfgIncrement);
        (this.cfgWeekday == null ? SignInReward.EMPTY : this.cfgWeekday).writeToByteBuf(buf);
        (this.cfgWeekend == null ? SignInReward.EMPTY : this.cfgWeekend).writeToByteBuf(buf);
        Map<Integer, SignInReward> overrides = this.cfgDayOverrides == null ? new HashMap<>() : this.cfgDayOverrides;
        buf.writeInt(overrides.size());
        for (Map.Entry<Integer, SignInReward> entry : overrides.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            buf.writeInt(entry.getKey());
            entry.getValue()
                .writeToByteBuf(buf);
        }
        // 连续阶梯配置快照（天数 + 统一奖励模型）
        List<SignInRewardTier> tiers = this.cfgTiers == null ? new ArrayList<>() : this.cfgTiers;
        buf.writeInt(tiers.size());
        for (SignInRewardTier tier : tiers) {
            buf.writeInt(tier.getRequiredDays());
            tier.getReward()
                .writeToByteBuf(buf);
        }
        // 累计阶梯配置快照（v1.7.8 任务5）
        List<SignInRewardTier> cumTiers = this.cfgCumulativeTiers == null ? new ArrayList<>() : this.cfgCumulativeTiers;
        buf.writeInt(cumTiers.size());
        for (SignInRewardTier tier : cumTiers) {
            buf.writeInt(tier.getRequiredDays());
            tier.getReward()
                .writeToByteBuf(buf);
        }
        // 在线时长配置快照（v1.7.6 G2③；保持旧序列化）
        List<OnlineTimeRewardTier> onlineTiers = this.cfgOnlineTiers == null ? new ArrayList<>() : this.cfgOnlineTiers;
        buf.writeInt(onlineTiers.size());
        for (OnlineTimeRewardTier tier : onlineTiers) {
            buf.writeInt(tier.getRequiredSeconds());
            ByteBufUtils.writeUTF8String(buf, tier.getCurrencyId() == null ? "" : tier.getCurrencyId());
            buf.writeInt(tier.getCurrencyAmount());
            ByteBufUtils.writeUTF8String(buf, tier.getItemRewardId() == null ? "" : tier.getItemRewardId());
            buf.writeInt(tier.getItemRewardAmount());
            buf.writeInt(tier.getItemRewardMeta());
            ByteBufUtils.writeUTF8String(buf, tier.getItemNbt() == null ? "" : tier.getItemNbt());
        }
    }

    public NBTTagCompound getDataTag() {
        return dataTag;
    }

    public String getServerToday() {
        return serverToday;
    }

    public int getResult() {
        return result;
    }

    public int getBaseReward() {
        return baseReward;
    }

    public int getTierDays() {
        return tierDays;
    }

    public boolean isCfgIncrementEnabled() {
        return cfgIncrementEnabled;
    }

    public double getCfgIncrement() {
        return cfgIncrement;
    }

    public SignInReward getCfgWeekday() {
        return cfgWeekday;
    }

    public SignInReward getCfgWeekend() {
        return cfgWeekend;
    }

    public Map<Integer, SignInReward> getCfgDayOverrides() {
        return cfgDayOverrides;
    }

    public List<SignInRewardTier> getCfgTiers() {
        return cfgTiers;
    }

    public List<SignInRewardTier> getCfgCumulativeTiers() {
        return cfgCumulativeTiers;
    }

    public List<OnlineTimeRewardTier> getCfgOnlineTiers() {
        return cfgOnlineTiers;
    }

    public static class Handler implements IMessageHandler<SignInSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(SignInSyncPacket message, MessageContext ctx) {
            // 本包只发往客户端；1.7.10 的 onMessage 运行在 Netty 线程，
            // 需切回客户端主线程再写缓存（GUI 在主线程读取）
            if (ctx.side == Side.CLIENT) {
                handleClient(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(final SignInSyncPacket message) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    DailySignInData data = new DailySignInData();
                    data.readFromNBT(message.getDataTag());
                    SignInClientData.update(
                        data,
                        message.getServerToday(),
                        message.getResult(),
                        message.getBaseReward(),
                        message.getTierDays());
                    // 刷新客户端配置缓存（v1.7.8 任务5+6：每月块 + 连续/累计阶梯）
                    SignInClientData.updateConfig(
                        message.isCfgIncrementEnabled(),
                        message.getCfgIncrement(),
                        message.getCfgWeekday(),
                        message.getCfgWeekend(),
                        message.getCfgDayOverrides(),
                        message.getCfgTiers(),
                        message.getCfgCumulativeTiers());
                    // 刷新在线时长配置缓存（v1.7.6 G2③）
                    SignInClientData.updateOnlineConfig(message.getCfgOnlineTiers());
                });
        }
    }
}
