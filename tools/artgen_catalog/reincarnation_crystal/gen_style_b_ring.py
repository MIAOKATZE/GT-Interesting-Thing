#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gtit 轮回水晶 · 候选风格 B：轮回法环（动画 16x128 = 8 帧）

设计语言：清澈蓝白圆环（轮回环形母题），受光弧固定 + 虹彩彗星流光
（白头→青→品红→金尾）绕环飞驰；LCG 星尘常驻。底形 = 环形。

动画合规：帧 16x16 正方形、帧宽 16 (POT)、高 128 = 8x16 精确整除、
mcmeta 仅 frametime（显式），无 interpolate/width/height 死键。
幂等再生：MANIFEST 常量 + LCG 纯函数，绘制代码零硬编码色值。
"""
import hashlib
import json
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TAU = 2.0 * math.pi

# ---------------- MANIFEST：全部 t 值 / 种子 / 几何 / 动画常量 ----------------
MANIFEST = {
    "name": "style_b_ring",
    "size": 16,
    "frames": 8,
    "seed": 20260908,
    "out_rel": ("output", "style_b_ring.png"),
    "mcmeta_rel": ("output", "style_b_ring.png.mcmeta"),
    "min_opaque": 50,
    "palette": {
        "outline": (0x22, 0x22, 0x48),
        "lit": (0xDF, 0xF6, 0xFF),
        "mid": (0x9F, 0xD4, 0xEE),
        "deep": (0x5D, 0x9C, 0xCB),
        "iri_magenta": (0xFF, 0x5F, 0xD0),
        "iri_cyan": (0x52, 0xF5, 0xE8),
        "iri_gold": (0xFF, 0xD4, 0x5E),
        "sparkle": (0xFF, 0xFF, 0xFF),
    },
    "cx": 7.5,
    "cy": 7.5,
    "r_out": 6.4,
    "r_in": 3.4,
    "rim_out": 5.9,
    "rim_in": 3.9,
    "light": (-0.7071, -0.7071),
    "shade_lit": 0.5,
    "shade_deep": -0.2,
    "head": 0.30,
    "band1": 0.55,
    "band2": 0.85,
    "band3": 1.15,
    "frametime": 2,
    "dust_candidates": ((1, 2), (14, 3), (2, 13), (13, 12)),
    "dust_pick": 2,
}


def lcg_next(state):
    return (state * 1103515245 + 12345) & 0x7FFFFFFF


def lcg_pick(cands, count, seed):
    pool = list(cands)
    rng = seed
    for i in range(count):
        rng = lcg_next(rng)
        j = i + rng % (len(pool) - i)
        pool[i], pool[j] = pool[j], pool[i]
    return pool[:count]


def collect_ring(m):
    """分类每像素：rim（描边）/ body，附角度与半径。返回按 (y,x) 排序的列表。"""
    cx, cy = m["cx"], m["cy"]
    items = []
    for y in range(m["size"]):
        for x in range(m["size"]):
            dx, dy = x - cx, y - cy
            d = math.sqrt(dx * dx + dy * dy)
            if d < m["r_in"] or d > m["r_out"]:
                continue
            zone = "rim" if (d > m["rim_out"] or d < m["rim_in"]) else "body"
            items.append((x, y, zone, math.atan2(dy, dx), d))
    items.sort(key=lambda it: (it[1], it[0]))
    return items


def draw_frame(m, ring, dust, frame):
    cv = [[None] * m["size"] for _ in range(m["size"])]
    lx, ly = m["light"]
    phi = TAU * frame / m["frames"]
    for x, y, zone, alpha, d in ring:
        if zone == "rim":
            cv[y][x] = "outline"
            continue
        dx, dy = x - m["cx"], y - m["cy"]
        s = (dx * lx + dy * ly) / d
        if s > m["shade_lit"]:
            base = "lit"
        elif s < m["shade_deep"]:
            base = "deep"
        else:
            base = "mid"
        da = (alpha - phi + math.pi) % TAU - math.pi
        if abs(da) <= m["head"]:
            tone = "sparkle"
        elif -m["band1"] <= da < -m["head"]:
            tone = "iri_cyan"
        elif -m["band2"] <= da < -m["band1"]:
            tone = "iri_magenta"
        elif -m["band3"] <= da < -m["band2"]:
            tone = "iri_gold"
        else:
            tone = base
        cv[y][x] = tone
    for x, y in dust:
        cv[y][x] = "lit"
    return cv


def render(canvas, pal, n):
    img = Image.new("RGBA", (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            t = canvas[y][x]
            px[x, y] = pal[t] + (255,) if t is not None else (0, 0, 0, 0)
    return img


def main():
    m = MANIFEST
    n = m["size"]
    pal = m["palette"]
    ring = collect_ring(m)
    dust = lcg_pick(m["dust_candidates"], m["dust_pick"], m["seed"])
    strip = Image.new("RGBA", (n, n * m["frames"]))
    for f in range(m["frames"]):
        frame_img = render(draw_frame(m, ring, dust, f), pal, n)
        strip.paste(frame_img, (0, f * n))
    out = os.path.join(HERE, *m["out_rel"])
    os.makedirs(os.path.dirname(out), exist_ok=True)
    strip.save(out)
    mcmeta = json.dumps(
        {"animation": {"frametime": m["frametime"]}},
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
