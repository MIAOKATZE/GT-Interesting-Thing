package com.miaokatze.gtit.gui.vm.scene;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.miaokatze.gtit.client.vending.scene.VendingCatChoreography;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;

/** Headless proof of finite, event-driven presentation without any game inventory or Entity. */
public final class VendingCatChoreographyTest {

    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }

    private static SceneBody settled(String id, double x, double z) {
        return new SceneBody(id, .22, .22, .22, x, .3, z, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, true, 20);
    }

    public static void main(String[] args) {
        VendingCatChoreography cat = new VendingCatChoreography();
        SceneBody target = settled("1", -2, -.1);
        List<SceneBody> bodies = Collections.singletonList(target);
        for (int i = 0; i < 1000; i++) cat.update(.05, bodies, false, false);
        check(!cat.active() && cat.x == VendingCatChoreography.HOME_X && cat.sitting == 1, "idle has no activity");
        cat.react("1");
        boolean watched = false, approached = false, sniffed = false, pawed = false, returned = false, sat = false;
        for (int i = 0; i < 220; i++) {
            cat.react("1"); // repeated selection cannot indefinitely restart a running reaction
            cat.update(.05, bodies, false, false);
            watched |= cat.phase()
                .equals("watch");
            approached |= cat.phase()
                .equals("approach");
            sniffed |= cat.phase()
                .equals("sniff");
            pawed |= cat.paw > .5;
            returned |= cat.phase()
                .equals("return");
            sat |= cat.phase()
                .equals("sit");
            if (i > 180 && !cat.active()) break;
        }
        check(
            watched && approached && sniffed && pawed && returned && sat && !cat.active(),
            "full finite choreography");
        check(
            cat.x == VendingCatChoreography.HOME_X && cat.z == VendingCatChoreography.HOME_Z && cat.sitting == 1,
            "returns home and sits");
        cat.react("1");
        List<SceneBody> blocked = Arrays.asList(target, settled("2", -2.8, 1.4));
        for (int i = 0; i < 80; i++) cat.update(.05, blocked, false, false);
        check(!cat.active() && cat.x == VendingCatChoreography.HOME_X, "pile blocks walking corridor");
        cat.react("1");
        cat.update(.1, bodies, false, true);
        check(
            cat.phase()
                .equals("watch") && cat.yaw == VendingCatChoreography.REST_YAW,
            "pause freezes pose");
        cat.update(.1, bodies, true, false);
        check(!cat.active() && cat.sitting == 1, "reduced motion immediately rests");
        cat.react("1");
        cat.update(.1, Collections.emptyList(), false, false);
        check(!cat.active(), "removed target cancels safely");
        check(target.x == -2 && target.z == -.1 && target.sleeping, "scene body never mutated");
        System.out.println(
            "VendingCatChoreographyTest: bounded phases, corridor, idle, pause, reduced motion and removal passed");
    }

    private VendingCatChoreographyTest() {}
}
