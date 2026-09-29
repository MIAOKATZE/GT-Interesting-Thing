<p align="center">
  <img alt="GTIT" src="README/GTIT.png">
</p>

<h1 align="center">GT-Interesting-Thing</h1>
<p align="center"><strong><em>GTNH 趣味道具模组</em></strong><br><strong><em>GTNH Interesting Gadgets Mod</em></strong></p>

<p align="center">
  <a href="LICENSE"><img alt="License AGPL-3.0" src="https://img.shields.io/badge/License-AGPL--3.0-blue.svg"></a>
  <img alt="Minecraft 1.7.10" src="https://img.shields.io/badge/Minecraft-1.7.10-blue.svg">
  <img alt="Forge 10.13.4.1614" src="https://img.shields.io/badge/Forge-10.13.4.1614-blue.svg">
  <a href="https://github.com/GTNewHorizons/GT-New-Horizons-Modpack"><img alt="GTNH 2.9.0 beta-3&RC-1" src="https://img.shields.io/badge/GTNH-2.9.0%20beta--3%26RC--1-orange.svg"></a>
  <a href="https://github.com/MIAOKATZE/GT-Interesting-Thing/releases"><img alt="Release 1.8.39" src="https://img.shields.io/badge/Release-1.8.39-green.svg"></a>
</p>

A GregTech New Horizons gadget mod that **provides interesting items enhancing the gameplay experience**, including flight cores, ore scanning tools, functional rings, a starter gift system, a hardcore reincarnation cycle, and a custom trading machine, while balancing usage costs to maintain progression integrity.

一个 GregTech New Horizons 趣味道具模组，**提供增强游玩体验的有趣物品**，包括浮空核心、探矿工具、功能性戒指、新手宝箱系统、周目轮回系统，以及自定义交易机器，同时平衡使用代价以保持进阶完整性。

> \[!NOTE]
> This is an unofficial mod. Please avoid discussing this mod in official GTNH forums.
> 这是一个非官方模组，讨论此模组时请注意场合。

## Downloads & Requirements / 下载与版本需求

| GTNH         | GTIT   | Maintenance / 维护 |
| ------------ | ------ | :--------------: |
| 2.9.0 beta-1&2&3&RC1 | **1.8.0 +**（当前 / current） |        ✔️        |
| 2.9.0 beta-1&2 | 1.0.0~1.7.53| ✔️ |
| 2.8.4        | 0.1.x  |        ❌️        |

The current version comes from `gradle.properties` (`RELEASE_VERSION`); development history is recorded under `plan/workflow/`. No external release status is claimed here.

当前版本取自 `gradle.properties` 的 `RELEASE_VERSION`；开发历史见 `plan/workflow/`。此处不声明对外发布状态。

***

## Neko Vending Machine / 猫猫售货机

<p align="center"><img src="README/neko%20vending%20machine.png" width="250" alt="猫猫售货机 / Neko Vending Machine"></p>
<p align="center"><img src="README/neko%20vending%20machine-1.png" width="250" alt="猫猫售货机界面 / Neko Vending Machine GUI"><img src="README/neko%20vending%20machine-2.png" width="250" alt="猫猫售货机界面 / Neko Vending Machine GUI"><br><em>猫猫售货机界面 / Neko Vending Machine GUI</em></p>

A custom trading machine — since V2 an independently implemented multiblock built on GT5U's `MTEEnhancedMultiBlockBase` (the early prototype originated from the VendingMachine mod) — featuring an independent currency system (Neko Coin), dynamic tabbed GUI, BetterQuesting integration, and BGM. Designed as a progression-gated reward shop where players earn Neko Coins through quests and spend them on loot bags and items.

自定义交易机器——V2 起为基于 GT5U `MTEEnhancedMultiBlockBase` 独立实现的多方块机器（开发早期原型源自 VendingMachine 模组），拥有独立的货币系统（猫猫币）、动态标签页 GUI、BetterQuesting 任务集成和背景音乐。设计为进度门控奖励商店——玩家通过任务获取猫猫币，再消费猫猫币购买战利品袋和物品。

### Currency System / 货币系统

Two types of Neko Coins, stored in an independent wallet system (not in player inventory):

两种猫猫币，存储在独立钱包系统中（不占用玩家背包）：

| Currency / 货币                | ID               | Description / 说明                                   |
| ---------------------------- | ---------------- | -------------------------------------------------- |
| Neko Coin / 猫猫币              | `neko`           | Basic currency, earned through BQ quest completion |
| Shimmering Neko Coin / 闪烁猫猫币 | `shimmeringNeko` | Premium currency, earned through harder quests     |

- **Team wallet / 团队钱包**: Wallets are shared among team members via GTNHLib Teams API. All members of the same team share a single wallet balance. (Since v1.5.1; v1.5.0 used per-player personal wallets stored in `<world>/gtit_neko_wallets/<uuid>.dat`)
- Neko Coins are automatically detected when placed in the trade edit inventory
- Coins are deducted natively from the wallet by V2's own trade executor (`NekoWallet.tryDeduct`, atomic), no Mixin involved
- **Cooldown scaling / 冷却缩放**: Trade cooldown limits scale with the number of online team members
- **团队钱包**：钱包通过 GTNHLib Teams API 在团队成员间共享，同团队的所有成员共享同一个钱包余额。（自 v1.5.1 起；v1.5.0 使用按玩家个人钱包，存储在 `<world>/gtit_neko_wallets/<uuid>.dat`）
- 在交易编辑界面放入猫猫币时自动识别为货币参数
- 猫猫币由 V2 自身交易执行器从钱包原生扣减（`NekoWallet.tryDeduct`，原子操作），不经由 Mixin
- **冷却缩放**：交易冷却上限随在线团队成员数缩放

### Trade System / 交易系统

Three trade types supported:

支持三种交易类型：

| Type / 类型             | Example / 示例                               |
| --------------------- | ------------------------------------------ |
| Pure Neko Coin / 纯猫猫币 | 100 Neko Coins → 1 Loot Bag                |
| Mixed / 混合交易          | 50 Neko Coins + 10 Iron Ingots → 1 Diamond |
| Item Exchange / 物品交换  | 10 Iron Ingots → 1 Diamond (no coins)      |

**Trade entry fields / 交易条目字段**:

- `tabId`: Which tab the trade appears in
- `orderId`: Sort order within the tab
- `currency`: Neko Coin cost (optional, auto-detected from inventory)
- `fromItems`: Required items (optional for pure coin trades)
- `toItems`: Reward items
- `cooldown`: Cooldown in seconds (0 = no cooldown)
- `bqQuestId`: BQ quest requirement (empty = no requirement)

### GUI & Tabs / 界面与标签页

<p align="center"><img src="README/trade.png" width="300" alt="交易界面 / Trade GUI"><br><em>交易界面 / Trade GUI</em></p>

- **Dynamic tabs / 动态标签页**: 3 default tabs + unlimited custom tabs (add via command with held item as icon)
- **Neko Coin display / 猫猫币显示**: Real-time balance display with expandable details
- **Coin intercept slot / 猫猫币拦截槽**: Automatically routes Neko Coins from inventory to wallet
- **Sort & Search / 排序与搜索**: Sort by order ID or name, with text search filtering
- **BGM / 背景音乐**: 3 random BGM variants with 2-second fade in/out, max 50% volume
- **BQ lock display / BQ 锁定显示**: Locked trades show golden "LOCKED" text with cooldown overlay; cooling-down trades show cyan cooldown text
- **动态标签页**：3 个默认标签页 + 无限自定义标签页（通过指令添加，手持物品作图标）
- **猫猫币显示**：实时余额显示，可展开详情
- **猫猫币拦截槽**：自动将背包中的猫猫币导入钱包
- **排序与搜索**：按顺序 ID 或名称排序，支持文字搜索过滤
- **背景音乐**：3 首随机 BGM，2 秒淡入淡出，最大音量 50%
- **BQ 锁定显示**：锁定交易显示金色 "LOCKED" 文字 + 冷却遮罩；冷却中交易显示青色冷却时间

### BetterQuesting Integration / BQ 任务集成

Trades can be locked behind BQ quest completion:

交易可绑定 BQ 任务作为前置条件：

- **Server-side lock / 服务端锁定**: `NekoBqCondition` (a V2 trade condition) checks BQ quest completion before the trade executes
- **Client-side display / 客户端显示**: `NekoTradeDisplayWidgetV2` shows golden "LOCKED" overlay for locked trades
- **Event sync / 事件同步**: `NekoBqBridge` listens for quest completion events and maintains a completion cache
- **Sort optimization / 排序优化**: Locked trades are sorted after available trades (cooldown does not affect sort order)
- **bqQuestId formats / bqQuestId 格式**: Supports base64 (recommended, from BQ quest filename), `high:low`, and standard UUID
- **服务端锁定**：`NekoBqCondition`（V2 交易条件）在交易执行前检查 BQ 任务完成状态
- **客户端显示**：`NekoTradeDisplayWidgetV2` 为锁定交易显示金色 "LOCKED" 遮罩
- **事件同步**：`NekoBqBridge` 监听任务完成事件并维护完成状态缓存
- **排序优化**：锁定交易排在可交易之后（冷却不影响排序）
- **bqQuestId 格式**：支持 base64（推荐，从 BQ 任务文件名复制）、`high:low`、标准 UUID

### Visual Editor / 可视化编辑

