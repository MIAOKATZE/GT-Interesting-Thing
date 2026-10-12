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
    private File snapshotFile;
    private NBTTagCompound blueprintSnapshot;
    private int scenario = -1;
    private static final String[] SCENARIOS = { "blueprint", "prompts", "gizmo", "missing", "missing-source",
        "versions", "materials", "nbt-storage", "nbt-placement", "resources", "missing-confirm", "force-ready",
        "blueprint" };

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
                snapshotFile = new File(path);
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
            String name = scenario < 0 ? "hologram-scale-" + scale
                : "hologram-" + SCENARIOS[scenario] + "-scale-" + scale;
            File screenshot = new File(new File(mc.mcDataDir, "screenshots"), name + ".png");
            if (screenshot.exists() && !screenshot.delete())
                throw new IllegalStateException("Cannot replace smoke screenshot: " + screenshot);
            ScreenShotHelper
                .saveScreenshot(mc.mcDataDir, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            if (!screenshot.isFile() || screenshot.length() == 0)
                throw new IllegalStateException("Screenshot not saved: " + screenshot);
            if (scenario >= 0
                && ("force-ready".equals(SCENARIOS[scenario]) || "missing-confirm".equals(SCENARIOS[scenario]))) {
                NBTTagCompound captured = screen.smokeCaptureMissingBuildClick();
                System.out.println(
                    "[GTIT-HOLOGRAM-CLIENT] PASS actual modal mouse click captured op=" + captured.getString("op")
                        + " revision="
                        + captured.getLong("planRevision")
                        + "; build reply reopens once, cancel survives later snapshot; no packet sent (fixture)");
            }
            ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            System.out.println(
                "[GTIT-HOLOGRAM-CLIENT] PASS scale=" + scale
                    + " actual="
                    + resolution.getScaleFactor()
                    + " "
                    + layout
                    + " screenshot="
                    + screenshot.getAbsolutePath());
            if (scenario >= 0 || scale == 3) nextScenario(mc);
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

    private void nextScenario(Minecraft mc) throws Exception {
        scenario++;
        if (scenario >= SCENARIOS.length) {
            finish(
                mc,
                true,
                "structure + real schema2 blueprint/prompt/gizmo/material/version render; missing-block texture is an explicit unresolved-registry fixture; no world action packet sent");
            return;
        }
        if (blueprintSnapshot == null) {
            File source = new File(
                System.getProperty(
                    "gtit.hologram.blueprintClientState",
                    new File(snapshotFile.getParentFile(), "hologram-blueprint-client-state.nbt").getPath()));
            try (InputStream input = new FileInputStream(source)) {
                blueprintSnapshot = CompressedStreamTools.readCompressed(input);
            }
            if (!blueprintSnapshot.getBoolean("virtual") || !blueprintSnapshot.hasKey("blueprintAwaitingConfirmation")
                || blueprintSnapshot.getTagList("cells", 10)
                    .tagCount() == 0)
                throw new IllegalStateException("Expected real schema2 blueprint snapshot: " + source);
            System.out.println("[GTIT-HOLOGRAM-CLIENT] real blueprint snapshot " + source.getAbsolutePath());
        }
        NBTTagCompound scene = (NBTTagCompound) blueprintSnapshot.copy();
        if ("missing-confirm".equals(SCENARIOS[scenario]) || "force-ready".equals(SCENARIOS[scenario])) {
            scene.setBoolean("virtualPlacement", true);
            scene.setBoolean("virtualLocked", true);
            scene.setBoolean("blueprintAwaitingConfirmation", false);
            scene.setBoolean("forcePlacementReady", "force-ready".equals(SCENARIOS[scenario]));
            scene.setBoolean("creativeMaterials", false);
            scene.setInteger("missing", 1);
            scene.setString("status", "缺料测试 fixture：再次确认仅放已有，缺口保持未放置");
            NBTTagCompound cell = scene.getTagList("cells", 10)
                .getCompoundTagAt(0);
            cell.setBoolean("inScope", true);
            cell.setString("operation", "PLACE");
            cell.setString("status", "missing");
        }
        if ("missing".equals(SCENARIOS[scenario]) || "missing-source".equals(SCENARIOS[scenario])) {
            NBTTagCompound cell = scene.getTagList("cells", 10)
                .getCompoundTagAt(0);
            cell.setString("wantId", "gtit_smoke_missing:block");
            cell.setBoolean("missingBlock", true);
            cell.setBoolean("anchor", false);
            cell.setBoolean("iconOnly", false);
            cell.setString("role", "");
            cell.removeTag("targetStack");
            cell.removeTag("actualStack");
            scene.setBoolean("sourceMissing", true);
            scene.setString("sourceClass", "gtit_smoke_missing.UnavailableController");
            scene.setString("sourceWarning", "旧蓝图来源类不可加载或控制器 ID 已复用（测试 fixture）");
            cell.setBoolean("sourceMissing", true);
            cell.setString("sourceClass", scene.getString("sourceClass"));
            cell.setString("sourceWarning", scene.getString("sourceWarning"));
            if ("missing-source".equals(SCENARIOS[scenario]))
                scene.setTag("modVersions", new net.minecraft.nbt.NBTTagList());
        }
        scale = scenario == SCENARIOS.length - 1 ? 3 : 1;
        mc.gameSettings.guiScale = scale;
        HologramScreen screen = new HologramScreen(new HologramState(scene));
        mc.displayGuiScreen(screen);
        screen.smokeRenderScenario(SCENARIOS[scenario]);
        stageAt = System.nanoTime();
    }

    private void finish(Minecraft mc, boolean passed, String reason) {
        finished = true;
        System.out.println("[GTIT-HOLOGRAM-CLIENT] FINAL " + (passed ? "PASS " : "FAIL ") + reason);
        mc.shutdown();
    }
}
