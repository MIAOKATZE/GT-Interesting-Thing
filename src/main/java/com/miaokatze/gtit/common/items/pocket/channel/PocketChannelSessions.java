package com.miaokatze.gtit.common.items.pocket.channel;

import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtit.common.items.pocket.PocketCellBindings;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceStore;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketSession;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * ★R100（需求 2「免开背包」）：持续化通道的<b>会话复原腿</b> + 载体档解析的<b>单一公共入口</b>。
 * <p>
 * <b>为什么需要它</b>：{@code PocketSession} 的唯一登记点此前只有面板装配
 * （{@code NekoPocketPanel#assemble} 的服务端分支，开界面才登记）⇒ 玩家登录后从不开背包时
 * {@code PocketSessions.peek} 恒空，而 R96 S5 的激活口又要求"会话在场且认的就是这一枚承载栈"
 * ⇒ 持续化通道永远起不来。本类补的就是那一腿：<b>驱动侧</b>（{@code PocketChannelDriver} 的真空支）
 * 在「persist 位开 + 会话缺失」时就地复原一只 <b>headless 会话</b>（无 GUI、恒 {@code isOpen()=false}），
 * 让"有位 ⇒ 有道"在不开界面的前提下闭环。
 * <p>
 * <b>★同源纪律（禁止第二份真相）</b>：会话的"解析"（载体 NBT → {@link PocketInventory}）只有
 * {@link #parseCarrierInventory(ItemStack)} 这<b>一个</b>入口——面板装配（{@code NekoPocketPanel}
 * 构造器那一行）与复原腿都调它；登记则共用 {@link PocketSessions#register} 那个既有单点。
 * 面板自己的升级探针注入（活查载体栈那两条 lambda）<b>留在面板</b>（门禁按"注入点恰 1"钉着它），
 * headless 这边注入的是同一式子的本栈版本（读的是同一个 NBT 根 ⇒ 同一份真相）。
 * <p>
 * <b>三条前置一条不省（对齐 R96 S5 那三条）</b>：① persist 位当前<b>生效</b>（组合谓词，关着开关这条路
 * 跟着断）；② 复原出的会话认的就是<b>此刻被 tick 的这一枚</b>承载栈（对象身份）；③ 绑定表非空
 * （空表不开道——那是只会空跑、还会把会话永久钉在内存里的形状）。任一不满足 ⇒ <b>零写入</b>返回
 * {@code null}：不建会话、不建通道条目、不写一个字节的 NBT。
 * <p>
 * <b>防"复原-退役"振荡</b>：headless 会话恒 {@code isOpen()=false} ⇒ 满足
 * {@code PocketChannelDriver#retireIdleSession} 的判据一致性（退役只会发生在"道已停 + 蒸馏空"，
 * 而道停的前提恰是 persist 关掉 ⇒ 复原腿同因不再触发）。落盘（{@link #persistIdle}）复用
 * {@code PocketInventory#writeTo} 的"脏标记才序列化"口径，与面板的 {@code writeSessionToCarrier}
 * 同一条"谁在写档只有一个答案"的纪律；找不到承载栈时只 WARN 不回滚（面板那条终态回滚簇被离线
 * 套件按方法体锚定在面板上，headless 这边不复制第二份回滚状态机，如实声明）。
 */
public final class PocketChannelSessions {

    private PocketChannelSessions() {}

    /**
     * 载体档 → 会话内存的<b>唯一解析入口</b>（面板装配与复原腿共用，别处不得再写第二份
     * "取 NBT 根 + readFrom"的式子）。{@code null} 栈 / 无根 ⇒ 空档解析（不建档，R53c）。
     */
    public static PocketInventory parseCarrierInventory(ItemStack carrier) {
        return PocketInventory.readFrom(carrier == null ? null : carrier.getTagCompound());
    }

    /**
     * 复原一只 headless 会话（★三条前置见类 javadoc；任一不满足 ⇒ {@code null} 且<b>零写入</b>）。
     * 成功 ⇒ 已登记进 {@link PocketSessions}（同键顶替旧会话时旧会话先落盘，语义与面板开屏一致）。
     * <p>
     * 成本口径（R53c）：调用方（driver 真空支）每 tick 都会路过这里 ⇒ 先用
     * {@link PocketCellBindings#readFrom} 那张<b>便宜</b>的绑定表当门（只解析 boundCells 一张
     * NBTTagList，≤64 条）；表空 ⇒ 直接判"无可复原"，<b>不做</b>整套 135+36+12 格的 readFrom。
     * 只有真要复原的那一拍才付全档解析的成本（复原后会话在场 ⇒ 不会逐拍重复付）。
     */
    public static PocketSession restoreIfBound(EntityPlayer player, ItemStack carrier) {
        if (player == null || carrier == null) {
            return null;
        }
        final UUID uuid = player.getGameProfile() == null ? null
            : player.getGameProfile()
                .getId();
        if (uuid == null) {
            return null;
        }
        // ★前置①：persist 位当前生效（组合谓词；关着开关这条路跟着断，S1 语义不被绕过）
        if (!PocketUpgradeSwitches.isActive(carrier, PocketUpgradeType.CHANNEL_PERSIST)) {
            return null;
        }
        final NBTTagCompound root = carrier.getTagCompound();
        if (root == null) {
            return null;
        }
        // ★前置③的便宜门 + 前置②的载体：绑定表非空才值得付全档解析（空表 = 没有可搬的对面）
        if (PocketCellBindings.readFrom(root)
            .isEmpty()) {
            return null;
        }
        final PocketInventory inventory = parseCarrierInventory(carrier);
        if (inventory.bindings()
            .isEmpty()) {
            // 全档解析后表仍空（档与门读数不一致的病态形状）⇒ 按前置③拒绝，零登记零写入
            return null;
        }
        final HeadlessSession session = new HeadlessSession(uuid, player, carrier, inventory);
        PocketSessions.register(session);
        return session;
    }

    /**
     * headless 会话的<b>驱动侧回填口</b>：driver 每拍到场时把"此刻真被 tick 的那一枚"回填进会话
     * （面板会话有自己的 relocateCarrier，<b>不</b>实现本口 ⇒ driver 按 instanceof 识别，互不串）。
     */
    interface HeadlessCarrier {

        /** 回填承载栈引用（只换引用，不改任何内容判定）。 */
        void retargetCarrier(ItemStack stack);
    }

    /**
     * 无 GUI 的活会话：全部读写委托给复原时解析出的那一份 {@link PocketInventory}
     * （与面板会话同一条"内存里只有一份真相"的纪律），落盘写回承载栈 NBT。
     */
    private static final class HeadlessSession implements PocketSession, HeadlessCarrier {

        private final UUID playerId;
        private final EntityPlayer player;
        /** 复原时认下的承载栈（对象身份）；由 driver 在每次 tick 到场时按需回填（{@link #retargetCarrier}）。 */
        private ItemStack carrier;
        private final PocketInventory inventory;

        HeadlessSession(UUID playerId, EntityPlayer player, ItemStack carrier, PocketInventory inventory) {
            this.playerId = playerId;
            this.player = player;
            this.carrier = carrier;
            this.inventory = inventory;
            // 与面板构造器同一式子的本栈版：探针读<b>这一枚栈</b>的活根（位图与 off-mask 现读）。
            // 面板那份活查 lambda 留在面板（门禁钉"注入点恰 1"）；两条式子同形同源，不构成第二份真相。
            this.inventory.setUpgradeProbes(
                () -> PocketUpgradeSwitches.isActive(this.carrier, PocketUpgradeType.CAPACITY),
                () -> PocketUpgradeSwitches.isActive(this.carrier, PocketUpgradeType.STACK));
        }

        /** driver 在 tick 到场时回填"此刻真被 tick 的那一枚"（关屏重定位/堆叠移动后引用不陈旧）。 */
        @Override
        public void retargetCarrier(ItemStack stack) {
            this.carrier = stack;
        }

        @Override
        public UUID playerId() {
            return playerId;
        }

        @Override
        public EntityPlayer player() {
            return player;
        }

        @Override
        public ItemStack carrierStack() {
            return carrier;
        }

        @Override
        public NBTTagCompound carrierTag() {
            return carrier == null ? null : carrier.getTagCompound();
        }

        /** headless 恒 false：无界面可关 ⇒ 与 retireIdleSession 的判据天然一致（防"复原-退役"振荡）。 */
        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public boolean isHeldByOwner() {
            if (player == null || player.inventory == null || carrier == null) {
                return false;
            }
            for (final ItemStack held : player.inventory.mainInventory) {
                if (held == carrier) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void markDirty() {
            inventory.markDirty();
        }

        /** 脏标记才序列化（R53c）；写回的就是会话认得的那一枚承载栈的 NBT 根。 */
        @Override
        public void persistIdle() {
            writeIfDirty();
        }

        @Override
        public void persistFinal() {
            writeIfDirty();
        }

        private void writeIfDirty() {
            if (!inventory.isDirty() || carrier == null) {
                if (inventory.isDirty()) {
                    // ★与面板 B3 兜底同一条如实口径：承载栈暂时找不到 ⇒ 不写也不清脏，等下一拍重试；
                    // 面板那条源质增量逆施回滚被离线套件锚定在面板上，此处不复制第二份回滚状态机。
                    GTInterestingThing.LOG.warn("[pocket] 持续化会话落盘时承载栈缺席（玩家 {}），本次内容暂不落档，脏标记保留", playerId);
                }
                return;
            }
            NBTTagCompound root = carrier.getTagCompound();
            if (root == null) {
                root = new NBTTagCompound();
                carrier.setTagCompound(root);
            }
            inventory.writeTo(root);
            inventory.markClean();
        }

        @Override
        public PocketCellBindings bindings() {
            return inventory.bindings();
        }

        @Override
        public PocketFilterConfig filters() {
            return inventory.filters();
        }

        @Override
        public PocketEssenceStore essence() {
            return inventory.essence();
        }

        @Override
        public ItemStack distillInputStack(int index) {
            return index < 0 || index >= distillInputSlots() ? null
                : inventory.distillInput()
                    .getStackInSlot(index);
        }

        @Override
        public int distillInputSlots() {
            return inventory.distillInput()
                .getSlots();
        }

        @Override
        public void consumeOneDistillInput(int index) {
            final ItemStack at = distillInputStack(index);
            if (at == null) {
                return;
            }
            if (at.stackSize <= 1) {
                inventory.distillInput()
                    .setStackInSlot(index, null);
            } else {
                final ItemStack rest = at.copy();
                rest.stackSize = at.stackSize - 1;
                inventory.distillInput()
                    .setStackInSlot(index, rest);
            }
            inventory.markDirty();
        }

        @Override
        public int depositItem(ItemStack stack) {
            if (stack == null || stack.stackSize <= 0) {
                return 0;
            }
            // 与面板 depositItem 同一条落点序：先口袋中栏（跳过 ghost 格），装不下的余量才进玩家背包
            final int want = stack.stackSize;
            final int moved = inventory.depositIntoStorage(stack);
            final int left = want - moved;
            if (left <= 0 || player == null) {
                return moved;
            }
            final ItemStack rest = stack.copy();
            rest.stackSize = left;
            final boolean accepted = player.inventory.addItemStackToInventory(rest);
            // 与面板 moveToPlayer 同一口径（movedByVanillaContract 的同形算式，不跨包引面板）：原版契约
            // "返回 true ⇔ 入参已置 0" ⇒ true 支按请求数计；false 支按余量现读并双向钳非负。
            if (accepted) {
                return moved + left;
            }
            final int net = Math.max(0, Math.min(left, left - rest.stackSize));
            return moved + net;
        }

        @Override
        public int storageSlots() {
            return inventory.storage()
                .getSlots();
        }

        @Override
        public ItemStack storageStackAt(int slot) {
            final int size = storageSlots();
            return slot < 0 || slot >= size ? null
                : inventory.storage()
                    .getStackInSlot(slot);
        }

        @Override
        public void setStorageStackAt(int slot, ItemStack stack) {
            if (slot < 0 || slot >= storageSlots()) {
                return;
            }
            inventory.storage()
                .setStackInSlot(slot, stack);
            inventory.markDirty();
        }

        @Override
        public boolean isStorageGhostDeclared(int slot) {
            return inventory.isGhostItemSlot(slot);
        }

        @Override
        public int fluidBarRoom(int tank, FluidStack probe) {
            return inventory.fluidBarRoom(tank, probe);
        }

        @Override
        public int depositFluid(int tank, FluidStack fluid) {
            return inventory.depositFluidIntoBar(tank, fluid);
        }

        @Override
        public int fluidTankCount() {
            return PocketInventory.tankCount();
        }

        @Override
        public FluidStack fluidInTank(int tank) {
            return inventory.ownTankFluid(tank);
        }

        @Override
        public int drainOwnTank(int tank, int milliBuckets) {
            return inventory.drainOwnTank(tank, milliBuckets);
        }

        @Override
        public long fluidBarRoomL(int tank, FluidStack probe) {
            return inventory.fluidBarRoomL(tank, probe);
        }

        @Override
        public long depositFluidL(int tank, Fluid fluid, long amount) {
            return inventory.fillOwnTankL(tank, fluid, amount);
        }

        @Override
        public long drainOwnTankL(int tank, long amount) {
            return inventory.drainOwnTankL(tank, amount);
        }

        @Override
        public boolean storageStackUpgraded() {
            // 与注入的 STACK 探针同一式子（PocketUpgradeSwitches 组合谓词，读本栈活根）——
            // PocketInventory#storageStackUpgraded 是包私有读口，headless 住在 channel 包够不着它，
            // 而探针本身就是这一条谓词 ⇒ 直接问谓词，不构成第二份真相。
            return PocketUpgradeSwitches.isActive(carrier, PocketUpgradeType.STACK);
        }

        @Override
        public Map<String, Integer> essenceStock() {
            return inventory.essence()
                .snapshot();
        }

        @Override
        public int drainEssence(String tag, int points) {
            final int removed = inventory.essence()
                .extract(tag, points);
            if (removed > 0) {
                // ★R90 S1 同一条纪律（面板 drainEssence 的镜像）：扣点必须登记增量日志并置脏，
                // 否则"扣点只在内存、序列化只在落盘"这条链一断就是净复制。
                inventory.recordEssenceDelta(tag, -removed);
                inventory.markDirty();
            }
            return removed;
        }
    }
}
