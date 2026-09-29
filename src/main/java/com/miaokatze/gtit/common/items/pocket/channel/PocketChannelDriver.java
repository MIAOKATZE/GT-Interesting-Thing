package com.miaokatze.gtit.common.items.pocket.channel;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.common.items.pocket.ItemNekoDimensionPocket;
import com.miaokatze.gtit.common.items.pocket.PocketAeChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketCellBindings;
import com.miaokatze.gtit.common.items.pocket.PocketCellProbe;
import com.miaokatze.gtit.common.items.pocket.PocketChannelManager;
import com.miaokatze.gtit.common.items.pocket.PocketChannelOps;
import com.miaokatze.gtit.common.items.pocket.PocketChannelState;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.common.items.pocket.distill.PocketDistillDriver;

/**
 * 次元通道服务端的 <b>tick 宿主本体</b>（S6 / R57c）。
 * <p>
 * <b>为什么宿主是 {@code Item.onUpdate}</b>（R57a/R57b）：S1 的
 * {@code PocketChannelManager#openChannel/tickShortChannel/requestBurst} 在全仓<b>零调用方</b>，
 * 其 javadoc 承诺的 {@code ServerTickEvent} 监听器实测不存在 ⇒ 通道一拍不跑且不报错。
 * R57b 用 javap 证实、本批又用可读源码（{@code build/rfg/minecraft-src/java}）逐行确认了驱动通路：
 * {@code EntityPlayer.onLivingUpdate} → {@code InventoryPlayer.decrementAnimations()}
 * （{@code InventoryPlayer.java:341-349}，每 tick 遍历 {@code mainInventory} 全部 36 格）
 * → {@code ItemStack.updateAnimation}（{@code ItemStack.java:454-462}）
 * → <b>五参</b> {@code Item.onUpdate(stack, world, entity, slot, selected)}。
 * <p>
 * <b>{@code selected} 的确切语义（R66b 定死，★R95 作废其门控用法）</b>：
 * {@code InventoryPlayer.java:347} 传的是 {@code this.currentItem == i} ⇒ <b>只有当前手持的那一格为真</b>。
 * R66b 时代据此把门控钉在"主手持有"（R24 裁定）；★R95 升级插件体系<b>放宽</b>：宿主
 * {@code ItemNekoDimensionPocket.onUpdate} 已<b>摘掉 {@code !selected}</b>——背包 36 格任意位都推进
 * （通道/蒸馏/磁力；vanilla 证据 {@code InventoryPlayer.java:343-348} 对全部 36 格调
 * {@code updateAnimation}），箱子/饰品栏/盔甲位 vanilla 不 tick 物品故停（盔甲位走
 * {@code onArmorTick}，:351-357）。多枚口袋由下面的会话身份守卫保证只有"开界面那一枚"跑通道。
 * 旧"换手/收进背包即停摆"的玩家声明文案（{@code gtit.pocket.held.note}）自 R95 起口径过期，
 * lang 侧翻新属 S2b；★★<b>R98 后该族键号已整体漂移</b>（tooltip 激进裁剪 ⇒ 连号族 {@code 0..9} 重排成
 * {@code 0..3}；★R100 片 E 再收紧成 {@code 0..2}）：那句"内容随物品丢"现在住在 {@code tooltip.1}，
 * ★旧号 {@code .7} 在两份 lang 里<b>都不再存在</b>
 * ⇒ <b>按内容认、别照号抄</b>；它与主手口径无关，不得混引。
 * 对照参考：GT5U {@code ItemGTToolbox.onUpdate} 对全部 36 格都跑，R95 起本仓与它同形。
 * <p>
 * <b>本方法每 tick 做且仅做三件事</b>（R57c①；★R96 S5 给第 1 条补了"有位 ⇒ 保证有道"那一腿）：
 * <ol>
 * <li>没有活通道 ⇒ 先一次 {@code Map.get} 判空；★真空态若持续化<b>当前生效</b>且这一枚承载栈正是
 * 活会话认的那一枚，就经 {@link PocketChannelManager#ensurePersistentShortChannel} 就地装填一条
 * SHORT 通道（R95 缺的正是这一腿：位一置起，通道谁也开不起来 ⇒ 界面三处承诺全成了假读数）。
 * ★R100（需求 2）在此腿上补了<b>会话自动复原</b>：会话缺失时先经
 * {@link PocketChannelSessions#restoreIfBound} 复原一只 headless 会话（免开背包），
 * 三条守卫的顺序按成本排：{@code peek}（Map）→ 载体身份（引用比较）→ 位图（两次 byte 读）——
 * ★"不建条目、不分配 ops"照旧成立，被放宽的只有"不读 NBT"半句，且只在有活会话的口袋里发生
 * （没开过界面 ⇒ 第二道守卫就把这次读省掉了）；</li>
 * <li>有活通道 ⇒ {@code PocketChannelManager.INSTANCE.tickShortChannel(uuid, bindings, ops,
 * {@link PocketChannelManager#pairsPerBatchFromConfig()})}；绑定表取激活时快照
 * （{@link PocketChannelState#sessionBindings()}），<b>绝不</b>每 tick 现解 NBT（R53c）；
 * {@code pairs} 走配置，不写字面量（R58b）；</li>
 * <li>跑完一批后把 {@code work} 位的剩余 tick 续到"本通道剩余总长"，通道自然结束即由
 * {@code ItemNekoDimensionPocket.onUpdate} 的递减归零自清理（R37/R62：动画走 NBT 相对倒计时，
 * 冷却才走墙钟）。★R96 S5 起这一条同时是帧带「常亮」的<b>真读数来源</b>（B4 的裁定，见
 * {@code ItemNekoDimensionPocket#isChannelWorkLive}）：续上去的量 ≥ 一整拍 ⇒ 活通道在跑时
 * 该键永不 lapse，位在场而通道没跑时它必然 lapse。</li>
 * </ol>
 * 推送 / 拉取的分支<b>不在本类</b>：那是 {@code openChannel} 激活时算一次的 {@code pullMode}（R39b），
 * 本类只是把这一拍交给状态机。
 * <p>
 * 顺带承担会话回收（R57c④ 的"防残留"）：界面已关、通道已停、12 格也没有东西 ⇒ 落盘并摘除
 * {@link PocketSessions} 条目，不留"没人 tick 也无人清理"的活会话。
 */
