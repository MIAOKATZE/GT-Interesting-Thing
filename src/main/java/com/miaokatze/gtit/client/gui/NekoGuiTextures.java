package com.miaokatze.gtit.client.gui;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.drawable.ColorType;
import com.cleanroommc.modularui.drawable.TabTexture;
import com.cleanroommc.modularui.drawable.UITexture;

/**
 * 猫猫售货机 GUI 纹理常量类
 * <p>
 * 完美复刻 VM mod 的 {@code com.cubefury.vendingmachine.gui.GuiTextures}，
 * 所有纹理资源从 {@code vendingmachine:textures/gui/} 迁移到 {@code gtit:textures/gui/}。
 * <p>
 * 共 28 个纹理常量，涵盖背景、覆盖层、标签页、图标等全部 GUI 素材。
 */
public final class NekoGuiTextures {

    /** 交易背景纹理（61x12，可适应4像素边框） */
    public static final UITexture TRADE_BACKGROUND = UITexture.builder()
        .location("gtit", "gui/background/trade_background")
        .imageSize(61, 12)
        .adaptable(4)
        .canApplyTheme()
        .name("trade_background")
        .build();

    /** 侧边面板背景纹理（50x214，可适应4像素边框） */
    public static final UITexture SIDE_PANEL_BACKGROUND = UITexture.builder()
        .location("gtit", "gui/background/panel_side")
        .imageSize(50, 214)
        .adaptable(4)
        .canApplyTheme()
        .name("panel_side_background")
        .build();

    /** 文本框背景纹理（61x12，可适应1像素边框） */
    public static final UITexture TEXT_FIELD_BACKGROUND = UITexture.builder()
        .location("gtit", "gui/background/text_field_light_gray")
        .imageSize(61, 12)
        .adaptable(1)
        .canApplyTheme()
        .name("text_field_background")
        .build();

    /** TILE 模式交易按钮-未按下（47x25，可适应4像素边框） */
    public static final UITexture TILE_TRADE_BUTTON_UNPRESSED = UITexture.builder()
        .location("gtit", "gui/background/trade_button_unpressed")
        .canApplyTheme()
        .imageSize(47, 25)
        .adaptable(4)
        .name("trade_button_unpressed")
        .build();

    /** TILE 模式交易按钮-按下（47x25，可适应4像素边框） */
    public static final UITexture TILE_TRADE_BUTTON_PRESSED = UITexture.builder()
        .location("gtit", "gui/background/trade_button_pressed")
        .canApplyTheme()
        .imageSize(47, 25)
        .adaptable(4)
        .name("trade_button_pressed")
        .build();

    /** LIST 模式交易按钮-未按下（154x14，可适应4像素边框） */
    public static final UITexture LIST_TRADE_BUTTON_UNPRESSED = UITexture.builder()
        .location("gtit", "gui/background/list_trade_button_unpressed")
        .canApplyTheme()
        .imageSize(154, 14)
        .adaptable(4)
        .name("list_trade_button_unpressed")
        .build();

    /** LIST 模式交易按钮-按下（154x14） */
    public static final UITexture LIST_TRADE_BUTTON_PRESSED = UITexture.builder()
        .location("gtit", "gui/background/list_trade_button_pressed")
        .canApplyTheme()
        .imageSize(154, 14)
        .name("list_trade_button_pressed")
        .build();

    /** 可交易覆盖层纹理（47x25，非不透明） */
    public static final UITexture OVERLAY_TRADEABLE = UITexture.builder()
        .location("gtit", "gui/overlay/tile_tradeable")
        .imageSize(47, 25)
        .adaptable(4)
        .nonOpaque()
        .name("overlay_tradeable")
        .build();

    /** 选中覆盖层纹理（47x25，非不透明） */
    public static final UITexture OVERLAY_SELECTED = UITexture.builder()
        .location("gtit", "gui/overlay/tile_selected")
        .imageSize(47, 25)
        .adaptable(4)
        .nonOpaque()
        .name("overlay_selected")
        .build();

    /** 冷却覆盖层纹理（47x25，非不透明） */
    public static final UITexture OVERLAY_COOLDOWN = UITexture.builder()
        .location("gtit", "gui/overlay/tile_cooldown")
        .imageSize(47, 25)
        .adaptable(4)
        .nonOpaque()
        .name("overlay_cooldown")
        .build();

