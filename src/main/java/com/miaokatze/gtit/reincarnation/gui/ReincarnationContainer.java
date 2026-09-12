package com.miaokatze.gtit.reincarnation.gui;

import java.io.File;
import java.util.Collections;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.reincarnation.ReincarnationConfirmHook;
import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.core.ReincarnationSaveGuard;
import com.miaokatze.gtit.reincarnation.core.ReincarnationStore;
import com.miaokatze.gtit.reincarnation.handler.ReincarnationHandler;
import com.miaokatze.gtit.reincarnation.storage.ReincarnationMigration;
import com.miaokatze.gtit.reincarnation.storage.ReincarnationWorldData;
import com.miaokatze.gtit.util.ServerTaskScheduler;

/**
 * 周目轮回容器（v1.8.3，MUI2 → Forge 原版 IGuiHandler 链路迁移；双端同类）
 * <p>
 * 逻辑唯一承载者（自 client/gui 旧 {@code ReincarnationCycleGui} 逐函数移植，语义零漂移；
 * 渲染在 client/gui 的 {@code ReincarnationGuiContainer}，布局/契约单源在
 * {@link ReincarnationLayout}）。
 * <p>
 * <b>服务端权威会话</b>：GUI 打开（{@code EntityPlayer.openGui} → IGuiHandler）时服务端
 * {@code store.load(uuid)} 建立会话，每次模型变更即 {@code store.save}；客户端镜像同构造
 * （空记录，展示走进度条同步值与槽位同步），与旧 GUI 双端镜像纪律一致。
 * <p>
 * <b>同步机制</b>：槽位内容由 vanilla {@code detectAndSendChanges} 槽位 diff 同步；
 * 计数/状态/锁定/武装/消息/连掷激活位经 {@code ICrafting.sendProgressBarUpdate} 推送
 * （id 映射见 {@link ReincarnationLayout} 进度条区，消息行仅传事件码、参数由客户端
 * 从其余进度条值与 lang 键本地派生，无需字符串同步通道——v1.8.3 裁决：消息行可用
 * 进度条表达，ReincarnationSyncPacket 不扩展）。
 * <p>
 * <b>线程模型</b>：vanilla 链路下槽位点击（{@code NetHandlerPlayServer.processClickWindow}）
 * 与按钮（{@code processEnchantItem} → {@link #enchantItem}）随 1.7.10 包队列机制
 * （{@code NetworkSystem.networkTick} 主线程排空）已在服务端主线程到达；
 * 模型变更与落盘等重活仍按任务契约统一经 {@link ServerTaskScheduler#scheduleServerTask}
 * 投递（同一 FIFO 队列保证「先寄存后确认」等复合操作的次序语义与旧 GUI 主线程队列一致）。
 * <p>
 * <b>交互语义</b>（与旧 GUI 逐条对照）：
 * <ul>
 * <li>外壳累计槽（每列 1 格）：仅未解锁列接受 {@code ReincarnationHullMatcher} 匹配外壳；
 * 放入即消耗（服务端暂持吸收量至 consumeHull 任务清空，经槽位 diff 纠正客户端预测），
 * 单次吸收 min(放入, 16-进度) 件、按吸收量计进度
 * （D2 堆叠口径），满 16 自动 unlockColumn；取物恒拒绝
 * （canTakeStack=false，即「放入即消耗」的字面化）；</li>
 * <li>物品格（每列 3 格 × 全局行解锁）：每格最大 1 件；放入 = {@code ItemRef(id+meta, 忽略 NBT)}
 * 记录，首件放入且 IDLE 经 {@code deposit} 过渡 IDLE→DEPOSITED，其余 {@code addPendingItem}；
 * 模型拒绝时槽位清空 + 物品退回（背包满则掉落）；取出 = {@code cycle.withdraw}，失败时格位
 * 重建（防御路径）；</li>
 * <li>解锁行按钮 ×2（v1.9.0 连掷重构，猫猫币支付格已摘除）：按下 = toggle 该币种连掷会话
 * （首按开启、再按停止）；会话激活期间每服务端 tick 从背包扣 1 枚对应猫猫币掷 1 次
 * （{@link Random} 判定，闪烁 1/1000，普通 1/100000，{@code nextInt(分母) == 0} 即成功）→
 * 成功 {@code setUnlockedRows+1}（成功不停止会话；失败静默不刷屏）。停止条件：
 * ① 无剩余可解锁目标（isUnlockAvailable 同口径）→ 静默停止；② 背包无该币种 →
 * 沿用 chat.unlock.no_coin 消息键；③ 登出/死亡/GUI 关闭 → 静默停止；④ 再按同按钮 →
 * 立即停止。会话注册表与 tick 驱动在 {@code ReincarnationHandler}（UUID → shimmer/normal
 * 两独立槽，不持久化），激活位经 {@link ReincarnationLayout#BAR_ROLL_ACTIVE} 同步；</li>
 * <li>确认轮回按钮：两步制（第一次点击仅服务端武装，第二次点击执行；槽位变动/关窗重置武装），
 * 替代旧 {@code NekoConfirmationDialog}；可用性 = DEPOSITED + 待领取非空 + 钩子已注入；
 * 执行经 {@link ReincarnationConfirmHook#onConfirmRequested} 回调 S4。</li>
 * </ul>
 * <p>
 * <b>侧与加载（三层自查：import/字段/方法体）</b>：本类零 MC 客户端侧类型引用、
 * 零 client 包引用，物理集成服务端可加载；物理专用服务器上周目系统整体门控，本类不可达。
 */
public class ReincarnationContainer extends Container {

    // ==================== lang 键（只读复用既有 gtit.reincarnation.* 键，lang 文件不改） ====================

    private static final String KEY_PREFIX = "gtit.reincarnation.";
    private static final String KEY_COLUMN_PREFIX = KEY_PREFIX + "column.";
    private static final String KEY_CHAT_COLUMN_UNLOCKED = KEY_PREFIX + "chat.column_unlocked";
    private static final String KEY_CHAT_HULL_CONSUMED = KEY_PREFIX + "chat.hull.consumed";
    private static final String KEY_CHAT_UNLOCK_SUCCESS = KEY_PREFIX + "chat.unlock.success";
    private static final String KEY_CHAT_UNLOCK_FAIL = KEY_PREFIX + "chat.unlock.fail";
    private static final String KEY_CHAT_UNLOCK_NO_COIN = KEY_PREFIX + "chat.unlock.no_coin";
    private static final String KEY_CHAT_READONLY = KEY_PREFIX + "chat.readonly";
    private static final String KEY_CHAT_DEPOSIT_FAILED = KEY_PREFIX + "chat.deposit.failed";
    private static final String KEY_CHAT_WITHDRAW_FAILED = KEY_PREFIX + "chat.withdraw.failed";
    private static final String KEY_CONFIRM_TOOLTIP_NOHOOK = KEY_PREFIX + "gui.confirm.tooltip.nohook";

