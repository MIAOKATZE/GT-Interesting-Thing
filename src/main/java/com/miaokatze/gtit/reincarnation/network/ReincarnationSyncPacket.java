package com.miaokatze.gtit.reincarnation.network;

import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * 周目只读状态快照包（服务端→客户端，discriminator=3）
 * <p>
 * 携带周目系统只读展示快照（GUI 只读渲染用，客户端不得据此写回任何权威状态）：
 * <ul>
 * <li>{@code cycleStateOrdinal}：周目状态枚举 ordinal（int）</li>
 * <li>{@code unlockedRows}：已解锁行数（int）</li>
 * <li>{@code hullProgress}：船体进度数组（约定长度 15）</li>
 * <li>{@code unlockedColumns}：已解锁列标记数组（约定长度 15）</li>
 * <li>{@code pendingItemsCount}：待领取发放物品数（int）</li>
 * <li>{@code mutextLock}：是否存在未领取轮回（boolean 互斥锁位）</li>
 * </ul>
 * 客户端 handler 构建不可变快照落入 {@link ClientReincarnationFxState}。
 * 数组按「长度字节 + 元素」序列化并带防御上限，读到超限长度按截断处理。
 * 本类（含 Handler）不引用任何 net.minecraft.client 类型，仅经 ClientProxy 注册路径可达。
 */
public class ReincarnationSyncPacket implements IMessage {

    /** 数组长度防御上限（协议约定 15，超限截断防损坏包放大分配） */
    private static final int MAX_ARRAY_LEN = 64;

    private int cycleStateOrdinal = 0;
    private int unlockedRows = 0;
    private int[] hullProgress = new int[0];
    private boolean[] unlockedColumns = new boolean[0];
    private int pendingItemsCount = 0;
    private boolean mutextLock = false;

    public ReincarnationSyncPacket() {
        // 反序列化需要无参构造
    }

    /**
     * 构建状态快照包（服务端）
     *
     * @param cycleStateOrdinal 周目状态枚举 ordinal
     * @param unlockedRows      已解锁行数
     * @param hullProgress      船体进度（约定长度 15；null 按空数组处理）
     * @param unlockedColumns   已解锁列标记（约定长度 15；null 按空数组处理）
     * @param pendingItemsCount 待领取发放物品数
     * @param mutextLock        是否存在未领取轮回
     */
    public ReincarnationSyncPacket(int cycleStateOrdinal, int unlockedRows, int[] hullProgress,
        boolean[] unlockedColumns, int pendingItemsCount, boolean mutextLock) {
        this.cycleStateOrdinal = cycleStateOrdinal;
        this.unlockedRows = unlockedRows;
        this.hullProgress = hullProgress == null ? new int[0] : hullProgress;
        this.unlockedColumns = unlockedColumns == null ? new boolean[0] : unlockedColumns;
        this.pendingItemsCount = pendingItemsCount;
        this.mutextLock = mutextLock;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.cycleStateOrdinal = buf.readInt();
        this.unlockedRows = buf.readInt();
        this.hullProgress = readIntArray(buf);
        this.unlockedColumns = readBooleanArray(buf);
        this.pendingItemsCount = buf.readInt();
        this.mutextLock = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.cycleStateOrdinal);
        buf.writeInt(this.unlockedRows);
        writeIntArray(buf, this.hullProgress);
        writeBooleanArray(buf, this.unlockedColumns);
        buf.writeInt(this.pendingItemsCount);
        buf.writeBoolean(this.mutextLock);
    }

    private static void writeIntArray(ByteBuf buf, int[] values) {
        int len = values == null ? 0 : Math.min(values.length, MAX_ARRAY_LEN);
        buf.writeByte(len);
        for (int i = 0; i < len; i++) {
            buf.writeInt(values[i]);
        }
    }

    private static int[] readIntArray(ByteBuf buf) {
        int len = buf.readByte();
        int safe = Math.max(0, Math.min(len, MAX_ARRAY_LEN));
        int[] values = new int[safe];
        for (int i = 0; i < safe; i++) {
            values[i] = buf.readInt();
        }
        return values;
    }

    private static void writeBooleanArray(ByteBuf buf, boolean[] values) {
        int len = values == null ? 0 : Math.min(values.length, MAX_ARRAY_LEN);
        buf.writeByte(len);
        for (int i = 0; i < len; i++) {
            buf.writeBoolean(values[i]);
        }
    }

    private static boolean[] readBooleanArray(ByteBuf buf) {
        int len = buf.readByte();
        int safe = Math.max(0, Math.min(len, MAX_ARRAY_LEN));
        boolean[] values = new boolean[safe];
        for (int i = 0; i < safe; i++) {
            values[i] = buf.readBoolean();
        }
        return values;
    }

    public int getCycleStateOrdinal() {
        return cycleStateOrdinal;
    }

    public int getUnlockedRows() {
        return unlockedRows;
    }

    public int[] getHullProgress() {
        return hullProgress;
    }

    public boolean[] getUnlockedColumns() {
        return unlockedColumns;
    }

    public int getPendingItemsCount() {
        return pendingItemsCount;
    }

    public boolean isMutextLock() {
        return mutextLock;
    }

    public static class Handler implements IMessageHandler<ReincarnationSyncPacket, IMessage> {

        @Override
        public IMessage onMessage(ReincarnationSyncPacket message, MessageContext ctx) {
            // 本包只发往客户端；handler 在 Netty 线程执行，仅写 volatile holder（GUI 主线程读取）
            if (ctx.side == Side.CLIENT) {
                ClientReincarnationFxState.updateSync(
                    message.cycleStateOrdinal,
                    message.unlockedRows,
                    message.hullProgress,
                    message.unlockedColumns,
                    message.pendingItemsCount,
                    message.mutextLock);
                GTInterestingThing.LOG.info(
                    "[reincarnation] 收到周目状态快照：cycleState=" + message.cycleStateOrdinal
                        + "，unlockedRows="
                        + message.unlockedRows
                        + "，pendingItems="
                        + message.pendingItemsCount
                        + "，mutextLock="
                        + message.mutextLock);
            }
            return null;
        }
    }
}
