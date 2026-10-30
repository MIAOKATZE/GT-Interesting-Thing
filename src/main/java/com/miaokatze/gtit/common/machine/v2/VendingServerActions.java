package com.miaokatze.gtit.common.machine.v2;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.util.ServerTaskScheduler;

/**
 * ★R100 片 F（架构解耦）：猫猫售货机 C2S 服务端动作队列（原 {@code NekoVMGuiV2} 的
 * {@code SERVER_ACTIONS} / {@code scheduleServerAction} / {@code drainServerActions} 三件逐字迁入）。
 * <p>
 * 迁移理由：队列的<b>生产者</b>（GUI C2S 同步值的服务端迁移点）与<b>消费者</b>
 * （{@link MTENekoVendingMachineV2#onPostTick} 服务端分支）都在 common 侧，唯一住在 gui 侧的
 * 只是这段公共设施本身——它搬走后 TE 不再 import gui 包（依赖方向唯一允许 gui→common）。
 * C2S GUI 请求现走独立有界准入，在 server tick END 执行；不依赖机器数量或某台机器继续加载。
 * 旧无界 FIFO API 仅为兼容保留，不再有本模组生产者；不得将已承诺的交付改投到有界请求队列。
 */
public final class VendingServerActions {

    /** 与原宿主 NekoVMGuiV2 同一 logger 名（日志过滤口径不变；消息自带 [NekoVMV2] 前缀）。 */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /**
     * B2-02：C2S 同步值服务端动作的投递队列（原 NekoVMGuiV2.SERVER_ACTIONS 原样迁入）。
     * <p>
     * 共享机器槽读写（NekoTradeExecutor 快照→扣减→整槽写回）、HashMap 标志写与
     * meTransferQueue 写——与主线程 checkTrade（detectAndSendChanges 驱动）/
     * onPostTick 交叉访问存在竞态。服务端动作主体整体 offer 到本队列，
     * 由 {@link MTENekoVendingMachineV2#onPostTick} 服务端分支逐 tick 消费
     * （操作延迟 ≤1 tick，玩家无感）。1 tick 后 GUI 可能已关：闭包内引用的
     * multiblock/baseMetaTileEntity 生命周期独立于 GUI，各动作方法自带存活守卫。
     */
    private static final Queue<Runnable> SERVER_ACTIONS = new ConcurrentLinkedQueue<>();

    private VendingServerActions() {}

    /** Pre-business GUI admission, after all machine ticks have restored their active state. */
    public static boolean schedulePlayerRequest(EntityPlayerMP player, Runnable guardedAction) {
        return ServerTaskScheduler.trySchedulePlayerRequest(player, guardedAction);
    }

    /** The only current completion is a thread-safe transient scene request latch, not an obligation. */
    public static boolean schedulePlayerRequest(EntityPlayerMP player, Runnable guardedAction, Runnable completion) {
        if (guardedAction == null || completion == null) throw new IllegalArgumentException("action and completion");
        return ServerTaskScheduler.trySchedulePlayerRequest(player, guardedAction, completion);
    }

    /**
     * Legacy non-dropping FIFO. New uncommitted GUI requests must use {@link #schedulePlayerRequest}.
     * <p>
     * 仅服务端侧调用；客户端侧的 changeListener 保持原语义直跑（无服务端动作）。
     */
    @Deprecated
    public static void scheduleServerAction(Runnable action) {
        if (action != null) {
            SERVER_ACTIONS.offer(action);
        }
    }

    /** Completion belongs outside the session guard, so rejected or failed actions release their pending latch. */
    @Deprecated
    public static void scheduleServerAction(Runnable guardedAction, Runnable completion) {
        if (guardedAction == null || completion == null) throw new IllegalArgumentException("action and completion");
        scheduleServerAction(() -> {
            try {
                guardedAction.run();
            } finally {
                completion.run();
            }
        });
    }

    /**
     * B2-02：服务器主线程逐 tick 消费投递的 C2S 动作。
     * <p>
     * 由 {@link MTENekoVendingMachineV2#onPostTick} 服务端分支调用；调用点必须位于
     * {@code setActive(mMachine)} 之后（super 链每 tick 以 mMaxProgresstime>0 重置 active，
     * 先 drain 会使投递动作的 isActive() 前置守卫恒判 false，v1.7.52 修复）；单任务异常仅记日志，
     * 不中断同批其余任务（对齐 MailHandler 消费循环）。
     */
    public static void drainServerActions() {
        Runnable action;
        while ((action = SERVER_ACTIONS.poll()) != null) {
            try {
                action.run();
            } catch (Throwable t) {
                LOG.error("[NekoVMV2] 执行投递的 C2S 动作失败", t);
            }
        }
    }
}
