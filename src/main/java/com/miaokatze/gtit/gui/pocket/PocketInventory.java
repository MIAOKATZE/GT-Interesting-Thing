package com.miaokatze.gtit.gui.pocket;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.miaokatze.gtit.common.items.pocket.PocketCellBindings;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceStore;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;

/**
 * 一次 GUI 会话的口袋内容（内存 {@code ItemStack[]} ↔ 物品 NBT）。
 * <p>
 * <b>三条硬口径</b>（R22 + R53c，不是偏好）：
 * <ol>
 * <li><b>绝不用 MUI2 自带的那个「按 NBT 现取现解」handler</b>（R22）——其上游源码里自带
 * 一句 {@code "this doesn't work"} 的 TODO 注释（原因：它持有的 container 与真实槽里的不是同一对象），
 * 且每次读格都现场反序列化。这里用内存数组
 * （{@link ItemStackHandler} 的 {@code ItemStack[]} 形态），仓内同口径先例
 * {@code reincarnation/gui/ReincarnationContainer.java:128-139} 的 {@code SessionInventory}。</li>
 * <li><b>NBT 只在打开时读一次</b>（{@link #readFrom(NBTTagCompound)}），<b>写只发生在关屏</b>
 * （{@code NekoPocketContainer.onModularContainerClosed} → {@link #writeTo(NBTTagCompound)}）。</li>
 * <li><b>脏标记才序列化</b>（{@link #isDirty()}）：禁止每 tick {@code setTagCompound}，
 * 否则 vanilla {@code Container.detectAndSendChanges} 会拿整份 NBT 做相等比较并在变化时重发整枚口袋
 * （R53c 的包放大面）。</li>
 * </ol>
 * <p>
 * 本对象<b>双端各持一份</b>（面板树双端同构构建）。128 格的<b>内容</b>由 vanilla 的
 * {@code Packet103SetSlot} → {@code Slot.putStack} 喂给客户端那份 handler，
 * 因此客户端只负责显示；序列化只从服务端那份发生（关屏钩子按 mixin 只跑在 {@code EntityPlayerMP}）。
 * <p>
 * 纯数据件：不持 {@code EntityPlayer}、不持 {@code World}，也不做任何搬运决策
 * （流体搬运在 {@link NekoPocketLeftColumn}，通道与蒸馏归 S6/S7）。
 */
public final class PocketInventory {

    /** 中栏格数 = 8 列 × 16 行 = 128（§14.3；单源取 {@link PocketConstants#GHOST_ITEM_SLOT_LIMIT}）。 */
    public static final int STORAGE_SLOTS = PocketConstants.GHOST_ITEM_SLOT_LIMIT;
    /** 左栏流体交互格数 = 上 4 + 下 4，两排同权（R39a）。 */
    public static final int FLUID_INTERACTION_SLOTS = 8;
    /** 右栏蒸馏输入格数 = 3 行 × 4 列（§14.3 覆盖计划 §6 的「3 个槽」旧口径）。 */
    public static final int DISTILL_INPUT_SLOTS = 12;
    /** 右下绑定格数（需求 5，R43a 的瞬时入口）。 */
    public static final int BIND_SLOTS = 1;

    /** 脏标记：只有内容真的变过才序列化（R53c 第 1 条）。 */
    private boolean dirty;

    private final ItemStackHandler storage = newSlotGroup(STORAGE_SLOTS);
    private final ItemStackHandler fluidInteraction = newSlotGroup(FLUID_INTERACTION_SLOTS);
    private final ItemStackHandler distillInput = newSlotGroup(DISTILL_INPUT_SLOTS);
    private final ItemStackHandler bindSlot = newSlotGroup(BIND_SLOTS);

    /** 流体条内容（内存 {@code FluidStack}；读写只经 {@link #barTank}，不让 {@code getFluid()} 去摸 NBT）。 */
    private FluidStack barFluid;
    private final FluidStackTank barTank = new FluidStackTank(() -> barFluid, fluid -> {
        this.barFluid = fluid == null || fluid.amount <= 0 ? null : fluid;
        this.dirty = true;
    }, PocketConstants.FLUID_BAR_CAPACITY_ML);

    private PocketEssenceStore essence;
    private PocketCellBindings bindings;
    private PocketFilterConfig filters;

    private PocketInventory() {
        this.essence = PocketEssenceStore.readFrom(new NBTTagCompound());
        this.bindings = PocketCellBindings.readFrom(new NBTTagCompound());
        this.filters = PocketFilterConfig.readFrom(new NBTTagCompound());
    }

