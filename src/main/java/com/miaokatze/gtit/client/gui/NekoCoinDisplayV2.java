package com.miaokatze.gtit.client.gui;

import java.util.function.IntSupplier;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtit.trade.NekoCurrencyRegistrar;

/**
 * V2 猫猫币余额显示组件（带 serp 缓动动画和弹出按钮）
 * <p>
 * 复刻 VM {@code CoinDisplay} 的精华（serp 缓动动画）+ V1 {@code NekoCoinDisplay} 的简洁结构，
 * 完全脱离 VM 依赖，使用 GTIT 本地组件。
 * <p>
 * 组件结构（从左到右）：硬币图标(ItemStack, 22px) + 余额数字(带动画) + 弹出按钮(EJECT_COINS, 12px)
 * <p>
 * <b>与 V1 NekoCoinDisplay 的差异</b>：
 * <ul>
 * <li>增加 serp 缓动动画：余额变化时数字缩放（0.8→0.9 增加 / 0.8→0.7 减少），200ms 动画时间</li>
 * <li>弹出按钮需 Shift 才能点击（参考 VM CoinButton，防止误触弹出硬币）</li>
 * <li>tooltip 动态显示实时余额和货币名称（V1 为静态文本）</li>
 * <li>使用 {@link NekoGuiTextures#EJECT_COINS} 替代 VM {@code GuiTextures.EJECT_COINS}</li>
 * </ul>
 * <p>
 * <b>与 VM CoinDisplay 的差异</b>：
 * <ul>
 * <li>VM 有多面额硬币图标（1/10/100/1000/10000），V2 用单一 ItemStack 图标（简洁，V2 无多面额纹理资源）</li>
 * <li>VM 有团队余额(coinSyncValueMe)合并显示，V2 仅显示个人余额（V2 架构简化）</li>
 * <li>VM 有基于 meCoins 的颜色变化，V2 省略（简化，无团队余额概念）</li>
 * <li>保留 VM 的 serp 缓动动画（精华）和可读字符串（10000→10K）</li>
 * <li>VM 的 Shift 检测通过 {@code panel.shiftHeld}，V2 通过 {@link Keyboard#isKeyDown} 实时检测（解耦，无需 Panel 参数）</li>
 * </ul>
 *
 * @see NekoGuiTextures#EJECT_COINS
 * @see NekoCurrencyRegistrar#getItemStack(String, int)
 */
public class NekoCoinDisplayV2 extends Flow {

    /** 余额变化动画时长（毫秒），复刻 VM CoinDisplay.COIN_ANIM_TIME */
    private static final int COIN_ANIM_TIME = 200;
    /** 余额数字默认缩放，复刻 VM CoinDisplay.DEFAULT_AMOUNT_SCALE */
    private static final float DEFAULT_AMOUNT_SCALE = 0.8f;
    /** 余额变化时缩放偏移量，复刻 VM CoinDisplay.AMOUNT_SCALE_OFFSET（增加 +0.1，减少 -0.1） */
    private static final float AMOUNT_SCALE_OFFSET = 0.1f;

    /** 余额同步值，通过 syncHandler {@code "nekoCoinAmount_" + currencyId} 同步 */
    private final IntSyncValue coinSyncValue;
    /** 余额数字文本组件（scale 会被 serp 动画动态修改） */
    private final TextWidget<?> coinAmount;
    /** 弹出硬币按钮（需 Shift 才能点击，使用普通 ButtonWidget 替代 ToggleButton） */
    private final ButtonWidget<?> ejectButton;

    /** 上一次的余额值（用于检测变化），-1 表示未初始化 */
    private int oldCoinValue = -1;
    /** 上次余额变化的时间戳（毫秒），-1 表示尚未发生余额变化 */
    private long lastCoinChange = -1;
    /** 上次余额变化方向：true=增加，false=减少（决定动画缩放方向） */
    private boolean coinIncreased;

    /**
     * ME 网络货币余额查询器（阶段 6）
     * <p>
     * 由 GUI 层通过 {@link #setMeAmountSupplier} 注入，用于在 tooltip 中显示
     * ME 网络中的猫猫币数量。为 null 时不显示 ME 余额行。
     */
    private IntSupplier meAmountSupplier;

