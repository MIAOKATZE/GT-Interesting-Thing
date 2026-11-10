package com.miaokatze.gtit.gift;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Inventory and receipts share the native canonical player file: level.dat for the integrated host, UUID.dat otherwise.
 */
final class GiftPlayerFiles {

    private GiftPlayerFiles() {}

    static void save(EntityPlayerMP player) throws IOException {
        NBTTagCompound root = new NBTTagCompound();
        player.writeToNBT(root);
        if (!player.mcServer.isDedicatedServer() && player.getCommandSenderName()
            .equals(player.mcServer.getServerOwner())) {
            GiftHostPlayerFile.save(player, root);
            return;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(root, bytes);
        Path folder = player.mcServer.worldServerForDimension(0)
            .getSaveHandler()
            .getWorldDirectory()
            .toPath()
            .resolve("playerdata");
        Files.createDirectories(folder);
        Path file = folder.resolve(player.getUniqueID() + ".dat");
        com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher.publishAtomic(file, bytes.toByteArray());
    }
}
