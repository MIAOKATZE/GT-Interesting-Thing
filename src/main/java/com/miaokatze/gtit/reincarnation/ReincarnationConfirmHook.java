package com.miaokatze.gtit.reincarnation;

import net.minecraft.entity.player.EntityPlayer;

/**
 * 周目「确认轮回」回调钩子（S7 GUI 切片定义，S4 实现注入；v1.9.0 C批）
 * <p>
 * GUI 的确认轮回按钮（{@code client/gui} 切片）不直接触碰状态机——点击后经
 * 二次确认弹窗转入服务端，由本钩子回调 S4 侧完成实际语义（寄存收束
 * {@code deposit → confirmReincarnation(seed)} → 时间线指纹 → 飞升演出/奖励等）。
 * 这样 GUI 切片与状态机收束切片解耦，双方只依赖本接口。
 * <p>
 * <b>注入时机</b>：S4 在周目系统初始化（proxy 接线路径）调用 {@link #setHook(ReincarnationConfirmHook)}；
 * 物理专用服务器周目系统整体拒绝注册，专用服上不注入即无回调，GUI 亦不可达（双保险）。
 * <p>
 * <b>线程与侧</b>：回调只在<b>服务端</b>逻辑路径触发（MUI2 C2S synced action），
 * 实现方在回调内做玩家/世界操作无需再判侧；实现类不得引用 {@code net.minecraft.client}。
 * 接口与 holder 本体仅依赖 {@link EntityPlayer}，common 路径可安全加载。
 * <p>
 * GUI 侧行为：{@link #getHook()} 为 null（S4 未注入）时确认按钮置灰（切片契约）。
 */
public interface ReincarnationConfirmHook {

    /**
     * 玩家已通过 GUI 二次确认，请求执行轮回。
     * <p>
     * 实现方职责（S4 契约）：校验 {@code cycleState == DEPOSITED && pendingItems 非空}，
     * 以玩家 seed 收束本轮轮回（{@code confirmReincarnation}），处理指纹/演出/奖励后续，
     * 并把结果反馈给玩家。GUI 侧不等待返回值。
     *
     * @param player 触发确认的玩家（非 null；其 UUID 即周目记录主键）
     */
    void onConfirmRequested(EntityPlayer player);

    // ==================== 注入 holder（静态） ====================

    /**
     * 钩子注入 holder（volatile 单槽）。
     * <p>
     * 以接口内嵌类形式收拢，避免为 holder 单独开公共类型；注入方（S4）与
     * 消费方（S7 GUI）均只经本 holder 存取。
     */
    final class Holder {

        /** 当前注入的钩子（null = S4 未注入，GUI 确认按钮置灰） */
        private static volatile ReincarnationConfirmHook hook;

        private Holder() {}

        /**
         * 注入/替换钩子（S4 初始化路径调用；幂等，重复注入以最后一次为准）。
         *
         * @param confirmHook 钩子实现（null 等价于 {@link #clear()}）
         */
        public static void setHook(ReincarnationConfirmHook confirmHook) {
            hook = confirmHook;
        }

        /** @return 当前钩子（null = 未注入） */
        public static ReincarnationConfirmHook getHook() {
            return hook;
        }

        /** 清除钩子（登出/关闭周目系统等场景；幂等） */
        public static void clear() {
            hook = null;
        }
    }
}
