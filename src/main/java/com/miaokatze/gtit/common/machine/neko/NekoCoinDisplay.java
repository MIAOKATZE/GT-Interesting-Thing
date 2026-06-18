package com.miaokatze.gtit.common.machine.neko;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cubefury.vendingmachine.gui.GuiTextures;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;

/**
 * 猫猫币余额显示组件
 * <p>
 * 类似 VM 的 CoinDisplay，但简化为只显示猫猫币图标和余额。
 * 点击弹出按钮可以弹出该种猫猫币。
 */
public class NekoCoinDisplay extends Flow {

    private final IntSyncValue coinSyncValue;

    /**
     * 构造猫猫币显示组件
     *
     * @param syncManager 同步管理器
     * @param currencyId  猫猫币 ID（"neko" 或 "shimmeringNeko"）
     * @param displayName 显示名称（如"猫猫币"）
     */
    public NekoCoinDisplay(PanelSyncManager syncManager, String currencyId, String displayName) {
        super(GuiAxis.X);
        this.coinSyncValue = (IntSyncValue) syncManager
            .findSyncHandler("nekoCoinAmount_" + currencyId, 0, IntSyncValue.class);

        // 猫猫币图标（纯展示）
        ItemStack coinStack = NekoCurrencyRegistrar.getItemStack(currencyId, 1);
        if (coinStack == null) {
            // 猫猫币物品未初始化时使用安全占位
            coinStack = new net.minecraft.item.ItemStack(net.minecraft.init.Items.coal, 1, 1);
        }
        ItemDisplayWidget iconWidget = new ItemDisplayWidget().item(coinStack)
            .size(16);

        // 余额数字
        TextWidget<?> amountText = IKey.dynamic(() -> getReadableString(this.coinSyncValue.getValue()))
            .scale(0.8f)
            .asWidget()
            .top(3)
            .left(18)
            .width(21);

        // 弹出按钮（点击弹出该种猫猫币）
        ToggleButton ejectButton = new ToggleButton().overlay(
            new IDrawable[] { GuiTextures.EJECT_COINS.asIcon()
                .size(12) })
            .size(12)
            .left(40)
            .syncHandler("nekoEjectCoin_" + currencyId)
            .tooltipDynamic(builder -> {
                builder.clearText();
                builder.addLine(this.coinSyncValue.getValue() + " " + displayName);
                builder.emptyLine();
                builder.addLine(
                    IKey.str("点击弹出该种猫猫币")
                        .style(new EnumChatFormatting[] { IKey.GRAY, IKey.ITALIC }));
                builder.setAutoUpdate(true);
            });

        this.child(iconWidget)
            .child(amountText)
            .child(ejectButton)
            .height(16)
            .width(54);
    }

    /**
     * 将金额转换为可读字符串（如 10000 -> 10K）
     */
    private static String getReadableString(int amount) {
        if (amount < 10000) {
            return "" + amount;
        }
        if (amount < 1000000) {
            return amount / 1000 + "K";
        }
        return amount / 1000000 + "M";
    }
}
