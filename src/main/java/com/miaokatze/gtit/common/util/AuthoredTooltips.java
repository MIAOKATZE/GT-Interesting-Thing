// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable;

/** Shared authored pages; item classes supply only live state and keep their legacy text as fallback. */
public final class AuthoredTooltips {

    private static final Map<String, JsonObject> CATALOGS = new HashMap<>();

    private AuthoredTooltips() {}

    private static synchronized JsonObject catalog() {
        String locale = StatCollector.translateToLocal("gtit.tooltip.authored_locale");
        if (!"zh_CN".equals(locale)) locale = "en_US";
        if (CATALOGS.containsKey(locale)) return CATALOGS.get(locale);
        String resource = "/assets/gtit/tooltip/authored_" + locale + ".json";
        InputStream stream = AuthoredTooltips.class.getResourceAsStream(resource);
        if (stream == null) return null;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject result = new JsonParser().parse(reader)
                .getAsJsonObject();
            CATALOGS.put(locale, result);
            return result;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read tooltip catalog: " + resource, e);
        }
    }

    public static boolean append(ItemStack stack, List<String> lines, String id) {
        return append(stack, lines, id, (binding, page) -> {});
    }

    public static boolean append(ItemStack stack, List<String> lines, String id,
        BiConsumer<String, NativeTooltipPage> dynamic) {
        NativeTooltipPage page = page(stack, id, dynamic);
        return page != null && page.appendTo(stack, lines);
    }

    public static NativeTooltipPage page(ItemStack stack, String id) {
        return page(stack, id, (binding, page) -> {});
    }

    public static NativeTooltipPage page(ItemStack stack, String id, BiConsumer<String, NativeTooltipPage> dynamic) {
        JsonObject catalog = catalog();
        if (catalog == null) return null;
        JsonObject aliases = catalog.getAsJsonObject("aliases");
        if (aliases != null && aliases.has(id)) id = aliases.get(id)
            .getAsString();
        JsonObject items = catalog.getAsJsonObject("items");
        if (items == null || !items.has(id)) return null;
        NativeTooltipPage page = NativeTooltipPage.forItem(stack)
            .twoTiers();
        String tier = page.tier() == NativeTooltipPage.Tier.QUICK ? "quick" : "common";
        for (JsonElement value : items.getAsJsonObject(id)
            .getAsJsonArray(tier)) {
            JsonObject block = value.getAsJsonObject();
            switch (block.get("type")
                .getAsString()) {
                case "line":
                    page.input(
                        block.get("text")
                            .getAsString());
                    break;
                case "warning":
                    page.warning(
                        block.get("text")
                            .getAsString());
                    break;
                case "divider":
                    page.divider();
                    break;
                case "table":
                    List<List<String>> rows = new ArrayList<>();
                    for (JsonElement row : block.getAsJsonArray("rows")) rows.add(strings(row.getAsJsonArray()));
                    TooltipTable table = TooltipTable.of("", strings(block.getAsJsonArray("columns")), rows)
                        .withInputIcons();
                    for (int column = 0; column < table.headers()
                        .size(); column++) {
                        table = table.align(column, TooltipTable.Alignment.LEFT);
                    }
                    page.block(table);
                    break;
                case "dynamic":
                    dynamic.accept(
                        block.get("binding")
                            .getAsString(),
                        page);
                    break;
                default:
                    throw new IllegalStateException("Unknown tooltip block: " + block);
            }
        }
        return page;
    }

    private static List<String> strings(JsonArray array) {
        List<String> result = new ArrayList<>();
        for (JsonElement value : array) result.add(value.getAsString());
        return result;
    }
}
