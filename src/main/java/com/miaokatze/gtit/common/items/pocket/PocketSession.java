package com.miaokatze.gtit.common.items.pocket;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

/**
 * 一枚口袋的<b>服务端活会话</b>（S6/S7 的宿主与 GUI 之间的唯一接口）。
 * <p>
 * <b>为什么需要这一层</b>（R57c + R53c 的联立约束）：通道与蒸馏的宿主是 {@code Item.onUpdate}
 * （每 tick 一次），而口袋的全部内容（128 格物品、12 格蒸馏输入、流体条、源质表、绑定表、ghost 配置）
 * 平时只活在面板会话的内存对象里，<b>关屏才写 NBT</b>（R22/R53c 的"一次读一次写"）。
 * 于是两头都不通：tick 侧要读就得每 tick 现解 NBT（明令禁止），不读就只能读会话。
 * 本接口就是"tick 侧允许读的那一个东西"，实现者是面板（{@code gui/pocket/NekoPocketPanel}），
 * 因此<b>内存里始终只有一份真相</b>。
 * <p>
 * <b>会话在界面关闭后继续存活</b>是刻意的（R57c①：30 秒通道要求关掉界面仍继续；需求 2 的蒸馏
 * 同理——玩家不会为了看进度条一直挂着界面）。此时写权从面板转交给 driver：
 * {@link #persistIdle()} 由 driver 在每批结束后调用，把内存内容序列化回承载栈；
 * {@link #isOpen()} 为真时该方法什么都不做（写权仍在面板，避免半程落盘，R35）。
 * <p>
 * 持有 {@code EntityPlayer}/{@code ItemStack} 是服务端专用；本接口不得被客户端代码调用。
 */
public interface PocketSession {

    /** 玩家维键（与 {@link PocketChannelManager} 的条目键同一）。 */
    UUID playerId();

    /** 服务端玩家本体（每次现取，跨维重建后自动是新的实例）。 */
    EntityPlayer player();

    /**
     * 当前承载本会话的口袋栈。面板关屏后会按 {@code open} 位重定位；重定位失败返回 {@code null}
     * （driver 据此跳过本拍，绝不写到别的栈上——R35 防御③同一口径）。
     */
    ItemStack carrierStack();

    /** 承载栈的 NBT 根（设备维冷却与关屏落盘的落点）；可能为 {@code null}（无档）。 */
    NBTTagCompound carrierTag();

    /** 界面是否仍打开（打开时写权属面板，{@link #persistIdle()} 不动作）。 */
    boolean isOpen();

    /**
     * 承载本会话的口袋是否还在玩家主背包 36 格里（{@code Item.onUpdate} 的覆盖范围，R57c⑥）。
     * <p>
     * 口袋被塞进箱子/掉了 ⇒ driver 永远等不到下一拍 ⇒ 空闲回收只能靠离线/停服挂钩兜底扫这一条。
     */
    boolean isHeldByOwner();

    /** 内容变过（driver 侧写入了东西）→ 打脏标记，由下一次 {@link #persistIdle()} 或关屏落盘。 */
    void markDirty();

    /** 界面已关闭时把内存内容序列化回承载栈；界面打开时不做任何事。 */
    void persistIdle();

    /** 解绑/丢弃会话时的最后一道落盘（含界面仍打开的情形）。 */
    void persistFinal();

    /** 绑定表（激活时快照进 {@link PocketChannelState} 的就是这个实例，面板改的也是它）。 */
    PocketCellBindings bindings();

    /** ghost 配置（拉取模式的声明源，同时是"哪些格不能作为落点"的判据）。 */
    PocketFilterConfig filters();

    /** 源质表（S7 蒸馏入账与"要素栏取出"的同一份存储，R15 的另一条路径不共用缓冲）。 */
    PocketEssenceStore essence();

    /** 蒸馏输入 12 格的当前内容（行主序；元素可能为 {@code null}）。 */
    ItemStack distillInputStack(int index);

    /** 蒸馏输入格数（= {@code PocketInventory.DISTILL_INPUT_SLOTS}，不是字面量 12）。 */
    int distillInputSlots();

    /**
     * 消耗某格蒸馏输入的<b>一个</b>物品（R28 的一轮 = 每格各消耗 1 个）。
     * 由 S7 在"全有全无预检通过"之后调用，本方法不做任何判定。
     */
    void consumeOneDistillInput(int index);

    /**
     * 把一件物品落到口袋真实栏（拉取模式与"要素栏取出→晶化源质"的唯一物品落点）。
     * <p>
     * ghost 格<b>不是</b>落点（R38 第 2 条"产物不能进自己"），空槽与可合并槽才算。
     * 顺序 = 口袋中栏 → 玩家背包兜底；<b>不</b>掉到脚下（拉取是自动行为，往地上喷物品会被踩丢）。
     *
     * @return 实际落下的个数（0 表示无处可放，调用方据此发 {@code TARGET_FULL} 并把源侧原样退回）
     */
    int depositItem(ItemStack stack);

    /**
     * 流体条还能收这一份流体多少 mB。
     * <p>
     * ★必须在抽取<b>之前</b>问：先抽后放会把超出部分的流体凭空抹掉（AE2 侧已经扣了）。
     * 条里已有别的流体 ⇒ 0；{@code probe} 为 null ⇒ 0。
     */
    int fluidBarRoom(FluidStack probe);

    /** 往流体条灌入（{@code FluidStackTank.fill} 自身会拒收别的流体）；返回实际接收 mB。 */
    int depositFluid(FluidStack fluid);
}
