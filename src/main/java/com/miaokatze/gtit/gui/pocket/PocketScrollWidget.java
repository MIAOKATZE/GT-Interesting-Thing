package com.miaokatze.gtit.gui.pocket;

import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;

/** 三个存储区共用的原生滚动容器；保留主题颜色，滚动条使用口袋材质。 */
public final class PocketScrollWidget extends ScrollWidget<PocketScrollWidget> {

    public static final int SCROLLBAR_WIDTH = 6;

    public PocketScrollWidget(int contentHeight) {
        super(new VerticalScrollData(false, SCROLLBAR_WIDTH));
        getScrollArea().getScrollY()
            .setScrollSize(contentHeight);
        getScrollArea().getScrollY()
            .setScrollSpeed(NekoPocketPanel.GRID);
        getScrollArea().getScrollY()
            .texture(PocketGuiTextures.SCROLLBAR);
        showScrollShadows(false);
    }
}
