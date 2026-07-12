package com.miaokatze.gtit.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;

/**
 * 交易显示渲染组件
 * <p>
 * 完美复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.TradeItemDisplayWidget}，
 * 并融合 V2 的 {@code NekoTradeDisplayWidgetV2} 的增强特性。
 * <p>
 * 继承 {@link ItemDisplayWidget}，实现 {@link Interactable}，
 * 支持 TILE（47x25）和 LIST（153x14）双模式渲染。
 * <p>
 * 渲染特性（融合 VM + V2）：
 * <ul>
 * <li><b>背景按钮</b>：根据 pressed 状态切换按下/未按下纹理</li>
 * <li><b>双图标渲染</b>：主图标（产物显示物品）+ 副图标（需求物品/猫猫币，8x8 右下角）</li>
 * <li><b>可交易覆盖层</b>：TILE 模式下 tradeable=true 时绘制绿色边框纹理 OVERLAY_TRADEABLE</li>
 * <li><b>选中覆盖层</b>：selected=true 时绘制 OVERLAY_SELECTED</li>
 * <li><b>冷却覆盖层</b>：不可交易/BQ锁定/冷却中时绘制 OVERLAY_COOLDOWN</li>
 * <li><b>LOCKED 文字</b>：bqLocked=true 时显示金色 "LOCKED" 文字</li>
 * <li><b>冷却文字</b>：cooldownRemaining>0 时显示青色冷却秒数</li>
 * <li><b>收藏星标</b>：favourite=true 时显示 FAVOURITE_SPRITE</li>
 * <li><b>Tooltip</b>：产物列表（青色）、需求列表（绿色）、猫猫币花费、BQ锁定、冷却</li>
 * </ul>
 * <p>
 * 交互特性：
 * <ul>
 * <li><b>Shift+左键</b>：通过 {@link TradeActionCallback#onTradeRequested} 通知交易请求</li>
 * <li><b>Ctrl+左键</b>：通过 {@link TradeActionCallback#onFavouriteToggled} 通知收藏切换</li>
 * <li><b>鼠标悬停</b>：改变按钮 pressed 状态（视觉反馈）</li>
 * </ul>
 * <p>
 * <b>设计要点</b>：使用回调接口而非直接引用 GUI 类，实现解耦。
 * GUI 类实现 {@link TradeActionCallback} 接口，注入到 Widget 中即可接收交互事件。
 *
 * @see NekoTradeItemDisplay
 * @see NekoGuiTextures
 * @see NekoDisplayType
 */
public class NekoTradeItemDisplayWidget extends ItemDisplayWidget implements Interactable {

    // ==================== 尺寸常量 ====================

    /** TILE 模式按钮宽度（与 VM 的 MTEVendingMachineGui.TILE_ITEM_WIDTH 一致） */
    public static final int TILE_ITEM_WIDTH = 47;

    /** TILE 模式按钮高度 */
    public static final int TILE_ITEM_HEIGHT = 25;

    /** LIST 模式按钮宽度 */
    public static final int LIST_ITEM_WIDTH = 153;

    /** LIST 模式按钮高度 */
    public static final int LIST_ITEM_HEIGHT = 14;

    // ==================== 颜色常量（对应 VM 的 ColorUtils） ====================

    /** 普通文字颜色（黑色，RGB） */
    private static final int COLOR_TEXT = 0x000000;

    /** LOCKED 文字颜色（金色，RGB） */
    private static final int COLOR_LOCKED = 0xFFAA00;

    /** 冷却计时文字颜色（青色，RGB） */
    private static final int COLOR_COOLDOWN = 0x00FFFF;

    /** LIST 模式可交易指示条颜色（半透明绿色，ARGB） */
    private static final int COLOR_LIST_TRADEABLE = 0x883CFF00;

    /** LIST 模式不可交易指示条颜色（半透明深灰，ARGB） */
    private static final int COLOR_LIST_UNTRADABLE = 0x88333333;

    /** LIST 模式选中指示条颜色（半透明蓝色，ARGB） */
    private static final int COLOR_LIST_SELECTED = 0xAA039BE5;

    /** 禁用遮罩颜色（半透明黑色，ARGB） */
    private static final int COLOR_DISABLED_OVERLAY = 0xBB000000;

