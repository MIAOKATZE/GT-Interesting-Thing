
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

tasks.register<JavaExec>("runPocketFluidClickTest") {
    group = "verification"
    description = "Runs real MUI fluid click packets through dirty-gated NBT persistence."
    mainClass = "com.miaokatze.gtit.common.items.pocket.PocketFluidClickTest"
    classpath = storeTestRuntimeClasspath
    classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
    val scratchDir = layout.buildDirectory.dir("pocket-fluid-click-test")
    workingDir(scratchDir)
    doFirst { scratchDir.get().asFile.mkdirs() }
}

tasks.named("check") { dependsOn("runPocketFluidClickTest") }

tasks.register<JavaExec>("runInfinityCellTransferTest") {
    group = "verification"
    description = "Checks exact long-count conservation across infinite-cell channels."
    mainClass = "com.miaokatze.gtit.common.items.infinitycell.InfinityCellTransferTest"
    classpath = storeTestRuntimeClasspath
    classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
    classpath += configurations.getByName("nativeLwjglTestRuntime")
}

tasks.register<JavaExec>("runPocketTaggedFluidTest") {
    group = "verification"
    description = "Checks tagged fluid identity through pocket tanks, containers and persistence."
    mainClass = "com.miaokatze.gtit.common.items.pocket.PocketTaggedFluidTest"
    classpath = storeTestRuntimeClasspath
    classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
    val scratchDir = layout.buildDirectory.dir("pocket-tagged-fluid-test")
    workingDir(scratchDir)
    doFirst { scratchDir.get().asFile.mkdirs() }
}

tasks.named("check") { dependsOn("runInfinityCellTransferTest", "runPocketTaggedFluidTest") }

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

// NEI production query entry points need these client classes only in test runtimes.
val neiTestRuntimeClasspath = configurations.detachedConfiguration(
    dependencies.create("com.github.GTNewHorizons:NotEnoughItems:2.8.171-GTNH:dev"),
    dependencies.create("com.github.GTNewHorizons:CodeChickenCore:1.4.22:dev"),
    dependencies.create("org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209"),
    dependencies.create("org.lwjgl.lwjgl:lwjgl_util:2.9.4-nightly-20150209")
).apply { isTransitive = false }

tasks.register<JavaExec>("runNekoTradeNeiTest") {
    group = "verification"
    description = "Runs native NEI trade item, all-recipes and catalyst query regressions."
    mainClass = "com.miaokatze.gtit.client.nei.NekoTradeNeiTestBootstrap"
    classpath = storeTestRuntimeClasspath
    classpath += neiTestRuntimeClasspath
    val scratchDir = layout.buildDirectory.dir("nei-handler-test")
    workingDir(scratchDir)
    doFirst { scratchDir.get().asFile.mkdirs() }
}

tasks.named("check") { dependsOn("runNekoTradeNeiTest") }

mapOf(
    "runNekoTextFitTest" to "com.miaokatze.gtit.client.gui.NekoTextFitTest",
    "runNekoTradePresentationTest" to "com.miaokatze.gtit.client.gui.NekoTradePresentationTest",
    "runNekoTradeCardLayoutTest" to "com.miaokatze.gtit.client.gui.NekoTradeCardLayoutTest",
    "runNekoWorkbenchLayoutTest" to "com.miaokatze.gtit.client.gui.NekoWorkbenchLayoutTest",
    "runNekoGhostSlotTest" to "com.miaokatze.gtit.client.gui.NekoGhostSlotTest",
    "runNekoEditDragGeometryTest" to "com.miaokatze.gtit.client.gui.NekoEditDragGeometryTest",
    "runLotteryWheelGeometryTest" to "com.miaokatze.gtit.lottery.LotteryWheelGeometryTest",
    "runLotteryDeliveryPlanTest" to "com.miaokatze.gtit.lottery.LotteryDeliveryPlanTest",
    "runNekoEmbeddedNeiProbeTest" to "com.miaokatze.gtit.client.nei.NekoEmbeddedNeiProbeTest",
    "runTradeEditorDraftContractTest" to "com.miaokatze.gtit.gui.vm.edit.TradeEditorDraftContractTest",
    "runTradeEditPreconditionTest" to "com.miaokatze.gtit.trade.v2.TradeEditPreconditionTest",
    "runTerminalClientDataTest" to "com.miaokatze.gtit.terminal.TerminalClientDataTest",
    "runClientConnectionCacheTest" to "com.miaokatze.gtit.terminal.ClientConnectionCacheTest",
    "runNekoVendingLayoutTest" to "com.miaokatze.gtit.gui.vm.NekoVendingLayoutTest",
    "runNekoOutputStateTest" to "com.miaokatze.gtit.common.machine.v2.NekoOutputStateTest",
    "runNekoOutputBodyGeometryTest" to "com.miaokatze.gtit.common.machine.v2.NekoOutputBodyGeometryTest",
    "runNekoTvStructureTest" to "com.miaokatze.gtit.common.machine.v2.NekoTvStructureTest",
    "runVendingSceneContracts" to "com.miaokatze.gtit.gui.vm.scene.VendingSceneContracts",
    "runVendingActionCompletionTest" to "com.miaokatze.gtit.gui.vm.VendingActionCompletionTest"
).forEach { (taskName, entryPoint) ->
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        mainClass = entryPoint
        classpath = storeTestRuntimeClasspath + sourceSets.getByName("main").compileClasspath + neiTestRuntimeClasspath
        classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
        classpath += configurations.detachedConfiguration(dependencies.create("org.joml:joml:1.10.8"))
    }
    tasks.named("check") { dependsOn(taskName) }
}

