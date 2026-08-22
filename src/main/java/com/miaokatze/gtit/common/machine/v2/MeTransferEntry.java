package com.miaokatze.gtit.common.machine.v2;

import net.minecraft.item.ItemStack;

/**
 * ME 传输队列条目：记录待发送到 ME 网络的物品及其入队时间
 * <p>
 * 当 meOutputMode=true 时，产出物品先进入 {@link NekoMeTransferQueue}，
 * 经过 {@link NekoMeTransferQueue#ME_TRANSFER_DELAY_MS} (3000ms) 延迟后才通过
 * {@code uplinkHatch.injectItems} 注入 ME 网络。
 * 期间玩家可通过 GUI 点击取回（移回 outputBuffer）。
 * <p>
 * A01 蓝图 M1（O2-07 一期）自 MTENekoVendingMachineV2 内部类顶层化，
 * 字段与方法体逐字保留；GUI（gui.vm）为包外唯一消费方。
 */
public class MeTransferEntry {

    /** 待传输的物品栈（已 copy，防止外部修改） */
    public final ItemStack stack;

    /** 入队时间戳（System.currentTimeMillis()），用于 3 秒延迟计算；v1.7.26 起允许延迟重试时更新 */
    public long creationTimeMs;

    /**
     * 物品所在出货槽索引（v1.6.23 新增）
     * <p>
     * -1 表示旧存档兼容（物品未占用槽位）或无空槽时回退。
     * >= 0 时，粒子动画围绕该槽位坐标渲染，3 秒后注入 ME 成功则清空该槽位。
     * </p>
     */
    public final int slotIndex;

    /**
     * 构造 ME 传输队列条目（旧存档兼容，slotIndex 默认 -1）
     *
     * @param stack          待传输的物品栈
     * @param creationTimeMs 入队时间戳（毫秒）
     */
    MeTransferEntry(ItemStack stack, long creationTimeMs) {
        this(stack, creationTimeMs, -1);
    }

    /**
     * 构造 ME 传输队列条目（v1.6.23 新增，带 slotIndex）
     *
     * @param stack          待传输的物品栈
     * @param creationTimeMs 入队时间戳（毫秒）
     * @param slotIndex      物品所在出货槽索引（-1 表示未占用槽位）
     */
    public MeTransferEntry(ItemStack stack, long creationTimeMs, int slotIndex) {
        this.stack = stack;
        this.creationTimeMs = creationTimeMs;
        this.slotIndex = slotIndex;
    }
}
