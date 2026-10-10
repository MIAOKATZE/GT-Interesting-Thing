
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

// v1.8.17 默认贸易体系零依赖测试套件：同 runStoreTest 模式（用例注册在
// DefaultTradeSyncTest.main，任一失败以非零退出码结束）。
// 运行：gradlew runTradeSyncTest
tasks.register<JavaExec>("runTradeSyncTest") {
    group = "verification"
    description = "Runs the zero-dependency default trade sync test suite (DefaultTradeSyncTest.main)."
    mainClass = "com.miaokatze.gtit.trade.api.DefaultTradeSyncTest"
    classpath = storeTestRuntimeClasspath
}

// v1.8.22 无限元件嵌套通道探测零依赖测试套件（Issue #16 回归）：同 runStoreTest 模式。
// 运行：gradlew runNestedCellProbeTest
tasks.register<JavaExec>("runNestedCellProbeTest") {
    group = "verification"
    description = "Runs the zero-dependency nested-cell probe test suite (InfinityNestedCellProbeTest.main)."
    mainClass = "com.miaokatze.gtit.common.items.infinitycell.InfinityNestedCellProbeTest"
    classpath = storeTestRuntimeClasspath
}

// v1.8.22 无限元件通道分桶与旧档兼容零依赖测试套件：同 runStoreTest 模式。
// 运行：gradlew runStorageTypeKeyTest
tasks.register<JavaExec>("runStorageTypeKeyTest") {
    group = "verification"
    description = "Runs the zero-dependency infinity-cell channel bucket test suite (InfinityStorageTypeKeyTest.main)."
    mainClass = "com.miaokatze.gtit.common.items.infinitycell.InfinityStorageTypeKeyTest"
    classpath = storeTestRuntimeClasspath
}

// 猫猫次元口袋数据模型与通道状态机零依赖测试套件：同 runStoreTest 模式（用例注册在
// NekoPocketModelTest.main，任一失败以非零退出码结束）。模型与原生滚动回归，附加fastutil测试依赖。
// 运行：gradlew runPocketTest
tasks.register<JavaExec>("runPocketTest") {
    group = "verification"
    description = "Runs dimensional pocket model and native scrollbar regressions (NekoPocketModelTest.main)."
    mainClass = "com.miaokatze.gtit.common.items.pocket.NekoPocketModelTest"
    classpath = storeTestRuntimeClasspath
    classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
    classpath += configurations.detachedConfiguration(dependencies.create("org.joml:joml:1.10.8"))
}

// 抽奖槽数、草稿和轮盘布局纯 JVM 回归。
tasks.register<JavaExec>("runLotterySlotsTest") {
    group = "verification"
    description = "Runs lottery slot editing, draft and wheel layout regressions."
    mainClass = "com.miaokatze.gtit.lottery.LotterySlotsTest"
    classpath = storeTestRuntimeClasspath
    val scratchDir = layout.buildDirectory.dir("lottery-slots-test")
    workingDir(scratchDir)
    systemProperty("gtit.lottery.testScratch", "true")
    doFirst { scratchDir.get().asFile.mkdirs() }
}

tasks.named("check") { dependsOn("runLotterySlotsTest") }

// 交易槽 OR、矿词、共享库存分配和随机产出的真实物品回归。
tasks.register<JavaExec>("runTradeMatchingTest") {
    group = "verification"
    description = "Runs multi-option and ore-dictionary trade matching regressions."
    mainClass = "com.miaokatze.gtit.trade.v2.NekoTradeMatchingTest"
    classpath = storeTestRuntimeClasspath
    val scratchDir = layout.buildDirectory.dir("trade-matching-test")
    workingDir(scratchDir)
    doFirst { scratchDir.get().asFile.mkdirs() }
}

tasks.named("check") { dependsOn("runTradeMatchingTest") }

tasks.register<JavaExec>("runTradePageRefreshTest") {
    group = "verification"
    description = "Runs trade tab selection, pagination and registry revision regression checks."
    mainClass = "com.miaokatze.gtit.gui.vm.TradePageRefreshTest"
    classpath = storeTestRuntimeClasspath
    classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
}

tasks.named("check") { dependsOn("runTradePageRefreshTest") }

tasks.register<JavaExec>("runHologramTest") {
    group = "verification"
    description = "Runs hologram channel, packet and inactive capture regressions."
    mainClass = "com.miaokatze.gtit.hologram.HologramRegressionTest"
    classpath = storeTestRuntimeClasspath
    val scratchDir = layout.buildDirectory.dir("hologram-test")
    workingDir(scratchDir)
    doFirst { scratchDir.get().asFile.mkdirs() }
}

tasks.named("check") { dependsOn("runHologramTest") }

if (providers.gradleProperty("hologramSmoke").isPresent) {
    extensions.configure<com.gtnewhorizons.retrofuturagradle.MinecraftExtension>("minecraft") {
        extraRunJvmArguments.add("-Dgtit.hologram.smoketest=true")
    }
    tasks.named<JavaExec>("runServer") {
        workingDir(layout.buildDirectory.dir("hologram-smoke/server"))
    }
}

if (providers.gradleProperty("hologramClientSmoke").isPresent) {
    extensions.configure<com.gtnewhorizons.retrofuturagradle.MinecraftExtension>("minecraft") {
        extraRunJvmArguments.add("-Dgtit.hologram.clientSmoke=true")
        extraRunJvmArguments.add("-Dgtit.hologram.clientState=" + layout.buildDirectory.file("hologram-smoke/server/hologram-client-state.nbt").get().asFile.absolutePath)
    }
    tasks.named<JavaExec>("runClient") {
        workingDir(layout.buildDirectory.dir("hologram-smoke/client"))
        args("--width", "1100", "--height", "800")
    }
}