    /**
     * 构造 V2 猫猫币显示组件
     *
     * @param syncManager 同步管理器（用于查找 IntSyncValue 和注册弹出按钮 syncHandler）
     * @param currencyId  猫猫币 ID（如 "neko" 或 "shimmeringNeko"）
     * @param displayName 显示名称（如"猫猫币"，用于 tooltip 展示）
     */
    public NekoCoinDisplayV2(PanelSyncManager syncManager, String currencyId, String displayName) {
        super(GuiAxis.X);

        // 查找余额同步值（服务端通过 "nekoCoinAmount_" + currencyId 注册 IntSyncValue）
        this.coinSyncValue = (IntSyncValue) syncManager
            .findSyncHandler("nekoCoinAmount_" + currencyId, 0, IntSyncValue.class);

        // ==================== 硬币图标 ====================
        // 使用 NekoCurrencyRegistrar 获取硬币 ItemStack（V1 方式，替代 VM 的多面额纹理图标）
        ItemStack coinStack = NekoCurrencyRegistrar.getItemStack(currencyId, 1);
        if (coinStack == null) {
            // fallback：货币未注册时用木炭占位（与 V1 一致）
            coinStack = new ItemStack(net.minecraft.init.Items.coal, 1, 1);
        }
        ItemDisplayWidget iconWidget = new ItemDisplayWidget().item(coinStack)
            .size(22)
            .background(new IDrawable[0]); // 无背景（隐藏物品槽默认背景）

        // ==================== 余额数字 ====================
        // 动态文本，scale 受 serp 动画控制（draw 方法中更新）
        this.coinAmount = IKey.dynamic(() -> getReadableString(this.coinSyncValue.getValue()))
            .scale(DEFAULT_AMOUNT_SCALE)
            .asWidget()
            .top(6)
            .left(24)
            .width(24);

        // ==================== 弹出按钮 ====================
        // 使用 NekoGuiTextures.EJECT_COINS 作为图标，需 Shift 才能点击（参考 VM CoinButton）
        // 使用普通 ButtonWidget 替代 ToggleButton：仅在按住 Shift 时手动触发 BooleanSyncValue，
        // 未按 Shift 时返回 false（IGNORE），保持防误触行为。
        this.ejectButton = new ButtonWidget<>().size(12)
            .disableThemeBackground(true)
            .disableHoverThemeBackground(true)
            .overlay(
                new IDrawable[] { NekoGuiTextures.EJECT_COINS.asIcon()
                    .size(12) })
            .onMousePressed(btn -> {
                // 未按住 Shift 时忽略点击（防止误触弹出硬币）
                if (!isShiftDown()) {
                    return false;
                }
                // 按住 Shift 时手动触发弹出同步值：通过 syncManager 查找对应 BooleanSyncValue 并置 true
                BooleanSyncValue ejectCoinSync = syncManager
                    .findSyncHandler("nekoEjectCoin_" + currencyId, 0, BooleanSyncValue.class);
                if (ejectCoinSync != null) {
                    ejectCoinSync.setValue(true);
                }
                return true;
            });
        // tooltip 动态显示实时余额和货币名称（autoUpdate 保证每帧刷新）
        this.ejectButton.tooltipDynamic(builder -> {
            builder.clearText();
            // 第一行：余额 + 货币名称
            builder.addLine(this.coinSyncValue.getValue() + " " + displayName);
            // ME 网络余额行（阶段 6）：仅当 meAmountSupplier 已注入且余额 > 0 时显示
            if (meAmountSupplier != null) {
                int meAmt = meAmountSupplier.getAsInt();
                if (meAmt > 0) {
                    builder.addLine(IKey.str(EnumChatFormatting.LIGHT_PURPLE + "ME 网络: " + meAmt));
                }
            }
            builder.emptyLine();
            // 提示行：灰色斜体（复刻 VM 的 eject_hint 样式）
            builder.addLine(
                IKey.str("按住 Shift 点击弹出")
                    .style(IKey.GRAY, IKey.ITALIC));
            builder.setAutoUpdate(true);
        });

        // ==================== 组装 ====================
        // 布局：图标(22px) + 余额数字 + 弹出按钮(12px)
        this.child(iconWidget)
            .child(this.coinAmount)
            .child(
                this.ejectButton.left(48)
                    .top(5))
            .height(22)
            .width(60);
    }

    /**
     * 设置 ME 网络货币余额查询器（阶段 6）
     * <p>
     * 由 GUI 层在创建组件后调用，注入 ME 余额查询逻辑。
     * 注入后，弹出按钮的 tooltip 会额外显示 ME 网络中的该货币数量。
     *
     * @param supplier ME 余额查询器，返回 ME 网络中该货币的数量
     */
    public void setMeAmountSupplier(IntSupplier supplier) {
        this.meAmountSupplier = supplier;
    }

