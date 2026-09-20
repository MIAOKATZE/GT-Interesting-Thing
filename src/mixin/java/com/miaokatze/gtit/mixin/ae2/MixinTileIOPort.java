package com.miaokatze.gtit.mixin.ae2;

import java.util.ArrayList;
import java.util.List;

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
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.storage.TileIOPort;

/**
 * 让一枚元件在 ME-IO 端口里也能被<b>逐通道</b>搬运（真·同槽多通道的搬运侧）。
 * <p>
 * <b>被改的原始行为</b>（AE2 rv3-beta-1050-GTNH，{@code appeng/tile/storage/TileIOPort.java}）：
 * {@code private IMEInventory<?> getInv(ItemStack)} 位于 :488-502，缓存是<b>单值</b>字段
 * {@code cachedInventory}（:108），失效判据是 :489 {@code this.currentCell != is}（ItemStack 身份比较）；
 * 内层 :492-498 遍历 {@code AEStackTypeRegistry.getAllTypes()}，:493 以 {@code host = null} 取
 * {@code getCellInventory(is, null, type)}，第一个非 null 即在 :496 {@code break} —— 于是一个元件在 IO
 * 端口里只被它命中的<b>第一个</b>通道服务，其余通道静默忽略。
 * <p>
 * <b>本轮到底服务哪个通道</b>（本 mixin 正确性的全部依据，实读 :391-428 与 :504-570）：
 * {@code tickingRequest} 的槽位循环 :393-474 里<b>没有</b>按 {@code IAEStackType} 的外层循环；:406
 * {@code final IMEInventory<?> inv = this.getInv(is)} 每槽每轮<b>只调一次</b>，随后
 * :412 {@code getMEMonitor(inv.getStackType())}、:414 {@code inv.getStackType().getAmountPerUnit()}、
 * :415 {@code transferBudget = amountToMove * amountPerUnit}、:433 {@code shouldMove(inv, ...)} 全部
 * 由这<b>同一个</b> {@code inv} 局部量派生。也就是说「本轮服务的通道」≡ {@code getInv} 的返回值自身。
 * <p>
 * <b>最高危的 SIMULATE/MODULATE 错配为什么在此不成立</b>：{@code transferContents} 的
 * {@code src}/{@code destination}（:504-505）是 :418 / :420 传入的<b>形参</b>；:527-528 的
 * {@code poweredInsert(energy, destination, ..., SIMULATE)} 与 :538 的
 * {@code src.extractItems(..., MODULATE, ...)} 读的是同一对 final 形参，中途<b>不再</b>查
 * {@code getInv}/{@code cachedInventory}，也不再查 {@code getMEMonitor}。故只要一次 {@code transferContents}
 * 调用内 {@code src}↔{@code destination} 配对来自同一个 {@code inv}，就不可能「A 通道模拟、B 通道抽取」。
 * 因此本 mixin 把接管点放在 :406 那一次查询上、并保证<b>单次返回一个自洽 handler</b> 即可，
 * 无需重写 tick 循环、无需触碰 {@code amountToMove}/{@code shouldMove}/{@code moveSlot}/
 * {@code OPERATION_MODE} 语义。
 * <p>
 * <b>做法</b>：HEAD 可取消注入，自己按 {@code (currentCell 身份, 该元件命中的全部通道)} 解析出候选列表
 * （遍历顺序与 :492 完全一致，取的也是 :493 那个逐字相同的调用），再用每 tile 的轮转序号
 * {@link #gtit$seq} 选出一个写回 {@code cachedInventory} 并作为返回值。由于 :406 每槽每轮只发生一次调用，
 * 每个元件每 tick 前进一格 ⇒ 各通道按轮转公平地被服务。
 * <ul>
 * <li>只用 {@code @Inject}（项目无 {@code @Redirect}/{@code @Overwrite} 先例）。</li>
 * <li>候选列表与 {@code gtit$cell} 的身份判据复刻 :489 的失效语义；{@code cachedInventory}/{@code currentCell}
 * 仍被正常写回，二者在 {@code TileIOPort} 内除 :489/:490/:491/:495/:501 外无其它读写点，故不会引入字段丢失。</li>
 * <li>任何异常都<b>不</b> {@code cancel}：原生 :488-502 整段照跑，退回单通道行为（见 {@link #gtit$warned}）。</li>
 * </ul>
 * <p>
 * 仅在 AE2 已加载时由 {@code com.miaokatze.gtit.asm.GtitLateMixinLoader} 条件施加。
 */
