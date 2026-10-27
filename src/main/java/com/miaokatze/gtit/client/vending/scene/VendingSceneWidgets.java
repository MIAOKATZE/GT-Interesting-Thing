package com.miaokatze.gtit.client.vending.scene;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtit.client.gui.NekoVendingTheme;
import com.miaokatze.gtit.gui.vm.IoColumnPanel;
import com.miaokatze.gtit.gui.vm.scene.VendingSceneBridge;
import com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames;
import com.miaokatze.gtit.gui.vm.scene.VendingSceneFrames.Entry;
import com.miaokatze.gtit.gui.vm.scene.VendingScenePicking;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBounds;
import com.miaokatze.miaokatzecore.api.miaovision.ScenePresentation;
import com.miaokatze.miaokatzecore.api.miaovision.SceneSnapshot;
import com.miaokatze.miaokatzecore.client.miaovision.MiaoVisionRenderer;
import com.miaokatze.miaokatzecore.client.miaovision.SceneCamera;
import com.miaokatze.miaokatzecore.client.miaovision.SceneModelResolver;
import com.miaokatze.miaokatzecore.client.miaovision.SceneViewport;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Pure client children; never owns a sync handler, never a second ItemSlot/handler, never a scene simulation. */
@SideOnly(Side.CLIENT)
public final class VendingSceneWidgets {

    public static void attach(VendingSceneBridge bridge, IoColumnPanel io) {
        VendingSceneView view = new VendingSceneView(io.getOutputSlotRefs());
        NativeScene scene = new NativeScene(view, bridge);
        io.getSceneMount()
            .child(
                scene.fullWidth()
                    .heightRelOffset(1, -25));
        io.getSceneMount()
            .child(
                NekoVendingTheme.button(() -> "复位", scene::reset)
                    .left(0)
                    .bottom(0)
                    .size(32, 20));
        io.getSceneMount()
            .child(
                NekoVendingTheme.button(() -> view.paused ? "继续" : "暂停", () -> view.paused = !view.paused)
                    .left(34)
                    .bottom(0)
                    .size(32, 20)
                    .addTooltipLine("只暂停画面；库存、取回与 ME 传输继续由服务器处理"));
        io.getSceneMount()
            .child(
                NekoVendingTheme
                    .button(() -> view.reducedMotion ? "静态" : "动态", () -> view.reducedMotion = !view.reducedMotion)
                    .left(68)
                    .bottom(0)
                    .size(32, 20));
        io.getSceneMount()
            .child(
                NekoVendingTheme.button(() -> "−", () -> scene.zoom = Math.max(.65, scene.zoom - .1))
                    .left(102)
                    .bottom(0)
                    .size(18, 20));
        io.getSceneMount()
            .child(
                NekoVendingTheme.button(() -> "+", () -> scene.zoom = Math.min(2, scene.zoom + .1))
                    .left(122)
                    .bottom(0)
                    .size(18, 20));
        io.getOutputStatsMount()
            .child(
                new GroupList(view).fullWidth()
                    .heightRelOffset(1, -91));
        io.getOutputStatsMount()
            .child(
                NekoVendingTheme.button(() -> "全取", () -> bridge.take(view.selected(), 0, 0))
                    .bottom(67)
                    .fullWidth()
                    .height(20));
        io.getOutputStatsMount()
            .child(
                NekoVendingTheme.button(() -> "半取", () -> bridge.take(view.selected(), 1, 0))
                    .bottom(45)
                    .fullWidth()
                    .height(20));
        final String[] amount = { "1" };
        io.getOutputStatsMount()
            .child(
                new TextFieldWidget()
                    .value(new StringValue.Dynamic(() -> amount[0], v -> { if (v.length() <= 7) amount[0] = v; }))
                    .bottom(23)
                    .fullWidth()
                    .height(20)
                    .background(NekoVendingTheme.INPUT)
                    .addTooltipLine("自定义取回数量"));
        io.getOutputStatsMount()
            .child(NekoVendingTheme.button(() -> "取回", () -> {
                try {
                    int n = Integer.parseInt(amount[0]);
                    if (n > 0 && n <= 1000000) bridge.take(view.selected(), 2, n);
                } catch (NumberFormatException ignored) {}
            })
                .bottom(1)
                .fullWidth()
                .height(20));
    }

    private static final class NativeScene extends Widget<NativeScene> implements Interactable {

