package com.miaokatze.gtit.testlab;

import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;

/** A socket-free sink for normal server callbacks performed by synthetic actors. */
final class SilentProbeConnection extends NetHandlerPlayServer {

    SilentProbeConnection(FakePlayer actor) {
        super(MinecraftServer.getServer(), new NetworkManager(false), actor);
    }

    @Override
    public void sendPacket(Packet packet) {}
}
