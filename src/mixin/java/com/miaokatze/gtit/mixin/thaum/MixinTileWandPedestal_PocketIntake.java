package com.miaokatze.gtit.mixin.thaum;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.gtit.crossmod.taum.PocketVisSupport;

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
 * <b>{@code remap} 的取值依据（★R96 S10c 按 AP 字节码实测重写，原文的推断是错的）</b>：两个名字都来自
 * MC，但<b>声明它们的接口不在同一层</b>——{@code canInsertItem} 由 {@code ISidedInventory} 声明，
 * 而 {@code isItemValidForSlot} 只由 {@code IInventory} 声明。javap 实证：
 * {@code TileWandPedestal extends TileThaumcraft implements ISidedInventory, IAspectContainer}
 * ⇒ {@code IInventory} 只是「接口之接口」（{@code ISidedInventory extends IInventory}），
 * <b>不在</b>本类的直接接口表里。而 Mixing 注解处理器的映射解析
 * （{@code MappingMethodResolvable.getSuper()}，本次直读 unimixins 的 AP 字节码）走的是
 * 「<b>超类链 + 当前类型的直接接口</b>」，且只有<b>自己声明了该名</b>的父类型才会被踏上，
 * 接口之接口<b>不会被展开</b> ⇒ {@code isItemValidForSlot} 在这个 target 上<b>无解</b>，构建期直接报
 * {@code Unable to locate obfuscation mapping for @Inject target isItemValidForSlot}。
 * 同一条规则的三个交叉验证：{@code canInsertItem}（直接接口声明）/{@code updateEntity}
 * （超类 {@code TileEntity}）/{@code onCollideWithPlayer}（超类 {@code Entity}）本次都正常产出了 refmap 条目。
 * <p>
 * 原文引的 SalisArcana 先例（{@code setInventorySlotContents → TileMagicWorkbench;func_70299_a}）
 * <b>前提不同</b>：{@code TileMagicWorkbench extends TileThaumcraft implements IInventory, ISidedInventory}
 * ——它<b>直接</b>实现了 {@code IInventory} 所以能被解析，这条先例不能推广到本类。另：全实例 47 份
 * refmap 里 {@code isItemValidForSlot} / {@code func_94041_b} 的条目数 = <b>0</b>（S10c 复核），
 * 与「该名在这类 target 上映射不出来」互证。
 * <p>
 * <b>处置</b>：P1 改用「双名自 qualify + {@code remap = false}」（理由见方法上的注释），P2 保持 {@code remap = true}。
 * <p>
 * 仅在 TC 已加载时由 {@code com.miaokatze.gtit.asm.GtitThaumLateMixinLoader} 条件施加。
 */
@Mixin(value = TileWandPedestal.class, remap = false)
public class MixinTileWandPedestal_PocketIntake {

    /**
     * P1：搬运侧准入判据的<b>前置那一半</b>。★本次实测：MC 1.7.10 {@code TileEntityHopper:539} 的形状是
     * {@code !inv.isItemValidForSlot(slot, stack) ? false
     *  : !(inv instanceof ISidedInventory) || inv.canInsertItem(slot, stack, side)}
     * ⇒ 本方法与 P2 <b>是「与」关系、缺一不可</b>（只放开 P2 时漏斗仍在这里被否掉）；而 {@code Container}
     * 全类不查本方法（grep MC 反源码实测）⇒ 原文「GUI / 玩家放置侧」这句<b>没有证据</b>，已按实测改记漏斗侧。
     * <p>
     * ★非 void 目标 ⇒ 回调形参必须 {@code CallbackInfoReturnable<Boolean>}（用 {@code CallbackInfo}
     * 写非 void 目标的 {@code @Inject} 回调<b>构建期完全合法</b>，FATAL 只在运行期暴露，
     * 且会让<b>整个 config</b> 的 APPLY 中止——判据出处 {@code r96-plan.md} §7.3 第 2 条）。
     * <p>
     * ★★<b>为什么这里写两个名字并且 {@code remap = false}</b>（R96 S10c，修的是构建期硬失败）：
     * 本名只由 {@code IInventory} 声明，而 {@code TileWandPedestal} 仅<b>间接</b>实现该接口 ⇒ AP 的
     * 「超类链 + 直接接口」解析踩不到声明者，{@code remap = true} 必然报
     * {@code Unable to locate obfuscation mapping}（详见类注释）。这里不放宽语义，只把<b>两个命名空间的
     * 真名各写一份</b>：dev 类路径里该类只有 {@code isItemValidForSlot}（javap 实证：dev jar 内该类
     * {@code func_} 前缀成员数 = 0），生产类里只有 {@code func_94041_b}（TC 生产转储 {@code :384}），
     * 任一侧都<b>恰好命中一个</b>。运行期计数由 {@code TargetSelectors.validate} 把<b>整组</b>选择器
     * 的命中数当判据（字节码实证：{@code targets.size() > 0} 即直接 return），未命中的那一个名字
     * 只贡献 0，不会触发「no targets matching」；{@code require} 默认 1，与「1 个命中方法」相符。
     * 之所以 {@code remap = false}：既然两名都是运行时真名、不依赖 refmap 翻译，让 AP 不去查映射即可，
     * 也避免它再产一条名不副实的条目。
     */
    @Inject(method = { "isItemValidForSlot", "func_94041_b" }, at = @At("HEAD"), cancellable = true, remap = false)
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
    private void gtit$acceptPocketAutomation(int slot, ItemStack stack, int side, CallbackInfoReturnable<Boolean> cir) {
        if (PocketVisSupport.isPocket(stack) && ((IInventory) (Object) this).getStackInSlot(slot) == null) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
