package com.miaokatze.gtit.common.machine.v2;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ★R100 片 F（架构解耦）：猫猫售货机 C2S 服务端动作队列（原 {@code NekoVMGuiV2} 的
 * {@code SERVER_ACTIONS} / {@code scheduleServerAction} / {@code drainServerActions} 三件逐字迁入）。
 * <p>
 * 迁移理由：队列的<b>生产者</b>（GUI C2S 同步值的服务端迁移点）与<b>消费者</b>
 * （{@link MTENekoVendingMachineV2#onPostTick} 服务端分支）都在 common 侧，唯一住在 gui 侧的
 * 只是这段公共设施本身——它搬走后 TE 不再 import gui 包（依赖方向唯一允许 gui→common）。
 * 方法体与队列语义逐字未改：Netty IO 线程可投递、主线程逐 tick 消费、单任务异常仅记日志不中断同批。
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

    /**
     * B2-02：将 C2S 同步值的服务端动作主体投递到服务器主线程（Netty 线程调用安全）。
     * <p>
     * 仅服务端侧调用；客户端侧的 changeListener 保持原语义直跑（无服务端动作）。
     */
    public static void scheduleServerAction(Runnable action) {
        if (action != null) {
            SERVER_ACTIONS.offer(action);
        }
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
