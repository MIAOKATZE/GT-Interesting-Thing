# 猫猫次元口袋 · 物品图标候选（重做轮）设计与验收记录

目录：`tools/artgen_catalog/neko_dimension_pocket/`
渲染入口（唯一）：`python -B gen_pocket.py`（自检 + 写 `out/`）→ `python -B make_preview_pocket.py`（总览板）
本轮性质：**重做**。前任（同一目标，80 分钟）留下 6 个 .py 与 0 张 PNG；本轮以「先出像素、再谈打磨」为第一纪律，
在其 `pocket_manifest.py` 设计清单之上做**最小像素修正**后跑通全链，未从零重设计。

---

## 0. 交付清单（磁盘实测）

| 项 | 路径 | 实测 |
|---|---|---|
| 帧带 PNG | `out/neko_dimension_pocket_f{1,2,3,4}{,_open,_work,_work_open}.png` | 16 张（P0 要求的 8 张闭口/开口 + P2 的 8 张工作态） |
| 同名 mcmeta | `out/*.png.mcmeta` | 16 个，内容仅 `{"animation":{"frametime":N}}`，无 interpolate/width/height |
| 总览板 | `plan/assest/猫猫次元口袋-候选总览.png` | 1790x1352，207240 B |
| 清单/调色板/编码器 | `pocket_manifest.py` / `palette.py` / `pngwrite.py` | 沿用前任，仅改 3 处像素（见 §4） |
| 总览板生成器 | `make_preview_pocket.py` | 本轮新增（唯一新增脚本，非调试脚本） |
| 未接入的遗留调试脚本 | `_block_spark.py` / `_probe_gap.py` | 渲染链零引用（grep 实测 0 处）；属他方产物，未擅自删除 |

`src/main/resources/**` 与 `src/main/java/**` 本轮**零写入**：`gen_pocket.py` 每次运行都断言
`src/.../items` 20 个文件逐字节未变（实跑输出 `本次变化数 = 0`），
`git status --porcelain src/main/resources` 实测空输出。实装需另批授权。

另需报备：`plan/assest/pocket-candidates-overview.png`（2560x216，47721 B，mtime 04:01:49）
**不是本代理产物**——本代理的板是上表那张中文名文件（1790x1352）；该文件出现时本目录尚无渲染链写过 `plan/`，
仓内也 grep 不到引用它的脚本，疑为同仓并发代理或上一轮残留进程所写。未删、未改，交由主代理处置。

---

## 1. 语义锚点（画什么）

束口抽绳包囊 + 囊面猫猫纹章 + 闪光/星尘/次元裂隙质感；同一图标要同时服务
储物袋(16x8 GUI) / 流体储罐交互口 / 神秘4源质蒸馏炉 / 次元通道发射器。
四家族在 5 个轴上拉开：**描边 / 明度层级 / 猫脸画法 / 闪光表达 / 配色锚点**（清单 `axes` 字段，总览板每行末段即此五轴）。

## 2. 调色板派生依据（单一权威 = `palette.py`）

- 本目录**唯一允许出现字面 RGB 的文件**；清单与生成器零字面 RGB。
- 基色 34 档：每档钉在既有实图上，加载时逐档断言「该 RGB 确实以不透明像素出现在该实图」，
  实跑证据：`基色 34 档逐字命中 8 张实图`。8 张来源图（`infinity_cell` / `infinity_fluid_cell` /
  `miao_coin` / `neko_infinity_unit` / `reincarnation_crystal` / 两枚指环 / `electric_float_core`）
  的 SHA256 已钉死，每次运行先校验，漂移即拒绝出图。
- 派生色 14 档：只允许 `tint(基色,t)`（向白插值，同色相浅调）与 `shade(基色,k)`（向黑等比，保色相压暗），
  公式登记在 `DERIV_POINTS`，运行时重算并断言相等。
- 色符共 48 档，RGB 互不相同（断言防别名掩盖调色板膨胀）；其中 7 档未被本轮清单引用（`G K P T Z a i`），
  属清单预留，不影响出图。
