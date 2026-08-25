package com.miaokatze.gtit.testutil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 极简用例执行器：逐用例隔离异常（失败记录、不中断其余），末尾输出汇总，
 * 任一失败以非零退出码结束（供 CI/脚本判定）。
 */
public final class TestRunner {

    private TestRunner() {}

    public static void run(Class<?> suite, Map<String, Runnable> cases) {
        List<String> failed = new ArrayList<>();
        for (Map.Entry<String, Runnable> entry : cases.entrySet()) {
            try {
                entry.getValue()
                    .run();
            } catch (Throwable t) {
                failed.add(entry.getKey() + " -> " + t);
            }
        }
        System.out.println("[" + suite.getName() + "] " + (cases.size() - failed.size()) + "/" + cases.size() + " 通过");
        if (!failed.isEmpty()) {
            for (String f : failed) {
                System.err.println("  FAIL " + f);
            }
            throw new IllegalStateException("测试套件存在失败用例: " + suite.getName());
        }
    }
}
