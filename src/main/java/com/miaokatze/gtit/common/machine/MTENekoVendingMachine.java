package com.miaokatze.gtit.common.machine;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cubefury.vendingmachine.blocks.MTEVendingMachine;
import com.cubefury.vendingmachine.blocks.MTEVendingUplinkHatch;
import com.cubefury.vendingmachine.blocks.gui.MTEVendingMachineGui;
import com.cubefury.vendingmachine.blocks.gui.WalletMode;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.trade.TradeDatabase;
import com.cubefury.vendingmachine.trade.TradeGroup;
import com.cubefury.vendingmachine.util.OverlayHelper;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.miaokatze.gtit.common.machine.neko.NekoVendingMachineGui;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.register.TextureManager;
import com.miaokatze.gtit.trade.NekoTradeRegistry;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.RenderOverlay;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 猫猫售货机
 * <p>
 * 继承自 VM (VendingMachine) 模组的 {@link MTEVendingMachine}，保留交易处理逻辑，
 * 覆盖多方块结构（2x2x1）、材质和 GUI。
 * <p>
 * 多方块结构：2宽 x 2高 x 1深，控制器在右下角
 * <p>
 * 猫猫币交易机制：
 * - 猫猫币不放入 Trade.fromItems，只记录在 NekoTradeInfo 中
 * - checkTrade 覆盖：对猫猫币交易，先检查 NekoWallet 余额，再检查 fromItems 中的普通物品
 * - processTradeOnServer 是 private 方法，通过 MixinMTEVendingMachine 拦截，扣减 NekoWallet
 */
@IMetaTileEntity.SkipGenerateDescription
public class MTENekoVendingMachine extends MTEVendingMachine {

    // 反射访问父类 private 字段 uplinkHatch
    private static final java.lang.reflect.Field UPLINK_HATCH_FIELD;

    static {
        try {
            UPLINK_HATCH_FIELD = MTEVendingMachine.class.getDeclaredField("uplinkHatch");
            UPLINK_HATCH_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("[NEKO] Failed to access uplinkHatch field", e);
        }
    }

    /**
     * 猫猫机 2x2x1 多方块结构定义
     * <p>
     * StructureLib shape: 外层数组=深度(前后), 内层数组=高度(上到下), 字符=宽度(左到右)
     * 形状：上层 cc，下层 c~（控制器在右下角）
     */
    private static final IStructureDefinition<MTENekoVendingMachine> NEKO_STRUCTURE_DEFINITION = IStructureDefinition
        .<MTENekoVendingMachine>builder()
        .addShape("main", new String[][] { { "cc", "c~" } }) // 深度1层: 上层cc, 下层c~(控制器右下)
        .addElement(
            'c',
            StructureUtility.ofChain(
                GTStructureUtility.ofHatchAdderOptional(
                    MTENekoVendingMachine::addUplinkHatchNeko,
                    com.cubefury.vendingmachine.blocks.VendingMachineBlocks.casingBlock.getTextureIndex(0),
                    1,
                    com.cubefury.vendingmachine.blocks.VendingMachineBlocks.casingBlock,
                    0),
                StructureUtility.ofBlock(com.cubefury.vendingmachine.blocks.VendingMachineBlocks.casingBlock, 0)))
        .build();

    /**
     * 猫猫机覆盖层偏移（3个非控制器方块）
     * <p>
     * 控制器在右下角，3个非控制器方块：
     * 索引0: (0, +1) 右上（控制器正上方）
     * 索引1: (-1, +1) 左上
     * 索引2: (-1, 0) 左下（控制器左侧）
     */
    private static final int[] NEKO_VM_X = new int[] { 0, -1, -1 };
    private static final int[] NEKO_VM_Y = new int[] { 1, 1, 0 };

    // 材质常量
    private static final ITexture[] NEKO_FACING_SIDE = new ITexture[] {
        TextureFactory.of(com.cubefury.vendingmachine.api.enums.Textures.VM_CASING) };
    private static final ITexture[] NEKO_FACING_FRONT = new ITexture[] {
        TextureFactory.of(com.cubefury.vendingmachine.api.enums.Textures.VM_MACHINE_FRONT_OFF) };
    private static final ITexture[] NEKO_FACING_ACTIVE = new ITexture[] {
        TextureFactory.of(com.cubefury.vendingmachine.api.enums.Textures.VM_MACHINE_FRONT_ON), TextureFactory.builder()
            .addIcon(com.cubefury.vendingmachine.api.enums.Textures.VM_MACHINE_FRONT_ON_GLOW)
            .glow()
            .build() };

