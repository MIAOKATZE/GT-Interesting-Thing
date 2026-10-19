package com.miaokatze.gtit.gift.ui;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.ScrollArea;
import com.miaokatze.gtit.client.gui.utility.UtilityUiStyle;
import com.miaokatze.gtit.testutil.MinecraftTestBootstrap;
import com.miaokatze.gtit.testutil.SimpleAssert;
import com.miaokatze.gtit.testutil.TestRunner;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Management packets contain no client-supplied authority or gift IDs. */
public final class GiftAccessTest {

    public static void main(String[] args) throws Exception {
        if (!MinecraftTestBootstrap.enter(GiftAccessTest.class, args)) return;
        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("openRequestContainsNoClientAuthority", () -> {
            new GiftAccessHandler.Open().fromBytes(Unpooled.EMPTY_BUFFER);
            ByteBuf forged = Unpooled.wrappedBuffer(new byte[] { 1 });
            try {
                new GiftAccessHandler.Open().fromBytes(forged);
                throw new AssertionError("Gift management must reject a client permission payload");
            } catch (IllegalArgumentException expected) {
                // Connection identity is the only input accepted by the server.
            } finally {
                forged.release();
            }
        });
        cases.put("revokeAndRegrantAreRepresentable", () -> {
            for (boolean allowed : new boolean[] { true, false, true }) {
                ByteBuf wire = Unpooled.buffer();
                try {
                    new GiftAccessHandler.Access(allowed, false).toBytes(wire);
                    GiftAccessHandler.Access received = new GiftAccessHandler.Access();
                    received.fromBytes(wire);
                    SimpleAssert.that(
                        received.allowed == allowed && !received.remind,
                        "Revocation and subsequent authorization must survive the real packet codec");
                } finally {
                    wire.release();
                }
            }
        });
        cases.put("shortAndNarrowViewportStaysWithinScreen", () -> {
            SimpleAssert.that(
                UtilityUiStyle.viewportExtent(360, 270) == 258,
                "A short GUI viewport needs vertical scrolling instead of a 360px root");
            SimpleAssert.that(
                UtilityUiStyle.viewportExtent(420, 320) == 308,
                "A narrow GUI viewport needs horizontal scrolling instead of a 420px root");
            SimpleAssert.that(
                UtilityUiStyle.viewportExtent(360, 600) == 372,
                "A large screen must retain the full form plus its frame");
        });
        cases.put("nativeViewportScrollsAndClearsOffsetAfterResize", () -> {
            ModularPanel panel = UtilityUiStyle.scrollable("gift_test", 406, 360, new ParentWidget<>().size(406, 360));
            ScrollWidget<?> viewport = (ScrollWidget<?>) panel.getChildren()
                .get(0);
            ScrollArea area = viewport.getScrollArea();
            area.set(0, 0, 410, 250);
            viewport.postResize();
            SimpleAssert.that(
                area.isScrollBarYActive() && !area.isScrollBarXActive(),
                "A short viewport needs only the native vertical scrollbar");
            area.getScrollY()
                .scrollTo(area, 100);
            SimpleAssert.that(viewport.getScrollY() == 100, "The lower form must be reachable");
            area.set(0, 0, 300, 364);
            viewport.postResize();
            SimpleAssert.that(
                area.isScrollBarXActive() && !area.isScrollBarYActive(),
                "A narrow viewport needs only the native horizontal scrollbar");
            area.getScrollX()
                .scrollTo(area, 75);
            area.set(0, 0, 410, 364);
            viewport.postResize();
            SimpleAssert.that(
                !area.isScrollBarXActive() && !area.isScrollBarYActive()
                    && viewport.getScrollX() == 0
                    && viewport.getScrollY() == 0,
                "Growing the viewport must remove bars and reset hidden offsets");
        });
        TestRunner.run(GiftAccessTest.class, cases);
    }
}
