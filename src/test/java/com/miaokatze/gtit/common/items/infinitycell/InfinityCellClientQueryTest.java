// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.common.items.infinitycell;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import com.miaokatze.gtit.testutil.NativeAeTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.integration.modules.NEIHelpers.NEICellSearchFilter;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

/** Real AE2 registry/NEI calls, without a running world or client window. */
public final class InfinityCellClientQueryTest {

    private static final class Storage extends StorageManager {

        int queries;

        Storage() {
            super("infinity_query_test");
        }

        @Override
        public DataStorage getStorage(ItemStack item, IAEStackType<?> type) {
            queries++;
            if (Platform.isClient()) throw new AssertionError("Client queried server-backed storage");
            return super.getStorage(item, type);
        }
    }

    private static final class Warnings extends AbstractAppender {

        int count;

        Warnings() {
            super("infinity-query-test", null, null, true);
        }

        @Override
        public void append(LogEvent event) {
            if (event.getLevel() == Level.WARN || event.getLevel() == Level.ERROR || event.getLevel() == Level.FATAL)
                count++;
        }
    }

    public static void main(String[] args) throws Exception {
        if (!NativeAeTestBootstrap.enter(InfinityCellClientQueryTest.class, args)) return;
        cpw.mods.fml.server.FMLServerHandler.instance();
        Field instance = StorageManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        String oldThreadName = Thread.currentThread()
            .getName();
        org.apache.logging.log4j.core.Logger log = (org.apache.logging.log4j.core.Logger) LogManager.getLogger("gtit");
        Warnings warnings = new Warnings();
        warnings.start();
        log.addAppender(warnings);
        try {
            AEApi.instance()
                .registries()
                .cell()
                .addCellHandler(new InfinityCellHandler());
            ItemStack[] cells = { new ItemStack(new ItemInfinityStorageCell()),
                new ItemStack(new ItemInfinityStorageFluidCell()), new ItemStack(new ItemNekoInfinityStorageUnit()) };
            Thread.currentThread()
                .setName("NEI Item Filtering");
            SimpleAssert.that(Platform.isClient(), "NEI worker is a logical client even in a physical-server fixture");
            instance.set(null, null);
            clientQueries(cells);
            SimpleAssert.eq(0, warnings.count, "uninitialized client queries produce no storage warning");

            Storage storage = new Storage();
            clientQueries(cells);
            SimpleAssert.eq(0, storage.queries, "integrated-server singleton is inaccessible to client queries");
            SimpleAssert.eq(0, warnings.count, "client queries never enter the exception fallback");

            Thread.currentThread()
                .setName("Server thread");
            SimpleAssert.that(Platform.isServer(), "integrated and dedicated server work uses effective side");
            serverRoundTrip(cells[0]);
            serverRoundTrip(cells[2]);
            SimpleAssert.that(
                AEApi.instance()
                    .registries()
                    .cell()
                    .getCellInventory(
                        cells[1],
                        null,
                        InfinityStackTypes.FLUID_STACK_TYPE) instanceof InfinityFluidCellInventoryHandler,
                "legacy fluid inventory remains available on server");
            SimpleAssert.that(
                AEApi.instance()
                    .registries()
                    .cell()
                    .getCellInventory(
                        cells[2],
                        null,
                        InfinityStackTypes.FLUID_STACK_TYPE) instanceof InfinityTypedCellHandler,
                "new unit retains its independent typed-fluid inventory");
            SimpleAssert.that(storage.queries > 0, "server requests still reach real storage");
            int serverQueries = storage.queries;
            Thread.currentThread()
                .setName("NEI Item Filtering");
            clientQueries(cells);
            SimpleAssert.eq(serverQueries, storage.queries, "populated server inventories cannot leak into NEI");

            Thread.currentThread()
                .setName("Server thread");
            instance.set(null, null);
            SimpleAssert.eq(
                null,
                AEApi.instance()
                    .registries()
                    .cell()
                    .getCellInventory(cells[0], null, InfinityStackTypes.ITEM_STACK_TYPE),
                "unready server storage remains unavailable");
            SimpleAssert.eq(1, warnings.count, "real server lifecycle failure retains its warning");
            System.out.println(
                "InfinityCellClientQueryTest: 4 cases passed (native NEI, isolation, server storage, diagnostics)");
        } finally {
            instance.set(null, previous);
            Thread.currentThread()
                .setName(oldThreadName);
            log.removeAppender(warnings);
            warnings.stop();
        }
    }

    private static void clientQueries(ItemStack[] cells) {
        NEICellSearchFilter.Filter filter = new NEICellSearchFilter.Filter(Pattern.compile("iron"));
        for (ItemStack cell : cells) {
            SimpleAssert.that(
                AEApi.instance()
                    .registries()
                    .cell()
                    .isCellHandled(cell),
                "native storage-slot recognition remains available on clients");
            NBTTagCompound before = cell.hasTagCompound() ? (NBTTagCompound) cell.getTagCompound()
                .copy() : null;
            for (int repeat = 0; repeat < 100; repeat++) {
                SimpleAssert
                    .that(!filter.matches(cell), "native NEI accepts unavailable external contents as no match");
                for (StorageChannel channel : StorageChannel.values()) {
                    SimpleAssert.eq(
                        null,
                        AEApi.instance()
                            .registries()
                            .cell()
                            .getCellInventory(cell, null, channel),
                        "legacy client query cannot construct an inventory");
                }
                for (IAEStackType<?> type : InfinityStackTypes.allSupportedTypes()) {
                    SimpleAssert.eq(
                        null,
                        AEApi.instance()
                            .registries()
                            .cell()
                            .getCellInventory(cell, null, type),
                        "typed client query cannot construct an inventory");
                }
            }
            SimpleAssert.eq(before, cell.getTagCompound(), "client query neither creates nor changes cell NBT");
        }
    }

    @SuppressWarnings("unchecked")
    private static void serverRoundTrip(ItemStack cell) {
        IMEInventoryHandler<IAEItemStack> handler = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(cell, null, InfinityStackTypes.ITEM_STACK_TYPE);
        SimpleAssert.that(handler instanceof InfinityCellInventoryHandler, "server item inventory is preserved");
        IAEItemStack offered = AEItemStack.create(new ItemStack(Items.iron_ingot, 9));
        SimpleAssert
            .eq(null, handler.injectItems(offered, Actionable.MODULATE, null), "server stores all offered items");
        IAEItemStack request = offered.copy();
        request.setStackSize(4);
        SimpleAssert.eq(
            4L,
            handler.extractItems(request, Actionable.MODULATE, null)
                .getStackSize(),
            "real server storage extraction still works");
        SimpleAssert.that(
            cell.hasTagCompound() && !cell.getTagCompound()
                .getString(InfinityCellConstants.DISKUUID)
                .isEmpty(),
            "only the server assigns and persists storage identity");
    }
}
