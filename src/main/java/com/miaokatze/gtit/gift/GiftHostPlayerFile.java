// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gift;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraft.world.storage.WorldInfo;

import com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.ReflectionHelper;

/** A single strict level.dat publication for the integrated host's inventory and receipts. */
final class GiftHostPlayerFile {

    interface Publisher {

        void publish(Path file, byte[] bytes) throws IOException;
    }

    private GiftHostPlayerFile() {}

    static void save(EntityPlayerMP player, NBTTagCompound playerTag) throws IOException {
        net.minecraft.world.WorldServer world = player.mcServer.worldServerForDimension(0);
        if (!(world.getSaveHandler() instanceof SaveHandler handler))
            throw new IOException("Integrated gift save requires the native SaveHandler");
        Object hostList = player.mcServer.getConfigurationManager();
        WorldInfo info = world.getWorldInfo();
        // Resolve both fields before any disk publication. No client-only class is linked on dedicated servers.
        Field worldPlayer = ReflectionHelper.findField(WorldInfo.class, "playerTag", "field_76108_i");
        Field hostPlayer = findHostField(hostList.getClass());
        drainEarlierWorldWrites();
        NBTTagCompound document = new NBTTagCompound();
        document.setTag("Data", info.cloneNBTCompound(playerTag));
        FMLCommonHandler.instance()
            .handleWorldDataSave(handler, info, document);
        publish(
            world.getSaveHandler()
                .getWorldDirectory()
                .toPath()
                .resolve("level.dat"),
            document,
            info,
            worldPlayer,
            hostList,
            hostPlayer,
            playerTag,
            (file, bytes) -> AtomicFilePublisher.publishAtomic(file, bytes));
    }

    static Field findHostField(Class<?> type) throws IOException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field field = ReflectionHelper.findField(cursor, "hostPlayerData", "field_72416_e");
                if (field.getType() != NBTTagCompound.class)
                    throw new IOException("Unexpected host player snapshot type");
                return field;
            } catch (ReflectionHelper.UnableToFindFieldException absent) { /* inspect parent */ }
        }
        throw new IOException("Integrated host player snapshot is unavailable");
    }

    static void publish(Path file, NBTTagCompound document, Object info, Field worldPlayer, Object hostList,
        Field hostPlayer, NBTTagCompound playerTag, Publisher publisher) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(document, bytes);
        Object oldWorld, oldHost;
        try {
            oldWorld = worldPlayer.get(info);
            oldHost = hostPlayer.get(hostList);
        } catch (IllegalAccessException failure) {
            throw new IOException("Cannot inspect host snapshots", failure);
        }
        try {
            worldPlayer.set(info, playerTag.copy());
            hostPlayer.set(hostList, playerTag.copy());
            publisher.publish(file, bytes.toByteArray());
        } catch (IOException | RuntimeException | IllegalAccessException failure) {
            try {
                worldPlayer.set(info, oldWorld);
                hostPlayer.set(hostList, oldHost);
            } catch (IllegalAccessException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            if (failure instanceof IOException io) throw io;
            throw new IOException("Integrated host gift save was not committed", failure);
        }
    }

    private static void drainEarlierWorldWrites() throws IOException {
        try {
            ThreadedFileIOBase.threadedIOInstance.waitForFinish();
            // Newer Hodgepodge retains failed tasks; its explicit flush retries and reports that boundary.
            try {
                Class<?> saver = Class.forName("com.mitchej123.hodgepodge.util.WorldDataSaver");
                saver.getMethod("flush")
                    .invoke(
                        saver.getField("INSTANCE")
                            .get(null));
            } catch (ClassNotFoundException
                | NoSuchMethodException absent) { /* vanilla or earlier synchronous contract */ }
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            throw new IOException("Interrupted while draining earlier world writes", interrupted);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof InterruptedException) Thread.currentThread()
                .interrupt();
            throw new IOException("Earlier asynchronous world writes did not complete", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IOException("Cannot verify asynchronous world save completion", failure);
        }
    }
}
