package com.miaokatze.gtit.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.gui.ReincarnationContainer;
import com.miaokatze.gtit.reincarnation.gui.ReincarnationLayout;

/**
 * 周目轮回 GUI 渲染层（v1.8.3，MUI2 → Forge 原版 IGuiHandler 链路迁移；仅物理客户端加载）
 * <p>
 * 布局/契约单源在 {@link ReincarnationLayout}（几何、按钮 ID、进度条映射、消息事件码），
 * 会话逻辑在 {@link ReincarnationContainer}（本类只读消费其公开访问器）。
 * <p>
 * <b>绘制</b>：GuiContainer 标准 API（{@code Gui.drawRect} + {@code FontRenderer}），
 * 不新增任何贴图；文本全走既有 {@code gtit.reincarnation.*} lang 键
 * （{@link StatCollector}，§ 格式码随键值渲染，双语由 lang 文件承载）。
 * <p>
 * <b>按钮 C2S 通道</b>：vanilla {@code PlayerControllerMP.sendEnchantPacket(windowId, buttonId)}
 * → C11 → 服务端 {@code NetHandlerPlayServer.processEnchantItem} →
 * {@code ReincarnationContainer.enchantItem}（零自定义 C2S 包，任务包红线）。
 * <p>
 * <b>确认轮回两步制</b>（替代旧 {@code NekoConfirmationDialog}）：第一次点击仅服务端武装
 * （按钮文案切换为 {@code gtit.reincarnation.gui.confirm.dialog} 警示语），第二次点击执行；
 * 武装态经进度条 {@code BAR_CONFIRM_ARMED} 下发，槽位变动/关窗由 Container 重置。
 * <p>
 * <b>消息行</b>：服务端仅推送事件码（进度条 id 20），列名/n 值/概率参数由本类从其余进度条
 * 值与 lang 键本地派生（已知纯显示级差异：旧 MUI2 在事件时刻冻结参数文本，本实现参数实时刷新）。
 * 旧 MUI2 悬浮提示（tooltip）为 UI 附属能力，本切片不迁移（逻辑零承载）。
 */
public class ReincarnationGuiContainer extends GuiContainer {

    // ==================== lang 键（只读复用既有键，lang 文件不改） ====================

    private static final String KEY_PREFIX = "gtit.reincarnation.";
    private static final String KEY_TITLE = KEY_PREFIX + "gui.title";
    private static final String KEY_BANNER_IDLE = KEY_PREFIX + "gui.banner.idle";
    private static final String KEY_BANNER_DEPOSITED = KEY_PREFIX + "gui.banner.deposited";
    private static final String KEY_BANNER_EXECUTED = KEY_PREFIX + "gui.banner.executed";
    private static final String KEY_PROGRESS = KEY_PREFIX + "gui.progress";
    private static final String KEY_IGNORE_NBT = KEY_PREFIX + "gui.ignore_nbt";
    private static final String KEY_ENCRYPTED = KEY_PREFIX + "gui.encrypted";
    private static final String KEY_UNLOCK_SHIMMER = KEY_PREFIX + "gui.unlock.shimmer";
    private static final String KEY_UNLOCK_NORMAL = KEY_PREFIX + "gui.unlock.normal";
    private static final String KEY_CONFIRM_BUTTON = KEY_PREFIX + "gui.confirm.button";
    private static final String KEY_CONFIRM_DIALOG = KEY_PREFIX + "gui.confirm.dialog";
    private static final String KEY_COLUMN_PREFIX = KEY_PREFIX + "column.";

    /** 底部提示行颜色（与旧 GUI footer 0xFF777788 一致） */
    private static final int FOOTER_COLOR = 0x777788;
    /** 未解锁列名颜色（EnumChatFormatting.DARK_GRAY 同值） */
    private static final int COLUMN_LOCKED_COLOR = 0x555555;
    /** 已解锁列名颜色（EnumChatFormatting.GOLD 同值） */
    private static final int COLUMN_UNLOCKED_COLOR = 0xFFAA00;

    public ReincarnationGuiContainer(EntityPlayer player) {
        super(new ReincarnationContainer(player));
        this.xSize = ReincarnationLayout.PANEL_WIDTH;
        this.ySize = ReincarnationLayout.PANEL_HEIGHT;
    }

    private ReincarnationContainer container() {
        return (ReincarnationContainer) this.inventorySlots;
    }

