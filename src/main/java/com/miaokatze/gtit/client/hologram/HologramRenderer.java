package com.miaokatze.gtit.client.hologram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.ForgeHooksClient;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.structurelib.StructureLibAPI;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.blocks.ItemMachines;

/** Textured, depth-sorted block geometry using the actual block atlas, without a fake world. */
final class HologramRenderer {

    private static final int[][] FACES = { { 0, 1, 5, 4 }, { 3, 7, 6, 2 }, { 0, 3, 2, 1 }, { 4, 5, 6, 7 },
        { 0, 4, 7, 3 }, { 1, 2, 6, 5 } };
    private static final int[][] EDGES = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, { 4, 5 }, { 5, 6 }, { 6, 7 },
        { 7, 4 }, { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } };
    private static final double[][] VERTICES = { { -.5, -.5, -.5 }, { .5, -.5, -.5 }, { .5, .5, -.5 }, { -.5, .5, -.5 },
        { -.5, -.5, .5 }, { .5, -.5, .5 }, { .5, .5, .5 }, { -.5, .5, .5 } };

    private HologramRenderer() {}

    static int lastWorldGeometry, lastWorldGTGeometry, lastWorldFallback, lastWorldMatched, lastWorldGhosts;
    static int lastPreviewRoleMarkers, lastPreviewAnchorMarkers;
    private static boolean loggedNativeFailure;
    private static final java.lang.reflect.Method ANGELICA_WORLD_PASS = angelicaWorldPass();
    private static final java.lang.reflect.Field FORGE_WORLD_PASS = forgeWorldPass();
    private static int lastNativeWorldPass = -1;

    static int preview(HologramState state, int x, int y, int width, int height, double yaw, double pitch, double zoom,
        int layer, int view, int selected, int mouseX, int mouseY, double rehearsal) {
        List<Face> faces = new ArrayList<>();
        List<RoleMarker> markers = new ArrayList<>();
        lastPreviewRoleMarkers = lastPreviewAnchorMarkers = 0;
        double cx = 0, cy = 0, cz = 0, extent = 1;
        if (!state.cells.isEmpty()) {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = minX, maxZ = maxX;
            for (HologramState.Cell c : state.cells) {
                minX = Math.min(minX, c.dx);
                maxX = Math.max(maxX, c.dx);
                minZ = Math.min(minZ, c.dz);
                maxZ = Math.max(maxZ, c.dz);
            }
            cx = (minX + maxX) / 2;
            cy = (state.minY + state.maxY) / 2.0;
            cz = (minZ + maxZ) / 2;
            extent = Math.max(maxX - minX + 2, Math.max(maxZ - minZ + 2, state.maxY - state.minY + 2));
        }
        double scale = Math.min(width, height) * .72 / extent * zoom;
        for (HologramState.Cell c : state.cells) {
            if ((!c.anchor && c.dy > layer) || (!c.anchor && view == 2 && c.status.equals("satisfied"))) continue;
            double drop = 0; // The GUI is a static selectable model, never a construction rehearsal.
            boolean current = view == 1 || state.data.getInteger("mode") == 2;
            Block block = c.block(current);
            int meta = current ? c.meta : c.wantMeta;
            if (block == null) block = c.block(true);
            double[][] projected = new double[8][];
            for (int v = 0; v < 8; v++) {
                double px = c.dx - cx + VERTICES[v][0], pz = c.dz - cz + VERTICES[v][2];
                double py = c.dy - cy + VERTICES[v][1] + drop;
                double rx = px * Math.cos(yaw) - pz * Math.sin(yaw);
                double rz = px * Math.sin(yaw) + pz * Math.cos(yaw);
                double ry = py * Math.cos(pitch) - rz * Math.sin(pitch);
                double depth = py * Math.sin(pitch) + rz * Math.cos(pitch);
                projected[v] = new double[] { x + width / 2.0 + rx * scale, y + height / 2.0 - ry * scale, depth };
            }
            String role = roleLabel(c);
            if (!role.isEmpty()) markers.add(new RoleMarker(c, projected, role));
            for (int side = 0; side < 6; side++) {
                IIcon icon = icon(c, block, meta, side, current);
                faces.add(new Face(c, projected, side, icon, itemAtlas(c, current)));
            }
        }
        faces.sort(Comparator.comparingDouble(f -> f.depth));
        int hovered = -1;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            // GUI ortho spans +/-1000 around z=0. Normalize model depth independently of zoom,
            // and clear only this viewport so textured faces and outlines share reliable occlusion.
            Minecraft mc = Minecraft.getMinecraft();
            int factor = new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
                .getScaleFactor();
            // HologramScreen supplies a framebuffer scissor after its origin/scale transform.
            // x/y here are model-local coordinates; replacing that rectangle clips the model
            // against the wrong part of the screen. Retain the caller's transformed viewport.
            if (!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(x * factor, mc.displayHeight - (y + height) * factor, width * factor, height * factor);
            }
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glClearDepth(1);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            double depthScale = 128 / extent;
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            int atlas = 0;
            for (Face face : faces) {
                int nextAtlas = face.atlas;
                if (atlas != nextAtlas) {
                    t.draw();
                    atlas = nextAtlas;
                    Minecraft.getMinecraft()
                        .getTextureManager()
                        .bindTexture(atlas == 1 ? TextureMap.locationItemsTexture : TextureMap.locationBlocksTexture);
                    t.startDrawingQuads();
                }
                boolean inside = face.contains(mouseX, mouseY) && mouseX >= x
                    && mouseX < x + width
                    && mouseY >= y
                    && mouseY < y + height;
                if (inside) hovered = face.cell.index;
                int color = view == 1 ? 0xffffff : face.cell.textureColor();
                if (view != 1 && state.data.getInteger("mode") == 2 && face.cell.status.equals("pending"))
                    color = 0xf08b83;
                float shade = face.side == 1 ? 1 : face.side < 2 ? .6f : .8f;
                t.setColorRGBA_F(
                    ((color >> 16) & 255) / 255f * shade,
                    ((color >> 8) & 255) / 255f * shade,
                    (color & 255) / 255f * shade,
                    .86f);
                if (face.icon != null) {
                    for (int i = 0; i < 4; i++) {
                        double[] p = face.points[FACES[face.side][i]];
                        double u = i == 0 || i == 3 ? face.icon.getMinU() : face.icon.getMaxU();
                        double v = i < 2 ? face.icon.getMaxV() : face.icon.getMinV();
                        t.addVertexWithUV(p[0], p[1], p[2] * depthScale, u, v);
                    }
                }
            }
            t.draw();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            // Read face depth without writing line depth. The small z bias avoids coplanar
            // flicker while keeping back edges hidden; iconless cells remain clear wireframes.
            GL11.glDepthMask(false);
            t.startDrawing(GL11.GL_LINES);
            for (Face face : faces) {
                t.setColorRGBA_F(
                    face.cell.index == selected ? 1 : .2f,
                    face.cell.index == selected ? .92f : .7f,
                    face.cell.index == selected ? .5f : .8f,
                    face.cell.index == selected ? 1 : .45f);
                int[] indices = FACES[face.side];
                for (int i = 0; i < 4; i++) {
                    double[] a = face.points[indices[i]], b = face.points[indices[(i + 1) % 4]];
                    t.addVertex(a[0], a[1], a[2] * depthScale + .15);
                    t.addVertex(b[0], b[1], b[2] * depthScale + .15);
                }
            }
            t.draw();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1, 1, 1, 1);
            markers.sort(Comparator.comparingInt(marker -> marker.cell.anchor ? 1 : 0));
            for (RoleMarker marker : markers) {
                if (!marker.cell.anchor
                    && (marker.x < x || marker.x >= x + width || marker.y < y || marker.y >= y + height)) continue;
                float labelScale = marker.cell.anchor ? 1f : .7f;
                int labelWidth = Math.round(mc.fontRenderer.getStringWidth(marker.label) * labelScale);
                int lx = Math.max(x + 2, Math.min(x + width - labelWidth - 4, (int) marker.x - labelWidth / 2));
                int ly = Math.max(y + 2, Math.min(y + height - 12, (int) marker.y - 4));
                net.minecraft.client.gui.Gui.drawRect(
                    lx - 2,
                    ly - 1,
                    lx + labelWidth + 2,
                    ly + Math.round(9 * labelScale) + 1,
                    marker.cell.anchor ? 0xee514025 : 0xda102a34);
                GL11.glPushMatrix();
                try {
                    GL11.glTranslatef(lx, ly, 0);
                    GL11.glScalef(labelScale, labelScale, 1);
                    mc.fontRenderer.drawStringWithShadow(marker.label, 0, 0, marker.cell.anchor ? 0xffffbd : 0x9ceaf3);
                } finally {
                    GL11.glPopMatrix();
                }
                if (mouseX >= lx - 2 && mouseX < lx + labelWidth + 2
                    && mouseY >= ly - 1
                    && mouseY < ly + Math.round(9 * labelScale) + 1) hovered = marker.cell.index;
                lastPreviewRoleMarkers++;
                if (marker.cell.anchor) lastPreviewAnchorMarkers++;
            }
        } finally {
            GL11.glPopAttrib();
        }
        return hovered;
    }

    static String roleLabel(HologramState.Cell cell) {
        if (cell.anchor || "controller".equals(cell.role)) return "\u63a7\u5236\u5668";
        if (hatchStack(cell.actualStack) || hatchStack(cell.targetStack)) return "\u4ed3\u5ba4";
        if (cell.role.contains("interface")) return "\u63a5\u53e3";
        return "";
    }

    private static boolean hatchStack(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemMachines)) return false;
        int id = stack.getItemDamage();
        return id >= 0 && id < gregtech.api.GregTechAPI.METATILEENTITIES.length
            && gregtech.api.GregTechAPI.METATILEENTITIES[id] instanceof gregtech.api.metatileentity.implementations.MTEHatch;
    }

    private static final class RoleMarker {

        final HologramState.Cell cell;
        final String label;
        final double x, y;

        RoleMarker(HologramState.Cell cell, double[][] points, String label) {
            this.cell = cell;
            this.label = label;
            double sx = 0, sy = 0;
            for (double[] point : points) {
                sx += point[0];
                sy += point[1];
            }
            x = sx / points.length;
            y = sy / points.length;
        }
    }

    // Exposed within the client package for smoke assertions; no chunk renderer or world mutation is used.
    static boolean renderableBlock(HologramState.Cell cell) {
        Block block = cell.block(false);
        return block != null && block != Blocks.air
            && !cell.iconOnly
            && cell.actualStack == null
            && !(cell.targetStack != null && cell.targetStack.getItem() instanceof ItemMachines)
            && !block.hasTileEntity(cell.wantMeta)
            && block.getRenderType() >= 0;
    }

    static boolean worldMatched(HologramState.Cell cell) {
        net.minecraft.world.World world = Minecraft.getMinecraft().theWorld;
        Block block = cell.block(false);
        if (world == null || !world.blockExists(cell.x, cell.y, cell.z)
            || block == null
            || world.getBlock(cell.x, cell.y, cell.z) != block) return false;
        ItemStack target = cell.targetStack;
        if (target != null && target.getItem() instanceof ItemMachines) {
            net.minecraft.tileentity.TileEntity tile = world.getTileEntity(cell.x, cell.y, cell.z);
            return tile instanceof IGregTechTileEntity
                && ((IGregTechTileEntity) tile).getMetaTileID() == target.getItemDamage();
        }
        return world.getBlockMetadata(cell.x, cell.y, cell.z) == cell.wantMeta;
    }

    static ItemStack renderStack(HologramState.Cell cell, boolean current) {
        if (current || cell.anchor || (cell.status.equals("protected") && cell.actualStack != null))
            return cell.actualStack;
        if (cell.targetStack != null
            && (cell.targetStack.getItem() instanceof ItemMachines || "item".equals(cell.renderKind)))
            return cell.targetStack;
        return cell.actualStack;
    }

    private static int itemAtlas(HologramState.Cell cell, boolean current) {
        ItemStack stack = renderStack(cell, current);
        return stack == null ? 0
            : stack.getItem()
                .getSpriteNumber();
    }

    private static IIcon icon(HologramState.Cell cell, Block block, int meta, int side, boolean current) {
        try {
            ItemStack stack = renderStack(cell, current);
            if (stack != null) {
                IIcon item = stack.getIconIndex();
                return item == null ? missing(cell, current) : item;
            }
            if (!cell.hintIcons[side].isEmpty()) return Minecraft.getMinecraft()
                .getTextureMapBlocks()
                .getAtlasSprite(cell.hintIcons[side]);
            if (cell.iconOnly || block == null) return StructureLibAPI.getBlockHint()
                .getIcon(side, cell.hintIndex >= 0 ? cell.hintIndex : 0);
            return block.getIcon(side, meta);
        } catch (RuntimeException | LinkageError ignored) {
            return missing(cell, current);
        }
    }

    private static IIcon missing(HologramState.Cell cell, boolean current) {
        Minecraft mc = Minecraft.getMinecraft();
        if (itemAtlas(cell, current) == 1) {
            net.minecraft.client.renderer.texture.ITextureObject texture = mc.getTextureManager()
                .getTexture(TextureMap.locationItemsTexture);
            if (texture instanceof TextureMap) return ((TextureMap) texture).getAtlasSprite("missingno");
        }
        return mc.getTextureMapBlocks()
            .getAtlasSprite("missingno");
    }

    static void world(HologramState state, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.renderViewEntity;
        if (camera == null) return;
        double px = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        double py = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        double pz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
        lastWorldGeometry = lastWorldGTGeometry = lastWorldFallback = lastWorldMatched = lastWorldGhosts = 0;
        HologramBlockAccess access = new HologramBlockAccess(state);
        RenderBlocks renderer = new RenderBlocks(access);
        renderer.renderAllFaces = true;
        renderer.enableAO = false;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        Tessellator t = Tessellator.instance;
        try {
            GL11.glTranslated(-px, -py, -pz);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glAlphaFunc(GL11.GL_GREATER, .01f);
            for (HologramState.Cell c : state.cells) {
                if (camera.getDistanceSq(c.x, c.y, c.z) > 4096 || !mc.theWorld.blockExists(c.x, c.y, c.z)) continue;
                boolean matched = worldMatched(c);
                if (matched) lastWorldMatched++;
                boolean ghost = !matched && !c.anchor
                    && !(c.actualStack != null && c.status.equals("protected"))
                    && !c.status.equals("removed")
                    && !c.completed()
                    && !HologramClient.prepareExpired();
                double drop = ghost ? HologramClient.fall(c) : 0;
                int color = c.color();
                if (state.data.getInteger("mode") == 2 && c.status.equals("pending")) color = 0xf08b83;
                if (ghost) {
                    lastWorldGhosts++;
                    GL11.glPushMatrix();
                    try {
                        GL11.glTranslated(0, drop, 0);
                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                        mc.getTextureManager()
                            .bindTexture(
                                itemAtlas(c, false) == 1 ? TextureMap.locationItemsTexture
                                    : TextureMap.locationBlocksTexture);
                        int textureColor = c.textureColor();
                        GL11.glColor4f(
                            ((textureColor >> 16) & 255) / 255f,
                            ((textureColor >> 8) & 255) / 255f,
                            (textureColor & 255) / 255f,
                            .42f);
                        t.startDrawingQuads();
                        t.disableColor(); // RenderBlocks opaque color calls must not overwrite ghost alpha.
                        t.setBrightness(0xf000f0);
                        boolean rendered = false;
                        if (renderableBlock(c)) {
                            try {
                                rendered = renderBlock(renderer, c);
                            } catch (RuntimeException | LinkageError failure) {
                                logNativeFailure(c, failure);
                            }
                            if (!rendered) logNativeFailure(c, null);
                        }
                        if (rendered) {
                            lastWorldGeometry++;
                            if (c.block(false) instanceof gregtech.common.blocks.BlockCasingsAbstract
                                || c.block(false) instanceof gregtech.api.interfaces.IBlockWithTextures)
                                lastWorldGTGeometry++;
                        } else {
                            cube(t, c);
                            lastWorldFallback++;
                        }
                        t.draw();
                    } finally {
                        GL11.glPopMatrix();
                    }
                }
                // Existing controller and hatch models remain in the real world; these role markers stay visible.
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glLineWidth(c.anchor ? 2f : 1.25f);
                GL11.glColor4f(1, 1, 1, 1);
                t.startDrawing(GL11.GL_LINES);
                t.setColorRGBA_F(
                    ((color >> 16) & 255) / 255f,
                    ((color >> 8) & 255) / 255f,
                    (color & 255) / 255f,
                    c.anchor ? .85f : .48f);
                for (int[] edge : EDGES) for (int v : edge) t.addVertex(
                    c.x + .5 + VERTICES[v][0] * 1.006,
                    c.y + .5 + VERTICES[v][1] * 1.006 + drop,
                    c.z + .5 + VERTICES[v][2] * 1.006);
                Long start = HologramClient.arrivals.get(c.index);
                if (start != null) {
                    double age = (System.nanoTime() - start) / 1000000000.0;
                    if (age < 1.25) {
                        double radius = .3 + age * .65;
                        t.setColorRGBA_F(.45f, 1, .85f, (float) (1 - age / 1.25));
                        for (int i = 0; i < 12; i++) {
                            double angle = i * Math.PI / 6;
                            double rx = c.x + .5 + Math.cos(angle) * radius, rz = c.z + .5 + Math.sin(angle) * radius;
                            t.addVertex(rx, c.y + .1 + age * .35, rz);
                            t.addVertex(rx, c.y + .2 + age * .35, rz);
                        }
                    }
                }
                t.draw();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static boolean renderBlock(RenderBlocks renderer, HologramState.Cell cell) {
        Block block = cell.block(false);
        double minX = block.getBlockBoundsMinX(), minY = block.getBlockBoundsMinY(), minZ = block.getBlockBoundsMinZ();
        double maxX = block.getBlockBoundsMaxX(), maxY = block.getBlockBoundsMaxY(), maxZ = block.getBlockBoundsMaxZ();
        int originalPass = ForgeHooksClient.getWorldRenderPass();
        try {
            // GT's SBRWorldContext filters texture layers by the Forge world pass. RenderWorldLast
            // runs outside chunk passes (-1), so an unqualified call silently emits no vertices.
            boolean gregTechLayers = block instanceof gregtech.common.blocks.BlockCasingsAbstract
                || block instanceof gregtech.api.interfaces.IBlockWithTextures;
            setWorldPass(gregTechLayers ? 0 : block.getRenderBlockPass());
            boolean rendered = renderer.renderBlockByRenderType(block, cell.x, cell.y, cell.z);
            if (gregTechLayers) {
                setWorldPass(1);
                rendered |= renderer.renderBlockByRenderType(block, cell.x, cell.y, cell.z);
            }
            return rendered;
        } finally {
            setWorldPass(originalPass);
            block.setBlockBounds((float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ);
        }
    }

    private static java.lang.reflect.Method angelicaWorldPass() {
        try {
            return Class.forName("com.gtnewhorizons.angelica.rendering.celeritas.threading.RenderPassHelper")
                .getMethod("setWorldRenderPass", int.class);
        } catch (ReflectiveOperationException | LinkageError absent) {
            return null;
        }
    }

    private static java.lang.reflect.Field forgeWorldPass() {
        try {
            java.lang.reflect.Field field = ForgeHooksClient.class.getDeclaredField("worldRenderPass");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Forge world render pass is unavailable", failure);
        }
    }

    private static void setWorldPass(int pass) {
        try {
            // setRenderPass is Forge's entity pass and does not affect getWorldRenderPass.
            // Use Angelica's thread-aware setter when available; plain Forge has a separate field.
            if (ANGELICA_WORLD_PASS != null) ANGELICA_WORLD_PASS.invoke(null, pass);
            else FORGE_WORLD_PASS.setInt(null, pass);
            int actualPass = ForgeHooksClient.getWorldRenderPass();
            if (pass >= 0) lastNativeWorldPass = actualPass;
            if (actualPass != pass) throw new IllegalStateException("World render pass did not change to " + pass);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot scope hologram world render pass", failure);
        }
    }

    private static void logNativeFailure(HologramState.Cell cell, Throwable failure) {
        if (loggedNativeFailure || !Boolean.getBoolean("gtit.hologram.renderDiagnostics")) return;
        loggedNativeFailure = true;
        Block block = cell.block(false);
        String detail = "Hologram native renderer emitted no geometry: block=" + cell.wantId
            + ":"
            + cell.wantMeta
            + " renderType="
            + (block == null ? -1 : block.getRenderType())
            + " originalPass="
            + ForgeHooksClient.getWorldRenderPass()
            + " lastNativePass="
            + lastNativeWorldPass;
        if (failure == null) com.miaokatze.gtit.main.GTInterestingThing.LOG.warn(detail);
        else com.miaokatze.gtit.main.GTInterestingThing.LOG.warn(detail, failure);
    }

    private static void cube(Tessellator t, HologramState.Cell cell) {
        for (int side = 0; side < 6; side++) {
            IIcon icon = icon(cell, cell.block(false), cell.wantMeta, side, false);
            if (icon == null) continue;
            for (int i = 0; i < 4; i++) {
                double[] v = VERTICES[FACES[side][i]];
                t.addVertexWithUV(
                    cell.x + .5 + v[0],
                    cell.y + .5 + v[1],
                    cell.z + .5 + v[2],
                    i == 0 || i == 3 ? icon.getMinU() : icon.getMaxU(),
                    i < 2 ? icon.getMaxV() : icon.getMinV());
            }
        }
    }

    private static final class Face {

        final HologramState.Cell cell;
        final double[][] points;
        final int side, atlas;
        final IIcon icon;
        final double depth;

        Face(HologramState.Cell cell, double[][] points, int side, IIcon icon, int atlas) {
            this.cell = cell;
            this.points = points;
            this.side = side;
            this.icon = icon;
            this.atlas = atlas;
            double d = 0;
            for (int index : FACES[side]) d += points[index][2];
            depth = d / 4;
        }

        boolean contains(double x, double y) {
            boolean inside = false;
            int[] face = FACES[side];
            for (int i = 0, j = 3; i < 4; j = i++) {
                double[] a = points[face[i]], b = points[face[j]];
                if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) inside = !inside;
            }
            return inside;
        }
    }
}
