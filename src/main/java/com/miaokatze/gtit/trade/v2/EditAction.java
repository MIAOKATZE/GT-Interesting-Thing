package com.miaokatze.gtit.trade.v2;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 编辑 ACTION 统一策略接口（O2-05 策略表化）
 * <p>
 * 15 个编辑 ACTION 呈「解析→校验 sendError→配置落盘+后置→sendSuccess」统一四段流水，
 * 本接口以统一 4 参签名覆盖全部分发差异；各 ACTION 实现保持原方法签名逐字搬移，
 * 参数差异（openTradeEditor 用 targetIndex、createTrade 把 targetId 当 tabId、
 * deleteXxx 无 payload、createXxx 仅用 payload 等）由 {@link EditActionRegistry}
 * 注册处的方法引用/lambda 适配消化。
 * <p>
 * 实现均在服务器主线程执行（{@code scheduleServerTask} 保证），且必经
 * {@link NekoEditActionHandler#validateEditRequest} 统一校验入口之后；
 * 各域后置动作（sendSyncToAll 广播 / 注册表热重载 / 无重载）不统一，由各策略自带。
 */
interface EditAction {

    /**
     * 执行编辑操作
     *
     * @param player      发起玩家（服务器主线程）
     * @param targetId    目标标识（各 ACTION 语义不同：交易组 UUID / 签到天数键 /
     *                    {@code "<poolId>:<entryId>"} / pageId / 档位秒数字符串等）
     * @param targetIndex 目标索引（仅交易条目编辑使用，其余 ACTION 忽略）
     * @param jsonPayload JSON 序列化的编辑参数（无载荷 ACTION 忽略）
     */
    void execute(EntityPlayerMP player, String targetId, int targetIndex, String jsonPayload);
}
