package com.miaokatze.gtit.gui.vm;

import net.minecraft.network.PacketBuffer;

import com.miaokatze.gtit.trade.NekoTeamStatusServiceTest;

import io.netty.buffer.Unpooled;

public final class NekoTeamStatusSyncTest {

    public static void main(String[] args) throws Exception {
        NekoTeamStatusServiceTest.main(args);
        NekoTeamStatusSync sync = new NekoTeamStatusSync();
        for (int action = 0; action <= 4; action++) {
            sync.readOnServer(action, new PacketBuffer(Unpooled.buffer()));
        }
        for (int action : new int[] { -1, 5, Integer.MAX_VALUE }) {
            try {
                sync.readOnServer(action, new PacketBuffer(Unpooled.buffer()));
                throw new AssertionError("Arbitrary team action accepted");
            } catch (java.io.IOException expected) {}
        }
        PacketBuffer targetUuid = new PacketBuffer(Unpooled.buffer());
        targetUuid.writeLong(1)
            .writeLong(2);
        try {
            sync.readOnServer(0, targetUuid);
            throw new AssertionError("Client-supplied UUID accepted");
        } catch (java.io.IOException expected) {}
        System.out.println("NekoTeamStatusSyncTest: bounded navigation-only C2S requests passed");
    }
}
