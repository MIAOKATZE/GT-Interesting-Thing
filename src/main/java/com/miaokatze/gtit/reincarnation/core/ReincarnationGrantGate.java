package com.miaokatze.gtit.reincarnation.core;

/**
 * 轮回奖励发放门控（v1.8.6 纯逻辑判定）：把发放条件收紧为
 * 「严格单机 + 新存档首次登录 + 本档未领取」中的后两项纯信号判定
 * （严格单机由调用方 {@code ReincarnationHandler.isStrictSinglePlayer()} 前置把关，
 * 本类只做零 MC 类型的纯决策，与 {@link ReincarnationSaveGuard} 同红线：
 * final 类、私有构造、静态纯函数、零 client 类型、零 {@code @SideOnly}，
 * 可被零依赖测试套件（{@code ReincarnationGrantGateTest}）直接覆盖）。
 * <p>
 * <b>双信号互补理由</b>：仅凭"玩家累计游玩 tick == 0"不够——统计文件可能丢失/被删
 * （旧档换目录、stat 文件损坏），此时 playTicks 缺省为 0，会把老玩家误判为新档；
 * 仅凭"overworld 总计时 &lt; 阈值"也不够——旧档接手时 overworld 计时虽大，但无法区分
 * "刚建的新档"与"玩过 20 分钟的档"，须由 playTicks 信号把旧档缺统计/换档的形态排除。
 * 两信号同时满足（没玩过且世界很新）才认定"新存档首次登录"，缺一即拦截（信箱保留）。
 * <p>
 * <b>阈值语义</b>：{@link #NEW_SAVE_MAX_TOTAL_WORLD_TIME} 为<b>严格小于</b>判定——
 * {@code overworldTotalWorldTime == 1200} 即拒绝（1200 tick = 60 秒：新存档建档后的
 * 首次登录编排发生在进入世界初期，60 秒窗口足够覆盖正常首登；超过即视为已在档内游玩，
 * 不再属于"首次登录"形态）。
 */
public final class ReincarnationGrantGate {

    /**
     * 新存档判定阈值（tick，严格小于）：overworld {@code getTotalWorldTime()} 达到
     * 该值即视为非新存档（=1200 即拒绝）。
     */
    public static final long NEW_SAVE_MAX_TOTAL_WORLD_TIME = 1200L;

    private ReincarnationGrantGate() {}

    /**
     * 发放判定：允许发放当且仅当——存在待领取轮回奖励（{@code canClaim}）、
     * 本存档未领取过（{@code !grantClaimed}）、玩家累计游玩 tick 为 0
     * （新档首登；统计缺失按 0，由时间信号互补兜底）且 overworld 总计时严格小于
     * {@link #NEW_SAVE_MAX_TOTAL_WORLD_TIME}（=1200 即拒绝）。
     *
     * @param canClaim                轮回许可链路判定（EXECUTED 待领取，调用方已核）
     * @param grantClaimed            每存档"已领取"标记（{@code ReincarnationWorldData.isGrantClaimed}）
     * @param playerTotalPlayTicks    玩家累计游玩 tick（stat.playOneMinute 原始值，缺失按 0）
     * @param overworldTotalWorldTime overworld {@code getTotalWorldTime()}（累计 tick）
     * @return true = 通过门控可发放；false = 拦截（调用方审计日志 + 信箱保留）
     */
    public static boolean shouldGrant(boolean canClaim, boolean grantClaimed, long playerTotalPlayTicks,
        long overworldTotalWorldTime) {
        return canClaim && !grantClaimed
            && playerTotalPlayTicks == 0
            && overworldTotalWorldTime < NEW_SAVE_MAX_TOTAL_WORLD_TIME;
    }
}
