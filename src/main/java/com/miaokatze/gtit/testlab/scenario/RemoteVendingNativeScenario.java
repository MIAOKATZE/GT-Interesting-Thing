package com.miaokatze.gtit.testlab.scenario;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.FakePlayer;

import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.items.terminal.ItemNekoVendingTerminal;
import com.miaokatze.gtit.common.items.terminal.NekoVendingTarget;
import com.miaokatze.gtit.common.items.terminal.RemoteVendingAccess;
import com.miaokatze.gtit.common.items.terminal.RemoteVendingContainer;
import com.miaokatze.gtit.common.items.terminal.RemoteVendingSession;
import com.miaokatze.gtit.common.machine.v2.MTENekoVendingMachineV2;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;
import com.miaokatze.gtit.trade.NekoTeamStatusService;
import com.miaokatze.gtit.trade.TeamStatusPage;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.blocks.ItemMachines;

/** Small opt-in RC2 probe: real controller, held item and server widget lifecycle, without a client claim. */
final class RemoteVendingNativeScenario implements ProbeScenario {

    @Override
    public String id() {
        return "native-remote-vending-team";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public ProbeTask start(ProbeContext context) throws Exception {
        FakePlayer owner = context.player("remote-owner");
        FakePlayer stranger = context.player("remote-stranger");
        NativeFixtures.ownIdentity(context, owner.getUniqueID());
        return new ProbeTask() {

            private BaseMetaTileEntity base;
            private int ticks;

            @Override
            public boolean tick() throws Exception {
                if (ticks++ == 0) {
                    ItemStack stack = context.mark(GTITItemList.NekoVendingMachine.get(1));
                    ItemMachines item = (ItemMachines) stack.getItem();
                    int metadata = GregTechAPI.METATILEENTITIES[stack.getItemDamage()].getTileEntityBaseType();
                    boolean placed = context.placeNativeBlock(
                        12,
                        0,
                        12,
                        GregTechAPI.sBlockMachines,
                        metadata,
                        () -> item.placeBlockAt(
                            stack,
                            owner,
                            context.world(),
                            context.x(12),
                            context.y(0),
                            context.z(12),
                            1,
                            0.5F,
                            0.5F,
                            0.5F,
                            metadata));
                    TileEntity tile = context.world()
                        .getTileEntity(context.x(12), context.y(0), context.z(12));
                    boolean valid = placed && tile instanceof BaseMetaTileEntity
                        && ((BaseMetaTileEntity) tile).getMetaTileEntity() instanceof MTENekoVendingMachineV2;
                    context.check(
                        "native-controller",
                        valid,
                        "Owned canvas placement uses the registered GT5U controller item");
                    if (!valid) return true;
                    base = (BaseMetaTileEntity) tile;
                    base.setOwnerName(owner.getDisplayName());
                    base.setOwnerUuid(owner.getUniqueID());
                    owner.setPosition(context.x(12) + 0.5, context.y(0) + 0.5, context.z(12) + 0.5);
                    stranger.setPosition(owner.posX, owner.posY, owner.posZ);
                    return false;
                }
                if (base.getTimer() <= 1 && ticks < 20) return false;
                context.check(
                    "native-controller-ticks",
                    base.getTimer() > 1,
                    "Waited for natural GT tile ticks; timer=" + base.getTimer());
                exercise(context, owner, stranger, base);
                return true;
            }
        };
    }

    private static void exercise(ProbeContext context, FakePlayer owner, FakePlayer stranger, BaseMetaTileEntity base)
        throws Exception {
        ItemStack terminal = context.mark(GTITItemList.NekoVendingTerminal.get(1));
        context.check(
            "terminal-registered",
            terminal.getItem() instanceof ItemNekoVendingTerminal
                && Item.itemRegistry.getNameForObject(terminal.getItem()) != null
                && terminal.getMaxStackSize() == 1,
            "Real registry item is a single-stack remote terminal");
        if (!(terminal.getItem() instanceof ItemNekoVendingTerminal)) return;
        owner.inventory.currentItem = 0;
        owner.inventory.setInventorySlotContents(0, terminal);
        ItemNekoVendingTerminal item = (ItemNekoVendingTerminal) terminal.getItem();
        NekoVendingTarget expected = new NekoVendingTarget(
            context.world().provider.dimensionId,
            context.x(12),
            context.y(0),
            context.z(12));
        context.check(
            "unbound-read-no-mutation",
            NekoVendingTarget.read(terminal) == null && !terminal.getTagCompound()
                .hasKey(NekoVendingTarget.TAG),
            "Reading the unbound marked item creates no target NBT");
        boolean consumed = item
            .onItemUseFirst(terminal, owner, context.world(), expected.x, expected.y, expected.z, 1, 0.5F, 0.5F, 0.5F);
        NBTTagCompound encoded = new NBTTagCompound();
        terminal.writeToNBT(encoded);
        context.check(
            "native-item-bind-nbt-roundtrip",
            consumed && expected.equals(NekoVendingTarget.read(terminal))
                && expected.equals(NekoVendingTarget.read(ItemStack.loadItemStackFromNBT(encoded))),
            "Actual held item onItemUseFirst binds the real controller and survives vanilla ItemStack NBT roundtrip");
        context.check(
            "loaded-target-access",
            RemoteVendingAccess.findLoaded(expected) == base && RemoteVendingAccess.failure(expected, owner) == null,
            "Loaded address resolves to the installed authoritative tile");

        // Fixture-only lock exercises GT's own owner predicate, without changing any real player's machine.
        Field lock = BaseMetaTileEntity.class.getDeclaredField("mLockUpgrade");
        lock.setAccessible(true);
        boolean previous = lock.getBoolean(base);
        try {
            lock.setBoolean(base, true);
            context.check(
                "native-owner-permission",
                base.isUseableByPlayer(owner) && !base.isUseableByPlayer(stranger)
                    && RemoteVendingAccess.canUse(base, owner)
                    && !RemoteVendingAccess.canUse(base, stranger)
                    && "gtit.terminal.access_denied".equals(RemoteVendingAccess.failure(expected, stranger)),
                "Real GT lock predicates agree with remote owner/stranger permission");
            ItemStack rejected = context.mark(new ItemStack(item));
            stranger.inventory.setInventorySlotContents(stranger.inventory.currentItem, rejected);
            item.onItemUseFirst(
                rejected,
                stranger,
                context.world(),
                expected.x,
                expected.y,
                expected.z,
                1,
                0.5F,
                0.5F,
                0.5F);
            context.check(
                "native-bind-permission-denied",
                NekoVendingTarget.read(rejected) == null,
                "Actual item binding refuses the unrelated synthetic player on the locked controller");
        } finally {
            lock.setBoolean(base, previous);
        }
        owner.setPosition(context.x(100) + 0.5, context.y(0), context.z(100) + 0.5);
        context.check(
            "remote-distance-only",
            !base.isUseableByPlayer(owner) && RemoteVendingAccess.canUse(base, owner),
            "Remote access retains GT permission while allowing distance beyond the local eight-block check");
        NekoVendingTarget unloaded = new NekoVendingTarget(
            expected.dimension,
            context.x(0) + 30_000_000,
            expected.y,
            expected.z);
        boolean existed = context.world()
            .getChunkProvider()
            .chunkExists(unloaded.x >> 4, unloaded.z >> 4);
        context.check(
            "unloaded-target-no-force-load",
            !existed && "gtit.terminal.chunk_unloaded".equals(RemoteVendingAccess.failure(unloaded, owner))
                && RemoteVendingAccess.findLoaded(unloaded) == null
                && !context.world()
                    .getChunkProvider()
                    .chunkExists(unloaded.x >> 4, unloaded.z >> 4),
            "Far ungenerated address remains absent after both lookup entry points");
        Container before = owner.openContainer;
        GuiFactories.playerInventory()
            .openFromMainHand(owner);
        if (owner.openContainer == before) context.skip(
            "physical-client-open",
            "MUI2 rejects FakePlayer opening; manual server tree below does not claim client networking, rendering or input");
        else {
            context.check(
                "native-factory-open",
                owner.openContainer instanceof RemoteVendingContainer,
                "Factory produced the production remote container on the server");
            owner.closeScreen();
        }
        ConcurrentVendingProbe.run(context, owner, stranger, terminal, expected, base);
        exerciseUi(context, owner, terminal, expected, base);
        for (TeamStatusPage.Source source : TeamStatusPage.Source.values()) {
            TeamStatusPage page = NekoTeamStatusService.query(owner, source, Integer.MAX_VALUE);
            context.check(
                "current-team-read-" + source.name()
                    .toLowerCase(java.util.Locale.ROOT),
                page.source == source && page.state != TeamStatusPage.State.ERROR
                    && page.state != TeamStatusPage.State.LOADING
                    && page.members.size() <= TeamStatusPage.PAGE_SIZE,
                "Read-only query for this disconnected synthetic UUID; state=" + page.state
                    + ", total="
                    + page.total
                    + "; no real team membership is changed");
        }
    }

    private static void exerciseUi(ProbeContext context, FakePlayer player, ItemStack terminal,
        NekoVendingTarget target, BaseMetaTileEntity base) throws Exception {
        PlayerInventoryGuiData data = new PlayerInventoryGuiData(player, InventoryTypes.PLAYER, 0);
        UISettings settings = new UISettings(RecipeViewerSettings.DUMMY);
        settings.defaultCanInteractWith(GuiFactories.playerInventory(), data);
        ModularSyncManager sync = new ModularSyncManager(false);
        PanelSyncManager main = new PanelSyncManager(sync, true);
        ModularPanel panel = GuiFactories.playerInventory()
            .createPanel(data, main, settings);
        WidgetTree.collectSyncValues(main, panel);
        ModularContainer container = settings.hasCustomContainer() ? settings.createContainer()
            : GuiFactories.playerInventory()
                .createContainer();
        Container previous = player.openContainer;
        try {
            container.construct(player, sync, settings, panel.getName(), data);
            player.openContainer = container;
            sync.onOpen();
            context.check(
                "remote-server-widget-tree",
                container instanceof RemoteVendingContainer && container.isInitialized()
                    && container.canInteractWith(player),
                "Production item factory constructs and initializes the real remote server GUI tree");
            MTENekoVendingMachineV2 machine = (MTENekoVendingMachineV2) base.getMetaTileEntity();
            int players = 0, inputs = 0, outputs = 0;
            boolean mapped = true;
            Set<Object> identities = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int i = 0; i < container.inventorySlots.size(); i++) {
                Slot raw = (Slot) container.inventorySlots.get(i);
                mapped &= identities.add(raw) && raw.slotNumber == i && raw instanceof ModularSlot;
                if (raw instanceof ModularSlot slot) {
                    mapped &= slot.isInitialized();
                    if (ModularSlot.isPlayerSlot(slot) && ModularSlot.getPlayerSlotPlayer(slot) == player) players++;
                    if (slot.getItemHandler() == machine.inputItems) inputs++;
                    if (slot.getItemHandler() == machine.outputItems) outputs++;
                }
            }
            context.check(
                "remote-native-slot-mapping",
                mapped && players == 36
                    && inputs == machine.inputItems.getSlots()
                    && outputs == machine.outputItems.getSlots(),
                "Unique initialized sequential native slots: player=" + players
                    + ", input="
                    + inputs
                    + ", output="
                    + outputs);
            Object handler = main.findPanelHandlerNullable("nekoV2Teams");
            context.check(
                "team-child-handler-identity",
                handler instanceof PanelSyncHandler && main.getSyncHandlerFromMapKey("nekoV2Teams") == handler,
                "Team child is registered once under its production panel sync key");
            if (handler instanceof PanelSyncHandler teams) {
                teams.openPanel();
                try {
                    PanelSyncManager child = sync.getPanelSyncManager("neko_team_status_panel");
                    context.check(
                        "team-child-server-lifecycle",
                        teams.isPanelOpen() && child.isInitialised()
                            && "nekoV2Teams".equals(teams.getKey())
                            && child.findSyncHandlerNullable("neko_team_status", 0) != null
                            && main.findSyncHandlerNullable("neko_team_status", 0) == null,
                        "Production child builder collects its own team status handler without changing the parent panel handler identity");
                } finally {
                    teams.closePanelInternal();
                }
                teams.openPanel();
                context.check(
                    "team-child-reopen",
                    teams.isPanelOpen() && sync.isOpen("neko_team_status_panel"),
                    "Cached production team child reopens under the same server sync identity");
                teams.closePanelInternal();
            }
            if (container instanceof RemoteVendingContainer remote) {
                RemoteVendingSession session = remote.getRemoteSession();
                int[] actions = { 0 };
                int[] wrappedActions = { 0 };
                vendingGuard(session, main, base, machine, () -> wrappedActions[0]++).run();
                context.check(
                    "vending-wrapper-current-remote",
                    wrappedActions[0] == 1,
                    "Actual VendingGuiSession wrapper executes once for the valid current remote container");
                Runnable windowChanged = vendingGuard(session, main, base, machine, () -> wrappedActions[0]++);
                int window = container.windowId;
                try {
                    container.windowId = window + 1;
                    windowChanged.run();
                    context.check(
                        "vending-wrapper-window-change",
                        wrappedActions[0] == 1,
                        "Queued production wrapper refuses a changed window ID on the same container");
                } finally {
                    container.windowId = window;
                }
                RemoteVendingSession.guard(session, main, () -> actions[0]++)
                    .run();
                context.check(
                    "remote-current-endpoint",
                    actions[0] == 1
                        && RemoteVendingSession.resolveCurrent(player, target.dimension, target.x, target.y, target.z)
                            == machine
                        && RemoteVendingSession
                            .resolveCurrent(player, target.dimension, target.x + 1, target.y, target.z) == null,
                    "Current authoritative session permits its exact address only");
                Runnable swapped = RemoteVendingSession.guard(session, main, () -> actions[0]++);
                Runnable wrappedSwap = vendingGuard(session, main, base, machine, () -> wrappedActions[0]++);
                player.inventory.currentItem = 1;
                swapped.run();
                wrappedSwap.run();
                context.check(
                    "remote-hand-swap-guard",
                    actions[0] == 1 && !container.canInteractWith(player),
                    "Changing held slot invalidates the real container and queued action");
                context.check(
                    "vending-wrapper-hand-swap",
                    wrappedActions[0] == 1,
                    "Production wrapper preserves the remote held-slot guard for an already queued action");
                player.inventory.currentItem = 0;
                exerciseLocalWrapper(context, player, main, base, machine, container, previous);
                RemoteVendingSession closed = new RemoteVendingSession(data, target, base);
                Runnable closing = RemoteVendingSession.guard(closed, main, () -> actions[0]++);
                Runnable wrappedClose = vendingGuard(closed, main, base, machine, () -> wrappedActions[0]++);
                boolean validBeforeClose = closed.isValid();
                closed.close();
                closing.run();
                wrappedClose.run();
                context.check(
                    "remote-session-close-guard",
                    validBeforeClose && actions[0] == 1 && !closed.isValid(),
                    "A valid production session closed before its queued action runs refuses that action");
                context.check(
                    "vending-wrapper-session-close",
                    validBeforeClose && wrappedActions[0] == 1,
                    "Production wrapper refuses an action queued before its remote session was closed");
                Runnable removed = RemoteVendingSession.guard(session, main, () -> actions[0]++);
                context.setBlock(12, 0, 12, Blocks.air, 0);
                removed.run();
                context.check(
                    "remote-target-removal-guard",
                    actions[0] == 1 && !session.isValid()
                        && "gtit.terminal.target_missing".equals(RemoteVendingAccess.failure(target, player)),
                    "Removing the owned controller closes authority without loading another target");
            }
        } finally {
            player.openContainer = previous;
            if (sync.isOpen()) sync.onClose();
            if (sync.isClosed()) sync.dispose();
            player.inventory.setInventorySlotContents(0, terminal);
        }
    }

