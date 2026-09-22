# POCKET_C2_progress · 蒸馏横向进度条材质（C2 契约第 13 个 token）设计与验收记录

- 目录：`tools/artgen_catalog/neko_dimension_pocket/`
- 渲染入口（唯一）：`python -B gen_pocket_gui_progress.py`（自检 + 写 `out/gui/`）
  → `python -B gen_pocket_gui_progress.py --land --i-have-authorization`（落地那一个目标文件）
- 交付物：`src/main/resources/assets/gtit/textures/gui/pocket/POCKET_C2_progress.png`（108×36，193 B）
- 消费方：`src/main/java/com/miaokatze/gtit/gui/pocket/NekoPocketEssenceColumn.java` 的 `progressBar()`
  （经 `PocketGuiTextures.PROGRESS` ← `PocketGuiTextureContract` 第 13 行）
- 轮次：R83 批 B2（裁定 D-8）；基线 `master` / `f71af8c` / `v1.8.28` + 批 A 已落地

---

## 0. 为什么这张图的脚本不住在 `pocket_gui/`（先说破例，免得被当成纪律松动）

同风格的 12 张 C2 装饰贴图由 `tools/artgen_catalog/pocket_gui/gen_pocket_gui.py` 生成。本片**不能**用那一套：

| 卡点 | 实测位置 |
|---|---|
| 契约解析器把 tex 数组钉死在 HTML 的 355-366 行，第 13 行落在区间外即 `ContractError` | `pocket_gui/contract.py:24`（`TEX_LINE_LO, TEX_LINE_HI = 355, 366`）、`:326-330`（起始行不符就抛） |
| 生成/自检只遍历自己那张 SHEETS 表，多出来的 token 进不去 | `pocket_gui/gui_manifest.py`（`SHEETS`）+ `gen_pocket_gui.py:325-333`（`assert (t.w, t.h) == (ct.w, ct.h)` 逐表对账） |
| 要放开就得改 `plan/assest/pocket-ui-mockups-2.html` 与 `pocket_gui/**` | 两者都不在本片可写面（HTML 是跨片材质单源） |

破例的方式是**只破目录、不破单源**：脚本 `import` 兄弟目录的 `contract.py`（该文件自述为
「C2 装饰贴图契约的唯一解析入口」）取颜色，`import` `palette.py` 取 `tint/shade/contrast` 公式，
几何回 HTML 现读；PNG 编码器 `pngwrite.py` 两份实测逐字节相同（`diff` 无输出）。
**`pocket_gui/**` 与 `plan/assest/*.html` 全程零写入**（见 §5 的 git 读数）。
把这一行折回 `pocket_gui/` 的 manifest/contract 属批 D 的收尾项（要同时动 HTML 契约）。

## 1. 尺寸（不写字面量，回契约现读）

| 量 | 值 | 来源 |
|---|---|---|
| 进度槽 | 108×18 | `plan/assest/pocket-ui-mockups-2.html:261` `push({ col:'aspect', x:0, y:180, w:108, h:18, kind:'bar', role:'prog', mui:'ProgressWidget', name:'蒸馏进度条 108×18' })` —— 全文件 `kind:'bar'` 恰一条（`grep -c` 实测 = 1），脚本断言"恰一条"，多一条即抛 |
| 交叉核对 | `h == GRID` | 同文件 `var GRID = ...` 由 `contract.py` 的 `Contract.spec` 解析；条高必须恰为一格栅格高（18）⇒ 与 Java 侧 `NekoPocketEssenceColumn.SEPARATOR_HEIGHT = NekoPocketPanel.GRID` 同源 |
| 描边厚度 | 1 px | `C2.paint.prog` 的 `border:1px solid`，经 `contract.Decl.border_w` 取；脚本断言 `BAR_H > 2*FRAME`（描边不吃掉条心） |
| 交付图 | 108×36 | MUI2 `ProgressWidget.texture(单张堆叠图, imageSize)` 的规定：**上半空槽、下半满条**（内部 `getSubArea(0,0,1,0.5)` / `getSubArea(0,0.5,1,1)`）⇒ 一根 108×18 的条 = 两根 108×18 叠起来，各半按 1:1 画进 widget 区域，**不纵向拉伸** |

Java 侧契约行：`PocketGuiTextureContract` 第 13 行 `new Spec("POCKET_C2_progress", 108, 36, -1, false)`
（`sliceMargin = -1` = 非 9-slice；`tiled = false`）。消费方静态块把「token 名 ↔ 契约宽 ↔ 右列宽」三者对账，
不一致就在装配期抛 `IllegalStateException`（不是靠注释自觉）。

## 2. 色板（零字面 RGB）

| 档 | 值（实跑打印） | 来源 |
|---|---|---|
| `BODY` 满条体 | `#a8895a` = (168,137,90) | `C2.paint.prog` 的 `background`，经 `CONTRACT.hexa("prog","bg",0)` |
| `BORDER` 描边（两半共用） | `#6b4a2f` = (107,74,47) | 同一条的 `border:1px solid`，经 `CONTRACT.hexa("prog","border",0)` |
| `DEEP` 空槽内壁顶影 | (48,33,21) | `shade(BORDER, 0.45)` |
| `FLOOR` 空槽底 | (66,46,29) | `shade(BORDER, 0.62)` |
| `HI` 满条上缘 | (192,170,136) | `tint(BODY, 0.28)` |
| `LO_MID` / `LO` 满条下缘两档 | (141,115,76) / (118,96,63) | `shade(BODY, 0.84)` / `shade(BODY, 0.70)` |

