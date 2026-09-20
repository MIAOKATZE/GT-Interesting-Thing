# neko_infinity_unit（猫猫无限存储单元）物品图标设计清单

单一权威 = `manifest.py`（`PALETTE` / `OCTAGON_SPANS` / `ART`）。本文件只做说明，脚本不含散落魔数。

## 落地契约

| 项 | 值 |
| --- | --- |
| 资产路径 | `src/main/resources/assets/gtit/textures/items/neko_infinity_unit.png` |
| 尺寸 / 帧 | 16×16，静态单帧，**无 `.png.mcmeta`**（正方形静态合法；样板 `infinity_cell.png` 138px 不透明 / `infinity_fluid_cell.png` 142px） |
| 注册名 | `gtit:neko_infinity_unit`（Java 侧 `setTextureName` 逐字对应，不带 `items/` 根段） |
| 消费链 | 默认物品 atlas，不覆写 `getSpriteNumber()`，不走方块图集 |

## 母题与骨架

- 外壳：逐字沿用 `infinity_cell.png` 的八边形轮廓（`OCTAGON_SPANS`，row15 全透明，col0/col15 空）→ 同类元件一眼可辨。
- 三层读法：环 1 壳体三档灰（受光 64 / 主 40 / 背光 36,29,36）→ 环 2 通道内圈 → 空腔黑 + 中心徽记。
- 中心徽记：`miao_coin.png` 的猫脸币（圆币 + 双耳 + 双眼 + 小鼻），金色调取自币体色阶，受光用家族黄 `y#fff200`（= 旧单元中心 8px 同值，保证家族连续）。
- 三通道合一：内圈按 8/8/8 等权分三区，黑粒分隔——左上=物品蓝 `B#00a2e8`，右上=流体青 `Q#99daf6`（B 的 60% 浅调，唯一派生色），底部=源质紫 `P#c515c9`；分隔位 `(7,1)(8,1)(3,10)(12,10)`。

## 调色板（全部来自三张实图）

`S#282828 H#404040 C#241d24 o#000000 B#00a2e8 P#c515c9 y#fff200 g#fcbb23 d#f6a51e r#842e07 e#6f2502 w#fefec0` + 派生 `Q#99daf6`。
不引入包外新色相；`Q` 由 `B` 线性向白插值（`_tint`）得到，脚本断言该关系。

## 16px 可读性判据（脚本内断言，全部 PASS 才算数）

1. 轮廓逐字等于八边形骨架；不透明 138px（落在样板 138/142 同档，不糊成一团）。
2. col0/col15/row15 全透明 → 无贴边像素；整体 4-连通单组件 → 无孤立像素。
3. 三层无越界（壳 32 / 内圈 28 / 空腔 78 = 徽记 44 + 黑腔 34）。
4. 三通道各 8px、两两 8-邻域不接触、4 粒黑分隔位钉死。
5. 徽记与内圈 4-邻域不接触（至少隔 1px 黑腔）→ 不糊底。
6. 币体受光/背光亮差 0.70（阈值 0.5）；三通道最小 RGB 色差 164（阈值 60）。
7. 用色 13 档（阈值 8–22），全部为登记基色或唯一派生 `Q`。
8. alpha 二值（仅 0/255），PIL 读回 16×16 RGBA。

## 运行

```bash
cd tools/artgen_catalog/neko_infinity_unit
python gen_neko_infinity_unit.py            # 双跑自检 + 写 out/neko_infinity_unit.png
python gen_neko_infinity_unit.py --land     # 写字进 src 材质目录 + 参考贴图字节不变性核验
python make_preview.py                      # out/preview_board.png（1x/4x + 槽灰底 + 色带）
```

手写 PNG 直写（IHDR/IDAT/IEND，无时间戳）→ 同输入逐字节一致；`--land` 若目标已存在且字节不同会拒绝覆盖。

## 未验证项

- 游戏内实机截图（NEI 槽 / 手持 / GUI 实际渲染）未采集——需主代理在 Java 注册落地后按 `gtnh-texture-showcase` 或实机补验。
- 与 `infinity_cell` 家族在**同一格 GUI** 里的并排观感只在预览板上确认，未在游戏内确认。

---

# R5 重绘：三套候选（本轮只出候选，**未覆写** `src/.../neko_infinity_unit.png`）

现役图被判定「信息密度过高、1x 全糊」，按 `plan/plan-promote.md` §R5 锁定的三套方向各出一张 16×16 候选。
本节权威 = `candidates_manifest.CANDIDATES`（位图清单）；色值仍复用本文件上方的 `manifest.BASE`，
**新脚本里没有任何字面 RGB**。落地（同名覆写）等用户挑定向后另批执行。

