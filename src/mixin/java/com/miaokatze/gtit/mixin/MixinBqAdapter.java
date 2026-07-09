package com.miaokatze.gtit.mixin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 BqAdapter.setQuestUnfinished 的 NPE 服务端崩溃。
 * <p>
 * 原版 BqAdapter.setQuestUnfinished 直接迭代 questUpdateTriggers.get(quest)，
 * 当 quest 未注册任何触发器（如未绑定 VM/NekoVM 交易的普通 BQ 任务被 RESET）时，
 * Map.get() 返回 null，for-each 迭代 null 抛出 NullPointerException 导致服务端崩溃。
 * 对比 setQuestFinished 有 containsKey 守卫，setQuestUnfinished 缺失该守卫。
 * <p>
 * 本 Mixin 在方法 HEAD 处拦截：若 quest 不在 questUpdateTriggers 中，
 * 清理 playerSatisfiedCache 中该 quest 的残留条目后取消原方法执行，
 * 避免 NPE 并保持缓存一致性（处理 triggers 被热重载清除后任务重置的边缘场景）。
 * <p>
 * 使用 targets 字符串形式而非 @Mixin(BqAdapter.class)，避免 Mixin 类加载时
 * 触发 BqAdapter 链式类加载导致 BQ 未就绪崩溃，与 MixinMTEVendingMachine
 * 不直接 import BqAdapter 的设计一致。@Shadow 字段使用不引用 TradeGroup 的
 * 泛型参数（Set&lt;?&gt;），避免引入 VM 类的编译期依赖。
 */
@Mixin(targets = "com.cubefury.vendingmachine.integration.betterquesting.BqAdapter", priority = 1000)
public class MixinBqAdapter {

    // questUpdateTriggers 实际类型为 Map<UUID, Set<TradeGroup>>，此处用 Set<?> 规避 TradeGroup import
    @Shadow(remap = false)
    private Map<UUID, Set<?>> questUpdateTriggers;

    @Shadow(remap = false)
    private Map<UUID, Set<UUID>> playerSatisfiedCache;

    @Inject(method = "setQuestUnfinished", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtit$guardSetQuestUnfinished(UUID player, UUID quest, CallbackInfo ci) {
        if (quest == null) {
            ci.cancel();
            return;
        }
        // quest 未注册任何触发器：原方法会 NPE，此处清理缓存后取消
        if (!this.questUpdateTriggers.containsKey(quest)) {
            synchronized (this.playerSatisfiedCache) {
                Set<UUID> satisfied = this.playerSatisfiedCache.get(player);
                if (satisfied != null) {
                    satisfied.remove(quest);
                }
            }
            ci.cancel();
        }
    }
}
