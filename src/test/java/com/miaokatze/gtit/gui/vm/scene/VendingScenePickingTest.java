package com.miaokatze.gtit.gui.vm.scene;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBounds;

/** Pure geometry regressions: independent of OpenGL, Minecraft registries and insertion order. */
public final class VendingScenePickingTest {

    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }

    private static String pick(List<SceneBody> bodies, double x, double y, double yaw, double pitch) {
        return VendingScenePicking.pick(
            bodies,
            Collections.emptyList(),
            VendingSceneFrames.BOUNDS,
            200,
            200,
            x,
            y,
            yaw,
            pitch,
            1,
            id -> true);
    }

    public static void main(String[] args) {
        check(VendingSceneViewDefaults.YAW == -20, "initial/reset view has the requested twenty-degree left yaw");
        SceneBounds room = new SceneBounds(-5.25, -.25, -3.85, 5.25, 4.55, 3.2);
        SceneBody displayBlock = new SceneBody("large-block", .39, .39, .39, 1, 1, 1);
        double radius = Math.sqrt(10.5 * 10.5 + 4.8 * 4.8 + 7.05 * 7.05) * .55;
        double halfH = radius / VendingSceneViewDefaults.ZOOM, halfW = halfH * 400 / 300;
        for (double yawDegrees : new double[] { VendingSceneViewDefaults.YAW, -37, 32 }) {
            double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(VendingSceneViewDefaults.PITCH);
            // Project a known native block center with Core's Rx * Ry * T camera, then invert with picking.
            double x = displayBlock.x, y = displayBlock.y - 2.15, z = displayBlock.z + .325;
            double eyeX = Math.cos(yaw) * x + Math.sin(yaw) * z;
            double eyeY = Math.cos(pitch) * y - Math.sin(pitch) * (-Math.sin(yaw) * x + Math.cos(yaw) * z);
            check(
                "large-block".equals(
                    VendingScenePicking.pick(
                        Collections.singletonList(displayBlock),
                        Collections.emptyList(),
                        room,
                        400,
                        300,
                        (eyeX / halfW + 1) * 200,
                        (1 - eyeY / halfH) * 150,
                        yawDegrees,
                        VendingSceneViewDefaults.PITCH,
                        VendingSceneViewDefaults.ZOOM,
                        id -> true)),
                "default/reset and user-rotated cameras pick their rendered native block center");
        }
        SceneBody rear = new SceneBody("rear", .22, .22, .22, 0, 4, 0);
        SceneBody front = new SceneBody(
            "front",
            .22,
            .22,
            .22,
            .7736590696265117,
            4.618033988749895,
            1.7376667208456675);
        check("front".equals(pick(Arrays.asList(rear, front), 100, 100, -24, 18)), "nearest body wins rear-first");
        check("front".equals(pick(Arrays.asList(front, rear), 100, 100, -24, 18)), "nearest body wins front-first");
        check(
            "rear".equals(
                VendingScenePicking.pick(
                    Arrays.asList(front, rear),
                    Collections.emptyList(),
                    VendingSceneFrames.BOUNDS,
                    200,
                    200,
                    100,
                    100,
                    -24,
                    18,
                    1,
                    id -> !"front".equals(id))),
            "invisible/unresolved body cannot intercept picking");
        SceneBody thin = new SceneBody("thin", .05, 1, .05, 0, 4, 0)
            .withRotation(0, 0, Math.sin(Math.PI / 8), Math.cos(Math.PI / 8));
        double pixels = 100 / (Math.sqrt(200) * .55);
        check(
            "thin".equals(pick(Collections.singletonList(thin), 100 - .6 * pixels, 100 - .6 * pixels, 0, 0)),
            "rotated slender box corner is selectable");
        check(
            pick(Collections.singletonList(thin), 100 + .6 * pixels, 100 - .6 * pixels, 0, 0) == null,
            "ray inside enclosing circle but outside oriented box misses");
        check(pick(Collections.singletonList(rear), -1, 100, 0, 0) == null, "negative viewport coordinate misses");
        check(pick(Collections.singletonList(rear), 200, 100, 0, 0) == null, "outside right viewport edge misses");
        check(pick(Collections.singletonList(rear), 100, 200, 0, 0) == null, "outside lower viewport edge misses");
        check(pick(Collections.singletonList(rear), Double.NaN, 100, 0, 0) == null, "nonfinite mouse misses");
        check(pick(Collections.singletonList(rear), 10, 10, 0, 0) == null, "empty corner misses");
        SceneBody wall = new SceneBody("wall", 4, 4, .1, 0, 4, 2);
        check(
            VendingScenePicking.pick(
                Collections.singletonList(rear),
                Collections.singletonList(wall),
                VendingSceneFrames.BOUNDS,
                200,
                200,
                100,
                100,
                0,
                0,
                1,
                id -> true) == null,
            "solid room art occludes hidden stock");
        SceneBody behind = new SceneBody("behind", .2, .2, .2, 0, 4, 1026);
        check(pick(Collections.singletonList(behind), 100, 100, 0, 0) == null, "negative ray hit is rejected");
        System.out.println(
            "VendingScenePickingTest: nearest oriented hit, camera inverse, order independence, visibility, occlusion and misses passed");
    }

    private VendingScenePickingTest() {}
}
