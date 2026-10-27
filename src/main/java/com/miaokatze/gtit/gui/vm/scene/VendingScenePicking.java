package com.miaokatze.gtit.gui.vm.scene;

import java.util.List;
import java.util.function.Predicate;

import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBounds;

/** Exact inverse of Core's orthographic camera, followed by nearest ray/oriented-box intersection. */
public final class VendingScenePicking {

    private static final double CLIP = 1024;
    private static final double EPSILON = 1e-9;

    public static String pick(List<SceneBody> bodies, List<SceneBody> occluders, SceneBounds bounds, double width,
        double height, double mouseX, double mouseY, double yawDegrees, double pitchDegrees, double zoom,
        Predicate<String> visible) {
        if (bodies == null || bounds == null
            || visible == null
            || !Double.isFinite(width)
            || !Double.isFinite(height)
            || !Double.isFinite(mouseX)
            || !Double.isFinite(mouseY)
            || !Double.isFinite(yawDegrees)
            || !Double.isFinite(pitchDegrees)
            || !Double.isFinite(zoom)
            || width <= 0
            || height <= 0
            || zoom <= 0
            || mouseX < 0
            || mouseY < 0
            || mouseX >= width
            || mouseY >= height) return null;
        double sizeX = bounds.maxX - bounds.minX, sizeY = bounds.maxY - bounds.minY;
        double sizeZ = bounds.maxZ - bounds.minZ;
        double radius = Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ) * .55;
        double aspect = width / height, halfH = radius / zoom / Math.min(1, aspect), halfW = halfH * aspect;
        double screenX = (mouseX / width * 2 - 1) * halfW;
        double screenY = (1 - mouseY / height * 2) * halfH;
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        // GL modelview is Rx(pitch) * Ry(yaw) * T(-center), so invert in reverse order.
        double rotatedY = cp * screenY + sp * CLIP, rotatedZ = -sp * screenY + cp * CLIP;
        double ox = cy * screenX - sy * rotatedZ + (bounds.minX + bounds.maxX) / 2;
        double oy = rotatedY + (bounds.minY + bounds.maxY) / 2;
        double oz = sy * screenX + cy * rotatedZ + (bounds.minZ + bounds.maxZ) / 2;
        double dx = sy * cp, dy = -sp, dz = -cy * cp;
        double wall = CLIP * 2;
        if (occluders != null)
            for (SceneBody body : occluders) wall = Math.min(wall, hit(body, ox, oy, oz, dx, dy, dz));
        String selected = null;
        double nearest = wall;
        for (SceneBody body : bodies) {
            if (!visible.test(body.id)) continue;
            double distance = hit(body, ox, oy, oz, dx, dy, dz);
            // An opaque room surface at the same depth wins. Stable ID ties avoid insertion-order effects.
            if (distance >= wall - EPSILON) continue;
            if (distance < nearest - EPSILON
                || Math.abs(distance - nearest) <= EPSILON && selected != null && body.id.compareTo(selected) < 0) {
                nearest = distance;
                selected = body.id;
            }
        }
        return selected;
    }

    private static double hit(SceneBody body, double ox, double oy, double oz, double dx, double dy, double dz) {
        double x = body.qx, y = body.qy, z = body.qz, w = body.qw;
        ox -= body.x;
        oy -= body.y;
        oz -= body.z;
        // Apply the conjugate quaternion (transpose of the normalized body rotation) to ray and origin.
        double[] origin = { (1 - 2 * (y * y + z * z)) * ox + 2 * (x * y + z * w) * oy + 2 * (x * z - y * w) * oz,
            2 * (x * y - z * w) * ox + (1 - 2 * (x * x + z * z)) * oy + 2 * (y * z + x * w) * oz,
            2 * (x * z + y * w) * ox + 2 * (y * z - x * w) * oy + (1 - 2 * (x * x + y * y)) * oz };
        double[] direction = { (1 - 2 * (y * y + z * z)) * dx + 2 * (x * y + z * w) * dy + 2 * (x * z - y * w) * dz,
            2 * (x * y - z * w) * dx + (1 - 2 * (x * x + z * z)) * dy + 2 * (y * z + x * w) * dz,
            2 * (x * z + y * w) * dx + 2 * (y * z - x * w) * dy + (1 - 2 * (x * x + y * y)) * dz };
        double[] half = { body.halfX, body.halfY, body.halfZ };
        double near = 0, far = CLIP * 2;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < EPSILON) {
                if (origin[axis] < -half[axis] || origin[axis] > half[axis]) return Double.POSITIVE_INFINITY;
            } else {
                double a = (-half[axis] - origin[axis]) / direction[axis];
                double b = (half[axis] - origin[axis]) / direction[axis];
                near = Math.max(near, Math.min(a, b));
                far = Math.min(far, Math.max(a, b));
                if (near > far) return Double.POSITIVE_INFINITY;
            }
        }
        return near;
    }

    private VendingScenePicking() {}
}
