package com.miaokatze.gtit.asm;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/**
 * GTIT 神秘时代（Thaumcraft 4.2.3.5）侧的条件型 Mixin 注册入口。
 * <p>
 * <b>为什么必须再开一个类，而不是把 TC 那几枚塞进 {@link GtitLateMixinLoader}</b>：
 * {@code ILateMixinLoader#getMixinConfig()} 返回的是<b>单个</b> String，一个 loader 类只能带一份
 * mixin 配置，而 AE2 那份 {@code mixins.gtit.ae2.json} 的注册条件是
 * {@code appliedenergistics2} 在场。TC 与 AE2 是两条互相独立的在场判据（RC-1 实例里 AE2 恒在、
 * TC 是可选件），塞进同一个类就只能要么放弃「只装 TC」的组合，要么放弃「只装 AE2」的组合，
 * 两枚 mixin 里必有一枚静默不生效。故这里另开一类、另配一份 json。
 * <p>
 * <b>为什么还需要第二份 json</b>：gtnhgradle 的 ToolchainModule 只按 {@code "mixins." + modId + ".json"}
 * 拼出<b>单一</b>配置名写进 jar manifest 的 {@code MixinsConfigs}（{@code gradle.properties} 的
 * {@code modId = gtit} ⇒ 只有 {@code mixins.gtit.json} 会被自动施加）。这一条对 AE2 那份已经成立，
 * 对 TC 这份同样成立，见 {@link GtitLateMixinLoader} 的类注释与 {@code r96-s10.md} 的注册面判据
 * （{@code unzip -p <jar> META-INF/MANIFEST.MF} 只有一条 {@code MixinConfigs}）。
 * <p>
 * <b>本类的引导时机</b>：unimixins 内置的
 * {@code com.gtnewhorizon.gtnhmixins.mixins.LateMixinOrchestrationMixin#beforeConstructing} 在 FML
 * Constructing 阶段之前扫描 ASMDataTable 中所有带 {@link LateMixin} 的类并调用其
 * {@link ILateMixinLoader}，故可在 TC 类被真正加载前完成条件注册。
 * <p>
 * TC 不是本模组的 {@code @Mod} 必需依赖（{@code main/GTInterestingThing.java:37} 的 dependencies 串里
 * 没有 Thaumcraft，写 {@code required-after:Thaumcraft} 会让无 TC 的实例直接起不来），
 * 所以这里必须按已加载 mod 条件施加；TC 侧的常驻桥接一律走 {@code crossmod/taum/TaumCompat}
 * 的 String/int 口径，mixin 类只经 transformer 通道消费。
 */
@LateMixin
public class GtitThaumLateMixinLoader implements ILateMixinLoader {

    private static final Logger LOGGER = LogManager.getLogger("gtit");

    /** 与 {@code src/main/resources/mixins.gtit.thaum.json} 一一对应；该 json 有意不写 mixins/client 数组。 */
    private static final String THAUM_MIXIN_CONFIG = "mixins.gtit.thaum.json";

    /**
     * Thaumcraft 的 FML modId。
     * <p>
     * ★这里必须是首字母大写的 {@code Thaumcraft}，不是 Java 包名 {@code thaumcraft}：
     * {@link #getMixins(Set)} 拿到的是 <b>modId</b> 集合，而 TC 把 modId 写死成
     * {@code @Mod(modid = "Thaumcraft")}（{@code thaumcraft/common/Thaumcraft.java:70}，
     * 同文件 {@code :76} 的 {@code public static final String MODID = "Thaumcraft"} 与之互证）。
     * 大小写写错的表现与 AE2 那次一模一样：判据恒 false、五枚 mixin 从未被施加、
     * 口袋与基座/要素球/护盾的三条腿静默退回 TC 原生行为，<b>不报错</b>。
     * 包名 {@code thaumcraft} 只出现在 import 与 {@code @Mixin} 的类引用里。
     */
    private static final String THAUMCRAFT_MOD_ID = "Thaumcraft";

    /**
     * 仅在 TC 存在时施加的 mixin 列表（相对 json 的 {@code package} 的点号子包名）。
     * <p>
     * 五条腿，成对关系是硬约束，不要单独摘：
     * <ul>
     * <li>{@code thaum.MixinTileWandPedestal_PocketIntake}（P1 {@code isItemValidForSlot} +
     * P2 {@code canInsertItem}）与 {@code thaum.MixinTileWandPedestal_PocketCharge}（P3 充能）：
     * 只放前一枚就是「放得进、永远不涨」，只放后一枚则口袋根本上不了基座；</li>
     * <li>{@code thaum.MixinInventoryUtils_PocketHotbar}（P4）与
     * {@code thaum.MixinEntityAspectOrb_PocketAbsorb}（P5）：只做 P4 会让
     * {@code EntityAspectOrb.java:200} 那条唯一的 {@code checkcast ItemWandCasting} 在玩家走近球时抛
     * CCE，两枚必须同轮；</li>
     * <li>{@code thaum.MixinEventHandlerRunic_PocketShield}：符文护盾回充的口袋掏账腿（G-1 扩权项）。</li>
     * </ul>
     */
    private static final List<String> THAUM_MIXINS = Collections.unmodifiableList(
        Arrays.asList(
            "thaum.MixinTileWandPedestal_PocketIntake",
            "thaum.MixinTileWandPedestal_PocketCharge",
            "thaum.MixinInventoryUtils_PocketHotbar",
            "thaum.MixinEntityAspectOrb_PocketAbsorb",
            "thaum.MixinEventHandlerRunic_PocketShield"));

    @Override
    public String getMixinConfig() {
        return THAUM_MIXIN_CONFIG;
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        final boolean tcPresent = loadedMods != null && loadedMods.contains(THAUMCRAFT_MOD_ID);
        // 一次性取证日志：这行是否出现即判别 TC 侧 LateMixin 引导链是否跑通（缺席=unimixins 没扫到
        // @LateMixin，五条腿会静默退回 TC 原生行为，而不是报错）。本仓对「mixin 静默不生效」的唯一在产防护。
        LOGGER.info(
            "[gtit] LateMixin 引导命中：tcPresent={}，施加 {}（config={}）",
            tcPresent,
            tcPresent ? THAUM_MIXINS : "无",
            THAUM_MIXIN_CONFIG);
        if (tcPresent) {
            return THAUM_MIXINS;
        }
        return Collections.emptyList();
    }
}
