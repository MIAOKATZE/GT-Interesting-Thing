package com.miaokatze.gtit.testlab;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularScreen;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Loaded exclusively by ClientProxy on an explicitly opted-in test installation. */
public final class TestLabUiClient {

    private static final TestLabUiClient INSTANCE = new TestLabUiClient();
    private volatile TestLabUiNetwork.Request incoming;
    private TestLabUiNetwork.Request pending;
    private int ticks;
    private boolean invoked;

    private TestLabUiClient() {}

    public static void install() {
        if (!TestLabConfig.enabled || !TestLabConfig.clientUiEnabled || TestLabDimension.problem() != null) return;
        TestLabUiNetwork.setClientRequest(request -> INSTANCE.incoming = request);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (incoming != null) {
            pending = incoming;
            incoming = null;
            ticks = 0;
            invoked = false;
        }
        if (pending == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.theWorld == null
            || minecraft.thePlayer.dimension != pending.dimension) {
            finish(0, "client changed dimension or disconnected");
            return;
        }
        ItemStack held = minecraft.thePlayer.getCurrentEquippedItem();
        if (!invoked && ticks++ >= 3
            && TestLabUiNetwork.marked(held, pending.runId)
            && minecraft.thePlayer.inventory.currentItem == pending.slot) {
            try {
                // The real client controller emits the normal item-use packet; the normal item/GUI factories open the
                // UI.
                minecraft.playerController.sendUseItem(minecraft.thePlayer, minecraft.theWorld, held);
                invoked = true;
            } catch (Exception error) {
                finish(
                    0,
                    error.getClass()
                        .getSimpleName());
                return;
            }
        }
        ModularScreen screen = ModularScreen.getCurrent();
        if (invoked && screen != null
            && "gtit.pocket.main".equals(
                screen.getMainPanel()
                    .getName())) {
            finish(
                1,
                screen.getMainPanel()
                    .getName());
        } else if (++ticks >= 100) finish(0, "client UI did not open within 100 ticks");
    }

    private void finish(int result, String detail) {
        TestLabUiNetwork.acknowledge(pending, result, detail);
        pending = null;
    }
}
