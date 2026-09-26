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
 * <b>为什么仍然不能靠 required=false / {@code @Mixin(targets="…")} + require=0 这类「声明式可选」写法</b>：
 * ★先更正一处本注释历史上说错的话——「target 类缺失会<b>导致启动崩溃</b>」是<b>错的</b>，
 * 已被本仓归档的专用服冒烟日志证否：同一轮里就有两例目标类缺失只降级成 WARN 而<b>没有</b>中断启动
 * （{@code plan/smoketest/runs/20260909-090709-repeatable_pass/fml-server-latest.log:1013}
 * 的 {@code @Mixin target net.minecraft.client.renderer.RenderBlocks was not found
 * mixins.gtnhlib.early.json:…}，与 {@code :2284}（其配对 WARN 在 {@code :2282}）的
 * {@code @Mixin target MoreFunQuicksandMod.main.MFQM was not found mixins.endlessids.late.json:…}），
 * 两处之后 Mixin 都照常 {@code Preparing …} 下一个 config，同轮 {@code markers.log:22} 为
 * {@code FINAL PASS} 且断言 {@code no_crash_report new=0 unexpected_exception=0}。
 * 机制面互证：该报错的严格性开关是 {@code Option.DEBUG_TARGETS}，而 gtnhgradle 2.0.20 整个 jar 里
 * {@code DEBUG_TARGETS} token 出现 <b>0</b> 次（其 {@code MixinModule} 不注入该项），默认值即为关。
 * <p>
 * 但这<b>不改变</b>结论，只把失败形态改写得<b>更糟</b>：目标缺失不崩 ⇒ 「AE2 未安装」根本不需要防，
 * 真正防不住的是「config 压根没被注册 ⇒ 一行 WARN 都没有、mixin 静默不施加」。
 * {@code required=false} 与 {@code shouldApplyMixin} 都只在<b>配置已被注册</b>之后才起作用
 * （后者还只解决单条 mixin 的取舍），而上一段已经说明第二份 json 不会被自动注册；
 * 所以「靠 required:false 兜住可选依赖」这条路本身就是空的。
 * 参见 {@code plan/sum/13_MDK_Fix_sum.md:17} 记录的「靠 required:false 导致静默失效」教训——
 * 那条教训的措辞（「静默失效」而非「崩溃」）与本更正完全一致。
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
     * 驱动器、ME 箱与 IO 端口三枚。前两枚解决「登记侧」——让一个元件的<b>每个</b>已注册 {@code IAEStackType}
     * 都各自持有一份独立 handler 进入网格；第三枚 {@code ae2.MixinTileIOPort} 解决「搬运侧」——
     * {@code TileIOPort#getInv}（:488-502）原本用单值 {@code cachedInventory} 只缓存第一个命中的通道，
     * 现按该元件命中的全部通道做每 tick 轮转。
     * <p>
     * 之所以轮转是安全的：{@code tickingRequest} 的槽位循环里<b>没有</b>按 type 的外层循环，
     * 每槽每轮只在 :406 调一次 {@code getInv}，随后 :412 的 {@code getMEMonitor(inv.getStackType())}、
     * :414-415 的 {@code amountPerUnit}/{@code transferBudget}、:433 的 {@code shouldMove(inv, ...)}
     * 全部由这同一个 {@code inv} 派生；而 {@code transferContents} 的 {@code src}/{@code destination}
     * 是 :418/:420 传入的形参，:528 的 SIMULATE 与 :538 的 MODULATE 读的就是这对形参，中途不再查任何缓存。
     * 故「一次 {@code getInv} 返回一个自洽 handler」即足以杜绝跨通道错投，无需重写 tick 循环。
     * 任何解析异常都不取消原生实现，直接退回原生单通道行为。
     */
    private static final List<String> AE2_MIXINS = Collections
        .unmodifiableList(Arrays.asList("ae2.MixinTileDrive", "ae2.MixinTileChest", "ae2.MixinTileIOPort"));

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
