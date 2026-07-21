package com.miaokatze.gtit.lottery;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * 抽奖结果包（服务端→客户端）
 * <p>
 * 服务端 {@link LotteryManager#drawLottery} 完成后下发，携带：
 * <ul>
 * <li>卡池 ID + 结果码（成功/余额不足/卡池缺失/其他错误）</li>
 * <li>本次抽取结果列表（每条：条目 ID、稀有度、数量、保底标记、高稀有标记、轮盘停格索引）</li>
 * </ul>
 * 客户端写入 {@link LotteryClientData#updateDrawResult}，{@link LotteryGui} 检测到
 * 未消费的新结果后启动轮盘动画（停格索引即动画落点）；失败结果码仅显示提示文本。
 * <p>
 * 奖品图标由客户端按条目 ID 从已同步的卡池摘要解析（{@link LotteryEntry#getDisplayStack}），
 * 本包不重复传输 ItemStack。
 */
public class LotteryResultPacket implements IMessage {

    private String poolId = "";
    private int resultCode = LotteryClientData.RESULT_NONE;
    private List<LotteryClientData.DrawResult> results = new ArrayList<>();

    public LotteryResultPacket() {
        // 反序列化需要无参构造
    }

    /**
     * 构建结果包（服务端）
     *
     * @param poolId      抽取卡池 ID
     * @param drawResults {@link LotteryManager#drawLottery} 产出（失败时传空列表）
     * @param resultCode  结果码（{@link LotteryClientData#RESULT_SUCCESS} 等）
     */
    public LotteryResultPacket(String poolId, List<LotteryDrawResult> drawResults, int resultCode) {
        this.poolId = poolId == null ? "" : poolId;
        this.resultCode = resultCode;
        this.results = new ArrayList<>();
        if (drawResults != null) {
            for (LotteryDrawResult draw : drawResults) {
                if (draw == null || draw.getEntry() == null) continue;
                LotteryClientData.DrawResult data = new LotteryClientData.DrawResult();
                data.entryId = draw.getEntry()
                    .getId();
                data.rarityName = draw.getEntry()
                    .getRarity()
                    .name();
                data.amount = draw.getAmount();
                data.isPity = draw.isPity();
                data.isHighRarity = draw.isHighRarity();
                data.slotIndex = draw.getSlotIndex();
                this.results.add(data);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        if (tag == null) return;
        this.poolId = tag.getString("pool");
        this.resultCode = tag.getInteger("code");
        this.results = new ArrayList<>();
        NBTTagList list = tag.getTagList("results", 10); // 10 = NBTTagCompound
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entryTag = list.getCompoundTagAt(i);
            LotteryClientData.DrawResult data = new LotteryClientData.DrawResult();
            data.entryId = entryTag.getString("entry");
            data.rarityName = entryTag.getString("rarity");
            data.amount = entryTag.getInteger("amount");
            data.isPity = entryTag.getBoolean("pity");
            data.isHighRarity = entryTag.getBoolean("high");
            data.slotIndex = entryTag.getInteger("slot");
            this.results.add(data);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("pool", this.poolId);
        tag.setInteger("code", this.resultCode);
        NBTTagList list = new NBTTagList();
        for (LotteryClientData.DrawResult data : this.results) {
            NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setString("entry", data.entryId == null ? "" : data.entryId);
            entryTag.setString("rarity", data.rarityName == null ? "" : data.rarityName);
            entryTag.setInteger("amount", data.amount);
            entryTag.setBoolean("pity", data.isPity);
            entryTag.setBoolean("high", data.isHighRarity);
            entryTag.setInteger("slot", data.slotIndex);
            list.appendTag(entryTag);
        }
        tag.setTag("results", list);
        ByteBufUtils.writeTag(buf, tag);
    }

    public String getPoolId() {
        return poolId;
    }

    public int getResultCode() {
        return resultCode;
    }

    public List<LotteryClientData.DrawResult> getResults() {
        return results;
    }

    public static class Handler implements IMessageHandler<LotteryResultPacket, IMessage> {

        @Override
        public IMessage onMessage(LotteryResultPacket message, MessageContext ctx) {
            // 本包只发往客户端；切回客户端主线程写缓存（GUI 在主线程读取并启动动画）
            if (ctx.side == Side.CLIENT) {
                handleClient(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(final LotteryResultPacket message) {
            Minecraft.getMinecraft()
                .func_152344_a(
                    () -> LotteryClientData
                        .updateDrawResult(message.getPoolId(), message.getResults(), message.getResultCode()));
        }
    }
}