tasks.register<JavaExec>("runGtmbProjectorTest") {
    group = "verification"
    description = "Runs optional GTMB projector ownership, trade, quest and native Forge remapping regressions."
    mainClass = "com.miaokatze.gtit.trade.api.GtmbProjectorCompatibilityTest"
    classpath = storeTestRuntimeClasspath
    classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
    val scratchDir = layout.buildDirectory.dir("gtmb-projector-test")
    workingDir(scratchDir)
    systemProperty("gtit.projector.testScratch", "true")
    doFirst { scratchDir.get().asFile.mkdirs() }
}

tasks.named("check") { dependsOn("runGtmbProjectorTest") }

tasks.register<JavaExec>("runTradePageRefreshTest") {
    group = "verification"
    description = "Runs trade tab selection, pagination and registry revision regression checks."
    mainClass = "com.miaokatze.gtit.gui.vm.TradePageRefreshTest"
    classpath = storeTestRuntimeClasspath
    classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
}

tasks.named("check") { dependsOn("runTradePageRefreshTest") }

tasks.register<JavaExec>("runNekoMusicConfigTest") {
    group = "verification"
    description = "Runs real client music config persistence across separate JVM restarts."
    mainClass = "com.miaokatze.gtit.config.NekoMusicConfigTest"
    classpath = storeTestRuntimeClasspath
    classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
    val scratchDir = layout.buildDirectory.dir("music-config-test")
    workingDir(scratchDir)
    doFirst { scratchDir.get().asFile.mkdirs() }
}

tasks.named("check") { dependsOn("runNekoMusicConfigTest") }

tasks.register<JavaExec>("runTestLabTest") {
    group = "verification"
    description = "Runs automation lab default gates, recovery bounds and report result invariants."
    mainClass = "com.miaokatze.gtit.testlab.TestLabFrameworkTest"
    classpath = storeTestRuntimeClasspath
    classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
}

tasks.named("check") { dependsOn("runTestLabTest") }

tasks.register<JavaExec>("runDefaultContentPolicyTest") {
    group = "verification"
    description = "Checks independent bundled-content gates and preservation of custom/empty lottery files."
    mainClass = "com.miaokatze.gtit.lottery.DefaultContentPolicyTest"
    classpath = storeTestRuntimeClasspath
    classpath += sourceSets.getByName("main").compileClasspath
    classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
    val scratchDir = layout.buildDirectory.dir("content-policy-test")
    workingDir(scratchDir)
    doFirst { scratchDir.get().asFile.mkdirs() }
}

tasks.named("check") { dependsOn("runDefaultContentPolicyTest") }

