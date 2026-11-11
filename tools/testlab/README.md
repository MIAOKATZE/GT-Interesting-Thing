# GTIT 测试设施与固定实例

本页描述当前源码和工具合同，不是实机通过记录。GTIT 保留 18 个场景；离线目录只表示声明能力，运行时 `/miaotest gtit list` 及本轮原始报告才是实际执行依据。

## 安装与开启

普通 GTIT JAR 不含完整实验室、测试资源或 JVM 测试种子。生产仅保留小型可选 bootstrap 和通用观察/适配口。缺少配置或 `enabled=false` 时不加载实验室实现，也不自动生成配置。

需要测试时，分别用匹配的 **GTIT `-testlab.jar`** 和 **MiaoKatzeCore 0.2.9 或兼容版本 `-testlab.jar`** 替换普通包。每个 Mod 只装一份，不能把普通包与同 modid 的测试包并装。完整测试变体包含同次编译的全部生产内容、Tags、Mixin 及实验室；`reobfTestlabJar` 生成运行包，`testlab-dev` 用于开发环境。

`config/gtit/testlab.cfg` 的 `testlab` 类别中显式设置 `enabled=true`；物理 UI 测试客户端还需 `clientUiEnabled=true`，重启后生效。显式开启但缺测试实现或 Core 世界策略时清晰拒绝启动，不以缺类异常静默降级。维度/provider 默认 1048580，冲突拒绝，不借用已有维度。

## 固定实例与统一入口

固定路径来自 `E:/CodeGT/plan/testlab-instances.json` 的 GTIT 项：

- 服务端：`E:/CodeGT/测试实例-GTIT/服务端`。
- 客户端游戏目录：`E:/CodeGT/测试实例-GTIT/客户端/.minecraft`。
- Java 与堆预算读取登记值；当前登记 Java 26，客户端 4096 MiB，客户端不得超过 5120 MiB。

长期增量复用这一套实例，只替换本次授权的候选包、配置和 fixture，不按轮复制整包，不重置既有 world，也不修改日常 Prism 实例。服务端只绑定 localhost；整合包、Java、候选 SHA 和配置状态以当前 manifest、bundle 与 doctor 为准，不能沿用旧 RC2 路径说明。

统一宿主为 `E:/CodeGT/tools/testlab/host.py`。例如：

```powershell
python E:/CodeGT/tools/testlab/host.py doctor --module gtit --case harness-smoke --prepare
python E:/CodeGT/tools/testlab/host.py catalog --module gtit
python E:/CodeGT/tools/testlab/host.py run --module gtit --case harness-smoke --prepare --execute
python E:/CodeGT/tools/testlab/host.py run --module gtit --suite --prepare --execute
python E:/CodeGT/tools/testlab/host.py status --module gtit
python E:/CodeGT/tools/testlab/host.py report --module gtit --run-id <宿主UUID>
```

doctor 和不带 `--execute` 的 run 只生成计划、列阻断项。`--prepare` 的临时 opt-in 受实例 lease 约束，退出后恢复原配置字节。命令送达、进程启动、窗口存在都不是业务完成。

## 命令、目录与取消

统一命令及兼容命令均要求 **OP2/console**：

```text
/miaotest gtit list
/miaotest gtit status
/miaotest gtit run all --request <UUID>
/miaotest gtit run native-pocket-runtime --request <UUID>
/miaotest gtit stop --request <UUID>
/miaotest gtit reset --request <UUID>
/miaotest gtit report --request <UUID>
```

`run all` 明确选择默认 suite，不代表所有显式场景。`/gtitlab` 保留 help、status、catalog、run、suite、stress、profile、stop/cancel、reset、refresh 和 UI 子命令。stop/cancel 取消当前 task、清待执行队列及待用真人 UI 请求，再按 owned journal 清理；取消结果为 CANCELLED，不能因清理完成变为 PASS。旧 reset 在运行中仍记 FAIL/interrupted；refresh 清理后再开 suite。宿主 stop 另负责自有进程正常退出，不能与 Mod 的 task stop 混为一个成功条件。

