"""预览板（验收用，非游戏资产）：新单元 vs 两张旧单元 vs 猫猫币首帧；1x + 4x + GUI 槽灰底 4x。"""
from __future__ import annotations

import hashlib
import pathlib

from PIL import Image, ImageDraw, ImageFont

import manifest as M

HERE = pathlib.Path(__file__).resolve().parent
ITEMS = HERE.parents[2] / "src" / "main" / "resources" / "assets" / "gtit" / "textures" / "items"
OUT = HERE / "output" / "neko_infinity_unit.png"
BOARD = HERE / "output" / "preview_board.png"

CELL, GAP, PAD, TITLEH, LABH = 160, 24, 16, 22, 16
BG, SLOT = (24, 24, 26, 255), (139, 139, 139, 255)
TITLES = ["neko_infinity_unit (NEW)", "infinity_cell", "infinity_fluid_cell", "miao_coin frame0 (32px)"]
PALETTE_ORDER = ["S", "H", "C", "o", "B", "Q", "P", "y", "g", "d", "r", "e", "w"]


def font(sz):
    try:
        return ImageFont.load_default(size=sz)
    except TypeError:  # Pillow < 10.1
        return ImageFont.load_default()


def cell_images():
    return [
        Image.open(OUT).convert("RGBA"),
        Image.open(ITEMS / "infinity_cell.png").convert("RGBA"),
        Image.open(ITEMS / "infinity_fluid_cell.png").convert("RGBA"),
        Image.open(ITEMS / "miao_coin.png").convert("RGBA").crop((0, 0, 32, 32)),
    ]


def row(imgs, scale, bg=BG):
    """每格固定宽 CELL，图按最近邻放大后左上对齐（保像素网格）。"""
    h = 16 * scale
    strip = Image.new("RGBA", (len(imgs) * (CELL + GAP), h + LABH), bg)
    for i, im in enumerate(imgs):
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        strip.alpha_composite(big, (i * (CELL + GAP), 0))
    return strip


def palette_strip(w):
    step, sw = 58, 22
    strip = Image.new("RGBA", (w, sw + 18), BG)
    d = ImageDraw.Draw(strip)
    d.text((0, 2), "palette (manifest-derived)", font=font(12), fill=(200, 200, 200, 255))
    for i, k in enumerate(PALETTE_ORDER):
        rgb = M.PALETTE[k]
        x0 = 150 + i * step
        d.rectangle([x0, 2, x0 + sw, 2 + sw], fill=(*rgb, 255), outline=(90, 90, 90, 255))
        d.text((x0, 2 + sw + 1), f"{k}#{rgb[0]:02x}{rgb[1]:02x}{rgb[2]:02x}", font=font(10),
               fill=(200, 200, 200, 255))
    return strip


def main():
    assert OUT.exists(), "先跑 gen_neko_infinity_unit.py"
    imgs = cell_images()
    rows = [(row(imgs, 1, SLOT), "1x on GUI slot gray"),
            (row(imgs, 4), "4x nearest (dark bg)"),
            (row(imgs, 4, SLOT), "4x nearest (slot gray bg)")]
    strip_h = 22 + 18
    w = max(len(imgs) * (CELL + GAP), 150 + len(PALETTE_ORDER) * 58 + 8)
    h = TITLEH + sum(r.height for r, _ in rows) + 10 * len(rows) + strip_h + PAD
    board = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(board)
    for i, t in enumerate(TITLES):
        d.text((i * (CELL + GAP), 4), t, font=font(12), fill=(235, 235, 235, 255))
    y = TITLEH
    for r, lab in rows:
        board.alpha_composite(r, (0, y))
        d = ImageDraw.Draw(board)
        d.text((2, y + r.height - LABH), lab, font=font(11), fill=(255, 242, 0, 255))
        y += r.height + 10
    strip = palette_strip(w)
    board.alpha_composite(strip, (0, y))
    board.save(BOARD)
    print("BOARD ", BOARD)
    print("board sha256", hashlib.sha256(BOARD.read_bytes()).hexdigest())
    print("asset sha256", hashlib.sha256(OUT.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
