package com.miaokatze.gtit.client.hologram;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.miaokatze.gtit.hologram.HologramNetwork;

/** The shared tool settings menu uses only server supplied preset identifiers. */
final class HologramSettingsScreen extends GuiScreen {

    private HologramState state;
    private boolean main, containers, me, downgrade;
    private int priority, recovery, presetIndex = -1;
    private long sequence;
    private long pendingSequence = -1;

    HologramSettingsScreen(HologramState snapshot) {
        updateState(snapshot);
    }

    void updateState(HologramState snapshot) {
        state = snapshot;
        if (snapshot.data.getLong("uiSequence") >= pendingSequence) pendingSequence = -1;
        sequence = Math.max(sequence, snapshot.data.getLong("uiSequence"));
        main = snapshot.data.getBoolean("materialMain");
        containers = snapshot.data.getBoolean("materialContainers");
        me = snapshot.data.getBoolean("materialMe");
        downgrade = snapshot.data.getBoolean("hatchDowngrade");
        priority = Math.max(0, Math.min(2, snapshot.data.getInteger("materialPriority")));
        recovery = snapshot.data.getInteger("recoveryDestination") == 1 ? 1 : 0;
        presetIndex = -1;
        NBTTagList presets = presets();
        for (int i = 0; i < presets.tagCount(); i++) if (presets.getCompoundTagAt(i)
            .getString("id")
            .equals(snapshot.data.getString("selectedPresetId"))) presetIndex = i;
        if (mc != null) initGui();
    }

    private NBTTagList presets() {
        return state.data.getTagList("presets", 10);
    }

    private String on(boolean enabled) {
        return enabled ? "开启" : "关闭";
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int x = width / 2 - 130, y = height / 2 - 95;
        buttonList.add(new GuiButton(0, x, y, 260, 20, "取料 · 主背包：" + on(main)));
        buttonList.add(new GuiButton(1, x, y + 24, 260, 20, "取料 · 背包容器：" + on(containers)));
        buttonList.add(new GuiButton(2, x, y + 48, 260, 20, "取料 · ME：" + on(me)));
        String[] priorities = { "主背包", "背包容器", "ME" };
        buttonList.add(new GuiButton(3, x, y + 72, 260, 20, "优先取料：" + priorities[priority]));
        buttonList.add(new GuiButton(4, x, y + 96, 260, 20, "缺料时仓室等级降级：" + on(downgrade)));
        String preset = presetIndex < 0 ? "未选择"
            : presets().getCompoundTagAt(presetIndex)
                .getString("name");
        GuiButton presetButton = new GuiButton(5, x, y + 120, 260, 20, "自定义模板：" + preset);
        presetButton.enabled = presets().tagCount() > 0;
        buttonList.add(presetButton);
        buttonList.add(new GuiButton(6, x, y + 144, 260, 20, "默认回收：" + (recovery == 1 ? "ME 优先" : "主背包")));
        buttonList.add(new GuiButton(7, x, y + 174, 126, 20, "保存设置"));
        buttonList.add(new GuiButton(8, x + 134, y + 174, 126, 20, "返回编辑"));
        if (pendingSequence >= 0) {
            for (Object entry : buttonList) ((GuiButton) entry).enabled = false;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (pendingSequence >= 0 || !button.enabled) return;
        switch (button.id) {
            case 0 -> main = !main;
            case 1 -> containers = !containers;
            case 2 -> me = !me;
            case 3 -> priority = (priority + 1) % 3;
            case 4 -> downgrade = !downgrade;
            case 5 -> presetIndex = (presetIndex + 1) % presets().tagCount();
            case 6 -> recovery = 1 - recovery;
            case 7 -> save();
            case 8 -> {
                NBTTagCompound request = new NBTTagCompound();
                request.setString("action", "useAir");
                HologramNetwork.sendAction(request);
            }
            default -> {}
        }
        initGui();
    }

    private void save() {
        NBTTagCompound request = new NBTTagCompound();
        request.setString("op", "settings");
        request.setString("session", state.session);
        request.setLong("uiSequence", ++sequence);
        pendingSequence = sequence;
        request.setBoolean("materialMain", main);
        request.setBoolean("materialContainers", containers);
        request.setBoolean("materialMe", me);
        request.setInteger("materialPriority", priority);
        request.setBoolean("hatchDowngrade", downgrade);
        request.setInteger("recoveryDestination", recovery);
        request.setString(
            "selectedPresetId",
            presetIndex < 0 ? ""
                : presets().getCompoundTagAt(presetIndex)
                    .getString("id"));
        HologramNetwork.sendAction(request);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "投影仪设置 · 与编辑界面共享", width / 2, height / 2 - 120, 0x9ceaf3);
        drawCenteredString(fontRendererObj, "ME 不可用时回收至主背包；背包满则持久暂存", width / 2, height / 2 + 108, 0xbacbd1);
        drawCenteredString(fontRendererObj, state.data.getString("status"), width / 2, height / 2 + 121, 0xe5c488);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
