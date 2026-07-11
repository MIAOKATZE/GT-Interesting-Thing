package com.miaokatze.gtit.common.machine.v2;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.miaokatze.gtit.main.GTInterestingThing;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;
import com.miaokatze.gtit.trade.v2.NekoBigItemStack;
import com.miaokatze.gtit.trade.v2.NekoTrade;
import com.miaokatze.gtit.trade.v2.NekoTradeGroup;

/**
 * V2 交易显示 Widget
 * <p>
 * 继承 {@link ItemDisplayWidget}，在标准物品显示的基础上增加以下视觉效果：
 * <ul>
 * <li>双图标渲染：主图标（产物显示物品）+ 副图标（需求物品或猫猫币，小号显示在右下角）</li>
 * <li>绿色边框：可交易状态时在物品周围绘制绿色边框</li>
 * <li>LOCKED 文字：BQ 前置条件未满足时显示金色 "LOCKED" 文字</li>
 * <li>冷却文字：冷却中时显示青色冷却剩余秒数</li>
 * <li>Tooltip：产物列表（青色）、需求列表（绿色）、猫猫币花费、BQ锁定（金色）、冷却（青色）</li>
 * <li>Shift+Click：向服务端发送交易请求（通过 nekoTradeRequest 同步值）</li>
 * </ul>
 * <p>
 * 每个 Widget 关联一个 {@link NekoTradeGroup} 和交易索引，
 * 从交易数据中提取显示物品和需求物品。
 */
public class NekoTradeDisplayWidgetV2 extends ItemDisplayWidget implements Interactable {

    // ==================== 字段 ====================

    /** 关联的交易组 */
    private final NekoTradeGroup tradeGroup;
    /** 交易在组内的索引 */
    private final int tradeIndex;
    /** BQ 前置条件是否未满足（锁定状态） */
    private boolean bqLocked = false;
    /** 是否可交易（综合判断：非锁定、非冷却中） */
    private boolean tradeable = true;
    /** 冷却剩余时间（秒），0 表示无冷却 */
    private long cooldownRemaining = 0;
    /** 交易请求同步值引用（用于 shift+click 发送交易请求到服务端） */
    private StringSyncValue tradeRequestSync;
    /** 副图标（需求物品第一个或猫猫币图标），null 表示无副图标 */
    private ItemStack secondaryIcon;

    // ==================== 构造器 ====================

    /**
     * 构造交易显示 Widget
     * <p>
     * 从交易组中获取指定索引的交易，设置主图标和副图标，
     * 并配置动态 tooltip。
     *
     * @param group 关联的交易组，不能为 null
     * @param index 交易在组内的索引（0-based）
     */
    public NekoTradeDisplayWidgetV2(NekoTradeGroup group, int index) {
        super();
        this.tradeGroup = group;
        this.tradeIndex = index;
        // 初始化显示物品和副图标
        updateDisplayItem();
        // 配置动态 tooltip（每次显示时重新构建，确保状态最新）
        this.tooltipDynamic(tooltip -> buildTooltip((RichTooltip) tooltip));
        this.tooltipAutoUpdate(true);
    }

    // ==================== 显示物品初始化 ====================

    /**
     * 更新显示物品和副图标
     * <p>
     * 主图标取自交易的 displayItem（产物显示物品），
     * 副图标取自 fromItems 第一个物品（若有）或猫猫币图标（若仅有货币花费）。
     */
    private void updateDisplayItem() {
        if (tradeGroup == null) {
            return;
        }
        List<NekoTrade> trades = tradeGroup.getTrades();
        if (tradeIndex < 0 || tradeIndex >= trades.size()) {
            return;
        }
        NekoTrade trade = trades.get(tradeIndex);

        // 主图标：产物的显示物品
        NekoBigItemStack displayItem = trade.getDisplayItem();
        if (displayItem != null && displayItem.getBaseStack() != null) {
            this.item(displayItem.getBaseStack());
        }

        // 副图标：优先取 fromItems 第一个物品，否则取猫猫币图标
        if (trade.hasFromItems()) {
            NekoBigItemStack fromItem = trade.getFromItems()
                .get(0);
            this.secondaryIcon = fromItem.getBaseStack();
        } else if (trade.hasCurrencyCost()) {
            this.secondaryIcon = NekoCurrencyRegistrar.getItemStack(trade.getCurrencyId(), 1);
        }
    }

    // ==================== Tooltip 构建 ====================

