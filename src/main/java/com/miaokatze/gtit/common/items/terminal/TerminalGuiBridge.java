package com.miaokatze.gtit.common.items.terminal;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Typed GUI injection point keeping the terminal's common code independent of the gui package.
 * CommonProxy registers the panel factory on both physical sides: MUI builds its sync tree on the server too.
 * ClientProxy alone registers the screen factory. Delegation preserves the original per-opening GuiData identity.
 */
public final class TerminalGuiBridge {

    @FunctionalInterface
    public interface PanelFactory {

        ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager sync, UISettings settings);
    }

    @SideOnly(Side.CLIENT)
    @FunctionalInterface
    public interface ScreenFactory {

        ModularScreen create(PlayerInventoryGuiData data, ModularPanel panel);
    }

    private static volatile PanelFactory panelFactory;
    @SideOnly(Side.CLIENT)
    private static volatile ScreenFactory screenFactory;

    private TerminalGuiBridge() {}

    public static void registerPanel(PanelFactory factory) {
        panelFactory = factory;
    }

    @SideOnly(Side.CLIENT)
    public static void registerScreen(ScreenFactory factory) {
        screenFactory = factory;
    }

    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager sync, UISettings settings) {
        final PanelFactory factory = panelFactory;
        if (factory == null) {
            throw new IllegalStateException("Terminal panel factory not registered: CommonProxy#init has not run");
        }
        return factory.build(data, sync, settings);
    }

    @SideOnly(Side.CLIENT)
    public static ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel panel) {
        final ScreenFactory factory = screenFactory;
        if (factory == null) {
            throw new IllegalStateException("Terminal screen factory not registered: ClientProxy#init has not run");
        }
        return factory.create(data, panel);
    }
}
