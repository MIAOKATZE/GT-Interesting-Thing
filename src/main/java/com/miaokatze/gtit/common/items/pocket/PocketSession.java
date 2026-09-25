package com.miaokatze.gtit.common.items.pocket;

import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

/**
 * 一枚口袋的<b>服务端活会话</b>（S6/S7 的宿主与 GUI 之间的唯一接口）。
 * <p>
 * <b>为什么需要这一层</b>（R57c + R53c 的联立约束）：通道与蒸馏的宿主是 {@code Item.onUpdate}
 * （每 tick 一次），而口袋的全部内容（135 格物品、12 格蒸馏输入、六个流体槽、源质表、绑定表、ghost 配置）
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
     * 消耗某格蒸馏输入的<b>一个</b>物品（★R84 后：一轮 = 每个非空且含源质的格各消耗 1 件，
     * 同物多格<b>并行各扣 1 件</b>；旧 R83 β 的"同物多格折叠成一组、一组只扣一次"口径作废）。
     * 由 S7 在"该格的装箱预检通过"之后调用，本方法不做任何判定。
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
     * 中栏真实栏的格数（★R84：通道<b>注入侧的来源空间</b>由"玩家背包 36 格"改为此处，作废 R12 读法 B）。
     */
    int storageSlots();

    /** 中栏第 {@code slot} 格的当前内容（可能为 {@code null}；越界亦为 {@code null}）。 */
    ItemStack storageStackAt(int slot);

    /**
     * 程序化写回中栏第 {@code slot} 格（{@code null} 即清空）。
     * <p>
     * 走 {@code ItemStackHandler.setStackInSlot} 那条<b>不经过</b> {@code isItemValid} 的写入面，所以
     * ghost 声明格（玩家禁放置）照样能当抽取落点被写进去（R84）。越界一律丢弃且不打脏。
     */
    void setStorageStackAt(int slot, ItemStack stack);

    /**
     * 第 {@code slot} 格是否已被某条 ghost 声明占用（★"配置好需求的格不参与上传"的判据，R84 用户裁定）。
     */
    boolean isStorageGhostDeclared(int slot);

    /**
     * 第 {@code tank} 号流体槽还能收这一份流体多少 mB。
     * <p>
     * ★必须在抽取<b>之前</b>问：先抽后放会把超出部分的流体凭空抹掉（AE2 侧已经扣了）。
     * 槽里已有别的流体 ⇒ 0；{@code probe} 为 null ⇒ 0；{@code tank} 越界 ⇒ 0。
     * <p>
     * tank 号就是该条 ghost 声明的 {@code slotIndex}（R75①，★R78 起列数为 {@code 18}：
     * 十八个流体列 = 十八个 tank = {@code Kind.FLUID} 的索引空间），所以"哪一列要拉什么"与"拉到哪儿"
     * 共用同一个数，不存在第二份映射。
     */
    int fluidBarRoom(int tank, FluidStack probe);

    /** 往第 {@code tank} 号流体槽灌入（{@code FluidStackTank.fill} 自身会拒收别的流体）；返回实际接收 mB。 */
    int depositFluid(int tank, FluidStack fluid);

    // ------------------------------------------------------ ★R95 S5：16G 双轨的 long 面 + STACK 位查询

    /**
     * ★R95 S5：第 {@code tank} 号流体槽的 <b>long 余量</b>（16G 真值域；通道抽取用，与
     * {@link #fluidBarRoom(int, FluidStack)} 的 int 头口径并存——后者服务世界侧/交互面）。
     */
    long fluidBarRoomL(int tank, FluidStack probe);

    /**
     * ★R95 S5：往第 {@code tank} 号槽灌入 {@code amount} mB 的 long 原语（真值域直达 16G；
     * 单笔超 int 的通道批量靠它，不经过头层 {@code fill} 的 int 容量算术）。
     *
     * @return 实际接收量（long）
     */
    long depositFluidL(int tank, net.minecraftforge.fluids.Fluid fluid, long amount);

    /**
     * ★R95 S5：从第 {@code tank} 号槽抽走 {@code amount} mB 的 long 原语（真值域；注入向在
     * 元件真收货之后调用，差额纪律与 {@link #drainOwnTank(int, int)} 同一条）。
     *
     * @return 实际抽走量（long）
     */
    long drainOwnTankL(int tank, long amount);

    /**
     * ★R95 S5：STACK 升级位是否固化（存储格堆叠 ×16 与源质每格上限 256→4096 的共同输入；
     * 通道消费侧的 room/上限钳与源质声明档回落都读它）。
     */
    boolean storageStackUpgraded();

    // ------------------------------------------------------ ★R86 缺陷 3：口袋 → 元件的推送向来源面

    /**
     * 流体条的 tank 数（注入向来源枚举用；与 {@code PocketFilterConfig.Kind.FLUID} 的索引空间同一）。
     */
    int fluidTankCount();

    /**
     * 第 {@code tank} 号槽<b>当前</b>那份流体（不改动任何东西；越界或空槽返 {@code null}）。
     * <p>
     * ★注入支靠它做防陈旧对表：快照里记的是"当时看见的那一条内容的键 + 量"，真投递前必须现读一遍比过，
     * 否则玩家在两拍之间把槽换成别的流体就会把旧内容灌进元件（与物品支
     * {@code PocketAeChannelOps#inject} 的 contentKey 对表同一条纪律）。
     */
    FluidStack fluidInTank(int tank);

    /**
     * 从第 {@code tank} 号槽抽走 {@code milliBuckets} mB（★只在元件那边<b>真的收了货</b>之后调）。
     *
     * @return 实际抽走量；小于请求量 ⇒ 调用方必须把差额<b>原路注回元件</b>，不得凭空抹掉（R85 小项 2 纪律）
     */
    int drainOwnTank(int tank, int milliBuckets);

    /** 源质表现存内容（{@code tag → 点数}，不可变快照）；注入向来源枚举用。 */
    Map<String, Integer> essenceStock();

    /**
     * 从源质表扣掉 {@code points} 点该 tag（★只在元件真的收了晶化源质之后调）。
     *
     * @return 实际扣掉的点数（不足按现存给；0 ⇒ 一格都没动）
     */
    int drainEssence(String tag, int points);
}
