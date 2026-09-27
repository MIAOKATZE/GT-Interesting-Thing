package com.miaokatze.gtit.mixin.thaum;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;

import com.miaokatze.gtit.common.items.pocket.ItemNekoDimensionPocket;

import thaumcraft.api.IRunicArmor;

/**
 * ★R96 S11-fix · 需求 8 的「符文护盾 +20」：<b>把护盾容量接口注入常驻的口袋 Item 类</b>。
 * <p>
 * <b>为什么这一半必须是 mixin 而不是 {@code implements}（本片唯一的阻塞缺陷）</b>：
 * S11 原本让 {@link ItemNekoDimensionPocket} 直接 {@code implements thaumcraft.api.IRunicArmor}，
 * 兜底理由写的是「{@code @Optional.Interface} 会让 FML 在 Thaumcraft 缺席时把接口从
 * {@code interfaces} 数组里擦掉」。★那条理由不成立：神秘时代在本仓是 {@code compileOnly}
 * （{@code dependencies.gradle:73}，运行期不在场的可选件），把一个 {@code compileOnly} 的类型写进
 * <b>会被正常类加载器加载的类</b>的 {@code implements} 位，就是让该类的<b>类型层次</b>在运行期要求它在场 ——
 * 接口解析发生在 JVM 加载本类的那一刻，{@code @Optional} 的擦除是 FML 的类变换在做，
 * 而「解析顺序上先擦除还是先解析」不是本仓可以许诺的契约；写全限定名只绕得过 {@code import}
 * 那一条<b>文本</b>检查，绕不过类型层次本身。后果是<b>没装神秘时代的实例上整个 mod 起不来</b>
 * （{@code NoClassDefFoundError: thaumcraft/api/IRunicArmor}），撞的正是
 * {@code crossmod/taum/TaumCompat.java:15-19} 那条「会被正常加载的类的常量池与签名不得静态引用
 * {@code thaumcraft.*}」的类污染红线。同一处写法还顺带把测试源集打红：{@code compileOnly} 不进
 * test 的编译类路径，{@code NekoPocketModelTest} 只要引用口袋类就得解析那个接口。
 * <p>
 * <b>mixin 通道为什么算绕开、不算撞线</b>（取证 {@code .qoder/tmp/r96-ret10.md} §6 与
 * {@code r96-s10.md}）：mixin 类的常量池里必然带 {@code Lthaumcraft/api/IRunicArmor;}，
 * 但 mixin 类<b>只由 Mixin 的 transformer 通道消费</b>，mod 的常规类加载器按名看不见它
 * （wiki {@code SmokeTest/mixin-apply-green-blindspots.md} §2 实测：对已成功施加的 mixin 类做
 * {@code Class.forName} 恒 {@code ClassNotFoundException}）⇒ 红线管的是「我们自己的、会被正常加载的类」，
 * 这一枚不在其列。施加之后 TC 接口进的是<b>运行期被改写过的那份</b> {@link ItemNekoDimensionPocket}
 * 字节码，而那份字节码只在 TC 在场时才会被造出来。
 * <p>
 * <b>★施加条件 = TC 在场</b>（本枚因此必须走 {@code asm/GtitThaumLateMixinLoader}，
 * <b>不得</b>进 {@code mixins.gtit.json}）：那份配置由 jar manifest 无条件施加，把接口写进去等于
 * 「TC 缺席时也照注入」= 原缺陷原地复发，只是成因从我们的 {@code implements} 换成注入器。
 * TC 缺席 ⇒ 本枚不施加 ⇒ 口袋照常注册、照常可穿戴、照常跑八条被动，只是<b>不给符文护盾加容量</b> ——
 * 这正是需求 8 里「有神秘时代才有这 +20」的可选语义（S11 原写法想要的 {@code @Optional} 行为）。
 * <p>
 * <b>读数 20 的消费端（两条必须一起读）</b>：{@code EventHandlerRunic} 把<b>穿在身上的</b> 4 格盔甲
 * （{@code EventHandlerRunic.java:63-64}）与 4 格饰品（{@code :72-73}）里每个 {@link IRunicArmor} 的
 * {@link #getRunicCharge(ItemStack)} 累加成护盾的<b>容量上限</b>（{@code time}），再按它决定回充节奏：
 * <ol>
 * <li><b>不穿戴 = 不计入</b>：那一双循环只扫盔甲 0..3 与 bauble 0..3，主背包与快捷栏一格都不问
 * （vanilla 侧同源证据 {@code InventoryPlayer.java:689} 的护甲/饰品枚举）⇒ 口袋躺在背包里时
 * 这个数字对护盾没有任何作用；</li>
 * <li><b>穿戴才计入，而且只数前四格</b>：{@code BaubleType.UNIVERSAL} 允许口袋落到 bauble 栏的任意格，
 * 而 TC 的循环上界写死 {@code < 4}（SalisArcana 之类把它放宽到 {@code getSizeInventory()} 是
 * 另一枚 mod 的行为，不由本仓许诺）⇒ 落在第 5 格及以后就<b>不占容量</b>。这条差异进 README。</li>
 * </ol>
 * ★刻意<b>不是</b> {@code ItemAmuletVis} / {@code IEssentiaContainerItem} 那两型（S11 明令禁止项）：
 * 本件只做「计入容量」这一半，「掏口袋元素」那一半已由 S10 的
 * {@link MixinEventHandlerRunic_PocketShield} 的 {@code @Redirect} 落好，两半合起来才是需求 8 说的那句话。
 * <p>
 * <b>目标类是本仓自己的明文类</b>（{@link ItemNekoDimensionPocket} 不经 SRG 改名），接口方法
 * {@code getRunicCharge} 在 TC 侧也是明文（{@code IRunicArmor} 是 API 接口）⇒ 类级 {@code remap = false}，
 * 本枚<b>不需要</b> refmap 条目。「穿上真的把护盾容量抬到 20」与「TC 在场时 {@code instanceof IRunicArmor}
 * 判据真的命中」都属<b>实机项</b>（计划 §8 V-3），本仓的绿只证到注入点在位、施加条件对、读数唯一。
 */
@Mixin(value = ItemNekoDimensionPocket.class, remap = false)
public class MixinItemNekoDimensionPocket_RunicArmor implements IRunicArmor {

    /**
     * 符文护盾贡献的容量位数（★需求 8 的字面值，全仓只住这一个常量：没有第二处显示面读它，
     * 因为「不穿戴不计入」那一半由 TC 的循环执法，本仓再判一遍「是否穿戴」就是两处真相，
     * 而且会把 TC 自己那条恒假 tooltip（{@code EventHandlerRunic#tooltipEvent} 不区分穿戴）
     * 伪装成我方已经处理过）。
     * <p>
     * ★TC 侧真正的读法是 {@code getFinalCharge}：{@link #getRunicCharge(ItemStack)} 之<b>上</b>还会叠
     * 栈里 {@code RS.HARDEN} 那个 NBT 的加固件数，本件不写那个键 ⇒ 读数就是这个常数。
     * <p>
     * ★{@code static final int} 是编译期内联常量：本字段合流进目标类后不再被任何字节码引用
     * （{@link #getRunicCharge(ItemStack)} 的体内是 {@code iconst_20}），所以它既不会给目标类
     * 带来运行期依赖，也不会给 TC 缺席的实例留下任何符号引用。
     */
    private static final int RUNIC_CHARGE = 20;

    @Override
    public int getRunicCharge(ItemStack stack) {
        return RUNIC_CHARGE;
    }
}
