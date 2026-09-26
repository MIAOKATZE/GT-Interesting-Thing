package com.miaokatze.gtit.mixin.thaum;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import thaumcraft.common.tiles.TileWandPedestal;

/**
 * ★R96 S10 · P1 + P2：让充能基座<b>接受</b>口袋（判据放宽腿）。
 * <p>
 * <b>被改的原始行为</b>（TC 4.2.3.5 {@code thaumcraft/common/tiles/TileWandPedestal.java}，
 * 方法名以 dev jar {@code javap} + 生产 jar SRG 名双向核对为准，取证 {@code r96-ret10.md} §4 冲突 1）：
 * <ul>
 * <li>{@code :384 isItemValidForSlot(int, ItemStack)} = {@code func_94041_b}
 * ⇒ {@code stack instanceof ItemWandCasting || instanceof ItemAmuletVis}，否则 false；</li>
 * <li>{@code :392 canInsertItem(int, ItemStack, int)} = {@code func_102007_a}
 * ⇒ 除上面两条还多要求 {@code getStackInSlot(slot) == null}。</li>
 * </ul>
 * 两处都是<b>字面 {@code instanceof} 具体类</b>，TC4 没有任何接口/事件挂载点（{@code r96-ret4.md} §3.2、
 * {@code r96-eva3.md} §2.3），所以「把口袋放上基座」在原生 TC 里不可能成立。
 * <p>
 * <b>本 mixin 的做法</b>：HEAD 注入，命中口袋就 {@code setReturnValue(true)}。
 * ★刻意<b>不</b>查「魔法使开关」——本类只管<b>放得进</b>，不管<b>涨不涨</b>：
 * 搬运腿在 {@link MixinTileWandPedestal_PocketCharge}，那边才闸开关。把两件事分开的理由是
 * 「关掉被动后基座里已经躺着的口袋仍然拿得出来、也不会被 TC 判成非法物品」。
 * <p>
 * <b>与既有注入点的关系</b>：RC-1 全实例 refmap 扫描实测
 * {@code isItemValidForSlot / canInsertItem / func_94041_b / func_102007_a} 的条目数 = <b>0</b>
 * （Hodgepodge / SalisArcana / Backhand / GT5U 都没碰这两个判据方法）⇒ 这一对注入点<b>无争抢</b>，
 * 与 {@code updateEntity} 那条（四枚共存）不是同一档风险。
 * <p>
 * <b>{@code remap} 的取值依据</b>：类级 {@code remap = false}（TC 自有名一律不映射），
 * 但 {@code isItemValidForSlot} / {@code canInsertItem} 是 {@code ISidedInventory} 声明的
 * <b>MC 派生名</b> ⇒ 两条 {@code @Inject} 都显式 {@code remap = true}。这条形状不是推测：
 * 同实例的已知全绿构件 SalisArcana {@code mixins.salisarcana.refmap.json} 里有
 * {@code setInventorySlotContents → Lthaumcraft/common/tiles/TileMagicWorkbench;func_70299_a(ILnet/minecraft/item/ItemStack;)V}
 * ——同样是「mod 类实现 MC 接口」的跨代解析，且 {@code setInventorySlotContents} 只由 MC 接口声明。
 * <p>
 * 仅在 TC 已加载时由 {@code com.miaokatze.gtit.asm.GtitThaumLateMixinLoader} 条件施加。
 */
@Mixin(value = TileWandPedestal.class, remap = false)
public class MixinTileWandPedestal_PocketIntake {

    /**
     * P1：GUI / 玩家放置侧的准入判据。
     * ★非 void 目标 ⇒ 回调形参必须 {@code CallbackInfoReturnable<Boolean>}（用 {@code CallbackInfo}
     * 写非 void 目标的 {@code @Inject} 回调<b>构建期完全合法</b>，FATAL 只在运行期暴露，
     * 且会让<b>整个 config</b> 的 APPLY 中止——判据出处 {@code r96-plan.md} §7.3 第 2 条）。
     */
    @Inject(method = "isItemValidForSlot", at = @At("HEAD"), cancellable = true, remap = true)
    private void gtit$acceptPocket(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (PocketVisSupport.isPocket(stack)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    /**
     * P2：hopper / 管道侧的准入判据。
     * <p>
     * ★照抄 TC 原式的「目标槽必须为空」这一半（{@code :393}），不是因为怕丢件——基座
     * {@code getInventoryStackLimit() = 1}（{@code :157}）加上 vanilla 的 free-slot 数学，
     * 往非空槽本来就塞不进——而是因为<b>不照抄就等于给这条判据发明了第二个答案</b>：
     * 将来有人按「canInsertItem 是否等价于 isItemValidForSlot」推理时会被这里误导。
     */
    @Inject(method = "canInsertItem", at = @At("HEAD"), cancellable = true, remap = true)
    private void gtit$acceptPocketAutomation(int slot, ItemStack stack, int side,
        CallbackInfoReturnable<Boolean> cir) {
        if (PocketVisSupport.isPocket(stack) && ((IInventory) (Object) this).getStackInSlot(slot) == null) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
