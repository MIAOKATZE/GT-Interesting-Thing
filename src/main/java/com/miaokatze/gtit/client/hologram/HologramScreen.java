package com.miaokatze.gtit.client.hologram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.structurelib.ChannelDescription;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.miaokatze.gtit.hologram.HologramNetwork;

/** Responsive vanilla screen. Rehearsal controls only the view, construction controls only send server actions. */
public final class HologramScreen extends GuiScreen {

    private static final ResourceLocation CLOTH = texture("cloth"), PANEL = texture("panel"), RIVET = texture("rivet");
    private final List<Control> controls = new ArrayList<>();
    private final RenderItem itemRenderer = new RenderItem();
    private HologramState state;
    private GuiTextField mainField, channelField, valueField;
    private int page, listOffset, view, selected = -1, candidateOffset, hovered = -1, layer;
    private int viewportX, viewportY, viewportWidth, viewportHeight, dragX, dragY;
    private boolean dragging, moved, allLayers = true, rehearsalPlaying;
    private double yaw = Math.PI / 4, pitch = Math.PI / 6, zoom = 1, rehearsal = -1;
    private long lastFrame = System.nanoTime();
    private String notice = "";
    private List<String> tooltip;
    private final List<String> channelNames = new ArrayList<>();

    HologramScreen(HologramState state) {
        this.state = state;
        layer = state.maxY;
        selected = state.data.getInteger("selected");
    }

    void updateState(HologramState next) {
        state = next;
        if (allLayers) layer = next.maxY;
        selected = next.data.getInteger("selected");
        if (mainField != null && !mainField.isFocused())
            mainField.setText(Integer.toString(next.data.getInteger("main")));
        rebuildChannels();
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation("gtit", "textures/gui/pocket/POCKET_C2_" + name + ".png");
    }

    private static String tr(String key, String fallback) {
        return HologramClient.text(key, fallback);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        mainField = new GuiTextField(fontRendererObj, 82, 61, Math.max(40, width - 164), 18);
        mainField.setMaxStringLength(10);
        mainField.setText(Integer.toString(state.data.getInteger("main")));
        channelField = new GuiTextField(fontRendererObj, 12, 88, Math.max(75, width - 166), 18);
        channelField.setMaxStringLength(64);
        valueField = new GuiTextField(fontRendererObj, Math.max(99, width - 145), 88, 54, 18);
        valueField.setMaxStringLength(10);
        valueField.setText("1");
        rebuildChannels();
    }

    private void rebuildChannels() {
        TreeSet<String> names = new TreeSet<>(
            ChannelDescription.getAll()
                .keySet());
        NBTTagCompound channels = state.data.getCompoundTag("channels");
        for (Object key : channels.func_150296_c()) names.add(key.toString());
        channelNames.clear();
        channelNames.addAll(names);
        listOffset = Math.max(0, Math.min(listOffset, Math.max(0, channelNames.size() - 1)));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        mainField.updateCursorCounter();
        channelField.updateCursorCounter();
        valueField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        if (rehearsalPlaying) rehearsal += Math.min(.5, (now - lastFrame) / 1000000000.0);
        lastFrame = now;
        controls.clear();
        tooltip = null;
        drawDefaultBackground();
        skin(CLOTH, 4, 4, width - 8, height - 8);
        drawRect(8, 27, width - 8, height - 40, 0xde142a32);
        skin(PANEL, 8, 6, width - 16, 22);
        drawString(fontRendererObj, tr("title", "猫猫全息投影仪") + "  /  " + state.data.getString("title"), 15, 13, 0xf5e4b9);
        skin(RIVET, width - 23, 11, 9, 9);
        int tabWidth = (width - 24) / 4;
        control(100, 12, 32, tabWidth, 19, tr("tab.preview", "结构预览"), true, mouseX, mouseY);
        control(101, 12 + tabWidth, 32, tabWidth, 19, tr("tab.channels", "信道配置"), true, mouseX, mouseY);
        control(102, 12 + tabWidth * 2, 32, tabWidth, 19, tr("tab.materials", "材料清单"), true, mouseX, mouseY);
        control(103, 12 + tabWidth * 3, 32, tabWidth, 19, tr("tab.help", "操作说明"), true, mouseX, mouseY);
        if (page == 0) drawPreview(mouseX, mouseY);
        if (page == 1) drawChannels(mouseX, mouseY);
        if (page == 2) drawMaterials(mouseX, mouseY);
        if (page == 3) drawHelp(mouseX, mouseY);
        if (page == 4) drawCellDetails(mouseX, mouseY);
        drawFooter(mouseX, mouseY);
        if (!notice.isEmpty()) drawString(fontRendererObj, trim(notice, width - 26), 13, height - 77, 0xffbd91);
        if (tooltip != null) drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
    }