@Mixin(value = TileIOPort.class, priority = 1000)
public class MixinTileIOPort {

    @Unique
    private static final Logger gtit$LOG = LogManager.getLogger("gtit");

    /** {@link #gtit$invs} 这批候选 handler 归属的元件；判据与原生 :489 的 {@code currentCell != is} 一致。 */
    @Unique
    private ItemStack gtit$cell;

    /** 该元件按 {@code AEStackTypeRegistry.getAllTypes()} 顺序命中的<b>全部</b>通道 handler。 */
    @Unique
    private List<IMEInventory<?>> gtit$invs;

    /** 轮转序号：只在候选数 &gt; 1 时才有意义，故单通道元件天然恒等于原生第一个命中。 */
    @Unique
    private int gtit$seq;

    /** 接管失败只 warn 一次，避免每 tick 刷日志。 */
    @Unique
    private boolean gtit$warned;

    /** 一次性取证日志：证明 IO 端口这侧确实见到了多通道元件并已开始逐通道轮转。 */
    @Unique
    private boolean gtit$logged;

    @Shadow(remap = false)
    private ItemStack currentCell;

    @Shadow(remap = false)
    private IMEInventory<?> cachedInventory;

    /**
     * 在原生单值缓存（:489 的 {@code currentCell != is} 判断）之前接管。
     * <p>
     * 全程先算到局部变量，最后一刻才写 {@code currentCell}/{@code cachedInventory} 并 {@code cancel}，
     * 因此中途抛异常时原生字段保持原样、原生实现会以完全原生的方式重算一遍。
     */
    @Inject(method = "getInv", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtit$serveEveryStackType(final ItemStack is, final CallbackInfoReturnable<IMEInventory<?>> ci) {
        if (is == null) {
            // 原生在这种情况下也只会得到 null，不必接管，让 :488-502 自己跑
            return;
        }
        try {
            if (this.gtit$cell != is || this.gtit$invs == null) {
                final List<IMEInventory<?>> found = new ArrayList<IMEInventory<?>>(4);
                for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                    // 与 :493 逐字相同：host 传 null，取该 type 的 handler；不吞异常，异常时整体退回原生
                    final IMEInventory<?> inventory = AEApi.instance()
                        .registries()
                        .cell()
                        .getCellInventory(is, null, type);
                    if (inventory != null) {
                        found.add(inventory);
                    }
                }
                this.gtit$cell = is;
                this.gtit$invs = found;
                this.gtit$seq = 0;
            }

            final List<IMEInventory<?>> invs = this.gtit$invs;
            if (invs == null || invs.isEmpty()) {
                // 零通道：与原生一致，缓存 null 并失效原生缓存
                this.currentCell = is;
                this.cachedInventory = null;
                ci.setReturnValue(null);
                return;
            }

            final int size = invs.size();
            IMEInventory<?> selected;
            if (size == 1) {
                // 单通道（旧两枚元件若只有一个通道、AE2 原生元件）：恒为第一个命中，逐字节等价于原生
                selected = invs.get(0);
            } else {
                int index = this.gtit$seq % size;
                if (index < 0) {
                    index = 0;
                    this.gtit$seq = 0;
                }
                selected = invs.get(index);
                this.gtit$seq = this.gtit$seq + 1;
                if (!this.gtit$logged) {
                    this.gtit$logged = true;
                    gtit$LOG.info(
                        "[gtit] ME-IO 端口多通道轮转命中：该元件通道数={}，本轮服务通道 id={}",
                        size,
                        selected.getStackType()
                            .getId());
                }
            }

            // 只在选完之后才写回原生字段，保证「本轮 inv↔monitor 配对」由 :412 从这同一个对象反推
            this.currentCell = is;
            this.cachedInventory = selected;
            ci.setReturnValue(selected);
        } catch (Throwable t) {
            if (!this.gtit$warned) {
                this.gtit$warned = true;
                gtit$LOG.warn("[gtit] ME-IO 端口多通道轮转失败，已退回 AE2 原生的单通道搬运行为（端口本身不受影响）", t);
            }
            // 关键：不 cancel、也不再改任何字段 —— 原生 :488-502 会照常执行并返回它自己的单通道结果
            this.gtit$cell = null;
            this.gtit$invs = null;
        }
    }
}