## 跑法

```bash
cd tools/artgen_catalog/neko_infinity_unit
python gen_candidates.py            # 判据自检 + 写 candidates/*.png + 打印六个 SHA256（三候选 x 双跑）
python make_preview_candidates.py   # candidates/preview_candidates.png（三候选 + 现役 + 三张参照，1x/4x 两档）
```

PNG 走 `pngwrite.py` 手写 IHDR/IDAT/IEND（无时间戳块），跨进程重跑文件 SHA 一致；
预览板标签含中文，字体取 `C:/Windows/Fonts/msyh.ttc`（缺则回落 simhei/simsun/默认位图字体），
双跑断言已覆盖，换机重跑只影响字体形状、不影响三张资产字节。

## 三套方向落成像素

| 候选 | 母题 | 关键判据（脚本内断言，实测值） |
| --- | --- | --- |
| `cand_a_minimal` 减法版 | 一枚币 + 单个通道角标 | 三色分弧全删，零通道色相；角标 = 1 枚 **8px x 1px** `y#fff200`，上下邻行净空 0.59 / 0.86；猫脸 3 痕（双眼 2x2 + 鼻 2x1）；130px / 7 档 |
| `cand_b_twotone` 两级明度三格 | 币面 120° 三等分（Y 形分格筋） | 三格被黑筋切成 **3 个独立连通胞**；只用 `w` / `g` **两级明度**（相对亮度差 0.23），零第三色相（B/P/Q 出现即 FAIL）；黑筋 32px 单连通；148px / 5 档 |
| `cand_c_casing` 元件感优先 | 存储单元外壳为主，猫币退为徽记 | 外壳 98px = 徽记 32px 的 **3.1 倍**；开窗 32px 整块，徽记四邻只允许徽记或开窗（不许贴外壳）；162px / 9 档 |

## 调色板派生的硬证据

`gen_candidates.ref_colors()` 运行时读三张实图（只读），取全部不透明 RGB 集合；
三张候选用到的**每一个色值都必须逐字命中该集合**，否则直接 FAIL。当前命中：
`y o S H C` <- infinity_cell，`g d r e w` <- miao_coin。
本轮三张候选**不使用**任何通道色相（物品蓝 `B` / 流体青 `Q` / 源质紫 `P`）——多通道语义改由
几何（A 的一枚角标、B 的三胞分格、C 的开窗徽记）承载，这正是 §R5 三套方向的共同减法。

## 共同纪律的量化门

- 结构：16x16、row0/row15/col0/col15 全透明、不透明像素 4-连通单组件、**无 1px 孤岛色块**
  （每种色的 8-连通块 >= 2px）、**无孤立透明像素**（透明 4-连通团必须触边，闭腔在抗锯齿下会变脏点）、
  alpha 二值、无 `.png.mcmeta`。
- 低密度：不透明 120-175px、用色 <= 10 档（样板 `infinity_cell` 138px / `infinity_fluid_cell` 142px）。
- 一眼可辨：三候选两两整图逐通道平均绝对差（MAD，0-255）原尺寸 >= 40、2x2 缩小后 >= 30。
  实测 A/B 43.4 -> 34.8，A/C 45.2 -> 41.2，B/C 54.8 -> 45.3。
- 1px 细线风险：只有 A 的角标是 1px 高（方向本身要求）。2x2 缩小（8x8，最坏情况）后
  它与最近的非角标块仍差 **23.9**（门限 20）；GUI 槽 1x 原生纹素下不存在该缩小，故保留 1px。
  这是「1px 角标」方向的已知代价，未偷偷改成 2px。

## 非目标不变（本轮已核）

脚本每次运行前后对 `src/.../items/` 的四张贴图取 SHA 并断言不变；本轮结束值：
`neko_infinity_unit.png 5876882c…029e2`（与用户判定为丑的那张逐字节相同，未被覆写）、
`infinity_cell.png 074e5950…955ec5`、`infinity_fluid_cell.png 3eb2a4c0…1d9bba`、
`miao_coin.png 65c73db9…696612`。

## 本轮未验证项

- 三候选的**游戏内**观感（NEI 槽 / 手持 / GUI 缩放）未采集，只有预览板 1x/4x 与槽灰底两档。
- 用户挑选结果未定：本节不构成任何「已选定」结论，`src` 材质目录保持原样，等主代理发起选向。
- B 的「三格等分」是几何等分（120° Y），非按通道权重等分；若用户要求某通道视觉优先需重排清单。
- C 的徽记猫眼为 1x2 竖 slit（猫眼瞳孔），缩小后并入币体色，只作 4x 细节，不作 1x 判据。

