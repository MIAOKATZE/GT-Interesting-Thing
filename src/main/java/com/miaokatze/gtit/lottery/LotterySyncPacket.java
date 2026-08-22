package com.miaokatze.gtit.lottery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * 抽奖数据同步包（服务端→客户端）
 * <p>
 * 携带指定玩家团队的抽奖全量状态（NBT 序列化）：
 * <ul>
 * <li>卡池摘要列表（id/名称/货币/价格/保底阈值/条目列表，供轮盘渲染）</li>
 * <li>团队保底计数（poolId → 连续未出高稀有次数）</li>
 * </ul>
 * 玩家登录、打开抽奖页（主标签选中页 2）、每次抽奖完成后由服务端主动推送，
 * 客户端写入 {@link LotteryClientData} 缓存供 {@link LotteryGui} 渲染。
 */
public class LotterySyncPacket implements IMessage {

    /** 卡池摘要/保底计数/余额的 NBT 根 */
    private NBTTagCompound dataTag = new NBTTagCompound();

    public LotterySyncPacket() {
        // 反序列化需要无参构造
    }

    /**
     * 构建同步包（服务端）
     *
     * @param pools        卡池列表（{@link LotteryManager#getAllPools()}）
     * @param pityCounters 团队保底计数快照
     * @param balances     团队钱包余额（currencyId → 数量，仅含卡池消耗币种）
     */
    public LotterySyncPacket(List<LotteryPool> pools, Map<String, Integer> pityCounters,
        Map<String, Integer> balances) {
        NBTTagCompound root = new NBTTagCompound();

        // --- 卡池摘要 ---
        NBTTagList poolList = new NBTTagList();
        if (pools != null) {
            for (LotteryPool pool : pools) {
                if (pool == null) continue;
                NBTTagCompound poolTag = new NBTTagCompound();
                poolTag.setString("id", pool.getId() == null ? "" : pool.getId());
                poolTag.setString("name", pool.getName() == null ? "" : pool.getName());
                poolTag.setString("currency", pool.getNekoCurrencyId() == null ? "" : pool.getNekoCurrencyId());
                poolTag.setInteger("cost", pool.getCostPerDraw());
                // page 图标（v1.7.6）
                poolTag.setString("iconItem", pool.getIconItem());
                poolTag.setInteger("iconMeta", pool.getIconMeta());
                poolTag.setString("iconNbt", pool.getIconNbt());
                // 需求物品列表（v1.7.6 货币解绑，NekoBigItemStack NBT 列表）
                NBTTagList costList = new NBTTagList();
                for (com.miaokatze.gtit.trade.v2.NekoBigItemStack cost : pool.getCostItems()) {
                    if (cost != null && cost.getBaseStack() != null) {
                        costList.appendTag(cost.writeToNBT());
                    }
                }
                poolTag.setTag("costItems", costList);
                // 保底摘要（客户端展示 + v1.7.6 池编辑面板数值填充）
                PityConfig pity = pool.getPityConfig();
                poolTag.setBoolean("pityEnabled", pity.isEnabled());
                poolTag.setInteger("softPity", pity.getSoftPityThreshold());
                poolTag.setDouble("softPityInc", pity.getSoftPityIncrement());
                poolTag.setInteger("hardPity", pity.getHardPityThreshold());
                poolTag.setString(
                    "guaranteed",
                    pity.getGuaranteedRarity()
                        .name());
                // 条目列表（顺序即轮盘槽位顺序；v1.7.7 G3② 截断防御，防止第三端篡改导致越界）
                NBTTagList entryList = new NBTTagList();
                List<LotteryEntry> entries = pool.getEntries();
                int entryLimit = Math.min(entries.size(), LotteryPool.MAX_ENTRIES);
                for (int i = 0; i < entryLimit; i++) {
                    LotteryEntry entry = entries.get(i);
                    if (entry != null) {
                        entryList.appendTag(entry.writeToNBT());
                    }
                }
                poolTag.setTag("entries", entryList);
                poolList.appendTag(poolTag);
            }
        }
        root.setTag("pools", poolList);

        // --- 保底计数 ---
        NBTTagCompound pityTag = new NBTTagCompound();
        if (pityCounters != null) {
            for (Map.Entry<String, Integer> e : pityCounters.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    pityTag.setInteger(e.getKey(), e.getValue());
                }
            }
        }
        root.setTag("pity", pityTag);

        // v1.7.9：中奖记录功能已移除，同步包不再携带历史

        // --- 团队钱包余额（卡池消耗币种） ---
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

    public NBTTagCompound getDataTag() {
        return dataTag;
    }

    // ==================== 客户端解析 ====================

