package com.miaokatze.gtit.gui.pocket;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.utils.fluid.FluidStackTank;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.miaokatze.gtit.common.items.pocket.PocketCellBindings;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketEssenceStore;
import com.miaokatze.gtit.common.items.pocket.PocketFilterConfig;
import com.miaokatze.gtit.main.GTInterestingThing;

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
 * 本对象<b>双端各持一份</b>（面板树双端同构构建）。中栏 135 格的<b>内容</b>由 vanilla 的
 * {@code Packet103SetSlot} → {@code Slot.putStack} 喂给客户端那份 handler，
 * 因此客户端只负责显示；序列化只从服务端那份发生（关屏钩子按 mixin 只跑在 {@code EntityPlayerMP}）。
 * <p>
 * <b>形状变更与旧档兼容</b>：中栏 128→150（R75）→<b>135</b>（R80①，★方向相反：<b>收缩</b>）、
 * 流体 1 tank→{@link #FLUID_TANK_COUNT} tank。读侧<b>格数永远由构造期决定</b>（{@link #loadGroup}
 * 忽略档里的 {@code Size} ⇒ "只增不减"那条老话在收缩场景下的正确表述是
 * <b>"handler 绝不跟着档缩小，也绝不跟着档变大"</b>），且旧单 tank 内容落到 0 号（{@link #loadTanks}），
 * 写侧一律新形状。
 * <p>
 * ★<b>R80① 收缩兼容的三个后果（本轮新增，逐条有回归用例）</b>：
 * ① 150 格时代写的档，其 135…149 号条目<b>槽号越界</b> ⇒ ② 越界条目<b>丢弃 + 一次性 WARN</b>
 * （报出条数与现有格数；既不静默丢件，也不像上游 {@code deserializeNBT} 那样把 handler 缩成 150 格
 * 再去点越界槽号炸容器）⇒ ③ 0…134 号条目<b>原样落位</b>（同一批槽号在收缩前后指的是同一格，
 * 不重排、不搬移），玩家侧表现为"最后 15 格的东西不见了 + 日志一条 WARN"，
 * <b>不是</b>面板打不开、也<b>不是</b>整档清零。
 * <p>
 * 纯数据件：不持 {@code EntityPlayer}、不持 {@code World}，也不做任何搬运决策
 * （流体搬运在 {@link PocketSlots}，通道与蒸馏归 S6/S7）。
 */
public final class PocketInventory {

    /** 中栏格数 = 9 列 × 15 行 = 135（R80①；单源取 {@link PocketConstants#GHOST_ITEM_SLOT_LIMIT}）。 */
    public static final int STORAGE_SLOTS = PocketConstants.GHOST_ITEM_SLOT_LIMIT;
    /**
     * 流体交互格数 = {@link PocketConstants#FLUID_TANK_TOTAL} 个 tank ×
     * {@link PocketConstants#FLUID_INTERACTION_PER_COLUMN} 格 = <b>36</b>（R78②：3 组 × 6 列 × 进/出）。
     * <p>
     * ★ handler 索引与 tank 的对应关系住在 {@link #tankOfInteractionSlot(int)}（唯一映射点）：
     * 矩阵按组产出索引，一组内"先铺满 6 个上格、再铺 6 个下格"⇒
     * {@code 0…5} 是第 1 组的进格、{@code 6…11} 是第 1 组的出格、{@code 12…17} 第 2 组进格…
     * 旧 12 格档读进新 handler 的前 12 格<b>恰好还是同一批 tank</b>（见该方法的兼容说明）。
     */
    public static final int FLUID_INTERACTION_SLOTS = PocketConstants.FLUID_INTERACTION_TOTAL;
    /** 蒸馏输入格数 = 2 行 × 6 列（R75 换排布不换格数；§14.3 覆盖计划 §6 的「3 个槽」旧口径）。 */
    public static final int DISTILL_INPUT_SLOTS = 12;
    /** 绑定格数（需求 5，R43a 的瞬时入口；R75 后落在底部带）。 */
    public static final int BIND_SLOTS = 1;
    /**
     * 独立流体 tank 数（= 组数 × 每组列数 = {@code 3 × 6 = 18}，R78②；
     * 单源同 {@link PocketConstants#FLUID_TANK_TOTAL}，也是 {@code Kind.FLUID} 的 ghost 索引空间）。
     */
    public static final int FLUID_TANK_COUNT = PocketConstants.FLUID_TANK_TOTAL;
    /**
     * 每组流体侧的交互格数（= 该组的 tank 数 × 每 tank 格数 = {@code 6 × 2 = 12}）。
     * <p>
     * ★这一层是 R78 新增的"组"维度：旧口径（1 组）下它等于全部交互格数，
     * 所以 {@link #tankOfInteractionSlot(int)} 的"取模"读法在旧档上与新读法<b>逐字同解</b>。
     */
    public static final int FLUID_INTERACTION_PER_GROUP = PocketConstants.FLUID_COLUMN_COUNT
        * PocketConstants.FLUID_INTERACTION_PER_COLUMN;

    /**
     * {@code ItemStackHandler} 自己的落档形状键（{@code Items} / {@code Slot} / {@code Count} / {@code Size}）。
     * <p>
     * ★<b>不是本 mod 的 NBT 键名</b>，而是上游 handler 的内部形状；列在这里只有一个理由：
     * {@link #loadGroup} 必须<b>绕开</b> {@code handler.deserializeNBT}（见该方法），而要绕开就得自己
     * 读这一层。形状一旦被上游改动，{@code NekoPocketModelTest#storage_group_shape_roundtrip_pins_library_keys}
     * 会立刻红（它用 handler 自己的 {@code serializeNBT} 产出输入，再喂回 {@link #readFrom}），
     * 而不是留一个"越界键被静默吞掉"的哑洞。
     */
    private static final String LIB_ITEMS = "Items";
    private static final String LIB_SLOT = "Slot";
    private static final String LIB_COUNT = "Count";
    /** NBT 的 compound / int / list tag id（与 {@code PocketCellBindings} 同一口径的字面量）。 */
    private static final int LIB_TAG_COMPOUND = 10;
    private static final int LIB_TAG_INT = 3;
    private static final int LIB_TAG_LIST = 9;

    /** 脏标记：只有内容真的变过才序列化（R53c 第 1 条）。 */
    private boolean dirty;

    private final ItemStackHandler storage = newSlotGroup(STORAGE_SLOTS);
    private final ItemStackHandler fluidInteraction = newSlotGroup(FLUID_INTERACTION_SLOTS);
    private final ItemStackHandler distillInput = newSlotGroup(DISTILL_INPUT_SLOTS);
    private final ItemStackHandler bindSlot = newSlotGroup(BIND_SLOTS);

    /**
     * {@link #FLUID_TANK_COUNT} 个<b>互相独立</b>的流体 tank（R75①：每列一个，各自 16,000,000 mB）。
     * <p>
     * 每个 tank 是「内存 {@code FluidStack} + {@link FluidStackTank}」的一对：
     * 数组下标 = tank 号 = {@code Kind.FLUID} 的 ghost 槽号 = 流体列号，三者同一个数，
     * 不再有"条子只有一格"的特例。
     */
    private final FluidStack[] tankFluid = new FluidStack[FLUID_TANK_COUNT];
    private final FluidStackTank[] tanks = new FluidStackTank[FLUID_TANK_COUNT];

    private PocketEssenceStore essence;
    private PocketCellBindings bindings;
    private PocketFilterConfig filters;

    private PocketInventory() {
        for (int index = 0; index < FLUID_TANK_COUNT; index++) {
            final int tank = index;
            tanks[index] = new FluidStackTank(() -> tankFluid[tank], fluid -> {
                this.tankFluid[tank] = fluid == null || fluid.amount <= 0 ? null : fluid;
                this.dirty = true;
            }, PocketConstants.FLUID_BAR_CAPACITY_ML);
        }
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
        loadGroup(root, PocketConstants.ITEM_CONTENTS, inventory.storage, "中栏");
        loadGroup(root, PocketConstants.FLUID_INTERACTION_SLOTS, inventory.fluidInteraction, "流体交互格");
        loadGroup(root, PocketConstants.DISTILL_INPUT_SLOTS, inventory.distillInput, "蒸馏输入");
        loadGroup(root, PocketConstants.BIND_SLOT, inventory.bindSlot, "绑定格");
        inventory.loadTanks(root);
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
     * <p>
     * ★写档<b>一律按新形状</b>（R75 的存档兼容口径）：中栏写出 135 格的 {@code Size}，
     * 流体写出 {@link PocketConstants#FLUID_BAR_TANK} 编号的列表；读侧的旧形状兼容只在
     * {@link #readFrom} 那一边。
     */
    public void writeTo(NBTTagCompound root) {
        if (root == null) {
            return;
        }
        saveGroup(root, PocketConstants.ITEM_CONTENTS, storage);
        saveGroup(root, PocketConstants.FLUID_INTERACTION_SLOTS, fluidInteraction);
        saveGroup(root, PocketConstants.DISTILL_INPUT_SLOTS, distillInput);
        saveGroup(root, PocketConstants.BIND_SLOT, bindSlot);
        saveTanks(root);
        essence.writeTo(root);
        bindings.writeTo(root);
        filters.writeTo(root);
    }

    /**
     * 读一个槽组。<b>不用</b> {@code handler.deserializeNBT}，理由是硬性的（不是风格）：
     * <p>
     * 上游那份实现开头就 {@code setSize(nbt.getInteger("Size"))}，而 {@code setSize} 会把整个
     * {@code stacks} 列表<b>换成一个新数组</b>（字节码实证：{@code Arrays.fill} + {@code Arrays.asList}
     * 后 {@code putfield}）⇒ 拿旧档（{@code Size=128}）读进 135 格的 handler，会把 handler <b>缩成 128 格</b>，
     * 而 Container 那边 150 个 {@code ModularSlot} 仍会去点 128…149 号（★R80 后同理：旧档 135…149 号会点到只有 135 格的 handler） ⇒
     * {@code validateSlotIndex} 抛越界，玩家侧表现为"点后面几格没反应 / 面板炸"。
     * 本方法因此：① 忽略 {@code Size}（格数永远由构造期决定 ⇒ <b>只增不减</b>）；
     * ② 越出当前格数的条目<b>丢弃并一次性 WARN</b>（旧实现是静默跳过 = 丢件无痕）。
     */
    private static void loadGroup(NBTTagCompound root, String key, ItemStackHandler handler, String label) {
        if (!root.hasKey(key)) {
            return;
        }
        final NBTTagCompound group = root.getCompoundTag(key);
        if (group == null) {
            return;
        }
        final NBTTagList items = group.getTagList(LIB_ITEMS, LIB_TAG_COMPOUND);
        int dropped = 0;
        for (int i = 0; i < items.tagCount(); i++) {
            final NBTTagCompound entry = items.getCompoundTagAt(i);
            final int slot = entry.getInteger(LIB_SLOT);
            if (slot < 0 || slot >= handler.getSlots()) {
                dropped++;
                continue;
            }
            final ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
            if (stack == null) {
                continue;
            }
            if (entry.hasKey(LIB_COUNT, LIB_TAG_INT)) {
                stack.stackSize = entry.getInteger(LIB_COUNT);
            }
            handler.setStackInSlot(slot, stack);
        }
        if (dropped > 0) {
            warnOutOfRangeOnce(label, key, handler.getSlots(), dropped);
        }
    }

    /**
     * "越界槽号被丢弃"的一次性 WARN（R75：不许静默丢件）。
     * <p>
     * ★R80① 将闩从"整进程一次"改为"<b>每个槽组键一次</b>"：收缩场景（150 → 135）下同一枚口袋
     * 一次读档就可能让 {@code contents}、{@code interactionSlots} 两个区各自丢条目，
     * 整进程只报一次就等于第二个区<b>静默丢件</b>（用户那句"不许静默丢件"不许）。
     * 仍然不会成为日志洪水：键集合是固定的四个区 + 流体 tank，面板反复开关也只各报一次。
     */
    private static void warnOutOfRangeOnce(String label, String key, int slots, int dropped) {
        if (!outOfRangeWarnedKeys.add(key)) {
            return;
        }
        GTInterestingThing.LOG.warn(
            "[pocket] 存档里 {}（键 {}）有 {} 条槽号越出当前形状（现有 {} 格），已丢弃这些条目" + "（面板形状变更后的旧/外来档；本条按区只报一次）",
            label,
            key,
            dropped,
            slots);
    }

    /** 越界槽号 WARN 的"每个区一次"闩（★R80①；用 LinkedHashSet 保首次出现的顺序，日志可读）。 */
    private static final java.util.Set<String> outOfRangeWarnedKeys = new java.util.LinkedHashSet<>();

    /**
     * 读 {@link PocketConstants#FLUID_BAR}：两代形状都在这里分流（见该键的 javadoc）。
     * <p>
     * 旧档的单 compound ⇒ <b>整份落到 0 号 tank</b>；新档的列表 ⇒ 按 {@link PocketConstants#FLUID_BAR_TANK}
     * 归位。缺 tank 键按 0 读（与旧档同义）并计入一次性 WARN；tank 号越界的条目丢弃（同一个 WARN）。
     */
    private void loadTanks(NBTTagCompound root) {
        if (!root.hasKey(PocketConstants.FLUID_BAR)) {
            return;
        }
        if (root.hasKey(PocketConstants.FLUID_BAR, LIB_TAG_LIST)) {
            final NBTTagList list = root.getTagList(PocketConstants.FLUID_BAR, LIB_TAG_COMPOUND);
            int dropped = 0;
            for (int i = 0; i < list.tagCount(); i++) {
                final NBTTagCompound entry = list.getCompoundTagAt(i);
                int tank = 0;
                if (entry.hasKey(PocketConstants.FLUID_BAR_TANK, LIB_TAG_INT)) {
                    tank = entry.getInteger(PocketConstants.FLUID_BAR_TANK);
                } else {
                    dropped++;
                }
                if (tank < 0 || tank >= FLUID_TANK_COUNT) {
                    dropped++;
                    continue;
                }
                final FluidStack fluid = FluidStack.loadFluidStackFromNBT(entry);
                if (fluid != null && fluid.amount > 0) {
                    tankFluid[tank] = fluid;
                }
            }
            if (dropped > 0) {
                GTInterestingThing.LOG
                    .warn("[pocket] 流体档里有 {} 条缺 tank 号或越出 {} 个 tank 的范围（已按 0 号 tank 或丢弃处理）", dropped, FLUID_TANK_COUNT);
            }
            return;
        }
        if (root.hasKey(PocketConstants.FLUID_BAR, LIB_TAG_COMPOUND)) {
            final FluidStack legacy = FluidStack.loadFluidStackFromNBT(root.getCompoundTag(PocketConstants.FLUID_BAR));
            if (legacy != null && legacy.amount > 0) {
                tankFluid[0] = legacy;
            }
        }
    }

    /** 写档：只写非空 tank，每条自带 tank 号；全空即 {@code removeTag}。 */
    private void saveTanks(NBTTagCompound root) {
        final NBTTagList list = new NBTTagList();
        for (int tank = 0; tank < FLUID_TANK_COUNT; tank++) {
            final FluidStack fluid = tankFluid[tank];
            if (fluid == null || fluid.amount <= 0) {
                continue;
            }
            final NBTTagCompound entry = new NBTTagCompound();
            fluid.writeToNBT(entry);
            entry.setInteger(PocketConstants.FLUID_BAR_TANK, tank);
            list.appendTag(entry);
        }
        if (list.tagCount() == 0) {
            root.removeTag(PocketConstants.FLUID_BAR);
            return;
        }
        root.setTag(PocketConstants.FLUID_BAR, list);
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

    /** 中栏 135 格（行主序 0..134，与 {@code SlotGroupWidget} 矩阵的字符序天然一致）。 */
    public ItemStackHandler storage() {
        return storage;
    }

    /** 流体列的 12 个同权交互格（6 列 × 输入/输出；tank 号见 {@link #tankOfInteractionSlot(int)}）。 */
    public ItemStackHandler fluidInteraction() {
        return fluidInteraction;
    }

    /** 右栏 12 个蒸馏输入格（R44c 的入口拒容器在 {@link PocketSlots} 的槽过滤里）。 */
    public ItemStackHandler distillInput() {
        return distillInput;
    }

    /** 底部带绑定格（1 格，瞬时入口）。 */
    public ItemStackHandler bindSlot() {
        return bindSlot;
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

    /**
     * 某个流体交互格属于哪个 tank（<b>唯一</b>映射点，R78② 的"组"维度收在这一个函数里）。
     * <p>
     * 矩阵按"组"顺序产出索引，一组内先铺完 6 个上格再铺 6 个下格 ⇒
     * 
     * <pre>
     *   组号   = index / FLUID_INTERACTION_PER_GROUP      （每组 12 格）
     *   组内列 = index % FLUID_COLUMN_COUNT               （0…5）
     *   tank   = 组号 × FLUID_COLUMN_COUNT + 组内列        （0…17）
     * </pre>
     * 
     * ★<b>旧档同解</b>：组数=1 时本式退化为 {@code index % 6}，与 R75 那版取模逐字一致 ⇒
     * 12 格老档的进/出格仍然落在同一批 tank，不需要任何迁移代码。
     * <p>
     * 越界入参原样返回（调用方是槽号，不该越界；真越界了就让上层的数组访问炸出来，
     * 而不是静默映射到 0 号 tank 去动别人的液体）。
     */
    public static int tankOfInteractionSlot(int interactionIndex) {
        final int group = interactionIndex / FLUID_INTERACTION_PER_GROUP;
        final int columnInGroup = interactionIndex % PocketConstants.FLUID_COLUMN_COUNT;
        return group * PocketConstants.FLUID_COLUMN_COUNT + columnInGroup;
    }

    /** 交互格属于哪一组（0…{@link PocketConstants#FLUID_GROUP_COUNT}−1；GUI 的分组渲染与 tooltip 用）。 */
    public static int groupOfInteractionSlot(int interactionIndex) {
        return interactionIndex / FLUID_INTERACTION_PER_GROUP;
    }

    /**
     * 某一组内该交互格是"上格（进）"还是"下格（出）"（★<b>不</b>改变 R39a 的双用语义，
     * 只描述它画在流体槽的哪一侧，供 tooltip 选 {@code legend.input} / {@code legend.output}）。
     */
    public static boolean isLowerInteractionRow(int interactionIndex) {
        return interactionIndex % FLUID_INTERACTION_PER_GROUP >= PocketConstants.FLUID_COLUMN_COUNT;
    }

    /** tank 号是否合法（GUI 与通道侧共用的这一道界）。 */
    public static boolean isValidTank(int tank) {
        return tank >= 0 && tank < FLUID_TANK_COUNT;
    }

    /**
     * 第 {@code tank} 号流体 tank（{@code FluidStackTank}：内存 FluidStack + 只报真实容量）。
     * <p>
     * ★GUI 侧的 {@code FluidSlotSyncHandler} 直接挂它 ⇒ 6 个槽各有一根同步通道，
     * 上游那份 handler 只在<b>内容与缓存不等</b>时才发更新（{@code needsSync} 走
     * {@code FluidStack#isFluidEqual} + 数量比较，实证自 dev jar 字节码），
     * 不会把 {@code FluidStack} 的 NBT 塞进每 tick 包（R75 §3 的顾虑点）。
     */
    public FluidStackTank tankAt(int tank) {
        return tanks[tank];
    }

    /** tank 总数（GUI 装配循环用它，别处不得内联 6）。 */
    public static int tankCount() {
        return FLUID_TANK_COUNT;
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

    /**
     * 第 {@code tank} 号流体槽还能收这一份多少 mB（槽内已有别的流体 ⇒ 0；tank 号非法 ⇒ 0）。
     * <p>
     * ★拉取模式下的落点就是"该流体列自己那一格"：ghost 声明的 {@code slotIndex} 即 tank 号，
     * 所以十八个 tank 各拉各的（R78②：3 组 × 6 列），不会像旧单条那样"第一格满了后面全满"。
     */
    public int fluidBarRoom(int tank, FluidStack probe) {
        if (probe == null || probe.amount <= 0 || !isValidTank(tank)) {
            return 0;
        }
        final FluidStack current = tankFluid[tank];
        final int currentAmount = current == null || current.amount <= 0 ? 0 : current.amount;
        final boolean compatible = currentAmount == 0 || current.getFluid() == probe.getFluid();
        return barRoom(PocketConstants.FLUID_BAR_CAPACITY_ML, currentAmount, compatible);
    }

    /**
     * 流体槽空间的纯算术（<b>不碰任何 {@code Fluid} 实例</b>）：
     * 异种流体 ⇒ 0（一个 tank 只装一种），同种/空槽 ⇒ 容量减现有量。
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

    /** 往第 {@code tank} 号流体槽灌入（{@code FluidStackTank.fill} 自身会拒收别的流体）；返回实际接收 mB。 */
    public int depositFluidIntoBar(int tank, FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0 || !isValidTank(tank)) {
            return 0;
        }
        final int moved = tanks[tank].fill(fluid, true);
        if (moved > 0) {
            dirty = true;
        }
        return moved;
    }
}
