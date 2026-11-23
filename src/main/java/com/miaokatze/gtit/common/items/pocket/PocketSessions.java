// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.pocket;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.util.AtomicNbtFile;

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
    private static final Map<UUID, Long> DEFERRED = new LinkedHashMap<>();

    private PocketSessions() {}

    /** 面板打开（服务端）时登记；替换前先把被顶掉的旧会话落盘。 */
    public static void register(PocketSession session) {
        if (session == null || session.playerId() == null) {
            return;
        }
        final PocketSession previous = LIVE.get(session.playerId());
        if (previous != null && previous != session) {
            previous.persistFinal();
            requireFlushed(previous);
        }
        LIVE.put(session.playerId(), session);
        DEFERRED.remove(session.playerId());
    }

    /** Call before capturing a new carrier token, so an unresolved old writer cannot be silently revoked. */
    public static void flushBeforeReplacement(UUID player) {
        PocketSession previous = peek(player);
        if (previous == null) return;
        previous.persistFinal();
        requireFlushed(previous);
    }

    /** A reopened panel can use a verified reconnect carrier to finish the old write first. */
    public static void flushBeforeOpening(EntityPlayerMP player) {
        retryAfterLogin(player);
        flushBeforeReplacement(
            player.getGameProfile() == null ? null
                : player.getGameProfile()
                    .getId());
    }

    private static void requireFlushed(PocketSession session) {
        if (hasUnsavedState(session))
            throw new IllegalStateException("Previous pocket session has unpersisted state; replacement denied");
    }

    private static boolean hasUnsavedState(PocketSession session) {
        return session instanceof PocketRuntimeSession runtime && runtime.inventory()
            .isDirty();
    }

    private static void reportDeferred(UUID player, RuntimeException exception) {
        if (DEFERRED.put(player, System.nanoTime() + TimeUnit.SECONDS.toNanos(1)) != null) return;
        final String message = "[pocket] Player " + player + " has an unsaved pocket session; retained for retry";
        if (exception == null) GTInterestingThing.LOG.warn(message);
        else GTInterestingThing.LOG.error(message, exception);
    }

    private static boolean tryFlush(UUID player, PocketSession session) {
        try {
            session.persistFinal();
            if (!hasUnsavedState(session)) {
                DEFERRED.remove(player);
                return true;
            }
            reportDeferred(player, null);
        } catch (RuntimeException failure) {
            reportDeferred(player, failure);
        }
        return false;
    }

    static void onLogout(UUID uuid, EntityPlayerMP player) {
        final PocketSession session = LIVE.get(uuid);
        if (session != null && session.player() == player && flushForDeparture(uuid, session)) {
            LIVE.remove(uuid, session);
        }
    }

    private static boolean flushForDeparture(UUID uuid, PocketSession session) {
        if (tryFlush(uuid, session)) return true;
        if (session instanceof PocketRuntimeSession runtime && runtime.player() instanceof EntityPlayerMP player) {
            try {
                runtime.suspendAfterLogout(player);
            } catch (RuntimeException failure) {
                GTInterestingThing.LOG.error("[pocket] Could not capture the reconnect baseline for " + uuid, failure);
            }
        }
        return false;
    }

    static void retryAfterLogin(EntityPlayerMP player) {
        if (player.getGameProfile() == null) return;
        final UUID uuid = player.getGameProfile()
            .getId();
        if (!(LIVE.get(uuid) instanceof PocketRuntimeSession runtime)) return;
        try {
            if (!runtime.isDisconnected()) {
                if (!(runtime.player() instanceof EntityPlayerMP previousPlayer) || previousPlayer == player) return;
                // An interrupted disconnect can leave an old owner even if its logout callback never ran.
                if (hasUnsavedState(runtime)) runtime.suspendAfterLogout(previousPlayer);
            }
            if (hasUnsavedState(runtime)) runtime.retryAfterLogin(player);
            if (!hasUnsavedState(runtime)) {
                LIVE.remove(uuid, runtime);
                DEFERRED.remove(uuid);
            } else {
                reportDeferred(uuid, null);
            }
        } catch (RuntimeException failure) {
            reportDeferred(uuid, failure);
        }
    }

    /** 只读取（tick 宿主每 tick 一次，绝不建条目）。 */
    public static PocketSession peek(UUID player) {
        return player == null ? null : LIVE.get(player);
    }

    /** A failed authority lookup cannot authorize a second writer for the same carrier token. */
    public static boolean mayWriteIndependently(UUID player, ItemStack carrier) {
        PocketSession session = peek(player);
        return !(session instanceof PocketRuntimeSession runtime) || !runtime.ownsCarrier(carrier);
    }

    public static boolean mayTickCarrier(UUID player, ItemStack carrier) {
        PocketSession session = peek(player);
        return !(session instanceof PocketRuntimeSession runtime) || !runtime.isDisconnected()
            || !runtime.ownsCarrier(carrier);
    }

    /** Called before vanilla serializes inventory, and before a carrier leaves its current host. */
    public static boolean flushPlayer(EntityPlayerMP player) {
        if (player == null || player.getGameProfile() == null) return true;
        UUID uuid = player.getGameProfile()
            .getId();
        PocketSession session = peek(uuid);
        return session == null || (session.player() == player && tryFlush(uuid, session));
    }

    public static boolean prepareCarrierMove(EntityPlayer player, ItemStack stack) {
        if (!(player instanceof EntityPlayerMP serverPlayer) || serverPlayer.getGameProfile() == null) return true;
        PocketSession session = peek(
            serverPlayer.getGameProfile()
                .getId());
        return !(session instanceof PocketRuntimeSession runtime) || !runtime.ownsCarrier(stack)
            || flushPlayer(serverPlayer);
    }

    public static boolean prepareContainerMove(EntityPlayer player, ItemStack clicked, int button, int mode) {
        if (player == null || player.inventory == null) return true;
        return prepareCarrierMove(player, clicked) && prepareCarrierMove(player, player.inventory.getItemStack())
            && (mode != 2 || button < 0
                || button >= 9
                || prepareCarrierMove(player, player.inventory.mainInventory[button]));
    }

    public static void beforePlayerSave(EntityPlayerMP player) {
        if (!flushPlayer(player) && player.getGameProfile() != null) retainRecovery(
            player.getGameProfile()
                .getId(),
            peek(
                player.getGameProfile()
                    .getId()));
    }

    private static boolean retainRecovery(UUID uuid, PocketSession session) {
        if (!(session instanceof PocketRuntimeSession runtime)) return false;
        MinecraftServer server = runtime.recoveryServer();
        if (server == null || server.worldServerForDimension(0) == null) return false;
        try {
            Path directory = server.worldServerForDimension(0)
                .getSaveHandler()
                .getWorldDirectory()
                .toPath()
                .resolve("data/gtit-pocket-recovery");
            Files.createDirectories(directory);
            NBTTagCompound record = runtime.recoverySnapshot();
            record.setString("player", uuid.toString());
            String token = record.getCompoundTag("carrierBaseline")
                .getCompoundTag("tag")
                .getString("gtitPocketSession");
            UUID.fromString(token);
            File target = directory.resolve(uuid + "-" + token + ".dat")
                .toFile();
            AtomicNbtFile.writeCompressedAndSync(record, target, path -> {
                try (InputStream input = Files.newInputStream(path)) {
                    CompressedStreamTools.readCompressed(input);
                }
            });
            GTInterestingThing.LOG.error("[pocket] Uncommitted inventory retained for operator recovery at {}", target);
            return true;
        } catch (IOException | RuntimeException failure) {
            GTInterestingThing.LOG.error("[pocket] Could not retain pocket recovery for " + uuid, failure);
            return false;
        }
    }

    static void flushTick() {
        for (Map.Entry<UUID, PocketSession> entry : new ArrayList<>(LIVE.entrySet())) {
            Long retryAt = DEFERRED.get(entry.getKey());
            if (retryAt == null || System.nanoTime() - retryAt >= 0) tryFlush(entry.getKey(), entry.getValue());
        }
    }

    /** 摘除某玩家的会话；<b>先落盘</b>再摘（离线即丢内容的形态是事故，不是回收）。 */
    public static void forget(UUID player) {
        if (player == null) {
            return;
        }
        final PocketSession removed = LIVE.get(player);
        if (removed != null) {
            removed.persistFinal();
            requireFlushed(removed);
            LIVE.remove(player);
            DEFERRED.remove(player);
        }
    }

    /** 停服/换档：全部落盘后清空，防跨存档残留会话内容。 */
    public static void clearAll() {
        for (PocketSession session : new ArrayList<>(LIVE.values())) {
            session.persistFinal();
            requireFlushed(session);
        }
        LIVE.clear();
        DEFERRED.clear();
    }

    static void flushBeforeStop() {
        // A pending player must not prevent other sessions from being saved.
        LIVE.entrySet()
            .removeIf(
                entry -> flushForDeparture(entry.getKey(), entry.getValue())
                    || retainRecovery(entry.getKey(), entry.getValue()));
        DEFERRED.keySet()
            .retainAll(LIVE.keySet());
        if (!LIVE.isEmpty()) {
            GTInterestingThing.LOG.error("[pocket] Unresolved pocket sessions at stop: {}", LIVE.keySet());
        }
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
        final Long retryAt = DEFERRED.get(player);
        if (retryAt != null && System.nanoTime() - retryAt < 0) return;
        if (tryFlush(player, session)) LIVE.remove(player, session);
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
            try {
                if (session.isOpen() || session.isHeldByOwner()) continue;
            } catch (RuntimeException failure) {
                reportDeferred(entry.getKey(), failure);
                continue;
            }
            if (!tryFlush(entry.getKey(), session)) continue;
            it.remove();
            reaped++;
        }
        return reaped;
    }

    public static int liveSessions() {
        return LIVE.size();
    }
}
