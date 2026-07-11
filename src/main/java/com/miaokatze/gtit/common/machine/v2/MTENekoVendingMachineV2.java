package com.miaokatze.gtit.common.machine.v2;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.IAlignmentProvider;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.miaokatze.gtit.register.TextureManager;
import com.miaokatze.gtit.trade.v2.NekoTradeExecutor;
import com.miaokatze.gtit.trade.v2.NekoTradeResult;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.render.ISBRInventoryContext;
import gregtech.api.render.RenderOverlay;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
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
 * v1.6.2 功能填充：完整实现交易逻辑、材质渲染、覆盖层渲染和 GUI 接入。
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
     * <li>{@code c}: 机器外壳或仓室位置（支持钨钢方块及各类仓室）</li>
     * <li>{@code ~}: 控制器位置（位于下层右侧）</li>
     * </ul>
     * 使用 {@code buildHatchAdder} 模式（与 MTEMultiTestMachine 一致），
     * 支持 InputHatch/OutputHatch/InputBus/OutputBus/Maintenance/Energy 仓室，
     * 非仓室位置检查是否为钨钢机器方块（GregTechAPI.sBlockCasings4:0）。
     */
    private static final IStructureDefinition<MTENekoVendingMachineV2> NEKO_STRUCTURE_DEFINITION = IStructureDefinition
        .<MTENekoVendingMachineV2>builder()
        .addShape(STRUCTURE_PIECE_MAIN, new String[][] { { "cc", "c~" } })
        .addElement(
            'c',
            buildHatchAdder(MTENekoVendingMachineV2.class)
                // 声明该位置至少可以是以下仓室之一，或者是普通的外壳方块
                .atLeast(InputHatch, OutputHatch, InputBus, OutputBus, Maintenance, Energy)
                // 指定将识别到的仓室添加到机器列表的方法引用（继承自 MTEMultiBlockBase）
                .adder(MTENekoVendingMachineV2::addToMachineList)
                // 在游戏内使用软锤查看结构时，该位置的提示点编号
                .hint(1)
                // 设置外壳方块的材质纹理索引（钨钢机器方块）
                .casingIndex(GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 0))
                // 如果不是仓室，则检查是否为指定的外壳方块，并在匹配成功时触发 onCasingAdded 计数
                .buildAndChain(
                    onElementPass(
                        MTENekoVendingMachineV2::onCasingAdded,
                        StructureUtility.ofBlock(GregTechAPI.sBlockCasings4, 0))))
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

    /** 记录结构中成功匹配的外壳数量，用于完整性校验（2x2x1 结构除去控制器后需至少 3 个外壳） */
    private int mCasingAmount = 0;

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
     * 重置外壳计数器并调用 StructureLib 进行空间扫描。
     * 只有当外壳数量达标（>= 3）时，机器才会被视为"已成型"。
     *
     * @param aBaseMetaTileEntity 机器所在的 TileEntity
     * @param aStack              玩家手持的物品（可用于动态结构调整，此处未使用）
     * @param errors              结构错误列表，检测失败时添加错误信息
     */
    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mCasingAmount = 0;
        boolean structureValid = checkPiece(STRUCTURE_PIECE_MAIN, 1, 1, 0) && mCasingAmount >= 3;
        if (!structureValid && errors.isEmpty()) {
            errors.add(gregtech.api.structure.error.StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
        }
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
     * 创建 {@link BusInputSlotAccessor} 适配器，委托
     * {@link NekoTradeExecutor#checkTrade} 进行交易检查。
     *
     * @param playerId   玩家 UUID
     * @param groupId    交易组 UUID
     * @param tradeIndex 交易在组内的索引
     * @return 交易结果（SUCCESS 或对应的失败状态）
     */
    public NekoTradeResult checkTrade(UUID playerId, UUID groupId, int tradeIndex) {
        BusInputSlotAccessor accessor = new BusInputSlotAccessor();
        return NekoTradeExecutor.INSTANCE.checkTrade(playerId, groupId, tradeIndex, accessor);
    }

    /**
     * 执行交易
     * <p>
     * 创建 {@link BusInputSlotAccessor} 和 {@link BusOutputSlotAccessor} 适配器，
     * 委托 {@link NekoTradeExecutor#executeTrade} 执行交易。
     *
     * @param playerId   玩家 UUID
     * @param groupId    交易组 UUID
     * @param tradeIndex 交易在组内的索引
     * @return 交易结果（SUCCESS 或对应的失败状态）
     */
    public NekoTradeResult processTrade(UUID playerId, UUID groupId, int tradeIndex) {
        BusInputSlotAccessor inputAccessor = new BusInputSlotAccessor();
        BusOutputSlotAccessor outputAccessor = new BusOutputSlotAccessor();
        return NekoTradeExecutor.INSTANCE.executeTrade(playerId, groupId, tradeIndex, inputAccessor, outputAccessor);
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
            .addOtherStructurePart("Input Bus", "Any casing, for trade inputs")
            .addOtherStructurePart("Output Bus", "Any casing, for trade outputs")
            .toolTipFinisher();
    }

    // === 结构扫描回调 ===

    /**
     * 结构扫描回调：每当 StructureLib 匹配到一个外壳方块时调用
     */
    private void onCasingAdded() {
        mCasingAmount++;
    }

    // === NBT 持久化 ===

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        // TODO: v1.6.2 如需持久化交易组绑定等状态可在此扩展
        super.saveNBTData(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        // TODO: v1.6.2 如需持久化交易组绑定等状态可在此扩展
        super.loadNBTData(aNBT);
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
     * 输入总线槽位访问器
     * <p>
     * 实现 {@link NekoTradeExecutor.InputSlotAccessor} 接口，
     * 将 GT5U 的 mInputBusses（输入总线列表）适配为交易执行器所需的输入槽抽象。
     * <p>
     * 工作原理：
     * <ul>
     * <li>getCopyOfInputs()：遍历所有输入总线的所有槽位，收集非空物品的副本，
     * 同时记录每个物品来自哪个总线、哪个槽位（用于后续回写）</li>
     * <li>setInputs()：按记录的映射关系，将扣减后的物品数组回写到对应的总线槽位。
     * 如果某物品为 null，则清空对应槽位</li>
     * </ul>
     * 这种设计使得 NekoTradeExecutor 可以在不了解 GT5U 总线结构的情况下操作输入物品。
     */
    private class BusInputSlotAccessor implements NekoTradeExecutor.InputSlotAccessor {

        /** 记录每个收集的物品来自哪个 bus 的索引（与 getCopyOfInputs 返回的数组一一对应） */
        private int[] busIndices;

        /** 记录每个收集的物品来自哪个 slot 的索引（与 getCopyOfInputs 返回的数组一一对应） */
        private int[] slotIndices;

        /**
         * 获取所有输入总线中非空槽位物品的副本
         * <p>
         * 遍历 mInputBusses 中的每个有效总线，收集其 mInventory 中所有非空槽位的物品副本。
         * 同时记录 busIndex 和 slotIndex 映射，供 setInputs 回写使用。
         *
         * @return 物品数组副本
         */
        @Override
        public ItemStack[] getCopyOfInputs() {
            List<ItemStack> inputs = new ArrayList<>();
            List<Integer> busIdx = new ArrayList<>();
            List<Integer> slotIdx = new ArrayList<>();

            // 遍历所有输入总线
            for (int b = 0; b < mInputBusses.size(); b++) {
                MTEHatchInputBus bus = mInputBusses.get(b);
                // 跳过无效总线（已被移除或未初始化）
                if (bus == null || bus.getBaseMetaTileEntity() == null) {
                    continue;
                }
                // 遍历总线内的所有槽位
                ItemStack[] inv = bus.mInventory;
                for (int s = 0; s < inv.length; s++) {
                    if (inv[s] != null) {
                        inputs.add(inv[s].copy());
                        busIdx.add(b);
                        slotIdx.add(s);
                    }
                }
            }

            // 将 List 转为基本类型数组，避免装箱开销
            busIndices = new int[busIdx.size()];
            slotIndices = new int[slotIdx.size()];
            for (int i = 0; i < busIdx.size(); i++) {
                busIndices[i] = busIdx.get(i);
                slotIndices[i] = slotIdx.get(i);
            }

            return inputs.toArray(new ItemStack[0]);
        }

        /**
         * 将扣减后的物品数组回写到对应的输入总线槽位
         * <p>
         * 按 getCopyOfInputs 时记录的 busIndex/slotIndex 映射，
         * 将修改后的物品写回原始槽位。如果 inputs[i] 为 null，则清空对应槽位。
         *
         * @param inputs 扣减后的物品数组（与 getCopyOfInputs 返回的数组一一对应）
         */
        @Override
        public void setInputs(ItemStack[] inputs) {
            // 如果未调用过 getCopyOfInputs，则无映射数据可回写
            if (busIndices == null || slotIndices == null) {
                return;
            }
            // 按映射关系回写每个槽位
            for (int i = 0; i < inputs.length && i < busIndices.length; i++) {
                int b = busIndices[i];
                int s = slotIndices[i];
                // 检查总线索引是否仍有效（总线可能在交易过程中被移除）
                if (b < mInputBusses.size()) {
                    MTEHatchInputBus bus = mInputBusses.get(b);
                    if (bus != null) {
                        // inputs[i] 为 null 表示该物品已被完全消耗，清空槽位
                        bus.mInventory[s] = inputs[i];
                    }
                }
            }
        }
    }

    /**
     * 输出总线槽位访问器
     * <p>
     * 实现 {@link NekoTradeExecutor.OutputSlotAccessor} 接口，
     * 将 GT5U 的 mOutputBusses（输出总线列表）适配为交易执行器所需的输出槽抽象。
     * <p>
     * 工作原理：
     * <ul>
     * <li>hasSpaceFor(stack)：遍历所有输出总线，使用 storePartial(stack, true) 模拟检查
     * 是否有足够空间容纳指定物品。多个总线可分摊容纳</li>
     * <li>insertItem(stack)：使用 addOutputPartial(stack) 将物品分发到输出总线。
     * addOutputPartial 会尽可能弹出物品，优先填入已有同类物品的槽位</li>
     * </ul>
     */
    private class BusOutputSlotAccessor implements NekoTradeExecutor.OutputSlotAccessor {

        /**
         * 检查输出总线是否有空间容纳指定物品
         * <p>
         * 遍历所有有效的输出总线，使用 storePartial 的模拟模式（simulate=true）
         * 逐步检查剩余物品是否能被全部容纳。多个总线可分摊空间。
         * <p>
         * 注意：storePartial 在 simulate 模式下不修改 mInventory，但会修改传入 stack
         * 的 stackSize（逐步减少），因此传入剩余物品的副本。
         *
         * @param stack 待检查的物品栈
         * @return 所有输出总线加起来有足够空间返回 true，否则 false
         */
        @Override
        public boolean hasSpaceFor(ItemStack stack) {
            if (stack == null) {
                return true;
            }
            // 创建副本用于模拟检查（storePartial 会修改传入 stack 的 stackSize）
            ItemStack remaining = stack.copy();
            for (MTEHatchOutputBus bus : mOutputBusses) {
                // 跳过无效总线
                if (bus == null || bus.getBaseMetaTileEntity() == null) {
                    continue;
                }
                // simulate=true：只检查不修改 mInventory，但会减少 remaining.stackSize
                bus.storePartial(remaining, true);
                // 如果剩余物品已全部能被容纳，返回 true
                if (remaining.stackSize <= 0) {
                    return true;
                }
            }
            return remaining.stackSize <= 0;
        }

        /**
         * 将物品插入输出总线
         * <p>
         * 使用 GT5U 的 addOutputPartial 方法将物品分发到 mOutputBusses。
         * addOutputPartial 会尽可能弹出物品，优先填入已有同类物品的槽位，
         * 无法容纳的部分会被丢弃（hasSpaceFor 已预检，正常情况下不会丢弃）。
         *
         * @param stack 待插入的物品栈
         */
        @Override
        public void insertItem(ItemStack stack) {
            if (stack == null) {
                return;
            }
            // 使用 GT5U 的 addOutputPartial 分发物品到输出总线
            addOutputPartial(stack);
        }
    }
}
