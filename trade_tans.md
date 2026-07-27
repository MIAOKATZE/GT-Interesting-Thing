# trade_tans.bat 使用说明

旧版（v1.7.0 – v1.7.6）贸易配置迁移工具：把单文件 `nekovm_trades.json` 转换为新版（v1.7.7+）按标签页分文件的目录结构。

## 用途

- 旧格式：`config/gtit/nekovm_trades.json`，根结构 `{ "version": 1, "trades": [ ... ] }`，所有贸易条目堆在一个文件里。
- 新格式：`config/gtit/trade/trades/tab_<id>.json`，每个标签页一个文件，结构 `{ "version": 1, "tabId": <int>, "trades": [ ... ] }`，**文件名中的数字才是权威 tabId**。
- 本脚本读取旧文件，按每条目的 `tabId` 分组、`orderId` 升序，为每个标签页生成一个新文件，并把旧文件备份为 `.bak`。

## 前置条件

- Windows，PowerShell 3.0 或更高版本：
  - Windows 8 / 8.1 / 10 / 11 自带，无需安装；
  - Windows 7 自带的是 PowerShell 2.0，需先安装 WMF 3.0（微软官方免费组件）。
- 旧配置文件 `nekovm_trades.json`（v1.7.0 – v1.7.6 生成的那个）。

## 使用步骤

1. 把 `trade_tans.bat` 与 `nekovm_trades.json` 放在**同一目录**，推荐直接放在 `config/gtit/` 里。
2. 双击 `trade_tans.bat`。
3. 若目录下已存在 `trade\trades\tab_*.json`，脚本会询问是否覆盖：输入 `Y` 回车继续，按其它任意键中止。
4. 看到「迁移完成：成功 N 条」及每个 tab 的条数摘要即成功，按任意键关闭窗口。
5. 若脚本不是在 `config/gtit/` 里运行的，把生成的 `trade` 文件夹整体移动到 `config/gtit/` 下。
6. 启动游戏。

## 输出结构

```
<脚本所在目录>/
├── nekovm_trades.json.bak      ← 原文件备份（迁移成功后自动改名）
└── trade/
    └── trades/
        ├── tab_1.json          ← tabId 为 1 的标签页
        ├── tab_2.json
        ├── tab_3.json
        └── ...                 ← 每个出现过的 tabId 一个文件
```

每个文件内容为 `{ "version": 1, "tabId": <int>, "trades": [ ... ] }`，条目按 `orderId` 升序，编码为 UTF-8 无 BOM。

## 迁移规则（字段归一化）

- `id`：缺失、为空或与前面条目重复时，自动生成新的 UUID。
- `tabId`：缺失或 ≤ 0 时归入第 3 页。
- `orderId`：缺失时在其所属标签页内按原顺序顺排（从 1 起，跳过已占用的号）。
- `currency`：支持对象式 `{ "type": ..., "amount": ... }` 与字符串式 `"type:amount"`；`null` 保留。
- `fromItems` / `toItems`：元素支持对象式与字符串式 `"modid:name:meta:amount"`（3 段写法 `"modid:name:amount"` 补 `meta=0`，2 段写法 `"modid:name"` 补 `amount=1`）。
- `cooldown` 缺省 0，`maxTrades` 缺省 -1，`bqQuestId` 缺省空字符串。
- 旧字段 `recordNBT` 不再输出（新版已不使用）。
- 单条迁移失败时打印该条序号并跳过，不影响其它条目。

## 备份与回滚

- 迁移成功后，原文件自动改名为 `nekovm_trades.json.bak`；若 `.bak` 已存在，则改名为 `nekovm_trades_<时间戳>.json.bak`。
- 回滚方法：删除生成的 `trade` 文件夹，把 `.bak` 文件改回 `nekovm_trades.json` 即可。

## 故障排查

- **闪一下就关 / 报执行策略错误**：脚本启动已带 `-ExecutionPolicy Bypass`，正常双击不受系统执行策略限制；若仍被拦，检查是否被组策略强制禁用 PowerShell。
- **提示需要 PowerShell 3.0**：当前系统 PowerShell 版本过低（Win7 自带 2.0），安装 WMF 3.0+ 后重试。
- **提示未找到 nekovm_trades.json**：确认 bat 与该 json 在同一目录（双击运行时以 bat 所在目录为准，中文路径已兼容）。
- **提示某条迁移失败并跳过**：记下打印的序号（从 1 起），打开旧 json 检查该条目的字段格式，修正后可重新运行（重新运行前建议先删掉已生成的 `trade` 文件夹）。
- **JSON 解析失败**：旧文件不是合法 JSON（常见为手改后多了/少了逗号括号），先用任意 JSON 校验工具修正。

## 注意事项

- 迁移完成后，新版游戏只读取 `trade/trades/tab_*.json`，**不再读取旧的单文件** `nekovm_trades.json`；请确认 `trade` 文件夹就位后再进游戏。
- PowerShell 输出 JSON 时会把非 ASCII 字符（如中文）转义为 `\uXXXX` 形式，这是合法的 JSON 转义，游戏读取不受影响，无需手动改回。
- 重复运行脚本会基于同一个旧文件重新生成并覆盖 `tab_*.json`（会先询问确认）；若旧文件已被改名为 `.bak`，请先把 `.bak` 改回原名再运行。
