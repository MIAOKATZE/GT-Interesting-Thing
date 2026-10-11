package com.miaokatze.gtit.client.nei;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;

import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoBqQuestIdParser;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeCatalyst;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** One recipe per synchronized trade group, with bounded internal pages for every exchange and slot. */
public final class NekoTradeRecipeHandler extends TemplateRecipeHandler {

    public static final String ID = "gtit.neko_trade";
    public static final int HEIGHT = 150;
    private static final Rectangle PREVIOUS = new Rectangle(2, 110, 20, 12);
    private static final Rectangle NEXT = new Rectangle(144, 110, 20, 12);
    private static final Rectangle QUEST = new Rectangle(2, 134, 162, 12);
    private long databaseVersion = Long.MIN_VALUE;
    private ItemStack query;
    private boolean outputQuery;
    private boolean active;

    @Override
    public String getRecipeName() {
        return text("name");
    }

    @Override
    public String getOverlayIdentifier() {
        return ID;
    }

    @Override
    public String getGuiTexture() {
        return "minecraft:textures/gui/container/crafting_table.png";
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if (ID.equals(outputId)) load(null, false);
        else super.loadCraftingRecipes(outputId, results);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        load(result, true);
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        load(ingredient, false);
    }

    private void load(ItemStack item, boolean output) {
        query = item == null ? null : item.copy();
        outputQuery = output;
        active = true;
        rebuild();
    }

    private void rebuild() {
        long version = NekoTradeDatabase.INSTANCE.getVersion();
        arecipes.clear();
        for (NekoTradeGroup group : NekoTradeNeiData.groups()) {
            if (query != null) {
                boolean matches = false;
                for (NekoTrade trade : group.getTrades()) {
                    if (NekoTradeNeiData.matches(trade, query, outputQuery)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) continue;
            }
            arecipes.add(new CachedGroup(group));
        }
        databaseVersion = version;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (active && databaseVersion != NekoTradeDatabase.INSTANCE.getVersion()) {
            rebuild();
            if (Minecraft.getMinecraft().currentScreen instanceof GuiRecipe) {
                ((GuiRecipe<?>) Minecraft.getMinecraft().currentScreen).forceRefreshPage();
            }
        }
    }

    @Override
    public void drawBackground(int recipeIndex) {
        Gui.drawRect(0, 0, 166, HEIGHT, 0xFFE4E4E4);
        for (int side = 0; side < 2; side++) for (int slot = 0; slot < 12; slot++) {
            int x = (side == 0 ? 3 : 93) + slot % 4 * 18;
            int y = 40 + slot / 4 * 22;
            Gui.drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF888888);
            Gui.drawRect(x, y, x + 16, y + 16, 0xFFBBBBBB);
        }
    }

    @Override
    public void drawExtras(int recipeIndex) {
        if (recipeIndex >= arecipes.size()) return;
        CachedGroup recipe = (CachedGroup) arecipes.get(recipeIndex);
        NekoTradeGroup group = recipe.group;
        NekoTrade trade = recipe.trade();
        line(text("group", NekoPageRegistry.getPageName(group.getTabId()), group.getOrderId()), 0);
        line(
            text(
                "interval",
                group.getCooldown() < 0 ? text("none") : text("time", group.getCooldown() / 20.0, group.getCooldown())),
            10);
        line(text("limit", group.getMaxTrades() < 0 ? text("unlimited") : group.getMaxTrades()), 20);
        line(text(trade.isRecordNBT() ? "strict" : "fuzzy"), 30);
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        font.drawString(" > ", 75, 63, 0x444444, false);
        font.drawString("[<]", 2, 111, 0x2255AA, false);
        font.drawString("[>]", 144, 111, 0x2255AA, false);
        String pages = text("page", recipe.pageIndex + 1, recipe.pages.size());
        font.drawString(pages, (166 - font.getStringWidth(pages)) / 2, 111, 0x444444, false);
        line(
            text(
                "exchange",
                recipe.page().tradeIndex + 1,
                group.getTrades()
                    .size()),
            123);
        line(text("quest", NekoTradeBqClient.title(recipe.questId)), 135);
    }

