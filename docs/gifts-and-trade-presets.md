# 礼包与贸易预设

普通玩家使用 `/gifts` 查看礼包、触发条件、内容和领取状态；BQ 解锁型礼包也可使用 `/gifts claim <ID>` 领取。旧的新手宝箱物品与已领取标记保留兼容。礼包包裹绑定领取者，不能用复制的包裹重复兑换。

## 可视化配置礼包

单人存档拥有者或专用服务器 OP4 可在礼包目录中新增、编辑、删除礼包，配置名称、像素图标或物品图标、必得物品、随机物品池、不重复抽取数量、NBT 和无线 EU。管理页独立于终端，沿用 480×330 布局；礼包目录在左侧按每页 6 项显示，右侧显示所选礼包详情。管理窗口统一使用猫猫次元口袋 C2 的棕金布纹、铜框、物品槽和币条材质，按钮沿用 C2 五态。列表与字段左对齐，标题加粗、正文加大，行面使用轻薄 C2 布纹并以颜色标示状态；字段、图标和玩家背包槽对齐，保存按钮固定在右下角。ID 等次要信息放入 tooltip，底部显示简短状态，详细错误仍可查看。32 个礼包壳均使用 24×24 静态 RGBA 图标与半像素深灰描边，其中 8 个时代礼包取消旧动画，原注册 ID 保持不变。独立图标未设置时继承礼包载体图标，显式设置的自定义图标会保留。图标和奖励幽灵槽支持从背包与 NEI 选择物品，不消耗原物品。

礼包编辑器分为基础、必给和随机三页，条目按每页 4 项分页；物品奖励通过物品槽配置，并可填写数量与 SNBT。草稿可保存或放弃；有未保存修改时关闭会触发保护。终端交互保持不变。

触发条件有三种：

- 首次入服：仅发给首次被本系统记录登录的账号。新增这种礼包不会向已有账号追发；原 `starter` 保留旧领取标记与明确管理员重置的兼容行为。
- BQ 解锁后手动领取：任务完成后可在 `/gifts` 领取。
- BQ 首次完成自动发放：记录完成事件当时的团队成员，包括离线成员；离线成员下次登录时补发。每个玩家与礼包 ID 只授予一次，换队和任务重置不会再次领取。

BQ 任务 ID 使用与猫猫贸易机相同的解析规则。任务不存在、格式错误或 BQ 不可用时不会放行。默认礼包包括新手、蒸汽、LV、MV、HV、EV、IV、LuV；时代入口绑定 GTNH 2.9.0-RC-2 的任务 `44 / 88 / 1148 / 160 / 176 / 213 / 1493`。自定义任务库需要自行调整绑定。

蒸汽礼包的 `bread`、`torch`、`pearl`、`coin` 奖励已替换为：`gregtech:gt.blockcasings` meta `10` 镀铜/青铜镀层砖 ×16；Potin 粗青铜合金 `pipeHuge`（`gregtech:gt.blockmachines` meta `30729`）×32；普通 `pipeMedium`（同物品 meta `30727`）×256；HP solar（同物品 meta `114`）×2。LV、MV、HV、EV、IV、LuV 礼包各包含对应电压等级的机器外壳 ×4、水泵 ×4、传送带 ×4、马达 ×8，替换原时代奖励而非追加。

定义保存在 `config/gtit/gifts.json`。首次迁移读取旧 `gift_config.json`；之后以新定义为准，旧 `gift certain/random/reset` 指令继续修改新手礼包。默认礼包内容版本仍为 V2，奖励数量不变；Mod 小版本发布不会自动递增该内容版本。管理员打开礼包窗口时，若有待处理的推荐默认内容更新，会看到“是否更新默认礼包？”弹窗，只有“更新”和“暂不”两个选项。“暂不”忽略当前内容版本；下一个实际发布的默认内容版本仍会询问。旧版 `SKIP` 状态继续生效；旧磁盘字段 `updateNotice=false` 会转换为忽略当时已评估的版本，新内容版本仍会询问。关闭默认礼包库时不弹提示，也不会自动启用礼包。选择更新后恢复全部 8 个默认礼包，包括被手动修改或删除的默认项，同时保留自定义礼包和领取记录；`starter` 奖励保持不变，已发放礼包的奖励快照不追溯修改。旧版 JSON 缺少 `defaultsInitialized` 标记时会依据已有礼包定义补初始化状态，保护现有内容；重新启用默认库时重新评估推荐更新。

