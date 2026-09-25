"""C2 装饰贴图生产板：每张 1:1 + 4x 并排，外加真实尺寸的「面板局部样例」组装与机检读数。

只读 out/ 与契约，不写 src/；落地由 gen_pocket_gui.py --land 执行。
板上的每个数字都取自 gen_pocket_gui.self_check()（与生成期同一份机检，不手抄）。

判读重点（用户的硬要求）：空格子本身必须看得见。样例里 3x3 物品盘留 8 空 1 有货，
旁边另立一列 3 格流体槽（1 有流体 2 空），同一批空槽因此分别坐在「布底」与「列底」
两种背景上，1:1 与 3x 各一份，可直接判断"空槽是不是还在"。
"""
from __future__ import annotations

import sys
from pathlib import Path

import canvas as CV
import gen_pocket_gui_upgrades as UPG   # S1 升级格占位：token/用途/尺寸门与生成器单源
import gui_manifest as GM
import pngwrite
from contract import CONTRACT
from gen_pocket_gui import (build_all, contract_key, fmt_num, res_name,
                            self_check)
from palette import CHROME, HUES, PAL, STOCK

from PIL import Image, ImageDraw, ImageFont  # noqa: E402  (字体只服务标签，不入资产)

FONT_DIRS = (Path("C:") / "Windows" / "Fonts",)
FONT_NAMES = ("msyh.ttc", "simhei.ttf", "NotoSansCJK-Regular.ttc")


def font(size: int):
    for d in FONT_DIRS:
        for nm in FONT_NAMES:
            p = d / nm
            if p.exists():
                try:
                    return ImageFont.truetype(str(p), size)
                except OSError:
                    continue
    return ImageFont.load_default()


F_TIT, F_HDR, F_BODY, F_SMALL = font(28), font(18), font(16), font(13)

W = 1240
LABEL_W = 300
PAD = 12
DSTRETCH, HSTRETCH = 26, 10        # 9-slice 演示：两轴各加这么多再 2x 观察
ROW_MIN = 96


# ---------------------------------------------------------------- 小工具

def load_sheets() -> dict:
    sheets = {}
    for sheet in GM.SHEETS:
        p = GM.OUT / (res_name(sheet) + ".png")
        assert p.exists(), "缺产物 " + str(p) + "，先跑 gen_pocket_gui.py"
        sheets[sheet] = CV.read_png(p)
    assert len(sheets) == len(GM.SHEETS) > 0
    return sheets


def checker_im(w, h):
    return CV.to_image(w, h, CV.checker(w, h, 4, CHROME["chk_a"], CHROME["chk_b"]))


def paste(board, im, x, y, checker=False):
    if checker:
        board.paste(checker_im(im.width, im.height), (x, y))
    board.paste(im, (x, y), im)
    return x + im.width + PAD


def tile_into(dst, dw, dh, src, sw, sh, x0, y0, x1, y1):
    """把 src 按 (x0,y0) 起平铺进 dst 的闭开区间矩形（平铺件的真实用法）。"""
    for y in range(y0, y1):
        for x in range(x0, x1):
            i = (((y - y0) % sh) * sw + ((x - x0) % sw)) * 4
            if src[i + 3] == 0:
                continue
            CV.put(dst, dw, x, y, (src[i], src[i + 1], src[i + 2]), 255)


# ---------------------------------------------------------------- 逐张贴图

