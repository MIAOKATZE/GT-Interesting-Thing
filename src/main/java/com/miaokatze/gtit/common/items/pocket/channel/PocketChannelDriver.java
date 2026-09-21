package com.miaokatze.gtit.common.items.pocket.channel;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

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
 * <b>{@code selected} 的确切语义（R66b 的待证项，本轮已定死）</b>：
 * {@code InventoryPlayer.java:347} 传的是 {@code this.currentItem == i} ⇒ <b>只有当前手持的那一格为真</b>。
 * 也就是说本方法只在口袋<b>握在主手</b>时被调用。这不是缺陷而是 R24 的裁定口径
 * （"要求主手持有，与 {@code openFromMainHand} 自洽，且天然杜绝塞进箱子后继续跑"），
 * 因此门控<b>保持原样不摘</b>（R66b：证实之前不改；现已证实它恰好实现了裁定，改它才是偏离）。
 * 代价已按裁定对玩家显式声明：{@code gtit.pocket.held.note} + {@code item.neko_dimension_pocket.tooltip.7}。
 * 对照参考：GT5U {@code ItemGTToolbox.onUpdate} 干脆不使用第 5 个形参（它对全部 36 格都跑），
 * 本仓刻意比它严，是裁定而非疏漏。
 * <p>
 * <b>本方法每 tick 做且仅做三件事</b>（R57c①）：
 * <ol>
 * <li>没有活通道 ⇒ 一次 {@code Map.get} 判空即返回（<b>不建条目、不读 NBT、不分配 ops</b>）；</li>
 * <li>有活通道 ⇒ {@code PocketChannelManager.INSTANCE.tickShortChannel(uuid, bindings, ops,
 * {@link PocketChannelManager#pairsPerBatchFromConfig()})}；绑定表取激活时快照
 * （{@link PocketChannelState#sessionBindings()}），<b>绝不</b>每 tick 现解 NBT（R53c）；
 * {@code pairs} 走配置，不写字面量（R58b）；</li>
 * <li>跑完一批后把 {@code work} 位的剩余 tick 续到"本通道剩余总长"，通道自然结束即由
 * {@code ItemNekoDimensionPocket.onUpdate} 的递减归零自清理（R37/R62：动画走 NBT 相对倒计时，
 * 冷却才走墙钟）。</li>
 * </ol>
 * 推送 / 拉取的分支<b>不在本类</b>：那是 {@code openChannel} 激活时算一次的 {@code pullMode}（R39b），
 * 本类只是把这一拍交给状态机。
 * <p>
 * 顺带承担会话回收（R57c④ 的"防残留"）：界面已关、通道已停、12 格也没有东西 ⇒ 落盘并摘除
 * {@link PocketSessions} 条目，不留"没人 tick 也无人清理"的活会话。
 */
public final class PocketChannelDriver {

    private PocketChannelDriver() {}

    /**
     * 由 {@code ItemNekoDimensionPocket.onUpdate} 的<b>服务端且主手持有</b>分支调用（每 tick 一次）。
     */
    public static void onItemTick(ItemStack stack, World world, EntityPlayer player, int slot, boolean isHeld) {
        if (player == null || player.getGameProfile() == null) {
            return;
        }
        final UUID uuid = player.getGameProfile()
            .getId();
        final PocketChannelState state = PocketChannelManager.INSTANCE.peek(uuid);
        if (state == null || state.idle()) {
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
        final PocketChannelOps ops = new PocketAeChannelOps(player, stack, PocketCellProbe.INSTANCE, session);
        final boolean ranBatch = PocketChannelManager.INSTANCE
            .tickShortChannel(uuid, bindings, ops, PocketChannelManager.pairsPerBatchFromConfig());
        if (ranBatch) {
            // 只在批边界写一次 NBT（不是每 tick 写档，R53c）：动画窗口 = 本通道剩余总长
            ItemNekoDimensionPocket.startWorkTicks(
                stack,
                state.remainingBatches() * PocketConstants.CHANNEL_TICK_PERIOD + state.ticksUntilDue());
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
