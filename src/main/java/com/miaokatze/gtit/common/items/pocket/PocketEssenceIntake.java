package com.miaokatze.gtit.common.items.pocket;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtit.common.items.pocket.distill.EssenceGate;
import com.miaokatze.gtit.crossmod.taum.TaumAspectAmounts;
import com.miaokatze.gtit.crossmod.taum.TaumDistillRules;

/**
 * ★<b>R87-d（缺陷 1「源质结晶放不进源质槽」）点击入槽的服务端判定流</b>。
 * <p>
 * ★<b>R88 换载体</b>：能点进去的东西现在是<b>源质瓶</b>（{@code ItemEssence}，8 点/只），
 * <b>旧晶</b>（1 点/枚）按裁定 C2 <b>仍可点进去</b>（不吃件），只是本仓不再产出它。
 * 本类的态名与 lang 键里的 "crystal" 字样因此<b>刻意保留</b>（改名会打断 E2 与离线套件的既有引用），
 * 语义一律读成"源质载体"；单一判据见 {@link #isAcceptedCarrierCapacity(int)}。
 * <p>
 * 背景：72 源质格是<b>非槽显示件</b>（不进 Container，{@code PocketSlots.java} 类注释），载体的
 * 常规入账口在蒸馏/注入 12 格；玩家按字面往 72 格放东西 ⇒ 无槽可落、观感即"放不进去"。
 * 本类给出第三条入账路：<b>左键点 72 格、游标上持载体</b> ⇒ 服务端读游标栈、
 * <b>全有全无</b>转点数入账。判定流四态（与回执键一一对应）：
 * <ol>
 * <li>{@link Outcome#NOT_CRYSTAL}：手里不是源质载体（空手/非容器/TC 判不出）⇒ 粘性回执
 * {@code gtit.pocket.essence.intake.not_crystal}，分毫不动；</li>
 * <li>{@link Outcome#NO_OWNER}：载体没有源质归属（{@code readContainer} 空，例：空瓶）⇒ 粘性回执
 * {@code gtit.pocket.essence.intake.no_owner}，分毫不动；</li>
 * <li>{@link Outcome#NO_ROOM}：余量不足 ⇒ 粘性回执 {@code gtit.pocket.essence.intake.no_room}，
 * <b>载体分毫未动</b>（复用 12 格路径 {@code PocketIntakeOps#injectCrystals} 的 {@code canAcceptAll}
 * {@code ESSENCE_CAP_PER_TAG}=256 上限口径，全有全无的失败面）。★R88 换瓶后这条失败面<b>更容易被踩到</b>：
 * 一叠满瓶就是 8×64 = 512 点 &gt; 256 ⇒ 整叠点进 72 格必然 {@code NO_ROOM}（旧晶一叠最多 64 点，
 * 结构上撞不到这条）。这不是吞点（东西还在游标上、回执也发），但玩家要看懂"先拆一小撮再点"，
 * 收口办法（游标侧也按整瓶向下收口 + 把余量写回游标）需要面板配合，已进报告的待裁决项；</li>
 * <li>{@link Outcome#ACCEPTED}：已入账（点数 = 单件 amount × 叠数 ⇒ 满瓶一叠是 8×N 点）⇒ 成功回执
 * {@code gtit.pocket.essence.intake.ok}（含 tag 与点数），调用方清游标。</li>
 * </ol>
 * <p>
 * <b>目标 tag = 载体自带的 tag</b>（{@code PocketEssenceStore#assignCell} 幂等落回该 tag 既有格；
 * 点击格只是手势锚点，<b>服务端不读它</b>——本类签名里没有格号，就是这条纪律的执行形态）。
 * <p>
 * ★<b>点击入槽消耗的是载体本身</b>（瓶与旧晶都一样整叠销毁，走 {@code injectCrystals} 那条
 * "读出 × 叠数 + 不退件"的原语）——与通道下传的"读容器 → 加点 → 就地消耗容器"同一条口径。
 * 差别要说清楚：12 格那条路（{@code PocketIntakeOps#injectContainer}）对瓶是<b>抽干后把空壳退回玩家</b>，
 * 而游标这一条没有"退回空壳"的落点（要退回就得由面板改写游标栈，那是 E2 的形状），
 * 于是玻璃瓶身按消耗处理。该不对称已进报告，留给主代理裁决（不是本轮擅自扩出来的新行为）。
 * <p>
 * ★★<b>R90 E3（D1 手势三分）起：空瓶不再从客户端走到这里</b>——左键分流层
 * （{@code NekoPocketPanel#dispatchEssenceCellPress}）按 {@link #isEmptyPhialCarrier} 把空瓶改派
 * 「格→瓶取出」新动作；但本类的 {@link Outcome#NO_OWNER} 拒收<b>原样保留作纵深防御</b>：
 * 伪造 / 旧客户端 / 分流竞态下空瓶仍会落进 {@code intake}，语义仍是"分毫不动 + 粘性回执"，
 * 不因上游新增分流而放水。
 * <p>
 * <b>游标清空在调用方（面板）</b>：本类保持纯判定 + 入账、不触玩家对象（零依赖套件可直接驱动四态）。
 * ★★<b>R88 更正一条被上游源码证伪的旧断言</b>（旧文："{@code player.inventory.setItemStack(null)}
 * 走原版 {@code Container} 的 cursor 同步（1.7.10 {@code sentItemStack} 比对）送达客户端，
 * 客户端不得本地清游标"）：<b>那句不成立</b>。取证 {@code plan/_taskpack/r88-essentia/06-main-agent-probes.md}
 * P1：MUI2 的 {@code ModularSyncManager.java:128-131} 里 {@code setCursorItem(stack)} =
 * {@code getPlayer().inventory.setItemStack(stack)} <b>再加上</b> {@code CursorSlotSyncHandler#sync()}
 * （{@code CursorSlotSyncHandler.java:11-13} 用 {@code NetworkUtils.writeItemStack} 把服务端游标现值
 * 推到客户端，{@code :16-17} 客户端直接 {@code setItemStack}），而<b>裸写</b> {@code setItemStack(null)}
 * 只改服务端、<b>不走那半程推送</b> ⇒ 客户端游标上仍留着那一叠，玩家落包/丢地就是凭空复制（R83 抓到的
 * "刷晶"根因）。参数可以为 {@code null}（写的是服务端现值，null 即原版空游标编码）。
 * ⇒ 服务端清/换游标<b>必须</b>用 {@code syncManager.setCursorItem(...)}（面板取出/取瓶那一支本来就在用它，
 * R88 前只有清游标这一处漏了半程）；客户端仍然<b>不得</b>本地清游标（防双端漂移，这半句旧断言是对的）。
 */