    /** TILE 显示模式图标（32x32） */
    public static final UITexture MODE_TILE = UITexture.builder()
        .location("gtit", "gui/overlay/mode_tile")
        .imageSize(32, 32)
        .name("mode_tile")
        .build();

    /** LIST 显示模式图标（32x32） */
    public static final UITexture MODE_LIST = UITexture.builder()
        .location("gtit", "gui/overlay/mode_list")
        .imageSize(32, 32)
        .name("mode_list")
        .build();

    /** 智能排序图标（32x32） */
    public static final UITexture SORT_SMART = UITexture.builder()
        .location("gtit", "gui/overlay/sort_smart")
        .imageSize(32, 32)
        .name("sort_smart")
        .build();

    /** 字母排序图标（32x32） */
    public static final UITexture SORT_ALPHABET = UITexture.builder()
        .location("gtit", "gui/overlay/sort_alphabet")
        .imageSize(32, 32)
        .name("sort_alphabet")
        .build();

    /** 个人钱包图标（16x16） */
    public static final UITexture WALLET_PERSONAL = UITexture.builder()
        .location("gtit", "gui/overlay/wallet_personal")
        .imageSize(16, 16)
        .name("wallet_personal")
        .build();

    /** 团队钱包图标（16x16） */
    public static final UITexture WALLET_TEAM = UITexture.builder()
        .location("gtit", "gui/overlay/wallet_team")
        .imageSize(16, 16)
        .name("wallet_team")
        .build();

    /** 显示猫猫币图标（12x12） */
    public static final UITexture SHOW_COINS = UITexture.builder()
        .location("gtit", "gui/overlay/show_coins")
        .imageSize(12, 12)
        .name("show_coins")
        .build();

    /** 隐藏猫猫币图标（12x12） */
    public static final UITexture HIDE_COINS = UITexture.builder()
        .location("gtit", "gui/overlay/hide_coins")
        .imageSize(12, 12)
        .name("hide_coins")
        .build();

    /** 音频开启图标（12x12） */
    public static final UITexture AUDIO_ON = UITexture.builder()
        .location("gtit", "gui/overlay/audio_on")
        .imageSize(12, 12)
        .name("audio_on")
        .build();

    /** 音频关闭图标（12x12） */
    public static final UITexture AUDIO_OFF = UITexture.builder()
        .location("gtit", "gui/overlay/audio_off")
        .imageSize(12, 12)
        .name("audio_off")
        .build();

    /** 输入槽背景纹理（30x20） */
    public static final UITexture INPUT_SPRITE = UITexture.builder()
        .location("gtit", "gui/background/input")
        .imageSize(30, 20)
        .name("background_input")
        .build();

    /** 出货槽背景纹理（可适应主题） */
    public static final UITexture DISPENSER_BACKGROUND = UITexture.builder()
        .location("gtit", "gui/background/dispenser_background")
        .canApplyTheme()
        .name("background_dispenser_background")
        .build();

    /** 出货槽悬垂纹理（可适应主题） */
    public static final UITexture DISPENSER_OVERHANG = UITexture.builder()
        .location("gtit", "gui/background/dispenser_overhang")
        .canApplyTheme()
        .name("background_dispenser_overhang")
        .build();

    /** 弹出猫猫币按钮图标（16x16） */
    public static final UITexture EJECT_COINS = UITexture.builder()
        .location("gtit", "gui/overlay/coin_return")
        .imageSize(16, 16)
        .name("coin_eject")
        .build();

    /** 弹出物品按钮图标（16x16） */
    public static final UITexture EJECT_SLOTS = UITexture.builder()
        .location("gtit", "gui/overlay/slot_empty")
        .imageSize(16, 16)
        .name("slot_empty")
        .build();

    /** 左侧标签页纹理（TabTexture，32x28，4像素边框） */
    public static final TabTexture TAB_LEFT = TabTexture.of(
        UITexture.fullImage(new ResourceLocation("gtit", "gui/tabs_left"), ColorType.DEFAULT),
        GuiAxis.X,
        false,
        32,
        28,
        4);

