// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gui.vm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import com.miaokatze.gtit.client.gui.NekoDisplayType;
import com.miaokatze.gtit.client.gui.NekoSortMode;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplay;
import com.miaokatze.gtit.client.gui.NekoTradeMainPanel;
import com.miaokatze.gtit.client.gui.NekoWalletMode;
import com.miaokatze.gtit.gui.vm.StatusCodec.TradeKey;
import com.miaokatze.gtit.gui.vm.StatusCodec.TradeStatus;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeCategory;
import com.miaokatze.gtit.trade.v2.NekoTradeDatabase;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

import io.netty.buffer.Unpooled;

/** Native MUI2 packets/cache with a counted 360-entry catalogue; no game, renderer or FPS benchmark. */
public final class TradeRefreshWorkTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000096");
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static int checks;
    private static int definitionWrites;

    private TradeRefreshWorkTest() {}

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(TradeRefreshWorkTest.class, args)) return;
        cpw.mods.fml.server.FMLServerHandler.instance();
        System.out.println(
            "TRADE_REFRESH source_status=" + StatusCodec.class.getProtectionDomain()
                .getCodeSource()
                + " source_panel="
                + NekoTradeMainPanel.class.getProtectionDomain()
                    .getCodeSource());
        clientCatalogue();
        System.out.println(
            "TradeRefreshWorkTest PASS checks=" + checks
                + " definitions="
                + definitionWrites
                + " entries=360 game_started=false fps_measured=false");
    }

    private static final class CountedTrade extends NekoTrade {

        @Override
        public NBTTagCompound writeToNBT() {
            definitionWrites++;
            return super.writeToNBT();
        }
    }

    private static void clientCatalogue() throws Exception {
        NekoTradeDatabase.INSTANCE.clear();
        try {
            NekoTradeGroup first = group(FIRST, 5001, 0);
            group(SECOND, 5002, 180);
            View view = new View();
            PanelSyncManager manager = new PanelSyncManager(new ModularSyncManager(true), true);
            StatusCodec codec = new StatusCodec(null, () -> view.notifications++);
            codec.registerSyncValues(manager, PLAYER);
            manager.initialize("trade_refresh_contract");
            NekoTradeMainPanel.PanelCallback callback = (NekoTradeMainPanel.PanelCallback) Proxy.newProxyInstance(
                TradeRefreshWorkTest.class.getClassLoader(),
                new Class<?>[] { NekoTradeMainPanel.PanelCallback.class },
                (proxy, method, values) -> view.call(codec, method.getName(), values));
            NekoTradeMainPanel panel = new NekoTradeMainPanel("trade_refresh_work", callback, null, manager);
            Field player = NekoTradeMainPanel.class.getDeclaredField("playerId");
            player.setAccessible(true);
            player.set(panel, PLAYER);
            panel.updateGui();
            int built = definitionWrites;
            check(built == 360 && view.structures == 1, "initial catalogue constructs exactly 360 definitions once");
            panel.updateGui();
            check(
                definitionWrites == built,
                "unchanged catalogue refresh must reuse 360 definitions: initial=" + built
                    + " after="
                    + definitionWrites);
            NekoTradeCategory category = NekoTradeCategory.ofTabId(5001);
            NekoTradeItemDisplay identity = find(view.trades, FIRST, 0);

            TradeKey clicked = new TradeKey(FIRST, 0);
            TradeStatus firstCooldown = new TradeStatus(false, 40, false, 1, 1, "冷却");
            NekoTradeItemDisplay untouched = find(view.trades, SECOND, 0);
            untouched.setTradeFailure("outside the patch");
            StatusCodecSyncTest.receiveRows(manager, Collections.singletonMap(clicked, firstCooldown));
            Map<TradeKey, TradeStatus> changes = codec.takeClientChanges();
            check(
                changes.keySet()
                    .equals(Collections.singleton(clicked)),
                "exactly the clicked row reaches presentation");
            applyChanges(panel, changes);
            check(identity.getCooldownRemaining() == 40, "the changed cooldown reaches its existing display");
            check(
                untouched.getTradeFailure()
                    .equals("outside the patch"),
                "unrelated rows are not rewritten");
            check(
                view.reordered.equals(Collections.singleton(category)),
                "only the affected SMART category is rebound");
            check(
                view.trades.get(category)
                    .get(179) == identity,
                "SMART moves the cooling row behind available rows");

            for (int i = 0; i < 20; i++)
                StatusCodecSyncTest.receiveRows(manager, Collections.singletonMap(clicked, firstCooldown));
            check(
                codec.takeClientChanges()
                    .isEmpty(),
                "replayed packets produce no more changes");
            check(view.notifications == 0, "row updates never request a catalogue refresh");

            for (int seconds = 39; seconds >= 17; seconds--) {
                TradeStatus status = new TradeStatus(false, seconds, false, 1, 1, "冷却");
                StatusCodecSyncTest.receiveRows(manager, Collections.singletonMap(clicked, status));
                applyChanges(panel, codec.takeClientChanges());
                check(find(view.trades, FIRST, 0) == identity, "status changes retain the existing display");
                check(identity.getCooldownRemaining() == seconds, "countdowns patch immediately");
            }
            check(definitionWrites == built && view.reorders == 1, "countdowns do not rebuild or rebind the display");

            List<NekoTradeItemDisplay> held = new ArrayList<>(view.trades.get(category));
            panel.shiftHeld = true;
            TradeKey other = new TradeKey(FIRST, 2);
            StatusCodecSyncTest
                .receiveRows(manager, Collections.singletonMap(other, new TradeStatus(false, 50, false, 1, 1, "冷却")));
            applyChanges(panel, codec.takeClientChanges());
            check(held.equals(view.trades.get(category)), "Shift preserves the current ordering");
            check(find(view.trades, FIRST, 2).getCooldownRemaining() == 50, "the held row still updates");
            panel.shiftHeld = false;
            applyChanges(panel, Collections.emptyMap());
            check(view.reorders == 2, "the postponed SMART order applies after releasing Shift");
            panel.ctrlHeld = true;
            receive(manager, "nekoV2FavouriteStatus", FIRST + ":2");
            panel.updateGui();
            check(
                view.trades.get(NekoTradeCategory.FAVOURITES)
                    .size() == 1,
                "Ctrl updates favourites membership");
            NekoTradeItemDisplay favourite = view.trades.get(NekoTradeCategory.FAVOURITES)
                .get(0);
            StatusCodecSyncTest
                .receiveRows(manager, Collections.singletonMap(other, new TradeStatus(false, 49, false, 1, 1, "冷却")));
            applyChanges(panel, codec.takeClientChanges());
            check(
                favourite == find(view.trades, FIRST, 2) && favourite.getCooldownRemaining() == 49,
                "the favourite and tab share the same patched display");
            panel.ctrlHeld = false;
            receive(manager, "nekoV2FavouriteStatus", "");
            panel.updateGui();
            check(
                view.trades.get(NekoTradeCategory.FAVOURITES)
                    .isEmpty(),
                "unfavourite removes derived membership");
            view.scope = 5001;
            panel.updateGui();
            check(!view.trades.containsKey(NekoTradeCategory.ofTabId(5002)), "scope hides the other category");
            view.scope = 0;
            view.search = "Entry 000";
            panel.updateGui();
            check(memberCount(view.trades) == 1, "search filters cached definition data");
            view.search = "";
            view.sort = NekoSortMode.ALPHABET;
            panel.updateGui();
            check(memberCount(view.trades) == 360, "search/scope restoration brings back the entire catalogue");
            check(
                view.trades.get(category)
                    .get(0) == identity,
                "ALPHABET order is retained independently of SMART cooldown rank");
            check(definitionWrites == built, "sorting, search, scope and favourites reuse the existing definitions");

            String fingerprint = identity.getDefinitionFingerprint();
            first.getTrades()
                .get(0)
                .setRecordNBT(true);
            NekoTradeDatabase.INSTANCE.addTradeGroup(first);
            panel.ctrlHeld = true;
            panel.updateGui();
            NekoTradeItemDisplay replaced = find(view.trades, FIRST, 0);
            check(
                replaced != identity && !fingerprint.equals(replaced.getDefinitionFingerprint()),
                "real database revision rebuilds definitions and fingerprints even with Ctrl held");
            check(definitionWrites == built + 360, "exactly one new definition pass follows the database revision");
            System.out.println(
                "CLIENT refresh counters: entries=360 initial_definitions=" + built
                    + " status_changes=24 repeated_definition_builds=0 duplicate_packet_refreshes=0 revision_definitions=360");
        } finally {
            NekoTradeDatabase.INSTANCE.clear();
        }
    }

    private static final class View {

        int notifications, structures, reorders, scope;
        Set<NekoTradeCategory> reordered;
        String search = "";
        NekoSortMode sort = NekoSortMode.SMART;
        Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades;

        @SuppressWarnings("unchecked")
        Object call(StatusCodec codec, String name, Object[] values) {
            switch (name) {
                case "getPlayerId":
                    return PLAYER;
                case "getSearchText":
                    return search;
                case "getSortMode":
                    return sort;
                case "getWalletMode":
                    return NekoWalletMode.PERSONAL;
                case "getDisplayType":
                    return NekoDisplayType.LIST;
                case "getActiveCategory":
                    return NekoTradeCategory.ofTabId(5001);
                case "getRefreshInterval":
                    return 20;
                case "isClient":
                    return true;
                case "isTradeCategoryVisible":
                    return scope == 0 || NekoTradeCategory.ofTabId(scope)
                        .equals(values[0]);
                case "getSyncedCooldownRemaining":
                    return 0L;
                case "getSyncedOneTimeRedeemed":
                    return false;
                case "getSyncedFavourite":
                    return codec.isFavourite((UUID) values[0], (Integer) values[1]);
                case "getSyncedTradeableStatus":
                    return null;
                case "getSyncedTradeStatus":
                    return codec.clientStatus((TradeKey) values[0]);
                case "getDisplayedWidgets":
                    return Collections.emptyList();
                case "onTradeDisplayUpdated":
                    structures++;
                    trades = (Map<NekoTradeCategory, List<NekoTradeItemDisplay>>) values[0];
                    return null;
                case "onTradeCategoriesReordered":
                    reorders++;
                    reordered = new java.util.HashSet<>((Set<NekoTradeCategory>) values[0]);
                    trades = (Map<NekoTradeCategory, List<NekoTradeItemDisplay>>) values[1];
                    return null;
                case "isSearchBarFocused":
                case "isEditOverlayOpen":
                    return false;
                default:
                    return null;
            }
        }
    }

    private static NekoTradeGroup group(UUID id, int tab, int start) {
        NekoTradeGroup group = new NekoTradeGroup(id);
        group.setTabId(tab);
        group.setCategory(NekoTradeCategory.ofTabId(tab));
        for (int i = 0; i < 180; i++) {
            CountedTrade trade = new CountedTrade();
            ItemStack stack = new ItemStack(Items.iron_ingot);
            stack.setStackDisplayName(String.format(java.util.Locale.ROOT, "Entry %03d", start + i));
            trade.getToItems()
                .add(new NekoBigItemStack(stack));
            group.getTrades()
                .add(trade);
        }
        NekoTradeDatabase.INSTANCE.addTradeGroup(group);
        return group;
    }

    private static NekoTradeItemDisplay find(Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades, UUID group,
        int index) {
        for (List<NekoTradeItemDisplay> entries : trades.values()) for (NekoTradeItemDisplay display : entries)
            if (group.equals(display.getGroupId()) && index == display.getTradeIndex()) return display;
        throw new AssertionError("Missing display: " + group + ":" + index);
    }

    private static int memberCount(Map<NekoTradeCategory, List<NekoTradeItemDisplay>> trades) {
        int count = 0;
        for (Map.Entry<NekoTradeCategory, List<NekoTradeItemDisplay>> entry : trades.entrySet())
            if (!NekoTradeCategory.FAVOURITES.equals(entry.getKey())) count += entry.getValue()
                .size();
        return count;
    }

    private static void receive(PanelSyncManager manager, String channel, String status) throws Exception {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new StringSyncValue(() -> status).write(packet);
            manager.receiveWidgetUpdate(
                PanelSyncManager.makeSyncKey(channel, 0),
                false,
                ValueSyncHandler.SYNC_VALUE,
                packet);
            check(!packet.isReadable(), "native MUI2 status packet is completely consumed");
        } finally {
            packet.release();
        }
    }

    private static void applyChanges(NekoTradeMainPanel panel, Map<TradeKey, TradeStatus> changes) throws Exception {
        Method method = NekoTradeMainPanel.class.getDeclaredMethod("applyTradeStatusChanges", Map.class);
        method.setAccessible(true);
        method.invoke(panel, changes);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