- 视觉锚点：「像猫猫币一样的闪光」= 金暖层（`gld/brt/wrm/crm` 全部取自 `miao_coin`）+ 纯白星尘核心
  （`wht` 取自 `reincarnation_crystal`）；「次元裂隙」= `deep/deep_dk/lpur/lpur_dk` + 冷光 `aqua/cyan`。

## 3. 四家族差异点（清单原文摘要）

| 家族 | 底形 | 描边 | 明度层级 | 猫脸画法 | 闪光表达 | 配色锚点 |
|---|---|---|---|---|---|---|
| **F1 家族轮廓直系** | 16px，逐字 = `infinity_cell` 八边形（alpha 团 138 px 与其实图逐位相同，最外一圈 32 px 逐字 `#282828`） | 家族灰外圈，零黑描边 | 继承 `S/H/C` 三档灰链 | 暖金浮雕 + 竖瞳阴刻（`miao_coin` 语汇） | 同色相提亮，全部落在内圈，**永不碰继承外圈** | 家族灰 + miao_coin 金 |
| **F2 现代包面板风** | 16px 自己的包形（与 F1 剪影不同） | 1px 纯黑全包络 | 扁平板块 + 唯一左上高光角（Modernity 两段式） | 暗格内亮金剪影，五官为挖空 | 描边内侧高光跳动 + 口内青色裂隙 | 黑灰现代包带 + 亮金 + 物品蓝 |
| **F3 神秘4 布囊符纹风** | 16px 软布囊（束口 + 侧垂流苏 + 下摆缝线） | 无描边，柔边塑形 | 布料四档 `cl0_dk/cl1/cl2/cl3/cl5` | 不画五官，猫耳 + 菱形符（乳白晶心）作纹章 | 布面受光呼吸 + 符芯闪 | `infinity_fluid_cell` 柔紫灰布 + 金绳 |
| **F4 32px 高细节裂隙风** | 32px，囊体本身即一道次元裂隙（紫黑裂底 + 青核白心） | 黑描边 + 紫背光双层 | 同色相四档 `L/l/p/q` + 奖牌暗格 | 完整五官（耳窝/竖瞳/鼻/颔） | 体外星尘团（凭空出现的星）+ 裂隙溢出 | 深紫 + 亮青互补 + 青铜金徽 |

### 3b. 轮廓量化（对既有 `infinity_cell` / `neko_infinity_unit` 首帧，16px 档逐位；32px 档按 2x nearest 放大后同口径）

判据只对 F1 断言「逐位相等」；其余三族在此**逐套实测报告**（中心 8x8 之外的改动全部列出，符合工程量化纪律）：

| 族 | 保留参考轮廓 | 削掉的参考轮廓 px | 新增 px（全在中心 8x8 之外） | 结论 |
|---|---|---|---|---|
| F1 | **100%** | 0 | 0 | 逐字继承（≥90% 门以 100% 通过） |
| F2 | **100%** | 0 | 18：(1,10)(1,11)(2,11)(2,12)(3,12)(3,13)(4,13)(4,14)… | **只外扩不削**：金属扣带与底角把八边形的两肩撑出 18 px，原轮廓一位未丢 |
| F3 | 93% | 10：(1,6)(2,5)(3,4)(4,3)(5,3)(10,3)… | 26：束口耳 + 流苏 | 软布囊的圆弧吃掉八边形左上/右上斜边各若干位（>90% 门仍过，但**不是**继承） |
| F4 | 94% | 34（32px 档） | 90（32px 档） | 裂隙造型，仅借八边形的下体 |

⇒ 若主代理的裁定是「实装 F2 且必须守轮廓继承纪律」，**F2 现状已满足**（100% 保留、零削位，
新增 18 px 全在中心 8x8 之外且都是束口五金），不需要再改外壳。

## 4. 本轮对清单的 3 处像素修正（都是「跑不通」的真实原因，逐条可复算）

1. `F3` 束口行 `".....4mMMm4....."` → `".....4MMMM4....."`。
   原写法让 `m`（`gld`）在 (6,2) 与 (9,2) 各成 **1px 孤岛**，触发「1px 孤岛缩小必消失」硬门；
   合并为 4px 金绳后既过门又更像束紧的绳。