背包满时包裹留在待发队列，不生成掉落物。大礼包可以分批打开；剩余内容、取出进度和玩家库存共同保存。无线 EU 使用十进制非负整数，存入授予记录锁定的 GT 无线网络所有者，只有最后一批奖励完成时支付，并通过持久收据避免重放。

## 贸易预设

默认按 **I** 打开，按键可在游戏控制设置中修改；专用服务器也可用 `/gtit presets`。服务端管理动作仍要求 OP4，单人模式由存档拥有者执行；普通玩家可查看页签来源。首次提示延迟 160 tick，并按存档与玩家记录。默认贸易与自定义贸易分为两个主栏，搜索与收藏在两栏内分别生效。

JAR 直接提供 GTIT 基础组和三套默认预设，不会在启动时将整个默认库导出到 `MiaoTrade/` 或 `preset_tabs/`。`gtit-base` 基础组固定选中，使用 `-400001` 起始的基础页签序列和 `MIAO400…` 交易系列。三个可选内置预设为 `gtit-ae-basic`、`gtit-ae-advanced`、`gtit-base-decoration`，稳定 ID、互斥声明与 `-100/-200/-300` 页签系列不变。自定义预设使用 `MIAO900` 交易系列、`<= -500000` 页签 ID，并以 `presetBuiltin=false` 标记。服务端页签元数据 `NekoPageEntry` 携带来源预设 ID、名称与图标物品/metadata/NBT；来源角标与名称提示对普通玩家可见。自定义贸易栏为空时的空态教程只对单人拥有者或服务端 OP4 显示。

默认贸易只有在某组被编辑且内容与 JAR 版本不同时，才会将该组完整快照（包含删除标记）保存到 `config/gtit/trade/default_overrides/<builtin-id>.json`；未编辑组不写覆盖文件。对已选组应用更新时，比较 JAR 指纹与上次应用记录；只有指纹变化的已选组才清除对应覆盖，并先出现简短的“更新/暂不”确认。不显示长篇差异页；重新读取目录或取消选择不会清除覆盖。`/gtit nekovm defultreset` 恢复四组默认贸易并清空默认覆盖，同时保留自定义预设、当前选择与历史账本；`defaultreset` 是兼容拼写。

一次性迁移标记为 `config/gtit/trade/default_storage_v3.json`。首次迁移把识别出的旧默认文件先备份到 `config/gtit/trade/default_migration_backup/`，再清理旧默认内容并写入标记，所有步骤通过同一可回滚事务处理；标记独立于 Mod 小版本，重启不会重复清理。旧默认归属按已知旧 UUID、MIAO1–92 交易标识或默认 owner/owned ID 标记识别。旧的非默认正页与自建预设保留，未知字段不丢失；若旧默认负页混有用户内容，会把用户内容迁到正页并保留原始数据；占用基础页范围的旧自定义页 ID 会映射到 `<= -500000`。旧单文件与分页文件并存时优先分页，失效的旧单文件也会备份并退役，以免清理后再次导入旧默认。

三套默认预设均不要求 BQ，AE basic 为 118 笔/7 页签、AE advanced 为 239 笔/12 页签、基地装饰为 64 笔/2 页签，合计 421 笔/21 页签；原 92 条基础贸易与 GTIT 特色 MIAO89 AE 材料交易保留，Web `gtit-base` / `gtit-miao` 不输出 AE 商品。普通 4k/16k 存储单元仍在并重价；基础组移除 4 核/16 核合成处理单元（`BlockCraftingUnit` meta `2/3`），不是普通存储。AE 基础组与高级组都只保留 Creative Energy Controller 单次交易，并恢复原交易稳定 ID；不销售 Creative Energy Cell，它作为兑换材料。两笔均 `maxTrades=1`、`cooldown=-1`；GTNH 原生合成配方不改，ME 2710–2723 已有条目不重复添加。

