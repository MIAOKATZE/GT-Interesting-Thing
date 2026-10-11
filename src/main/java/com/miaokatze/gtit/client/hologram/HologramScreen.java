package com.miaokatze.gtit.client.hologram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.miaokatze.gtit.hologram.HologramNetwork;
import com.miaokatze.gtit.hologram.HologramRecovery;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.common.blocks.ItemMachines;

/** Workstation for choosing targets and executing one immediate server-validated operation. */
public final class HologramScreen extends GuiScreen {

    private static final int DESIGN_W = 416, DESIGN_H = 288;
    private final List<Control> controls = new ArrayList<>();
    private final RenderItem itemRenderer = new RenderItem();
    private HologramState state;
    private GuiTextField mainField, channelNameField, channelValueField, optionValueField;
    private GuiTextField positionXField, positionYField, positionZField;
    private boolean positionDrawer, sourceDrawer;
    private boolean hatchDrawer, presetDrawer;
    private int hatchOffset, hatchRole, hatchFilterTier = -1, presetTier = 1;
    private boolean presetDowngrade;
    private int selected = -1, layer, view, hovered = -1, drawerOffset, materialOffset;
    private int viewportX = 12, viewportY = 64, viewportWidth = 142, viewportHeight = 136;
    private int dragX, dragY, originX, originY;
    private double yaw = Math.PI / 4, pitch = Math.PI / 6, zoom = 1;
    private float scale = 1;
    private boolean allLayers = true, dragging, moved, drawer, confirmation, inventory;
    private int inventorySlot = -1, sourceChoice = -1, sourceOffset;
    private boolean sourceTargets;
    private boolean inventoryDragging, awaitingScan;
    private String optionChannel = "", notice = "";
    private long sequence, pendingSequence = -1, confirmedRevision = -1, renderGeneration;
    private List<String> tooltip;

    HologramScreen(HologramState state) {
        this.state = state;
        selected = state.data.hasKey("selected") ? state.data.getInteger("selected") : -1;
        layer = state.maxY;
        sequence = state.data.getLong("uiSequence");
        resetCamera();
    }

