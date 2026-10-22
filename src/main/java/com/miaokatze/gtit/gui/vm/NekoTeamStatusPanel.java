package com.miaokatze.gtit.gui.vm;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.miaokatze.gtit.client.gui.utility.UtilityUiStyle;
import com.miaokatze.gtit.trade.TeamStatusPage;
import com.miaokatze.gtit.trade.TeamStatusPage.Member;
import com.miaokatze.gtit.trade.TeamStatusPage.Source;

/** Read-only team status subpanel. Register with syncedPanel on both logical sides. */
public final class NekoTeamStatusPanel {

    private NekoTeamStatusPanel() {}

    public static ModularPanel create(PanelSyncManager sm) {
        NekoTeamStatusSync sync = new NekoTeamStatusSync();
        sm.syncValue("neko_team_status", sync);
        ModularPanel panel = new ModularPanel("neko_team_status_panel").size(340, 272)
            .background(UtilityUiStyle.PANEL);
        panel.child(ButtonWidget.panelCloseButton());
        panel.child(
            IKey.lang("gtit.gui.teams.title")
                .asWidget()
                .pos(12, 9)
                .size(296, 12)
                .color(UtilityUiStyle.TEXT));
        panel.child(
            IKey.lang("gtit.gui.teams.read_only")
                .asWidget()
                .pos(12, 25)
                .size(316, 12)
                .scale(0.8f)
                .color(UtilityUiStyle.MUTED));
        panel.child(button(sync, "gtit.gui.teams.bq", NekoTeamStatusSync.BQ, 12, 42, 152));
        panel.child(button(sync, "gtit.gui.teams.neko", NekoTeamStatusSync.NEKO, 172, 42, 156));
        panel.child(
            IKey.dynamic(
                () -> text("gtit.gui.teams.team", sourceName(sync.view()), shortened(sync.view().teamName, 28)))
                .asWidget()
                .pos(12, 65)
                .size(316, 12)
                .scale(0.8f)
                .color(UtilityUiStyle.TEXT)
                .tooltipDynamic(t -> t.addLine(IKey.str(sourceName(sync.view()) + ": " + sync.view().teamName)))
                .tooltip(t -> t.setAutoUpdate(true)));
        panel.child(
            IKey.dynamic(() -> status(sync.view()))
                .asWidget()
                .pos(12, 80)
                .size(316, 12)
                .scale(0.8f)
                .color(UtilityUiStyle.MUTED));
        for (int i = 0; i < TeamStatusPage.PAGE_SIZE; i++) {
            final int row = i;
            panel.child(
                IKey.dynamic(() -> memberName(sync.view(), row))
                    .asWidget()
                    .pos(12, 99 + i * 16)
                    .size(214, 14)
                    .scale(0.8f)
                    .color(UtilityUiStyle.TEXT)
                    .setEnabledIf(w -> row < sync.view().members.size())
                    .tooltipDynamic(t -> {
                        if (row < sync.view().members.size()) {
                            Member member = sync.view().members.get(row);
                            if (!member.name.isEmpty()) t.addLine(IKey.str(member.name));
                            t.addLine(IKey.str(sourceName(sync.view()) + ": " + member.id));
                        }
                    })
                    .tooltip(t -> t.setAutoUpdate(true)));
            panel.child(
                IKey.dynamic(() -> memberState(sync.view(), row))
                    .asWidget()
                    .pos(230, 99 + i * 16)
                    .size(98, 14)
                    .scale(0.8f)
                    .color(UtilityUiStyle.MUTED));
        }
        panel.child(
            button(sync, "gtit.gui.teams.previous", NekoTeamStatusSync.PREVIOUS, 12, 240, 64)
                .setEnabledIf(w -> sync.view().page > 0));
        panel.child(
            IKey.dynamic(
                () -> text(
                    "gtit.gui.teams.page",
                    sync.view().page + 1,
                    TeamStatusPage.lastPage(sync.view().total) + 1,
                    sync.view().total))
                .asWidget()
                .pos(80, 244)
                .size(132, 12)
                .scale(0.8f)
                .color(UtilityUiStyle.TEXT));
        panel.child(
            button(sync, "gtit.gui.teams.next", NekoTeamStatusSync.NEXT, 214, 240, 52)
                .setEnabledIf(w -> sync.view().page < TeamStatusPage.lastPage(sync.view().total)));
        panel.child(button(sync, "gtit.gui.teams.refresh", NekoTeamStatusSync.REFRESH, 272, 240, 56));
        return panel;
    }

    private static ButtonWidget<?> button(NekoTeamStatusSync sync, String label, int action, int x, int y, int width) {
        return new ButtonWidget<>().pos(x, y)
            .size(width, 18)
            .background(UtilityUiStyle.BUTTON)
            .hoverBackground(UtilityUiStyle.BUTTON_HOVER)
            .overlay(IKey.lang(label))
            .onMousePressed(mouse -> {
                if (mouse == 0 && sync.getSyncManager()
                    .isClient()) sync.request(action);
                return true;
            });
    }

    private static String sourceName(TeamStatusPage page) {
        return text(page.source == Source.BQ ? "gtit.gui.teams.bq" : "gtit.gui.teams.neko");
    }

    private static String status(TeamStatusPage page) {
        return text(
            "gtit.gui.teams.state." + page.state.name()
                .toLowerCase(java.util.Locale.ROOT));
    }

    private static String memberName(TeamStatusPage page, int row) {
        if (row >= page.members.size()) return "";
        Member member = page.members.get(row);
        return member.name.isEmpty() ? text("gtit.gui.teams.unknown", member.id.toString())
            : shortened(member.name, 32);
    }

    private static String memberState(TeamStatusPage page, int row) {
        if (row >= page.members.size()) return "";
        return text(page.members.get(row).online ? "gtit.gui.teams.online" : "gtit.gui.teams.offline");
    }

    private static String text(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    private static String shortened(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }
}
