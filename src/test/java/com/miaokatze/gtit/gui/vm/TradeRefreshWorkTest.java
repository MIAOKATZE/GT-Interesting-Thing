// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gui.vm;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.utils.ICopy;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import com.miaokatze.gtit.client.gui.NekoDisplayType;
import com.miaokatze.gtit.client.gui.NekoSortMode;
import com.miaokatze.gtit.client.gui.NekoTradeItemDisplay;
import com.miaokatze.gtit.client.gui.NekoTradeMainPanel;
import com.miaokatze.gtit.client.gui.NekoWalletMode;
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
        if (args.length == 0 || !"client".equals(args[0])) serverNotifications();
        if (args.length == 0 || !"server".equals(args[0])) clientCatalogue();
        System.out.println(
            "TradeRefreshWorkTest PASS checks=" + checks
                + " definitions="
                + definitionWrites
                + " entries=360 game_started=false fps_measured=false");
    }

    private static final class CountedString extends StringSyncValue {

        int sends;

        CountedString(Supplier<String> getter) {
            super(getter);
        }

        @Override
        protected void sync() {
            sends++; // Capture the network boundary; native change detection/listeners remain intact.
        }
    }

    private static final class CountedFailures extends GenericListSyncHandler<String> {

        int sends;

        CountedFailures(Supplier<List<String>> getter) {
            super(getter, null, NetworkUtils::readStringSafe, NetworkUtils::writeStringSafe, null, ICopy.immutable());
        }

        @Override
        protected void sync() {
            sends++;
        }
    }

    private static void serverNotifications() throws Exception {
        StatusCodec codec = new StatusCodec(null, null, () -> {});
        AtomicReference<String> available = new AtomicReference<>("before");
        AtomicReference<String> cooldown = new AtomicReference<>("before");
        AtomicReference<String> team = new AtomicReference<>("before");
        AtomicReference<List<String>> failures = new AtomicReference<>(Collections.emptyList());
        int[] reads = { 0 };
        CountedString tradeable = new CountedString(() -> {
            reads[0]++;
            return available.get();
        });
        CountedString cooldownValue = new CountedString(cooldown::get);
        CountedString teamValue = new CountedString(team::get);
        CountedFailures failureValue = new CountedFailures(failures::get);
        set(codec, "tradeableStatusSync", tradeable);
        set(codec, "cooldownStatusSync", cooldownValue);
        set(codec, "teamScaleSync", teamValue);
        set(codec, "tradeFailureSync", failureValue);
        cooldownValue.setChangeListener(codec::markTradeableStatusDirtyAndNotify);
        reads[0] = 0;
        available.set("after");
        cooldown.set("after");
        team.set("after");
        failures.set(Collections.singletonList("after"));
        codec.notifyTradeStatusChanged();
        check(reads[0] == 1, "one availability evaluation despite the native cooldown change listener");
        check(
            tradeable.sends == 1 && cooldownValue.sends == 1 && teamValue.sends == 1 && failureValue.sends == 1,
            "each changed native handler sends once");
        codec.notifyTradeStatusChanged();
        check(
            tradeable.sends == 1 && cooldownValue.sends == 1 && teamValue.sends == 1 && failureValue.sends == 1,
            "identical explicit notifications do not send again");
        available.set("same-tick-next-change");
        codec.markTradeableStatusDirtyAndNotify();
        check(tradeable.sends == 2 && teamValue.sends == 1, "later same-tick invalidation is not delayed or lost");
        System.out.println("SERVER refresh counters: first_evaluation=1 changed_sends=4 duplicate_sends=0");
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
            StatusCodec codec = new StatusCodec(null, null, () -> view.notifications++);
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

            String allAvailable = availability(first, true) + ","
                + availability(NekoTradeDatabase.INSTANCE.getTradeGroup(SECOND), true);
            receive(manager, "nekoV2TradeableStatus", allAvailable);
            int notified = view.notifications;
            for (int i = 0; i < 20; i++) receive(manager, "nekoV2TradeableStatus", allAvailable);
            check(view.notifications == notified, "identical native S2C packets do not request another refresh");
            panel.updateGui();
            check(view.structures == 1 && view.states == 2, "unchanged membership uses the state-only callback");

            for (int seconds = 40; seconds >= 17; seconds--) {
                receive(manager, "nekoV2CooldownStatus", FIRST + ":0:" + seconds);
                panel.updateGui();
                check(find(view.trades, FIRST, 0) == identity, "status changes retain the display definition instance");
                check(identity.getCooldownRemaining() == seconds, "changed cooldown is immediately visible");
            }
            check(definitionWrites == built, "24 received status changes never serialize/build definitions again");
            check(view.structures == 2, "SMART ranking changes structure once; countdown-only changes reuse widgets");
            check(
                view.trades.get(category)
                    .get(179) == identity,
                "SMART sort moves the cooling trade behind available trades");

            List<NekoTradeItemDisplay> held = new ArrayList<>(view.trades.get(category));
            panel.shiftHeld = true;
            receive(manager, "nekoV2CooldownStatus", FIRST + ":179:50");
            panel.updateGui();
            check(held.equals(view.trades.get(category)), "Shift preserves current ordering while applying state");
            panel.shiftHeld = false;
            panel.ctrlHeld = true;
            receive(manager, "nekoV2FavouriteStatus", FIRST + ":179");
            panel.updateGui();
            check(
                view.trades.get(NekoTradeCategory.FAVOURITES)
                    .size() == 1,
                "favourites membership changes immediately while Ctrl holds ordinary ordering");
            panel.ctrlHeld = false;
            receive(manager, "nekoV2FavouriteStatus", "");
            panel.updateGui();
            check(
                view.trades.get(NekoTradeCategory.FAVOURITES)
                    .isEmpty(),
                "unfavourite removes the old derived membership");

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

        int notifications, structures, states, scope;
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
                    return codec.getCooldownRemaining(values[0] + ":" + values[1]);
                case "getSyncedOneTimeRedeemed":
                    return codec.isOneTimeRedeemed(values[0] + ":" + values[1]);
                case "getSyncedFavourite":
                    return codec.isFavourite((UUID) values[0], (Integer) values[1]);
                case "getSyncedTradeableStatus":
                    return codec.getTradeable(values[0] + ":" + values[1]);
                case "getDisplayedWidgets":
                    return Collections.emptyList();
                case "onTradeDisplayUpdated":
                    structures++;
                    trades = (Map<NekoTradeCategory, List<NekoTradeItemDisplay>>) values[0];
                    return null;
                case "onTradeStatusUpdated":
                    states++;
                    trades = (Map<NekoTradeCategory, List<NekoTradeItemDisplay>>) values[0];
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

    private static String availability(NekoTradeGroup group, boolean available) {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < group.getTrades()
            .size(); i++) entries.add(group.getId() + ":" + i + ":" + available);
        return String.join(",", entries);
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

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = StatusCodec.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
