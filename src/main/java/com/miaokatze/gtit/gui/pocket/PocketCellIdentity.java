package com.miaokatze.gtit.gui.pocket;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.miaokatze.gtit.common.items.pocket.PocketConstants;

/**
 * ★★<b>R92-⑤（D5）：空格子 tooltip 的"这一格是什么 + 三个功能键各干什么"读数单源</b>。
 * <p>
 * 用户口径："格子的 Tooltip 简化，对空格子只显示该格子是什么，和三个功能键的作用。"
 * 取证 B3 的实读与直觉相反：<b>三属性今天在 tooltip 里一行都没有</b>（三条长句 {@code *_gesture}
 * 只住在底部帮助块里，玩家点不到格子那一层）⇒ 对空格子而言本号不是"删到只剩两样"，而是
 * <b>把那两样补上</b>。有内容格子的 tooltip 一条未动（不在 D5 范围内）。
 * <p>
 * ★为什么要单独一个文件：三类格件（中栏 {@link NekoFilterSlot} / 流体条 {@link NekoPocketFluidSlot}
 * / 源质盘 {@link NekoPocketEssenceColumn}）都要挂同一组三行，写三遍迟早有一遍漏改键名
 * （与 {@code PocketGhostRequest} 里 cap 读数、角标几何同一个"三类共用一份"的纪律）。
 * ★本类<b>不含任何判据</b>：属性怎么迁移、哪个手势落哪一档全在 {@link PocketGhostRequest} 与
 * {@code PocketFilterConfig} 那侧；这里只是把同一件事实<b>说三遍</b>的地方收成一处。
 */
public final class PocketCellIdentity {

    private PocketCellIdentity() {}

    /**
     * 追加三行功能键短句（中键 / alt+左 / alt+右）。
     * <p>
     * ★短句住在格子上、长句（{@code gtit.pocket.storage.*_gesture}）留在底部帮助块：两套并存是刻意的
     * （D5 未撤帮助块），前者答"按哪个键"，后者答"撤销 / 并存 / NEI 落成"这些细节。
     */
    public static void addKeyHints(RichTooltip tooltip) {
        if (tooltip == null) {
            return;
        }
        tooltip.addLine(IKey.lang("gtit.pocket.storage.key.bind"));
        tooltip.addLine(IKey.lang("gtit.pocket.storage.key.memory"));
        tooltip.addLine(IKey.lang("gtit.pocket.storage.key.block"));
    }

    /**
     * 中栏格的身份行（"中栏 第 n 行 第 m 列"）。
     * <p>
     * ★行列号走 {@link PocketConstants#storageRowOf} / {@link PocketConstants#storageColumnOf}，
     * ★不在这里写 {@code / 9 + 1}；读不出身份（未绑定 / 越界 ⇒ 0）就<b>一行都不加</b>，
     * 不拿越界索引去凑一个看起来合法的行列号。
     */
    public static void addStorageIdentity(RichTooltip tooltip, int slotIndex) {
        if (tooltip == null) {
            return;
        }
        final int row = PocketConstants.storageRowOf(slotIndex);
        final int column = PocketConstants.storageColumnOf(slotIndex);
        if (row <= 0 || column <= 0) {
            return;
        }
        tooltip.addLine(IKey.lang("gtit.pocket.legend.storage_of", Integer.valueOf(row), Integer.valueOf(column)));
    }

    /** 源质盘格的身份行（"源质盘 第 n 格"；换算单源同 {@link #addStorageIdentity}）。 */
    public static void addEssenceIdentity(RichTooltip tooltip, int cellIndex) {
        if (tooltip == null) {
            return;
        }
        final int number = PocketConstants.essenceCellNumberOf(cellIndex);
        if (number <= 0) {
            return;
        }
        tooltip.addLine(IKey.lang("gtit.pocket.legend.essence_cell", Integer.valueOf(number)));
    }
}