    /** 收藏星标图标（16x16，完整图像） */
    public static final UITexture FAVOURITE_SPRITE = UITexture.builder()
        .location("gtit", "gui/icons/favourite_indicator")
        .imageSize(16, 16)
        .fullImage()
        .name("favourite_indicator")
        .build();

    /** 标签页高亮覆盖层（20x16，非不透明） */
    public static final UITexture TAB_HIGHLIGHT = UITexture.builder()
        .location("gtit", "gui/overlay/filtered_tab")
        .imageSize(20, 16)
        .name("filtered_tab")
        .nonOpaque()
        .build();

    // ==================== v1.7.0 主标签图标（32x32，完整图像） ====================

    /** 主标签-贸易图标 */
    public static final UITexture MAIN_TAB_TRADE = UITexture.builder()
        .location("gtit", "gui/main_tabs/tab_trade")
        .imageSize(32, 32)
        .fullImage()
        .name("main_tab_trade")
        .build();

    /** 主标签-签到图标 */
    public static final UITexture MAIN_TAB_SIGNIN = UITexture.builder()
        .location("gtit", "gui/main_tabs/tab_signin")
        .imageSize(32, 32)
        .fullImage()
        .name("main_tab_signin")
        .build();

    /** 主标签-抽奖图标 */
    public static final UITexture MAIN_TAB_LOTTERY = UITexture.builder()
        .location("gtit", "gui/main_tabs/tab_lottery")
        .imageSize(32, 32)
        .fullImage()
        .name("main_tab_lottery")
        .build();

    /** 主标签-邮件图标 */
    public static final UITexture MAIN_TAB_MAIL = UITexture.builder()
        .location("gtit", "gui/main_tabs/tab_mail")
        .imageSize(32, 32)
        .fullImage()
        .name("main_tab_mail")
        .build();

    /** 主标签-编辑模式图标（扳手） */
    public static final UITexture MAIN_TAB_EDIT = UITexture.builder()
        .location("gtit", "gui/main_tabs/tab_edit")
        .imageSize(32, 32)
        .fullImage()
        .name("main_tab_edit")
        .build();

    // ==================== v1.7.0 签到日历素材 ====================

    /** 签到日期格-普通（20x20） */
    public static final UITexture SIGNIN_CELL_NORMAL = UITexture.builder()
        .location("gtit", "gui/signin/cell_normal")
        .imageSize(20, 20)
        .fullImage()
        .name("signin_cell_normal")
        .build();

    /** 签到日期格-已签到（20x20） */
    public static final UITexture SIGNIN_CELL_SIGNED = UITexture.builder()
        .location("gtit", "gui/signin/cell_signed")
        .imageSize(20, 20)
        .fullImage()
        .name("signin_cell_signed")
        .build();

    /** 签到日期格-今天可签到（20x20，亮绿边） */
    public static final UITexture SIGNIN_CELL_TODAY = UITexture.builder()
        .location("gtit", "gui/signin/cell_today")
        .imageSize(20, 20)
        .fullImage()
        .name("signin_cell_today")
        .build();

    /** 签到日期格-有奖励可领（20x20，橙边） */
    public static final UITexture SIGNIN_CELL_REWARD = UITexture.builder()
        .location("gtit", "gui/signin/cell_reward")
        .imageSize(20, 20)
        .fullImage()
        .name("signin_cell_reward")
        .build();

    /** 签到猫爪印章（16x16，独立件） */
    public static final UITexture SIGNIN_PAW_STAMP = UITexture.builder()
        .location("gtit", "gui/signin/paw_stamp")
        .imageSize(16, 16)
        .fullImage()
        .name("signin_paw_stamp")
        .build();

    /** 7天连续签到小宝箱（24x24） */
    public static final UITexture SIGNIN_CHEST_7 = UITexture.builder()
        .location("gtit", "gui/signin/chest_7")
        .imageSize(24, 24)
        .fullImage()
        .name("signin_chest_7")
        .build();

    /** 14天连续签到中宝箱（24x24） */
    public static final UITexture SIGNIN_CHEST_14 = UITexture.builder()
        .location("gtit", "gui/signin/chest_14")
        .imageSize(24, 24)
        .fullImage()
        .name("signin_chest_14")
        .build();

