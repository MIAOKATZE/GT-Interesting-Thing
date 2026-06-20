package com.miaokatze.gtit.mixin;

import java.util.UUID;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplay;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplayWidget;
import com.miaokatze.gtit.trade.NekoTradeRegistry;

/**
 * Mixin 拦截 TradeItemDisplayWidget.draw() 渲染
 * <p>
 * 实现 BQ 锁定交易的客户端显示：
 * - BQ 锁定交易：显示冷却遮罩 + 金色 "LOCKED" 文字
 * - 冷却交易（猫猫币）：青色冷却时间文字
 * - 非猫猫币交易：原样渲染
 * <p>
 * 使用 Minecraft 颜色代码（§6=金色，§b=青色）嵌入 cooldownText，
 * 利用 FontRenderer 对颜色代码的原生支持，无需拦截 IKey.color()。
 */
@Mixin(TradeItemDisplayWidget.class)
public class MixinTradeItemDisplayWidget {

    static {
        System.out.println("[NEKO-MIXIN] MixinTradeItemDisplayWidget 静态初始化器已执行（§代码方案）");
    }

    @Shadow(remap = false)
    private TradeItemDisplay display;

    // BQ 锁定状态（仅在 draw() 执行期间有效）
    private boolean nekoBqLocked = false;
    // 猫猫币冷却状态
    private boolean nekoCooldown = false;
    // 原始值备份
    private boolean nekoOriginalHasCooldown = false;
    private String nekoOriginalCooldownText = null;

    @Inject(method = "draw", at = @At("HEAD"), remap = false)
    private void onDrawHead(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme, CallbackInfo ci) {
        nekoBqLocked = false;
        nekoCooldown = false;

        if (display == null || display.tgID == null) {
            return;
        }
        if (!NekoTradeRegistry.isNekoTradeGroup(display.tgID)) {
            return;
        }

        // 获取客户端玩家 UUID
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        UUID playerId = mc.thePlayer.getUniqueID();

        if (NekoTradeRegistry.isTradeBqLocked(display.tgID, playerId)) {
            // BQ 锁定：临时修改 display 字段以触发冷却遮罩 + 金色 LOCKED
            nekoBqLocked = true;
            nekoOriginalHasCooldown = display.hasCooldown;
            nekoOriginalCooldownText = display.cooldownText;
            display.hasCooldown = true;
            display.cooldownText = "\u00A76LOCKED"; // §6 = 金色
        } else if (display.hasCooldown && display.cooldownText != null && !display.cooldownText.isEmpty()) {
            // 猫猫币交易冷却中：青色冷却时间
            nekoCooldown = true;
            nekoOriginalCooldownText = display.cooldownText;
            display.cooldownText = "\u00A7b" + display.cooldownText; // §b = 青色
        }
    }

    @Inject(method = "draw", at = @At("RETURN"), remap = false)
    private void onDrawReturn(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme, CallbackInfo ci) {
        if (nekoBqLocked && display != null) {
            display.hasCooldown = nekoOriginalHasCooldown;
            display.cooldownText = nekoOriginalCooldownText;
        } else if (nekoCooldown && display != null) {
            display.cooldownText = nekoOriginalCooldownText;
        }
        nekoBqLocked = false;
        nekoCooldown = false;
    }
}
