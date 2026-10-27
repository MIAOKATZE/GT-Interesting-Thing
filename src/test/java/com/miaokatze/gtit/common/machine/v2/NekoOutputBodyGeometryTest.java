package com.miaokatze.gtit.common.machine.v2;

import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBounds;

/** Pure geometry checks; no Minecraft bootstrap, GL context or visual-pass claim. */
public final class NekoOutputBodyGeometryTest {

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (int type : new int[] { 0, 11, 22, 31, 39 })
            check(NekoOutputBodyGeometry.nativeBlock(true, type), "native vanilla block model " + type);
        for (int type : new int[] { 1, 2, 6, 9, 40 })
            check(!NekoOutputBodyGeometry.nativeBlock(true, type), "native vanilla inventory sprite " + type);
        check(
            NekoOutputBodyGeometry.nativeBlock(false, 1) && NekoOutputBodyGeometry.nativeBlock(true, 100)
                && NekoOutputBodyGeometry.nativeBlock(true, -1),
            "unknown/modded native fallback is not flattened");
        SceneBounds bounds = new SceneBounds(-5, 0, -3, 5, 8, 3);
        SceneBody block = NekoOutputBodyGeometry.create("block", true, true, 0, 1, 0);
        SceneBody special = NekoOutputBodyGeometry.create("special", false, true, 0, 1, 0);
        SceneBody sprite = NekoOutputBodyGeometry.create("sprite", false, false, 0, 1, 0);
        check(
            block.halfX * 2 == .78 && block.halfY == block.halfX && block.halfZ == block.halfX,
            "HTML-sized native block has the same physical and rendered volume");
        check(
            special.halfX == .22 && special.halfY == .22 && special.halfZ == .22,
            "special GT meshes retain their audited isotropic dimensions");
        check(
            sprite.halfX == .27 && sprite.halfY == .27 && sprite.halfZ == .027,
            "ordinary sprites retain their native thin extrusion");
        SceneBody old = new SceneBody("corner", .22, .22, .22, 4.78, .22, -2.78).withVelocity(.1, .2, .3, .4, .5, .6)
            .withSleeping(true);
        SceneBody enlarged = NekoOutputBodyGeometry.enlargeBlock(old, bounds);
        bounds.validate(enlarged);
        check(
            enlarged.x == 5 - .39 && enlarged.y == .39 && enlarged.z == -3 + .39,
            "old floor/wall corner receives only the minimum required correction");
        check(
            enlarged.id.equals(old.id) && enlarged.vx == old.vx
                && enlarged.vy == old.vy
                && enlarged.vz == old.vz
                && enlarged.wx == old.wx
                && enlarged.wy == old.wy
                && enlarged.wz == old.wz
                && enlarged.sleeping == old.sleeping
                && enlarged.quietTicks == old.quietTicks,
            "migration preserves identity, motion and sleep metadata");
        old = old.withPosition(1, 3, .2)
            .withRotation(0, Math.sin(.3), 0, Math.cos(.3));
        enlarged = NekoOutputBodyGeometry.enlargeBlock(old, bounds);
        check(
            enlarged.x == old.x && enlarged.y == old.y
                && enlarged.z == old.z
                && enlarged.qx == old.qx
                && enlarged.qy == old.qy
                && enlarged.qz == old.qz
                && enlarged.qw == old.qw,
            "already valid rotated pose stays exact");
        SceneBody rear = NekoOutputBodyGeometry.fitInside(block.withPosition(0, 1.8, -2.65), bounds);
        bounds.validate(rear);
        check(rear.z == -3 + .39, "expanded rear-feed block stays inside simulation bounds");
        SceneBody torch = NekoOutputBodyGeometry.flattenInventoryBlock(old, bounds);
        check(
            torch.halfX == .27 && torch.halfY == .27
                && torch.halfZ == .027
                && torch.x == old.x
                && torch.y == old.y
                && torch.z == old.z
                && torch.qy == old.qy,
            "old vanilla ItemBlock sprite migrates to thin native extrusion without moving valid pose");
        System.out.println(
            "NekoOutputBodyGeometryTest PASS: native block size, thin/special preservation and bounded migration");
    }
}
