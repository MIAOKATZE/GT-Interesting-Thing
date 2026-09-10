package com.miaokatze.gtit.reincarnation.core;

/**
 * 轮回奖励发放门控（纯逻辑判定）：把发放条件收紧为
 * 「严格单机 + 新存档首次登录（或本档发放续跑）+ 本档未领取」中的后两项纯信号判定
 * （严格单机由调用方 {@code ReincarnationHandler.isStrictSinglePlayer()} 前置把关，
 * 本类只做零 MC 类型的纯决策，与 {@link ReincarnationSaveGuard} 同红线：
 * final 类、私有构造、静态纯函数、零 client 类型、零 {@code @SideOnly}，
 * 可被零依赖测试套件（{@code ReincarnationGrantGateTest}）直接覆盖）。
 * <p>
 * <b>playTicks 一次性语义</b>：{@code playerTotalPlayTicks} 由调用方在<b>登录瞬间一次性
 * 读取</b>（stat.playOneMinute 原始累计 tick，0 ⇔ 本档从未游玩 = 首次登录；统计文件
 * 丢失/损坏按 0，形态兜底由 {@code grantStartedHere} 续跑旁路承担）。判定只在登录点做
 * 一次，<b>调用方不得在延迟复核点重读该信号</b>（见下）。
 * <p>
 * <b>时间信号删除理由</b>（v1.8.6 → 门控修订）：v1.8.6 采用
 * {@code overworldTotalWorldTime < 1200} 阈值作第二信号，真实运行中被证伪——登录后
 * 玩家实体每 tick 累计 {@code stat.playOneMinute}（时变信号），登录分支判定通过并调度
 * +200t 的 {@code executeGrant} 重入复核时重读该信号，实测 playTicks 已漂移到 198，
 * 新存档首登发放被二次判定误拦（日志实证）；慢机世界生成超过阈值窗口属同类误杀形态。
 * 故删除 overworld 总计时阈值（{@code NEW_SAVE_MAX_TOTAL_WORLD_TIME} 连同
 * {@code totalWorldTime} 参数一并移除），改由登录点一次读取的 playTicks + 每存档
 * {@code GrantStarted} 标记（崩溃后同档重进走 {@code grantStartedHere} 续跑旁路）
 * 共同承担形态判定。
 */
public final class ReincarnationGrantGate {

    private ReincarnationGrantGate() {}

    /**
     * 发放判定：允许发放当且仅当——存在待领取轮回奖励（{@code canClaim}）、
     * 本存档未领取过（{@code !grantClaimed}）、且形态满足其一：本档发放已启动过
     * （{@code grantStartedHere} 续跑旁路）或玩家累计游玩 tick 为 0（新档首登）。
     *
     * @param canClaim             轮回许可链路判定（EXECUTED 待领取，调用方已核）
     * @param grantClaimed         每存档"已领取"标记（{@code ReincarnationWorldData.isGrantClaimed}）
     * @param grantStartedHere     每存档"发放已启动"承诺标记（{@code ReincarnationWorldData.isGrantStarted}，
     *                             NBT 键 {@code GrantStarted}；true = 本档曾进入发放流程，
     *                             崩溃/登出重进按续跑放行，不依赖时变信号）
     * @param playerTotalPlayTicks 玩家累计游玩 tick（登录瞬间一次性读取的
     *                             stat.playOneMinute 原始值，0 ⇔ 本档从未游玩 = 首次登录；缺失按 0）
     * @return true = 通过门控可发放；false = 拦截（调用方审计日志 + 信箱保留）
     */
    public static boolean shouldGrant(boolean canClaim, boolean grantClaimed, boolean grantStartedHere,
        long playerTotalPlayTicks) {
        return canClaim && !grantClaimed && (grantStartedHere || playerTotalPlayTicks == 0);
    }
}
