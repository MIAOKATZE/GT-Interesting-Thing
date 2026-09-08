#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gtit 轮回水晶 · 候选风格 C：轮回双螺旋（动画 16x64 = 4 帧 ping-pong）

设计语言：紫白细臂双螺旋向心汇聚（轮回涡旋母题，1.1 匝留透明匝间沟），
臂上窄亮脉冲段随呼吸明暗同步向心移动（能量被吸入核心），2x2 白核 +
四对角虹彩点，虹彩晶砂随增益渐次点亮。底形 = 涡旋。

动画合规：帧 16x16 正方形、帧宽 16 (POT)、高 64 = 4x16 精确整除、
mcmeta = frametime 3 + frames[] ping-pong [0,1,2,3,2,1]（重复 index 合法）、
无 interpolate/width/height 死键。
幂等再生：MANIFEST 常量 + LCG 纯函数，绘制代码零硬编码色值。
"""
import hashlib
import json
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------- MANIFEST：全部 t 值 / 种子 / 几何 / 动画常量 ----------------
MANIFEST = {
    "name": "style_c_spiral",
    "size": 16,
    "frames": 4,
    "seed": 20260908,
    "out_rel": ("output", "style_c_spiral.png"),
    "mcmeta_rel": ("output", "style_c_spiral.png.mcmeta"),
    "min_opaque": 40,
    "palette": {
        "outline": (0x2E, 0x24, 0x50),
        # 三档体色各 4 级增亮变体（gain 0..3）
        "deep": [(0x6E, 0x52, 0xC8), (0x7E, 0x63, 0xD2), (0x8E, 0x75, 0xDC), (0xA0, 0x89, 0xE6)],
        "mid": [(0x9C, 0x80, 0xE8), (0xAC, 0x92, 0xF0), (0xBC, 0xA4, 0xF6), (0xCC, 0xB6, 0xFA)],
        "lit": [(0xE2, 0xD6, 0xFF), (0xE9, 0xE0, 0xFF), (0xF0, 0xE9, 0xFF), (0xF8, 0xF4, 0xFF)],
        "nucleus": (0xFF, 0xFF, 0xFF),
        # 虹彩色环：品红 / 青 / 金 / 白
        "iri": [(0xFF, 0x5F, 0xD0), (0x52, 0xF5, 0xE8), (0xFF, 0xD4, 0x5E), (0xFF, 0xFF, 0xFF)],
    },
    "cx": 7.5,
    "cy": 7.5,
    "theta0": -1.5707963,
    "sweep": 6.9115,           # 1.1 * 2pi（拉开匝间距，保透明沟）
    "arm_offsets": (0.0, 3.14159265),
    "r0": 6.0,
    "r_end": 0.9,
    "w0": 2.4,
    "w_end": 1.0,
    "samples": 700,
    "band_deep": 0.30,
    "band_lit": 0.88,          # t > band_lit 才进最亮档（只留收束点）
    # 白核 2x2（内旋臂终点处，最亮档收束）
    "nucleus_offsets": ((0, 0), (1, 0), (0, 1), (1, 1)),
    "nucleus_c": (8, 8),       # 白核 2x2 的左上格
    # 每帧脉冲段中心（t 空间）：随呼吸增益向心移动
    "pulse_centers": (0.88, 0.62, 0.38, 0.18),
    "pulse_band": 0.05,
    "pulse_core": 0.022,
    "sparkle_ts": (0.22, 0.48, 0.72, 0.92),
    "gain_levels": (0, 1, 2, 3),
    "frames_seq": (0, 1, 2, 3, 2, 1),
    "frametime": 3,
}


def lcg_next(state):
    return (state * 1103515245 + 12345) & 0x7FFFFFFF


def lcg_permutation(count, seed):
    """LCG 驱动的 Fisher-Yates 全排列（确定性）。"""
    order = list(range(count))
    rng = seed
    for i in range(count - 1):
        rng = lcg_next(rng)
        j = i + rng % (count - i)
        order[i], order[j] = order[j], order[i]
    return order


def spiral_point(m, t, offset):
    theta = m["theta0"] + offset + t * m["sweep"]
    r = m["r0"] + (m["r_end"] - m["r0"]) * t
    return m["cx"] + r * math.cos(theta), m["cy"] + r * math.sin(theta)


def brush_radius(m, t):
    w = m["w0"] + (m["w_end"] - m["w0"]) * t
    return w / 2.0 + 0.2


def sparkle_cells(m):
    """晶砂候选格：参数螺旋采样点取整（保证落在臂上）。"""
    cells = []
    for off in m["arm_offsets"]:
        for ts in m["sparkle_ts"]:
            sx, sy = spiral_point(m, ts, off)
            cells.append((int(round(sx)), int(round(sy))))
    return tuple(cells)


def draw_frame(m, gain, pulse_c, cand_order, n_visible):
    """单帧 tone-key 画布。halo/晶砂以 ('iri', 色号) 记录。"""
    n = m["size"]
    cv = [[None] * n for _ in range(n)]
    # 1) 双臂螺旋体 + 窄脉冲段：t 升序采样，内圈更亮自然覆盖
    for i in range(m["samples"]):
        t = i / float(m["samples"] - 1)
        rho = brush_radius(m, t)
        if t > m["band_lit"]:
            tone = "lit"
        elif t < m["band_deep"]:
            tone = "deep"
        else:
            tone = "mid"
        ds = abs(t - pulse_c)
        if ds < m["pulse_core"]:
            tone = "pulse"            # 脉冲核心：lit 最亮档
        elif ds < m["pulse_band"]:
            tone = "lit"              # 脉冲晕：随呼吸增益
        for off in m["arm_offsets"]:
            sx, sy = spiral_point(m, t, off)
            x_lo = max(0, int(sx - rho - 1))
            x_hi = min(n - 1, int(sx + rho + 1))
            y_lo = max(0, int(sy - rho - 1))
            y_hi = min(n - 1, int(sy + rho + 1))
            for yy in range(y_lo, y_hi + 1):
                for xx in range(x_lo, x_hi + 1):
                    d2 = (xx - sx) ** 2 + (yy - sy) ** 2
                    if d2 <= rho * rho:
                        cv[yy][xx] = tone
    # 2) 轮廓描边（4 邻接透明）
    for y in range(n):
        for x in range(n):
            if cv[y][x] is None:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if nx < 0 or ny < 0 or nx >= n or ny >= n or cv[ny][nx] is None:
                    cv[y][x] = "outline"
                    break
    # 3) 白核（内旋臂终点处）
    ncx, ncy = m["nucleus_c"]
    for ox, oy in m["nucleus_offsets"]:
        cv[ncy + oy][ncx + ox] = "nucleus"
    # 4) 虹彩晶砂：可见数随增益增长（LCG 稳定排列前缀）
    for idx in cand_order[:n_visible]:
        x, y = m["_sparkle_cells"][idx]
        cv[y][x] = ("iri", idx % 4)
    return cv


def render(canvas, pal, gain, n):
    img = Image.new("RGBA", (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            t = canvas[y][x]
            if t is None:
                px[x, y] = (0, 0, 0, 0)
            elif isinstance(t, tuple):
                px[x, y] = pal["iri"][t[1]] + (255,)
            elif t == "deep" or t == "mid" or t == "lit":
                px[x, y] = pal[t][gain] + (255,)
            elif t == "pulse":
                px[x, y] = pal["lit"][3] + (255,)
            else:
                px[x, y] = pal[t] + (255,)
    return img


def main():
    m = MANIFEST
    n = m["size"]
    pal = m["palette"]
    m["_sparkle_cells"] = sparkle_cells(m)
    cand_order = lcg_permutation(len(m["_sparkle_cells"]), m["seed"])

    strip = Image.new("RGBA", (n, n * m["frames"]))
    for f, gain in enumerate(m["gain_levels"]):
        n_vis = (gain + 1) * 2
        frame_img = render(
            draw_frame(m, gain, m["pulse_centers"][f], cand_order, n_vis),
            pal, gain, n)
        strip.paste(frame_img, (0, f * n))
    out = os.path.join(HERE, *m["out_rel"])
    os.makedirs(os.path.dirname(out), exist_ok=True)
    strip.save(out)
    mcmeta = json.dumps(
        {"animation": {"frametime": m["frametime"], "frames": list(m["frames_seq"])}},
        sort_keys=True, separators=(",", ":")) + "\n"
    mc_path = os.path.join(HERE, *m["mcmeta_rel"])
    with open(mc_path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(mcmeta)
    with open(out, "rb") as fh:
        digest = hashlib.sha256(fh.read()).hexdigest()
    print("%s strip=%dx%d frames=%d bytes=%d sha256=%s" % (
        m["name"], n, n * m["frames"], m["frames"],
        os.path.getsize(out), digest))
    print("%s mcmeta=%s sha256=%s" % (
        m["name"], mcmeta.strip(),
        hashlib.sha256(mcmeta.encode("utf-8")).hexdigest()))


if __name__ == "__main__":
    main()
