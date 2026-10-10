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

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.common.blocks.ItemMachines;

/** Compact C2 workstation. Configuration previews a server plan; only confirmation starts construction. */
public final class HologramScreen extends GuiScreen {

    private static final int DESIGN_W = 416, DESIGN_H = 288;
    private final List<Control> controls = new ArrayList<>();
    private final RenderItem itemRenderer = new RenderItem();
    private HologramState state;
    private GuiTextField mainField, channelNameField, channelValueField, optionValueField;
    private int selected = -1, layer, view, hovered = -1, drawerOffset, candidateOffset, materialOffset;
    private int viewportX = 12, viewportY = 64, viewportWidth = 142, viewportHeight = 136;
    private int dragX, dragY, originX, originY;
    private double yaw = Math.PI / 4, pitch = Math.PI / 6, zoom = 1;
    private float scale = 1;
    private boolean allLayers = true, dragging, moved, drawer, confirmation, channelDrawer, inventory;
    private int inventorySlot = -1;
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
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float ticks) {
        drawDefaultBackground();
        int mx = localX(mouseX), my = localY(mouseY);
        controls.clear();
        tooltip = null;
        GL11.glPushMatrix();
        GL11.glTranslatef(originX, originY, 0);
        GL11.glScalef(scale, scale, 1);
        try {
            skin("cloth", 0, 0, DESIGN_W, DESIGN_H);
            drawRect(6, 29, 410, 280, 0xed142a32);
            skin("panel", 6, 6, 404, 22);
            text("猫猫全息投影仪 · " + state.data.getString("title"), 13, 13, 270, 0xf5e4b9);
            button(60, 287, 9, 117, 17, "等级 / 信道设置", true, mx, my);
            String[] modes = { "建造", "替换", "拆除" };
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
            drawPreview(mx, my);
            drawConfig(mx, my);
            drawFooter(mx, my);
            if (drawer) {
                if (channelDrawer) drawChannels(mx, my);
                else drawDrawer(mx, my);
            }
            if (inventory) drawInventory(mx, my);
            if (!optionChannel.isEmpty()) drawOptions(mx, my);
            if (confirmation) drawConfirmation(mx, my);
            if (!notice.isEmpty() && !confirmation) text(notice, 165, 249, 231, 0xff9292);
            if (tooltip != null) drawHoveringText(tooltip, mx, my, fontRendererObj);
        } finally {
            GL11.glPopMatrix();
        }
        renderGeneration++;
    }

    private void drawPreview(int mx, int my) {
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
        text("拖动旋转 · 滚轮缩放", 14, 226, 140, 0x94afb4);
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
            if (mx >= 166 && mx < 400 && my >= 77 && my < 98)
                tooltip = Collections.singletonList(channel + " · " + optionLabel(primary));
        } else text("无已验证的等级参数 · 高级查看", 166, 82, 234, 0x94afb4);
        button(
            33,
            166,
            104,
            113,
            18,
            state.data.getInteger("mode") == 2 ? "拆除包含仓室" : state.data.getBoolean("noHatches") ? "只放外壳" : "允许仓室",
            editable() && state.data.getInteger("mode") != 2,
            mx,
            my);
        NBTTagCompound hatch = capability("gt_hatch");
        button(
            83,
            282,
            104,
            118,
            18,
            state.data.getInteger("mode") == 2 ? "保留控制器 " + (state.data.getBoolean("keepController") ? "✓" : "×")
                : hatch == null ? "自动选材未支持" : hatch.getBoolean("explicit") ? "仓室自动选材" : "仓室手选",
            editable() && (state.data.getInteger("mode") == 2 || hatch != null && hatch.getBoolean("editable")),
            mx,
            my);
        text("控制器朝向：" + facingName(), 166, 129, 234, 0x94afb4);
        String[] scopes = { "全结构", "当前层", "选中格", "同类部件" };
        for (int i = 0; i < 4; i++) button(
            90 + i,
            166 + i * 59,
            143,
            56,
            18,
            (state.data.getInteger("scope") == i ? "›" : "") + scopes[i],
            editable() && (i < 2 || selectedCell() != null),
            mx,
            my);
        HologramState.Cell cell = selectedCell();
        if (cell == null) {
            text("点击预览选择结构格", 166, 171, 234, 0xb9d5d4);
            text("目标等级仅改变草案，不自动施工", 166, 185, 234, 0x94afb4);
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
        button(26, 166, 217, 113, 18, "候选 / 材料清单", true, mx, my);
        button(61, 282, 217, 118, 18, "从背包指定目标", true, mx, my);
        String progress = state.data.getBoolean("targetClosed") ? "拆除结束，重新右键新控制器开启任务" : state.data.getString("status");
        text(pendingSequence >= 0 ? "等待服务端校验…" : progress, 166, 244, 234, 0xffbd91);
        if (mx >= 166 && mx < 400 && my >= 239 && my < 257)
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
            job == 5 ? "重扫 / 新计划" : "扫描并查看差分",
            editable() && state.data.getBoolean("target") && state.data.getBoolean("supported"),
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

    private void drawDrawer(int mx, int my) {
        drawRect(157, 59, 405, 257, 0xff172f36);
        text("当前结构能力 / 部件", 165, 66, 200, 0xf5e4b9);
        button(62, 373, 62, 23, 17, "×", true, mx, my);
        NBTTagList caps = capabilities();
        int y = 88;
        for (int i = drawerOffset; i < caps.tagCount() && y < 133; i++) {
            NBTTagCompound cap = caps.getCompoundTagAt(i);
            if (cap.getString("id")
                .equals("main")) continue;
            button(
                2000 + i,
                165,
                y,
                231,
                18,
                cap.getString("label") + "：" + optionLabel(cap),
                editable() && cap.getBoolean("editable"),
                mx,
                my);
            y += 20;
        }
        HologramState.Cell cell = selectedCell();
        if (cell != null) {
            text("目标候选（建造 / 替换）", 165, 137, 230, 0xe4d3a9);
            for (int i = candidateOffset; i < cell.candidates.size() && i < candidateOffset + 2; i++) {
                ItemStack stack = cell.candidates.get(i);
                drawItem(stack, 166, 149 + (i - candidateOffset) * 20);
                button(
                    1000 + i,
                    188,
                    149 + (i - candidateOffset) * 20,
                    208,
                    18,
                    stack.getDisplayName(),
                    editable() && !cell.anchor && state.data.getInteger("mode") != 2,
                    mx,
                    my);
            }
            if (cell.candidates.isEmpty()) text(cellTag().getString("reason"), 165, 152, 231, 0xe4b85a);
        } else {
            for (int i = materialOffset; i < Math.min(materialOffset + 2, state.materials.size()); i++) {
                HologramState.Material m = state.materials.get(i);
                text(
                    (m.stack == null ? "未知材料" : m.stack.getDisplayName()) + " " + m.required + "/" + m.available,
                    165,
                    149 + (i - materialOffset) * 15,
                    231,
                    m.available < m.required ? 0xff9292 : 0xa2e8b8);
            }
        }
        text("主原始正整数", 165, 199, 230, 0x94afb4);
        mainField.drawTextBox();
        button(30, 330, 213, 66, 16, "应用主值", editable(), mx, my);
        button(23, 165, 213, 98, 16, "重新采集", editable(), mx, my);
        button(40, 165, 233, 111, 16, "原版提示", editable(), mx, my);
        button(41, 280, 233, 116, 16, "原版构造回退", editable(), mx, my);
        if (my >= 233 && mx >= 165 && mx < 396)
            tooltip = Collections.singletonList("原版提示 / 构造回退不属于高级事务，不提供本工具的逐格保护与回执。自动仓室只选材，数量和机器成型需手动核对。");
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
                    + (option.getBoolean("present") ? "显式目标，原始值 " + option.getInteger("value") : "清除独立指定，恢复主信号或默认规则")
                    + (editable() ? "；仅更新草案。" : "；" + editingReason()));
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
                tooltip.add("当前草案：" + optionLabel(cap) + " · 原始值 " + cap.getInteger("current"));
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
        button(40, 242, 234, 74, 16, "原版提示", editable(), mx, my);
        button(41, 319, 234, 77, 16, "原版构造", editable(), mx, my);
    }

    private void drawInventory(int mx, int my) {
        tooltip = null;
        drawRect(157, 59, 405, 257, 0xff172f36);
        text("玩家背包 · 复制目标，不移动物品", 165, 65, 205, 0xf5e4b9);
        button(64, 373, 62, 23, 17, "×", true, mx, my);
        HologramState.Cell cell = selectedCell();
        button(
            65,
            165,
            87,
            231,
            22,
            cell == null ? "先点击左侧预览选择目标格" : "目标格：" + cellName(cell, false),
            editable() && canInventoryPin() && inventorySlot >= 0,
            mx,
            my);
        text("点击物品再点目标槽，或拖到左侧预览格", 165, 113, 231, 0x94afb4);
        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) {
            int slot = row == 3 ? col : 9 + row * 9 + col;
            int x = 181 + col * 22, y = 128 + row * 22;
            drawRect(x, y, x + 20, y + 20, inventorySlot == slot ? 0xff5f858a : 0xff0c1e28);
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            drawItem(stack, x + 2, y + 2);
            if (stack != null)
                itemRenderer.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x + 2, y + 2);
            if (mx >= x && mx < x + 20 && my >= y && my < y + 20 && stack != null)
                tooltip = Collections.singletonList(stack.getDisplayName() + " · 库存 " + stack.stackSize + "（不消耗）");
        }
        button(66, 165, 221, 105, 17, "清除单格指定", editable() && cell != null, mx, my);
        ItemStack picked = inventorySlot >= 0 ? mc.thePlayer.inventory.mainInventory[inventorySlot] : null;
        text(picked == null ? "未选物品" : picked.getDisplayName(), 277, 224, 119, 0xe4d3a9);
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
                    .listFormattedStringToWidth("背包选择：" + picked.getDisplayName() + " · 槽位 " + inventorySlot, 230));
        }
        if (picked != null && inventoryDragging) drawItem(picked, mx - 8, my - 8);
    }

    private int inventorySlotAt(int x, int y) {
        int col = (x - 181) / 22, row = (y - 128) / 22;
        if (x < 181 || y < 128 || col >= 9 || row >= 4 || (x - 181) % 22 >= 20 || (y - 128) % 22 >= 20) return -1;
        return row == 3 ? col : 9 + row * 9 + col;
    }

    private void pinInventory(int target) {
        if (!editable() || target < 0 || inventorySlot < 0) return;
        selected = target;
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("selected", target);
        n.setInteger("index", target);
        n.setInteger("inventorySlot", inventorySlot);
        send("customPin", n, true);
    }

    private boolean canInventoryPin() {
        HologramState.Cell cell = selectedCell();
        return cell != null && !cell.anchor && !cell.family.equals("coil") && state.data.getInteger("mode") != 2;
    }

    private void drawConfirmation(int mx, int my) {
        drawRect(40, 58, 376, 254, 0xff172f36);
        text("确认服务端差分 · 计划 " + confirmedRevision, 50, 68, 315, 0xf5e4b9);
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
        if (selected != null)
            text(cellName(selected, true) + " → " + cellName(selected, false), 50, 100, 315, 0xe4d3a9);
        for (int i = materialOffset; i < Math.min(materialOffset + 5, state.materials.size()); i++) {
            HologramState.Material m = state.materials.get(i);
            text(
                (m.stack == null ? "未知材料" : m.stack.getDisplayName()) + " × " + m.required + " / 库存 " + m.available,
                50,
                119 + (i - materialOffset) * 16,
                315,
                m.available < m.required ? 0xff9292 : 0xa2e8b8);
        }
        text("预计回收 " + recovery + " · 不提前抵扣材料", 50, 202, 315, 0x94afb4);
        text(
            notice.isEmpty() ? (state.data.getInteger("mode") == 2
                ? (state.data.getBoolean("keepController") ? "保留控制器锚点；拆除所选范围与仓室" : "最后回收控制器；外围未完成则保留")
                : "直接施工，无动画；仓室数据不迁移") : notice,
            50,
            215,
            315,
            0xe4b85a);
        button(54, 50, 232, 146, 17, "返回草案", true, mx, my);
        button(
            55,
            203,
            232,
            162,
            17,
            "确认并直接施工",
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
            if (selectedCell().family.equals("coil")) return "线圈使用等级信道统一修改，请打开等级 / 信道设置。";
            if (!canInventoryPin()) return "控制器、已有受保护设备或拆除模式不能指定新目标；仓室数据不迁移，服务端验证替换安全性。";
        }
        if (id == 92 || id == 93 || id == 65 || id == 66)
            return selectedCell() == null ? "先点击左侧预览选择一个结构格。" : "先从背包选择目标物品。";
        if (id == 83) return "此结构没有已验证的仓室自动选材能力。";
        if (id == 55) return state.data.getInteger("missing") > 0 ? "材料不足：请补充背包后重新采集。" : "当前范围没有可执行差分，或计划已经更新。";
        if (id == 51 || id == 52) return pendingSequence >= 0 ? "等待服务端回执。" : "没有可暂停或取消的施工任务。";
        if (id == 12 || id == 13) return "已到达结构图层边界。";
        if (id == 80 || id == 82) return "已到达当前已验证目标列表边界。";
        return "此操作不支持当前目标或状态。";
    }

    private String controlHelp(int id) {
        switch (id) {
            case 10:
                return "切换目标结构、真实现场和施工差分。";
            case 11:
                return "切换所有图层与当前单层预览；施工范围由右侧范围按钮决定。";
            case 12:
            case 13:
                return "查看相邻图层。";
            case 15:
                return "显示或隐藏世界中的全息预览；不改变施工计划。";
            case 26:
                return "查看选中空位候选与材料清单，保护原因在主面板显示。";
            case 30:
                return "高级主原始信号；未显式指定的子信道继承它。修改后重新生成草案。";
            case 33:
                return "只放外壳：可回退位置使用外壳，固定接口仍需手动补齐。允许仓室：服务端可验证仓室候选；不保证机器成型。";
            case 40:
                return "调用机器原版提示，仅显示结构提示。";
            case 41:
                return "调用机器原版构造；不提供高级逐格保护与施工回执。";
            case 50:
                return "先重新扫描现场并生成差分，再核对材料、回收与范围；第二步确认才直接施工。";
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
            case 65:
                return "将选中的背包物品指定给当前预览格；服务端重新验证结构候选与替换安全性。";
            case 66:
                return "清除当前格的手动目标，恢复信道或结构默认规则。";
            case 80:
            case 81:
            case 82:
                return "修改当前部件的合法目标等级，仅更新草案。";
            case 83:
                if (state.data.getInteger("mode") == 2) return "默认关闭：所选外围全部成功拆除后，最后回收控制器；外围失败或未完成时保留。开启后保留控制器锚点。";
                return "仓室手选：从候选或背包指定目标。自动选材：允许结构自身选择仓室材料，不保证数量与成型配额。";
            case 90:
                return "施工范围：整个结构中可安全执行的差分格。";
            case 91:
                return "施工范围：当前显示图层的差分格。";
            case 92:
                return "施工范围：当前选中的一个格。";
            case 93:
                return "施工范围：与选中格属于同一已识别部件家族的格；未知部件仍保留保护。";
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
        try {
            RenderHelper.enableGUIStandardItemLighting();
            itemRenderer.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        } finally {
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
        if (drawer && !confirmation && optionChannel.isEmpty()) {
            mainField.mouseClicked(mx, my, button);
            if (channelDrawer) {
                channelNameField.mouseClicked(mx, my, button);
                channelValueField.mouseClicked(mx, my, button);
            }
        }
        if (!optionChannel.isEmpty() && !confirmation) optionValueField.mouseClicked(mx, my, button);
        if (button == 0) {
            if (inventory && !confirmation && optionChannel.isEmpty()) {
                int slot = inventorySlotAt(mx, my);
                if (slot >= 0) {
                    if (mc.thePlayer.inventory.mainInventory[slot] != null && editable()) {
                        inventorySlot = slot;
                        inventoryDragging = true;
                    }
                    return;
                }
            }
            for (int i = controls.size() - 1; i >= 0; i--) {
                Control c = controls.get(i);
                if (c.contains(mx, my)) {
                    if (confirmation && c.id != 54 && c.id != 55) return;
                    if (!optionChannel.isEmpty() && c.id != 63 && c.id != 68 && c.id < 4000) return;
                    if (inventory && c.id != 64 && c.id != 65 && c.id != 66 && !inViewport(mx, my)) return;
                    if (drawer && !confirmation
                        && optionChannel.isEmpty()
                        && c.id < 1000
                        && c.id != 62
                        && c.id != 30
                        && c.id != 23
                        && c.id != 40
                        && c.id != 41
                        && c.id != 67
                        && c.id != 68) return;
                    if (c.enabled) activate(c.id);
                    return;
                }
            }
            if (!drawer && !confirmation && optionChannel.isEmpty() && inViewport(mx, my)) {
                dragging = true;
                moved = false;
                dragX = mx;
                dragY = my;
            }
        }
        if (button == 2 && !drawer && !inventory && !confirmation && optionChannel.isEmpty() && inViewport(mx, my))
            resetCamera();
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
            else if (mx >= 165 && mx < 396 && my >= 87 && my < 109) pinInventory(selected);
            return;
        }
        if (button == 0 && dragging) {
            int target = HologramRenderer.pick(localX(x), localY(y));
            if (!moved && inViewport(localX(x), localY(y)) && target >= 0 && editable()) {
                selected = target;
                candidateOffset = 0;
                configure("selected", selected);
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
        if (confirmation) {
            materialOffset = Math
                .max(0, Math.min(Math.max(0, state.materials.size() - 5), materialOffset + (wheel > 0 ? -1 : 1)));
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
        } else if (drawer && channelDrawer) {
            drawerOffset = Math
                .max(0, Math.min(Math.max(0, capabilities().tagCount() - 4), drawerOffset + (wheel > 0 ? -1 : 1)));
        } else if (drawer) {
            if (my >= 137 && my < 190 && selectedCell() != null) candidateOffset = Math.max(
                0,
                Math.min(Math.max(0, selectedCell().candidates.size() - 2), candidateOffset + (wheel > 0 ? -1 : 1)));
            else if (my >= 137 && my < 190) materialOffset = Math
                .max(0, Math.min(Math.max(0, state.materials.size() - 2), materialOffset + (wheel > 0 ? -1 : 1)));
            else drawerOffset = Math
                .max(0, Math.min(Math.max(0, capabilities().tagCount() - 2), drawerOffset + (wheel > 0 ? -1 : 1)));
        } else if (inViewport(mx, my)) zoom = Math.max(.25, Math.min(4, zoom * (wheel > 0 ? 1.1 : 1 / 1.1)));
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (inventoryDragging || inventorySlot >= 0) {
                inventoryDragging = false;
                inventorySlot = -1;
            } else if (confirmation) confirmation = false;
            else if (!optionChannel.isEmpty()) optionChannel = "";
            else if (drawer) drawer = false;
            else if (inventory) inventory = false;
            else mc.displayGuiScreen(null);
            return;
        }
        if (!optionChannel.isEmpty() && !confirmation) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) activate(68);
            else optionValueField.textboxKeyTyped(c, key);
        } else if (drawer && !confirmation) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)
                activate(channelDrawer && (channelNameField.isFocused() || channelValueField.isFocused()) ? 67 : 30);
            else if (channelDrawer && channelNameField.isFocused()) channelNameField.textboxKeyTyped(c, key);
            else if (channelDrawer && channelValueField.isFocused()) channelValueField.textboxKeyTyped(c, key);
            else mainField.textboxKeyTyped(c, key);
        }
    }

    private void activate(int id) {
        notice = "";
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
        if (id >= 1000) {
            if (!editable() || selectedCell() == null || selectedCell().anchor || state.data.getInteger("mode") == 2)
                return;
            NBTTagCompound n = new NBTTagCompound();
            n.setInteger("selected", selected);
            n.setInteger("choiceindex", id - 1000);
            send("pin", n, true);
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
                modeConfig.setBoolean("keepController", false);
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
            case 23:
                if (editable()) send("scan", null, true);
                break;
            case 26:
                drawer = true;
                channelDrawer = false;
                drawerOffset = 0;
                break;
            case 60:
                drawer = true;
                channelDrawer = true;
                inventory = false;
                drawerOffset = 0;
                break;
            case 61:
                drawer = false;
                inventory = true;
                inventorySlot = -1;
                break;
            case 64:
                inventory = false;
                inventoryDragging = false;
                inventorySlot = -1;
                break;
            case 65:
                pinInventory(selected);
                break;
            case 66:
                NBTTagCompound clear = new NBTTagCompound();
                clear.setInteger("selected", selected);
                send("clearPin", clear, true);
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
            case 40:
                if (editable()) send("hints", null, true);
                break;
            case 41:
                if (editable()) send("native", null, true);
                break;
            case 50:
                if (editable()) {
                    awaitingScan = true;
                    send("scan", null, true);
                }
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
                    send("start", n, true);
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
            case 83:
                if (state.data.getInteger("mode") == 2) {
                    if (editable()) {
                        NBTTagCompound config = configuration();
                        config.setBoolean("keepController", !state.data.getBoolean("keepController"));
                        send("configure", config, true);
                    }
                } else {
                    NBTTagCompound hatch = capability("gt_hatch");
                    if (hatch != null) chooseOption(hatch, hatch.getBoolean("explicit") ? 0 : 1);
                }
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
        n.setBoolean("keepController", state.data.getBoolean("keepController"));
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