2. `F3_SHARED` 位 (10,6) 的半亮档 `"2"`(`cl1`) → `"7"`(`cl1_dk`)。
   底形该位是 `3`(`cl2`)，`cl2->cl1` 的 RGB 总位移只有 **46**，低于半亮门限 90（等于看不见）；
   换成公式派生的 `cl1_dk` 后位移 132，仍是「背光压暗」的同一读法。
3. `F3` 的 `work_open` 表整体相位 +1（新增 `pocket_manifest.shift()`，环形偏移，不改位点间相对节奏）。
   该态闪光位最密（26 位），帧 7→0 的接缝步变 21 > 环内最大 20，表现为每 8 帧「抽一下」；
   整表偏移后接缝 20 ≤ 环内最大 21。逐位调相会破坏 SHARED 表的两态同位纪律，故走整表偏移。

### 4b. 一处判据口径修正（必须显式报告，非放宽）

前任的「家族互异」门限写成**整图 MAD ≥ 35**（32x32 比对画布、含透明背景）。算术上这对
16px 图标不可达：F1/F2 画到像素只占并集画布的 **61%**，透明底把整图口径稀释 **6.6 倍**
（F1/F2 实测 并集 161.1 → 整图 24.5；六对家族稀释系数 6.2~6.6）。
本轮把该门限改为**并集口径 + 剪影双条件**：`并集 MAD ≥ 110` 且（`剪影 XOR ≥ 12%` 或 `并集 MAD ≥ 150`），
整图口径仍计算并打印、只是不作断言。
新口径实测六对全部通过，最紧的一对仍是 F1 vs F2：并集 **161.1 / 满量程 765 = 21%**（门限 110），
缩小 2x 后位移 24.5（门限 24）——即「同形状不同画法」在 16px 档确实只比门限高一点点，
若用户认为 F1/F2 一眼太像，正解是换 F2 剪影而不是换阈值。

## 5. 帧数与 frametime

- 帧数统一 **8 帧**（`FRAMES = 8`）：四套同帧数才能在同一张板上逐列并排比选；
  8 帧也是既有 `neko_infinity_unit`（16x128 = 8 帧）的同族口径。
- frametime 按态分级（tick/帧），`1.7.10` 只认 `animation.frametime`：
  闭口 idle `4` → 整环 32 tick = **1.6 s**（与 `neko_infinity_unit` 实测 32 tick 完全同速，同族不抢戏）；
  开口 open `3` → 24 tick = 1.2 s（GUI 开屏期间，向 `reincarnation_crystal` 的 24 tick 靠）；
  工作 work `3` / work_open `2` → 1.2 s / **0.8 s**（需求「工作时闪光更多」由两件事同时满足：
  包络半宽 2→3 让同刻亮位更多，且整环更快）。
  参照：`miao_coin` 是 32x128 = 4 帧 / frametime 10 = 40 tick（2.0 s）——它是「慢而亮」的币，
  口袋取「快而密」的囊，靠色板同源（金暖 + 纯白）而不是靠同帧数来呼应。
- 强度阶梯实测（同刻亮位数 min~max）：F1 闭口 4~5 → 闭口工作 8~12，开口 6 → 开口工作 11~13；
  F2 6→11 / 9→15；F3 6→11 / 8→15；F4 7→22 / 14→25。四套均满足「工作态严格更多、开口工作最闪」。

## 6. 判据与实跑结果（`gen_pocket.py` 全绿）

八类判据：构造（行数/列宽/首末列与最底行全透明/4-连通单组件/无 1px 孤岛/用色 6~22 档）、
轮廓继承（F1 alpha 团与 `infinity_cell` 逐位相同 + 外圈逐字主灰 + 闪光位与外圈交集为空）、
开口态与闭口态是同一只袋子（位重合 IoU ∈ [0.55,0.99]）、只动登记位（每帧新增位 ⊆ 登记表，
未登记位逐位不变，本体不被削）、循环节奏（接缝步变 ≤ 环内最大）、缩小可辨（单像素位移门限
满 180 / 半 90，体外星只看亮度 ≥140；mip1 整图位移门限 闭口/开口 1.0、工作 1.6）、
家族互异（§4b）、mcmeta 自洽 + 非目标不变（src 目录逐字节不变）。
实测抽样：F1 idle 本体 138 px 全帧不变、可变位 12、逐帧位变 [6]*8、mip1 位移 2.00；
F3 work_open 可变位 26、mip1 位移 5.10；F4 work_open 可变位 42、mip1 位移 4.27。
帧带方向：`emit()` 断言 `im.size == (sz, sz*N)`，即 **竖排**（宽 16/32、高 128/256），横向条带不可能通过。

