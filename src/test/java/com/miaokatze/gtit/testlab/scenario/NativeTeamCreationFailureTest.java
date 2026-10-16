package com.miaokatze.gtit.testlab.scenario;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizon.gtnhlib.teams.Team;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

/** Mirrors GTNHLib's cache-before-event ordering using a local cache and local throwing callback. */
public final class NativeTeamCreationFailureTest {

    private NativeTeamCreationFailureTest() {}

    public static void main(String[] args) {
        Map<String, Runnable> tests = new LinkedHashMap<>();
        tests.put("creation callback failure recovers exact synthetic team", () -> {
            UUID owner = UUID.randomUUID();
            String expected = "GTITLab-" + UUID.randomUUID() + "'s Team";
            Map<UUID, Team> cache = new HashMap<>();
            Team team = new Team(expected, UUID.randomUUID(), false);
            team.addOwner(owner);
            Team[] returned = new Team[1];
            try {
                // Same relevant ordering as createTeam: cache put -> event -> return assignment.
                cache.put(owner, team);
                Runnable callback = () -> { throw new IllegalStateException("injected TeamCreateEvent failure"); };
                callback.run();
                returned[0] = team;
            } catch (IllegalStateException expectedFailure) {
                SimpleAssert.that(returned[0] == null, "Factory never returned");
            }
            Team recovered = TeamWalletNativeScenario.recoverCreatingTeam(cache, owner, expected);
            SimpleAssert.that(recovered == team, "Recover published test cache entry despite absent return value");
            cache.entrySet()
                .removeIf(entry -> entry.getValue() == recovered);
            SimpleAssert.that(cache.isEmpty(), "Exact recovered team cache removed");
        });
        tests.put("failure recovery preserves foreign names and owners", () -> {
            UUID owner = UUID.randomUUID();
            String expected = "GTITLab-test-run's Team";
            Map<UUID, Team> cache = new HashMap<>();
            Team foreign = new Team("foreign", UUID.randomUUID(), false);
            foreign.addOwner(owner);
            cache.put(owner, foreign);
            SimpleAssert.that(
                TeamWalletNativeScenario.recoverCreatingTeam(cache, owner, expected) == null,
                "Different name is not owned by this run");
            Team wrongOwner = new Team(expected, UUID.randomUUID(), false);
            wrongOwner.addOwner(UUID.randomUUID());
            cache.put(owner, wrongOwner);
            SimpleAssert.that(
                TeamWalletNativeScenario.recoverCreatingTeam(cache, owner, expected) == null,
                "Matching name without exact owner is not a fixture");
            Team multiMember = new Team(expected, UUID.randomUUID(), false);
            multiMember.addOwner(owner);
            multiMember.addMember(UUID.randomUUID());
            cache.put(owner, multiMember);
            SimpleAssert.that(
                TeamWalletNativeScenario.recoverCreatingTeam(cache, owner, expected) == null,
                "Partially created fixture must have exactly its initial owner");
            SimpleAssert.that(cache.get(owner) == multiMember, "Refused recovery never mutates foreign cache");
        });
        TestRunner.run(NativeTeamCreationFailureTest.class, tests);
    }
}
