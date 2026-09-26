package com.miaokatze.gtit.gui.pocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeGuards;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeSwitches;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;

/**
 * 升级<b>配置面板</b>：主面板之上的次级面板，五个插件格的左键都开<b>这一个</b>（R96 S2，P-3）。
 *
 * <h2>为什么是一个面板而不是五套</h2>
 * P-3 的字面是"五个已安装插件格左键一律打开一个配置面板"。本件因此把<b>结构</b>定成"一行一型、五行常驻"，
 * 左键只决定"开哪一面"（同一面），不决定"造哪一面"。MUI2 2.3.88 的 {@code SecondaryPanel} 会<b>缓存</b>
 * 首次构建的面板（{@code openPanel()} 里 {@code panel != null} 就直接开），而同一版本<b>没有 widget
 * 可见性 API</b>（{@code NekoVMGuiV2:997-1000} 已实测记过这条）⇒ 结构一旦按"点击的型"变形，第二次点别的格
 * 拿到的还是第一型那一套。"五行常驻"把这条库限制一次消化掉：面板不需要按型重建。
 *
 * <h2>★按型分派不被写死成五型同形</h2>
 * 每一行的内容由 {@link #sectionsOf(PocketUpgradeType)} 这张<b>唯一分派表</b>决定：五型都有开关段；
 * 只有磁力与魔法使各带一段<b>挂载位</b>（{@link Section#MOUNT_MAGNET} / {@link Section#MOUNT_MAGE} ——
 * S7 / S9 的完整配置内容就填进那两个框，本轮把位画出来并钉住型）。容量 / 堆叠 / 通道持续化三型
 * <b>只有开关</b> ⇒ 面板给它们的就是那一枚开关，不给空框。
 * 分派表对 {@link PocketUpgradeType} 的五个值<b>逐个点名</b> ⇒ "追加第 6 型"时必须在这里表态，
 * 不能靠一条 {@code default} 把"只有开关"变成新形状的默认形状。
 * 这条是"五型同形"的唯一反证面，由用例 {@code config_panel_dispatch_is_not_five_identical} 钉。
 *
 * <h2>三块职责（装配面与数据面分开，数据面必须能在纯 JVM 真跑）</h2>
 * <ol>
 * <li>{@link #build} = 纯装配：只画控件、只发码，★一个字节都不写档；</li>
 * <li>{@link #encode} / {@link #typeOfArg} / {@link #offOfArg} = 动作码 arg 的编解码（双端同一条式子，
 * 越界一律丢弃）；</li>
 * <li>{@link #commitSwitch} = ★<b>服务端唯一的开关写腿</b>：三判里的"该型在档上"与"关闭守卫"两判
 * 加落档全在这一条腿里。它之所以住在本类而不是 {@link NekoPocketServerHandler}：
 * <b>handler 需要 {@code NekoPocketPanel}，而面板在纯 JVM 里造不出来</b> ⇒ 判据留在 handler 里就只能
 * "钉文本"，正是 R57/C3 那族"用例全绿、实机什么都没发生"。抽成静态纯函数后，三种拒绝与两种成交
 * 都能在 JVM 跑出来（用例 {@code switch_write_refuses_three_cases}）。
 * 第三判"持有者本人 + 载体身份"仍归 handler（它要读 {@code Container} 与 {@code EntityPlayer}）。</li>
 * </ol>
 *
 * <h2>★客户端读的是什么、以及为什么没有第二条通道</h2>
 * 行上的读数来自 {@code NekoPocketPanel#carrierStackLive()} —— <b>载体栈的 NBT</b>：服务端读权威栈、
 * 客户端读 vanilla 槽同步过来的那份镜像（与 R95 S4 的 {@code channelPersistActive} 客户端腿<b>同一条通道</b>，
 * off-mask 本身就住在这个根里）。本轮<b>不</b>为开关另开同步键：给同一个布尔造第二个读数面，就是
 * EVA-1 方向丙 DP2「零新通道」要避免的那件事（{@code registerSyncValues} 因此一处未加）。
 * ★代价照实登记：镜像是否 ≤1 tick 到达属实机项（V-2 / V-8）；点完按钮<b>立刻</b>能看到的反馈是回执
 * （走既有 {@code SYNC_RECEIPT}），那条不依赖镜像 ⇒ "切了开关但什么都不动"这一族被回执腿挡住。
 *
 * <h2>几何账（★硬顶，装配期自断言）</h2>
 * {@code 348 × 308}，两个方向都<b>严格小于</b>主面板的 {@code 398 × 360}，也小于次级面板自己的判据顶
 * （宽 ≤ {@link #MAX_WIDTH}=380、高 ≤ {@link #MAX_HEIGHT}=340）。★五行是<b>横贯面板的全宽带</b>
 * （型名在左、开关在本行最右端），挂载框排在带区之下 —— 只有全宽带才装得下英文最坏串
 * （"Channel Persistence Upgrade Module：Not installed" 逻辑宽 280，0.6 档要 168px，而左列只有 88px）。
 * ★为什么必须留余量而不是贴边：
 * 非主面板在 MUI2 里<b>恒可拖</b>（{@code ModularPanel.isDraggable()} 的实现就是"不是主面板就可拖"），
 * 拖动走 {@code DraggablePanelWrapper:49-50}，那里按"可视面减面板"的余量做除算 ⇒ 面板一旦等于可视面
 * 就是除零/负数。主面板那条 {@code HEIGHT != 360} 的断言一字未动（本件的顶是<b>另立</b>的常数，不搬它的账）。
 *
 * <h2>不进 Container、不进同步值</h2>
 * 面板里<b>不</b>放 {@code ItemSlot}/{@code SlotGroupWidget}，也不用任何 {@code syncValue}：
 * MUI2 的 {@code SecondaryPanel.openPanel()} 对"树里有同步值、面板本身没被同步"直接抛
 * {@code IllegalArgumentException}（库实测形体）⇒ 本件零同步值，从形状上避开那条抛，而不是靠"运行期
 * 别再点一次"。它因此也不占 {@code PocketSlots.TOTAL_REAL_SLOTS} 那条真实槽账（225 一字未动，
 * ★不变量 G1：本件不新增 {@code registerFactory}）。
 */