public final class PocketChannelDriver {

    /** ★R85 D1 止损那条要留一行痕（"停道"是玩家可自恢复的重要事件，不是每拍噪声）。 */
    private static final Logger LOG = LogManager.getLogger("gtit");

    private PocketChannelDriver() {}

    /**
     * 由 {@code ItemNekoDimensionPocket.onUpdate} 的<b>服务端</b>分支调用（每 tick 一次；★R95 起背包
     * 36 格任意位都会到达，会话身份守卫见方法体）。
     */
    public static void onItemTick(ItemStack stack, World world, EntityPlayer player, int slot, boolean isHeld) {
        if (player == null || player.getGameProfile() == null) {
            return;
        }
        final UUID uuid = player.getGameProfile()
            .getId();
        final PocketChannelState state = PocketChannelManager.INSTANCE.peek(uuid);
        if (state == null || state.idle()) {
            // ★★R96 S5（TP-S5 决定性的那条腿）：把「有位常驻」接到一条真的会走的生产路上。
            // R95 的旧形状是「这一支无条件 retireIdleSession + return」，而全仓唯一能造出 SHORT 状态的
            // openChannel 又排在 NekoPocketServerHandler 同方法那条持续化早退<b>之后</b> ⇒
            // 位一置起就没人能把通道开起来，而下面那条回满腿要求 ranBatch、ranBatch 又要求已有活通道
            // ⇒ 闭环死锁（取证 r96-ret1 §2.4/§2.5：算式对，执法点从来没被问到）。
            // ★三条前置一条都不省：① 判据是组合谓词（关着开关 ⇒ 这条路跟着断，S1 的语义不被绕过）；
            // ② 会话必须在场且认的就是<b>这一枚</b>承载栈（缺这一判就是把 A 的绑定写到 B 的档上，
            // R85 D1 那一族的反例）；③ 绑定表非空由激活口自己把关（空表 ⇒ 一条只会空跑、还会把
            // 会话永久钉在内存里的通道）。装填只在真空态发生一次 ⇒ 常态成本仍是一次 Map.get。
            if (PocketUpgradeSwitches.isActive(stack, PocketUpgradeType.CHANNEL_PERSIST)) {
                PocketSession pending = PocketSessions.peek(uuid);
                if (pending == null) {
                    // ★★R100（需求 2「免开背包」）：会话缺失（登录后从未开过界面 / 服重启后）⇒ 就地复原一只
                    // headless 会话。会话构造与面板登记点同源（解析单源 = PocketChannelSessions#
                    // parseCarrierInventory，登记单源 = PocketSessions.register，禁第二份真相）；
                    // 前置与 S5 那三条同一套：isActive（就是外层这一判）、会话身份（复原出的会话认的就是
                    // 本枚承载栈）、绑定表非空（restoreIfBound 的便宜门 + 全档复核，空表 ⇒ null 零写入）。
                    // 复原出的会话恒 isOpen()=false ⇒ 与下面的 retireIdleSession 判据一致，
                    // 不会出现"刚复原就被退役"的振荡。
                    pending = PocketChannelSessions.restoreIfBound(player, stack);
                }
                if (pending != null && pending.carrierStack() == stack
                    && PocketChannelManager.INSTANCE
                        .ensurePersistentShortChannel(uuid, stack, pending.bindings(), pending.filters())) {
                    // 本拍只装填就返回：与 openChannel 的短效支同口径。★R100 起"开启瞬间立即首批"
                    // 由激活口自己装 due=0（fireFirstBatchNow）⇒ 下一拍（≤1 tick 后）就是第一批，
                    // 不再是"倒计时刚装整拍、白等一节拍"。★刻意不在此刻写 work 位：帧带的常亮由批边界
                    // 那条续写负责（B4 的"真读数"裁定），在这里抢写一次反而让"还没穿过一件货"的那一拍亮起来。
                    return;
                }
            }
            retireIdleSession(uuid);
            return;
        }
        final PocketCellBindings bindings = state.sessionBindings();
        if (bindings == null) {
            // 状态条目在、会话快照没了（停服/换档残留）⇒ 就地停道，绝不在无主对象上跑传输
            state.stop();
            PocketChannelManager.INSTANCE.forget(uuid);
            return;
        }
        // 承载栈以"此刻真的被 tick 到的这一枚"为准：关屏重定位与堆叠移动都不会让它写错对象
        state.retargetCarrier(stack);
        final PocketSession session = PocketSessions.peek(uuid);
        if (session == null || session.carrierStack() != stack) {
            // ★一个玩家只允许有一个活会话，且会话认的是"开界面的那一枚口袋"（对象身份）。
            // 玩家同时持有两枚口袋时，另一枚的 onUpdate 会走到这里 ⇒ 直接跳过：
            // 否则就是把 A 的绑定表与内容写到 B 的 NBT 上（跨口袋串档）。
            return;
        }
        if (session instanceof PocketChannelSessions.HeadlessCarrier) {
            // ★R100：headless 会话同步回填"此刻被 tick 的这一枚"（与上一行 state.retargetCarrier
            // 同一理由：堆叠移动/跨维重建后引用不陈旧；面板会话有它自己的 relocateCarrier，不走这里）。
            ((PocketChannelSessions.HeadlessCarrier) session).retargetCarrier(stack);
        }
        // ★R85 D1 止损（台账挂"另案未决"的那条，机制本轮被证具体）：本玩家只有<b>一份</b>通道状态，
        // 而它的 `sessionBindings()` 是<b>开道那一枚</b>口袋的绑定表实例；玩家随后改开另一枚口袋的面板时，
        // 会话与 carrier 都漂到 B，上面那条守卫就再也拦不住 ⇒ 结果是"A 绑定的元件搬 B 的中栏"。
        // 绑定表实例天然随口袋走，所以拿它当同一枚口袋的身份判据即可，零新增同步面：不一致就地停道，
        // 宁可停也不跨口袋搬件（停道是玩家可自行恢复的：重新按一次通道键）。
        if (state.sessionBindings() != session.bindings()) {
            LOG.warn(
                "[gtit] 口袋通道：玩家 {} 的通道跑在另一枚口袋的绑定表上（当前会话承载={}）⇒ 就地停道，不跨口袋搬运",
                uuid,
                Integer.toHexString(System.identityHashCode(stack)));
            state.stop();
            PocketChannelManager.INSTANCE.forget(uuid);
            retireIdleSession(uuid);
            return;
        }
        final PocketChannelOps ops = new PocketAeChannelOps(player, stack, PocketCellProbe.INSTANCE, session);
        final boolean ranBatch = PocketChannelManager.INSTANCE
            .tickShortChannel(uuid, bindings, ops, PocketChannelManager.pairsPerBatchFromConfig());
        if (ranBatch) {
            // ★R95 通道持续化：载体已固化 CHANNEL_PERSIST 位 ⇒ 批边界把剩余批次<b>回满</b>——这是
            // 「批边界续批」，不是独立状态机：不新开计数器、不绕过 PocketChannelState 的批次权威，
            // 复用 activate 的 SHORT 装填单点（remainingBatches 与 ticksUntilDue 都由它写；finishBatch
            // 刚把节拍装回<b>本次运行的节拍</b>，★R100 起持续化道按档位 tick 装填，这里同值重装无害）。
            // 因为每批跑完都立刻回满，remainingBatches 永远到不了 0 ⇒ finishBatch 的 stop() 分支与
            // tickShortChannel 的「批次用尽即回收」都结构性不可达。位图与档位每节拍读一次（每批一次，
            // R53c 量级可忽略）。
            // 免激活费免冷却：无激活事件可挂扣费点（R95 裁定）——持续化的成本语义就是
            // "一次性付过激活费后不再到 0"，不引入任何周期扣费。
            // ★R96 S1：判据换组合谓词 isActive（位图 ∧ ¬off-mask）⇒ 玩家关掉持续化之后这一行不再成立，
            // 回满停止 ⇒ remainingBatches 逐批衰减。
            // ★★★<b>R98 改判（需求 3）</b>：本处旧句「★刻意不主动 stop()：那等于替玩家发明第二条状态机
            // （R59e 那一族），自然衰减已足够」<b>自 R98 起作废</b>。作废的理由：它把"关掉一个开关"
            // 的效果推迟到最长 30 秒之后（上一次回满装好的 30 批逐秒衰减才归零），而那 30 秒里
            // 搬运<b>仍然分文不取、不进冷却</b> ⇒ 玩家读到的是"开关已经关了，货还在搬，还不要钱"。
            // 这不是"少一条状态机"的收益，而是"开关不成立"的代价。现在关持续化由<b>服务端开关写点</b>
            // 即刻停道：见 {@code NekoPocketServerHandler#performUpgradeSwitchToggle} 里
            // {@code type + Outcome.TURNED_OFF} 那一支 → {@code PocketChannelManager#stopChannel}
            // （stop 与 forget 成对，并清 {@code workTicks} 镜像）。
            // ★<b>本处代码一行不动</b>，回满腿继续存在：关掉持续化之后它只负责"<b>开着</b>时不断"。
            // 停道不落在这一支的理由：这一支对<b>所有</b> SHORT 通道成立，在这里补 else 会连带打死
            // "持续化从没开过、玩家刚花 2 闪烁币开的手动 30 批道"（状态条目里没有"这条道由持续化撑着"
            // 的位，无从区分）⇒ 那是候选 B/C 被判死的理由，也是本轮采边沿方案的唯一理由。
            if (state.mode() == PocketChannelState.Mode.SHORT
                && PocketUpgradeSwitches.isActive(stack, PocketUpgradeType.CHANNEL_PERSIST)) {
                // ★R100：回满装填的节拍按<b>载体 NBT 的频率</b>取（无键=默认 5s 档），不再用全局 1s 常量
                // ——频率写腿之外没有第二个改频入口，这里每批边界现读一次（每节拍一次的 NBT int 读，
                // R53c 量级可忽略）⇒ 玩家中途调频，下一批边界就换新节拍（另有 manager 的
                // retimePersistentChannel 让"调频"当场钳进新节拍）。
                // ★R101：读数换秒值口（新键 channelFreqSeconds 优先 + 旧档位键映射兼容，域 [1,60]），
                // 换算单源 channelFreqSecondsTicks。
                state.activate(
                    PocketChannelState.Mode.SHORT,
                    0L,
                    0L,
                    PocketConstants
                        .channelFreqSecondsTicks(PocketConstants.readChannelFreqSeconds(stack.getTagCompound())));
            }
            // 只在批边界写一次 NBT（不是每 tick 写档，R53c）：动画窗口 = 本通道剩余总长
            // （★R100：长度按<b>本次运行的节拍</b>算——持续化道按档位走时，1s 常量会把读数放大/缩小错档）
            ItemNekoDimensionPocket
                .startWorkTicks(stack, state.remainingBatches() * state.periodTicks() + state.ticksUntilDue());
            if (refreshLocationSnapshot(bindings)) {
                session.markDirty();
            }
            // 界面已关时写权在 driver 手上（R57c①）：把这一批造成的会话变化落回承载栈
            session.persistIdle();
        }
    }

