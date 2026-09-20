"""neko_infinity_unit 动态物品图标的动画清单（单一权威：帧数 / frametime / 点亮位表）。

基准 = `candidates_manifest.C_CASING`（用户选定的候选 C「元件感优先」），本文件只登记
「哪些既有像素允许在哪些帧改变」，不新画任何形状：
  * 外壳 SHC / 币体轮廓 / 猫脸阴刻 e / 高光 w —— 全帧逐位不变；
  * 允许变化的只有两类：开窗内 3 个通道格的 `o` 位（点亮为 B/Q/P）与币体 2x2 的 `g` 位
    （点亮为家族黄 y = 币面微光）。
色符一律复用 `manifest.PALETTE`（逐字来自 infinity_cell / infinity_fluid_cell / miao_coin
三张实图，Q 为 manifest 已登记的唯一派生浅调），本文件不含任何字面 RGB。
坐标一律 (x=列, y=行)，'.'=透明，与位图清单同口径。
"""
from __future__ import annotations

import candidates_manifest as CM
import manifest as M

BASE_FRAME = CM.C_CASING            # 第 0 帧基准（逐字 = cand_c）
SIZE = CM.SIZE                      # 16
FRAME_COUNT = 12                    # N：垂直帧带 16 x (16*12) = 16x192
FRAMETIME = 3                       # tick/帧；1 tick = 50ms

# 三通道格：开窗内币体四周的既有 `o` 空位（左上=物品 / 右上=流体 / 底部=源质，
# 与旧静态图 manifest.ART 的三通道方位语义一致）。每格 4 位，满亮度三格等权。
CHANNELS = (
    {"key": "item", "hue": "B", "cn": "物品",
     "cells": ((4, 4), (5, 4), (4, 5), (5, 5)),   # 左上 2x2 角袋
     "dim": ((4, 4), (4, 5))},                    # 半亮 = 外列（先不贴币体）
    {"key": "fluid", "hue": "Q", "cn": "流体",
     "cells": ((10, 4), (11, 4), (10, 5), (11, 5)),  # 右上 2x2 角袋（左右镜像）
     "dim": ((11, 4), (11, 5))},
    {"key": "essentia", "hue": "P", "cn": "源质",
     "cells": ((6, 11), (7, 11), (8, 11), (9, 11)),  # 底部 4x1 承座条
     "dim": ((7, 11), (8, 11))},                     # 半亮 = 居中 2px
)

# 峰值帧等距 4 帧（三角包络半宽 2）-> f0 / f4 / f8 为「全暗拍点」，第 0 帧逐字等于 cand_c
PEAK_FRAME = (2, 6, 10)
RAMP_HALF = 2


def level(idx: int, frame: int) -> int:
    """第 idx 个通道格在第 frame 帧的点亮档：0=熄 / 1=半亮(2px) / 2=满亮(4px)。

    以 FRAME_COUNT 为周期的环形三角包络（对任意 frame 取模，天然首尾相接）
    -> 循环无缝由构造保证。
    """
    d = (frame - PEAK_FRAME[idx]) % FRAME_COUNT
    d = min(d, FRAME_COUNT - d)
    return max(0, RAMP_HALF - d)


# 币面微光呼吸：币体中轴 `g` 位随「当前被供能的通道」同档点亮为家族黄 y
# （y 在 manifest 里的登记语义就是「新币体受光」，不引入新色相）。
# 半亮档取「高光下方的 3 格斜带」而不是 2 格横带：只取 (7,7)(8,7) 会把 (8,6) 那粒 g
# 孤立成 1px speck（缩小后变脏点），取 3 格斜带则 y 与 g 两侧都保持连通。
COIN_GLOW_HUE = "y"
COIN_GLOW = {1: ((8, 6), (7, 7), (8, 7)),
             2: ((8, 6), (7, 7), (8, 7), (7, 8), (8, 8))}


def coin_level(frame: int) -> int:
    """币面呼吸档 = 本帧三通道格的最高档（一枚元件轮流供三通道 -> 币体随之呼吸）。"""
    return max(level(i, frame) for i in range(len(CHANNELS)))


# 全帧允许被改写的位（供 gen_animation.py 做「只动这些位」的逐位断言）
def mutable_cells() -> dict[tuple[int, int], str]:
    out: dict[tuple[int, int], str] = {}
    for ch in CHANNELS:
        for c in ch["cells"]:
            out[c] = "o"
    for cells in COIN_GLOW.values():
        for c in cells:
            out[c] = "g"
    return out


PALETTE = M.PALETTE
