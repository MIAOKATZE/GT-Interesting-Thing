package com.miaokatze.gtit.reincarnation.gui;

import com.miaokatze.gtit.reincarnation.core.ReincarnationCycle;

/**
 * 周目 GUI 契约唯一单源（v1.8.3，MUI2 → Forge 原版 IGuiHandler 链路迁移；
 * v1.9.0 连掷重构：猫猫币支付格摘除，解锁改连掷会话制）。
 * <p>
 * 本类是 {@link ReincarnationContainer}（common，双端）与 client 渲染层
 * {@code ReincarnationGuiContainer}（client/gui 包，仅客户端加载，不在 common 文件中具名引用）
 * 之间全部契约的<b>唯一</b>定义处：GUI ID、槽位索引布局（含像素几何）、按钮 ID、
 * 进度条 id 映射与消息事件码。两侧均只引用本类常量，禁止各写一份魔数。
 * <p>
 * <b>侧与加载</b>：纯常量类，零 MC 客户端侧类型 / 零 client 包引用，
 * common 路径可安全加载；物理专用服务器上周目系统整体门控，本类不可达。
 */
public final class ReincarnationLayout {

    // ==================== GUI ID（EntityPlayer.openGui 的 modGuiId） ====================

    /**
     * 周目 GUI 的 openGui id。本 mod 仅有此一个 {@code cpw.mods.fml.common.network.IGuiHandler}
     * 注册（NetworkRegistry 每 mod 单 handler，重复注册为整体替换），故取 0 即无碰撞；
     * 专用服上 ClientProxy 不注册任何 handler，该 id 恒不可达。
     */
    public static final int GUI_ID = 0;

    // ==================== 槽位索引布局（Container 内全局槽位下标） ====================

    /**
     * 外壳累计槽区起点：下标 {@code 0..14}（每列 1 格，index = 列下标）。
     * 放入即消耗（槽位不持有物品），见 {@link ReincarnationContainer} 外壳槽语义。
     */
    public static final int SLOT_HULL_FIRST = 0;

    /** 外壳累计槽数量（= {@link ReincarnationCycle#COLUMN_COUNT}） */
    public static final int HULL_SLOT_COUNT = ReincarnationCycle.COLUMN_COUNT;

    /**
     * 物品格区起点：下标 {@code 15..59}（15 列 × 3 行 = 45 格），
     * index = {@link #SLOT_ITEM_FIRST} + column * {@link ReincarnationCycle#MAX_UNLOCKED_ROWS} + row。
     */
    public static final int SLOT_ITEM_FIRST = SLOT_HULL_FIRST + HULL_SLOT_COUNT;

    /** 物品格数量（15 列 × 3 行） */
    public static final int ITEM_SLOT_COUNT = ReincarnationCycle.COLUMN_COUNT * ReincarnationCycle.MAX_UNLOCKED_ROWS;

    /**
     * 玩家背包槽区起点：下标 {@code 60..86} 主背包（inventory 9..35）+ {@code 87..95} 快捷栏
     * （inventory 0..8）。
     * <p>
     * v1.9.0 连掷重构：原猫猫币支付格（下标 60）整体摘除（解锁改连掷会话制，激活位见
     * {@link #BAR_ROLL_ACTIVE}），玩家背包下标自 60 起。
     */
    public static final int SLOT_PLAYER_FIRST = SLOT_ITEM_FIRST + ITEM_SLOT_COUNT;

    /** 玩家背包槽位数量（vanilla InventoryPlayer：主背包 27 + 快捷栏 9 = 36，下标 60..95） */
    public static final int PLAYER_SLOT_COUNT = 36;

    /**
     * Container 总槽位数（15 外壳 + 45 物品 + 36 玩家背包 = 96）。
     * <p>
     * v1.8.3 接续修正：原公式 {@code SLOT_PLAYER_FIRST + ITEM_SLOT_COUNT + HULL_SLOT_COUNT}
     * 把物品格与外壳格在玩家背包之后重复计入（=121），与 {@link #SLOT_PLAYER_FIRST}
     * 的下标映射自相矛盾，按该下标映射口径修正。
     * v1.9.0 连掷重构：猫猫币支付格（原 1 格）摘除，总数 97 → 96。
     */
    public static final int TOTAL_SLOT_COUNT = SLOT_PLAYER_FIRST + PLAYER_SLOT_COUNT;

    /** 物品格 (column, row) → Container 槽位下标 */
    public static int itemSlotIndex(int column, int row) {
        return SLOT_ITEM_FIRST + column * ReincarnationCycle.MAX_UNLOCKED_ROWS + row;
    }

    // ==================== 按钮 ID（enchantItem / sendEnchantPacket 通道） ====================