    /**
     * 批边界把探针当前观测到的位置回填进绑定表快照（<b>只能用 {@code recordLocation}</b>，
     * 误用 7 参 {@code bind(...)} 会把已有定位一并改写 —— S1fix 风险⑥）。
     * <p>
     * 为什么必须做：GUI 的「维度+xyz」读的是探针（唯一真相），但绑定表里的快照是
     * <b>跨重启</b>缓存；没有这一步，绑定 tooltip 的状态位永远停在 {@code N}（从未定位），
     * {@code bind.stale}（曾有位置而现已失效）这条文案就成死路。
     * 探针未观测到的元件一律跳过（不写、不清），因此不会把"未知"写成"丢失"。
     *
     * @return 本次是否有快照真的变了（调用方据此决定是否打脏标记）
     */
    private static boolean refreshLocationSnapshot(PocketCellBindings bindings) {
        if (bindings == null) {
            return false;
        }
        boolean changed = false;
        for (String id : bindings.cells()) {
            final PocketCellProbe.Located at = PocketCellProbe.INSTANCE.locationOf(id);
            if (at == null) {
                continue;
            }
            changed |= bindings.recordLocation(id, at.dim, at.x, at.y, at.z, at.slot);
        }
        return changed;
    }

    /** 界面已关 + 通道已停 + 蒸馏格为空 ⇒ 会话落盘并回收（否则它会一直占着 128+12 格内存）。 */
    private static void retireIdleSession(UUID uuid) {
        final PocketSession session = PocketSessions.peek(uuid);
        if (session == null || session.isOpen() || PocketDistillDriver.hasInputs(session)) {
            return;
        }
        PocketDistillDriver.forget(uuid);
        PocketSessions.retire(uuid, session);
    }
}
