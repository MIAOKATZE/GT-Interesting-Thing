package com.miaokatze.gtit.client.hologram;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ScreenShotHelper;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Opt-in isolated render smoke. It loads a real server snapshot and never sends an action packet. */
public final class HologramClientSmoke {

    private final long installedAt = System.nanoTime();
    private NBTTagCompound snapshot;
    private int scale = 0;
    private long stageAt;
    private boolean finished;

    static void install() {
        if (Boolean.getBoolean("gtit.hologram.clientSmoke")) FMLCommonHandler.instance()
            .bus()
            .register(new HologramClientSmoke());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (scale == 0) {
                if (!(mc.currentScreen instanceof GuiMainMenu)) {
                    if (System.nanoTime() - installedAt > 300000000000L)
                        throw new IllegalStateException("MainMenu initialization timed out");
                    return;
                }
                String path = System.getProperty("gtit.hologram.clientState", "");
                if (path.isEmpty()) throw new IllegalStateException("Missing gtit.hologram.clientState");
                try (InputStream input = new FileInputStream(new File(path))) {
                    snapshot = CompressedStreamTools.readCompressed(input);
                }
                if (snapshot == null || snapshot.getTagList("cells", 10)
                    .tagCount() == 0 || !snapshot.getBoolean("target")) {
                    throw new IllegalStateException("Expected a real target state with captured cells");
                }
                System.out.println(
                    "[GTIT-HOLOGRAM-CLIENT] real snapshot " + new File(path).getAbsolutePath()
                        + " cells="
                        + snapshot.getTagList("cells", 10)
                            .tagCount());
                nextScale(mc);
                return;
            }
            if (System.nanoTime() - stageAt < 2000000000L) return;
            if (!(mc.currentScreen instanceof HologramScreen))
                throw new IllegalStateException("Hologram screen was replaced");
            HologramScreen screen = (HologramScreen) mc.currentScreen;
            String layout = screen.smokeLayout();
            int glError = GL11.glGetError();
            if (glError != GL11.GL_NO_ERROR) throw new IllegalStateException("OpenGL error " + glError);
            File screenshot = new File(new File(mc.mcDataDir, "screenshots"), "hologram-scale-" + scale + ".png");
            if (screenshot.exists() && !screenshot.delete())
                throw new IllegalStateException("Cannot replace smoke screenshot: " + screenshot);
            ScreenShotHelper.saveScreenshot(
                mc.mcDataDir,
                "hologram-scale-" + scale + ".png",
                mc.displayWidth,
                mc.displayHeight,
                mc.getFramebuffer());
            if (!screenshot.isFile() || screenshot.length() == 0)
                throw new IllegalStateException("Screenshot not saved: " + screenshot);
            ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            System.out.println(
                "[GTIT-HOLOGRAM-CLIENT] PASS scale=" + scale
                    + " actual="
                    + resolution.getScaleFactor()
                    + " "
                    + layout
                    + " screenshot="
                    + screenshot.getAbsolutePath());
            if (scale == 3)
                finish(mc, true, "render/layout/GL only; no world interaction or construction was exercised");
            else nextScale(mc);
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(mc, false, failure.toString());
        }
    }

    private void nextScale(Minecraft mc) {
        scale++;
        mc.gameSettings.guiScale = scale;
        mc.displayGuiScreen(new HologramScreen(new HologramState(snapshot)));
        stageAt = System.nanoTime();
    }

    private void finish(Minecraft mc, boolean passed, String reason) {
        finished = true;
        System.out.println("[GTIT-HOLOGRAM-CLIENT] FINAL " + (passed ? "PASS " : "FAIL ") + reason);
        mc.shutdown();
    }
}