public final class PocketConfigPanel {

    /** 次级面板名（MUI2 的面板标识与调试树；<b>不是</b>同步键，本件零同步值）。 */
    public static final String PANEL_NAME = "gtit.pocket.config";

    /** 外边距（与主面板同一档，两把尺子才对得上）。 */
    public static final int MARGIN = 6;
    /** 行高：一行 = 一个 18px 控件 + 2px 缝。 */
    public static final int ROW_HEIGHT = 20;
    /** 开关按钮的宽（"关闭 / 打开 / 未装"三态在常驻档下都要放得下）。 */
    public static final int SWITCH_WIDTH = 66;
    /** 开关按钮的高。 */
    public static final int SWITCH_HEIGHT = 18;
    /**
     * 左列宽 —— ★本轮它的唯一职责是<b>给出挂载列的 x 偏移</b>（{@code MARGIN + SWITCH_COLUMN +
     * COLUMN_GAP}）：五行是横贯面板的全宽带（型名在左、开关在本行最右端），不与挂载列竞争横向空间。
     * 它仍必须容得下一枚开关 + 一点余量（见 static 块那条），否则挂载列会压在开关上。
     */
    public static final int SWITCH_COLUMN = 156;
    /** 右列（挂载位）的宽 —— 比开关列宽，因为 S7 的名单格与 S9 的元素格走 18px 栅格。 */
    public static final int MOUNT_COLUMN = 174;
    /** 列间距。 */
    public static final int COLUMN_GAP = 6;
    /** 一个挂载框的高。 */
    public static final int MOUNT_HEIGHT = 80;
    /** 两个挂载框之间的缝。 */
    public static final int MOUNT_GAP = 4;
    /** 回执行的高。 */
    public static final int RECEIPT_HEIGHT = 12;
    /** 关闭按钮的高与宽。 */
    public static final int CLOSE_HEIGHT = 16;
    /** 关闭按钮的宽。 */
    public static final int CLOSE_WIDTH = 56;
    /** 挂载框的个数 = 分派表里有内容段的型数（本轮两条：S7 磁力、S9 魔法使）。★声明在 {@link #HEIGHT} 之前：纵向预算要读它。 */
    public static final int MOUNT_FRAMES = 2;

    /** 面板宽 = {@code 6 + 156 + 6 + 174 + 6} = <b>348</b>（★派生自列宽，不手抄）。 */
    public static final int WIDTH = MARGIN + SWITCH_COLUMN + COLUMN_GAP + MOUNT_COLUMN + MARGIN;
    /**
     * 面板高 = {@code 6 + 5×20(五行) + 4(缝) + 2×80(两个挂载框) + 4(框间缝) + 12(回执) + 16(关闭) + 6}
     * = <b>308</b>。★纵向预算由<b>挂载列</b>给（开关列只占 100），改本常数时先看挂载列而不是行高。
     */
    public static final int HEIGHT = MARGIN + PocketUpgradeType.values().length * ROW_HEIGHT + 4
        + MOUNT_FRAMES * MOUNT_HEIGHT + (MOUNT_FRAMES - 1) * MOUNT_GAP + RECEIPT_HEIGHT + CLOSE_HEIGHT + MARGIN;