低阶常用商品保持廉价直购；高阶贸易采用前阶材料加猫币加工费，不使用纯大币堆叠。Creative Energy Controller 仅作交换产出，需空白普通 ME Controller、空白 Creative Energy Cell 与 1 常币加工费；Creative Energy Cell 不单独出售。Quantum 方案为 4×16M 存储单元、4 个量子环、致密能源单元与 24 闪币；2714 使用实际前阶部件及 UEV 存储等材料加 192 闪币，2716 使用实际总成与 UEV sensor 等材料加 256 闪币。单笔最多 7 种输入，即使输入全不可堆叠也不超过 26 格；底料与 Creative Energy Cell 在模型中按非零符号材料计，不把它们换算成伪造的完整币价。

`cooldown=-1` 使用按玩家/团队与 `tradeGroupId` 持久化的 `tradeCount`；成功一次后永久拒绝再次交易，团队人数不放大次数，失败、取消或支付失败不消费。管理员可用 `timereset` / `resetHistory` / `resetAllHistory` 清理记录；重载或默认重置不改历史。正 cooldown 以实际秒计，0 不限制时间；默认 `NekoTradeGroup` cooldown 为 0。界面显示“仅限一次/已兑换”而不显示负的剩余秒数。GT-MIAO-Web-BQ-Trade 可加载、编辑、保存和导出 `cooldown=-1`；小于 -1 或非整数会报错，`maxTrades` 规则不变。

`/gtit` 根命令仍要求 OP2，没有扩大权限。`/gtit help`、`/gtit tutorial`、Tab 补全和各子模块文案已补齐 `presets` 入口及正确参数位置；`/gtit presets` 可直接打开预设管理。`/gtit nekovm timereset` 重置当前玩家/团队在所有交易组的历史和冷却。默认恢复命令 `/gtit nekovm defultreset` 额外要求单人拥有者或专用服务器 OP4，并兼容 `defaultreset` 拼写。

Web 编辑器导出的 `config/gtit/trade/**` 用于贸易/页面数据，ZIP 或 `pages.json` 不能改名当作游戏预设；预设文件还需 ID、名称、图标、来源及互斥信息。v1.8.99 的 21-tab Web 工作区及 BQ 清理回执属于上一版本历史，其 426 条快照不代表当前 JAR 中的 419 条预设交易。

## 关闭内置默认内容

编辑 `config/gtit/default_content.cfg` 后重启实例：

```text
defaults {
    B:loadDefaultTradeGroups=false
    B:loadDefaultLottery=false
    B:loadDefaultGifts=false
}
```

三个开关互相独立，默认均为 `true`。关闭后不注入相应内置内容，保留用户配置与礼包授予记录。贸易开关控制 JAR 中的 GTIT 基础组和三套默认预设；自定义预设仍可使用。抽奖空配置保持为空，已经编辑过的卡池保留。礼包关闭不会使已经领取的有效包裹丢失；默认礼包库关闭时不弹更新提示，也不会自动重新启用。

## 自动化验证入口

启用专用测试维度后可运行：

```text
gtitlab run native-gift-system
gtitlab run native-trade-presets
gtitlab run native-preset-catalog-check
```

前两个也包含在默认 `suite` 中。第三个 `native-preset-catalog-check` 还会以只读方式检查生产状态：`gtit-base` 仍固定选中、未编辑基础组的 JAR 页签计数正确、保留页 ID 和来源元数据一致；同时检查随包预设的页签/交易数量、物品与 metadata、依赖和声明任务。它不应用预设、不改写世界数据。JSON 报告区分 PASS、FAIL、SKIP；服务端检查不能替代 Minecraft 物理客户端的视觉与点击验收。

最新单次控制器与贸易机制的验证状态见项目工作记录；Minecraft 物理客户端 UI 验收未做。
