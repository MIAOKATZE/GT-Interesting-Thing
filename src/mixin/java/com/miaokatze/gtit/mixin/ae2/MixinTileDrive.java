package com.miaokatze.gtit.mixin.ae2;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.storage.TileDrive;

/**
 * 让 AE2 驱动器对<b>每个已注册的 {@link IAEStackType} 各得一份独立 handler</b>（真·同槽多通道）。
 * <p>
 * <b>被改的原始行为</b>（AE2 rv3-beta-1050-GTNH，{@code appeng/tile/storage/TileDrive.java}）：
 * {@code private void updateState()} 位于 :302-343，整个方法体被 :303 {@code if (!this.isCached)} 包住，
 * :341 收尾 {@code isCached = true}。槽位循环 :311-337 内层 :320-334 遍历
 * {@code AEStackTypeRegistry.getAllTypes()}，:321 取 handler，第一个非 null 即在 :332 {@code break}，
 * 于是一个元件在驱动器里只占用它命中的<b>第一个</b> type（:329 {@code invBySlot[x] = ih}、
 * :330 {@code cellsMap.get(type).add(ih)}），其余通道被静默忽略。
 * <p>
 * <b>本 mixin 的做法</b>：不改动原循环（原通道、原 idle drain 计费、原 {@code invBySlot} 全部保持不变），
 * 只在真正重建的那一次 {@code updateState()} 末尾为「已被占用 type 之外」的每个 type 追加登记一份
 * <b>独立</b>的 {@link MEInventoryHandler} 到 {@code cellsMap}。
 * <ul>
 * <li>幂等性：{@code updateState()} 有 6 个调用点（:284/:355/:365/:385/:484/:508），而 TAIL 注入在
 * {@code isCached == true} 的早退路径上同样会被执行；若无条件补登记，每次调用都会向 {@code cellsMap}
 * 重复追加 watcher。因此用 HEAD 注入把「本次是否真的重建」记进 {@link #gtit$rebuild}，TAIL 首行据此早退。</li>
 * <li>不重复计费：{@code power += cellIdleDrain}（:323）与 {@code setIdlePowerUsage}（:339）一概不动，
 * 多通道元件在驱动器里仍只按原生那一通道扣待机电。</li>
 * <li>不覆盖 {@code invBySlot}：该数组是「每槽主通道」语义（原生只存一份），补登记的通道只进 {@code cellsMap}。</li>
 * </ul>
 * <p>
 * <b>为什么用 {@code new MEInventoryHandler} 而不是复刻 {@code DriveWatcher}</b>：{@code DriveWatcher}
 * 是 :347-352 的 {@code private static} 内部类且没有任何覆写，只是给 EquivalentEnergistics 用的类型标记，
 * 存档/脏标记由被包裹的内层 handler（持有 {@code this} 作为 ISaveProvider）负责，故行为等价。
 * <p>
 * 仅在 AE2 已加载时由 {@code com.miaokatze.gtit.asm.GtitLateMixinLoader} 条件施加。
 */
@Mixin(value = TileDrive.class, priority = 1000)
public class MixinTileDrive {

    @Unique
    private static final Logger gtit$LOG = LogManager.getLogger("gtit");

    /** 本次 {@code updateState()} 调用是否真的执行了重建（{@code isCached} 由 false 翻 true）。 */
    @Unique
    private boolean gtit$rebuild;

    /** 补登记失败只 warn 一次，避免每 tick 刷日志。 */
    @Unique
    private boolean gtit$warned;

    @Shadow(remap = false)
    private boolean isCached;

    @Shadow(remap = false)
    private int priority;

    @Shadow(remap = false)
    private AppEngInternalInventory inv;

    @Shadow(remap = false)
    private ICellHandler[] handlersBySlot;

    @Shadow(remap = false)
    private MEInventoryHandler[] invBySlot;

    @Shadow(remap = false)
    private Map<IAEStackType<?>, List<IMEInventoryHandler>> cellsMap;

    /**
     * 记录本次是否会真正重建：HEAD 处 {@code isCached} 还是重建前的值，:303 的判断依据即此。
     */
    @Inject(method = "updateState", at = @At("HEAD"), remap = false)
    private void gtit$captureRebuildFlag(CallbackInfo ci) {
        this.gtit$rebuild = !this.isCached;
    }

    /**
     * 重建完成后为其余通道补登记。TAIL 在早退路径（{@code isCached == true}）也会执行，故必须先查
     * {@link #gtit$rebuild}。
     */
    @Inject(method = "updateState", at = @At("TAIL"), remap = false)
    @SuppressWarnings("unchecked")
    private void gtit$registerAllStackTypes(CallbackInfo ci) {
        if (!this.gtit$rebuild) {
            return;
        }
        try {
            final ICellHandler[] handlers = this.handlersBySlot;
            final MEInventoryHandler[] bySlot = this.invBySlot;
            for (int x = 0; x < this.inv.getSizeInventory(); x++) {
                final ICellHandler handler = handlers[x];
                final MEInventoryHandler primary = bySlot[x];
                if (handler == null || primary == null) {
                    continue;
                }
                final ItemStack is = this.inv.getStackInSlot(x);
                if (is == null) {
                    continue;
                }
                // 原生循环已经登记过的那个 type，跳过以免重复
                final IAEStackType<?> occupied = primary.getStackType();
                for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                    if (type == occupied) {
                        continue;
                    }
                    final List<IMEInventoryHandler> list = this.cellsMap.get(type);
                    if (list == null) {
                        // :304-307 会为每个注册 type 建好 list；缺表说明状态异常，跳过而不是 NPE
                        continue;
                    }
                    final IMEInventoryHandler cell = handler.getCellInventory(is, (ISaveProvider) this, type);
                    if (cell == null) {
                        continue;
                    }
                    final MEInventoryHandler ih = new MEInventoryHandler(cell, cell.getStackType());
                    ih.setPriority(this.priority);
                    list.add(ih);
                }
            }
        } catch (Throwable t) {
            if (!this.gtit$warned) {
                this.gtit$warned = true;
                gtit$LOG.warn("[gtit] 驱动器多通道登记失败，已退回 AE2 原生的单通道行为（驱动器本身不受影响）", t);
            }
        }
    }
}
