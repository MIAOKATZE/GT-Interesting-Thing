package com.miaokatze.gtit.terminal;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;

/** Reads real vanilla gzip data and the previous audit writer's legacy uncompressed data. */
public final class StarterGiftAuditNbtTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(StarterGiftAuditNbtTest.class, args)) return;
        Path folder = Files.createTempDirectory("gtit-gift-audit-");
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound forge = new NBTTagCompound();
        NBTTagCompound persisted = new NBTTagCompound();
        persisted.setBoolean(StarterGiftAudit.GIFT_CLAIMED_KEY, true);
        forge.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        root.setTag("ForgeData", forge);
        root.setString("otherMod", "must survive");
        Path gzip = folder.resolve("gzip.dat");
        Path legacy = folder.resolve("legacy.dat");
        try {
            try (OutputStream output = Files.newOutputStream(gzip)) {
                CompressedStreamTools.writeCompressed(root, output);
            }
            CompressedStreamTools.safeWrite(root, legacy.toFile());
            for (Path file : new Path[] { gzip, legacy }) {
                byte[] before = Files.readAllBytes(file);
                NBTTagCompound loaded = StarterGiftAudit.readPlayerData(file.toFile());
                if (!StarterGiftAudit.hasGiftClaimedFlag(loaded.getCompoundTag("ForgeData"))) {
                    throw new AssertionError("Claim flag missing: " + file);
                }
                if (!"must survive".equals(loaded.getString("otherMod"))
                    || !java.util.Arrays.equals(before, Files.readAllBytes(file))) {
                    throw new AssertionError("Read-only audit changed player data");
                }
            }
            System.out.println("StarterGiftAuditNbtTest: vanilla gzip and legacy NBT read without mutation passed");
        } finally {
            Files.deleteIfExists(gzip);
            Files.deleteIfExists(legacy);
            Files.deleteIfExists(folder);
        }
    }
}