    /**
     * 打开会话：<b>整个面板生命周期内只调用一次</b>（R53c 第 2 条）。
     *
     * @param root 口袋物品的 NBT 根；{@code null}（新物品）即全空会话
     */
    public static PocketInventory readFrom(NBTTagCompound root) {
        final PocketInventory inventory = new PocketInventory();
        if (root == null) {
            return inventory;
        }
        loadGroup(root, PocketConstants.ITEM_CONTENTS, inventory.storage);
        loadGroup(root, PocketConstants.FLUID_INTERACTION_SLOTS, inventory.fluidInteraction);
        loadGroup(root, PocketConstants.DISTILL_INPUT_SLOTS, inventory.distillInput);
        loadGroup(root, PocketConstants.BIND_SLOT, inventory.bindSlot);
        if (root.hasKey(PocketConstants.FLUID_BAR)) {
            inventory.barFluid = FluidStack.loadFluidStackFromNBT(root.getCompoundTag(PocketConstants.FLUID_BAR));
        }
        inventory.essence = PocketEssenceStore.readFrom(root);
        inventory.bindings = PocketCellBindings.readFrom(root);
        inventory.filters = PocketFilterConfig.readFrom(root);
        // 读档过程本身不算"内容变了"（handler 反序列化会回调脏标记）
        inventory.dirty = false;
        return inventory;
    }

    /**
     * 关屏写档（<b>仅服务端</b>被调用；见 {@code NekoPocketContainer.onModularContainerClosed}）。
     * <p>
     * 空区一律 {@code removeTag} 而不是写空 compound：口袋会跟着玩家到处走，留空壳档会让
     * {@code detectAndSendChanges} 的 NBT 比较与存档体积都白付一遍。
     */
    public void writeTo(NBTTagCompound root) {
        if (root == null) {
            return;
        }
        saveGroup(root, PocketConstants.ITEM_CONTENTS, storage);
        saveGroup(root, PocketConstants.FLUID_INTERACTION_SLOTS, fluidInteraction);
        saveGroup(root, PocketConstants.DISTILL_INPUT_SLOTS, distillInput);
        saveGroup(root, PocketConstants.BIND_SLOT, bindSlot);
        if (barFluid == null || barFluid.amount <= 0) {
            root.removeTag(PocketConstants.FLUID_BAR);
        } else {
            final NBTTagCompound bar = new NBTTagCompound();
            barFluid.writeToNBT(bar);
            root.setTag(PocketConstants.FLUID_BAR, bar);
        }
        essence.writeTo(root);
        bindings.writeTo(root);
        filters.writeTo(root);
    }

    private static void loadGroup(NBTTagCompound root, String key, ItemStackHandler handler) {
        if (!root.hasKey(key)) {
            return;
        }
        final NBTTagCompound group = root.getCompoundTag(key);
        if (group != null) {
            handler.deserializeNBT(group);
        }
    }

    private static void saveGroup(NBTTagCompound root, String key, ItemStackHandler handler) {
        // 只在"真的有货"时落档；空区一律 removeTag（不依赖 handler 自己的 NBT 内部键名形状）
        if (!hasAnyStack(handler)) {
            root.removeTag(key);
            return;
        }
        final NBTTagCompound group = handler.serializeNBT();
        if (group != null) {
            root.setTag(key, group);
        }
    }

