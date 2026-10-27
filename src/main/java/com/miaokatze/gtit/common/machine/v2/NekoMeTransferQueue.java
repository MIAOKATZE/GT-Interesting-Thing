package com.miaokatze.gtit.common.machine.v2;

import java.util.function.BooleanSupplier;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

/**
 * ME 传输队列 collaborator（A01 蓝图 M1 / O2-07 一期，抽取自 MTENekoVendingMachineV2，
 * 方法体逐字搬移 + 窄接口注入）
 * <p>
 * 持有 ME 模式下「产出落槽 → 3 秒延迟 → 注入 ME 网络」的传输队列全部状态与行为：
 * FIFO tick 消费（槽位校验/remainder 重试/uplink 丢失清队）、GUI 取回、容量判定、
 * 投放交汇点入队、序列化（内置 <b>O2-19 脏标记缓存</b>：GUI 打开期每 tick 的同步轮询
 * 命中缓存直接返回上次结果，仅在入队/取回/tick 变更/NBT 载入后失效）与 NBT 片段读写
 * （键名 {@code meTransferQueue} 与条目内 {@code creationTime}/{@code slotIndex} 冻结）。
 * <p>
 * 窄接口注入（不持机器引用）：{@link UplinkOps} 承接 canAccept/inject/isLost 三触点，
 * {@link OutputSlots} 承接出货槽读写，{@code markDirty} 承接基类脏标记，
 * {@code meOutputMode} 供应商承接交汇点模式判定。B2-02 后全队列仅服务器主线程访问。
 */
public final class NekoMeTransferQueue {

    /** uplink 注入触点（由宿主用既有安全包装实现） */
    public interface UplinkOps {

        /** ME 网络当前能否接收指定物品栈（simulate 探测，含能量/空间校验） */
        boolean canAccept(ItemStack stack);

        /** 实际注入 ME 网络，返回未注入完的剩余 AEItemStack（null/stackSize<=0 表示全部成功） */
        appeng.api.storage.data.IAEItemStack inject(ItemStack stack);

        /** uplink 是否已断开（uplinkHatch == null 判定） */
        boolean isLost();
    }

    /** 出货槽读写触点（由宿主对 outputItems 实现） */
    public interface OutputSlots {

        ItemStack get(int slot);

        void set(int slot, ItemStack stack);

        default long dropId(int slot) {
            return 0;
        }

        default long generation(int slot) {
            return 0;
        }

        /** Legacy detached queue entries are migrated into the paid persistent buffer. */
        default boolean preserveLegacy(ItemStack stack) {
            return false;
        }

        default void visualReset(int slot) {}

        /** True only after the authoritative delivery enters the room; legacy hosts are already visible. */
        default boolean isVisible(int slot) {
            return true;
        }

    }

    /** uplink 注入触点 */
    private final UplinkOps uplink;
    /** 出货槽读写触点 */
    private final OutputSlots out;
    /** 基类脏标记回调 */
    private final Runnable markDirty;
    /** ME 输出模式供应商（投放交汇点判定，值随 NBT/切换延迟求值） */
    private final BooleanSupplier meOutputMode;

    /** ME 传输队列（B2-02 后仅服务器主线程访问） */
    private final java.util.List<MeTransferEntry> meTransferQueue = new java.util.ArrayList<>();

    /** 队列条目上限（常量随类内聚；超限条目按本地槽保留） */
    public static final int MAX_ME_QUEUE_SIZE = 100;
    /** 条目从落槽到注入 ME 的延迟 */
    public static final long ME_TRANSFER_DELAY_MS = 3000L;
    public static final long ME_FADE_MS = 2000L;
    /** Spread visual-group countdowns across rounds instead of fading an entire room together. */
    public static final long ME_STAGGER_MS = 100L;
    /** Includes rejected simulations: a full network must not cause a 100-entry probe burst. */
    public static final int MAX_NETWORK_ATTEMPTS_PER_TICK = 1;

    /** O2-19 序列化缓存（null=脏，需重建；空队列短路不缓存） */
    private String cachedSerialized = null;
    private long lastTickTimeMs = -1;

