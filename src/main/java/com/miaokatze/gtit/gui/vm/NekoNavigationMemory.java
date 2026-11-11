// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gui.vm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 客户端浏览偏好；编辑导航只保留在窗口内，永不写入文件。 */
final class NekoNavigationMemory {

    static final class Selection {

        int mainTab;
        int scope;
        final int[] tabIds = { -1, -1, -1 };
        int signInTab;
        int mailTab;
        String poolId = "";

        Selection copy() {
            Selection copy = new Selection();
            copy.mainTab = mainTab;
            copy.scope = scope;
            System.arraycopy(tabIds, 0, copy.tabIds, 0, tabIds.length);
            copy.signInTab = signInTab;
            copy.mailTab = mailTab;
            copy.poolId = poolId;
            return copy;
        }
    }

    private final Path file;
    final Selection browsing = new Selection();
    private Selection editing;

    NekoNavigationMemory(Path file) {
        this.file = file;
    }

    void load() throws IOException {
        if (!Files.exists(file)) return;
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
        }
        Selection loaded = new Selection();
        try {
            loaded.mainTab = Integer.parseInt(values.getProperty("mainTab", "0"));
            loaded.scope = Integer.parseInt(values.getProperty("tradeScope", "0"));
            loaded.signInTab = Integer.parseInt(values.getProperty("signInTab", "0"));
            loaded.mailTab = Integer.parseInt(values.getProperty("mailTab", "0"));
            loaded.poolId = values.getProperty("poolId", "");
            for (int i = 0; i < loaded.tabIds.length; i++) {
                loaded.tabIds[i] = Integer.parseInt(values.getProperty("tabId." + i, "-1"));
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid Neko vending navigation preference: " + file, e);
        }
        browsing.mainTab = loaded.mainTab >= 0 && loaded.mainTab < 4 ? loaded.mainTab : 0;
        browsing.scope = loaded.scope >= 0 && loaded.scope < 3 ? loaded.scope : 0;
        browsing.signInTab = loaded.signInTab >= 0 && loaded.signInTab < 4 ? loaded.signInTab : 0;
        browsing.mailTab = loaded.mailTab >= 0 && loaded.mailTab < 5 ? loaded.mailTab : 0;
        browsing.poolId = loaded.poolId;
        System.arraycopy(loaded.tabIds, 0, browsing.tabIds, 0, loaded.tabIds.length);
    }

    Selection selection(boolean editMode) {
        if (!editMode) return browsing;
        if (editing == null) editing = browsing.copy();
        return editing;
    }

    /** 只在普通浏览位置确实变化时写盘，初始化和编辑导航均不产生写入。 */
    void remember(boolean editMode, int mainTab, int scope, int tabId, int signInTab, int mailTab, String poolId)
        throws IOException {
        Selection selected = selection(editMode);
        boolean changed = selected.mainTab != mainTab || selected.scope != scope
            || selected.tabIds[scope] != tabId
            || selected.signInTab != signInTab
            || selected.mailTab != mailTab
            || !selected.poolId.equals(poolId);
        selected.mainTab = mainTab;
        selected.scope = scope;
        selected.tabIds[scope] = tabId;
        selected.signInTab = signInTab;
        selected.mailTab = mailTab;
        selected.poolId = poolId;
        if (editMode || !changed) return;
        Properties values = new Properties();
        values.setProperty("mainTab", Integer.toString(browsing.mainTab));
        values.setProperty("tradeScope", Integer.toString(browsing.scope));
        values.setProperty("signInTab", Integer.toString(browsing.signInTab));
        values.setProperty("mailTab", Integer.toString(browsing.mailTab));
        values.setProperty("poolId", browsing.poolId);
        for (int i = 0; i < browsing.tabIds.length; i++) {
            values.setProperty("tabId." + i, Integer.toString(browsing.tabIds[i]));
        }
        Files.createDirectories(file.getParent());
        try (OutputStream output = Files.newOutputStream(file)) {
            values.store(output, "GTIT Neko vending navigation (client browsing only)");
        }
    }
}
