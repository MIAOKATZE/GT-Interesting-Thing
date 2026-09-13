package com.miaokatze.gtit.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtit.client.gui.reincarnation.ReincarnationGuiButton;
import com.miaokatze.gtit.client.gui.reincarnation.ReincarnationGuiDrawing;
import com.miaokatze.gtit.client.gui.reincarnation.ReincarnationGuiPalette;
import com.miaokatze.gtit.client.gui.reincarnation.ReincarnationGuiTextures;
import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;
import com.miaokatze.gtit.reincarnation.gui.ReincarnationContainer;
import com.miaokatze.gtit.reincarnation.gui.ReincarnationHullMatcher;
import com.miaokatze.gtit.reincarnation.gui.ReincarnationLayout;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicHull;

/**
 * 周目轮回 GUI 渲染层（v1.8.8 贴图化接线：消费 plan/gui-texture-v19 贴图契约家族
 * panel.png / atlas.png，几何/交互/网络零变更；仅物理客户端加载）
 * <p>
 * 布局/契约单源在 {@link ReincarnationLayout}（几何、按钮 ID、进度条映射、消息事件码），
 * 会话逻辑在 {@link ReincarnationContainer}（本类只读消费其公开访问器）。
 * <p>
 * <b>绘制</b>：面板整幅与图集小件经 {@code client.gui.reincarnation} 消费层四类
 * （Textures/Palette/Drawing/Button）绘制——panel.png 整幅 Tessellator 直绘、
 * atlas 槽位格/标签条衬/按钮三态/计数角标；文本仍走 {@code FontRenderer} 与既有
 * {@code gtit.reincarnation.*} lang 键（{@link StatCollector}，§ 格式码随键值渲染，
 * 双语由 lang 文件承载）。1.7.10 仅用 {@code org.lwjgl.opengl.GL11}。
 * <p>
 * <b>按钮 C2S 通道</b>：vanilla {@code PlayerControllerMP.sendEnchantPacket(windowId, buttonId)}
 * → C11 → 服务端 {@code NetHandlerPlayServer.processEnchantItem} →
 * {@code ReincarnationContainer.enchantItem}（零自定义 C2S 包，任务包红线）。
 * <p>
 * <b>解锁连掷会话</b>：解锁按钮 = toggle 连掷会话（首按开启、再按停止；激活期间每
 * 服务端 tick 从背包扣 1 枚对应猫猫币掷 1 次），激活位经进度条 {@code BAR_ROLL_ACTIVE}
 * （bit0=闪烁 bit1=普通）下发，按钮文案据此切换为停止态（v1.8.8 接线）。
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

    // ==================== lang 键（既有键 + v1.8.8 新增连掷停止/按钮悬浮键） ====================

    private static final String KEY_PREFIX = "gtit.reincarnation.";
    private static final String KEY_TITLE = KEY_PREFIX + "gui.title";
    private static final String KEY_BANNER_IDLE = KEY_PREFIX + "gui.banner.idle";
    private static final String KEY_BANNER_DEPOSITED = KEY_PREFIX + "gui.banner.deposited";
    private static final String KEY_BANNER_EXECUTED = KEY_PREFIX + "gui.banner.executed";
    private static final String KEY_IGNORE_NBT = KEY_PREFIX + "gui.ignore_nbt";
    private static final String KEY_ENCRYPTED = KEY_PREFIX + "gui.encrypted";
    private static final String KEY_UNLOCK_SHIMMER = KEY_PREFIX + "gui.unlock.shimmer";
    private static final String KEY_UNLOCK_NORMAL = KEY_PREFIX + "gui.unlock.normal";
    /** v1.8.8：连掷会话激活期间的按钮停止文案（bit 驱动，见 refreshState） */
    private static final String KEY_UNLOCK_SHIMMER_STOP = KEY_PREFIX + "gui.unlock.shimmer.stop";
    private static final String KEY_UNLOCK_NORMAL_STOP = KEY_PREFIX + "gui.unlock.normal.stop";
    private static final String KEY_CONFIRM_BUTTON = KEY_PREFIX + "gui.confirm.button";
    private static final String KEY_CONFIRM_DIALOG = KEY_PREFIX + "gui.confirm.dialog";
    private static final String KEY_COLUMN_PREFIX = KEY_PREFIX + "column.";
    private static final String KEY_HULL_TOOLTIP = KEY_PREFIX + "gui.hull.tooltip";
    private static final String KEY_HULL_TOOLTIP_DONE = KEY_PREFIX + "gui.hull.tooltip.done";
    /** v1.8.8：解锁按钮悬浮三段（连掷口径说明 / 概率行 / 剩余可解锁状态行），键值已有、本切片接线 */
    private static final String KEY_UNLOCK_TOOLTIP_LINE = KEY_PREFIX + "gui.unlock.tooltip.line";
    private static final String KEY_UNLOCK_TOOLTIP_SHIMMER = KEY_PREFIX + "gui.unlock.tooltip.shimmer";
    private static final String KEY_UNLOCK_TOOLTIP_NORMAL = KEY_PREFIX + "gui.unlock.tooltip.normal";
    private static final String KEY_UNLOCK_TOOLTIP_COLUMNS = KEY_PREFIX + "gui.unlock.tooltip.columns";
    private static final String KEY_UNLOCK_TOOLTIP_DONE = KEY_PREFIX + "gui.unlock.tooltip.done";
    /** v1.8.8：确认按钮悬浮（按会话状态现算选行），键值已有、本切片接线 */
    private static final String KEY_CONFIRM_TOOLTIP_STATE = KEY_PREFIX + "gui.confirm.tooltip.state";
    private static final String KEY_CONFIRM_TOOLTIP_EMPTY = KEY_PREFIX + "gui.confirm.tooltip.empty";

    /** v1.8.8 连掷会话激活位掩码（bit0=闪烁 bit1=普通；编码唯一单源见 BAR_ROLL_ACTIVE javadoc） */
    private static final int ROLL_ACTIVE_SHIMMER_BIT = 1 << 0;
    /** v1.8.8 连掷会话激活位掩码（bit1=普通；编码唯一单源见 BAR_ROLL_ACTIVE javadoc） */
    private static final int ROLL_ACTIVE_NORMAL_BIT = 1 << 1;

    /** n=0 档外壳虚影 alpha（vanilla ghost slot 风格的固定浅淡预览） */
    private static final float HULL_GHOST_EMPTY_ALPHA = 0.40F;
    /** 虚影遮罩淡出底色（面板底色 0x1C1C22；遮罩 alpha = 1-虚影目标 alpha，复现浅淡/渐进观感） */
    private static final int GHOST_FADE_COLOR = 0x1C1C22;
    /** 锁定列灰化罩（半透明浅灰压饱和；真灰化承载层，满 16 服务端解锁后同帧消失） */
    private static final int GHOST_LOCKED_VEIL = 0x99B4B4B4;
    /**
     * 外壳虚影遮罩 zLevel。承载原语必须是 z-aware 的 drawGradientRect（1.7.10
     * Gui.drawRect 是 static 且四顶点 z 硬编码 0——会被前台层深度测试与面板打平
     * 丢弃，一像素都不画）。区间取 (图标 200, 光标持物 250) 开区间中值：图标经
     * renderItemAndEffectIntoGUI 实际绘制于 itemRender.zLevel(150)+内部 50 ≈ 200；
     * vanilla 光标持物堆在前景层之后以 ≈250 渲染，遮罩须低于它避免等深裁切。
     */
    private static final float GHOST_OVERLAY_ZLEVEL = 230.0F;
    /** 外壳计数角标整体 zLevel（高于遮罩 230、低于光标持物 250；tooltip 的 300 由 drawHoveringText 自管） */
    private static final float HULL_COUNTER_ZLEVEL = 240.0F;
    /**
     * 角标计数文字 zLevel：必须严格高于 chip 贴图（v1.8.11 实机修复——前景层深度测试
     * 开启（GL_LESS），文字与 chip 同 z=240 时等深 fragment 被整片裁除，chip 成"有底无字"；
     * +1 抬升至 241 仍在光标持物 250 之下）。
     */
    private static final float HULL_COUNTER_TEXT_ZLEVEL = HULL_COUNTER_ZLEVEL + 1.0F;
    /** 确认按钮高（布局定值 16，ReincarnationLayout 侧未单列常量；initGui 与悬浮命中区共用） */
    private static final int CONFIRM_BUTTON_HEIGHT = 16;

    /**
     * 外壳虚影图标缓存（按列下标；{@link #HULL_GHOST_RESOLVED} 置位后 cache 为 null 即
     * 解析失败负缓存，防逐帧重扫注册表）。仅客户端主线程访问。
     */
    private static final ItemStack[] HULL_GHOST_CACHE = new ItemStack[ReincarnationCycle.COLUMN_COUNT];
    /** 虚影解析完成标记（含失败负缓存） */
    private static final boolean[] HULL_GHOST_RESOLVED = new boolean[ReincarnationCycle.COLUMN_COUNT];

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
                StatCollector.translateToLocal(KEY_UNLOCK_SHIMMER),
                ReincarnationGuiTextures.BTN_UNLOCK_NORMAL_V,
                ReincarnationGuiTextures.BTN_UNLOCK_HOVER_V,
                ReincarnationGuiTextures.BTN_UNLOCK_DISABLED_V));
        this.buttonList.add(
            new StatefulButton(
                ReincarnationLayout.BUTTON_UNLOCK_NORMAL,
                this.guiLeft + ReincarnationLayout.UNLOCK_NORMAL_X,
                this.guiTop + ReincarnationLayout.ACTION_Y,
                ReincarnationLayout.UNLOCK_BUTTON_WIDTH,
                ReincarnationLayout.SLOT_SIZE,
                StatCollector.translateToLocal(KEY_UNLOCK_NORMAL),
                ReincarnationGuiTextures.BTN_UNLOCK_NORMAL_V,
                ReincarnationGuiTextures.BTN_UNLOCK_HOVER_V,
                ReincarnationGuiTextures.BTN_UNLOCK_DISABLED_V));
        this.buttonList.add(
            new StatefulButton(
                ReincarnationLayout.BUTTON_CONFIRM,
                this.guiLeft + ReincarnationLayout.CONFIRM_X,
                this.guiTop + ReincarnationLayout.CONFIRM_Y,
                ReincarnationLayout.CONFIRM_BUTTON_WIDTH,
                CONFIRM_BUTTON_HEIGHT,
                StatCollector.translateToLocal(KEY_CONFIRM_BUTTON),
                ReincarnationGuiTextures.BTN_CONFIRM_NORMAL_V,
                ReincarnationGuiTextures.BTN_CONFIRM_HOVER_V,
                ReincarnationGuiTextures.BTN_CONFIRM_DISABLED_V));
    }

    /**
     * 每帧刷新文案与置灰态的按钮（渲染消费 Container 只读访问器）。
     * v1.8.8：皮肤换 {@link ReincarnationGuiButton} 三态贴图（尺寸/hover 判定/点击结果
     * /文字居中口径不变）；连掷会话激活位驱动解锁按钮停止文案。
     */
    private static final class StatefulButton extends ReincarnationGuiButton {

        StatefulButton(int id, int x, int y, int width, int height, String label, int normalV, int hoverV,
            int disabledV) {
            super(id, x, y, width, height, label, normalV, hoverV, disabledV);
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
                int rollBits = container.getRollActiveBits();
                if (this.id == ReincarnationLayout.BUTTON_UNLOCK_SHIMMER) {
                    this.displayString = StatCollector.translateToLocal(
                        (rollBits & ROLL_ACTIVE_SHIMMER_BIT) != 0 ? KEY_UNLOCK_SHIMMER_STOP : KEY_UNLOCK_SHIMMER);
                } else {
                    this.displayString = StatCollector.translateToLocal(
                        (rollBits & ROLL_ACTIVE_NORMAL_BIT) != 0 ? KEY_UNLOCK_NORMAL_STOP : KEY_UNLOCK_NORMAL);
                }
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
        // 面板整幅（GuiContainer.drawScreen 已画全屏暗化背景；v1.8.8 契约 §4-1：panel.png
        // 金纹雕边框 + 烘焙猫耳/金分隔线/footer 带/爪印，Tessellator 直绘替代旧纯色底+顶描边）
        ReincarnationGuiDrawing.drawPanel(this.guiLeft, this.guiTop);
        // v1.8.8 装饰（契约 §4-3 / §6-A7）：列名标签条衬 nine-slice（几何取自布局常量：
        // 宽 = 15 列 × 20 列距 - 2 间隙 = 298，与网格总宽同源；高 10 = STRIP_H）
        ReincarnationGuiDrawing.bindTexture(ReincarnationGuiTextures.RL_ATLAS);
        ReincarnationGuiDrawing.drawNineSlice(
            this,
            ReincarnationGuiTextures.STRIP_U,
            ReincarnationGuiTextures.STRIP_V,
            ReincarnationGuiTextures.STRIP_W,
            ReincarnationGuiTextures.STRIP_H,
            ReincarnationGuiTextures.STRIP_SLICE,
            this.guiLeft + ReincarnationLayout.GRID_X,
            this.guiTop + ReincarnationLayout.LABELS_Y,
            ReincarnationCycle.COLUMN_COUNT * ReincarnationLayout.SLOT_PITCH - 2,
            ReincarnationGuiTextures.STRIP_H);
        // 15 列网格：外壳槽 + 3 物品格（未解锁行/锁定列画灰化格）；
        // v1.8.8 契约 §4-2/§6-A5：图集 slot_frame / slot_frame_locked（几何/循环零变更）
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
        // （币格绘制已随连掷重构摘除——动作行仅剩两解锁按钮，原位不动，币格位置留白不补绘）
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

    /**
     * 单个槽位单元（v1.8.8 契约 §4-2/§6-A5）：atlas slot_frame 金沿凹格；locked 用
     * slot_frame_locked 灰化框承载（旧半透明叠罩随贴图化删除）。18px 槽位、命中区、
     * Slot 坐标零变更；调用方须已 bind {@link ReincarnationGuiTextures#RL_ATLAS}。
     */
    private void drawCell(int x, int y, boolean locked) {
        int left = this.guiLeft + x;
        int top = this.guiTop + y;
        if (locked) {
            this.drawTexturedModalRect(
                left,
                top,
                ReincarnationGuiTextures.SLOT_LOCKED_U,
                ReincarnationGuiTextures.SLOT_LOCKED_V,
                ReincarnationLayout.SLOT_SIZE,
                ReincarnationLayout.SLOT_SIZE);
        } else {
            this.drawTexturedModalRect(
                left,
                top,
                ReincarnationGuiTextures.SLOT_FRAME_U,
                ReincarnationGuiTextures.SLOT_FRAME_V,
                ReincarnationLayout.SLOT_SIZE,
                ReincarnationLayout.SLOT_SIZE);
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
            ReincarnationGuiPalette.TEXT_WARM,
            0.8f);
        // 标题
        drawScaledCentered(
            StatCollector.translateToLocal(KEY_TITLE),
            ReincarnationLayout.PANEL_WIDTH / 2,
            ReincarnationLayout.TITLE_Y,
            ReincarnationGuiPalette.TEXT_WARM,
            1.0f);

        for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT; column++) {
            int centerX = ReincarnationLayout.GRID_X + column * ReincarnationLayout.SLOT_PITCH
                + ReincarnationLayout.SLOT_SIZE / 2;
            // 列名（已解锁猫金 / 未解锁暖灰；v1.8.8 §5 新旧色映射，色值单源 Palette）
            int color = container.isColumnLocked(column) ? ReincarnationGuiPalette.TEXT_LOCKED_WARM
                : ReincarnationGuiPalette.TEXT_ACCENT;
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
                ReincarnationGuiPalette.TEXT_WARM,
                0.7f);
        }

        // 底部提示行：忽略 NBT（左）· 混淆加密·绑定玩家（右）
        drawScaledLeft(
            StatCollector.translateToLocal(KEY_IGNORE_NBT),
            4,
            ReincarnationLayout.FOOTER_Y,
            ReincarnationGuiPalette.TEXT_MUTED_WARM,
            0.55f);
        String encrypted = StatCollector.translateToLocal(KEY_ENCRYPTED);
        drawScaledLeft(
            encrypted,
            ReincarnationLayout.PANEL_WIDTH - 4 - (int) (this.fontRendererObj.getStringWidth(encrypted) * 0.55f),
            ReincarnationLayout.FOOTER_Y,
            ReincarnationGuiPalette.TEXT_MUTED_WARM,
            0.55f);

        // D2：外壳进度虚影（外壳槽格内叠画：0 档 0.40 浅淡预览 / 1..15 n/16 渐进 / 16 实影；
        // v1.8.8 锁定列深灰乘色真灰化——未解锁=灰、解锁后=彩色）
        drawHullGhosts(container);
        // v1.8.8：外壳计数角标（插在虚影之后、tooltip 之前；z=240 后画覆盖图标 ≈200 与遮罩 230）
        drawHullCounters(container);
        // D4：hover tooltip（外壳槽 / 解锁与确认按钮悬浮说明；仅覆盖对应命中区）。
        // 1.7.10 前台层实收屏幕绝对鼠标坐标（GuiContainer.drawScreen 于 glTranslatef(guiLeft,guiTop)
        // 之后传入原始 mouseX/mouseY，并不扣 gui 原点）——命中判定与 tooltip 锚点统一在此
        // 换算为面板相对坐标（tooltip 经前台层平移空间绘制，相对坐标即落在鼠标处）。
        drawTooltips(container, mouseX - this.guiLeft, mouseY - this.guiTop);
    }

    /**
     * 外壳进度虚影（D2）：按列进度 n 叠画该档外壳图标——0 固定
     * {@link #HULL_GHOST_EMPTY_ALPHA}（vanilla ghost slot 风浅淡预览）/ 1..15 渐进
     * {@code alpha = n/16.0F} / 16 实影。图标按 {@link ReincarnationHullMatcher} 同口径
     * 反向构造（列 0 = 镀铜砖块；列 1..14 = 对应 tier 的 {@link MTEBasicHull}，
     * 经 {@code GregTechAPI.METATILEENTITIES} 单次线性扫描 + 负缓存）。
     * <p>
     * v1.8.8a 修复（实机回归）：{@code RenderItem.renderItemIntoGUI} 在 renderWithColor
     * 分支用物品自身颜色 {@code glColor4f(f3,f4,f, 1.0F)} 覆写当前 GL 色（alpha 亦强制
     * 1.0），glColor 乘色/透明路线对本原语无效——虚影透明度与锁定灰化改由「图标上层
     * 遮罩」承载：①面板底色遮罩 alpha=1-alpha 复现浅淡/渐进（满 16 无遮罩=实影）；
     * ②锁定列叠加 {@link #GHOST_LOCKED_VEIL} 灰罩（真灰化，未解锁=灰、解锁后=彩色）。
     * GL 纪律：blend/blendFunc/color 保存恢复，itemRender.zLevel 与 Gui.zLevel 显式
     * 设定与还原，不污染同层后续渲染。
     */
    private void drawHullGhosts(ReincarnationContainer container) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float savedItemZ = this.itemRender.zLevel;
        float savedGuiZ = this.zLevel;
        this.itemRender.zLevel = 150.0F;
        try {
            for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT; column++) {
                ItemStack ghost = hullGhostIcon(column);
                if (ghost == null) {
                    continue;
                }
                int count = container.getColumnCount(column);
                float alpha = count <= 0 ? HULL_GHOST_EMPTY_ALPHA
                    : Math.min(1.0F, count / (float) ReincarnationLayout.HULL_TARGET);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                int iconX = ReincarnationLayout.GRID_X + column * ReincarnationLayout.SLOT_PITCH + 1;
                int iconY = ReincarnationLayout.HULL_Y + 1;
                this.itemRender
                    .renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), ghost, iconX, iconY);
                // 图标上层遮罩：承载原语必须 z-aware——1.7.10 Gui.drawRect 为 static 且
                // 四顶点 z 硬编码 0（深度测试下与面板打平、被图标 ≈200 压制，一像素不画），
                // 故用 drawGradientRect（读 this.zLevel，同色两次即平面遮罩，顶点色承载 alpha）：
                // ①透明度衰减 ②锁定灰化。drawGradientRect 退出会关 blend，补开供后续列使用。
                this.zLevel = GHOST_OVERLAY_ZLEVEL;
                if (alpha < 1.0F - 1.0E-3F) {
                    int fadeAlpha = (int) ((1.0F - alpha) * 255.0F);
                    int fade = (fadeAlpha << 24) | GHOST_FADE_COLOR;
                    drawGradientRect(iconX, iconY, iconX + 16, iconY + 16, fade, fade);
                }
                if (container.isColumnLocked(column)) {
                    drawGradientRect(iconX, iconY, iconX + 16, iconY + 16, GHOST_LOCKED_VEIL, GHOST_LOCKED_VEIL);
                }
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                this.zLevel = savedGuiZ;
            }
        } finally {
            this.itemRender.zLevel = savedItemZ;
            this.zLevel = savedGuiZ;
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    /**
     * 解析某列的虚影图标（懒解析 + 负缓存；解析失败返回 null 不再重试）。
     * 口径与 {@link ReincarnationHullMatcher#matchesHull} 反向一致：
     * 列 0 = {@code GregTechAPI.sBlockCasings1} meta {@value ReincarnationHullMatcher#STEAM_CASING_META}；
     * 列 1..14 = 注册表中首个 {@code mTier == column} 的 {@link MTEBasicHull} 经
     * {@code getStackForm(1L)}（仓内先例 GTITRecipes.java:127-128）。
     */
    private static ItemStack hullGhostIcon(int column) {
        if (HULL_GHOST_RESOLVED[column]) {
            return HULL_GHOST_CACHE[column];
        }
        HULL_GHOST_RESOLVED[column] = true;
        ItemStack resolved = null;
        try {
            if (column == 0) {
                if (GregTechAPI.sBlockCasings1 != null) {
                    resolved = new ItemStack(GregTechAPI.sBlockCasings1, 1, ReincarnationHullMatcher.STEAM_CASING_META);
                }
            } else {
                IMetaTileEntity[] entries = GregTechAPI.METATILEENTITIES;
                if (entries != null) {
                    for (IMetaTileEntity mte : entries) {
                        if (mte instanceof MTEBasicHull && ((MTEBasicHull) mte).mTier == column) {
                            resolved = mte.getStackForm(1L);
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            resolved = null; // 注册表异常按未解析处理（不崩 GUI）
        }
        HULL_GHOST_CACHE[column] = resolved;
        return resolved;
    }

    /**
     * 外壳计数角标（v1.8.8 新增）：每列进度 n（进度条镜像，与虚影同源 getColumnCount），
     * {@code 0 < n < 16} 时在该列槽位右上角叠画 atlas chip_count 半透明底衬（12×10）+
     * 计数数字（glScalef 0.5，暖白带阴影，chip 内居中写 n）。n=0 不画；满 16 列不画
     * （服务端已解锁，灰化同帧消失）。n/16 完整口径仍由既有外壳槽 hover tooltip 承载。
     * <p>
     * 调用序：本方法插在 {@link #drawHullGhosts}（图标 ≈200/遮罩 230）之后、hover tooltip
     * 之前，角标整体 z={@link #HULL_COUNTER_ZLEVEL}=240（低于光标持物 250、高于遮罩）。
     * GL 纪律：chip 含 α&lt;255 像素，显式 enable GL_BLEND SRC_ALPHA/ONE_MINUS_SRC_ALPHA，
     * 退出经 glPopAttrib 成对恢复；文字层 push/scale/pop 逐对出现。
     */
    private void drawHullCounters(ReincarnationContainer container) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float savedZLevel = this.zLevel;
        this.zLevel = HULL_COUNTER_ZLEVEL;
        try {
            for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT; column++) {
                int count = container.getColumnCount(column);
                if (count <= 0 || count >= ReincarnationLayout.HULL_TARGET) {
                    continue;
                }
                int chipX = ReincarnationLayout.GRID_X + column * ReincarnationLayout.SLOT_PITCH
                    + ReincarnationLayout.SLOT_SIZE
                    - ReincarnationGuiTextures.CHIP_W;
                int chipY = ReincarnationLayout.HULL_Y;
                ReincarnationGuiDrawing.bindTexture(ReincarnationGuiTextures.RL_ATLAS);
                ReincarnationGuiDrawing.drawAtlasRegion(
                    this,
                    chipX,
                    chipY,
                    ReincarnationGuiTextures.CHIP_U,
                    ReincarnationGuiTextures.CHIP_V,
                    ReincarnationGuiTextures.CHIP_W,
                    ReincarnationGuiTextures.CHIP_H);
                String text = String.valueOf(count);
                GL11.glPushMatrix();
                GL11.glTranslatef(
                    chipX + ReincarnationGuiTextures.CHIP_W / 2.0F,
                    chipY + ReincarnationGuiTextures.CHIP_H / 2.0F,
                    HULL_COUNTER_TEXT_ZLEVEL);
                GL11.glScalef(0.5F, 0.5F, 1.0F);
                this.fontRendererObj.drawStringWithShadow(
                    text,
                    -this.fontRendererObj.getStringWidth(text) / 2,
                    -4,
                    ReincarnationGuiPalette.TEXT_WARM);
                GL11.glPopMatrix();
            }
        } finally {
            this.zLevel = savedZLevel;
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    /**
     * hover tooltip（D4 + v1.8.8 按钮悬浮增补）：外壳槽 → {@code gui.hull.tooltip}
     * （列名 + n/16）或已解锁列 {@code gui.hull.tooltip.done}（列名）；两解锁按钮 →
     * 连掷口径说明 + 对应币种概率行 + 剩余可解锁状态行（按列解锁态现算）；确认按钮 →
     * {@code gui.confirm.tooltip.state / .empty}（按会话状态现算选行，可用态不画）。
     * 命中区：外壳槽仅覆盖 18px 槽位（tooltip 不外溢整行）；按钮覆盖布局常量定值的
     * 整钮矩形。鼠标不在任何目标内不绘制。入参为面板相对坐标（v1.8.8a 修复：
     * 1.7.10 前台层原始入参是屏幕绝对坐标，已在 drawGuiContainerForegroundLayer
     * 入口扣 guiLeft/guiTop 换算；tooltip 绘制于前台层平移空间，相对坐标即锚定鼠标）。
     */
    private void drawTooltips(ReincarnationContainer container, int mouseX, int mouseY) {
        int hullColumn = hitHullColumn(mouseX, mouseY);
        if (hullColumn >= 0) {
            List<String> lines;
            if (container.isColumnLocked(hullColumn)) {
                lines = Collections.singletonList(
                    StatCollector.translateToLocalFormatted(
                        KEY_HULL_TOOLTIP,
                        StatCollector.translateToLocal(KEY_COLUMN_PREFIX + hullColumn),
                        container.getColumnCount(hullColumn) + "/" + ReincarnationLayout.HULL_TARGET));
            } else {
                lines = Collections.singletonList(
                    StatCollector.translateToLocalFormatted(
                        KEY_HULL_TOOLTIP_DONE,
                        StatCollector.translateToLocal(KEY_COLUMN_PREFIX + hullColumn)));
            }
            drawHoveringText(lines, mouseX, mouseY, this.fontRendererObj);
            return;
        }
        // v1.8.8 按钮悬浮增补：解锁按钮（整钮 136×18 命中区，布局常量取值）与确认按钮（150×16）
        if (hitRect(
            mouseX,
            mouseY,
            ReincarnationLayout.UNLOCK_SHIMMER_X,
            ReincarnationLayout.ACTION_Y,
            ReincarnationLayout.UNLOCK_BUTTON_WIDTH,
            ReincarnationLayout.SLOT_SIZE)) {
            drawHoveringText(unlockTooltipLines(container, true), mouseX, mouseY, this.fontRendererObj);
            return;
        }
        if (hitRect(
            mouseX,
            mouseY,
            ReincarnationLayout.UNLOCK_NORMAL_X,
            ReincarnationLayout.ACTION_Y,
            ReincarnationLayout.UNLOCK_BUTTON_WIDTH,
            ReincarnationLayout.SLOT_SIZE)) {
            drawHoveringText(unlockTooltipLines(container, false), mouseX, mouseY, this.fontRendererObj);
            return;
        }
        if (hitRect(
            mouseX,
            mouseY,
            ReincarnationLayout.CONFIRM_X,
            ReincarnationLayout.CONFIRM_Y,
            ReincarnationLayout.CONFIRM_BUTTON_WIDTH,
            CONFIRM_BUTTON_HEIGHT)) {
            String line = confirmTooltipLine(container);
            if (line != null) {
                drawHoveringText(Collections.singletonList(line), mouseX, mouseY, this.fontRendererObj);
            }
        }
    }

    /**
     * 解锁按钮悬浮行（v1.8.8）：连掷口径说明（translateToLocal 直读，单 % 约定）→
     * 对应币种概率行（%% 经格式化路径转 %）→ 剩余可解锁状态行（现算：3 行全满 → done；
     * 尚有锁定列 → columns（参数 = HULL_TARGET）；否则不追加第三行）。
     */
    private List<String> unlockTooltipLines(ReincarnationContainer container, boolean shimmer) {
        List<String> lines = new ArrayList<String>(3);
        lines.add(StatCollector.translateToLocal(KEY_UNLOCK_TOOLTIP_LINE));
        lines.add(
            StatCollector.translateToLocalFormatted(shimmer ? KEY_UNLOCK_TOOLTIP_SHIMMER : KEY_UNLOCK_TOOLTIP_NORMAL));
        if (container.getUnlockedRows() >= ReincarnationCycle.MAX_UNLOCKED_ROWS) {
            lines.add(StatCollector.translateToLocal(KEY_UNLOCK_TOOLTIP_DONE));
        } else {
            for (int column = 0; column < ReincarnationCycle.COLUMN_COUNT; column++) {
                if (container.isColumnLocked(column)) {
                    lines.add(
                        StatCollector
                            .translateToLocalFormatted(KEY_UNLOCK_TOOLTIP_COLUMNS, ReincarnationLayout.HULL_TARGET));
                    break;
                }
            }
        }
        return lines;
    }

    /**
     * 确认按钮悬浮行（v1.8.8）：非 DEPOSITED → state 行；DEPOSITED 但寄存清单空 →
     * empty 行；可用态（两条件皆过）无既定文案，返回 null 不画。
     */
    private String confirmTooltipLine(ReincarnationContainer container) {
        if (container.getStateOrdinal() != ReincarnationCycle.CycleState.DEPOSITED.ordinal()) {
            return StatCollector.translateToLocal(KEY_CONFIRM_TOOLTIP_STATE);
        }
        if (container.getPendingCount() <= 0) {
            return StatCollector.translateToLocal(KEY_CONFIRM_TOOLTIP_EMPTY);
        }
        return null;
    }

    /** 鼠标是否命中 gui 相对矩形（左闭右开；v1.8.8 按钮悬浮命中区专用，口径与槽位命中一致） */
    private static boolean hitRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** 鼠标是否命中外壳槽行（返回列下标；未命中返回 -1；gui 相对坐标，仅 18px 槽位框） */
    private static int hitHullColumn(int mouseX, int mouseY) {
        if (mouseY < ReincarnationLayout.HULL_Y
            || mouseY >= ReincarnationLayout.HULL_Y + ReincarnationLayout.SLOT_SIZE) {
            return -1;
        }
        int offsetX = mouseX - ReincarnationLayout.GRID_X;
        if (offsetX < 0) {
            return -1;
        }
        int column = offsetX / ReincarnationLayout.SLOT_PITCH;
        if (column >= ReincarnationCycle.COLUMN_COUNT
            || offsetX - column * ReincarnationLayout.SLOT_PITCH >= ReincarnationLayout.SLOT_SIZE) {
            return -1;
        }
        return column;
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