    public NekoMeTransferQueue(UplinkOps uplink, OutputSlots out, Runnable markDirty, BooleanSupplier meOutputMode) {
        this.uplink = uplink;
        this.out = out;
        this.markDirty = markDirty;
        this.meOutputMode = meOutputMode;
    }

    /**
     * 处理 ME 传输队列（原宿主 processMeTransferQueue，方法体逐字搬移）
     * <p>
     * 每 tick 最多探测/注入一个条目；各条目错开倒计时，并保留完整 3 秒停留 + 2 秒淡出。
     * <p>
     * 若注入未完全成功（返回 remainder），将槽位更新为剩余物品并重置延迟，稍后重试。
     * <p>
     * 如果 uplink 已断开（isLost），取消作业，真实物品留在原出货槽，不重播出货。
     * <p>
     * 应在宿主 onPostTick 服务端块内、dispenseItems() 之后调用。
     */
    public void tick() {
        tick(System.currentTimeMillis());
    }

    /** Deterministic clock overload for headless regressions; production runs on server main thread. */
    public void tick(long now) {
        if (meTransferQueue.isEmpty()) return;
        if (lastTickTimeMs >= 0 && now < lastTickTimeMs) {
            // Wall-clock correction is not a huge scheduled delay: restart the visible groups safely.
            for (MeTransferEntry entry : meTransferQueue) if (entry.creationTimeMs >= 0) entry.creationTimeMs = -1;
            changed();
        }
        lastTickTimeMs = now;
        int networkAttempts = 0;
        java.util.Iterator<MeTransferEntry> it = meTransferQueue.iterator();
        while (it.hasNext()) {
            MeTransferEntry entry = it.next();
            if (entry.slotIndex < 0) {
                if (out.preserveLegacy(entry.stack.copy())) {
                    it.remove();
                    changed();
                }
                continue; // Never inject an unowned legacy copy or discard its remainder.
            }
            ItemStack actual = out.get(entry.slotIndex);
            if (actual == null || !NekoOutputLedger.same(actual, entry.stack)
                || out.dropId(entry.slotIndex) != entry.dropId
                || out.generation(entry.slotIndex) != entry.generation) {
                out.visualReset(entry.slotIndex);
                it.remove();
                changed();
                continue;
            }
            if (uplink.isLost()) {
                out.visualReset(entry.slotIndex);
                it.remove();
                changed();
                continue;
            }
            if (entry.creationTimeMs < 0) {
                if (out.isVisible(entry.slotIndex)) {
                    schedule(entry, now);
                    changed();
                }
                continue;
            }
            if (actual.stackSize != entry.stack.stackSize) {
                entry.stack.stackSize = actual.stackSize;
                restartGroup(entry, now);
                out.visualReset(entry.slotIndex);
                changed();
                continue;
            }
            // Actual delivery precedes this clock. Visible wait 3 seconds, then 2 second fade.
            if (now < entry.creationTimeMs || now - entry.creationTimeMs < ME_TRANSFER_DELAY_MS + ME_FADE_MS) continue;
            if (networkAttempts >= MAX_NETWORK_ATTEMPTS_PER_TICK) continue;
            networkAttempts++;
            if (!uplink.canAccept(actual.copy())) {
                restartGroup(entry, now);
                out.visualReset(entry.slotIndex);
                changed();
                continue;
            }
            int before = actual.stackSize;
            appeng.api.storage.data.IAEItemStack remainder = uplink.inject(actual.copy());
            long remaining = remainder == null ? 0 : remainder.getStackSize();
            // Only an actual accepted quantity is removed, never a simulation result.
            int accepted = (int) Math.max(0, Math.min((long) before, (long) before - Math.max(0, remaining)));
            if (accepted > 0) {
                ItemStack retained = actual.copy();
                retained.stackSize = before - accepted;
                out.set(entry.slotIndex, retained.stackSize == 0 ? null : retained);
            }
            if (accepted == before) it.remove();
            else {
                entry.stack.stackSize = before - accepted;
                restartGroup(entry, now);
                out.visualReset(entry.slotIndex);
            }
            changed();
        }
    }

    /** The ledger gives each visual body one stable dropId; slot generations remain individual guards. */
    private static boolean sameGroup(MeTransferEntry a, MeTransferEntry b) {
        return a.dropId > 0 && a.dropId == b.dropId;
    }

