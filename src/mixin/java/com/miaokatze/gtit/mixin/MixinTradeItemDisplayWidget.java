package com.miaokatze.gtit.mixin;

import java.util.UUID;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplay;
import com.cubefury.vendingmachine.blocks.gui.TradeItemDisplayWidget;
import com.miaokatze.gtit.trade.NekoTradeRegistry;

/**
 * Mixin 拦截 TradeItemDisplayWidget.draw() 渲染
 * <p>
 * 实现 BQ 锁定交易的客户端显示：
 * - BQ 锁定交易：显示冷却遮罩 + 橙色 "LOCKED" 文字
 * - 冷却交易（猫猫币）：青色冷却时间文字
 * - 非猫猫币交易：原样渲染
 */
@Mixin(TradeItemDisplayWidget.class)
public class MixinTradeItemDisplayWidget {

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

        if (display == null || display.tgID == null) return;
        if (!NekoTradeRegistry.isNekoTradeGroup(display.tgID)) return;

        // 获取客户端玩家 UUID
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) return;
        UUID playerId = mc.thePlayer.getUniqueID();

        // 检查 BQ 锁定状态
        if (NekoTradeRegistry.isTradeBqLocked(display.tgID, playerId)) {
            // BQ 锁定：临时修改 display 字段以触发冷却遮罩
            nekoBqLocked = true;
            nekoOriginalHasCooldown = display.hasCooldown;
            nekoOriginalCooldownText = display.cooldownText;
            display.hasCooldown = true;
            display.cooldownText = "LOCKED";
        } else if (display.hasCooldown) {
            // 猫猫币交易冷却中
            nekoCooldown = true;
        }
    }

    @Redirect(
        method = "draw",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cleanroommc/modularui/api/drawable/IKey;color(I)Lcom/cleanroommc/modularui/api/drawable/IKey;"),
        remap = false)
    private IKey redirectNekoColor(IKey instance, int color) {
        if (nekoBqLocked) {
            return instance.color(0xFFFFA500); // 橙色 ARGB
        }
        if (nekoCooldown) {
            return instance.color(0xFF00FFFF); // 青色 ARGB
        }
        return instance.color(color); // 原始颜色
    }

    @Inject(method = "draw", at = @At("RETURN"), remap = false)
    private void onDrawReturn(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme, CallbackInfo ci) {
        if (nekoBqLocked && display != null) {
            display.hasCooldown = nekoOriginalHasCooldown;
            display.cooldownText = nekoOriginalCooldownText;
        }
        nekoBqLocked = false;
        nekoCooldown = false;
    }
}
