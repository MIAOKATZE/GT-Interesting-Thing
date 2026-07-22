package com.miaokatze.gtit.signin;

import java.util.ArrayList;
import java.util.List;

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
 * + 服务端签到配置快照（v1.7.0 目标 5：基础奖励/连续系数/阶梯列表）。
 * 登录、签到后、跨日修正、管理员指令修改、配置编辑保存、/gtit nekovm sync 后均由服务端主动推送，
 * 客户端写入 {@link SignInClientData} 缓存供 {@link SignInCalendarGui} 渲染。
 */
public class SignInSyncPacket implements IMessage {

    /** 签到数据 NBT（{@link DailySignInData#writeToNBT()} 产物） */
    private NBTTagCompound dataTag;
    /** 服务端「今天」（yyyy-MM-dd，统一客户端日期口径） */
    private String serverToday = "";
    /** 签到结果（{@link SignInClientData#RESULT_NONE} 表示纯状态刷新） */
    private int result = SignInClientData.RESULT_NONE;
    /** 本次签到基础奖励（result 为 SUCCESS 时有效） */
    private int baseReward;
    /** 本次触发的阶梯奖励天数（0 表示未触发） */
    private int tierDays;

    // ==================== 服务端配置快照（v1.7.0 目标 5） ====================

    /** 服务端每日基础奖励 */
    private int cfgBaseReward;
    /** 服务端连续天数奖励系数 */
    private double cfgIncrement;
    /** 服务端阶梯奖励列表 */
    private List<SignInRewardTier> cfgTiers = new ArrayList<>();

    public SignInSyncPacket() {
        // 反序列化需要无参构造
    }

    /**
     * 构造同步包（服务端调用，配置快照取自服务端权威的 {@link DailySignInConfig}）
     */
    public SignInSyncPacket(DailySignInData data, String serverToday, int result, int baseReward, int tierDays) {
        this.dataTag = data != null ? data.writeToNBT() : new NBTTagCompound();
        this.serverToday = serverToday == null ? "" : serverToday;
        this.result = result;
        this.baseReward = baseReward;
        this.tierDays = tierDays;
        // 配置快照：服务端权威值（v1.7.0 目标 5）
        this.cfgBaseReward = DailySignInConfig.getBaseRewardNeko();
        this.cfgIncrement = DailySignInConfig.getConsecutiveIncrement();
        this.cfgTiers = new ArrayList<>(DailySignInConfig.getRewardTiers());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.dataTag = ByteBufUtils.readTag(buf);
        this.serverToday = ByteBufUtils.readUTF8String(buf);
        this.result = buf.readInt();
        this.baseReward = buf.readInt();
        this.tierDays = buf.readInt();
        // 配置快照
        this.cfgBaseReward = buf.readInt();
        this.cfgIncrement = buf.readDouble();
        int tierCount = buf.readInt();
        this.cfgTiers = new ArrayList<>(Math.max(0, tierCount));
        for (int i = 0; i < tierCount; i++) {
            int days = buf.readInt();
            String currencyId = ByteBufUtils.readUTF8String(buf);
            int amount = buf.readInt();
            String itemId = ByteBufUtils.readUTF8String(buf);
            int itemAmount = buf.readInt();
            int itemMeta = buf.readInt();
            this.cfgTiers.add(new SignInRewardTier(days, currencyId, amount, itemId, itemAmount, itemMeta));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, this.dataTag == null ? new NBTTagCompound() : this.dataTag);
        ByteBufUtils.writeUTF8String(buf, this.serverToday == null ? "" : this.serverToday);
        buf.writeInt(this.result);
        buf.writeInt(this.baseReward);
        buf.writeInt(this.tierDays);
        // 配置快照
        buf.writeInt(this.cfgBaseReward);
        buf.writeDouble(this.cfgIncrement);
        List<SignInRewardTier> tiers = this.cfgTiers == null ? new ArrayList<>() : this.cfgTiers;
        buf.writeInt(tiers.size());
        for (SignInRewardTier tier : tiers) {
            buf.writeInt(tier.getRequiredDays());
            ByteBufUtils.writeUTF8String(buf, tier.getCurrencyId() == null ? "" : tier.getCurrencyId());
            buf.writeInt(tier.getCurrencyAmount());
            ByteBufUtils.writeUTF8String(buf, tier.getItemRewardId() == null ? "" : tier.getItemRewardId());
            buf.writeInt(tier.getItemRewardAmount());
            buf.writeInt(tier.getItemRewardMeta());
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

    public int getCfgBaseReward() {
        return cfgBaseReward;
    }

    public double getCfgIncrement() {
        return cfgIncrement;
    }

    public List<SignInRewardTier> getCfgTiers() {
        return cfgTiers;
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
                    // 刷新客户端配置缓存（v1.7.0 目标 5：服务端配置同步）
                    SignInClientData.updateConfig(
                        message.getCfgBaseReward(),
                        message.getCfgIncrement(),
                        message.getCfgTiers());
                });
        }
    }
}