    /** Countdown starts are reserved on the owner thread; no worker ever touches Minecraft or AE. */
    private void schedule(MeTransferEntry entry, long now) {
        for (MeTransferEntry other : meTransferQueue)
            if (other != entry && sameGroup(entry, other) && other.creationTimeMs >= 0) {
                // A genuinely new visible member needs its full hold and fade, not an older body's age.
                if (other.creationTimeMs < now) restartGroup(entry, now);
                else entry.creationTimeMs = other.creationTimeMs;
                return;
            }
        entry.creationTimeMs = nextGroupStart(entry, now);
    }

    private long nextGroupStart(MeTransferEntry entry, long now) {
        long start = now;
        for (MeTransferEntry other : meTransferQueue)
            if (other != entry && !sameGroup(entry, other) && other.creationTimeMs >= 0) start = Math.max(
                start,
                other.creationTimeMs > Long.MAX_VALUE - ME_STAGGER_MS ? Long.MAX_VALUE
                    : other.creationTimeMs + ME_STAGGER_MS);
        return start;
    }

    /** A rejected/partial group returns visibly together, rather than leaving one shared body hidden. */
    private void restartGroup(MeTransferEntry entry, long now) {
        long start = nextGroupStart(entry, now);
        entry.creationTimeMs = start;
        for (MeTransferEntry other : meTransferQueue)
            if (other != entry && sameGroup(entry, other) && other.creationTimeMs >= 0) {
                other.creationTimeMs = start;
                out.visualReset(other.slotIndex);
            }
    }

    private void changed() {
        cachedSerialized = null;
        markDirty.run();
    }

    /** Called at the first server-visible feed pose, not at inventory placement or physics release. */
    public void visible(long dropId, long now) {
        for (MeTransferEntry entry : meTransferQueue) if (entry.dropId == dropId && entry.creationTimeMs < 0) {
            schedule(entry, now);
            changed();
        }
    }

    public long phaseAgeMs(int slot, long now) {
        for (MeTransferEntry e : meTransferQueue) if (e.slotIndex == slot) {
            if (e.creationTimeMs < 0) return 0;
            long elapsed = Math.max(0, now - e.creationTimeMs);
            return elapsed < ME_TRANSFER_DELAY_MS ? elapsed : Math.min(ME_FADE_MS, elapsed - ME_TRANSFER_DELAY_MS);
        }
        return 0;
    }

    public int phase(int slot, long now) {
        for (MeTransferEntry e : meTransferQueue)
            if (e.slotIndex == slot) return e.creationTimeMs < 0 || now < e.creationTimeMs ? 0
                : now - e.creationTimeMs >= ME_TRANSFER_DELAY_MS ? 2 : 1;
        return 0;
    }

    /**
     * 取回队列首部物品（原宿主 retrieveEarliestMeTransferItem，方法体逐字搬移）
     * <p>
     * 供 GUI 取回按钮调用（阶段 4）。将队列首部的物品移到 outputBuffer，
     * 由宿主 dispenseItems() 逐 tick 投放到出货槽。
     * <p>
     * FIFO 语义：取回的是最早入队的物品（玩家可能最想立即拿到的）。
     *
     * @return true 表示取回成功（队列非空且移除成功）
     */
    public boolean retrieveEarliest() {
        if (meTransferQueue.isEmpty()) return false;
        // v1.6.23: 物品已在出货槽中，仅从队列移除（阻止 3 秒后注入 ME）
        // 不再重新加入 outputBuffer（避免触发二次掉落动画到其他空槽）
        MeTransferEntry entry = meTransferQueue.get(0);
        if (entry.slotIndex < 0 && !out.preserveLegacy(entry.stack.copy())) return false;
        meTransferQueue.remove(0);
        out.visualReset(entry.slotIndex);
        cachedSerialized = null;
        markDirty.run();
        return true;
    }

    /** Move old detached inventory into the host's paid buffer before any tick or controller removal. */
    public void preserveDetached() {
        java.util.Iterator<MeTransferEntry> iterator = meTransferQueue.iterator();
        boolean moved = false;
        while (iterator.hasNext()) {
            MeTransferEntry entry = iterator.next();
            if (entry.slotIndex < 0 && out.preserveLegacy(entry.stack.copy())) {
                iterator.remove();
                moved = true;
            }
        }
        if (moved) changed();
    }

