package com.miaokatze.gtit.trade;

import java.util.LinkedHashMap;
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
 * 钱包余额轻量同步包（服务端→客户端，O2-B01 通道自立）
 * <p>
 * 只携带团队/个人钱包余额（currencyId → 数量），不带卡池摘要与保底计数——
 * 钱包余额变化（入账/扣费/投币）不触发抽奖全量推送，由 {@link NekoWalletManager}
 * 以脏标记 + 约 100ms 节流合帧后经 {@link WalletNetworkManager} 批量下发本包，
 * 消除批量投币/连抽场景对全队在线成员的 m×n 全量包风暴。
 * <p>
 * 原实现寄居抽奖通道（LotteryBalancePacket，id=3），O2-B01 起自立为 trade 域
 * 专用通道，消除钱包核心→抽奖网络的域间反向；编码格式与原包一致
 * （NBT tag "balances"，与 LotterySyncPacket 的 balances 节同构）。
 * 客户端写入 {@link NekoClientBalances}（仅覆盖 balances 维度）。
 */
public class WalletBalancePacket implements IMessage {

    /** 余额表的 NBT 根 */
    private NBTTagCompound dataTag = new NBTTagCompound();

    public WalletBalancePacket() {
        // 反序列化需要无参构造
    }

    /**
     * 构建余额轻量包（服务端）
     *
     * @param balances 钱包余额（currencyId → 数量）
     */
    public WalletBalancePacket(Map<String, Integer> balances) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound balanceTag = new NBTTagCompound();
        if (balances != null) {
            for (Map.Entry<String, Integer> e : balances.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    balanceTag.setInteger(e.getKey(), e.getValue());
                }
            }
        }
        root.setTag("balances", balanceTag);
        this.dataTag = root;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.dataTag = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, this.dataTag == null ? new NBTTagCompound() : this.dataTag);
    }

    /** 解析余额表（currencyId → 数量） */
    public Map<String, Integer> parseBalances() {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (dataTag == null || !dataTag.hasKey("balances")) return result;
        NBTTagCompound balanceTag = dataTag.getCompoundTag("balances");
        for (String key : balanceTag.func_150296_c()) { // getKeySet 的 SRG 名
            result.put(key, balanceTag.getInteger(key));
        }
        return result;
    }

    public static class Handler implements IMessageHandler<WalletBalancePacket, IMessage> {

        @Override
        public IMessage onMessage(WalletBalancePacket message, MessageContext ctx) {
            // 本包只发往客户端；1.7.10 的 onMessage 运行在 Netty 线程，
            // 需切回客户端主线程再写缓存（GUI 在主线程读取）
            if (ctx.side == Side.CLIENT) {
                handleClient(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(final WalletBalancePacket message) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> NekoClientBalances.updateBalances(message.parseBalances()));
        }
    }
}