    private static void line(String value, int y) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String clipped = font.trimStringToWidth(value, 162);
        font.drawString(clipped, 2, y, 0x333333, false);
    }

    private static String text(String key, Object... values) {
        return StatCollector.translateToLocalFormatted("gtit.nei.trade." + key, values);
    }

    private static Point mouse(GuiRecipe<?> gui, int recipeIndex) {
        GuiRecipeCatalyst catalyst = gui.getRecipeCatalystWidget();
        Minecraft minecraft = Minecraft.getMinecraft();
        // Same scaled-coordinate conversion as CCL GuiDraw, including the bottom-origin Y correction.
        Point position = new Point(
            Mouse.getX() * gui.width / minecraft.displayWidth,
            gui.height - Mouse.getY() * gui.height / minecraft.displayHeight - 1);
        Point offset = gui.getRecipePosition(recipeIndex);
        return new Point(
            position.x - (catalyst.x + catalyst.w - 6) - offset.x,
            position.y - (catalyst.y + 9) - offset.y);
    }

    @Override
    public boolean mouseClicked(GuiRecipe<?> gui, int button, int recipeIndex) {
        if (button == 0 && recipeIndex < arecipes.size()) {
            CachedGroup recipe = (CachedGroup) arecipes.get(recipeIndex);
            Point position = mouse(gui, recipeIndex);
            if (PREVIOUS.contains(position) || NEXT.contains(position)) {
                recipe.pageIndex = Math
                    .floorMod(recipe.pageIndex + (NEXT.contains(position) ? 1 : -1), recipe.pages.size());
                recipe.buildStacks();
                gui.forceRefreshPage();
                return true;
            }
            if (QUEST.contains(position)) return NekoTradeBqClient.open(recipe.questId);
        }
        return super.mouseClicked(gui, button, recipeIndex);
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> tooltip, int recipeIndex) {
        if (recipeIndex < arecipes.size()) {
            CachedGroup recipe = (CachedGroup) arecipes.get(recipeIndex);
            Point position = mouse(gui, recipeIndex);
            if (QUEST.contains(position)) tooltip.add(text("quest", NekoTradeBqClient.title(recipe.questId)));
            if (PREVIOUS.contains(position) || NEXT.contains(position)) tooltip.add(text("paging_hint"));
            if (new Rectangle(0, 0, 166, 10).contains(position)) {
                tooltip.add(
                    text("group", NekoPageRegistry.getPageName(recipe.group.getTabId()), recipe.group.getOrderId()));
                tooltip.add(
                    recipe.group.getId()
                        .toString());
            }
        }
        return super.handleTooltip(gui, tooltip, recipeIndex);
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> tooltip, int recipeIndex) {
        if (recipeIndex < arecipes.size()) {
            CachedGroup recipe = (CachedGroup) arecipes.get(recipeIndex);
            for (TradeSlot slot : recipe.slots) if (gui.isMouseOver(slot, recipeIndex)) {
                tooltip.add(text(slot.output ? "output_hint" : "input_hint"));
                for (NekoBigItemStack option : slot.source.getOptions()) {
                    tooltip.add(
                        option.getStackSize() + " x "
                            + (option.hasOreDict() ? option.getOreDict()
                                : option.getBaseStack()
                                    .getDisplayName()));
                }
                break;
            }
        }
        return super.handleItemTooltip(gui, stack, tooltip, recipeIndex);
    }

    private static final class TradeSlot extends PositionedStack {

        final NekoBigItemStack source;
        final boolean output;

        TradeSlot(NekoBigItemStack source, boolean output, int index) {
            super(
                NekoTradeNeiData.displayStacks(source, output),
                (output ? 93 : 3) + index % 4 * 18,
                40 + index / 4 * 22);
            this.source = source;
            this.output = output;
        }
    }

    public final class CachedGroup extends CachedRecipe {

        final NekoTradeGroup group;
        final UUID questId;
        final List<NekoTradePages.Page> pages;
        final List<TradeSlot> slots = new ArrayList<>();
        final List<PositionedStack> inputs = new ArrayList<>();
        final List<PositionedStack> outputs = new ArrayList<>();
        int pageIndex;

        CachedGroup(NekoTradeGroup group) {
            this.group = group;
            questId = NekoBqQuestIdParser.parse(group.getBqQuestId());
            int[] inputCounts = new int[group.getTrades()
                .size()];
            int[] outputCounts = new int[inputCounts.length];
            for (int index = 0; index < inputCounts.length; index++) {
                NekoTrade trade = group.getTrades()
                    .get(index);
                inputCounts[index] = trade.getFromItems()
                    .size();
                outputCounts[index] = trade.getToItems()
                    .size();
            }
            pages = NekoTradePages.create(inputCounts, outputCounts);
            pageIndex = NekoTradeNeiData.queryPage(group, pages, query, outputQuery);
            buildStacks();
        }

        NekoTradePages.Page page() {
            return pages.get(pageIndex);
        }

        NekoTrade trade() {
            return group.getTrades()
                .get(page().tradeIndex);
        }

        void buildStacks() {
            slots.clear();
            inputs.clear();
            outputs.clear();
            addSlots(trade().getFromItems(), false, inputs);
            addSlots(trade().getToItems(), true, outputs);
        }

        private void addSlots(List<NekoBigItemStack> side, boolean output, List<PositionedStack> destination) {
            for (int slot = page().slotOffset; slot < Math.min(side.size(), page().slotOffset + 12); slot++) {
                TradeSlot positioned = new TradeSlot(side.get(slot), output, slot - page().slotOffset);
                slots.add(positioned);
                destination.add(positioned);
            }
        }

        @Override
        public PositionedStack getResult() {
            return null;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return getCycledIngredients(cycleticks / 20, inputs);
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return getCycledIngredients(cycleticks / 20, outputs);
        }
    }
}