    private void drawPreview(int mouseX, int mouseY) {
        int toolbarY = 55, unit = (width - 24) / 5;
        String[] modes = { tr("view.hologram", "全息"), tr("view.current", "现状"), tr("view.diff", "差分") };
        control(10, 12, toolbarY, unit, 18, modes[view], true, mouseX, mouseY);
        control(
            11,
            12 + unit,
            toolbarY,
            unit,
            18,
            allLayers ? tr("layer.all", "全部层") : tr("layer", "层") + " " + layer,
            true,
            mouseX,
            mouseY);
        control(12, 12 + unit * 2, toolbarY, unit / 2, 18, "−", layer > state.minY, mouseX, mouseY);
        control(13, 12 + unit * 2 + unit / 2, toolbarY, unit / 2, 18, "+", layer < state.maxY, mouseX, mouseY);
        control(14, 12 + unit * 3, toolbarY, unit, 18, tr("camera.reset", "复位"), true, mouseX, mouseY);
        control(
            15,
            12 + unit * 4,
            toolbarY,
            unit,
            18,
            tr("projection", "世界投影") + (HologramClient.worldPreview ? " ✓" : " ×"),
            true,
            mouseX,
            mouseY);
        int configY = 77, configWidth = (width - 24) / 3;
        String[] modes2 = { tr("mode.build", "建造"), tr("mode.replace", "原位升级"), tr("mode.remove", "拆除") };
        String[] scopes = { tr("scope.all", "全部"), tr("scope.layer", "当前层"), tr("scope.selected", "选中格") };
        control(
            16,
            12,
            configY,
            configWidth,
            18,
            modes2[Math.floorMod(state.data.getInteger("mode"), 3)],
            idle(),
            mouseX,
            mouseY);
        control(
            17,
            12 + configWidth,
            configY,
            configWidth,
            18,
            scopes[Math.floorMod(state.data.getInteger("scope"), 3)],
            idle(),
            mouseX,
            mouseY);
        control(
            18,
            12 + configWidth * 2,
            configY,
            configWidth,
            18,
            tr("facing", "朝向") + " " + facingName(),
            idle() && state.data.getIntArray("facings").length > 1,
            mouseX,
            mouseY);
        int lowerHeight = 54;
        viewportX = 12;
        viewportY = 98;
        viewportWidth = width - 24;
        viewportHeight = Math.max(8, height - 98 - lowerHeight - 79);
        drawRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, 0xff0c1e28);
        if (state.cells.isEmpty()) {
            centered(
                state.data.getBoolean("target") ? tr("preview.empty", "尚无可预览的结构，请重新采集")
                    : tr("target.empty", "手持投影仪右键控制器以选择目标"),
                viewportY + viewportHeight / 2,
                0xa6c5c7);
        } else {
            // Scissor protects every UI element, including when zooming and rotating a large structure.
            int factor = new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
                .getScaleFactor();
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(
                viewportX * factor,
                mc.displayHeight - (viewportY + viewportHeight) * factor,
                viewportWidth * factor,
                viewportHeight * factor);
            try {
                hovered = HologramRenderer.preview(
                    state,
                    viewportX,
                    viewportY,
                    viewportWidth,
                    viewportHeight,
                    yaw,
                    pitch,
                    zoom,
                    allLayers ? Integer.MAX_VALUE : layer,
                    view,
                    selected,
                    mouseX,
                    mouseY,
                    rehearsal);
            } finally {
                GL11.glPopAttrib();
            }
        }
        int y = viewportY + viewportHeight + 3;
        drawString(fontRendererObj, tr("camera.help", "拖动旋转 · 滚轮缩放 · 点击选格"), 14, y, 0x8bbecb);
        int rehearsalWidth = (width - 24) / 4;
        control(
            20,
            12,
            y + 13,
            rehearsalWidth,
            18,
            rehearsalPlaying ? tr("rehearsal.pause", "暂停预演") : tr("rehearsal.play", "播放预演"),
            !state.cells.isEmpty(),
            mouseX,
            mouseY);
        control(
            21,
            12 + rehearsalWidth,
            y + 13,
            rehearsalWidth,
            18,
            tr("rehearsal.replay", "重播预演"),
            !state.cells.isEmpty(),
            mouseX,
            mouseY);
        control(
            22,
            12 + rehearsalWidth * 2,
            y + 13,
            rehearsalWidth,
            18,
            tr("rehearsal.end", "退出预演"),
            rehearsal >= 0,
            mouseX,
            mouseY);
        control(23, 12 + rehearsalWidth * 3, y + 13, rehearsalWidth, 18, tr("scan", "重新采集"), idle(), mouseX, mouseY);
        HologramState.Cell cell = selectedCell();
        if (cell != null) {
            drawString(
                fontRendererObj,
                trim(
                    "(" + cell.dx
                        + ","
                        + cell.dy
                        + ","
                        + cell.dz
                        + ") "
                        + statusName(cell.status)
                        + "  "
                        + cellName(cell),
                    width - 111),
                14,
                y + 38,
                cell.color());
            control(26, width - 96, y + 33, 80, 18, tr("cell.details", "部件详情"), true, mouseX, mouseY);
        }
    }

    private void drawCellDetails(int mouseX, int mouseY) {
        HologramState.Cell cell = selectedCell();
        control(27, 14, 56, 78, 18, tr("back", "返回预览"), true, mouseX, mouseY);
        if (cell == null) return;
        drawString(
            fontRendererObj,
            trim(cellName(cell) + "  " + statusName(cell.status), width - 118),
            101,
            61,
            cell.color());
        drawString(
            fontRendererObj,
            tr("cell.position", "世界坐标") + " " + cell.x + " / " + cell.y + " / " + cell.z,
            16,
            81,
            0xb9d5d4);
        drawString(
            fontRendererObj,
            tr("cell.local", "相对控制器") + " " + cell.dx + " / " + cell.dy + " / " + cell.dz,
            16,
            95,
            0xb9d5d4);
        drawString(
            fontRendererObj,
            cell.pinScope.equals("channel") ? tr("candidate.channel", "部件须符合统一信道目标")
                : tr("candidate.newbuild", "仅选择空位新建取料；保留已有合法部件"),
            16,
            115,
            0xe4d3a9);
        int rows = Math.max(1, (height - 218) / 25);
        for (int i = 0; i < rows && i + candidateOffset < cell.candidates.size(); i++) {
            ItemStack stack = cell.candidates.get(i + candidateOffset);
            int y = 130 + i * 25;
            drawRect(12, y, width - 15, y + 23, 0xff254751);
            drawItem(stack, 17, y + 3);
            drawString(
                fontRendererObj,
                trim((cell.chosenChoice == i + candidateOffset ? "✓ " : "") + stack.getDisplayName(), width - 75),
                40,
                y + 7,
                cell.chosenChoice == i + candidateOffset ? 0xa2e8b8 : 0xcde1dc);
            controls.add(new Control(1000 + i + candidateOffset, 12, y, width - 27, 23, "", idle()));
            if (mouseX > 12 && mouseX < width - 15 && mouseY >= y && mouseY < y + 23)
                tooltip = Collections.singletonList(stack.getDisplayName());
        }
        if (cell.candidates.isEmpty())
            drawString(fontRendererObj, tr("candidate.empty", "此位置没有可安全选择的部件"), 16, 135, 0x94afb4);
        control(24, 14, height - 81, 55, 18, "◀", candidateOffset > 0, mouseX, mouseY);
        control(25, 74, height - 81, 55, 18, "▶", candidateOffset + rows < cell.candidates.size(), mouseX, mouseY);
    }

    private void drawChannels(int mouseX, int mouseY) {
        drawString(fontRendererObj, tr("main", "主信道"), 14, 67, 0xe4d3a9);
        mainField.drawTextBox();
        control(30, width - 72, 61, 58, 18, tr("apply", "应用"), idle(), mouseX, mouseY);
        channelField.drawTextBox();
        valueField.drawTextBox();
        control(31, width - 83, 88, 69, 18, tr("channel.set", "添加 / 更新"), idle(), mouseX, mouseY);
        int y = 112, rowHeight = 22;
        NBTTagCompound channels = state.data.getCompoundTag("channels");
        int rows = Math.max(1, (height - 203) / rowHeight);
        for (int i = 0; i < rows && listOffset + i < channelNames.size(); i++) {
            String name = channelNames.get(listOffset + i);
            int ry = y + i * rowHeight;
            drawRect(12, ry, width - 14, ry + rowHeight - 2, channels.hasKey(name) ? 0xff254751 : 0xff1b333b);
            String label = name + (channels.hasKey(name) ? " = " + channels.getInteger(name) : "  +");
            drawString(
                fontRendererObj,
                trim(label, width - 100),
                18,
                ry + 6,
                channels.hasKey(name) ? 0x9eefd3 : 0x94afb4);
            controls.add(new Control(2000 + listOffset + i, 12, ry, width - 64, rowHeight - 2, "", idle()));
            control(
                3000 + listOffset + i,
                width - 61,
                ry + 1,
                43,
                18,
                tr("delete", "删除"),
                idle() && channels.hasKey(name),
                mouseX,
                mouseY);
            if (mouseX >= 12 && mouseX < width - 65 && mouseY >= ry && mouseY < ry + rowHeight)
                channelTooltip(name, mouseX, mouseY);
        }
        int bottom = height - 91;
        control(32, 12, bottom, (width - 28) / 2, 19, tr("coil", "线圈信道") + " coil", idle(), mouseX, mouseY);
        control(
            33,
            16 + (width - 28) / 2,
            bottom,
            (width - 28) / 2,
            19,
            tr("noHatches", "无仓室") + (state.data.getBoolean("noHatches") ? " ✓" : " ×"),
            idle(),
            mouseX,
            mouseY);
        drawString(
            fontRendererObj,
            trim(tr("channel.help", "输入小写信道名与正整数；滚轮浏览候选，点击行填入"), width - 26),
            14,
            height - 69,
            0x94afb4);
    }

    private void drawMaterials(int mouseX, int mouseY) {
        drawString(fontRendererObj, tr("materials.header", "材料                         所需 / 可用"), 14, 62, 0xe4d3a9);
        int rows = Math.max(1, (height - 133) / 24);
        for (int i = 0; i < rows && listOffset + i < state.materials.size(); i++) {
            HologramState.Material m = state.materials.get(listOffset + i);
            int y = 78 + i * 24;
            drawRect(12, y, width - 14, y + 22, i % 2 == 0 ? 0xff203e46 : 0xff1a343d);
            drawItem(m.stack, 16, y + 3);
            drawString(
                fontRendererObj,
                trim(m.stack == null ? tr("material.unknown", "未知材料") : m.stack.getDisplayName(), width - 126),
                38,
                y + 7,
                0xcde1dc);
            drawString(
                fontRendererObj,
                m.required + " / " + m.available,
                width - 89,
                y + 7,
                m.available < m.required ? 0xff9292 : 0xa2e8b8);
            if (m.stack != null && mouseY >= y && mouseY < y + 22 && mouseX > 12 && mouseX < width - 14)
                tooltip = Collections.singletonList(
                    m.stack.getDisplayName() + " · "
                        + tr("materials.missing", "缺少")
                        + " "
                        + Math.max(0, m.required - m.available));
        }
        if (state.materials.isEmpty()) centered(tr("materials.empty", "当前没有需要补充的材料"), 92, 0xa6c5c7);
    }

    private void drawHelp(int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        String unsupported = state.data.getString("unsupportedReason");
        if (!unsupported.isEmpty()) lines.add(unsupported);
        lines.add(tr("help.1", "右键控制器选取结构。调整信道后会重新采集结构与材料。"));
        lines.add(tr("help.2", "拖动旋转，滚轮缩放。点击方块查看状态与可选材料。"));
        lines.add(tr("help.3", "全息显示目标，现状显示现场，差分突出需要改变的位置。"));
        lines.add(tr("help.4", "绿色：已满足；蓝色：待补；红色：缺料；金色：受保护；紫色：不支持。"));
        lines.add(tr("help.5", "原位升级仅处理服务端确认可替换的部件；不会跳过保护与缺料。"));
        lines.add(tr("help.6", "预演可暂停与重播，仅演示结构；施工进度以服务端回执为准。"));
        lines.add(tr("help.7", "世界投影保留到离开目标范围。切层与朝向只使用目标允许的配置。"));
        String description = state.data.getString("description");
        if (!description.isEmpty()) lines.add(description);
        int y = 60;
        for (String line : lines) {
            for (Object wrapped : fontRendererObj.listFormattedStringToWidth(line, width - 36)) {
                if (y > height - 111) break;
                drawString(fontRendererObj, wrapped.toString(), 18, y, 0xb9d5d4);
                y += 12;
            }
            y += 5;
        }
        control(40, 14, height - 94, (width - 32) / 2, 20, tr("hints", "原版结构提示"), idle(), mouseX, mouseY);
        control(
            41,
            18 + (width - 32) / 2,
            height - 94,
            (width - 32) / 2,
            20,
            tr("native", "原版构造回退"),
            idle(),
            mouseX,
            mouseY);
    }

    private void drawFooter(int mouseX, int mouseY) {
        int y = height - 38;
        int completed = state.data.getInteger("completed"), total = state.data.getInteger("total");
        drawRect(12, y - 14, width - 14, y - 10, 0xff0b1720);
        if (total > 0) drawRect(12, y - 14, 12 + (width - 26) * Math.min(completed, total) / total, y - 10, 0xff74d0af);
        String status = state.data.getString("status");
        if (mouseY >= y - 27 && mouseY < y - 15 && mouseX >= 12 && mouseX < width - 14 && !status.isEmpty()) {
            tooltip = new ArrayList<>(fontRendererObj.listFormattedStringToWidth(status, Math.max(140, width - 45)));
        }
        drawString(
            fontRendererObj,
            trim(
                statusName(status) + " "
                    + completed
                    + "/"
                    + total
                    + "  "
                    + tr("materials.missing", "缺少")
                    + " "
                    + state.data.getInteger("missing")
                    + "  "
                    + tr("protected", "受保护")
                    + " "
                    + state.data.getInteger("protected"),
                width - 26),
            13,
            y - 25,
            0xcce5d8);
        int unit = (width - 24) / 4, job = state.data.getInteger("job");
        control(
            50,
            12,
            y,
            unit,
            23,
            tr("start", "开始施工"),
            idle() && state.data.getBoolean("supported") && state.data.getBoolean("target"),
            mouseX,
            mouseY);
        control(
            51,
            12 + unit,
            y,
            unit,
            23,
            job == 2 ? tr("resume", "继续施工") : tr("pause", "暂停施工"),
            job == 1 || job == 2,
            mouseX,
            mouseY);
        control(52, 12 + unit * 2, y, unit, 23, tr("cancel", "取消施工"), job == 1 || job == 2, mouseX, mouseY);
        control(53, 12 + unit * 3, y, unit, 23, tr("close", "关闭"), true, mouseX, mouseY);
    }

    private boolean idle() {
        int job = state.data.getInteger("job");
        return job != 1 && job != 2;
    }

    private String trim(String text, int size) {
        return fontRendererObj.trimStringToWidth(text, Math.max(10, size));
    }

    private void centered(String text, int y, int color) {
        drawCenteredString(fontRendererObj, trim(text, width - 30), width / 2, y, color);
    }

    private String facingName() {
        int f = state.data.getInteger("facing");
        if (f < 0 || f >= ExtendedFacing.values().length) return "?";
        ExtendedFacing facing = ExtendedFacing.values()[f];
        String direction = facing.getDirection()
            .name()
            .toLowerCase(Locale.ROOT);
        String[] names = { "下", "上", "北", "南", "西", "东" };
        int ordinal = facing.getDirection()
            .ordinal();
        return tr("direction." + direction, ordinal < names.length ? names[ordinal] : direction) + " "
            + facing.getRotation()
                .ordinal() * 90
            + "°"
            + (facing.getFlip()
                .ordinal() == 0 ? ""
                    : " " + tr("mirror", "镜像")
                        + facing.getFlip()
                            .ordinal());
    }

    private HologramState.Cell selectedCell() {
        return selected >= 0 && selected < state.cells.size() ? state.cells.get(selected) : null;
    }

    private String cellName(HologramState.Cell cell) {
        if (cell.chosenChoice >= 0 && cell.chosenChoice < cell.candidates.size())
            return cell.candidates.get(cell.chosenChoice)
                .getDisplayName();
        if (!cell.candidates.isEmpty()) return cell.candidates.get(0)
            .getDisplayName();
        net.minecraft.block.Block block = cell.block(view == 1);
        return block == null ? tr("cell.unknown", "尚未确定目标部件") : block.getLocalizedName();
    }

    private static String statusName(String status) {
        switch (status) {
            case "pending":
                return tr("status.pending", "待补");
            case "satisfied":
                return tr("status.satisfied", "已满足");
            case "missing":
                return tr("status.missing", "缺料");
            case "protected":
                return tr("status.protected", "受保护");
            case "unsupported":
                return tr("status.unsupported", "不支持");
            case "placed":
                return tr("status.placed", "已放置");
            case "replaced":
                return tr("status.replaced", "已替换");
            case "removed":
                return tr("status.removed", "已拆除");
            default:
                return status;
        }
    }

    private void skin(ResourceLocation texture, int x, int y, int w, int h) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glColor4f(1, 1, 1, 1);
            mc.getTextureManager()
                .bindTexture(texture);
            func_152125_a(x, y, 0, 0, 1, 1, w, h, 1, 1);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void control(int id, int x, int y, int w, int h, String label, boolean enabled, int mx, int my) {
        Control c = new Control(id, x, y, w, h, label, enabled);
        controls.add(c);
        skin(texture(c.contains(mx, my) && enabled ? "btn_pressed" : "btn"), x, y, w, h);
        drawCenteredString(
            fontRendererObj,
            trim(label, w - 6),
            x + w / 2,
            y + (h - 8) / 2,
            enabled ? 0xf0ddb3 : 0x647c7f);
    }

    private void drawItem(ItemStack stack, int x, int y) {
        if (stack == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            RenderHelper.enableGUIStandardItemLighting();
            itemRenderer.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void channelTooltip(String name, int x, int y) {
        List<String> text = new ArrayList<>();
        text.add(name);
        ChannelDescription description = ChannelDescription.getAll()
            .get(name);
        if (description != null) for (Map.Entry<String, String> d : description.getDescriptions()
            .entrySet()) {
                text.addAll(
                    fontRendererObj
                        .listFormattedStringToWidth(d.getKey() + ": " + d.getValue(), Math.max(100, width / 2)));
            }
        if (text.size() == 1) text.add(tr("channel.custom", "自定义信道；由目标结构解释其数值"));
        tooltip = text;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (page == 1) {
            mainField.mouseClicked(mouseX, mouseY, button);
            channelField.mouseClicked(mouseX, mouseY, button);
            valueField.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0) {
            for (int i = controls.size() - 1; i >= 0; i--) {
                Control control = controls.get(i);
                if (control.enabled && control.contains(mouseX, mouseY)) {
                    activate(control.id);
                    return;
                }
            }
            if (page == 0 && inViewport(mouseX, mouseY)) {
                dragging = true;
                moved = false;
                dragX = mouseX;
                dragY = mouseY;
            }
        }
        if (button == 2 && page == 0 && inViewport(mouseX, mouseY)) resetCamera();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (dragging) {
            int dx = mouseX - dragX, dy = mouseY - dragY;
            if (Math.abs(dx) + Math.abs(dy) > 1) moved = true;
            yaw += dx * .012;
            pitch = Math.max(-1.35, Math.min(1.35, pitch + dy * .012));
            dragX = mouseX;
            dragY = mouseY;
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (button == 0 && dragging) {
            if (!moved && hovered >= 0) {
                selected = hovered;
                candidateOffset = 0;
                configure("selected", selected);
            }
            dragging = false;
        }
    }

    private boolean inViewport(int x, int y) {
        return x >= viewportX && x < viewportX + viewportWidth && y >= viewportY && y < viewportY + viewportHeight;
    }

    /** Read-only smoke assertion, after actual drawScreen has established the responsive layout. */
    String smokeLayout() {
        if (viewportWidth <= 0 || viewportHeight <= 0
            || viewportX < 0
            || viewportY < 0
            || viewportX + viewportWidth > width
            || viewportY + viewportHeight > height - 63) {
            throw new IllegalStateException(
                "Invalid viewport " + viewportX + "," + viewportY + "," + viewportWidth + "," + viewportHeight);
        }
        if (controls.isEmpty()) throw new IllegalStateException("Screen has not rendered controls");
        for (Control c : controls) if (c.x < 0 || c.y < 0 || c.x + c.w > width || c.y + c.h > height)
            throw new IllegalStateException("Control outside screen: " + c.id);
        return "gui=" + width
            + "x"
            + height
            + " viewport="
            + viewportWidth
            + "x"
            + viewportHeight
            + " cells="
            + state.cells.size()
            + " controls="
            + controls.size();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = Mouse.getEventX() * width / mc.displayWidth,
            y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (page == 0 && inViewport(x, y)) zoom = Math.max(.25, Math.min(4, zoom * (wheel > 0 ? 1.1 : 1 / 1.1)));
        if (page == 1 || page == 2) {
            int count = page == 1 ? channelNames.size() : state.materials.size();
            listOffset = Math.max(0, Math.min(Math.max(0, count - 1), listOffset + (wheel > 0 ? -1 : 1)));
        }
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (page == 1) {
            if (key == Keyboard.KEY_TAB) {
                if (channelField.isFocused()) {
                    channelField.setFocused(false);
                    valueField.setFocused(true);
                } else {
                    mainField.setFocused(false);
                    valueField.setFocused(false);
                    channelField.setFocused(true);
                }
                return;
            }
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                activate(mainField.isFocused() ? 30 : 31);
                return;
            }
            mainField.textboxKeyTyped(character, key);
            channelField.textboxKeyTyped(character, key);
            valueField.textboxKeyTyped(character, key);
        }
    }

    private void activate(int id) {
        notice = "";
        if (id >= 100 && id <= 103) {
            page = id - 100;
            listOffset = 0;
            return;
        }
        if (id >= 3000) {
            String name = channelNames.get(id - 3000);
            NBTTagCompound config = configuration();
            config.getCompoundTag("channels")
                .removeTag(name);
            send("configure", config);
            return;
        }
        if (id >= 2000) {
            channelField.setText(channelNames.get(id - 2000));
            channelField.setFocused(true);
            return;
        }
        if (id >= 1000) {
            NBTTagCompound action = new NBTTagCompound();
            action.setInteger("selected", selected);
            action.setInteger("choiceindex", id - 1000);
            send("pin", action);
            return;
        }
        switch (id) {
            case 10:
                view = (view + 1) % 3;
                break;
            case 11:
                allLayers = !allLayers;
                layer = state.maxY;
                configure("layer", layer);
                break;
            case 12:
                allLayers = false;
                layer--;
                configure("layer", layer);
                break;
            case 13:
                allLayers = false;
                layer++;
                configure("layer", layer);
                break;
            case 14:
                resetCamera();
                break;
            case 15:
                HologramClient.worldPreview = !HologramClient.worldPreview;
                break;
            case 16:
                configure("mode", (state.data.getInteger("mode") + 1) % 3);
                break;
            case 17:
                configure("scope", (state.data.getInteger("scope") + 1) % 3);
                break;
            case 18:
                int[] facings = state.data.getIntArray("facings");
                if (facings.length > 0) {
                    int next = 0;
                    for (int i = 0; i < facings.length; i++)
                        if (facings[i] == state.data.getInteger("facing")) next = (i + 1) % facings.length;
                    configure("facing", facings[next]);
                }
                break;
            case 20:
                if (rehearsal < 0) rehearsal = 0;
                rehearsalPlaying = !rehearsalPlaying;
                break;
            case 21:
                rehearsal = 0;
                rehearsalPlaying = true;
                break;
            case 22:
                rehearsal = -1;
                rehearsalPlaying = false;
                break;
            case 23:
                send("scan", null);
                break;
            case 24:
                candidateOffset = Math.max(0, candidateOffset - Math.max(1, (height - 218) / 25));
                break;
            case 25:
                candidateOffset += Math.max(1, (height - 218) / 25);
                break;
            case 26:
                page = 4;
                candidateOffset = 0;
                break;
            case 27:
                page = 0;
                break;
            case 30:
                Integer main = positive(mainField.getText());
                if (main != null) configure("main", main);
                break;
            case 31:
                String name = channelField.getText()
                    .trim();
                if (name.isEmpty() || !name.equals(name.toLowerCase(Locale.ROOT)) || !name.matches("[a-z0-9_.-]+")) {
                    notice = tr("channel.invalid", "信道名须为小写字母、数字、下划线、点或连字符");
                    break;
                }
                Integer value = positive(valueField.getText());
                if (value != null) {
                    NBTTagCompound config = configuration();
                    config.getCompoundTag("channels")
                        .setInteger(name, value);
                    send("configure", config);
                }
                break;
            case 32:
                channelField.setText("coil");
                valueField.setFocused(true);
                break;
            case 33:
                NBTTagCompound config = configuration();
                config.setBoolean("noHatches", !state.data.getBoolean("noHatches"));
                send("configure", config);
                break;
            case 40:
                send("hints", null);
                break;
            case 41:
                send("native", null);
                break;
            case 50:
                send("start", null);
                break;
            case 51:
                send(state.data.getInteger("job") == 2 ? "resume" : "pause", null);
                break;
            case 52:
                send("cancel", null);
                break;
            case 53:
                mc.displayGuiScreen(null);
                break;
            default:
                break;
        }
    }

    private Integer positive(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value > 0) return value;
        } catch (NumberFormatException ignored) {}
        notice = tr("value.invalid", "请输入大于零的整数");
        return null;
    }

    private void resetCamera() {
        yaw = Math.PI / 4;
        pitch = Math.PI / 6;
        zoom = 1;
    }

    private NBTTagCompound configuration() {
        NBTTagCompound n = new NBTTagCompound();
        for (String key : new String[] { "main", "facing", "mode", "scope" })
            n.setInteger(key, state.data.getInteger(key));
        n.setInteger("layer", layer);
        n.setInteger("selected", selected);
        n.setBoolean("noHatches", state.data.getBoolean("noHatches"));
        n.setTag(
            "channels",
            state.data.getCompoundTag("channels")
                .copy());
        return n;
    }

    private void configure(String key, int value) {
        if (!idle()) return;
        NBTTagCompound n = configuration();
        n.setInteger(key, value);
        send("configure", n);
    }

    private void send(String op, NBTTagCompound action) {
        if (action == null) action = new NBTTagCompound();
        action.setString("session", state.session);
        action.setString("op", op);
        HologramNetwork.sendAction(action);
    }

    private static final class Control {

        final int id, x, y, w, h;
        final boolean enabled;

        Control(int id, int x, int y, int w, int h, String label, boolean enabled) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.enabled = enabled;
        }

        boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
}
