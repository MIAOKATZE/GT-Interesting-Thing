package com.miaokatze.gtit.common.items.pocket;

/**
 * 绑定<b>入口面</b>的四态判定（★R81①②，修「绑定只能绑定一个」）。
 * <p>
 * <b>为什么要单独立一个纯类</b>：修复前的 {@code performBind} 把两件事混成一次 {@code cellUuid == null}
 * 判断——「格子里根本没放元件」和「放了元件、但元件还没被分配身份」走同一条回执
 * （{@code bind.slot_hint} =「把元件放入此格」），而后者格子里<b>明明白白躺着</b>一枚元件 ⇒ 玩家
 * 读成"我放错了地方"，换个位置再试还是失败，观感就是<b>只能绑一个</b>（R81 根因）。
 * 判定与 MC 类型（{@code ItemStack} / {@code StorageManager}）分开后，这四态在 JVM 回归里
 * 就<b>可枚举、可断言</b>；MC 那一跳由 {@link Identity} 注进来，本类不碰。
 * <p>
 * <b>本类不知道也不该知道"元件"长什么样</b>：{@code candidateIsCell} 由调用方给（生产实现 =
 * {@code stack.getItem() instanceof IInfinityCellItem}），{@link Identity} 由调用方给（生产实现 =
 * 读 {@code PocketCellProbe.cellUuid} / 服务端调 {@code StorageManager.getStorage(ItemStack)}）。
 * <p>
 * ★四态各回<b>一个不同的 lang 键</b>（见 {@link #langKeyOf}）——"同身份重绑"在修复前是<b>完全静默</b>
 * 的（{@code bind()} 的返回值被丢掉），现在必须有可见回执。
 */
public final class PocketBindFlow {

    private PocketBindFlow() {}

    /**
     * 一次绑定请求的<b>互斥</b>结果。
     * <p>
     * ★枚举而不是布尔：布尔只能表达"成功/失败"，而这次缺陷的形态是"三种失败共用一条误导文案 +
     * 一种失败完全静默"，只有<b>各自有名</b>才拦得住回归。
     */
    public enum Result {

        /** 新身份已<b>追加</b>进绑定表（{@code PocketCellBindings} 的追加语义，R81 无罪面①）。 */
        ADDED,
        /** 格内不是元件（含空格子）：什么都不做，回执 {@code bind.slot_hint}。 */
        NOT_A_CELL,
        /** 是元件但<b>拿不到身份</b>：连服务端物化都失败（StorageManager 未初始化等）。 */
        NO_IDENTITY,
        /** <b>同身份重绑</b>：只刷新位置快照，条目数不变（修复前静默，现在必须说话）。 */
        DUPLICATE,
        /** 表已满且身份不在表内（沿用 R43a 的 {@code bind.full}，★不算本次四态之一）。 */
        FULL;

        /** 本次裁定的"四态"（表满另有旧键，不在四态里，见 {@code PocketCellBindings.bind} 的上限判定）。 */
        public static final Result[] FOUR_STATES = { ADDED, NOT_A_CELL, NO_IDENTITY, DUPLICATE };
    }

    /**
     * 元件身份的<b>读写面</b>（★两件事必须分开，因为"读"两端都能做、"写"只有服务端做得到）。
     * <p>
     * 生产实现见 {@code NekoPocketPanel.BindIdentity}；回归实现见 {@code NekoPocketModelTest} 里
     * 那枚"第一次读为空、物化后才出现身份"的假元件——那条正是 R81 的根因形状。
     */
    public interface Identity {

        /** 当前读到的身份；{@code null} = 元件 NBT 里还没有 {@code diskuuid}。 */
        String read();

        /**
         * 把身份<b>就地物化</b>进元件 NBT（★只许服务端调用，见 {@link #identityOrMaterialize}）。
         * 实现不需要返回值：物化后由 {@link #read()} 重读，避免"物化返回值"与"NBT 现值"两处真相。
         */
        void materialize();
    }

