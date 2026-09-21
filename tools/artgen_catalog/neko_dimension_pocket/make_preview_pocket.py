"""候选总览板（P0 交付物）：4 家族 x (闭口 / 开口) x (1x 帧带 / 8x nearest 放大)。

读 `out/` 里已生成的帧带 PNG，不重算像素 -> 与 gen_pocket 同源，永不出现「板与货不一致」。
放大一律 Image.resize(..., NEAREST)：像素硬边，禁止任何平滑/抗锯齿。
文字用系统 CJK 字体（缺失时回落 PIL 默认点阵，并打印警告），PNG 字节走 pngwrite -> 双跑一致。

用法: python -B make_preview_pocket.py [--out <路径>]
默认输出 plan/assest/猫猫次元口袋-候选总览.png
"""
from __future__ import annotations

import pathlib
import sys

from PIL import Image, ImageDraw, ImageFont

import pngwrite
import pocket_manifest as PM

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = HERE / "out"
BOARD = ROOT / "plan" / "assest" / "猫猫次元口袋-候选总览.png"

BG = (28, 28, 30)
PANEL = (18, 18, 20)
LINE = (64, 64, 70)
TXT = (226, 226, 232)
DIM = (150, 150, 158)
ACC = (252, 187, 35)

LABEL_W = 372
CELL = 48                      # 1x 帧位（16px 图标留 16 边距；32px 图标留 8）
ZOOM = 8                       # nearest 放大倍率（硬门：不得模糊）
ROW_H = 8 * 32 + 56            # 最高行 = 32px 图标 8x 放大 + 上下留白
HDR_H = 64
GAP = 14

CJK_CANDIDATES = (
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
    r"C:\Windows\Fonts\NotoSansCJK-Regular.ttc",
)


def font(size: int):
    for path in CJK_CANDIDATES:
        p = pathlib.Path(path)
        if p.exists():
            try:
                return ImageFont.truetype(str(p), size)
            except OSError:
                continue
    print(f"[warn] 未找到 CJK 字体，中文标签会退化为点阵默认字体", file=sys.stderr)
    return ImageFont.load_default()


F_TITLE, F_HEAD, F_BODY, F_SMALL = font(26), font(18), font(16), font(14)


def frame(src: Image.Image, i: int, size: int) -> Image.Image:
    """从竖排帧带取第 i 帧（1.7.10 口径：竖排，帧高 = 宽）。"""
    return src.crop((0, i * size, size, (i + 1) * size))


