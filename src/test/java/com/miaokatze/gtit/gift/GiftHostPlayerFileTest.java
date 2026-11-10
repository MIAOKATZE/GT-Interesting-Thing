package com.miaokatze.gtit.gift;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher;

public final class GiftHostPlayerFileTest {

    private static final class Info {

        private NBTTagCompound playerTag;
    }

    private static final class Host {

        private NBTTagCompound hostPlayerData;
    }

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(GiftHostPlayerFileTest.class, args)) return;
        Path dir = Files.createTempDirectory("gtit-host-save-");
        Path file = dir.resolve("level.dat");
        try {
            byte[] baseline = { 1, 2, 3 };
            Files.write(file, baseline);
            Info info = new Info();
            Host host = new Host();
            info.playerTag = new NBTTagCompound();
            host.hostPlayerData = new NBTTagCompound();
            NBTTagCompound oldInfo = info.playerTag, oldHost = host.hostPlayerData;
            Field world = Info.class.getDeclaredField("playerTag");
            world.setAccessible(true);
            Field hostField = GiftHostPlayerFile.findHostField(Host.class);
            NBTTagCompound player = new NBTTagCompound();
            player.setString("inventory-and-receipt", "same-commit");
            NBTTagCompound document = new NBTTagCompound(), data = new NBTTagCompound();
            data.setTag("Player", player);
            data.setString("unrelated-world-state", "preserve");
            document.setTag("Data", data);
            try {
                GiftHostPlayerFile.publish(
                    file,
                    document,
                    info,
                    world,
                    host,
                    hostField,
                    player,
                    (target, bytes) -> { throw new java.io.IOException("injected publication failure"); });
                throw new AssertionError("publication failure must propagate");
            } catch (java.io.IOException expected) {}
            check(
                Arrays.equals(baseline, Files.readAllBytes(file)) && info.playerTag == oldInfo
                    && host.hostPlayerData == oldHost,
                "failure preserves file and both snapshot identities");
            GiftHostPlayerFile.publish(
                file,
                document,
                info,
                world,
                host,
                hostField,
                player,
                (target, bytes) -> AtomicFilePublisher.publishAtomic(target, bytes));
            NBTTagCompound reread;
            try (java.io.InputStream input = Files.newInputStream(file)) {
                reread = CompressedStreamTools.readCompressed(input);
            }
            check(
                "same-commit".equals(
                    reread.getCompoundTag("Data")
                        .getCompoundTag("Player")
                        .getString("inventory-and-receipt"))
                    && "preserve".equals(
                        reread.getCompoundTag("Data")
                            .getString("unrelated-world-state")),
                "canonical level document retains world state and one player snapshot");
            check(
                info.playerTag.equals(player) && host.hostPlayerData.equals(player)
                    && info.playerTag != player
                    && host.hostPlayerData != player,
                "both native read/save snapshots updated with copies");
            player.setString("inventory-and-receipt", "changed-after-save");
            check(
                "same-commit".equals(host.hostPlayerData.getString("inventory-and-receipt")),
                "caller mutation cannot change host snapshot");
            System.out.println("PASS GiftHostPlayerFileTest 4 contracts");
        } finally {
            Files.deleteIfExists(file);
            Files.delete(dir);
        }
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
