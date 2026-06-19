package com.miaokatze.gtit.common.machine.neko;

import net.minecraft.item.ItemStack;

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
 * 猫猫币余额显示组件（无容器版，带弹出按钮）
 * <p>
 * 图标(22px) + 余额数字 + 弹出按钮(与图标并列)。
 * 无容器边框，使用 background(new IDrawable[0]) 隐藏物品槽背景。
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

        // 猫猫币图标（22px，无背景）
        ItemStack coinStack = NekoCurrencyRegistrar.getItemStack(currencyId, 1);
        if (coinStack == null) {
            coinStack = new net.minecraft.item.ItemStack(net.minecraft.init.Items.coal, 1, 1);
        }
        ItemDisplayWidget iconWidget = new ItemDisplayWidget().item(coinStack)
            .size(22)
            .background(new IDrawable[0]);

        // 余额数字
        TextWidget<?> amountText = IKey.dynamic(() -> getReadableString(this.coinSyncValue.getValue()))
            .scale(0.75f)
            .asWidget()
            .top(6)
            .left(24)
            .width(24);

        // 弹出按钮（与图标并列，使用原版弹出硬币图标）
        ToggleButton ejectButton = new ToggleButton();
        ejectButton.size(12);
        ejectButton.disableThemeBackground(true);
        ejectButton.disableHoverThemeBackground(true);
        ejectButton.overlay(
            new IDrawable[] { GuiTextures.EJECT_COINS.asIcon()
                .size(12) });
        ejectButton.syncHandler("nekoEjectCoin_" + currencyId);
        ejectButton.tooltipBuilder(builder -> { builder.addLine(displayName + " 弹出"); });

        this.child(iconWidget)
            .child(amountText)
            .child(
                ejectButton.left(48)
                    .top(5))
            .height(22)
            .width(60);
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
