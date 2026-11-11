// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.pocket.magnet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;

/**
 * Server-thread runtime claims for the two-stage magnet transfer. Claims contain no inventory
 * truth and never change entity contents; loss/release merely leaves the drop to native pickup.
 * Each owner has at most MAX_CLAIMS_PER_OWNER claims. Server END ticks expire them independently
 * of carrier activity; logout and world shutdown explicitly release the owner's runtime state.
 * Hover progress belongs to the claiming carrier, so another pocket cannot accelerate it.
 */
public final class PocketMagnetClaims {

    /** 单人同时在飞的认领上限（★有界：GTNH 矿机一次吐几千件时本表不跟着长）。 */
    public static final int MAX_CLAIMS_PER_OWNER = 64;

    /** 一条认领的最长寿命（服务端 END tick），与载体是否继续 tick 无关。 */
    public static final int CLAIM_STALE_TICKS = 100;

    /** 一条认领：实体 id、归属载体及各由载体/服务端推进的悬停与寿命计数。 */
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

        /** 归属载体推进悬停；寿命由 advanceTick 独立推进。 */
        private void advance() {
            if (hoverLeft > 0) {
                hoverLeft--;
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

    /** Server END tick owns expiry, including when no carrier is ticking. No entity/NBT is touched. */
    public static void advanceTick() {
        Iterator<Map.Entry<UUID, Owner>> owners = OWNERS.entrySet()
            .iterator();
        while (owners.hasNext()) {
            Owner owner = owners.next()
                .getValue();
            for (int i = owner.claims.size() - 1; i >= 0; i--) {
                Claim claim = owner.claims.get(i);
                if (--claim.staleLeft <= 0) {
                    owner.claims.remove(i);
                    LIVE_IDS.remove(claim.entityId);
                }
            }
            if (owner.claims.isEmpty() && owner.backoffScans <= 0) owners.remove();
        }
    }

    /** Runtime claims own no items. Releasing an owner leaves every dropped entity unchanged. */
    public static void forget(UUID owner) {
        Owner removed = OWNERS.remove(owner);
        if (removed != null) for (Claim claim : removed.claims) LIVE_IDS.remove(claim.entityId);
    }

    public static void reset() {
        OWNERS.clear();
        LIVE_IDS.clear();
    }

    /**
     * 推进这枚载体的悬停，把<b>本拍该收口</b>且<b>归属这枚载体栈</b>的条目
     * 追加进 {@code out}（★追加而非返回新表：调用方复用一份缓冲，热路径零分配）。
     * <p>
     * 寿命由 {@link #advanceTick()} 在服务端 END 推进，载体停止 tick 仍会过期。
     * 另一枚口袋既不能收取，也不能加速本载体的悬停。
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
            if (claim.carrier != carrier) {
                continue;
            }
            claim.advance();
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