    /**
     * BGM 构建阶段标志位（已废弃）
     *
     * @deprecated 使用 {@link NekoVendingMachineGui#isNekoGuiOpen} 替代
     */
    @Deprecated
    public static boolean isBuildingNekoGui = false;

    public MTENekoVendingMachine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTENekoVendingMachine(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTENekoVendingMachine(this.mName);
    }

    @Override
    protected MTEVendingMachineGui getGui() {
        GTInterestingThing.LOG.info("[NEKO] getGui() called, creating NekoVendingMachineGui");
        return new NekoVendingMachineGui(this);
    }

    @Override
    protected MultiblockTooltipBuilder getTooltip() {
        return new MultiblockTooltipBuilder().addMachineType("Neko Vending Machine")
            .addInfo("喵~ 猫猫售货机，用猫猫币购买物品！")
            .beginStructureBlock(2, 2, 1, false)
            .addController("Bottom Right")
            .addOtherStructurePart("Tin Item Pipe Casings", "Everything except the controller")
            .addOtherStructurePart("ME Vending Uplink Hatch", "Any Pipe Casing, Optional")
            .toolTipFinisher();
    }

    /**
     * 猫猫机的多方块结构检查
     * <p>
     * 使用 2x2x1 结构（控制器在右下角），偏移参数 (1, 0, 0) 对应结构定义中 ~ 的位置
     */
    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        if (this.getBaseMetaTileEntity() == null) {
            return false;
        }
        // 反射重置父类 private uplinkHatch 字段
        try {
            UPLINK_HATCH_FIELD.set(this, null);
        } catch (IllegalAccessException e) {
            GTInterestingThing.LOG.error("[NEKO] Failed to reset uplinkHatch", e);
        }
        return NEKO_STRUCTURE_DEFINITION.check(
            this,
            "main",
            this.getBaseMetaTileEntity()
                .getWorld(),
            this.getExtendedFacing(),
            this.getBaseMetaTileEntity()
                .getXCoord(),
            this.getBaseMetaTileEntity()
                .getYCoord(),
            this.getBaseMetaTileEntity()
                .getZCoord(),
            1, // 水平偏移：~ 在第2列（index 1）
            1, // 垂直偏移：~ 在第2行（index 1，下层）
            0, // 深度偏移
            !this.mMachine);
    }

    /**
     * 猫猫机的 Uplink Hatch 添加器
     * <p>
     * 复用父类 uplinkHatch 字段，上限1个，可替换任意 casing 方块
     */
    private boolean addUplinkHatchNeko(IGregTechTileEntity aBaseMetaTileEntity, int aBaseCasingIndex) {
        // 检查是否已有 uplinkHatch（上限1个）
        try {
            if (UPLINK_HATCH_FIELD.get(this) != null) {
                return false;
            }
        } catch (IllegalAccessException e) {
            GTInterestingThing.LOG.error("[NEKO] Failed to check uplinkHatch", e);
            return false;
        }
        if (aBaseMetaTileEntity == null) {
            return false;
        }
        IMetaTileEntity aMetaTileEntity = aBaseMetaTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) {
            return false;
        }
        if (!(aMetaTileEntity instanceof MTEVendingUplinkHatch)) {
            return false;
        }
        MTEVendingUplinkHatch uplinkHatch = (MTEVendingUplinkHatch) aMetaTileEntity;
        uplinkHatch.updateTexture(aBaseCasingIndex);
        uplinkHatch.updateCraftingIcon(uplinkHatch.getMachineCraftingIcon());
        // 反射设置父类 private uplinkHatch 字段
        try {
            UPLINK_HATCH_FIELD.set(this, uplinkHatch);
        } catch (IllegalAccessException e) {
            GTInterestingThing.LOG.error("[NEKO] Failed to set uplinkHatch", e);
            return false;
        }
        GTInterestingThing.LOG.info(
            "[NEKO] addUplinkHatchNeko: uplink hatch added at ({},{},{})",
            aBaseMetaTileEntity.getXCoord(),
            aBaseMetaTileEntity.getYCoord(),
            aBaseMetaTileEntity.getZCoord());
        return true;
    }

    /**
     * 猫猫机的材质渲染
     * <p>
     * 正面使用 VM 原版前面板材质，侧面使用 casing 材质
     */
    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        if (side == facing) {
            if (baseMetaTileEntity == null) {
                return NEKO_FACING_FRONT;
            }
            return active ? NEKO_FACING_ACTIVE : NEKO_FACING_FRONT;
        }
        return NEKO_FACING_SIDE;
    }

    /**
     * 猫猫机的覆盖材质设置
     * <p>
     * 使用猫猫机专属覆盖材质（3帧），其余复用 VM 原版材质
     */
    @Override
    protected void setTextureOverlay() {
        IGregTechTileEntity tile = this.getBaseMetaTileEntity();
        if (tile == null || tile.isServerSide()) {
            return;
        }

        IIconContainer[] nekoTextures = this.getBaseMetaTileEntity()
            .isActive() && this.usingAnimations() ? TextureManager.NEKOVM_OVERLAY_ACTIVE
                : TextureManager.NEKOVM_OVERLAY;

        setNekoVMOverlay(
            tile.getWorld(),
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            this.getExtendedFacing(),
            nekoTextures,
            this.overlayTickets);
    }

    /**
     * 猫猫机覆盖层渲染
     * <p>
     * 与 VM 原版的 OverlayHelper.setVMOverlay 类似，但使用猫猫机的偏移和材质
     */
    public static void setNekoVMOverlay(World world, int x, short y, int z, ExtendedFacing facing,
        IIconContainer[] nekoTextures, List<RenderOverlay.OverlayTicket> overlayTickets) {
        OverlayHelper.clearVMOverlay(overlayTickets);

        int[] tXYZOffset = new int[3];
        ForgeDirection tDirection = facing.getDirection();
        facing = ExtendedFacing.of(tDirection);
        gregtech.api.render.RenderOverlay overlay = gregtech.api.render.RenderOverlay.getOrCreate(world);

        for (int i = 0; i < 3; ++i) {
            int[] tABCCoord = new int[] { NEKO_VM_X[i], NEKO_VM_Y[i], 0 };
            facing.getWorldOffset(tABCCoord, tXYZOffset);
            int tX = tXYZOffset[0] + x;
            int tY = tXYZOffset[1] + y;
            int tZ = tXYZOffset[2] + z;

            // 所有覆盖都带发光效果
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

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        GTInterestingThing.LOG.info(
            "[NEKO] onRightclick: side={}, mMachine={}, player={}",
            aBaseMetaTileEntity.isServerSide() ? "server" : "client",
            this.mMachine,
            aPlayer.getCommandSenderName());
        boolean result = super.onRightclick(aBaseMetaTileEntity, aPlayer);
        GTInterestingThing.LOG.info("[NEKO] onRightclick result={}, getActive={}", result, this.getActive());
        return result;
    }

    @Override
    public boolean checkTrade(Trade trade, UUID player, WalletMode walletMode, boolean simulate) {
        for (Map.Entry<UUID, NekoTradeRegistry.NekoTradeInfo> entry : NekoTradeRegistry.NEKO_TRADES.entrySet()) {
            UUID tgId = entry.getKey();
            NekoTradeRegistry.NekoTradeInfo nekoInfo = entry.getValue();
            TradeGroup tg = TradeDatabase.INSTANCE.getTradeGroupFromId(tgId);

            if (tg == null) continue;

            for (int i = 0; i < tg.getTrades()
                .size(); i++) {
                Trade tgTrade = tg.getTrades()
                    .get(i);
                if (tgTrade == trade) {
                    if (nekoInfo.currencyId != null && nekoInfo.cost > 0) {
                        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(player);
                        if (wallet == null) {
                            GTInterestingThing.LOG.info("[NEKO] checkTrade: 猫猫币交易, 无钱包, simulate={}", simulate);
                            return false;
                        }
                        int balance = wallet.getCount(nekoInfo.currencyId);
                        boolean canAfford = balance >= nekoInfo.cost;
                        if (!canAfford) {
                            GTInterestingThing.LOG.info(
                                "[NEKO] checkTrade: 猫猫币余额不足, balance={}, cost={}, simulate={}",
                                balance,
                                nekoInfo.cost,
                                simulate);
                            return false;
                        }
                    }

                    if (trade.fromItems.isEmpty()) {
                        return true;
                    }
                    boolean superResult = super.checkTrade(trade, player, walletMode, simulate);
                    GTInterestingThing.LOG.info(
                        "[NEKO] checkTrade: 猫猫机交易(有fromItems), superResult={}, fromItems.size={}, simulate={}, currencyId={}",
                        superResult,
                        trade.fromItems.size(),
                        simulate,
                        nekoInfo.currencyId);
                    return superResult;
                }
            }
        }

        return super.checkTrade(trade, player, walletMode, simulate);
    }
}