    /** 解锁判定 RNG（任务包冻结：java.util.Random；仅服务端逻辑线程使用） */
    private static final Random UNLOCK_RNG = new Random();

    /** 闪烁猫猫币解锁成功率分母：1/1000（与旧 GUI SHIMMER_UNLOCK_ONE_IN 一致） */
    private static final int SHIMMER_UNLOCK_ONE_IN = 1000;
    /** 普通猫猫币解锁成功率分母：1/100000 */
    private static final int NORMAL_UNLOCK_ONE_IN = 100000;

    /** 周目状态 ordinal：EXECUTED（只读锁，口径见类 javadoc） */
    private static final int STATE_EXECUTED_ORDINAL = ReincarnationCycle.CycleState.EXECUTED.ordinal();
    /** 周目状态 ordinal：DEPOSITED */
    private static final int STATE_DEPOSITED_ORDINAL = ReincarnationCycle.CycleState.DEPOSITED.ordinal();

    // ==================== 会话字段 ====================

    /** 触发玩家（会话绑定） */
    private final EntityPlayer player;
    /** true = 服务端（权威模型）；false = 客户端镜像 */
    private final boolean server;
    /** 持久化仓库（仅服务端；baseDir = MC 运行目录，与旧 GUI 一致；D1 起仅承载许可字段） */
    private final ReincarnationStore store;
    /** 每存档进度载体（仅服务端；overworld MapStorage，D1 每存档隔离） */
    private final ReincarnationWorldData worldData;
    /**
     * 权威周目模型（服务端）/空记录（客户端）。
     * <p>
     * 非 final（A4）：确认回调在权威层推进 EXECUTED 落盘后，本模型为陈旧快照，
     * 须经 {@link #reloadIfLicenseAdvancedQuietly()} 按打开同款路径重载替换，
     * 防关窗保存用陈旧模型全量覆写抹掉磁盘上的信箱/指纹（丢更新覆写）。
     */
    private ReincarnationCycle cycle;

    /** 外壳累计槽缓冲（每列 1 格；服务端仅暂持本次吸收量至 consumeHull 任务消费后清空，见 HullSlot#putStack） */
    private final SessionInventory hullInventory = new SessionInventory(ReincarnationLayout.HULL_SLOT_COUNT, 1);
    /** 物品格缓冲（15 列 × 3 行，index = col*3+row，每格 1 件） */
    private final SessionInventory itemInventory = new SessionInventory(ReincarnationLayout.ITEM_SLOT_COUNT, 1);

    /** 物品格槽位对应的权威 ItemRef 快照（服务端；取出时定位 withdraw 目标，index = col*3+row） */
    private final ReincarnationCycle.ItemRef[] itemSlotRefs = new ReincarnationCycle.ItemRef[ReincarnationLayout.ITEM_SLOT_COUNT];

    /** 物品格 Slot 实例（按 col*3+row 索引，transferStackInSlot 复用同一套校验） */
    private final CycleItemSlot[] itemSlots = new CycleItemSlot[ReincarnationLayout.ITEM_SLOT_COUNT];

    /** 外壳累计槽 Slot 实例（按列下标索引，D2 shift-click 分支复用同一套校验/容量） */
    private final HullSlot[] hullSlots = new HullSlot[ReincarnationLayout.HULL_SLOT_COUNT];

    /** 确认按钮武装标记（仅服务端置位，槽位变动/关窗重置） */
    private boolean confirmArmed;
    /** 面板消息事件码（服务端；语义见 {@link ReincarnationLayout} 消息事件码区） */
    private int messageEvent = ReincarnationLayout.MSG_NONE;

    // ==================== 进度条同步缓存 ====================

    /** 服务端上次推送值（int[] 默认 0 初值，与客户端镜像 0 初值一致：0→0 不推送、非零差异必推送） */
    private final int[] lastHull = new int[ReincarnationLayout.HULL_SLOT_COUNT];
    private int lastState = -1;
    private int lastMask = -1;
    private int lastRows = -1;
    private int lastPending = -1;
    private int lastArmed = -1;
    private int lastMessage = -1;
    /** 连掷激活位上次推送值（bit0=闪烁 bit1=普通，映射见 {@link ReincarnationLayout#BAR_ROLL_ACTIVE}） */
    private int lastRollActive = -1;

    /** 客户端镜像（updateProgressBar 写入；访问器经 sided 分发读取） */
    private final int[] clientHull = new int[ReincarnationLayout.HULL_SLOT_COUNT];
    private int clientState;
    private int clientMask;
    private int clientRows = ReincarnationCycle.MIN_UNLOCKED_ROWS;
    private int clientPending;
    private int clientArmed;
    private int clientMessage;
    /** 客户端连掷激活位镜像（updateProgressBar 写入；bit0=闪烁 bit1=普通） */
    private int clientRollActive;

    /**
     * @param player 打开 GUI 的玩家（双端各建一实例）
     */
    public ReincarnationContainer(EntityPlayer player) {
        this.player = player;
        this.server = !player.worldObj.isRemote;
        if (server) {
            // 服务端：全局许可仓库 + 每存档进度（D1 拆分）。
            // ① 每存档载体就绪 → ② 旧全局 v1 字段一次性 consume-once 迁移进当档 →
            // ③ 读全局许可（指纹/投胎信箱，最后仁慈兜底空记录）→ ④ 每存档进度合并进模型
            this.store = new ReincarnationStore(new File("."));
            this.worldData = ReincarnationWorldData.get(
                MinecraftServer.getServer()
                    .getEntityWorld());
            ReincarnationMigration.migrateOnce(
                this.store,
                this.worldData,
                player.getUniqueID()
                    .toString());
            this.cycle = this.store.load(
                player.getUniqueID()
                    .toString());
            this.worldData.applyTo(this.cycle);
        } else {
            this.store = null;
            this.worldData = null;
            this.cycle = new ReincarnationCycle(
                player.getUniqueID()
                    .toString());
        }

        addSlotsToContainer();
        if (server) {
            // GUI 打开时按「列优先、行内自上而下」把 pendingItems 回填到已解锁格（超出的留在模型不展示）
            fillItemSlotsFromCycle();
        }
    }

