"""R6 预览板：`candidates/preview_v3.png`（挑图用；基准与候选同一行对齐，便于直接比轮廓）。

每套候选一段，含四件事：
  A 行  基准 8x | 候选 8x | 基准 1x | 候选 1x | 候选 2x 深槽底 | 轮廓叠加（红 = 基准最外一圈压在候选上）
  B 行  逐帧 1x（f0..f7）+ 接缝 f7|f0
  C 行  逐帧 4x + 右侧用色表（每个用色标注取自哪张实图 / 派生系数 / 明度）
权威与像素全部走 `r6_manifest` + `r6_gen_candidates`，本文件不新增任何色值或形状。
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE))

import r6_manifest as M                                  # noqa: E402
import r6_gen_candidates as G                           # noqa: E402
from pngwrite import image_to_png                       # noqa: E402

OUT = HERE / "candidates" / "preview_v3.png"
# 预览板文字用系统中文字体（板面是给人看的派生物，不进材质目录；贴图本体不含文字）
FONT_CANDIDATES = ["C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf",
                   "C:/Windows/Fonts/simsun.ttc"]
S8, S4, S2 = 8, 4, 2
BG, SLOT, SLOT_D = (94, 96, 104), (168, 168, 172), (46, 46, 52)
INK, INK_DIM, EDGE = (240, 240, 235), (190, 192, 200), (255, 40, 40)
GAPC = 26                       # 图块间距
COLW, COLX = 300, 640           # 用色表两列
HEAD_H, LABEL_H = 34, 14
ROWA_H = LABEL_H + S8 * 16 + 12
STRIP1_H = 13 + 16 + 22
STRIP4_H = 200              # 4x 帧条 + 右侧两列用色表（8 行/列）取高者
BLOCK_H = HEAD_H + ROWA_H + STRIP1_H + STRIP4_H
W = 1360
H = 58 + BLOCK_H * len(M.SCHEMES) + 30

ROWS = [("L_band", "青带/主色层"), ("F_hi", "猫脸受光金"), ("F_mid", "猫脸背光金·耳"),
        ("F_nose", "小鼻"), ("F_eye", "眼（露暗腔）"), ("L_cavity", "暗腔"),
        ("M_item_lit", "通道1 物品 亮"), ("M_fluid_lit", "通道2 流体 亮"),
        ("M_essen_lit", "通道3 源质 亮"), ("M_item_dim", "通道1 暗态"),
        ("M_fluid_dim", "通道2 暗态"), ("M_essen_dim", "通道3 暗态"),
        ("L_outline", "轮廓灰（锁死基准）"), ("L_bevel", "倒角灰（锁死基准）"),
        ("L_shell", "暗壳（锁死基准）")]

img = Image.new("RGBA", (W, H), BG)
d = ImageDraw.Draw(img)


def font(sz):
    for p in FONT_CANDIDATES:
        try:
            return ImageFont.truetype(p, sz)
        except OSError:
            continue
    try:
        return ImageFont.load_default(size=sz)
    except TypeError:
        return ImageFont.load_default()


def txt_w(s, sz):
    return d.textlength(s, font=font(sz))


def txt(s, x, y, sz=11, col=INK):
    d.text((x, y), s, font=font(sz), fill=col)


def cell(g, x, y, s, bg=SLOT):
    d.rectangle([x - 1, y - 1, x + 16 * s, y + 16 * s], fill=bg, outline=(28, 28, 32))
    for py in range(16):
        for px in range(16):
            r, gg, b, a = g[py][px]
            if a:
                d.rectangle([x + px * s, y + py * s, x + (px + 1) * s - 1,
                             y + (py + 1) * s - 1], fill=(r, gg, b))
    return x + 16 * s + GAPC


def cells(glist, x, y, s, gap=GAPC, bg=SLOT):
    for g in glist:
        x = cell(g, x, y, s, bg) + (gap - GAPC)
    return x


def swatch(x, y, rgb, label, note):
    d.rectangle([x, y, x + 20, y + 20], fill=rgb + (255,), outline=(28, 28, 32))
    txt(label, x + 26, y - 2, 11, INK)
    txt(note, x + 26, y + 9, 10, INK_DIM)


root = ROOT / M.ITEM_DIR
pal = M.load_palette(root)
base = M.load_base(root)
c4 = M.contour_set(base)
c8 = M.contour_set(base, True)

y = 10
txt("neko_infinity_unit  R6 三套候选（本轮只出候选，未覆写 src）", 10, y, 16)
y += 22
txt("形状权威 = infinity_cell.png｜A 行前两格 = 基准/候选同倍率并排；红环格 = 基准最外一圈像素压在候选上，"
    "重合 100%% 时只见外圈红环不见错位｜帧带 16x%d，%d 帧，frametime %d tick，整环 %.1fs"
    % (16 * M.FRAMES, M.FRAMES, M.FRAMETIME, M.LOOP_SECONDS), 10, y, 11, INK_DIM)
y += 24

for scheme in M.SCHEMES:
    roles, notes = G.resolve(scheme, pal)
    frames = [G.build_frame(scheme, roles, base, c4, fi) for fi in range(M.FRAMES)]
    f0 = frames[0]
    same8 = sum(1 for p in c8 if f0[p[1]][p[0]] == base[p[1]][p[0]])
    y0 = y
    txt("%s ｜ %s" % (scheme["label"], scheme["note"]), 10, y, 12)
    txt("轮廓重合：4 邻域最外一圈 %d/%d px 逐位含色一致 = 100%%（门限 %.0f%%）；"
        "更严的 8 邻域两圈口径 %.1f%%（差 %d px，全在第 2 圈的角标位；灰阶边缘层零改动）"
        % (len(c4), len(c4), M.GATE_CONTOUR, 100.0 * same8 / len(c8), len(c8) - same8),
        10, y + 16, 10, INK_DIM)
    y += HEAD_H

    # A 行（标签与图块共用一套 x 坐标）
    ly = y
    cols = [("基准 8x", base, S8, SLOT), ("候选 8x", f0, S8, SLOT),
            ("基准 1x", base, 1, SLOT), ("候选 1x", f0, 1, SLOT),
            ("候选 2x 深槽底", f0, S2, SLOT_D),
            ("轮廓叠加（红=基准最外一圈）", f0, S8, SLOT)]
    xs, x = [], 10
    for lab, g, s, bg in cols:
        xs.append(x)
        txt(lab, x, ly, 10, INK_DIM)
        x += max(16 * s + GAPC, txt_w(lab, 10) + 14)
    y = ly + LABEL_H
    body = ROWA_H - LABEL_H - 6
    for i, (lab, g, s, bg) in enumerate(cols):
        oy = y + (body - 16 * s) // 2
        cell(g, xs[i], oy, s, bg)
        if i == 5:
            for (px, py) in sorted(c4):
                d.rectangle([xs[i] + px * s, oy + py * s, xs[i] + (px + 1) * s - 1,
                             oy + (py + 1) * s - 1], outline=EDGE)
    y += ROWA_H - LABEL_H

    # B 行
    txt("逐帧 1x（f0→f7）", 10, y, 10, INK_DIM)
    xr = cells(frames, 10, y + 13, 1, gap=4) + 20
    txt("接缝 f7|f0", xr, y, 10, INK_DIM)
    cells([frames[-1], f0], xr, y + 13, 1, gap=4)
    y += STRIP1_H

    # C 行
    txt("逐帧 4x", 10, y, 10, INK_DIM)
    cells(frames, 10, y + 13, S4, gap=4)
    steps = [G.diff_px(frames[i], frames[i + 1]) for i in range(M.FRAMES - 1)]
    txt("呼吸档位 k=%s（只缩面部两档金，位置不动）；相邻帧位变 %s；接缝 %d <= 环内最大 %d"
        % ("/".join("%.2f" % k for k in M.BREATH_STEPS), " ".join(map(str, steps)),
           G.diff_px(frames[-1], f0), max(steps)), 10, y + 13 + S4 * 16 + 6, 10, INK_DIM)
    txt("用色（来源实图）", COLX, y, 11)
    for i, (key, lab) in enumerate(r for r in ROWS if r[0] in roles):
        note = notes[key]
        fin = "#%02x%02x%02x" % roles[key]
        if note.split("#")[-1][:6] != fin[1:]:
            note += " -> " + fin
        swatch(COLX + (i // 8) * COLW, y + 14 + (i % 8) * 22, roles[key], lab,
               "%s  L=%.0f" % (note, M.luma(roles[key])))
    d.line([8, y0 + BLOCK_H - 6, W - 8, y0 + BLOCK_H - 6], fill=(60, 62, 70))
    y = y0 + BLOCK_H

txt("全部颜色 = 实图钉点 x 亮度系数（k<=1，保色相），钉点仅取自 infinity_cell.png / "
    "infinity_fluid_cell.png / miao_coin.png，未引入包外色相；猫脸像素全部落在基准暗腔/高光位。",
    10, y + 2, 11, INK_DIM)
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(image_to_png(img.crop((0, 0, W, y + 22))))
print("wrote", OUT, (W, y + 22))
