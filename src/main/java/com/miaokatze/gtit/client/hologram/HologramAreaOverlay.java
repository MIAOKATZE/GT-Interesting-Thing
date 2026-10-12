package com.miaokatze.gtit.client.hologram;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;

import org.lwjgl.opengl.GL11;

/** Read-only bounded world overlay; the inclusive coordinates always come from the server mirror. */
final class HologramAreaOverlay {

    private HologramAreaOverlay() {}

    static void render(HologramState state, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.renderViewEntity;
        int[] points = state.data.getIntArray("areaPoints");
        if (camera == null || points.length < 3) return;
        int bx = points[0], by = points[1], bz = points[2];
        if (points.length >= 6) {
            bx = points[3];
            by = points[4];
            bz = points[5];
        } else {
            MovingObjectPosition hit = mc.objectMouseOver;
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                bx = hit.blockX;
                by = hit.blockY;
                bz = hit.blockZ;
            }
        }
        double px = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        double py = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        double pz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(
                Math.min(points[0], bx),
                Math.min(points[1], by),
                Math.min(points[2], bz),
                Math.max(points[0], bx) + 1,
                Math.max(points[1], by) + 1,
                Math.max(points[2], bz) + 1)
            .expand(.002, .002, .002)
            .getOffsetBoundingBox(-px, -py, -pz);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2);
            GL11.glColor4f(.35f, .85f, 1, .85f);
            RenderGlobal.drawOutlinedBoundingBox(box, -1);
        } finally {
            GL11.glPopAttrib();
        }
    }
}
