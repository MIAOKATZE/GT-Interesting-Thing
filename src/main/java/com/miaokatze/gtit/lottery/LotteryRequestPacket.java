package com.miaokatze.gtit.lottery;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 抽奖请求包（客户端→服务端）
 * <p>
 * 负载：卡池 ID + 连抽次数 + 触发机器坐标/维度（物品奖品出货槽定位——
 * 奖品弹入该机器的出货槽，与贸易产出同机处理）。
 * <p>
 * 服务端收到后经 {@link LotteryHandler#scheduleServerTask} 投递到服务器主线程执行
 * （1.7.10 的包处理器运行在 Netty 线程，不能直接操作钱包/背包/机器）。
 * 抽奖判定完全在服务端权威执行（{@link LotteryManager#drawLottery}），客户端仅表现。
 */
public class LotteryRequestPacket implements IMessage {

    /** 单包最大连抽次数（防恶意包刷爆服务器，10 连为 GUI 上限） */
    private static final int MAX_COUNT = 10;

    private String poolId = "";
    private int count = 1;
    /** 触发机器坐标（出货槽定位） */
    private int machineX;
    private int machineY;
    private int machineZ;
    /** 触发机器维度 ID */
    private int machineDim;

    public LotteryRequestPacket() {
        // 反序列化需要无参构造
    }

    public LotteryRequestPacket(String poolId, int count, int x, int y, int z, int dim) {
        this.poolId = poolId == null ? "" : poolId;
        this.count = count;
        this.machineX = x;
        this.machineY = y;
        this.machineZ = z;
        this.machineDim = dim;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.poolId = ByteBufUtils.readUTF8String(buf);
        this.count = buf.readInt();
        this.machineX = buf.readInt();
        this.machineY = buf.readInt();
        this.machineZ = buf.readInt();
        this.machineDim = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.poolId);
        buf.writeInt(this.count);
        buf.writeInt(this.machineX);
        buf.writeInt(this.machineY);
        buf.writeInt(this.machineZ);
        buf.writeInt(this.machineDim);
    }

    public String getPoolId() {
        return poolId;
    }

    public int getCount() {
        return count;
    }

    public static class Handler implements IMessageHandler<LotteryRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(LotteryRequestPacket message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // 切到服务器主线程执行抽奖逻辑（涉及钱包扣费、物品出货、NBT 持久化）
            LotteryHandler.scheduleServerTask(() -> processLottery(player, message));
            return null;
        }

        /**
         * 服务器主线程：校验 → 抽奖 → 回发结果与全量同步
         * <p>
         * 失败路径（余额不足/卡池缺失/机器无效）同样回发结果码，
         * 客户端据此显示提示文本（不启动轮盘动画）。
         */
        private void processLottery(EntityPlayerMP player, LotteryRequestPacket request) {
            UUID playerId = player.getUniqueID();
            String poolId = request.poolId;
            // 连抽数钳制（防恶意包）
            int count = Math.max(1, Math.min(MAX_COUNT, request.count));

            // 1. 卡池校验
            LotteryPool pool = LotteryManager.INSTANCE.getPool(poolId);
            if (pool == null || !pool.validate()) {
                sendFailure(player, poolId, LotteryClientData.RESULT_POOL_MISSING, "卡池不存在或暂不可用");
                return;
            }

            // 2. 定位触发机器（物品奖品出货槽 + 物品消耗来源；坐标无效时退化为直接给玩家，
            // 但含物品消耗的池会在下一步预校验被拒）
            MTENekoVendingMachineV2 machine = LotteryManager
                .findMachine(request.machineDim, request.machineX, request.machineY, request.machineZ);

            // 3. 消耗预校验（v1.7.6 costItems 分流口径：货币条目查团队钱包余额、
            // 物品条目在机器输入槽副本模拟扣除；失败时给出明确错误码）
            if (!LotteryManager.INSTANCE.canAfford(playerId, pool, count, machine)) {
                sendFailure(player, poolId, LotteryClientData.RESULT_INSUFFICIENT, "消耗不足（猫猫币或需求物品不够）");
                return;
            }

            // 4. 服务端权威抽取（扣费分流 → 逐抽含保底 → 记历史 → 落盘 → 调度延迟出货）
            List<LotteryDrawResult> results = LotteryManager.INSTANCE.drawLottery(playerId, poolId, count, machine);
            if (results.isEmpty()) {
                sendFailure(player, poolId, LotteryClientData.RESULT_ERROR, "抽奖失败，请稍后再试");
                return;
            }

            // 5. 回发抽取结果（客户端启动轮盘动画）。
            // v1.7.8 起不再立即全量同步：保底/历史/余额刷新移入 LotteryManager.dispatchAll
            // 末尾（延迟出货完成后），避免动画旋转期间同步包提前剧透。
            LotteryNetworkManager.sendResultToClient(player, poolId, results, LotteryClientData.RESULT_SUCCESS);
        }

        /** 失败回执：结果码 + 聊天提示 + 状态同步（保底/历史未变但仍刷新卡池配置） */
        private void sendFailure(EntityPlayerMP player, String poolId, int resultCode, String reason) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[猫猫扭蛋] " + reason));
            LotteryNetworkManager.sendResultToClient(player, poolId, java.util.Collections.emptyList(), resultCode);
            LotteryNetworkManager.sendSyncToClient(player);
        }
    }
}