val contentTestCompileClasspath = sourceSets.getByName("main").compileClasspath
mapOf(
    "runReincarnationDurabilityRegressionTest" to "com.miaokatze.gtit.reincarnation.ReincarnationDurabilityRegressionTest",
    "runReincarnationResumeRegressionTest" to "com.miaokatze.gtit.reincarnation.ReincarnationResumeRegressionTest",
    "runReincarnationFailureBoundaryRegressionTest" to "com.miaokatze.gtit.reincarnation.ReincarnationFailureBoundaryRegressionTest",
    "runReincarnationLifecycleRegressionTest" to "com.miaokatze.gtit.reincarnation.ReincarnationLifecycleRegressionTest",
    "runReincarnationSessionRegressionTest" to "com.miaokatze.gtit.reincarnation.ReincarnationSessionRegressionTest",
    "runMailConservationTest" to "com.miaokatze.gtit.mail.MailConservationTest",
    "runSignInRewardConservationTest" to "com.miaokatze.gtit.signin.SignInRewardConservationTest",
    "runLotteryPersistenceTest" to "com.miaokatze.gtit.lottery.LotteryPersistenceTest",
    "runBqQuestPackTest" to "com.miaokatze.gtit.crossmod.bq.BqQuestPackTest",
    "runBqStampFileTest" to "com.miaokatze.gtit.crossmod.bq.BqStampFileTest",
    "runLotteryCostValidationTest" to "com.miaokatze.gtit.lottery.LotteryCostValidationTest",
    "runRewardSessionLifecycleTest" to "com.miaokatze.gtit.rewards.RewardSessionLifecycleTest",
    "runRewardPersistenceBoundaryTest" to "com.miaokatze.gtit.rewards.RewardPersistenceBoundaryTest",
    "runRewardConfigPersistenceTest" to "com.miaokatze.gtit.rewards.RewardConfigPersistenceTest",
    "runBoundedRequestQueueTest" to "com.miaokatze.gtit.util.BoundedRequestQueueTest",
    "runPacketStringsTest" to "com.miaokatze.gtit.util.PacketStringsTest",
    "runFlightSourceSupportTest" to "com.miaokatze.gtit.common.items.FlightSourceSupportTest",
    "runPocketFluidNativeBoundaryTest" to "com.miaokatze.gtit.common.items.pocket.PocketFluidNativeBoundaryTest",
    "runPocketCursorDropTest" to "com.miaokatze.gtit.gui.pocket.PocketCursorDropTest",
    "runPocketCarrierRelocationTest" to "com.miaokatze.gtit.gui.pocket.PocketCarrierRelocationTest",
    "runPocketHeadlessCarrierTest" to "com.miaokatze.gtit.common.items.pocket.channel.PocketHeadlessCarrierTest",
    "runVendingRequestAdmissionTest" to "com.miaokatze.gtit.gui.vm.VendingRequestAdmissionTest",
    "runHistoryPersistenceTest" to "com.miaokatze.gtit.trade.v2.HistoryPersistenceTest",
    "runGiftSystemTest" to "com.miaokatze.gtit.gift.GiftSystemTest",
    "runGiftEditorSessionTest" to "com.miaokatze.gtit.gift.ui.GiftEditorSessionTest",
    "runGiftAccessTest" to "com.miaokatze.gtit.gift.ui.GiftAccessTest",
    "runTeamStatusTest" to "com.miaokatze.gtit.gui.vm.NekoTeamStatusSyncTest",
    "runRemoteVendingTerminalTest" to "com.miaokatze.gtit.common.items.terminal.RemoteVendingTerminalTest",
    "runVendingConcurrencyTest" to "com.miaokatze.gtit.gui.vm.VendingConcurrencyTest",
    "runLootRegistrarTest" to "com.miaokatze.gtit.common.loot.LootRegistrarTest",
    "runTradePresetStoreTest" to "com.miaokatze.gtit.trade.preset.TradePresetStoreTest",
    "runTradePageSourceTest" to "com.miaokatze.gtit.trade.preset.TradePageSourceTest",
    "runGTITCommandTest" to "com.miaokatze.gtit.command.GTITCommandTest",
    "runStarterGiftAuditNbtTest" to "com.miaokatze.gtit.terminal.StarterGiftAuditNbtTest",
    "runTradePresetAccessTest" to "com.miaokatze.gtit.trade.preset.ui.TradePresetAccessTest"
).forEach { (taskName, entryPoint) ->
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        mainClass = entryPoint
        classpath = storeTestRuntimeClasspath + contentTestCompileClasspath
        if (taskName == "runLotteryPersistenceTest" || taskName == "runLotteryCostValidationTest" || taskName == "runRewardSessionLifecycleTest" || taskName == "runRewardPersistenceBoundaryTest" || taskName == "runRewardConfigPersistenceTest") {
            systemProperty("gtit.rewards.testScratch", "true")
            val scratchDir = layout.buildDirectory.dir("review-tests/$taskName")
            workingDir(scratchDir)
            doFirst { scratchDir.get().asFile.mkdirs() }
        }
        classpath += configurations.detachedConfiguration(dependencies.create("it.unimi.dsi:fastutil:8.5.18"))
        if (taskName == "runRemoteVendingTerminalTest") {
            classpath += configurations.getByName("nativeTerminalScreenTestRuntime")
        }
        if (taskName == "runRemoteVendingTerminalTest" || taskName == "runVendingConcurrencyTest") {
            classpath += configurations.detachedConfiguration(dependencies.create(
                "xyz.wagyourtail.jvmdowngrader:jvmdowngrader-java-api:1.3.5:downgraded-8@jar"))
            classpath += configurations.detachedConfiguration(dependencies.create("org.joml:joml:1.10.8"))
        }
    }
    tasks.named("check") { dependsOn(taskName) }
}

// Explicit target-8 native Forge/AE lane: the original ASM5 transformer requires GT's actual base classes.
// This does not certify the modern server's Java17+ multi-release variant; server acceptance is a separate gate.
listOf("runPocketFluidNativeBoundaryTest", "runVendingConcurrencyTest").forEach { taskName ->
    tasks.named<JavaExec>(taskName) {
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(8))
        })
    }
}
