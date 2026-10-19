package com.miaokatze.gtit.gui.vm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.miaokatze.gtit.client.gui.NekoTradeItemDisplay;
import com.miaokatze.gtit.trade.NekoPageConfig;
import com.miaokatze.gtit.trade.NekoPageEntry;
import com.miaokatze.gtit.trade.NekoPageRegistry;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

/** 标签增删后的选择、分页与同步注册表回归，无 Minecraft GUI 实机依赖。 */
public final class TradePageRefreshTest {

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    private static List<NekoTradeCategory> categories(int... ids) {
        List<NekoTradeCategory> result = new ArrayList<>();
        for (int id : ids) result.add(NekoTradeCategory.ofTabId(id));
        return result;
    }

    private static NekoPageEntry page(int id, String name) {
        NekoPageEntry entry = new NekoPageEntry();
        entry.setId(id);
        entry.setName(name);
        entry.setPresetBuiltin(true);
        return entry;
    }

    public static void main(String[] args) throws Exception {
        NekoTradeCategory selected = NekoTradeCategory.ofTabId(9);
        check(TradePage.resolveSelectedIndex(categories(-1, 1, 3, 9), selected, 4) == 3, "删除选中标签之前的页必须按 tabId 保持选择");
        check(TradePage.resolveSelectedIndex(categories(-1, 1, 2, 3, 9, 10), selected, 4) == 4, "追加空页不改变选择");
        check(TradePage.resolveSelectedIndex(categories(-1, 1, 3), selected, 4) == 2, "删除选中末页回退到合法末页");
        check(TradePage.resolveSelectedIndex(categories(-1), selected, 4) == 0, "空注册表仍能回退到收藏");
        check(TradePage.resolveTabPage(2, 3, 12, false) == 1, "末标签分页删除后钳制到上一页");
        check(TradePage.resolveTabPage(1, 10, 22, true) == 0, "删除前页跨边界后选中标签仍可见");
        check(TradePage.resolveTabPage(1, 3, 22, false) == 1, "用户浏览其他标签分页时保留位置");

        NekoPageConfig.NekoPageData data = new NekoPageConfig.NekoPageData();
        data.setPages(Arrays.asList(page(1, "default"), page(9, "empty")));
        long before = NekoPageRegistry.getVersion();
        NekoPageRegistry.applySyncedPages(data);
        check(NekoPageRegistry.getVersion() > before, "新增空标签必须改变注册表版本");
        check(
            NekoPageRegistry.getPageIds()
                .equals(Arrays.asList(1, 9)),
            "同步保持标签顺序");
        TradePage tradePage = new TradePage(null);
        check(
            tradePage.getTradeCategories()
                .equals(categories(-1, 1, 9)),
            "真实页面分类包含同步的空标签");
        List<NekoPageEntry> snapshot = NekoPageRegistry.getAllPages();
        before = NekoPageRegistry.getVersion();
        data.setPages(Arrays.asList(page(1, "renamed")));
        NekoPageRegistry.applySyncedPages(data);
        check(NekoPageRegistry.getVersion() > before, "删除空页和更名必须改变注册表版本");
        check(snapshot.size() == 2 && NekoPageRegistry.getPageCount() == 1, "同步后旧快照不被修改");
        check("renamed".equals(NekoPageRegistry.getPageName(1)), "更名即时应用到注册表");
        java.lang.reflect.Method reload = TradePage.class.getDeclaredMethod("loadTradeCategories");
        reload.setAccessible(true);
        reload.invoke(tradePage);
        check(
            tradePage.getTradeCategories()
                .equals(categories(-1, 1)),
            "真实分类重载移除已删除的空页");
        before = NekoPageRegistry.getVersion();
        NekoPageRegistry.applySyncedPages(null);
        check(NekoPageRegistry.getVersion() == before, "无效同步不能产生虚假修订");
        NekoPageEntry base = page(-400001, "Foundation");
        NekoPageEntry author = page(-100003, "AE");
        NekoPageEntry custom = page(7, "Manual");
        custom.setPresetBuiltin(false);
        NekoPageEntry imported = page(-500001, "Imported");
        imported.setPresetBuiltin(false);
        imported.setSourcePresetId("player-preset");
        imported.setSourcePresetName("Player preset");
        imported.setSourcePresetIconItem("minecraft:stone");
        data.setPages(Arrays.asList(base, author, custom, imported));
        NekoPageRegistry.applySyncedPages(data);
        reload.invoke(tradePage);
        check(
            tradePage.getTradeCategories()
                .equals(categories(-1, -400001, -100003)),
            "Default navigation contains foundation and author presets only");
        java.lang.reflect.Field scope = TradePage.class.getDeclaredField("tradeScope");
        scope.setAccessible(true);
        scope.setInt(tradePage, TradePage.SCOPE_CUSTOM);
        reload.invoke(tradePage);
        check(
            tradePage.getTradeCategories()
                .equals(categories(-1, 7, -500001)),
            "Custom navigation retains positive pages and negative imported preset pages");
        check(
            tradePage.isCategoryVisible(NekoTradeCategory.ofTabId(-500001))
                && !tradePage.isCategoryVisible(NekoTradeCategory.ofTabId(-100003)),
            "Search and favourites use the same author/custom scope");
        check(
            "Player preset".equals(
                NekoPageRegistry.getPage(-500001)
                    .getSourcePresetName())
                && "minecraft:stone".equals(
                    NekoPageRegistry.getPage(-500001)
                        .getSourcePresetIconItem()),
            "Public page snapshot retains actual source name and icon metadata");
        data.setPages(Arrays.asList(base, author));
        NekoPageRegistry.applySyncedPages(data);
        reload.invoke(tradePage);
        check(
            tradePage.getTradeCategories()
                .equals(categories(-1)),
            "An empty custom section never falls back to author defaults");
        NekoTradeGroup once = new NekoTradeGroup();
        once.setCooldown(-1);
        NekoTradeItemDisplay onceDisplay = NekoTradeItemDisplay.fromNekoTrade(new NekoTrade(), once, 0);
        onceDisplay.setTradeable(false); // Missing currency/inputs is not proof of a completed purchase.
        check(
            onceDisplay.isOneTimeOnly() && !onceDisplay.isOneTimeRedeemed()
                && "gtit.trade.cooldown.once".equals(onceDisplay.getOneTimeStatusKey()),
            "Unavailable one-time trades remain labelled one-time until the explicit history flag arrives");
        onceDisplay.setOneTimeRedeemed(true);
        onceDisplay.setTradeable(true); // A stale ready flag must not override authoritative redemption.
        onceDisplay.setCooldownRemaining(-1);
        check(
            !onceDisplay.isTradeable() && onceDisplay.getCooldownRemaining() == 0
                && "gtit.trade.cooldown.redeemed".equals(onceDisplay.getOneTimeStatusKey()),
            "Redeemed one-time trades never render ready or negative seconds");
        NekoTradeGroup unrestricted = new NekoTradeGroup();
        unrestricted.setCooldown(0);
        NekoTradeItemDisplay ordinary = NekoTradeItemDisplay.fromNekoTrade(new NekoTrade(), unrestricted, 0);
        ordinary.setOneTimeRedeemed(true);
        check(
            !ordinary.isOneTimeRedeemed() && ordinary.getOneTimeStatusKey()
                .isEmpty(),
            "No-cooldown trades do not inherit one-time labels");
        unrestricted.setCooldown(600);
        NekoTradeItemDisplay timed = NekoTradeItemDisplay.fromNekoTrade(new NekoTrade(), unrestricted, 0);
        timed.setCooldownRemaining(12);
        check(
            timed.getCooldownRemaining() == 12 && timed.getOneTimeStatusKey()
                .isEmpty(),
            "Positive cooldown seconds retain the original display path");
        System.out.println("TradePageRefreshTest: 24 checks passed");
    }
}