    void updateState(HologramState next) {
        if (!state.session.equals(next.session)) pendingSequence = -1;
        state = next;
        if (pendingSequence >= 0 && next.data.getLong("uiSequence") >= pendingSequence) pendingSequence = -1;
        if (confirmation && confirmedRevision != revision()) confirmation = false;
        if (allLayers) layer = next.maxY;
        selected = next.data.hasKey("selected") ? next.data.getInteger("selected") : -1;
        if (mainField != null && !mainField.isFocused())
            mainField.setText(Integer.toString(next.data.getInteger("main")));
        if (awaitingScan && pendingSequence < 0 && !next.data.getBoolean("capturePending")) {
            awaitingScan = false;
            if (next.data.getBoolean("supported")) {
                confirmedRevision = revision();
                materialOffset = 0;
                confirmation = true;
            }
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        scale = Math.min(1F, Math.min((width - 8F) / DESIGN_W, (height - 8F) / DESIGN_H));
        originX = (width - Math.round(DESIGN_W * scale)) / 2;
        originY = (height - Math.round(DESIGN_H * scale)) / 2;
        mainField = new GuiTextField(fontRendererObj, 271, 213, 55, 16);
        mainField.setMaxStringLength(10);
        mainField.setText(Integer.toString(state.data.getInteger("main")));
        channelNameField = new GuiTextField(fontRendererObj, 165, 190, 150, 16);
        channelNameField.setMaxStringLength(48);
        channelValueField = new GuiTextField(fontRendererObj, 321, 190, 75, 16);
        channelValueField.setMaxStringLength(10);
        channelValueField.setText("1");
        optionValueField = new GuiTextField(fontRendererObj, 168, 232, 137, 16);
        optionValueField.setMaxStringLength(10);
        positionXField = positionField(166);
        positionYField = positionField(246);
        positionZField = positionField(326);
    }

    private GuiTextField positionField(int x) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, 116, 70, 18);
        field.setMaxStringLength(4);
        field.setText("0");
        return field;
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
        if (mainField != null) mainField.updateCursorCounter();
        if (channelNameField != null) channelNameField.updateCursorCounter();
        if (channelValueField != null) channelValueField.updateCursorCounter();
        if (optionValueField != null) optionValueField.updateCursorCounter();
        if (positionXField != null) {
            positionXField.updateCursorCounter();
            positionYField.updateCursorCounter();
            positionZField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float ticks) {
        drawDefaultBackground();
        int mx = localX(mouseX), my = localY(mouseY);
        controls.clear();
        tooltip = null;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPushMatrix();
        GL11.glTranslatef(originX, originY, 0);
        GL11.glScalef(scale, scale, 1);
        try {
            skin("cloth", 0, 0, DESIGN_W, DESIGN_H);
            drawRect(6, 29, 410, 280, 0xed142a32);
            skin("panel", 6, 6, 404, 22);
            text("猫猫全息投影仪 · " + state.data.getString("title"), 13, 13, 270, 0xf5e4b9);
            button(60, 287, 9, 117, 17, "等级 / 信道设置", true, mx, my);
            String[] modes = { "补建", "替换", "拆除" };
            for (int i = 0; i < 3; i++) button(
                70 + i,
                12 + i * 131,
                34,
                128,
                21,
                (state.data.getInteger("mode") == i ? "› " : "") + modes[i],
                editable(),
                mx,
                my);
            if (!confirmation) drawPreview(mx, my);
            drawConfig(mx, my);
            drawFooter(mx, my);
            if (drawer || inventory || positionDrawer || sourceDrawer || !optionChannel.isEmpty()) {
                controls.removeIf(control -> control.x >= 157);
                tooltip = null;
                GL11.glDepthMask(true);
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            if (drawer) {
                drawChannels(mx, my);
            }
            if (inventory) drawInventory(mx, my);
            if (positionDrawer) drawPosition(mx, my);
            if (sourceDrawer) drawSources(mx, my);
            if (!optionChannel.isEmpty()) {
                controls.clear();
                drawOptions(mx, my);
            }
            if (hatchDrawer || presetDrawer) {
                controls.clear();
                tooltip = null;
                GL11.glDepthMask(true);
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                if (hatchDrawer) drawHatches(mx, my);
                else drawPreset(mx, my);
            }
            if (confirmation) {
                controls.clear();
                tooltip = null;
                GL11.glDepthMask(true);
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                drawConfirmation(mx, my);
            }
            if (!confirmation) {
                String status = pendingSequence >= 0 ? "等待服务端校验…" : state.data.getString("status");
                text(notice.isEmpty() ? status : notice, 12, 279, 388, notice.isEmpty() ? 0xffbd91 : 0xff9292);
                if (mx >= 12 && mx < 400 && my >= 279)
                    tooltip = fontRendererObj.listFormattedStringToWidth(notice.isEmpty() ? status : notice, 380);
            }
            if (tooltip != null) drawHoveringText(tooltip, mx, my, fontRendererObj);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        renderGeneration++;
    }

    private void drawPreview(int mx, int my) {
        HologramRenderer.showEmptyCells = inventory || positionDrawer;
        HologramRenderer.projectionLayer = allLayers ? Integer.MAX_VALUE : layer;
        drawRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, 0xff0c1e28);
        int factor = new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
            .getScaleFactor();
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            Math.round((originX + viewportX * scale) * factor),
            mc.displayHeight - Math.round((originY + (viewportY + viewportHeight) * scale) * factor),
            Math.round(viewportWidth * scale * factor),
            Math.round(viewportHeight * scale * factor));
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
                mx,
                my,
                -1);
        } finally {
            GL11.glPopAttrib();
        }
        if (state.cells.isEmpty()) text("右键控制器选择目标", 20, 125, 128, 0xa6c5c7);
        button(10, 12, 204, 44, 17, new String[] { "目标", "现场", "差分" }[view], true, mx, my);
        button(11, 58, 204, 50, 17, allLayers ? "全部层" : "层 " + layer, true, mx, my);
        button(12, 110, 204, 20, 17, "−", layer > state.minY, mx, my);
        button(13, 132, 204, 22, 17, "+", layer < state.maxY, mx, my);
        text("拖动旋转 · 右键取消选中", 14, 226, 140, 0x94afb4);
        button(14, 12, 239, 66, 17, "复位视角", true, mx, my);
        button(15, 80, 239, 74, 17, HologramClient.worldPreview ? "世界投影 ✓" : "世界投影 ×", true, mx, my);
    }

    private void drawConfig(int mx, int my) {
        NBTTagCompound primary = primaryCapability();
        text(primary == null ? "当前结构参数" : primary.getString("label"), 166, 64, 234, 0xe4d3a9);
        if (primary != null) {
            String channel = primary.getString("id");
            button(80, 166, 77, 23, 21, "−", editable() && canStep(primary, -1), mx, my);
            button(81, 192, 77, 180, 21, optionLabel(primary), editable(), mx, my);
            button(82, 375, 77, 25, 21, "+", editable() && canStep(primary, 1), mx, my);
            if (!drawer && !inventory
                && !positionDrawer
                && !confirmation
                && mx >= 166
                && mx < 400
                && my >= 77
                && my < 98) tooltip = Collections.singletonList(channel + " · " + optionLabel(primary));
        } else text("无已验证的等级参数 · 高级查看", 166, 82, 234, 0x94afb4);
        button(75, 166, 104, 74, 18, "添加位置", editable(), mx, my);
        button(78, 244, 104, 74, 18, "材料来源", editable(), mx, my);
        button(94, 322, 104, 78, 18, "仓室预设", editable() && state.data.getInteger("mode") != 2, mx, my);
        button(
            95,
            166,
            125,
            113,
            15,
            "本格候选 / 朝向",
            editable() && selectedCell() != null && state.data.getInteger("mode") != 2,
            mx,
            my);
        text(
            state.data.getInteger("mode") == 2 ? "保留控制器"
                : state.data.getBoolean("creativeMaterials") ? "创造：普通免材料" : "只改目标，再施工",
            286,
            129,
            114,
            0x94afb4);
        String[] scopes = { "全结构", "当前层", "选中格", "同类部件" };
        for (int i = 0; i < 4; i++) button(
            90 + i,
            166 + i * 59,
            143,
            56,
            18,
            (state.data.getInteger("scope") == i ? "›" : "") + scopes[i],
            editable() && (i < 2 || selectedCell() != null && (i != 3 || !selectedCell().family.isEmpty())),
            mx,
            my);
        HologramState.Cell cell = selectedCell();
        if (cell == null) {
            text("点击预览选位，或添加任意坐标", 166, 171, 234, 0xb9d5d4);
            text("选择目标后点击底部按钮执行", 166, 185, 234, 0x94afb4);
        } else {
            drawItem(displayedStack(cell, true), 166, 166);
            drawItem(displayedStack(cell, false), 186, 166);
            text(cellName(cell, true) + " → " + cellName(cell, false), 207, 169, 193, cell.color());
            NBTTagCompound row = cellTag();
            text(
                "(" + cell.dx
                    + ","
                    + cell.dy
                    + ","
                    + cell.dz
                    + ") "
                    + row.getString("role")
                    + " / "
                    + row.getString("family"),
                166,
                184,
                234,
                0xb9d5d4);
            String reason = row.getString("reason");
            text(reason.isEmpty() ? statusName(cell.status) : reason, 166, 199, 234, cell.color());
            if (mx >= 166 && my >= 169 && mx < 400 && my < 213) tooltip = fontRendererObj.listFormattedStringToWidth(
                cellName(cell, true) + " → "
                    + cellName(cell, false)
                    + "\n"
                    + (reason.isEmpty() ? statusName(cell.status) : reason),
                230);
        }
        button(66, 166, 214, 113, 18, "清除本格目标", editable() && hasSelectedPin(), mx, my);
        button(69, 282, 214, 118, 18, "取消格选择", selectedCell() != null && editable(), mx, my);
        button(26, 166, 235, 113, 18, "材料与改动", editable() && state.data.getBoolean("supported"), mx, my);
        button(61, 282, 235, 118, 18, "选择方块 / 仓室", editable() && state.data.getInteger("mode") != 2, mx, my);
        if (mx >= 166 && mx < 400 && my >= 235 && my < 253 && tooltip == null)
            tooltip = Collections.singletonList(state.data.getString("status") + " · 阶段 " + phaseName());
    }

    private void drawFooter(int mx, int my) {
        int job = state.data.getInteger("job");
        text(
            "材料缺口 " + state.data.getInteger("missing")
                + " · 保护 "
                + state.data.getInteger("protected")
                + " · "
                + state.data.getInteger("completed")
                + "/"
                + state.data.getInteger("workTotal"),
            12,
            264,
            233,
            0xcce5d8);
        button(
            50,
            248,
            261,
            87,
            19,
            new String[] { "直接构建", "应用替换", "拆除结构" }[Math.floorMod(state.data.getInteger("mode"), 3)],
            editable() && state.data.getBoolean("target")
                && (state.data.getInteger("scope") < 2 || selectedCell() != null)
                && (state.data.getInteger("scope") != 3 || selectedCell() != null && !selectedCell().family.isEmpty())
                && (state.data.getBoolean("supported") || state.data.getInteger("mode") == 0),
            mx,
            my);
        button(51, 338, 261, 30, 19, job == 2 ? "继续" : "暂停", pendingSequence < 0 && (job == 1 || job == 2), mx, my);
        button(
            52,
            371,
            261,
            30,
            19,
            "取消",
            pendingSequence < 0 && (job == 1 || job == 2 || state.data.getBoolean("capturePending")),
            mx,
            my);
    }

    private void drawOptions(int mx, int my) {
        NBTTagCompound cap = capability(optionChannel);
        if (cap == null) {
            optionChannel = "";
            return;
        }
        drawRect(160, 59, 405, 257, 0xff172f36);
        text(cap.getString("label") + " · 选择目标", 168, 65, 203, 0xf5e4b9);
        button(63, 375, 62, 23, 17, "×", true, mx, my);
        NBTTagList options = cap.getTagList("options", 10);
        for (int i = drawerOffset; i < options.tagCount() && i < drawerOffset + 7; i++) {
            NBTTagCompound option = options.getCompoundTagAt(i);
            boolean chosen = option.getBoolean("present")
                ? cap.getBoolean("explicit") && option.getInteger("value") == cap.getInteger("current")
                : !cap.getBoolean("explicit");
            int y = 86 + (i - drawerOffset) * 18;
            button(4000 + i, 168, y, 228, 17, (chosen ? "✓ " : "") + option.getString("label"), editable(), mx, my);
            if (mx >= 168 && mx < 396 && my >= y && my < y + 17) tooltip = Collections.singletonList(
                option.getString("label") + " · "
                    + (option.getBoolean("present") ? "显式目标，原始值 " + option.getInteger("value") : "恢复默认值")
                    + (editable() ? "；仅更新目标。" : "；" + editingReason()));
        }
        if (!"_hatch_candidate".equals(optionChannel)) {
            text("原始正整数（可指定列表之外的值）", 168, 215, 228, 0x94afb4);
            optionValueField.drawTextBox();
            button(68, 311, 232, 85, 16, "应用原始值", editable(), mx, my);
        }
    }

    private void drawChannels(int mx, int my) {
        tooltip = null;
        drawRect(157, 59, 405, 257, 0xff172f36);
        text("等级 / 信道设置（滚轮查看全部）", 165, 65, 205, 0xf5e4b9);
        button(62, 373, 62, 23, 17, "×", true, mx, my);
        NBTTagList caps = capabilities();
        for (int i = drawerOffset; i < caps.tagCount() && i < drawerOffset + 4; i++) {
            NBTTagCompound cap = caps.getCompoundTagAt(i);
            int y = 86 + (i - drawerOffset) * 22;
            String source = cap.getString("id")
                .equals("main") ? "主值"
                    : cap.getBoolean("explicit") ? "指定" : "presence".equals(cap.getString("kind")) ? "默认" : "继承";
            button(
                2000 + i,
                165,
                y,
                231,
                20,
                cap.getString("label") + "：" + optionLabel(cap) + "（" + source + "）",
                editable() && cap.getBoolean("editable"),
                mx,
                my);
            if (mx >= 165 && mx < 396 && my >= y && my < y + 20) {
                tooltip = new ArrayList<>();
                tooltip.add(cap.getString("label") + " / " + cap.getString("id"));
                tooltip.add("当前目标：" + optionLabel(cap) + " · 原始值 " + cap.getInteger("current"));
                tooltip.add(
                    cap.getString("id")
                        .equals("main") ? "主原始信号：未独立指定的子信道继承此值。"
                            : cap.getBoolean("explicit") ? "已显式指定；选择继承项可恢复默认。" : "未指定：使用主信号或机器默认规则。");
                tooltip.add("具名目标或原始正整数均可设置；机器是否使用信道由自身规则决定。");
                if (!editable()) tooltip.add(editingReason());
            }
        }
        text("手填 name（小写） / 正整数 value", 165, 178, 231, 0x94afb4);
        channelNameField.drawTextBox();
        channelValueField.drawTextBox();
        button(67, 165, 213, 98, 16, "添加 / 更新", editable(), mx, my);
        mainField.drawTextBox();
        button(30, 330, 213, 66, 16, "应用主值", editable(), mx, my);
        button(23, 165, 234, 74, 16, "重新采集", editable(), mx, my);
        button(
            33,
            242,
            234,
            74,
            16,
            state.data.getBoolean("noHatches") ? "只放外壳 ✓" : "允许仓室",
            editable() && state.data.getInteger("mode") != 2,
            mx,
            my);
        NBTTagCompound hatch = capability("gt_hatch");
        button(
            83,
            319,
            234,
            77,
            16,
            hatch != null && hatch.getBoolean("explicit") ? "自动选材" : "仓室手选",
            editable() && state.data.getInteger("mode") != 2 && hatch != null && hatch.getBoolean("editable"),
            mx,
            my);

    }

    private void drawInventory(int mx, int my) {
        tooltip = null;
        drawRect(157, 59, 405, 257, 0xff172f36);
        text("选择方块 / 仓室 · 不消耗物品", 165, 65, 205, 0xf5e4b9);
        button(16, 165, 82, 74, 18, (sourceTargets ? "" : "› ") + "主背包", true, mx, my);
        button(17, 243, 82, 74, 18, (sourceTargets ? "› " : "") + "材料来源", true, mx, my);
        button(95, 321, 82, 75, 18, "仓室候选", selectedCell() != null && !selectedCell().candidates.isEmpty(), mx, my);
        button(64, 373, 62, 23, 17, "×", true, mx, my);
        HologramState.Cell cell = selectedCell();
        button(
            65,
            165,
            104,
            231,
            18,
            cell == null ? "先点击左侧预览选择目标格" : "目标格：" + cellName(cell, false),
            editable() && canInventoryPin() && pickedTarget() != null,
            mx,
            my);
        text("点击物品再点目标槽，或拖到左侧预览格", 165, 126, 231, 0x94afb4);
        NBTTagList choices = state.data.getTagList("materialChoices", 10);
        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) {
            int slot = row == 3 ? col : 9 + row * 9 + col;
            int choice = sourceOffset + row * 9 + col;
            int x = 181 + col * 22, y = 139 + row * 20;
            boolean chosen = sourceTargets ? sourceChoice == choice : inventorySlot == slot;
            drawRect(x, y, x + 20, y + 18, chosen ? 0xff5f858a : 0xff0c1e28);
            ItemStack stack = sourceTargets ? sourceStack(choice) : mc.thePlayer.inventory.mainInventory[slot];
            drawItem(stack, x + 2, y + 1);
            if (mx >= x && mx < x + 20 && my >= y && my < y + 18 && stack != null)
                tooltip = Collections.singletonList(stack.getDisplayName() + " · 选为目标（不消耗）");
        }
        if (sourceTargets && choices.tagCount() == 0) {
            text("开启来源并准备物品", 183, 160, 194, 0xe4d3a9);
            text("然后重新扫描或重新打开", 183, 180, 194, 0xe4d3a9);
        }
        button(66, 165, 221, 105, 17, "清除本格目标", editable() && hasSelectedPin(), mx, my);
        ItemStack picked = pickedTarget();
        if (sourceTargets) button(18, 277, 221, 119, 17, "刷新来源 · 滚轮翻页", editable(), mx, my);
        else text(picked == null ? "未选物品" : picked.getDisplayName(), 277, 224, 119, 0xe4d3a9);
        text(pendingSequence >= 0 ? "等待服务端校验目标…" : state.data.getString("status"), 165, 243, 231, 0xffbd91);
        if (mx >= 165 && mx < 396 && my >= 240 && my < 255) {
            tooltip = new ArrayList<>(fontRendererObj.listFormattedStringToWidth(state.data.getString("status"), 230));
            if (pendingSequence >= 0) tooltip.add("等待服务端校验目标…");
            if (cell != null) {
                tooltip.add("选中格 #" + cell.index + " · 世界坐标 " + cell.x + "," + cell.y + "," + cell.z);
                tooltip.addAll(fontRendererObj.listFormattedStringToWidth("当前目标：" + cellName(cell, false), 230));
                tooltip.add(cell.wantId + " @ " + cell.wantMeta);
            }
            if (picked != null) tooltip.addAll(
                fontRendererObj
                    .listFormattedStringToWidth((sourceTargets ? "来源选择：" : "背包选择：") + picked.getDisplayName(), 230));
        }
        if (picked != null && inventoryDragging) drawItem(picked, mx - 8, my - 8);
    }

    private boolean sourceEnabled(String key) {
        return !state.data.hasKey(key) || state.data.getBoolean(key);
    }

    private List<String> hatchRoles() {
        List<String> roles = new ArrayList<>();
        roles.add("全部类型");
        NBTTagList tags = cellTag().getTagList("candidateRoles", 8);
        for (int i = 0; i < tags.tagCount(); i++) {
            String role = tags.getStringTagAt(i);
            if (!role.isEmpty() && !roles.contains(role)) roles.add(role);
        }
        return roles;
    }

    private List<Integer> filteredHatches() {
        List<Integer> indices = new ArrayList<>();
        HologramState.Cell cell = selectedCell();
        if (cell == null) return indices;
        List<String> roles = hatchRoles();
        hatchRole = Math.floorMod(hatchRole, roles.size());
        NBTTagList tags = cellTag().getTagList("candidateRoles", 8);
        int[] tiers = cellTag().getIntArray("candidateTiers");
        for (int i = 0; i < cell.candidates.size(); i++) {
            if (hatchRole > 0 && !roles.get(hatchRole)
                .equals(tags.getStringTagAt(i))) continue;
            if (hatchFilterTier >= 0 && (i >= tiers.length || tiers[i] != hatchFilterTier)) continue;
            indices.add(i);
        }
        return indices;
    }

    private String tierName(int tier) {
        return tier >= 0 && tier < GTValues.VN.length ? GTValues.VN[tier] : "非电压部件";
    }

    private String directionName(int facing) {
        String[] names = { "下", "上", "北", "南", "西", "东" };
        return facing >= 0 && facing < names.length ? names[facing] : "默认";
    }

    private void drawHatches(int mx, int my) {
        drawRect(157, 59, 405, 257, 0xff172f36);
        text("本格仓室候选 · 类型 / 等级", 165, 65, 205, 0xf5e4b9);
        button(96, 373, 62, 23, 17, "×", true, mx, my);
        List<String> roles = hatchRoles();
        hatchRole = Math.floorMod(hatchRole, roles.size());
        button(97, 165, 84, 139, 18, roles.get(hatchRole), true, mx, my);
        button(98, 308, 84, 88, 18, hatchFilterTier < 0 ? "全部等级" : tierName(hatchFilterTier), true, mx, my);
        List<Integer> indices = filteredHatches();
        hatchOffset = Math.max(0, Math.min(hatchOffset, Math.max(0, indices.size() - 5)));
        int[] available = cellTag().getIntArray("candidateAvailable");
        int[] tiers = cellTag().getIntArray("candidateTiers");
        for (int row = 0; row < 5 && hatchOffset + row < indices.size(); row++) {
            int index = indices.get(hatchOffset + row);
            ItemStack stack = selectedCell().candidates.get(index);
            int y = 107 + row * 21;
            drawItem(stack, 165, y);
            String count = state.data.getBoolean("creativeMaterials") ? "无限"
                : index < available.length ? (available[index] >= 4096 ? "≥4096" : Integer.toString(available[index]))
                    : "?";
            button(
                10000 + index,
                186,
                y,
                210,
                19,
                stack.getDisplayName() + " ×" + count,
                editable() && canInventoryPin(),
                mx,
                my);
            if (mx >= 165 && mx < 396 && my >= y && my < y + 19) {
                tooltip = new ArrayList<>();
                tooltip.add(stack.getDisplayName());
                tooltip.add((index < tiers.length ? tierName(tiers[index]) : "固定等级") + " · 已启用来源可用 " + count);
                tooltip.add("仅指定本格目标；施工时重新核对材料。");
                if (!editable()) tooltip.add(editingReason());
            }
        }
        if (indices.isEmpty()) text("本筛选没有候选，请更换类型或等级", 165, 125, 231, 0xe4b85a);
        text("滚轮翻页 · " + indices.size() + " 项", 165, 216, 115, 0x94afb4);
        button(66, 282, 213, 114, 18, "清除本格目标", editable() && hasSelectedPin(), mx, my);
        int[] facings = cellTag().getIntArray("hatchFacings");
        button(
            99,
            165,
            235,
            139,
            18,
            facings.length == 0 ? "仓室朝向不可用" : "仓室朝向：" + directionName(cellTag().getInteger("hatchFacing")),
            editable() && facings.length > 0,
            mx,
            my);
        button(102, 308, 235, 88, 18, "恢复朝向", editable() && cellTag().getBoolean("hasFacingPin"), mx, my);
    }

    private void drawPreset(int mx, int my) {
        drawRect(157, 59, 405, 257, 0xff172f36);
        text("声明仓室一键预设", 165, 65, 205, 0xf5e4b9);
        button(96, 373, 62, 23, 17, "×", true, mx, my);
        text("按结构声明配额配置空位，不改已有仓室", 165, 90, 231, 0xb9d5d4);
        button(103, 165, 113, 28, 21, "−", presetTier > 0, mx, my);
        text("仓室等级：" + tierName(presetTier), 201, 120, 153, 0xe4d3a9);
        button(104, 368, 113, 28, 21, "+", presetTier < state.data.getInteger("hatchTierMax"), mx, my);
        button(105, 165, 143, 231, 21, presetDowngrade ? "缺料时允许降级 ✓" : "缺料时严格保持等级", true, mx, my);
        String reason = state.data.getString("presetReason");
        List<String> lines = fontRendererObj
            .listFormattedStringToWidth(reason.isEmpty() ? "生成目标后检查差分，再点击底部施工按钮。" : reason, 230);
        for (int i = 0; i < Math.min(4, lines.size()); i++) text(lines.get(i), 165, 174 + i * 12, 231, 0xe4b85a);
        button(
            106,
            165,
            232,
            142,
            20,
            "生成预设目标",
            editable() && state.data.getBoolean("supported") && state.data.getBoolean("presetAvailable"),
            mx,
            my);
        button(107, 312, 232, 84, 20, "撤销预设", editable() && state.data.getBoolean("presetActive"), mx, my);
    }

    private void drawSources(int mx, int my) {
        tooltip = null;
        drawRect(157, 59, 405, 257, 0xff172f36);
        text("材料来源", 166, 67, 200, 0xf5e4b9);
        button(88, 375, 62, 23, 17, "×", true, mx, my);
        String[] keys = { "materialMain", "materialContainers", "materialMe" };
        String[] labels = { "主背包", "背包内物品容器", "携带的 ME 无线终端" };
        for (int i = 0; i < keys.length; i++) button(
            84 + i,
            166,
            94 + i * 25,
            230,
            21,
            labels[i] + (sourceEnabled(keys[i]) ? " ✓" : " ×"),
            editable(),
            mx,
            my);
        int priority = Math.floorMod(state.data.getInteger("materialPriority"), 3);
        button(87, 166, 175, 230, 21, "优先：" + new String[] { "主背包", "物品容器", "ME 终端" }[priority], editable(), mx, my);
        text(new String[] { "主背包 → 容器 → ME", "容器 → 主背包 → ME", "ME → 主背包 → 容器" }[priority], 166, 209, 230, 0xb9d5d4);
        text("仅使用已开启的来源（包括饰品终端）", 166, 226, 230, 0x94afb4);
        text("已绑定、有电、在范围内；遵守网络权限", 166, 241, 230, 0x94afb4);
    }

    private void drawPosition(int mx, int my) {
        tooltip = null;
        drawRect(157, 59, 405, 257, 0xff172f36);
        text("添加位置 · 相对控制器", 166, 67, 228, 0xf5e4b9);
        button(77, 375, 62, 23, 17, "×", true, mx, my);
        text("世界轴偏移 X / Y / Z（−64～64）", 166, 91, 230, 0x94afb4);
        positionXField.drawTextBox();
        positionYField.drawTextBox();
        positionZField.drawTextBox();
        button(76, 166, 146, 230, 20, "添加并选中此位置", editable(), mx, my);
        text("选中后点击“选择方块 / 仓室”指定目标", 166, 180, 230, 0xb9d5d4);
        text("控制器本身受保护，最多4096个位置", 166, 199, 230, 0x94afb4);
    }

    private void addPosition() {
        if (!editable()) return;
        try {
            int dx = Integer.parseInt(
                positionXField.getText()
                    .trim());
            int dy = Integer.parseInt(
                positionYField.getText()
                    .trim());
            int dz = Integer.parseInt(
                positionZField.getText()
                    .trim());
            if (Math.abs((long) dx) > 64 || Math.abs((long) dy) > 64 || Math.abs((long) dz) > 64)
                throw new NumberFormatException();
            NBTTagCompound action = new NBTTagCompound();
            action.setInteger("dx", dx);
            action.setInteger("dy", dy);
            action.setInteger("dz", dz);
            positionDrawer = false;
            send("addPosition", action, true);
        } catch (NumberFormatException invalid) {
            notice = "坐标须为−64～64的整数";
        }
    }

    private ItemStack sourceStack(int choice) {
        NBTTagList choices = state.data.getTagList("materialChoices", 10);
        return choice >= 0 && choice < choices.tagCount()
            ? ItemStack.loadItemStackFromNBT(choices.getCompoundTagAt(choice))
            : null;
    }

    private ItemStack pickedTarget() {
        return sourceTargets ? sourceStack(sourceChoice)
            : inventorySlot >= 0 ? mc.thePlayer.inventory.mainInventory[inventorySlot] : null;
    }

    private int inventorySlotAt(int x, int y) {
        int col = (x - 181) / 22, row = (y - 139) / 20;
        if (x < 181 || y < 139 || col >= 9 || row >= 4 || (x - 181) % 22 >= 20 || (y - 139) % 20 >= 18) return -1;
        return sourceTargets ? sourceOffset + row * 9 + col : row == 3 ? col : 9 + row * 9 + col;
    }

    private void pinInventory(int target) {
        if (!editable() || target < 0 || pickedTarget() == null) return;
        selected = target;
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("selected", target);
        n.setInteger("index", target);
        if (sourceTargets) n.setInteger("choice", sourceChoice);
        else n.setInteger("inventorySlot", inventorySlot);
        n.setInteger("scope", 2);
        send(sourceTargets ? "sourcePin" : "customPin", n, true);
    }

    private boolean canInventoryPin() {
        HologramState.Cell cell = selectedCell();
        return cell != null && !cell.anchor && state.data.getInteger("mode") != 2;
    }

    private boolean hasSelectedPin() {
        return selectedCell() != null && cellTag().getBoolean("hasTargetOverride");
    }

    private void clearSelection() {
        if (!editable() || selected < 0) return;
        selected = -1;
        send("configure", configuration(), true);
    }

    private boolean previewInteractive() {
        return !drawer && !positionDrawer
            && !sourceDrawer
            && !hatchDrawer
            && !presetDrawer
            && !confirmation
            && optionChannel.isEmpty();
    }

    private void drawConfirmation(int mx, int my) {
        drawRect(40, 58, 376, 254, 0xff172f36);
        text("材料与改动", 50, 68, 315, 0xf5e4b9);
        int operations = 0, recovery = 0;
        NBTTagList rows = state.data.getTagList("cells", 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            String operation = row.getString("operation");
            if (row.getBoolean("inScope")
                && (operation.equals("PLACE") || operation.equals("REPLACE") || operation.equals("REMOVE"))
                && (row.getString("status")
                    .equals("pending")
                    || row.getString("status")
                        .equals("missing")))
                operations++;
        }
        NBTTagList recovered = state.data.getTagList("recovery", 10);
        for (int i = 0; i < recovered.tagCount(); i++) recovery += recovered.getCompoundTagAt(i)
            .getInteger("expected");
        text(
            "范围 " + new String[] { "全部", "图层", "单格", "同族" }[Math.floorMod(state.data.getInteger("scope"), 4)]
                + " · 差分 "
                + operations
                + " 格 · 保护 "
                + state.data.getInteger("protected"),
            50,
            86,
            315,
            0xcce5d8);
        HologramState.Cell selected = selectedCell();
        if (state.data.getBoolean("creativeMaterials")) text("创造：普通材料无限；封存设备仍消耗真实物品", 50, 100, 315, 0xe4d3a9);
        else if (selected != null)
            text(cellName(selected, true) + " → " + cellName(selected, false), 50, 100, 315, 0xe4d3a9);
        NBTTagList materialRows = state.data.getTagList("materials", 10);
        for (int i = materialOffset; i < Math.min(materialOffset + 3, state.materials.size()); i++) {
            HologramState.Material m = state.materials.get(i);
            int y = 119 + (i - materialOffset) * 27;
            String available = state.data.getBoolean("creativeMaterials") && !HologramRecovery.hasSeal(m.stack) ? "无限"
                : Integer.toString(m.available);
            text(
                (m.stack == null ? "未知材料" : m.stack.getDisplayName()) + " × " + m.required + " / 可用来源 " + available,
                50,
                y,
                315,
                m.available < m.required ? 0xff9292 : 0xa2e8b8);
            NBTTagCompound material = materialRows.getCompoundTagAt(i);
            int controllerAvailable = material.getInteger("controllerAvailable");
            String counts = "主背包 " + material.getInteger("mainAvailable")
                + " · 控制器 "
                + (controllerAvailable < 0 ? "不可访问" : Integer.toString(controllerAvailable))
                + "（仅查看）";
            text(counts, 50, y + 11, 315, 0x94afb4);
            if (mx >= 50 && mx < 365 && my >= y && my < y + 24) {
                tooltip = new ArrayList<>();
                tooltip.add(m.stack == null ? "未知材料" : m.stack.getDisplayName());
                tooltip.add("需求 " + m.required + " · 已启用来源可用 " + available);
                tooltip.add(counts);
                tooltip.add("控制器库存仅查看，不参与本次取料；滚轮查看更多。");
            }
        }
        text("预计回收 " + recovery + " · 不提前抵扣材料", 50, 202, 315, 0x94afb4);
        text(
            notice.isEmpty() ? (state.data.getInteger("mode") == 2 ? "始终保留控制器；拆除所选范围与仓室" : "直接施工，无动画；仓室数据不迁移") : notice,
            50,
            215,
            315,
            0xe4b85a);
        button(54, 50, 232, 146, 17, "返回", true, mx, my);
        button(
            55,
            203,
            232,
            162,
            17,
            "直接执行",
            editable() && operations > 0 && confirmedRevision == revision() && state.data.getInteger("missing") == 0,
            mx,
            my);
    }

    private boolean editable() {
        int job = state.data.getInteger("job");
        return !state.data.getBoolean("targetClosed") && job != 1
            && job != 2
            && pendingSequence < 0
            && !state.data.getBoolean("capturePending");
    }

    private long revision() {
        return state.data.getLong("planRevision");
    }

    private String facingName() {
        int index = state.data.getInteger("facing");
        if (index < 0 || index >= ExtendedFacing.values().length) return "未知";
        ExtendedFacing facing = ExtendedFacing.values()[index];
        String[] names = { "下", "上", "北", "南", "西", "东", "未知" };
        int direction = facing.getDirection()
            .ordinal();
        return names[Math.min(direction, names.length - 1)] + " / "
            + facing.getRotation()
                .ordinal() * 90
            + "°"
            + (facing.getFlip()
                .ordinal() == 0 ? ""
                    : " / 镜像 " + facing.getFlip()
                        .ordinal());
    }

    private NBTTagList capabilities() {
        return state.data.getTagList("capabilities", 10);
    }

    private NBTTagCompound capability(String id) {
        if ("_hatch_candidate".equals(id)) return hatchCandidateCapability();
        NBTTagList list = capabilities();
        for (int i = 0; i < list.tagCount(); i++) if (id.equals(
            list.getCompoundTagAt(i)
                .getString("id")))
            return list.getCompoundTagAt(i);
        return null;
    }

    private NBTTagCompound primaryCapability() {
        HologramState.Cell selected = selectedCell();
        if (selected != null) {
            NBTTagCompound hatch = hatchCandidateCapability();
            if (hatch != null) return hatch;
            if (selected.family.startsWith("glass:")) {
                NBTTagCompound glass = capability("glass");
                if (glass != null && glass.getBoolean("editable")) return glass;
                return null;
            }
            if (selected.family.startsWith("tiered:")) {
                NBTTagList rows = capabilities();
                for (int i = 0; i < rows.tagCount(); i++) {
                    NBTTagCompound row = rows.getCompoundTagAt(i);
                    if (!row.getBoolean("editable") || !row.getString("kind")
                        .equals("material")) continue;
                    NBTTagList options = row.getTagList("options", 10);
                    for (int j = 0; j < options.tagCount(); j++) {
                        ItemStack item = ItemStack.loadItemStackFromNBT(
                            options.getCompoundTagAt(j)
                                .getCompoundTag("item"));
                        if (item == null) continue;
                        net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(item.getItem());
                        String member = net.minecraft.block.Block.blockRegistry.getNameForObject(block) + "@"
                            + item.getItem()
                                .getMetadata(item.getItemDamage())
                            + ";";
                        if (selected.family.substring("tiered:".length())
                            .contains(member)) return row;
                    }
                }
                return null;
            }
        }
        NBTTagCompound coil = capability("coil");
        if (coil != null && coil.getBoolean("editable")) return coil;
        NBTTagList list = capabilities();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound cap = list.getCompoundTagAt(i);
            if (!cap.getString("id")
                .equals("main")
                && !cap.getString("id")
                    .equals("gt_hatch")
                && cap.getBoolean("editable")) return cap;
        }
        return null;
    }

    /** A local selector over server-approved candidates; its values are candidate indices, never channel data. */
    private NBTTagCompound hatchCandidateCapability() {
        HologramState.Cell cell = selectedCell();
        if (cell == null || cell.anchor || state.data.getInteger("mode") == 2) return null;
        ItemStack target = ItemStack.loadItemStackFromNBT(cellTag().getCompoundTag("targetStack"));
        int current = cell.chosenChoice;
        if (current < 0 && target != null) for (int i = 0; i < cell.candidates.size(); i++) {
            if (target.isItemEqual(cell.candidates.get(i))
                && ItemStack.areItemStackTagsEqual(target, cell.candidates.get(i))) {
                current = i;
                break;
            }
        }
        MTEHatch prototype = current >= 0 && current < cell.candidates.size()
            ? hatchPrototype(cell.candidates.get(current))
            : null;
        if (prototype == null) for (int i = 0; i < cell.candidates.size(); i++) {
            prototype = hatchPrototype(cell.candidates.get(i));
            if (prototype != null) {
                current = i;
                break;
            }
        }
        if (prototype == null) return null;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < cell.candidates.size(); i++) {
            MTEHatch candidate = hatchPrototype(cell.candidates.get(i));
            if (candidate != null && candidate.getClass() == prototype.getClass()) indices.add(i);
        }
        indices.sort(
            (a, b) -> Integer
                .compare(hatchPrototype(cell.candidates.get(a)).mTier, hatchPrototype(cell.candidates.get(b)).mTier));
        NBTTagCompound row = new NBTTagCompound();
        row.setString("id", "_hatch_candidate");
        row.setString("kind", "hatchCandidate");
        row.setString("label", "空仓室目标 · 独立设备等级");
        row.setBoolean("editable", true);
        row.setBoolean("explicit", true);
        row.setInteger("current", current);
        NBTTagList options = new NBTTagList();
        for (Integer index : indices) {
            ItemStack stack = cell.candidates.get(index);
            NBTTagCompound option = new NBTTagCompound();
            option.setInteger("value", index);
            option.setBoolean("present", true);
            option.setString("label", stack.getDisplayName() + " · 等级 " + hatchPrototype(stack).mTier);
            option.setTag("item", stack.writeToNBT(new NBTTagCompound()));
            options.appendTag(option);
        }
        row.setTag("options", options);
        return row;
    }

    private static MTEHatch hatchPrototype(ItemStack stack) {
        if (stack == null) return null;
        int id = stack.getItemDamage();
        return stack.getItem() instanceof ItemMachines && id >= 0
            && id < GregTechAPI.METATILEENTITIES.length
            && GregTechAPI.METATILEENTITIES[id] instanceof MTEHatch ? (MTEHatch) GregTechAPI.METATILEENTITIES[id]
                : null;
    }

    private int optionIndex(NBTTagCompound cap) {
        NBTTagList options = cap.getTagList("options", 10);
        for (int i = 0; i < options.tagCount(); i++) {
            NBTTagCompound option = options.getCompoundTagAt(i);
            if (option.getBoolean("present") && option.getInteger("value") == cap.getInteger("current")) return i;
        }
        if (!cap.getBoolean("explicit") && options.tagCount() > 0
            && !options.getCompoundTagAt(0)
                .getBoolean("present"))
            return 0;
        return -1;
    }

    private String optionLabel(NBTTagCompound cap) {
        int index = optionIndex(cap);
        return index < 0 ? "有效值 " + cap.getInteger("current")
            : cap.getTagList("options", 10)
                .getCompoundTagAt(index)
                .getString("label");
    }

    private boolean canStep(NBTTagCompound cap, int delta) {
        int index = optionIndex(cap) + delta;
        return index >= 0 && index < cap.getTagList("options", 10)
            .tagCount()
            && cap.getTagList("options", 10)
                .getCompoundTagAt(index)
                .getBoolean("present");
    }

    private void chooseOption(NBTTagCompound cap, int index) {
        if (cap == null || !cap.getBoolean("editable") || !editable()) return;
        NBTTagList options = cap.getTagList("options", 10);
        if (index < 0 || index >= options.tagCount()) return;
        NBTTagCompound option = options.getCompoundTagAt(index), config = configuration();
        if (cap.getString("kind")
            .equals("hatchCandidate")) {
            config = new NBTTagCompound();
            config.setInteger("selected", selected);
            config.setInteger("choiceindex", option.getInteger("value"));
            optionChannel = "";
            send("pin", config, true);
            return;
        }
        String id = cap.getString("id");
        if (id.equals("main")) config.setInteger("main", option.getInteger("value"));
        else if (option.getBoolean("present")) config.getCompoundTag("channels")
            .setInteger(id, option.getInteger("value"));
        else config.getCompoundTag("channels")
            .removeTag(id);
        optionChannel = "";
        send("configure", config, true);
    }

    private HologramState.Cell selectedCell() {
        return selected >= 0 && selected < state.cells.size() ? state.cells.get(selected) : null;
    }

    private NBTTagCompound cellTag() {
        return selected < 0 ? new NBTTagCompound()
            : state.data.getTagList("cells", 10)
                .getCompoundTagAt(selected);
    }

    private String cellName(HologramState.Cell cell, boolean current) {
        if (current && cell.actualStack != null) return cell.actualStack.getDisplayName();
        if (!current && cell.targetStack != null) return cell.targetStack.getDisplayName();
        if (!current && cell.chosenChoice >= 0 && cell.chosenChoice < cell.candidates.size())
            return cell.candidates.get(cell.chosenChoice)
                .getDisplayName();
        if (!current && !cell.candidates.isEmpty()) return cell.candidates.get(0)
            .getDisplayName();
        net.minecraft.block.Block block = cell.block(current);
        if (block != null) return block.getLocalizedName();
        return current ? "空位 / 未知现场" : "固定接口 / 未知目标";
    }

    private static String statusName(String status) {
        switch (status) {
            case "protected":
                return "受保护：控制器 / 未适配设备保留";
            case "unsupported":
                return "固定接口待手动补齐";
            case "missing":
                return "缺少材料";
            case "satisfied":
                return "目标已满足";
            case "pending":
                return "待施工";
            case "placed":
                return "已建造";
            case "replaced":
                return "已替换";
            case "removed":
                return "已拆除";
            default:
                return status;
        }
    }

    private void text(String value, int x, int y, int w, int color) {
        drawString(fontRendererObj, fontRendererObj.trimStringToWidth(value, w), x, y, color);
    }

    private void skin(String name, int x, int y, int w, int h) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glColor4f(1, 1, 1, 1);
            mc.getTextureManager()
                .bindTexture(new ResourceLocation("gtit", "textures/gui/pocket/POCKET_C2_" + name + ".png"));
            func_152125_a(x, y, 0, 0, 1, 1, w, h, 1, 1);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void button(int id, int x, int y, int w, int h, String label, boolean enabled, int mx, int my) {
        Control c = new Control(id, x, y, w, h, enabled);
        controls.add(c);
        skin(enabled && c.contains(mx, my) ? "btn_pressed" : "btn", x, y, w, h);
        drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(label, w - 6),
            x + w / 2,
            y + (h - 8) / 2,
            enabled ? 0xf0ddb3 : 0x647c7f);
        if (c.contains(mx, my)) {
            String help = controlHelp(id);
            if (help != null) {
                tooltip = new ArrayList<>(fontRendererObj.listFormattedStringToWidth(help, 230));
                if (!enabled) tooltip.add(disabledReason(id));
            }
        }
    }

    private String editingReason() {
        if (state.data.getBoolean("targetClosed")) return "控制器已拆除；保留最终统计，重新右键新控制器开启任务。";
        if (pendingSequence >= 0) return "等待服务端回执后可操作。";
        if (state.data.getBoolean("capturePending")) return "正在采集结构，请稍候。";
        if (state.data.getInteger("job") == 1 || state.data.getInteger("job") == 2) return "施工任务中；取消任务后可修改草案。";
        return "当前目标不支持此操作。";
    }

    private String phaseName() {
        String phase = state.data.getString("phase");
        if (phase.equals("PREPARE")) return "等待服务端执行";
        if (phase.equals("ACK")) return "已收到施工结果";
        if (phase.equals("GAP")) return "等待服务端执行";
        return phase;
    }

    private String disabledReason(int id) {
        if (!editable() && id != 51 && id != 52) return editingReason();
        if (id == 65 && selectedCell() != null) {
            if (!canInventoryPin()) return "控制器、已有受保护设备或拆除模式不能指定新目标；仓室数据不迁移，服务端验证替换安全性。";
        }
        if (id == 66) return selectedCell() == null ? "先选择预览格。" : "本格没有独立指定的目标，无需清除。";
        if (id == 69) return "当前没有选中预览格。";
        if (id == 95) return "先选择有仓室候选或可调整朝向的预览格；拆除模式无需配置目标。";
        if (id == 99) return "本格没有可安全修改的仓室朝向。";
        if (id == 102) return "本格没有单独指定的朝向，无需恢复。";
        if (id == 106) return state.data.getString("presetReason");
        if (id == 107) return "当前没有启用整机仓室预设，无需撤销。";
        if (id == 103 || id == 104) return "已到达合法仓室等级边界。";
        if (id == 93 && selectedCell() != null && selectedCell().family.isEmpty()) return "本格没有可安全识别的同类部件，请使用单格范围。";
        if (id == 61 && state.data.getInteger("mode") == 2) return "拆除模式无需指定新方块；切换补建或替换后可选材。";
        if (id == 50 && state.data.getInteger("scope") >= 2 && selectedCell() == null) return "选中格或同类部件范围需要先选择预览格。";
        if (id == 92 || id == 93 || id == 65) return selectedCell() == null ? "先点击左侧预览选位，或添加坐标。" : "先从背包选择目标物品。";
        if (id == 83) return "此结构没有已验证的仓室自动选材能力。";
        if (id == 55) return state.data.getInteger("missing") > 0 ? "材料不足：请补充背包后重新采集。" : "材料与改动已更新，请重新查看。";
        if (id == 51 || id == 52) return pendingSequence >= 0 ? "等待服务端回执。" : "没有可暂停或取消的施工任务。";
        if (id == 12 || id == 13) return "已到达结构图层边界。";
        if (id == 80 || id == 82) return "已到达当前已验证目标列表边界。";
        return "此操作不支持当前目标或状态。";
    }

    private String controlHelp(int id) {
        switch (id) {
            case 70:
            case 71:
            case 72:
                return "选择补建、替换或拆除；主界面数字键 1 / 2 / 3 也可切换。切换模式不会直接施工。";
            case 10:
                return "切换目标结构、真实现场和施工差分。";
            case 11:
                return "切换所有图层与当前单层预览；施工范围由右侧范围按钮决定。";
            case 12:
            case 13:
                return "查看相邻图层。";
            case 15:
                return "显示或隐藏世界中的材质预览。";
            case 26:
                return "可选：重新核对材料与改动，不执行施工。";
            case 30:
                return "高级主原始信号；未显式指定的子信道继承它。修改后更新预览。";
            case 33:
                return "只放外壳：可回退位置使用外壳，固定接口仍需手动补齐。允许仓室：服务端可验证仓室候选；不保证机器成型。";
            case 50:
                return "按当前操作立即执行；服务端先核对现场与材料，无施工动画。";
            case 51:
                return "暂停或继续当前施工；暂停保留已完成方块。";
            case 52:
                return "停止任务，保留已完成方块与材料消耗，不撤销施工。";
            case 55:
                return "按原版接口直接施工，无下落动画；界面保留，完成统计来自服务端回执。";
            case 60:
                return "查看具名目标与原始值，可手填任意合法信道；滚轮查看完整列表。";
            case 61:
                return "打开真实36格背包；点击或拖动复制目标，物品不会被移动或消耗。";
            case 75:
                return "按世界轴填写相对控制器的坐标，添加后自动选择单格。";
            case 78:
                return "选择从主背包、背包内容器与 ME 终端取材，并设置优先来源。";
            case 65:
                return "将选中的背包物品指定给当前预览格；可指定任意可放置方块或仓室，服务端验证替换安全性。";
            case 66:
                return "清除当前格的手动或预设目标，恢复信道或结构默认规则；主界面 Delete 同效。取消格选择不会清除目标。";
            case 69:
                return "仅取消预览格选择，保留已指定目标；也可右键预览或按 Esc。";
            case 80:
            case 81:
            case 82:
                return "修改当前部件的合法目标等级，仅更新目标。";
            case 83:
                return "仓室手选：从候选或背包指定目标。自动选材：允许结构自身选择仓室材料，不保证数量与成型配额。";
            case 90:
                return "施工范围：整个结构中可安全执行的差分格。";
            case 91:
                return "施工范围：当前显示图层的差分格。";
            case 92:
                return "施工范围：当前选中的一个格。";
            case 93:
                return "施工范围：与选中格属于同一已识别部件家族的格；未知部件仍保留保护。";
            case 94:
                return "按结构声明的仓室种类和数量生成空位目标，可选等级及缺料降级策略。保留手动目标和已有仓室。";
            case 95:
                return "选择本格所有服务端认可的仓室候选，按类型、等级筛选并查看可用数量；也可调整仓室朝向。";
            case 97:
                return "切换仓室类型筛选，不修改目标。";
            case 98:
                return "切换电压等级筛选；全部等级包含所有服务端认可的候选。";
            case 99:
                return "在本格合法方向中循环指定仓室朝向；应用施工时生效。";
            case 102:
                return "清除本格独立朝向设置，恢复自动方向规则。";
            case 103:
            case 104:
                return "预设仓室的独立电压等级，不修改线圈、玻璃或其它结构等级。";
            case 105:
                return "严格等级：缺料时保留缺料目标；允许降级：按已启用来源选择较低等级。";
            case 106:
                return "按声明配额生成预设目标，不执行施工；随后检查差分与材料并点击施工按钮。";
            case 107:
                return "撤销整机预设目标；保留手选目标、已有仓室与实际施工结果。";
            default:
                return null;
        }
    }

    private ItemStack displayedStack(HologramState.Cell cell, boolean current) {
        ItemStack stack = current ? cell.actualStack : cell.targetStack;
        if (stack != null) return stack;
        net.minecraft.block.Block block = cell.block(current);
        if (block == null || block == net.minecraft.init.Blocks.air) return null;
        net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(block);
        return item == null ? null : new ItemStack(item, 1, current ? cell.meta : cell.wantMeta);
    }

    private void drawItem(ItemStack stack, int x, int y) {
        if (stack == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        float previousZ = itemRenderer.zLevel;
        try {
            itemRenderer.zLevel = 0;
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            RenderHelper.enableGUIStandardItemLighting();
            itemRenderer.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
            itemRenderer.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        } finally {
            itemRenderer.zLevel = previousZ;
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private int localX(int x) {
        return (int) ((x - originX) / scale);
    }

    private int localY(int y) {
        return (int) ((y - originY) / scale);
    }

    private boolean inViewport(int x, int y) {
        return x >= viewportX && x < viewportX + viewportWidth && y >= viewportY && y < viewportY + viewportHeight;
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        int mx = localX(x), my = localY(y);
        if (hatchDrawer || presetDrawer) {
            if (button == 0) for (int i = controls.size() - 1; i >= 0; i--) {
                Control control = controls.get(i);
                if (control.contains(mx, my)) {
                    if (control.enabled) activate(control.id);
                    return;
                }
            }
            return;
        }
        if (drawer && !confirmation && optionChannel.isEmpty()) {
            mainField.mouseClicked(mx, my, button);
            channelNameField.mouseClicked(mx, my, button);
            channelValueField.mouseClicked(mx, my, button);
        }
        if (!optionChannel.isEmpty() && !confirmation) optionValueField.mouseClicked(mx, my, button);
        if (positionDrawer && !confirmation) {
            positionXField.mouseClicked(mx, my, button);
            positionYField.mouseClicked(mx, my, button);
            positionZField.mouseClicked(mx, my, button);
        }
        if (button == 1 && previewInteractive() && inViewport(mx, my)) {
            if (inventoryDragging || inventorySlot >= 0 || sourceChoice >= 0) {
                inventoryDragging = false;
                inventorySlot = sourceChoice = -1;
            } else clearSelection();
            dragging = false;
            return;
        }
        if (button == 0) {
            if (inventory && !confirmation && optionChannel.isEmpty()) {
                int slot = inventorySlotAt(mx, my);
                if (slot >= 0) {
                    if ((sourceTargets ? sourceStack(slot) : mc.thePlayer.inventory.mainInventory[slot]) != null
                        && editable()) {
                        if (sourceTargets) sourceChoice = slot;
                        else inventorySlot = slot;
                        inventoryDragging = true;
                    }
                    return;
                }
            }
            for (int i = controls.size() - 1; i >= 0; i--) {
                Control c = controls.get(i);
                if (c.contains(mx, my)) {
                    if (sourceDrawer && (c.id < 84 || c.id > 88)) return;
                    if (positionDrawer && c.id != 76 && c.id != 77) return;
                    if (confirmation && c.id != 54 && c.id != 55) return;
                    if (!optionChannel.isEmpty() && c.id != 63 && c.id != 68 && c.id < 4000) return;
                    if (inventory && c.id != 64
                        && c.id != 65
                        && c.id != 66
                        && c.id != 16
                        && c.id != 17
                        && c.id != 18
                        && c.id != 95
                        && !inViewport(mx, my)) return;
                    if (drawer && !confirmation
                        && optionChannel.isEmpty()
                        && c.id < 1000
                        && c.id != 62
                        && c.id != 30
                        && c.id != 23
                        && c.id != 33
                        && c.id != 83
                        && c.id != 67
                        && c.id != 68) return;
                    if (c.enabled) activate(c.id);
                    return;
                }
            }
            if (previewInteractive() && inViewport(mx, my)) {
                dragging = true;
                moved = false;
                dragX = mx;
                dragY = my;
            }
        }
        if (button == 2 && previewInteractive() && !inventory && inViewport(mx, my)) resetCamera();
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (!dragging) return;
        int mx = localX(x), my = localY(y), dx = mx - dragX, dy = my - dragY;
        if (Math.abs(dx) + Math.abs(dy) > 1) moved = true;
        yaw += dx * .012;
        pitch = Math.max(-1.35, Math.min(1.35, pitch + dy * .012));
        dragX = mx;
        dragY = my;
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int button) {
        if (button == 0 && inventoryDragging) {
            int mx = localX(x), my = localY(y);
            inventoryDragging = false;
            int target = HologramRenderer.pick(mx, my);
            if (inViewport(mx, my) && target >= 0) pinInventory(target);
            else if (mx >= 165 && mx < 396 && my >= 104 && my < 122) pinInventory(selected);
            return;
        }
        if (button == 0 && dragging) {
            int target = HologramRenderer.pick(localX(x), localY(y));
            if (!moved && inViewport(localX(x), localY(y)) && editable()) {
                if (target < 0 || target == selected && !inventory) {
                    clearSelection();
                    dragging = false;
                    return;
                }
                selected = target;
                NBTTagCompound selection = configuration();
                selection.setInteger("selected", selected);
                send("configure", selection, true);
            }
            dragging = false;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = localX(Mouse.getEventX() * width / mc.displayWidth),
            my = localY(height - Mouse.getEventY() * height / mc.displayHeight - 1);
        if (positionDrawer || sourceDrawer || presetDrawer) return;
        if (hatchDrawer) {
            hatchOffset = Math
                .max(0, Math.min(Math.max(0, filteredHatches().size() - 5), hatchOffset + (wheel > 0 ? -1 : 1)));
            return;
        }
        if (confirmation) {
            materialOffset = Math
                .max(0, Math.min(Math.max(0, state.materials.size() - 3), materialOffset + (wheel > 0 ? -1 : 1)));
        } else if (inventory && sourceTargets && mx >= 157) {
            int count = state.data.getTagList("materialChoices", 10)
                .tagCount();
            int maxOffset = Math.max(0, (count - 36 + 8) / 9 * 9);
            sourceOffset = Math.max(0, Math.min(maxOffset, sourceOffset + (wheel > 0 ? -9 : 9)));
        } else if (!optionChannel.isEmpty()) {
            NBTTagCompound cap = capability(optionChannel);
            drawerOffset = Math.max(
                0,
                Math.min(
                    Math.max(
                        0,
                        cap == null ? 0
                            : cap.getTagList("options", 10)
                                .tagCount() - 7),
                    drawerOffset + (wheel > 0 ? -1 : 1)));
        } else if (drawer) {
            drawerOffset = Math
                .max(0, Math.min(Math.max(0, capabilities().tagCount() - 4), drawerOffset + (wheel > 0 ? -1 : 1)));
        } else if (inViewport(mx, my)) zoom = Math.max(.25, Math.min(4, zoom * (wheel > 0 ? 1.1 : 1 / 1.1)));
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (inventoryDragging || inventorySlot >= 0 || sourceChoice >= 0) {
                inventoryDragging = false;
                inventorySlot = sourceChoice = -1;
            } else if (inventory) inventory = false;
            else if (hatchDrawer) hatchDrawer = false;
            else if (presetDrawer) presetDrawer = false;
            else if (confirmation) confirmation = false;
            else if (positionDrawer) positionDrawer = false;
            else if (sourceDrawer) sourceDrawer = false;
            else if (!optionChannel.isEmpty()) optionChannel = "";
            else if (drawer) drawer = false;
            else if (selected >= 0) clearSelection();
            else mc.displayGuiScreen(null);
            return;
        }
        if (previewInteractive() && !inventory && editable()) {
            if (key == Keyboard.KEY_1 || key == Keyboard.KEY_2 || key == Keyboard.KEY_3) {
                activate(70 + key - Keyboard.KEY_1);
                return;
            }
            if (key == Keyboard.KEY_DELETE && hasSelectedPin()) {
                activate(66);
                return;
            }
        }
        if (positionDrawer && !confirmation) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) activate(76);
            else {
                positionXField.textboxKeyTyped(c, key);
                positionYField.textboxKeyTyped(c, key);
                positionZField.textboxKeyTyped(c, key);
            }
        } else if (!optionChannel.isEmpty() && !confirmation) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) activate(68);
            else optionValueField.textboxKeyTyped(c, key);
        } else if (drawer && !confirmation) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)
                activate((channelNameField.isFocused() || channelValueField.isFocused()) ? 67 : 30);
            else if (channelNameField.isFocused()) channelNameField.textboxKeyTyped(c, key);
            else if (channelValueField.isFocused()) channelValueField.textboxKeyTyped(c, key);
            else mainField.textboxKeyTyped(c, key);
        }
    }

    private void activate(int id) {
        notice = "";
        if (id >= 10000) {
            if (!editable() || !canInventoryPin()) return;
            NBTTagCompound n = new NBTTagCompound();
            n.setInteger("selected", selected);
            n.setInteger("choiceindex", id - 10000);
            send("pin", n, true);
            return;
        }
        if (id >= 4000) {
            chooseOption(capability(optionChannel), id - 4000);
            return;
        }
        if (id >= 2000) {
            optionChannel = capabilities().getCompoundTagAt(id - 2000)
                .getString("id");
            drawerOffset = 0;
            optionValueField.setText(Integer.toString(capability(optionChannel).getInteger("current")));
            return;
        }
        if (id >= 90 && id <= 93) {
            configure("scope", id - 90);
            return;
        }
        if (id >= 70 && id <= 72) {
            NBTTagCompound modeConfig = configuration();
            modeConfig.setInteger("mode", id - 70);
            if (id == 72) {
                modeConfig.setInteger("scope", 0);
                modeConfig.setBoolean("keepController", true);
            }
            send("configure", modeConfig, true);
            return;
        }
        switch (id) {
            case 10:
                view = (view + 1) % 3;
                break;
            case 11:
                allLayers = !allLayers;
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
                if (!HologramClient.worldPreview) HologramRenderer.release();
                break;
            case 16:
            case 17:
                sourceTargets = id == 17;
                inventorySlot = sourceChoice = -1;
                inventoryDragging = false;
                sourceOffset = 0;
                break;
            case 18:
                if (editable()) {
                    sourceChoice = -1;
                    send("scan", null, true);
                }
                break;
            case 23:
                if (editable()) send("scan", null, true);
                break;
            case 26:
                if (editable()) {
                    awaitingScan = true;
                    send("scan", null, true);
                }
                break;
            case 60:
                drawer = true;
                inventory = false;
                drawerOffset = 0;
                break;
            case 61:
                drawer = false;
                inventory = true;
                inventorySlot = sourceChoice = -1;
                sourceOffset = 0;
                if (editable()) send("scan", null, true);
                break;
            case 64:
                inventory = false;
                inventoryDragging = false;
                inventorySlot = sourceChoice = -1;
                break;
            case 65:
                pinInventory(selected);
                break;
            case 66:
                if (!editable() || !hasSelectedPin()) break;
                NBTTagCompound clear = new NBTTagCompound();
                clear.setInteger("selected", selected);
                send("clearPin", clear, true);
                break;
            case 69:
                clearSelection();
                break;
            case 94:
                presetTier = state.data.getInteger("hatchTier");
                presetDowngrade = state.data.getBoolean("hatchDowngrade");
                presetDrawer = true;
                break;
            case 95:
                if (selectedCell() == null) break;
                inventory = false;
                inventorySlot = sourceChoice = -1;
                hatchDrawer = true;
                hatchOffset = hatchRole = 0;
                hatchFilterTier = -1;
                break;
            case 96:
                hatchDrawer = presetDrawer = false;
                break;
            case 97:
                hatchRole = (hatchRole + 1) % hatchRoles().size();
                hatchOffset = 0;
                break;
            case 98:
                hatchFilterTier++;
                if (hatchFilterTier > state.data.getInteger("hatchTierMax")) hatchFilterTier = -1;
                hatchOffset = 0;
                break;
            case 99:
            case 102:
                if (!editable()) break;
                int[] facings = cellTag().getIntArray("hatchFacings");
                int nextFacing = -1;
                if (id == 99 && facings.length > 0) {
                    nextFacing = facings[0];
                    for (int i = 0; i < facings.length; i++) if (facings[i] == cellTag().getInteger("hatchFacing")) {
                        nextFacing = facings[(i + 1) % facings.length];
                        break;
                    }
                }
                NBTTagCompound facingAction = new NBTTagCompound();
                facingAction.setInteger("selected", selected);
                facingAction.setInteger("facing", nextFacing);
                send("hatchFacing", facingAction, true);
                break;
            case 103:
                presetTier = Math.max(0, presetTier - 1);
                break;
            case 104:
                presetTier = Math.min(state.data.getInteger("hatchTierMax"), presetTier + 1);
                break;
            case 105:
                presetDowngrade = !presetDowngrade;
                break;
            case 106:
                if (!editable() || !state.data.getBoolean("supported") || !state.data.getBoolean("presetAvailable"))
                    break;
                NBTTagCompound preset = new NBTTagCompound();
                preset.setInteger("tier", presetTier);
                preset.setBoolean("downgrade", presetDowngrade);
                presetDrawer = false;
                send("hatchPreset", preset, true);
                break;
            case 107:
                if (!editable() || !state.data.getBoolean("presetActive")) break;
                presetDrawer = false;
                send("clearHatchPreset", new NBTTagCompound(), true);
                break;
            case 62:
                drawer = false;
                mainField.setFocused(false);
                break;
            case 63:
                optionChannel = "";
                break;
            case 67:
                applyRawChannel(
                    channelNameField.getText()
                        .trim(),
                    channelValueField.getText());
                break;
            case 68:
                applyRawChannel(optionChannel, optionValueField.getText());
                break;
            case 30:
                if (!editable()) break;
                try {
                    int value = Integer.parseInt(
                        mainField.getText()
                            .trim());
                    if (value >= 1) configure("main", value);
                    else notice = "主值须为1–2147483647";
                } catch (NumberFormatException ignored) {
                    notice = "主值须为1–2147483647";
                }
                break;
            case 33:
                if (editable()) {
                    NBTTagCompound n = configuration();
                    n.setBoolean("noHatches", !state.data.getBoolean("noHatches"));
                    send("configure", n, true);
                }
                break;

            case 50:
                if (editable()) send("build", configuration(), true);
                break;
            case 51:
                send(state.data.getInteger("job") == 2 ? "resume" : "pause", null, true);
                break;
            case 52:
                send("cancel", null, true);
                break;
            case 54:
                confirmation = false;
                break;
            case 55:
                if (editable() && confirmation
                    && confirmedRevision == revision()
                    && state.data.getInteger("missing") == 0) {
                    NBTTagCompound n = new NBTTagCompound();
                    n.setLong("planRevision", confirmedRevision);
                    confirmation = false;
                    send("build", n, true);
                }
                break;
            case 80:
            case 82:
                NBTTagCompound cap = primaryCapability();
                if (cap != null) chooseOption(cap, optionIndex(cap) + (id == 80 ? -1 : 1));
                break;
            case 81:
                NBTTagCompound primary = primaryCapability();
                if (primary != null) {
                    optionChannel = primary.getString("id");
                    optionValueField.setText(Integer.toString(primary.getInteger("current")));
                    drawerOffset = 0;
                }
                break;
            case 75:
                positionDrawer = true;
                drawer = inventory = false;
                break;
            case 76:
                addPosition();
                break;
            case 77:
                positionDrawer = false;
                break;
            case 78:
                sourceDrawer = true;
                drawer = inventory = positionDrawer = false;
                break;
            case 84:
            case 85:
            case 86:
                if (editable()) {
                    String key = new String[] { "materialMain", "materialContainers", "materialMe" }[id - 84];
                    NBTTagCompound sourceConfig = configuration();
                    sourceConfig.setBoolean(key, !sourceEnabled(key));
                    send("configure", sourceConfig, true);
                }
                break;
            case 87:
                configure("materialPriority", (state.data.getInteger("materialPriority") + 1) % 3);
                break;
            case 88:
                sourceDrawer = false;
                break;
            case 83:
                NBTTagCompound hatch = capability("gt_hatch");
                if (hatch != null) chooseOption(hatch, hatch.getBoolean("explicit") ? 0 : 1);
                break;
            default:
                break;
        }
    }

    private void applyRawChannel(String name, String raw) {
        if (!editable()) return;
        if (!name.matches("[a-z0-9_.-]{1,48}") || "_hatch_candidate".equals(name)) {
            notice = "name须为1–48位小写字母、数字、_、.或-";
            return;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1) throw new NumberFormatException();
            NBTTagCompound config = configuration();
            if ("main".equals(name)) config.setInteger("main", value);
            else config.getCompoundTag("channels")
                .setInteger(name, value);
            optionChannel = "";
            send("configure", config, true);
        } catch (NumberFormatException invalid) {
            notice = "value须为1–2147483647";
        }
    }

    private void resetCamera() {
        yaw = frontCameraYaw();
        pitch = Math.PI / 6;
        zoom = 1;
    }

    private double frontCameraYaw() {
        int facing = state.data.getInteger("facing");
        if (facing < 0 || facing >= ExtendedFacing.values().length) return Math.PI / 4;
        ForgeDirection direction = ExtendedFacing.values()[facing].getDirection();
        if (direction.offsetX == 0 && direction.offsetZ == 0) return Math.PI / 4;
        return Math.atan2(direction.offsetX, direction.offsetZ) + Math.PI / 4;
    }

    private NBTTagCompound configuration() {
        NBTTagCompound n = new NBTTagCompound();
        for (String key : new String[] { "main", "facing", "mode", "scope" })
            n.setInteger(key, state.data.getInteger(key));
        n.setInteger("layer", layer);
        n.setInteger("selected", selected);
        n.setBoolean("noHatches", state.data.getBoolean("noHatches"));
        n.setBoolean("keepController", true);
        for (String key : new String[] { "materialMain", "materialContainers", "materialMe" })
            n.setBoolean(key, sourceEnabled(key));
        n.setInteger("materialPriority", state.data.getInteger("materialPriority"));
        n.setTag(
            "channels",
            state.data.getCompoundTag("channels")
                .copy());
        return n;
    }

    private void configure(String key, int value) {
        if (!editable()) return;
        NBTTagCompound n = configuration();
        n.setInteger(key, value);
        send("configure", n, true);
    }

    private void send(String op, NBTTagCompound n, boolean pending) {
        if (n == null) n = new NBTTagCompound();
        n.setString("session", state.session);
        n.setString("op", op);
        n.setLong("uiSequence", ++sequence);
        if (pending) {
            pendingSequence = sequence;
            confirmation = false;
        }
        HologramNetwork.sendAction(n);
    }

    /** Smoke hooks use real mouse hit testing, including modal gates and disabled controls. */
    int[] smokeControlPoint(int id) {
        for (Control c : controls) if (c.id == id) return new int[] { originX + Math.round((c.x + c.w / 2F) * scale),
            originY + Math.round((c.y + c.h / 2F) * scale) };
        throw new IllegalArgumentException("No drawn control " + id);
    }

    void smokeClickControl(int id) {
        requireInteractionSmoke();
        int[] p = smokeControlPoint(id);
        mouseClicked(p[0], p[1], 0);
        mouseMovedOrUp(p[0], p[1], 0);
    }

    NBTTagCompound smokeSnapshot() {
        requireInteractionSmoke();
        return (NBTTagCompound) state.data.copy();
    }

    boolean smokeHasControl(int id) {
        requireInteractionSmoke();
        for (Control control : controls) if (control.id == id && control.enabled) return true;
        return false;
    }

    long smokeRenderGeneration() {
        requireInteractionSmoke();
        return renderGeneration;
    }

    private static void requireInteractionSmoke() {
        if (!Boolean.getBoolean("gtit.hologram.realInteractionSmoke"))
            throw new IllegalStateException("Real interaction smoke is disabled");
    }

    boolean smokePending() {
        return pendingSequence >= 0;
    }

    String smokeLayout() {
        if (controls.isEmpty()) throw new IllegalStateException("Screen has not rendered controls");
        for (Control c : controls) if (c.x < 0 || c.y < 0 || c.x + c.w > DESIGN_W || c.y + c.h > DESIGN_H)
            throw new IllegalStateException("Control outside design: " + c.id);
        return "gui=" + width
            + "x"
            + height
            + " design=416x288 scale="
            + scale
            + " viewport="
            + viewportWidth
            + "x"
            + viewportHeight
            + " cells="
            + state.cells.size()
            + " controls="
            + controls.size();
    }

    private static final class Control {

        final int id, x, y, w, h;
        final boolean enabled;

        Control(int id, int x, int y, int w, int h, boolean enabled) {
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
