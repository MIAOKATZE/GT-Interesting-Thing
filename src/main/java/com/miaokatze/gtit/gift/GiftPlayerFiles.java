package com.miaokatze.gtit.gift;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/** Inventory and delivery receipts are committed together in vanilla's player.dat. */
final class GiftPlayerFiles {

    private GiftPlayerFiles() {}

    static void save(EntityPlayerMP player) throws IOException {
        NBTTagCompound root = new NBTTagCompound();
        player.writeToNBT(root);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(root, bytes);
        Path folder = player.mcServer.worldServerForDimension(0)
            .getSaveHandler()
            .getWorldDirectory()
            .toPath()
            .resolve("playerdata");
        Files.createDirectories(folder);
        Path file = folder.resolve(player.getUniqueID() + ".dat");
        Path temporary = folder.resolve(player.getUniqueID() + ".gtit-gift.tmp");
        try {
            Files.write(temporary, bytes.toByteArray());
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
