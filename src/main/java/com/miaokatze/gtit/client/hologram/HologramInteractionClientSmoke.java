package com.miaokatze.gtit.client.hologram;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Uses a real socket, PlayerController C08, GUI mouse path and server-owned receipts. */
public final class HologramInteractionClientSmoke {

    private final long installed = System.nanoTime();
    private int stage, waitTicks;
    private boolean done;
    private File receipts;
    private HologramScreen realScreen;
    private long lastClickedRender = -1;
    private long renderedWorldFrames, closedAtWorldFrame;
    private int receiptReadRetries;
    private final int[] initialClicks = { 71, 72, 70, 82, 80, 60 };

    public static void install() {
        if (Boolean.getBoolean("gtit.hologram.realInteractionSmoke")) {
            HologramInteractionClientSmoke driver = new HologramInteractionClientSmoke();
            FMLCommonHandler.instance()
                .bus()
                .register(driver);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(driver);
        }
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void worldRendered(net.minecraftforge.client.event.RenderWorldLastEvent event) {
        renderedWorldFrames++;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (System.nanoTime() - installed > 600000000000L)
                throw new IllegalStateException("real interaction timeout stage=" + stage);
            if (stage == 0) {
                if (!(mc.currentScreen instanceof GuiMainMenu)) return;
                receipts = new File(System.getProperty("gtit.hologram.interactionReceiptDir", ""));
                if (!receipts.isDirectory()) throw new IllegalStateException("prepared receipt directory required");
                String address = System.getProperty("gtit.hologram.interactionAddress", "127.0.0.1:25581");
                if (!address.equals("127.0.0.1:25581"))
                    throw new IllegalStateException("isolated localhost address guard");
                cpw.mods.fml.client.FMLClientHandler.instance()
                    .setupServerList();
                cpw.mods.fml.client.FMLClientHandler.instance()
                    .connectToServer(mc.currentScreen, new ServerData("GTIT isolated interaction", address));
                stage = 1;
                return;
            }
            if (stage == 1) {
                if (mc.thePlayer == null || mc.theWorld == null || !new File(receipts, "world-receipt.nbt").isFile())
                    return;
                if (mc.thePlayer.getHeldItem() == null || mc.theWorld.getTileEntity(48, 80, 48) == null) return;
                mc.displayGuiScreen(null);
                mc.playerController.onPlayerRightClick(
                    mc.thePlayer,
                    mc.theWorld,
                    mc.thePlayer.getHeldItem(),
                    48,
                    80,
                    48,
                    2,
                    Vec3.createVectorHelper(48.5, 80.5, 48));
                stage = 2;
                return;
            }
            if (stage == 30) {
                if (renderedWorldFrames <= closedAtWorldFrame) return;
                waitTicks = 0;
                require(HologramRenderer.lastWorldGeometry > 0, "cached_world_material_geometry_" + stage);
                require(HologramRenderer.lastWorldGTGeometry > 0, "world_renders_GT_material_models_" + stage);
                require(HologramRenderer.lastWorldGhosts > 0, "real_world_has_ghost_cells_" + stage);
                screenshot(mc, "world-materials");
                reopenWithRightClick(mc);
                stage = 3;
                return;
            }
            if (!(mc.currentScreen instanceof HologramScreen)) return;
            HologramScreen screen = (HologramScreen) mc.currentScreen;
            if (screen.smokeRenderGeneration() <= lastClickedRender) return;
            NBTTagCompound state = screen.smokeSnapshot();
            if (screen.smokePending()) return;
            if (++waitTicks < 4) return;
            waitTicks = 0;
            if (stage == 2) {
                if (state.getBoolean("capturePending")) return;
                require(
                    state.getBoolean("target") && state.getBoolean("supported")
                        && state.getTagList("cells", 10)
                            .tagCount() == 36,
                    "real_C08_state_opens_full_EBF_GUI");
                awaitControl(screen, 81);
                require(HologramRenderer.lastPreviewAnchorMarkers > 0, "controller_marker_drawn_in_real_preview");
                verifyAnimatedItemAtlas(mc);
                screenshot(mc, "initial-GUI");
                click(screen, 81);
                stage = 28;
                return;
            }
            if (stage >= 3 && stage < 3 + initialClicks.length) {
                click(screen, initialClicks[stage - 3]);
                stage++;
                return;
            }
            switch (stage) {
                case 28:
                    awaitControl(screen, 63);
                    screenshot(mc, "grade-options");
                    click(screen, 63);
                    stage = 29;
                    break;
                case 29:
                    realScreen = screen;
                    mc.thePlayer.rotationYaw = 0;
                    mc.thePlayer.rotationPitch = 20;
                    HologramClient.worldPreview = true;
                    closedAtWorldFrame = renderedWorldFrames;
                    mc.displayGuiScreen(null);
                    stage = 30;
                    break;
                case 9:
                    click(screen, 33);
                    stage = 32;
                    break;
                case 32:
                    click(screen, 62);
                    stage = 31;
                    break;
                case 31:
                    require(state.getInteger("mode") == 0, "three_modes_grade_buttons_acknowledged");
                    click(screen, 50);
                    stage = 11;
                    break;
                case 11:
                    if (state.getInteger("job") != 3 && state.getInteger("job") != 5) return;
                    require(receipt().getInteger("placed") > 0, "direct_build_server_world_and_material_receipt");
                    require(mc.currentScreen == screen, "direct_build_retains_GUI_for_real_progress");
                    require(!state.hasKey("layerDuration"), "direct_build_has_no_layer_animation_schedule");
                    click(screen, 60);
                    stage = 25;
                    break;
                case 25:
                    click(screen, 23);
                    stage = 26;
                    break;
                case 26:
                    click(screen, 62);
                    stage = 27;
                    break;
                case 27:
                    click(screen, 50);
                    stage = 17;
                    break;
                case 17:
                    if (state.getInteger("job") != 3 && state.getInteger("job") != 5) return;
                    require(receipt().getInteger("coils") == 16, "build_installs_sixteen_real_coils");
                    click(screen, 71);
                    stage++;
                    break;
                case 18:
                    click(screen, 82);
                    stage++;
                    break;
                case 19:
                    click(screen, 50);
                    stage = 21;
                    break;
                case 21:
                    if (state.getInteger("job") != 3 && state.getInteger("job") != 5) return;
                    require(
                        receipt().getInteger("coils") == 16 && receipt().getInteger("highCoils") == 16,
                        "upgrade_changes_all_sixteen_real_coils_and_preserves_material_conservation");
                    click(screen, 72);
                    stage++;
                    break;
                case 22:
                    click(screen, 50);
                    stage = 24;
                    break;
                case 24:
                    if (state.getInteger("job") != 3 && state.getInteger("job") != 5) return;
                    NBTTagCompound receipt = receipt();
                    require(
                        receipt.getInteger("placed") == 0 && receipt.getInteger("stock") == 192,
                        "dismantle_returns_all_shell_materials_exactly");
                    require(
                        !state.getBoolean("targetClosed") && mc.theWorld.getTileEntity(48, 80, 48) != null,
                        "dismantle_preserves_controller_target");
                    Files.write(
                        new File(receipts, "client-complete.txt").toPath(),
                        "real socket C08 + GUI mouse + direct-build/upgrade/full-dismantle PASS\n"
                            .getBytes(StandardCharsets.UTF_8));
                    done = true;
                    System.out.println("[GTIT-HOLOGRAM-INTERACTION-CLIENT] FINAL PASS stage=" + stage);
                    mc.shutdown();
                    break;
                default:
                    throw new IllegalStateException("unknown stage " + stage);
            }
        } catch (AwaitReceipt pendingReceipt) {
            // A Windows receipt publication can briefly replace the target; preserve this driver stage.
        } catch (AwaitDraw pendingDraw) {
            // A hidden client can tick many times before drawing its next frame.
        } catch (Throwable failure) {
            done = true;
            System.err.println("[GTIT-HOLOGRAM-INTERACTION-CLIENT] FINAL FAIL stage=" + stage + " " + failure);
            failure.printStackTrace();
            mc.shutdown();
        }
    }

