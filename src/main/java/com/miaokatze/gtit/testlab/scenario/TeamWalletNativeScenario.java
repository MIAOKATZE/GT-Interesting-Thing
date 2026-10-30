package com.miaokatze.gtit.testlab.scenario;

import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataSaver;
import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.TeamDataProvider;

final class TeamWalletNativeScenario implements ProbeScenario {

    @Override
    public String id() {
        return "native-team-wallet";
    }

    @Override
    public ProbeTask start(ProbeContext context) throws Exception {
        if (!TeamDataProvider.isAvailable()) {
            context.check("required-teams-api", false, "Required RC2 GTNHLib Teams API unavailable");
            return () -> true;
        }
        UUID owner = context.player("owner")
            .getUniqueID();
        UUID member = context.player("member")
            .getUniqueID();
        UUID outsider = context.player("outsider")
            .getUniqueID();
        NativeFixtures.ownIdentity(context, owner);
        NativeFixtures.ownIdentity(context, member);
        NativeFixtures.ownIdentity(context, outsider);
        return () -> {
            String coin = NekoCurrencyRegistrar.NEKO_ID;
            NekoWalletManager manager = NekoWalletManager.INSTANCE;
            NekoWallet ownerPersonal = manager.getWallet(owner);
            NekoWallet memberPersonal = manager.getWallet(member);
            NekoWallet outsiderWallet = manager.getWallet(outsider);
            ownerPersonal.addCount(coin, 41);
            memberPersonal.addCount(coin, 13);
            outsiderWallet.addCount(coin, 9);
            Team team = createOwnedTeam(context, owner);
            if (TeamDataProvider.getData(team) == null) {
                context.check("team-data-registered", false, "Real TeamManager.createTeam lacks GTIT NekoTeamData");
                return true;
            }
            team.addMember(member);
            NekoWallet shared = manager.getWallet(owner);
            context.check(
                "join-routes-shared",
                shared == manager.getWallet(member) && shared != ownerPersonal
                    && shared != memberPersonal
                    && shared.isTeamWallet(),
                "Two synthetic members resolve one real team wallet");
            context.check(
                "join-freezes-personal",
                shared.getCount(coin) == 0 && ownerPersonal.getCount(coin) == 41 && memberPersonal.getCount(coin) == 13,
                "Joining does not merge personal snapshots into team funds");
            manager.saveWallet(owner);
            File personalFile = NativeFixtures.dataFile(manager, owner);
            context.check(
                "join-preserves-unsaved-personal-file",
                personalFile != null && personalFile.isFile()
                    && net.minecraft.nbt.CompressedStreamTools.read(personalFile)
                        .getCompoundTag("wallet")
                        .getInteger(coin) == 41,
                "Saving while team-routed also persists the frozen personal wallet's pending balance");
            shared.addCount(coin, 50);
            boolean first = manager.getWallet(owner)
                .tryDeduct(coin, 30);
            boolean second = manager.getWallet(member)
                .tryDeduct(coin, 30);
            context.check(
                "shared-spend-no-double-charge",
                first && !second && shared.getCount(coin) == 20,
                "First member spends 30/50; second request for 30 rejects with balance 20");
            context.check(
                "outsider-isolated",
                manager.getWallet(outsider) == outsiderWallet && outsiderWallet.getCount(coin) == 9,
                "Independent identity cannot observe or consume team balance");
            context.check(
                "team-key-routing",
                team.getTeamId()
                    .equals(TeamDataProvider.resolveTeamKey(member))
                    && team.getTeamId()
                        .equals(com.miaokatze.gtit.lottery.LotteryManager.resolveTeamKey(owner)),
                "Wallet/lottery resolve synthetic members to the same real team key");
            team.removeMember(member);
            context.check(
                "leave-restores-frozen-personal",
                manager.getWallet(member) == memberPersonal && memberPersonal.getCount(coin) == 13
                    && shared.getCount(coin) == 20,
                "Real Team.removeMember invalidates cached team routing and restores personal snapshot");
            team.addMember(member);
            context.check(
                "rejoin-preserves-team-funds",
                manager.getWallet(member) == shared && shared.getCount(coin) == 20,
                "Rejoin neither duplicates funds nor imports the personal snapshot");
            context.skip(
                "team-command-network",
                "Team invite UI, permission packets and real player connections require client probes");
            return true;
        };
    }

