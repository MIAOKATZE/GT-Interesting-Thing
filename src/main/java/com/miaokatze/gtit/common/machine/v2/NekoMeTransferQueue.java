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
    public static final int MAX_ME_QUEUE_SIZE = 18;
    /** 条目从落槽到注入 ME 的延迟 */
    public static final long ME_TRANSFER_DELAY_MS = 3000L;

    /** O2-19 序列化缓存（null=脏，需重建；空队列短路不缓存） */
    private String cachedSerialized = null;

    public NekoMeTransferQueue(UplinkOps uplink, OutputSlots out, Runnable markDirty, BooleanSupplier meOutputMode) {
        this.uplink = uplink;
        this.out = out;
        this.markDirty = markDirty;
        this.meOutputMode = meOutputMode;
    }

    /**
     * 处理 ME 传输队列（原宿主 processMeTransferQueue，方法体逐字搬移）
     * <p>
     * 遍历队列，将超过 {@link #ME_TRANSFER_DELAY_MS}（3 秒）的条目通过
     * {@code appeng.util.Platform.poweredInsert} 直接注入 ME 网络。
     * <p>
     * 若注入未完全成功（返回 remainder），将槽位更新为剩余物品并重置延迟，稍后重试。
     * <p>
     * 如果 uplink 已断开（isLost），将队列中所有物品回退到 outputBuffer，
     * 由本地出货槽路径投放，防止物品丢失。
     * <p>
     * 应在宿主 onPostTick 服务端块内、dispenseItems() 之后调用。
     */
    public void tick() {
        if (meTransferQueue.isEmpty()) return;
        long now = System.currentTimeMillis();

        // v1.6.23: uplink 丢失时，物品已在出货槽中，仅清空队列（玩家可自行取走）
        if (uplink.isLost()) {
            meTransferQueue.clear();
            cachedSerialized = null;
            markDirty.run();
            return;
        }

        // 处理已到期的条目（FIFO：队列头部先入队，先到期）
        java.util.Iterator<MeTransferEntry> it = meTransferQueue.iterator();
        while (it.hasNext()) {
            MeTransferEntry entry = it.next();
            if (now - entry.creationTimeMs >= ME_TRANSFER_DELAY_MS) {
                if (entry.slotIndex >= 0) {
                    // v1.6.23: 检查槽位中是否还有匹配的物品（玩家可能已取走）
                    ItemStack slotStack = out.get(entry.slotIndex);
                    if (slotStack != null && slotStack.isItemEqual(entry.stack)
                        && ItemStack.areItemStackTagsEqual(slotStack, entry.stack)) {
                        // 物品仍在槽中，先检查 ME 网络是否能接收
                        if (uplink.canAccept(slotStack)) {
                            // v1.7.33: 直接注入槽内实际物品，根据 remainder 决定是否清空或保留剩余
                            appeng.api.storage.data.IAEItemStack remainder = uplink.inject(slotStack);
                            if (remainder == null || remainder.getStackSize() <= 0) {
                                // 全部注入成功，清空对应出货槽
                                out.set(entry.slotIndex, null);
                            } else {
                                // 注入未完全成功，保留剩余并重试
                                out.set(entry.slotIndex, remainder.getItemStack());
                                entry.creationTimeMs = now;
                                cachedSerialized = null;
                                break;
                            }
                        } else {
                            // ME 网络当前不能接收（无能量/无空间），保留在出货槽并延迟重试
                            entry.creationTimeMs = now;
                            cachedSerialized = null;
                            break;
                        }
                    }
                    // 槽位为空或物品不匹配：玩家已取走，跳过注入
                } else {
                    // slotIndex == -1（旧存档兼容或无空槽回退）：直接注入，无法保留剩余
                    uplink.inject(entry.stack);
                }
                it.remove();
                cachedSerialized = null;
                markDirty.run();
            } else {
                // 队列是 FIFO，遇到未到期的就停止（后续条目入队更晚，必然也未到期）
                break;
            }
        }
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
        meTransferQueue.remove(0);
        cachedSerialized = null;
        markDirty.run();
        return true;
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
        // B2-09：入队点统一守门 MAX_ME_QUEUE_SIZE——交易路径由 hasSpaceFor/getAvailableSlotCount
        // 预检，抽奖出货路径此前无检查（10 连可入队 10+ 条）
        if (meOutputMode.getAsBoolean() && meTransferQueue.size() < MAX_ME_QUEUE_SIZE) {
            meTransferQueue.add(new MeTransferEntry(stack.copy(), System.currentTimeMillis(), slotIndex));
            cachedSerialized = null;
        }
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
        net.minecraft.nbt.NBTTagList meQueueList = new net.minecraft.nbt.NBTTagList();
        for (MeTransferEntry entry : meTransferQueue) {
            net.minecraft.nbt.NBTTagCompound entryTag = new net.minecraft.nbt.NBTTagCompound();
            entryTag.setLong("creationTime", entry.creationTimeMs);
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
        for (int i = 0; i < meQueueList.tagCount(); i++) {
            net.minecraft.nbt.NBTTagCompound entryTag = meQueueList.getCompoundTagAt(i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(entryTag);
            long creationTime = entryTag.getLong("creationTime");
            // v1.6.23: 读取 slotIndex，旧存档无此 key 时默认 -1
            int slotIndex = entryTag.hasKey("slotIndex") ? entryTag.getInteger("slotIndex") : -1;
            if (stack != null && stack.stackSize > 0) {
                meTransferQueue.add(new MeTransferEntry(stack, creationTime, slotIndex));
            }
        }
        cachedSerialized = null;
    }
}
