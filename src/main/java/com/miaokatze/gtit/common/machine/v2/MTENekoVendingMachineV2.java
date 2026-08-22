package com.miaokatze.gtit.common.machine.v2;

import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;
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
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.v2.NekoTradeExecutor;
import com.miaokatze.gtit.trade.v2.NekoTradeResult;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
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
    implements ISurvivalConstructable {

    /** 结构定义的唯一标识符，用于在 StructureLib 中索引特定的结构片段 */
    private static final String STRUCTURE_PIECE_MAIN = "main";

    /** VendingMachine ME Uplink Hatch 的 MTE ID（0.4.87 与 0.4.95 均为 2742） */
    private static final int VM_ME_UPLINK_MTE_ID = 2742;

    /**
     * 猫猫机 2x2x1 多方块结构定义
     * <p>
     * StructureLib shape 格式：外层数组=深度(前后), 内层数组=高度(上到下), 字符=宽度(左到右)
     * <ul>
     * <li>{@code c}: 机器外壳或 ME Vending Uplink Hatch 位置</li>
     * <li>{@code ~}: 控制器位置（位于下层右侧）</li>
     * </ul>
     * <p>
     * 使用 {@link gregtech.api.util.HatchElementBuilder#hatchId} 按 MTE ID 检测
     * VendingMachine ME Uplink Hatch（ID = {@link #VM_ME_UPLINK_MTE_ID}），避免跨版本
     * /类加载器导致的反射兼容识别失败。普通位置仍可放置任意
     * {@code VendingMachineBlocks.casingBlock} 作为外壳。
     */
    // 懒加载结构定义（v1.6.33 方案 D）：
    // 不能使用 static final 直接初始化，因为 <clinit> 会访问 VendingMachineBlocks.casingBlock（VM mod Init 产物）。
    // 机器注册在 sAfterGTLoad（gregtech Init 末尾）执行，触发类加载 <clinit>，此时若 <clinit> 访问 casingBlock
    // 可能因 VM.Init 未派发而 NPE。改为懒加载后，<clinit> 不再访问 casingBlock，
    // getStructureDefinition() 首次调用时（运行时）casingBlock 必然就绪。
    // 仿照 GTSR MTEKineticProcessingArray 的懒加载模式（避免 <clinit> 触发跨 mod 产物访问）。
    private static IStructureDefinition<MTENekoVendingMachineV2> NEKO_STRUCTURE_DEFINITION = null;

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

    // v1.6.28: 批次投放相关字段，用于按物品数量分档控制下落时序
    /** 当前批次物品总数（一次 executeTrade 的产出物品数） */
    private int currentBatchSize = 0;
    /** 当前批次已投放的物品数 */
    private int dispensedInBatch = 0;
    /** 是否有批次正在进行（startBatch 后、所有物品投放完前为 true） */
    private boolean batchActive = false;
    /** 批次内投放间隔（tick，由 currentBatchSize 分档决定，带随机性） */
    private int currentBatchDelay = 0;
    /** 随机数生成器（用于批次延迟和每次下落数量的随机化） */
    private final java.util.Random batchRandom = new java.util.Random();

    /** 可选的 ME Vending UplinkHatch，结构检查时设置，上限 1 个 */
    private MTEVendingUplinkHatch uplinkHatch = null;

    /** 是否已经尝试为 uplink hatch 触发 AE 代理就绪，防止每 tick 重复调用 */
    private boolean uplinkProxyReadyAttempted = false;

    /** ME 输出模式：true 时产出发往 ME 网络，false 时走本地出货槽 */
    private boolean meOutputMode = false;

    /** ME 传输队列：meOutputMode=true 时产出进入此队列，3 秒后注入 ME 网络 */
    private final java.util.List<MeTransferEntry> meTransferQueue = new java.util.ArrayList<>();

    /** ME 传输队列最大容量（独立限制，防止无限堆积） */
    private static final int MAX_ME_QUEUE_SIZE = 18;

    /** ME 传输延迟（毫秒），3 秒 = 3000ms */
    private static final long ME_TRANSFER_DELAY_MS = 3000L;

    /** UplinkHatch 缓存刷新间隔（tick），与 GUI 的 getRefreshInterval() 一致 */
    private static final long REFRESH_CACHE_INTERVAL = 20;

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
        // 懒加载：首次调用时构建结构定义，此时 VendingMachineBlocks.casingBlock（VM mod Init 产物）必然就绪
        if (NEKO_STRUCTURE_DEFINITION == null) {
            NEKO_STRUCTURE_DEFINITION = IStructureDefinition.<MTENekoVendingMachineV2>builder()
                .addShape(STRUCTURE_PIECE_MAIN, new String[][] { { "cc", "c~" } })
                .addElement(
                    'c',
                    buildHatchAdder(MTENekoVendingMachineV2.class).adder(MTENekoVendingMachineV2::addUplinkHatch)
                        .hatchId(VM_ME_UPLINK_MTE_ID)
                        .casingIndex(VendingMachineBlocks.casingBlock.getTextureIndex(0))
                        .hint(1)
                        .buildAndChain(VendingMachineBlocks.casingBlock, 0))
                .build();
        }
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
        if (aMetaTileEntity == null) {
            return false;
        }
        int metaTileId = aBaseMetaTileEntity.getMetaTileID();
        if (metaTileId != VM_ME_UPLINK_MTE_ID) {
            if (com.miaokatze.gtit.main.GTInterestingThing.LOG.isDebugEnabled()) {
                com.miaokatze.gtit.main.GTInterestingThing.LOG.debug(
                    "[NekoVMV2] addUplinkHatch rejected: metaTileID={} (expected {})",
                    metaTileId,
                    VM_ME_UPLINK_MTE_ID);
            }
            return false;
        }
        if (!(aMetaTileEntity instanceof MTEVendingUplinkHatch)) {
            if (com.miaokatze.gtit.main.GTInterestingThing.LOG.isDebugEnabled()) {
                com.miaokatze.gtit.main.GTInterestingThing.LOG.debug(
                    "[NekoVMV2] addUplinkHatch rejected: metaTileID matches {} but class is {} (potential reflection compat issue)",
                    VM_ME_UPLINK_MTE_ID,
                    aMetaTileEntity.getClass()
                        .getName());
            }
            return false;
        }
        MTEVendingUplinkHatch hatch = (MTEVendingUplinkHatch) aMetaTileEntity;
        hatch.updateTexture(aBaseCasingIndex);
        // 防御 GTNL mixin 对 null icon 调用 func_82837_s() 导致 NPE
        ItemStack machineCraftingIcon = hatch.getMachineCraftingIcon();
        if (machineCraftingIcon != null) {
            hatch.updateCraftingIcon(machineCraftingIcon);
        }
        uplinkHatch = hatch;
        if (com.miaokatze.gtit.main.GTInterestingThing.LOG.isDebugEnabled()) {
            com.miaokatze.gtit.main.GTInterestingThing.LOG.debug(
                "[NekoVMV2] addUplinkHatch succeeded at ({}, {}, {})",
                aBaseMetaTileEntity.getXCoord(),
                aBaseMetaTileEntity.getYCoord(),
                aBaseMetaTileEntity.getZCoord());
        }
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
     * 创建 {@link InternalInputSlotAccessor} 与 {@link InternalOutputSlotAccessor} 适配器，
     * 委托 {@link NekoTradeExecutor#checkTrade} 进行交易检查。
     * v1.7.8 A2：传入输出槽访问器，扣款前预检输出空间（不足直接 OUTPUT_FULL）。
     *
     * @param playerId   玩家 UUID
     * @param groupId    交易组 UUID
     * @param tradeIndex 交易在组内的索引
     * @return 交易结果（SUCCESS 或对应的失败状态）
     */
    public NekoTradeResult checkTrade(UUID playerId, UUID groupId, int tradeIndex) {
        return NekoTradeExecutor.INSTANCE.checkTrade(
            playerId,
            groupId,
            tradeIndex,
            new InternalInputSlotAccessor(uplinkHatch),
            new InternalOutputSlotAccessor());
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
            new InternalInputSlotAccessor(uplinkHatch),
            new InternalOutputSlotAccessor());
    }

    /**
     * 安全调用 uplink hatch 的 {@code removeItem}，捕获所有异常避免整机崩溃。
     * <p>
     * 当 uplink 未连接或调用抛出异常时，返回原始请求数量（表示全部未满足）。
     *
     * @param stack    请求的物品栈（会被 uplink 内部读取，建议传入 copy）
     * @param simulate true 表示模拟提取
     * @param ore      矿物词典名（可选）
     * @param tracker  提取追踪器（可选）
     * @return 未满足的剩余数量；异常时返回 {@code stack.stackSize}
     */
    private int safeRemoveItemFromUplink(ItemStack stack, boolean simulate, String ore,
        Consumer<appeng.api.storage.data.IAEItemStack> tracker) {
        if (uplinkHatch == null || stack == null || stack.stackSize <= 0) {
            return stack == null ? 0 : stack.stackSize;
        }
        try {
            return uplinkHatch.removeItem(stack, simulate, ore, tracker);
        } catch (Throwable t) {
            com.miaokatze.gtit.main.GTInterestingThing.LOG.error("[NekoVMV2] safeRemoveItemFromUplink 异常，视为提取失败", t);
            return stack.stackSize;
        }
    }

    /**
     * 安全触发 uplink hatch 的缓存刷新，捕获异常避免整机崩溃。
     */
    private void safeSetRefreshCache() {
        if (uplinkHatch == null) return;
        try {
            uplinkHatch.setRefreshCache();
        } catch (Throwable t) {
            com.miaokatze.gtit.main.GTInterestingThing.LOG.error("[NekoVMV2] safeSetRefreshCache 异常", t);
        }
    }

    /**
     * 查询 uplink 连接的 ME 网络中指定货币的余额
     * <p>
     * 供 GUI 层显示"ME 网络可用货币"使用。当未连接 uplink hatch 时返回 0。
     * <p>
     * 实现方式与 {@link InternalInputSlotAccessor#getMECurrencyAmount} 一致：
     * 通过 {@link NekoCurrencyRegistrar} 将 currencyId 转为猫猫币物品，
     * 再用 uplink 的 {@code removeItem(simulate=true)} 反推 ME 中的可用数量。
     *
     * @param currencyId 货币 ID（如 "neko"、"shimmeringNeko"）
     * @return ME 网络中该货币对应的物品总数量，无 uplink 返回 0
     */
    public int getUplinkCurrencyAmount(String currencyId) {
        if (uplinkHatch == null) return 0;
        ItemStack coinStack = NekoCurrencyRegistrar.getItemStack(currencyId, 1);
        if (coinStack == null) return 0;
        // 模拟提取大数量反推实际可用（详见 InternalInputSlotAccessor.getMECurrencyAmount）
        final int probeSize = 1_000_000_000;
        coinStack.stackSize = probeSize;
        int remain = safeRemoveItemFromUplink(coinStack, true, null, tracker -> {});
        return probeSize - remain;
    }

    /**
     * 从 ME 网络提取指定物品栈（公开接口，供 GUI 导入按钮调用）
     * <p>
     * 与 {@link InternalInputSlotAccessor#extractFromME} 不同，此方法返回剩余数量而非布尔值，
     * 便于 GUI 层知道实际提取的数量。
     *
     * @param stack 要提取的物品栈（stackSize 为请求数量）
     * @return 未满足的剩余数量（0 = 全部提取成功，等于 stackSize = 完全失败）
     */
    public int extractFromUplink(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return stack == null ? 0 : stack.stackSize;
        }
        // simulate=false 实际提取，传入 copy 防止 uplink 内部修改影响调用方
        return safeRemoveItemFromUplink(stack.copy(), false, null, tracker -> {});
    }

    /**
     * 获取 ME 输出模式状态
     * <p>
     * ME 输出模式开启时，新产出的物品通过 uplink 发送到 ME 网络；
     * 关闭时，产出走本地出货槽。
     *
     * @return true 表示 ME 输出模式已开启
     */
    public boolean isMeOutputMode() {
        return meOutputMode;
    }

    /**
     * 设置 ME 输出模式
     * <p>
     * 由 GUI 同步值 setter 调用（服务端）。
     *
     * @param mode true 开启 ME 输出模式，false 关闭
     */
    public void setMeOutputMode(boolean mode) {
        if (this.meOutputMode != mode) {
            this.meOutputMode = mode;
            if (getBaseMetaTileEntity() != null) {
                getBaseMetaTileEntity().markDirty();
            }
        }
    }

    /**
     * uplink 是否已连接且 ME 网络在线（设备通电+频道分配成功）
     * <p>
     * 供 GUI 判断 ME 模式切换按钮的可见性。
     * 与 VendingMachine 源码一致，使用 {@link MTEVendingUplinkHatch#isActive()}
     * 作为"ME 网络可用"判断标准（要求通电 AND 频道可用，因 hatch 设置了 REQUIRE_CHANNEL）。
     * <p>
     * 注意：{@code uplinkHatch != null} 仅表示结构中放置了 uplink hatch，
     * 不代表 ME 网络已通电或频道已分配。
     *
     * @return true 表示 uplink 已连接且 ME 网络激活（可切换 ME 输出模式）
     */
    public boolean hasUplink() {
        return uplinkHatch != null && uplinkHatch.isActive();
    }

    /**
     * 取回 ME 传输队列中最早入队的物品到本地出货槽
     * <p>
     * 供 GUI 取回按钮调用（阶段 4）。将队列首部的物品移到 outputBuffer，
     * 由 {@link #dispenseItems()} 逐 tick 投放到出货槽。
     * <p>
     * FIFO 语义：取回的是最早入队的物品（玩家可能最想立即拿到的）。
     *
     * @return true 表示取回成功（队列非空且移除成功）
     */
    public boolean retrieveEarliestMeTransferItem() {
        if (meTransferQueue.isEmpty()) return false;
        // v1.6.23: 物品已在出货槽中，仅从队列移除（阻止 3 秒后注入 ME）
        // 不再重新加入 outputBuffer（避免触发二次掉落动画到其他空槽）
        meTransferQueue.remove(0);
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
        return true;
    }

    /**
     * 获取 ME 传输队列大小
     * <p>
     * 供 GUI 显示队列中待传输物品数量。
     *
     * @return 队列大小
     */
    public int getMeTransferQueueSize() {
        return meTransferQueue.size();
    }

    /**
     * 序列化 ME 传输队列为字符串（供 GUI 同步值传输到客户端）
     * <p>
     * v1.6.23 格式：{@code creationTimeMs:stackSize:slotIndex:itemNBTBase64;...}
     * <p>
     * 旧格式（v1.6.22 及之前）：{@code creationTimeMs:stackSize:itemNBTBase64;...}
     * 客户端 parseMeTransferQueue 兼容两种格式（根据 split 段数判断）。
     * <p>
     * 客户端解析后用于渲染粒子动画（显示剩余传输时间和物品图标）。
     * 空队列返回空字符串。NBT 编解码复用 {@link com.miaokatze.gtit.util.NbtBase64Util}。
     *
     * @return 序列化字符串，空队列返回空字符串
     */
    public String serializeMeTransferQueue() {
        if (meTransferQueue.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (MeTransferEntry entry : meTransferQueue) {
            if (!first) sb.append(";");
            first = false;
            // 序列化 ItemStack 到 NBT，再转 base64
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            entry.stack.writeToNBT(tag);
            String base64 = com.miaokatze.gtit.util.NbtBase64Util.nbtToBase64(tag);
            sb.append(entry.creationTimeMs)
                .append(":")
                .append(entry.stack.stackSize)
                .append(":")
                .append(entry.slotIndex) // v1.6.23 新增
                .append(":")
                .append(base64 == null ? "" : base64);
        }
        return sb.toString();
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
     * v1.6.28: 标记批次开始，根据物品总数设置分档延迟
     * <p>
     * 分档规则：
     * <ul>
     * <li>1 个物品：无间隔（立即投放）</li>
     * <li>2 个物品：间隔 0.3-0.5s（6-10 tick 随机）</li>
     * <li>3-4 个物品：间隔 0.1-0.3s（2-6 tick 随机）</li>
     * <li>≥5 个物品：间隔 0.1-0.3s（2-6 tick 随机），每次下落 1-2 个</li>
     * </ul>
     * <p>
     * 由 NekoTradeExecutor.executeTrade（经 InternalOutputSlotAccessor.startBatch）或
     * dispenseItemStacks（GUI 层入口）调用。批次状态在所有物品投放完成后由
     * dispenseItems 自动清理。
     *
     * @param totalCount 本批次物品总数
     */
    public void startBatch(int totalCount) {
        // v1.7.8 A3 防御复位：上一批次若未正常结束（回滚残留/跨批次叠加等），
        // 先清理旧批次状态，避免旧计数污染新批次的分档延迟与结束判定
        if (batchActive) {
            endBatch();
        }
        this.currentBatchSize = totalCount;
        this.dispensedInBatch = 0;
        this.batchActive = true;
        this.currentBatchDelay = calculateBatchDelay(totalCount);
    }

    /**
     * v1.6.28: 标记批次结束，清理批次状态
     * <p>
     * 由 dispenseItems 在所有物品投放完成后调用，或由 executeTrade 回滚路径调用。
     */
    public void endBatch() {
        this.batchActive = false;
        this.currentBatchSize = 0;
        this.dispensedInBatch = 0;
        this.currentBatchDelay = 0;
    }

    /**
     * v1.6.28: 根据批次大小计算投放延迟（tick）
     *
     * @param count 物品总数
     * @return 投放间隔（tick）
     */
    private int calculateBatchDelay(int count) {
        if (count <= 1) {
            return 0; // 单物品无间隔
        }
        if (count == 2) {
            // 2 物品：6-10 tick（0.3-0.5s）
            return 6 + batchRandom.nextInt(5);
        }
        // 3+ 物品：2-6 tick（0.1-0.3s）
        return 2 + batchRandom.nextInt(5);
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
        // v1.6.28: GUI 层入口也启动批次模式（仅对实际入队的物品计数）
        int batchCount = 0;
        for (ItemStack stack : itemStacks) {
            if (stack != null && stack.stackSize > 0) {
                // 溢出检查：与单个版 dispenseItemStack 一致，完全无空槽时掉落到机器旁
                if (getFirstEmptyOutputSlot() == -1) {
                    // 输出槽完全满，剩余物品掉落到机器旁
                    dropItemsNearMachine(stack);
                } else {
                    this.outputBuffer.add(stack.copy());
                    batchCount++;
                }
            }
        }
        // v1.6.28: 启动批次模式控制下落时序（由 dispenseItems 在投放完成后自动结束）
        if (batchCount > 0) {
            startBatch(batchCount);
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
     * <p>
     * v1.6.28: 批次模式下（batchActive=true）使用分档延迟；
     * 批次≥5时每次下落 1-2 个（50% 概率额外投放一个）；
     * 所有物品投放完成后自动调用 endBatch 清理批次状态。
     */
    private void dispenseItems() {
        if (!mMachine) return;
        // v1.6.28: 获取投放延迟（批次模式下为分档延迟，单物品批次返回 0 表示立即投放）
        int delay = getDispensingDelay();
        if (this.newBufferedOutputs
            || (!this.outputBuffer.isEmpty() && (delay <= 0 || this.ticksSinceOutput % delay == 0))) {
            dispenseFirstNonNullItem();
            // v1.6.28: 批次>=5时每次下落1-2个（50%概率额外投放一个）
            if (batchActive && currentBatchSize >= 5 && !this.outputBuffer.isEmpty()) {
                if (batchRandom.nextInt(2) == 0) {
                    dispenseFirstNonNullItem();
                }
            }
            this.ticksSinceOutput = 0;
        }
        this.ticksSinceOutput = this.newBufferedOutputs ? 0 : this.ticksSinceOutput + 1;
        this.newBufferedOutputs = false;
        // v1.6.28: 批次结束判断——所有物品投放完成后清理批次状态
        // v1.7.8 A3 加固：追加 outputBuffer.isEmpty() 条件——跨批次叠加或溢出掉落后
        // 计数可能先满而队列仍有物品未投放，须等队列排空才允许结束批次，
        // 防止提前 endBatch 导致后续物品失去分档控制/批次状态错乱
        if (batchActive && dispensedInBatch >= currentBatchSize && outputBuffer.isEmpty()) {
            endBatch();
        }
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    /**
     * 获取投放延迟（tick）
     * <p>
     * v1.6.28: 批次模式下（batchActive=true）使用分档延迟（由 calculateBatchDelay 设置）；
     * 非批次场景（如 GUI 弹出猫猫币等旧路径）保留原有对数加速逻辑。
     */
    private int getDispensingDelay() {
        // v1.6.28: 批次模式下使用分档延迟
        if (batchActive) {
            return currentBatchDelay;
        }
        // 原有对数加速逻辑（向后兼容，非批次场景如 GUI 弹出猫猫币）
        int baseDelay = 10;
        int queueSize = outputBuffer.size();
        if (queueSize <= 1) return baseDelay;
        double acceleration = Math.log(queueSize);
        if (acceleration < 1) return baseDelay;
        return (int) (baseDelay / acceleration);
    }

    /**
     * 从队列中取出第一个有效物品，投放到第一个空槽
     * <p>
     * v1.6.28: 投放后递增 dispensedInBatch 计数，供 dispenseItems 判断批次是否完成。
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
            // v1.6.28: 递增批次已投放计数（无论成功入槽还是溢出掉落）
            if (batchActive) {
                dispensedInBatch++;
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
     * <p>
     * v1.6.28: ME 输出模式下，投放时创建 MeTransferEntry（从 InternalOutputSlotAccessor.insertItem 移至此处），
     * 使 ME 模式也走 outputBuffer 队列分档投放。
     */
    private void outputIntoSlot(ItemStack stack, int slotIndex) {
        ItemStack output = stack.copy();
        output.stackSize = stack.stackSize;
        stack.stackSize = 0;
        outputItems.setStackInSlot(slotIndex, output);
        // v1.7.11：物品落槽时播放 item_drop 音效（复刻 1.6.* VM 原版行为）
        IGregTechTileEntity bMTE = getBaseMetaTileEntity();
        if (bMTE != null) {
            net.minecraft.world.World world = bMTE.getWorld();
            if (world != null && !world.isRemote) {
                world.playSoundEffect(
                    bMTE.getXCoord() + 0.5,
                    bMTE.getYCoord() + 0.5,
                    bMTE.getZCoord() + 0.5,
                    "vendingmachine:item_drop",
                    1.0f,
                    1.0f);
            }
        }
        // v1.6.28: ME 模式下投放时创建 MeTransferEntry（从 insertItem 移至此处）
        // 记录 slotIndex 供粒子定位和 3 秒后清槽注入 ME
        if (meOutputMode) {
            meTransferQueue.add(new MeTransferEntry(output.copy(), System.currentTimeMillis(), slotIndex));
        }
    }

    /**
     * 将出货槽的物品填充到玩家背包
     * <p>
     * 复刻 V1/VM 父类 MTEVendingMachine.fillPlayerInventoryWithDispensedItems：
     * 遍历 outputItems，将物品复制到玩家背包；无法放入的物品保留在槽中。
     *
     * @param player 目标玩家
     */
    public void fillPlayerInventoryWithDispensedItems(EntityPlayer player) {
        if (player == null) return;
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            ItemStack stack = outputItems.getStackInSlot(i);
            if (stack == null) continue;
            ItemStack toAdd = stack.copy();
            boolean fullyAdded = player.inventory.addItemStackToInventory(toAdd);
            outputItems.setStackInSlot(i, toAdd.stackSize <= 0 ? null : toAdd);
            if (!fullyAdded) break;
        }
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
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

    // v1.7.18：删除 ICasingTextureProvider（5.09.54.20 专用接口），改用 getTexture() 覆写（5.09.52.594 + 5.09.54.20 通用）
    // 实现 beta-1（5.09.52.594）和 beta-2（5.09.54.20）双环境兼容

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
        // 保存 ME 输出模式状态
        aNBT.setBoolean("meOutputMode", meOutputMode);
        // 保存 ME 传输队列（阶段 4，v1.6.23 增加 slotIndex）
        if (!meTransferQueue.isEmpty()) {
            net.minecraft.nbt.NBTTagList meQueueList = new net.minecraft.nbt.NBTTagList();
            for (MeTransferEntry entry : meTransferQueue) {
                net.minecraft.nbt.NBTTagCompound entryTag = new net.minecraft.nbt.NBTTagCompound();
                entryTag.setLong("creationTime", entry.creationTimeMs);
                entryTag.setInteger("slotIndex", entry.slotIndex); // v1.6.23 新增
                entry.stack.writeToNBT(entryTag);
                meQueueList.appendTag(entryTag);
            }
            aNBT.setTag("meTransferQueue", meQueueList);
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
        // 加载 ME 输出模式状态
        if (aNBT.hasKey("meOutputMode")) {
            meOutputMode = aNBT.getBoolean("meOutputMode");
        }
        // 加载 ME 传输队列（阶段 4，v1.6.23 增加 slotIndex 兼容）
        if (aNBT.hasKey("meTransferQueue")) {
            net.minecraft.nbt.NBTTagList meQueueList = aNBT.getTagList("meTransferQueue", 10);
            for (int i = 0; i < meQueueList.tagCount(); i++) {
                net.minecraft.nbt.NBTTagCompound entryTag = meQueueList.getCompoundTagAt(i);
                ItemStack stack = ItemStack.loadItemStackFromNBT(entryTag);
                long creationTime = entryTag.getLong("creationTime");
                // v1.6.23: 读取 slotIndex，旧存档无此 key 时默认 -1
                int slotIndex = entryTag.hasKey("slotIndex") ? entryTag.getInteger("slotIndex") : -1;
                if (stack != null && stack.stackSize > 0) {
                    meTransferQueue.add(new MeTransferEntry(stack, creationTime, slotIndex));
                }
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

    // === ME 传输队列处理（阶段 4） ===

    /**
     * 处理 ME 传输队列
     * <p>
     * 遍历队列，将超过 {@link #ME_TRANSFER_DELAY_MS}（3 秒）的条目通过
     * {@code appeng.util.Platform.poweredInsert} 直接注入 ME 网络。
     * <p>
     * 若注入未完全成功（返回 remainder），将槽位更新为剩余物品并重置延迟，稍后重试。
     * <p>
     * 如果 uplink 已断开（uplinkHatch == null），将队列中所有物品回退到 outputBuffer，
     * 由本地出货槽路径投放，防止物品丢失。
     * <p>
     * 应在 onPostTick 服务端块内、{@link #dispenseItems()} 之后调用。
     */
    private void processMeTransferQueue() {
        if (meTransferQueue.isEmpty()) return;
        long now = System.currentTimeMillis();

        // v1.6.23: uplink 丢失时，物品已在出货槽中，仅清空队列（玩家可自行取走）
        if (uplinkHatch == null) {
            meTransferQueue.clear();
            if (getBaseMetaTileEntity() != null) {
                getBaseMetaTileEntity().markDirty();
            }
            return;
        }

        // 处理已到期的条目（FIFO：队列头部先入队，先到期）
        java.util.Iterator<MeTransferEntry> it = meTransferQueue.iterator();
        while (it.hasNext()) {
            MeTransferEntry entry = it.next();
            if (now - entry.creationTimeMs >= ME_TRANSFER_DELAY_MS) {
                if (entry.slotIndex >= 0) {
                    // v1.6.23: 检查槽位中是否还有匹配的物品（玩家可能已取走）
                    ItemStack slotStack = outputItems.getStackInSlot(entry.slotIndex);
                    if (slotStack != null && slotStack.isItemEqual(entry.stack)
                        && ItemStack.areItemStackTagsEqual(slotStack, entry.stack)) {
                        // 物品仍在槽中，先检查 ME 网络是否能接收
                        if (canUplinkAcceptItems(slotStack)) {
                            // v1.7.33: 直接注入槽内实际物品，根据 remainder 决定是否清空或保留剩余
                            appeng.api.storage.data.IAEItemStack remainder = injectItemToUplink(slotStack);
                            if (remainder == null || remainder.getStackSize() <= 0) {
                                // 全部注入成功，清空对应出货槽
                                outputItems.setStackInSlot(entry.slotIndex, null);
                            } else {
                                // 注入未完全成功，保留剩余并重试
                                outputItems.setStackInSlot(entry.slotIndex, remainder.getItemStack());
                                entry.creationTimeMs = now;
                                break;
                            }
                        } else {
                            // ME 网络当前不能接收（无能量/无空间），保留在出货槽并延迟重试
                            entry.creationTimeMs = now;
                            break;
                        }
                    }
                    // 槽位为空或物品不匹配：玩家已取走，跳过注入
                } else {
                    // slotIndex == -1（旧存档兼容或无空槽回退）：直接注入，无法保留剩余
                    injectItemToUplink(entry.stack);
                }
                it.remove();
                if (getBaseMetaTileEntity() != null) {
                    getBaseMetaTileEntity().markDirty();
                }
            } else {
                // 队列是 FIFO，遇到未到期的就停止（后续条目入队更晚，必然也未到期）
                break;
            }
        }
    }

    /**
     * 检查 uplink 连接的 ME 网络当前能否接受指定物品栈
     * <p>
     * 使用 {@code appeng.util.Platform.poweredInsert} 的 simulate 模式直接注入网络存储，
     * 同时验证空间与能量。任何异常都按"不能接收"处理，避免 uplink 未就绪或网络不可用时崩溃。
     *
     * @param stack 待检查的物品栈
     * @return true 表示 ME 网络可以接收该物品栈
     */
    private boolean canUplinkAcceptItems(ItemStack stack) {
        if (uplinkHatch == null || stack == null || stack.stackSize <= 0) return false;
        try {
            appeng.api.networking.energy.IEnergySource energy = uplinkHatch.getProxy()
                .getEnergy();
            appeng.api.networking.storage.IStorageGrid storage = uplinkHatch.getProxy()
                .getStorage();
            if (storage == null) return false;
            appeng.api.storage.data.IAEItemStack aeStack = appeng.util.item.AEItemStack.create(stack);
            if (aeStack == null) return false;
            appeng.api.storage.data.IAEItemStack remainder = appeng.util.Platform.poweredInsert(
                energy,
                storage.getItemInventory(),
                aeStack,
                new appeng.api.networking.security.MachineSource(uplinkHatch),
                appeng.api.config.Actionable.SIMULATE);
            return remainder == null || remainder.getStackSize() <= 0;
        } catch (Throwable t) {
            com.miaokatze.gtit.main.GTInterestingThing.LOG.error("[NekoVMV2] canUplinkAcceptItems 检查失败，按不能接收处理", t);
            return false;
        }
    }

    /**
     * 将物品栈直接注入 uplink 的 ME 网络
     * <p>
     * 使用 {@code appeng.util.Platform.poweredInsert} 的 modulate 模式直接注入网络存储，
     * 绕过 VendingMachine uplink hatch 的 pendingItemInject 缓冲（该缓冲会忽略 damage/meta
     * 错误合并同 Item 类的不同物品）。
     * <p>
     * 若获取能量/存储失败或发生异常，返回原始 stack 的 AEItemStack，让上层保留物品并重试。
     *
     * @param stack 待注入的物品栈
     * @return 未注入完的剩余 AEItemStack；null 或 stackSize<=0 表示全部注入成功
     */
    private appeng.api.storage.data.IAEItemStack injectItemToUplink(ItemStack stack) {
        if (uplinkHatch == null || stack == null || stack.stackSize <= 0) return null;
        appeng.api.storage.data.IAEItemStack aeStack = appeng.util.item.AEItemStack.create(stack);
        if (aeStack == null) {
            com.miaokatze.gtit.main.GTInterestingThing.LOG.error("[NekoVMV2] injectItemToUplink 无法将物品转换为 AEItemStack");
            return null;
        }
        try {
            appeng.api.networking.energy.IEnergySource energy = uplinkHatch.getProxy()
                .getEnergy();
            appeng.api.networking.storage.IStorageGrid storage = uplinkHatch.getProxy()
                .getStorage();
            if (storage == null) return aeStack;
            return appeng.util.Platform.poweredInsert(
                energy,
                storage.getItemInventory(),
                aeStack,
                new appeng.api.networking.security.MachineSource(uplinkHatch),
                appeng.api.config.Actionable.MODULATE);
        } catch (Throwable t) {
            // v1.7.33: 注入失败时返回原始 AEItemStack，物品保留在出货槽中稍后重试
            com.miaokatze.gtit.main.GTInterestingThing.LOG.error("[NekoVMV2] injectItemToUplink 失败，物品保留在出货槽中", t);
            return aeStack;
        }
    }

    // === 维护检查 ===

    /**
     * 猫猫机不需要维护（无维修工具槽），覆写返回 false。
     * <p>
     * 父类 {@link gregtech.api.metatileentity.implementations.MTEMultiBlockBase} 会在
     * 构造器（第 269 行）和 NBT 加载（第 278、451 行）中调用此方法判断是否需要维护检查；
     * 返回 false 时基类会调用 {@code fixAllIssues()} 自动设置所有维护字段
     * (mWrench/mScrewdriver/mSoftMallet/mHardHammer/mSolderingTool/mCrowbar) 为 true，
     * 使 {@code getRepairStatus()=6=getIdealStatus()}，
     * 从而 {@code hasProblems=false}，waila 不再显示"存在问题"。
     * <p>
     * 同时基类 doTickUpdate 中的维护相关逻辑（如第 658 行早返回、第 1430/1436 行的
     * 随机维护触发）也会因返回 false 而跳过，符合猫猫机无维护机制的设计。
     *
     * @return false 表示本机器不需要维护检查
     */
    @Override
    public boolean shouldCheckMaintenance() {
        return false;
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
            // B2-02：消费 GUI C2S 同步值投递的服务端动作（Netty IO 线程 → 主线程，
            // 队列为空时仅一次 volatile 读，开销可忽略；任一台在 tick 的机器都会清空全局队列）
            NekoVMGuiV2.drainServerActions();
            aBaseMetaTileEntity.setActive(mMachine);
            // 当 uplink 丢失时重置代理就绪兜底标记，下次连接成功后重新尝试
            if (uplinkHatch == null) {
                uplinkProxyReadyAttempted = false;
            }
            // 逐 tick 投放缓冲队列中的物品
            if (mMachine) {
                dispenseItems();
                // 阶段 4：处理 ME 传输队列，将到期物品注入 ME 网络
                processMeTransferQueue();
                // 复刻 V1/VM 父类（第 595-603 行）：周期性通知 UplinkHatch 刷新 ME 网络缓存，
                // 使连接的 ME 网络中的物品/货币数据保持同步。
                if (uplinkHatch != null && aTick % REFRESH_CACHE_INTERVAL == 0) {
                    safeSetRefreshCache();
                }
            }
            // beta-1 兼容兜底：VM uplink hatch 的 onFirstTick 可能因加载顺序未能正确初始化 AE 代理，
            // 在检测到已连接但代理未激活时主动触发一次 onReady()。
            if (uplinkHatch != null && !uplinkProxyReadyAttempted && !uplinkHatch.isActive()) {
                try {
                    uplinkHatch.getProxy()
                        .onReady();
                    uplinkProxyReadyAttempted = true;
                    if (com.miaokatze.gtit.main.GTInterestingThing.LOG.isDebugEnabled()) {
                        com.miaokatze.gtit.main.GTInterestingThing.LOG.debug(
                            "[NekoVMV2] 已触发 uplink hatch AE 代理就绪兜底 at ({}, {}, {})",
                            aBaseMetaTileEntity.getXCoord(),
                            aBaseMetaTileEntity.getYCoord(),
                            aBaseMetaTileEntity.getZCoord());
                    }
                } catch (Throwable t) {
                    uplinkProxyReadyAttempted = true;
                    com.miaokatze.gtit.main.GTInterestingThing.LOG.error("[NekoVMV2] uplink hatch AE 代理就绪兜底调用失败", t);
                }
            }
        }
        // 客户端：复刻 V1 MTEVendingMachine 逻辑
        // 仅在非激活时清除覆盖层，不主动重注册（依赖 onTextureUpdate/onValueUpdate 事件驱动）
        // 修复：原代码每 tick 调用 setTextureOverlay（先 clearOverlay 再注册）导致闪烁
        if (aBaseMetaTileEntity.isClientSide()) {
            if (!aBaseMetaTileEntity.isActive()) {
                clearOverlay();
            }
            return;
        }
    }

    /**
     * 同步 mMachine 状态到客户端
     * <p>
     * 复刻 GT5U MTEAirFilterBase 的模式。基类默认不同步 mMachine，
     * 导致客户端无法及时获知结构成型状态，覆盖层渲染与实际状态脱节。
     *
     * @return mMachine 状态编码为 byte（1=成型，0=未成型）
     */
    @Override
    public byte getUpdateData() {
        return (byte) (mMachine ? 1 : 0);
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
        // 从更新数据提取 mMachine 状态（复刻 MTEAirFilterBase 模式）
        // 修复：原代码直接读取 mMachine 字段，但该字段未由 getUpdateData 同步，
        // 客户端 mMachine 为旧值，导致覆盖层与实际结构状态脱节
        mMachine = (aValue & 0x1) != 0;
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isClientSide()) {
            if (mMachine) {
                setTextureOverlay();
            } else {
                clearOverlay();
            }
        }
    }

    /**
     * 方块更新回调
     * <p>
     * 复刻 V1 MTEVendingMachine 和 GT5U MTEAirFilterBase 的模式。
     * GT5U 的 handleBlockUpdateClient 会调用此方法，覆盖后让方块更新机制
     * 驱动覆盖层刷新，避免依赖 onPostTick 每 tick 重注册。
     */
    @Override
    public void onTextureUpdate() {
        setTextureOverlay();
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
        // 复刻 V1 MTEVendingMachine：客户端首次 tick 时同步朝向并无条件初始化覆盖层
        // 修复：原代码有 if (mMachine) 条件，区块重载时 mMachine=false 会跳过覆盖层设置，
        // 导致覆盖层"时有时无"。V1 和 MTEAirFilterBase 均为无条件调用。
        // setTextureOverlay 内部会通过 tile.isActive() 判断使用激活态还是非激活态材质。
        if (aBaseMetaTileEntity.isClientSide()) {
            StructureLibAPI.queryAlignment((IAlignmentProvider) aBaseMetaTileEntity);
            setTextureOverlay();
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
     * 设置朝向回调
     * <p>
     * 朝向变化时重新设置覆盖层，确保覆盖层位置与新朝向同步。
     * <p>
     * 注意：V2 继承自 MTEEnhancedMultiBlockBase，其 setExtendedFacing 默认实现（第 85-107 行）
     * 不会调用 setFrontFacing，因此不会触发 onFacingChange 递归。
     * V1 父类 MTEVendingMachine 继承自 MTEMultiBlockBase（无 onFacingChange），可以直接调用
     * setFrontFacing，但 V2 不能照搬，否则会导致 setExtendedFacing → setFrontFacing →
     * onFacingChange → toolSetDirection → setExtendedFacing 无限递归（StackOverflowError）。
     * 正确做法是调用 super.setExtendedFacing 让基类处理朝向，再刷新覆盖层。
     *
     * @param alignment 新的朝向
     */
    @Override
    public void setExtendedFacing(ExtendedFacing alignment) {
        super.setExtendedFacing(alignment);
        setTextureOverlay();
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
        } else if (getBaseMetaTileEntity() != null && !getBaseMetaTileEntity().isClientSide()) {
            // B2-08：服务端拆机时把仍在掉落动画窗口内（outputBuffer 排队中）的已付款产物
            // 逐件掉落在机器位置，避免随 MTE 消失（NBT 路径不受影响——区块卸载走 saveNBTData）
            ItemStack pending;
            while ((pending = outputBuffer.poll()) != null) {
                try {
                    dropItemsNearMachine(pending);
                } catch (Throwable t) {
                    com.miaokatze.gtit.main.GTInterestingThing.LOG.error("[NekoVMV2] 拆机掉落排队产物失败", t);
                }
            }
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
     * 创建输入槽访问器（v1.7.6 公开入口）
     * <p>
     * 供抽奖扣费分流（{@link com.miaokatze.gtit.lottery.LotteryManager}）等外部系统
     * 以「副本校验 → 写回」的原子模式消耗本机输入槽物品；
     * 访问器携带当前 uplinkHatch 引用（可为 null，此时无 ME 能力）。
     *
     * @return 输入槽访问器（永不为 null）
     */
    public NekoTradeExecutor.InputSlotAccessor createInputSlotAccessor() {
        return new InternalInputSlotAccessor(uplinkHatch);
    }

    /**
     * 内置输入槽访问器
     * <p>
     * 实现 {@link NekoTradeExecutor.InputSlotAccessor} 接口，
     * 直接操作本机器的 {@link #inputItems}。
     * <p>
     * 当构造时传入非 null 的 {@link MTEVendingUplinkHatch} 时，
     * 同时实现 ME 网络物品/货币提取能力：
     * <ul>
     * <li>{@link #canExtractFromME} / {@link #extractFromME}：通过 uplink 的
     * {@code removeItem} 接口从 ME 网络提取物品</li>
     * <li>{@link #getMECurrencyAmount} / {@link #tryDeductMECurrency}：通过
     * {@link NekoCurrencyRegistrar} 将 currencyId 转为猫猫币物品，
     * 再经 uplink 从 ME 提取</li>
     * </ul>
     * <p>
     * V2 的 currencyId（"neko"、"shimmeringNeko"）与 VM 的 CurrencyType
     * （dreamcraft 硬币系统）不互通，因此不走 uplink 的 removeCoins 路径，
     * 而是直接以"猫猫币物品"形式从 ME 提取（1 物品 = 1 货币值）。
     */
    private class InternalInputSlotAccessor implements NekoTradeExecutor.InputSlotAccessor {

        /** 连接的 uplink hatch，为 null 时所有 ME 方法返回默认值（无 ME 能力） */
        private final MTEVendingUplinkHatch uplinkHatch;

        /**
         * 构造输入槽访问器
         *
         * @param uplinkHatch 连接的 uplink hatch，可为 null
         */
        InternalInputSlotAccessor(MTEVendingUplinkHatch uplinkHatch) {
            this.uplinkHatch = uplinkHatch;
        }

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

        @Override
        public boolean canExtractFromME(ItemStack stack) {
            if (uplinkHatch == null || stack == null || stack.stackSize <= 0) return false;
            // simulate=true 模拟提取，返回未满足的剩余数量；0 表示全部满足
            // 传入 copy 防止 uplink 内部修改影响调用方
            int remain = safeRemoveItemFromUplink(stack.copy(), true, null, tracker -> {});
            return remain == 0;
        }

        @Override
        public boolean extractFromME(ItemStack stack) {
            if (uplinkHatch == null || stack == null || stack.stackSize <= 0) return false;
            // simulate=false 实际提取
            int remain = safeRemoveItemFromUplink(stack.copy(), false, null, tracker -> {});
            return remain == 0;
        }

        @Override
        public int getMECurrencyAmount(String currencyId) {
            if (uplinkHatch == null) return 0;
            ItemStack coinStack = NekoCurrencyRegistrar.getItemStack(currencyId, 1);
            if (coinStack == null) return 0;
            // 通过模拟提取大数量反推 ME 中猫猫币物品的实际可用数量
            // uplink.removeItem 返回"未满足的剩余数量"，所以实际可用 = probeSize - remain
            // probeSize 取 10 亿（足够覆盖任何合理场景，又远小于 int 上限避免溢出风险）
            final int probeSize = 1_000_000_000;
            coinStack.stackSize = probeSize;
            int remain = safeRemoveItemFromUplink(coinStack, true, null, tracker -> {});
            return probeSize - remain;
        }

        @Override
        public boolean tryDeductMECurrency(String currencyId, int amount) {
            if (uplinkHatch == null || amount <= 0) return false;
            ItemStack coinStack = NekoCurrencyRegistrar.getItemStack(currencyId, amount);
            if (coinStack == null) return false;
            // simulate=false 实际提取指定数量的猫猫币物品
            int remain = safeRemoveItemFromUplink(coinStack, false, null, tracker -> {});
            return remain == 0;
        }
    }

    /**
     * ME 传输队列条目：记录待发送到 ME 网络的物品及其入队时间
     * <p>
     * 当 meOutputMode=true 时，产出物品先进入 meTransferQueue，
     * 经过 {@link #ME_TRANSFER_DELAY_MS} (3000ms) 延迟后才通过
     * {@code uplinkHatch.injectItems} 注入 ME 网络。
     * 期间玩家可通过 GUI 点击取回（移回 outputBuffer）。
     */
    public static class MeTransferEntry {

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
        MeTransferEntry(ItemStack stack, long creationTimeMs, int slotIndex) {
            this.stack = stack;
            this.creationTimeMs = creationTimeMs;
            this.slotIndex = slotIndex;
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
     * <p>
     * v1.6.28 改造：ME 模式和本地模式统一走 outputBuffer 队列分档投放。
     * ME 模式的 MeTransferEntry 在 outputIntoSlot 时创建（不再在 insertItem 中直接写入槽位）。
     * rollback 统一从 outputBuffer 尾部移除。
     */
    private class InternalOutputSlotAccessor implements NekoTradeExecutor.OutputSlotAccessor {

        @Override
        public boolean hasSpaceFor(ItemStack stack) {
            if (stack == null) return true;
            // v1.6.28: ME 模式和本地模式统一走 outputBuffer 队列，空间检查逻辑一致
            if (meOutputMode) {
                // ME 模式额外检查传输队列容量（MeTransferEntry 在投放时创建）
                if (meTransferQueue.size() >= MAX_ME_QUEUE_SIZE) return false;
            }
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

        /**
         * v1.7.8 A2：返回当前可用输出槽数，供 checkTrade 在扣款前预检输出空间
         * <p>
         * 口径与 {@link #hasSpaceFor} 一致：空槽数扣除 outputBuffer 队列占位（防超卖）；
         * ME 输出模式传输队列满时视为无可用空间。
         */
        @Override
        public int getAvailableSlotCount() {
            if (meOutputMode && meTransferQueue.size() >= MAX_ME_QUEUE_SIZE) {
                return 0;
            }
            int emptySlots = 0;
            for (int i = 0; i < OUTPUT_SLOTS; i++) {
                if (outputItems.getStackInSlot(i) == null) {
                    emptySlots++;
                }
            }
            // 扣除队列已占用的虚拟槽位，防止预检超卖（与 hasSpaceFor 同口径）
            return Math.max(0, emptySlots - outputBuffer.size());
        }

        @Override
        public void insertItem(ItemStack stack) {
            if (stack == null) return;
            // v1.6.28: ME 模式和本地模式统一走 outputBuffer 队列分档投放
            // ME 模式的 MeTransferEntry 在 dispenseFirstNonNullItem → outputIntoSlot 时创建
            outputBuffer.add(stack.copy());
            newBufferedOutputs = true;
            if (getBaseMetaTileEntity() != null) {
                getBaseMetaTileEntity().markDirty();
            }
        }

        @Override
        public void rollback(int count) {
            // v1.6.28: ME 模式和本地模式都走 outputBuffer，统一从队列尾部移除
            // outputBuffer 是 ConcurrentLinkedQueue，无 removeLast，用迭代移除最后一个
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

        // v1.6.28: 批次标记接口实现，委托给外部类的方法控制下落时序分档

        @Override
        public void startBatch(int count) {
            MTENekoVendingMachineV2.this.startBatch(count);
        }

        @Override
        public void endBatch() {
            MTENekoVendingMachineV2.this.endBatch();
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