    /**
     * 构建交易 tooltip
     * <p>
     * Tooltip 内容布局：
     * <ol>
     * <li>产物列表（青色）：显示每个产物及其数量</li>
     * <li>空行</li>
     * <li>需求列表（绿色）：猫猫币花费 + 输入物品</li>
     * <li>空行（若有需求）</li>
     * <li>BQ 锁定状态（金色，仅锁定时显示）</li>
     * <li>冷却时间（青色，仅冷却中显示）</li>
     * <li>操作提示（灰色）</li>
     * </ol>
     *
     * @param builder tooltip 构建器
     */
    private void buildTooltip(RichTooltip builder) {
        if (tradeGroup == null) {
            return;
        }
        List<NekoTrade> trades = tradeGroup.getTrades();
        if (tradeIndex < 0 || tradeIndex >= trades.size()) {
            return;
        }
        NekoTrade trade = trades.get(tradeIndex);

        builder.clearText();

        // --- 产物列表（青色）---
        for (NekoBigItemStack toItem : trade.getToItems()) {
            int amount = toItem.getStackSize();
            String name = toItem.getBaseStack()
                .getDisplayName();
            builder.addLine(EnumChatFormatting.AQUA + (amount > 1 ? amount + "x " : "") + name);
        }

        // --- 需求列表（绿色）---
        boolean hasRequirements = trade.hasFromItems() || trade.hasCurrencyCost();
        if (hasRequirements) {
            builder.emptyLine();
            builder.addLine(EnumChatFormatting.DARK_GREEN + "" + EnumChatFormatting.ITALIC + "需求:");

            // 猫猫币花费
            if (trade.hasCurrencyCost()) {
                String currencyName = NekoCurrencyRegistrar.getDisplayName(trade.getCurrencyId());
                builder.addLine(EnumChatFormatting.DARK_GREEN + "  " + trade.getCurrencyCost() + " " + currencyName);
            }

            // 输入物品需求
            for (NekoBigItemStack fromItem : trade.getFromItems()) {
                int amount = fromItem.getStackSize();
                String name = fromItem.getBaseStack()
                    .getDisplayName();
                String oreDict = fromItem.hasOreDict() ? " (" + fromItem.getOreDict() + ")" : "";
                builder.addLine(EnumChatFormatting.DARK_GREEN + "  " + amount + "x " + name + oreDict);
            }
        }

        // --- BQ 锁定状态（金色）---
        if (bqLocked) {
            builder.emptyLine();
            builder.addLine(EnumChatFormatting.GOLD + "[BQ锁定] 前置任务未完成");
        }

        // --- 冷却时间（青色）---
        if (cooldownRemaining > 0) {
            builder.emptyLine();
            builder.addLine(EnumChatFormatting.AQUA + "冷却剩余: " + cooldownRemaining + "秒");
        }

        // --- 操作提示（灰色）---
        builder.emptyLine();
        builder.addLine(EnumChatFormatting.GRAY + "Shift+点击 执行交易");
    }

    // ==================== 自定义渲染 ====================

    /**
     * 自定义渲染方法
     * <p>
     * 在标准物品渲染之后，追加以下视觉元素：
     * <ul>
     * <li>绿色边框：tradeable=true 且 !bqLocked 时绘制</li>
     * <li>LOCKED 文字：bqLocked=true 时绘制（金色）</li>
     * <li>冷却文字：cooldownRemaining>0 时绘制（青色）</li>
     * <li>副图标：右下角小号显示需求物品或猫猫币</li>
     * </ul>
     * 坐标均相对于 Widget 左上角（18x18 区域）。
     *
     * @param context     GUI 渲染上下文
     * @param widgetTheme Widget 主题
     */
    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        // 先调用父类渲染（绘制物品图标和背景）
        super.draw(context, widgetTheme);

