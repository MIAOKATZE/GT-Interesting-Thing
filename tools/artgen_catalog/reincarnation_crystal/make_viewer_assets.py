# -*- coding: utf-8 -*-
"""viewer 资产生成：动画款 strip → 逐帧 GIF 预览（帧时按 mcmeta 换算）。
幂等：双跑产物字节一致（PIL GIF 确定性编码）。帧时换算：1 tick = 50ms。
B: frametime=2 → 100ms/帧，全帧 0..7；C: frametime=3 → 150ms/帧，frames=[0,1,2,3,2,1]；
FINAL: frametime=1 → 50ms/帧，全帧 0..23（自旋线性循环，1.2s/圈，与游戏内一致）。
"""
import os
from PIL import Image

BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "output")
STYLES = os.path.join(BASE, "styles")

JOBS = [
    # (strip 文件名, 输出 gif 路径, 帧序(帧索引列表), 每帧 ms)
    ("style_b_ring.png", os.path.join(STYLES, "style_b_ring", "reincarnation_crystal.gif"),
     list(range(8)), 100),
    ("style_c_spiral.png", os.path.join(STYLES, "style_c_spiral", "reincarnation_crystal.gif"),
     [0, 1, 2, 3, 2, 1], 150),
    ("final_crystal.png", os.path.join(STYLES, "final", "reincarnation_crystal.gif"),
     list(range(24)), 50),
]

for strip_name, dst, order, ms in JOBS:
    strip = Image.open(os.path.join(OUT, strip_name)).convert("RGBA")
    w, h = strip.size
    assert w == 16 and h % 16 == 0, strip_name
    total = h // 16
    assert all(0 <= i < total for i in order), strip_name
    frames = [strip.crop((0, i * 16, 16, (i + 1) * 16)) for i in order]
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    # RGBA 帧直接交 PIL 处理透明索引量化（P 模式手工 putalpha 会触发 wrong mode）
    frames[0].save(dst, save_all=True, append_images=frames[1:], loop=0,
                   duration=[ms] * len(order), disposal=2)
    print("WROTE", dst, "frames=%d ms=%d" % (len(order), ms))
