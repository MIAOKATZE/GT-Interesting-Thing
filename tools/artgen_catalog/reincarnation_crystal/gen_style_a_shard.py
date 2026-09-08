#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gtit 轮回水晶 · 候选风格 A：六棱晶柱（静态 16x16）

设计语言：清澈蓝白多面晶簇，左受光/右背光棱面 + 横向断口分晶，
虹彩顶点（品红/青/金）+ LCG 抽取星芒；底形 = 竖向棱柱晶簇。

幂等再生：MANIFEST 常量 + LCG 纯函数，绘制代码零硬编码色值。
边界：仅写本目录 output/；目录自洽，不 import 主管线。
"""
import hashlib
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------- MANIFEST：全部 t 值 / 种子 / 几何常量 ----------------
MANIFEST = {
    "name": "style_a_shard",
    "size": 16,
    "seed": 20260908,
    "out_rel": ("output", "style_a_shard.png"),
    "min_opaque": 70,
    "palette": {
        "outline": (0x23, 0x23, 0x4A),
        "lit": (0xE6, 0xFB, 0xFF),
        "body": (0xB5, 0xE8, 0xF7),
        "mid": (0x7C, 0xC4, 0xE8),
        "deep": (0x4B, 0x8F, 0xC4),
        "iri_magenta": (0xFF, 0x5F, 0xD0),
        "iri_cyan": (0x52, 0xF5, 0xE8),
        "iri_gold": (0xFF, 0xD4, 0x5E),
        "sparkle": (0xFF, 0xFF, 0xFF),
    },
    # 中央主晶柱每行 (y, x_left, x_right)
    "central_rows": [
        (1, 8, 8), (2, 7, 8), (3, 7, 9), (4, 6, 9), (5, 6, 10), (6, 6, 10),
        (7, 5, 10), (8, 5, 11), (9, 5, 11), (10, 4, 11), (11, 4, 11),
        (12, 4, 11), (13, 5, 10),
    ],
    "left_rows": [
        (5, 3, 3), (6, 3, 3), (7, 2, 4), (8, 2, 4), (9, 2, 4),
        (10, 2, 3), (11, 2, 3), (12, 2, 3), (13, 2, 3),
    ],
    "right_rows": [
        (7, 13, 13), (8, 12, 13), (9, 12, 13), (10, 12, 13),
        (11, 12, 13), (12, 12, 13), (13, 12, 13),
    ],
    "break_rows": (6, 10),
    "iri_points": (
        (8, 1, "iri_magenta"), (4, 11, "iri_cyan"), (10, 12, "iri_gold"),
        (3, 5, "iri_cyan"), (13, 7, "iri_gold"),
    ),
    "sparkle_candidates": ((8, 3), (8, 8), (6, 9), (10, 11)),
    "sparkle_pick": 2,
}

DARKEN = {"lit": "body", "body": "mid", "mid": "deep"}


def lcg_next(state):
    return (state * 1103515245 + 12345) & 0x7FFFFFFF


def lcg_pick(cands, count, seed):
    """部分 Fisher-Yates：确定性抽取 count 个候选。"""
    pool = list(cands)
    rng = seed
    for i in range(count):
        rng = lcg_next(rng)
        j = i + rng % (len(pool) - i)
        pool[i], pool[j] = pool[j], pool[i]
    return pool[:count]


def new_canvas(n):
    return [[None] * n for _ in range(n)]


def draw_shard_rows(canvas, rows):
    """主晶柱：左缘亮、中段 body、右半 mid、两缘 deep（后续描边覆盖）。"""
    for y, xl, xr in rows:
        cx = (xl + xr) // 2
        for x in range(xl, xr + 1):
            if x == xl or x == xr:
                canvas[y][x] = "deep"
            elif x == xl + 1 and x <= cx:
                canvas[y][x] = "lit"
            elif x <= cx:
                canvas[y][x] = "body"
            else:
                canvas[y][x] = "mid"


def draw_side_rows(canvas, rows):
    """侧晶柱：左缘 deep、右缘 mid、中段 body。"""
    for y, xl, xr in rows:
        for x in range(xl, xr + 1):
            if x == xl:
                canvas[y][x] = "deep"
            elif x == xr and xr > xl:
                canvas[y][x] = "mid"
            else:
                canvas[y][x] = "body"


def apply_breaks(canvas, bounds, break_rows):
    """横向棱面断口：该行整体降一档。"""
    for y in break_rows:
        xl, xr = bounds[y]
        for x in range(xl, xr + 1):
            t = canvas[y][x]
            if t in DARKEN:
                canvas[y][x] = DARKEN[t]


def outline_pass(canvas, n, outline_key):
    """4 邻接透明即描边（全轮廓暗线）。"""
    for y in range(n):
        for x in range(n):
            if canvas[y][x] is None:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if nx < 0 or ny < 0 or nx >= n or ny >= n or canvas[ny][nx] is None:
                    canvas[y][x] = outline_key
                    break


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
    cv = new_canvas(n)
    draw_shard_rows(cv, m["central_rows"])
    draw_side_rows(cv, m["left_rows"])
    draw_side_rows(cv, m["right_rows"])
    bounds = {y: (xl, xr) for y, xl, xr in m["central_rows"]}
    apply_breaks(cv, bounds, m["break_rows"])
    outline_pass(cv, n, "outline")
    for x, y, key in m["iri_points"]:
        cv[y][x] = key
    for x, y in lcg_pick(m["sparkle_candidates"], m["sparkle_pick"], m["seed"]):
        if cv[y][x] is not None:
            cv[y][x] = "sparkle"
    img = render(cv, pal, n)
    out = os.path.join(HERE, *m["out_rel"])
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    with open(out, "rb") as fh:
        digest = hashlib.sha256(fh.read()).hexdigest()
    print("%s size=%dx%d bytes=%d sha256=%s" % (
        m["name"], n, n, os.path.getsize(out), digest))


if __name__ == "__main__":
    main()
