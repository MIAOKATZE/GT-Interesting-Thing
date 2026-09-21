package com.miaokatze.gtit.common.items.pocket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 口袋活会话的<b>唯一</b>注册表（服务端内存，按玩家 {@code UUID} 一条）。
 * <p>
 * 存在的理由：{@code Item.onUpdate} 宿主（S6 通道一拍 / S7 蒸馏节拍）必须读"当前那份内存真相"，
 * 而那份真相由面板会话持有（R22/R53c：一次读、关屏一次写）。会话因此不能只活在 GUI 生命周期里
 * （R57c①：30 秒通道与"物品留在格内继续蒸"都要在关屏后推进），但也不能无人清理——
 * 一个会话背后是 128+12+8 个 {@code ItemStack} 与整份源质表。
 * <p>
 * 因此本表承担三件事，且<b>只</b>承担这三件：
 * <ol>
 * <li>{@link #register} 面板打开时登记；同键已有会话 ⇒ 先把旧会话落盘再替换（防两个写者）；</li>
 * <li>{@link #retireIfIdle} 关屏后由 driver 在无活可干时回收（通道已停 + 蒸馏无输入 ⇒ 落盘并摘除）；</li>
 * <li>{@link #forget} / {@link #clearAll} 玩家离线与停服的收口（先落盘后摘除）——
 * 这就是 R57c④ 要的"真挂钩"，落点在 {@code PocketLifecycleHandler}，不留"承诺了但没人调"的孤儿方法。</li>
 * </ol>
 * 单线程访问（服务器主线程），与 {@link PocketChannelManager} 同口径，不加锁。
 */
public final class PocketSessions {

    private static final Map<UUID, PocketSession> LIVE = new LinkedHashMap<>();

    private PocketSessions() {}

    /** 面板打开（服务端）时登记；替换前先把被顶掉的旧会话落盘。 */
    public static void register(PocketSession session) {
        if (session == null || session.playerId() == null) {
            return;
        }
        final PocketSession previous = LIVE.put(session.playerId(), session);
        if (previous != null && previous != session) {
            previous.persistFinal();
        }
    }

    /** 只读取（tick 宿主每 tick 一次，绝不建条目）。 */
    public static PocketSession peek(UUID player) {
        return player == null ? null : LIVE.get(player);
    }

    /** 摘除某玩家的会话；<b>先落盘</b>再摘（离线即丢内容的形态是事故，不是回收）。 */
    public static void forget(UUID player) {
        if (player == null) {
            return;
        }
        final PocketSession removed = LIVE.remove(player);
        if (removed != null) {
            removed.persistFinal();
        }
    }

    /** 停服/换档：全部落盘后清空，防跨存档残留会话内容。 */
    public static void clearAll() {
        for (PocketSession session : new ArrayList<>(LIVE.values())) {
            session.persistFinal();
        }
        LIVE.clear();
    }

    /**
     * 关屏后的空闲回收：本会话已无活可干（通道停 + 蒸馏格空）⇒ 落盘并摘除。
     * <p>
     * 判定权在调用方（driver 才知道"有没有活"），本方法只负责"既然你说没活了就真的收掉"。
     */
    public static void retire(UUID player, PocketSession session) {
        if (player == null || session == null) {
            return;
        }
        if (LIVE.get(player) != session) {
            // 已被更新的会话替换：不碰，避免把新会话当成旧的摘掉
            return;
        }
        LIVE.remove(player);
        session.persistFinal();
    }

    /**
     * 兜底扫描：把"界面已关、但承载栈已经不在玩家身上"的僵尸会话收掉。
     * <p>
     * 正常情况下 driver 会在每一拍里 retire；本方法由离线/停服挂钩调用，覆盖"玩家整局都没再
     * 拿过口袋"这一条 driver 永远看不到（{@code onUpdate} 不再被调）的路径。
     *
     * @return 本次回收的会话数
     */
    public static int sweepOrphans() {
        int reaped = 0;
        final Iterator<Map.Entry<UUID, PocketSession>> it = LIVE.entrySet()
            .iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, PocketSession> entry = it.next();
            final PocketSession session = entry.getValue();
            if (session == null) {
                it.remove();
                continue;
            }
            if (session.isOpen() || session.isHeldByOwner()) {
                continue;
            }
            session.persistFinal();
            it.remove();
            reaped++;
        }
        return reaped;
    }

    public static int liveSessions() {
        return LIVE.size();
    }
}