public final class PocketEssenceIntake {

    /** 一次点击入槽的结论（四态互斥，对应三条失败回执键与一条成功键）。 */
    public enum Outcome {
        /** 手里不是源质载体（空手 / 非容器 / TC 缺席判不出）。★R88：态名保留，语义已含瓶。 */
        NOT_CRYSTAL,
        /** 载体没有源质归属（容器内容读出为空：无 NBT 裸晶、<b>空瓶</b>同样落这一态）。 */
        NO_OWNER,
        /** 源质格余量不足：全有全无失败，载体分毫未动。 */
        NO_ROOM,
        /** 已入账；调用方清游标并给成功回执。 */
        ACCEPTED
    }

    /** 判定结果：结论 + 入账的 tag 与点数（失败态 tag 为 {@code null}、点数为 0）。 */
    public static final class Result {

        public final Outcome outcome;
        /** ★载体自带的 tag（ACCEPTED 才非 {@code null}；回执文案与格位归属都以它为准）。 */
        public final String tag;
        /** 实际入账点数（★R88：满瓶一叠 = 8 × 叠数，旧晶一叠 = 1 × 叠数；失败态恒 0）。 */
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

        /**
         * 本态的回执 lang 键（失败三条 = 粘性面板回执；成功一条同样应走<b>面板回执</b>——
         * ★R88 裁定"此类回执不进聊天框"，本方法只交键、不决定投递面；今天还在往聊天框发的那一句
         * 住在面板侧，改道由 E2 承接，键名与占位不变）。
         */
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
     * ★R88：<b>"这一栈是不是可以点进源质盘的载体"的唯一判据</b>——按 {@code capacityOf} 的<b>档位</b>认，
     * 不认物品类（R31/R44a 的接口探测纪律）。
     * <p>
     * 收两档：<b>瓶</b>（{@link TaumDistillRules#PHIAL_CAPACITY}，现役载体，8 点/只）与
     * <b>旧晶</b>（{@link TaumDistillRules#CRYSTAL_CAPACITY}，裁定 C2 的只读支 ⇒ 仍进得了账、
     * 但本仓不再产出）。第三方罐（{@link TaumDistillRules#CAPACITY_UNKNOWN}）<b>刻意不收</b>：
     * 档位无证据 ⇒ 点数读得出来但"一叠值几点"没把握，游标上销毁它就是把猜当真相。
     * <p>
     * ★给 E2 的接缝：客户端预筛（面板 {@code requestEssenceIntake} 那一步）与 NEI 拖入判据
     * （{@code NekoEssenceGhostCell#carriesTag}）应改调本谓词，<b>不要</b>在 GUI 里再抄一遍
     * "== CRYSTAL_CAPACITY || == PHIAL_CAPACITY"（那就是第二处真相，R85 耦合审计点名的形状）。
     *
     * @param capacityOfStack {@code EssenceGate#capacityOf} / {@code TaumCompat#capacityOf} 的返回值
     */
    public static boolean isAcceptedCarrierCapacity(int capacityOfStack) {
        return capacityOfStack == TaumDistillRules.PHIAL_CAPACITY
            || capacityOfStack == TaumDistillRules.CRYSTAL_CAPACITY;
    }

    /**
     * ★<b>R91-⑦：「拖入栈 → 声明 tag」的唯一单源判据</b>（声明侧 tag 反解收敛到这一条）。
     * <p>
     * 读法三档，按优先级：
     * <ol>
     * <li><b>本格 tag 优先</b>（{@code cellTag} 非空 ⇒ 原样返回，<b>不受拖入物影响</b>）——与
     * {@code NekoEssenceGhostCell#tagOfCarrier} 的 R90 E3（D3）既有语义逐字一致；</li>
     * <li><b>本格无归属 ⇒ 真容器的首个有量 tag</b>（{@code gate.readContainer} + {@link #firstTagOf}，
     * 即 TC 满瓶 / 带 NBT 旧晶 / 第三方罐共用那一条）；</li>
     * <li><b>仍读不出 ⇒ 白名单伪物品的 NBT tag</b>（{@code gate.pseudoAspectTag}：ARI 的
     * {@code aspectrecipeindex:aspect} 一类配方显示件，注册名精确白名单 + TC {@code Aspect} 复核）。</li>
     * </ol>
     * 三档都读不出 ⇒ {@code null}（空瓶、无 NBT 裸晶、普通物品一律 {@code null} ⇒ 既有的
     * "组不出键就拒收"入口生效）。
     * <p>
     * ★★<b>本判据只覆盖「声明侧」，绝不参与计点</b>——载体清单与边界（R91-⑦ 原文口径）：
     * <table border="1">
     * <tr>
     * <th>载体</th>
     * <th>声明（本判据）</th>
     * <th>入库存计点（{@link #isAcceptedCarrierCapacity}）</th>
     * </tr>
     * <tr>
     * <td>伪物品 {@code item.aspect} + String 型 aspect 键</td>
     * <td>只声明</td>
     * <td><b>绝不入库存</b></td>
     * </tr>
     * <tr>
     * <td>TC 满瓶（{@code PHIAL_CAPACITY}）</td>
     * <td>声明 + 入槽</td>
     * <td>收（8 点/只）</td>
     * </tr>
     * <tr>
     * <td>带 NBT 旧晶（{@code CRYSTAL_CAPACITY}）</td>
     * <td>声明 + 入槽</td>
     * <td>收（1 点/枚，C2 只读支）</td>
     * </tr>
     * <tr>
     * <td>第三方罐（{@code CAPACITY_UNKNOWN}）</td>
     * <td>声明</td>
     * <td>刻意不收（档位无证据）</td>
     * </tr>
     * <tr>
     * <td>空瓶 / 无 NBT 裸晶</td>
     * <td colspan="2">拒收（读不出 tag）</td>
     * </tr>
     * </table>
     * 计点闸门 {@code capacityOf ∈ {瓶, 晶}} <b>一个字都不动</b>：伪物品不是容器 ⇒ 档位天然落
     * {@code CAPACITY_NOT_A_CONTAINER} ⇒ {@code intake} 对它恒 {@code NOT_CRYSTAL}，
     * <b>不需要也不允许</b>再加第二道门（第二道门就是第二处真相）。
     * <p>
     * ★刻意落在本类（纯 JVM 件、{@code EssenceGate} 可注入桩件）而不是 {@code TaumBridge} 或 GUI：
     * 桥接层只在 TC 在场时加载 ⇒ 判据放那里零依赖套件只能拿到"恒空"的假绿（R59b 同族陷阱）；
     * 放 GUI 则声明侧与入槽侧各抄一份（{@code verify-pocket.sh} 的 {@code R91-a2} 段钉
     * "GUI 里不得出现字面键与注册名"）。第三方解析本体只住 {@code crossmod/taum/} 一份。
     *
     * @param stack   拖入 / 游标上的栈；可为 {@code null}
     * @param cellTag 本格已归属的 tag（{@code null}/空 = 无归属 ⇒ 回落读栈）
     * @param gate    源质探针（生产传 {@link EssenceGate#TAUM}）
     * @return 该次声明使用的 tag；三档都读不出 ⇒ {@code null}
     */
    public static String declarationTagOf(ItemStack stack, String cellTag, EssenceGate gate) {
        if (stack == null || gate == null) {
            return null;
        }
        if (cellTag != null && !cellTag.isEmpty()) {
            return cellTag;
        }
        final String containerTag = firstTagOf(gate.readContainer(stack));
        if (containerTag != null) {
            return containerTag;
        }
        final String pseudoTag = gate.pseudoAspectTag(stack);
        return pseudoTag == null || pseudoTag.isEmpty() ? null : pseudoTag;
    }

    /**
     * ★R90 E3（D1 手势三分）的<b>预筛唯一判据</b>：载体容器内容是否非空。
     * <p>
     * 内容读数只走 {@code gate.readContainer}（生产 = {@code EssenceGate.TAUM} → {@code TaumCompat} →
     * {@code TaumBridge#readContainer}，即 {@code IEssentiaContainerItem.getAspects} 口径：无 NBT / 空
     * {@code AspectList} / TC 缺席一律读成空）——<b>本方法就是那条"内容读数 helper"的唯一落点</b>，
     * 客户端分流（{@code NekoPocketPanel#dispatchEssenceCellPress}）与服务端复验
     * （{@code NekoPocketPanel#performEssenceOutToPhial}）两侧同调它，GUI 不抄第二份逻辑。
     * 刻意落在本类（纯 JVM 件、{@code EssenceGate} 可注入桩件）而不是 {@code TaumBridge}：
     * 桥接层只在 TC 在场时被加载，判据放那里零依赖套件只能拿到"恒空"的假绿（R59b 偏离①同族陷阱）。
     */
    public static boolean carriesEssence(ItemStack carrier, EssenceGate gate) {
        return carrier != null && gate != null
            && !gate.readContainer(carrier)
                .isEmpty();
    }

    /**
     * ★R90 E3（D1）「格→瓶取出」的<b>载体白名单</b>：仅<b>空瓶</b>——{@code capacityOf} 档位恰为
     * {@link TaumDistillRules#PHIAL_CAPACITY}（= TC {@code ItemEssence}，isPhial 的接口探测口径）且
     * meta 0（TC 空瓶位）且内容空（{@link #carriesEssence} 为 false）。
     * <p>
     * <b>C2 白名单的落点</b>：旧晶（{@code CRYSTAL_CAPACITY}）、第三方罐（{@code CAPACITY_UNKNOWN}）、
     * 非容器与满瓶一律 {@code false} ⇒ 它们<b>不得触发</b>格→瓶取出（满瓶走既有入槽支，晶走识别支，
     * 其余交回 {@code super} 由"游标已被占用"回执说话）。判据三件全走 {@code gate}（桩件可注入），
     * 与 {@link #isAcceptedCarrierCapacity(int)} 一样是"会不会动游标上那叠东西"的唯一执法点。
     */
    public static boolean isEmptyPhialCarrier(ItemStack carrier, EssenceGate gate) {
        if (carrier == null || carrier.stackSize <= 0 || gate == null) {
            return false;
        }
        if (carrier.getItemDamage() != 0) {
            // TC ItemEssence：meta 0 = 空瓶 / meta 1 = 装瓶；meta 1 即便 NBT 被第三方清空也不是本支的"空瓶"
            return false;
        }
        if (gate.capacityOf(carrier) != TaumDistillRules.PHIAL_CAPACITY) {
            return false;
        }
        return !carriesEssence(carrier, gate);
    }

    /**
     * 判定 + 入账本体（生产传 {@link EssenceGate#TAUM} 与服务端游标栈；
     * 回归套件传桩件直接钉四态）。★R88：两档载体都走 {@code PocketIntakeOps#injectCrystals} 那条
     * "读出 × 叠数 + 整叠消耗、不退件"的原语（游标没有"退回空壳"的落点，见类注释那条不对称说明），
     * 所以这一支<b>不碰</b> {@code drainContainer}（生产实现对晶恒返 EMPTY，抽干不销毁 = 危险态）。
     *
     * @param carried 服务端游标栈（{@code player.inventory.getItemStack()}）；可为 {@code null}
     * @param store   源质表（入账落点）
     * @param gate    源质探针（载体识别与容器内容读取）
     */
    public static Result intake(ItemStack carried, PocketEssenceStore store, EssenceGate gate) {
        if (carried == null || carried.stackSize <= 0
            || store == null
            || gate == null
            || !isAcceptedCarrierCapacity(gate.capacityOf(carried))) {
            return new Result(Outcome.NOT_CRYSTAL, null, 0);
        }
        final TaumAspectAmounts content = gate.readContainer(carried);
        final String tag = firstTagOf(content);
        if (content == null || content.isEmpty() || tag == null) {
            // 无 NBT 裸晶 / 空瓶（或内容空）：TC 侧会随机给晶赋型，但那是它自己的节拍，本路径不替它造归属
            return new Result(Outcome.NO_OWNER, null, 0);
        }
        // 复用 12 格路径的"整叠消耗"原语（canAcceptAll 全有全无预检 + putAll 入账 + 整叠换点数，
        // ★R88 起这条乘法对瓶给出 8×叠数）：预检失败 ⇒ 源质表分毫未动、载体也分毫未动（调用方什么都不用退）。
        final PocketIntakeOps.IntakeResult injected = PocketIntakeOps.injectCrystals(carried, store, gate);
        if (injected.kind == PocketIntakeOps.Intake.STORE_FULL) {
            return new Result(Outcome.NO_ROOM, tag, 0);
        }
        if (!injected.consumed() || injected.points <= 0) {
            // 理论不可达（内容非空 ⇒ candidates 非空 ⇒ 要么 STORE_FULL 要么入账成功）；兜底按无归属处理
            return new Result(Outcome.NO_OWNER, null, 0);
        }
        return new Result(Outcome.ACCEPTED, tag, injected.points);
    }

    /**
     * 容器内容里的第一个有效 tag（源质载体恒单 aspect：满瓶一个 tag、晶一个 tag；多 tag 内容取首个有量的）。
     * <p>
     * ★R90 E3（D3）起公开单源：点击入槽（本类 {@link #intake}）与 NEI 无归属格建档
     * （{@code NekoEssenceGhostCell#tagOfCarrier}）读的是<b>同一条</b>"拖入物自带 tag"判据，
     * 两处各写一遍就是两处真相（首 tag 的取舍漂移会让入槽与建档对同一栈给出不同归属）。
     */
    public static String firstTagOf(TaumAspectAmounts content) {
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