    // ==================== 槽位构建（顺序必须与 ReincarnationLayout 全局下标一致） ====================

    private void addSlotsToContainer() {
        // 外壳累计槽 0..14（放入即消耗；D2 起一次吸收多件，容量 = 16 - 已吸收）
        for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT; column++) {
            HullSlot hullSlot = new HullSlot(
                column,
                ReincarnationLayout.GRID_X + column * ReincarnationLayout.SLOT_PITCH,
                ReincarnationLayout.HULL_Y);
            this.hullSlots[column] = hullSlot;
            addSlotToContainer(hullSlot);
        }
        // 物品格 15..59（15 列 × 3 行）
        for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT; column++) {
            for (int row = 0; row < ReincarnationCycle.MAX_UNLOCKED_ROWS; row++) {
                CycleItemSlot slot = new CycleItemSlot(
                    column,
                    row,
                    ReincarnationLayout.GRID_X + column * ReincarnationLayout.SLOT_PITCH,
                    ReincarnationLayout.ITEMS_Y + row * ReincarnationLayout.SLOT_PITCH);
                this.itemSlots[column * ReincarnationCycle.MAX_UNLOCKED_ROWS + row] = slot;
                addSlotToContainer(slot);
            }
        }
        // 玩家背包 60..95（主背包 inventory 9..35 + 快捷栏 inventory 0..8，vanilla 排布；
        // v1.9.0 连掷重构：原币格 60 摘除，背包下标前移）
        InventoryPlayer inventory = this.player.inventory;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlotToContainer(
                    new Slot(
                        inventory,
                        column + 9 + row * 9,
                        ReincarnationLayout.PLAYER_INV_X + column * ReincarnationLayout.SLOT_SIZE,
                        ReincarnationLayout.INVENTORY_Y + row * ReincarnationLayout.SLOT_SIZE));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlotToContainer(
                new Slot(
                    inventory,
                    column,
                    ReincarnationLayout.PLAYER_INV_X + column * ReincarnationLayout.SLOT_SIZE,
                    ReincarnationLayout.INVENTORY_Y + 58));
        }
    }

    /**
     * 外壳累计槽：放入即服务端按容量一次吸收（重活经调度器投递），取物恒拒绝。
     * <p>
     * D2 堆叠口径：容量 = {@link ReincarnationLayout#HULL_TARGET} - 已吸收（满 16 拒放）；
     * vanilla 放置按 {@link #getSlotStackLimit()} 拆分，本槽 putStack 一次吸收
     * {@code min(放入, 容量)}（只消费 accepted 只计 accepted，多余留在光标/原槽）。
     * v1.8.8a：吸收量暂持槽内（非立即清空）以驱动 vanilla 槽位 diff 纠正客户端预测，
     * consumeHull 任务同/次 tick 消费清空——「放入即消耗」的用户观感由纠正包承载。
     */
    private final class HullSlot extends Slot {

        /** 该槽对应的等级列下标 */
        private final int column;

        HullSlot(int column, int x, int y) {
            super(hullInventory, column, x, y);
            this.column = column;
        }

        /** 仅未解锁列 + 匹配外壳 + 空格 + 容量未满可放入（客户端预测与服务端权威同口径） */
        @Override
        public boolean isItemValid(ItemStack stack) {
            return getStack() == null && !isReadonly()
                && isColumnLocked(this.column)
                && getColumnCount(this.column) < ReincarnationLayout.HULL_TARGET
                && ReincarnationHullMatcher.matchesHull(stack, this.column);
        }

        /** 「放入即消耗」的字面化：槽位不持有物品，无物可取 */
        @Override
        public boolean canTakeStack(EntityPlayer p_82869_1_) {
            return false;
        }

        /**
         * 剩余容量：16 - 已吸收（满 16 → 0，拒放；sided 经进度条镜像，双端同口径）
         */
        @Override
        public int getSlotStackLimit() {
            return Math.max(0, ReincarnationLayout.HULL_TARGET - getColumnCount(this.column));
        }

        @Override
        public void putStack(ItemStack stack) {
            if (stack == null) {
                super.putStack(null);
                return;
            }
            if (server) {
                disarmConfirm();
                // v1.8.8a 同 tick 连击防御：上一次吸收量仍在槽内等待任务消费时拒绝续投
                // （暂持策略下槽位非空 = 任务未跑；容量按进度计尚未包含本次，防双记）
                if (getStack() != null) {
                    giveBack(stack);
                    return;
                }
                int capacity = getSlotStackLimit();
                int accepted = Math.min(stack.stackSize, capacity);
                if (accepted <= 0 || isReadonly()
                    || !isColumnLocked(this.column)
                    || !ReincarnationHullMatcher.matchesHull(stack, this.column)) {
                    giveBack(stack); // 校验失败（竞态/已满/异常路径）：物品整份退回玩家，不进槽
                    return;
                }
                // 一次吸收 min(放入, 容量)：只消费 accepted 只计 accepted；
                // 多余（仅异常路径可达——vanilla 已按 limit 拆分）退回玩家
                final int targetColumn = this.column;
                final ItemStack consumed = stack.copy();
                consumed.stackSize = accepted;
                if (accepted < stack.stackSize) {
                    ItemStack excess = stack.copy();
                    excess.stackSize = stack.stackSize - accepted;
                    giveBack(excess);
                }
                // v1.8.8a 实机回归修复：槽位暂持本次吸收量而非立即清空——客户端本地
                // slotClick 预测会把同量堆放进客户端槽位，若服务端恒空则服务端槽位 diff
                // （null vs null）永无变化、不发 set-slot 纠正包，客户端预测残留"永远卡槽"
                // （手点无同步）。暂持（null→held→null）经 vanilla diff 下发两次纠正：
                // 首包与预测同值无观感，次包（consumeHull 清槽）即"放入即消失"。
                super.putStack(consumed);
                scheduleServerTask(() -> consumeHull(targetColumn, consumed));
            } else {
                super.putStack(stack); // 客户端预测；服务端以 held→null 两段 set-slot 纠正（见服务端分支注释）
            }
        }
    }

    /** 物品格：仅已解锁列的已解锁行可放入；每格 1 件；放入/取出经服务端同步到权威模型 */
    private final class CycleItemSlot extends Slot {

        /** 背包内局部下标（col*3+row；与 itemSlotRefs / itemInventory 对齐） */
        private final int localIndex;
        private final int column;
        private final int row;

        CycleItemSlot(int column, int row, int x, int y) {
            super(itemInventory, column * ReincarnationCycle.MAX_UNLOCKED_ROWS + row, x, y);
            this.localIndex = column * ReincarnationCycle.MAX_UNLOCKED_ROWS + row;
            this.column = column;
            this.row = row;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return getStack() == null && !isReadonly() && !isColumnLocked(this.column) && this.row < getUnlockedRows();
        }

        /** EXECUTED 只读锁：取出禁用（与旧 GUI 只读态禁用槽位交互同口径） */
        @Override
        public boolean canTakeStack(EntityPlayer p_82869_1_) {
            return !isReadonly();
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }

        @Override
        public void putStack(ItemStack stack) {
            if (stack == null) {
                super.putStack(null);
                return;
            }
            if (server) {
                disarmConfirm();
                super.putStack(stack); // 槽位缓冲持有展示堆，权威记录由 placeItem 任务落模型
                final int index = this.localIndex;
                final ItemStack placed = stack.copy();
                scheduleServerTask(() -> placeItem(index, placed));
            } else {
                super.putStack(stack);
            }
        }

        @Override
        public void onPickupFromSlot(EntityPlayer who, ItemStack taken) {
            super.onPickupFromSlot(who, taken);
            if (server) {
                disarmConfirm();
                final int index = this.localIndex;
                scheduleServerTask(() -> takeItem(index));
            }
        }
    }

    /** 纯 GUI 会话背包（不入世界、不接自动化；markDirty 无需落盘——模型变更在任务内显式 save） */
    private final class SessionInventory implements IInventory {

        private final ItemStack[] stacks;
        private final int stackLimit;

        SessionInventory(int size, int stackLimit) {
            this.stacks = new ItemStack[size];
            this.stackLimit = stackLimit;
        }

        @Override
        public int getSizeInventory() {
            return this.stacks.length;
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            return this.stacks[index];
        }

        @Override
        public ItemStack decrStackSize(int index, int count) {
            ItemStack stack = this.stacks[index];
            if (stack == null) {
                return null;
            }
            if (stack.stackSize <= count) {
                this.stacks[index] = null;
                return stack;
            }
            ItemStack split = stack.splitStack(count);
            if (stack.stackSize == 0) {
                this.stacks[index] = null;
            }
            return split;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int index) {
            // 关闭收束（保存）在 onContainerClosed 服务端路径统一处理，槽位内容不随关窗散落
            return null;
        }

        @Override
        public void setInventorySlotContents(int index, ItemStack stack) {
            this.stacks[index] = stack;
        }

        @Override
        public String getInventoryName() {
            return "gtit.reincarnation.gui.title";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return this.stackLimit;
        }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUseableByPlayer(EntityPlayer who) {
            // 会话绑定打开者（GUI 生命周期由 openGui/closeScreen 托管）
            return who == ReincarnationContainer.this.player;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int index, ItemStack stack) {
            // 纯 GUI 会话背包不接任何自动化，一律拒绝插入
            return false;
        }
    }

    // ==================== 会话语义（逐函数自旧 ReincarnationCycleGui 移植） ====================

    /**
     * 外壳消耗（旧 :790 consumeHull；D2 加数量参数）：一次吸收 {@code amount} 件 →
     * 进度 +amount → 满 {@value ReincarnationLayout#HULL_TARGET} 解锁列 → 保存。
     * 只计 accepted（放入量已被容量截断，进度不越 16）。
     * <p>
     * v1.8.8a：本方法同时负责清掉 {@code HullSlot#putStack} 暂持在槽内的吸收量
     * （成功路径首行清槽只留计数；校验失败路径清槽后原样退回，避免槽内引用与
     * 玩家背包实例混叠）。
     */
    private void consumeHull(int column, ItemStack consumed) {
        if (isReadonly() || !isColumnLocked(column) || !ReincarnationHullMatcher.matchesHull(consumed, column)) {
            this.hullInventory.setInventorySlotContents(column, null); // 清暂持（失败路径不吸收）
            giveBack(consumed); // 原样退回
            return;
        }
        int amount = consumed.stackSize;
        this.hullInventory.setInventorySlotContents(column, null); // 槽位清空只留计数
        this.cycle.recordHullConsumption(column, amount);
        int progress = this.cycle.getHullProgress()[column];
        if (progress >= ReincarnationLayout.HULL_TARGET && !this.cycle.isColumnUnlocked(column)) {
            this.cycle.unlockColumn(column);
            String columnName = StatCollector.translateToLocal(KEY_COLUMN_PREFIX + column);
            setMessageEvent(ReincarnationLayout.MSG_COLUMN_UNLOCKED_FIRST + column);
            chat(KEY_CHAT_COLUMN_UNLOCKED, columnName, progress + "/" + ReincarnationLayout.HULL_TARGET);
        } else {
            setMessageEvent(ReincarnationLayout.MSG_HULL_CONSUMED_FIRST + column);
        }
        saveQuietly();
    }

    /**
     * 物品放入（旧 :818 placeItem）：记录 ItemRef（id+meta，忽略 NBT）。
     * <p>
     * 首件放入且状态 IDLE 时经 {@code deposit} 过渡 IDLE→DEPOSITED（确认按钮契约前置）；
     * 其余情况 {@code addPendingItem}（S4 契约：IDLE/DEPOSITED 均允许）。模型拒绝时物品原样退回。
     */
    private void placeItem(int index, ItemStack placed) {
        if (isReadonly()) {
            this.itemInventory.setInventorySlotContents(index, null);
            giveBack(placed);
            chat(KEY_CHAT_READONLY);
            return;
        }
        ReincarnationCycle.ItemRef ref = refOf(placed);
        if (ref == null) {
            this.itemInventory.setInventorySlotContents(index, null);
            giveBack(placed);
            chat(KEY_CHAT_DEPOSIT_FAILED, placed.getDisplayName());
            return;
        }
        try {
            if (this.cycle.getPendingItems()
                .isEmpty() && this.cycle.canDeposit()) {
                // IDLE → DEPOSITED（首件寄存；deposit=整体替换，单件清单语义等价 addPendingItem+过渡）
                this.cycle.deposit(Collections.singletonList(ref));
            } else {
                this.cycle.addPendingItem(ref);
            }
            this.itemSlotRefs[index] = ref;
            saveQuietly();
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 模型拒绝（EXECUTED/契约未放宽等）：槽位清空 + 物品退回
            this.itemInventory.setInventorySlotContents(index, null);
            this.itemSlotRefs[index] = null;
            giveBack(placed);
            chat(KEY_CHAT_DEPOSIT_FAILED, placed.getDisplayName());
        }
    }

    /**
     * 物品取出（旧 :854 takeItem）：withdraw（S4 契约）移除一条记录；失败时格位按 ItemRef 重建
     */
    private void takeItem(int index) {
        ReincarnationCycle.ItemRef ref = this.itemSlotRefs[index];
        this.itemSlotRefs[index] = null;
        if (ref == null) return;
        if (isReadonly()) {
            // 只读态拒绝取出：重建格位展示（记录保留）；正常路径已被 canTakeStack 前置拦截
            restoreItemSlot(index, ref);
            chat(KEY_CHAT_READONLY);
            return;
        }
        if (withdrawBridge(this.cycle, ref)) {
            saveQuietly();
        } else {
            // 记录移除失败（防御路径）：格位重建，物品不丢
            restoreItemSlot(index, ref);
            chat(KEY_CHAT_WITHDRAW_FAILED);
        }
    }

    /** 重建格位展示（ItemRef → 展示堆；registry 失效时留空） */
    private void restoreItemSlot(int index, ReincarnationCycle.ItemRef ref) {
        ItemStack display = freshStack(ref);
        if (display != null) {
            this.itemInventory.setInventorySlotContents(index, display);
            this.itemSlotRefs[index] = ref;
        }
    }

    /**
     * S4 契约直连：{@code ReincarnationCycle#withdraw(ItemRef)}（IDLE/DEPOSITED 移除一件并返回
     * true；物品不在清单返回 false；EXECUTED 抛 IllegalStateException——只读锁已由
     * canTakeStack/takeItem 前置拦截，此处兜底转 false 保持旧 withdrawBridge 的签名语义）。
     */
    private static boolean withdrawBridge(ReincarnationCycle cycle, ReincarnationCycle.ItemRef ref) {
        try {
            return cycle.withdraw(ref);
        } catch (IllegalStateException denied) {
            return false;
        }
    }

    /**
     * 确认轮回（旧 :900 doConfirm）：两步确认已过 → 回调 S4 钩子（未注入则提示）
     */
    private void doConfirm() {
        if (isReadonly()) {
            chat(KEY_CHAT_READONLY);
            return;
        }
        if (this.cycle.getCycleState() != ReincarnationCycle.CycleState.DEPOSITED || this.cycle.getPendingItems()
            .isEmpty()) {
            return;
        }
        ReincarnationConfirmHook hook = ReincarnationConfirmHook.Holder.getHook();
        if (hook == null) {
            chat(KEY_CONFIRM_TOOLTIP_NOHOOK);
            return;
        }
        // S4 契约：由钩子完成 deposit 收束 → confirmReincarnation(seed) → 演出/奖励
        hook.onConfirmRequested(this.player);
        // A4（丢更新覆写防御）：确认回调已在权威层推进 EXECUTED 并同事务落盘
        // （EXECUTED+投胎信箱+指纹），容器内存模型此刻必为陈旧 DEPOSITED——按 GUI
        // 打开同款路径立即重载，防关窗 saveQuietly 用陈旧模型全量覆写抹掉信箱/指纹
        reloadIfLicenseAdvancedQuietly();
    }

    // ==================== 解锁连掷（v1.9.0 连掷重构；会话注册表与 tick 驱动在 ReincarnationHandler） ====================

    /**
     * 解锁按钮 toggle 入口（旧 :923 tryUnlock 一次性扣币 → 连掷会话制）。
     * <p>
     * EXECUTED 只读锁与 isUnlockAvailable 首行检查保留：会话未激活时不可解锁（只读/行满/
     * 列未全解锁）则拒绝开启并沿用只读 chat 反馈；已激活会话的再按 = 立即停止（停止条件④），
     * 不受该门控影响。经 {@code enchantItem} 的 {@code scheduleServerTask} 到达，恒在服务端线程。
     */
    private void toggleUnlock(boolean shimmer, GTITItemList coinType) {
        if (ReincarnationHandler.isRollSessionActive(this.player, shimmer)) {
            ReincarnationHandler.stopRollSession(this.player, shimmer); // 停止条件④：再按同按钮立即停止
            return;
        }
        if (!isUnlockAvailable()) {
            chat(KEY_CHAT_READONLY);
            return;
        }
        ReincarnationHandler.startRollSession(this.player, shimmer, coinType, () -> rollOnce(shimmer, coinType));
    }

    /**
     * 连掷单掷（会话激活期间每服务端 tick 一次，由 {@code ReincarnationHandler.tickRollSessions}
     * 驱动）。返回 {@code false} = 会话终止。
     * <p>
     * RNG 冻结口径沿用旧 tryUnlock：{@code UNLOCK_RNG.nextInt(分母) == 0}
     * （闪烁 1/1000=0.1%，普通 1/100000=0.001%）。成功不停止会话（继续掷至停止条件命中）；
     * 失败静默（20 掷/秒不刷屏，不设消息事件码）。模型变更走权威会话内存模型 + saveQuietly，
     * 与 GUI 其余路径同源，关窗收束不丢账。
     */
    private boolean rollOnce(boolean shimmer, GTITItemList coinType) {
        if (!isUnlockAvailable()) {
            return false; // 停止条件①：无剩余可解锁目标（isUnlockAvailable 同口径），静默停止
        }
        int coinSlot = indexOfCoin(coinType);
        if (coinSlot < 0) {
            chat(KEY_CHAT_UNLOCK_NO_COIN); // 停止条件②：背包无该币种，沿用现有 no_coin 消息键
            return false;
        }
        this.player.inventory.decrStackSize(coinSlot, 1);
        boolean success = UNLOCK_RNG.nextInt(shimmer ? SHIMMER_UNLOCK_ONE_IN : NORMAL_UNLOCK_ONE_IN) == 0;
        if (success) {
            this.cycle.setUnlockedRows(this.cycle.getUnlockedRows() + 1);
            saveQuietly();
            chat(KEY_CHAT_UNLOCK_SUCCESS, this.cycle.getUnlockedRows() + "/" + ReincarnationCycle.MAX_UNLOCKED_ROWS);
            setMessageEvent(ReincarnationLayout.MSG_UNLOCK_SUCCESS);
        }
        return true;
    }

    /** 背包中该币种猫猫币首个命中格（主背包 36 格含快捷栏，实时扫描；与 Handler 水晶扫描同口径） */
    private int indexOfCoin(GTITItemList coinType) {
        ItemStack[] mainInventory = this.player.inventory.mainInventory;
        for (int slot = 0; slot < mainInventory.length; slot++) {
            if (isNekoCoin(mainInventory[slot], coinType)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * GUI 关闭（旧 :961 onCloseSession；v1.9.0 币格摘除后退币段删除）：收束保存
     * （槽位内物品已记录进模型，不退回）+ 停止该玩家全部连掷会话（停止条件③，静默）。
     */
    @Override
    public void onContainerClosed(EntityPlayer who) {
        super.onContainerClosed(who);
        if (!server) {
            return;
        }
        this.confirmArmed = false;
        ReincarnationHandler.stopRollSessions(this.player); // 停止条件③：GUI 关闭静默停止（登出清扫由 Handler 兜底）
        saveQuietly();
    }

    /**
     * GUI 打开时（旧 :770 fillItemSlotsFromCycle）：按「列优先、行内自上而下」把 pendingItems
     * 回填到已解锁格（超出的留在模型不展示；registry 名失效的记录保留、格位留空）
     */
    private void fillItemSlotsFromCycle() {
        int cursor = 0;
        java.util.List<ReincarnationCycle.ItemRef> pending = this.cycle.getPendingItems();
        for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT; column++) {
            if (!this.cycle.isColumnUnlocked(column)) continue;
            for (int row = 0; row < this.cycle.getUnlockedRows(); row++) {
                if (cursor >= pending.size()) return;
                ReincarnationCycle.ItemRef ref = pending.get(cursor++);
                ItemStack display = freshStack(ref);
                if (display == null) continue;
                int localIndex = column * ReincarnationCycle.MAX_UNLOCKED_ROWS + row;
                this.itemInventory.setInventorySlotContents(localIndex, display);
                this.itemSlotRefs[localIndex] = ref;
            }
        }
    }

    // ==================== 按钮分发（C2S 通道 = vanilla C11 enchantItem） ====================

    /**
     * 按钮分发（仅服务端被 {@code NetHandlerPlayServer.processEnchantItem} 调用）。
     * <p>
     * 确认按钮两步制：第一次点击仅服务端武装（替代旧 NekoConfirmationDialog 的二次确认语义），
     * 第二次点击执行；武装态由槽位变动（各 Slot 钩子）/关窗（onContainerClosed）重置。
     */
    @Override
    public boolean enchantItem(EntityPlayer who, int id) {
        if (!server || who != this.player) {
            return false;
        }
        if (id == ReincarnationLayout.BUTTON_CONFIRM) {
            if (!isConfirmAvailable()) {
                return false;
            }
            if (!this.confirmArmed) {
                this.confirmArmed = true; // 仅武装，下一帧经进度条推送客户端换文案
                return true;
            }
            this.confirmArmed = false;
            scheduleServerTask(this::doConfirm);
            return true;
        }
        if (id == ReincarnationLayout.BUTTON_UNLOCK_SHIMMER) {
            scheduleServerTask(() -> toggleUnlock(true, GTITItemList.ShimmeringNekoCoin));
            return true;
        }
        if (id == ReincarnationLayout.BUTTON_UNLOCK_NORMAL) {
            scheduleServerTask(() -> toggleUnlock(false, GTITItemList.NekoCoin));
            return true;
        }
        return false;
    }

    /** 槽位变动重置确认武装（仅服务端） */
    private void disarmConfirm() {
        if (server) {
            this.confirmArmed = false;
        }
    }

    // ==================== 状态同步（进度条通道，映射见 ReincarnationLayout） ====================

    /**
     * 槽位 diff 同步（super）+ 进度条推送：计数/状态/锁定/武装/消息事件码；
     * 首帧（缓存初值 -1）随 {@code addCraftingToCrafters} 内的首次 detect 全量下发
     */
    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!server) {
            return;
        }
        int[] hull = this.cycle.getHullProgress();
        for (int i = 0; i < ReincarnationLayout.HULL_SLOT_COUNT; i++) {
            if (this.lastHull[i] != hull[i]) {
                this.lastHull[i] = hull[i];
                pushProgressBar(ReincarnationLayout.BAR_HULL_COUNT_FIRST + i, hull[i]);
            }
        }
        int state = this.cycle.getCycleState()
            .ordinal();
        if (this.lastState != state) {
            this.lastState = state;
            pushProgressBar(ReincarnationLayout.BAR_STATE, state);
        }
        int mask = ReincarnationLayout.columnUnlockMask(this.cycle.getUnlockedColumns());
        if (this.lastMask != mask) {
            this.lastMask = mask;
            pushProgressBar(ReincarnationLayout.BAR_COLUMN_UNLOCK_MASK, mask);
        }
        int rows = this.cycle.getUnlockedRows();
        if (this.lastRows != rows) {
            this.lastRows = rows;
            pushProgressBar(ReincarnationLayout.BAR_UNLOCKED_ROWS, rows);
        }
        int pending = this.cycle.getPendingItems()
            .size();
        if (this.lastPending != pending) {
            this.lastPending = pending;
            pushProgressBar(ReincarnationLayout.BAR_PENDING_COUNT, pending);
        }
        int armed = this.confirmArmed ? 1 : 0;
        if (this.lastArmed != armed) {
            this.lastArmed = armed;
            pushProgressBar(ReincarnationLayout.BAR_CONFIRM_ARMED, armed);
        }
        if (this.lastMessage != this.messageEvent) {
            this.lastMessage = this.messageEvent;
            pushProgressBar(ReincarnationLayout.BAR_MESSAGE_EVENT, this.messageEvent);
        }
        // 连掷激活位：服务端注册表实况（ReincarnationHandler 单源），变化 ≤1 tick 送达
        int rollActive = ReincarnationHandler.rollActiveBits(this.player);
        if (this.lastRollActive != rollActive) {
            this.lastRollActive = rollActive;
            pushProgressBar(ReincarnationLayout.BAR_ROLL_ACTIVE, rollActive);
        }
    }

    /** 向全部监听者推送一条进度条值（值域均已限 short 16 位，映射见 ReincarnationLayout） */
    private void pushProgressBar(int barId, int value) {
        for (Object crafter : this.crafters) {
            ((ICrafting) crafter).sendProgressBarUpdate(this, barId, value);
        }
    }

    /** 客户端：进度条到达（映射见 ReincarnationLayout；服务端不调用） */
    @Override
    public void updateProgressBar(int id, int value) {
        if (id >= ReincarnationLayout.BAR_HULL_COUNT_FIRST
            && id < ReincarnationLayout.BAR_HULL_COUNT_FIRST + ReincarnationLayout.HULL_SLOT_COUNT) {
            this.clientHull[id - ReincarnationLayout.BAR_HULL_COUNT_FIRST] = value;
            return;
        }
        if (id == ReincarnationLayout.BAR_STATE) {
            this.clientState = value;
        } else if (id == ReincarnationLayout.BAR_COLUMN_UNLOCK_MASK) {
            this.clientMask = value;
        } else if (id == ReincarnationLayout.BAR_UNLOCKED_ROWS) {
            this.clientRows = value;
        } else if (id == ReincarnationLayout.BAR_PENDING_COUNT) {
            this.clientPending = value;
        } else if (id == ReincarnationLayout.BAR_CONFIRM_ARMED) {
            this.clientArmed = value;
        } else if (id == ReincarnationLayout.BAR_MESSAGE_EVENT) {
            this.clientMessage = value;
        } else if (id == ReincarnationLayout.BAR_ROLL_ACTIVE) {
            this.clientRollActive = value;
        }
    }

    /** 会话绑定虚拟 GUI：无方块依赖，生命周期由 openGui/closeScreen 托管 */
    @Override
    public boolean canInteractWith(EntityPlayer who) {
        return true;
    }

    // ==================== 只读访问器（client 渲染层消费；sided 分发） ====================

    /** 周目状态 ordinal */
    public int getStateOrdinal() {
        return server ? this.cycle.getCycleState()
            .ordinal() : this.clientState;
    }

    /** 列 i 已累计消耗外壳数（0..16） */
    public int getColumnCount(int column) {
        return server ? this.cycle.getHullProgress()[column] : this.clientHull[column];
    }

    /** 列 i 是否锁定（未解锁） */
    public boolean isColumnLocked(int column) {
        return server ? !this.cycle.isColumnUnlocked(column) : (this.clientMask & (1 << column)) == 0;
    }

    /** 全局已解锁行数（1..3） */
    public int getUnlockedRows() {
        return server ? this.cycle.getUnlockedRows() : this.clientRows;
    }

    /** 待领取（已寄存）物品数 */
    public int getPendingCount() {
        return server ? this.cycle.getPendingItems()
            .size() : this.clientPending;
    }

    /** 确认按钮是否处于武装（二次确认）态 */
    public boolean isConfirmArmed() {
        return server ? this.confirmArmed : this.clientArmed != 0;
    }

    /**
     * 连掷会话激活位（v1.9.0 连掷重构）：bit 0 = 闪烁币会话激活，bit 1 = 普通币会话激活
     * （编码唯一单源见 {@link ReincarnationLayout#BAR_ROLL_ACTIVE}；sided：服务端读 Handler
     * 注册表实况，客户端读进度条镜像）。client 渲染层据此切换解锁按钮文案。
     */
    public int getRollActiveBits() {
        return server ? ReincarnationHandler.rollActiveBits(this.player) : this.clientRollActive;
    }

    /** 当前面板消息 lang 键（无消息返回空串；参数由渲染层从其余进度条值本地派生） */
    public String getMessageKey() {
        return messageLangKey(currentMessageEvent());
    }

    /** 当前面板消息事件码（渲染层据此反解列下标/文案形态） */
    public int getMessageEvent() {
        return currentMessageEvent();
    }

    /** 是否只读（EXECUTED = 存在未领取轮回，口径见类 javadoc） */
    public boolean isReadonly() {
        return getStateOrdinal() == STATE_EXECUTED_ORDINAL;
    }

    private int currentMessageEvent() {
        return server ? this.messageEvent : this.clientMessage;
    }

    /** 事件码 → lang 键（无消息返回空串） */
    public static String messageLangKey(int eventCode) {
        if (ReincarnationLayout.isColumnUnlockedEvent(eventCode)) {
            return KEY_CHAT_COLUMN_UNLOCKED;
        }
        if (ReincarnationLayout.isHullConsumedEvent(eventCode)) {
            return KEY_CHAT_HULL_CONSUMED;
        }
        if (eventCode == ReincarnationLayout.MSG_UNLOCK_SUCCESS) {
            return KEY_CHAT_UNLOCK_SUCCESS;
        }
        if (eventCode == ReincarnationLayout.MSG_UNLOCK_FAIL_SHIMMER
            || eventCode == ReincarnationLayout.MSG_UNLOCK_FAIL_NORMAL) {
            return KEY_CHAT_UNLOCK_FAIL;
        }
        return "";
    }

    /** 服务端：置面板消息事件码（下一 detect 推送客户端） */
    private void setMessageEvent(int eventCode) {
        this.messageEvent = eventCode;
    }

    // ==================== 可用性判定（sided 分发；口径与旧 GUI 一致） ====================

    /** 已解锁列是否全满（行解锁前置条件） */
    private boolean allColumnsUnlocked() {
        for (int i = 0; i < ReincarnationCycle.COLUMN_COUNT; i++) {
            if (isColumnLocked(i)) return false;
        }
        return true;
    }

    /** 解锁行按钮可用：非只读、行未满 3、15 列全部解锁（旧 :685 unlockAvailable；sided，客户端经进度条镜像判定按钮置灰） */
    public boolean isUnlockAvailable() {
        return !isReadonly() && getUnlockedRows() < ReincarnationCycle.MAX_UNLOCKED_ROWS && allColumnsUnlocked();
    }

    /** 确认按钮可用：DEPOSITED + 非空 + 钩子已注入（旧 :691 confirmAvailable；Holder 单机共享 JVM 双端可见） */
    public boolean isConfirmAvailable() {
        return !isReadonly() && getStateOrdinal() == STATE_DEPOSITED_ORDINAL
            && getPendingCount() > 0
            && ReincarnationConfirmHook.Holder.getHook() != null;
    }

    // ==================== 基础设施（自旧 GUI 移植） ====================

    /**
     * 保存（服务端；D1 拆分写入）：先全局许可（指纹/投胎信箱——失败抛出中断，
     * 保证「确认推进 EXECUTED 与写信箱」同事务、失败不写半份），再每存档进度
     * （WorldData，markDirty 由存档保存期落盘）。磁盘异常不阻断 GUI
     * （记录日志语义由 store 异常消息承载）。
     */
    private void saveQuietly() {
        if (this.store == null) return;
        try {
            // A4 防御：磁盘许可已推进 EXECUTED 而内存漂移 → 先重载再保存（覆盖
            // doConfirm 后重载遗漏/权威链路异步推进等残余时序，防信箱/指纹被抹）
            reloadIfLicenseAdvancedQuietly();
            this.store.save(this.cycle);
            if (this.worldData != null) {
                this.worldData.saveFrom(this.cycle);
            }
        } catch (IllegalStateException ignored) {
            // 写入失败（磁盘等）：保持内存态，下次变更重试；不向玩家刷屏
        }
    }

    /**
     * 丢更新覆写防御（A4）：保存前从磁盘新读许可，{@link ReincarnationSaveGuard}
     * 判定"磁盘已 EXECUTED 而内存漂移为非 EXECUTED"时，按 GUI 打开同款路径重载
     * 内存模型（{@code store.load} + {@code worldData.applyTo}，WorldData 为打开时
     * 捕获的同一 overworld MapStorage 单例）后返回 true。
     * <p>
     * 只用新读实例整体替换（{@code applyTo} 进度灌入为累加语义，不可对同一实例
     * 重复施加）；重载失败按正常保存处理（不放大磁盘异常）。
     *
     * @return true = 判定命中且已重载
     */
    private boolean reloadIfLicenseAdvancedQuietly() {
        if (this.store == null || this.worldData == null) {
            return false;
        }
        try {
            ReincarnationCycle disk = this.store.load(
                this.player.getUniqueID()
                    .toString());
            if (!ReincarnationSaveGuard.needsReloadBeforeSave(this.cycle, disk)) {
                return false;
            }
            this.worldData.applyTo(disk);
            this.cycle = disk;
            return true;
        } catch (RuntimeException ignored) {
            // 磁盘读失败：按无漂移处理（原保存语义不变），不阻断 GUI
            return false;
        }
    }

    /** 物品退回玩家（背包满则原地掉落；旧 :983 giveBack） */
    private void giveBack(ItemStack stack) {
        if (stack == null || !this.player.inventory.addItemStackToInventory(stack)) {
            if (stack != null) {
                this.player.dropPlayerItemWithRandomChoice(stack, false);
            }
        }
    }

    /** 服务端聊天反馈（ChatComponentTranslation：客户端本地化，双语由 lang 文件承载） */
    private void chat(String key, Object... args) {
        this.player.addChatMessage(new ChatComponentTranslation(key, args));
    }

    /** ItemRef → 展示/退回用物品堆（1 件；registry 名缺失返回 null；旧 :757 freshStack） */
    private static ItemStack freshStack(ReincarnationCycle.ItemRef ref) {
        Item item = (Item) Item.itemRegistry.getObject(ref.getId());
        return item == null ? null : new ItemStack(item, 1, ref.getMeta());
    }

    /** ItemStack → ItemRef（registry 名 + meta，忽略 NBT；名缺失返回 null；旧 :763 refOf） */
    private static ReincarnationCycle.ItemRef refOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name == null ? null : new ReincarnationCycle.ItemRef(name.toString(), stack.getItemDamage());
    }

    /** 猫猫币判定（两种猫猫币均接受；旧 :705 isNekoCoin。v1.9.0 币格摘除后暂无调用点，按任务口径保留） */
    private static boolean isNekoCoin(ItemStack stack) {
        return isNekoCoin(stack, GTITItemList.NekoCoin) || isNekoCoin(stack, GTITItemList.ShimmeringNekoCoin);
    }

    /** 指定猫猫币判定（null 安全；经 IItemContainer.isStackEqual 口径） */
    private static boolean isNekoCoin(ItemStack stack, GTITItemList coinType) {
        return stack != null && stack.getItem() != null && coinType.isStackEqual(stack);
    }

    /** 重活投递服务端主线程（任务契约：ServerTaskScheduler 跳主线程；FIFO 保证复合操作次序） */
    private void scheduleServerTask(Runnable task) {
        if (server && task != null) {
            ServerTaskScheduler.scheduleServerTask(task);
        }
    }

    // ==================== shift-click 转移（QUICK_MOVE 通道） ====================

    /**
     * shift-click 转移：玩家背包 → <b>外壳累计槽</b>（D2 新增：仅未锁定列 + 容量未满，
     * 沿列顺序一次吸收，经 {@link HullSlot#putStack} 自动进 consumeHull 任务）
     * → 物品格（逐槽 {@link Slot#isItemValid} 同套校验，列优先行内自上而下；与旧 GUI
     * 仅注册物品格 SlotGroup 的 shift 口径一致）；物品格 → 玩家背包（外壳格
     * canTakeStack=false 被 vanilla QUICK_MOVE 前置拦截；v1.9.0 币格摘除，原币格分支删除，
     * 猫猫币停留玩家背包由连掷会话直接扣取）。
     * <p>
     * 放入路径经 {@code putStack} 钩子自动进 consumeHull/placeItem 任务；取出路径在本方法内
     * 显式投递 takeItem（vanilla QUICK_MOVE 不触发 onPickupFromSlot）。
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer who, int index) {
        Slot source = getSlot(index);
        ItemStack stack = source == null ? null : source.getStack();
        if (stack == null) {
            return null;
        }
        if (source.inventory == who.inventory) {
            // 玩家背包 → 外壳累计槽（D2：仅未锁定列 + 容量未满，沿列顺序一次吸收）
            ItemStack remaining = stack.copy();
            boolean moved = false;
            for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT && remaining.stackSize > 0; column++) {
                HullSlot target = this.hullSlots[column];
                if (target == null || !target.isItemValid(remaining)) {
                    continue;
                }
                int capacity = target.getSlotStackLimit();
                if (capacity <= 0) {
                    continue;
                }
                target.putStack(remaining.splitStack(Math.min(remaining.stackSize, capacity)));
                moved = true;
            }
            // 玩家背包 → 物品格（逐槽同套校验；每格 1 件）
            for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT && remaining.stackSize > 0; column++) {
                for (int row = 0; row < ReincarnationCycle.MAX_UNLOCKED_ROWS && remaining.stackSize > 0; row++) {
                    CycleItemSlot target = this.itemSlots[column * ReincarnationCycle.MAX_UNLOCKED_ROWS + row];
                    if (target.isItemValid(remaining)) {
                        target.putStack(remaining.splitStack(1));
                        moved = true;
                    }
                }
            }
            if (moved) {
                source.putStack(remaining.stackSize > 0 ? remaining : null);
                return remaining.stackSize > 0 ? remaining : null;
            }
            return null;
        }
        if (source instanceof CycleItemSlot) {
            // 物品格 → 玩家背包：显式走 withdraw 链（vanilla QUICK_MOVE 不触发 onPickupFromSlot）
            if (server) {
                disarmConfirm();
                int localIndex = ((CycleItemSlot) source).localIndex;
                scheduleServerTask(() -> takeItem(localIndex));
            }
            ItemStack moved = source.getStack()
                .copy();
            source.putStack(null);
            if (!who.inventory.addItemStackToInventory(moved)) {
                who.dropPlayerItemWithRandomChoice(moved, false); // 背包满则原地掉落（giveBack 同语义）
            }
            return null;
        }
        return null;
    }
}
