package com.miaokatze.gtit.client.hologram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.IIcon;

import org.lwjgl.opengl.GL11;

/** Textured, depth-sorted block geometry using the actual block atlas, without a fake world. */
final class HologramRenderer {

    private static final int[][] FACES = { { 0, 1, 5, 4 }, { 3, 7, 6, 2 }, { 0, 3, 2, 1 }, { 4, 5, 6, 7 },
        { 0, 4, 7, 3 }, { 1, 2, 6, 5 } };
    private static final int[][] EDGES = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, { 4, 5 }, { 5, 6 }, { 6, 7 },
        { 7, 4 }, { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } };
    private static final double[][] VERTICES = { { -.5, -.5, -.5 }, { .5, -.5, -.5 }, { .5, .5, -.5 }, { -.5, .5, -.5 },
        { -.5, -.5, .5 }, { .5, -.5, .5 }, { .5, .5, .5 }, { -.5, .5, .5 } };

    private HologramRenderer() {}

    static int preview(HologramState state, int x, int y, int width, int height, double yaw, double pitch, double zoom,
        int layer, int view, int selected, int mouseX, int mouseY, double rehearsal) {
        List<Face> faces = new ArrayList<>();
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
            if (c.dy > layer || (view == 2 && c.status.equals("satisfied"))) continue;
            double drop = HologramClient.fall(c);
            if (rehearsal >= 0) {
                double start = 2.5 + (c.dy - state.minY) * 1.75 + ((c.index * 17 % 7) * .04);
                if (rehearsal >= 2 && rehearsal < start) continue;
                if (rehearsal >= start)
                    drop = 3 * (1 - Math.sin(Math.min(1, (rehearsal - start) / 1.25) * Math.PI / 2));
            }
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
            for (int side = 0; side < 6; side++) {
                IIcon icon = null;
                if (block != null) {
                    try {
                        icon = block.getIcon(side, meta);
                    } catch (RuntimeException ignored) { /* Unknown custom icon. */ }
                }
                faces.add(new Face(c, projected, side, icon));
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
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x * factor, mc.displayHeight - (y + height) * factor, width * factor, height * factor);
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
            for (Face face : faces) {
                boolean inside = face.contains(mouseX, mouseY) && mouseX >= x
                    && mouseX < x + width
                    && mouseY >= y
                    && mouseY < y + height;
                if (inside) hovered = face.cell.index;
                int color = view == 1 ? 0xffffff : face.cell.color();
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
        } finally {
            GL11.glPopAttrib();
        }
        return hovered;
    }

    static void world(HologramState state, float partialTicks) {
        Entity camera = Minecraft.getMinecraft().renderViewEntity;
        if (camera == null) return;
        double px = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        double py = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        double pz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-px, -py, -pz);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1.5f);
            Tessellator t = Tessellator.instance;
            t.startDrawing(GL11.GL_LINES);
            for (HologramState.Cell c : state.cells) {
                if (c.status.equals("satisfied") || c.status.equals("removed")) continue;
                int color = c.color();
                if (state.data.getInteger("mode") == 2 && c.status.equals("pending")) color = 0xf08b83;
                t.setColorRGBA_F(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, .62f);
                double fall = HologramClient.fall(c);
                for (int[] edge : EDGES) {
                    for (int v : edge) t.addVertex(
                        c.x + .5 + VERTICES[v][0],
                        c.y + .5 + VERTICES[v][1] + fall,
                        c.z + .5 + VERTICES[v][2]);
                }
                Long start = HologramClient.arrivals.get(c.index);
                if (start != null) {
                    double age = (System.nanoTime() - start) / 1000000000.0;
                    if (age < 1.25) {
                        double radius = .3 + age * .65;
                        t.setColorRGBA_F(.45f, 1, .85f, (float) (1 - age / 1.25));
                        for (int i = 0; i < 12; i++) {
                            double a = i * Math.PI / 6;
                            double rx = c.x + .5 + Math.cos(a) * radius, rz = c.z + .5 + Math.sin(a) * radius;
                            t.addVertex(rx, c.y + .1 + age * .35, rz);
                            t.addVertex(rx, c.y + .2 + age * .35, rz);
                        }
                    }
                }
            }
            t.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static final class Face {

        final HologramState.Cell cell;
        final double[][] points;
        final int side;
        final IIcon icon;
        final double depth;

        Face(HologramState.Cell cell, double[][] points, int side, IIcon icon) {
            this.cell = cell;
            this.points = points;
            this.side = side;
            this.icon = icon;
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
