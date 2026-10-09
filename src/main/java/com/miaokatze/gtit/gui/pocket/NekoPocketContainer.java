package com.miaokatze.gtit.gui.pocket;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 口袋面板的 Container（两件事：<b>关屏写状态的落点</b> + <b>每拍同步硬化钩子</b>）。
 * <p>
 * 为什么必须是这个钩子（R35）：MUI2 在 {@code EntityPlayerMPMixin:22-27} 里把
 * {@code Container#onContainerClosed} 的"真关屏"事件转成 {@code onModularContainerClosed()}，
 * 与 {@code onContainerClosed} 本身的区别是 <b>NEI/HEI 顶屏不会误触发</b>
 * （{@code ModularContainer.java:104-108} 的 javadoc 明写这一点）。
 * 若把写档放在 {@code addCloseListener}，玩家只是打开 NEI 看一眼就会把会话缓冲落盘一次。
 * <p>
 * 本类由 {@link NekoPocketPanel} 经 {@code UISettings#customContainer} 提供
 * （{@code GuiManager.java:81/112/143} 双端都读该 supplier ⇒ 双端同一个类，不破坏 R32）。
 * {@code slotClick} 已覆写（仅服务端拦截：中栏超过 64 的存储堆只放行空手取件，其余手势拒绝
 * ——R104 用户裁定「超过 64 不允许被替换」，旧「前置拆分顶背包」方案在背包已有同物时行为
 * 怪异被否；其余路径原样交 MUI2）；{@code transferStackInSlot} 仍不覆写，也<b>不手工加槽</b>：
 * 槽位注册与其余点击处理全交 MUI2（slice-s4-brief §5 判据里"口袋不手工加槽"是硬口径）。
 * <p>
 * ★★<b>R97 R2：唯一的新覆写是 {@code detectAndSendChanges}</b>（{@code @MustBeInvokedByOverriders}，
 * super 先行）。 vanilla 每 tick 对 {@code openContainer} 调它、MUI2 的 {@code slotClick} 在点击
 * 收尾也调它 ⇒ 一处钩子覆盖全部 vanilla 点击路径；客户端支整体 no-op。
 */
public class NekoPocketContainer extends ModularContainer {

    private final NekoPocketPanel panel;
    /**
     * ★R97 R2：上一拍比对过的<b>游标副本</b>（{@code copy()} 而非活引用——vanilla 的
     * {@code splitStack(n)} 在<b>同一个对象</b>上原地扣件数，缓存活引用会跟着变、差分永远看不见，
     * 即 R12 那条 {@code forceSyncItem} 活引用边角的同族）。
     */
    private ItemStack lastCursorSynced;
    /** ★R97 R2：首拍只初始化缓存不推送（开屏全量包已带游标，推了就是回声）。 */
    private boolean cursorSyncSeeded;

    public NekoPocketContainer(NekoPocketPanel panel) {
        this.panel = panel;
    }

    @Override
    public void onModularContainerClosed() {
        super.onModularContainerClosed();
        // 落点集中：脏标记判定、open 位清零、承载格重定位与"找不到只 warn 不落盘"
        // 全在 NekoPocketPanel.onContainerClosed()（R35 四道防御 + R24 清理点集中一处）
        panel.onContainerClosed();
    }

    /**
     * ★★<b>R97 R2（游标差分推送）+ R5（拒收强推消费）：把 MUI2 的 push-only 游标同步补成准 pull</b>。
     * <p>
     * 库层事实（取证 r97-inv-sync §2.1/§2.2）：1.7.10 服务端→客户端的游标唯一 vanilla 通道是
     * mismatch 全量救援包里的 {@code S2FPacketSetSlot(-1,-1,cursor)}，而 MUI2 的
     * {@code ModularContainer#slotClick} 对 PICKUP/QUICK_MOVE <b>恒返 null</b> ⇒ 双端返回值恒相等
     * ⇒ mismatch 救援<b>永不触发</b>；库自带的 {@code CursorSlotSyncHandler} 又只在
     * {@code setCursorItem} 显式调用时才推（没有 detect 覆写）⇒ vanilla 点击造成的游标变化
     * （{@code slotClick} 里全是裸 {@code inventoryplayer.setItemStack}) 永远不发 S2C ⇒
     * 一切点击分歧都以「幽灵游标」呈现且不自愈（用户报④的正身）。
     * <p>
     * 形状：super 之后（先让 vanilla 槽差分与 MUI2 同步值跑完），服务端比对<b>缓存的游标副本</b>与
     * {@code inventoryplayer.getItemStack()}，变更才 {@code syncManager.setCursorItem(现值)}
     * （它才是带 S2C 推送的那一半，R88 B1 判例）⇒ 游标纠正与点击同 tick 出包。客户端支 no-op
     * （客户端那份 {@code PanelSyncManager.isClient()} 为真，本方法整体早退）。
     * <p>
     * R5 同钩子消费：{@code PocketInventory} 的 {@code isItemValid} 拒收记账（读一次即清）非负时，
     * 对被点槽做一次 {@code PocketSlots#forceSyncSlot} 强推（R86 先例，isInitialized 挡装配前）⇒
     * 压掉「客户端预测接受 / 服务端拒收 ⇒ 无纠正包」的反向幽灵 ≤1 tick 窗口。
     */
    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (panel.syncManager()
            .isClient()) {
            return;
        }
        pushCursorDiff();
        forceSyncRejectedStorageSlot();
    }

    /**
     * 大堆交互线（R104 用户裁定）：超过 64 的存储堆不接受任何"整堆离格"的手势。
     * 64 = vanilla 天然满量；大堆（STACK 升级档）只能靠 Shift 搬运或空手取件出格。
     */
    private static final int OVERSIZED_STACK_LINE = 64;

    /**
     * ★大堆拦截（R104 修订：由「前置拆分顶背包」改为「超过 64 不允许被替换」——用户实测拆分支
     * 在背包已有同物时几乎不加量、其余掉脚下，裁定干脆禁止）。只拦<b>服务端</b>：客户端那份
     * 槽栈对 &gt;127 的堆本就是 byte 回绕后的坏数据，拦不住也不该拦（预测瞬态由拒绝后的双纠正拉回）。
     * QUICK_MOVE（mode 1）早退放行——MUI2 自带按天然满量拆块且失败留格，天然安全。
     */
    @Override
    public ItemStack slotClick(int slotId, int mouseButton, int mode, EntityPlayer player) {
        if (mode != 1 && !panel.syncManager()
            .isClient()) {
            final ModularSlot modular = storageSlotAt(slotId);
            if (modular != null && rejectsOversizedClick(
                modular.getStack(),
                mode,
                player == null ? null : player.inventory.getItemStack())) {
                rejectOversizedClick(modular, player);
                return null;
            }
        }
        return super.slotClick(slotId, mouseButton, mode, player);
    }

    /** 被点格若是中栏存储格则返回该 {@code ModularSlot}（服务端真值面），否则 null。 */
    private ModularSlot storageSlotAt(int slotId) {
        if (slotId < 0 || slotId >= this.inventorySlots.size()) {
            return null;
        }
        if (this.inventorySlots.get(slotId) instanceof ModularSlot modular
            && PocketSlots.GROUP_STORAGE.equals(modular.getSlotGroupName())) {
            return modular;
        }
        return null;
    }

    /**
     * 大堆拦截判据（★纯函数，JVM 直测）：超过 {@link #OVERSIZED_STACK_LINE} 的存储堆，只放行
     * <b>空手取件</b>（mode 0 且光标为空——MUI2 的 PICKUP 按天然上限截取，左键 ≤64 / 右键 ≤32
     * 上游标，1.7.10 的 byte 数量序列化天然安全）；其余手势一律拒绝，各自对应的蒸发面：
     * <ul>
     * <li>手持物品点击（替换 swap / 同物合并）——swap 分支把整堆推上游标，&gt;127 出网络回绕成 0
     * （物品蒸发，正是用户报的"大堆被替换就消失"；合并也一并拒：手上有东西就不许碰大堆，规则只有一条）；</li>
     * <li>热键换位（mode 2）——vanilla 把整堆塞进玩家背包栏，落 NBT 时同一条 byte 纪律蒸发；</li>
     * <li>creative 克隆（mode 3）——光标拿到整堆副本，同 swap；</li>
     * <li>丢掷（mode 4，Q / Ctrl+Q）——Ctrl+Q 整堆丢出，EntityItem.Count 是 byte，1024 回绕。</li>
     * </ul>
     * 判据本体对 mode 1 也表拒绝，作 QUICK_MOVE 上游早退之外的第二层防线。
     */
    static boolean rejectsOversizedClick(ItemStack slotStack, int mode, ItemStack held) {
        if (slotStack == null || slotStack.stackSize <= OVERSIZED_STACK_LINE) {
            return false;
        }
        return mode != 0 || held != null;
    }

    /**
     * 拒绝后的双纠正：客户端会先本地预测一遍 MUI2 的手势（槽被换成手上的 A、游标拿到大堆副本），
     * 服务端拒绝后若不拉回，两端分叉无包可纠（MUI2 对 PICKUP 恒返 null ⇒ vanilla mismatch 救援
     * 永不触发，R97 R2 取证的同族幽灵）——① {@code forceSyncSlot} 把槽真值（byte 口径，与拒绝前
     * 一致）强推回去覆盖预测；② {@code setCursorItem} 把游标真值强推回去。返回值与 MUI2 的
     * PICKUP 口径一致取 null：mismatch 救援包推的是服务端返回值本身，&gt;127 同样回绕，救不了。
     */
    private void rejectOversizedClick(ModularSlot modular, EntityPlayer player) {
        PocketSlots.forceSyncSlot(modular);
        // A late vanilla S30 covers all slots, so restore every precise storage count next tick.
        for (Object candidate : this.inventorySlots) {
            if (candidate instanceof ModularSlot storage && storage.isInitialized()
                && PocketSlots.GROUP_STORAGE.equals(storage.getSlotGroupName())
                && storage.getSyncHandler() instanceof PocketItemSlotSyncHandler precise) {
                precise.requestResync();
            }
        }
        panel.syncManager()
            .setCursorItem(player == null ? null : player.inventory.getItemStack());
    }

    /** ★R97 R2 的差分本体（见 {@link #detectAndSendChanges()} 的库层取证）。 */
    private void pushCursorDiff() {
        final ItemStack current = panel.syncManager()
            .getCursorItem();
        if (!cursorSyncSeeded) {
            cursorSyncSeeded = true;
            lastCursorSynced = current == null ? null : current.copy();
            return;
        }
        if (ItemStack.areItemStacksEqual(lastCursorSynced, current)) {
            return;
        }
        lastCursorSynced = current == null ? null : current.copy();
        // 兜底缺陷指示器：1.7.10 数量以有符号 byte 序列化，>127 出网络即回绕蒸发；
        // slotClick 的大堆拦截本应阻止超 64 堆以任何手势离格，仍见到 ⇒ 有路径漏拦，请回报复现路径。
        if (current != null && current.stackSize > 127) {
            GTInterestingThing.LOG.warn(
                "口袋游标仍出现 {} 件的超天然大堆（{}），1.7.10 byte 序列化会回绕蒸发；" + "slotClick 大堆拦截本应阻止——本条 WARN 即缺陷指示器",
                current.stackSize,
                current.getUnlocalizedName());
        }
        // setCursorItem = setItemStack（对现值是 no-op）+ cursorSlotSyncHandler.sync()（S2C 推送）
        panel.syncManager()
            .setCursorItem(current);
    }

    /** ★R97 R5 的强推本体：拒收记账非负 ⇒ 找到中栏那一格的 {@code ModularSlot} 强推真值。 */
    private void forceSyncRejectedStorageSlot() {
        final int slot = panel.inventory()
            .consumeRejectedPlacementSlot();
        if (slot < 0) {
            return;
        }
        // 只在「真有拒收」的那一拍扫一次 225 个槽（记账侧零扫描的对称面：低频路径才许扫描）。
        // 匹配 = 槽组名 + handler 内索引（三个区域各自从 0 起编号，裸索引会撞别的区域）。
        for (Slot candidate : this.inventorySlots) {
            if (candidate instanceof ModularSlot modular && PocketSlots.GROUP_STORAGE.equals(modular.getSlotGroupName())
                && modular.getSlotIndex() == slot) {
                PocketSlots.forceSyncSlot(modular);
                return;
            }
        }
    }
}