    // ==================== 按钮（动态文案/置灰 + sendEnchantPacket 通道） ====================

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.add(
            new StatefulButton(
                ReincarnationLayout.BUTTON_UNLOCK_SHIMMER,
                this.guiLeft + ReincarnationLayout.UNLOCK_SHIMMER_X,
                this.guiTop + ReincarnationLayout.ACTION_Y,
                ReincarnationLayout.UNLOCK_BUTTON_WIDTH,
                ReincarnationLayout.SLOT_SIZE,
                StatCollector.translateToLocal(KEY_UNLOCK_SHIMMER)));
        this.buttonList.add(
            new StatefulButton(
                ReincarnationLayout.BUTTON_UNLOCK_NORMAL,
                this.guiLeft + ReincarnationLayout.UNLOCK_NORMAL_X,
                this.guiTop + ReincarnationLayout.ACTION_Y,
                ReincarnationLayout.UNLOCK_BUTTON_WIDTH,
                ReincarnationLayout.SLOT_SIZE,
                StatCollector.translateToLocal(KEY_UNLOCK_NORMAL)));
        this.buttonList.add(
            new StatefulButton(
                ReincarnationLayout.BUTTON_CONFIRM,
                this.guiLeft + ReincarnationLayout.CONFIRM_X,
                this.guiTop + ReincarnationLayout.CONFIRM_Y,
                ReincarnationLayout.CONFIRM_BUTTON_WIDTH,
                16,
                StatCollector.translateToLocal(KEY_CONFIRM_BUTTON)));
    }

    /** 每帧刷新文案与置灰态的按钮（渲染消费 Container 只读访问器） */
    private static final class StatefulButton extends GuiButton {

        StatefulButton(int id, int x, int y, int width, int height, String label) {
            super(id, x, y, width, height, label);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            refreshState();
            super.drawButton(mc, mouseX, mouseY);
        }

        private void refreshState() {
            GuiContainer gui = (GuiContainer) Minecraft.getMinecraft().currentScreen;
            if (!(gui instanceof ReincarnationGuiContainer)) {
                return;
            }
            ReincarnationContainer container = ((ReincarnationGuiContainer) gui).container();
            if (this.id == ReincarnationLayout.BUTTON_CONFIRM) {
                this.enabled = container.isConfirmAvailable();
                this.displayString = StatCollector
                    .translateToLocal(container.isConfirmArmed() ? KEY_CONFIRM_DIALOG : KEY_CONFIRM_BUTTON);
            } else {
                this.enabled = container.isUnlockAvailable();
                this.displayString = StatCollector.translateToLocal(
                    this.id == ReincarnationLayout.BUTTON_UNLOCK_SHIMMER ? KEY_UNLOCK_SHIMMER : KEY_UNLOCK_NORMAL);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        // C2S 唯一通道：vanilla enchant 包（服务端 ReincarnationContainer.enchantItem 分发）
        this.mc.playerController.sendEnchantPacket(this.inventorySlots.windowId, button.id);
    }

    // ==================== 绘制 ====================

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // 面板底（GuiContainer.drawScreen 已画全屏暗化背景，此处画面板本体，无贴图）
        drawRect(
            this.guiLeft,
            this.guiTop,
            this.guiLeft + ReincarnationLayout.PANEL_WIDTH,
            this.guiTop + ReincarnationLayout.PANEL_HEIGHT,
            0xF0101010);
        drawRect(
            this.guiLeft,
            this.guiTop,
            this.guiLeft + ReincarnationLayout.PANEL_WIDTH,
            this.guiTop + 1,
            0xFF555555);
        // 15 列网格：外壳槽 + 3 物品格（未解锁行/锁定列画暗化格）
        for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT; column++) {
            int x = ReincarnationLayout.GRID_X + column * ReincarnationLayout.SLOT_PITCH;
            drawCell(x, ReincarnationLayout.HULL_Y, container().isColumnLocked(column));
            for (int row = 0; row < ReincarnationCycle.MAX_UNLOCKED_ROWS; row++) {
                drawCell(
                    x,
                    ReincarnationLayout.ITEMS_Y + row * ReincarnationLayout.SLOT_PITCH,
                    container().isColumnLocked(column) || row >= container().getUnlockedRows());
            }
        }
        // 币格
        drawCell(ReincarnationLayout.GRID_X, ReincarnationLayout.ACTION_Y, container().isReadonly());
        // 玩家背包 36 格
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawCell(
                    ReincarnationLayout.PLAYER_INV_X + column * ReincarnationLayout.SLOT_SIZE,
                    ReincarnationLayout.INVENTORY_Y + row * ReincarnationLayout.SLOT_SIZE,
                    false);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawCell(
                ReincarnationLayout.PLAYER_INV_X + column * ReincarnationLayout.SLOT_SIZE,
                ReincarnationLayout.INVENTORY_Y + 58,
                false);
        }
    }

    /** 单个槽位单元（1px 亮框 + 暗底；locked 叠加暗化罩，提示不可交互） */
    private void drawCell(int x, int y, boolean locked) {
        int left = this.guiLeft + x;
        int top = this.guiTop + y;
        drawRect(left, top, left + ReincarnationLayout.SLOT_SIZE, top + ReincarnationLayout.SLOT_SIZE, 0xFF8B8B8B);
        drawRect(left + 1, top + 1, left + 17, top + 17, 0xFF373737);
        if (locked) {
            drawRect(left + 1, top + 1, left + 17, top + 17, 0x66000000);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        ReincarnationContainer container = container();

        // 顶部横幅（状态提示；lang 值自带 § 颜色）
        drawScaledCentered(
            bannerText(container),
            ReincarnationLayout.PANEL_WIDTH / 2,
            ReincarnationLayout.BANNER_Y,
            0xFFFFFF,
            0.8f);
        // 标题
        drawScaledCentered(
            StatCollector.translateToLocal(KEY_TITLE),
            ReincarnationLayout.PANEL_WIDTH / 2,
            ReincarnationLayout.TITLE_Y,
            0xFFFFFF,
            1.0f);

        for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT; column++) {
            int centerX = ReincarnationLayout.GRID_X + column * ReincarnationLayout.SLOT_PITCH
                + ReincarnationLayout.SLOT_SIZE / 2;
            // 外壳进度 n/16（lang 键自带 §e/§7 配色）
            drawScaledCentered(
                StatCollector.translateToLocalFormatted(KEY_PROGRESS, container.getColumnCount(column)),
                centerX,
                ReincarnationLayout.PROGRESS_Y,
                0xFFFFFF,
                0.5f);
            // 列名（已解锁金 / 未解锁深灰，与旧 GUI columnText 一致）
            int color = container.isColumnLocked(column) ? COLUMN_LOCKED_COLOR : COLUMN_UNLOCKED_COLOR;
            drawScaledCentered(
                StatCollector.translateToLocal(KEY_COLUMN_PREFIX + column),
                centerX,
                ReincarnationLayout.LABELS_Y,
                color,
                0.5f);
        }

        // 面板消息行（事件码 + 进度条参数本地派生）
        String message = messageText(container);
        if (!message.isEmpty()) {
            drawScaledCentered(
                message,
                ReincarnationLayout.PANEL_WIDTH / 2,
                ReincarnationLayout.MESSAGE_Y,
                0xFFFFFF,
                0.7f);
        }

        // 底部提示行：忽略 NBT（左）· 混淆加密·绑定玩家（右）
        drawScaledLeft(
            StatCollector.translateToLocal(KEY_IGNORE_NBT),
            4,
            ReincarnationLayout.FOOTER_Y,
            FOOTER_COLOR,
            0.55f);
        String encrypted = StatCollector.translateToLocal(KEY_ENCRYPTED);
        drawScaledLeft(
            encrypted,
            ReincarnationLayout.PANEL_WIDTH - 4 - (int) (this.fontRendererObj.getStringWidth(encrypted) * 0.55f),
            ReincarnationLayout.FOOTER_Y,
            FOOTER_COLOR,
            0.55f);
    }

    /** 横幅文本（DEPOSITED 金 / EXECUTED 红 / IDLE 灰，颜色由 lang 值承载，与旧 bannerText 同构） */
    private static String bannerText(ReincarnationContainer container) {
        int state = container.getStateOrdinal();
        if (state == ReincarnationCycle.CycleState.DEPOSITED.ordinal()) {
            return StatCollector.translateToLocal(KEY_BANNER_DEPOSITED);
        }
        if (state == ReincarnationCycle.CycleState.EXECUTED.ordinal()) {
            return StatCollector.translateToLocal(KEY_BANNER_EXECUTED);
        }
        return StatCollector.translateToLocal(KEY_BANNER_IDLE);
    }

    /**
     * 消息行文本：事件码 → lang 键 + 由进度条实时值派生参数
     * （列事件 = 列名 + 当前 n/16；解锁成功 = 当前行数/3；解锁失败 = 固定概率文案）
     */
    private static String messageText(ReincarnationContainer container) {
        int event = container.getMessageEvent();
        String key = ReincarnationContainer.messageLangKey(event);
        if (key.isEmpty()) {
            return "";
        }
        if (ReincarnationLayout.isColumnUnlockedEvent(event) || ReincarnationLayout.isHullConsumedEvent(event)) {
            int column = ReincarnationLayout.eventColumn(event);
            String columnName = StatCollector.translateToLocal(KEY_COLUMN_PREFIX + column);
            return StatCollector.translateToLocalFormatted(
                key,
                columnName,
                container.getColumnCount(column) + "/" + ReincarnationLayout.HULL_TARGET);
        }
        if (event == ReincarnationLayout.MSG_UNLOCK_SUCCESS) {
            return StatCollector.translateToLocalFormatted(
                key,
                container.getUnlockedRows() + "/" + ReincarnationCycle.MAX_UNLOCKED_ROWS);
        }
        return StatCollector
            .translateToLocalFormatted(key, event == ReincarnationLayout.MSG_UNLOCK_FAIL_SHIMMER ? "0.1%" : "0.001%");
    }

    // ==================== 缩放文本工具（等价旧 MUI2 scale 参数；无阴影与旧 shadow(false) 一致） ====================

    private void drawScaledCentered(String text, int centerX, int y, int color, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX, y, this.zLevel);
        GL11.glScalef(scale, scale, 1.0F);
        int width = this.fontRendererObj.getStringWidth(text);
        this.fontRendererObj.drawString(text, -width / 2, 0, color);
        GL11.glPopMatrix();
    }

    private void drawScaledLeft(String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, this.zLevel);
        GL11.glScalef(scale, scale, 1.0F);
        this.fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    /** 不暂停单机游戏（与旧 MUI2 GUI 打开时不暂停集成服的观感一致） */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