    /** 确认轮回按钮（两步：第一次点击仅武装，第二次点击执行；槽位变动/关窗重置武装态） */
    public static final int BUTTON_CONFIRM = 0;

    /** 解锁行按钮：闪烁猫猫币（成功率 1/1000） */
    public static final int BUTTON_UNLOCK_SHIMMER = 1;

    /** 解锁行按钮：普通猫猫币（成功率 1/100000） */
    public static final int BUTTON_UNLOCK_NORMAL = 2;

    // ==================== 进度条 id 映射（ICrafting.sendProgressBarUpdate 通道，值限 short 16 位） ====================

    /**
     * 外壳计数进度条起点：id {@code 0..14}，值 = 列 i 已累计消耗外壳数（0..16）。
     * <p>
     * <b>锁定列的 16 位表达（唯一固定编码）</b>：不另设布尔通道，列解锁标记按位打包进
     * {@link #BAR_COLUMN_UNLOCK_MASK} 的一个 16 位 short —— bit i = 列 i 已解锁（1），
     * bit 15 恒为 0（保留）。15 列 ≤ 16 位恰好容纳，这是唯一允许的编码，消费方不得另解。
     */
    public static final int BAR_HULL_COUNT_FIRST = 0;

    /** 周目状态进度条：id {@code 15}，值 = {@link ReincarnationCycle.CycleState#ordinal()}（0..2） */
    public static final int BAR_STATE = 15;

    /** 列解锁位掩码进度条：id {@code 16}，编码见 {@link #BAR_HULL_COUNT_FIRST} javadoc（唯一固定编码） */
    public static final int BAR_COLUMN_UNLOCK_MASK = 16;

    /** 全局已解锁行数进度条：id {@code 17}，值 = 1..3 */
    public static final int BAR_UNLOCKED_ROWS = 17;

    /** 待领取（已寄存）物品数进度条：id {@code 18}，值 = 0..45 */
    public static final int BAR_PENDING_COUNT = 18;

    /** 确认按钮武装标记进度条：id {@code 19}，值 = 0/1（仅服务端置位，槽位变动/关窗重置） */
    public static final int BAR_CONFIRM_ARMED = 19;

    /**
     * 面板消息事件码进度条：id {@code 20}，值 = {@link #MSG_NONE} / {@link #MSG_COLUMN_UNLOCKED_FIRST}+i /
     * {@link #MSG_HULL_CONSUMED_FIRST}+i / {@link #MSG_UNLOCK_SUCCESS} / {@link #MSG_UNLOCK_FAIL_SHIMMER} /
     * {@link #MSG_UNLOCK_FAIL_NORMAL}。
     * <p>
     * 事件码只表达「发生了什么」，具体参数（列名/n 值/概率文案）由客户端在渲染时
     * 从其余进度条值与 lang 键本地派生——因此消息行无需字符串同步通道
     * （v1.8.3 裁决：ReincarnationSyncPacket 不扩展）。
     * 已知纯显示级差异：旧 MUI2 消息行在事件时刻冻结参数文本，本实现参数随进度条实时刷新。
     */
    public static final int BAR_MESSAGE_EVENT = 20;

    /**
     * 连掷会话激活位进度条：id {@code 21}，位打包（唯一固定编码）——bit 0 = 闪烁币连掷会话
     * 激活（1 = 激活），bit 1 = 普通币连掷会话激活，两会话相互独立，未用位恒 0。
     * <p>
     * v1.9.0 连掷重构新增：解锁按钮 = toggle 连掷会话（首按开启、再按停止；激活期间每
     * 服务端 tick 从背包扣 1 枚对应猫猫币掷 1 次），客户端按钮文案切换由本位驱动
     * （client 树消费，另一切片）。激活态变化 ≤1 tick 经 {@code detectAndSendChanges} 送达。
     */
    public static final int BAR_ROLL_ACTIVE = 21;

    /** 消息事件码：无消息（消息行渲染为空） */
    public static final int MSG_NONE = 0;

    /** 消息事件码区间起点：列解锁（{@code 1..15}，列 = 码-1）—— gtit.reincarnation.chat.column_unlocked */
    public static final int MSG_COLUMN_UNLOCKED_FIRST = 1;

    /** 消息事件码区间起点：外壳消耗（{@code 16..30}，列 = 码-16）—— gtit.reincarnation.chat.hull.consumed */
    public static final int MSG_HULL_CONSUMED_FIRST = 16;

    /** 消息事件码：行解锁成功 —— gtit.reincarnation.chat.unlock.success */
    public static final int MSG_UNLOCK_SUCCESS = 31;

    /** 消息事件码：行解锁失败（闪烁猫猫币，0.1%%） */
    public static final int MSG_UNLOCK_FAIL_SHIMMER = 32;