    /**
     * 读身份，读不到时在<b>服务端</b>补一次物化再重读。
     * <p>
     * ★{@code isServer == false} 时<b>绝不</b>调用 {@link Identity#materialize()}：客户端写物品 NBT
     * 永不到达服务端（本仓已证死），而且会在客户端那份临时栈上留下一个服务端不认识的身份——
     * 那正好是 {@code StorageManager} 注释里点名要防的"临时桶泄漏"。
     *
     * @param identity 读写面
     * @param isServer 当前是否服务端（生产调用点已经在 {@code serverGuardOk()} 之后，这里只是把
     *                 "客户端不许物化"这件事变成<b>可断言</b>的一条，而不是靠注释）
     * @return 身份，或 {@code null}
     */
    public static String identityOrMaterialize(Identity identity, boolean isServer) {
        if (identity == null) {
            return null;
        }
        final String first = identity.read();
        if (first != null || !isServer) {
            return first;
        }
        identity.materialize();
        // ★重读，不复用 materialize 的任何返回值：NBT 里现在写成了什么，才算是什么。
        return identity.read();
    }

    /**
     * 完整一次绑定：<b>非元件 → 无身份 → 表满 → 覆盖 / 追加</b>。
     * <p>
     * 判定顺序就是这条链，★不得调整：
     * <ul>
     * <li>{@code candidateIsCell} 先判，否则空格子会被发成"元件没身份"（新误导）；</li>
     * <li>身份为空才谈物化（{@link #identityOrMaterialize}），拿到身份前<b>不碰</b>绑定表；</li>
     * <li>表满判定沿用旧口径：{@code !hasRoom() && !contains(uuid)} ⇒ 已在表内的同身份重绑
     * <b>仍然</b>要能刷新位置快照，不能被"表满"挡掉（R43a 行为，逐字保留）；</li>
     * <li>最后由 {@code bind()} 的<b>返回值</b>分 ADDED / DUPLICATE——这个返回值在修复前被丢掉，
     * 是 R81 点名的次因。</li>
     * </ul>
     * 副作用只有一个：往 {@code bindings} 里追加或覆盖一条（写档与退栈仍归面板，本类不碰存档、
     * 不碰玩家背包）。
     *
     * @param bindings 目标绑定表（★不得为 {@code null}：表由 {@code PocketInventory} 构造，
     *                 传 null 就是调用点写错了，本类不替它编一个"无身份"的假象）
     */
    public static Result bind(PocketCellBindings bindings, boolean candidateIsCell, Identity identity,
        boolean isServer) {
        if (!candidateIsCell) {
            return Result.NOT_A_CELL;
        }
        final String uuid = identityOrMaterialize(identity, isServer);
        if (uuid == null || uuid.isEmpty()) {
            return Result.NO_IDENTITY;
        }
        if (!bindings.hasRoom() && !bindings.contains(uuid)) {
            return Result.FULL;
        }
        return bindings.bind(uuid, PocketConstants.MODE_DISK_UUID) ? Result.ADDED : Result.DUPLICATE;
    }

    /**
     * 结果 → 回执 lang 键（★四态必须各一个键，判据 {@code bind_receipt_distinguishes_four_states}
     * 就是把这几个键取出来两两比不等）。
     * <p>
     * {@code NOT_A_CELL} 复用旧键 {@code bind.slot_hint}——只有这一支的措辞（"把元件放入此格，
     * 再按绑定键"）与事实相符；{@code NO_IDENTITY} 与 {@code DUPLICATE} 是本次新增的
     * {@code bind.no_identity} / {@code bind.dup}，措辞只说"身份没能写入"与"已在表内"，
     * ★不得回头叫玩家放元件。
     */
    public static String langKeyOf(Result result) {
        if (result == null) {
            return "gtit.pocket.bind.slot_hint";
        }
        switch (result) {
            case ADDED:
                return "gtit.pocket.bind.added";
            case DUPLICATE:
                return "gtit.pocket.bind.dup";
            case NO_IDENTITY:
                return "gtit.pocket.bind.no_identity";
            case FULL:
                return "gtit.pocket.bind.full";
            case NOT_A_CELL:
            default:
                return "gtit.pocket.bind.slot_hint";
        }
    }
}