    private void click(HologramScreen screen, int id) throws Exception {
        awaitControl(screen, id);
        screen.smokeClickControl(id);
        lastClickedRender = screen.smokeRenderGeneration();
        System.out.println("[GTIT-HOLOGRAM-INTERACTION-CLIENT] mouse control=" + id + " stage=" + stage);
    }

    private void reopenWithRightClick(Minecraft mc) {
        mc.playerController.onPlayerRightClick(
            mc.thePlayer,
            mc.theWorld,
            mc.thePlayer.getHeldItem(),
            48,
            80,
            48,
            2,
            Vec3.createVectorHelper(48.5, 80.5, 48));
        lastClickedRender = -1;
        waitTicks = 0;
    }

    private void awaitControl(HologramScreen screen, int id) {
        if (!screen.smokeHasControl(id)) throw new AwaitDraw();
    }

    private static final class AwaitDraw extends RuntimeException {
    }

    private static final class AwaitReceipt extends RuntimeException {
    }

    private void screenshot(Minecraft mc, String name) throws Exception {
        int error = GL11.glGetError();
        require(error == GL11.GL_NO_ERROR, "OpenGL_no_error_" + name + "_error=" + error);
        String filename = "interaction-" + name + ".png";
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, filename, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        File image = new File(new File(mc.mcDataDir, "screenshots"), filename);
        require(image.isFile() && image.length() > 0, "saved_real_network_screenshot_" + name);
        System.out.println(
            "[GTIT-HOLOGRAM-INTERACTION-CLIENT] RENDER geometry=" + HologramRenderer.lastWorldGeometry
                + " GTgeometry="
                + HologramRenderer.lastWorldGTGeometry
                + " fallback="
                + HologramRenderer.lastWorldFallback
                + " ghost="
                + HologramRenderer.lastWorldGhosts
                + " matched="
                + HologramRenderer.lastWorldMatched
                + " screenshot="
                + image.getAbsolutePath());
    }