    /** 次级面板的宽度硬顶（见类 javadoc 的几何账）。 */
    public static final int MAX_WIDTH = 380;
    /** 次级面板的高度硬顶。 */
    public static final int MAX_HEIGHT = 340;

    /**
     * 一行的<b>内容段</b>（★按型分派，"五型同形"的反证面）。{@link #SWITCH} 是公共段，
     * 两段 {@code MOUNT_*} 只有磁力与魔法使有。
     */
    public enum Section {
        /** 开 / 关切换（五型都有）。 */
        SWITCH,
        /** 磁力的配置内容挂载位（S7：吸取目标 / 三态名单 / 12×6 格）。 */
        MOUNT_MAGNET,
        /** 魔法使的配置内容挂载位（S9：四模式 / 元素容量 / 充能读数）。 */
        MOUNT_MAGE
    }

    /** 一型的开关现状（★三态而不是两态："没装"与"关着"必须分得开）。 */
    public enum SwitchState {
        /** 在档上且开着。 */
        ON,
        /** 在档上且被关着。 */
        OFF,
        /** 这一型没在档上 ⇒ 没有开关可切。 */
        ABSENT
    }

    /** {@link #commitSwitch} 的结论（★枚举而不是布尔：拒绝有<b>三种</b>不同说法，并成一条就是假读数）。 */
    public enum Outcome {
        /** 关掉了一型。 */
        TURNED_OFF,
        /** 打开了一型。 */
        TURNED_ON,
        /** 目标值就是现值 ⇒ 零写入、零同步包（连点不刷包那一条）。 */
        NO_CHANGE,
        /** 这一型没在档上（既没生效也没被关着）。 */
        NOT_ON_RECORD,
        /** 容量守卫拒绝：关掉以后现有流体装不下。 */
        REFUSE_CAPACITY,
        /** 堆叠守卫拒绝：关掉以后现有的堆装不下。 */
        REFUSE_STACK
    }

    private PocketConfigPanel() {}