def sheet_row(board, dr, y, sheet, sheets, notes) -> int:
    t = CONTRACT.t(contract_key(sheet))
    w, h, buf = sheets[sheet]
    state = "" if sheet == t.key else sheet[len(t.key):]
    dr.text((PAD, y + 4), t.token + state, font=F_HDR, fill=CHROME["txt"])
    dr.text((PAD, y + 26), str(w) + "x" + str(h) + " ｜ "
            + ("9-slice N=" + str(t.n) if t.slice
               else "非 9-slice（" + ("平铺" if sheet in ("cloth", "rope") else "1:1 贴") + "）"),
            font=F_BODY, fill=CHROME["dim"])
    dr.text((PAD, y + 46), t.usage, font=F_SMALL, fill=CHROME["dim"])
    for i, note in enumerate(notes.get(sheet, ())):
        dr.text((PAD, y + 62 + i * 14), note, font=F_SMALL, fill=CHROME["warn"])

    x = LABEL_W
    x = paste(board, CV.to_image(w, h, buf), x, y, checker=True)
    x = paste(board, CV.scale_nearest(w, h, buf, GM.ZOOM), x, y, checker=True)
    if t.slice:
        dw, dh = w + DSTRETCH, h + HSTRETCH
        st = CV.to_image(dw, dh, CV.nine_slice(w, h, buf, t.n, dw, dh))
        st = st.resize((st.width * 2, st.height * 2), Image.NEAREST)
        x = paste(board, st, x, y - 6, checker=True)
    else:
        if sheet in ("corner", "rivet"):                 # 1:1 件不谈平铺，给更大倍率看像素
            x = paste(board, CV.scale_nearest(w, h, buf, 8), x, y - 6, checker=True)
        else:
            kx, ky = (4, 2) if sheet in ("cloth", "rope") else (3, 3)
            tw, th = w * kx, h * ky
            tiled = CV.blank(tw, th)
            tile_into(tiled, tw, th, buf, w, h, 0, 0, tw, th)
            x = paste(board, CV.scale_nearest(tw, th, tiled, 2), x, y - 6, checker=True)
    return max(h * GM.ZOOM, ROW_MIN) + PAD


def row_h(sheet, sheets):
    """与 sheet_row 的排版分支一一对应（两处不一致就会互相压字）。"""
    w, h, _ = sheets[sheet]
    t = CONTRACT.t(contract_key(sheet))
    if t.slice:
        tall = max(h * GM.ZOOM, (h + HSTRETCH) * 2)
    elif sheet in ("corner", "rivet"):
        tall = max(h * GM.ZOOM, h * 8)
    else:
        ky = 2 if sheet in ("cloth", "rope") else 3
        tall = max(h * GM.ZOOM, h * ky * 2)
    return max(tall, ROW_MIN) + PAD


# ---------------------------------------------------------------- S1 升级格灰化占位

UPG_ZOOM = 4


def upg_section(board, dr, y0, sheets) -> int:
    """S1 升级格灰化占位：每组 = 槽底+图案合成（真实用法）与图案单独 4x（棋盘底）。

    token 清单、用途文案、画布尺寸门全部 import 自生成器（gen_pocket_gui_upgrades），
    本函数不复制第二份；缺 out/ 产物时直接抛（提示先跑生成器）。
    """
    sw, sh, sbuf = sheets["slot"]
    dr.text((PAD, y0), "升级格灰化占位（S1）—— 底部右段行 3 的 5 个升级格，未升级时显示灰化插件图案",
            font=F_HDR, fill=CHROME["txt"])
    dr.text((PAD, y0 + 24), "每组从左到右：槽底 18×18 ＋ 图案合成 " + str(UPG_ZOOM) + "x（真实用法）｜ "
            "图案单独 " + str(UPG_ZOOM) + "x（棋盘底）。图案画在 16×16 语义区（(1,1) 起，画布外圈 1px 让位）"
            "⇒ 16×16 插件图标放入后逐像素遮盖；灰阶 = palette 冷钢三档（st_lo/st_mid/steel_lip，零字面 RGB）",
            font=F_SMALL, fill=CHROME["dim"])
    y = y0 + 46
    for token, (usage, _art) in UPG.UPGRADES.items():
        p = GM.OUT / (token + ".png")
        assert p.exists(), "缺产物 " + str(p) + "，先跑 gen_pocket_gui_upgrades.py"
        iw, ih, ibuf = CV.read_png(p)
        assert (iw, ih) == (UPG.SIZE, UPG.SIZE), (token, iw, ih)
        comp = bytearray(sbuf)                      # 槽底 + 图案 = 未升级时的真实观感
        CV.blit_over(comp, UPG.SIZE, UPG.SIZE, ibuf, iw, ih, 0, 0)
        dr.text((PAD, y + 6), token, font=F_BODY, fill=CHROME["txt"])
        dr.text((PAD, y + 26), usage, font=F_SMALL, fill=CHROME["warn"])
        x = LABEL_W
        x = paste(board, CV.scale_nearest(UPG.SIZE, UPG.SIZE, comp, UPG_ZOOM), x, y, checker=True)
        x = paste(board, CV.scale_nearest(UPG.SIZE, UPG.SIZE, ibuf, UPG_ZOOM), x, y, checker=True)
        y += UPG_ZOOM * UPG.SIZE + PAD
    return y - y0


# ---------------------------------------------------------------- 组装样例

