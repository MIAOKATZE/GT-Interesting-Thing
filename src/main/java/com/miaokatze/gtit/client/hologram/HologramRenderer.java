package com.miaokatze.gtit.client.hologram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.blocks.ItemMachines;

/** Cached schematic cubes; rendering never calls machine models or reads live world cells. */
final class HologramRenderer {

    private static final int[][] FACES = { { 0, 1, 5, 4 }, { 3, 7, 6, 2 }, { 0, 3, 2, 1 }, { 4, 5, 6, 7 },
        { 0, 4, 7, 3 }, { 1, 2, 6, 5 } };
    private static final int[][] NORMALS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };
    private static final int[][] EDGES = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, { 4, 5 }, { 5, 6 }, { 6, 7 },
        { 7, 4 }, { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } };
    private static final double[][] VERTICES = { { -.5, -.5, -.5 }, { .5, -.5, -.5 }, { .5, .5, -.5 }, { -.5, .5, -.5 },
        { -.5, -.5, .5 }, { .5, -.5, .5 }, { .5, .5, .5 }, { -.5, .5, .5 } };

    private HologramRenderer() {}

    static int lastWorldGeometry, lastWorldGTGeometry, lastWorldFallback, lastWorldMatched, lastWorldGhosts;
    static int lastPreviewRoleMarkers, lastPreviewAnchorMarkers;
    private static HologramState previewState, worldState;
    private static String previewKey = "";
    private static List<Face> cachedFaces = new ArrayList<>();
    private static List<Face> cachedOutlines = new ArrayList<>();
    private static HologramState visibleState;
    private static int visibleLayer, visibleView, visibleMode;
    private static final List<HologramState.Cell> visibleCells = new ArrayList<>();
    private static final Set<Long> occupied = new HashSet<>();
    private static List<RoleMarker> cachedMarkers = new ArrayList<>();
    private static double cachedExtent = 1;
    private static int worldList, worldLayer = Integer.MAX_VALUE;
    static int projectionLayer = Integer.MAX_VALUE;

    static void release() {
        if (worldList != 0) GL11.glDeleteLists(worldList, 1);
        worldList = 0;
        worldState = previewState = null;
        cachedFaces.clear();
        cachedMarkers.clear();
        cachedOutlines.clear();
        visibleState = null;
        visibleCells.clear();
        occupied.clear();
    }

    static int preview(HologramState state, int x, int y, int width, int height, double yaw, double pitch, double zoom,
        int layer, int view, int selected, int mouseX, int mouseY, double rehearsal) {
        String key = x + ":"
            + y
            + ":"
            + width
            + ":"
            + height
            + ":"
            + yaw
            + ":"
            + pitch
            + ":"
            + zoom
            + ":"
            + layer
            + ":"
            + view;
        if (previewState != state || !key.equals(previewKey)) {
            List<Face> faces = new ArrayList<>();
            List<RoleMarker> markers = new ArrayList<>();
            List<Face> outlines = new ArrayList<>();
            double sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw);
            double sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch);
            int mode = state.data.getInteger("mode");
            if (visibleState != state || visibleLayer != layer || visibleView != view || visibleMode != mode) {
                visibleState = state;
                visibleLayer = layer;
                visibleView = view;
                visibleMode = mode;
                visibleCells.clear();
                occupied.clear();
                boolean currentView = view == 1 || mode == 2;
                for (HologramState.Cell c : state.cells) {
                    if (c.status.equals("removed") || (view == 2 && (c.completed() || c.status.equals("satisfied"))))
                        continue;
                    if (!c.anchor && (currentView ? c.id : c.wantId).equals("minecraft:air")) continue;
                    if (!c.anchor && c.dy != layer && layer != Integer.MAX_VALUE) continue;
                    visibleCells.add(c);
                    occupied.add(coordinate(c.x, c.y, c.z));
                }
            }
            boolean[] facing = new boolean[6];
            for (int side = 0; side < 6; side++) {
                int[] normal = NORMALS[side];
                facing[side] = normal[1] * sinPitch + (normal[0] * sinYaw + normal[2] * cosYaw) * cosPitch > 1e-8;
            }
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
            for (HologramState.Cell c : visibleCells) {
                int exposed = 0;
                for (int side = 0; side < 6; side++) {
                    if (!facing[side]) continue;
                    int[] normal = NORMALS[side];
                    if (!occupied.contains(coordinate(c.x + normal[0], c.y + normal[1], c.z + normal[2])))
                        exposed |= 1 << side;
                }
                String role = roleLabel(c);
                if (exposed == 0 && role.isEmpty()) continue;
                double[][] projected = new double[8][];
                for (int v = 0; v < 8; v++) {
                    double px = c.dx - cx + VERTICES[v][0], pz = c.dz - cz + VERTICES[v][2];
                    double py = c.dy - cy + VERTICES[v][1];
                    double rx = px * cosYaw - pz * sinYaw;
                    double rz = px * sinYaw + pz * cosYaw;
                    double ry = py * cosPitch - rz * sinPitch;
                    double depth = py * sinPitch + rz * cosPitch;
                    projected[v] = new double[] { x + width / 2.0 + rx * scale, y + height / 2.0 - ry * scale, depth };
                }
                if (!role.isEmpty() && (c.anchor || markers.size() < 64))
                    markers.add(new RoleMarker(c, projected, role));
                outlines.add(new Face(c, projected, 0));
                for (int side = 0; side < 6; side++) {
                    if ((exposed & (1 << side)) != 0) faces.add(new Face(c, projected, side));
                }
            }
            faces.sort(Comparator.comparingDouble(f -> f.depth));
            markers.sort(Comparator.comparingInt(marker -> marker.cell.anchor ? 1 : 0));
            previewState = state;
            previewKey = key;
            cachedFaces = faces;
            cachedMarkers = markers;
            cachedOutlines = outlines;
            cachedExtent = extent;
        }
        List<Face> faces = cachedFaces;
        List<RoleMarker> markers = cachedMarkers;
        double extent = cachedExtent;
        lastPreviewRoleMarkers = lastPreviewAnchorMarkers = 0;
        int hovered = -1;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            // GUI ortho spans +/-1000 around z=0. Normalize model depth independently of zoom,
            // and clear only this viewport so schematic faces and outlines share reliable occlusion.
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
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            double depthScale = 128 / extent;
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            for (Face face : faces) {
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
                for (int i = 0; i < 4; i++) {
                    double[] p = face.points[FACES[face.side][i]];
                    t.addVertex(p[0], p[1], p[2] * depthScale);
                }
            }
            t.draw();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            // Read face depth without writing line depth. The small z bias avoids coplanar
            // flicker while keeping back edges hidden; all cells retain clear wireframes.
            GL11.glDepthMask(false);
            t.startDrawing(GL11.GL_LINES);
            for (Face face : cachedOutlines) {
                t.setColorRGBA_F(
                    face.cell.index == selected ? 1 : .2f,
                    face.cell.index == selected ? .92f : .7f,
                    face.cell.index == selected ? .5f : .8f,
                    face.cell.index == selected ? 1 : .45f);
                for (int[] edge : EDGES) {
                    double[] a = face.points[edge[0]], b = face.points[edge[1]];
                    t.addVertex(a[0], a[1], a[2] * depthScale + .15);
                    t.addVertex(b[0], b[1], b[2] * depthScale + .15);
                }
            }
            t.draw();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1, 1, 1, 1);
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
                marker.left = lx - 2;
                marker.top = ly - 1;
                marker.right = lx + labelWidth + 2;
                marker.bottom = ly + Math.round(9 * labelScale) + 1;
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

    /** Minecraft world bounds fit signed 26-bit X/Z and 12-bit Y without aliasing. */
    private static long coordinate(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | (y & 0xfffL);
    }

    static int pick(int x, int y) {
        int result = -1;
        for (Face face : cachedFaces) if (face.contains(x, y)) result = face.cell.index;
        for (RoleMarker marker : cachedMarkers)
            if (x >= marker.left && x < marker.right && y >= marker.top && y < marker.bottom)
                result = marker.cell.index;
        return result;
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
        int left, top, right, bottom;

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

    static void world(HologramState state, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.renderViewEntity;
        if (camera == null) return;
        double px = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        double py = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        double pz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
        if (worldState != state || worldLayer != projectionLayer) {
            if (worldList != 0) GL11.glDeleteLists(worldList, 1);
            worldList = GL11.glGenLists(1);
            if (worldList == 0) return;
            worldState = state;
            worldLayer = projectionLayer;
            lastWorldGeometry = lastWorldGTGeometry = lastWorldFallback = lastWorldMatched = lastWorldGhosts = 0;
            GL11.glNewList(worldList, GL11.GL_COMPILE);
            Tessellator t = Tessellator.instance;
            t.startDrawing(GL11.GL_LINES);
            for (HologramState.Cell c : state.cells) {
                if (c.status.equals("removed") || c.completed()) continue;
                if (!c.anchor && (worldLayer != Integer.MAX_VALUE && c.dy != worldLayer)) continue;
                if (!c.anchor && c.id.equals("minecraft:air")
                    && c.wantId.equals("minecraft:air")
                    && !c.role.contains("interface")) continue;
                // Hard radius around the controller keeps large sparse snapshots bounded.
                if (c.dx * (double) c.dx + c.dy * (double) c.dy + c.dz * (double) c.dz > 4096) continue;
                int color = c.anchor ? 0xffffbd : c.color();
                if (state.data.getInteger("mode") == 2 && c.status.equals("pending")) color = 0xf08b83;
                t.setColorRGBA_F(
                    ((color >> 16) & 255) / 255f,
                    ((color >> 8) & 255) / 255f,
                    (color & 255) / 255f,
                    c.anchor ? .95f : .65f);
                for (int[] edge : EDGES) for (int v : edge) t.addVertex(
                    c.x + .5 + VERTICES[v][0] * 1.006,
                    c.y + .5 + VERTICES[v][1] * 1.006,
                    c.z + .5 + VERTICES[v][2] * 1.006);
                lastWorldGeometry++;
                if (c.status.equals("satisfied")) lastWorldMatched++;
                else if (!c.anchor) lastWorldGhosts++;
            }
            t.draw();
            GL11.glEndList();
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-px, -py, -pz);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1.25f);
            GL11.glCallList(worldList);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static final class Face {

        final HologramState.Cell cell;
        final double[][] points;
        final int side;
        final double depth;

        Face(HologramState.Cell cell, double[][] points, int side) {
            this.cell = cell;
            this.points = points;
            this.side = side;
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