    /**
     * 绘制：检测余额变化并应用 serp 缓动动画
     * <p>
     * 复刻 VM CoinDisplay.draw 的动画逻辑（简化版，无 meCoins）：
     * <ol>
     * <li>读取当前余额值（从 IntSyncValue）</li>
     * <li>检测余额变化 → 记录变化时间戳和方向（增加/减少）</li>
     * <li>计算动画进度（0 ~ COIN_ANIM_TIME 毫秒）</li>
     * <li>使用 serp 缓动函数计算当前 scale：
     * <ul>
     * <li>余额增加：scale 从 0.8 缓动到 0.9 再回到 0.8（放大弹跳，正向反馈）</li>
     * <li>余额减少：scale 从 0.8 缓动到 0.7 再回到 0.8（缩小弹跳，负向反馈）</li>
     * </ul>
     * </li>
     * <li>动画结束后恢复默认 scale 0.8</li>
     * </ol>
     * <p>
     * <b>动画原理</b>：serp 是对称抛物线函数，t=0 和 t=1 时返回 start，
     * t=0.5 时返回 start+offset（极值）。即动画"先变化再回归"，产生弹跳效果。
     *
     * @param context     GUI 上下文
     * @param widgetTheme 控件主题
     */
    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int currentVal = this.coinSyncValue.getValue();

        // 检测余额变化
        if (currentVal != this.oldCoinValue) {
            if (this.oldCoinValue != -1) {
                // 非首次更新：记录变化时间戳和方向
                this.lastCoinChange = System.currentTimeMillis();
                this.coinIncreased = currentVal > this.oldCoinValue;
            }
            this.oldCoinValue = currentVal;
        }

        // 计算 serp 动画 scale
        long now = System.currentTimeMillis();
        int diff = (int) (now - this.lastCoinChange);
        float scale = DEFAULT_AMOUNT_SCALE;
        if (diff > 0 && diff < COIN_ANIM_TIME) {
            // 动画进行中：应用 serp 缓动
            float progress = (float) diff / COIN_ANIM_TIME;
            scale = serp(
                DEFAULT_AMOUNT_SCALE,
                this.coinIncreased ? AMOUNT_SCALE_OFFSET : -AMOUNT_SCALE_OFFSET,
                progress);
        }
        this.coinAmount.scale(scale);

        super.draw(context, widgetTheme);
    }

    /**
     * serp 缓动函数（对称抛物线）
     * <p>
     * 复刻 VM CoinDisplay.serp：{@code offset * (-4*(t-0.5)^2 + 1) + start}
     * <br>
     * 这是一个开口向下的抛物线，顶点在 t=0.5 处：
     * <ul>
     * <li>t=0：返回 start（动画起点，默认 scale 0.8）</li>
     * <li>t=0.5：返回 start+offset（极值，增加时 0.9 / 减少时 0.7）</li>
     * <li>t=1：返回 start（动画终点，回归默认 0.8）</li>
     * </ul>
     *
     * @param start  起始值（默认 scale 0.8）
     * @param offset 偏移量（增加时 +0.1，减少时 -0.1）
     * @param t      进度 [0, 1]
     * @return 当前 scale 值
     */
    private static float serp(float start, float offset, float t) {
        return offset * (-4 * (t - 0.5f) * (t - 0.5f) + 1) + start;
    }

    /**
     * 将金额转换为可读字符串
     * <p>
     * 复刻 VM CoinDisplay.getReadableStringFromCoinAmount 和 V1 NekoCoinDisplay.getReadableString：
     * <ul>
     * <li>小于 10000：直接显示数字（如 "9999"）</li>
     * <li>小于 1000000（100万）：显示 K（千），如 "10K"</li>
     * <li>大于等于 1000000：显示 M（百万），如 "1M"</li>
     * </ul>
     *
     * @param amount 金额
     * @return 可读字符串
     */
    private static String getReadableString(int amount) {
        if (amount < 10000) {
            return "" + amount;
        }
        if (amount < 1000000) {
            return amount / 1000 + "K";
        }
        return amount / 1000000 + "M";
    }

    // ==================== 辅助方法 ====================

    /**
     * 检测 Shift 键是否按下
     * <p>
     * 通过 {@link Keyboard#isKeyDown(int)} 实时检测左 Shift 或右 Shift，
     * 用于弹出按钮的防误触判断。
     *
     * @return 左 Shift 或右 Shift 任一按下返回 true
     */
    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}
