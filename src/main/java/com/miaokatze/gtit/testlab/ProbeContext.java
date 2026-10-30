package com.miaokatze.gtit.testlab;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import com.mojang.authlib.GameProfile;

/** Scenario fixtures are confined to one canvas and use identities unique to this run. */
public final class ProbeContext {

    public static final String TEST_ITEM_TAG = "GTITTestLab";
    private final WorldServer world;
    private final TestLabReport report;
    private final String scenario;
    private final int originX;
    private final Map<String, FakePlayer> players = new LinkedHashMap<>();
    private final List<Runnable> cleanup = new ArrayList<>();
    private int placed;

    ProbeContext(WorldServer world, TestLabReport report, String scenario, boolean stress) {
        this.world = world;
        this.report = report;
        this.scenario = scenario;
        originX = stress ? 1048576 : 0;
    }

    public WorldServer world() {
        return world;
    }

    public String runId() {
        return report.runId;
    }

    public int x(int dx) {
        if (Math.abs((long) dx) > 256) throw new IllegalArgumentException("Fixture outside lab canvas");
        return originX + dx;
    }

    public int y(int dy) {
        return TestLabBounds.height(dy);
    }

    public int z(int dz) {
        if (Math.abs((long) dz) > 256) throw new IllegalArgumentException("Fixture outside lab canvas");
        return dz;
    }

    /** Creates disconnected actors; never implicitly adds them to the real player list. */
    public FakePlayer player(String role) {
        return players.computeIfAbsent(role, key -> {
            UUID id = UUID.nameUUIDFromBytes((runId() + ":" + scenario + ":" + key).getBytes(StandardCharsets.UTF_8));
            FakePlayer player = new FakePlayer(
                world,
                new GameProfile(
                    id,
                    "GTITLab" + id.toString()
                        .substring(0, 8)));
            new SilentProbeConnection(player);
            player.setPosition(x(0) + 0.5, y(2), z(0) + 0.5);
            return player;
        });
    }

    public void check(String name, boolean passed, String detail) {
        report.add(scenario, name, passed ? "PASS" : "FAIL", detail);
    }

    public void skip(String name, String reason) {
        report.add(scenario, name, "SKIP", reason);
    }

    public void metric(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException("Non-finite metric");
        report.metrics.put(scenario + "." + name, value);
    }

    public boolean setBlock(int dx, int dy, int dz, Block block, int meta) {
        if (++placed > 4096) throw new IllegalStateException("Fixture block budget exceeded (4096)");
        return TestLabOwnership.get(world)
            .put(world, runId(), x(dx), y(dy), z(dz), block, meta);
    }

    public boolean placeNativeBlock(int dx, int dy, int dz, Block block, int meta,
        java.util.function.BooleanSupplier placement) {
        if (++placed > 4096) throw new IllegalStateException("Fixture block budget exceeded (4096)");
        return TestLabOwnership.get(world)
            .placeNative(world, runId(), x(dx), y(dy), z(dz), block, meta, placement);
    }

    public ItemStack mark(ItemStack stack) {
        if (stack == null) throw new IllegalArgumentException("Null test item");
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound marker = new NBTTagCompound();
        marker.setString("runId", runId());
        marker.setString("scenario", scenario);
        marker.setBoolean("testOnly", true);
        stack.getTagCompound()
            .setTag(TEST_ITEM_TAG, marker);
        return stack;
    }

    /** Register undo immediately after creating any business-domain fixture (wallet, team, etc.). */
    public void onCleanup(Runnable action) {
        cleanup.add(action);
    }

    void close() {
        for (int i = cleanup.size() - 1; i >= 0; i--) {
            try {
                cleanup.get(i)
                    .run();
            } catch (Exception | LinkageError error) {
                check("cleanup", false, error.toString());
            }
        }
        cleanup.clear();
        for (FakePlayer player : players.values()) {
            try {
                player.closeScreen();
                player.inventory.clearInventory(null, -1);
                player.setDead();
            } catch (Exception | LinkageError error) {
                check("actor-cleanup", false, error.toString());
            }
        }
        players.clear();
    }
}