def zoom(im: Image.Image, k: int) -> Image.Image:
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def paste(canvas: Image.Image, im: Image.Image, x: int, y: int, box: int) -> None:
    """把图按 nearest 放大到不超过 box 后居中贴入，保证像素网格对齐。"""
    k = max(1, box // im.width)
    z = zoom(im, k)
    canvas.paste(z, (x + (box - z.width) // 2, y + (box - z.height) // 2), z)


def wrap(text: str, fnt, maxw: int) -> list[str]:
    """按像素宽度折行（CJK 无空格，只能逐字量宽）。"""
    lines, cur = [], ""
    for ch in text:
        if fnt.getlength(cur + ch) > maxw:
            lines.append(cur)
            cur = ch
        else:
            cur += ch
    if cur:
        lines.append(cur)
    return lines


def build() -> bytes:
    fams = PM.FAMILIES
    col_w = [LABEL_W, CELL, CELL * PM.FRAMES, CELL, CELL * PM.FRAMES, 8 * 32 + GAP, 8 * 32 + GAP]
    width = sum(col_w) + GAP
    height = HDR_H + ROW_H * len(fams) + 40
    board = Image.new("RGBA", (width, height), BG + (255,))
    d = ImageDraw.Draw(board)

    xs = [0]
    for w in col_w:
        xs.append(xs[-1] + w)

    d.text((GAP, 14), "猫猫次元口袋 · 物品图标候选总览（4 家族 x 闭口/开口）", font=F_TITLE, fill=ACC)
    heads = ["家族 / 设计语言", "首帧", "闭口 8 帧（竖排帧带按列展开）", "首帧",
             "开口 8 帧（竖排帧带按列展开）", f"闭口 {ZOOM}x nearest", f"开口 {ZOOM}x nearest"]
    for i, h in enumerate(heads):
        col = xs[i] + (GAP if i != 1 and i != 3 else 4)
        for j, ln in enumerate(h.split("\n")):
            d.text((col, 40 + j * 12), ln, font=F_SMALL if i in (1, 3) else F_HEAD, fill=TXT)
    d.line([(0, HDR_H - 6), (width, HDR_H - 6)], fill=LINE)

    for r, fam in enumerate(fams):
        y0 = HDR_H + r * ROW_H
        sz = fam["size"]
        d.rectangle([0, y0, width, y0 + ROW_H - 1], fill=PANEL + (255,))
        idle = Image.open(OUT / f"{PM.candidate_name(fam['id'], 'idle')}.png").convert("RGBA")
        open_ = Image.open(OUT / f"{PM.candidate_name(fam['id'], 'open')}.png").convert("RGBA")
        assert idle.size == (sz, sz * PM.FRAMES), f"{fam['id']} 帧带不是竖排 {idle.size}"

        note = fam["note"]
        ax = " · ".join(f"{k} {v}" for k, v in fam["axes"].items())
        d.text((xs[0] + GAP, y0 + 12), f"{fam['tag']}  {fam['label']}", font=F_HEAD, fill=ACC)
        yy = y0 + 40
        for line in wrap(note, F_BODY, LABEL_W - 2 * GAP):
            d.text((xs[0] + GAP, yy), line, font=F_BODY, fill=TXT)
            yy += 20
        d.text((xs[0] + GAP, yy + 4), f"尺寸 {sz}x{sz} · {PM.FRAMES} 帧竖排 · "
               f"frametime 闭口 {PM.FRAMETIME['idle']} / 开口 {PM.FRAMETIME['open']} tick",
               font=F_SMALL, fill=DIM)
        yy += 24
        for line in wrap(ax, F_SMALL, LABEL_W - 2 * GAP)[:4]:
            d.text((xs[0] + GAP, yy), line, font=F_SMALL, fill=DIM)
            yy += 17

        box = min(CELL, ROW_H - 110)
        top = y0 + (ROW_H - box) // 2 - 10          # 1x 帧位垂直居中，别贴着说明文字
        paste(board, frame(idle, 0, sz), xs[1], top, box)
        paste(board, frame(open_, 0, sz), xs[3], top, box)
        for f in range(PM.FRAMES):
            paste(board, frame(idle, f, sz), xs[2] + f * CELL, top, box)
            paste(board, frame(open_, f, sz), xs[4] + f * CELL, top, box)
        paste(board, frame(idle, 0, sz), xs[5] + GAP // 2, y0 + 10, 8 * 32)
        paste(board, frame(open_, 0, sz), xs[6] + GAP // 2, y0 + 10, 8 * 32)
        for i in range(1, len(col_w)):
            d.line([(xs[i], y0), (xs[i], y0 + ROW_H - 1)], fill=LINE)

    y = HDR_H + len(fams) * ROW_H + 12
    d.text((GAP, y), "口径：1.7.10 物品动画 = 16xN*16 竖排帧带 + 同名 .mcmeta 只写 animation.frametime"
                     "（interpolate/width/height 为死键）；本板不写 src/，实装待用户选定后另批授权。",
           font=F_SMALL, fill=DIM)
    return pngwrite.image_to_png(board)


def main() -> int:
    out = pathlib.Path(sys.argv[sys.argv.index("--out") + 1]) if "--out" in sys.argv else BOARD
    out.parent.mkdir(parents=True, exist_ok=True)
    b1, b2 = build(), build()
    assert b1 == b2, "总览板双跑不一致（存在非确定源）"
    out.write_bytes(b1)
    im = Image.open(out)
    print(f"OK 总览板 {out}  {im.size[0]}x{im.size[1]}  {len(b1)}B  nearest x{ZOOM}")
    import hashlib
    print("SHA256", hashlib.sha256(b1).hexdigest())
    return 0


if __name__ == "__main__":
    sys.exit(main())
