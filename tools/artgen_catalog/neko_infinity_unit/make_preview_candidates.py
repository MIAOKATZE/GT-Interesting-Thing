"""R5 候选比选预览板（验收用，非游戏资产）：三候选 + 现役图 + 三张参照，1x / 4x 两档。

产物 candidates/preview_candidates.png 走 pngwrite 的确定性直写，双跑逐字节一致。
"""
from __future__ import annotations

import hashlib
import pathlib

from PIL import Image, ImageDraw, ImageFont

import candidates_manifest as CM
import manifest as M
import pngwrite

HERE = pathlib.Path(__file__).resolve().parent
CAND_DIR = HERE / "candidates"
ITEMS = HERE.parents[2] / "src" / "main" / "resources" / "assets" / "gtit" / "textures" / "items"
BOARD = CAND_DIR / "preview_candidates.png"

CELL_W, GAP, PAD, TITLE_H, LAB_W, ROW_LAB_H = 150, 18, 14, 44, 96, 16
BG, SLOT = (24, 24, 26, 255), (139, 139, 139, 255)

CAND_ORDER = list(CM.CANDIDATES)
COLS = [(n, CAND_DIR / f"{n}.png") for n in CAND_ORDER] + [
    ("[现役] neko_infinity_unit", ITEMS / "neko_infinity_unit.png"),
    ("infinity_cell (参照)", ITEMS / "infinity_cell.png"),
    ("infinity_fluid_cell (参照)", ITEMS / "infinity_fluid_cell.png"),
    ("miao_coin 首帧 (参照)", ITEMS / "miao_coin.png"),
]
ROWS = [("1x / GUI 槽灰底", 1, SLOT), ("1x / 深色底", 1, BG),
        ("4x / 深色底", 4, BG), ("4x / GUI 槽灰底", 4, SLOT)]
USED = ["y", "g", "d", "r", "e", "w", "o", "S", "H", "C"]
USED_SRC = {
    "y": "infinity_cell", "g": "miao_coin", "d": "miao_coin", "r": "miao_coin",
    "e": "miao_coin", "w": "miao_coin", "o": "infinity_cell", "S": "infinity_cell",
    "H": "infinity_cell", "C": "infinity_cell",
}


CJK_FONTS = ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf",
             "C:/Windows/Fonts/simsun.ttc")


def font(sz):
    """标签含中文，默认位图字体无 CJK 字形 -> 优先系统 CJK TTF，逐机确定性由双跑断言把关。"""
    for path in CJK_FONTS:
        if pathlib.Path(path).is_file():
            try:
                return ImageFont.truetype(path, sz)
            except OSError:
                continue
    try:
        return ImageFont.load_default(size=sz)
    except TypeError:  # Pillow < 10.1
        return ImageFont.load_default()


def col_images():
    out = []
    for name, path in COLS:
        im = Image.open(path).convert("RGBA")
        if im.width > 16:  # miao_coin 是 32x128 动画表，只取首帧
            im = im.crop((0, 0, 32, 32))
        out.append((name, im))
    return out


def row_strip(imgs, scale, bg):
    h = max(im.height for _, im in imgs) * scale + ROW_LAB_H
    strip = Image.new("RGBA", (LAB_W + len(imgs) * (CELL_W + GAP), h), bg)
    for i, (_, im) in enumerate(imgs):
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        strip.alpha_composite(big, (LAB_W + i * (CELL_W + GAP), 0))
    return strip


def palette_strip(width):
    step, sw = 62, 20
    strip = Image.new("RGBA", (width, sw + 44), BG)
    d = ImageDraw.Draw(strip)
    d.text((LAB_W, 2), "候选用色（逐字取自三张实图；B/P/Q 通道色本轮一律不用）",
           font=font(12), fill=(235, 235, 235, 255))
    for i, k in enumerate(USED):
        rgb = M.PALETTE[k]
        x0 = LAB_W + i * step
        d.rectangle([x0, 18, x0 + sw, 18 + sw], fill=(*rgb, 255), outline=(90, 90, 90, 255))
        d.text((x0, 18 + sw + 2), f"{k} #{rgb[0]:02x}{rgb[1]:02x}{rgb[2]:02x}\n{USED_SRC[k]}",
               font=font(9), fill=(200, 200, 200, 255))
    return strip


def build():
    imgs = col_images()
    strips = [row_strip(imgs, sc, bg) for _, sc, bg in ROWS]
    w = max(LAB_W + len(imgs) * (CELL_W + GAP) + PAD, LAB_W + len(USED) * 62 + 20)
    pal = palette_strip(w)
    h = TITLE_H + sum(s.height for s in strips) + 10 * len(strips) + pal.height + PAD
    board = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(board)
    for i, (name, _) in enumerate(imgs):
        d.text((LAB_W + i * (CELL_W + GAP), 6), name, font=font(11), fill=(255, 242, 0, 255))
        d.text((LAB_W + i * (CELL_W + GAP), 22), CM.CANDIDATES[name]["title"]
               if name in CM.CANDIDATES else "", font=font(9), fill=(190, 190, 190, 255))
    y = TITLE_H
    for (lab, _, _), st in zip(ROWS, strips):
        board.alpha_composite(st, (0, y))
        d = ImageDraw.Draw(board)
        d.text((6, y + 2), lab, font=font(11), fill=(150, 220, 255, 255))
        y += st.height + 10
    board.alpha_composite(pal, (0, y))
    return pngwrite.image_to_png(board)


def main():
    assert CAND_DIR.is_dir(), "先跑 gen_candidates.py 生成三张候选"
    b1, b2 = build(), build()
    assert b1 == b2, "预览板双跑不一致（存在非确定源）"
    BOARD.write_bytes(b1)
    print(f"BOARD {BOARD}  {len(b1)} B")
    print("board sha256 run1 =", hashlib.sha256(b1).hexdigest())
    print("board sha256 run2 =", hashlib.sha256(b2).hexdigest())
    print("PASS 预览板双跑逐字节一致")


if __name__ == "__main__":
    main()