| 选择 | 场景 |
| --- | --- |
| 默认 suite（8） | harness-smoke、native-pocket、native-pocket-runtime、native-machine-trade、native-team-wallet、native-business-events、native-gift-system、native-trade-presets |
| stress（2） | harness-stress、native-bounded-stress |
| 显式服务端 | native-remote-vending-team、native-preset-catalog-check、native-persistence-write/read |
| 显式真人阶段 | native-gift-host-stage/resume |
| 显式客户端 | client-ui-pocket、client-ui-vending |

目录带 lane、tags、defaultSuite、requiresRealActor；离线 cases.json 标 `evidence=declared`。profile 是重复计时，不是 CPU profiler。持久化 write/read 必须跨真实 JVM；GiftHost 必须同世界、同 profile 的真实集成服房主，阶段凭据、17 份标记奖励和 receipt 有意保留，普通 reset 不抹除。

## 生存语义与世界隔离

保持真实生存付款、资源、权限及奖励逻辑，不切 Peaceful，不改共享 gamerules、用户库存或用户 game mode。GTIT ownsWorld 同时核 enabled、专属 provider、维度 ID 和 DimensionManager 当前实际 world 对象；Core 测试变体只在此精确世界隔离自然刷怪/干扰实体，GTIT provider 禁雨雪雷电并固定天光，不写共享 WorldInfo。

两个 void 画布中心 X=0/1048576，各 ±256；Z=±256、Y=1..255。fixture 有界、先记 cleanup、再发布资源。普通演员是断线 FakePlayer；remote 并发探针仅在空服前置下临时加入精确 owned 的原生 session，不能概括成所有 FakePlayer 永不进在线表。只清 owned 方块、标记物和演员，不扫描抹除用户区域或阶段奖励。

## 后台 GUI 与证据

真实 GUI 必须运行在 **Windows private desktop**，不切换用户桌面，不抢鼠标/键盘，不发送 OS 输入。创建隔离桌面失败即 BLOCKED，禁止退回前台。统一宿主当前 GTIT 自动客户端 profile 为 `client-ui-pocket`，运行于独立测试 actor 的 run-owned integrated world；vending 的多样品手工 fixture 保留，不自动宣称已覆盖。

口袋 UI 要求真实在线 OP2、非 FakePlayer、背包与光标全空、关闭容器且不潜行；实际玩家留在原世界。客户端通过正常 playerController 使用物品。成功 ACK 仅在目标 `gtit.pocket.main` 面板实际 RenderTick.END 后、LOWEST 优先级重新核屏时发送，让宿主默认优先级先取截图。该 ACK 仍不等于像素、焦点、输入或玩法验收。vending 不强制 creative/survival，不改原模式，记录实际 creative 标记；真人 host 和奖励场景另按各自前置验收。

Core 每次命令写 `logs/miao-testlab/<requestUUID>.json`，含 accepted、snapshot、events 和实际 artifact 身份。GTIT 原始业务报告为 `logs/gtit-testlab/<runId>.json`；同时保留本轮 console、原始 JSON、截图和宿主回执。业务成功至少核对本轮 request/run、实际 Jar SHA/来源、所选场景实质检查、最终 outcome、reportWritten=true、cleanupPending=false；全 SKIP、CANCELLED、超时或报告失败均不可当 PASS。

宿主证据位于 `E:/CodeGT/.artifacts/testlab-system-optimization/host/<宿主UUID>/`。旧 runner 的 `.testlab/runs/` 及 last-run.json 仅作兼容证据，不能用旧报告或 stdin exit0 代替本轮结果。一实例一个 lease，核 PID 与创建身份；不删活锁、不杀全部 Java。失败保留世界、恢复凭据及原始报告，停止自有 child 后恢复候选包和配置。

当前候选已完成默认八场景服务端套件及真实 Pocket GUI、自然帧截图、集成服正常退出与临时世界回收验证。编译、JVM 合同和 Jar 边界核验不替代其他场景的实机证据；贸易机多样品交互、压力及房主跨 JVM 流程不在上述通过范围。工件身份与逐项结果见工作区实施报告和 `.artifacts/testlab-system-optimization/`，本页只维护使用合同。