    private void verifyAnimatedItemAtlas(Minecraft mc) {
        Object texture = mc.getTextureManager()
            .getTexture(TextureMap.locationItemsTexture);
        require(texture instanceof TextureMap, "real_item_atlas_loaded");
        TextureAtlasSprite sprite = ((TextureMap) texture).getAtlasSprite("gtit:neko_hologram_projector");
        require(
            sprite != null && !sprite.getIconName()
                .equals("missingno"),
            "projector_sprite_registered_in_item_atlas");
        require(
            sprite.hasAnimationMetadata() && sprite.getFrameCount() > 1,
            "projector_atlas_animation_metadata_and_multiple_frames_registered");
        boolean distinct = false;
        int[] first = sprite.getFrameTextureData(0)[0];
        for (int i = 1; i < sprite.getFrameCount(); i++)
            if (!java.util.Arrays.equals(first, sprite.getFrameTextureData(i)[0])) distinct = true;
        require(distinct, "projector_atlas_contains_distinct_animation_frames");
    }

    private NBTTagCompound receipt() throws Exception {
        byte[] encoded;
        try {
            // Close the filesystem handle before decompression to minimize Windows replace conflicts.
            encoded = Files.readAllBytes(new File(receipts, "world-receipt.nbt").toPath());
            receiptReadRetries = 0;
        } catch (java.nio.file.FileSystemException unavailable) {
            String reason = String.valueOf(unavailable.getReason())
                .toLowerCase(java.util.Locale.ROOT);
            boolean transientPublication = unavailable instanceof java.nio.file.NoSuchFileException
                || reason.contains("另一个程序")
                || reason.contains("another process")
                || reason.contains("being used")
                || reason.contains("sharing violation");
            if (!transientPublication || ++receiptReadRetries > 20) throw unavailable;
            throw new AwaitReceipt();
        }
        try (java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(encoded)) {
            return CompressedStreamTools.readCompressed(input);
        }
    }

    private void require(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        System.out.println("[GTIT-HOLOGRAM-INTERACTION-CLIENT] ASSERT PASS " + label);
    }
}
