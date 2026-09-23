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
 * <b>载体分毫未动</b>（复用 12 格路径同源的 {@code canAcceptAll}（{@code PocketIntakeOps
 * #injectCarrierStack} → {@code ESSENCE_CAP_PER_TAG}=256 上限口径），全有全无的失败面）。★R88 换瓶后这条
 * 失败面<b>更容易被踩到</b>：一叠满瓶就是 8×64 = 512 点 &gt; 256 ⇒ 整叠点进 72 格必然 {@code NO_ROOM}
 * （旧晶一叠最多 64 点，结构上撞不到这条）。这不是吞点（东西还在游标上、回执也发、<b>也不退壳</b>），
 * 但玩家要看懂"先拆一小撮再点"；★游标侧的逐只向下收口<b>刻意不做</b>（游标只有一个栈，收口出来的
 * "半叠满瓶 + 半叠空瓶"没有第二个落点，硬做就是 R83 那类"少了东西却说不动"换皮）；</li>
 * <li>{@link Outcome#ACCEPTED}：已入账（点数 = 单件 amount × 叠数 ⇒ 满瓶一叠是 8×N 点）⇒ 成功回执
 * <b>按档分键</b>（★R91-o：瓶档 {@code gtit.pocket.essence.intake.ok.phial} / 晶档 {@code ...ok.crystal}，
 * 两档各说各的退件事实——旧版两档共用一条键、括注只说退瓶，晶档玩家读到的是假话）。★R91-④：本态同时带
 * {@link Result#refund}（瓶档 = 等量空壳；晶档 = {@code null}），调用方据此结算游标。</li>
 * </ol>
 * <p>
 * <b>目标 tag = 载体自带的 tag</b>（{@code PocketEssenceStore#assignCell} 幂等落回该 tag 既有格；
 * 点击格只是手势锚点，<b>服务端不读它</b>——本类签名里没有格号，就是这条纪律的执行形态）。
 * <p>
 * ★★<b>R91-④（瓶往返改判）：点击入槽<b>溶掉的是载体里的源质，玻璃本体原路退回</b></b>——
 * 游标上 N 只<b>满瓶</b>溶进盘 ⇒ 退回 N 只<b>空瓶</b>（旧文案那段"游标没有退空壳的落点、于是按消耗处理"
 * <b>已被本裁定撤销</b>，落点就在 {@link Result#refund} 与面板的 {@code performEssenceIntake}）。
 * ★但这条<b>只对瓶成立</b>：R86「晶化源质入槽 {@code CONSUMED}、不退空壳」<b>对晶继续有效</b>
 * （无 NBT 的晶留在场会被 TC 服务端随机重赋型 = 销毁价值之外的第二条危害）。两条口径的唯一分派表
 * 住 {@link PocketIntakeOps#refundsEmptyCarrier(int)}，★面板与本类都不许再判一遍档位。
 * <p>
 * ★★<b>R90 E3（D1 手势三分）起：空瓶不再从客户端走到这里</b>——左键分流层
 * （{@code NekoPocketPanel#dispatchEssenceCellPress}）按 {@link #isEmptyPhialCarrier} 把空瓶改派
 * 「格→瓶取出」新动作；但本类的 {@link Outcome#NO_OWNER} 拒收<b>原样保留作纵深防御</b>：
 * 伪造 / 旧客户端 / 分流竞态下空瓶仍会落进 {@code intake}，语义仍是"分毫不动 + 粘性回执"，
 * 不因上游新增分流而放水。
 * <p>
 * <b>游标结算在调用方（面板）</b>：本类保持纯判定 + 入账、不触玩家对象（零依赖套件可直接驱动四态与
 * {@link Result#refund}）。★R91-④ 之后"结算"不再只有清游标一种：瓶档要把 {@code refund} 那叠<b>空瓶放回
 * 游标</b>（晶档 {@code refund == null} ⇒ 仍是清游标）——两种写法都必须走
 * {@code syncManager.setCursorItem(...)} 那一条正解，理由见下面 R88 B1 那段。
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

    /** 一次点击入槽的结论（失败态各回一条回执键、成功一条）。 */
    public enum Outcome {
        /** 手里不是源质载体（空手 / 非容器 / TC 缺席判不出）。★R88：态名保留，语义已含瓶。 */
        NOT_CRYSTAL,
        /** 载体没有源质归属（容器内容读出为空：无 NBT 裸晶、<b>空瓶</b>同样落这一态）。 */
        NO_OWNER,
        /** 源质格余量不足：全有全无失败，载体分毫未动。 */
        NO_ROOM,
        /**
         * ★★<b>R91-⑤（记忆 L 的源质执法腿）</b>：本格被 alt+左 挂了<b>记忆</b>、而记的<b>不是</b>本次
         * 这一种 ⇒ 整笔拒收（源质表分毫未动、载体分毫未动、<b>也不退壳</b>）。
         * <p>
         * ★与 {@link #NO_ROOM} 分开成两态的理由：那是"格满了"、这是"格不许放这个"，玩家看到的
         * 修法完全不同（等补满 vs 换一种东西 / 撤掉记忆）；并成一条就是"把两种失败说成一句话"。
         * ★只有四参形态（服务端生产）<b>可能</b>返这一态：三参旧形态不判这道闸 ⇒ 既有回归用例逐字不变。
         */
        MEMORY_LOCKED,
        /** 已入账；调用方清游标并给成功回执。 */
        ACCEPTED
    }

    /** 判定结果：结论 + 入账的 tag 与点数（失败态 tag 为 {@code null}、点数为 0）+（★R91-④）该退回的容器。 */
    public static final class Result {

        public final Outcome outcome;
        /** ★载体自带的 tag（ACCEPTED 才非 {@code null}；回执文案与格位归属都以它为准）。 */
        public final String tag;
        /** 实际入账点数（★R88：满瓶一叠 = 8 × 叠数，旧晶一叠 = 1 × 叠数；失败态恒 0）。 */
        public final int points;
        /**
         * ★★<b>R91-④：这一笔入槽该原路退回游标的空壳</b>（{@code null} ⇒ 什么都不退）。
         * <p>
         * 只有<b>瓶档</b>非空，且<b>只数 == 被溶掉的只数</b>；<b>晶档恒 {@code null}</b>
         * （R86「晶入槽 {@code CONSUMED} 不退空壳」只对晶继续成立）；三条失败态同样 {@code null}。
         * 判档不在此处——那张表是 {@link PocketIntakeOps#refundsEmptyCarrier(int)} 的单源，
         * ★面板不得再判一遍。本字段是<b>抽干后的副本</b>（游标本体没被碰过），调用方必须经
         * {@code syncManager.setCursorItem(refund)} 落它，裸写 {@code inventory.setItemStack} 到不了客户端
         * （R88 B1 的刷取根因）。
         */
        public final ItemStack refund;

        private Result(Outcome outcome, String tag, int points) {
            this(outcome, tag, points, null);
        }

        private Result(Outcome outcome, String tag, int points, ItemStack refund) {
            this.outcome = outcome;
            this.tag = tag;
            this.points = points;
            this.refund = refund;
        }

        /** 是否已入账（调用方据此结算游标 + {@code markDirty}）。 */
        public boolean accepted() {
            return outcome == Outcome.ACCEPTED;
        }

        /** ★R91-④：本次是否真的退了空壳（面板的落点分派用这一条，不在面板里数瓶子档位）。 */
        public boolean refundsCarrier() {
            return refund != null && refund.stackSize > 0;
        }

        /**
         * 本态的回执 lang 键（失败三条 = 粘性面板回执；成功一条同样应走<b>面板回执</b>——
         * ★R88 裁定"此类回执不进聊天框"，本方法只交键、不决定投递面；今天还在往聊天框发的那一句
         * 住在面板侧，改道由 E2 承接，键名与占位不变）。
         * <p>
         * ★★<b>R91-o：ACCEPTED 一档按载体档分键</b>（瓶档 {@code .ok.phial} / 晶档 {@code .ok.crystal}）。
         * 旧版两档共用一条 {@code intake.ok} 而括注只说退瓶 ⇒ 玩家溶晶时读到假话（晶档不退壳，R86 只对晶
         * 继续成立）。★分档读的是 {@link #refundsCarrier()}——它就是 {@code PocketIntakeOps#
         * refundsEmptyCarrier} 那张单源分派表在本结果上的<b>投影读数</b>，本方法不判档位、不数瓶子；
         * 三条失败态的键不受影响。
         */
        public String langKey() {
            switch (outcome) {
                case NO_OWNER:
                    return "gtit.pocket.essence.intake.no_owner";
                case NO_ROOM:
                    return "gtit.pocket.essence.intake.no_room";
                case ACCEPTED:
                    return refundsCarrier() ? "gtit.pocket.essence.intake.ok.phial"
                        : "gtit.pocket.essence.intake.ok.crystal";
                case MEMORY_LOCKED:
                    // ★R91-⑤：本格被记忆锁成"只能放另一种东西"⇒ 面板回执（★不进聊天框，R88 C3 同一条）
                    return "gtit.pocket.essence.intake.memory_locked";
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
     * 回归套件传桩件直接钉四态）。★R91-④：两档载体都走 {@link PocketIntakeOps#injectCarrierStack}
     * 那一条<b>入账 + 按档退件</b>的单点（瓶档退回等量空壳、晶档整叠销毁零退件），乘法仍是
     * {@code PocketIntakeOps#scaledByStackSize} 那一条（★本支<b>不碰</b> {@code drainContainer}——
     * 抽干发生在单点里，且只在瓶档）。
     * <p>
     * ★<b>R91-⑤</b>：本形态 = 不判记忆闸（旧行为逐字不变，供既有回归用例与不关心属性的调用方）；
     * 服务端生产走四参形态 {@link #intake(ItemStack, PocketEssenceStore, EssenceGate, MemoryGate)}。
     *
     * @param carried 服务端游标栈（{@code player.inventory.getItemStack()}）；可为 {@code null}
     * @param store   源质表（入账落点）
     * @param gate    源质探针（载体识别与容器内容读取）
     */
    public static Result intake(ItemStack carried, PocketEssenceStore store, EssenceGate gate) {
        return intake(carried, store, gate, null);
    }

    /**
     * ★<b>R91-⑤ 的 L 执法腿（源质支）注入点</b>：本格是否被挂了<b>记忆</b>且记的不是这一种。
     * <p>
     * ★刻意做成<b>注入接口</b>而不是在本类直读 {@code PocketFilterConfig} + {@code cellOf}：
     * ① 判据（attr 的读法、"记的是不是这一种"的比较）住在 {@code PocketFilterConfig} 那<b>一张</b>位表里，
     * 本类只问结论；② 源质格的归属是<b>动态</b>的（R86：扣到 0 当场腾格），"哪个 tag 在哪一格"只有
     * 服务端权威表知道，纯 JVM 件不许自己猜；③★零依赖套件必须能注入桩件把它驱动成真的判过
     * （R59b 那条"判据不入桩件就是假绿同族陷阱"的同一条理由）。
     */
    public interface MemoryGate {

        /**
         * @param tag 本次入槽要落的 tag（已从载体内容解出）
         * @return true ⇒ 该 tag 当前所在格被 alt+左 记成了<b>别的东西</b> ⇒ 本次入槽必须整笔不落地拒收
         */
        boolean isMemoryLockedOtherThan(String tag);
    }

    /**
     * ★R91-⑤ 四参形态（服务端生产唯一入口）：多带一道<b>记忆闸</b>，其余三条失败态与入账算术
     * <b>一字不改</b>；{@code memory == null} ⇒ 不判（与三参形态逐字同行为）。
     */
    public static Result intake(ItemStack carried, PocketEssenceStore store, EssenceGate gate, MemoryGate memory) {
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
        // ★★<b>R91-⑤ 的 L 执法腿（源质支）</b>：记忆格 = "本格只能放那一种东西"，★纯过滤、不产生抽取。
        // 落在<b>入账之前</b>（{@code canAcceptAll} 那一步之上）：源质表分毫未动、载体分毫未动、
        // 也不退壳 ⇒ 调用方什么都不用做（与 NO_ROOM 那一条同形）。
        // ★probe == null（旧三参形态 = 回归套件的既有调用）⇒ 不判这一道，行为与 R90 逐字一致。
        if (memory != null && memory.isMemoryLockedOtherThan(tag)) {
            return new Result(Outcome.MEMORY_LOCKED, tag, 0);
        }
        // 复用 12 格路径同源的"读出 × 叠数 + canAcceptAll 全有全无预检 + putAll 入账"原语，并按档退件
        // （预检失败 ⇒ 源质表分毫未动、载体也分毫未动、也不退壳 ⇒ 调用方什么都不用做）。
        final PocketIntakeOps.IntakeResult injected = PocketIntakeOps.injectCarrierStack(carried, store, gate);
        if (injected.kind == PocketIntakeOps.Intake.STORE_FULL) {
            return new Result(Outcome.NO_ROOM, tag, 0);
        }
        if (injected.points <= 0 || !(injected.consumed() || injected.drained())) {
            // 理论不可达（内容非空 ⇒ candidates 非空 ⇒ 要么 STORE_FULL 要么入账成功）；兜底按无归属处理
            return new Result(Outcome.NO_OWNER, null, 0);
        }
        return new Result(Outcome.ACCEPTED, tag, injected.points, injected.returnedCarrier);
    }

    /**
     * ★★<b>R91-④：一次取出点击<b>实际灌几只</b>的唯一算式</b>（面板只调它，不再自己写第二份取整）。
     * <p>
     * 三段算术，每段都是既有单源的转发：
     * <ol>
     * <li><b>请求量</b>按 shift 分档：Shift = 该格整份（{@code stock}），非 Shift = 一次动作上界
     * {@link PocketConstants#ESSENCE_OUT_MAX_POINTS_PER_ACTION}（★与 R90 之前"空游标取出支"同一条上界，
     * 换的是<b>落点</b>不再是<b>量</b>）；</li>
     * <li><b>向下取整到整瓶</b>走 {@link TaumDistillRules#floorToPhialUnits}（★R88 自立口径 C1 的唯一实现点，
     * 余数原地留盘）；</li>
     * <li><b>游标上的空瓶只数</b>是硬上界——★本仓<b>没有</b>"凭空造瓶"这条路（R91-④ 撤销 R90 D1 的自造瓶口），
     * 一只空瓶换一只满瓶，一只也没有 ⇒ 一支都不产。</li>
     * </ol>
     *
     * @param carriedPhials 游标上<b>空安瓿瓶</b>的只数（调用方须先用 {@link #isEmptyPhialCarrier} 验过档位）
     * @param stock         该格现有点数
     * @param shift         是否 Shift 支（整份）
     * @return 应灌只数；{@code 0} ⇒ 一支都不产、一分都不扣（空游标 / 凑不满一瓶 / 该格没存量）
     */
    public static int phialsToFill(int carriedPhials, int stock, boolean shift) {
        if (carriedPhials <= 0 || stock <= 0) {
            // ★空手 / 持非容器（白名单已挡）或该格无存量 ⇒ 零产出，不是"产一只空的"
            return 0;
        }
        final int whole = TaumDistillRules.floorToPhialUnits(requestedPoints(stock, shift));
        if (whole < PocketConstants.ESSENCE_OUT_UNIT_POINTS) {
            return 0;
        }
        return Math.min(carriedPhials, whole / PocketConstants.ESSENCE_OUT_UNIT_POINTS);
    }

    /**
     * ★R91-④：与 {@link #phialsToFill} 同一条请求量口径下的<b>留盘余数</b>读数（C1"余数留盘"要说出来的那一半）。
     * 两条读数读的是<b>同一个</b> {@link #requestedPoints}，★面板不得自己再写一遍 {@code stock % 8}。
     */
    public static int leftoverOnFloor(int stock, boolean shift) {
        if (stock <= 0) {
            return 0;
        }
        final int wanted = requestedPoints(stock, shift);
        return wanted - TaumDistillRules.floorToPhialUnits(wanted);
    }

    /**
     * 本次动作请求的点数（★<b>R91-e 改判</b>后的 shift 二分，仍是唯一算式；
     * {@link #phialsToFill} 与 {@link #leftoverOnFloor} 共用同一个它）。
     * <p>
     * 裁定原文：<b>左键 = 消耗 1 只空瓶、装 1 只</b>；<b>shift+左键 = 装到既有单动作上限</b>。
     * ★<b>不新增常量、不新增第二套算式</b>：单点那一档用的就是
     * {@link PocketConstants#ESSENCE_OUT_UNIT_POINTS}（"一只瓶 = 几点"这件事本来只有一个数），
     * 批量那一档用的就是 R84 立的 {@link PocketConstants#ESSENCE_OUT_MAX_POINTS_PER_ACTION}
     * （经 {@code min(stock, …)} 收口，与 shift 支过去读的同一枚上界）。
     * <p>
     * ★与旧（S2 版）算式的差只有一处：过去单点也允许"一次把游标那一叠全灌满"（上界是
     * {@code min(手持只数, 64)}），与账本 R91-④ 字面「消耗 <b>1</b> 只空瓶」不等价 ⇒ 按 R91-e 收窄。
     */
    private static int requestedPoints(int stock, boolean shift) {
        final int perActionCeiling = shift ? PocketConstants.ESSENCE_OUT_MAX_POINTS_PER_ACTION
            : PocketConstants.ESSENCE_OUT_UNIT_POINTS;
        return Math.min(stock, perActionCeiling);
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
