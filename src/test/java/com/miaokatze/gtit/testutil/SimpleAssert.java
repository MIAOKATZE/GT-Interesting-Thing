package com.miaokatze.gtit.testutil;

/**
 * 极简断言工具。
 * <p>
 * 环境说明：本仓库 GTNH convention（gtnhgradle 2.0.x）的 test source set 未提供任何
 * 测试框架依赖（junit/testng 均不在 testCompileClasspath/testRuntimeClasspath 上），
 * 且 build 文件不在本次改动许可范围，故测试用例为零依赖纯 Java 断言套件
 * （本类 + {@link TestRunner}），由各测试类的 {@code main} 入口执行。
 */
public final class SimpleAssert {

    private SimpleAssert() {}

    /** 条件不成立抛 {@link AssertionError} */
    public static void that(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    /** 相等断言（null 安全） */
    public static void eq(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + "：期望 <" + expected + ">，实际 <" + actual + ">");
        }
    }
}
