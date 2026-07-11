package com.miaokatze.gtit.client.gui;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.DoubleValue.Dynamic;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.miaokatze.gtit.config.NekoMusicConfig;

/**
 * 音量控制面板
 * <p>
 * 全量复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.VolumeControlGui}。
 * <p>
 * 提供一个浮动面板，包含：
 * <ul>
 * <li>标题文本（本地化 key: {@code gtit.gui.volume.title}）</li>
 * <li>音量滑块（0.01~1.0，绑定 {@link NekoMusicConfig#music_volume}）</li>
 * <li>百分比文本（本地化 key: {@code gtit.gui.volume.percent}）</li>
 * <li>关闭按钮</li>
 * </ul>
 * <p>
 * 与 VM 原版的差异：
 * <ul>
 * <li>使用 {@link NekoMusicConfig#music_volume} 替代 VMConfig.music.music_volume</li>
 * <li>使用 {@link ConfigurationManager#save} 保存 NekoMusicConfig.class 替代 VMConfig.class</li>
 * <li>使用固定颜色 {@code 0xFF000000} 替代 ColorUtils.volumeSliderBackground</li>
 * <li>本地化 key 从 vendingmachine.gui.volume.* 迁移到 gtit.gui.volume.*</li>
 * <li>移除了 VMMusicManager.onVolumeChange() 调用 —— NekoMusicEventHandler 在 tick 中读取 music_volume，自动应用新音量</li>
 * </ul>
 * <p>
 * 音量变化处理机制：
 * 滑块值变化时更新 NekoMusicConfig.music_volume，NekoMusicEventHandler 在每 tick 的
 * onClientTick() 中读取此值作为音量倍率，通过 SoundSystem.setVolume() 应用到 BGM。
 * 因此无需像 VM 那样显式调用 onVolumeChange()。
 */
public class NekoVolumeControlGui {

    /** 滑块背景颜色（纯黑 0xFF000000，与 VM 的 ColorUtils.volumeSliderBackground 一致） */
    private static final int SLIDER_BACKGROUND_COLOR = 0xFF000000;

    /** 音量是否被修改（用于关闭时决定是否保存配置） */
    private boolean volumeChanged = false;

    /**
     * 创建音量控制面板
     * <p>
     * 面板会定位在父组件下方 5 像素处，关闭时自动保存配置（如果音量被修改过）。
     *
     * @param syncManager 面板同步管理器
     * @param parent      父组件（用于相对定位）
     * @return 配置好的 ModularPanel
     */
    public final ModularPanel createPanel(PanelSyncManager syncManager, IWidget parent) {
        ModularPanel panel = new ModularPanel("volume").coverChildren();
        addWidgets(panel, syncManager);
        panel.child(ButtonWidget.panelCloseButton());
        if (syncManager.isClient()) {
            // 面板定位在父组件正下方
            panel.relative(parent)
                .topRel(1f, 5, 0);
            // 面板关闭时，如果音量被修改过，保存配置到磁盘
            panel.onCloseAction(() -> {
                if (volumeChanged) {
                    ConfigurationManager.save(NekoMusicConfig.class);
                }
            });
        }
        return panel;
    }

    /**
     * 向面板添加子组件
     * <p>
     * 仅在客户端添加组件，服务端返回空面板。
     * 布局结构：
     * 
     * <pre>
     * ┌─────────────────────────┐
     * │ 标题文本                 │
     * │ [====滑块====] 50%      │
     * └─────────────────────────┘
     * </pre>
     *
     * @param panel       面板
     * @param syncManager 同步管理器
     */
    private void addWidgets(ModularPanel panel, PanelSyncManager syncManager) {
        if (!syncManager.isClient()) return;
        panel.coverChildren()
            .padding(5)
            .child(
                Flow.column()
                    .coverChildren()
                    .child(
                        // 标题文本
                        IKey.lang("gtit.gui.volume.title")
                            .asWidget()
                            .paddingRight(20)
                            .leftRel(0))
                    .child(
                        Flow.row()
                            .coverChildren()
                            .marginTop(5)
                            .child(
                                // 音量滑块（0.01~1.0，双向绑定 NekoMusicConfig.music_volume）
                                new SliderWidget().bounds(0.01f, 1f)
                                    .width(100)
                                    .height(10)
                                    .marginRight(5)
                                    .verticalCenter()
                                    .background(new Rectangle().color(SLIDER_BACKGROUND_COLOR))
                                    .value(
                                        new Dynamic(
                                            // getter: 读取当前配置值
                                            () -> NekoMusicConfig.music_volume,
                                            // setter: 写入配置值并标记已修改
                                            value -> {
                                                NekoMusicConfig.music_volume = (float) value;
                                                // NekoMusicEventHandler 在 tick 中读取 music_volume，
                                                // 自动应用新音量，无需显式调用 onVolumeChange()
                                                volumeChanged = true;
                                            })))
                            .child(
                                // 百分比文本（动态更新）
                                IKey.dynamic(
                                    () -> StatCollector
                                        .translateToLocalFormatted("gtit.gui.volume.percent", getVolumeAsString()))
                                    .asWidget()
                                    .width(30)
                                    .verticalCenter())));
    }

    /**
     * 获取音量百分比字符串
     * <p>
     * 将 music_volume (0.01~1.0) 转换为百分比整数 (1~100)。
     * 用于滑块右侧的百分比显示。
     *
     * @return 音量百分比字符串（如 "50"）
     */
    public static String getVolumeAsString() {
        return Integer.toString((int) (NekoMusicConfig.music_volume * 100));
    }
}
