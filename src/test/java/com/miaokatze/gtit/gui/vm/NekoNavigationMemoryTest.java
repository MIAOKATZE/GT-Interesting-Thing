// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.gui.vm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** 在独立 JVM 中验证普通导航落盘、稳定 ID 和编辑导航隔离。 */
public final class NekoNavigationMemoryTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            Path file = Files.createTempDirectory("gtit-navigation-")
                .resolve("navigation.properties");
            runChild("write", file);
            runChild("read", file);
            System.out.println("NekoNavigationMemoryTest: cross-JVM persistence and editor isolation passed");
            return;
        }
        Path file = Paths.get(args[1]);
        NekoNavigationMemory memory = new NekoNavigationMemory(file);
        memory.load();
        if ("write".equals(args[0])) {
            check(memory.browsing.mainTab == 0 && memory.browsing.scope == 0, "首次打开默认贸易主界面");
            check(Arrays.equals(memory.browsing.tabIds, new int[] { -1, -1, -1 }), "各范围首次默认收藏");
            memory.remember(false, 0, 0, -400001, 0, 0, "normal-pool");
            memory.remember(false, 0, 1, 27, 2, 0, "normal-pool");
            memory.remember(false, 3, 2, -500003, 2, 4, "normal-pool");
            byte[] saved = Files.readAllBytes(file);
            memory.remember(true, 2, 1, 90, 3, 1, "editor-pool");
            memory.remember(true, 1, 0, -400002, 3, 1, "editor-pool");
            check(Arrays.equals(saved, Files.readAllBytes(file)), "编辑导航绝不修改磁盘浏览偏好");
            check(memory.selection(false).mainTab == 3 && memory.selection(false).scope == 2, "退出编辑恢复浏览主页面和范围");
            check(Arrays.equals(memory.selection(false).tabIds, new int[] { -400001, 27, -500003 }), "编辑选择不污染任何浏览范围");
            check(memory.selection(true).mainTab == 1 && memory.selection(true).tabIds[1] == 90, "窗口内编辑范围各自保留选择");
            check(
                memory.browsing.signInTab == 2 && memory.browsing.mailTab == 4
                    && "normal-pool".equals(memory.browsing.poolId),
                "编辑不污染签到、邮件和抽奖池浏览选择");
        } else {
            check(memory.browsing.mainTab == 3 && memory.browsing.scope == 2, "重启恢复邮件主界面与预设范围");
            check(Arrays.equals(memory.browsing.tabIds, new int[] { -400001, 27, -500003 }), "重启按稳定 ID 分别恢复三个范围");
            check(
                memory.browsing.signInTab == 2 && memory.browsing.mailTab == 4
                    && "normal-pool".equals(memory.browsing.poolId),
                "重启恢复签到、邮件子标签及抽奖池 ID");
            check(
                memory.selection(true).mainTab == 3 && memory.selection(true).tabIds[1] == 27,
                "新窗口的编辑导航来自浏览位置，不恢复旧编辑状态");
        }
    }

    private static void runChild(String phase, Path file) throws Exception {
        Process child = new ProcessBuilder(
            Paths.get(System.getProperty("java.home"), "bin", "java")
                .toString(),
            "-cp",
            System.getProperty("java.class.path"),
            NekoNavigationMemoryTest.class.getName(),
            phase,
            file.toString()).inheritIO()
                .start();
        check(child.waitFor() == 0, "导航偏好子进程失败：" + phase);
    }
}
