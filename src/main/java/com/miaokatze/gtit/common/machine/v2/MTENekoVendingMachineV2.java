package com.miaokatze.gtit.common.machine.v2;

import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cubefury.vendingmachine.blocks.MTEVendingUplinkHatch;
import com.cubefury.vendingmachine.blocks.VendingMachineBlocks;
import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.IAlignmentProvider;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.miaokatze.gtit.register.TextureManager;
import com.miaokatze.gtit.trade.v2.NekoTradeExecutor;
import com.miaokatze.gtit.trade.v2.NekoTradeResult;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.render.ISBRInventoryContext;
import gregtech.api.render.RenderOverlay;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 猫猫售货机 V2 - 独立化版本
 * <p>
 * 继承 GT5U 的 MTEEnhancedMultiBlockBase，完全脱离 VendingMachine mod。
 * 交易逻辑委托给 {@link NekoTradeExecutor}。
 * <p>
 * 多方块结构：2宽 x 2高 x 1深，控制器在右下角
 * <ul>
 * <li>上层：cc（两个外壳方块）</li>
 * <li>下层：c~（左外壳 + 右控制器）</li>
 * </ul>
 * <p>
 * 本版本复刻 V1 的槽机制：使用内置的 {@link ItemStackHandler} 分别作为输入槽
 * ({@link #inputItems}) 和输出槽 ({@link #outputItems})，并可选连接一个
 * {@link MTEVendingUplinkHatch} 用于 ME 网络交互。
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTENekoVendingMachineV2 extends MTEEnhancedMultiBlockBase<MTENekoVendingMachineV2>
    implements ISurvivalConstructable, ICasingTextureProvider {

    /** 结构定义的唯一标识符，用于在 StructureLib 中索引特定的结构片段 */
    private static final String STRUCTURE_PIECE_MAIN = "main";

    /**
     * 猫猫机 2x2x1 多方块结构定义
     * <p>
     * StructureLib shape 格式：外层数组=深度(前后), 内层数组=高度(上到下), 字符=宽度(左到右)
     * <ul>
     * <li>{@code c}: 机器外壳或 ME Vending Uplink Hatch 位置</li>
     * <li>{@code ~}: 控制器位置（位于下层右侧）</li>
     * </ul>
     * <p>
     * 使用 {@link gregtech.api.util.HatchElementBuilder#hatchClass} 限定只接受
     * {@link MTEVendingUplinkHatch}，使 NEI 多方块结构预览只显示 Uplink Hatch
     * 物品候选，不再误显示通用 Input Bus / Input Hatch。普通位置仍可放置任意
     * {@code VendingMachineBlocks.casingBlock} 作为外壳。
     */
    private static final IStructureDefinition<MTENekoVendingMachineV2> NEKO_STRUCTURE_DEFINITION = IStructureDefinition
        .<MTENekoVendingMachineV2>builder()
        .addShape(STRUCTURE_PIECE_MAIN, new String[][] { { "cc", "c~" } })
        .addElement(
            'c',
            buildHatchAdder(MTENekoVendingMachineV2.class).adder(MTENekoVendingMachineV2::addUplinkHatch)
                .hatchClass(MTEVendingUplinkHatch.class)
                .casingIndex(VendingMachineBlocks.casingBlock.getTextureIndex(0))
                .hint(1)
                .buildAndChain(VendingMachineBlocks.casingBlock, 0))
        .build();

    // === 材质常量 ===

    /** 侧面材质（非正面朝向的方块面使用） */
    private static final ITexture[] FACING_SIDE = new ITexture[] { TextureFactory.of(TextureManager.NEKOVM_CASING) };

    /** 正面材质（非激活状态） */
    private static final ITexture[] FACING_FRONT = new ITexture[] {
        TextureFactory.of(TextureManager.NEKOVM_FRONT_OFF) };

    /** 正面材质（激活状态，含发光层） */
    private static final ITexture[] FACING_ACTIVE = new ITexture[] { TextureFactory.of(TextureManager.NEKOVM_FRONT_ON),
        TextureFactory.builder()
            .addIcon(TextureManager.NEKOVM_FRONT_ON_GLOW)
            .glow()
            .build() };

    // === 覆盖层偏移 ===

    /**
     * 猫猫机覆盖层偏移（3个非控制器方块）
     * <p>
     * 控制器在右下角(row=1,col=1)，偏移 = (blockCol - 1, blockRow - 1)
     * 行从上到下排列，所以上方偏移为负值
     * <ul>
     * <li>索引0: 右上 (0, -1)</li>
     * <li>索引1: 左上 (-1, -1)</li>
     * <li>索引2: 左下 (-1, 0)</li>
     * </ul>
     */
    private static final int[] NEKO_VM_X = new int[] { 0, -1, -1 };
    private static final int[] NEKO_VM_Y = new int[] { -1, -1, 0 };

    /** 覆盖层渲染 ticket 列表，用于管理和清除已注册的覆盖层 */
    protected final List<RenderOverlay.OverlayTicket> overlayTickets = new ArrayList<>();

    // === 内置输入/输出槽 ===

    /** 输入槽数量，与 V1 内置输入槽一致 */
    public static final int INPUT_SLOTS = 8;

    /** 输出槽数量，与 V1 内置输出槽一致 */
    public static final int OUTPUT_SLOTS = 100;

    /** 内置输入物品槽 */
    public final ItemStackHandler inputItems = new ItemStackHandler(INPUT_SLOTS);

    /** 内置输出物品槽 */
    public final ItemStackHandler outputItems = new ItemStackHandler(OUTPUT_SLOTS);

    /** 输出缓冲队列，物品先入队再逐 tick 投放到 outputItems，复刻 V1 的串行掉落节奏 */
    private final Queue<ItemStack> outputBuffer = new ConcurrentLinkedQueue<>();

    /** 标记有新缓冲输出待投放，触发立即投放而非等待延迟 */
    private boolean newBufferedOutputs = false;

    /** 距上次投放的 tick 计数，用于控制投放节奏 */
    private int ticksSinceOutput = 0;

    /** 可选的 ME Vending Uplink Hatch，结构检查时设置，上限 1 个 */
    private MTEVendingUplinkHatch uplinkHatch = null;

    // === 构造器 ===

    /**
     * 注册构造器（由 MachineLoader 调用）
     *
     * @param aID           机器 ID
     * @param aName         机器名称
     * @param aNameRegional 区域化名称
     */
    public MTENekoVendingMachineV2(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    /**
     * 实例构造器（由 newMetaEntity 调用，创建运行时实例）
     *
     * @param aName 机器名称
     */
    public MTENekoVendingMachineV2(String aName) {
        super(aName);
    }

    // === 结构定义 ===

    @Override
    public IStructureDefinition<MTENekoVendingMachineV2> getStructureDefinition() {
        return NEKO_STRUCTURE_DEFINITION;
    }

    /**
     * 创造模式结构构建
     * <p>
     * 偏移量 (1, 1, 0) 对应结构定义中 ~ 的位置（下层右侧）
     */
    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, 1, 0);
    }

    /**
     * 生存模式结构构建
     * <p>
     * 偏移量 (1, 1, 0) 对应结构定义中 ~ 的位置（下层右侧）
     */
    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 1, 1, 0, elementBudget, env, false, true);
    }

    // === 结构检查（3参数版，含 List<StructureError> errors）===

    /**
     * 结构完整性检测方法
     * <p>
     * 每次检查前重置 uplinkHatch 引用，然后调用 StructureLib 进行空间扫描。
     * <p>
     * 完全模仿 V1 的实现思路：调用带 {@code errors} 列表的 5 参数版本
     * {@code checkPiece(piece, x, y, z, errors)}，让 StructureChecker 自动把
     * 具体的错误位置/描述写入 errors。基类 {@link gregtech.api.metatileentity.implementations.MTEMultiBlockBase#checkStructure}
     * 会根据 errors 是否为空来设置 {@code mMachine}，子类无需再手动赋值或塞默认错误。
     * <p>
     * 旧实现调用 4 参数版（不传 errors），导致具体错误信息丢失，且手动塞入
     * {@code UNKNOWN_STRUCTURE_ERROR} 会覆盖 StructureChecker 本应生成的定位信息。
     *
     * @param aBaseMetaTileEntity 机器所在的 TileEntity
     * @param aStack              玩家手持的物品（可用于动态结构调整，此处未使用）
     * @param errors              结构错误列表，由 StructureChecker 自动填充
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        uplinkHatch = null;
        // 调用带 errors 列表的 5 参数版本，由 StructureChecker 自动写入错误
        checkPiece(STRUCTURE_PIECE_MAIN, 1, 1, 0, errors);
    }

    /**
     * Uplink Hatch 添加器
     * <p>
     * 复刻 V1 的 {@code addUplinkHatchNeko} 逻辑。每个猫猫机最多连接 1 个
     * {@link MTEVendingUplinkHatch}，可替换任意 casing 方块。
     *
     * @param aBaseMetaTileEntity 待检查的 TileEntity
     * @param aBaseCasingIndex    外壳材质索引
     * @return 成功添加返回 true，否则 false
     */
    private boolean addUplinkHatch(IGregTechTileEntity aBaseMetaTileEntity, int aBaseCasingIndex) {
        if (uplinkHatch != null) {
            return false;
        }
        if (aBaseMetaTileEntity == null) {
            return false;
        }
        IMetaTileEntity aMetaTileEntity = aBaseMetaTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null || !(aMetaTileEntity instanceof MTEVendingUplinkHatch)) {
            return false;
        }
        MTEVendingUplinkHatch hatch = (MTEVendingUplinkHatch) aMetaTileEntity;
        hatch.updateTexture(aBaseCasingIndex);
        hatch.updateCraftingIcon(hatch.getMachineCraftingIcon());
        uplinkHatch = hatch;
        return true;
    }

    // === 多线程安全构造 ===

    /**
     * 创建新的 MetaTileEntity 实例
     * <p>
     * 每次机器放置时调用，返回一个新的实例以隔离不同机器间的状态。
     */
    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTENekoVendingMachineV2(mName);
    }

    // === 交易委托 ===

    /**
     * 检查交易是否可执行（不实际执行）
     * <p>
     * 创建 {@link InternalInputSlotAccessor} 适配器，委托
     * {@link NekoTradeExecutor#checkTrade} 进行交易检查。
     *
     * @param playerId   玩家 UUID
     * @param groupId    交易组 UUID
     * @param tradeIndex 交易在组内的索引
     * @return 交易结果（SUCCESS 或对应的失败状态）
     */
    public NekoTradeResult checkTrade(UUID playerId, UUID groupId, int tradeIndex) {
        return NekoTradeExecutor.INSTANCE.checkTrade(playerId, groupId, tradeIndex, new InternalInputSlotAccessor());
    }

    /**
     * 执行交易
     * <p>
     * 创建 {@link InternalInputSlotAccessor} 和 {@link InternalOutputSlotAccessor} 适配器，
     * 委托 {@link NekoTradeExecutor#executeTrade} 执行交易。
     *
     * @param playerId   玩家 UUID
     * @param groupId    交易组 UUID
     * @param tradeIndex 交易在组内的索引
     * @return 交易结果（SUCCESS 或对应的失败状态）
     */
    public NekoTradeResult processTrade(UUID playerId, UUID groupId, int tradeIndex) {
        // 机器未成型时不允许交易（防止物品被扣但 onPostTick 不投放）
        if (!mMachine) {
            return NekoTradeResult.fail(NekoTradeResult.Status.NOT_FORMED);
        }
        return NekoTradeExecutor.INSTANCE.executeTrade(
            playerId,
            groupId,
            tradeIndex,
            new InternalInputSlotAccessor(),
            new InternalOutputSlotAccessor());
    }

    /**
     * 将单个物品栈弹入出货槽（加入缓冲队列）
     * <p>
     * 物品加入 outputBuffer 队列，由 onPostTick 逐 tick 投放到 outputItems 的第一个空槽。
     * 仅当 outputItems 完全无空槽时返回原栈作为溢出（调用方可掉落到地面）。
     *
     * @param stack 要弹入的物品栈
     * @return 未入队的剩余物品（仅在 outputItems 完全满时返回），null 表示已入队
     */
    public ItemStack dispenseItemStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return null;
        }
        // 检查是否还有空槽，完全满时返回溢出
        if (getFirstEmptyOutputSlot() == -1) {
            return stack.copy();
        }
        this.outputBuffer.add(stack.copy());
        this.newBufferedOutputs = true;
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
        return null;
    }

    /**
     * 将物品列表加入输出缓冲队列
     * <p>
     * 复刻 V1 的 dispenseItemStacks：物品先入队，由 onPostTick 逐 tick 投放到 outputItems。
     * 每次投放写入第一个空槽（不合并），确保 NekoFallingItemSlotFactory 的掉落动画必然触发。
     *
     * @param itemStacks 要弹入的物品列表
     */
    public void dispenseItemStacks(java.util.List<ItemStack> itemStacks) {
        if (itemStacks == null || itemStacks.isEmpty()) return;
        for (ItemStack stack : itemStacks) {
            if (stack != null && stack.stackSize > 0) {
                // 溢出检查：与单个版 dispenseItemStack 一致，完全无空槽时掉落到机器旁
                if (getFirstEmptyOutputSlot() == -1) {
                    // 输出槽完全满，剩余物品掉落到机器旁
                    dropItemsNearMachine(stack);
                } else {
                    this.outputBuffer.add(stack.copy());
                }
            }
        }
        this.newBufferedOutputs = true;
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    /**
     * 逐 tick 投放缓冲队列中的物品到 outputItems
     * <p>
     * 复刻 V1 的 dispenseItems：每次调用最多投放一个物品到第一个空槽，
     * 投放节奏由 getDispensingDelay() 控制（队列越长延迟越短）。
     */
    private void dispenseItems() {
        if (!mMachine) return;
        if (this.newBufferedOutputs
            || (!this.outputBuffer.isEmpty() && this.ticksSinceOutput % getDispensingDelay() == 0)) {
            dispenseFirstNonNullItem();
            this.ticksSinceOutput = 0;
        }
        this.ticksSinceOutput = this.newBufferedOutputs ? 0 : this.ticksSinceOutput + 1;
        this.newBufferedOutputs = false;
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    /**
     * 获取投放延迟（tick）
     * <p>
     * 队列越长延迟越短（对数加速），复刻 V1 的 getDispensingDelay。
     */
    private int getDispensingDelay() {
        int baseDelay = 10;
        int queueSize = outputBuffer.size();
        if (queueSize <= 1) return baseDelay;
        double acceleration = Math.log(queueSize);
        if (acceleration < 1) return baseDelay;
        return (int) (baseDelay / acceleration);
    }

    /**
     * 从队列中取出第一个有效物品，投放到第一个空槽
     */
    private void dispenseFirstNonNullItem() {
        ItemStack dispensable = getNextDispensable();
        if (dispensable != null) {
            int targetSlot = getFirstEmptyOutputSlot();
            if (targetSlot != -1) {
                outputIntoSlot(dispensable, targetSlot);
                this.outputBuffer.poll();
            } else {
                // 槽满时掉落到机器旁并 poll 出队列，防止物品永久卡队列
                dropItemsNearMachine(dispensable);
                this.outputBuffer.poll();
            }
        }
    }

    /**
     * 获取队列中第一个有效物品（不弹出）
     */
    private ItemStack getNextDispensable() {
        while (!this.outputBuffer.isEmpty()) {
            ItemStack next = this.outputBuffer.peek();
            if (next != null && next.stackSize > 0) {
                return next;
            }
            this.outputBuffer.poll();
        }
        return null;
    }

    /**
     * 获取第一个空输出槽索引
     *
     * @return 空槽索引，无空槽返回 -1
     */
    private int getFirstEmptyOutputSlot() {
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            if (outputItems.getStackInSlot(i) == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取输出槽的空槽数量
     * <p>
     * 供 GUI 层在投放前判断是否有空间（考虑 outputBuffer 堆积）。
     *
     * @return 空槽数量
     */
    public int getOutputEmptySlotCount() {
        int count = 0;
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            if (outputItems.getStackInSlot(i) == null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取输出缓冲队列的当前大小
     * <p>
     * 供 GUI 层在投放前判断队列是否已堆积过多未投放物品。
     *
     * @return 队列大小
     */
    public int getOutputBufferSize() {
        return this.outputBuffer.size();
    }

    /**
     * 将物品写入指定输出槽
     * <p>
     * 使用 copy() 确保新对象引用，触发 ModularUI 的完整同步链。
     */
    private void outputIntoSlot(ItemStack stack, int slotIndex) {
        ItemStack output = stack.copy();
        output.stackSize = stack.stackSize;
        stack.stackSize = 0;
        outputItems.setStackInSlot(slotIndex, output);
    }

    /**
     * 将物品掉落到机器旁（世界实体）
     * <p>
     * 当输出槽满且队列无法投放时，将物品作为 EntityItem 掉落到机器控制器方块上方，
     * 防止物品永久卡在 outputBuffer 队列中。
     *
     * @param stack 要掉落的物品栈
     */
    private void dropItemsNearMachine(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        if (baseMetaTileEntity == null) return;
        // 在控制器方块上方中心位置生成物品实体
        net.minecraft.world.World world = baseMetaTileEntity.getWorld();
        int x = baseMetaTileEntity.getXCoord();
        int y = baseMetaTileEntity.getYCoord() + 1; // 上方一格
        int z = baseMetaTileEntity.getZCoord();
        net.minecraft.entity.item.EntityItem entityItem = new net.minecraft.entity.item.EntityItem(
            world,
            x + 0.5D,
            y + 0.5D,
            z + 0.5D,
            stack.copy());
        // 给一个轻微的向上初速度，模拟"弹出"效果
        entityItem.motionX = (world.rand.nextDouble() - 0.5D) * 0.2D;
        entityItem.motionY = 0.2D;
        entityItem.motionZ = (world.rand.nextDouble() - 0.5D) * 0.2D;
        world.spawnEntityInWorld(entityItem);
    }

    // === ICasingTextureProvider ===

    /**
     * 获取外壳材质纹理
     * <p>
     * 供仓室等组件获取对应的材质索引。
     *
     * @return 侧面材质 FACING_SIDE[0]
     */
    @Override
    public ITexture getCasingTexture() {
        return FACING_SIDE[0];
    }

    /**
     * 物品栏渲染覆盖
     * <p>
     * 默认实现会调用 getTexture(igte, side, WEST, -1, true, false)，
     * 由于 aActive=true 且 side==facing(WEST)，会返回 FACING_ACTIVE（含发光层）。
     * 发光层在物品栏渲染上下文中可能因 OpenGL 状态不匹配导致正面材质不可见。
     * <p>
     * 此覆盖方法直接使用 FACING_FRONT（无发光层）渲染正面，FACING_SIDE 渲染其他面，
     * 确保物品栏中正确显示猫猫机正面材质。
     *
     * @param ctx 库存渲染上下文
     * @return true 表示已自定义渲染，跳过默认的 renderNormalInventoryMetaTileEntity
     */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean renderInInventory(ISBRInventoryContext ctx) {
        // 物品栏中正面朝向 WEST（左面），使用非激活正面材质（无发光层）
        ctx.renderNegativeYFacing(FACING_SIDE); // DOWN
        ctx.renderPositiveYFacing(FACING_SIDE); // UP
        ctx.renderNegativeZFacing(FACING_SIDE); // NORTH
        ctx.renderPositiveZFacing(FACING_SIDE); // SOUTH
        ctx.renderNegativeXFacing(FACING_FRONT); // WEST（物品栏中的正面）
        ctx.renderPositiveXFacing(FACING_SIDE); // EAST
        return true;
    }

    // === Tooltip ===

    /**
     * 创建多方块机器的 Tooltip 信息
     * <p>
     * 描述机器类型、功能说明、结构组成和各部件位置。
     */
    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType("Neko Vending Machine V2")
            .addInfo("喵~ 猫猫售货机V2，完全独立版！")
            .beginStructureBlock(2, 2, 1, false)
            .addController("Bottom Right")
            .addOtherStructurePart("Vending Machine Casing", "Any casing block")
            .addOtherStructurePart("ME Vending Uplink Hatch", "Optional, any casing")
            .toolTipFinisher();
    }

    // === NBT 持久化 ===

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setTag("inputItems", inputItems.serializeNBT());
        aNBT.setTag("outputItems", outputItems.serializeNBT());
        // 保存输出缓冲队列
        if (!outputBuffer.isEmpty()) {
            net.minecraft.nbt.NBTTagList bufferList = new net.minecraft.nbt.NBTTagList();
            for (ItemStack stack : outputBuffer) {
                if (stack != null && stack.stackSize > 0) {
                    net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
                    stack.writeToNBT(tag);
                    bufferList.appendTag(tag);
                }
            }
            aNBT.setTag("outputBuffer", bufferList);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("inputItems")) {
            inputItems.deserializeNBT(aNBT.getCompoundTag("inputItems"));
        }
        if (aNBT.hasKey("outputItems")) {
            outputItems.deserializeNBT(aNBT.getCompoundTag("outputItems"));
        }
        // 加载输出缓冲队列
        if (aNBT.hasKey("outputBuffer")) {
            net.minecraft.nbt.NBTTagList bufferList = aNBT.getTagList("outputBuffer", 10);
            for (int i = 0; i < bufferList.tagCount(); i++) {
                ItemStack stack = ItemStack.loadItemStackFromNBT(bufferList.getCompoundTagAt(i));
                if (stack != null && stack.stackSize > 0) {
                    outputBuffer.add(stack);
                }
            }
            if (!outputBuffer.isEmpty()) {
                newBufferedOutputs = true;
            }
        }
    }

    // === 材质 ===

    /**
     * 获取机器方块的材质
     * <p>
     * 朝向面（side==facing）根据激活状态选择正面材质：
     * <ul>
     * <li>baseMetaTileEntity==null：返回 FACING_FRONT（用于 NEI 渲染等）</li>
     * <li>激活状态：返回 FACING_ACTIVE（含发光层）</li>
     * <li>非激活状态：返回 FACING_FRONT</li>
     * </ul>
     * 非朝向面返回 FACING_SIDE（外壳材质）。
     *
     * @param aBaseMetaTileEntity 机器所在的 TileEntity
     * @param side                被渲染的面方向
     * @param facing              机器的朝向
     * @param aColorIndex         颜色索引（未使用）
     * @param aActive             是否激活
     * @param aRedstone           红石信号（未使用）
     * @return 对应面的材质数组
     */
    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aBaseMetaTileEntity == null) {
                return FACING_FRONT;
            }
            return aActive ? FACING_ACTIVE : FACING_FRONT;
        }
        return FACING_SIDE;
    }

    // === 覆盖层渲染 ===

    /**
     * 设置猫猫机的覆盖层渲染
     * <p>
     * 在客户端为 2x2x1 结构的 3 个非控制器方块设置覆盖材质。
     * 激活时使用 {@link TextureManager#NEKOVM_OVERLAY_ACTIVE}，
     * 非激活时使用 {@link TextureManager#NEKOVM_OVERLAY}。
     * 所有覆盖层均带发光效果。
     * <p>
     * 实现方式参考 GT5U 的 MTELargeTurbineBase 模式：
     * 自行维护 overlayTickets 列表，每次更新时先清除旧 tickets 再注册新的。
     */
    protected void setTextureOverlay() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null || tile.isServerSide()) {
            return;
        }

        // 复刻 V1：尊重用户关闭动画的配置，只有 isActive 且 usingAnimations() 才使用激活态材质
        boolean isActive = tile.isActive() && usingAnimations();
        IIconContainer[] nekoTextures = isActive ? TextureManager.NEKOVM_OVERLAY_ACTIVE : TextureManager.NEKOVM_OVERLAY;

        // 清除旧的覆盖层 tickets
        clearOverlay();

        // 计算覆盖层位置并设置新的 tickets
        int[] tXYZOffset = new int[3];
        ForgeDirection tDirection = getExtendedFacing().getDirection();
        ExtendedFacing facing = ExtendedFacing.of(tDirection);
        RenderOverlay overlay = RenderOverlay.getOrCreate(tile.getWorld());

        int x = tile.getXCoord();
        short y = tile.getYCoord();
        int z = tile.getZCoord();

        for (int i = 0; i < 3; i++) {
            // 将 ABC 坐标（结构相对坐标）转换为世界坐标偏移
            int[] tABCCoord = new int[] { NEKO_VM_X[i], NEKO_VM_Y[i], 0 };
            facing.getWorldOffset(tABCCoord, tXYZOffset);
            int tX = tXYZOffset[0] + x;
            int tY = tXYZOffset[1] + y;
            int tZ = tXYZOffset[2] + z;

            // 注册覆盖层 ticket，所有覆盖层均带发光效果
            overlayTickets.add(
                overlay.set(
                    x,
                    y,
                    z,
                    tX,
                    tY,
                    tZ,
                    tDirection,
                    TextureFactory.builder()
                        .addIcon(nekoTextures[i])
                        .glow()
                        .build(),
                    0));
        }
    }

    /**
     * 清除所有已注册的覆盖层 tickets
     * <p>
     * 遍历 overlayTickets 列表，对每个 ticket 调用 remove() 移除渲染，
     * 然后清空列表。在覆盖层更新或机器移除时调用。
     */
    private void clearOverlay() {
        for (RenderOverlay.OverlayTicket ticket : overlayTickets) {
            ticket.remove();
        }
        overlayTickets.clear();
    }

    // === 生命周期回调 ===

    /**
     * 后置 Tick 回调
     * <p>
     * 在客户端且机器成型时更新覆盖层渲染，确保覆盖层与机器状态同步。
     *
     * @param aBaseMetaTileEntity 机器所在的 TileEntity
     * @param aTick               当前 tick 计数
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        // 服务端：基类 MTEMultiBlockBase.onPostTick 会按 mMaxProgresstime > 0 设置 active，
        // 但猫猫机没有持续配方进度，必须按结构是否成型 (mMachine) 覆盖 active 状态，
        // 使正面材质/覆盖层与结构状态同步。该值会由 GT 同步到客户端。
        if (aBaseMetaTileEntity.isServerSide()) {
            aBaseMetaTileEntity.setActive(mMachine);
            // 逐 tick 投放缓冲队列中的物品
            if (mMachine) {
                dispenseItems();
            }
        }
        // 客户端：复刻 V1 父类逻辑，非激活/结构未形成时清除覆盖层
        if (aBaseMetaTileEntity.isClientSide()) {
            if (mMachine) {
                setTextureOverlay();
            } else {
                clearOverlay();
            }
        }
    }

    /**
     * 客户端值更新回调
     * <p>
     * 当服务器发送状态更新（如激活状态变化）时，客户端刷新覆盖层渲染。
     *
     * @param aValue 更新数据字节
     */
    @Override
    public void onValueUpdate(byte aValue) {
        super.onValueUpdate(aValue);
        // 复刻 V1：客户端收到状态更新时，根据 mMachine 决定叠加或清除覆盖层
        // 修复：原代码无 mMachine 检查，存档加载时结构未形成也会注册覆盖层导致材质混乱
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isClientSide()) {
            if (mMachine) {
                setTextureOverlay();
            } else {
                clearOverlay();
            }
        }
    }

    /**
     * 首次 Tick 回调
     * <p>
     * 复刻 V1 父类 MTEVendingMachine.java 第 706-712 行。
     * 客户端首次 tick 时同步朝向并初始化覆盖层。
     * <p>
     * 修复：原 V2 未覆盖 onFirstTick，存档加载后客户端朝向可能过时导致覆盖层位置错乱。
     *
     * @param aBaseMetaTileEntity 机器所在的 TileEntity
     */
    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        // 复刻 V1 父类：客户端首次 tick 时同步朝向并初始化覆盖层
        if (aBaseMetaTileEntity.isClientSide()) {
            StructureLibAPI.queryAlignment((IAlignmentProvider) aBaseMetaTileEntity);
            if (mMachine) {
                setTextureOverlay();
            }
        }
    }

    /**
     * 是否启用动画
     * <p>
     * 复刻 V1 MTEVendingMachine.java 第 164-167 行。
     * 基类 MTEEnhancedMultiBlockBase 未提供此方法，此处返回 true 表示默认启用动画。
     * 用于 setTextureOverlay 中决定是否使用激活态材质。
     *
     * @return true 表示启用动画（使用激活态材质）
     */
    public boolean usingAnimations() {
        return true;
    }

    /**
     * 机器移除回调
     * <p>
     * 在客户端移除机器时清除覆盖层，避免残留渲染。
     */
    @Override
    public void onRemoval() {
        super.onRemoval();
        // 客户端移除时清除覆盖层
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isClientSide()) {
            clearOverlay();
        }
    }

    // === GUI ===

    /**
     * 获取机器的 GUI 实例
     * <p>
     * 返回 {@link NekoVMGuiV2} 实例，由 GT5U 的 buildUI (final 方法) 调用。
     *
     * @return NekoVMGuiV2 实例
     */
    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new NekoVMGuiV2(this);
    }

    // === 输入/输出槽适配器 ===

    /**
     * 内置输入槽访问器
     * <p>
     * 实现 {@link NekoTradeExecutor.InputSlotAccessor} 接口，
     * 直接操作本机器的 {@link #inputItems}。
     */
    private class InternalInputSlotAccessor implements NekoTradeExecutor.InputSlotAccessor {

        @Override
        public ItemStack[] getCopyOfInputs() {
            ItemStack[] inputs = new ItemStack[INPUT_SLOTS];
            for (int i = 0; i < INPUT_SLOTS; i++) {
                ItemStack stack = inputItems.getStackInSlot(i);
                inputs[i] = stack == null ? null : stack.copy();
            }
            return inputs;
        }

        @Override
        public void setInputs(ItemStack[] inputs) {
            for (int i = 0; i < INPUT_SLOTS && i < inputs.length; i++) {
                inputItems.setStackInSlot(i, inputs[i]);
            }
        }
    }

    /**
     * 内置输出槽访问器
     * <p>
     * 实现 {@link NekoTradeExecutor.OutputSlotAccessor} 接口，
     * 直接操作本机器的 {@link #outputItems}。
     * <p>
     * V2 完整版：hasSpaceFor 仅检查空槽（不合并），insertItem 加入缓冲队列，
     * 由 onPostTick 逐 tick 投放，确保 NekoFallingItemSlotFactory 的掉落动画必然触发。
     */
    private class InternalOutputSlotAccessor implements NekoTradeExecutor.OutputSlotAccessor {

        @Override
        public boolean hasSpaceFor(ItemStack stack) {
            if (stack == null) return true;
            // 可用空槽 = 当前空槽数 - outputBuffer 队列已占用的虚拟槽位数
            // 队列堆积时，空槽数会逐步被消耗（onPostTick 逐 tick 投放），
            // hasSpaceFor 必须预留队列占位，避免超卖
            int emptySlots = 0;
            for (int i = 0; i < OUTPUT_SLOTS; i++) {
                if (outputItems.getStackInSlot(i) == null) {
                    emptySlots++;
                }
            }
            int available = emptySlots - outputBuffer.size();
            return available > 0;
        }

        @Override
        public void insertItem(ItemStack stack) {
            if (stack == null) return;
            // 加入缓冲队列，由 onPostTick 逐 tick 投放到空槽
            outputBuffer.add(stack.copy());
            newBufferedOutputs = true;
            // 标记脏数据，确保队列变化持久化到存档
            if (getBaseMetaTileEntity() != null) {
                getBaseMetaTileEntity().markDirty();
            }
        }

        @Override
        public void rollback(int count) {
            // 从队列尾部移除 count 个物品（回滚本轮已插入的）
            // ConcurrentLinkedQueue 无 removeLast，用迭代移除最后一个
            for (int i = 0; i < count && !outputBuffer.isEmpty(); i++) {
                java.util.Iterator<ItemStack> it = outputBuffer.iterator();
                ItemStack last = null;
                while (it.hasNext()) {
                    last = it.next();
                }
                if (last != null) {
                    it = outputBuffer.iterator();
                    while (it.hasNext()) {
                        if (it.next() == last) {
                            it.remove();
                            break;
                        }
                    }
                }
            }
            if (getBaseMetaTileEntity() != null) {
                getBaseMetaTileEntity().markDirty();
            }
        }
    }

    // === GT5U 基础接口重写 ===

    /**
     * 获取物品栏总大小（输入槽 + 输出槽）
     * <p>
     * 重写以暴露内置的 inputItems 和 outputItems，使 GT5U 的物品栏操作（如玩家取物、
     * 自动抽出）能正确识别所有槽位。复刻 V1 MTEVendingMachine.java 的实现。
     */
    @Override
    public int getSizeInventory() {
        return INPUT_SLOTS + OUTPUT_SLOTS;
    }

    /**
     * 获取指定槽位的物品栈
     * <p>
     * 索引 0~INPUT_SLOTS-1 对应输入槽，INPUT_SLOTS~INPUT_SLOTS+OUTPUT_SLOTS-1 对应输出槽。
     * 复刻 V1 MTEVendingMachine.java 的实现。
     */
    @Override
    public ItemStack getStackInSlot(int index) {
        if (index < INPUT_SLOTS) {
            return inputItems.getStackInSlot(index);
        }
        if (index < INPUT_SLOTS + OUTPUT_SLOTS) {
            return outputItems.getStackInSlot(index - INPUT_SLOTS);
        }
        return null;
    }

    /**
     * 检查槽位是否有效
     * <p>
     * 所有输入槽和输出槽均为有效槽位。复刻 V1 MTEVendingMachine.java 的实现。
     */
    @Override
    public boolean isValidSlot(int aIndex) {
        return aIndex < INPUT_SLOTS + OUTPUT_SLOTS;
    }

    /**
     * 是否允许在指定槽位掉落物品
     * <p>
     * 所有槽位均允许掉落物品（玩家破坏机器时）。复刻 V1 MTEVendingMachine.java 的实现。
     */
    @Override
    public boolean shouldDropItemAt(int index) {
        return true;
    }

    /**
     * 设置指定槽位的物品内容
     * <p>
     * 根据索引路由到 inputItems 或 outputItems，并在设置后调用 markDirty
     * 确保数据变更被持久化。复刻 V1 MTEVendingMachine.java 的实现。
     */
    @Override
    public void setInventorySlotContents(int aIndex, ItemStack aStack) {
        if (aIndex < INPUT_SLOTS) {
            inputItems.setStackInSlot(aIndex, aStack);
        } else if (aIndex < INPUT_SLOTS + OUTPUT_SLOTS) {
            outputItems.setStackInSlot(aIndex - INPUT_SLOTS, aStack);
        }
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }
}