def assemble(sheets) -> tuple[int, int, bytearray]:
    """按契约几何把家族拼成一块真实尺寸的面板局部（单位 = MC GUI 像素，栅格取自契约 SPEC）。"""
    spec = CONTRACT.spec
    grid, margin, gap = spec["GRID"], spec["MARGIN"], spec["COLGAP"]
    frame_t = sum(b[2] for b in CONTRACT.frame_bands("panel"))
    cw, ch, cbuf = sheets["cloth"]
    sw, sh, sbuf = sheets["slot"]
    tw, th, tbuf = sheets["slot_tall"]
    gw, gh, gbuf = sheets["corner"]
    rvw, rvh, rvb = sheets["rivet"]
    cob, coh, coinb = sheets["coinbar"]
    bw, bh, bbuf = sheets["btn"]
    dw, dh, dbuf = sheets["btn_pressed"]
    srb, srh, srbuf = sheets["scrollbar"]
    rpw, rph, rpbuf = sheets["rope"]

    x0 = frame_t + margin                                # 内容区左上
    rows_y = x0
    col_x = x0                                           # 流体列（3 格 slot_tall）
    grid_x = col_x + grid + gap                          # 3x3 物品盘
    txt_x = grid_x + grid * 3 + gap                      # 素布文字牌
    txt_w = grid * 3 + 4
    sb_x = txt_x + txt_w + gap                           # 拉长的 scrollbar
    rope_y = rows_y + grid * 3 + 10
    bar_y = rope_y + rph + 10
    aw = max(sb_x + srb, x0 + cob + gap + bw) + margin + frame_t
    ah = bar_y + bh + 2 + dh + margin + frame_t

    pw, ph, pbuf = sheets["panel"]
    out = CV.nine_slice(pw, ph, pbuf, CONTRACT.t("panel").n, aw, ah)
    tile_into(out, aw, ah, cbuf, cw, ch, frame_t, frame_t, aw - frame_t, ah - frame_t)

    for i in range(3):                                   # 流体列：槽底坐在 col 底上
        CV.rect(out, aw, col_x - 1, rows_y + i * grid - 1, col_x + tw,
                rows_y + (i + 1) * grid, PAL["col_wall"])
        CV.rect(out, aw, col_x, rows_y + i * grid, col_x + tw - 1,
                rows_y + (i + 1) * grid - 1, PAL["col_floor"])
        CV.blit_over(out, aw, ah, tbuf, tw, th, col_x, rows_y + i * grid)
        if i == 1:                                       # 中间那格给流体（色取自契约 fluid）
            CV.rect(out, aw, col_x + 3, rows_y + i * grid + 5, col_x + tw - 4,
                    rows_y + (i + 1) * grid - 4, PAL["fluid_floor"])

    filled = 0
    for i in range(3):
        for j in range(3):
            k = i * 3 + j
            px, py = grid_x + j * grid, rows_y + i * grid
            CV.blit_over(out, aw, ah, sbuf, sw, sh, px, py)
            if STOCK[k]:                                 # 有货格：色与有货位都取自契约
                filled += 1
                CV.rect(out, aw, px + 3, py + 3, px + sw - 4, py + sh - 4,
                        HUES[k % len(HUES)])
    assert 0 < filled < 9, "样例必须同时有空槽与有货槽才可比"

    CV.rect(out, aw, txt_x, rows_y, txt_x + txt_w, rows_y + grid * 3 - 1, PAL["text_floor"])
    CV.rect(out, aw, txt_x, rows_y, txt_x + txt_w, rows_y, PAL["wall"])
    CV.rect(out, aw, txt_x, rows_y + grid * 3 - 1, txt_x + txt_w,
            rows_y + grid * 3 - 1, PAL["wall"])
    for ln, wd in enumerate((txt_w - 12, txt_w - 26, txt_w - 8, int(txt_w * 0.6))):
        CV.rect(out, aw, txt_x + 4, rows_y + 6 + ln * 12, txt_x + 4 + wd,
                rows_y + 9 + ln * 12, PAL["text_ink"])
    CV.blit_over(out, aw, ah, CV.nine_slice(srb, srh, srbuf, CONTRACT.t("scrollbar").n,
                                            srb, grid * 3), srb, grid * 3, sb_x, rows_y)
    tile_into(out, aw, ah, rpbuf, rpw, rph, grid_x, rope_y, grid_x + grid * 3, rope_y + rph)
    CV.blit_over(out, aw, ah, coinb, cob, coh, x0, bar_y)
    CV.blit_over(out, aw, ah, bbuf, bw, bh, x0 + cob + gap, bar_y)
    CV.blit_over(out, aw, ah, dbuf, dw, dh, x0 + cob + gap, bar_y + bh + 2)
    for cx, cy in [(0, 0), (aw - gw, 0), (0, ah - gh), (aw - gw, ah - gh)]:
        CV.blit_over(out, aw, ah, gbuf, gw, gh, cx, cy)                    # 1:1 铜包角
        CV.blit_over(out, aw, ah, rvb, rvw, rvh, cx + (gw - rvw) // 2,
                     cy + (gh - rvh) // 2)                                 # 角上压一枚铆钉
    # 契约原话是"铆钉落在面板 14px 角部与列分隔上"；4px 列距塞不下 6px 铆钉，
    # 所以按硬壳箱语言排到上下左右四条框带上（框厚 7px 装得下 6px），不压槽位。
    for cx in [col_x + grid // 2, grid_x + grid * 3 // 2, txt_x + txt_w // 2]:
        CV.blit_over(out, aw, ah, rvb, rvw, rvh, cx - rvw // 2, 1)
        CV.blit_over(out, aw, ah, rvb, rvw, rvh, cx - rvw // 2, ah - frame_t)
    for cy in [rows_y + grid * 3 // 2, bar_y + coh // 2]:
        CV.blit_over(out, aw, ah, rvb, rvw, rvh, 1, cy - rvh // 2)
        CV.blit_over(out, aw, ah, rvb, rvw, rvh, aw - frame_t, cy - rvh // 2)
    return aw, ah, out


# ---------------------------------------------------------------- 拼板

def main() -> int:
    built = build_all()
    log, num = self_check(built)
    sheets = load_sheets()
    notes = {
        "slot": ("实测中心净空 " + str(num["slot_center"]) + "x" + str(num["slot_center"])
                 + "（N=3 边距）",
                 "内暗环对布底 " + fmt_num(num["slot_wall_vs_cloth"])
                 + "、对槽心 " + fmt_num(num["slot_wall_vs_floor"])),
        "slot_tall": ("实测中心净空 " + str(num["slot_tall_center"]) + "x"
                      + str(num["slot_tall_center"]), "冷色环，与物品槽分形"),
        "corner": ("逐位 90 度旋转对称 => 一张供四角", "切角透明，1:1 贴不遮木框"),
        "rivet": ("1:1 多点位复用", "掩码沿主对角镜像，无方向偏差"),
        "cloth": ("平铺判据：" + num["cloth_seam"], "零预算（接缝不得更糟）"),
        "rope": ("同一张 tile 横竖两用", "90 度旋转对称 + 每 8 格空 2 格"),
        "panel": ("框厚由契约导出 " + str(num["panel_frame_px"]) + "px <= N=10",
                  "中心留平色：织纹画在可拉伸带会被拉成条纹"),
        "coinbar": ("两铆钉坐在 4px 角块内", "角块是唯一不拉伸的区域"),
        "scrollbar": ("铜条 + 四角铆钉点", "条身全部同心环，纵向拉长不糊"),
    }
    heights = [row_h(s, sheets) for s in GM.SHEETS]
    aw, ah, samp = assemble(sheets)
    s1 = CV.to_image(aw, ah, samp)
    sz = GM.SAMPLE_ZOOM
    s3 = s1.resize((aw * sz, ah * sz), Image.NEAREST)
    samp_h = s3.height + 92
    upg_h = 46 + len(UPG.UPGRADES) * (UPG_ZOOM * UPG.SIZE + PAD)

    total = 100 + sum(heights) + 34 + upg_h + 24 + samp_h + 26 * (len(log) + len(num)) + 90
    board = Image.new("RGBA", (W, total), (*CHROME["bg"], 255))
    dr = ImageDraw.Draw(board)
    dr.text((PAD, 14), "猫猫次元口袋 MUI2 面板 ｜ C2｜铜包角束口 —— 装饰贴图生产板",
            font=F_TIT, fill=CHROME["txt"])
    lo = CONTRACT.t("cloth").line
    hi = CONTRACT.t("rope").line
    dr.text((PAD, 50), "契约单源 plan/assest/pocket-ui-mockups-2.html 第 " + str(lo)
            + "-" + str(hi) + " 行：名称 / 像素尺寸 / 是否 9-slice / N / 色值 / 框厚"
            " 全部由 contract.py 现读，脚本正文零色值字面量", font=F_BODY, fill=CHROME["dim"])
    dr.text((PAD, 72), "每组从左到右：1:1 原生 ｜ " + str(GM.ZOOM) + "x nearest ｜ 右端 = "
            "9-slice 两轴同时拉伸后再 2x（非 9-slice 件改为平铺 2x）", font=F_SMALL,
            fill=CHROME["dim"])

    y = 100
    for i, sheet in enumerate(GM.SHEETS):
        dr.rectangle((PAD, y - 3, W - PAD, y + heights[i] - 3), fill=(*CHROME["panel"], 255))
        dr.line((PAD, y + heights[i] - 3, W - PAD, y + heights[i] - 3), fill=CHROME["line"])
        sheet_row(board, dr, y, sheet, sheets, notes)
        y += heights[i]

    y += 12
    dr.rectangle((PAD, y - 3, W - PAD, y + upg_h - 3), fill=(*CHROME["panel"], 255))
    dr.line((PAD, y + upg_h - 3, W - PAD, y + upg_h - 3), fill=CHROME["line"])
    upg_section(board, dr, y, sheets)
    y += upg_h + 12
    dr.rectangle((PAD, y - 6, W - PAD, y + samp_h), fill=(*CHROME["panel"], 255))
    dr.text((PAD + 8, y + 2), "面板局部样例 —— 真实像素 1:1（左）与 " + str(sz)
            + "x（右）", font=F_HDR, fill=CHROME["txt"])
    dr.text((PAD + 8, y + 26), "panel 9-slice 拉开 ＋ corner/rivet 1:1 贴（四角各一枚铆钉、"
            "盘左侧列分隔再各一枚）＋ 3x3 物品盘（8 空 1 有货）＋ 3 格流体槽（1 有 2 空）"
            "＋ 素布文字牌 ＋ 拉长 scrollbar ＋ coinbar/btn/btn 按下", font=F_SMALL,
            fill=CHROME["dim"])
    board.paste(s1, (PAD + 8, y + 48))
    board.paste(s3, (PAD + 8 + aw + 26, y + 48))
    dr.text((PAD + 8, y + 48 + s3.height + 6),
            "判读：空槽靠「内暗环」承重（对布底 " + fmt_num(num["slot_wall_vs_cloth"])
            + "、对列底更亮一档），不是靠槽心与背景的色差；C1 单侧方案同口径只有 "
            + fmt_num(num["c1_boundary_vs_cloth"]) + " => 这就是 C2 换来的可读性",
            font=F_SMALL, fill=CHROME["warn"])
    dr.text((PAD + 8, y + 48 + s3.height + 23),
            "边界提示（需 Java 侧定，本板不代答）：corner 是 14x14 而契约 MARGIN=6 ⇒ 包角会向内吃 7px，"
            "首行首列槽位与包角重叠 8x8", font=F_SMALL, fill=CHROME["warn"])
    dr.text((PAD + 8, y + 48 + s3.height + 40),
            "本板把内容画在「框厚 + MARGIN」之外（安全侧）；实装要么把包角压在槽位之下（z 序），"
            "要么首行首列让开一格", font=F_SMALL, fill=CHROME["warn"])
    y += samp_h + 18
    print("SEC assembly y0=", y - samp_h - 16, "size=", aw, "x", ah, "zoom=", sz)

    dr.text((PAD + 8, y), "机检读数（与 gen_pocket_gui.py --report 同源，非手抄）",
            font=F_HDR, fill=CHROME["txt"])
    y += 26
    for k in sorted(num):
        dr.text((PAD + 12, y), "NUM " + k + " = " + fmt_num(num[k]), font=F_SMALL,
                fill=CHROME["dim"])
        y += 17
    y += 6
    for ln in log:
        dr.text((PAD + 12, y), "CHK " + ln, font=F_SMALL, fill=CHROME["dim"])
        y += 17

    board = board.crop((0, 0, W, min(total, y + 16)))
    data = pngwrite.image_to_png(board)
    GM.BOARD.parent.mkdir(parents=True, exist_ok=True)
    GM.BOARD.write_bytes(data)
    print("BOARD", GM.BOARD, board.width, "x", board.height, "bytes", len(data))
    return 0


if __name__ == "__main__":
    sys.exit(main())