    /** 消息事件码：行解锁失败（普通猫猫币，0.001%%） */
    public static final int MSG_UNLOCK_FAIL_NORMAL = 33;

    /** 解锁列位掩码：bit i = 列 i 已解锁（编码见 {@link #BAR_HULL_COUNT_FIRST} javadoc） */
    public static int columnUnlockMask(boolean[] unlockedColumns) {
        int mask = 0;
        for (int i = 0; i < ReincarnationCycle.COLUMN_COUNT; i++) {
            if (unlockedColumns[i]) {
                mask |= (1 << i);
            }
        }
        return mask;
    }

    /** 列 i 是否命中「列解锁」消息事件码区间 */
    public static boolean isColumnUnlockedEvent(int eventCode) {
        return eventCode >= MSG_COLUMN_UNLOCKED_FIRST && eventCode < MSG_COLUMN_UNLOCKED_FIRST + HULL_SLOT_COUNT;
    }

    /** 列 i 是否命中「外壳消耗」消息事件码区间 */
    public static boolean isHullConsumedEvent(int eventCode) {
        return eventCode >= MSG_HULL_CONSUMED_FIRST && eventCode < MSG_HULL_CONSUMED_FIRST + HULL_SLOT_COUNT;
    }

    /** 事件码 → 涉及列下标（区间码反解；非列事件返回 -1） */
    public static int eventColumn(int eventCode) {
        if (isColumnUnlockedEvent(eventCode)) {
            return eventCode - MSG_COLUMN_UNLOCKED_FIRST;
        }
        if (isHullConsumedEvent(eventCode)) {
            return eventCode - MSG_HULL_CONSUMED_FIRST;
        }
        return -1;
    }

    // ==================== 像素几何（Slot 命中区与 client 渲染层共用，禁止两侧各写一份） ====================

    /** 面板宽（15 列 × 20px 列距 + 两侧余量；与旧 MUI2 面板 320×258 一致） */
    public static final int PANEL_WIDTH = 320;

    /** 面板高 */
    public static final int PANEL_HEIGHT = 258;

    /** 列距（18px 槽 + 2px 间隙） */
    public static final int SLOT_PITCH = 20;

    /** 槽位边长 */
    public static final int SLOT_SIZE = 18;

    /** 网格左缘（(320 - 15*20 + 2) / 2 = 11，网格总宽 298） */
    public static final int GRID_X = 11;

    /** 顶部横幅 Y（gui 相对坐标，下同） */
    public static final int BANNER_Y = 2;

    /** 标题 Y */
    public static final int TITLE_Y = 13;

    /** 外壳累计槽行 Y */
    public static final int HULL_Y = 26;

    /** 物品格首行 Y（3 行，行距 {@link #SLOT_PITCH}） */
    public static final int ITEMS_Y = 53;

    /** 列名标签行 Y */
    public static final int LABELS_Y = 114;

    /** 动作行 Y（2 解锁按钮；v1.9.0 币格摘除） */
    public static final int ACTION_Y = 124;

    /** 面板消息行 Y */
    public static final int MESSAGE_Y = 144;

    /** 确认轮回按钮 Y（宽 {@link #CONFIRM_BUTTON_WIDTH} × 高 16） */
    public static final int CONFIRM_Y = 154;

    /** 确认按钮宽 */
    public static final int CONFIRM_BUTTON_WIDTH = 150;

    /** 确认按钮 X（面板内水平居中） */
    public static final int CONFIRM_X = (PANEL_WIDTH - CONFIRM_BUTTON_WIDTH) / 2;

    /** 解锁按钮宽（两个解锁行按钮同宽） */
    public static final int UNLOCK_BUTTON_WIDTH = 136;

    /** 闪烁币解锁按钮 X（网格左缘 + 1 槽位 + 2px；v1.9.0 币格摘除后几何值不变） */
    public static final int UNLOCK_SHIMMER_X = GRID_X + SLOT_SIZE + 2;

    /** 普通币解锁按钮 X（闪烁币按钮右侧 2px） */
    public static final int UNLOCK_NORMAL_X = GRID_X + SLOT_SIZE + 4 + UNLOCK_BUTTON_WIDTH;

    /** 底部提示行 Y（忽略 NBT / 混淆加密） */
    public static final int FOOTER_Y = 172;

    /** 玩家背包首行 Y */
    public static final int INVENTORY_Y = 181;

    /** 玩家背包区左缘（9 × 18 = 162，面板内居中） */
    public static final int PLAYER_INV_X = (PANEL_WIDTH - 9 * SLOT_SIZE) / 2;

    /** 外壳解锁阈值：每列累计消耗 16 个外壳后解锁该列（旧 GUI 契约沿用） */
    public static final int HULL_TARGET = 16;

    private ReincarnationLayout() {}
}
