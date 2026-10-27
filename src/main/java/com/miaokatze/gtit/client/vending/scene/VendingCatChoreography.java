package com.miaokatze.gtit.client.vending.scene;

import java.util.List;

import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;

/** Bounded presentation-only reaction. No Entity, world, physics or inventory owner. */
public final class VendingCatChoreography {

    public static final double HOME_X = -3.6, HOME_Z = 1.85, REST_YAW = -.55;
    public double x = HOME_X, z = HOME_Z, yaw = REST_YAW, sitting = 1, stride, paw, headYaw, headPitch;
    private String target;
    private double age, destinationX, destinationZ, startYaw, goalYaw;
    private boolean approach;
    private String phase = "rest";

    public String phase() {
        return phase;
    }

    public boolean active() {
        return target != null;
    }

    public void react(String id) {
        // A stream of clicks/deliveries cannot restart or extend this finite reaction.
        if (id == null || active()) return;
        target = id;
        age = 0;
        startYaw = yaw;
        phase = "watch";
        approach = false;
    }

    public void reset() {
        target = null;
        age = 0;
        phase = "rest";
        x = HOME_X;
        z = HOME_Z;
        yaw = REST_YAW;
        sitting = 1;
        stride = paw = headYaw = headPitch = 0;
        approach = false;
    }

    public void update(double dt, List<SceneBody> bodies, boolean reducedMotion, boolean paused) {
        if (reducedMotion) {
            if (active()) reset();
            return;
        }
        if (paused || !active()) return; // Literally no clock/pose work while idle.
        SceneBody body = find(bodies, target);
        if (body == null) {
            reset();
            return;
        }
        age += Math.max(0, Math.min(.1, dt));
        stride = paw = 0;
        if (age < 2.4) {
            phase = "watch";
            yaw = angle(startYaw, Math.atan2(body.x - x, body.z - z), ease(age / .9));
        } else {
            if ("watch".equals(phase)) {
                chooseApproach(bodies, body);
                if (!approach) phase = "observe";
            }
            if (!approach) {
                if (age >= 3.5) {
                    reset();
                    return;
                }
            } else if (age < 4.4) {
                phase = "approach";
                double t = ease((age - 2.4) / 2);
                x = mix(HOME_X, destinationX, t);
                z = mix(HOME_Z, destinationZ, t);
                yaw = angle(startYaw, goalYaw, ease((age - 2.4) / .45));
                sitting = 1 - ease((age - 2.4) / .4);
                stride = Math.sin(age * 12) * .28;
            } else if (age < 6.6) {
                phase = age < 4.9 ? "sniff" : "paw";
                x = destinationX;
                z = destinationZ;
                yaw = angle(goalYaw, Math.PI, ease((age - 4.4) / .35));
                sitting = 0;
                double t = age - 4.9;
                paw = t > 0 && t < 1.1 ? Math.sin(t / 1.1 * Math.PI) * .95 : 0;
            } else if (age < 8.8) {
                phase = "return";
                double t = ease((age - 6.6) / 2.2);
                x = mix(destinationX, HOME_X, t);
                z = mix(destinationZ, HOME_Z, t);
                yaw = angle(Math.PI, goalYaw + Math.PI, ease((age - 6.6) / .4));
                sitting = 0;
                stride = Math.sin(age * 12) * .28;
            } else if (age < 9.4) {
                phase = "sit";
                x = HOME_X;
                z = HOME_Z;
                yaw = angle(goalYaw + Math.PI, REST_YAW, ease((age - 8.8) / .6));
                sitting = ease((age - 8.8) / .6);
            } else {
                reset();
                return;
            }
        }
        headYaw = clamp(
            Math.atan2(
                Math.sin(Math.atan2(body.x - x, body.z - z) - yaw),
                Math.cos(Math.atan2(body.x - x, body.z - z) - yaw)),
            -.75,
            .75);
        headPitch = clamp(Math.atan2(.8 - body.y, Math.hypot(body.x - x, body.z - z)), -.45, .45);
    }

    private void chooseApproach(List<SceneBody> bodies, SceneBody body) {
        // Only settled, low, reachable targets; a conservative swept corridor clears the whole cat.
        if (!body.sleeping || body.y > 1.1) return;
        double nx = body.x, nz = body.z + 1.1;
        double dx = nx - HOME_X, dz = nz - HOME_Z, length = dx * dx + dz * dz;
        if (length > 20 || Math.abs(nx) > 4.1 || nz < -.8 || nz > 2.15) return;
        for (SceneBody other : bodies) {
            if (other == body || other.y - other.extentY() > 1.4) continue;
            double t = length == 0 ? 0 : clamp(((other.x - HOME_X) * dx + (other.z - HOME_Z) * dz) / length, 0, 1);
            if (Math.hypot(other.x - HOME_X - t * dx, other.z - HOME_Z - t * dz)
                < .65 + Math.max(other.extentX(), other.extentZ())) return;
        }
        destinationX = nx;
        destinationZ = nz;
        startYaw = yaw;
        goalYaw = Math.atan2(dx, dz);
        approach = true;
    }

    private static SceneBody find(List<SceneBody> bodies, String id) {
        for (SceneBody body : bodies) if (body.id.equals(id)) return body;
        return null;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double ease(double v) {
        v = clamp(v, 0, 1);
        return v * v * (3 - 2 * v);
    }

    private static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double angle(double a, double b, double t) {
        return a + Math.atan2(Math.sin(b - a), Math.cos(b - a)) * t;
    }
}