    public void cancelAll() {
        java.util.Iterator<MeTransferEntry> iterator = meTransferQueue.iterator();
        while (iterator.hasNext()) {
            MeTransferEntry entry = iterator.next();
            if (entry.slotIndex < 0 && !out.preserveLegacy(entry.stack.copy())) continue;
            out.visualReset(entry.slotIndex);
            iterator.remove();
        }
        changed();
    }

    /** 队列大小（GUI 心跳同步消费） */
    public int size() {
        return meTransferQueue.size();
    }

    /** 队列是否为空 */
    public boolean isEmpty() {
        return meTransferQueue.isEmpty();
    }

    /** 队列是否已满（MAX_ME_QUEUE_SIZE 判定收编，原散在宿主三处） */
    public boolean isFull() {
        return meTransferQueue.size() >= MAX_ME_QUEUE_SIZE;
    }

    /**
     * 投放交汇点入队（原宿主 outputIntoSlot 尾段，语义逐字保留）
     * <p>
     * ME 模式且队列未满时创建条目；超限条目物品已在出货槽（调用方先落槽），
     * 等价"按本地槽保留"（B2-09 口径）。
     *
     * @param stack     已落槽的产出物品栈（入队时 copy）
     * @param slotIndex 物品所在出货槽索引
     */
    public void enqueueIfMeMode(ItemStack stack, int slotIndex) {
        enqueueIfMeMode(stack, slotIndex, System.currentTimeMillis());
    }

    /** Idempotent admission, shared by new deliveries and explicit activation over existing output. */
    public void enqueueIfMeMode(ItemStack stack, int slotIndex, long now) {
        if (!meOutputMode.getAsBoolean() || isFull()
            || stack == null
            || stack.stackSize <= 0
            || slotIndex < 0
            || slotIndex >= MAX_ME_QUEUE_SIZE) return;
        long dropId = out.dropId(slotIndex), generation = out.generation(slotIndex);
        for (MeTransferEntry entry : meTransferQueue)
            if (entry.slotIndex == slotIndex && entry.dropId == dropId && entry.generation == generation) return;
        MeTransferEntry entry = new MeTransferEntry(stack, -1, slotIndex, dropId, generation);
        if (out.isVisible(slotIndex)) schedule(entry, now);
        meTransferQueue.add(entry);
        changed();
    }

    /** Called only on an explicit OFF -> ON transition; cancelled jobs stay cancelled. */
    public void enqueueExisting(int slotCount, long now) {
        if (!meOutputMode.getAsBoolean()) return;
        for (int slot = 0; slot < Math.min(slotCount, MAX_ME_QUEUE_SIZE); slot++)
            enqueueIfMeMode(out.get(slot), slot, now);
    }

    /**
     * 序列化队列为字符串（供 GUI 同步值传输到客户端，方法体逐字搬移 + O2-19 缓存）
     * <p>
     * v1.6.23 格式：{@code creationTimeMs:stackSize:slotIndex:itemNBTBase64;...}
     * <p>
     * 旧格式（v1.6.22 及之前）：{@code creationTimeMs:stackSize:itemNBTBase64;...}
     * 客户端 parseMeTransferQueue 兼容两种格式（根据 split 段数判断）。
     * <p>
     * 客户端解析后用于渲染粒子动画（显示剩余传输时间和物品图标）。
     * 空队列返回空字符串。NBT 编解码复用 {@link com.miaokatze.gtit.util.NbtBase64Util}。
     * <p>
     * O2-19：GUI 打开期 MUI2 每轮同步轮询都会调用本方法，命中缓存直接返回上次结果，
     * 仅在队列变更（入队/取回/tick 变更/NBT 载入）后重建。
     *
     * @return 序列化字符串，空队列返回空字符串
     */
    public String serialize() {
        if (meTransferQueue.isEmpty()) return "";
        if (cachedSerialized != null) return cachedSerialized;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (MeTransferEntry entry : meTransferQueue) {
            if (!first) sb.append(";");
            first = false;
            // 序列化 ItemStack 到 NBT，再转 base64
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            entry.stack.writeToNBT(tag);
            String base64 = com.miaokatze.gtit.util.NbtBase64Util.nbtToBase64(tag);
            sb.append(entry.creationTimeMs)
                .append(":")
                .append(entry.stack.stackSize)
                .append(":")
                .append(entry.slotIndex) // v1.6.23 新增
                .append(":")
                .append(base64 == null ? "" : base64);
        }
        cachedSerialized = sb.toString();
        return cachedSerialized;
    }

