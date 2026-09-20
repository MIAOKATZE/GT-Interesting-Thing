"""neko_infinity_unit 动态图标的验收预览板（非游戏资产）。

产物 candidates/preview_animation.png，含：
  1) 12 帧逐帧横向展开（1x / 4x 两档，另附 GUI 槽灰底档）
  2) 与 infinity_cell / infinity_fluid_cell / miao_coin 首帧并排（同 64px 显示尺寸）
  3) 「循环接缝检查」行：f08 f09 f10 f11 | f00 f01 f02 f03 相邻摆放，红线即接缝
  4) 帧表（N / frametime / 每帧三格与币面档位）与调色板派生来源
读的是 output/animation/ 的真实产物（不是内存重算），并核验 src 落地件与之逐字节相同。
双跑逐字节一致由 pngwrite 的确定性直写保证。
"""
from __future__ import annotations

import hashlib
import pathlib
import sys

from PIL import Image, ImageDraw, ImageFont

import animation_manifest as AM
import manifest as M
import pngwrite

HERE = pathlib.Path(__file__).resolve().parent
STRIP = HERE / "output" / "animation" / "neko_infinity_unit.png"
META = HERE / "output" / "animation" / "neko_infinity_unit.png.mcmeta"
ITEMS = HERE.parents[2] / "src" / "main" / "resources" / "assets" / "gtit" / "textures" / "items"
BOARD = HERE / "candidates" / "preview_animation.png"

N, SZ = AM.FRAME_COUNT, AM.SIZE
COL_W, GAP, LAB_W, PAD = 74, 8, 132, 14
BG, SLOT, INK, DIM = (24, 24, 26, 255), (139, 139, 139, 255), (236, 236, 236, 255), (150, 150, 150, 255)
CYAN_TXT, SEAM = (150, 220, 255, 255), (255, 90, 90, 255)

CJK_FONTS = ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf",
             "C:/Windows/Fonts/simsun.ttc")
PAL_SRC = {
    "S": "infinity_cell", "H": "infinity_cell", "C": "infinity_cell", "o": "infinity_cell",
    "B": "infinity_cell+fluid", "P": "infinity_cell", "y": "infinity_cell+fluid",
    "g": "miao_coin", "d": "miao_coin", "r": "miao_coin", "e": "miao_coin", "w": "miao_coin",
    "Q": "派生：B->白 60%",
}


def font(sz):
    """标签含中文 -> 优先系统 CJK TTF；缺字体只影响标签形状，不影响任何资产字节。"""
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


def frames(strip):
    return [strip.crop((0, f * SZ, SZ, (f + 1) * SZ)) for f in range(N)]


def frame_note(f):
    lv = [AM.level(i, f) for i in range(len(AM.CHANNELS))]
    on = [f"{ch['hue']}{v}" for ch, v in zip(AM.CHANNELS, lv) if v]
    return (" ".join(on) + f" 币{AM.coin_level(f)}") if any(lv) else "全暗(=cand_c)"


def cell_row(board, d, y, cells, scale, bg, notes=None, seam_after=None, lab=""):
    """一行等宽格子；seam_after = 在该下标后画接缝标记线。"""
    d.rectangle([0, y, board.width, y + SZ * scale + 22], fill=bg)
    d.text((6, y + 3), lab, font=font(11), fill=CYAN_TXT)
    for i, im in enumerate(cells):
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        x = LAB_W + i * (COL_W + GAP) + (COL_W - big.width) // 2
        board.alpha_composite(big, (x, y))
        if notes is not None:
            d.text((LAB_W + i * (COL_W + GAP), y + SZ * scale + 2),
                   notes[i], font=font(9), fill=INK if big.width > 20 else DIM)
        if seam_after is not None and i == seam_after:
            sx = LAB_W + (i + 1) * (COL_W + GAP) - GAP // 2
            d.line([(sx, y - 4), (sx, y + SZ * scale + 20)], fill=SEAM, width=2)
    return y + SZ * scale + 22


def ref_row(board, d, y):
    items = [("neko 第 0 帧（动画首帧）", Image.open(STRIP).convert("RGBA").crop((0, 0, SZ, SZ)), 4),
             ("neko 峰值帧 f02（物品格）", Image.open(STRIP).convert("RGBA").crop((0, 2 * SZ, SZ, 3 * SZ)), 4),
             ("infinity_cell.png", Image.open(ITEMS / "infinity_cell.png").convert("RGBA"), 4),
             ("infinity_fluid_cell.png", Image.open(ITEMS / "infinity_fluid_cell.png").convert("RGBA"), 4),
             ("miao_coin.png 首帧(32px)", Image.open(ITEMS / "miao_coin.png").convert("RGBA")
              .crop((0, 0, 32, 32)), 2)]
    d.rectangle([0, y, board.width, y + 64 + 22], fill=BG)
    d.text((6, y + 3), "同家族并排", font=font(11), fill=CYAN_TXT)
    for i, (name, im, sc) in enumerate(items):
        big = im.resize((im.width * sc, im.height * sc), Image.NEAREST)
        x = LAB_W + i * (COL_W * 2 + GAP) + (COL_W * 2 - big.width) // 2
        board.alpha_composite(big, (x, y))
        d.text((LAB_W + i * (COL_W * 2 + GAP), y + 66), name, font=font(9), fill=INK)
    return y + 64 + 22