    private static boolean hasAnyStack(ItemStackHandler handler) {
        for (int index = 0; index < handler.getSlots(); index++) {
            if (handler.getStackInSlot(index) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 带脏标记回调的槽组（{@code ItemStackHandler} 的 {@code onContentsChanged} 是唯一能同时覆盖
     * 「玩家点击」「{@code putStack} 同步」「程序内 insert/extract」三条写入路径的钩子）。
     */
    private ItemStackHandler newSlotGroup(final int size) {
        return new ItemStackHandler(size) {

            @Override
            protected void onContentsChanged(int slot) {
                PocketInventory.this.dirty = true;
            }
        };
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void markClean() {
        this.dirty = false;
    }

    /** 中栏 128 格（行主序 0..127，与 {@code SlotGroupWidget} 矩阵的字符序天然一致）。 */
    public ItemStackHandler storage() {
        return storage;
    }

    /** 左栏 8 个同权流体交互格。 */
    public ItemStackHandler fluidInteraction() {
        return fluidInteraction;
    }

    /** 右栏 12 个蒸馏输入格（R44c 的入口拒容器在 {@link PocketSlots} 的槽过滤里）。 */
    public ItemStackHandler distillInput() {
        return distillInput;
    }

    /** 右下绑定格（1 格，瞬时入口）。 */
    public ItemStackHandler bindSlot() {
        return bindSlot;
    }

    /** 流体条（{@code FluidStackTank}：内存 FluidStack + 只报真实容量）。 */
    public FluidStackTank barTank() {
        return barTank;
    }

    public PocketEssenceStore essence() {
        return essence;
    }

    public PocketCellBindings bindings() {
        return bindings;
    }

    public PocketFilterConfig filters() {
        return filters;
    }

    /**
     * 源质存储换实例。
     * <p>
     * ★D 批的蒸馏入账<b>不</b>走这里：{@code PocketDistillDriver} 拿的是 {@code essence()} 返回的
     * 同一实例，按 {@code canAcceptAll → putAll} 就地提交（换实例会造成"表被替换后旧引用仍被
     * 面板/通道持有着"的两处真相）。本方法保留给"整表导入/导出"一类外部操作，当前零调用方。
     */
    public void replaceEssence(PocketEssenceStore store) {
        this.essence = store == null ? PocketEssenceStore.readFrom(new NBTTagCompound()) : store;
        this.dirty = true;
    }

    /** 绑定表换实例（S6 的绑定/解绑动作写完后放回；{@code null} 视为空表）。 */
    public void replaceBindings(PocketCellBindings table) {
        this.bindings = table == null ? PocketCellBindings.readFrom(new NBTTagCompound()) : table;
        this.dirty = true;
    }

    /** ghost 配置换实例（S5 的就地转换写入；{@code null} 视为空表）。 */
    public void replaceFilters(PocketFilterConfig config) {
        this.filters = config == null ? PocketFilterConfig.readFrom(new NBTTagCompound()) : config;
        this.dirty = true;
    }

    /** 中栏指定格当前内容（可能为 {@code null}）。 */
    public ItemStack storageStack(int index) {
        return storage.getStackInSlot(index);
    }

    // ------------------------------------------------------------------ S6/S7 的落点出口
    //
    // 拉取模式（需求 4）与"要素栏取出→晶化源质"（需求 2）都要往口袋里放东西，而放东西的
    // 三条纪律都只能在这一处实现：ghost 格不是落点（R38 第 2 条）、先合堆再占空槽、
    // 装不下就回报装不下（绝不凭空造件也绝不吃件）。

    /**
     * 中栏某一格是否已被就地转成 ghost 配置格。
     * <p>
     * 判据只读服务端的 {@link PocketFilterConfig}（{@code Kind.ITEM} 的槽索引集合），
     * <b>不</b>读 widget 状态 —— widget 双端各一份，读它就是把显示层当真相。
     */
    public boolean isGhostItemSlot(int index) {
        return filters.at(PocketFilterConfig.Kind.ITEM, index) != null;
    }

    /**
     * 把一件物品尽力安置进中栏（先合并同类、再占空槽；ghost 格跳过）。
     *
     * @return 实际放下的个数（{@code 0} = 无处可放；调用方据此发 {@code TARGET_FULL} 并回滚源侧）
     */
    public int depositIntoStorage(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return 0;
        }
        int left = stack.stackSize;
        for (int index = 0; index < storage.getSlots() && left > 0; index++) {
            if (isGhostItemSlot(index)) {
                continue;
            }
            final ItemStack attempt = stack.copy();
            attempt.stackSize = left;
            final ItemStack rest = storage.insertItem(index, attempt, false);
            left = rest == null ? 0 : rest.stackSize;
        }
        final int moved = stack.stackSize - left;
        if (moved > 0) {
            dirty = true;
        }
        return moved;
    }

    /** 流体条还能收这一份多少 mB（条内已有别的流体 ⇒ 0）。 */
    public int fluidBarRoom(FluidStack probe) {
        if (probe == null || probe.amount <= 0) {
            return 0;
        }
        return barRoom(
            barTank.getCapacity(),
            barFluid == null || barFluid.amount <= 0 ? 0 : barFluid.amount,
            barFluid == null || barFluid.amount <= 0 || barFluid.getFluid() == probe.getFluid());
    }

    /**
     * 流体条空间的纯算术（<b>不碰任何 {@code Fluid} 实例</b>）：
     * 异种流体 ⇒ 0（条子只装一种），同种/空条 ⇒ 容量减现有量。
     * <p>
     * 单独成函数并由零依赖套件直接驱动的理由：Forge 的 {@code Fluid}/{@code FluidRegistry}
     * 在纯 JVM 里连类初始化都过不去（实测 {@code ExceptionInInitializerError}），
     * 而"抽取前先算准能收多少"正是流体支唯一会静默吞流体的判据点（R45b）；
     * {@code FluidStackTank.fill} 自身的截断属 Forge 代码，列为实机项。
     */
    public static int barRoom(int capacity, int currentAmount, boolean compatible) {
        if (!compatible || capacity <= 0) {
            return 0;
        }
        return Math.max(0, capacity - Math.max(0, currentAmount));
    }

    /** 往流体条灌入（{@code FluidStackTank.fill} 自身会拒收别的流体）；返回实际接收 mB。 */
    public int depositFluidIntoBar(FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) {
            return 0;
        }
        final int moved = barTank.fill(fluid, true);
        if (moved > 0) {
            dirty = true;
        }
        return moved;
    }
}