        final VendingSceneView view;
        final VendingSceneBridge bridge;
        final MiaoVisionRenderer renderer = new MiaoVisionRenderer();
        final VendingRoomArt art = new VendingRoomArt();
        double yaw = 0, pitch = 27, zoom = 1.2;
        int dragX, dragY;
        boolean disposed;
        SceneSnapshot drawn;
        SceneBounds framing = new SceneBounds(-5.25, -.25, -3.85, 5.25, 4.55, 3.2);

        NativeScene(VendingSceneView view, VendingSceneBridge bridge) {
            this.view = view;
            this.bridge = bridge;
            background(NekoVendingTheme.PANEL);
            addTooltipLine("左键选择物品；Shift+左键取回同类全部；右键拖动旋转展示");
        }

        void reset() {
            yaw = 0;
            pitch = 27;
            zoom = 1.2;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            VendingSceneFrames.Frame f;
            long now = System.nanoTime();
            while ((f = bridge.poll()) != null) view.accept(f, now);
            bridge.requestResyncIfNeeded();
        }

        @Override
        public void draw(ModularGuiContext c, WidgetThemeEntry<?> theme) {
            if (disposed || getArea().width < 2 || getArea().height < 2) return;
            long now = System.nanoTime();
            drawn = view.snapshot(now);
            // Frame the actual room, not the tall legacy simulation envelope. Include high saved poses too:
            // this is camera composition, never a crop/filter that hides stock.
            double topY = 4.55;
            for (SceneBody body : drawn.bodies) topY = Math.max(topY, body.y + body.extentY() + .2);
            framing = new SceneBounds(-5.25, -.25, -3.85, 5.25, topY, 3.2);
            view.animate(now, drawn);
            Minecraft mc = Minecraft.getMinecraft();
            double sx = (double) mc.displayWidth / c.getScreenArea().width,
                sy = (double) mc.displayHeight / c.getScreenArea().height;
            int x = (int) Math.floor(c.transformX(0, 0) * sx), top = (int) Math.floor(c.transformY(0, 0) * sy);
            int right = (int) Math.ceil(c.transformX(getArea().width, 0) * sx),
                bottom = (int) Math.ceil(c.transformY(0, getArea().height) * sy);
            if (x < 0 || top < 0 || right > mc.displayWidth || bottom > mc.displayHeight || right <= x || bottom <= top)
                return;
            final long time = now;
            renderer.render(
                drawn,
                VendingSceneFrames.BOUNDS,
                framing,
                new SceneViewport(x, mc.displayHeight - bottom, right - x, bottom - top),
                new SceneCamera(yaw, pitch, zoom),
                new SceneModelResolver() {

                    @Override
                    public boolean render(SceneBody body, ItemStack stack, float opacity) {
                        return VendingNativeModels.render(stack);
                    }

                    @Override
                    public boolean extrudeInventorySprites() {
                        return true;
                    }

                    @Override
                    public ItemStack resolve(String id) {
                        return view.stack(id);
                    }

                    @Override
                    public void beforeBodies(SceneSnapshot snapshot) {
                        art.room(view.door(0), view.door(1), view.door(2));
                        art.cat(view.cat); // Opaque room/cat depth precedes Core's translucent item pass.
                    }

                    @Override
                    public void afterBodies(SceneSnapshot snapshot) {
                        GL11.glDisable(GL11.GL_TEXTURE_2D);
                        for (SceneBody body : snapshot.bodies) if (view.highlighted(body.id)) outline(body);
                        if (!view.reducedMotion) for (VendingSceneView.Particle p : view.particles(time)) {
                            double age = (time - p.start) / 1200000000.0;
                            VendingRoomArt.box(
                                p.x + p.dx * age,
                                p.y + age * 1.8,
                                p.z + p.dz * age,
                                .07,
                                .07,
                                .07,
                                0xc891f3,
                                (float) (1 - age));
                        }
                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                        GL11.glColor4f(1, 1, 1, 1);
                    }
                },
                new ScenePresentation() {

                    @Override
                    public float opacity(String id) {
                        return view.opacity(id, time);
                    }
                });
            if (isHovering()) view.hover(pick(c.getMouseX(), c.getMouseY()));
            if (view.entries()
                .isEmpty())
                IKey.str("出货展示 · 等待物品")
                    .draw(c, 6, getArea().height - 15, 150, 10, theme.getTheme());
        }

