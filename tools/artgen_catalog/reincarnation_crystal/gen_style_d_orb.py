#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gtit 轮回水晶 · 候选风格 D：晶球核（静态 16x16）

设计语言：蓝紫玻璃球体，左上高光弧 + 右下透射热点，中心白核 +
八角虹彩光环，LCG 气泡微晶。底形 = 正球。

幂等再生：MANIFEST 常量 + LCG 纯函数，绘制代码零硬编码色值。
边界：仅写本目录 output/；目录自洽，不 import 主管线。
"""
import hashlib
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------- MANIFEST：全部 t 值 / 种子 / 几何常量 ----------------
MANIFEST = {
    "name": "style_d_orb",
    "size": 16,
    "seed": 20260908,
    "out_rel": ("output", "style_d_orb.png"),
    "min_opaque": 80,
    "palette": {
        "outline": (0x26, 0x24, 0x50),
        "lit": (0xEE, 0xF6, 0xFF),
        "body": (0xC2, 0xD4, 0xF6),
        "mid": (0x8F, 0xA8, 0xE8),
        "deep": (0x5A, 0x72, 0xC8),
        "rim": (0x3C, 0x4A, 0x9C),
        "iri_magenta": (0xFF, 0x5F, 0xD0),
        "iri_cyan": (0x52, 0xF5, 0xE8),
        "iri_gold": (0xFF, 0xD4, 0x5E),
        "sparkle": (0xFF, 0xFF, 0xFF),
    },
    "cx": 7.5,
    "cy": 7.5,
    "r_body": 6.2,
    "r_rim": 5.3,
    "light": (-0.6, -0.8),
    "bands": {"lit": 0.45, "body": 0.05, "mid": -0.40},
    "hot_dir": (0.7071, 0.7071),
    "hot_band": (4.2, 5.2),
    "hot_dot": 0.70,
    "nucleus_cells": ((7, 8), (8, 8), (7, 9), (8, 9)),
    "halo_c": (8.0, 9.0),
    "halo_in": 1.8,
    "halo_out": 2.8,
    # 八角位（idx = int((atan2+pi)/(pi/4))：0=W,2=N,4=E,6=S）-> 虹彩色键序号
    "halo_octants": (0, 1, 1, 2, 1, 2, 2, 0),
    "iri_order": ("iri_magenta", "iri_cyan", "iri_gold"),
    "shine_cells": ((4, 3), (4, 4), (4, 5), (3, 4), (5, 4)),
    "bubble_candidates": ((10, 5), (11, 9), (5, 11), (10, 12)),
    "bubble_pick": 2,
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


def outline_pass(canvas, n, outline_key):
    for y in range(n):
        for x in range(n):
            if canvas[y][x] is None:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if nx < 0 or ny < 0 or nx >= n or ny >= n or canvas[ny][nx] is None:
                    canvas[y][x] = outline_key
                    break


def main():
    m = MANIFEST
    n = m["size"]
    pal = m["palette"]
    cv = [[None] * n for _ in range(n)]
    lx, ly = m["light"]
    # 1) 球体分带
    for y in range(n):
        for x in range(n):
            dx, dy = x - m["cx"], y - m["cy"]
            d = math.sqrt(dx * dx + dy * dy)
            if d > m["r_body"]:
                continue
            if d > m["r_rim"]:
                cv[y][x] = "rim"
                continue
            s = -(lx * dx + ly * dy) / d
            if s > m["bands"]["lit"]:
                cv[y][x] = "lit"
            elif s > m["bands"]["body"]:
                cv[y][x] = "body"
            elif s > m["bands"]["mid"]:
                cv[y][x] = "mid"
            else:
                cv[y][x] = "deep"
    # 2) 全轮廓描边
    outline_pass(cv, n, "outline")
    # 3) 右下透射热点（光穿玻璃聚焦）
    hx, hy = m["hot_dir"]
    for y in range(n):
        for x in range(n):
            if cv[y][x] is None:
                continue
            dx, dy = x - m["cx"], y - m["cy"]
            d = math.sqrt(dx * dx + dy * dy)
            if m["hot_band"][0] < d <= m["hot_band"][1]:
                if (dx * hx + dy * hy) / d > m["hot_dot"]:
                    cv[y][x] = "lit"
    # 4) 白核 + 八角虹彩光环
    for x, y in m["nucleus_cells"]:
        cv[y][x] = "sparkle"
    hcx, hcy = m["halo_c"]
    for y in range(n):
        for x in range(n):
            d = math.sqrt((x - hcx) ** 2 + (y - hcy) ** 2)
            if m["halo_in"] < d <= m["halo_out"]:
                alpha = math.atan2(y - hcy, x - hcx)
                oct_ = int((alpha + math.pi) / (math.pi / 4.0)) % 8
                cv[y][x] = m["iri_order"][m["halo_octants"][oct_]]
    # 5) 左上玻璃高光十字
    for x, y in m["shine_cells"]:
        cv[y][x] = "sparkle"
    # 6) LCG 气泡微晶
    for x, y in lcg_pick(m["bubble_candidates"], m["bubble_pick"], m["seed"]):
        if cv[y][x] is not None:
            cv[y][x] = "mid"
    # 渲染
    img = Image.new("RGBA", (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            t = cv[y][x]
            px[x, y] = pal[t] + (255,) if t is not None else (0, 0, 0, 0)
    out = os.path.join(HERE, *m["out_rel"])
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    with open(out, "rb") as fh:
        digest = hashlib.sha256(fh.read()).hexdigest()
    print("%s size=%dx%d bytes=%d sha256=%s" % (
        m["name"], n, n, os.path.getsize(out), digest))


if __name__ == "__main__":
    main()
