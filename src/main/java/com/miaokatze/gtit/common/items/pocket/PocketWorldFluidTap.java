package com.miaokatze.gtit.common.items.pocket;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtit.main.GTInterestingThing;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * ★新功能 N（S6，G-C）世界站抽液的<b>薄适配器</b>：把 MC/GT5U 的调用（门禁模拟读数、真抽、灌入口袋）
 * 收在这一处，决策（判因分类 / 落点选择 / want 算术）全部委托给纯函数 {@link PocketFluidExtraction}。
 * 入口是 {@code ItemNekoDimensionPocket#onItemUseFirst}（AUQ-④=b：<b>仅潜行</b>手势；
 * 非潜行与客户端一律 {@code return false} 完整放行，机器 GUI 原语义可达——那是入口侧的职责，不在本类）。
 * <p>
 * <b>调用链</b>（GT5U 事实，参考树 5.09.54.133）：{@code BaseMetaTileEntity.drain(UNKNOWN, max, doDrain)}
 * 的门禁是 {@code mTickTimer>5 && canAccessData() && (mRunningThroughTick || !mOutputDisabled)}
 * （:1901-1911），UNKNOWN 面免朝向/免 cover；先 {@code doDrain=false} 模拟（三段式，先例
 * {@code CoverPump → GTUtility.moveFluid}），再按 {@code want=min(模拟量, 落点余量)} 真抽 ⇒
 * 口袋必然装得下，不依赖「抽了再退」的兜底（差额注回仍留作防御，绝不静默丢弃）。
 * <p>
 * <b>持久契约（F1，双分支）</b>——世界侧写入不得与驱动 {@code persistIdle}/关屏写入交错出双真相：
 * <ol>
 * <li><b>有活会话且承载同一枚栈</b>（{@code PocketSessions.peek(uuid).carrierStack() == 手持栈}，
 * 按对象身份）：走会话模型——读 {@code fluidInTank}、写 {@code depositFluid}（内部即
 * {@code PocketInventory.fillOwnTank} 语义并置 dirty），落盘交给既有通路（GUI 开着 = 关屏钩子；
 * GUI 已关 = driver 的 {@code persistIdle}）。<b>绝不直改这枚栈的 NBT</b>（双写竞争）。</li>
 * <li><b>无活会话（或会话承载的是另一枚口袋）</b>：一次性 NBT 读改写——{@code PocketInventory.readFrom}
 * → {@code fillOwnTank} → dirty 才 {@code writeTo}（与开一次面板关一次屏完全同构，无每 tick 写档）。</li>
 * </ol>
 * <p>
 * <b>聊天白名单（AUQ-②=a）</b>：本文件与 {@code ItemNekoDimensionPocket}/{@code PocketFluidExtraction}
 * 是 pocket 域<b>仅有的</b>允许出现 {@code addChatMessage} 的文件，且聊天键一律 {@code gtit.pocket.world.}
 * 前缀（世界交互反馈不属 R88 裁定 3 的三类操作性回执——证据 C.4 已裁定）；
 * {@code verify-pocket.sh} R88① 段按此白名单改判（R90 §0.2-1）。
 */
public final class PocketWorldFluidTap {

    /** L10 防刷屏闩：每个「判因×分支」组合只 INFO 一次（有界组合数 ⇒ 日志有界）。 */
    private static final Set<String> L10_SEEN = new LinkedHashSet<>();

    /** 抽液读数异常只 WARN 一次（异常 MTE 不该让日志洪水淹没真问题）。 */
    private static boolean drainWarned = false;

    private PocketWorldFluidTap() {}

    /**
     * 潜行手持口袋右击 GT5U 机器：抽机器流体进口袋流体槽。
     *
     * @return 是否<b>拦截</b>本次方块交互（true = 机器 GUI 不开；按计划 §3-S6：实收 &gt;0 才拦截，
     *         一切「没搬动」的结局都 return false 放行原语义）
     */
    public static boolean tap(EntityPlayer player, ItemStack stack, BaseMetaTileEntity tile) {
        final long timer = tile.getTimer();
        // 三段式第一段：走门禁的模拟读数（大数探总量 ⇒ tier≥4 大罐能如实报「部分搬运」）
        final FluidStack sim = tryDrain(tile, Integer.MAX_VALUE, false);
        final int gateSim = sim == null || sim.getFluid() == null || sim.amount <= 0 ? 0 : sim.amount;
        // 门禁量 0 且非「太新」才需要分辨 U6/无液：绕门禁直探 MTE（IMetaTileEntity 本身是 IFluidHandler）
        int ungated = -1;
        if (gateSim <= 0 && timer > PocketFluidExtraction.GATE_TICK_THRESHOLD) {
            ungated = probeUngatedContent(tile);
        }
        final PocketFluidExtraction.Reason machine = PocketFluidExtraction.machineGate(timer, gateSim, ungated);
        if (machine != PocketFluidExtraction.Reason.GATE_OPEN) {
            logL10("判因=" + machine, timer, gateSim, ungated, 0, 0, 0, 0, false, -1, tile);
            chatGateReason(player, machine);
            return false;
        }

        // ---- 口袋侧视图：F1 双分支（会话在场 ⇒ 会话模型；缺席 ⇒ 一次性 NBT 读改写的载体） ----
        final UUID uuid = player.getGameProfile() == null ? null
            : player.getGameProfile()
                .getId();
        final PocketSession session = uuid == null ? null : PocketSessions.peek(uuid);
        // 按对象身份认栈：会话承载的是另一枚口袋（玩家开着 A 的面板、手持 B 右击）时绝不动 A 的内存真相
        final boolean viaSession = session != null && session.carrierStack() == stack;
        PocketInventory oneshot = null;
        if (!viaSession) {
            oneshot = PocketInventory.readFrom(stack.getTagCompound());
        }
        final int tankTotal = PocketConstants.FLUID_TANK_TOTAL;
        final int[] amounts = new int[tankTotal];
        final boolean[] compatible = new boolean[tankTotal];
        for (int tank = 0; tank < tankTotal; tank++) {
            final FluidStack own = viaSession ? session.fluidInTank(tank) : oneshot.ownTankFluid(tank);
            final int amount = own == null || own.amount <= 0 ? 0 : own.amount;
            amounts[tank] = amount;
            compatible[tank] = amount == 0 || own.getFluid() == sim.getFluid();
        }
        final PocketFluidExtraction.Plan plan = PocketFluidExtraction
            .plan(gateSim, amounts, compatible, PocketConstants.FLUID_BAR_CAPACITY_ML);
        if (plan.reason != PocketFluidExtraction.Reason.GATE_OPEN) {
            logL10("判因=" + plan.reason, timer, gateSim, ungated, 0, 0, 0, 0, viaSession, -1, tile);
            chat(player, "gtit.pocket.world.draw.pocket_full");
            return false;
        }

        // ---- 三段式第二/三段：按 want 真抽（want ≤ 落点余量 ⇒ 口袋必装得下）再灌入 ----
        final FluidStack drained = tryDrain(tile, plan.want, true);
        final int got = drained == null || drained.getFluid() == null || drained.amount <= 0 ? 0 : drained.amount;
        if (got <= 0) {
            // 模拟有量而真抽反悔（门禁在两次调用之间翻转）：按「无液或禁输出」合并口径如实报告
            logL10("真抽=0", timer, gateSim, ungated, plan.want, 0, 0, 0, viaSession, plan.tank, tile);
            chat(player, "gtit.pocket.world.draw.machine_empty_or_locked");
            return false;
        }
        final int received = viaSession ? session.depositFluid(plan.tank, drained)
            : oneshot.fillOwnTank(plan.tank, drained);
        final int leftover = got - received;
        if (leftover > 0) {
            pushBack(tile, drained, leftover, received);
        }
        // 无会话分支的一次性落盘：只有真的灌进去了才写（readFrom→writeTo 与开关一次面板同构）
        if (!viaSession && received > 0 && oneshot.isDirty()) {
            NBTTagCompound root = stack.getTagCompound();
            if (root == null) {
                root = new NBTTagCompound();
                stack.setTagCompound(root);
            }
            oneshot.writeTo(root);
        }
        final int remaining = PocketFluidExtraction.remainingAfter(gateSim, received);
        logL10(
            received > 0 ? remaining > 0 ? "部分搬运" : "全搬" : "未搬",
            timer,
            gateSim,
            ungated,
            plan.want,
            got,
            received,
            leftover,
            viaSession,
            plan.tank,
            tile);
        if (received <= 0) {
            chat(player, "gtit.pocket.world.draw.machine_empty_or_locked");
            return false;
        }
        final String fluidName = drained.getLocalizedName();
        if (remaining > 0) {
            chat(
                player,
                "gtit.pocket.world.draw.moved_partial",
                fluidName,
                Integer.valueOf(received),
                Integer.valueOf(remaining));
        } else {
            chat(player, "gtit.pocket.world.draw.moved", fluidName, Integer.valueOf(received));
        }
        return true;
    }

    // ------------------------------------------------------------------ 内部：读数 / 回填 / 聊天 / L10

    /** 门禁模拟/真抽的统一入口：任何 MTE 自实现抛错都不许炸掉玩家的一次右击。 */
    private static FluidStack tryDrain(BaseMetaTileEntity tile, int maxDrain, boolean doDrain) {
        try {
            return tile.drain(ForgeDirection.UNKNOWN, maxDrain, doDrain);
        } catch (Throwable t) {
            if (!drainWarned) {
                drainWarned = true;
                GTInterestingThing.LOG.warn("[PocketR89] 世界抽液：目标机器的 drain 抛错（已按抽不出处理）", t);
            }
            return null;
        }
    }

    /**
     * 绕过 BaseMetaTileEntity 门禁直探 MTE 存量（{@code getMetaTileEntity()} 公开且
     * {@code IMetaTileEntity extends IFluidHandler}）：只用于把「U6 禁输出」和「机器无液」分开。
     * 纯模拟（doDrain=false）不改任何状态；探不到（异常 / 无 MTE）返回 -1 = 口径并档。
     */
    private static int probeUngatedContent(BaseMetaTileEntity tile) {
        try {
            final IMetaTileEntity mte = tile.getMetaTileEntity();
            if (mte == null) {
                return -1;
            }
            final FluidStack probe = mte.drain(ForgeDirection.UNKNOWN, Integer.MAX_VALUE, false);
            return probe == null || probe.getFluid() == null || probe.amount <= 0 ? 0 : probe.amount;
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 防御性回注（设计上不可达：{@code want ≤ 落点余量} 与灌入同一套余量语义 ⇒ 实收恒=实抽）：
     * 真抽多于实收的差额必须原路注回机器，注不回去就 ERROR——绝不静默抹掉一滴。
     */
    private static void pushBack(BaseMetaTileEntity tile, FluidStack drained, int leftover, int received) {
        final FluidStack back = drained.copy();
        back.amount = leftover;
        int returned = 0;
        try {
            returned = tile.fill(ForgeDirection.UNKNOWN, back, true);
        } catch (Throwable t) {
            GTInterestingThing.LOG.error(
                "[PocketR89] 世界抽液：差额回注机器时抛错（实抽 {} 实收 {}，差额 {} 有丢失风险）",
                Integer.valueOf(received + leftover),
                Integer.valueOf(received),
                Integer.valueOf(leftover),
                t);
            return;
        }
        if (returned < leftover) {
            GTInterestingThing.LOG.error(
                "[PocketR89] 世界抽液：口袋只实收 {} 但机器被抽 {}，差额 {} 注回只回了 {}（此路径设计上不可达，请上报）",
                Integer.valueOf(received),
                Integer.valueOf(received + leftover),
                Integer.valueOf(leftover),
                Integer.valueOf(returned));
        }
    }

    private static void chatGateReason(EntityPlayer player, PocketFluidExtraction.Reason reason) {
        if (reason == PocketFluidExtraction.Reason.MACHINE_TOO_NEW) {
            chat(player, "gtit.pocket.world.draw.machine_new");
        } else if (reason == PocketFluidExtraction.Reason.MACHINE_LOCKED) {
            chat(player, "gtit.pocket.world.draw.machine_locked");
        } else if (reason == PocketFluidExtraction.Reason.MACHINE_EMPTY) {
            chat(player, "gtit.pocket.world.draw.machine_empty");
        } else {
            chat(player, "gtit.pocket.world.draw.machine_empty_or_locked");
        }
    }

    private static void chat(EntityPlayer player, String key, Object... args) {
        if (player == null) {
            return;
        }
        try {
            player.addChatMessage(new ChatComponentTranslation(key, args));
        } catch (Throwable t) {
            if (!drainWarned) {
                drainWarned = true;
                GTInterestingThing.LOG.warn("[PocketR89] 世界抽液：聊天回执发送失败", t);
            }
        }
    }

    /**
     * L10（S6）：入口读数——timer/门禁模拟量/绕门禁直探量/want/实抽/实收/退回/会话在场/tank 号/目标 MTE 类。
     * 「判因×分支」组合只 INFO 一次（防刷屏；组合数有界），之后的重复事件静默。
     */
    private static void logL10(String outcome, long timer, int gateSim, int ungated, int want, int got, int received,
        int leftover, boolean viaSession, int tank, BaseMetaTileEntity tile) {
        if (!L10_SEEN.add(outcome + (viaSession ? "/会话" : "/NBT"))) {
            return;
        }
        String mteClass = "?";
        try {
            final IMetaTileEntity mte = tile.getMetaTileEntity();
            mteClass = mte == null ? "无MTE"
                : mte.getClass()
                    .getSimpleName();
        } catch (Throwable ignored) {
            mteClass = "读数失败";
        }
        GTInterestingThing.LOG.info(
            "[PocketR89] 世界抽液 {}：MTE={} timer={} 门禁模拟量={} 直探量={} want={} 实抽={} 实收={} 退回={} 会话模型={} tank={}",
            outcome,
            mteClass,
            Long.valueOf(timer),
            Integer.valueOf(gateSim),
            Integer.valueOf(ungated),
            Integer.valueOf(want),
            Integer.valueOf(got),
            Integer.valueOf(received),
            Integer.valueOf(leftover),
            Boolean.valueOf(viaSession),
            Integer.valueOf(tank));
    }
}
