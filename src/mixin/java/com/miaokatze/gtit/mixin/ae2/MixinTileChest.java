package com.miaokatze.gtit.mixin.ae2;

import java.util.Map;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.MEMonitorHandler;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.storage.TileChest;

/**
 * 让 ME 箱对<b>每个已注册的 {@link IAEStackType} 各得一份互相独立的 handler</b>（真·同槽多通道）。
 * <p>
 * <b>被改的原始行为</b>（AE2 rv3-beta-1050-GTNH，{@code appeng/tile/storage/TileChest.java}）：
 * {@code getHandler(IAEStackType)} 位于 :210-240，重建分支 :211-233 内 :221-228 遍历
 * {@code AEStackTypeRegistry.getAllTypes()}，第一个非 null 结果在 :224 {@code cellMap.put(internalType, wrap(cell))}
 * 后于 :226 {@code break}，所以 {@code cellMap} 只有一个条目；:235-238 查不到请求的 type 就抛
 * {@code NO_HANDLER}，即多通道元件在 ME 箱里只服务一个通道。
 * <p>
 * <b>语义目标</b>：被问 type T 时，{@code cellMap[T]} 必须是能服务 T 的那一份。
 * <p>
 * <b>最小改法</b>：不动 :221-228 的既有循环（把 {@code break} 改成「type 不匹配则 continue」需要
 * {@code @Redirect}/{@code @ModifyVariable}，项目无此先例），而是在 {@code getHandler} 的 HEAD 做一次
 * 惰性补满：缓存有效（{@code isCached}）且请求的 type 缺表时，为所有缺失 type 各调一次
 * {@code cellHandler.getCellInventory(is, this, t)} 并用原生 {@code wrap()} 包一份塞进 {@code cellMap}。
 * 原生随后的 :235 直接命中，行为与语义都不再需要 {@code NO_HANDLER}。
 * <ul>
 * <li>独立性（硬要求）：{@code wrap()}（:242-254）每次都 {@code new MEInventoryHandler} +
 * {@code new ChestMonitorHandler} + {@code addListener(new ChestNetNotifier(...))}，无按 handler 去重，
 * 因此每个 type 得到独立实例；若共用实例会出现 idle drain 重复计费与双份通知。</li>
 * <li>不重复计费：不复现 :225 的 {@code power +=} 与 :230 的 {@code setIdlePowerUsage}，
 * 与 {@code MixinTileDrive} 同口径——多通道只增加可用通道，不额外扣待机电。</li>
 * <li>失效自动收敛：:212 的重建会 {@code cellMap.clear()}，:487-488 / :594-595 / :760-761 换件时同样
 * clear 并置 {@code isCached = false}；HEAD 注入在 {@code !isCached} 分支只复位闩并立即返回，
 * 补登记条目随原生重建一起作废，不会残留旧元件的 handler。</li>
 * <li>不掺和显示层：不注入 {@code getCellType}（:284-312 的单值显示路径）与 :262/:285 的
 * {@code Platform.isClient()} 分支；它们经 {@code getHandler} 间接受益。</li>
 * </ul>
 * <p>
 * 仅在 AE2 已加载时由 {@code com.miaokatze.gtit.asm.GtitLateMixinLoader} 条件施加。
 */
@Mixin(value = TileChest.class, priority = 1000)
public class MixinTileChest {

    @Unique
    private static final Logger gtit$LOG = LogManager.getLogger("gtit");

    /**
     * 本轮缓存有效期内是否已经尝试过补满。置位后即使某些 type 真的不支持也只探测一次，
     * 避免 {@code getHandler(FLUID_STACK_TYPE)} 这类每 tick 调用反复走全量探测。
     */
    @Unique
    private boolean gtit$didFill;

    @Unique
    private boolean gtit$warned;

    @Shadow(remap = false)
    private boolean isCached;

    @Shadow(remap = false)
    private ICellHandler cellHandler;

    @Shadow(remap = false)
    private AppEngInternalInventory inv;

    @Shadow(remap = false)
    private Map<IAEStackType<?>, MEMonitorHandler> cellMap;

    /**
     * 目标为 {@code TileChest} 自身的 {@code private <S extends IAEStack<?>> MEMonitorHandler<S> wrap(IMEInventoryHandler)}
     * （:242-254）。{@code @Shadow} 可以遮蔽目标类的私有方法，合并后就是对 {@code TileChest} 的直接调用，
     * 从而完全复用原生的 per-type 包装（含 {@code ChestMonitorHandler} 与 {@code ChestNetNotifier}）。
     */
    @Shadow(remap = false)
    private MEMonitorHandler wrap(final IMEInventoryHandler h) {
        return null;
    }

    /**
     * @param type 被请求的 stack type
     */
    @Inject(method = "getHandler", at = @At("HEAD"), remap = false)
    private void gtit$lazilyFillAllStackTypes(IAEStackType<?> type, CallbackInfoReturnable<IMEInventoryHandler> cir) {
        try {
            if (!this.isCached) {
                // 原生 :212 即将 clear + 重建，补登记随之作废；这里只做复位
                this.gtit$didFill = false;
                return;
            }
            if (this.gtit$didFill || this.cellMap.containsKey(type)) {
                return;
            }
            // 先落闩再干活：wrap()/getCellInventory() 若再入 getHandler 也不会递归展开
            this.gtit$didFill = true;

            final ItemStack is = this.inv.getStackInSlot(1);
            final ICellHandler handler = this.cellHandler;
            if (is == null || handler == null) {
                return;
            }
            for (IAEStackType<?> t : AEStackTypeRegistry.getAllTypes()) {
                if (this.cellMap.containsKey(t)) {
                    continue;
                }
                final IMEInventoryHandler cell = handler.getCellInventory(is, (ISaveProvider) this, t);
                if (cell == null) {
                    continue;
                }
                this.cellMap.put(t, this.wrap(cell));
            }
        } catch (Throwable t) {
            if (!this.gtit$warned) {
                this.gtit$warned = true;
                gtit$LOG.warn("[gtit] ME 箱多通道补登记失败，已退回 AE2 原生的单通道行为（箱子本身不受影响）", t);
            }
        }
    }
}
