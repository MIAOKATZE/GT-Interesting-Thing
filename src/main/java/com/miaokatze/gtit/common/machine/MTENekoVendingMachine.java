package com.miaokatze.gtit.common.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cubefury.vendingmachine.blocks.MTEVendingMachine;
import com.cubefury.vendingmachine.blocks.gui.MTEVendingMachineGui;
import com.cubefury.vendingmachine.blocks.gui.WalletMode;
import com.cubefury.vendingmachine.trade.Trade;
import com.cubefury.vendingmachine.util.BigItemStack;
import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.miaokatze.gtit.common.machine.neko.NekoVendingMachineGui;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.NekoTradeRegistry;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;

import gregtech.api.interfaces.ISecondaryDescribable;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 猫猫售货机
 * <p>
 * 继承自 VM (VendingMachine) 模组的 {@link MTEVendingMachine}，保留原版的多方块结构、动画与交易处理逻辑，
 * 仅覆盖 GUI 与 Tooltip 显示。当前阶段：
 * <ul>
 * <li>GUI 返回自定义 {@link NekoVendingMachineGui}，完全重建 UI（仅显示猫猫币、自动入袋、不播 BGM）</li>
 * <li>交易逻辑暂时直接委托给父类 {@link #checkTrade}，后续阶段实现猫猫币交易</li>
 * <li>{@link #isBuildingNekoGui} 静态标志已废弃，BGM 由 NekoMusicEventHandler + NekoVendingMachineGui.isNekoGuiOpen 方案替代</li>
 * </ul>
 * <p>
 * 结构与父类一致：2x3x1 的 "cc", "c~", "cc" 形状，使用 Tin Item Pipe Casings 作为外壳。
 */
public class MTENekoVendingMachine extends MTEVendingMachine
    implements ISurvivalConstructable, ISecondaryDescribable, IAlignment {

    /**
     * BGM 构建阶段标志位（已废弃）
     * <p>
     * 原设计用于 Mixin 拦截原版 VMMusicManager 的 BGM 播放，
     * 但 BGM 系统已改为 NekoMusicEventHandler + NekoVendingMachineGui.isNekoGuiOpen 方案，
     * 此字段不再使用。保留以避免序列化兼容性问题。
     *
     * @deprecated 使用 {@link NekoVendingMachineGui#isNekoGuiOpen} 替代
     */
    @Deprecated
    public static boolean isBuildingNekoGui = false;

    /**
     * 注册用构造函数
     * <p>
     * 由 {@link com.miaokatze.gtit.register.MultiblockMachineRegistrar} 调用，用于注册新的机器类型。
     *
     * @param aID           机器的全局唯一 ID
     * @param aName         机器的内部名称
     * @param aNameRegional 机器的区域显示名称
     */
    public MTENekoVendingMachine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    /**
     * 实例化用构造函数
     * <p>
     * 由 {@link #newMetaEntity} 调用，用于创建机器的实际运行实例。
     *
     * @param aName 机器的内部名称
     */
    public MTENekoVendingMachine(String aName) {
        super(aName);
    }

    /**
     * 创建新的元机器实体实例
     * <p>
     * GregTech 在放置机器方块时调用此方法，返回一个新的 {@link MTENekoVendingMachine} 实例。
     *
     * @param aTileEntity 关联的 GregTech TileEntity
     * @return 新的猫猫售货机实例
     */
    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTENekoVendingMachine(this.mName);
    }

    /**
     * 返回猫猫售货机的 GUI
     * <p>
     * 返回自定义的 {@link NekoVendingMachineGui}，该 GUI 完全覆盖父类的 build() 方法，
     * 重建 UI 布局：仅显示 2 种猫猫币图标（neko / shimmeringNeko），输入槽中的猫猫币自动进入 NekoWallet，
     * "弹出全部硬币" 按钮替换为弹出全部猫猫币，且不播放原版 BGM。
     * 交易列表、输入槽、输出槽、FallingItemSlotFactory 动画效果均保留。
     * <p>
     * BGM 系统由 {@link com.miaokatze.gtit.common.machine.neko.NekoMusicEventHandler} 负责，
     * 通过 {@link NekoVendingMachineGui#isNekoGuiOpen} 标志位检测 GUI 状态。
     *
     * @return 自定义猫猫售货机 GUI 实例
     */
    @Override
    protected MTEVendingMachineGui getGui() {
        return new NekoVendingMachineGui(this);
    }

    /**
     * 构建猫猫售货机的 Tooltip 信息
     * <p>
     * 在原版售货机 Tooltip 基础上，添加猫猫售货机的专属说明信息。
     *
     * @return 包含猫猫售货机信息的 Tooltip 构建器
     */
    @Override
    protected MultiblockTooltipBuilder getTooltip() {
        MultiblockTooltipBuilder tooltipBuilder = super.getTooltip();
        if (tooltipBuilder != null) {
            // 在原版 Tooltip 之后追加猫猫售货机的专属信息
            tooltipBuilder.addInfo("喵~ 猫猫售货机，用猫猫币购买物品！");
            tooltipBuilder.addInfo("结构、动画与交易逻辑继承自原版 VM 售货机。");
        }
        return tooltipBuilder;
    }

    /**
     * 交易校验入口（猫猫币版本）
     * <p>
     * 对猫猫币交易：检查 NekoWallet 余额，扣款走 NekoWallet
     * 对非猫猫币交易：委托给父类（原版逻辑）
     */
    @Override
    public boolean checkTrade(Trade trade, UUID player, WalletMode walletMode, boolean simulate) {
        // 检查是否为猫猫币交易：遍历 trade.fromItems 查找猫猫币物品
        NekoTradeRegistry.NekoTradeInfo nekoInfo = findNekoTradeInfo(trade);
        if (nekoInfo != null) {
            return checkNekoTrade(trade, player, nekoInfo, simulate);
        }
        // 非猫猫币交易：委托给父类
        return super.checkTrade(trade, player, walletMode, simulate);
    }

    /**
     * 查找交易中的猫猫币信息
     * <p>
     * 遍历 trade.fromItems，如果包含猫猫币物品，返回对应的 NekoTradeInfo
     */
    private NekoTradeRegistry.NekoTradeInfo findNekoTradeInfo(Trade trade) {
        for (BigItemStack fromItem : trade.fromItems) {
            String currencyId = NekoCurrencyRegistrar.getNekoCurrencyId(fromItem.getBaseStack());
            if (currencyId != null) {
                // 这是猫猫币交易，构造 NekoTradeInfo
                return new NekoTradeRegistry.NekoTradeInfo(currencyId, fromItem.stackSize);
            }
        }
        return null;
    }

    /**
     * 执行猫猫币交易校验
     * <p>
     * 检查 NekoWallet 余额是否足够，如果足够且非模拟则扣款并输出物品
     */
    private boolean checkNekoTrade(Trade trade, UUID player, NekoTradeRegistry.NekoTradeInfo nekoInfo,
        boolean simulate) {
        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(player);
        if (wallet == null) return false;

        int balance = wallet.getCount(nekoInfo.currencyId);
        if (balance < nekoInfo.cost) return false;

        if (!simulate) {
            // 扣除猫猫币
            wallet.addCount(nekoInfo.currencyId, -nekoInfo.cost);
            NekoWalletManager.INSTANCE.saveWallet(player);
            // 输出物品
            List<net.minecraft.item.ItemStack> toDispense = new ArrayList<>();
            for (BigItemStack toItem : trade.toItems) {
                toDispense.addAll(toItem.getCombinedStacks());
            }
            this.dispenseItemStacks(toDispense);
            this.playSoundEffect("vendingmachine:coin_insert");
        }
        return true;
    }
}
