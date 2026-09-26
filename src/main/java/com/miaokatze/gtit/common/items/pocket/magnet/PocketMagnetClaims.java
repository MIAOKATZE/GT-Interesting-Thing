package com.miaokatze.gtit.common.items.pocket.magnet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;

/**
 * 磁力位移的<b>进程内认领表</b>（★R96 S6，候选 B 的<b>唯一</b>新状态；计划 §5 S6 允许清单里的"磁力支撑件"）。
 * <p>
 * <b>为什么非要有跨拍状态</b>（取证结论，不是偏好）：服务端<b>同一拍</b>内 {@code setPosition} + {@code setDead()}
 * 客户端<b>什么都看不见</b>——实体本来就在它的视野里，服务端只发一条"移除"，位置更新不会单独成帧
 * （{@code r96-eva2.md §2.3} 的"关键物理事实"）。所以"东西飞到我身上"这个观感<b>在结构上必须跨拍</b>：
 * 先位移（本表登记），下一拍再收口（入账 + 摘除同一拍做完）。
 * <p>
 * <b>为什么它不是第二份存档真相</b>——三条同时成立才允许它存在：
 * <ol>
 * <li><b>不落 NBT</b>：整张表一个字节都不写档（R53c 禁每 tick 写档；S6 明令禁止把认领状态塞进栈 NBT）。</li>
 * <li><b>可整体重建且丢掉无害</b>：被认领的实体在认领期间<b>内容一个字节都没被改过</b>（入账只发生在收口那一拍，
 * 且入账与 {@code setDead()} 同拍）。条目丢了最坏的结果是那件掉落物留在原地由原版重新判拾取 ⇒
 * <b>既不丢件也不复制</b>。这是它和"写死的绝对到期"（R59e 要消灭的形态）的本质区别：本表不持有任何到期<b>时刻</b>，
 * 只有逐拍递减的<b>整数</b>，跨维重建至多让某一拍早到或晚到。</li>
 * <li><b>有界</b>：每人最多 {@value #MAX_CLAIMS_PER_OWNER} 条（超了就这一轮不吸，下一轮再说），
 * 每条最长 {@value #CLAIM_STALE_TICKS} 拍（载体栈消失／玩家离线时靠它自然淘汰，防残留）。
 * 本类不提供 {@code clearAll} 这类"承诺了但没人调"的收口口——按 {@code PocketSessions} 的纪律，
 * 淘汰必须由调用路径自己完成（见 {@link #ripeClaims}）。</li>
 * </ol>
 * <p>
 * <b>成本口径</b>：驱动每拍问一次 {@link #ripeClaims}；无认领时是一次 {@code Map.get} + 空表判，
 * <b>零分配、零遍历、零 NBT</b>。有认领时按条 aging，条数上界见上。
 * <p>
 * <b>多人／多枚口袋的仲裁</b>：条目按<b>载体栈的对象身份</b>归属——A 口袋吸的东西只由 A 口袋那一拍收口，
 * 不因为同玩家另一枚口袋先跑到就把东西塞进另一枚。跨玩家的重复抓取由 {@link #isClaimed} 的全局 id 索引挡住
 * （配合驱动侧 {@code getClosestPlayerToEntity} 的最近玩家判据，S6 验收 4）。
 * <p>
 * 单线程访问（服务器主线程），与 {@code PocketSessions}／{@code PocketChannelManager} 同口径，不加锁。
 */
public final class PocketMagnetClaims {

    /** 单人同时在飞的认领上限（★有界：GTNH 矿机一次吐几千件时本表不跟着长）。 */
    public static final int MAX_CLAIMS_PER_OWNER = 64;

    /** 一条认领的最长寿命（tick）：载体栈消失／玩家离线时靠它自然淘汰。 */
    public static final int CLAIM_STALE_TICKS = 100;

    /** 一条认领：实体 id + 归属载体（对象身份）+ 两个逐拍递减的整数。 */
    public static final class Claim {

        private final int entityId;
        private final ItemStack carrier;
        private int hoverLeft;
        private int staleLeft;

        Claim(int entityId, ItemStack carrier, int hoverTicks) {
            this.entityId = entityId;
            this.carrier = carrier;
            this.hoverLeft = hoverTicks;
            this.staleLeft = CLAIM_STALE_TICKS;
        }

        /** 被认领的实体 id（收口时用它 {@code world.getEntityByID}，<b>不</b>再扫 AABB）。 */
        public int entityId() {
            return entityId;
        }

        /** 归属的载体栈（<b>对象身份</b>，与 {@code PocketSession#carrierStack()} 同口径）。 */
        public ItemStack carrier() {
            return carrier;
        }

        /** 本条是否已悬停到位（到期 ⇒ 这一拍收口）。 */
        public boolean isRipe() {
            return hoverLeft <= 0;
        }

        /** 推进一拍：悬停与寿命各减一（★递减的是整数，不是"和绝对时刻比大小"）。 */
        private void advance() {
            if (hoverLeft > 0) {
                hoverLeft--;
            }
            if (staleLeft > 0) {
                staleLeft--;
            }
        }
    }

    private static final class Owner {

        private final ArrayList<Claim> claims = new ArrayList<>();
        /** 满载退避：还剩几次扫描整轮不做位移（★P-11「满载不吸」，只在扫描拍递减）。 */
        private int backoffScans;
    }

    private static final Map<UUID, Owner> OWNERS = new HashMap<>();

    /** 全局实体 id 索引（跨玩家／跨口袋挡住"同一件被两枚口袋同时认领"）。 */
    private static final Set<Integer> LIVE_IDS = new HashSet<>();