- 手段只有 `tint`/`shade`（兄弟目录 `palette.py` 认可的两种），参数登记在
  `gen_pocket_gui_progress.py` 的 `DERIV`；折回 `pocket_gui/` 时这几档要搬进 `palette.DERIV`。
- **强调色（铜高光 `#e6b878`，`palette.ACCENT_RGB`）0 命中**：C2 的设计文本把铜高光只给可点物，
  进度条不是可点物；这条由脚本机检（`assert ACCENT not in seen`）。
- 用色档数机检：实际用色 ⊆ 登记用色（`COLORS`），实跑 `用色共 7 档`（2 基色 + 5 派生）。

## 3. 像素规则（两半各 18 行，行号是带内局部 y）

```
EMPTY（上半，progress 未覆盖处可见）        FULL（下半，随 progress 从左往右露出）
  y=0        BORDER                          y=0        BORDER
  y=1        DEEP     ← 内壁顶影（凹槽信号）  y=1        HI       ← 上缘受光
  y=2..16    FLOOR                           y=2..14    BODY
  y=17       BORDER                          y=15       LO_MID
                                             y=16       LO
                                             y=17       BORDER
x=0 / x=107 一律 BORDER（两半同色 ⇒ 裁切边界不跳色）；alpha 恒 255。
```

三条硬规则及其理由：

1. **逐行横向均匀**（除左右描边）。`ProgressWidget` 的可见推进是**按 UV 裁切**
   （`ProgressWidget.java:83-106`，`Direction.RIGHT` 时 `u1 = progress`、`width *= progress`），
   任何随 x 变化的像素都只在接近 100% 时才出现 ⇒ 会变成"快满才亮一下"的伪影。
   ⇒ 不做刻度、不做流光、不做"前沿高亮列"（前沿就是裁切线本身，靠 `BODY` 与 `FLOOR` 的对比读）。
   脚本把这一条钉成机检：`_uniform()` 对两半逐行断言内区颜色集合大小 == 1。
2. **两半描边同色同厚**。空槽与满条共用外框 ⇒ 填充推进时外框不闪。
3. **凹槽与条体靠明度分层，不靠色相**。`DEEP < FLOOR` 给"这是个坑"的读法；
   `LO < BODY < HI` 给"这是根凸出来的条"的读法。脚本断言两条阶梯单调。

## 4. 非目标与不变量

- 不画帧带：本 token 是静态件。用户说的"动画"在 MUI2 里 = 按 tick 连续推进的填充
  （平滑或按像素步进，由 `ModularUIConfig.smoothProgressBar` 决定）；真要帧带得另开资产面 + 另立 token。
- 不动盘上既有 12 张：落地步骤先记录目录内全部 PNG 的 SHA256，写完再记一次，
  断言"变化的文件集合 ⊆ {POCKET_C2_progress.png}"（实跑输出「该目录其余 12 张逐字节未变」+ 13 行 SHA 表）。
- 不写 `src/**`：脚本唯一的仓库内写入目标是那一个 PNG。

## 5. 实测读数

双跑（两次独立进程、`--land` 各一次）：

```
RUN-1 本跑 SHA256 = 529f4aa7d810dde8d81ed175e5bec3dbfb5f9175d23f84313b126e6948b5e556
RUN-2 本跑 SHA256 = 529f4aa7d810dde8d81ed175e5bec3dbfb5f9175d23f84313b126e6948b5e556
盘上文件 SHA256   = 529f4aa7d810dde8d81ed175e5bec3dbfb5f9175d23f84313b126e6948b5e556  bytes = 193
```

同一次进程内的两次编码也相等（脚本内 `assert twice == png`）。其余 12 张的 SHA256 两次完全一致
（`bindbtn f35640f5… / btn ebaf4628… / btn_pressed 8361d9b7… / cloth 5a084c89… / coinbar f4c46ef3… /
corner 0ca92515… / panel 92885008… / rivet da7b3d79… / rope 52cde27c… / scrollbar bb0e8bd7… /
slot dcc848b7… / slot_tall b34ec9ee…`）。

`git status --porcelain src/main/resources/assets/gtit/textures/` 实跑只有一条：

```
?? src/main/resources/assets/gtit/textures/gui/pocket/POCKET_C2_progress.png
```

自检全绿读数（每次运行都打印）：几何 108×18 ⇒ 图 108×36、描边 1px；满条/空条逐行横向均匀；
明度阶梯单调；对比度 满条体 vs 空槽底 = 3.90（门 1.60）、描边 vs 满条体 = 2.42（门 1.30）；
用色 7 档且铜高光 0 命中；双跑逐字节一致。

## 6. 已知边界（不得当成已验）

- **像素级观感只能实机判**：条是否看得见、横向推进是否读得出来、与 C2 布纹/木框是否协调、
  1px 描边在 1.7.10 的 GUI 缩放下是否发虚 —— 全部 `[未实测]`。
- 满条与空槽的对比度是**按 WCAG 相对亮度算的机器数**（3.90），不等于玩家观感。
- 值是否真在动，取决于消费侧接线（`ProgressWidget` 拿**已注册**的 `DoubleSyncValue`）；
  材质本身只保证"有东西可画"。
- 若批 D 把这一行折回 `pocket_gui/` 的 manifest，本文件与脚本要一起迁，不留两份生成入口。