    /** 解析卡池摘要列表 */
    public List<LotteryClientData.PoolSummary> parsePools() {
        List<LotteryClientData.PoolSummary> result = new ArrayList<>();
        if (dataTag == null || !dataTag.hasKey("pools")) return result;
        NBTTagList poolList = dataTag.getTagList("pools", 10);
        for (int i = 0; i < poolList.tagCount(); i++) {
            NBTTagCompound poolTag = poolList.getCompoundTagAt(i);
            LotteryClientData.PoolSummary summary = new LotteryClientData.PoolSummary();
            summary.id = poolTag.getString("id");
            summary.name = poolTag.getString("name");
            summary.currencyId = poolTag.getString("currency");
            summary.costPerDraw = poolTag.getInteger("cost");
            // page 图标（v1.7.6）
            summary.iconItem = poolTag.getString("iconItem");
            summary.iconMeta = poolTag.getInteger("iconMeta");
            summary.iconNbt = poolTag.getString("iconNbt");
            // 需求物品列表（v1.7.6；旧包无此键时由旧字段合成，保持客户端口径一致）
            if (poolTag.hasKey("costItems")) {
                NBTTagList costList = poolTag.getTagList("costItems", 10);
                for (int j = 0; j < costList.tagCount(); j++) {
                    com.miaokatze.gtit.trade.v2.NekoBigItemStack cost = com.miaokatze.gtit.trade.v2.NekoBigItemStack
                        .loadFromNBT(costList.getCompoundTagAt(j));
                    if (cost != null && cost.getBaseStack() != null && cost.getStackSize() > 0) {
                        summary.costItems.add(cost);
                    }
                }
            }
            if (summary.costItems.isEmpty() && !summary.currencyId.isEmpty() && summary.costPerDraw > 0) {
                net.minecraft.item.ItemStack currencyStack = com.miaokatze.gtit.currency.NekoCurrencyRegistrar
                    .getItemStack(summary.currencyId, summary.costPerDraw);
                if (currencyStack != null) {
                    summary.costItems.add(new com.miaokatze.gtit.trade.v2.NekoBigItemStack(currencyStack));
                }
            }
            summary.pityEnabled = poolTag.getBoolean("pityEnabled");
            // 软保底（旧包无键时回退 PityConfig 默认值，与服务端默认配置口径一致）
            summary.softPityThreshold = poolTag.hasKey("softPity") ? poolTag.getInteger("softPity") : 30;
            summary.softPityIncrement = poolTag.hasKey("softPityInc") ? poolTag.getDouble("softPityInc") : 5.0;
            summary.hardPityThreshold = poolTag.getInteger("hardPity");
            summary.guaranteedRarity = poolTag.getString("guaranteed");
            NBTTagList entryList = poolTag.getTagList("entries", 10);
            for (int j = 0; j < entryList.tagCount(); j++) {
                LotteryEntry entry = LotteryEntry.fromNBT(entryList.getCompoundTagAt(j));
                if (entry != null) {
                    summary.entries.add(entry);
                }
            }
            result.add(summary);
        }
        return result;
    }

    /** 解析保底计数表 */
    public Map<String, Integer> parsePityCounters() {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (dataTag == null || !dataTag.hasKey("pity")) return result;
        NBTTagCompound pityTag = dataTag.getCompoundTag("pity");
        for (String key : pityTag.func_150296_c()) { // getKeySet 的 SRG 名
            result.put(key, pityTag.getInteger(key));
        }
        return result;
    }

    /** 解析团队钱包余额表（currencyId → 数量） */
    public Map<String, Integer> parseBalances() {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (dataTag == null || !dataTag.hasKey("balances")) return result;
        NBTTagCompound balanceTag = dataTag.getCompoundTag("balances");
        for (String key : balanceTag.func_150296_c()) { // getKeySet 的 SRG 名
            result.put(key, balanceTag.getInteger(key));
        }
        return result;
    }

    public static class Handler implements IMessageHandler<LotterySyncPacket, IMessage> {

        @Override
        public IMessage onMessage(LotterySyncPacket message, MessageContext ctx) {
            // 本包只发往客户端；1.7.10 的 onMessage 运行在 Netty 线程，
            // 需切回客户端主线程再写缓存（GUI 在主线程读取）
            if (ctx.side == Side.CLIENT) {
                handleClient(message);
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(final LotterySyncPacket message) {
            // O2-B01：余额维度写入 trade 域客户端缓存（卡池摘要/保底计数仍写 LotteryClientData）
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    LotteryClientData.updatePools(message.parsePools(), message.parsePityCounters());
                    com.miaokatze.gtit.trade.NekoClientBalances.updateBalances(message.parseBalances());
                });
        }
    }
}