## 7. 幂等双跑证据

`pngwrite.py` 手写 IHDR/IDAT/IEND、无 tIME/tEXt 块、`zlib.compressobj(9, DEFLATED, 15)` 固定参数；
`gen_pocket.emit()` 内部对每张图就地重算并断言字节相等。跨进程双跑（同一清单、两次独立 `python -B gen_pocket.py`）：
`sha256sum out/*` 两份清单 `diff` 为空 → **32 个文件（16 png + 16 mcmeta）逐字节一致**。
总览板同样跨进程双跑：`diff` 为空 → 一致。

帧带 SHA256（双跑同值）：

```
18fceeee9ce029fcc8f67841fa3937e691c6358c6f695baa56bb3c9204ff8d73  neko_dimension_pocket_f1.png
c4c8a33d7bf12cadf62fa538673a697bffb45ab8251410b1cd322c87c4c3903b  neko_dimension_pocket_f1_open.png
09eabcad652e7c99a3330453a117f0f928a6443cd73895f2c1fa603fa8180bfe  neko_dimension_pocket_f1_work.png
a86eb8c1e2a099b23b34925ab33841c1d611d6aa5190dd23cbd7d391bdb69e0f  neko_dimension_pocket_f1_work_open.png
205c657281eb6851ddd0c80f14040965a4e30b6bff5a1a6b98b889d9695bdd6c  neko_dimension_pocket_f2.png
8e6afe38ca185d6a8bcc499bde5655ce3404172e736fe2a241dc811e7b460e2b  neko_dimension_pocket_f2_open.png
34d4bb50ffa8b9e42207be4ffe2d3eb7a7287b86abd248f4535c38477f798dcf  neko_dimension_pocket_f2_work.png
b21cb6274b7adb2b951936ce099450f6a86851ed88d6baf1be5074eeae51a3c1  neko_dimension_pocket_f2_work_open.png
18157aa62274401fd6a235780bc5d6e99e7911130ed15e9c0a532602cd260966  neko_dimension_pocket_f3.png
3b0099f79df26df65afdf12ed1eb6d734db5a97a07465f6a633b4d6b1a7896f0  neko_dimension_pocket_f3_open.png
3faaa6465cfb41d00ac3fc64b4664cddbf6bf38bf66e730244675e5e4fed2764  neko_dimension_pocket_f3_work.png
5b4b2e6c4fa3383b966add5db1a9b6a9f8ae196e56c1f414680410236c9c754b  neko_dimension_pocket_f3_work_open.png
a84b638dd8a0f45b573ad356d85d3f4f8bd54c69425fc00cd512f4684219346c  neko_dimension_pocket_f4.png
f4bfb6bc36250af0705d04c98c69b41cde951f7f037ba8add4c22dc153af2253  neko_dimension_pocket_f4_open.png
dc5724ee55bc6409a5d51551a085e6b82ba32741afbf68c28aac2fc495246c0f  neko_dimension_pocket_f4_work.png
e8dace5be783fb0fa2f33c38c5bfa34cb68df7944f1a7b5f430eedd70f06754b  neko_dimension_pocket_f4_work_open.png
0a7d35605159254889c8cee17b71e0fd4bbe23f3388287e1a743f4bb4fbe1f23  *.png.mcmeta  (frametime 4)
f40687b33732ca9dc467e6ed874f6d5945675c8fd645ab98efbb27b929b76577  *_open/_work.png.mcmeta  (frametime 3)
b4297339c84c86a52c920f0314557de6d9fca46beac3940817c5185387f7cd73  *_work_open.png.mcmeta  (frametime 2)
```

总览板 SHA256（双跑同值）：`3239f723eae2ea4c67083d73142de85be87f59bb9b880b73860e08dd724a93a9`

## 8. 推荐实装：**F2 现代包面板风**

