package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;
import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;
import com.miaokatze.gtit.gui.pocket.PocketSlots;

/**
 * ★<b>R87-d（缺陷 1「源质结晶放不进源质槽」）结晶点击入槽的服务端判定流</b>。
 * <p>
 * 背景：72 源质格是<b>非槽显示件</b>（不进 Container，{@code PocketSlots.java} 类注释），晶的
 * 唯一入账口在蒸馏/注入 12 格；玩家按字面往 72 格放晶 ⇒ 无槽可落、观感即"放不进去"。
 * 本类给出第三条入账路：<b>左键点 72 格、游标上持晶</b> ⇒ 服务端读游标栈、
 * <b>全有全无</b>转点数入账。判定流四态（与回执键一一对应）：
 * <ol>
 * <li>{@link Outcome#NOT_CRYSTAL}：手里不是源质结晶（空手/非晶/TC 判不出）⇒ 粘性回执
 * {@code gtit.pocket.essence.intake.not_crystal}，分毫不动；</li>
 * <li>{@link Outcome#NO_OWNER}：晶没有源质归属（{@code readContainer} 空）⇒ 粘性回执
 * {@code gtit.pocket.essence.intake.no_owner}，分毫不动；</li>
 * <li>{@link Outcome#NO_ROOM}：余量不足 ⇒ 粘性回执 {@code gtit.pocket.essence.intake.no_room}，
 * <b>晶分毫未动</b>（复用 12 格路径 {@code PocketSlots#injectCrystals} 的 {@code canAcceptAll}
 * {@code ESSENCE_CAP_PER_TAG}=256 上限口径，全有全无的失败面）；</li>
 * <li>{@link Outcome#ACCEPTED}：已入账（点数 = 单件 amount × 叠数）⇒ 成功回执
 * {@code gtit.pocket.essence.intake.ok}（含 tag 与点数），调用方清游标。</li>
 * </ol>
 * <p>
 * <b>目标 tag = 晶自带的 tag</b>（{@code PocketEssenceStore#assignCell} 幂等落回该 tag 既有格；
 * 点击格只是手势锚点，<b>服务端不读它</b>——本类签名里没有格号，就是这条纪律的执行形态）。
 * <p>
 * <b>游标清空在调用方</b>（面板）：本类保持纯判定 + 入账、不触玩家对象（零依赖套件可直接驱动四态）；
 * {@code player.inventory.setItemStack(null)} 走原版 {@code Container} 的 cursor 同步
 * （1.7.10 {@code sentItemStack} 比对）送达客户端，<b>客户端不得本地清游标</b>（防双端漂移）。
 */
public final class PocketEssenceIntake {

    /** 一次点击入槽的结论（四态互斥，对应三条失败回执键与一条成功键）。 */
    public enum Outcome {
        /** 手里不是源质结晶（空手 / 非晶 / TC 缺席判不出）。 */
        NOT_CRYSTAL,
        /** 晶没有源质归属（容器内容读出为空）。 */
        NO_OWNER,
        /** 源质格余量不足：全有全无失败，晶分毫未动。 */
        NO_ROOM,
        /** 已入账；调用方清游标并给成功回执。 */
        ACCEPTED
    }

    /** 判定结果：结论 + 入账的 tag 与点数（失败态 tag 为 {@code null}、点数为 0）。 */
    public static final class Result {

        public final Outcome outcome;
        /** ★晶自带的 tag（ACCEPTED 才非 {@code null}；回执文案与格位归属都以它为准）。 */
        public final String tag;
        /** 实际入账点数（单件 amount × 叠数；失败态恒 0）。 */
        public final int points;

        private Result(Outcome outcome, String tag, int points) {
            this.outcome = outcome;
            this.tag = tag;
            this.points = points;
        }

        /** 是否已入账（调用方据此清游标 + {@code markDirty}）。 */
        public boolean accepted() {
            return outcome == Outcome.ACCEPTED;
        }