        try {
            float z = context.getCurrentDrawingZ();

            // --- 绿色边框（可交易状态）---
            if (tradeable && !bqLocked) {
                int greenColor = 0xFF00CC00; // ARGB 格式的绿色
                // 上、下、左、右四条边框线（各1像素宽）
                GuiDraw.drawRect(0, 0, 18, 1, greenColor);
                GuiDraw.drawRect(0, 17, 18, 1, greenColor);
                GuiDraw.drawRect(0, 0, 1, 18, greenColor);
                GuiDraw.drawRect(17, 0, 1, 18, greenColor);
            }

            // --- LOCKED 文字（BQ 锁定状态，金色）---
            if (bqLocked) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.fontRenderer != null) {
                    // 在物品中心位置绘制 "LOCKED" 文字
                    String lockText = "LOCKED";
                    int textWidth = mc.fontRenderer.getStringWidth(lockText);
                    int xPos = (18 - textWidth) / 2; // 水平居中
                    mc.fontRenderer.drawStringWithShadow(lockText, xPos, 5, 0xFFAA00);
                }
            }

            // --- 冷却文字（青色）---
            if (cooldownRemaining > 0 && !bqLocked) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.fontRenderer != null) {
                    // 显示冷却剩余秒数
                    String cdText = cooldownRemaining + "s";
                    int textWidth = mc.fontRenderer.getStringWidth(cdText);
                    int xPos = (18 - textWidth) / 2; // 水平居中
                    mc.fontRenderer.drawStringWithShadow(cdText, xPos, 5, 0x00FFFF);
                }
            }

            // --- 副图标（右下角小号显示）---
            if (secondaryIcon != null) {
                // 副图标位置：右下角，8x8 像素，Z+200 确保在主图标之上
                GuiDraw.drawItem(secondaryIcon, 10, 6, 8, 8, (int) (z + 200));
            }
        } catch (Throwable t) {
            // 渲染异常不影响游戏运行，仅记录日志
            GTInterestingThing.LOG.error("[NekoVMV2] NekoTradeDisplayWidgetV2.draw() 渲染异常!", t);
        }
    }

    // ==================== 点击处理 ====================

    /**
     * 鼠标点击处理
     * <p>
     * 当玩家 Shift+点击交易图标时，向服务端发送交易请求。
     * 请求格式为 "groupId:tradeIndex"，通过 nekoTradeRequest 同步值传输。
     * 服务端收到请求后调用 {@link MTENekoVendingMachineV2#processTrade} 执行交易。
     *
     * @param mouseButton 鼠标按钮（0=左键, 1=右键）
     * @return SUCCESS 表示已处理，IGNORE 表示未处理
     */
    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        // 仅左键 + Shift 时触发交易
        if (mouseButton == 0 && Interactable.hasShiftDown() && tradeRequestSync != null && tradeGroup != null) {
            // 构建交易请求字符串："groupId:tradeIndex"
            String request = tradeGroup.getId()
                .toString() + ":"
                + tradeIndex;
            // 设置同步值，触发 C2S 同步到服务端
            tradeRequestSync.setValue(request);
            return Interactable.Result.SUCCESS;
        }
        return Interactable.Result.IGNORE;
    }

    // ==================== Setter 方法 ====================

    /**
     * 设置 BQ 锁定状态
     *
     * @param locked true 表示 BQ 前置条件未满足（锁定）
     */
    public void setBqLocked(boolean locked) {
        this.bqLocked = locked;
    }

    /**
     * 设置可交易状态
     *
     * @param tradeable true 表示当前可交易
     */
    public void setTradeable(boolean tradeable) {
        this.tradeable = tradeable;
    }

    /**
     * 设置冷却剩余时间
     *
     * @param seconds 冷却剩余秒数，0 表示无冷却
     */
    public void setCooldownRemaining(long seconds) {
        this.cooldownRemaining = seconds;
    }

    /**
     * 设置交易请求同步值引用
     * <p>
     * 该引用用于 shift+click 时发送交易请求到服务端。
     *
     * @param sync 交易请求的 StringSyncValue
     */
    public void setTradeRequestSync(StringSyncValue sync) {
        this.tradeRequestSync = sync;
    }

    // ==================== Getter 方法 ====================

    /**
     * 获取关联的交易组
     *
     * @return 交易组实例
     */
    public NekoTradeGroup getTradeGroup() {
        return tradeGroup;
    }

    /**
     * 获取交易索引
     *
     * @return 交易在组内的索引
     */
    public int getTradeIndex() {
        return tradeIndex;
    }

    /**
     * 是否处于 BQ 锁定状态
     *
     * @return true 表示锁定
     */
    public boolean isBqLocked() {
        return bqLocked;
    }

    /**
     * 是否可交易
     *
     * @return true 表示可交易
     */
    public boolean isTradeable() {
        return tradeable;
    }

    /**
     * 获取冷却剩余时间
     *
     * @return 冷却剩余秒数
     */
    public long getCooldownRemaining() {
        return cooldownRemaining;
    }
}
