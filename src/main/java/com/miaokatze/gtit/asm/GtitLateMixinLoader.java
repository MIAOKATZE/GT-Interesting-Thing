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
 * GTIT 条件型（Late）Mixin 注册入口。
 * <p>
 * <b>为什么必须走 LateMixin 而不是往 resources 里再放一个 json 就完事</b>：gtnhgradle 的 ToolchainModule
 * 只按 {@code "mixins." + modId + ".json"} 拼出<b>单一</b>配置名写进 jar manifest 的 {@code MixinsConfigs}
 * （见 {@code gradle.properties} 的 {@code modId = gtit} → 只有 {@code mixins.gtit.json} 会被自动施加），
 * 因此额外放置的 {@code mixins.gtit.ae2.json} 不会被自动注册，必须由代码显式提供。
 * <p>
 * <b>为什么不能靠 required=false / @Mixin(targets=...) + require=0</b>：Mixin 的 target 类缺失报错来自
 * {@code Option.DEBUG_TARGETS} 的严格性检查，与 json 的 {@code required} 无关，两种写法都防不住
 * 「AE2 未安装 → 启动崩溃」；{@code IMixinConfigPlugin#shouldApplyMixin} 也只解决单条 mixin 的取舍，
 * 配置本身仍需先被注册。参见 {@code plan/sum/13_MDK_Fix_sum.md:17} 记录的「靠 required:false 导致静默失效」教训。
 * <p>
 * <b>本类的引导时机</b>：unimixins 内置的
 * {@code com.gtnewhorizon.gtnhmixins.mixins.LateMixinOrchestrationMixin#beforeConstructing} 在 FML
 * Constructing 阶段之前扫描 ASMDataTable 中所有带 {@link LateMixin} 的类并调用其
 * {@link ILateMixinLoader}，故可在 AE2 类被真正加载前完成条件注册。
 * <p>
 * AE2 不是本模组的 {@code @Mod} 必需依赖（见 {@code main/GTInterestingThing.java:37} 的 dependencies 串里
 * 没有 appeng），所以这里必须按已加载 mod 条件施加；写法参照 GT-Not-Leisure 的
 * {@code com.science.gtnl.mixins.LateMixinLoader}。
 */
@LateMixin
public class GtitLateMixinLoader implements ILateMixinLoader {

    private static final Logger LOGGER = LogManager.getLogger("gtit");

    /** 与 {@code src/main/resources/mixins.gtit.ae2.json} 一一对应；该 json 有意不写 mixins/client 数组。 */
    private static final String AE2_MIXIN_CONFIG = "mixins.gtit.ae2.json";

    /**
     * AE2 的 FML modId。
     * <p>
     * ⚠ 这里必须是 {@code appliedenergistics2} 而不是包名 {@code appeng}：{@link #getMixins(Set)} 拿到的是
     * modId 集合（实机日志实证：写成 appeng 时 ae2Present 恒为 false，两枚 mixin 从未被施加，
     * 多通道静默退回 AE2 原生「一槽只登记第一个命中通道」的行为）。包名 {@code appeng} 只在
     * {@code @Mod} 依赖串与类路径里出现。
     */
    private static final String AE2_MOD_ID = "appliedenergistics2";

    /**
     * 仅在 AE2 存在时施加的 mixin 列表（相对 json 的 {@code package} 的点号子包名）。
     * <p>
     * 驱动器与 ME 箱两枚解决「登记侧」——让一个元件的<b>每个</b>已注册 {@code IAEStackType}
     * 都各自持有一份独立 handler 进入网格。搬运侧（ME-IO 端口）本提交尚未覆盖。
     */
    private static final List<String> AE2_MIXINS = Collections
        .unmodifiableList(Arrays.asList("ae2.MixinTileDrive", "ae2.MixinTileChest"));

    @Override
    public String getMixinConfig() {
        return AE2_MIXIN_CONFIG;
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        final boolean ae2Present = loadedMods != null && loadedMods.contains(AE2_MOD_ID);
        // 一次性取证日志：这行是否出现即判别 LateMixin 引导链是否跑通（缺席=unimixins 没扫到 @LateMixin，
        // 多通道会静默退回 AE2 原生单通道行为，而不是报错）
        LOGGER.info(
            "[gtit] LateMixin 引导命中：ae2Present={}，施加 {}（config={}）",
            ae2Present,
            ae2Present ? AE2_MIXINS : "无",
            AE2_MIXIN_CONFIG);
        if (ae2Present) {
            return AE2_MIXINS;
        }
        return Collections.emptyList();
    }
}