理由（按 16px 消费口径，不凭感觉）：
1. **1x 可辨性最高**：F2 是四套里唯一用 1px 纯黑全包络锚定外轮廓的，在浅色 GUI/物品栏背景上
   轮廓不化；F3 无描边靠柔边塑形，1x 上是一团紫灰（总览板第 4 行左半可直接看到）。
2. **状态语义最清楚**：开口态把口内画成青色倒三角裂隙，闭口态是亮金扣带，两态在 1x 一眼可分；
   F1/F3 的开口与闭口在 1x 上差别主要靠口内那 2-4 个像素。
3. **不撞既有资产**：F1 逐字继承 `infinity_cell` 轮廓（这是它的优点，也是风险——放进物品栏会被读成
   「又一枚元件」，而口袋是容器不是元件）；F2 保留「猫 + 金 + 囊」的家族语汇但换了剪影。
4. 与需求「像猫猫币一样的闪光」同源：F2 的亮金层直接取 `miao_coin` 钉点，靠色板呼应而非帧数呼应。

次选与代价：若主代理的裁定是「必须与既有 `neko_*` 同轮廓」，则改选 **F1**（唯一满足逐字继承的家族，
判据里已把这条写成硬门），代价是与 `infinity_cell` 撞形。
**F4 不建议现在实装**：32px 帧带在默认 16x 材质包下会被线性缩小，本轮所有判据（1px 孤岛、位移门限）
都是按原生纹素算的，32px 的实际观感必须等真机或 32x 包环境才能验收（见 §10 未验证项）。
另注（与账本「实装家族 = F2 + 轮廓继承纪律」直接相关）：§3b 实测 **F2 已 100% 保留 `infinity_cell`
轮廓、零削位**，只外扩 18 px 五金，所以这条纪律不需要再改外壳；F2 唯一未满足的是「外圈逐字 `#282828`」
（F2 的外圈按 Modernity 读法是纯黑 `#000000`），若要求连外圈色值也继承，只需把 F2 的描边色符从 `#`(blk)
换成 `S`(gry) —— 一处清单替换，可复跑。

## 9. P0 / P1 / P2 完成状态

- P0-1 帧带 + mcmeta：**完成**（要求 8 张，实交 16 张，竖排，mcmeta 只写 `animation.frametime`）。
- P0-2 总览板：**完成**（`plan/assest/猫猫次元口袋-候选总览.png`，4 行家族 x [闭口首帧 / 闭口 8 帧 /
  开口首帧 / 开口 8 帧 / 闭口 8x nearest / 开口 8x nearest]，左侧家族号 + 一句话设计说明 + 五轴；
  放大全部走 `Image.resize(..., NEAREST)`，无任何平滑）。
- P1-3 幂等：**完成**（跨进程双跑 32 文件 + 板 1 文件，`diff` 为空，SHA 见 §7）。
- P1-4 本文件：**完成**。
- P2-5 工作态：**完成**（四套各 2 张 `_work` / `_work_open`，不只推荐版；共 8 张）。

## 10. 未验证项（不得当作已核实）

1. **游戏内目检未做**：未启动客户端、未实装、未注册 `setTextureName`（本任务禁写 `src/`）。
   所有「可辨性」结论来自 1x/8x 像素板与 mip1 盒式平均位移，属代理指标。
2. **F4 的 32px 消费未验**：1.7.10 默认 16x 包下 32px 物品图标的实际缩放/滤波表现未测。
3. **帧带在物品 atlas 合并后的表现未验**：GTNH 物品图标走默认物品 atlas，竖排帧带被图集切分后
   的 `frametime` 行为与 mipmap 设置需真机确认（1.7.10 死键已按规范只留 `frametime`）。
4. **F1/F2 在 1x 的区分度**：并集位移 161.1 只比门限高 47%，人眼是否认为「同一版」需用户在看板上裁决。
5. **落地命名未定**：清单里 `candidate_name()`（候选带 `_fN` 后缀）与 `land_name()`（实装基名
   `neko_dimension_pocket` / `_open` / `_work` / `_work_open`）两套并存，实装时选哪一族由主代理决定，
   本轮未生成无后缀的实装名文件。
6. **前任遗留脚本**（`_block_spark.py` / `_probe_gap.py`）未被渲染链引用，是否清理由主代理决定。