    private static Runnable vendingGuard(PosGuiData data, PanelSyncManager sync, BaseMetaTileEntity tile,
        MTENekoVendingMachineV2 machine, Runnable action) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.miaokatze.gtit.gui.vm.VendingGuiSession");
        Method guard = type.getDeclaredMethod(
            "guard",
            PosGuiData.class,
            PanelSyncManager.class,
            gregtech.api.interfaces.tileentity.IGregTechTileEntity.class,
            gregtech.api.interfaces.metatileentity.IMetaTileEntity.class,
            Runnable.class);
        guard.setAccessible(true);
        return (Runnable) guard.invoke(null, data, sync, tile, machine, action);
    }

    private static void exerciseLocalWrapper(ProbeContext context, FakePlayer player, PanelSyncManager sync,
        BaseMetaTileEntity tile, MTENekoVendingMachineV2 machine, Container container, Container previous)
        throws ReflectiveOperationException {
        double x = player.posX, y = player.posY, z = player.posZ;
        int[] actions = { 0 };
        try {
            player.setPosition(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5);
            PosGuiData local = new PosGuiData(player, tile.xCoord, tile.yCoord, tile.zCoord);
            vendingGuard(local, sync, tile, machine, () -> actions[0]++).run();
            boolean initialActionRan = actions[0] == 1;
            Runnable stale = vendingGuard(local, sync, tile, machine, () -> actions[0]++);
            player.openContainer = previous;
            stale.run();
            context.check(
                "vending-wrapper-local-stale-container",
                initialActionRan && actions[0] == 1,
                "Ordinary PosGuiData uses the real initialized fixture container: local access runs once, replacement refuses its queued action");
        } finally {
            player.openContainer = container;
            player.setPosition(x, y, z);
        }
    }
}
