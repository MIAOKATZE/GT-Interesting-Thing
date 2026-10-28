package com.miaokatze.gtit.client.gui;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.RichTooltipEvent;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Opt-in tooltip palette: never recolors NEI, other mods or an unrelated vanilla item tooltip. */
public final class NekoVendingTooltip {

    private static final Set<RichTooltip> STYLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<RichTooltip> RECEIPTS = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean registered;

    private NekoVendingTooltip() {}

    public static RichTooltip apply(RichTooltip tooltip) {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(new NekoVendingTooltip());
            registered = true;
        }
        STYLED.add(tooltip);
        return tooltip.textColor(NekoVendingTheme.TOOLTIP_INK)
            .textShadow(false)
            .titleMargin(2);
    }

    /** A local receipt composed of ordinary RichTooltip drawables; no item-tooltip/global theme replacement. */
    public static void describeTrade(RichTooltip tooltip, NekoTradeItemDisplay trade) {
        tooltip.clearText();
        apply(tooltip).titleMargin(0);
        RECEIPTS.add(tooltip);
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution screen = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        boolean expanded = Interactable.hasShiftDown();
        int width = Math.max(96, Math.min(expanded ? 310 : 272, screen.getScaledWidth() - 28));
        row(tooltip, width, 28, (context, x, y, w, h, theme) -> {
            GuiDraw.drawRect(x, y, w, h, 0xff4e5941);
            GuiDraw.drawRect(x, y + h - 1, w, 1, 0xffa99a6f);
            GuiDraw.drawItem(trade.getDisplayStack(), x + 4, y + 4, 20, 20, context.getCurrentDrawingZ());
            text(trade.getDisplayItemName(), x + 30, y + 3, w - 36, 1f, 0xffffedc7);
            text("GT Interesting Thing  /  #" + trade.getOrderId(), x + 30, y + 16, w - 49, .75f, 0xffc6c9ac);
            text(trade.isFavourite() ? "★" : "☆", x + w - 14, y + 16, 12, .8f, 0xffffd083);
        });
        int statusColor = trade.isTradeable() && !trade.isBqLocked() && trade.getCooldownRemaining() == 0
            ? NekoVendingTheme.GREEN
            : NekoVendingTheme.DANGER;
        String status = trade.isOneTimeRedeemed() ? tr("redeemed")
            : trade.isBqLocked() ? tr("bq_locked")
                : trade.getCooldownRemaining() > 0 ? tr("cooldown", trade.getCooldownRemaining())
                    : tr(trade.isTradeable() ? "ready" : "unavailable");
        row(tooltip, width, 15, (context, x, y, w, h, theme) -> {
            GuiDraw.drawRect(x + 5, y + 5, 5, 5, statusColor);
            text(status, x + 15, y + 4, w - 21, .85f, statusColor);
        });
        // ComposedLine adds one pixel after each non-font-height icon (MUI2 2.3.92).
        int fixedHeight = 29 + 16 + 56 + 28;
        if (trade.isBqLocked() && trade.isOneTimeRedeemed()) {
            label(tooltip, width, tr("bq_locked"), NekoVendingTheme.DANGER, 12);
            fixedHeight += 13;
        }
        if (trade.isBqLocked() && trade.getCooldownRemaining() > 0) {
            label(tooltip, width, tr("cooldown", trade.getCooldownRemaining()), NekoVendingTheme.MUTED, 12);
            fixedHeight += 13;
        }
        if (trade.isOneTimeOnly()) {
            label(tooltip, width, tr(trade.isOneTimeRedeemed() ? "once_used" : "once"), statusColor, 12);
            fixedHeight += 13;
        } else {
            label(
                tooltip,
                width,
                tr(
                    "remaining",
                    Math.max(0, trade.getMaxTradesInCooldown() - trade.getUsedTradesInCooldown()),
                    trade.getMaxTradesInCooldown()),
                NekoVendingTheme.MUTED,
                12);
            fixedHeight += 13;
        }
        // Always reserve the exact wallet payments and both section summaries before spending space on item previews.
        // Reserve summaries for both lists even when no preview fits, and divide the rest by actual preview demand.
        int summaries = (trade.hasInputs() ? 13 : 0) + (trade.getOutputs()
            .isEmpty() ? 0 : 13);
        int budget = Math.max(
            0,
            screen.getScaledHeight() - 28
                - fixedHeight
                - summaries
                - trade.getCurrencyCosts()
                    .size() * 19
                - (trade.hasInputs() ? 13 : 0)
                - (!trade.hasInputs() && !trade.hasCurrencyCost() ? 13 : 0));
        int inputHeight = previewHeight(trade.getInputs(), expanded);
        int outputHeight = previewHeight(trade.getOutputs(), expanded);
        int inputBudget = trade.hasInputs() ? 13 + budget * inputHeight / Math.max(1, inputHeight + outputHeight) : 0;
        section(tooltip, width, tr("requires", trade.getDisplayedRequirementCount()), tr("and"), 0xffaa8138);
        for (Map.Entry<String, Long> currency : trade.getCurrencyCosts()
            .entrySet()) {
            item(
                tooltip,
                width,
                NekoCurrencyRegistrar.getItemStack(currency.getKey(), 1),
                currency.getValue(),
                NekoCurrencyRegistrar.getDisplayName(currency.getKey()) + " · " + tr("wallet"),
                0xffaa8138);
        }
        if (trade.hasInputs()) {
            label(tooltip, width, tr(trade.isRecordNBT() ? "nbt_exact" : "nbt_ignored"), NekoVendingTheme.MUTED, 12);
        }
        int spent = choices(tooltip, width, trade.getInputs(), false, expanded, inputBudget);
        if (!trade.hasInputs() && !trade.hasCurrencyCost())
            label(tooltip, width, tr("free"), NekoVendingTheme.MUTED, 12);
        section(
            tooltip,
            width,
            tr(
                "outputs",
                trade.getOutputs()
                    .size()),
            tr("independent"),
            0xff61704d);
        choices(tooltip, width, trade.getOutputs(), true, expanded, 13 + budget - Math.max(0, spent - 13));
        row(tooltip, width, 27, (context, x, y, w, h, theme) -> {
            GuiDraw.drawRect(x, y, w, h, 0xffdfd5ba);
            GuiDraw.drawRect(x, y, w, 1, 0xffc6b796);
            text(tr("view") + "  ·  " + tr("trade"), x + 5, y + 4, w - 10, .8f, NekoVendingTheme.INK);
            text(
                tr(expanded ? "expanded" : "expand") + "  ·  " + tr(trade.isFavourite() ? "unfavourite" : "favourite"),
                x + 5,
                y + 16,
                w - 10,
                .75f,
                NekoVendingTheme.MUTED);
        });
    }

    private static int choices(RichTooltip tooltip, int width, List<NekoBigItemStack> slots, boolean output,
        boolean expanded, int budget) {
        int spent = 0;
        int accent = output ? NekoVendingTheme.GREEN : NekoVendingTheme.GOLD;
        for (int i = 0; i < slots.size(); i++) {
            NekoBigItemStack slot = slots.get(i);
            List<NekoBigItemStack> options = slot.getOptions();
            int remainingSlots = slots.size() - i - 1;
            // The final summary is part of the height budget; never silently clip additional AND slots.
            if (budget - spent < 31 + (options.size() > 1 ? 13 : 0) + (remainingSlots > 0 ? 13 : 0)) {
                label(tooltip, width, tr(output ? "more_outputs" : "more_inputs", slots.size() - i), accent, 12);
                return spent + 13;
            }
            label(
                tooltip,
                width,
                tr(options.size() > 1 ? output ? "random" : "choice" : "slot", i + 1, options.size()),
                accent,
                11);
            spent += 12;
            int limit = expanded ? options.size() : Math.min(3, options.size());
            int shown = 0;
            while (shown < limit
                && budget - spent >= 19 + (shown + 1 < options.size() ? 13 : 0) + (remainingSlots > 0 ? 13 : 0)) {
                NekoBigItemStack option = options.get(shown++);
                String name = option.hasOreDict() ? tr(output ? "ore_output" : "ore_input", option.getOreDict())
                    : option.getBaseStack()
                        .getDisplayName();
                item(tooltip, width, option.getBaseStack(), option.getStackSize(), name, accent);
                spent += 19;
            }
            if (shown < options.size()) {
                label(tooltip, width, tr("more_options", options.size() - shown), NekoVendingTheme.MUTED, 12);
                spent += 13;
            }
        }
        return spent;
    }

    private static int previewHeight(List<NekoBigItemStack> slots, boolean expanded) {
        int height = 0;
        for (NekoBigItemStack slot : slots) {
            int options = 1 + slot.getAlternatives()
                .size();
            height += 12 + (expanded ? options : Math.min(3, options)) * 19 + (!expanded && options > 3 ? 13 : 0);
        }
        return height;
    }

    private static void section(RichTooltip tooltip, int width, String title, String rule, int accent) {
        row(tooltip, width, 15, (context, x, y, w, h, theme) -> {
            GuiDraw.drawRect(x, y, w, h, 0xffdbcfb1);
            GuiDraw.drawRect(x, y, 3, h, accent);
            text(title, x + 7, y + 3, w - 14, .85f, NekoVendingTheme.INK);
        });
        label(tooltip, width, rule, NekoVendingTheme.MUTED, 11);
    }

    private static void item(RichTooltip tooltip, int width, ItemStack stack, long quantity, String name, int accent) {
        row(tooltip, width, 18, (context, x, y, w, h, theme) -> {
            NekoVendingTheme.SLOT.draw(context, x + 5, y + 1, 16, 16, theme);
            GuiDraw.drawItem(stack, x + 5, y + 1, 16, 16, context.getCurrentDrawingZ());
            String amount = "× " + quantity;
            int amountWidth = (int) Math.ceil(Minecraft.getMinecraft().fontRenderer.getStringWidth(amount) * .85f);
            text(amount, x + w - amountWidth - 5, y + 5, amountWidth, .85f, accent);
            text(name, x + 26, y + 5, w - amountWidth - 37, .85f, NekoVendingTheme.INK);
        });
    }

    private static void label(RichTooltip tooltip, int width, String text, int color, int height) {
        row(
            tooltip,
            width,
            height,
            (context, x, y, w, h, theme) -> text(text, x + 6, y + (h - 7) / 2, w - 12, .8f, color));
    }

    private static void row(RichTooltip tooltip, int width, int height, IDrawable content) {
        IDrawable paper = (context, x, y, w, h, theme) -> {
            GuiDraw.drawRect(x, y, w, h, 0xffeee4ca);
            content.draw(context, x, y, w, h, theme);
        };
        tooltip.addLine(
            paper.asIcon()
                .size(width, height));
    }

    private static void text(String text, int x, int y, int width, float scale, int color) {
        String fitted = NekoTextFit.fit(
            EnumChatFormatting.getTextWithoutFormattingCodes(text),
            width,
            scale,
            Minecraft.getMinecraft().fontRenderer::getStringWidth);
        GuiDraw.drawText(fitted, x, y, scale, color, false);
    }

    private static String tr(String key, Object... args) {
        return I18n.format("gtit.trade.tooltip." + key, args);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void color(RichTooltipEvent.Color event) {
        if (!STYLED.contains(event.getTooltip())) return;
        if (RECEIPTS.contains(event.getTooltip())) {
            event.setBackground(0xffeee4ca);
            event.setBorderStart(0xffb09a70);
            event.setBorderEnd(0xff8b8764);
            return;
        }
        event.setBackground(0xf71d2e26);
        event.setBorderStart(0xff9b865c);
        event.setBorderEnd(0xff727957);
    }
}