    /** 30天连续签到大宝箱（24x24） */
    public static final UITexture SIGNIN_CHEST_30 = UITexture.builder()
        .location("gtit", "gui/signin/chest_30")
        .imageSize(24, 24)
        .fullImage()
        .name("signin_chest_30")
        .build();

    /** 签到领取按钮胚（48x24，绿色，无文字） */
    public static final UITexture SIGNIN_BTN_CLAIM = UITexture.builder()
        .location("gtit", "gui/signin/btn_claim")
        .imageSize(48, 24)
        .fullImage()
        .name("signin_btn_claim")
        .build();

    // ==================== v1.7.1 抽奖轮盘素材 ====================

    /** 抽奖槽位-普通（24x24，灰框，COMMON/RARE 稀有度底） */
    public static final UITexture LOTTERY_SLOT_NORMAL = UITexture.builder()
        .location("gtit", "gui/lottery/slot_normal")
        .imageSize(24, 24)
        .fullImage()
        .name("lottery_slot_normal")
        .build();

    /** 抽奖槽位-金边（24x24，EPIC 稀有度底） */
    public static final UITexture LOTTERY_SLOT_RARE = UITexture.builder()
        .location("gtit", "gui/lottery/slot_rare")
        .imageSize(24, 24)
        .fullImage()
        .name("lottery_slot_rare")
        .build();

    /** 抽奖槽位-闪角（24x24，LEGENDARY 稀有度底） */
    public static final UITexture LOTTERY_SLOT_EPIC = UITexture.builder()
        .location("gtit", "gui/lottery/slot_epic")
        .imageSize(24, 24)
        .fullImage()
        .name("lottery_slot_epic")
        .build();

    /** 抽奖轮盘指针（16x16，橙色倒三角，指示当前点亮格） */
    public static final UITexture LOTTERY_POINTER = UITexture.builder()
        .location("gtit", "gui/lottery/pointer")
        .imageSize(16, 16)
        .fullImage()
        .name("lottery_pointer")
        .build();

    /** 抽 1 次按钮胚（64x24，橙色长按钮，无文字） */
    public static final UITexture LOTTERY_BTN_DRAW = UITexture.builder()
        .location("gtit", "gui/lottery/btn_draw")
        .imageSize(64, 24)
        .fullImage()
        .name("lottery_btn_draw")
        .build();

    /** 抽 10 次按钮胚（64x24，橙粉双色长按钮，无文字） */
    public static final UITexture LOTTERY_BTN_DRAW10 = UITexture.builder()
        .location("gtit", "gui/lottery/btn_draw10")
        .imageSize(64, 24)
        .fullImage()
        .name("lottery_btn_draw10")
        .build();

    /** 猫猫币图标（16x16，金色，卡池价格/余额展示） */
    public static final UITexture LOTTERY_COIN_NEKO = UITexture.builder()
        .location("gtit", "gui/lottery/coin_neko")
        .imageSize(16, 16)
        .fullImage()
        .name("lottery_coin_neko")
        .build();

    /** 闪烁猫猫币图标（16x16，粉色，卡池价格/余额展示） */
    public static final UITexture LOTTERY_COIN_SHIMMER = UITexture.builder()
        .location("gtit", "gui/lottery/coin_shimmer")
        .imageSize(16, 16)
        .fullImage()
        .name("lottery_coin_shimmer")
        .build();

    /** 稀有度角标-蓝（8x8，RARE 条目右上角标） */
    public static final UITexture LOTTERY_CORNER_BLUE = UITexture.builder()
        .location("gtit", "gui/lottery/corner_blue")
        .imageSize(8, 8)
        .fullImage()
        .name("lottery_corner_blue")
        .build();

    /** 稀有度角标-紫（8x8，EPIC/LEGENDARY 条目右上角标） */
    public static final UITexture LOTTERY_CORNER_PURPLE = UITexture.builder()
        .location("gtit", "gui/lottery/corner_purple")
        .imageSize(8, 8)
        .fullImage()
        .name("lottery_corner_purple")
        .build();

    private NekoGuiTextures() {}
}
