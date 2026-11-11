// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.trade;

import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;

/** GTNHLib migrates members before datum callbacks; reject damaged GTIT ledgers before that first mutation. */
public final class TeamMergePreflight {

    private TeamMergePreflight() {}

    public static void requireUsable(Team surviving, Team consumed) {
        if (surviving == null || consumed == null
            || surviving.getTeamId()
                .equals(consumed.getTeamId()))
            return;
        check(surviving);
        check(consumed);
    }

    private static void check(Team team) {
        ITeamData data = team.getData(NekoTeamData.ID);
        if (data instanceof NekoTeamData ledger) ledger.requireUsable();
    }
}
