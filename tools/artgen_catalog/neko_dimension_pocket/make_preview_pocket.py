"""候选总览板：--board 分流（幂等，读 out/ 帧带不重算像素）。

  candidates  旧四族（F1..F4，用户已否掉形态与明度，仅留档复跑）
  bundles     返工轮 B 族（含定稿轮 B4G）家族 x 四态（行=家族、列=四态）——交用户挑选的主板
  silhouettes 16px 黑白剪影对照板（旧 F2/F3 vs 新 B 族 + B4G）——证伪「水滴形」与「改了形状」的量化对照
  b4g         定稿轮专属板：B4G 四态 x 逐帧展开（1x/8x 两档）+ 逐对帧间差异率 + B4 底形对照
  all         三块新板（默认）

读 `out/` 里已生成的帧带 PNG，不重算像素 -> 与 gen_pocket 同源，永不出现「板与货不一致」。
放大一律 Image.resize(..., NEAREST)：像素硬边，禁止任何平滑/抗锯齿。
文字用系统 CJK 字体（缺失时回落 PIL 默认点阵，并打印警告），PNG 字节走 pngwrite -> 双跑一致。

用法: python -B make_preview_pocket.py [--board=candidates|bundles|silhouettes|b4g|all]
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
BOARD_BUNDLES = ROOT / "plan" / "assest" / "猫猫次元口袋-包裹候选总览.png"
BOARD_SIL = ROOT / "plan" / "assest" / "猫猫次元口袋-包裹剪影对照.png"

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


STATE_ORDER = ("idle", "open", "work", "work_open")
STATE_HEAD = {"idle": "闭口 static", "open": "开口 open",
              "work": "工作中 work", "work_open": "开口工作 work_open"}


def build_bundles() -> bytes:
    """返工轮主板：行 = B1..B4 家族，列 = 四态；每格 = 首帧 8x + 8 帧 1x 展开。"""
    fams = [f for f in PM.FAMILIES if f.get("bundle")]
    lab_w, z_w, strip_w, gap = 340, 132, 8 * 40 + 8, 14
    width = lab_w + (z_w + strip_w + gap) * 4 + gap
    row_h = 250
    height = HDR_H + row_h * len(fams) + 44
    board = Image.new("RGBA", (width, height), BG + (255,))
    d = ImageDraw.Draw(board)
    d.text((gap, 12), f"猫猫次元口袋 · 包裹形候选总览（{len(fams)} 家族 x 四态 · 底形=扎绳方包 · "
                      f"明度对齐金币；末行 B4G = 用户裁定金暖定稿轮）", font=F_TITLE, fill=ACC)
    d.text((gap, 44), "家族 / 设计语言 / 量化", font=F_SMALL, fill=TXT)
    for si, st in enumerate(STATE_ORDER):
        bx = lab_w + si * (z_w + strip_w + gap)
        d.text((bx + 4, 40), STATE_HEAD[st], font=F_HEAD, fill=TXT)
        d.text((bx + 4, 58), "首帧 8x / 8 帧 1x", font=F_SMALL, fill=DIM)
    d.line([(0, HDR_H - 6), (width, HDR_H - 6)], fill=LINE)

    for r, fam in enumerate(fams):
        y0 = HDR_H + r * row_h
        sz = fam["size"]
        d.rectangle([0, y0, width, y0 + row_h - 1], fill=PANEL + (255,))
        m = PM.parcel_metrics(fam["grid"]["normal"])
        d.text((gap, y0 + 10), f"{fam['tag']}  {fam['label']}", font=F_HEAD, fill=ACC)
        yy = y0 + 36
        for line in wrap(fam["note"], F_SMALL, lab_w - 2 * gap)[:5]:
            d.text((gap, yy), line, font=F_SMALL, fill=TXT)
            yy += 16
        d.text((gap, yy + 2), f"顶结 {m['flat_top']} 行 / 宽行占比 {m['square']:.2f} / 平底 "
               f"{m['bottom']:.2f} / 长宽比 {m['aspect']:.2f}", font=F_SMALL, fill=DIM)
        d.text((gap, yy + 18), f"{sz}px · {PM.FRAMES} 帧竖排 · frametime "
               f"{'/'.join(str(PM.FRAMETIME[s]) for s in STATE_ORDER)}", font=F_SMALL, fill=DIM)
        for si, st in enumerate(STATE_ORDER):
            name = PM.candidate_name(fam["id"], st)
            src = Image.open(OUT / f"{name}.png").convert("RGBA")
            assert src.size == (sz, sz * PM.FRAMES), f"{name} 帧带尺寸 {src.size}"
            bx = lab_w + si * (z_w + strip_w + gap)
            paste(board, frame(src, 0, sz), bx + 2, y0 + 10, z_w)
            for f in range(PM.FRAMES):
                paste(board, frame(src, f, sz), bx + z_w + f * 40 + 4, y0 + 10, 36)
            d.line([(bx - 4, y0), (bx - 4, y0 + row_h - 1)], fill=LINE)
    y = HDR_H + len(fams) * row_h + 10
    d.text((gap, y), "口径：本板只读 out/（与 gen_pocket 断言同源）；实装切换由主代理在用户选定后执行，"
                    "本轮不写 src/。放大全部 NEAREST，无平滑。", font=F_SMALL, fill=DIM)
    return pngwrite.image_to_png(board)


SIL_Z = 8          # 剪影板 8x nearest
SIL_ROWS = [       # (展示名, 家族 id, 定性)
    ("旧 F2（已否：水滴+偏暗）", "f2_modernity_panel", "old"),
    ("旧 F3（已否：束口收拢）", "f3_thaum_cloth", "old"),
    ("新 B1 方布包十字绳", "b1_gold_cross_bundle", "new"),
    ("新 B2 竖纸包斜带", "b2_paper_diag_string", "new"),
    ("新 B3 折叠包+封签", "b3_folded_wrap_tag", "new"),
    ("新 B4 硬壳包+提手", "b4_hardcase_handle", "new"),
    ("B4G 同底形·金暖（掩码差 0）", "b4g_gold_hardcase", "new"),
]


def _sil_tile(g, fg, bgc):
    """字符底形 -> 16x16 二值块（不透明=fg，透明=bgc）。"""
    im = Image.new("RGBA", (16, 16), bgc + (255,))
    dp = im.load()
    for y, row in enumerate(g):
        for x, c in enumerate(row):
            if c != ".":
                dp[x, y] = fg + (255,)
    return im


def build_silhouette() -> bytes:
    """16px 黑白剪影对照板：证「包裹形 vs 水滴形」，附 PARCEL_GATE 实测数。"""
    gt = PM.PARCEL_GATE
    lab_w, cell, txt_w, gap = 250, 16 * SIL_Z, 430, 16
    width = lab_w + (cell + gap) * 2 + txt_w
    row_h = cell + 28
    height = 56 + row_h * len(SIL_ROWS) + 30
    board = Image.new("RGBA", (width, height), (250, 250, 252, 255))
    d = ImageDraw.Draw(board)
    d.text((gap, 10), "16px 剪影对照板：行=家族，列=闭口/开口；黑=不透明。"
                      f"门限：顶结≤{gt['flat_top_max']}行 / 宽行占比≥{gt['wide_frac_min']} / "
                      f"平底≥{gt['bottom_min']} / 长宽比≤{gt['aspect_max']}", font=F_HEAD,
           fill=(20, 20, 24))
    d.line([(0, 46), (width, 46)], fill=LINE)
    for r, (label, fid, kind) in enumerate(SIL_ROWS):
        fam = PM.family(fid)
        y0 = 56 + r * row_h
        ok_all = True
        parts = []
        for key in ("normal", "open"):
            m = PM.parcel_metrics(fam["grid"][key])
            good = (m["flat_top"] <= gt["flat_top_max"] and m["square"] >= gt["wide_frac_min"]
                    and m["bottom"] >= gt["bottom_min"] and m["aspect"] <= gt["aspect_max"])
            ok_all &= good
            parts.append(f"{'闭' if key == 'normal' else '开'} 顶结{m['flat_top']} "
                         f"宽{m['square']:.2f} 底{m['bottom']:.2f} 比{m['aspect']:.2f}")
        col = (20, 120, 30) if (ok_all and kind == "new") else \
            (190, 30, 30) if kind == "old" else (20, 20, 24)
        d.text((gap, y0 + 4), label, font=F_BODY, fill=col)
        for ci, key in enumerate(("normal", "open")):
            x0 = lab_w + ci * (cell + gap)
            tile = _sil_tile(fam["grid"][key], (16, 16, 20), (250, 250, 252))
            board.paste(tile.resize((cell, cell), Image.NEAREST), (x0, y0))
            for gpos in range(17):
                d.line([(x0 + gpos * SIL_Z, y0), (x0 + gpos * SIL_Z, y0 + cell)],
                       fill=(226, 226, 232))
                d.line([(x0, y0 + gpos * SIL_Z), (x0 + cell, y0 + gpos * SIL_Z)],
                       fill=(226, 226, 232))
        tx = lab_w + 2 * (cell + gap) + 8
        d.text((tx, y0 + 6), " | ".join(parts), font=F_SMALL, fill=(60, 60, 66))
        d.text((tx, y0 + 24), ("过门（读形=扎绳方包）" if ok_all else "违门（读形=收口水滴/束口袋）"),
               font=F_SMALL, fill=col)
    d.text((gap, height - 26), f"旧两族仅作反例参照（其 out/ 产物本轮逐字节未变）；其余 {len(SIL_ROWS) - 2} 族"
                               f"的量化门由 gen_pocket.check_bundle 每次运行重断言；B4G 与 B4 的掩码差异"
                               f"由 gen_pocket.check_geom_reuse 断言 = 0 位（本板两行剪影逐格相同）。",
           font=F_SMALL, fill=(90, 90, 96))
    return pngwrite.image_to_png(board)


BOARD_B4G = ROOT / "plan" / "assest" / "猫猫次元口袋-B4G金暖定稿板.png"
B4G_FAM = "b4g_gold_hardcase"
Z8 = 8 * 16                  # 8x 档单帧边长（16px 图标）


def strip_frames(name: str, size: int) -> list[list[tuple]]:
    """out/ 帧带 PNG -> 逐帧 RGBA 平铺（索引口径 i = y*size + x，与 PM.anim_stats 一致）。

    板上的所有读数都从**已交付字节**复算，不由清单重推 -> 标注与货物永不两张皮。
    """
    im = Image.open(OUT / f"{name}.png").convert("RGBA")
    assert im.size == (size, size * PM.FRAMES), f"{name} 帧带尺寸 {im.size}"
    return [list(im.crop((0, f * size, size, (f + 1) * size)).getdata())
            for f in range(PM.FRAMES)]


def build_b4g() -> bytes:
    """定稿轮专属板：B4G 四态 x 逐帧展开（1x 与 8x 两档）+ 逐对帧间差异率标注
    + B4 vs B4G 底形 8x 对照（证「改的是色与动画，不是形状」）。"""
    fam = PM.family(B4G_FAM)
    src = PM.family(fam["source"])
    sz = fam["size"]
    gt = PM.ANIM_GATE
    lab_w, gap = 470, 14
    one_w = 16 * PM.FRAMES + PM.FRAMES
    x1 = lab_w
    x8 = x1 + one_w + 18
    width = x8 + Z8 * PM.FRAMES + gap
    row_h = Z8 + 58
    hdr = 100
    y_cmp = hdr + row_h * len(STATE_ORDER) + 30
    height = y_cmp + Z8 + 96
    board = Image.new("RGBA", (width, height), BG + (255,))
    d = ImageDraw.Draw(board)

    d.text((gap, 10), "猫猫次元口袋 · 定稿轮 B4G（B4 底形 + 金暖 + 动画加强）· 四态逐帧展开",
           font=F_TITLE, fill=ACC)
    d.text((gap, 42), f"色板：壳面 brt/n + gld/m -> gld_dk2/W 背光棱（同 B1 那一族金暖，零新基色）；"
                      f"提手/铆钉换暖铜 brz + 厚边棕 rim；底形 = B4 逐位复用", font=F_SMALL, fill=TXT)
    d.text((gap, 62), f"动画门（ANIM_GATE）：相邻帧不透明像素差异率 ≥{gt['adj_min']:.0%}（逐对取最小）/ "
                      f"同格整环明度摆幅 ≥{gt['swing_min']} / 亮带净转角 ≥{gt['rev_min']} 圈每环 且"
                      f"同向步 ≥{gt['samedir_min']:.0%} / 周期 ≤{gt['period_max']}s", font=F_SMALL, fill=DIM)
    d.text((x1, 80), "1x（逐帧）", font=F_SMALL, fill=DIM)
    d.text((x8, 80), "8x nearest（逐帧 · 下方百分数 = 该帧到下一帧的不透明像素差异率）",
           font=F_SMALL, fill=DIM)
    d.line([(0, hdr - 8), (width, hdr - 8)], fill=LINE)

    for ri, st in enumerate(STATE_ORDER):
        name = PM.candidate_name(fam["id"], st)
        src_name = PM.candidate_name(src["id"], st)
        im = Image.open(OUT / f"{name}.png").convert("RGBA")
        fr = strip_frames(name, sz)
        s = PM.anim_stats(fr, sz)
        ref = PM.anim_stats(strip_frames(src_name, sz), sz)
        y0 = hdr + ri * row_h
        d.rectangle([0, y0, width, y0 + row_h - 6], fill=PANEL + (255,))
        d.text((gap - 4, y0 + 6), f"{st} · {PM.STATE_LABEL[st]}", font=F_HEAD, fill=ACC)
        d.text((gap - 4, y0 + 30),
               f"frametime {PM.FRAMETIME[st]} tick/帧 · {PM.FRAMES} 帧 = 整环 "
               f"{PM.FRAMES * PM.FRAMETIME[st] / 20.0:.1f}s（门 ≤{gt['period_max']}s）· 相位 = 顺时针极角秩",
               font=F_SMALL, fill=DIM)
        d.text((gap - 4, y0 + 46),
               f"帧间差异率：min {s['adj_min']:.1%} / 均值 {s['adj_mean']:.1%}"
               f"（同态 B4 参照 {ref['adj_min']:.1%} -> 提升 {s['adj_min'] / max(1e-9, ref['adj_min']):.1f}x）",
               font=F_BODY, fill=TXT)
        d.text((gap - 4, y0 + 66),
               f"同格摆幅：本体 min {s['swing_body_min']:.2f} / 全格 min {s['swing_all_min']:.2f}"
               f"（B4 {ref['swing_body_min']:.2f}）· 位点 {s['n_spark']}（B4 {ref['n_spark']}）",
               font=F_SMALL, fill=TXT)
        d.text((gap - 4, y0 + 84),
               f"方向性：亮带净转角 {s['rev']:+.2f} 圈/环 · 同向步 {s['samedir']:.0%}"
               f"（B4 {ref['rev']:+.2f} 圈 / {ref['samedir']:.0%} = 整体呼吸）",
               font=F_SMALL, fill=TXT)
        for f in range(PM.FRAMES):
            tile = frame(im, f, sz)
            d.rectangle([x1 + f * (16 + 1) - 1, y0 + 6, x1 + f * (16 + 1) + 16, y0 + 23],
                        fill=(8, 8, 10, 255))
            board.paste(tile, (x1 + f * (16 + 1), y0 + 7))
            paste(board, tile, x8 + f * Z8, y0 + 6, Z8)
            d.text((x8 + f * Z8 + 4, y0 + Z8 + 10), f"{f}->{(f + 1) % PM.FRAMES}",
                   font=F_SMALL, fill=DIM)
            d.text((x8 + f * Z8 + 4, y0 + Z8 + 26), f"{s['adj'][f]:.1%}", font=F_BODY,
                   fill=(ACC if s["adj"][f] >= gt["adj_min"] else (255, 90, 90)))
        d.line([(x1 - 10, y0), (x1 - 10, y0 + row_h - 6)], fill=LINE)
        d.line([(x8 - 10, y0), (x8 - 10, y0 + row_h - 6)], fill=LINE)

    d.text((gap, y_cmp - 14), "底形对照（均为 8x nearest 首帧）：B4 与 B4G 的不透明像素掩码逐位相同 —— "
                              "gen_pocket 断言掩码差异 = 0 位，逐位差异只落在登记换色映射 7 档上",
           font=F_HEAD, fill=TXT)
    for ci, key in enumerate(("idle", "open")):
        g_a = src["grid"][PM.BASE_OF[key]]
        g_b = fam["grid"][PM.BASE_OF[key]]
        chg = sum(1 for ra, rb in zip(g_a, g_b) for ca, cb in zip(ra, rb) if ca != cb)
        ops = sum(1 for ra in g_b for ca in ra if ca != ".")
        X0 = gap + ci * (2 * Z8 + 150)
        for kj, (fid, tag) in enumerate(((src["id"], src["tag"]), (fam["id"], fam["tag"]))):
            im0 = Image.open(OUT / f"{PM.candidate_name(fid, key)}.png").convert("RGBA")
            tx = X0 + kj * (Z8 + 16)
            paste(board, frame(im0, 0, sz), tx, y_cmp, Z8)
            d.text((tx + 4, y_cmp + Z8 + 2), f"{tag} {STATE_HEAD[key]}", font=F_SMALL,
                   fill=(TXT if kj else DIM))
        d.text((X0, y_cmp + Z8 + 22),
               f"不透明位 {ops} px 逐位相同（掩码差异 0）· 仅换色 {chg} 位 / {ops} 位",
               font=F_SMALL, fill=DIM)
    y = height - 30
    d.text((gap, y), "口径：1.7.10 物品动画 = 16xN*16 竖排帧带 + 同名 .mcmeta 只写 animation.frametime；"
                     "本板只读 out/，不写 src/，实装由主代理执行 --land 并逐张 SHA 对账。", font=F_SMALL, fill=DIM)
    return pngwrite.image_to_png(board)


def main() -> int:
    arg = [a for a in sys.argv[1:] if a.startswith("--board")]
    board = (arg[0].split("=", 1)[1] if arg and "=" in arg[0]
             else (sys.argv[sys.argv.index("--board") + 1] if arg else "all"))
    jobs = {"candidates": [(build, BOARD)],
            "bundles": [(build_bundles, BOARD_BUNDLES)],
            "silhouettes": [(build_silhouette, BOARD_SIL)],
            "b4g": [(build_b4g, BOARD_B4G)],
            "all": [(build_bundles, BOARD_BUNDLES), (build_silhouette, BOARD_SIL),
                    (build_b4g, BOARD_B4G)]}[board]
    for fn, out in jobs:
        out.parent.mkdir(parents=True, exist_ok=True)
        b1, b2 = fn(), fn()
        assert b1 == b2, f"{out.name} 双跑不一致（存在非确定源）"
        out.write_bytes(b1)
        im = Image.open(out)
        import hashlib
        print(f"OK {out}  {im.size[0]}x{im.size[1]}  {len(b1)}B  "
              f"SHA256 {hashlib.sha256(b1).hexdigest()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
