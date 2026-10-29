package com.miaokatze.gtit.terminal;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client terminal drafts and replies belong to one connection, not the entire Minecraft process. */
@SideOnly(Side.CLIENT)
public final class TerminalClientSession {

    private static final AtomicLong generation = new AtomicLong();
    private static boolean installed;

    private TerminalClientSession() {}

    public static synchronized void install() {
        if (installed) return;
        FMLCommonHandler.instance()
            .bus()
            .register(new TerminalClientSession());
        installed = true;
    }

    static void enqueue(MessageContext context, Runnable action) {
        Object connection = context.netHandler;
        long expected = generation.get();
        Minecraft mc = Minecraft.getMinecraft();
        mc.func_152344_a(() -> {
            // A reply queued before disconnect must not reopen a screen or populate the next world's cache.
            if (generation.get() != expected || connection == null || mc.getNetHandler() != connection
                || mc.theWorld == null || mc.thePlayer == null) return;
            action.run();
        });
    }

    @SubscribeEvent
    public void connect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        invalidate();
    }

    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        invalidate();
    }

    private static void invalidate() {
        // Events can arrive on the network thread. Invalidate immediately, but only mutate GUI state on main.
        long expected = generation.incrementAndGet();
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                if (generation.get() != expected) return;
                TerminalClientData.resetSession();
                com.miaokatze.gtit.mail.MailClientData.resetSession();
                com.miaokatze.gtit.signin.SignInClientData.resetSession();
                com.miaokatze.gtit.lottery.LotteryClientData.resetSession();
                com.miaokatze.gtit.trade.NekoClientBalances.updateBalances(java.util.Collections.emptyMap());
                TerminalMailPage.resetSession();
                TerminalSignInPage.resetSession();
                TerminalTradePage.resetSession();
                TerminalGiftPage.resetSession();
            });
    }
}