Enable with `/gtit nekovm edit on` (see [Administrator Commands](#administrator-commands--管理员命令)), then click trade entries in the machine GUI to edit them visually. The editing surface covers seven domains: trades, tab pages, lottery entries, lottery pools, sign-in rewards, online-time tiers, and blessings.

通过 `/gtit nekovm edit on` 开启（见[管理员命令](#administrator-commands--管理员命令)），在售货机 GUI 内点击交易条目即可进行可视化编辑。编辑面现覆盖七个域：交易条目、标签页、抽奖条目、抽奖卡池、签到奖励、在线时长档位与祝福。

<p align="center"><img src="README/edit1.png" width="250" alt="可视化编辑界面 / Visual Editor GUI"><img src="README/edit2.png" width="250" alt="可视化编辑界面 / Visual Editor GUI"></p>
<p align="center"><img src="README/edit3.png" width="250" alt="可视化编辑界面 / Visual Editor GUI"><img src="README/edit4.png" width="250" alt="可视化编辑界面 / Visual Editor GUI"></p>


### Configuration / 配置

| File / 文件              | Path / 路径                                | Description / 说明                                                            |
| --------------------- | --------------------------------------- | -------------------------------------------------------------------------- |
| Trades / 交易配置         | `config/gtit/trade/trades/tab_<id>.json` | Trade entries with tabId, orderId, items (optionally with `nbtBase64`), currency, cooldown, bqQuestId |
| Tabs / 标签页配置          | `config/gtit/trade/pages.json`          | Custom tab definitions (ID, name, icon)                                     |
| Trade ledger / 贸易整合记账 | `config/gtit/trade/integrated/`         | Version ledger for integrated trade groups (delete a file to force re-registration) |
| Lottery pools / 抽奖卡池   | `config/gtit/lottery/lottery.json`      | Gacha pool definitions (pools with entries, cost items, pity config)        |
| Lottery ledger / 抽奖整合记账 | `config/gtit/lottery/integrated/`       | Version ledger for integrated lottery pool groups                           |
| Wallets / 钱包          | GTNHLib Teams team data                 | Team-shared Neko Coin balances (v1.5.0: per-player `<world>/gtit_neko_wallets/<uuid>.dat`) |
| Gift / 新手宝箱           | `config/gtit/gift_config.json`          | Starter gift guaranteed + random items (optionally with `nbtBase64`)        |

If no trade config exists, default trades are generated from built-in defaults on first launch; with `enhancedDefaultTrades` enabled, the bundled base trade group from jar assets takes over instead.

如果交易配置不存在，首次启动时将生成默认交易；开启增强默认交易（`enhancedDefaultTrades`）时，由 jar 内置基础贸易组替代内置默认交易。

### Third-party Integration API / 第三方整合 API

Register external trade groups and lottery pool groups from your own mod via three interchangeable channels, all funnelling into the same idempotent, version-ledgered pipeline:

- **Java direct call / Java 直调**: `NekoTradeIntegrationAPI.registerTradeGroup(NekoTradeGroupDef)` / `LotteryIntegrationAPI.registerLotteryPool(LotteryPoolGroupDef)` — thread-safe, queued until the server is ready.
- **IMC**: `FMLInterModComms.sendMessage("gtit", "gtit:registerTradeGroup" | "gtit:registerTradeAsset" | "gtit:registerLotteryPool", nbt)` with the definition JSON in the NBT string field (`groupJson` / `tradeAssetJson`).
- **jar assets / jar 资产清单（BQ 式）**: ship `assets/<your-modid>/gtit/trade/index.json` + `groups/*.json` and `assets/<your-modid>/gtit/lottery/index.json` + `pools/*.json` inside your jar (an explicit manifest — jar directories are not enumerable on 1.7.10), then call `NekoTradeIntegrationAPI.registerTradeAssetsFromJar("<your-modid>")` / `LotteryIntegrationAPI.registerLotteryPoolsFromJar("<your-modid>")` in `postInit`.

Minimal example / 最小示例：

```java
// postInit（物品注册完成后）
NekoTradeIntegrationAPI.registerTradeAssetsFromJar("mymod");
LotteryIntegrationAPI.registerLotteryPoolsFromJar("mymod");

// 或程序化直调（def JSON 模型见 NekoTradeGroupDef / LotteryPoolGroupDef）
NekoTradeIntegrationAPI.registerTradeGroup(def);
LotteryIntegrationAPI.registerLotteryPool(poolGroupDef);
```

Idempotence & version ledger / 幂等与版本记账：each group is recorded in `config/gtit/trade/integrated/<groupId>.json` or `config/gtit/lottery/integrated/<groupId>.json` with its source version. Same version → skipped (player edits to `tab_*.json` / `lottery.json` stay authoritative); version bump → old content is removed per the ledger and re-registered; deleting the ledger file forces re-registration. Pools/trades owned by the player's local config are never silently overwritten (WARN + skip on conflict).

每个组在 `config/gtit/{trade,lottery}/integrated/<groupId>.json` 记账：版本未变跳过（玩家对 `tab_*.json` / `lottery.json` 的编辑保持权威）；版本变化按记账移除旧内容后重注册；删除记账文件即强制重注册；玩家本地配置占用的池/交易不会被静默覆盖（冲突 WARN 跳过）。

Full schema, ledger semantics walkthrough and the content-author workflow: local working notes `plan/wiki/integration-assets-api.md` (kept out of git per repo convention); durable entries in the MIAO GTNH wiki: `mods/gtit/integration/assets-api.md` and `mods/gtit/lottery/lottery-draw-algorithm.md`.

完整 schema、版本记账语义图解与内容作者工作流见本地工作树文档 `plan/wiki/integration-assets-api.md`（按仓库惯例不入库）；持久版本见 MIAO GTNH wiki 条目 `mods/gtit/integration/assets-api.md` 与 `mods/gtit/lottery/lottery-draw-algorithm.md`。



### Mixin Architecture / Mixin 架构

Since V2, trade logic (currency deduction, BQ locks, cooldowns) is handled natively by the mod's own machine and trade-executor classes instead of Mixins. The remaining Mixins cover rings, neko BGM, fishing coins, reincarnation ascension, and compatibility:

自 V2 起交易逻辑（货币扣减、BQ 锁定、冷却）由模组自身的机器与交易执行类原生处理，不再经由 Mixin。现存 Mixin 覆盖戒指、猫猫 BGM、钓鱼猫猫币、周目升天与兼容性：

| Mixin                              | Target                                     | Function / 功能                                             |
| ---------------------------------- | ------------------------------------------ | ----------------------------------------------------------- |
| `MixinPlayerControllerMP`           | `PlayerControllerMP.getBlockReachDistance` | Client-side block reach extension for Ring of Distant Grasp |
| `MixinItemInWorldManager`           | `ItemInWorldManager.getBlockReachDistance` | Server-side block reach extension for Ring of Distant Grasp |
| `NekoSoundManagerMixin`             | `SoundManager.playSound`                   | Capture neko BGM sound source for per-frame volume control  |
| `MixinAEBaseGuiDrawHoveringTextFix` | `GuiScreen.drawHoveringText`               | Fix AE2 tooltip `AbstractMethodError` under Angelica         |
| `MixinEntityFishHook`               | `EntityFishHook` fish-catching roll        | Server-side bonus Neko Coin / Shimmering Neko Coin on each successful player catch (default 10% / 2%, config-tunable; machine fish farms get nothing — anti-farm) / 玩家每次成功钓鱼服务端掷概率附赠猫猫币/闪烁猫猫币（默认 10%/2%，Config 可调；机器渔场不产，防刷币） |
| `MixinEntityRenderer`               | `EntityRenderer.setupCameraTransform`      | Reincarnation ascension camera warp (read-only replica of vanilla portal dizziness, hard-gated to the 120t cutscene window) / 周目升天相机 warp（只读复刻原版传送门眩晕，120t 演出窗口硬门控） |

Sound-mute Mixins are listed in the [Machine Sound Mute](#machine-sound-mute--机器音效静音) section.

机器音效静音相关 Mixin 见[机器音效静音](#machine-sound-mute--机器音效静音)章节。

***

## Daily Sign-In / 每日签到

<p align="center"><img src="README/signin.png" width="384" alt="签到界面 / Sign-In GUI"><br><em>签到界面 / Sign-In GUI</em></p>

A daily reward system that grants players cumulative rewards for logging in each day, with optional online-time tier bonuses.

每日签到系统，玩家每日登录可领取累积奖励，并支持按在线时长解锁额外档位奖励。

***

## Lottery / 抽奖

<p align="center"><img src="README/lottery.png" width="384" alt="抽奖界面 / Lottery GUI"><br><em>抽奖界面 / Lottery GUI</em></p>

A gacha-style reward pool where players spend Neko Coins or items to draw random rewards.

消耗猫猫币或物品进行随机抽奖的奖励池系统。

***

## Mail / 邮件

<p align="center"><img src="README/mail.png" width="384" alt="邮件界面 / Mail GUI"><br><em>邮件界面 / Mail GUI</em></p>

An in-game mail system for receiving rewards, announcements, and attachments from server operators or automated events.

游戏内邮件系统，用于接收管理员或自动事件发放的奖励、公告与附件。

***

## Items / 物品

### Float Core / 浮空核心

<p align="center"><img src="README/Float%20Core.png" width="128" alt="浮空核心 / Float Core"><br><em>浮空核心 / Float Core</em></p>

A simple yet powerful flight enabler. Equip to any Baubles slot to gain creative-like flight ability.

简单而强大的飞行道具。装备到任意 Baubles 饰品栏即可获得类似创造模式的飞行能力。

- Equip to any Baubles slot to gain flight
- Consumes hunger per tick while flying
- Automatically disables flight when hunger drops below 3 shanks (6 hunger points)
- Early-game accessible — no electricity required
- 装备到任意 Baubles 饰品栏即获得飞行能力
- 飞行时每 tick 消耗饥饿值
- 饥饿值低于3格（6点）时自动禁用飞行
- 前期即可获取——无需电力

***

### Electric Float Core / 电力浮空核心

<p align="center"><img src="README/Electric%20Float%20Core.png" width="128" alt="电力浮空核心 / Electric Float Core"><br><em>电力浮空核心 / Electric Float Core</em></p>

An upgraded version of the Float Core with massive EU storage. When electricity runs out, it falls back to hunger consumption at half the rate.

浮空核心的升级版，拥有超大 EU 容量。电力耗尽时回退至饥饿消耗，消耗量为浮空核心的一半。

- LV voltage tier, 32,000,000 EU capacity
- Consumes electricity while flying
- Falls back to hunger consumption when depleted (half rate of Float Core)
- Compatible with IC2 charging stations
- LV 电压等级，32,000,000 EU 容量
- 飞行时消耗电力
- 电力耗尽时消耗饥饿值（浮空核心的一半消耗率）
- 兼容 IC2 充电站

***

### Telekinesis Ore Scanner Core / 念力共振探矿核心

<p align="center"><img src="README/Telekinesis%20Ore%20Scanner%20Core.png" width="128" alt="念力共振探矿核心 / Telekinesis Ore Scanner Core"><br><em>念力共振探矿核心 / Telekinesis Ore Scanner Core</em></p>

A long-range ore and fluid prospecting tool that integrates with JourneyMap via VisualProspecting. Scan results are uploaded to the map automatically — you cannot view ore info directly, preserving the exploration challenge.

远程矿石和流体勘探工具，通过 VisualProspecting 与旅行地图集成。扫描结果自动上传至地图——无法直接获取矿石信息，保留探索挑战性。

- Right-click air or block to scan a 19×19 chunk area
- Consumes 6 hunger points per scan
- Data automatically uploaded to JourneyMap (requires VisualProspecting)
- Cannot view ore info directly — only map markers
- Shift + Right-click to switch between Ore / Fluid detection mode
- Requires VisualProspecting mod for map integration
- 右击空气或方块进行 19×19 区块大范围探矿
- 每次消耗 6 点饥饿值进行探矿
- 数据自动上传至旅行地图（需要 VisualProspecting）
- 无法直接获取矿石信息——仅显示地图标记
- Shift + 右键切换矿石/流体探测模式
- 需要 VisualProspecting 模组实现地图联动

***

### Infinity Cell / 无限存储元件

<p align="center"><img src="README/Neko%20Infinity%20Unit.png" width="128" alt="猫猫无限存储单元 / Neko Infinity Storage Unit"><br><em>猫猫无限存储单元 / Neko Infinity Storage Unit（动画材质首帧）</em></p>

<p align="center"><img src="README/Infinity%20Cell.png" width="128" alt="[OLD] ME 无限存储元件 / [OLD] ME Infinity Cell"><br><em>[OLD] 移植版两枚 / the two AE2Things-derived legacy cells (still usable, deprecated)</em></p>

GTIT 自有的**多通道**无限容量存储元件：同一枚元件在同一个驱动器槽位里同时向物品、流体以及整合包已注册的第三方通道（源质等）贡献无限容量，各通道内容独立分桶存放。数据外部化存储在全局 WorldSavedData 中，避免 NBT 膨胀。从 AE2Things 移植来的物品版与流体版已降级为 `[OLD]`，仍可正常使用。

An AE2 storage cell of GTIT's own design: **one cell serves every registered channel** — items, fluids and, when the pack registers one, essentia — all from a single drive slot, each kept in its own bucket. Data is externalized to a global WorldSavedData, avoiding NBT bloat. The AE2Things-derived item and fluid cells are now `[OLD]` but still work.

- **Channels / 通道**: 物品 + 流体 + 运行时已注册的第三方通道（源质等）/ items, fluids and any registered third-party type (e.g. essentia)
- **Capacity / 容量**: 每通道 Integer.MAX\_VALUE 种类型、每类型 1 字节，实际无限
- **Idle drain / 空闲功耗**: 1 AE/tick（多通道只计一次）
- **Upgrade slots / 升级卡槽**: 2（物品通道支持分区、模糊、分类卡）
- **Migration / 迁移**: 两枚 `[OLD]` 旧元件（物品版 + 流体版）可在工作台**无序合成**为 1 枚新单元（不比较 NBT），迁移前请先用 ME-IO 端口转空存量；旧元件每 10 分钟向归属玩家播报所在容器坐标以提示迁移

### Neko Dimensional Pocket / 猫猫次元口袋

<p align="center"><img src="README/Neko%20Dimensional%20Pocket%20UI.png" width="560" alt="猫猫次元口袋面板 / Neko Dimensional Pocket GUI"><br><em>猫猫次元口袋面板：左流体 · 中随身库存 · 右蒸馏与源质 / the three-column panel</em></p>

一枚**束口袋**形态的随身容器（袋面带猫脸）：右手持物右键打开面板，把「随身库存 + 元素蒸馏 + 跨维度灌仓」收在一件物品里。内容全部存在物品 NBT 上，不占世界数据。

A pouch-shaped portable container with a cat face on the drawstring: right-click while held to open a panel that combines a portable inventory, Thaumcraft distillation and cross-dimensional ME filing — all stored in the item's own NBT, no world data.

- **Storage / 随身库存**: 中栏 9×15 = 135 格真实存储（★R80① 由 10 列收到 9 列，行数一行未删）/ 135 real slots
- **Fluid side / 左栏**: **3 组 × 6 列 = 18 个流体槽**（R78），每组的纵向 = 输入行 + **拉长的流体槽（18×36）** + 输出行，组间空一行；**每槽自带 20,000,000 mB**（★18 槽合计 **360,000,000**；★R96 S3 由 R75 的 16,000,000 / 288,000,000 抬档）；**灌/排的实际入口是每组上下两行的交互格**（放入容器即处理，方向看容器当前有无流体）。★**不说"与 GT5U 流体仓一致"了**——按库实现核对过，两者有语义差：MUI2 的真实（非 phantom）流体槽只在 `isPhantom()` **或鼠标光标上真拎着一件物品**时才发 `SYNC_CLICK`（`FluidSlot.onMousePressed`），**空手点槽不响应**；`onMouseScroll` 只在 phantom 支发 `SYNC_SCROLL` ⇒ **本仓流体槽的滚轮原样是空档**（不是被谁吃掉）。★R83 在它上面另加自家手势（不是 GT5U 口径）、R91-⑤ 重排：`中键` = 用本格现有流体请求绑定（声明一条会补货的需求）、`alt+左键` = 记忆 L、`alt+右键` = 阻拦上传 P、`alt+滚轮` = 调该条声明的组上限（流体一档 = 单 tank 的 1% = 200,000 mB，`alt+ctrl` 时步进 ×10）。★同一条三手势族在中栏／流体列／源质盘三处逐字同形（见「Cell attributes & gestures」条）。★**同列两行不再同权（R83 D-2 覆盖 R39a 的落位部分 → ★R93-② 连输入侧一起收）**：**只有上一行（进格）能放容器**——灌与排都从它发起；**下一行（出格）只收处理完的容器**，玩家以点击 / 拖拽 / 快捷移动往它放东西被挡回（★库层按 1–9 与快捷栏交换那一路只校验快捷栏一侧，属实机探针项，见检查表 16.2.8）（装配侧 `accessibility(false,true)+canDragInto(false)` 挡手感 + 服务端 `moveFluidBetweenTanks` 入口同一道 `isLowerInteractionRow` 守真值，两侧共用一个单源谓词）。★出格**仍可取出**（否则产物被永久关死）；★**18 个流体槽本体不受影响**，手持储罐直接点槽仍是 GT5U 那套按键语义。★随本条作废：旧"下行原地抽干、余量留在下行"那两条验收面（检查表 2.5 / 9.6），`restCellOf` 第一支与 `canPlacePair` 的 `replaceable` 同支因此成为玩家路径不可达——★按 R91-i 通则不删，改由用例 `fluid_output_row_read_only` 钉"处理永不从出格发起"这条正向判据；`PocketSlots` 那道输出闩也自此结构上不可命中（★不删，理由与台账记由见 `decision-ledger.md` R93 节）
- **Distillation / 右栏**: 下 6×2 = 12 格输入 → 按 TC4 源质原量蒸馏（5 秒一轮，无物品或无源质即无进度，判据同炼金炉 `canSmelt`）。★**粒度是"每种物品一组"而不是"每格一份"（R83 D-3 取 β）**：同一物品摊在几格只算一组，每轮该组蒸 1 次、耗 1 件（消耗与产出都不乘堆叠数）⇒ 最坏情形（12 格同一物）吞吐 12 件/轮 → **1 件/轮**，这是裁定的代价不是回归 bug；某组一件就带超过单 tag 上限（64 点）时该组**永远放不下**，现在**明确放弃并给出"放弃 N 组 / M 点"的读数**（物品留在格里不销毁、别的组照常蒸），不再出现"放了东西、进度条满着、什么都不发生"，上 6×**12** = **72 格**显示盘（R78：格数 ≥ 实测 aspect 注册数 69，超出者只存不显）。盘与蒸馏盘之间留**一个空行**（零槽行，进度条住在这里）。**格序 = 该源质首次入账的顺序**（先入账的落前面的格；映射由服务端算、随源质 blob 同步、并落口袋 NBT `essCellOrder`），**撤空不回收格位**（R78③ 的用户裁定；★R87-f 另开一条例外：**被 ghost 声明占住的格清零后保留格位**，那是遮罩锚点不漂的前提）。**空格子本身仍绘制**，只是无货时不画图标与数量；★**单 tag 上限 = `ESSENCE_CAP_PER_TAG` = 256 点**（★**R92-② 改口**：ghost 声明的**组上限**上界 `FILTER_CAP_CEILING_ESSENCE` 现在按符号等于本常量 = **256**，即"一格存得下多少、一条声明就能一次要满多少"；它与"玩家一次掏瓶的硬上界" `ESSENCE_OUT_MAX_POINTS_PER_ACTION` = 64 **仍是两条常量**，同值不同名——超上限判据 `still.over_cap` 走的始终是 256）；**取出不再是"晶化"**（★R88，见下面「载体改判」条）；**第三方源质容器（罐）可直接放入/取出**（按 `IEssentiaContainerItem` 接口探测，任何 mod 的容器自动兼容）
- **Binding / 绑定**: 一枚口袋可绑至多 64 枚 GTIT 无限元件（元件只提供 ID）。**左键绑定按钮 = 绑定、右键 = 解绑最后一条、Shift 右键 = 清空全部**；绑定清单（维度 / 坐标 / 状态位）走悬浮 tooltip，超过 10 条给出"另有 N 条未列出"的显式截断提示。**★R81：从未进过驱动器的新元件也能直接绑**——绑定格里身份缺失时由服务端调现成公开 API `StorageManager#getStorage` 就地物化 `diskuuid` 后重读（★客户端一个字节都不写，两道闸各有回归断言），回执拆成**四态各一条**（新增成功 / 格内非元件 / 身份未能写入 / 同身份重复，修复前后两种共用一条误导文案、后一种完全静默，这就是"只能绑一个"的来源）。右段另有 **2 条常驻绑定行**（不悬停也能读到绑了谁）：★**R93-③ 起第 1 行给「整条位置行」**——元件短码 + 维度 / 坐标 / 槽位（用户："元件维度放到右边去，右边本身就有维度了"，那一族原先常驻在底部带左段），第 2 行在条目多于行位时报 `bind.rows_more`「另有 N 条，悬停绑定按钮可见全部」；★"另有 N 条旧绑定不在服务口径"（`bind.inert`）本轮起改由**绑定按钮 tooltip** 承载（86×18 那一行装不下这句 30 余字的话，且它读的是"要不要去解绑"这种动作前决策）。★同一条事实只住一个面，两个面不双写（用例反控）
- **Coins / 币值区**: 照猫猫售货机的形态显示（**只剩「币物品图标 + 数量」两件**；原先叠在条内的两枚自造快捷小图标「弹出」/「ME 导入」已撤，撤前实测是 **4 枚** = 两种币各 2 枚，其中一枚本来就只弹 tooltip 不发消息）。★**R84 起是「每种币各占一行、一行三段」**：`49(币值条) + 2(缝) + 61(它自己的启动按钮) = 112 = 段宽`——两件都靠 `POCKET_C2_coinbar` / `POCKET_C2_btn` 的 **9-slice N=4** 才收得住（装配期把"两张仍是 9-slice""收窄后仍留得下边距""只收不放"钉成断言），代价是按钮标签换短文案「启动」、★完整成本账（`-3 闪烁猫猫币 / 30 秒`那一句）**一字不减地留在 tooltip**。⇒ 左段纵向 `4 行 × 18 = 72` 与带高逐像素闭合，视觉顺序 猫猫币+瞬时按钮 → 闪烁币+短效按钮 →（下段见 Layout 条）。★撤图标**不丢任何功能**：图标与按钮走同一条动作码，成本/冷却明细改由按钮与币值条的 tooltip 承载（那四条 lang 键 `channel.instant/timed`、`balance.neko/shimmering` 实测仍有调用方）
- **Player inventory / 玩家背包（R78①）**: 底部带中间段 **9 列 × 4 行 = 162×72** 显示并可交互（`SlotGroupWidget` 绑到框架注册的 `"player:0"…"player:35"` handler，本仓不自造那 36 格）；shift 点击因此在中栏与背包之间双向可用，★**R93-① 撤销"整栏一键取出"**：中栏原先盖着一块满覆盖隐形件，把 Shift+左键在到达 135 格之前截走、服务端再扫整仓 ⇒ 用户实机报"一次把整栏全拿出来"。现该件与它的动作码/请求口/执行体四类一起删除，Shift+左键交回原版 `ModularContainer` 的 QUICK_MOVE → `transferStackInSlot(slotId)` ⇒ **只搬被点那一格**（与 Shift+右键同一条链，背包段今天本来就这么走）。★顺带消掉第二个缺陷的<b>形状</b>：那条链体内一次都不问 `isGhostItemSlot`，于是一次点击就把<b>整栏</b>挂着声明的格子一起掏空（那才是 R85 A5 管的面）。★但别把这句读成"声明格从此取不出来"：**单格** Shift+左键点在挂着声明的真实格上<b>照旧会取出</b>，走的是 R84 既有口径「声明格禁放置、**可取出**」（原版 QUICK_MOVE 只问 `canTakeStack` 与 `isPhantom`，一次都不问 `isGhostItemSlot`）。★动作码编号不重排：留洞比全体平移安全。★R80①：本段与中栏**同 x 同宽**，都占 118..280
- **Layout / 布局口径**: 面板 **398×360** 真实槽 **220** = 中栏 135 + 流体交互 36 + 蒸馏 12 + 绑定 1 + 玩家背包 36；主区三列实占 `6+108+4+162+4+108+6 = 398`，★R82 把面板宽**收到与实占同宽**（上一版保留的 416 与那 18px 无主空白一起删掉，`MAIN_RIGHT_SLACK` 这个具名量已不存在）⇒ 底部带三段 `112 | 162 | 112`，`6+112+162+112+6 = 398` 且段间不留间距（`BIND_SLACK` 必须为 0，无主空白会装配期报错）。★★**左段那 112×96 的归属（R94-① 定稿，覆盖 R93-③）**：本段现在<b>比另两段高</b>——从 `y = 258` 起、高 96，因为**左列末行那 18px 收了回来**（用户："按钮上下都有文字…我希望按钮移到最下、上面都放说明文字，这样文字就可以放大了"）。段内上下两半：**上 60 = 一整块连续的说明文字**（模式 + 最近一次回执 + 冷却/剩余，字号 `STATUS_TEXT_SCALE = 0.8`）、**下 36 = 两行币栏**（每种币一行三段）⇒ ★**启动按钮压到面板最底，上面整片都是说明文字**。★它仍**不是**两个 18px 件：一条正文要的是连续纵向预算，切两半会让最长那条在第一个件里折四行顶穿、第二个件空着。★左列末行**整行撤销** ⇒ 模式串只在说明块里出现一次（上一版"上下各一段、模式串读两次"就是用户报的那件事）；★三段**底对齐**（左段 258…354 与背包/绑定段 282…354 共用同一条下沿）与"币栏第一行必须压在说明块正下方"都由装配期断言钉住，不靠注释
- **Instant channel / 瞬时通道**: 8 猫猫币，一次穿完全部绑定，图标动画显示 5 秒，冷却 10 秒（玩家维 + 设备维双校验，重启不重置）
- **Timed channel / 短效通道**: 2 闪烁猫猫币，持续 30 秒、每秒一批，每批搬运的「元件×通道」对数由 `pocketChannelPairsPerSecond` 决定（默认 1）；★R97 起配额只被「有机会投」的通道消耗——**无源可投的通道不占对数**（快照里没有该通道能搬的东西——比如口袋里只有流体时——物品通道不再把唯一配额烧掉），且**注入相在通道间轮转**（每批的起始通道 +1 循环推进，配额 1 时物品/流体/源质轮流吃到首对；轮转相位只在内存延续，重启只丢公平起始位、不落 NBT）；★★**R98 需求 3 改判（即刻停道）**：装上并开着「通道持续化」时批边界回满、道永不停（这条没变），但**关掉它的那一拍当场就停**——服务端开关写点认的是 `type + Outcome.TURNED_OFF` 这个**边沿**（`NekoPocketServerHandler.java:424-430` → `PocketChannelManager#stopChannel`，`PocketChannelManager.java:99`，`stop()` 与 `forget()` 成对），★不再等最长 30 秒让批次逐秒衰减；同一拍把 `work` 位镜像清掉（`startWorkTicks(carrier, -1)`）⇒ **面板状态行不再显示剩余秒数、图标帧带与光泽转暗**（★这一句的射程只有 `work` 那一腿：burst 显示窗那一半代表"已发生完的瞬时通道"、边沿腿不碰它，而光泽还含"任一其它升级开着"那一半 ⇒ 三腿是否<b>视觉上</b>同拍翻转属实机项 §二十二 X-4，完整限定见代价 57）。★旧口径「刻意不主动 `stop()`：自然衰减已足够」自 R98 起作废（台账 §R96 ② 原位标注 + §R98 R98-①）。★两点取舍：① 这一拍**一律停**当时在跑的道，**含玩家在窗口内自付 2 闪烁币开的那条手动 30 批道**（状态条目里没有"这条道由持续化撑着"的位，无从区分），已扣的 2 枚按 R14「部分失败不退」**不退**、★也不另发回执（口袋域零聊天输出，R88①）；② 停道最多丢一次**注入相轮转起始位**（游标只在内存、`stop()` 不清它），★不丢件、不重复。★★**R100 片 C 四件套**：① **频率档位**——装着持续化时批节拍不再恒每秒，改按**档位表** `{1,2,3,5,10,15,30,60,120,300,600}` 秒/批（CHANNEL_PERSIST 面上升/降钮调档，**默认 5s** 档；NBT 键 `channelFreqTier` 落档即冻结、**无键 = 默认档** ⇒ 旧存档零迁移；换算单源 `PocketConstants#channelFreqTierTicks`，改档**当场**对在跑的持续化道生效——倒计时钳进新节拍，旧档余量不带过来）；② **免开背包自动恢复**——登录后/服重启后从不开面板的口袋，只要持续化生效且绑定表非空，驱动侧就地复原一只 **headless 会话**让"有位 ⇒ 有道"在不开界面的前提下闭环（`PocketChannelSessions.parseCarrierInventory` 是载体档解析的唯一入口，面板装配与复原腿共用它 ⇒ 无第二份解析真相）；③ **开启即首批**——手动道与持续化道激活瞬间立即跑第一批（due=0 相对倒计时，首批照常计入 30 批预算），不再白等一拍（R100 需求 4 改判，见代价 63）；④ **保守跳过**——本拍既无可投来源（物品/流体/源质三来源全空）又无补满需求时整拍早退：零传输、零 NBT 写、零同步、**不消耗批次**、道不断开（含补满相就不跳——口袋侧读数冒充不了元件事实）。★**手动付费道的"2 闪烁币、每秒一批 ×30 批"语义一字未动**（用户 R100 裁决：可调档位只作用于持续化通道）；唯一精确化：被保守跳过的拍不消耗批次 ⇒ "30 批"从此严格指 30 个真跑过的批，空载时手动道墙钟寿命可长于 30 秒（台账 §R100 ②偏差一）
- **Channel precondition / 前置**: 绑定元件必须处于**带电**的驱动器或 ME 箱子内，否则通道不开
- **★Essentia carrier / 源质搬运载体（R88 改判 → R91 瓶往返再改判）**: 搬运载体是 **TC4 安瓿瓶 `ItemEssence`**（`Thaumcraft:ItemEssence`，**一瓶固定 8 点**、meta 0 空 / 1 满、类型写在栈的 NBT `AspectList` 里、单堆 64）；**晶化源质退役**——不再作为载体被任何路径产出。
  - **★R91-① 判据改口（源质"原生通道"识别法 = AE2 容器契约）**：一条通道算不算源质原生通道，判据是 `IAEStackType#isContainerItemForType(满瓶) && getStackFromContainerItem(满瓶).getStackSize() > 0`（AE2 官方为"容器"留的口）。旧口径走 `convertStackFromItem`，AE2 javadoc 明写该口 `should not be container`、ThaumicEnergistics 的实现只认两家的 `ItemAspect` ⇒ 装了 TE 的整合包**必然假阴**（这就是"无法上传"的真身）。改点只在 `EssenceNativeChannels#nativeProbe` 一处；`getAmountPerUnit()` 作**交叉断言**（★不硬编码倍率、不绑版本），不上时打一条一次性 WARN。政策四项不变：无原生通道 ⇒ `NO_CHANNEL` 面板拒收 + 粘性回执、上传链路零瓶化、不回落物品通道。★**代价**：判据依赖第三方对容器契约的实现，是否命中属实机项（离线只钉判据形状与接线）。
  - **依据（实机事实，非推断）**：NEI 物品面板里**每个 aspect 各一条、图标按该 aspect 染色**的那些条目就是瓶（TC `ItemEssence#getSubItems` 逐 aspect 造**满瓶**并 `setAspects(add(tag, 8))` ⇒ 条目自带可读 NBT，拖到格上判得出归属）；而**晶在 NEI 只有一条、颜色按时间轮播**（无 NBT ⇒ 结构上判不出 tag，旧实现靠一条"晶族恒真"特判放水）。
  - **★R91-④ 瓶往返改判（用户报"凭空生成安瓿瓶"）**：**取出必须手持空瓶**——左键点格消耗 **1 只**空瓶装 **1 只**（R91-e 裁定：单点 = 一只，shift = 装到既有单动作上限；★撤销 R90 D1 的自造瓶路径，`newPhialStack` 一类"无中生有"物化口全仓归零并进机检）；空手 / 持非容器点格 ⇒ **零产出、零扣点**，只给面板内 `essence.need_phial` 回执。**入槽返还空瓶**：满瓶溶进盘 ⇒ 等量空瓶原路退回游标（★晶档继续整叠销毁、不退空壳，R86 安全要求只对晶成立）。本地路径不依赖 AE 在场：装瓶/倒空走 TC 容器 helper 一份真相。
  - **★自立口径 C1（最小粒度 = 一瓶）**：从盘里取出时**向下取整到 8 的倍数**，凑不满一瓶的**余数原地留盘**并给面板回执（`essence.partial_leftover` / `essence.not_enough_phial`）。不做半瓶——TC 的瓶没有半瓶语义。
  - **★自立口径 C2（旧晶只读不产）**：存档里既存的晶仍会被**识别**并溶回盘（1 点/枚，不吃件），但任何路径都不再产出晶。
  - **下传的落点也改了**：从元件拉回的源质**读容器 → 点数加回 72 格盘 → 容器就地消耗**，不再往物品栏塞一叠东西。
  - **聊天框零输出**：源质入盘、12 格注入、蒸馏入账这类操作性回执一律走**面板内粘性回执**，pocket 域 `addChatMessage` 的**实站点为 0**（机检在 `verify-pocket.sh` ★R88①）。
  - **游戏内文案落点**（与本节同源，项目纪律要求双处声明）：帮助块 `gtit.pocket.note.phial`（瓶往返三层一次说完）、源质格 tooltip `gtit.pocket.essence.phial_note`、声明引导 `gtit.pocket.essence.need_stock`、取瓶引导 `gtit.pocket.essence.need_phial`（★R91 新增）。
- **★Cell attributes & gestures / 三种格子属性与手势重排（R91-⑤⑥⑧b）**: 中栏／流体列／源质盘的格子上新增**三种属性**，手势整体重排——
  - **中键 = 请求绑定（BIND）**：把这一格登记成一条会从 AE 网络补货的需求（★原 alt+左 的语义迁到中键）。**整理让位**：中栏整理从此**只剩 R 键**一个手势（`sort_gesture` 文案与 `NekoPocketPanel` 说明同步改述；若继续写"中键＝整理"就是第二条"文案声称了代码不做的事"）。中键支必须**早退不调 `super`**（原版把 button 2 读成创造取物）。
  - **alt+左键 = 记忆（L）**：本格**只能放那一种东西**，观感为虚化遮罩 + 左上角蓝色 `L`；★**纯过滤，不产生任何 AE 拉取**（补货只认 BIND）。**三条执法腿（★R91-p 把流体支补成第三支，此前流体列挂了 `L` 没有执法面）**：物品 = `PocketInventory#newStorageGroup` 的 `isItemValid` 链问 `allowsPlayerPlacement`；流体 = 两个玩家灌入入口（交互格容器处理进 `PocketFluidTransfer#drainIntoTank` 先问、拒 ⇒ 容器原样躺进格并给 `fluid.tank_memory_locked` 回执；持容器点流体槽走 MUI2 handler 自带的 `filter` 谓词，在 `fillFluid` 预检处整支静默拒掉——与库内"条内是别的流体"那一拒同形）；源质 = 入槽四参形态的 `MemoryGate`。三条腿的**判据全部住在 `PocketFilterConfig` 那一张位表**（`allowsPlayerPlacement` / `memoryAllowsFluid` / `memoryAllowsTag` 同一族三条口径），调用方只问结论——★上一条都不许多，也不许少。
  - **alt+右键 = 阻拦上传（P）**：本格内容**永不进注入向**，观感为左下角绿色 `P`；与 attr **正交可并存**；只有 P 的格不携带内容 ⇒ 对 NEI 拖拽**不响应**。
  - 三手势共同规则：**格内有物 ⇒ 按该物记录；空 ⇒ 只进入该状态**（pending），内容待 NEI 拖拽落成。模型 `attr ∈ {NONE, BIND, MEMORY}` 互斥单值（同格重复同手势 = 撤销），NEI 拖拽按 `dragRouteOf` 四行分派（BIND→绑定物 / MEMORY→记忆物 / 无 attr 无 P→隐式建档【需求 4 不回退】/ 只有 P→不响应）。
  - **数据面**：S2C 属性层走**独立的第二枚 `StringSyncValue`**（`SYNC_GHOST_FLAGS`，不焊回 ghost blob 编解码）；C2S **复用 `SYNC_GHOST_REQUEST`** 加一条 `FLG|槽|区|手势` 四段支，★零新动作码、零新同步键。pending 档（挂了 L 没载荷）**不限制放置**（R91-i 显式裁定：还没有可比载荷，限制无从谈起）。
  - **★R91-h（阻断级落码）**：`FLG` 支读回的载荷键必须与请求里的**区域字母同 kind**，不一致 ⇒ 整条 `REJECTED`（不写、不猜）——这是本仓 R70 `CLR|0` bug 家族（三区索引各自从 0 起，跨区合法键会静默落进"那个区同号的格"）。
  - **★代价并列披露**：一格可能同时出现**三枚读数**（虚化遮罩 + `L`/`P` 角标 + 既有 cap 读数），16px 格内净空属实机项（R91-l）；旧手势（alt+左 = 绑定标记）的玩家肌肉记忆从此作废；alt+滚轮调上限与 alt+左/右**不冲突**（滚轮与左右键是不同输入事件，已核对，文案如实保留）。
- **NEI ghost / 就地虚化**: 从 NEI 把物品 / 流体 / 源质拖到格上即把**该格**配成 ghost（右键解绑），通道打开时按声明从元件里补齐；声明侧 tag 反解走**单源判据**（TC 容器 → TC 蒸馏表 → AspectRecipeIndex 伪物品 `item.aspect` 第三条读数，★白名单伪物品**只可声明、绝不入库存计点**）。★源质声明按**格号**索引，因此"撤空不回收格位"是它正确性的前提（回收会让已声明的格拉错东西）
- **说明文字 / Where the help text went（R78 D-2）**: 面板内**不再常驻**任何说明书文字（图例、用法摘要、主手限制、每槽容量全部撤进 tooltip：格件 tooltip + 左栏末行 tooltip + 底部带右段的「?」帮助按钮）。常驻只剩**一行状态回显**（推送/拉取方向 + 冷却/剩余 + 最近一次回执）与**两条常驻绑定行**（★R81③ 补的，理由见上面 Binding 条：玩家不悬停也必须能读出"到底绑了几条"）——前者是服务端每拍算出来的运行期事实，后者是运行期事实的清单，都不是说明书。★★**R98 给这条补三处更正**（旧句写的是 R78 那一代的形状，三处已经与代码分叉）：① 括号里那项**「主手限制」从来没有对应的 tooltip 行**（实读两份 `item.neko_dimension_pocket.tooltip.*` 族零命中，取证 `01a` §6-3）⇒ 它既不在面板常驻、也不在物品 tooltip，本轮把 README 代价 1 与检查表 §1.6 里"已声明"的说法一并改口；② **「每槽容量」自 R98 起不再进物品 tooltip**（旧 `tooltip.9` 整行撤掉，见代价 55），它的读数面**全部在面板侧且三处同源**同一条 `capacityReadoutText()`：流体格件 tooltip（`NekoPocketFluidSlot`）+ 本条这块说明文字的 tooltip（`NekoPocketBottomBand:1130`）+ ★新增的**配置面板 CAPACITY 面那一行**（`PocketConfigPanel:1110`）——★不是"只剩配置面板看得见"；③ 那句里的「左栏末行 tooltip」这个宿主自 R94-① 起随末行**整行撤销**（见代价 27），今天挂着那三条 tooltip 的是**底部带左段那块 112×60 说明文字**（它仍是 tooltip，不是常驻说明书）
- **★Sneak right-click never opens / 潜行右击永不开屏（R91-⑧）**：手持口袋对**任何目标**（机器 / 空气 / 非机器方块）潜行右击**一律不开面板**——闸在开屏唯一入口 `Item#onItemRightClick`（`isSneak ⇒ return`），与原版 C08 双包链的补发形状无关；非潜行右击开屏不变；潜行右击 GT5U 机器 = 抽流体（R90 新功能 N）。★**代价**：想"潜着开面板"的玩家改按普通右击；过去依赖"潜行右击也开屏"的旧习惯作废。
- **Icon / 图标**: 四态动画材质（静态 / 打开 / 工作 / 打开且工作，`frametime` 4/3/3/2），工作位扫光只在通道或蒸馏活跃时出现
- **Recipe / 配方**: `PEP / LCL / LLL`（末影珍珠 + 末影之眼 + 皮革 + 猫猫无限存储单元）；合成匹配不比较元件 NBT
- **Requires / 前置**: ModularUI2、AE2、GT5U；Thaumcraft 4 为**可选**（缺席时蒸馏栏整栏灰显而非隐藏，面板宽度不变）

**口径代价 / Documented trade-offs**（设计选择，不是缺陷）：

1. **★口袋在背包任意格与饰品栏都工作（R95-① 立、★R98-④ 把口径写正）**：通道与蒸馏的被动宿主是**两枚**——`Item#onUpdate`（vanilla 每 tick 只遍历主背包 36 格）与 `onWornTick`（穿戴态，Baubles 的 `EventHandlerEntity.playerTick` 驱动），两枚共用同一段 `runPassives`（`ItemNekoDimensionPocket.java:301-305` / `:323-327`）；★该段自 R95 起**不**用形参 `slot` / `selected` 做门控（javadoc `:318-321` 明写；体首行只问 `world.isRemote` 与 `entity instanceof EntityPlayer`，`!selected` 字面全仓为零）⇒ **放进背包 36 格的任意一位、或穿在 Baubles 饰品栏上都照常推进**，关掉背包界面也不停（同一口径见代价 29、lang `gtit.pocket.held.note` 与本轮改口的 `gtit.pocket.note.summary`）。★本条旧文（「只在主手工作 ⇒ 塞进副手或背包深处即停摆，面板与 tooltip 均已声明」）**两处都不成立**：背包那一半自 R95-① 就是反的，「tooltip 已声明」那一半**从来没有对应的 tooltip 行**（实读两份 lang 的 `item.neko_dimension_pocket.tooltip.*` 族零命中，取证 `01a` §6-3）。★真正不工作的场景仍然有，但理由在 vanilla 那一侧、不是本模加的门：**箱内 / 展示架 / 掉落物 / 盔甲位**都不走 `onUpdate`（盔甲位是 `onArmorTick` 另一条路）。★成本上界同样由 vanilla 给出：最多 36 次/玩家/tick 的 `onUpdate`，而三个 driver 每次进入先读一次位图早退（无升级 ⇒ 一次 `hasUpgrade` 即返回），**不新增每 tick 的 NBT 写入**。
2. **ghost 声明会占掉一格真实存储**：中栏声明与真实格共用同一 135 格索引空间（流体槽与源质格各在自己的索引空间，不占中栏真实槽）。
3. **内容随物品一起丢**：135 格物品、18 个流体槽与 72 格源质的归属表都存在口袋 NBT 里，口袋丢失/销毁即同时失去其中所有内容。
4. **每槽 20,000,000 mB 是超规格自选值**：需求只说「像 GT5U 流体仓」而未给数字，此值取自大型储罐尺度、按 18 个槽独立计（★合计 **360,000,000**，★R96 S3 由 R75 的 16,000,000 抬到 20,000,000；R78 那代从六列的 96,000,000 起算，两代史实都只留在这里，不再作为当前读数），不是规格出处。
5. **★面板内开玩家背包 = 包放大风险回归（E4，R78① 明确换回来的代价）**：那 36 格由 MUI2 默认绑定进 Container，于是首次打开要同步 36 格，且 vanilla `Container#detectAndSendChanges` **每 tick** 对这 36 格做整栈相等比较（**含 NBT 深比较**）；玩家背包内容一变，就把整枚口袋连同 135 格一起重发。旧实现（R69-D2）用"预注册空 `PlayerSlotGroup`"消掉了这条代价，R78 按用户裁决撤销那一招 ⇒ 这条代价是**为要背包而换回来的**，不是实现走形。
6. **中栏 15 行一行不删**：背包只能横向挤（底部带拆三段），因为 360 高是 GUI Scale 3 的硬天花板。
7. **面板高 360 是 GUI Scale 3 的硬上限**：1080p 下 Scale 3 只有 360 逻辑像素可用，故中栏取 15 行而非 16 行；**Scale 4 会纵向溢出**，请用 Scale ≤3。
8. **三段宽度的历史取舍**：R78 把币值/通道段 180→100、绑定段 220→120（绑定格挪到第二行，绑定按钮的 106 原生宽不缩——缩它会破 C2 贴图契约）；R80① 中栏收到 9 列后曾把面板宽留在 416，于是三段变 `112 | 162 | 130`（左段由中栏左沿倒推、右段吸收让出的 18px），段间 4px 间距并进段宽 ⇒ 背包段与中栏逐像素对齐；★R82 再把面板宽收到 **398 = 主区实占**，右段 130→**112**（= 6×18 + 4 一格绳缝），那 18px 的"无主空白"连同 `MAIN_RIGHT_SLACK` 一起删除——**收宽而不是塞内容**，因为拿空白去发明需求外的功能不是设计。
9. **★旧档收缩兼容（R80①）**：150 格时代写的档读到 135 格 ⇒ 越界的 135…149 号条目被**丢弃**并按区各敲**一条 WARN**（含条数与现有格数），handler 格数永远由构造期决定 ⇒ 面板不炸、0…134 号条目原样落位；丢件不是静默的，但**确实是丢件**（升级前请把后 15 格腾空）。
10. **★源质"取出"与"上下传"是两档粒度（R88 C1 + ★R92-① 分档）**：**取瓶路**（玩家游标 / Shift 进背包）最小粒度仍是**一瓶 = 8 点**，不足一瓶的零头拿不出来、留在盘里等攒够（面板给"取出 N / 留盘 M"回执，不静默少给）——TC 的 `ItemEssence` 没有半瓶语义（`canHoldPartialAmount = false`），自造半瓶就是把私有形状送进第三方兼容面。★但**通道路**（口袋 ↔ 元件的上传/下传）自 R92-① 起按 **1 点**量化：AE2 源质原生通道天然支持 1 点，旧形状在两条腿上各写一次 `floorToPhialUnits` 才是"一次 8"的真身。⇒ 换算单源收成 `PocketEssenceChannelOps#channelUnitsForPoints` 一处，两档之比就是"一只瓶折多少通道点"，★仍不写死倍率。
11. **★通道倍率除不尽瓶档时退回瓶档（R92-① 的 P1 保守档）**：某第三方通道"一只满瓶折多少通道单位"不是 8 的整数倍 ⇒ 按点换算会丢精度，该通道**整体退回瓶档**（与改判前逐位一致）并留一行一次性 INFO。★宁可少搬，绝不静默丢点；用例以 0…300 点穷举钉住"降级档 = 旧瓶档读数"这条等价。
12. **★ghost 组上限步进与搬运粒度已重新重合（R92-① 收口 R88 那条张力）**：`FILTER_CAP_STEP_ESSENCE` 是 1 点、通道档也是 1 点 ⇒ 把上限停在 3、17 这类非整瓶档位**照样搬得动**（旧形状那里向下取整到 0 / 16，通道照跑、零搬运）。取瓶路不受影响。★抬天花板（代价 2 的 R92-②）之所以安全，前提正是这一条：落点预检也从"逐瓶回退 + 每退一次重跑 `canAcceptAll`"收口成一次 `PocketEssenceStore#roomFor`，"一批 256 点折算不成整数 ⇒ 净吞点数"那族形状结构性不可达（V 段 ★R92-② G3 把它钉成顺序闸）。
13. **★容器契约判据的上游依赖（R91-①）**：源质通道识别改走 AE2 容器契约后，"命中与否"取决于第三方通道对 `isContainerItemForType`/`getStackFromContainerItem` 的实现；若存在第四个 `IAEStackType` 注册者，`nativeChannelTypeId` 的"取第一条"确定性（`getAllTypes()` 是 HashMap 序）只能真机判。单位交叉断言不上时只打一次性 WARN，**不改判据与算术**（仍以容器栈读数当 unit）。
14. **★灌装依赖"同叠共享一份 `AspectList`"（R91-④/R91-f）**：一次点击的灌装是对游标那叠空瓶的**一次** `addEssentia`（TC 瓶内容挂在栈 NBT、一叠一份，入槽侧 `scaledByStackSize` 是其逆运算）。真机若出现整叠只算一只或 TE 读数差 8 倍 ⇒ 改逐只 `copy()` 循环，算术不动——**JVM 测不到（TC 缺席恒降级），只能实机裁决**。
15. **★手势重排的肌肉记忆代价（R91-⑤⑧b）**：alt+左 从"绑定标记"改派为"记忆 L"、绑定改挂**中键**、整理**只剩 R 键**；旧习惯期玩家会按错。另有两枚新角标（蓝 `L` 左上 / 绿 `P` 左下）叠进 16px 格——一格最多三枚读数（遮罩 + 角标 + cap），**净空属实机项**（R91-l），离线只钉了"几何不重叠"的算式。
16. **★潜行右击永不开屏（R91-⑧）**：这是裁定的口径不是过渡态——想开屏就普通右击。修在开屏唯一入口（`onItemRightClick` 潜行闸），因此与原版 C08 双包链怎么发都无关；三个 `return false` 出口一个字不改（改了会砍掉 R90 的抽液新功能）。
17. **★本轮（R91）实机未验**：本轮全部 JVM 用例与机检（`runPocketTest` / `verify-pocket.sh`）钉的是判据形状、接线、算术与文案在场；**通道命中、真瓶 NBT 往返、手势在真实 GUI 里的消费顺序、角标净空、旧档升级手感**全部属实机项（判据见 `plan/_taskpack/in-game-checklist.md` §十四）。**不得把"构建与测试绿"读成"实机已验证"**；本轮收束态只能记「交付待实机验收」。
18. **★右上角橙色是"组上限读数"，不是记忆角标（R92-③）**：用户报的"alt+左键唤出右上角橙色"实为这条橙字——旧判据只看"格内有没有载荷"、一个字没问属性，而 alt+左会落载荷 ⇒ 橙字跟着冒出来。本号把可见性收成单源 `PocketGhostRequest#capReadoutVisible`（`声明 && 属性 ≠ MEMORY`）。★取"排除记忆档"而不是"只认中键"是刻意的：NEI 直接拖入建档的格子属性为 NONE 且今天照样拉货，只认中键会让它**永久失去上限读数** = 撤信息没有落点。左上 `L`、左下 `P` 两个角标与遮罩一字未动。
19. **★记忆档空格"放上去即配置"，但已定档格不被放置改写（R92-④）**：旧形状是"没被拦、但根本没有配置口"——全仓只有 NEI 拖入与手势两条路写声明。本号补三条落档腿（物品 / 流体 / 源质安瓿瓶），准入判据单源 `PocketFilterConfig#memoryPendingForPlacement`、写入单源 `#declare`。★**只对"记忆档 + 尚无声明"开放**：NONE / BIND 格放置既不放开也不建档；已定档格放**异类** ⇒ 拒绝、声明不变（换声明走 NEI 或手势）——放置与定档不永久耦合。★源质支还撤掉了"已声明格整条关掉左键分流"那道 `!ghost` 前置（取出向照旧早退，行为逐字不变）；入槽动作码从此**带上玩家实点的那一格**（旧形状把 arg 丢了），但**记忆闸仍现读 `cellOf(tag)`**，两者分离由用例钉住。
20. **★空格子 tooltip 是"补两样"而不是"删到剩两样"（R92-⑤）**：取证实读——三属性此前在格子 tooltip 里**一行都没有**（三条 `*_gesture` 长句只住在底部帮助块，玩家点不到格子那一层）。本号给空格补上「这一格是什么」+「三个功能键各干什么」，读数单源 `PocketCellIdentity`（三类格件共用一份）。★格身份的行列/格号一律由 `PocketConstants.storageRowOf/storageColumnOf/essenceCellNumberOf` 派生，显示点不写 `/ 9 + 1`；有内容格的那条分支一字未动。长句与短句**两套并存**（D5 未撤帮助块）。
21. **★常驻小字统一档 = 0.6（R92-⑥；★R93-③ 原位更正它的理由）**：八处常驻 `TextWidget` 从写死的 `0.5f` 换成单源 `PocketGhostRequest.RESIDENT_TEXT_SCALE`。★当时写的是"0.6 = 算出来的**下界**，卡在左列末行 88×18（最坏 330 逻辑像素）"——**那句算错了**：状态串还会在最前面拼一条**回执**，最长那条单独就 ≈330，拼全的真实最坏是 **663 逻辑像素** ⇒ 那一格的上界其实是 **0.45**，也就是说 0.6 本来就在那一格上越界（用户实机报的"说明文字…会超出"就是它的表现）。★R93-③ 的修法不是降档而是**换落点**（长文搬进底部带那块 112×36、另立 `STATUS_TEXT_SCALE = 0.65`）；★R94-① 再往前一步：那块长到 **112×60**、字号抬到 **0.8**（左列末行整行撤销，见代价 27），并把这个漏项在机检里堵死：像素账现在**遍历全部 `gtit.pocket.receipt.*` 取最宽**，以后新增/加长任何回执键都自动入账。配色按拍板的分工：**提示语深色、数据读数白色 + 阴影**（两条单源 `hintTextColor()` / `readoutTextColor()`，写成方法而非常量，否则本类在零依赖测试 JVM 里初始化即炸）。★顺带修一个真缺陷：`ButtonWidget` 走 `SingleChildWidget`、**不替子件摆位**，所以"绑定/启动"两个标签的 `textAlign(Center)` 从来没生效过（截图里都顶着左上角）⇒ 居中量必须由盒子产生，现给子件显式 `pos(0,0)+size(父宽,父高)`，★父盒与段宽一个字未改（398×360 是硬顶）。★16px 格内那三个读数（遮罩 + 角标 + cap）用的是另一张几何账（`CAP_READOUT_SCALE`），本号明令未动。
22. **★本轮（R92）实机未验**：本轮全部 JVM 用例与机检钉的是判据形状、接线、算术、像素账与文案在场；**通道 1 点上传在真实 AE2 里的观感、上限 256 的三位橙字压不压线、alt+左 之后橙字是否真消失、放置即定档的手感、往已声明格放瓶的入口、空格 tooltip 是否溢出、0.6 档是否折行、两个标签是否真居中**全部属实机项（判据见 `plan/_taskpack/in-game-checklist.md` §十五）。**不得把"构建与测试绿"读成"实机已验证"**；本轮收束态只能记「交付待实机验收」。R86–R91 的实机项继续全部挂账。
23. **★「整栏一键取出」这个功能已经不存在（R93-①）**：中栏原先盖着一块满覆盖隐形件，它把 Shift+左键在到达 135 格之前截走、服务端再**扫整个 slot group** ⇒ 用户实机读到"一次把整栏全拿出来"。本号把**该件与它的动作码 / 请求口 / 执行体四类一起删除**（留动作码就是留一条零调用方的整仓扫描支），Shift+左键交回原版 `ModularContainer` QUICK_MOVE → `transferStackInSlot(slotId)` ⇒ **只搬被点那一格**。★连带消掉的是第二个缺陷的<b>形状</b>——那条链一次都不问 `isGhostItemSlot`，于是一次点击就把<b>整栏</b>挂着声明的格子一起掏空（R85 A5 管的就是这种整批搬运）。★口径边界必须说清：**单格** Shift+左键点在挂着声明的真实格上**仍然会把它取出**，那不是绕过 A5，而是 R84 既有的「声明格禁放置、**可取出**」（库链：`NekoFilterSlot` 给声明档 `accessibility(false,true)` ⇒ `canTake` 真；原版 QUICK_MOVE 只问 `canTakeStack` 与 `isPhantom`，★一次都不问 `isGhostItemSlot`）。★MUI2 的 `onMousePressed(int)` **不带鼠标坐标**，"自己算被点哪格"在本库根本不成立 ⇒ 唯一正确解是不拦截。★动作码编号**不重排**（留洞比全体平移安全）。
24. **★流体「出格」是单向门（R93-②）**：只有每组**上一行（进格）**能放容器——灌与排都从它发起；**下一行（出格）**只收系统写入的产物、玩家放东西被挡回（两侧共问单源谓词 `isLowerInteractionRow`：装配侧挡手感、服务端入口守真值）。★出格仍可取出（否则产物被永久关死）；★**18 个流体槽本体不受影响**，手持储罐直接点槽仍是 GT5U 那套按键语义。★代价：旧"下行原地抽干 / 余量留在下行"两条验收面（检查表 2.5 / 9.6）随裁定作废，`restCellOf` 第一支、`canPlacePair` 的 `replaceable` 同支与那道输出闩自此**玩家路径不可达**——★按 R91-i 通则**不删**，改由用例钉"处理永不从出格发起"这条正向判据。
25. **★说明文字换落点 + 两档字号，且有三处与用户字面要求的偏差（R93-③）**：① 用户要求"按钮整体移到最底下"，实查发现那两行位 **R84 就已经腾出来了** ⇒ 一枚按钮都没搬，直接把左段下沿那块 112×36 改道给说明文字，结果与"移到底部"等价且不动任何几何账；② **模式串当时两处各出现一次**（左列末行 + 底部说明块），两处都走 `modeText()` 同一个源 ⇒ 重复的是字、不是第二处真相（当时撤左列那一处会在该列留下 18px 无主空白）。★★R94-① 把那一行整行撤销 ⇒ 这条重复随之消失，而"18px 无主空白"并没有出现——那格被底部带左段吃去做说明了（见代价 27）。★本条的教训不是"论证写错了"，而是**论证成立不等于形状可接受**；③ 0.65 **不是**计算上界（该点算出来是 0.70，按用户拍板停 0.65、差的 0.05 当实机安全边际），而统一档 0.6 之上有 **0.3 未用余量本轮刻意不抬**（用户点名的只有说明文字那一处）。★另登记一处命名债：那块仍叫 `CELL_INFO_*` / `CellInfoText`（改名会让十四条装配期断言与 V 锚点空转，收益只有好看）。
26. **★本轮（R93）实机未验**：JVM 用例与机检钉的是判据形状、接线、算术、像素账与文案在场；**一整块说明文字在 112×36 里的真实折行与 0.65 档是否压线、右栏那条整条位置行会不会顶到帮助按钮、出格拒放的手感（东西弹回光标、无额外文字回执）、Shift+左键逐格搬是否合意、U+3000 换 ASCII 空格后的实际间距**全部属实机项（判据见 `plan/_taskpack/in-game-checklist.md` §十六）。**不得把"构建与测试绿"读成"实机已验证"**；本轮收束态只能记「交付待实机验收」。R86–R92 的实机项继续全部挂账。
27. **★左列末行整行撤销、说明块长到 112×60、启动按钮压到最底（R94-①）**：用户看了 v1.8.37 的形状指出"按钮上下都有文字…我希望按钮移到最下、上面都放说明文字，这样文字就可以放大了"。★上一轮（R93-③）我只把长正文搬到底部带那块 112×36、把模式串留在左列末行，于是同一块区域里上下各一段、模式串读两次。本轮做法：左列末行那 18px 收回给底部带左段（该段从 `y = 258` 起、高 96 = 上 60 说明 + 下 36 币栏），字号 0.65 → **0.8**（★该点计算上界 1.00，留 0.2 实机安全边际）。★代价与边界：① 左列现在比中栏矮一格（252 vs 270，★中栏 15 行一格未删），那一格归左段 ⇒ 三段底对齐；② 左列与带之间那条 6px 外边距被说明块吃掉 ⇒ 视觉上"贴得更紧"，是否读成挤属实机项；③ 末行原先挂的三条 tooltip（完整状态读法 / 每槽容量 / 用法摘要）**原样搬进说明块**（★撤形状成对，一条不删）；④ `R78 D-2` 允许本列常驻"一行状态回显"那半条随末行撤销，★"说明书文字不得常驻"那半条继续有效（本轮搬的是运行期状态，不是说明书）；⑤ ★装饰层的**下沿铆钉**（钉在流体列分隔缝下端，y≈270…276）现在落进加高后的说明块右沿内——装饰是最底一层、文字盖在它上面，纯观感（独立审查指出，本轮只如实登记，不挪铆钉：挪它就等于改主区缝的几何账）。
28. **★本轮（R94）实机未验**：JVM 用例与机检钉的是几何派生式、顺序判据、落点计数与文案在场；**0.8 档在 112×60 里的真实折行与是否压线、左段比另两段高出一截后的观感、说明块同时挂三条 tooltip 会不会挡视线、按钮压底后与背包段第一行的视觉关系**全部属实机项（判据见 `plan/_taskpack/in-game-checklist.md` §十七）。★本轮 lang 零增删（两份仍 349 键、差集为空）⇒ 玩家可见口径未变，改的只有排版与字号。不得把"构建与测试绿"读成"实机已验证"；R86–R93 的实机项继续全部挂账。
29. **★口袋在背包任意格都工作（R95-①，口径变更）**：旧版要求口袋**握在主手**才继续蒸馏/推通道（`R24` 尾段与 `R70` 的裁定），本轮按用户实机裁定**摘掉该门控** ⇒ 塞在背包 36 格的任意一位都照常推进，**关掉背包界面也不停**。★代价与边界：① 成本上界由 vanilla 的 tick 链给出（`InventoryPlayer.java:343-348` 每 tick 遍历全部 36 格各调一次 `Item.onUpdate`）⇒ 最坏 36 次/玩家/tick，而每次进入都先读一次升级位图早退，无升级的口袋几乎零成本、**不新增每 tick 的 NBT 写入**；② ★仍然不跑的场景：箱内 / 展示架 / 掉落物（vanilla 根本不 tick 那些位置的物品，不是本模组加的门），盔甲位走 `onArmorTick` 与物品 `onUpdate` 无关；③ 旧文案 `gtit.pocket.held.note`（"必须手持"）随本轮过期，玩家可见口径以本条与 lang 为准。
30. **★容量升级 = 2G 双轨（R95-③ 立、★R96 S3 换档）**：未升级档 **每槽 20,000,000 mB（20M）**；装上 `capacity` 插件后单条流体条容量 → **2,000,000,000 mB（2G = 20 亿，★R96 S3 由用户从 16G 改判为十进制 2G）**，18 条合计 36G。★实现仍是**双轨**：真值住 `AmountL`（long，权威），旧字段 `Amount` 仍是 int 且恒等于 `min(真值, 2,147,483,647)`（这条头不变式由**双保险执法**——业务写全部收进真值域单式、推完真值后由调用点回同步 `syncHead` 钳头，且构造期把 tank 覆写为子类、`fill`/`drain` 转发该单式，MUI2 的增量分支不再旁路）⇒ 老版本读得到的字段永远不自相矛盾，读档优先 `AmountL`、没有该键的旧档按 `Amount` 回退（**旧档零损失**）。★**与 R95 那代的实质差别（这条撤销必须说清）**：2G **不超过** int 顶（`2,000,000,000 < 2,147,483,647`）⇒ 旧口径里「**真值一旦超过 int 顶（2.147G），原版容器口径的灌排就收 0**」那一支在本档**数值上不可达**，头与真值恒同值（用例 `fluid_truth_never_exceeds_int_head` 把这条钉住，不是只删文档）。★双轨机制本身**全部保留**：已写出的 16G 时代存档只有 `AmountL` 保得住原值，且 7 处 `min(…, Integer.MAX_VALUE)` 在这一档退化为恒等、零成本，删任何一处都是把「当前够不着」读成「永远够不着」。读数侧仍走 K/M/G 梯子（`20M` / `2G`）；**仍不承诺**跨版本容量一致——旧 jar 读新档只认 `Amount`，而 `Amount` 现在就是真值本身。
31. **★堆叠升级 ×16：两条尺子与「GUI 那一把是平的」（R95-④ 立、★★R96 S4 重写）**：装上 `stack` 插件后中栏单格上限 64 → **1024**、源质每格上限 256 → **4096**。★★**R95 那一版这条是记为完成而实机未生效的**（取证 `r96-ret1 §1`）：口袋 GUI 是 ModularUI2 自有容器，玩家每一条手势的件数由 `ModularContainer.stackLimit` 单点决定，它对 modular 槽转调 `SlotItemHandler.getItemStackLimit`，而后者**第一行**就取 `stack.getMaxStackSize()` ⇒ 恒 64；当年覆写的是 `ItemStackHandler` 的 `getSlotLimit`/`getStackLimit` 两条，只有 `insertItem` 会问到它们，而 GUI 写格走 `putStack → setStackInSlot`（**不钳**）⇒ **算式对，执法点从来没被问到**。S4 补的是 MUI2 为此准备的唯一开关 `ModularSlot.ignoreMaxStackSize(true)`（恒 true，两档由 handler 收口）+ `canDragInto(false)`，并把「点一次整理把 1024 拆回 64」与「ME 推送每批只走 64」两条独立破坏改读同一条单源算式。★**现在的两把尺子，必须分开说，不许合并成一句「×16 生效」**：① **程序化写入面**（磁力拾取、ME 补满、产物重塞、读档）仍是 `min(格上限, 物品自身上限 × 16)` ⇒ 16 叠药材 256、不可叠物品 1，逐字不变；② ★**玩家手势面自 S4 起是一把平尺**（`getSlotLimit(index)` 按定义不含物品）⇒ 基档一律 64、升级档一律 1024，**16 叠药材与不可叠的工具也在这一把尺上**。★连带更正 R95 旧文案里那句「只能叠 1 的工具与插件升级后仍是 1」：**那只对①成立**，S4 当时的手势面上确实是 64/1024（→ 已被 S4b 收口，见下）。★★**R96 S4b 补另一半（R-2 闭合）**：平尺决定的是手势一次**问**多少件，★**落进格里的件数**由 `PocketInventory` 的中栏 handler 覆写 `setStackInSlot`（那一支带 `stack` 参数）按 `effectiveStorageLimit(当前档位, 这一件)` 收口 ⇒ **未升级档 1 / 16 / 64、升级档 1 / 256 / 1024 三族各回各位**，「两把同款新斧头叠成一格」这一族不再可达（多出来的那一把被摊到别的空格）。★两条纪律一起成立：**收口 ≠ 吃件**（钳下来的差额走既有落点 `depositIntoStorage`，先同类再空槽、跳过声明格，放不下就并回本格 ⇒ 宁可就得多绝不凭空少 —— 手势的合并腿是「先从游标扣件、再 `putStack`」，静默裁数等于把玩家的东西变没）；**内部整体搬运挂起收口**（读档那一段与 `performSort` 的回写那一段成对挂起，否则差额会被摊到后面那几次回写的落点上被覆盖 = 整理吃件，且与 P-4「关开关不许销毁已存进去的东西」同一条裁定）。★S4 当时写的「不在 S4 擅自动 `getSlotLimit`」这条裁定**仍然成立**：平尺一字未动（A1 的 1024 与 A2 的平尺半边照旧），补的是收口那一半。★**同时改口 A2 的规格**：它原来断言「未升级档恒 64」——那是一条**写错的判据**（把平尺读成了规格），未升级档对不同物品本来就该给 1 / 16 / 64；用例 `stack_limit_base_tier_unchanged_at_enforcement` 已改成按件断言（★突变自证：摘掉收口 ⇒ 不可叠那一档红「期望 1 / 实际 2」）。★新增用例 `storage_slot_write_enforces_per_item_tier`（升级档分档 + 不吃件 + 挂起不漏还，全条真驱动）。★本条原有的三条用例继续在位：A1 `stack_limit_enforcement_asked_by_modular_container`（★修前红「期望 1024 / 实际 64」，S4b 一字未动）、A2 `stack_limit_base_tier_unchanged_at_enforcement`（★已改口成按件）、平尺读数 `stack_slot_ruler_is_flat_across_items_cost`（★断言一字未动，读法已补成「平尺仍在、已被收口兜住」）。五条腿的逐条定性见代价 36。
32. **★磁力升级：自动拾取，落点是三级兜底（R95-⑥）**：装上 `magnet` 插件后，每 **10 tick** 扫一次玩家周围 **8 格**（AABB 三轴各扩 8）的掉落物并自动收进口袋。★顺序是**入袋 → 背包 → 脚下**：先尽量塞进中栏，塞不下的余量交玩家背包，背包也满才掉在玩家脚下（★满载时物品会在脚下滞留，这是磁力满载的可观测代价，不是丢失）。★两条克制：① 尊重 `delayBeforeCanPickup` ⇒ 玩家**故意丢出去**的东西不会被瞬间吸回；② 吸收成功后原实体立即摘除（不摘就是复制）。★成本：每枚带磁力的口袋各自独立扫描 ⇒ N 枚 = N 次查询（无实体时一次空 AABB 即返回，零 NBT 读写）。
33. **★通道持续化与蒸馏加速（R95-②⑤）+ 本轮实机未验四条**：装上 `channel_persist` 插件后短效通道**批边界自动续批、永不停**，工作帧带常亮；★**免费、免冷却**——一次性付过激活费后不再到 0，不引入任何周期扣费，也不占瞬时冷却。通道按钮**两端都禁用**（客户端置灰 + 服务端在识别/冷却/扣费三重点检之前早退，返回"已在常开态"回执 ⇒ 伪造包与旧客户端同样被挡）。装上 `distill_fast` 插件后蒸馏节拍 5 秒/轮 → **2.5 秒/轮**（★中途装上不改当前轮：倒计时只在装填点读一次位图，下一轮生效）。★★**R96 S8 两处改口**：该型插件已整套改名「魔法使」（token/注册名/贴图基名/lang 全换 `mage`，旧 ID 由影子注册钉住，见代价 45），节拍再收一档 2.5 秒 → **1 秒**（fast 支 `50` → `20` tick，见代价 43）⇒ 本句里的 `distill_fast` 与「2.5 秒」都是 **R95 期读数**，当前口径以代价 43/45 为准。★升级插件**放入对应格即固化、不可取出**（没有"拔掉退回效果"的路，`install` 只置不清）⇒ 手滑放错就是永久，五格各只认自己那一型。★**本轮（R95）实机未验四条**（JVM 用例与机检钉的是位图算式、双轨不变式、行序与文案在场；下面四条全部属实机项，判据见 `plan/_taskpack/in-game-checklist.md` §十八）：① **升级格"放入即固化"的手势观感**（不可取出，误放永久 —— 界面是否给足确认）；② **16G 的读数与容器灌排并存**（真值超 int 顶后 GUI 灌排收 0，玩家能否理解"还装得下但倒不进去"）；③ **多枚口袋（尤其多枚带磁力）的性能**（成本线性，实测帧数未验）；④ **`>64` 的堆在游标上关屏时的归还表现**（超 stackSize 的游标栈如何回背包）。★不得把"构建与测试绿"读成"实机已验证"；R86–R94 的实机项继续全部挂账。★★**R98 需求 3 改判（只改「关掉之后怎么停」这一半）**：上面那句「批边界自动续批、永不停」与「免费、免冷却」**一字未动**，但旧口径「关掉持续化**不主动 `stop()`** ⇒ 让批次自然衰减（最坏 30 秒）」**作废**（台账 §R96 ② 原位标注 + §R98 R98-①）——现在是**关开关那一拍当场停道**：服务端开关写点认 `type + Outcome.TURNED_OFF` 这个**边沿**（`NekoPocketServerHandler.java:424-430`）→ `PocketChannelManager#stopChannel`（`PocketChannelManager.java:99`，`stop()` 与 `forget()` 成对）→ 同拍清 `work` 位镜像；★判据必须是边沿而不是 `isActive` 现值，否则会把"持续化从没开过、玩家刚自付 2 闪烁币开的手动 30 批道"一起打死（状态条目里没有"这条道由持续化撑着"的位）。⇒ 这一拍**一律停**当时在跑的道，**含那条付费开的手动道**，已扣的 2 枚按 R14「部分失败不退」口径**不退**、★也不另发回执（DP-6 定案 + 口袋域零聊天输出 R88①）⇒ 玩家理解"道为什么没了"的可读面只剩**状态行不再显示剩余秒数**与**帧带/光泽转暗**两件事（★这一组翻转是否同拍 = 检查表 §二十二 X-4，本轮离线不可证）。★★**R100 片 C 给本条补三处（通道语义，持续化承诺一字未动）**：① "永不停"的批节拍改为按**频率档位** `{1,2,3,5,10,15,30,60,120,300,600}s`（默认 5s，CHANNEL_PERSIST 面调档、改档当场生效，通道契约行有全文）；② **免开背包自动恢复**——R96 S5 的"有位 ⇒ 有道"此前要求先开一次面板（会话只在面板装配登记），R100 起驱动侧在"persist 位生效 + 会话缺失 + 绑定表非空"时就地复原 headless 会话 ⇒ 重登/重启后不再需要"先开一次面板"这道隐含步骤；③ **开启即首批**——`ensurePersistentShortChannel` 与手动道同口径立即跑首批（due=0，首批计入 30 批预算），不再白等一拍。两条偏差记档见台账 §R100 ②（保守跳过"跳过不消耗"对手动道同样成立；headless 复原缺承载栈只 WARN 不回滚）。

34. **★光泽只有一位，逐型状态读不到图标上（R96 S1 定案、S2 给它落了宿主）**：vanilla 的附魔光泽是 `ItemStack.hasEffect` 那**一个布尔**，物理上装不下"哪几型开着"⇒ 本模的口径是 **光泽 = 有插件开着 ∨ 正在作业**（两个重载同一条并集 `isWorkActive(stack) || PocketUpgradeSwitches.anyActive(stack)`）。★直接后果：**关掉五型里的某一型，只要还剩一型开着或在作业，图标照旧亮** —— 玩家从图上永远分不出型，这不是漏做，是一位存储不下五件事。★逐型状态的唯一读数是本轮新增的**配置面板**（五个插件格左键都开同一面，五行一行一型，三态读数「开着 / 已关闭 / 未安装」——★「没装」与「关着」是两条不同的文案，把没装显示成已关闭等于暗示"还能开回来"）与升级格 tooltip 只在**关着**时追加的那一行；开着与没装都不追加。★明确不提供"每型一枚角标"：那要把图标帧带从 4 档扩到 8 档，S1 已裁定不扩（门禁钉帧带表恰 4 档，扩表属材质轮工作）。⇒ 代价照实：想知道某一型现在是什么状态，**必须打开配置面板**，看图标问不出来。
35. **★次级面板有独立的尺寸天花板（R96 S2）**：配置面板是主面板之上的次级面板，判据为**宽 ≤ 380、高 ≤ 340**（★R96 S2 那一代只有一面、当时实占 348 × 308；★★R98 S4 之后是**五面各自成立**，★R99 整改后再翻（R99 期现值 = CAPACITY 214×80 / STACK 214×76 / CHANNEL_PERSIST 250×76 / MAGNET 236×294 / MAGE 200×154；★R98 期读数 214×98 / 214×94 / 250×94 / 356×312 / 200×172 作废），★★R100 片 D 再翻（文字 0.6→0.8 档全链重算 + 边距 6→8）：现值 = CAPACITY **266×85** / STACK **266×81** / CHANNEL_PERSIST **318×102** / MAGNET **266×299** / MAGE **248×164**，全部逐型自断言 ≤ 上限且严格小于主面板），且两个方向都**严格小于**主面板的 398 × 360。★理由不是观感而是除零：非主面板在 MUI2 里恒可拖，拖动走 `DraggablePanelWrapper:49-50` 的「可视面 − 面板」余量做除算 ⇒ 面板一旦等于可视面就是除零/负数，贴边等于把崩溃留下。★三条连带代价：① 横向只有 348px，挂载列被钉在 174px（★★R98 S4 现值：磁力面横向 **356px**、挂载框 **110×236** 右贴边，x = 356 − 6 − 110 = 240；★**R99 P3 再翻：磁力面收窄至 236px、挂载框 x = 236 − 6 − 110 = 120**，控制块单列化在左；本句的 348 / 174 与前句的 356 / 240 分别是 R96、R98 那两代的预算，史实留在原句不删）⇒ S7 的 12×6 名单与 S9 的元素格要在这一格里排完，排不下就得再开一面，而**再开的那一面同样受本条上限约束**，不会越开越宽；② 主面板那条 `HEIGHT == 360` 的断言一字未动、本件**另立常数** ⇒ 将来给主面板加高不会自动把余量传给次级面板，两边要一起改；③ 英文文案比中文宽得多（最坏串「Channel Persistence Upgrade Module：Not installed」逻辑宽 280，0.6 档要 168px）⇒ 五行只能做成**横贯面板的全宽带**（型名在左、开关在本行最右端），按左列 88px 排必然折第二行顶穿 20px 行盒（★R98 S4 度量统一后行盒 = `ROW_HEIGHT` **18**，同批 `READOUT_HEIGHT` 20→18、`CLOSE_HEIGHT` 16→18、`COLUMN_GAP` 6→4 ⇒ 句里的 20 是 R96 读数）；改任何列宽前先看这条像素账（用例 `config_panel_geometry_within_secondary_caps` 钉它，两份 lang 各算一遍取最宽）。
36. **★堆叠放大后五条手势腿的逐条定性（R96 S4）：本轮★不声称「全手势 1024」**：MUI2 2.3.91 上「往中栏写件数」不是一条路而是**五条互不复用的腿**（取证 `r96-ret7 §4.1 表 B` / `§4.2`），`ignoreMaxStackSize` 只管得到其中两条。逐条处置与**残留的玩家可见差异**：① **点击放入 / 同类合并 / 整堆交换**（`ModularContainer:289/:321/:334 → :379-390 stackLimit → ModularSlot:88`）——★**已打通 1024**，A1 用例直接钉这条执法点；② **Shift 入格**（`:470/:504-505 getItemStackLimit`）——★**已打通**（同一个读点）；③ **取出腿**（空手左/右键从 1024 的格里拿：`:303-304 Math.min(stackSize, getMaxStackSize())`）——★**本轮不修**：一次只能拿起 **64**（不是拿不起，是要点 16 次），不丢件。修它要覆写 `ModularContainer.slotClick`，为手感付一层容器子类不划算，已进检查表实机项；④ **Shift 出格分块**（`:443-445 base = stackSize − maxStackSize`，每趟只搬 `maxStackSize`）——★**本轮不修**：一格 1024 按 **64/趟** 搬回背包（同样不丢件，只是搬 16 趟）；⑤ **拖拽均分（QUICK_CRAFT）**（资格在 vanilla 静态 `Container.func_94527_a:727` 的裸 `getMaxStackSize()`，**没有任何 per-slot 参数能把标志传进去**）——★**用 `canDragInto(false)` 直接禁掉这条手势**（先例同文件 `PocketSlots#upgradeCell`）。⇒ 诚实口径：**放得进 1024、合得成 1024，但取得出 64、Shift 出 64/趟、不能拖着均分**。⑤ 的定性特别说明：它不是"预览画错"而是**这条手势在中栏上不再可用**（别的槽照旧可拖，只有中栏关掉资格）。（另有两处 handler 内部读数 `ItemStackHandler:138 getStackLimit` / `:107 extractItem`：前者本仓早已覆写、★不许动；后者是"一次最多抽 64"的取件手感，与③同源，本轮同样不修。）
37. **★`>100` 的堆只许存在于口袋自己的 handler 内（R96 S4 的两条新风险与一处欠账 → ★S4b 已闭合）**：把每格上限抬到 1024 之后，**离开口袋**这一步本身变成风险面，两条独立理由（取证 `r96-ret7 §2.1 / §3.3 / §3.4`）：① **NEI 的裸数值判据**——`InfiniteStackSizeHandler.isItemInfinite` 写作 `stackSize == -1 || stackSize > 100`，驱动器 `NEIController.updateUnlimitedItems` 由 `ClientHandler` **每客户端 tick** 扫 `InventoryPlayer` 每一格，命中即 `replenishInfiniteStack` ⇒ **把该格件数改写成 111**（NEI 开了 item 动作时：创造 / SP 开作弊 / 服务端授权；普通生存多半不触发，但这条腿的存在不由我们决定）；② **vanilla 的 byte 宽度**——`ItemStack.writeToNBT` 用 `setByte("Count", …)`，`EntityItem` 的掉落物存档同病 ⇒ 1024 掉成实体读回是 **0（件数蒸发）**。口袋自己的 handler 存档早已是 INT 口径（`PocketInventory` 读档那段与 MUI2 `ItemStackHandler.serializeNBT` 同一手法），★**但那条加固不覆盖原版背包与原版实体**。⇒ 裁定：**按天然 `maxStackSize` 拆堆的口径 = 出包那一刻**（每块 ≤ 64，同时躲开 ①的 100 阈与 ②的 byte 宽）。本轮落地的出口：整理溢出腿改走 `NekoPocketServerHandler#giveAwayInNaturalChunks`（★尺子取 `getMaxStackSize()`，★不取 `effectiveStorageLimit` —— 出包后的世界是原版背包）。★**那处欠账已由 R96 S4b 闭合**：`NekoPocketPanel#evictFromSlot` 的余量仍写作 `giveToPlayer(rest)`（S4 把它钉成「恰 1 计数门」的那条判据一字未动，读到 2 仍是"又添一条整堆外运"），但**拆堆已上移到 `giveToPlayer` 这一个漏斗**（三个出口共用：`evictFromSlot` 的余量 / 整理溢出 / 绑定格退回），所以那一行交出去的已经不是整堆。★尺子仍是 `getMaxStackSize()`（★不是 `effectiveStorageLimit`），循环判据读**本体剩余**（`while (stack.stackSize > natural)` + 每轮 `splitStack`）——★这一点是有名字的旧坑：R83 那版拿原版入参的 `stackSize` 当进度，而原版成功那一刻就把它置 0 ⇒ 进度恒 0 ⇒ 服务器主线程死循环（同文件 `moveToPlayer` 的 javadoc 记着）。★"进原版背包 / 掉脚下"的落点因此**各仍只有一处**（门禁门 D 的恰 3 / 恰 2 撑不住，循环里套投口立刻红）。★如实记一条未塌回的冗余：S4 的 `giveAwayInNaturalChunks` 照旧存在并先拆一遍，交给漏斗的每一块本来就 ≤ 天然满量 ⇒ 漏斗那一条循环对它恒不触发；没把它塌回是因为 A4 那条用例把「整理溢出恰走 `giveAwayInNaturalChunks` 一次」钉着（S4b 不得删改既有断言）。新用例 `pocket_exit_funnel_splits_at_natural_limit`（★只证在场：Panel 需要真 `EntityPlayer`，理由写在用例首段）。门禁侧同步加门 H3（漏斗体内四条读数）+ 门 E 改口径。门禁侧同步加两条：口袋目录内 `dropPlayerItemWithRandomChoice` 恒 0、`addItemStackToInventory` / `entityDropItem` 落点数恰 3 / 恰 2 —— ★新写一条"整堆外运"立刻红。
38. **★本轮（R96 S4）实机未验**：用例与机检钉的是**执法点被问到**（A1/A2 真调 `ModularContainer.stackLimit`）、装配标志在场、拖拽资格已关、平尺的两族读数、三条源码接线与出包落点穷举；★下面这些**全部属实机项**（判据见 `plan/_taskpack/in-game-checklist.md` §十九）：① 升级档往中栏**手动放/Shift/合并**是否真能叠到 1024（③④两条腿仍给 64，玩家会读到"放得进拿不出"的不对称）；② 中栏**不能拖着均分**的观感（⑤的裁定代价）；③ 两把同款工具被合成一格的**渲染与 tooltip** 是否读成异常（平尺代价）；④ 实装 NEI 且开了 item 动作时，从口袋取出的 64 块是否**没有**被改写成 111；⑤ 一次整理 1024 件大档的耗时与同步包大小。不得把"构建与测试绿"读成"实机已验证"；R86–R95 的实机项继续全部挂账。
39. **★可穿戴之后，被动的宿主从一枚变成两枚（R96 S11 · 需求 8）**：口袋现在能穿进 Baubles 的饰品栏，槽型是 **`UNIVERSAL`**（★刻意不给 `RING`：本仓在册的七枚功能戒指已经占着戒指格，给 `RING` 就是让它们互相挤槽）。直接后果有两条，都不是"多了一种用法"这么轻：
    ① **穿戴态下 vanilla 不再调 `Item.onUpdate`**（`InventoryPlayer.java:341-348` 那一圈只走主背包 36 格），被动改由 Baubles 的 `EventHandlerEntity.playerTick` 驱动 ⇒ 本模把九行被动（两条倒计时 + 通道 + 蒸馏 + 磁力 + 魔法使四条）抽成共用的 `runPassives`，由 `onUpdate` 与 `onWornTick` **双挂**。★这条桥是给未来加的：以后再加一条被动，只挂 `onUpdate` 就会出现"在背包里会动、穿在身上全停"且**不报错**——门禁 `★R96-S11 门 A` 把"调用点恰 2"与"九行逐字在场"钉死，摘掉任一侧立刻红。
    ② **`onWornTick` 由 `LivingUpdateEvent` 驱动、双端都发** ⇒ 体首行那道 `isRemote` 早退不是收尾，而是"客户端不许跑服务端账"的唯一保证（摘掉它就是 `★R96-S11 门 A` 的第一条 FAIL）。
    另外三条口径：**穿在身上时内容仍然只有 1 只口袋的那份账**（饰品格不进 `Container` ⇒ 225 槽守恒一字未动）；**落盘位置变了，而且不是一句「位置差异」能盖住的** —— 穿在身上的那一枚住在 `world/save/playerdata/<uuid>.baub`（★不是 `playerdata/<uuid>.dat`，更不是主背包那一段），★写与读都由 Baubles 自己做（登录时 `PlayerHandler` 建 `InventoryBaubles` 并读回、存档时整体写出），**本仓不注册任何落盘点、也不需要**；但同一枚口袋**放进充能基座**时走的是另一条路：它躺在**方块实体自己的 NBT** 里（区块存档 `region/*.mca` 的 tile 段，`TileWandPedestal` 的 `writeToNBT`/`readFromNBT`），★与 `.baub` 是两个文件、两套读写时机 ⇒ 「从身上摘下来放上基座」是**跨文件的搬运**，任何一侧单独回滚或备份都不会跟着另一边（S10 那枚 mixin 只改了基座「能不能放 / 能不能充」，★没有新增任何落盘动作，基座那侧的落盘仍由 TC 自己负责）。⇒ 备份口径从今天起是**三处**：`playerdata/<uuid>.dat`（躺在背包里的那枚）、`playerdata/<uuid>.baub`（穿在身上那枚）、区块文件（放在基座里那枚）；`relocateCarrier` 与 `carrierStillPresent` 因此各补了一条 bauble 腿，关屏落盘与会话存续都认饰品栏；**符文护盾 +20** 挂在 `IRunicArmor` 上（★R96 S11-fix：该接口与那个读数**不住**口袋常驻类，而由 `src/mixin/java/com/miaokatze/gtit/mixin/thaum/MixinItemNekoDimensionPocket_RunicArmor.java` 注入，施加条件 = 神秘时代在场（`asm/GtitThaumLateMixinLoader` 的条件清单，★不是被 manifest 无条件施加的 `mixins.gtit.json`）⇒ **没装神秘时代的实例上这份加成自然缺席**，口袋的可穿戴、被动推进、抽液、面板全部一字不受影响。反过来，S11 原本把那个接口写进常驻类的 `implements` 位是**阻塞缺陷**：TC 是 `compileOnly`，常驻类的类型层次在 JVM 加载它的那一刻就要解析该接口，`@Optional.Interface` 的擦除与全限定名写法都兜不住层次 ⇒ 未装 TC 的实例上 `NoClassDefFoundError` ⇒ 整个 mod 起不来），而神秘时代算容量只扫**盔甲 0..3 + 饰品 0..3**（`EventHandlerRunic.java:63-73`）——★`UNIVERSAL` 允许口袋落到第 5 格及以后的空位，而那里**TC 一眼都不看** ⇒ 同一枚口袋"戴在哪一格"会决定 +20 到底计不计入，★本仓不承诺这一致（放宽上界是 SalisArcana 那类 mod 的行为，不由我们许诺）。★这一条 + "穿上真的把护盾容量抬到 20" 全部属**实机未验**（计划 §8 V-3），用例与门禁只证挂载点在位、读数唯一、槽型是 `UNIVERSAL`。
40. **★B 键与"从饰品栏打开"是本轮新开的口，代价是三处不可省的形状**：Baubles fork 自带的 `KeyHandler` **默认键码是 0（未绑定）**，而且它开的是饰品架、不是口袋面板 ⇒ "按键开饰品背包"必须本仓自己开一条腿（此前全仓 0 处 keybind 先例）。落地形状抄 MUI2 自己的 `ClientProxy`：`new KeyBinding` + `ClientRegistry.registerKeyBinding` ⇒ **走 vanilla 的 `options/keybindings`，玩家可换绑，本仓不自写配置**（自写就是第二处按键真相）。代价四条：① 唯一那枚 `KeyBinding` 住在 **client-only 包**（`client/PocketBaubleKeybind`），由 `ClientProxy.preInit` 显式 `install()`；`KeyBinding`/`Keyboard`/`ClientRegistry` 进任何常驻类或 `common` 包 = 专用服 `NoClassDefFoundError`，门禁 `门 B` 同时钉"全树恰 1"与"common 包恰 0"；② 1.7.10 的 `InputEvent.KeyInputEvent` **不可取消**（`ReincarnationClientFx:77-80` 实证）⇒ 按键腿只读 `isPressed()` 做事，★不写 `setCanceled`，别把它读成"拦下了别的按键行为"；③ 穿戴态开面板走 MUI2 自带的 `openFromBaublesClient(index)`，而它在 Baubles 缺席时会**直接抛** `IllegalArgumentException`（`PlayerInventoryGuiFactory:55-57`）⇒ 外面必须套 `ModularUI.Mods.BAUBLES.isLoaded()` 那道闸，闸外一个开屏调用都不许有；④ **降级腿**：没穿口袋（或 Baubles 不在）时 B 键回落到"手上这一枚确实是口袋 ⇒ 走既有主手那一路"，★所以 B 键不是死键，但它也不是"任意时刻都能开面板"——手上没口袋、身上也没穿 ⇒ 按下什么都不发生（★刻意不给提示音/聊天，R88 的口袋域聊天零输出口径继续成立）。多枚口袋同时穿着时**只开 `visitAll` 枚举到的第一枚**（★不是"选一枚"的界面，那需要再开一面板）。
41. **★穿戴后的抽液手势 = 潜行 + 空手右击机器；不戴时那条腿一个字没改（R96 S11 · 需求 8 第二件）**。裁定口径（计划 P-10）：**不用 alt 修饰键** ⇒ 潜行与空手在服务端都读得到 ⇒ 这是一条**纯服务端单腿**（`ItemInWorldManager:386` 发 `RIGHT_CLICK_BLOCK`，自带 x/y/z/face/world 与 `getTileEntity`），★因此 reach 与 `isBlockProtected` 自动生效（`NetHandlerPlayServer:588-590` 在它上游）、★不需要新写任何 C2S 包、★不需要客户端轮询。反过来若做成"客户端轮询 + 自发包"，这两道防护会被旁路——门禁 `门 E` 因此钉"全仓 `SimpleNetworkWrapper` 仍恰 1、新腿文件内零网络符号"。四条要付的账：① **两条腿互斥**：新手势的硬门是 `getCurrentEquippedItem() == null`（空手），手持口袋时走的还是既有的 `onItemUseFirst`（潜行 + 手持右击）——那两段的源码文本由 `门 D` 用 hash **逐字**钉住，改一个字符就红；② **空手这道门不是保守，是避撞**：GT 机器对"手持螺丝刀/扳手"的右击有自己的白名单语义（`BlockMachines.java:412-419`），潜行 + 手持工具是配置机器的常规手势 ⇒ 手持任何东西时本腿绝不生效（用例钉这一条）；③ **只在真搬动了流体时才 `setCanceled(true)`**（成交那一刻才需要"不开机器 GUI"），一切"没搬动"的结局都不取消 ⇒ 取消的连带只有服务端回的那一枚 `S23PacketBlockChange`（原版既定行为，无副作用）；④ **穿在饰品栏上的口袋与基座（`TileWandPedestal`）的交互照旧吃 S10 那条钳制**：基座的 `getInventoryStackLimit()` 在 TC 里写死 **1** ⇒ 从身上摘下来放上基座、以及从基座上拿回身上，**任何时刻都只有一只**（不会像在投料口那样堆一摞），而"关掉魔法使只是不再涨"那句仍成立。★本轮（R96 S11）**实机未验**：穿戴后九行被动真的在推进（V-1）、B 键换绑与"从饰品栏开面板"的手感（V-2）、护盾容量 +20 是否真被 TC 读进去（V-3，还含"戴在第 5 格读不到"那一格）、潜行空手右击在多人服上的 reach/保护面板表现（V-4）、`.baub` 里内容跨存档迁移。判据见 `plan/_taskpack/in-game-checklist.md`；★不得把"构建与测试绿"读成"实机已验证"，R86–R95 的实机项继续全部挂账。
42. **★结晶模式 = 对 R88 裁定 C2「晶只读不产」的显式改判，且只改判一半（R96 S9b · 需求 7 / 计划 P-8）**：R88 那条 C2 的原文是「存档里既存的晶仍会被**识别**并溶回盘（1 点/枚，不吃件），但任何路径都不再产出晶」。本轮按用户裁定的「结晶模式放行」把**「不产」那一半收回**，★**「只读」那一半一字有效** —— 既存晶照旧被识别、照旧溶回盘，这两件事不是同一码事，别读成整条翻案。这不是顺手实现，是**记档改判**（台账 §R96 有一条专号，指回 `TaumDistillRules:94-101` / `TaumCompat:328-336` / `TaumBridgeApi.newCrystalStack` 三处 javadoc 的同步改口）。四条边界：① ★**缺省关**（`PocketConstants.MAGE_MODES_DEFAULT` = 猫猫币 + 源质转换**开**、结晶**关**）——前两档开是「不改变 S9a 已落地行为」的唯一取法（默认关会让玩家升级照旧、被动全体停摆 = 静默回归），结晶默认关是用户对本模式的**显式裁定**（它是改变源质去向的功能，开着会悄悄把玩家盘里的源质换成物品）；② ★**读数 + 消耗整件，不就地排空**：`snapshot()` 读存量 → 按 `MAGE_CRYSTAL_MAX_PER_BATCH` 取整批枚数 → `CrystalGate#mint` 出**新晶** → 交付 → 最后才 `extract` 扣「实际交付枚数 × `CRYSTAL_CAPACITY`」，晶自身的 `AspectList` 从头到尾没被改过一次（wiki `gtit-taumcraft-essentia-carriers.md §3` 证死：就地排空会让服务端随机重赋型）；③ **交付失败一分不扣**：背包优先、满则掉脚下（`PocketItemExit#giveOrDrop` 两级兜底）；交付返 `false` 或出晶返 `null`（TC 缺席）⇒ 这一批源质**留在盘里等下一拍**，不吞产物也不凭空扣点；④ ★**子模式位另立一枚 byte**（住在 `elem` 内、谓词面 `PocketMageModes`），**不塞进 `upgradesOff`** —— 并成一位会让「关掉魔法使」与「只关结晶」在档上是同一个数，面板与光泽再也分不开两态；合取点只许住在被闸的那条腿里（三条 driver 与面板提交腿），不折进谓词类。⇒ 本节全部离线证据钉的是挂载行与三段式形状，**「开了结晶之后盘里的源质到底怎么变」属实机项**。
43. **★蒸馏 1 秒 = 产量 ×2.5 无补偿，而且魔法使又顺手加了两条「1 秒」节拍（R96 S8 立、S9a 放大一档）**：单一真值点 `TaumDistillRules#distillIntervalTicks(fast)` —— 基档 `100` tick（5 秒）**不变**，fast 支 `50` → **`20`** tick ⇒ 相对基档 **×5**、相对 R95 那版 2.5 秒**再 ×2.5**，★**没有任何配方、能耗或产物侧的补偿**（嫌快只改那一个符号，其余全是派生式；用例钉 `ticksToSecondsCeil(distillIntervalTicks(true)) == 1` 与基档 `== 5` 两侧）。S9a 的三条被动在同一条「按需求原文、与 TC4 魔力石同形」的口径下又新增两条 1 秒节拍（猫猫币 1 秒 1 枚、源质转换 1 元素/秒）与一条 5 tick 节拍（充法杖 5 tick × 每 tag ≤5 点 ⇒ 六条满仓时 ≈ **60 点/秒**的注水速率），调档符号共四个：`MAGE_SECOND_INTERVAL_TICKS` / `MAGE_WAND_INTERVAL_TICKS` / `MAGE_WAND_MAX_POINTS_PER_BATCH` / `MAGE_TRANSMUTE_POINTS_PER_BATCH`（★driver 侧一律引符号，实测 `src/main` 里 driver 码位零裸 `5`/`20`）。★**同一条纪律连着一条挂账**：改完这些数必须**手跑** `TaumDistillRulesTest`（`java -cp "$(cat build/testcp.txt)" com.miaokatze.gtit.crossmod.taum.TaumDistillRulesTest`）—— 该套件**没有 gradle 在册入口**（`src/test` 12 个可运行类、只有 6 条 `JavaExec`，见台账 §R96 挂账项），不手跑则改了数字**一次都不会被执行**。中途装上不改当前轮那条 R95 口径照旧（倒计时只在装填点读一次位图）。
44. **★磁力「无限制」态保留名单但不生效 —— 三态里的这条不对称是裁定，不是漏做（R96 S7a 立、S7b 给它落了盘）**：切到「无限制」时那 72 格名单**原样在档、但一律放行**；切回白/黑名单条目**立即恢复生效**；想抹掉条目要用**「清空名单」那枚单独的按钮**。⇒ 玩家可见的怪处：**配过名单再切无限制，面板上格子还满着**。连带三条：① **白名单 + 空名单 = 什么都不吸**（显式姿态，与「没配过」可区分，不做"空表当全放"那种反直觉读法）；② 条目只到 `itemId + meta`，★**不区分 NBT**（同一种物品附魔与否同判；带 NBT 尾段的拖入键会被剥掉尾段只留身份 ——「为什么我这把剑也被吸了 / 都不吸了」的答案在这里）；③ 外来或损坏的名单条目 = **丢条目 + 一次性 WARN**（`磁力名单…条目上限 72`），不让整枚口袋读不出来。盘的形状：`MAGNET_FILTER_ROWS = 12` × `MAGNET_FILTER_COLUMNS = 6` = **72**（`PocketConstants:526/:527`，★插入序就是格序）；NEI 与背包的拖入这条口是**只记录不放置**（声明侧动作，不动槽内容）。★成本口径对 S6 旧承诺做一次修正：「位移拍完全不碰 NBT」改为「**只在真扫到掉落物的那一拍**读一次名单、建一个判定集合」，磁力的常态（周围没东西）仍是零 NBT 零分配。吸取目标两档 = 玩家主背包 36 格 / 口袋内 135 格栏（P-11），**满载不吸**、**不吸经验球**、名单不做 NBT 敏感匹配。
45. **★三枚新根键 + `mage` 注册名 = 落档即冻结，且本轮多了一件「永不出现、但不许删」的注册件（R96 S1 / S7a / S9a / S8 四片同一条纪律）**：`upgradesOff`（S1 的 off-mask byte，`PocketConstants:473`）、`magnetFilter`（S7a 的名单 compound，`:498`）、`elem`（S9a 的元素容量 compound，`:553`）三个**根键名一经落档即冻结** —— 这是「可加不可改」那条键纪律的**键名版**：改字面量 = 玩家存档里配好的开关 / 名单 / 容量读回来**变空**，★且零日志。根层的键从 R95 的 `upgrades` 一族扩到本轮的**第四次**扩，第四次起再想要新根键就是另一轮口径。★同族还有一条：`mage` 这个**注册名**自本版本起冻结 —— 改注册名 = 换 Forge ID 表的键 ⇒ 破档，此后再改回来又是**一次新的破档**。影子注册的代价如实记：`ItemRegistrar` 里同一个 `MAGE` 实例以旧注册名 `neko_pocket_upgrade_distill_fast` **第二次**裸 `GameRegistry.registerItem`（不走 `setAndRegister`），作用是占住旧 ID 键、使旧数字 ID 不被释放也不被别的 mod 复用 ⇒ 本模**永久多一件「永不出现」的 Item 实例，而且它不许被删**（删 = 再一次破档）；旧栈读回仍是「这一型插件」（槽位识别只看实例的 `type` 字段，与注册名无关），显示名与贴图都已是魔法使。★别名是否真能钉住旧 ID **未经实机证**（Forge 1.7.10 的 `IdMapping` / `idworld` 本地无源码），属 §8 V-4。★另登记一处**刻意的名实不对称**：GUI 灰化图案 token `POCKET_C2_upg_distill`（与 `PocketGuiTextures.UPGRADE_DISTILL`）以及行为层的 `PocketDistillDriver` / `TaumDistillRules` / lang 键 `gtit.pocket.tooltip.distill_fast` **全部保持原名，只换像素** —— 它们锚在 `plan/assest/pocket-ui-mockups-2.html` 与 `pocket_gui/contract.py` 两张契约表上，说的是「做什么」而不是「是谁」⇒ `mage` 型读 `distill_fast` 行为键是**登记过的分家**，不是漏改。★**R98 S2 补一句同一条纪律的下半场**：lang 键 `gtit.pocket.tooltip.distill_fast` 的**名字仍冻结**（用例硬钉「不许改名」），本轮只**改值**——它同时接手被撤的旧 `item.neko_dimension_pocket.tooltip.5`，把"基档 5 秒 / 魔法使档 1 秒"两档口径并成一行（正好吃掉此前备而未用的 `%12$d` ⇒ ★零扩槽），换算单源仍是 `TaumDistillRules#distillIntervalTicks`。★★**R100 补入冻结清单两件（同一条「可加不可改」纪律）**：① **根键 `channelFreqTier`**（片 C 频率档位的 NBT 键，`PocketConstants` javadoc 原位明写"一经落档即冻结"⇒ 改名 = 玩家调好的档位读回变默认 5s 且零日志；**无键 = 默认档**，不是缺档错误）；② **片 A/B 新 lang 键族** `gtit.tooltip.added_by` 与 `gtit.pocket.upgrade.mage.line.0..9`——名字自落档起冻结（连号族消费端是 equals-break 循环，改名/跳号 = 行静默消失，与 tooltip 主族同一条机检形状）。
46. **★元素容量刻意与流体不对称：不建双轨、是活档视图、并且有一条每 tick 的读侧成本（R96 S9a）**：6 种 primal（aer/terra/ignis/aqua/ordo/perditio，判据 `Aspect.isPrimal()` 叠加白名单）各 **500**、合计 3000（`ELEMENT_TOTAL_CAP` 是派生式）。四条代价：① ★**不建 long/int 双轨**（与流体那条 `AmountL` 是**有理由的不对称**）—— 单 tag 500 时 int 头恒不越界，流体建双轨的根因（`FluidStack.amount` 是 int 而真值要冲 16G）在这里结构性不存在，加第二轨只会多出一个「可以对不齐」的地方；★将来若把 `ELEMENT_CAP_PER_TAG` 抬到 2³¹ 量级，**必须先把载体改掉再抬上限**，反过来就是静默截断；② ★**活档视图不是快照**：`PocketElementStore` 只持载体根引用，`get` 时夹负数与越 500 的脏档、`extract` 扣到 0 即摘键、整族空则摘掉 `elem` 键 ⇒「面板关屏的整表回写把被动刚入账的容量抹回旧值」这一族**结构性不可表示**（币已扣、容量回退 = 净吞玩家件），代价是**每次读都是一次 NBT 取值**（纳秒级零分配，但面板若逐帧刷六行就是 6×6 次/帧）；③ **两条同轮被动故意不同口径**：猫猫币是**整笔预检**（要么六条各涨、要么一分不吃），源质转换是**逐 tag 原子**（ignis 到顶只冻结 ignis 那一条，其余五种继续折 —— 绑成整笔会直接违反「6 种可同时」那句需求）⇒ 玩家侧读法：**「币没被吃」不代表口袋满了，「某种源质不折了」只代表那一条到顶**；④ ★**无会话常态下猫猫币腿每 tick 做一次整表装配**（实测下限 7 µs/次 ≈ **150 µs/秒/只口袋**，内容非空时更高）—— 写侧零 NBT 写，是**读侧**成本与 `R53c` 纪律的偏离，已如实登记不粉饰。另记一条时点：元素容量在 **S9a 落地期没有任何可见读数**（不进 tooltip 也不进面板），是 S9b 才接上的四模式控件。★★**R98 S2 把这条时点闭掉**（本条那句「没有可见读数」只描述 S9a 落地期，★不得再当现状读）：元素**储量**第一次进**物品 tooltip** —— 新增键 `gtit.pocket.tooltip.element`（zh `:481` / en `:494`，值 `%1$s§r x %2$d`），6 个 primal 按 `PRIMAL_TAGS` 白名单序逐行一物、★**非零才出**（新口袋一行都不出 ⇒ 越用越长；★**R99 P4-D 原位作废**：用户实机发现全 0 时六行一行不出、无法判读吸收是否工作 ⇒ 改判**恒六行、空位显 0**，见代价 56 与 60）、★**不带魔力石那层 `/100` 与小数格式化**（那是 TC 的 vis ×100 存档刻度，本仓 `elem` 存的直接是点数 ⇒ 抄过来会把 500 显示成 5，是量纲错不是简化，取证 `01b` §3.7）；同时配置面板 MAGE 面那行元素**容量**读数从框外搬进框内（代价 58）。⇒ 现状的读数面 = 物品 tooltip（储量六行）+ 魔法使面（容量那一行），另加磁力/魔法使被动本身照旧。
47. **★口袋接神秘时代 = 六枚 mixin 走条件注册，TC 不在场时三条腿一条都不施加、也不报错（R96 S10 / S10c / S11-fix · 需求 7）**。清单住在代码不在 json：`GtitThaumLateMixinLoader.THAUM_MIXINS` 六条，施加判据 = 已加载 mod 里有 `Thaumcraft`（首字母大写）；`mixins.gtit.thaum.json` **只声明 package**（类清单一条不写）。★这六枚**绝不进**被 jar manifest 无条件施加的 `mixins.gtit.json` —— 第六枚（把 `IRunicArmor` **注入**常驻口袋类）一旦进了无条件清单，等于「TC 缺席时也注入那个接口」⇒ 常驻类在类加载期解析不出该接口 ⇒ 整个 mod 起不来（★正是 S11 原本那个阻塞缺陷原地复发，只是成因从我们的 `implements` 换成注入器；那半条口径见代价 39）。开出来的三条腿与各自的价：① **充能基座：放得进，但一次只放一只** —— TC 的 `getInventoryStackLimit()` 写死 **1**（`TileWandPedestal.java:157`），管道侧照抄「目标槽必须是空的」原式 ⇒ hopper 也塞不进第二只；**只认元始节点**，复合要素一律不抽（折算要基座上方摆 meta 8 的石设备，本轮不接）；★**客户端不亮那道抽能光束**（要亮得在客户端再复刻一遍节点扫描）；节点空了最长 100 tick 才重扫；关掉「魔法使」只是不再涨，已在基座里的口袋仍拿得出来；② **要素球：只认快捷栏 9 格、只吸元始球** —— 扫描面刻意与 TC 原语一致，★**不放宽到 36 格**，非原始要素球照旧永不吸收；③ **符文护盾：口袋当「油箱」，不当「油箱容量」** —— 回充可在魔力石/法杖**整笔付不起**时改掏口袋元素，默认一拍 = **Air 1 点 + Earth 1 点**（TC 的 `runic_cost = 50` 是存储刻度，÷100 向上取整），顺序硬保证「先法杖后口袋」，原路付得起时口袋**一分不动**；★刻意没做的部分要点名：这一枚只钉在护盾回充那一条指令上 ⇒ **法杖自动修复、以及别的 mod 经 `ThaumcraftApiHelper.consumeVisFromInventory` 抽能时都不会掏你的口袋**（要让修复也吃口袋是另一个决策，不在本被动里顺带开出）。④ **缺席降级只有一行 INFO**（`[gtit] LateMixin 引导命中：tcPresent=false，施加 无`）——★目标类缺失在 1.7.10 的 unimixins 栈上只写 WARN 不崩（本仓归档冒烟日志两例实证，见台账 §R96 证伪节），所以「TC 在但判据写错」时同样**没有任何报错**；★★**R99 P5 两档改口（旧句「是否真 apply 全部未离线证成」过粗）**：**施加已证**——要素球那枚的 transform 级施加有归档冒烟日志硬证（`plan/smoketest/runs/20260928-014453-r97s1-tcpresent-round1/fml-server-latest.log:3598` `Mixing … into EntityAspectOrb` + `:3604` handler 解析成功，Mixin 自己打印的施加记录）；**执行未证**——六个归档里 `[gtit] TC mixin 元素入账命中`（`PocketVisSupport.java:183`）命中数 = 0（无头服没有玩家也没有球）⇒「真实碰撞 → 写 `elem`」既无正证也无负证，只能实机判读（检查表 §二十三 Y-4，依赖储量恒六行才可读数）；与 Hodgepodge / SalisArcana / Backhand 抢同一批注入点时的实际共存顺序**仍未证**（§20.1 V-6 / V-7）。
48. **★本轮（R96）实机未验 = 计划 §8 那十五项，收束态只记「交付待实机验收」**：本轮全部离线证据钉的是判据形状、**执法点是否被问到**、注册是否真挂了、文案与 lang 是否对得上；★**V-1..V-15 十五项逐条落 `plan/_taskpack/in-game-checklist.md` §二十，本轮状态一律「未做」**（两处例外是**离线项**、不属实机：V-13 冒烟工装的 `resolutionStrategy.force` 自查 = 全枚举无命中；V-15 的静态半边 = `src/mixin` 源集编译期继承 TC 的 `compileOnly`，随 S10 编过落地）。三条最容易被「构建绿」掩盖的：① **V-1** `EntityItem.writeToNBT:333` 的 `setByte("Count")` 未补 ⇒ 一格 1024 掉成实体再读回**可能是 0（件数蒸发）**，本轮的防线是「出包一律按天然满量拆块」（代价 37），★防线本身在真机上跑过没有 = 未验；② **V-5** 1024 的**物理可行性**全押 MUI1 的 `PacketBufferMixin` 是否真 apply（四条静态制品证据 ≠ 真 apply）；③ **V-11** NEI 的 `updateUnlimitedItems` 每客户端 tick 扫 `InventoryPlayer`、`isItemInfinite` = `stackSize == -1 或 > 100` ⇒ 开了 item 动作时是否把 1024 改写成 **111**。其余十二项（服务端改 NBT 是否 ≤1 tick 推包、bauble 槽位 4 vs 20 与 +20 是否真计入、改名旧档表现、基座三枚 TAIL 共存顺序、Backhand 是否绕过球、GUI 内左键点格手感、新图标 mipmap 下糊不糊、磁力悬停拍数、穿戴抽液的 reach/保护面板与多方块一致性、2G 单槽在第三方容器里的 int 假设与 `fluidsUsable` 真驱动守恒）**全部挂账**。★不得把「构建与测试绿」读成「实机已验证」；R86–R95 的实机项继续全部挂账。

49. **★mixin 声明包里禁放普通类——吸取要素球崩溃的正身是「defined mixin package」地雷（R97-①）**：R96 S10 落的 `PocketVisSupport` 是个普通静态辅助类，却住在两份 mixin json 都声明的 `com.miaokatze.gtit.mixin` 包树里 ⇒ Mixin 类加载体系对该包树特殊对待，注入进 TC 目标类的 handler 一引用它就 `IllegalClassLoadError`。★**六枚 TC mixin 全部引用它**（球 / 基座充能 / 基座抽能 / 护盾 / 快捷栏），用户先撞见球腿，其余几条同样会炸——本轮修的是整族地雷，不是球那一枚。修法 = 移到 `crossmod/taum`（类与 9 个被 mixin 调的静态方法升 public，5 枚 mixin 各补 1 行 import）；★取舍照抄 GTOOS 同款判例（duck-typing-interface.md）：类**只被条件施加的 mixin handler 引用、常驻面零静态引用** ⇒ 放 src/main 的 crossmod 是安全的（与 `TaumBridge` 同一张安全论证），门 F-1 豁免面同步扩一枚 + **新门 P** 钉死「src/main 常驻类对它的 import 恰 0 / 旧路径消失 / mixin 侧 import 恰 5」；新回归断言 `mixin_package_tree_has_no_plain_classes` 把「mixin 包树内每个 .java 必含 @Mixin」钉成永久判据。备选形状（留在 src/mixin 源集、挪去非声明包 `mixinsupport.thaum`）**未启用**——三份 mixin json / refmap / build.gradle.kts 零改动是本轮验收面之一。★类加载是运行期行为，离线证据只证结构：**运行时证明归无头冒烟**（S6.5，结果落台账/检查表）。

50. **★通道「对数」的公平语义改判：无源可投的通道不再占对（R97-③）**：`pocketChannelPairsPerSecond=1` 时流体/源质持续化通道饿死的根因是**注入相把 pair 花在没有可投来源的通道上**。本轮语义二选一：**采 A「有机会投就占对」**（该通道存在兼容来源 ⇒ 消耗 pair），**拒绝 B「真搬动了才计」**——B 会把「对数 = 服务机会」这条契约改得更深，且满目标重试会退化成**活锁**（每一对都"没搬动"、永远轮不到别的通道）。配套：**注入相轮转**（每批通道起始序号 +1，与补满相游标同构独立推进），游标**只在内存延续、不进 NBT**——重启丢相位只影响公平起始位（无害），换来的是不动 NBT 格式；未知第三方通道按「有任意源即视为可投」的**保守规则**消耗 pair（宁可占对、不给未知通道开后门）。★ThE（ThaumicEnergistics）缺席时源质席位拒收面仍呈 `no_channel`（口径不变）；BURST 与补满相零改；默认 pairLimit 仍 1（**不采用**调大配置的"零代码缓解"）。玩家可读语义已写进通道契约行与回执文案。★**R98 加一句边界**：即刻停道（代价 33 与通道契约行那条）**不碰这条公平账**——注入相轮转游标 `state.rotation()` 由既有 `stop()` 原语**保留**（★"序号不因激活重置"是设计），且 R97 S2 起它只在内存、不落 NBT ⇒ 中途停道**最多丢掉一次轮转起始位**（当批没轮到的那几对本来就没搬），★不丢件、不重复，也不新增任何计时真相。

51. **★幽灵物品六连修 + 一条明示的「不消耗」（R97-④；R7–R12 挂账不修）**：六条修的是——R1 幽灵声明位表并入 `replaceFilters`（客户端预测与服务端执法读同一份真值，blob 换实例不再抹 attr）；R2 `detectAndSendChanges` 游标差分推送（存**副本**比对防活引用、首拍播种防回声 ⇒ 一处覆盖全部 vanilla 点击路径）；R3 清 intent 挪到真放置判定之后（MUI2 的探测写不再吃掉「放置即配置」）；R4 幽灵 blob 预算 **24000 → 32000**（MUI2 硬顶 32693 留余量；tail-stop 截断与 `not_synced` 读数语义逐字不动——抬预算收的是**现实规模**，病理规模仍走截断）；R5 服务端拒收记账 + 被点槽强推（反向幽灵窗口压到 ≤1 tick）；R6 堆叠升级状态改走同步位图镜像（客户端不再依赖装配期快照 ⇒ **会话中装上堆叠插件，当场面板就认**）。磁力那颗盘的手势 tooltip **就地加一句「不会消耗手上的物品」**——磁力 phantom 语义是**裁定不是缺陷**，改文案不改行为。**R7–R12 六项挂账只记录不修**（含 R12 活引用边角，R2 已用副本比对规避其危害）。门禁钉值随轮就地翻新（`setCursorItem` 代码位 4 → 5：新的第 5 写口 = Container 游标差分）。

52. **★附属 mod 源质图标改走桥——「编译依赖问题」这个猜测被证据证伪（R97-⑤）**：用户报的表象是"附属 mod 的源质在源质槽显示材质错误、物品槽正常"，猜测是附属的编译依赖问题；**实测不是**——根因是 `TaumDistillRules.aspectTexturePath` 把图标路径**硬编码**成 `thaumcraft:textures/aspects/<tag小写>.png`，而附属注册的 aspect 自带**自己资源域**的 ResourceLocation ⇒ 在 thaumcraft 域下根本没有那张贴图（物品槽走 item 渲染不经这条路，所以一直正常）。修法 = `TaumBridgeApi.imageLocationOf(tag)` 经桥读活 `Aspect.getImage()`，**三重回落**（桥缺席 / `getImage()` null / Throwable → 硬编码 `thaumcraft:` 串）——TC 原生 aspect 的真路径恰与旧公式重合 ⇒ 回落无损；回落单源（`aspectTexturePath` 公式）一字不动，消费点唯一（72 格源质幽灵件）。★挂附属后的实机比对归检查表（离线只证三分支：无桥回落 / 桩桥自定义域 / 桥 null）。

53. **★五型升级插件五面配置面板——对 R96 P-3 的显式翻案（R97-②，用户 2026-09-27 明令）**：R96 拍板过"五个插件格左键一律打开同一面、不做五套面板"，本轮用户明令"五种插件应有不同 UI 且排版需调整" ⇒ **翻案以新号落地 + 旧号原位作废**（台账双落）。现在的形状：`build(ui, type)` 参数化 + 宿主侧 5 数组按格分派（签名与调用点零改），五面互异——CAPACITY **214×98**（开关 + 容量读数）/ STACK **214×118**（开关 + 单格上限读数）/ CHANNEL_PERSIST **250×140**（开关 + 常开说明 + 按钮禁用提示）/ MAGNET **358×314**（72 格名单盘 + 三态/目标/清空/计数，右列挂载框升格为整面主体）/ MAGE **200×176**（三行模式控件 + 元素容量读数）。★★**R98 S4 尺寸翻新（本行那四个非 98 的数字到此为止是 R97 期读数）**：现值 = CAPACITY **214×98**（★唯一高度 0 变化的一面）/ STACK **214×94**（−24）/ CHANNEL_PERSIST **250×94**（−46）/ MAGNET **356×312**（−2 / −1，只统一度量）/ MAGE **200×172**（−4，容量行改进框内）⇒ 详见代价 58 与 §R98。★★**R99 再翻（本行 R98 那组数字到此为止）**：底带并带（回执行 + 关闭钮并成一条，五面各 −18）+ 磁力面 M-1 重排（控制块单列化、面收窄）⇒ R99 期现值 = CAPACITY **214×80** / STACK **214×76** / CHANNEL_PERSIST **250×76** / MAGNET **236×294** / MAGE **200×154**，五面第一 child 另接**主面板同源装饰底**（cloth + 木框 + 4 包角，见代价 60）⇒ 详见 §R99。★★★**R100 片 D 再翻（本行 R99 那组数字到此为止）**：面板文字 0.6 → 0.8 档（面板局部 `TEXT_SCALE`，对齐主面板 STATUS_TEXT_SCALE 先例）+ 边距 6→8 + 小钮 30/38→46 + 磁力说明行迁 tooltip、左列四段等距收编空带 + 魔法使框宽对齐行带 ⇒ 现值 = CAPACITY **266×85** / STACK **266×81** / CHANNEL_PERSIST **318×102** / MAGNET **266×299** / MAGE **248×164**（lang 键数不动，425）。**两条没变的硬约束**：次级面板上限 380×340 与「严格小于主面板」断言保留；72 格盘仍只属于磁力型。lang **413 → 418**（+6 读数/说明键、−1 撤 `mount.pending` 占位；★**R98 再翻：418 → 413 → 410**，撤的是 tooltip 说明书 6 行与配置面板静态说明 3 条、加的是元素储量键 1 条 ⇒ 现值与算式见代价 55 / 57 与台账 §R98 ③）；★本条那句「CHANNEL_PERSIST 面上有"常开说明 + 按钮禁用提示"两行」自 R98 S4 起**不再成立**（那两行连键一起撤，见代价 57）；★光泽只有一位那条口径**不受翻案影响**——逐型状态读数从"共用一面"变成"各开各面"，读法反而更直接。代码整片可回退（翻案记账保留在台账，史实不随代码消失）。

54. **★本轮（R97）实机未验 = 检查表 §二十一，收束态只记「交付待实机验收」**：本轮全部离线证据钉的是判据形状、同步路径接线、面板几何 static 断言与文案在场；★**无头冒烟（S6.5）进行中、结果待回填**（判据三条：`tcPresent=true` 6 枚施加 / 全 log 无 `IllegalClassLoadError`·`NoClassDefFoundError.*PocketVisSupport`·`defined mixin package` / 起服到 DONE——这是 S1 崩溃修复的**运行时正身**，离线替代不了）。实机面：①腿三交互（球吸收 / 基座充能抽能 / 护盾回充掏口袋）、五型面板逐型开屏排版、流体上传（配额 1）、L 锁定放入游标扣减 / 拒收无残留、附属 aspect 图标比对、磁力 tooltip、堆叠升级会话中装上后的预测档——**全部「未做」**（判据见 `plan/_taskpack/in-game-checklist.md` §二十一）。★不得把「构建与测试绿」读成「实机已验证」；R86–R96 的实机项继续全部挂账。

55. **★物品 tooltip 激进裁剪（11 行 → 恒定 5 行 → 恒定 4 行）与整体重编号的代价（R98-①/③，需求 1；R100 片 E 收紧）**：用户裁定「大幅度简化 Tooltip」⇒ 连号族 `item.neko_dimension_pocket.tooltip.0..9`（10 条）+ 追加的蒸馏行共 **11 行**收成 **恒定 5 行**（`0..3` 四条 + 一条合并蒸馏行），另加 **0–6 行**元素储量浮动（⇒ 悬停总行数 5–11，含名字行 6–12；★**R99 P4-D 后储量恒 6 行** ⇒ 悬停总行数恒 **11**，"浮动"只剩数值、不再指行数，见代价 60）。★**四行说明书删除**、★**一行合并**、★**一行让位**：旧 `.1` 几何说明书（全 tooltip 最长行，新家 = 面板本身数得见）、旧 `.2` 绑定流程三步（新家 = 面板说明块 `gtit.pocket.note.summary`，★它此前逐字近重复这句话）、旧 `.3` 元件须有电前提（新家 = 同上 + 运行期读数 `gtit.pocket.bind.unlocated`）、旧 `.4` NEI 拖拽虚化（新家 = 面板侧 `gtit.pocket.ghost.*` 一族，实测 6 条键，比那一句精确得多）；旧 `.5` 蒸馏节拍**并入**已有追加行 `gtit.pocket.tooltip.distill_fast`（键名冻结、只改值，正好吃掉此前备而未用的 `%12$d` ⇒ ★零扩槽）；旧 `.9` 每槽/合计容量**让位**（见代价 56 与台账 §R98 R98-③）。留下的三条声明位一个没动，只是整体换号：新 `.1` = 旧 `.6`（R39a 两排同权）、新 `.2` = 旧 `.7`（R53b 内容随物品丢）、新 `.3` = 旧 `.8`（合成吃一枚无限单元）。★★**为什么是"整体重编号"而不是"删几条留几条"**：消费端是 `equals(key)` 即 break 的循环（先例 `NekoCoin.java:28-34`）⇒ **跳号 = 后面所有行静默消失**，一行都不报错。这条风险现在两侧都有机检：shell 门 `[R98-5]`（①恰 4 条 ②首号 0 ③末号 3 ④无重号 ⑤相邻差恒 1 ⑥两份 lang 键集 `comm -3` 空，★配四条阳性对照 ⇒ 不是只打印给人眼）+ JVM 用例 `pocket_tooltip_family_is_contiguous_and_four`（★R100 片 E 后两者均已同批翻新为"恰 3 条 / 末号 2"与 `_and_three`）。★连带两条欠账如实登记：① `tooltipArgs()` 那 12 项里 `%1,%2,%3,%4,%7,%8,%10,%11` **八项备而未用**（lang 停止引用），`%5$d`/`%9$d` 也不再被念★但必须留在数组里（它们的读点 `PocketUpgradeSwitches.isActive(carrier, CAPACITY)` 被用例硬钉，且面板那句容量随时可能把这两个数念回来）；将来新增数字一律从 `%13$d` 起槽并同步扩数组 ⇒ **越界 = `MissingFormatArgumentException` = 悬停当场崩**，这是本条最硬的技术风险；② tooltip 的行数**随存档内容浮动**（储量非零才出）⇒ 悬停框不再等高，真实折行与撑宽（1.7.10 的 `drawHoveringText` 不折行只撑宽）属实机项（检查表 §二十二 X-1）。★★★**R100 片 E 再翻（本行 R98/R99 那组行数与号段口径到此为止）**：描述族 4 → **3**——删的就是 R39a 那行"两排同权"声明（R98 期 `.1`、更早 `.6`），★**显式翻案**：用户裁定描述要"简洁明了不废话"，该行防的误读在面板逐列同形的现状下已无落点；翻案按仓内纪律「原位注释 + 台账双落」执行——注释落在 `ItemNekoDimensionPocket` 的 addInformation javadoc 原声明位，台账由片 G 记档。剩余三行整体重排成 `0..2` 且文案口语化压短（`.0` 右键或按 B 键打开面板 / `.1` = R53b 内容随物品丢（旧 `.2`）/ `.2` = 合成一次性代价（旧 `.3`），每行 ≤28 中文字符）；蒸馏行同步删冗词（键名 `distill_fast` 冻结、`%6$d`/`%12$d` 注入形态与"产出按原量"要点不动）⇒ **恒定 4 行**（3 + 蒸馏），连号+蒸馏+储量恒 **10 行**，再加片 A 品牌尾行 ⇒ addInformation 总产出恒 **11 行**（R99 期"恒 11 行"是不含尾行的 4+1+6 口径，检查表 X-1 的行数判读挂账由片 G 翻新）；lang 键数两份各 425 → **424**；测试侧同批：用例改名 `pocket_tooltip_family_is_contiguous_and_three`（恰 3 条 {0,1,2}、恒 4 行、新内容钉）、新增 `pocket_tooltip_r39a_row_removed_with_reversal_note`（R39a 行已删 + 翻案注释在位，只增不删）、shell 门 `TT_MAX` 3 → 2、`LANG_KEY_PIN` 425 → 424；`%13$d` 起槽纪律不变（本批零新增数字注入，12 槽数组与备而未用清单原样）。

56. **★元素储量六行 = 第一个"储量"玩家可见面，色表是有意留的第二份真相（R98 需求 1 的后半 / DP-1、DP-2）**：新键 `gtit.pocket.tooltip.element`（zh `:481` / en `:494`，值 `%1$s§r x %2$d`）承担 **6 个 primal**（aer/terra/ignis/aqua/ordo/perditio）的逐行储量，形状照 TC4 魔力石 `ItemAmuletVis.java:127-137`，接线 `ItemNekoDimensionPocket.java:703`、生产入口 `:738-747`、排版腿 `:775-788`。★四条口径与它们各自的代价：① **行序 = `PocketConstants.PRIMAL_TAGS` 白名单序**（与 TC 的 `LinkedHashMap` 插入序逐字一致），★不由数量、色或玩家操作决定 ⇒ TC 缺席照样出这六行；② **非零才出**（取数走 `PocketElementStore#snapshot()`，它本身就 `amount > 0` 才入表 ⇒ ★绝不为了"显示 0"去建档，R53c 那条读路径纪律一字未动；★★**R99 P4-D 原位作废**：全 0 时六行一行不出让「真 0」与「坏了/没同步」在画面上长得一样——用户实机正是被这个挡住（「我也不知道实际有没有」）⇒ 改判**恒六行、空位显 0**，排版腿改走逐 tag 的 `PocketElementStore#get`（缺键即 0，读侧**仍零建档**），`snapshot()` 保留给它在数据主人那侧的其余消费点）；③ ★**不带 `/100`、不带小数**：魔力石那层 `/ 100.0F` + `DecimalFormat` 是 TC 的 **vis ×100 存档刻度**（`addVis` 写入时 `amount * 100`），本仓 `elem` 存的**就是点数**（单 tag 上限 500，见代价 46）⇒ 照抄会把"500"显示成"5"，是**量纲错**不是简化；④ ★**§色是本模自己的一份 6 项表** `PocketConstants.PRIMAL_CHAT_CODES`（与 `PRIMAL_TAGS` 同序同长、零 TC 依赖）——**这是一处有意保留的第二份真相**：TC 侧的真值在 `Aspect.getChatcolor()`，而桥接面 `TaumBridgeApi` 的 15 个方法里没有它，"新加一个桥方法"那一路在 TC 不在场时**仍然**要有本地兜底 ⇒ 实际还是本地表 + 三处改动，故 DP-1 选本地档。**代价 = TC 若改配色，本表不跟**（只影响观感、不涉规格数字 ⇒ R75 的"数字单源"纪律没被触碰）。另两条形状事实：行首那一个半角空格与 `§r` 复位★必须由 Java 侧加（`java.util.Properties` 会吃掉 lang 值的行首空白，写进 lang 等于没写）；这一行用**独立的两个实参**（`%1$s` 名字、`%2$d` 数量），★不占 `tooltipArgs()` 那个恒定规格读数族 ⇒ 混用会同时造出第二份真相与越界风险。TC 缺席时回落的是**名字**（`TaumCompat.nameOf(tag)` 返 tag 本身），逻辑腿离线已钉（用例前提段实测 `!isThaumcraftLoaded()`），★真实字体下的色可读性属实机项（检查表 §二十二 X-5）。

57. **★关持续化 ⇒ 当场停道的改判，连带一处玩家可见的信息面收缩（R98-① + DP-6 + DP-7）**：本条把两件事钉在一起，因为它们合起来才是玩家读到的那一版。**（一）改判**：R96 S1 那句「★刻意不主动 `stop()`：那等于替玩家发明第二条状态机，自然衰减已足够」**自 R98 起作废**（台账 §R96 ② 原位标注 + §R98 R98-①；史实留在四处注释原处不删）。新判 = 关掉「通道持续化」的**那一个边沿**由服务端开关写点当场停道（`NekoPocketServerHandler.java:424-430` → `PocketChannelManager#stopChannel` `:99`，`stop()` + `forget()` 成对）并同拍清 `work` 位镜像 ⇒ **work 那一腿同拍清零** ⇒ 状态行不再显示剩余秒数、帧带与光泽转暗。★**但"同一拍转暗"的射程只有这一条腿，不得读成"整块画面必然熄灭"**：`isWorkActive` 是两条腿的合取，另有 `UI_BURST_SHOW_TICKS` 那一腿（≤5 秒，代表一次<b>已经发生完</b>的瞬时通道、与持续化无关，而边沿腿★不碰它 —— `startWorkTicks(…,-1)` 只走 `removeTag(UI_WORK_TICKS)` ⇒ burst 显示窗内画面照旧会亮到它自己归零，那**不是**停道失效）；而光泽读的是 `isWorkActive() || anyActive(…)` ⇒ **任一其它升级插件开着本就该亮**，同样不在本判射程。⇒ 三条腿在玩家眼里是否<b>视觉上同拍</b>翻转属实机项（检查表 §二十二 X-4，本轮离线不可证）。★"不建第二台状态机"那一半**被加强而不是被推翻**：用的就是既有 `PocketChannelState#stop()` 原语与既有 `forget` 的成对形状，零新增状态、零新增计时真相；`isActive` 组合谓词与门禁读数（`hasUpgrade(` 直调总点 8→2）一字未动。**（二）取舍**：判据是边沿、不是 `isActive` 现值 ⇒ 现值判据会连带打死"持续化从没开过、玩家刚自付 2 闪烁币开的手动 30 批道"（`PocketChannelState` 里没有"这条道由持续化撑着"的位，无从区分），所以这一拍**一律停**，★含那条付费开的手动道；已扣的 2 枚按 **R14「部分失败不退」**口径**不退**，且 DP-6 定案**不新增玩家回执**（口袋域 `addChatMessage` 实站点为 0，R88①）。**（三）★信息面收缩（这才是本条真正的代价）**：为了让 STACK / CHANNEL_PERSIST 两面"小巧"，撤了三条静态说明键 `gtit.pocket.config.stack.note`（中栏物品格与源质格同用这把尺）/ `config.persist.button`（常开生效期间通道按钮被禁用）/ `config.persist.frame`（口袋图标保持"工作中"常亮），⇒ 两份 lang 的 `gtit.pocket.config.*` 族实测 **17 条**、键数 **413 → 410**。★**这三条键说的两件事在 R98 之后仍然成立**（按钮双端禁用、帧带常亮）——**撤键 ≠ 事实作废**，变的是承载面：它们从配置面板消失，而唯一常驻的那句 `gtit.pocket.config.switch.hint` 只写「左键切换这一型的开与关；关掉不会退回插件」，★**并不复述**这两件事 ⇒ 玩家只剩"看现场"一条路（按钮真点不动、图标真常亮）。叠加（二）里"边沿停道故意不给回执"，★"这条道为什么没了"的可读面只剩**状态行不再显示剩余秒数**与**帧带转暗**两件事。★这是用户明确接受的取舍（"去啰嗦"优先），登记在此而不是留给实机发现。

58. **★五块插件面板 mini 之后「不得同一张脸」的判据改判，与"不是五面都变小"的如实读数（R98-② + R98-⑥，需求 2）**：**（一）度量统一**（用户"风格对齐猫猫次元口袋"）：`ROW_HEIGHT` 20→**18**、`READOUT_HEIGHT` 20→**18**、`CLOSE_HEIGHT` 16→**18**（主面板的按钮全是 18 高，同一张 `BUTTON` 9-slice 被拉成 16 与 18 两种高就是"不像同一套 UI"的直接来源）、`COLUMN_GAP` 6→**4**（= 主面板那一档）；派生量 `CONTENT_TOP`=26、`BOTTOM_STACK`=50。★**字号与配色一个字都没动**（取证列的五个维度里那两维本来就同源），也**不**给次级面板加主面板那层装饰（它是 package-private 且尺寸写死 398×360）。**（二）逐型结果，★如实报告净变化**：STACK 214×118 → **214×94**（−24，删掉那条说明行）/ CHANNEL_PERSIST 250×140 → **250×94**（−46，删两条）/ MAGNET 358×314 → **356×312**（只统一度量，72 格盘与五枚控件一字未动）/ MAGE 200×176 → **200×172**（元素容量行从框外搬进**框内**，框宽 144→**170** 才装得下它）/ ★★**CAPACITY 高度 0 变化（214×98）** —— 那句容量读数的英文最坏串逻辑宽 348，在 202px 的盒里按保守前进量要折**两行** ⇒ 读数盒必须留 22（`CAPACITY_READOUT_HEIGHT = RECEIPT_HEIGHT`），本面只换了按钮高与对齐。⇒ ★**不得写成"五面都变小"**：三面 mini、两面只统一度量、一面没变小。**（三）★断言改判（P-3 式双落）**：三面趋同到 94 之后，R97 S5 立的那条测试判据「五本**高账**互不相同」（`heights.size() == 5`）**必然**红，而趋同是用户裁定的**结果**不是症状 ⇒ 原位作废（台账 §R97 ① 表内标注 + §R98 R98-②），同批换成**三条更严**的正判据：① 五面 `(宽,高)` **元组**互异；② 三个简单型各带**只属于自己那一型**的读数键（`readoutKeyOf`：CAPACITY→`gtit.pocket.fluid.capacity`、STACK→`config.stack.limit`、CHANNEL_PERSIST→`config.persist.idle`，两枚挂载型回 null；★装配侧与用例读同一张表，不是第二份真相）；③ `sectionsOf(type)` 的**第二段**互异（与既有的 `config_panel_dispatch_is_not_five_identical` 同判据**双钉** ⇒ 分派面与几何面分开，改一处忘另一处就红）。三条各做过一次人为突变，红都在点名那条上、互不影子。**（四）★装配序改判（R98-⑥）**：`build(ui, type)` 的 child 序从「回执行 → 关闭钮 → 各段」换成「**各段先、底部两件后**」（`PocketConfigPanel.java:991`，循环 `:1001-1029`、两件 `:1030-1031`）——MUI2 按 child 序绘制 ⇒ 旧序下磁力框（y 26…262）与魔法使框（26…122）会画在回执行与关闭钮**之上**；当前几何不重叠所以看不出来，但任何一次往下扩段的排版都会把底部两件盖掉，换序之后"贴底的两件压得住内容段"是**结构性的**、不靠行号运气。**（五）未证到**：真实字体下的折行/压线、mini 后是否读起来"小巧"、CAPACITY 那句是否恰好两行、换序后的遮挡观感——★全部属实机项（检查表 §二十二 X-2 / X-6 / X-7），本轮离线只数到像素账与判据形状。★★★**R100 片 D 再翻（本条 R98 那组度量与尺寸到此为止）**：面板文字换 0.8 档（面板局部 `PocketConfigPanel.TEXT_SCALE`，主面板 `RESIDENT_TEXT_SCALE`=0.6 一字未动）⇒ `MARGIN` 6→**8**（不压 ~7px 装饰框线）、`READOUT_GAP` 2→**3**、`MODE_ROW_GAP` 1→**3**、`SWITCH_WIDTH` 66→**72**、小钮（`MODE_BUTTON_WIDTH` 30→**46**、`FREQ_BUTTON_WIDTH` 38→**46**，88×18 N=4 的 9-slice 中带 ≥ 源 45%，治用户点名的「小钮变形」）、`MAGNET_BUTTON_WIDTH` 110→**128**、`MAGE_FRAME_WIDTH` 170→**行带等宽 232**（框右空带归零）、磁力面 M-1 的第 5 段说明行**迁三态钮/格件 tooltip**（键不删只改用途 ⇒ lang 425 不动）、左列四段**等距铺满框高**（步距 72，`MAGNET_NOTE_HEIGHT` 撤销）⇒ 五面现值 = CAPACITY **266×85** / STACK **266×81** / CHANNEL_PERSIST **318×102** / MAGNET **266×299** / MAGE **248×164**；空白率两档判据同批重算（紧凑 ≤15% 不变，磁力 30%→**35%** = 说明行迁出后的新结构地板 ≈34.1%，另加左列连续空白带 ≤ 步距的纵向判据）。

59. **★五块插件面板"开新关旧"这三行循环的代价（R98-⑤，需求 2-a）**：`NekoPocketPanel#openUpgradeConfig`（`:1513`）里加了一个五行循环（`:1536-1540`）⇒ 左键开第 N 块插件面板时，先关掉**其他**已经开着的那几块。★**互斥范围只有这么大**：只在**五块插件面板之间**，主面板的关闭行为与 ESC / E 语义**一字未改**（不是"整个口袋屏单实例"）。三条形状约束不是风格：**① 严格排除 `index` 自身**（不能"先全关再开"）——`SecondaryPanel.openPanel()` 首行 `if (this.open) return;`（实测 2.3.91 `SecondaryPanel.java:79-80`），而 `open` 只在 `closePanelInternal()`（`:51-52`）里清 ⇒ 先关本枚再开本枚 = **开不起来**，NEA 在场时关闭还是异步动画；**② 只关 `isPanelOpen()` 为真的那几枚** ⇒ 对"没开的关成 no-op"这条库侧行为免疫，也不依赖关闭是否异步；**③ 用接口自带的读数**（`IPanelHandler.java:38`，★取证写的 `:60` 在 2.3.91 不对，台账已按现测登记）⇒ ★不长出"哪枚开着"的第二份镜像状态（那份镜像一旦与真值分叉，就会出现"关不掉的面板"）。★那一支还**必须排在两个早退支之后**（空格点击不该顺手关掉已经开着的面板），由用例的顺序腿钉。**玩家可见的三条代价**：① **同型重开不会重新定位面板**（库侧首行早退，本片刻意不改，需求原话只到"再打开会关掉旧的"）；② 大面（磁力 356×312）被小面顶掉之后**要再点一次那枚格**才能回来——handler 实例级缓存还在，但"显示态被关掉"是玩家看得见的；③ ★**"屏幕上是否真只剩一面"本轮离线不可证**：互斥的**正例**在这个测试 JVM 里**结构性不可驱动**（`ModularPanel` 连类初始化都过不去——`NoClassDefFoundError: it/unimi/dsi/fastutil/objects/ObjectList`，fastutil 不在 `runPocketTest` 的 runtime classpath 上），离线能真驱动的只有早退两支 + 全关那条腿与三条源码/顺序判据 ⇒ 归检查表 §二十二 X-3（含"NEA 在场时旧面会不会闪一下又没了"）。

60. **★R98 实机判不合格的整改五条（R99，用户 2026-09-28 附磁力面板截图驳回「几乎啥都没改」）**：v1.8.43 的需求 2 判不合格、需求 1 判半合格 ⇒ 本轮五片，每片都配**可截图判读**的验收（检查表 §二十三 Y-1..Y-4），不再接受"测试绿即完成"：**① 装饰层上五面（T3 档）**——五面第一 child = `NekoPocketDecoration.build(w,h)`（cloth + PANEL 9-slice 木框 + 4 铜包角；铆钉与绳缝是主面板专属：次级面板没有可压的列缝，绳缝坐标是底部带常量），MUI2 面板主题默认底 `GuiTextures.MC_BACKGROUND`（浅灰板）被盖掉；★R98 期「不复用装饰层」的理由里 package-private 那条**不成立**（同包），尺寸写死那条为真、已参数化解决——零参 `build()` 与主面板调用点一字不动，主面板回归面 0。**② 底带并带（B-0）**——`BOTTOM_STACK` 50 → **32**，回执行与关闭钮同带并排（关闭钮纵向落在回执带内居中）⇒ 五面各 −18px，"关闭孤在右下角"从几何上消失。**③ 磁力面 M-1 重排**——控制块从两列 230 宽改**单列 110 宽 × 5 段**（三态/目标/清空/计数/说明竖排），说明盒 228→108（`MAGNET_NOTE_HEIGHT` 24→**30**，英文要折三行）、面 356×312 → **236×294**；72 格盘保持 12 列×6 行（**M-2 转置属翻案，用户明确不采**；M-3 不满足诉求不采）。**④ 元素储量恒六行显 0（A+D）**——数据源保持 `elem`，排版腿弃 `snapshot()` 改逐 tag `get`（缺键即 0）⇒ 「没吸进来」与「吸了但没同步」分得开；DP-2「非零才出」作废（代价 46/55/56 已原位标注）。**⑤ P0 判据补账**——新用例 `magnet_panel_text_fits_its_boxes`（磁力说明行折行 + 两枚挂载框标题一行账）与 `config_panel_blank_rate_within_caps`（逐面内区空白率：紧凑四面 ≤15%、磁力 ≤30% = M-1 结构地板，R99-D-2 那种 51.4% 空洞从此是测试能拦的形状）；用例总数 241 → **243**。★吸球证据两档改口见代价 47。

61. **★全 mod 物品与售货机的 tooltip 品牌尾行（R100 片 A）**：**13 处调用点**（12 类物品——浮空/电力浮空核心、猫猫币两枚、轮回水晶、新手宝箱、探矿核心、无限元件两枚、口袋、升级插件、功能戒指族〔八枚共用 `BaseRing` 一处〕——加猫猫售货机）的 tooltip **末尾**统一追加一行 `gtit.tooltip.added_by` 彩色署名行（GTSR 同款形状，产出单源 `GTITUtils.getAddedByLine`）。★代价：每件物品悬停多一行；两份 lang 各 +1 键（410 → 411 链的头一截，见代价 55 的链尾 424）；键名落档即冻结（代价 45）。工具提示的"这是谁家的"从此不用猜。
62. **★魔法使插件 tooltip 十行连号族（R100 片 B）**：升级插件的 tooltip 从一行摘要扩为**十行连号** `gtit.pocket.upgrade.mage.line.0..9`（固化不可逆 / 四模式 / 蒸馏节拍 / 充法杖 / 猫猫币 / 源质转换 / 结晶 / 元素容量 / 结晶与转换同吃源质盘的冲突声明），数字**全部 `%n$d` 注入**（驱动器里零裸数值，与"速率单源在 PocketConstants"同一条纪律）。★代价：插件悬停显著变长（信息面换透明度，用户点名要的）；两份 lang 各 +10 键；连号族消费端是 equals-break 循环 ⇒ 跳号即静默截断，机检与冻结纪律与 tooltip 主族同形（代价 45）。
63. **★通道频率档位 + 免开背包会话复原 + 开启即首批 + 保守跳过（R100 片 C，三条翻案记档见台账 §R100 ①）**：① 持续化道批节拍按档位表 `{1,2,3,5,10,15,30,60,120,300,600}s` 可调（默认 5s，NBT 键 `channelFreqTier` 冻结、无键=默认 ⇒ 旧档零迁移；换算单源 `channelFreqTierTicks`，改档当场对在跑的道生效）；**手动付费道 1s×30 批语义一字未动**（用户裁决：档位只作用于持续化）——旧句「本片一个字节都不碰 CHANNEL_TICK_PERIOD」关于该常量的那一半**原位作废**（它吸收为档位表 1 秒档成员，全仓速率源两处并一处；PairsPerSecond 与 ticksToSecondsCeil 的不碰裁定仍成立）。② **免开背包**：登录后从不开面板的口袋由驱动侧就地复原 headless 会话（`PocketChannelSessions`，载体档解析唯一公共入口，三条前置一条不省：persist 位生效 / 承载栈身份 / 绑定表非空，任一不满足零写入）。③ **开启即首批**（R100 需求 4 改判）：旧句「短效通道不在这里跑第一批」原位作废——`fireFirstBatchNow()` due=0 形式，手动道与持续化道激活瞬间立即跑第一批、首批照计入 30 批预算；E3 §3.4 与 R62 两条纪律原样保留。④ **保守跳过**：三来源全空且无补满需求的拍整拍早退（零写零同步、不消耗批次、道不断；含补满相就不跳）。★代价：ACTION 18 新动作码；空载时手动道墙钟寿命可长于 30 秒（"跳过不消耗"偏差，台账 §R100 ②）；headless 复原缺承载栈只 WARN 不回滚（同处）；驱动真空支在"有活会话"的口袋里每 tick 多两次 byte 读（R53c"不读 NBT"半句的受控放宽）。
64. **★五型面板重排：文字 0.8 档 + 描述悬浮化 + 三 switch 收敛（R100 片 D，尺寸现值见代价 35/58）**：面板文字 0.6 → **0.8** 档（面板局部 `TEXT_SCALE`，主面板 `RESIDENT_TEXT_SCALE` 一字未动），边距与控件随之重算（`MARGIN` 6→8、小钮统一 46 宽治"小钮变形"、磁力说明行迁三态钮/格件 tooltip ⇒ "描述悬浮化"——键不删只改用途）；三处重复 switch 收敛为单源分派。★判据同批重算：磁力面空白率上限 30% → **35%**（说明行迁出后的新结构地板），另加**左列连续空白带 ≤72** 的纵向判据（"成片空洞"从此有两个方向的拦法）。★代价：五面尺寸全体变化（现值 266×85 / 266×81 / 318×102 / 266×299 / 248×164）；lang 键数零变化。
65. **★口袋 tooltip 描述族 4 → 3 行、恒 4 行总账（R100 片 E，删行翻案与行数账见代价 55）**：描述删到 `{0,1,2}` 三行（打开方式 / 内容随物品丢 / 合成一次性代价），**显式翻案**删掉 R39a"两排同权"声明行（该行防的误读在面板逐列同形现状下已无落点；翻案注释落物品类 javadoc 原声明位，用例钉"已删 + 翻案在位"）；蒸馏行同步压短（键名冻结、注入形态不动）⇒ **恒 4 行**（3 描述 + 1 蒸馏）、含储量恒 6 行 = **10 行**、再加片 A 品牌尾行 = **11 行**。★代价：R39a 那句防误读声明从此不在物品侧（依赖它的人改读面板流体列条目上的 `ghost` 族 tooltip）；两份 lang 各 −1 键（425 → 424）。
66. **★common→gui import 归零：两座工厂桥 + 队列搬家 + BGM 标志迁居（R100 片 F）**：依赖方向归正（唯一允许 gui→common）——`ItemNekoDimensionPocket#buildUI` 与 `MTENekoVendingMachineV2#getGui` 的 gui 包直连改经 **`PocketPanelBridge` / `VendingGuiBridge`** 工厂委托（行为零变化：委托目标就是原来那一次调用本身；注册点刻意不同侧：口袋桥 MUI2 双端建树 ⇒ 双端注册，售货机桥 `getGui` 仅客户端 ⇒ 仅客户端注册）；`VendingServerActions` 队列搬家、BGM 的 V2 开屏标志真相迁 `NekoMusicEventHandler`、`drawBadge` 三处收拢 `PocketBadgeDrawer`。★门：常驻归零用例 `r100_common_has_zero_gui_imports_and_bridges_wired` 把"common 包 gui import 恰 0 + 桥已接线"钉成永久判据。★代价：多一层工厂委托的间接性（读代码要多跳一跳）；桥未注册时空指针面由注册时机纪律守住（双端/客户端各自的 init 同批注册）。

***

## Rings / 戒指

<p align="center"><img src="README/ring.png" width="256" alt="8 枚功能性戒指 / 8 Functional Rings"><br><em>8 枚功能性戒指 / 8 Functional Rings</em></p>

A set of 8 functional rings that equip to Baubles ring slots, providing various buffs and abilities. Rings are obtained from chest loot, crafting, or the starter gift.

8 枚功能性戒指，装备于 Baubles 戒指栏，提供各种增益和能力。戒指可通过宝箱战利品、合成或新手宝箱获得。

### Baubles Ring Slot Expansion / Baubles 戒指栏扩展

The mod expands the Baubles ring slots from **2 to 10** via the Baubles-Expanded API, allowing the player to equip multiple rings simultaneously and stack effects.

模组通过 Baubles-Expanded API 将戒指栏从 **2 个扩展到 10 个**，使玩家可以同时装备多枚戒指并叠加效果。

- Calls `BaubleExpandedSlots.tryAssignSlotsUpToMinimum("ring", 10)` during PreInit
- Falls back to `overrideSlots()` during Init to ensure correct slot ordering
- Automatically compatible with other mods using `BaublesApi.getBaubles()` (dynamic `getSizeInventory()`)
- 在 PreInit 阶段调用 `BaubleExpandedSlots.tryAssignSlotsUpToMinimum("ring", 10)`
- 在 Init 阶段通过 `overrideSlots()` 兜底，确保槽位顺序正确
- 自动兼容其他使用 `BaublesApi.getBaubles()` 遍历的模组（动态 `getSizeInventory()`）

### Ring List / 戒指总览

| # | Name / 名称                       | Effect / 效果                                                                                | Stackable / 叠加 |
| - | ------------------------------- | ------------------------------------------------------------------------------------------ | :------------: |
| 1 | Ring of Distant Grasp / 戒指·遥握   | Interaction & attack range +2 per ring / 交互与攻击距离 +2                                        |       ✔️       |
| 2 | Ring of Skywalk / 戒指·凌步         | Auto step-up 1 block / 自动走上 1 格方块                                                          |       ✖️       |
| 3 | Ring of Windrider / 戒指·御风       | Creative flight / 创造飞行                                                                     |       ✖️       |
| 4 | Ring of Gluttony / 戒指·饕餮        | Continuous hunger restore + emergency fill / 持续恢复饥饿度 + 应急饱食                                |       ✖️       |
| 5 | Ring of Ironheart / 戒指·磐躯       | Max health +20 per ring (10 hearts) / 生命上限 +20                                             |       ✔️       |
| 6 | Ring of Dragon's Breath / 戒指·龙息 | Fire Resistance + Night Vision + Regeneration I + Resistance I / 抗火 + 夜视 + 生命恢复 I + 抗性提升 I |       ✖️       |
| 7 | Ring of Mountainbreaker / 戒指·裂山 | Strength II + Haste II / 力量 II + 急迫 II                                                     |       ✖️       |
| 8 | Ring of Tempest / 戒指·疾风         | Speed II + Jump Boost II / 速度 II + 跳跃提升 II                                                 |       ✖️       |

### Ring Details / 戒指详情

- **Ring of Distant Grasp / 戒指·遥握**: Extends block reach distance via Mixin (`MixinPlayerControllerMP`). Each ring adds +2 blocks. Stackable — multiple rings compound the bonus.
- **Ring of Skywalk / 戒指·凌步**: Automatically steps up 1-block heights without jumping. Walks smoothly like flat ground.
- **Ring of Windrider / 戒指·御风**: Grants creative flight with no cost. Obtained only via crafting.
- **Ring of Gluttony / 戒指·饕餮**: Restores 1 hunger/sec; restores saturation when full. Emergency-fills hunger and saturation when below 5 (60s cooldown).
- **Ring of Ironheart / 戒指·磐躯**: Uses `SharedMonsterAttributes.maxHealth` Attribute Modifier. Each ring adds +20 max health (10 hearts). Stackable.
- **Ring of Dragon's Breath / 戒指·龙息**: Refreshes 30s potion effects every 5s — no flickering.
- **Ring of Mountainbreaker / 戒指·裂山**: Refreshes 30s potion effects every 5s.
- **Ring of Tempest / 戒指·疾风**: Refreshes 30s potion effects every 5s.
- **戒指·遥握**：通过 Mixin（`MixinPlayerControllerMP`）扩展方块交互距离，每枚 +2 格，可叠加。
- **戒指·凌步**：自动走上 1 格高方块，如履平地，无需跳跃。
- **戒指·御风**：获得创造飞行，无消耗。仅通过合成获得。
- **戒指·饕餮**：每秒恢复 1 点饥饿值，满后恢复饱和度；饥饿度低于 5 时一次性补满（冷却 60 秒）。
- **戒指·磐躯**：使用 `SharedMonsterAttributes.maxHealth` 属性修饰器，每枚 +20 生命上限（10 颗心），可叠加。
- **戒指·龙息**：每 5 秒刷新 30 秒药水效果。
- **戒指·裂山**：每 5 秒刷新 30 秒药水效果。
- **戒指·疾风**：每 5 秒刷新 30 秒药水效果。

***

## Starter Gift / 新手宝箱

<p align="center"><img src="README/gift.png" width="128" alt="新手宝箱 / Starter Gift"><br><em>新手宝箱 / Starter Gift</em></p>

A gift box automatically granted to players on their **first login** to a world. Right-click to open and receive a set of starter items — guaranteed items plus randomly drawn items from a configurable pool.

玩家**首次进入某个世界**时自动获得的新手宝箱。右击打开即可获得一系列新手物资——包含必中物品和从随机物品池中抽取的随机物品。

- Auto-granted on first world login (tracked via persisted NBT)
- Right-click to open: grants guaranteed items + random items
- Items drop to the ground if inventory is full
- Fully configurable via `config/gtit/gift_config.json`
- Default random pool includes 6 rings (Skywalk, Gluttony, Ironheart, Dragon's Breath, Mountainbreaker, Tempest)
- 首次进入世界自动发放（通过持久化 NBT 追踪）
- 右击打开：获得必中物品 + 随机物品
- 背包满了物品丢到地上
- 通过 `config/gtit/gift_config.json` 完全可配置
- 默认随机池包含 6 枚戒指（凌步、饕餮、磐躯、龙息、裂山、疾风）

### Gift Config / 宝箱配置

```json
{
  "guaranteed_items": [
    { "item": "minecraft:bread", "amount": 16, "meta": 0 },
    { "item": "minecraft:torch", "amount": 64, "meta": 0 }
  ],
  "random_items": [
    { "item": "gtit:ring_skywalk", "amount": 1, "meta": 0 },
    { "item": "minecraft:enchanted_book", "amount": 1, "meta": 0, "nbtBase64": "..." }
  ],
  "random_count": 2
}
```

- `guaranteed_items`: Items always granted / 必中物品
- `random_items`: Random item pool / 随机物品池
- `random_count`: Number of random items drawn / 随机抽取数量
- `nbtBase64` (optional): Base64-encoded NBT data; written automatically when using `yesNBT` / 可选，Base64 编码的 NBT 数据；使用 `yesNBT` 时自动写入

***

## Reincarnation / 周目轮回

<p align="center"><img src="README/reincarnation.png" width="500" alt="周目轮回 GUI / Reincarnation GUI"><br><em>周目轮回 GUI / Reincarnation GUI</em></p>

A hardcore "new cycle" system built around the Reincarnation Crystal. Deposit items, confirm reincarnation, and your character ascends and dies — the world save is deleted — while the deposited items carry over to the next world as a reincarnation reward. Strictly single-player: the whole system disables itself when the world is shared over LAN.

围绕轮回水晶构建的硬核"新周目"系统。寄存物品、确认轮回后角色升天死亡——存档随之删除——寄存的物品则作为轮回奖励穿越到下一个世界。仅限严格单机：对局域网开放时周目系统整体停用。

### Cycle Flow / 轮回流程

- Right-click a **Reincarnation Crystal** to open the Reincarnation GUI / 右击**轮回水晶**打开周目 GUI
- Deposit items, then press **Confirm Reincarnation**: a 6-second ascension plays, the character dies, and the world save is deleted / 寄存物品后按下**确认轮回**：6 秒升天演出后角色死亡，存档删除
- Re-entering a world whose timeline has already been reincarnated triggers a 30-second countdown before the same ascension / 进入已被轮回过的时间线时，30 秒倒计时结束后同样升天
- Deposited items travel through the reincarnation mailbox and are granted **5 seconds** after the first login to the new world / 寄存物品经投胎信箱穿越，在新世界首次登录 **5 秒**后发放
- Reward items spiral down **one by one** — each becomes pickupable in the final 15% of its descent (~0.4 blocks away); excess drops to the ground when the inventory is full / 奖励物品**依次**螺旋降落——每件在降落末段 15%（距玩家约 0.4 格）即可拾取；背包满则掉落地面
- Spark trail particles stop in the final 20% of each item's descent to keep the view clear / 每件物品降落的最后 20% 停撒拖尾粒子，避免遮挡视野
- Enchantments and damage NBT are not preserved (the deposit ignores NBT) / 附魔、损伤等 NBT 不保留（寄存忽略 NBT）
- Cycle data is obfuscated, encrypted and bound to the player / 周目数据混淆加密、绑定玩家

### Progression / 进度解锁

- **15 hull columns** (Steam → MAX): feed machine hulls into a column's slot — 16 hulls unlock the column / **15 个等级列**（蒸汽 → MAX）：向列槽放入机器外壳，累计 16 个解锁该列
- **3 unlockable rows** via Neko Coin rolls: press an unlock button to toggle continuous rolling (1 coin per tick while active); Shimmer Neko Coin has a 0.1% and Neko Coin a 0.001% success rate per roll / **3 行解锁**靠猫猫币连掷：按下解锁按钮开始连掷（激活期间每 tick 消耗 1 枚），闪烁猫猫币单掷 0.1%、猫猫币单掷 0.001% 成功率
- Unlocked rows and columns determine the deposit slots available for the next cycle / 已解锁的行与列决定下次轮回可用的寄存格

***

## Machine Sound Mute / 机器音效静音

Two QoL configs controlling GT5U machine sounds. Config file: `config/gtit/gtit_mute.json`.

两项 GT5U 机器音效静音配置。配置文件：`config/gtit/gtit_mute.json`。

```json
{
  "_comment_mute": "mute_machine_working_sounds=true 时：新放置机器默认静音（有 GUI 按钮的机器可单独取消静音）。",
  "mute_machine_working_sounds": false,
  "_comment_extra_mute": "extra_mute=true 时：额外强制拦截无静音按钮机器的音效（锅炉蒸汽排放音/锅炉沸腾加热循环音/管道蒸汽泄漏音），不受 GUI 按钮控制。",
  "extra_mute": false
}
```

### `mute_machine_working_sounds`

- **`false` (default)**: No intervention. Players can toggle each machine's mute button in its GUI.
- **`true`**: Newly placed machines (or those without a saved mute state) default to muted; players can still unmute per-machine via the GUI button.
- **`false`（默认）**：不干预，玩家可通过每台机器 GUI 右上角的静音按钮单独控制。
- **`true`**：新放置或未保存过静音状态的机器默认静音；玩家仍可通过 GUI 按钮单独取消。

### `extra_mute`

For machines **without** a GUI mute button (boilers, fluid pipes). Force-cancels their sounds regardless of any state.

针对**没有** GUI 静音按钮的机器（锅炉、流体管道）。无论状态如何，强制拦截其音效。

- **`false` (default)**: No intervention.
- **`true`**: Force-cancel boiler steam-vent sound (`ventSteamIfTankIsFull` → `sendSound`), boiler boiling/heating loop sounds, and fluid pipe steam-leak sound.
- **`false`（默认）**：不干预。
- **`true`**：强制拦截锅炉蒸汽满罐排放音（`ventSteamIfTankIsFull` → `sendSound`）、锅炉沸腾/加热循环音、流体管道蒸汽泄漏音。

### Mixin Architecture / Mixin 架构

| Mixin                              | Target                              | Config                | Function / 功能                                                                                |
| ---------------------------------- | ----------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------- |
| `MixinBaseMetaTileEntityMuffle`    | `setInitialValuesAsNBT` (TAIL)      | `mute_machine_working_sounds` | Default new machines to `mMuffler=true`; GUI button still works per-machine                   |
| `MixinMTEBrickedBlastFurnace`      | `updateSound` (HEAD)                | `mute_machine_working_sounds` | Cancel brick blast furnace flame loop sound                                                    |
| `MixinMTEBlackHoleCompressor`      | `playBlackHoleSounds` (HEAD)        | `mute_machine_working_sounds` | Cancel black hole compressor loop sound                                                        |
| `MixinMTEBoilerVentSteam`          | `MTEBoiler.doSound` (HEAD)          | `extra_mute`          | Cancel boiler steam vent sound + particle (`SOUND_EVENT_LET_OFF_EXCESS_STEAM`)                 |
| `MixinMTEBoilerSoundLoops`         | `updateSoundLoops` (HEAD)           | `extra_mute`          | Cancel boiler boiling/heating loop sounds (`GTCEU_LOOP_BOILER` / `GTCEU_LOOP_FURNACE`)         |
| `MixinMTEFluidPipeSound`           | `MTEFluidPipe.doSound` (HEAD)       | `extra_mute`          | Cancel fluid pipe steam-leak sound (`aIndex==9`, `RANDOM_FIZZ`)                                |

***

## Multiblock Test Machine / 多方块测试机器

A development-stage test multiblock (HV tier, 3×3×3 hollow TungstenSteel) once used to verify the multiblock registration process, structure detection logic, and recipe system integration. It was removed in v1.6.30 — the Neko Vending Machine V2, built directly on GT5U's `MTEEnhancedMultiBlockBase`, has since taken over this role.

开发阶段用于验证多方块机器注册流程、结构检测逻辑及配方系统的测试多方块（HV 级，3×3×3 空心钨钢结构）。该测试机已于 v1.6.30 移除——直接基于 GT5U `MTEEnhancedMultiBlockBase` 构建的猫猫售货机 V2 已承接其职责。

***

## Administrator Commands / 管理员命令

All commands under `/gtit`, OP permission level 2. **Tab completion supported** for subcommands, tab IDs, order IDs, and player names. Subcommands that manipulate the executor's own inventory are player-only; the rest also work from the server console.

所有指令通过 `/gtit`，OP 权限等级 2。**支持 Tab 补全**——子命令、标签页 ID、顺序 ID、玩家名均可自动补全。需要操作执行者背包的子命令仅玩家可执行，其余支持服务器控制台。

### Starter Gift / 新手宝箱

| Command / 指令                                | Description / 说明                                                               |
| ------------------------------------------- | ------------------------------------------------------------------------------ |
| `/gtit gift certain [yesNBT\|noNBT]`        | Set guaranteed items from current inventory / 将当前背包物品设为必中物品；默认 `noNBT`       |
| `/gtit gift random <count> [yesNBT\|noNBT]` | Set random pool from inventory + draw count / 将背包物品设为随机池并设置抽取数；默认 `noNBT`   |
| `/gtit gift reset`                          | Reset to default config / 恢复默认配置                                                 |
| `/gtit gift claimlist`                      | List players who claimed the gift (online + offline) / 列出已领取玩家（在线+离线）          |
| `/gtit gift claimreset [all\|玩家名]`         | Reset claim status; re-gift on next login / 重置领取状态，玩家下次登录时自动发放（支持离线玩家） |

### NekoVM / 猫猫售货机

| Command / 指令                | Description / 说明                                                        |
| --------------------------- | ------------------------------------------------------------------------ |
| `/gtit nekovm edit on\|off` | Toggle visual config edit mode / 开关可视化配置编辑模式                              |
| `/gtit nekovm reload`       | Hot-reload trade + tab config / 热重载交易与标签页配置                                |
| `/gtit nekovm timereset`    | Reset all trade cooldowns of the executor's team / 重置当前玩家（团队）的全部交易冷却 |
| `/gtit nekovm help`         | Full help / 完整帮助                                                              |

### Sign-In / 每日签到

| Command / 指令                              | Description / 说明                                                        |
| ----------------------------------------- | ------------------------------------------------------------------------ |
| `/gtit signin`                            | Player self sign-in, same as the GUI button / 玩家自助签到，与签到 GUI 按钮等效（仅玩家） |
| `/gtit signin info [玩家名]`                | View sign-in status (online players only) / 查看签到状态（目标仅支持在线玩家）        |
| `/gtit signin reload`                     | Hot-reload sign-in + online-time reward config / 热重载签到与在线时长奖励配置        |
| `/gtit signin admin set <玩家名> <天数>`     | Set consecutive sign-in days / 设置连续签到天数（在线玩家）                          |
| `/gtit signin admin reset <玩家名>`         | Reset a player's sign-in data / 重置玩家签到数据（在线玩家）                          |
| `/gtit signin help`                        | Full help / 完整帮助                                                              |

### Lottery / 抽奖

| Command / 指令           | Description / 说明                        |
| ---------------------- | ---------------------------------------- |
| `/gtit lottery reload` | Hot-reload lottery pool config / 热重载抽奖卡池配置 |
| `/gtit lottery help`   | Full help / 完整帮助                                 |

### Mail / 邮件

| Command / 指令                              | Description / 说明                                                                                       |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `/gtit mail send <玩家名> <标题> [正文...]`   | Send mail; attachment = held item (console sends as "系统" without attachment) / 发送邮件；附件=执行者手持物品（控制台以「系统」名义发送、无附件） |
| `/gtit mail first <标题> [正文...]`           | Set first-login reward template, overwrites the old one / 设置首登奖励模板（覆盖旧模板；仅玩家）                  |
| `/gtit mail firstclear`                   | Clear the first-login reward template / 清除首登奖励模板                                                  |
| `/gtit mail once <奖励ID> <标题> [正文...]`    | Publish a one-time reward; every player receives it once / 发布一次性奖励，全体玩家各收一次（奖励 ID 不可重复）      |
| `/gtit mail help`                      | Full help / 完整帮助                                                                                      |

A literal `\n` in mail bodies is converted to a line break. / 邮件正文中字面 `\n` 会被转换为换行。

### Server Management Terminal / 服务器管理终端

| Command / 指令    | Description / 说明                                                                                     |
| ---------------- | ---------------------------------------------------------------------------------------------------- |
| `/gtit terminal` | Open the server management terminal (mail / sign-in / trade-cooldown / gift-audit panels); in-game OP2 players only — unavailable and unlisted on physical dedicated servers / 打开服务器管理终端（邮件/签到/交易冷却/礼包审计四页面板）；仅游戏内 OP2 玩家可用，物理专用服务器上不可用且 Tab 补全不提示 |

***

## Tech Stack / 技术栈

- Jabel (modern Java syntax, Java 8 bytecode) / Minecraft 1.7.10 / Forge 10.13.4.1614
- ModularUI / ModularUI2 / StructureLib
- Dependencies: GT5-Unofficial (5.09.54.183), GTNHLib, VisualProspecting, Baubles-Expanded, IC2; VendingMachine 0.4.100 (dev local jar; the V2 multiblock's structure casing and uplink hatch are still provided by VendingMachine at runtime), BetterQuesting 3.8.72 (compileOnly)
- Jabel（现代 Java 语法，编译为 Java 8 字节码）/ Minecraft 1.7.10 / Forge 10.13.4.1614
- 依赖：GT5-Unofficial（5.09.54.183）、GTNHLib、VisualProspecting、Baubles-Expanded、IC2；VendingMachine 0.4.100（dev 本地 jar；V2 多方块的结构外壳与上行仓仍由 VendingMachine 提供运行时支持）、BetterQuesting 3.8.72（compileOnly）

***

## License / 许可证

Released under the AGPL-3.0 License. See the LICENSE file for details.

以 AGPL-3.0 许可证发布，详见 LICENSE 文件。

***

## Acknowledgments / 致谢

- **[AE2Things](https://github.com/asdflj/AE2Things)** — The Infinity Cell implementation is adapted from AE2Things' storage cell code (GTNH 2.8.4 version), rewritten for the GTNH 2.9.0 AE2 API.
  无限存储元件的最初实现移植自 AE2Things 的存储元件代码（GTNH 2.8.4 版本），为 GTNH 2.9.0 AE2 API 重写；v1.8.22 起已被 GTIT 自有的多通道「猫猫无限存储单元」取代，移植版两枚降级为 `[OLD]` 保留。
- **[VendingMachine](https://github.com/GTNewHorizons/VendingMachine)** — The Neko Vending Machine originated on top of the VendingMachine framework, with custom currency, GUI, and trade logic, before being rebuilt as the independent V2 multiblock on GT5U.
  猫猫售货机最初基于 VendingMachine 框架构建（自定义货币、界面与交易逻辑），后重构为基于 GT5U 的独立 V2 多方块机器。