---

# R6 收敛落地：候选 C 改为动态图标（已同名覆写 `src`）

用户定案 = 候选 C「元件感优先」（`candidates/cand_c_casing.png`，SHA256 `3f253e2d…1742389`）
+ 要求「动态材质，不要静态单帧」。本节权威 = `animation_manifest.py`（帧数 / frametime / 三通道格位表 /
币面呼吸位表），形状基准逐字复用 `candidates_manifest.C_CASING`，色值仍只来自 `manifest.PALETTE`。

## 落地契约（覆盖第一节「静态单帧」口径）

| 项 | 值 |
| --- | --- |
| 资产 | `src/main/resources/assets/gtit/textures/items/neko_infinity_unit.png` **16x192 = 12 帧垂直帧带**（同名覆写旧 16x16 静态图，旧图指纹 `5876882c…`） |
| 侧车 | 同目录新增 `neko_infinity_unit.png.mcmeta` = `{"animation":{"frametime":3}}`（写法对齐仓内两例 `miao_coin` / `reincarnation_crystal`；无 `frames` 白名单 = 自动全帧循环） |
| 节奏 | 单帧 3 tick = 150ms，整环 36 tick = **1.8s**，每通道 0.6s，币面呼吸 3 次/环 |
| 注册 | 无需改 Java：`ItemNekoInfinityStorageUnit` 已是 `gtit:neko_infinity_unit`，物品图集拼接器按同名 mcmeta 自动动画 |

## 动画语义（呼应 lang 里已写的「三通道合一 · 物品 / 流体 / 源质」）

开窗内三格顺次点亮：**左上=物品蓝 B → 右上=流体青 Q → 底部=源质紫 P**（方位沿用旧静态图 `manifest.ART`
的三通道语义），峰值帧 f02 / f06 / f10 等距 4 帧；每格三档 0 熄 / 1 半亮(2px) / 2 满亮(4px)，
用「同色覆盖面积」而不是新色档表达亮度 → 零新增色相。币面微光（币体中轴 3-5 粒 `g`→`y` 家族黄，
y 的登记语义就是「新币体受光」）与当前被供能的通道同档呼吸。f00 / f04 / f08 为全暗拍点。

- **第 0 帧逐字 = cand_c**：脚本按 PNG 指纹断言，玩家先看到的正是选定那张图，动画只是它「通电」。
- **无缝**：档位取以 12 为周期的环形三角包络，接缝 f11→f00 位变 5px = 环内最大相邻位变 5px（断言 ≤）。
- **形状零改动**：12 帧不透明位集合逐帧相同（162px/帧），外壳 `SHC`、币体轮廓、猫脸 `e`、高光 `w`
  全帧逐位不变；全图只有清单登记的 17 个位可变（12 个开窗 `o` 位 + 5 个币体 `g` 位）。

## 判据与跑法

```bash
cd tools/artgen_catalog/neko_infinity_unit
python -B gen_animation.py           # 6 类判据 + 双跑 SHA256 + 写 output/animation/{png,mcmeta}
python -B gen_animation.py --land    # 追加同名覆写 src，并断言 items 目录只有这两文件变化
python -B make_preview_animation.py  # candidates/preview_animation.png（逐帧 1x/4x + 接缝行 + 参照并排 + 色板）
```

`-B` 只为不落 `__pycache__`。新增判据：通道色永不贴碰猫脸阴刻 `e`；无 1px 孤岛（半亮档刻意用 2px）；
缩小 2x（8x8 最坏情况）后三格所在块平均色位移 满亮 98.5 / 154.2 / 52.4、半亮 49.2 / 77.1 / 26.2
（门限 20 / 10）。

## 非目标不变（落地时已核）

`infinity_cell.png 074e5950…` / `infinity_fluid_cell.png 3eb2a4c0…` / `miao_coin.png 65c73db9…`
与两枚既有 mcmeta（`miao_coin.png.mcmeta dbee29d5…`、`reincarnation_crystal.png.mcmeta 0bd43575…`）
逐字节不变；`items/` 其余 15 张同样不变（脚本对整目录做前后快照并断言越界为空）。

## 本节未验证项

- 游戏内实机播放（NEI 槽 / 手持 / GUI 里的 1.8s 循环）未采集，只有预览板 1x/4x 两档。
- 12 帧里有 7 帧互不相同（升/降档复用同形），是包络的自然结果，非缺陷；若要求「每帧唯一」需改成
  非对称包络并重排清单。