    /**
     * 序列化队列到 NBT 片段（原宿主 saveNBTData 内联段逐字搬移；键名 meTransferQueue 冻结）
     *
     * @return 条目 NBT 列表（creationTime/slotIndex/物品 tag）
     */
    public NBTTagList writeToNBT() {
        return writeToNBT(System.currentTimeMillis());
    }

    public NBTTagList writeToNBT(long now) {
        net.minecraft.nbt.NBTTagList meQueueList = new net.minecraft.nbt.NBTTagList();
        for (MeTransferEntry entry : meTransferQueue) {
            net.minecraft.nbt.NBTTagCompound entryTag = new net.minecraft.nbt.NBTTagCompound();
            entryTag.setLong("creationTime", entry.creationTimeMs);
            entryTag.setLong("scheduledRemainingMs", Math.max(0, entry.creationTimeMs - now));
            entryTag.setLong(
                "visibleElapsedMs",
                entry.creationTimeMs < 0 ? -1
                    : Math.min(ME_TRANSFER_DELAY_MS + ME_FADE_MS, Math.max(0, now - entry.creationTimeMs)));
            entryTag.setLong("dropId", entry.dropId);
            entryTag.setLong("generation", entry.generation);
            entryTag.setInteger("slotIndex", entry.slotIndex); // v1.6.23 新增
            entry.stack.writeToNBT(entryTag);
            meQueueList.appendTag(entryTag);
        }
        return meQueueList;
    }

    /**
     * 从 NBT 片段载入队列（原宿主 loadNBTData 内联段逐字搬移；旧档 slotIndex 默认 -1 兼容保留）
     *
     * @param meQueueList 条目 NBT 列表
     */
    public void readFromNBT(NBTTagList meQueueList) {
        readFromNBT(meQueueList, System.currentTimeMillis());
    }

    public void readFromNBT(NBTTagList meQueueList, long now) {
        meTransferQueue.clear();
        lastTickTimeMs = now;
        for (int i = 0; i < meQueueList.tagCount(); i++) {
            net.minecraft.nbt.NBTTagCompound entryTag = meQueueList.getCompoundTagAt(i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(entryTag);
            long creationTime = entryTag.getLong("creationTime");
            if (entryTag.hasKey("visibleElapsedMs")) {
                long elapsed = entryTag.getLong("visibleElapsedMs");
                creationTime = elapsed < 0 ? -1 : now - Math.min(ME_TRANSFER_DELAY_MS + ME_FADE_MS, elapsed);
                if (elapsed >= 0 && entryTag.getLong("scheduledRemainingMs") > 0)
                    creationTime = now + Math.min(Long.MAX_VALUE - now, entryTag.getLong("scheduledRemainingMs"));
            } else if (creationTime > now) {
                // Old timestamps predate scheduled starts; a backwards clock must not strand owned output.
                creationTime = now;
            }
            // v1.6.23: 读取 slotIndex，旧存档无此 key 时默认 -1
            int slotIndex = entryTag.hasKey("slotIndex") ? entryTag.getInteger("slotIndex") : -1;
            if (stack != null && stack.stackSize > 0) {
                if (slotIndex >= MAX_ME_QUEUE_SIZE) continue;
                long dropId = entryTag.hasKey("dropId") ? entryTag.getLong("dropId") : out.dropId(slotIndex);
                long generation = entryTag.hasKey("generation") ? entryTag.getLong("generation")
                    : out.generation(slotIndex);
                meTransferQueue.add(new MeTransferEntry(stack, creationTime, slotIndex, dropId, generation));
            }
        }
        cachedSerialized = null;
        preserveDetached();
    }
}
