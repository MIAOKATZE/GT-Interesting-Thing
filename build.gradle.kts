
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// 测试源是手写零依赖断言套件（TestRunner.main，无 JUnit 注解）；
// Gradle 9 在存在 test 源但零发现时使 :test 直接失败，按报错提示关闭该门。
tasks.test {
    failOnNoDiscoveredTests = false
}
