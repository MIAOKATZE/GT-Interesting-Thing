package com.miaokatze.gtit.lottery;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

/**
 * 抽奖请求包（客户端→服务端）
 * <p>
 * 负载：卡池 ID + 连抽次数 + 触发机器坐标/维度（物品奖品出货槽定位——
 * 奖品弹入该机器的出货槽，与贸易产出同机处理）。
 * <p>
 * 服务端收到后经 {@link com.miaokatze.gtit.util.ServerTaskScheduler#scheduleServerTask} 投递到服务器主线程执行
 * （1.7.10 的包处理器运行在 Netty 线程，不能直接操作钱包/背包/机器）。
 * 抽奖判定完全在服务端权威执行（{@link LotteryManager#drawLottery}），客户端仅表现。
 */
public class LotteryRequestPacket implements IMessage {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 单包最大连抽次数（防恶意包刷爆服务器，10 连为 GUI 上限） */
    private static final int MAX_COUNT = 10;

    // ==================== 触发机器校验（B2-01 防盗刷） ====================

    /**
     * 抽奖触发机器与请求者的最大距离平方（8 格）。
     * <p>
     * 对齐 {@code MailActionPacket}（IT-BUG-03）的三重校验范式
     * （同维度/距离/GUI 会话绑定）与 MUI2 容器交互距离规则。
     */
    private static final double MAX_MACHINE_DISTANCE_SQ = 64.0D;

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
            ServerTaskScheduler.scheduleServerTask(() -> processLottery(player, message));
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

            // 2. 定位触发机器（B2-01：坐标客户端可控，命中前须通过同维度/距离/GUI 会话三重校验；
            // 校验失败或机器无效时整包拒绝——不退化为"无机器直接给玩家"（该退化路径对纯货币池
            // 保留绕过面，物品池本就被 canAfford 的 machine==null 分支拦截，正常流程无感）
            MTENekoVendingMachineV2 machine = findMachine(player, request);
            if (machine == null) {
                sendFailure(player, poolId, LotteryClientData.RESULT_ERROR, "抽奖请求无效（未找到可用机器）");
                return;
            }

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
            // v1.7.36：在动画触发后立即发一次全量同步——客户端余额显示走
            // LotteryClientData 静态缓存，须在同步包到达后才刷新；延迟出货期间玩家切看
            // 抽奖页不应再看到「抽完但余额未减」，此处即时刷新为正确值。延迟出货末尾的
            // 那次 sendSyncToClient 作为冗余刷新保留无害。
            LotteryNetworkManager.sendResultToClient(player, poolId, results, LotteryClientData.RESULT_SUCCESS);
            LotteryNetworkManager.sendSyncToClient(player);
        }

        /**
         * 按请求包坐标定位猫猫售货机 V2（B2-01 三重校验版，抽奖页=机器 GUI tab，
         * 正常流程必然开着对应机器 GUI，对正常玩家无感）
         * <p>
         * 参照 {@code MailActionPacket.Handler#findMachine}（IT-BUG-03）范式，命中机器前须依次通过：
         * <ol>
         * <li>同维度：请求维度必须与请求者当前世界一致（拒绝跨维度指定他人机器）</li>
         * <li>距离上限：请求者与机器距离 ≤ 8 格（{@link #MAX_MACHINE_DISTANCE_SQ}）</li>
         * <li>GUI 会话绑定：请求者当前打开的 MUI2 容器必须是这台机器的 GUI
         * （{@link #isMachineGuiOpen}）</li>
         * </ol>
         * 任一失败返回 null（调用方整包拒绝）。延迟出货路径（{@code LotteryManager.dispatchAll}）
         * 仍用无校验静态版——出货时玩家可能已关 GUI/离线，此处验证过的坐标快照即可信。
         *
         * @return 机器实例；未找到或校验失败返回 null
         */
        private MTENekoVendingMachineV2 findMachine(EntityPlayerMP player, LotteryRequestPacket request) {
            try {
                MinecraftServer server = MinecraftServer.getServer();
                if (server == null) return null;
                World world = server.worldServerForDimension(request.machineDim);
                if (world == null || world != player.worldObj) return null;
                // 距离上限（防恶意包隔空指定他人机器扣物/塞奖）
                if (player.getDistanceSq(request.machineX + 0.5D, request.machineY + 0.5D, request.machineZ + 0.5D)
                    > MAX_MACHINE_DISTANCE_SQ) return null;
                // GUI 会话绑定（请求者必须正打开这台机器的 GUI，仅持坐标不允许抽奖）
                if (!isMachineGuiOpen(player, request)) return null;
                TileEntity te = world.getTileEntity(request.machineX, request.machineY, request.machineZ);
                if (te instanceof IGregTechTileEntity) {
                    if (((IGregTechTileEntity) te).getMetaTileEntity() instanceof MTENekoVendingMachineV2) {
                        return (MTENekoVendingMachineV2) ((IGregTechTileEntity) te).getMetaTileEntity();
                    }
                }
            } catch (Exception e) {
                LOG.error("定位抽奖触发机器失败", e);
            }
            return null;
        }

        /**
         * GUI 会话绑定校验：请求者当前打开的容器是否为请求坐标机器的 MUI2 GUI
         * <p>
         * 比对 {@code player.openContainer} 携带的 {@code PosGuiData} 与请求坐标，
         * 未开 GUI/开着其他 GUI/坐标不符一律拒绝（与 {@code MailActionPacket} 同型）。
         */
        private static boolean isMachineGuiOpen(EntityPlayerMP player, LotteryRequestPacket request) {
            if (!(player.openContainer instanceof ModularContainer container)) return false;
            if (!(container.getGuiData() instanceof PosGuiData guiData)) return false;
            return guiData.getX() == request.machineX && guiData.getY() == request.machineY
                && guiData.getZ() == request.machineZ;
        }

        /**
         * 失败回执：结果码 + 聊天提示（B2-05：失败路径不再回发全量同步——客户端本地缓存
         * 未变，失败无需刷新，结果码包照发驱动提示文本；防"小请求→全池 NBT"网络放大）
         */
        private void sendFailure(EntityPlayerMP player, String poolId, int resultCode, String reason) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[猫猫扭蛋] " + reason));
            LotteryNetworkManager.sendResultToClient(player, poolId, java.util.Collections.emptyList(), resultCode);
        }
    }
}
