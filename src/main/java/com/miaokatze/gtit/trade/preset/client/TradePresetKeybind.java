package com.miaokatze.gtit.trade.preset.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentTranslation;

import org.lwjgl.input.Keyboard;

import com.miaokatze.gtit.trade.preset.ui.TradePresetAccessHandler;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Installed exclusively by ClientProxy; authorization is supplied by the current server. */
@SideOnly(Side.CLIENT)
public final class TradePresetKeybind {

    private static KeyBinding binding;
    private static volatile boolean allowed;

    private TradePresetKeybind() {}

    public static synchronized void install() {
        if (binding != null) return;
        binding = new KeyBinding("key.gtit.trade_presets", Keyboard.KEY_I, "key.categories.gtit");
        ClientRegistry.registerKeyBinding(binding);
        FMLCommonHandler.instance()
            .bus()
            .register(new TradePresetKeybind());
        TradePresetAccessHandler.setClientReceiver(
            access -> Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    allowed = access.allowed;
                    if (access.remind && allowed
                        && binding.getKeyCode() != Keyboard.KEY_NONE
                        && Minecraft.getMinecraft().thePlayer != null)
                        Minecraft.getMinecraft().thePlayer.addChatComponentMessage(
                            new ChatComponentTranslation(
                                "gtit.preset.login_notice",
                                GameSettings.getKeyDisplayString(binding.getKeyCode())));
                }));
    }

    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        allowed = false;
    }

    @SubscribeEvent
    public void connect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        allowed = false;
    }

    @SubscribeEvent
    public void key(InputEvent.KeyInputEvent event) {
        openIfPressed();
    }

    @SubscribeEvent
    public void mouse(InputEvent.MouseInputEvent event) {
        openIfPressed();
    }

    private static void openIfPressed() {
        if (binding == null || binding.getKeyCode() == Keyboard.KEY_NONE || !binding.isPressed()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (allowed && mc.thePlayer != null && mc.currentScreen == null) TradePresetAccessHandler.requestOpen();
    }
}
