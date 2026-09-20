package com.miaokatze.gtit.mixin.ae2;

import java.util.ArrayList;
import java.util.IdentityHashMap;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.AEApi;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.tile.storage.TileIOPort;

/**
 * 让一枚多通道元件在 ME-IO 端口里<b>每个通道都被搬运到</b>，且<b>不被提前弹到输出侧</b>
 * （真·同槽多通道的搬运侧）。
 * <p>
 * <b>被改的原始行为</b>（AE2 rv3-beta-1050-GTNH，{@code appeng/tile/storage/TileIOPort.java}）：
 * <ul>
 * <li>{@code getInv(ItemStack)}（:488-502）用<b>单值</b>字段 {@code cachedInventory} 缓存，内层 :492-498
 * 遍历 {@code AEStackTypeRegistry.getAllTypes()}、:493 以 {@code host = null} 取 handler、第一个非 null
 * 即 :496 {@code break} ⇒ 一个元件只被它命中的第一个通道服务。</li>
 * <li>{@code matches(...)}（:599-625）在「抽干元件」（{@code OPERATION_MODE.EMPTY} +
 * {@code FULLNESS_MODE.EMPTY}）下 {@code :612 return myList.isEmpty()}，而
 * {@code myList = getAvailableStacks(src)}（:605）只取<b>本轮那一个通道</b> ⇒ 物品通道一空，
 * {@code shouldMove}（:572-584）就放行 {@code moveSlot}（:441/:586-597）把元件弹走，
 * 流体与源质通道根本没轮到过。这是实测「IO 端口只能传物品、一瞬间就结束」的直接原因。</li>
 * </ul>
 * <p>
 * <b>「本轮服务哪个通道」≡ {@code getInv} 的返回值自身</b>（实读 :391-428 与 :504-570）：
 * {@code tickingRequest} 的槽位循环 :393-474 里<b>没有</b>按 {@code IAEStackType} 的外层循环；:406 每槽每轮
 * 只调一次 {@code getInv}，随后 :412 的 {@code getMEMonitor(inv.getStackType())}、:414-415 的
 * {@code amountPerUnit}/{@code transferBudget}、:433 的 {@code shouldMove(inv, ...)} 全部由这<b>同一个</b>
 * {@code inv} 派生；{@code transferContents} 的 {@code src}/{@code destination} 是 :418/:420 传入的形参，
 * :528 的 SIMULATE 与 :538 的 MODULATE 读的就是这对 final 形参，中途不再查任何缓存。
 * ⇒ 只要一次 {@code getInv} 返回一个自洽 handler，就不可能「A 通道模拟、B 通道抽取」。
 * <p>
 * <b>两处接管</b>：
 * <ol>
 * <li>{@code getInv} HEAD 可取消注入：按该元件命中的<b>全部</b>通道取候选（遍历顺序与 :492 一致、调用与 :493
 * 逐字相同），轮转选一个写回 {@code cachedInventory} 并返回。轮转序号<b>按元件独立</b>
 * （{@link #gtit$rotation}，身份键）：早先实现用单一共享序号且在身份变化时归零，多槽位下每次调用都被重置，
 * 结果所有元件永远停在第 0 通道。</li>
 * <li>{@code shouldMove} HEAD 可取消注入：仅在「抽干元件」且该元件确为多通道时接管——
 * <b>任一通道仍有内容就不许弹件</b>，全部通道都空了才放行给原生判定。</li>
 * </ol>
 * 单通道元件（旧两枚、AE2 原生元件）与其余模式组合一律不接管，行为逐字同原生。候选列表<b>每次调用重算</b>
 * （各通道 handler 按外置存储即时构造，缓存实例反而会读到陈旧内容），只有轮转序号跨调用保留。
 * 任何异常都<b>不</b> {@code cancel}、不改 {@code currentCell}/{@code cachedInventory}，
 * 原生 :488-502 整段照跑，退回单通道行为。
 * <p>
 * 仅在 AE2 已加载时由 {@code com.miaokatze.gtit.asm.GtitLateMixinLoader} 条件施加。
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
@Mixin(value = TileIOPort.class, priority = 1000)
public class MixinTileIOPort {

    @Unique
    private static final Logger gtit$LOG = LogManager.getLogger("gtit");

    /** 每枚元件各自的轮转序号（身份键）；元件被换掉即自然失效，超过 6 槽余量整体清空。 */
    @Unique
    private final Map<ItemStack, Integer> gtit$rotation = new IdentityHashMap<>();

    /** 本轮 {@code getInv} 服务的元件与其全部通道，供紧随其后（:433）的 {@code shouldMove} 判定用。 */
    @Unique
    private List<IMEInventory<?>> gtit$activeChannels;

    /** 接管失败只 warn 一次，避免每 tick 刷日志。 */
    @Unique
    private boolean gtit$warned;

    /** 一次性取证日志：证明 IO 端口这侧确实见到了多通道元件并开始轮转。 */
    @Unique
    private boolean gtit$logged;

    @Shadow(remap = false)
    private ItemStack currentCell;

    @Shadow(remap = false)
    private IMEInventory<?> cachedInventory;

    /** 原生 :628-634，本 mixin 只借它读某通道的现有内容判空。 */
    @Shadow(remap = false)
    private IItemList<? extends IAEStack> getAvailableStacks(final IMEInventory inventory) {
        return null;
    }

    @Inject(method = "getInv", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtit$serveEveryStackType(final ItemStack is, final CallbackInfoReturnable<IMEInventory<?>> ci) {
        if (is == null) {
            return;
        }
        try {
            final List<IMEInventory<?>> channels = this.gtit$channelsOf(is);
            final int size = channels.size();
            if (size == 0) {
                // 零通道：与原生一致，缓存 null 并失效原生缓存
                this.currentCell = is;
                this.cachedInventory = null;
                this.gtit$activeChannels = null;
                ci.setReturnValue(null);
                return;
            }

            // size==1 时恒为第 0 个，与原生 :496 break 的结果等价
            final int index = size == 1 ? 0 : this.gtit$nextIndex(is, size);
            final IMEInventory<?> selected = channels.get(index);

            this.gtit$activeChannels = size > 1 ? channels : null;
            this.currentCell = is;
            this.cachedInventory = selected;

            if (size > 1 && !this.gtit$logged) {
                this.gtit$logged = true;
                gtit$LOG.info(
                    "[gtit] ME-IO 端口多通道轮转命中：该元件通道数={}，本轮服务通道 id={}",
                    size,
                    selected.getStackType()
                        .getId());
            }
            ci.setReturnValue(selected);
        } catch (Throwable t) {
            this.gtit$warn(t);
            // 不 cancel、不改原生字段：:488-502 照常执行并返回它自己的单通道结果
            this.gtit$activeChannels = null;
        }
    }

    /**
     * 抽干元件时不许提前弹件：任一通道仍有内容就压住，全部空了才让原生给出弹件结论。
     */
    @Inject(method = "shouldMove", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtit$holdUntilAllChannelsDrained(final IMEInventory<?> inventory,
        final boolean sourceEmptyAfterTransfer, final boolean destinationFull, final boolean didWork,
        final boolean moveOnEmptyWhileFilling, final OperationMode operationMode, final FullnessMode fullnessMode,
        final CallbackInfoReturnable<Boolean> ci) {
        final List<IMEInventory<?>> channels = this.gtit$activeChannels;
        if (channels == null || inventory == null
            || operationMode != OperationMode.EMPTY
            || fullnessMode != FullnessMode.EMPTY) {
            return;
        }
        try {
            for (final IMEInventory<?> channel : channels) {
                if (channel == inventory) {
                    // 本轮这个通道直接用原生刚算出的结论，避免重复扫一遍大列表
                    if (!sourceEmptyAfterTransfer) {
                        ci.setReturnValue(false);
                        return;
                    }
                } else {
                    final IItemList<? extends IAEStack> contents = this.getAvailableStacks((IMEInventory) channel);
                    if (contents != null && !contents.isEmpty()) {
                        ci.setReturnValue(false);
                        return;
                    }
                }
            }
            // 全部通道都空：不接管，交给原生判定
        } catch (Throwable t) {
            this.gtit$warn(t);
        }
    }

    /** 取该元件命中的全部通道 handler，遍历顺序与调用形式都与原生 :492-493 一致。 */
    @Unique
    private List<IMEInventory<?>> gtit$channelsOf(final ItemStack is) {
        final List<IMEInventory<?>> found = new ArrayList<>(4);
        for (final IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            final IMEInventory<?> inventory = AEApi.instance()
                .registries()
                .cell()
                .getCellInventory(is, null, type);
            if (inventory != null) {
                found.add(inventory);
            }
        }
        return found;
    }

    @Unique
    private int gtit$nextIndex(final ItemStack is, final int size) {
        if (this.gtit$rotation.size() > 8) {
            this.gtit$rotation.clear();
        }
        final Integer previous = this.gtit$rotation.get(is);
        final int index = previous == null ? 0 : previous;
        this.gtit$rotation.put(is, (index + 1) % size);
        return index;
    }

    @Unique
    private void gtit$warn(final Throwable t) {
        if (!this.gtit$warned) {
            this.gtit$warned = true;
            gtit$LOG.warn("[gtit] ME-IO 端口多通道处理失败，已退回 AE2 原生的单通道搬运行为（端口本身不受影响）", t);
        }
    }
}