    /** GTNHLib has no public remove-team API. Resolve its exact containers before creating a temporary team. */
    static Team createOwnedTeam(ProbeContext context, UUID owner) throws Exception {
        Collection<?> teams = (Collection<?>) NativeFixtures.field(TeamManager.class, "TEAMS");
        Map<?, ?> byId = (Map<?, ?>) NativeFixtures.field(TeamManager.class, "TEAM_MAP");
        Map<?, ?> players = (Map<?, ?>) NativeFixtures.field(TeamManager.class, "PLAYER_TEAM_CACHE");
        Map<?, ?> invites = (Map<?, ?>) NativeFixtures.field(TeamManager.class, "PENDING_INVITES");
        Map<?, ?> merges = (Map<?, ?>) NativeFixtures.field(TeamManager.class, "PENDING_MERGE_REQUESTS");
        Set<?> removed = (Set<?>) NativeFixtures.field(TeamManager.class, "REMOVED_TEAMS");
        Set<?> balanceKeys = (Set<?>) NativeFixtures.field(NekoWalletManager.INSTANCE, "dirtyBalanceKeys");
        File directory = TeamDataSaver.INSTANCE == null ? null
            : (File) NativeFixtures.field(TeamDataSaver.INSTANCE, "saveDir");
        String playerName = "GTITLab-" + context.runId();
        String expectedTeamName = playerName + "'s Team";
        Team[] created = new Team[1];
        context.onCleanup(() -> {
            Team team = created[0] != null ? created[0] : recoverCreatingTeam(players, owner, expectedTeamName);
            if (team == null) return;
            teams.remove(team);
            if (byId.get(team.getTeamId()) == team) byId.remove(team.getTeamId());
            players.entrySet()
                .removeIf(entry -> entry.getValue() == team);
            invites.entrySet()
                .removeIf(entry -> {
                    Set<?> values = (Set<?>) entry.getValue();
                    return values.remove(team) && values.isEmpty();
                });
            merges.remove(team);
            merges.entrySet()
                .removeIf(entry -> {
                    Set<?> values = (Set<?>) entry.getValue();
                    return values.remove(team) && values.isEmpty();
                });
            removed.remove(team.getTeamId());
            balanceKeys.remove("team:" + team.getTeamId());
            if (directory != null) {
                try {
                    Files.deleteIfExists(new File(directory, team.getTeamId() + ".json").toPath());
                } catch (java.io.IOException error) {
                    throw new IllegalStateException("Cannot remove synthetic team file", error);
                }
            }
            context.check(
                "cleanup-synthetic-team",
                !teams.contains(team) && byId.get(team.getTeamId()) != team
                    && !players.containsValue(team)
                    && !removed.contains(team.getTeamId())
                    && !balanceKeys.contains("team:" + team.getTeamId())
                    && (directory == null || !new File(directory, team.getTeamId() + ".json").exists()),
                "Only this team's list/map/cache/notification/file entries were removed");
        });
        created[0] = TeamManager.createTeam(playerName, owner);
        return created[0];
    }

    /** createTeam publishes this cache entry before TeamCreateEvent, so a throwing listener can prevent its return. */
    static Team recoverCreatingTeam(Map<?, ?> players, UUID owner, String expectedName) {
        Object candidate = players.get(owner);
        if (!(candidate instanceof Team)) return null;
        Team team = (Team) candidate;
        return expectedName.equals(team.getTeamName()) && team.isOwner(owner)
            && team.getOwners()
                .size() == 1
            && team.getMembers()
                .size() == 1 ? team : null;
    }
}
