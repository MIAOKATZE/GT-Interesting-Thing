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
