package com.miaokatze.gtit.client.vending.scene;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelOcelot;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;

/** Original voxel room geometry; cat geometry and texture come from Minecraft, never copied assets/entities. */
final class VendingRoomArt {

    private static final ResourceLocation CAT = new ResourceLocation("minecraft", "textures/entity/cat/black.png");
    private final ModelOcelot cat = new ModelOcelot();
    private final List<SceneBody> occluders = new ArrayList<>();

    void room(double leftDoor, double rightDoor, double rearDoor) {
        occluders.clear();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        roomBox(-5, -.18, -3, 10, .22, 6, 0xb99d77, 1);
        // Narrow warm timber planks, flush seams and an open front edge.
        for (int row = 0; row < 24; row++) {
            roomBox(-5, .045, -3 + row * .25, 10, .008, .235, row % 2 == 0 ? 0xe1c499 : 0xd8b582, 1);
            for (int seam = 0; seam < 3; seam++)
                roomBox(-4.2 + seam * 3.2 + row % 2 * .6, .054, -3 + row * .25, .015, .008, .235, 0xbda98a, 1);
        }
        roomBox(-5, 0, -3.15, 10, 4.35, .15, 0xf5e6c9, 1);
        // Voxel cutaway: high at the rear, tapering to a low sill at the open front.
        // Each visible step is also its exact ray-picking occluder, not a full-height proxy wall.
        for (int side : new int[] { -1, 1 }) {
            for (int i = 0; i < 24; i++) {
                double z = -3 + i * .25, height = 4.25 - 4.0 * i / 23.0;
                roomBox(side < 0 ? -5 : 4.8, 0, z, .2, height, .25, 0xf0dfbf, 1);
            }
            roomBox(side < 0 ? -5 : 4.8, .04, -3, .2, .16, 6, 0x8c7254, 1);
        }
        // Retracting individual shutter slats reveal the dark intake, before delivery proxies emerge.
        roomBox(-3.65, .15, -2.98, 7.3, 3.65, .055, 0x534b50, 1);
        for (int i = 0; i < 19; i++) {
            double base = .24 + i * .18, y = base + (3.63 - base) * rearDoor;
            roomBox(-3.5, y, -2.86, 7, .168 * (1 - .86 * rearDoor), .045, i % 2 == 0 ? 0xcfc3ac : 0xc1b397, 1);
        }
        roomBox(-3.8, 3.78, -2.92, 7.6, .38, .38, 0xf5e6c9, 1);
        roomBox(-3.72, .12, -2.85, .12, 3.65, .12, 0xa48f70, 1);
        roomBox(3.6, .12, -2.85, .12, 3.65, .12, 0xa48f70, 1);
        roomBox(-.5, .28 + rearDoor * 3.2, -2.78, 1, .07, .04, 0x675346, 1);
        outlet(-1, leftDoor);
        outlet(1, rightDoor);
        roomBox(-4.6, .06, 1.0, 2, .012, 1.95, 0x8da69a, 1);
        roomBox(-4.48, .073, 1.12, 1.76, .009, 1.7, 0xb9c9b3, 1);
        roomBox(-4.36, .083, 1.24, 1.52, .009, 1.46, 0x8da69a, 1);
        for (int side : new int[] { -1, 1 }) {
            double x = side * 4.25;
            roomBox(x - .32, 1.7, -2.78, .64, .12, .55, 0xd8b582, 1);
            roomBox(x - .18, 1.82, -2.7, .36, .38, .34, 0xbf805c, 1);
            roomBox(x - .04, 2.2, -2.57, .08, .55, .08, 0x5e7958, 1);
            roomBox(x - .24, 2.43, -2.66, .4, .2, .26, 0x7d9680, 1);
            roomBox(x, 2.6, -2.62, .24, .26, .24, 0xaabd8e, 1);
            roomBox(x - .15, 3.36, -2.8, .3, .4, .22, 0x80614b, 1);
            roomBox(x - .11, 3.42, -2.55, .22, .26, .06, 0xffedb5, 1);
        }
        roomBox(-4.6, 4.15, -2.9, 9.2, .055, .12, 0xffedb5, 1);
        roomBox(2.45, 3.86, -2.51, .42, .26, .04, 0x9c28e2, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void outlet(int side, double openness) {
        double outer = side < 0 ? -5.05 : 3.76;
        // Five solid shell panels; only the inward mouth opens. Feed center: ±4.6, 3.0, .5.
        roomBox(outer, 2.46, -.22, 1.29, .12, 1.44, 0xa48f70, 1);
        roomBox(outer, 3.48, -.22, 1.29, .14, 1.44, 0xf5e6c9, 1);
        roomBox(outer, 2.58, -.22, 1.29, .9, .12, 0xf5e6c9, 1);
        roomBox(outer, 2.58, 1.1, 1.29, .9, .12, 0xf5e6c9, 1);
        roomBox(side < 0 ? -5.05 : 4.93, 2.58, -.22, .12, .9, 1.44, 0xf5e6c9, 1);
        double hingeX = side * 3.73, hingeZ = 1.09, angle = side * openness * 2.45;
        hingedBox(hingeX, 3.03, hingeZ, angle, -.055, -.46, -1.3, .11, .92, 1.3, 0xbda98a);
        hingedBox(hingeX, 3.03, hingeZ, angle, -.065, -.4, -1.23, .13, .8, 1.16, 0xf5e6c9);
        // Paw medallions on both faces, moving with the actual hinged panel.
        for (int face : new int[] { -1, 1 }) {
            double px = face < 0 ? -.075 : .065;
            hingedBox(hingeX, 3.03, hingeZ, angle, px, -.24, -.83, .01, .28, .34, 0xb78341);
            for (int toe = 0; toe < 3; toe++)
                hingedBox(hingeX, 3.03, hingeZ, angle, px, .11, -.99 + toe * .24, .01, .14, .14, 0xb78341);
        }
        for (double z : new double[] { -.04, .94 }) roomBox(side < 0 ? -4.65 : 4.25, 3.62, z, .3, .22, .2, 0xbda98a, 1);
    }

    private void hingedBox(double px, double py, double pz, double angle, double x, double y, double z, double w,
        double h, double d, int rgb) {
        double cx = x + w / 2, cz = z + d / 2;
        occluders.add(
            new SceneBody(
                "room" + occluders.size(),
                w / 2,
                h / 2,
                d / 2,
                px + Math.cos(angle) * cx + Math.sin(angle) * cz,
                py + y + h / 2,
                pz - Math.sin(angle) * cx + Math.cos(angle) * cz)
                    .withRotation(0, Math.sin(angle / 2), 0, Math.cos(angle / 2)));
        GL11.glPushMatrix();
        GL11.glTranslated(px, py, pz);
        GL11.glRotated(Math.toDegrees(angle), 0, 1, 0);
        box(x, y, z, w, h, d, rgb, 1);
        GL11.glPopMatrix();
    }

    List<SceneBody> pickOccluders() {
        return occluders;
    }

    private void roomBox(double x, double y, double z, double w, double h, double d, int rgb, float alpha) {
        // The very same solid geometry supplies rendering and mouse occlusion; no approximate wall proxy.
        occluders.add(new SceneBody("room" + occluders.size(), w / 2, h / 2, d / 2, x + w / 2, y + h / 2, z + d / 2));
        box(x, y, z, w, h, d, rgb, alpha);
    }

    // Vanilla constructor order: head, body, tail1, tail2, hind L/R, front L/R.
    private static final double[][] STAND = { { 0, 15, -9, 0 }, { 0, 12, -10, Math.PI / 2 }, { 0, 15, 8, .9 },
        { 0, 20, 14, 1.7278761 }, { 1.1, 18, 5, 0 }, { -1.1, 18, 5, 0 }, { 1.2, 13.8, -5, 0 }, { -1.2, 13.8, -5, 0 } };
    private static final double[][] SIT = { { 0, 11.7, -8, 0 }, { 0, 8, -5, Math.PI / 4 }, { 0, 23, 6, 1.7278761 },
        { 0, 22, 13.2, 2.670354 }, { 1.1, 21, 1, -Math.PI / 2 }, { -1.1, 21, 1, -Math.PI / 2 },
        { 1.2, 15.8, -7, -.15707964 }, { -1.2, 15.8, -7, -.15707964 } };

    void cat(VendingCatChoreography pose) {
        GL11.glPushMatrix();
        try {
            // Seated vanilla pivots extend 1.68 pixels below ground; lift the whole model, not its paws.
            double baseY = 1.59 + pose.sitting * 1.68 / 16;
            GL11.glTranslated(pose.x, baseY, pose.z);
            GL11.glRotated(Math.toDegrees(pose.yaw), 0, 1, 0);
            GL11.glScaled(1, -1, -1);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(CAT);
            GL11.glColor4f(1, 1, 1, 1);
            cat.isChild = false;
            for (int i = 0; i < 8; i++) {
                ModelRenderer part = cat.boxList.get(i);
                double[] a = STAND[i], b = SIT[i];
                part.setRotationPoint(
                    (float) mix(a[0], b[0], pose.sitting),
                    (float) mix(a[1], b[1], pose.sitting),
                    (float) mix(a[2], b[2], pose.sitting));
                part.rotateAngleX = (float) mix(a[3], b[3], pose.sitting);
                part.rotateAngleY = part.rotateAngleZ = 0;
                if (i == 0) {
                    part.rotateAngleY = (float) -pose.headYaw;
                    part.rotateAngleX = (float) pose.headPitch;
                }
                if (i == 6) part.rotateAngleX -= pose.stride + pose.paw;
                if (i == 5) part.rotateAngleX -= pose.stride;
                if (i == 7 || i == 4) part.rotateAngleX += pose.stride;
                catOccluders(part, pose, baseY);
                drawPart(part, .0625f);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private void catOccluders(ModelRenderer part, VendingCatChoreography pose, double baseY) {
        double[] root = multiply(
            new double[] { 0, Math.sin(pose.yaw / 2), 0, Math.cos(pose.yaw / 2) },
            new double[] { 1, 0, 0, 0 });
        double[] rotation = multiply(
            root,
            multiply(
                new double[] { 0, Math.sin(part.rotateAngleY / 2), 0, Math.cos(part.rotateAngleY / 2) },
                new double[] { Math.sin(part.rotateAngleX / 2), 0, 0, Math.cos(part.rotateAngleX / 2) }));
        double[] pivot = rotate(
            root,
            part.rotationPointX / 16.0,
            part.rotationPointY / 16.0,
            part.rotationPointZ / 16.0);
        for (ModelBox box : part.cubeList) {
            double[] center = rotate(
                rotation,
                (box.posX1 + box.posX2) / 32.0,
                (box.posY1 + box.posY2) / 32.0,
                (box.posZ1 + box.posZ2) / 32.0);
            occluders.add(
                new SceneBody(
                    "cat" + occluders.size(),
                    (box.posX2 - box.posX1) / 32.0,
                    (box.posY2 - box.posY1) / 32.0,
                    (box.posZ2 - box.posZ1) / 32.0,
                    pose.x + pivot[0] + center[0],
                    baseY + pivot[1] + center[1],
                    pose.z + pivot[2] + center[2]).withRotation(rotation[0], rotation[1], rotation[2], rotation[3]));
        }
    }

    private static double[] multiply(double[] a, double[] b) {
        return new double[] { a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2] };
    }

    private static double[] rotate(double[] q, double x, double y, double z) {
        double[] v = multiply(multiply(q, new double[] { x, y, z, 0 }), new double[] { -q[0], -q[1], -q[2], q[3] });
        return new double[] { v[0], v[1], v[2] };
    }

    /** Render native ModelBox geometry directly: no ModelRenderer display-list allocations survive GUI close. */
    private static void drawPart(ModelRenderer p, float scale) {
        if (p.isHidden || !p.showModel) return;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(
                p.offsetX + p.rotationPointX * scale,
                p.offsetY + p.rotationPointY * scale,
                p.offsetZ + p.rotationPointZ * scale);
            GL11.glRotatef(p.rotateAngleZ * 57.29578f, 0, 0, 1);
            GL11.glRotatef(p.rotateAngleY * 57.29578f, 0, 1, 0);
            GL11.glRotatef(p.rotateAngleX * 57.29578f, 1, 0, 0);
            for (ModelBox b : p.cubeList) b.render(Tessellator.instance, scale);
            if (p.childModels != null) for (ModelRenderer child : p.childModels) drawPart(child, scale);
        } finally {
            GL11.glPopMatrix();
        }
    }

    static void box(double x, double y, double z, double w, double h, double d, int rgb, float alpha) {
        Tessellator t = Tessellator.instance;
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        t.startDrawingQuads();
        t.setColorRGBA_F(r, g, b, alpha);
        quad(t, x, y + h, z, x + w, y + h, z, x + w, y + h, z + d, x, y + h, z + d);
        t.setColorRGBA_F(r * .8f, g * .8f, b * .8f, alpha);
        quad(t, x, y, z + d, x + w, y, z + d, x + w, y + h, z + d, x, y + h, z + d);
        quad(t, x, y, z, x + w, y, z, x + w, y + h, z, x, y + h, z);
        t.setColorRGBA_F(r * .68f, g * .68f, b * .68f, alpha);
        quad(t, x, y, z, x, y, z + d, x, y + h, z + d, x, y + h, z);
        quad(t, x + w, y, z, x + w, y, z + d, x + w, y + h, z + d, x + w, y + h, z);
        quad(t, x, y, z, x + w, y, z, x + w, y, z + d, x, y, z + d);
        t.draw();
    }

    private static void quad(Tessellator t, double... v) {
        for (int i = 0; i < 12; i += 3) t.addVertex(v[i], v[i + 1], v[i + 2]);
    }
}
