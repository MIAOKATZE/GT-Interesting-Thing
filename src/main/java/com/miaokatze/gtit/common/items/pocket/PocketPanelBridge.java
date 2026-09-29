package com.miaokatze.gtit.common.items.pocket;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

/**
 * ★R100 片 F（架构解耦）：口袋主面板的「gui 侧实现注入点」。
 * <p>
 * 旧形状是 {@code ItemNekoDimensionPocket#buildUI} 直接 {@code import} 并调用
 * {@code gui/pocket/NekoPocketPanel.build}——common 反向依赖 gui 包（依赖方向唯一允许 gui→common）。
 * 本类把那一次调用改成经此处的<b>工厂委托</b>：gui 侧实现由双端都会跑的
 * {@code CommonProxy#init} 注册（{@code NekoPocketPanel::build}），物品类只认本桥。
 * <p>
 * ★为什么注册点必须<b>双端</b>：MUI2 的 {@code IGuiHolder#buildUI} 在服务端也要构建面板树
 * （同步所需，见物品类 {@code buildUI} 的 javadoc「双端各走一遍 buildUI」）⇒ 只在客户端注入
 * 会让专用服上第一次开屏就炸「工厂未注册」。{@code ClientProxy#init} 首行
 * {@code super.init(event)} 会带上这段注册，两侧同享。
 * <p>
 * 行为零变化：委托目标就是原来那次静态调用本身，无任何包裹逻辑。
 */
public final class PocketPanelBridge {

    /** 与 {@code NekoPocketPanel.build(PlayerInventoryGuiData, PanelSyncManager, UISettings)} 同形的工厂口。 */
    @FunctionalInterface
    public interface PanelFactory {

        ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager syncManager, UISettings settings);
    }

    private static volatile PanelFactory factory;

    private PocketPanelBridge() {}

    /** gui 侧（{@code CommonProxy#init}，双端）在开屏前注册实现；后注册者覆盖先注册者。 */
    public static void register(PanelFactory panelFactory) {
        factory = panelFactory;
    }

    /**
     * {@code ItemNekoDimensionPocket#buildUI} 的唯一出口：转发到已注册的 gui 侧工厂。
     *
     * @throws IllegalStateException init 尚未跑过就有人开屏（工程上不可达：开屏必须先有玩家）
     */
    public static ModularPanel build(PlayerInventoryGuiData data, PanelSyncManager syncManager, UISettings settings) {
        final PanelFactory panelFactory = factory;
        if (panelFactory == null) {
            throw new IllegalStateException("口袋主面板工厂未注册：CommonProxy#init 未执行（gui 侧实现注入点 PocketPanelBridge.register）");
        }
        return panelFactory.build(data, syncManager, settings);
    }
}