def text_block(board, d, y):
    lines = [
        f"帧带 {SZ}x{SZ * N} = {N} 帧（垂直）  |  mcmeta {META.read_text(encoding='ascii').strip()}"
        f"  |  单帧 {AM.FRAMETIME * 50}ms  整环 {N * AM.FRAMETIME} tick = {N * AM.FRAMETIME / 20:.1f}s",
        "帧表（B=物品格 Q=流体格 P=源质格，数字=档位 0熄/1半亮/2满亮；币=币面微光档）：",
        "   " + "   ".join(f"f{f:02d} {frame_note(f)}" for f in range(N)),
        "语义：一枚元件轮流为三通道供能——左上物品 -> 右上流体 -> 底部源质 顺次点亮，"
        "币面微光与当前通道同呼吸；f00/f04/f08 为全暗拍点，第 0 帧逐字 = 已选定的 cand_c。",
        "接缝检查行：红线右侧即 f11 -> f00 的回接处，源质格 1 档 -> 全暗 -> 物品格 1 档，"
        "与环内相邻帧步变同量（脚本断言接缝位变 <= 环内最大位变）。",
    ]
    h = 12 + len(lines) * 16
    d.rectangle([0, y, board.width, y + h], fill=BG)
    for i, ln in enumerate(lines):
        d.text((6, y + 6 + i * 16), ln, font=font(11 if i != 2 else 10),
               fill=INK if i != 4 else (255, 242, 0, 255))
    return y + h


def palette_block(board, d, y, keys):
    step, sw = 78, 22
    d.rectangle([0, y, board.width, y + sw + 34], fill=BG)
    d.text((6, y + 2), "用色（逐字取自三张实图；Q 为 manifest 登记的 B->白 派生浅调）",
           font=font(11), fill=CYAN_TXT)
    for i, k in enumerate(keys):
        rgb = M.PALETTE[k]
        x = LAB_W + i * step
        d.rectangle([x, y + 18, x + sw, y + 18 + sw], fill=(*rgb, 255),
                    outline=(90, 90, 90, 255))
        d.text((x, y + 18 + sw + 2), f"{k} #{rgb[0]:02x}{rgb[1]:02x}{rgb[2]:02x}\n{PAL_SRC[k]}",
               font=font(9), fill=(200, 200, 200, 255))
    return y + sw + 34


def build():
    assert STRIP.is_file(), "先跑 gen_animation.py 生成帧带"
    strip = Image.open(STRIP).convert("RGBA")
    assert strip.size == (SZ, SZ * N), f"帧带尺寸 {strip.size}"
    fr = frames(strip)
    short = [f"f{f:02d}" for f in range(N)]
    notes = [f"f{f:02d} {frame_note(f)}" for f in range(N)]
    keys = ["S", "H", "C", "o", "B", "Q", "P", "y", "g", "d", "r", "e", "w"]
    width = LAB_W + N * (COL_W + GAP) + PAD
    board = Image.new("RGBA", (width, 1200), BG)
    d = ImageDraw.Draw(board)
    y = 6
    d.text((6, y), f"neko_infinity_unit 动态图标（{N} 帧动画）验收板 —— 基准 = 候选 C「元件感优先」",
           font=font(14), fill=(255, 242, 0, 255))
    y += 24
    rows = [("逐帧 1x / GUI 槽灰底", fr, 1, SLOT, short),
            ("逐帧 1x / 深色底", fr, 1, BG, short),
            ("逐帧 4x / 深色底", fr, 4, BG, notes),
            ("逐帧 4x / GUI 槽灰底", fr, 4, SLOT, short),
            ("接缝检查", [fr[i] for i in (8, 9, 10, 11, 0, 1, 2, 3)],
             4, BG, ["f08 全暗", "f09 P1", "f10 P2", "f11 P1", "| f00 全暗", "f01 B1",
                     "f02 B2", "f03 B1"])]
    for lab, cells, sc, bg, nt in rows:
        seam = 3 if lab.startswith("循环接缝") else None
        y = cell_row(board, d, y, cells, sc, bg, nt, seam, lab) + 8
    y = ref_row(board, d, y) + 8
    y = text_block(board, d, y) + 8
    y = palette_block(board, d, y, keys) + PAD
    return pngwrite.image_to_png(board.crop((0, 0, board.width, y)))


def main():
    b1, b2 = build(), build()
    assert b1 == b2, "预览板双跑不一致（存在非确定源）"
    landed = (ITEMS / "neko_infinity_unit.png").read_bytes()
    same = landed == STRIP.read_bytes()
    print("PASS 落地件与帧带逐字节相同" if same
          else "注意：src 落地件与帧带不同（未 --land 或尚未覆写）")
    BOARD.write_bytes(b1)
    print(f"BOARD {BOARD}  {len(b1)} B")
    print("board sha256 run1 =", hashlib.sha256(b1).hexdigest())
    print("board sha256 run2 =", hashlib.sha256(b2).hexdigest())
    print("PASS 预览板双跑逐字节一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