        /** 本态的回执 lang 键（失败三条 = 粘性面板回执；成功一条 = 聊天播报，含 tag 与点数）。 */
        public String langKey() {
            switch (outcome) {
                case NO_OWNER:
                    return "gtit.pocket.essence.intake.no_owner";
                case NO_ROOM:
                    return "gtit.pocket.essence.intake.no_room";
                case ACCEPTED:
                    return "gtit.pocket.essence.intake.ok";
                case NOT_CRYSTAL:
                default:
                    return "gtit.pocket.essence.intake.not_crystal";
            }
        }
    }

    private PocketEssenceIntake() {}

    /**
     * 判定 + 入账本体（生产传 {@link EssenceGate#TAUM} 与服务端游标栈；
     * 回归套件传桩件直接钉四态）。★晶这一支<b>不碰</b> {@code drainContainer}
     * （生产实现对其恒返 EMPTY，抽干不销毁 = 危险态，见 {@code PocketSlots#injectCrystals} 的注）。
     *
     * @param carried 服务端游标栈（{@code player.inventory.getItemStack()}）；可为 {@code null}
     * @param store   源质表（入账落点）
     * @param gate    源质探针（晶识别与容器内容读取）
     */
    public static Result intake(ItemStack carried, PocketEssenceStore store, EssenceGate gate) {
        if (carried == null || carried.stackSize <= 0
            || store == null
            || gate == null
            || gate.capacityOf(carried) != TaumDistillRules.CRYSTAL_CAPACITY) {
            return new Result(Outcome.NOT_CRYSTAL, null, 0);
        }
        final TaumAspectAmounts content = gate.readContainer(carried);
        final String tag = firstTagOf(content);
        if (content == null || content.isEmpty() || tag == null) {
            // 无 NBT 裸晶（或内容空）：TC 侧会随机赋型，但那是它自己的节拍，本路径不替它造归属
            return new Result(Outcome.NO_OWNER, null, 0);
        }
        // 复用 12 格路径的晶支（canAcceptAll 全有全无预检 + putAll 入账 + 整叠换点数）：
        // 预检失败 ⇒ 源质表分毫未动、晶也分毫未动（调用方什么都不用退）。
        final PocketSlots.IntakeResult injected = PocketSlots.injectCrystals(carried, store, gate);
        if (injected.kind == PocketSlots.Intake.STORE_FULL) {
            return new Result(Outcome.NO_ROOM, tag, 0);
        }
        if (!injected.consumed() || injected.points <= 0) {
            // 理论不可达（内容非空 ⇒ candidates 非空 ⇒ 要么 STORE_FULL 要么入账成功）；兜底按无归属处理
            return new Result(Outcome.NO_OWNER, null, 0);
        }
        return new Result(Outcome.ACCEPTED, tag, injected.points);
    }

    /** 容器内容里的第一个有效 tag（晶恒单 aspect；多 tag 内容取首个有量的）。 */
    private static String firstTagOf(TaumAspectAmounts content) {
        if (content == null) {
            return null;
        }
        for (int index = 0; index < content.size(); index++) {
            final String tag = content.tagAt(index);
            if (tag != null && !tag.isEmpty() && content.amountAt(index) > 0) {
                return tag;
            }
        }
        return null;
    }

    /**
     * ★R87-f：某 tag 是否处于 ghost 声明之下（查询式遍历现役声明表，非轮询；给源质表的
     * {@code setDeclaredTagProbe} 保格谓词用——面板侧构造期注入 {@code tag -> isDeclaredEssenceTag(filters, tag)}）。
     */
    public static boolean isDeclaredEssenceTag(PocketFilterConfig filters, String tag) {
        if (filters == null || tag == null || tag.isEmpty()) {
            return false;
        }
        for (PocketFilterConfig.Filter filter : filters.filters()) {
            if (filter instanceof PocketFilterConfig.EssenceFilter essence && tag.equals(essence.tag)) {
                return true;
            }
        }
        return false;
    }
}