    private PocketMagnetClaims() {}

    /**
     * 登记一条认领。
     *
     * @return 是否登记成功；{@code false} = 该实体已被认领（别人的／上一拍的）或本表已满 ⇒ 调用方<b>不要</b>位移它
     */
    public static boolean claim(UUID owner, int entityId, ItemStack carrier, int hoverTicks) {
        if (owner == null || carrier == null) {
            return false;
        }
        if (LIVE_IDS.contains(entityId)) {
            return false;
        }
        Owner target = OWNERS.get(owner);
        if (target == null) {
            target = new Owner();
            OWNERS.put(owner, target);
        }
        if (target.claims.size() >= MAX_CLAIMS_PER_OWNER) {
            return false;
        }
        target.claims.add(new Claim(entityId, carrier, hoverTicks));
        LIVE_IDS.add(entityId);
        return true;
    }

    /** 该实体是否已在飞（全局判据，与归属无关）。 */
    public static boolean isClaimed(int entityId) {
        return LIVE_IDS.contains(entityId);
    }

    /**
     * 推进某一玩家的全部认领（悬停与寿命各减一拍），把<b>本拍该收口</b>且<b>归属这枚载体栈</b>的条目
     * 追加进 {@code out}（★追加而非返回新表：调用方复用一份缓冲，热路径零分配）。
     * <p>
     * 寿命耗尽的条目在这里被<b>就地淘汰</b>（载体栈已被丢掉／玩家离线 ⇒ 没人会再收口它）；
     * 归属别的栈的条目只 aging，由那一枚自己收口。
     *
     * @return {@code out} 里的条数（空表早退返回 0，且不碰 {@code out}）
     */
    public static int ripeClaims(UUID owner, ItemStack carrier, ArrayList<Claim> out) {
        if (owner == null || carrier == null) {
            return 0;
        }
        final Owner entry = OWNERS.get(owner);
        if (entry == null) {
            // 无在飞认领：一次 Map.get 即返回 ⇒ 零分配、零遍历、零 NBT
            return 0;
        }
        if (entry.claims.isEmpty()) {
            pruneIfIdle(owner, entry);
            return 0;
        }
        for (int i = entry.claims.size() - 1; i >= 0; i--) {
            final Claim claim = entry.claims.get(i);
            claim.advance();
            if (claim.staleLeft <= 0) {
                entry.claims.remove(i);
                LIVE_IDS.remove(claim.entityId);
                continue;
            }
            if (claim.carrier != carrier) {
                continue;
            }
            if (claim.isRipe()) {
                out.add(claim);
            }
        }
        pruneIfIdle(owner, entry);
        return out.size();
    }

    /** 收口完成（入账并摘除）或实体已消失 ⇒ 摘掉认领。 */
    public static void release(UUID owner, int entityId) {
        if (owner == null) {
            return;
        }
        final Owner entry = OWNERS.get(owner);
        if (entry != null) {
            for (int i = 0; i < entry.claims.size(); i++) {
                if (entry.claims.get(i).entityId == entityId) {
                    entry.claims.remove(i);
                    break;
                }
            }
            pruneIfIdle(owner, entry);
        }
        LIVE_IDS.remove(entityId);
    }

    /**
     * 本次扫描是否应<b>整轮不做位移</b>（满载退避中）。命中时把计数减一拍——本方法<b>只</b>应在
     * 扫描拍（{@code SCAN_PERIOD_TICKS} 到点）被调用，所以单位是"扫描次数"而不是 tick。
     */
    public static boolean isScanCooling(UUID owner) {
        if (owner == null) {
            return false;
        }
        final Owner entry = OWNERS.get(owner);
        if (entry == null || entry.backoffScans <= 0) {
            return false;
        }
        entry.backoffScans--;
        pruneIfIdle(owner, entry);
        return true;
    }

    /** 一次 0 成交 ⇒ 接下来 {@code scans} 次扫描不做位移（★P-11 满载不吸；取最大值，不被重复失败拉长）。 */
    public static void noteScanBackoff(UUID owner, int scans) {
        if (owner == null || scans <= 0) {
            return;
        }
        Owner entry = OWNERS.get(owner);
        if (entry == null) {
            entry = new Owner();
            OWNERS.put(owner, entry);
        }
        if (scans > entry.backoffScans) {
            entry.backoffScans = scans;
        }
    }

    /** 只要真的收进过东西，退避立刻归零（玩家掏空口袋后下一拍就该恢复手感）。 */
    public static void noteProductive(UUID owner) {
        if (owner == null) {
            return;
        }
        final Owner entry = OWNERS.get(owner);
        if (entry != null) {
            entry.backoffScans = 0;
        }
    }

    /** 某玩家当前在飞的认领条数（观测与用例用）。 */
    public static int claimCount(UUID owner) {
        final Owner entry = owner == null ? null : OWNERS.get(owner);
        return entry == null ? 0 : entry.claims.size();
    }

    /** 全表当前认领条数（观测与用例用）。 */
    public static int liveClaimCount() {
        return LIVE_IDS.size();
    }

    /** 某玩家的退避还剩几次扫描（观测与用例用）。 */
    public static int scanBackoffLeft(UUID owner) {
        final Owner entry = owner == null ? null : OWNERS.get(owner);
        return entry == null ? 0 : entry.backoffScans;
    }

    /** 空壳不留：既没认领也没退避就把条目摘掉（否则本表只增不减）。 */
    private static void pruneIfIdle(UUID owner, Owner entry) {
        if (entry.claims.isEmpty() && entry.backoffScans <= 0) {
            OWNERS.remove(owner);
        }
    }
}