        private static void outline(SceneBody b) {
            GL11.glLineWidth(2);
            GL11.glColor4f(.45f, .9f, .95f, 1);
            GL11.glBegin(GL11.GL_LINES);
            double x = b.x - b.extentX() - .04, y = b.y - b.extentY() - .04, z = b.z - b.extentZ() - .04;
            double w = b.extentX() * 2 + .08, h = b.extentY() * 2 + .08, d = b.extentZ() * 2 + .08;
            for (int i = 0; i < 8; i++) for (int bit = 1; bit <= 4; bit *= 2) if ((i & bit) == 0) {
                int j = i | bit;
                GL11.glVertex3d(x + ((i & 1) > 0 ? w : 0), y + ((i & 2) > 0 ? h : 0), z + ((i & 4) > 0 ? d : 0));
                GL11.glVertex3d(x + ((j & 1) > 0 ? w : 0), y + ((j & 2) > 0 ? h : 0), z + ((j & 4) > 0 ? d : 0));
            }
            GL11.glEnd();
        }

        String pick(int mx, int my) {
            if (drawn == null) return null;
            long now = System.nanoTime();
            return VendingScenePicking.pick(
                drawn.bodies,
                art.pickOccluders(),
                framing,
                getArea().width,
                getArea().height,
                mx,
                my,
                yaw,
                pitch,
                zoom,
                id -> view.stack(id) != null && view.opacity(id, now) > 0);
        }

        @Override
        public Result onMousePressed(int button) {
            dragX = getContext().getMouseX();
            dragY = getContext().getMouseY();
            if (button == 0) {
                view.select(pick(dragX, dragY));
                if (Interactable.hasShiftDown()) bridge.take(view.selected(), 0, 0);
                return Result.SUCCESS;
            }
            return button == 1 ? Result.SUCCESS : Result.IGNORE;
        }

        @Override
        public void onMouseDrag(int button, long time) {
            if (button != 1) return;
            int x = getContext().getMouseX(), y = getContext().getMouseY();
            yaw = Math.max(-70, Math.min(70, yaw + (x - dragX) * .5));
            pitch = Math.max(5, Math.min(45, pitch + (y - dragY) * .4));
            dragX = x;
            dragY = y;
        }

        @Override
        public void onMouseLeaveArea() {
            view.hover(null);
        }

        @Override
        public void dispose() {
            disposed = true;
            renderer.close();
            view.close();
            super.dispose();
        }
    }

    private static final class GroupList extends Widget<GroupList> implements Interactable {

        final VendingSceneView view;
        int offset;

        GroupList(VendingSceneView view) {
            this.view = view;
            background(NekoVendingTheme.PANEL);
            addTooltipLine("按物品与 NBT 合并；滚轮浏览，点击联动展示中全部同类");
        }

        @Override
        public void draw(ModularGuiContext c, WidgetThemeEntry<?> theme) {
            List<Entry> groups = view.groups();
            int rows = Math.max(1, getArea().height / 24);
            offset = Math.max(0, Math.min(offset, Math.max(0, groups.size() - rows)));
            for (int i = 0; i < rows && i + offset < groups.size(); i++) {
                Entry e = groups.get(i + offset);
                int y = i * 24;
                if (view.highlighted(e.pose.id)) GuiDraw.drawRect(0, y, getArea().width, 23, 0xffdbc49b);
                ItemStack stack = view.stack(e.pose.id);
                if (stack != null) {
                    com.cleanroommc.modularui.drawable.ItemDrawable icon = new com.cleanroommc.modularui.drawable.ItemDrawable(
                        stack);
                    icon.draw(c, 2, y + 2, 16, 16, theme.getTheme());
                }
                IKey.str(Integer.toString(view.quantity(e.pose.id)))
                    .draw(c, 20, y + 6, 28, 10, theme.getTheme());
            }
            if (isHovering()) {
                int row = c.getMouseY() / 24 + offset;
                view.hover(row >= 0 && row < groups.size() ? groups.get(row).pose.id : null);
            }
        }

        @Override
        public Result onMousePressed(int button) {
            if (button != 0) return Result.IGNORE;
            List<Entry> groups = view.groups();
            int row = getContext().getMouseY() / 24 + offset;
            if (row >= 0 && row < groups.size()) view.select(groups.get(row).pose.id);
            return Result.SUCCESS;
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            offset = Math.max(0, offset + (direction == UpOrDown.UP ? -1 : 1));
            return true;
        }

        @Override
        public void onMouseLeaveArea() {
            view.hover(null);
        }
    }

    private VendingSceneWidgets() {}
}
