package com.miaokatze.gtit.trade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.miaokatze.gtit.trade.TeamStatusPage.Member;
import com.miaokatze.gtit.trade.TeamStatusPage.Source;
import com.miaokatze.gtit.trade.TeamStatusPage.State;
import com.miaokatze.gtit.trade.v2.NekoBqBridge;
import com.miaokatze.gtit.util.PlayerLookup;
import com.miaokatze.gtit.util.PlayerResolver;

/** Server-only read model. No team creation, data migration, wallet or quest mutation. */
public final class NekoTeamStatusService {

    private NekoTeamStatusService() {}

    /** The authenticated container player is the only lookup subject; no target UUID enters the wire API. */
    public static TeamStatusPage query(EntityPlayerMP player, Source source, int requestedPage) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return TeamStatusPage.empty(source, State.ERROR);
        }
        try {
            UUID viewer = player.getUniqueID();
            if (source == Source.BQ) return queryBq(viewer, requestedPage);
            if (!TeamDataProvider.isAvailable()) return TeamStatusPage.empty(source, State.MISSING);
            Team team = TeamDataProvider.getTeam(viewer);
            if (team == null) return TeamStatusPage.empty(source, State.NONE);
            return page(
                source,
                viewer,
                team.getTeamName(),
                team.getMembers(),
                requestedPage,
                NekoTeamStatusService::resolveMember);
        } catch (RuntimeException | LinkageError unavailable) {
            return TeamStatusPage.empty(source, State.ERROR);
        }
    }

    private static TeamStatusPage queryBq(UUID viewer, int requestedPage) {
        if (!NekoBqBridge.isBqLoaded()) return TeamStatusPage.empty(Source.BQ, State.MISSING);
        if (!betterquesting.storage.QuestSettings.INSTANCE
            .getProperty(betterquesting.api.properties.NativeProps.PARTY_ENABLE)) {
            return TeamStatusPage.empty(Source.BQ, State.DISABLED);
        }
        betterquesting.api2.storage.DBEntry<betterquesting.api.questing.party.IParty> entry = betterquesting.questing.party.PartyManager.INSTANCE
            .getParty(viewer);
        if (entry == null) return TeamStatusPage.empty(Source.BQ, State.NONE);
        betterquesting.api.questing.party.IParty party = entry.getValue();
        return page(
            Source.BQ,
            viewer,
            party.getProperties()
                .getProperty(betterquesting.api.properties.NativeProps.NAME),
            party.getMembers(),
            requestedPage,
            NekoTeamStatusService::resolveMember);
    }

    static TeamStatusPage page(Source source, UUID viewer, String name, Collection<UUID> memberIds, int requestedPage,
        Function<UUID, Member> resolver) {
        // Recheck the authoritative member list, including when a cached team lookup is stale.
        if (viewer == null || memberIds == null || !memberIds.contains(viewer)) {
            return TeamStatusPage.empty(source, State.NONE);
        }
        List<UUID> ids = new ArrayList<>(memberIds);
        ids.removeIf(id -> id == null);
        ids.sort(Comparator.comparing(UUID::toString));
        int page = Math.max(0, Math.min(requestedPage, TeamStatusPage.lastPage(ids.size())));
        int start = page * TeamStatusPage.PAGE_SIZE;
        List<Member> members = new ArrayList<>();
        for (int i = start; i < Math.min(start + TeamStatusPage.PAGE_SIZE, ids.size()); i++) {
            members.add(resolver.apply(ids.get(i)));
        }
        return new TeamStatusPage(source, State.READY, name, page, ids.size(), members);
    }

    private static Member resolveMember(UUID id) {
        EntityPlayerMP online = PlayerLookup.getOnlinePlayerByUuid(id);
        if (online != null) return new Member(
            id,
            online.getGameProfile()
                .getName(),
            true);
        String name = null;
        if (NekoBqBridge.isBqLoaded()) {
            try {
                name = betterquesting.storage.NameCache.INSTANCE.getName(id);
                if (id.toString()
                    .equals(name)) name = null;
            } catch (RuntimeException | LinkageError ignored) {}
        }
        if (name == null || name.isEmpty()) {
            name = PlayerResolver.findNameFromUserCache(id, PlayerResolver.getUserCacheFile());
        }
        return new Member(id, name, false);
    }
}
