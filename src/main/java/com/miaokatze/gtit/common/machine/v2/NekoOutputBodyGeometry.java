package com.miaokatze.gtit.common.machine.v2;

import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBounds;

/** Shared render/collision dimensions. Native special meshes and ordinary item extrusion are not inflated. */
public final class NekoOutputBodyGeometry {

    public static final double BLOCK_HALF = .39, SPECIAL_HALF = .22, SPRITE_HALF = .27, SPRITE_DEPTH = .027;

    private NekoOutputBodyGeometry() {}

    /** Pinned vanilla RenderBlocks inventory cases, without a client class dependency on a dedicated server. */
    public static boolean nativeBlock(boolean vanilla, int renderType) {
        // Mod registrations/custom inventory helpers are not described by vanilla's switch. Preserve their volume.
        if (!vanilla || renderType < 0 || renderType > 41) return true;
        switch (renderType) {
            case 0:
            case 31:
            case 39:
            case 13:
            case 10:
            case 11:
            case 27:
            case 22:
            case 21:
            case 16:
            case 26:
            case 32:
            case 34:
            case 35:
                return true;
            default:
                return false;
        }
    }

    public static SceneBody create(String id, boolean block, boolean specialVolume, double x, double y, double z) {
        double half = block ? BLOCK_HALF : specialVolume ? SPECIAL_HALF : SPRITE_HALF;
        return new SceneBody(id, half, half, block || specialVolume ? half : SPRITE_DEPTH, x, y, z);
    }

    /** Retain the saved pose except the minimum displacement needed to keep newly expanded corners in the room. */
    public static SceneBody enlargeBlock(SceneBody body, SceneBounds bounds) {
        return resize(body, BLOCK_HALF, BLOCK_HALF, BLOCK_HALF, bounds);
    }

    /** Vanilla torch/plant ItemBlocks use the same thin native sprite mesh as ordinary items. */
    public static SceneBody flattenInventoryBlock(SceneBody body, SceneBounds bounds) {
        return resize(body, SPRITE_HALF, SPRITE_HALF, SPRITE_DEPTH, bounds);
    }

    private static SceneBody resize(SceneBody body, double halfX, double halfY, double halfZ, SceneBounds bounds) {
        SceneBody resized = new SceneBody(
            body.id,
            halfX,
            halfY,
            halfZ,
            body.x,
            body.y,
            body.z,
            body.qx,
            body.qy,
            body.qz,
            body.qw,
            body.vx,
            body.vy,
            body.vz,
            body.wx,
            body.wy,
            body.wz,
            body.sleeping,
            body.quietTicks);
        return fitInside(resized, bounds);
    }

    public static SceneBody fitInside(SceneBody body, SceneBounds bounds) {
        return body.withPosition(
            Math.max(bounds.minX + body.extentX(), Math.min(bounds.maxX - body.extentX(), body.x)),
            Math.max(bounds.minY + body.extentY(), Math.min(bounds.maxY - body.extentY(), body.y)),
            Math.max(bounds.minZ + body.extentZ(), Math.min(bounds.maxZ - body.extentZ(), body.z)));
    }
}