    static {
        // ★装配期自断言（形状照 NekoPocketPanel 的 static 块：判据一红就要求改动方回来看账，
        // 而不是留一条"派生式跟着漂、永不红"的活口）。★本块只碰整数与字符串，不碰纹理表
        // （{@code PocketGuiTextures} 的初始化会连带拉 fastutil，零依赖回归套件的类路径上没有它 ——
        // 同 {@code NekoPocketBottomBand#upgradeCellBackground} 记过的实测）。
        if (WIDTH > MAX_WIDTH || HEIGHT > MAX_HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 配置面板越过次级面板硬顶: " + WIDTH + "x" + HEIGHT
                    + " 顶=" + MAX_WIDTH + "x" + MAX_HEIGHT
                    + "（★非主面板恒可拖，DraggablePanelWrapper 按可视面余量做除算 ⇒ 贴边即除零/负数）");
        }
        if (WIDTH >= NekoPocketPanel.WIDTH || HEIGHT >= NekoPocketPanel.HEIGHT) {
            throw new IllegalStateException(
                "[pocket] 配置面板不再严格小于主面板: " + WIDTH + "x" + HEIGHT + "，主面板="
                    + NekoPocketPanel.WIDTH + "x" + NekoPocketPanel.HEIGHT
                    + "（盖满主面板的次级面板读起来就是一块新屏，且拖动余量归零）");
        }
        if (SWITCH_COLUMN < SWITCH_WIDTH + 18) {
            throw new IllegalStateException("[pocket] 左列放不下开关（挂载列的 x 偏移会压到按钮上）: " + SWITCH_COLUMN);
        }
        // ★横向闭合（与下面那条纵向闭合同一条纪律：{@link #WIDTH} 是派生式，有人手改成字面量时先在这里红，
        // 而不是等到屏幕上挂载框被裁掉）。形状 = 左列 + 列缝 + 挂载列必须落在右边距之内。
        if (MARGIN + SWITCH_COLUMN + COLUMN_GAP + MOUNT_COLUMN > WIDTH - MARGIN) {
            throw new IllegalStateException(
                "[pocket] 配置面板横向不闭合（挂载列顶出面板）: " + WIDTH + "，左列=" + SWITCH_COLUMN);
        }
        if (mountY(MOUNT_FRAMES - 1) + MOUNT_HEIGHT > HEIGHT - MARGIN) {
            throw new IllegalStateException("[pocket] 配置面板纵向不闭合（挂载列顶出面板）: " + HEIGHT);
        }
        if (PocketUpgradeType.values().length * ROW_HEIGHT > HEIGHT - MARGIN) {
            throw new IllegalStateException("[pocket] 开关列放不下五行: " + HEIGHT);
        }
        // ★分派表与挂载框数必须对得上：对不上就是"有人改了分派表而没改纵向预算"。
        int mounts = 0;
        for (final PocketUpgradeType type : PocketUpgradeType.values()) {
            if (hasMount(type)) {
                mounts++;
            }
        }
        if (mounts != MOUNT_FRAMES) {
            throw new IllegalStateException(
                "[pocket] 分派表里有挂载位的型数 = " + mounts + "，纵向预算按 " + MOUNT_FRAMES + " 个框算");
        }
    }

    // ================================================================== 数据面（纯 JVM 可跑）

    /**
     * ★唯一分派表：这一型的面板由哪些内容段组成。
     * <p>
     * 五个值<b>逐个点名</b>（不写成 {@code default} 一把梭）就是这条判据的全部内容：容量 / 堆叠 /
     * 通道持续化 = 只开关（P-3 的字面"只需开关的项其面板里就只放开关"），磁力 = 开关 + 名单挂载位，
     * 魔法使 = 开关 + 元素挂载位。若这里退化成"五型同形"，S7/S9 的完整配置就失去了宿主，
     * 而这条退化<b>不会</b>让编译或任何既有读数变红 —— 所以它由用例钉住而不是由注释自证。
     */
    public static List<Section> sectionsOf(PocketUpgradeType type) {
        if (type == null) {
            return Collections.singletonList(Section.SWITCH);
        }
        switch (type) {
            case CAPACITY:
                return Collections.singletonList(Section.SWITCH);
            case STACK:
                return Collections.singletonList(Section.SWITCH);
            case MAGNET:
                return Arrays.asList(Section.SWITCH, Section.MOUNT_MAGNET);
            case CHANNEL_PERSIST:
                return Collections.singletonList(Section.SWITCH);
            case DISTILL_FAST:
                return Arrays.asList(Section.SWITCH, Section.MOUNT_MAGE);
            default:
                // 走不到（上面已穷举五个值）。留这一支的意义：将来追加第 6 型而忘了在上面表态时，
                // 这里给的是"与开关同形"的最小安全回落，同时 static 块的 mounts 对账会先一步炸红。
                return Collections.singletonList(Section.SWITCH);
        }
    }

    /** 这一型是否有挂载位（★由 {@link #sectionsOf} 派生，别处不得再写一遍型清单）。 */
    public static boolean hasMount(PocketUpgradeType type) {
        return sectionsOf(type).size() > 1;
    }

    /** 挂载框的标题键；这一型没有挂载位 ⇒ {@code null}（★调用方不许把 null 读成空串继续画框）。 */
    public static String mountTitleKey(PocketUpgradeType type) {
        final List<Section> sections = sectionsOf(type);
        if (sections.contains(Section.MOUNT_MAGNET)) {
            return "gtit.pocket.config.mount.magnet";
        }
        if (sections.contains(Section.MOUNT_MAGE)) {
            return "gtit.pocket.config.mount.mage";
        }
        return null;
    }

    /** 动作码 arg 的编码：{@code ordinal * 2 + offBit}（★一条式子，双端同读；非法入参 ⇒ −1）。 */
    public static int encode(PocketUpgradeType type, boolean off) {
        if (type == null || type.ordinal() >= PocketConstants.UPGRADE_SLOTS) {
            return -1;
        }
        return type.ordinal() * 2 + (off ? 1 : 0);
    }

    /** arg → 型；越界（含 −1）⇒ {@code null}，调用方必须丢弃这条包而不是猜一个型。 */
    public static PocketUpgradeType typeOfArg(int arg) {
        final PocketUpgradeType[] values = PocketUpgradeType.values();
        if (arg < 0 || arg >= values.length * 2) {
            return null;
        }
        return values[arg / 2];
    }

    /** arg → 玩家请求的目标（{@code true} = 要关掉）。 */
    public static boolean offOfArg(int arg) {
        return (arg & 1) != 0;
    }

    /**
     * 这一型当前的开关现状（★读<b>载体栈 NBT</b>：服务端权威、客户端 vanilla 镜像，见类 javadoc）。
     * <p>
     * "在不在档上"用 {@code isActive ∨ isOff} 而<b>不是</b>第三次直读效果位图。为什么这不是近似式：
     * {@code isActive = 位图 ∧ ¬关闭}、{@code isOff = 关闭位}；关闭位只有
     * {@link PocketUpgradeSwitches#setOff} 写得出去，而 {@code setOff} 的类外调用点被门禁钉成
     * "恰 1 且点名"（唯一名点就是 {@link #commitSwitch}），本方法又只在两位皆 0 时<b>拒写</b>
     * ⇒ <b>归纳基</b>是没有关闭位的干净档，<b>归纳步</b>是"只给已在档上的型写关闭位"，
     * 于是"关闭位在场"必然意味着"位图那一位在场"，{@code isActive ∨ isOff} 与"位图有位"
     * 在<b>可达状态集</b>上等值。★直接问 {@code PocketUpgrades.hasUpgrade} 也成立，但那是门禁 E 段
     * 明令归零的第三条直调腿（R96 计划 §5 S2 验收 6：全仓总点数 8 → 2），拿它换掉上面这段论证不合算。
     */
    public static SwitchState switchState(ItemStack carrier, PocketUpgradeType type) {
        if (carrier == null || type == null) {
            return SwitchState.ABSENT;
        }
        if (PocketUpgradeSwitches.isActive(carrier, type)) {
            return SwitchState.ON;
        }
        return PocketUpgradeSwitches.isOff(carrier, type) ? SwitchState.OFF : SwitchState.ABSENT;
    }

    /** 下一次点击应请求的目标（★现读，不是装配期快照：面板是缓存件、行是常驻件）。 */
    public static boolean nextOff(ItemStack carrier, PocketUpgradeType type) {
        return !PocketUpgradeSwitches.isOff(carrier, type);
    }

    /**
     * ★服务端唯一的开关写腿（三判中的两判 + 落档）。
     * <p>
     * <b>判序是判据的一部分</b>：先问"这一型在不在档上"，再（只在要关的时候）跑守卫，最后才写。
     * 反过来就会给一型从未买过的插件写进关闭位 ⇒ 那一行显示"已关闭"，而它对应的是"没有这个升级"，
     * 既是假读数，也是"还能开回来"的错误暗示。
     * <p>
     * <b>关的方向跑守卫、开的方向不跑</b>：守卫问的是"关掉以后装不装得下"，开着不存在缩尺问题。
     * <p>
     * <b>请求的是目标值而不是"翻一下"</b>：同一目标重复到达 ⇒ 走 {@link Outcome#NO_CHANGE} 支，
     * 零写入、零同步包（重复包不会被重放成两次翻转，也满足"同值不刷 NBT"那条既有纪律）。
     * <p>
     * 线程：只在服务器主线程被调（{@code NekoPocketServerHandler} 的入口已按 R71 经
     * {@code ServerTaskScheduler} 投递过）。本方法<b>不</b>做持有者 / 载体身份判定 —— 那两个判据要读
     * {@code Container} 与 {@code EntityPlayer}，留在 handler 一侧（{@code serverGuardOk}）。
     *
     * @param carrier 载体口袋栈（★它的 NBT 根就是写入目标；{@code null} 或无根 ⇒ 判"不在档上"，
     *                <b>不建档</b>，口径同 {@code PocketUpgradeSwitches} 的读路径纪律 R53c）
     * @param wantOff 玩家请求的目标：{@code true} = 关掉
     * @param inv     当前面板会话的数据面（守卫的 long 真值与中栏扫描面），可为 {@code null}
     * @param cursor  玩家游标栈（堆叠守卫的扫描面必须含它，理由见 {@code PocketUpgradeGuards} 类 javadoc）
     */
    public static Outcome commitSwitch(ItemStack carrier, PocketUpgradeType type, boolean wantOff,
        PocketInventory inv, ItemStack cursor) {
        final SwitchState state = switchState(carrier, type);
        if (state == SwitchState.ABSENT) {
            return Outcome.NOT_ON_RECORD;
        }
        final boolean currentlyOff = state == SwitchState.OFF;
        if (currentlyOff == wantOff) {
            // ★目标 == 现值 ⇒ 一个字节都不动（连 setOff 都不必进：零写入、零同步包）。
            return Outcome.NO_CHANGE;
        }
        if (wantOff) {
            final PocketUpgradeGuards.OffVerdict verdict = PocketUpgradeGuards.canTurnOff(type, inv, carrier, cursor);
            if (!verdict.allowed()) {
                return verdict.reason() == PocketUpgradeGuards.Reason.CAPACITY_OVER_OFF_LIMIT
                    ? Outcome.REFUSE_CAPACITY
                    : Outcome.REFUSE_STACK;
            }
        }
        // 守卫过了还要认 setOff 自己的读数：它才是"本次是否真的改变了"的唯一裁判
        // （★不拿上面的 currentlyOff 反推写入结果，两处判断一旦分叉，回执就在说谎）。
        final net.minecraft.nbt.NBTTagCompound root = carrier == null ? null : carrier.getTagCompound();
        if (!PocketUpgradeSwitches.setOff(root, type, wantOff)) {
            return Outcome.NO_CHANGE;
        }
        return wantOff ? Outcome.TURNED_OFF : Outcome.TURNED_ON;
    }

    /**
     * 结论 → 玩家可见回执键（★粘性回执；{@link Outcome#NOT_ON_RECORD} 与两种守卫拒绝各一条键，不许并成
     * "关闭失败"：玩家要能分辨"罐还没腾""堆还没拆""这型根本没装"，并成一条就是让玩家自己试。
     * 口径同 {@code NekoPocketPanel#receiptOfReport} 把分区拒收与元件满分成两条键）。
     */
    public static String receiptKey(Outcome outcome) {
        switch (outcome) {
            case TURNED_OFF:
                return "gtit.pocket.receipt.upgrade.off";
            case TURNED_ON:
                return "gtit.pocket.receipt.upgrade.on";
            case NOT_ON_RECORD:
                return "gtit.pocket.receipt.upgrade.not_on_record";
            case REFUSE_CAPACITY:
                return "gtit.pocket.receipt.upgrade.refuse_capacity";
            case REFUSE_STACK:
                return "gtit.pocket.receipt.upgrade.refuse_stack";
            default:
                return "gtit.pocket.receipt.upgrade.no_change";
        }
    }

    /** 持有者 / 载体身份那一判的回执键（判据在 handler 侧，键与上面六条同族 ⇒ 列在本件做单源）。 */
    public static String identityReceiptKey() {
        return "gtit.pocket.receipt.upgrade.not_owner";
    }

    /** 开关按钮上的文字（三态各一条键；★"未装"不许显示成"已关闭"，那暗示着"还能开回来"）。 */
    public static String switchLabelKey(SwitchState state) {
        switch (state) {
            case ON:
                return "gtit.pocket.config.switch.turn_off";
            case OFF:
                return "gtit.pocket.config.switch.turn_on";
            default:
                return "gtit.pocket.config.switch.none";
        }
    }

    /** 行首"这一型现在开着没有"的读数键（三态，与 {@link #switchLabelKey} 共用同一个 {@link SwitchState}）。 */
    public static String stateKey(SwitchState state) {
        switch (state) {
            case ON:
                return "gtit.pocket.config.state.on";
            case OFF:
                return "gtit.pocket.config.state.off";
            default:
                return "gtit.pocket.config.state.absent";
        }
    }

    /**
     * 升级格 tooltip 的<b>关闭态读数</b>键：只在"这一型已被装上且被关掉"时追加一行
     * （开着与没装都不追加 —— 玩家不需要在两种正常态上读同一句话）。
     * <p>
     * ★这是"格 → 面板"之外唯一的开关可见面：光泽只有一个布尔位（S1 定案），逐型状态的读数就落在
     * 这一行与配置面板上。
     */
    public static String cellOffReadoutKeyOf(SwitchState state) {
        return state == SwitchState.OFF ? "gtit.pocket.upgrade.cell.off" : null;
    }

    /** 本件的全部 lang 键（★字面量清单，给两份 lang 的对账与用例点名用；不参与装配）。 */
    public static List<String> langKeys() {
        final List<String> keys = new ArrayList<>(Arrays.asList(
            "gtit.pocket.config.close",
            "gtit.pocket.config.state.on",
            "gtit.pocket.config.state.off",
            "gtit.pocket.config.state.absent",
            "gtit.pocket.config.switch.turn_on",
            "gtit.pocket.config.switch.turn_off",
            "gtit.pocket.config.switch.none",
            "gtit.pocket.config.switch.hint",
            "gtit.pocket.config.mount.magnet",
            "gtit.pocket.config.mount.mage",
            "gtit.pocket.config.mount.pending",
            "gtit.pocket.upgrade.cell.off",
            identityReceiptKey()));
        for (final Outcome outcome : Outcome.values()) {
            keys.add(receiptKey(outcome));
        }
        keys.addAll(Arrays.asList(UPGRADE_NAME_KEYS));
        return keys;
    }

    // ================================================================== 装配面（客户端控件树）

    /**
     * 构建次级面板（★只画控件与发码，不写档、不跑守卫）。
     * <p>
     * <b>树形恒定</b>（R32 同一条纪律的次级面板版）：行数 = {@code PocketUpgradeType.values().length}、
     * 挂载框数 = {@link #MOUNT_FRAMES}、回执行与关闭按钮各一枚，全部<b>不随</b>"哪一型开着"变化；
     * 状态只进 {@code IKey.dynamic} 的内容层 ⇒ 原位换字，不重排、不增删节点。
     * <p>
     * <b>装配期不执行点击谓词</b>：{@code onMousePressed} 在这里只是登记 lambda，服务端那次
     * {@code assemble()} 调用不会因此跑任何开关判定，也不会多出一条写腿。
     */
    public static ModularPanel build(NekoPocketPanel ui) {
        final ModularPanel panel = ModularPanel.defaultPanel(PANEL_NAME, WIDTH, HEIGHT);
        panel.child(receiptLine(ui));
        panel.child(closeButton(ui));
        final PocketUpgradeType[] types = PocketUpgradeType.values();
        for (int row = 0; row < types.length; row++) {
            panel.child(typeLabel(ui, types[row], row));
            panel.child(switchButton(ui, types[row], row));
        }
        int frame = 0;
        for (final PocketUpgradeType type : types) {
            // ★挂载框只给"有挂载位"的型画（判据来自分派表，本处不重写第二份型清单）
            if (hasMount(type)) {
                panel.child(mountFrame(type, frame));
                frame++;
            }
        }
        if (frame != MOUNT_FRAMES) {
            throw new IllegalStateException(
                "[pocket] 分派表画出的挂载框数与几何账不符: " + frame + " vs " + MOUNT_FRAMES);
        }
        return panel;
    }

    /** 第 {@code row} 行的型名 + 现状读数（★动态：型名与状态同一句里，读的是同一个 {@link SwitchState}）。
     * <p>
     * ★行带是<b>全宽</b>的，不是"开关列那么宽"：挂载框从 {@code mountY(0)} 起才排在开关列之<b>下</b>，
     * 五行这一段横向无人竞争 ⇒ 型名可用宽度 = 面板宽 − 两个边距 − 一枚开关 − 两像素缝。贴左排字，
     * 右端留给本行的开关（{@link #switchButton}），两者之间留 2px。英文最坏串（"Channel Persistence
     * Upgrade Module：Not installed" 逻辑宽 280）在 0.6 档下要 168px，只按开关列的 88px 排必然折第二行
     * 顶穿 20px 行盒 ⇒ 这条全宽带是"型名 + 现状一行放下"的唯一形状（用例
     * {@code config_panel_geometry_within_secondary_caps} 的像素账钉它）。
     */
    private static IWidget typeLabel(NekoPocketPanel ui, PocketUpgradeType type, int row) {
        final String nameKey = UPGRADE_NAME_KEYS[type.ordinal()];
        return (IWidget) new TextWidget(IKey.dynamic(() -> {
            final SwitchState state = switchState(ui.carrierStackLive(), type);
            return StatCollector.translateToLocal(nameKey) + "："
                + StatCollector.translateToLocal(stateKey(state));
        })).textAlign(Alignment.CenterLeft)
            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
            .color(PocketGhostRequest.readoutTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_config_label_" + type.ordinal())
            .pos(MARGIN, rowY(row))
            .size(WIDTH - 2 * MARGIN - SWITCH_WIDTH - 2, ROW_HEIGHT);
    }

    /** 第 {@code row} 行的开关（★唯一出口是发码，本地零写入；★排在本行最右端，与型名同一横带）。 */
    private static IWidget switchButton(NekoPocketPanel ui, PocketUpgradeType type, int row) {
        return new ButtonWidget<>().pos(WIDTH - MARGIN - SWITCH_WIDTH, rowY(row))
            .size(SWITCH_WIDTH, SWITCH_HEIGHT)
            .name("pocket_config_switch_" + type.ordinal())
            .background(PocketGuiTextures.BUTTON)
            .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
            .overlay(
                IKey.dynamic(
                    () -> StatCollector.translateToLocal(switchLabelKey(switchState(ui.carrierStackLive(), type)))))
            .tooltipDynamic(tooltip -> tooltip.addLine(IKey.lang("gtit.pocket.config.switch.hint")))
            .tooltipAutoUpdate(true)
            .playClickSound(true)
            // ★只有左键生效（同 R83 B2(5) 的口径：丢掉 button 形参 = 右键也切一次）；
            // ★★这里一个字节都不写档 —— 客户端私写 off-mask 是门禁 G12 / 门 D 钉死的 FAIL 形状。
            .onMousePressed(button -> button == 0
                && ui.requestUpgradeSwitch(type, nextOff(ui.carrierStackLive(), type)));
    }

    /** 回执行：最近一次切换成了什么 / 为什么被拒（★走既有粘性回执通道，不新建通道）。 */
    private static IWidget receiptLine(NekoPocketPanel ui) {
        return (IWidget) new TextWidget(IKey.dynamic(ui::upgradeConfigReceiptText)).textAlign(Alignment.CenterLeft)
            .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
            .color(PocketGhostRequest.hintTextColor())
            .shadow(Boolean.TRUE)
            .name("pocket_config_receipt")
            .pos(MARGIN, HEIGHT - MARGIN - CLOSE_HEIGHT - RECEIPT_HEIGHT)
            .size(WIDTH - 2 * MARGIN - CLOSE_WIDTH - 2, RECEIPT_HEIGHT);
    }

    /** 关闭本面的按钮（★只关面板，不动任何数据：本面无"待提交"状态，每次点开关都是即时单发）。 */
    private static IWidget closeButton(NekoPocketPanel ui) {
        return new ButtonWidget<>().pos(WIDTH - MARGIN - CLOSE_WIDTH, HEIGHT - MARGIN - CLOSE_HEIGHT)
            .size(CLOSE_WIDTH, CLOSE_HEIGHT)
            .name("pocket_config_close")
            .background(PocketGuiTextures.BUTTON)
            .hoverBackground(PocketGuiTextures.BUTTON_PRESSED)
            .overlay(IKey.lang("gtit.pocket.config.close"))
            .playClickSound(true)
            .onMousePressed(button -> button == 0 && ui.closeUpgradeConfig());
    }

    /**
     * 挂载位框（★S7 / S9 的内容落在这里）。本轮框里只有标题 + 一行"内容尚未提供"——
     * <b>这不是留白，是把位显出来</b>：分派表说了这一型有内容段，画面上就得有一块对得上那句话的框，
     * 否则 S7/S9 落地时玩家读到的是凭空长出的控件。★S7/S9 的替换点就是下面那枚 {@code pending} 文本件
     * （框位与列宽已由几何账定死，它们不需要改本件的布局）。
     */
    private static IWidget mountFrame(PocketUpgradeType type, int frame) {
        final int x = MARGIN + SWITCH_COLUMN + COLUMN_GAP;
        final ParentWidget<?> box = new ParentWidget<>().background(PocketGuiTextures.PANEL)
            .name("pocket_config_mount_" + type.ordinal())
            .pos(x, mountY(frame))
            .size(MOUNT_COLUMN, MOUNT_HEIGHT);
        final String titleKey = mountTitleKey(type);
        if (titleKey == null) {
            throw new IllegalStateException("[pocket] 分派表说这一型有挂载位，却没有标题键: " + type);
        }
        box.child(
            (IWidget) new TextWidget(IKey.lang(titleKey)).textAlign(Alignment.TopLeft)
                .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                .color(PocketGhostRequest.readoutTextColor())
                .shadow(Boolean.TRUE)
                .name("pocket_config_mount_title_" + type.ordinal())
                .pos(1, 1)
                .size(MOUNT_COLUMN - 2, ROW_HEIGHT));
        box.child(
            (IWidget) new TextWidget(IKey.lang("gtit.pocket.config.mount.pending")).textAlign(Alignment.TopLeft)
                .scale(PocketGhostRequest.RESIDENT_TEXT_SCALE)
                .color(PocketGhostRequest.hintTextColor())
                .shadow(Boolean.TRUE)
                .name("pocket_config_mount_pending_" + type.ordinal())
                .pos(1, ROW_HEIGHT)
                .size(MOUNT_COLUMN - 2, MOUNT_HEIGHT - ROW_HEIGHT));
        return box;
    }

    /** 第 {@code row} 行的 y（★单源：行高改了这里跟着走，调用点不写第二次）。 */
    public static int rowY(int row) {
        return MARGIN + row * ROW_HEIGHT;
    }

    /** 第 {@code frame} 个挂载框的 y（★排在开关列之下，与纵向预算同源）。 */
    public static int mountY(int frame) {
        return MARGIN + PocketUpgradeType.values().length * ROW_HEIGHT + 4 + frame * (MOUNT_HEIGHT + MOUNT_GAP);
    }

    /**
     * 型名键表（★整键字面量表，与 {@code NekoPocketBottomBand#UPGRADE_ITEM_NAME_KEYS} 同一组键的第二个
     * 消费面；下标 = ordinal = 位图位 = 槽号，三个空间同一个数）。
     */
    private static final String[] UPGRADE_NAME_KEYS = { "item.neko_pocket_upgrade_capacity.name",
        "item.neko_pocket_upgrade_stack.name", "item.neko_pocket_upgrade_magnet.name",
        "item.neko_pocket_upgrade_channel_persist.name", "item.neko_pocket_upgrade_distill_fast.name" };
}
