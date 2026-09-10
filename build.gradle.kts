
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// 测试源是手写零依赖断言套件（TestRunner.main，无 JUnit 注解）；
// Gradle 9 在存在 test 源但零发现时使 :test 直接失败，按报错提示关闭该门。
tasks.test {
    failOnNoDiscoveredTests = false
}

// 周目零依赖测试套件手动入口：用例注册在 ReincarnationStoreTest.main（内部驱动
// testutil.TestRunner，任一失败以非零退出码结束），不占用 Gradle :test 发现机制。
// 运行：gradlew runStoreTest
val storeTestRuntimeClasspath = sourceSets.getByName("test").runtimeClasspath

tasks.register<JavaExec>("runStoreTest") {
    group = "verification"
    description = "Runs the zero-dependency reincarnation test suite (ReincarnationStoreTest.main)."
    mainClass = "com.miaokatze.gtit.reincarnation.ReincarnationStoreTest"
    classpath = storeTestRuntimeClasspath
}

// v1.8.6 发放门控零依赖测试套件：同 runStoreTest 模式（用例注册在
// ReincarnationGrantGateTest.main，任一失败以非零退出码结束）。
// 运行：gradlew runGateTest
tasks.register<JavaExec>("runGateTest") {
    group = "verification"
    description = "Runs the zero-dependency reincarnation grant gate test suite (ReincarnationGrantGateTest.main)."
    mainClass = "com.miaokatze.gtit.reincarnation.ReincarnationGrantGateTest"
    classpath = storeTestRuntimeClasspath
}