    /**
     * 格式化冷却剩余时间
     * <p>
     * 按最大单位组合显示，规则与 V1 保持一致：
     * <ul>
     * <li>{@code <= 0}：不显示，返回空字符串</li>
     * <li>{@code >= 86400}：显示 "Xd Xh"（天和小时）</li>
     * <li>{@code >= 3600}：显示 "Xh Xm"（小时和分钟）</li>
     * <li>{@code >= 60}：显示 "Xm Xs"（分钟和秒）</li>
     * <li>其他：显示 "Xs"（仅秒）</li>
     * </ul>
     *
     * @param seconds 冷却剩余秒数
     * @return 格式化后的冷却文字
     */
    private static String formatCooldown(long seconds) {
        if (seconds <= 0) {
            return "";
        }
        if (seconds >= 86400) {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return days + "d " + hours + "h";
        }
        if (seconds >= 3600) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
        if (seconds >= 60) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return minutes + "m " + secs + "s";
        }
        return seconds + "s";
    }

    // ==================== 回调接口 ====================

    /**
     * 交易动作回调接口
     * <p>
     * 由 GUI 类实现并注入到 Widget 中，用于接收用户交互事件。
     * 避免 Widget 直接引用 GUI 类，实现解耦。
     */
    public interface TradeActionCallback {

        /**
         * 交易请求回调
         * <p>
         * 当用户 Shift+左键点击交易时触发。
         * GUI 类应在此方法中向服务端发送交易请求。
         *
         * @param display 被点击的交易显示数据
         */
        void onTradeRequested(NekoTradeItemDisplay display);

        /**
         * 收藏切换回调
         * <p>
         * 当用户 Ctrl+左键点击交易时触发。
         * GUI 类应在此方法中切换该交易的收藏状态。
         *
         * @param display 被点击的交易显示数据
         */
        void onFavouriteToggled(NekoTradeItemDisplay display);
    }

    // ==================== 字段 ====================

    /** 交易显示数据源 */
    private NekoTradeItemDisplay display;

    /** 显示模式（TILE 或 LIST） */
    private final NekoDisplayType displayType;

    /** 交易动作回调（可为 null，null 时不响应点击） */
    private TradeActionCallback callback;

    /** 按钮是否处于按下状态（鼠标按下时为 true，释放或离开时为 false） */
    private boolean pressed = false;

    /** 是否被选中（鼠标悬停时由 GUI 设置，用于绘制选中覆盖层） */
    private boolean selected = false;

    /** 副图标（需求物品第一个或猫猫币图标），null 表示无副图标 */
    private ItemStack secondaryIcon;

    /** 主图标 ItemStack（从 display.getDisplayStack() 获取，缓存避免重复计算） */
    private ItemStack displayStack;

    // ==================== 构造器 ====================

    /**
     * 构造交易显示 Widget
     * <p>
     * 根据显示模式设置尺寸和背景纹理，
     * 配置动态 Tooltip，并初始化显示物品和副图标。
     *
     * @param display     交易显示数据（可为 null，后续通过 setDisplay 更新）
     * @param displayType 显示模式（TILE 或 LIST）
     */
    public NekoTradeItemDisplayWidget(NekoTradeItemDisplay display, NekoDisplayType displayType) {
        super();
        this.displayType = displayType;

        // 设置 Widget 主题（与 VM 的 WidgetThemes.THEME_TRADE_BUTTON 对应）
        widgetTheme(NekoWidgetThemes.THEME_TRADE_BUTTON);

        // 根据显示模式设置尺寸和动态背景
        if (displayType == NekoDisplayType.TILE) {
            // TILE 模式：47x25，使用 TILE 按钮纹理
            height(TILE_ITEM_HEIGHT);
            width(TILE_ITEM_WIDTH);
            background(
                new DynamicDrawable(
                    () -> pressed ? NekoGuiTextures.TILE_TRADE_BUTTON_PRESSED
                        : NekoGuiTextures.TILE_TRADE_BUTTON_UNPRESSED));
        } else {
            // LIST 模式：153x14，使用 LIST 按钮纹理
            height(LIST_ITEM_HEIGHT);
            width(LIST_ITEM_WIDTH);
            background(
                new DynamicDrawable(
                    () -> pressed ? NekoGuiTextures.LIST_TRADE_BUTTON_PRESSED
                        : NekoGuiTextures.LIST_TRADE_BUTTON_UNPRESSED));
        }

        // 配置动态 Tooltip（每次显示时重新构建，确保状态最新）
        this.tooltipDynamic(tooltip -> buildTooltip((RichTooltip) tooltip));
        this.tooltipAutoUpdate(true);

        // 初始化显示数据
        setDisplay(display);
    }

    // ==================== 显示数据管理 ====================

    /**
     * 设置交易显示数据
     * <p>
     * 从 display 中提取主图标和副图标，并设置到 ItemDisplayWidget 的同步系统中。
     * display 为 null 时清空所有显示。
     *
     * @param display 交易显示数据（可为 null）
     */
    public void setDisplay(NekoTradeItemDisplay display) {
        this.display = display;

        if (display == null || display.getDisplayItem() == null) {
            // 无显示数据时清空
            this.displayStack = null;
            this.secondaryIcon = null;
            this.item((ItemStack) null);
        } else {
            // 设置主图标
            this.displayStack = display.getDisplayStack();
            this.item(this.displayStack);
            // 计算副图标
            updateSecondaryIcon();
        }
    }

    /**
     * 更新副图标
     * <p>
     * 副图标优先取输入物品列表的第一个物品，
     * 若无输入物品但有猫猫币花费则取猫猫币图标。
     */
    private void updateSecondaryIcon() {
        if (display == null) {
            secondaryIcon = null;
            return;
        }

        // 优先取输入物品第一个
        if (display.hasInputs()) {
            NekoBigItemStack fromItem = display.getInputs()
                .get(0);
            secondaryIcon = fromItem != null ? fromItem.getBaseStack() : null;
        } else if (display.hasCurrencyCost()) {
            // 无输入物品但有猫猫币花费，取猫猫币图标
            secondaryIcon = NekoCurrencyRegistrar.getItemStack(display.getCurrencyId(), 1);
        } else {
            secondaryIcon = null;
        }
    }

    // ==================== 自定义渲染 ====================

    /**
     * 自定义渲染入口
     * <p>
     * 不调用 super.draw()，完全手动渲染（与 VM 的 TradeItemDisplayWidget 一致），
     * 以实现对 TILE/LIST 双模式的完全控制。
     * 背景纹理由框架在 draw() 之前自动绘制。
     *
     * @param context     GUI 渲染上下文
     * @param widgetTheme Widget 主题
     */
    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        // 无显示数据时不渲染
        if (display == null || displayStack == null) {
            return;
        }

        try {
            if (displayType == NekoDisplayType.TILE) {
                drawTileMode(context, widgetTheme);
            } else {
                drawListMode(context, widgetTheme);
            }
        } catch (Throwable t) {
            // 渲染异常不影响游戏运行，仅记录日志
            GTInterestingThing.LOG.error("[NekoGUI] NekoTradeItemDisplayWidget.draw() 渲染异常!", t);
        }
    }

    /**
     * TILE 模式渲染（47x25）
     * <p>
     * 渲染层次（从底到顶）：
     * <ol>
     * <li>可交易覆盖层（OVERLAY_TRADEABLE，绿色边框）</li>
     * <li>选中覆盖层（OVERLAY_SELECTED）</li>
     * <li>数量文字</li>
     * <li>主图标（16x16，位于右侧）</li>
     * <li>冷却覆盖层（OVERLAY_COOLDOWN，不可交易/锁定/冷却时）</li>
     * <li>LOCKED 文字（金色，BQ 锁定时）</li>
     * <li>冷却秒数文字（青色，冷却中且非锁定时）</li>
     * <li>副图标（8x8，右下角，Z+200 确保在最上层）</li>
     * <li>收藏星标（6x6，左上角）</li>
     * </ol>
     *
     * @param context     GUI 渲染上下文
     * @param widgetTheme Widget 主题
     */
    private void drawTileMode(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        float z = context.getCurrentDrawingZ();

        // --- 可交易覆盖层（绿色边框）---
        if (display.isTradeable()) {
            NekoGuiTextures.OVERLAY_TRADEABLE.draw(0, 0, TILE_ITEM_WIDTH, TILE_ITEM_HEIGHT);
        }

        // --- 选中覆盖层 ---
        if (selected) {
            NekoGuiTextures.OVERLAY_SELECTED.draw(0, 0, TILE_ITEM_WIDTH, TILE_ITEM_HEIGHT);
        }

        // --- 数量文字 ---
        int stackSize = display.getDisplayStackSize();
        GuiDraw.drawText(" " + stackSize, 4, 9, 1.0f, COLOR_TEXT, false);

        // --- 主图标（16x16，位于按钮右侧）---
        GuiDraw.drawItem(displayStack, 26, 4, 16, 16, (int) z);

        // --- 冷却覆盖层（仅 BQ 锁定或冷却中时绘制；已解锁但不可交易保持亮色，仅缺绿色边框）---
        boolean showCooldownOverlay = display.isBqLocked() || display.getCooldownRemaining() > 0;
        if (showCooldownOverlay) {
            NekoGuiTextures.OVERLAY_COOLDOWN.draw(0, 0, TILE_ITEM_WIDTH, TILE_ITEM_HEIGHT);
        }

        // --- LOCKED 文字（金色，BQ 锁定时）---
        if (display.isBqLocked()) {
            drawCenteredText("LOCKED", 9, 1.0f, COLOR_LOCKED, TILE_ITEM_WIDTH);
        }

        // --- 冷却秒数文字（青色，冷却中且非锁定时）---
        if (display.getCooldownRemaining() > 0 && !display.isBqLocked()) {
            String cdText = formatCooldown(display.getCooldownRemaining());
            drawCenteredText(cdText, 9, 1.0f, COLOR_COOLDOWN, TILE_ITEM_WIDTH);
        }

        // --- 副图标（8x8，右下角，Z+200 确保在覆盖层之上）---
        if (secondaryIcon != null) {
            GuiDraw.drawItem(secondaryIcon, 35, 13, 8, 8, (int) (z + 200));
        }

        // --- 收藏星标（6x6，左上角）---
        if (display.isFavourite()) {
            NekoGuiTextures.FAVOURITE_SPRITE.draw(context, 4, 4, 6, 6, widgetTheme.getTheme());
        }
    }

    /**
     * LIST 模式渲染（153x14）
     * <p>
     * 渲染层次（从底到顶）：
     * <ol>
     * <li>数量文字（左侧）</li>
     * <li>主图标（9x9，位于数量右侧）</li>
     * <li>产物名称文字（截断到21字符）</li>
     * <li>可交易指示条（左侧3px，绿色/灰色）</li>
     * <li>禁用遮罩（锁定/冷却时，半透明黑色覆盖；已解锁但不可交易保持亮色）</li>
     * <li>选中指示条（左侧2px，蓝色）</li>
     * <li>LOCKED 文字（金色，BQ 锁定时）</li>
     * <li>冷却秒数文字（青色，冷却中且非锁定时）</li>
     * <li>副图标（8x8，右侧）</li>
     * <li>收藏星标（10x10，最右侧）</li>
     * </ol>
     *
     * @param context     GUI 渲染上下文
     * @param widgetTheme Widget 主题
     */
    private void drawListMode(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        float z = context.getCurrentDrawingZ();

        // --- 数量文字 ---
        int stackSize = display.getDisplayStackSize();
        GuiDraw.drawText("" + stackSize, 6, 4, 0.9f, COLOR_TEXT, false);

        // --- 主图标（9x9）---
        GuiDraw.drawItem(displayStack, 24, 2, 9, 9, (int) z);

        // --- 产物名称文字（截断到21字符，超出显示 "..."）---
        String name = display.getDisplayItemName();
        if (name.length() > 21) {
            name = name.substring(0, 21) + "...";
        }
        GuiDraw.drawText(name, 36, 4, 0.9f, COLOR_TEXT, false);

        // --- 可交易指示条（左侧3px宽竖条，绿色=可交易，灰色=不可交易）---
        int barColor = display.isTradeable() ? COLOR_LIST_TRADEABLE : COLOR_LIST_UNTRADABLE;
        GuiDraw.drawRect(1, 1, 3, LIST_ITEM_HEIGHT - 2, barColor);

        // --- 禁用遮罩（仅 BQ 锁定或冷却中时绘制半透明黑色覆盖；已解锁但不可交易保持亮色）---
        boolean showDisabled = display.isBqLocked() || display.getCooldownRemaining() > 0;
        if (showDisabled) {
            GuiDraw.drawRect(1, 1, LIST_ITEM_WIDTH - 2, LIST_ITEM_HEIGHT - 2, COLOR_DISABLED_OVERLAY);
        }

        // --- 选中指示条（左侧2px宽竖条，蓝色）---
        if (selected) {
            GuiDraw.drawRect(1, 1, 2, LIST_ITEM_HEIGHT - 2, COLOR_LIST_SELECTED);
        }

        // --- LOCKED 文字（金色，BQ 锁定时）---
        if (display.isBqLocked()) {
            drawCenteredText("LOCKED", 4, 0.9f, COLOR_LOCKED, LIST_ITEM_WIDTH);
        }

        // --- 冷却秒数文字（青色，冷却中且非锁定时）---
        if (display.getCooldownRemaining() > 0 && !display.isBqLocked()) {
            String cdText = formatCooldown(display.getCooldownRemaining());
            GuiDraw.drawText(cdText, 100, 4, 0.9f, COLOR_COOLDOWN, true);
        }

        // --- 副图标（8x8，右侧，Z+200 确保在最上层）---
        if (secondaryIcon != null) {
            GuiDraw.drawItem(secondaryIcon, 125, 3, 8, 8, (int) (z + 200));
        }

        // --- 收藏星标（10x10，最右侧）---
        if (display.isFavourite()) {
            NekoGuiTextures.FAVOURITE_SPRITE.draw(context, 139, 2, 10, 10, widgetTheme.getTheme());
        }
    }

    /**
     * 绘制居中文字
     * <p>
     * 根据文字宽度和容器宽度计算水平居中位置，使用带阴影的字体渲染。
     *
     * @param text           要渲染的文字
     * @param y              Y 坐标
     * @param scale          字体缩放
     * @param color          文字颜色（RGB）
     * @param containerWidth 容器宽度（用于计算居中位置）
     */
    private void drawCenteredText(String text, int y, float scale, int color, int containerWidth) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null) return;

        // 计算文字宽度（考虑缩放）
        int textWidth = (int) (mc.fontRenderer.getStringWidth(text) * scale);
        int xPos = (containerWidth - textWidth) / 2;

        GuiDraw.drawText(text, xPos, y, scale, color, true);
    }

    // ==================== Tooltip 构建 ====================

    /**
     * 构建交易 Tooltip
     * <p>
     * Tooltip 内容布局：
     * <ol>
     * <li>产物列表（青色）：显示每个产物及其数量</li>
     * <li>空行</li>
     * <li>需求列表（绿色）：猫猫币花费 + 输入物品</li>
     * <li>空行（若有需求）</li>
     * <li>BQ 锁定状态（金色，仅锁定时显示）</li>
     * <li>冷却时间（青色，仅冷却中显示）</li>
     * <li>空行</li>
     * <li>操作提示（灰色）</li>
     * </ol>
     *
     * @param builder tooltip 构建器
     */
    private void buildTooltip(RichTooltip builder) {
        if (display == null) {
            return;
        }

        builder.clearText();

        // --- 产物列表（青色）---
        for (NekoBigItemStack output : display.getOutputs()) {
            if (output == null || output.getBaseStack() == null) continue;
            int amount = output.getStackSize();
            String name = output.getBaseStack()
                .getDisplayName();
            builder.addLine(EnumChatFormatting.AQUA + (amount > 1 ? amount + "x " : "") + name);
        }

        // --- 需求列表（绿色）---
        boolean hasRequirements = display.hasInputs() || display.hasCurrencyCost();
        if (hasRequirements) {
            builder.emptyLine();
            builder.addLine(EnumChatFormatting.DARK_GREEN + "" + EnumChatFormatting.ITALIC + "需求:");

            // 猫猫币花费
            if (display.hasCurrencyCost()) {
                String currencyName = NekoCurrencyRegistrar.getDisplayName(display.getCurrencyId());
                builder.addLine(EnumChatFormatting.DARK_GREEN + "  " + display.getCost() + " " + currencyName);
            }

            // 输入物品需求
            for (NekoBigItemStack input : display.getInputs()) {
                if (input == null || input.getBaseStack() == null) continue;
                int amount = input.getStackSize();
                String name = input.getBaseStack()
                    .getDisplayName();
                String oreDict = input.hasOreDict() ? " (" + input.getOreDict() + ")" : "";
                builder.addLine(EnumChatFormatting.DARK_GREEN + "  " + amount + "x " + name + oreDict);
            }
        }

        // --- BQ 锁定状态（金色）---
        if (display.isBqLocked()) {
            builder.emptyLine();
            builder.addLine(EnumChatFormatting.GOLD + "[BQ锁定] 前置任务未完成");
        }

        // --- 冷却时间（青色）---
        if (display.getCooldownRemaining() > 0) {
            builder.emptyLine();
            builder.addLine(EnumChatFormatting.AQUA + "冷却剩余: " + formatCooldown(display.getCooldownRemaining()));
        }

        // --- 操作提示（灰色）---
        builder.emptyLine();
        builder.addLine(EnumChatFormatting.GRAY + "Shift+点击 执行交易");
        builder.addLine(EnumChatFormatting.GRAY + "Ctrl+点击 收藏/取消收藏");
    }

    // ==================== 鼠标交互 ====================

    /**
     * 鼠标按下处理
     * <p>
     * 交互逻辑（与 VM 的 TradeItemDisplayWidget 一致）：
     * <ul>
     * <li>Shift 和 Ctrl 同时按下或同时未按下：忽略（返回 IGNORE）</li>
     * <li>仅 Shift 按下：触发交易请求回调</li>
     * <li>仅 Ctrl 按下：触发收藏切换回调</li>
     * </ul>
     *
     * @param mouseButton 鼠标按钮（0=左键, 1=右键, 2=中键）
     * @return SUCCESS 表示已处理，IGNORE 表示未处理
     */
    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        // 仅响应左键
        if (mouseButton != 0) {
            return Interactable.Result.IGNORE;
        }
        if (display == null) {
            return Interactable.Result.IGNORE;
        }

        // 检查修饰键状态
        boolean shiftHeld = Interactable.hasShiftDown();
        boolean ctrlHeld = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        // Shift 和 Ctrl 同时按下或同时未按下时忽略
        if (shiftHeld == ctrlHeld) {
            return Interactable.Result.IGNORE;
        }

        // 设置按下状态（视觉反馈）
        pressed = true;

        if (shiftHeld) {
            // Shift+左键：交易请求
            if (callback != null) {
                callback.onTradeRequested(display);
            }
        } else {
            // Ctrl+左键：收藏切换
            if (callback != null) {
                callback.onFavouriteToggled(display);
            }
        }

        return Interactable.Result.SUCCESS;
    }

    /**
     * 鼠标释放处理
     * <p>
     * 释放鼠标按钮时重置按下状态。
     *
     * @param mouseButton 鼠标按钮
     * @return true 表示已处理
     */
    @Override
    public boolean onMouseRelease(int mouseButton) {
        pressed = false;
        return true;
    }

    /**
     * 鼠标离开悬停处理
     * <p>
     * 鼠标离开 Widget 区域时重置按下状态。
     */
    @Override
    public void onMouseEndHover() {
        pressed = false;
        super.onMouseEndHover();
    }

    // ==================== Getter / Setter ====================

    /**
     * 获取交易显示数据
     *
     * @return 显示数据，可能为 null
     */
    public NekoTradeItemDisplay getDisplay() {
        return display;
    }

    /**
     * 获取显示模式
     *
     * @return TILE 或 LIST
     */
    public NekoDisplayType getDisplayType() {
        return displayType;
    }

    /**
     * 设置交易动作回调
     *
     * @param callback 回调实例（可为 null）
     */
    public void setCallback(TradeActionCallback callback) {
        this.callback = callback;
    }

    /**
     * 设置选中状态
     * <p>
     * 由 GUI 层在检测到鼠标悬停时调用，用于绘制选中覆盖层。
     *
     * @param selected true 表示当前被选中（悬停）
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /**
     * 是否被选中
     *
     * @return true 表示被选中
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * 是否处于按下状态
     *
     * @return true 表示按钮被按下
     */
    public boolean isPressed() {
        return pressed;
    }
}
